package hgc.flowsync.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class ApiDateTimeTests {

	@Test
	void convertsDatabaseTimeToUtcAndPreservesNull() {
		assertThat(ApiDateTime.toInstant(LocalDateTime.of(2026, 7, 16, 16, 30)))
			.isEqualTo(Instant.parse("2026-07-16T08:30:00Z"));
		assertThat(ApiDateTime.toInstant(null)).isNull();
	}
}
