package com.leeburke.springgame.ai.interpreter;

import java.util.List;
import java.util.Optional;

import com.leeburke.springgame.action.AttackTemplate;
import com.leeburke.springgame.action.resolution.IncomingAttack;
import com.leeburke.springgame.character.Fated;
import com.leeburke.springgame.character.PlayerBody;
import com.leeburke.springgame.character.PlayerCharacterState;
import com.leeburke.springgame.character.ToolBelt;
import com.leeburke.springgame.character.ToolBeltEntry;
import com.leeburke.springgame.content.GameContentCatalog;
import com.leeburke.springgame.content.GameContentLoader;
import com.leeburke.springgame.content.world.WorldContentCatalog;
import com.leeburke.springgame.content.world.WorldContentLoader;
import com.leeburke.springgame.mechanics.Effectiveness;
import com.leeburke.springgame.mechanics.StatBlock;
import com.leeburke.springgame.mechanics.StatValue;
import com.leeburke.springgame.world.view.PlayerSceneView;
import com.leeburke.springgame.world.view.PlayerSceneView.KnownExit;
import com.leeburke.springgame.world.view.PlayerSceneView.VisibleConnection;
import com.leeburke.springgame.world.view.PlayerSceneView.VisibleEntity;
import com.leeburke.springgame.world.view.PlayerSceneView.VisibleHazard;
import com.leeburke.springgame.world.view.PlayerSceneView.VisibleObject;
import com.leeburke.springgame.world.view.PlayerSceneView.VisibleZone;

/**
 * A fixed interpretation situation. Aliases (by ascending local ID): zone_1 = aisle, zone_2 =
 * entrance; entity_1 = acolyte_1; object_1 = pew_1; hazard_1 = fire_1; exit_1 = north_door;
 * weapon_1 = Longsword; item_1 = Restorative Salve, item_2 = Crowbar; ability_1 = Stoneblood;
 * attack_1 = backend attack "enemy_attack_7" by acolyte_1 (an overhead strike).
 */
public final class InterpreterFixtures {

	public static final GameContentCatalog CONTENT = GameContentLoader.loadBundled();
	public static final WorldContentCatalog WORLD = WorldContentLoader.loadBundled();
	public static final String BACKEND_ATTACK = "enemy_attack_7";

	private InterpreterFixtures() {
	}

	public static PlayerSceneView view() {
		return new PlayerSceneView("entrance",
				List.of(new VisibleZone("entrance", "Nave Entrance"), new VisibleZone("aisle", "Side Aisle")),
				List.of(new VisibleConnection("entrance", "aisle")),
				List.of(new VisibleEntity("acolyte_1", "HOLLOW_ACOLYTE", "aisle")),
				List.of(new VisibleObject("pew_1", "WOODEN_PEW", "entrance")),
				List.of(new VisibleHazard("fire_1", "FIRE", "aisle")),
				List.of(new KnownExit("north_door", "entrance")),
				List.of());
	}

	public static PlayerCharacterState player() {
		StatBlock stats = new StatBlock(new StatValue(8), new StatValue(7), new StatValue(4), new StatValue(3), new StatValue(5));
		return new PlayerCharacterState("Wren", stats, new Fated(3), 25, 25, PlayerBody.healthy(),
				CONTENT.passives().getFirst(), CONTENT.findAbility("STONEBLOOD").orElseThrow(),
				new ToolBelt(List.of(new ToolBeltEntry.Weapon(CONTENT.findWeapon("LONGSWORD").orElseThrow()),
						new ToolBeltEntry.Item(CONTENT.findItem("RESTORATIVE_SALVE").orElseThrow()),
						new ToolBeltEntry.Item(CONTENT.findItem("CROWBAR").orElseThrow()))));
	}

	public static IncomingAttack incoming() {
		return new IncomingAttack(BACKEND_ATTACK, "acolyte_1", AttackTemplate.OVERHEAD_STRIKE, 13, 4, 2, Effectiveness.NORMAL,
				0, 0, Optional.empty());
	}

	public static InterpretationSetup setup() {
		return new InterpretationContextBuilder(WORLD).build(view(), player(), List.of(incoming()));
	}

	public static InterpretationSetup setupWithoutAttack() {
		return new InterpretationContextBuilder(WORLD).build(view(), player(), List.of());
	}
}
