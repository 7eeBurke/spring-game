package com.leeburke.springgame.world.generation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.leeburke.springgame.content.world.RegionDefinition;
import com.leeburke.springgame.world.HiddenContentKind;
import com.leeburke.springgame.world.SceneEntity;
import com.leeburke.springgame.world.SceneEvent;
import com.leeburke.springgame.world.SceneExit;
import com.leeburke.springgame.world.SceneInstance;
import com.leeburke.springgame.world.ScenePlacement;

/**
 * Validates a complete generated region against its definition before it may be persisted.
 * <p>
 * Checks identity and ownership, counts and route shape, the exact bidirectional exit graph,
 * reachability, visibility of progression, archetype rules, the boss and region-unique events.
 * Gameplay rules that do not exist yet (recovery pacing, threat, objectives) are not checked.
 */
public final class CompleteRegionValidator implements RegionValidator {

	private final RegionDefinition definition;

	public CompleteRegionValidator(RegionDefinition definition) {
		this.definition = Objects.requireNonNull(definition, "definition");
	}

	@Override
	public List<String> problems(GeneratedRegion region) {
		List<String> problems = new ArrayList<>();
		Map<UUID, SceneInstance> scenes = new HashMap<>();
		region.scenes().forEach(scene -> scenes.put(scene.id(), scene));
		UUID entry = region.entrySceneId();
		UUID preBoss = region.preBossSceneId();
		UUID boss = region.bossSceneId();

		if (!region.region().definitionCode().equals(definition.code())) {
			problems.add("Region code " + region.region().definitionCode() + " is not " + definition.code());
		}
		checkOwnershipAndArchetypes(region, scenes, problems);
		checkCounts(region, problems);

		Set<UUID> routeScenes = new HashSet<>();
		region.routeStages().forEach(routeScenes::addAll);
		Map<UUID, Set<UUID>> links = exitGraph(region, scenes, entry, problems);

		Set<Set<UUID>> expectedRouteLinks = new HashSet<>();
		List<List<UUID>> stages = region.routeStages();
		for (int s = 0; s + 1 < stages.size(); s++) {
			for (UUID a : stages.get(s)) {
				for (UUID b : stages.get(s + 1)) {
					expectedRouteLinks.add(Set.of(a, b));
				}
			}
		}
		expectedRouteLinks.add(Set.of(preBoss, boss));
		Set<Set<UUID>> actualRouteLinks = new HashSet<>();
		Set<UUID> optional = new HashSet<>(region.optionalSceneIds());
		links.forEach((from, tos) -> tos.forEach(to -> {
			if (!optional.contains(from) && !optional.contains(to)) {
				actualRouteLinks.add(Set.of(from, to));
			}
		}));
		if (!actualRouteLinks.equals(expectedRouteLinks)) {
			problems.add("Route exits do not match the route structure");
		}
		Set<UUID> hosts = new HashSet<>();
		for (UUID optionalScene : region.optionalSceneIds()) {
			Set<UUID> neighbours = links.getOrDefault(optionalScene, Set.of());
			if (neighbours.size() != 1) {
				problems.add("Optional scene " + optionalScene + " must be a dead end with exactly one link");
				continue;
			}
			UUID host = neighbours.iterator().next();
			if (!routeScenes.contains(host) || host.equals(preBoss)) {
				problems.add("Optional scene " + optionalScene + " must hang off a route scene other than the pre-boss scene");
			}
			if (!hosts.add(host)) {
				problems.add("Two optional scenes share host " + host);
			}
		}

		Set<UUID> reached = new HashSet<>();
		Deque<UUID> queue = new ArrayDeque<>(List.of(entry));
		while (!queue.isEmpty()) {
			UUID scene = queue.poll();
			if (reached.add(scene)) {
				queue.addAll(links.getOrDefault(scene, Set.of()));
			}
		}
		if (!reached.equals(scenes.keySet())) {
			problems.add("Not every scene is reachable from the entry; unreachable: " + difference(scenes.keySet(), reached));
		}
		if (!reached.contains(boss)) {
			problems.add("The boss scene is not reachable from the entry");
		}

		links.forEach((from, tos) -> tos.forEach(to -> {
			if (!from.equals(boss) && !to.equals(boss)
					&& scenes.get(from).definitionCode().equals(scenes.get(to).definitionCode())) {
				problems.add("Adjacent scenes " + from + " and " + to + " share archetype " + scenes.get(from).definitionCode());
			}
		}));

		checkBossEntity(region, boss, problems);
		checkUniqueEvents(region, problems);
		return problems.stream().distinct().toList();
	}

