package com.leeburke.springgame.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.leeburke.springgame.action.ActionApproach;
import com.leeburke.springgame.action.ActionIntent;
import com.leeburke.springgame.action.ActionPayload;
import com.leeburke.springgame.action.ActionPayload.MovePayload;
import com.leeburke.springgame.action.ActionStep;
import com.leeburke.springgame.action.ActionTarget;
import com.leeburke.springgame.action.InterpretationConfidence;
import com.leeburke.springgame.action.MovementType;
import com.leeburke.springgame.action.RelativeGoal;
import com.leeburke.springgame.action.StepRelation;
import com.leeburke.springgame.action.TargetSpecificity;
import com.leeburke.springgame.action.UnresolvedReference;
import com.leeburke.springgame.game.DestinationCheck.Place;
import com.leeburke.springgame.world.view.PlayerSceneView;

/**
 * Grounds an interpreted journey in the visible world graph, keeping the player in charge of where
 * they go. The interpreter says what the player means; Java checks it against the player's own words
 * and decides the route.
 * <p>
 * <b>Crossing into another scene is the player's decision.</b> An exit is taken only when three
 * sources agree: the interpreted move heads for that exit (or contradicts every place the words
 * name, or has no target); the exit is uniquely identifiable (named by its label, or the only way out
 * within reach, for "go inside"); and the player's words express crossing ("enter", "into",
 * "through"...) rather than approaching or looking ("toward", "look for"...), or, for a place
 * already discovered, deliberately going back ("back", "return"). The model's own certainty is never
 * enough.
 * <p>
 * Otherwise the journey stays in the scene: a named place is approached (an exit's zone, never the
 * exit), and an unnamed "walk forward" takes at most the one local step toward the single way on.
 * When there is no such step (the way on leaves from here, or there are several), the player is
 * asked, unless the intent does something else too (looking around), which then goes ahead.
 * <p>
 * It is not a pathfinder: at most one step is added, a journey is at most two moves, nothing hidden
 * is used (only {@link PlayerSceneView} and the player's exit labels), and other steps keep their
 * order. The result must be validated again. Slash commands never come here.
 */
public final class RouteGrounding {

	/** Words that say the player goes through or into somewhere. A handful, not a parser. */
	static final Set<String> THRESHOLD = Set.of("enter", "enters", "entering", "into", "inside", "through", "cross", "crosses",
			"crossing", "travel", "travels", "travelling", "traveling");
	/**
	 * Words that say the player approaches, or seeks, somewhere: never a crossing. (Looking around is
	 * its own OBSERVE step, so "enter the chapel and look around" still enters.)
	 */
	static final Set<String> APPROACH = Set.of("toward", "towards", "approach", "approaches", "approaching", "near", "nearer",
			"edge", "search", "searching", "seek", "seeking", "find", "finding");
	/** Words that say the player goes back. */
	static final Set<String> RETURN = Set.of("back", "return", "returns", "returning", "retreat", "retreats", "retreating");

	private RouteGrounding() {
	}

	/** The outcome of grounding. */
	public sealed interface Result {
	}

	/** Nothing to ground: the intent stands as interpreted. */
	public record Unchanged() implements Result {
	}

	/** The journey was rewritten (and any unclear phrases about its destination resolved). */
	public record Grounded(ActionIntent intent) implements Result {
		public Grounded {
			Objects.requireNonNull(intent, "intent");
		}
	}

	/** The player's words fit several places or ways: ask, with these choices. */
	public record Ambiguous(List<Place> choices) implements Result {
		public Ambiguous {
			choices = List.copyOf(choices);
		}
	}

	/**
	 * The player is already where the way on leaves from, and did not ask to go through: ask, naming
	 * the ways, rather than cross for them.
	 */
	public record Threshold(List<Place> ways) implements Result {
		public Threshold {
			ways = List.copyOf(ways);
		}
	}

	/**
	 * @param backExits known exits that lead back to a scene the player has already discovered (not the
	 *                  hub's road, which is always the way on)
	 */
	public static Result ground(String playerInput, ActionIntent intent, PlayerSceneView view, Map<String, String> exitLabels,
			Set<String> backExits) {
		return ground(playerInput, intent, view, exitLabels, backExits, Set.of());
	}

