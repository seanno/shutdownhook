//
// MASKRULES.JAVA
//

package com.shutdownhook.life.lifelib;

import java.util.Set;
import java.util.HashSet;
import java.util.logging.Logger;

public class MaskRules implements Rules.Rule
{
	// 3+3+1 = max neighborhood that fits in a single long
	public static final int MAX_RADIUS = 3;
	
	// +-------+
	// | Setup |
	// +-------+

	public MaskRules(Bitmap3D bits) throws IllegalArgumentException {
		this.bits = bits;
		parseRules();
	}
	
	// +------------+
	// | Rules.Rule |
	// +------------+

	public Rules.Outcome apply(Bitmap3D neighborhood) {

		Long match = neighborhood.getAsDNA()[0] & mask;
		
		if (rulesOn.contains(match)) return(Rules.Outcome.On);
		if (rulesOff.contains(match)) return(Rules.Outcome.Off);
		return(defaultOutcome);
	}
	
	public int getRadius() { return(radius); }
	public Bitmap3D.EdgeStrategy getEdgeStrategy() { return(edgeStrategy); }

	// +-------------+
	// | RuntimeRule |
	// +-------------+

	// "bits" format is set of square bitmaps; each bitmap is one column and row larger than
	// the actual bits that make up the rule mask; the bit at x=0,y=dy-1 is the outcome bit.
	// The rest of the bottom row and rightmost column are ignored. The first bitmap in z-order
	// is a mask applied to the rules and environment before checking for match.
	//
	// The "outcome" bit for the mask is overly-complex. If the bit is on, default outcome
	// will be "on". If that bit is off, we check the bit at x+1, if it is on the default
	// outcome is "off", else it's inertia (no change).
	//
	// The "edge strategy" bit is similary complex, using the top-right bit and the one below it.
	// (1 = on, 10 = off, 00 = wrap)
	//
	// We do not enforce rule "uniqueness" --- if the same masked bitmap appears multiple times,
	// the last one we see will be the active one. You have been warned.

	private void parseRules() throws IllegalArgumentException {

		int count = bits.getDz() - 1;
		int width = bits.getDx();
		
		this.radius = (width / 2);
		
		if (this.radius != bits.getDy()) throw new IllegalArgumentException("rules must be square");
		if ((width & 1) == 0) throw new IllegalArgumentException("width must be odd");
		if (this.radius > MAX_RADIUS) throw new IllegalArgumentException("radius too large");

		Bitmap3D maskBits = bits.getNeighborhood(radius, radius, 0, radius,
												 Bitmap3D.EdgeStrategy.Off);
		this.mask = maskBits.getAsDNA()[0];

		if (bits.get(0, width - 1, 0)) { this.defaultOutcome = Rules.Outcome.On; }
		else if (bits.get(1, width - 1, 0)) { this.defaultOutcome = Rules.Outcome.Off; }
		else { this.defaultOutcome = Rules.Outcome.Inertia; }
		
		if (bits.get(width - 1, 0, 0)) { this.edgeStrategy = Bitmap3D.EdgeStrategy.On; }
		else if (bits.get(width - 1, 1, 0)) { this.edgeStrategy = Bitmap3D.EdgeStrategy.Off; }
		else { this.edgeStrategy = Bitmap3D.EdgeStrategy.Wrap; }

		this.rulesOn = new HashSet<Long>();
		this.rulesOff = new HashSet<Long>();
		
		for (int z = 1; z <= count; ++z) {
			Bitmap3D matchBits = bits.getNeighborhood(radius, radius, z, radius,
													  Bitmap3D.EdgeStrategy.Off);

			Long match = matchBits.getAsDNA()[0] & this.mask;
			
			if (bits.get(0, width - 1, z)) { rulesOn.add(match); }
			else { rulesOff.add(match); }
		}
	}
	
	// +---------+
	// | Members |
	// +---------+

	private Bitmap3D bits;

    private int radius;
	private long mask;
	private Rules.Outcome defaultOutcome;
    private Bitmap3D.EdgeStrategy edgeStrategy;
	private Set<Long> rulesOn;
	private Set<Long> rulesOff;

	private final static Logger log = Logger.getLogger(MaskRules.class.getName());
}
