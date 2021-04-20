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
	private final static int SERVER_COUNT = 2;
	
	private static WebServer[] servers = new WebServer[SERVER_COUNT];
	private static String[] baseUrls = new String[SERVER_COUNT];
	
	private static WebRequests requests;
	
	@BeforeClass
	public static void beforeClass() throws Exception {

		int port = new Random().nextInt(2000) + 7000; // rand to minimize conflict
		servers[0] = createServer(port, false);
		baseUrls[0] = "http://localhost:" + Integer.toString(port);

		port = new Random().nextInt(2000) + 7000; // rand to minimize conflict
		servers[1] = createServer(port, true);
		baseUrls[1] = "https://localhost:" + Integer.toString(port);
			
		WebRequests.Config clientConfig = new WebRequests.Config();
		clientConfig.TimeoutMillis = 3000;
		clientConfig.TrustedCertificateFile = "@localhost.crt";
		requests = new WebRequests(clientConfig);
	}

	private static WebServer createServer(int port, boolean secure) throws Exception {

		WebServer.Config serverConfig = new WebServer.Config();
		serverConfig.Port = port;

		if (secure) {
			serverConfig.SSLCertificateFile = "@localhost.crt";
			serverConfig.SSLCertificateKeyFile = "@localhost.key";
		}
		
		WebServer server = WebServer.create(serverConfig);

		server.registerStaticHandler("/static", "static", "text/plain");
		
		server.registerHandler("/echo", new WebServer.Handler() {
			public void handle(WebServer.Request request, WebServer.Response response)
				throws Exception {
				
				String s = request.QueryParams.get("echo");
				if (s == null) { response.Status = 500; }
				else { response.setText(s); }
			}
		});
		
		server.registerHandler("/echoPost", new WebServer.Handler() {
			public void handle(WebServer.Request request, WebServer.Response response)
				throws Exception {
				
				if (request.Body == null) { response.Status = 500; }
				else { response.setText(request.Body); }
			}
		});

		server.registerHandler("/exception", new WebServer.Handler() {
			public void handle(WebServer.Request request, WebServer.Response response)
				throws Exception {
				
				throw new Exception("yuck");
			}
		});

		server.start();
		
		return(server);
	}


	@AfterClass
	public static void afterClass() {

		for (int i = 0; i < SERVER_COUNT; ++i) {
			servers[i].close(); servers[i] = null;
			baseUrls[i]= null;
		}

		requests.close(); requests = null;
	}

	@Test
    public void staticSuccess() throws Exception
    {
		for (int i = 0; i < SERVER_COUNT; ++i) {
			WebRequests.Response response = requests.fetch(baseUrls[i] + "/static");
			Assert.assertEquals(200, response.Status);
			Assert.assertNull(response.Ex);
			Assert.assertEquals("static", response.Body);
		}
	}

	@Test
    public void echoError() throws Exception
    {
		for (int i = 0; i < SERVER_COUNT; ++i) {
			WebRequests.Response response = requests.fetch(baseUrls[i] + "/echo");
			Assert.assertEquals(500, response.Status);
			Assert.assertNull(response.Ex);
		}
	}

    @Test
    public void echoSuccess() throws Exception
    {
		for (int i = 0; i < SERVER_COUNT; ++i) {
		
			WebRequests.Params params = new WebRequests.Params();
			params.addQueryParam("echo", "bananafishbones");

			WebRequests.Response response = requests.fetch(baseUrls[i] + "/echo", params);
			Assert.assertEquals(200, response.Status);
			Assert.assertNull(response.Ex);
			Assert.assertEquals("bananafishbones", response.Body);
		}
	}

    @Test
    public void echoPostError() throws Exception
    {
		for (int i = 0; i < SERVER_COUNT; ++i) {
			WebRequests.Response response = requests.fetch(baseUrls[i] + "/echoPost");
			Assert.assertEquals(500, response.Status);
			Assert.assertNull(response.Ex);
		}
	}

    @Test
    public void echoPostSuccess() throws Exception
    {
		for (int i = 0; i < SERVER_COUNT; ++i) {
			
			WebRequests.Params params = new WebRequests.Params();
			params.Body = "zippideedoo";
		
			WebRequests.Response response = requests.fetch(baseUrls[i] + "/echoPost", params);
			Assert.assertEquals(200, response.Status);
			Assert.assertNull(response.Ex);
			Assert.assertEquals("zippideedoo", response.Body);
		}
	}

    @Test
    public void exceptionError() throws Exception
    {
		for (int i = 0; i < SERVER_COUNT; ++i) {
			WebRequests.Response response = requests.fetch(baseUrls[i] + "/exception");
			Assert.assertEquals(500, response.Status);
			Assert.assertNull(response.Ex);
		}
	}

    @Test
    public void notFoundException() throws Exception
	{
		for (int i = 0; i < SERVER_COUNT; ++i) {
			WebRequests.Response response = requests.fetch(baseUrls[i] + "/nomatch");
			Assert.assertEquals(404, response.Status);
			Assert.assertNull(response.Ex);
		}
	}

    @Test
    public void bogusUrlException() throws Exception
	{
		WebRequests.Response response = requests.fetch("http://notaserverreallyiswear.us/");
		Assert.assertEquals(500, response.Status);
		Assert.assertNotNull(response.Ex);
	}
}
