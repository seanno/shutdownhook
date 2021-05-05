/*
** Read about this code at http://shutdownhook.com.
** No restrictions on use; no assurances or warranties either!
*/

package com.shutdownhook.trials;

import java.io.Closeable;
import java.time.Instant;
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
import com.shutdownhook.smart.SmartTypes.ConditionCategoryCode;
import com.shutdownhook.trials.ClinicalTrialsTypes.*;

import com.google.gson.Gson;

public class ClinicalTrialsServer extends SmartServer
{
	private final static String SEARCH_PATH = "/search";
	private final static String PATIENT_PATH = "/patient";
	private final static String HOME_HTML = "trials.html";
	
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
		this.homeHtml = Easy.stringFromResource(HOME_HTML);
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

				PatientInfo info = new PatientInfo();
				info.NeedPatientBanner = session.NeedPatientBanner;
				info.Type = session.Config.Type.toString();

				SmartTypes.HumanName name = patient.bestName();
				info.PatientName = (name == null ? "unknown" : name.displayName());

				addConditions(info.Query, conditions);

				if (patient.birthDate != null) {
					info.Query.AgeYears =
						(int) ChronoUnit.YEARS.between(patient.birthDate,
													   LocalDate.now());
				}

				if (patient.gender != null) {
					info.Query.setGender(patient.gender.toString());
				}

				SmartTypes.Address address = patient.bestAddress();
				if (address != null) {

					info.Query.setCountry(address.country == null
										  ? "US" : address.country);

					if (address.state != null &&
						(info.Query.Country.equals("US") || info.Query.Country.equals("CA"))) {
						if (address.state != null) info.Query.setStateProvince(address.state);
					}
				}

				response.setJson(new Gson().toJson(info));
			}
		});
	}

	private void addConditions(ClinicalTrialsSearch.Query query,
							   List<SmartTypes.Condition> conditions) {

		// sort descending by our best guess at onset
		Collections.sort(conditions, new Comparator<SmartTypes.Condition>() {
			@Override
			public int compare(SmartTypes.Condition c1, SmartTypes.Condition c2) {
				if (c1 == null && c2 == null) return(0);
				if (c1 == null && c2 != null) return(-1);
				if (c1 != null && c2 == null) return(1);
					
				LocalDate ld1 = c1.bestGuessOnset();
				LocalDate ld2 = c2.bestGuessOnset();

				if (ld1 == null && ld2 == null) return(0);
				if (ld1 == null && ld2 != null) return(-1);
				if (ld1 != null && ld2 == null) return(1);

				return(ld2.compareTo(ld1));
			}
		});

		// we try to add just "problem list" conditions. If that doesn't work
		// we'll take any that are valid and active. We stop there, but might
		// want to extend to valid but not active if we still come up empty.
		
		List<String> uniqueProblems = new ArrayList<String>();
		List<String> uniqueActive = new ArrayList<String>();
		
		for (SmartTypes.Condition condition : conditions) {
			if (condition.validAndActive()) {

				String clean = cleanCondition(condition.code.text);
				if (!uniqueActive.contains(clean)) uniqueActive.add(clean);
									 
				SmartTypes.ConditionCategoryCodes cats = condition.category;
				if (cats != null) {
					for (ConditionCategoryCode cat : cats) {
						if (cat == ConditionCategoryCode.problem ||
							cat == ConditionCategoryCode.problem_list_item) {

							if (!uniqueProblems.contains(clean)) uniqueProblems.add(clean);
						}
					}
				}
			}
		}

		List<String> uniqueFinal =
			(uniqueProblems.size() > 0 ? uniqueProblems : uniqueActive);
		
		for (String c : uniqueFinal) {
			query.Conditions.add(c);
		}
	}

	@Override
	public void home(Request request, Response response,
					 SmartEhr.Session session, SmartEhr smart) throws Exception {

		response.setHtml(homeHtml);
	}

	// +--------------------+
	// | Fields and Helpers |
	// +--------------------+

	private String cleanCondition(String input) {
		// hacky rules to try to make these better for searching,
		// purely based on inspection ... nothing pretty in here.
		String clean = input.replace(",", ";");
		clean = clean.replace(" (disorder)", "");
		clean = clean.replace("; initial encounter", "");
		clean = clean.replace("; subsequent encounter", "");
		return(clean);
	}
	
	public static class PatientInfo
	{
		public String PatientName;
		public Boolean NeedPatientBanner;
		public String Type;
		public ClinicalTrialsSearch.Query Query = new ClinicalTrialsSearch.Query();
	}
		
	private Config cfg;
	private ClinicalTrialsSearch trials;
	private String homeHtml;
	
	private final static Logger log =
		Logger.getLogger(ClinicalTrialsServer.class.getName());
}