	/** @param fallen visible creatures at 0 HP: they do not stop a walk toward an object */
	public static Result ground(String playerInput, ActionIntent intent, PlayerSceneView view, Map<String, String> exitLabels,
			Set<String> backExits, Set<String> fallen) {
		List<ActionStep> steps = intent.steps();
		Optional<int[]> journey = journey(steps);
		if (journey.isEmpty()) {
			return new Unchanged();
		}
		int from = journey.get()[0];
		int to = journey.get()[1];
		MovePayload last = (MovePayload) steps.get(to).payload();
		boolean forward = steps.subList(from, to + 1).stream()
				.anyMatch(s -> ((MovePayload) s.payload()).movementType() == MovementType.ADVANCE);
		ActionApproach approach = ((MovePayload) steps.get(from).payload()).approach();

		List<MovePayload> route;
		if (last.target() instanceof ActionTarget.ObjectTarget object) {
			// Toward an object: never through a way out, at most two moves (one for "toward"), stopping at danger.
			Optional<List<String>> walk = towardObject(new Words(playerInput), object.objectId(), view, fallen);
			if (walk.isEmpty()) {
				return new Unchanged(); // already beside it, or no known way: the engine says so truthfully
			}
			route = walk.get().stream()
					.map(zone -> new MovePayload(MovementType.REPOSITION, new ActionTarget.ZoneTarget(zone, TargetSpecificity.INFERRED),
							RelativeGoal.NONE, approach))
					.toList();
			return new Grounded(rebuild(intent, from, to, route, keptUnresolved(intent, steps, from, to)));
		}

		Decision decision = decide(new Words(playerInput), last.target(), forward, view, exitLabels, backExits);
		switch (decision) {
			case Decision.Go go -> route = route(go.target(), view, approach);
			case Decision.Ask ask -> {
				if (steps.size() == to - from + 1) {
					return ask.threshold() ? new Threshold(ask.choices()) : new Ambiguous(ask.choices());
				}
				// Something else is asked for too (looking around): stay here, and let that go ahead.
				route = List.of(stay(view, approach));
			}
		}

		List<UnresolvedReference> kept = keptUnresolved(intent, steps, from, to);
		List<MovePayload> current = steps.subList(from, to + 1).stream().map(s -> (MovePayload) s.payload()).toList();
		if (sameRoute(current, route) && kept.size() == intent.unresolvedReferences().size()) {
			return new Unchanged();
		}
		return new Grounded(rebuild(intent, from, to, route, kept));
	}

	/** Unclear phrases about something other than the journey (those about it are settled by grounding it). */
	private static List<UnresolvedReference> keptUnresolved(ActionIntent intent, List<ActionStep> steps, int from, int to) {
		boolean onlyMovement = from == 0 && to == steps.size() - 1;
		Set<String> journeyIds = new HashSet<>();
		steps.subList(from, to + 1).forEach(s -> journeyIds.add(s.id()));
		return intent.unresolvedReferences().stream()
				.filter(u -> u.stepId().map(id -> !journeyIds.contains(id)).orElse(!onlyMovement))
				.toList();
	}

	/** Words that make a walk toward something incremental: one step closer, no more. */
	static final Set<String> CLOSER = Set.of("toward", "towards", "closer", "nearer", "approach", "approaches", "approaching");

	/**
	 * The zones to walk through toward an object, along the shortest known passages: one move when the
	 * player only goes closer ("toward the crate"), otherwise at most two ("go to the crate"). The walk
	 * stops in the first zone that holds a living creature or a hazard, short of the object: the player
	 * decides what to do there. Empty when already beside it or when no known passage leads there.
	 */
	private static Optional<List<String>> towardObject(Words words, String objectId, PlayerSceneView view, Set<String> fallen) {
		Optional<String> objectZone = view.objects().stream().filter(o -> o.id().equals(objectId))
				.map(PlayerSceneView.VisibleObject::zoneId).findFirst();
		if (objectZone.isEmpty() || objectZone.get().equals(view.currentZoneId())) {
			return Optional.empty();
		}
		List<String> path = shortestPath(view, view.currentZoneId(), objectZone.get());
		if (path.isEmpty()) {
			return Optional.empty();
		}
		int limit = words.tokens.stream().anyMatch(CLOSER::contains) ? 1 : 2;
		List<String> walk = new ArrayList<>();
		for (String zone : path) {
			if (walk.size() == limit) {
				break;
			}
			walk.add(zone);
			boolean danger = view.hazards().stream().anyMatch(h -> h.zoneId().equals(zone))
					|| view.entities().stream().anyMatch(e -> e.zoneId().equals(zone) && !fallen.contains(e.id()));
			if (danger && !zone.equals(objectZone.get())) {
				break;
			}
		}
		return Optional.of(walk);
	}

