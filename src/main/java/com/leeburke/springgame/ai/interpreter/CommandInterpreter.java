package com.leeburke.springgame.ai.interpreter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
import com.leeburke.springgame.action.validation.ActionValidator;
import com.leeburke.springgame.action.validation.ValidatedActionIntent;
import com.leeburke.springgame.ai.interpreter.ActionInterpretationResult.Failed;
import com.leeburke.springgame.ai.interpreter.ActionInterpretationResult.Failure;
import com.leeburke.springgame.ai.interpreter.ActionInterpretationResult.Interpreted;
import com.leeburke.springgame.ai.interpreter.ActionInterpretationResult.Source;
import com.leeburke.springgame.ai.interpreter.AliasTable.AliasException;
import com.leeburke.springgame.mechanics.BodyPart;

/**
 * The deterministic fallback interpreter: explicit slash commands using the same aliases the AI
 * context shows. It never guesses: anything it cannot parse exactly is {@code INVALID_COMMAND}.
 * Commands produce an ordinary {@link ActionIntent} (confidence HIGH, approach NORMAL, specificity
 * EXPLICIT) that still goes through Stage 10 validation, and resolve no mechanics.
 * <p>
 * Commands are joined with {@code ;} (THEN) or {@code &&} (IF_PREVIOUS_SUCCEEDS). See
 * docs/AI_CONTRACTS.md for the full syntax.
 */
public final class CommandInterpreter {

	/**
	 * Template used when an attack command names none (no mechanical effect today). POMMEL_STRIKE has
	 * no default: no template fits it naturally, so the command must name one.
	 */
	static final Map<WeaponMethod, AttackTemplate> DEFAULT_TEMPLATES = Map.of(
			WeaponMethod.SLASH, AttackTemplate.HORIZONTAL_SWING,
			WeaponMethod.THRUST, AttackTemplate.THRUST,
			WeaponMethod.SMASH, AttackTemplate.OVERHEAD_STRIKE,
			WeaponMethod.HOOK, AttackTemplate.HOOK_AND_PULL,
			WeaponMethod.PROJECT, AttackTemplate.PROJECTED_ATTACK);

	private static final Map<String, InteractionKind> INTERACTIONS = Map.of(
			"push", InteractionKind.PUSH, "pull", InteractionKind.PULL, "break", InteractionKind.BREAK,
			"open", InteractionKind.OPEN, "close", InteractionKind.CLOSE, "pickup", InteractionKind.PICK_UP,
			"take", InteractionKind.PICK_UP, "jam", InteractionKind.JAM, "ignite", InteractionKind.IGNITE,
			"extinguish", InteractionKind.EXTINGUISH);

	private static final TargetSpecificity EXPLICIT = TargetSpecificity.EXPLICIT;

	private final ActionValidator validator = new ActionValidator();

	public ActionInterpretationResult interpret(String input, InterpretationSetup setup) {
		ActionIntent intent;
		try {
			intent = parse(input.strip(), setup);
		} catch (CommandException | AliasException e) {
			return new Failed(Failure.INVALID_COMMAND, Optional.empty(), List.of(e.getMessage()));
		}
		Optional<ValidatedActionIntent> validated = validator.validated(intent, setup.validation());
		if (validated.isPresent()) {
			return new Interpreted(validated.get(), Source.COMMAND);
		}
		return new Failed(Failure.INVALID_COMMAND, Optional.empty(),
				RepairFeedback.validationProblems(validator.validate(intent, setup.validation()).errors()));
	}

	ActionIntent parse(String input, InterpretationSetup setup) {
		List<String> segments = new ArrayList<>();
		List<StepRelation> relations = new ArrayList<>(List.of(StepRelation.START));
		split(input, segments, relations);

		Context context = new Context(setup.aliases());
		List<ActionStep> steps = new ArrayList<>();
		for (int i = 0; i < segments.size(); i++) {
			String segment = segments.get(i);
			if (!segment.startsWith("/") || segment.length() < 2) {
				throw new CommandException("Every command starts with / (command " + (i + 1) + ")");
			}
			ActionPayload payload = command(segment.substring(1).strip().split("\\s+"), segment, context);
			steps.add(new ActionStep("s" + (i + 1), i + 1, relations.get(i), payload));
		}
		Optional<String> response = context.response;
		return new ActionIntent(ActionIntent.CURRENT_SCHEMA_VERSION, response, steps, InterpretationConfidence.HIGH, List.of());
	}

