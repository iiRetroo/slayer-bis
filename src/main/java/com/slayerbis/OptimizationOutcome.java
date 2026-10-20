package com.slayerbis;

import com.slayerbis.calc.CalcResult;
import com.slayerbis.calc.Optimizer;
import com.slayerbis.calc.PlayerSetup;
import com.slayerbis.calc.StyleResult;
import com.slayerbis.data.AttackType;
import com.slayerbis.data.EquipmentSlot;
import com.slayerbis.data.Monster;
import com.slayerbis.prep.PrepPlan;
import java.util.List;
import java.util.Map;
import lombok.Value;

/**
 * Everything the panel and overlay need from one finished calculation. Immutable once built.
 */
@Value
class OptimizationOutcome
{
	String task;
	Monster monster;
	String targetSource;
	/** Monsters the task could mean, for the panel's picker. */
	List<Monster> monsterChoices;
	Map<AttackType, StyleResult> results;
	Map<Integer, ItemSource> sources;
	PlayerSetup setup;
	int itemsScanned;
	/** Per style, what charging or repairing owned items would add; only present when it beats the real result. */
	Map<AttackType, ChargeHint> chargeHints;
	/** DPS of the gear currently worn, in its best style; null if nothing worn can attack. */
	CurrentGear currentGear;
	/** Requirements, inventory and teleports; only the requirements when preparation is turned off. */
	PrepPlan prep;
	/** Kills left on the task when this was calculated, or 0 if unknown. */
	int taskRemaining;
	/** Monsters each burst or barrage is assumed to hit, for the Burst result. */
	int burstTargets;
	/** Per style, the DPS given up to keep the Slayer helmet on; only where it was kept. */
	Map<AttackType, Double> helmetCost;
	/** The ranged set with each owned ammo swapped in, best first; empty when it takes none. */
	List<StyleResult.Alternative> ammoOptions;

	@Value
	static class ChargeHint
	{
		double dps;
		/** Names of the owned items that would need charging or repairing. */
		List<String> items;
	}

	@Value
	static class CurrentGear
	{
		AttackType type;
		CalcResult result;
	}

	StyleResult best()
	{
		StyleResult best = null;
		for (StyleResult r : results.values())
		{
			if (r.isValid() && (best == null || r.getResult().getDps() > best.getResult().getDps()))
			{
				best = r;
			}
		}
		return best;
	}

	/**
	 * The result for a type, or the overall best when type is null.
	 */
	StyleResult resultFor(AttackType type)
	{
		return type == null ? best() : results.get(type);
	}

	/**
	 * The ranged result with the chosen ammo swapped in, or the result itself when no ammo is
	 * chosen or it isn't one of the options.
	 */
	StyleResult withAmmo(StyleResult r, Integer ammoId)
	{
		if (r == null || ammoId == null || r.getType() != AttackType.RANGED)
		{
			return r;
		}
		for (StyleResult.Alternative alt : ammoOptions)
		{
			Optimizer.Candidate ammo = alt.getGear()[EquipmentSlot.AMMO.ordinal()];
			if (ammo != null && ammo.getOwnedItemId() == ammoId)
			{
				return new StyleResult(r.getType(), alt.getResult(), alt.getGear(), r.getCombinationsTried(),
					r.getItemsConsidered(), r.getItemsAfterPruning(), r.getSetsConsidered(), r.isStoppedEarly(),
					r.getAlternatives());
			}
		}
		return r;
	}
}
