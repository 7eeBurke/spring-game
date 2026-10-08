package com.leeburke.springgame.game.service;

import java.util.Objects;

/**
 * A turn request's answer as stored JSON: a success (200) or a stored rejection. Replays return
 * exactly the same status and body.
 */
public record TurnReply(int status, String json) {
	public TurnReply {
		Objects.requireNonNull(json, "json");
	}
}
