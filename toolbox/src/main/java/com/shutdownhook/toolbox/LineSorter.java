/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.toolbox;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;

public class LineSorter
{
	// +-------+
	// | Setup |
	// +-------+

	public static class Params
	{
		public Boolean HasHeaderRow = false;
		public Boolean ReverseSort = false;
		public int LinesPerChunk = 500000; // assuming 1k/line, about half a gig
		public SortItemFactory Factory = new IdentitySortItemFactory();
		public LineParser Parser = new IdentityLineParser();
		public ExecutorService Executor;
		public String KeySeparator = "|||";
		public Character CommentChar = '#';
	}

	public LineSorter() {
		this(new Params());
	}
	
	public LineSorter(Params params) {
		this.params = params;
	}

	// +------------+
	// | Interfaces |
	// +------------+

	public static class ParsedLine
	{
		public String Key;
		public String Output;
	}
	
	public interface LineParser {
		// passing in the target object reduces a ton of wasted allocation
		public void parse(String line, ParsedLine target);
	}

	public interface SortItem extends Comparable<SortItem> {
		public int compareTo(SortItem item);
		public String getKeyString();
		public String getOutputString();
	}

	public interface SortItemFactory {
		public SortItem create(String key, String output);
	}

	// +-----------------+
	// | sortFile[Async] |
	// +-----------------+

	public CompletableFuture<Boolean> sortFileAsync(File inputFile, File outputFile) {

		CompletableFuture<Boolean> future = new CompletableFuture<Boolean>();

		params.Executor.submit(() -> {

			try {
				sortFile(inputFile, outputFile);
				future.complete(true);
			}
			catch (Exception e) {
				log.severe(Easy.exMsg(e, "sortFileAsync", true));
				future.complete(false);
			}
		});

		return(future);
	}

	public void sortFile(File inputFile, File outputFile) throws Exception {

		InputStream inputStm = null;
		OutputStream outputStm = null;

		try {
			inputStm = new FileInputStream(inputFile);
			outputStm = new FileOutputStream(outputFile);
			sortStream(inputStm, outputStm);
		}
		finally {
			safeClose(inputStm);
			safeClose(outputStm);
		}
	}

	// +-------------------+
	// | sortStream[Async] |
	// +-------------------+

	public CompletableFuture<Boolean> sortStreamAsync(InputStream inputStm, OutputStream outputStm) {

		CompletableFuture<Boolean> future = new CompletableFuture<Boolean>();

		params.Executor.submit(() -> {

			try {
				sortStream(inputStm, outputStm);
				future.complete(true);
			}
			catch (Exception e) {
				log.severe(Easy.exMsg(e, "sortStreamAsync", true));
				future.complete(false);
			}
		});

		return(future);
	}

	public void sortStream(InputStream inputStm, OutputStream outputStm) throws Exception {

		long startNanos = System.nanoTime();
		
		try {
			
			// 1. initial chunking and sorting
			// TRUE return means it all fit in memory and we're done
			if (chunkAndSort(inputStm, outputStm)) return;

			// 2. incrementally merge and remerge
			mergeDance();

			// 3. write the output
			writeFinalOutput(outputStm);
		}
		finally {
			long elapsedMS = (System.nanoTime() - startNanos) / 1000000L;
			log.fine(String.format("Sort complete in %d milliseconds", elapsedMS));
		}
	}

	// +------------------+
	// | writeFinalOutput |
	// +------------------+

	private void writeFinalOutput(OutputStream outputStm) throws IOException {

		OutputWriter writer = null;
		InterimInputReader input = null;

		try {
			File file = interimFiles.pop();
			input = new InterimInputReader(file, params);
		
			writer = new OutputWriter(outputStm, params);
			if (headerLine != null) writer.writeLine(headerLine);

			SortItem item;
			while ((item = input.readItem()) != null) {
				writer.writeLine(item.getOutputString());
			}
		}
		finally {
			safeClose(input);
			safeClose(writer);
		}
	}

	// +------------+
	// | mergeDance |
	// +------------+

