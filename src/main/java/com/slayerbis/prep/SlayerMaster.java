package com.slayerbis.prep;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The Slayer master the player goes back to, for a teleport to them. The names are the keys of
 * "masters" in tasks.json.
 */
@Getter
@RequiredArgsConstructor
public enum SlayerMaster
{
	NONE("None"),
	TURAEL("Turael"),
	SPRIA("Spria"),
	KRYSTILIA("Krystilia"),
	MAZCHNA("Mazchna"),
	VANNAKA("Vannaka"),
	CHAELDAR("Chaeldar"),
	KONAR("Konar"),
	NIEVE("Nieve"),
	DURADEL("Duradel"),
	MORTIMER("Mortimer");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}
}
