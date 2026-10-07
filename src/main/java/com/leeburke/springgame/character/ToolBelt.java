package com.leeburke.springgame.character;

import java.util.List;
import java.util.Objects;

/**
 * The tool belt's occupied slots, up to {@link #CAPACITY}. Immutable; slot positions and
 * add/remove/use behaviour are not modelled yet.
 */
public record ToolBelt(List<ToolBeltEntry> entries) {

	public static final int CAPACITY = StartingCharacterRules.TOOL_BELT_CAPACITY;

	public ToolBelt {
		entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
		if (entries.size() > CAPACITY) {
			throw new IllegalArgumentException("A tool belt holds at most " + CAPACITY + " entries, but had " + entries.size());
		}
	}

	public int occupiedSlots() {
		return entries.size();
	}

	public int emptySlots() {
		return CAPACITY - entries.size();
	}
}
