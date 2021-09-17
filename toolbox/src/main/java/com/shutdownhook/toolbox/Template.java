/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.toolbox;

import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Template
{
	private final static String DIRECTIVE_START = "{{";
	private final static String DIRECTIVE_END = "}}";

	private final static String RAW_DIRECTIVE = ":raw";
	private final static String CMD_DIRECTIVE = ":cmd";
	private final static String REPEAT_DIRECTIVE = ":rpt";
	private final static String EACH_DIRECTIVE = ":each";
	private final static String END_DIRECTIVE = ":end";
	
	public Template(String templateText) throws Exception {
		this.templateText = templateText;
		setupBlocks();
	}

	public String render() throws Exception {
		return(render(null, null));
	}

	public String render(Map<String,String> tokens) throws Exception {
		return(render(tokens, null));
	}

	public String render(Map<String,String> tokens,
						 TemplateProcessor processor) throws Exception {

		Map<String,String> realTokens =
			(tokens == null ? new HashMap<String,String>() : tokens);
		
		TemplateProcessor realProcessor =
			(processor == null ? new TemplateProcessor() : processor);
		
		StringBuilder sb = new StringBuilder();
		renderRecursive(sb, realTokens, realProcessor, 0);
		return(sb.toString());
	}

	private int renderRecursive(StringBuilder sb, Map<String,String> tokens,
								TemplateProcessor processor, int iblockStart) throws Exception {
								
		int iblock = iblockStart;

		while (iblock < blocks.size()) {

			log.fine(String.format("renderRecursive; iblockStart = %d, iblock = %d",
								   iblockStart, iblock));
			
			// we'll eat this block regardless of what happens
			Block thisBlock = blocks.get(iblock++);

			if (thisBlock instanceof EndBlock) {
				// fall out
				break;
			}
			else if (thisBlock instanceof EachBlock) {
				// start an each cycle
				int cblocksRecursed = 0;
				EachBlock eb = (EachBlock) thisBlock;
				String originalTokenValue = mapOrEnv(tokens, eb.getToken());
				for (String elt : originalTokenValue.split(eb.getSplit(), -1)) {
					tokens.put(eb.getToken(), elt);
					// ditto comment below re: cblocksRecursed
					cblocksRecursed = renderRecursive(sb, tokens, processor, iblock);
				}

				tokens.put(eb.getToken(), originalTokenValue);
				iblock += cblocksRecursed;
			}
			else if (thisBlock instanceof RepeatBlock) {
				// start a repeat cycle
				int cblocksRecursed = 0;
				RepeatBlock rb = (RepeatBlock) thisBlock;
				int counter = 0;
				while (processor.repeat(rb.getArgs(), counter++)) {
					// weird that we keep resetting cblocksRecursed ... it will be the
					// same every time, but counting the blocks consumed is a side
					// effect of running this so life is just simpler this way
					cblocksRecursed = renderRecursive(sb, tokens, processor, iblock);
				}

				iblock += cblocksRecursed;
			}
			else {
				// normal rendering block
				thisBlock.append(sb, tokens, processor);
			}
		}

		return(iblock - iblockStart);
	}

	// +-------------------+
	// | TemplateProcessor |
	// +-------------------+

	public static class TemplateProcessor {

		public String token(String token, String args) throws Exception {
			throw new Exception("TemplateProcessor.token used without implementation");
		}

		public boolean repeat(String args, int counter) {
			int count = Integer.parseInt(args);
			return(counter < count);
		}
	}

	// +-------------+
	// | Block Types |
	// +-------------+

	public static class Block
	{
		public void append(StringBuilder sb,
						   Map<String,String> tokens,
						   TemplateProcessor processor) throws Exception {
			
			// default do nothing, e.g. for repeat/end directives
		}
	}

	public static class StaticBlock extends Block
	{
		public StaticBlock(String text) {
			this.text = text;
		}

		public void append(StringBuilder sb,
						   Map<String,String> tokens,
						   TemplateProcessor processor) throws Exception {
			
			sb.append(text);
		}

		private String text;
	}

	public static class TokenBlock extends Block
	{
		public TokenBlock(String token, String args, boolean outputRaw) {

			this.token = token;
			this.args = args;
			this.outputRaw = outputRaw;
		}

		public void append(StringBuilder sb,
						   Map<String,String> params,
						   TemplateProcessor processor) throws Exception {
			
			String s = mapOrEnv(params, token);
			if (s == null) s = processor.token(token, args);

			sb.append(outputRaw ? s : Easy.htmlEncode(s));
		}

		private String token;
		private String args;
		private boolean outputRaw;
	}

	public static class CommandBlock extends Block
	{
		public CommandBlock(String cmdLine) {
			this.cmdLine = cmdLine;
		}

		public void append(StringBuilder sb,
						   Map<String,String> params,
						   TemplateProcessor processor) throws Exception {
			
			sb.append(Easy.stringFromProcess(cmdLine));
		}

		private String cmdLine;
	}

	public static class RepeatBlock extends Block
	{
		public RepeatBlock(String args) { this.args = args;	}
		public String getArgs() { return(args); }
		private String args;
	}

	public static class EachBlock extends Block
	{
		public EachBlock(String args) {
			int cch = args.length();

			int ichStart = skipOverWhitespace(args, 0, cch);
			int ichSplit = skipToWhitespace(args, ichStart, cch);
			this.token = args.substring(ichStart, ichSplit);

			ichStart = skipOverWhitespace(args, ichSplit, cch);
			ichSplit = skipToWhitespace(args, ichStart, cch);
			
			this.split =
				args.substring(ichStart, ichSplit)
				.replaceAll("\\n", "\n")
				.replaceAll("\\t", "\t");
		}

		public String getToken() { return(token); }
		public String getSplit() { return(split); }

		private String token;
		private String split;
	}

	public static class EndBlock extends Block {
		// marker class
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
				log.fine("Adding StaticBlock");
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

				switch (token.toLowerCase()) {

				    case CMD_DIRECTIVE:
						log.fine("Adding CommandBlock: " + args);
						blocks.add(new CommandBlock(args));
						break;

				    case REPEAT_DIRECTIVE:
						log.fine("Adding RepeatBlock: " + token);
						blocks.add(new RepeatBlock(args));
						break;
						
				    case EACH_DIRECTIVE:
						log.fine("Adding EachBlock: " + token);
						blocks.add(new EachBlock(args));
						break;
						
				    case END_DIRECTIVE:
						log.fine("Adding EndBlock");
						blocks.add(new EndBlock());
						break;
						
				    case RAW_DIRECTIVE:
						int cchArgs = args.length();
						int ichRealToken = skipToWhitespace(args, 0, cchArgs);
						int ichRealArgs = skipOverWhitespace(args, ichRealToken, cchArgs);
						token = args.substring(0, ichRealToken);
						args = args.substring(ichRealArgs);
						
						log.fine("Adding Raw TokenBlock: " + token);
						blocks.add(new TokenBlock(token, args, true));
						break;

				    default:
						log.fine("Adding TokenBlock: " + token);
						blocks.add(new TokenBlock(token, args, false));
						break;
				}
				
				ichWalk = ichDirectiveEnd + DIRECTIVE_END.length();
			}
		}
	}

	// +---------------------------+
	// | Test / Cmdline Entrypoint |
	// +---------------------------+

	public static void main(String[] args) throws Exception {

		String template = (args.length == 0
						   ? Easy.stringFromInputStream(System.in)
						   : Easy.stringFromFile(args[0]));

		System.out.print(new Template(template).render(null));
	}

	// +-------------------+
	// | Helpers & Members |
	// +-------------------+

	private static String mapOrEnv(Map<String,String> map, String key) {
		
		if (key != null && map != null && map.containsKey(key)) {
			return(map.get(key));
		}

		if (key != null && System.getenv().containsKey(key)) {
			return(System.getenv(key));
		}

		return(null);
	}
	
	private static int skipToWhitespace(String text, int ichStart, int ichMac) {

		int ichWalk = ichStart;
		while (ichWalk < ichMac && !Character.isWhitespace(text.charAt(ichWalk))) {
			++ichWalk;
		}

		return(ichWalk);
	}
	
	private static int skipOverWhitespace(String text, int ichStart, int ichMac) {

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
