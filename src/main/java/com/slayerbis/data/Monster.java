package com.slayerbis.data;

import java.util.Set;
import lombok.Value;

@Value
public class Monster
{
	int id;
	String name;
	String version;
	int size;
	int hitpoints;
	int defenceLevel;
	int magicLevel;
	int magicAttackBonus;
	int defStab;
	int defSlash;
	int defCrush;
	int defMagic;
	int defLight;
	int defStandard;
	int defHeavy;
	int flatArmour;
	Set<String> attributes;
	boolean slayerMonster;
	String weaknessElement;
	int weaknessSeverity;

	public boolean has(String attribute)
	{
		return attributes.contains(attribute);
	}

	public String displayName()
	{
		return version.isEmpty() ? name : name + " (" + version + ")";
	}

	public static final String DEMON = "demon";
	public static final String DRAGON = "dragon";
	public static final String FIERY = "fiery";
	public static final String FLYING = "flying";
	public static final String GOLEM = "golem";
	public static final String KALPHITE = "kalphite";
	public static final String LEAFY = "leafy";
	public static final String RAT = "rat";
	public static final String UNDEAD = "undead";
	public static final String VAMPYRE_1 = "vampyre1";
	public static final String VAMPYRE_2 = "vampyre2";
	public static final String VAMPYRE_3 = "vampyre3";
	public static final String XERICIAN = "xerician";
}
