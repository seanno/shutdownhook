/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.toolbox;

import org.junit.Assert;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Ignore;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.shutdownhook.toolbox.JsonRpc2.Method;

public class JsonRpc2Test
{
	// +------------------+
	// | Setup & Teardown |
	// +------------------+

	private static JsonRpc2 rpc;
	private static JsonParser parser;

	public final static String ECHO = "echo";
	public final static String REQ = "required";
	public final static String OPT = "optional";
	
	public final static String ERR = "error";
	public final static String CODE = "code";
	public final static String MSG = "message";
	public final static String RES = "result";
	
	@BeforeClass
	public static void beforeClass() throws Exception {
		
		Global.init();
		parser = new JsonParser();
		
		rpc = new JsonRpc2();

		rpc.registerMethod(ECHO, new String[] { REQ, OPT }, new Method() {
			public JsonObject execute(JsonObject params) throws Exception {
				JsonObject result = new JsonObject();
				if (!params.has(REQ)) throw new Exception("missing required param");
				result.add(REQ, params.get(REQ));
				if (params.has(OPT)) result.add(OPT, params.get(OPT));
				return(result);
			}
		});
	}

	@AfterClass
	public static void afterClass() throws Exception {
		rpc.close();
	}

	// +-------------------------+
	// | Invalid format & params |
	// +-------------------------+

	@Test
	public void invalidJson() throws Exception {
		JsonObject resp = parser.parse(rpc.executeJsonAsync("{ {").get()).getAsJsonObject();
		JsonObject error = resp.getAsJsonObject(ERR);
		Assert.assertEquals(-32700, error.get(CODE).getAsInt());
		Assert.assertNotNull(error.get(MSG));
	}
	
	@Test
	public void unknownMethod() throws Exception {
		String req = buildRequest("yodawg", "test", "{}");
		JsonObject resp = parser.parse(rpc.executeJsonAsync(req).get()).getAsJsonObject();
		JsonObject error = resp.getAsJsonObject(ERR);
		Assert.assertEquals(-32601, error.get(CODE).getAsInt());
		Assert.assertNotNull(error.get(MSG));
	}

	@Test
	public void unknownParam() throws Exception {
		String req = buildRequest(ECHO, "test", "{ 'zippy': 'abc' }");
		JsonObject resp = parser.parse(rpc.executeJsonAsync(req).get()).getAsJsonObject();
		JsonObject error = resp.getAsJsonObject(ERR);
		Assert.assertEquals(-32602, error.get(CODE).getAsInt());
		Assert.assertNotNull(error.get(MSG));
	}

	@Test
	public void tooManyParams() throws Exception {
		String req = buildRequest(ECHO, "test", "[ 'abc', 'def', 'ghi' ]");
		JsonObject resp = parser.parse(rpc.executeJsonAsync(req).get()).getAsJsonObject();
		JsonObject error = resp.getAsJsonObject(ERR);
		Assert.assertEquals(-32602, error.get(CODE).getAsInt());
		Assert.assertNotNull(error.get(MSG));
	}

	@Test
	public void missingParam() throws Exception {
		String req = buildRequest(ECHO, "test", "{}");
		JsonObject resp = parser.parse(rpc.executeJsonAsync(req).get()).getAsJsonObject();
		JsonObject error = resp.getAsJsonObject(ERR);
		Assert.assertEquals(-32603, error.get(CODE).getAsInt()); // method-detected so -32603
		Assert.assertNotNull(error.get(MSG));
	}
	
	@Test
	public void missingParamNotify() throws Exception {
		String req = buildRequest(ECHO, null, "{}");
		String resp = rpc.executeJsonAsync(req).get();
		Assert.assertEquals("", resp.trim());
	}
	
	// +-------------+
	// | Happy paths |
	// +-------------+

	@Test
	public void basicNotify() throws Exception {

		String req = buildRequest(ECHO, null, "{ 'required': 'abc' }");
		String resp = rpc.executeJsonAsync(req).get();
		Assert.assertEquals("", resp.trim());
	}
	
