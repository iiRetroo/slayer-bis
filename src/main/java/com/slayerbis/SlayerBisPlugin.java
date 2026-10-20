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
import com.slayerbis.prep.PrayerRestore;
import com.slayerbis.prep.PrepData;
import com.slayerbis.prep.PrepPlan;
import com.slayerbis.prep.PrepPlanner;
import com.slayerbis.prep.SlayerMaster;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
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
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.ScriptID;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemVariationMapping;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;
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
@PluginDependency(BankTagsPlugin.class)
public class SlayerBisPlugin extends Plugin
{
	private static final String SLAYER_CONFIG_GROUP = "slayer";
	private static final String SLAYER_TASK_KEY = "taskName";
	private static final String SLAYER_LOCATION_KEY = "taskLocation";
	private static final String SLAYER_AMOUNT_KEY = "amount";
	private static final int SPELLBOOK_ANCIENT = 1;
	/** Name of the temporary bank tag that shows the suggested gear and supplies. */
	private static final String BANK_TAG = "slayerbis";
	/** Per-account setting holding the remembered bank, as "itemId:quantity,..." */
	private static final String BANK_KEY = "bankItems";
	private static final String POTION_STORE_KEY = "potionStore";
	private static final int MAX_DOSES = 4;
	/** Items that give the Slayer helmet's on-task bonus, by canonical name. */
	private static final Set<String> SLAYER_HELMS = Set.of("Slayer helmet", "Slayer helmet (i)", "Black mask", "Black mask (i)");
	/** Helms that make you immune to poison and venom while charged. */
	private static final Set<String> SERPENTINE_HELMS = Set.of("Serpentine helm", "Tanzanite helm", "Magma helm");

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

	@Inject
	private ConfigManager configManager;

	@Inject
	private TagManager tagManager;

	@Inject
	private BankTagsService bankTagsService;

	@Inject
	private ChatMessageManager chatMessageManager;

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
	/** Potions in the bank's potion storage, as whole potions of their highest dose. */
	private Map<Integer, Integer> potionStoreItems = Collections.emptyMap();
	private String savedPotionStore;
	private boolean potionStoreQueued;
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
	/** The remembered bank as last saved, to avoid rewriting an unchanged value. */
	private String savedBank;
	private boolean bankOpen;
	/** What the panel was last told about the items on you, to only update it on change. */
	private ReadyState lastReady;
	/** The task the Slayer helmet reminder was last shown for, so it shows once per task. */
	private String remindedTask;

