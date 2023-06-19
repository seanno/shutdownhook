/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.shl;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.AfterClass;

import com.google.gson.Gson;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.SqlStore;

public class SHLStoreTest
{
	private static TestHelper.TestConfigs configs = null;
	private static SHLStore store = null;
	private static String basicManifestId = null;
	
	// +------------------+
	// | Setup & Teardown |
	// +------------------+

	@BeforeClass
	public static void setup() throws Exception {

		Easy.setSimpleLogFormat("FINE");
		configs = new TestHelper.TestConfigs();
		store = new SHLStore(configs.getStoreConfig());
		basicManifestId = createBasicManifest();
	}

	@AfterClass
	public static void cleanup() throws Exception {
		
		if (basicManifestId != null) store.deleteManifest(basicManifestId);
		configs.close(); configs = null;
	}
	
	// +-------------------+
	// | testManifestBasic |
	// +-------------------+

	@Test
    public void testManifestBasic() throws Exception {

		SHLStore.FullManifest fm = store.queryManifest(basicManifestId);
		
		Assert.assertNotNull(fm);
		Assert.assertEquals(basicManifestId, fm.Manifest.ManifestId);
		Assert.assertEquals(0, (long) fm.Manifest.ExpirationEpochSecond);
		Assert.assertEquals(0, (long) fm.Manifest.RetrySeconds);
		Assert.assertEquals(null, fm.Manifest.Passcode);
		Assert.assertEquals(0, (long) fm.Manifest.PasscodeFailures);

		SHLStore.ManifestFile mf1 = fm.Files.get(0);
		Assert.assertNotNull(mf1.FileId);
		Assert.assertEquals(basicManifestId, mf1.ManifestId);
		checkBasicFile(mf1);
		
		SHLStore.ManifestFile mf2 = fm.Files.get(1);
		Assert.assertNotNull(mf2.FileId);
		Assert.assertEquals(basicManifestId, mf2.ManifestId);
		checkBasicFile(mf2);
	}

	private void checkBasicFile(SHLStore.ManifestFile mf) {
		if (mf.ManifestUniqueName.equals(TestHelper.BASIC_FILENAME_1)) {
			Assert.assertEquals(TestHelper.SHC_CONTENTTYPE, mf.ContentType);
			Assert.assertEquals(TestHelper.SHC_JWE, mf.JWE);
		}
		else if (mf.ManifestUniqueName.equals(TestHelper.BASIC_FILENAME_2)) {
			Assert.assertEquals(TestHelper.FHIR_CONTENTTYPE, mf.ContentType);
			Assert.assertEquals(TestHelper.IPS_JWE, mf.JWE);
		}
		else {
			Assert.fail();
		}
	}

	// +-------------------------+
	// | testManifestFilesUpdate |
	// +-------------------------+

	@Test
	public void testManifestFilesUpdate() throws Exception {

		String manifestId = createBasicManifest();
		SHLStore.FullManifest fm = store.queryManifest(manifestId);
		
		Assert.assertEquals(2, fm.Files.size());

		// remove f1
		store.deleteFile(manifestId, TestHelper.BASIC_FILENAME_1);
		fm = store.queryManifest(manifestId);
		
		Assert.assertEquals(1, fm.Files.size());
		Assert.assertEquals(TestHelper.BASIC_FILENAME_2, fm.Files.get(0).ManifestUniqueName);

		// update f2 changing ips -> shc
		SHLStore.ManifestFile mf = fm.Files.get(0);
		mf.ContentType = TestHelper.SHC_CONTENTTYPE;
		mf.JWE = TestHelper.SHC_JWE;

		Assert.assertTrue(store.upsertFile(mf));
		fm = store.queryManifest(manifestId);
		
		Assert.assertEquals(1, fm.Files.size());
		Assert.assertEquals(TestHelper.BASIC_FILENAME_2, fm.Files.get(0).ManifestUniqueName);
		Assert.assertEquals(TestHelper.SHC_JWE, fm.Files.get(0).JWE);

		// add back f1 (but as ips)
		mf = new SHLStore.ManifestFile();
		mf.ManifestId = manifestId;
		mf.ManifestUniqueName = TestHelper.BASIC_FILENAME_1;
		mf.ContentType = TestHelper.FHIR_CONTENTTYPE;
		mf.JWE = TestHelper.IPS_JWE;

		Assert.assertTrue(store.upsertFile(mf));
		fm = store.queryManifest(manifestId);

		Assert.assertEquals(2, fm.Files.size());
		checkFlippedFile(fm.Files.get(0));
		checkFlippedFile(fm.Files.get(1));

		store.deleteManifest(manifestId);
	}

