package com.leeburke.springgame.ai.interpreter;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.leeburke.springgame.ai.AiJson;
import com.leeburke.springgame.ai.interpreter.ActionInterpretationContext.Owned;
import com.leeburke.springgame.ai.interpreter.ActionInterpretationContext.Thing;
import com.leeburke.springgame.ai.interpreter.ActionInterpretationContext.Zone;
import com.leeburke.springgame.world.WorldFixtures;
import com.leeburke.springgame.world.view.PlayerSceneView;
import com.leeburke.springgame.world.view.PlayerSceneViewProjector;

class InterpretationContextTest {

	@Test
	void aliasesAreTypedNumberedAndNamed() {
		ActionInterpretationContext context = InterpreterFixtures.setup().context();

		assertThat(context.currentZone()).isEqualTo("zone_2");
		assertThat(context.zones()).containsExactly(new Zone("zone_1", "Side Aisle"), new Zone("zone_2", "Nave Entrance"));
		assertThat(context.entities()).containsExactly(new Thing("entity_1", "Hollow Acolyte", "zone_1"));
		assertThat(context.objects()).containsExactly(new Thing("object_1", "Wooden Pew", "zone_2"));
		assertThat(context.hazards()).containsExactly(new Thing("hazard_1", "Fire", "zone_1"));
		assertThat(context.weapons()).containsExactly(new Owned("weapon_1", "Longsword"));
		assertThat(context.items()).containsExactly(new Owned("item_1", "Restorative Salve"), new Owned("item_2", "Crowbar"));
		assertThat(context.abilities()).extracting(Owned::alias).containsExactly("ability_1");
		assertThat(context.incomingAttacks()).singleElement().satisfies(attack -> {
			assertThat(attack.alias()).isEqualTo("attack_1");
			assertThat(attack.attacker()).contains("entity_1");
			assertThat(attack.cue()).contains("overhead");
		});
	}

	@Test
	void aliasesMapBackToStageTenReferences() {
		InterpretationSetup setup = InterpreterFixtures.setup();
		AliasTable aliases = setup.aliases();

		assertThat(aliases.resolve(AliasKind.ENTITY, "entity_1")).isEqualTo("acolyte_1");
		assertThat(aliases.resolve(AliasKind.ZONE, "zone_1")).isEqualTo("aisle");
		assertThat(aliases.resolve(AliasKind.ATTACK, "attack_1")).isEqualTo(InterpreterFixtures.BACKEND_ATTACK);
		assertThat(aliases.resolve(AliasKind.WEAPON, "weapon_1")).isEqualTo("weapon_1");
		assertThat(setup.validation().references().weapons()).containsOnlyKeys("weapon_1");
		assertThat(setup.validation().references().items()).containsOnlyKeys("item_1", "item_2");
		assertThat(setup.validation().references().abilities()).containsOnlyKeys("ability_1");
		assertThat(setup.validation().incomingAttacks()).containsExactly(InterpreterFixtures.BACKEND_ATTACK);
	}

	@Test
	void sameSituationGivesTheSameAliasesWhateverTheListOrder() {
		PlayerSceneView view = InterpreterFixtures.view();
		PlayerSceneView reversed = new PlayerSceneView(view.currentZoneId(), view.zones().reversed(), view.connections(),
				view.entities().reversed(), view.objects(), view.hazards(), view.exits(), view.discoveredFacts());
		InterpretationContextBuilder builder = new InterpretationContextBuilder(InterpreterFixtures.WORLD);

		assertThat(builder.build(reversed, InterpreterFixtures.player(), List.of(InterpreterFixtures.incoming())).context())
				.isEqualTo(InterpreterFixtures.setup().context());
		assertThat(InterpreterFixtures.setup().context()).isEqualTo(InterpreterFixtures.setup().context());
	}

	@Test
	void aliasesAreUniqueAndWellFormed() {
		String json = AiJson.write(InterpreterFixtures.setup().context());
		Set<String> seen = new HashSet<>();
		var matcher = java.util.regex.Pattern.compile("\"alias\":\"([^\"]+)\"").matcher(json);
		while (matcher.find()) {
			assertThat(seen.add(matcher.group(1))).as(matcher.group(1)).isTrue();
			assertThat(AliasKind.kindOf(matcher.group(1))).isPresent();
		}
		assertThat(seen).hasSizeGreaterThan(5);
	}

	@Test
	void hiddenContentNeverReachesTheRequest() {
		PlayerSceneView view = PlayerSceneViewProjector.project(WorldFixtures.richScene(), WorldFixtures.ENTRANCE,
				Set.of(WorldFixtures.ENTRANCE, WorldFixtures.AISLE, WorldFixtures.ALTAR));
		String json = AiJson.write(new InterpretationContextBuilder(InterpreterFixtures.WORLD)
				.build(view, InterpreterFixtures.player(), List.of()).context());

		assertThat(json).doesNotContain("warden", "relic", "plate", "secret", "crypt", "ghoul", "chest", "tunnel",
				"CANDLES_LIT", "pilgrim", "aaaaaaaa", "bbbbbbbb", "acolyte_1", "pew_1", "north_door");
		assertThat(json).contains("Hollow Acolyte", "Wooden Pew", "entity_1");
	}

	@Test
	void contextTypeGraphHasNoBackendOrMechanicalTypes() {
		Set<Class<?>> reachable = new HashSet<>();
		collect(ActionInterpretationContext.class, reachable);
		assertThat(reachable).extracting(Class::getSimpleName).doesNotContain("SceneState", "SceneInstance", "UUID",
				"StatBlock", "StatValue", "IncomingAttack", "TargetCombatProfile", "EnemyInstance", "EnemyDefinition",
				"PlayerCharacterState", "AliasTable", "WeaponDefinition");
		assertThat(reachable).noneMatch(type -> type.getPackageName().contains("persistence")
				|| type.getPackageName().contains("enemy") || type.getPackageName().contains("resolution"));
		Set<String> names = new HashSet<>();
		reachable.stream().filter(Class::isRecord).forEach(type -> {
			for (RecordComponent component : type.getRecordComponents()) {
				names.add(component.getName().toLowerCase());
			}
		});
		assertThat(names).noneMatch(name -> name.contains("difficulty") || name.contains("damage") || name.contains("weight")
				|| name.contains("hp") || name.contains("stat") || name.contains("seed") || name.contains("revision"));
	}

	static void collect(Type type, Set<Class<?>> seen) {
		if (type instanceof ParameterizedType parameterized) {
			collect(parameterized.getRawType(), seen);
			for (Type argument : parameterized.getActualTypeArguments()) {
				collect(argument, seen);
			}
			return;
		}
		if (!(type instanceof Class<?> clazz) || !seen.add(clazz)) {
			return;
		}
		if (clazz.isRecord()) {
			for (RecordComponent component : clazz.getRecordComponents()) {
				collect(component.getGenericType(), seen);
			}
		}
	}
}
