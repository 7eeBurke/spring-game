package com.leeburke.springgame.ai.interpreter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import com.leeburke.springgame.action.WeaponMethod;
import com.leeburke.springgame.action.resolution.IncomingAttack;
import com.leeburke.springgame.ai.interpreter.ActionInterpretationResult.Failed;
import com.leeburke.springgame.ai.interpreter.ActionInterpretationResult.Failure;
import com.leeburke.springgame.ai.interpreter.ActionInterpretationResult.Interpreted;
import com.leeburke.springgame.ai.interpreter.ActionInterpretationResult.Source;
import com.leeburke.springgame.character.PlayerCharacterState;
import com.leeburke.springgame.character.ToolBelt;
import com.leeburke.springgame.character.ToolBeltEntry;
import com.leeburke.springgame.mechanics.BodyPart;

/** Every slash command form: ordinary Stage 10 intents, validated, never guessed. */
class CommandInterpreterTest {

	private static final TargetSpecificity EXPLICIT = TargetSpecificity.EXPLICIT;
	private static final ActionTarget ACOLYTE = new ActionTarget.EntityTarget("acolyte_1", Optional.empty(), EXPLICIT);

	private final CommandInterpreter commands = new CommandInterpreter();
	private final InterpretationSetup setup = InterpreterFixtures.setup();

	private ActionIntent interpreted(String command) {
		return interpreted(command, setup);
	}

	private ActionIntent interpreted(String command, InterpretationSetup setup) {
		ActionInterpretationResult result = commands.interpret(command, setup);
		assertThat(result).as(command).isInstanceOf(Interpreted.class);
		assertThat(((Interpreted) result).source()).isEqualTo(Source.COMMAND);
		return ((Interpreted) result).validated().intent();
	}

	private ActionPayload single(String command) {
		ActionIntent intent = interpreted(command);
		assertThat(intent.steps()).hasSize(1);
		assertThat(intent.confidence()).isEqualTo(InterpretationConfidence.HIGH);
		return intent.steps().getFirst().payload();
	}

	private Failed failed(String command, InterpretationSetup setup) {
		ActionInterpretationResult result = commands.interpret(command, setup);
		assertThat(result).as(command).isInstanceOf(Failed.class);
		Failed failed = (Failed) result;
		assertThat(failed.reason()).isEqualTo(Failure.INVALID_COMMAND);
		return failed;
	}

	// --- Attacks ---

	@Test
	void attackUsesTheOnlyWeaponDamageAndTheMethodsDefaultTemplate() {
		ActionIntent intent = interpreted("/attack entity_1 slash");
		assertThat(intent).isEqualTo(new ActionIntent(1, Optional.empty(), List.of(new ActionStep("s1", 1, StepRelation.START,
				new ActionPayload.AttackPayload("weapon_1", WeaponMethod.SLASH, AttackTemplate.HORIZONTAL_SWING, ACOLYTE,
						ActionApproach.NORMAL, AttackPurpose.DAMAGE))),
				InterpretationConfidence.HIGH, List.of()));
	}

	@ParameterizedTest
	@CsvSource({ "SLASH, HORIZONTAL_SWING", "THRUST, THRUST", "SMASH, OVERHEAD_STRIKE", "HOOK, HOOK_AND_PULL",
			"PROJECT, PROJECTED_ATTACK" })
	void defaultTemplatePerMethod(WeaponMethod method, AttackTemplate template) {
		ActionPayload.AttackPayload attack = (ActionPayload.AttackPayload) single("/attack entity_1 " + method.name().toLowerCase());
		assertThat(attack.method()).isEqualTo(method);
		assertThat(attack.template()).isEqualTo(template);
	}

	@Test
	void pommelStrikeHasNoDefaultTemplate() {
		assertThat(CommandInterpreter.DEFAULT_TEMPLATES).doesNotContainKey(WeaponMethod.POMMEL_STRIKE);
		assertThat(failed("/attack entity_1 pommel_strike", setup).details()).singleElement().asString()
				.contains("POMMEL_STRIKE needs an explicit template");
		ActionPayload.AttackPayload attack = (ActionPayload.AttackPayload) single("/attack entity_1 pommel_strike template thrust");
		assertThat(attack.method()).isEqualTo(WeaponMethod.POMMEL_STRIKE);
		assertThat(attack.template()).isEqualTo(AttackTemplate.THRUST);
	}

