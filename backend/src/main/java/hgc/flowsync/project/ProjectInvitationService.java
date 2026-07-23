package hgc.flowsync.project;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import hgc.flowsync.common.error.BusinessException;
import hgc.flowsync.common.error.ErrorCode;
import hgc.flowsync.common.time.ApiDateTime;
import hgc.flowsync.user.CurrentUserService;
import hgc.flowsync.user.SystemRole;
import hgc.flowsync.user.User;
import hgc.flowsync.user.UserMapper;
import hgc.flowsync.user.UserWriteLockService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectInvitationService {

	private final ProjectInvitationMapper projectInvitationMapper;
	private final ProjectMemberMapper projectMemberMapper;
	private final UserMapper userMapper;
	private final CurrentUserService currentUserService;
	private final ProjectAccessService projectAccessService;
	private final UserWriteLockService userWriteLockService;

	public ProjectInvitationService(
		ProjectInvitationMapper projectInvitationMapper,
		ProjectMemberMapper projectMemberMapper,
		UserMapper userMapper,
		CurrentUserService currentUserService,
		ProjectAccessService projectAccessService,
		UserWriteLockService userWriteLockService) {
		this.projectInvitationMapper = projectInvitationMapper;
		this.projectMemberMapper = projectMemberMapper;
		this.userMapper = userMapper;
		this.currentUserService = currentUserService;
		this.projectAccessService = projectAccessService;
		this.userWriteLockService = userWriteLockService;
	}

	@Transactional
	public List<ProjectInvitationResponse> createAll(
		Authentication authentication,
		Long projectId,
		List<String> requestedUserIds) {
		List<Long> userIds = parseDistinctIds(requestedUserIds);
		User requestedBy = currentUserService.require(authentication);
		UserWriteLockService.LockedUsers lockedUsers = userWriteLockService.lockUsers(
			requestedBy, userIds.toArray(Long[]::new));
		User currentUser = lockedUsers.currentUser();
		Project project = projectAccessService.requireProjectForUpdate(projectId);
		projectAccessService.requireOwner(project, currentUser);
		projectAccessService.requireUnarchived(project);

		Map<Long, User> users = new HashMap<>();
		userIds.forEach(userId -> users.put(userId, lockedUsers.user(userId)));
		Map<Long, ProjectInvitation> existing = new HashMap<>();
		for (int index = 0; index < userIds.size(); index++) {
			Long userId = userIds.get(index);
			User user = users.get(userId);
			String field = userIdField(index);
			if (user == null) {
				throw new BusinessException(ErrorCode.NOT_FOUND, field);
			}
			if (!user.isActive() || user.getSystemRole() != SystemRole.USER) {
				throw new BusinessException(ErrorCode.VALIDATION_ERROR, field);
			}
			if (projectMemberMapper.existsByProjectIdAndUserIdForUpdate(projectId, userId)) {
				throw new BusinessException(ErrorCode.MEMBER_ALREADY_EXISTS, field);
			}
			ProjectInvitation invitation = projectInvitationMapper
				.selectByProjectIdAndInviteeIdForUpdate(projectId, userId);
			if (invitation != null && invitation.getStatus() == InvitationStatus.PENDING) {
				throw new BusinessException(ErrorCode.INVITATION_ALREADY_PENDING, field);
			}
			existing.put(userId, invitation);
		}

		List<ProjectInvitationResponse> invitations = new ArrayList<>(userIds.size());
		for (int index = 0; index < userIds.size(); index++) {
			Long userId = userIds.get(index);
			try {
				invitations.add(save(project, users.get(userId), currentUser, existing.get(userId)));
			} catch (DuplicateKeyException exception) {
				throw new BusinessException(
					ErrorCode.INVITATION_ALREADY_PENDING, userIdField(index));
			}
		}
		return List.copyOf(invitations);
	}

	@Transactional(readOnly = true)
	public List<InvitationCandidateResponse> findCandidates(
		Authentication authentication,
		Long projectId,
		String query) {
		String normalizedQuery = query.trim();
		if (normalizedQuery.length() < 2) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "q");
		}
		User currentUser = currentUserService.require(authentication);
		Project project = projectAccessService.requireProject(projectId);
		projectAccessService.requireOwner(project, currentUser);
		projectAccessService.requireUnarchived(project);

		List<Long> excludedUserIds = new ArrayList<>(projectMemberMapper
			.selectList(Wrappers.<ProjectMember>lambdaQuery()
				.select(ProjectMember::getUserId)
				.eq(ProjectMember::getProjectId, projectId)).stream()
			.map(ProjectMember::getUserId)
			.toList());
		excludedUserIds.addAll(projectInvitationMapper
			.selectList(Wrappers.<ProjectInvitation>lambdaQuery()
				.select(ProjectInvitation::getInviteeId)
				.eq(ProjectInvitation::getProjectId, projectId)
				.eq(ProjectInvitation::getStatus, InvitationStatus.PENDING)).stream()
			.map(ProjectInvitation::getInviteeId)
			.toList());

		var userQuery = Wrappers.<User>lambdaQuery()
			.eq(User::isActive, true)
			.eq(User::getSystemRole, SystemRole.USER)
			.and(wrapper -> wrapper
				.like(User::getDisplayName, normalizedQuery)
				.or()
				.like(User::getUsername, normalizedQuery))
			.orderByAsc(User::getDisplayName, User::getUsername)
			.last("LIMIT 20");
		if (!excludedUserIds.isEmpty()) {
			userQuery.notIn(User::getId, excludedUserIds);
		}
		return userMapper.selectList(userQuery).stream()
			.map(InvitationCandidateResponse::from)
			.toList();
	}

	@Transactional(readOnly = true)
	public List<ProjectInvitationResponse> findByProject(
		Authentication authentication,
		Long projectId) {
		User currentUser = currentUserService.require(authentication);
		Project project = projectAccessService.requireProject(projectId);
		projectAccessService.requireOwnerOrAdmin(project, currentUser);
		return projectInvitationMapper.selectList(Wrappers.<ProjectInvitation>lambdaQuery()
			.eq(ProjectInvitation::getProjectId, projectId)
			.orderByDesc(ProjectInvitation::getCreatedAt, ProjectInvitation::getId)).stream()
			.map(invitation -> response(invitation, project))
			.toList();
	}

	@Transactional(readOnly = true)
	public List<ProjectInvitationResponse> findMine(
		Authentication authentication,
		InvitationStatus status) {
		User currentUser = currentUserService.require(authentication);
		var query = Wrappers.<ProjectInvitation>lambdaQuery()
			.eq(ProjectInvitation::getInviteeId, currentUser.getId());
		if (status != null) {
			query.eq(ProjectInvitation::getStatus, status);
		}
		return projectInvitationMapper.selectList(query
			.orderByDesc(ProjectInvitation::getCreatedAt, ProjectInvitation::getId)).stream()
			.map(invitation -> response(invitation, projectAccessService.requireProject(invitation.getProjectId())))
			.toList();
	}

	@Transactional
	public void cancel(Authentication authentication, Long projectId, Long invitationId) {
		User currentUser = currentUserService.requireForUpdate(authentication);
		Project project = projectAccessService.requireProjectForUpdate(projectId);
		projectAccessService.requireOwnerOrAdmin(project, currentUser);
		projectAccessService.requireUnarchived(project);
		ProjectInvitation invitation = projectInvitationMapper.selectOne(Wrappers.<ProjectInvitation>lambdaQuery()
			.eq(ProjectInvitation::getId, invitationId)
			.eq(ProjectInvitation::getProjectId, projectId)
			.last("FOR UPDATE"));
		requirePending(invitation);
		invitation.setStatus(InvitationStatus.CANCELLED);
		invitation.setRespondedAt(ApiDateTime.now());
		projectInvitationMapper.updateById(invitation);
	}

	@Transactional
	public ProjectInvitationResponse respond(
		Authentication authentication,
		Long invitationId,
		InvitationStatus status) {
		User currentUser = currentUserService.requireForUpdate(authentication);
		ProjectInvitation invitation = projectInvitationMapper.selectById(invitationId);
		if (invitation == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND);
		}
		Project project = projectAccessService.requireProjectForUpdate(invitation.getProjectId());
		invitation = projectInvitationMapper.selectOne(Wrappers.<ProjectInvitation>lambdaQuery()
			.eq(ProjectInvitation::getId, invitationId)
			.eq(ProjectInvitation::getProjectId, project.getId())
			.last("FOR UPDATE"));
		if (!invitation.getInviteeId().equals(currentUser.getId())) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
		projectAccessService.requireUnarchived(project);
		requirePending(invitation);

		if (status == InvitationStatus.ACCEPTED) {
			if (projectMemberMapper.existsByProjectIdAndUserId(project.getId(), currentUser.getId())) {
				throw new BusinessException(ErrorCode.MEMBER_ALREADY_EXISTS);
			}
			ProjectMember member = new ProjectMember();
			member.setProjectId(project.getId());
			member.setUserId(currentUser.getId());
			try {
				projectMemberMapper.insert(member);
			} catch (DuplicateKeyException exception) {
				throw new BusinessException(ErrorCode.MEMBER_ALREADY_EXISTS);
			}
		}
		invitation.setStatus(status);
		invitation.setRespondedAt(ApiDateTime.now());
		projectInvitationMapper.updateById(invitation);
		return response(invitation, project);
	}

	private ProjectInvitationResponse save(
		Project project,
		User invitee,
		User inviter,
		ProjectInvitation invitation) {
		if (invitation == null) {
			invitation = new ProjectInvitation();
			invitation.setProjectId(project.getId());
			invitation.setInviteeId(invitee.getId());
		} else {
			invitation.setRespondedAt(null);
		}
		invitation.setInvitedBy(inviter.getId());
		invitation.setStatus(InvitationStatus.PENDING);
		if (invitation.getId() == null) {
			projectInvitationMapper.insert(invitation);
		} else {
			projectInvitationMapper.updateById(invitation);
		}
		return response(projectInvitationMapper.selectById(invitation.getId()), project);
	}

	private ProjectInvitationResponse response(ProjectInvitation invitation, Project project) {
		return ProjectInvitationResponse.from(
			invitation,
			project,
			userMapper.selectById(invitation.getInviteeId()),
			userMapper.selectById(invitation.getInvitedBy()));
	}

	private static List<Long> parseDistinctIds(List<String> requestedUserIds) {
		List<Long> userIds = new ArrayList<>(requestedUserIds.size());
		HashSet<Long> seen = new HashSet<>();
		for (int index = 0; index < requestedUserIds.size(); index++) {
			Long userId;
			try {
				userId = Long.parseLong(requestedUserIds.get(index));
			} catch (NumberFormatException exception) {
				throw new BusinessException(ErrorCode.VALIDATION_ERROR, userIdField(index));
			}
			if (!seen.add(userId)) {
				throw new BusinessException(ErrorCode.VALIDATION_ERROR, userIdField(index));
			}
			userIds.add(userId);
		}
		return List.copyOf(userIds);
	}

	private static String userIdField(int index) {
		return "userIds[" + index + "]";
	}

	private static void requirePending(ProjectInvitation invitation) {
		if (invitation == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND);
		}
		if (invitation.getStatus() != InvitationStatus.PENDING) {
			throw new BusinessException(ErrorCode.INVALID_INVITATION_STATE);
		}
	}
}
