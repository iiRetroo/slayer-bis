package com.slayerbis.calc;

import com.slayerbis.data.AttackType;
import com.slayerbis.data.EquipmentSlot;
import com.slayerbis.data.GearItem;
import com.slayerbis.data.Monster;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.Getter;

/**
 * Finds the highest-DPS loadout from owned items for each attack type.
 * <ol>
 * <li>Items that are beaten or tied on every stat relevant to the attack type by another item in the
 * same slot are removed, unless the item has a special effect that could apply.</li>
 * <li>Complete armour sets are added as single candidates covering several slots.</li>
 * <li>Every remaining combination is evaluated with the DPS calculator, two-handed weapons leaving
 * the shield slot empty. Items without special effects only change DPS through their summed stats,
 * and DPS never falls when those sums rise, so for those items only the combinations whose summed
 * stats are not beaten by another combination are evaluated. This gives the same answer as
 * evaluating them all, in a fraction of the time.</li>
 * </ol>
 */
public final class Optimizer
{
	/**
	 * Hard stop so a pathological bank cannot keep the worker busy forever.
	 */
	static final long MAX_EVALUATIONS_PER_STYLE = 20_000_000L;

	/** How many best-set-with-another-weapon alternatives to report per style. */
	static final int MAX_ALTERNATIVES = 3;

	private static final int SLOTS = EquipmentSlot.values().length;
	private static final int WEAPON = EquipmentSlot.WEAPON.ordinal();
	private static final int SHIELD = EquipmentSlot.SHIELD.ordinal();
	private static final int AMMO = EquipmentSlot.AMMO.ordinal();

	private static final EquipmentSlot[] ARMOUR_SLOTS = {
		EquipmentSlot.HEAD, EquipmentSlot.CAPE, EquipmentSlot.NECK, EquipmentSlot.BODY,
		EquipmentSlot.LEGS, EquipmentSlot.HANDS, EquipmentSlot.FEET, EquipmentSlot.RING,
	};

	/** Weapons whose damage is not explained by their stats alone. */
	private static final Set<String> SPECIAL_WEAPONS = ImmutableSet.of(
		"Osmumten's fang", "Osmumten's fang (or)", "Dual macuahuitl", "Torag's hammers", "Sulphur blades",
		"Glacial temotli", "Earthbound tecpatl", "Verac's flail", "Sanguinesti staff", "Holy sanguinesti staff",
		"Twisted bow", "Dark bow", "Tonalztics of Ralos", "Colossal blade", "Crystal bow", "Twinflame staff",
		"Smoke battlestaff", "Mystic smoke staff", "Tzhaar-ket-em", "Tzhaar-ket-om", "Tzhaar-ket-om (t)",
		"Toktz-xil-ak", "Toktz-xil-ek", "Toktz-mej-tal", "Eclipse atlatl", "Hunter's spear", "Karil's crossbow",
		"Ahrim's staff", "Zaryte crossbow", "Keris partisan of amascut", "Dawnbringer", "Harmonised Nightmare staff"
	);

	private static final Set<String> MAGIC_EFFECTS = ImmutableSet.of(
		"Chaos gauntlets", "Elemental amulet", "Amulet of air", "Amulet of water", "Amulet of earth", "Amulet of fire",
		"Tome of Fire", "Tome of Water", "Tome of Earth", "Brimstone ring", "Confliction gauntlets"
	);

	private static final Set<String> ANCIENT_MAGIC_EFFECTS = ImmutableSet.of(
		"Virtus mask", "Virtus robe top", "Virtus robe bottom"
	);

	private static final Set<String> RANGED_EFFECTS = ImmutableSet.of(
		"Dizana's quiver", "Blessed Dizana's quiver", "Dizana's max cape", "Crystal helm", "Crystal body", "Crystal legs"
	);

	private static final Set<String> MELEE_EFFECTS = ImmutableSet.of(
		"Berserker necklace", "Berserker necklace (or)"
	);