	private void mergeDance() throws Exception {

		List<CompletableFuture<File>> futures = new LinkedList<CompletableFuture<File>>();
		
		while (interimFiles.size() > 1 || futures.size() > 0) {

			// start new pairwise merges
			while (interimFiles.size() > 1) {
				futures.add(mergeFilePairAsync(interimFiles.pop(), interimFiles.pop()));
			}

			// wait for any to complete
			CompletableFuture.anyOf(futures.toArray(new CompletableFuture[futures.size()])).get();

			// collect finished results
			int i = 0;
			while (i < futures.size()) {
				if (futures.get(i).isDone()) {
					interimFiles.push(futures.get(i).join());
					futures.remove(i);
				}
				else {
					++i;
				}
			}
		}
	}

	private CompletableFuture<File> mergeFilePairAsync(File file1, File file2) {

		CompletableFuture<File> future = new CompletableFuture<File>();

		params.Executor.submit(() -> {

			try {
				future.complete(mergeFilePair(file1, file2));
			}
			catch (Exception e) {
				log.severe(Easy.exMsg(e, "mergeFilePairAsync", true));
				future.complete(null);
			}
		});

		return(future);
	}

	private File mergeFilePair(File file1, File file2) throws IOException {

		log.fine(String.format("Merging files %s and %S", file1.getName(), file2.getName()));
		
		InterimInputReader input1 = null;
		InterimInputReader input2 = null;
		OutputWriter writer = null;

		try {
			input1 = new InterimInputReader(file1, params);
			input2 = new InterimInputReader(file2, params);
			writer = new OutputWriter(params);
			
			SortItem item1 = input1.readItem();
			SortItem item2 = input2.readItem();

			// write out in order, de-duping
			while (item1 != null && item2 != null) {
				
				int icmp = item1.compareTo(item2);
				if (params.ReverseSort) icmp *= -1;
				
				if (icmp <= 0) {
					writer.writeItem(item1);
					item1 = input1.readItem();
				}
				else {
					writer.writeItem(item2);
					item2 = input2.readItem();
				}
			}

			// spit out the balance
			
			while (item1 != null) { writer.writeItem(item1); item1 = input1.readItem(); }
			while (item2 != null) {	writer.writeItem(item2); item2 = input2.readItem(); }

			// and done!
			return(writer.getFile());
		}
		finally {
			safeClose(writer);
			safeClose(input2);
			safeClose(input1);
		}
	}

	// +--------------+
	// | chunkAndSort |
	// +--------------+

	private boolean chunkAndSort(InputStream inputStm, OutputStream outputStm) throws IOException {

		InterimInputReader input = null;

		try {
			input = new InterimInputReader(inputStm, params);
			
			// note this is an ArrayList on purpose; we want to minimize reallocs
			ArrayList<SortItem> items = new ArrayList<SortItem>(params.LinesPerChunk);
			ParsedLine target = new ParsedLine();
			int chunks = 0;

			if (params.HasHeaderRow) {
				params.Parser.parse(input.readLine(), target);
				headerLine = target.Output;
			}

			while (true) {
				
				// read the next chunk into memory and sort it.
				String line;
				items.clear(); 

				while ((line = input.readLine()) != null) {
					params.Parser.parse(line, target);
					items.add(params.Factory.create(target.Key, target.Output));
					if (items.size() == params.LinesPerChunk) break;
				}

				if (items.size() == 0) break;
				++chunks;
				
				// sort it
				if (params.ReverseSort) Collections.sort(items, Collections.reverseOrder());
				else Collections.sort(items);

				// and write it out
				OutputWriter writer = null;
				try {
					if (chunks == 1 && input.peekLine() == null) {
						// if we are here, our entire input fit into memory; no reason to 
						// write to a temp file at all --- just write directly to output
						writer = new OutputWriter(outputStm, params);
						if (headerLine != null) writer.writeLine(headerLine);
						writer.writeItems(items, false);
						log.fine(String.format("Oneshot sort complete with %d lines", items.size()));
						return(true);
					}
					else {
						// ah well that's cool
						writer = new OutputWriter(params);
						writer.writeItems(items);
						interimFiles.push(writer.getFile());
						log.fine(String.format("Pushing initial interim file %s (%d lines)",
											   writer.getFile().getName(), items.size() ));
					}
				}
				finally {
					safeClose(writer);
				}
				
			}
		}
		finally {
			safeClose(input);
		}

		// false == merging to do!
		return(false);
	}

