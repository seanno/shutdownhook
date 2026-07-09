//
// UTILITYTEST.JAVA
//

package com.shutdownhook.colossus;

import org.junit.Test;
import static org.junit.Assert.*;

public class UtilityTest
{
	// +------------------+
	// | escapeJsonString |
	// +------------------+

	@Test
	public void testEscapeJsonStringPlainText() {
		assertEquals("Hello, World!", Utility.escapeJsonString("Hello, World!"));
	}

	@Test
	public void testEscapeJsonStringWithDoubleQuotes() {
		assertEquals("He said \\\"hello\\\"", Utility.escapeJsonString("He said \"hello\""));
	}

	@Test
	public void testEscapeJsonStringWithBackslash() {
		assertEquals("path\\\\to\\\\file", Utility.escapeJsonString("path\\to\\file"));
	}

	@Test
	public void testEscapeJsonStringWithNewline() {
		assertEquals("line1\\nline2", Utility.escapeJsonString("line1\nline2"));
	}

	@Test
	public void testEscapeJsonStringWithTab() {
		assertEquals("col1\\tcol2", Utility.escapeJsonString("col1\tcol2"));
	}

	@Test
	public void testEscapeJsonStringEmpty() {
		assertEquals("", Utility.escapeJsonString(""));
	}

	@Test
	public void testEscapeJsonStringResultIsValidJsonInnerValue() {
		// result should be embeddable in {"key":"<result>"} without breaking JSON
		String input = "has \"quotes\" and \\backslashes\\ and\nnewlines";
		String escaped = Utility.escapeJsonString(input);
		String json = "{\"key\":\"" + escaped + "\"}";

		com.google.gson.JsonObject obj =
			com.google.gson.JsonParser.parseString(json).getAsJsonObject();
		assertEquals(input, obj.get("key").getAsString());
	}
}
