package com.leeburke.springgame.game;

import static com.leeburke.springgame.enemy.EnemyFixtures.withHp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.leeburke.springgame.action.resolution.IncomingAttack;
import com.leeburke.springgame.action.resolution.OverallResult;
import com.leeburke.springgame.action.resolution.ResolvedOutcome;
import com.leeburke.springgame.action.resolution.StepStatus;
import com.leeburke.springgame.ai.interpreter.AliasKind;
import com.leeburke.springgame.character.PlayerBody;
import com.leeburke.springgame.character.PlayerCharacterState;
import com.leeburke.springgame.enemy.EnemyFixtures;
import com.leeburke.springgame.enemy.EnemyInstance;
import com.leeburke.springgame.game.EffectApplier.StateChanges;
import com.leeburke.springgame.mechanics.BodyPart;
import com.leeburke.springgame.mechanics.BodySeverity;
import com.leeburke.springgame.world.PlayerLocation;
import com.leeburke.springgame.world.SceneExit;
import com.leeburke.springgame.world.SceneInstance;
import com.leeburke.springgame.world.view.PlayerSceneView;

/** Pure tests of the Stage 14 game rules over a real generated run. */
class GameRulesTest {

	private static final GameHarness HARNESS = IntStream.rangeClosed(1, 60).mapToObj(GameHarness::new)
			.filter(h -> h.init.world().region().scenes().stream().anyMatch(s -> h.visibleEnemies(s).size() >= 2))
			.findFirst().orElseThrow();
	private static final String WEAPON = " with weapon_1";

	private final SceneInstance scene = HARNESS.sceneWithEnemies();
	private final List<EnemyInstance> visible = HARNESS.visibleEnemies(scene);
	private final EnemyInstance first = visible.getFirst();

	// --- Encounter: who acts, and when ---

	@Test
	void nextActorIsRoundRobinInEntityOrderAndWraps() {
		List<EnemyInstance> two = visible.subList(0, 2);
		UUID id = scene.id();

		assertThat(EncounterRules.nextActor(two, id, Optional.empty())).contains(two.get(0));
		assertThat(EncounterRules.nextActor(two, id, cursor(id, two.get(0)))).contains(two.get(1));
		assertThat(EncounterRules.nextActor(two, id, cursor(id, two.get(1)))).contains(two.get(0));
	}

	@Test
	void aCursorFromAnotherSceneResetsTheOrder() {
		assertThat(EncounterRules.nextActor(visible, scene.id(), cursor(UUID.randomUUID(), visible.get(1))))
				.contains(visible.getFirst());
	}

	@Test
	void fallenEnemiesNeverAct() {
		List<EnemyInstance> withFallen = List.of(withHp(visible.get(0), 0), visible.get(1));

		assertThat(EncounterRules.nextActor(withFallen, scene.id(), Optional.empty())).contains(visible.get(1));
		assertThat(EncounterRules.nextActor(withFallen, scene.id(), cursor(scene.id(), visible.get(1)))).contains(visible.get(1));
		assertThat(EncounterRules.nextActor(List.of(withHp(first, 0)), scene.id(), Optional.empty())).isEmpty();
		assertThat(EncounterRules.nextActor(List.of(), scene.id(), Optional.empty())).isEmpty();
	}

	@Test
	void aCursorOnAnEnemyThatFellMovesToTheNextLivingOne() {
		List<EnemyInstance> two = List.of(withHp(visible.get(0), 0), visible.get(1));

		assertThat(EncounterRules.nextActor(two, scene.id(), cursor(scene.id(), visible.get(0)))).contains(visible.get(1));
	}

	@Test
	void theEnemyPhaseFollowsAResolvedNonDefendStep() {
		GameSnapshot snapshot = HARNESS.snapshot(scene);
		ResolvedOutcome attack = HARNESS.resolve(snapshot, "/attack " + HARNESS.alias(snapshot, first.entityId()) + " slash" + WEAPON, 1);

		assertThat(attack.steps().getFirst().status()).isEqualTo(StepStatus.RESOLVED);
		assertThat(EncounterRules.enemyPhaseRuns(attack, RunStatus.ACTIVE, false, false)).isTrue();
		assertThat(EncounterRules.enemyPhaseRuns(attack, RunStatus.DEAD, false, false)).isFalse();
		assertThat(EncounterRules.enemyPhaseRuns(attack, RunStatus.VICTORIOUS, false, false)).isFalse();
		assertThat(EncounterRules.enemyPhaseRuns(attack, RunStatus.ACTIVE, true, false)).isFalse();
		assertThat(EncounterRules.enemyPhaseRuns(attack, RunStatus.ACTIVE, false, true)).isFalse();
	}