	// +----------------------------+
	// | LineParser Implementations |
	// +----------------------------+

	// Identity
	
	public static class IdentityLineParser implements LineParser
	{
		public void parse(String line, ParsedLine target) {
			target.Key = null;
			target.Output = line;
		}
	}

	// Xsv

	public static int XSV_OUTPUT_ALL = -1;
	
	public static class XsvLineParser implements LineParser
	{
		public XsvLineParser(int icolKey, int icolOutput) {
			this(icolKey, icolOutput, '\t', '"');
		}
		
		public XsvLineParser(int icolKey, int icolOutput, char chSep, Character chQuote) {
			this.icolKey = icolKey;
			this.icolOutput = icolOutput;
			this.chSep = chSep;
			this.chQuote = chQuote;
		}
		
		private int icolKey;
		private int icolOutput;
		private char chSep;
		private Character chQuote;
		
		public void parse(String line, ParsedLine target) {

			target.Key = null;
			target.Output = null;
			
			StringBuilder sbKey = new StringBuilder();

			StringBuilder sbOutput = null;
			if (icolOutput == XSV_OUTPUT_ALL) target.Output = line;
			else sbOutput = new StringBuilder();

			int ich = 0;
			int cch = line.length();
			int icol = 0;

			while (ich < cch) {
				// here we are always pointing at the start of the next field
				
				// skip whitespace
				while (ich < cch &&
					   Character.isWhitespace(line.charAt(ich)) &&
					   line.charAt(ich) != chSep) {
					++ich;
				}

				if (ich < cch) {
					
					// check for quoting
					boolean quoting = false;
					if (chQuote != null && line.charAt(ich) == chQuote) {
						quoting = true;
						++ich;
					}

					// walk to end of the field; collect chars iff target col
					while (ich < cch) {

						char ch = line.charAt(ich);
						
						if (quoting && ch == chQuote) {
							++ich;
							if (ich < cch && line.charAt(ich) == chQuote) {
								// escaped quotation; accumulate iff target
								if (icol == icolKey) sbKey.append(chQuote);
								if (icol == icolOutput) sbOutput.append(chQuote);
								++ich;
							}
							else {
								// end of quoted field; find separator
								while (ich < cch && line.charAt(ich) != chSep) ++ich;
								break;
							}
						}
						else if (!quoting && ch == chSep) {
							// end of non-quoted field; eat back whitespace iff target
							if (icol == icolKey) removeTrailingWhitespace(sbKey);
							if (icol == icolOutput) removeTrailingWhitespace(sbOutput);
							break;
						}
						else {
							// normal character, accumulate & move on
							if (icol == icolKey) sbKey.append(ch);
							if (icol == icolOutput) sbOutput.append(ch);
							++ich;
						}
					}

					// woot!
					if (icol >= icolKey && icol >= icolOutput) {
						target.Key = sbKey.toString();
						if (sbOutput != null) target.Output = sbOutput.toString();
						return;
					}

					// bounce over separator and increment column counter
					if (ich < cch && line.charAt(ich) == chSep) ++ich;
					++icol;
				}
			}
		}
	}

	// +--------------------------+
	// | SortItem Implementations |
	// +--------------------------+

	// Identity
	
	public static class IdentitySortItem implements SortItem
	{
		public IdentitySortItem(String line) { this.line = line; }
		public int compareTo(SortItem item) { return(line.compareTo(((IdentitySortItem)item).line)); }
		public String getKeyString() { return(null); }
		public String getOutputString() { return(line); }
		private String line;
	}

