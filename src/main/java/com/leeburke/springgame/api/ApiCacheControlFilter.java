package com.leeburke.springgame.api;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Marks every API response, including errors, {@code Cache-Control: no-store}: authenticated game
 * state must never be kept by a browser, proxy or service-worker cache.
 */
class ApiCacheControlFilter extends OncePerRequestFilter {

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith("/api/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
		chain.doFilter(request, response);
	}
}
