package com.slayerbis;

import com.slayerbis.calc.Optimizer;
import com.slayerbis.calc.PlayerSetup;
import com.slayerbis.calc.StyleResult;
import com.slayerbis.data.AttackType;
import com.slayerbis.data.EquipmentSlot;
import com.slayerbis.data.GameData;
import com.slayerbis.data.GearItem;
import com.slayerbis.data.Monster;
import com.slayerbis.data.TaskMonsterResolver;
import com.slayerbis.prep.PrepData;
import com.slayerbis.prep.PrepPlan;
import com.slayerbis.prep.PrepPlanner;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Offline checks of the whole-fight plan: the main set against the first monster, and switches for
 * the rest. No game client involved.
 */
public class FightPlanTest
{
	private static GameData data;
	private static TaskMonsterResolver resolver;

	@BeforeClass
	public static void load() throws Exception
	{
		data = GameData.load(new Gson());
		resolver = new TaskMonsterResolver(data);
	}

	private static final PlayerSetup SETUP = PlayerSetup.builder().attack(99).strength(99).ranged(99).magic(99)
		.hitpoints(99).blowpipeDartStrength(35).build();

	/** A mid-level bank with a set for each style. */
	private static final String[] BANK = {"Abyssal whip", "Dragon defender", "Fighter torso", "Bandos tassets",
		"Helm of Neitiznot", "Barrows gloves", "Dragon boots", "Amulet of fury", "Fire cape", "Berserker ring (i)",
		"Rune crossbow", "Adamant bolts", "Black d'hide body", "Black d'hide chaps", "Ava's accumulator",
		"Trident of the Swamp", "Occult necklace", "Mystic robe top", "Mystic robe bottom", "Dragon warhammer"};

	/**
	 * A whole-fight plan for the Dagannoth Kings with this switch tolerance, printed for checking.
	 */
	private static List<OptimizationOutcome.Phase> planKings(int tolerance, List<PrepPlan.Switch> switches,
		StyleResult[] mainOut)
	{
		List<Optimizer.Candidate> bank = new ArrayList<>();
		Map<Integer, String> names = new HashMap<>();
		for (String n : BANK)
		{
			GearItem item = data.findItem(n);
			assertNotNull(n, item);
			bank.add(new Optimizer.Candidate(item, item.getId()));
			names.put(item.getId(), item.getName());
		}
		List<Monster> kings = resolver.forBoss(List.of("Dagannoth Rex", "Dagannoth Prime", "Dagannoth Supreme"));
		Monster rex = kings.get(0);
		AtomicBoolean cancel = new AtomicBoolean();
		Optimizer optimizer = new Optimizer(SETUP, rex, cancel);
		StyleResult main = null;
		for (AttackType t : AttackType.values())
		{
			if (t == AttackType.BURST)
			{
				continue;
			}
			StyleResult r = optimizer.optimise(bank, t);
			if (r.isValid() && (main == null || r.getResult().getDps() > main.getResult().getDps()))
			{
				main = r;
			}
		}
		assertNotNull(main);
		mainOut[0] = main;

		List<OptimizationOutcome.Phase> phases = new ArrayList<>();
		Set<String> weapons = new HashSet<>();
		SlayerBisPlugin.planPhases(kings, rex, main, SETUP, cancel, bank, Collections.emptyMap(),
			Collections.emptyList(), 0, tolerance, names, phases, switches, weapons);
		System.out.println("tolerance " + tolerance + "%:");
		for (OptimizationOutcome.Phase p : phases)
		{
			System.out.printf("  %s: %s %.2f DPS (gave up %.2f, saved %d), switches %s%n", p.getMonster().getName(),
				p.getType(), p.getDps(), p.getDpsGivenUp(), p.getSwitchesSaved(),
				p.getSwitches().stream().map(c -> c.getItem().getName()).collect(java.util.stream.Collectors.toList()));
		}
		return phases;
	}

