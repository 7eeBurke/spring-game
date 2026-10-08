package com.leeburke.springgame.ai.interpreter;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import com.leeburke.springgame.action.ActionApproach;
import com.leeburke.springgame.action.ActionType;
import com.leeburke.springgame.action.AttackPurpose;
import com.leeburke.springgame.action.AttackTemplate;
import com.leeburke.springgame.action.CarriedKind;
import com.leeburke.springgame.action.CommunicationKind;
import com.leeburke.springgame.action.DefenseMethod;
import com.leeburke.springgame.action.EvadeType;
import com.leeburke.springgame.action.InteractionKind;
import com.leeburke.springgame.action.InterpretationConfidence;
import com.leeburke.springgame.action.MovementType;
import com.leeburke.springgame.action.ObservationKind;
import com.leeburke.springgame.action.ParryContact;
import com.leeburke.springgame.action.RelativeGoal;
import com.leeburke.springgame.action.StepRelation;
import com.leeburke.springgame.action.TargetSpecificity;
import com.leeburke.springgame.action.WeaponMethod;
import com.leeburke.springgame.mechanics.BodyPart;
import com.leeburke.springgame.shared.StrictJson;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The JSON schema of {@link ActionDocument}, generated from the Stage 10 enums so the two cannot
 * drift. It uses only the strict structured-output subset: every property is required, objects
 * forbid additional properties, and optional values are nullable unions.
 */
public final class ActionDocumentSchema {

	public static final String NAME = "action_document_v1";

	private static final JsonMapper MAPPER = StrictJson.createMapper();

	private ActionDocumentSchema() {
	}

	public static String json() {
		return MAPPER.writeValueAsString(schema());
	}

	static ObjectNode schema() {
		ObjectNode target = described(object(map(
				"kind", described(enumOf(ActionDocument.TargetKind.values()),
						"What the target is. ENTITY, OBJECT, HAZARD, ZONE and EXIT name something in the context by alias; "
								+ "SELF is the player; NONE means no target."),
				"alias", described(nullable("string"),
						"The context alias whose prefix matches kind (entity_N for ENTITY, object_N for OBJECT, and so on). "
								+ "null for SELF and NONE."),
				"bodyPart", described(nullableEnum(BodyPart.values()),
						"Only for ENTITY or SELF when the player names a body part; otherwise null."),
				"specificity", described(enumOf(TargetSpecificity.values()),
						"EXPLICIT if the player named or clearly described the target; INFERRED if you identified it from "
								+ "context (for example 'it' or the only enemy). Every kind except NONE must be EXPLICIT or "
								+ "INFERRED. UNSPECIFIED is only for kind NONE.")),
				"kind", "alias", "bodyPart", "specificity"),
				"A target. Example: 'I slash at the acolyte' -> {kind: ENTITY, alias: entity_1, bodyPart: null, "
						+ "specificity: EXPLICIT}. No target -> {kind: NONE, alias: null, bodyPart: null, specificity: UNSPECIFIED}.");

		ObjectNode attack = object(map("weapon", type("string"), "method", enumOf(WeaponMethod.values()),
				"template", enumOf(AttackTemplate.values()), "target", target.deepCopy(),
				"approach", enumOf(ActionApproach.values()), "purpose", enumOf(AttackPurpose.values())),
				"weapon", "method", "template", "target", "approach", "purpose");
		ObjectNode defend = object(map("method", enumOf(DefenseMethod.values()), "evadeType", enumOf(EvadeType.values()),
				"parryContact", enumOf(ParryContact.values()), "cover", target.deepCopy()),
				"method", "evadeType", "parryContact", "cover");
		ObjectNode move = object(map("movementType", enumOf(MovementType.values()), "target", target.deepCopy(),
				"goal", enumOf(RelativeGoal.values()), "approach", enumOf(ActionApproach.values())),
				"movementType", "target", "goal", "approach");
		ObjectNode carried = object(map("kind", enumOf(CarriedKind.values()), "alias", type("string")), "kind", "alias");
		ObjectNode interact = object(map("kind", enumOf(InteractionKind.values()), "target", target.deepCopy(),
				"carried", nullableObject(carried), "approach", enumOf(ActionApproach.values())),
				"kind", "target", "carried", "approach");
		ObjectNode observe = object(map("kind", enumOf(ObservationKind.values()), "target", target.deepCopy()), "kind", "target");
		ObjectNode useAbility = object(map("ability", type("string"), "target", target.deepCopy()), "ability", "target");
		ObjectNode useItem = object(map("item", type("string"), "target", target.deepCopy()), "item", "target");
		ObjectNode communicate = object(map("kind", enumOf(CommunicationKind.values()), "content", type("string"),
				"target", target.deepCopy()), "kind", "content", "target");

		ObjectNode step = object(map("relation", enumOf(StepRelation.values()), "action", enumOf(ActionType.values()),
				"attack", nullableObject(attack), "defend", nullableObject(defend), "move", nullableObject(move),
				"interact", nullableObject(interact), "observe", nullableObject(observe),
				"useAbility", nullableObject(useAbility), "useItem", nullableObject(useItem),
				"communicate", nullableObject(communicate)),
				"relation", "action", "attack", "defend", "move", "interact", "observe", "useAbility", "useItem", "communicate");
		ObjectNode unresolved = object(map("stepNumber", nullable("integer"), "phrase", type("string")), "stepNumber", "phrase");

		return object(map("schemaVersion", type("integer"), "supported", type("boolean"),
				"responseToAttack", nullable("string"), "confidence", enumOf(InterpretationConfidence.values()),
				"steps", array(step), "unresolved", array(unresolved)),
				"schemaVersion", "supported", "responseToAttack", "confidence", "steps", "unresolved");
	}

