package hgc.flowsync.project;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import hgc.flowsync.common.error.BusinessException;
import hgc.flowsync.common.error.ErrorCode;
import hgc.flowsync.user.CurrentUserService;
import hgc.flowsync.user.SystemRole;
import hgc.flowsync.user.User;
import hgc.flowsync.user.UserMapper;
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

	public ProjectInvitationService(
		ProjectInvitationMapper projectInvitationMapper,
		ProjectMemberMapper projectMemberMapper,
		UserMapper userMapper,
		CurrentUserService currentUserService,
		ProjectAccessService projectAccessService) {
		this.projectInvitationMapper = projectInvitationMapper;
		this.projectMemberMapper = projectMemberMapper;
		this.userMapper = userMapper;
		this.currentUserService = currentUserService;
		this.projectAccessService = projectAccessService;
	}

	@Transactional
	public List<ProjectInvitationResponse> createAll(
		Authentication authentication,
		Long projectId,
		List<String> requestedUserIds) {
		User currentUser = currentUserService.requireForUpdate(authentication);
		Project project = projectAccessService.requireProjectForUpdate(projectId);
		projectAccessService.requireOwner(project, currentUser);
		projectAccessService.requireUnarchived(project);

		List<Long> userIds = parseDistinctIds(requestedUserIds);
		Map<Long, User> users = users(userIds);
		Map<Long, ProjectInvitation> existing = new HashMap<>();
		for (Long userId : userIds) {
			User user = users.get(userId);
			if (!user.isActive() || user.getSystemRole() != SystemRole.USER) {
				throw new BusinessException(ErrorCode.VALIDATION_ERROR);
			}
			if (projectMemberMapper.existsByProjectIdAndUserId(projectId, userId)) {
				throw new BusinessException(ErrorCode.MEMBER_ALREADY_EXISTS);
			}
			ProjectInvitation invitation = projectInvitationMapper
				.selectByProjectIdAndInviteeIdForUpdate(projectId, userId);
			if (invitation != null && invitation.getStatus() == InvitationStatus.PENDING) {
				throw new BusinessException(ErrorCode.INVITATION_ALREADY_PENDING);
			}
			existing.put(userId, invitation);
		}

		try {
			return userIds.stream()
				.map(userId -> save(project, users.get(userId), currentUser, existing.get(userId)))
				.toList();
		} catch (DuplicateKeyException exception) {
			throw new BusinessException(ErrorCode.INVITATION_ALREADY_PENDING);
		}
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
		invitation.setRespondedAt(LocalDateTime.now());
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
		requirePending(invitation);
		projectAccessService.requireUnarchived(project);

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
		invitation.setRespondedAt(LocalDateTime.now());
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

	private Map<Long, User> users(List<Long> userIds) {
		Map<Long, User> users = new HashMap<>();
		userMapper.selectList(Wrappers.<User>lambdaQuery()
			.in(User::getId, userIds)
			.last("FOR UPDATE")).forEach(user -> users.put(user.getId(), user));
		if (users.size() != userIds.size()) {
			throw new BusinessException(ErrorCode.NOT_FOUND);
		}
		return users;
	}

	private static List<Long> parseDistinctIds(List<String> requestedUserIds) {
		List<Long> userIds = requestedUserIds.stream().map(Long::parseLong).toList();
		if (new HashSet<>(userIds).size() != userIds.size()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR);
		}
		return userIds;
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
