package com.slayerbis.data;

import lombok.Value;

/**
 * Combat-relevant stats of one equippable item, as used by the OSRS Wiki DPS calculator.
 * Magic damage is stored in tenths of a percent (occult necklace = 50).
 */
@Value
public class GearItem
{
	int id;
	String name;
	String version;
	EquipmentSlot slot;
	int speed;
	String category;
	boolean twoHanded;
	int stab;
	int slash;
	int crush;
	int magic;
	int ranged;
	int strength;
	int rangedStrength;
	int magicDamage;
	/** Weight in grams (negative for weight-reducing items such as graceful). */
	int weight;

	/**
	 * Whether this variant can actually be used in combat. Uncharged, empty, inactive and broken
	 * variants are listed in the data but cannot attack (weapons) or be worn (broken armour).
	 */
	public boolean isUsable()
	{
		switch (version)
		{
			case "Broken":
			case "Inactive":
			case "0":
				return false;
			case "Uncharged":
			case "Empty":
				return slot != EquipmentSlot.WEAPON;
			default:
				return true;
		}
	}

	public int offensive(AttackType type)
	{
		switch (type)
		{
			case STAB:
				return stab;
			case SLASH:
				return slash;
			case CRUSH:
				return crush;
			case MAGIC:
				return magic;
			case RANGED:
				return ranged;
			default:
				return 0;
		}
	}

	@Override
	public String toString()
	{
		return name;
	}
}
