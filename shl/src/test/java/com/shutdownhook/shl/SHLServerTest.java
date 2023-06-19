/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.shl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.Test;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.AfterClass;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.WebRequests;

public class SHLServerTest
{
	private static TestHelper.TestConfigs configs = null;
	private static SHLServer.Config cfg = null;
	private static SHLServer server = null;
	private static SHLStore store = null;
	private static WebRequests requests = null;
	
	// +------------------+
	// | Setup & Teardown |
	// +------------------+

	@BeforeClass
	public static void setup() throws Exception {

		Easy.setSimpleLogFormat("FINE");
		configs = new TestHelper.TestConfigs();

		cfg = configs.getServerConfig();
		server = new SHLServer(cfg);
		server.start();

		// note will use same store as server
		store = new SHLStore(configs.getStoreConfig()); 

		requests = new WebRequests(new WebRequests.Config());
	}

	@AfterClass
	public static void cleanup() throws Exception {

		requests.close();
		server.close();
		configs.close(); configs = null;
	}

	// +--------------------+
	// | Manifest / Content |
	// +--------------------+

	@Test
	public void testManifestEmbedded() throws Exception {
		testManifest(false);
	}

	@Test
	public void testManifestLocations() throws Exception {
		testManifest(true);
	}

	private void testManifest(boolean withLocations) throws Exception {

		JsonObject jsonPayload = testCreateHelper(true, false);
		String manifestUrl = jsonPayload.get("url").getAsString();
		String keyB64u = jsonPayload.get("key").getAsString();

		JsonObject jsonPost = new JsonObject();
		jsonPost.addProperty("recipient", "me");
		jsonPost.addProperty("passcode", TestHelper.SAMPLE_PASSCODE);
		if (withLocations) jsonPost.addProperty("embeddedLengthMax", 0);

		WebRequests.Params params = new WebRequests.Params();
		params.setContentType("application/json");
		params.Body = jsonPost.toString();

		WebRequests.Response response = requests.fetch(manifestUrl, params);
		Assert.assertTrue(response.successful());

		JsonObject jsonManifest = new JsonParser().parse(response.Body).getAsJsonObject();
		JsonArray jsonFiles = jsonManifest.get("files").getAsJsonArray();
		Assert.assertEquals(2, jsonFiles.size());

		checkFile(jsonFiles.get(0).getAsJsonObject(), keyB64u);
		checkFile(jsonFiles.get(1).getAsJsonObject(), keyB64u);
	}

	private void checkFile(JsonObject jsonFile, String keyB64u) throws Exception {

		String contentType = jsonFile.get("contentType").getAsString();

		String jwe = null;
		if (jsonFile.has("embedded")) {
			jwe = jsonFile.get("embedded").getAsString();
		}
		else {
			String location = jsonFile.get("location").getAsString();
			WebRequests.Response response = requests.fetch(location);
			Assert.assertTrue(response.successful());
			jwe = response.Body;
		}
		
		String decrypted = TestHelper.decrypt(jwe, keyB64u);

		String expected = (contentType.equals(TestHelper.SHC_CONTENTTYPE)
						   ? TestHelper.SHC_RAW : TestHelper.IPS_RAW);

		Assert.assertEquals(expected, decrypted);
	}

	// +--------+
	// | Create |
	// +--------+

	@Test
	public void testCreateLinkWithPasscode() throws Exception {
		testCreateHelper(true, true);
	}

	@Test
	public void testCreateLinkNoPasscode() throws Exception {
		testCreateHelper(false, true);
	}

    private JsonObject testCreateHelper(boolean withPasscode, boolean delete) throws Exception {

		long ttlSeconds = 60L * 60L * 72L; // 3 days

		JsonObject jsonParams = new JsonObject();
		jsonParams.addProperty("Passcode", (withPasscode ? TestHelper.SAMPLE_PASSCODE : null));
		jsonParams.addProperty("TtlSeconds", ttlSeconds);
		jsonParams.addProperty("RetrySeconds", 10);
		jsonParams.addProperty("EncryptFiles", true);
		jsonParams.addProperty("Label", TestHelper.SAMPLE_LABEL);

		JsonArray jsonFiles = new JsonArray();
		jsonParams.add("Files", jsonFiles);

		JsonObject jsonFile1 = new JsonObject();
		jsonFiles.add(jsonFile1);
		jsonFile1.addProperty("ManifestUniqueName", TestHelper.BASIC_FILENAME_1);
		jsonFile1.addProperty("ContentType", TestHelper.SHC_CONTENTTYPE);
		jsonFile1.addProperty("FileB64u", Easy.base64urlEncode(TestHelper.SHC_RAW));
			
		JsonObject jsonFile2 = new JsonObject();
		jsonFiles.add(jsonFile2);
		jsonFile2.addProperty("ManifestUniqueName", TestHelper.BASIC_FILENAME_2);
		jsonFile2.addProperty("ContentType", TestHelper.FHIR_CONTENTTYPE);
		jsonFile2.addProperty("FileB64u", Easy.base64urlEncode(TestHelper.IPS_RAW));

		WebRequests.Params params = new WebRequests.Params();
		addAdminToken(params);
		params.setContentType("application/json");
		params.Body = jsonParams.toString();

		WebRequests.Response response = requests.fetch(makeUrl("createPayload"), params);
		Assert.assertTrue(response.successful());

		JsonObject jsonPayload = new JsonParser().parse(response.Body).getAsJsonObject();

		SHLStore.FullManifest fm = store.queryManifest(jsonPayload.get("_manifestId").getAsString());

		Assert.assertEquals(2, fm.Files.size());
		Assert.assertEquals(0, (long) fm.Manifest.PasscodeFailures);
		Assert.assertEquals(withPasscode ? TestHelper.SAMPLE_PASSCODE : null, fm.Manifest.Passcode);
		
		if (delete) {

			params.Body = String.format("{ \"ManifestId\": \"%s\" }",
										jsonPayload.get("_manifestId").getAsString());

			response = requests.fetch(makeUrl("deleteManifest"), params);
			Assert.assertTrue(response.successful());
		}

		return(jsonPayload);
	}

	// +---------+
	// | Helpers |
	// +---------+

	private String makeUrl(String relativeUrl) {
		String baseUrl = String.format("http://localhost:%d/", cfg.WebServer.Port);
		return(baseUrl + relativeUrl);
	}

	private void addAdminToken(WebRequests.Params params) {
		params.addHeader(cfg.AdminTokenHeader, TestHelper.ADMIN_TOKEN);
	}
}

