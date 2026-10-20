package com.slayerbis.calc;

import com.slayerbis.data.AttackType;
import com.slayerbis.data.EquipmentSlot;
import com.slayerbis.data.GameData;
import com.slayerbis.data.GearItem;
import com.slayerbis.data.Monster;
import com.slayerbis.data.TaskMonsterResolver;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Offline checks of pruning, set handling and two-handed weapons using made-up banks.
 */
public class OptimizerTest
{
	private static GameData data;
	private static TaskMonsterResolver resolver;

	@BeforeClass
	public static void load() throws Exception
	{
		data = GameData.load(new Gson());
		resolver = new TaskMonsterResolver(data);
	}

	private static List<Optimizer.Candidate> bank(String... names)
	{
		List<Optimizer.Candidate> out = new ArrayList<>();
		for (String n : names)
		{
			GearItem item = data.findItem(n);
			assertNotNull("missing item " + n, item);
			out.add(new Optimizer.Candidate(item, item.getId()));
		}
		return out;
	}

	private static String nameIn(StyleResult r, EquipmentSlot slot)
	{
		Optimizer.Candidate c = r.getGear()[slot.ordinal()];
		return c == null ? null : c.getItem().getName();
	}

	private static Monster monster(String task)
	{
		Monster m = resolver.fromName(task);
		assertNotNull(m);
		return m;
	}

	@Test
	public void outclassedItemsAreRemoved()
	{
		Optimizer o = new Optimizer(DpsCalculatorTest.maxed(false), monster("Abyssal demons"));
		StyleResult r = o.optimise(bank("Abyssal whip", "Bronze full helm", "Helm of Neitiznot", "Leather boots",
			"Dragon boots"), AttackType.SLASH);
		assertTrue(r.isValid());
		assertEquals("Helm of Neitiznot", nameIn(r, EquipmentSlot.HEAD));
		assertEquals("Dragon boots", nameIn(r, EquipmentSlot.FEET));
	}

	@Test
	public void twoHandedWeaponsNeverUseAShield()
	{
		Optimizer o = new Optimizer(DpsCalculatorTest.maxed(false), monster("Abyssal demons"));
		StyleResult r = o.optimise(bank("Dragon 2h sword", "Dragon defender", "Rune scimitar"), AttackType.SLASH);
		assertTrue(r.isValid());
		Optimizer.Candidate weapon = r.getGear()[EquipmentSlot.WEAPON.ordinal()];
		if (weapon.getItem().isTwoHanded())
		{
			assertNull(nameIn(r, EquipmentSlot.SHIELD));
		}
	}

	@Test
	public void bestOfTwoHandedVersusWeaponAndShield()
	{
		Optimizer o = new Optimizer(DpsCalculatorTest.maxed(false), monster("Abyssal demons"));
		// the whip with a defender beats a dragon 2h sword against a 4-tick, high slash-defence target
		StyleResult r = o.optimise(bank("Dragon 2h sword", "Dragon defender", "Abyssal whip"), AttackType.SLASH);
		assertEquals("Abyssal whip", nameIn(r, EquipmentSlot.WEAPON));
		assertEquals("Dragon defender", nameIn(r, EquipmentSlot.SHIELD));
	}

	@Test
	public void completeSetIsTriedAsOneCandidate()
	{
		Optimizer o = new Optimizer(DpsCalculatorTest.maxed(false), monster("Abyssal demons"));
		StyleResult r = o.optimise(bank("Abyssal whip", "Void melee helm", "Void knight top", "Void knight robe",
			"Void knight gloves", "Helm of Neitiznot"), AttackType.SLASH);
		assertTrue(r.getSetsConsidered().contains("Void Knight (melee)"));
		// void's 10% accuracy and strength beats loose pieces with these few other items
		assertEquals("Void melee helm", nameIn(r, EquipmentSlot.HEAD));
		assertEquals("Void knight top", nameIn(r, EquipmentSlot.BODY));
	}

