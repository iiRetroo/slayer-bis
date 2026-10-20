package com.slayerbis.prep;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
		List<String> locations;
	}

	static final class InventoryItem
	{
		List<String> names;
		String note;
		/** Used up on every kill, so one is needed per kill left. */
		boolean perKill;
		/** Fits in one inventory slot however many there are. */
		boolean stackable;
	}

	static final class SpecWeapon
	{
		String name;
		String note;
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
		List<String> meleePotions;
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
		List<String> dragonfireShields;
		List<String> wyvernShields;
		/** Loot type (herbs, seeds, gems, bones, ashes, alchables) to the items that collect it. */
		Map<String, List<String>> lootTools;
		List<String> alchRunes;
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
	}

	private final Map<String, TaskInfo> tasks;
	private final Map<String, List<Teleport>> locations;
	private final Map<String, List<Teleport>> masters;
	private final Supplies supplies;
	/** Lower-case monster name to loot type to average kills per item of that type. */
	private final Map<String, Map<String, Integer>> drops;

	private PrepData(Map<String, TaskInfo> tasks, Map<String, List<Teleport>> locations,
		Map<String, List<Teleport>> masters, Supplies supplies, Map<String, Map<String, Integer>> drops)
	{
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
