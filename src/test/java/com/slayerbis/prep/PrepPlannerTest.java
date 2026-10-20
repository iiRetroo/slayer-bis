package com.slayerbis.prep;

import com.google.gson.Gson;
import com.slayerbis.data.AttackType;
import com.slayerbis.data.EquipmentSlot;
import com.slayerbis.data.GameData;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Offline checks of the task preparation data and suggestions. No game client involved.
 */
public class PrepPlannerTest
{
	private static PrepData data;
	private static PrepPlanner planner;
	private static GameData gameData;

	@BeforeClass
	public static void load() throws Exception
	{
		data = PrepData.load(new Gson());
		planner = new PrepPlanner(data);
		gameData = GameData.load(new Gson());
	}

	private static OwnedItems owned(Object... nameQty)
	{
		Map<String, Integer> q = new HashMap<>();
		Map<String, Set<Integer>> ids = new HashMap<>();
		for (int i = 0; i < nameQty.length; i += 2)
		{
			q.put((String) nameQty[i], (Integer) nameQty[i + 1]);
			ids.put((String) nameQty[i], new HashSet<>(Collections.singletonList(1000 + i)));
		}
		return OwnedItems.of(q, ids);
	}

	@Test
	public void namesMatchDosesAndCharges()
	{
		assertTrue(OwnedItems.matches("Prayer potion(3)", "Prayer potion"));
		assertTrue(OwnedItems.matches("Slayer ring (8)", "Slayer ring"));
		assertTrue(OwnedItems.matches("Fungicide spray 10", "Fungicide spray"));
		assertFalse(OwnedItems.matches("Magic potion(4)", "Divine magic potion"));
		assertFalse(OwnedItems.matches("Anti-venom+(4)", "Anti-venom"));
	}

	@Test
	public void basilisksNeedAMirrorShield()
	{
		List<PrepPlan.Requirement> without = planner.requirements("Basilisks", owned(), Collections.emptySet());
		assertEquals(1, without.size());
		assertEquals(EquipmentSlot.SHIELD, without.get(0).getSlot());
		assertFalse(without.get(0).isOwned());

		List<PrepPlan.Requirement> with = planner.requirements("Basilisks", owned("Mirror shield", 1), Collections.emptySet());
		assertTrue(with.get(0).isOwned());

		PrepPlan plan = planner.plan("Basilisks", null, AttackType.SLASH, without, owned(), 4, 1, true, null, 0);
		assertTrue(plan.getNotes().get(0).contains("Mirror shield"));
	}

	@Test
	public void slayerHelmetCountsByCanonicalName()
	{
		// "Black slayer helmet (i)" reaches the planner as its canonical gear name
		Set<String> gear = new HashSet<>(Collections.singletonList("Slayer helmet (i)"));
		assertTrue(planner.requirements("Aberrant spectres", owned(), gear).get(0).isOwned());
	}

	@Test
	public void superAntifireRemovesTheShieldRequirement()
	{
		assertEquals(1, planner.requirements("Blue dragons", owned(), Collections.emptySet()).size());
		assertEquals(0, planner.requirements("Blue dragons", owned("Extended super antifire(4)", 3),
			Collections.emptySet()).size());
		// wyverns need a shield whatever potions you have
		assertEquals(1, planner.requirements("Skeletal wyverns", owned("Extended super antifire(4)", 3),
			Collections.emptySet()).size());
	}

	@Test
	public void inventoryHasTaskItemsPotionsAndFood()
	{
		OwnedItems bank = owned("Rock hammer", 1, "Super combat potion(4)", 5, "Prayer potion(4)", 20,
			"Shark", 100, "Lobster", 50);
		PrepPlan plan = planner.plan("Gargoyles", "Slayer Tower", AttackType.CRUSH,
			Collections.emptyList(), bank, 4, 1, true, null, 0);
		assertEquals("Rock hammer", plan.getInventory().get(0).getName());
		assertEquals(PrepPlanner.INVENTORY_SLOTS, plan.getSlotsUsed());
		PrepPlan.InventoryLine food = plan.getInventory().get(plan.getInventory().size() - 1);
		assertEquals("Shark", food.getName());
		assertEquals(PrepPlanner.INVENTORY_SLOTS - 1 - 1 - 4, food.getQuantity());
		assertFalse(plan.getSupplyBankIds().isEmpty());
	}

