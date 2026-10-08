package com.leeburke.springgame.config;

import java.security.SecureRandom;
import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.leeburke.springgame.action.resolution.ActionEngine;
import com.leeburke.springgame.ai.interpreter.InterpretationContextBuilder;
import com.leeburke.springgame.character.PlayerCharacterGenerator;
import com.leeburke.springgame.character.PlayerStatGenerator;
import com.leeburke.springgame.content.GameContentCatalog;
import com.leeburke.springgame.content.enemy.EnemyCatalog;
import com.leeburke.springgame.content.world.WorldContentCatalog;
import com.leeburke.springgame.enemy.EnemyRosterGenerator;
import com.leeburke.springgame.enemy.behavior.EnemyBehavior;
import com.leeburke.springgame.game.ResolutionContextFactory;
import com.leeburke.springgame.run.initialization.RunInitializer;
import com.leeburke.springgame.world.generation.IdSource;
import com.leeburke.springgame.world.generation.RunWorldGenerator;

/**
 * Exposes the pure-Java game engine (generators, the Stage 11 engine, enemy behaviour) as beans for
 * the application layer. The classes stay framework-free; Spring only constructs them here, which
 * keeps them trivially unit-testable with {@code new}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GameApiProperties.class)
public class GameConfiguration {

	/** Wall-clock time for leases, limits and timestamps; tests replace it with a fixed clock. */
	@Bean
	@ConditionalOnMissingBean
	Clock clock() {
		return Clock.systemUTC();
	}

	/** Run seeds come from a CSPRNG and are never exposed, so runs cannot be predicted. */
	@Bean
	SecureRandom runSeedSource() {
		return new SecureRandom();
	}

	@Bean
	PlayerCharacterGenerator playerCharacterGenerator(GameContentCatalog content) {
		return new PlayerCharacterGenerator(content, new PlayerStatGenerator());
	}

	@Bean
	RunInitializer runInitializer(WorldContentCatalog world, EnemyCatalog enemies) {
		return new RunInitializer(new RunWorldGenerator(world, IdSource.random()), new EnemyRosterGenerator(enemies));
	}

	@Bean
	ActionEngine actionEngine() {
		return new ActionEngine();
	}

	@Bean
	EnemyBehavior enemyBehavior() {
		return new EnemyBehavior();
	}

	@Bean
	ResolutionContextFactory resolutionContextFactory(InterpretationContextBuilder interpretation, EnemyCatalog enemies,
			GameContentCatalog content) {
		return new ResolutionContextFactory(interpretation, enemies, content);
	}
}
