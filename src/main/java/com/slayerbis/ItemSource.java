package com.slayerbis;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
enum ItemSource
{
	WORN("worn"),
	INVENTORY("inventory"),
	BANK("bank"),
	GROUP_STORAGE("group storage");

	private final String label;
}
