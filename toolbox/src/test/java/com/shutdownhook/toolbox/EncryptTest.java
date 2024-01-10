/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.toolbox;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Ignore;

public class EncryptTest
{
	@BeforeClass
	public static void beforeClass() throws Exception {
		Global.init();
	}

	@Test
	public void testEncryptRoundTripAES() throws Exception {

		Encrypt enc = new Encrypt(makeTestConfig());

		final String ENC_ROUNDTRIP_INPUT = "akdsjflksadfalksajf***(*)(*)__akjdsfakjdsas";
		
		String encrypted = enc.encrypt(ENC_ROUNDTRIP_INPUT);
		System.out.println("Encrypted: " + encrypted);

		String decrypted = enc.decrypt(encrypted);
		System.out.println("Decrypted: " + decrypted);

		Assert.assertEquals(ENC_ROUNDTRIP_INPUT, decrypted);
	}

	// +---------+
	// | Helpers |
	// +---------+

	private Encrypt.Config makeTestConfig() {

		Encrypt.Config cfg = new Encrypt.Config();
		
		cfg.ActiveKey = new Encrypt.TaggedKey();
		cfg.ActiveKey.Tag = "testme";
		cfg.ActiveKey.Transform = "AES";
		cfg.ActiveKey.Key = Encrypt.generateKey(cfg.ActiveKey.Transform);

		return(cfg);
	}
	
}
