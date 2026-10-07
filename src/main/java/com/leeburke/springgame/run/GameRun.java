package com.leeburke.springgame.run;

import java.util.Objects;
import java.util.UUID;

import com.leeburke.springgame.character.PlayerCharacterState;

/**
 * Immutable read model of a persisted run.
 *
 * @param id      internal run identity; not a runtime item ID and not exposed to AI roles
 * @param runSeed the seed the run's generation derives from
 */
public record GameRun(UUID id, long runSeed, PlayerCharacterState playerCharacter) {

	public GameRun {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(playerCharacter, "playerCharacter");
	}
}
