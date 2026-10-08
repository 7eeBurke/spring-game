package com.leeburke.springgame.game;

import java.util.Optional;

import com.leeburke.springgame.action.ActionIntent;
import com.leeburke.springgame.action.ActionPayload;
import com.leeburke.springgame.action.resolution.ResolvedOutcome;
import com.leeburke.springgame.action.resolution.StepOutcome;
import com.leeburke.springgame.action.resolution.StepResult;
import com.leeburke.springgame.action.resolution.StepStatus;

/**
 * The mandatory-defense rule. While an attack is pending, a turn must open with a DEFEND responding
 * to it ({@link #answers}), and that DEFEND must actually reach RESOLVED against it
 * ({@link #resolved}); otherwise nothing from the turn is committed and the attack stays pending. A
 * failed defense check is still RESOLVED, and its damage applies. There is no way to ignore an
 * attack: an undefended-attack rule is deferred.
 */
public final class DefenseGate {

	private DefenseGate() {
	}

	/** Before resolution: the intent's first step is a DEFEND that responds to the pending attack. */
	public static boolean answers(ActionIntent intent, Optional<PendingAttack> pending) {
		if (pending.isEmpty()) {
			return true;
		}
		return intent.responseToAttack().filter(pending.get().attack().ref()::equals).isPresent()
				&& intent.steps().getFirst().payload() instanceof ActionPayload.DefendPayload;
	}

	/** After resolution: the first step resolved as a defense against the pending attack. */
	public static boolean resolved(ResolvedOutcome outcome, Optional<PendingAttack> pending) {
		if (pending.isEmpty()) {
			return true;
		}
		StepOutcome first = outcome.steps().getFirst();
		return first.status() == StepStatus.RESOLVED
				&& first.result().filter(StepResult.DefenseResult.class::isInstance)
						.map(r -> ((StepResult.DefenseResult) r).attackRef().equals(pending.get().attack().ref()))
						.orElse(false);
	}
}
