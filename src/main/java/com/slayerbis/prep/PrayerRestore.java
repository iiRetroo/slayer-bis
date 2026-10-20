package com.slayerbis.prep;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Which prayer restores to suggest first. Moonlight moth mixes are cheaper but restore less, so
 * some players save their prayer potions for harder content.
 */
@Getter
@RequiredArgsConstructor
public enum PrayerRestore
{
	POTIONS_FIRST("Prayer potions first"),
	MIXES_FIRST("Moth mixes first"),
	POTIONS_ONLY("Prayer potions only");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}
}
