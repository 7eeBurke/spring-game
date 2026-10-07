package com.leeburke.springgame.character;

import java.util.Objects;

import com.leeburke.springgame.content.AbilityDefinition;
import com.leeburke.springgame.content.PassiveDefinition;
import com.leeburke.springgame.mechanics.StatBlock;

/**
 * The player character's ongoing state, as loaded from persistence and, later, as changed by play.
 * <p>
 * Unlike {@link GeneratedCharacter} (a creation result), this allows any structurally valid state:
 * current HP below max, any body severities, and any tool belt within capacity. Immutable; there
 * are no damage, healing or inventory operations yet.
 */
public record PlayerCharacterState(
		String name,
		StatBlock stats,
		Fated fated,
		int maxHp,
		int currentHp,
		PlayerBody body,
		PassiveDefinition passive,
		AbilityDefinition ability,
		ToolBelt toolBelt) {

	public PlayerCharacterState {
		Objects.requireNonNull(name, "name");
		if (name.isBlank()) {
			throw new IllegalArgumentException("Character name must not be blank");
		}
		Objects.requireNonNull(stats, "stats");
		Objects.requireNonNull(fated, "fated");
		Objects.requireNonNull(body, "body");
		Objects.requireNonNull(passive, "passive");
		Objects.requireNonNull(ability, "ability");
		Objects.requireNonNull(toolBelt, "toolBelt");
		if (maxHp < 1) {
			throw new IllegalArgumentException("Max HP must be at least 1, but was " + maxHp);
		}
		if (currentHp < 0 || currentHp > maxHp) {
			throw new IllegalArgumentException("Current HP must be between 0 and max HP " + maxHp + ", but was " + currentHp);
		}
	}

	/** The initial ongoing state of a newly generated character. */
	public static PlayerCharacterState from(GeneratedCharacter generated) {
		Objects.requireNonNull(generated, "generated");
		return new PlayerCharacterState(
				generated.name(),
				generated.stats(),
				generated.fated(),
				generated.maxHp(),
				generated.currentHp(),
				generated.body(),
				generated.passive(),
				generated.ability(),
				generated.toolBelt());
	}
}
