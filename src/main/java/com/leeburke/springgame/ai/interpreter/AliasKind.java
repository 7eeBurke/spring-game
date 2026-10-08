package com.leeburke.springgame.ai.interpreter;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The typed, request-scoped aliases shown to the Action Interpreter instead of backend references:
 * {@code <kind>_<n>} with n from 1, for example {@code entity_2} or {@code weapon_1}.
 */
public enum AliasKind {
	ZONE,
	ENTITY,
	OBJECT,
	HAZARD,
	EXIT,
	WEAPON,
	ITEM,
	ABILITY,
	ATTACK;

	public static final Pattern FORMAT = Pattern.compile(
			"^(zone|entity|object|hazard|exit|weapon|item|ability|attack)_([1-9][0-9]*)$");

	public String prefix() {
		return name().toLowerCase(Locale.ROOT);
	}

	public String alias(int number) {
		if (number < 1) {
			throw new IllegalArgumentException("Alias numbers start at 1");
		}
		return prefix() + "_" + number;
	}

	/** The kind named by a well-formed alias, or empty for anything malformed. */
	public static Optional<AliasKind> kindOf(String alias) {
		if (alias == null) {
			return Optional.empty();
		}
		Matcher matcher = FORMAT.matcher(alias);
		if (!matcher.matches()) {
			return Optional.empty();
		}
		return Arrays.stream(values()).filter(kind -> kind.prefix().equals(matcher.group(1))).findFirst();
	}
}
