package com.leeburke.springgame.game.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.leeburke.springgame.ai.narration.EnemyAttackNarration;
import com.leeburke.springgame.ai.narration.EnemyAttackNarrator;
import com.leeburke.springgame.ai.narration.Narration;
import com.leeburke.springgame.ai.narration.OutcomeNarrator;
import com.leeburke.springgame.config.GameApiProperties;
import com.leeburke.springgame.game.PendingAttack;
import com.leeburke.springgame.game.TurnStatus;
import com.leeburke.springgame.game.view.GameView;
import com.leeburke.springgame.game.view.TurnResponse;
import com.leeburke.springgame.persistence.PendingAttackStore;
import com.leeburke.springgame.persistence.TurnStore;
import com.leeburke.springgame.persistence.TurnStore.TurnRecord;

/**
 * Turns a MECHANICS_COMMITTED turn into a COMPLETED one: claims the narration lease, calls the
 * narrators with no transaction open, then stores the narrations and the response in one short
 * transaction fenced on the lease. Works only from the stored mechanics summary, so it also
 * recovers a turn whose request crashed after its mechanics committed. A concurrent finaliser gets
 * REQUEST_IN_PROGRESS rather than a second, paid narration.
 */
@Component
class NarrationFinalizer {

	private final TurnStore turns;
	private final PendingAttackStore pendingAttacks;
	private final GameViewAssembler views;
	private final OutcomeNarrator outcomeNarrator;
	private final EnemyAttackNarrator attackNarrator;
	private final TransactionTemplate transactions;
	private final Clock clock;
	private final Duration lease;
	private final TurnJson json = new TurnJson();

	NarrationFinalizer(TurnStore turns, PendingAttackStore pendingAttacks, GameViewAssembler views,
			OutcomeNarrator outcomeNarrator, EnemyAttackNarrator attackNarrator, TransactionTemplate transactions, Clock clock,
			GameApiProperties properties) {
		this.turns = Objects.requireNonNull(turns, "turns");
		this.pendingAttacks = Objects.requireNonNull(pendingAttacks, "pendingAttacks");
		this.views = Objects.requireNonNull(views, "views");
		this.outcomeNarrator = Objects.requireNonNull(outcomeNarrator, "outcomeNarrator");
		this.attackNarrator = Objects.requireNonNull(attackNarrator, "attackNarrator");
		this.transactions = Objects.requireNonNull(transactions, "transactions");
		this.clock = Objects.requireNonNull(clock, "clock");
		this.lease = properties.lease();
	}

	TurnReply finalizeTurn(UUID runId, UUID requestKey) {
		TurnRecord record = turns.find(runId, requestKey)
				.orElseThrow(() -> new IllegalStateException("Turn " + requestKey + " disappeared"));
		if (record.status() == TurnStatus.COMPLETED) {
			return stored(record);
		}
		if (record.status() != TurnStatus.MECHANICS_COMMITTED) {
			throw new IllegalStateException("Turn " + requestKey + " is " + record.status() + ", not committed");
		}
		UUID owner = UUID.randomUUID();
		Instant now = clock.instant();
		if (!turns.claimNarration(runId, requestKey, owner, now.plus(lease), now)) {
			Optional<TurnRecord> again = turns.find(runId, requestKey).filter(r -> r.status() == TurnStatus.COMPLETED);
			if (again.isPresent()) {
				return stored(again.get());
			}
			throw inProgress();
		}

		try {
			return narrateAndComplete(runId, requestKey, owner, json.read(record.summaryJson().orElseThrow(), MechanicsSummary.class));
		} catch (RuntimeException e) {
			// The mechanics stay committed; let the next attempt narrate without waiting for the lease.
			turns.releaseNarration(runId, requestKey, owner);
			throw e;
		}
	}

	private TurnReply narrateAndComplete(UUID runId, UUID requestKey, UUID owner, MechanicsSummary summary) {
		// No transaction is open while the narrators (possibly remote models) run.
		Narration narration = outcomeNarrator.narrate(summary.narration());
		Optional<EnemyAttackNarration> attack = Optional.ofNullable(summary.attack()).map(a -> attackNarrator.narrate(a.context()));

		String response = transactions.execute(status -> {
			attack.ifPresent(a -> pendingAttacks.setNarration(runId, summary.attack().attackRef(),
					new PendingAttack.StoredNarration(a.prose().text(), a.prose().source())));
			GameView.NarrationView narrationView = new GameView.NarrationView(narration.text(), narration.source().name());
			GameView view = views.viewAfter(runId, new GameView.LastTurnView(summary.turnNumber(), narrationView));
			String body = json.write(response(summary, narrationView, view));
			if (!turns.complete(runId, requestKey, owner, 200, body)) {
				status.setRollbackOnly();
				return null;
			}
			return body;
		});
		if (response == null) {
			throw inProgress();
		}
		return new TurnReply(200, response);
	}

	static TurnResponse response(MechanicsSummary summary, GameView.NarrationView narration, GameView view) {
		TurnResponse.Changes changes = new TurnResponse.Changes(summary.playerHpLost(), summary.enemiesDefeated(),
				summary.movedTo(), summary.enteredScene());
		TurnResponse.EnemyTurn enemy = Optional.ofNullable(summary.enemyTurn())
				.map(e -> new TurnResponse.EnemyTurn(e.attacker(), e.action())).orElse(null);
		return new TurnResponse(summary.turnNumber(), summary.overall().name(), narration, changes, enemy, view);
	}

	private static TurnReply stored(TurnRecord record) {
		return new TurnReply(record.responseStatus().orElseThrow(), record.responseJson().orElseThrow());
	}

	private static GameException inProgress() {
		return new GameException(ErrorCode.REQUEST_IN_PROGRESS, "This turn is still being narrated; retry shortly.");
	}
}
