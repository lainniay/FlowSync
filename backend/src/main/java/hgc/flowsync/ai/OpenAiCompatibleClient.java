package hgc.flowsync.ai;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

import hgc.flowsync.common.error.BusinessException;
import hgc.flowsync.common.error.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class OpenAiCompatibleClient {

	private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleClient.class);

	private final AiProperties properties;
	private final ObjectMapper objectMapper;
	private final RestClient restClient;
	private final Semaphore permits;

	public OpenAiCompatibleClient(
		AiProperties properties,
		ObjectMapper objectMapper,
		RestClient.Builder restClientBuilder) {
		this.properties = properties;
		this.objectMapper = objectMapper;
		this.permits = new Semaphore(properties.getMaxConcurrentRequests());
		if (!properties.isEnabled()) {
			this.restClient = null;
			return;
		}
		HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(properties.getConnectTimeout())
			.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.getReadTimeout());
		String baseUrl = properties.getBaseUrl().toString().replaceAll("/+$", "") + "/";
		this.restClient = restClientBuilder
			.requestFactory(requestFactory)
			.baseUrl(baseUrl)
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
			.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
			.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
			.build();
	}

	public String generateSuggestion(String systemPrompt, String userPrompt) {
		return complete(systemPrompt, userPrompt, null);
	}

	public String generatePlan(String systemPrompt, String userPrompt) {
		return complete(systemPrompt, userPrompt, responseFormat());
	}

	AiProperties.ResponseFormat responseFormatMode() {
		return properties.getResponseFormat();
	}

	private String complete(String systemPrompt, String userPrompt, Map<String, Object> responseFormat) {
		if (!properties.isEnabled()) {
			throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE);
		}
		if (!permits.tryAcquire()) {
			throw new BusinessException(ErrorCode.RATE_LIMITED);
		}
		long started = System.nanoTime();
		try {
			ChatRequest body = new ChatRequest(
				properties.getModel(),
				List.of(new Message("system", systemPrompt), new Message("user", userPrompt)),
				false,
				responseFormat);
			return restClient.post()
				.uri("chat/completions")
				.body(body)
				.exchange((request, response) -> {
					int status = response.getStatusCode().value();
					String requestId = requestId(response.getHeaders());
					log.info("AI provider response model={} status={} durationMs={} requestId={}",
						properties.getModel(), status, elapsedMillis(started), requestId);
					if (status == 429) {
						throw new BusinessException(ErrorCode.RATE_LIMITED);
					}
					if (!response.getStatusCode().is2xxSuccessful()) {
						throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR);
					}
					byte[] responseBody = readBounded(response.getBody());
					return content(responseBody);
				});
		} catch (BusinessException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			log.warn("AI provider request failed model={} durationMs={}",
				properties.getModel(), elapsedMillis(started));
			throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR);
		} finally {
			permits.release();
		}
	}

	private byte[] readBounded(java.io.InputStream input) {
		try {
			byte[] bytes = input.readNBytes(properties.getMaxResponseBytes() + 1);
			if (bytes.length > properties.getMaxResponseBytes()) {
				throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR);
			}
			return bytes;
		} catch (IOException exception) {
			throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR);
		}
	}

	private String content(byte[] bytes) {
		try {
			JsonNode root = objectMapper.readTree(bytes);
			JsonNode choices = root == null ? null : root.get("choices");
			JsonNode content = choices == null || !choices.isArray() || choices.isEmpty()
				? null : choices.get(0).path("message").get("content");
			if (content == null || !content.isTextual() || content.textValue().isBlank()) {
				throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR);
			}
			return content.textValue();
		} catch (BusinessException exception) {
			throw exception;
		} catch (IOException exception) {
			throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR);
		}
	}

	private Map<String, Object> responseFormat() {
		return switch (properties.getResponseFormat()) {
			case NONE -> null;
			case JSON_OBJECT -> Map.of("type", "json_object");
			case JSON_SCHEMA -> Map.of(
				"type", "json_schema",
				"json_schema", Map.of(
					"name", "flowsync_task_plan",
					"strict", true,
					"schema", taskPlanSchema()));
		};
	}

	private static Map<String, Object> taskPlanSchema() {
		Map<String, Object> nullableString = Map.of("type", List.of("string", "null"));
		Map<String, Object> item = Map.of(
			"type", "object",
			"additionalProperties", false,
			"properties", Map.of(
				"draftId", Map.of("type", "string"),
				"parentDraftId", nullableString,
				"title", Map.of("type", "string"),
				"description", nullableString,
				"priority", Map.of("type", "string", "enum", List.of("LOW", "MEDIUM", "HIGH")),
				"dueDate", nullableString,
				"assigneeId", nullableString),
			"required", List.of(
				"draftId", "parentDraftId", "title", "description", "priority", "dueDate", "assigneeId"));
		return Map.of(
			"type", "object",
			"additionalProperties", false,
			"properties", Map.of(
				"overview", Map.of("type", "string"),
				"items", Map.of("type", "array", "items", item)),
			"required", List.of("overview", "items"));
	}

	private static String requestId(HttpHeaders headers) {
		String requestId = headers.getFirst("x-request-id");
		return requestId == null ? headers.getFirst("request-id") : requestId;
	}

	private static long elapsedMillis(long started) {
		return Duration.ofNanos(System.nanoTime() - started).toMillis();
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private record ChatRequest(
		String model,
		List<Message> messages,
		boolean stream,
		@JsonProperty("response_format") Map<String, Object> responseFormat) {
	}

	private record Message(String role, String content) {
	}

}
