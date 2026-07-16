package hgc.flowsync.common.security;

import hgc.flowsync.common.error.GlobalExceptionHandler;
import hgc.flowsync.common.error.ProblemDetailResponseWriter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SecurityErrorContractTests.SecurityController.class)
@Import({
	SecurityConfig.class,
	GlobalExceptionHandler.class,
	ProblemDetailResponseWriter.class,
	SecurityErrorContractTests.SecurityController.class
})
class SecurityErrorContractTests {

	private final MockMvc mockMvc;

	@Autowired
	SecurityErrorContractTests(MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}

	@Test
	void anonymousRequestReturnsUnauthorizedProblem() throws Exception {
		mockMvc.perform(get("/test/security/authenticated"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentType("application/problem+json;charset=UTF-8"))
			.andExpect(jsonPath("$.type").value("about:blank"))
			.andExpect(jsonPath("$.title").value("Unauthorized"))
			.andExpect(jsonPath("$.status").value(401))
			.andExpect(jsonPath("$.instance").value("/test/security/authenticated"))
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
			.andExpect(jsonPath("$.errors").isEmpty());
	}

	@Test
	@WithMockUser(roles = "USER")
	void forbiddenRequestReturnsForbiddenProblem() throws Exception {
		mockMvc.perform(get("/test/security/admin"))
			.andExpect(status().isForbidden())
			.andExpect(content().contentType("application/problem+json"))
			.andExpect(jsonPath("$.status").value(403))
			.andExpect(jsonPath("$.instance").value("/test/security/admin"))
			.andExpect(jsonPath("$.code").value("FORBIDDEN"))
			.andExpect(jsonPath("$.errors").isEmpty());
	}

	@Test
	@WithMockUser
	void missingCsrfTokenReturnsCsrfProblem() throws Exception {
		mockMvc.perform(post("/test/security/authenticated"))
			.andExpect(status().isForbidden())
			.andExpect(content().contentType("application/problem+json;charset=UTF-8"))
			.andExpect(jsonPath("$.status").value(403))
			.andExpect(jsonPath("$.instance").value("/test/security/authenticated"))
			.andExpect(jsonPath("$.code").value("CSRF_INVALID"))
			.andExpect(jsonPath("$.errors").isEmpty());
	}

	@RestController
	public static class SecurityController {

		@GetMapping("/test/security/authenticated")
		void authenticated() {
		}

		@PostMapping("/test/security/authenticated")
		void write() {
		}

		@GetMapping("/test/security/admin")
		@PreAuthorize("hasRole('ADMIN')")
		void admin() {
		}
	}
}
