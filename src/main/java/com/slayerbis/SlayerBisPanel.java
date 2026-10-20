package com.slayerbis;

import com.slayerbis.calc.CalcResult;
import com.slayerbis.calc.Optimizer;
import com.slayerbis.calc.PlayerSetup;
import com.slayerbis.calc.StyleResult;
import com.slayerbis.data.AttackType;
import com.slayerbis.data.EquipmentSlot;
import com.slayerbis.data.Monster;
import com.slayerbis.prep.PrepPlan;
import com.slayerbis.prep.PrepPlanner;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * Side panel: monster picker, a tab per combat style, the chosen set as an equipment grid, its
 * stats, the suggested inventory as an inventory grid, and collapsible extras. Only ever touched
 * on the Swing thread.
 */
class SlayerBisPanel extends PluginPanel
{
	private static final DecimalFormat DPS_FORMAT = new DecimalFormat("0.00");
	private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("0.0%");
	private static final DecimalFormat SECONDS_FORMAT = new DecimalFormat("0.0");
	private static final String AUTOMATIC = "Automatic";

	private static final Dimension TILE = new Dimension(36, 32);
	private static final Color TILE_BACKGROUND = ColorScheme.DARKER_GRAY_COLOR;
	private static final Color TILE_BORDER = new Color(60, 60, 60);
	private static final Color BANK_BORDER = new Color(0, 255, 140);
	private static final Color MISSING_BORDER = new Color(200, 50, 50);
	private static final Color GOOD = ColorScheme.PROGRESS_COMPLETE_COLOR;
	private static final Color WARN = ColorScheme.PROGRESS_INPROGRESS_COLOR;
	private static final Color BAD = ColorScheme.PROGRESS_ERROR_COLOR;
	private static final Color MUTED = ColorScheme.LIGHT_GRAY_COLOR;
	private static final Color BANNER = new Color(120, 35, 30);

	/**
	 * The worn-equipment interface layout: 5 rows of 3, null for gaps.
	 */
	private static final EquipmentSlot[][] EQUIPMENT_LAYOUT = {
		{null, EquipmentSlot.HEAD, null},
		{EquipmentSlot.CAPE, EquipmentSlot.NECK, EquipmentSlot.AMMO},
		{EquipmentSlot.WEAPON, EquipmentSlot.BODY, EquipmentSlot.SHIELD},
		{null, EquipmentSlot.LEGS, null},
		{EquipmentSlot.HANDS, EquipmentSlot.FEET, EquipmentSlot.RING},
	};

	private static final String[] TAB_NAMES = {"Stab", "Slash", "Crush", "Rng", "Mag"};

	/** Text wraps at this width (pixels) so labels get the right height in the narrow sidebar. */
	private static final int TEXT_WIDTH = 160;
	private static final Color STACK_TEXT = new Color(255, 255, 0);

	private final SlayerBisPlugin plugin;
	/** May be null in offline previews, in which case items show by name only. */
	private final ItemManager itemManager;

	private final JLabel statusLabel = new JLabel();
	private final JPanel content = new JPanel();
	private final JComboBox<String> monsterSelect = new JComboBox<>();
	private final List<Integer> monsterIds = new ArrayList<>();
	/** Which collapsible sections are open, by title; remembered across updates. */
	private final Map<String, Boolean> expanded = new HashMap<>();

	private OptimizationOutcome outcome;
	/** The style tab picked by the player, or null to follow the best style. */
	private AttackType selected;
	private boolean updatingSelect;

