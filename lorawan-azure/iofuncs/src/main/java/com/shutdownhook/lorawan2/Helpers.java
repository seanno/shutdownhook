/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.lorawan2;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.logging.Logger;

import com.microsoft.azure.functions.HttpRequestMessage;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.SqlStore;

public class Helpers {

	// +-------+
	// | Store |
	// +-------+
	
	private static String WITTER_METRIC = "WitterTankLevel";

	private static SingleMetricStore witterStore = null;

	public static synchronized SingleMetricStore getWitterStore() throws Exception {
		
		if (witterStore == null) {
			SqlStore.Config cfg = new SqlStore.Config(getWitterConnectionString());
			witterStore = new SingleMetricStore(WITTER_METRIC, cfg);
		}

		return(witterStore);
	}

	// +----------+
	// | Settings |
	// +----------+

	public static String getSetting(String name) {
		return(System.getenv(name));
	}

	public static String getWitterConnectionString() {
		return(getSetting("WITTER_CONNECTION_STRING"));
	}

	public static String getWitterHookUser() {
		return(getSetting("WITTER_HOOK_USER"));
	}

	public static String getWitterHookPassword() {
		return(getSetting("WITTER_HOOK_PASSWORD"));
	}

	public static boolean returnExceptionText() {
		String ret = getSetting("RETURN_EXCEPTION_TEXT");
		if (Easy.nullOrEmpty(ret)) return(false);
		return(ret.toLowerCase().equals("true"));
	}

	// +-------------+
	// | QueryParams |
	// +-------------+

	private static String TZ_DEFAULT = "PST8PDT";
	private static int DAYS_DEFAULT = 7;
	private static int CHECK_CM_DEFAULT = 100;

	public static class QueryParams
	{
		public int Days;
		public ZoneId Zone;
		public Instant Start;
		public Instant End;
		public int Cm;

		public String toString() {
			return(String.format("Days=%d, Zone=%s, Start=%s, End=%s, Cm=%d",
								 Days, Zone, Start, End, Cm));
		}
	}

	public interface QueryParamProvider {
		public String get(String name);
	}

	public static QueryParams parseQueryParams(QueryParamProvider qpp) {
		
		QueryParams qp = new QueryParams();
		
		String daysStr = qpp.get("days");
		qp.Days = (Easy.nullOrEmpty(daysStr) ? DAYS_DEFAULT : Integer.parseInt(daysStr));

		String zoneStr = qpp.get("tz");
		qp.Zone = ZoneId.of(Easy.nullOrEmpty(zoneStr) ? TZ_DEFAULT : zoneStr);

		String startStr = qpp.get("start");
		if (Easy.nullOrEmpty(startStr)) {
			qp.Start = Instant.now().minus(qp.Days, ChronoUnit.DAYS);
			qp.End = null;
		}
		else {
			qp.Start = LocalDate.parse(startStr).atStartOfDay().atZone(qp.Zone).toInstant();
			qp.End = qp.Start.plus(qp.Days, ChronoUnit.DAYS);
		}

		String cmStr = qpp.get("cm");
		qp.Cm = (cmStr == null ? CHECK_CM_DEFAULT : Integer.parseInt(cmStr));

		log.info("QueryParams: " + qp.toString());
		return(qp);
	}
	
	public static QueryParams parseQueryParams(HttpRequestMessage<Optional<String>> request) {

		return(parseQueryParams(new QueryParamProvider() {
			public String get(String name) {
				return(request.getQueryParameters().get(name));
			}
			
		}));
	}

	public static QueryParams parseQueryParams(JsonObject jsonParams) {

		return(parseQueryParams(new QueryParamProvider() {
			public String get(String name) {
				JsonElement elt = jsonParams.get(name);
				return(elt == null ? null : elt.getAsString());
			}
			
		}));
	}

	private final static Logger log = Logger.getLogger(Helpers.class.getName());
}