	private static final Set<String> CRUSH_EFFECTS = ImmutableSet.of(
		"Inquisitor's great helm", "Inquisitor's hauberk", "Inquisitor's plateskirt"
	);

	private final DpsCalculator calculator;
	/** For the search in progress: the least the set may weigh in grams, or 0. */
	private int minWeight;
	private final PlayerSetup player;
	private final Monster monster;
	private final AtomicBoolean cancelled;

	public Optimizer(PlayerSetup player, Monster monster)
	{
		this(player, monster, new AtomicBoolean());
	}

	/**
	 * @param cancelled set to true from another thread to stop the search early
	 */
	public Optimizer(PlayerSetup player, Monster monster, AtomicBoolean cancelled)
	{
		this.player = player;
		this.monster = monster;
		this.cancelled = cancelled;
		this.calculator = new DpsCalculator(player, monster);
	}

	/**
	 * An item the player owns, with the stats it is evaluated with.
	 */
	@Getter
	public static final class Candidate
	{
		private final GearItem item;
		private final int ownedItemId;

		public Candidate(GearItem item, int ownedItemId)
		{
			this.item = item;
			this.ownedItemId = ownedItemId;
		}
	}

	/**
	 * The ranged set with each owned ammo the weapon can fire swapped in, best first, so a cheaper
	 * ammo's DPS can be compared. Empty when the weapon takes no ammo.
	 */
	public List<StyleResult.Alternative> ammoOptions(StyleResult result, Collection<Candidate> owned)
	{
		if (result == null || !result.isValid() || result.getType() != AttackType.RANGED)
		{
			return Collections.emptyList();
		}
		Candidate weapon = result.getGear()[WEAPON];
		WeaponRules.AmmoKind kind = weapon == null ? WeaponRules.AmmoKind.NONE : WeaponRules.ammoKind(weapon.item);
		if (kind == WeaponRules.AmmoKind.NONE || kind == WeaponRules.AmmoKind.UNSUPPORTED)
		{
			return Collections.emptyList();
		}
		List<StyleResult.Alternative> out = new ArrayList<>();
		Set<Integer> seen = new HashSet<>();
		for (Candidate c : owned)
		{
			if (c.item.getSlot() != EquipmentSlot.AMMO || !WeaponRules.ammoUsable(weapon.item, c.item)
				|| !seen.add(c.item.getId()))
			{
				continue;
			}
			Candidate[] gear = result.getGear().clone();
			gear[AMMO] = c;
			GearItem[] items = new GearItem[SLOTS];
			for (int i = 0; i < SLOTS; i++)
			{
				items[i] = gear[i] == null ? null : gear[i].item;
			}
			CalcResult r = calculator.best(items, AttackType.RANGED);
			if (r.getDps() > 0)
			{
				out.add(new StyleResult.Alternative(r, gear));
			}
		}
		out.sort((a, b) -> Double.compare(b.getResult().getDps(), a.getResult().getDps()));
		return out;
	}

	public StyleResult optimise(Collection<Candidate> owned, AttackType type)
	{
		return optimise(owned, type, Collections.emptyMap());
	}

	/**
	 * @param required slots that must hold one of the named items (e.g. a mirror shield for
	 *                 basilisks). Only pass requirements the player can meet.
	 */
	public StyleResult optimise(Collection<Candidate> owned, AttackType type, Map<EquipmentSlot, Set<String>> required)
	{
		return optimise(owned, type, required, 0);
	}

