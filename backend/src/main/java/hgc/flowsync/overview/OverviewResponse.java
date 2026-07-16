package hgc.flowsync.overview;

import java.time.Instant;
import java.util.List;

import hgc.flowsync.task.TaskStatus;

public record OverviewResponse(
	Counts counts,
	List<StatusCount> tasksByStatus,
	List<Activity> recentActivities) {

	public record Counts(
		long projects,
		long tasks,
		long completedTasks,
		long overdueTasks,
		long summaries,
		long members) {
	}

	public record StatusCount(TaskStatus status, long count) {
	}

	public record Activity(String type, String resourceId, String summary, Instant occurredAt) {
	}
}
