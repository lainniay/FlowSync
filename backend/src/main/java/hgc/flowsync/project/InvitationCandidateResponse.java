package hgc.flowsync.project;

import hgc.flowsync.user.User;

public record InvitationCandidateResponse(String id, String displayName, String username) {

	static InvitationCandidateResponse from(User user) {
		return new InvitationCandidateResponse(
			user.getId().toString(),
			user.getDisplayName(),
			user.getUsername());
	}
}
