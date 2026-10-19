package com.slayerbis;

import com.slayerbis.calc.CalcResult;
import com.slayerbis.calc.PlayerSetup;
import com.slayerbis.calc.StyleResult;
import com.slayerbis.data.AttackType;
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
}
