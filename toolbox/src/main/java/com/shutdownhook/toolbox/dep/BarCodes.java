/*
** BarCodes.java
** Generate barcode images
**
   <dependency>
     <groupId>io.nayuki</groupId>
     <artifactId>qrcodegen</artifactId>
     <version>1.8.0</version>
   </dependency>
*/

package com.shutdownhook.toolbox.dep;

import java.util.logging.Logger;

import io.nayuki.qrcodegen.QrCode;

import com.shutdownhook.toolbox.Easy;

public class BarCodes
{

	// +----------------+
	// | qrSvgForString |
	// +----------------+

	// returns svg text for a qr code representing the input text or url
	// picks reasonable defaults for most settings because who has time for this.

	// svg conversion cribbed from (MIT license):
	// https://github.com/nayuki/QR-Code-generator/blob/master/java/QrCodeGeneratorDemo.java

	public static String qrSvgForString(String input, long dp) {

		// encode the qr
		QrCode qr = QrCode.encodeText(input, QrCode.Ecc.MEDIUM);

		StringBuilder sb = new StringBuilder()
			.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
			.append("<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" ")
			.append("\"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">\n")
			.append("<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\" ")
			.append(String.format("viewBox=\"0 0 %1$d %1$d\" stroke=\"none\">\n", qr.size + dp * 2))
			.append("\t<rect width=\"100%\" height=\"100%\" fill=\"" + LIGHT_COLOR + "\"/>\n")
			.append("\t<path d=\"");

		// make the svg
		StringBuilder sbRects = new StringBuilder();
		
		for (int y = 0; y < qr.size; ++y) {
			for (int x = 0; x < qr.size; ++x) {
				if (qr.getModule(x, y)) {
					if (sbRects.length() > 0) sbRects.append(" ");
					sbRects.append(String.format("M%d,%dh1v1h-1z", x + dp, y + dp));
				}
			}
		}
		
		sb.append(sbRects.toString());
		sb.append("\" fill=\"" + DARK_COLOR + "\"/>\n</svg>\n");

		// and return it
		return(sb.toString());
	}

	// +-----------------+
	// | qrDataForString |
	// +-----------------+

	// returns a data: string that can be used as src for an html image tag (dp x dp)
	// uses qrSvgForString to generate the SVG

	public static String qrDataForString(String input, long dp) {
		return("data:image/svg;base64," +
			   Easy.base64urlEncode(qrSvgForString(input, dp)));
	}
	
	// +---------+
	// | Members |
	// +---------+

	// empty blocks around the qr itself; 4 is recommended
	private final static int BORDER_MODULES = 4;

	// colors for the QR 
	private final static String LIGHT_COLOR = "#FFFFFF";
	private final static String DARK_COLOR = "#000000";
	
	
	private final static Logger log = Logger.getLogger(BarCodes.class.getName());
}

