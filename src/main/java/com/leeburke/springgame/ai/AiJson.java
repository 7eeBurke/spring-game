package com.leeburke.springgame.ai;

import com.leeburke.springgame.shared.StrictJson;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Serializes AI input documents. Player text only ever appears as a JSON string value, never as instructions. */
public final class AiJson {

	private static final JsonMapper MAPPER = StrictJson.createMapper();

	private AiJson() {
	}

	public static String write(Object document) {
		try {
			return MAPPER.writeValueAsString(document);
		} catch (JacksonException e) {
			throw new IllegalStateException("Could not serialize AI input " + document.getClass().getSimpleName(), e);
		}
	}
}
