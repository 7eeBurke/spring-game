package com.leeburke.springgame.game;

import static com.leeburke.springgame.enemy.EnemyFixtures.CONTENT;
import static com.leeburke.springgame.enemy.EnemyFixtures.ENEMIES;
import static com.leeburke.springgame.enemy.EnemyFixtures.WORLD;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;

import com.leeburke.springgame.action.resolution.ActionEngine;
import com.leeburke.springgame.action.resolution.IncomingAttack;
import com.leeburke.springgame.action.resolution.ResolvedOutcome;
import com.leeburke.springgame.action.validation.ValidatedActionIntent;
import com.leeburke.springgame.ai.AiFailureKind;
import com.leeburke.springgame.ai.AiGenerationSettings;
import com.leeburke.springgame.ai.DisabledAiProvider;
import com.leeburke.springgame.ai.interpreter.ActionInterpretationResult;
import com.leeburke.springgame.ai.interpreter.ActionInterpreter;
import com.leeburke.springgame.ai.interpreter.AliasKind;
import com.leeburke.springgame.ai.interpreter.InterpretationContextBuilder;
import com.leeburke.springgame.ai.interpreter.InterpretationSetup;
import com.leeburke.springgame.character.PlayerCharacterGenerator;
import com.leeburke.springgame.character.PlayerCharacterState;
import com.leeburke.springgame.character.PlayerStatGenerator;
import com.leeburke.springgame.enemy.EnemyAttacks;
import com.leeburke.springgame.enemy.EnemyCombatant;
import com.leeburke.springgame.enemy.EnemyInstance;
import com.leeburke.springgame.enemy.EnemyRosterGenerator;
import com.leeburke.springgame.run.initialization.RunInitialization;
import com.leeburke.springgame.run.initialization.RunInitializer;
import com.leeburke.springgame.world.HiddenContentKind;
import com.leeburke.springgame.world.PlayerLocation;
import com.leeburke.springgame.world.SceneInstance;
import com.leeburke.springgame.world.generation.GenerationContextSnapshot;
import com.leeburke.springgame.world.generation.RunWorldGenerator;

/**
 * A real generated run (world, enemies, character) for pure game-layer tests, with slash-command
 * interpretation and Stage 11 resolution. No Spring, no database, no AI.
 */
final class GameHarness {

	static final UUID RUN = UUID.fromString("00000000-0000-0000-0000-00000000c0de");

	final long seed;
	final RunInitialization init;
	final PlayerCharacterState player;
	final ResolutionContextFactory contexts = new ResolutionContextFactory(new InterpretationContextBuilder(WORLD), ENEMIES, CONTENT);
	private final ActionInterpreter commands = new ActionInterpreter(new DisabledAiProvider(AiFailureKind.DISABLED), "unused", 1,
			new AiGenerationSettings("none", 100, Duration.ofSeconds(1), Optional.empty()));

	GameHarness(long seed) {
		this.seed = seed;
		long[] ids = { 0 };
		this.init = new RunInitializer(new RunWorldGenerator(WORLD, () -> new UUID(0, ++ids[0])), new EnemyRosterGenerator(ENEMIES))
				.initialize(RUN, seed, GenerationContextSnapshot.empty());
		this.player = PlayerCharacterState.from(new PlayerCharacterGenerator(CONTENT, new PlayerStatGenerator())
				.generate(TurnRandom.character(seed)));
	}

	/** A region scene with at least two visible enemies. */
	SceneInstance sceneWithEnemies() {
		return init.world().region().scenes().stream()
				.filter(s -> visibleEnemies(s).size() >= 2).findFirst()
				.orElseThrow(() -> new AssertionError("seed " + seed + " has no scene with two visible enemies"));
	}

	List<EnemyInstance> visibleEnemies(SceneInstance scene) {
		return init.enemies().inScene(scene.id()).stream()
				.filter(e -> !scene.state().isHidden(HiddenContentKind.ENTITY, e.entityId()))
				.sorted(java.util.Comparator.comparing(EnemyInstance::entityId)).toList();
	}

	GameSnapshot snapshot(SceneInstance scene, List<EnemyInstance> enemies, Optional<PendingAttack> pending, int playerHp) {
		String zone = scene.state().zones().stream().map(z -> z.id())
				.filter(z -> !scene.state().isHidden(HiddenContentKind.ZONE, z)).findFirst().orElseThrow();
		PlayerCharacterState hurt = new PlayerCharacterState(player.name(), player.stats(), player.fated(), player.maxHp(),
				Math.min(playerHp, player.maxHp()), player.body(), player.passive(), player.ability(), player.toolBelt());
		return new GameSnapshot(new RunSession(RUN, RunStatus.ACTIVE, 0, 0, Optional.empty()), seed, hurt,
				new PlayerLocation(scene.id(), zone), scene, enemies, pending);
	}

	GameSnapshot snapshot(SceneInstance scene) {
		return snapshot(scene, init.enemies().inScene(scene.id()), Optional.empty(), player.maxHp());
	}

	PendingAttack pending(SceneInstance scene, EnemyInstance attacker, String ref) {
		EnemyCombatant combatant = contexts.combatant(attacker);
		IncomingAttack attack = EnemyAttacks.build(ref, combatant, combatant.definition().attacks().getFirst());
		return new PendingAttack(attack, scene.id(), combatant.definition().attacks().getFirst().code(), attacker.weaponCode(), 1,
				"Incoming: something.", Optional.empty());
	}

	String alias(GameSnapshot snapshot, String entityId) {
		return contexts.interpretation(snapshot).aliases().aliasOf(AliasKind.ENTITY, entityId).orElseThrow();
	}

	/** Interprets a slash command against the snapshot; it must be valid. */
	ValidatedActionIntent command(GameSnapshot snapshot, String command) {
		InterpretationSetup setup = contexts.interpretation(snapshot);
		ActionInterpretationResult result = commands.interpret(command, setup);
		if (result instanceof ActionInterpretationResult.Interpreted interpreted) {
			return interpreted.validated();
		}
		throw new AssertionError(command + " was not interpreted: " + result);
	}

	ResolvedOutcome resolve(GameSnapshot snapshot, String command, int... d20) {
		ValidatedActionIntent validated = command(snapshot, command);
		return new ActionEngine().resolve(validated, contexts.resolution(snapshot, validated), new Rolls(d20));
	}

	/** Preset d20 faces; anything else fails. */
	static final class Rolls implements RandomGenerator {
		private final Deque<Integer> faces = new ArrayDeque<>();

		Rolls(int... faces) {
			for (int face : faces) {
				this.faces.add(face);
			}
		}

		@Override
		public int nextInt(int origin, int bound) {
			if (faces.isEmpty()) {
				throw new AssertionError("More rolls drawn than supplied");
			}
			return faces.poll();
		}

		@Override
		public long nextLong() {
			throw new AssertionError("Only d20 rolls are expected");
		}
	}
}
