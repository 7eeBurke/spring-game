package com.leeburke.springgame.ai.interpreter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import com.leeburke.springgame.action.resolution.IncomingAttack;
import com.leeburke.springgame.action.validation.ActionValidationContext;
import com.leeburke.springgame.action.validation.PlayerActionReferences;
import com.leeburke.springgame.ai.interpreter.ActionInterpretationContext.BodyPartState;
import com.leeburke.springgame.ai.interpreter.ActionInterpretationContext.Condition;
import com.leeburke.springgame.ai.interpreter.ActionInterpretationContext.Connection;
import com.leeburke.springgame.ai.interpreter.ActionInterpretationContext.Creature;
import com.leeburke.springgame.ai.interpreter.ActionInterpretationContext.Exit;
import com.leeburke.springgame.ai.interpreter.ActionInterpretationContext.IncomingAttackSummary;
import com.leeburke.springgame.ai.interpreter.ActionInterpretationContext.Owned;
import com.leeburke.springgame.ai.interpreter.ActionInterpretationContext.Thing;
import com.leeburke.springgame.ai.interpreter.ActionInterpretationContext.Zone;
import com.leeburke.springgame.ai.narration.AttackCue;
import com.leeburke.springgame.character.PlayerCharacterState;
import com.leeburke.springgame.character.ToolBeltEntry;
import com.leeburke.springgame.content.AbilityDefinition;
import com.leeburke.springgame.content.ItemDefinition;
import com.leeburke.springgame.content.WeaponDefinition;
import com.leeburke.springgame.content.world.WorldContentCatalog;
import com.leeburke.springgame.mechanics.BodyPart;
import com.leeburke.springgame.world.view.PlayerSceneView;

/**
 * Builds an interpretation request's context, alias table and validation context from player-safe
 * inputs only: the scene view, the player's body and tool belt (never stats), catalogue names and
 * each incoming attack's reference, attacker and template.
 * <p>
 * Aliases are deterministic for an identical situation: scene content is numbered in ascending
 * order of local ID, weapons and items in tool-belt slot order, the ability as {@code ability_1},
 * and incoming attacks in ascending order of their reference. The player-owned aliases are also
 * the opaque Stage 10 references, so {@code weapon_1} validates directly.
 */
public final class InterpretationContextBuilder {

	private final WorldContentCatalog world;

	public InterpretationContextBuilder(WorldContentCatalog world) {
		this.world = Objects.requireNonNull(world, "world");
	}

	/** Every visible creature is ACTIVE. */
	public InterpretationSetup build(PlayerSceneView view, PlayerCharacterState player, List<IncomingAttack> incoming) {
		return build(view, player, incoming, Set.of());
	}

