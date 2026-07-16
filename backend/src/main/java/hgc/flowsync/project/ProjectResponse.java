package hgc.flowsync.project;

import java.time.Instant;
import java.time.LocalDate;

import hgc.flowsync.common.time.ApiDateTime;
import hgc.flowsync.user.User;
import hgc.flowsync.user.UserBrief;

public record ProjectResponse(
	String id,
	UserBrief owner,
	String name,
	String description,
	ProjectStatus status,
	Priority priority,
	LocalDate startDate,
	LocalDate endDate,
	Instant archivedAt,
	long memberCount,
	TaskStats taskStats,
	Instant createdAt,
	Instant updatedAt) {

	public static ProjectResponse from(
		Project project,
		User owner,
		long memberCount,
		long totalTasks,
		long completedTasks) {
		return new ProjectResponse(
			project.getId().toString(),
			UserBrief.from(owner),
			project.getName(),
			project.getDescription(),
			project.getStatus(),
			project.getPriority(),
			project.getStartDate(),
			project.getEndDate(),
			ApiDateTime.toInstant(project.getArchivedAt()),
			memberCount,
			new TaskStats(totalTasks, completedTasks),
			ApiDateTime.toInstant(project.getCreatedAt()),
			ApiDateTime.toInstant(project.getUpdatedAt()));
	}

	public record TaskStats(long total, long completed) {
	}
}
