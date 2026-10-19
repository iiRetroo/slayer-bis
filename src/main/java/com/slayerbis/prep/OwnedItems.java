package com.slayerbis.prep;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Names and quantities of everything the player owns (bank, inventory and worn), plus the bank
 * item ids for each name so supplies can be highlighted. Built on the client thread; read-only after.
 */
public final class OwnedItems
{
	private final Map<String, Integer> quantities;
	private final Map<String, Set<Integer>> bankIds;

	public OwnedItems(Map<String, Integer> quantities, Map<String, Set<Integer>> bankIds)
	{
		this.quantities = quantities;
		this.bankIds = bankIds;
	}

	/**
	 * Whether an owned item name is the base name, or the base name followed by a dose, charge
	 * count or variant, e.g. "Prayer potion(3)", "Slayer ring (8)", "Fungicide spray 10".
	 */
	static boolean matches(String ownedName, String base)
	{
		return ownedName.equals(base) || ownedName.startsWith(base + "(") || ownedName.startsWith(base + " ");
	}

	/**
	 * Total quantity of items matching the base name. Food is matched exactly.
	 */
	int count(String base, boolean exact)
	{
		if (exact)
		{
			return quantities.getOrDefault(base, 0);
		}
		int total = 0;
		for (Map.Entry<String, Integer> e : quantities.entrySet())
		{
			if (matches(e.getKey(), base))
			{
				total += e.getValue();
			}
		}
		return total;
	}

	boolean owns(String base)
	{
		return count(base, false) > 0;
	}

	/**
	 * Bank item ids whose names match the base name, for highlighting.
	 */
	void collectBankIds(String base, boolean exact, Set<Integer> out)
	{
		for (Map.Entry<String, Set<Integer>> e : bankIds.entrySet())
		{
			if (exact ? e.getKey().equals(base) : matches(e.getKey(), base))
			{
				out.addAll(e.getValue());
			}
		}
	}

	public static OwnedItems of(Map<String, Integer> quantities, Map<String, Set<Integer>> bankIds)
	{
		return new OwnedItems(new HashMap<>(quantities), new HashMap<>(bankIds));
	}
}
