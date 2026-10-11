package com.leeburke.springgame.world;

import java.util.List;
import java.util.Objects;

import com.leeburke.springgame.shared.DefinitionCodes;

/**
 * The state of a placed container object: whether it is open, and the item codes it still holds
 * (at most one in this slice). Generated with the scene, persisted, and changed only by resolved
 * interactions. Contents are hidden from the player while the container is closed.
 */
public record ContainerState(String objectId, boolean open, List<String> contents) {

	public ContainerState {
		LocalIds.requireLocalId(objectId, "Container object id");
		contents = List.copyOf(Objects.requireNonNull(contents, "contents"));
		contents.forEach(code -> DefinitionCodes.requireCode(code, "Container item"));
	}

	public ContainerState opened() {
		return new ContainerState(objectId, true, contents);
	}

	public ContainerState without(String itemCode) {
		List<String> left = new java.util.ArrayList<>(contents);
		if (!left.remove(itemCode)) {
			throw new IllegalArgumentException("Container " + objectId + " does not hold " + itemCode);
		}
		return new ContainerState(objectId, open, left);
	}
}
