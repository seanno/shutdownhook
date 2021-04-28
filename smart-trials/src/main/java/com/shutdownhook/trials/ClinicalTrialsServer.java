/*
** Read about this code at http://shutdownhook.com.
** No restrictions on use; no assurances or warranties either!
*/

package com.shutdownhook.trials;

import java.io.Closeable;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.logging.Logger;

import com.shutdownhook.toolbox.Easy;;
import com.shutdownhook.toolbox.WebRequests;
import com.shutdownhook.toolbox.WebServer;
import com.shutdownhook.toolbox.WebServer.*;
import com.shutdownhook.smart.SmartEhr;
import com.shutdownhook.smart.SmartServer;
import com.shutdownhook.smart.SmartTypes;
import com.shutdownhook.trials.ClinicalTrialsTypes.*;

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

	private final static String SEARCH_PATH = "/search";
	private final static String PATIENT_PATH = "/patient";
	
	@Override
	protected void registerAdditionalHandlers(WebServer server, SmartEhr smart) {
		registerSearchHandler(server, smart);
		registerPatientHandler(server, smart);
	}
	
	public void registerSearchHandler(WebServer server, SmartEhr smart) {
		server.registerHandler(SEARCH_PATH, new SessionHandler(smart) {
				
			public void handle2(Request request, Response response,
							    SmartEhr.Session session) throws Exception {

				// set up parameters
				String conditionsCsv = request.QueryParams.get("conditions");
				String ageYears = request.QueryParams.get("age");
				String gender = request.QueryParams.get("gender");
				String country = request.QueryParams.get("country");
				String state = request.QueryParams.get("state");

				ClinicalTrialsSearch.Query query = new ClinicalTrialsSearch.Query();

				if (conditionsCsv != null) {
					for (String condition : conditionsCsv.split(",")) {
						String trimmed = condition.trim();
						if (!trimmed.isEmpty()) query.Conditions.add(trimmed);
					}
				}

				if (ageYears != null) query.AgeYears = Integer.parseInt(ageYears);
				if (gender != null) query.setGender(gender);
				if (country != null) query.setCountry(country);
				
				if (state != null && (query.Country.equals("US") || query.Country.equals("CA"))) {
					query.setStateProvince(state);
				}

				// make the query and return the json
				List<ClinicalTrialsSearch.Trial> found = trials.search(query);
				response.setJson(new Gson().toJson(found));
			}
		});
	}

	public void registerPatientHandler(WebServer server, SmartEhr smart) {
		server.registerHandler(PATIENT_PATH, new SessionHandler(smart) {
				
			public void handle2(Request request, Response response,
							    SmartEhr.Session session) throws Exception {

				SmartTypes.Patient patient = smart.getPatient(session);
				List<SmartTypes.Condition> conditions = smart.getConditions(session);

				ClinicalTrialsSearch.Query query = new ClinicalTrialsSearch.Query();
				
				for (SmartTypes.Condition condition : conditions) {
					if (condition.validAndActive())
						query.Conditions.add(condition.code.text);
				}

				if (patient.birthDate != null) {
					query.AgeYears = (int) ChronoUnit.YEARS.between(patient.birthDate,
																	LocalDate.now());
				}

				if (patient.gender != null) {
					query.setGender(patient.gender.toString());
				}

				SmartTypes.Address address = patient.bestAddress();
				if (address != null) {

					query.setCountry(address.country == null ? "US" : address.country);

					if (address.state != null &&
						(query.Country.equals("US") || query.Country.equals("CA"))) {
						if (address.state != null) query.setStateProvince(address.state);
					}
				}

				response.setJson(new Gson().toJson(query));
			}
		});
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
