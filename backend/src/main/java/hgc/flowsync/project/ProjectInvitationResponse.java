package hgc.flowsync.project;

import java.time.Instant;

import hgc.flowsync.common.time.ApiDateTime;
import hgc.flowsync.user.User;
import hgc.flowsync.user.UserBrief;

public record ProjectInvitationResponse(
	String id,
	ProjectBrief project,
	UserBrief invitee,
	UserBrief invitedBy,
	InvitationStatus status,
	Instant createdAt,
	Instant respondedAt) {

	public static ProjectInvitationResponse from(
		ProjectInvitation invitation,
		Project project,
		User invitee,
		User inviter) {
		return new ProjectInvitationResponse(
			invitation.getId().toString(),
			new ProjectBrief(project.getId().toString(), project.getName()),
			UserBrief.from(invitee),
			UserBrief.from(inviter),
			invitation.getStatus(),
			ApiDateTime.toInstant(invitation.getCreatedAt()),
			ApiDateTime.toInstant(invitation.getRespondedAt()));
	}

	public record ProjectBrief(String id, String name) {
	}
}
