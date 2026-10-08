package com.leeburke.springgame.ai.interpreter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.leeburke.springgame.action.ActionApproach;
import com.leeburke.springgame.action.ActionIntent;
import com.leeburke.springgame.action.ActionPayload;
import com.leeburke.springgame.action.ActionStep;
import com.leeburke.springgame.action.ActionTarget;
import com.leeburke.springgame.action.AttackPurpose;
import com.leeburke.springgame.action.AttackTemplate;
import com.leeburke.springgame.action.CarriedKind;
import com.leeburke.springgame.action.CarriedReference;
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
import com.leeburke.springgame.action.UnresolvedReference;
import com.leeburke.springgame.action.WeaponMethod;
import com.leeburke.springgame.ai.interpreter.ActionDocumentMapper.DocumentMappingException;
import com.leeburke.springgame.ai.interpreter.ActionDocumentParser.DocumentParseException;
import com.leeburke.springgame.mechanics.BodyPart;

/** Strict parsing of the AI action document and its mapping to the exact Stage 10 intent. */
class ActionDocumentTest {

	private static final TargetSpecificity EXPLICIT = TargetSpecificity.EXPLICIT;
	private final ActionDocumentMapper mapper = new ActionDocumentMapper(InterpreterFixtures.setup().aliases());

	// --- JSON builders ---

	static String target(String kind, String alias, String bodyPart) {
		return target(kind, alias, bodyPart, kind.equals("NONE") ? "UNSPECIFIED" : "EXPLICIT");
	}

	static String target(String kind, String alias, String bodyPart, String specificity) {
		return "{\"kind\":\"" + kind + "\",\"alias\":" + quoted(alias) + ",\"bodyPart\":" + quoted(bodyPart)
				+ ",\"specificity\":\"" + specificity + "\"}";
	}

	static String none() {
		return target("NONE", null, null);
	}

	static String step(String relation, String action, String payloadName, String payload) {
		List<String> slots = List.of("attack", "defend", "move", "interact", "observe", "useAbility", "useItem", "communicate");
		StringBuilder json = new StringBuilder("{\"relation\":\"" + relation + "\",\"action\":\"" + action + "\"");
		for (String slot : slots) {
			json.append(",\"").append(slot).append("\":").append(slot.equals(payloadName) ? payload : "null");
		}
		return json.append('}').toString();
	}

	static String document(String responseToAttack, List<String> steps, String unresolved) {
		return "{\"schemaVersion\":1,\"supported\":true,\"responseToAttack\":" + quoted(responseToAttack)
				+ ",\"confidence\":\"HIGH\",\"steps\":[" + String.join(",", steps) + "],\"unresolved\":[" + unresolved + "]}";
	}

	static String document(String... steps) {
		return document(null, List.of(steps), "");
	}

	static String attack(String targetJson) {
		return step("START", "ATTACK", "attack", "{\"weapon\":\"weapon_1\",\"method\":\"SLASH\",\"template\":\"HORIZONTAL_SWING\","
				+ "\"target\":" + targetJson + ",\"approach\":\"NORMAL\",\"purpose\":\"DAMAGE\"}");
	}

	private static String quoted(String value) {
		return value == null ? "null" : "\"" + value + "\"";
	}

	private ActionIntent map(String json) {
		return mapper.toIntent(ActionDocumentParser.parse(json));
	}

	private static ActionIntent intent(Optional<String> response, List<UnresolvedReference> unresolved, ActionPayload... payloads) {
		List<ActionStep> steps = new ArrayList<>();
		for (int i = 0; i < payloads.length; i++) {
			steps.add(new ActionStep("s" + (i + 1), i + 1, i == 0 ? StepRelation.START : StepRelation.THEN, payloads[i]));
		}
		return new ActionIntent(1, response, steps, InterpretationConfidence.HIGH, unresolved);
	}

	private static ActionIntent intent(ActionPayload... payloads) {
		return intent(Optional.empty(), List.of(), payloads);
	}

	// --- Every action type and target kind ---

