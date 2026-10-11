package com.leeburke.springgame.world.view;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * What is left to explore of the current scene, as far as the player knows: derived from the
 * player's known view only, so nothing hidden, unseen or undiscovered can appear in it.
 * <ul>
 * <li>{@code unexplored}: known ways out whose destination the player has not discovered;</li>
 * <li>{@code unvisited}: known places the player has not stood in (only when visits are recorded);</li>
 * <li>{@code visited}: places the player has stood in, other than here;</li>
 * <li>{@code known}: the other known ways out (to places already discovered).</li>
 * </ul>
 * Everything listed is reachable over passages the player knows, with its distance and the first
 * place to go through. {@link #nothingKnownLeft()} means no lead is <em>known</em>: it never claims
 * that no other way exists.
 *
 * @param view the known view these leads were taken from (for naming them)
 */
public record ExplorationLeads(PlayerSceneView view, List<Way> unexplored, List<Place> unvisited, List<Place> visited, List<Way> known,
		boolean visitsRecorded) {

	/**
	 * A known way out.
	 *
	 * @param steps passages from here to the zone it leaves from (0: here)
	 * @param via   the first place to go through, when it is more than one passage away
	 */
	public record Way(String exitId, String zoneId, int steps, Optional<String> via) {
		public Way {
			Objects.requireNonNull(exitId, "exitId");
			Objects.requireNonNull(zoneId, "zoneId");
			Objects.requireNonNull(via, "via");
		}
	}

	/** A known place, with how far it is and the first place to go through when it is not beside here. */
	public record Place(String zoneId, int steps, Optional<String> via) {
		public Place {
			Objects.requireNonNull(zoneId, "zoneId");
			Objects.requireNonNull(via, "via");
		}
	}

	public ExplorationLeads {
		Objects.requireNonNull(view, "view");
		unexplored = List.copyOf(unexplored);
		unvisited = List.copyOf(unvisited);
		visited = List.copyOf(visited);
		known = List.copyOf(known);
	}

	/** No unexplored way and no unvisited place is known (other ways may exist that the player has not found). */
	public boolean nothingKnownLeft() {
		return unexplored.isEmpty() && unvisited.isEmpty();
	}

	/**
	 * @param view          what the player knows of the scene, standing where they are
	 * @param visitedZones  where they have stood, if recorded for this scene
	 * @param undiscovered  known exits whose destination they have not discovered
	 */
	public static ExplorationLeads of(PlayerSceneView view, Optional<List<String>> visitedZones, Set<String> undiscovered) {
		String here = view.currentZoneId();
		Map<String, Integer> steps = view.stepsFrom(here);
		Map<String, String> firstStep = firstSteps(view, here);
		Comparator<Way> wayOrder = Comparator.comparingInt(Way::steps).thenComparing(Way::exitId);
		List<Way> ways = view.exits().stream().filter(x -> steps.containsKey(x.zoneId()))
				.map(x -> new Way(x.id(), x.zoneId(), steps.get(x.zoneId()), via(firstStep, steps, x.zoneId())))
				.sorted(wayOrder).toList();
		Comparator<Place> placeOrder = Comparator.comparingInt(Place::steps).thenComparing(Place::zoneId);
		List<Place> places = view.zones().stream().map(PlayerSceneView.VisibleZone::id)
				.filter(z -> !z.equals(here) && steps.containsKey(z))
				.map(z -> new Place(z, steps.get(z), via(firstStep, steps, z))).sorted(placeOrder).toList();
		List<String> stoodIn = visitedZones.orElse(List.of());
		return new ExplorationLeads(view,
				ways.stream().filter(w -> undiscovered.contains(w.exitId())).toList(),
				visitedZones.isPresent() ? places.stream().filter(p -> !stoodIn.contains(p.zoneId())).toList() : List.of(),
				places.stream().filter(p -> stoodIn.contains(p.zoneId())).toList(),
				ways.stream().filter(w -> !undiscovered.contains(w.exitId())).toList(),
				visitedZones.isPresent());
	}

	private static Optional<String> via(Map<String, String> firstStep, Map<String, Integer> steps, String zone) {
		return steps.get(zone) >= 2 ? Optional.ofNullable(firstStep.get(zone)) : Optional.empty();
	}

	/** For each reachable zone, the zone beside here that a shortest known path to it goes through first. */
	private static Map<String, String> firstSteps(PlayerSceneView view, String here) {
		Map<String, String> first = new HashMap<>();
		ArrayDeque<String> queue = new ArrayDeque<>();
		queue.add(here);
		first.put(here, here);
		while (!queue.isEmpty()) {
			String at = queue.poll();
			view.connections().stream()
					.map(c -> c.zoneA().equals(at) ? c.zoneB() : c.zoneB().equals(at) ? c.zoneA() : null)
					.filter(Objects::nonNull).sorted()
					.forEach(next -> {
						if (!first.containsKey(next)) {
							first.put(next, at.equals(here) ? next : first.get(at));
							queue.add(next);
						}
					});
		}
		first.remove(here);
		return first;
	}
}
