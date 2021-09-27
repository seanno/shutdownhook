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
		
		for (String key : realTokens.keySet()) {
			log.finest("render map: [" + key + "]=[" + realTokens.get(key) + "]");
		}
		
		StringBuilder sb = new StringBuilder();
		renderRecursive(sb, realTokens, realProcessor, blocks);
		return(sb.toString());
	}

	private void renderRecursive(StringBuilder sb,
								 Map<String,String> tokens,
								 TemplateProcessor processor,
								 List<Block> blocks) throws Exception {

		for (Block block : blocks) {

			log.fine(String.format("renderRecursive; cch on entry = %d", sb.length()));

			if (block instanceof RecursiveBlock) {
				// gonna recurse
				RecursiveBlock rb = (RecursiveBlock) block;
				
				switch (rb.getDirective()) {
					
				    case REPEAT_DIRECTIVE:
						int counter = 0;
						while (processor.repeat(rb.getArgs(), counter++)) {
							renderRecursive(sb, tokens, processor, rb.getChildren());
						}
						break;

				    case EACH_DIRECTIVE:
						String token = rb.getArgs()[0];
						String split = rb.getArgs()[1];
						String originalTokenValue = mapOrEnv(tokens, token);
						
						for (String elt : originalTokenValue.split(split, -1)) {
							tokens.put(token, elt);
							renderRecursive(sb, tokens, processor, rb.getChildren());
						}
						
						tokens.put(token, originalTokenValue);
						break;

				    default:
						throw new Exception("What evil is this? " + rb.getDirective());
				}
			}
			else {
				// normal rendering block
				block.append(sb, tokens, processor);
			}
		}
	}

	// +-------------------+
	// | TemplateProcessor |
	// +-------------------+

	public static class TemplateProcessor {

		public String token(String token, String args) throws Exception {
			String msg = String.format("TemplateProcessor.token used without impl " +
									   "for token %s with args %s", token, args);
			throw new Exception(msg);
		}

		public boolean repeat(String[] args, int counter) {
			int count = Integer.parseInt(args[0]);
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

			int cchSnippet = (text.length() > 6 ? 6 : text.length());
			log.fine("Adding StaticBlock: " + text.substring(cchSnippet) + "...");
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

			log.fine(String.format("Adding TokenBlock (%s): %s", outputRaw, token));
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
			
			log.fine("Adding CommandBlock: " + cmdLine);
		}

		public void append(StringBuilder sb,
						   Map<String,String> params,
						   TemplateProcessor processor) throws Exception {
			
			sb.append(Easy.stringFromProcess(cmdLine));
		}

		private String cmdLine;
	}

	public static class RecursiveBlock extends Block
	{
		public RecursiveBlock(String directive, String args) {
			this.directive = directive.toLowerCase();
			this.args = splitOnWhitespace(args);
			this.children = new ArrayList<Block>();

			log.fine(String.format("Adding %s RecursiveBlock %s", directive, args));
		}

		public List<Block> getChildren() { return(children); }
		public String getDirective() { return(directive); }
		public String[] getArgs() { return(args); }
	
		private String directive;
		private String[] args;
		private List<Block> children;
	}

	// +-------------+
	// | setupBlocks |
	// +-------------+

	private void setupBlocks() throws Exception {
		blocks = new ArrayList<Block>();
		setupBlockLevel(blocks, templateText, 0, templateText.length());
	}

	// on entry ichStart is the start of template text. We build the
	// level (recursively if needed) by adding to levelBlocks until
	// we come to the end of the text or a matching end block.
	// Return value is the first character following that end block
	// (or cch on end of template)
	
	private int setupBlockLevel(List<Block> levelBlocks,
								String templateText,
								int ichStart, int cch) throws Exception {

		String directive;
		String args;
		
		int ichWalk;
		int ichDirectiveStart;

		ichWalk = ichStart;
		while (ichWalk < cch) {
			
			ichDirectiveStart = templateText.indexOf(DIRECTIVE_START, ichWalk);
			
			if (ichDirectiveStart != ichWalk) {
				// there is stuff before the next directive (or before end of
				// the template). Add it as a static block.
				int ichMac = (ichDirectiveStart == -1 ? cch : ichDirectiveStart);
				levelBlocks.add(new StaticBlock(templateText.substring(ichWalk, ichMac)));
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
				directive = templateText.substring(ichWalk, ichSpace);

				int ichArgs = skipOverWhitespace(templateText, ichSpace, ichDirectiveEnd);
				args = (ichArgs == ichDirectiveEnd
						? null : templateText.substring(ichArgs, ichDirectiveEnd).trim());
				
				ichWalk = ichDirectiveEnd + DIRECTIVE_END.length();
				
				switch (directive.toLowerCase()) {

					// end forces us to fall out
					
				    case END_DIRECTIVE:
						log.fine("Found EndBlock; returning " + Integer.toString(ichWalk));
						return(ichWalk);

					// recursive blocks
						
				    case REPEAT_DIRECTIVE:
				    case EACH_DIRECTIVE:
						RecursiveBlock rb = new RecursiveBlock(directive, args);
						levelBlocks.add(rb);
						ichWalk = setupBlockLevel(rb.getChildren(),
												  templateText,
												  ichWalk, cch);
						break;
						
					// content blocks
						
				    case CMD_DIRECTIVE:
						levelBlocks.add(new CommandBlock(args));
						break;

				    case RAW_DIRECTIVE:
						int cchArgs = args.length();
						int ichRealToken = skipToWhitespace(args, 0, cchArgs);
						int ichRealArgs = skipOverWhitespace(args, ichRealToken, cchArgs);
						String token = args.substring(0, ichRealToken);
						args = args.substring(ichRealArgs);

						levelBlocks.add(new TokenBlock(token, args, true));
						break;

				    default:
						// directive is just a token
						levelBlocks.add(new TokenBlock(directive, args, false));
						break;
				}
				
			}
		}

		return(cch);
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

	private static String[] splitOnWhitespace(String text) {

		List<String> items = new ArrayList<String>();

		int cch = text.length();
		int ichWalk = skipOverWhitespace(text, 0, cch);

		while (ichWalk < cch) {
			int ichSpace = skipToWhitespace(text, ichWalk, cch);

			String split = 
				text.substring(ichWalk, ichSpace)
				.replaceAll("\\n", "\n")
				.replaceAll("\\t", "\t");

			items.add(split);
			ichWalk = skipOverWhitespace(text, ichSpace , cch);
		}

		return(items.toArray(new String[items.size()]));
	}

	private String templateText;
	private List<Block> blocks;
	
	private final static Logger log = Logger.getLogger(Template.class.getName());
}
