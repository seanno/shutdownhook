/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.evolve;

import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.lang.NumberFormatException;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.shutdownhook.toolbox.Easy;

public class Normalize
{
	// +-------+
	// | Setup |
	// +-------+

	public static class Config
	{
		public String InputTsvPath;
		public String OutputTsvPath;
		public String DictionaryPath;

		public Boolean HasHeaders;
		public String[] OutputColumnNames; // if !HasHeaders, "field0", "field1", etc.
		public Boolean SkipIncompleteRows;
		public Boolean AppendOriginalRowNumber;

		public static Config fromJson(String json) {
			return(new Gson().fromJson(json, Config.class));
		}

		public String toJson() {
			return(new Gson().toJson(this));
		}
	}

	// +-----------+
	// | Normalize |
	// +-----------+

	public static void normalize(Config cfg) throws Exception {

		List<ColumnInfo> infos = interrogateColumns(cfg);
		writeDictionary(infos, cfg);
		writeOutput(infos, cfg);
	}

	// +-------------+
	// | WriteOutput |
	// +-------------+

	private static void writeOutput(List<ColumnInfo> infos, Config cfg) throws Exception {
		
		log.info("Writing output to " + cfg.OutputTsvPath);

		FileWriter writer = null;
		BufferedWriter bufferedWriter = null;
		FileReader reader = null;
		BufferedReader bufferedReader = null;
		
		try {

			writer = new FileWriter(new File(cfg.OutputTsvPath));
			bufferedWriter = new BufferedWriter(writer);
			reader = new FileReader(new File(cfg.InputTsvPath));
			bufferedReader = new BufferedReader(reader);

			// headers
			for (int i = 0; i < infos.size(); ++i) {
				if (i > 0) bufferedWriter.write("\t");
				bufferedWriter.write(infos.get(i).Name);
			}

			if (cfg.AppendOriginalRowNumber) {
				bufferedWriter.write("\t" + ROWNUM_FIELD_HEADER);
			}
			
			bufferedWriter.newLine();

			int originalRowNum = 1;
			
			// rows
			if (cfg.HasHeaders) {
				bufferedReader.readLine();
				++originalRowNum;
			}
			
			String[] fields;
			StringBuilder sb = new StringBuilder();
			
			int goodLines = 0;
			int skippedLines = 0;
			
			while ((fields = toFields(bufferedReader.readLine())) != null) {

				sb.setLength(0);
				boolean lineError = false;
				
				for (int i = 0; i < infos.size(); ++i) {
					
					if (i > 0) sb.append("\t");

					try {
						double d = infos.get(i).normalize(fields, cfg);
						sb.append(String.format("%04.3f", d));
					}
					catch (Exception e) {
						if (!cfg.SkipIncompleteRows) throw e;
						// otherwise ignore this line and keep on trucking
						lineError = true;
						++skippedLines;
						break;
					}
				}

				if (!lineError) {
					++goodLines;
					sb.append("\t").append(Integer.toString(originalRowNum));
					bufferedWriter.write(sb.toString());
					bufferedWriter.newLine();
				}

				++originalRowNum;

				if (((goodLines + skippedLines) % REPORT_INTERVAL) == 0) {
					log.info(String.format("Processing - %d good lines so far " +
										   "(%d skipped)", goodLines, skippedLines));
				}
			}

			log.info(String.format("Complete - Wrote %d good lines (%d skipped)",
								   goodLines, skippedLines));

		}
		finally {
			
			if (bufferedReader != null) bufferedReader.close();
			if (reader != null) reader.close();
			if (bufferedWriter != null) bufferedWriter.close();
			if (writer != null) writer.close();
		}
		
	}

	// +-----------------+
	// | WriteDictionary |
	// +-----------------+

	private static void writeDictionary(List<ColumnInfo> infos, Config cfg)
		throws Exception {

		if (cfg.DictionaryPath == null) return;
		log.info("Writing dictionary to " + cfg.DictionaryPath);

		Gson gson = new Gson();
		JsonParser parser = new JsonParser();
		JsonObject json = new JsonObject();
		
		json.add("config", parser.parse(cfg.toJson()));
		json.add("columns", parser.parse(gson.toJson(infos)));

		Easy.stringToFile(cfg.DictionaryPath, gson.toJson(json));
	}

	// +-------------------+
	// | InterrgateColumns |
	// +-------------------+

	// returns a list of column infos, in the order they should be output,
	// prepped to normalize fields (note this is a full read of the input)
	
	private static List<ColumnInfo> interrogateColumns(Config cfg) throws Exception {

		log.info("Interrogating columns from " + cfg.InputTsvPath);

		List<ColumnInfo> infos = null;

		FileReader reader = null;
		BufferedReader buffered = null;
		
		try {

			reader = new FileReader(new File(cfg.InputTsvPath));
			buffered = new BufferedReader(reader);

			// first setup headers 
			String[] fields = toFields(buffered.readLine());
			infos = setupColumns(fields, cfg);

			// now guess at some data types
			if (cfg.HasHeaders) fields = toFields(buffered.readLine());
			for (ColumnInfo info : infos) info.setDataType(fields);

			// figure out ranges
			while (fields != null) {
				for (ColumnInfo info : infos) info.incorporateData(fields, cfg);
				fields = toFields(buffered.readLine());
			}

			// and finish things up
			for (ColumnInfo info : infos) info.prepForNormalization();
		}
		finally {
			
			if (buffered != null) buffered.close();
			if (reader != null) reader.close();
		}

		log.info("Set to normalize " + Integer.toString(infos.size()) + " columns");
		
		return(infos);
	}