	SlayerBisPanel(SlayerBisPlugin plugin, ItemManager itemManager)
	{
		this.plugin = plugin;
		this.itemManager = itemManager;

		setBorder(new EmptyBorder(8, 8, 8, 8));
		setLayout(new BorderLayout(0, 6));

		JPanel header = column();

		JLabel title = new JLabel("SlayerBIS");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		header.add(left(title));

		statusLabel.setFont(FontManager.getRunescapeSmallFont());
		statusLabel.setForeground(MUTED);
		statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.add(statusLabel);

		monsterSelect.setToolTipText("Monster to calculate against. Some tasks cover several monsters.");
		monsterSelect.addActionListener(e ->
		{
			if (updatingSelect)
			{
				return;
			}
			int index = monsterSelect.getSelectedIndex();
			plugin.selectMonster(index <= 0 ? null : monsterIds.get(index - 1));
		});

		JButton refresh = new JButton(new ImageIcon(refreshIcon()));
		refresh.setToolTipText("Calculate again with your current levels and settings");
		refresh.setPreferredSize(new Dimension(28, 25));
		refresh.addActionListener(e -> plugin.requestRecalculate());

		JPanel top = new JPanel(new BorderLayout(4, 0));
		top.setBorder(new EmptyBorder(6, 0, 0, 0));
		top.add(monsterSelect, BorderLayout.CENTER);
		top.add(refresh, BorderLayout.EAST);
		header.add(left(top));

		add(header, BorderLayout.NORTH);

		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		add(content, BorderLayout.CENTER);

		showMessage("Open your bank to scan your gear.");
	}

	// ---- public API used by the plugin ----

	void showMessage(String message)
	{
		statusLabel.setText(wrap(escape(message)));
		if (outcome == null)
		{
			content.removeAll();
			content.revalidate();
			content.repaint();
		}
	}

	/**
	 * Forgets the last result, e.g. after logging out.
	 */
	void reset(String message)
	{
		outcome = null;
		updatingSelect = true;
		monsterSelect.removeAllItems();
		monsterIds.clear();
		updatingSelect = false;
		showMessage(message);
	}

	void showOutcome(OptimizationOutcome outcome, AttackType selected)
	{
		this.outcome = outcome;
		this.selected = selected;
		statusLabel.setText(wrap(escape(outcome.getMonster().displayName()) + " &middot; "
			+ outcome.getItemsScanned() + " items scanned"));
		updatingSelect = true;
		monsterSelect.removeAllItems();
		monsterIds.clear();
		monsterSelect.addItem(AUTOMATIC);
		int selectedIndex = 0;
		for (Monster m : outcome.getMonsterChoices())
		{
			monsterIds.add(m.getId());
			monsterSelect.addItem(m.displayName());
			if ("your choice".equals(outcome.getTargetSource()) && m.getId() == outcome.getMonster().getId())
			{
				selectedIndex = monsterIds.size();
			}
		}
		monsterSelect.setSelectedIndex(selectedIndex);
		updatingSelect = false;
		render();
	}

	// ---- rendering ----

	private void render()
	{
		content.removeAll();
		if (outcome == null)
		{
			content.revalidate();
			content.repaint();
			return;
		}

		StyleResult best = outcome.best();
		AttackType shownType = selected != null ? selected : best == null ? null : best.getType();
		StyleResult shown = shownType == null ? null : outcome.getResults().get(shownType);

		content.add(styleTabs(best, shownType));
		renderWarnings(best);

		if (shown == null || !shown.isValid())
		{
			content.add(left(text(best == null ? "None of your gear can damage this monster."
				: "No usable gear for this style.", BAD, false)));
		}
		else
		{
			content.add(gap(6));
			content.add(centered(equipmentGrid(shown)));
			content.add(gap(6));
			content.add(statsBox(shown, best));
			content.add(banner(shown.getResult()));
			OptimizationOutcome.ChargeHint hint = outcome.getChargeHints().get(shown.getType());
			if (hint != null)
			{
				content.add(left(text(chargeText(hint, shown.getResult().getDps()), WARN, true)));
			}
		}

		renderInventory();
		if (shown != null && shown.isValid() && !shown.getAlternatives().isEmpty())
		{
			content.add(collapsible("Other weapons", alternatives(shown)));
		}
		JComponent teleports = teleports();
		if (teleports != null)
		{
			content.add(collapsible("Getting there", teleports));
		}
		content.add(collapsible("Details", details(shown)));

		content.revalidate();
		content.repaint();
	}

