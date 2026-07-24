package hgc.flowsync.overview;

import java.time.Instant;
import java.util.List;

import hgc.flowsync.task.TaskStatus;
import hgc.flowsync.project.ProjectStatus;
import java.time.LocalDate;

public record OverviewResponse(
	Counts counts,
	List<StatusCount> tasksByStatus,
	List<ProjectHealth> projectHealth,
	List<Activity> recentActivities) {

	public record Counts(
		long projects,
		long inProgressProjects,
		long tasks,
		long completedTasks,
		long overdueTasks,
		long blockedTasks,
		long dueSoonTasks,
		long myOverdueTasks,
		long myBlockedTasks,
		long myTodayDueTasks,
		long staleBlockedTasks,
		long summaries,
		long members) {
	}

	public record StatusCount(TaskStatus status, long count) {
	}

	public record ProjectHealth(
		String id,
		String name,
		boolean isOwner,
		ProjectStatus status,
		LocalDate endDate,
		long tasks,
		long completedTasks,
		long overdueTasks,
		long blockedTasks) {
	}

	public record Activity(String type, String resourceId, String summary, Instant occurredAt) {
	}
}
