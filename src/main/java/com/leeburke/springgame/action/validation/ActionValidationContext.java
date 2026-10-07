package com.leeburke.springgame.action.validation;

import java.util.Objects;
import java.util.Set;

import com.leeburke.springgame.action.Refs;
import com.leeburke.springgame.world.view.PlayerSceneView;

/**
 * Everything action validation may consult: the player-safe scene view, the player's current owned
 * references and the currently known incoming-attack references. Never authoritative scene state,
 * hidden content, scene or exit-destination identities, or persistence types.
 */
public record ActionValidationContext(
		PlayerSceneView view,
		PlayerActionReferences references,
		Set<String> incomingAttacks) {

	public ActionValidationContext {
		Objects.requireNonNull(view, "view");
		Objects.requireNonNull(references, "references");
		incomingAttacks = Set.copyOf(Objects.requireNonNull(incomingAttacks, "incomingAttacks"));
		incomingAttacks.forEach(ref -> Refs.require(ref, "Incoming attack reference"));
	}
}
