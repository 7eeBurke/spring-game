package com.leeburke.springgame.game;

import java.util.List;
import java.util.Objects;

import com.leeburke.springgame.content.world.ContainerRules;
import com.leeburke.springgame.world.ContainerState;
import com.leeburke.springgame.world.SceneInstance;
import com.leeburke.springgame.world.SceneObject;
import com.leeburke.springgame.world.SceneState;

/**
 * Scenes stored before containers had state hold container objects (crates) with no record of
 * being open or what is inside. They are treated as closed and empty: nothing is invented for a
 * run that never rolled contents. The normalised state is only written back if a turn changes it.
 */
public final class LegacyContainers {

	private LegacyContainers() {
	}

	public static SceneInstance normalized(SceneInstance scene, ContainerRules rules) {
		Objects.requireNonNull(scene, "scene");
		SceneState state = scene.state();
		SceneState normalized = state;
		for (SceneObject object : state.objects()) {
			if (rules.isContainer(object.definitionCode()) && state.container(object.id()).isEmpty()) {
				normalized = normalized.withContainer(new ContainerState(object.id(), false, List.of()));
			}
		}
		return normalized == state ? scene
				: new SceneInstance(scene.id(), scene.runId(), scene.definitionCode(), scene.placement(), scene.discovered(),
						scene.revision(), normalized);
	}
}
