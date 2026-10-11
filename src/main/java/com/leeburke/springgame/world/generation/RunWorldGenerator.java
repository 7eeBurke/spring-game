package com.leeburke.springgame.world.generation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

import com.leeburke.springgame.content.world.FixedSceneDefinition;
import com.leeburke.springgame.content.world.RegionDefinition;
import com.leeburke.springgame.content.world.SceneArchetypeDefinition;
import com.leeburke.springgame.content.world.WorldContentCatalog;
import com.leeburke.springgame.world.PlayerLocation;
import com.leeburke.springgame.world.RegionInstance;
import com.leeburke.springgame.world.SceneExit;
import com.leeburke.springgame.world.SceneInstance;
import com.leeburke.springgame.world.ScenePlacement;
import com.leeburke.springgame.world.SceneState;
import com.leeburke.springgame.world.generation.SceneContentGenerator.ExitSpec;

/**
 * Generates a new run's complete world in memory: the fixed {@value #HUB_CODE} hub and the region
 * its exit leads to. Nothing is generated lazily and nothing here touches persistence.
 * <p>
 * Each attempt uses a seed derived from the run seed and attempt index. Its topology and archetype
 * draws use one stream; each scene's contents use that scene's own seed. A candidate is validated as
 * a whole; the first valid attempt wins, up to {@value #MAX_ATTEMPTS}. Fated, character stats and
 * loadout have no influence on generation.
 * <p>
 * Runtime UUIDs come from the {@link IdSource} and never from the procedural random streams.
 */
public final class RunWorldGenerator {

	public static final String HUB_CODE = "THE_LAST_LANTERN";
	public static final int MAX_ATTEMPTS = 8;
	static final String HUB_RETURN_EXIT_ID = "lantern_road";

	private final WorldContentCatalog catalog;
	private final IdSource ids;
	private final FixedSceneDefinition hub;
	private final RegionDefinition region;
	private final RegionValidator validator;

	public RunWorldGenerator(WorldContentCatalog catalog, IdSource ids) {
		this(catalog, ids, null);
	}

	/** Test seam: a {@code null} validator means the production {@link CompleteRegionValidator}. */
	RunWorldGenerator(WorldContentCatalog catalog, IdSource ids, RegionValidator validator) {
		this.catalog = Objects.requireNonNull(catalog, "catalog");
		this.ids = Objects.requireNonNull(ids, "ids");
		this.hub = catalog.findFixedScene(HUB_CODE)
				.orElseThrow(() -> new IllegalArgumentException("World content has no fixed scene " + HUB_CODE));
		this.region = catalog.findRegion(hub.exit().destinationRegion()).orElseThrow();
		this.validator = validator != null ? validator : new CompleteRegionValidator(region, catalog::findArchetype);
	}

