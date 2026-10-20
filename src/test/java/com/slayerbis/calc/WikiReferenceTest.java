package com.slayerbis.calc;

import com.slayerbis.data.AttackType;
import com.slayerbis.data.EquipmentSlot;
import com.slayerbis.data.GameData;
import com.slayerbis.data.GearItem;
import com.slayerbis.data.Monster;
import com.google.gson.Gson;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Loadouts whose results were checked by hand against the live OSRS Wiki DPS calculator
 * (https://tools.runescape.wiki/osrs-dps/, version 89c3e25) on 2026-10-19.
 *
 * <p>Calculator settings for every case: all skills 99, no prayers, no potions, the combat style
 * named in the case. "On Slayer task" only matters for the slayer helmet case.
 * Expected values are copied from the calculator's Results table, which rounds DPS to 3 decimal
 * places and accuracy to 2.</p>
 *
 * <p>Monster ids: 415 Abyssal demon (Standard), 7245 Greater demon (Level 100),
 * 8031 Rune dragon, 2516 Ankou (Level 86).</p>
 */
public class WikiReferenceTest
{
	private static final int ABYSSAL_DEMON = 415;
	private static final int GREATER_DEMON_100 = 7245;
	private static final int RUNE_DRAGON = 8031;
	private static final int ANKOU_86 = 2516;

	private static GameData data;

	@BeforeClass
	public static void load() throws Exception
	{
		data = GameData.load(new Gson());
	}

	private static PlayerSetup player(boolean onTask)
	{
		return PlayerSetup.builder().attack(99).strength(99).ranged(99).magic(99).hitpoints(99)
			.onSlayerTask(onTask).blowpipeDartStrength(35).build();
	}

	/**
	 * Calculates one stance directly, so the check does not depend on which stance the optimiser prefers.
	 */
	private static void check(int monsterId, AttackType type, Stance stance, boolean onTask,
		int maxHit, double accuracyPercent, double dps, String... items)
	{
		Monster monster = data.getMonster(monsterId);
		assertNotNull(monster);
		GearItem[] gear = new GearItem[EquipmentSlot.values().length];
		for (String name : items)
		{
			GearItem item = data.findItem(name);
			assertNotNull("missing item " + name, item);
			gear[item.getSlot().ordinal()] = item;
		}
		CalcResult r = new DpsCalculator(player(onTask), monster).single(gear, type, stance);
		String label = String.join(", ", items) + " vs " + monster.displayName() + " (" + stance + ")";
		assertEquals(label + ": max hit", maxHit, r.getMaxHit());
		assertEquals(label + ": accuracy", accuracyPercent, r.getAccuracy() * 100, 0.005);
		assertEquals(label + ": dps", dps, r.getDps(), 0.0005);
	}

	@Test
	public void whipOnly()
	{
		check(ABYSSAL_DEMON, AttackType.SLASH, Stance.ACCURATE, false, 24, 62.34, 3.127, "Abyssal whip");
		check(ABYSSAL_DEMON, AttackType.SLASH, Stance.CONTROLLED, false, 25, 61.64, 3.220, "Abyssal whip");
	}

	@Test
	public void midLevelMelee()
	{
		String[] gear = {"Abyssal whip", "Helm of Neitiznot", "Fire cape", "Amulet of fury", "Bandos chestplate",
			"Bandos tassets", "Dragon defender", "Barrows gloves", "Dragon boots", "Berserker ring (i)"};
		check(ABYSSAL_DEMON, AttackType.SLASH, Stance.CONTROLLED, false, 33, 70.98, 4.889, gear);
		check(ABYSSAL_DEMON, AttackType.SLASH, Stance.ACCURATE, false, 33, 71.51, 4.925, gear);
	}

	@Test
	public void slayerHelmetOnTask()
	{
		check(ABYSSAL_DEMON, AttackType.SLASH, Stance.ACCURATE, true, 28, 67.72, 3.960, "Abyssal whip", "Slayer helmet (i)");
		check(ABYSSAL_DEMON, AttackType.SLASH, Stance.CONTROLLED, true, 29, 67.12, 4.064, "Abyssal whip", "Slayer helmet (i)");
	}

	@Test
	public void arclightAgainstDemon()
	{
		check(GREATER_DEMON_100, AttackType.STAB, Stance.CONTROLLED, false, 20, 79.03, 3.309, "Arclight");
	}

	@Test
	public void blowpipeWithDragonDarts()
	{
		check(ABYSSAL_DEMON, AttackType.RANGED, Stance.RAPID, false, 23, 76.35, 7.343,
			"Toxic blowpipe", "Masori mask (f)", "Masori body (f)", "Masori chaps (f)", "Necklace of anguish",
			"Ava's assembler", "Zaryte vambraces", "Pegasian boots", "Venator ring");
	}

	@Test
	public void twistedBowAgainstLowMagic()
	{
		check(ABYSSAL_DEMON, AttackType.RANGED, Stance.RAPID, false, 12, 23.70, 0.480, "Twisted bow", "Dragon arrow");
	}

	@Test
	public void tridentOfTheSwamp()
	{
		check(ABYSSAL_DEMON, AttackType.MAGIC, Stance.ACCURATE, false, 37, 98.56, 7.608,
			"Trident of the Swamp", "Ancestral hat", "Ancestral robe top", "Ancestral robe bottom", "Occult necklace",
			"Tormented bracelet", "Eternal boots", "Magus ring");
	}

	@Test
	public void dragonHunterLanceAgainstDragon()
	{
		check(RUNE_DRAGON, AttackType.STAB, Stance.CONTROLLED, false, 27, 40.33, 2.274, "Dragon hunter lance");
	}

	@Test
	public void salveAgainstUndead()
	{
		check(ANKOU_86, AttackType.SLASH, Stance.ACCURATE, false, 28, 85.22, 4.983, "Abyssal whip", "Salve amulet(ei)");
		check(ANKOU_86, AttackType.SLASH, Stance.CONTROLLED, false, 30, 84.94, 5.320, "Abyssal whip", "Salve amulet(ei)");
	}

	/**
	 * Manual-cast Ice Barrage, as used for the Burst result. Checked on 2026-10-20 (version
	 * 89c3e25): "Magic, Manual Cast" with Ice Barrage selected, everything else as above.
	 */
	private static CalcResult barrage(String... items)
	{
		Monster monster = data.getMonster(ABYSSAL_DEMON);
		GearItem[] gear = new GearItem[EquipmentSlot.values().length];
		for (String name : items)
		{
			GearItem item = data.findItem(name);
			assertNotNull("missing item " + name, item);
			gear[item.getSlot().ordinal()] = item;
		}
		Spell barrage = Spell.ANCIENT.stream().filter(s -> s.getName().equals("Ice Barrage")).findFirst().get();
		return new DpsCalculator(player(false), monster).single(gear, AttackType.MAGIC, Stance.AUTOCAST, barrage);
	}

	private static void assertResult(CalcResult r, int maxHit, double accuracyPercent, double dps)
	{
		assertEquals("max hit", maxHit, r.getMaxHit());
		assertEquals("accuracy", accuracyPercent, r.getAccuracy() * 100, 0.005);
		assertEquals("dps", dps, r.getDps(), 0.0005);
	}

	@Test
	public void iceBarrageWithKodai()
	{
		assertResult(barrage("Kodai wand"), 34, 96.77, 5.493);
	}

	@Test
	public void iceBarrageWithVirtus()
	{
		assertResult(barrage("Kodai wand", "Virtus robe top"), 36, 97.66, 5.868);
	}

	@Test
	public void iceBarrageWithTumekensShadow()
	{
		// the shadow's bonuses are only tripled for its own spell, not a manual cast
		assertResult(barrage("Tumeken's shadow", "Virtus robe top"), 31, 97.78, 5.062);
	}
}
