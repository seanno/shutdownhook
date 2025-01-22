//
// LIFERULESPROCESSOR.JAVA
//

package com.shutdownhook.life.lifelib;

import java.util.logging.Logger;

import com.shutdownhook.life.lifelib.Bitmap.EdgeStrategy;
import com.shutdownhook.life.lifelib.Neighborhood.NeighborhoodType;


public class LifeRulesProcessor extends NeighborhoodRulesProcessor
{
	public LifeRulesProcessor() {
		
		super(NeighborhoodType.Moore);

		for (int i = 0; i < 512; ++i) {

			boolean alive = ((i & (1 << 4)) != 0); // center of the moore neighborhood
			int count = Long.bitCount(i) - (alive ? 1 : 0);

			boolean newVal = false;
			if ((alive && count >= 2 && count <= 3) ||
				(!alive && count == 3)) {

				newVal = true;
			}

			vals.set(i, 0, newVal);
		}
	}
	
	// +--------------------------+
	// | RulesProcessor.reproduce |
	// +--------------------------+

	@Override
	public Rules.RulesProcessor reproduce(Rules.RulesProcessor other,
									Reproduction.Params params)
		throws IllegalArgumentException {

		log.warning("LifeRulesProcessor reproduction is identity; is that what you want?");
		return(this);
	}

	// +---------+
	// | Members |
	// +---------+

	private final static Logger log = Logger.getLogger(LifeRulesProcessor.class.getName());

	// +----------+
	// | Patterns |
	// +----------+

	public final static String BLINKER =
		"5 5 Wrap " + "....." +
		              "....." +
		              ".XXX." +
		              "....." +
		              "....." ;

	public final static String LWSS =
		"20 10 Wrap " + "...................." + 
		                "...................." +
		                "...XXXX............." +
		                "..X...X............." +
		                "......X............." +
		                "..X..X.............." +
		                "...................." +
		                "...................." +
		                "...................." +
		                "...................." ;

	public final static String PULSAR =
		"17 17 Wrap " + "................." + 
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

	public final static String COMBO =
		"47 17 Wrap " + "..............................................." + 
		                "..............................................." +
		                "....XXX...XXX.......XXX.....XX..........XXX...." +
		                "............................XX.........XXX....." +
		                "..X....X.X....X...............XX..............." +
		                "..X....X.X....X...............XX..............." +
		                "..X....X.X....X................................" +
		                "....XXX...XXX.................................." +
		                "..............................................." +
		                "....XXX...XXX..........XXXX...................." +
		                "..X....X.X....X.......X...X...................." +
		                "..X....X.X....X...........X...................." +
		                "..X....X.X....X.......X..X....................." +
		                "..............................................." +
		                "....XXX...XXX.................................." +
		                "..............................................." +
		                "..............................................." ;

}
