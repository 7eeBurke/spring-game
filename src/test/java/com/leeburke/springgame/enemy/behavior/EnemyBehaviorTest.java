package com.leeburke.springgame.enemy.behavior;

import static com.leeburke.springgame.enemy.EnemyFixtures.acolyte;
import static com.leeburke.springgame.enemy.EnemyFixtures.combatant;
import static com.leeburke.springgame.enemy.EnemyFixtures.guardian;
import static com.leeburke.springgame.enemy.EnemyFixtures.instance;
import static com.leeburke.springgame.enemy.EnemyFixtures.penitent;
import static com.leeburke.springgame.enemy.EnemyFixtures.stats;
import static com.leeburke.springgame.enemy.EnemyFixtures.warden;
import static com.leeburke.springgame.enemy.EnemyFixtures.withHp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.leeburke.springgame.content.enemy.AnatomyDefinition;
import com.leeburke.springgame.content.enemy.EnemyAttackOption;
import com.leeburke.springgame.content.enemy.EnemyDefinition;
import com.leeburke.springgame.content.enemy.EnemyTrait;
import com.leeburke.springgame.enemy.EnemyAttacks;
import com.leeburke.springgame.enemy.EnemyBody;
import com.leeburke.springgame.enemy.EnemyCombatant;
import com.leeburke.springgame.enemy.EnemyFixtures;
import com.leeburke.springgame.enemy.EnemyInstance;
import com.leeburke.springgame.enemy.ScriptedRandom;

class EnemyBehaviorTest {

	private final EnemyBehavior behavior = new EnemyBehavior();

	private static EnemyDecisionContext context(EnemyInstance enemy, String... recent) {
		return new EnemyDecisionContext(combatant(enemy), List.of(recent), "attack_1");
	}

	private Map<String, Long> weights(EnemyDecisionContext context) {
		Map<String, Long> weights = new java.util.LinkedHashMap<>();
		behavior.weigh(context).forEach(c -> weights.put(c.code(), c.weight()));
		return weights;
	}

	/** An acolyte-shaped enemy with chosen traits and hold weight, for isolating one rule. */
	private static EnemyCombatant custom(List<EnemyTrait> traits, int holdWeight, int resolve, int currentHpOf9) {
		EnemyDefinition base = EnemyFixtures.definition("HOLLOW_ACOLYTE");
		EnemyDefinition definition = new EnemyDefinition(base.code(), base.anatomy(), base.weapon(), base.statPriority(),
				Map.of(), base.baseHp(), base.defenseStat(), traits, holdWeight, base.attacks());
		AnatomyDefinition anatomy = EnemyFixtures.ENEMIES.anatomyOf(base);
		int maxHp = definition.maxHpAt(new com.leeburke.springgame.mechanics.StatValue(resolve));
		EnemyInstance enemy = new EnemyInstance("acolyte_1", base.code(), stats(5, 9, 7, 3, resolve), maxHp,
				Math.min(currentHpOf9, maxHp), EnemyBody.healthy(anatomy), base.weapon());
		return new EnemyCombatant(enemy, definition, anatomy, EnemyFixtures.CONTENT.findWeapon(base.weapon()).orElseThrow());
	}

	private Map<String, Long> weights(EnemyCombatant enemy, String... recent) {
		return weights(new EnemyDecisionContext(enemy, List.of(recent), "attack_1"));
	}

	// --- Base weights ---

	@Test
	void baseWeightTablesInCandidateOrder() {
		assertThat(weights(context(acolyte()))).containsExactly(Map.entry("ACOLYTE_QUICK_SLASH", 55L),
				Map.entry("ACOLYTE_STAB", 45L), Map.entry("HOLD", 0L));
		assertThat(weights(context(warden()))).containsExactly(Map.entry("WARDEN_OVERHEAD_SMASH", 45L),
				Map.entry("WARDEN_HEAVY_SMASH", 35L), Map.entry("WARDEN_HAFT_HOOK", 20L), Map.entry("HOLD", 0L));
		// Penitent: hold 10 + CAUTIOUS 10.
		assertThat(weights(context(penitent()))).containsExactly(Map.entry("PENITENT_EMBER_BOLT", 70L),
				Map.entry("PENITENT_ROD_STRIKE", 20L), Map.entry("HOLD", 20L));
		assertThat(weights(context(guardian()))).containsExactly(Map.entry("GUARDIAN_SWEEPING_CUT", 40L),
				Map.entry("GUARDIAN_OVERHEAD_CLEAVE", 35L), Map.entry("GUARDIAN_LUNGE", 25L), Map.entry("HOLD", 0L));
	}

	// --- Traits ---

