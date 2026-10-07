package com.leeburke.springgame.world.generation;

/** No valid world could be generated within the bounded number of deterministic attempts. */
public class GenerationException extends RuntimeException {

	public GenerationException(String message) {
		super(message);
	}
}
