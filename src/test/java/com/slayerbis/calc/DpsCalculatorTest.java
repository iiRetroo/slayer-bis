package com.slayerbis.calc;

import com.slayerbis.data.AttackType;
import com.slayerbis.data.EquipmentSlot;
import com.slayerbis.data.GameData;
import com.slayerbis.data.GearItem;
import com.slayerbis.data.Monster;
import com.slayerbis.data.TaskMonsterResolver;
import com.google.gson.Gson;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Offline checks of the DPS maths against known OSRS reference values. No game client involved.
 */
public class DpsCalculatorTest
{
	private static GameData data;
	private static Monster abyssalDemon;

	@BeforeClass
	public static void load() throws Exception
	{
		data = GameData.load(new Gson());
		abyssalDemon = new TaskMonsterResolver(data).fromName("Abyssal demons");
	}

	static PlayerSetup maxed(boolean onTask)
	{
		return PlayerSetup.builder().attack(99).strength(99).ranged(99).magic(99).hitpoints(99)
			.onSlayerTask(onTask).blowpipeDartStrength(35).build();
	}

	static GearItem item(String name)
	{
		GearItem item = data.findItem(name);
		assertNotNull("missing item " + name, item);
		return item;
	}

	private static GearItem[] gear(String... names)
	{
		GearItem[] gear = new GearItem[EquipmentSlot.values().length];
		for (String n : names)
		{
			GearItem item = item(n);
			gear[item.getSlot().ordinal()] = item;
		}
		return gear;
	}

	@Test
	public void accuracyFormula()
	{
		assertEquals(1 - 10002.0 / (2 * 20001), DpsCalculator.normalAccuracy(20000, 10000), 1e-12);
		assertEquals(10000.0 / (2 * 20001), DpsCalculator.normalAccuracy(10000, 20000), 1e-12);
	}

	@Test
	public void twistedBowScalingCapsAt250Magic()
	{
		// at the 250 magic cap outside raids: 140% accuracy, 215% damage
		assertEquals(140, DpsCalculator.tbowScaling(100, 250, true));
		assertEquals(215, DpsCalculator.tbowScaling(100, 250, false));
	}

	@Test
	public void whipMaxHitAt99Strength()
	{
		// 99 strength, abyssal whip only, controlled (lash): ((99+8+1) * (82+64) + 320) / 640 = 25
		CalcResult r = new DpsCalculator(maxed(false), abyssalDemon).best(gear("Abyssal whip"), AttackType.SLASH);
		assertTrue(r.getDps() > 0);
		int max = r.getStance() == Stance.CONTROLLED ? 25 : (99 + 8) * (82 + 64) / 640;
		assertEquals(max, r.getMaxHit());
	}

	@Test
	public void poweredStaffMaxHits()
	{
		assertEquals(31, WeaponRules.poweredBaseMaxHit("Trident of the Swamp", 99));
		assertEquals(28, WeaponRules.poweredBaseMaxHit("Trident of the Seas", 99));
		assertEquals(34, WeaponRules.poweredBaseMaxHit("Tumeken's shadow", 99));
		assertEquals(33, WeaponRules.poweredBaseMaxHit("Sanguinesti staff", 99));
	}

	@Test
	public void slayerHelmBoostsMeleeOnTaskOnly()
	{
		GearItem[] withHelm = gear("Abyssal whip", "Slayer helmet (i)");
		double onTask = new DpsCalculator(maxed(true), abyssalDemon).best(withHelm, AttackType.SLASH).getDps();
		double offTask = new DpsCalculator(maxed(false), abyssalDemon).best(withHelm, AttackType.SLASH).getDps();
		assertTrue(onTask > offTask * 1.15);
	}

	@Test
	public void rangedNeedsCompatibleAmmo()
	{
		DpsCalculator calc = new DpsCalculator(maxed(false), abyssalDemon);
		assertEquals(0, calc.best(gear("Magic shortbow"), AttackType.RANGED).getDps(), 0);
		assertEquals(0, calc.best(gear("Magic shortbow", "Dragon arrow"), AttackType.RANGED).getDps(), 0);
		assertTrue(calc.best(gear("Magic shortbow", "Amethyst arrow"), AttackType.RANGED).getDps() > 0);
		// blowpipe needs nothing in the ammo slot
		assertTrue(calc.best(gear("Toxic blowpipe"), AttackType.RANGED).getDps() > 0);
	}

	@Test
	public void flyingMonstersAreImmuneToNonPolearmMelee()
	{
		Monster aviansie = new TaskMonsterResolver(data).fromName("Aviansies");
		assertNotNull(aviansie);
		assertTrue(aviansie.has(Monster.FLYING));
		DpsCalculator calc = new DpsCalculator(maxed(false), aviansie);
		assertEquals(0, calc.best(gear("Abyssal whip"), AttackType.SLASH).getDps(), 0);
	}

	@Test
	public void standardSpellsAutocastFromStaff()
	{
		CalcResult r = new DpsCalculator(maxed(false), abyssalDemon).best(gear("Staff of fire"), AttackType.MAGIC);
		assertNotNull(r.getSpell());
		// the best spell depends on the demon's elemental weakness, but at 99 magic it is always a surge
		assertTrue(r.getSpell().getName().endsWith("Surge"));
		assertTrue(r.getMaxHit() >= 21);
		assertEquals(5, r.getAttackSpeedTicks());
	}

	@Test
	public void burstHitsEveryTargetAndNeedsTheLevel()
	{
		GearItem[] kodai = gear("Kodai wand");
		Spell barrage = Spell.ANCIENT.get(Spell.ANCIENT.size() - 1);
		assertEquals("Ice Barrage", barrage.getName());
		CalcResult one = new DpsCalculator(maxed(false), abyssalDemon).single(kodai, AttackType.MAGIC, Stance.AUTOCAST, barrage);
		PlayerSetup burst = maxed(false).toBuilder().ancientSpellbook(true).burstTargets(3).build();
		CalcResult three = new DpsCalculator(burst, abyssalDemon).best(kodai, AttackType.MAGIC);
		assertEquals("Ice Barrage", three.getSpell().getName());
		assertEquals(one.getDps() * 3, three.getDps(), 1e-9);
		assertEquals(one.getMaxHit(), three.getMaxHit());

		// Ice Burst at 70, nothing below Smoke Burst's 62
		PlayerSetup lower = burst.toBuilder().magic(75).build();
		assertEquals("Ice Burst", new DpsCalculator(lower, abyssalDemon).best(kodai, AttackType.MAGIC).getSpell().getName());
		PlayerSetup low = burst.toBuilder().magic(61).build();
		assertEquals(0, new DpsCalculator(low, abyssalDemon).best(kodai, AttackType.MAGIC).getDps(), 0);
	}
}
