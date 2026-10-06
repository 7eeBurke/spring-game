package com.leeburke.springgame.mechanics;

/**
 * How well an attack connected. Distinct from {@link ImpactSeverity}, which measures local trauma.
 */
public enum ContactQuality {
	NONE,
	GLANCING,
	SOLID,
	CLEAN
}
