package hgc.flowsync.common.http;

import java.util.concurrent.atomic.AtomicBoolean;

import hgc.flowsync.common.error.ProblemDetailResponseWriter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "flowsync.http.max-json-body-bytes=64")
@AutoConfigureMockMvc
class JsonBodySizeFilterTests {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private ProblemDetailResponseWriter problemWriter;

	@Test
	void declaredOversizedJsonReturnsPayloadTooLargeProblem() throws Exception {
		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("x".repeat(65)))
			.andExpect(status().isPayloadTooLarge())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(413))
			.andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"))
			.andExpect(jsonPath("$.instance").value("/api/auth/login"))
			.andExpect(jsonPath("$.errors").isEmpty());
	}

	@Test
	void unknownLengthOversizedJsonIsRejectedBeforeDownstreamStopsReading() throws Exception {
		JsonBodySizeFilter filter = new JsonBodySizeFilter(problemWriter, 64);
		MockHttpServletRequest request = unknownLengthJsonRequest("x".repeat(65));
		MockHttpServletResponse response = new MockHttpServletResponse();
		AtomicBoolean downstreamInvoked = new AtomicBoolean();

		filter.doFilter(request, response, (servletRequest, servletResponse) -> {
			downstreamInvoked.set(true);
			servletRequest.getInputStream().read();
		});

		assertThat(response.getStatus()).isEqualTo(413);
		assertThat(response.getContentAsString()).contains("\"code\":\"PAYLOAD_TOO_LARGE\"");
		assertThat(downstreamInvoked).isFalse();
	}

	@Test
	void unknownLengthJsonWithinLimitIsReplayedDownstream() throws Exception {
		JsonBodySizeFilter filter = new JsonBodySizeFilter(problemWriter, 64);
		MockHttpServletRequest request = unknownLengthJsonRequest("{\"username\":\"user\"}");
		String[] downstreamBody = new String[1];

		filter.doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) ->
			downstreamBody[0] = new String(
				servletRequest.getInputStream().readAllBytes(),
				java.nio.charset.StandardCharsets.UTF_8));

		assertThat(downstreamBody[0]).isEqualTo("{\"username\":\"user\"}");
	}

	@Test
	void smallJsonStillReachesNormalRequestValidation() throws Exception {
		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CSRF_INVALID"));
	}

	private static MockHttpServletRequest unknownLengthJsonRequest(String body) {
		MockHttpServletRequest request = new MockHttpServletRequest() {
			@Override
			public int getContentLength() {
				return -1;
			}

			@Override
			public long getContentLengthLong() {
				return -1;
			}
		};
		request.setMethod("POST");
		request.setRequestURI("/api/auth/login");
		request.setContentType(MediaType.APPLICATION_JSON_VALUE);
		request.setContent(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		return request;
	}
}
