package hgc.flowsync.user;

import java.time.Instant;

import hgc.flowsync.common.time.ApiDateTime;

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

	public static UserResponse from(User user) {
		return new UserResponse(
			user.getId().toString(),
			user.getUsername(),
			user.getDisplayName(),
			user.getPhone(),
			user.getEmail(),
			user.getSystemRole(),
			user.isActive(),
			ApiDateTime.toInstant(user.getCreatedAt()),
			ApiDateTime.toInstant(user.getUpdatedAt()));
	}
}
