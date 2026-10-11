package com.leeburke.springgame.ai.narration;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;

import com.leeburke.springgame.content.ContentLoadException;
import com.leeburke.springgame.shared.StrictJson;

import tools.jackson.core.JacksonException;

/**
 * The fixed world premise given to the Character Introduction Narrator, and the player's opening
 * direction (the objective), authored in {@code content/lore.json} rather than buried in prompt
 * code. Loaded strictly; it is canon, so a narrator may restate it but never extend it into new
 * mechanics. The objective is a narrative direction, not a quest tracker: it is true to the
 * mechanics (the run is won when the Chapel Guardian falls) without dictating any route.
 */
public final class LoreCatalog {

	public static final String BUNDLED_RESOURCE = "content/lore.json";

	static final String DEFAULT_OBJECTIVE = "Follow Chapel Road to the Hollow Chapel, and discover what guards its depths.";
	static final String DEFAULT_OBJECTIVE_INSIDE = "Find a way deeper into the Hollow Chapel, and discover what guards its depths.";

	private final List<String> premise;
	private final String objective;
	private final String objectiveInside;

	public LoreCatalog(List<String> premise) {
		this(premise, DEFAULT_OBJECTIVE);
	}

	public LoreCatalog(List<String> premise, String objective) {
		this(premise, objective, DEFAULT_OBJECTIVE_INSIDE);
	}

	public LoreCatalog(List<String> premise, String objective, String objectiveInside) {
		this.premise = List.copyOf(Objects.requireNonNull(premise, "premise"));
		if (this.premise.isEmpty() || this.premise.stream().anyMatch(line -> line == null || line.isBlank())) {
			throw new IllegalArgumentException("Lore premise needs at least one non-blank line");
		}
		this.objective = Objects.requireNonNull(objective, "objective");
		if (objective.isBlank()) {
			throw new IllegalArgumentException("The lore objective must not be blank");
		}
		this.objectiveInside = Objects.requireNonNull(objectiveInside, "objectiveInside");
		if (objectiveInside.isBlank()) {
			throw new IllegalArgumentException("The lore objective inside the region must not be blank");
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
			LoreDocument document = StrictJson.createMapper().readValue(in, LoreDocument.class);
			return new LoreCatalog(document.premise(), document.objective(), document.objectiveInside());
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

	/** The player's opening direction, shown with the introduction and in the scene panel. */
	public String objective() {
		return objective;
	}

	/** The direction once the player is inside the region: the road is behind them, the depths ahead. */
	public String objectiveInside() {
		return objectiveInside;
	}

	record LoreDocument(List<String> premise, String objective, String objectiveInside) {
	}
}