	@Test
	void attackOnAnEntityBodyPart() {
		assertThat(map(document(attack(target("ENTITY", "entity_1", "HEAD"))))).isEqualTo(intent(new ActionPayload.AttackPayload(
				"weapon_1", WeaponMethod.SLASH, AttackTemplate.HORIZONTAL_SWING,
				new ActionTarget.EntityTarget("acolyte_1", Optional.of(BodyPart.HEAD), EXPLICIT), ActionApproach.NORMAL,
				AttackPurpose.DAMAGE)));
	}

	@Test
	void attackOnObjectAndHazard() {
		assertThat(((ActionPayload.AttackPayload) map(document(attack(target("OBJECT", "object_1", null)))).steps().getFirst()
				.payload()).target()).isEqualTo(new ActionTarget.ObjectTarget("pew_1", EXPLICIT));
		assertThat(((ActionPayload.AttackPayload) map(document(attack(target("HAZARD", "hazard_1", null)))).steps().getFirst()
				.payload()).target()).isEqualTo(new ActionTarget.HazardTarget("fire_1", EXPLICIT));
	}

	@Test
	void defenseRespondingToAnIncomingAttack() {
		String defend = step("START", "DEFEND", "defend",
				"{\"method\":\"PARRY\",\"evadeType\":\"UNSPECIFIED\",\"parryContact\":\"WEAPON\",\"cover\":" + none() + "}");
		assertThat(map(document("attack_1", List.of(defend), ""))).isEqualTo(intent(Optional.of(InterpreterFixtures.BACKEND_ATTACK),
				List.of(), new ActionPayload.DefendPayload(DefenseMethod.PARRY, EvadeType.UNSPECIFIED, ParryContact.WEAPON,
						ActionTarget.unspecified())));
	}

	@Test
	void moveToAZoneAndThroughAnExit() {
		String zone = step("START", "MOVE", "move", "{\"movementType\":\"REPOSITION\",\"target\":" + target("ZONE", "zone_1", null)
				+ ",\"goal\":\"NONE\",\"approach\":\"NORMAL\"}");
		String exit = step("THEN", "MOVE", "move", "{\"movementType\":\"ADVANCE\",\"target\":" + target("EXIT", "exit_1", null)
				+ ",\"goal\":\"NONE\",\"approach\":\"QUICK\"}");
		assertThat(map(document(zone, exit))).isEqualTo(intent(
				new ActionPayload.MovePayload(MovementType.REPOSITION, new ActionTarget.ZoneTarget("aisle", EXPLICIT), RelativeGoal.NONE,
						ActionApproach.NORMAL),
				new ActionPayload.MovePayload(MovementType.ADVANCE, new ActionTarget.ExitTarget("north_door", EXPLICIT),
						RelativeGoal.NONE, ActionApproach.QUICK)));
	}

	@Test
	void interactionsWithAndWithoutCarriedThings() {
		String push = step("START", "INTERACT", "interact", "{\"kind\":\"PUSH\",\"target\":" + target("OBJECT", "object_1", null)
				+ ",\"carried\":null,\"approach\":\"FORCEFUL\"}");
		String drop = step("THEN", "INTERACT", "interact", "{\"kind\":\"DROP\",\"target\":" + none()
				+ ",\"carried\":{\"kind\":\"ITEM\",\"alias\":\"item_2\"},\"approach\":\"NORMAL\"}");
		assertThat(map(document(push, drop))).isEqualTo(intent(
				new ActionPayload.InteractPayload(InteractionKind.PUSH, new ActionTarget.ObjectTarget("pew_1", EXPLICIT),
						Optional.empty(), ActionApproach.FORCEFUL),
				new ActionPayload.InteractPayload(InteractionKind.DROP, ActionTarget.unspecified(),
						Optional.of(new CarriedReference(CarriedKind.ITEM, "item_2")), ActionApproach.NORMAL)));
	}

