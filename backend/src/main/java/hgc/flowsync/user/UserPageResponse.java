package hgc.flowsync.user;

import java.util.List;

public record UserPageResponse(
	List<UserResponse> items,
	int page,
	int size,
	long totalElements,
	long totalPages) {
}
