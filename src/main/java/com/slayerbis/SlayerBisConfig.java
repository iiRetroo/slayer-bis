package com.slayerbis;

import java.awt.Color;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(SlayerBisConfig.GROUP)
public interface SlayerBisConfig extends Config
{
	String GROUP = "slayerbis";

	@ConfigSection(
		name = "Gear sources",
		description = "Which of your items are considered",
		position = 0
	)
	String sourcesSection = "sources";

	@ConfigSection(
		name = "Assumptions",
		description = "Buffs assumed in the DPS calculation. These are inputs to the maths, not recommendations.",
		position = 1
	)
	String assumptionsSection = "assumptions";

	@ConfigSection(
		name = "Target",
		description = "Which monster the DPS is calculated against",
		position = 2
	)
	String targetSection = "target";

	@ConfigSection(
		name = "Preparation",
		description = "Inventory, supply and teleport suggestions for the task",
		position = 3
	)
	String prepSection = "preparation";

	@ConfigSection(
		name = "Display",
		description = "Highlighting options",
		position = 4
	)
	String displaySection = "display";

	@ConfigItem(
		keyName = "includeEquipped",
		name = "Include worn items",
		description = "Also consider the items you are currently wearing",
		section = sourcesSection,
		position = 0
	)
	default boolean includeEquipped()
	{
		return true;
	}

	@ConfigItem(
		keyName = "includeInventory",
		name = "Include inventory",
		description = "Also consider the items in your inventory",
		section = sourcesSection,
		position = 1
	)
	default boolean includeInventory()
	{
		return true;
	}

	@ConfigItem(
		keyName = "meleePrayer",
		name = "Melee prayer",
		description = "Melee prayer assumed to be active in the calculation",
		section = assumptionsSection,
		position = 0
	)
	default MeleePrayer meleePrayer()
	{
		return MeleePrayer.NONE;
	}

	@ConfigItem(
		keyName = "rangedPrayer",
		name = "Ranged prayer",
		description = "Ranged prayer assumed to be active in the calculation",
		section = assumptionsSection,
		position = 1
	)
	default RangedPrayer rangedPrayer()
	{
		return RangedPrayer.NONE;
	}

	@ConfigItem(
		keyName = "magicPrayer",
		name = "Magic prayer",
		description = "Magic prayer assumed to be active in the calculation",
		section = assumptionsSection,
		position = 2
	)
	default MagicPrayer magicPrayer()
	{
		return MagicPrayer.NONE;
	}

	@ConfigItem(
		keyName = "meleeBoost",
		name = "Melee boost",
		description = "Potion boost assumed for Attack and Strength",
		section = assumptionsSection,
		position = 3
	)
	default MeleeBoost meleeBoost()
	{
		return MeleeBoost.NONE;
	}

	@ConfigItem(
		keyName = "rangedBoost",
		name = "Ranged boost",
		description = "Potion boost assumed for Ranged",
		section = assumptionsSection,
		position = 4
	)
	default RangedBoost rangedBoost()
	{
		return RangedBoost.NONE;
	}

	@ConfigItem(
		keyName = "magicBoost",
		name = "Magic boost",
		description = "Boost assumed for Magic",
		section = assumptionsSection,
		position = 5
	)
	default MagicBoost magicBoost()
	{
		return MagicBoost.NONE;
	}

	@ConfigItem(
		keyName = "spellbook",
		name = "Spellbook",
		description = "Spellbook used for autocast spells. Current uses the spellbook you are on (lunar and arceuus fall back to standard).",
		section = assumptionsSection,
		position = 6
	)
	default SpellbookChoice spellbook()
	{
		return SpellbookChoice.CURRENT;
	}

	@ConfigItem(
		keyName = "blowpipeDart",
		name = "Blowpipe darts",
		description = "Darts assumed to be loaded in any blowpipe",
		section = assumptionsSection,
		position = 7
	)
	default BlowpipeDart blowpipeDart()
	{
		return BlowpipeDart.DRAGON;
	}

	@ConfigItem(
		keyName = "kandarinHard",
		name = "Kandarin hard diary",
		description = "Enchanted bolt effects trigger 10% more often",
		section = assumptionsSection,
		position = 8
	)
	default boolean kandarinHard()
	{
		return false;
	}

	@ConfigItem(
		keyName = "targetOverride",
		name = "Target override",
		description = "Monster name to calculate against instead of your Slayer task, e.g. Abyssal demon. Leave empty to use your task.",
		section = targetSection,
		position = 0
	)
	default String targetOverride()
	{
		return "";
	}

	@ConfigItem(
		keyName = "showPreparation",
		name = "Show preparation",
		description = "Suggest an inventory (task items, potions, food) and teleports for the task",
		section = prepSection,
		position = 0
	)
	default boolean showPreparation()
	{
		return true;
	}

