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
		public Integer MaxPasscodeFailures = 5;
		
		public String ManifestRelativeUrl = "manifest";
		public String ContentRelativeUrl = "content";

		public Long ContentUrlTtlSeconds = (60L * 60L); // 1 hour
		
		public static Config fromJson(String json) {
			return(new Gson().fromJson(json, Config.class));
		}
	}

	public SHL(Config cfg) throws Exception {
		
		this.cfg = cfg;
		this.store = new SHLStore(cfg.Store);
		this.encrypt = new SHLEncrypt();
	}

	// +----------+
	// | manifest |
	// +----------+

	public static class ManifestPOST
	{
		public String recipient;
		public String passcode;
		public Long embeddedLengthMax = -1L; // default no limit

		public static ManifestPOST fromJson(String json) {
			return(new Gson().fromJson(json, ManifestPOST.class));
		}
	}

	public static class ManifestReturn
	{
		public Integer Status;
		public Long RetrySeconds;
		public String JSON;
	}
	
	public static class PasscodeFailure
	{
		public Integer remainingAttempts;
	}

	public static class ManifestResponse
	{
		public List<ManifestEntry> files = new ArrayList<ManifestEntry>();
	}

	public static class ManifestEntry
	{
		public String contentType;
		public String location;
		public String embedded;
	}
	
	public ManifestReturn manifest(String manifestUrl, String baseUrl,
								   ManifestPOST post) throws Exception {

		// setup
		String manifestId = findId(manifestUrl);
		ManifestReturn mr = new ManifestReturn();

		String cleanRecipient = post.recipient.replaceAll("[\\W]+", " ");
		log.info(String.format("Manifest ID %s requested by %s", manifestId, cleanRecipient));

		SHLStore.FullManifest fm = store.queryManifest(manifestId);

		// check for invalid or expired manifest id
		
		if (fm == null) {
			log.warning(String.format("Manifest ID %s not found", manifestId));
			mr.Status = 404;
			return(mr);
		}

		if (fm.Manifest.ExpirationEpochSecond > 0 &&
			Instant.now().getEpochSecond() > fm.Manifest.ExpirationEpochSecond) {

			log.warning(String.format("Manifest ID %s expired", manifestId));
			mr.Status = 404;
			return(mr);
		}
		
		// passcode stuff
		
		if (fm.Manifest.PasscodeFailures >= cfg.MaxPasscodeFailures) {
			log.warning(String.format("Manifest ID %s disabled (passcode failure max)", manifestId));

			// per spec act like it doesn't exist
			mr.Status = 404;
			return(mr);
		}

		if (fm.Manifest.Passcode != null && !fm.Manifest.Passcode.equals(post.passcode)) {

			PasscodeFailure pf = new PasscodeFailure();
			pf.remainingAttempts = cfg.MaxPasscodeFailures - (fm.Manifest.PasscodeFailures + 1);
			
			log.warning(String.format("Failed passcode for Manifest ID %s; %d remaining",
									  manifestId, pf.remainingAttempts));

			store.incrementPasscodeFailures(manifestId);
			
			mr.Status = 401;
			mr.JSON = new Gson().toJson(pf);
			return(mr);
		}

		// ok seems like we have to return a manifest

		ManifestResponse resp = new ManifestResponse();

		if (fm.Manifest.RetrySeconds > 0) mr.RetrySeconds = fm.Manifest.RetrySeconds;

		for (SHLStore.ManifestFile mf : fm.Files) {

			ManifestEntry me = new ManifestEntry();
			resp.files.add(me);
			
			me.contentType = mf.ContentType;

			if (post.embeddedLengthMax >= 0 &&
				mf.JWE.length() > post.embeddedLengthMax) {

				// make a new location entry
				Long expirationEpochSecond =
					Instant.now().plusSeconds(cfg.ContentUrlTtlSeconds).getEpochSecond();

				String urlId = store.createUrl(mf, expirationEpochSecond);
				if (urlId == null) throw new Exception("failed creating url");
				
				me.location = Easy.urlPaste(Easy.urlPaste(baseUrl, cfg.ContentRelativeUrl), urlId);
			}
			else {
				// cram it right on in there
				me.embedded = mf.JWE;
			}
		}

		mr.Status = 200;
		mr.JSON = new Gson().toJson(resp);
		return(mr);
	}

	// +---------+
	// | content |
	// +---------+

	public String content(String contentUrl) throws Exception {

		String urlId = findId(contentUrl);
		
		SHLStore.FullUrl fu = store.queryUrl(urlId);
		if (fu == null) throw new Exception("content url not found");

		if (fu.Url.ExpirationEpochSecond > 0 &&
			Instant.now().getEpochSecond() > fu.Url.ExpirationEpochSecond) {
			
			throw new Exception("content url expired");
		}

		return(fu.File.JWE);
	}

	// +----------------+
	// | Admin - Create |
	// +----------------+

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
	
	public static class FileData
	{
		public String ManifestUniqueName;
		public String ContentType;
		public String FileB64u;
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

	// create a SHL and return a shlink:/ URL. Note this can only be used
	// when the service allocates the key (otherwise we don't hae a complete
	// payload JSON to encode). viewerUrl may be null in which case
	// the bare shlink:/ is returned.

	public String createLink(String adminToken,
							 String baseUrl, 
							 String viewerUrl,
							 CreateParams params) throws Exception {

		if (!params.EncryptFiles) {
			throw new IllegalArgumentException("createLink only works with EncryptFiles == true");
		}

		Payload payload = createPayload(adminToken, baseUrl, params);

		String shlink = "shlink:/" + Easy.base64urlEncode(payload.toJson());
		if (viewerUrl != null) {
			String hash = (viewerUrl.endsWith("#") ? "" : "#");
			shlink = viewerUrl + hash + shlink;
		}

		return(shlink);
	}
	
	// create a SHL and return payload JSON. If EncryptFiles is true,
	// allocate a new key, encrypt the files with it, and return it in b64u form.
	// Otherwise we assume the file data is already encrypted and just store it.
	// this implies the caller will generate the acutally SHL payload and
	// link themselves, which is a slight annoyance but enables us to provide
	// the service trust-free (i.e., with the caller holding the keys).

	public Payload createPayload(String adminToken,
								 String baseUrl,
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
		if (p._manifestId == null) throw new Exception("failed creating manifest");

		p.url = Easy.urlPaste(Easy.urlPaste(baseUrl, cfg.ManifestRelativeUrl), p._manifestId);
		
		if (key != null) p.key = encrypt.keyToB64u(key);
		if (fm.Manifest.ExpirationEpochSecond != null) p.exp = fm.Manifest.ExpirationEpochSecond;

		p.flag = (params.Passcode != null ? "P" : "");
		if (params.RetrySeconds != null && params.RetrySeconds > 0) p.flag += "L";

		if (params.Label != null) p.label = params.Label;

		return(p);
	}

 	// +------------------------+
	// | Admin - deleteManifest |
 	// +------------------------+

	public static class DeleteManifestParams
	{
		public DeleteManifestParams(String manifestId) { this.ManifestId = manifestId; }
		
		public String ManifestId;

		public static DeleteManifestParams fromJson(String json) {
			return(new Gson().fromJson(json, DeleteManifestParams.class));
		}
	}
	
	public void deleteManifest(String adminToken, DeleteManifestParams params) throws Exception {
		requireAdminToken(adminToken);
		store.deleteManifest(params.ManifestId);
	}

 	// +--------------------+
	// | Admin - upsertFile |
 	// +--------------------+

	public static class UpsertFileParams
	{
		public String ManifestId;
		public String KeyB64u; // null == file encrypted by caller
		public FileData File;

		public static UpsertFileParams fromJson(String json) {
			return(new Gson().fromJson(json, UpsertFileParams.class));
		}
	}

	public void upsertFile(String adminToken, UpsertFileParams params) throws Exception {
		requireAdminToken(adminToken);

		SHLStore.ManifestFile mf = new SHLStore.ManifestFile();
		mf.ManifestId = params.ManifestId;
		mf.ManifestUniqueName = params.File.ManifestUniqueName;
		mf.ContentType = params.File.ContentType;

		if (params.KeyB64u == null) {
			mf.JWE = params.File.FileB64u;
		}
		else {
			SecretKey key = encrypt.b64uToKey(params.KeyB64u);
			mf.JWE = encrypt.jweEncrypt(key, params.File.FileB64u);
		}

		if (!store.upsertFile(mf)) {
			throw new Exception("failed updating manifest file");
		}
	}

	// +--------------------+
	// | Admin - deleteFile |
	// +--------------------+

	public static class DeleteFileParams
	{
		public String ManifestId;
		public String ManifestUniqueName;

		public static DeleteFileParams fromJson(String json) {
			return(new Gson().fromJson(json, DeleteFileParams.class));
		}
	}

	public void deleteFile(String adminToken, DeleteFileParams params) throws Exception {

		requireAdminToken(adminToken);

		if (!store.deleteFile(params.ManifestId, params.ManifestUniqueName)) {
			throw new Exception("delete failed");
		}
	}

 	// +---------+
	// | Helpers |
	// +---------+

	private String findId(String url) {
		String[] parts = url.split("/");
		String id = parts[parts.length - 1];
		int ichQuestion = id.indexOf("?");
		if (ichQuestion != -1) id = id.substring(0, ichQuestion);
		return(id);
	}

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