	@Test
	public void missingTaskItemIsFlagged()
	{
		PrepPlan plan = planner.plan("Gargoyles", null, AttackType.CRUSH, Collections.emptyList(), owned(), 4, 1, true, null, 0);
		assertTrue(plan.getNotes().stream().anyMatch(n -> n.contains("Rock hammer")));
	}

	@Test
	public void teleportsFollowTheReportedLocation()
	{
		PrepPlan plan = planner.plan("Abyssal demons", "Slayer Tower", AttackType.SLASH, Collections.emptyList(),
			owned("Slayer ring (8)", 1), 0, 0, false, null, 0);
		assertTrue(plan.getTeleports().stream().allMatch(t -> t.getLocation().equals("Slayer Tower")));
		assertEquals(Boolean.TRUE, plan.getTeleports().get(0).getOwned());
	}

	@Test
	public void requiredItemNamesExistInTheItemData()
	{
		for (Map.Entry<String, PrepData.TaskInfo> task : data.allTasks().entrySet())
		{
			if (task.getValue().required == null)
			{
				continue;
			}
			for (List<String> names : task.getValue().required.values())
			{
				for (String n : names)
				{
					if (!n.equals("Boots of brimstone"))
					{
						assertNotNull(task.getKey() + ": unknown item " + n, gameData.findItem(n));
					}
				}
			}
		}
		for (String n : data.supplies().wyvernShields)
		{
			assertNotNull("unknown shield " + n, gameData.findItem(n));
		}
		for (String n : data.supplies().dragonfireShields)
		{
			assertNotNull("unknown shield " + n, gameData.findItem(n));
		}
	}

	@Test
	public void taskLocationsHaveTeleports()
	{
		for (Map.Entry<String, PrepData.TaskInfo> task : data.allTasks().entrySet())
		{
			if (task.getValue().locations == null)
			{
				continue;
			}
			for (String location : task.getValue().locations)
			{
				assertNotNull(task.getKey() + " lists unknown location " + location, data.locations().get(location));
			}
		}
	}

	@Test
	public void taskLocationsResolveToKnownKeys()
	{
		// common non-boss tasks, named as the Slayer plugin reports them
		String[] common = {
			"ankou", "aquanites", "araxytes", "aviansies", "bandits", "bats", "bears", "birds", "brine rats",
			"bronze dragons", "catablepon", "cave bugs", "cave slimes", "chaos druids", "cows", "crabs", "crocodiles",
			"custodian stalkers", "dark warriors", "dogs", "dwarves", "earth warriors", "elves", "ents",
			"fever spiders", "fleshcrawlers", "frost dragons", "ghosts", "ghouls", "goblins", "greater demons",
			"green dragons", "gryphons", "harpie bug swarms", "hill giants", "hobgoblins", "ice giants",
			"ice warriors", "icefiends", "iron dragons", "jungle horrors", "killerwatts", "lava dragons",
			"lesser demons", "lesser nagua", "lizardmen", "lizards", "magic axes", "mammoths", "metal dragons",
			"minotaurs", "mogres", "molanisks", "monkeys", "moss giants", "mutated zygomites", "ogres",
			"otherworldly beings", "pirates", "rats", "red dragons", "revenants", "rogues", "scabarites",
			"scorpions", "sea snakes", "shades", "shadow warriors", "skeletons", "sourhogs", "spiders",
			"steel dragons", "suqahs", "terror dogs", "tzhaar", "vampyres", "venators", "wall beasts",
			"werewolves", "wolves", "zombies"};
		for (String name : common)
		{
			PrepData.TaskInfo info = data.task(name);
			assertNotNull(name + " is missing", info);
			assertNotNull(name + " has no locations", info.locations);
			assertFalse(name + " has no locations", info.locations.isEmpty());
		}
		for (Map.Entry<String, PrepData.TaskInfo> task : data.allTasks().entrySet())
		{
			if (task.getValue().locations == null)
			{
				continue;
			}
			for (String location : task.getValue().locations)
			{
				List<PrepData.Teleport> teleports = data.locations().get(location);
				assertNotNull(task.getKey() + " lists unknown location " + location, teleports);
				assertFalse(location + " has no teleports", teleports.isEmpty());
				for (PrepData.Teleport t : teleports)
				{
					assertNotNull(location + " has a teleport without text", t.text);
					assertFalse(location + " has a teleport without text", t.text.trim().isEmpty());
				}
			}
		}
		// a reported location is matched by substring, so no key may contain another
		for (String a : data.locations().keySet())
		{
			for (String b : data.locations().keySet())
			{
				assertTrue(a + " overlaps " + b, a.equals(b)
					|| !b.toLowerCase(java.util.Locale.ROOT).contains(a.toLowerCase(java.util.Locale.ROOT)));
			}
		}
	}

