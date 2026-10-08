package com.leeburke.springgame.game.service;

import com.leeburke.springgame.content.world.FixedSceneDefinition;
import com.leeburke.springgame.content.world.SceneArchetypeDefinition;
import com.leeburke.springgame.content.world.WorldContentCatalog;
import com.leeburke.springgame.world.SceneInstance;
import com.leeburke.springgame.world.SceneZone;

/** Player-visible names of scenes and zones, from the content catalogue and the scene's own zones. */
final class SceneNames {

	private SceneNames() {
	}

	static String scene(WorldContentCatalog world, SceneInstance scene) {
		return world.findFixedScene(scene.definitionCode()).map(FixedSceneDefinition::displayName)
				.or(() -> world.findArchetype(scene.definitionCode()).map(SceneArchetypeDefinition::displayName))
				.orElseThrow(() -> new IllegalStateException("Unknown scene definition " + scene.definitionCode()));
	}

	static String zone(SceneInstance scene, String zoneId) {
		return scene.state().zones().stream().filter(z -> z.id().equals(zoneId)).map(SceneZone::displayName).findFirst()
				.orElseThrow(() -> new IllegalStateException("Zone " + zoneId + " is not in scene " + scene.id()));
	}
}
