package com.leeburke.springgame.mechanics;

/** Local functional state of a body part. Escalation rules are deferred. */
public enum BodySeverity {
	HEALTHY,
	INJURED,
	WOUNDED,
	CRIPPLED,
	DESTROYED
}