	/** Results of the last calculation; replaced wholesale, read from the overlay and Swing threads. */
	private volatile OptimizationOutcome outcome;
	private volatile AttackType selectedType;
	/** Ammo picked in the panel for the ranged set (owned item id), or null for the best. */
	private volatile Integer selectedAmmo;
	/** Main food picked in the panel, or null for the best owned. */
	private volatile String selectedFood;
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
		tagManager.registerTag(BANK_TAG, this::inBankTag);
		clientThread.invokeLater(this::loadSavedBank);
	}

	@Override
	protected void shutDown()
	{
		clientThread.invokeLater(() ->
		{
			if (BANK_TAG.equals(bankTagsService.getActiveTag()))
			{
				bankTagsService.closeBankTag();
			}
		});
		tagManager.unregisterTag(BANK_TAG);
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
		savedBank = null;
		bankOpen = false;
		lastReady = null;
		remindedTask = null;
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

	SlayerBisConfig.InventoryView inventoryView()
	{
		return config.inventoryView();
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
			saveBank();
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
			if (bankOpen)
			{
				// potions can be deposited straight into potion storage
				queuePotionStoreRead();
			}
			if (bankItems != null)
			{
				recalculate(Trigger.GEAR_ONLY);
			}
		}
		if (bankOpen)
		{
			updateReadiness();
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			bankOpen = true;
			queuePotionStoreRead();
			updateReadiness();
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			bankOpen = false;
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		int id = event.getVarpId();
		if (id == VarPlayerID.POTIONSTORE_BASE_VAR_1 || id == VarPlayerID.POTIONSTORE_BASE_VAR_2
			|| id == VarPlayerID.POTIONSTORE_BASE_VAR_3 || id == VarPlayerID.POTIONSTORE_BASE_VAR_4
			|| id == VarPlayerID.POTIONSTORE_BASE_VAR_5 || id == VarPlayerID.POTIONSTORE_BASE_VAR_6)
		{
			queuePotionStoreRead();
		}
	}

	/**
	 * Reads the potion storage once, later this tick, however many changes asked for it.
	 */
	private void queuePotionStoreRead()
	{
		if (!potionStoreQueued)
		{
			potionStoreQueued = true;
			clientThread.invokeLater(this::readPotionStore);
		}
	}

	/**
	 * Reads the potion storage (from Mastering Mixology) the way RuneLite's Bank Tags plugin does:
	 * the game's own scripts give the doses stored of each potion. Read-only.
	 */
	private void readPotionStore()
	{
		potionStoreQueued = false;
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		Map<Integer, Integer> items = new HashMap<>();
		for (int enumId : new int[]{EnumID.POTIONSTORE_POTIONS, EnumID.POTIONSTORE_UNFINISHED_POTIONS})
		{
			for (int potionEnumId : client.getEnum(enumId).getIntVals())
			{
				client.runScript(ScriptID.POTIONSTORE_DOSES, potionEnumId);
				int doses = client.getIntStack()[0];
				if (doses <= 0)
				{
					continue;
				}
				EnumComposition potion = client.getEnum(potionEnumId);
				for (int dose = MAX_DOSES; dose >= 1; dose--)
				{
					int itemId = potion.getIntValue(dose);
					if (itemId > 0)
					{
						int id = itemManager.canonicalize(itemId);
						items.merge(id, Math.max(1, doses / dose), Integer::sum);
						itemNames.computeIfAbsent(id, i -> itemManager.getItemComposition(i).getName());
						break;
					}
				}
			}
		}
		if (!items.equals(potionStoreItems))
		{
			potionStoreItems = items;
			saveBank();
			recalculate(Trigger.BANK);
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		clientThread.invokeLater(this::loadSavedBank);
	}

	/**
	 * Reminds the player, once per task, when they attack a task monster without a Slayer helmet
	 * or black mask on but own one. A local chat message only; nothing is sent to the server.
	 */
	@Subscribe
	public void onInteractingChanged(InteractingChanged event)
	{
		if (!config.helmetReminder() || event.getSource() != client.getLocalPlayer())
		{
			return;
		}
		Actor target = event.getTarget();
		String task = slayerPluginService.getTask();
		if (!(target instanceof NPC) || task == null || task.isEmpty() || task.equals(remindedTask)
			|| !slayerPluginService.getTargets().contains(target))
		{
			return;
		}
		GameData data = gameData;
		if (data == null || hasSlayerHelm(data, wornItems.keySet()) || !ownsSlayerHelm(data))
		{
			return;
		}
		remindedTask = task;
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(new ChatMessageBuilder()
				.append(ChatColorType.HIGHLIGHT).append("SlayerBIS: ")
				.append(ChatColorType.NORMAL).append("you're not wearing your Slayer helmet or black mask.")
				.build())
			.build());
	}

	private static boolean hasSlayerHelm(GameData data, Collection<Integer> ids)
	{
		for (int id : ids)
		{
			GearItem item = data.getItem(id);
			if (item != null && SLAYER_HELMS.contains(item.getName()))
			{
				return true;
			}
		}
		return false;
	}

	private boolean ownsSlayerHelm(GameData data)
	{
		return hasSlayerHelm(data, inventoryItems.keySet()) || (bankItems != null && hasSlayerHelm(data, bankItems.keySet()));
	}

	/**
	 * Called from the panel's "Show in bank" button: filters the bank to the suggested items, or
	 * goes back to the normal bank if they are already shown. Uses the Bank Tags plugin's own
	 * temporary tag view; saved tags and layouts are not changed.
	 */
	void toggleBankTag()
	{
		clientThread.invokeLater(() ->
		{
			if (BANK_TAG.equals(bankTagsService.getActiveTag()))
			{
				bankTagsService.closeBankTag();
			}
			else
			{
				bankTagsService.openBankTag(BANK_TAG, BankTagsService.OPTION_NO_LAYOUT);
			}
		});
	}

	private boolean inBankTag(int itemId)
	{
		return Arrays.binarySearch(highlightIds, itemId) >= 0 || Arrays.binarySearch(supplyHighlightIds, itemId) >= 0;
	}

	/**
	 * Saves the bank for this account so it is known before the bank is next opened. Stored in
	 * RuneLite's own per-account settings, like any other plugin setting.
	 */
	private void saveBank()
	{
		if (!config.rememberBank() || bankItems == null)
		{
			return;
		}
		String value = serialise(bankItems);
		if (!value.equals(savedBank))
		{
			configManager.setRSProfileConfiguration(SlayerBisConfig.GROUP, BANK_KEY, value);
			savedBank = value;
		}
		String potions = serialise(potionStoreItems);
		if (!potions.equals(savedPotionStore))
		{
			configManager.setRSProfileConfiguration(SlayerBisConfig.GROUP, POTION_STORE_KEY, potions);
			savedPotionStore = potions;
		}
	}

	private static String serialise(Map<Integer, Integer> items)
	{
		StringBuilder sb = new StringBuilder();
		items.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e ->
			sb.append(sb.length() == 0 ? "" : ",").append(e.getKey()).append(':').append(e.getValue()));
		return sb.toString();
	}

	private Map<Integer, Integer> deserialise(String value)
	{
		Map<Integer, Integer> items = new HashMap<>();
		if (value == null || value.isEmpty())
		{
			return items;
		}
		for (String entry : value.split(","))
		{
			int colon = entry.indexOf(':');
			try
			{
				int id = Integer.parseInt(entry.substring(0, colon));
				items.put(id, Integer.parseInt(entry.substring(colon + 1)));
				itemNames.computeIfAbsent(id, i -> itemManager.getItemComposition(i).getName());
			}
			catch (RuntimeException e)
			{
				log.debug("Skipping bad remembered bank entry {}", entry);
			}
		}
		return items;
	}

	/**
	 * Loads the remembered bank for the logged-in account, if the bank has not been read yet.
	 * Runs on the client thread.
	 */
	private void loadSavedBank()
	{
		if (!config.rememberBank() || bankItems != null || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		String value = configManager.getRSProfileConfiguration(SlayerBisConfig.GROUP, BANK_KEY);
		if (value == null || value.isEmpty())
		{
			return;
		}
		bankItems = deserialise(value);
		savedBank = value;
		if (potionStoreItems.isEmpty())
		{
			savedPotionStore = configManager.getRSProfileConfiguration(SlayerBisConfig.GROUP, POTION_STORE_KEY);
			potionStoreItems = deserialise(savedPotionStore);
		}
		recalculate(Trigger.FORCE);
	}

	/**
	 * Tells the panel which suggested items are already on you, so it can tick them off while you
	 * bank. Runs on the client thread; only updates the panel when something changed.
	 */
	private void updateReadiness()
	{
		Set<Integer> onPerson = new HashSet<>(wornItems.keySet());
		onPerson.addAll(inventoryItems.keySet());
		Map<String, Integer> inventoryByName = new HashMap<>();
		for (Map.Entry<Integer, Integer> e : inventoryItems.entrySet())
		{
			String name = itemNames.get(e.getKey());
			if (name != null)
			{
				inventoryByName.merge(name, e.getValue(), Integer::sum);
			}
		}
		ReadyState ready = new ReadyState(onPerson, inventoryByName);
		if (ready.equals(lastReady))
		{
			return;
		}
		lastReady = ready;
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.showReadiness(ready);
			}
		});
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (SLAYER_CONFIG_GROUP.equals(event.getGroup())
			&& (SLAYER_TASK_KEY.equals(event.getKey()) || SLAYER_LOCATION_KEY.equals(event.getKey())))
		{
			clientThread.invokeLater(() -> recalculate(Trigger.GEAR_ONLY));
		}
		else if (SLAYER_CONFIG_GROUP.equals(event.getGroup()) && SLAYER_AMOUNT_KEY.equals(event.getKey()))
		{
			// a kill: only the task time estimate changes
			clientThread.invokeLater(() ->
			{
				int remaining = slayerPluginService.getRemainingAmount();
				SwingUtilities.invokeLater(() ->
				{
					if (panel != null)
					{
						panel.showRemaining(remaining);
					}
				});
			});
		}
		else if (SlayerBisConfig.GROUP.equals(event.getGroup()))
		{
			String key = event.getKey();
			if ("highlightColor".equals(key) || "highlightBank".equals(key)
				|| "highlightSupplies".equals(key) || "supplyHighlightColor".equals(key))
			{
				updateHighlightColors();
			}
			else if ("inventoryView".equals(key))
			{
				// display only: redraw, no new search
				SwingUtilities.invokeLater(() ->
				{
					if (panel != null)
					{
						panel.redraw();
					}
				});
			}
			else if ("rememberBank".equals(key) || "helmetReminder".equals(key))
			{
				if (!config.rememberBank())
				{
					configManager.unsetRSProfileConfiguration(SlayerBisConfig.GROUP, BANK_KEY);
					configManager.unsetRSProfileConfiguration(SlayerBisConfig.GROUP, POTION_STORE_KEY);
					savedBank = null;
				}
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
			savedBank = null;
			potionStoreItems = Collections.emptyMap();
			savedPotionStore = null;
			lastReady = null;
			remindedTask = null;
			lastSignature = Collections.emptyMap();
			lastGearSignature = Collections.emptySet();
			selectedMonsterId = null;
			selectedAmmo = null;
			selectedFood = null;
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
	 * Called from the panel's ammo menu; null goes back to the best ammo.
	 */
	void selectAmmo(Integer ammoId)
	{
		selectedAmmo = ammoId;
		updateHighlights();
	}

	Integer getSelectedAmmo()
	{
		return selectedAmmo;
	}

	/**
	 * Called from the panel's food menu; null goes back to the best food. Re-plans the inventory.
	 */
	void selectFood(String food)
	{
		clientThread.invokeLater(() ->
		{
			selectedFood = food;
			recalculate(Trigger.FORCE);
		});
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
		addSource(potionStoreItems, ItemSource.BANK, totals, sources);

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
		if (!sameTask)
		{
			selectedAmmo = null;
			selectedFood = null;
		}
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
				if (bankItems.containsKey(e.getKey()) || potionStoreItems.containsKey(e.getKey()))
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
			config.prayerPotions(), config.prayerRestore(), config.combatPotions(), config.fillFood(), config.lootTools().getMaxKills(),
			slayerPluginService.getRemainingAmount(), config.specWeapon(), config.slayerMaster(), config.burstTargets(),
			config.preferSlayerHelmet(), selectedFood, config.serpentineHelm());

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
		PrayerRestore prayerRestore;
		int combatPotions;
		boolean fillFood;
		int lootMaxKills;
		int taskRemaining;
		boolean specWeapon;
		SlayerMaster slayerMaster;
		int burstTargets;
		int preferSlayerHelmet;
		String preferredFood;
		SlayerBisConfig.SerpentineHelm serpentineHelm;
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

			// a boss picked on a regular task (Cerberus on hellhounds) uses the boss's own preparation
			String prepTask = task;
			String prepLocation = location;
			String bossTask = TaskMonsterResolver.bossTask(monster.getName());
			if (bossTask != null && (task == null || !bossTask.equalsIgnoreCase(task.trim())))
			{
				prepTask = bossTask;
				prepLocation = null;
			}

			// gear the task requires; only enforce what you own, and flag the rest
			Set<String> ownedGearNames = new HashSet<>();
			for (Optimizer.Candidate c : candidates)
			{
				ownedGearNames.add(c.getItem().getName());
			}
			List<PrepPlan.Requirement> requirements = prep.requirements(prepTask, prepLocation, owned, ownedGearNames);
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
			Map<AttackType, Double> helmetCost = new EnumMap<>(AttackType.class);
			Map<AttackType, Double> serpentineCost = new EnumMap<>(AttackType.class);
			int minWeight = prep.minWeight(prepTask);
			boolean poisons = prep.poisons(prepTask);
			Set<AttackType> tooLight = EnumSet.noneOf(AttackType.class);
			for (AttackType type : AttackType.values())
			{
				if (type != AttackType.BURST)
				{
					StyleResult r = optimizer.optimise(candidates, type, required, minWeight);
					if (minWeight > 0 && !r.isValid())
					{
						// nothing owned is heavy enough: show the best set anyway, with a warning
						r = optimizer.optimise(candidates, type, required);
						if (r.isValid())
						{
							tooLight.add(type);
						}
					}
					int weight = tooLight.contains(type) ? 0 : minWeight;
					StyleResult serp = poisons ? preferSerpentine(optimizer, candidates, type, r, required,
						settings.getSerpentineHelm().getPercent(), serpentineCost, weight) : r;
					results.put(type, serp != r ? serp : preferHelmet(optimizer, candidates, type, r, required, setup,
						settings.getPreferSlayerHelmet(), helmetCost, weight));
				}
			}
			if (settings.getBurstTargets() > 0 && prep.stacks(prepTask))
			{
				// casting Ancients by hand on a stack: magic on the Ancient spellbook, DPS times the targets
				PlayerSetup burstSetup = setup.toBuilder().ancientSpellbook(true)
					.burstTargets(settings.getBurstTargets()).build();
				Optimizer burstOptimizer = new Optimizer(burstSetup, monster, cancel);
				StyleResult r = burstOptimizer.optimise(candidates, AttackType.MAGIC, required);
				Map<AttackType, Double> burstCost = new EnumMap<>(AttackType.class);
				r = preferHelmet(burstOptimizer, candidates, AttackType.MAGIC, r, required, setup,
					settings.getPreferSlayerHelmet(), burstCost, 0);
				if (burstCost.containsKey(AttackType.MAGIC))
				{
					helmetCost.put(AttackType.BURST, burstCost.get(AttackType.MAGIC));
				}
				results.put(AttackType.BURST, new StyleResult(AttackType.BURST, r.getResult(), r.getGear(),
					r.getCombinationsTried(), r.getItemsConsidered(), r.getItemsAfterPruning(), r.getSetsConsidered(),
					r.isStoppedEarly(), r.getAlternatives()));
			}

			Map<AttackType, OptimizationOutcome.ChargeHint> hints = chargeHints(optimizer, candidates, chargeable,
				results, required, helmetCost);

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
				? prep.plan(PrepPlanner.Request.builder()
				.task(prepTask)
				.location(prepLocation)
				.bestType(best == null ? null : best.getType())
				.requirements(requirements)
				.owned(owned)
				.prayerPotions(settings.getPrayerPotions())
				.prayerRestore(settings.getPrayerRestore())
				.combatPotions(settings.getCombatPotions())
				.fillFood(settings.isFillFood())
				.monsterName(monster.getName())
				.lootMaxKills(settings.getLootMaxKills())
				.taskRemaining(settings.getTaskRemaining())
				.monsterHitpoints(monster.getHitpoints())
				.bestWeapon(bestWeaponName(best))
				.suggestSpecWeapon(settings.isSpecWeapon())
				.slayerMaster(settings.getSlayerMaster() == SlayerMaster.NONE ? null : settings.getSlayerMaster().getName())
				.preferredFood(settings.getPreferredFood())
				.venomImmune(best != null && best.getGear()[EquipmentSlot.HEAD.ordinal()] != null
					&& isChargedSerpentine(best.getGear()[EquipmentSlot.HEAD.ordinal()].getItem()))
				.build())
				: new PrepPlan(requirements, List.of(), List.of(), List.of(), List.of(), 0, Set.of(), List.of(), List.of());

			OptimizationOutcome done = new OptimizationOutcome(task, monster, targetSource, choices, results, sources,
				setup, candidates.size(), hints, current, plan, settings.getTaskRemaining(),
				settings.getBurstTargets(), helmetCost, optimizer.ammoOptions(results.get(AttackType.RANGED), candidates),
				minWeight, tooLight, serpentineCost);
			outcome = done;
			updateHighlights();
			clientThread.invokeLater(() ->
			{
				lastReady = null;
				updateReadiness();
			});
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

	private static String bestWeaponName(StyleResult best)
	{
		if (best == null || best.getGear() == null)
		{
			return null;
		}
		Optimizer.Candidate weapon = best.getGear()[EquipmentSlot.WEAPON.ordinal()];
		return weapon == null ? null : weapon.getItem().getName();
	}

	/**
	 * On task, if the best set leaves off an owned Slayer helmet (say for a Salve amulet and a
	 * Serpentine helm), searches again with the helmet kept and uses that set when it is within
	 * the allowed percentage. Records the DPS given up.
	 */
	private static StyleResult preferHelmet(Optimizer optimizer, List<Optimizer.Candidate> candidates,
		AttackType type, StyleResult best, Map<EquipmentSlot, Set<String>> required, PlayerSetup setup,
		int percent, Map<AttackType, Double> cost, int minWeight)
	{
		if (percent <= 0 || !setup.isOnSlayerTask() || !best.isValid() || required.containsKey(EquipmentSlot.HEAD))
		{
			return best;
		}
		Optimizer.Candidate head = best.getGear()[EquipmentSlot.HEAD.ordinal()];
		if (head != null && (SLAYER_HELMS.contains(head.getItem().getName()) || head.getItem().getName().equals("V's helm")))
		{
			return best;
		}
		Set<String> owned = new HashSet<>();
		for (Optimizer.Candidate c : candidates)
		{
			if (SLAYER_HELMS.contains(c.getItem().getName()))
			{
				owned.add(c.getItem().getName());
			}
		}
		if (owned.isEmpty())
		{
			return best;
		}
		Map<EquipmentSlot, Set<String>> withHelmet = new EnumMap<>(EquipmentSlot.class);
		withHelmet.putAll(required);
		withHelmet.put(EquipmentSlot.HEAD, owned);
		StyleResult kept = optimizer.optimise(candidates, type, withHelmet, minWeight);
		double bestDps = best.getResult().getDps();
		if (!kept.isValid() || kept.getResult().getDps() * (100 + percent) < bestDps * 100)
		{
			return best;
		}
		cost.put(type, bestDps - kept.getResult().getDps());
		return kept;
	}

	/**
	 * On tasks that poison or envenom, searches again with a charged Serpentine helm (or Tanzanite or
	 * Magma helm) and uses that set when the setting allows the DPS it costs. Returns the original
	 * result when it doesn't apply.
	 */
	private static StyleResult preferSerpentine(Optimizer optimizer, List<Optimizer.Candidate> candidates,
		AttackType type, StyleResult best, Map<EquipmentSlot, Set<String>> required, int percent,
		Map<AttackType, Double> cost, int minWeight)
	{
		if (percent <= 0 || !best.isValid() || required.containsKey(EquipmentSlot.HEAD))
		{
			return best;
		}
		Optimizer.Candidate head = best.getGear()[EquipmentSlot.HEAD.ordinal()];
		if (head != null && isChargedSerpentine(head.getItem()))
		{
			return best;
		}
		Set<String> owned = new HashSet<>();
		List<Optimizer.Candidate> usable = new ArrayList<>();
		for (Optimizer.Candidate c : candidates)
		{
			if (SERPENTINE_HELMS.contains(c.getItem().getName()) && !isChargedSerpentine(c.getItem()))
			{
				continue; // uncharged: no protection
			}
			usable.add(c);
			if (isChargedSerpentine(c.getItem()))
			{
				owned.add(c.getItem().getName());
			}
		}
		if (owned.isEmpty())
		{
			return best;
		}
		Map<EquipmentSlot, Set<String>> withHelm = new EnumMap<>(EquipmentSlot.class);
		withHelm.putAll(required);
		withHelm.put(EquipmentSlot.HEAD, owned);
		StyleResult kept = optimizer.optimise(usable, type, withHelm, minWeight);
		double bestDps = best.getResult().getDps();
		if (!kept.isValid() || (percent != Integer.MAX_VALUE
			&& kept.getResult().getDps() * (100 + percent) < bestDps * 100))
		{
			return best;
		}
		cost.put(type, bestDps - kept.getResult().getDps());
		return kept;
	}

	private static boolean isChargedSerpentine(GearItem item)
	{
		return SERPENTINE_HELMS.contains(item.getName()) && !"Uncharged".equals(item.getVersion());
	}

	/**
	 * A second search as if the uncharged items were charged; reported only where it would win.
	 */
	private static Map<AttackType, OptimizationOutcome.ChargeHint> chargeHints(Optimizer optimizer,
		List<Optimizer.Candidate> candidates, List<Optimizer.Candidate> chargeable, Map<AttackType, StyleResult> results,
		Map<EquipmentSlot, Set<String>> required, Map<AttackType, Double> helmetCost)
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
			if (type == AttackType.BURST)
			{
				continue;
			}
			StyleResult charged = optimizer.optimise(withCharged, type, required);
			// compare with the best set before any Slayer helmet preference
			double withoutCharging = results.get(type).getResult().getDps() + helmetCost.getOrDefault(type, 0.0);
			if (!charged.isValid() || charged.getResult().getDps() <= withoutCharging)
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
		StyleResult r = o.withAmmo(o.resultFor(selectedType), selectedAmmo);
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
				addVariations(c.getOwnedItemId(), ids);
			}
		}
		highlightIds = sorted(ids);
	}

	/**
	 * Other versions of the same item (Blood Moon armour has new, used and broken ones, each its own
	 * item id), so the bank outline and Show in bank still find it if the id in the bank differs.
	 * Broken and other unusable versions are left out.
	 */
	private void addVariations(int itemId, Set<Integer> ids)
	{
		GameData data = gameData;
		for (int variation : ItemVariationMapping.getVariations(ItemVariationMapping.map(itemId)))
		{
			GearItem item = data == null ? null : data.getItem(variation);
			if (item == null || item.isUsable())
			{
				ids.add(variation);
			}
		}
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
