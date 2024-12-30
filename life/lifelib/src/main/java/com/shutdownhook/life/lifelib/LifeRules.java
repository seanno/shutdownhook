//
// LIFERULES.JAVA
//

package com.shutdownhook.life.lifelib;

import java.util.logging.Logger;

import com.shutdownhook.life.lifelib.Bitmap.EdgeStrategy;
import com.shutdownhook.life.lifelib.NeighborhoodRules.NeighborhoodType;


public class LifeRules extends NeighborhoodRules
{
	public LifeRules() {
		
		super(NeighborhoodType.Moore, EdgeStrategy.Wrap);

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

		System.out.println(String.format(">>>> VAL FOR 7 IS: %s", vals.get(7,0)));
	}
	
	// +---------+
	// | Members |
	// +---------+

	private final static Logger log = Logger.getLogger(LifeRules.class.getName());

	// +----------+
	// | Patterns |
	// +----------+

	public final static String BLINKER =
		"5 5 " + "....." +
		         "....." +
		         ".XXX." +
		         "....." +
		         "....." ;

	public final static String LWSS =
		"20 10 " + "...................." + 
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
		"17 17 " + "................." + 
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
