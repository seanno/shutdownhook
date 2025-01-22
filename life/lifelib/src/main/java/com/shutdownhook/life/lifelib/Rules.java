//
// RULES.JAVA
//

package com.shutdownhook.life.lifelib;

import java.util.logging.Logger;

import com.shutdownhook.life.lifelib.Neighborhood.NeighborhoodType;

public class Rules
{
	// +----------------+
	// | RulesProcessor |
	// +----------------+

	public static enum Outcome
	{
		On,
		Off,
		Flip,
		Inertia
	}
	
	public interface RulesProcessor {
		Outcome apply(Bitmap env, int x, int y);
		RulesProcessor reproduce(RulesProcessor other, Reproduction.Params params);
	}

	// +-------+
	// | apply |
	// +-------+

	public static Bitmap apply(Bitmap env, RulesProcessor proc) {

		int dx = env.getDx();
		int dy = env.getDy();
		
		Bitmap newEnv = new Bitmap(dx, dy);
		
		for (int x = 0; x < dx; ++x) {
			for (int y = 0; y < dy; ++y) {

				Outcome outcome = proc.apply(env, x, y);

				switch (outcome) {
					case On: newEnv.set(x, y, true); break;
					case Off: newEnv.set(x, y, false); break;
					case Flip: newEnv.set(x, y, !env.get(x, y)); break;
					case Inertia: newEnv.set(x, y, env.get(x, y)); break;
				}
			}
		}

		return(newEnv);
	}

	// +-----+
	// | get |
	// +-----+

	public static enum RulesType
	{
		Life,
		Neighborhood_Moore,
		Neighborhood_VonNeumann,
		Neighborhood_VonNeumannR2,
		Neighborhood_Cross2,
		Neighborhood_VonNeumann_EdgeDetect,
		Neighborhood_VonNeumann_Quadrants,
		Neighborhood_VonNeumann_Halves,
		Neighborhood_Moore_Halves
	}

	public static RulesProcessor get(RulesType rulesType) {
		
		switch (rulesType) {
			
			case Life:
				return(new LifeRulesProcessor());
				
			default: case Neighborhood_Moore:
				return(new NeighborhoodRulesProcessor(NeighborhoodType.Moore));
				
			case Neighborhood_VonNeumann:
				return(new NeighborhoodRulesProcessor(NeighborhoodType.VonNeumann));
				
			case Neighborhood_VonNeumannR2:
				return(new NeighborhoodRulesProcessor(NeighborhoodType.VonNeumannR2));
				
			case Neighborhood_Cross2:
				return(new NeighborhoodRulesProcessor(NeighborhoodType.Cross2));

			case Neighborhood_VonNeumann_EdgeDetect:
				return(new NeighborhoodRulesProcessor(NeighborhoodType.VonNeumannEdgeDetect));

			case Neighborhood_VonNeumann_Quadrants:
				return(new NeighborhoodRulesProcessor(NeighborhoodType.VonNeumannQuadrants));

			case Neighborhood_VonNeumann_Halves:
				return(new NeighborhoodRulesProcessor(NeighborhoodType.VonNeumannHalves));
				
			case Neighborhood_Moore_Halves:
				return(new NeighborhoodRulesProcessor(NeighborhoodType.MooreHalves));
		}
		
	}
	
	// +---------+
	// | Members |
	// +---------+

	private final static Logger log = Logger.getLogger(Rules.class.getName());
}
