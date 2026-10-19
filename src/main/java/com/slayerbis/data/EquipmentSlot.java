package com.slayerbis.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EquipmentSlot
{
	HEAD("head", "Head"),
	CAPE("cape", "Cape"),
	NECK("neck", "Neck"),
	AMMO("ammo", "Ammo"),
	WEAPON("weapon", "Weapon"),
	BODY("body", "Body"),
	SHIELD("shield", "Shield"),
	LEGS("legs", "Legs"),
	HANDS("hands", "Hands"),
	FEET("feet", "Feet"),
	RING("ring", "Ring");

	private final String key;
	private final String displayName;

	public static EquipmentSlot fromKey(String key)
	{
		for (EquipmentSlot s : values())
		{
			if (s.key.equals(key))
			{
				return s;
			}
		}
		return null;
	}
}
