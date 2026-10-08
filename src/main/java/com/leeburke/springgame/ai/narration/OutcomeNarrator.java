package com.leeburke.springgame.ai.narration;

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
		return role.narrate(new Input(context), TextRole.maxLength(MAX_LENGTH), () -> OutcomeFallback.render(context));
	}

	record Input(OutcomeNarrationContext outcome) {
	}
}
