package com.leeburke.springgame.game.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.leeburke.springgame.ai.narration.EnemyAttackNarration;
import com.leeburke.springgame.ai.narration.LoreCatalog;
import com.leeburke.springgame.ai.narration.NarrationFact;
import com.leeburke.springgame.content.world.FixedSceneDefinition;
import com.leeburke.springgame.content.world.WorldContentCatalog;
import com.leeburke.springgame.game.RunSession;
import com.leeburke.springgame.game.RunStatus;
import com.leeburke.springgame.game.TurnStatus;
import com.leeburke.springgame.game.view.ChronicleView;
import com.leeburke.springgame.game.view.GameView.NarrationView;
import com.leeburke.springgame.persistence.IntroductionStore;
import com.leeburke.springgame.persistence.RunSessionStore;
import com.leeburke.springgame.persistence.TurnStore;
import com.leeburke.springgame.persistence.TurnStore.TurnRecord;
import com.leeburke.springgame.world.SceneZone;
import com.leeburke.springgame.world.generation.RunWorldGenerator;

/**
 * Rebuilds a run's story from what Stage 14 already stores, without a second history table:
 * <ul>
 * <li>the introduction ({@code character_introduction});</li>
 * <li>each mechanically committed turn ({@code run_turn}, unique per turn number, so retries and
 * crash recovery can never duplicate an entry): the player's accepted wording
 * ({@code player_input}), the confirmed changes from its {@code mechanics_summary}, and, once
 * finalised, the narrations from its stored response.</li>
 * </ul>
 * An attack's Java cue is rebuilt from the summary, so it stays in history after the pending attack
 * is consumed. A turn whose narration is not finalised yet is shown as pending; nothing is narrated,
 * resolved or written here.
 */
@Service
public class ChronicleService {

	public static final int DEFAULT_LIMIT = 20;
	public static final int MAX_LIMIT = 50;

	private final RunSessionStore sessions;
	private final TurnStore turns;
	private final IntroductionStore introductions;
	private final WorldContentCatalog world;
	private final LoreCatalog lore;
	private final TurnJson json = new TurnJson();
	private final StoredResponses responses = new StoredResponses();

	ChronicleService(RunSessionStore sessions, TurnStore turns, IntroductionStore introductions, WorldContentCatalog world,
			LoreCatalog lore) {
		this.sessions = Objects.requireNonNull(sessions, "sessions");
		this.turns = Objects.requireNonNull(turns, "turns");
		this.introductions = Objects.requireNonNull(introductions, "introductions");
		this.world = Objects.requireNonNull(world, "world");
		this.lore = Objects.requireNonNull(lore, "lore");
	}

	/**
	 * @param before only turns numbered below this (the previous page's {@code nextBefore}); empty for the latest page
	 * @param limit  1 to {@value #MAX_LIMIT} turns
	 */
	@Transactional(readOnly = true)
	public ChronicleView chronicle(UUID runId, Optional<Integer> before, int limit) {
		if (limit < 1 || limit > MAX_LIMIT) {
			throw new GameException(ErrorCode.INVALID_REQUEST, "limit must be between 1 and " + MAX_LIMIT + ".");
		}
		if (before.filter(b -> b < 1).isPresent()) {
			throw new GameException(ErrorCode.INVALID_REQUEST, "before must be a turn number of at least 1.");
		}
		RunSession session = sessions.find(runId).orElseThrow(() -> new GameException(ErrorCode.RUN_NOT_FOUND, "Run not found."));
		if (session.status() == RunStatus.INITIALIZING) {
			throw new GameException(ErrorCode.RUN_INITIALIZING, "The run is still being created; retry its creation request.");
		}

		List<TurnRecord> newestFirst = turns.findCommittedBefore(runId, before.orElse(Integer.MAX_VALUE), limit + 1);
		boolean older = newestFirst.size() > limit;
		List<TurnRecord> page = new ArrayList<>(newestFirst.subList(0, Math.min(limit, newestFirst.size())));
		Collections.reverse(page);

		List<ChronicleView.Turn> entries = page.stream().map(this::turn).toList();
		Integer nextBefore = older ? page.getFirst().turnNumber().getAsInt() : null;
		ChronicleView.Opening opening = older ? null : opening(runId);
		return new ChronicleView(runId, session.status().name(), session.turnNumber(), opening, entries, nextBefore);
	}

	private ChronicleView.Opening opening(UUID runId) {
		NarrationView introduction = introductions.find(runId).map(i -> new NarrationView(i.text(), i.source().name())).orElse(null);
		// Every run starts at the hub's start zone (Stage 9 generation).
		FixedSceneDefinition hub = world.findFixedScene(RunWorldGenerator.HUB_CODE)
				.orElseThrow(() -> new IllegalStateException("The hub definition is missing"));
		String zone = hub.zones().stream().filter(z -> z.id().equals(hub.startZone())).map(SceneZone::displayName).findFirst()
				.orElseThrow();
		return new ChronicleView.Opening(introduction, lore.objective(), hub.displayName(), zone);
	}

	private ChronicleView.Turn turn(TurnRecord record) {
		MechanicsSummary summary = json.read(record.summaryJson().orElseThrow(), MechanicsSummary.class);
		boolean completed = record.status() == TurnStatus.COMPLETED;
		Optional<String> response = completed ? record.responseJson() : Optional.empty();

		ChronicleView.Action action = record.playerInput()
				.map(text -> new ChronicleView.Action(text, text.strip().startsWith("/") ? "COMMAND" : "FREE_TEXT")).orElse(null);
		ChronicleView.Place entered = Optional.ofNullable(summary.enteredScene())
				.map(scene -> new ChronicleView.Place(scene, arrivalZone(summary))).orElse(null);
		NarrationView narration = response.flatMap(responses::narration).orElse(null);

		ChronicleView.Enemy enemy = null;
		if (summary.enemyTurn() != null) {
			boolean attacked = summary.attack() != null;
			String cue = attacked ? EnemyAttackNarration.cueTextOf(summary.attack().context().cue()) : null;
			NarrationView attackNarration = attacked ? response.flatMap(responses::pendingAttackNarration).orElse(null) : null;
			enemy = new ChronicleView.Enemy(summary.enemyTurn().attacker(), summary.enemyTurn().action(), cue, attackNarration);
		}
		String ending = summary.runStatus().terminal() ? summary.runStatus().name() : null;
		return new ChronicleView.Turn(summary.turnNumber(), action, entered, summary.movedTo(), narration, !completed, enemy, ending);
	}

	/** The zone arrived in, as the confirmed narration fact named it. */
	/** The arrival zone's label, from the crossing fact (or, in turns stored before it existed, the older one). */
	private static String arrivalZone(MechanicsSummary summary) {
		return summary.narration().facts().stream()
				.map(f -> switch (f) {
					case NarrationFact.CrossedInto crossed -> crossed.arrival().label();
					case NarrationFact.PlayerLeftScene left -> left.arrivalZone();
					default -> null;
				})
				.filter(java.util.Objects::nonNull).findFirst().orElse(null);
	}
}