	private void checkFlippedFile(SHLStore.ManifestFile mf) {
		if (mf.ManifestUniqueName.equals(TestHelper.BASIC_FILENAME_2)) {
			Assert.assertEquals(TestHelper.SHC_CONTENTTYPE, mf.ContentType);
			Assert.assertEquals(TestHelper.SHC_JWE, mf.JWE);
		}
		else if (mf.ManifestUniqueName.equals(TestHelper.BASIC_FILENAME_1)) {
			Assert.assertEquals(TestHelper.FHIR_CONTENTTYPE, mf.ContentType);
			Assert.assertEquals(TestHelper.IPS_JWE, mf.JWE);
		}
		else {
			Assert.fail();
		}
	}

	// +--------------+
	// | testUrlBasic |
	// +--------------+

	@Test
    public void testUrlBasic() throws Exception {

		SHLStore.FullManifest fm = store.queryManifest(basicManifestId);

		testUrlRoundTrip(fm.Files.get(0), null);
		testUrlRoundTrip(fm.Files.get(1), 60L * 60L);
	}

	private void testUrlRoundTrip(SHLStore.ManifestFile mf, Long expirationEpochSecond) {
		
		String urlId = store.createUrl(mf, expirationEpochSecond);
		SHLStore.FullUrl fu = store.queryUrl(urlId);

		Assert.assertEquals(mf.FileId, fu.File.FileId);
		Assert.assertEquals(mf.ManifestId, fu.File.ManifestId);
		Assert.assertEquals(mf.ContentType, fu.File.ContentType);
		Assert.assertEquals(mf.ManifestUniqueName, fu.File.ManifestUniqueName);
		Assert.assertEquals(mf.JWE, fu.File.JWE);
		
		Assert.assertEquals(urlId, fu.Url.UrlId);
		Assert.assertEquals(mf.FileId, fu.Url.FileId);
		Assert.assertEquals(mf.ManifestId, fu.Url.ManifestId);
		
		Assert.assertEquals(expirationEpochSecond == null ? 0 : expirationEpochSecond,
							(long) fu.Url.ExpirationEpochSecond);
	}

	// +---------------+
	// | Setup Helpers |
	// +---------------+
	
    private static String createBasicManifest() throws Exception
    {
		SHLStore.FullManifest fm = new SHLStore.FullManifest();
		fm.Manifest = new SHLStore.Manifest();

		SHLStore.ManifestFile mf1 = new SHLStore.ManifestFile();
		mf1.ContentType = TestHelper.SHC_CONTENTTYPE;
		mf1.ManifestUniqueName = TestHelper.BASIC_FILENAME_1;
		mf1.JWE = TestHelper.SHC_JWE;
		
		SHLStore.ManifestFile mf2 = new SHLStore.ManifestFile();
		mf2.ContentType = TestHelper.FHIR_CONTENTTYPE;
		mf2.ManifestUniqueName = TestHelper.BASIC_FILENAME_2;
		mf2.JWE = TestHelper.IPS_JWE;

		List<SHLStore.ManifestFile> files = new ArrayList<SHLStore.ManifestFile>();
		fm.Files.add(mf1);
		fm.Files.add(mf2);

		return(store.createManifest(fm));
    }

}

