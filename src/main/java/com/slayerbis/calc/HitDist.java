package com.slayerbis.calc;

import java.util.Arrays;

/**
 * Probability distribution of the damage dealt by one hitsplat, split by whether the
 * hit was accurate. Mirrors the wiki calculator's HitDistribution closely enough to apply
 * the same transforms (minimum-1 accurate hits, flat armour, bolt effects, etc).
 */
final class HitDist
{
	interface Transform
	{
		/**
		 * Adds the outcomes of transforming one hit of the given probability to {@code out}.
		 */
		void apply(int damage, boolean accurate, double probability, HitDist out);
	}

	private double[] accurate;
	private double[] inaccurate;

	HitDist(int maxDamage)
	{
		accurate = new double[Math.max(1, maxDamage + 1)];
		inaccurate = new double[1];
	}

	static HitDist linear(double accuracy, int min, int max)
	{
		HitDist d = new HitDist(max);
		double p = accuracy / (max - min + 1);
		for (int i = min; i <= max; i++)
		{
			d.accurate[i] += p;
		}
		d.inaccurate[0] += 1 - accuracy;
		return d;
	}


	void add(int damage, boolean isAccurate, double probability)
	{
		if (probability == 0)
		{
			return;
		}
		damage = Math.max(0, damage);
		if (isAccurate)
		{
			if (damage >= accurate.length)
			{
				accurate = Arrays.copyOf(accurate, damage + 1);
			}
			accurate[damage] += probability;
		}
		else
		{
			if (damage >= inaccurate.length)
			{
				inaccurate = Arrays.copyOf(inaccurate, damage + 1);
			}
			inaccurate[damage] += probability;
		}
	}

	HitDist transform(Transform t, boolean transformInaccurate)
	{
		HitDist out = new HitDist(accurate.length - 1);
		for (int i = 0; i < accurate.length; i++)
		{
			if (accurate[i] != 0)
			{
				t.apply(i, true, accurate[i], out);
			}
		}
		for (int i = 0; i < inaccurate.length; i++)
		{
			if (inaccurate[i] == 0)
			{
				continue;
			}
			if (transformInaccurate)
			{
				t.apply(i, false, inaccurate[i], out);
			}
			else
			{
				out.add(i, false, inaccurate[i]);
			}
		}
		return out;
	}

	/**
	 * Multiplies every hit's damage by num/den, truncating like the game does.
	 */
	HitDist scaleDamage(int num, int den)
	{
		return transform((dmg, acc, p, out) -> out.add(dmg * num / den, acc, p), true);
	}

	/**
	 * Weighted mix of two distributions: this with weight w, other with 1-w.
	 */
	HitDist mix(HitDist other, double w)
	{
		HitDist out = new HitDist(Math.max(accurate.length, other.accurate.length));
		addScaled(out, this, w);
		addScaled(out, other, 1 - w);
		return out;
	}

	private static void addScaled(HitDist out, HitDist src, double w)
	{
		for (int i = 0; i < src.accurate.length; i++)
		{
			out.add(i, true, src.accurate[i] * w);
		}
		for (int i = 0; i < src.inaccurate.length; i++)
		{
			out.add(i, false, src.inaccurate[i] * w);
		}
	}

	double expected()
	{
		double e = 0;
		for (int i = 1; i < accurate.length; i++)
		{
			e += i * accurate[i];
		}
		for (int i = 1; i < inaccurate.length; i++)
		{
			e += i * inaccurate[i];
		}
		return e;
	}

	int max()
	{
		int m = 0;
		for (int i = accurate.length - 1; i > 0; i--)
		{
			if (accurate[i] > 0)
			{
				m = i;
				break;
			}
		}
		for (int i = inaccurate.length - 1; i > m; i--)
		{
			if (inaccurate[i] > 0)
			{
				return i;
			}
		}
		return m;
	}
}