	/** The zones after {@code from} on a shortest path to {@code to} over the view's passages; empty when none. */
	private static List<String> shortestPath(PlayerSceneView view, String from, String to) {
		Map<String, String> previous = new HashMap<>();
		java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>(List.of(from));
		Set<String> seen = new HashSet<>(Set.of(from));
		while (!queue.isEmpty()) {
			String at = queue.poll();
			if (at.equals(to)) {
				break;
			}
			view.connections().stream()
					.map(c -> c.zoneA().equals(at) ? c.zoneB() : c.zoneB().equals(at) ? c.zoneA() : null)
					.filter(Objects::nonNull).sorted()
					.filter(seen::add)
					.forEach(next -> {
						previous.put(next, at);
						queue.add(next);
					});
		}
		if (!previous.containsKey(to)) {
			return List.of();
		}
		List<String> path = new ArrayList<>();
		for (String at = to; !at.equals(from); at = previous.get(at)) {
			path.addFirst(at);
		}
		return path;
	}

	/**
	 * For an answer with no steps at all (the model could not tell what was meant): one journey, only
	 * if the player's words name a place or ask to go through the one way within reach.
	 */
	public static Result groundNamedPlace(String playerInput, PlayerSceneView view, Map<String, String> exitLabels,
			Set<String> backExits) {
		Words words = new Words(playerInput);
		if (named(words, view, exitLabels).isEmpty() && !words.crossing() && !words.returning()) {
			return new Unchanged();
		}
		Decision decision = decide(words, ActionTarget.unspecified(), false, view, exitLabels, backExits);
		if (decision instanceof Decision.Ask ask) {
			return ask.threshold() ? new Threshold(ask.choices()) : new Ambiguous(ask.choices());
		}
		List<MovePayload> route = route(((Decision.Go) decision).target(), view, ActionApproach.NORMAL);
		List<ActionStep> steps = new ArrayList<>();
		for (int i = 0; i < route.size(); i++) {
			steps.add(new ActionStep("s" + (i + 1), i + 1, i == 0 ? StepRelation.START : StepRelation.IF_PREVIOUS_SUCCEEDS,
					route.get(i)));
		}
		return new Grounded(new ActionIntent(ActionIntent.CURRENT_SCHEMA_VERSION, Optional.empty(), steps,
				InterpretationConfidence.MEDIUM, List.of()));
	}

	// --- The journey ---

	/**
	 * The indices of the intent's one journey: a single zone/exit/untargeted move, or a zone move then
	 * an exit move. Absent when there is no such move, or more than one journey.
	 */
	private static Optional<int[]> journey(List<ActionStep> steps) {
		List<Integer> moves = new ArrayList<>();
		for (int i = 0; i < steps.size(); i++) {
			if (steps.get(i).payload() instanceof MovePayload) {
				if (!journeyLeg(steps.get(i))) {
					return Optional.empty(); // another kind of movement: leave the whole intent alone
				}
				moves.add(i);
			}
		}
		if (moves.size() == 1) {
			return Optional.of(new int[] { moves.getFirst(), moves.getFirst() });
		}
		if (moves.size() == 2 && moves.get(1) == moves.get(0) + 1
				&& ((MovePayload) steps.get(moves.get(0)).payload()).target() instanceof ActionTarget.ZoneTarget
				&& ((MovePayload) steps.get(moves.get(1)).payload()).target() instanceof ActionTarget.ExitTarget) {
			return Optional.of(new int[] { moves.get(0), moves.get(1) });
		}
		return Optional.empty();
	}

