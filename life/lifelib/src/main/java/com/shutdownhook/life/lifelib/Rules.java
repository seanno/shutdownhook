//
// RULES.JAVA
//

package com.shutdownhook.life.lifelib;

import java.util.logging.Logger;

public class Rules
{
	// +---------------+
	// | RuleProcessor |
	// +---------------+

	public static enum Outcome
	{
		On,
		Off,
		Flip,
		Inertia
	}
	
	public interface RuleProcessor {
		Outcome apply(Bitmap env, int x, int y);
	}

	// +-------+
	// | apply |
	// +-------+

	public static Bitmap apply(Bitmap env, RuleProcessor proc) {

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

	// +---------+
	// | Members |
	// +---------+

	private final static Logger log = Logger.getLogger(Rules.class.getName());
}
