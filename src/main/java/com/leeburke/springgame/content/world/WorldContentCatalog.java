package com.leeburke.springgame.content.world;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Immutable catalogue of authored world-generation content, indexed by code, in authored order.
 * <p>
 * Construction checks every cross-reference: slot candidates exist and have the slot's kind,
 * regions name existing archetypes and a valid boss setup, and fixed scenes lead to an existing
 * region. Any number of regions is allowed.
 */
public final class WorldContentCatalog {

	private final Map<String, WorldElementDefinition> elements;
	private final Map<String, SceneArchetypeDefinition> archetypes;
	private final Map<String, RegionDefinition> regions;
	private final Map<String, FixedSceneDefinition> fixedScenes;
	private final PlaceTexts texts;
	private final ContainerRules containers;

	/** Structure only: no place texts or containers (for tests of the structure). */
	public WorldContentCatalog(
			Collection<WorldElementDefinition> elements,
			Collection<SceneArchetypeDefinition> archetypes,
			Collection<RegionDefinition> regions,
			Collection<FixedSceneDefinition> fixedScenes) {
		this(elements, archetypes, regions, fixedScenes, PlaceTexts.NONE, ContainerRules.NONE);
	}

	/**
	 * @param texts      when not {@link PlaceTexts#NONE}, must describe every scene, zone, connection, exit zone,
	 *                   object and hazard
	 * @param containers objects that are containers; each must be a known OBJECT
	 */
	public WorldContentCatalog(
			Collection<WorldElementDefinition> elements,
			Collection<SceneArchetypeDefinition> archetypes,
			Collection<RegionDefinition> regions,
			Collection<FixedSceneDefinition> fixedScenes,
			PlaceTexts texts,
			ContainerRules containers) {
		this.elements = index("world element", elements, WorldElementDefinition::code);
		this.archetypes = index("archetype", archetypes, SceneArchetypeDefinition::code);
		this.regions = index("region", regions, RegionDefinition::code);
		this.fixedScenes = index("fixed scene", fixedScenes, FixedSceneDefinition::code);
		this.texts = Objects.requireNonNull(texts, "texts");
		this.containers = Objects.requireNonNull(containers, "containers");
		this.archetypes.values().forEach(this::checkArchetypeReferences);
		this.regions.values().forEach(this::checkRegionReferences);
		this.fixedScenes.values().forEach(this::checkFixedSceneReferences);
		if (texts != PlaceTexts.NONE) {
			checkTexts();
		}
		containers.containers().keySet().forEach(code -> {
			WorldElementDefinition element = this.elements.get(code);
			if (element == null || element.kind() != WorldElementKind.OBJECT) {
				throw new IllegalArgumentException("Container " + code + " is not a known OBJECT");
			}
		});
	}

	/** Authored physical descriptions (scene, zone, passage, exit, object, hazard). */
	public PlaceTexts texts() {
		return texts;
	}

	/** Which objects are containers and what they may hold. */
	public ContainerRules containers() {
		return containers;
	}

	/** Every scene, zone, connection, exit zone, object and hazard has its text, so nothing is shown as a bare label. */
	/**
	 * The most ways out one exit zone of a generated scene can hold (an archetype with a single exit
	 * zone, such as the sacristy, may hold every link of a branching route scene and an optional
	 * scene), so each needs that many distinct passages.
	 */
	public static final int PASSAGES_PER_EXIT_ZONE = 4;

	private void checkTexts() {
		for (SceneArchetypeDefinition archetype : archetypes.values()) {
			checkSceneTexts(archetype.code(), archetype.zones(), archetype.connections(), archetype.exitZones());
			archetype.exitZones().forEach(zone -> {
				if (texts.scene(archetype.code()).orElseThrow().exits().get(zone).size() < PASSAGES_PER_EXIT_ZONE) {
					throw new IllegalArgumentException("Scene " + archetype.code() + " exit zone " + zone + " needs "
							+ PASSAGES_PER_EXIT_ZONE + " distinct passages");
				}
			});
		}
		for (RegionDefinition region : regions.values()) {
			for (String opening : region.openingArchetypes()) {
				if (texts.entrance(opening).isEmpty()) {
					throw new IllegalArgumentException("Opening archetype " + opening + " of region " + region.code()
							+ " has no entrance text (how the region's doors look from inside)");
				}
			}
		}
		for (FixedSceneDefinition fixed : fixedScenes.values()) {
			checkSceneTexts(fixed.code(), fixed.zones(), fixed.connections(), List.of(fixed.exit().zoneId()));
		}
		for (WorldElementDefinition element : elements.values()) {
			if ((element.kind() == WorldElementKind.OBJECT || element.kind() == WorldElementKind.HAZARD)
					&& texts.element(element.code()).isEmpty()) {
				throw new IllegalArgumentException("No description for " + element.code());
			}
		}
	}

