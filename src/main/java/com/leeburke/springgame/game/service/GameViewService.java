package com.leeburke.springgame.game.service;

import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.leeburke.springgame.game.view.GameView;

/** Read-only access to a run's view for the API: no AI calls, no writes, no finalisation. */
@Service
public class GameViewService {

	private final GameViewAssembler assembler;

	GameViewService(GameViewAssembler assembler) {
		this.assembler = Objects.requireNonNull(assembler, "assembler");
	}

	public GameView view(UUID runId) {
		return assembler.view(runId);
	}
}
