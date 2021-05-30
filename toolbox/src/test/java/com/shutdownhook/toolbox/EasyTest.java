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
import java.util.Random;

import org.junit.Assert;
import org.junit.Test;
import org.junit.Ignore;

public class EasyTest
{
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
}
