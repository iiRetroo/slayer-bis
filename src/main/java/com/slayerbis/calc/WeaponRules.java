package com.slayerbis.calc;

import com.slayerbis.data.AttackType;
import com.slayerbis.data.GearItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Weapon category behaviour (attack styles, ranged damage type) and ranged ammo compatibility,
 * following the OSRS Wiki DPS calculator.
 */
public final class WeaponRules
{
	private WeaponRules()
	{
	}

	public static final int DEFAULT_ATTACK_SPEED = 4;

	public enum RangedType
	{
		LIGHT, STANDARD, HEAVY
	}

	public enum AmmoKind
	{
		NONE, ARROW, BOLT, JAVELIN, BOLT_RACK, ATLATL_DART, UNSUPPORTED
	}

	/**
	 * The non-defensive stances a weapon category offers for an attack type. Defensive and
	 * longrange stances are never better for damage, so they are left out.
	 */
	public static List<Stance> stances(String category, AttackType type)
	{
		List<Stance> out = new ArrayList<>(3);
		switch (category)
		{
			case "2h Sword":
			case "Axe":
				if (type == AttackType.SLASH)
				{
					add(out, Stance.ACCURATE, Stance.AGGRESSIVE);
				}
				else if (type == AttackType.CRUSH)
				{
					add(out, Stance.AGGRESSIVE);
				}
				break;
			case "Banner":
				if (type == AttackType.STAB)
				{
					add(out, Stance.ACCURATE);
				}
				else if (type == AttackType.SLASH)
				{
					add(out, Stance.AGGRESSIVE);
				}
				else if (type == AttackType.CRUSH)
				{
					add(out, Stance.CONTROLLED);
				}
				break;
			case "Bladed Staff":
				if (type == AttackType.STAB)
				{
					add(out, Stance.ACCURATE);
				}
				else if (type == AttackType.SLASH)
				{
					add(out, Stance.AGGRESSIVE);
				}
				else if (type == AttackType.MAGIC)
				{
					add(out, Stance.AUTOCAST);
				}
				break;
			case "Bow":
			case "Crossbow":
			case "Thrown":
			case "Chinchompas":
				if (type == AttackType.RANGED)
				{
					add(out, Stance.ACCURATE, Stance.RAPID);
				}
				break;
			case "Bulwark":
				if (type == AttackType.CRUSH)
				{
					add(out, Stance.ACCURATE);
				}
				break;
			case "Multi-Melee":
				if (type == AttackType.STAB)
				{
					add(out, Stance.ACCURATE);
				}
				else if (type == AttackType.SLASH || type == AttackType.CRUSH)
				{
					add(out, Stance.AGGRESSIVE);
				}
				break;
			case "Partisan":
			case "Pickaxe":
				if (type == AttackType.STAB)
				{
					add(out, Stance.ACCURATE, Stance.AGGRESSIVE);
				}
				else if (type == AttackType.CRUSH)
				{
					add(out, Stance.AGGRESSIVE);
				}
				break;
			case "Polearm":
				if (type == AttackType.STAB)
				{
					add(out, Stance.CONTROLLED);
				}
				else if (type == AttackType.SLASH)
				{
					add(out, Stance.AGGRESSIVE);
				}
				break;
			case "Powered Staff":
			case "Powered Wand":
				if (type == AttackType.MAGIC)
				{
					add(out, Stance.ACCURATE);
				}
				break;
			case "Claw":
			case "Slash Sword":
				if (type == AttackType.SLASH)
				{
					add(out, Stance.ACCURATE, Stance.AGGRESSIVE);
				}
				else if (type == AttackType.STAB)
				{
					add(out, Stance.CONTROLLED);
				}
				break;
			case "Bludgeon":
				if (type == AttackType.CRUSH)
				{
					add(out, Stance.AGGRESSIVE);
				}
				break;
			case "Blunt":
			case "Polestaff":
				if (type == AttackType.CRUSH)
				{
					add(out, Stance.ACCURATE, Stance.AGGRESSIVE);
				}
				break;
			case "Spiked":
				if (type == AttackType.CRUSH)
				{
					add(out, Stance.ACCURATE, Stance.AGGRESSIVE);
				}
				else if (type == AttackType.STAB)
				{
					add(out, Stance.CONTROLLED);
				}
				break;
			case "Staff":
				if (type == AttackType.CRUSH)
				{
					add(out, Stance.ACCURATE, Stance.AGGRESSIVE);
				}
				else if (type == AttackType.MAGIC)
				{
					add(out, Stance.AUTOCAST);
				}
				break;
			case "Scythe":
				if (type == AttackType.SLASH)
				{
					add(out, Stance.ACCURATE, Stance.AGGRESSIVE);
				}
				else if (type == AttackType.CRUSH)
				{
					add(out, Stance.AGGRESSIVE);
				}
				break;
			case "Spear":
				if (type.isMelee())
				{
					add(out, Stance.CONTROLLED);
				}
				break;
			case "Stab Sword":
				if (type == AttackType.STAB)
				{
					add(out, Stance.ACCURATE, Stance.AGGRESSIVE);
				}
				else if (type == AttackType.SLASH)
				{
					add(out, Stance.AGGRESSIVE);
				}
				break;
			case "Whip":
				if (type == AttackType.SLASH)
				{
					add(out, Stance.ACCURATE, Stance.CONTROLLED);
				}
				break;
			case "Flail":
				if (type == AttackType.SLASH)
				{
					add(out, Stance.ACCURATE, Stance.AGGRESSIVE);
				}
				break;
			case "Gun":
				if (type == AttackType.CRUSH)
				{
					add(out, Stance.AGGRESSIVE);
				}
				break;
			case "":
			case "Unarmed":
				if (type == AttackType.CRUSH)
				{
					add(out, Stance.ACCURATE, Stance.AGGRESSIVE);
				}
				break;
			default:
				break;
		}
		return out.isEmpty() ? Collections.emptyList() : out;
	}

