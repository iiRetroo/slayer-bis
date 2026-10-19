package com.slayerbis.prep;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
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
		List<String> locations;
	}

	static final class InventoryItem
	{
		List<String> names;
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
		List<String> superAntifire;
		List<String> antifire;
		List<String> antivenom;
		List<String> dragonfireShields;
		List<String> wyvernShields;
	}

	private static final class TasksFile
	{
		Map<String, TaskInfo> tasks;
		Map<String, List<Teleport>> locations;
	}

	private final Map<String, TaskInfo> tasks;
	private final Map<String, List<Teleport>> locations;
	private final Supplies supplies;

	private PrepData(Map<String, TaskInfo> tasks, Map<String, List<Teleport>> locations, Supplies supplies)
	{
		this.tasks = tasks;
		this.locations = locations;
		this.supplies = supplies;
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
		return new PrepData(
			tasksFile.tasks == null ? Collections.emptyMap() : tasksFile.tasks,
			tasksFile.locations == null ? Collections.emptyMap() : tasksFile.locations,
			supplies);
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

	Supplies supplies()
	{
		return supplies;
	}

	/**
	 * Every item name the data refers to, for checking the data against the item list.
	 */
	public Map<String, TaskInfo> allTasks()
	{
		return Collections.unmodifiableMap(tasks);
	}
}
