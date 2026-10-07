package com.leeburke.springgame.content.world;

import static com.leeburke.springgame.content.world.WorldContentFixtures.archetype;
import static com.leeburke.springgame.content.world.WorldContentFixtures.archetypes;
import static com.leeburke.springgame.content.world.WorldContentFixtures.bossArchetype;
import static com.leeburke.springgame.content.world.WorldContentFixtures.element;
import static com.leeburke.springgame.content.world.WorldContentFixtures.elements;
import static com.leeburke.springgame.content.world.WorldContentFixtures.hub;
import static com.leeburke.springgame.content.world.WorldContentFixtures.region;
import static com.leeburke.springgame.content.world.WorldContentFixtures.slot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Cross-reference rules of the world catalogue, with hand-built content. */
class WorldContentCatalogTest {

	private static WorldContentCatalog catalog(List<WorldElementDefinition> elements, List<SceneArchetypeDefinition> archetypes,
			List<RegionDefinition> regions, List<FixedSceneDefinition> fixed) {
		return new WorldContentCatalog(elements, archetypes, regions, fixed);
	}

	private static List<SceneArchetypeDefinition> archetypesPlus(SceneArchetypeDefinition extra) {
		List<SceneArchetypeDefinition> list = new ArrayList<>(archetypes());
		list.add(extra);
		return list;
	}

	@Test
	void validContentWithTwoRegionsIsAccepted() {
		WorldContentCatalog catalog = catalog(elements(), archetypes(), List.of(region("NORTH"), region("SOUTH")),
				List.of(hub("NORTH")));
		assertThat(catalog.regions()).extracting(RegionDefinition::code).containsExactly("NORTH", "SOUTH");
		assertThat(catalog.findArchetype("ROOM_B")).isPresent();
		assertThat(catalog.findElement("NOPE")).isEmpty();
		assertThat(catalog.archetypes()).extracting(SceneArchetypeDefinition::code)
				.containsExactly("ROOM_A", "ROOM_B", "ROOM_C", "ARENA");
	}

	@Test
	void listsAreUnmodifiable() {
		WorldContentCatalog catalog = catalog(elements(), archetypes(), List.of(region("NORTH")), List.of(hub("NORTH")));
		assertThatThrownBy(() -> catalog.elements().clear()).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> catalog.regions().clear()).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void rejectsDuplicateCodesInEveryCollection() {
		List<WorldElementDefinition> dupElements = new ArrayList<>(elements());
		dupElements.add(element("GRUNT", WorldElementKind.ENTITY));
		assertThatIllegalArgumentException().isThrownBy(() -> catalog(dupElements, archetypes(), List.of(), List.of()))
				.withMessageContaining("GRUNT");
		assertThatIllegalArgumentException().isThrownBy(
				() -> catalog(elements(), archetypesPlus(archetype("ROOM_A", List.of())), List.of(), List.of()))
				.withMessageContaining("ROOM_A");
		assertThatIllegalArgumentException().isThrownBy(
				() -> catalog(elements(), archetypes(), List.of(region("NORTH"), region("NORTH")), List.of()));
		assertThatIllegalArgumentException().isThrownBy(
				() -> catalog(elements(), archetypes(), List.of(region("NORTH")), List.of(hub("NORTH"), hub("NORTH"))));
	}

	@Test
	void slotMustReferenceKnownElementOfItsKind() {
		SceneArchetypeDefinition unknown = archetype("ROOM_X", List.of(slot("s", WorldElementKind.OBJECT, List.of("GHOST"), 50, 0)));
		assertThatIllegalArgumentException().isThrownBy(() -> catalog(elements(), archetypesPlus(unknown), List.of(), List.of()))
				.withMessageContaining("GHOST");
		SceneArchetypeDefinition mismatch = archetype("ROOM_X", List.of(slot("s", WorldElementKind.HAZARD, List.of("BARREL"), 50, 0)));
		assertThatIllegalArgumentException().isThrownBy(() -> catalog(elements(), archetypesPlus(mismatch), List.of(), List.of()))
				.withMessageContaining("BARREL");
	}

	@Test
	void regionMustReferenceKnownArchetypes() {
		RegionDefinition missingNormal = new RegionDefinition("R", "R", List.of("ROOM_A", "ROOM_B", "ROOM_Z"), "ARENA", "BOSS",
				new IntRange(5, 7), new IntRange(0, 2), new IntRange(1, 2));
		assertThatIllegalArgumentException().isThrownBy(() -> catalog(elements(), archetypes(), List.of(missingNormal), List.of()))
				.withMessageContaining("ROOM_Z");
		RegionDefinition missingBoss = new RegionDefinition("R", "R", List.of("ROOM_A", "ROOM_B", "ROOM_C"), "THRONE", "BOSS",
				new IntRange(5, 7), new IntRange(0, 2), new IntRange(1, 2));
		assertThatIllegalArgumentException().isThrownBy(() -> catalog(elements(), archetypes(), List.of(missingBoss), List.of()))
				.withMessageContaining("THRONE");
	}

	@Test
	void bossEntityRules() {
		RegionDefinition notAnEntity = new RegionDefinition("R", "R", List.of("ROOM_A", "ROOM_B", "ROOM_C"), "ARENA", "BARREL",
				new IntRange(5, 7), new IntRange(0, 2), new IntRange(1, 2));
		assertThatIllegalArgumentException().isThrownBy(() -> catalog(elements(), archetypes(), List.of(notAnEntity), List.of()));

		List<SceneArchetypeDefinition> normalUsesBoss = new ArrayList<>(archetypes());
		normalUsesBoss.set(2, archetype("ROOM_C", List.of(slot("b", WorldElementKind.ENTITY, List.of("BOSS"), 10, 0))));
		assertThatIllegalArgumentException().isThrownBy(() -> catalog(elements(), normalUsesBoss, List.of(region("R")), List.of()))
				.withMessageContaining("ROOM_C");

		List<SceneArchetypeDefinition> unreliableBoss = new ArrayList<>(archetypes());
		unreliableBoss.set(3, archetype("ARENA", List.of(slot("boss", WorldElementKind.ENTITY, List.of("BOSS"), 90, 0))));
		assertThatIllegalArgumentException().isThrownBy(() -> catalog(elements(), unreliableBoss, List.of(region("R")), List.of()))
				.withMessageContaining("ARENA");

		List<SceneArchetypeDefinition> hiddenBoss = new ArrayList<>(archetypes());
		hiddenBoss.set(3, archetype("ARENA", List.of(slot("boss", WorldElementKind.ENTITY, List.of("BOSS"), 100, 10))));
		assertThatIllegalArgumentException().isThrownBy(() -> catalog(elements(), hiddenBoss, List.of(region("R")), List.of()));

		assertThat(bossArchetype().slots()).hasSize(1);
	}

	@Test
	void fixedSceneExitMustLeadToKnownRegion() {
		assertThatIllegalArgumentException().isThrownBy(
				() -> catalog(elements(), archetypes(), List.of(region("NORTH")), List.of(hub("ATLANTIS"))))
				.withMessageContaining("ATLANTIS");
	}
}
