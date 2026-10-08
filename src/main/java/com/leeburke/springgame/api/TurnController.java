package com.leeburke.springgame.api;

import java.util.Objects;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.leeburke.springgame.game.service.ErrorCode;
import com.leeburke.springgame.game.service.GameException;
import com.leeburke.springgame.game.service.TurnReply;
import com.leeburke.springgame.game.service.TurnService;

/**
 * Submits a turn. The answer is returned exactly as stored, so a retry with the same
 * {@code Idempotency-Key} replays the same status and body without applying anything again.
 */
@RestController
class TurnController {

	private final TurnService turns;

	TurnController(TurnService turns) {
		this.turns = Objects.requireNonNull(turns, "turns");
	}

	@PostMapping(path = "/api/v1/runs/{runId}/turns", produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<String> submit(@PathVariable UUID runId,
			@RequestHeader(name = RunController.IDEMPOTENCY_HEADER, required = false) String key, @RequestBody TurnRequest body) {
		if (body == null || body.input() == null || body.stateVersion() == null) {
			throw new GameException(ErrorCode.INVALID_REQUEST, "The body must be {\"input\": \"...\", \"stateVersion\": n}.");
		}
		TurnReply reply = turns.submit(runId, requestKey(key), body.input(), body.stateVersion());
		return ResponseEntity.status(reply.status()).contentType(MediaType.APPLICATION_JSON).body(reply.json());
	}

	private static UUID requestKey(String header) {
		try {
			return UUID.fromString(Objects.requireNonNull(header));
		} catch (IllegalArgumentException | NullPointerException e) {
			throw new GameException(ErrorCode.INVALID_REQUEST, "An Idempotency-Key header with a UUID is required.");
		}
	}

	/** @param input free text or a slash command, 1 to 500 characters */
	record TurnRequest(String input, Long stateVersion) {
	}
}
