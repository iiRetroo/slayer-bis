package com.slayerbis.prep;

import com.slayerbis.data.AttackType;
import com.slayerbis.data.EquipmentSlot;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds the preparation suggestions for a task: gear the task requires, an inventory, and
 * teleports. It only suggests; it never withdraws or equips anything, and never suggests prayers.
 */
public final class PrepPlanner
{
	public static final int INVENTORY_SLOTS = 28;
	private static final int ANTIFIRE_DOSES = 2;

	private final PrepData data;

	public PrepPlanner(PrepData data)
	{
		this.data = data;
	}

	/**
	 * Worn items the task needs, known before the gear search so the search can respect them.
	 *
	 * @param ownedGearNames canonical names of all owned equipment (these may differ from the raw
	 *                       names, e.g. "Black slayer helmet (i)" counts as "Slayer helmet (i)")
	 */
	public List<PrepPlan.Requirement> requirements(String task, OwnedItems owned, Collection<String> ownedGearNames)
	{
		PrepData.TaskInfo info = data.task(task);
		if (info == null)
		{
			return Collections.emptyList();
		}
		List<PrepPlan.Requirement> out = new ArrayList<>();
		if (info.required != null)
		{
			for (Map.Entry<String, List<String>> e : info.required.entrySet())
			{
				EquipmentSlot slot = EquipmentSlot.fromKey(e.getKey());
				if (slot != null)
				{
					out.add(new PrepPlan.Requirement(slot, e.getValue(), "needed for " + task,
						ownsAny(e.getValue(), owned, ownedGearNames)));
				}
			}
		}
		PrepData.Supplies s = data.supplies();
		if ("wyvern".equals(info.dragonfire))
		{
			out.add(new PrepPlan.Requirement(EquipmentSlot.SHIELD, s.wyvernShields,
				"protects against wyvern breath", ownsAny(s.wyvernShields, owned, ownedGearNames)));
		}
		else if ("dragon".equals(info.dragonfire) && firstOwned(s.superAntifire, owned) == null)
		{
			out.add(new PrepPlan.Requirement(EquipmentSlot.SHIELD, s.dragonfireShields,
				"protects against dragonfire (not needed with a super antifire)",
				ownsAny(s.dragonfireShields, owned, ownedGearNames)));
		}
		return out;
	}

