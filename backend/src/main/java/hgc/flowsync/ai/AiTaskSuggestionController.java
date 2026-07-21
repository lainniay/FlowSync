package hgc.flowsync.ai;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AiTaskSuggestionController {

	private final AiGenerationService generationService;

	public AiTaskSuggestionController(AiGenerationService generationService) {
		this.generationService = generationService;
	}

	@PostMapping("/api/ai/task-suggestions")
	AiTaskSuggestionResponse suggest(
		Authentication authentication,
		@Valid @RequestBody AiTaskSuggestionRequest body) {
		return generationService.generateSuggestion(authentication, body.taskId(), body.focus());
	}
}

record AiTaskSuggestionRequest(
	@JsonProperty(required = true) @NotNull @Pattern(regexp = "[1-9]\\d*") String taskId,
	@Size(max = 2000) String focus) {
}

record AiTaskSuggestionResponse(String suggestion, Instant generatedAt) {
}
