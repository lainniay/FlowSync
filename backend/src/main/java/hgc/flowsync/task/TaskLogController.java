package hgc.flowsync.task;

import hgc.flowsync.common.api.PageResponse;
import hgc.flowsync.common.error.BusinessException;
import hgc.flowsync.common.error.ErrorCode;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TaskLogController {

	private final TaskLogService taskLogService;

	public TaskLogController(TaskLogService taskLogService) {
		this.taskLogService = taskLogService;
	}

	@GetMapping("/api/tasks/{taskId}/logs")
	PageResponse<TaskLogResponse> taskLogs(
		Authentication authentication,
		@PathVariable Long taskId,
		@RequestParam(required = false) String page,
		@RequestParam(required = false) String size,
		@RequestParam(required = false) String sort) {
		return taskLogService.findAll(
			authentication,
			taskId,
			parseInteger(page, 0, 0, Integer.MAX_VALUE),
			parseInteger(size, 20, 1, 100),
			parseSort(sort));
	}

	@PostMapping("/api/tasks/{taskId}/logs")
	@ResponseStatus(HttpStatus.CREATED)
	TaskLogResponse createTaskLog(
		Authentication authentication,
		@PathVariable Long taskId,
		@Valid @RequestBody CreateTaskLogRequest body) {
		return taskLogService.create(
			authentication,
			taskId,
			parseProgressPercent(body.progressPercent()),
			body.content());
	}

	@DeleteMapping("/api/tasks/{taskId}/logs/{logId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteTaskLog(
		Authentication authentication,
		@PathVariable Long taskId,
		@PathVariable Long logId) {
		taskLogService.delete(authentication, taskId, logId);
	}

	private static int parseProgressPercent(JsonNode value) {
		if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "progressPercent");
		}
		int progressPercent = value.intValue();
		if (progressPercent < 0 || progressPercent > 100) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "progressPercent");
		}
		return progressPercent;
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
		if (!parsed.matches("(?:createdAt|progressPercent),(?:asc|desc)")) {
			throw validationError();
		}
		return parsed;
	}

	private static BusinessException validationError() {
		return new BusinessException(ErrorCode.VALIDATION_ERROR);
	}

	record CreateTaskLogRequest(
		@JsonProperty(required = true) JsonNode progressPercent,
		@JsonProperty(required = true) @NotBlank @Size(max = 1000) String content) {
	}
}