	/**
	 * @param minWeight the least the worn set may weigh, in grams (gryphons knock lighter players
	 *                  back); 0 for no limit. Heavier items are then never pruned for being weaker.
	 */
	public StyleResult optimise(Collection<Candidate> owned, AttackType type, Map<EquipmentSlot, Set<String>> required,
		int minWeight)
	{
		this.minWeight = minWeight;
		Map<EquipmentSlot, List<Candidate>> bySlot = new EnumMap<>(EquipmentSlot.class);
		for (EquipmentSlot s : EquipmentSlot.values())
		{
			bySlot.put(s, new ArrayList<>());
		}
		Map<String, Candidate> byName = new HashMap<>();
		for (Candidate c : owned)
		{
			bySlot.get(c.item.getSlot()).add(c);
			byName.putIfAbsent(c.item.getName(), c);
		}

		// required task gear: the slot may only hold an accepted item, so nothing else competes for it
		boolean[] requiredSlot = new boolean[SLOTS];
		for (Map.Entry<EquipmentSlot, Set<String>> r : required.entrySet())
		{
			List<Candidate> accepted = new ArrayList<>();
			for (Candidate c : bySlot.get(r.getKey()))
			{
				if (r.getValue().contains(c.item.getName()))
				{
					accepted.add(c);
				}
			}
			if (!accepted.isEmpty())
			{
				bySlot.put(r.getKey(), accepted);
				requiredSlot[r.getKey().ordinal()] = true;
			}
		}

		// 1. remove outclassed items, slot by slot
		List<List<Candidate>> pruned = new ArrayList<>(SLOTS);
		for (EquipmentSlot s : EquipmentSlot.values())
		{
			pruned.add(Collections.emptyList());
		}
		for (EquipmentSlot s : ARMOUR_SLOTS)
		{
			pruned.set(s.ordinal(), requiredSlot[s.ordinal()] ? bySlot.get(s) : prune(bySlot.get(s), type, true));
		}
		pruned.set(SHIELD, requiredSlot[SHIELD] ? bySlot.get(EquipmentSlot.SHIELD)
			: prune(bySlot.get(EquipmentSlot.SHIELD), type, true));
		if (type != AttackType.RANGED)
		{
			pruned.set(AMMO, prune(bySlot.get(EquipmentSlot.AMMO), type, true));
		}
		List<Candidate> weapons = pruneWeapons(bySlot.get(EquipmentSlot.WEAPON), type);
		if (requiredSlot[SHIELD])
		{
			// a required shield rules out two-handed weapons
			weapons.removeIf(w -> w != null && w.item.isTwoHanded());
		}

		int after = 0;
		for (List<Candidate> l : pruned)
		{
			for (Candidate c : l)
			{
				after += c == null ? 0 : 1;
			}
		}
		for (Candidate c : weapons)
		{
			after += c == null ? 0 : 1;
		}

		// 2. complete sets owned for this type
		List<Candidate[]> sets = new ArrayList<>();
		List<String> setNames = new ArrayList<>();
		for (GearSets set : GearSets.ALL)
		{
			if (!set.getTypes().contains(type) || (set.isAncientsOnly() && !player.isAncientSpellbook()))
			{
				continue;
			}
			Candidate[] fixed = new Candidate[SLOTS];
			boolean complete = true;
			for (Map.Entry<EquipmentSlot, List<String>> piece : set.getPieces().entrySet())
			{
				Candidate found = null;
				for (String n : piece.getValue())
				{
					found = byName.get(n);
					if (found != null)
					{
						break;
					}
				}
				if (found == null)
				{
					complete = false;
					break;
				}
				if (requiredSlot[piece.getKey().ordinal()] && !required.get(piece.getKey()).contains(found.item.getName()))
				{
					// this set would replace required task gear
					complete = false;
					break;
				}
				fixed[piece.getKey().ordinal()] = found;
			}
			if (complete)
			{
				sets.add(fixed);
				setNames.add(set.getName());
			}
		}

		// 3. search: no set, then every combination of sets that do not share a slot
		Search search = new Search(type, pruned, weapons, bySlot.get(EquipmentSlot.AMMO), requiredSlot);
		List<List<Integer>> combos = new ArrayList<>();
		combos.add(new ArrayList<>());
		for (int i = 0; i < sets.size(); i++)
		{
			int n = combos.size();
			for (int j = 0; j < n; j++)
			{
				List<Integer> base = combos.get(j);
				if (disjoint(sets, base, i))
				{
					List<Integer> withSet = new ArrayList<>(base);
					withSet.add(i);
					combos.add(withSet);
				}
			}
		}
		for (List<Integer> combo : combos)
		{
			Candidate[] fixed = new Candidate[SLOTS];
			for (int idx : combo)
			{
				Candidate[] set = sets.get(idx);
				for (int s = 0; s < SLOTS; s++)
				{
					if (set[s] != null)
					{
						fixed[s] = set[s];
					}
				}
			}
			search.run(fixed);
			if (search.stoppedEarly)
			{
				break;
			}
		}

		List<StyleResult.Alternative> alternatives = new ArrayList<>();
		for (Map.Entry<Integer, StyleResult.Alternative> e : search.bestByWeapon.entrySet())
		{
			Candidate bestWeapon = search.bestGear == null ? null : search.bestGear[WEAPON];
			int bestKey = bestWeapon == null ? -1 : bestWeapon.item.getId();
			if (e.getKey() != bestKey)
			{
				alternatives.add(e.getValue());
			}
		}
		alternatives.sort((a, b) -> Double.compare(b.getResult().getDps(), a.getResult().getDps()));
		if (alternatives.size() > MAX_ALTERNATIVES)
		{
			alternatives = new ArrayList<>(alternatives.subList(0, MAX_ALTERNATIVES));
		}

		return new StyleResult(type, search.bestResult, search.bestGear, search.evaluations, owned.size(), after,
			setNames, search.stoppedEarly, alternatives);
	}

