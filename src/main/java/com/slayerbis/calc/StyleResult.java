package com.slayerbis.calc;

import com.slayerbis.data.AttackType;
import java.util.List;
import lombok.Value;

/**
 * The best loadout found for one attack type.
 */
@Value
public class StyleResult
{
	AttackType type;
	CalcResult result;
	/** Indexed by EquipmentSlot ordinal; null entries are empty slots. Null if nothing could attack. */
	Optimizer.Candidate[] gear;
	long combinationsTried;
	int itemsConsidered;
	int itemsAfterPruning;
	List<String> setsConsidered;
	boolean stoppedEarly;
	/** The best set for each other weapon, best first. */
	List<Alternative> alternatives;

	@Value
	public static class Alternative
	{
		CalcResult result;
		Optimizer.Candidate[] gear;
	}

	public boolean isValid()
	{
		return gear != null && result.getDps() > 0;
	}
}
