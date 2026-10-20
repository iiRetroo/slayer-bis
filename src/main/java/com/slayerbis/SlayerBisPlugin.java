package com.slayerbis;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.slayerbis.calc.CalcResult;
import com.slayerbis.calc.DpsCalculator;
import com.slayerbis.calc.Optimizer;
import com.slayerbis.calc.PlayerSetup;
import com.slayerbis.calc.StyleResult;
import com.slayerbis.data.AttackType;
import com.slayerbis.data.EquipmentSlot;
import com.slayerbis.data.GameData;
import com.slayerbis.data.GearItem;
import com.slayerbis.data.Monster;
import com.slayerbis.data.TaskMonsterResolver;
import com.slayerbis.prep.OwnedItems;
import com.slayerbis.prep.PrepData;
import com.slayerbis.prep.PrepPlan;
import com.slayerbis.prep.PrepPlanner;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.slayer.SlayerPlugin;
import net.runelite.client.plugins.slayer.SlayerPluginService;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

/**
 * Reads the bank when it is opened, works out the highest-DPS gear for the current Slayer task,
 * suggests an inventory and teleports, and shows it all in a side panel and as bank highlights.
 * This plugin only reads game state; it never equips, withdraws or otherwise acts on items.
 */
@Slf4j
@PluginDescriptor(
	name = "SlayerBIS",
	description = "Finds your highest-DPS gear in your bank for your Slayer task and highlights it",
	tags = {"dps", "gear", "bank", "slayer", "bis", "best in slot", "optimizer", "calculator", "inventory"}
)
@PluginDependency(SlayerPlugin.class)
public class SlayerBisPlugin extends Plugin
{
	private static final String SLAYER_CONFIG_GROUP = "slayer";
	private static final String SLAYER_TASK_KEY = "taskName";
	private static final String SLAYER_LOCATION_KEY = "taskLocation";
	private static final int SPELLBOOK_ANCIENT = 1;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private SlayerBisConfig config;

	@Inject
	private ItemManager itemManager;

	@Inject
	private SlayerPluginService slayerPluginService;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private BankHighlightOverlay overlay;

	@Inject
	private Gson gson;

	private SlayerBisPanel panel;
	private NavigationButton navButton;
	private ExecutorService executor;
	private Future<?> running;
	/** Cancel flag of the search in progress; set to true to make it stop at its next check. */
	private AtomicBoolean runningCancel;

	private volatile GameData gameData;
	private volatile TaskMonsterResolver resolver;
	private volatile PrepPlanner planner;

	// Owned items by canonical item id and quantity, per container. Only touched on the client thread.
	private Map<Integer, Integer> bankItems;
	private final Map<Integer, Integer> wornItems = new HashMap<>();
	private final Map<Integer, Integer> inventoryItems = new HashMap<>();
	/** Item names, looked up once per id on the client thread. */
	private final Map<Integer, String> itemNames = new HashMap<>();
	/** Total owned quantity per item at the last calculation, used to skip redundant work. */
	private Map<Integer, Integer> lastSignature = Collections.emptyMap();
	/** Owned equippable item ids at the last calculation. */
	private Set<Integer> lastGearSignature = Collections.emptySet();
	private String lastTask;
	/** Monster picked in the panel, or null to choose automatically. Reset when the task changes. */
	private Integer selectedMonsterId;

	/** Results of the last calculation; replaced wholesale, read from the overlay and Swing threads. */
	private volatile OptimizationOutcome outcome;
	private volatile AttackType selectedType;
	/** Sorted bank item ids to outline; replaced wholesale so the overlay never sees a partial update. */
	private volatile int[] highlightIds = new int[0];
	private volatile int[] supplyHighlightIds = new int[0];
	/** Cached from config so the overlay does not hit the config manager every frame; null when disabled. */
	private volatile Color highlightColor;
	private volatile Color supplyHighlightColor;

