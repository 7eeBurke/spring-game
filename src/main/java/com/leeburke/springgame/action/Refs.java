package com.leeburke.springgame.action;

/**
 * The rule for every string reference in an action contract (step IDs, scene-local IDs, opaque
 * player references, incoming-attack references): non-null, non-blank, already trimmed.
 */
public final class Refs {

	private Refs() {
	}

	public static String require(String value, String label) {
		if (value == null) {
			throw new NullPointerException(label + " must not be null");
		}
		if (value.isBlank() || !value.equals(value.strip())) {
			throw new IllegalArgumentException(label + " must be non-blank with no leading or trailing whitespace");
		}
		return value;
	}
}