	@Test
	public void lootToolsFollowTheMonstersDrops()
	{
		// abyssal demons drop herbs and items worth alching often, demonic ashes every kill
		OwnedItems bank = owned("Herb sack", 1, "Rune pouch", 1, "Ash sanctifier", 1, "Seed box", 1, "Shark", 100);
		PrepPlan plan = planner.plan("Abyssal demons", null, AttackType.SLASH, Collections.emptyList(), bank, 0, 0,
			true, "Abyssal demon", PrepPlanner.COMMON_DROP_KILLS);
		List<String> names = new java.util.ArrayList<>();
		for (PrepPlan.InventoryLine l : plan.getInventory())
		{
			names.add(l.getName());
		}
		assertTrue(names.contains("Herb sack"));
		assertTrue(names.contains("Rune pouch"));
		assertTrue(names.contains("Ash sanctifier"));
		// abyssal demons don't drop seeds
		assertFalse(names.contains("Seed box"));
		assertEquals(PrepPlanner.INVENTORY_SLOTS, plan.getSlotsUsed());
	}

	@Test
	public void rareLootOnlyWithAllDrops()
	{
		Map<String, Integer> drops = data.drops("Rockslug");
		Integer alch = drops.get("alchables");
		assertNotNull(alch);
		assertTrue("test needs a rare loot type", alch > PrepPlanner.COMMON_DROP_KILLS);
		OwnedItems bank = owned("Rune pouch", 1);
		PrepPlan common = planner.plan("Rockslugs", null, AttackType.RANGED, Collections.emptyList(), bank, 0, 0,
			false, "Rockslug", PrepPlanner.COMMON_DROP_KILLS);
		PrepPlan all = planner.plan("Rockslugs", null, AttackType.RANGED, Collections.emptyList(), bank, 0, 0,
			false, "Rockslug", Integer.MAX_VALUE);
		PrepPlan off = planner.plan("Rockslugs", null, AttackType.RANGED, Collections.emptyList(), bank, 0, 0,
			false, "Rockslug", 0);
		assertTrue(common.getInventory().stream().noneMatch(l -> l.getName().equals("Rune pouch")));
		assertTrue(all.getInventory().stream().anyMatch(l -> l.getName().equals("Rune pouch")));
		assertTrue(off.getInventory().stream().noneMatch(l -> l.getName().equals("Rune pouch")));
	}

	@Test
	public void natureRunesWhenThereIsNoPouch()
	{
		PrepPlan plan = planner.plan("Gargoyles", null, AttackType.CRUSH, Collections.emptyList(),
			owned("Nature rune", 500), 0, 0, false, "Gargoyle", PrepPlanner.COMMON_DROP_KILLS);
		assertTrue(plan.getInventory().stream().anyMatch(l -> l.getName().equals("Nature rune")));
	}

	@Test
	public void dropNamesAreCaseInsensitive()
	{
		assertFalse(data.drops("Skeletal Wyvern").isEmpty());
		assertFalse(data.drops("skeletal wyvern").isEmpty());
	}

	@Test
	public void cannonIsSuggestedWhenYouOwnOne()
	{
		OwnedItems bank = owned("Cannon base", 1, "Cannon stand", 1, "Cannon barrels", 1, "Cannon furnace", 1,
			"Granite cannonball", 2000);
		PrepPlan plan = planner.plan("Kalphites", null, AttackType.STAB, Collections.emptyList(), bank, 0, 0, false,
			null, 0);
		assertEquals(5, plan.getInventory().size());
		assertTrue(plan.getTips().get(0).contains("cannon"));
		// without a full cannon only the tip shows
		PrepPlan partial = planner.plan("Kalphites", null, AttackType.STAB, Collections.emptyList(),
			owned("Cannon base", 1, "Cannonball", 500), 0, 0, false, null, 0);
		assertTrue(partial.getInventory().isEmpty());
		assertEquals(1, partial.getTips().size());
	}

	@Test
	public void stackingTasksSuggestBursting()
	{
		PrepPlan plan = planner.plan("Dust devils", null, AttackType.MAGIC, Collections.emptyList(),
			owned("Rune pouch", 1), 0, 0, false, "Dust devil", 0);
		assertTrue(plan.getTips().stream().anyMatch(t -> t.contains("bursting")));
		assertTrue(plan.getInventory().stream().anyMatch(l -> l.getName().equals("Rune pouch")));
	}

