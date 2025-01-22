//
// MISC.JAVA
//

package com.shutdownhook.life.lifelib;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import com.shutdownhook.toolbox.Template;

import com.shutdownhook.life.lifelib.Neighborhood.NeighborhoodType;

public class Misc
{
	// +--------------------+
	// | visualizeRules     |
	// | visualizeRulesHTML |
	// +--------------------+

	private static int RULE_ITEM_WIDTH = 15;
	private static int RULE_ITEM_MARGIN = 10;

	private static String RULE_TEMPLATE =
		"<svg width='{{DX}}' height='{{DY}}' xmlns='http://www.w3.org/2000/svg' >" +
		"{{:rpt item }}<rect x='{{X}}' y='{{Y}}' width='{{R}}' height='{{R}}' " +
		"                    stroke='black' fill='{{FILL}}' />{{:end}}" +
		"</svg>";

	private static String OUTCOME_FMT = 
		"<svg width='30' height='20' xmlns='http://www.w3.org/2000/svg' >" +
		"<rect x='{{10}}' y='0' width='20' height='20' stroke='black' fill='%s' />" +
		"</svg>";
			
	public static String visualizeRulesHtml(NeighborhoodRulesProcessor rules) throws Exception {

		StringBuilder sbOff = new StringBuilder();
		StringBuilder sbOn = new StringBuilder();
		
		Template ruleTemplate = new Template(RULE_TEMPLATE);
		Map<String,String> ruleTokens = new HashMap<String,String>();
			
		Bitmap vals = rules.getVals();

		for (int x = 0; x < vals.getDx(); ++x) {
			
			int[][] grid = Neighborhood.reverse(x, rules.getNeighborhoodType());
			
			if (x == 0) {
				
				int dx = grid.length;
				int dy = grid[0].length;

				int dxSVG = (dx * RULE_ITEM_WIDTH) + (RULE_ITEM_MARGIN * 2);
				int dySVG = (dy * RULE_ITEM_WIDTH) + (RULE_ITEM_MARGIN * 2);

				ruleTokens.put("DX", Integer.toString(dxSVG));
				ruleTokens.put("DY", Integer.toString(dySVG));
				ruleTokens.put("R", Integer.toString(RULE_ITEM_WIDTH));
			}

			boolean on = vals.get(x,0);
			(on ? sbOn : sbOff).append(gridToSVG(grid, ruleTemplate, ruleTokens));
		}

		String onOutcome = String.format(OUTCOME_FMT, "black");
		String offOutcome = String.format(OUTCOME_FMT, "white");
		
		return("<div class='rules'>" +
			   "<div class='off_outcome'>" + offOutcome + "</div>" +
			   "<div class='off_rules'>" + sbOff.toString() + "</div>" +
			   "<div class='on_outcome'>" + onOutcome + "</div>" +
			   "<div class='on_rules'>" + sbOn.toString() + "</div>" +
			   "</div>");
	}

	private static String gridToSVG(int[][] grid, Template tmpl,
									Map<String,String> tokens) throws Exception {

		int dx = grid.length;
		int dy = grid[0].length;

		String svg = tmpl.render(tokens, new Template.TemplateProcessor() {
				
			public boolean repeat(String[] args, int counter) {

				while (advance()) {
					int cellVal = grid[x][y];
					
					if (cellVal != -1) {

						int xSquare = RULE_ITEM_MARGIN + (RULE_ITEM_WIDTH * x);
						int ySquare = RULE_ITEM_MARGIN + (RULE_ITEM_WIDTH * y);
						
						tokens.put("X", Integer.toString(xSquare));
						tokens.put("Y", Integer.toString(ySquare));
						tokens.put("FILL", (cellVal == 0 ? "white" : "black"));
								   
						return(true);
					}
				}
				return(false);
			}

			private boolean advance() {
				if (++x == dx) { x = 0; ++y; }
				return(y < dy);
			}

			private int x = -1;
			private int y = 0;

		});

		return(svg);
	}
	
	public static void visualizeRules(NeighborhoodRulesProcessor rules) {

		Bitmap vals = rules.getVals();

		for (int x = 0; x < vals.getDx(); ++x) {
			
			int[][] grid = Neighborhood.reverse(x, rules.getNeighborhoodType());
			String gridStr = gridToString(grid);

			System.out.println(gridStr + Boolean.toString(vals.get(x, 0)) + "\n");
		}
	}
	
	private static String gridToString(int[][] grid) {
		
		int dx = grid.length;
		int dy = grid[0].length;
		
		StringBuilder sb = new StringBuilder();
		for (int y = 0; y < dy; ++y) {
			for (int x = 0; x < dx; ++x) {
				int cellVal = grid[x][y];
				sb.append(cellVal == -1 ? " " : (cellVal == 0 ? "." : "X"));
			}
			sb.append("\n");
		}

		return(sb.toString());
	}
	
	// +-----------------------+
	// | convertRules_VN2Moore |
	// +-----------------------+

	public static NeighborhoodRulesProcessor
		convertRules_VN2Moore(NeighborhoodRulesProcessor vnRules) {

		NeighborhoodRulesProcessor mooreRules = new NeighborhoodRulesProcessor(NeighborhoodType.Moore);

		Bitmap vnBits = vnRules.getVals();
		Bitmap mooreBits = mooreRules.getVals();
		
		for (int vn = 0; vn < vnBits.getDx(); ++vn) {

			boolean val = vnBits.get(vn, 0);
				
			int moore = 0;
			if ((vn & 0b1) != 0) moore |= 0b10;
			if ((vn & 0b10) != 0) moore |= 0b1000;
			if ((vn & 0b100) != 0) moore |= 0b10000;
			if ((vn & 0b1000) != 0) moore |= 0b100000;
			if ((vn & 0b10000) != 0) moore |= 0b10000000;

			permuteValuesRecursive(mooreBits, moore, val, 0);
		}

		return(mooreRules);
	}

	private static void permuteValuesRecursive(Bitmap bm, int startingBits, boolean val, int i) {
		
		if (i == PERMUTE_BITS.length) {
			bm.set(startingBits, 0, val);
			return;
		}

		permuteValuesRecursive(bm, startingBits, val, i + 1);
		permuteValuesRecursive(bm, startingBits | PERMUTE_BITS[i], val, i + 1);
	}

	static int[] PERMUTE_BITS = { 0b1, 0b100, 0b1000000, 0b100000000 };
		
	// +---------+
	// | Members |
	// +---------+

	private final static Logger log = Logger.getLogger(Misc.class.getName());
}
