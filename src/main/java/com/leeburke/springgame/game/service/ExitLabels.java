package com.leeburke.springgame.game.service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.leeburke.springgame.ai.narration.Surroundings;
import com.leeburke.springgame.content.world.FixedSceneDefinition;
import com.leeburke.springgame.content.world.RegionDefinition;
import com.leeburke.springgame.content.world.WorldContentCatalog;
import com.leeburke.springgame.persistence.WorldStore;
import com.leeburke.springgame.world.HiddenContentKind;
import com.leeburke.springgame.world.SceneExit;
import com.leeburke.springgame.world.SceneInstance;
import com.leeburke.springgame.world.SceneKind;

/**
 * What each known exit of a scene is called, as far as the player can know:
 * <ul>
 * <li>the hub's exit names its region from the start ("the road to the Hollow Chapel"): that is
 * established public lore;</li>
 * <li>an exit to a scene the player has already discovered names that scene ("the way to the
 * Ossuary");</li>
 * <li>any other exit is {@value Surroundings#UNEXPLORED}: undiscovered destinations are never
 * revealed.</li>
 * </ul>
 * Derived from persisted state on every read, so older runs get labels too.
 */
final class ExitLabels {

	private ExitLabels() {
	}

	static Map<String, String> of(SceneInstance scene, WorldStore world, WorldContentCatalog content) {
		Objects.requireNonNull(scene, "scene");
		Map<String, String> labels = new LinkedHashMap<>();
		for (SceneExit exit : scene.state().exits()) {
			if (scene.state().isHidden(HiddenContentKind.EXIT, exit.id())) {
				continue;
			}
			labels.put(exit.id(), label(scene, exit, world, content));
		}
		return labels;
	}

	/**
	 * Known exits that lead back: to a scene the player has already discovered. The hub's road is
	 * never one of them; it is always the way on. Used to tell "onward" from going back.
	 */
	static Set<String> backward(SceneInstance scene, WorldStore world, WorldContentCatalog content) {
		Objects.requireNonNull(scene, "scene");
		Set<String> back = new LinkedHashSet<>();
		for (SceneExit exit : scene.state().exits()) {
			if (!scene.state().isHidden(HiddenContentKind.EXIT, exit.id()) && hubRoad(scene, exit, content) == null
					&& world.findScene(exit.destinationSceneId()).filter(SceneInstance::discovered).isPresent()) {
				back.add(exit.id());
			}
		}
		return back;
	}

	/** Known exits whose destination the player has not discovered: the unexplored ways. */
	static Set<String> undiscovered(SceneInstance scene, WorldStore world) {
		Objects.requireNonNull(scene, "scene");
		Set<String> unexplored = new LinkedHashSet<>();
		for (SceneExit exit : scene.state().exits()) {
			if (!scene.state().isHidden(HiddenContentKind.EXIT, exit.id())
					&& world.findScene(exit.destinationSceneId()).filter(SceneInstance::discovered).isEmpty()) {
				unexplored.add(exit.id());
			}
		}
		return unexplored;
	}

	private static String label(SceneInstance scene, SceneExit exit, WorldStore world, WorldContentCatalog content) {
		String region = hubRoad(scene, exit, content);
		if (region != null) {
			return "the road to the " + region;
		}
		return world.findScene(exit.destinationSceneId())
				.filter(SceneInstance::discovered)
				.map(destination -> "the way to " + withArticle(SceneNames.scene(content, destination)))
				.orElse(Surroundings.UNEXPLORED);
	}

	/** The region the hub's fixed exit leads to, or null if this is not that exit. */
	private static String hubRoad(SceneInstance scene, SceneExit exit, WorldContentCatalog content) {
		if (scene.kind() != SceneKind.HUB) {
			return null;
		}
		return content.findFixedScene(scene.definitionCode())
				.map(FixedSceneDefinition::exit)
				.filter(fixed -> fixed.id().equals(exit.id()))
				.flatMap(fixed -> content.findRegion(fixed.destinationRegion()))
				.map(RegionDefinition::displayName)
				.orElse(null);
	}

	private static String withArticle(String name) {
		return name.startsWith("The ") ? name : "the " + name;
	}
}
