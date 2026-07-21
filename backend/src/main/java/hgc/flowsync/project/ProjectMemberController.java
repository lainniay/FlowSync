package hgc.flowsync.project;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProjectMemberController {

	private final ProjectMemberService projectMemberService;

	public ProjectMemberController(ProjectMemberService projectMemberService) {
		this.projectMemberService = projectMemberService;
	}

	@GetMapping("/api/projects/{projectId}/members")
	List<ProjectMemberResponse> members(Authentication authentication, @PathVariable Long projectId) {
		return projectMemberService.findAll(authentication, projectId);
	}

	@PostMapping("/api/projects/{projectId}/members")
	@ResponseStatus(HttpStatus.CREATED)
	List<ProjectMemberResponse> addMembers(
		Authentication authentication,
		@PathVariable Long projectId,
		@Valid @RequestBody UserIdsRequest body) {
		return projectMemberService.addAll(authentication, projectId, body.userIds());
	}

	@DeleteMapping("/api/projects/{projectId}/members/{userId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void removeMember(
		Authentication authentication,
		@PathVariable Long projectId,
		@PathVariable Long userId) {
		projectMemberService.remove(authentication, projectId, userId);
	}

	record UserIdsRequest(
		@JsonProperty(required = true) @NotEmpty List<@NotNull @Pattern(regexp = "[1-9]\\d*") String> userIds) {
	}
}