	@Test
	public void runePouchIsNotListedTwice()
	{
		// abyssal demons stack and drop alchables: one pouch line, not two
		PrepPlan plan = planner.plan("Abyssal demons", null, AttackType.SLASH, Collections.emptyList(),
			owned("Rune pouch", 1), 0, 0, false, "Abyssal demon", PrepPlanner.COMMON_DROP_KILLS);
		assertEquals(1, plan.getInventory().stream().filter(l -> l.getName().equals("Rune pouch")).count());
	}

	@Test
	public void consumableTaskItemsScaleWithKillsLeft()
	{
		PrepPlan plan = planner.plan(PrepPlanner.Request.builder().task("Rockslugs").bestType(AttackType.RANGED)
			.owned(owned("Bag of salt", 23)).taskRemaining(40).build());
		PrepPlan.InventoryLine salt = plan.getInventory().get(0);
		assertEquals("Bag of salt", salt.getName());
		assertEquals(40, salt.getQuantity());
		assertTrue(salt.isStackable());
		assertEquals(1, plan.getSlotsUsed());
		assertTrue(plan.getNotes().stream().anyMatch(n -> n.contains("need 40") && n.contains("have 23")));
		// reusable task items stay at one
		PrepPlan gargoyles = planner.plan(PrepPlanner.Request.builder().task("Gargoyles").bestType(AttackType.CRUSH)
			.owned(owned("Rock hammer", 1)).taskRemaining(120).build());
		assertEquals(1, gargoyles.getInventory().get(0).getQuantity());
	}

	@Test
	public void specWeaponOnlyForHighHitpointMonsters()
	{
		OwnedItems bank = owned("Dragon warhammer", 1, "Bandos godsword", 1);
		PrepPlan tanky = planner.plan(PrepPlanner.Request.builder().task("Rune dragons").bestType(AttackType.STAB)
			.owned(bank).monsterHitpoints(330).suggestSpecWeapon(true).build());
		assertTrue(tanky.getInventory().stream().anyMatch(l -> l.getName().equals("Dragon warhammer")));
		PrepPlan small = planner.plan(PrepPlanner.Request.builder().task("Gargoyles").bestType(AttackType.CRUSH)
			.owned(bank).monsterHitpoints(105).suggestSpecWeapon(true).build());
		assertTrue(small.getInventory().stream().noneMatch(l -> l.getName().equals("Dragon warhammer")));
		// already wielding the warhammer: suggest the next one instead
		PrepPlan wielding = planner.plan(PrepPlanner.Request.builder().task("Rune dragons").bestType(AttackType.CRUSH)
			.owned(bank).monsterHitpoints(330).bestWeapon("Dragon warhammer").suggestSpecWeapon(true).build());
		assertTrue(wielding.getInventory().stream().anyMatch(l -> l.getName().equals("Bandos godsword")));
		assertTrue(wielding.getInventory().stream().noneMatch(l -> l.getName().equals("Dragon warhammer")));
	}

	@Test
	public void mothMixesMakeUpForMissingPrayerPotions()
	{
		PrepPlan both = planner.plan(PrepPlanner.Request.builder().task("Bloodveld").bestType(AttackType.SLASH)
			.owned(owned("Prayer potion(4)", 2, "Moonlight moth mix (2)", 20)).prayerPotions(5).build());
		assertEquals("Prayer potion", both.getInventory().get(0).getName());
		assertEquals(2, both.getInventory().get(0).getQuantity());
		assertEquals("Moonlight moth mix", both.getInventory().get(1).getName());
		assertEquals(6, both.getInventory().get(1).getQuantity());

		PrepPlan mixes = planner.plan(PrepPlanner.Request.builder().task("Bloodveld").bestType(AttackType.SLASH)
			.owned(owned("Moonlight moth mix (2)", 20)).prayerPotions(5).build());
		assertEquals(10, mixes.getInventory().get(0).getQuantity());

		PrepPlan none = planner.plan(PrepPlanner.Request.builder().task("Bloodveld").bestType(AttackType.SLASH)
			.owned(owned()).prayerPotions(5).combatPotions(1).build());
		assertTrue(none.getNotes().stream().anyMatch(n -> n.contains("No prayer potions")));
		assertTrue(none.getNotes().stream().anyMatch(n -> n.contains("No slash boosting potion")));
	}

