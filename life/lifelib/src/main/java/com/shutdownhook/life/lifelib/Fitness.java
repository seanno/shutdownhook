//
// FITNESS.JAVA
//

package com.shutdownhook.life.lifelib;

import java.util.Arrays;
import java.util.Random;
import java.util.logging.Logger;

public class Fitness
{
	// +-------------+
	// | FitnessType |
	// +-------------+

	public static enum FitnessType {
		MostOn,
		MostOff,
		FiftyFifty,
		Edges,
		VStripes,
		VStripes2,
		VStripesCombo,
		VBlocks,
		Checkerboard
	}

	// +---------+
	// | compute |
	// +---------+

	public static double compute(Bitmap env, FitnessType fitnessType) {
		switch (fitnessType) {
			case MostOn: default: return(fractionWithValue(env, true));
			case MostOff: return(fractionWithValue(env, false));
			case FiftyFifty: return(fiftyFiftyScore(env));
			case Edges: return(edgesScore(env));
			case VStripes: return(vStripesScore(env));
			case VStripes2: return(vStripes2Score(env));
			case VStripesCombo: return(vStripesComboScore(env));
			case VBlocks: return(vBlocksScore(env));
			case Checkerboard: return(checkerboardScore(env));
		}
	}

	private static double fractionWithValue(Bitmap env, boolean val) {

		double countOn = (double) env.getTrueCount();
		double total = (double) (env.getDx() * env.getDy());
		double fractionOn = countOn / total;
		double score = (val ? fractionOn : 1.0 - fractionOn);
		
		return(score);
	}

	// how close to an even distribution
	
	private static double fiftyFiftyScore(Bitmap env) {
		
		double countOn = (double) env.getTrueCount();
		double total = (double) (env.getDx() * env.getDy());
		double fraction = countOn / (total / 2);
		double score = (fraction <= 1.0 ? fraction : 2.0 - fraction);

		return(score);
	}

	// average of % of edges correct and % center correct
	// this weights getting both regions correct, otherwise the
	// center region would dominate
	
	private static double edgesScore(Bitmap env) {

		int dx = env.getDx();
		int dy = env.getDy();

		int totalPoints = dx * dy;
		int edgePoints = (dx * 2) + (dy * 2) - 4;
		int centerPoints = totalPoints - edgePoints;
		int totalOn = env.getTrueCount();

		int edgesOn = 0;
		for (int x = 0; x < dx; ++x) {
			if (env.get(x, 0)) ++edgesOn;
			if (env.get(x, dy-1)) ++edgesOn;
		}

		for (int y = 1; y < (dy -1); ++y) {
			if (env.get(0, y)) ++edgesOn;
			if (env.get(dx-1, y)) ++edgesOn;
		}

		double edgeScore = ((double) edgesOn) / ((double) edgePoints);

		int centerOff = centerPoints - (totalOn - edgesOn);
		double nonEdgeScore = ((double) centerOff) / ((double) centerPoints);
		double score = ((edgeScore + nonEdgeScore) / 2.0);

		return(score);
	}
	
	// how close to a perfect grid of 1 pixel vertical stripes; note leftmost
	// stripe can be on or off --- that doesn't matter
	
	private static double vStripesScore(Bitmap env) {

		int dx = env.getDx();
		int dy = env.getDy();

		boolean evenVal = env.get(0,0);

		int countCorrect = 0;

		for (int x = 0; x < dx; x += 2) {
			for (int y = 0; y < dy; ++y) {
				if (env.get(x,y) == evenVal) ++countCorrect;
				if (x+1 < dx && (env.get(x+1,y) != evenVal)) ++countCorrect;
			}
		}
		
		double score = ((double)countCorrect) / ((double)(dx * dy));
		
		return(score);
	}

	// also rewards vertical striping, but uses the average continuous
	// length run of the correct value rather than just the number of
	// them ... the idea is to score more continuously rather than
	// be "all or nothing"
	
	private static double vStripes2Score(Bitmap env) {

		int dx = env.getDx();
		int dy = env.getDy();

		// figure out if we should start on or off

		double avgRunAccum = averageRunLengthOf(env, 0, true);
		boolean evenVal = true;
		
		double temp = averageRunLengthOf(env, 0, false);
		if (temp > avgRunAccum) {
			avgRunAccum = temp;
			evenVal = false;
		}

		for (int x = 1; x < dx; x += 2) {
			avgRunAccum += averageRunLengthOf(env, x, !evenVal);
			if (x+1 < dx) avgRunAccum += averageRunLengthOf(env, x+1, evenVal);
		}

		double avgRun = avgRunAccum / ((double)dx);
		double score = avgRun / ((double)dy);

		return(score);
	}
	
	private static double averageRunLengthOf(Bitmap env, int x, boolean val) {

		int dy = env.getDy();

		int runs = 0;
		int accum = 0;
		
		int y = 0;
		while (y < dy) {
			// skip any that are wrong
			while (y < dy && env.get(x, y) != val) {
				++y;
			}
			// count as long as they're right
			if (y < dy) {
				++runs;
				while (y < dy && env.get(x, y) == val) {
					++accum; ++y;
				}
			}
		}

		return(runs == 0 ? 0.0 : ((double)accum) / ((double)runs));
	}

	// equally weights VStipes2 and FiftyFifty to suppress the dominance of
	// solids that consistently perform at 0.5
	
	private static double vStripesComboScore(Bitmap env) {
		double vStripes2Score = vStripes2Score(env);
		double fiftyFiftyScore = fiftyFiftyScore(env);
		return((vStripes2Score + fiftyFiftyScore) / 2.0);
	}

	// how close to all columns being the same --- that is, vertical stripes
	// but in any order and sequence of true/false
	
	private static double vBlocksScore(Bitmap env) {

		int dx = env.getDx();
		int dy = env.getDy();

		int countCorrect = 0;

		for (int x = 0; x < dx; x += 1) {
			boolean targetVal = env.get(x, 0);
			for (int y = 1; y < dy; ++y) {
				if (env.get(x,y) == targetVal) ++countCorrect;
			}
		}
		
		double score = ((double)countCorrect) / ((double)(dx * (dy-1)));
		
		return(score);
	}

	// every other bit on/off
	
	private static double checkerboardScore(Bitmap env) {

		int dx = env.getDx();
		int dy = env.getDy();

		int countCorrect = 0;

		boolean startVal = env.get(0,0);
		for (int x = 0; x < dx; ++x) {
			boolean val = startVal;
			for (int y = 0; y < dy; ++y) {
				if (env.get(x,y) == val) ++countCorrect;
				val = !val;
			}
			startVal = !startVal;
		}

		double score = ((double)countCorrect) / ((double)(dx * dy));
		
		return(score);
	}

	// +---------+
	// | Members |
	// +---------+

	private final static Logger log = Logger.getLogger(Fitness.class.getName());
}