	@Test
	void aDefenseOnlyTurnNeverProvokesAnotherAttack() {
		GameSnapshot snapshot = pendingSnapshot(HARNESS.player.maxHp());

		for (int face : new int[] { 1, 10, 20 }) {
			ResolvedOutcome defense = HARNESS.resolve(snapshot, "/defend parry", face);
			assertThat(defense.steps().getFirst().status()).isEqualTo(StepStatus.RESOLVED);
			assertThat(EncounterRules.enemyPhaseRuns(defense, RunStatus.ACTIVE, false, false)).as("roll " + face).isFalse();
		}
	}

	@Test
	void defendingThenCounterattackingLetsAnEnemyRespond() {
		GameSnapshot snapshot = pendingSnapshot(HARNESS.player.maxHp());
		ResolvedOutcome outcome = HARNESS.resolve(snapshot,
				"/defend parry ; /attack " + HARNESS.alias(snapshot, first.entityId()) + " slash" + WEAPON, 20, 20);

		assertThat(outcome.steps()).extracting(s -> s.status()).containsOnly(StepStatus.RESOLVED);
		assertThat(EncounterRules.enemyPhaseRuns(outcome, RunStatus.ACTIVE, false, false)).isTrue();
	}

	@Test
	void anIntentWithNothingResolvedLetsNoEnemyAct() {
		GameSnapshot snapshot = HARNESS.snapshot(scene);
		ResolvedOutcome outcome = HARNESS.resolve(snapshot, "/listen");

		assertThat(outcome.overall()).isEqualTo(OverallResult.MECHANICS_UNAVAILABLE);
		assertThat(EncounterRules.enemyPhaseRuns(outcome, RunStatus.ACTIVE, false, false)).isFalse();
	}

	// --- Mandatory defense ---

	@Test
	void whileAnAttackIsPendingTheTurnMustOpenWithADefense() {
		GameSnapshot snapshot = pendingSnapshot(HARNESS.player.maxHp());
		Optional<PendingAttack> pending = snapshot.pending();
		String target = HARNESS.alias(snapshot, first.entityId());

		assertThat(DefenseGate.answers(HARNESS.command(snapshot, "/defend evade").intent(), pending)).isTrue();
		assertThat(DefenseGate.answers(HARNESS.command(snapshot, "/defend parry ; /attack " + target + " slash" + WEAPON).intent(),
				pending)).isTrue();
		assertThat(DefenseGate.answers(HARNESS.command(snapshot, "/attack " + target + " slash" + WEAPON).intent(), pending)).isFalse();
		assertThat(DefenseGate.answers(HARNESS.command(snapshot, "/hold").intent(), pending)).isFalse();
		assertThat(DefenseGate.answers(HARNESS.command(HARNESS.snapshot(scene), "/hold").intent(), Optional.empty())).isTrue();
	}

	@Test
	void aFailedDefenseStillResolvesTheAttack() {
		GameSnapshot snapshot = pendingSnapshot(HARNESS.player.maxHp());

		assertThat(DefenseGate.resolved(HARNESS.resolve(snapshot, "/defend parry", 20), snapshot.pending())).isTrue();
		assertThat(DefenseGate.resolved(HARNESS.resolve(snapshot, "/defend parry", 1), snapshot.pending())).isTrue();
	}

	@Test
	void aDefenseThatCannotBeResolvedDoesNotCount() {
		PendingAttack base = HARNESS.pending(scene, first, "attack-1");
		IncomingAttack a = base.attack();
		PendingAttack aimed = new PendingAttack(new IncomingAttack(a.ref(), a.attackerEntityId(), a.template(), a.difficulty(),
				a.baseDamage(), a.weaponTrauma(), a.effectiveness(), a.attackFormModifier(), a.anatomyInteractionModifier(),
				Optional.of(BodyPart.LEFT_ARM)), base.sceneId(), base.optionCode(), base.weaponCode(), 1, base.cueText(), Optional.empty());
		Map<BodyPart, BodySeverity> severities = new EnumMap<>(PlayerBody.healthy().severities());
		severities.put(BodyPart.LEFT_ARM, BodySeverity.DESTROYED);
		GameSnapshot snapshot = withBody(HARNESS.snapshot(scene, HARNESS.init.enemies().inScene(scene.id()), Optional.of(aimed),
				HARNESS.player.maxHp()), new PlayerBody(severities));

		ResolvedOutcome outcome = HARNESS.resolve(snapshot, "/defend parry");

		assertThat(outcome.steps().getFirst().status()).isEqualTo(StepStatus.MECHANICS_UNAVAILABLE);
		assertThat(DefenseGate.resolved(outcome, snapshot.pending())).isFalse();
	}

