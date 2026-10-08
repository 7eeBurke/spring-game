package com.leeburke.springgame.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.leeburke.springgame.game.TurnStatus;

/**
 * Persists turn requests ({@code run_turn}) with plain SQL. Every state change is a compare-and-set
 * {@code UPDATE ... WHERE} on the expected status and lease owner, and reports whether it applied:
 * a request whose lease was taken over is fenced out and cannot write. JDBC through
 * {@link JdbcClient} joins the surrounding JPA transaction, so a turn's mechanics and its record
 * commit together.
 * <p>
 * The mechanics summary is JSONB (queryable) written with {@code CAST(? AS jsonb)}; the response is
 * stored as exact text so replays are byte-for-byte identical.
 */
@Service
public class TurnStore {

	private static final String COLUMNS = "run_id, request_key, request_hash, base_state_version, status, lease_owner, "
			+ "lease_until, narration_owner, narration_lease_until, turn_number, mechanics_summary::text AS summary, "
			+ "response_status, response AS response_json";

	private final JdbcClient jdbc;

	public TurnStore(JdbcClient jdbc) {
		this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
	}

	@Transactional(readOnly = true)
	public Optional<TurnRecord> find(UUID runId, UUID requestKey) {
		return jdbc.sql("SELECT " + COLUMNS + " FROM run_turn WHERE run_id = ? AND request_key = ?")
				.params(runId, requestKey).query(TurnStore::map).optional();
	}

	/** The run's one unfinished turn (INTERPRETING or MECHANICS_COMMITTED), if any. */
	@Transactional(readOnly = true)
	public Optional<TurnRecord> findUnfinished(UUID runId) {
		return jdbc.sql("SELECT " + COLUMNS + " FROM run_turn WHERE run_id = ? AND status IN ('INTERPRETING', 'MECHANICS_COMMITTED')")
				.params(runId).query(TurnStore::map).optional();
	}

	/** The run's latest turn whose mechanics committed, if any. */
	@Transactional(readOnly = true)
	public Optional<TurnRecord> findLatestCommitted(UUID runId) {
		return jdbc.sql("SELECT " + COLUMNS + " FROM run_turn WHERE run_id = ? AND turn_number IS NOT NULL "
				+ "ORDER BY turn_number DESC LIMIT 1").params(runId).query(TurnStore::map).optional();
	}

	/**
	 * Starts a turn. Fails with a {@code DuplicateKeyException} if the key exists or the run already
	 * has an unfinished turn (partial unique index).
	 */
	@Transactional
	public void insertInterpreting(UUID runId, UUID requestKey, String requestHash, long baseStateVersion, UUID leaseOwner,
			Instant leaseUntil, Instant now) {
		jdbc.sql("INSERT INTO run_turn (run_id, request_key, request_hash, base_state_version, status, lease_owner, lease_until, created_at) "
				+ "VALUES (?, ?, ?, ?, 'INTERPRETING', ?, ?, ?)")
				.params(runId, requestKey, requestHash, baseStateVersion, leaseOwner, ts(leaseUntil), ts(now)).update();
	}

