package hgc.flowsync.overview;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import hgc.flowsync.common.error.BusinessException;
import hgc.flowsync.common.error.ErrorCode;
import hgc.flowsync.common.time.ApiDateTime;
import hgc.flowsync.project.Project;
import hgc.flowsync.project.ProjectAccessService;
import hgc.flowsync.project.ProjectMapper;
import hgc.flowsync.project.ProjectMember;
import hgc.flowsync.project.ProjectMemberMapper;
import hgc.flowsync.project.ProjectStatus;
import hgc.flowsync.summary.SummaryMapper;
import hgc.flowsync.task.TaskLogMapper;
import hgc.flowsync.task.TaskMapper;
import hgc.flowsync.task.Task;
import hgc.flowsync.task.TaskStatus;
import hgc.flowsync.user.CurrentUserService;
import hgc.flowsync.user.User;
import hgc.flowsync.user.UserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OverviewService {

	private final ProjectMapper projectMapper;
	private final ProjectMemberMapper projectMemberMapper;
	private final TaskMapper taskMapper;
	private final TaskLogMapper taskLogMapper;
	private final SummaryMapper summaryMapper;
	private final UserMapper userMapper;
	private final CurrentUserService currentUserService;
	private final ProjectAccessService projectAccessService;

	public OverviewService(
		ProjectMapper projectMapper,
		ProjectMemberMapper projectMemberMapper,
		TaskMapper taskMapper,
		TaskLogMapper taskLogMapper,
		SummaryMapper summaryMapper,
		UserMapper userMapper,
		CurrentUserService currentUserService,
		ProjectAccessService projectAccessService) {
		this.projectMapper = projectMapper;
		this.projectMemberMapper = projectMemberMapper;
		this.taskMapper = taskMapper;
		this.taskLogMapper = taskLogMapper;
		this.summaryMapper = summaryMapper;
		this.userMapper = userMapper;
		this.currentUserService = currentUserService;
		this.projectAccessService = projectAccessService;
	}

	@Transactional(readOnly = true)
	public OverviewResponse find(Authentication authentication, Long requestedProjectId) {
		User currentUser = currentUserService.require(authentication);
		List<Project> projects = visibleProjects(currentUser, requestedProjectId);
		if (requestedProjectId != null && projects.isEmpty()) {
			throw new BusinessException(ErrorCode.NOT_FOUND);
		}
		if (projects.isEmpty()) {
			return response(currentUser, projects, List.of());
		}

		List<Long> projectIds = projects.stream().map(Project::getId).toList();
		return response(currentUser, projects, projectIds);
	}

	private List<Project> visibleProjects(User currentUser, Long requestedProjectId) {
		if (projectAccessService.isAdmin(currentUser)) {
			return List.of();
		}
		List<Long> projectIds = projectMemberMapper.selectList(Wrappers.<ProjectMember>lambdaQuery()
			.select(ProjectMember::getProjectId)
			.eq(ProjectMember::getUserId, currentUser.getId())).stream()
			.map(ProjectMember::getProjectId)
			.filter(id -> requestedProjectId == null || requestedProjectId.equals(id))
			.toList();
		return projectIds.isEmpty() ? List.of() : projectMapper.selectList(
			Wrappers.<Project>lambdaQuery()
				.in(Project::getId, projectIds)
				.isNull(Project::getArchivedAt));
	}

	private OverviewResponse response(
		User currentUser,
		List<Project> projects,
		List<Long> projectIds) {
		var today = ApiDateTime.today();
		var staleThreshold = ApiDateTime.now().minusDays(3);
		List<Task> projectTasks = projectIds.isEmpty() ? List.of() : taskMapper.selectList(
			Wrappers.<Task>lambdaQuery().in(Task::getProjectId, projectIds));
		List<Task> tasks = projectTasks.stream()
			.filter(task -> currentUser.getId().equals(task.getAssigneeId()))
			.toList();
		Map<TaskStatus, Long> tasksByStatus = new EnumMap<>(TaskStatus.class);
		for (TaskStatus status : TaskStatus.values()) {
			tasksByStatus.put(status, 0L);
		}
		long overdueTasks = 0;
		long dueSoonTasks = 0;
		long myOverdueTasks = 0;
		long myBlockedTasks = 0;
		long myTodayDueTasks = 0;
		long staleBlockedTasks = 0;
		for (Task task : tasks) {
			tasksByStatus.compute(task.getStatus(), (status, count) -> count + 1);
			boolean incomplete = task.getStatus() != TaskStatus.COMPLETED
				&& task.getStatus() != TaskStatus.CANCELLED;
			boolean assignedToCurrentUser = currentUser.getId().equals(task.getAssigneeId());
			boolean overdue = incomplete && task.getDueDate() != null && task.getDueDate().isBefore(today);
			if (overdue) overdueTasks++;
			if (overdue && assignedToCurrentUser) myOverdueTasks++;
			if (incomplete && task.getDueDate() != null && task.getDueDate().isAfter(today)
				&& !task.getDueDate().isAfter(today.plusDays(7))) {
				dueSoonTasks++;
			}
			if (task.getStatus() == TaskStatus.BLOCKED && assignedToCurrentUser) myBlockedTasks++;
			if (incomplete && assignedToCurrentUser && today.equals(task.getDueDate())) myTodayDueTasks++;
			if (task.getStatus() == TaskStatus.BLOCKED && task.getUpdatedAt() != null
				&& task.getUpdatedAt().isBefore(staleThreshold)) {
				staleBlockedTasks++;
			}
		}
		long taskCount = tasks.size();

		return new OverviewResponse(
			new OverviewResponse.Counts(
				projects.size(),
				projects.stream().filter(project -> project.getStatus() == ProjectStatus.IN_PROGRESS).count(),
				taskCount,
				tasksByStatus.get(TaskStatus.COMPLETED),
				overdueTasks,
				tasksByStatus.get(TaskStatus.BLOCKED),
				dueSoonTasks,
				myOverdueTasks,
				myBlockedTasks,
				myTodayDueTasks,
				staleBlockedTasks,
				summaryMapper.countByProjectIds(projectIds),
				projectMemberMapper.countDistinctUsersByProjectIds(projectIds)),
			java.util.Arrays.stream(TaskStatus.values())
				.map(status -> new OverviewResponse.StatusCount(status, tasksByStatus.get(status)))
				.toList(),
			projectHealth(currentUser, projects, projectTasks, today),
			recentActivities(currentUser, projects, projectIds));
	}

	private List<OverviewResponse.ProjectHealth> projectHealth(
		User currentUser,
		List<Project> projects,
		List<Task> tasks,
		java.time.LocalDate today) {
		Map<Long, List<Task>> tasksByProject = tasks.stream()
			.collect(Collectors.groupingBy(Task::getProjectId));
		return projects.stream().map(project -> {
			List<Task> projectTasks = tasksByProject.getOrDefault(project.getId(), List.of());
			long completed = projectTasks.stream()
				.filter(task -> task.getStatus() == TaskStatus.COMPLETED).count();
			long overdue = projectTasks.stream().filter(task -> task.getDueDate() != null
				&& task.getDueDate().isBefore(today)
				&& task.getStatus() != TaskStatus.COMPLETED
				&& task.getStatus() != TaskStatus.CANCELLED).count();
			long blocked = projectTasks.stream()
				.filter(task -> task.getStatus() == TaskStatus.BLOCKED).count();
			return new OverviewResponse.ProjectHealth(
				project.getId().toString(), project.getName(),
				currentUser.getId().equals(project.getOwnerId()), project.getStatus(), project.getEndDate(),
				projectTasks.size(), completed, overdue, blocked);
		}).sorted(Comparator.comparing(OverviewResponse.ProjectHealth::isOwner).reversed()
			.thenComparing(Comparator.comparingLong(OverviewResponse.ProjectHealth::overdueTasks).reversed())
			.thenComparing(Comparator.comparingLong(OverviewResponse.ProjectHealth::blockedTasks).reversed())
			.thenComparing(OverviewResponse.ProjectHealth::name))
			.toList();
	}

	private List<OverviewResponse.Activity> recentActivities(
		User currentUser,
		List<Project> projects,
		List<Long> projectIds) {
		return recentActivitiesForUserId(currentUser.getId(), projects, projectIds);
	}

	List<OverviewResponse.Activity> recentActivitiesForAdmin(
		List<Project> projects,
		List<Long> projectIds) {
		return recentActivitiesForUserId(null, projects, projectIds);
	}

	private List<OverviewResponse.Activity> recentActivitiesForUserId(
		Long currentUserId,
		List<Project> projects,
		List<Long> projectIds) {
		List<OverviewResponse.Activity> activities = new ArrayList<>();
		projects.stream()
			.filter(project -> currentUserId == null || currentUserId.equals(project.getOwnerId()))
			.sorted(Comparator.comparing(Project::getCreatedAt).reversed()
				.thenComparing(Project::getId, Comparator.reverseOrder()))
			.limit(10)
			.forEach(project -> activities.add(new OverviewResponse.Activity(
			"PROJECT_CREATED",
			project.getId().toString(),
			"创建项目「" + project.getName() + "」",
			ApiDateTime.toInstant(project.getCreatedAt()))));
		(currentUserId == null
			? taskMapper.selectRecentByProjectIds(projectIds)
			: taskMapper.selectRecentByProjectIdsAndUserId(projectIds, currentUserId))
			.forEach(task -> activities.add(
			new OverviewResponse.Activity(
			"TASK_CREATED",
			task.getId().toString(),
			"创建任务「" + task.getTitle() + "」",
			ApiDateTime.toInstant(task.getCreatedAt()))));
		(currentUserId == null
			? summaryMapper.selectRecentByProjectIds(projectIds)
			: summaryMapper.selectRecentByProjectIdsAndCreatorId(projectIds, currentUserId))
			.forEach(summary -> activities.add(
			new OverviewResponse.Activity(
			"SUMMARY_CREATED",
			summary.getId().toString(),
			"创建项目总结",
			ApiDateTime.toInstant(summary.getCreatedAt()))));

		List<TaskLogMapper.RecentActivity> taskLogs = taskLogMapper.selectRecentActivities(
			projectIds, currentUserId);
		if (!taskLogs.isEmpty()) {
			Map<Long, User> operators = users(taskLogs.stream()
				.map(TaskLogMapper.RecentActivity::getOperatorId).distinct().toList());
			for (TaskLogMapper.RecentActivity taskLog : taskLogs) {
				User operator = operators.get(taskLog.getOperatorId());
				activities.add(new OverviewResponse.Activity(
					"TASK_PROGRESS_ADDED",
					taskLog.getId().toString(),
					operator.getDisplayName() + " 将「" + taskLog.getTaskTitle()
						+ "」进度更新至 " + taskLog.getProgressPercent() + "%",
					ApiDateTime.toInstant(taskLog.getCreatedAt())));
			}
		}
		return activities.stream()
			.sorted(Comparator.comparing(OverviewResponse.Activity::occurredAt).reversed()
				.thenComparing(OverviewResponse.Activity::type)
				.thenComparing(OverviewResponse.Activity::resourceId, Comparator.reverseOrder()))
			.limit(10)
			.toList();
	}

	private Map<Long, User> users(List<Long> userIds) {
		if (userIds.isEmpty()) {
			return Map.of();
		}
		return userMapper.selectByIds(userIds).stream()
			.collect(Collectors.toMap(User::getId, Function.identity()));
	}

}
