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

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.SqlStore;

public class SHLTest
{
	private static TestHelper helper = null;
	
	// +------------------+
	// | Setup & Teardown |
	// +------------------+

	@BeforeClass
	public static void setup() throws Exception {

		Easy.setSimpleLogFormat("FINE");
		helper = new TestHelper();
	}

	@AfterClass
	public static void cleanup() throws Exception {
		helper.close(); helper = null;
	}
	
	@Test
    public void testCreateWithPasscode() throws Exception {
		testCreateHelper(true);
	}
	
	@Test
    public void testCreateWithoutPasscode() throws Exception {
		testCreateHelper(false);
	}

    private void testCreateHelper(boolean withPasscode) throws Exception {

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

		SHL.Payload payload = helper.getSHL().create(TestHelper.ADMIN_TOKEN,
													 TestHelper.URL_PREFIX,
													 params);

		Assert.assertTrue(payload.url.startsWith(TestHelper.URL_PREFIX));
		Assert.assertTrue(payload.exp > Instant.now().plusSeconds(ttlSeconds - 10).getEpochSecond());
		if (withPasscode) Assert.assertTrue(payload.flag.indexOf("P") != -1);
		Assert.assertTrue(payload.flag.indexOf("L") != -1);
		Assert.assertEquals("", payload.flag.replace("P","").replace("L",""));
		Assert.assertEquals(TestHelper.SAMPLE_LABEL, payload.label);

		SHLStore.FullManifest fm = helper.getStore().queryManifest(payload._manifestId);

		if (withPasscode) {
			Assert.assertEquals(fm.Manifest.Passcode, TestHelper.SAMPLE_PASSCODE);
		}

		String decrypted = helper.decrypt(fm.Files.get(0).JWE, payload.key);
		Assert.assertEquals(TestHelper.SHC_RAW, decrypted);
	}

}