	public static class IdentitySortItemFactory implements SortItemFactory
	{
		public SortItem create(String key, String output) {
			return(new IdentitySortItem(output));
		}
	}
	
	// String

	public static class StringSortItem implements SortItem
	{
		public StringSortItem(String key, String output) { this.key = key; this.output = output; }
		public int compareTo(SortItem item) { return(key.compareTo(((StringSortItem)item).key)); }
		public String getKeyString() { return(key); }
		public String getOutputString() { return(output); }
		private String key;
		private String output;
	}

	public static class StringSortItemFactory implements SortItemFactory
	{
		public SortItem create(String key, String output) {
			return(new StringSortItem(key, output));
		}
	}

	// Typed (Long)

	public static class TypedSortItem<T extends Comparable<T>> extends StringSortItem
	{
		public TypedSortItem(T t, String key, String output) { super(key, output); this.t = t; }
		public int compareTo(SortItem item) { return(t.compareTo(((TypedSortItem<T>)item).t)); }
		private T t;
	}

	public static class LongSortItemFactory implements SortItemFactory
	{
		public SortItem create(String key, String output) {
			
			Long l = 0L;
			try { l = Long.parseLong(key); }
			catch (Exception e) { /* eat it and use 0 */ }
			
			return(new TypedSortItem<Long>(l, key, output));
		}
	}

	// +--------------+
	// | OutputWriter |
	// +--------------+

	public static class OutputWriter implements Closeable
	{
		public OutputWriter(OutputStream stm, Params params) throws IOException {
			this.stm = stm;
			this.params = params;
			this.writer = new OutputStreamWriter(stm);
			this.buf = new BufferedWriter(writer);
		}
		
		public OutputWriter(Params params) throws IOException {

			this.params = params;
			
			this.file = File.createTempFile("srt", ".xsv");
			this.file.deleteOnExit();
			
			this.writer = new FileWriter(file);
			this.buf = new BufferedWriter(writer);
		}

		public void close() {
			LineSorter.safeClose(buf);
			LineSorter.safeClose(writer);
		}

		public void writeItems(List<SortItem> items) throws IOException {
			writeItems(items, true);
		}

		public void writeItems(List<SortItem> items, boolean writeKey) throws IOException {
			for (SortItem item : items) writeItem(item, writeKey);
		}

		public void writeItem(SortItem item) throws IOException {
			writeItem(item, true);
		}

		public void writeItem(SortItem item, boolean writeKey) throws IOException {
			writeLine(writeKey && item.getKeyString() != null
					  ? item.getKeyString() + params.KeySeparator + item.getOutputString()
					  : item.getOutputString());
		}
		
		public void writeLine(String line) throws IOException {
			buf.write(line);
			buf.newLine();
		}

		public File getFile() {
			return(file);
		}

		private File file;
		private Params params;
		private OutputStream stm;
		private OutputStreamWriter writer;
		private BufferedWriter buf;
	}

	// +--------------------+
	// | InterimInputReader |
	// +--------------------+

	public static class InterimInputReader implements Closeable
	{
		public InterimInputReader(File file, Params params) throws IOException {
			this.file = file;
			this.stm = new FileInputStream(file);
			this.params = params;
			sharedInit();
		}

		public InterimInputReader(InputStream stm, Params params) throws IOException {
			this.stm = stm;
			this.params = params;
			sharedInit();
		}

		private void sharedInit() throws IOException {
			reader = new InputStreamReader(stm);
			buf = new BufferedReader(reader);
		}

		public void close() {
			LineSorter.safeClose(buf);
			LineSorter.safeClose(reader);
			
			if (file != null) {
				LineSorter.safeClose(stm);
				
				try { file.delete(); }
				catch (Exception e) { /* eat it */ }
			}
		}

		public SortItem readItem() throws IOException {
			
			String line = readLine();
			if (line == null) return(null);

			String key;
			String output;
			
			int ichSep = line.indexOf(params.KeySeparator);

			if (ichSep >= 0) {
				key = line.substring(0, ichSep);
				output = line.substring(ichSep + params.KeySeparator.length());
			}
			else {
				key = null;
				output = line;
			}

			return(params.Factory.create(key, output));
		}
		
