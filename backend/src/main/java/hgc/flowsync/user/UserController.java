package hgc.flowsync.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping("/api/users")
	@PreAuthorize("hasRole('ADMIN')")
	UserPageResponse users(
		@RequestParam(required = false) String q,
		@RequestParam(required = false) SystemRole systemRole,
		@RequestParam(defaultValue = "true") @Pattern(regexp = "true|false") String active,
		@RequestParam(defaultValue = "0") @Min(0) int page,
		@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
		@RequestParam(defaultValue = "createdAt,desc")
		@Pattern(regexp = "(?:createdAt|username|displayName),(?:asc|desc)") String sort) {
		return userService.findAll(q, systemRole, Boolean.parseBoolean(active), page, size, sort);
	}

	@GetMapping("/api/users/{userId}")
	@PreAuthorize("hasRole('ADMIN')")
	UserResponse user(@PathVariable Long userId) {
		return userService.findById(userId);
	}

	@PostMapping("/api/users")
	@PreAuthorize("hasRole('ADMIN')")
	@ResponseStatus(HttpStatus.CREATED)
	UserResponse createUser(@Valid @RequestBody CreateUserRequest body) {
		return userService.create(
			body.username(),
			body.initialPassword(),
			body.displayName(),
			body.systemRole(),
			body.phone(),
			body.email());
	}

	@PutMapping("/api/users/{userId}")
	@PreAuthorize("hasRole('ADMIN')")
	UserResponse updateUser(
		@PathVariable Long userId,
		@Valid @RequestBody UpdateUserRequest body) {
		return userService.update(
			userId,
			body.displayName(),
			body.phone(),
			body.email(),
			body.systemRole(),
			body.active());
	}

	@PutMapping("/api/users/{userId}/password")
	@PreAuthorize("hasRole('ADMIN')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void resetPassword(
		@PathVariable Long userId,
		@Valid @RequestBody ResetPasswordRequest body) {
		userService.resetPassword(userId, body.newPassword());
	}

	record ResetPasswordRequest(@NotEmpty @PasswordPolicy.Valid String newPassword) {
	}

	record CreateUserRequest(
		@JsonProperty(required = true) @NotBlank @Size(max = 50) String username,
		@JsonProperty(required = true) @NotEmpty @PasswordPolicy.Valid String initialPassword,
		@JsonProperty(required = true) @NotBlank @Size(max = 50) String displayName,
		@JsonProperty(required = true) @NotNull SystemRole systemRole,
		@JsonProperty(required = true) @Size(max = 20) String phone,
		@JsonProperty(required = true) @Size(min = 1, max = 100) @Email String email) {
	}

	record UpdateUserRequest(
		@JsonProperty(required = true) @NotBlank @Size(max = 50) String displayName,
		@JsonProperty(required = true) @Size(max = 20) String phone,
		@JsonProperty(required = true) @Size(min = 1, max = 100) @Email String email,
		@JsonProperty(required = true) @NotNull SystemRole systemRole,
		@JsonProperty(required = true) @NotNull Boolean active) {
	}
}
