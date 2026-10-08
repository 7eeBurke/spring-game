package com.leeburke.springgame.ai.interpreter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.leeburke.springgame.shared.StrictJson;

import tools.jackson.databind.JsonNode;

/** The structured-output schema is strict, complete and free of mechanical fields. */
class ActionDocumentSchemaTest {

	private static final List<String> FORBIDDEN = List.of("stat", "suitability", "dc", "difficulty", "roll", "success",
			"degree", "damage", "trauma", "contact", "effectiveness", "hp", "injury", "condition", "reward", "hidden");

	/** Vocabulary words that only look mechanical: spoken content and the parry contact point. */
	private static final Set<String> ALLOWED = Set.of("content", "parrycontact");

	private final JsonNode schema = StrictJson.createMapper().readTree(ActionDocumentSchema.json());

	@Test
	void everyObjectRequiresAllPropertiesAndForbidsOthers() {
		List<JsonNode> objects = new ArrayList<>();
		collectObjects(schema, objects);
		assertThat(objects).hasSizeGreaterThan(10);
		for (JsonNode object : objects) {
			Set<String> properties = new HashSet<>(object.get("properties").propertyNames());
			Set<String> required = new HashSet<>();
			object.get("required").forEach(name -> required.add(name.asString()));
			assertThat(required).isEqualTo(properties);
			assertThat(object.get("additionalProperties").asBoolean()).isFalse();
		}
	}

	@Test
	void rootFieldsArePinned() {
		assertThat(schema.get("type").asString()).isEqualTo("object");
		assertThat(schema.get("properties").propertyNames())
				.containsExactly("schemaVersion", "supported", "responseToAttack", "confidence", "steps", "unresolved");
		JsonNode step = schema.get("properties").get("steps").get("items");
		assertThat(step.get("properties").propertyNames()).containsExactly("relation", "action", "attack", "defend", "move",
				"interact", "observe", "useAbility", "useItem", "communicate");
	}

	@Test
	void noPropertyCarriesMechanics() {
		Set<String> names = new HashSet<>();
		collectPropertyNames(schema, names);
		for (String name : names) {
			String lower = name.toLowerCase(Locale.ROOT);
			assertThat(FORBIDDEN).as(name).noneMatch(word -> lower.contains(word) && !ALLOWED.contains(lower));
		}
		assertThat(names).contains("target", "weapon", "method", "content");
	}

	@Test
	void documentRecordsHaveNoMechanicalComponents() {
		Set<Class<?>> reachable = new HashSet<>();
		InterpretationContextTest.collect(ActionDocument.class, reachable);
		reachable.stream().filter(Class::isRecord).forEach(type -> {
			for (var component : type.getRecordComponents()) {
				String lower = component.getName().toLowerCase(Locale.ROOT);
				assertThat(FORBIDDEN).as(type.getSimpleName() + "." + component.getName())
						.noneMatch(word -> lower.contains(word) && !ALLOWED.contains(lower));
			}
		});
	}

	private static void collectObjects(JsonNode node, List<JsonNode> out) {
		if (node.isObject()) {
			if (node.has("type") && node.get("type").isString() && node.get("type").asString().equals("object")) {
				out.add(node);
			}
			node.forEach(child -> collectObjects(child, out));
		} else if (node.isArray()) {
			node.forEach(child -> collectObjects(child, out));
		}
	}

	private static void collectPropertyNames(JsonNode node, Set<String> out) {
		if (node.isObject()) {
			if (node.has("properties")) {
				out.addAll(node.get("properties").propertyNames());
			}
			node.forEach(child -> collectPropertyNames(child, out));
		} else if (node.isArray()) {
			node.forEach(child -> collectPropertyNames(child, out));
		}
	}
}
