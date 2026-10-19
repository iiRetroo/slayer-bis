package com.slayerbis.calc;

import com.slayerbis.data.AttackType;
import com.slayerbis.data.EquipmentSlot;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;

/**
 * Armour sets whose effect only applies when every piece is worn. Each owned set is tried as a
 * single candidate that fills all of its slots at once.
 */
@Getter
public final class GearSets
{
	private final String name;
	private final Set<AttackType> types;
	private final boolean ancientsOnly;
	/** For each slot, the accepted item names (any one of them completes that slot). */
	private final Map<EquipmentSlot, List<String>> pieces;

	private GearSets(String name, Set<AttackType> types, boolean ancientsOnly, Map<EquipmentSlot, List<String>> pieces)
	{
		this.name = name;
		this.types = types;
		this.ancientsOnly = ancientsOnly;
		this.pieces = pieces;
	}

	private static final Set<AttackType> MELEE = EnumSet.of(AttackType.STAB, AttackType.SLASH, AttackType.CRUSH);

	public static final List<GearSets> ALL = ImmutableList.of(
		set("Void Knight (melee)", MELEE, false,
			EquipmentSlot.HEAD, names("Void melee helm", "Void melee helm (or)"),
			EquipmentSlot.BODY, names("Void knight top", "Void knight top (or)"),
			EquipmentSlot.LEGS, names("Void knight robe", "Void knight robe (or)"),
			EquipmentSlot.HANDS, names("Void knight gloves")),
		set("Elite Void (melee)", MELEE, false,
			EquipmentSlot.HEAD, names("Void melee helm", "Void melee helm (or)"),
			EquipmentSlot.BODY, names("Elite void top", "Elite void top (or)"),
			EquipmentSlot.LEGS, names("Elite void robe", "Elite void robe (or)"),
			EquipmentSlot.HANDS, names("Void knight gloves")),
		set("Void Knight (ranged)", EnumSet.of(AttackType.RANGED), false,
			EquipmentSlot.HEAD, names("Void ranger helm", "Void ranger helm (or)"),
			EquipmentSlot.BODY, names("Void knight top", "Void knight top (or)"),
			EquipmentSlot.LEGS, names("Void knight robe", "Void knight robe (or)"),
			EquipmentSlot.HANDS, names("Void knight gloves")),
		set("Elite Void (ranged)", EnumSet.of(AttackType.RANGED), false,
			EquipmentSlot.HEAD, names("Void ranger helm", "Void ranger helm (or)"),
			EquipmentSlot.BODY, names("Elite void top", "Elite void top (or)"),
			EquipmentSlot.LEGS, names("Elite void robe", "Elite void robe (or)"),
			EquipmentSlot.HANDS, names("Void knight gloves")),
		set("Void Knight (magic)", EnumSet.of(AttackType.MAGIC), false,
			EquipmentSlot.HEAD, names("Void mage helm", "Void mage helm (or)"),
			EquipmentSlot.BODY, names("Void knight top", "Void knight top (or)"),
			EquipmentSlot.LEGS, names("Void knight robe", "Void knight robe (or)"),
			EquipmentSlot.HANDS, names("Void knight gloves")),
		set("Elite Void (magic)", EnumSet.of(AttackType.MAGIC), false,
			EquipmentSlot.HEAD, names("Void mage helm"),
			EquipmentSlot.BODY, names("Elite void top"),
			EquipmentSlot.LEGS, names("Elite void robe"),
			EquipmentSlot.HANDS, names("Void knight gloves")),
		set("Obsidian armour", MELEE, false,
			EquipmentSlot.HEAD, names("Obsidian helmet"),
			EquipmentSlot.BODY, names("Obsidian platebody"),
			EquipmentSlot.LEGS, names("Obsidian platelegs")),
		set("Inquisitor's armour", EnumSet.of(AttackType.CRUSH), false,
			EquipmentSlot.HEAD, names("Inquisitor's great helm"),
			EquipmentSlot.BODY, names("Inquisitor's hauberk"),
			EquipmentSlot.LEGS, names("Inquisitor's plateskirt")),
		set("Crystal armour", EnumSet.of(AttackType.RANGED), false,
			EquipmentSlot.HEAD, names("Crystal helm"),
			EquipmentSlot.BODY, names("Crystal body"),
			EquipmentSlot.LEGS, names("Crystal legs")),
		set("Verac's equipment", MELEE, false,
			EquipmentSlot.HEAD, names("Verac's helm"),
			EquipmentSlot.BODY, names("Verac's brassard"),
			EquipmentSlot.LEGS, names("Verac's plateskirt"),
			EquipmentSlot.WEAPON, names("Verac's flail")),
		set("Karil's equipment", EnumSet.of(AttackType.RANGED), false,
			EquipmentSlot.HEAD, names("Karil's coif"),
			EquipmentSlot.BODY, names("Karil's leathertop"),
			EquipmentSlot.LEGS, names("Karil's leatherskirt"),
			EquipmentSlot.WEAPON, names("Karil's crossbow"),
			EquipmentSlot.NECK, names("Amulet of the Damned")),
		set("Ahrim's equipment", EnumSet.of(AttackType.MAGIC), false,
			EquipmentSlot.HEAD, names("Ahrim's hood"),
			EquipmentSlot.BODY, names("Ahrim's robetop"),
			EquipmentSlot.LEGS, names("Ahrim's robeskirt"),
			EquipmentSlot.WEAPON, names("Ahrim's staff"),
			EquipmentSlot.NECK, names("Amulet of the Damned")),
		set("Virtus robes", EnumSet.of(AttackType.MAGIC), true,
			EquipmentSlot.HEAD, names("Virtus mask"),
			EquipmentSlot.BODY, names("Virtus robe top"),
			EquipmentSlot.LEGS, names("Virtus robe bottom"))
	);

	private static List<String> names(String... names)
	{
		return Collections.unmodifiableList(Arrays.asList(names));
	}

	@SuppressWarnings("unchecked")
	private static GearSets set(String name, Set<AttackType> types, boolean ancientsOnly, Object... slotNamePairs)
	{
		Map<EquipmentSlot, List<String>> pieces = new EnumMap<>(EquipmentSlot.class);
		for (int i = 0; i < slotNamePairs.length; i += 2)
		{
			pieces.put((EquipmentSlot) slotNamePairs[i], (List<String>) slotNamePairs[i + 1]);
		}
		return new GearSets(name, Collections.unmodifiableSet(types), ancientsOnly, Collections.unmodifiableMap(pieces));
	}
}
