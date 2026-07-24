package hgc.flowsync.overview;

import java.time.LocalDate;
import java.util.List;

import hgc.flowsync.project.ProjectStatus;
import hgc.flowsync.task.TaskStatus;

public record AdminOverviewResponse(
	Counts counts,
	List<ProjectStatusCount> projectsByStatus,
	List<OverviewResponse.StatusCount> tasksByStatus,
	List<FocusProject> focusProjects,
	List<OverviewResponse.Activity> recentActivities) {

	public record Counts(
		long activeUsers,
		long inactiveUsers,
		long users,
		long admins,
		long projects,
		long inProgressProjects,
		long tasks,
		long completedTasks,
		long overdueTasks,
		long overdueProjects) {
	}

	public record ProjectStatusCount(ProjectStatus status, long count) {
	}

	public record FocusProject(
		String id,
		String name,
		String ownerId,
		String ownerName,
		LocalDate endDate,
		long tasks,
		long completedTasks,
		long overdueTasks,
		long blockedTasks) {
	}
}
