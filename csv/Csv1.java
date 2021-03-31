/*
** Read about this code at http://shutdownhook.com.
** No restrictions on use; no assurances or warranties either!
*/


import java.nio.file.Files;
import java.nio.file.Paths;

public class Csv1
{
	// args[0] = muppet name; args[1] = path to csv
	public static void main(String[] args) throws Exception {
		boolean first = true;
		for (String line : Files.readAllLines(Paths.get(args[1]))) {
			if (first) { first = false; continue; }
			String[] fields = line.split(",");
			if (fields[1].equals(args[0])) {
				System.out.println(fields[0]);
				return;
			}
		}
	}
}
