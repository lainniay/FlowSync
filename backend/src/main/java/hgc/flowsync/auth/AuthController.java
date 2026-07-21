package hgc.flowsync.auth;

import hgc.flowsync.user.UserResponse;
import hgc.flowsync.user.PasswordPolicy;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class AuthController {

	private final AuthService authService;
	private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@GetMapping("/api/auth/csrf")
	CsrfResponse csrf(CsrfToken token) {
		return new CsrfResponse(token.getToken(), token.getHeaderName());
	}

	@PostMapping("/api/auth/login")
	UserResponse login(
		@Valid @RequestBody LoginRequest body,
		HttpServletRequest request,
		HttpServletResponse response) {
		return authService.login(body.username(), body.password(), request, response);
	}

	@PostMapping("/api/auth/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void logout(
		Authentication authentication,
		HttpServletRequest request,
		HttpServletResponse response) {
		logoutHandler.logout(request, response, authentication);
	}

	@GetMapping("/api/users/me")
	UserResponse currentUser(Authentication authentication) {
		return authService.currentUser(authentication);
	}

	@PutMapping("/api/users/me")
	UserResponse updateProfile(
		@Valid @RequestBody UpdateProfileRequest body,
		Authentication authentication) {
		return authService.updateProfile(
			authentication,
			body.displayName(),
			body.phone(),
			body.email());
	}

	@PutMapping("/api/users/me/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void changePassword(
		@Valid @RequestBody ChangePasswordRequest body,
		Authentication authentication) {
		authService.changePassword(authentication, body.currentPassword(), body.newPassword());
	}

	record LoginRequest(
		@NotBlank @Size(max = 50) String username,
		String password) {
	}

	record CsrfResponse(String token, String headerName) {
	}

	record UpdateProfileRequest(
		@JsonProperty(required = true) @NotBlank @Size(max = 50) String displayName,
		@JsonProperty(required = true) @Size(max = 20) String phone,
		@JsonProperty(required = true) @Size(min = 1, max = 100) @Email String email) {
	}

	record ChangePasswordRequest(
		@NotEmpty String currentPassword,
		@NotEmpty @PasswordPolicy.Valid String newPassword) {
	}
}
