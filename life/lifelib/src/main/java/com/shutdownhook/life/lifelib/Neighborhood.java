//
// NEIGHBORHOOD.JAVA
//

package com.shutdownhook.life.lifelib;

import java.util.logging.Logger;

public class Neighborhood
{
	// +--------------------+
	// | Neighborhood Types |
	// +--------------------+

	public static enum NeighborhoodType
	{
		Moore,
		VonNeumann,
		VonNeumannR2,
		Cross2
	}
	
	// +-----+
	// | get |
	// +-----+

	// packs the relatives into an int that can be used as an index

	public static int get(Bitmap bits, int x, int y,
						  NeighborhoodType neighborhoodType) {

		int[][] relatives = NEIGHBORHOOD_RELATIVES[neighborhoodType.ordinal()];
		
		int val = 0;

		for (int i = 0; i < relatives.length; ++i) {
			val = val << 1;
			boolean thisVal = bits.getRelative(x, y, relatives[i][0], relatives[i][1]);
			if (thisVal) val |= 1;
		}

		return(val);
	}
	
	// +------------------+
	// | getRelativeCount |
	// +------------------+

	public static int getRelativeCount(NeighborhoodType neighborhoodType) {
		return(NEIGHBORHOOD_RELATIVES[neighborhoodType.ordinal()].length);
	}

	// +---------+
	// | Members |
	// +---------+

	private final static Logger log = Logger.getLogger(Neighborhood.class.getName());

	// +------------------------------+
	// | Neighborhood Relative Arrays |
	// +------------------------------+

	private static final int[][][] NEIGHBORHOOD_RELATIVES = {
		{
			// MOORE
			
			{-1, -1}, { 0, -1},	{ 1, -1},
			{-1,  0}, { 0,  0},	{ 1,  0},
			{-1,  1}, { 0,  1}, { 1,  1}
		},{
			// VON NEUMANN
			
			          { 0, -1},
			{-1,  0}, { 0,  0}, { 1,  0},
			          { 0,  1}   
		},{
			// VON NEUMANN MANHATTAN R2
			
			                    { 0, -2},
			          {-1, -1}, { 0, -1}, { 1, -1},
			{-2,  0}, {-1,  0}, { 0,  0}, { 1,  0}, { 2,  0},
			          {-1,  1}, { 0,  1}, { 1,  1},
					            { 0,  2}
		},{
			// CROSS R2
			
			                    { 0, -2},
			                    { 0, -1},
			{-2,  0}, {-1,  0}, { 0,  0}, { 1,  0}, { 2,  0},
			                    { 0,  1},
			                    { 0,  2}
		}
	};
}
