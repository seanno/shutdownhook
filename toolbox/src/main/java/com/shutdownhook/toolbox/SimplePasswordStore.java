/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.toolbox;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class SimplePasswordStore implements WebServer.PasswordStore
{
	// +----------------+
	// | Config & Setup |
	// +----------------+

	public final static String ALG_SHA256 = "SHA256";
	public final static String ALG_DEFAULT = ALG_SHA256;
	
	public static class Config
	{
		public List<Credential> Credentials = new ArrayList<Credential>();
	}

	public static class Credential
	{
		public String User;
		public String Hash;
		public String Salt;
		public String Algorithm;
	}

	public SimplePasswordStore(Config cfg) {
		this.cfg = cfg;
	}

	public SimplePasswordStore() {
		this.cfg = new Config();
	}

	public Config getConfig() {
		return(cfg);
	}

	// +-------------------------+
	// | WebServer.PasswordStore |
	// +-------------------------+

	public synchronized boolean check(String user, String password) {

		Credential cred = find(user);
		if (cred == null) return(false);

		String actual = hash(cred.Salt, password, cred.Algorithm);
		if (actual == null) return(false);

		return(actual.equals(cred.Hash));
	}

	// +-------+
	// | Admin |
	// +-------+

	public synchronized void upsert(String user, String password) {

		if (Easy.nullOrEmpty(user) || Easy.nullOrEmpty(password)) {
			log.severe("user or pass empty");
			return;
		}
		
		Credential cred = find(user);

		if (cred == null) {
			cred = new Credential();
			cred.User = user;
			cfg.Credentials.add(cred);
		}

		cred.Salt = Easy.randomAlphaNumeric(10);
		cred.Algorithm = ALG_DEFAULT;
		cred.Hash = hash(cred.Salt, password, cred.Algorithm);
	}

	public synchronized void delete(String user) {

		int i = findIndex(user);
		if (i != -1) cfg.Credentials.remove(i);
	}

	// +---------+
	// | Helpers |
	// +---------+

	private synchronized Credential find(String user) {

		int i = findIndex(user);
		return(i == -1 ? null : cfg.Credentials.get(i));
	}

	private synchronized int findIndex(String user) {

		for (int i = 0; i < cfg.Credentials.size(); ++i) {
			Credential cred = cfg.Credentials.get(i);
			if (cred.User.equalsIgnoreCase(user)) return(i);
		}

		return(-1);
	}

	private static String hash(String salt, String password, String algorithm) {

		String ret = null;
		
		switch (algorithm) {
			
			case ALG_SHA256:
				ret = Easy.sha256(salt + "|" + password);
				break;

			default:
				log.warning("Unknown algorithm: " + algorithm);
				ret = null;
				break;
		}

		return(ret);
	}

	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	
	private final static Logger log = Logger.getLogger(SimplePasswordStore.class.getName());
}
