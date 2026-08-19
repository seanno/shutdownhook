/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.toolbox.dep;

import java.util.Base64;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.shutdownhook.toolbox.Global;

public class BarCodesTest
{
	@BeforeClass
	public static void beforeClass() throws Exception {
		Global.init();
	}

	private String decodeDataUrl(String dataUrl) {
		String prefix = "data:image/svg;base64,";
		String encoded = dataUrl.substring(prefix.length());
		return new String(Base64.getUrlDecoder().decode(encoded));
	}

	// +------------+
	// | DataUrl    |
	// +------------+

	@Test
	public void testDataUrlPrefix() {
		String result = BarCodes.qrDataForString("https://example.com");
		Assert.assertTrue(result.startsWith("data:image/svg;base64,"));
	}

	// +--------------+
	// | SVG Structure|
	// +--------------+

	@Test
	public void testSvgStructure() {
		String svg = decodeDataUrl(BarCodes.qrDataForString("https://example.com"));
		Assert.assertTrue(svg.contains("<?xml"));
		Assert.assertTrue(svg.contains("<svg "));
		Assert.assertTrue(svg.contains("</svg>"));
	}

	@Test
	public void testSvgColors() {
		String svg = decodeDataUrl(BarCodes.qrDataForString("https://example.com"));
		Assert.assertTrue(svg.contains("#FFFFFF"));
		Assert.assertTrue(svg.contains("#000000"));
	}

	// +-----+
	// | SVG |
	// +-----+

	@Test
	public void testQrSvgForStringStructure() {
		String svg = BarCodes.qrSvgForString("https://example.com");
		Assert.assertTrue(svg.contains("<?xml"));
		Assert.assertTrue(svg.contains("<svg "));
		Assert.assertTrue(svg.contains("</svg>"));
	}

	@Test
	public void testQrSvgMatchesDecodedDataUrl() {
		String svg = BarCodes.qrSvgForString("https://example.com");
		String svgFromData = decodeDataUrl(BarCodes.qrDataForString("https://example.com"));
		Assert.assertEquals(svg, svgFromData);
	}

	// +--------+
	// | Border |
	// +--------+

	@Test
	public void testBorderAppliedToPath() {
		// QR finder pattern has a dark module at (0,0); with border=4 it maps to M4,4
		String svg = decodeDataUrl(BarCodes.qrDataForString("https://example.com"));
		Assert.assertTrue(svg.contains("M4,4h1v1h-1z"));
	}

	// +--------+
	// | Inputs |
	// +--------+

	@Test
	public void testDifferentInputsDifferentOutput() {
		String r1 = BarCodes.qrDataForString("https://example.com");
		String r2 = BarCodes.qrDataForString("https://other.com");
		Assert.assertFalse(r1.equals(r2));
	}

	@Test
	public void testVariousInputTypes() {
		String prefix = "data:image/svg;base64,";
		Assert.assertTrue(BarCodes.qrDataForString("hello").startsWith(prefix));
		Assert.assertTrue(BarCodes.qrDataForString("12345").startsWith(prefix));
		Assert.assertTrue(BarCodes.qrDataForString("https://example.com/path?q=1&foo=bar").startsWith(prefix));
	}

	// +---------+
	// | Helpers |
	// +---------+

	private int extractViewBoxDim(String svg) {
		// viewBox="0 0 N N" — grab the first N
		int idx = svg.indexOf("viewBox=\"0 0 ");
		String rest = svg.substring(idx + "viewBox=\"0 0 ".length());
		return Integer.parseInt(rest.substring(0, rest.indexOf(' ')));
	}
}
