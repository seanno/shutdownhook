/*
** Read about this code at http://shutdownhook.com.
** No restrictions on use; no assurances or warranties either!
*/


import java.nio.file.Files;
import java.nio.file.Paths;

public class Csv2a
{
	// args[0] = muppet name; args[1] = path to csv
	public static void main(String[] args) throws Exception {
		Csv2a csv = new Csv2a();
		csv.process(args[1], new CsvCallback() {
			public boolean handleLine() throws Exception {
				if (csv.getField(1).equals(args[0])) {
					System.out.println(csv.getField(0));
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
	
	public void process(String inputPath, CsvCallback callback) throws Exception {
		boolean first = true;
		for (String line : Files.readAllLines(Paths.get(inputPath))) {
			fields = line.split(",");
			if (first) { first = false; continue; }
			if (!callback.handleLine()) return;
		}
	}

	private String[] fields;
}
