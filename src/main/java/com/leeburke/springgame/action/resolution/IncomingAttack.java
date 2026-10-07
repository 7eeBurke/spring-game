package com.leeburke.springgame.action.resolution;

import java.util.Objects;
import java.util.Optional;

import com.leeburke.springgame.action.AttackTemplate;
import com.leeburke.springgame.action.Refs;
import com.leeburke.springgame.mechanics.BodyPart;
import com.leeburke.springgame.mechanics.Effectiveness;

/**
 * Confirmed backend facts about an attack already aimed at the player, which a DEFEND step responds
 * to. Which attack an enemy chooses, and how these values are produced, belongs to the enemy model.
 * Attack geometry (height, width, speed and so on) is not yet part of resolution.
 *
 * @param difficulty     the DC of the player's defense check
 * @param targetBodyPart where the attack lands if it makes contact, when known
 */
public record IncomingAttack(
		String ref,
		String attackerEntityId,
		AttackTemplate template,
		int difficulty,
		int baseDamage,
		int weaponTrauma,
		Effectiveness effectiveness,
		int attackFormModifier,
		int anatomyInteractionModifier,
		Optional<BodyPart> targetBodyPart) {

	public IncomingAttack {
		Refs.require(ref, "Incoming attack reference");
		Refs.require(attackerEntityId, "Attacker entity id");
		Objects.requireNonNull(template, "template");
		Objects.requireNonNull(effectiveness, "effectiveness");
		Objects.requireNonNull(targetBodyPart, "targetBodyPart");
		if (baseDamage < 0 || weaponTrauma < 0) {
			throw new IllegalArgumentException("Base damage and weapon trauma cannot be negative");
		}
	}
}
