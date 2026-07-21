package hgc.flowsync.user;

import java.util.List;
import java.util.TreeSet;

import hgc.flowsync.common.api.PageResponse;
import hgc.flowsync.common.error.BusinessException;
import hgc.flowsync.common.error.ErrorCode;
import hgc.flowsync.project.ProjectInvitationMapper;
import hgc.flowsync.project.ProjectMapper;
import hgc.flowsync.project.ProjectMemberMapper;
import hgc.flowsync.task.TaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class UserService {

	private final UserMapper userMapper;
	private final PasswordEncoder passwordEncoder;
	private final SessionRegistry sessionRegistry;
	private final ProjectMapper projectMapper;
	private final ProjectMemberMapper projectMemberMapper;
	private final ProjectInvitationMapper projectInvitationMapper;
	private final TaskMapper taskMapper;
	private final UserWriteLockService userWriteLockService;

	public UserService(
		UserMapper userMapper,
		PasswordEncoder passwordEncoder,
		SessionRegistry sessionRegistry,
		ProjectMapper projectMapper,
		ProjectMemberMapper projectMemberMapper,
		ProjectInvitationMapper projectInvitationMapper,
		TaskMapper taskMapper,
		UserWriteLockService userWriteLockService) {
		this.userMapper = userMapper;
		this.passwordEncoder = passwordEncoder;
		this.sessionRegistry = sessionRegistry;
		this.projectMapper = projectMapper;
		this.projectMemberMapper = projectMemberMapper;
		this.projectInvitationMapper = projectInvitationMapper;
		this.taskMapper = taskMapper;
		this.userWriteLockService = userWriteLockService;
	}

	public UserResponse findById(Long userId) {
		User user = userMapper.selectById(userId);
		if (user == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND);
		}
		return UserResponse.from(user);
	}

	@Transactional(readOnly = true)
	public PageResponse<UserResponse> findAll(
		String q,
		SystemRole systemRole,
		boolean active,
		int page,
		int size,
		String sort) {
		LambdaQueryWrapper<User> query = Wrappers.<User>lambdaQuery()
			.eq(User::isActive, active)
			.eq(systemRole != null, User::getSystemRole, systemRole)
			.and(q != null && !q.isEmpty(), conditions -> conditions
				.like(User::getUsername, q)
				.or()
				.like(User::getDisplayName, q));
		long totalElements = userMapper.selectCount(query);
		applySort(query, sort);
		query.orderByAsc(User::getId)
			.last("LIMIT " + size + " OFFSET " + (long) page * size);
		return PageResponse.of(
			userMapper.selectList(query).stream().map(UserResponse::from).toList(),
			page,
			size,
			totalElements);
	}

	@Transactional
	public UserResponse create(
		String actingUsername,
		String username,
		String initialPassword,
		String displayName,
		SystemRole systemRole,
		String phone,
		String email) {
		// Serialize acting-admin revalidation with concurrent role and active-state changes.
		userWriteLockService.lockAdminRoleChanges();
		User actingAdmin = findActingUser(actingUsername);
		requireActingAdmin(userWriteLockService.lockUsersById(List.of(actingAdmin.getId()))
			.get(actingAdmin.getId()));
		if (userMapper.selectCount(Wrappers.<User>lambdaQuery()
			.eq(User::getUsername, username)) > 0) {
			throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS);
		}

		User user = new User();
		user.setUsername(username);
		user.setPasswordHash(passwordEncoder.encode(initialPassword));
		user.setDisplayName(displayName);
		user.setSystemRole(systemRole);
		user.setPhone(phone);
		user.setEmail(email);
		try {
			userMapper.insert(user);
		} catch (DuplicateKeyException exception) {
			throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS);
		}
		return UserResponse.from(userMapper.selectById(user.getId()));
	}

	@Transactional
	public void resetPassword(String actingUsername, Long userId, String newPassword) {
		userWriteLockService.lockAdminRoleChanges();
		User actingAdmin = findActingUser(actingUsername);
		var lockedUsers = userWriteLockService.lockUsersById(List.of(actingAdmin.getId(), userId));
		requireActingAdmin(lockedUsers.get(actingAdmin.getId()));
		User user = lockedUsers.get(userId);
		if (user == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND);
		}
		updatePassword(user, newPassword);
	}

	@Transactional
	public UserResponse update(
		String actingUsername,
		Long userId,
		String displayName,
		String phone,
		String email,
		SystemRole systemRole,
		boolean active) {
		userWriteLockService.lockAdminRoleChanges();
		User actingAdmin = findActingUser(actingUsername);
		var userIds = new TreeSet<>(userMapper.selectList(Wrappers.<User>lambdaQuery()
			.select(User::getId)
			.eq(User::getSystemRole, SystemRole.ADMIN)
			.eq(User::isActive, true)).stream().map(User::getId).toList());
		userIds.add(userId);
		userIds.add(actingAdmin.getId());
		return updateLocked(
			actingAdmin.getId(), userId, displayName, phone, email, systemRole, active, userIds);
	}

	private UserResponse updateLocked(
		Long actingUserId,
		Long userId,
		String displayName,
		String phone,
		String email,
		SystemRole systemRole,
		boolean active,
		TreeSet<Long> userIds) {
		var lockedUsers = userWriteLockService.lockUsersById(userIds);
		requireActingAdmin(lockedUsers.get(actingUserId));
		User user = lockedUsers.get(userId);
		if (user == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND);
		}
		long activeAdminCount = lockedUsers.values().stream()
			.filter(candidate -> candidate.isActive()
				&& candidate.getSystemRole() == SystemRole.ADMIN)
			.count();

		boolean removesActiveAdmin = user.isActive()
			&& user.getSystemRole() == SystemRole.ADMIN
			&& (!active || systemRole != SystemRole.ADMIN);
		if (removesActiveAdmin && activeAdminCount == 1) {
			throw new BusinessException(ErrorCode.LAST_ADMIN_REQUIRED);
		}
		if (user.getSystemRole() == SystemRole.USER && systemRole == SystemRole.ADMIN
			&& (projectMemberMapper.existsByUserIdForUpdate(userId)
				|| projectInvitationMapper.existsPendingByInviteeIdForUpdate(userId))) {
			throw new BusinessException(ErrorCode.USER_HAS_PROJECT_MEMBERSHIP);
		}
		if (user.isActive() && !active) {
			if (projectMapper.existsByOwnerIdForUpdate(userId)) {
				throw new BusinessException(ErrorCode.USER_OWNS_PROJECT);
			}
			if (taskMapper.existsIncompleteByAssigneeIdForUpdate(userId)) {
				throw new BusinessException(ErrorCode.USER_HAS_ACTIVE_TASKS);
			}
		}

		boolean invalidateSessions = user.getSystemRole() != systemRole || user.isActive() && !active;
		userMapper.update(null, Wrappers.<User>lambdaUpdate()
			.eq(User::getId, userId)
			.set(User::getDisplayName, displayName)
			.set(User::getPhone, phone)
			.set(User::getEmail, email)
			.set(User::getSystemRole, systemRole)
			.set(User::isActive, active));
		if (invalidateSessions) {
			invalidateSessions(user.getUsername());
		}
		return UserResponse.from(userMapper.selectById(userId));
	}

	private User findActingUser(String username) {
		User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
		if (user == null) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED);
		}
		return user;
	}

	private static void requireActingAdmin(User user) {
		if (user == null || !user.isActive()) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED);
		}
		if (user.getSystemRole() != SystemRole.ADMIN) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
	}

	public UserResponse updateProfile(User user, String displayName, String phone, String email) {
		userMapper.update(null, Wrappers.<User>lambdaUpdate()
			.eq(User::getId, user.getId())
			.set(User::getDisplayName, displayName)
			.set(User::getPhone, phone)
			.set(User::getEmail, email));
		return UserResponse.from(userMapper.selectById(user.getId()));
	}

	public void updatePassword(User user, String newPassword) {
		userMapper.update(null, Wrappers.<User>lambdaUpdate()
			.eq(User::getId, user.getId())
			.set(User::getPasswordHash, passwordEncoder.encode(newPassword)));
		invalidateSessions(user.getUsername());
	}

	private void invalidateSessions(String username) {
		List<String> sessionIds = sessionRegistry.getAllSessions(username, false).stream()
			.map(SessionInformation::getSessionId)
			.toList();
		if (TransactionSynchronizationManager.isActualTransactionActive()
			&& TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					invalidateSessionsNow(sessionIds);
				}
			});
			return;
		}
		invalidateSessionsNow(sessionIds);
	}

	private void invalidateSessionsNow(List<String> sessionIds) {
		sessionIds.stream()
			.map(sessionRegistry::getSessionInformation)
			.filter(java.util.Objects::nonNull)
			.forEach(SessionInformation::expireNow);
	}

	private static void applySort(LambdaQueryWrapper<User> query, String sort) {
		String[] parts = sort.split(",", 2);
		boolean ascending = parts[1].equals("asc");
		switch (parts[0]) {
			case "createdAt" -> query.orderBy(true, ascending, User::getCreatedAt);
			case "username" -> query.orderBy(true, ascending, User::getUsername);
			case "displayName" -> query.orderBy(true, ascending, User::getDisplayName);
			default -> throw new IllegalArgumentException("Unsupported user sort field");
		}
	}
}
