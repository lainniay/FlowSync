package hgc.flowsync.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class ApiDateTimeTests {

	@Test
	void convertsDatabaseTimeToUtcAndPreservesNull() {
		assertThat(ApiDateTime.toInstant(LocalDateTime.of(2026, 7, 16, 16, 30)))
			.isEqualTo(Instant.parse("2026-07-16T08:30:00Z"));
		assertThat(ApiDateTime.toInstant(null)).isNull();
	}

	@Test
	void createsDatabaseDatesInShanghai() {
		ZoneId shanghai = ZoneId.of("Asia/Shanghai");
		LocalDateTime before = LocalDateTime.now(shanghai);
		LocalDateTime actual = ApiDateTime.now();
		LocalDateTime after = LocalDateTime.now(shanghai);

		assertThat(actual).isBetween(before, after);
		assertThat(ApiDateTime.today()).isEqualTo(LocalDate.now(shanghai));
	}
}
