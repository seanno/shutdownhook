/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.mynotes;

import java.io.Closeable;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import com.google.gson.Gson;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.WebServer;
import com.shutdownhook.toolbox.WebServer.Request;
import com.shutdownhook.toolbox.WebServer.Response;

public class Server implements Closeable
{
	// +----------------+
	// | Config & Setup |
	// +----------------+

	public static class Config
	{
		public PDF.Config PDF = new PDF.Config();
		public OpenAI.Config OpenAI = new OpenAI.Config();
		public Endpoints.Config Endpoints = new Endpoints.Config();
			
		public WebServer.Config WebServer = new WebServer.Config();
		public String LoggingConfigPath = "@logging.properties";

		public String ClientSiteZip = "@clientSite.zip";

		public String PdfUrl = "/pdf";
		public String ExplainUrl = "/explain";
		public String FilterUrl = "/filter";

		public static Config fromJson(String json) {
			return(new Gson().fromJson(json, Config.class));
		}
	}
	
	public Server(Config cfg) throws Exception {
		
		this.cfg = cfg;
		this.pdf = new PDF(cfg.PDF);
		this.openai = new OpenAI(cfg.OpenAI);
		this.endpoints = new Endpoints(cfg.Endpoints);

		cfg.WebServer.ReadBodyAsString = false;

		if (cfg.WebServer.StaticPagesDirectory == null) {
			this.cfg.WebServer.StaticPagesZip = cfg.ClientSiteZip;
			this.cfg.WebServer.StaticPagesRouteHtmlWithoutExtension = false;
		}

		setupWebServer();
	}
	
	private void setupWebServer() throws Exception {
		server = WebServer.create(cfg.WebServer);
		registerPDF();
		registerExplain();
		registerFilter();
	}

	// +----------------+
	// | Server Control |
	// +----------------+

	public void start() { server.start(); }
	public void runSync() throws Exception { server.runSync(); }
	public void close() { server.close(); }

	// +-------------+
	// | registerPDF |
	// +-------------+

	private void registerPDF() throws Exception {

		server.registerHandler(cfg.PdfUrl, new WebServer.Handler() {
			public void handle(Request request, Response response) throws Exception {
				
				response.BodyFile = pdf.convertToHtmlAsync(request.BodyStream).get();
				response.DeleteBodyFile = true;
				response.ContentType = "text/html";
			}
		});
	}

	// +-----------------+
	// | registerExplain |
	// +-----------------+

	private void registerExplain() throws Exception {

		server.registerHandler(cfg.ExplainUrl, new WebServer.Handler() {
			public void handle(Request request, Response response) throws Exception {
				
				String input = new String(request.BodyStream.readAllBytes(),
										  StandardCharsets.UTF_8);
				
				response.setText(openai.explain(input));
			}
		});
		
	}

	// +----------------+
	// | registerFilter |
	// +----------------+

	private void registerFilter() throws Exception {

		server.registerHandler(cfg.FilterUrl, new WebServer.Handler() {
			public void handle(Request request, Response response) throws Exception {
				String input = request.QueryParams.get("input");
				if (Easy.nullOrEmpty(input)) throw new Exception("Input required");
				response.setJson(endpoints.filterAsync(input).get());
			}
		});
	}

	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	private PDF pdf;
	private OpenAI openai;
	private Endpoints endpoints;
	private WebServer server;

	private final static Logger log = Logger.getLogger(Server.class.getName());
}
