/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.shl;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.SqlStore;

public class SHLStore extends SqlStore
{
	// +----------------+
	// | Config & Setup |
	// +----------------+

	public static class Config
	{
		public SqlStore.Config Sql;
		public String FilesPath;
	}

	public SHLStore(Config cfg) throws Exception {
		super(cfg.Sql);
		this.cfg = cfg;
		ensureTables();
	}

	// +------------+
	// | Structures |
	// +------------+

	public static class FullManifest
	{
		public Manifest Manifest = new Manifest();
		public List<ManifestFile> Files = new ArrayList<ManifestFile>();
	}
	
	public static class Manifest
	{
		public String ManifestId;
		public Long ExpirationEpochSecond;
		public Long RetrySeconds;
		public String Passcode;
		public Integer PasscodeFailures;

		public static Manifest fromRow(ResultSet rs) throws SQLException {

			Manifest m = new Manifest();
			m.ManifestId = rs.getString("manifest_id");
			m.ExpirationEpochSecond = rs.getLong("exp_epoch_second");
			m.RetrySeconds = rs.getLong("retry_seconds");
			m.Passcode = rs.getString("passcode");
			m.PasscodeFailures = rs.getInt("passcode_failures");

			return(m);
		}
	}

	public static class ManifestFile
	{
		public String FileId;
		public String ManifestId;
		public String ContentType;
		public String ManifestUniqueName;
		public String JWE;

		public static ManifestFile fromRow(ResultSet rs) throws SQLException {
			
			ManifestFile mf = new ManifestFile();
			mf.FileId = rs.getString("file_id");
			mf.ManifestId = rs.getString("manifest_id");
			mf.ContentType = rs.getString("content_type");
			mf.ManifestUniqueName = rs.getString("manifest_unique_name");

			return(mf);
		}
	}

	public static class FullUrl
	{
		public ManifestUrl Url;
		public ManifestFile File;
	}
	
	public static class ManifestUrl
	{
		public String UrlId;
		public String FileId;
		public String ManifestId;
		public Long ExpirationEpochSecond;

		public static ManifestUrl fromRow(ResultSet rs) throws SQLException {
			
			ManifestUrl mu = new ManifestUrl();
			mu.UrlId = rs.getString("url_id");
			mu.FileId = rs.getString("file_id");
			mu.ManifestId = rs.getString("manifest_id");
			mu.ExpirationEpochSecond = rs.getLong("exp_epoch_second");

			return(mu);
		}
	}
	
	// +---------------+
	// | queryManifest |
	// +---------------+

	public FullManifest queryManifest(String manifestId) {

		try {
			return(internalQueryManifest(manifestId));
		}
		catch (Exception e) {
			log.severe(Easy.exMsg(e, "queryManifest", true));
			return(null);
		}
	}

	private FullManifest internalQueryManifest(String manifestId) throws Exception {

		SqlStore.Return<FullManifest> fm = new SqlStore.Return<FullManifest>();
		fm.Value = new FullManifest();

		query(QUERY_MANIFEST, new SqlStore.QueryHandler() {
			public void prepare(PreparedStatement stmt) throws Exception {
				stmt.setString(1, manifestId);
			}
			public void row(ResultSet rs, int irow) throws Exception {
				if (irow == 0) fm.Value.Manifest = Manifest.fromRow(rs);
				ManifestFile mf = ManifestFile.fromRow(rs);
				mf.JWE = loadJWE(mf.FileId);
				fm.Value.Files.add(mf);
			}
		});

		return(fm.Value);
	}

	// +----------------+
	// | createManifest |
	// +----------------+

	public String createManifest(FullManifest fm) {

		fm.Manifest.ManifestId = makeId();

		try {
			return(internalCreateManifest(fm));
		}
		catch (Exception e) {
			log.severe(Easy.exMsg(e, "createManifest", true));
			deleteManifest(fm.Manifest.ManifestId);
			return(null);
		}
	}
	
