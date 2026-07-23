package hgc.flowsync.user;

public record PublicUserProfileResponse(
	String username,
	String displayName,
	String phone,
	String email,
	SystemRole systemRole,
	boolean active) {

	static PublicUserProfileResponse from(User user) {
		return new PublicUserProfileResponse(
			user.getUsername(),
			user.getDisplayName(),
			user.getPhone(),
			user.getEmail(),
			user.getSystemRole(),
			user.isActive());
	}
}
