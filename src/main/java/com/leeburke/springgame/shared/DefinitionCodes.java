package com.leeburke.springgame.shared;

import java.util.regex.Pattern;

/**
 * The stable definition-code format shared by static content and world instances: upper snake
 * case, for example {@code WAR_HAMMER} or {@code THE_LAST_LANTERN}.
 */
public final class DefinitionCodes {

	public static final Pattern CODE_FORMAT = Pattern.compile("[A-Z][A-Z0-9_]*");

	private DefinitionCodes() {
	}

	/**
	 * @param label what the value is, used to start the error message (for example "Definition code")
	 * @throws IllegalArgumentException if the code is null or not upper snake case
	 */
	public static String requireCode(String code, String label) {
		if (code == null || !CODE_FORMAT.matcher(code).matches()) {
			throw new IllegalArgumentException(
					label + " must be upper snake case (" + CODE_FORMAT.pattern() + "), but was: "
							+ (code == null ? "null" : "\"" + code + "\""));
		}
		return code;
	}
}
