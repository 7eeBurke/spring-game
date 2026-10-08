package com.leeburke.springgame.api;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import com.leeburke.springgame.game.service.ErrorCode;
import com.leeburke.springgame.game.service.GameException;
import com.leeburke.springgame.game.service.RunAccessService;

/**
 * Authenticates every request to {@code /api/v1/runs/{runId}/**} before the controller runs: the
 * bearer token's hash must belong to the run in the path. A lightweight {@link HandlerInterceptor}
 * rather than Spring Security, because the only credential is one opaque token per run. Exceptions
 * thrown here are rendered by {@link ApiExceptionHandler} like any controller error.
 */
@Component
class RunTokenInterceptor implements HandlerInterceptor {

	private final RunAccessService access;

	RunTokenInterceptor(RunAccessService access) {
		this.access = Objects.requireNonNull(access, "access");
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		@SuppressWarnings("unchecked")
		Map<String, String> variables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
		String runId = variables == null ? null : variables.get("runId");
		if (runId == null) {
			return true;
		}
		UUID id;
		try {
			id = UUID.fromString(runId);
		} catch (IllegalArgumentException e) {
			throw new GameException(ErrorCode.RUN_NOT_FOUND, "Run not found.");
		}
		access.authorize(id, request.getHeader(HttpHeaders.AUTHORIZATION));
		return true;
	}
}