	private static void add(List<Stance> out, Stance... stances)
	{
		Collections.addAll(out, stances);
	}

	public static RangedType rangedType(String category)
	{
		switch (category)
		{
			case "Thrown":
				return RangedType.LIGHT;
			case "Crossbow":
			case "Chinchompas":
				return RangedType.HEAVY;
			default:
				return RangedType.STANDARD;
		}
	}

	/**
	 * Powered staves and wands with a built-in spell whose max hit the calculator knows.
	 */
	public static boolean isSupportedPoweredStaff(String name)
	{
		return poweredBaseMaxHit(name, 99) > 0;
	}

	/**
	 * Base max hit of a powered staff's built-in spell, or 0 if unsupported.
	 */
	public static int poweredBaseMaxHit(String name, int magicLevel)
	{
		switch (name)
		{
			case "Starter staff":
				return 8;
			case "Trident of the seas":
			case "Trident of the Seas":
			case "Trident of the Seas (e)":
				return Math.max(1, magicLevel / 3 - 5);
			case "Thammaron's sceptre":
				return Math.max(1, magicLevel / 3 - 8);
			case "Accursed sceptre":
				return Math.max(1, magicLevel / 3 - 6);
			case "Trident of the swamp":
			case "Trident of the Swamp":
			case "Trident of the Swamp (e)":
			case "Dawnbringer":
				return Math.max(1, magicLevel / 3 - 2);
			case "Sanguinesti staff":
			case "Holy sanguinesti staff":
				return Math.max(1, magicLevel / 3);
			case "Tumeken's shadow":
				return Math.max(1, magicLevel / 3 + 1);
			case "Eye of Ayak":
				return Math.max(1, magicLevel / 3 - 6);
			case "Warped sceptre":
				return Math.max(1, (8 * magicLevel + 96) / 37);
			case "Crystal staff (basic)":
			case "Corrupted staff (basic)":
				return 23;
			case "Crystal staff (attuned)":
			case "Corrupted staff (attuned)":
				return 31;
			case "Crystal staff (perfected)":
			case "Corrupted staff (perfected)":
				return 39;
			default:
				return 0;
		}
	}