	@Test
	void observeAbilityItemAndCommunication() {
		String observe = step("START", "OBSERVE", "observe", "{\"kind\":\"INSPECT\",\"target\":" + target("HAZARD", "hazard_1", null) + "}");
		String ability = step("THEN", "USE_ABILITY", "useAbility", "{\"ability\":\"ability_1\",\"target\":"
				+ target("SELF", null, "CHEST") + "}");
		String item = step("THEN", "USE_ITEM", "useItem", "{\"item\":\"item_1\",\"target\":" + target("SELF", null, null) + "}");
		String speak = step("THEN", "COMMUNICATE", "communicate", "{\"kind\":\"THREATEN\",\"content\":\"Back away.\",\"target\":"
				+ target("ENTITY", "entity_1", null) + "}");
		assertThat(map(document(observe, ability, item, speak))).isEqualTo(intent(
				new ActionPayload.ObservePayload(ObservationKind.INSPECT, new ActionTarget.HazardTarget("fire_1", EXPLICIT)),
				new ActionPayload.UseAbilityPayload("ability_1", new ActionTarget.SelfTarget(Optional.of(BodyPart.CHEST), EXPLICIT)),
				new ActionPayload.UseItemPayload("item_1", new ActionTarget.SelfTarget(Optional.empty(), EXPLICIT)),
				new ActionPayload.CommunicatePayload(CommunicationKind.THREATEN, "Back away.",
						new ActionTarget.EntityTarget("acolyte_1", Optional.empty(), EXPLICIT))));
	}

	@Test
	void everyStepRelationIsKept() {
		String search = "{\"kind\":\"SEARCH\",\"target\":" + none() + "}";
		ActionIntent intent = map(document(step("START", "OBSERVE", "observe", search), step("THEN", "OBSERVE", "observe", search),
				step("IF_PREVIOUS_SUCCEEDS", "OBSERVE", "observe", search), step("WHILE", "OBSERVE", "observe", search)));
		assertThat(intent.steps()).extracting(ActionStep::relation).containsExactly(StepRelation.START, StepRelation.THEN,
				StepRelation.IF_PREVIOUS_SUCCEEDS, StepRelation.WHILE);
		assertThat(intent.steps()).extracting(ActionStep::id).containsExactly("s1", "s2", "s3", "s4");
	}

	@Test
	void unresolvedPhrasesKeepTheirStep() {
		String json = document(null, List.of(attack(target("ENTITY", "entity_1", null))),
				"{\"stepNumber\":1,\"phrase\":\"the other one\"},{\"stepNumber\":null,\"phrase\":\"the bell\"}");
		assertThat(map(json).unresolvedReferences()).containsExactly(new UnresolvedReference(Optional.of("s1"), "the other one"),
				new UnresolvedReference(Optional.empty(), "the bell"));
	}

	@Test
	void unsupportedDocumentHasNoSteps() {
		ActionDocument document = ActionDocumentParser.parse(
				"{\"schemaVersion\":1,\"supported\":false,\"responseToAttack\":null,\"confidence\":\"HIGH\",\"steps\":[],\"unresolved\":[]}");
		assertThat(document.supported()).isFalse();
	}

	// --- Strict parsing ---

