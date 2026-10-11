package com.leeburke.springgame.ai.narration;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.StringJoiner;

import com.leeburke.springgame.action.ActionType;
import com.leeburke.springgame.action.CommunicationKind;
import com.leeburke.springgame.ai.narration.AttemptedAction.TargetKind;
import com.leeburke.springgame.mechanics.BodyPart;

/**
 * Deterministic factual narration: one plain sentence per confirmed fact, then any run-ending line.
 * Attempts are described as attempts; only resolved communication repeats the player's words.
 */
public final class OutcomeFallback {

	private OutcomeFallback() {
	}

	public static String render(OutcomeNarrationContext context) {
		StringJoiner text = new StringJoiner(" ");
		for (NarrationFact fact : context.facts()) {
			String sentence = sentence(fact);
			if (!sentence.isBlank()) {
				text.add(sentence);
			}
		}
		context.terminal().ifPresent(terminal -> text.add(switch (terminal) {
			case TerminalFact.PlayerDied died -> "Your strength fails. The run ends here.";
			case TerminalFact.GuardianDefeated defeated -> "The " + defeated.name() + " falls. The Hollow Chapel is still at last.";
		}));
		return text.toString();
	}

	static String sentence(NarrationFact fact) {
		return switch (fact) {
			case NarrationFact.PlayerAttacked a -> a.noContact()
					? "Your " + a.weaponName() + " misses the " + a.targetName() + "."
					: a.contactWithoutDamage()
							? "Your " + a.weaponName() + " catches the " + a.targetName() + part(a.bodyPart()) + ", but does no harm."
							: "Your " + a.weaponName() + " lands a " + word(a.contact().name()) + " hit on the " + a.targetName()
									+ part(a.bodyPart()) + ", dealing " + a.hpDamage() + " damage.";
			case NarrationFact.PlayerDefended d -> d.avoided()
					? "You " + word(d.method().name()) + " against the " + d.attackerName() + "'s attack and take no harm."
					: d.contactWithoutDamage()
							? "The " + d.attackerName() + "'s attack reaches you, but does no harm."
							: "The " + d.attackerName() + "'s attack hits you" + part(d.bodyPart()) + " for " + d.hpDamage()
									+ " damage.";
			case NarrationFact.PlayerMoved m -> "You move from the " + m.fromZone() + " to the " + m.toZone() + ".";
			case NarrationFact.PlayerStayed s -> s.attemptedZone()
					.map(zone -> "You cannot reach the " + zone + " from here, and stay in the " + s.zone() + ".")
					.orElse("You hold your ground in the " + s.zone() + ".");
			case NarrationFact.PlayerLeftScene l -> "You make your way on into " + withArticle(l.destinationScene())
					+ ", and find yourself in the " + l.arrivalZone() + ".";
			case NarrationFact.PlayerArrived a -> a.surroundings().describeAround();
			case NarrationFact.WalkedTo w -> "You make your way " + (w.passage().isEmpty() ? "" : "through " + w.passage() + " ")
					+ "to " + w.to().phrase() + "."
					+ (w.to().description().isEmpty() ? "" : " " + w.to().description());
			case NarrationFact.StayedPut s -> s.couldNotReach()
					.map(target -> "You find no way from " + s.here().phrase() + " to " + target.phrase() + ", and stay where you are.")
					.orElse("You stay where you are.");
			case NarrationFact.CrossedInto c -> "You pass through " + c.through() + "."
					+ (c.sceneDescription().isEmpty() ? "" : " " + c.sceneDescription())
					+ (c.arrival().description().isEmpty() ? "" : " " + c.arrival().description())
					+ c.behind().map(b -> " Behind you is " + b + c.behindLeadsTo().map(to -> ", " + to).orElse("") + ".").orElse("");
			case NarrationFact.Perceived p -> p.unchanged() ? "Nothing has changed around you." : perception(p.perception());
			case NarrationFact.InspectedPlace p -> !p.inSight()
					? capitalised(p.place().phrase()) + " is too far away to make out from here."
					: (p.place().description().isEmpty() ? "You look closely at " + p.place().phrase() + "." : p.place().description())
							+ (p.ways().isEmpty() ? ""
									: " Ways from " + (p.where().equals("here") ? "here" : p.place().phrase()) + ": "
											+ join(p.ways().stream().map(NarrationFact.Lead::what).toList()) + ".");
			case NarrationFact.Inspected i -> !i.inSight()
					? "The " + i.name() + " is too far away to make out from here."
					: (i.description().isEmpty() ? "You look closely at the " + i.name() + "." : i.description())
							+ i.state().map(st -> " It is " + st + ".").orElse("");
			case NarrationFact.ContainerOpened o -> o.alreadyOpen()
					? "The " + o.name() + " is already open" + contents(o.contents())
					: "You open the " + o.name() + contents(o.contents());
			case NarrationFact.OpenedContainer o -> (o.alreadyOpen() ? "The " + o.name() + " is already open" : "You open the " + o.name())
					+ contents(o.contents().stream().map(NarrationFact.Found::item).toList())
					+ o.contents().stream().map(NarrationFact.Found::description).filter(d -> !d.isEmpty()).map(d -> " " + d)
							.collect(java.util.stream.Collectors.joining());
			case NarrationFact.ItemTaken t -> "You take the " + t.item() + " from the " + t.from() + " and tuck it into your belt.";
			case NarrationFact.TookItem t -> "You take the " + t.item() + " from the " + t.from() + " and tuck it into your belt."
					+ (t.description().isEmpty() ? "" : " " + t.description());
			case NarrationFact.SoughtWays w -> soughtWays(w);
			case NarrationFact.InteractionFailed f -> switch (f.reason()) {
				case OUT_OF_REACH -> "The " + f.name() + " is out of reach from here" + f.where().map(w -> ": it is in " + w).orElse("") + ".";
				case CLOSED -> "The " + f.name() + " is shut.";
				case EMPTY -> "There is nothing in the " + f.name() + ".";
				case NO_ROOM -> "Your belt has no room for anything more.";
			};
			case NarrationFact.PlayerObserved o -> o.surroundings().describe();
			case NarrationFact.ExitNotReached x -> "The exit is not within reach from the " + x.zone()
					+ "; you would have to move to it first.";
			case NarrationFact.PlayerSpoke s -> {
				String said = "You " + s.addressee().map(name -> speech(s.kind()) + " the " + name)
						.orElse(word(s.kind().name()) + " aloud");
				yield s.attempt().spokenWords().map(words -> said + ": \"" + words + "\"").orElse(said + ".");
			}
			case NarrationFact.StepCancelled c -> (switch (c.reason()) {
				case PLAYER_DOWN -> "You fall before you can " + phrase(c.attempt());
				case TARGET_DEFEATED -> "There is no need to " + phrase(c.attempt()) + ": it has already fallen";
				case LEFT_SCENE -> "You have moved on before you can " + phrase(c.attempt());
				case PREVIOUS_STEP_NOT_SUCCESSFUL -> "You do not follow through with your attempt to " + phrase(c.attempt());
			}) + (c.action() == ActionType.COMMUNICATE ? ", and nothing is said." : ".");
			case NarrationFact.StepHadNoEffect n -> "Nothing comes of your attempt to " + phrase(n.attempt()) + ".";
		};
	}

