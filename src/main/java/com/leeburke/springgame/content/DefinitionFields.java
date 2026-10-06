package com.leeburke.springgame.content;

import java.util.regex.Pattern;

/** Shared validation for the code and display name every static definition carries. */
final class DefinitionFields {

	/** Upper snake case, for example {@code WAR_HAMMER}. */
	static final Pattern CODE_FORMAT = Pattern.compile("[A-Z][A-Z0-9_]*");

	private DefinitionFields() {
	}

	static String requireCode(String code) {
		if (code == null || !CODE_FORMAT.matcher(code).matches()) {
			throw new IllegalArgumentException(
					"Definition code must be upper snake case (" + CODE_FORMAT.pattern() + "), but was: " + quoted(code));
		}
		return code;
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
