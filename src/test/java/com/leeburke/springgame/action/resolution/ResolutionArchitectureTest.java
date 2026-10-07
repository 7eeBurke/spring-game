package com.leeburke.springgame.action.resolution;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.leeburke.springgame.action.validation.ValidatedActionIntent;
import com.leeburke.springgame.mechanics.ContactQuality;
import com.leeburke.springgame.mechanics.DamageResult;
import com.leeburke.springgame.mechanics.DegreeOfSuccess;

/** Resolution stays pure Java, takes no precomputed mechanics, and accepts only validated intents. */
class ResolutionArchitectureTest {

	private static final Path SOURCES = Path.of("src/main/java/com/leeburke/springgame/action/resolution");

	private static final List<String> FORBIDDEN = List.of("org.springframework", "jakarta.persistence", "org.hibernate",
			"springgame.persistence", "springgame.ai", "java.util.Random;", "new Random", "Math.random", "ThreadLocalRandom");

	@Test
	void resolutionSourcesUseNoFrameworkPersistenceAiOrHiddenRandomness() throws IOException {
		List<Path> files;
		try (Stream<Path> walk = Files.list(SOURCES)) {
			files = walk.filter(p -> p.toString().endsWith(".java")).toList();
		}
		assertThat(files).isNotEmpty();
		for (Path file : files) {
			String source = Files.readString(file);
			for (String forbidden : FORBIDDEN) {
				assertThat(source).as(file + " must not use " + forbidden).doesNotContain(forbidden);
			}
		}
	}

	@Test
	void engineTakesNoPrecomputedMechanics() {
		Method resolve = Arrays.stream(ActionEngine.class.getDeclaredMethods())
				.filter(m -> m.getName().equals("resolve") && Modifier.isPublic(m.getModifiers()))
				.findFirst().orElseThrow();

		assertThat(resolve.getParameterTypes()).containsExactly(ValidatedActionIntent.class, ActionResolutionContext.class,
				java.util.random.RandomGenerator.class);
		List<Class<?>> contextTypes = Arrays.stream(ActionResolutionContext.class.getRecordComponents())
				.<Class<?>>map(c -> c.getType()).toList();
		assertThat(contextTypes).doesNotContainAnyElementsOf(List.of(DegreeOfSuccess.class, ContactQuality.class, DamageResult.class));
	}

	@Test
	void validatedIntentCannotBeConstructedOutsideValidation() {
		assertThat(ValidatedActionIntent.class.getConstructors()).isEmpty();
		assertThat(Modifier.isFinal(ValidatedActionIntent.class.getModifiers())).isTrue();
	}

	@Test
	void onlyTheEngineIsPublicAmongTheResolvers() {
		for (Class<?> resolver : List.of(AttackResolver.class, DefenseResolver.class, MovementResolver.class,
				ResolutionCoherence.class)) {
			assertThat(Modifier.isPublic(resolver.getModifiers())).as(resolver.getSimpleName()).isFalse();
		}
	}
}
