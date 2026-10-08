package com.leeburke.springgame.api;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Refuses API request bodies over {@value #MAX_BYTES} bytes: by declared length up front, and while
 * reading for chunked bodies. Turn input is at most 500 characters, so legitimate bodies are tiny.
 */
class RequestSizeLimitFilter extends OncePerRequestFilter {

	static final int MAX_BYTES = 8 * 1024;
	private static final String TOO_LARGE = "{\"error\":{\"code\":\"INVALID_REQUEST\",\"message\":\"The request body is too large.\"}}";

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith("/api/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (request.getContentLengthLong() > MAX_BYTES) {
			response.setStatus(400);
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			response.getWriter().write(TOO_LARGE);
			return;
		}
		chain.doFilter(new LimitedRequest(request), response);
	}

	/** Fails reading past the limit; the JSON converter then reports an unreadable body (400). */
	private static final class LimitedRequest extends HttpServletRequestWrapper {

		private LimitedRequest(HttpServletRequest request) {
			super(request);
		}

		@Override
		public ServletInputStream getInputStream() throws IOException {
			ServletInputStream in = super.getInputStream();
			return new ServletInputStream() {
				private int read;

				@Override
				public int read() throws IOException {
					int b = in.read();
					if (b >= 0 && ++read > MAX_BYTES) {
						throw new IOException("Request body too large");
					}
					return b;
				}

				@Override
				public int read(byte[] buffer, int offset, int length) throws IOException {
					int n = in.read(buffer, offset, length);
					if (n > 0 && (read += n) > MAX_BYTES) {
						throw new IOException("Request body too large");
					}
					return n;
				}

				@Override
				public boolean isFinished() {
					return in.isFinished();
				}

				@Override
				public boolean isReady() {
					return in.isReady();
				}

				@Override
				public void setReadListener(ReadListener listener) {
					in.setReadListener(listener);
				}
			};
		}
	}
}
