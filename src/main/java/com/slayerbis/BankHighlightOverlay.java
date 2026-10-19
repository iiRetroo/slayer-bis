package com.slayerbis;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.util.Arrays;
import javax.inject.Inject;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * Outlines the chosen gear and suggested supplies in the bank. Drawing only: it adds no menu
 * options and does not interact with the items. Per frame it does at most two binary searches per
 * visible bank item and allocates nothing.
 */
class BankHighlightOverlay extends WidgetItemOverlay
{
	private static final Stroke STROKE = new BasicStroke(2);

	private final SlayerBisPlugin plugin;

	@Inject
	BankHighlightOverlay(SlayerBisPlugin plugin)
	{
		this.plugin = plugin;
		showOnBank();
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		Color color = null;
		Color gear = plugin.getHighlightColor();
		if (gear != null && contains(plugin.getHighlightIds(), itemId))
		{
			color = gear;
		}
		else
		{
			Color supply = plugin.getSupplyHighlightColor();
			if (supply != null && contains(plugin.getSupplyHighlightIds(), itemId))
			{
				color = supply;
			}
		}
		if (color == null)
		{
			return;
		}
		Rectangle bounds = widgetItem.getCanvasBounds();
		graphics.setColor(color);
		graphics.setStroke(STROKE);
		graphics.drawRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1);
	}

	private static boolean contains(int[] sortedIds, int itemId)
	{
		return sortedIds.length != 0 && Arrays.binarySearch(sortedIds, itemId) >= 0;
	}
}
