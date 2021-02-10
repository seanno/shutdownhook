
import java.util.HashMap;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Csv2b
{
	// args[0] = muppet name; args[1] = path to csv
	public static void main(String[] args) throws Exception {
		Csv2b csv = new Csv2b();
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

	public interface CsvCallback {
		public boolean handleLine() throws Exception;
	}

	public String getField(int ifield) throws Exception {
		return(fields[ifield]);
	}
	
	public String getField(String header) throws Exception {
		return(fields[headerPositions.get(header)]);
	}
	
	public void process(String inputPath, CsvCallback callback) throws Exception {

		separator = (inputPath.toLowerCase().endsWith(".tsv") ? "\t" : ",");
		List<String> lines = Files.readAllLines(Paths.get(inputPath));
		readHeaders(lines.get(0));

		for (int iline = 1; iline < lines.size(); ++iline) {

			String line = lines.get(iline).trim();
			if (line.isEmpty()) continue;
			
			fields = line.split(separator);
			for (int ifield = 0; ifield < fields.length; ++ifield) {
				fields[ifield] = fields[ifield].trim();
			}

			if (!callback.handleLine()) return;
		}
	}

	private void readHeaders(String headerLine) {
		String[] headers = headerLine.split(separator);
		headerPositions = new HashMap<String,Integer>();
		for (int i = 0; i < headers.length; ++i) {
			headerPositions.put(headers[i].trim(), i);
		}
	}

	private String[] fields;
	private HashMap<String,Integer> headerPositions;
	private String separator;
}
