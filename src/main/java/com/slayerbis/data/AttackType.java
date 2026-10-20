package com.slayerbis.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The combat styles the optimiser searches. Each one is optimised separately. BURST is magic with
 * an Ancient burst or barrage on a stack of monsters; the calculator itself only ever sees MAGIC.
 */
@Getter
@RequiredArgsConstructor
public enum AttackType
{
	STAB("Stab"),
	SLASH("Slash"),
	CRUSH("Crush"),
	RANGED("Ranged"),
	MAGIC("Magic"),
	BURST("Burst");

	private final String displayName;

	public boolean isMelee()
	{
		return this == STAB || this == SLASH || this == CRUSH;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