	@ParameterizedTest
	@ValueSource(strings = {
			// unknown field
			"{\"schemaVersion\":1,\"supported\":true,\"responseToAttack\":null,\"confidence\":\"HIGH\",\"steps\":[STEP],\"unresolved\":[],\"damage\":999}",
			// missing field
			"{\"schemaVersion\":1,\"supported\":true,\"confidence\":\"HIGH\",\"steps\":[STEP],\"unresolved\":[]}",
			// wrong primitive type
			"{\"schemaVersion\":\"1\",\"supported\":true,\"responseToAttack\":null,\"confidence\":\"HIGH\",\"steps\":[STEP],\"unresolved\":[]}",
			// null where prohibited
			"{\"schemaVersion\":1,\"supported\":true,\"responseToAttack\":null,\"confidence\":null,\"steps\":[STEP],\"unresolved\":[]}",
			// unknown enum
			"{\"schemaVersion\":1,\"supported\":true,\"responseToAttack\":null,\"confidence\":\"CERTAIN\",\"steps\":[STEP],\"unresolved\":[]}",
			// wrongly cased enum
			"{\"schemaVersion\":1,\"supported\":true,\"responseToAttack\":null,\"confidence\":\"high\",\"steps\":[STEP],\"unresolved\":[]}",
			// unsupported schema version
			"{\"schemaVersion\":2,\"supported\":true,\"responseToAttack\":null,\"confidence\":\"HIGH\",\"steps\":[STEP],\"unresolved\":[]}",
			// supported but no steps
			"{\"schemaVersion\":1,\"supported\":true,\"responseToAttack\":null,\"confidence\":\"HIGH\",\"steps\":[],\"unresolved\":[]}",
			// unsupported but with steps
			"{\"schemaVersion\":1,\"supported\":false,\"responseToAttack\":null,\"confidence\":\"HIGH\",\"steps\":[STEP],\"unresolved\":[]}",
			// trailing content
			"{\"schemaVersion\":1,\"supported\":true,\"responseToAttack\":null,\"confidence\":\"HIGH\",\"steps\":[STEP],\"unresolved\":[]} {}",
			"not json" })
	void malformedDocumentsAreRejected(String template) {
		String json = template.replace("STEP", attack(target("ENTITY", "entity_1", null)));
		assertThatThrownBy(() -> ActionDocumentParser.parse(json)).isInstanceOf(DocumentParseException.class);
	}

	@Test
	void stepPayloadMustMatchItsActionAndBeAlone() {
		String mismatched = attack(target("ENTITY", "entity_1", null)).replace("\"action\":\"ATTACK\"", "\"action\":\"DEFEND\"");
		String twoPayloads = attack(target("ENTITY", "entity_1", null)).replace("\"observe\":null",
				"\"observe\":{\"kind\":\"SEARCH\",\"target\":" + none() + "}");
		assertThatThrownBy(() -> ActionDocumentParser.parse(document(mismatched))).isInstanceOf(DocumentParseException.class);
		assertThatThrownBy(() -> ActionDocumentParser.parse(document(twoPayloads))).isInstanceOf(DocumentParseException.class)
				.hasMessageContaining("exactly one payload");
	}

	@Test
	void targetShapeRulesAreEnforced() {
		assertThatThrownBy(() -> ActionDocumentParser.parse(document(attack(target("SELF", "entity_1", null)))))
				.isInstanceOf(DocumentParseException.class);
		assertThatThrownBy(() -> ActionDocumentParser.parse(document(attack(target("ENTITY", null, null)))))
				.isInstanceOf(DocumentParseException.class);
		assertThatThrownBy(() -> ActionDocumentParser.parse(document(attack(target("OBJECT", "object_1", "HEAD")))))
				.isInstanceOf(DocumentParseException.class);
		assertThatThrownBy(() -> ActionDocumentParser.parse(document(attack(target("NONE", null, "HEAD")))))
				.isInstanceOf(DocumentParseException.class);
	}

	// --- Target specificity ---

	@Test
	void namedTargetMarkedUnspecifiedIsRejectedWithGuidanceAndAPath() {
		assertThatThrownBy(() -> ActionDocumentParser.parse(document(attack(target("ENTITY", "entity_1", null, "UNSPECIFIED")))))
				.isInstanceOf(DocumentParseException.class)
				.hasMessageStartingWith("steps[0].attack.target: ")
				.hasMessageContaining("EXPLICIT (the player named it)")
				.hasMessageContaining("INFERRED (you identified it from context)")
				.hasMessageContaining("UNSPECIFIED is only for kind NONE")
				.message().doesNotContain("ActionDocument", "com.leeburke", "Cannot construct");
	}

