/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.toolbox;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.shutdownhook.toolbox.Exec;

public class JsonRpc2 implements Closeable
{
	// +------------------+
	// | Setup & Teardown |
	// +------------------+

	public JsonRpc2() {
		this.exec = new Exec();
		this.jsonParser = new JsonParser();
		this.methods = new HashMap<String,MethodInfo>();
	}

	public void close() {
		exec.shutdownPool();
	}

	// +----------------+
	// | registerMethod |
	// +----------------+

	public static interface Method {
		public JsonObject execute(JsonObject params, JsonObject req) throws Exception;
	}

	public void registerMethod(String name, String[] params, Method method) {
		
		MethodInfo info = new MethodInfo();
		info.Name = name;
		info.Params = params;
		info.Method = method;

		log.info("Registered method: " + name);
		methods.put(name, info);
	}

	public static class MethodInfo
	{
		public String Name;
		public String[] Params;
		public Method Method;
	}

	// +--------------------+
	// | executeJson(Async) |
	// +--------------------+

	public CompletableFuture<String> executeJsonAsync(String requestJson) {
		return(exec.runAsync("executeAsync", new Exec.AsyncOperation() {
			public String execute() throws Exception {
				return(executeJson(requestJson));
			}
		}));
	}
	
	public String executeJson(String requestJson) throws Exception {

		JsonElement req = null;
		JsonArray reqs = null;

		// parse the whole thing
		
		try {
			req = jsonParser.parse(requestJson);
		}
		catch (Exception e) {
			return(makeErrorResponse(JsonNull.INSTANCE, PARSE_ERROR, e));
		}

		// figure out if we're a batch or single 
		
		StringBuilder sb = new StringBuilder();
		int responseCount = 0;
		boolean isBatch = false;

		if (req.isJsonArray()) {
			isBatch = true;
			sb.append("[");
			reqs = req.getAsJsonArray();
		}
		else {
			reqs = new JsonArray();
			reqs.add(req);
		}

		// execute and then collect up responses

		List<CompletableFuture<String>> futures = new ArrayList<CompletableFuture<String>>();
		for (int i = 0; i < reqs.size(); ++i) {
			futures.add(executeOneInternalAsync(reqs.get(i).getAsJsonObject()));
		}

		for (int i = 0; i < futures.size(); ++i) {
			String resp = futures.get(i).get();
			if (resp != null) {
				// null is ok, it just means this was a notification
				// and doesn't require a response to be returned
				if (responseCount++ > 0) sb.append(",");
				sb.append(resp);
			}
		}

		// and we're out
		
		if (isBatch) sb.append("]");

		return(sb.toString());
	}

	// +---------------------------+
	// | executeOneInternal(Async) |
	// +---------------------------+

	public CompletableFuture<String> executeOneInternalAsync(JsonObject req) {
		return(exec.runAsync("executeOneInternalAsync", new Exec.AsyncOperation() {
			public String execute() throws Exception {
				return(executeOneInternal(req));
			}
		}));
	}
	
	private String executeOneInternal(JsonObject req) {

		JsonElement id = req.get(PROP_ID);
		String method = req.get(PROP_METHOD).getAsString();
		log.info(String.format("executeOneInternal; method = %s, id = %s", method, id));
		
		MethodInfo info = methods.get(method);
		if (info == null) return(makeErrorResponse(id, METHOD_NOT_FOUND, null));

		JsonObject params = paramsFromRequest(req, info);
		if (params == null) return(makeErrorResponse(id, INVALID_PARAMS, null));
		
		try {
			JsonObject result = info.Method.execute(params, req);
			return(makeResponse(id, result == null ? JsonNull.INSTANCE : result));
		}
		catch (Exception e) {
			return(makeErrorResponse(id, INTERNAL_ERROR, e));
		}
	}

	// +-------------------+
	// | makeResponse      |
	// | makeErrorResponse |
	// +-------------------+

	private String makeResponse(JsonElement id, JsonElement result) {

		if (id == null) return(null);
		
		JsonObject resp = new JsonObject();
		resp.addProperty(PROP_JSONRPC, JSONRPC_V2);
		resp.add(PROP_ID, id);
		resp.add(PROP_RESULT, result);

		String strResp = resp.toString();
		log.fine("Normal Response >>> " + strResp);
		
		return(strResp);
	}
	
