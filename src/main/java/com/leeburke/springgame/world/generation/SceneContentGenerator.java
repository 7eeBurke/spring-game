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
 * Then the exits are placed. A way in (back toward the region's first scene, or out to the road)
 * leaves from the archetype's entrance zone. Every other exit goes deeper: it leaves from one of the
 * other exit zones, drawn uniformly among those not yet holding a deeper exit while any remain (so
 * ways on spread out), or from the entrance if the archetype has no other exit zone. Exits are
 * never hidden, and no zone is ever hidden.
 */
final class SceneContentGenerator {

	/**
	 * An exit to create: its local ID, destination scene, and whether it is a way in (it leads back
	 * toward the region's first scene, or out to the road), which leaves from the entrance zone.
	 */
	record ExitSpec(String id, UUID destinationSceneId, boolean inward) {
		/** A way deeper. */
		ExitSpec(String id, UUID destinationSceneId) {
			this(id, destinationSceneId, false);
		}
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
		List<String> deeperZones = archetype.exitZones().stream().filter(z -> !z.equals(archetype.entranceZone())).toList();
		Set<String> used = new HashSet<>();
		for (ExitSpec exit : exits) {
			String zone;
			if (exit.inward() || deeperZones.isEmpty()) {
				zone = archetype.entranceZone();
			} else {
				List<String> free = deeperZones.stream().filter(z -> !used.contains(z)).toList();
				List<String> choices = free.isEmpty() ? deeperZones : free;
				zone = choices.get(rng.nextInt(choices.size()));
				used.add(zone);
			}
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
