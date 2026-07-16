package hgc.flowsync.common.error;

import java.util.Objects;

public final class BusinessException extends RuntimeException {

	private final ErrorCode code;

	public BusinessException(ErrorCode code) {
		super(Objects.requireNonNull(code, "code").detail());
		this.code = code;
	}

	public ErrorCode code() {
		return code;
	}
}
