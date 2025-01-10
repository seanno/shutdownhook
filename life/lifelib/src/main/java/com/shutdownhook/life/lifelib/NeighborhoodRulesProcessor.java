//
// NEIGHBORHOODRULESPROCESSOR.JAVA
//

package com.shutdownhook.life.lifelib;

import java.util.Set;
import java.util.HashSet;
import java.util.logging.Logger;

import com.shutdownhook.life.lifelib.Bitmap.EdgeStrategy;
import com.shutdownhook.life.lifelib.Neighborhood.NeighborhoodType;
import com.shutdownhook.life.lifelib.Rules.Outcome;

public class NeighborhoodRulesProcessor implements Rules.RulesProcessor
{
	// +----------+
	// | Creation |
	// +----------+

	public NeighborhoodRulesProcessor(NeighborhoodType neighborhoodType)
		throws IllegalArgumentException {
		
		this(neighborhoodType, null);
	}

	public NeighborhoodRulesProcessor(NeighborhoodType neighborhoodType,
									  Bitmap vals) throws IllegalArgumentException {
		
		this.neighborhoodType = neighborhoodType;

		int dx = (int) Math.pow(2, Neighborhood.getRelativeCount(neighborhoodType));
		
		if (vals == null) {
			this.vals = new Bitmap(dx, 1);
			this.vals.randomize();
		}
		else {
			if (vals.getDx() != dx || vals.getDy() != 1) {
				throw new IllegalArgumentException("NeighborhoodRules vals must match relative count");
			}
			
			this.vals = vals;
		}
	}

	// +----------------------+
	// | RulesProcessor.apply |
	// +----------------------+

	@Override
	public Outcome apply(Bitmap env, int x, int y) {

		int val = Neighborhood.get(env, x, y, neighborhoodType);
		return(vals.get(val, 0) ? Outcome.On : Outcome.Off);
	}

	// +--------------------------+
	// | RulesProcessor.reproduce |
	// +--------------------------+

	@Override
	public Rules.RulesProcessor reproduce(Rules.RulesProcessor other,
										  Reproduction.Params params)
		throws IllegalArgumentException {

		NeighborhoodRulesProcessor otherNRP = (NeighborhoodRulesProcessor) other;
		Bitmap newVals = Reproduction.reproduce(vals, otherNRP.vals, params);
		return(new NeighborhoodRulesProcessor(neighborhoodType, newVals));
	}

	// +---------+
	// | Members |
	// +---------+

	private NeighborhoodType neighborhoodType;
	protected Bitmap vals;

	private final static Logger log = Logger.getLogger(NeighborhoodRulesProcessor.class.getName());
}
