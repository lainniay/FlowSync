package hgc.flowsync.ai;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import hgc.flowsync.common.error.BusinessException;
import hgc.flowsync.common.error.ErrorCode;
import hgc.flowsync.project.Priority;
import hgc.flowsync.project.Project;
import hgc.flowsync.project.ProjectAccessService;
import hgc.flowsync.project.ProjectMember;
import hgc.flowsync.project.ProjectMemberMapper;
import hgc.flowsync.project.ProjectStatus;
import hgc.flowsync.task.Task;
import hgc.flowsync.task.TaskMapper;
import hgc.flowsync.task.TaskStatus;
import hgc.flowsync.user.CurrentUserService;
import hgc.flowsync.user.SystemRole;
import hgc.flowsync.user.User;
import hgc.flowsync.user.UserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiContextService {

	private final TaskMapper taskMapper;
	private final ProjectMemberMapper projectMemberMapper;
	private final UserMapper userMapper;
	private final CurrentUserService currentUserService;
	private final ProjectAccessService projectAccessService;

	public AiContextService(
		TaskMapper taskMapper,
		ProjectMemberMapper projectMemberMapper,
		UserMapper userMapper,
		CurrentUserService currentUserService,
		ProjectAccessService projectAccessService) {
		this.taskMapper = taskMapper;
		this.projectMemberMapper = projectMemberMapper;
		this.userMapper = userMapper;
		this.currentUserService = currentUserService;
		this.projectAccessService = projectAccessService;
	}

	@Transactional(readOnly = true)
	public TaskSuggestionContext taskSuggestion(Authentication authentication, Long taskId) {
		User currentUser = currentUserService.require(authentication);
		Task task = taskId == null ? null : taskMapper.selectById(taskId);
		if (task == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND);
		}
		Project project = projectAccessService.requireProject(task.getProjectId());
		if (projectAccessService.isAdmin(currentUser)) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
		if (!projectAccessService.isMember(project, currentUser)) {
			throw new BusinessException(ErrorCode.NOT_FOUND);
		}
		projectAccessService.requireUnarchived(project);
		if (!projectAccessService.isOwner(project, currentUser)
			&& !Objects.equals(task.getAssigneeId(), currentUser.getId())) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
		return new TaskSuggestionContext(
			task.getTitle(), task.getDescription(), task.getStatus(), task.getPriority(), task.getDueDate());
	}

	@Transactional(readOnly = true)
	public TaskPlanContext taskPlan(Authentication authentication, Long projectId) {
		User currentUser = currentUserService.require(authentication);
		Project project = projectAccessService.requireProject(projectId);
		if (projectAccessService.isAdmin(currentUser)) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
		projectAccessService.requireOwner(project, currentUser);
		projectAccessService.requireUnarchived(project);
		List<Long> memberIds = projectMemberMapper.selectList(Wrappers.<ProjectMember>lambdaQuery()
			.eq(ProjectMember::getProjectId, projectId)).stream()
			.map(ProjectMember::getUserId)
			.toList();
		List<MemberContext> members = memberIds.isEmpty() ? List.of()
			: userMapper.selectBatchIds(memberIds).stream()
				.filter(User::isActive)
				.filter(user -> user.getSystemRole() == SystemRole.USER)
				.sorted(Comparator.comparing(User::getId))
				.map(user -> new MemberContext(user.getId().toString(), user.getDisplayName()))
				.toList();
		return new TaskPlanContext(
			project.getName(), project.getDescription(), project.getStatus(), project.getPriority(),
			project.getStartDate(), project.getEndDate(), members);
	}

	public record TaskSuggestionContext(
		String title,
		String description,
		TaskStatus status,
		Priority priority,
		LocalDate dueDate) {
	}

	public record TaskPlanContext(
		String name,
		String description,
		ProjectStatus status,
		Priority priority,
		LocalDate startDate,
		LocalDate endDate,
		List<MemberContext> members) {
	}

	public record MemberContext(String id, String displayName) {
	}
}
