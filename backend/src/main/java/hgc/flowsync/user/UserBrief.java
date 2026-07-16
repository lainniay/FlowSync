package hgc.flowsync.user;

public record UserBrief(String id, String displayName) {

	public static UserBrief from(User user) {
		return new UserBrief(user.getId().toString(), user.getDisplayName());
	}
}
