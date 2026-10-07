package com.leeburke.springgame.world.generation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.leeburke.springgame.world.RegionInstance;
import com.leeburke.springgame.world.SceneInstance;

/**
 * A complete generated region, in memory, before persistence.
 * <p>
 * {@code routeStages} lists route scene IDs from the entry stage to the pre-boss stage (a stage of
 * two scenes is a branch). Optional scenes are dead ends; the boss follows the pre-boss scene. This
 * structure is generation-time metadata used for validation; it is not persisted.
 */
public record GeneratedRegion(
		RegionInstance region,
		List<List<UUID>> routeStages,
		List<UUID> optionalSceneIds,
		UUID bossSceneId,
		List<SceneInstance> scenes) {

	public GeneratedRegion {
		Objects.requireNonNull(region, "region");
		Objects.requireNonNull(bossSceneId, "bossSceneId");
		routeStages = Objects.requireNonNull(routeStages, "routeStages").stream().map(List::copyOf).toList();
		optionalSceneIds = List.copyOf(Objects.requireNonNull(optionalSceneIds, "optionalSceneIds"));
		scenes = List.copyOf(Objects.requireNonNull(scenes, "scenes"));
		if (routeStages.size() < 2 || routeStages.getFirst().size() != 1 || routeStages.getLast().size() != 1) {
			throw new IllegalArgumentException("A region route needs a single entry stage and a single pre-boss stage");
		}
		List<UUID> structured = new ArrayList<>();
		routeStages.forEach(structured::addAll);
		structured.addAll(optionalSceneIds);
		structured.add(bossSceneId);
		Set<UUID> structuredSet = new HashSet<>(structured);
		if (structuredSet.size() != structured.size()) {
			throw new IllegalArgumentException("A scene appears more than once in the region structure");
		}
		Set<UUID> sceneIds = new HashSet<>();
		for (SceneInstance scene : scenes) {
			if (!sceneIds.add(scene.id())) {
				throw new IllegalArgumentException("Duplicate generated scene id " + scene.id());
			}
		}
		if (!sceneIds.equals(structuredSet)) {
			throw new IllegalArgumentException("Generated scenes do not match the region structure");
		}
	}

	public UUID entrySceneId() {
		return routeStages.getFirst().getFirst();
	}

	public UUID preBossSceneId() {
		return routeStages.getLast().getFirst();
	}

	public Optional<SceneInstance> scene(UUID sceneId) {
		return scenes.stream().filter(scene -> scene.id().equals(sceneId)).findFirst();
	}

	public Set<UUID> sceneIds() {
		Set<UUID> ids = new HashSet<>();
		scenes.forEach(scene -> ids.add(scene.id()));
		return ids;
	}
}
