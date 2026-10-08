package com.leeburke.springgame.game;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.leeburke.springgame.action.resolution.ActionResolutionContext;
import com.leeburke.springgame.action.resolution.IncomingAttack;
import com.leeburke.springgame.action.resolution.TargetCombatProfile;
import com.leeburke.springgame.action.resolution.TargetProfileKey;
import com.leeburke.springgame.action.validation.ValidatedActionIntent;
import com.leeburke.springgame.ai.interpreter.InterpretationContextBuilder;
import com.leeburke.springgame.ai.interpreter.InterpretationSetup;
import com.leeburke.springgame.content.GameContentCatalog;
import com.leeburke.springgame.content.enemy.EnemyCatalog;
import com.leeburke.springgame.enemy.EnemyCombatant;
import com.leeburke.springgame.enemy.EnemyInstance;
import com.leeburke.springgame.enemy.EnemyTargetProfiles;

/**
 * Builds the two views of one turn from a snapshot: the player-safe interpretation setup (aliases,
 * Stage 10 validation context) and the backend-only Stage 11 resolution context. Only living,
 * visible enemies get combat profiles; every visible enemy's HP is passed so a target felled
 * earlier in the same intent cancels later attacks on it.
 */
public final class ResolutionContextFactory {

	private final InterpretationContextBuilder interpretation;
	private final EnemyCatalog enemies;
	private final GameContentCatalog content;

	public ResolutionContextFactory(InterpretationContextBuilder interpretation, EnemyCatalog enemies, GameContentCatalog content) {
		this.interpretation = Objects.requireNonNull(interpretation, "interpretation");
		this.enemies = Objects.requireNonNull(enemies, "enemies");
		this.content = Objects.requireNonNull(content, "content");
	}

	public InterpretationSetup interpretation(GameSnapshot snapshot) {
		List<IncomingAttack> incoming = snapshot.pending().map(p -> List.of(p.attack())).orElse(List.of());
		return interpretation.build(snapshot.view(), snapshot.player(), incoming, snapshot.fallenVisibleEnemyIds());
	}

	public ActionResolutionContext resolution(GameSnapshot snapshot, ValidatedActionIntent validated) {
		Map<TargetProfileKey, TargetCombatProfile> profiles = new HashMap<>();
		Map<String, Integer> hitPoints = new HashMap<>();
		for (EnemyInstance enemy : snapshot.visibleEnemies()) {
			hitPoints.put(enemy.entityId(), enemy.currentHp());
			if (enemy.currentHp() > 0) {
				profiles.putAll(EnemyTargetProfiles.profiles(combatant(enemy)));
			}
		}
		Map<String, IncomingAttack> incoming = snapshot.pending()
				.map(p -> Map.of(p.attack().ref(), p.attack())).orElse(Map.of());
		return new ActionResolutionContext(snapshot.player(), snapshot.scene().state(), snapshot.location(),
				validated.context().references(), profiles, incoming, hitPoints);
	}

	public EnemyCombatant combatant(EnemyInstance enemy) {
		return EnemyCombatant.of(enemy, enemies, content);
	}
}
