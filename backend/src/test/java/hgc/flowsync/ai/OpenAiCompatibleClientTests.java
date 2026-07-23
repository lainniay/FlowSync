package hgc.flowsync.ai;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import hgc.flowsync.common.error.BusinessException;
import hgc.flowsync.common.error.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiCompatibleClientTests {

	private HttpServer server;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress(0), 0);
		server.start();
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	@Test
	void sendsCompatibleRequestAndReturnsContent() throws Exception {
		AtomicReference<String> authorization = new AtomicReference<>();
		AtomicReference<JsonNode> requestBody = new AtomicReference<>();
		server.createContext("/v1/chat/completions", exchange -> {
			authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
			requestBody.set(objectMapper.readTree(exchange.getRequestBody()));
			respond(exchange, 200, "{\"choices\":[{\"message\":{\"role\":\"assistant\","
				+ "\"content\":\"suggestion\"}}]}");
		});

		AiProperties properties = properties(1024, 2, Duration.ofSeconds(2));
		properties.setResponseFormat(AiProperties.ResponseFormat.JSON_OBJECT);
		OpenAiCompatibleClient client = client(properties);

		assertThat(client.generateSuggestion("system", "user")).isEqualTo("suggestion");
		assertThat(authorization).hasValue("Bearer secret-key");
		assertThat(requestBody.get().get("model").asText()).isEqualTo("test-model");
		assertThat(requestBody.get().get("stream").asBoolean()).isFalse();
		assertThat(requestBody.get().get("messages").toString()).doesNotContain("secret-key");
		assertThat(client.generatePlan("system", "user")).isEqualTo("suggestion");
		assertThat(requestBody.get().get("response_format").get("type").asText())
			.isEqualTo("json_object");
	}

	@Test
	void sendsStrictTaskPlanJsonSchema() throws Exception {
		AtomicReference<JsonNode> requestBody = new AtomicReference<>();
		server.createContext("/v1/chat/completions", exchange -> {
			requestBody.set(objectMapper.readTree(exchange.getRequestBody()));
			respond(exchange, 200,
				"{\"choices\":[{\"message\":{\"content\":\"{}\"}}]}");
		});
		AiProperties properties = properties(4096, 1, Duration.ofSeconds(2));
		properties.setResponseFormat(AiProperties.ResponseFormat.JSON_SCHEMA);

		assertThat(client(properties).generatePlan("system", "user")).isEqualTo("{}");
		JsonNode responseFormat = requestBody.get().path("response_format");
		JsonNode jsonSchema = responseFormat.path("json_schema");
		JsonNode schema = jsonSchema.path("schema");
		JsonNode itemSchema = schema.path("properties").path("items").path("items");
		assertThat(responseFormat.path("type").asText()).isEqualTo("json_schema");
		assertThat(jsonSchema.path("name").asText()).isEqualTo("flowsync_task_plan");
		assertThat(jsonSchema.path("strict").asBoolean()).isTrue();
		assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
		assertThat(schema.path("required").toString()).isEqualTo("[\"overview\",\"items\"]");
		assertThat(itemSchema.path("additionalProperties").asBoolean()).isFalse();
		assertThat(itemSchema.path("required").size()).isEqualTo(7);
		for (String field : new String[] {
			"draftId", "parentDraftId", "title", "description", "priority", "dueDate", "assigneeId"
		}) {
			assertThat(itemSchema.path("properties").has(field)).isTrue();
		}
		assertThat(itemSchema.path("properties").path("parentDraftId").path("type").toString())
			.isEqualTo("[\"string\",\"null\"]");
		assertThat(itemSchema.path("properties").path("priority").path("enum").toString())
			.isEqualTo("[\"LOW\",\"MEDIUM\",\"HIGH\"]");
	}

	@Test
	void disabledClientIsUnavailableAndEnabledConfigurationIsValidated() {
		AiProperties disabled = new AiProperties();
		OpenAiCompatibleClient client = client(disabled);
		assertBusinessError(() -> client.generateSuggestion("system", "user"),
			ErrorCode.SERVICE_UNAVAILABLE);

		AiProperties invalid = new AiProperties();
		invalid.setEnabled(true);
		try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
			assertThat(validatorFactory.getValidator().validate(invalid)).isNotEmpty();
		}
	}

	@Test
	void mapsRateLimitsAndOversizedResponsesWithoutRetrying() {
		AtomicInteger requests = new AtomicInteger();
		server.createContext("/v1/chat/completions", exchange -> {
			requests.incrementAndGet();
			respond(exchange, 429, "limited");
		});
		OpenAiCompatibleClient rateLimited = client(properties(1024, 2, Duration.ofSeconds(2)));

		assertBusinessError(() -> rateLimited.generateSuggestion("system", "user"),
			ErrorCode.RATE_LIMITED);
		assertThat(requests).hasValue(1);

		server.removeContext("/v1/chat/completions");
		server.createContext("/v1/chat/completions", exchange ->
			respond(exchange, 200, "x".repeat(65)));
		OpenAiCompatibleClient oversized = client(properties(64, 2, Duration.ofSeconds(2)));
		assertBusinessError(() -> oversized.generateSuggestion("system", "user"),
			ErrorCode.AI_PROVIDER_ERROR);
	}

	@Test
	void mapsHttpAndProtocolFailuresAndReleasesPermit() {
		AtomicInteger requests = new AtomicInteger();
		server.createContext("/v1/chat/completions", exchange -> {
			requests.incrementAndGet();
			respond(exchange, 500, "provider failed");
		});
		OpenAiCompatibleClient client = client(properties(1024, 1, Duration.ofSeconds(2)));
		assertBusinessError(() -> client.generateSuggestion("system", "user"),
			ErrorCode.AI_PROVIDER_ERROR);

		server.removeContext("/v1/chat/completions");
		server.createContext("/v1/chat/completions", exchange -> {
			requests.incrementAndGet();
			respond(exchange, 200,
				"{\"choices\":[{\"message\":{\"content\":\"first\",\"content\":\"second\"}}]}");
		});
		assertBusinessError(() -> client.generateSuggestion("system", "user"),
			ErrorCode.AI_PROVIDER_ERROR);

		server.removeContext("/v1/chat/completions");
		server.createContext("/v1/chat/completions", exchange -> {
			requests.incrementAndGet();
			respond(exchange, 200,
				"{\"choices\":[{\"message\":{\"content\":\"ignored\"}}]} trailing prose");
		});
		assertBusinessError(() -> client.generateSuggestion("system", "user"),
			ErrorCode.AI_PROVIDER_ERROR);

		server.removeContext("/v1/chat/completions");
		server.createContext("/v1/chat/completions", exchange -> {
			requests.incrementAndGet();
			respond(exchange, 200,
				"{\"choices\":[{\"message\":{\"content\":\"   \"}}]}");
		});
		assertBusinessError(() -> client.generateSuggestion("system", "user"),
			ErrorCode.AI_PROVIDER_ERROR);

		server.removeContext("/v1/chat/completions");
		server.createContext("/v1/chat/completions", exchange -> {
			requests.incrementAndGet();
			respond(exchange, 400, "bad request");
		});
		assertBusinessError(() -> client.generateSuggestion("system", "user"),
			ErrorCode.AI_PROVIDER_ERROR);

		server.removeContext("/v1/chat/completions");
		server.createContext("/v1/chat/completions", exchange -> {
			requests.incrementAndGet();
			respond(exchange, 200, "{\"choices\":[]}");
		});
		assertBusinessError(() -> client.generateSuggestion("system", "user"),
			ErrorCode.AI_PROVIDER_ERROR);

		server.removeContext("/v1/chat/completions");
		server.createContext("/v1/chat/completions", exchange -> {
			requests.incrementAndGet();
			respond(exchange, 200,
				"{\"extra\":true,\"choices\":[{\"message\":{\"content\":\"recovered\"}}]}");
		});
		assertThat(client.generateSuggestion("system", "user")).isEqualTo("recovered");
		assertThat(requests).hasValue(7);
	}

	@Test
	void mapsConnectionFailureToProviderError() throws IOException {
		int unusedPort;
		try (ServerSocket socket = new ServerSocket(0)) {
			unusedPort = socket.getLocalPort();
		}
		AiProperties properties = properties(1024, 1, Duration.ofSeconds(1));
		properties.setBaseUrl(URI.create("http://localhost:" + unusedPort + "/v1"));
		OpenAiCompatibleClient client = client(properties);

		assertBusinessError(() -> client.generateSuggestion("system", "user"),
			ErrorCode.AI_PROVIDER_ERROR);
	}

	@Test
	void limitsConcurrentCallsAndReleasesPermit() throws Exception {
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		server.createContext("/v1/chat/completions", exchange -> {
			entered.countDown();
			await(release);
			respond(exchange, 200,
				"{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}");
		});
		OpenAiCompatibleClient client = client(properties(1024, 1, Duration.ofSeconds(2)));
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			Future<String> first = executor.submit(() -> client.generateSuggestion("system", "user"));
			assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
			assertBusinessError(() -> client.generateSuggestion("system", "user"),
				ErrorCode.RATE_LIMITED);
			release.countDown();
			assertThat(first.get(2, TimeUnit.SECONDS)).isEqualTo("ok");
			assertThat(client.generateSuggestion("system", "user")).isEqualTo("ok");
		} finally {
			release.countDown();
			executor.shutdownNow();
			assertThat(executor.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
		}
	}

	@Test
	void mapsReadTimeoutToProviderError() {
		AtomicInteger requests = new AtomicInteger();
		server.createContext("/v1/chat/completions", exchange -> {
			requests.incrementAndGet();
			try {
				Thread.sleep(300);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
			}
			respond(exchange, 200,
				"{\"choices\":[{\"message\":{\"content\":\"late\"}}]}");
		});
		OpenAiCompatibleClient client = client(properties(1024, 1, Duration.ofMillis(50)));

		assertBusinessError(() -> client.generateSuggestion("system", "user"),
			ErrorCode.AI_PROVIDER_ERROR);
		assertThat(requests).hasValue(1);
	}

	private OpenAiCompatibleClient client(AiProperties properties) {
		return new OpenAiCompatibleClient(properties, objectMapper, RestClient.builder());
	}

	private AiProperties properties(int maxBytes, int maxConcurrent, Duration readTimeout) {
		AiProperties properties = new AiProperties();
		properties.setEnabled(true);
		properties.setBaseUrl(URI.create("http://localhost:" + server.getAddress().getPort() + "/v1"));
		properties.setApiKey("secret-key");
		properties.setModel("test-model");
		properties.setConnectTimeout(Duration.ofSeconds(1));
		properties.setReadTimeout(readTimeout);
		properties.setMaxResponseBytes(maxBytes);
		properties.setMaxConcurrentRequests(maxConcurrent);
		return properties;
	}

	private static void respond(HttpExchange exchange, int status, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.sendResponseHeaders(status, bytes.length);
		exchange.getResponseBody().write(bytes);
		exchange.close();
	}

	private static void assertBusinessError(Runnable action, ErrorCode expected) {
		assertThatThrownBy(action::run)
			.isInstanceOf(BusinessException.class)
			.satisfies(exception -> assertThat(((BusinessException) exception).code())
				.isEqualTo(expected));
	}

	private static void await(CountDownLatch latch) {
		try {
			if (!latch.await(2, TimeUnit.SECONDS)) {
				throw new AssertionError("Timed out waiting for test latch");
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new AssertionError(exception);
		}
	}
}
