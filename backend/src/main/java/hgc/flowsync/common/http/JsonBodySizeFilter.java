package hgc.flowsync.common.http;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import hgc.flowsync.common.error.ErrorCode;
import hgc.flowsync.common.error.ProblemDetailResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class JsonBodySizeFilter extends OncePerRequestFilter {

	private final ProblemDetailResponseWriter problemWriter;
	private final int maxBodyBytes;

	public JsonBodySizeFilter(
		ProblemDetailResponseWriter problemWriter,
		@Value("${flowsync.http.max-json-body-bytes:1048576}") int maxBodyBytes) {
		this.problemWriter = problemWriter;
		this.maxBodyBytes = maxBodyBytes;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		try {
			return request.getContentType() == null
				|| !MediaType.APPLICATION_JSON.isCompatibleWith(
					MediaType.parseMediaType(request.getContentType()));
		} catch (InvalidMediaTypeException exception) {
			return true;
		}
	}

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain) throws ServletException, IOException {
		if (request.getContentLengthLong() > maxBodyBytes) {
			writePayloadTooLarge(request, response);
			return;
		}
		byte[] body = request.getInputStream().readNBytes(maxBodyBytes + 1);
		if (body.length > maxBodyBytes) {
			writePayloadTooLarge(request, response);
			return;
		}
		filterChain.doFilter(new CachedBodyRequest(request, body), response);
	}

	private void writePayloadTooLarge(HttpServletRequest request, HttpServletResponse response)
		throws IOException {
		problemWriter.write(
			request,
			response,
			ErrorCode.PAYLOAD_TOO_LARGE,
			ErrorCode.PAYLOAD_TOO_LARGE.status().getReasonPhrase(),
			ErrorCode.PAYLOAD_TOO_LARGE.detail());
	}

	private static final class CachedBodyRequest extends HttpServletRequestWrapper {

		private final byte[] body;

		private CachedBodyRequest(HttpServletRequest request, byte[] body) {
			super(request);
			this.body = body;
		}

		@Override
		public ServletInputStream getInputStream() {
			ByteArrayInputStream input = new ByteArrayInputStream(body);
			return new ServletInputStream() {
				@Override
				public boolean isFinished() {
					return input.available() == 0;
				}

				@Override
				public boolean isReady() {
					return true;
				}

				@Override
				public void setReadListener(ReadListener listener) {
					throw new UnsupportedOperationException("Non-blocking reads are not supported");
				}

				@Override
				public int read() {
					return input.read();
				}
			};
		}

		@Override
		public BufferedReader getReader() {
			String encoding = getCharacterEncoding();
			Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
			return new BufferedReader(new InputStreamReader(getInputStream(), charset));
		}

		@Override
		public int getContentLength() {
			return body.length;
		}

		@Override
		public long getContentLengthLong() {
			return body.length;
		}
	}
}
