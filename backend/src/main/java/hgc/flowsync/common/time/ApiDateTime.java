package hgc.flowsync.common.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

public final class ApiDateTime {

	private static final ZoneId DATABASE_ZONE = ZoneId.of("Asia/Shanghai");

	private ApiDateTime() {
	}

	public static Instant toInstant(LocalDateTime value) {
		return value == null ? null : value.atZone(DATABASE_ZONE).toInstant();
	}

	public static LocalDateTime now() {
		return LocalDateTime.now(DATABASE_ZONE);
	}

	public static LocalDate today() {
		return LocalDate.now(DATABASE_ZONE);
	}
}
