/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.dss.server;

import java.io.Closeable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import com.google.gson.Gson;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.SqlStore;
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
		public WebServer.Config WebServer = new WebServer.Config();
		public SqlStore.Config Sql = new SqlStore.Config();

		public int MaxQueryRows = 2500;

		public String LoggingConfigPath = "@logging.properties";
		
		public String ListQueriesUrl = "/data/queries";
		public String GetQueryUrl = "/data/query/details";
		public String SaveQueryUrl = "/data/query/save";
		public String RunQueryUrl = "/data/query/run";
		public String DeleteQueryUrl = "/data/query/delete";
		public String GetSchemaUrl = "/data/connection/schema";

		public String ClientSiteZip = "@clientSite.zip";
		public Boolean StaticPagesRouteHtmlWithoutExtension = false;
		
		public static Config fromJson(String json) {
			return(new Gson().fromJson(json, Config.class));
		}
	}
	
	public Server(Config cfg) throws Exception {
		
		this.cfg = cfg;

		if (cfg.WebServer.StaticPagesDirectory == null) {
			this.cfg.WebServer.StaticPagesZip = cfg.ClientSiteZip;
		}

		this.gson = new Gson();
		this.store = new QueryStore(cfg.Sql);
		this.runner = new QueryRunner();
		
		setupWebServer();
	}
	
	private void setupWebServer() throws Exception {

		server = WebServer.create(cfg.WebServer);

		registerListQueries();
		registerGetQuery();
		registerSaveQuery();
		registerRunQuery();
		registerDeleteQuery();
		registerSchemaQuery();
	}

	// +----------------+
	// | Server Control |
	// +----------------+

	public void start() { server.start(); }
	public void runSync() throws Exception { server.runSync(); }
	public void close() { server.close(); }

	// +------------------+
	// | registerGetQuery |
	// +------------------+

	private void registerGetQuery() throws Exception {
		server.registerHandler(cfg.GetQueryUrl, new WebServer.Handler() {
			public void handle(Request request, Response response) throws Exception {
				UUID id = UUID.fromString(request.QueryParams.get("id"));
				response.setJson(gson.toJson(store.getQueryDetails(id, getAuthUser(request))));
			}
		});
	}
	
	// +-------------------+
	// | registerSaveQuery |
	// +-------------------+

	private void registerSaveQuery() throws Exception {
		server.registerHandler(cfg.SaveQueryUrl, new WebServer.Handler() {
			public void handle(Request request, Response response) throws Exception {
				QueryStore.QueryDetails details = QueryStore.QueryDetails.fromJson(request.Body);
				String user = getAuthUser(request);

				QueryStore.ExecutionInfo exInfo =
					store.getConnectionExecutionInfo(details.ConnectionName, user);
				
				if (exInfo == null) { response.Status = 401; return; }
		
				UUID id = store.saveQuery(details, user);

				if (exInfo.LogQueries) {
					log.info(String.format("[AUDIT] saveQuery %s (%s): %s", id.toString(),
										   user, request.Body.replace("\n", " ")));
				}
				
				response.setJson(String.format("{ \"id\": \"%s\" }", id.toString()));
			}
		});
	}
	
	// +---------------------+
	// | registerDeleteQuery |
	// +---------------------+

	private void registerDeleteQuery() throws Exception {
		server.registerHandler(cfg.DeleteQueryUrl, new WebServer.Handler() {
			public void handle(Request request, Response response) throws Exception {
				
				UUID id = UUID.fromString(request.QueryParams.get("id"));
				boolean success = store.deleteQuery(id, getAuthUser(request));
				
				response.setJson(String.format("{ \"success\": %s }",
											   success ? "true" : "false"));
			}
		});
	}
	
	// +---------------------+
	// | registerListQueries |
	// +---------------------+

	private void registerListQueries() throws Exception {
		server.registerHandler(cfg.ListQueriesUrl, new WebServer.Handler() {
			public void handle(Request request, Response response) throws Exception {
				response.setJson(gson.toJson(store.listQueries(getAuthUser(request))));
			}
		});
	}
	
	// +---------------------+
	// | registerSchemaQuery |
	// +---------------------+

	private void registerSchemaQuery() throws Exception {
		server.registerHandler(cfg.GetSchemaUrl, new WebServer.Handler() {
			public void handle(Request request, Response response) throws Exception {
				
				String connectionName = (request.QueryParams.get("connection"));

				QueryStore.ExecutionInfo exInfo =
					store.getConnectionExecutionInfo(connectionName, getAuthUser(request));
				
				if (exInfo == null) { response.Status = 401; return; }
				
				QueryRunner.QueryResults results = runner.getTableInfo(exInfo.ConnectionString);
				response.setJson(gson.toJson(results));
			}
		});
	}
	
	// +------------------+
	// | registerRunQuery |
	// +------------------+

	public static class RunQueryInfo
	{
		public String Connection;
		public String Statement; 
		public UUID QueryId;
		public String[] Params;
	}
	
	private void registerRunQuery() throws Exception {

		server.registerHandler(cfg.RunQueryUrl, new WebServer.Handler() {
			public void handle(Request request, Response response) throws Exception {

				RunQueryInfo runInfo =
					new Gson().fromJson(request.Body, RunQueryInfo.class);

				if (runInfo == null) throw new Exception("Parameters missing");
				
				String user = getAuthUser(request);

				QueryStore.ExecutionInfo exInfo;
				
				if (runInfo.Statement != null) {
					// check for ability to run arbitrary sql in connection
					exInfo = store.getConnectionExecutionInfo(runInfo.Connection, user);
					if (exInfo == null) { response.Status = 401; return; }
					exInfo.Statement = runInfo.Statement;
				}
				else if (runInfo.QueryId != null) {
					// check for ability to run given query
					exInfo = store.getQueryExecutionInfo(runInfo.QueryId, user);
					if (exInfo == null) { response.Status = 401; return; }
				}
				else {
					throw new Exception("request must include RunQueryInfo in body or params");
				}

				if (exInfo.LogQueries) {
					log.info(String.format("[AUDIT] runQuery (%s): %s",
										   user, request.Body.replace("\n", " ")));
				}

				QueryRunner.QueryResults results =
					runner.run(exInfo.ConnectionString, exInfo.Statement,
							   runInfo.Params, cfg.MaxQueryRows);
				
				response.setJson(gson.toJson(results));
			}
		});
	}

	// +---------+
	// | Helpers |
	// +---------+

	private static String getAuthUser(Request request) throws Exception {
		String user = request.User.Email;
		if (Easy.nullOrEmpty(user)) user = request.User.Id;
		if (Easy.nullOrEmpty(user)) throw new Exception("missing auth email or id");
		return(user);
	}

	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	private WebServer server;
	private QueryStore store;
	private QueryRunner runner;
	private Gson gson;

	private final static Logger log = Logger.getLogger(Server.class.getName());
}