		public String readLine() throws IOException {

			if (nextLine != null) {
				String t = nextLine;
				nextLine = null;
				return(t);
			}
			
			return(readLineInternal());
		}

		public String peekLine() throws IOException {
			if (nextLine != null) return(nextLine);
			nextLine = readLineInternal();
			return(nextLine);
		}

		private String readLineInternal() throws IOException {
			// skips empty and comment lines
			while (true) {
				String line = buf.readLine();
				if (line == null) return(line);
				if (line.length() == 0) continue;
				if (params.CommentChar == null) return(line);
				if (line.charAt(0) != params.CommentChar) return(line);
			}
		}

		public File getFile() {
			return(file);
		}
		
		private File file;
		private InputStream stm;
		private InputStreamReader reader;
		private BufferedReader buf;
		private String nextLine;
		private Params params;
	}
		
	// +---------+
	// | Helpers |
	// +---------+

	private static void safeClose(Closeable c) {
		try { if (c != null) c.close(); }
		catch (Exception e) { /* eat it */ }
	}

	private static void removeTrailingWhitespace(StringBuilder sb) {
		while (sb.length() > 0 && Character.isWhitespace(sb.charAt(sb.length() - 1))) {
			sb.setLength(sb.length() - 1);
		}
	}

	// +------------+
	// | Entrypoint |
	// +------------+

	public static void main(String[] args) throws Exception {

		Easy.setSimpleLogFormat("FINE");
		
		int icolKey = -1;
		int icolOutput = XSV_OUTPUT_ALL;
		char chSep = '\t';
		char chQuote = '\"';
		boolean numericSort = false;

		Params params = new Params();
		params.Executor = Executors.newCachedThreadPool();
		
		for (String arg : args) {
			
			if (!arg.startsWith("-")) continue;
			
			switch (arg.charAt(1)) {
				case '?': usage(); return;
				case 'h': params.HasHeaderRow = true; break;
				case 'r': params.ReverseSort = true; break;
				case 'x': params.LinesPerChunk = Integer.parseInt(arg.substring(2)); break;
				case 't': params.CommentChar = arg.charAt(2); break;
				case 'n': numericSort = true; break;
				case 's': chSep = arg.charAt(2); break;
				case 'q': chQuote = arg.charAt(2); break;
				case 'c': icolKey = Integer.parseInt(arg.substring(2)); break;
				case 'o': icolOutput = Integer.parseInt(arg.substring(2)); break;
			}
		}

		if (icolKey != -1 || icolOutput != XSV_OUTPUT_ALL) {
			params.Parser = new XsvLineParser(icolKey, icolOutput, chSep, chQuote);
			params.Factory = (numericSort ? new LongSortItemFactory() : new StringSortItemFactory());
		}

		LineSorter sorter = new LineSorter(params);
		sorter.sortStreamAsync(System.in, System.out).join();

		params.Executor.shutdownNow();
	}

	private static void usage() {
		System.out.println("java -cp PATH com.shutdownhook.toolbox.LineSorter ARGS" );
		System.out.println("Sorts lines from stdin to stdout");
		System.out.println("-h\ttreat first row as header (default false)");
		System.out.println("-r\treverse sort (default false)");
		System.out.println("-sCHAR\tuse CHAR as field separator (default '\\t')");
		System.out.println("-qCHAR\tuse CHAR as quote character (default '\\\"')");
		System.out.println("-tCHAR\tuse CHAR as start-of-line comment character (default '#')");
		System.out.println("-n\tsort numerically (default string)");
		System.out.println("-c#\tsort on field # (default full line)");
		System.out.println("-o#\toutput field # (default full line)");
		System.out.println("-x#\tset lines per chunk to #");
	}

	// +---------+
	// | Members |
	// +---------+

	private Params params;
	private String headerLine;
	private LinkedList<File> interimFiles = new LinkedList<File>();
	
	private final static Logger log = Logger.getLogger(LineSorter.class.getName());
}
