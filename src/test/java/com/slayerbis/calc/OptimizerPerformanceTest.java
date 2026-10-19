package com.slayerbis.calc;

import com.slayerbis.data.AttackType;
import com.slayerbis.data.GameData;
import com.slayerbis.data.GearItem;
import com.slayerbis.data.Monster;
import com.slayerbis.data.TaskMonsterResolver;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertTrue;

/**
 * A worst-case bank: one of every equippable item in the game. Checks the search still finishes.
 */
public class OptimizerPerformanceTest
{
	@Test(timeout = 60_000)
	public void everyItemInTheGame() throws Exception
	{
		GameData data = GameData.load(new Gson());
		Monster target = new TaskMonsterResolver(data).fromName("Abyssal demons");
		List<Optimizer.Candidate> bank = new ArrayList<>();
		for (int id = 0; id < 40_000; id++)
		{
			GearItem item = data.getItem(id);
			if (item != null && item.getId() == id)
			{
				bank.add(new Optimizer.Candidate(item, id));
			}
		}

		Optimizer optimizer = new Optimizer(DpsCalculatorTest.maxed(true), target);
		for (AttackType type : AttackType.values())
		{
			long start = System.nanoTime();
			StyleResult r = optimizer.optimise(bank, type);
			long ms = (System.nanoTime() - start) / 1_000_000;
			System.out.printf("%s: %.3f dps, %d -> %d items, %d combinations, %d ms, stopped early %s%n",
				type, r.getResult().getDps(), r.getItemsConsidered(), r.getItemsAfterPruning(),
				r.getCombinationsTried(), ms, r.isStoppedEarly());
			StringBuilder sb = new StringBuilder();
			for (Optimizer.Candidate c : r.getGear())
			{
				if (c != null)
				{
					sb.append(c.getItem().getName()).append(", ");
				}
			}
			System.out.println("  " + sb);
			assertTrue(r.isValid());
		}
	}
}
