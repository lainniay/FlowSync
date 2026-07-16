package hgc.flowsync.user;

import java.time.Instant;
import java.time.ZoneId;

public record UserResponse(
	String id,
	String username,
	String displayName,
	String phone,
	String email,
	SystemRole systemRole,
	boolean active,
	Instant createdAt,
	Instant updatedAt) {

	private static final ZoneId DATABASE_ZONE = ZoneId.of("Asia/Shanghai");

	public static UserResponse from(User user) {
		return new UserResponse(
			user.getId().toString(),
			user.getUsername(),
			user.getDisplayName(),
			user.getPhone(),
			user.getEmail(),
			user.getSystemRole(),
			user.isActive(),
			user.getCreatedAt().atZone(DATABASE_ZONE).toInstant(),
			user.getUpdatedAt().atZone(DATABASE_ZONE).toInstant());
	}
}
