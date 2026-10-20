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
import lombok.Builder;
import lombok.Value;

/**
 * Builds the preparation suggestions for a task: gear the task requires, an inventory, and
 * teleports. It only suggests; it never withdraws or equips anything, and never suggests prayers.
 */
public final class PrepPlanner
{
	public static final int INVENTORY_SLOTS = 28;
	private static final int ANTIFIRE_DOSES = 2;
	/** A moonlight moth mix has two doses to a prayer potion's four. */
	private static final int PRAYER_MIXES_PER_POTION = 2;
	/** Karambwans to bring for combo eating. */
	private static final int COMBO_FOOD = 4;

	/** For "common drops only": a loot type counts when it drops at least once per this many kills. */
	public static final int COMMON_DROP_KILLS = 64;

	/** Loot types in the order they are suggested, with how they are described. */
	private static final String[][] LOOT_TYPES = {
		{"alchables", "items worth alching"},
		{"herbs", "herbs"},
		{"seeds", "seeds"},
		{"gems", "gems"},
		{"bones", "bones"},
		{"ashes", "demonic ashes"},
	};

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

	/**
	 * Everything the inventory plan depends on.
	 */
	@Value
	@Builder
	public static class Request
	{
		String task;
		/** Where the Slayer plugin says the task is, or null. */
		String location;
		AttackType bestType;
		@Builder.Default
		List<PrepPlan.Requirement> requirements = Collections.emptyList();
		OwnedItems owned;
		int prayerPotions;
		int combatPotions;
		boolean fillFood;
		/** The monster being fought, for its drops. */
		String monsterName;
		/** Suggest tools for loot that drops at least once per this many kills; 0 is off. */
		int lootMaxKills;
		/** Kills left on the task, for items used up per kill; 0 if unknown. */
		int taskRemaining;
		int monsterHitpoints;
		/** The weapon in the best set, so it is not suggested again as a spec weapon. */
		String bestWeapon;
		boolean suggestSpecWeapon;
	}

	/**
	 * Shorthand used by tests that only care about the basics.
	 */
	public PrepPlan plan(String task, String location, AttackType bestType, List<PrepPlan.Requirement> requirements,
		OwnedItems owned, int prayerPotions, int combatPotions, boolean fillFood, String monsterName, int lootMaxKills)
	{
		return plan(Request.builder().task(task).location(location).bestType(bestType).requirements(requirements)
			.owned(owned).prayerPotions(prayerPotions).combatPotions(combatPotions).fillFood(fillFood)
			.monsterName(monsterName).lootMaxKills(lootMaxKills).build());
	}

