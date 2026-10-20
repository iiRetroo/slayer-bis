package com.slayerbis.calc;

import lombok.Builder;
import lombok.Value;

/**
 * Player levels and assumed buffs for a calculation. Levels already include any potion boost.
 */
@Value
@Builder(toBuilder = true)
public class PlayerSetup
{
	int attack;
	int strength;
	int ranged;
	int magic;
	int hitpoints;

	/** Prayer factors as numerator over 100; 100 means no prayer. */
	@Builder.Default
	int meleeAccuracyPrayer = 100;
	@Builder.Default
	int meleeStrengthPrayer = 100;
	@Builder.Default
	int rangedAccuracyPrayer = 100;
	@Builder.Default
	int rangedStrengthPrayer = 100;
	@Builder.Default
	int magicAccuracyPrayer = 100;
	/** Magic damage from prayer, in tenths of a percent. */
	int magicDamagePrayer;

	boolean onSlayerTask;
	boolean ancientSpellbook;
	boolean kandarinHardDiary;
	/** Ranged strength of the darts assumed to be loaded in a blowpipe. */
	int blowpipeDartStrength;
	/**
	 * When above 0, magic means casting an Ancient burst or barrage that hits this many monsters:
	 * any weapon can cast it manually, and DPS is per-target DPS times this.
	 */
	int burstTargets;
}