	public PrepPlan plan(String task, String location, AttackType bestType, List<PrepPlan.Requirement> requirements,
		OwnedItems owned, int prayerPotions, int combatPotions, boolean fillFood)
	{
		PrepData.TaskInfo info = data.task(task);
		PrepData.Supplies s = data.supplies();
		List<PrepPlan.InventoryLine> lines = new ArrayList<>();
		List<String> notes = new ArrayList<>();
		Set<Integer> ids = new HashSet<>();
		int slots = 0;

		if (info != null && info.inventory != null)
		{
			for (PrepData.InventoryItem item : info.inventory)
			{
				String name = firstOwned(item.names, owned);
				lines.add(new PrepPlan.InventoryLine(name != null ? name : item.names.get(0), 1,
					name != null ? owned.count(name, false) : 0, item.note, true,
					name != null ? owned.anyId(name, false) : -1));
				collect(name, false, owned, ids);
				slots++;
			}
		}

		if (info != null && "dragon".equals(info.dragonfire))
		{
			String superAntifire = firstOwned(s.superAntifire, owned);
			String antifire = firstOwned(s.antifire, owned);
			if (superAntifire != null)
			{
				slots += addLine(lines, ids, owned, superAntifire, ANTIFIRE_DOSES, "protects against dragonfire", false);
			}
			else if (antifire != null)
			{
				slots += addLine(lines, ids, owned, antifire, ANTIFIRE_DOSES, "use with a dragonfire-protecting shield", false);
			}
			else
			{
				notes.add("No antifire potions found. A dragonfire-protecting shield alone does not block all dragonfire.");
			}
		}

		if (info != null && info.venom)
		{
			String anti = firstOwned(s.antivenom, owned);
			if (anti != null)
			{
				slots += addLine(lines, ids, owned, anti, 1, "this task can poison or envenom you", false);
			}
			else
			{
				notes.add("No anti-venom or antidote found; this task can poison or envenom you.");
			}
		}

		List<String> stylePotions = bestType == null ? Collections.emptyList()
			: bestType == AttackType.RANGED ? s.rangedPotions
			: bestType == AttackType.MAGIC ? s.magicPotions
			: s.meleePotions;
		String potion = firstOwned(stylePotions, owned);
		if (potion != null && combatPotions > 0)
		{
			slots += addLine(lines, ids, owned, potion, combatPotions, "boosts your " + bestType.getDisplayName().toLowerCase(Locale.ROOT), false);
		}
		if (bestType == AttackType.MAGIC)
		{
			String heart = firstOwned(s.magicBoostItems, owned);
			if (heart != null)
			{
				slots += addLine(lines, ids, owned, heart, 1, "boosts Magic", false);
			}
		}

		String prayer = firstOwned(s.prayerPotions, owned);
		if (prayer != null && prayerPotions > 0)
		{
			slots += addLine(lines, ids, owned, prayer, prayerPotions, "restores prayer points", false);
		}

		if (fillFood)
		{
			int remaining = INVENTORY_SLOTS - slots;
			boolean anyFood = false;
			for (PrepData.Food food : s.food)
			{
				if (remaining <= 0)
				{
					break;
				}
				int have = owned.count(food.name, true);
				if (have <= 0)
				{
					continue;
				}
				anyFood = true;
				int take = Math.min(remaining, have);
				lines.add(new PrepPlan.InventoryLine(food.name, take, have, "heals " + food.heal, false,
					owned.anyId(food.name, true)));
				owned.collectBankIds(food.name, true, ids);
				remaining -= take;
				slots += take;
			}
			if (!anyFood)
			{
				notes.add("No food found in your bank.");
			}
		}

		for (PrepPlan.Requirement r : requirements)
		{
			if (!r.isOwned())
			{
				notes.add(0, "Missing " + r.getSlot().getDisplayName().toLowerCase(Locale.ROOT) + " item: "
					+ String.join(" / ", r.getAccepted()) + " (" + r.getReason() + ").");
			}
		}
		for (PrepPlan.InventoryLine line : lines)
		{
			if (line.isRequired() && line.getOwned() == 0)
			{
				notes.add(0, "Missing " + line.getName() + " (" + line.getNote() + ").");
			}
		}

		return new PrepPlan(requirements, lines, teleports(info, location, owned), notes, slots,
			Collections.unmodifiableSet(ids));
	}

	private List<PrepPlan.TeleportOption> teleports(PrepData.TaskInfo info, String location, OwnedItems owned)
	{
		List<String> keys = new ArrayList<>();
		if (location != null && !location.trim().isEmpty())
		{
			String loc = location.toLowerCase(Locale.ROOT);
			for (String key : data.locations().keySet())
			{
				String k = key.toLowerCase(Locale.ROOT);
				if (loc.contains(k) || k.contains(loc))
				{
					keys.add(key);
				}
			}
		}
		if (keys.isEmpty() && info != null && info.locations != null)
		{
			keys.addAll(info.locations);
		}
		List<PrepPlan.TeleportOption> out = new ArrayList<>();
		for (String key : keys)
		{
			List<PrepData.Teleport> list = data.locations().get(key);
			if (list == null)
			{
				continue;
			}
			for (PrepData.Teleport t : list)
			{
				Boolean has = t.items == null || t.items.isEmpty() ? null : firstOwned(t.items, owned) != null;
				out.add(new PrepPlan.TeleportOption(key, t.text, has));
			}
		}
		return out;
	}

	private static int addLine(List<PrepPlan.InventoryLine> lines, Set<Integer> ids, OwnedItems owned, String name,
		int quantity, String note, boolean required)
	{
		int have = owned.count(name, false);
		int take = Math.max(1, Math.min(quantity, have));
		lines.add(new PrepPlan.InventoryLine(name, take, have, note, required, owned.anyId(name, false)));
		collect(name, false, owned, ids);
		return take;
	}

	private static void collect(String name, boolean exact, OwnedItems owned, Set<Integer> ids)
	{
		if (name != null)
		{
			owned.collectBankIds(name, exact, ids);
		}
	}

	static String firstOwned(List<String> names, OwnedItems owned)
	{
		if (names == null)
		{
			return null;
		}
		for (String n : names)
		{
			if (owned.owns(n))
			{
				return n;
			}
		}
		return null;
	}

	private static boolean ownsAny(List<String> names, OwnedItems owned, Collection<String> ownedGearNames)
	{
		for (String n : names)
		{
			if (ownedGearNames.contains(n) || owned.owns(n))
			{
				return true;
			}
		}
		return false;
	}
}
