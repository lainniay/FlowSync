package hgc.flowsync.task;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import hgc.flowsync.common.api.PageResponse;
import hgc.flowsync.common.error.BusinessException;
import hgc.flowsync.common.error.ErrorCode;
import hgc.flowsync.project.Priority;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TaskController {

	private final TaskService taskService;

	public TaskController(TaskService taskService) {
		this.taskService = taskService;
	}

	@GetMapping("/api/tasks")
	PageResponse<TaskResponse> tasks(
		Authentication authentication,
		@RequestParam(required = false) @Pattern(regexp = "[1-9]\\d*") String projectId,
		@RequestParam(required = false) @Pattern(regexp = "[1-9]\\d*") String assigneeId,
		@RequestParam(required = false) String status,
		@RequestParam(required = false) String priority,
		@RequestParam(required = false) @Pattern(regexp = "[1-9]\\d*") String parentId,
		@RequestParam(required = false) String dueBefore,
		@RequestParam(required = false) String dueAfter,
		@RequestParam(required = false) String q,
		@RequestParam(required = false) String page,
		@RequestParam(required = false) String size,
		@RequestParam(required = false) String sort) {
		return taskService.findAll(
			authentication,
			projectId,
			assigneeId,
			parseEnum(status, TaskStatus.class),
			parseEnum(priority, Priority.class),
			parentId,
			parseDate(dueBefore),
			parseDate(dueAfter),
			optionalNonBlank(q),
			parseInteger(page, 0, 0, Integer.MAX_VALUE),
			parseInteger(size, 20, 1, 100),
			parseSort(sort));
	}

	private static <E extends Enum<E>> E parseEnum(String value, Class<E> type) {
		if (value == null) {
			return null;
		}
		if (value.isBlank()) {
			throw validationError();
		}
		try {
			return Enum.valueOf(type, value);
		}
		catch (IllegalArgumentException exception) {
			throw validationError();
		}
	}

	private static LocalDate parseDate(String value) {
		if (value == null) {
			return null;
		}
		if (value.isBlank()) {
			throw validationError();
		}
		try {
			return LocalDate.parse(value);
		}
		catch (DateTimeParseException exception) {
			throw validationError();
		}
	}

	private static String optionalNonBlank(String value) {
		if (value != null && value.isBlank()) {
			throw validationError();
		}
		return value;
	}

	private static int parseInteger(String value, int defaultValue, int minimum, int maximum) {
		if (value == null) {
			return defaultValue;
		}
		if (value.isBlank()) {
			throw validationError();
		}
		try {
			int parsed = Integer.parseInt(value);
			if (parsed < minimum || parsed > maximum) {
				throw validationError();
			}
			return parsed;
		}
		catch (NumberFormatException exception) {
			throw validationError();
		}
	}

	private static String parseSort(String value) {
		String parsed = value == null ? "createdAt,desc" : value;
		if (!parsed.matches("(?:createdAt|updatedAt|title|dueDate|priority|status),(?:asc|desc)")) {
			throw validationError();
		}
		return parsed;
	}

	private static BusinessException validationError() {
		return new BusinessException(ErrorCode.VALIDATION_ERROR);
	}

	@PostMapping("/api/tasks")
	@ResponseStatus(HttpStatus.CREATED)
	TaskResponse createTask(
		Authentication authentication,
		@Valid @RequestBody CreateTaskRequest body) {
		return taskService.create(
			authentication,
			body.projectId(),
			body.parentId(),
			body.title(),
			body.description(),
			body.assigneeId(),
			body.status(),
			body.priority(),
			body.dueDate());
	}

	@GetMapping("/api/tasks/{taskId}")
	TaskResponse task(Authentication authentication, @PathVariable Long taskId) {
		return taskService.findById(authentication, taskId);
	}

	@PutMapping("/api/tasks/{taskId}")
	TaskResponse updateTask(
		Authentication authentication,
		@PathVariable Long taskId,
		@Valid @RequestBody UpdateTaskRequest body) {
		return taskService.update(
			authentication,
			taskId,
			body.parentId(),
			body.title(),
			body.description(),
			body.assigneeId(),
			body.status(),
			body.priority(),
			body.dueDate());
	}

	@PutMapping("/api/tasks/{taskId}/status")
	TaskResponse updateTaskStatus(
		Authentication authentication,
		@PathVariable Long taskId,
		@Valid @RequestBody UpdateTaskStatusRequest body) {
		return taskService.updateStatus(authentication, taskId, body.status());
	}

	@DeleteMapping("/api/tasks/{taskId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteTask(Authentication authentication, @PathVariable Long taskId) {
		taskService.delete(authentication, taskId);
	}

	record CreateTaskRequest(
		@JsonProperty(required = true) @NotNull @Pattern(regexp = "[1-9]\\d*") String projectId,
		@JsonProperty(required = true) @Pattern(regexp = "[1-9]\\d*") String parentId,
		@JsonProperty(required = true) @NotBlank @Size(max = 100) String title,
		@JsonProperty(required = true) @Size(max = 5000) String description,
		@JsonProperty(required = true) @Pattern(regexp = "[1-9]\\d*") String assigneeId,
		@JsonProperty(required = true) @NotNull TaskStatus status,
		@JsonProperty(required = true) @NotNull Priority priority,
		@JsonProperty(required = true) LocalDate dueDate) {
	}

	record UpdateTaskRequest(
		@JsonProperty(required = true) @Pattern(regexp = "[1-9]\\d*") String parentId,
		@JsonProperty(required = true) @NotBlank @Size(max = 100) String title,
		@JsonProperty(required = true) @Size(max = 5000) String description,
		@JsonProperty(required = true) @Pattern(regexp = "[1-9]\\d*") String assigneeId,
		@JsonProperty(required = true) @NotNull TaskStatus status,
		@JsonProperty(required = true) @NotNull Priority priority,
		@JsonProperty(required = true) LocalDate dueDate) {
	}

	record UpdateTaskStatusRequest(
		@JsonProperty(required = true) @NotNull TaskStatus status) {
	}
}