	private static boolean disjoint(List<Candidate[]> sets, List<Integer> chosen, int add)
	{
		Candidate[] a = sets.get(add);
		for (int idx : chosen)
		{
			Candidate[] b = sets.get(idx);
			for (int s = 0; s < SLOTS; s++)
			{
				if (a[s] != null && b[s] != null)
				{
					return false;
				}
			}
		}
		return true;
	}

	// ---- pruning ----

	private static int damageStat(GearItem g, AttackType type)
	{
		switch (type)
		{
			case RANGED:
				return g.getRangedStrength();
			case MAGIC:
				return g.getMagicDamage();
			default:
				return g.getStrength();
		}
	}

	private static int offence(Candidate c, AttackType type)
	{
		return c == null ? 0 : c.item.offensive(type);
	}

	private static int damage(Candidate c, AttackType type)
	{
		return c == null ? 0 : damageStat(c.item, type);
	}

	private static int weight(Candidate c)
	{
		return c == null ? 0 : c.item.getWeight();
	}

	/**
	 * Total worn weight of a set, in grams.
	 */
	public static int weight(Candidate[] gear)
	{
		int total = 0;
		for (Candidate c : gear)
		{
			total += weight(c);
		}
		return total;
	}

	/**
	 * Keeps the items not beaten on every relevant stat by another item in the same slot (or by
	 * leaving the slot empty). A {@code null} entry in the result means "leave the slot empty".
	 */
	private List<Candidate> prune(List<Candidate> items, AttackType type, boolean allowEmpty)
	{
		List<Candidate> out = new ArrayList<>();
		List<Candidate> all = new ArrayList<>(items);
		if (allowEmpty)
		{
			all.add(null);
		}
		for (int i = 0; i < all.size(); i++)
		{
			Candidate c = all.get(i);
			if (c != null && isSpecial(c.item, type))
			{
				out.add(c);
				continue;
			}
			boolean dominated = false;
			for (int j = 0; j < all.size() && !dominated; j++)
			{
				dominated = j != i && dominates(all.get(j), c, type, j < i, minWeight > 0);
			}
			if (!dominated)
			{
				out.add(c);
			}
		}
		return out;
	}

