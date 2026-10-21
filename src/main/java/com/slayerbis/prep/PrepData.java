package com.slayerbis.prep;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Task preparation data from tasks.json and supplies.json. Both files are hand-maintained;
 * see the comment at the top of each for the format.
 */
public final class PrepData
{
	static final class TaskInfo
	{
		Map<String, List<String>> required;
		List<InventoryItem> inventory;
		String dragonfire;
		boolean venom;
		/** Commonly done with a dwarf cannon. */
		boolean cannon;
		/** Monsters bunch up, so multi-target spells work well. */
		boolean stacks;
		/** Monsters hit hard enough that combo eating helps. */
		boolean comboEat;
		/** The least the worn gear must weigh, in kg; 0 for no limit. */
		double minWeight;
		List<String> tips;
		List<String> locations;
		List<Safespot> safespots;
		/** A boss's monsters or phases, "Name (version)" or "Name". */
		List<String> monsters;
		/** Every listed monster is fought each trip, so the plan carries switches for the others. */
		boolean wholeFight;
		/** Worn items the wiki recommends for reasons the DPS maths can't see. */
		List<Preference> prefer;
	}

	/**
	 * A worn item the wiki recommends for a boss; kept when the best set without it is no more than
	 * withinPercent better.
	 */
	static final class Preference
	{
		String slot;
		List<String> items;
		int withinPercent;
		String reason;
	}

	static final class InventoryItem
	{
		List<String> names;
		String note;
		/** Used up on every kill, so one is needed per kill left. */
		boolean perKill;
		/** Fits in one inventory slot however many there are. */
		boolean stackable;
		/** With perKill: one item covers this many kills (0 means one). */
		int killsEach;
		/** With perKill: never suggest more than this many (0 means no limit). */
		int maxCount;
		/** Useful but not needed, so not flagged when missing. */
		boolean optional;
		/** Match names exactly ("Fungicide" must not match "Fungicide spray"). */
		boolean exact;
		/**
		 * A swap the wiki recommends for part of a boss fight, carried only when owned and when the
		 * Wiki switches setting is on. The note says what it's for, never when to use it.
		 */
		@SerializedName("switch")
		boolean switchItem;
		/** Only for this version of the boss (matched in its version, e.g. "Awakened"), or null for all. */
		String onlyFor;
	}

	/** What a location needs whatever the task. */
	static final class LocationNeeds
	{
		Map<String, List<String>> required;
		List<InventoryItem> inventory;
		List<String> tips;
		/** Shown as a tip when the task may or may not be done here. */
		String summary;
	}

	static final class SpecWeapon
	{
		String name;
		String note;
	}

	static final class Safespot
	{
		/** Where it is, or null when it applies anywhere. */
		String location;
		String text;
	}

	static final class Teleport
	{
		String text;
		List<String> items;
	}

	static final class Food
	{
		String name;
		int heal;
	}

	static final class Supplies
	{
		List<Food> food;
		/** Super combat potions, best first. */
		List<String> meleePotions;
		/** Super attack and strength potions, used together when there's no super combat potion. */
		List<String> attackPotions;
		List<String> strengthPotions;
		/** Weaker fallbacks: a combat potion, then attack and strength potions. */
		List<String> weakMeleePotions;
		List<String> weakAttackPotions;
		List<String> weakStrengthPotions;
		List<String> rangedPotions;
		List<String> magicPotions;
		List<String> magicBoostItems;
		List<String> prayerPotions;
		/** Cheaper prayer restores; two of these stand in for one prayer potion. */
		List<String> prayerMixes;
		/** Food eaten in the same tick as other food (combo eating). */
		List<String> comboFood;
		/** Teleport items worn rather than carried, so they need no inventory slot. */
		List<String> wornTeleports;
		/** Teleport items that stack in one slot. */
		List<String> stackableTeleports;
		/** Exact names of the uncharged versions of teleport jewellery, which can't teleport. */
		List<String> unchargedTeleports;
		List<String> superAntifire;
		List<String> antifire;
		List<String> antivenom;
		/** Of the antivenom list, the ones that cure poison but don't protect against venom. */
		List<String> poisonOnly;
		List<String> dragonfireShields;
		List<String> wyvernShields;
		/** Loot type (herbs, seeds, gems, bones, ashes, alchables) to the items that collect it. */
		Map<String, List<String>> lootTools;
		List<String> alchRunes;
		/** Cheap throwing weapons to tag monsters and gather them into a stack before bursting, cheapest first. */
		List<String> lureWeapons;
		List<String> cannonParts;
		List<String> cannonballs;
		/** Defence-reducing special attack weapons, best first. */
		List<SpecWeapon> specWeapons;
		/** A spec weapon is suggested for monsters with at least this much HP. */
		int specMinHitpoints;
	}

