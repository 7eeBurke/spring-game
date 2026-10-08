package com.leeburke.springgame.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.leeburke.springgame.game.service.ErrorCode;
import com.leeburke.springgame.game.service.GameException;
import com.leeburke.springgame.game.view.ApiErrorResponse;

/**
 * Renders every API failure as {@code {"error": {code, message, reason?, hint?}}} with a stable
 * code. Never returns stack traces, exception messages from libraries or provider text; unexpected
 * failures are logged server-side (without request bodies or tokens) and answered with
 * INTERNAL_ERROR.
 */
@RestControllerAdvice
class ApiExceptionHandler {

	private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(GameException.class)
	ResponseEntity<ApiErrorResponse> game(GameException e) {
		return ResponseEntity.status(e.code().status()).body(e.body());
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ApiErrorResponse> unreadable(HttpMessageNotReadableException e) {
		return error(ErrorCode.INVALID_REQUEST, "The request body is not valid JSON of the expected shape.");
	}

	@ExceptionHandler(TypeMismatchException.class)
	ResponseEntity<ApiErrorResponse> typeMismatch(TypeMismatchException e) {
		return error(ErrorCode.INVALID_REQUEST, "The request is not valid.");
	}

	@ExceptionHandler({ DataAccessResourceFailureException.class, TransientDataAccessException.class })
	ResponseEntity<ApiErrorResponse> unavailable(Exception e) {
		LOG.warn("Database unavailable: {}", e.getClass().getSimpleName());
		return error(ErrorCode.SERVICE_UNAVAILABLE, "The game is temporarily unavailable; try again shortly.");
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiErrorResponse> unexpected(Exception e) {
		if (e instanceof ErrorResponse framework) {
			// Spring MVC's own client errors: unknown path, wrong method or media type, missing header.
			int status = framework.getStatusCode().value();
			ErrorCode code = status == 404 ? ErrorCode.RUN_NOT_FOUND : ErrorCode.INVALID_REQUEST;
			return ResponseEntity.status(status).body(ApiErrorResponse.of(code.name(),
					status == 404 ? "Not found." : "The request is not valid.", null, null));
		}
		LOG.error("Unexpected API failure", e);
		return error(ErrorCode.INTERNAL_ERROR, "Something went wrong.");
	}

	private static ResponseEntity<ApiErrorResponse> error(ErrorCode code, String message) {
		return ResponseEntity.status(code.status()).body(ApiErrorResponse.of(code.name(), message, null, null));
	}
}