	private void checkSceneTexts(String code, List<com.leeburke.springgame.world.SceneZone> zones,
			List<com.leeburke.springgame.world.ZoneConnection> connections, List<String> exitZones) {
		PlaceTexts.SceneTexts scene = texts.scene(code)
				.orElseThrow(() -> new IllegalArgumentException("No place texts for scene " + code));
		zones.forEach(zone -> {
			if (!scene.zones().containsKey(zone.id())) {
				throw new IllegalArgumentException("Scene " + code + " zone " + zone.id() + " has no phrase or description");
			}
		});
		connections.forEach(connection -> {
			if (!scene.connections().containsKey(connection.id())) {
				throw new IllegalArgumentException("Scene " + code + " connection " + connection.id() + " has no passage text");
			}
		});
		exitZones.forEach(zone -> {
			if (!scene.exits().containsKey(zone)) {
				throw new IllegalArgumentException("Scene " + code + " exit zone " + zone + " has no exit text");
			}
		});
	}

	public List<WorldElementDefinition> elements() {
		return List.copyOf(elements.values());
	}

	public Optional<WorldElementDefinition> findElement(String code) {
		return find(elements, code);
	}

	public List<SceneArchetypeDefinition> archetypes() {
		return List.copyOf(archetypes.values());
	}

	public Optional<SceneArchetypeDefinition> findArchetype(String code) {
		return find(archetypes, code);
	}

	public List<RegionDefinition> regions() {
		return List.copyOf(regions.values());
	}

	public Optional<RegionDefinition> findRegion(String code) {
		return find(regions, code);
	}

	public List<FixedSceneDefinition> fixedScenes() {
		return List.copyOf(fixedScenes.values());
	}

	public Optional<FixedSceneDefinition> findFixedScene(String code) {
		return find(fixedScenes, code);
	}

	private void checkArchetypeReferences(SceneArchetypeDefinition archetype) {
		for (ContentSlot slot : archetype.slots()) {
			for (String candidate : slot.candidates()) {
				WorldElementDefinition element = elements.get(candidate);
				if (element == null) {
					throw new IllegalArgumentException("Archetype " + archetype.code() + " slot " + slot.id()
							+ " references unknown world element " + candidate);
				}
				if (element.kind() != slot.kind()) {
					throw new IllegalArgumentException("Archetype " + archetype.code() + " slot " + slot.id() + " is "
							+ slot.kind() + " but candidate " + candidate + " is " + element.kind());
				}
			}
		}
	}

	private void checkRegionReferences(RegionDefinition region) {
		for (String code : region.normalArchetypes()) {
			SceneArchetypeDefinition archetype = requireArchetype(region, code);
			boolean usesBossEntity = archetype.slots().stream().anyMatch(slot -> slot.candidates().contains(region.bossEntity()));
			if (usesBossEntity) {
				throw new IllegalArgumentException("Region " + region.code() + " normal archetype " + code
						+ " may place the boss entity " + region.bossEntity());
			}
		}
		SceneArchetypeDefinition boss = requireArchetype(region, region.bossArchetype());
		WorldElementDefinition bossEntity = elements.get(region.bossEntity());
		if (bossEntity == null || bossEntity.kind() != WorldElementKind.ENTITY) {
			throw new IllegalArgumentException("Region " + region.code() + " boss entity " + region.bossEntity()
					+ " is not a known ENTITY");
		}
		boolean guaranteedVisibleBoss = boss.slots().stream().anyMatch(slot -> slot.kind() == WorldElementKind.ENTITY
				&& slot.candidates().equals(List.of(region.bossEntity())) && slot.chance() == 100 && slot.hiddenChance() == 0);
		if (!guaranteedVisibleBoss) {
			throw new IllegalArgumentException("Region " + region.code() + " boss archetype " + boss.code()
					+ " must have a slot that always places a visible " + region.bossEntity());
		}
	}

	private SceneArchetypeDefinition requireArchetype(RegionDefinition region, String code) {
		SceneArchetypeDefinition archetype = archetypes.get(code);
		if (archetype == null) {
			throw new IllegalArgumentException("Region " + region.code() + " references unknown archetype " + code);
		}
		return archetype;
	}

	private void checkFixedSceneReferences(FixedSceneDefinition scene) {
		if (!regions.containsKey(scene.exit().destinationRegion())) {
			throw new IllegalArgumentException("Fixed scene " + scene.code() + " exit leads to unknown region "
					+ scene.exit().destinationRegion());
		}
	}

	private static <T> Optional<T> find(Map<String, T> index, String code) {
		return Optional.ofNullable(index.get(Objects.requireNonNull(code, "code")));
	}

	private static <T> Map<String, T> index(String type, Collection<T> items, Function<T, String> code) {
		Objects.requireNonNull(items, type + " definitions");
		Map<String, T> index = new LinkedHashMap<>();
		for (T item : items) {
			Objects.requireNonNull(item, "null " + type + " definition");
			if (index.putIfAbsent(code.apply(item), item) != null) {
				throw new IllegalArgumentException("Duplicate " + type + " code: " + code.apply(item));
			}
		}
		return Collections.unmodifiableMap(index);
	}
}
