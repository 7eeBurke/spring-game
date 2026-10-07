package com.leeburke.springgame.world.generation;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.leeburke.springgame.world.HiddenContentKind;
import com.leeburke.springgame.world.PlayerLocation;
import com.leeburke.springgame.world.SceneExit;
import com.leeburke.springgame.world.SceneInstance;
import com.leeburke.springgame.world.SceneKind;

/**
 * Everything generated for a new run's world, ready to persist atomically: the context snapshot
 * actually used, the hub, the generated region and the initial player location.
 * <p>
 * Checks that every part belongs to the run, and that the hub and region connect in both
 * directions: the hub's exits lead to the region entry, and the entry's single external exit leads
 * back to this hub.
 */
public record GeneratedRunWorld(
		UUID runId,
		GenerationContextSnapshot context,
		SceneInstance hub,
		GeneratedRegion region,
		PlayerLocation start) {

	public GeneratedRunWorld {
		Objects.requireNonNull(runId, "runId");
		Objects.requireNonNull(context, "context");
		Objects.requireNonNull(hub, "hub");
		Objects.requireNonNull(region, "region");
		Objects.requireNonNull(start, "start");

		if (hub.kind() != SceneKind.HUB || hub.revision() != 0 || !hub.runId().equals(runId)) {
			throw new IllegalArgumentException("The hub must be a new HUB scene of run " + runId);
		}
		if (!region.region().runId().equals(runId)) {
			throw new IllegalArgumentException("The region belongs to another run");
		}
		for (SceneInstance scene : region.scenes()) {
			if (!scene.runId().equals(runId)) {
				throw new IllegalArgumentException("Scene " + scene.id() + " belongs to another run");
			}
		}

		UUID entryId = region.entrySceneId();
		List<SceneExit> hubExits = hub.state().exits();
		if (hubExits.isEmpty() || hubExits.stream().anyMatch(exit -> !exit.destinationSceneId().equals(entryId))) {
			throw new IllegalArgumentException("The hub's exits must lead to the region entry scene " + entryId);
		}
		Set<UUID> regionSceneIds = region.sceneIds();
		SceneInstance entry = region.scene(entryId).orElseThrow();
		List<SceneExit> external = entry.state().exits().stream()
				.filter(exit -> !regionSceneIds.contains(exit.destinationSceneId()))
				.toList();
		if (external.size() != 1 || !external.getFirst().destinationSceneId().equals(hub.id())) {
			throw new IllegalArgumentException("The region entry must have exactly one external exit, leading back to hub " + hub.id());
		}

		if (!start.sceneId().equals(hub.id())) {
			throw new IllegalArgumentException("The player must start in the hub");
		}
		if (!hub.state().hasZone(start.zoneId()) || hub.state().isHidden(HiddenContentKind.ZONE, start.zoneId())) {
			throw new IllegalArgumentException("Start zone " + start.zoneId() + " must be a visible zone of the hub");
		}
	}
}