	/** @param fallenEntityIds visible creatures at 0 HP, shown as FALLEN */
	public InterpretationSetup build(PlayerSceneView view, PlayerCharacterState player, List<IncomingAttack> incoming,
			Set<String> fallenEntityIds) {
		Objects.requireNonNull(view, "view");
		Objects.requireNonNull(fallenEntityIds, "fallenEntityIds");
		Objects.requireNonNull(player, "player");
		Objects.requireNonNull(incoming, "incoming");
		AliasTable.Builder aliases = AliasTable.builder();

		Map<String, String> zoneAliases = mint(aliases, AliasKind.ZONE, view.zones(), PlayerSceneView.VisibleZone::id);
		List<Zone> zones = sorted(view.zones(), PlayerSceneView.VisibleZone::id).stream()
				.map(z -> new Zone(zoneAliases.get(z.id()), z.displayName())).toList();
		List<Connection> connections = view.connections().stream()
				.map(c -> new Connection(zoneAliases.get(c.zoneA()), zoneAliases.get(c.zoneB())))
				.sorted(Comparator.comparing(Connection::zoneA).thenComparing(Connection::zoneB))
				.toList();

		Map<String, String> entityAliases = mint(aliases, AliasKind.ENTITY, view.entities(), PlayerSceneView.VisibleEntity::id);
		List<Creature> entities = sorted(view.entities(), PlayerSceneView.VisibleEntity::id).stream()
				.map(e -> new Creature(entityAliases.get(e.id()), name(e.definitionCode()), zoneAliases.get(e.zoneId()),
						fallenEntityIds.contains(e.id()) ? Condition.FALLEN : Condition.ACTIVE))
				.toList();
		Map<String, String> objectAliases = mint(aliases, AliasKind.OBJECT, view.objects(), PlayerSceneView.VisibleObject::id);
		List<Thing> objects = sorted(view.objects(), PlayerSceneView.VisibleObject::id).stream()
				.map(o -> new Thing(objectAliases.get(o.id()), name(o.definitionCode()), zoneAliases.get(o.zoneId()))).toList();
		Map<String, String> hazardAliases = mint(aliases, AliasKind.HAZARD, view.hazards(), PlayerSceneView.VisibleHazard::id);
		List<Thing> hazards = sorted(view.hazards(), PlayerSceneView.VisibleHazard::id).stream()
				.map(h -> new Thing(hazardAliases.get(h.id()), name(h.definitionCode()), zoneAliases.get(h.zoneId()))).toList();
		Map<String, String> exitAliases = mint(aliases, AliasKind.EXIT, view.exits(), PlayerSceneView.KnownExit::id);
		List<Exit> exits = sorted(view.exits(), PlayerSceneView.KnownExit::id).stream()
				.map(x -> new Exit(exitAliases.get(x.id()), zoneAliases.get(x.zoneId()))).toList();

		Map<String, WeaponDefinition> weaponRefs = new LinkedHashMap<>();
		Map<String, ItemDefinition> itemRefs = new LinkedHashMap<>();
		List<Owned> weapons = new ArrayList<>();
		List<Owned> items = new ArrayList<>();
		for (ToolBeltEntry entry : player.toolBelt().entries()) {
			switch (entry) {
				case ToolBeltEntry.Weapon weapon -> {
					String alias = aliases.mint(AliasKind.WEAPON, AliasKind.WEAPON.alias(weapons.size() + 1));
					weaponRefs.put(alias, weapon.definition());
					weapons.add(new Owned(alias, weapon.definition().displayName()));
				}
				case ToolBeltEntry.Item item -> {
					String alias = aliases.mint(AliasKind.ITEM, AliasKind.ITEM.alias(items.size() + 1));
					itemRefs.put(alias, item.definition());
					items.add(new Owned(alias, item.definition().displayName()));
				}
			}
		}
		AbilityDefinition ability = player.ability();
		String abilityAlias = aliases.mint(AliasKind.ABILITY, AliasKind.ABILITY.alias(1));
		List<Owned> abilities = List.of(new Owned(abilityAlias, ability.displayName()));

		Set<String> attackRefs = new HashSet<>();
		List<IncomingAttackSummary> attacks = new ArrayList<>();
		for (IncomingAttack attack : incoming.stream().sorted(Comparator.comparing(IncomingAttack::ref)).toList()) {
			String alias = aliases.mint(AliasKind.ATTACK, attack.ref());
			attackRefs.add(attack.ref());
			attacks.add(new IncomingAttackSummary(alias, Optional.ofNullable(entityAliases.get(attack.attackerEntityId())),
					AttackCue.of(attack.template()).phrase()));
		}

		List<BodyPartState> body = new ArrayList<>();
		for (BodyPart part : BodyPart.values()) {
			body.add(new BodyPartState(part, player.body().severity(part)));
		}

		ActionInterpretationContext context = new ActionInterpretationContext(ActionInterpretationContext.CURRENT_SCHEMA_VERSION,
				zoneAliases.get(view.currentZoneId()), zones, connections, entities, objects, hazards, exits,
				view.discoveredFacts(), body, weapons, items, abilities, attacks);
		PlayerActionReferences references = new PlayerActionReferences(weaponRefs, Map.of(abilityAlias, ability), itemRefs);
		return new InterpretationSetup(context, aliases.build(), new ActionValidationContext(view, references, attackRefs));
	}

	private String name(String definitionCode) {
		return world.findElement(definitionCode)
				.orElseThrow(() -> new IllegalArgumentException("Unknown world element " + definitionCode))
				.displayName();
	}

	private static <T> List<T> sorted(List<T> items, Function<T, String> id) {
		return items.stream().sorted(Comparator.comparing(id)).toList();
	}

	private static <T> Map<String, String> mint(AliasTable.Builder aliases, AliasKind kind, List<T> items, Function<T, String> id) {
		Map<String, String> byId = new LinkedHashMap<>();
		for (T item : sorted(items, id)) {
			byId.put(id.apply(item), aliases.mint(kind, id.apply(item)));
		}
		return byId;
	}
}
