package hgc.flowsync.common.error;

import java.time.LocalDate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GlobalExceptionHandlerTests.ErrorController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
	GlobalExceptionHandler.class,
	ProblemDetailResponseWriter.class,
	GlobalExceptionHandlerTests.ErrorController.class
})
class GlobalExceptionHandlerTests {

	private final MockMvc mockMvc;

	@Autowired
	GlobalExceptionHandlerTests(MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}

	@Test
	void nonObjectJsonReturnsBadRequestProblem() throws Exception {
		mockMvc.perform(post("/test/errors/body")
				.contentType(MediaType.APPLICATION_JSON)
				.content("[]"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.instance").value("/test/errors/body"))
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"))
			.andExpect(jsonPath("$.errors").isEmpty());
	}

	@Test
	void malformedJsonReturnsBadRequestProblem() throws Exception {
		mockMvc.perform(post("/test/errors/body")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"))
			.andExpect(jsonPath("$.errors").isEmpty());
	}

	@Test
	void invalidEnumReturnsValidationProblem() throws Exception {
		mockMvc.perform(post("/test/errors/typed-body")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"role\":\"OWNER\",\"date\":\"2026-07-15\"}"))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.errors[0].field").value("role"))
			.andExpect(jsonPath("$.errors[0].code").value("Invalid"));
	}

	@Test
	void invalidDateReturnsValidationProblem() throws Exception {
		mockMvc.perform(post("/test/errors/typed-body")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"role\":\"USER\",\"date\":\"not-a-date\"}"))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.errors[0].field").value("date"));
	}

	@Test
	void invalidBodyReturnsValidationProblem() throws Exception {
		mockMvc.perform(post("/test/errors/body")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(422))
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.errors[0].field").value("name"))
			.andExpect(jsonPath("$.errors[0].code").value("NotBlank"))
			.andExpect(jsonPath("$.errors[0].message").isNotEmpty());
	}

	@Test
	void businessExceptionUsesItsStableCode() throws Exception {
		mockMvc.perform(get("/test/errors/business"))
			.andExpect(status().isConflict())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(409))
			.andExpect(jsonPath("$.code").value("USERNAME_ALREADY_EXISTS"))
			.andExpect(jsonPath("$.detail").value("Username already exists."))
			.andExpect(jsonPath("$.errors").isEmpty());
	}

	@Test
	void unexpectedExceptionDoesNotExposeItsMessage() throws Exception {
		mockMvc.perform(get("/test/errors/unexpected"))
			.andExpect(status().isInternalServerError())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(500))
			.andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
			.andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
			.andExpect(jsonPath("$.errors").isEmpty());
	}

	@Test
	void unavailableDataServiceReturnsServiceUnavailableProblem() throws Exception {
		mockMvc.perform(get("/test/errors/unavailable"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(503))
			.andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
			.andExpect(jsonPath("$.detail").value("The service is temporarily unavailable."))
			.andExpect(jsonPath("$.errors").isEmpty());
	}

	@Test
	void frameworkErrorDoesNotExposeItsProblemDetail() throws Exception {
		mockMvc.perform(get("/test/errors/framework"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
			.andExpect(jsonPath("$.detail").value("The service is temporarily unavailable."));
	}

	@Test
	void missingResourceReturnsNotFoundProblem() throws Exception {
		mockMvc.perform(get("/test/errors/missing"))
			.andExpect(status().isNotFound())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(404))
			.andExpect(jsonPath("$.code").value("NOT_FOUND"))
			.andExpect(jsonPath("$.instance").value("/test/errors/missing"))
			.andExpect(jsonPath("$.errors").isEmpty());
	}

	@Test
	void unsupportedMethodReturnsWrappedProblem() throws Exception {
		mockMvc.perform(put("/test/errors/business"))
			.andExpect(status().isMethodNotAllowed())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(405))
			.andExpect(jsonPath("$.instance").value("/test/errors/business"))
			.andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
			.andExpect(jsonPath("$.errors").isEmpty());
	}

	@Test
	void unacceptableRepresentationReturnsWrappedProblem() throws Exception {
		mockMvc.perform(get("/test/errors/representation").accept(MediaType.APPLICATION_XML))
			.andExpect(status().isNotAcceptable())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(406))
			.andExpect(jsonPath("$.instance").value("/test/errors/representation"))
			.andExpect(jsonPath("$.code").value("NOT_ACCEPTABLE"))
			.andExpect(jsonPath("$.errors").isEmpty());
	}

	@Test
	void unsupportedContentTypeReturnsWrappedProblem() throws Exception {
		mockMvc.perform(post("/test/errors/body")
				.contentType(MediaType.TEXT_PLAIN)
				.content("name"))
			.andExpect(status().isUnsupportedMediaType())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(415))
			.andExpect(jsonPath("$.instance").value("/test/errors/body"))
			.andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
			.andExpect(jsonPath("$.errors").isEmpty());
	}

	@RestController
	public static class ErrorController {

		@PostMapping("/test/errors/body")
		void body(@Valid @RequestBody TestBody body) {
		}

		@PostMapping("/test/errors/typed-body")
		void typedBody(@RequestBody TypedBody body) {
		}

		@GetMapping("/test/errors/business")
		void business() {
			throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS);
		}

		@GetMapping("/test/errors/unexpected")
		void unexpected() {
			throw new IllegalStateException("sensitive internal detail");
		}

		@GetMapping("/test/errors/unavailable")
		void unavailable() {
			throw new org.springframework.dao.DataAccessResourceFailureException("database secret");
		}

		@GetMapping("/test/errors/framework")
		void framework() {
			ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.SERVICE_UNAVAILABLE,
				"sensitive framework detail");
			throw new ErrorResponseException(HttpStatus.SERVICE_UNAVAILABLE, problem, null);
		}

		@GetMapping(value = "/test/errors/representation", produces = MediaType.APPLICATION_JSON_VALUE)
		TestBody representation() {
			return new TestBody("ok");
		}
	}

	record TestBody(@NotBlank String name) {
	}

	record TypedBody(TestRole role, LocalDate date) {
	}

	enum TestRole {
		ADMIN,
		USER
	}
}
