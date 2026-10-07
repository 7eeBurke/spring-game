package com.leeburke.springgame.content;

import com.leeburke.springgame.shared.DefinitionCodes;

/** Shared validation for the code and display name every static definition carries. */
final class DefinitionFields {

	private DefinitionFields() {
	}

	static String requireCode(String code) {
		return DefinitionCodes.requireCode(code, "Definition code");
	}

	static String requireDisplayName(String displayName) {
		if (displayName == null || displayName.isBlank()) {
			throw new IllegalArgumentException("Display name must not be null or blank, but was: " + quoted(displayName));
		}
		return displayName;
	}

	private static String quoted(String value) {
		return value == null ? "null" : "\"" + value + "\"";
	}
}
