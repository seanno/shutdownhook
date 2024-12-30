//
// GRAPHICS.JAVA
//

package com.shutdownhook.life.lifelib;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.logging.Logger;

import com.shutdownhook.toolbox.Template;

public class Graphics
{
	// +----------------+
	// | Setup & Config |
	// +----------------+

	public static class Config
	{
		// SVG

		public Integer SvgItemRadius = 2;
		
		public String SvgTemplate =
			"<svg width='{{DX}}' height={{DY}}' xmlns='http://www.w3.org/2000/svg' >" +
			"{{:rpt itm}}<circle cx='{{X}}' cy='{{Y}}' r='{{R}}' fill='black' />{{:end}}" +
			"</svg>";
	}

	public Graphics(Config cfg) throws Exception {
		this.cfg = cfg;
		this.svgTemplate = new Template(cfg.SvgTemplate);
	}

	// +-----------+
	// | renderSVG |
	// +-----------+

	public String renderSVG(final Bitmap bits) throws Exception {

		final int radius = cfg.SvgItemRadius;
		final int diameter = radius * 2;
			
		HashMap tokens = new HashMap<String,String>();
		tokens.put("DX", Integer.toString((bits.getDx() * diameter)));
		tokens.put("DY", Integer.toString((bits.getDy() * diameter)));
		tokens.put("R", Integer.toString(radius));
		
		String svg = svgTemplate.render(tokens, new Template.TemplateProcessor() {
				
			public boolean repeat(String[] args, int counter) {

				while (advance()) {

					if (bits.get(x, y)) {
						tokens.put("X", Integer.toString((x * diameter) + radius));
						tokens.put("Y", Integer.toString((y * diameter) + radius));
						return(true);
					}
				}
				return(false);
			}

			private boolean advance() {
				if (++x == bits.getDx()) { x = 0; ++y; }
				return(y < bits.getDy());
			}

			private int x = -1;
			private int y = 0;
		});

		return(svg);
	}
	
	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	private Template svgTemplate;

	private final static Logger log = Logger.getLogger(Graphics.class.getName());
}
