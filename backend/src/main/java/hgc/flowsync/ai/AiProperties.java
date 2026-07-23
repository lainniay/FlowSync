package hgc.flowsync.ai;

import java.net.URI;
import java.time.Duration;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("flowsync.ai")
public class AiProperties {

	private boolean enabled;
	private URI baseUrl;
	private String apiKey;
	private String model;
	private Duration connectTimeout = Duration.ofSeconds(3);
	private Duration readTimeout = Duration.ofSeconds(30);
	private ResponseFormat responseFormat = ResponseFormat.NONE;
	private int maxResponseBytes = 1_048_576;
	private int maxConcurrentRequests = 5;

	@AssertTrue(message = "AI configuration is invalid")
	public boolean isValid() {
		if (!enabled) {
			return true;
		}
		return baseUrl != null
			&& ("http".equalsIgnoreCase(baseUrl.getScheme())
				|| "https".equalsIgnoreCase(baseUrl.getScheme()))
			&& baseUrl.getHost() != null
			&& baseUrl.getUserInfo() == null
			&& baseUrl.getQuery() == null
			&& baseUrl.getFragment() == null
			&& apiKey != null && !apiKey.isBlank()
			&& model != null && !model.isBlank()
			&& connectTimeout != null && connectTimeout.isPositive()
			&& readTimeout != null && readTimeout.isPositive()
			&& responseFormat != null
			&& maxResponseBytes > 0 && maxResponseBytes <= 10_485_760
			&& maxConcurrentRequests > 0 && maxConcurrentRequests <= 100;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public URI getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(URI baseUrl) {
		this.baseUrl = baseUrl;
	}

	public String getApiKey() {
		return apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public Duration getConnectTimeout() {
		return connectTimeout;
	}

	public void setConnectTimeout(Duration connectTimeout) {
		this.connectTimeout = connectTimeout;
	}

	public Duration getReadTimeout() {
		return readTimeout;
	}

	public void setReadTimeout(Duration readTimeout) {
		this.readTimeout = readTimeout;
	}

	public ResponseFormat getResponseFormat() {
		return responseFormat;
	}

	public void setResponseFormat(ResponseFormat responseFormat) {
		this.responseFormat = responseFormat;
	}

	public int getMaxResponseBytes() {
		return maxResponseBytes;
	}

	public void setMaxResponseBytes(int maxResponseBytes) {
		this.maxResponseBytes = maxResponseBytes;
	}

	public int getMaxConcurrentRequests() {
		return maxConcurrentRequests;
	}

	public void setMaxConcurrentRequests(int maxConcurrentRequests) {
		this.maxConcurrentRequests = maxConcurrentRequests;
	}

	public enum ResponseFormat {
		NONE,
		JSON_OBJECT,
		JSON_SCHEMA
	}
}
