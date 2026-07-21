package hgc.flowsync.ai;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;

import hgc.flowsync.project.Priority;
import hgc.flowsync.task.TaskResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
	private final AiGenerationService generationService;

	public AiTaskPlanController(
		AiTaskPlanImportService importService,
		AiGenerationService generationService) {
		this.importService = importService;
		this.generationService = generationService;
	}

	@PostMapping("/api/projects/{projectId}/ai/task-plans")
	AiTaskPlanResponse generatePlan(
		Authentication authentication,
		@PathVariable Long projectId,
		@Valid @RequestBody AiTaskPlanGenerateRequest body) {
		return generationService.generatePlan(authentication, projectId, body);
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
	@JsonProperty(required = true) @NotBlank @Size(max = 100) String draftId,
	@JsonProperty(required = true) @Size(max = 100) String parentDraftId,
	@JsonProperty(required = true) @NotBlank @Size(max = 100) String title,
	@JsonProperty(required = true) @Size(max = 5000) String description,
	@JsonProperty(required = true) @NotNull Priority priority,
	@JsonProperty(required = true) LocalDate dueDate,
	@JsonProperty(required = true) @Pattern(regexp = "[1-9]\\d*") String assigneeId) {
}

record AiTaskPlanImportResponse(int importedCount, List<TaskResponse> tasks) {
}

record AiTaskPlanGenerateRequest(
	@JsonProperty(required = true) @NotBlank @Size(max = 500) String goal,
	@Size(max = 5000) String description,
	@Valid AiTaskPlanConstraints constraints) {
}

record AiTaskPlanConstraints(
	@Min(1) @Max(20) Integer maxItems,
	LocalDate targetEndDate) {
}

record AiTaskPlanResponse(String overview, List<AiTaskPlanItem> items, Instant generatedAt) {
}