	/**
	 * Whether a is at least as good as b on every relevant stat. Exact ties are broken so that
	 * exactly one survives, preferring the empty slot and then the earlier item.
	 */
	private static boolean dominates(Candidate a, Candidate b, AttackType type, boolean aFirst, boolean byWeight)
	{
		int aOff = offence(a, type);
		int bOff = offence(b, type);
		int aDmg = damage(a, type);
		int bDmg = damage(b, type);
		int aW = byWeight ? weight(a) : 0;
		int bW = byWeight ? weight(b) : 0;
		if (aOff < bOff || aDmg < bDmg || aW < bW)
		{
			return false;
		}
		if (aOff > bOff || aDmg > bDmg || aW > bW)
		{
			return true;
		}
		return a == null || (b != null && aFirst);
	}

	private List<Candidate> pruneWeapons(List<Candidate> items, AttackType type)
	{
		List<Candidate> usable = new ArrayList<>();
		for (Candidate c : items)
		{
			if (WeaponRules.attackTypes(c.item).contains(type))
			{
				usable.add(c);
			}
		}
		if (type == AttackType.CRUSH)
		{
			usable.add(null); // unarmed
		}

		List<Candidate> out = new ArrayList<>();
		for (int i = 0; i < usable.size(); i++)
		{
			Candidate c = usable.get(i);
			if (c == null || isSpecial(c.item, type) || c.item.getCategory().startsWith("Powered"))
			{
				out.add(c);
				continue;
			}
			boolean dominated = false;
			for (int j = 0; j < usable.size() && !dominated; j++)
			{
				Candidate o = usable.get(j);
				if (o == null || j == i || !sameWeaponGroup(o.item, c.item, type) || o.item.getSpeed() > c.item.getSpeed())
				{
					continue;
				}
				// a strictly faster weapon wins stat ties outright
				dominated = dominates(o, c, type, o.item.getSpeed() < c.item.getSpeed() || j < i, minWeight > 0);
			}
			if (!dominated)
			{
				out.add(c);
			}
		}
		return out;
	}

	private static boolean sameWeaponGroup(GearItem a, GearItem b, AttackType type)
	{
		if (!a.getCategory().equals(b.getCategory()) || a.isTwoHanded() != b.isTwoHanded())
		{
			return false;
		}
		if (type == AttackType.RANGED && !WeaponRules.ammoKey(a).equals(WeaponRules.ammoKey(b)))
		{
			return false;
		}
		if (WeaponRules.isBlowpipe(a.getName()) != WeaponRules.isBlowpipe(b.getName()))
		{
			return false;
		}
		return type != AttackType.MAGIC
			|| WeaponRules.canAutocastAncients(a.getName()) == WeaponRules.canAutocastAncients(b.getName());
	}

