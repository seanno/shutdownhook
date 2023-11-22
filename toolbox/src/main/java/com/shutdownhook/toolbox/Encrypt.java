/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.toolbox;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class Encrypt
{
	// +--------+
	// | Config |
	// +--------+
	
	public static class Config
	{
		public TaggedKey ActiveKey;
		public TaggedKey[] RolledKeys;
	}
	
	public static class TaggedKey
	{
		public String Tag;
		public String Key;
		public String Transform = "AES";
	}

	// +-------+
	// | Setup |
	// +-------+

	public Encrypt(Config cfg) {

		encryptingKey = new LiveKey(cfg.ActiveKey);

		decryptingKeys = new HashMap<String,LiveKey>();
		decryptingKeys.put(cfg.ActiveKey.Tag, encryptingKey);

		if (cfg.RolledKeys != null) {
			for (TaggedKey taggedKey : cfg.RolledKeys) {
				decryptingKeys.put(taggedKey.Tag, new LiveKey(taggedKey));
			}
		}
	}

	public static class LiveKey extends TaggedKey
	{
		public LiveKey(TaggedKey taggedKey) {
			this.Tag = taggedKey.Tag;
			this.Key = taggedKey.Key;
			this.Transform = taggedKey.Transform;

			byte[] decodedKey = Base64.getUrlDecoder().decode(this.Key);
			this.SecretKey = new SecretKeySpec(decodedKey, 0, decodedKey.length, this.Transform);

			this.EncodedTag = Easy.base64urlEncode(this.Tag);
		}
		
		public SecretKey SecretKey;
		public String EncodedTag;
	}

	// +---------+
	// | Encrypt |
	// +---------+

	public String encrypt(String input) {

		try {
			Cipher cipher = Cipher.getInstance(encryptingKey.Transform);
			cipher.init(Cipher.ENCRYPT_MODE, encryptingKey.SecretKey);

			byte[] encrypted = cipher.doFinal(input.getBytes(StandardCharsets.UTF_8));
			String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted);

			return(encoded + "." + encryptingKey.EncodedTag);
		}
		catch (Exception e) {
			log.severe(Easy.exMsg(e, "encrypting: " + input, false));
			return(null);
		}
	}

	// +---------+
	// | Decrypt |
	// +---------+

	public String decrypt(String input) {

		try {
			String fields[] = input.split("\\.");
			if (fields.length != 2) return(null);

			LiveKey decryptingKey = decryptingKeys.get(Easy.base64urlDecode(fields[1]));
			if (decryptingKey == null) return(null);
		
			Cipher cipher = Cipher.getInstance(decryptingKey.Transform);
			cipher.init(Cipher.DECRYPT_MODE, decryptingKey.SecretKey);

			byte[] decrypted = cipher.doFinal(Base64.getUrlDecoder().decode(fields[0]));
			return(new String(decrypted, StandardCharsets.UTF_8));
		}
		catch (Exception e) {
			log.severe(Easy.exMsg(e, "decrypting: " + input, false));
			return(null);
		}
	}

	// +-------------+
	// | generateKey |
	// +-------------+

	public static String generateKey(String transform) {

		try {
			SecretKey key = KeyGenerator.getInstance(transform).generateKey();
			String keyStr = Base64.getUrlEncoder().withoutPadding().encodeToString(key.getEncoded());
			return(keyStr);
		}
		catch (Exception e) {
			log.severe(Easy.exMsg(e, "generating key: " + transform, false));
			return(null);
		}
	}

	// +------------+
	// | Entrypoint |
	// +------------+

	public static void main(String[] args) throws Exception {

		switch (args[0].toLowerCase()) {
			
			case "keygen":
				String transform = args.length > 1 ? args[1] : "AES";
				System.out.println(Encrypt.generateKey(transform));
				break;
		}
	}

	// +---------+
	// | Members |
	// +---------+

	private LiveKey encryptingKey;
	private Map<String,LiveKey> decryptingKeys;

	private final static Logger log = Logger.getLogger(Encrypt.class.getName());
}
