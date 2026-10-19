package com.slayerbis.data;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Picks the monster whose stats represent a Slayer task. Spawned task targets reported by the
 * Slayer plugin are used when available; otherwise the task name is matched against monster names.
 */
public class TaskMonsterResolver
{
	/**
	 * Tasks whose name does not match a single monster name.
	 */
	private static final Map<String, String> TASK_OVERRIDES = ImmutableMap.<String, String>builder()
		.put("kalphites", "Kalphite Soldier")
		.put("metal dragons", "Steel dragon")
		.put("spiritual creatures", "Spiritual mage")
		.put("fossil island wyverns", "Spitting wyvern")
		.put("barrows brothers", "Dharok the Wretched")
		.put("lesser nagua", "Sulphur Nagua")
		.put("warped creatures", "Warped Terrorbird")
		.put("birds", "Terrorbird")
		.put("crabs", "Sand Crab")
		.put("scabarites", "Scarab mage")
		.put("tzhaar", "TzHaar-Ket")
		.put("shades", "Fiyr Shade")
		.put("dogs", "Jackal")
		.put("the cave kraken boss", "Kraken")
		.put("mutated zygomites", "Zygomite")
		.put("aviansies", "Aviansie")
		.build();

	/**
	 * Tasks that cover several different monsters; all of them are offered in the panel.
	 */
	private static final Map<String, List<String>> TASK_MONSTERS = ImmutableMap.<String, List<String>>builder()
		.put("metal dragons", ImmutableList.of("Bronze dragon", "Iron dragon", "Steel dragon", "Mithril dragon",
			"Adamant dragon", "Rune dragon"))
		.put("kalphites", ImmutableList.of("Kalphite Worker", "Kalphite Soldier", "Kalphite Guardian"))
		.put("spiritual creatures", ImmutableList.of("Spiritual ranger", "Spiritual warrior", "Spiritual mage"))
		.put("fossil island wyverns", ImmutableList.of("Spitting wyvern", "Taloned wyvern", "Long-tailed wyvern",
			"Ancient wyvern"))
		.put("lesser nagua", ImmutableList.of("Sulphur Nagua", "Frost Nagua"))
		.put("barrows brothers", ImmutableList.of("Ahrim the Blighted", "Dharok the Wretched", "Guthan the Infested",
			"Karil the Tainted", "Torag the Corrupted", "Verac the Defiled"))
		.put("warped creatures", ImmutableList.of("Warped Terrorbird", "Warped Tortoise"))
		.build();

	private static final int MAX_CANDIDATES = 40;

	private final GameData data;

	public TaskMonsterResolver(GameData data)
	{
		this.data = data;
	}

	public Monster fromNpcIds(Collection<Integer> npcIds)
	{
		for (int id : npcIds)
		{
			Monster m = data.getMonster(id);
			if (m != null)
			{
				return m;
			}
		}
		return null;
	}

	public Monster fromName(String name)
	{
		if (name == null || name.trim().isEmpty())
		{
			return null;
		}
		String lower = name.trim().toLowerCase(Locale.ROOT);
		String override = TASK_OVERRIDES.get(lower);
		if (override != null)
		{
			Monster m = exactMatch(override.toLowerCase(Locale.ROOT));
			if (m != null)
			{
				return m;
			}
		}

		List<String> candidates = new ArrayList<>(singulars(lower));
		for (String c : candidates)
		{
			Monster m = exactMatch(c);
			if (m != null)
			{
				return m;
			}
		}
		for (String c : candidates)
		{
			Monster m = prefixMatch(c);
			if (m != null)
			{
				return m;
			}
		}
		return null;
	}

	/**
	 * Every monster (and version) that could be the target of a task, for the panel's picker.
	 * The first entry is the one {@link #fromName} picks.
	 */
	public List<Monster> candidates(String task)
	{
		List<Monster> out = new ArrayList<>();
		Monster main = fromName(task);
		if (main == null)
		{
			return out;
		}
		out.add(main);
		Set<String> names = new LinkedHashSet<>();
		names.add(main.getName().toLowerCase(Locale.ROOT));
		List<String> multi = TASK_MONSTERS.get(task.trim().toLowerCase(Locale.ROOT));
		if (multi != null)
		{
			for (String n : multi)
			{
				names.add(n.toLowerCase(Locale.ROOT));
			}
		}
		for (String name : names)
		{
			for (Monster m : data.getMonsters())
			{
				if (out.size() >= MAX_CANDIDATES)
				{
					return out;
				}
				if (m.getName().toLowerCase(Locale.ROOT).equals(name) && !out.contains(m))
				{
					out.add(m);
				}
			}
		}
		return out;
	}

	private static Set<String> singulars(String task)
	{
		String t = task.startsWith("the ") ? task.substring(4) : task;
		Set<String> out = new LinkedHashSet<>();
		out.add(t);
		if (t.endsWith("ies"))
		{
			out.add(t.substring(0, t.length() - 3) + "y");
		}
		if (t.endsWith("ves"))
		{
			out.add(t.substring(0, t.length() - 3) + "f");
		}
		if (t.endsWith("es"))
		{
			out.add(t.substring(0, t.length() - 2));
		}
		if (t.endsWith("s"))
		{
			out.add(t.substring(0, t.length() - 1));
		}
		return out;
	}

	private Monster exactMatch(String lowerName)
	{
		Monster best = null;
		for (Monster m : data.getMonsters())
		{
			if (m.getName().toLowerCase(Locale.ROOT).equals(lowerName) && better(m, best))
			{
				best = m;
			}
		}
		return best;
	}

	private Monster prefixMatch(String lowerName)
	{
		Monster best = null;
		for (Monster m : data.getMonsters())
		{
			if (m.getName().toLowerCase(Locale.ROOT).startsWith(lowerName) && m.isSlayerMonster() && better(m, best))
			{
				best = m;
			}
		}
		return best;
	}

	/**
	 * Prefer slayer-flagged, standard versions of a monster over quest, minigame or boss variants.
	 */
	private static boolean better(Monster m, Monster current)
	{
		if (current == null)
		{
			return true;
		}
		return rank(m) > rank(current);
	}

	private static int rank(Monster m)
	{
		int r = 0;
		if (m.isSlayerMonster())
		{
			r += 2;
		}
		if (m.getVersion().isEmpty() || m.getVersion().equalsIgnoreCase("Standard"))
		{
			r += 1;
		}
		return r;
	}
}
