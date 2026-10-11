package com.leeburke.springgame.content.world;

import java.util.List;
import java.util.Objects;

import com.leeburke.springgame.shared.DefinitionCodes;

/**
 * Authored description of a procedurally generated region.
 * <p>
 * Counts: {@code requiredScenes} normal scenes on the route (entry and pre-boss included, boss
 * excluded), {@code optionalScenes} dead-end side scenes, and {@code branches} route forks (a stage
 * of two alternative scenes). A route with B forks needs at least 2 + 2B required scenes, so both
 * ends of the branch range must be attainable.
 * <p>
 * {@code openingArchetypes} are the normal archetypes that may be the region's first scene: the
 * places that can plausibly lie just behind the region's exterior entrance (the Hollow Chapel's
 * west doors open into a nave or under a bell tower, not into a sacristy).
 */
public record RegionDefinition(
		String code,
		String displayName,
		List<String> normalArchetypes,
		List<String> openingArchetypes,
		String bossArchetype,
		String bossEntity,
		IntRange requiredScenes,
		IntRange optionalScenes,
		IntRange branches) {

	/** A region any of whose normal archetypes may open it. */
	public RegionDefinition(String code, String displayName, List<String> normalArchetypes, String bossArchetype, String bossEntity,
			IntRange requiredScenes, IntRange optionalScenes, IntRange branches) {
		this(code, displayName, normalArchetypes, normalArchetypes, bossArchetype, bossEntity, requiredScenes, optionalScenes, branches);
	}

	/** Within-region assignment excludes up to two neighbour archetypes, so at least three are needed. */
	public static final int MIN_NORMAL_ARCHETYPES = 3;

	public RegionDefinition {
		DefinitionCodes.requireCode(code, "Region code");
		WorldText.requireDisplayName(displayName, code);
		normalArchetypes = WorldText.uniqueList(normalArchetypes, "normal archetypes of region " + code, true);
		normalArchetypes.forEach(a -> DefinitionCodes.requireCode(a, "Region normal archetype"));
		openingArchetypes = WorldText.uniqueList(openingArchetypes, "opening archetypes of region " + code, true);
		for (String opening : openingArchetypes) {
			if (!normalArchetypes.contains(opening)) {
				throw new IllegalArgumentException("Region " + code + " opening archetype " + opening + " is not one of its normal archetypes");
			}
		}
		DefinitionCodes.requireCode(bossArchetype, "Region boss archetype");
		DefinitionCodes.requireCode(bossEntity, "Region boss entity");
		Objects.requireNonNull(requiredScenes, "requiredScenes");
		Objects.requireNonNull(optionalScenes, "optionalScenes");
		Objects.requireNonNull(branches, "branches");
		if (normalArchetypes.size() < MIN_NORMAL_ARCHETYPES) {
			throw new IllegalArgumentException("Region " + code + " needs at least " + MIN_NORMAL_ARCHETYPES + " normal archetypes");
		}
		if (normalArchetypes.contains(bossArchetype)) {
			throw new IllegalArgumentException("Region " + code + " lists its boss archetype as a normal archetype");
		}
		if (requiredScenes.min() < minimumRequiredFor(branches.min())
				|| requiredScenes.max() < minimumRequiredFor(branches.max())) {
			throw new IllegalArgumentException("Region " + code + " required scene range " + requiredScenes
					+ " cannot accommodate branch range " + branches);
		}
	}

	/** Entry + pre-boss + two scenes per fork. */
	public static int minimumRequiredFor(int branchCount) {
		return 2 + 2 * branchCount;
	}
}