	/** Going somewhere: on, to a place, or back the way the player came (RETREAT to a place). */
	private static boolean journeyLeg(ActionStep step) {
		MovePayload move = (MovePayload) step.payload();
		if (move.target() instanceof ActionTarget.ObjectTarget) {
			return move.goal() == RelativeGoal.NONE && (move.movementType() == MovementType.REPOSITION
					|| move.movementType() == MovementType.ADVANCE || move.movementType() == MovementType.CLOSE_DISTANCE);
		}
		boolean kind = move.movementType() == MovementType.REPOSITION || move.movementType() == MovementType.ADVANCE
				|| move.movementType() == MovementType.RETREAT && !(move.target() instanceof ActionTarget.Unspecified);
		return kind && move.goal() == RelativeGoal.NONE
				&& (move.target() instanceof ActionTarget.ZoneTarget || move.target() instanceof ActionTarget.ExitTarget
						|| move.target() instanceof ActionTarget.Unspecified);
	}

	// --- Where to ---

	private sealed interface Decision {
		record Go(ActionTarget target) implements Decision {
		}

		/** @param threshold the player is at the way on (rather than facing several ways) */
		record Ask(List<Place> choices, boolean threshold) implements Decision {
		}
	}

	/**
	 * @param interpreted the target of the journey's last move, as interpreted
	 * @param forward     the journey advances (rather than repositions or goes back)
	 */
	private static Decision decide(Words words, ActionTarget interpreted, boolean forward, PlayerSceneView view,
			Map<String, String> exitLabels, Set<String> backExits) {
		List<Place> exits = exits(view, exitLabels);
		List<Place> named = named(words, view, exitLabels);

		// 1. Crossing: the words go through, the way is uniquely identifiable, and the interpretation agrees.
		List<Place> byLabel = exits.stream().filter(x -> words.mention(x.name())).toList();
		List<Place> authorized = (byLabel.isEmpty() ? withinReach(exits, view) : byLabel).stream()
				.filter(x -> backExits.contains(x.id()) ? words.returning() || words.crossing() : words.crossing())
				.toList();
		if (authorized.size() > 1) {
			return new Decision.Ask(authorized, false); // "through the doorway", with more than one: which?
		}
		if (authorized.size() == 1 && agrees(interpreted, authorized.getFirst(), named, words, view)) {
			return new Decision.Go(target(authorized.getFirst()));
		}

		// 2. Otherwise stay in the scene. A named place is approached: a zone, or the zone an exit leaves from.
		if (!named.isEmpty()) {
			Set<String> zones = new LinkedHashSet<>();
			for (Place place : named) {
				zones.add(place.exit() ? zoneOf(place, view) : place.id());
			}
			zones.remove(view.currentZoneId());
			if (interpreted instanceof ActionTarget.ZoneTarget z && zones.contains(z.zoneId())) {
				return new Decision.Go(interpreted);
			}
			if (zones.size() == 1) {
				return new Decision.Go(new ActionTarget.ZoneTarget(zones.iterator().next(), TargetSpecificity.INFERRED));
			}
			if (zones.isEmpty()) {
				return new Decision.Ask(named.stream().filter(Place::exit).toList(), true); // all of it leaves from here
			}
			return new Decision.Ask(named, false);
		}

		// 3. Nothing named. Repositioning (or going back) to a zone is taken as interpreted.
		if (!forward && interpreted instanceof ActionTarget.ZoneTarget) {
			return new Decision.Go(interpreted);
		}
		// Walking on: at most the one local step toward the way on.
		List<Place> ahead = exits.stream().filter(x -> !backExits.contains(x.id())).toList();
		Set<String> steps = new LinkedHashSet<>();
		for (Place way : ahead) {
			String zone = zoneOf(way, view);
			if (!zone.equals(view.currentZoneId()) && connected(view, view.currentZoneId(), zone)) {
				steps.add(zone);
			}
		}
		if (interpreted instanceof ActionTarget.ZoneTarget z
				&& (steps.contains(z.zoneId()) || ahead.isEmpty() && !z.zoneId().equals(view.currentZoneId()))) {
			return new Decision.Go(interpreted);
		}
		boolean someHere = ahead.stream().anyMatch(x -> zoneOf(x, view).equals(view.currentZoneId()));
		if (steps.size() == 1 && !someHere) {
			return new Decision.Go(new ActionTarget.ZoneTarget(steps.iterator().next(), TargetSpecificity.INFERRED));
		}
		if (someHere && steps.isEmpty() && ahead.stream().allMatch(x -> zoneOf(x, view).equals(view.currentZoneId()))) {
			return new Decision.Ask(ahead, true); // the way on begins here
		}
		return new Decision.Ask(ahead.isEmpty() ? exits : ahead, false);
	}