	private String internalCreateManifest(FullManifest fm) throws Exception {

		SqlStore.Return<Boolean> added = new SqlStore.Return<Boolean>();

		long expirationEpochSecond = (fm.Manifest.ExpirationEpochSecond == null
									  ? 0 : fm.Manifest.ExpirationEpochSecond);

		long retrySeconds = (fm.Manifest.RetrySeconds == null ? 0 : fm.Manifest.RetrySeconds);

		update(INSERT_MANIFEST, new SqlStore.UpdateHandler() {
			public void prepare(PreparedStatement stmt, int iter) throws Exception {
				stmt.setString(1, fm.Manifest.ManifestId);
				stmt.setLong(2, expirationEpochSecond);
				stmt.setLong(3, retrySeconds);
				stmt.setString(4, fm.Manifest.Passcode);
			}
			public void confirm(int rowsAffected, int iter) {
				if (rowsAffected > 0) added.Value = true;
			}
		});

		if (!added.Value) throw new Exception("Failed adding manifest row");

		for (ManifestFile mf : fm.Files) {

			mf.ManifestId = fm.Manifest.ManifestId;
			mf.FileId = makeId();

			insertFile(mf);
			saveJWE(mf.FileId, mf.JWE);
		}

		return(fm.Manifest.ManifestId);
	}

	// +----------------+
	// | deleteManifest |
	// +----------------+

	public void deleteManifest(String manifestId) {
		deleteFiles(manifestId);
		deleteManifestHelper(manifestId, DELETE_URLS);
		deleteManifestHelper(manifestId, DELETE_FILES);
		deleteManifestHelper(manifestId, DELETE_MANIFEST);
	}

	private void deleteFiles(String manifestId) {

		try {
			query(QUERY_FILES, new SqlStore.QueryHandler() {
				public void prepare(PreparedStatement stmt) throws Exception {
					stmt.setString(1, manifestId);
				}
				public void row(ResultSet rs, int irow) throws Exception {
					try { deleteJWE(rs.getString(1)); }
					catch (Exception e) { /* eat it */ }
				}
			});
		}
		catch (Exception e2) {
			log.severe(Easy.exMsg(e2, "deleteFiles", true));
		}
	}
		
	private void deleteManifestHelper(String manifestId, String stmt) {
		
		try {
			update(stmt, new SqlStore.UpdateHandler() {
				public void prepare(PreparedStatement stmt, int iter) throws Exception {
					stmt.setString(1, manifestId);
				}
			});
		}
		catch (Exception e) {
			log.severe(Easy.exMsg(e, "deleteManifestHelper", true));
		}
	}

	// +---------------------------+
	// | incrementPasscodeFailures |
	// +---------------------------+

	public void incrementPasscodeFailures(String manifestId) {
		try {
			update(INCREMENT_PASSCODE_FAILURES, new SqlStore.UpdateHandler() {
				public void prepare(PreparedStatement stmt, int iter) throws Exception {
					stmt.setString(1, manifestId);
				}
			});
		}
		catch (Exception e) {
			log.severe(Easy.exMsg(e, "incremetnPasscodeFailures", true));
		}
	}

	// +------------+
	// | upsertFile |
	// +------------+

	public boolean upsertFile(ManifestFile mf) {
		try {
			upsertFileHelper(mf);
			return(true);
		}
		catch (Exception e) {
			log.severe(Easy.exMsg(e, "upsertManifest", true));
			return(false);
		}
	}

	public void upsertFileHelper(ManifestFile mfNew) throws Exception {

		ManifestFile mfOld = queryFile(mfNew.ManifestId, mfNew.ManifestUniqueName);

		if (mfOld == null) {
			mfNew.FileId = makeId();
			insertFile(mfNew);
		}
		else {
			mfNew.FileId = mfOld.FileId;
			
			if (!mfOld.ContentType.equals(mfNew.ContentType)) {
				updateContentType(mfNew);
			}

			deleteJWE(mfOld.FileId);
		}

		saveJWE(mfNew.FileId, mfNew.JWE);
	}

	private ManifestFile queryFile(String manifestId, String manifestUniqueName) throws Exception {
		
		SqlStore.Return<ManifestFile> mf = new SqlStore.Return<ManifestFile>();

		query(QUERY_FILE, new SqlStore.QueryHandler() {
			public void prepare(PreparedStatement stmt) throws Exception {
				stmt.setString(1, manifestId);
				stmt.setString(2, manifestUniqueName);
			}
			public void row(ResultSet rs, int irow) throws Exception {
				mf.Value = ManifestFile.fromRow(rs);
			}
		});

		return(mf.Value);
	}
	
