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
		String result = BarCodes.qrDataForString("https://example.com", 4);
		Assert.assertTrue(result.startsWith("data:image/svg;base64,"));
	}

	// +--------------+
	// | SVG Structure|
	// +--------------+

	@Test
	public void testSvgStructure() {
		String svg = decodeDataUrl(BarCodes.qrDataForString("https://example.com", 4));
		Assert.assertTrue(svg.contains("<?xml"));
		Assert.assertTrue(svg.contains("<svg "));
		Assert.assertTrue(svg.contains("</svg>"));
	}

	@Test
	public void testSvgColors() {
		String svg = decodeDataUrl(BarCodes.qrDataForString("https://example.com", 4));
		Assert.assertTrue(svg.contains("#FFFFFF"));
		Assert.assertTrue(svg.contains("#000000"));
	}

	// +-----+
	// | SVG |
	// +-----+

	@Test
	public void testQrSvgForStringStructure() {
		String svg = BarCodes.qrSvgForString("https://example.com", 4);
		Assert.assertTrue(svg.contains("<?xml"));
		Assert.assertTrue(svg.contains("<svg "));
		Assert.assertTrue(svg.contains("</svg>"));
	}

	@Test
	public void testQrSvgMatchesDecodedDataUrl() {
		String svg = BarCodes.qrSvgForString("https://example.com", 4);
		String svgFromData = decodeDataUrl(BarCodes.qrDataForString("https://example.com", 4));
		Assert.assertEquals(svg, svgFromData);
	}

	// +--------+
	// | Border |
	// +--------+

	@Test
	public void testBorderAppliedToPath() {
		// QR finder pattern has a dark module at (0,0); with dp=4 it maps to M4,4
		String svg = decodeDataUrl(BarCodes.qrDataForString("https://example.com", 4));
		Assert.assertTrue(svg.contains("M4,4h1v1h-1z"));
	}

	@Test
	public void testZeroBorderPath() {
		// With dp=0, the dark module at (0,0) maps to M0,0
		String svg = decodeDataUrl(BarCodes.qrDataForString("https://example.com", 0));
		Assert.assertTrue(svg.contains("M0,0h1v1h-1z"));
	}

	@Test
	public void testViewBoxSizeIncludesBorder() {
		// viewBox format: "0 0 N N" where N = qr.size + dp*2
		// same input, dp=4 vs dp=0 should differ by exactly 8
		String svg4 = decodeDataUrl(BarCodes.qrDataForString("test", 4));
		String svg0 = decodeDataUrl(BarCodes.qrDataForString("test", 0));
		Assert.assertEquals(8, extractViewBoxDim(svg4) - extractViewBoxDim(svg0));
	}

	// +--------+
	// | Inputs |
	// +--------+

	@Test
	public void testDifferentInputsDifferentOutput() {
		String r1 = BarCodes.qrDataForString("https://example.com", 4);
		String r2 = BarCodes.qrDataForString("https://other.com", 4);
		Assert.assertFalse(r1.equals(r2));
	}

	@Test
	public void testVariousInputTypes() {
		String prefix = "data:image/svg;base64,";
		Assert.assertTrue(BarCodes.qrDataForString("hello", 4).startsWith(prefix));
		Assert.assertTrue(BarCodes.qrDataForString("12345", 4).startsWith(prefix));
		Assert.assertTrue(BarCodes.qrDataForString("https://example.com/path?q=1&foo=bar", 4).startsWith(prefix));
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
