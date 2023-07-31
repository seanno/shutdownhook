/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.toolbox;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Ignore;

public class EasyTest
{
	@BeforeClass
	public static void beforeClass() throws Exception {
		Global.init();
	}

	// +---------+
	// | Strings |
	// +---------+

	@Test
	public void testJoin() throws Exception {

		List<String> l = new ArrayList<String>();
		String j = Easy.join(l, ",");
		Assert.assertEquals("", j);
		
		l.add("foo");
		j = Easy.join(l, ",");
		Assert.assertEquals("foo", j);

		l.add("bar");
		j = Easy.join(l, "COWFISH");
		Assert.assertEquals("fooCOWFISHbar", j);
	}

	// +----+
	// | IO |
	// +----+

	@Test
	public void testStringToProcess() throws Exception {
		Assert.assertEquals("hello\n", Easy.stringFromProcess("echo \"hello\""));
		Assert.assertEquals("0\n", Easy.stringFromProcess("expr 5 = 6"));

		String piped = Easy.stringFromProcess("ping -c 1 127.0.0.1 " +
											  "| grep received " +
											  "| wc -l");

		Assert.assertEquals("1\n", piped);
	}

	// +-------------+
	// | Conversions |
	// +-------------+
	
	@Test
	public void testParseDT_UtcSpecified() {
		ZonedDateTime zdtExpected = ZonedDateTime.of(2017, 5, 10, 1, 1, 1, 0, ZoneOffset.UTC);
		ZonedDateTime zdtParsed = Easy.parseVariablePrecisionDateTime("2017-05-10T01:01:01Z", true);
		Assert.assertEquals(zdtExpected, zdtParsed);
	}

	@Test
	public void testParseDT_OffsetSpecified() {
		ZonedDateTime zdtExpected = ZonedDateTime.of(2015, 2, 7, 13, 28, 17, 239000000, ZoneOffset.ofHours(2));
		ZonedDateTime zdtParsed = Easy.parseVariablePrecisionDateTime("2015-02-07T13:28:17.239+02:00", false);
		Assert.assertEquals(zdtExpected, zdtParsed);
	}
	
	@Test
	public void testParseDT_YMD() {
		ZonedDateTime zdtExpected = ZonedDateTime.of(2015, 2, 7, 0, 0, 0, 0, ZoneId.systemDefault());
		ZonedDateTime zdtParsed = Easy.parseVariablePrecisionDateTime("2015-02-07", true);
		Assert.assertEquals(zdtExpected, zdtParsed);
	}
	
	@Test
	public void testParseDT_YM() {
		ZonedDateTime zdtExpected = ZonedDateTime.of(2015, 2, 1, 0, 0, 0, 0, ZoneId.systemDefault());
		ZonedDateTime zdtParsed = Easy.parseVariablePrecisionDateTime("2015-02", true);
		Assert.assertEquals(zdtExpected, zdtParsed);
	}

	@Test
	public void testParseDT_Y() {
		ZonedDateTime zdtExpected = ZonedDateTime.of(2015, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault());
		ZonedDateTime zdtParsed = Easy.parseVariablePrecisionDateTime("2015", true);
		Assert.assertEquals(zdtExpected, zdtParsed);
	}

	@Test
	public void testParseD_YMD() {
		LocalDate ldExpected = LocalDate.of(2015, 2, 7);
		LocalDate ldParsed = Easy.parseVariablePrecisionDate("2015-02-07");
		Assert.assertEquals(ldExpected, ldParsed);
	}

	@Test
	public void testParseD_YM() {
		LocalDate ldExpected = LocalDate.of(2015, 2, 1);
		LocalDate ldParsed = Easy.parseVariablePrecisionDate("2015-02");
		Assert.assertEquals(ldExpected, ldParsed);
	}

	@Test
	public void testParseD_Y() {
		LocalDate ldExpected = LocalDate.of(2015, 1, 1);
		LocalDate ldParsed = Easy.parseVariablePrecisionDate("2015");
		Assert.assertEquals(ldExpected, ldParsed);
	}

	@Test
	public void testSha256() {
		String sha = Easy.sha256("abc");
		Assert.assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha);
	}

	@Test
	public void testHmac256() throws Exception {
		String sig = Easy.hmac256("abc", Easy.base64Encode("def"));
		Assert.assertEquals("OX9Gc0Hk14xHSGfvMmHNtGwOEDUempiZY+bLLc5A7l0=", sig);
	}

	@Test
	public void testHtmlDecode() throws Exception {
		testOneHtmlDecode("yo", "yo");
		testOneHtmlDecode("yo&", "yo&");
		testOneHtmlDecode("&gt;", ">");
		testOneHtmlDecode("&gt;&lt;&#36;&#x24;&#X24;", "><$$$");
		testOneHtmlDecode("", "");
		testOneHtmlDecode("&", "&");
	}

	private void testOneHtmlDecode(String input, String expected) throws Exception {
		Assert.assertEquals(expected, Easy.htmlDecode(input));
	}
}