	@Test
	public void prayerThenToolsThenTeleportsThenFood()
	{
		OwnedItems bank = owned("Prayer potion(4)", 10, "Bonecrusher", 1, "Slayer ring (8)", 1, "Anglerfish", 100);
		PrepPlan plan = planner.plan(PrepPlanner.Request.builder().task("Bloodveld").bestType(AttackType.SLASH)
			.owned(bank).prayerPotions(5).fillFood(true).monsterName("Bloodveld").lootMaxKills(Integer.MAX_VALUE).build());
		List<PrepPlan.InventoryLine> inv = plan.getInventory();
		assertEquals("Prayer potion", inv.get(0).getName());
		assertEquals(5, inv.get(0).getQuantity());
		assertEquals("Bonecrusher", inv.get(1).getName());
		assertEquals("Slayer ring", inv.get(2).getName());
		assertEquals("Anglerfish", inv.get(3).getName());
		assertEquals(21, inv.get(3).getQuantity());
		assertEquals(PrepPlanner.INVENTORY_SLOTS, plan.getSlotsUsed());
	}

	@Test
	public void karambwansForHardHittingTasks()
	{
		OwnedItems bank = owned("Anglerfish", 100, "Cooked karambwan", 50);
		PrepPlan rune = planner.plan(PrepPlanner.Request.builder().task("Rune dragons").bestType(AttackType.STAB)
			.owned(bank).fillFood(true).build());
		assertEquals("Cooked karambwan", rune.getInventory().get(0).getName());
		assertEquals(4, rune.getInventory().get(0).getQuantity());
		assertEquals("Anglerfish", rune.getInventory().get(1).getName());
		assertEquals(24, rune.getInventory().get(1).getQuantity());

		PrepPlan gargoyles = planner.plan(PrepPlanner.Request.builder().task("Gargoyles").bestType(AttackType.CRUSH)
			.owned(bank).monsterHitpoints(105).fillFood(true).build());
		assertTrue(gargoyles.getInventory().stream().noneMatch(l -> l.getName().equals("Cooked karambwan")));

		// karambwans as the best food too: one line, not two
		PrepPlan onlyKarambwans = planner.plan(PrepPlanner.Request.builder().task("Rune dragons").bestType(AttackType.STAB)
			.owned(owned("Cooked karambwan", 50)).fillFood(true).build());
		assertEquals(1, onlyKarambwans.getInventory().size());
		assertEquals(28, onlyKarambwans.getInventory().get(0).getQuantity());
	}

	@Test
	public void wornTeleportsAreATipNotASlot()
	{
		PrepPlan plan = planner.plan(PrepPlanner.Request.builder().task("Hydras").location("Karuulm Slayer Dungeon")
			.bestType(AttackType.SLASH).owned(owned("Rada's blessing 4", 1)).build());
		assertTrue(plan.getInventory().stream().noneMatch(l -> l.getName().startsWith("Rada's")));
		assertTrue(plan.getTips().stream().anyMatch(t -> t.contains("Rada's blessing")));
	}

	@Test
	public void prayerRestoreSettingPicksTheOrder()
	{
		OwnedItems bank = owned("Prayer potion(4)", 20, "Moonlight moth mix (2)", 6);
		PrepPlan mixesFirst = planner.plan(PrepPlanner.Request.builder().task("Bloodveld").bestType(AttackType.SLASH)
			.owned(bank).prayerPotions(5).prayerRestore(PrayerRestore.MIXES_FIRST).build());
		// 6 mixes cover 3 potions, so 2 prayer potions top it up
		assertEquals("Moonlight moth mix", mixesFirst.getInventory().get(0).getName());
		assertEquals(6, mixesFirst.getInventory().get(0).getQuantity());
		assertEquals("Prayer potion", mixesFirst.getInventory().get(1).getName());
		assertEquals(2, mixesFirst.getInventory().get(1).getQuantity());

		PrepPlan potionsFirst = planner.plan(PrepPlanner.Request.builder().task("Bloodveld").bestType(AttackType.SLASH)
			.owned(bank).prayerPotions(5).build());
		assertEquals("Prayer potion", potionsFirst.getInventory().get(0).getName());
		assertEquals(5, potionsFirst.getInventory().get(0).getQuantity());
		assertTrue(potionsFirst.getInventory().stream().noneMatch(l -> l.getName().startsWith("Moonlight")));

		PrepPlan potionsOnly = planner.plan(PrepPlanner.Request.builder().task("Bloodveld").bestType(AttackType.SLASH)
			.owned(owned("Moonlight moth mix (2)", 20)).prayerPotions(5).prayerRestore(PrayerRestore.POTIONS_ONLY).build());
		assertTrue(potionsOnly.getInventory().stream().noneMatch(l -> l.getName().startsWith("Moonlight")));
		assertTrue(potionsOnly.getNotes().stream().anyMatch(n -> n.contains("No prayer potions or super restores")));
	}

