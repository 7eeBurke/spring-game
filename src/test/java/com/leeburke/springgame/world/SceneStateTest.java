package com.leeburke.springgame.world;

import static com.leeburke.springgame.world.WorldFixtures.ALTAR;
import static com.leeburke.springgame.world.WorldFixtures.CRYPT;
import static com.leeburke.springgame.world.WorldFixtures.DESTINATION_A;
import static com.leeburke.springgame.world.WorldFixtures.richScene;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SceneStateTest {

	private static final SceneZone HALL = new SceneZone("hall", "Hall");
	private static final SceneZone YARD = new SceneZone("yard", "Yard");

	/** Mutable builder over the ten lists, so each test changes one thing from a valid two-zone scene. */
	private static final class Parts {
		List<SceneZone> zones = new ArrayList<>(List.of(HALL, YARD));
		List<ZoneConnection> connections = new ArrayList<>(List.of(new ZoneConnection("hall_yard", "hall", "yard")));
		List<SceneEntity> entities = new ArrayList<>(List.of(new SceneEntity("e1", "HOLLOW_ACOLYTE", "hall")));
		List<SceneObject> objects = new ArrayList<>(List.of(new SceneObject("o1", "ALTAR", "hall")));
		List<SceneHazard> hazards = new ArrayList<>(List.of(new SceneHazard("h1", "FIRE", "yard")));
		List<SceneExit> exits = new ArrayList<>(List.of(new SceneExit("x1", "yard", DESTINATION_A)));
		List<SceneEvent> events = new ArrayList<>(List.of(new SceneEvent("v1", "FALSE_BLESSING", "hall")));
		List<String> flags = new ArrayList<>(List.of("SMOKE_THICK"));
		List<HiddenContentRef> hidden = new ArrayList<>();
		List<String> facts = new ArrayList<>(List.of("DOOR_IS_BARRED"));

		SceneState build() {
			return new SceneState(zones, connections, entities, objects, hazards, exits, events, flags, hidden, facts);
		}
	}

	private static SceneState with(UnaryOperator<Parts> change) {
		return change.apply(new Parts()).build();
	}

	@Test
	void richFixtureIsValid() {
		assertThat(richScene().zones()).hasSize(4);
	}

	@Test
	void singleZoneWithEmptyCollectionsIsValid() {
		assertThat(WorldFixtures.minimalScene().zones()).hasSize(1);
	}

	@Test
	void requiresAtLeastOneZone() {
		assertThatIllegalArgumentException().isThrownBy(() -> with(p -> {
			p.zones.clear();
			p.connections.clear();
			p.entities.clear();
			p.objects.clear();
			p.hazards.clear();
			p.exits.clear();
			p.events.clear();
			return p;
		}));
	}

	@Test
	void rejectsDuplicateIdsInEachCollection() {
		assertThatIllegalArgumentException().isThrownBy(() -> with(p -> { p.zones.add(new SceneZone("hall", "Other")); return p; }))
				.withMessageContaining("zone");
		assertThatIllegalArgumentException().isThrownBy(() -> with(p -> {
			p.zones.add(new SceneZone("cellar", "Cellar"));
			p.connections.add(new ZoneConnection("hall_yard", "hall", "cellar"));
			return p;
		})).withMessageContaining("connection");
		assertThatIllegalArgumentException().isThrownBy(() -> with(p -> { p.entities.add(new SceneEntity("e1", "BONE_WARDEN", "yard")); return p; }))
				.withMessageContaining("entity");
		assertThatIllegalArgumentException().isThrownBy(() -> with(p -> { p.objects.add(new SceneObject("o1", "CHAIN", "yard")); return p; }))
				.withMessageContaining("object");
		assertThatIllegalArgumentException().isThrownBy(() -> with(p -> { p.hazards.add(new SceneHazard("h1", "FIRE", "hall")); return p; }))
				.withMessageContaining("hazard");
		assertThatIllegalArgumentException().isThrownBy(() -> with(p -> { p.exits.add(new SceneExit("x1", "hall", DESTINATION_A)); return p; }))
				.withMessageContaining("exit");
		assertThatIllegalArgumentException().isThrownBy(() -> with(p -> { p.events.add(new SceneEvent("v1", "FERRYMAN_OF_ASH", "yard")); return p; }))
				.withMessageContaining("event");
	}

	@Test
	void rejectsBrokenConnections() {
		assertThatIllegalArgumentException().isThrownBy(() -> with(p -> { p.connections.add(new ZoneConnection("c2", "hall", "nowhere")); return p; }))
				.withMessageContaining("nowhere");
		assertThatIllegalArgumentException().isThrownBy(() -> with(p -> { p.connections.add(new ZoneConnection("c2", "hall", "hall")); return p; }))
				.withMessageContaining("itself");
		assertThatIllegalArgumentException().isThrownBy(() -> with(p -> { p.connections.add(new ZoneConnection("yard_hall", "yard", "hall")); return p; }))
				.withMessageContaining("Duplicate connection");
	}

	@Test
	void rejectsContentInUnknownZones() {
		assertThatIllegalArgumentException().isThrownBy(() -> with(p -> { p.entities.add(new SceneEntity("e2", "HOLLOW_ACOLYTE", "void")); return p; }));
		assertThatIllegalArgumentException().isThrownBy(() -> with(p -> { p.objects.add(new SceneObject("o2", "CHAIN", "void")); return p; }));
		assertThatIllegalArgumentException().isThrownBy(() -> with(p -> { p.hazards.add(new SceneHazard("h2", "FIRE", "void")); return p; }));
		assertThatIllegalArgumentException().isThrownBy(() -> with(p -> { p.exits.add(new SceneExit("x2", "void", DESTINATION_A)); return p; }));
		assertThatIllegalArgumentException().isThrownBy(() -> with(p -> { p.events.add(new SceneEvent("v2", "FALSE_BLESSING", "void")); return p; }));
	}

	@Test
	void hiddenReferencesMustResolveToContentOfTheirKind() {
		assertThat(with(p -> { p.hidden.add(new HiddenContentRef(HiddenContentKind.OBJECT, "o1")); return p; }).hiddenContent())
				.hasSize(1);
		assertThatIllegalArgumentException().isThrownBy(() -> with(p -> { p.hidden.add(new HiddenContentRef(HiddenContentKind.OBJECT, "o9")); return p; }));
		// "e1" exists, but as an entity, not an object.
		assertThatIllegalArgumentException().isThrownBy(() -> with(p -> { p.hidden.add(new HiddenContentRef(HiddenContentKind.OBJECT, "e1")); return p; }));
		assertThatIllegalArgumentException().isThrownBy(() -> with(p -> {
			p.hidden.add(new HiddenContentRef(HiddenContentKind.ZONE, "yard"));
			p.hidden.add(new HiddenContentRef(HiddenContentKind.ZONE, "yard"));
			return p;
		})).withMessageContaining("Duplicate hidden");
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "  ", " hall", "hall " })
	void rejectsBlankOrUntrimmedLocalIds(String id) {
		assertThatIllegalArgumentException().isThrownBy(() -> new SceneZone(id, "Name"));
		assertThatIllegalArgumentException().isThrownBy(() -> new SceneObject(id, "CHAIN", "hall"));
		assertThatIllegalArgumentException().isThrownBy(() -> new HiddenContentRef(HiddenContentKind.ZONE, id));
	}

	@Test
	void rejectsNullLocalIdsAndBlankDisplayName() {
		assertThatNullPointerException().isThrownBy(() -> new SceneZone(null, "Name"));
		assertThatIllegalArgumentException().isThrownBy(() -> new SceneZone("hall", " "));
		assertThatNullPointerException().isThrownBy(() -> new SceneExit("x", "hall", null));
	}

	@ParameterizedTest
	@ValueSource(strings = { "wooden_pew", "Wooden Pew", "", "1PEW" })
	void rejectsBadDefinitionCodes(String code) {
		assertThatIllegalArgumentException().isThrownBy(() -> new SceneObject("o", code, "hall"));
		assertThatIllegalArgumentException().isThrownBy(() -> new SceneEntity("e", code, "hall"));
		assertThatIllegalArgumentException().isThrownBy(() -> new SceneHazard("h", code, "hall"));
		assertThatIllegalArgumentException().isThrownBy(() -> new SceneEvent("v", code, "hall"));
	}

	@Test
	void rejectsBadOrDuplicateFlagsAndFacts() {
		assertThatIllegalArgumentException().isThrownBy(() -> with(p -> { p.flags.add("smoke"); return p; }));
		assertThatIllegalArgumentException().isThrownBy(() -> with(p -> { p.flags.add("SMOKE_THICK"); return p; }));
		assertThatIllegalArgumentException().isThrownBy(() -> with(p -> { p.facts.add(" "); return p; }));
		assertThatIllegalArgumentException().isThrownBy(() -> with(p -> { p.facts.add("DOOR_IS_BARRED"); return p; }));
	}

	@Test
	void rejectsNullListsAndElements() {
		assertThatNullPointerException().isThrownBy(() -> with(p -> { p.objects = null; return p; }));
		assertThatNullPointerException().isThrownBy(() -> with(p -> { p.facts = Arrays.asList("A_FACT", null); return p; }));
	}

	@Test
	void listsAreUnmodifiableAndCallerListsAreNotRetained() {
		Parts parts = new Parts();
		SceneState state = parts.build();

		parts.zones.add(new SceneZone("cellar", "Cellar"));
		parts.facts.add("NEW_FACT");

		assertThat(state.zones()).hasSize(2);
		assertThat(state.discoveredFacts()).containsExactly("DOOR_IS_BARRED");
		assertThatThrownBy(() -> state.zones().add(HALL)).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> state.hiddenContent().clear()).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> state.environmentFlags().clear()).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void discoveredFactsKeepTheirOrder() {
		assertThat(richScene().discoveredFacts()).containsExactly("ALTAR_IS_HOLLOW", "ACOLYTES_FEAR_FIRE");
	}

	@Test
	void zoneQueries() {
		SceneState scene = richScene();
		assertThat(scene.hasZone(CRYPT)).isTrue();
		assertThat(scene.isHidden(HiddenContentKind.ZONE, CRYPT)).isTrue();
		assertThat(scene.hasZone(ALTAR)).isTrue();
		assertThat(scene.isHidden(HiddenContentKind.ZONE, ALTAR)).isFalse();
		assertThat(scene.hasZone("bell_tower")).isFalse();
	}
}
