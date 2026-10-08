package com.leeburke.springgame.ai.narration;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;

import com.leeburke.springgame.content.ContentLoadException;
import com.leeburke.springgame.shared.StrictJson;

import tools.jackson.core.JacksonException;

/**
 * The fixed world premise given to the Character Introduction Narrator, authored in
 * {@code content/lore.json} rather than buried in prompt code. Loaded strictly; it is canon, so a
 * narrator may restate it but never extend it into new mechanics.
 */
public final class LoreCatalog {

	public static final String BUNDLED_RESOURCE = "content/lore.json";

	private final List<String> premise;

	public LoreCatalog(List<String> premise) {
		this.premise = List.copyOf(Objects.requireNonNull(premise, "premise"));
		if (this.premise.isEmpty() || this.premise.stream().anyMatch(line -> line == null || line.isBlank())) {
			throw new IllegalArgumentException("Lore premise needs at least one non-blank line");
		}
	}

	public static LoreCatalog loadBundled() {
		return load(BUNDLED_RESOURCE);
	}

	static LoreCatalog load(String resource) {
		try (InputStream in = LoreCatalog.class.getClassLoader().getResourceAsStream(resource)) {
			if (in == null) {
				throw new ContentLoadException("Missing required content resource: " + resource);
			}
			return new LoreCatalog(StrictJson.createMapper().readValue(in, LoreDocument.class).premise());
		} catch (JacksonException e) {
			throw new ContentLoadException("Invalid content in " + resource + ": " + e.getOriginalMessage(), e);
		} catch (IllegalArgumentException e) {
			throw new ContentLoadException("Invalid content in " + resource + ": " + e.getMessage(), e);
		} catch (IOException e) {
			throw new ContentLoadException("Could not read content resource " + resource, e);
		}
	}

	public List<String> premise() {
		return premise;
	}

	record LoreDocument(List<String> premise) {
	}
}
