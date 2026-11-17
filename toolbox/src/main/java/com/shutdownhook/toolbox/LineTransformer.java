/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.toolbox;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class LineTransformer
{
	// +---------------+
	// | LineProcessor |
	// +---------------+
	
	public interface LineProcessor {
		public String processLine(int lineIndex, String line) throws IOException;
		public default boolean stop(int lineIndex) { return(false); }
	}

	// +------+
	// | read |
	// +------+

	public static String read(Path path, int lineIndexStart, int lineIndexEndInclusive) throws IOException {

		StringBuilder sb = new StringBuilder();

		process(path, new LineProcessor() {
			public String processLine(int lineIndex, String line) throws IOException {
				if (inRange(lineIndex, lineIndexStart, lineIndexEndInclusive)) {
					sb.append(line).append("\n");
				}
				return(null);
			}
			public boolean stop(int lineIndex) {
				return(lineIndexEndInclusive != -1 && lineIndex > lineIndexEndInclusive);
			}
		});

		return(sb.toString());
	}

	// +---------+
	// | readAll |
	// +---------+

	// special snowflake #1

	public static String readAll(Path path) throws IOException {
		if (!Files.exists(path)) return("");
		return(Files.readString(path));
	}

	// +--------+
	// | search |
	// +--------+

	// case-INsensitive
	// returned lines are prefixed by line index and a tab

	public static String search(Path path, String search) throws IOException {

		StringBuilder sb = new StringBuilder();
		Pattern pattern = Pattern.compile(Pattern.quote(search), Pattern.CASE_INSENSITIVE);

		process(path, new LineProcessor() {
			public String processLine(int lineIndex, String line) throws IOException {
				if (pattern.matcher(line).find()) {
					sb.append(String.format("%d\t", lineIndex)).append(line).append("\n");
				}
				return(null);
			}
		});

		return(sb.toString());
	}

	// +--------+
	// | delete |
	// +--------+

	public static void delete(Path path, int lineIndexStart, int lineIndexEndInclusive) throws IOException {

		if (!Files.exists(path)) return;
		
		transform(path, new LineProcessor() {
			public String processLine(int lineIndex, String line) throws IOException {
				if (inRange(lineIndex, lineIndexStart, lineIndexEndInclusive)) return(null);
				return(line);
			}
		});
	}

	// +---------+
	// | replace |
	// +---------+

	public static void replace(Path path, int lineIndexStart,
							   int lineIndexEndInclusive, String lines) throws IOException {

		if (!Files.exists(path) && lineIndexStart == 0) {
			append(path, lines);
			return;
		}
		
		transform(path, new LineProcessor() {
			public String processLine(int lineIndex, String line) throws IOException {
				if (lineIndex == lineIndexStart) return(lines);
				if (inRange(lineIndex, lineIndexStart, lineIndexEndInclusive)) return(null);
				return(line);
			}
		});
	}

	// +--------+
	// | insert |
	// +--------+

	public static void insert(Path path, int lineIndexStart, String lines) throws IOException {

		if (!Files.exists(path) && lineIndexStart == 0) {
			append(path, lines);
			return;
		}
		
		transform(path, new LineProcessor() {
			public String processLine(int lineIndex, String line) throws IOException {
				if (lineIndex == lineIndexStart) {
					StringBuilder sb = new StringBuilder();
					sb.append(lines);
					if (!lines.endsWith("\n")) sb.append("\n");
					sb.append(line);
					return(sb.toString());
				}
				return(line);
			}
		});
	}
	
	// +--------+
	// | append |
	// +--------+

	// special snowflake #2
	
	public static void append(Path path, String lines) throws IOException {

		boolean existingFile = Files.exists(path);
		boolean prependNewline = existingFile ? !endsWithNewline(path) : false;

		FileWriter writer = null;
		BufferedWriter buffered = null;

		try {
			writer = new FileWriter(path.toFile(), true);
			buffered = new BufferedWriter(writer);

			if (prependNewline) buffered.newLine();
			buffered.write(lines);
			if (!lines.endsWith("\n")) buffered.newLine();
		}
		finally {
			Easy.safeClose(buffered);
			Easy.safeClose(writer);
		}
	}

	// +---------+
	// | process |
	// +---------+

	// read over path, allowing the Processor to look at each line

	public static void process(Path path, LineProcessor processor) throws IOException {

		// no file == no lines
		if (!Files.exists(path)) return;
		
		FileReader reader = null;
		BufferedReader buffered = null;

		try {
			reader = new FileReader(path.toFile());
			buffered = new BufferedReader(reader);
			
			String line;
			int lineIndex = 0;
			while ((line = buffered.readLine()) != null) {
				processor.processLine(lineIndex, line);
				if (processor.stop(lineIndex)) break;
				++lineIndex;
			}
		}
		finally {
			Easy.safeClose(buffered);
			Easy.safeClose(reader);
		}
	}
	
	// +-----------+
	// | transform |
	// +-----------+

	// read over path, allowing the Processor to transform each
	// line. Writes to a temp file and copies the temp file to
	// path on completion. 

	public static void transform(Path path, LineProcessor processor) throws IOException {

		if (!Files.exists(path)) {
			throw new NoSuchFileException(path.toString());
		}

		Path outputPath = Paths.get(path.toString() + ".tmp");
		FileWriter writer = null;
		BufferedWriter buffered = null;
		boolean success = false;

		try {
			writer = new FileWriter(outputPath.toFile());
			buffered = new BufferedWriter(writer);
			final BufferedWriter bufferedFinal = buffered;

			process(path, new LineProcessor() { // "super" LP
				public String processLine(int lineIndex, String line) throws IOException {
					String newLine = processor.processLine(lineIndex, line);
					if (newLine != null) {
						bufferedFinal.write(newLine);
						if (!newLine.endsWith("\n")) bufferedFinal.newLine();
					}
					return(null);
				}
				public boolean stop(int lineIndex) {
					return(processor.stop(lineIndex));
				}
					
			});

			buffered.close(); buffered = null;
			writer.close(); writer = null;

			Files.move(outputPath, path,
					   StandardCopyOption.ATOMIC_MOVE,
					   StandardCopyOption.REPLACE_EXISTING);
		}
		finally {
			Easy.safeClose(buffered);
			Easy.safeClose(writer);
			
			try { Files.deleteIfExists(outputPath); }
			catch (Exception eDel) { /* eat it */ }
		}
	}

	// +---------+
	// | Helpers |
	// +---------+

	private static boolean inRange(int index, int indexStart, int indexEndInclusive) {
		return(index >= indexStart &&
			   (indexEndInclusive == -1 || index <= indexEndInclusive));
	}

	private static boolean endsWithNewline(Path path) throws IOException {

		long cb;
		try { cb = Files.size(path); }
		catch (NoSuchFileException e) { cb = 0; }
		if (cb == 0) return(false);

		SeekableByteChannel channel = null;
		try {
			channel = Files.newByteChannel(path);
			channel.position(cb - 1);
			ByteBuffer buf = ByteBuffer.allocate(1);
			channel.read(buf);
			return(buf.get(0) == '\n');
		}
		finally {
			Easy.safeClose(channel);
		}
	}
	
	// +---------+
	// | Members |
	// +---------+
	
	private final static Logger log = Logger.getLogger(LineTransformer.class.getName());
}

