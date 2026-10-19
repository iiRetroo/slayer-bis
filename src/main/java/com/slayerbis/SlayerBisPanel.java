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
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * Side panel showing the best style, its gear and DPS, and the task preparation. Only ever touched
 * on the Swing thread.
 */
class SlayerBisPanel extends PluginPanel
{
	private static final DecimalFormat DPS_FORMAT = new DecimalFormat("0.000");
	private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("0.0%");
	private static final String BEST = "Best overall";
	private static final String AUTOMATIC = "Automatic";

	private final SlayerBisPlugin plugin;
	private final ItemManager itemManager;

	private final JLabel statusLabel = new JLabel();
	private final JPanel content = new JPanel();
	private final JComboBox<String> styleSelect = new JComboBox<>();
	private final JComboBox<String> monsterSelect = new JComboBox<>();
	/** Monster ids in monsterSelect order, after the "Automatic" entry. */
	private final List<Integer> monsterIds = new ArrayList<>();

	private OptimizationOutcome outcome;
	private boolean updatingSelect;

	SlayerBisPanel(SlayerBisPlugin plugin, ItemManager itemManager)
	{
		this.plugin = plugin;
		this.itemManager = itemManager;

		setBorder(new EmptyBorder(10, 10, 10, 10));
		setLayout(new BorderLayout(0, 8));

		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

		JLabel title = new JLabel("SlayerBIS");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		header.add(title);

		statusLabel.setFont(FontManager.getRunescapeSmallFont());
		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		header.add(statusLabel);

		monsterSelect.setToolTipText("Which monster to calculate against. Some tasks cover several monsters.");
		monsterSelect.addActionListener(e ->
		{
			if (updatingSelect)
			{
				return;
			}
			int index = monsterSelect.getSelectedIndex();
			plugin.selectMonster(index <= 0 ? null : monsterIds.get(index - 1));
		});

		styleSelect.addItem(BEST);
		for (AttackType t : AttackType.values())
		{
			styleSelect.addItem(t.getDisplayName());
		}
		styleSelect.setToolTipText("Which style's gear to show and highlight in the bank");
		styleSelect.addActionListener(e ->
		{
			if (updatingSelect)
			{
				return;
			}
			AttackType type = selectedType();
			plugin.selectType(type);
			render(type);
		});

		JButton recalc = new JButton("Recalculate");
		recalc.setToolTipText("Run the calculation again with your current levels and settings");
		recalc.addActionListener(e -> plugin.requestRecalculate());

		JPanel controls = new JPanel(new GridLayout(3, 1, 0, 4));
		controls.setBorder(new EmptyBorder(6, 0, 0, 0));
		controls.add(monsterSelect);
		controls.add(styleSelect);
		controls.add(recalc);
		header.add(controls);

		add(header, BorderLayout.NORTH);

		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		add(content, BorderLayout.CENTER);

		showMessage("Open your bank to scan your gear.");
	}

	private AttackType selectedType()
	{
		int index = styleSelect.getSelectedIndex();
		return index <= 0 ? null : AttackType.values()[index - 1];
	}

	void showMessage(String message)
	{
		statusLabel.setText("<html>" + escape(message) + "</html>");
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
		statusLabel.setText("<html>Scanned " + outcome.getItemsScanned() + " equippable items.</html>");
		updatingSelect = true;
		styleSelect.setSelectedIndex(selected == null ? 0 : selected.ordinal() + 1);
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
		render(selected);
	}

