
import java.util.HashMap;
import java.io.FileReader;
import java.io.BufferedReader;

public class Csv3
{
	// args[0] = muppet name; args[1] = path to csv
	public static void main(String[] args) throws Exception {
		if (args[0].equals("echo")) {
			echoMain(args);
			return;
		}
		Csv3 csv = new Csv3();
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
		Csv3 csv = new Csv3();
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
		return(fields[ifield]);
	}
	
	public String getField(String header) throws Exception {
		return(fields[headerPositions.get(header)]);
	}

	public String[] getFields() {
		return(fields);
	}

	public String[] getHeaders() {
		return(headers);
	}
	
	public void process(String inputPath, CsvCallback callback) throws Exception {

		FileReader fileReader = null;
		BufferedReader bufferedReader = null;

		try {
			fileReader = new FileReader(inputPath);
			bufferedReader = new BufferedReader(fileReader);
			
			separator = (inputPath.toLowerCase().endsWith(".tsv") ? '\t' : ',');
			readHeaders(bufferedReader.readLine());
			if (!callback.peekHeaders()) return;

			String line = null;
			while ((line = bufferedReader.readLine()) != null) {
				if (!parseFields(line.trim())) continue;
				if (!callback.handleLine()) return;
			}
		}
		finally {
			if (bufferedReader != null) bufferedReader.close();
			if (fileReader != null) fileReader.close();
		}
	}

	private boolean parseFields(String line) {

		if (line.isEmpty()) return(false);
		
		if (fields == null) fields = new String[headers.length];
		for (int i = 0; i < fields.length; ++i) fields[i] = "";

		int ich = 0;
		int ifield = 0;
		int cch = line.length();
		StringBuilder sb = new StringBuilder();

		while (ich < cch && ifield < fields.length) {
			// here we are always at the start of a field; skip leading whitespace
			sb.setLength(0);
			while (ich < cch && Character.isWhitespace(line.charAt(ich))) ++ ich;

			if (ich < cch) {
				// check for quoting
				boolean quoting = false;
				if (line.charAt(ich) == QUOTE_CHAR) {
					quoting = true;
					++ich;
				}

				// collect chars until end of field
				while (ich < cch) {
					char ch = line.charAt(ich);
					
					if (quoting && ch == QUOTE_CHAR) {
						++ich;
						if (ich < cch && line.charAt(ich) == QUOTE_CHAR) {
							// escaped quotation; accumulate it
							sb.append(QUOTE_CHAR);
							++ich;
						}
						else {
							// end of quoted field; find separator
							while (ich < cch && line.charAt(ich) != separator) ++ich;
							break;
						}
					}
					else if (!quoting && ch == separator) {
						// end of non-quoted field; eat back whitespace
						while (sb.length() > 0 && Character.isWhitespace(sb.charAt(sb.length() - 1))) {
							sb.setLength(sb.length() - 1);
						}
						break;
					}
					else {
						// normal character, accumulate and move on
						sb.append(ch);
						++ich;
					}
				}

				// bounce over the separator
				if (ich < cch && line.charAt(ich) == separator) ++ich;
			}

			fields[ifield++] = sb.toString();
		}

		return(true);
	}

	private void readHeaders(String headerLine) {
		// this assumes headers aren't quoted
		headers = headerLine.split(Character.toString(separator));
		headerPositions = new HashMap<String,Integer>();
		for (int i = 0; i < headers.length; ++i) {
			headers[i] = headers[i].trim();
			headerPositions.put(headers[i], i);
		}
	}

	private String[] fields;
	private String[] headers;
	private HashMap<String,Integer> headerPositions;
	private char separator;

	private static char QUOTE_CHAR = '"';
}