	/**
	 * Whether an item's effect could matter for this attack type against this monster, in which
	 * case it is never removed by the stat comparison.
	 */
	boolean isSpecial(GearItem item, AttackType type)
	{
		String n = item.getName();
		boolean weapon = item.getSlot() == EquipmentSlot.WEAPON;
		if (weapon && (SPECIAL_WEAPONS.contains(n) || n.contains("Bow of Faerdhinen") || n.toLowerCase().contains("of vitur")
			|| n.contains("Keris")))
		{
			return true;
		}
		if (type == AttackType.MAGIC && (MAGIC_EFFECTS.contains(n) || (player.isAncientSpellbook() && ANCIENT_MAGIC_EFFECTS.contains(n))))
		{
			return true;
		}
		if (type == AttackType.RANGED && (RANGED_EFFECTS.contains(n) || (n.endsWith("(e)") && n.contains("bolts")) || n.startsWith("Seeking")))
		{
			return true;
		}
		if (type.isMelee() && MELEE_EFFECTS.contains(n))
		{
			return true;
		}
		if (type == AttackType.CRUSH && CRUSH_EFFECTS.contains(n))
		{
			return true;
		}
		boolean slayer = monster.isSlayerMonster() && player.isOnSlayerTask();
		if (slayer && (n.equals("Black mask (i)") || n.equals("Slayer helmet (i)") || n.equals("V's helm")))
		{
			return true;
		}
		if (slayer && type.isMelee() && (n.equals("Black mask") || n.equals("Slayer helmet")))
		{
			return true;
		}
		if (monster.has(Monster.UNDEAD) && (n.equals("Salve amulet(i)") || n.equals("Salve amulet(ei)")
			|| (type.isMelee() && (n.equals("Salve amulet") || n.equals("Salve amulet (e)")))))
		{
			return true;
		}
		if (monster.getName().startsWith("Revenant") && n.equals("Amulet of avarice"))
		{
			return true;
		}
		if (monster.has(Monster.DRAGON) && n.startsWith("Dragon hunter"))
		{
			return true;
		}
		if (monster.has(Monster.DEMON) && (n.equals("Arclight") || n.equals("Emberlight") || n.equals("Silverlight")
			|| n.equals("Darklight") || n.equals("Burning claws") || n.equals("Scorching bow")))
		{
			return true;
		}
		if (monster.has(Monster.LEAFY) && (n.startsWith("Leaf-bladed") || n.contains("Broad") || n.contains("broad")))
		{
			return true;
		}
		if (monster.has(Monster.GOLEM) && (n.equals("Granite hammer") || n.equals("Barronite mace")))
		{
			return true;
		}
		if (monster.has(Monster.RAT) && weapon && n.startsWith("Bone "))
		{
			return true;
		}
		if ((monster.has(Monster.VAMPYRE_1) || monster.has(Monster.VAMPYRE_2) || monster.has(Monster.VAMPYRE_3))
			&& (n.contains("Blisterwood") || n.contains("Ivandis") || n.contains("flail") || n.contains("sickle")
			|| n.equals("Sunspear") || n.equals("Efaritay's aid") || n.contains("Silver") || n.equals("Wolfbane")
			|| n.equals("Blessed axe")))
		{
			return true;
		}
		if (monster.has(Monster.FLYING) && type.isMelee() && weapon)
		{
			return true;
		}
		return monster.getName().equals("Corporeal Beast") && weapon;
	}

	// ---- search ----

	/**
	 * Summed stats of a choice of plain (no special effect) items for a group of slots.
	 */
	private static final class StatPoint
	{
		final int offence;
		final int damage;
		final int weight;
		final Candidate[] picks;

		StatPoint(int offence, int damage, int weight, Candidate[] picks)
		{
			this.offence = offence;
			this.damage = damage;
			this.weight = weight;
			this.picks = picks;
		}
	}

	private final class Search
	{
		private final AttackType type;
		private final List<List<Candidate>> pruned;
		private final List<Candidate> weapons;
		private final List<Candidate> ammoAll;
		private final Map<String, List<Candidate>> ammoByKey = new HashMap<>();

		/** Per slot: the special items, and the plain items (with null for "empty"). */
		private final List<List<Candidate>> special = new ArrayList<>(SLOTS);
		private final List<List<Candidate>> plain = new ArrayList<>(SLOTS);
		/** Slots chosen freely in the search, in order. */
		private final int[] freeSlots;
		private final Map<Long, List<StatPoint>> frontierCache = new HashMap<>();

		private final GearItem[] gear = new GearItem[SLOTS];
		private final Candidate[] chosen = new Candidate[SLOTS];

		CalcResult bestResult = CalcResult.NONE;
		Candidate[] bestGear;
		long evaluations;
		boolean stoppedEarly;

		/** Best result for each weapon (item id, -1 for unarmed), so alternatives can be shown. */
		final Map<Integer, StyleResult.Alternative> bestByWeapon = new HashMap<>();

		private final boolean shieldRequired;

