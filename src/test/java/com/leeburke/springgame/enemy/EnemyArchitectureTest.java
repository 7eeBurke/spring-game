package com.leeburke.springgame.enemy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.leeburke.springgame.enemy.behavior.EnemyBehavior;
import com.leeburke.springgame.enemy.behavior.EnemyDecisionContext;
import com.leeburke.springgame.world.view.PlayerSceneView;

/** Enemy code stays pure Java, and enemy decisions cannot see the player's hidden state. */
class EnemyArchitectureTest {

	private static final List<Path> SOURCES = List.of(
			Path.of("src/main/java/com/leeburke/springgame/enemy"),
			Path.of("src/main/java/com/leeburke/springgame/enemy/behavior"),
			Path.of("src/main/java/com/leeburke/springgame/content/enemy"),
			Path.of("src/main/java/com/leeburke/springgame/run/initialization"));

	private static final List<String> FORBIDDEN = List.of("org.springframework", "jakarta.persistence", "org.hibernate",
			"springgame.persistence", "springgame.ai", "java.util.Random;", "new Random", "Math.random", "ThreadLocalRandom",
			"UUID.randomUUID", "character.Fated");

	@Test
	void enemySourcesUseNoFrameworkPersistenceAiFatedOrHiddenRandomness() throws IOException {
		int files = 0;
		for (Path directory : SOURCES) {
			try (Stream<Path> list = Files.list(directory)) {
				for (Path file : list.filter(p -> p.toString().endsWith(".java")).toList()) {
					files++;
					String source = Files.readString(file);
					for (String forbidden : FORBIDDEN) {
						assertThat(source).as(file + " must not use " + forbidden).doesNotContain(forbidden);
					}
				}
			}
		}
		assertThat(files).isGreaterThan(10);
	}

	@Test
	void decisionContextReachesNoPlayerOrValidationType() {
		Set<Class<?>> reachable = new HashSet<>();
		collect(EnemyDecisionContext.class, reachable);

		assertThat(reachable).noneMatch(type -> type.getPackageName().equals("com.leeburke.springgame.character"));
		assertThat(reachable).noneMatch(type -> type.getPackageName().startsWith("com.leeburke.springgame.action.validation"));
		assertThat(reachable).noneMatch(type -> type.getPackageName().startsWith("com.leeburke.springgame.world"));
		assertThat(reachable).extracting(Class::getSimpleName)
				.doesNotContain("PlayerCharacterState", "PlayerBody", "ToolBelt", "PassiveDefinition", "AbilityDefinition",
						"ItemDefinition", "Fated");
	}

	@Test
	void behaviourTakesOnlyTheDecisionContextAndARandomSource() {
		for (Method method : EnemyBehavior.class.getDeclaredMethods()) {
			if (Modifier.isPublic(method.getModifiers())) {
				assertThat(method.getParameterTypes()).as(method.getName())
						.isSubsetOf(EnemyDecisionContext.class, java.util.random.RandomGenerator.class);
			}
		}
	}

	@Test
	void playerSceneViewStillShowsOnlyEntityIdentity() {
		assertThat(Arrays.stream(PlayerSceneView.VisibleEntity.class.getRecordComponents()).map(RecordComponent::getName))
				.containsExactly("id", "definitionCode", "zoneId");
	}

	/** Every type reachable through record components and generic arguments. */
	private static void collect(Type type, Set<Class<?>> seen) {
		if (type instanceof ParameterizedType parameterized) {
			collect(parameterized.getRawType(), seen);
			for (Type argument : parameterized.getActualTypeArguments()) {
				collect(argument, seen);
			}
			return;
		}
		if (!(type instanceof Class<?> clazz) || !seen.add(clazz)) {
			return;
		}
		if (clazz.isRecord()) {
			for (RecordComponent component : clazz.getRecordComponents()) {
				collect(component.getGenericType(), seen);
			}
		}
		if (clazz.isSealed()) {
			for (Class<?> permitted : clazz.getPermittedSubclasses()) {
				collect(permitted, seen);
			}
		}
	}
}