	private void render(AttackType selected)
	{
		content.removeAll();
		if (outcome == null)
		{
			content.revalidate();
			content.repaint();
			return;
		}

		renderTarget();
		renderTaskGear();

		StyleResult best = outcome.best();
		content.add(section("Best style"));
		if (best == null)
		{
			content.add(line("None of your gear can damage this monster.", ColorScheme.PROGRESS_ERROR_COLOR));
		}
		else
		{
			content.add(line(describe(best), ColorScheme.PROGRESS_COMPLETE_COLOR));
			OptimizationOutcome.CurrentGear current = outcome.getCurrentGear();
			if (current != null)
			{
				double gain = best.getResult().getDps() - current.getResult().getDps();
				content.add(small("Your current gear: " + DPS_FORMAT.format(current.getResult().getDps()) + " DPS ("
					+ current.getType().getDisplayName() + "). Best set: "
					+ (gain > 0.0005 ? "+" + DPS_FORMAT.format(gain) + " DPS (+"
					+ PERCENT_FORMAT.format(gain / current.getResult().getDps()) + ")" : "no improvement") + "."));
			}
		}
		double bestDps = best == null ? 0 : best.getResult().getDps();
		for (Map.Entry<AttackType, OptimizationOutcome.ChargeHint> hint : outcome.getChargeHints().entrySet())
		{
			if (hint.getValue().getDps() > bestDps)
			{
				content.add(line(chargeText(hint.getKey(), hint.getValue(), bestDps), ColorScheme.PROGRESS_INPROGRESS_COLOR));
			}
		}

		content.add(section("DPS by style"));
		for (AttackType t : AttackType.values())
		{
			StyleResult r = outcome.getResults().get(t);
			String text = r != null && r.isValid() ? DPS_FORMAT.format(r.getResult().getDps()) : "-";
			JPanel row = new JPanel(new BorderLayout());
			row.setBorder(new EmptyBorder(1, 0, 1, 0));
			JLabel name = new JLabel(t.getDisplayName());
			JLabel value = new JLabel(text, SwingConstants.RIGHT);
			Color c = r == best && best != null ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.LIGHT_GRAY_COLOR;
			name.setForeground(c);
			value.setForeground(c);
			row.add(name, BorderLayout.WEST);
			row.add(value, BorderLayout.EAST);
			content.add(row);
		}

		renderSet(selected);
		renderPreparation();

		content.add(section("Assumptions"));
		content.add(small(assumptions(outcome.getSetup())));
		content.add(small("DPS uses the OSRS Wiki DPS calculator formulas. Special attacks are not included. "
			+ "Nothing is equipped or withdrawn for you."));

		content.revalidate();
		content.repaint();
	}

	private void renderTarget()
	{
		Monster m = outcome.getMonster();
		String task = outcome.getTask();
		content.add(section("Target"));
		content.add(line(m.displayName(), Color.WHITE));
		content.add(small("From " + outcome.getTargetSource()
			+ (task != null && !task.isEmpty() ? " (" + task + ")" : "")));
		content.add(small("Defence " + m.getDefenceLevel() + ", Magic " + m.getMagicLevel() + ", HP " + m.getHitpoints()));
	}

	private void renderTaskGear()
	{
		List<PrepPlan.Requirement> requirements = outcome.getPrep().getRequirements();
		if (requirements.isEmpty())
		{
			return;
		}
		content.add(section("Task gear"));
		for (PrepPlan.Requirement r : requirements)
		{
			String text = r.getSlot().getDisplayName() + ": " + String.join(" / ", r.getAccepted()) + " (" + r.getReason() + ")";
			if (r.isOwned())
			{
				content.add(small("Included in every set. " + text));
			}
			else
			{
				content.add(line("Not found: " + text, ColorScheme.PROGRESS_ERROR_COLOR));
			}
		}
	}

	private void renderSet(AttackType selected)
	{
		StyleResult shown = outcome.resultFor(selected);
		content.add(section(selected == null ? "Best set" : selected.getDisplayName() + " set"));
		if (shown == null || !shown.isValid())
		{
			content.add(line("No usable gear for this style.", ColorScheme.LIGHT_GRAY_COLOR));
			return;
		}
		CalcResult r = shown.getResult();
		content.add(line("DPS " + DPS_FORMAT.format(r.getDps()), Color.WHITE));
		content.add(small("Max hit " + r.getMaxHit() + ", accuracy " + PERCENT_FORMAT.format(r.getAccuracy())
			+ ", " + r.getAttackSpeedTicks() + "-tick"));
		content.add(small("Stance: " + r.getStance() + (r.getSpell() != null ? ", casting " + r.getSpell().getName() : "")));
		OptimizationOutcome.ChargeHint hint = outcome.getChargeHints().get(shown.getType());
		if (hint != null)
		{
			content.add(line(chargeText(shown.getType(), hint, r.getDps()), ColorScheme.PROGRESS_INPROGRESS_COLOR));
		}
		for (EquipmentSlot slot : EquipmentSlot.values())
		{
			Optimizer.Candidate c = shown.getGear()[slot.ordinal()];
			if (c != null)
			{
				content.add(itemRow(slot, c));
			}
		}

		if (!shown.getAlternatives().isEmpty())
		{
			content.add(small("Best with another weapon:"));
			for (StyleResult.Alternative alt : shown.getAlternatives())
			{
				Optimizer.Candidate weapon = alt.getGear()[EquipmentSlot.WEAPON.ordinal()];
				String name = weapon == null ? "Unarmed" : weapon.getItem().getName();
				double diff = alt.getResult().getDps() - r.getDps();
				content.add(small("  " + name + ": " + DPS_FORMAT.format(alt.getResult().getDps()) + " DPS ("
					+ DPS_FORMAT.format(diff) + ")"));
			}
		}
		if (!shown.getSetsConsidered().isEmpty())
		{
			content.add(small("Sets tried: " + String.join(", ", shown.getSetsConsidered())));
		}
		content.add(small(shown.getItemsAfterPruning() + " of " + shown.getItemsConsidered()
			+ " items left after removing outclassed gear; " + shown.getCombinationsTried() + " combinations tried."));
		if (shown.isStoppedEarly())
		{
			content.add(line("Search stopped early: too many combinations.", ColorScheme.PROGRESS_ERROR_COLOR));
		}
	}