	/**
	 * Splits commands at {@code ;} (THEN) and {@code &&} (IF_PREVIOUS_SUCCEEDS), but never inside a
	 * double-quoted string; {@code \"} inside quotes is an escaped quote.
	 */
	static void split(String input, List<String> segments, List<StepRelation> relations) {
		StringBuilder current = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);
			if (quoted && c == '\\' && i + 1 < input.length() && input.charAt(i + 1) == '"') {
				current.append(c).append('"');
				i++;
			} else if (c == '"') {
				quoted = !quoted;
				current.append(c);
			} else if (!quoted && c == ';') {
				segments.add(current.toString().strip());
				relations.add(StepRelation.THEN);
				current.setLength(0);
			} else if (!quoted && c == '&' && i + 1 < input.length() && input.charAt(i + 1) == '&') {
				segments.add(current.toString().strip());
				relations.add(StepRelation.IF_PREVIOUS_SUCCEEDS);
				current.setLength(0);
				i++;
			} else {
				current.append(c);
			}
		}
		if (quoted) {
			throw new CommandException("A quotation is not closed");
		}
		segments.add(current.toString().strip());
	}

	private ActionPayload command(String[] tokens, String segment, Context context) {
		String verb = tokens[0].toLowerCase(Locale.ROOT);
		List<String> args = Arrays.asList(tokens).subList(1, tokens.length);
		if (INTERACTIONS.containsKey(verb)) {
			requireCount(verb, args, 1);
			return new ActionPayload.InteractPayload(INTERACTIONS.get(verb), context.target(args.getFirst()), Optional.empty(),
					ActionApproach.NORMAL);
		}
		Optional<CommunicationKind> speech = Arrays.stream(CommunicationKind.values())
				.filter(k -> k.name().toLowerCase(Locale.ROOT).equals(verb)).findFirst();
		if (speech.isPresent()) {
			return communicate(speech.get(), segment, args, context);
		}
		return switch (verb) {
			case "attack" -> attack(args, context);
			case "defend" -> defend(args, context);
			case "move" -> move(args, context);
			case "hold" -> {
				requireCount(verb, args, 0);
				yield new ActionPayload.MovePayload(MovementType.HOLD_POSITION, ActionTarget.unspecified(), RelativeGoal.NONE,
						ActionApproach.NORMAL);
			}
			case "search" -> observe(verb, ObservationKind.SEARCH, args, 0, context);
			case "listen" -> observe(verb, ObservationKind.LISTEN, args, 0, context);
			case "watch" -> observe(verb, ObservationKind.WATCH, args, args.isEmpty() ? 0 : 1, context);
			case "inspect" -> observe(verb, ObservationKind.INSPECT, args, 1, context);
			case "drop" -> {
				requireCount(verb, args, 1);
				yield new ActionPayload.InteractPayload(InteractionKind.DROP, ActionTarget.unspecified(),
						Optional.of(context.carried(args.getFirst())), ActionApproach.NORMAL);
			}
			case "place" -> {
				if (args.size() != 3 || !args.get(1).equalsIgnoreCase("on")) {
					throw new CommandException("Use /place <weapon or item> on <target>");
				}
				yield new ActionPayload.InteractPayload(InteractionKind.PLACE, context.target(args.get(2)),
						Optional.of(context.carried(args.getFirst())), ActionApproach.NORMAL);
			}
			case "use" -> new ActionPayload.UseItemPayload(context.aliases.resolve(AliasKind.ITEM, first(verb, args)),
					onTarget(verb, args, context));
			case "ability" -> new ActionPayload.UseAbilityPayload(context.aliases.resolve(AliasKind.ABILITY, first(verb, args)),
					onTarget(verb, args, context));
			default -> throw new CommandException("Unknown command /" + verb);
		};
	}

	private ActionPayload attack(List<String> args, Context context) {
		if (args.size() < 2) {
			throw new CommandException("Use /attack <target> <method> [template <T>] [part <P>] [with <weapon>] [purpose <P>]");
		}
		WeaponMethod method = enumValue(WeaponMethod.class, args.get(1), "method");
		Map<String, String> options = options(args.subList(2, args.size()), Set.of("template", "part", "with", "purpose"));
		AttackTemplate template;
		if (options.containsKey("template")) {
			template = enumValue(AttackTemplate.class, options.get("template"), "template");
		} else if (DEFAULT_TEMPLATES.containsKey(method)) {
			template = DEFAULT_TEMPLATES.get(method);
		} else {
			throw new CommandException(method + " needs an explicit template, for example 'template THRUST'");
		}
		Optional<BodyPart> part = Optional.ofNullable(options.get("part")).map(p -> enumValue(BodyPart.class, p, "body part"));
		String weapon = options.containsKey("with") ? context.aliases.resolve(AliasKind.WEAPON, options.get("with"))
				: context.onlyWeapon();
		AttackPurpose purpose = options.containsKey("purpose")
				? enumValue(AttackPurpose.class, options.get("purpose"), "purpose")
				: AttackPurpose.DAMAGE;
		ActionTarget target = context.target(args.getFirst());
		if (part.isPresent()) {
			if (!(target instanceof ActionTarget.EntityTarget entity)) {
				throw new CommandException("Only a creature target can take a body part");
			}
			target = new ActionTarget.EntityTarget(entity.entityId(), part, EXPLICIT);
		}
		return new ActionPayload.AttackPayload(weapon, method, template, target, ActionApproach.NORMAL, purpose);
	}

	private ActionPayload defend(List<String> args, Context context) {
		if (args.isEmpty()) {
			throw new CommandException("Use /defend <method> [evade <type>] [parry <contact>] [cover <object>] [against <attack>]");
		}
		DefenseMethod method = enumValue(DefenseMethod.class, args.getFirst(), "defense");
		Map<String, String> options = options(args.subList(1, args.size()), Set.of("evade", "parry", "cover", "against"));
		EvadeType evade = Optional.ofNullable(options.get("evade")).map(e -> enumValue(EvadeType.class, e, "evade type"))
				.orElse(EvadeType.UNSPECIFIED);
		ParryContact parry = Optional.ofNullable(options.get("parry")).map(p -> enumValue(ParryContact.class, p, "parry contact"))
				.orElse(ParryContact.UNSPECIFIED);
		ActionTarget cover = options.containsKey("cover")
				? new ActionTarget.ObjectTarget(context.aliases.resolve(AliasKind.OBJECT, options.get("cover")), EXPLICIT)
				: ActionTarget.unspecified();
		context.respondTo(Optional.ofNullable(options.get("against")));
		try {
			return new ActionPayload.DefendPayload(method, evade, parry, cover);
		} catch (IllegalArgumentException e) {
			throw new CommandException(e.getMessage());
		}
	}

	private ActionPayload move(List<String> args, Context context) {
		requireCount("move", args, 1);
		String alias = args.getFirst();
		AliasKind kind = AliasKind.kindOf(alias).orElseThrow(() -> new CommandException("'" + alias + "' is not a valid alias"));
		return switch (kind) {
			case ZONE -> new ActionPayload.MovePayload(MovementType.REPOSITION,
					new ActionTarget.ZoneTarget(context.aliases.resolve(AliasKind.ZONE, alias), EXPLICIT), RelativeGoal.NONE,
					ActionApproach.NORMAL);
			case EXIT -> new ActionPayload.MovePayload(MovementType.ADVANCE,
					new ActionTarget.ExitTarget(context.aliases.resolve(AliasKind.EXIT, alias), EXPLICIT), RelativeGoal.NONE,
					ActionApproach.NORMAL);
			// Toward an object: one step closer along known passages (a command never walks further).
			case OBJECT -> new ActionPayload.MovePayload(MovementType.CLOSE_DISTANCE,
					new ActionTarget.ObjectTarget(context.aliases.resolve(AliasKind.OBJECT, alias), EXPLICIT), RelativeGoal.NONE,
					ActionApproach.NORMAL);
			default -> throw new CommandException("/move needs a zone, exit or object alias");
		};
	}

	private ActionPayload observe(String verb, ObservationKind kind, List<String> args, int count, Context context) {
		requireCount(verb, args, count);
		ActionTarget target = args.isEmpty() ? ActionTarget.unspecified() : context.target(args.getFirst());
		return new ActionPayload.ObservePayload(kind, target);
	}

	private ActionPayload communicate(CommunicationKind kind, String segment, List<String> args, Context context) {
		ActionTarget addressee = ActionTarget.unspecified();
		int skip = 1;
		if (args.size() >= 2 && args.getFirst().equalsIgnoreCase("to")) {
			addressee = new ActionTarget.EntityTarget(context.aliases.resolve(AliasKind.ENTITY, args.get(1)), Optional.empty(),
					EXPLICIT);
			skip = 3;
		}
		String[] words = segment.substring(1).strip().split("\\s+", skip + 1);
		String rest = words.length > skip ? words[skip].strip() : "";
		String content = rest.startsWith("\"") ? quoted(rest) : rest;
		if (content.isBlank()) {
			throw new CommandException("/" + kind.name().toLowerCase(Locale.ROOT) + " needs something to say");
		}
		return new ActionPayload.CommunicatePayload(kind, content, addressee);
	}

	/** The text of one double-quoted string with {@code \"} unescaped; nothing may follow it. */
	static String quoted(String text) {
		StringBuilder content = new StringBuilder();
		for (int i = 1; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '\\' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
				content.append('"');
				i++;
			} else if (c == '"') {
				if (!text.substring(i + 1).isBlank()) {
					throw new CommandException("Nothing may follow the closing quotation mark");
				}
				return content.toString();
			} else {
				content.append(c);
			}
		}
		throw new CommandException("A quotation is not closed");
	}

	private ActionTarget onTarget(String verb, List<String> args, Context context) {
		if (args.size() == 1) {
			return ActionTarget.unspecified();
		}
		if (args.size() != 3 || !args.get(1).equalsIgnoreCase("on")) {
			throw new CommandException("Use /" + verb + " <alias> [on <target> or self]");
		}
		return context.target(args.get(2));
	}

	private static String first(String verb, List<String> args) {
		if (args.isEmpty()) {
			throw new CommandException("/" + verb + " needs an alias");
		}
		return args.getFirst();
	}

	private static void requireCount(String verb, List<String> args, int count) {
		if (args.size() != count) {
			throw new CommandException("/" + verb + " takes " + count + (count == 1 ? " argument" : " arguments"));
		}
	}

	private static Map<String, String> options(List<String> tokens, Set<String> allowed) {
		if (tokens.size() % 2 != 0) {
			throw new CommandException("Options come in pairs, for example 'part HEAD'");
		}
		Map<String, String> options = new HashMap<>();
		for (int i = 0; i < tokens.size(); i += 2) {
			String key = tokens.get(i).toLowerCase(Locale.ROOT);
			if (!allowed.contains(key)) {
				throw new CommandException("Unknown option '" + tokens.get(i) + "'");
			}
			if (options.put(key, tokens.get(i + 1)) != null) {
				throw new CommandException("Option '" + key + "' given twice");
			}
		}
		return options;
	}

	private static <E extends Enum<E>> E enumValue(Class<E> type, String token, String label) {
		String name = token.toUpperCase(Locale.ROOT);
		return Arrays.stream(type.getEnumConstants()).filter(e -> e.name().equals(name)).findFirst()
				.orElseThrow(() -> new CommandException("Unknown " + label + " '" + token + "'"));
	}

	/** Per-command state: aliases, and the one incoming attack the intent responds to. */
	private static final class Context {

		private final AliasTable aliases;
		private Optional<String> response = Optional.empty();
		private boolean responded;

		Context(AliasTable aliases) {
			this.aliases = aliases;
		}

		ActionTarget target(String alias) {
			if (alias.equalsIgnoreCase("self")) {
				return new ActionTarget.SelfTarget(Optional.empty(), EXPLICIT);
			}
			AliasKind kind = AliasKind.kindOf(alias).orElseThrow(() -> new CommandException("'" + alias + "' is not a valid alias"));
			return switch (kind) {
				case ENTITY -> new ActionTarget.EntityTarget(aliases.resolve(kind, alias), Optional.empty(), EXPLICIT);
				case OBJECT -> new ActionTarget.ObjectTarget(aliases.resolve(kind, alias), EXPLICIT);
				case HAZARD -> new ActionTarget.HazardTarget(aliases.resolve(kind, alias), EXPLICIT);
				case ZONE -> new ActionTarget.ZoneTarget(aliases.resolve(kind, alias), EXPLICIT);
				case EXIT -> new ActionTarget.ExitTarget(aliases.resolve(kind, alias), EXPLICIT);
				default -> throw new CommandException("'" + alias + "' cannot be a target");
			};
		}

		CarriedReference carried(String alias) {
			AliasKind kind = AliasKind.kindOf(alias).orElseThrow(() -> new CommandException("'" + alias + "' is not a valid alias"));
			return switch (kind) {
				case WEAPON -> new CarriedReference(CarriedKind.WEAPON, aliases.resolve(kind, alias));
				case ITEM -> new CarriedReference(CarriedKind.ITEM, aliases.resolve(kind, alias));
				default -> throw new CommandException("'" + alias + "' is not a weapon or item you carry");
			};
		}

		String onlyWeapon() {
			Map<String, String> weapons = aliases.aliases(AliasKind.WEAPON);
			if (weapons.size() != 1) {
				throw new CommandException("Name the weapon with 'with weapon_N'");
			}
			return weapons.values().iterator().next();
		}

		void respondTo(Optional<String> against) {
			Map<String, String> attacks = aliases.aliases(AliasKind.ATTACK);
			Optional<String> chosen;
			if (against.isPresent()) {
				chosen = Optional.of(aliases.resolve(AliasKind.ATTACK, against.get()));
			} else if (attacks.size() > 1) {
				throw new CommandException("Several attacks are incoming: add 'against attack_N'");
			} else {
				chosen = attacks.values().stream().findFirst();
			}
			if (responded && !response.equals(chosen)) {
				throw new CommandException("One action can respond to only one incoming attack");
			}
			responded = true;
			response = chosen;
		}
	}

	static final class CommandException extends RuntimeException {
		CommandException(String message) {
			super(message);
		}
	}
}