	@Test
	void selfMarkedUnspecifiedAndNoneMarkedExplicitAreRejected() {
		String selfItem = step("START", "USE_ITEM", "useItem", "{\"item\":\"item_1\",\"target\":"
				+ target("SELF", null, null, "UNSPECIFIED") + "}");
		assertThatThrownBy(() -> ActionDocumentParser.parse(document(selfItem))).isInstanceOf(DocumentParseException.class)
				.hasMessageContaining("a SELF target identifies something");
		String search = step("START", "OBSERVE", "observe", "{\"kind\":\"SEARCH\",\"target\":"
				+ target("NONE", null, null, "EXPLICIT") + "}");
		assertThatThrownBy(() -> ActionDocumentParser.parse(document(search))).isInstanceOf(DocumentParseException.class)
				.hasMessageContaining("steps[0].observe.target: a NONE target identifies nothing");
	}

	@Test
	void inferredTargetsAndUnspecifiedNoneAreAccepted() {
		assertThat(((ActionPayload.AttackPayload) map(document(attack(target("ENTITY", "entity_1", null, "INFERRED")))).steps()
				.getFirst().payload()).target())
				.isEqualTo(new ActionTarget.EntityTarget("acolyte_1", Optional.empty(), TargetSpecificity.INFERRED));
		String search = step("START", "OBSERVE", "observe", "{\"kind\":\"SEARCH\",\"target\":" + none() + "}");
		assertThat(map(document(search)).steps().getFirst().payload())
				.isEqualTo(new ActionPayload.ObservePayload(ObservationKind.SEARCH, ActionTarget.unspecified()));
	}

	@Test
	void otherParseProblemsAlsoCarryTheirPath() {
		String badMethod = attack(target("ENTITY", "entity_1", null)).replace("\"SLASH\"", "\"BITE\"");
		assertThatThrownBy(() -> ActionDocumentParser.parse(document(badMethod))).isInstanceOf(DocumentParseException.class)
				.hasMessageStartingWith("steps[0].attack.method: ");
	}

	// --- Alias mapping ---

	@Test
	void aliasOfTheWrongKindIsRejected() {
		assertThatThrownBy(() -> map(document(attack(target("ENTITY", "object_1", null)))))
				.isInstanceOf(DocumentMappingException.class).hasMessageContaining("'object_1' is a object alias");
	}

	@Test
	void unknownAliasIsRejected() {
		assertThatThrownBy(() -> map(document(attack(target("ENTITY", "entity_9", null)))))
				.isInstanceOf(DocumentMappingException.class).hasMessageContaining("'entity_9' is not in the context");
	}

	@Test
	void backendIdsAndMalformedAliasesAreRejected() {
		for (String alias : List.of("acolyte_1", "entity_0", "Entity_1", "entity_01", "")) {
			assertThatThrownBy(() -> map(document(attack(target("ENTITY", alias, null))))).as(alias)
					.isInstanceOf(DocumentMappingException.class).hasMessageContaining("is not a valid alias");
		}
	}

	@Test
	void ownedAliasesAreTyped() {
		String itemAsWeapon = attack(target("ENTITY", "entity_1", null)).replace("\"weapon_1\"", "\"item_1\"");
		assertThatThrownBy(() -> map(document(itemAsWeapon))).isInstanceOf(DocumentMappingException.class)
				.hasMessageContaining("'item_1' is a item alias");
	}

	@Test
	void unknownIncomingAttackAndOutOfRangeUnresolvedStepAreRejected() {
		assertThatThrownBy(() -> map(document("attack_2", List.of(attack(target("ENTITY", "entity_1", null))), "")))
				.isInstanceOf(DocumentMappingException.class).hasMessageContaining("responseToAttack");
		assertThatThrownBy(() -> map(document(null, List.of(attack(target("ENTITY", "entity_1", null))),
				"{\"stepNumber\":3,\"phrase\":\"that\"}")))
				.isInstanceOf(DocumentMappingException.class).hasMessageContaining("step 3");
	}

	@Test
	void mappingProblemsNeverMentionBackendReferences() {
		try {
			map(document("attack_2", List.of(attack(target("ENTITY", "entity_9", null))), ""));
		} catch (DocumentMappingException e) {
			assertThat(e.getMessage()).doesNotContain("acolyte_1", "pew_1", "aisle", InterpreterFixtures.BACKEND_ATTACK);
			return;
		}
		throw new AssertionError("expected a mapping failure");
	}
}
