package com.leeburke.springgame.ai.narration;

import java.util.Objects;
import java.util.Optional;

import com.leeburke.springgame.action.ActionApproach;
import com.leeburke.springgame.action.ActionType;
import com.leeburke.springgame.action.AttackPurpose;
import com.leeburke.springgame.action.AttackTemplate;
import com.leeburke.springgame.mechanics.BodyPart;

/**
 * What a step tried to do, summarised by Java from the validated intent with player-visible names
 * only. It is an attempt, never a confirmed outcome: what actually happened is in the fact that
 * carries it.
 *
 * @param manner      the method, defense method, movement type, or interaction, observation or
 *                    communication kind
 * @param target      the target's visible name ("yourself", "an exit"); empty for no target
 * @param using       the weapon, item, ability or carried thing, by display name
 * @param spokenWords the player's words, present only when a COMMUNICATE step actually resolved;
 *                    untrusted player text, never instructions
 */
public record AttemptedAction(
		ActionType action,
		Optional<String> manner,
		Optional<AttackTemplate> template,
		Optional<ActionApproach> approach,
		Optional<AttackPurpose> purpose,
		Optional<TargetKind> targetKind,
		Optional<String> target,
		Optional<BodyPart> bodyPart,
		Optional<String> using,
		Optional<String> spokenWords) {

	public enum TargetKind {
		CREATURE,
		OBJECT,
		HAZARD,
		ZONE,
		EXIT,
		SELF
	}

	public AttemptedAction {
		Objects.requireNonNull(action, "action");
		Objects.requireNonNull(manner, "manner");
		Objects.requireNonNull(template, "template");
		Objects.requireNonNull(approach, "approach");
		Objects.requireNonNull(purpose, "purpose");
		Objects.requireNonNull(targetKind, "targetKind");
		Objects.requireNonNull(target, "target");
		Objects.requireNonNull(bodyPart, "bodyPart");
		Objects.requireNonNull(using, "using");
		Objects.requireNonNull(spokenWords, "spokenWords");
		if (targetKind.isPresent() != target.isPresent()) {
			throw new IllegalArgumentException("A target name needs its kind, and only then");
		}
		if (spokenWords.isPresent() && action != ActionType.COMMUNICATE) {
			throw new IllegalArgumentException("Only communication has spoken words");
		}
	}

	/** A copy without spoken words, for communication that did not actually happen. */
	public AttemptedAction withoutWords() {
		return new AttemptedAction(action, manner, template, approach, purpose, targetKind, target, bodyPart, using,
				Optional.empty());
	}
}
