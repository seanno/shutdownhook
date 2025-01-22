//
// GRAPHICS.JAVA
//

package com.shutdownhook.life.lifelib;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.util.Base64;
import java.util.HashMap;
import java.util.logging.Logger;
import javax.imageio.ImageIO;

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

	// +---------------+
	// | renderDataURL |
	// +---------------+

	public String renderDataURL(Bitmap bits) throws Exception {
		return(renderDataURL(bits, "bmp"));
	}

	public String renderDataURL(Bitmap bits, String format) throws Exception {
		return(renderDataURL(bits, format, 1));
	}
	
	public String renderDataURL(Bitmap bits, String format, int dp) throws Exception {

		BufferedImage img = renderBufferedImage(bits, dp);

		// close is a documented nop for BAOS so don't worry about it
		ByteArrayOutputStream stm = new ByteArrayOutputStream();
		boolean ret = ImageIO.write(img, format, stm);
		if (!ret) throw new Exception("ImageIO write failed");

		String b64 = Base64
			.getEncoder()
			.encodeToString(stm.toByteArray());

		return("data:image/" + format + ";base64," + b64);
	}

	// +---------------------+
	// | renderBufferedImage |
	// +---------------------+

	public BufferedImage renderBufferedImage(final Bitmap bits) throws Exception {
		return(renderBufferedImage(bits, 1));
	}
	
	public BufferedImage renderBufferedImage(final Bitmap bits, int dpCell) throws Exception {

		int dx = bits.getDx();
		int dy = bits.getDy();

		// image pixels are initialized to 0 (black) so turn ON the white ones
		
		BufferedImage img = new BufferedImage(dx * dpCell, dy * dpCell, BufferedImage.TYPE_BYTE_BINARY);
		
		for (int x = 0; x < dx; ++x) {
			for (int y = 0; y < dy; ++y) {
				if (!bits.get(x, y)) {
					fillRect(img, x * dpCell, y * dpCell, dpCell, 0xFFFFFF);
				}
			}
		}

		return(img);
	}

	private void fillRect(BufferedImage img, int x, int y, int dp, int rgb) {
		for (int xRect = x; xRect < x + dp; ++xRect) {
			for (int yRect = y; yRect < y + dp; ++yRect) {
				img.setRGB(xRect, yRect, rgb);
			}
		}
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
