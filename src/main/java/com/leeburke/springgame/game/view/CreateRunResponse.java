package com.leeburke.springgame.game.view;

import java.util.UUID;

/** A created (or resumed) run. The client already holds its token; it is never echoed. */
public record CreateRunResponse(UUID runId, GameView view) {
}
