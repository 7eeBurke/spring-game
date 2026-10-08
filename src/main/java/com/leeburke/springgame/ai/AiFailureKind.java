package com.leeburke.springgame.ai;

/** Why an AI call produced no usable output. Never carries provider messages, keys or bodies. */
public enum AiFailureKind {
	/** AI is switched off. */
	DISABLED,
	/** AI is switched on but the key or model is missing. */
	NOT_CONFIGURED,
	TIMEOUT,
	AUTHENTICATION,
	RATE_LIMITED,
	PROVIDER_ERROR,
	NETWORK,
	/** The model declined to answer. */
	REFUSED,
	/** The output was cut off, for example at the token limit. */
	TRUNCATED,
	/** The provider answered, but without usable output. */
	MALFORMED_RESPONSE
}
