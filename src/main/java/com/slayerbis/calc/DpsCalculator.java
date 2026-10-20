package com.slayerbis.calc;

import com.slayerbis.data.AttackType;
import com.slayerbis.data.EquipmentSlot;
import com.slayerbis.data.GearItem;
import com.slayerbis.data.Monster;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Player-vs-NPC DPS using the formulas of the OSRS Wiki DPS calculator
 * (https://github.com/weirdgloop/osrs-dps-calc, PlayerVsNPCCalc). Special attacks,
 * wilderness/raid/boss-phase mechanics and other situational buffs are not modelled.
 */
public final class DpsCalculator
{
	private static final double SECONDS_PER_TICK = 0.6;

	private static final int HEAD = EquipmentSlot.HEAD.ordinal();
	private static final int CAPE = EquipmentSlot.CAPE.ordinal();
	private static final int NECK = EquipmentSlot.NECK.ordinal();
	private static final int AMMO = EquipmentSlot.AMMO.ordinal();
	private static final int WEAPON = EquipmentSlot.WEAPON.ordinal();
	private static final int BODY = EquipmentSlot.BODY.ordinal();
	private static final int SHIELD = EquipmentSlot.SHIELD.ordinal();
	private static final int LEGS = EquipmentSlot.LEGS.ordinal();
	private static final int HANDS = EquipmentSlot.HANDS.ordinal();

	private final PlayerSetup player;
	private final Monster monster;

	public DpsCalculator(PlayerSetup player, Monster monster)
	{
		this.player = player;
		this.monster = monster;
	}

	/**
	 * Best DPS for this gear and attack type over every stance (and, for autocasting, every spell)
	 * the weapon offers. Returns {@link CalcResult#NONE} if the gear cannot attack with this type.
	 */
	public CalcResult best(GearItem[] gear, AttackType type)
	{
		if (type == AttackType.MAGIC && player.getBurstTargets() > 0)
		{
			return bestBurst(gear);
		}
		GearItem weapon = gear[WEAPON];
		String category = weapon == null ? "Unarmed" : weapon.getCategory();
		List<Stance> stances = WeaponRules.stances(category, type);
		if (stances.isEmpty())
		{
			return CalcResult.NONE;
		}

		Set<String> names = new HashSet<>(16);
		for (GearItem g : gear)
		{
			if (g != null)
			{
				names.add(g.getName());
			}
		}

		CalcResult best = CalcResult.NONE;
		for (Stance stance : stances)
		{
			if (stance == Stance.AUTOCAST)
			{
				List<Spell> book = player.isAncientSpellbook() && weapon != null && WeaponRules.canAutocastAncients(weapon.getName())
					? Spell.ANCIENT : Spell.STANDARD;
				for (Spell spell : Spell.castable(book, player.getMagic()))
				{
					CalcResult r = new Attack(gear, names, type, stance, spell).result();
					if (r.getDps() > best.getDps())
					{
						best = r;
					}
				}
			}
			else
			{
				CalcResult r = new Attack(gear, names, type, stance, null).result();
				if (r.getDps() > best.getDps() || best == CalcResult.NONE)
				{
					best = r;
				}
			}
		}
		return best;
	}

	/**
	 * Casting an Ancient burst or barrage by hand (any weapon can) on a stack of monsters: the best
	 * one for the Magic level, with DPS on one target times the number of targets. Each target is
	 * rolled separately in game, so the expected damage simply adds up.
	 */
	private CalcResult bestBurst(GearItem[] gear)
	{
		Set<String> names = new HashSet<>(16);
		for (GearItem g : gear)
		{
			if (g != null)
			{
				names.add(g.getName());
			}
		}
		CalcResult best = CalcResult.NONE;
		for (Spell spell : Spell.castable(Spell.ANCIENT, player.getMagic()))
		{
			if (!spell.isMultiTarget())
			{
				continue;
			}
			CalcResult r = new Attack(gear, names, AttackType.MAGIC, Stance.AUTOCAST, spell).result();
			if (r.getDps() > best.getDps())
			{
				best = r;
			}
		}
		if (best == CalcResult.NONE)
		{
			return best;
		}
		return new CalcResult(best.getDps() * player.getBurstTargets(), best.getMaxHit(), best.getAccuracy(),
			best.getAttackSpeedTicks(), best.getStance(), best.getSpell());
	}

	/**
	 * DPS for one specific stance (no autocast spell). Used by tests to compare against the wiki
	 * calculator one combat style at a time.
	 */
	CalcResult single(GearItem[] gear, AttackType type, Stance stance)
	{
		return single(gear, type, stance, null);
	}

	/**
	 * As above, casting a given spell (autocast or by hand: the maths is the same).
	 */
	CalcResult single(GearItem[] gear, AttackType type, Stance stance, Spell spell)
	{
		Set<String> names = new HashSet<>(16);
		for (GearItem g : gear)
		{
			if (g != null)
			{
				names.add(g.getName());
			}
		}
		return new Attack(gear, names, type, stance, spell).result();
	}

	/**
	 * One fully specified attack (gear, attack type, stance, spell).
	 */
	private final class Attack
	{
		private final GearItem[] gear;
		private final Set<String> names;
		private final AttackType type;
		private final Stance stance;
		private final Spell spell;
		private final GearItem weapon;
		private final String weaponName;

		private int offensive;
		private int strength;
		private int rangedStrength;
		private int magicDamage;
		private boolean ammoIncluded;

		Attack(GearItem[] gear, Set<String> names, AttackType type, Stance stance, Spell spell)
		{
			this.gear = gear;
			this.names = names;
			this.type = type;
			this.stance = stance;
			this.spell = spell;
			this.weapon = gear[WEAPON];
			this.weaponName = weapon == null ? "" : weapon.getName();
			sumBonuses();
		}

		private boolean wearing(String name)
		{
			return names.contains(name);
		}

		private boolean wearingAny(String... any)
		{
			for (String n : any)
			{
				if (names.contains(n))
				{
					return true;
				}
			}
			return false;
		}

		private boolean wearingAll(String... all)
		{
			for (String n : all)
			{
				if (!names.contains(n))
				{
					return false;
				}
			}
			return true;
		}

		private boolean melee()
		{
			return type.isMelee();
		}

		// ---- equipment bonuses (Equipment.calculateEquipmentBonusesFromGear) ----

		private void sumBonuses()
		{
			ammoIncluded = weapon != null && gear[AMMO] != null && WeaponRules.ammoUsable(weapon, gear[AMMO]);
			for (int i = 0; i < gear.length; i++)
			{
				GearItem g = gear[i];
				if (g == null)
				{
					continue;
				}
				boolean rangedStats = i != AMMO || ammoIncluded;
				offensive += (type == AttackType.RANGED && !rangedStats) ? 0 : g.offensive(type);
				strength += g.getStrength();
				rangedStrength += rangedStats ? g.getRangedStrength() : 0;
				magicDamage += g.getMagicDamage();
			}

			if (WeaponRules.isBlowpipe(weaponName))
			{
				rangedStrength += player.getBlowpipeDartStrength();
			}
			// tripled for its own spell only, not for a spell cast by hand
			if (weaponName.equals("Tumeken's shadow") && type == AttackType.MAGIC && spell == null)
			{
				magicDamage = Math.min(1000, magicDamage * 3);
				offensive *= 3;
			}
			if (weaponName.equals("Keris partisan of amascut"))
			{
				strength -= 22;
				if (type == AttackType.STAB)
				{
					offensive -= 50;
				}
			}
			if (spell != null && spell.isAncient())
			{
				for (int slot : new int[]{HEAD, BODY, LEGS})
				{
					if (gear[slot] != null && gear[slot].getName().contains("Virtus"))
					{
						magicDamage += 30;
					}
				}
			}
			if (gear[HEAD] != null && gear[HEAD].getName().equals("Void mage helm")
				&& gear[BODY] != null && gear[BODY].getName().equals("Elite void top")
				&& gear[LEGS] != null && gear[LEGS].getName().equals("Elite void robe")
				&& gear[HANDS] != null && gear[HANDS].getName().equals("Void knight gloves"))
			{
				magicDamage += 50;
			}
			GearItem cape = gear[CAPE];
			if (cape != null && ammoIncluded && (cape.getName().equals("Dizana's max cape") || cape.getName().equals("Blessed Dizana's quiver")
				|| (cape.getName().equals("Dizana's quiver") && cape.getVersion().equals("Charged"))))
			{
				if (type == AttackType.RANGED)
				{
					offensive += 10;
				}
				rangedStrength += 1;
			}
		}

		// ---- set and item checks (BaseCalc) ----

		private boolean voidRobes()
		{
			return wearingAny("Void knight top", "Void knight top (or)", "Elite void top", "Elite void top (or)")
				&& wearingAny("Void knight robe", "Void knight robe (or)", "Elite void robe", "Elite void robe (or)")
				&& wearing("Void knight gloves");
		}

		private boolean eliteVoidRobes()
		{
			return wearingAny("Elite void top", "Elite void top (or)")
				&& wearingAny("Elite void robe", "Elite void robe (or)")
				&& wearing("Void knight gloves");
		}

		private boolean meleeVoid()
		{
			return voidRobes() && wearingAny("Void melee helm", "Void melee helm (or)");
		}

		private boolean rangedVoid()
		{
			return voidRobes() && wearingAny("Void ranger helm", "Void ranger helm (or)");
		}

		private boolean eliteRangedVoid()
		{
			return eliteVoidRobes() && wearingAny("Void ranger helm", "Void ranger helm (or)");
		}

		private boolean magicVoid()
		{
			return voidRobes() && wearingAny("Void mage helm", "Void mage helm (or)");
		}

		private boolean blackMask()
		{
			return imbuedBlackMask() || wearingAny("Black mask", "Slayer helmet");
		}

		private boolean imbuedBlackMask()
		{
			return wearingAny("Black mask (i)", "Slayer helmet (i)", "V's helm");
		}

		private boolean slayerBonus()
		{
			return monster.isSlayerMonster() && player.isOnSlayerTask();
		}

		private boolean tzhaarWeapon()
		{
			return wearingAny("Tzhaar-ket-em", "Tzhaar-ket-om", "Tzhaar-ket-om (t)", "Toktz-xil-ak", "Toktz-xil-ek", "Toktz-mej-tal");
		}

		private boolean obsidian()
		{
			return wearingAll("Obsidian helmet", "Obsidian platelegs", "Obsidian platebody");
		}

		private boolean crystalBow()
		{
			return weaponName.equals("Crystal bow") || weaponName.contains("Bow of Faerdhinen");
		}

		private int crystalPieces()
		{
			return (wearing("Crystal helm") ? 1 : 0) + (wearing("Crystal legs") ? 2 : 0) + (wearing("Crystal body") ? 3 : 0);
		}

		private boolean fang()
		{
			return wearingAny("Osmumten's fang", "Osmumten's fang (or)");
		}

		private boolean scythe()
		{
			return weaponName.toLowerCase().contains("of vitur");
		}

		private boolean twoHitWeapon()
		{
			return wearingAny("Torag's hammers", "Sulphur blades", "Glacial temotli", "Earthbound tecpatl");
		}

		private boolean keris()
		{
			return weaponName.contains("Keris");
		}

		private boolean veracs()
		{
			return wearingAll("Verac's helm", "Verac's brassard", "Verac's plateskirt", "Verac's flail");
		}

		private boolean karils()
		{
			return wearingAll("Karil's coif", "Karil's leathertop", "Karil's leatherskirt", "Karil's crossbow", "Amulet of the Damned");
		}

		private boolean ahrims()
		{
			return wearingAll("Ahrim's staff", "Ahrim's hood", "Ahrim's robetop", "Ahrim's robeskirt", "Amulet of the Damned");
		}

		private boolean berserkerNecklace()
		{
			return wearingAny("Berserker necklace", "Berserker necklace (or)");
		}

		private boolean smokeStaff()
		{
			return wearingAny("Smoke battlestaff", "Mystic smoke staff", "Twinflame staff");
		}

		private boolean ratBoneWeapon()
		{
			return wearingAny("Bone mace", "Bone shortbow", "Bone staff");
		}

		private boolean silverWeapon()
		{
			if (type == AttackType.RANGED && gear[AMMO] != null && gear[AMMO].getName().startsWith("Silver bolts"))
			{
				return true;
			}
			return melee() && wearingAny("Blessed axe", "Ivandis flail", "Blisterwood flail", "Hallowed flail", "Silver sickle",
				"Silver sickle (b)", "Emerald sickle", "Emerald sickle (b)", "Enchanted emerald sickle (b)", "Ruby sickle (b)",
				"Enchanted ruby sickle (b)", "Blisterwood sickle", "Silverlight", "Darklight", "Arclight", "Rod of Ivandis", "Wolfbane");
		}

		private boolean vampyrebane(boolean tier2)
		{
			if (type == AttackType.RANGED && weaponName.equals("Blisterwood stake"))
			{
				return true;
			}
			return (tier2 || melee()) && (wearingAny("Ivandis flail", "Blisterwood sickle", "Blisterwood flail", "Hallowed flail", "Sunspear")
				|| (tier2 && wearing("Rod of Ivandis")));
		}

		private boolean leafBladed()
		{
			if (melee() && wearingAny("Leaf-bladed battleaxe", "Leaf-bladed spear", "Leaf-bladed sword"))
			{
				return true;
			}
			return type == AttackType.RANGED && ammoIncluded
				&& wearingAny("Broad arrows", "Broad bolts", "Amethyst broad bolts", "Seeking broad arrows");
		}

		private boolean usingDemonbane()
		{
			if (type == AttackType.RANGED)
			{
				return wearing("Scorching bow");
			}
			return melee() && wearingAny("Silverlight", "Darklight", "Arclight", "Emberlight", "Bone claws", "Burning claws");
		}

		private boolean usingAbyssal()
		{
			return melee() && wearingAny("Abyssal bludgeon", "Abyssal dagger", "Abyssal whip", "Abyssal tentacle");
		}

		private boolean corpbane()
		{
			if (weapon == null)
			{
				return false;
			}
			boolean stab = type == AttackType.STAB;
			if (fang() || weaponName.endsWith("halberd") || (weaponName.contains("spear") && !weaponName.equals("Blue moon spear")))
			{
				return stab;
			}
			return type == AttackType.MAGIC;
		}

		private int[] demonbaneFactor(int percent)
		{
			return new int[]{percent, 100};
		}

		private int addFactor(int base, int[] factor)
		{
			return base + base * factor[0] / factor[1];
		}

		private String spellElement()
		{
			return spell == null ? "" : spell.getElement();
		}

		// ---- accuracy ----

		private int npcDefenceRoll()
		{
			int level = type == AttackType.MAGIC ? monster.getMagicLevel() : monster.getDefenceLevel();
			int bonus;
			switch (type)
			{
				case STAB:
					bonus = monster.getDefStab();
					break;
				case SLASH:
					bonus = monster.getDefSlash();
					break;
				case CRUSH:
					bonus = monster.getDefCrush();
					break;
				case MAGIC:
					bonus = monster.getDefMagic();
					break;
				default:
					switch (WeaponRules.rangedType(weapon.getCategory()))
					{
						case LIGHT:
							bonus = monster.getDefLight();
							break;
						case HEAVY:
							bonus = monster.getDefHeavy();
							break;
						default:
							bonus = monster.getDefStandard();
							break;
					}
			}
			return (level + 9) * (bonus + 64);
		}

		private int meleeAttackRoll()
		{
			int effective = player.getAttack() * player.getMeleeAccuracyPrayer() / 100;
			int stanceBonus = 8;
			if (stance == Stance.ACCURATE)
			{
				stanceBonus += 3;
			}
			else if (stance == Stance.CONTROLLED)
			{
				stanceBonus += 1;
			}
			effective += stanceBonus;
			if (meleeVoid())
			{
				effective = effective * 11 / 10;
			}
			int baseRoll = effective * (offensive + 64);
			int roll = baseRoll;

			if (wearing("Amulet of avarice") && monster.getName().startsWith("Revenant"))
			{
				roll = roll * 24 / 20;
			}
			else if (wearingAny("Salve amulet (e)", "Salve amulet(ei)") && monster.has(Monster.UNDEAD))
			{
				roll = roll * 6 / 5;
			}
			else if (wearingAny("Salve amulet", "Salve amulet(i)") && monster.has(Monster.UNDEAD))
			{
				roll = roll * 7 / 6;
			}
			else if (blackMask() && slayerBonus())
			{
				roll = roll * 7 / 6;
			}

			if (tzhaarWeapon() && obsidian())
			{
				roll += baseRoll / 10;
			}
			if (wearingAny("Arclight", "Emberlight") && monster.has(Monster.DEMON))
			{
				roll = addFactor(roll, demonbaneFactor(70));
			}
			if (wearingAny("Silverlight", "Darklight") && monster.has(Monster.DEMON))
			{
				roll = addFactor(roll, demonbaneFactor(60));
			}
			if (wearingAny("Bone claws", "Burning claws") && monster.has(Monster.DEMON))
			{
				roll = addFactor(roll, demonbaneFactor(5));
			}
			if (monster.has(Monster.DRAGON))
			{
				if (wearing("Dragon hunter lance"))
				{
					roll = roll * 6 / 5;
				}
				else if (wearing("Dragon hunter wand"))
				{
					roll = roll * 7 / 4;
				}
			}
			if (wearing("Keris partisan of breaching") && monster.has(Monster.KALPHITE))
			{
				roll = roll * 133 / 100;
			}
			if (wearingAny("Blisterwood flail", "Blisterwood sickle") && isVampyre())
			{
				roll = roll * 105 / 100;
			}
			if (wearingAny("Hallowed flail", "Sunspear") && isVampyre())
			{
				roll = roll * 125 / 100;
			}
			if (wearing("Granite hammer") && monster.has(Monster.GOLEM))
			{
				roll = roll * 13 / 10;
			}
			if (type == AttackType.CRUSH)
			{
				int inq = inquisitorBonus();
				if (inq > 0)
				{
					roll = roll * (200 + inq) / 200;
				}
			}
			return roll;
		}

		private int inquisitorBonus()
		{
			return (wearing("Inquisitor's great helm") ? 1 : 0) + (wearing("Inquisitor's hauberk") ? 2 : 0)
				+ (wearing("Inquisitor's plateskirt") ? 2 : 0);
		}

		private boolean isVampyre()
		{
			return monster.has(Monster.VAMPYRE_1) || monster.has(Monster.VAMPYRE_2) || monster.has(Monster.VAMPYRE_3);
		}

		private int rangedAttackRoll()
		{
			int effective = player.getRanged() * player.getRangedAccuracyPrayer() / 100;
			if (stance == Stance.ACCURATE)
			{
				effective += 3;
			}
			effective += 8;
			if (rangedVoid())
			{
				effective = effective * 11 / 10;
			}
			int roll = effective * (offensive + 64);
			if (crystalBow())
			{
				roll = roll * (20 + crystalPieces()) / 20;
			}

			if (wearing("Amulet of avarice") && monster.getName().startsWith("Revenant"))
			{
				roll = roll * 24 / 20;
			}
			else if (wearing("Salve amulet(ei)") && monster.has(Monster.UNDEAD))
			{
				roll = roll * 6 / 5;
			}
			else if (wearing("Salve amulet(i)") && monster.has(Monster.UNDEAD))
			{
				roll = roll * 7 / 6;
			}
			else if (imbuedBlackMask() && slayerBonus())
			{
				roll = roll * 23 / 20;
			}

			if (wearing("Twisted bow"))
			{
				roll = tbowScaling(roll, tbowMagic(), true);
			}
			if (wearing("Dragon hunter crossbow") && monster.has(Monster.DRAGON))
			{
				roll = roll * 13 / 10;
			}
			if ("Chinchompas".equals(weapon.getCategory()))
			{
				// the calculator's default distance of 4 tiles
				int numerator = stance == Stance.RAPID ? 4 : 3;
				roll = roll * numerator / 4;
			}
			if (wearing("Scorching bow") && monster.has(Monster.DEMON))
			{
				roll = addFactor(roll, demonbaneFactor(30));
			}
			return roll;
		}

		private int tbowMagic()
		{
			int cap = monster.has(Monster.XERICIAN) ? 350 : 250;
			return Math.min(cap, Math.max(monster.getMagicLevel(), monster.getMagicAttackBonus()));
		}

		private int magicAttackRoll()
		{
			int effective = player.getMagic() * player.getMagicAccuracyPrayer() / 100;
			if (stance == Stance.ACCURATE)
			{
				effective += 2;
			}
			effective += 9;
			if (magicVoid())
			{
				effective = effective * 29 / 20;
			}
			int baseRoll = effective * (offensive + 64);
			int roll = baseRoll;

			int additive = 0;
			boolean mask = false;
			if (wearing("Amulet of avarice") && monster.getName().startsWith("Revenant"))
			{
				additive += 20;
			}
			else if (wearing("Salve amulet(ei)") && monster.has(Monster.UNDEAD))
			{
				additive += 20;
			}
			else if (wearing("Salve amulet(i)") && monster.has(Monster.UNDEAD))
			{
				additive += 15;
			}
			else if (imbuedBlackMask() && slayerBonus())
			{
				mask = true;
			}
			if (smokeStaff() && spell != null && !spell.isAncient())
			{
				additive += 10;
			}
			if (additive != 0)
			{
				roll = roll * (100 + additive) / 100;
			}
			if (monster.has(Monster.DRAGON))
			{
				if (wearing("Dragon hunter crossbow"))
				{
					roll = roll * 13 / 10;
				}
				else if (wearing("Dragon hunter lance"))
				{
					roll = roll * 6 / 5;
				}
				else if (wearing("Dragon hunter wand"))
				{
					roll = roll * 7 / 4;
				}
			}
			if (mask)
			{
				roll = roll * 23 / 20;
			}
			if (wearing("Tome of Water") && "water".equals(spellElement()))
			{
				roll = roll * 6 / 5;
			}
			String element = spellElement();
			if (!element.isEmpty() && element.equals(monster.getWeaknessElement()))
			{
				roll += baseRoll * monster.getWeaknessSeverity() / 100;
			}
			return roll;
		}

		private int attackRoll()
		{
			switch (type)
			{
				case RANGED:
					return rangedAttackRoll();
				case MAGIC:
					return magicAttackRoll();
				default:
					return meleeAttackRoll();
			}
		}

		private double hitChance(int atk, int def)
		{
			double chance = normalAccuracy(atk, def);
			if (fang() && type == AttackType.STAB)
			{
				chance = fangAccuracy(atk, def);
			}
			if (wearing("Confliction gauntlets") && type == AttackType.MAGIC && !weapon.isTwoHanded())
			{
				double doubleRoll = fangAccuracy(atk, def);
				chance = doubleRoll / (1 + doubleRoll - normalAccuracy(atk, def));
			}
			return chance;
		}

		// ---- max hits ----

		private int[] meleeMaxHit()
		{
			int effective = player.getStrength() * player.getMeleeStrengthPrayer() / 100;
			int stanceBonus = 8;
			if (stance == Stance.AGGRESSIVE)
			{
				stanceBonus += 3;
			}
			else if (stance == Stance.CONTROLLED)
			{
				stanceBonus += 1;
			}
			effective += stanceBonus;
			if (meleeVoid())
			{
				effective = effective * 11 / 10;
			}
			int baseMax = (effective * (strength + 64) + 320) / 640;
			int min = 0;
			int max = baseMax;

			if (wearing("Amulet of avarice") && monster.getName().startsWith("Revenant"))
			{
				max = max * 24 / 20;
			}
			else if (wearingAny("Salve amulet (e)", "Salve amulet(ei)") && monster.has(Monster.UNDEAD))
			{
				max = max * 6 / 5;
			}
			else if (wearingAny("Salve amulet", "Salve amulet(i)") && monster.has(Monster.UNDEAD))
			{
				max = max * 7 / 6;
			}
			else if (blackMask() && slayerBonus())
			{
				max = max * 7 / 6;
			}

			if (wearingAny("Arclight", "Emberlight") && monster.has(Monster.DEMON))
			{
				max = addFactor(max, demonbaneFactor(70));
			}
			if (wearingAny("Bone claws", "Burning claws") && monster.has(Monster.DEMON))
			{
				max = addFactor(max, demonbaneFactor(5));
			}
			if (tzhaarWeapon() && obsidian())
			{
				max += baseMax / 10;
			}
			if (wearing("Dragon hunter lance") && monster.has(Monster.DRAGON))
			{
				max = max * 6 / 5;
			}
			if (wearing("Dragon hunter wand") && monster.has(Monster.DRAGON))
			{
				max = max * 7 / 5;
			}
			if (keris() && monster.has(Monster.KALPHITE))
			{
				max = wearing("Keris partisan of amascut") ? max * 115 / 100 : max * 133 / 100;
			}
			if (wearing("Barronite mace") && monster.has(Monster.GOLEM))
			{
				max = max * 23 / 20;
			}
			if (wearing("Granite hammer") && monster.has(Monster.GOLEM))
			{
				max = max * 13 / 10;
			}
			if (wearingAny("Silverlight", "Darklight", "Silverlight (dyed)") && monster.has(Monster.DEMON))
			{
				max = addFactor(max, demonbaneFactor(60));
			}
			if (wearing("Leaf-bladed battleaxe") && monster.has(Monster.LEAFY))
			{
				max = max * 47 / 40;
			}
			if (wearing("Colossal blade"))
			{
				max += Math.min(monster.getSize() * 2, 10);
			}
			if (ratBoneWeapon() && monster.has(Monster.RAT))
			{
				max += 10;
			}
			if (type == AttackType.CRUSH)
			{
				int inq = inquisitorBonus();
				if (inq > 0)
				{
					max = max * (200 + inq) / 200;
				}
			}
			if (fang())
			{
				int shrink = max * 3 / 20;
				min = shrink;
				max -= shrink;
			}
			return new int[]{min, max};
		}

		private int[] rangedMaxHit()
		{
			boolean scalesWithStr = wearingAny("Eclipse atlatl", "Hunter's spear");
			int effective = scalesWithStr ? player.getStrength() : player.getRanged();
			effective = effective * player.getRangedStrengthPrayer() / 100;
			if (stance == Stance.ACCURATE)
			{
				effective += 3;
			}
			effective += 8;
			if (eliteRangedVoid())
			{
				effective = effective * 9 / 8;
			}
			else if (rangedVoid())
			{
				effective = effective * 11 / 10;
			}
			int bonusStr = scalesWithStr ? strength : rangedStrength;
			int max = (effective * (64 + bonusStr) + 320) / 640;

			if (crystalBow())
			{
				max = max * (40 + crystalPieces()) / 40;
			}

			boolean needDragonbane = wearing("Dragon hunter crossbow") && monster.has(Monster.DRAGON);
			boolean needDemonbane = wearing("Scorching bow") && monster.has(Monster.DEMON);

			if (wearing("Amulet of avarice") && monster.getName().startsWith("Revenant"))
			{
				max = max * 24 / 20;
			}
			else if ((wearing("Salve amulet(ei)") || (scalesWithStr && wearing("Salve amulet (e)"))) && monster.has(Monster.UNDEAD))
			{
				max = max * 6 / 5;
			}
			else if ((wearing("Salve amulet(i)") || (scalesWithStr && wearing("Salve amulet"))) && monster.has(Monster.UNDEAD))
			{
				max = max * 7 / 6;
			}
			else if (scalesWithStr && blackMask() && slayerBonus())
			{
				max = max * 7 / 6;
			}
			else if (imbuedBlackMask() && slayerBonus())
			{
				int numerator = 23;
				if (needDragonbane)
				{
					needDragonbane = false;
					numerator += 5;
				}
				if (needDemonbane)
				{
					needDemonbane = false;
					numerator += 6;
				}
				max = max * numerator / 20;
			}

			if (wearing("Twisted bow"))
			{
				max = tbowScaling(max, tbowMagic(), false);
			}
			if (needDragonbane)
			{
				max = max * 5 / 4;
			}
			if (needDemonbane)
			{
				max = addFactor(max, demonbaneFactor(30));
			}
			if (ratBoneWeapon() && monster.has(Monster.RAT))
			{
				max += 10;
			}
			if (wearing("Tonalztics of Ralos"))
			{
				max = max * 3 / 4;
			}
			return new int[]{0, max};
		}

		private int[] magicMaxHit()
		{
			int level = player.getMagic();
			int max;
			if (spell != null)
			{
				max = spell.getMaxHit();
			}
			else
			{
				max = WeaponRules.poweredBaseMaxHit(weaponName, level);
			}
			if (max == 0)
			{
				return new int[]{0, 0};
			}

			if (wearing("Chaos gauntlets") && spell != null && spell.getName().contains("Bolt"))
			{
				max += 3;
			}
			if (hasMatchingElementalAmulet())
			{
				max += 2;
			}
			int baseMax = max;
			int dmgBonus = magicDamage;
			if (smokeStaff() && spell != null && !spell.isAncient())
			{
				dmgBonus += 100;
			}
			boolean mask = false;
			if (wearing("Salve amulet(ei)") && monster.has(Monster.UNDEAD))
			{
				dmgBonus += 200;
			}
			else if (wearing("Salve amulet(i)") && monster.has(Monster.UNDEAD))
			{
				dmgBonus += 150;
			}
			else if (wearing("Amulet of avarice") && monster.getName().startsWith("Revenant"))
			{
				dmgBonus += 200;
			}
			else if (imbuedBlackMask() && slayerBonus())
			{
				mask = true;
			}
			dmgBonus += player.getMagicDamagePrayer();

			max = max + max * dmgBonus / 1000;
			if (mask)
			{
				max = max * 23 / 20;
			}
			if (weaponName.equals("Dawnbringer"))
			{
				max = Math.max(2, max / 2);
			}
			if (monster.has(Monster.DRAGON))
			{
				if (wearing("Dragon hunter lance"))
				{
					max = max * 6 / 5;
				}
				else if (wearing("Dragon hunter wand"))
				{
					max = max * 7 / 5;
				}
				else if (wearing("Dragon hunter crossbow"))
				{
					max = max * 5 / 4;
				}
			}
			String element = spellElement();
			if (!element.isEmpty() && element.equals(monster.getWeaknessElement()))
			{
				max += baseMax * monster.getWeaknessSeverity() / 100;
			}
			GearItem shield = gear[SHIELD];
			if (shield != null && "Charged".equals(shield.getVersion())
				&& ((shield.getName().equals("Tome of Fire") && element.equals("fire"))
				|| (shield.getName().equals("Tome of Water") && element.equals("water"))
				|| (shield.getName().equals("Tome of Earth") && element.equals("earth"))))
			{
				max = max * 11 / 10;
			}
			return new int[]{0, max};
		}

		private boolean hasMatchingElementalAmulet()
		{
			String element = spellElement();
			if (element.isEmpty())
			{
				return false;
			}
			if (wearing("Elemental amulet"))
			{
				return true;
			}
			switch (element)
			{
				case "air":
					return wearing("Amulet of air");
				case "water":
					return wearing("Amulet of water");
				case "earth":
					return wearing("Amulet of earth");
				default:
					return wearing("Amulet of fire");
			}
		}

		private int[] minMax()
		{
			int[] mm;
			switch (type)
			{
				case RANGED:
					mm = rangedMaxHit();
					break;
				case MAGIC:
					mm = magicMaxHit();
					break;
				default:
					mm = meleeMaxHit();
					break;
			}
			if (mm[0] > mm[1])
			{
				mm[1] = mm[0];
			}
			mm[0] = Math.max(0, mm[0]);
			mm[1] = Math.max(0, mm[1]);
			return mm;
		}

		// ---- speed ----

		private int attackSpeed()
		{
			int speed = weapon == null || weapon.getSpeed() <= 0 ? WeaponRules.DEFAULT_ATTACK_SPEED : weapon.getSpeed();
			if (type == AttackType.RANGED && stance == Stance.RAPID)
			{
				speed -= 1;
			}
			else if (stance == Stance.AUTOCAST)
			{
				if (weaponName.equals("Harmonised Nightmare staff") && spell != null && !spell.isAncient())
				{
					speed = 4;
				}
				else if (weaponName.equals("Twinflame staff"))
				{
					speed = 6;
				}
				else
				{
					speed = 5;
				}
			}
			return Math.max(1, speed);
		}

		// ---- distribution (getAttackerDist + applyNpcTransforms) ----

		/**
		 * The hitsplats of one attack, each with the probability that the hitsplat happens at all.
		 */
		private final List<HitDist> hits = new ArrayList<>(3);
		private final List<Double> hitWeights = new ArrayList<>(3);

		private void addHit(HitDist d, double weight)
		{
			hits.add(d);
			hitWeights.add(weight);
		}

		private void replaceHits(HitDist d)
		{
			hits.clear();
			hitWeights.clear();
			addHit(d, 1);
		}

		private void transformAll(HitDist.Transform t, boolean transformInaccurate)
		{
			for (int i = 0; i < hits.size(); i++)
			{
				hits.set(i, hits.get(i).transform(t, transformInaccurate));
			}
		}

		private void buildDistribution(double acc, int min, int max, int defRoll, int atkRoll)
		{
			HitDist standard = HitDist.linear(acc, min, max);
			addHit(standard, 1);

			if (type == AttackType.RANGED && wearing("Tonalztics of Ralos") && "Charged".equals(weapon.getVersion()))
			{
				addHit(standard, 1);
			}
			if (type == AttackType.RANGED && wearing("Dark bow"))
			{
				addHit(standard, 1);
			}
			if (melee() && veracs())
			{
				replaceHits(standard.mix(HitDist.linear(1.0, 1, max + 1), 0.75));
			}
			if (type == AttackType.RANGED && karils())
			{
				transformAll((dmg, a, p, out) ->
				{
					out.add(dmg, a, p * 0.75);
					out.add(dmg + dmg / 2, a, p * 0.25);
				}, false);
			}
			if (melee() && scythe())
			{
				hits.clear();
				hitWeights.clear();
				for (int i = 0; i < Math.min(Math.max(monster.getSize(), 1), 3); i++)
				{
					addHit(HitDist.linear(acc, min, Math.max(min, max >> i)), 1);
				}
			}
			if (melee() && wearing("Dual macuahuitl"))
			{
				int firstMax = max / 2;
				int secondMax = max - firstMax;
				hits.clear();
				hitWeights.clear();
				addHit(HitDist.linear(acc, min, Math.max(min, firstMax)), 1);
				// the second hit only happens when the first is accurate
				addHit(HitDist.linear(acc, min, Math.max(min, secondMax)), acc);
			}
			if (melee() && twoHitWeapon())
			{
				int firstMax = max / 2;
				hits.clear();
				hitWeights.clear();
				addHit(HitDist.linear(acc, min, Math.max(min, firstMax)), 1);
				addHit(HitDist.linear(acc, min, Math.max(min, max - firstMax)), 1);
			}
			if (melee() && keris() && monster.has(Monster.KALPHITE))
			{
				replaceHits(standard.mix(standard.scaleDamage(3, 1), 50.0 / 51.0));
			}
			if (wearingAny("Sanguinesti staff", "Holy sanguinesti staff"))
			{
				transformAll((dmg, a, p, out) ->
				{
					out.add(dmg, a, p * 0.8);
					out.add(dmg + 8, a, p * 0.2);
				}, false);
			}
			if (type == AttackType.MAGIC && ahrims())
			{
				transformAll((dmg, a, p, out) ->
				{
					out.add(dmg, a, p * 0.75);
					out.add(dmg * 13 / 10, a, p * 0.25);
				}, true);
			}
			if (melee() && berserkerNecklace() && tzhaarWeapon())
			{
				transformAll((dmg, a, p, out) -> out.add(dmg * 6 / 5, a, p), true);
			}
			if (isVampyre())
			{
				int[] f = vampyreFactor();
				if (f != null)
				{
					transformAll((dmg, a, p, out) -> out.add(dmg * f[0] / f[1], a, p), true);
				}
			}

			boolean crossbow = type == AttackType.RANGED && "Crossbow".equals(weapon.getCategory()) && ammoIncluded;
			String ammo = gear[AMMO] == null ? "" : gear[AMMO].getName();
			boolean zcb = wearing("Zaryte crossbow");
			double kandarin = player.isKandarinHardDiary() ? 1.1 : 1.0;
			if (crossbow)
			{
				if (ammo.equals("Opal bolts (e)") || ammo.equals("Opal dragon bolts (e)"))
				{
					boltBonus(0.05 * kandarin, player.getRanged() / (zcb ? 9 : 10), false);
				}
				else if (ammo.equals("Pearl bolts (e)") || ammo.equals("Pearl dragon bolts (e)"))
				{
					int divisor = monster.has(Monster.FIERY) ? 15 : 20;
					boltBonus(0.06 * kandarin, player.getRanged() / (zcb ? divisor - 2 : divisor), false);
				}
				else if (ammo.equals("Diamond bolts (e)") || ammo.equals("Diamond dragon bolts (e)"))
				{
					boltReplace(0.1 * kandarin, max * (zcb ? 126 : 115) / 100, false);
				}
				else if ((ammo.equals("Dragonstone bolts (e)") || ammo.equals("Dragonstone dragon bolts (e)"))
					&& !monster.has(Monster.FIERY) && !monster.has(Monster.DRAGON))
				{
					boltBonus(0.06 * kandarin, player.getRanged() * 2 / (zcb ? 9 : 10), true);
				}
				else if ((ammo.equals("Onyx bolts (e)") || ammo.equals("Onyx dragon bolts (e)")) && !monster.has(Monster.UNDEAD))
				{
					boltReplace(0.11 * kandarin, max * (zcb ? 132 : 120) / 100, true);
				}
			}

			if (type == AttackType.RANGED && ammoIncluded && ammo.contains("Seeking"))
			{
				transformAll((dmg, a, p, out) -> out.add(Math.max(dmg, 3), a, p), false);
			}

			// raise accurate zeros to 1
			transformAll((dmg, a, p, out) -> out.add(Math.max(dmg, 1), a, p), false);

			if (type == AttackType.MAGIC && spell != null && !spell.isAncient() && wearing("Twinflame staff")
				&& (spell.getName().contains("Bolt") || spell.getName().contains("Blast") || spell.getName().contains("Wave")))
			{
				transformAll((dmg, a, p, out) -> out.add(dmg + dmg * 4 / 10, a, p), true);
			}

			if (monster.getName().equals("Corporeal Beast") && !corpbane())
			{
				transformAll((dmg, a, p, out) -> out.add(dmg / 2, a, p), true);
			}

			if (crossbow && (ammo.equals("Ruby bolts (e)") || ammo.equals("Ruby dragon bolts (e)")) && player.getHitpoints() >= 10)
			{
				int effect = Math.min(zcb ? 110 : 100, monster.getHitpoints() * (zcb ? 22 : 20) / 100);
				double chance = 0.06 * kandarin;
				transformAll((dmg, a, p, out) ->
				{
					out.add(effect, true, p * chance);
					out.add(dmg, a, p * (1 - chance));
				}, true);
			}

			if (type == AttackType.MAGIC && !skipBrimstone && wearing("Brimstone ring"))
			{
				// 25% of attacks roll against 90% of the target's defence
				double effectAcc = hitChance(atkRoll, defRoll * 9 / 10);
				Attack effect = new Attack(gear, names, type, stance, spell);
				effect.buildDistributionNoBrimstone(effectAcc, min, max);
				for (int i = 0; i < hits.size() && i < effect.hits.size(); i++)
				{
					hits.set(i, hits.get(i).mix(effect.hits.get(i), 0.75));
				}
			}

			applyNpcTransforms();
		}

		private boolean skipBrimstone;

		private void buildDistributionNoBrimstone(double acc, int min, int max)
		{
			skipBrimstone = true;
			buildDistribution(acc, min, max, 0, 0);
		}

		private void boltBonus(double chance, int bonus, boolean accurateOnly)
		{
			transformAll((dmg, a, p, out) ->
			{
				if (!a && accurateOnly)
				{
					out.add(dmg, false, p);
					return;
				}
				out.add(dmg + bonus, a, p * chance);
				out.add(dmg, a, p * (1 - chance));
			}, true);
		}

		private void boltReplace(double chance, int effectMax, boolean accurateOnly)
		{
			double each = 1.0 / (effectMax + 1);
			transformAll((dmg, a, p, out) ->
			{
				if (!a && accurateOnly)
				{
					out.add(dmg, false, p);
					return;
				}
				for (int i = 0; i <= effectMax; i++)
				{
					out.add(i, true, p * chance * each);
				}
				out.add(dmg, a, p * (1 - chance));
			}, true);
		}

		private int[] vampyreFactor()
		{
			boolean efaritay = wearing("Efaritay's aid");
			int[] f;
			if (wearingAny("Blisterwood flail", "Hallowed flail", "Blisterwood stake"))
			{
				f = new int[]{125, 100};
			}
			else if (wearing("Sunspear"))
			{
				f = new int[]{150, 100};
			}
			else if (wearing("Blisterwood sickle"))
			{
				f = new int[]{23, 20};
			}
			else if (wearing("Ivandis flail"))
			{
				f = new int[]{6, 5};
			}
			else if (wearing("Rod of Ivandis") && !monster.has(Monster.VAMPYRE_3))
			{
				f = new int[]{11, 10};
			}
			else if (silverWeapon() && monster.has(Monster.VAMPYRE_1))
			{
				f = new int[]{11, 10};
			}
			else
			{
				return null;
			}
			if (efaritay)
			{
				f = new int[]{f[0] * 11, f[1] * 10};
			}
			return f;
		}

		private boolean immune()
		{
			if (monster.has(Monster.FLYING) && melee())
			{
				String category = weapon == null ? "" : weapon.getCategory();
				if (!category.equals("Polearm") && !category.equals("Salamander"))
				{
					return true;
				}
			}
			if (monster.has(Monster.VAMPYRE_3) && !vampyrebane(false))
			{
				return true;
			}
			if (monster.has(Monster.VAMPYRE_2) && !vampyrebane(true) && !wearing("Efaritay's aid") && !silverWeapon())
			{
				return true;
			}
			if (monster.has(Monster.LEAFY) && !leafBladed())
			{
				return true;
			}
			return !monster.has(Monster.RAT) && ratBoneWeapon();
		}

		private void applyNpcTransforms()
		{
			if (immune())
			{
				hits.clear();
				hitWeights.clear();
				return;
			}
			if ((monster.getName().equals("Kraken") || monster.getName().equals("Cave kraken")) && type == AttackType.RANGED)
			{
				transformAll((dmg, a, p, out) -> out.add(dmg / 7, a, p), true);
			}
			if (monster.getName().equals("Tormented Demon") && !usingDemonbane() && !usingAbyssal())
			{
				transformAll((dmg, a, p, out) ->
				{
					int reduced = dmg * 4 / 5;
					out.add(dmg >= 1 ? Math.max(1, reduced) : Math.max(dmg, reduced), a, p);
				}, true);
			}
			if (monster.has(Monster.VAMPYRE_2))
			{
				if (!vampyrebane(true) && wearing("Efaritay's aid"))
				{
					transformAll((dmg, a, p, out) -> out.add(dmg / 2, a, p), true);
				}
				else if (silverWeapon())
				{
					transformAll((dmg, a, p, out) -> out.add(Math.min(dmg, 10), a, p), true);
				}
			}
			int flatArmour = monster.getFlatArmour();
			if (flatArmour != 0 && type != AttackType.MAGIC)
			{
				transformAll((dmg, a, p, out) -> out.add(Math.max(0, dmg - flatArmour), a, p), false);
			}
		}

		CalcResult result()
		{
			if (type == AttackType.RANGED)
			{
				if (weapon == null)
				{
					return CalcResult.NONE;
				}
				WeaponRules.AmmoKind kind = WeaponRules.ammoKind(weapon);
				if (kind == WeaponRules.AmmoKind.UNSUPPORTED || (kind != WeaponRules.AmmoKind.NONE && !ammoIncluded))
				{
					return CalcResult.NONE;
				}
			}
			int atk = attackRoll();
			int def = npcDefenceRoll();
			double acc = hitChance(atk, def);
			int[] mm = minMax();
			if (mm[1] == 0)
			{
				return CalcResult.NONE;
			}
			buildDistribution(acc, mm[0], mm[1], def, atk);
			double expected = 0;
			int maxHit = 0;
			for (int i = 0; i < hits.size(); i++)
			{
				expected += hits.get(i).expected() * hitWeights.get(i);
				maxHit += hits.get(i).max();
			}
			int speed = attackSpeed();
			double dps = expected / speed / SECONDS_PER_TICK;
			return new CalcResult(dps, maxHit, acc, speed, stance, spell);
		}
	}

	static int tbowScaling(int current, int magic, boolean accuracy)
	{
		int factor = accuracy ? 10 : 14;
		int base = accuracy ? 140 : 250;
		int t2 = (3 * magic - factor) / 100;
		int t3 = (int) Math.pow((3 * magic / 10) - (10 * factor), 2) / 100;
		int bonus = Math.max(0, Math.min(base, base + t2 - t3));
		return current * bonus / 100;
	}

	static double normalAccuracy(int atk, int def)
	{
		if (atk < 0)
		{
			atk = Math.min(0, atk + 2);
		}
		if (def < 0)
		{
			def = Math.min(0, def + 2);
		}
		if (atk >= 0 && def >= 0)
		{
			return stdRoll(atk, def);
		}
		if (atk >= 0)
		{
			return 1 - 1.0 / (-def + 1) / (atk + 1);
		}
		if (def >= 0)
		{
			return 0;
		}
		return stdRoll(-def, -atk);
	}

	private static double stdRoll(double attack, double defence)
	{
		return attack > defence
			? 1 - (defence + 2) / (2 * (attack + 1))
			: attack / (2 * (defence + 1));
	}

	static double fangAccuracy(int atk, int def)
	{
		if (atk < 0)
		{
			atk = Math.min(0, atk + 2);
		}
		if (def < 0)
		{
			def = Math.min(0, def + 2);
		}
		if (atk >= 0 && def >= 0)
		{
			double a = atk;
			double d = def;
			return a > d
				? 1 - (d + 2) * (2 * d + 3) / (a + 1) / (a + 1) / 6
				: a * (4 * a + 5) / 6 / (a + 1) / (d + 1);
		}
		if (atk >= 0)
		{
			return 1 - 1.0 / (-def + 1) / (atk + 1);
		}
		if (def >= 0)
		{
			return 0;
		}
		double a = -def;
		double d = -atk;
		return a < d
			? a * (d * 6 - 2 * a + 5) / 6 / (d + 1) / (d + 1)
			: 1 - (d + 2) * (2 * d + 3) / 6 / (d + 1) / (a + 1);
	}
}
