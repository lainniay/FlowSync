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
import hgc.flowsync.summary.SummaryMapper;
import hgc.flowsync.task.TaskLogMapper;
import hgc.flowsync.task.TaskMapper;
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
			return response(projects, List.of());
		}

		List<Long> projectIds = projects.stream().map(Project::getId).toList();
		return response(projects, projectIds);
	}

	private List<Project> visibleProjects(User currentUser, Long requestedProjectId) {
		if (projectAccessService.isAdmin(currentUser)) {
			return projectMapper.selectList(Wrappers.<Project>lambdaQuery()
				.eq(requestedProjectId != null, Project::getId, requestedProjectId));
		}
		List<Long> projectIds = projectMemberMapper.selectList(Wrappers.<ProjectMember>lambdaQuery()
			.select(ProjectMember::getProjectId)
			.eq(ProjectMember::getUserId, currentUser.getId())).stream()
			.map(ProjectMember::getProjectId)
			.filter(id -> requestedProjectId == null || requestedProjectId.equals(id))
			.toList();
		return projectIds.isEmpty() ? List.of() : projectMapper.selectByIds(projectIds);
	}

	private OverviewResponse response(
		List<Project> projects,
		List<Long> projectIds) {
		Map<TaskStatus, Long> tasksByStatus = new EnumMap<>(TaskStatus.class);
		for (TaskStatus status : TaskStatus.values()) {
			tasksByStatus.put(status, 0L);
		}
		long overdueTasks = 0;
		for (TaskMapper.OverviewTaskStats stats
			: taskMapper.selectOverviewStats(projectIds, ApiDateTime.today())) {
			tasksByStatus.put(stats.getStatus(), stats.getTaskCount());
			overdueTasks += stats.getOverdueCount();
		}
		long taskCount = tasksByStatus.values().stream().mapToLong(Long::longValue).sum();

		return new OverviewResponse(
			new OverviewResponse.Counts(
				projects.size(),
				taskCount,
				tasksByStatus.get(TaskStatus.COMPLETED),
				overdueTasks,
				summaryMapper.countByProjectIds(projectIds),
				projectMemberMapper.countDistinctUsersByProjectIds(projectIds)),
			java.util.Arrays.stream(TaskStatus.values())
				.map(status -> new OverviewResponse.StatusCount(status, tasksByStatus.get(status)))
				.toList(),
			recentActivities(projects, projectIds));
	}

	private List<OverviewResponse.Activity> recentActivities(
		List<Project> projects,
		List<Long> projectIds) {
		List<OverviewResponse.Activity> activities = new ArrayList<>();
		projects.stream()
			.sorted(Comparator.comparing(Project::getCreatedAt).reversed()
				.thenComparing(Project::getId, Comparator.reverseOrder()))
			.limit(10)
			.forEach(project -> activities.add(new OverviewResponse.Activity(
			"PROJECT_CREATED",
			project.getId().toString(),
			"Created project \"" + project.getName() + "\".",
			ApiDateTime.toInstant(project.getCreatedAt()))));
		taskMapper.selectRecentByProjectIds(projectIds).forEach(task -> activities.add(
			new OverviewResponse.Activity(
			"TASK_CREATED",
			task.getId().toString(),
			"Created task \"" + task.getTitle() + "\".",
			ApiDateTime.toInstant(task.getCreatedAt()))));
		summaryMapper.selectRecentByProjectIds(projectIds).forEach(summary -> activities.add(
			new OverviewResponse.Activity(
			"SUMMARY_CREATED",
			summary.getId().toString(),
			"Created project summary.",
			ApiDateTime.toInstant(summary.getCreatedAt()))));

		List<TaskLogMapper.RecentActivity> taskLogs = taskLogMapper.selectRecentActivities(projectIds);
		if (!taskLogs.isEmpty()) {
			Map<Long, User> operators = users(taskLogs.stream()
				.map(TaskLogMapper.RecentActivity::getOperatorId).distinct().toList());
			for (TaskLogMapper.RecentActivity taskLog : taskLogs) {
				User operator = operators.get(taskLog.getOperatorId());
				activities.add(new OverviewResponse.Activity(
					"TASK_PROGRESS_ADDED",
					taskLog.getId().toString(),
					operator.getDisplayName() + " updated \"" + taskLog.getTaskTitle()
						+ "\" progress to " + taskLog.getProgressPercent() + "%.",
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
