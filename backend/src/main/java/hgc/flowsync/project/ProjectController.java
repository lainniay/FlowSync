package hgc.flowsync.project;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProjectController {

	private final ProjectService projectService;

	public ProjectController(ProjectService projectService) {
		this.projectService = projectService;
	}

	@PostMapping("/api/projects")
	@ResponseStatus(HttpStatus.CREATED)
	ProjectResponse createProject(
		Authentication authentication,
		@Valid @RequestBody CreateProjectRequest body) {
		return projectService.create(
			authentication,
			body.name(),
			body.description(),
			body.status(),
			body.priority(),
			body.startDate(),
			body.endDate(),
			body.ownerId());
	}

	record CreateProjectRequest(
		@JsonProperty(required = true) @NotBlank @Size(max = 100) String name,
		@JsonProperty(required = true) @Size(max = 2000) String description,
		@JsonProperty(required = true) @NotNull ProjectStatus status,
		@JsonProperty(required = true) @NotNull Priority priority,
		@JsonProperty(required = true) LocalDate startDate,
		@JsonProperty(required = true) LocalDate endDate,
		@Pattern(regexp = "[1-9]\\d*") String ownerId) {

		@AssertTrue(message = "endDate must not be before startDate")
		@JsonIgnore
		public boolean isDateRangeValid() {
			return startDate == null || endDate == null || !endDate.isBefore(startDate);
		}
	}
}
