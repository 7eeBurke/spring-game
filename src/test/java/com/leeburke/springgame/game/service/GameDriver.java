package com.leeburke.springgame.game.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import org.springframework.jdbc.core.JdbcTemplate;

import com.leeburke.springgame.enemy.EnemyInstance;
import com.leeburke.springgame.game.view.GameView;
import com.leeburke.springgame.persistence.EnemyStore;
import com.leeburke.springgame.persistence.GameRunStore;
import com.leeburke.springgame.persistence.WorldStore;
import com.leeburke.springgame.world.HiddenContentKind;
import com.leeburke.springgame.world.PlayerLocation;
import com.leeburke.springgame.world.SceneInstance;
import com.leeburke.springgame.world.SceneZone;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Plays the game through the application services, like the REST layer does, plus test-only
 * shortcuts for arranging state (placing the player, setting HP) through the stores.
 */
final class GameDriver {

	static final String INVITE = "test-invite";
	private static final SecureRandom TOKENS = new SecureRandom();
	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final RunCreationService creation;
	private final TurnService turns;
	private final GameViewService views;
	private final WorldStore world;
	private final EnemyStore enemies;
	private final GameRunStore runs;
	private final JdbcTemplate jdbc;

	GameDriver(RunCreationService creation, TurnService turns, GameViewService views, WorldStore world, EnemyStore enemies,
			GameRunStore runs, JdbcTemplate jdbc) {
		this.creation = creation;
		this.turns = turns;
		this.views = views;
		this.world = world;
		this.enemies = enemies;
		this.runs = runs;
		this.jdbc = jdbc;
	}

	/** A strict interpreter answer: SLASH at the given creature alias with weapon_1. */
	static String attackDocument(String alias) {
		String target = "{\"kind\":\"ENTITY\",\"alias\":\"" + alias + "\",\"bodyPart\":null,\"specificity\":\"EXPLICIT\"}";
		String payload = "{\"weapon\":\"weapon_1\",\"method\":\"SLASH\",\"template\":\"HORIZONTAL_SWING\",\"target\":" + target
				+ ",\"approach\":\"NORMAL\",\"purpose\":\"DAMAGE\"}";
		StringBuilder step = new StringBuilder("{\"relation\":\"START\",\"action\":\"ATTACK\"");
		for (String slot : List.of("attack", "defend", "move", "interact", "observe", "useAbility", "useItem", "communicate")) {
			step.append(",\"").append(slot).append("\":").append(slot.equals("attack") ? payload : "null");
		}
		step.append('}');
		return "{\"schemaVersion\":1,\"supported\":true,\"responseToAttack\":null,\"confidence\":\"HIGH\",\"steps\":[" + step
				+ "],\"unresolved\":[]}";
	}

	static String newToken() {
		byte[] bytes = new byte[32];
		TOKENS.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	UUID newRun() {
		return creation.create(INVITE, UUID.randomUUID().toString(), "Bearer " + newToken(), "127.0.0.1").runId();
	}

	GameView view(UUID runId) {
		return views.view(runId);
	}

	/** A new turn on the current view. */
	Reply turn(UUID runId, String input) {
		return turn(runId, UUID.randomUUID(), input, view(runId).stateVersion());
	}

	Reply turn(UUID runId, UUID key, String input, long stateVersion) {
		TurnReply reply = turns.submit(runId, key, input, stateVersion);
		return new Reply(reply.status(), JSON.readTree(reply.json()), reply.json());
	}

	List<SceneInstance> scenes(UUID runId) {
		return jdbc.queryForList("SELECT id FROM scene_instance WHERE run_id = ? ORDER BY id", UUID.class, runId).stream()
				.map(id -> world.findScene(id).orElseThrow()).toList();
	}

	List<EnemyInstance> visibleEnemies(SceneInstance scene) {
		return enemies.findEnemies(scene.id()).stream()
				.filter(e -> !scene.state().isHidden(HiddenContentKind.ENTITY, e.entityId()))
				.sorted(Comparator.comparing(EnemyInstance::entityId)).toList();
	}

	/** Moves the player (test arrangement only) into the first scene matching, at its first visible zone. */
	SceneInstance place(UUID runId, Predicate<SceneInstance> wanted) {
		SceneInstance scene = scenes(runId).stream().filter(wanted).findFirst().orElseThrow();
		String zone = scene.state().zones().stream().map(SceneZone::id)
				.filter(z -> !scene.state().isHidden(HiddenContentKind.ZONE, z)).findFirst().orElseThrow();
		world.setPlayerLocation(runId, new PlayerLocation(scene.id(), zone));
		return scene;
	}

	SceneInstance placeAmongEnemies(UUID runId, int atLeast) {
		return place(runId, s -> visibleEnemies(s).size() >= atLeast);
	}

	void setPlayerHp(UUID runId, int hp) {
		runs.updatePlayerHp(runId, hp);
	}

	int maxHp(UUID runId) {
		return runs.findRun(runId).orElseThrow().playerCharacter().maxHp();
	}

	void setEnemyHp(SceneInstance scene, String entityId, int hp) {
		enemies.updateHp(scene.id(), entityId, hp);
	}

	/** The view alias of a visible enemy: aliases follow ascending entity ID. */
	String alias(SceneInstance scene, String entityId) {
		List<EnemyInstance> visible = visibleEnemies(scene);
		for (int i = 0; i < visible.size(); i++) {
			if (visible.get(i).entityId().equals(entityId)) {
				return "entity_" + (i + 1);
			}
		}
		throw new AssertionError(entityId + " is not visible");
	}

	/** Attacks the creature, defending first whenever an attack is pending. */
	Reply strike(UUID runId, String alias) {
		String attack = "/attack " + alias + " slash with weapon_1";
		GameView view = view(runId);
		return turn(runId, view.awaiting().equals("DEFENSE") ? "/defend parry ; " + attack : attack);
	}

	/** Keeps attacking the first active creature until an enemy attack is pending. */
	GameView untilPending(UUID runId) {
		for (int i = 0; i < 40; i++) {
			GameView view = view(runId);
			assertThat(view.status()).isEqualTo("ACTIVE");
			if (view.awaiting().equals("DEFENSE")) {
				return view;
			}
			setPlayerHp(runId, maxHp(runId));
			Optional<GameView.CreatureView> target = view.scene().creatures().stream()
					.filter(c -> c.condition().equals("ACTIVE")).findFirst();
			Reply reply = turn(runId, "/attack " + target.orElseThrow().alias() + " slash with weapon_1");
			assertThat(reply.status()).as(reply.raw()).isEqualTo(200);
		}
		throw new AssertionError("No enemy attacked in 40 turns");
	}

	int count(String sql, Object... args) {
		return jdbc.queryForObject(sql, Integer.class, args);
	}

	record Reply(int status, JsonNode body, String raw) {
		String code() {
			return body.path("error").path("code").asString();
		}
	}
}
