
import java.io.FileReader;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

public class Csv4
{
	// args[0] = muppet name; args[1] = path to csv
	public static void main(String[] args) throws Exception {
		if (args[0].equals("echo")) {
			echoMain(args);
			return;
		}
		Csv4 csv = new Csv4();
		csv.process(args[1], new CsvCallback() {
			public boolean handleLine() throws Exception {
				if (csv.getField("Muppet").equals(args[0])) {
					System.out.println(csv.getField("Year Introduced"));
					return(false);
				}
				return(true);
			}
		});
	}

	// args[0] = "echo", args[1] = path to csv
	public static void echoMain(String[] args) throws Exception {
		Csv4 csv = new Csv4();
		csv.process(args[1], new CsvCallback() {
			public boolean handleLine() throws Exception {
				return(printArray(csv.getFields()));
			}
			public boolean peekHeaders() throws Exception {
				return(printArray(csv.getHeaders()));
			}
			private boolean printArray(String[] items) {
				for (int i = 0; i < items.length; ++i) {
					System.out.println(String.format("%d: %s", i, items[i]));
				}
				System.out.println("");
				return(true);
			}
		});
	}

	public interface CsvCallback {
		public boolean handleLine() throws Exception;
		default public boolean peekHeaders() throws Exception { return(true); }
	}

	public String getField(int ifield) throws Exception {
		return(record.get(ifield));
	}
	
	public String getField(String header) throws Exception {
		return(record.get(header));
	}

	public String[] getFields() {
		String[] fields = new String[record.size()];
		Iterator<String> iter = record.iterator();
		for (int i = 0; i < fields.length; ++i) fields[i] = iter.next();
		return(fields);
	}

	public String[] getHeaders() {
		List<String> headers = parser.getHeaderNames();
		return(headers.toArray(new String[headers.size()]));
	}
	
	public void process(String inputPath, CsvCallback callback) throws Exception {

		FileReader reader = null;
		parser = null;
		record = null;
		
		try {
			boolean tsv = inputPath.toLowerCase().endsWith(".tsv");
			CSVFormat format = (tsv ? CSVFormat.TDF : CSVFormat.DEFAULT);

			format = format
				.withFirstRecordAsHeader()
				.withIgnoreEmptyLines()
				.withIgnoreHeaderCase()
				.withIgnoreSurroundingSpaces()
				.withTrim();

			reader = new FileReader(inputPath);
			parser = CSVParser.parse(reader, format);
			if (!callback.peekHeaders()) return;

			Iterator<CSVRecord> iter = parser.iterator();
			while (iter.hasNext()) {
				record = iter.next();
				if (!callback.handleLine()) return;
			}
		}
		finally {
			if (parser != null) parser.close();
			if (reader != null) reader.close();
		}
	}

	private CSVParser parser;
	private CSVRecord record;
}
