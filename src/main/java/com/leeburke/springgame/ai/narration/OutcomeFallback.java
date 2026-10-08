package com.leeburke.springgame.ai.narration;

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
			text.add(sentence(fact));
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
			case NarrationFact.PlayerLeftScene l -> "You leave through the exit and arrive in the " + l.arrivalZone()
					+ " of " + withArticle(l.destinationScene()) + ".";
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
				case LEFT_SCENE -> "You are gone before you can " + phrase(c.attempt());
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
}
