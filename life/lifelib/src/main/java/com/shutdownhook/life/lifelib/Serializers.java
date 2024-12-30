//
// SERIALIZERS.JAVA
//
// >>> I AM NOT THREAD SAFE !!!
//

package com.shutdownhook.life.lifelib;

import java.io.File;
import java.io.IOException;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Random;
import java.util.Scanner;
import java.util.logging.Logger;

import com.shutdownhook.toolbox.Easy;

public class Serializers
{
	public final static char CELL_ON = 'X';
	public final static char CELL_OFF = '.';
		
	// +------------+
	// | Interfaces |
	// +------------+

	public interface Serializer {
		public void serialize(Bitmap bm, PrintWriter pw) throws Exception;
		default public Bitmap deserialize(Scanner scanner) throws Exception { return(null); }
	}

	// +------+
	// | file |
	// +------+

	public static void toFile(Bitmap bm, File file, Serializer serializer) throws Exception {

		log.info(String.format("Writing %d,%d bitmap to %s",
							   bm.getDx(), bm.getDy(),
							   file.getAbsolutePath()));

		FileWriter fw = null;
		PrintWriter pw = null;

		try {
			fw = new FileWriter(file);
			pw = new PrintWriter(fw);
			serializer.serialize(bm, pw);
		}
		finally {
			Easy.safeClose(pw);
			Easy.safeClose(fw);
		}
	}

	public static void toFile(Bitmap bm, File file) throws Exception {
		toFile(bm, file, new CompactSerializer());
	}
	
	public static Bitmap fromFile(File file, Serializer serializer) throws Exception {

		log.info(String.format("Reading bitmap from %s", file.getAbsolutePath()));
		
		Scanner scanner = null;

		try {
			scanner = new Scanner(file);
			return(serializer.deserialize(scanner));
		}
		finally {
			Easy.safeClose(scanner);
		}
	}

	public static Bitmap fromFile(File file) throws Exception {
		return(fromFile(file, new CompactSerializer()));
	}

	// +--------+
	// | String |
	// +--------+

	public static String toString(Bitmap bm, Serializer serializer) throws Exception {

		log.info(String.format("Writing %d,%d bitmap to string",
							   bm.getDx(), bm.getDy()));

		StringWriter sw = null;
		PrintWriter pw = null;

		try {
			sw = new StringWriter();
			pw = new PrintWriter(sw);
			serializer.serialize(bm, pw);

			return(sw.toString());
		}
		finally {
			Easy.safeClose(pw);
			Easy.safeClose(sw);
		}
	}

	public static String toString(Bitmap bm) throws Exception {
		return(toString(bm, new ExpandedSerializer()));
	}
	
	public static Bitmap fromString(String str, Serializer serializer) throws Exception {

		log.info("Reading bitmap from string");
		
		Scanner scanner = null;

		try {
			scanner = new Scanner(str);
			return(serializer.deserialize(scanner));
		}
		finally {
			Easy.safeClose(scanner);
		}
	}

	public static Bitmap fromString(String str) throws Exception {
		return(fromString(str, new ExpandedSerializer()));
	}

	// +---------+
	// | Compact |
	// +---------+

	public static class CompactSerializer implements Serializer {

		public void serialize(Bitmap bm, PrintWriter pw) throws Exception {
			pw.println(bm.getDx());
			pw.println(bm.getDy());

			for (int i = 0; i < bm.longs.length; ++i) pw.println(bm.longs[i]);
		}
		
		public Bitmap deserialize(Scanner scanner) throws Exception {
			int dx = scanner.nextInt();
			int dy = scanner.nextInt();

			int longsNeeded = Bitmap.getLongsNeeded(dx, dy);
			long[] longs = new long[longsNeeded];
			for (int i = 0; i < longsNeeded; ++i) {
				longs[i] = scanner.nextLong();
			}

			return(new Bitmap(dx, dy, longs));
		}
	}

	// +----------+
	// | Expanded |
	// +----------+

	public static class ExpandedSerializer implements Serializer {

		public void serialize(Bitmap bm, PrintWriter pw) throws Exception {

			int dx = bm.getDx();
			int dy = bm.getDy();
			
			pw.println(String.format("%d %d", dx, dy));

			for (int y = 0; y < dy; ++y) {
				for (int x = 0; x < dx; ++x) {
					pw.print(bm.get(x,y) ? CELL_ON : CELL_OFF);
				}
				pw.println();
			}
		}

		public Bitmap deserialize(Scanner scanner) throws Exception {
			int dx = scanner.nextInt();
			int dy = scanner.nextInt();

			int bitsRead = 0;
			int bitsNeeded = Bitmap.getBitCount(dx, dy);
			long[] longs = new long[Bitmap.getLongsNeeded(dx, dy)];

			for (int i = 0; i < longs.length; ++i) {

				long setter = 1L;
				while (setter != 0L && bitsRead < bitsNeeded) {

					char ch = nextChar(scanner);
					if (ch == CELL_ON) {
						longs[i] |= setter;
					}
					
					++bitsRead;
					setter <<= 1;
				}
			}

			return(new Bitmap(dx, dy, longs));
		}
			
		private char nextChar(Scanner scanner) throws Exception {

			while (currentLine == null || ichNext >= currentLine.length()) {
				currentLine = scanner.nextLine().trim();
				ichNext = 0;
			}

			return(currentLine.charAt(ichNext++));
		}

		private String currentLine = null;
		private int ichNext = 0;
	}
	
	private final static Logger log = Logger.getLogger(Serializers.class.getName());
}
