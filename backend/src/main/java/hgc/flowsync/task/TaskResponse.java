package hgc.flowsync.task;

import java.time.Instant;
import java.time.LocalDate;

import hgc.flowsync.common.time.ApiDateTime;
import hgc.flowsync.project.Priority;
import hgc.flowsync.user.User;
import hgc.flowsync.user.UserBrief;

public record TaskResponse(
	String id,
	String projectId,
	String parentId,
	UserBrief assignee,
	UserBrief creator,
	String title,
	String description,
	TaskStatus status,
	Priority priority,
	int progressPercent,
	LocalDate dueDate,
	Instant createdAt,
	Instant updatedAt) {

	public static TaskResponse from(Task task, User creator, User assignee, int progressPercent) {
		return new TaskResponse(
			task.getId().toString(),
			task.getProjectId().toString(),
			task.getParentId() == null ? null : task.getParentId().toString(),
			assignee == null ? null : UserBrief.from(assignee),
			UserBrief.from(creator),
			task.getTitle(),
			task.getDescription(),
			task.getStatus(),
			task.getPriority(),
			progressPercent,
			task.getDueDate(),
			ApiDateTime.toInstant(task.getCreatedAt()),
			ApiDateTime.toInstant(task.getUpdatedAt()));
	}
}
