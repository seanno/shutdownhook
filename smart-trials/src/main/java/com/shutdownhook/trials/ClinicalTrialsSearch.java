/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.trials;

import java.io.Closeable;
import java.util.*;
import java.util.logging.Logger;

import com.shutdownhook.toolbox.WebRequests;
import com.shutdownhook.trials.ClinicalTrialsTypes.*;

public class ClinicalTrialsSearch implements Closeable
{
	// Service details at https://clinicaltrials.gov/ct2/home		
	private final static String BASE_QUERY_URL =
		"https://clinicaltrials.gov/ct2/results/download_fields?" +
		"down_count=50&down_flds=all&down_fmt=tsv";

	// +--------------------+
	// | Query and Response |
	// +--------------------+

	public static class Query
	{
		public List<String> Conditions = new ArrayList<String>();
		public Integer AgeYears;
		public LegacyGender Gender = LegacyGender.U;
		public String Country;
		public StateProvince StateProvince;

		public void setGender(String input) {
			LegacyGender lg = LegacyGender.find(input);
			if (lg != null) Gender = lg;
		}
		
		public void setCountry(String input) {
			Country = null;
			String cleanInput = (input != null && input.length() > 2 ? input.substring(0,2) : input);
			for (Locale loc : Locale.getAvailableLocales()) {
				String locCountry = loc.getCountry();
				if (locCountry.length() == 0) continue;
				if (locCountry.length() > 2) locCountry = locCountry.substring(0,2);
				
				if (cleanInput.equalsIgnoreCase(locCountry) ||
					cleanInput.equalsIgnoreCase(loc.getDisplayCountry())) {
					Country = locCountry;
					return;
				}
			}
		}

		public void setStateProvince(String input) {
			StateProvince = StateProvince.find(input);
		}

	}

	public static class Trial
	{
		public String NCTNumber;
		public String Title;
		public String Acronym;
		public String Status;
		public String[] Conditions;
		public String[] Interventions;
		public String[] Locations;
		public String Url;
	}

	// +----------------------+
	// | ClinicalTrialsSearch |
	// +----------------------+

	public ClinicalTrialsSearch(WebRequests.Config wrConfig) throws Exception {
		requests = new WebRequests(wrConfig);
	}

	public void close() {
		requests.close();
	}

	public List<Trial> search(Query query) throws Exception {

		WebRequests.Response response = requests.fetch(BASE_QUERY_URL,
													   makeQueryParams(query));
		
		if (!response.successful()) {

			if (response.Status == 404) {
				// this is actually fine ... just means no results
				return(new ArrayList<Trial>()); 
			}
			
			String msg = String.format("Error calling clinicaltrials.gov (%d: %s)",
									   response.Status, response.StatusText);
			
			throw new Exception(msg, response.Ex);
		}
		
		return(parseQueryResponse(response.Body));
	}

	private List<Trial> parseQueryResponse(String tsv) {
		
		List<Trial> trials = new ArrayList<Trial>();

		String[] lines = tsv.split("\n");
		Map<String,Integer> headers = readHeaders(lines[0].split("\t"));

		for (int i = 1; i < lines.length; ++i) {
			
			Trial trial = new Trial();
			trials.add(trial);

			String[] fields = lines[i].split("\t");
			trial.NCTNumber = fields[headers.get("NCT Number")];
			trial.Title = fields[headers.get("Title")];
			trial.Acronym = fields[headers.get("Acronym")];
			trial.Status = fields[headers.get("Status")];
			trial.Conditions = parsePsv(fields[headers.get("Conditions")]);
			trial.Interventions = parsePsv(fields[headers.get("Interventions")]);
			trial.Locations = parsePsv(fields[headers.get("Locations")]);
			trial.Url = fields[headers.get("URL")];
		}
		
		return(trials);
	}

	private String[] parsePsv(String input) {
		return(input.trim().isEmpty() ? null : input.split("\\|"));
	}

	private Map<String,Integer> readHeaders(String[] headers) {
		Map<String,Integer> headerPositions = new HashMap<String,Integer>();
		for (int i = 0; i < headers.length; ++i) headerPositions.put(headers[i].trim(), i);
		return(headerPositions);
	}
	
	private WebRequests.Params makeQueryParams(Query query) {

		WebRequests.Params params = new WebRequests.Params();

		// hardcodes Recruiting / Not Yet Recruiting / Enrolling by Invitation
		params.addQueryParam("recrs", "abf"); 

		if (query.Conditions.size() > 0) {
			StringBuilder sb = new StringBuilder();
			for (String condition : query.Conditions) {
				if (sb.length() > 0) sb.append(",");
				sb.append(condition);
			}
			params.addQueryParam("cond", sb.toString());
		}

		if (query.AgeYears != null) {
			params.addQueryParam("age_v", query.AgeYears.toString());
		}
		
		if (query.Gender != null) {
			if (query.Gender.equals(LegacyGender.M)) params.addQueryParam("gndr", "Male");
			if (query.Gender.equals(LegacyGender.F)) params.addQueryParam("gndr", "Female");
		}

		if (query.Country != null) {
			params.addQueryParam("cntry", query.Country);
			if (query.StateProvince != null &&
				(query.Country.equals("US") || query.Country.equals("CA"))) {
				
				params.addQueryParam("state",
									 query.Country + ":" + query.StateProvince.toString());
			}					
		}

		return(params);
	}

	public static void main(String[] args) throws Exception {

		if (args.length == 0) {
			System.out.println("Usage: java ClinicalTrialsSearch OPTION [OPTION] ...");
			System.out.println("       Options are NAME=VALUE");
			System.out.println("       NAME = condition,age,gender,country,state");
			return;
		}
		
		Query query = new Query();
		for (int i = 0; i < args.length; ++i) {
			String[] nv = args[i].split("=");
			switch (nv[0]) {
			    case "condition": query.Conditions.add(nv[1]); break;
			    case "age": query.AgeYears = Integer.parseInt(nv[1]); break;
			    case "gender": query.setGender(nv[1]); break;
			    case "country": query.setCountry(nv[1]); break;
			    case "state": query.setStateProvince(nv[1]); break;
			}
		}

		WebRequests.Config cfg = new WebRequests.Config();
		ClinicalTrialsSearch search = null;

		try {
			search = new ClinicalTrialsSearch(cfg);
			List<Trial> trials = search.search(query);

			for (Trial trial : trials) {
				System.out.println(trial.Url);
				System.out.println(trial.Title);
				System.out.println(trial.Conditions[0]);
				System.out.println("");
			}
		}
		finally {
			search.close();
		}
	}

	private WebRequests requests;
	
	private final static Logger log =
		Logger.getLogger(ClinicalTrialsSearch.class.getName());
}
