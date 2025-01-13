//
// FITNESSTEST.JAVA
//

package com.shutdownhook.life.lifelib;

import org.junit.Assert;
import org.junit.Test;

import com.shutdownhook.life.lifelib.Fitness.FitnessType;

public class FitnessTest
{
	@Test
	public void twoByFitness() throws Exception {
		assertFitness(BITS_PERFECT_2BY_1, FitnessType.TwoBySquares, 1.0);
		assertFitness(BITS_PERFECT_2BY_2, FitnessType.TwoBySquares, 1.0);
		assertFitness(BITS_HALF_2BY, FitnessType.TwoBySquares, 0.5);
		assertFitness(BITS_QUARTER_2BY, FitnessType.TwoBySquares, 0.25);
	}

	@Test
	public void edgeFitness() throws Exception {
		assertFitness(BITS_PERFECT_EDGES, FitnessType.Edges, 1.0);
		assertFitness(BITS_ZERO_EDGES, FitnessType.Edges, 0.0);
		assertFitness(BITS_HALF_EDGES, FitnessType.Edges, 0.5);

		Bitmap bits = new Bitmap(10, 10);
		bits.fill(true);
		assertFitness(bits, FitnessType.Edges, 0.5);

		bits.fill(false);
		assertFitness(bits, FitnessType.Edges, 0.5);
	}

	@Test
	public void fiftyFiftyFitness() throws Exception {
		assertFitness(BITS_PERFECT_5050_1, FitnessType.FiftyFifty, 1.0);
		assertFitness(BITS_PERFECT_5050_2, FitnessType.FiftyFifty, 1.0);

		Bitmap bits = new Bitmap(10, 10);
		bits.fill(true);
		assertFitness(bits, FitnessType.FiftyFifty, 0.0);

		bits.fill(false);
		assertFitness(bits, FitnessType.FiftyFifty, 0.0);
	}
	
	@Test
	public void vStripesFitness() throws Exception {
		
		assertFitness(BITS_PERFECT_VSTRIPES_1, FitnessType.VStripes, 1.0);
		assertFitness(BITS_PERFECT_VSTRIPES_2, FitnessType.VStripes, 1.0);
		
		assertFitness(BITS_BAD_VSTRIPES, FitnessType.VStripes, 1.0 / 9.0);

		Bitmap bits = new Bitmap(10, 10);
		bits.fill(true);
		assertFitness(bits, FitnessType.VStripes, 0.5);

		bits.fill(false);
		assertFitness(bits, FitnessType.VStripes, 0.5);
	}
	
	// +---------+
	// | helpers |
	// +---------+

	private void assertFitness(String bitsStr,
							   FitnessType fitnessType,
							   double expectedFitness) throws Exception {

		assertFitness(Serializers.fromString(bitsStr),
					  fitnessType, expectedFitness);
	}
	
	private void assertFitness(Bitmap bits, 
							   FitnessType fitnessType,
							   double expectedFitness) throws Exception {

		double actualFitness = Fitness.compute(bits, fitnessType);
		Assert.assertEquals(expectedFitness, actualFitness, 0.000001);
	}

	// +--------------+
	// | test bitmaps |
	// +--------------+
	
	public final static String BITS_PERFECT_VSTRIPES_1 =
		"7 5 Wrap " + "X.X.X.X" +
		              "X.X.X.X" +
		              "X.X.X.X" +
		              "X.X.X.X" +
		              "X.X.X.X" ;

	public final static String BITS_PERFECT_VSTRIPES_2 =
		"6 5 Wrap " + ".X.X.X" +
		              ".X.X.X" +
		              ".X.X.X" +
		              ".X.X.X" +
		              ".X.X.X" ;

	public final static String BITS_BAD_VSTRIPES =
		"3 3 Wrap " + "XX." +
		              ".X." +
		              ".X." ;

	public final static String BITS_PERFECT_EDGES =
		"5 5 Wrap " + "XXXXX" +
		              "X...X" +
		              "X...X" +
		              "X...X" +
		              "XXXXX" ;
	
	public final static String BITS_ZERO_EDGES =
		"5 5 Wrap " + "....." +
		              ".XXX." +
     		          ".XXX." +
     		          ".XXX." +
     		          "....." ;

	public final static String BITS_HALF_EDGES =
		"5 4 Wrap " + "XXXXX" +
		              "X...." +
		              "XXXX." +
		              "....." ;

	public final static String BITS_PERFECT_5050_1 =
		"4 4 Wrap " + "...." +
		              "XXXX" +
                      "...." +
		              "XXXX" ;

	public final static String BITS_PERFECT_5050_2 =
		"4 4 Wrap " + "X.X." +
		              ".X.X" +
     		          "X.X." +
     		          ".X.X" ;

	public final static String BITS_PERFECT_2BY_1 =
		"4 4 Wrap " + "XX.." +
		              "XX.." +
     		          "..XX" +
     		          "..XX" ;

	public final static String BITS_PERFECT_2BY_2 =
		"7 4 Wrap " + "..XX..X" + 
		              "..XX..X" +
     		          "XX..XX." +
     		          "XX..XX." ;

	public final static String BITS_HALF_2BY =
		"4 4 Wrap " + "...." +
		              "...." +
     		          "...." +
     		          "...." ;
	
	public final static String BITS_QUARTER_2BY =
		"4 4 Wrap " + "XXXX" +
		              "XXXX" +
     		          "XX.." +
     		          "XX.." ;
}
