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

import com.shutdownhook.toolbox.Easy;

public class SHLTest
{
	private static TestHelper.TestConfigs configs = null;
	private static SHLStore store = null;
	private static SHL shl = null;

	// +------------------+
	// | Setup & Teardown |
	// +------------------+

	@BeforeClass
	public static void setup() throws Exception {

		Easy.setSimpleLogFormat("FINE");
		configs = new TestHelper.TestConfigs();

		// note both of the below will share the same store config;
		// we use the store as an alternate way to get data for assert
		// confirmations
		shl = new SHL(configs.getSHLConfig());
		store = new SHLStore(configs.getStoreConfig()); 
	}

	@AfterClass
	public static void cleanup() throws Exception {
		configs.close(); configs = null;
	}

	// +--------------------+
	// | Manifest / Content |
	// +--------------------+

	@Test
	public void testManifestEmbedded() throws Exception {

		SHL.Payload payload = testCreateHelper(false, false);
		
		SHL.ManifestPOST post = new SHL.ManifestPOST();
		post.recipient = "me";

		SHL.ManifestReturn mr = shl.manifest(payload.url, "http://localhost/svc", post);

		Assert.assertEquals(200, (long) mr.Status);
		
		SHL.ManifestResponse resp = new Gson().fromJson(mr.JSON, SHL.ManifestResponse.class);
		Assert.assertEquals(1, resp.files.size());
		Assert.assertEquals(TestHelper.SHC_CONTENTTYPE, resp.files.get(0).contentType);
		
		String decrypted = TestHelper.decrypt(resp.files.get(0).embedded, payload.key);
		Assert.assertEquals(TestHelper.SHC_RAW, decrypted);
	}

	@Test
	public void testManifestLocation() throws Exception {

		SHL.Payload payload = testCreateHelper(false, false);
		
		SHL.ManifestPOST post = new SHL.ManifestPOST();
		post.recipient = "me";
		post.embeddedLengthMax = 0L;

		SHL.ManifestReturn mr = shl.manifest(payload.url,
														 "http://localhost/svc",
														 post);

		Assert.assertEquals(200, (long) mr.Status);
		
		SHL.ManifestResponse resp = new Gson().fromJson(mr.JSON, SHL.ManifestResponse.class);
		Assert.assertEquals(1, resp.files.size());
		Assert.assertEquals(TestHelper.SHC_CONTENTTYPE, resp.files.get(0).contentType);

		String jwe = shl.content(resp.files.get(0).location);
		String decrypted = TestHelper.decrypt(jwe, payload.key);
		Assert.assertEquals(TestHelper.SHC_RAW, decrypted);
	}

	// +--------------+
	// | Create Links |
	// +--------------+

	@Test
	public void testCreateLink() throws Exception {
		SHL.CreateParams params = new SHL.CreateParams();
		params.EncryptFiles = true;
		params.Label = TestHelper.SAMPLE_LABEL;
		
		params.Files = new SHL.FileData[1];
		params.Files[0] = new SHL.FileData();
		params.Files[0].ManifestUniqueName = TestHelper.BASIC_FILENAME_1;
		params.Files[0].ContentType = TestHelper.SHC_CONTENTTYPE;
		params.Files[0].FileB64u = Easy.base64urlEncode(TestHelper.SHC_JWE);

		String link = shl.createLink(TestHelper.ADMIN_TOKEN,
												 "https://localhost/svc",
												 "https://localhost/viewer",
												 params);

		Assert.assertTrue(link.startsWith("https://localhost/viewer#"));
		
		String json = Easy.base64urlDecode(link.substring(link.indexOf("shlink:/") + 8));
		System.out.println(json);
		
		SHL.Payload payload = new Gson().fromJson(json, SHL.Payload.class);

		Assert.assertTrue(payload.url.startsWith("https://localhost/svc/"));
	}
	
	@Test
    public void testCreateWithPasscode() throws Exception {
		testCreateHelper(true, true);
	}
	
	@Test
    public void testCreateWithoutPasscode() throws Exception {
		testCreateHelper(false, true);
	}

    private SHL.Payload testCreateHelper(boolean withPasscode, boolean delete) throws Exception {

		long ttlSeconds = 60L * 60L * 72L; // 3 days
		
		SHL.CreateParams params = new SHL.CreateParams();
		params.Passcode = (withPasscode ? TestHelper.SAMPLE_PASSCODE : null);
		params.TtlSeconds = ttlSeconds;
		params.RetrySeconds = 10L;
		params.EncryptFiles = true;
		params.Label = TestHelper.SAMPLE_LABEL;
		
		params.Files = new SHL.FileData[1];
		params.Files[0] = new SHL.FileData();
		params.Files[0].ManifestUniqueName = TestHelper.BASIC_FILENAME_1;
		params.Files[0].ContentType = TestHelper.SHC_CONTENTTYPE;
		params.Files[0].FileB64u = Easy.base64urlEncode(TestHelper.SHC_RAW); // unencrypted

		SHL.Payload payload = shl.createPayload(TestHelper.ADMIN_TOKEN,
															TestHelper.MANIFEST_URL,
															params);

		Assert.assertTrue(payload.url.startsWith(TestHelper.MANIFEST_URL));
		Assert.assertTrue(payload.exp > Instant.now().plusSeconds(ttlSeconds - 10).getEpochSecond());
		if (withPasscode) Assert.assertTrue(payload.flag.indexOf("P") != -1);
		Assert.assertTrue(payload.flag.indexOf("L") != -1);
		Assert.assertEquals("", payload.flag.replace("P","").replace("L",""));
		Assert.assertEquals(TestHelper.SAMPLE_LABEL, payload.label);

		SHLStore.FullManifest fm = store.queryManifest(payload._manifestId);

		if (withPasscode) {
			Assert.assertEquals(fm.Manifest.Passcode, TestHelper.SAMPLE_PASSCODE);
		}

		String decrypted = TestHelper.decrypt(fm.Files.get(0).JWE, payload.key);
		Assert.assertEquals(TestHelper.SHC_RAW, decrypted);

		if (delete) {
			SHL.DeleteManifestParams deleteParams = new SHL.DeleteManifestParams(payload._manifestId);
			shl.deleteManifest(TestHelper.ADMIN_TOKEN, deleteParams);
		}

		return(payload);
	}
}

