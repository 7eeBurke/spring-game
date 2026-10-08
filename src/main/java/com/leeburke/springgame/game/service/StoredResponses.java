package com.leeburke.springgame.game.service;

import java.util.Optional;

import com.leeburke.springgame.game.view.GameView.NarrationView;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the few presentation fields that history needs from a stored turn response
 * ({@code run_turn.response}), by path rather than by binding the whole document, so responses
 * stored by earlier versions stay readable when the response gains fields.
 */
final class StoredResponses {

	private final JsonMapper mapper = JsonMapper.builder().build();

	/** The turn's outcome narration. */
	Optional<NarrationView> narration(String responseJson) {
		return narrationAt(mapper.readTree(responseJson).path("narration"));
	}

	/** The narration of the attack left pending by the turn (the view after the turn). */
	Optional<NarrationView> pendingAttackNarration(String responseJson) {
		return narrationAt(mapper.readTree(responseJson).path("view").path("pendingAttack").path("narration"));
	}

	private static Optional<NarrationView> narrationAt(JsonNode node) {
		JsonNode text = node.path("text");
		JsonNode source = node.path("source");
		if (!node.isObject() || !text.isValueNode() || text.isNull() || !source.isValueNode() || source.isNull()) {
			return Optional.empty();
		}
		return Optional.of(new NarrationView(text.asString(), source.asString()));
	}
}
