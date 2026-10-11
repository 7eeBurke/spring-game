package com.leeburke.springgame.ai.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.leeburke.springgame.action.ActionIntent;
import com.leeburke.springgame.action.ActionPayload;
import com.leeburke.springgame.action.ActionStep;
import com.leeburke.springgame.action.ActionTarget;
import com.leeburke.springgame.ai.AiGenerationSettings;
import com.leeburke.springgame.ai.AiRole;
import com.leeburke.springgame.ai.PromptLibrary;
import com.leeburke.springgame.ai.interpreter.ActionInterpretationResult;
import com.leeburke.springgame.ai.interpreter.ActionInterpretationResult.Interpreted;
import com.leeburke.springgame.ai.interpreter.ActionInterpreter;
import com.leeburke.springgame.ai.interpreter.InterpretationContextBuilder;
import com.leeburke.springgame.ai.interpreter.InterpretationSetup;
import com.leeburke.springgame.ai.interpreter.InterpreterFixtures;
import com.leeburke.springgame.action.validation.ActionValidator;
import com.leeburke.springgame.game.RouteGrounding;
import com.leeburke.springgame.action.ActionApproach;
import com.leeburke.springgame.action.ActionType;
import com.leeburke.springgame.action.resolution.OverallResult;
import com.leeburke.springgame.ai.narration.AttemptedAction;
import com.leeburke.springgame.ai.narration.Narration;
import com.leeburke.springgame.ai.narration.NarrationFact;
import com.leeburke.springgame.ai.narration.NarrationMode;
import com.leeburke.springgame.ai.narration.NarrationSource;
import com.leeburke.springgame.ai.narration.OutcomeNarrationContext;
import com.leeburke.springgame.ai.narration.OutcomeNarrator;
import com.leeburke.springgame.world.view.PlayerSceneView;
import com.leeburke.springgame.world.view.PlayerSceneView.KnownExit;
import com.leeburke.springgame.world.view.PlayerSceneView.VisibleConnection;
import com.leeburke.springgame.world.view.PlayerSceneView.VisibleZone;

/**
 * Opt-in probe of the real interpreter on the exact phrases from the first playtest, in the hub as
 * a new run sees it (Lantern Hearth and Chapel Road, one road out). Excluded from the normal build;
 * needs OPENAI_API_KEY and GAME_AI_MODEL and is skipped without them. About 13 small calls. Run with:
 * {@code .\mvnw.cmd test -Dgroups=ai-smoke -DexcludedGroups=none -Dtest=OpeningInterpreterProbeTest}
 * <p>
 * Each case reports the model's <b>raw</b> reading (where it aimed), its attempts and, if it was
 * repaired, why; and the reading <b>after Java's route grounding</b>, exactly as a turn would use it.
 * The grounded reading must be right. Raw misreadings are counted and printed rather than hidden
 * behind the grounding that corrects them, and more than one repair in the run fails the probe: that
 * was the regression this probe found. Only shapes are printed, never the key or prompts; the first
 * answer of a repaired attempt is printed so it can become a regression fixture.
 */
@Tag("ai-smoke")
class OpeningInterpreterProbeTest {

	private static final String HEARTH = "lantern_hearth";
	private static final String ROAD = "chapel_road";
	private static final String EXIT = "road_to_chapel";
	private static final Map<String, String> LABELS = Map.of(EXIT, "the road to the Hollow Chapel");

	private static PlayerSceneView hub(String zone) {
		return new PlayerSceneView(zone, List.of(new VisibleZone(ROAD, "Chapel Road"), new VisibleZone(HEARTH, "Lantern Hearth")),
				List.of(new VisibleConnection(HEARTH, ROAD)), List.of(), List.of(), List.of(), List.of(new KnownExit(EXIT, ROAD)),
				List.of());
	}

	/** Where the model aimed. */
	enum Raw {
		LOOKS, ROAD_EXIT, ROAD_ZONE, HEARTH_ZONE, ONWARD, UNCLEAR, NOT_SUPPORTED, FAILED, OTHER
	}

	/** Where the player would end up once Java has grounded the reading. */
	enum Grounded {
		LOOKS, MOVES_TO_ROAD, TAKES_THE_ROAD, MOVES_TO_HEARTH, STAYS, ASKED, REFUSED, OTHER
	}

	private record Case(String input, String from, Set<Raw> raw, Set<Grounded> grounded) {
	}

