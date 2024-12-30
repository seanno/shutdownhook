//
// NEIGHBORHOODRULES.JAVA
//

package com.shutdownhook.life.lifelib;

import java.util.Set;
import java.util.HashSet;
import java.util.logging.Logger;

import com.shutdownhook.life.lifelib.Bitmap.EdgeStrategy;
import com.shutdownhook.life.lifelib.Rules.Outcome;

public class NeighborhoodRules implements Rules.RuleProcessor
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

	// +----------+
	// | Creation |
	// +----------+


	public NeighborhoodRules(NeighborhoodType neighborhoodType,
							 EdgeStrategy edgeStrategy) throws IllegalArgumentException {

		this(KNOWN_POINTS[neighborhoodType.ordinal()], edgeStrategy);
	}

	public NeighborhoodRules(int[][] points,
							 EdgeStrategy edgeStrategy) throws IllegalArgumentException {

		this(points, edgeStrategy, null);
	}
	
	public NeighborhoodRules(int[][] points,
							 EdgeStrategy edgeStrategy,
							 Bitmap vals) throws IllegalArgumentException {
		
		if (points.length > (Integer.SIZE - 1)) {
			// (Integer.SIZE - 1) bc we use them to index an array, so can't be negative
			throw new IllegalArgumentException("Too many points for NeighborhoodRules");
		}
			
		this.points = points;
		this.edgeStrategy = edgeStrategy;

		int dx = (int) Math.pow(2, points.length);
		
		if (vals == null) {
			this.vals = new Bitmap(dx, 1);
			this.vals.randomize();
		}
		else {
			if (vals.getDx() != dx || vals.getDy() != 1) {
				throw new IllegalArgumentException("NeighborhoodRules provided vals must match points");
			}
			
			this.vals = vals;
		}
	}

	// +------------+
	// | Rules.Rule |
	// +------------+

	public Outcome apply(Bitmap env, int x, int y) {

		int val = 0;

		for (int i = 0; i < points.length; ++i) {
			val = val << 1;
			boolean thisVal = env.getRelative(x, y, points[i][0], points[i][1], edgeStrategy);
			if (thisVal) val |= 1;
		}

		return(vals.get(val, 0) ? Outcome.On : Outcome.Off);
	}

	// +---------+
	// | Members |
	// +---------+

	private int[][] points;
	private EdgeStrategy edgeStrategy;
	protected Bitmap vals;

	private final static Logger log = Logger.getLogger(NeighborhoodRules.class.getName());

	// +--------------------------+
	// | Known Neighborhood Types |
	// +--------------------------+

	private static final int[][][] KNOWN_POINTS = {
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