	@Range(max = 27)
	@ConfigItem(
		keyName = "prayerPotions",
		name = "Prayer potions",
		description = "How many prayer potions (or super restores) to suggest",
		section = prepSection,
		position = 1
	)
	default int prayerPotions()
	{
		return 4;
	}

	@Range(max = 27)
	@ConfigItem(
		keyName = "combatPotions",
		name = "Combat potions",
		description = "How many boosting potions for your best style to suggest",
		section = prepSection,
		position = 2
	)
	default int combatPotions()
	{
		return 1;
	}

	@ConfigItem(
		keyName = "fillFood",
		name = "Fill with food",
		description = "Fill the rest of the inventory with your best food",
		section = prepSection,
		position = 3
	)
	default boolean fillFood()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightSupplies",
		name = "Highlight supplies",
		description = "Outline suggested supplies in your bank as well as gear",
		section = prepSection,
		position = 4
	)
	default boolean highlightSupplies()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "supplyHighlightColor",
		name = "Supply highlight colour",
		description = "Colour of the outline around suggested supplies",
		section = prepSection,
		position = 5
	)
	default Color supplyHighlightColor()
	{
		return new Color(255, 200, 0, 200);
	}

	@ConfigItem(
		keyName = "highlightBank",
		name = "Highlight in bank",
		description = "Outline the chosen items in your bank",
		section = displaySection,
		position = 0
	)
	default boolean highlightBank()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "highlightColor",
		name = "Highlight colour",
		description = "Colour of the bank item outline",
		section = displaySection,
		position = 1
	)
	default Color highlightColor()
	{
		return new Color(0, 255, 140, 200);
	}

	@Getter
	@RequiredArgsConstructor
	enum MeleePrayer
	{
		NONE("None", 100, 100),
		ULTIMATE_STRENGTH_INCREDIBLE_REFLEXES("Ultimate Str + Inc Reflexes", 115, 115),
		CHIVALRY("Chivalry", 115, 118),
		PIETY("Piety", 120, 123);

		private final String name;
		private final int accuracy;
		private final int strength;

		@Override
		public String toString()
		{
			return name;
		}
	}

	@Getter
	@RequiredArgsConstructor
	enum RangedPrayer
	{
		NONE("None", 100, 100),
		EAGLE_EYE("Eagle Eye", 115, 115),
		DEADEYE("Deadeye", 118, 118),
		RIGOUR("Rigour", 120, 123);

		private final String name;
		private final int accuracy;
		private final int strength;

		@Override
		public String toString()
		{
			return name;
		}
	}

	@Getter
	@RequiredArgsConstructor
	enum MagicPrayer
	{
		NONE("None", 100, 0),
		MYSTIC_MIGHT("Mystic Might", 115, 20),
		MYSTIC_VIGOUR("Mystic Vigour", 118, 30),
		AUGURY("Augury", 125, 40);

		private final String name;
		private final int accuracy;
		/** tenths of a percent */
		private final int damage;

		@Override
		public String toString()
		{
			return name;
		}
	}

	@Getter
	@RequiredArgsConstructor
	enum MeleeBoost
	{
		NONE("None", 0, 0),
		COMBAT("Combat potion", 3, 10),
		SUPER_COMBAT("Super combat", 5, 15);

		private final String name;
		private final int flat;
		private final int percent;

		@Override
		public String toString()
		{
			return name;
		}
	}

	@Getter
	@RequiredArgsConstructor
	enum RangedBoost
	{
		NONE("None", 0, 0),
		RANGING("Ranging potion", 4, 10);

		private final String name;
		private final int flat;
		private final int percent;

		@Override
		public String toString()
		{
			return name;
		}
	}

	@Getter
	@RequiredArgsConstructor
	enum MagicBoost
	{
		NONE("None", 0, 0),
		MAGIC_POTION("Magic potion", 4, 0),
		IMBUED_HEART("Imbued heart", 1, 10),
		SATURATED_HEART("Saturated heart", 4, 10);

		private final String name;
		private final int flat;
		private final int percent;

		@Override
		public String toString()
		{
			return name;
		}
	}

	@Getter
	@RequiredArgsConstructor
	enum SpellbookChoice
	{
		CURRENT("Current"),
		STANDARD("Standard"),
		ANCIENT("Ancient");

		private final String name;

		@Override
		public String toString()
		{
			return name;
		}
	}

	@Getter
	@RequiredArgsConstructor
	enum BlowpipeDart
	{
		BRONZE("Bronze", 1),
		IRON("Iron", 3),
		STEEL("Steel", 4),
		BLACK("Black", 6),
		MITHRIL("Mithril", 7),
		ADAMANT("Adamant", 10),
		RUNE("Rune", 14),
		AMETHYST("Amethyst", 28),
		DRAGON("Dragon", 35);

		private final String name;
		private final int rangedStrength;

		@Override
		public String toString()
		{
			return name;
		}
	}
}
