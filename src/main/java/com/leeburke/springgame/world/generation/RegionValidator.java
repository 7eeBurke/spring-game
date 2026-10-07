package com.leeburke.springgame.world.generation;

import java.util.List;

/** Validates a complete generated region; an empty result means valid. */
@FunctionalInterface
public interface RegionValidator {

	List<String> problems(GeneratedRegion candidate);
}
