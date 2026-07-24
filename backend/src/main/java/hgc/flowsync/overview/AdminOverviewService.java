package hgc.flowsync.overview;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import hgc.flowsync.common.time.ApiDateTime;
import hgc.flowsync.project.Project;
import hgc.flowsync.project.ProjectMapper;
import hgc.flowsync.project.ProjectStatus;
import hgc.flowsync.task.Task;
import hgc.flowsync.task.TaskMapper;
import hgc.flowsync.task.TaskStatus;
import hgc.flowsync.user.SystemRole;
import hgc.flowsync.user.User;
import hgc.flowsync.user.UserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminOverviewService {

	private final UserMapper userMapper;
	private final ProjectMapper projectMapper;
	private final TaskMapper taskMapper;
	private final OverviewService overviewService;

	public AdminOverviewService(
		UserMapper userMapper,
		ProjectMapper projectMapper,
		TaskMapper taskMapper,
		OverviewService overviewService) {
		this.userMapper = userMapper;
		this.projectMapper = projectMapper;
		this.taskMapper = taskMapper;
		this.overviewService = overviewService;
	}

	@Transactional(readOnly = true)
	public AdminOverviewResponse overview() {
		List<User> users = userMapper.selectList(null);
		List<Project> projects = projectMapper.selectList(Wrappers.<Project>lambdaQuery()
			.isNull(Project::getArchivedAt));
		List<Long> projectIds = projects.stream().map(Project::getId).toList();
		List<Task> tasks = projectIds.isEmpty() ? List.of() : taskMapper.selectList(
			Wrappers.<Task>lambdaQuery().in(Task::getProjectId, projectIds));
		LocalDate today = ApiDateTime.today();

		Map<ProjectStatus, Long> projectsByStatus = new EnumMap<>(ProjectStatus.class);
		for (ProjectStatus status : ProjectStatus.values()) projectsByStatus.put(status, 0L);
		for (Project project : projects) projectsByStatus.compute(project.getStatus(), (key, count) -> count + 1);

		Map<TaskStatus, Long> tasksByStatus = new EnumMap<>(TaskStatus.class);
		for (TaskStatus status : TaskStatus.values()) tasksByStatus.put(status, 0L);
		for (Task task : tasks) tasksByStatus.compute(task.getStatus(), (key, count) -> count + 1);

		Map<Long, List<Task>> tasksByProject = tasks.stream()
			.collect(Collectors.groupingBy(Task::getProjectId));
		List<Long> ownerIds = projects.stream().map(Project::getOwnerId).distinct().toList();
		Map<Long, User> owners = ownerIds.isEmpty() ? Map.of() : userMapper.selectByIds(ownerIds).stream()
			.collect(Collectors.toMap(User::getId, Function.identity()));
		List<AdminOverviewResponse.FocusProject> focusProjects = projects.stream()
			.map(project -> focusProject(project, tasksByProject.getOrDefault(project.getId(), List.of()),
				owners.get(project.getOwnerId()), today))
			.sorted(Comparator.comparingLong(AdminOverviewResponse.FocusProject::overdueTasks).reversed()
				.thenComparing(Comparator.comparingLong(
					AdminOverviewResponse.FocusProject::blockedTasks).reversed())
				.thenComparing(AdminOverviewResponse.FocusProject::endDate,
					Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(AdminOverviewResponse.FocusProject::name))
			.limit(10)
			.toList();
		List<ProjectTaskRisk> projectRisks = focusProjects(projects, tasksByProject, today);
		long overdueTasks = projectRisks.stream()
			.mapToLong(ProjectTaskRisk::overdueTasks).sum();
		long overdueProjects = projectRisks.stream()
			.filter(project -> project.overdueTasks() > 0).count();

		return new AdminOverviewResponse(
			new AdminOverviewResponse.Counts(
				users.stream().filter(User::isActive).count(),
				users.stream().filter(user -> !user.isActive()).count(),
				users.stream().filter(user -> user.getSystemRole() == SystemRole.USER).count(),
				users.stream().filter(user -> user.getSystemRole() == SystemRole.ADMIN).count(),
				projects.size(), projectsByStatus.get(ProjectStatus.IN_PROGRESS), tasks.size(),
				tasksByStatus.get(TaskStatus.COMPLETED), overdueTasks, overdueProjects),
			java.util.Arrays.stream(ProjectStatus.values())
				.map(status -> new AdminOverviewResponse.ProjectStatusCount(
					status, projectsByStatus.get(status))).toList(),
			java.util.Arrays.stream(TaskStatus.values())
				.map(status -> new OverviewResponse.StatusCount(status, tasksByStatus.get(status))).toList(),
			focusProjects,
			overviewService.recentActivitiesForAdmin(projects, projectIds));
	}

	private static AdminOverviewResponse.FocusProject focusProject(
		Project project,
		List<Task> tasks,
		User owner,
		LocalDate today) {
		ProjectTaskRisk risk = risk(tasks, today);
		return new AdminOverviewResponse.FocusProject(
			project.getId().toString(), project.getName(), owner.getId().toString(), owner.getDisplayName(),
			project.getEndDate(), tasks.size(), risk.completedTasks(), risk.overdueTasks(), risk.blockedTasks());
	}

	private static List<ProjectTaskRisk> focusProjects(
		List<Project> projects,
		Map<Long, List<Task>> tasksByProject,
		LocalDate today) {
		return projects.stream()
			.map(project -> risk(tasksByProject.getOrDefault(project.getId(), List.of()), today))
			.toList();
	}

	private static ProjectTaskRisk risk(List<Task> tasks, LocalDate today) {
		long completed = tasks.stream().filter(task -> task.getStatus() == TaskStatus.COMPLETED).count();
		long overdue = tasks.stream().filter(task -> task.getDueDate() != null
			&& task.getDueDate().isBefore(today)
			&& task.getStatus() != TaskStatus.COMPLETED
			&& task.getStatus() != TaskStatus.CANCELLED).count();
		long blocked = tasks.stream().filter(task -> task.getStatus() == TaskStatus.BLOCKED).count();
		return new ProjectTaskRisk(completed, overdue, blocked);
	}

	private record ProjectTaskRisk(long completedTasks, long overdueTasks, long blockedTasks) {
	}
}
