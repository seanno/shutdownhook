/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.lorawan2;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
import com.shutdownhook.toolbox.Template;
import com.shutdownhook.lorawan2.Helpers.QueryParams;

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
		
		String expected = "Basic " + Easy.base64Encode(Helpers.getWitterHookUser() + ":" +
													   Helpers.getWitterHookPassword());

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
			Helpers.getWitterStore().saveMetric(waterLevel, epochSecond);
			return(request.createResponseBuilder(HttpStatus.OK).build());
		}
		catch (Exception e) {
			String txt = Easy.exMsg(e, "witterhook", true);
			context.getLogger().warning(txt);
			
			return(request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
				   .body(Helpers.returnExceptionText() ? txt : "")
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

		QueryParams qp = Helpers.parseQueryParams(request);
		
		List<MetricStore.Metric> metrics =
			Helpers.getWitterStore().getMetrics(qp.Start, qp.End, null, qp.Zone);

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

		QueryParams qp = Helpers.parseQueryParams(request);

		MetricStore.Metric metric = Helpers.getWitterStore().getLatestMetric(qp.Zone);

		String body = String.format("%s (%.3f)",
									metric.Value >= (double) qp.Cm ? "OK" : "LOW",
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

		QueryParams qp = Helpers.parseQueryParams(request);

		String startTkn = request.getQueryParameters().get("start");
		if (Easy.nullOrEmpty(startTkn)) startTkn = "";
		
		URI thisURI = request.getUri();
		context.getLogger().info("URI is: " + thisURI.toString());
		
		HashMap tokens = new HashMap<String,String>();
		tokens.put(TKN_DATA_URL, thisURI.resolve("witter-data").toString());
		tokens.put(TKN_GRAPH_URL, thisURI.resolve("witter-graph").toString());
		tokens.put(TKN_DAYS, Integer.toString(qp.Days));
		tokens.put(TKN_START, startTkn);
				
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
	// | Members |
	// +---------+

	
	private static Template graphTemplate = null;
}
