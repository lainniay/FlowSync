package hgc.flowsync.project;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
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
public class ProjectController {

	private final ProjectService projectService;

	public ProjectController(ProjectService projectService) {
		this.projectService = projectService;
	}

	@GetMapping("/api/projects")
	hgc.flowsync.common.api.PageResponse<ProjectResponse> projects(
		Authentication authentication,
		@RequestParam(required = false) String q,
		@RequestParam(required = false) ProjectStatus status,
		@RequestParam(required = false) @Pattern(regexp = "[1-9]\\d*") String ownerId,
		@RequestParam(defaultValue = "false") @Pattern(regexp = "true|false") String archived,
		@RequestParam(defaultValue = "0") @Min(0) int page,
		@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
		@RequestParam(defaultValue = "createdAt,desc")
		@Pattern(regexp = "(?:createdAt|updatedAt|name|startDate|endDate|priority),(?:asc|desc)") String sort) {
		return projectService.findAll(
			authentication,
			q,
			status,
			ownerId,
			Boolean.parseBoolean(archived),
			page,
			size,
			sort);
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

	@GetMapping("/api/projects/{projectId}")
	ProjectResponse project(Authentication authentication, @PathVariable Long projectId) {
		return projectService.findById(authentication, projectId);
	}

	@PutMapping("/api/projects/{projectId}")
	ProjectResponse updateProject(
		Authentication authentication,
		@PathVariable Long projectId,
		@Valid @RequestBody UpdateProjectRequest body) {
		return projectService.update(
			authentication,
			projectId,
			body.name(),
			body.description(),
			body.status(),
			body.priority(),
			body.startDate(),
			body.endDate());
	}

	@PutMapping("/api/projects/{projectId}/owner")
	ProjectResponse transferOwner(
		Authentication authentication,
		@PathVariable Long projectId,
		@Valid @RequestBody TransferOwnerRequest body) {
		return projectService.transferOwner(authentication, projectId, body.ownerId());
	}

	@PutMapping("/api/projects/{projectId}/archive")
	ProjectResponse archiveProject(Authentication authentication, @PathVariable Long projectId) {
		return projectService.archive(authentication, projectId);
	}

	@DeleteMapping("/api/projects/{projectId}/archive")
	ProjectResponse restoreProject(Authentication authentication, @PathVariable Long projectId) {
		return projectService.restore(authentication, projectId);
	}

	@DeleteMapping("/api/projects/{projectId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteProject(Authentication authentication, @PathVariable Long projectId) {
		projectService.delete(authentication, projectId);
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

	record UpdateProjectRequest(
		@JsonProperty(required = true) @NotBlank @Size(max = 100) String name,
		@JsonProperty(required = true) @Size(max = 2000) String description,
		@JsonProperty(required = true) @NotNull ProjectStatus status,
		@JsonProperty(required = true) @NotNull Priority priority,
		@JsonProperty(required = true) LocalDate startDate,
		@JsonProperty(required = true) LocalDate endDate) {

		@AssertTrue(message = "endDate must not be before startDate")
		@JsonIgnore
		public boolean isDateRangeValid() {
			return startDate == null || endDate == null || !endDate.isBefore(startDate);
		}
	}

	record TransferOwnerRequest(
		@JsonProperty(required = true) @NotNull @Pattern(regexp = "[1-9]\\d*") String ownerId) {
	}
}
