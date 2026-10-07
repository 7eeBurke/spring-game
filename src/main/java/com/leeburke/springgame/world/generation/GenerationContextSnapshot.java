package com.leeburke.springgame.world.generation;

import java.util.List;
import java.util.Objects;

import com.leeburke.springgame.shared.DefinitionCodes;

/**
 * Immutable history used as input when a run's world is generated, persisted exactly as used.
 * <p>
 * Currently holds only the opening archetypes of recent runs, most recent first, at most
 * {@value #MAX_RECENT_OPENINGS}. Codes need not belong to the region being generated; irrelevant
 * codes simply have no effect. Generation never changes a snapshot.
 */
public record GenerationContextSnapshot(List<String> recentOpeningArchetypeCodes) {

	public static final int MAX_RECENT_OPENINGS = 3;

	public GenerationContextSnapshot {
		recentOpeningArchetypeCodes = List.copyOf(Objects.requireNonNull(recentOpeningArchetypeCodes, "recentOpeningArchetypeCodes"));
		if (recentOpeningArchetypeCodes.size() > MAX_RECENT_OPENINGS) {
			throw new IllegalArgumentException("At most " + MAX_RECENT_OPENINGS + " recent opening archetypes, but got "
					+ recentOpeningArchetypeCodes.size());
		}
		recentOpeningArchetypeCodes.forEach(code -> DefinitionCodes.requireCode(code, "Recent opening archetype code"));
	}

	/** For callers with no history. */
	public static GenerationContextSnapshot empty() {
		return new GenerationContextSnapshot(List.of());
	}
}