	@Test
	public void basicParamObjectSome() throws Exception {

		String req = buildRequest(ECHO, "test", "{ 'required': 'abc' }");
		JsonObject resp = parser.parse(rpc.executeJsonAsync(req).get()).getAsJsonObject();
		
		assertValidResponse(resp, "test");
		Assert.assertEquals("abc", resp.getAsJsonObject(RES).get(REQ).getAsString());
		Assert.assertNull("abc", resp.getAsJsonObject(RES).get(OPT));
	}
	
	@Test
	public void basicParamObjectAll() throws Exception {

		String req = buildRequest(ECHO, "test", "{ 'required': 'abc', 'optional': 5  }");
		JsonObject resp = parser.parse(rpc.executeJsonAsync(req).get()).getAsJsonObject();
		
		assertValidResponse(resp, "test");
		Assert.assertEquals("abc", resp.getAsJsonObject(RES).get(REQ).getAsString());
		Assert.assertEquals(5, resp.getAsJsonObject(RES).get(OPT).getAsInt());
	}
	
	@Test
	public void basicParamArraySome() throws Exception {

		String req = buildRequest(ECHO, "test", "['abc']");
		JsonObject resp = parser.parse(rpc.executeJsonAsync(req).get()).getAsJsonObject();
		
		assertValidResponse(resp, "test");
		Assert.assertEquals("abc", resp.getAsJsonObject(RES).get(REQ).getAsString());
		Assert.assertNull("abc", resp.getAsJsonObject(RES).get(OPT));
	}

	@Test
	public void basicParamArrayAll() throws Exception {

		String req = buildRequest(ECHO, "test", "[ 'abc', 5 ]");
		JsonObject resp = parser.parse(rpc.executeJsonAsync(req).get()).getAsJsonObject();
		
		assertValidResponse(resp, "test");
		Assert.assertEquals("abc", resp.getAsJsonObject(RES).get(REQ).getAsString());
		Assert.assertEquals(5, resp.getAsJsonObject(RES).get(OPT).getAsInt());
	}
	
	// +-------+
	// | Batch |
	// +-------+

	@Test
	public void batch() throws Exception {

		String req = "[" + buildRequest(ECHO, "t1", "['abc']") + "," +
			buildRequest("badmethodnotify", null, "{}") + "," +
			buildRequest("badmethod", "bm", "{}") + "," +
			buildRequest(ECHO, "t2", "{ 'required': 5, 'optional': 5 }") + "]";

		JsonArray responses = parser.parse(rpc.executeJsonAsync(req).get()).getAsJsonArray();
		
		JsonObject respT1 = findById(responses, "t1");
		assertValidResponse(respT1, "t1");
		Assert.assertEquals("abc", respT1.getAsJsonObject(RES).get(REQ).getAsString());
		Assert.assertNull(respT1.getAsJsonObject(RES).get(OPT));
		
		JsonObject respT2 = findById(responses, "t2");
		assertValidResponse(respT2, "t2");
		Assert.assertEquals(5, respT2.getAsJsonObject(RES).get(REQ).getAsInt());
		Assert.assertEquals(5, respT2.getAsJsonObject(RES).get(OPT).getAsInt());
		
		JsonObject respBM = findById(responses, "bm");
		JsonObject error = respBM.getAsJsonObject(ERR);
		Assert.assertEquals(-32601, error.get(CODE).getAsInt());
		Assert.assertNotNull(error.get(MSG));
		
	}

	private JsonObject findById(JsonArray responses, String id) {
		for (int i = 0; i < responses.size(); ++i) {
			JsonObject resp = responses.get(i).getAsJsonObject();
			if (id.equals(resp.get("id").getAsString())) return(resp);
		}
		return(null);
	}

	// +---------+
	// | Helpers |
	// +---------+

	private void assertValidResponse(JsonObject resp, String id) {
		Assert.assertEquals("2.0", resp.get("jsonrpc").getAsString());
		Assert.assertEquals(id, resp.get("id").getAsString());
		Assert.assertNull(resp.get("error"));
		Assert.assertNotNull(resp.get("result"));
	}

	private static String buildRequest(String methodName, String id, String paramsJson) {
		
		String reqStr = String.format("{ 'jsonrpc': '2.0', %s 'method': '%s', 'params': %s }",
									  id == null ? "" : String.format("'id': '%s',", id),
									  methodName, paramsJson);
		return(reqStr);
	}
	
}
