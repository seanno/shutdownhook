/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.shl;

import java.io.Closeable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import javax.crypto.SecretKey;

import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.DirectDecrypter;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.SqlStore;

public class TestHelper implements Closeable
{
	public TestHelper() throws Exception {
			
		String tmp = "xyz" + Integer.toString(new Random().nextInt(20000));
		System.out.println("TempStore Prefix is " + tmp);

		this.sqlFile = Files.createTempFile(tmp, ".sql");
		this.jweDir = Files.createTempDirectory(tmp);
		
		SHLStore.Config cfgStore = new SHLStore.Config();
		cfgStore.Sql = new SqlStore.Config("jdbc:sqlite:" + sqlFile.toString());
		cfgStore.FilesPath = jweDir.toString();

		this.store = new SHLStore(cfgStore);

		SHL.Config cfgSHL = new SHL.Config();
		cfgSHL.Store = cfgStore;
		cfgSHL.AdminToken = ADMIN_TOKEN;

		this.shl = new SHL(cfgSHL);
		this.encrypt = new SHLEncrypt();
	}

	public void close() {
		this.shl = null;
		this.store = null;
		sqlFile.toFile().delete();
		jweDir.toFile().delete();
	}

	public SHL getSHL() { return(shl); }
	public SHLStore getStore() { return(store); }

	// +---------+
	// | Decrypt |
	// +---------+

	public String decrypt(String JWE, String keyB64u) throws Exception {

		SecretKey key = encrypt.b64uToKey(keyB64u);
		
		JWEObject jweObject = JWEObject.parse(JWE);
		jweObject.decrypt(new DirectDecrypter(key));

		return(jweObject.getPayload().toString());
	}

	// +---------+
	// | Members |
	// +---------+
	
	private Path sqlFile;
	private Path jweDir;
	private SHLStore store;
	private SHL shl;
	private SHLEncrypt encrypt;

	// +------+
	// | Data |
	// +------+

	public final static String ADMIN_TOKEN = "admin";
	public final static String URL_PREFIX = "https://localhost/manifest?id=";

	public final static String SAMPLE_PASSCODE = "pa$$c0de";
	public final static String SAMPLE_LABEL = "look at me writing tests";
	
	public final static String SHC_CONTENTTYPE = "application/smart-health-card";
	public final static String FHIR_CONTENTTYPE = "application/fhir+json";
	
	public final static String BASIC_FILENAME_1 = "shc";
	public final static String BASIC_FILENAME_2 = "ips";

	public static String SHC_JWE = null;
	public static String SHC_RAW = null;
	public static String IPS_JWE = null;
	public static String IPS_RAW = null;

	static {
		try {
			SHC_JWE = Easy.stringFromResource("shc.jwe.txt");
			SHC_RAW = Easy.stringFromResource("shc.json.txt");
			IPS_JWE = Easy.stringFromResource("ips.jwe.txt");
			IPS_RAW = Easy.stringFromResource("ips.json.txt");
		}
		catch (Exception e) {
			System.err.println(e.toString());
		}
	}
}

