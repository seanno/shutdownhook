/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.lorawan2;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.SqlStore;
import com.shutdownhook.toolbox.Template;

public class WitterWater {

	// +-------------------------+
	// | Function: Witter-Update |
	// +-------------------------+
	
    @FunctionName("Witter-Update")
    public HttpResponseMessage update(
								   
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.POST},
                authLevel = AuthorizationLevel.ANONYMOUS)
                HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) throws Exception {
		
        context.getLogger().info("Request: WITTER-UPDATE");

		// verify auth
		
		String auth = request.getHeaders().get("authorization");
		
		String expected = "Basic " + Easy.base64Encode(getWitterHookUser() + ":" +
													   getWitterHookPassword());

		if (!expected.equals(auth)) {
			context.getLogger().warning("Invalid auth: " + auth);
            return(request.createResponseBuilder(HttpStatus.FORBIDDEN).build());
		}
		
        // read water level

		JsonObject msg = new JsonParser().parse(request.getBody().get()).getAsJsonObject();
				
		double waterLevel =
			msg.get("uplink_message").getAsJsonObject()
			.get("decoded_payload").getAsJsonObject()
			.get("water_level").getAsDouble();

		Instant timestamp = Instant.parse(msg.get("received_at").getAsString());
		long epochSecond = timestamp.getEpochSecond();

		context.getLogger().info(String.format("Received level %02f at %s", waterLevel, timestamp));

        // store it

		try {
			getStore().saveMetric(WITTER_METRIC, waterLevel, epochSecond);
			return(request.createResponseBuilder(HttpStatus.OK).build());
		}
		catch (Exception e) {
			String txt = Easy.exMsg(e, "witterhook", true);
			context.getLogger().warning(txt);
			
			return(request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
				   .body(returnExceptionText() ? txt : "")
				   .build());
		}
    }

	// +-----------------------+
	// | Function: Witter-Data |
	// +-----------------------+

    @FunctionName("Witter-Data")
    public HttpResponseMessage data(
								   
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.GET},
                authLevel = AuthorizationLevel.ANONYMOUS)
                HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) throws Exception {
		
        context.getLogger().info("Request: WITTER-DATA");

		String daysStr = request.getQueryParameters().get("days");
		int days = (daysStr == null ? DAYS_DEFAULT : Integer.parseInt(daysStr));

		String zoneStr = request.getQueryParameters().get("tz");
		ZoneId zone = ZoneId.of(zoneStr == null ? TZ_DEFAULT : zoneStr);

		Instant start, end;

		String startStr = request.getQueryParameters().get("start");
		if (Easy.nullOrEmpty(startStr)) {
			start = Instant.now().minus(days, ChronoUnit.DAYS);
			end = null;
		}
		else {
			start = LocalDate.parse(startStr).atStartOfDay().atZone(zone).toInstant();
			end = start.plus(days, ChronoUnit.DAYS);
		}
		
		List<MetricStore.Metric> metrics = getStore().getMetrics(WITTER_METRIC, start,
																 end, null, zone);

		return(request.createResponseBuilder(HttpStatus.OK)
			   .body(new Gson().toJson(metrics))
			   .build());
    }

	// +------------------------+
	// | Function: Witter-Check |
	// +------------------------+

    @FunctionName("Witter-Check")
    public HttpResponseMessage check(
								   
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.GET},
                authLevel = AuthorizationLevel.ANONYMOUS)
                HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) throws Exception {
		
        context.getLogger().info("Request: WITTER-CHECK");

		String cmStr = request.getQueryParameters().get("cm");
		int cm = (cmStr == null ? CHECK_CM_DEFAULT : Integer.parseInt(cmStr));

		String zoneStr = request.getQueryParameters().get("tz");
		ZoneId zone = ZoneId.of(zoneStr == null ? TZ_DEFAULT : zoneStr);

		MetricStore.Metric metric =	getStore().getLatestMetric(WITTER_METRIC, zone);

		String body = String.format("%s (%.3f)",
									metric.Value >= (double) cm ? "OK" : "LOW",
									metric.Value);

		return(request.createResponseBuilder(HttpStatus.OK)
			   .body(body)
			   .build());
    }

	// +------------------------+
	// | Function: Witter-Graph |
	// +------------------------+

	private final static String GRAPH_TEMPLATE = "witterGraph.html.tmpl";
	private final static String TKN_DATA_URL = "DATA_URL";
	private final static String TKN_GRAPH_URL = "GRAPH_URL";
	private final static String TKN_DAYS = "DAYS";
	private final static String TKN_START = "START";

    @FunctionName("Witter-Graph")
    public HttpResponseMessage graph(
								   
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.GET},
                authLevel = AuthorizationLevel.ANONYMOUS)
                HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) throws Exception {
		
        context.getLogger().info("Request: WITTER-GRAPH");
		
		String daysStr = request.getQueryParameters().get("days");
		int days = (daysStr == null ? DAYS_DEFAULT : Integer.parseInt(daysStr));

		String start = request.getQueryParameters().get("start");
		if (Easy.nullOrEmpty(start)) start = "";

		URI thisURI = request.getUri();
		context.getLogger().info("URI is: " + thisURI.toString());
		
		HashMap tokens = new HashMap<String,String>();
		tokens.put(TKN_DATA_URL, thisURI.resolve("witter-data").toString());
		tokens.put(TKN_GRAPH_URL, thisURI.resolve("witter-graph").toString());
		tokens.put(TKN_DAYS, Integer.toString(days));
		tokens.put(TKN_START, start);
				
		return(request.createResponseBuilder(HttpStatus.OK)
			   .header("Content-Type", "text/html")
			   .body(getGraphTemplate().render(tokens))
			   .build());
    }

	private synchronized Template getGraphTemplate() throws Exception {

		if (graphTemplate == null) {
			String templateText = Easy.stringFromResource(GRAPH_TEMPLATE);
			graphTemplate = new Template(templateText);
		}

		return(graphTemplate);
	}

	// +---------+
	// | Helpers |
	// +---------+

	private synchronized MetricStore getStore() throws Exception {
		
		if (store == null) {
			SqlStore.Config cfg = new SqlStore.Config(getWitterConnectionString());
			store = new MetricStore(cfg);
		}

		return(store);
	}

	private static String getSetting(String name) {
		return(System.getenv(name));
	}

	private static String getWitterConnectionString() {
		return(getSetting("WITTER_CONNECTION_STRING"));
	}

	private static String getWitterHookUser() {
		return(getSetting("WITTER_HOOK_USER"));
	}

	private static String getWitterHookPassword() {
		return(getSetting("WITTER_HOOK_PASSWORD"));
	}

	private static boolean returnExceptionText() {
		String ret = getSetting("RETURN_EXCEPTION_TEXT");
		if (Easy.nullOrEmpty(ret)) return(false);
		return(ret.toLowerCase().equals("true"));
	}
	
	// +---------+
	// | Members |
	// +---------+

	private static String WITTER_METRIC = "WitterTankLevel";
	private static String TZ_DEFAULT = "PST8PDT";
	private static int DAYS_DEFAULT = 7;
	private static int CHECK_CM_DEFAULT = 100;
	
	private static MetricStore store = null;
	private static Template graphTemplate = null;
}
