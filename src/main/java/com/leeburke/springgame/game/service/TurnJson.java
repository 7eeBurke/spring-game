package com.leeburke.springgame.game.service;

import com.leeburke.springgame.shared.StrictJson;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * JSON for stored turn documents (mechanics summaries and responses). Strict like other persisted
 * documents, except that null is accepted for absent optional values.
 */
final class TurnJson {

	private final JsonMapper mapper = StrictJson.createMapper().rebuild()
			.disable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES).build();

	String write(Object value) {
		return mapper.writeValueAsString(value);
	}

	<T> T read(String json, Class<T> type) {
		return mapper.readValue(json, type);
	}
}