	private static final class TasksFile
	{
		Map<String, TaskInfo> tasks;
		Map<String, List<Teleport>> locations;
		Map<String, List<Teleport>> masters;
		Map<String, LocationNeeds> locationNeeds;
	}

	private final Map<String, TaskInfo> tasks;
	private final Map<String, List<Teleport>> locations;
	private final Map<String, List<Teleport>> masters;
	private final Map<String, LocationNeeds> locationNeeds;
	private final Supplies supplies;
	/** Lower-case monster name to loot type to average kills per item of that type. */
	private final Map<String, Map<String, Integer>> drops;

	private PrepData(Map<String, TaskInfo> tasks, Map<String, List<Teleport>> locations,
		Map<String, List<Teleport>> masters, Map<String, LocationNeeds> locationNeeds, Supplies supplies,
		Map<String, Map<String, Integer>> drops)
	{
		this.locationNeeds = locationNeeds;
		this.tasks = tasks;
		this.locations = locations;
		this.masters = masters;
		this.supplies = supplies;
		this.drops = drops;
	}

	public static PrepData load(Gson gson) throws IOException
	{
		TasksFile tasksFile;
		try (Reader r = open("tasks.json"))
		{
			tasksFile = gson.fromJson(r, TasksFile.class);
		}
		Supplies supplies;
		try (Reader r = open("supplies.json"))
		{
			supplies = gson.fromJson(r, Supplies.class);
		}
		Map<String, Map<String, Integer>> drops = new HashMap<>();
		try (Reader r = open("drops.json"))
		{
			JsonObject root = gson.fromJson(r, JsonObject.class);
			for (Map.Entry<String, JsonElement> monster : root.entrySet())
			{
				Map<String, Integer> kinds = new HashMap<>();
				for (Map.Entry<String, JsonElement> kind : monster.getValue().getAsJsonObject().entrySet())
				{
					kinds.put(kind.getKey(), kind.getValue().getAsInt());
				}
				drops.put(monster.getKey().toLowerCase(Locale.ROOT), kinds);
			}
		}
		return new PrepData(
			tasksFile.tasks == null ? Collections.emptyMap() : tasksFile.tasks,
			tasksFile.locations == null ? Collections.emptyMap() : tasksFile.locations,
			tasksFile.masters == null ? Collections.emptyMap() : tasksFile.masters,
			tasksFile.locationNeeds == null ? Collections.emptyMap() : tasksFile.locationNeeds,
			supplies, drops);
	}

	private static Reader open(String name) throws IOException
	{
		InputStream in = PrepData.class.getResourceAsStream("/com/slayerbis/" + name);
		if (in == null)
		{
			throw new IOException("Missing resource " + name);
		}
		return new InputStreamReader(in, StandardCharsets.UTF_8);
	}

	TaskInfo task(String taskName)
	{
		return taskName == null ? null : tasks.get(taskName.trim().toLowerCase(Locale.ROOT));
	}

	Map<String, List<Teleport>> locations()
	{
		return locations;
	}

	Map<String, LocationNeeds> locationNeeds()
	{
		return locationNeeds;
	}

	Map<String, List<Teleport>> masters()
	{
		return masters;
	}

	Supplies supplies()
	{
		return supplies;
	}

	/**
	 * Loot type to average kills per item of that type, for a monster; empty if unknown.
	 */
	Map<String, Integer> drops(String monsterName)
	{
		Map<String, Integer> d = monsterName == null ? null : drops.get(monsterName.toLowerCase(Locale.ROOT));
		return d == null ? Collections.emptyMap() : d;
	}

	/**
	 * Every item name the data refers to, for checking the data against the item list.
	 */
	public Map<String, TaskInfo> allTasks()
	{
		return Collections.unmodifiableMap(tasks);
	}
}