	/** Stores a request that was answered without starting a turn (a stale view). */
	@Transactional
	public void insertFinished(UUID runId, UUID requestKey, String requestHash, long baseStateVersion, TurnStatus status,
			int responseStatus, String responseJson, Instant now) {
		requireFinal(status);
		jdbc.sql("INSERT INTO run_turn (run_id, request_key, request_hash, base_state_version, status, response_status, response, created_at) "
				+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
				.params(runId, requestKey, requestHash, baseStateVersion, status.name(), responseStatus, responseJson, ts(now)).update();
	}

	/** Takes over an INTERPRETING record whose lease has expired. */
	@Transactional
	public boolean takeOver(UUID runId, UUID requestKey, UUID newOwner, Instant leaseUntil, Instant now) {
		return jdbc.sql("UPDATE run_turn SET lease_owner = ?, lease_until = ? WHERE run_id = ? AND request_key = ? "
				+ "AND status = 'INTERPRETING' AND lease_until < ?")
				.params(newOwner, ts(leaseUntil), runId, requestKey, ts(now)).update() == 1;
	}

	/** Deletes another request's abandoned INTERPRETING record (expired lease; it committed nothing). */
	@Transactional
	public boolean deleteAbandoned(UUID runId, UUID requestKey, Instant now) {
		return jdbc.sql("DELETE FROM run_turn WHERE run_id = ? AND request_key = ? AND status = 'INTERPRETING' AND lease_until < ?")
				.params(runId, requestKey, ts(now)).update() == 1;
	}

	/** Deletes this owner's INTERPRETING record so the key can be retried (transient AI failure). */
	@Transactional
	public boolean release(UUID runId, UUID requestKey, UUID leaseOwner) {
		return jdbc.sql("DELETE FROM run_turn WHERE run_id = ? AND request_key = ? AND status = 'INTERPRETING' AND lease_owner = ?")
				.params(runId, requestKey, leaseOwner).update() == 1;
	}

	/** Finishes this owner's INTERPRETING record with a stored error (REJECTED or STALE). */
	@Transactional
	public boolean finish(UUID runId, UUID requestKey, UUID leaseOwner, TurnStatus status, int responseStatus, String responseJson) {
		requireFinal(status);
		return jdbc.sql("UPDATE run_turn SET status = ?, response_status = ?, response = ?, lease_owner = NULL, "
				+ "lease_until = NULL WHERE run_id = ? AND request_key = ? AND status = 'INTERPRETING' AND lease_owner = ?")
				.params(status.name(), responseStatus, responseJson, runId, requestKey, leaseOwner).update() == 1;
	}

	/** Marks this owner's turn as mechanically committed; joins the mechanics transaction. */
	@Transactional
	public boolean commitMechanics(UUID runId, UUID requestKey, UUID leaseOwner, int turnNumber, Optional<EnemyAction> enemy,
			String summaryJson) {
		return jdbc.sql("UPDATE run_turn SET status = 'MECHANICS_COMMITTED', turn_number = ?, enemy_scene_id = ?, "
				+ "enemy_entity_id = ?, enemy_choice = ?, mechanics_summary = CAST(? AS jsonb), lease_owner = NULL, lease_until = NULL "
				+ "WHERE run_id = ? AND request_key = ? AND status = 'INTERPRETING' AND lease_owner = ?")
				.params(turnNumber, enemy.map(EnemyAction::sceneId).orElse(null), enemy.map(EnemyAction::entityId).orElse(null),
						enemy.map(EnemyAction::choice).orElse(null), summaryJson, runId, requestKey, leaseOwner)
				.update() == 1;
	}

	/** Claims narration of a committed turn if nobody holds a live narration lease. */
	@Transactional
	public boolean claimNarration(UUID runId, UUID requestKey, UUID owner, Instant leaseUntil, Instant now) {
		return jdbc.sql("UPDATE run_turn SET narration_owner = ?, narration_lease_until = ? WHERE run_id = ? AND request_key = ? "
				+ "AND status = 'MECHANICS_COMMITTED' AND (narration_owner IS NULL OR narration_lease_until < ?)")
				.params(owner, ts(leaseUntil), runId, requestKey, ts(now)).update() == 1;
	}

	/** Completes a committed turn with its response, fenced on the narration owner. */
	@Transactional
	public boolean complete(UUID runId, UUID requestKey, UUID narrationOwner, int responseStatus, String responseJson) {
		return jdbc.sql("UPDATE run_turn SET status = 'COMPLETED', response_status = ?, response = ?, "
				+ "narration_owner = NULL, narration_lease_until = NULL "
				+ "WHERE run_id = ? AND request_key = ? AND status = 'MECHANICS_COMMITTED' AND narration_owner = ?")
				.params(responseStatus, responseJson, runId, requestKey, narrationOwner).update() == 1;
	}

	/** Gives up this owner's narration lease after a failed attempt, so a retry need not wait for it to expire. */
	@Transactional
	public void releaseNarration(UUID runId, UUID requestKey, UUID owner) {
		jdbc.sql("UPDATE run_turn SET narration_owner = NULL, narration_lease_until = NULL WHERE run_id = ? AND request_key = ? "
				+ "AND status = 'MECHANICS_COMMITTED' AND narration_owner = ?").params(runId, requestKey, owner).update();
	}

	/** An enemy's own recent choices in its scene (option codes or HOLD), oldest first. */
	@Transactional(readOnly = true)
	public List<String> recentEnemyChoices(UUID runId, UUID sceneId, String entityId, int limit) {
		List<String> newestFirst = jdbc.sql("SELECT enemy_choice FROM run_turn WHERE run_id = ? AND enemy_scene_id = ? "
				+ "AND enemy_entity_id = ? AND turn_number IS NOT NULL ORDER BY turn_number DESC LIMIT ?")
				.params(runId, sceneId, entityId, limit).query(String.class).list();
		List<String> oldestFirst = new ArrayList<>(newestFirst);
		Collections.reverse(oldestFirst);
		return List.copyOf(oldestFirst);
	}

	private static void requireFinal(TurnStatus status) {
		if (status != TurnStatus.REJECTED && status != TurnStatus.STALE) {
			throw new IllegalArgumentException("Only REJECTED or STALE are stored without mechanics, not " + status);
		}
	}

	private static Timestamp ts(Instant instant) {
		return Timestamp.from(instant);
	}

	private static TurnRecord map(ResultSet rs, int row) throws SQLException {
		int turnNumber = rs.getInt("turn_number");
		OptionalInt turn = rs.wasNull() ? OptionalInt.empty() : OptionalInt.of(turnNumber);
		int responseStatus = rs.getInt("response_status");
		OptionalInt status = rs.wasNull() ? OptionalInt.empty() : OptionalInt.of(responseStatus);
		return new TurnRecord(rs.getObject("run_id", UUID.class), rs.getObject("request_key", UUID.class),
				rs.getString("request_hash"), rs.getLong("base_state_version"), TurnStatus.valueOf(rs.getString("status")),
				Optional.ofNullable(rs.getObject("lease_owner", UUID.class)), instant(rs, "lease_until"),
				Optional.ofNullable(rs.getObject("narration_owner", UUID.class)), instant(rs, "narration_lease_until"),
				turn, Optional.ofNullable(rs.getString("summary")), status, Optional.ofNullable(rs.getString("response_json")));
	}

	private static Optional<Instant> instant(ResultSet rs, String column) throws SQLException {
		return Optional.ofNullable(rs.getTimestamp(column)).map(Timestamp::toInstant);
	}

	/** One stored turn request. */
	public record TurnRecord(UUID runId, UUID requestKey, String requestHash, long baseStateVersion, TurnStatus status,
			Optional<UUID> leaseOwner, Optional<Instant> leaseUntil, Optional<UUID> narrationOwner,
			Optional<Instant> narrationLeaseUntil, OptionalInt turnNumber, Optional<String> summaryJson,
			OptionalInt responseStatus, Optional<String> responseJson) {
	}

	/** The enemy that acted on a turn and what it chose (an option code or HOLD). */
	public record EnemyAction(UUID sceneId, String entityId, String choice) {
		public EnemyAction {
			Objects.requireNonNull(sceneId, "sceneId");
			Objects.requireNonNull(entityId, "entityId");
			Objects.requireNonNull(choice, "choice");
		}
	}
}