	@Test
	public void everySlayerMasterHasTeleports()
	{
		for (SlayerMaster master : SlayerMaster.values())
		{
			if (master != SlayerMaster.NONE)
			{
				assertNotNull(master + " has no teleports", data.masters().get(master.getName()));
			}
		}
	}

	@Test
	public void teleportBackToTheSlayerMaster()
	{
		PrepPlan mortimer = planner.plan(PrepPlanner.Request.builder().task("Bloodveld").bestType(AttackType.SLASH)
			.owned(owned("Necklace of passage(5)", 3)).slayerMaster("Mortimer").build());
		assertTrue(mortimer.getInventory().stream()
			.anyMatch(l -> l.getName().equals("Necklace of passage") && l.getNote().contains("Mortimer")));
		assertTrue(mortimer.getTeleports().stream().anyMatch(t -> t.getLocation().equals("Back to Mortimer")));

		// the slayer ring already in the plan for the task also goes to Mortimer: no second item
		PrepPlan ring = planner.plan(PrepPlanner.Request.builder().task("Bloodveld").bestType(AttackType.SLASH)
			.owned(owned("Slayer ring (8)", 1, "Necklace of passage(5)", 3)).slayerMaster("Mortimer").build());
		assertTrue(ring.getInventory().stream().anyMatch(l -> l.getName().equals("Slayer ring")));
		assertTrue(ring.getInventory().stream().noneMatch(l -> l.getName().equals("Necklace of passage")));

		// worn Karamja gloves need no slot; an uncharged glory can't teleport
		PrepPlan duradel = planner.plan(PrepPlanner.Request.builder().task("Bloodveld").bestType(AttackType.SLASH)
			.owned(owned("Karamja gloves 4", 1)).slayerMaster("Duradel").build());
		assertTrue(duradel.getInventory().isEmpty());
		assertTrue(duradel.getTips().stream().anyMatch(t -> t.contains("Karamja gloves 4")));
		PrepPlan vannaka = planner.plan(PrepPlanner.Request.builder().task("Bloodveld").bestType(AttackType.SLASH)
			.owned(owned("Amulet of glory", 1)).slayerMaster("Vannaka").build());
		assertTrue(vannaka.getInventory().isEmpty());

		PrepPlan none = planner.plan(PrepPlanner.Request.builder().task("Bloodveld").bestType(AttackType.SLASH)
			.owned(owned("Necklace of passage(5)", 3)).build());
		assertTrue(none.getInventory().isEmpty());
	}

	@Test
	public void safespotsFollowTheReportedLocation()
	{
		PrepPlan any = planner.plan(PrepPlanner.Request.builder().task("Bloodveld").bestType(AttackType.SLASH)
			.owned(owned()).build());
		assertEquals(4, any.getSafespots().size());
		PrepPlan tower = planner.plan(PrepPlanner.Request.builder().task("Bloodveld").location("Slayer Tower")
			.bestType(AttackType.SLASH).owned(owned()).build());
		assertEquals(1, tower.getSafespots().size());
		assertEquals("Slayer Tower", tower.getSafespots().get(0).getLocation());
	}

	@Test
	public void noSafespotsForBosses()
	{
		for (String boss : new String[]{"the alchemical hydra", "vorkath", "zulrah", "araxxor", "the king black dragon",
			"the thermonuclear smoke devil"})
		{
			assertTrue(boss, data.allTasks().get(boss).safespots == null);
		}
	}