	@Test
	public void slayerHelmChosenOnTask()
	{
		Optimizer o = new Optimizer(DpsCalculatorTest.maxed(true), monster("Abyssal demons"));
		StyleResult r = o.optimise(bank("Abyssal whip", "Slayer helmet (i)", "Helm of Neitiznot"), AttackType.SLASH);
		assertEquals("Slayer helmet (i)", nameIn(r, EquipmentSlot.HEAD));
	}

	@Test
	public void rangedPicksCompatibleAmmo()
	{
		Optimizer o = new Optimizer(DpsCalculatorTest.maxed(false), monster("Abyssal demons"));
		StyleResult r = o.optimise(bank("Magic shortbow", "Dragon arrow", "Rune arrow", "Bronze arrow"), AttackType.RANGED);
		assertTrue(r.isValid());
		assertEquals("Rune arrow", nameIn(r, EquipmentSlot.AMMO));
	}

	@Test
	public void leafyMonsterNeedsLeafBladedWeapon()
	{
		Optimizer o = new Optimizer(DpsCalculatorTest.maxed(false), monster("Kurask"));
		StyleResult r = o.optimise(bank("Abyssal whip", "Leaf-bladed sword"), AttackType.SLASH);
		assertEquals("Leaf-bladed sword", nameIn(r, EquipmentSlot.WEAPON));
	}

	@Test
	public void noUsableGearGivesNoResult()
	{
		Optimizer o = new Optimizer(DpsCalculatorTest.maxed(false), monster("Abyssal demons"));
		StyleResult r = o.optimise(bank("Helm of Neitiznot"), AttackType.MAGIC);
		assertTrue(!r.isValid());
	}

	@Test
	public void taskNamesResolve()
	{
		assertEquals("Abyssal demon", monster("Abyssal demons").getName());
		assertEquals("Kalphite Soldier", monster("Kalphites").getName());
		assertEquals("Wolf", monster("Wolves").getName());
		assertEquals("Dwarf", monster("Dwarves").getName());
		assertEquals("Hellhound", monster("Hellhounds").getName());
		assertEquals("Steel dragon", monster("Metal dragons").getName());
	}

	@Test
	public void setPieceNamesExistInData()
	{
		for (GearSets set : GearSets.ALL)
		{
			for (List<String> names : set.getPieces().values())
			{
				boolean any = false;
				for (String n : names)
				{
					any |= data.findItem(n) != null;
				}
				assertTrue(set.getName() + " has an unknown piece " + names, any);
			}
		}
	}

	@Test
	public void unchargedVariantsAreNotUsable()
	{
		GearItem charged = null;
		GearItem empty = null;
		for (int id : new int[]{12926, 12924})
		{
			GearItem g = data.getItem(id);
			if ("Charged".equals(g.getVersion()))
			{
				charged = g;
			}
			else
			{
				empty = g;
			}
		}
		assertNotNull(charged);
		assertNotNull(empty);
		assertTrue(charged.isUsable());
		assertTrue(!empty.isUsable());
		// the charged version is offered as the "if charged" alternative
		assertEquals(charged.getId(), data.findUsableVariant(empty).getId());
	}

	@Test
	public void cancelFlagStopsTheSearch()
	{
		java.util.concurrent.atomic.AtomicBoolean cancel = new java.util.concurrent.atomic.AtomicBoolean(true);
		Optimizer o = new Optimizer(DpsCalculatorTest.maxed(false), monster("Abyssal demons"), cancel);
		List<Optimizer.Candidate> everything = new ArrayList<>();
		for (int id = 0; id < 40_000; id++)
		{
			GearItem item = data.getItem(id);
			if (item != null && item.getId() == id && item.isUsable())
			{
				everything.add(new Optimizer.Candidate(item, id));
			}
		}
		try
		{
			o.optimise(everything, AttackType.RANGED);
			org.junit.Assert.fail("expected the search to stop");
		}
		catch (java.util.concurrent.CancellationException expected)
		{
			// stopped at the first check
		}
	}