	@Test
	void attackOptionsInAnyOrderAndAnyCase() {
		assertThat(single("/ATTACK entity_1 Thrust purpose disarm part head template low_sweep with weapon_1"))
				.isEqualTo(new ActionPayload.AttackPayload("weapon_1", WeaponMethod.THRUST, AttackTemplate.LOW_SWEEP,
						new ActionTarget.EntityTarget("acolyte_1", Optional.of(BodyPart.HEAD), EXPLICIT), ActionApproach.NORMAL,
						AttackPurpose.DISARM));
	}

	@Test
	void attackOnObjectsAndHazards() {
		assertThat(((ActionPayload.AttackPayload) single("/attack object_1 smash")).target())
				.isEqualTo(new ActionTarget.ObjectTarget("pew_1", EXPLICIT));
		assertThat(((ActionPayload.AttackPayload) single("/attack hazard_1 smash")).target())
				.isEqualTo(new ActionTarget.HazardTarget("fire_1", EXPLICIT));
	}

	// --- Defense ---

	@Test
	void defenseRespondsToTheOnlyIncomingAttack() {
		ActionIntent intent = interpreted("/defend parry parry weapon");
		assertThat(intent.responseToAttack()).contains(InterpreterFixtures.BACKEND_ATTACK);
		assertThat(intent.steps().getFirst().payload()).isEqualTo(new ActionPayload.DefendPayload(DefenseMethod.PARRY,
				EvadeType.UNSPECIFIED, ParryContact.WEAPON, ActionTarget.unspecified()));
	}

	@Test
	void defenseOptions() {
		assertThat(single("/defend evade evade duck against attack_1")).isEqualTo(new ActionPayload.DefendPayload(
				DefenseMethod.EVADE, EvadeType.DUCK, ParryContact.UNSPECIFIED, ActionTarget.unspecified()));
		assertThat(single("/defend take_cover cover object_1")).isEqualTo(new ActionPayload.DefendPayload(DefenseMethod.TAKE_COVER,
				EvadeType.UNSPECIFIED, ParryContact.UNSPECIFIED, new ActionTarget.ObjectTarget("pew_1", EXPLICIT)));
	}

	@Test
	void defenseWithoutAnIncomingAttackHasNoResponse() {
		assertThat(interpreted("/defend block", InterpreterFixtures.setupWithoutAttack()).responseToAttack()).isEmpty();
	}

	@Test
	void severalIncomingAttacksNeedAgainst() {
		IncomingAttack second = new IncomingAttack("enemy_attack_9", "acolyte_1", AttackTemplate.THRUST, 12, 3, 1,
				com.leeburke.springgame.mechanics.Effectiveness.NORMAL, 0, 0, Optional.empty());
		InterpretationSetup two = new InterpretationContextBuilder(InterpreterFixtures.WORLD).build(InterpreterFixtures.view(),
				InterpreterFixtures.player(), List.of(InterpreterFixtures.incoming(), second));

		assertThat(failed("/defend block", two).details()).singleElement().asString().contains("against attack_N");
		assertThat(interpreted("/defend block against attack_2", two).responseToAttack()).contains("enemy_attack_9");
	}

	// --- Movement and observation ---

	@Test
	void movementCommands() {
		assertThat(single("/move zone_1")).isEqualTo(new ActionPayload.MovePayload(MovementType.REPOSITION,
				new ActionTarget.ZoneTarget("aisle", EXPLICIT), RelativeGoal.NONE, ActionApproach.NORMAL));
		assertThat(single("/move exit_1")).isEqualTo(new ActionPayload.MovePayload(MovementType.ADVANCE,
				new ActionTarget.ExitTarget("north_door", EXPLICIT), RelativeGoal.NONE, ActionApproach.NORMAL));
		assertThat(single("/hold")).isEqualTo(new ActionPayload.MovePayload(MovementType.HOLD_POSITION, ActionTarget.unspecified(),
				RelativeGoal.NONE, ActionApproach.NORMAL));
	}

