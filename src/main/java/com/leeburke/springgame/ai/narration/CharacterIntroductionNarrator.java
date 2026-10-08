package com.leeburke.springgame.ai.narration;

import java.util.Comparator;
import java.util.Locale;
import java.util.Map;

import com.leeburke.springgame.ai.AiGenerationSettings;
import com.leeburke.springgame.ai.AiProvider;
import com.leeburke.springgame.ai.AiRole;
import com.leeburke.springgame.mechanics.StatType;

/**
 * The Character Introduction Narrator role. It may invent soft history and impressions but no
 * mechanics, items, powers, obligations or places; the prompt states the limits and the output is
 * presentation only. On any failure it uses a deterministic introduction built from the same facts.
 */
public final class CharacterIntroductionNarrator {

	static final int MAX_LENGTH = 2000;

	private final TextRole role;

	public CharacterIntroductionNarrator(AiProvider provider, String instructions, int promptVersion,
			AiGenerationSettings settings) {
		this.role = new TextRole(AiRole.CHARACTER_INTRODUCTION, provider, instructions, promptVersion, settings);
	}

	public Narration narrate(CharacterIntroductionContext context) {
		return role.narrate(new Input(context), TextRole.maxLength(MAX_LENGTH), () -> fallback(context));
	}

	/** Restates the lore and the confirmed facts; strongest and weakest stat by value, ties in stat order. */
	public static String fallback(CharacterIntroductionContext context) {
		Comparator<Map.Entry<StatType, Integer>> byValue = Map.Entry.comparingByValue();
		Comparator<Map.Entry<StatType, Integer>> byOrder = Map.Entry.comparingByKey();
		StatType strongest = context.stats().entrySet().stream().sorted(byValue.reversed().thenComparing(byOrder))
				.findFirst().orElseThrow().getKey();
		StatType weakest = context.stats().entrySet().stream().sorted(byValue.thenComparing(byOrder))
				.findFirst().orElseThrow().getKey();
		StringBuilder text = new StringBuilder(String.join(" ", context.lore()));
		text.append(" Your name is ").append(context.name()).append('.');
		text.append(" Your greatest strength is ").append(word(strongest)).append("; your weakness is ").append(word(weakest)).append('.');
		if (!context.weapons().isEmpty()) {
			text.append(" You carry ").append(String.join(" and ", context.weapons()));
			if (!context.items().isEmpty()) {
				text.append(", along with ").append(String.join(" and ", context.items()));
			}
			text.append('.');
		}
		text.append(" You have the gift of ").append(context.passive()).append(" and the ability ").append(context.ability()).append('.');
		text.append(' ').append(context.fatedBand().phrase());
		return text.toString();
	}

	private static String word(StatType stat) {
		String name = stat.name().toLowerCase(Locale.ROOT);
		return Character.toUpperCase(name.charAt(0)) + name.substring(1);
	}

	record Input(CharacterIntroductionContext character) {
	}
}
