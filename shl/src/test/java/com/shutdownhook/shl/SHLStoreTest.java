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

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.SqlStore;

public class SHLStoreTest
{
	private static TestHelper helper = null;
	private static String basicManifestId = null;
	
	// +------------------+
	// | Setup & Teardown |
	// +------------------+

	@BeforeClass
	public static void setup() throws Exception {

		Easy.setSimpleLogFormat("FINE");
		helper = new TestHelper();
		basicManifestId = createBasicManifest();
	}

	@AfterClass
	public static void cleanup() throws Exception {
		
		if (basicManifestId != null) helper.getStore().deleteManifest(basicManifestId);
		helper.close(); helper = null;
	}
	
	// +-------------------+
	// | testManifestBasic |
	// +-------------------+

	@Test
    public void testManifestBasic() throws Exception {

		SHLStore.FullManifest fm = helper.getStore().queryManifest(basicManifestId);
		
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

	// +--------------+
	// | testUrlBasic |
	// +--------------+

	@Test
    public void testUrlBasic() throws Exception {

		SHLStore.FullManifest fm = helper.getStore().queryManifest(basicManifestId);

		testUrlRoundTrip(fm.Files.get(0), null);
		testUrlRoundTrip(fm.Files.get(1), 60L * 60L);
	}

	private void testUrlRoundTrip(SHLStore.ManifestFile mf, Long expirationEpochSecond) {
		
		String urlId = helper.getStore().createUrl(mf, expirationEpochSecond);
		SHLStore.FullUrl fu = helper.getStore().queryUrl(urlId);

		Assert.assertEquals(mf.FileId, fu.ManifestFile.FileId);
		Assert.assertEquals(mf.ManifestId, fu.ManifestFile.ManifestId);
		Assert.assertEquals(mf.ContentType, fu.ManifestFile.ContentType);
		Assert.assertEquals(mf.ManifestUniqueName, fu.ManifestFile.ManifestUniqueName);
		Assert.assertEquals(mf.JWE, fu.ManifestFile.JWE);
		
		Assert.assertEquals(urlId, fu.ManifestUrl.UrlId);
		Assert.assertEquals(mf.FileId, fu.ManifestUrl.FileId);
		Assert.assertEquals(mf.ManifestId, fu.ManifestUrl.ManifestId);
		
		Assert.assertEquals(expirationEpochSecond == null ? 0 : expirationEpochSecond,
							(long) fu.ManifestUrl.ExpirationEpochSecond);
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

		return(helper.getStore().createManifest(fm));
    }

}