	/**
	 * @throws GenerationException if no attempt produces a valid region
	 */
	public GeneratedRunWorld generate(UUID runId, long runSeed, GenerationContextSnapshot context) {
		Objects.requireNonNull(runId, "runId");
		Objects.requireNonNull(context, "context");
		UUID hubId = ids.next();
		List<String> lastProblems = List.of();
		for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
			GeneratedRegion candidate = generateRegion(runId, runSeed, context, attempt, hubId);
			lastProblems = validator.problems(candidate);
			if (lastProblems.isEmpty()) {
				SceneInstance hubScene = buildHub(hubId, runId, candidate.entrySceneId());
				return new GeneratedRunWorld(runId, context, hubScene, candidate, new PlayerLocation(hubId, hub.startZone()));
			}
		}
		throw new GenerationException("Could not generate a valid " + region.code() + " for run " + runId + " after "
				+ MAX_ATTEMPTS + " attempts; last problems: " + lastProblems);
	}

	/** Each scene's number of passages from the region's first scene (index 0), over the topology. */
	static int[] depths(List<List<Integer>> neighbours) {
		int[] depth = new int[neighbours.size()];
		java.util.Arrays.fill(depth, Integer.MAX_VALUE);
		depth[0] = 0;
		java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>(List.of(0));
		while (!queue.isEmpty()) {
			int scene = queue.poll();
			for (int next : neighbours.get(scene)) {
				if (depth[next] == Integer.MAX_VALUE) {
					depth[next] = depth[scene] + 1;
					queue.add(next);
				}
			}
		}
		return depth;
	}

	/** One deterministic generation attempt (no validation). */
	GeneratedRegion generateRegion(UUID runId, long runSeed, GenerationContextSnapshot context, int attempt, UUID hubId) {
		long attemptSeed = WorldRandom.attemptSeed(runSeed, attempt);
		RandomGenerator rng = WorldRandom.create(attemptSeed);
		RegionTopology topology = TopologyPlanner.plan(region, rng);
		List<List<Integer>> neighbours = topology.neighbours();
		int bossIndex = topology.bossIndex();

		String[] archetypes = new String[topology.sceneCount()];
		Map<String, Integer> usage = new HashMap<>();
		for (int i = 0; i < bossIndex; i++) {
			if (i == 0) {
				// Only a place that can lie just behind the region's exterior doors may open it.
				archetypes[i] = ArchetypeSelector.pickOpening(region.openingArchetypes(), context, rng);
			} else {
				Set<String> neighbourArchetypes = new HashSet<>();
				for (int j : neighbours.get(i)) {
					if (j < i) {
						neighbourArchetypes.add(archetypes[j]);
					}
				}
				archetypes[i] = ArchetypeSelector.pickNext(region.normalArchetypes(), neighbourArchetypes, usage, rng);
			}
			usage.merge(archetypes[i], 1, Integer::sum);
		}
		archetypes[bossIndex] = region.bossArchetype();

		UUID regionId = ids.next();
		List<UUID> sceneIds = new ArrayList<>();
		for (int i = 0; i < topology.sceneCount(); i++) {
			sceneIds.add(ids.next());
		}

		List<SceneState> states = new ArrayList<>();
		long[] sceneSeeds = new long[topology.sceneCount()];
		int[] depth = depths(neighbours);
		for (int i = 0; i < topology.sceneCount(); i++) {
			sceneSeeds[i] = WorldRandom.sceneSeed(attemptSeed, i);
			List<ExitSpec> exits = new ArrayList<>();
			List<Integer> linked = neighbours.get(i);
			for (int k = 0; k < linked.size(); k++) {
				// A way back toward the region's first scene is a way in: it leaves from the entrance.
				exits.add(new ExitSpec("exit_" + (k + 1), sceneIds.get(linked.get(k)), depth[linked.get(k)] < depth[i]));
			}
			if (i == 0) {
				exits.add(new ExitSpec(HUB_RETURN_EXIT_ID, hubId, true));
			}
			SceneArchetypeDefinition archetype = catalog.findArchetype(archetypes[i]).orElseThrow();
			states.add(SceneContentGenerator.generate(archetype, WorldRandom.create(sceneSeeds[i]), exits));
		}
		states = SceneContentGenerator.dropDuplicateEvents(states);
		// Containers and the first find come from their own streams: the structure above is unchanged by them.
		List<SceneState> furnished = new ArrayList<>();
		for (int i = 0; i < states.size(); i++) {
			furnished.add(SceneFurnisher.furnish(states.get(i), sceneSeeds[i], catalog.containers(),
					i == 0 ? java.util.Optional.of(HUB_RETURN_EXIT_ID) : java.util.Optional.empty()));
		}
		states = furnished;

		List<SceneInstance> scenes = new ArrayList<>();
		for (int i = 0; i < topology.sceneCount(); i++) {
			scenes.add(new SceneInstance(sceneIds.get(i), runId, archetypes[i],
					new ScenePlacement.Region(regionId, sceneSeeds[i]), false, 0, states.get(i)));
		}
		List<List<UUID>> routeStages = topology.stages().stream()
				.map(stage -> stage.stream().map(sceneIds::get).toList())
				.toList();
		List<UUID> optional = sceneIds.subList(topology.requiredCount(), bossIndex);
		return new GeneratedRegion(new RegionInstance(regionId, runId, region.code()), routeStages, optional,
				sceneIds.get(bossIndex), scenes);
	}

	private SceneInstance buildHub(UUID hubId, UUID runId, UUID entrySceneId) {
		SceneState state = new SceneState(hub.zones(), hub.connections(), List.of(), List.of(), List.of(),
				List.of(new SceneExit(hub.exit().id(), hub.exit().zoneId(), entrySceneId)), List.of(), List.of(),
				List.of(), List.of()).generated(List.of());
		return new SceneInstance(hubId, runId, hub.code(), new ScenePlacement.Hub(), true, 0, state);
	}
}
