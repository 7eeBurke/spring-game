package com.leeburke.springgame.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.leeburke.springgame.PostgresTestcontainersConfiguration;
import com.leeburke.springgame.game.service.GameTestConfiguration;
import com.leeburke.springgame.game.service.GameTestConfiguration.MovableClock;
import com.leeburke.springgame.persistence.EnemyStore;
import com.leeburke.springgame.persistence.WorldStore;
import com.leeburke.springgame.world.HiddenContentKind;
import com.leeburke.springgame.world.PlayerLocation;
import com.leeburke.springgame.world.SceneInstance;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The public HTTP contract: status codes and stable error codes, token ownership, abuse limits that
 * ignore forwarding headers, request limits, byte-identical replays, and a scan that no backend
 * internals leak into responses. Scripted (disabled) AI only.
 */
@SpringBootTest(properties = { "game.api.invite-codes=api-invite, second-invite",
		"game.api.limits.invalid-invites-per-hour-per-address=3", "game.api.limits.turns-per-minute-per-run=3",
		"game.api.limits.creations-per-hour-per-invite=10000", "game.api.limits.creations-per-day=10000" })
@AutoConfigureMockMvc
@Import({ PostgresTestcontainersConfiguration.class, GameTestConfiguration.class })
class RunApiIntegrationTest {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private WorldStore world;
	@Autowired
	private EnemyStore enemies;
	@Autowired
	private MovableClock clock;

	private static String token() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private MockHttpServletResponse perform(MockHttpServletRequestBuilder request) throws Exception {
		return mvc.perform(request).andReturn().getResponse();
	}

	private static MockHttpServletRequestBuilder create(String invite, String key, String token) {
		MockHttpServletRequestBuilder request = post("/api/v1/runs");
		if (invite != null) {
			request.header("X-Invite-Code", invite);
		}
		if (key != null) {
			request.header("Idempotency-Key", key);
		}
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		return request;
	}

	private static MockHttpServletRequestBuilder turn(UUID runId, String token, String key, String body) {
		MockHttpServletRequestBuilder request = post("/api/v1/runs/" + runId + "/turns").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content(body);
		if (key != null) {
			request.header("Idempotency-Key", key);
		}
		return request;
	}

	private static String body(String input, long version) {
		return "{\"input\":" + JSON.writeValueAsString(input) + ",\"stateVersion\":" + version + "}";
	}

	private static JsonNode json(MockHttpServletResponse response) throws Exception {
		return JSON.readTree(response.getContentAsString());
	}

	private static String code(MockHttpServletResponse response) throws Exception {
		return json(response).path("error").path("code").asString();
	}

	private record Run(UUID id, String token) {
	}

	private Run newRun() throws Exception {
		String token = token();
		MockHttpServletResponse response = perform(create("api-invite", UUID.randomUUID().toString(), token));
		assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(201);
		return new Run(UUID.fromString(json(response).path("runId").asString()), token);
	}

	private JsonNode view(Run run) throws Exception {
		MockHttpServletResponse response = perform(get("/api/v1/runs/" + run.id()).header("Authorization", "Bearer " + run.token()));
		assertThat(response.getStatus()).isEqualTo(200);
		return json(response);
	}

	// --- Creation ---

	@Test
	void creationReturnsTheRunAndItsViewButNeverTheToken() throws Exception {
		String token = token();
		String key = UUID.randomUUID().toString();

		MockHttpServletResponse created = perform(create("second-invite", key, token));
		MockHttpServletResponse again = perform(create("second-invite", key, token));

		assertThat(created.getStatus()).isEqualTo(201);
		assertThat(json(created).path("view").path("status").asString()).isEqualTo("ACTIVE");
		assertThat(created.getContentAsString()).doesNotContain(token);
		assertThat(json(again).path("runId").asString()).isEqualTo(json(created).path("runId").asString());
		MockHttpServletResponse otherToken = perform(create("second-invite", key, token()));
		assertThat(otherToken.getStatus()).isEqualTo(409);
		assertThat(code(otherToken)).isEqualTo("IDEMPOTENCY_KEY_REUSED");
	}

	@Test
	void creationNeedsAValidInviteKeyAndClientToken() throws Exception {
		String key = UUID.randomUUID().toString();

		MockHttpServletResponse noKey = perform(create("api-invite", null, token()));
		MockHttpServletResponse badKey = perform(create("api-invite", "not-a-uuid", token()));
		MockHttpServletResponse shortToken = perform(create("api-invite", key, "abc"));
		MockHttpServletResponse noToken = perform(create("api-invite", key, null));

		assertThat(List.of(noKey.getStatus(), badKey.getStatus(), shortToken.getStatus(), noToken.getStatus())).containsOnly(400);
		assertThat(code(noKey)).isEqualTo("INVALID_REQUEST");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM run_session WHERE creation_key = ?::uuid", Integer.class, key)).isZero();
	}