	@Test
	void aDefenseAgainstADifferentAttackDoesNotCount() {
		GameSnapshot snapshot = pendingSnapshot(HARNESS.player.maxHp());
		ResolvedOutcome outcome = HARNESS.resolve(snapshot, "/defend parry", 20);

		assertThat(DefenseGate.resolved(outcome, Optional.of(HARNESS.pending(scene, first, "attack-99")))).isFalse();
		assertThat(DefenseGate.resolved(outcome, Optional.empty())).isTrue();
	}

	// --- Effects ---

	@Test
	void aHitReducesEnemyHpOnlyFromTypedEffects() {
		GameSnapshot snapshot = HARNESS.snapshot(scene);
		ResolvedOutcome outcome = HARNESS.resolve(snapshot, "/attack " + HARNESS.alias(snapshot, first.entityId()) + " slash" + WEAPON, 20);

		StateChanges changes = EffectApplier.apply(snapshot, outcome);

		assertThat(changes.enemyHp().get(first.entityId())).isLessThan(first.currentHp()).isNotNegative();
		assertThat(changes.playerHpLost()).isZero();
		assertThat(changes.zone()).isEqualTo(snapshot.location().zoneId());
		assertThat(changes.exitId()).isEmpty();
		assertThat(changes.attackConsumed()).isFalse();
	}

	@Test
	void anEnemyBroughtToZeroIsDefeatedAndHpIsClamped() {
		List<EnemyInstance> enemies = HARNESS.init.enemies().inScene(scene.id()).stream()
				.map(e -> e.entityId().equals(first.entityId()) ? withHp(e, 1) : e).toList();
		GameSnapshot snapshot = HARNESS.snapshot(scene, enemies, Optional.empty(), HARNESS.player.maxHp());
		ResolvedOutcome outcome = HARNESS.resolve(snapshot, "/attack " + HARNESS.alias(snapshot, first.entityId()) + " slash" + WEAPON, 20);

		StateChanges changes = EffectApplier.apply(snapshot, outcome);

		assertThat(changes.enemyHp()).containsEntry(first.entityId(), 0);
		assertThat(changes.defeated()).containsExactly(first.entityId());
	}

	@Test
	void aFailedDefenseCostsHpConsumesTheAttackAndCanKill() {
		GameSnapshot healthy = pendingSnapshot(HARNESS.player.maxHp());
		StateChanges hurt = EffectApplier.apply(healthy, HARNESS.resolve(healthy, "/defend parry", 1));

		assertThat(hurt.playerHpLost()).isPositive();
		assertThat(hurt.attackConsumed()).isTrue();
		assertThat(hurt.playerDown()).isFalse();

		GameSnapshot frail = pendingSnapshot(1);
		StateChanges down = EffectApplier.apply(frail, HARNESS.resolve(frail, "/defend parry", 1));
		assertThat(down.playerHpAfter()).isZero();
		assertThat(down.playerDown()).isTrue();
		assertThat(TerminalRules.after(down.playerDown(), List.of())).isEqualTo(RunStatus.DEAD);
	}

	@Test
	void movingWithinTheSceneChangesTheZone() {
		GameSnapshot snapshot = HARNESS.snapshot(scene);
		PlayerSceneView view = snapshot.view();
		String here = snapshot.location().zoneId();
		String there = view.connections().stream().filter(c -> c.zoneA().equals(here) || c.zoneB().equals(here))
				.map(c -> c.zoneA().equals(here) ? c.zoneB() : c.zoneA()).findFirst().orElseThrow();
		String alias = HARNESS.contexts.interpretation(snapshot).aliases().aliasOf(AliasKind.ZONE, there).orElseThrow();

		StateChanges changes = EffectApplier.apply(snapshot, HARNESS.resolve(snapshot, "/move " + alias));

		assertThat(changes.zone()).isEqualTo(there);
	}

	@Test
	void leavingThroughAnExitIsReportedForTraversal() {
		SceneExit exit = scene.state().exits().getFirst();
		GameSnapshot base = HARNESS.snapshot(scene);
		GameSnapshot atExit = new GameSnapshot(base.session(), base.runSeed(), base.player(),
				new PlayerLocation(scene.id(), exit.zoneId()), scene, base.enemies(), Optional.empty());
		String alias = HARNESS.contexts.interpretation(atExit).aliases().aliasOf(AliasKind.EXIT, exit.id()).orElseThrow();

		StateChanges changes = EffectApplier.apply(atExit, HARNESS.resolve(atExit, "/move " + alias));

		assertThat(changes.exitId()).contains(exit.id());
	}

