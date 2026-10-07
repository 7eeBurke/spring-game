package com.leeburke.springgame.world.view;

import static com.leeburke.springgame.world.WorldFixtures.AISLE;
import static com.leeburke.springgame.world.WorldFixtures.ALTAR;
import static com.leeburke.springgame.world.WorldFixtures.CRYPT;
import static com.leeburke.springgame.world.WorldFixtures.DESTINATION_A;
import static com.leeburke.springgame.world.WorldFixtures.DESTINATION_B;
import static com.leeburke.springgame.world.WorldFixtures.DESTINATION_C;
import static com.leeburke.springgame.world.WorldFixtures.ENTRANCE;
import static com.leeburke.springgame.world.WorldFixtures.richScene;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.leeburke.springgame.world.SceneState;
import com.leeburke.springgame.world.view.PlayerSceneView.KnownExit;
import com.leeburke.springgame.world.view.PlayerSceneView.VisibleConnection;
import com.leeburke.springgame.world.view.PlayerSceneView.VisibleEntity;
import com.leeburke.springgame.world.view.PlayerSceneView.VisibleHazard;
import com.leeburke.springgame.world.view.PlayerSceneView.VisibleObject;
import com.leeburke.springgame.world.view.PlayerSceneView.VisibleZone;

/** The view is a safety boundary: it must show legitimate content and never anything hidden or backend-only. */
class PlayerSceneViewProjectorTest {

	private static final Set<String> VISIBLE = Set.of(ENTRANCE, AISLE, ALTAR);

	private static PlayerSceneView project(Set<String> visibleZones) {
		return PlayerSceneViewProjector.project(richScene(), ENTRANCE, visibleZones);
	}

	@Test
	void showsLegitimateVisibleContent() {
		PlayerSceneView view = project(VISIBLE);

		assertThat(view.currentZoneId()).isEqualTo(ENTRANCE);
		assertThat(view.zones()).containsExactly(
				new VisibleZone(ENTRANCE, "Nave Entrance"),
				new VisibleZone(AISLE, "Side Aisle"),
				new VisibleZone(ALTAR, "Altar"));
		assertThat(view.connections()).containsExactly(new VisibleConnection(ENTRANCE, AISLE));
		assertThat(view.entities()).containsExactly(new VisibleEntity("acolyte_1", "HOLLOW_ACOLYTE", AISLE));
		assertThat(view.objects()).containsExactly(new VisibleObject("pew_1", "WOODEN_PEW", ENTRANCE));
		assertThat(view.hazards()).containsExactly(new VisibleHazard("ceiling_1", "UNSTABLE_CEILING", AISLE));
		assertThat(view.exits()).containsExactly(new KnownExit("north_door", ENTRANCE));
		assertThat(view.discoveredFacts()).containsExactly("ALTAR_IS_HOLLOW", "ACOLYTES_FEAR_FIRE");
	}

	@Test
	void neverShowsHiddenContentOrHiddenZoneContents() {
		String rendered = project(VISIBLE).toString();
		for (String secret : List.of(
				"warden_1", "relic_1", "plate_1", "secret_stair",          // explicitly hidden
				CRYPT, "ghoul_1", "crypt_chest", "crypt_tunnel",           // hidden zone and its contents
				"aisle_altar", "altar_crypt",                              // hidden or hidden-zone connections
				"BONE_WARDEN", "CURSED_RELIC", "PRESSURE_PLATE", "CONTAINER")) {
			assertThat(rendered).as("view must not mention %s", secret).doesNotContain(secret);
		}
		assertThat(project(VISIBLE).connections()).doesNotContain(new VisibleConnection(AISLE, ALTAR), new VisibleConnection(ALTAR, CRYPT));
	}

	@Test
	void neverShowsBackendOnlyState() {
		String rendered = project(VISIBLE).toString();
		assertThat(rendered).doesNotContain(DESTINATION_A.toString(), DESTINATION_B.toString(), DESTINATION_C.toString());
		assertThat(rendered).doesNotContain("CANDLES_LIT", "pilgrim_event", "WOUNDED_PILGRIM");
	}

