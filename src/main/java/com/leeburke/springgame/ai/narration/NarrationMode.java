package com.leeburke.springgame.ai.narration;

/** The Outcome Narrator's modes. The AI never decides that a run has ended; Java chooses the mode. */
public enum NarrationMode {
	NORMAL,
	RUN_DEATH,
	RUN_VICTORY
}
