//
// GRAPHICSTEST.JAVA
//

package com.shutdownhook.life.lifelib;

import org.junit.Assert;
import org.junit.Test;

public class GraphicsTest
{
	@Test
	public void svg() throws Exception {
		
		Bitmap bits = new Bitmap(10, 10);
		bits.randomize();

		Graphics g = new Graphics(new Graphics.Config());
		String svg = g.renderSVG(bits);

		System.out.println(svg);
	}

}
