package com.leeburke.springgame.mechanics;

import java.util.Objects;

/**
 * An immutable, complete set of the five stats. Every stat is always present.
 * <p>
 * The point budget (27 for player characters) is a character-generation rule and is
 * deliberately not enforced here.
 */
public record StatBlock(
		StatValue might,
		StatValue agility,
		StatValue perception,
		StatValue arcana,
		StatValue resolve) {

	public StatBlock {
		Objects.requireNonNull(might, "might");
		Objects.requireNonNull(agility, "agility");
		Objects.requireNonNull(perception, "perception");
		Objects.requireNonNull(arcana, "arcana");
		Objects.requireNonNull(resolve, "resolve");
	}

	/** Exhaustive switch: adding a {@link StatType} without mapping it here fails to compile. */
	public StatValue get(StatType type) {
		return switch (type) {
			case MIGHT -> might;
			case AGILITY -> agility;
			case PERCEPTION -> perception;
			case ARCANA -> arcana;
			case RESOLVE -> resolve;
		};
	}
}
