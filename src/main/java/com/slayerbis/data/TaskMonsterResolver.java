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
		.put("greater demons", ImmutableList.of("Greater demon", "Tormented Demon"))
		.put("black dragons", ImmutableList.of("Black dragon", "Baby black dragon", "Brutal black dragon"))
		.put("blue dragons", ImmutableList.of("Blue dragon", "Baby blue dragon", "Brutal blue dragon"))
		.put("green dragons", ImmutableList.of("Green dragon", "Baby green dragon", "Brutal green dragon"))
		.put("red dragons", ImmutableList.of("Red dragon", "Baby red dragon", "Brutal red dragon"))
		.build();

	/**
	 * Bosses that count for a task, offered in the picker after the task's own monsters but
	 * never chosen automatically.
	 */
	private static final Map<String, List<String>> TASK_BOSSES = ImmutableMap.<String, List<String>>builder()
		.put("hellhounds", ImmutableList.of("Cerberus"))
		.put("abyssal demons", ImmutableList.of("Abyssal Sire"))
		.put("smoke devils", ImmutableList.of("Thermonuclear smoke devil"))
		.put("cave kraken", ImmutableList.of("Kraken"))
		.put("hydras", ImmutableList.of("Alchemical Hydra"))
		.put("gargoyles", ImmutableList.of("Dusk", "Dawn"))
		.put("kalphites", ImmutableList.of("Kalphite Queen"))
		.put("black dragons", ImmutableList.of("King Black Dragon"))
		.put("dagannoth", ImmutableList.of("Dagannoth Rex", "Dagannoth Prime", "Dagannoth Supreme"))
		.put("greater demons", ImmutableList.of("K'ril Tsutsaroth", "Skotizo"))
		.put("blue dragons", ImmutableList.of("Vorkath"))
		.put("zombies", ImmutableList.of("Vorkath"))
		.put("rats", ImmutableList.of("Scurrius"))
		.put("bears", ImmutableList.of("Callisto", "Artio"))
		.put("spiders", ImmutableList.of("Sarachnis", "Venenatis", "Spindel"))
		.put("scorpions", ImmutableList.of("Scorpia"))
		.put("skeletons", ImmutableList.of("Vet'ion", "Calvar'ion"))
		.put("araxytes", ImmutableList.of("Araxxor"))
		.put("aviansies", ImmutableList.of("Kree'arra"))
		.put("gryphons", ImmutableList.of("Shellbane gryphon"))
		.build();

	/** A boss's own Slayer task name (for its preparation data), by lower-case monster name. */
	private static final Map<String, String> BOSS_TASKS = ImmutableMap.<String, String>builder()
		.put("cerberus", "cerberus")
		.put("abyssal sire", "the abyssal sire")
		.put("thermonuclear smoke devil", "the thermonuclear smoke devil")
		.put("kraken", "the cave kraken boss")
		.put("alchemical hydra", "the alchemical hydra")
		.put("dusk", "the grotesque guardians")
		.put("dawn", "the grotesque guardians")
		.put("kalphite queen", "the kalphite queen")
		.put("king black dragon", "the king black dragon")
		.put("dagannoth rex", "dagannoth kings")
		.put("dagannoth prime", "dagannoth kings")
		.put("dagannoth supreme", "dagannoth kings")
		.put("k'ril tsutsaroth", "k'ril tsutsaroth")
		.put("callisto", "callisto")
		.put("artio", "callisto")
		.put("sarachnis", "sarachnis")
		.put("venenatis", "venenatis")
		.put("spindel", "venenatis")
		.put("scorpia", "scorpia")
		.put("vet'ion", "vet'ion")
		.put("calvar'ion", "vet'ion")
		.put("araxxor", "araxxor")
		.put("kree'arra", "kree'arra")
		.put("vorkath", "vorkath")
		.put("shellbane gryphon", "the shellbane gryphon")
		.build();

	private static final int MAX_CANDIDATES = 40;

	/**
	 * Whether this monster is a boss offered on a regular task, for labelling it in the picker.
	 */
	public static boolean isTaskBoss(String monsterName)
	{
		if (monsterName == null)
		{
			return false;
		}
		String lower = monsterName.toLowerCase(Locale.ROOT);
		for (List<String> bosses : TASK_BOSSES.values())
		{
			for (String b : bosses)
			{
				if (b.toLowerCase(Locale.ROOT).equals(lower))
				{
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * The boss's own task name, for its preparation data, or null if this isn't such a boss.
	 */
	public static String bossTask(String monsterName)
	{
		return monsterName == null ? null : BOSS_TASKS.get(monsterName.toLowerCase(Locale.ROOT));
	}

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
		List<String> bosses = TASK_BOSSES.getOrDefault(task.trim().toLowerCase(Locale.ROOT), ImmutableList.of());
		addNamed(out, names, MAX_CANDIDATES - bosses.size());
		Set<String> bossNames = new LinkedHashSet<>();
		for (String b : bosses)
		{
			bossNames.add(b.toLowerCase(Locale.ROOT));
		}
		addNamed(out, bossNames, MAX_CANDIDATES);
		return out;
	}

	private void addNamed(List<Monster> out, Set<String> names, int limit)
	{
		for (String name : names)
		{
			for (Monster m : data.getMonsters())
			{
				if (out.size() >= limit)
				{
					return;
				}
				if (m.getName().toLowerCase(Locale.ROOT).equals(name) && !out.contains(m))
				{
					out.add(m);
				}
			}
		}
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
