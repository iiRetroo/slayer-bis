package com.slayerbis;

import java.util.Map;
import java.util.Set;
import lombok.Value;

/**
 * Which items are on the player (worn or in the inventory), so the panel can tick off suggested
 * gear and supplies while banking.
 */
@Value
class ReadyState
{
	/** Canonical ids of items worn or in the inventory. */
	Set<Integer> onPerson;
	/** Inventory item names and quantities. */
	Map<String, Integer> inventoryByName;
}