	private void insertFile(ManifestFile mf) throws Exception {
		
		SqlStore.Return<Boolean> added = new SqlStore.Return<Boolean>();
		
		update(INSERT_FILE, new SqlStore.UpdateHandler() {
			public void prepare(PreparedStatement stmt, int iter) throws Exception {
				stmt.setString(1, mf.FileId);
				stmt.setString(2, mf.ManifestId);
				stmt.setString(3, mf.ContentType);
				stmt.setString(4, mf.ManifestUniqueName);
			}
			public void confirm(int rowsAffected, int iter) {
				if (rowsAffected > 0) added.Value = true;
			}
		});

		if (!added.Value) throw new Exception("Failed adding manifest file row");
	}

	private void updateContentType(ManifestFile mf) throws Exception {

		update(UPDATE_FILE_CT, new SqlStore.UpdateHandler() {
			public void prepare(PreparedStatement stmt, int iter) throws Exception {
				stmt.setString(1, mf.ContentType);
				stmt.setString(2, mf.FileId);
			}
		});
	}

	// +------------+
	// | deleteFile |
	// +------------+

	public boolean deleteFile(String manifestId, String manifestUniqueName) {
		try {
			deleteFileHelper(manifestId, manifestUniqueName);
			return(true);
		}
		catch (Exception e) {
			log.severe(Easy.exMsg(e, "deleteManifest", true));
			return(false);
		}
	}

	private void deleteFileHelper(String manifestId, String manifestUniqueName) throws Exception {

		ManifestFile mf = queryFile(manifestId, manifestUniqueName);
		if (mf == null) throw new Exception("manifest file not found");

		update(DELETE_FILE, new SqlStore.UpdateHandler() {
			public void prepare(PreparedStatement stmt, int iter) throws Exception {
				stmt.setString(1, mf.FileId);
			}
		});

		deleteJWE(mf.FileId);
	}

	// +-----------+
	// | createUrl |
	// +-----------+

	public String createUrl(ManifestFile mf, Long expirationEpochSecond) {
		try {
			return(internalCreateUrl(mf, expirationEpochSecond));
		}
		catch (Exception e) {
			log.severe(Easy.exMsg(e, "createUrl", true));
			return(null);
		}
	}

	private String internalCreateUrl(ManifestFile mf, Long expirationEpochSecond) throws Exception {

		String urlId = makeId();

		SqlStore.Return<Boolean> added = new SqlStore.Return<Boolean>();

		update(INSERT_URL, new SqlStore.UpdateHandler() {
			public void prepare(PreparedStatement stmt, int iter) throws Exception {
				stmt.setString(1, urlId);
				stmt.setString(2, mf.FileId);
				stmt.setString(3, mf.ManifestId);
				stmt.setLong(4, expirationEpochSecond == null ? 0 : expirationEpochSecond);
			}
			public void confirm(int rowsAffected, int iter) {
				if (rowsAffected > 0) added.Value = true;
			}
		});

		if (!added.Value) throw new Exception("Failed adding url row");

		return(urlId);
	}
	

	// +----------+
	// | queryUrl |
	// +----------+

	public FullUrl queryUrl(String urlId) {
		try {
			return(internalQueryUrl(urlId));
		}
		catch (Exception e) {
			log.severe(Easy.exMsg(e, "queryFileUrl", true));
			return(null);
		}
	}

	private FullUrl internalQueryUrl(String urlId) throws Exception {

		SqlStore.Return<FullUrl> fu = new SqlStore.Return<FullUrl>();
		fu.Value = new FullUrl();
		
		query(QUERY_URL, new SqlStore.QueryHandler() {
			public void prepare(PreparedStatement stmt) throws Exception {
				stmt.setString(1, urlId);
			}
			public void row(ResultSet rs, int irow) throws Exception {
				fu.Value.File = ManifestFile.fromRow(rs);
				fu.Value.Url = ManifestUrl.fromRow(rs);
			}
		});

		fu.Value.File.JWE = loadJWE(fu.Value.File.FileId);

		return(fu.Value);
	}
	
	// +--------------+
	// | ensureTables |
	// +--------------+

	private void ensureTables() throws Exception {
		ensureTable("manifests", CREATE_MANIFESTS_TABLE);
		ensureTable("files", CREATE_FILES_TABLE);
		ensureTable("urls", CREATE_URLS_TABLE);
	}

	// +---------+
	// | Helpers |
	// +---------+

	private String loadJWE(String fileId) throws Exception {
		return(Easy.stringFromFile(Paths.get(cfg.FilesPath, fileId).toString()));
	}

	private void saveJWE(String fileId, String JWE) throws Exception {
		Easy.stringToFile(Paths.get(cfg.FilesPath, fileId).toString(), JWE);
	}

