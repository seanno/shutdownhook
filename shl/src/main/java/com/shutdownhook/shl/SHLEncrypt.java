/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.shl;

import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.logging.Logger;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.DirectEncrypter;

public class SHLEncrypt
{
	// +--------------------------------+
	// | SHL-specified Encryption Stuff |
	// +--------------------------------+
	
	private final static String KEY_TYPE = "AES";
	private final static int KEY_BITLENGTH = 256;
	private final static JWEAlgorithm JWE_ALGORITHM = JWEAlgorithm.DIR;
	private final static EncryptionMethod ENCRYPTION_METHOD = EncryptionMethod.A256GCM;

	// +------+
	// | Keys |
	// +------+
	
	public SecretKey generateKey() {
		return(keyGen.get().generateKey());
	}

	public String keyToB64u(SecretKey key) {
		return(Base64.getUrlEncoder().withoutPadding().encodeToString(key.getEncoded()));
	}

	public SecretKey b64uToKey(String keyB64u) {
		return(new SecretKeySpec(Base64.getUrlDecoder().decode(keyB64u), KEY_TYPE));
	}

	// +---------+
	// | Encrypt |
	// +---------+

	public String jweEncrypt(SecretKey key, String payloadB64u) throws Exception {
		
		JWEHeader header = new JWEHeader(JWE_ALGORITHM, ENCRYPTION_METHOD);
		Payload payload = new Payload(Base64.getUrlDecoder().decode(payloadB64u));
		
		JWEObject jwe = new JWEObject(header, payload);
		jwe.encrypt(new DirectEncrypter(key));

		return(jwe.serialize());
	}
	
	// +---------+
	// | Members |
	// +---------+

	// KeyGenerator is not documented theadsafe, so....
	private final static ThreadLocal<KeyGenerator> keyGen =
		new ThreadLocal<KeyGenerator>() {
			@Override protected KeyGenerator initialValue() {
				try {
					KeyGenerator keyGen = KeyGenerator.getInstance(KEY_TYPE);
					keyGen.init(KEY_BITLENGTH);
					return(keyGen);
				}
				catch (NoSuchAlgorithmException e) {
					// won't happen
					return(null);
				}
			}
		};
			
		
	private final static Logger log = Logger.getLogger(SHLEncrypt.class.getName());
}