	@Test
	void theOpeningPhrasesAreReadAsThePlayerMeantThem() {
		// Raw: what a careful reading under prompt v5 looks like. Grounded: where the player must end up,
		// with the player deciding when to cross into the Hollow Chapel.
		List<Case> cases = List.of(
				new Case("I look around. Look for signs of places to go", HEARTH, Set.of(Raw.LOOKS), Set.of(Grounded.LOOKS)),
				new Case("where can I go?", HEARTH, Set.of(Raw.LOOKS), Set.of(Grounded.LOOKS)),
				new Case("I continue forward", HEARTH, Set.of(Raw.ROAD_ZONE, Raw.ONWARD), Set.of(Grounded.MOVES_TO_ROAD)),
				new Case("I walk forward, keeping an eye on my surroundings to see if I spot anything", HEARTH,
						Set.of(Raw.ROAD_ZONE, Raw.ONWARD), Set.of(Grounded.MOVES_TO_ROAD)),
				new Case("I head toward the chapel, looking for its entrance", HEARTH, Set.of(Raw.ROAD_ZONE),
						Set.of(Grounded.MOVES_TO_ROAD)),
				new Case("I look around", ROAD, Set.of(Raw.LOOKS), Set.of(Grounded.LOOKS)),
				new Case("I continue onward until I find something", ROAD, Set.of(Raw.ONWARD, Raw.ROAD_ZONE, Raw.UNCLEAR),
						Set.of(Grounded.ASKED)),
				new Case("I enter the chapel", ROAD, Set.of(Raw.ROAD_EXIT), Set.of(Grounded.TAKES_THE_ROAD)),
				new Case("I enter the chapel", HEARTH, Set.of(Raw.ROAD_EXIT), Set.of(Grounded.TAKES_THE_ROAD)),
				new Case("I go inside", ROAD, Set.of(Raw.ROAD_EXIT, Raw.ONWARD), Set.of(Grounded.TAKES_THE_ROAD)),
				new Case("follow the road", HEARTH, Set.of(Raw.ROAD_ZONE, Raw.ONWARD), Set.of(Grounded.MOVES_TO_ROAD)),
				new Case("follow the road", ROAD, Set.of(Raw.ONWARD, Raw.ROAD_ZONE), Set.of(Grounded.ASKED)));

		List<String> report = new ArrayList<>();
		List<String> wrong = new ArrayList<>();
		List<String> rawMisreadings = new ArrayList<>();
		List<String> firstAnswers = new ArrayList<>();
		int repairs = 0;
		List<ActionInterpreter.InterpretationTrace> traces = new ArrayList<>();
		try (Session session = session(traces::add)) {
			for (Case c : cases) {
				traces.clear();
				PlayerSceneView view = hub(c.from());
				InterpretationSetup setup = new InterpretationContextBuilder(InterpreterFixtures.WORLD)
						.build(view, InterpreterFixtures.player(), List.of(), Set.of(), LABELS);
				ActionInterpretationResult result = session.interpreter().interpret(c.input(), setup);
				Optional<ActionInterpreter.InterpretationTrace> trace = traces.stream().findFirst();
				int attempts = trace.map(ActionInterpreter.InterpretationTrace::attempts).orElse(0);
				if (attempts > 1) {
					repairs++;
					firstAnswers.add('"' + c.input() + "\" " + trace.get().repairCause().orElse("-") + "\n    first answer: "
							+ trace.get().firstOutput() + "\n    problems: " + trace.get().firstProblems());
				}

				Raw raw = raw(result);
				Grounded grounded = grounded(c.input(), result, view, setup);
				String line = String.format("%-48s from %-15s raw %-13s attempts %d %-28s -> grounded %-15s %s", '"' + c.input() + '"',
						c.from(), raw, attempts, trace.flatMap(ActionInterpreter.InterpretationTrace::repairCause).orElse(""), grounded,
						shape(result));
				report.add(line);
				if (!c.raw().contains(raw)) {
					rawMisreadings.add(line + "   (raw expected " + c.raw() + ")");
				}
				if (!c.grounded().contains(grounded)) {
					wrong.add(line + "   (expected " + c.grounded() + ")");
				}
			}
		}
		System.out.println("Opening interpreter probe:\n  " + String.join("\n  ", report));
		System.out.println("RAW MODEL MISREADINGS: " + rawMisreadings.size() + " of " + cases.size()
				+ (rawMisreadings.isEmpty() ? "" : "\n  " + String.join("\n  ", rawMisreadings)));
		System.out.println("REPAIRS: " + repairs + " of " + cases.size()
				+ (firstAnswers.isEmpty() ? "" : "\n  " + String.join("\n  ", firstAnswers)));
		assertThat(wrong).as("grounded readings outside what the opening needs").isEmpty();
		assertThat(repairs).as("repairs (the first answer should normally be accepted)").isLessThanOrEqualTo(1);
	}

