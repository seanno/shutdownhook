//
// BACKSTOP.JAVA
//

package com.shutdownhook.backstop;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import com.shutdownhook.backstop.Resource.Status;
import com.shutdownhook.backstop.Resource.StatusLevel;
import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.Exec;
import com.shutdownhook.toolbox.Template;
import com.shutdownhook.toolbox.Timey;

public class Backstop implements Closeable
{
	// +------------------+
	// | Setup & Teardown |
	// +------------------+

	public final static int DEFAULT_THREADS = 10;
	
	public static class Config
	{
		public Resource.Config[] Resources;
		public Sender.Config Sender;

		public int Threads = DEFAULT_THREADS;
		
		public String SubjectTemplate = "BACKSTOP for {{NOW}}";
		public String BodyTemplatePath = "@backstop.tmpl.html";
		public int NumericDecimalPlaces = 3;
		public String ZoneId;
		
		public static Config fromJson(String json) throws JsonSyntaxException {
			return(new Gson().fromJson(json, Backstop.Config.class));
		}
	}

	public Backstop(Config cfg) throws Exception {
		this.cfg = cfg;
		this.exec = new Exec(cfg.Threads);
		this.sender = new Sender(cfg.Sender);

		this.subjectTemplate = new Template(cfg.SubjectTemplate);
		this.bodyTemplate = new Template(Easy.stringFromSmartyPath(cfg.BodyTemplatePath));
	}

	public void close() {
		exec.close();
	}

	public Config getConfig() {
		return(cfg);
	}
	
	// +-----------------+
	// | checkAllAndSend |
	// +-----------------+

	public void checkAllAndSend() throws Exception {
		List<Status> statuses = checkAll();
		EmailContent content = renderForEmail(statuses);
		sender.send(content.Subject, content.Body);
	}
	
	// +----------+
	// | checkAll |
	// | checkOne |
	// +----------+

	public List<Status> checkAll() throws Exception {

		// run all resource checkers async
		
		List<CompletableFuture<List<Status>>> futures =
			new ArrayList<CompletableFuture<List<Status>>>();

		for (int i = 0; i < cfg.Resources.length; ++i) {

			final Resource.Config resourceConfig = cfg.Resources[i];

			futures.add(exec.runAsync("Resource " + resourceConfig.Name,
									  new Exec.AsyncOperation() {

			    public List<Status> execute() throws Exception {
					return(checkOne(resourceConfig));
			    }
			}));
		}

		// collect up statuses
		
		List<Status> allStatus = new ArrayList<Status>();

		for (int i = 0; i < cfg.Resources.length; ++i) {
			allStatus.addAll(futures.get(i).get());
		}

		// sort and return
		Collections.sort(allStatus);

		return(allStatus);
	}
	
	public List<Status> checkOne(final Resource.Config resourceConfig) throws Exception {
		final List<Status> statuses = new ArrayList<Status>();
		Resource.check(resourceConfig, statuses);
		if (statuses.size() == 0) statuses.add(new Status(resourceConfig));
		Collections.sort(statuses);
		return(statuses);
	}
	
	// +----------------+
	// | renderForEmail |
	// +----------------+

	public static class EmailContent
	{
		public String Subject;
		public String Body;
	}

	public static class BackstopProcessor extends Template.TemplateProcessor
	{
		public BackstopProcessor(List<Status> statuses,
								 Map<String,String> tokens,
								 int numericDecimalPlaces) {
			
			this.statuses = statuses;
			this.tokens = tokens;
			this.numericDecimalPlaces = numericDecimalPlaces;
		}
		
		public boolean repeat(String[] args, int counter) {

			if (counter >= statuses.size()) return(false);
				
			Status status = statuses.get(counter);

			tokens.put("STAT", status.Level.toString());
			tokens.put("NAME", status.Name);
			tokens.put("RESULT", status.getResultText(numericDecimalPlaces));
			tokens.put("NARRATIVE", status.Narrative == null ? "" : status.Narrative);
			
			return(true);
		}

		private List<Status> statuses;
		private Map<String,String> tokens;
		private int numericDecimalPlaces;
	}
	
	public EmailContent renderForEmail(List<Status> statuses) throws Exception {

		EmailContent response = new EmailContent();

		Map<String,String> tokens = new HashMap<String,String>();
		tokens.put("NOW", new Timey(null, cfg.ZoneId).asInformalDateTimeString());
		
		Template.TemplateProcessor proc =
			new BackstopProcessor(statuses, tokens, cfg.NumericDecimalPlaces);

		response.Subject = subjectTemplate.render(tokens, proc);
		response.Body = bodyTemplate.render(tokens, proc);
		
		return(response);
	}

	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	private Exec exec;
	private Sender sender;

	private Template subjectTemplate;
	private Template bodyTemplate;
}
