package hgc.flowsync.task;

import java.time.Instant;

import hgc.flowsync.common.time.ApiDateTime;
import hgc.flowsync.user.User;
import hgc.flowsync.user.UserBrief;

public record TaskLogResponse(
	String id,
	String taskId,
	UserBrief operator,
	int progressPercent,
	String content,
	Instant createdAt) {

	public static TaskLogResponse from(TaskLog taskLog, User operator) {
		return new TaskLogResponse(
			taskLog.getId().toString(),
			taskLog.getTaskId().toString(),
			UserBrief.from(operator),
			taskLog.getProgressPercent(),
			taskLog.getContent(),
			ApiDateTime.toInstant(taskLog.getCreatedAt()));
	}
}
