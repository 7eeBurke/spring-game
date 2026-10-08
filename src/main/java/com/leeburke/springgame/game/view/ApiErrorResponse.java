package com.leeburke.springgame.game.view;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The body of every API error: a stable code, a player-safe message, and optionally a stable reason
 * and a hint. Never internals, stack traces or provider text.
 */
public record ApiErrorResponse(Body error) {

	public static ApiErrorResponse of(String code, String message, String reason, String hint) {
		return new ApiErrorResponse(new Body(code, message, reason, hint));
	}

	public record Body(String code, String message, @JsonInclude(JsonInclude.Include.NON_NULL) String reason,
			@JsonInclude(JsonInclude.Include.NON_NULL) String hint) {
	}
}
