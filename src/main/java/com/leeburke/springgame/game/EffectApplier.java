package com.leeburke.springgame.game;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.leeburke.springgame.action.resolution.OutcomeEffect;
import com.leeburke.springgame.action.resolution.ResolvedOutcome;
import com.leeburke.springgame.action.resolution.StepResult;
import com.leeburke.springgame.action.resolution.StepStatus;
import com.leeburke.springgame.enemy.EnemyInstance;
import com.leeburke.springgame.world.HiddenContentKind;

/**
 * Turns a confirmed {@link ResolvedOutcome} into state changes, from its typed effects only (never
 * the player's text). HP damage is subtracted and clamped at 0; a 0-HP enemy is fallen and a 0-HP
 * player is down. Impact severity is reported to narration but never changes body-part severity:
 * injury escalation is deferred. No conditions, healing, loot or XP. Pure: the caller persists the
 * result, once, in the turn's mechanics transaction.
 */
public final class EffectApplier {

	private EffectApplier() {
	}

	public static StateChanges apply(GameSnapshot snapshot, ResolvedOutcome outcome) {
		Objects.requireNonNull(snapshot, "snapshot");
		Objects.requireNonNull(outcome, "outcome");
		int playerHp = snapshot.player().currentHp();
		Map<String, Integer> enemyHp = new LinkedHashMap<>();
		String zone = snapshot.location().zoneId();
		Optional<String> exitId = Optional.empty();

		for (OutcomeEffect effect : outcome.effects()) {
			switch (effect) {
				case OutcomeEffect.TargetDamaged hit -> {
					EnemyInstance enemy = snapshot.enemy(hit.entityId())
							.orElseThrow(() -> new IllegalStateException("Damage to an enemy that is not in the scene"));
					int before = enemyHp.getOrDefault(hit.entityId(), enemy.currentHp());
					enemyHp.put(hit.entityId(), before - Math.min(before, hit.hpDamage()));
				}
				case OutcomeEffect.PlayerDamaged hurt -> playerHp -= Math.min(playerHp, hurt.hpDamage());
				case OutcomeEffect.PlayerMoved moved -> {
					if (!moved.fromZone().equals(zone) || !snapshot.scene().state().hasZone(moved.toZone())
							|| snapshot.scene().state().isHidden(HiddenContentKind.ZONE, moved.toZone())) {
						throw new IllegalStateException("A move that does not fit the current location");
					}
					zone = moved.toZone();
				}
				case OutcomeEffect.LeftScene left -> exitId = Optional.of(left.exitId());
			}
		}

		List<String> defeated = new ArrayList<>();
		enemyHp.forEach((id, hp) -> {
			if (hp == 0 && snapshot.enemy(id).orElseThrow().currentHp() > 0) {
				defeated.add(id);
			}
		});
		boolean attackConsumed = snapshot.pending().isPresent() && outcome.steps().stream()
				.anyMatch(s -> s.status() == StepStatus.RESOLVED && s.result()
						.filter(r -> r instanceof StepResult.DefenseResult d
								&& d.attackRef().equals(snapshot.pending().get().attack().ref()))
						.isPresent());
		return new StateChanges(snapshot.player().currentHp(), playerHp, enemyHp, defeated, zone, exitId, attackConsumed);
	}

	/**
	 * What a turn changes.
	 *
	 * @param enemyHp        new HP of each enemy that took damage
	 * @param defeated       enemies brought from above 0 to 0 HP this turn
	 * @param zone           the player's zone within the current scene after movement
	 * @param exitId         the exit the player left through, if any
	 * @param attackConsumed whether the pending attack was resolved by a defense
	 */
	public record StateChanges(int playerHpBefore, int playerHpAfter, Map<String, Integer> enemyHp, List<String> defeated,
			String zone, Optional<String> exitId, boolean attackConsumed) {

		public StateChanges {
			enemyHp = Map.copyOf(enemyHp);
			defeated = List.copyOf(defeated);
			Objects.requireNonNull(zone, "zone");
			Objects.requireNonNull(exitId, "exitId");
		}

		public int playerHpLost() {
			return playerHpBefore - playerHpAfter;
		}

		public boolean playerDown() {
			return playerHpAfter == 0;
		}
	}
}
