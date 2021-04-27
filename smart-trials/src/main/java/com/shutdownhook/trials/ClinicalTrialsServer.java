/*
** Read about this code at http://shutdownhook.com.
** No restrictions on use; no assurances or warranties either!
*/

package com.shutdownhook.trials;

import java.io.Closeable;
import java.util.*;
import java.util.logging.Logger;

import com.shutdownhook.toolbox.Easy;;
import com.shutdownhook.toolbox.WebRequests;
import com.shutdownhook.toolbox.WebServer.*;
import com.shutdownhook.smart.SmartEhr;
import com.shutdownhook.smart.SmartServer;
import com.shutdownhook.smart.SmartTypes;

import com.google.gson.Gson;

public class ClinicalTrialsServer extends SmartServer
{
	// +------------------+
	// | Config and Setup |
	// +------------------+
	
	public static class Config
	{
		public SmartServer.Config SmartServer;
		public WebRequests.Config Requests = new WebRequests.Config();

		public static Config fromJson(String json) {
			return(new Gson().fromJson(json, Config.class));
		}
	}

	public ClinicalTrialsServer(Config cfg) throws Exception {
		super(cfg.SmartServer);
		this.cfg = cfg;
		this.trials = new ClinicalTrialsSearch(cfg.Requests);
	}

	@Override
	public void close() {
		super.close();
		trials.close();
	}

    public static void main(String[] args) throws Exception 
    {
		Easy.setSimpleLogFormat();

		String path = (args.length == 0 ? "@config.json" : args[0]);
		Config cfg = Config.fromJson(Easy.stringFromSmartyPath(path));

		ClinicalTrialsServer server = new ClinicalTrialsServer(cfg);
		server.runSync();
    }

	// +----------+
	// | Handlers |
	// +----------+

	@Override
	protected void registerAdditionalHandlers(SmartEhr smart) {
		// nyi
	}
	
	@Override
	public void home(Request request, Response response,
					 SmartEhr.Session session, SmartEhr smart) throws Exception {

		StringBuilder sb = new StringBuilder();
		sb.append("\nUser: " + session.UserId);
		sb.append("\nPatient: " + session.PatientId);
		sb.append("\nToken expires: " + session.AccessExpires.toString());

		SmartTypes.Patient patient = smart.getPatient(session);
		sb.append("\n\nGender: " + patient.gender.toString());
		sb.append("\nBirthDate: " + patient.birthDate.toString());

		sb.append("\n\nConditions: ");

		List<SmartTypes.Condition> conditions = smart.getConditions(session);

		if (conditions.size() == 0) {
			sb.append("(none)");
		}
		else {
			int added = 0;
			for (SmartTypes.Condition c : conditions) {
				if (added++ > 0) sb.append(", ");
				sb.append(c.code.text);
				if (!c.validAndActive()) sb.append(" (!active)");
			}
		}
		
		response.setText(sb.toString());
	}

	// +--------------------+
	// | Fields and Helpers |
	// +--------------------+

	private Config cfg;
	private ClinicalTrialsSearch trials;
	
	private final static Logger log =
		Logger.getLogger(ClinicalTrialsServer.class.getName());
}
