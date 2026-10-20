package com.slayerbis.data;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Equipment and monster stats bundled with the plugin. The data is the same data the
 * OSRS Wiki DPS calculator uses (https://github.com/weirdgloop/osrs-dps-calc), reduced
 * to the fields the calculation needs.
 */
public class GameData
{
	private final Map<Integer, GearItem> itemsById = new HashMap<>();
	private final Map<Integer, Integer> aliasToBase = new HashMap<>();
	private final Map<Integer, Monster> monstersById = new HashMap<>();
	private final List<Monster> monsters = new ArrayList<>();

	public static GameData load(Gson gson) throws IOException
	{
		GameData data = new GameData();
		data.loadEquipment(gson);
		data.loadMonsters(gson);
		return data;
	}

	private static Reader open(String name) throws IOException
	{
		InputStream in = GameData.class.getResourceAsStream("/com/slayerbis/" + name);
		if (in == null)
		{
			throw new IOException("Missing resource " + name);
		}
		return new InputStreamReader(in, StandardCharsets.UTF_8);
	}

	private void loadEquipment(Gson gson) throws IOException
	{
		Map<Integer, Integer> weights = new HashMap<>();
		try (Reader reader = open("weights.json"))
		{
			for (Map.Entry<String, JsonElement> w : gson.fromJson(reader, JsonObject.class).entrySet())
			{
				weights.put(Integer.parseInt(w.getKey()), w.getValue().getAsInt());
			}
		}
		try (Reader reader = open("equipment.json"))
		{
			JsonObject root = gson.fromJson(reader, JsonObject.class);
			for (JsonElement e : root.getAsJsonArray("items"))
			{
				JsonArray a = e.getAsJsonArray();
				EquipmentSlot slot = EquipmentSlot.fromKey(a.get(3).getAsString());
				if (slot == null)
				{
					continue;
				}
				GearItem item = new GearItem(
					a.get(0).getAsInt(), a.get(1).getAsString(), a.get(2).getAsString(), slot,
					a.get(4).getAsInt(), a.get(5).getAsString(), a.get(6).getAsInt() == 1,
					a.get(7).getAsInt(), a.get(8).getAsInt(), a.get(9).getAsInt(), a.get(10).getAsInt(), a.get(11).getAsInt(),
					a.get(12).getAsInt(), a.get(13).getAsInt(), a.get(14).getAsInt(),
					weights.getOrDefault(a.get(0).getAsInt(), 0));
				itemsById.put(item.getId(), item);
			}
			for (Map.Entry<String, JsonElement> alias : root.getAsJsonObject("aliases").entrySet())
			{
				aliasToBase.put(Integer.parseInt(alias.getKey()), alias.getValue().getAsInt());
			}
		}
	}

	private void loadMonsters(Gson gson) throws IOException
	{
		try (Reader reader = open("monsters.json"))
		{
			JsonArray root = gson.fromJson(reader, JsonArray.class);
			for (JsonElement e : root)
			{
				JsonArray a = e.getAsJsonArray();
				Set<String> attrs = new HashSet<>();
				for (JsonElement attr : a.get(16).getAsJsonArray())
				{
					attrs.add(attr.getAsString());
				}
				Monster m = new Monster(
					a.get(0).getAsInt(), a.get(1).getAsString(), a.get(2).getAsString(), a.get(3).getAsInt(),
					a.get(4).getAsInt(), a.get(5).getAsInt(), a.get(6).getAsInt(), a.get(7).getAsInt(),
					a.get(8).getAsInt(), a.get(9).getAsInt(), a.get(10).getAsInt(), a.get(11).getAsInt(),
					a.get(12).getAsInt(), a.get(13).getAsInt(), a.get(14).getAsInt(), a.get(15).getAsInt(),
					Collections.unmodifiableSet(attrs), a.get(17).getAsInt() == 1,
					a.get(18).getAsString(), a.get(19).getAsInt());
				monsters.add(m);
				monstersById.putIfAbsent(m.getId(), m);
			}
		}
	}

	/**
	 * Returns the stats to use for an owned item id, following cosmetic/locked/degraded variants
	 * back to their base item the same way the wiki calculator does.
	 */
	public GearItem getItem(int itemId)
	{
		Integer base = aliasToBase.get(itemId);
		if (base != null && itemsById.containsKey(base))
		{
			return itemsById.get(base);
		}
		return itemsById.get(itemId);
	}

	/**
	 * An item with this exact name, preferring usable (charged, active) variants and then the
	 * version-less entry.
	 */
	public GearItem findItem(String name)
	{
		GearItem found = null;
		for (GearItem item : itemsById.values())
		{
			if (item.getName().equals(name) && (found == null || findRank(item) < findRank(found)))
			{
				found = item;
			}
		}
		return found;
	}

	private static int findRank(GearItem item)
	{
		return (item.isUsable() ? 0 : 2) + (item.getVersion().isEmpty() ? 0 : 1);
	}

	/**
	 * For an unusable variant (uncharged, empty, inactive, broken), the usable version of the same
	 * item, or null if there is none.
	 */
	public GearItem findUsableVariant(GearItem item)
	{
		if (item.isUsable())
		{
			return item;
		}
		GearItem found = null;
		for (GearItem other : itemsById.values())
		{
			if (other.getName().equals(item.getName()) && other.getSlot() == item.getSlot() && other.isUsable()
				&& (found == null || preferredVariant(other) < preferredVariant(found)))
			{
				found = other;
			}
		}
		return found;
	}

	private static int preferredVariant(GearItem item)
	{
		switch (item.getVersion())
		{
			case "Charged":
			case "Active":
			case "100":
				return 0;
			case "":
				return 1;
			default:
				return 2;
		}
	}

	public Monster getMonster(int npcId)
	{
		return monstersById.get(npcId);
	}

	public List<Monster> getMonsters()
	{
		return Collections.unmodifiableList(monsters);
	}
}