	/** The attempted action as a verb phrase, for example "attack the Hollow Acolyte". */
	static String phrase(AttemptedAction attempt) {
		String target = attempt.target().map(name -> targetPhrase(attempt.targetKind().orElseThrow(), name)).orElse("");
		String using = attempt.using().orElse("");
		String verb = switch (attempt.action()) {
			case ATTACK -> "attack";
			case DEFEND -> attempt.manner().map(OutcomeFallback::word).orElse("defend");
			case MOVE -> attempt.manner().filter("HOLD_POSITION"::equals).isPresent() ? "hold your position" : "move";
			case INTERACT, OBSERVE -> attempt.manner().map(OutcomeFallback::word).orElse(word(attempt.action().name()));
			case USE_ABILITY, USE_ITEM -> "use " + (using.isEmpty() ? "it" : "the " + using);
			case COMMUNICATE -> attempt.manner()
					.map(m -> target.isEmpty() ? word(m) : speech(CommunicationKind.valueOf(m))).orElse("speak");
		};
		String connector = switch (attempt.action()) {
			case MOVE -> attempt.targetKind().filter(TargetKind.ZONE::equals).isPresent() ? " to " : " toward ";
			case DEFEND -> " behind ";
			case USE_ABILITY, USE_ITEM -> " on ";
			default -> " ";
		};
		String phrase = target.isEmpty() ? verb : verb + connector + target;
		if (attempt.action() == ActionType.INTERACT && !using.isEmpty() && target.isEmpty()) {
			phrase += " the " + using;
		}
		return phrase + attempt.bodyPart().map(p -> " (" + word(p.name()) + ")").orElse("");
	}

	private static String targetPhrase(TargetKind kind, String name) {
		return switch (kind) {
			case SELF, EXIT -> name;
			default -> "the " + name;
		};
	}

	private static String part(Optional<BodyPart> part) {
		return part.map(p -> " in the " + word(p.name())).orElse("");
	}

