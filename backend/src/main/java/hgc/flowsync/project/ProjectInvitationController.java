package hgc.flowsync.project;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
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
public class ProjectInvitationController {

	private final ProjectInvitationService projectInvitationService;

	public ProjectInvitationController(ProjectInvitationService projectInvitationService) {
		this.projectInvitationService = projectInvitationService;
	}

	@PostMapping("/api/projects/{projectId}/invitations")
	@ResponseStatus(HttpStatus.CREATED)
	List<ProjectInvitationResponse> createInvitations(
		Authentication authentication,
		@PathVariable Long projectId,
		@Valid @RequestBody UserIdsRequest body) {
		return projectInvitationService.createAll(authentication, projectId, body.userIds());
	}

	@GetMapping("/api/projects/{projectId}/invitations")
	List<ProjectInvitationResponse> projectInvitations(
		Authentication authentication,
		@PathVariable Long projectId) {
		return projectInvitationService.findByProject(authentication, projectId);
	}

	@DeleteMapping("/api/projects/{projectId}/invitations/{invitationId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void cancelInvitation(
		Authentication authentication,
		@PathVariable Long projectId,
		@PathVariable Long invitationId) {
		projectInvitationService.cancel(authentication, projectId, invitationId);
	}

	@GetMapping("/api/project-invitations")
	List<ProjectInvitationResponse> myInvitations(
		Authentication authentication,
		@RequestParam(required = false) InvitationStatus status) {
		return projectInvitationService.findMine(authentication, status);
	}

	@PutMapping("/api/project-invitations/{invitationId}")
	ProjectInvitationResponse respondToInvitation(
		Authentication authentication,
		@PathVariable Long invitationId,
		@Valid @RequestBody RespondRequest body) {
		return projectInvitationService.respond(authentication, invitationId, body.status());
	}

	record UserIdsRequest(
		@JsonProperty(required = true) @NotEmpty List<@Pattern(regexp = "[1-9]\\d*") String> userIds) {
	}

	record RespondRequest(@JsonProperty(required = true) @NotNull InvitationStatus status) {

		@AssertTrue(message = "status must be ACCEPTED or REJECTED")
		@JsonIgnore
		public boolean isStatusAllowed() {
			return status == null || status == InvitationStatus.ACCEPTED || status == InvitationStatus.REJECTED;
		}
	}
}
