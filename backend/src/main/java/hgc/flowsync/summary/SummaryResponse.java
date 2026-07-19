package hgc.flowsync.summary;

import java.time.Instant;

import hgc.flowsync.common.time.ApiDateTime;
import hgc.flowsync.user.User;
import hgc.flowsync.user.UserBrief;

public record SummaryResponse(
	String id,
	String projectId,
	String taskId,
	UserBrief createdBy,
	SummaryType type,
	String content,
	Instant createdAt,
	Instant updatedAt) {

	public static SummaryResponse from(Summary summary, User creator) {
		return new SummaryResponse(
			summary.getId().toString(),
			summary.getProjectId().toString(),
			summary.getTaskId() == null ? null : summary.getTaskId().toString(),
			UserBrief.from(creator),
			summary.getType(),
			summary.getContent(),
			ApiDateTime.toInstant(summary.getCreatedAt()),
			ApiDateTime.toInstant(summary.getUpdatedAt()));
	}
}
