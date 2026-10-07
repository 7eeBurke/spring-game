package com.leeburke.springgame.content.world;

import static com.leeburke.springgame.content.world.WorldContentFixtures.archetype;
import static com.leeburke.springgame.content.world.WorldContentFixtures.slot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.leeburke.springgame.world.SceneZone;
import com.leeburke.springgame.world.ZoneConnection;

/** Invariants of each authored world definition record, without a catalogue. */
class WorldContentDefinitionTest {

	@ParameterizedTest
	@ValueSource(strings = { "grunt", "Grunt", "", " GRUNT" })
	void elementRejectsBadCodes(String code) {
		assertThatIllegalArgumentException().isThrownBy(() -> new WorldElementDefinition(code, "Grunt", WorldElementKind.ENTITY));
	}

	@Test
	void elementRejectsBlankNameAndNullKind() {
		assertThatIllegalArgumentException().isThrownBy(() -> new WorldElementDefinition("GRUNT", " ", WorldElementKind.ENTITY));
		assertThatNullPointerException().isThrownBy(() -> new WorldElementDefinition("GRUNT", "Grunt", null));
	}

	@Test
	void intRangeRules() {
		assertThat(new IntRange(0, 0).contains(0)).isTrue();
		assertThat(new IntRange(5, 7).contains(8)).isFalse();
		assertThatIllegalArgumentException().isThrownBy(() -> new IntRange(3, 2));
		assertThatIllegalArgumentException().isThrownBy(() -> new IntRange(-1, 2));
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 101, -5 })
	void slotRejectsBadChance(int chance) {
		assertThatIllegalArgumentException().isThrownBy(() -> slot("s", WorldElementKind.OBJECT, List.of("BARREL"), chance, 0));
	}

	@ParameterizedTest
	@ValueSource(ints = { -1, 101 })
	void slotRejectsBadHiddenChance(int hiddenChance) {
		assertThatIllegalArgumentException().isThrownBy(() -> slot("s", WorldElementKind.OBJECT, List.of("BARREL"), 50, hiddenChance));
	}

	@Test
	void slotAcceptsBoundaryChances() {
		assertThat(slot("s", WorldElementKind.OBJECT, List.of("BARREL"), 1, 0).chance()).isEqualTo(1);
		assertThat(slot("s", WorldElementKind.OBJECT, List.of("BARREL"), 100, 100).hiddenChance()).isEqualTo(100);
	}

	@Test
	void slotRejectsEmptyOrDuplicateCandidatesAndZones() {
		assertThatIllegalArgumentException().isThrownBy(() -> slot("s", WorldElementKind.OBJECT, List.of(), 50, 0));
		assertThatIllegalArgumentException().isThrownBy(() -> slot("s", WorldElementKind.OBJECT, List.of("BARREL", "BARREL"), 50, 0));
		assertThatIllegalArgumentException().isThrownBy(
				() -> new ContentSlot("s", WorldElementKind.OBJECT, List.of("BARREL"), List.of(), 50, 0));
		assertThatIllegalArgumentException().isThrownBy(
				() -> new ContentSlot("s", WorldElementKind.OBJECT, List.of("BARREL"), List.of("a", "a"), 50, 0));
		assertThatNullPointerException().isThrownBy(
				() -> new ContentSlot("s", WorldElementKind.OBJECT, null, List.of("a"), 50, 0));
	}

	@Test
	void archetypeRejectsUnknownZonesDuplicateSlotsAndBadLayouts() {
		assertThatIllegalArgumentException().isThrownBy(() -> new SceneArchetypeDefinition("ROOM", "Room",
				List.of(new SceneZone("a", "A")), List.of(), List.of("nowhere"), List.of()));
		assertThatIllegalArgumentException().isThrownBy(() -> new SceneArchetypeDefinition("ROOM", "Room",
				List.of(new SceneZone("a", "A")), List.of(), List.of("a"),
				List.of(new ContentSlot("s", WorldElementKind.OBJECT, List.of("BARREL"), List.of("nowhere"), 50, 0))));
		assertThatIllegalArgumentException().isThrownBy(() -> archetype("ROOM", List.of(
				slot("s", WorldElementKind.OBJECT, List.of("BARREL"), 50, 0),
				slot("s", WorldElementKind.HAZARD, List.of("SPIKES"), 50, 0))));
		assertThatIllegalArgumentException().isThrownBy(() -> new SceneArchetypeDefinition("ROOM", "Room",
				List.of(new SceneZone("a", "A"), new SceneZone("a", "Again")), List.of(), List.of("a"), List.of()));
		assertThatIllegalArgumentException().isThrownBy(() -> new SceneArchetypeDefinition("ROOM", "Room",
				List.of(new SceneZone("a", "A")), List.of(new ZoneConnection("c", "a", "z")), List.of("a"), List.of()));
		assertThatIllegalArgumentException().isThrownBy(() -> new SceneArchetypeDefinition("ROOM", "Room",
				List.of(new SceneZone("a", "A")), List.of(), List.of("a", "a"), List.of()));
		assertThatIllegalArgumentException().isThrownBy(() -> new SceneArchetypeDefinition("ROOM", "Room",
				List.of(new SceneZone("a", "A")), List.of(), List.of(), List.of()));
	}

	@Test
	void archetypeRejectsDisconnectedZones() {
		assertThatIllegalArgumentException().isThrownBy(() -> new SceneArchetypeDefinition("ROOM", "Room",
				List.of(new SceneZone("a", "A"), new SceneZone("b", "B"), new SceneZone("c", "C")),
				List.of(new ZoneConnection("a_b", "a", "b")), List.of("a"), List.of()))
				.withMessageContaining("not connected").withMessageContaining("c");
	}

	@Test
	void fixedSceneRejectsDisconnectedZonesAndUnknownZones() {
		assertThatIllegalArgumentException().isThrownBy(() -> new FixedSceneDefinition("HUB", "Hub",
				List.of(new SceneZone("hearth", "Hearth"), new SceneZone("road", "Road")), List.of(),
				"hearth", new FixedSceneDefinition.FixedSceneExit("out", "road", "REGION")))
				.withMessageContaining("not connected");
		assertThatIllegalArgumentException().isThrownBy(() -> new FixedSceneDefinition("HUB", "Hub",
				List.of(new SceneZone("hearth", "Hearth")), List.of(),
				"cellar", new FixedSceneDefinition.FixedSceneExit("out", "hearth", "REGION")));
		assertThatIllegalArgumentException().isThrownBy(() -> new FixedSceneDefinition("HUB", "Hub",
				List.of(new SceneZone("hearth", "Hearth")), List.of(),
				"hearth", new FixedSceneDefinition.FixedSceneExit("out", "cellar", "REGION")));
	}

	@Test
	void regionRejectsTooFewArchetypesBossInNormalListAndUnattainableCounts() {
		IntRange req = new IntRange(5, 7);
		IntRange opt = new IntRange(0, 2);
		IntRange br = new IntRange(1, 2);
		assertThatIllegalArgumentException().isThrownBy(
				() -> new RegionDefinition("R", "R", List.of("A", "B"), "BOSS_ROOM", "BOSS", req, opt, br));
		assertThatIllegalArgumentException().isThrownBy(
				() -> new RegionDefinition("R", "R", List.of("A", "B", "BOSS_ROOM"), "BOSS_ROOM", "BOSS", req, opt, br));
		assertThatIllegalArgumentException().isThrownBy(
				() -> new RegionDefinition("R", "R", List.of("A", "B", "A"), "BOSS_ROOM", "BOSS", req, opt, br));
		// Minimum of the branch range unattainable: 1 fork needs 4 required scenes.
		assertThatIllegalArgumentException().isThrownBy(
				() -> new RegionDefinition("R", "R", List.of("A", "B", "C"), "BOSS_ROOM", "BOSS", new IntRange(3, 7), opt, br));
		// Maximum of the branch range unattainable: 3 forks need 8 required scenes.
		assertThatIllegalArgumentException().isThrownBy(
				() -> new RegionDefinition("R", "R", List.of("A", "B", "C"), "BOSS_ROOM", "BOSS", req, opt, new IntRange(1, 3)))
				.withMessageContaining("branch");
	}

	@Test
	void minimumRequiredForBranches() {
		assertThat(RegionDefinition.minimumRequiredFor(1)).isEqualTo(4);
		assertThat(RegionDefinition.minimumRequiredFor(2)).isEqualTo(6);
	}
}