	private static List<ColumnInfo> setupColumns(String[] firstLineFields, Config cfg) {
		
		List<ColumnInfo> infos = new LinkedList<ColumnInfo>();
			
		for (int ifield = 0; ifield < firstLineFields.length; ++ifield) {

			String name = (cfg.HasHeaders
						   ? firstLineFields[ifield]
						   : DEFAULT_FIELD_PREFIX + Integer.toString(ifield));
			
			infos.add(new ColumnInfo(name, ifield));
		}

		if (cfg.OutputColumnNames != null) {

			for (String name : cfg.OutputColumnNames) {
				for (int i = 0; i < infos.size(); ++i) {
					if (infos.get(i).Name.equals(name)) {
						infos.add(infos.remove(i));
						break;
					}
				}
			}
		}

		return(infos);
	}

	// +------------+
	// | ColumnInfo |
	// +------------+

	public static class ColumnInfo
	{
		public ColumnInfo(String name, int indexInInput) {
			this.Name = name;
			this.IndexInInput = indexInInput;

			Min = Double.MAX_VALUE;
			Max = Double.MIN_VALUE;
		}

		public void setDataType(String[] exampleFields) {
			
			String val = exampleFields[IndexInInput].trim();
			
			try {
				double d = Double.parseDouble(val);
				IsNumeric = true;
			}
			catch (NumberFormatException e) {
				IsNumeric = false;
				uniqueInstances = new HashSet<String>();
			}
		}

		public void incorporateData(String[] fields, Config cfg)
			throws NumberFormatException {

			String val = fields[IndexInInput].trim();

			if (val.isEmpty() && cfg.SkipIncompleteRows) {
				// skip it
			}
			
			if (IsNumeric) {
				try {
					double d = Double.parseDouble(val);
					if (d > Max) Max = d;
					if (d < Min) Min = d;
				}
				catch (NumberFormatException e) {
					if (!cfg.SkipIncompleteRows) throw e;
					// otherwise just ignore it, as we'll also do in the main loop
				}
			}
			else {
				uniqueInstances.add(val);
			}
		}

		public void prepForNormalization() {
			
			if (IsNumeric) return;

			Min = 0.0;
			Max = 0.0;
			
			InstanceVals = new HashMap<String,Double>();

			double increment = 1.0 / ((double)uniqueInstances.size() - 1);
			double val = 0.0;

			for (String instance : uniqueInstances) {
				InstanceVals.put(instance, val);
				val += increment;
			}
		}

		public double normalize(String[] fields, Config cfg) throws Exception {

			String val = fields[IndexInInput].trim();

			if (val.isEmpty() && cfg.SkipIncompleteRows) {
				throw new Exception("empty field");
			}
			
			if (IsNumeric) {
				double d = Double.parseDouble(val);
				return(d / (Max - Min));
			}
			
			return(InstanceVals.get(val));
		}

		// Members
		
		public String Name;
		public int IndexInInput;
		public boolean IsNumeric;

		public double Min;
		public double Max;

		private Map<String,Double> InstanceVals;

		private transient Set<String> uniqueInstances;
	}

	private static String[] toFields(String line) {
		return(line == null ? null : line.split("\t"));
	}

	// +------------+
	// | Entrypoint |
	// +------------+

	public static void main(String[] args) throws Exception {

		Easy.setSimpleLogFormat("INFO");

		if (args.length != 7) {
			usage();
			return;
		}

		Config cfg = new Config();
		cfg.InputTsvPath = args[0];
		cfg.OutputTsvPath = args[1];
		cfg.DictionaryPath = args[2];

		cfg.HasHeaders = Boolean.parseBoolean(args[3]);
		cfg.OutputColumnNames = args[4].split(",");
		cfg.SkipIncompleteRows = Boolean.parseBoolean(args[5]);
		cfg.AppendOriginalRowNumber = Boolean.parseBoolean(args[6]);

		normalize(cfg);
	}

	private static void usage() {
		
		System.err.println("Usage: java -cp JAR_PATH com.shutdownhook.evolve.Normalize \\");
		System.err.println("\tINPUT_PATH OUTPUT_PATH DICTIONARY_PATH \\");
		System.err.println("\tHAS_HEADERS OUTPUT_COLUMN_NAMES_CSV \\");
		System.err.println("\tSKIP_INCOMPLETE_ROWS APPEND_ORIGINAL_ROWNUM");
		
		
	}
	
	// +---------+
	// | Members |
	// +---------+

	private Config cfg;

	private final static int REPORT_INTERVAL = 1000;
	private final static String DEFAULT_FIELD_PREFIX = "field";
	private final static String ROWNUM_FIELD_HEADER = "rownum";

	private final static Logger log = Logger.getLogger(Normalize.class.getName());
}