		Search(AttackType type, List<List<Candidate>> pruned, List<Candidate> weapons, List<Candidate> ammoAll,
			boolean[] requiredSlot)
		{
			this.shieldRequired = requiredSlot[SHIELD];
			this.type = type;
			this.pruned = pruned;
			this.weapons = weapons;
			this.ammoAll = ammoAll;

			List<Integer> free = new ArrayList<>();
			for (EquipmentSlot s : ARMOUR_SLOTS)
			{
				free.add(s.ordinal());
			}
			free.add(SHIELD);
			if (type != AttackType.RANGED)
			{
				free.add(AMMO);
			}
			freeSlots = free.stream().mapToInt(Integer::intValue).toArray();

			for (int s = 0; s < SLOTS; s++)
			{
				List<Candidate> sp = new ArrayList<>();
				List<Candidate> pl = new ArrayList<>();
				for (Candidate c : pruned.get(s))
				{
					if (c != null && (requiredSlot[s] || isSpecial(c.item, type)))
					{
						sp.add(c);
					}
					else
					{
						pl.add(c);
					}
				}
				special.add(sp);
				plain.add(pl);
			}
		}

		void run(Candidate[] fixed)
		{
			List<Candidate> weaponList = weapons;
			if (fixed[WEAPON] != null)
			{
				if (!WeaponRules.attackTypes(fixed[WEAPON].item).contains(type))
				{
					return;
				}
				weaponList = Collections.singletonList(fixed[WEAPON]);
			}

			for (Candidate w : weaponList)
			{
				boolean twoHanded = w != null && w.item.isTwoHanded();
				if (twoHanded && shieldRequired)
				{
					continue;
				}
				set(WEAPON, w);
				List<Candidate> ammo = type == AttackType.RANGED ? ammoFor(w) : Collections.singletonList(null);
				for (Candidate am : ammo)
				{
					if (type == AttackType.RANGED)
					{
						set(AMMO, am);
					}
					chooseSpecials(0, 0L, fixed, twoHanded);
					if (stoppedEarly)
					{
						return;
					}
				}
			}
		}

		/**
		 * For each free slot, either pick one of its special items or leave it to the plain-item
		 * frontier (recorded as a bit in plainMask).
		 */
		private void chooseSpecials(int i, long plainMask, Candidate[] fixed, boolean twoHanded)
		{
			if (i == freeSlots.length)
			{
				evaluateFrontier(plainMask, twoHanded);
				return;
			}
			int slot = freeSlots[i];
			if (fixed[slot] != null)
			{
				set(slot, fixed[slot]);
				chooseSpecials(i + 1, plainMask, fixed, twoHanded);
				return;
			}
			if (slot == SHIELD && twoHanded)
			{
				set(slot, null);
				chooseSpecials(i + 1, plainMask, fixed, true);
				return;
			}
			for (Candidate c : special.get(slot))
			{
				set(slot, c);
				chooseSpecials(i + 1, plainMask, fixed, twoHanded);
				if (stoppedEarly)
				{
					return;
				}
			}
			if (!plain.get(slot).isEmpty())
			{
				chooseSpecials(i + 1, plainMask | (1L << i), fixed, twoHanded);
			}
		}

		private void evaluateFrontier(long plainMask, boolean twoHanded)
		{
			for (StatPoint p : frontier(plainMask))
			{
				for (int i = 0; i < freeSlots.length; i++)
				{
					if ((plainMask & (1L << i)) != 0)
					{
						set(freeSlots[i], p.picks[freeSlots[i]]);
					}
				}
				evaluate();
				if (stoppedEarly)
				{
					return;
				}
			}
		}

		/**
		 * The combinations of plain items over the masked slots whose summed (offence, damage) are not
		 * beaten on both by another combination. Built one slot at a time and cached.
		 */
		private List<StatPoint> frontier(long plainMask)
		{
			List<StatPoint> cached = frontierCache.get(plainMask);
			if (cached != null)
			{
				return cached;
			}
			List<StatPoint> points;
			if (plainMask == 0)
			{
				points = Collections.singletonList(new StatPoint(0, 0, 0, new Candidate[SLOTS]));
			}
			else
			{
				int top = 63 - Long.numberOfLeadingZeros(plainMask);
				List<StatPoint> rest = frontier(plainMask & ~(1L << top));
				int slot = freeSlots[top];
				List<StatPoint> combined = new ArrayList<>(rest.size() * plain.get(slot).size());
				for (StatPoint p : rest)
				{
					for (Candidate c : plain.get(slot))
					{
						Candidate[] picks = p.picks.clone();
						picks[slot] = c;
						// only reaching the minimum matters, so heavier than that counts as equal
						combined.add(new StatPoint(p.offence + offence(c, type), p.damage + damage(c, type),
							Math.min(minWeight, p.weight + weight(c)), picks));
					}
				}
				points = minWeight > 0 ? paretoFrontWithWeight(combined) : paretoFront(combined);
			}
			frontierCache.put(plainMask, points);
			return points;
		}

