package com.leeburke.springgame.world.generation;

import static com.leeburke.springgame.world.generation.GenerationTestSupport.fixed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.leeburke.springgame.content.world.ContentSlot;
import com.leeburke.springgame.content.world.SceneArchetypeDefinition;
import com.leeburke.springgame.content.world.WorldElementKind;
import com.leeburke.springgame.world.HiddenContentKind;
import com.leeburke.springgame.world.HiddenContentRef;
import com.leeburke.springgame.world.SceneEvent;
import com.leeburke.springgame.world.SceneExit;
import com.leeburke.springgame.world.SceneState;
import com.leeburke.springgame.world.SceneZone;
import com.leeburke.springgame.world.ZoneConnection;
import com.leeburke.springgame.world.generation.SceneContentGenerator.ExitSpec;

class SceneContentGeneratorTest {

	private static final UUID NEXT = UUID.fromString("00000000-0000-0000-0000-0000000000ee");

	private static SceneArchetypeDefinition archetype(List<ContentSlot> slots) {
		return new SceneArchetypeDefinition("ROOM", "Room",
				List.of(new SceneZone("a", "A"), new SceneZone("b", "B")),
				List.of(new ZoneConnection("a_b", "a", "b")),
				List.of("a", "b"), slots);
	}

	private static ContentSlot slot(String id, WorldElementKind kind, int chance, int hiddenChance) {
		return new ContentSlot(id, kind, List.of(kind == WorldElementKind.EVENT ? "OMEN" : "THING", "OTHER"),
				List.of("a", "b"), chance, hiddenChance);
	}

	@Test
	void lowestRollsFillEverySlotWithFirstChoices() {
		SceneArchetypeDefinition archetype = archetype(List.of(
				slot("e", WorldElementKind.ENTITY, 50, 0),
				slot("o", WorldElementKind.OBJECT, 1, 0),
				slot("h", WorldElementKind.HAZARD, 100, 0),
				slot("v", WorldElementKind.EVENT, 30, 0)));

		SceneState state = SceneContentGenerator.generate(archetype, fixed(0), List.of(new ExitSpec("exit_1", NEXT)));

		assertThat(state.entities()).extracting("id", "definitionCode", "zoneId").containsExactly(tuple("e", "THING", "a"));
		assertThat(state.objects()).hasSize(1);
		assertThat(state.hazards()).hasSize(1);
		assertThat(state.activeEvents()).extracting(SceneEvent::id).containsExactly("v");
		assertThat(state.hiddenContent()).isEmpty();
		assertThat(state.exits()).containsExactly(new SceneExit("exit_1", "a", NEXT));
		assertThat(state.zones()).isEqualTo(archetype.zones());
	}

	@Test
	void highRollsLeaveOptionalSlotsEmptyButQuietScenesStayValid() {
		SceneArchetypeDefinition archetype = archetype(List.of(
				slot("e", WorldElementKind.ENTITY, 50, 0),
				slot("o", WorldElementKind.OBJECT, 99, 0)));

		SceneState state = SceneContentGenerator.generate(archetype, fixed(99), List.of(new ExitSpec("exit_1", NEXT)));

		assertThat(state.entities()).isEmpty();
		assertThat(state.objects()).isEmpty();
		assertThat(state.exits()).containsExactly(new SceneExit("exit_1", "b", NEXT));
	}

	@Test
	void hiddenChanceHundredAlwaysHides() {
		SceneArchetypeDefinition archetype = archetype(List.of(slot("o", WorldElementKind.OBJECT, 100, 100)));
		SceneState state = SceneContentGenerator.generate(archetype, fixed(99), List.of());
		assertThat(state.hiddenContent()).containsExactly(new HiddenContentRef(HiddenContentKind.OBJECT, "o"));
		assertThat(state.objects()).extracting("zoneId").containsExactly("b");
	}

	@Test
	void duplicateEventsAreDroppedWithTheirHiddenReferences() {
		SceneArchetypeDefinition first = archetype(List.of(new ContentSlot("omen_a", WorldElementKind.EVENT, List.of("OMEN"),
				List.of("a"), 100, 0)));
		SceneArchetypeDefinition second = archetype(List.of(
				new ContentSlot("omen_b", WorldElementKind.EVENT, List.of("OMEN"), List.of("b"), 100, 100),
				new ContentSlot("other", WorldElementKind.EVENT, List.of("PORTENT"), List.of("a"), 100, 0)));
		SceneState a = SceneContentGenerator.generate(first, fixed(0), List.of());
		SceneState b = SceneContentGenerator.generate(second, fixed(0), List.of());
		assertThat(b.hiddenContent()).containsExactly(new HiddenContentRef(HiddenContentKind.EVENT, "omen_b"));

		List<SceneState> deduplicated = SceneContentGenerator.dropDuplicateEvents(List.of(a, b));

		assertThat(deduplicated.get(0)).isEqualTo(a);
		assertThat(deduplicated.get(1).activeEvents()).extracting(SceneEvent::id).containsExactly("other");
		assertThat(deduplicated.get(1).hiddenContent()).isEmpty();
	}

	@Test
	void sameRandomSourceGivesSameScene() {
		SceneArchetypeDefinition archetype = archetype(List.of(
				slot("e", WorldElementKind.ENTITY, 50, 50),
				slot("o", WorldElementKind.OBJECT, 50, 50)));
		List<ExitSpec> exits = List.of(new ExitSpec("exit_1", NEXT), new ExitSpec("exit_2", NEXT));
		assertThat(SceneContentGenerator.generate(archetype, WorldRandom.create(5), exits))
				.isEqualTo(SceneContentGenerator.generate(archetype, WorldRandom.create(5), exits));
	}
}
