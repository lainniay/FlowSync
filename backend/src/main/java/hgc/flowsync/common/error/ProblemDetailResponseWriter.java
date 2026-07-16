package hgc.flowsync.common.error;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public final class ProblemDetailResponseWriter {

	private final ObjectMapper objectMapper;

	public ProblemDetailResponseWriter(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	ResponseEntity<Object> response(
		ErrorCode code,
		String title,
		String detail,
		List<FieldViolation> errors,
		URI instance) {
		return ResponseEntity.status(code.status())
			.contentType(MediaType.APPLICATION_PROBLEM_JSON)
			.body(problem(code, title, detail, errors, instance));
	}

	public void write(
		HttpServletRequest request,
		HttpServletResponse response,
		ErrorCode code,
		String title,
		String detail) throws IOException {
		response.setStatus(code.status().value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		objectMapper.writeValue(response.getOutputStream(), problem(
			code,
			title,
			detail,
			List.of(),
			URI.create(request.getRequestURI())));
	}

	private ProblemDetail problem(
		ErrorCode code,
		String title,
		String detail,
		List<FieldViolation> errors,
		URI instance) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
		problem.setTitle(title);
		return enrich(problem, code, errors, instance);
	}

	ProblemDetail enrich(
		ProblemDetail problem,
		ErrorCode code,
		List<FieldViolation> errors,
		URI instance) {
		problem.setInstance(instance);
		problem.setProperty("code", code.name());
		problem.setProperty("errors", errors);
		return problem;
	}
}
