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
		public Manifest Manifest;
		public List<ManifestFile> Files = new ArrayList<ManifestFile>();
	}
	
	public static class Manifest
	{
		public String ManifestId;
		public String KeyB64u;
		public String Flags;
		public String Label;
		public Integer ExpirationEpochSeconds;
		public String Passcode;
		public Integer PasscodeFailures;

		public static Manifest fromRow(ResultSet rs) throws SQLException {

			Manifest m = new Manifest();
			m.ManifestId = rs.getString("manifest_id");
			m.KeyB64u = rs.getString("key_b64u");
			m.Flags = rs.getString("flags");
			m.Label = rs.getString("label");
			m.ExpirationEpochSeconds = rs.getInt("exp_epoch_seconds");
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

	public static class FullFile
	{
		public ManifestUrl ManifestUrl;
		public ManifestFile ManifestFile;
	}
	
	public static class ManifestUrl
	{
		public String UrlId;
		public String FileId;
		public String ManifestId;
		public Integer ExpirationEpochSeconds;

		public static ManifestUrl fromRow(ResultSet rs) throws SQLException {
			
			ManifestUrl mu = new ManifestUrl();
			mu.UrlId = rs.getString("url_id");
			mu.FileId = rs.getString("file_id");
			mu.ManifestId = rs.getString("manifest_id");
			mu.ExpirationEpochSeconds = rs.getInt("exp_epoch_seconds");

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

	public String createManifest(Manifest m, List<ManifestFile> files) {
		
		String manifestId = makeId();
		
		try {
			return(internalCreateManifest(manifestId, m, files));
		}
		catch (Exception e) {
			log.severe(Easy.exMsg(e, "createManifest", true));
			deleteManifest(manifestId);
			return(null);
		}
	}
	
	private String internalCreateManifest(String manifestId, Manifest m,
										  List<ManifestFile> files) throws Exception {

		SqlStore.Return<Boolean> added = new SqlStore.Return<Boolean>();

		update(INSERT_MANIFEST, new SqlStore.UpdateHandler() {
			public void prepare(PreparedStatement stmt, int iter) throws Exception {
				stmt.setString(1, manifestId);
				stmt.setString(2, m.KeyB64u);
				stmt.setString(3, m.Flags);
				stmt.setString(4, m.Label);
				stmt.setInt(5, (m.ExpirationEpochSeconds == null ? 0 : m.ExpirationEpochSeconds));
				stmt.setString(6, m.Passcode);
			}
			public void confirm(int rowsAffected, int iter) {
				if (rowsAffected > 0) added.Value = true;
			}
		});

		if (!added.Value) throw new Exception("Failed adding manifest row");

		for (ManifestFile mf : files) {

			String fileId = makeId();

			saveJWE(fileId, mf.JWE);
			
			update(INSERT_FILE, new SqlStore.UpdateHandler() {
				public void prepare(PreparedStatement stmt, int iter) throws Exception {
					stmt.setString(1, fileId);
					stmt.setString(2, manifestId);
					stmt.setString(3, mf.ContentType);
					stmt.setString(4, mf.ManifestUniqueName);
				}
				public void confirm(int rowsAffected, int iter) {
					if (rowsAffected > 0) added.Value = true;
				}
			});

			if (!added.Value) throw new Exception("Failed adding manifest file row");
		}

		return(manifestId);
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

	// +-----------+
	// | createUrl |
	// +-----------+

	public String createUrl(ManifestFile mf, Integer expirationEpochSeconds) {
		try {
			return(internalCreateUrl(mf, expirationEpochSeconds));
		}
		catch (Exception e) {
			log.severe(Easy.exMsg(e, "createUrl", true));
			return(null);
		}
	}

	private String internalCreateUrl(ManifestFile mf, Integer expirationEpochSeconds) throws Exception {

		String urlId = makeId();

		SqlStore.Return<Boolean> added = new SqlStore.Return<Boolean>();

		update(INSERT_URL, new SqlStore.UpdateHandler() {
			public void prepare(PreparedStatement stmt, int iter) throws Exception {
				stmt.setString(1, urlId);
				stmt.setString(2, mf.FileId);
				stmt.setString(3, mf.ManifestId);
				stmt.setInt(4, expirationEpochSeconds == null ? 0 : expirationEpochSeconds);
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

	public FullFile queryUrl(String urlId) {
		try {
			return(internalQueryUrl(urlId));
		}
		catch (Exception e) {
			log.severe(Easy.exMsg(e, "queryFileUrl", true));
			return(null);
		}
	}

	private FullFile internalQueryUrl(String urlId) throws Exception {

		SqlStore.Return<FullFile> ff = new SqlStore.Return<FullFile>();
		ff.Value = new FullFile();
		
		query(QUERY_URL, new SqlStore.QueryHandler() {
			public void prepare(PreparedStatement stmt) throws Exception {
				stmt.setString(1, urlId);
			}
			public void row(ResultSet rs, int irow) throws Exception {
				ff.Value.ManifestFile = ManifestFile.fromRow(rs);
				ff.Value.ManifestUrl = ManifestUrl.fromRow(rs);
			}
		});

		ff.Value.ManifestFile.JWE = loadJWE(ff.Value.ManifestFile.FileId);

		return(ff.Value);
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
		"(manifest_id, key_b64u, flags, label, exp_epoch_seconds, " +
		" passcode, passcode_failures) " +
		"values (?,?,?,?,?,?,0) ";

	private final static String DELETE_MANIFEST =
		"delete from manifests where manifest_id = ?";

	private final static String QUERY_MANIFEST =
		"select " +
		"  m.manifest_id manifest_id, " +
		"  m.key_b64u key_b64u, " +
		"  m.flags flags, " +
		"  m.label label, " +
		"  m.exp_epoch_seconds exp_epoch_seconds, " +
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

	// files
	
	private final static String INSERT_FILE =
		"insert into files " +
		"(file_id, manifest_id, content_type, manifest_unique_name) " +
		"values (?,?,?,?) ";

	private final static String DELETE_FILES =
		"delete from files where manifest_id = ?";

	private final static String QUERY_FILES =
		"select file_id from files where manifest_id = ?";

	// urls
	
	private final static String INSERT_URL =
		"insert into urls " +
		"(url_id, file_id, manifest_id, exp_epoch_seconds) " +
		"values (?,?,?,?) ";

	private final static String DELETE_URLS =
		"delete from urls where manifest_id = ?";

	private final static String QUERY_URL =
		"select " +
		"  u.url_id url_id, " +
		"  u.file_id file_id, " +
		"  u.manifest_id manifest_id, " +
		"  u.exp_epoch_seconds exp_epoch_seconds, " +
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
		"    key_b64u varchar(43) not null, " +
		"    flags varchar(4) not null, " +
		"    label varchar(80) not null, " +
		"    exp_epoch_seconds integer null, " +
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
		"    exp_epoch_seconds integer null, " +
		" " +
		"    foreign key (manifest_id) references manifests, " +
		"    foreign key (file_id) references files, " +
		"    primary key (url_id) " +
		") ";

}
