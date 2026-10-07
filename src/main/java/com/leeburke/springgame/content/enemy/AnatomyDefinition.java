package com.leeburke.springgame.content.enemy;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.leeburke.springgame.mechanics.BodyPart;
import com.leeburke.springgame.shared.DefinitionCodes;

/**
 * Which body parts a kind of creature has. Static content: an enemy's body contains exactly these
 * parts. Interaction values per part (anatomy modifiers, vital parts) are deferred.
 */
public record AnatomyDefinition(String code, List<BodyPart> bodyParts) {

	public AnatomyDefinition {
		DefinitionCodes.requireCode(code, "Anatomy code");
		bodyParts = List.copyOf(Objects.requireNonNull(bodyParts, "bodyParts"));
		if (bodyParts.isEmpty()) {
			throw new IllegalArgumentException("Anatomy " + code + " must have at least one body part");
		}
		if (EnumSet.copyOf(bodyParts).size() != bodyParts.size()) {
			throw new IllegalArgumentException("Anatomy " + code + " lists a body part more than once");
		}
	}

	public Set<BodyPart> parts() {
		return EnumSet.copyOf(bodyParts);
	}

	public boolean has(BodyPart part) {
		return bodyParts.contains(Objects.requireNonNull(part, "part"));
	}
}