	/**
	 * Does the interpreted move agree with going through this exit? It does if it heads for the exit,
	 * has no target, heads for the exit's zone without the words naming any zone in full ("enter the
	 * chapel" from the hearth, read as a move to Chapel Road), or contradicts every place the words name.
	 */
	private static boolean agrees(ActionTarget interpreted, Place exit, List<Place> named, Words words, PlayerSceneView view) {
		return switch (interpreted) {
			case ActionTarget.ExitTarget x -> x.exitId().equals(exit.id()) || !named.isEmpty() && named.stream().noneMatch(p -> p.id().equals(x.exitId()));
			case ActionTarget.Unspecified u -> true;
			case ActionTarget.ZoneTarget z -> z.zoneId().equals(zoneOf(exit, view)) && !anyZoneFullyNamed(words, view)
					|| !named.isEmpty() && named.stream().noneMatch(p -> p.id().equals(z.zoneId()));
			default -> false;
		};
	}

	private static boolean anyZoneFullyNamed(Words words, PlayerSceneView view) {
		return view.zones().stream().anyMatch(z -> words.names(z.displayName()));
	}

	/**
	 * Visible places the player's words name as somewhere to go. If any place is named in full ("the
	 * chapel road"), only fully named places count; otherwise every partly named one does ("the
	 * chapel" partly names both Chapel Road and the road to the Hollow Chapel). The zone the player is
	 * already in is then dropped: naming it in full ("I wait on the chapel road") names no destination.
	 */
	private static List<Place> named(Words words, PlayerSceneView view, Map<String, String> exitLabels) {
		List<Place> named = DestinationCheck.places(view, exitLabels).stream().filter(p -> words.mention(p.name())).toList();
		List<Place> full = named.stream().filter(p -> words.names(p.name())).toList();
		return (full.isEmpty() ? named : full).stream()
				.filter(p -> p.exit() || !p.id().equals(view.currentZoneId()))
				.toList();
	}

	/** Exits the player could mean without naming them: those here, or else those one step away. */
	private static List<Place> withinReach(List<Place> exits, PlayerSceneView view) {
		List<Place> here = exits.stream().filter(x -> zoneOf(x, view).equals(view.currentZoneId())).toList();
		return here.isEmpty()
				? exits.stream().filter(x -> connected(view, view.currentZoneId(), zoneOf(x, view))).toList()
				: here;
	}

	private static List<Place> exits(PlayerSceneView view, Map<String, String> exitLabels) {
		return DestinationCheck.places(view, exitLabels).stream().filter(Place::exit).toList();
	}

	/** The player's words: the significant ones (for names) and the whole ones (for the few markers). */
	private static final class Words {
		private final Set<String> significant;
		private final List<String> tokens;

		Words(String text) {
			this.significant = DestinationCheck.words(text);
			this.tokens = DestinationCheck.tokens(text);
		}

		/** Some significant word of the name is said. */
		boolean mention(String name) {
			return DestinationCheck.words(name).stream().anyMatch(significant::contains);
		}

		/** Every significant word of the name is said. */
		boolean names(String name) {
			Set<String> words = DestinationCheck.words(name);
			return !words.isEmpty() && significant.containsAll(words);
		}

		/** Going through or into, and not merely toward or looking for. */
		boolean crossing() {
			boolean through = tokens.stream().anyMatch(THRESHOLD::contains) || allTheWay();
			return through && tokens.stream().noneMatch(APPROACH::contains);
		}

		/** Going back, on purpose. */
		boolean returning() {
			return tokens.stream().anyMatch(RETURN::contains);
		}

		private boolean allTheWay() {
			for (int i = 0; i + 2 < tokens.size(); i++) {
				if (tokens.get(i).equals("all") && tokens.get(i + 1).equals("the") && tokens.get(i + 2).equals("way")) {
					return true;
				}
			}
			return false;
		}
	}

	// --- How to get there ---

