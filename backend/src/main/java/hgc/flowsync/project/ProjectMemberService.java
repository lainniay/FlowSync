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
public class ProjectMemberService {

	private final ProjectMemberMapper projectMemberMapper;
	private final ProjectInvitationMapper projectInvitationMapper;
	private final UserMapper userMapper;
	private final CurrentUserService currentUserService;
	private final ProjectAccessService projectAccessService;

	public ProjectMemberService(
		ProjectMemberMapper projectMemberMapper,
		ProjectInvitationMapper projectInvitationMapper,
		UserMapper userMapper,
		CurrentUserService currentUserService,
		ProjectAccessService projectAccessService) {
		this.projectMemberMapper = projectMemberMapper;
		this.projectInvitationMapper = projectInvitationMapper;
		this.userMapper = userMapper;
		this.currentUserService = currentUserService;
		this.projectAccessService = projectAccessService;
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
		User currentUser = currentUserService.requireForUpdate(authentication);
		if (!projectAccessService.isAdmin(currentUser)) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
		Project project = projectAccessService.requireProjectForUpdate(projectId);
		projectAccessService.requireUnarchived(project);

		List<Long> userIds = parseDistinctIds(requestedUserIds);
		Map<Long, User> users = users(userIds);
		for (Long userId : userIds) {
			User user = users.get(userId);
			if (!user.isActive() || user.getSystemRole() != SystemRole.USER) {
				throw new BusinessException(ErrorCode.VALIDATION_ERROR);
			}
			if (projectMemberMapper.existsByProjectIdAndUserId(projectId, userId)) {
				throw new BusinessException(ErrorCode.MEMBER_ALREADY_EXISTS);
			}
		}

		try {
			return userIds.stream().map(userId -> add(projectId, users.get(userId))).toList();
		} catch (DuplicateKeyException exception) {
			throw new BusinessException(ErrorCode.MEMBER_ALREADY_EXISTS);
		}
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
			invitation.setRespondedAt(LocalDateTime.now());
			projectInvitationMapper.updateById(invitation);
		}
		return ProjectMemberResponse.from(projectMemberMapper.selectById(member.getId()), user);
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
}
