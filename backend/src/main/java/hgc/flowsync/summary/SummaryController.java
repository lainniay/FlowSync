package hgc.flowsync.summary;

import hgc.flowsync.common.api.PageResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
public class SummaryController {

	private final SummaryService summaryService;

	public SummaryController(SummaryService summaryService) {
		this.summaryService = summaryService;
	}

	@GetMapping("/api/summaries")
	PageResponse<SummaryResponse> summaries(
		Authentication authentication,
		@RequestParam(required = false) @Pattern(regexp = "[1-9]\\d*") String projectId,
		@RequestParam(required = false) @Pattern(regexp = "(?:none|[1-9]\\d*)") String taskId,
		@RequestParam(required = false) SummaryType type,
		@RequestParam(required = false) @Pattern(regexp = "[1-9]\\d*") String createdBy,
		@RequestParam(defaultValue = "0") @Min(0) int page,
		@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
		@RequestParam(defaultValue = "createdAt,desc")
		@Pattern(regexp = "(?:createdAt|updatedAt|type),(?:asc|desc)") String sort) {
		return summaryService.findAll(
			authentication,
			projectId,
			taskId,
			type,
			createdBy,
			page,
			size,
			sort);
	}

	@PostMapping("/api/summaries")
	@ResponseStatus(HttpStatus.CREATED)
	SummaryResponse createSummary(
		Authentication authentication,
		@Valid @RequestBody CreateSummaryRequest body) {
		return summaryService.create(
			authentication,
			body.projectId(),
			body.taskId(),
			body.type(),
			body.content());
	}

	@GetMapping("/api/summaries/{summaryId}")
	SummaryResponse summary(Authentication authentication, @PathVariable Long summaryId) {
		return summaryService.findById(authentication, summaryId);
	}

	@PutMapping("/api/summaries/{summaryId}")
	SummaryResponse updateSummary(
		Authentication authentication,
		@PathVariable Long summaryId,
		@Valid @RequestBody UpdateSummaryRequest body) {
		return summaryService.update(authentication, summaryId, body.type(), body.content());
	}

	@DeleteMapping("/api/summaries/{summaryId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteSummary(Authentication authentication, @PathVariable Long summaryId) {
		summaryService.delete(authentication, summaryId);
	}

	record CreateSummaryRequest(
		@JsonProperty(required = true) @NotNull @Pattern(regexp = "[1-9]\\d*") String projectId,
		@JsonProperty(required = true) @Pattern(regexp = "[1-9]\\d*") String taskId,
		@JsonProperty(required = true) @NotNull SummaryType type,
		@JsonProperty(required = true) @NotBlank String content) {
	}

	record UpdateSummaryRequest(
		@JsonProperty(required = true) @NotNull SummaryType type,
		@JsonProperty(required = true) @NotBlank String content) {
	}
}
