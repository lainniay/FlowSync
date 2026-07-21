package hgc.flowsync.common.error;

import java.util.List;
import java.util.Objects;

public final class BusinessException extends RuntimeException {

	private final ErrorCode code;
	private final List<FieldViolation> errors;

	public BusinessException(ErrorCode code) {
		super(Objects.requireNonNull(code, "code").detail());
		this.code = code;
		this.errors = List.of();
	}

	public BusinessException(ErrorCode code, String field) {
		super(Objects.requireNonNull(code, "code").detail());
		this.code = code;
		this.errors = List.of(new FieldViolation(
			Objects.requireNonNull(field, "field"), code.name(), code.detail()));
	}

	public ErrorCode code() {
		return code;
	}

	public List<FieldViolation> errors() {
		return errors;
	}
}
