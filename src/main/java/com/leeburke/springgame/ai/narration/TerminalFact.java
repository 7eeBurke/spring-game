package com.leeburke.springgame.ai.narration;

import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** A run-ending fact supplied by Java. The narrator only describes it. */
@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, property = "fact")
public sealed interface TerminalFact {

	/** The player died; the attacker's visible name when known. */
	record PlayerDied(Optional<String> attackerName) implements TerminalFact {
		public PlayerDied {
			Objects.requireNonNull(attackerName, "attackerName");
		}
	}

	/** The boss was defeated. */
	record GuardianDefeated(String name) implements TerminalFact {
		public GuardianDefeated {
			Objects.requireNonNull(name, "name");
		}
	}
}
