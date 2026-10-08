package com.leeburke.springgame.game.service;

import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.leeburke.springgame.persistence.RunSessionStore;

/**
 * Checks that a request's bearer token belongs to the run in its path. A missing or malformed token
 * is UNAUTHORIZED; a token for another run, or an unknown one, is RUN_NOT_FOUND, so a token never
 * reveals whether some other run exists.
 */
@Service
public class RunAccessService {

	private final RunSessionStore sessions;

	public RunAccessService(RunSessionStore sessions) {
		this.sessions = Objects.requireNonNull(sessions, "sessions");
	}

	public void authorize(UUID runId, String authorizationHeader) {
		String token = RunTokens.fromHeader(authorizationHeader)
				.orElseThrow(() -> new GameException(ErrorCode.UNAUTHORIZED, "A run token is required."));
		if (!RunTokens.wellFormed(token)) {
			throw new GameException(ErrorCode.UNAUTHORIZED, "The run token is not valid.");
		}
		boolean owns = sessions.findRunByTokenHash(RunTokens.hash(token)).filter(runId::equals).isPresent();
		if (!owns) {
			throw new GameException(ErrorCode.RUN_NOT_FOUND, "Run not found.");
		}
	}
}