	private static Raw raw(ActionInterpretationResult result) {
		return switch (result) {
			case Interpreted interpreted -> aim(interpreted.validated().intent());
			case ActionInterpretationResult.Unclear unclear -> Raw.UNCLEAR;
			case ActionInterpretationResult.NotSupported unsupported -> Raw.NOT_SUPPORTED;
			case ActionInterpretationResult.Failed failed -> Raw.FAILED;
		};
	}

	private static Raw aim(ActionIntent intent) {
		List<ActionPayload> payloads = intent.steps().stream().map(ActionStep::payload).toList();
		if (payloads.stream().allMatch(p -> p instanceof ActionPayload.ObservePayload)) {
			return Raw.LOOKS;
		}
		Optional<ActionPayload.MovePayload> last = payloads.stream().filter(p -> p instanceof ActionPayload.MovePayload)
				.map(p -> (ActionPayload.MovePayload) p).reduce((a, b) -> b);
		if (last.isEmpty()) {
			return Raw.OTHER;
		}
		return switch (last.get().target()) {
			case ActionTarget.ExitTarget x when x.exitId().equals(EXIT) -> Raw.ROAD_EXIT;
			case ActionTarget.ZoneTarget z when z.zoneId().equals(ROAD) -> Raw.ROAD_ZONE;
			case ActionTarget.ZoneTarget z when z.zoneId().equals(HEARTH) -> Raw.HEARTH_ZONE;
			case ActionTarget.Unspecified u -> Raw.ONWARD;
			default -> Raw.OTHER;
		};
	}

	/** What TurnService does with the result: ground free text, validate again, or ask. */
	private static Grounded grounded(String input, ActionInterpretationResult result, PlayerSceneView view, InterpretationSetup setup) {
		RouteGrounding.Result grounding;
		Optional<ActionIntent> original;
		switch (result) {
			case Interpreted interpreted -> {
				original = Optional.of(interpreted.validated().intent());
				grounding = RouteGrounding.ground(input, original.get(), view, LABELS, Set.of());
			}
			case ActionInterpretationResult.Unclear unclear -> {
				original = Optional.empty();
				grounding = unclear.intent().map(i -> RouteGrounding.ground(input, i, view, LABELS, Set.of()))
						.orElseGet(() -> RouteGrounding.groundNamedPlace(input, view, LABELS, Set.of()));
			}
			default -> {
				return Grounded.REFUSED;
			}
		}
		ActionIntent intent;
		switch (grounding) {
			case RouteGrounding.Grounded g -> {
				if (new ActionValidator().validated(g.intent(), setup.validation()).isEmpty()) {
					return g.intent().unresolvedReferences().isEmpty() ? Grounded.REFUSED : Grounded.ASKED;
				}
				intent = g.intent();
			}
			case RouteGrounding.Ambiguous a -> {
				return Grounded.ASKED;
			}
			case RouteGrounding.Threshold t -> {
				return Grounded.ASKED;
			}
			case RouteGrounding.Unchanged u -> {
				if (original.isEmpty()) {
					return Grounded.ASKED;
				}
				intent = original.get();
			}
		}
		return classify(intent, view.currentZoneId());
	}

	private static String shape(ActionInterpretationResult result) {
		return switch (result) {
			case Interpreted interpreted -> interpreted.validated().intent().steps().stream().map(OpeningInterpreterProbeTest::describe)
					.collect(Collectors.joining(" ; "));
			case ActionInterpretationResult.Unclear unclear -> unclear.intent()
					.map(i -> i.steps().stream().map(OpeningInterpreterProbeTest::describe).collect(Collectors.joining(" ; ")))
					.orElse("(no steps)") + " unclear " + unclear.phrases().size() + " phrase(s)";
			default -> result.getClass().getSimpleName();
		};
	}