	private void renderPreparation()
	{
		PrepPlan prep = outcome.getPrep();
		if (prep.getInventory().isEmpty() && prep.getTeleports().isEmpty() && prep.getNotes().isEmpty())
		{
			return;
		}
		content.add(section("Inventory (" + prep.getSlotsUsed() + "/" + PrepPlanner.INVENTORY_SLOTS + ")"));
		for (String note : prep.getNotes())
		{
			content.add(line(note, ColorScheme.PROGRESS_INPROGRESS_COLOR));
		}
		for (PrepPlan.InventoryLine l : prep.getInventory())
		{
			String text = l.getName() + " x" + l.getQuantity() + "  (you have " + l.getOwned() + ")"
				+ (l.getNote() == null ? "" : " - " + l.getNote());
			content.add(l.getOwned() == 0 ? line(text, ColorScheme.PROGRESS_ERROR_COLOR) : small(text));
		}

		if (!prep.getTeleports().isEmpty())
		{
			content.add(section("Getting there"));
			String location = null;
			boolean fairyRing = false;
			for (PrepPlan.TeleportOption t : prep.getTeleports())
			{
				if (!t.getLocation().equals(location))
				{
					location = t.getLocation();
					content.add(line(location, Color.WHITE));
				}
				fairyRing |= t.getText().startsWith("Fairy ring");
				String owned = t.getOwned() == null ? "" : t.getOwned() ? " (you have this)" : " (not found)";
				content.add(small("  " + t.getText() + owned));
			}
			if (fairyRing)
			{
				content.add(small("Fairy rings need a Dramen or Lunar staff unless you have the Lumbridge Elite diary."));
			}
		}
	}

	private static String chargeText(AttackType type, OptimizationOutcome.ChargeHint hint, double comparedTo)
	{
		return "If charged or repaired (" + String.join(", ", hint.getItems()) + "): " + type.getDisplayName()
			+ " would be " + DPS_FORMAT.format(hint.getDps()) + " DPS (+" + DPS_FORMAT.format(hint.getDps() - comparedTo) + ")";
	}

	private static String describe(StyleResult r)
	{
		CalcResult c = r.getResult();
		String how = c.getSpell() != null ? c.getSpell().getName() : c.getStance().getDisplayName();
		return r.getType().getDisplayName() + " (" + how + "): " + DPS_FORMAT.format(c.getDps()) + " DPS";
	}

	private static String assumptions(PlayerSetup s)
	{
		return "Levels used: Atk " + s.getAttack() + ", Str " + s.getStrength() + ", Rng " + s.getRanged()
			+ ", Mag " + s.getMagic() + ". On task: " + (s.isOnSlayerTask() ? "yes" : "no")
			+ ". Spellbook: " + (s.isAncientSpellbook() ? "Ancient" : "Standard") + ".";
	}

	private JPanel itemRow(EquipmentSlot slot, Optimizer.Candidate c)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(2, 4, 2, 4));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(36, 32));
		AsyncBufferedImage image = itemManager.getImage(c.getOwnedItemId());
		image.addTo(icon);
		row.add(icon, BorderLayout.WEST);

		ItemSource source = outcome.getSources().get(c.getOwnedItemId());
		JLabel text = new JLabel("<html>" + escape(c.getItem().getName()) + "<br><font color='#a5a5a5'>"
			+ slot.getDisplayName() + (source != null ? " - " + source.getLabel() : "") + "</font></html>");
		text.setForeground(Color.WHITE);
		text.setFont(FontManager.getRunescapeSmallFont());
		row.add(text, BorderLayout.CENTER);

		JPanel wrap = new JPanel(new BorderLayout());
		wrap.setBorder(new EmptyBorder(2, 0, 0, 0));
		wrap.add(row, BorderLayout.CENTER);
		return wrap;
	}

	private static JLabel section(String text)
	{
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeBoldFont());
		l.setForeground(ColorScheme.BRAND_ORANGE);
		l.setBorder(BorderFactory.createEmptyBorder(10, 0, 2, 0));
		return l;
	}

	private static JLabel line(String text, Color color)
	{
		JLabel l = new JLabel("<html>" + escape(text) + "</html>");
		l.setForeground(color);
		return l;
	}

	private static JLabel small(String text)
	{
		JLabel l = new JLabel("<html>" + escape(text) + "</html>");
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		return l;
	}

	private static String escape(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