	@Test
	void observationCommands() {
		assertThat(single("/search")).isEqualTo(new ActionPayload.ObservePayload(ObservationKind.SEARCH, ActionTarget.unspecified()));
		assertThat(single("/listen")).isEqualTo(new ActionPayload.ObservePayload(ObservationKind.LISTEN, ActionTarget.unspecified()));
		assertThat(single("/watch entity_1")).isEqualTo(new ActionPayload.ObservePayload(ObservationKind.WATCH, ACOLYTE));
		assertThat(single("/inspect hazard_1")).isEqualTo(new ActionPayload.ObservePayload(ObservationKind.INSPECT,
				new ActionTarget.HazardTarget("fire_1", EXPLICIT)));
	}

	// --- Interaction, items and abilities ---

	@ParameterizedTest
	@CsvSource({ "push, PUSH", "pull, PULL", "break, BREAK", "open, OPEN", "close, CLOSE", "pickup, PICK_UP", "jam, JAM",
			"ignite, IGNITE", "extinguish, EXTINGUISH" })
	void interactionCommands(String verb, InteractionKind kind) {
		assertThat(single("/" + verb + " object_1")).isEqualTo(new ActionPayload.InteractPayload(kind,
				new ActionTarget.ObjectTarget("pew_1", EXPLICIT), Optional.empty(), ActionApproach.NORMAL));
	}

	@Test
	void placeCarriedThings() {
		assertThat(single("/place item_2 on object_1")).isEqualTo(new ActionPayload.InteractPayload(InteractionKind.PLACE,
				new ActionTarget.ObjectTarget("pew_1", EXPLICIT), Optional.of(new CarriedReference(CarriedKind.ITEM, "item_2")),
				ActionApproach.NORMAL));
	}

	@Test
	void dropMapsToTheCarriedThing() {
		ActionIntent intent = new CommandInterpreter().parse("/drop weapon_1", setup);
		assertThat(intent.steps().getFirst().payload()).isEqualTo(new ActionPayload.InteractPayload(InteractionKind.DROP,
				ActionTarget.unspecified(), Optional.of(new CarriedReference(CarriedKind.WEAPON, "weapon_1")), ActionApproach.NORMAL));
	}

	@Test
	void itemAndAbilityCommands() {
		assertThat(single("/use item_1 on self")).isEqualTo(new ActionPayload.UseItemPayload("item_1",
				new ActionTarget.SelfTarget(Optional.empty(), EXPLICIT)));
		assertThat(single("/use item_2")).isEqualTo(new ActionPayload.UseItemPayload("item_2", ActionTarget.unspecified()));
		assertThat(single("/ability ability_1 on entity_1")).isEqualTo(new ActionPayload.UseAbilityPayload("ability_1", ACOLYTE));
	}

	// --- Speech ---

	@Test
	void speechKeepsTheRestOfTheLine() {
		assertThat(single("/threaten to entity_1 Back away,   now.")).isEqualTo(new ActionPayload.CommunicatePayload(
				CommunicationKind.THREATEN, "Back away,   now.", ACOLYTE));
		assertThat(single("/say Hello there")).isEqualTo(new ActionPayload.CommunicatePayload(CommunicationKind.SAY, "Hello there",
				ActionTarget.unspecified()));
		assertThat(single("/bargain to entity_1 Let me pass")).isEqualTo(new ActionPayload.CommunicatePayload(
				CommunicationKind.BARGAIN, "Let me pass", ACOLYTE));
	}

	@Test
	void quotedSpeechKeepsSeparatorsAndEscapes() {
		assertThat(single("/threaten to entity_1 \"Back; away && now\"")).isEqualTo(new ActionPayload.CommunicatePayload(
				CommunicationKind.THREATEN, "Back; away && now", ACOLYTE));
		assertThat(single("/say \"She said \\\"run\\\" twice\"")).isEqualTo(new ActionPayload.CommunicatePayload(
				CommunicationKind.SAY, "She said \"run\" twice", ActionTarget.unspecified()));
	}

