package com.leeburke.springgame.world;

import java.util.Objects;

/** Marks one piece of scene content, identified by kind and local ID, as hidden from the player. */
public record HiddenContentRef(HiddenContentKind kind, String localId) {

	public HiddenContentRef {
		Objects.requireNonNull(kind, "kind");
		LocalIds.requireLocalId(localId, "Hidden content id");
	}
}
