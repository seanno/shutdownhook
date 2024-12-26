//
// RULES.JAVA
//

package com.shutdownhook.life.lifelib;

import java.util.logging.Logger;

public class Rules
{
	// +------+
	// | Rule |
	// +------+

	public static enum Outcome
	{
		On,
		Off,
		Inertia
	}
	
	public interface Rule {

		Outcome apply(Bitmap3D neighborhood);
		
		default int getRadius() { return(1); }
		default Bitmap3D.EdgeStrategy getEdgeStrategy() { return(Bitmap3D.EdgeStrategy.Wrap); }
	}

	// +-------+
	// | apply |
	// +-------+

	public static Bitmap3D apply(Bitmap3D env, Rule rule) {

		int radius = rule.getRadius();
		Bitmap3D.EdgeStrategy edgeStrategy = rule.getEdgeStrategy();
		
		int dx = env.getDx();
		int dy = env.getDy();
		
		Bitmap3D newEnv = new Bitmap3D(dx, dy);
		
		for (int x = 0; x < dx; ++x) {
			for (int y = 0; y < dy; ++y) {

				Bitmap3D neighborhood = env.getNeighborhood(x, y, radius, edgeStrategy);
				Outcome outcome = rule.apply(neighborhood);

				if (outcome == Outcome.On) { newEnv.set(x, y, true); }
				else if (outcome == Outcome.Off) { newEnv.set(x, y, false); }
				else { newEnv.set(x, y, env.get(x, y)); }
			}
		}

		return(newEnv);
	}

	// +---------+
	// | Members |
	// +---------+

	private final static Logger log = Logger.getLogger(Rules.class.getName());
}
