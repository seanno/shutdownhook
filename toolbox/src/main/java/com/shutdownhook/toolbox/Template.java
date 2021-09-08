/*
** Read about this code at http://shutdownhook.com
n** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.toolbox;

import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Template
{
	private final static String DIRECTIVE_START = "{{";
	private final static String DIRECTIVE_END = "}}";
	
	public Template(String templateText) throws Exception {
		this.templateText = templateText;
		setupBlocks();
	}

	public String render(Map<String,String> tokens,
						 TokenProcessor processor) throws Exception {

		StringBuilder sb = new StringBuilder();
		
		for (Block block : blocks) {
			block.append(sb, tokens, processor);
		}

		return(sb.toString());
	}
	
	// +----------------+
	// | TokenProcessor |
	// +----------------+

	public interface TokenProcessor {
		public String process(String token, String args) throws Exception;
	}

	// +-------------+
	// | Block Types |
	// +-------------+

	public abstract static class Block
	{
		public abstract void append(StringBuilder sb,
									Map<String,String> tokens,
									TokenProcessor processor) throws Exception;
	}

	public static class StaticBlock extends Block
	{
		public StaticBlock(String text) {
			this.text = text;
		}

		public void append(StringBuilder sb,
						   Map<String,String> tokens,
						   TokenProcessor processor) throws Exception {
			
			sb.append(text);
		}

		private String text;
	}

	public static class TokenBlock extends Block
	{
		public TokenBlock(String token, String args) {

			this.token = token;
			this.args = args;
		}

		public void append(StringBuilder sb,
						   Map<String,String> params,
						   TokenProcessor processor) throws Exception {
			
			if (token != null && params != null && params.containsKey(token)) {
				sb.append(params.get(token));
				return;
			}

			sb.append(processor.process(token, args));
		}

		private String token;
		private String args;
	}

	// +-------------+
	// | setupBlocks |
	// +-------------+

	private void setupBlocks() throws Exception {

		blocks = new ArrayList<Block>();

		String token;
		String args;
		
		int ichWalk;
		int ichDirectiveStart;
		int cch = templateText.length();

		ichWalk = 0;
		while (ichWalk < cch) {
			
			ichDirectiveStart = templateText.indexOf(DIRECTIVE_START, ichWalk);
			
			if (ichDirectiveStart != ichWalk) {
				// there is stuff before the next directive (or before end of
				// the template). Add it as a static block.
				int ichMac = (ichDirectiveStart == -1 ? cch : ichDirectiveStart);
				blocks.add(new StaticBlock(templateText.substring(ichWalk, ichMac)));
				ichWalk = ichMac;
			}

			if (ichWalk < cch) {
				// we must now be pointed at a directive, so handle it.
				ichWalk += DIRECTIVE_START.length();
				ichWalk = skipOverWhitespace(templateText, ichWalk, cch);
				
				int ichDirectiveEnd = templateText.indexOf(DIRECTIVE_END, ichWalk);
				if (ichDirectiveEnd == -1) throw new Exception("Mismatched directive at " +
															   Integer.toString(ichWalk));

				int ichSpace = skipToWhitespace(templateText, ichWalk, ichDirectiveEnd);
				token = templateText.substring(ichWalk, ichSpace);
				
				if (ichSpace == ichDirectiveEnd) {
					args = null;
				}
				else {
					ichWalk = skipOverWhitespace(templateText, ichSpace, ichDirectiveEnd);
					args = templateText.substring(ichWalk, ichDirectiveEnd).trim();
				}

				blocks.add(new TokenBlock(token, args));
				ichWalk = ichDirectiveEnd + DIRECTIVE_END.length();
			}
		}
	}

	// +-------------------+
	// | Helpers & Members |
	// +-------------------+

	private int skipToWhitespace(String text, int ichStart, int ichMac) {

		int ichWalk = ichStart;
		while (ichWalk < ichMac && !Character.isWhitespace(text.charAt(ichWalk))) {
			++ichWalk;
		}

		return(ichWalk);
	}
	
	private int skipOverWhitespace(String text, int ichStart, int ichMac) {

		int ichWalk = ichStart;
		while (ichWalk < ichMac && Character.isWhitespace(text.charAt(ichWalk))) {
			++ichWalk;
		}

		return(ichWalk);
	}

	private String templateText;
	private List<Block> blocks;
	
	private final static Logger log = Logger.getLogger(Template.class.getName());
}