	private String makeErrorResponse(JsonElement id, int code, Exception e) {

		if (id == null) return(null);
		
		JsonObject resp = new JsonObject();
		resp.addProperty(PROP_JSONRPC, JSONRPC_V2);
		resp.add(PROP_ID, id);

		JsonObject err = new JsonObject();
		resp.add(PROP_ERROR, err);

		err.addProperty(PROP_ERROR_CODE, code);

		if (e == null) {
			err.addProperty(PROP_ERROR_MSG, Integer.toString(code));
		}
		else {
			err.addProperty(PROP_ERROR_MSG, e.toString());
			log.warning(Easy.exMsg(e, "jsonrpc2", true));
		}

		String strResp = resp.toString();
		log.warning("Error Response >>> " + strResp);

		return(strResp);
	}

	// +-------------------+
	// | paramsFromRequest |
	// +-------------------+

	private JsonObject paramsFromRequest(JsonObject req, MethodInfo info) {

		JsonElement params = req.get(PROP_PARAMS);

		// no params is ok
		if (params == null) return(new JsonObject());

		// as an object is great
		if (params.isJsonObject()) {
			JsonObject objectParams = params.getAsJsonObject();
			return(verifyParamNames(objectParams, info) ? objectParams : null);
		}

		// if not array then, something is up
		if (!params.isJsonArray()) return(null);

		// can't have more given params than configured params;
		// it's ok to have the reverse; it's up to the method handler
		// to complain about that if needed
		JsonArray arrayParams = params.getAsJsonArray();
		if (arrayParams.size() > info.Params.length) return(null);
		
		JsonObject mappedParams = new JsonObject();

		for (int i = 0; i < arrayParams.size(); ++i) {
			mappedParams.add(info.Params[i], arrayParams.get(i));
		}

		return(verifyParamNames(mappedParams, info) ? mappedParams : null);
	}

	private boolean verifyParamNames(JsonObject params, MethodInfo info) {
		// ensure each found param is in the info list. the reverse does
		// not have to be true (the method can enforce if it wants)
		for (String key : params.keySet()) {
			boolean found = false;
			for (int i = 0; i < info.Params.length; ++i) {
				if (key.equals(info.Params[i])) { found = true; break; }
			}
			if (!found) return(false);
		}
		return(true);
	}

	// +--------------------+
	// | Request & Response |
	// +--------------------+

	public static class Request
	{
		public String jsonrpc;
		public String method;
		public Object params;
		public Object id;
	}
	
	public static class Response
	{
		public String jsonrpc;
		public Object result;
		public Error error;
		public Object id;
	}
	
	public static class Error
	{
		public int code;
		public String message;
		public Object data;
	}

	// +-----------+
	// | Constants |
	// +-----------+

	public final static String PROP_JSONRPC = "jsonrpc";
	public final static String PROP_ID = "id";
	public final static String PROP_METHOD = "method";
	public final static String PROP_PARAMS = "params";
	public final static String PROP_RESULT = "result";
	public final static String PROP_ERROR = "error";
	public final static String PROP_ERROR_CODE = "code";
	public final static String PROP_ERROR_MSG = "message";
	public final static String PROP_ERROR_DATA = "data";
	
	public final static String JSONRPC_V2 = "2.0";

	public final static int PARSE_ERROR = -32700;
	public final static int INVALID_REQUEST = -32600;
	public final static int METHOD_NOT_FOUND = -32601;
	public final static int INVALID_PARAMS = -32602;
	public final static int INTERNAL_ERROR = -32603;
	
	public final static int RESERVED_ERROR_MAX = -32000;
	public final static int RESERVED_ERROR_MIN = -32099;


	// +---------+
	// | Members |
	// +---------+

	private Exec exec;
	private JsonParser jsonParser;
	private Map<String,MethodInfo> methods;
	
	private final static Logger log = Logger.getLogger(JsonRpc2.class.getName());
}
