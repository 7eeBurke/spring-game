package com.leeburke.springgame.game;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.leeburke.springgame.character.PlayerCharacterState;
import com.leeburke.springgame.enemy.EnemyInstance;
import com.leeburke.springgame.world.HiddenContentKind;
import com.leeburke.springgame.world.SceneKnowledge;
import com.leeburke.springgame.world.PlayerLocation;
import com.leeburke.springgame.world.SceneInstance;
import com.leeburke.springgame.world.SceneZone;
import com.leeburke.springgame.world.view.PlayerSceneView;
import com.leeburke.springgame.world.view.PlayerSceneViewProjector;

/**
 * The authoritative state one turn works from, loaded inside a transaction: the run's session, the
 * player, where they are, the current scene, the enemies placed in it and any pending attack.
 * Backend-only: what the player may see is derived through {@link #view()}.
 */
public record GameSnapshot(RunSession session, long runSeed, PlayerCharacterState player, PlayerLocation location,
		SceneInstance scene, List<EnemyInstance> enemies, Optional<PendingAttack> pending) {

	public GameSnapshot {
		Objects.requireNonNull(session, "session");
		Objects.requireNonNull(player, "player");
		Objects.requireNonNull(location, "location");
		Objects.requireNonNull(scene, "scene");
		enemies = enemies.stream().sorted(Comparator.comparing(EnemyInstance::entityId)).toList();
		Objects.requireNonNull(pending, "pending");
		if (!location.sceneId().equals(scene.id())) {
			throw new IllegalArgumentException("The snapshot's scene is not the player's scene");
		}
	}

	public UUID runId() {
		return session.runId();
	}

	/**
	 * What the player knows of the scene: the zones they have seen, and the zone they stand in with
	 * those joined to it by a visible connection (filtered by the Stage 8 projector, so hidden content
	 * never appears). A scene stored before seen zones were recorded is known whole. This is the view
	 * the interpreter, the game view and narration work from; nothing further in is revealed.
	 */
	public PlayerSceneView view() {
		Set<String> known = SceneKnowledge.knownZones(scene.state(), location.zoneId());
		return PlayerSceneViewProjector.project(scene.state(), location.zoneId(), known);
	}

	/**
	 * Every non-hidden zone of the scene, as combat has always used it: which enemies are present and
	 * can act does not depend on what the player has seen (line of sight is deferred).
	 */
	public PlayerSceneView sceneView() {
		Set<String> visible = scene.state().zones().stream().map(SceneZone::id)
				.filter(zone -> !scene.state().isHidden(HiddenContentKind.ZONE, zone))
				.collect(Collectors.toSet());
		return PlayerSceneViewProjector.project(scene.state(), location.zoneId(), visible);
	}

	/** Enemies present and not hidden in this scene, living or fallen (whether or not the player has seen them yet). */
	public List<EnemyInstance> visibleEnemies() {
		Set<String> visible = sceneView().entities().stream().map(PlayerSceneView.VisibleEntity::id).collect(Collectors.toSet());
		return enemies.stream().filter(e -> visible.contains(e.entityId())).toList();
	}

	public Set<String> fallenVisibleEnemyIds() {
		return visibleEnemies().stream().filter(e -> e.currentHp() == 0).map(EnemyInstance::entityId).collect(Collectors.toSet());
	}

	public Optional<EnemyInstance> enemy(String entityId) {
		return enemies.stream().filter(e -> e.entityId().equals(entityId)).findFirst();
	}
}
