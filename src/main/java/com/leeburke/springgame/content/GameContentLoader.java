package com.leeburke.springgame.content;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;

import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

/**
 * Loads the static content JSON files from a classpath directory into a {@link GameContentCatalog}.
 * <p>
 * Deliberately a plain class, not a Spring bean: nothing needs it injected yet, and it uses its own
 * strict mapper rather than Spring Boot's web-configured one. Unknown or missing fields, unknown
 * enum values, string-to-number and number-to-string coercion, duplicate keys and trailing content
 * all fail loading.
 */
public final class GameContentLoader {

	public static final String BUNDLED_DIRECTORY = "content";

	static final String WEAPONS_FILE = "weapons.json";
	static final String PASSIVES_FILE = "passives.json";
	static final String ABILITIES_FILE = "abilities.json";
	static final String ITEMS_FILE = "items.json";
	static final String NAMES_FILE = "character-names.json";

	private static final JsonMapper MAPPER = JsonMapper.builder()
			.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
			.enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
			.enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
			.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
			.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
			.disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
			.disable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
			.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
			// Text values (such as character names) must be JSON strings, not numbers or booleans.
			.withCoercionConfig(LogicalType.Textual, config -> config
					.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
					.setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
					.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
			.build();

	private final String resourceDirectory;

	public GameContentLoader(String resourceDirectory) {
		this.resourceDirectory = Objects.requireNonNull(resourceDirectory, "resourceDirectory");
	}

	public static GameContentCatalog loadBundled() {
		return new GameContentLoader(BUNDLED_DIRECTORY).load();
	}

	public GameContentCatalog load() {
		List<WeaponDefinition> weapons = readDefinitions(path(WEAPONS_FILE), WeaponDefinition.class);
		List<PassiveDefinition> passives = readDefinitions(path(PASSIVES_FILE), PassiveDefinition.class);
		List<AbilityDefinition> abilities = readDefinitions(path(ABILITIES_FILE), AbilityDefinition.class);
		List<ItemDefinition> items = readDefinitions(path(ITEMS_FILE), ItemDefinition.class);
		List<String> characterNames = readDefinitions(path(NAMES_FILE), String.class);
		try {
			return new GameContentCatalog(weapons, passives, abilities, items, characterNames);
		} catch (IllegalArgumentException | NullPointerException e) {
			throw new ContentLoadException("Invalid content in " + resourceDirectory + ": " + e.getMessage(), e);
		}
	}

	<T> List<T> readDefinitions(String resourcePath, Class<T> type) {
		JavaType listType = MAPPER.getTypeFactory().constructCollectionType(List.class, type);
		try (InputStream in = GameContentLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
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
