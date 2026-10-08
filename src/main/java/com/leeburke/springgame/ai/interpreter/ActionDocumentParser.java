package com.leeburke.springgame.ai.interpreter;

import com.leeburke.springgame.shared.StrictJson;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Strictly parses model output into an {@link ActionDocument}. Uses the project's strict JSON
 * configuration with one difference: an explicit JSON null reaches the record constructors, which
 * reject it everywhere except the documented nullable slots. Missing fields, unknown fields,
 * wrong types, unknown or wrongly cased enum values and trailing content all still fail.
 */
public final class ActionDocumentParser {

	private static final JsonMapper MAPPER = StrictJson.createMapper().rebuild()
			.disable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
			.build();

	private static final int MAX_DETAIL = 300;

	private ActionDocumentParser() {
	}

	/** @throws DocumentParseException with a short, model-safe description of the problem */
	public static ActionDocument parse(String json) {
		if (json == null || json.isBlank()) {
			throw new DocumentParseException("the output was empty");
		}
		try {
			return MAPPER.readValue(json, ActionDocument.class);
		} catch (JacksonException e) {
			String detail = e.getOriginalMessage();
			if (detail == null || detail.isBlank()) {
				detail = "the document does not match the schema";
			}
			throw new DocumentParseException(detail.length() > MAX_DETAIL ? detail.substring(0, MAX_DETAIL) : detail);
		}
	}

	/** The document could not be parsed. The message describes the structure problem only. */
	public static final class DocumentParseException extends RuntimeException {
		DocumentParseException(String message) {
			super(message);
		}
	}
}
