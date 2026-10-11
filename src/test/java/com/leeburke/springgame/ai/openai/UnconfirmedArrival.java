package com.leeburke.springgame.ai.openai;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Judges model prose for the opt-in probes: does a narration claim the player entered, reached or
 * arrived at a place that Java did not confirm? Naming the place as somewhere ahead is allowed
 * ("the road runs on toward the Hollow Chapel"); only an arrival claim counts, and a negated or
 * future one ("you have not yet reached the chapel", "before you can enter the chapel") does not.
 */
final class UnconfirmedArrival {

	private static final String VERB = "(?:enter(?:s|ed|ing)?|step(?:s|ped|ping)?\\s+(?:into|inside)|arriv(?:e|es|ed|ing)\\s+(?:at|in)"
			+ "|reach(?:es|ed|ing)?|inside|within)";
	private static final Pattern HEDGE = Pattern.compile("\\b(?:not|never|yet\\s+to|before|until|will|would)\\b|n't");

	private UnconfirmedArrival() {
	}

	/**
	 * @param place one word of the place's name, for example "chapel"; "{place} Road" is never the place
	 */
	static boolean claimsArrival(String narration, String place) {
		Pattern claim = Pattern.compile("\\b" + VERB + "\\s+(?:the\\s+)?(?:\\w+\\s+)?" + Pattern.quote(place) + "\\b(?!\\s+road)",
				Pattern.CASE_INSENSITIVE);
		for (String sentence : narration.split("[.!?;]+")) {
			Matcher matcher = claim.matcher(sentence);
			while (matcher.find()) {
				String before = sentence.substring(0, matcher.start()).toLowerCase(Locale.ROOT);
				if (!HEDGE.matcher(before).find()) {
					return true;
				}
			}
		}
		return false;
	}
}