	@Test
	void aggressiveLowersHoldAndCautiousRaisesIt() {
		assertThat(weights(custom(List.of(), 30, 7, 99)).get("HOLD")).isEqualTo(30);
		assertThat(weights(custom(List.of(EnemyTrait.AGGRESSIVE), 30, 7, 99)).get("HOLD")).isEqualTo(20);
		assertThat(weights(custom(List.of(EnemyTrait.CAUTIOUS), 30, 7, 99)).get("HOLD")).isEqualTo(40);
		assertThat(weights(custom(List.of(EnemyTrait.AGGRESSIVE, EnemyTrait.CAUTIOUS), 30, 7, 99)).get("HOLD")).isEqualTo(30);
	}

	@Test
	void opportunisticHasNoEffectYet() {
		assertThat(weights(custom(List.of(EnemyTrait.OPPORTUNISTIC), 30, 3, 4)))
				.isEqualTo(weights(custom(List.of(), 30, 3, 4)));
	}

	// --- Pressure and Resolve ---

	@ParameterizedTest
	@CsvSource({ "3, 20", "5, 10", "6, 5", "7, 0", "10, 0" })
	void desperateEnemyHoldsMoreWithLowResolve(int resolve, long bonus) {
		// Current HP 1 is always at or below half.
		assertThat(weights(custom(List.of(), 0, resolve, 1)).get("HOLD")).isEqualTo(bonus);
	}

	@Test
	void desperationStartsAtHalfHp() {
		// RESOLVE 3: max HP 9. Desperate when 2 x HP <= 9, so 4 is desperate and 5 is not.
		assertThat(weights(custom(List.of(), 0, 3, 4)).get("HOLD")).isEqualTo(20);
		assertThat(weights(custom(List.of(), 0, 3, 5)).get("HOLD")).isZero();
		// RESOLVE 4: max HP 10. 2 x 5 = 10 is desperate, 6 is not.
		assertThat(weights(custom(List.of(), 0, 4, 5)).get("HOLD")).isEqualTo(15);
		assertThat(weights(custom(List.of(), 0, 4, 6)).get("HOLD")).isZero();
	}

	@Test
	void recklessIgnoresPressure() {
		assertThat(weights(custom(List.of(EnemyTrait.RECKLESS), 0, 3, 1)).get("HOLD")).isZero();
	}

	@Test
	void pressureDoesNotChangeAttackWeights() {
		assertThat(weights(custom(List.of(), 0, 3, 1)).get("ACOLYTE_QUICK_SLASH")).isEqualTo(55);
	}

	// --- Repetition ---

	@Test
	void repetitionCostsFifteenPerOccurrenceInTheLastTwoChoices() {
		EnemyCombatant plain = custom(List.of(), 0, 7, 99);
		assertThat(weights(plain, "ACOLYTE_STAB").get("ACOLYTE_STAB")).isEqualTo(30);
		assertThat(weights(plain, "ACOLYTE_STAB", "ACOLYTE_STAB").get("ACOLYTE_STAB")).isEqualTo(15);
		// Only the last two count.
		assertThat(weights(plain, "ACOLYTE_STAB", "HOLD", "ACOLYTE_QUICK_SLASH").get("ACOLYTE_STAB")).isEqualTo(45);
		assertThat(weights(plain, "ACOLYTE_STAB", "HOLD", "ACOLYTE_QUICK_SLASH").get("ACOLYTE_QUICK_SLASH")).isEqualTo(40);
	}

	@Test
	void adaptiveDoublesAndRelentlessRemovesTheRepetitionPenalty() {
		assertThat(weights(custom(List.of(EnemyTrait.ADAPTIVE), 0, 7, 99), "ACOLYTE_STAB").get("ACOLYTE_STAB")).isEqualTo(15);
		assertThat(weights(custom(List.of(EnemyTrait.ADAPTIVE), 0, 7, 99), "ACOLYTE_STAB", "ACOLYTE_STAB")
				.get("ACOLYTE_STAB")).isZero();
		assertThat(weights(custom(List.of(EnemyTrait.RELENTLESS), 0, 7, 99), "ACOLYTE_STAB", "ACOLYTE_STAB")
				.get("ACOLYTE_STAB")).isEqualTo(45);
	}

	@Test
	void holdIsPenalisedForRepetitionToo() {
		assertThat(weights(custom(List.of(), 30, 7, 99), "HOLD", "HOLD").get("HOLD")).isZero();
	}

	@Test
	void weightsNeverGoNegative() {
		assertThat(weights(custom(List.of(EnemyTrait.AGGRESSIVE), 0, 7, 99)).get("HOLD")).isZero();
		assertThat(behavior.weigh(context(acolyte()))).allMatch(c -> c.weight() >= 0);
	}

