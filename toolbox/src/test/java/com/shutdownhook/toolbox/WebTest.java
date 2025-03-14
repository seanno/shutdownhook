/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.toolbox;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

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
	private static WebRequests requestsDefaultSSL;
	
	@BeforeClass
	public static void beforeClass() throws Exception {
		Global.init();
		
		int port = new Random().nextInt(2000) + 7000; // rand to minimize conflict
		servers[0] = createServer(port, false);
		baseUrls[0] = "http://localhost:" + Integer.toString(port);

		port = new Random().nextInt(2000) + 7000; // rand to minimize conflict
		servers[1] = createServer(port, true);
		baseUrls[1] = "https://localhost:" + Integer.toString(port);
			
		WebRequests.Config clientConfig = new WebRequests.Config();
		clientConfig.TimeoutMillis = 10000;
		clientConfig.TrustedCertificateFile = "@localhost.crt";
		requests = new WebRequests(clientConfig);

		WebRequests.Config clientConfigDefaultSSL = new WebRequests.Config();
		clientConfigDefaultSSL.TimeoutMillis = 10000;
		requestsDefaultSSL = new WebRequests(clientConfigDefaultSSL);
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

		server.registerHandler("/cookie", new WebServer.Handler() {
			public void handle(WebServer.Request request, WebServer.Response response)
				throws Exception {

				String cookie = request.Cookies.get("MACOOKIE");
				if (cookie == null || !cookie.equals("YOIMACOOK  IE%")) {
					response.Status = 500;
				}
				else {
					response.setText("OK");
					response.setSessionCookie("MACOOKIE", "YOYOURACOOKIE", request);
				}
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
		requestsDefaultSSL.close(); requestsDefaultSSL = null;
	}

	@Test
	public void emptyResponse() throws Exception
	{
		WebRequests.Response response = new WebRequests.Response();
		String s = response.toString();
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
    public void echoSuccessToFile() throws Exception
    {
		for (int i = 0; i < SERVER_COUNT; ++i) {

			WebRequests.Params params = new WebRequests.Params();
			params.addQueryParam("echo", "bananafishbones");

			params.ResponseBodyPath =
				File.createTempFile(UUID.randomUUID().toString(), ".txt").getCanonicalPath();
				
			File f = new File(params.ResponseBodyPath);

			try {
				WebRequests.Response response = requests.fetch(baseUrls[i] + "/echo", params);
				Assert.assertEquals(200, response.Status);
				Assert.assertNull(response.Ex);
				Assert.assertEquals("bananafishbones", Easy.stringFromFile(params.ResponseBodyPath));
			}
			finally {
				if (f.exists()) f.delete();
			}
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

    @Test
    public void sslRequestGoodCertDefaultConfig() throws Exception
	{
		WebRequests.Response response = requestsDefaultSSL.fetch("https://google.com");
		Assert.assertEquals(200, response.Status);
		Assert.assertNull(response.Ex);
	}

    @Test
    public void sslRequestGoodCertExtendedConfig() throws Exception
	{
		WebRequests.Response response = requests.fetch("https://google.com");
		Assert.assertEquals(200, response.Status);
		Assert.assertNull(response.Ex);
	}

    @Test
    public void sslRequestBadCert() throws Exception
	{
		WebRequests.Response response = requestsDefaultSSL.fetch(baseUrls[1] + "/static");
		Assert.assertEquals(500, response.Status);
		Assert.assertNotNull(response.Ex);
	}

    @Test
    public void cookies() throws Exception
	{
		String encodedCookieSend = Easy.urlEncode("YOIMACOOK  IE%");
		String encodedCookieReceive = Easy.urlEncode("YOYOURACOOKIE");
		
		WebRequests.Params params = new WebRequests.Params();
		params.addHeader("Cookie", "MACOOKIE=" + encodedCookieSend);
		
		for (int i = 0; i < SERVER_COUNT; ++i) {
			WebRequests.Response response = requests.fetch(baseUrls[i] + "/cookie", params);
			Assert.assertEquals(200, response.Status);
			Assert.assertNull(response.Ex);

			Assert.assertTrue(response.Headers.get("Set-cookie").get(0).startsWith("MACOOKIE=" + encodedCookieReceive));
			Assert.assertTrue(response.Headers.get("Set-cookie").get(0).indexOf("; HttpOnly") != -1);
			if (i == 0) Assert.assertTrue(response.Headers.get("Set-cookie").get(0).indexOf("; Secure") == -1);
			if (i == 1) Assert.assertTrue(response.Headers.get("Set-cookie").get(0).indexOf("; Secure") != -1);
		}
	}

    @Test
    public void simplePasswords() throws Exception
	{
		SimplePasswordStore store = new SimplePasswordStore();
		store.upsert("user", "pass");
		
		WebServer.Config cfg = new WebServer.Config();
		cfg.AuthenticationType = WebServer.Config.AUTHTYPE_SIMPLE;
		cfg.SimplePasswordStore = store.getConfig();

		testBasicAuth(cfg);
	}
	
    @Test
    public void passwordInterfaceOK() throws Exception
	{
		WebServer.Config cfg = new WebServer.Config();
		cfg.AuthenticationType = WebServer.Config.AUTHTYPE_BASIC;
		cfg.BasicAuthClassName = TestPasswordStore.class.getName();
		cfg.BasicAuthConfiguration = "OK";

		testBasicAuth(cfg);
	}

    @Test
    public void passwordInterfaceBadInit() throws Exception
	{
		WebServer.Config cfg = new WebServer.Config();
		cfg.AuthenticationType = WebServer.Config.AUTHTYPE_BASIC;
		cfg.BasicAuthClassName = TestPasswordStore.class.getName();
		cfg.BasicAuthConfiguration = "NOT OK";

		try {
			testBasicAuth(cfg);
			Assert.fail("expected init failure");
		}
		catch (Exception e) {
			// yay
		}
	}

	static public class TestPasswordStore implements WebServer.PasswordStore
	{
		public boolean init(String param) {
			return("OK".equals(param));
		}
		
		public boolean check(String user, String password) {
			return("user".equals(user) && "pass".equals(password));
		}
	}

	private void testBasicAuth(WebServer.Config cfg) throws Exception {
		
		cfg.Port = new Random().nextInt(2000) + 7000; // rand to minimize conflict

		String route = "/x";
		String url = String.format("http://localhost:%d%s", cfg.Port, route);
			
		WebServer server = WebServer.create(cfg);
		server.registerStaticHandler(route, "static", "text/plain");
		server.start();

		// no pw sent
		WebRequests.Response response = requests.fetch(url);
		Assert.assertEquals(401, response.Status);
		Assert.assertEquals("Basic realm=\"" + cfg.BasicAuthRealm + "\"",
							response.Headers.get("Www-authenticate").get(0));

		// bad user
		WebRequests.Params params = new WebRequests.Params();
		params.setBasicAuth("notauser", "notapass");
		response = requests.fetch(url, params);
		Assert.assertEquals(401, response.Status);

		// bad pass
		params = new WebRequests.Params();
		params.setBasicAuth("user", "notapass");
		response = requests.fetch(url, params);
		Assert.assertEquals(401, response.Status);
		
		// good pass
		params = new WebRequests.Params();
		params.setBasicAuth("user", "pass");
		response = requests.fetch(url, params);
		Assert.assertEquals(200, response.Status);
		String setCookie = assertSetCookieNamed(response, cfg.BasicAuthCookieName);

		// good cookie
		params = new WebRequests.Params();
		params.addHeader("Cookie", setCookie);
		response = requests.fetch(url, params);
		Assert.assertEquals(200, response.Status);

		// logout
		String logoutUrl = String.format("http://localhost:%d%s",
										 cfg.Port, cfg.LogoutPath);
		
		response = requests.fetch(logoutUrl);
		Assert.assertEquals(200, response.Status);

		setCookie = assertSetCookieNamed(response, cfg.BasicAuthCookieName);
		int ichExpires = setCookie.indexOf("expires=Thu, 01 Jan 1970 00:00:00 GMT");
		Assert.assertTrue(ichExpires != -1);
		
		server.close();
	}

	private String assertSetCookieNamed(WebRequests.Response response, String name) {

		List<String> setCookies = response.Headers.get("Set-cookie");
		Assert.assertTrue(setCookies != null);
		
		for (String setCookie : setCookies) {
			if (setCookie.startsWith(name + "=")) return(setCookie);
		}

		Assert.fail("cookie " + name + " not found");
		return(null);
	}

}
