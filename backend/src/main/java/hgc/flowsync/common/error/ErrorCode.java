package hgc.flowsync.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

	BAD_REQUEST(HttpStatus.BAD_REQUEST, "The request is invalid."),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication is required."),
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Username or password is incorrect."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "You do not have permission to perform this action."),
	CSRF_INVALID(HttpStatus.FORBIDDEN, "The CSRF token is missing or invalid."),
	NOT_FOUND(HttpStatus.NOT_FOUND, "The requested resource was not found."),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "The request method is not supported."),
	NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "The requested representation is not available."),
	PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "The request body is too large."),
	UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "The request media type is not supported."),
	VALIDATION_ERROR(HttpStatus.UNPROCESSABLE_ENTITY, "One or more fields are invalid."),
	CURRENT_PASSWORD_INCORRECT(HttpStatus.UNPROCESSABLE_ENTITY, "The current password is incorrect."),
	USERNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "Username already exists."),
	LAST_ADMIN_REQUIRED(HttpStatus.CONFLICT, "At least one active administrator is required."),
	USER_OWNS_PROJECT(HttpStatus.CONFLICT, "The user still owns a project."),
	USER_HAS_ACTIVE_TASKS(HttpStatus.CONFLICT, "The user still has incomplete tasks."),
	USER_HAS_PROJECT_MEMBERSHIP(HttpStatus.CONFLICT, "The user still participates in a project."),
	PROJECT_ARCHIVED(HttpStatus.CONFLICT, "The project is archived."),
	PROJECT_NOT_ARCHIVED(HttpStatus.CONFLICT, "The project must be archived before deletion."),
	MEMBER_ALREADY_EXISTS(HttpStatus.CONFLICT, "The user is already a project member."),
	MEMBER_HAS_ACTIVE_TASKS(HttpStatus.CONFLICT, "The member still has incomplete tasks."),
	INVITATION_ALREADY_PENDING(HttpStatus.CONFLICT, "A pending invitation already exists."),
	INVALID_INVITATION_STATE(HttpStatus.CONFLICT, "The invitation cannot transition to that state."),
	RESOURCE_IN_USE(HttpStatus.CONFLICT, "The resource is still in use."),
	RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests."),
	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred."),
	AI_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY, "The AI provider request failed."),
	SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "The service is temporarily unavailable.");

	private final HttpStatus status;
	private final String detail;

	ErrorCode(HttpStatus status, String detail) {
		this.status = status;
		this.detail = detail;
	}

	public HttpStatus status() {
		return status;
	}

	public String detail() {
		return detail;
	}
}