	private static final Set<String> ANCIENT_AUTOCAST = Collections.unmodifiableSet(new java.util.HashSet<>(java.util.Arrays.asList(
		"Ancient staff", "Master wand", "Kodai wand", "Nightmare staff", "Harmonised Nightmare staff",
		"Eldritch Nightmare staff", "Volatile Nightmare staff", "Staff of the Dead", "Toxic staff of the dead",
		"Staff of Light", "Staff of Balance", "Zuriel's staff (bh)", "Ahrim's staff", "Void knight mace",
		"Accursed sceptre (a)", "Thammaron's sceptre (a)", "Purging staff"
	)));

	public static boolean canAutocastAncients(String name)
	{
		String lower = name.toLowerCase(Locale.ROOT);
		return ANCIENT_AUTOCAST.contains(name) || lower.contains("ancient sceptre") || lower.contains("kodai");
	}

	public static boolean isBlowpipe(String name)
	{
		return name.endsWith("blowpipe");
	}

	/**
	 * Which kind of ammo a ranged weapon fires from the ammo slot.
	 */
	public static AmmoKind ammoKind(GearItem weapon)
	{
		String n = weapon.getName();
		String lower = n.toLowerCase(Locale.ROOT);
		if (n.equals("Ogre bow") || n.equals("Comp ogre bow") || weapon.getCategory().equals("Salamander")
			|| n.equals("Hunters' crossbow") || n.equals("Hunters' sunlight crossbow") || n.equals("Training bow"))
		{
			return AmmoKind.UNSUPPORTED;
		}
		if (n.equals("Karil's crossbow"))
		{
			return AmmoKind.BOLT_RACK;
		}
		if (n.equals("Eclipse atlatl"))
		{
			return AmmoKind.ATLATL_DART;
		}
		if (lower.contains("ballista"))
		{
			return AmmoKind.JAVELIN;
		}
		if (weapon.getCategory().equals("Crossbow"))
		{
			return crossbowTier(n) > 0 ? AmmoKind.BOLT : AmmoKind.NONE;
		}
		if (weapon.getCategory().equals("Bow"))
		{
			return bowTier(n) > 0 ? AmmoKind.ARROW : AmmoKind.NONE;
		}
		return AmmoKind.NONE;
	}

	/**
	 * Whether an ammo item's ranged bonuses apply with this weapon (the calculator's INCLUDED state).
	 */
	public static boolean ammoUsable(GearItem weapon, GearItem ammo)
	{
		if (ammo == null)
		{
			return false;
		}
		String a = ammo.getName();
		String lower = a.toLowerCase(Locale.ROOT);
		switch (ammoKind(weapon))
		{
			case ARROW:
			{
				int tier = arrowTier(lower);
				return tier > 0 && tier <= bowTier(weapon.getName());
			}
			case BOLT:
			{
				int tier = boltTier(lower);
				return tier > 0 && tier <= crossbowTier(weapon.getName());
			}
			case JAVELIN:
				return lower.endsWith("javelin") && !lower.startsWith("morrigan");
			case BOLT_RACK:
				return a.equals("Bolt rack");
			case ATLATL_DART:
				return a.equals("Atlatl dart");
			default:
				return false;
		}
	}


	private static int bowTier(String name)
	{
		String lower = name.toLowerCase(Locale.ROOT);
		if (name.equals("Shortbow") || name.equals("Longbow") || name.equals("Cursed goblin bow") || name.equals("Rain bow"))
		{
			return 1;
		}
		if (lower.startsWith("oak ") || name.equals("Signed oak bow"))
		{
			return 5;
		}
		if (lower.startsWith("willow "))
		{
			return 20;
		}
		if (lower.startsWith("maple "))
		{
			return 30;
		}
		if (lower.startsWith("yew "))
		{
			return 40;
		}
		if (lower.startsWith("magic ") || name.equals("Bone shortbow") || name.equals("Seercull"))
		{
			return 50;
		}
		if (lower.startsWith("dark bow") || lower.equals("3rd age bow") || lower.contains("venator bow")
			|| name.equals("Twisted bow") || name.equals("Scorching bow") || name.equals("Nature's recurve"))
		{
			return 60;
		}
		return 0;
	}

