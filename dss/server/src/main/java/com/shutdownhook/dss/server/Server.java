/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.dss.server;

import java.io.Closeable;
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

		public String ListQueriesUrl = "/queries";
		public String GetQueryUrl = "/query/details";
		public String SaveQueryUrl = "/query/save";
		public String RunQueryUrl = "/query/run";
		public String DeleteQueryUrl = "/query/delete";
		
		public static Config fromJson(String json) {
			return(new Gson().fromJson(json, Config.class));
		}
	}
	
	public Server(Config cfg) throws Exception {
		this.cfg = cfg;
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
		
		server.registerEmptyHandler("/favicon.ico", 404);
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
				response.setJson(gson.toJson(store.getQueryDetails(id, getAuthEmail(request))));
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
				UUID id = store.saveQuery(details, getAuthEmail(request));
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
				boolean success = store.deleteQuery(id, getAuthEmail(request));
				
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
				response.setJson(gson.toJson(store.listQueries(getAuthEmail(request))));
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

				RunQueryInfo runInfo = ("POST".equalsIgnoreCase(request.Method)
										? runInfoFromBody(request)
										: runInfoFromQueryString(request));

				if (runInfo == null) throw new Exception("Parameters missing");
				
				String user = getAuthEmail(request);

				QueryStore.ExecutionInfo exInfo;
				
				if (runInfo.Statement != null) {
					// check for ability to run arbitrary sql in connection
					exInfo = new QueryStore.ExecutionInfo();
					exInfo.Statement = runInfo.Statement;
					exInfo.ConnectionString = store.getConnectionStringForCreate(runInfo.Connection, user);
					if (exInfo.ConnectionString == null) { response.Status = 401; return; }
				}
				else if (runInfo.QueryId != null) {
					// check for ability to run given query
					exInfo = store.getQueryExecutionInfo(runInfo.QueryId, user);
					if (exInfo == null) { response.Status = 401; return; }
				}
				else {
					throw new Exception("request must include RunQueryInfo in body or params");
				}

				QueryRunner.QueryResults results =
					runner.run(exInfo.ConnectionString, exInfo.Statement, runInfo.Params);
				
				response.setJson(gson.toJson(results));
			}
		});
	}

	private RunQueryInfo runInfoFromBody(Request request) {
		return(new Gson().fromJson(request.Body, RunQueryInfo.class));
	}

	private RunQueryInfo runInfoFromQueryString(Request request) {
		RunQueryInfo runInfo = new RunQueryInfo();
		runInfo.Connection = request.QueryParams.get("connection");
		runInfo.Statement = request.QueryParams.get("statement");

		String queryIdStr = request.QueryParams.get("queryId");
		if (queryIdStr != null) runInfo.QueryId = UUID.fromString(queryIdStr);

		int i = 1;
		List<String> params = new ArrayList<String>();
		
		while (true) {
			String param = request.QueryParams.get("p" + Integer.toString(i++));
			if (param == null) break;
			params.add(param);
		}

		if (params.size() > 0) {
			runInfo.Params = params.toArray(new String[0]);
		}

		if (runInfo.Connection == null ||
			(runInfo.Statement == null || runInfo.QueryId == null)) {

			return(null);
		}

		return(runInfo);
	}

	// +---------+
	// | Helpers |
	// +---------+

	private static String getAuthEmail(Request request) throws Exception {
		String user = request.OAuth2.getEmail();
		if (Easy.nullOrEmpty(user)) throw new Exception("missing auth email");
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