	@Test
	public void lureWeaponWhenBurstingIsBest()
	{
		OwnedItems bank = owned("Rune pouch", 1, "Iron knife", 200, "Bronze dart", 50);
		PrepPlan burst = planner.plan(PrepPlanner.Request.builder().task("Dust devils").bestType(AttackType.BURST)
			.owned(bank).build());
		PrepPlan.InventoryLine knives = burst.getInventory().stream().filter(l -> l.getName().equals("Iron knife"))
			.findFirst().orElse(null);
		assertNotNull(knives);
		assertTrue(knives.isStackable());
		assertEquals(200, knives.getQuantity());
		assertTrue(burst.getInventory().stream().noneMatch(l -> l.getName().equals("Bronze dart")));

		PrepPlan melee = planner.plan(PrepPlanner.Request.builder().task("Dust devils").bestType(AttackType.SLASH)
			.owned(bank).build());
		assertTrue(melee.getInventory().stream().noneMatch(l -> l.getName().equals("Iron knife")));
	}

	@Test
	public void burstUsesMagicPotions()
	{
		PrepPlan plan = planner.plan(PrepPlanner.Request.builder().task("Dust devils").bestType(AttackType.BURST)
			.owned(owned("Divine magic potion(4)", 2, "Super combat potion(4)", 2)).combatPotions(1).build());
		assertTrue(plan.getInventory().stream().anyMatch(l -> l.getName().equals("Divine magic potion")));
		assertTrue(plan.getInventory().stream().noneMatch(l -> l.getName().startsWith("Super combat")));
	}

	@Test
	public void warpedCreaturesHaveTeleports()
	{
		PrepPlan plan = planner.plan(PrepPlanner.Request.builder().task("Warped Creatures").bestType(AttackType.SLASH)
			.owned(owned("Ring of wealth (5)", 1)).build());
		assertTrue(plan.getTeleports().stream().anyMatch(t -> t.getLocation().equals("Poison Waste Dungeon")));
		assertTrue(plan.getInventory().stream().anyMatch(l -> l.getName().equals("Ring of wealth")));
		// the crystal chime is required, and flagged when missing
		assertEquals("Crystal chime", plan.getInventory().get(0).getName());
		assertTrue(plan.getInventory().get(0).isRequired());
		assertTrue(plan.getNotes().stream().anyMatch(n -> n.contains("Missing Crystal chime")));
		// an uncharged ring can't teleport
		PrepPlan uncharged = planner.plan(PrepPlanner.Request.builder().task("Warped Creatures").bestType(AttackType.SLASH)
			.owned(owned("Ring of wealth", 1, "Crystal chime", 1)).build());
		assertTrue(uncharged.getInventory().stream().noneMatch(l -> l.getName().equals("Ring of wealth")));
	}

	@Test
	public void karuulmNeedsBootsForAnyTask()
	{
		List<PrepPlan.Requirement> konar = planner.requirements("Fire giants", "Karuulm Slayer Dungeon", owned(),
			Collections.emptySet());
		assertEquals(1, konar.size());
		assertEquals(EquipmentSlot.FEET, konar.get(0).getSlot());
		assertFalse(konar.get(0).isOwned());
		assertTrue(planner.requirements("Fire giants", null, owned(), Collections.emptySet()).isEmpty());
		// drakes already need boots: not asked for twice
		assertEquals(1, planner.requirements("Drakes", "Karuulm Slayer Dungeon", owned(), Collections.emptySet()).size());
	}

	@Test
	public void darkCavesNeedALight()
	{
		// cave horrors are only in Mos Le'Harmless, so the light is needed without a reported location
		PrepPlan none = planner.plan(PrepPlanner.Request.builder().task("Cave horrors").bestType(AttackType.RANGED)
			.owned(owned()).build());
		assertTrue(none.getNotes().stream().anyMatch(n -> n.startsWith("Missing Bullseye lantern")));
		PrepPlan lit = planner.plan(PrepPlanner.Request.builder().task("Cave horrors").bestType(AttackType.RANGED)
			.owned(owned("Oil lantern", 1)).build());
		assertTrue(lit.getInventory().stream().anyMatch(l -> l.getName().equals("Oil lantern") && l.isRequired()));
	}

	@Test
	public void brimhavenNeedsAnAxeAndCoins()
	{
		PrepPlan plan = planner.plan(PrepPlanner.Request.builder().task("Red dragons").location("Brimhaven Dungeon")
			.bestType(AttackType.SLASH).owned(owned("Rune axe", 1)).build());
		assertTrue(plan.getInventory().stream().anyMatch(l -> l.getName().equals("Rune axe")));
		assertTrue(plan.getTips().stream().anyMatch(t -> t.contains("875 coins")));
	}

