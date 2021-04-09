/*
** Read about this code at http://shutdownhook.com.
** No restrictions on use; no assurances or warranties either!
*/

package com.shutdownhook.toolbox;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.junit.Assert;
import org.junit.Test;
import org.junit.BeforeClass;
import org.junit.AfterClass;

public class WebTest
{
	private static WebServer server;
	private static WebRequests requests;
	private static String baseUrl;
	
	@BeforeClass
	public static void beforeClass() throws Exception {
		
		WebServer.Config serverConfig = new WebServer.Config();
		serverConfig.Port = new Random().nextInt(2000) + 7000; // rand to minimize conflict
		server = new WebServer(serverConfig);

		server.registerStaticHandler("/static", "static", "text/plain");
		
		server.registerHandler("/echo", new WebServer.Handler() {
			public void handle(WebServer.Request request, WebServer.Response response) throws Exception {
				String s = request.QueryParams.get("echo");
				if (s == null) { response.Status = 500; }
				else { response.setText(s); }
			}
		});
		
		server.registerHandler("/exception", new WebServer.Handler() {
			public void handle(WebServer.Request request, WebServer.Response response) throws Exception {
				throw new Exception("yuck");
			}
		});

		server.start();

		WebRequests.Config clientConfig = new WebRequests.Config();
		clientConfig.TimeoutMillis = 3000;
		requests = new WebRequests(clientConfig);
		
		baseUrl = String.format("http://localhost:%d", serverConfig.Port);
	}

	@AfterClass
	public static void afterClass() {
		baseUrl = null;
		requests.close(); requests = null;
		server.close(); server = null;
	}
	
    @Test
    public void staticSuccess() throws Exception
    {
		WebRequests.Response response = requests.get(baseUrl + "/static");
		Assert.assertEquals(200, response.Status);
		Assert.assertNull(response.Ex);
		Assert.assertEquals("static", response.Body);
	}
	
    @Test
    public void echoError() throws Exception
    {
		WebRequests.Response response = requests.get(baseUrl + "/echo");
		Assert.assertEquals(500, response.Status);
		Assert.assertNull(response.Ex);
	}

    @Test
    public void echoSuccess() throws Exception
    {
		Map<String,String> echoParams = new HashMap<String,String>();
		echoParams.put("echo", "bananafishbones");

		WebRequests.Response response = requests.get(baseUrl + "/echo", echoParams);
		Assert.assertEquals(200, response.Status);
		Assert.assertNull(response.Ex);
		Assert.assertEquals("bananafishbones", response.Body);
	}

    @Test
    public void exceptionError() throws Exception
    {
		WebRequests.Response response = requests.get(baseUrl + "/exception");
		Assert.assertEquals(500, response.Status);
		Assert.assertNull(response.Ex);
	}

    @Test
    public void notFoundException() throws Exception
	{
		WebRequests.Response response = requests.get(baseUrl + "/nomatch");
		Assert.assertEquals(404, response.Status);
		Assert.assertNull(response.Ex);
	}

    @Test
    public void bogusUrlException() throws Exception
	{
		WebRequests.Response response = requests.get("http://notaserverreallyiswear.us/");
		Assert.assertEquals(500, response.Status);
		Assert.assertNotNull(response.Ex);
	}
}
