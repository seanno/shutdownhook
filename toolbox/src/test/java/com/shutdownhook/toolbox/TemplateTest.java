/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.toolbox;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.junit.Ignore;
import org.junit.BeforeClass;
import org.junit.AfterClass;

public class TemplateTest
{
	@Test
	public void testEmptyTemplate() throws Exception {
		testNoTokens("");
		testNoTokens("a");
		testNoTokens("my bonnie {notatoken} over the ocean"); 
	}

	@Test
	public void testSimpleTokens() throws Exception {
		HashMap<String,String> tokens = new HashMap<String,String>();
		tokens.put("abc", "def");
		tokens.put("def", "bananafish");
		
		testTokens("", tokens);
		testTokens("{{abc}}", tokens);
		testTokens("{{   abc}}", tokens);
		testTokens("{{abc\t}}", tokens);
		testTokens("0{{abc}}", tokens);
		testTokens("{{abc}}1", tokens);
		testTokens("0{{abc}}1", tokens);
		testTokens("{{abc}}{{abc}}", tokens);
		testTokens("{{def}}{{abc}}", tokens);
		testTokens("whoo{{def}}{{abc}}hoo", tokens);
	}

	@Test
	public void testSimpleProcessor() throws Exception {
		testReverseProcessor("abc");
		testReverseProcessor("abc{{REV fed}}ghi");
		testReverseProcessor("abc{{REV 1}}");
		testReverseProcessor("abc{{REV }}");
		testReverseProcessor("abc{{REV bananafish}}abc");
	}

	@Test
	public void testBoth() throws Exception {

		HashMap<String,String> tokens = new HashMap<String,String>();
		tokens.put("abc", "def");
		tokens.put("def", "bananafish");

		String input = "{{abc}} then {{reverse iguana }} and finally {{def}}";
		String expected = "def then anaugi and finally bananafish";

		testOne(input, expected, tokens, new ReverseProcessor());
	}

	// +---------+
	// | Helpers |
	// +---------+

	private void testNoTokens(String text) throws Exception {
		testOne(text, text, null, null);
	}

	private void testTokens(String text, Map<String,String> tokens) throws Exception {

		String expected = text;
		for (String name : tokens.keySet()) {
			expected = expected.replaceAll("\\{\\{\\s*" + name + "\\s*\\}\\}",
										   tokens.get(name));
		}

		testOne(text, expected, tokens, null);
	}

	private final static String REV_TOKEN = "REV ";
		
	private void testReverseProcessor(String text) throws Exception {

		StringBuilder sb = new StringBuilder();
		for (String firstSplit : text.split("\\{\\{")) {
			for (String secondSplit : firstSplit.split("\\}\\}")) {
				if (secondSplit.startsWith(REV_TOKEN)) {
					sb.append(reverse(secondSplit.substring(REV_TOKEN.length()).trim()));
				}
				else {
					sb.append(secondSplit);
				}
			}
		}

		testOne(text, sb.toString(), null, new ReverseProcessor());
	}

	public static class ReverseProcessor implements Template.TokenProcessor {
		public String process(String token, String args) throws Exception {
			return(args == null ? "-" : reverse(args));
		}
	}

	private static String reverse(String input) {
		char[] rgch = input.toCharArray();
		int cch = rgch.length;
		
		for (int i = 0; i < cch / 2; ++i) {
			char ch = rgch[i];
			rgch[i] = rgch[cch - i - 1];
			rgch[cch - i - 1] = ch;
		}

		return(new String(rgch));
	}

	private void testOne(String text, String expected,
						 Map<String,String> tokens,
						 Template.TokenProcessor processor) throws Exception {
		
		Template t = new Template(text);
		String result = t.render(tokens, processor);
		Assert.assertEquals(expected, result);
	}
}