	private void deleteJWE(String fileId) throws Exception {
		Files.delete(Paths.get(cfg.FilesPath, fileId));
	}

	private String makeId() {
		byte rgb[] = new byte[32];
		rand.nextBytes(rgb);
		return(Base64.getUrlEncoder().withoutPadding().encodeToString(rgb));
	}

	// +---------+
	// | Members |
	// +---------+

	private Config cfg;

	private final static SecureRandom rand = new SecureRandom();

	private final static Logger log = Logger.getLogger(SHLStore.class.getName());

	// +-----+
	// | SQL |
	// +-----+

	// manifest
	
	private final static String INSERT_MANIFEST =
		"insert into manifests " +
		"(manifest_id, exp_epoch_second, retry_seconds," +
		" passcode, passcode_failures) " +
		"values (?,?,?,?,0) ";

	private final static String DELETE_MANIFEST =
		"delete from manifests where manifest_id = ?";

	private final static String QUERY_MANIFEST =
		"select " +
		"  m.manifest_id manifest_id, " +
		"  m.exp_epoch_second exp_epoch_second, " +
		"  m.retry_seconds retry_seconds, " +
		"  m.passcode passcode, " +
		"  m.passcode_failures passcode_failures, " +
		"  f.file_id file_id, " +
		"  f.content_type content_type, " +
		"  f.manifest_unique_name manifest_unique_name " +
		"from " +
		"  manifests m " +
		"join " +
		"  files f on m.manifest_id = f.manifest_id " +
	    "where " +
		"  m.manifest_id = ? ";

	private final static String INCREMENT_PASSCODE_FAILURES =
		"update manifests " +
		"set passcode_failures = passcode_failures + 1 " +
		"where manifest_id = ? ";

	// files
	
	private final static String INSERT_FILE =
		"insert into files " +
		"(file_id, manifest_id, content_type, manifest_unique_name) " +
		"values (?,?,?,?) ";

	private final static String DELETE_FILES =
		"delete from files where manifest_id = ?";

	private final static String DELETE_FILE =
		"delete from files where file_id = ?";

	private final static String QUERY_FILES =
		"select file_id from files where manifest_id = ?";

	private final static String QUERY_FILE =
		"select file_id, manifest_id, content_type, manifest_unique_name " +
		"from files where manifest_id = ? and manifest_unique_name = ?";

	private final static String UPDATE_FILE_CT =
		"update files set content_type = ? where file_id = ?";
		
	// urls
	
	private final static String INSERT_URL =
		"insert into urls " +
		"(url_id, file_id, manifest_id, exp_epoch_second) " +
		"values (?,?,?,?) ";

	private final static String DELETE_URLS =
		"delete from urls where manifest_id = ?";

	private final static String QUERY_URL =
		"select " +
		"  u.url_id url_id, " +
		"  u.file_id file_id, " +
		"  u.manifest_id manifest_id, " +
		"  u.exp_epoch_second exp_epoch_second, " +
		"  f.content_type content_type, " +
		"  f.manifest_unique_name manifest_unique_name " +
		"from " +
		"  urls u " +
		"join " +
		"  files f on u.file_id = f.file_id " +
		"where " +
		"  u.url_id = ?";

	// DDL
	
	private final static String CREATE_MANIFESTS_TABLE =
		"create table manifests " +
		"( " +
		"    manifest_id varchar(43) not null, " +
		"    exp_epoch_second integer null, " +
		"    retry_seconds integer null, " +
		"    passcode varchar(128) null, " +
		"    passcode_failures integer null, " +
		" " +
		"    primary key (manifest_id) " +
		") ";

	private final static String CREATE_FILES_TABLE =
		"create table files " +
		"( " +
		"    file_id varchar(43) not null, " +
		"    manifest_id varchar(43) not null, " +
		"    content_type varchar(128) not null, " +
		"    manifest_unique_name varchar(128) null, " +
		" " +
		"    foreign key (manifest_id) references manifests, " +
		"    primary key (file_id) " +
		") ";

	private final static String CREATE_URLS_TABLE =
		"create table urls " +
		"( " +
		"    url_id varchar(43) not null, " +
		"    file_id varchar(43) not null, " +
		"    manifest_id varchar(43) not null, " +
		"    exp_epoch_second integer null, " +
		" " +
		"    foreign key (manifest_id) references manifests, " +
		"    foreign key (file_id) references files, " +
		"    primary key (url_id) " +
		") ";

}
