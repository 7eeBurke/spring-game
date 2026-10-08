package com.leeburke.springgame.game;

import java.util.List;
import java.util.Objects;

import com.leeburke.springgame.world.HiddenContentKind;
import com.leeburke.springgame.world.PlayerLocation;
import com.leeburke.springgame.world.SceneExit;
import com.leeburke.springgame.world.SceneInstance;

/**
 * Where an exit leads, decided by Java from the persisted world: the exit must exist in the current
 * scene and not be hidden; its destination must be another scene of the same run; and the player
 * arrives in the zone of the destination's own exit back to the origin. Generation guarantees
 * exactly one such return exit, so anything else is a corrupted world, not a gameplay outcome.
 */
public final class ExitTraversal {

	private ExitTraversal() {
	}

	public static PlayerLocation arrival(SceneInstance origin, String exitId, SceneInstance destination) {
		Objects.requireNonNull(origin, "origin");
		Objects.requireNonNull(exitId, "exitId");
		Objects.requireNonNull(destination, "destination");
		SceneExit exit = origin.state().exits().stream().filter(x -> x.id().equals(exitId)).findFirst()
				.orElseThrow(() -> new IllegalStateException("Exit " + exitId + " is not in the current scene"));
		if (origin.state().isHidden(HiddenContentKind.EXIT, exitId)) {
			throw new IllegalStateException("Exit " + exitId + " is hidden");
		}
		if (!exit.destinationSceneId().equals(destination.id()) || !destination.runId().equals(origin.runId())) {
			throw new IllegalStateException("Exit " + exitId + " does not lead to the given scene of this run");
		}
		List<SceneExit> back = destination.state().exits().stream()
				.filter(x -> x.destinationSceneId().equals(origin.id())).toList();
		if (back.size() != 1) {
			throw new IllegalStateException("Scene " + destination.id() + " has " + back.size() + " exits back to " + origin.id());
		}
		return new PlayerLocation(destination.id(), back.getFirst().zoneId());
	}
}