	@Test
	void invalidInvitesAreLimitedByTheSocketAddressWhateverTheForwardingHeaders() throws Exception {
		for (int i = 0; i < 3; i++) {
			String forwarded = "203.0.113." + i;
			MockHttpServletResponse wrong = perform(create("wrong-invite", UUID.randomUUID().toString(), token())
					.header("X-Forwarded-For", forwarded).header("X-Real-IP", forwarded)
					.with(request -> {
						request.setRemoteAddr("10.9.9.9");
						return request;
					}));
			assertThat(wrong.getStatus()).isEqualTo(401);
			assertThat(code(wrong)).isEqualTo("UNAUTHORIZED");
		}

		MockHttpServletResponse limited = perform(create("api-invite", UUID.randomUUID().toString(), token())
				.header("X-Forwarded-For", "198.51.100.7").with(request -> {
					request.setRemoteAddr("10.9.9.9");
					return request;
				}));
		MockHttpServletResponse elsewhere = perform(create("api-invite", UUID.randomUUID().toString(), token()));

		assertThat(limited.getStatus()).isEqualTo(429);
		assertThat(code(limited)).isEqualTo("RATE_LIMITED");
		assertThat(elsewhere.getStatus()).isEqualTo(201);
	}

	// --- Ownership ---

	@Test
	void aRunIsOnlyReadableWithItsOwnToken() throws Exception {
		Run mine = newRun();
		Run theirs = newRun();

		MockHttpServletResponse none = perform(get("/api/v1/runs/" + mine.id()));
		MockHttpServletResponse malformed = perform(get("/api/v1/runs/" + mine.id()).header("Authorization", "Bearer short"));
		MockHttpServletResponse foreign = perform(get("/api/v1/runs/" + mine.id()).header("Authorization", "Bearer " + theirs.token()));
		MockHttpServletResponse unknown = perform(get("/api/v1/runs/" + UUID.randomUUID()).header("Authorization", "Bearer " + mine.token()));
		MockHttpServletResponse notAnId = perform(get("/api/v1/runs/nonsense").header("Authorization", "Bearer " + mine.token()));
		MockHttpServletResponse foreignTurn = perform(turn(mine.id(), theirs.token(), UUID.randomUUID().toString(), body("/hold", 0)));

		assertThat(none.getStatus()).isEqualTo(401);
		assertThat(malformed.getStatus()).isEqualTo(401);
		assertThat(foreign.getStatus()).isEqualTo(404);
		assertThat(code(foreign)).isEqualTo("RUN_NOT_FOUND");
		assertThat(unknown.getStatus()).isEqualTo(404);
		assertThat(notAnId.getStatus()).isEqualTo(404);
		assertThat(foreignTurn.getStatus()).isEqualTo(404);
		assertThat(view(mine).path("stateVersion").asLong()).isZero();
	}

	// --- Turns ---

	@Test
	void aReplayIsByteIdenticalAndNeverCountsAgainstTheTurnLimit() throws Exception {
		Run run = newRun();
		String key = UUID.randomUUID().toString();

		MockHttpServletResponse first = perform(turn(run.id(), run.token(), key, body("/hold", 0)));
		assertThat(first.getStatus()).as(first.getContentAsString()).isEqualTo(200);
		for (int i = 0; i < 5; i++) {
			MockHttpServletResponse replay = perform(turn(run.id(), run.token(), key, body("/hold", 0)));
			assertThat(replay.getStatus()).isEqualTo(200);
			assertThat(replay.getContentAsString()).isEqualTo(first.getContentAsString());
		}

		assertThat(perform(turn(run.id(), run.token(), UUID.randomUUID().toString(), body("/hold", 1))).getStatus()).isEqualTo(200);
		assertThat(perform(turn(run.id(), run.token(), UUID.randomUUID().toString(), body("/hold", 2))).getStatus()).isEqualTo(200);
		MockHttpServletResponse limited = perform(turn(run.id(), run.token(), UUID.randomUUID().toString(), body("/hold", 3)));

		assertThat(limited.getStatus()).isEqualTo(429);
		assertThat(code(limited)).isEqualTo("RATE_LIMITED");
		clock.advance(Duration.ofMinutes(2));
		assertThat(perform(turn(run.id(), run.token(), UUID.randomUUID().toString(), body("/hold", 3))).getStatus()).isEqualTo(200);
	}