		private List<Candidate> ammoFor(Candidate weapon)
		{
			if (weapon == null || WeaponRules.ammoKind(weapon.item) == WeaponRules.AmmoKind.NONE)
			{
				// the ammo slot gives nothing to weapons that do not fire it
				return Collections.singletonList(null);
			}
			String key = WeaponRules.ammoKey(weapon.item);
			List<Candidate> cached = ammoByKey.get(key);
			if (cached == null)
			{
				List<Candidate> compatible = new ArrayList<>();
				for (Candidate a : ammoAll)
				{
					if (WeaponRules.ammoUsable(weapon.item, a.item))
					{
						compatible.add(a);
					}
				}
				cached = prune(compatible, AttackType.RANGED, false);
				ammoByKey.put(key, cached);
			}
			return cached;
		}

		private void set(int slot, Candidate c)
		{
			chosen[slot] = c;
			gear[slot] = c == null ? null : c.item;
		}

		private void evaluate()
		{
			if ((++evaluations & 0xFFF) == 0 && cancelled.get())
			{
				throw new CancellationException();
			}
			if (evaluations > MAX_EVALUATIONS_PER_STYLE)
			{
				stoppedEarly = true;
				return;
			}
			if (minWeight > 0 && weight(chosen) < minWeight)
			{
				return;
			}
			CalcResult r = calculator.best(gear, type);
			if (r.getDps() <= 0)
			{
				return;
			}
			if (r.getDps() > bestResult.getDps())
			{
				bestResult = r;
				bestGear = chosen.clone();
			}
			int weaponKey = chosen[WEAPON] == null ? -1 : chosen[WEAPON].item.getId();
			StyleResult.Alternative current = bestByWeapon.get(weaponKey);
			if (current == null || r.getDps() > current.getResult().getDps())
			{
				bestByWeapon.put(weaponKey, new StyleResult.Alternative(r, chosen.clone()));
			}
		}
	}

	/**
	 * Keeps the points not beaten or tied on both sums by another point (one of each tie survives).
	 */
	/**
	 * As {@link #paretoFront}, with weight as a third thing to keep: a lighter combination only
	 * drops out if another is at least as good on offence, damage and weight.
	 */
	private static List<StatPoint> paretoFrontWithWeight(List<StatPoint> points)
	{
		points.sort((a, b) -> a.offence != b.offence ? Integer.compare(b.offence, a.offence)
			: a.damage != b.damage ? Integer.compare(b.damage, a.damage) : Integer.compare(b.weight, a.weight));
		List<StatPoint> out = new ArrayList<>();
		for (StatPoint p : points)
		{
			boolean beaten = false;
			for (StatPoint q : out)
			{
				if (q.damage >= p.damage && q.weight >= p.weight)
				{
					beaten = true;
					break;
				}
			}
			if (!beaten)
			{
				out.add(p);
			}
		}
		return out;
	}

	private static List<StatPoint> paretoFront(List<StatPoint> points)
	{
		points.sort((a, b) -> a.offence != b.offence ? Integer.compare(b.offence, a.offence) : Integer.compare(b.damage, a.damage));
		List<StatPoint> out = new ArrayList<>();
		int bestDamage = Integer.MIN_VALUE;
		for (StatPoint p : points)
		{
			if (p.damage > bestDamage)
			{
				out.add(p);
				bestDamage = p.damage;
			}
		}
		return out;
	}
}
