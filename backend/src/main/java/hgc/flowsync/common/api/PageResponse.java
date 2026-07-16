package hgc.flowsync.common.api;

import java.util.List;

public record PageResponse<T>(
	List<T> items,
	int page,
	int size,
	long totalElements,
	long totalPages) {

	public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalElements) {
		return new PageResponse<>(items, page, size, totalElements, Math.ceilDiv(totalElements, size));
	}
}