	@Test
	void badTurnRequestsAreRejectedBeforeAnythingHappens() throws Exception {
		Run run = newRun();

		MockHttpServletResponse noKey = perform(turn(run.id(), run.token(), null, body("/hold", 0)));
		MockHttpServletResponse tooLong = perform(turn(run.id(), run.token(), UUID.randomUUID().toString(), body("a".repeat(501), 0)));
		MockHttpServletResponse blank = perform(turn(run.id(), run.token(), UUID.randomUUID().toString(), body("   ", 0)));
		MockHttpServletResponse malformed = perform(turn(run.id(), run.token(), UUID.randomUUID().toString(), "{not json"));
		MockHttpServletResponse missing = perform(turn(run.id(), run.token(), UUID.randomUUID().toString(), "{\"input\":\"/hold\"}"));
		MockHttpServletResponse huge = perform(turn(run.id(), run.token(), UUID.randomUUID().toString(),
				"{\"input\":\"/hold\",\"stateVersion\":0,\"pad\":\"" + "x".repeat(9000) + "\"}"));

		for (MockHttpServletResponse response : List.of(noKey, tooLong, blank, malformed, missing, huge)) {
			assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(400);
			assertThat(code(response)).isEqualTo("INVALID_REQUEST");
			assertThat(response.getContentAsString()).doesNotContain("Exception").doesNotContain("at com.");
		}
		assertThat(view(run).path("stateVersion").asLong()).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM run_turn WHERE run_id = ?", Integer.class, run.id())).isZero();
	}

	@Test
	void gameRejectionsUseStableCodes() throws Exception {
		Run run = newRun();

		MockHttpServletResponse invalid = perform(turn(run.id(), run.token(), UUID.randomUUID().toString(), body("/fly", 0)));
		MockHttpServletResponse freeText = perform(turn(run.id(), run.token(), UUID.randomUUID().toString(), body("I look around", 0)));
		MockHttpServletResponse stale = perform(turn(run.id(), run.token(), UUID.randomUUID().toString(), body("/hold", 7)));

		assertThat(invalid.getStatus()).isEqualTo(422);
		assertThat(code(invalid)).isEqualTo("INVALID_COMMAND");
		assertThat(freeText.getStatus()).isEqualTo(422);
		assertThat(code(freeText)).isEqualTo("INTERPRETATION_FAILED");
		assertThat(json(freeText).path("error").path("reason").asString()).isEqualTo("AI_UNAVAILABLE");
		assertThat(json(freeText).path("error").path("hint").asString()).contains("/attack");
		assertThat(stale.getStatus()).isEqualTo(409);
		assertThat(code(stale)).isEqualTo("STALE_VIEW");
	}

	// --- Leaks and the cue ---

	@Test
	void responsesNeverLeakBackendInternalsAndAlwaysCarryTheCue() throws Exception {
		Run run = newRun();
		SceneInstance scene = jdbc.queryForList("SELECT id FROM scene_instance WHERE run_id = ?", UUID.class, run.id()).stream()
				.map(id -> world.findScene(id).orElseThrow())
				.filter(s -> enemies.findEnemies(s.id()).stream().anyMatch(e -> !s.state().isHidden(HiddenContentKind.ENTITY, e.entityId())))
				.findFirst().orElseThrow();
		String zone = scene.state().zones().stream().map(z -> z.id())
				.filter(z -> !scene.state().isHidden(HiddenContentKind.ZONE, z)).findFirst().orElseThrow();
		world.setPlayerLocation(run.id(), new PlayerLocation(scene.id(), zone));

		StringBuilder everything = new StringBuilder();
		JsonNode view = view(run);
		for (int i = 0; i < 40 && !view.path("awaiting").asString().equals("DEFENSE"); i++) {
			assertThat(view.path("status").asString()).isEqualTo("ACTIVE");
			clock.advance(Duration.ofMinutes(1));
			String target = view.path("scene").path("creatures").get(0).path("alias").asString();
			MockHttpServletResponse reply = perform(turn(run.id(), run.token(), UUID.randomUUID().toString(),
					body("/attack " + target + " slash with weapon_1", view.path("stateVersion").asLong())));
			assertThat(reply.getStatus()).as(reply.getContentAsString()).isEqualTo(200);
			everything.append(reply.getContentAsString());
			view = view(run);
			jdbc.update("UPDATE player_character SET current_hp = max_hp WHERE run_id = ?", run.id());
		}
		assertThat(view.path("awaiting").asString()).isEqualTo("DEFENSE");
		assertThat(view.path("pendingAttack").path("cueText").asString()).startsWith("Incoming: ");
		everything.append(view);

		long seed = jdbc.queryForObject("SELECT run_seed FROM game_run WHERE id = ?", Long.class, run.id());
		String all = everything.toString();
		assertThat(all).doesNotContain(String.valueOf(seed)).doesNotContain(run.token())
				.doesNotContain("difficulty").doesNotContain("baseDamage").doesNotContain("weight").doesNotContain("revision")
				.doesNotContain("Seed").doesNotContain("attack-").doesNotContain("currentHp").doesNotContain("trauma");
		for (UUID sceneId : jdbc.queryForList("SELECT id FROM scene_instance WHERE run_id = ?", UUID.class, run.id())) {
			assertThat(all).doesNotContain(sceneId.toString());
		}
		for (var entity : scene.state().entities()) {
			assertThat(all).as("backend entity IDs are replaced by aliases").doesNotContain("\"" + entity.id() + "\"");
		}
	}
}