	@Override
	protected void startUp()
	{
		executor = Executors.newSingleThreadExecutor(r ->
		{
			Thread t = new Thread(r, "slayerbis");
			t.setDaemon(true);
			// the search is background work; let the game client have the CPU first
			t.setPriority(Thread.MIN_PRIORITY);
			return t;
		});
		executor.submit(() ->
		{
			try
			{
				GameData data = GameData.load(gson);
				planner = new PrepPlanner(PrepData.load(gson));
				resolver = new TaskMonsterResolver(data);
				gameData = data;
				// the bank may have been opened while the data was loading
				clientThread.invokeLater(() -> recalculate(Trigger.FORCE));
			}
			catch (Exception e)
			{
				log.warn("Unable to load SlayerBIS data", e);
			}
		});

		panel = new SlayerBisPanel(this, itemManager);
		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "panel_icon.png");
		navButton = NavigationButton.builder()
			.tooltip("SlayerBIS")
			.icon(icon)
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		updateHighlightColors();
		overlayManager.add(overlay);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		clientToolbar.removeNavigation(navButton);
		cancelRunning();
		executor.shutdownNow();
		executor = null;
		running = null;
		runningCancel = null;
		gameData = null;
		resolver = null;
		planner = null;
		bankItems = null;
		wornItems.clear();
		inventoryItems.clear();
		itemNames.clear();
		lastSignature = Collections.emptyMap();
		lastGearSignature = Collections.emptySet();
		lastTask = null;
		selectedMonsterId = null;
		outcome = null;
		selectedType = null;
		highlightIds = new int[0];
		supplyHighlightIds = new int[0];
		panel = null;
	}

	@Provides
	SlayerBisConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SlayerBisConfig.class);
	}

	int[] getHighlightIds()
	{
		return highlightIds;
	}

	int[] getSupplyHighlightIds()
	{
		return supplyHighlightIds;
	}

	Color getHighlightColor()
	{
		return highlightColor;
	}

	Color getSupplyHighlightColor()
	{
		return supplyHighlightColor;
	}

	private void updateHighlightColors()
	{
		highlightColor = config.highlightBank() ? config.highlightColor() : null;
		supplyHighlightColor = config.highlightBank() && config.highlightSupplies() ? config.supplyHighlightColor() : null;
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int id = event.getContainerId();
		if (id == InventoryID.BANK)
		{
			bankItems = readItems(event.getItemContainer());
			recalculate(Trigger.BANK);
		}
		else if (id == InventoryID.WORN)
		{
			wornItems.clear();
			wornItems.putAll(readItems(event.getItemContainer()));
			if (bankItems != null)
			{
				recalculate(Trigger.GEAR_ONLY);
			}
		}
		else if (id == InventoryID.INV)
		{
			inventoryItems.clear();
			inventoryItems.putAll(readItems(event.getItemContainer()));
			if (bankItems != null)
			{
				recalculate(Trigger.GEAR_ONLY);
			}
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (SLAYER_CONFIG_GROUP.equals(event.getGroup())
			&& (SLAYER_TASK_KEY.equals(event.getKey()) || SLAYER_LOCATION_KEY.equals(event.getKey())))
		{
			clientThread.invokeLater(() -> recalculate(Trigger.GEAR_ONLY));
		}
		else if (SlayerBisConfig.GROUP.equals(event.getGroup()))
		{
			String key = event.getKey();
			if ("highlightColor".equals(key) || "highlightBank".equals(key)
				|| "highlightSupplies".equals(key) || "supplyHighlightColor".equals(key))
			{
				updateHighlightColors();
			}
			else
			{
				clientThread.invokeLater(() -> recalculate(Trigger.FORCE));
			}
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			// a different account may log in next, so forget the previous bank
			bankItems = null;
			lastSignature = Collections.emptyMap();
			lastGearSignature = Collections.emptySet();
			selectedMonsterId = null;
			outcome = null;
			highlightIds = new int[0];
			supplyHighlightIds = new int[0];
			SwingUtilities.invokeLater(() ->
			{
				if (panel != null)
				{
					panel.reset("Open your bank to scan your gear.");
				}
			});
		}
	}

	/**
	 * Canonical item id to quantity. Runs on the client thread; also caches item names.
	 */
	private Map<Integer, Integer> readItems(ItemContainer container)
	{
		Map<Integer, Integer> items = new HashMap<>();
		if (container == null)
		{
			return items;
		}
		for (Item item : container.getItems())
		{
			if (item.getId() > 0 && item.getQuantity() > 0)
			{
				int id = itemManager.canonicalize(item.getId());
				items.merge(id, item.getQuantity(), Integer::sum);
				itemNames.computeIfAbsent(id, i -> itemManager.getItemComposition(i).getName());
			}
		}
		return items;
	}

	/**
	 * Called from the panel's button.
	 */
	void requestRecalculate()
	{
		clientThread.invokeLater(() -> recalculate(Trigger.FORCE));
	}

	void selectType(AttackType type)
	{
		selectedType = type;
		updateHighlights();
	}

	/**
	 * Called from the panel's monster picker; null goes back to choosing automatically.
	 */
	void selectMonster(Integer monsterId)
	{
		clientThread.invokeLater(() ->
		{
			selectedMonsterId = monsterId;
			recalculate(Trigger.FORCE);
		});
	}

	/**
	 * What caused a recalculation, which decides what counts as a change worth a new search.
	 */
	private enum Trigger
	{
		/** Always search again (settings, the panel's button, a new monster pick). */
		FORCE,
		/** The bank changed: search again if anything you own was added or used up. */
		BANK,
		/**
		 * Worn items, inventory or task changed: only search again if the gear you own (or the task)
		 * changed, so eating food or drinking potions in combat does not start a new search.
		 */
		GEAR_ONLY
	}

	/**
	 * Snapshots everything needed on the client thread, then runs the search on the worker thread.
	 */
	private void recalculate(Trigger trigger)
	{
		if (executor == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		GameData data = gameData;
		TaskMonsterResolver res = resolver;
		PrepPlanner prep = planner;
		if (bankItems == null || data == null || res == null || prep == null)
		{
			return;
		}

		// Total owned quantity by item, and the preferred source of each: worn first, then
		// inventory, then bank, so the bank is only highlighted for what is not already on you.
		Map<Integer, Integer> totals = new HashMap<>();
		Map<Integer, ItemSource> sources = new LinkedHashMap<>();
		addSource(wornItems, ItemSource.WORN, totals, sources);
		addSource(inventoryItems, ItemSource.INVENTORY, totals, sources);
		addSource(bankItems, ItemSource.BANK, totals, sources);

		String task = slayerPluginService.getTask();
		if (!Objects.equals(task, lastTask))
		{
			selectedMonsterId = null;
		}
		// Moving items between bank and inventory does not change the totals, so the plan stays
		// put while you withdraw.
		Set<Integer> gearSignature = new HashSet<>();
		for (int id : totals.keySet())
		{
			if (data.getItem(id) != null)
			{
				gearSignature.add(id);
			}
		}
		boolean sameTask = Objects.equals(task, lastTask);
		if (trigger == Trigger.BANK && sameTask && totals.equals(lastSignature))
		{
			return;
		}
		if (trigger == Trigger.GEAR_ONLY && sameTask && gearSignature.equals(lastGearSignature))
		{
			return;
		}
		lastSignature = totals;
		lastGearSignature = gearSignature;
		lastTask = task;

		Map<String, Integer> quantitiesByName = new HashMap<>();
		Map<String, Set<Integer>> bankIdsByName = new HashMap<>();
		Map<String, Integer> anyIdByName = new HashMap<>();
		for (Map.Entry<Integer, Integer> e : totals.entrySet())
		{
			String name = itemNames.get(e.getKey());
			if (name != null)
			{
				quantitiesByName.merge(name, e.getValue(), Integer::sum);
				anyIdByName.putIfAbsent(name, e.getKey());
				if (bankItems.containsKey(e.getKey()))
				{
					bankIdsByName.computeIfAbsent(name, n -> new HashSet<>()).add(e.getKey());
				}
			}
		}
		OwnedItems owned = OwnedItems.of(quantitiesByName, bankIdsByName, anyIdByName);

		Map<Integer, Optimizer.Candidate> candidates = new LinkedHashMap<>();
		// Uncharged, empty, inactive and broken items cannot be used as they are. Their working
		// versions are kept aside to show what charging or repairing them would give.
		Map<Integer, Optimizer.Candidate> chargeableById = new LinkedHashMap<>();
		for (Map.Entry<Integer, ItemSource> e : sources.entrySet())
		{
			ItemSource source = e.getValue();
			if ((source == ItemSource.WORN && !config.includeEquipped())
				|| (source == ItemSource.INVENTORY && !config.includeInventory()))
			{
				continue;
			}
			GearItem item = data.getItem(e.getKey());
			if (item == null)
			{
				continue;
			}
			if (!item.isUsable())
			{
				GearItem usable = data.findUsableVariant(item);
				if (usable != null)
				{
					chargeableById.putIfAbsent(usable.getId(), new Optimizer.Candidate(usable, e.getKey()));
				}
				continue;
			}
			candidates.putIfAbsent(item.getId(), new Optimizer.Candidate(item, e.getKey()));
		}
		List<Optimizer.Candidate> chargeable = new ArrayList<>();
		for (Optimizer.Candidate c : chargeableById.values())
		{
			// already owned in working form, so charging this copy changes nothing
			if (!candidates.containsKey(c.getItem().getId()))
			{
				chargeable.add(c);
			}
		}

		GearItem[] worn = new GearItem[EquipmentSlot.values().length];
		for (int id : wornItems.keySet())
		{
			GearItem item = data.getItem(id);
			if (item != null && item.isUsable())
			{
				worn[item.getSlot().ordinal()] = item;
			}
		}

		String location = slayerPluginService.getTaskLocation();
		List<Integer> targetIds = new ArrayList<>();
		for (NPC npc : slayerPluginService.getTargets())
		{
			targetIds.add(npc.getId());
		}

		PlayerSetup setup = buildSetup(task);
		Settings settings = new Settings(config.targetOverride(), selectedMonsterId, config.showPreparation(),
			config.prayerPotions(), config.combatPotions(), config.fillFood(), config.lootTools().getMaxKills());

		cancelRunning();
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.showMessage("Calculating...");
			}
		});

		List<Optimizer.Candidate> candidateList = new ArrayList<>(candidates.values());
		AtomicBoolean cancel = new AtomicBoolean();
		runningCancel = cancel;
		running = executor.submit(() -> runOptimisation(res, prep, cancel, candidateList, chargeable, sources, owned,
			worn, setup, task, location, targetIds, settings));
	}

	private static void addSource(Map<Integer, Integer> items, ItemSource source, Map<Integer, Integer> totals,
		Map<Integer, ItemSource> sources)
	{
		for (Map.Entry<Integer, Integer> e : items.entrySet())
		{
			totals.merge(e.getKey(), e.getValue(), Integer::sum);
			sources.putIfAbsent(e.getKey(), source);
		}
	}

	private void cancelRunning()
	{
		if (runningCancel != null)
		{
			runningCancel.set(true);
		}
		if (running != null)
		{
			running.cancel(false);
		}
	}

	/**
	 * Config values snapshotted on the client thread for the worker.
	 */
	@lombok.Value
	private static class Settings
	{
		String targetOverride;
		Integer selectedMonsterId;
		boolean showPreparation;
		int prayerPotions;
		int combatPotions;
		boolean fillFood;
		int lootMaxKills;
	}

	private PlayerSetup buildSetup(String task)
	{
		SlayerBisConfig.MeleeBoost melee = config.meleeBoost();
		SlayerBisConfig.RangedBoost ranged = config.rangedBoost();
		SlayerBisConfig.MagicBoost magic = config.magicBoost();

		boolean ancients;
		switch (config.spellbook())
		{
			case ANCIENT:
				ancients = true;
				break;
			case STANDARD:
				ancients = false;
				break;
			default:
				ancients = client.getVarbitValue(VarbitID.SPELLBOOK) == SPELLBOOK_ANCIENT;
				break;
		}

		return PlayerSetup.builder()
			.attack(boost(client.getRealSkillLevel(Skill.ATTACK), melee.getFlat(), melee.getPercent()))
			.strength(boost(client.getRealSkillLevel(Skill.STRENGTH), melee.getFlat(), melee.getPercent()))
			.ranged(boost(client.getRealSkillLevel(Skill.RANGED), ranged.getFlat(), ranged.getPercent()))
			.magic(boost(client.getRealSkillLevel(Skill.MAGIC), magic.getFlat(), magic.getPercent()))
			.hitpoints(client.getRealSkillLevel(Skill.HITPOINTS))
			.meleeAccuracyPrayer(config.meleePrayer().getAccuracy())
			.meleeStrengthPrayer(config.meleePrayer().getStrength())
			.rangedAccuracyPrayer(config.rangedPrayer().getAccuracy())
			.rangedStrengthPrayer(config.rangedPrayer().getStrength())
			.magicAccuracyPrayer(config.magicPrayer().getAccuracy())
			.magicDamagePrayer(config.magicPrayer().getDamage())
			.onSlayerTask(task != null && !task.isEmpty())
			.ancientSpellbook(ancients)
			.kandarinHardDiary(config.kandarinHard())
			.blowpipeDartStrength(config.blowpipeDart().getRangedStrength())
			.build();
	}

	private static int boost(int level, int flat, int percent)
	{
		return level + flat + level * percent / 100;
	}

	/**
	 * Runs on the worker thread.
	 */
	private void runOptimisation(TaskMonsterResolver res, PrepPlanner prep, AtomicBoolean cancel,
		List<Optimizer.Candidate> candidates, List<Optimizer.Candidate> chargeable, Map<Integer, ItemSource> sources,
		OwnedItems owned, GearItem[] worn, PlayerSetup setup, String task, String location, List<Integer> targetIds,
		Settings settings)
	{
		try
		{
			String override = settings.getTargetOverride();
			boolean hasOverride = override != null && !override.trim().isEmpty();
			String lookup = hasOverride ? override : task;
			List<Monster> choices = lookup == null ? Collections.emptyList() : res.candidates(lookup);

			Monster monster = null;
			String targetSource;
			if (settings.getSelectedMonsterId() != null)
			{
				for (Monster m : choices)
				{
					if (m.getId() == settings.getSelectedMonsterId())
					{
						monster = m;
					}
				}
			}
			if (monster != null)
			{
				targetSource = "your choice";
			}
			else if (hasOverride)
			{
				monster = res.fromName(override);
				targetSource = "target override";
			}
			else
			{
				monster = res.fromNpcIds(targetIds);
				targetSource = "nearby task monster";
				if (monster == null)
				{
					monster = res.fromName(task);
					targetSource = "Slayer task";
				}
			}
			if (monster == null)
			{
				String msg = hasOverride
					? "No monster called \"" + override.trim() + "\" was found."
					: task == null || task.isEmpty()
					? "No Slayer task. Get a task, or set a target override in the plugin settings."
					: "Could not find monster stats for task \"" + task + "\". Set a target override in the plugin settings.";
				SwingUtilities.invokeLater(() ->
				{
					if (panel != null)
					{
						panel.showMessage(msg);
					}
				});
				return;
			}
			if (!choices.contains(monster))
			{
				List<Monster> withCurrent = new ArrayList<>(choices);
				withCurrent.add(0, monster);
				choices = withCurrent;
			}

			// gear the task requires; only enforce what you own, and flag the rest
			Set<String> ownedGearNames = new HashSet<>();
			for (Optimizer.Candidate c : candidates)
			{
				ownedGearNames.add(c.getItem().getName());
			}
			List<PrepPlan.Requirement> requirements = prep.requirements(task, owned, ownedGearNames);
			Map<EquipmentSlot, Set<String>> required = new EnumMap<>(EquipmentSlot.class);
			for (PrepPlan.Requirement r : requirements)
			{
				if (r.isOwned())
				{
					required.put(r.getSlot(), new HashSet<>(r.getAccepted()));
				}
			}

			Optimizer optimizer = new Optimizer(setup, monster, cancel);
			Map<AttackType, StyleResult> results = new EnumMap<>(AttackType.class);
			for (AttackType type : AttackType.values())
			{
				results.put(type, optimizer.optimise(candidates, type, required));
			}

			Map<AttackType, OptimizationOutcome.ChargeHint> hints = chargeHints(optimizer, candidates, chargeable,
				results, required);

			OptimizationOutcome.CurrentGear current = currentGear(setup, monster, worn);

			StyleResult best = null;
			for (StyleResult r : results.values())
			{
				if (r.isValid() && (best == null || r.getResult().getDps() > best.getResult().getDps()))
				{
					best = r;
				}
			}
			PrepPlan plan = settings.isShowPreparation()
				? prep.plan(task, location, best == null ? null : best.getType(), requirements, owned,
				settings.getPrayerPotions(), settings.getCombatPotions(), settings.isFillFood(), monster.getName(),
				settings.getLootMaxKills())
				: new PrepPlan(requirements, List.of(), List.of(), List.of(), 0, Set.of());

			OptimizationOutcome done = new OptimizationOutcome(task, monster, targetSource, choices, results, sources,
				setup, candidates.size(), hints, current, plan);
			outcome = done;
			updateHighlights();
			SwingUtilities.invokeLater(() ->
			{
				if (panel != null)
				{
					panel.showOutcome(done, selectedType);
				}
			});
		}
		catch (CancellationException e)
		{
			log.debug("SlayerBIS search cancelled");
		}
		catch (Exception e)
		{
			log.warn("SlayerBIS search failed", e);
			SwingUtilities.invokeLater(() ->
			{
				if (panel != null)
				{
					panel.showMessage("Something went wrong while calculating. See the client log for details.");
				}
			});
		}
	}

	/**
	 * A second search as if the uncharged items were charged; reported only where it would win.
	 */
	private static Map<AttackType, OptimizationOutcome.ChargeHint> chargeHints(Optimizer optimizer,
		List<Optimizer.Candidate> candidates, List<Optimizer.Candidate> chargeable, Map<AttackType, StyleResult> results,
		Map<EquipmentSlot, Set<String>> required)
	{
		Map<AttackType, OptimizationOutcome.ChargeHint> hints = new EnumMap<>(AttackType.class);
		if (chargeable.isEmpty())
		{
			return hints;
		}
		Set<Optimizer.Candidate> extra = new HashSet<>(chargeable);
		List<Optimizer.Candidate> withCharged = new ArrayList<>(candidates);
		withCharged.addAll(chargeable);
		for (AttackType type : AttackType.values())
		{
			StyleResult charged = optimizer.optimise(withCharged, type, required);
			if (!charged.isValid() || charged.getResult().getDps() <= results.get(type).getResult().getDps())
			{
				continue;
			}
			List<String> needed = new ArrayList<>();
			for (Optimizer.Candidate c : charged.getGear())
			{
				if (c != null && extra.contains(c))
				{
					needed.add(c.getItem().getName());
				}
			}
			if (!needed.isEmpty())
			{
				hints.put(type, new OptimizationOutcome.ChargeHint(charged.getResult().getDps(), needed));
			}
		}
		return hints;
	}

	private static OptimizationOutcome.CurrentGear currentGear(PlayerSetup setup, Monster monster, GearItem[] worn)
	{
		DpsCalculator calculator = new DpsCalculator(setup, monster);
		OptimizationOutcome.CurrentGear best = null;
		for (AttackType type : AttackType.values())
		{
			CalcResult r = calculator.best(worn, type);
			if (r.getDps() > 0 && (best == null || r.getDps() > best.getResult().getDps()))
			{
				best = new OptimizationOutcome.CurrentGear(type, r);
			}
		}
		return best;
	}

	private void updateHighlights()
	{
		OptimizationOutcome o = outcome;
		if (o == null)
		{
			highlightIds = new int[0];
			supplyHighlightIds = new int[0];
			return;
		}
		supplyHighlightIds = sorted(o.getPrep().getSupplyBankIds());
		StyleResult r = o.resultFor(selectedType);
		if (r == null || !r.isValid())
		{
			highlightIds = new int[0];
			return;
		}
		Set<Integer> ids = new HashSet<>();
		for (Optimizer.Candidate c : r.getGear())
		{
			if (c != null && o.getSources().get(c.getOwnedItemId()) == ItemSource.BANK)
			{
				ids.add(c.getOwnedItemId());
			}
		}
		highlightIds = sorted(ids);
	}

	private static int[] sorted(Collection<Integer> ids)
	{
		int[] out = new int[ids.size()];
		int n = 0;
		for (int id : ids)
		{
			out[n++] = id;
		}
		Arrays.sort(out);
		return out;
	}
}
