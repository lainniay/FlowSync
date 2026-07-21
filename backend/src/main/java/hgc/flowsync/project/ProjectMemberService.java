package hgc.flowsync.project;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import hgc.flowsync.common.error.BusinessException;
import hgc.flowsync.common.error.ErrorCode;
import hgc.flowsync.common.time.ApiDateTime;
import hgc.flowsync.task.TaskMapper;
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
public class ProjectMemberService {

	private final ProjectMemberMapper projectMemberMapper;
	private final ProjectInvitationMapper projectInvitationMapper;
	private final TaskMapper taskMapper;
	private final UserMapper userMapper;
	private final CurrentUserService currentUserService;
	private final ProjectAccessService projectAccessService;
	private final UserWriteLockService userWriteLockService;

	public ProjectMemberService(
		ProjectMemberMapper projectMemberMapper,
		ProjectInvitationMapper projectInvitationMapper,
		TaskMapper taskMapper,
		UserMapper userMapper,
		CurrentUserService currentUserService,
		ProjectAccessService projectAccessService,
		UserWriteLockService userWriteLockService) {
		this.projectMemberMapper = projectMemberMapper;
		this.projectInvitationMapper = projectInvitationMapper;
		this.taskMapper = taskMapper;
		this.userMapper = userMapper;
		this.currentUserService = currentUserService;
		this.projectAccessService = projectAccessService;
		this.userWriteLockService = userWriteLockService;
	}

	@Transactional(readOnly = true)
	public List<ProjectMemberResponse> findAll(Authentication authentication, Long projectId) {
		User currentUser = currentUserService.require(authentication);
		Project project = projectAccessService.requireProject(projectId);
		projectAccessService.requireMemberOrAdmin(project, currentUser);
		return projectMemberMapper.selectList(Wrappers.<ProjectMember>lambdaQuery()
			.eq(ProjectMember::getProjectId, projectId)
			.orderByAsc(ProjectMember::getJoinedAt, ProjectMember::getId)).stream()
			.map(member -> ProjectMemberResponse.from(member, userMapper.selectById(member.getUserId())))
			.toList();
	}

	@Transactional
	public List<ProjectMemberResponse> addAll(
		Authentication authentication,
		Long projectId,
		List<String> requestedUserIds) {
		List<Long> userIds = parseDistinctIds(requestedUserIds);
		User requestedBy = currentUserService.require(authentication);
		UserWriteLockService.LockedUsers lockedUsers = userWriteLockService.lockUsers(
			requestedBy, userIds.toArray(Long[]::new));
		User currentUser = lockedUsers.currentUser();
		if (!projectAccessService.isAdmin(currentUser)) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
		Project project = projectAccessService.requireProjectForUpdate(projectId);
		projectAccessService.requireUnarchived(project);

		Map<Long, User> users = new HashMap<>();
		userIds.forEach(userId -> users.put(userId, lockedUsers.user(userId)));
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
		}

		List<ProjectMemberResponse> members = new ArrayList<>(userIds.size());
		for (int index = 0; index < userIds.size(); index++) {
			Long userId = userIds.get(index);
			try {
				members.add(add(projectId, users.get(userId)));
			} catch (DuplicateKeyException exception) {
				throw new BusinessException(ErrorCode.MEMBER_ALREADY_EXISTS, userIdField(index));
			}
		}
		return List.copyOf(members);
	}

	@Transactional
	public void remove(Authentication authentication, Long projectId, Long userId) {
		User currentUser = currentUserService.requireForUpdate(authentication);
		Project project = projectAccessService.requireProjectForUpdate(projectId);
		projectAccessService.requireOwnerOrAdmin(project, currentUser);
		projectAccessService.requireUnarchived(project);
		if (project.getOwnerId().equals(userId)) {
			throw new BusinessException(ErrorCode.RESOURCE_IN_USE);
		}
		if (!projectMemberMapper.existsByProjectIdAndUserId(projectId, userId)) {
			throw new BusinessException(ErrorCode.NOT_FOUND);
		}
		if (taskMapper.existsIncompleteByProjectIdAndAssigneeId(projectId, userId)) {
			throw new BusinessException(ErrorCode.MEMBER_HAS_ACTIVE_TASKS);
		}
		projectMemberMapper.delete(Wrappers.<ProjectMember>lambdaQuery()
			.eq(ProjectMember::getProjectId, projectId)
			.eq(ProjectMember::getUserId, userId));
	}

	private ProjectMemberResponse add(Long projectId, User user) {
		ProjectMember member = new ProjectMember();
		member.setProjectId(projectId);
		member.setUserId(user.getId());
		projectMemberMapper.insert(member);

		ProjectInvitation invitation = projectInvitationMapper
			.selectByProjectIdAndInviteeIdForUpdate(projectId, user.getId());
		if (invitation != null && invitation.getStatus() == InvitationStatus.PENDING) {
			invitation.setStatus(InvitationStatus.CANCELLED);
			invitation.setRespondedAt(ApiDateTime.now());
			projectInvitationMapper.updateById(invitation);
		}
		return ProjectMemberResponse.from(projectMemberMapper.selectById(member.getId()), user);
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
}