	private JComponent styleTabs(StyleResult best, AttackType shownType)
	{
		JPanel tabs = new JPanel(new GridLayout(1, AttackType.values().length, 2, 0));
		for (AttackType t : AttackType.values())
		{
			StyleResult r = outcome.getResults().get(t);
			boolean valid = r != null && r.isValid();
			boolean isBest = best != null && r == best;
			Color dpsColor = !valid ? MUTED : isBest ? GOOD : Color.WHITE;
			JLabel tab = new JLabel("<html><center>" + TAB_NAMES[t.ordinal()] + "<br><font color='"
				+ hex(dpsColor) + "'>" + (valid ? DPS_FORMAT.format(r.getResult().getDps()) : "-")
				+ "</font></center></html>", SwingConstants.CENTER);
			tab.setFont(FontManager.getRunescapeSmallFont());
			tab.setForeground(MUTED);
			tab.setOpaque(true);
			boolean active = t == shownType;
			tab.setBackground(active ? ColorScheme.MEDIUM_GRAY_COLOR : TILE_BACKGROUND);
			tab.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 0, 2, 0, active ? ColorScheme.BRAND_ORANGE : TILE_BACKGROUND),
				new EmptyBorder(3, 0, 2, 0)));
			tab.setToolTipText(t.getDisplayName() + (isBest ? " (best)" : ""));
			if (valid)
			{
				tab.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				tab.addMouseListener(new MouseAdapter()
				{
					@Override
					public void mouseClicked(MouseEvent e)
					{
						selected = t == (best == null ? null : best.getType()) ? null : t;
						plugin.selectType(selected);
						render();
					}
				});
			}
			tabs.add(tab);
		}
		return left(tabs);
	}

	private void renderWarnings(StyleResult best)
	{
		for (PrepPlan.Requirement r : outcome.getPrep().getRequirements())
		{
			if (!r.isOwned())
			{
				content.add(left(text("Missing " + String.join(" / ", r.getAccepted()) + " (" + r.getReason() + ")", BAD, true)));
			}
		}
		double bestDps = best == null ? 0 : best.getResult().getDps();
		for (Map.Entry<AttackType, OptimizationOutcome.ChargeHint> hint : outcome.getChargeHints().entrySet())
		{
			if (hint.getValue().getDps() > bestDps && hint.getKey() != (best == null ? null : best.getType()))
			{
				content.add(left(text(hint.getKey().getDisplayName() + ": " + chargeText(hint.getValue(), bestDps), WARN, true)));
			}
		}
	}

	private JComponent equipmentGrid(StyleResult shown)
	{
		JPanel grid = new JPanel(new GridLayout(EQUIPMENT_LAYOUT.length, 3, 3, 3));
		for (EquipmentSlot[] row : EQUIPMENT_LAYOUT)
		{
			for (EquipmentSlot slot : row)
			{
				if (slot == null)
				{
					JPanel gap = new JPanel();
					gap.setPreferredSize(TILE);
					grid.add(gap);
					continue;
				}
				Optimizer.Candidate c = shown.getGear()[slot.ordinal()];
				if (c == null)
				{
					grid.add(tile(-1, null, slot.getDisplayName() + ": empty", TILE_BORDER));
					continue;
				}
				ItemSource source = outcome.getSources().get(c.getOwnedItemId());
				boolean fromBank = source == ItemSource.BANK;
				String tip = c.getItem().getName() + " (" + (source == null ? "owned" : source.getLabel()) + ")";
				grid.add(tile(c.getOwnedItemId(), c.getItem().getName(), tip, fromBank ? BANK_BORDER : TILE_BORDER));
			}
		}
		grid.setMaximumSize(grid.getPreferredSize());
		return grid;
	}

	private JComponent statsBox(StyleResult shown, StyleResult best)
	{
		CalcResult r = shown.getResult();
		JPanel box = column();
		box.setBackground(TILE_BACKGROUND);
		box.setOpaque(true);
		box.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(shown == best ? GOOD.darker() : TILE_BORDER),
			new EmptyBorder(4, 6, 4, 6)));

		JLabel dps = new JLabel("DPS: " + DPS_FORMAT.format(r.getDps()));
		dps.setFont(FontManager.getRunescapeBoldFont());
		dps.setForeground(Color.WHITE);
		box.add(left(dps));
		box.add(statRow("Max hit", String.valueOf(r.getMaxHit())));
		box.add(statRow("Accuracy", PERCENT_FORMAT.format(r.getAccuracy())));
		box.add(statRow("Speed", SECONDS_FORMAT.format(r.getAttackSpeedTicks() * 0.6) + "s"));
		OptimizationOutcome.CurrentGear current = outcome.getCurrentGear();
		if (current != null)
		{
			double gain = r.getDps() - current.getResult().getDps();
			String text = gain > 0.005
				? "+" + DPS_FORMAT.format(gain) + " (+" + PERCENT_FORMAT.format(gain / current.getResult().getDps()) + ")"
				: "no gain";
			JComponent row = statRow("vs worn", text);
			row.setToolTipText("Your current gear: " + DPS_FORMAT.format(current.getResult().getDps()) + " DPS ("
				+ current.getType().getDisplayName() + ")");
			box.add(row);
		}
		box.setAlignmentX(Component.LEFT_ALIGNMENT);
		box.setMaximumSize(new Dimension(Integer.MAX_VALUE, box.getPreferredSize().height));
		return box;
	}

	private JComponent statRow(String name, String value)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		JLabel n = new JLabel(name);
		n.setFont(FontManager.getRunescapeSmallFont());
		n.setForeground(MUTED);
		JLabel v = new JLabel(value, SwingConstants.RIGHT);
		v.setFont(FontManager.getRunescapeSmallFont());
		v.setForeground(Color.WHITE);
		row.add(n, BorderLayout.WEST);
		row.add(v, BorderLayout.EAST);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		return row;
	}

	private JComponent banner(CalcResult r)
	{
		String main = r.getSpell() != null ? r.getSpell().getName() : r.getStance().getDisplayName();
		String sub = r.getSpell() != null ? "Autocast" : shownTypeName();
		JLabel label = new JLabel("<html><center>" + escape(main) + "<br><font color='#d8c8b0'>" + escape(sub)
			+ "</font></center></html>", SwingConstants.CENTER);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(Color.WHITE);
		label.setOpaque(true);
		label.setBackground(BANNER);
		label.setBorder(new EmptyBorder(3, 0, 3, 0));
		JPanel wrap = new JPanel(new BorderLayout());
		wrap.setBorder(new EmptyBorder(4, 0, 0, 0));
		wrap.add(label, BorderLayout.CENTER);
		return left(wrap);
	}

	private String shownTypeName()
	{
		StyleResult best = outcome.best();
		AttackType t = selected != null ? selected : best == null ? null : best.getType();
		return t == null ? "" : t.getDisplayName();
	}

	private void renderInventory()
	{
		PrepPlan prep = outcome.getPrep();
		if (prep.getInventory().isEmpty() && prep.getNotes().isEmpty())
		{
			return;
		}
		content.add(sectionTitle("Inventory  " + prep.getSlotsUsed() + "/" + PrepPlanner.INVENTORY_SLOTS));
		if (!prep.getInventory().isEmpty())
		{
			// one tile per item, with how many to bring shown like an in-game stack count
			JPanel grid = new JPanel(new GridLayout(0, 5, 3, 3));
			grid.setOpaque(false);
			for (PrepPlan.InventoryLine l : prep.getInventory())
			{
				String tip = l.getQuantity() + " x " + l.getName() + (l.getNote() == null ? "" : " - " + l.getNote())
					+ " (you have " + l.getOwned() + ")";
				boolean missing = l.getOwned() == 0;
				JLabel tile = tile(missing ? -1 : l.getItemId(), l.getName(), missing ? "Missing: " + tip : tip,
					missing ? MISSING_BORDER : TILE_BORDER, l.getQuantity());
				grid.add(tile);
			}
			grid.setMaximumSize(grid.getPreferredSize());
			content.add(centered(grid));
		}
		for (String note : prep.getNotes())
		{
			content.add(left(text(note, WARN, true)));
		}
	}

	private JComponent alternatives(StyleResult shown)
	{
		JPanel list = column();
		double dps = shown.getResult().getDps();
		for (StyleResult.Alternative alt : shown.getAlternatives())
		{
			Optimizer.Candidate weapon = alt.getGear()[EquipmentSlot.WEAPON.ordinal()];
			String name = weapon == null ? "Unarmed" : weapon.getItem().getName();
			JPanel row = new JPanel(new BorderLayout(6, 0));
			row.setOpaque(false);
			row.add(tile(weapon == null ? -1 : weapon.getOwnedItemId(), name, name, TILE_BORDER), BorderLayout.WEST);
			JLabel label = new JLabel("<html>" + escape(name) + "<br><font color='#a5a5a5'>"
				+ DPS_FORMAT.format(alt.getResult().getDps()) + " DPS ("
				+ DPS_FORMAT.format(alt.getResult().getDps() - dps) + ")</font></html>");
			label.setFont(FontManager.getRunescapeSmallFont());
			label.setForeground(Color.WHITE);
			row.add(label, BorderLayout.CENTER);
			list.add(left(row));
			list.add(gap(2));
		}
		return list;
	}

	private JComponent teleports()
	{
		PrepPlan prep = outcome.getPrep();
		if (prep.getTeleports().isEmpty())
		{
			return null;
		}
		JPanel list = column();
		String location = null;
		boolean fairyRing = false;
		for (PrepPlan.TeleportOption t : prep.getTeleports())
		{
			if (!t.getLocation().equals(location))
			{
				location = t.getLocation();
				list.add(left(text(location, Color.WHITE, false)));
			}
			fairyRing |= t.getText().startsWith("Fairy ring");
			Color c = t.getOwned() == null ? MUTED : t.getOwned() ? GOOD : BAD;
			list.add(left(text((t.getOwned() == null ? "  " : t.getOwned() ? "+ " : "x ") + t.getText(), c, true)));
		}
		if (fairyRing)
		{
			list.add(left(text("Fairy rings need a Dramen or Lunar staff unless you have the Lumbridge Elite diary.", MUTED, true)));
		}
		return list;
	}

	private JComponent details(StyleResult shown)
	{
		JPanel list = column();
		Monster m = outcome.getMonster();
		String task = outcome.getTask();
		list.add(left(text(m.displayName() + ": Defence " + m.getDefenceLevel() + ", Magic " + m.getMagicLevel()
			+ ", HP " + m.getHitpoints(), Color.WHITE, true)));
		list.add(left(text("Target from " + outcome.getTargetSource()
			+ (task != null && !task.isEmpty() ? " (" + task + ")" : ""), MUTED, true)));
		for (PrepPlan.Requirement r : outcome.getPrep().getRequirements())
		{
			if (r.isOwned())
			{
				list.add(left(text("Task gear kept in every set: " + String.join(" / ", r.getAccepted()), MUTED, true)));
			}
		}
		list.add(left(text(assumptions(outcome.getSetup()), MUTED, true)));
		if (shown != null && shown.isValid())
		{
			if (!shown.getSetsConsidered().isEmpty())
			{
				list.add(left(text("Sets tried: " + String.join(", ", shown.getSetsConsidered()), MUTED, true)));
			}
			list.add(left(text(shown.getItemsAfterPruning() + " of " + shown.getItemsConsidered()
				+ " items kept after removing outclassed gear; " + shown.getCombinationsTried() + " combinations tried.", MUTED, true)));
			if (shown.isStoppedEarly())
			{
				list.add(left(text("Search stopped early: too many combinations.", BAD, true)));
			}
		}
		list.add(left(text("OSRS Wiki DPS calculator formulas. Special attacks not included. "
			+ "Nothing is equipped or withdrawn for you.", MUTED, true)));
		return list;
	}

	// ---- building blocks ----

	/**
	 * A slot tile showing an item icon (or an empty slot), with a tooltip and coloured border.
	 */
	private JLabel tile(int itemId, String name, String tooltip, Color border)
	{
		return tile(itemId, name, tooltip, border, 1);
	}

	/**
	 * As {@link #tile(int, String, String, Color)}, drawing the quantity in the top-left corner
	 * like an in-game stack when it is more than one.
	 */
	private JLabel tile(int itemId, String name, String tooltip, Color border, int quantity)
	{
		String count = quantity > 1 ? String.valueOf(quantity) : null;
		JLabel tile = new JLabel()
		{
			@Override
			protected void paintComponent(java.awt.Graphics g)
			{
				super.paintComponent(g);
				if (count != null)
				{
					g.setFont(FontManager.getRunescapeSmallFont());
					g.setColor(Color.BLACK);
					g.drawString(count, 4, 12);
					g.setColor(STACK_TEXT);
					g.drawString(count, 3, 11);
				}
			}
		};
		tile.setPreferredSize(TILE);
		tile.setMinimumSize(TILE);
		tile.setHorizontalAlignment(SwingConstants.CENTER);
		tile.setOpaque(true);
		tile.setBackground(TILE_BACKGROUND);
		tile.setBorder(BorderFactory.createLineBorder(border));
		tile.setToolTipText(tooltip);
		if (itemId > 0 && itemManager != null)
		{
			itemManager.getImage(itemId).addTo(tile);
		}
		else if (name != null)
		{
			// no icon available (missing item, or an offline preview): show a short label instead
			tile.setText("<html><center>" + escape(name.length() > 8 ? name.substring(0, 7) + "." : name) + "</center></html>");
			tile.setFont(FontManager.getRunescapeSmallFont());
			tile.setForeground(border == MISSING_BORDER ? BAD : MUTED);
		}
		return tile;
	}

	private JComponent collapsible(String title, JComponent body)
	{
		JPanel section = column();
		boolean open = expanded.getOrDefault(title, false);
		JLabel header = sectionTitle((open ? "- " : "+ ") + title);
		header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		header.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				expanded.put(title, !open);
				render();
			}
		});
		section.add(header);
		if (open)
		{
			body.setAlignmentX(Component.LEFT_ALIGNMENT);
			section.add(body);
		}
		section.setAlignmentX(Component.LEFT_ALIGNMENT);
		return section;
	}

	private static JLabel sectionTitle(String text)
	{
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeBoldFont());
		l.setForeground(ColorScheme.BRAND_ORANGE);
		l.setBorder(new EmptyBorder(8, 0, 3, 0));
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		return l;
	}

	private static JLabel text(String text, Color color, boolean small)
	{
		JLabel l = new JLabel(wrap(escape(text)));
		l.setForeground(color);
		if (small)
		{
			l.setFont(FontManager.getRunescapeSmallFont());
		}
		return l;
	}

	private static JPanel column()
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setOpaque(false);
		return p;
	}

	private static JComponent left(JComponent c)
	{
		c.setAlignmentX(Component.LEFT_ALIGNMENT);
		c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
		return c;
	}

	private static JComponent centered(JComponent c)
	{
		JPanel wrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		wrap.setOpaque(false);
		wrap.add(c);
		return left(wrap);
	}

	private static JComponent gap(int height)
	{
		JPanel p = new JPanel();
		p.setOpaque(false);
		p.setPreferredSize(new Dimension(1, height));
		return left(p);
	}

	private static String chargeText(OptimizationOutcome.ChargeHint hint, double comparedTo)
	{
		return "charging " + String.join(", ", hint.getItems()) + " would give " + DPS_FORMAT.format(hint.getDps())
			+ " DPS (+" + DPS_FORMAT.format(hint.getDps() - comparedTo) + ")";
	}

	private static String assumptions(PlayerSetup s)
	{
		return "Levels: Atk " + s.getAttack() + ", Str " + s.getStrength() + ", Rng " + s.getRanged()
			+ ", Mag " + s.getMagic() + ". On task: " + (s.isOnSlayerTask() ? "yes" : "no")
			+ ". Spellbook: " + (s.isAncientSpellbook() ? "Ancient" : "Standard") + ".";
	}

	/**
	 * HTML that wraps at the sidebar width, so the label's preferred height is right.
	 */
	private static String wrap(String html)
	{
		return "<html><div style='width:" + TEXT_WIDTH + "px'>" + html + "</div></html>";
	}

	private static String hex(Color c)
	{
		return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
	}

	private static String escape(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/**
	 * A small circular-arrow refresh icon, drawn so the plugin needs no extra image file.
	 */
	private static BufferedImage refreshIcon()
	{
		BufferedImage img = new BufferedImage(14, 14, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(220, 220, 220));
		g.setStroke(new BasicStroke(1.8f));
		g.drawArc(2, 2, 10, 10, 60, 270);
		g.fillPolygon(new int[]{9, 13, 13}, new int[]{1, 1, 5}, 3);
		g.dispose();
		return img;
	}
}
