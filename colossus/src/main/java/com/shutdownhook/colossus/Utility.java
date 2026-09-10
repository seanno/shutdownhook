//
// UTILITY.JAVA
//

package com.shutdownhook.colossus;

import java.io.Closeable;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.jsoup.Jsoup;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.Exec;
import com.shutdownhook.toolbox.WebRequests;

public class Utility implements Closeable
{
	// +------------------+
	// | Setup & Teardown |
	// +------------------+

	public static class Config
	{
		public WebRequests.Config Requests = new WebRequests.Config();
		public String SimpleRequestOverrideFmt = null;
		
		public Integer ExecThreads = Exec.CACHED_THREADPOOL;

		public static Config fromJson(String json) {
			return(new Gson().fromJson(json, Config.class));
		}
	}

	public Utility(Config cfg) throws Exception {
		this.cfg = cfg;
		this.gson = createGson(true);
		this.gsonCompact = createGson(false);
		this.requests = new WebRequests(cfg.Requests);
		this.exec = new Exec(cfg.ExecThreads);
	}

	public void close() {
		requests.close();
		exec.close();
	}

	// +-----------+
	// | Utilities |
	// +-----------+

	public Gson getGson() { return(gson); }
	public Gson getCompactGson() { return(gsonCompact); }
	public WebRequests getRequests() { return(requests); }
	public Exec getExec() { return(exec); }

	// +------------------+
	// | escapeJsonString |
	// +------------------+

	public static String escapeJsonString(String input) {
		String json = new JsonPrimitive(input).toString();
		return(json.substring(1, json.length() - 1));
	}

	// +--------------------+
	// | simpleFetchUrlText |
	// +--------------------+

	public String simpleFetchUrlText(String url) throws Exception {

		if (cfg.SimpleRequestOverrideFmt != null) {
			String cmd = String.format(cfg.SimpleRequestOverrideFmt, url);
			ProcessResult result = runProcess(cmd);
			return(result.toString());
		}

		WebRequests.Response resp = getRequests().fetch(url);
			
		if (!resp.successful()) {
			log.warning(String.format("simpleFetchUrlText failed (%s): %d %s",
									  url, resp.Status, (resp.Ex == null ? "" : resp.Ex.toString())));
			return("");
		}

		String ret = resp.Body;
		String contentType = resp.getFirstHeader("Content-Type");
		if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("text/html")) {
			ret = Jsoup.parse(ret).text();
		}

		return(ret);
	}

	// +------------+
	// | runProcess |
	// +------------+

	public static class ProcessResult
	{
		public int ExitCode;
		public String Output;

		public String toString() {
			return((ExitCode == 0 ? "" : "ERROR\n") + Output);
		}
	}
	
	public static class ProcessOptions
	{
		public boolean CaptureErrorStream = false;
		public String WorkingDirectory = null;
		public Map<String,String> Environment = new HashMap<String,String>();
		public int TimeoutSeconds = 60 * 20; // 20 minute default!
	}

	public ProcessResult runProcess(String command) throws Exception {
		return(runProcess(command, new ProcessOptions()));
	}

	public ProcessResult runProcess(String command, ProcessOptions options) throws Exception {

		ProcessResult result = new ProcessResult();
		result.ExitCode = 1;
		
		String[] commands = new String[] { "bash", "-c", command };
		ProcessBuilder pb = new ProcessBuilder(commands);
		
		pb.redirectErrorStream(options.CaptureErrorStream);
		if (options.WorkingDirectory != null) pb.directory(new File(options.WorkingDirectory));
		for (String key : options.Environment.keySet()) pb.environment().put(key, options.Environment.get(key));
		
		Process p = pb.start();

		CompletableFuture<String> future =
			exec.runAsyncEx("Utility.runProcess", new Exec.AsyncOperationEx() {
				public String execute() throws Exception {
					return(new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
				}
			});

		try {
			result.Output = future.get(options.TimeoutSeconds, TimeUnit.SECONDS);
			p.waitFor();
			result.ExitCode = p.exitValue();
			if (result.ExitCode != 0) {
				log.warning(String.format("Error %d running process %s; output follows", result.ExitCode, command));
				log.warning(result.Output);
			}
		}
		catch (TimeoutException e) {
			future.cancel(true);
			p.destroyForcibly();
			log.warning("TIMEOUT running process: " + command);
		}

		return(result);
		}
	
	// +---------+
	// | Helpers |
	// +---------+

	private static Gson createGson(boolean pretty) {

		GsonBuilder builder = new GsonBuilder()
			.registerTypeAdapter(Instant.class, new JsonSerializer<Instant>() {
				public JsonElement serialize(Instant src, Type typeOfSrc, JsonSerializationContext ctx) {
					return(new JsonPrimitive(src.toString()));
				}
			})
			.registerTypeAdapter(Instant.class, new JsonDeserializer<Instant>() {
				public Instant deserialize(JsonElement json, Type typeOfT,
										   JsonDeserializationContext ctx) throws JsonParseException {
					return(Instant.parse(json.getAsString()));
				}
			});

		if (pretty) builder.setPrettyPrinting();

		return(builder.create());
	}
	
	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	private Gson gson;
	private Gson gsonCompact;
	private WebRequests requests;
	private Exec exec;
	
	private final static Logger log = Logger.getLogger(Utility.class.getName());
}