	@Test
	public void requiredShieldIsAlwaysWornAndRulesOutTwoHanders()
	{
		Optimizer o = new Optimizer(DpsCalculatorTest.maxed(true), monster("Basilisks"));
		java.util.Map<EquipmentSlot, java.util.Set<String>> required = new java.util.EnumMap<>(EquipmentSlot.class);
		required.put(EquipmentSlot.SHIELD, new java.util.HashSet<>(java.util.Arrays.asList("Mirror shield", "V's shield")));
		StyleResult r = o.optimise(bank("Dragon 2h sword", "Abyssal whip", "Dragon defender", "Mirror shield"),
			AttackType.SLASH, required);
		assertEquals("Mirror shield", nameIn(r, EquipmentSlot.SHIELD));
		assertEquals("Abyssal whip", nameIn(r, EquipmentSlot.WEAPON));
	}

	@Test
	public void alternativesUseOtherWeapons()
	{
		Optimizer o = new Optimizer(DpsCalculatorTest.maxed(false), monster("Abyssal demons"));
		// different weapon types, so none is removed as outclassed by another
		StyleResult r = o.optimise(bank("Abyssal whip", "Dragon scimitar", "Dragon battleaxe"), AttackType.SLASH);
		assertEquals(2, r.getAlternatives().size());
		String best = nameIn(r, EquipmentSlot.WEAPON);
		for (StyleResult.Alternative alt : r.getAlternatives())
		{
			assertTrue(!best.equals(alt.getGear()[EquipmentSlot.WEAPON.ordinal()].getItem().getName()));
			assertTrue(alt.getResult().getDps() <= r.getResult().getDps());
		}
	}

	@Test
	public void multiMonsterTasksOfferEveryMonster()
	{
		java.util.Set<String> names = new java.util.HashSet<>();
		for (Monster m : resolver.candidates("Metal dragons"))
		{
			names.add(m.getName());
		}
		assertTrue(names.containsAll(java.util.Arrays.asList("Bronze dragon", "Iron dragon", "Steel dragon",
			"Mithril dragon", "Adamant dragon", "Rune dragon")));
		assertTrue(resolver.candidates("Abyssal demons").size() >= 2);
	}

	@Test
	public void slayerHelmetCanBeKeptOnUndead()
	{
		// Salve amulet (ei) and Slayer helmet (i) don't stack; which wins depends on the rest of the gear,
		// and either way keeping the helmet costs little against crawling hands
		List<Optimizer.Candidate> owned = bank("Slayer helmet (i)", "Serpentine helm", "Salve amulet(ei)",
			"Amulet of glory", "Abyssal whip", "Dragon defender", "Fighter torso", "Bandos tassets",
			"Barrows gloves", "Dragon boots", "Berserker ring (i)", "Fire cape");
		Optimizer optimizer = new Optimizer(DpsCalculatorTest.maxed(true), monster("Crawling hands"));
		StyleResult best = optimizer.optimise(owned, AttackType.SLASH);

		java.util.Map<EquipmentSlot, java.util.Set<String>> helmet = new java.util.EnumMap<>(EquipmentSlot.class);
		helmet.put(EquipmentSlot.HEAD, java.util.Collections.singleton("Slayer helmet (i)"));
		StyleResult kept = optimizer.optimise(owned, AttackType.SLASH, helmet);
		assertEquals("Slayer helmet (i)", nameIn(kept, EquipmentSlot.HEAD));
		double gap = 1 - kept.getResult().getDps() / best.getResult().getDps();
		System.out.printf("crawling hands: best %.3f (%s, %s), with helmet %.3f (%s), gap %.1f%%%n",
			best.getResult().getDps(), nameIn(best, EquipmentSlot.HEAD), nameIn(best, EquipmentSlot.NECK),
			kept.getResult().getDps(), nameIn(kept, EquipmentSlot.NECK), gap * 100);
		assertTrue(gap >= 0 && gap < 0.05);
	}
}
