package com.leeburke.springgame.world.generation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

import com.leeburke.springgame.content.world.ContainerRules;
import com.leeburke.springgame.world.ContainerState;
import com.leeburke.springgame.world.SceneExit;
import com.leeburke.springgame.world.SceneObject;
import com.leeburke.springgame.world.SceneState;
import com.leeburke.springgame.world.ZoneConnection;

/**
 * Furnishes a generated scene after its structure and contents are drawn: what each container holds,
 * and (in the region's first scene) one guaranteed find. Every draw comes from its own derived
 * stream, so the structure a seed generates is exactly what it generated before containers existed.
 * <ul>
 * <li>Container contents: {@code derive(sceneSeed, CONTENTS, hash(objectId))}; empty with the
 * container's {@code chanceEmpty}, otherwise one uniform candidate. Hidden containers are filled too:
 * hidden things are only unseen, not absent.</li>
 * <li>The first find ({@code derive(sceneSeed, FIRST_FIND, 0)}): one container, never hidden and never
 * empty, in a zone joined to the one the player arrives in from the hub, so it is in sight on arrival
 * and one step away.</li>
 * </ul>
 * The scene starts with nothing seen; the player comes to know it zone by zone.
 */
final class SceneFurnisher {

	static final long CONTENTS_DOMAIN = 4;
	static final long FIRST_FIND_DOMAIN = 5;
	static final String FIRST_FIND_ID = "first_find";

	private SceneFurnisher() {
	}

	/**
	 * @param arrivalExit in the region's first scene, the exit back to the hub (where the player arrives);
	 *                    empty for every other scene
	 */
	static SceneState furnish(SceneState state, long sceneSeed, ContainerRules rules, Optional<String> arrivalExit) {
		SceneState furnished = state;
		if (arrivalExit.isPresent() && rules.firstFind().isPresent()) {
			furnished = withFirstFind(furnished, sceneSeed, rules.firstFind().get(), arrivalExit.get());
		}
		List<ContainerState> containers = new ArrayList<>();
		for (SceneObject object : furnished.objects()) {
			if (object.id().equals(FIRST_FIND_ID)) {
				continue; // filled with its object, below
			}
			rules.container(object.definitionCode()).ifPresent(container -> {
				RandomGenerator rng = WorldRandom.create(WorldRandom.derive(sceneSeed, CONTENTS_DOMAIN, WorldRandom.stableHash(object.id())));
				boolean empty = rng.nextInt(100) < container.chanceEmpty();
				List<String> contents = empty ? List.of() : List.of(container.candidates().get(rng.nextInt(container.candidates().size())));
				containers.add(new ContainerState(object.id(), false, contents));
			});
		}
		if (arrivalExit.isPresent() && rules.firstFind().isPresent()) {
			RandomGenerator rng = WorldRandom.create(WorldRandom.derive(sceneSeed, FIRST_FIND_DOMAIN, 1));
			List<String> candidates = rules.firstFind().get().candidates();
			containers.add(new ContainerState(FIRST_FIND_ID, false, List.of(candidates.get(rng.nextInt(candidates.size())))));
		}
		return furnished.generated(containers);
	}

	private static SceneState withFirstFind(SceneState state, long sceneSeed, ContainerRules.FirstFind find, String arrivalExit) {
		String arrival = state.exits().stream().filter(x -> x.id().equals(arrivalExit)).map(SceneExit::zoneId).findFirst()
				.orElseThrow(() -> new IllegalStateException("No arrival exit " + arrivalExit));
		List<String> beside = new ArrayList<>();
		for (ZoneConnection connection : state.connections()) {
			if (connection.zoneA().equals(arrival)) {
				beside.add(connection.zoneB());
			} else if (connection.zoneB().equals(arrival)) {
				beside.add(connection.zoneA());
			}
		}
		beside.sort(String::compareTo);
		RandomGenerator rng = WorldRandom.create(WorldRandom.derive(sceneSeed, FIRST_FIND_DOMAIN, 0));
		String zone = beside.isEmpty() ? arrival : beside.get(rng.nextInt(beside.size()));
		List<SceneObject> objects = new ArrayList<>(state.objects());
		objects.add(new SceneObject(FIRST_FIND_ID, find.object(), zone));
		return new SceneState(state.zones(), state.connections(), state.entities(), objects, state.hazards(), state.exits(),
				state.activeEvents(), state.environmentFlags(), state.hiddenContent(), state.discoveredFacts(), state.containers(),
				state.seenZones());
	}
}
