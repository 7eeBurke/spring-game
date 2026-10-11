package com.leeburke.springgame.ai.narration;

/**
 * Test-only access to the production Outcome Narrator's request and acceptance rules, so the
 * narrator benchmark sends exactly what the game sends and rates only text the game would display.
 */
public final class NarratorBenchmarkAccess {

	private NarratorBenchmarkAccess() {
	}

	/** The JSON user message the production narrator sends for this context. */
	public static String requestJson(OutcomeNarrationContext context) {
		return com.leeburke.springgame.ai.AiJson.write(new OutcomeNarrator.Input(OutcomeNarrator.forTheModel(context)));
	}

	/** True when the production narrator tells this context itself, with no model call (an unchanged look alone). */
	public static boolean toldDirectly(OutcomeNarrationContext context) {
		return OutcomeNarrator.onlyAnUnchangedLook(context);
	}

	/** The production acceptance rule: stripped text, not empty, within the narrator's length limit. */
	public static boolean accepted(String text) {
		String stripped = text.strip();
		return !stripped.isEmpty() && TextRole.maxLength(OutcomeNarrator.MAX_LENGTH).test(stripped);
	}

	public static int maxLength() {
		return OutcomeNarrator.MAX_LENGTH;
	}

	/** What the game shows instead when a narration is rejected. */
	public static String fallback(OutcomeNarrationContext context) {
		return OutcomeFallback.render(context);
	}
}