	// --- Selection ---

	@Test
	void cumulativeBoundariesPickCandidatesInOrder() {
		// Acolyte: quick slash [0, 55), stab [55, 100), hold has weight 0.
		assertThat(chosen(acolyte(), 0)).isEqualTo("ACOLYTE_QUICK_SLASH");
		assertThat(chosen(acolyte(), 54)).isEqualTo("ACOLYTE_QUICK_SLASH");
		assertThat(chosen(acolyte(), 55)).isEqualTo("ACOLYTE_STAB");
		assertThat(chosen(acolyte(), 99)).isEqualTo("ACOLYTE_STAB");
		// Penitent: bolt [0, 70), rod [70, 90), hold [90, 110).
		assertThat(chosen(penitent(), 89)).isEqualTo("PENITENT_ROD_STRIKE");
		assertThat(chosen(penitent(), 90)).isEqualTo("HOLD");
	}

	@Test
	void oneLongDrawBoundedByTheTotalWeight() {
		ScriptedRandom rng = new ScriptedRandom(10);
		behavior.decide(context(penitent()), rng);
		assertThat(rng.bounds()).containsExactly(110L);
	}

	@Test
	void zeroWeightCandidateIsNeverChosen() {
		// ADAPTIVE acolyte that stabbed twice: stab weight 0, so every draw in [0, 55) is the quick slash.
		EnemyCombatant enemy = custom(List.of(EnemyTrait.ADAPTIVE), 0, 7, 99);
		EnemyDecisionContext context = new EnemyDecisionContext(enemy, List.of("ACOLYTE_STAB", "ACOLYTE_STAB"), "attack_1");
		ScriptedRandom rng = new ScriptedRandom(54);
		assertThat(((EnemyDecision.Attack) behavior.decide(context, rng)).optionCode()).isEqualTo("ACOLYTE_QUICK_SLASH");
		assertThat(rng.bounds()).containsExactly(55L);
	}

	@Test
	void allZeroWeightsHoldWithoutDrawing() {
		EnemyCombatant allZero = zeroWeightEnemy();
		ScriptedRandom rng = new ScriptedRandom();
		EnemyDecision decision = behavior.decide(new EnemyDecisionContext(allZero, List.of("ZERO_POKE", "ZERO_POKE"),
				"attack_1"), rng);
		assertThat(decision).isInstanceOf(EnemyDecision.Hold.class);
		assertThat(decision.weights()).allMatch(c -> c.weight() == 0);
		assertThat(rng.bounds()).isEmpty();
	}

	@Test
	void holdProducesNoIncomingAttack() {
		EnemyDecision decision = behavior.decide(context(penitent()), new ScriptedRandom(100));
		assertThat(decision).isInstanceOf(EnemyDecision.Hold.class);
	}

	@Test
	void attackDecisionCarriesTheBuiltIncomingAttack() {
		EnemyCombatant guardian = combatant(guardian());
		EnemyDecision decision = behavior.decide(new EnemyDecisionContext(guardian, List.of(), "attack_7"),
				new ScriptedRandom(99));
		EnemyAttackOption lunge = guardian.definition().findAttack("GUARDIAN_LUNGE").orElseThrow();
		assertThat(decision).isEqualTo(new EnemyDecision.Attack("GUARDIAN_LUNGE",
				EnemyAttacks.build("attack_7", guardian, lunge), behavior.weigh(context(guardian()))));
	}

	@Test
	void sameInputsAndDrawGiveTheSameDecision() {
		assertThat(behavior.decide(context(warden(), "WARDEN_HEAVY_SMASH"), new ScriptedRandom(40)))
				.isEqualTo(behavior.decide(context(warden(), "WARDEN_HEAVY_SMASH"), new ScriptedRandom(40)));
	}

	@Test
	void downEnemyCannotDecide() {
		assertThatIllegalArgumentException().isThrownBy(
				() -> behavior.decide(context(withHp(acolyte(), 0)), new ScriptedRandom(0)));
	}