	@Test
	public void fungicideRefillsAreCappedAndOptional()
	{
		PrepPlan plan = planner.plan(PrepPlanner.Request.builder().task("Mutated zygomites").bestType(AttackType.SLASH)
			.owned(owned("Fungicide spray 10", 1, "Fungicide", 30)).taskRemaining(150).build());
		PrepPlan.InventoryLine refills = plan.getInventory().stream().filter(l -> l.getName().equals("Fungicide"))
			.findFirst().orElse(null);
		assertNotNull(refills);
		assertEquals(4, refills.getQuantity());
		assertFalse(refills.isRequired());
		PrepPlan without = planner.plan(PrepPlanner.Request.builder().task("Mutated zygomites").bestType(AttackType.SLASH)
			.owned(owned("Fungicide spray 10", 1)).taskRemaining(150).build());
		assertTrue(without.getInventory().stream().noneMatch(l -> l.getName().equals("Fungicide")));
		assertTrue(without.getNotes().stream().noneMatch(n -> n.contains("Fungicide")));
	}

	@Test
	public void taskTipsAreShown()
	{
		PrepPlan plan = planner.plan(PrepPlanner.Request.builder().task("Rune dragons").bestType(AttackType.STAB)
			.owned(owned()).build());
		assertTrue(plan.getTips().stream().anyMatch(t -> t.contains("Insulated boots")));
	}

	@Test
	public void bossTasksGetEntryItemsButNoSafespots()
	{
		PrepPlan barrows = planner.plan(PrepPlanner.Request.builder().task("Barrows Brothers").bestType(AttackType.MAGIC)
			.owned(owned("Spade", 1, "Barrows teleport", 20)).build());
		assertTrue(barrows.getInventory().stream().anyMatch(l -> l.getName().equals("Spade") && l.isRequired()));
		assertTrue(barrows.getInventory().stream().anyMatch(l -> l.getName().equals("Barrows teleport") && l.isStackable()));

		PrepPlan mole = planner.plan(PrepPlanner.Request.builder().task("The Giant Mole").bestType(AttackType.SLASH)
			.owned(owned()).build());
		assertTrue(mole.getNotes().stream().anyMatch(n -> n.startsWith("Missing Spade")));
		assertTrue(mole.getNotes().stream().anyMatch(n -> n.startsWith("Missing Bullseye lantern")));

		PrepPlan graardor = planner.plan(PrepPlanner.Request.builder().task("General Graardor").bestType(AttackType.CRUSH)
			.owned(owned("Dragon warhammer", 1)).build());
		assertTrue(graardor.getInventory().stream().anyMatch(l -> l.getName().equals("Dragon warhammer")));

		PrepPlan venenatis = planner.plan(PrepPlanner.Request.builder().task("Venenatis").bestType(AttackType.RANGED)
			.owned(owned()).build());
		assertTrue(venenatis.getTips().stream().anyMatch(t -> t.contains("Wilderness")));

		for (String boss : new String[]{"the abyssal sire", "cerberus", "zulrah", "vorkath", "the giant mole",
			"barrows brothers", "general graardor", "venenatis", "sarachnis", "the leviathan"})
		{
			assertTrue(boss, data.allTasks().get(boss).safespots == null);
			assertNotNull(boss, data.allTasks().get(boss).locations);
		}
	}

	@Test
	public void pickedFoodComesFirst()
	{
		OwnedItems bank = owned("Anglerfish", 10, "Shark", 100, "Monkfish", 5);
		PrepPlan best = planner.plan(PrepPlanner.Request.builder().task("Bloodveld").bestType(AttackType.SLASH)
			.owned(bank).fillFood(true).build());
		assertEquals("Anglerfish", best.getInventory().get(0).getName());
		assertEquals(3, best.getFoodOptions().size());
		assertEquals("Anglerfish", best.getFoodOptions().get(0).getName());

		PrepPlan sharks = planner.plan(PrepPlanner.Request.builder().task("Bloodveld").bestType(AttackType.SLASH)
			.owned(bank).fillFood(true).preferredFood("Shark").build());
		assertEquals("Shark", sharks.getInventory().get(0).getName());
		assertEquals(28, sharks.getInventory().get(0).getQuantity());

		// a picked food you don't own is ignored
		PrepPlan none = planner.plan(PrepPlanner.Request.builder().task("Bloodveld").bestType(AttackType.SLASH)
			.owned(bank).fillFood(true).preferredFood("Manta ray").build());
		assertEquals("Anglerfish", none.getInventory().get(0).getName());
	}
}
