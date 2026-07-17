package hgc.flowsync.task;

import java.time.LocalDate;

import hgc.flowsync.common.api.PageResponse;
import hgc.flowsync.project.Priority;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
		@RequestParam(required = false) TaskStatus status,
		@RequestParam(required = false) Priority priority,
		@RequestParam(required = false) @Pattern(regexp = "[1-9]\\d*") String parentId,
		@RequestParam(required = false) LocalDate dueBefore,
		@RequestParam(required = false) LocalDate dueAfter,
		@RequestParam(required = false) String q,
		@RequestParam(defaultValue = "0") @Min(0) int page,
		@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
		@RequestParam(defaultValue = "createdAt,desc")
		@Pattern(regexp = "(?:createdAt|updatedAt|title|dueDate|priority|status),(?:asc|desc)") String sort) {
		return taskService.findAll(
			authentication,
			projectId,
			assigneeId,
			status,
			priority,
			parentId,
			dueBefore,
			dueAfter,
			q,
			page,
			size,
			sort);
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
