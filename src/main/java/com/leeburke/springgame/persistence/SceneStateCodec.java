package com.leeburke.springgame.persistence;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import com.leeburke.springgame.shared.StrictJson;
import com.leeburke.springgame.world.HiddenContentKind;
import com.leeburke.springgame.world.HiddenContentRef;
import com.leeburke.springgame.world.SceneEntity;
import com.leeburke.springgame.world.SceneEvent;
import com.leeburke.springgame.world.SceneExit;
import com.leeburke.springgame.world.SceneHazard;
import com.leeburke.springgame.world.SceneObject;
import com.leeburke.springgame.world.SceneState;
import com.leeburke.springgame.world.SceneZone;
import com.leeburke.springgame.world.ZoneConnection;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The storage contract for {@link SceneState} JSONB documents.
 * <p>
 * Uses explicit document records rather than serializing domain records, so the domain stays
 * annotation-free and the stored shape cannot change silently. The document holds only dynamic
 * scene contents; scene metadata is relational. The structure version is stored relationally in
 * {@code state_schema_version}.
 * <p>
 * Decoding is strict: an unsupported version, malformed JSON, unknown or missing fields, invalid
 * enum values or a document that violates a {@link SceneState} invariant all raise
 * {@link PersistedStateException} naming the scene. A corrupt document never becomes a default scene.
 */
final class SceneStateCodec {

	static final int CURRENT_SCHEMA_VERSION = 1;

	private final JsonMapper mapper = StrictJson.createMapper();

	String encode(SceneState state) {
		Objects.requireNonNull(state, "state");
		return mapper.writeValueAsString(toDocument(state));
	}

	SceneState decode(UUID sceneId, int schemaVersion, String json) {
		if (schemaVersion != CURRENT_SCHEMA_VERSION) {
			throw new PersistedStateException("Scene " + sceneId + ": unsupported scene-state schema version " + schemaVersion
					+ " (supported: " + CURRENT_SCHEMA_VERSION + ")");
		}
		if (json == null) {
			throw new PersistedStateException("Scene " + sceneId + ": scene-state document is missing");
		}
		SceneStateDocument document;
		try {
			document = mapper.readValue(json, SceneStateDocument.class);
		} catch (JacksonException e) {
			throw new PersistedStateException("Scene " + sceneId + ": invalid scene-state document: " + e.getOriginalMessage(), e);
		}
		try {
			return toDomain(document);
		} catch (IllegalArgumentException | NullPointerException e) {
			throw new PersistedStateException("Scene " + sceneId + ": scene-state document violates scene invariants: " + e.getMessage(), e);
		}
	}

	// --- Stored document shape (version 1). Field names are the JSON contract. ---

	record SceneStateDocument(
			List<ZoneDocument> zones,
			List<ConnectionDocument> connections,
			List<PlacedDocument> entities,
			List<PlacedDocument> objects,
			List<PlacedDocument> hazards,
			List<ExitDocument> exits,
			List<PlacedDocument> activeEvents,
			List<String> environmentFlags,
			List<HiddenContentDocument> hiddenContent,
			List<String> discoveredFacts) {
	}

	record ZoneDocument(String id, String displayName) {
	}

	record ConnectionDocument(String id, String zoneA, String zoneB) {
	}

	/** Shared shape of placed content: entities, objects, hazards and active events. */
	record PlacedDocument(String id, String definitionCode, String zoneId) {
	}

	record ExitDocument(String id, String zoneId, UUID destinationSceneId) {
	}

	record HiddenContentDocument(HiddenContentKind kind, String localId) {
	}

	private static SceneStateDocument toDocument(SceneState state) {
		return new SceneStateDocument(
				map(state.zones(), z -> new ZoneDocument(z.id(), z.displayName())),
				map(state.connections(), c -> new ConnectionDocument(c.id(), c.zoneA(), c.zoneB())),
				map(state.entities(), e -> new PlacedDocument(e.id(), e.definitionCode(), e.zoneId())),
				map(state.objects(), o -> new PlacedDocument(o.id(), o.definitionCode(), o.zoneId())),
				map(state.hazards(), h -> new PlacedDocument(h.id(), h.definitionCode(), h.zoneId())),
				map(state.exits(), x -> new ExitDocument(x.id(), x.zoneId(), x.destinationSceneId())),
				map(state.activeEvents(), v -> new PlacedDocument(v.id(), v.definitionCode(), v.zoneId())),
				state.environmentFlags(),
				map(state.hiddenContent(), r -> new HiddenContentDocument(r.kind(), r.localId())),
				state.discoveredFacts());
	}

	private static SceneState toDomain(SceneStateDocument d) {
		return new SceneState(
				map(d.zones(), z -> new SceneZone(z.id(), z.displayName())),
				map(d.connections(), c -> new ZoneConnection(c.id(), c.zoneA(), c.zoneB())),
				map(d.entities(), e -> new SceneEntity(e.id(), e.definitionCode(), e.zoneId())),
				map(d.objects(), o -> new SceneObject(o.id(), o.definitionCode(), o.zoneId())),
				map(d.hazards(), h -> new SceneHazard(h.id(), h.definitionCode(), h.zoneId())),
				map(d.exits(), x -> new SceneExit(x.id(), x.zoneId(), x.destinationSceneId())),
				map(d.activeEvents(), v -> new SceneEvent(v.id(), v.definitionCode(), v.zoneId())),
				d.environmentFlags(),
				map(d.hiddenContent(), r -> new HiddenContentRef(r.kind(), r.localId())),
				d.discoveredFacts());
	}

	private static <A, B> List<B> map(List<A> source, Function<A, B> mapper) {
		return Objects.requireNonNull(source, "document list").stream()
				.map(item -> mapper.apply(Objects.requireNonNull(item, "document list element")))
				.toList();
	}
}
