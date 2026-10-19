package com.slayerbis.calc;

import lombok.Value;

@Value
public class CalcResult
{
	public static final CalcResult NONE = new CalcResult(0, 0, 0, 0, null, null);

	double dps;
	int maxHit;
	double accuracy;
	int attackSpeedTicks;
	Stance stance;
	/** Autocast spell, or null for weapons that do not cast spells. */
	Spell spell;
}
