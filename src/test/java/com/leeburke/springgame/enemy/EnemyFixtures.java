package com.leeburke.springgame.enemy;

import java.util.EnumMap;
import java.util.Map;

import com.leeburke.springgame.content.GameContentCatalog;
import com.leeburke.springgame.content.GameContentLoader;
import com.leeburke.springgame.content.enemy.EnemyCatalog;
import com.leeburke.springgame.content.enemy.EnemyContentLoader;
import com.leeburke.springgame.content.enemy.EnemyDefinition;
import com.leeburke.springgame.content.world.WorldContentCatalog;
import com.leeburke.springgame.content.world.WorldContentLoader;
import com.leeburke.springgame.mechanics.BodyPart;
import com.leeburke.springgame.mechanics.BodySeverity;
import com.leeburke.springgame.mechanics.StatBlock;
import com.leeburke.springgame.mechanics.StatValue;

/** Bundled catalogues and hand-built enemy instances for deterministic enemy tests. */
public final class EnemyFixtures {

	public static final GameContentCatalog CONTENT = GameContentLoader.loadBundled();
	public static final WorldContentCatalog WORLD = WorldContentLoader.loadBundled();
	public static final EnemyCatalog ENEMIES = EnemyContentLoader.loadBundled(CONTENT, WORLD);

	private EnemyFixtures() {
	}

	public static EnemyDefinition definition(String code) {
		return ENEMIES.findEnemy(code).orElseThrow();
	}

	public static StatBlock stats(int might, int agility, int perception, int arcana, int resolve) {
		return new StatBlock(new StatValue(might), new StatValue(agility), new StatValue(perception), new StatValue(arcana),
				new StatValue(resolve));
	}

	/** A full-health instance of a bundled enemy with the given stats. */
	public static EnemyInstance instance(String code, String entityId, StatBlock stats) {
		EnemyDefinition definition = definition(code);
		int maxHp = definition.maxHpAt(stats.resolve());
		return new EnemyInstance(entityId, code, stats, maxHp, maxHp, EnemyBody.healthy(ENEMIES.anatomyOf(definition)),
				definition.weapon());
	}

	public static EnemyInstance withHp(EnemyInstance enemy, int currentHp) {
		return new EnemyInstance(enemy.entityId(), enemy.definitionCode(), enemy.stats(), enemy.maxHp(), currentHp,
				enemy.body(), enemy.weaponCode());
	}

	public static EnemyInstance withPart(EnemyInstance enemy, BodyPart part, BodySeverity severity) {
		Map<BodyPart, BodySeverity> parts = new EnumMap<>(enemy.body().severities());
		parts.put(part, severity);
		return new EnemyInstance(enemy.entityId(), enemy.definitionCode(), enemy.stats(), enemy.maxHp(), enemy.currentHp(),
				new EnemyBody(parts), enemy.weaponCode());
	}

	public static EnemyCombatant combatant(EnemyInstance enemy) {
		return EnemyCombatant.of(enemy, ENEMIES, CONTENT);
	}

	/** Acolyte with AGI 9 (+3), PER 7, MIG 5, RES 3, ARC 3: max HP 9. */
	public static EnemyInstance acolyte() {
		return instance("HOLLOW_ACOLYTE", "acolyte_1", stats(5, 9, 7, 3, 3));
	}

	/** Penitent with ARC 9 (+3), RES 7, PER 5, AGI 3, MIG 3: max HP 15. */
	public static EnemyInstance penitent() {
		return instance("ASHBOUND_PENITENT", "penitent_1", stats(3, 3, 5, 9, 7));
	}

	/** The fixed boss block: MIG 10, AGI 9, RES 8, PER 5, ARC 4; max HP 42. */
	public static EnemyInstance guardian() {
		return instance("CHAPEL_GUARDIAN", "guardian", stats(10, 9, 5, 4, 8));
	}

	/** Warden with MIG 10 (+4), RES 6, PER 5, AGI 3, ARC 3: max HP 20. */
	public static EnemyInstance warden() {
		return instance("BONE_WARDEN", "warden_1", stats(10, 3, 5, 3, 6));
	}
}
