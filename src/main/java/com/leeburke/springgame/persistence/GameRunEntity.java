package com.leeburke.springgame.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JPA mapping of {@code game_run}. Persistence-only; the domain read model is {@code GameRun}. */
@Entity
@Table(name = "game_run")
public class GameRunEntity {

	@Id
	@Column(name = "id", nullable = false)
	private UUID id;

	@Column(name = "run_seed", nullable = false)
	private long runSeed;

	protected GameRunEntity() {
		// for JPA
	}

	GameRunEntity(UUID id, long runSeed) {
		this.id = id;
		this.runSeed = runSeed;
	}

	UUID getId() {
		return id;
	}

	long getRunSeed() {
		return runSeed;
	}
}
