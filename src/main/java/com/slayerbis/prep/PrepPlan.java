package com.slayerbis.prep;

import com.slayerbis.data.EquipmentSlot;
import java.util.List;
import java.util.Set;
import lombok.Value;

/**
 * What to wear, carry and use to get to a task. Suggestions only: nothing is withdrawn or equipped.
 */
@Value
public class PrepPlan
{
	/**
	 * A slot that must hold one of the listed items for this task.
	 */
	@Value
	public static class Requirement
	{
		EquipmentSlot slot;
		List<String> accepted;
		String reason;
		/** Whether the player owns any accepted item. */
		boolean owned;
	}

	/**
	 * One line of the suggested inventory.
	 */
	@Value
	public static class InventoryLine
	{
		/** Item name, or the first preferred name when none is owned. */
		String name;
		int quantity;
		/** How many of this item the player owns across bank, inventory and worn. */
		int owned;
		String note;
		boolean required;
		/** An owned item id to show the icon of, or -1 when none is owned. */
		int itemId;
	}

	@Value
	public static class TeleportOption
	{
		String location;
		String text;
		/** null when the teleport needs no item (spell, fairy ring). */
		Boolean owned;
	}

	List<Requirement> requirements;
	List<InventoryLine> inventory;
	List<TeleportOption> teleports;
	/** Warnings: missing items and supplies. */
	List<String> notes;
	/** Tips about the task, e.g. that it is often cannoned. */
	List<String> tips;
	int slotsUsed;
	/** Bank item ids of the suggested supplies, for highlighting. */
	Set<Integer> supplyBankIds;
}
