package com.leeburke.springgame.game.service;

import java.util.Objects;
import java.util.Optional;

import com.leeburke.springgame.game.view.ApiErrorResponse;

/** A request the game refuses, with a stable code and a player-safe message. Never carries internals. */
public class GameException extends RuntimeException {

	private final ErrorCode code;
	private final Optional<String> reason;
	private final Optional<String> hint;

	public GameException(ErrorCode code, String message) {
		this(code, message, Optional.empty(), Optional.empty());
	}

	public GameException(ErrorCode code, String message, Optional<String> reason, Optional<String> hint) {
		super(message, null, false, false);
		this.code = Objects.requireNonNull(code, "code");
		this.reason = Objects.requireNonNull(reason, "reason");
		this.hint = Objects.requireNonNull(hint, "hint");
	}

	public ErrorCode code() {
		return code;
	}

	public ApiErrorResponse body() {
		return ApiErrorResponse.of(code.name(), getMessage(), reason.orElse(null), hint.orElse(null));
	}
}
