//
// LIFERULES.JAVA
//

package com.shutdownhook.life.lifelib;

import java.util.logging.Logger;

public class LifeRules implements Rules.Rule
{
	private final String MASK_COUNT = "3 3 1 " + "XXX" +
		                                         "X.X" +
		                                         "XXX";

	private final String MASK_ALIVE = "3 3 1 " + "..." +
	                                             ".X." +
		                                         "...";

	public LifeRules() throws Exception {
		this.countMask = Serializers.fromString(MASK_COUNT).getAsDNA()[0];
		this.aliveMask = Serializers.fromString(MASK_ALIVE).getAsDNA()[0];
		
		log.fine(String.format("aliveMask=%s; countMask=%s",
							   Long.toBinaryString(aliveMask),
							   Long.toBinaryString(countMask)));
	}
		
	// +------------+
	// | Rules.Rule |
	// +------------+

	public Rules.Outcome apply(Bitmap3D neighborhood) {

		boolean alive = ((neighborhood.getAsDNA()[0] & aliveMask) != 0);
		int neighbors = Long.bitCount(neighborhood.getAsDNA()[0] & countMask);

		if (alive) {
			if (neighbors < 2) return(Rules.Outcome.Off);
			if (neighbors < 4) return(Rules.Outcome.On);
			return(Rules.Outcome.Off);
		}

		return(neighbors == 3 ? Rules.Outcome.On : Rules.Outcome.Off);
	}
	
	public int getRadius() { return(1); }
	public Bitmap3D.EdgeStrategy getEdgeStrategy() { return(Bitmap3D.EdgeStrategy.Wrap); }

	// +---------+
	// | Members |
	// +---------+

	private long countMask;
	private long aliveMask;

	private final static Logger log = Logger.getLogger(LifeRules.class.getName());

	// +----------+
	// | Patterns |
	// +----------+

	public final static String BLINKER =
		"5 5 1 " + "....." +
		           "....." +
		           ".XXX." +
		           "....." +
		           "....." ;

	public final static String LWSS =
		"10 10 1 " + ".......... " + 
		             ".........." +
		             "...XXXX..." +
		             "..X...X..." +
		             "......X..." +
		             "..X..X...." +
		             ".........." +
		             ".........." +
		             ".........." +
		             ".........." ;

	public final static String PULSAR =
		"17 17 1 " + "................." + 
		             "................." +
		             "....XXX...XXX...." +
		             "................." +
		             "..X....X.X....X.." +
		             "..X....X.X....X.." +
		             "..X....X.X....X.." +
		             "....XXX...XXX...." +
		             "................." +
		             "....XXX...XXX...." +
		             "..X....X.X....X.." +
		             "..X....X.X....X.." +
		             "..X....X.X....X.." +
		             "................." +
		             "....XXX...XXX...." +
		             "................." +
		             "................." ;



}
