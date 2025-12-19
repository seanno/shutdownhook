//
// PROCESSRESOURCE.JAVA
//

// If process exits with non-zero return value, it's assumed to have globally
// failed and is reported as such. Otherwise results are parsed for lines of 
// the form:
//
//    [STATUS]LEVEL^RESULT^METRIC^LINK
//
// i.e., caret-separated fields; first one is hardcoded to [STATUS] and the
// rest represent fields in the Status structure. RESULT, METRIC and
// LINK can be blank. No newlines or carets are allowed!
//
// If the exit code is 0 but no [STATUS] lines are found in the output,
// simple success will be assumed.
//
// NOTE: Because I'm lazy, this loads the full response into memory. The system
// allows for non-status lines in the output, but there shouldn't be megs and
// megs of them.

package com.shutdownhook.backstop;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.shutdownhook.toolbox.Easy;

import com.shutdownhook.backstop.Resource.Checker;
import com.shutdownhook.backstop.Resource.Config;
import com.shutdownhook.backstop.Resource.Status;
import com.shutdownhook.backstop.Resource.StatusLevel;

public class ProcessResource implements Checker
{
	private final static int DEFAULT_TIMEOUT = (2 * 60); // 2 minutes in seconds
	
	public void check(Map<String,String> params,
					  BackstopHelpers helpers,
					  List<Status> statuses) throws Exception {

		String cmd = params.get("Command");
		if (Easy.nullOrEmpty(cmd)) throw new Exception("ProcessResource requires Command attribute");

		String timeoutStr = params.get("TimeoutSeconds");
		int timeout = (Easy.nullOrEmpty(timeoutStr) ? DEFAULT_TIMEOUT : Integer.parseInt(timeoutStr));
						  
		String[] commands = new String[] { "bash", "-c", cmd};
		ProcessBuilder pb = new ProcessBuilder(commands);
		pb.environment().putAll(params);
		
		Process p = pb.start();
		if (!p.waitFor(timeout, TimeUnit.SECONDS)) {
			statuses.add(new Status("", StatusLevel.ERROR,
									"Process did not finish within timeout"));
			return;
		}
		
		int exitCode = p.exitValue();
		String results = Easy.stringFromInputStream(p.getInputStream());

		String debugProcess = params.get("Debug");
		if (!Easy.nullOrEmpty(debugProcess) && debugProcess.equalsIgnoreCase("true")) {
			System.out.println(String.format("-----\nProcess exit: %d\n%s\n-----", exitCode, results));
		}
			
		if (exitCode != 0) {
			statuses.add(new Status("", StatusLevel.ERROR,
									"Non-Zero exit from process\n" + results));
			return;
		}

		for (String line : results.split("\n")) {
			
			if (!line.startsWith("[STATUS]^")) continue;
			
			String[] fields = line.trim().split("\\^");
			if (fields.length < 2) continue;

			StatusLevel level = parseLevel(fields[1].trim());
			String result = (fields.length < 3 ? "" : fields[2]);
			String metric = (fields.length < 4 ? "" : fields[3]);
			String link = (fields.length < 5 ? "" : fields[4]);

			statuses.add(new Status(metric, level, result, link));
		}
	}

	private StatusLevel parseLevel(String input) {
		try { return(StatusLevel.valueOf(input)); }
		catch (Exception e) { return(StatusLevel.ERROR); }
	}
}
