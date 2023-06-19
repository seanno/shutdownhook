/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.shl;

import java.io.Closeable;
import java.util.List;
import java.util.logging.Logger;

import com.google.gson.Gson;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.WebServer;
import com.shutdownhook.toolbox.WebServer.*;

public class SHLServer implements Closeable
{
	// +----------------+
	// | Config & Setup |
	// +----------------+

	public static class Config
	{
		public WebServer.Config WebServer = new WebServer.Config();
		public SHL.Config SHL = new SHL.Config();

		public String ViewerUrl = null; // default bare shlinks
		
		public String ManifestUrl = "/manifest";
		public String ContentUrl = "/content";
		
		public String CreatePayloadUrl = "/createPayload";
		public String CreateLinkUrl = "/createLink";
		public String DeleteManifestUrl = "/deleteManifest";
		public String UpsertFileUrl = "/upsertFile";
		public String DeleteFileUrl = "/deleteFile";

		public String AdminTokenHeader = "X-SHL-AdminToken";

		public static Config fromJson(String json) {
			return(new Gson().fromJson(json, Config.class));
		}
	}

	public SHLServer(Config cfg) throws Exception {
		this.cfg = cfg;
		this.shl = new SHL(cfg.SHL);
		setupWebServer();
	}

	private void setupWebServer() throws Exception {

		server = WebServer.create(cfg.WebServer);
		
		registerManifestHandler();
		registerContentHandler();
		registerCreateLinkHandler();
		registerCreatePayloadHandler();
		registerDeleteManifestHandler();
		registerUpsertFileHandler();
		registerDeleteFileHandler();

		server.registerEmptyHandler("/favicon.ico", 404);
	}
	
	// +----------------+
	// | Server Control |
	// +----------------+

	public void start() { server.start(); }
	public void runSync() throws Exception { server.runSync(); }
	public void close() { server.close(); }

	// +--------------------+
	// | Manifest & Content |
	// +--------------------+
	
	private void registerManifestHandler() {

		server.registerHandler(cfg.ManifestUrl, new Handler() {
			public void handle(Request request, Response response) throws Exception {

				requireMethod(request, "POST");
				SHL.ManifestPOST post = SHL.ManifestPOST.fromJson(request.Body);
				SHL.ManifestReturn mr = shl.manifest(request.Path, request.Base, post);

				response.Status = mr.Status;
				response.ContentType = "application/json";
				response.Body = mr.JSON;

				if (mr.RetrySeconds != null) {
					response.addHeader("Retry-After", mr.RetrySeconds.toString());
				}
			}
		});
	}

	private void registerContentHandler() {
		server.registerHandler(cfg.ContentUrl, new Handler() {
			public void handle(Request request, Response response) throws Exception {

				requireMethod(request, "GET");

				response.Status = 200;
				response.ContentType = "application/jwt";
				response.Body = shl.content(request.Path);
			}
		});
	}

	// +--------------------------+
	// | Manifest Create & Delete |
	// +--------------------------+
		
	private void registerCreateLinkHandler() {
		server.registerHandler(cfg.CreateLinkUrl, new Handler() {
			public void handle(Request request, Response response) throws Exception {

				requireMethod(request, "POST");

				String link = shl.createLink(getAdminToken(request),
											 request.Base,
											 cfg.ViewerUrl,
											 SHL.CreateParams.fromJson(request.Body));

				response.setText(link);
			}
		});
	}

	private void registerCreatePayloadHandler() {
		server.registerHandler(cfg.CreatePayloadUrl, new Handler() {
			public void handle(Request request, Response response) throws Exception {

				requireMethod(request, "POST");

				SHL.Payload payload = shl.createPayload(getAdminToken(request),
														request.Base,
														SHL.CreateParams.fromJson(request.Body));

				response.setJson(payload.toJson());
			}
		});
	}

	private void registerDeleteManifestHandler() {
		server.registerHandler(cfg.DeleteManifestUrl, new Handler() {
			public void handle(Request request, Response response) throws Exception {

				requireMethod(request, "POST");

				shl.deleteManifest(getAdminToken(request),
								   SHL.DeleteManifestParams.fromJson(request.Body));

				response.setText("OK");
			}
		});
	}

	// +----------------------------+
	// | Post-Creation File Updates |
	// +----------------------------+
	
	private void registerUpsertFileHandler() {
		server.registerHandler(cfg.UpsertFileUrl, new Handler() {
			public void handle(Request request, Response response) throws Exception {

				requireMethod(request, "POST");

				shl.upsertFile(getAdminToken(request),
							   SHL.UpsertFileParams.fromJson(request.Body));

				response.setText("OK");
			}
		});
	}

	private void registerDeleteFileHandler() {
		server.registerHandler(cfg.DeleteFileUrl, new Handler() {
			public void handle(Request request, Response response) throws Exception {

				requireMethod(request, "POST");

				shl.deleteFile(getAdminToken(request),
							   SHL.DeleteFileParams.fromJson(request.Body));

				response.setText("OK");
			}
		});
	}

	// +---------+
	// | Helpers |
	// +---------+

	private void requireMethod(Request request, String method) throws Exception {
		if (!request.Method.equalsIgnoreCase(method)) {
			throw new Exception("Invalid method for route");
		}
	}

	private String getAdminToken(Request request) throws Exception {
		List<String> values = request.Headers.get(cfg.AdminTokenHeader);
		return(values == null ? null : values.get(0));
	}

	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	private SHL shl;
	private WebServer server;

	private final static Logger log = Logger.getLogger(SHLServer.class.getName());
}
