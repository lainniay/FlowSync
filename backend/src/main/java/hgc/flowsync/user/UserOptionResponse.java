package hgc.flowsync.user;

public record UserOptionResponse(String id, String username) {

	static UserOptionResponse from(User user) {
		return new UserOptionResponse(user.getId().toString(), user.getUsername());
	}
}
