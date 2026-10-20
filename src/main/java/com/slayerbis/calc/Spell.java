package com.slayerbis.calc;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import lombok.Value;

/**
 * Autocastable combat spells. Max hits match the OSRS Wiki DPS calculator's spell list.
 */
@Value
public class Spell
{
	String name;
	int level;
	int maxHit;
	boolean ancient;
	/** air, water, earth, fire or empty */
	String element;

	public static final List<Spell> STANDARD = ImmutableList.of(
		new Spell("Wind Strike", 1, 2, false, "air"),
		new Spell("Water Strike", 5, 4, false, "water"),
		new Spell("Earth Strike", 9, 6, false, "earth"),
		new Spell("Fire Strike", 13, 8, false, "fire"),
		new Spell("Wind Bolt", 17, 9, false, "air"),
		new Spell("Water Bolt", 23, 10, false, "water"),
		new Spell("Earth Bolt", 29, 11, false, "earth"),
		new Spell("Fire Bolt", 35, 12, false, "fire"),
		new Spell("Wind Blast", 41, 13, false, "air"),
		new Spell("Water Blast", 47, 14, false, "water"),
		new Spell("Earth Blast", 53, 15, false, "earth"),
		new Spell("Fire Blast", 59, 16, false, "fire"),
		new Spell("Wind Wave", 62, 17, false, "air"),
		new Spell("Water Wave", 65, 18, false, "water"),
		new Spell("Earth Wave", 70, 19, false, "earth"),
		new Spell("Fire Wave", 75, 20, false, "fire"),
		new Spell("Wind Surge", 81, 21, false, "air"),
		new Spell("Water Surge", 85, 22, false, "water"),
		new Spell("Earth Surge", 90, 23, false, "earth"),
		new Spell("Fire Surge", 95, 24, false, "fire")
	);

	public static final List<Spell> ANCIENT = ImmutableList.of(
		new Spell("Smoke Rush", 50, 13, true, ""),
		new Spell("Shadow Rush", 52, 14, true, ""),
		new Spell("Blood Rush", 56, 15, true, ""),
		new Spell("Ice Rush", 58, 16, true, ""),
		new Spell("Smoke Burst", 62, 17, true, ""),
		new Spell("Shadow Burst", 64, 18, true, ""),
		new Spell("Blood Burst", 68, 21, true, ""),
		new Spell("Ice Burst", 70, 22, true, ""),
		new Spell("Smoke Blitz", 74, 23, true, ""),
		new Spell("Shadow Blitz", 76, 24, true, ""),
		new Spell("Blood Blitz", 80, 25, true, ""),
		new Spell("Ice Blitz", 82, 26, true, ""),
		new Spell("Smoke Barrage", 86, 27, true, ""),
		new Spell("Shadow Barrage", 88, 28, true, ""),
		new Spell("Blood Barrage", 92, 29, true, ""),
		new Spell("Ice Barrage", 94, 30, true, "")
	);

	/**
	 * Bursts and barrages hit up to 9 monsters around the target.
	 */
	public boolean isMultiTarget()
	{
		return ancient && (name.endsWith("Burst") || name.endsWith("Barrage"));
	}

	public static List<Spell> castable(List<Spell> book, int magicLevel)
	{
		List<Spell> out = new ArrayList<>();
		for (Spell s : book)
		{
			if (s.level <= magicLevel)
			{
				out.add(s);
			}
		}
		return out;
	}

	@Override
	public String toString()
	{
		return name;
	}
}