	/** At most two moves: to a zone; through an exit here; or to a connected zone, then through its exit. */
	private static List<MovePayload> route(ActionTarget goal, PlayerSceneView view, ActionApproach approach) {
		if (goal instanceof ActionTarget.ExitTarget exit) {
			String exitZone = view.exits().stream().filter(x -> x.id().equals(exit.exitId())).findFirst()
					.map(PlayerSceneView.KnownExit::zoneId).orElse(view.currentZoneId());
			MovePayload through = new MovePayload(MovementType.ADVANCE, exit, RelativeGoal.NONE, approach);
			if (!exitZone.equals(view.currentZoneId()) && connected(view, view.currentZoneId(), exitZone)) {
				return List.of(new MovePayload(MovementType.REPOSITION, new ActionTarget.ZoneTarget(exitZone, TargetSpecificity.INFERRED),
						RelativeGoal.NONE, approach), through);
			}
			return List.of(through); // here, or not reachable in one step: the engine decides, nothing is invented
		}
		return List.of(new MovePayload(MovementType.REPOSITION, goal, RelativeGoal.NONE, approach));
	}

	/** Staying where the player is: an idle step, which never counts as a turn on its own. */
	private static MovePayload stay(PlayerSceneView view, ActionApproach approach) {
		return new MovePayload(MovementType.REPOSITION, new ActionTarget.ZoneTarget(view.currentZoneId(), TargetSpecificity.INFERRED),
				RelativeGoal.NONE, approach);
	}

	private static boolean connected(PlayerSceneView view, String a, String b) {
		return view.connections().stream()
				.anyMatch(c -> (c.zoneA().equals(a) && c.zoneB().equals(b)) || (c.zoneA().equals(b) && c.zoneB().equals(a)));
	}

	private static ActionTarget target(Place place) {
		return place.exit() ? new ActionTarget.ExitTarget(place.id(), TargetSpecificity.INFERRED)
				: new ActionTarget.ZoneTarget(place.id(), TargetSpecificity.INFERRED);
	}

	private static String zoneOf(Place exit, PlayerSceneView view) {
		return view.exits().stream().filter(x -> x.id().equals(exit.id())).findFirst().map(PlayerSceneView.KnownExit::zoneId)
				.orElse("");
	}

	/** The same moves, of the same kinds, to the same places. */
	private static boolean sameRoute(List<MovePayload> current, List<MovePayload> route) {
		if (current.size() != route.size()) {
			return false;
		}
		for (int i = 0; i < current.size(); i++) {
			if (!place(current.get(i).target()).equals(place(route.get(i).target()))
					|| current.get(i).movementType() == MovementType.RETREAT) {
				return false;
			}
		}
		return true;
	}

	private static String place(ActionTarget target) {
		return switch (target) {
			case ActionTarget.ZoneTarget z -> "zone:" + z.zoneId();
			case ActionTarget.ExitTarget x -> "exit:" + x.exitId();
			default -> "none";
		};
	}

	/** The intent with steps from..to replaced by the route; steps renumbered, order and relations kept. */
	private static ActionIntent rebuild(ActionIntent intent, int from, int to, List<MovePayload> route,
			List<UnresolvedReference> kept) {
		List<ActionStep> old = intent.steps();
		List<ActionPayload> payloads = new ArrayList<>();
		List<StepRelation> relations = new ArrayList<>();
		Map<String, String> renamed = new HashMap<>();
		for (int i = 0; i < from; i++) {
			payloads.add(old.get(i).payload());
			relations.add(old.get(i).relation());
			renamed.put(old.get(i).id(), "s" + payloads.size());
		}
		for (int i = 0; i < route.size(); i++) {
			payloads.add(route.get(i));
			relations.add(i == 0 ? old.get(from).relation() : StepRelation.IF_PREVIOUS_SUCCEEDS);
		}
		for (int i = to + 1; i < old.size(); i++) {
			payloads.add(old.get(i).payload());
			relations.add(old.get(i).relation());
			renamed.put(old.get(i).id(), "s" + payloads.size());
		}
		List<ActionStep> steps = new ArrayList<>();
		for (int i = 0; i < payloads.size(); i++) {
			steps.add(new ActionStep("s" + (i + 1), i + 1, i == 0 ? StepRelation.START : relations.get(i), payloads.get(i)));
		}
		List<UnresolvedReference> unresolved = kept.stream()
				.map(u -> new UnresolvedReference(u.stepId().map(renamed::get), u.phrase())).toList();
		return new ActionIntent(intent.schemaVersion(), intent.responseToAttack(), steps, intent.confidence(), unresolved);
	}
}
