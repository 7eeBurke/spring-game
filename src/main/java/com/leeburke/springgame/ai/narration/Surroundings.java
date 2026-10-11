package com.leeburke.springgame.ai.narration;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.leeburke.springgame.world.view.PlayerSceneView;

/**
 * What the player can see from one zone of the current scene, in player-visible names only: where
 * they are, the zones they can walk to, the creatures, objects and hazards in sight, and the ways
 * out (each named only as far as the player knows where it leads). Built by Java from the
 * player-safe scene view, so it never contains hidden content; observation narration and the
 * grounded hints of refused actions both use it.
 *
 * @param reachableZones zones joined to this one by a known connection
 * @param ways           known exits: where they lead (as known) and the zone they leave from
 */
public record Surroundings(String zone, List<String> reachableZones, List<Seen> creatures, List<Seen> objects,
		List<Seen> hazards, List<Way> ways) {

	public static final String UNEXPLORED = "an unexplored way";

	private static final String[] COUNTS = { "No", "One", "Two", "Three", "Four", "Five", "Several" };

	public Surroundings {
		Objects.requireNonNull(zone, "zone");
		reachableZones = List.copyOf(reachableZones);
		creatures = List.copyOf(creatures);
		objects = List.copyOf(objects);
		hazards = List.copyOf(hazards);
		ways = List.copyOf(ways);
	}

	/** Something in sight: its visible name, the zone it is in, whether that is the player's zone. */
	public record Seen(String name, String zone, boolean here, boolean fallen) {
		public Seen {
			Objects.requireNonNull(name, "name");
			Objects.requireNonNull(zone, "zone");
		}
	}

	/** A known way out of the scene. */
	public record Way(String leadsTo, String zone, boolean here) {
		public Way {
			Objects.requireNonNull(leadsTo, "leadsTo");
			Objects.requireNonNull(zone, "zone");
		}
	}

	/**
	 * @param fallenEntityIds visible creatures at 0 HP
	 * @param exitLabels      what each known exit is called ({@link #UNEXPLORED} when absent)
	 * @param zoneId          the zone the player is in at this moment
	 */
	public static Surroundings of(PlayerSceneView view, NarrationNames names, Set<String> fallenEntityIds,
			Map<String, String> exitLabels, String zoneId) {
		String zone = names.zone(zoneId).orElseThrow(() -> new IllegalArgumentException("Zone " + zoneId + " is not visible"));
		List<String> reachable = view.connections().stream()
				.filter(c -> c.zoneA().equals(zoneId) || c.zoneB().equals(zoneId))
				.map(c -> c.zoneA().equals(zoneId) ? c.zoneB() : c.zoneA())
				.distinct().sorted()
				.map(id -> names.zone(id).orElseThrow())
				.toList();
		List<Seen> creatures = view.entities().stream().sorted(Comparator.comparing(PlayerSceneView.VisibleEntity::id))
				.map(e -> new Seen(names.entity(e.id()).orElseThrow(), names.zone(e.zoneId()).orElseThrow(), e.zoneId().equals(zoneId),
						fallenEntityIds.contains(e.id())))
				.toList();
		List<Seen> objects = view.objects().stream().sorted(Comparator.comparing(PlayerSceneView.VisibleObject::id))
				.map(o -> new Seen(names.object(o.id()).orElseThrow(), names.zone(o.zoneId()).orElseThrow(), o.zoneId().equals(zoneId), false))
				.toList();
		List<Seen> hazards = view.hazards().stream().sorted(Comparator.comparing(PlayerSceneView.VisibleHazard::id))
				.map(h -> new Seen(names.hazard(h.id()).orElseThrow(), names.zone(h.zoneId()).orElseThrow(), h.zoneId().equals(zoneId), false))
				.toList();
		List<Way> ways = view.exits().stream().sorted(Comparator.comparing(PlayerSceneView.KnownExit::id))
				.map(x -> new Way(exitLabels.getOrDefault(x.id(), UNEXPLORED), names.zone(x.zoneId()).orElseThrow(), x.zoneId().equals(zoneId)))
				.toList();
		return new Surroundings(zone, reachable, creatures, objects, hazards, ways);
	}

	/**
	 * A short, plain description for hints and fallback narration: where you are, where you can go,
	 * what threatens you, and the ways out.
	 */
	public String describe() {
		StringBuilder text = new StringBuilder("You are in the ").append(zone).append('.');
		if (!reachableZones.isEmpty()) {
			text.append(" From here you can go to ").append(join(reachableZones.stream().map(z -> "the " + z).toList())).append('.');
		}
		List<String> threats = creatures.stream().filter(c -> !c.fallen())
				.map(c -> "the " + c.name() + (c.here() ? " here" : " in the " + c.zone())).toList();
		if (!threats.isEmpty()) {
			text.append(" You see ").append(join(threats)).append('.');
		}
		List<String> things = java.util.stream.Stream.concat(objects.stream(), hazards.stream())
				.map(t -> "the " + t.name() + (t.here() ? " here" : " in the " + t.zone())).toList();
		if (!things.isEmpty()) {
			text.append(" Nearby: ").append(join(things)).append('.');
		}
		// Identical unexplored ways from one zone are told once, counted: "Two unexplored ways leave from here."
		java.util.Map<Way, Long> counted = ways.stream()
				.collect(java.util.stream.Collectors.groupingBy(w -> w, java.util.LinkedHashMap::new, java.util.stream.Collectors.counting()));
		counted.forEach((way, count) -> {
			String from = way.here() ? " from here." : " from the " + way.zone() + ".";
			if (count > 1 && way.leadsTo().equals(UNEXPLORED)) {
				text.append(' ').append(COUNTS[(int) Math.min(count, COUNTS.length - 1)]).append(" unexplored ways leave").append(from);
			} else {
				String name = Character.toUpperCase(way.leadsTo().charAt(0)) + way.leadsTo().substring(1);
				for (long i = 0; i < count; i++) {
					text.append(' ').append(name).append(" leaves").append(from);
				}
			}
		});
		return text.toString();
	}

	/** {@link #describe()} without its first sentence (where you are): for when the arrival already said it. */
	public String describeAround() {
		String all = describe();
		int end = all.indexOf(". ");
		return end < 0 ? "" : all.substring(end + 2);
	}

	private static String join(List<String> parts) {
		if (parts.size() <= 1) {
			return String.join("", parts);
		}
		return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.getLast();
	}
}