	@Test
	public void dagannothKingsGetSwitchesForEachKing()
	{
		List<PrepPlan.Switch> switches = new ArrayList<>();
		StyleResult[] main = new StyleResult[1];
		List<OptimizationOutcome.Phase> phases = planKings(0, switches, main);
		assertEquals(3, phases.size());
		assertEquals("Dagannoth Rex", phases.get(0).getMonster().getName());
		assertTrue(phases.get(0).getSwitches().isEmpty());
		// Prime and Supreme are weak to other styles than Rex, so there are switches
		assertTrue(switches.size() >= 2);
		for (OptimizationOutcome.Phase p : phases.subList(1, 3))
		{
			// with no tolerance, every phase uses its best set
			assertEquals(0, p.getSwitchesSaved());
			assertEquals(0, p.getDpsGivenUp(), 1e-9);
			for (Optimizer.Candidate c : p.getSwitches())
			{
				Optimizer.Candidate mainItem = main[0].getGear()[c.getItem().getSlot().ordinal()];
				assertTrue(mainItem == null || mainItem.getItem().getId() != c.getItem().getId());
			}
		}
		// every switch is listed once
		assertEquals(switches.size(), new HashSet<>(switches).size());
		assertTrue(switches.stream().allMatch(s -> s.getNote().startsWith("switch for ")));
		assertTrue(main[0].getGear()[EquipmentSlot.WEAPON.ordinal()] != null);
	}

	@Test
	public void toleranceCarriesFewerSwitchesForLittleDps()
	{
		List<PrepPlan.Switch> all = new ArrayList<>();
		List<OptimizationOutcome.Phase> best = planKings(0, all, new StyleResult[1]);
		// the default, printed for comparison
		List<PrepPlan.Switch> byDefault = new ArrayList<>();
		planKings(10, byDefault, new StyleResult[1]);
		assertTrue(byDefault.size() <= all.size());
		List<PrepPlan.Switch> fewer = new ArrayList<>();
		List<OptimizationOutcome.Phase> trimmed = planKings(25, fewer, new StyleResult[1]);
		assertTrue("expected fewer switches: " + fewer.size() + " vs " + all.size(), fewer.size() < all.size());
		for (int i = 1; i < trimmed.size(); i++)
		{
			OptimizationOutcome.Phase full = best.get(i);
			OptimizationOutcome.Phase p = trimmed.get(i);
			assertTrue(p.getSwitches().size() <= full.getSwitches().size());
			// never more than 25% below the phase's best set
			assertTrue(p.getDps() * 125 >= full.getDps() * 100 - 1e-6);
			assertEquals(full.getDps() - p.getDps(), p.getDpsGivenUp(), 1e-9);
			assertEquals(full.getSwitches().size() - p.getSwitches().size(), p.getSwitchesSaved());
		}
	}

	@Test
	public void slayerHelmetCountsOnTaskOnlyBosses()
	{
		// task-only bosses are planned on task, where a Slayer helmet (i) beats a Neitiznot faceguard
		List<Optimizer.Candidate> bank = new ArrayList<>();
		for (String n : new String[]{"Abyssal whip", "Slayer helmet (i)", "Neitiznot faceguard", "Fighter torso",
			"Bandos tassets", "Dragon defender"})
		{
			GearItem item = data.findItem(n);
			assertNotNull(n, item);
			bank.add(new Optimizer.Candidate(item, item.getId()));
		}
		Monster cerberus = resolver.forBoss(List.of("Cerberus")).get(0);
		StyleResult onTask = new Optimizer(SETUP.toBuilder().onSlayerTask(true).build(), cerberus, new AtomicBoolean())
			.optimise(bank, AttackType.SLASH);
		StyleResult offTask = new Optimizer(SETUP, cerberus, new AtomicBoolean()).optimise(bank, AttackType.SLASH);
		assertEquals("Slayer helmet (i)", onTask.getGear()[EquipmentSlot.HEAD.ordinal()].getItem().getName());
		assertEquals("Neitiznot faceguard", offTask.getGear()[EquipmentSlot.HEAD.ordinal()].getItem().getName());
		assertTrue(onTask.getResult().getDps() > offTask.getResult().getDps());
	}