	private static String speech(CommunicationKind kind) {
		return switch (kind) {
			case SAY -> "speak to";
			case ASK -> "question";
			case THREATEN -> "threaten";
			case PERSUADE -> "try to persuade";
			case DECEIVE -> "try to deceive";
			case BARGAIN -> "bargain with";
		};
	}

	private static String word(String constant) {
		return constant.toLowerCase(Locale.ROOT).replace('_', ' ');
	}

	/** "the Ossuary", but "The Last Lantern" as named. */
	private static String withArticle(String name) {
		return name.startsWith("The ") ? name : "the " + name;
	}

	/** What is in sight, in the world's words: the place, the places beside it, things, creatures, ways on. */
	/**
	 * Where the player can go, from what they know: unexplored ways first, then places not yet
	 * visited, then where they have been. With no known lead, it says so without claiming there is
	 * no other way.
	 */
	static String soughtWays(NarrationFact.SoughtWays w) {
		StringJoiner text = new StringJoiner(" ");
		String inSight = perception(w.perception());
		if (!inSight.isBlank()) {
			text.add(inSight);
		}
		// Every unexplored way the player knows of, each with where it is (the perception no longer lists ways).
		if (!w.unexplored().isEmpty()) {
			text.add("Not yet explored: " + join(w.unexplored().stream().map(OutcomeFallback::lead).toList()) + ".");
		}
		if (!w.unvisited().isEmpty()) {
			text.add("Not yet visited: " + join(w.unvisited().stream().map(OutcomeFallback::lead).toList()) + ".");
		}
		if (!w.visited().isEmpty()) {
			text.add("Already walked: " + join(w.visited()) + ".");
		}
		if (w.nothingKnownLeft()) {
			text.add("You know of no way here that you have not already tried.");
		}
		return text.toString();
	}

	private static String lead(NarrationFact.Lead lead) {
		String where = lead.where().equals("here") ? "right here"
				: lead.what().equals(lead.where()) ? "" : "from " + lead.where();
		String route = lead.steps() <= 1 ? "" : lead.via().map(v -> "by way of " + v).orElse("");
		String to = lead.leadsTo().filter(t -> !t.equals(Surroundings.UNEXPLORED) && !t.isBlank()).map(t -> "(" + t + ")").orElse("");
		return java.util.stream.Stream.of(lead.what(), where, route, to).filter(part -> !part.isEmpty())
				.reduce((a, b) -> a + (b.startsWith("(") ? " " : ", ") + b).orElse(lead.what());
	}

	private static String join(java.util.List<String> parts) {
		if (parts.size() <= 1) {
			return String.join("", parts);
		}
		return String.join("; ", parts.subList(0, parts.size() - 1)) + "; and " + parts.getLast();
	}

	static String perception(Perception p) {
		StringBuilder text = new StringBuilder();
		p.here().ifPresent(here -> text.append(here.description().isEmpty() ? "You stand in " + here.phrase() + "." : here.description()));
		for (Perception.Beside beside : p.beside()) {
			text.append(' ').append(beside.passage().isEmpty() ? "From here you can reach " + beside.place().phrase() + "."
					: capitalised(beside.passage()) + " leads to " + beside.place().phrase() + ".");
		}
		for (Perception.SeenThing thing : p.things()) {
			text.append(' ').append(capitalised(PlaceDescriber.article(thing.name().toLowerCase(java.util.Locale.ROOT))))
					.append(thing.here() ? " is here" : " is in " + thing.where())
					.append(thing.state().map(st -> ", " + st).orElse("")).append('.');
		}
		for (Perception.SeenThing hazard : p.hazards()) {
			text.append(' ').append(hazard.description().isEmpty() ? capitalised(PlaceDescriber.article(hazard.name().toLowerCase(java.util.Locale.ROOT)))
					: hazard.description().replaceAll("\\.$", "")).append(hazard.here() ? " here." : " in " + hazard.where() + ".");
		}
		for (Perception.SeenCreature creature : p.creatures()) {
			text.append(" The ").append(creature.name()).append(creature.fallen() ? " lies fallen" : " is")
					.append(creature.here() ? " here." : " in " + creature.where() + ".");
		}
		for (Perception.WayOut way : p.ways()) {
			text.append(' ').append(capitalised(way.passage())).append(way.here() ? "" : ", reached from " + way.where())
					.append(way.leadsTo().isBlank() ? "" : ": " + way.leadsTo()).append('.');
		}
		return text.toString().strip();
	}

	private static String contents(java.util.List<String> items) {
		return items.isEmpty() ? "; it is empty."
				: ". Inside is " + String.join(" and ", items.stream().map(i -> PlaceDescriber.article(i)).toList()) + ".";
	}

	private static String capitalised(String text) {
		return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}
}
