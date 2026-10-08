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
			String detail = describe(e);
			throw new DocumentParseException(detail.length() > MAX_DETAIL ? detail.substring(0, MAX_DETAIL) : detail);
		}
	}

	/**
	 * A model-safe description: the document field path (for example {@code steps[0].attack.target})
	 * and, when a document rule was broken, that rule's own message rather than Jackson's text,
	 * which names Java classes.
	 */
	static String describe(JacksonException e) {
		String problem = ruleViolation(e).orElseGet(() -> {
			String original = e.getOriginalMessage();
			return original == null || original.isBlank() ? "the document does not match the schema" : original;
		});
		String path = path(e);
		return path.isEmpty() ? problem : path + ": " + problem;
	}

	private static java.util.Optional<String> ruleViolation(Throwable e) {
		for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
			if (cause instanceof IllegalArgumentException || cause instanceof NullPointerException) {
				return java.util.Optional.ofNullable(cause.getMessage()).filter(message -> !message.isBlank());
			}
		}
		return java.util.Optional.empty();
	}

	private static String path(JacksonException e) {
		StringBuilder path = new StringBuilder();
		for (JacksonException.Reference reference : e.getPath()) {
			if (reference.getIndex() >= 0) {
				path.append('[').append(reference.getIndex()).append(']');
			} else if (reference.getPropertyName() != null) {
				if (!path.isEmpty()) {
					path.append('.');
				}
				path.append(reference.getPropertyName());
			}
		}
		return path.toString();
	}

	/** The document could not be parsed. The message describes the structure problem only. */
	public static final class DocumentParseException extends RuntimeException {
		DocumentParseException(String message) {
			super(message);
		}
	}
}
