package com.leeburke.springgame.content.enemy;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

import com.leeburke.springgame.mechanics.StatBlock;
import com.leeburke.springgame.mechanics.StatType;

/**
 * How an enemy's stats are produced: a randomly drawn 27-point shape assigned along an authored
 * priority, or a fixed authored block (the boss).
 */
public sealed interface EnemyStatRule {

	/** @param priority all five stats, highest first */
	record ShapePriority(List<StatType> priority) implements EnemyStatRule {
		public ShapePriority {
			priority = List.copyOf(Objects.requireNonNull(priority, "priority"));
			if (priority.size() != StatType.values().length || EnumSet.copyOf(priority).size() != priority.size()) {
				throw new IllegalArgumentException("A stat priority must list each of the five stats exactly once");
			}
		}
	}

	record Fixed(StatBlock stats) implements EnemyStatRule {
		public Fixed {
			Objects.requireNonNull(stats, "stats");
		}
	}
}
