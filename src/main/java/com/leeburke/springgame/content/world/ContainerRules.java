package com.leeburke.springgame.content.world;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.leeburke.springgame.shared.DefinitionCodes;

/**
 * Which placed objects are containers, and what they may hold: at most one item each, drawn when a
 * run is created from a tiny authored list of existing items (not a loot system). The first find
 * is the one container every new run's first Hollow Chapel scene is guaranteed, never empty.
 */
public record ContainerRules(Map<String, ContainerDefinition> containers, Optional<FirstFind> firstFind) {

	public static final ContainerRules NONE = new ContainerRules(Map.of(), Optional.empty());

	public ContainerRules {
		Map<String, ContainerDefinition> known = Map.copyOf(Objects.requireNonNull(containers, "containers"));
		containers = known;
		Objects.requireNonNull(firstFind, "firstFind");
		firstFind.ifPresent(find -> {
			if (!known.containsKey(find.object())) {
				throw new IllegalArgumentException("The first find " + find.object() + " is not a container");
			}
		});
	}

	public Optional<ContainerDefinition> container(String objectCode) {
		return Optional.ofNullable(containers.get(objectCode));
	}

	public boolean isContainer(String objectCode) {
		return containers.containsKey(objectCode);
	}

	/**
	 * @param chanceEmpty percentage chance (0 to 100) that a placed container holds nothing
	 * @param candidates  item codes it may hold, drawn uniformly
	 */
	public record ContainerDefinition(String object, int chanceEmpty, List<String> candidates) {
		public ContainerDefinition {
			DefinitionCodes.requireCode(object, "Container object");
			if (chanceEmpty < 0 || chanceEmpty > 100) {
				throw new IllegalArgumentException("Container " + object + " chanceEmpty must be 0 to 100");
			}
			candidates = WorldText.uniqueList(candidates, "candidates of container " + object, true);
			candidates.forEach(code -> DefinitionCodes.requireCode(code, "Container candidate"));
		}
	}

	/** The guaranteed container of a new run's first scene in the region, and the items it may hold (never empty). */
	public record FirstFind(String object, List<String> candidates) {
		public FirstFind {
			DefinitionCodes.requireCode(object, "First find object");
			candidates = WorldText.uniqueList(candidates, "first find candidates", true);
			candidates.forEach(code -> DefinitionCodes.requireCode(code, "First find candidate"));
		}
	}

	/** The stored document shape. */
	public record Document(List<ContainerDefinition> containers, FirstFind firstFind) {
		public ContainerRules toRules() {
			Map<String, ContainerDefinition> byObject = new LinkedHashMap<>();
			for (ContainerDefinition container : containers) {
				if (byObject.put(container.object(), container) != null) {
					throw new IllegalArgumentException("Duplicate container " + container.object());
				}
			}
			return new ContainerRules(byObject, Optional.ofNullable(firstFind));
		}
	}

	/** Every item code the rules can place. */
	public List<String> itemCodes() {
		java.util.Set<String> codes = new java.util.LinkedHashSet<>();
		containers.values().forEach(c -> codes.addAll(c.candidates()));
		firstFind.ifPresent(f -> codes.addAll(f.candidates()));
		return List.copyOf(codes);
	}
}