	private static int arrowTier(String lower)
	{
		if (!lower.contains("arrow") || lower.contains("arrowtips") || lower.contains("ogre") || lower.contains("brutal")
			|| lower.contains("training"))
		{
			return 0;
		}
		if (lower.startsWith("bronze") || lower.startsWith("iron"))
		{
			return 1;
		}
		if (lower.startsWith("steel"))
		{
			return 5;
		}
		if (lower.startsWith("mithril"))
		{
			return 20;
		}
		if (lower.startsWith("adamant"))
		{
			return 30;
		}
		if (lower.startsWith("rune") || lower.startsWith("ice arrow"))
		{
			return 40;
		}
		if (lower.startsWith("amethyst") || lower.startsWith("broad") || lower.startsWith("seeking broad"))
		{
			return 50;
		}
		if (lower.startsWith("dragon"))
		{
			return 60;
		}
		return 0;
	}

	private static int crossbowTier(String name)
	{
		switch (name)
		{
			case "Crossbow":
			case "Phoenix crossbow":
			case "Bronze crossbow":
				return 1;
			case "Blurite crossbow":
			case "Dorgeshuun crossbow":
				return 16;
			case "Iron crossbow":
				return 26;
			case "Steel crossbow":
				return 31;
			case "Mithril crossbow":
				return 36;
			case "Adamant crossbow":
				return 46;
			case "Rune crossbow":
				return 61;
			case "Dragon crossbow":
			case "Dragon hunter crossbow":
			case "Armadyl crossbow":
			case "Zaryte crossbow":
			case "King's barrage":
				return 64;
			default:
				return 0;
		}
	}

	private static int boltTier(String lower)
	{
		if (!lower.contains("bolts") || lower.contains("bolt rack") || lower.contains("kebbit") || lower.contains("antler")
			|| lower.contains("unf") || lower.contains("bone bolts") || lower.contains("barbed"))
		{
			return 0;
		}
		if (lower.contains("dragon bolts") || lower.contains(" dragon bolts"))
		{
			return 64;
		}
		if (lower.startsWith("bronze") || lower.startsWith("opal"))
		{
			return 1;
		}
		if (lower.startsWith("blurite") || lower.startsWith("jade"))
		{
			return 16;
		}
		if (lower.startsWith("iron") || lower.startsWith("pearl") || lower.startsWith("silver"))
		{
			return 26;
		}
		if (lower.startsWith("steel") || lower.startsWith("topaz"))
		{
			return 31;
		}
		if (lower.startsWith("mithril") || lower.startsWith("sapphire") || lower.startsWith("emerald"))
		{
			return 36;
		}
		if (lower.startsWith("adamant") || lower.startsWith("ruby") || lower.startsWith("diamond"))
		{
			return 46;
		}
		if (lower.startsWith("runite") || lower.startsWith("broad") || lower.startsWith("amethyst broad")
			|| lower.startsWith("dragonstone") || lower.startsWith("onyx"))
		{
			return 61;
		}
		return 0;
	}

	/**
	 * Attack types this weapon (null for unarmed) can use for the optimiser.
	 */
	public static Set<AttackType> attackTypes(GearItem weapon)
	{
		Set<AttackType> out = EnumSet.noneOf(AttackType.class);
		String category = weapon == null ? "Unarmed" : weapon.getCategory();
		for (AttackType t : AttackType.values())
		{
			if (!stances(category, t).isEmpty())
			{
				out.add(t);
			}
		}
		if (weapon != null)
		{
			if (out.contains(AttackType.RANGED) && ammoKind(weapon) == AmmoKind.UNSUPPORTED)
			{
				out.remove(AttackType.RANGED);
			}
			if (category.startsWith("Powered") && !isSupportedPoweredStaff(weapon.getName()))
			{
				out.remove(AttackType.MAGIC);
			}
		}
		return out;
	}

	/**
	 * Two ranged weapons with the same key accept exactly the same ammo.
	 */
	public static String ammoKey(GearItem weapon)
	{
		AmmoKind kind = ammoKind(weapon);
		switch (kind)
		{
			case ARROW:
				return kind + ":" + bowTier(weapon.getName());
			case BOLT:
				return kind + ":" + crossbowTier(weapon.getName());
			default:
				return kind.name();
		}
	}
}
