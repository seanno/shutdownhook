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
		public Sender.Config Sender = new Sender.Config();
		public BackstopHelpers.Config Helpers = new BackstopHelpers.Config();

		public int Threads = DEFAULT_THREADS;
		
		public String SubjectTemplate = "BACKSTOP for {{NOW}}";
		public String BodyTemplatePath = "@backstop.tmpl.html";
		public String ZoneId;
		
		public static Config fromJson(String json) throws JsonSyntaxException {
			return(new Gson().fromJson(json, Backstop.Config.class));
		}
	}

	public Backstop(Config cfg) throws Exception {
		this.cfg = cfg;
		this.exec = new Exec(cfg.Threads);
		this.sender = new Sender(cfg.Sender);
		this.helpers = new BackstopHelpers(cfg.Helpers);

		this.subjectTemplate = new Template(cfg.SubjectTemplate);
		this.bodyTemplate = new Template(Easy.stringFromSmartyPath(cfg.BodyTemplatePath));
	}

	public void close() {
		helpers.close();
		exec.close();
	}

	public Config getConfig() {
		return(cfg);
	}
	
	// +------------------+
	// | checkAllAndSend  |
	// | checkAllAndPrint |
	// +------------------+

	public void checkAllAndSend() throws Exception {
		List<BackstopStatus> statuses = checkAll();
		EmailContent content = renderForEmail(statuses);
		sender.send(content.Subject, content.Body);
	}

	public void checkAllAndPrint() throws Exception {
		for (BackstopStatus status : checkAll()) {
			System.out.println(String.format("%s,%s,%s,%s",
											 status.getStatus().getLevel().toString(),
											 status.getResource(),
											 status.getStatus().getMetric(),
											 status.getStatus().getResult()));
		}
	}
	
	// +----------+
	// | checkAll |
	// | checkOne |
	// +----------+

	public List<BackstopStatus> checkAll() throws Exception {

		// run all resource checkers async
		List<CompletableFuture<List<BackstopStatus>>> futures =
			new ArrayList<CompletableFuture<List<BackstopStatus>>>();

		for (int i = 0; i < cfg.Resources.length; ++i) {

			final Resource.Config resourceConfig = cfg.Resources[i];

			futures.add(exec.runAsync("Resource " + resourceConfig.Name,
									  new Exec.AsyncOperation() {

			    public List<BackstopStatus> execute() throws Exception {
					return(checkOne(resourceConfig));
			    }
			}));
		}

		// collect up statuses
		List<BackstopStatus> allStatuses = new ArrayList<BackstopStatus>();

		for (int i = 0; i < cfg.Resources.length; ++i) {
			allStatuses.addAll(futures.get(i).get());
		}

		// sort and return
		Collections.sort(allStatuses);

		return(allStatuses);
	}
	
	public List<BackstopStatus> checkOne(Resource.Config resourceConfig) throws Exception {
		
		List<BackstopStatus> bstatuses = new ArrayList<BackstopStatus>();
		for (Status status : Resource.check(resourceConfig, helpers)) {
			String link = status.getLink();
			if (Easy.nullOrEmpty(link)) link = resourceConfig.Link;
			bstatuses.add(new BackstopStatus(resourceConfig.Name, link, status));
		}
		
		Collections.sort(bstatuses);
		
		return(bstatuses);
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
		public BackstopProcessor(List<BackstopStatus> statuses, Map<String,String> tokens) {
			
			this.statuses = statuses;
			this.tokens = tokens;
		}
		
		public boolean repeat(String[] args, int counter) {

			if (counter >= statuses.size()) return(false);
				
			BackstopStatus status = statuses.get(counter);

			String resource = status.getResource();
			String metric = status.getStatus().getMetric();
			if (!Easy.nullOrEmpty(metric)) resource += ": " + metric;

			tokens.put("STATUS", status.getStatus().getLevel().toString());
			tokens.put("RESOURCE", resource);
			tokens.put("RESULT", status.getStatus().getResult());
			tokens.put("EVENODD", (counter % 2 == 0 ? "EVEN" : "ODD"));

			boolean haveLink = !Easy.nullOrEmpty(status.getLink());
			tokens.put("ADDLINK", haveLink ? "TRUE" : "FALSE");
			tokens.put("LINK", status.getLink());

			return(true);
		}

		private List<BackstopStatus> statuses;
		private Map<String,String> tokens;
	}
	
	public EmailContent renderForEmail(List<BackstopStatus> statuses) throws Exception {

		EmailContent response = new EmailContent();

		Map<String,String> tokens = new HashMap<String,String>();
		tokens.put("NOW", new Timey(null, cfg.ZoneId).asInformalDateTimeString());
		
		Template.TemplateProcessor proc = new BackstopProcessor(statuses, tokens);
		response.Subject = subjectTemplate.render(tokens, proc);
		response.Body = bodyTemplate.render(tokens, proc);
		
		return(response);
	}

	// +----------------+
	// | BackstopStatus |
	// +----------------+

	public static class BackstopStatus implements Comparable<BackstopStatus>
	{
		public BackstopStatus(String resource, String link, Status status) {
			this.resource = resource;
			this.link = link;
			this.status = status;
		}

		public String getResource() { return(resource); }
		public String getLink() { return(link); }
		public Status getStatus() { return(status); }
		
		public int compareTo(BackstopStatus other) {
			if (other == null) throw new NullPointerException();
			int cmp = other.status.getLevel().ordinal() - this.status.getLevel().ordinal();
			if (cmp == 0) cmp = this.resource.compareTo(other.resource);
			if (cmp == 0) cmp = this.status.getMetric().compareTo(other.status.getMetric());
			return(cmp);
		}

		private String resource;
		private String link;
		private Status status;
	}

	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	private Exec exec;
	private Sender sender;
	private BackstopHelpers helpers;

	private Template subjectTemplate;
	private Template bodyTemplate;
}
