package com.leeburke.springgame.game;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.leeburke.springgame.action.ActionIntent;
import com.leeburke.springgame.action.ActionPayload;
import com.leeburke.springgame.action.ActionStep;
import com.leeburke.springgame.action.ActionTarget;
import com.leeburke.springgame.action.ObservationKind;
import com.leeburke.springgame.action.TargetSpecificity;
import com.leeburke.springgame.ai.interpreter.ActionInterpretationContext;
import com.leeburke.springgame.ai.interpreter.AliasKind;
import com.leeburke.springgame.ai.interpreter.InterpretationSetup;

/**
 * A narrow safety net for one interpreter mistake: a close look (OBSERVE INSPECT) aimed at an object
 * the player's words never mention. In the playtest, "I see if the hole is safe to drop down", said in
 * the cracked belfry, was read as an inspection of a crate in the next place.
 * <ul>
 * <li>An object the player names (any word of its name) is kept.</li>
 * <li>Otherwise, if the words name a feature of a place in sight (its phrase, its authored description,
 * or how a way out of it looks; "hole", "crack", "gap" and the like count as one kind of opening), the
 * look is at that place, the player's own place first.</li>
 * <li>Otherwise a pronoun ("look at it") keeps the object, and anything else is unclear: nothing is
 * substituted.</li>
 * </ul>
 * Free text only; commands are exact. Deterministic Java over the player's own words and the
 * player-known context, never anything hidden. It never moves the player.
 */
public final class InspectionTargetCheck {

	/** Words for an opening in the ground or a wall: any one names the same kind of feature as another. */
	private static final Set<String> OPENING = Set.of("hole", "opening", "gap", "crack", "split", "drop", "pit", "shaft", "chasm",
			"hatch", "breach");
	private static final Set<String> PRONOUNS = Set.of("it", "that", "this", "them", "these", "those", "its");
	/** Words too common to tell one thing from another. */
	private static final Set<String> COMMON = Set.of("look", "looks", "check", "safe", "whether", "there", "here", "where", "what",
			"down", "into", "from", "with", "would", "could", "should", "might", "carefully", "closely", "see", "examine", "inspect",
			"study", "peer", "gaze", "around", "about", "through", "this", "that", "your", "mine", "away", "below", "above", "under",
			"over", "then", "just", "more", "less", "very", "much", "some", "like", "feel", "test", "the", "and", "for", "can");

	public sealed interface Result permits Unchanged, Retargeted, Unclear {
	}

	public record Unchanged() implements Result {
	}

	/** The look is at this place instead. */
	public record Retargeted(ActionIntent intent) implements Result {
	}

	/** The words name nothing the player knows: ask, never guess. */
	public record Unclear() implements Result {
	}

	private InspectionTargetCheck() {
	}

	public static Result check(String input, ActionIntent intent, InterpretationSetup setup) {
		Objects.requireNonNull(intent, "intent");
		Set<String> words = words(input);
		ActionInterpretationContext context = setup.context();
		List<ActionStep> steps = new ArrayList<>();
		boolean changed = false;
		for (ActionStep step : intent.steps()) {
			if (step.payload() instanceof ActionPayload.ObservePayload observe && observe.kind() == ObservationKind.INSPECT
					&& observe.target() instanceof ActionTarget.ObjectTarget object) {
				Optional<String> alias = setup.aliases().aliasOf(AliasKind.OBJECT, object.objectId());
				Optional<ActionInterpretationContext.Thing> thing = alias.flatMap(a -> context.objects().stream()
						.filter(t -> t.alias().equals(a)).findFirst());
				if (thing.isPresent() && !named(thing.get().name(), words)) {
					// A feature of a place in sight wins over a stray pronoun ("is it safe to drop down the hole?").
					Optional<String> place = featuredPlace(words, context);
					if (place.isEmpty()) {
						if (words.stream().anyMatch(PRONOUNS::contains)) {
							steps.add(step);
							continue;
						}
						return new Unclear();
					}
					String zoneId = setup.aliases().resolve(AliasKind.ZONE, place.get());
					steps.add(new ActionStep(step.id(), step.sequence(), step.relation(),
							new ActionPayload.ObservePayload(ObservationKind.INSPECT, new ActionTarget.ZoneTarget(zoneId, TargetSpecificity.INFERRED))));
					changed = true;
					continue;
				}
			}
			steps.add(step);
		}
		return changed ? new Retargeted(new ActionIntent(intent.schemaVersion(), intent.responseToAttack(), steps, intent.confidence(),
				intent.unresolvedReferences())) : new Unchanged();
	}

	/** Any significant word of the thing's name is in the player's words ("crate" for a Crate). */
	private static boolean named(String name, Set<String> words) {
		return words(name).stream().anyMatch(words::contains);
	}

	/**
	 * The place in sight (the player's own first, then those beside it) whose phrase, description or
	 * ways out share a significant word with the player's words, or an opening word.
	 */
	private static Optional<String> featuredPlace(Set<String> words, ActionInterpretationContext context) {
		Set<String> asked = words.stream().filter(w -> !COMMON.contains(w) && w.length() >= 3).collect(Collectors.toSet());
		boolean opening = asked.stream().anyMatch(OPENING::contains);
		Set<String> inSight = new HashSet<>();
		inSight.add(context.currentZone());
		context.connections().forEach(c -> {
			if (c.zoneA().equals(context.currentZone())) {
				inSight.add(c.zoneB());
			} else if (c.zoneB().equals(context.currentZone())) {
				inSight.add(c.zoneA());
			}
		});
		List<ActionInterpretationContext.Zone> ordered = context.zones().stream().filter(z -> inSight.contains(z.alias()))
				.sorted((a, b) -> a.alias().equals(context.currentZone()) ? -1 : b.alias().equals(context.currentZone()) ? 1
						: a.alias().compareTo(b.alias()))
				.toList();
		for (ActionInterpretationContext.Zone zone : ordered) {
			Set<String> features = new HashSet<>();
			features.addAll(words(Optional.ofNullable(zone.phrase()).orElse("")));
			features.addAll(words(Optional.ofNullable(zone.description()).orElse("")));
			context.exits().stream().filter(x -> x.zone().equals(zone.alias()))
					.forEach(x -> features.addAll(words(Optional.ofNullable(x.passage()).orElse(""))));
			features.removeAll(COMMON);
			boolean shared = asked.stream().anyMatch(features::contains);
			boolean sameOpening = opening && features.stream().anyMatch(OPENING::contains);
			if (shared || sameOpening) {
				return Optional.of(zone.alias());
			}
		}
		return Optional.empty();
	}

	static Set<String> words(String text) {
		if (text == null) {
			return Set.of();
		}
		String folded = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
				.replace("'", "").replace("’", "");
		return Arrays.stream(folded.split("[^a-z]+")).filter(w -> !w.isEmpty()).collect(Collectors.toSet());
	}
}
