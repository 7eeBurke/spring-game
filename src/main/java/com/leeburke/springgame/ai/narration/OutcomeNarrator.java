package com.leeburke.springgame.ai.narration;

import java.util.List;
import java.util.Optional;

import com.leeburke.springgame.ai.AiGenerationSettings;
import com.leeburke.springgame.ai.AiProvider;
import com.leeburke.springgame.ai.AiRole;

/**
 * The Outcome Narrator role, with modes NORMAL, RUN_DEATH and RUN_VICTORY. It receives only the
 * confirmed facts of an {@link OutcomeNarrationContext} and returns presentation text; on any
 * failure or overlong output it uses {@link OutcomeFallback}.
 */
public final class OutcomeNarrator {

	static final int MAX_LENGTH = 1200;

	private final TextRole role;

	public OutcomeNarrator(AiProvider provider, String instructions, int promptVersion, AiGenerationSettings settings) {
		this.role = new TextRole(AiRole.OUTCOME_NARRATOR, provider, instructions, promptVersion, settings);
	}

	public Narration narrate(OutcomeNarrationContext context) {
		if (onlyAnUnchangedLook(context)) {
			// Nothing happened worth a storyteller: the game says so itself, with no model call.
			return new Narration(OutcomeFallback.render(context), NarrationSource.DIRECT, role.promptVersion(), Optional.empty());
		}
		return role.narrate(new Input(forTheModel(context)), TextRole.maxLength(MAX_LENGTH), () -> OutcomeFallback.render(context));
	}

	/**
	 * What the model is sent: the context with every unchanged look cut to where the player stands, so
	 * nothing already told is sent to be told again. The stored facts keep the whole perception.
	 */
	static OutcomeNarrationContext forTheModel(OutcomeNarrationContext context) {
		List<NarrationFact> facts = context.facts().stream().map(f -> f instanceof NarrationFact.Perceived p && p.unchanged()
				? new NarrationFact.Perceived(p.step(), p.attempt(), new Perception(
						p.perception().here().map(h -> new Perception.PlaceRef(h.label(), h.phrase(), "")), List.of(), List.of(), List.of(),
						List.of(), List.of()), true)
				: f).toList();
		return new OutcomeNarrationContext(context.mode(), context.currentZone(), context.overall(), facts, context.terminal(),
				context.untrustedPlayerWording());
	}

	/** A turn whose every fact is a look that found nothing changed (no other event, and the run goes on). */
	static boolean onlyAnUnchangedLook(OutcomeNarrationContext context) {
		return context.mode() == NarrationMode.NORMAL && context.terminal().isEmpty() && !context.facts().isEmpty()
				&& context.facts().stream().allMatch(f -> f instanceof NarrationFact.Perceived p && p.unchanged());
	}

	record Input(OutcomeNarrationContext outcome) {
	}
}
