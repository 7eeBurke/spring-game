package com.leeburke.springgame.action.resolution;

import java.util.Map;
import java.util.Objects;

import com.leeburke.springgame.action.validation.PlayerActionReferences;
import com.leeburke.springgame.character.PlayerCharacterState;
import com.leeburke.springgame.world.PlayerLocation;
import com.leeburke.springgame.world.SceneState;

/**
 * Authoritative backend facts for resolving one validated intent. Backend-only: never shown to the
 * interpreter and never used for validation (which sees only the player-safe view). Contains no
 * persistence types, revisions or scene identities beyond the player location.
 */
public record ActionResolutionContext(
		PlayerCharacterState player,
		SceneState scene,
		PlayerLocation location,
		PlayerActionReferences references,
		Map<TargetProfileKey, TargetCombatProfile> targetProfiles,
		Map<String, IncomingAttack> incomingAttacks) {

	public ActionResolutionContext {
		Objects.requireNonNull(player, "player");
		Objects.requireNonNull(scene, "scene");
		Objects.requireNonNull(location, "location");
		Objects.requireNonNull(references, "references");
		targetProfiles = Map.copyOf(Objects.requireNonNull(targetProfiles, "targetProfiles"));
		incomingAttacks = Map.copyOf(Objects.requireNonNull(incomingAttacks, "incomingAttacks"));
		if (!scene.hasZone(location.zoneId())) {
			throw new IllegalArgumentException("Player location zone " + location.zoneId() + " is not in the scene");
		}
		incomingAttacks.forEach((ref, attack) -> {
			if (!ref.equals(attack.ref())) {
				throw new IllegalArgumentException("Incoming attack keyed " + ref + " has reference " + attack.ref());
			}
		});
	}
}
