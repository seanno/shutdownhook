//
// REPRODUCTION.JAVA
//

package com.shutdownhook.life.lifelib;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

public class Reproduction
{
	// +--------+
	// | assess |
	// +--------+

	public static enum SurvivalType {
		TwoThirds,
		Half
	}
	
	public static enum PairingType {
		Random,
		Prom
	}

	public static class Triple
	{
		public int Kill;
		public int Partner1;
		public int Partner2;
	}

	public static class FitnessIndex implements Comparable<FitnessIndex>
	{
		public int Index;
		public double Fitness;

		public FitnessIndex(int index, double fitness) {
			this.Index = index;
			this.Fitness = fitness;
		}

		public int compareTo(FitnessIndex other) {
			int cmp = Double.compare(Fitness, other.Fitness);
			if (cmp == 0) cmp = Integer.compare(Index, other.Index);
			return(cmp);
		}
	}

	public static Triple[] assess(FitnessIndex[] fitnesses, PairingType pairingType) {
		return(assess(fitnesses, pairingType, SurvivalType.TwoThirds));
	}

	// Input is an array of FitnessIndex objects; return is an array
	// of "Triples" which indicate reproduction events --- an index
	// to be killed and replaced with the mating of two parter indices.
	// pairingType defines who is paired with who.
	// 
	// In "TwoThirds" survival type, The unfittest 1/3 of the population will die off,
	// replaced by matings of the two two-thirds.
	//
	// In "Half" survival type, The unfittest 1/2 of the population will die off, replaced
	// by 2x matings of the top half (each pair will have two offsprings).
	
	// if the # of organisms is not divisible evenly, the best of the worst will
	// live on but will not reproduce (they will simply not appear in the
	// returned triples).

	public static Triple[] assess(FitnessIndex[] fitnesses,
								  PairingType pairingType,
								  SurvivalType survivalType) {

		Arrays.sort(fitnesses);

		int killDiv = (survivalType.equals(SurvivalType.TwoThirds) ? 3 : 2);

		int fitnessCount = fitnesses.length;
		int killCount = fitnessCount / killDiv;
		int leftovers = fitnessCount % killDiv;

		log.info(String.format("evaluating pairings; %d to kill (%d survive wout reproduction)",
							   killCount, leftovers));

		int iKill = 0;
		int iPartners = iKill + killCount + leftovers; 

		switch (pairingType) {
			case Random: randomizePairings(fitnesses, iPartners); break;
			case Prom: /* already in prom order */ break;
		}

		Triple[] triples = new Triple[killCount];

		while (iKill < killCount) {

			triples[iKill] = new Triple();
			triples[iKill].Kill = fitnesses[iKill].Index;
			triples[iKill].Partner1 = fitnesses[iPartners].Index;
			triples[iKill].Partner2 = fitnesses[iPartners+1].Index;
			++iKill;

			if (survivalType.equals(SurvivalType.Half)) {
				triples[iKill] = new Triple();
				triples[iKill].Kill = fitnesses[iKill].Index;
				triples[iKill].Partner1 = fitnesses[iPartners].Index;
				triples[iKill].Partner2 = fitnesses[iPartners+1].Index;
				++iKill;
			}
			
			iPartners += 2;
		}

		return(triples);
	}

	// this version keeps the top half of performers and mates those
	// twice to replace the bottom half
	
	private static void randomizePairings(FitnessIndex[] fitnesses, int iPartners) {

		int fitnessCount = fitnesses.length;
		int partnerCount = (fitnessCount - iPartners);

		for (int i = iPartners; i < fitnessCount; ++i) {
			
			int j = iPartners + rand.nextInt(partnerCount);
			
			FitnessIndex temp = fitnesses[i];
			fitnesses[i] = fitnesses[j];
			fitnesses[j] = temp;
		}
	}

	// +-----------+
	// | reproduce |
	// +-----------+

	public static class Params
	{
		public int MaxCrossovers = 5;
		public double MinMutationRate = 0.00;
		public double MaxMutationRate = 0.05;
	}

	public static Bitmap reproduce(Bitmap bits1, Bitmap bits2, Params params) {

		Bitmap bitsKid = crossOver(bits1, bits2, params.MaxCrossovers);
		mutate(bitsKid, params.MinMutationRate, params.MaxMutationRate);

		return(bitsKid);
	}
	
	public static Bitmap crossOver(Bitmap bits1, Bitmap bits2, int maxCrossovers)
		throws IllegalArgumentException {

		int dx = bits1.getDx();
		int dy = bits2.getDy();

		if (dx != bits2.getDx() || dy != bits2.getDy()) {
			throw new IllegalArgumentException("incompatible bitmaps");
		}
		
		if (maxCrossovers < 1) {
			throw new IllegalArgumentException("need at least one crossover to getBusy");
		}

		long[][] sourceDNA = new long[2][];
		sourceDNA[0] = bits1.getAsDNA();
		sourceDNA[1] = bits2.getAsDNA();

		int totalLongs = sourceDNA[0].length;
		int totalBits = totalLongs * Long.SIZE;
		int completeBits = 0;

		int crossovers = (maxCrossovers == 1 ? 1 : rand.nextInt(maxCrossovers - 1) + 1);
		
		StringBuilder sb = new StringBuilder();
		sb.append(String.format("Crossovers for %d bits: ", totalBits));
		Set<Integer> crossoverPoints = new HashSet<Integer>();
		
		for (int i = 0; i < crossovers; ++i) {
			int xover = rand.nextInt(totalBits);
			sb.append(Integer.toString(xover)).append(" ");
			crossoverPoints.add(xover);
		}
		log.fine(sb.toString());

		long[] kidDNA = new long[totalLongs];
		int srcIndex = rand.nextInt(1);

		for (int ilong = 0; ilong < totalLongs; ++ilong) {

			long setter = 0x1L;
			
			for (int ibit = 0; ibit < Long.SIZE; ++ibit) {
				
				if ((sourceDNA[srcIndex][ilong] & setter) != 0L) kidDNA[ilong] |= setter;
				setter = setter << 1;

				if (crossoverPoints.contains(completeBits + ibit)) {
					srcIndex = (srcIndex == 1 ? 0 : 1);
				}
			}

			completeBits += Long.SIZE;
		}

		Bitmap kidBits = new Bitmap(dx, dy, bits1.getEdgeStrategy(), kidDNA);
		
		log.fine(String.format("XOVER RESULT:\nA: %s\nB: %s\nK: %s",
							   bits1.getAsDNAText(),
							   bits2.getAsDNAText(),
							   kidBits.getAsDNAText()));
		
		return(kidBits);
	}
	
	// +--------+
	// | mutate |
	// +--------+

	public static void mutate(Bitmap bits, double minRate, double maxRate) {

		int dx = bits.getDx();
		int dy = bits.getDy();
		
		double rate = (rand.nextDouble() * (maxRate - minRate)) + minRate;
		int mutations = (int) (rate * ((double)(dx * dy)));
		log.info(String.format("Mutating %d times over %d bits (effective rate %f)",
							   mutations, dx * dy, rate));
		
		for (int i = 0; i < mutations; ++i) {
			int x = rand.nextInt(dx);
			int y = rand.nextInt(dy);
			bits.set(x, y, !bits.get(x, y));
		}
	}
	
	// +---------+
	// | Members |
	// +---------+

	private final static Random rand = new Random();
	
	private final static Logger log = Logger.getLogger(Reproduction.class.getName());
}
