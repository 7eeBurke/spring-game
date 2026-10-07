package com.leeburke.springgame.content.enemy;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import com.leeburke.springgame.content.GameContentCatalog;
import com.leeburke.springgame.content.world.WorldContentCatalog;
import com.leeburke.springgame.content.world.WorldElementDefinition;
import com.leeburke.springgame.content.world.WorldElementKind;

/**
 * Immutable catalogue of enemy anatomies and enemy definitions, cross-checked against the other
 * static content: each enemy code is a world ENTITY, each anatomy and weapon code exists, and
 * attack option codes are unique across all enemies. A world entity without an enemy definition is
 * allowed (a future non-combat entity). Authored order is preserved.
 */
public final class EnemyCatalog {

	private final Map<String, AnatomyDefinition> anatomies;
	private final Map<String, EnemyDefinition> enemies;

	public EnemyCatalog(Collection<AnatomyDefinition> anatomies, Collection<EnemyDefinition> enemies,
			GameContentCatalog content, WorldContentCatalog worldContent) {
		Objects.requireNonNull(content, "content");
		Objects.requireNonNull(worldContent, "worldContent");
		this.anatomies = index("anatomy", anatomies, AnatomyDefinition::code);
		this.enemies = index("enemy", enemies, EnemyDefinition::code);

		Set<String> optionCodes = new HashSet<>();
		for (EnemyDefinition enemy : this.enemies.values()) {
			WorldElementDefinition element = worldContent.findElement(enemy.code()).orElseThrow(
					() -> new IllegalArgumentException("Enemy " + enemy.code() + " is not a world element"));
			if (element.kind() != WorldElementKind.ENTITY) {
				throw new IllegalArgumentException("Enemy " + enemy.code() + " is a world " + element.kind() + ", not an ENTITY");
			}
			if (!this.anatomies.containsKey(enemy.anatomy())) {
				throw new IllegalArgumentException("Enemy " + enemy.code() + " uses unknown anatomy " + enemy.anatomy());
			}
			if (content.findWeapon(enemy.weapon()).isEmpty()) {
				throw new IllegalArgumentException("Enemy " + enemy.code() + " uses unknown weapon " + enemy.weapon());
			}
			for (EnemyAttackOption option : enemy.attacks()) {
				if (!optionCodes.add(option.code())) {
					throw new IllegalArgumentException("Attack option " + option.code() + " is used by more than one enemy");
				}
			}
		}
	}

	public List<AnatomyDefinition> anatomies() {
		return List.copyOf(anatomies.values());
	}

	public Optional<AnatomyDefinition> findAnatomy(String code) {
		return Optional.ofNullable(code == null ? null : anatomies.get(code));
	}

	public List<EnemyDefinition> enemies() {
		return List.copyOf(enemies.values());
	}

	public Optional<EnemyDefinition> findEnemy(String code) {
		return Optional.ofNullable(code == null ? null : enemies.get(code));
	}

	/** The anatomy of a catalogued enemy. */
	public AnatomyDefinition anatomyOf(EnemyDefinition enemy) {
		return findAnatomy(enemy.anatomy())
				.orElseThrow(() -> new IllegalArgumentException("Enemy " + enemy.code() + " is not in this catalogue"));
	}

	private static <T> Map<String, T> index(String kind, Collection<T> definitions, Function<T, String> code) {
		Map<String, T> index = new LinkedHashMap<>();
		for (T definition : Objects.requireNonNull(definitions, kind + " definitions")) {
			Objects.requireNonNull(definition, kind + " definition");
			if (index.putIfAbsent(code.apply(definition), definition) != null) {
				throw new IllegalArgumentException("Duplicate " + kind + " code " + code.apply(definition));
			}
		}
		return index;
	}
}