	@Test
	void hiddenZoneListedByCallerIsStillExcluded() {
		Set<String> withCrypt = Set.of(ENTRANCE, AISLE, ALTAR, CRYPT);
		assertThat(project(withCrypt)).isEqualTo(project(VISIBLE));
	}

	@Test
	void contentOutsideVisibleZonesIsExcluded() {
		PlayerSceneView view = project(Set.of(ENTRANCE));

		assertThat(view.zones()).containsExactly(new VisibleZone(ENTRANCE, "Nave Entrance"));
		assertThat(view.connections()).isEmpty();
		assertThat(view.entities()).isEmpty();
		assertThat(view.hazards()).isEmpty();
		assertThat(view.objects()).containsExactly(new VisibleObject("pew_1", "WOODEN_PEW", ENTRANCE));
		assertThat(view.exits()).containsExactly(new KnownExit("north_door", ENTRANCE));
	}

	@Test
	void rejectsUnknownVisibleZone() {
		assertThatIllegalArgumentException().isThrownBy(() -> project(Set.of(ENTRANCE, "bell_tower")));
	}

	@Test
	void rejectsInvalidCurrentZone() {
		SceneState scene = richScene();
		assertThatIllegalArgumentException().isThrownBy(() -> PlayerSceneViewProjector.project(scene, "bell_tower", VISIBLE));
		assertThatIllegalArgumentException().isThrownBy(() -> PlayerSceneViewProjector.project(scene, ALTAR, Set.of(ENTRANCE)));
		assertThatIllegalArgumentException().isThrownBy(() -> PlayerSceneViewProjector.project(scene, CRYPT, Set.of(CRYPT, ENTRANCE)));
	}

	@Test
	void viewListsAreUnmodifiable() {
		PlayerSceneView view = project(VISIBLE);
		assertThatThrownBy(() -> view.objects().clear()).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> view.exits().clear()).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> view.discoveredFacts().clear()).isInstanceOf(UnsupportedOperationException.class);
	}

	/**
	 * Structural guard: no type reachable from the view can carry a UUID, a seed/revision-like long,
	 * a hidden-content marker or any authoritative world type. A future field added to the view that
	 * breaks this fails here.
	 */
	@Test
	void viewTypesCannotCarryBackendOnlyData() {
		List<Class<?>> reachable = new ArrayList<>();
		collectRecordTypes(PlayerSceneView.class, reachable, new HashSet<>());

		assertThat(reachable).contains(PlayerSceneView.class, KnownExit.class, VisibleEntity.class);
		for (Class<?> type : reachable) {
			assertThat(type.getPackageName()).as("%s must be a view type", type).isEqualTo("com.leeburke.springgame.world.view");
			for (RecordComponent component : type.getRecordComponents()) {
				for (Class<?> used : typesIn(component.getGenericType())) {
					// Only text, lists, and other view records: no UUID, long/Long, enum or authoritative type.
					boolean viewRecord = used.isRecord() && used.getPackageName().equals("com.leeburke.springgame.world.view");
					assertThat(used == String.class || used == List.class || viewRecord)
							.as("%s.%s uses %s", type.getSimpleName(), component.getName(), used.getName())
							.isTrue();
				}
			}
		}
	}

	private static void collectRecordTypes(Class<?> type, List<Class<?>> out, Set<Class<?>> seen) {
		if (!type.isRecord() || !seen.add(type)) {
			return;
		}
		out.add(type);
		for (RecordComponent component : type.getRecordComponents()) {
			for (Class<?> used : typesIn(component.getGenericType())) {
				collectRecordTypes(used, out, seen);
			}
		}
	}

	private static List<Class<?>> typesIn(Type type) {
		List<Class<?>> result = new ArrayList<>();
		if (type instanceof Class<?> c) {
			result.add(c);
		} else if (type instanceof ParameterizedType p) {
			result.add((Class<?>) p.getRawType());
			for (Type argument : p.getActualTypeArguments()) {
				result.addAll(typesIn(argument));
			}
		}
		return result;
	}
}
