package com.leeburke.springgame.world;

import java.util.Objects;
import java.util.UUID;

/**
 * Where a scene belongs. A hub belongs directly to the run and has no region or procedural seed;
 * a region scene belongs to a region and carries its scene seed. Invalid combinations cannot be
 * represented, and no magic UUIDs or seeds are needed.
 */
public sealed interface ScenePlacement {

	SceneKind kind();

	record Hub() implements ScenePlacement {
		@Override
		public SceneKind kind() {
			return SceneKind.HUB;
		}
	}

	record Region(UUID regionInstanceId, long sceneSeed) implements ScenePlacement {
		public Region {
			Objects.requireNonNull(regionInstanceId, "regionInstanceId");
		}

		@Override
		public SceneKind kind() {
			return SceneKind.REGION;
		}
	}
}
