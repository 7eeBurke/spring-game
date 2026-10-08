package com.leeburke.springgame.game.service;

/** Stable API error codes with their HTTP status. Game-input rejections are 422. */
public enum ErrorCode {
	INVALID_REQUEST(400),
	UNAUTHORIZED(401),
	RUN_NOT_FOUND(404),
	STALE_VIEW(409),
	REQUEST_IN_PROGRESS(409),
	IDEMPOTENCY_KEY_REUSED(409),
	TOKEN_IN_USE(409),
	RUN_FINISHED(409),
	RUN_INITIALIZING(409),
	ACTION_NOT_SUPPORTED(422),
	INTERPRETATION_FAILED(422),
	INVALID_COMMAND(422),
	DEFENSE_REQUIRED(422),
	DEFENSE_NOT_RESOLVED(422),
	RATE_LIMITED(429),
	INTERNAL_ERROR(500),
	SERVICE_UNAVAILABLE(503);

	private final int status;

	ErrorCode(int status) {
		this.status = status;
	}

	public int status() {
		return status;
	}
}
