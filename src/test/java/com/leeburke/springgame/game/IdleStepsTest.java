package com.leeburke.springgame.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.leeburke.springgame.action.resolution.ResolvedOutcome;
import com.leeburke.springgame.action.resolution.StepStatus;
import com.leeburke.springgame.action.validation.ValidatedActionIntent;
import com.leeburke.springgame.world.HiddenContentKind;
import com.leeburke.springgame.world.SceneInstance;
import com.leeburke.springgame.world.SceneZone;

/** Which resolved steps did nothing: only a move to the zone the player is already in. */
class IdleStepsTest {

	private static final GameHarness HARNESS = IntStream.rangeClosed(1, 60).mapToObj(GameHarness::new)
			.filter(h -> h.init.world().region().scenes().stream().anyMatch(s -> h.visibleEnemies(s).size() >= 2))
			.findFirst().orElseThrow();

	private final SceneInstance scene = HARNESS.sceneWithEnemies();
	private final GameSnapshot snapshot = HARNESS.snapshot(scene);
	private final String here = HARNESS.zoneAlias(snapshot, snapshot.location().zoneId());

	private Set<String> idle(String command) {
		ValidatedActionIntent validated = HARNESS.command(snapshot, command);
		ResolvedOutcome outcome = HARNESS.resolve(snapshot, command, 10, 10, 10, 10);
		return IdleSteps.of(outcome, validated.intent());
	}

	@Test
	void aMoveToWhereThePlayerAlreadyIsIsIdle() {
		ResolvedOutcome outcome = HARNESS.resolve(snapshot, "/move " + here);

		assertThat(outcome.steps().getFirst().status()).isEqualTo(StepStatus.RESOLVED); // still a success for resolution
		assertThat(idle("/move " + here)).containsExactly(outcome.steps().getFirst().stepId());
	}

	@Test
	void holdingPositionAndLookingAreNotIdle() {
		assertThat(idle("/hold")).isEmpty();
		assertThat(idle("/search")).isEmpty();
	}

	@Test
	void movesElsewhereAreNotIdleWhetherTheySucceedOrAreBlocked() {
		List<String> others = scene.state().zones().stream().map(SceneZone::id)
				.filter(z -> !scene.state().isHidden(HiddenContentKind.ZONE, z))
				.filter(z -> !z.equals(snapshot.location().zoneId()))
				.map(z -> HARNESS.zoneAlias(snapshot, z)).toList();
		assertThat(others).isNotEmpty();
		for (String other : others) {
			assertThat(idle("/move " + other)).as(other).isEmpty();
		}
		assertThat(idle("/move exit_1")).isEmpty();
	}

	@Test
	void onlyTheIdleStepOfAMixedTurnIsIdle() {
		Set<String> idle = idle("/move " + here + " ; /search");

		assertThat(idle).containsExactly("s1");
	}

	@Test
	void anIdleStepNeverProvokesAnEnemyButAMeaningfulOneStillDoes() {
		String target = HARNESS.alias(snapshot, HARNESS.visibleEnemies(scene).getFirst().entityId());
		String stayAndLook = "/move " + here + " ; /search";
		String stayAndStrike = "/move " + here + " ; /attack " + target + " slash with weapon_1";

		ResolvedOutcome looked = HARNESS.resolve(snapshot, stayAndLook);
		ResolvedOutcome struck = HARNESS.resolve(snapshot, stayAndStrike, 10);

		assertThat(EncounterRules.enemyPhaseRuns(looked, idle(stayAndLook), RunStatus.ACTIVE, false, false)).isFalse();
		assertThat(EncounterRules.enemyPhaseRuns(struck, IdleSteps.of(struck, HARNESS.command(snapshot, stayAndStrike).intent()),
				RunStatus.ACTIVE, false, false)).isTrue();
	}
}
