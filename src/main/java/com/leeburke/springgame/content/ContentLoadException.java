package com.leeburke.springgame.content;

/** Static content could not be loaded. The message names the resource or directory involved. */
public class ContentLoadException extends RuntimeException {

	public ContentLoadException(String message) {
		super(message);
	}

	public ContentLoadException(String message, Throwable cause) {
		super(message, cause);
	}
}
