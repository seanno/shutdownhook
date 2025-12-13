//
// PROCESSRESOURCE.JAVA
//

// If process exits with non-zero return value, it's assumed to have globally
// failed and is reported as such. Otherwise results are parsed for lines of 
// the form:
//
//    [STATUS]^LEVEL^METRIC^RESULT
//
// i.e., caret-separated fields; first one is hardcoded to [STATUS] and the
// rest represent fields in the Status structure. METRIC and RESULT
// can be blank. If LEVEL is blank, ERROR is assumed. No newlines or carets
// are allowed!
//
// If the exit code is 0 but no [STATUS] lines are found in the output,
// simple success will be assumed.
//
// NOTE: Because I'm lazy, this loads the full response into memory. The system
// allows for non-status lines in the output, but there shouldn't be megs and
// megs of them.

package com.shutdownhook.backstop;

import java.util.List;

import com.shutdownhook.toolbox.Easy;

import com.shutdownhook.backstop.Resource.Checker;
import com.shutdownhook.backstop.Resource.Config;
import com.shutdownhook.backstop.Resource.Status;
import com.shutdownhook.backstop.Resource.StatusLevel;

public class ProcessResource implements Checker
{
	public void check(Config cfg, List<Status> statuses) throws Exception {

		String cmd = cfg.Parameters.get("Command");
		if (Easy.nullOrEmpty(cmd)) throw new Exception("ProcessResource requires Command attribute");
		
		String[] commands = new String[] { "bash", "-c", cmd};
		ProcessBuilder pb = new ProcessBuilder(commands);
		if (cfg.Parameters != null) pb.environment().putAll(cfg.Parameters);
		
		Process p = pb.start();
		String results = Easy.stringFromInputStream(p.getInputStream());
		int exitCode = p.waitFor();

		String debugProcess = cfg.Parameters.get("Debug");
		if (!Easy.nullOrEmpty(debugProcess) && debugProcess.equalsIgnoreCase("true")) {
			System.out.println(String.format("-----\nProcess exit: %d\n%s\n-----", exitCode, results));
		}
			
		if (exitCode != 0) {
			Status status = new Status(cfg);
			status.Level = StatusLevel.ERROR;
			status.Result = "Non-Zero exit from process\n" + results;
			statuses.add(status);
			return;
		}

		for (String line : results.split("\n")) {
			
			if (!line.startsWith("[STATUS]^")) continue;
			
			String[] fields = line.trim().split("\\^");
			if (fields.length < 2) continue;

			Status s = new Status(cfg);
			s.Level = parseLevel(fields[1].trim());

			if (fields.length > 2) s.Metric = fields[2];
			if (fields.length > 3) s.Result = fields[3];

			statuses.add(s);
		}
	}

	private StatusLevel parseLevel(String input) {
		try { return(StatusLevel.valueOf(input)); }
		catch (Exception e) { return(StatusLevel.ERROR); }
	}
}
