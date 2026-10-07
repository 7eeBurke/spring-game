package com.leeburke.springgame.content.world;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Small shared checks for authored world definitions. */
final class WorldText {

	private WorldText() {
	}

	static void requireDisplayName(String displayName, String owner) {
		if (displayName == null || displayName.isBlank()) {
			throw new IllegalArgumentException("Display name of " + owner + " must not be blank");
		}
	}

	/** Copies the list, rejecting nulls, duplicates and (if required) emptiness. */
	static <T> List<T> uniqueList(List<T> list, String what, boolean requireNonEmpty) {
		List<T> copy = List.copyOf(Objects.requireNonNull(list, what));
		if (requireNonEmpty && copy.isEmpty()) {
			throw new IllegalArgumentException(what + " must not be empty");
		}
		Set<T> seen = new HashSet<>();
		for (T item : copy) {
			if (!seen.add(item)) {
				throw new IllegalArgumentException("Duplicate entry in " + what + ": " + item);
			}
		}
		return copy;
	}
}
