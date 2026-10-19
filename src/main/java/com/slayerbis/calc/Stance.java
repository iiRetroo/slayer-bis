package com.slayerbis.calc;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Stance
{
	ACCURATE("Accurate"),
	AGGRESSIVE("Aggressive"),
	CONTROLLED("Controlled"),
	DEFENSIVE("Defensive"),
	RAPID("Rapid"),
	LONGRANGE("Longrange"),
	AUTOCAST("Autocast");

	private final String displayName;

	@Override
	public String toString()
	{
		return displayName;
	}
}
