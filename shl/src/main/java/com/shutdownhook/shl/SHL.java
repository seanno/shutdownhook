/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.shl;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import javax.crypto.SecretKey;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import com.shutdownhook.toolbox.Easy;

public class SHL
{
	// +----------------+
	// | Config & Setup |
	// +----------------+
	
	public static class Config
	{
		public SHLStore.Config Store;
		public String AdminToken;
		
		public static Config fromJson(String json) {
			return(new Gson().fromJson(json, Config.class));
		}
	}

	public SHL(Config cfg) throws Exception {
		
		this.cfg = cfg;
		this.store = new SHLStore(cfg.Store);
		this.encrypt = new SHLEncrypt();
	}

	// +--------+
	// | Create |
	// +--------+

	// create a SHL and return payload JSON. If EncryptFiles is true,
	// allocate a new key, encrypt the files with it, and return it in b64u form.
	// Otherwise we assume the file data is already encrypted and just store it.
	// this implies the caller will generate the acutally SHL payload and
	// link themselves, which is a slight annoyance but enables us to provide
	// the service trust-free (i.e., with the caller holding the keys).

	public static class Payload
	{
		public String url;
		public String key;
		public Long exp;
		public String flag;
		public String label;
		public String _manifestId; // private convenience for callers

		public String toJson() {
			return(new Gson().toJson(this));
		}
	}
	
	public static class CreateParams
	{
		public String Passcode = null; // default no passcode
		public Long TtlSeconds = 0L; // default no expiration
		public Long RetrySeconds = 0L; // default no Retry-After header
		public Boolean EncryptFiles = false; // default files already encrypted
		public String Label;
		public FileData[] Files;

		public static CreateParams fromJson(String json) {
			return(new Gson().fromJson(json, CreateParams.class));
		}
	}

	public static class FileData
	{
		public String ManifestUniqueName;
		public String ContentType;
		public String FileB64u;
	}

	public Payload create(String adminToken,
						  String manifestUrlPrefix,
						  CreateParams params) throws Exception {

		requireAdminToken(adminToken);
		if (params.Files == null || params.Files.length < 1) {
			throw new IllegalArgumentException("must provide at least one manifest file");
		}

		SecretKey key = (params.EncryptFiles ? encrypt.generateKey() : null);

		// set up the manifest
		
		SHLStore.FullManifest fm = new SHLStore.FullManifest();
		fm.Manifest.Passcode = params.Passcode;

		if (params.RetrySeconds != null && params.RetrySeconds > 0) {
			fm.Manifest.RetrySeconds = params.RetrySeconds;
		}
		
		if (params.TtlSeconds != null && params.TtlSeconds > 0) {
			fm.Manifest.ExpirationEpochSecond = Instant.now()
				.plusSeconds(params.TtlSeconds)
				.getEpochSecond();
		}

		// and the files

		for (FileData fileData : params.Files) {
			SHLStore.ManifestFile mf = new SHLStore.ManifestFile();
			fm.Files.add(mf);

			mf.ManifestUniqueName = fileData.ManifestUniqueName;
			mf.ContentType = fileData.ContentType;
			
			mf.JWE = (key == null ? fileData.FileB64u
					  : encrypt.jweEncrypt(key, fileData.FileB64u));
		}

		// create it and generate the payload
		Payload p = new Payload();

		p._manifestId = store.createManifest(fm);
		p.url = manifestUrlPrefix + p._manifestId;
		
		if (key != null) p.key = encrypt.keyToB64u(key);
		if (fm.Manifest.ExpirationEpochSecond != null) p.exp = fm.Manifest.ExpirationEpochSecond;

		p.flag = (params.Passcode != null ? "P" : "");
		if (params.RetrySeconds != null && params.RetrySeconds > 0) p.flag += "L";

		if (params.Label != null) p.label = params.Label;

		return(p);
	}

 	// +---------+
	// | Helpers |
	// +---------+

	private void requireAdminToken(String inputToken) throws AccessDeniedException {
		
		if (!cfg.AdminToken.equals(inputToken)) {
			throw new AccessDeniedException("invalid admin token");
		}
	}

 	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	private SHLStore store;
	private SHLEncrypt encrypt;
	
	private final static Logger log = Logger.getLogger(SHL.class.getName());
}
