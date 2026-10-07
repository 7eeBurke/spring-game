package com.leeburke.springgame.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.leeburke.springgame.content.GameContentCatalog;
import com.leeburke.springgame.content.GameContentLoader;
import com.leeburke.springgame.content.world.WorldContentCatalog;
import com.leeburke.springgame.content.world.WorldContentLoader;

/**
 * Exposes the bundled static content as a Spring bean. The content classes themselves stay plain
 * Java; this is the only place Spring touches them.
 */
@Configuration(proxyBeanMethods = false)
public class ContentConfiguration {

	@Bean
	GameContentCatalog gameContentCatalog() {
		return GameContentLoader.loadBundled();
	}

	@Bean
	WorldContentCatalog worldContentCatalog() {
		return WorldContentLoader.loadBundled();
	}
}
