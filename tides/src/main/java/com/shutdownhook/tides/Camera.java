/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.tides;

import java.io.Closeable;
import java.io.File;
import java.util.logging.Logger;

import com.google.gson.Gson;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.WebRequests;

public class Camera implements Closeable
{
	// +----------------+
	// | Config & Setup |
	// +----------------+

	public static class Config
	{
		public String URL;
		public String UserName;
		public String Password;
		public String ImageExtension = ".jpg";

		public WebRequests.Config Requests = new WebRequests.Config();
	}

	public Camera(Config cfg) throws Exception {
		this.cfg = cfg;
		this.requests = new WebRequests(cfg.Requests);
	}

	public void close() {
		requests.close();
		requests = null;
	}
	
	// +--------------+
	// | takeSnapshot |
	// +--------------+

	public File takeSnapshot() {

		// saves to a temp file --- please clean it up!
		File tempFile = null;
		
		try {
			tempFile = File.createTempFile("snapshot", cfg.ImageExtension);
			tempFile.deleteOnExit();
			takeSnapshot(tempFile.getAbsolutePath());
			return(tempFile);
		}
		catch (Exception e) {

			if (tempFile != null) {
				try { tempFile.delete(); }
				catch (Exception e2) { /* eat it */ }
			}
			
			return(null);
		}
	}
	
	public void takeSnapshot(String savePath) throws Exception {

		WebRequests.Params params = new WebRequests.Params();
		params.setBasicAuth(cfg.UserName, cfg.Password);
		params.ResponseBodyPath = savePath;

		WebRequests.Response response = requests.fetch(cfg.URL, params);
		if (!response.successful()) response.throwException("getSnapshot");
	}

	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	private WebRequests requests;

	private final static Logger log = Logger.getLogger(Camera.class.getName());
	
}