	// --- Exits, endings, randomness ---

	@Test
	void travelArrivesAtTheDestinationsExitBack() {
		for (SceneInstance origin : HARNESS.init.world().region().scenes()) {
			for (SceneExit exit : origin.state().exits()) {
				SceneInstance destination = sceneById(exit.destinationSceneId());
				PlayerLocation arrival = ExitTraversal.arrival(origin, exit.id(), destination);

				assertThat(arrival.sceneId()).isEqualTo(destination.id());
				assertThat(destination.state().exits()).anyMatch(back -> back.destinationSceneId().equals(origin.id())
						&& back.zoneId().equals(arrival.zoneId()));
			}
		}
	}

	@Test
	void travelRejectsUnknownExitsAndWrongDestinations() {
		SceneExit exit = scene.state().exits().getFirst();

		assertThatIllegalStateException().isThrownBy(() -> ExitTraversal.arrival(scene, "no_such_exit", sceneById(exit.destinationSceneId())));
		assertThatIllegalStateException().isThrownBy(() -> ExitTraversal.arrival(scene, exit.id(), scene));
	}

	@Test
	void deathTakesPrecedenceOverVictory() {
		EnemyInstance guardian = EnemyFixtures.guardian();

		assertThat(TerminalRules.after(false, List.of(guardian))).isEqualTo(RunStatus.VICTORIOUS);
		assertThat(TerminalRules.after(true, List.of(guardian))).isEqualTo(RunStatus.DEAD);
		assertThat(TerminalRules.after(false, List.of(EnemyFixtures.acolyte()))).isEqualTo(RunStatus.ACTIVE);
		assertThat(RunStatus.DEAD.terminal()).isTrue();
		assertThat(RunStatus.ACTIVE.terminal()).isFalse();
	}

	@Test
	void turnStreamsAreFixedPerTurnAndSeparatedByDomain() {
		assertThat(TurnRandom.player(7, 3).nextLong()).isEqualTo(TurnRandom.player(7, 3).nextLong());
		assertThat(TurnRandom.player(7, 3).nextLong()).isNotEqualTo(TurnRandom.player(7, 4).nextLong());
		assertThat(TurnRandom.player(7, 3).nextLong()).isNotEqualTo(TurnRandom.enemy(7, 3).nextLong());
		assertThat(TurnRandom.player(7, 3).nextLong()).isNotEqualTo(TurnRandom.player(8, 3).nextLong());
		assertThat(TurnRandom.character(7).nextLong()).isEqualTo(TurnRandom.character(7).nextLong());
	}

	@Test
	void theViewShowsEveryNonHiddenZoneAndNoHiddenEnemy() {
		GameSnapshot snapshot = HARNESS.snapshot(scene);

		assertThat(snapshot.visibleEnemies()).containsExactlyElementsOf(visible);
		assertThat(snapshot.view().zones()).hasSize((int) scene.state().zones().stream()
				.filter(z -> !scene.state().isHidden(com.leeburke.springgame.world.HiddenContentKind.ZONE, z.id())).count());
	}

	private GameSnapshot pendingSnapshot(int playerHp) {
		return HARNESS.snapshot(scene, HARNESS.init.enemies().inScene(scene.id()),
				Optional.of(HARNESS.pending(scene, first, "attack-1")), playerHp);
	}

	private static GameSnapshot withBody(GameSnapshot s, PlayerBody body) {
		PlayerCharacterState p = s.player();
		return new GameSnapshot(s.session(), s.runSeed(), new PlayerCharacterState(p.name(), p.stats(), p.fated(), p.maxHp(),
				p.currentHp(), body, p.passive(), p.ability(), p.toolBelt()), s.location(), s.scene(), s.enemies(), s.pending());
	}

	private static Optional<RunSession.EnemyCursor> cursor(UUID sceneId, EnemyInstance enemy) {
		return Optional.of(new RunSession.EnemyCursor(sceneId, enemy.entityId()));
	}

	private static SceneInstance sceneById(UUID id) {
		return HARNESS.init.world().region().scene(id)
				.or(() -> Optional.of(HARNESS.init.world().hub()).filter(h -> h.id().equals(id))).orElseThrow();
	}
}