	@Test
	void quotedSpeechChainsWithOtherCommands() {
		ActionIntent intent = interpreted("/say \"Stay back; I mean it\" ; /attack entity_1 slash && /hold");
		assertThat(intent.steps()).extracting(ActionStep::relation)
				.containsExactly(StepRelation.START, StepRelation.THEN, StepRelation.IF_PREVIOUS_SUCCEEDS);
		assertThat(((ActionPayload.CommunicatePayload) intent.steps().getFirst().payload()).content()).isEqualTo("Stay back; I mean it");
	}

	@Test
	void unquotedSpeechStillEndsAtASeparator() {
		ActionIntent intent = interpreted("/say Hello there ; /hold");
		assertThat(((ActionPayload.CommunicatePayload) intent.steps().getFirst().payload()).content()).isEqualTo("Hello there");
		assertThat(intent.steps()).hasSize(2);
	}

	@ParameterizedTest
	@ValueSource(strings = { "/say \"never closed", "/say \"\"", "/say \"   \"", "/say \"done\" extra words",
			"/threaten to entity_1 \"open ; /hold" })
	void malformedQuotationsAreRejected(String command) {
		assertThat(failed(command, setup).details()).isNotEmpty();
	}

	// --- Chaining ---

	@Test
	void semicolonIsThenAndDoubleAmpersandIsConditional() {
		ActionIntent intent = interpreted("/attack entity_1 slash ; /move zone_1 && /hold");
		assertThat(intent.steps()).extracting(ActionStep::relation)
				.containsExactly(StepRelation.START, StepRelation.THEN, StepRelation.IF_PREVIOUS_SUCCEEDS);
		assertThat(intent.steps()).extracting(ActionStep::id).containsExactly("s1", "s2", "s3");
	}

	// --- Rejections: nothing is guessed ---

	@ParameterizedTest
	@ValueSource(strings = { "/fly", "/attack entity_1", "/attack entity_1 bite", "/attack entity_9 slash", "/attack acolyte_1 slash",
			"/move entity_1", "/attack entity_1 slash part HEAD part ARM", "/attack object_1 slash part HEAD",
			"/attack entity_1 slash ; hold", "/say", "/say to entity_1", "/attack entity_1 slash speed fast", "/hold now",
			"/defend dodge", "/attack weapon_1 slash", "/", "/use entity_1", "/attack entity_1 slash with item_1" })
	void malformedCommandsAreRejected(String command) {
		assertThat(failed(command, setup).details()).isNotEmpty();
	}

	@Test
	void ambiguousWeaponMustBeNamed() {
		PlayerCharacterState base = InterpreterFixtures.player();
		PlayerCharacterState twoWeapons = new PlayerCharacterState(base.name(), base.stats(), base.fated(), base.maxHp(),
				base.currentHp(), base.body(), base.passive(), base.ability(), new ToolBelt(List.of(
						new ToolBeltEntry.Weapon(InterpreterFixtures.CONTENT.findWeapon("LONGSWORD").orElseThrow()),
						new ToolBeltEntry.Weapon(InterpreterFixtures.CONTENT.findWeapon("DAGGER").orElseThrow()))));
		InterpretationSetup setup = new InterpretationContextBuilder(InterpreterFixtures.WORLD)
				.build(InterpreterFixtures.view(), twoWeapons, List.of());

		assertThat(failed("/attack entity_1 thrust", setup).details()).singleElement().asString().contains("with weapon_N");
		assertThat(((ActionPayload.AttackPayload) interpreted("/attack entity_1 thrust with weapon_2", setup).steps().getFirst()
				.payload()).weaponRef()).isEqualTo("weapon_2");
	}

	@Test
	void stageTenStillValidatesCommands() {
		Failed failed = failed("/attack zone_1 slash", setup);
		assertThat(failed.details()).singleElement().asString().contains("SCHEMA_INVALID");
	}
}
