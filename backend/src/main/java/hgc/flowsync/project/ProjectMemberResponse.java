package hgc.flowsync.project;

import java.time.Instant;

import hgc.flowsync.common.time.ApiDateTime;
import hgc.flowsync.user.User;
import hgc.flowsync.user.UserBrief;

public record ProjectMemberResponse(UserBrief user, Instant joinedAt) {

	public static ProjectMemberResponse from(ProjectMember member, User user) {
		return new ProjectMemberResponse(UserBrief.from(user), ApiDateTime.toInstant(member.getJoinedAt()));
	}
}