	/**
	 * Partial progress: "I enter the chapel" from the hearth moved the player only to Chapel Road. The
	 * real narrator must tell that arrival without claiming the chapel was entered, reached or arrived
	 * at; mentioning it as the way onward is allowed ({@link UnconfirmedArrival}).
	 */
	@Test
	void theNarratorNeverClaimsAChapelThePlayerHasNotReached() {
		try (Session session = session()) {
			AttemptedAction attempt = new AttemptedAction(ActionType.MOVE, Optional.of("ADVANCE"), Optional.empty(),
					Optional.of(ActionApproach.NORMAL), Optional.empty(), Optional.of(AttemptedAction.TargetKind.ZONE),
					Optional.of("Chapel Road"), Optional.empty(), Optional.empty(), Optional.empty());
			OutcomeNarrationContext context = new OutcomeNarrationContext(NarrationMode.NORMAL, "Chapel Road",
					OverallResult.COMPLETE_SUCCESS, List.of(new NarrationFact.PlayerMoved(1, attempt, "Lantern Hearth", "Chapel Road")),
					Optional.empty(), Optional.of("I enter the chapel"));

			Narration narration = session.narrator().narrate(context);

			System.out.println("Partial-progress narration (" + narration.source() + "):\n  " + narration.text());
			assertThat(narration.source()).as("the model, not the fallback, wrote it").isEqualTo(NarrationSource.AI);
			// Naming the Hollow Chapel as somewhere ahead is fine; claiming to have got there is not.
			assertThat(UnconfirmedArrival.claimsArrival(narration.text(), "chapel"))
					.as("claims the player entered, reached or arrived at the chapel: %s", narration.text()).isFalse();
		}
	}

	private static Grounded classify(ActionIntent intent, String from) {
		List<ActionPayload> payloads = intent.steps().stream().map(ActionStep::payload).toList();
		if (payloads.stream().allMatch(p -> p instanceof ActionPayload.ObservePayload)) {
			return Grounded.LOOKS;
		}
		List<ActionPayload.MovePayload> moves = payloads.stream().filter(p -> p instanceof ActionPayload.MovePayload)
				.map(p -> (ActionPayload.MovePayload) p).toList();
		if (moves.isEmpty()) {
			return Grounded.OTHER;
		}
		// Where the moves would end up, following the hub's single connection and exit.
		String zone = from;
		boolean leftScene = false;
		for (ActionPayload.MovePayload move : moves) {
			if (move.target() instanceof ActionTarget.ZoneTarget z) {
				zone = z.zoneId();
			} else if (move.target() instanceof ActionTarget.ExitTarget x && x.exitId().equals(EXIT) && zone.equals(ROAD)) {
				leftScene = true;
			}
		}
		if (leftScene) return Grounded.TAKES_THE_ROAD;
		if (zone.equals(from)) return Grounded.STAYS;
		return zone.equals(ROAD) ? Grounded.MOVES_TO_ROAD : Grounded.MOVES_TO_HEARTH;
	}

	private static String describe(ActionStep step) {
		return switch (step.payload()) {
			case ActionPayload.MovePayload move -> "MOVE " + move.movementType() + " -> " + target(move.target());
			case ActionPayload.ObservePayload observe -> "OBSERVE " + observe.kind() + " " + target(observe.target());
			default -> step.payload().getClass().getSimpleName();
		};
	}

	private static String target(ActionTarget target) {
		return switch (target) {
			case ActionTarget.ZoneTarget z -> "zone " + z.zoneId();
			case ActionTarget.ExitTarget x -> "exit " + x.exitId();
			default -> target.getClass().getSimpleName();
		};
	}

	/** Skips unless a key and model are configured, then builds the real provider, interpreter and narrator. */
	private static Session session() {
		return session(trace -> {
		});
	}

	private static Session session(java.util.function.Consumer<ActionInterpreter.InterpretationTrace> trace) {
		String key = System.getenv("OPENAI_API_KEY");
		String model = System.getenv("GAME_AI_MODEL");
		assumeTrue(key != null && !key.isBlank() && model != null && !model.isBlank(), "OPENAI_API_KEY and GAME_AI_MODEL are required");

		OpenAiProvider provider = OpenAiProvider.create(key, Optional.empty(), 1, Duration.ofSeconds(60));
		PromptLibrary prompts = new PromptLibrary();
		ActionInterpreter interpreter = new ActionInterpreter(provider, prompts.instructions(AiRole.ACTION_INTERPRETER),
				prompts.version(AiRole.ACTION_INTERPRETER),
				new AiGenerationSettings(model, 4000, Duration.ofSeconds(60), Optional.empty()), trace);
		OutcomeNarrator narrator = new OutcomeNarrator(provider, prompts.instructions(AiRole.OUTCOME_NARRATOR),
				prompts.version(AiRole.OUTCOME_NARRATOR), new AiGenerationSettings(model, 1000, Duration.ofSeconds(60), Optional.empty()));
		return new Session(provider, interpreter, narrator);
	}

	private record Session(OpenAiProvider provider, ActionInterpreter interpreter, OutcomeNarrator narrator) implements AutoCloseable {
		@Override
		public void close() {
			provider.close();
		}
	}
}
