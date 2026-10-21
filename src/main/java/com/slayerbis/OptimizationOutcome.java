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
import java.util.Set;
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
	/** The least the worn set must weigh for this task, in grams; 0 for no limit. */
	int minWeight;
	/** Styles where nothing owned is heavy enough, so the best set shown is too light. */
	Set<AttackType> tooLight;
	/** Per style, the DPS given up to wear a Serpentine helm against poison; only where it was worn. */
	Map<AttackType, Double> serpentineCost;
	/** Magic and Burst: the set with each spell it can cast, best first. */
	Map<AttackType, List<StyleResult.Alternative>> spellOptions;
	/** Per style, the boss's recommended items that were kept on, and what they cost. */
	Map<AttackType, List<String>> keptNotes;
	/** Boss whole fight only: the picked monster first, then each other monster or phase with its switches. */
	List<Phase> phases;

	/**
	 * The style the inventory was planned for: the best with the ammo and spell picked at the
	 * time. A pick that changes the best style plans again.
	 */
	AttackType plannedType;

	@Value
	static class ChargeHint
	{
		double dps;
		/** Names of the owned items that would need charging or repairing. */
		List<String> items;
	}

	/**
	 * One monster or phase of a boss fight, with the style and DPS of its best set and the items that
	 * set adds to the main one.
	 */
	@Value
	static class Phase
	{
		Monster monster;
		AttackType type;
		double dps;
		List<Optimizer.Candidate> switches;
		/** Switches left out because keeping the main set's items cost little DPS. */
		int switchesSaved;
		/** DPS given up against this monster to carry fewer switches. */
		double dpsGivenUp;
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
	 * The result for a type with the ammo and spell picked in the panel.
	 */
	StyleResult adjusted(AttackType type, Integer ammoId, String spell)
	{
		return withSpell(withAmmo(results.get(type), ammoId), spell);
	}

	/**
	 * The best style once the ammo and spell picked in the panel are swapped in, which can make
	 * another style the best.
	 */
	StyleResult best(Integer ammoId, String spell)
	{
		return best(results, ammoId, spell, ammoOptions, spellOptions);
	}

	/**
	 * The best style with the ammo and spell picked in the panel, before an outcome exists.
	 */
	static StyleResult best(Map<AttackType, StyleResult> results, Integer ammoId, String spell,
		List<StyleResult.Alternative> ammoOptions, Map<AttackType, List<StyleResult.Alternative>> spellOptions)
	{
		StyleResult best = null;
		for (StyleResult result : results.values())
		{
			StyleResult r = withSpell(withAmmo(result, ammoId, ammoOptions), spell, spellOptions);
			if (r.isValid() && (best == null || r.getResult().getDps() > best.getResult().getDps()))
			{
				best = r;
			}
		}
		return best;
	}

	/**
	 * The result for a type with the panel's picks, or the best when type is null.
	 */
	StyleResult resultFor(AttackType type, Integer ammoId, String spell)
	{
		return type == null ? best(ammoId, spell) : adjusted(type, ammoId, spell);
	}

	/**
	 * The result for a type, or the overall best when type is null.
	 */
	StyleResult resultFor(AttackType type)
	{
		return type == null ? best() : results.get(type);
	}

	/**
	 * A magic or burst result with the chosen spell, or the result itself when no spell is chosen
	 * or it isn't one of the options for that style.
	 */
	StyleResult withSpell(StyleResult r, String spell)
	{
		return withSpell(r, spell, spellOptions);
	}

	static StyleResult withSpell(StyleResult r, String spell, Map<AttackType, List<StyleResult.Alternative>> spellOptions)
	{
		if (r == null || spell == null)
		{
			return r;
		}
		for (StyleResult.Alternative alt : spellOptions.getOrDefault(r.getType(), List.of()))
		{
			if (spell.equals(alt.getResult().getSpell().getName()))
			{
				return new StyleResult(r.getType(), alt.getResult(), r.getGear(), r.getCombinationsTried(),
					r.getItemsConsidered(), r.getItemsAfterPruning(), r.getSetsConsidered(), r.isStoppedEarly(),
					r.getAlternatives());
			}
		}
		return r;
	}

	/**
	 * The ranged result with the chosen ammo swapped in, or the result itself when no ammo is
	 * chosen or it isn't one of the options.
	 */
	StyleResult withAmmo(StyleResult r, Integer ammoId)
	{
		return withAmmo(r, ammoId, ammoOptions);
	}

	static StyleResult withAmmo(StyleResult r, Integer ammoId, List<StyleResult.Alternative> ammoOptions)
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
