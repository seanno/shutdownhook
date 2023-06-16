/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.shl;

import java.io.IOException;
import java.util.logging.Logger;

import com.google.gson.Gson;

import com.shutdownhook.toolbox.Easy;

public class SmartHealthLinks
{
	public static class Config
	{
		public SHLStore.Config Store;
		
		public static Config fromJson(String json) {
			return(new Gson().fromJson(json, Config.class));
		}
	}

	public SmartHealthLinks(Config cfg) throws Exception {
		
		this.cfg = cfg;
		this.store = new SHLStore(cfg.Store);
	}

	// +--------+
	// | Create |
	// +--------+

	public static class CreateParams
	{
		public String Passcode; 
		public Integer TtlSeconds;
		public String Label;
		public Boolean UseUFlag;
		public Boolean UseLFlag;
		public FileData[] Files;
	}

	public static class FileData
	{
		public String ManifestUniqueName;
		public String ContentType;
		public String FileB64u;
	}

	public String create(String jsonParams) throws IllegalArgumentException {

		CreateParams params = parseParams(jsonParams);

		// nyi
		return(null);
	}

	private CreateParams parseParams(String json) throws IllegalArgumentException {

		CreateParams params = null;
		
		try {
			params = new Gson().fromJson(json, CreateParams.class);
		}
		catch (Exception e) {
			throw new IllegalArgumentException("Failed parsing JSON", e);
		}

		if (params.Files == null) {
			throw new IllegalArgumentException("Need at least one file");
		}
		
		if (params.UseUFlag) {
			if (params.Files.length != 1) {
				throw new IllegalArgumentException("U flag requires exactly one file");
			}
			if (Easy.nullOrEmpty(params.Passcode)) {
				throw new IllegalArgumentException("U flag is incompatible with passcode");
			}
		}

		return(params);
	}

	// +-------------------+
	// | Members & Helpers |
	// +-------------------+

	private Config cfg;
	private SHLStore store;
	
	private final static Logger log = Logger.getLogger(SmartHealthLinks.class.getName());
}