	private void checkOwnershipAndArchetypes(GeneratedRegion region, Map<UUID, SceneInstance> scenes, List<String> problems) {
		for (SceneInstance scene : scenes.values()) {
			if (!(scene.placement() instanceof ScenePlacement.Region placement)
					|| !placement.regionInstanceId().equals(region.region().id())) {
				problems.add("Scene " + scene.id() + " is not a REGION scene of region " + region.region().id());
			}
			if (!scene.runId().equals(region.region().runId())) {
				problems.add("Scene " + scene.id() + " belongs to another run");
			}
			if (scene.revision() != 0) {
				problems.add("Scene " + scene.id() + " is not at revision 0");
			}
			if (scene.state().hiddenContent().stream().anyMatch(ref -> ref.kind() == HiddenContentKind.ZONE)) {
				problems.add("Scene " + scene.id() + " has a hidden zone");
			}
			boolean isBoss = scene.id().equals(region.bossSceneId());
			if (isBoss && !scene.definitionCode().equals(definition.bossArchetype())) {
				problems.add("The boss scene must use " + definition.bossArchetype());
			}
			if (!isBoss && !definition.normalArchetypes().contains(scene.definitionCode())) {
				problems.add("Scene " + scene.id() + " uses archetype " + scene.definitionCode() + ", not a normal archetype of " + definition.code());
			}
		}
	}

	private void checkCounts(GeneratedRegion region, List<String> problems) {
		int required = 0;
		int branches = 0;
		for (int s = 0; s < region.routeStages().size(); s++) {
			int size = region.routeStages().get(s).size();
			required += size;
			boolean end = s == 0 || s == region.routeStages().size() - 1;
			if (size == 2 && !end) {
				branches++;
			} else if (size != 1) {
				problems.add("Route stage " + s + " has " + size + " scenes");
			}
		}
		if (!definition.requiredScenes().contains(required)) {
			problems.add("Required scene count " + required + " is outside " + definition.requiredScenes());
		}
		if (!definition.branches().contains(branches)) {
			problems.add("Branch count " + branches + " is outside " + definition.branches());
		}
		if (!definition.optionalScenes().contains(region.optionalSceneIds().size())) {
			problems.add("Optional scene count " + region.optionalSceneIds().size() + " is outside " + definition.optionalScenes());
		}
	}

	/** Builds the region exit graph, reporting dangling, self, hidden, duplicate and one-way exits. */
	private Map<UUID, Set<UUID>> exitGraph(GeneratedRegion region, Map<UUID, SceneInstance> scenes, UUID entry, List<String> problems) {
		Map<UUID, Set<UUID>> links = new HashMap<>();
		int entryExternalExits = 0;
		for (SceneInstance scene : scenes.values()) {
			Set<UUID> destinations = links.computeIfAbsent(scene.id(), id -> new HashSet<>());
			for (SceneExit exit : scene.state().exits()) {
				UUID destination = exit.destinationSceneId();
				if (scene.state().isHidden(HiddenContentKind.EXIT, exit.id())) {
					problems.add("Exit " + exit.id() + " of scene " + scene.id() + " is hidden");
				}
				if (destination.equals(scene.id())) {
					problems.add("Scene " + scene.id() + " has an exit to itself");
				} else if (scenes.containsKey(destination)) {
					if (!destinations.add(destination)) {
						problems.add("Scene " + scene.id() + " has more than one exit to " + destination);
					}
				} else if (scene.id().equals(entry)) {
					entryExternalExits++;
				} else {
					problems.add("Exit " + exit.id() + " of scene " + scene.id() + " leads outside the region");
				}
			}
		}
		if (entryExternalExits != 1) {
			problems.add("The entry scene must have exactly one exit leaving the region, but has " + entryExternalExits);
		}
		links.forEach((from, tos) -> tos.forEach(to -> {
			if (!links.getOrDefault(to, Set.of()).contains(from)) {
				problems.add("Exit from " + from + " to " + to + " has no return exit");
			}
		}));
		return links;
	}

	private void checkBossEntity(GeneratedRegion region, UUID boss, List<String> problems) {
		for (SceneInstance scene : region.scenes()) {
			List<SceneEntity> guardians = scene.state().entities().stream()
					.filter(entity -> entity.definitionCode().equals(definition.bossEntity()))
					.toList();
			if (scene.id().equals(boss)) {
				long visible = guardians.stream()
						.filter(entity -> !scene.state().isHidden(HiddenContentKind.ENTITY, entity.id()))
						.count();
				if (guardians.size() != 1 || visible != 1) {
					problems.add("The boss scene must contain exactly one visible " + definition.bossEntity());
				}
			} else if (!guardians.isEmpty()) {
				problems.add(definition.bossEntity() + " appears outside the boss scene in " + scene.id());
			}
		}
	}

	private static void checkUniqueEvents(GeneratedRegion region, List<String> problems) {
		Set<String> seen = new HashSet<>();
		for (SceneInstance scene : region.scenes()) {
			for (SceneEvent event : scene.state().activeEvents()) {
				if (!seen.add(event.definitionCode())) {
					problems.add("Event " + event.definitionCode() + " appears more than once in the region");
				}
			}
		}
	}

	private static Set<UUID> difference(Set<UUID> all, Set<UUID> reached) {
		Set<UUID> missing = new HashSet<>(all);
		missing.removeAll(reached);
		return missing;
	}
}
