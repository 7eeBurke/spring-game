package com.leeburke.springgame.content.enemy;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;

import com.leeburke.springgame.content.ContentLoadException;
import com.leeburke.springgame.content.GameContentCatalog;
import com.leeburke.springgame.content.world.WorldContentCatalog;
import com.leeburke.springgame.shared.StrictJson;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

/**
 * Loads enemy anatomies and definitions from a classpath directory into an {@link EnemyCatalog},
 * with the project's strict JSON rules and its own mapper. Needs the weapon and world catalogues to
 * check cross-references. Plain class, not a Spring bean.
 */
public final class EnemyContentLoader {

	public static final String BUNDLED_DIRECTORY = "content/enemy";

	static final String ANATOMIES_FILE = "anatomies.json";
	static final String ENEMIES_FILE = "enemies.json";

	private static final JsonMapper MAPPER = StrictJson.createMapper();

	private final String resourceDirectory;

	public EnemyContentLoader(String resourceDirectory) {
		this.resourceDirectory = Objects.requireNonNull(resourceDirectory, "resourceDirectory");
	}

	public static EnemyCatalog loadBundled(GameContentCatalog content, WorldContentCatalog worldContent) {
		return new EnemyContentLoader(BUNDLED_DIRECTORY).load(content, worldContent);
	}

	public EnemyCatalog load(GameContentCatalog content, WorldContentCatalog worldContent) {
		List<AnatomyDefinition> anatomies = readDefinitions(path(ANATOMIES_FILE), AnatomyDefinition.class);
		List<EnemyDefinition> enemies = readDefinitions(path(ENEMIES_FILE), EnemyDefinition.class);
		try {
			return new EnemyCatalog(anatomies, enemies, content, worldContent);
		} catch (IllegalArgumentException | NullPointerException e) {
			throw new ContentLoadException("Invalid enemy content in " + resourceDirectory + ": " + e.getMessage(), e);
		}
	}

	<T> List<T> readDefinitions(String resourcePath, Class<T> type) {
		JavaType listType = MAPPER.getTypeFactory().constructCollectionType(List.class, type);
		try (InputStream in = EnemyContentLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
			if (in == null) {
				throw new ContentLoadException("Missing required content resource: " + resourcePath);
			}
			return MAPPER.readValue(in, listType);
		} catch (JacksonException e) {
			throw new ContentLoadException("Invalid content in " + resourcePath + ": " + e.getOriginalMessage(), e);
		} catch (IOException e) {
			throw new ContentLoadException("Could not read content resource " + resourcePath, e);
		}
	}

	private String path(String fileName) {
		return resourceDirectory + "/" + fileName;
	}
}
