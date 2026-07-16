package hgc.flowsync.user;

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

@Service
public class UserService {

	private final UserMapper userMapper;
	private final PasswordEncoder passwordEncoder;
	private final SessionRegistry sessionRegistry;
	private final ProjectMapper projectMapper;
	private final ProjectMemberMapper projectMemberMapper;
	private final ProjectInvitationMapper projectInvitationMapper;
	private final TaskMapper taskMapper;

	public UserService(
		UserMapper userMapper,
		PasswordEncoder passwordEncoder,
		SessionRegistry sessionRegistry,
		ProjectMapper projectMapper,
		ProjectMemberMapper projectMemberMapper,
		ProjectInvitationMapper projectInvitationMapper,
		TaskMapper taskMapper) {
		this.userMapper = userMapper;
		this.passwordEncoder = passwordEncoder;
		this.sessionRegistry = sessionRegistry;
		this.projectMapper = projectMapper;
		this.projectMemberMapper = projectMemberMapper;
		this.projectInvitationMapper = projectInvitationMapper;
		this.taskMapper = taskMapper;
	}

	public UserResponse findById(Long userId) {
		User user = userMapper.selectById(userId);
		if (user == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND);
		}
		return UserResponse.from(user);
	}

	@Transactional(readOnly = true)
	public UserPageResponse findAll(
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
		return new UserPageResponse(
			userMapper.selectList(query).stream().map(UserResponse::from).toList(),
			page,
			size,
			totalElements,
			totalElements == 0 ? 0 : (totalElements - 1) / size + 1);
	}

	@Transactional
	public UserResponse create(
		String username,
		String initialPassword,
		String displayName,
		SystemRole systemRole,
		String phone,
		String email) {
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
	public void resetPassword(Long userId, String newPassword) {
		User user = userMapper.selectOne(Wrappers.<User>lambdaQuery()
			.eq(User::getId, userId)
			.last("FOR UPDATE"));
		if (user == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND);
		}
		updatePassword(user, newPassword);
	}

	@Transactional
	public UserResponse update(
		Long userId,
		String displayName,
		String phone,
		String email,
		SystemRole systemRole,
		boolean active) {
		var activeAdmins = userMapper.selectList(Wrappers.<User>lambdaQuery()
			.select(User::getId)
			.eq(User::getSystemRole, SystemRole.ADMIN)
			.eq(User::isActive, true)
			.orderByAsc(User::getId)
			.last("FOR UPDATE"));
		User user = userMapper.selectOne(Wrappers.<User>lambdaQuery()
			.eq(User::getId, userId)
			.last("FOR UPDATE"));
		if (user == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND);
		}

		boolean removesActiveAdmin = user.isActive()
			&& user.getSystemRole() == SystemRole.ADMIN
			&& (!active || systemRole != SystemRole.ADMIN);
		if (removesActiveAdmin && activeAdmins.size() == 1) {
			throw new BusinessException(ErrorCode.LAST_ADMIN_REQUIRED);
		}
		if (user.getSystemRole() == SystemRole.USER && systemRole == SystemRole.ADMIN
			&& (projectMemberMapper.existsByUserId(userId)
				|| projectInvitationMapper.existsPendingByInviteeId(userId))) {
			throw new BusinessException(ErrorCode.USER_HAS_PROJECT_MEMBERSHIP);
		}
		if (user.isActive() && !active) {
			if (projectMapper.existsByOwnerId(userId)) {
				throw new BusinessException(ErrorCode.USER_OWNS_PROJECT);
			}
			if (taskMapper.existsIncompleteByAssigneeId(userId)) {
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
		sessionRegistry.getAllSessions(username, false).forEach(SessionInformation::expireNow);
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
