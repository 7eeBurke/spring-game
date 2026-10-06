package com.leeburke.springgame.mechanics;

/**
 * How much local trauma an impact caused. Distinct from {@link ContactQuality}.
 */
public enum ImpactSeverity {
	GLANCING,
	SOLID,
	SEVERE,
	DEVASTATING
}
