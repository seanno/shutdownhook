//
// MISC.JAVA
//

package com.shutdownhook.life.lifelib;

import java.util.logging.Logger;

import com.shutdownhook.life.lifelib.Neighborhood.NeighborhoodType;

public class Misc
{
	// +-----------------------+
	// | convertRules_VN2Moore |
	// +-----------------------+

	public static NeighborhoodRulesProcessor
		convertRules_VN2Moore(NeighborhoodRulesProcessor vnRules) {

		NeighborhoodRulesProcessor mooreRules = new NeighborhoodRulesProcessor(NeighborhoodType.Moore);

		Bitmap vnBits = vnRules.getVals();
		Bitmap mooreBits = mooreRules.getVals();
		
		for (int vn = 0; vn < vnBits.getDx(); ++vn) {

			boolean val = vnBits.get(vn, 0);
				
			int moore = 0;
			if ((vn & 0b1) != 0) moore |= 0b10;
			if ((vn & 0b10) != 0) moore |= 0b1000;
			if ((vn & 0b100) != 0) moore |= 0b10000;
			if ((vn & 0b1000) != 0) moore |= 0b100000;
			if ((vn & 0b10000) != 0) moore |= 0b10000000;

			permuteValuesRecursive(mooreBits, moore, val, 0);
		}

		return(mooreRules);
	}

	private static void permuteValuesRecursive(Bitmap bm, int startingBits, boolean val, int i) {
		
		if (i == PERMUTE_BITS.length) {
			bm.set(startingBits, 0, val);
			return;
		}

		permuteValuesRecursive(bm, startingBits, val, i + 1);
		permuteValuesRecursive(bm, startingBits | PERMUTE_BITS[i], val, i + 1);
	}

	static int[] PERMUTE_BITS = { 0b1, 0b100, 0b1000000, 0b100000000 };
		
	// +---------+
	// | Members |
	// +---------+

	private final static Logger log = Logger.getLogger(Misc.class.getName());
}