	@Test
	public void vorkathKeepsADragonfireShieldOverATwistedBuckler() throws Exception
	{
		PrepPlanner planner = new PrepPlanner(PrepData.load(new Gson()));
		List<Optimizer.Candidate> bank = new ArrayList<>();
		for (String n : new String[]{"Dragon hunter crossbow", "Adamant bolts", "Twisted buckler", "Anti-dragon shield",
			"Black d'hide body", "Black d'hide chaps", "Ava's accumulator", "Amulet of fury"})
		{
			GearItem item = data.findItem(n);
			assertNotNull(n, item);
			bank.add(new Optimizer.Candidate(item, item.getId()));
		}
		Monster vorkath = resolver.forBoss(List.of("Vorkath (Post-quest)")).get(0);
		Optimizer optimizer = new Optimizer(SETUP, vorkath, new AtomicBoolean());
		StyleResult best = optimizer.optimise(bank, AttackType.RANGED);
		assertEquals("Twisted buckler", best.getGear()[EquipmentSlot.SHIELD.ordinal()].getItem().getName());

		Map<AttackType, List<String>> notes = new java.util.EnumMap<>(AttackType.class);
		Map<AttackType, Double> cost = new java.util.EnumMap<>(AttackType.class);
		StyleResult kept = SlayerBisPlugin.applyPreferences(optimizer, bank, AttackType.RANGED, best,
			Collections.emptyMap(), planner.preferences("vorkath"), 0, notes, cost);
		assertEquals("Anti-dragon shield", kept.getGear()[EquipmentSlot.SHIELD.ordinal()].getItem().getName());
		assertTrue(notes.get(AttackType.RANGED).get(0).startsWith("Anti-dragon shield kept on"));
		assertEquals(best.getResult().getDps() - kept.getResult().getDps(), cost.get(AttackType.RANGED), 1e-9);
		System.out.println(notes.get(AttackType.RANGED));
	}
	@Test
	public void tormentedDemonsGetASecondStyle()
	{
		List<Optimizer.Candidate> bank = new ArrayList<>();
		Map<Integer, String> names = new HashMap<>();
		for (String n : BANK)
		{
			GearItem item = data.findItem(n);
			bank.add(new Optimizer.Candidate(item, item.getId()));
			names.put(item.getId(), item.getName());
		}
		Monster demon = resolver.forBoss(List.of("Tormented Demon")).get(0);
		Optimizer optimizer = new Optimizer(SETUP, demon, new AtomicBoolean());
		Map<AttackType, StyleResult> results = new java.util.EnumMap<>(AttackType.class);
		StyleResult main = null;
		for (AttackType t : AttackType.values())
		{
			if (t == AttackType.BURST)
			{
				continue;
			}
			StyleResult r = optimizer.optimise(bank, t);
			results.put(t, r);
			if (r.isValid() && (main == null || r.getResult().getDps() > main.getResult().getDps()))
			{
				main = r;
			}
		}
		List<OptimizationOutcome.Phase> phases = new ArrayList<>();
		List<PrepPlan.Switch> switches = new ArrayList<>();
		SlayerBisPlugin.planSecondStyle(optimizer, results, main, bank, Collections.emptyMap(), 0, 10, names, demon,
			phases, switches, new HashSet<>());
		assertEquals(2, phases.size());
		AttackType second = phases.get(1).getType();
		System.out.printf("main %s, second %s, switches %s%n", main.getType(), second,
			switches.stream().map(PrepPlan.Switch::getName).collect(java.util.stream.Collectors.toList()));
		// a different style group from the main set, with at least a weapon to switch to
		assertTrue(main.getType().isMelee() != second.isMelee() || (second == AttackType.RANGED) != (main.getType() == AttackType.RANGED));
		assertTrue(phases.get(1).getSwitches().stream().anyMatch(c -> c.getItem().getSlot() == EquipmentSlot.WEAPON));
		assertTrue(switches.stream().allMatch(sw -> sw.getNote().endsWith("as a second style")));
	}
}
