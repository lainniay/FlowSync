package hgc.flowsync.ai;

import java.time.LocalDate;
import java.util.List;

import hgc.flowsync.project.Priority;
import hgc.flowsync.task.TaskResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AiTaskPlanController {

	private final AiTaskPlanImportService importService;

	public AiTaskPlanController(AiTaskPlanImportService importService) {
		this.importService = importService;
	}

	@PostMapping("/api/projects/{projectId}/ai/task-plans/imports")
	@ResponseStatus(HttpStatus.CREATED)
	AiTaskPlanImportResponse importPlan(
		Authentication authentication,
		@PathVariable Long projectId,
		@Valid @RequestBody AiTaskPlanImportRequest body) {
		return importService.importPlan(authentication, projectId, body.items());
	}
}

record AiTaskPlanImportRequest(
	@JsonProperty(required = true) @NotEmpty @Size(max = 20)
	List<@NotNull @Valid AiTaskPlanItem> items) {
}

record AiTaskPlanItem(
	@JsonProperty(required = true) @NotBlank String draftId,
	@JsonProperty(required = true) String parentDraftId,
	@JsonProperty(required = true) @NotBlank @Size(max = 100) String title,
	@JsonProperty(required = true) @Size(max = 5000) String description,
	@JsonProperty(required = true) @NotNull Priority priority,
	@JsonProperty(required = true) LocalDate dueDate,
	@JsonProperty(required = true) @Pattern(regexp = "[1-9]\\d*") String assigneeId) {
}

record AiTaskPlanImportResponse(int importedCount, List<TaskResponse> tasks) {
}
