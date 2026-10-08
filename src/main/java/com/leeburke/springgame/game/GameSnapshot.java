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
	 * What the player sees: every non-hidden zone of the current scene is visible (line of sight is
	 * deferred), filtered by the Stage 8 projector so hidden content never appears.
	 */
	public PlayerSceneView view() {
		Set<String> visible = scene.state().zones().stream().map(SceneZone::id)
				.filter(zone -> !scene.state().isHidden(HiddenContentKind.ZONE, zone))
				.collect(Collectors.toSet());
		return PlayerSceneViewProjector.project(scene.state(), location.zoneId(), visible);
	}

	/** Enemies the player can see in this scene, living or fallen. */
	public List<EnemyInstance> visibleEnemies() {
		Set<String> visible = view().entities().stream().map(PlayerSceneView.VisibleEntity::id).collect(Collectors.toSet());
		return enemies.stream().filter(e -> visible.contains(e.entityId())).toList();
	}

	public Set<String> fallenVisibleEnemyIds() {
		return visibleEnemies().stream().filter(e -> e.currentHp() == 0).map(EnemyInstance::entityId).collect(Collectors.toSet());
	}

	public Optional<EnemyInstance> enemy(String entityId) {
		return enemies.stream().filter(e -> e.entityId().equals(entityId)).findFirst();
	}
}
