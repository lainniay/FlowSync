package hgc.flowsync.common.error;

import java.net.URI;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.databind.JsonMappingException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public final class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
	private final ProblemDetailResponseWriter problemWriter;

	public GlobalExceptionHandler(ProblemDetailResponseWriter problemWriter) {
		this.problemWriter = problemWriter;
	}

	@ExceptionHandler(BusinessException.class)
	ResponseEntity<Object> handleBusinessException(BusinessException exception, WebRequest request) {
		ErrorCode code = exception.code();
		return response(code, code.status().getReasonPhrase(), code.detail(), exception.errors(), request);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException exception, WebRequest request) {
		List<FieldViolation> errors = exception.getConstraintViolations().stream()
			.map(violation -> new FieldViolation(
				constraintField(violation.getPropertyPath().toString()),
				violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName(),
				violation.getMessage()))
			.toList();
		return validationResponse(errors, request);
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<Object> handleAccessDenied(AccessDeniedException exception, WebRequest request) {
		return response(
			ErrorCode.FORBIDDEN,
			"Forbidden",
			"You do not have permission to perform this action.",
			List.of(),
			request);
	}

	@ExceptionHandler(DataAccessResourceFailureException.class)
	ResponseEntity<Object> handleServiceUnavailable(
		DataAccessResourceFailureException exception,
		WebRequest request) {
		log.error("Data service unavailable for {}", instance(request), exception);
		return response(
			ErrorCode.SERVICE_UNAVAILABLE,
			"Service unavailable",
			"The service is temporarily unavailable.",
			List.of(),
			request);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<Object> handleUnexpectedException(Exception exception, WebRequest request) {
		log.error("Unhandled exception for {}", instance(request), exception);
		return response(
			ErrorCode.INTERNAL_SERVER_ERROR,
			"Internal server error",
			"An unexpected error occurred.",
			List.of(),
			request);
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(
		HttpMessageNotReadableException exception,
		HttpHeaders headers,
		HttpStatusCode status,
		WebRequest request) {
		JsonMappingException mappingException = cause(exception, JsonMappingException.class);
		if (mappingException != null && !mappingException.getPath().isEmpty()) {
			String field = jsonPath(mappingException.getPath());
			return validationResponse(List.of(new FieldViolation(
				field,
				"Invalid",
				field + " has an invalid value")), request);
		}
		return response(
			ErrorCode.BAD_REQUEST,
			"Bad request",
			"Request body is malformed or must be a JSON object.",
			List.of(),
			request);
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(
		MethodArgumentNotValidException exception,
		HttpHeaders headers,
		HttpStatusCode status,
		WebRequest request) {
		List<FieldViolation> errors = exception.getBindingResult().getFieldErrors().stream()
			.map(GlobalExceptionHandler::fieldViolation)
			.toList();
		return validationResponse(errors, request);
	}

	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(
		HandlerMethodValidationException exception,
		HttpHeaders headers,
		HttpStatusCode status,
		WebRequest request) {
		List<FieldViolation> errors = exception.getParameterValidationResults().stream()
			.flatMap(result -> result.getResolvableErrors().stream()
				.map(error -> new FieldViolation(
					parameterName(result),
					validationCode(error),
					message(error))))
			.toList();
		return validationResponse(errors, request);
	}

	@Override
	protected ResponseEntity<Object> handleMissingServletRequestParameter(
		MissingServletRequestParameterException exception,
		HttpHeaders headers,
		HttpStatusCode status,
		WebRequest request) {
		return validationResponse(List.of(new FieldViolation(
			exception.getParameterName(),
			"NotNull",
			exception.getParameterName() + " is required")), request);
	}

	@Override
	protected ResponseEntity<Object> handleTypeMismatch(
		TypeMismatchException exception,
		HttpHeaders headers,
		HttpStatusCode status,
		WebRequest request) {
		String field = exception instanceof MethodArgumentTypeMismatchException argument
			? argument.getName()
			: Objects.requireNonNullElse(exception.getPropertyName(), "value");
		return validationResponse(List.of(new FieldViolation(
			field,
			exception.getErrorCode(),
			field + " has an invalid value")), request);
	}

	@Override
	protected ResponseEntity<Object> handleNoHandlerFoundException(
		NoHandlerFoundException exception,
		HttpHeaders headers,
		HttpStatusCode status,
		WebRequest request) {
		return notFoundResponse(request);
	}

	@Override
	protected ResponseEntity<Object> handleNoResourceFoundException(
		NoResourceFoundException exception,
		HttpHeaders headers,
		HttpStatusCode status,
		WebRequest request) {
		return notFoundResponse(request);
	}

	@Override
	protected ResponseEntity<Object> createResponseEntity(
		Object body,
		HttpHeaders headers,
		HttpStatusCode status,
		WebRequest request) {
		ErrorCode code = frameworkErrorCode(status);
		if (code == null) {
			code = ErrorCode.INTERNAL_SERVER_ERROR;
			status = HttpStatus.INTERNAL_SERVER_ERROR;
		}
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, code.detail());
		problem.setTitle(code.status().getReasonPhrase());
		problemWriter.enrich(problem, code, List.of(), instance(request));
		return super.createResponseEntity(problem, headers, status, request);
	}

	private ResponseEntity<Object> validationResponse(List<FieldViolation> errors, WebRequest request) {
		return response(
			ErrorCode.VALIDATION_ERROR,
			"Validation failed",
			"One or more fields are invalid.",
			errors,
			request);
	}

	private ResponseEntity<Object> notFoundResponse(WebRequest request) {
		return response(
			ErrorCode.NOT_FOUND,
			"Not found",
			"The requested resource was not found.",
			List.of(),
			request);
	}

	private ResponseEntity<Object> response(
		ErrorCode code,
		String title,
		String detail,
		List<FieldViolation> errors,
		WebRequest request) {
		return problemWriter.response(code, title, detail, errors, instance(request));
	}

	private static String jsonPath(List<JsonMappingException.Reference> path) {
		StringBuilder field = new StringBuilder();
		for (JsonMappingException.Reference reference : path) {
			if (reference.getFieldName() != null) {
				if (!field.isEmpty()) {
					field.append('.');
				}
				field.append(reference.getFieldName());
			} else if (reference.getIndex() >= 0) {
				field.append('[').append(reference.getIndex()).append(']');
			}
		}
		return field.isEmpty() ? "body" : field.toString();
	}

	private static ErrorCode frameworkErrorCode(HttpStatusCode status) {
		return switch (status.value()) {
			case 400 -> ErrorCode.BAD_REQUEST;
			case 401 -> ErrorCode.UNAUTHORIZED;
			case 403 -> ErrorCode.FORBIDDEN;
			case 404 -> ErrorCode.NOT_FOUND;
			case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
			case 406 -> ErrorCode.NOT_ACCEPTABLE;
			case 413 -> ErrorCode.PAYLOAD_TOO_LARGE;
			case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
			case 422 -> ErrorCode.VALIDATION_ERROR;
			case 429 -> ErrorCode.RATE_LIMITED;
			case 500 -> ErrorCode.INTERNAL_SERVER_ERROR;
			case 502 -> ErrorCode.AI_PROVIDER_ERROR;
			case 503 -> ErrorCode.SERVICE_UNAVAILABLE;
			default -> null;
		};
	}

	private static <T extends Throwable> T cause(Throwable exception, Class<T> type) {
		Throwable cause = exception;
		while (cause != null) {
			if (type.isInstance(cause)) {
				return type.cast(cause);
			}
			cause = cause.getCause();
		}
		return null;
	}

	private static FieldViolation fieldViolation(FieldError error) {
		return new FieldViolation(
			error.getField(),
			Objects.requireNonNullElse(error.getCode(), "Invalid"),
			message(error));
	}

	private static String validationCode(MessageSourceResolvable error) {
		String[] codes = error.getCodes();
		return codes == null || codes.length == 0 ? "Invalid" : codes[codes.length - 1];
	}

	private static String message(MessageSourceResolvable error) {
		return Objects.requireNonNullElse(error.getDefaultMessage(), "Invalid value");
	}

	private static String parameterName(ParameterValidationResult result) {
		String name = result.getMethodParameter().getParameterName();
		if (name == null) {
			name = "arg" + result.getMethodParameter().getParameterIndex();
		}
		return result.getContainerIndex() == null ? name : name + "[" + result.getContainerIndex() + "]";
	}

	private static String constraintField(String path) {
		int separator = path.indexOf('.');
		return separator < 0 ? path : path.substring(separator + 1);
	}

	private static URI instance(WebRequest request) {
		return URI.create(((ServletWebRequest) request).getRequest().getRequestURI());
	}
}
