package com.leeburke.springgame.world.generation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

import com.leeburke.springgame.content.world.ContentSlot;
import com.leeburke.springgame.content.world.SceneArchetypeDefinition;
import com.leeburke.springgame.world.HiddenContentKind;
import com.leeburke.springgame.world.HiddenContentRef;
import com.leeburke.springgame.world.SceneEntity;
import com.leeburke.springgame.world.SceneEvent;
import com.leeburke.springgame.world.SceneExit;
import com.leeburke.springgame.world.SceneHazard;
import com.leeburke.springgame.world.SceneObject;
import com.leeburke.springgame.world.SceneState;

/**
 * Generates one scene's contents from its archetype, using only that scene's own random stream.
 * <p>
 * Slots are processed in authored order: a presence roll ({@code nextInt(100) < chance}); if present,
 * a uniform candidate, a uniform zone, and a hidden roll ({@code nextInt(100) < hiddenChance}).
 * Then each exit's zone is drawn uniformly from the archetype's exit zones. Exits are never hidden,
 * and no zone is ever hidden.
 */
final class SceneContentGenerator {

	/** An exit to create: its local ID and destination scene. */
	record ExitSpec(String id, UUID destinationSceneId) {
	}

	private SceneContentGenerator() {
	}

	static SceneState generate(SceneArchetypeDefinition archetype, RandomGenerator rng, List<ExitSpec> exits) {
		List<SceneEntity> entities = new ArrayList<>();
		List<SceneObject> objects = new ArrayList<>();
		List<SceneHazard> hazards = new ArrayList<>();
		List<SceneEvent> events = new ArrayList<>();
		List<HiddenContentRef> hidden = new ArrayList<>();

		for (ContentSlot slot : archetype.slots()) {
			if (rng.nextInt(100) >= slot.chance()) {
				continue;
			}
			String code = slot.candidates().get(rng.nextInt(slot.candidates().size()));
			String zone = slot.zones().get(rng.nextInt(slot.zones().size()));
			boolean isHidden = rng.nextInt(100) < slot.hiddenChance();
			HiddenContentKind kind = switch (slot.kind()) {
				case ENTITY -> {
					entities.add(new SceneEntity(slot.id(), code, zone));
					yield HiddenContentKind.ENTITY;
				}
				case OBJECT -> {
					objects.add(new SceneObject(slot.id(), code, zone));
					yield HiddenContentKind.OBJECT;
				}
				case HAZARD -> {
					hazards.add(new SceneHazard(slot.id(), code, zone));
					yield HiddenContentKind.HAZARD;
				}
				case EVENT -> {
					events.add(new SceneEvent(slot.id(), code, zone));
					yield HiddenContentKind.EVENT;
				}
			};
			if (isHidden) {
				hidden.add(new HiddenContentRef(kind, slot.id()));
			}
		}

		List<SceneExit> sceneExits = new ArrayList<>();
		for (ExitSpec exit : exits) {
			String zone = archetype.exitZones().get(rng.nextInt(archetype.exitZones().size()));
			sceneExits.add(new SceneExit(exit.id(), zone, exit.destinationSceneId()));
		}

		return new SceneState(archetype.zones(), archetype.connections(), entities, objects, hazards, sceneExits,
				events, List.of(), hidden, List.of());
	}

	/**
	 * Makes every event code unique within a region: scenes are visited in generation order and the
	 * first occurrence of each code is kept. A dropped event's hidden-content reference is removed
	 * too, so each rebuilt state stays valid.
	 */
	static List<SceneState> dropDuplicateEvents(List<SceneState> statesInGenerationOrder) {
		Set<String> seen = new HashSet<>();
		List<SceneState> result = new ArrayList<>();
		for (SceneState state : statesInGenerationOrder) {
			List<SceneEvent> kept = new ArrayList<>();
			Set<String> droppedIds = new HashSet<>();
			for (SceneEvent event : state.activeEvents()) {
				if (seen.add(event.definitionCode())) {
					kept.add(event);
				} else {
					droppedIds.add(event.id());
				}
			}
			if (droppedIds.isEmpty()) {
				result.add(state);
				continue;
			}
			List<HiddenContentRef> hidden = state.hiddenContent().stream()
					.filter(ref -> !(ref.kind() == HiddenContentKind.EVENT && droppedIds.contains(ref.localId())))
					.toList();
			result.add(new SceneState(state.zones(), state.connections(), state.entities(), state.objects(),
					state.hazards(), state.exits(), kept, state.environmentFlags(), hidden, state.discoveredFacts()));
		}
		return List.copyOf(result);
	}
}
