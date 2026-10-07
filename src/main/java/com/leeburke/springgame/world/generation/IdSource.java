package com.leeburke.springgame.world.generation;

import java.util.UUID;

/**
 * Supplies runtime identities for generated instances. Kept separate from procedural randomness:
 * drawing an ID never consumes or perturbs a gameplay random stream. Runtime UUIDs are persistence
 * identity, not part of the reproducibility contract.
 */
@FunctionalInterface
public interface IdSource {

	UUID next();

	static IdSource random() {
		return UUID::randomUUID;
	}
}
