package com.leeburke.springgame.content.world;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;

import com.leeburke.springgame.content.ContentLoadException;
import com.leeburke.springgame.shared.StrictJson;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

/**
 * Loads authored world-generation content from a classpath directory into a
 * {@link WorldContentCatalog}, with the project's strict JSON rules. Plain class, not a Spring bean.
 */
public final class WorldContentLoader {

	public static final String BUNDLED_DIRECTORY = "content/world";

	static final String ELEMENTS_FILE = "world-elements.json";
	static final String ARCHETYPES_FILE = "scene-archetypes.json";
	static final String REGIONS_FILE = "regions.json";
	static final String FIXED_SCENES_FILE = "fixed-scenes.json";

	/** This loader's own strict mapper. */
	private static final JsonMapper MAPPER = StrictJson.createMapper();

	private final String resourceDirectory;

	public WorldContentLoader(String resourceDirectory) {
		this.resourceDirectory = Objects.requireNonNull(resourceDirectory, "resourceDirectory");
	}

	public static WorldContentCatalog loadBundled() {
		return new WorldContentLoader(BUNDLED_DIRECTORY).load();
	}

	public WorldContentCatalog load() {
		List<WorldElementDefinition> elements = readDefinitions(path(ELEMENTS_FILE), WorldElementDefinition.class);
		List<SceneArchetypeDefinition> archetypes = readDefinitions(path(ARCHETYPES_FILE), SceneArchetypeDefinition.class);
		List<RegionDefinition> regions = readDefinitions(path(REGIONS_FILE), RegionDefinition.class);
		List<FixedSceneDefinition> fixedScenes = readDefinitions(path(FIXED_SCENES_FILE), FixedSceneDefinition.class);
		try {
			return new WorldContentCatalog(elements, archetypes, regions, fixedScenes);
		} catch (IllegalArgumentException | NullPointerException e) {
			throw new ContentLoadException("Invalid world content in " + resourceDirectory + ": " + e.getMessage(), e);
		}
	}

	<T> List<T> readDefinitions(String resourcePath, Class<T> type) {
		JavaType listType = MAPPER.getTypeFactory().constructCollectionType(List.class, type);
		try (InputStream in = WorldContentLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
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
