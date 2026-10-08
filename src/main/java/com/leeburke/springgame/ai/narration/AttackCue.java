package com.leeburke.springgame.ai.narration;

import java.util.List;
import java.util.Locale;

import com.leeburke.springgame.action.AttackTemplate;

/**
 * The physical cue a player needs to choose a defense against an incoming attack, derived only from
 * its {@link AttackTemplate}. Narration of the attack must keep at least one of the cue's keywords.
 * No height, width or force geometry is implied beyond the template itself.
 */
public enum AttackCue {
	DIRECT_THRUST("a straight thrust driven directly at you", List.of("thrust", "lunge", "straight")),
	HORIZONTAL_SWEEP("a wide swing sweeping in from the side", List.of("sweep", "swing", "side")),
	LOW_SWEEP("a low sweep close to the ground", List.of("low")),
	DESCENDING_STRIKE("an overhead strike coming down from above", List.of("overhead", "above", "down")),
	FAST_CUT("a quick, fast cut", List.of("quick", "fast", "swift")),
	HEAVY_BLOW("a heavy, crushing blow", List.of("heavy", "crushing")),
	HOOKING_PULL("a hooking motion meant to catch and pull", List.of("hook")),
	PROJECTED("a bolt hurled through the air toward you", List.of("projected", "hurled", "flies", "bolt"));

	private final String phrase;
	private final List<String> keywords;

	AttackCue(String phrase, List<String> keywords) {
		this.phrase = phrase;
		this.keywords = keywords;
	}

	/** Exhaustive: a new template without a cue fails to compile. */
	public static AttackCue of(AttackTemplate template) {
		return switch (template) {
			case THRUST -> DIRECT_THRUST;
			case HORIZONTAL_SWING -> HORIZONTAL_SWEEP;
			case LOW_SWEEP -> LOW_SWEEP;
			case OVERHEAD_STRIKE -> DESCENDING_STRIKE;
			case QUICK_SLASH -> FAST_CUT;
			case HEAVY_SMASH -> HEAVY_BLOW;
			case HOOK_AND_PULL -> HOOKING_PULL;
			case PROJECTED_ATTACK -> PROJECTED;
		};
	}

	public String phrase() {
		return phrase;
	}

	public List<String> keywords() {
		return keywords;
	}

	/** True when the text names this cue: it contains at least one keyword, ignoring case. */
	public boolean isConveyedBy(String text) {
		String lower = text.toLowerCase(Locale.ROOT);
		return keywords.stream().anyMatch(lower::contains);
	}
}