	@Test
	void weightArithmeticIsOverflowChecked() {
		EnemyDefinition base = EnemyFixtures.definition("HOLLOW_ACOLYTE");
		EnemyDefinition heavy = new EnemyDefinition(base.code(), base.anatomy(), base.weapon(), base.statPriority(), Map.of(),
				base.baseHp(), base.defenseStat(), List.of(),
				Integer.MAX_VALUE, List.of(new EnemyAttackOption("BIG_A", base.attacks().getFirst().method(),
						base.attacks().getFirst().template(), Integer.MAX_VALUE)));
		AnatomyDefinition anatomy = EnemyFixtures.ENEMIES.anatomyOf(base);
		EnemyInstance enemy = instance("HOLLOW_ACOLYTE", "acolyte_1", stats(5, 9, 7, 3, 7));
		EnemyCombatant combatant = new EnemyCombatant(enemy, heavy, anatomy, EnemyFixtures.CONTENT.findWeapon("DAGGER").orElseThrow());
		ScriptedRandom rng = new ScriptedRandom(Integer.MAX_VALUE);

		EnemyDecision decision = behavior.decide(new EnemyDecisionContext(combatant, List.of(), "attack_1"), rng);
		assertThat(rng.bounds()).containsExactly(2L * Integer.MAX_VALUE);
		assertThat(decision).isInstanceOf(EnemyDecision.Hold.class);
	}

	// --- Context ---

	@Test
	void historyIsCopiedAndNeverMutated() {
		List<String> history = new ArrayList<>(List.of("ACOLYTE_STAB", "HOLD"));
		EnemyDecisionContext context = new EnemyDecisionContext(combatant(acolyte()), history, "attack_1");
		history.add("ACOLYTE_STAB");
		behavior.decide(context, new ScriptedRandom(0));
		assertThat(context.recentChoices()).containsExactly("ACOLYTE_STAB", "HOLD");
		assertThat(history).containsExactly("ACOLYTE_STAB", "HOLD", "ACOLYTE_STAB");
	}

	@Test
	void historyEntriesMustBeCleanAndBelongToTheEnemy() {
		EnemyCombatant acolyte = combatant(acolyte());
		List<String> withNull = new ArrayList<>();
		withNull.add(null);
		assertThatNullPointerException().isThrownBy(() -> new EnemyDecisionContext(acolyte, withNull, "attack_1"));
		assertThatIllegalArgumentException().isThrownBy(() -> new EnemyDecisionContext(acolyte, List.of(" "), "attack_1"));
		assertThatIllegalArgumentException().isThrownBy(() -> new EnemyDecisionContext(acolyte, List.of("HOLD "), "attack_1"));
		assertThatIllegalArgumentException().isThrownBy(
				() -> new EnemyDecisionContext(acolyte, List.of("WARDEN_HEAVY_SMASH"), "attack_1"));
		assertThatIllegalArgumentException().isThrownBy(() -> new EnemyDecisionContext(acolyte, List.of("hold"), "attack_1"));
	}

	@Test
	void attackReferenceMustBeClean() {
		EnemyCombatant acolyte = combatant(acolyte());
		assertThatIllegalArgumentException().isThrownBy(() -> new EnemyDecisionContext(acolyte, List.of(), ""));
		assertThatIllegalArgumentException().isThrownBy(() -> new EnemyDecisionContext(acolyte, List.of(), "attack_1 "));
		assertThatNullPointerException().isThrownBy(() -> new EnemyDecisionContext(acolyte, List.of(), null));
	}

	@Test
	void selfMustMatchItsDefinition() {
		EnemyInstance acolyte = acolyte();
		assertThatIllegalArgumentException().isThrownBy(() -> new EnemyDecisionContext(
				new EnemyCombatant(acolyte, EnemyFixtures.definition("BONE_WARDEN"), EnemyFixtures.ENEMIES.anatomyOf(
						EnemyFixtures.definition("BONE_WARDEN")), EnemyFixtures.CONTENT.findWeapon("DAGGER").orElseThrow()),
				List.of(), "attack_1"));
	}

	private String chosen(EnemyInstance enemy, long draw) {
		return switch (behavior.decide(context(enemy), new ScriptedRandom(draw))) {
			case EnemyDecision.Attack attack -> attack.optionCode();
			case EnemyDecision.Hold hold -> "HOLD";
		};
	}

	/** One attack of weight 15 and no hold weight: ADAPTIVE repetition (30 per use) zeroes it. */
	private static EnemyCombatant zeroWeightEnemy() {
		EnemyDefinition base = EnemyFixtures.definition("HOLLOW_ACOLYTE");
		EnemyDefinition definition = new EnemyDefinition(base.code(), base.anatomy(), base.weapon(), base.statPriority(),
				Map.of(), base.baseHp(), base.defenseStat(), List.of(EnemyTrait.ADAPTIVE), 0,
				List.of(new EnemyAttackOption("ZERO_POKE", base.attacks().getFirst().method(), base.attacks().getFirst().template(),
						15)));
		return new EnemyCombatant(acolyte(), definition, EnemyFixtures.ENEMIES.anatomyOf(base),
				EnemyFixtures.CONTENT.findWeapon("DAGGER").orElseThrow());
	}
}
