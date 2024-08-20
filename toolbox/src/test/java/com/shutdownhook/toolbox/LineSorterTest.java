/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.toolbox;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Ignore;

public class LineSorterTest
{
	@BeforeClass
	public static void beforeClass() throws Exception {
		Global.init();
	}

	// +---------------+
	// | XsvLineParser |
	// +---------------+
	
	private static String XSV_TSV_LINE = "a\t\"\"\"\t\"\tbbb\t\t\"ccc\"";
	private static String[] XSV_TSV_QUOTED = { "a", "\"\t", "bbb", "", "ccc" };
	private static String[] XSV_TSV_UNQUOTED = XSV_TSV_LINE.split("\t");
	
	@Test
	public void testXsvLineParser() throws Exception {

		LineSorter.ParsedLine parsed = new LineSorter.ParsedLine();

		// with quoting
		for (int i = 0; i < XSV_TSV_QUOTED.length; ++i) {
			new LineSorter.XsvLineParser(i, i).parse(XSV_TSV_LINE, parsed);
			Assert.assertEquals(XSV_TSV_QUOTED[i], parsed.Key);
			Assert.assertEquals(XSV_TSV_QUOTED[i], parsed.Output);
		}

		// off the end
		new LineSorter.XsvLineParser(XSV_TSV_QUOTED.length + 1, LineSorter.XSV_OUTPUT_ALL).parse(XSV_TSV_LINE, parsed);
		Assert.assertNull(parsed.Key);
		Assert.assertEquals(XSV_TSV_LINE, parsed.Output);
		
		// without quoting
		for (int i = 0; i < XSV_TSV_UNQUOTED.length; ++i) {
			new LineSorter.XsvLineParser(i, i, '\t', null).parse(XSV_TSV_LINE, parsed);
			Assert.assertEquals(XSV_TSV_UNQUOTED[i], parsed.Key);
			Assert.assertEquals(XSV_TSV_UNQUOTED[i], parsed.Output);
		}
	}

	// +---------+
	// | Helpers |
	// +---------+

	
}
