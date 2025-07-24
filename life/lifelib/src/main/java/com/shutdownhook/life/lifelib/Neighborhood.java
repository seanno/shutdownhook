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
		Cross2,
		VonNeumannEdgeDetect,
		VonNeumannQuadrants,
		VonNeumannHalves,
		MooreHalves,
		VonNeumannEdgeHalves,
		Diagonals
	}
	
	// +-----+
	// | get |
	// +-----+

	// packs the relatives into an int that can be used as an index

	public static int get(Bitmap bits, int x, int y,
						  NeighborhoodType neighborhoodType) {

		int[][] relatives = NEIGHBORHOOD_RELATIVES[neighborhoodType.ordinal()];
		
		int val = 0;
		boolean thisVal;

		int dx = bits.getDx();
		int dy = bits.getDy();
		
		for (int i = 0; i < relatives.length; ++i) {
			
			val = val << 1;
			
			switch (relatives[i][0]) {
				
				case EDGE:
					thisVal = (x == 0 || y == 0 || x == (dx - 1) || y == (dy - 1));
					break;

				case NS:
					thisVal = (y < (dy / 2));
					break;

				case WE:
					thisVal = (x < (dx / 2));
					break;

				case NW:
					thisVal = (x < (dx / 2) && y < (dy / 2));
					break;

				case NE:
					thisVal = (x >= (dx / 2) && y < (dy / 2));
					break;

				case SW:
					thisVal = (x < (dx / 2) && y >= (dy / 2));
					break;

				case SE:
					thisVal = (x >= (dx / 2) && y >= (dy / 2));
					break;

				default:
					thisVal = bits.getRelative(x, y, relatives[i][0], relatives[i][1]);
					break;
			}
			
			if (thisVal) val |= 1;
		}

		return(val);
	}

	// +---------+
	// | reverse |
	// +---------+

	// given a packed int, return a string showing the neighborhood mask. We don't
	// return a Bitmap because we need tristate cells (1, 0, ignored) to understand
	// what we're looking at

	public static int[][] reverse(int val, NeighborhoodType neighborhoodType) {
		
		int[][] relatives = NEIGHBORHOOD_RELATIVES[neighborhoodType.ordinal()];

		int xMin = Integer.MAX_VALUE;
		int xMax = Integer.MIN_VALUE;
		int yMin = Integer.MAX_VALUE;
		int yMax = Integer.MIN_VALUE;

		for (int i = 0; i < relatives.length; ++i) {
			if (relatives[i][0] < xMin) xMin = relatives[i][0];
			if (relatives[i][0] > xMax) xMax = relatives[i][0];
			if (relatives[i][1] < yMin) yMin = relatives[i][1];
			if (relatives[i][1] > yMax) yMax = relatives[i][1];
		}

		int dx = xMax - xMin + 1;
		int dy = yMax - yMin + 1;
		int[][] grid = new int[dx][dy];
		
		for (int x = 0; x < dx; ++x) {
			for (int y = 0; y < dy; ++y) {
				grid[x][y] = -1;
			}
		}

		for (int i = 0; i < relatives.length; ++i) {
			int cell = ((val & (0x1 << i)) == 0) ? 0 : 1;
			int x = relatives[i][0] - xMin;
			int y = relatives[i][1] - yMin;
			grid[x][y] = cell;
		}

		return(grid);
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

	private static final int EDGE = 100000;
	private static final int NW   = 100001;
	private static final int NE   = 100002;
	private static final int SW   = 100003;
	private static final int SE   = 100004;
	private static final int NS   = 100005;
	private static final int WE   = 100006;
	
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
		},{
			// VON NEUMANN - EDGE DETECT
			
			          { 0, -1},
			{-1,  0}, { 0,  0}, { 1,  0},
			          { 0,  1},   
			          { EDGE, EDGE }   
		},{
			// VON NEUMANN - HALVES
			
			          { 0, -1},
			{-1,  0}, { 0,  0}, { 1,  0},
			          { 0,  1},   
                      { NS, NS }, 
					  { WE, WE }
		},{
			// VON NEUMANN - QUADRANTS
			
			          { 0, -1},
			{-1,  0}, { 0,  0}, { 1,  0},
			          { 0,  1},   
                      { NW, NW }, {NE, NE},
					  { SW, SW }, {SE, SE}
		},{
			// MOORE - HALVES
			
			{-1, -1}, { 0, -1},	{ 1, -1},
			{-1,  0}, { 0,  0},	{ 1,  0},
			{-1,  1}, { 0,  1}, { 1,  1},
			{NS, NS}, {WE, WE}
		},{
			// VON NEUMANN - EDGE HALVES
			
			          { 0, -1},
			{-1,  0}, { 0,  0}, { 1,  0},
			          { 0,  1},   
                      { NS, NS }, 
					  { WE, WE },
					  { EDGE, EDGE }
		},{
			// DIAGONALS
			
			{-1, -1},           { 1, -1},
			          { 0,  0},
			{-1,  1},           { 1,  1}
		}
	};
}
