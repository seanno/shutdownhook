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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.JsonRpc2;
import com.shutdownhook.lorawan2.Helpers.QueryParams;

public class WitterMCP {

	// +------------------+
	// | Setup & Teardown |
	// +------------------+

	public WitterMCP() {
		ensureRPC();
	}
	
	private static synchronized void ensureRPC() {

		if (rpc != null) return;
		
		rpc = new JsonRpc2();

		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				if (rpc != null) {
					try { rpc.close(); }
					catch (Exception e) { /* eat it */ }
				}
			}
		});

		registerInitialize();
		registerList();
		registerCall();
	}

	// +----------------------+
	// | Function: Witter-MCP |
	// +----------------------+
	
    @FunctionName("Witter-MCP")
    public HttpResponseMessage mcp(
								   
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.POST, HttpMethod.GET},
                authLevel = AuthorizationLevel.ANONYMOUS)
                HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) throws Exception {
		
        context.getLogger().info("Request: WITTER-MCP");

		// we don't support streaming, sorry
		if (request.getHttpMethod() == HttpMethod.GET) {
			return(request.createResponseBuilder(HttpStatus.METHOD_NOT_ALLOWED).build());
		}

		// but we do answer requests
		String requestJson = request.getBody().get();
		context.getLogger().info(requestJson);
		
		String responseJson = rpc.executeJsonAsync(requestJson).get();

		if (Easy.nullOrEmpty(responseJson)) {
			return(request.createResponseBuilder(HttpStatus.ACCEPTED).build());
		}
		
		return(request.createResponseBuilder(HttpStatus.OK)
			   .header("Content-Type", "application/json")
			   .body(responseJson)
			   .build());
	}

	// +------------+
	// | initialize |
	// +------------+

	private static String INIT_METHOD = "initialize";
	private static String[] INIT_PARAMS = { "protocolVersion", "capabilities", "clientInfo", "_meta" };
	private static String INIT_RESULT_RES = "mcp-init-result.json";

	static void registerInitialize() {

		rpc.registerMethod(INIT_METHOD, INIT_PARAMS, new JsonRpc2.Method() {
			public JsonObject execute(JsonObject params, JsonObject request) throws Exception {
				
				String resultStr = Easy.stringFromResource(INIT_RESULT_RES);
				JsonObject result = JsonParser.parseString(resultStr).getAsJsonObject();

				// we are backwards compatible from maxPV.
				// (note the format sorts alpha; thank you for that at least)
				String maxPV = result.get("protocolVersion").getAsString();
				JsonElement clientPVElt = params.get("protocolVersion");
				String clientPV = (clientPVElt == null ? maxPV : clientPVElt.getAsString());
				String serverPV = (clientPV.compareTo(maxPV) < 0 ? clientPV : maxPV);
				result.addProperty("protocolVersion", serverPV);

				return(result);
			}
		});
	}
	
	// +------------+
	// | tools/list |
	// +------------+

	private static String LIST_METHOD = "tools/list";
	private static String[] LIST_PARAMS = { "cursor", "_meta" }; // na for us
	private static String LIST_RESULT_RES = "mcp-list-result.json";
	
	static void registerList() {

		rpc.registerMethod(LIST_METHOD, LIST_PARAMS, new JsonRpc2.Method() {
			public JsonObject execute(JsonObject params, JsonObject request) throws Exception {
				String result = Easy.stringFromResource(LIST_RESULT_RES);
				return(JsonParser.parseString(result).getAsJsonObject());
			}
		});
	}

	// +------------+
	// | tools/call |
	// +------------+

	private static String CALL_METHOD = "tools/call";
	private static String[] CALL_PARAMS = { "name", "arguments", "_meta" };
	
	static void registerCall() {

		rpc.registerMethod(CALL_METHOD, CALL_PARAMS, new JsonRpc2.Method() {
			public JsonObject execute(JsonObject params, JsonObject request) throws Exception {

				// we should validate params.name but we only have one tool so YOLO
				QueryParams qp = Helpers.parseQueryParams(params.get("arguments").getAsJsonObject());

				JsonObject result = new JsonObject();
				JsonArray content = new JsonArray();
				result.add("content", content);

				List<MetricStore.Metric> metrics = null;
				
				try {
					metrics = Helpers.getWitterStore().getMetrics(qp.Start, qp.End, null, qp.Zone);
					String metricsJson = "{ \"data\": " + new Gson().toJson(metrics) + " }";

					// add as text for older clients ...

					JsonObject textContent = new JsonObject();
					content.add(textContent);
					textContent.addProperty("type", "text");
					textContent.addProperty("text", metricsJson);

					// ... and as structured content for newer clients
					result.add("structuredContent", JsonParser.parseString(metricsJson));
				}
				catch (Exception e) {
					result.addProperty("isError", true);
					
					JsonObject errContent = new JsonObject();
					content.add(errContent);
					errContent.addProperty("type", "text");
					errContent.addProperty("text", e.toString());
				}

				return(result);
			}
		});
	}

	// +---------+
	// | Members |
	// +---------+

	private static JsonRpc2 rpc;
}
