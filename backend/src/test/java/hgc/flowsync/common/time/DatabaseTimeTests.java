package hgc.flowsync.common.time;

import java.time.Instant;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
class DatabaseTimeTests {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void databaseSessionAndApiConversionUseShanghaiTime() {
		assertThat(jdbcTemplate.queryForObject("SELECT @@session.time_zone", String.class))
			.isEqualTo("+08:00");
		LocalDateTime databaseNow = jdbcTemplate.queryForObject(
			"SELECT CURRENT_TIMESTAMP", LocalDateTime.class);

		assertThat(ApiDateTime.toInstant(databaseNow))
			.isCloseTo(Instant.now(), within(5, java.time.temporal.ChronoUnit.SECONDS));
	}
}