	public PrepPlan plan(Request request)
	{
		String task = request.getTask();
		AttackType bestType = request.getBestType();
		List<PrepPlan.Requirement> requirements = request.getRequirements();
		OwnedItems owned = request.getOwned();
		int prayerPotions = request.getPrayerPotions();
		int combatPotions = request.getCombatPotions();
		boolean fillFood = request.isFillFood();
		String monsterName = request.getMonsterName();
		int lootMaxKills = request.getLootMaxKills();
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
				int have = name != null ? owned.count(name, false) : 0;
				int need = item.perKill && request.getTaskRemaining() > 0 ? request.getTaskRemaining() : 1;
				String note = item.note + (item.perKill ? " (one per kill)" : "");
				lines.add(new PrepPlan.InventoryLine(name != null ? name : item.names.get(0), need, have, note, true,
					name != null ? owned.anyId(name, false) : -1, item.stackable));
				collect(name, false, owned, ids);
				slots += item.stackable ? 1 : need;
				if (item.perKill && have > 0 && have < need)
				{
					notes.add("You need " + need + " " + name + " for " + need + " kills but have " + have + ".");
				}
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

		if (prayerPotions > 0)
		{
			slots += addPrayer(lines, ids, owned, prayerPotions, notes);
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
		else if (bestType != null && combatPotions > 0)
		{
			notes.add("No " + bestType.getDisplayName().toLowerCase(Locale.ROOT) + " boosting potion found.");
		}
		if (bestType == AttackType.MAGIC)
		{
			String heart = firstOwned(s.magicBoostItems, owned);
			if (heart != null)
			{
				slots += addLine(lines, ids, owned, heart, 1, "boosts Magic", false);
			}
		}

		if (request.isSuggestSpecWeapon() && request.getMonsterHitpoints() >= s.specMinHitpoints && s.specWeapons != null)
		{
			for (PrepData.SpecWeapon spec : s.specWeapons)
			{
				if (owned.owns(spec.name) && !spec.name.equals(request.getBestWeapon()))
				{
					slots += addLine(lines, ids, owned, spec.name, 1, "special attack: " + spec.note, false);
					break;
				}
			}
		}

		if (lootMaxKills > 0)
		{
			slots += addLootTools(lines, ids, owned, monsterName, lootMaxKills);
		}

		List<String> tips = new ArrayList<>();
		if (info != null && info.cannon)
		{
			tips.add("This task is often cannoned; check your spot allows a cannon.");
			String balls = firstOwned(s.cannonballs, owned);
			boolean fullCannon = s.cannonParts != null && s.cannonParts.stream().allMatch(owned::owns);
			if (fullCannon && balls != null)
			{
				for (String part : s.cannonParts)
				{
					slots += addLine(lines, ids, owned, part, 1, "dwarf cannon", false);
				}
				slots += addStack(lines, ids, owned, balls, "for the cannon");
			}
		}
		if (info != null && info.stacks)
		{
			tips.add("Monsters stack here, so bursting or barraging (Ancient Magicks) works well.");
			String pouch = firstOwned(s.lootTools == null ? null : s.lootTools.get("alchables"), owned);
			if (pouch != null && !listed(lines, pouch))
			{
				slots += addLine(lines, ids, owned, pouch, 1, "with burst or barrage runes", false);
			}
		}

		List<String> locationKeys = locationKeys(info, request.getLocation());
		slots += addTeleports(lines, ids, owned, locationKeys, locationKnown(request.getLocation(), locationKeys), tips);

		if (fillFood)
		{
			boolean comboEat = info != null && info.comboEat || request.getMonsterHitpoints() >= s.specMinHitpoints;
			slots += addFood(lines, ids, owned, INVENTORY_SLOTS - slots, comboEat, notes);
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

		return new PrepPlan(requirements, lines, teleports(locationKeys, owned), notes, tips, slots,
			Collections.unmodifiableSet(ids));
	}

	/**
	 * Items that collect or make use of what the monster drops, for the ones the player owns.
	 */
	private int addLootTools(List<PrepPlan.InventoryLine> lines, Set<Integer> ids, OwnedItems owned,
		String monsterName, int lootMaxKills)
	{
		Map<String, Integer> drops = data.drops(monsterName);
		PrepData.Supplies s = data.supplies();
		int slots = 0;
		for (String[] type : LOOT_TYPES)
		{
			Integer kills = drops.get(type[0]);
			if (kills == null || kills > lootMaxKills || s.lootTools == null)
			{
				continue;
			}
			String often = kills <= 1 ? "every kill" : "about 1 in " + kills + " kills";
			String tool = firstOwned(s.lootTools.get(type[0]), owned);
			if (tool != null)
			{
				String note = monsterName + " drops " + type[1] + " (" + often + ")"
					+ ("alchables".equals(type[0]) ? "; fill it with nature and fire runes for High Alchemy" : "");
				slots += addLine(lines, ids, owned, tool, 1, note, false);
			}
			else if ("alchables".equals(type[0]))
			{
				// no rune pouch: nature runes still take just one slot
				String runes = firstOwned(s.alchRunes, owned);
				if (runes != null)
				{
					slots += addStack(lines, ids, owned, runes, monsterName + " drops " + type[1] + " (" + often
						+ "); bring a fire staff for High Alchemy");
				}
			}
		}
		return slots;
	}

	/**
	 * Prayer potions first, then moonlight moth mixes for any shortfall, two mixes per potion.
	 */
	private int addPrayer(List<PrepPlan.InventoryLine> lines, Set<Integer> ids, OwnedItems owned, int wanted,
		List<String> notes)
	{
		PrepData.Supplies s = data.supplies();
		int slots = 0;
		int missing = wanted;
		if (s.prayerPotions != null)
		{
			for (String name : s.prayerPotions)
			{
				if (missing > 0 && owned.owns(name))
				{
					int take = addLine(lines, ids, owned, name, missing, "restores prayer points", false);
					slots += take;
					missing -= take;
				}
			}
		}
		if (s.prayerMixes != null)
		{
			for (String name : s.prayerMixes)
			{
				if (missing > 0 && owned.owns(name))
				{
					int take = addLine(lines, ids, owned, name, missing * PRAYER_MIXES_PER_POTION,
						"restores prayer points; two stand in for one prayer potion", false);
					slots += take;
					missing -= (take + PRAYER_MIXES_PER_POTION - 1) / PRAYER_MIXES_PER_POTION;
				}
			}
		}
		if (missing == wanted)
		{
			notes.add("No prayer potions, super restores or moonlight moth mixes found.");
		}
		return slots;
	}

	/**
	 * One owned teleport item per location (just one in all when the location isn't known), so
	 * the plan doesn't carry several ways to the same task. Worn teleports become a tip instead.
	 */
	private int addTeleports(List<PrepPlan.InventoryLine> lines, Set<Integer> ids, OwnedItems owned,
		List<String> keys, boolean locationKnown, List<String> tips)
	{
		PrepData.Supplies s = data.supplies();
		int slots = 0;
		for (String key : keys)
		{
			List<PrepData.Teleport> list = data.locations().get(key);
			if (list == null)
			{
				continue;
			}
			boolean added = false;
			for (PrepData.Teleport t : list)
			{
				String name = firstOwned(t.items, owned);
				if (name == null)
				{
					continue;
				}
				added = true;
				if (matchesAny(name, s.wornTeleports))
				{
					tips.add("Wear your " + name + " to teleport to " + key + ".");
				}
				else if (!listed(lines, name))
				{
					String note = "teleport to " + key;
					slots += matchesAny(name, s.stackableTeleports)
						? addStack(lines, ids, owned, name, note)
						: addLine(lines, ids, owned, name, 1, note, false);
				}
				break;
			}
			if (added && !locationKnown)
			{
				break;
			}
		}
		return slots;
	}

	/**
	 * Karambwans to combo eat when the task hits hard, then the best food for the rest.
	 */
	private int addFood(List<PrepPlan.InventoryLine> lines, Set<Integer> ids, OwnedItems owned, int remaining,
		boolean comboEat, List<String> notes)
	{
		PrepData.Supplies s = data.supplies();
		int slots = 0;
		String combo = null;
		int comboTaken = 0;
		int comboIndex = -1;
		if (comboEat && s.comboFood != null)
		{
			for (String name : s.comboFood)
			{
				int have = owned.count(name, true);
				if (have > 0 && remaining > 0)
				{
					combo = name;
					comboTaken = Math.min(Math.min(COMBO_FOOD, have), remaining);
					comboIndex = lines.size();
					lines.add(new PrepPlan.InventoryLine(name, comboTaken, have,
						"eat in the same tick as your main food (combo eating)", false, owned.anyId(name, true), false));
					owned.collectBankIds(name, true, ids);
					remaining -= comboTaken;
					slots += comboTaken;
					break;
				}
			}
		}
		boolean anyFood = combo != null;
		for (PrepData.Food food : s.food)
		{
			if (remaining <= 0)
			{
				break;
			}
			int have = owned.count(food.name, true);
			int available = food.name.equals(combo) ? have - comboTaken : have;
			if (available <= 0)
			{
				continue;
			}
			anyFood = true;
			int take = Math.min(remaining, available);
			if (food.name.equals(combo))
			{
				// the combo food is also the best food left: keep it on one line
				PrepPlan.InventoryLine line = lines.get(comboIndex);
				lines.set(comboIndex, new PrepPlan.InventoryLine(line.getName(), line.getQuantity() + take, have,
					line.getNote(), false, line.getItemId(), false));
			}
			else
			{
				lines.add(new PrepPlan.InventoryLine(food.name, take, have, "heals " + food.heal, false,
					owned.anyId(food.name, true), false));
				owned.collectBankIds(food.name, true, ids);
			}
			remaining -= take;
			slots += take;
		}
		if (!anyFood)
		{
			notes.add("No food found in your bank.");
		}
		return slots;
	}

	private List<String> locationKeys(PrepData.TaskInfo info, String location)
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
		return keys;
	}

	private static boolean locationKnown(String location, List<String> keys)
	{
		return location != null && !location.trim().isEmpty() && !keys.isEmpty();
	}

	private List<PrepPlan.TeleportOption> teleports(List<String> keys, OwnedItems owned)
	{
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

	private static boolean listed(List<PrepPlan.InventoryLine> lines, String name)
	{
		return lines.stream().anyMatch(l -> l.getName().equals(name));
	}

	private static boolean matchesAny(String name, List<String> bases)
	{
		if (bases == null)
		{
			return false;
		}
		for (String base : bases)
		{
			if (OwnedItems.matches(name, base))
			{
				return true;
			}
		}
		return false;
	}

	private static int addLine(List<PrepPlan.InventoryLine> lines, Set<Integer> ids, OwnedItems owned, String name,
		int quantity, String note, boolean required)
	{
		int have = owned.count(name, false);
		int take = Math.max(1, Math.min(quantity, have));
		lines.add(new PrepPlan.InventoryLine(name, take, have, note, required, owned.anyId(name, false), false));
		collect(name, false, owned, ids);
		return take;
	}

	/**
	 * A stackable item (runes, cannonballs): the whole stack in one slot.
	 */
	private static int addStack(List<PrepPlan.InventoryLine> lines, Set<Integer> ids, OwnedItems owned, String name,
		String note)
	{
		int have = owned.count(name, false);
		lines.add(new PrepPlan.InventoryLine(name, Math.max(1, have), have, note, false, owned.anyId(name, false), true));
		collect(name, false, owned, ids);
		return 1;
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
