package com.leeburke.springgame.world;

import static com.leeburke.springgame.world.HiddenContentKind.CONNECTION;
import static com.leeburke.springgame.world.HiddenContentKind.ENTITY;
import static com.leeburke.springgame.world.HiddenContentKind.EXIT;
import static com.leeburke.springgame.world.HiddenContentKind.HAZARD;
import static com.leeburke.springgame.world.HiddenContentKind.OBJECT;
import static com.leeburke.springgame.world.HiddenContentKind.ZONE;

import java.util.List;
import java.util.UUID;

/** Deterministic world fixtures shared by world, view, codec and persistence tests. */
public final class WorldFixtures {

	public static final UUID DESTINATION_A = UUID.fromString("aaaaaaaa-0000-0000-0000-00000000000a");
	public static final UUID DESTINATION_B = UUID.fromString("bbbbbbbb-0000-0000-0000-00000000000b");
	public static final UUID DESTINATION_C = UUID.fromString("cccccccc-0000-0000-0000-00000000000c");

	public static final String ENTRANCE = "nave_entrance";
	public static final String AISLE = "side_aisle";
	public static final String ALTAR = "altar";
	public static final String CRYPT = "crypt";

	private WorldFixtures() {
	}

	/**
	 * A scene with visible content, explicitly hidden content of several kinds, and a hidden zone
	 * ({@code crypt}) whose contents are not individually marked hidden.
	 */
	public static SceneState richScene() {
		return new SceneState(
				List.of(
						new SceneZone(ENTRANCE, "Nave Entrance"),
						new SceneZone(AISLE, "Side Aisle"),
						new SceneZone(ALTAR, "Altar"),
						new SceneZone(CRYPT, "Crypt")),
				List.of(
						new ZoneConnection("entrance_aisle", ENTRANCE, AISLE),
						new ZoneConnection("aisle_altar", AISLE, ALTAR),
						new ZoneConnection("altar_crypt", ALTAR, CRYPT)),
				List.of(
						new SceneEntity("acolyte_1", "HOLLOW_ACOLYTE", AISLE),
						new SceneEntity("warden_1", "BONE_WARDEN", ALTAR),
						new SceneEntity("ghoul_1", "HOLLOW_ACOLYTE", CRYPT)),
				List.of(
						new SceneObject("pew_1", "WOODEN_PEW", ENTRANCE),
						new SceneObject("relic_1", "CURSED_RELIC", ALTAR),
						new SceneObject("crypt_chest", "CONTAINER", CRYPT)),
				List.of(
						new SceneHazard("ceiling_1", "UNSTABLE_CEILING", AISLE),
						new SceneHazard("plate_1", "PRESSURE_PLATE", ALTAR)),
				List.of(
						new SceneExit("north_door", ENTRANCE, DESTINATION_A),
						new SceneExit("secret_stair", ALTAR, DESTINATION_B),
						new SceneExit("crypt_tunnel", CRYPT, DESTINATION_C)),
				List.of(new SceneEvent("pilgrim_event", "WOUNDED_PILGRIM", ENTRANCE)),
				List.of("CANDLES_LIT"),
				List.of(
						new HiddenContentRef(ZONE, CRYPT),
						new HiddenContentRef(CONNECTION, "aisle_altar"),
						new HiddenContentRef(ENTITY, "warden_1"),
						new HiddenContentRef(OBJECT, "relic_1"),
						new HiddenContentRef(HAZARD, "plate_1"),
						new HiddenContentRef(EXIT, "secret_stair")),
				List.of("ALTAR_IS_HOLLOW", "ACOLYTES_FEAR_FIRE"));
	}

	/** One zone, every other collection empty. */
	public static SceneState minimalScene() {
		return new SceneState(List.of(new SceneZone("hearth", "Hearth")), List.of(), List.of(), List.of(), List.of(),
				List.of(), List.of(), List.of(), List.of(), List.of());
	}

	public static SceneInstance hubScene(UUID sceneId, UUID runId, SceneState state) {
		return new SceneInstance(sceneId, runId, "THE_LAST_LANTERN", new ScenePlacement.Hub(), true, 0, state);
	}

	public static SceneInstance regionScene(UUID sceneId, UUID runId, UUID regionId, long seed, SceneState state) {
		return new SceneInstance(sceneId, runId, "RUINED_NAVE", new ScenePlacement.Region(regionId, seed), false, 0, state);
	}
}