	private static Map<String, JsonNode> map(Object... keysAndValues) {
		Map<String, JsonNode> map = new LinkedHashMap<>();
		for (int i = 0; i < keysAndValues.length; i += 2) {
			map.put((String) keysAndValues[i], (JsonNode) keysAndValues[i + 1]);
		}
		return map;
	}

	private static ObjectNode object(Map<String, JsonNode> properties, String... order) {
		ObjectNode node = MAPPER.createObjectNode();
		node.put("type", "object");
		ObjectNode props = node.putObject("properties");
		ArrayNode required = MAPPER.createArrayNode();
		for (String name : order) {
			props.set(name, properties.get(name));
			required.add(name);
		}
		if (properties.size() != order.length) {
			throw new IllegalStateException("Schema property order does not list every property");
		}
		node.set("required", required);
		node.put("additionalProperties", false);
		return node;
	}

	/** Adds guidance for the model; descriptions never change the schema's structure. */
	private static ObjectNode described(ObjectNode node, String description) {
		node.put("description", description);
		return node;
	}

	private static ObjectNode type(String type) {
		ObjectNode node = MAPPER.createObjectNode();
		node.put("type", type);
		return node;
	}

	private static ObjectNode nullable(String type) {
		ObjectNode node = MAPPER.createObjectNode();
		node.putArray("type").add(type).add("null");
		return node;
	}

	private static ObjectNode enumOf(Enum<?>[] values) {
		ObjectNode node = type("string");
		ArrayNode list = node.putArray("enum");
		Arrays.stream(values).forEach(v -> list.add(v.name()));
		return node;
	}

	private static ObjectNode nullableEnum(Enum<?>[] values) {
		ObjectNode node = MAPPER.createObjectNode();
		node.putArray("anyOf").add(enumOf(values)).add(type("null"));
		return node;
	}

	private static ObjectNode nullableObject(ObjectNode object) {
		ObjectNode node = MAPPER.createObjectNode();
		node.putArray("anyOf").add(object).add(type("null"));
		return node;
	}

	private static ObjectNode array(ObjectNode items) {
		ObjectNode node = type("array");
		node.set("items", items);
		return node;
	}
}
