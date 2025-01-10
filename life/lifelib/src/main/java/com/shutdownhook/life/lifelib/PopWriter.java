/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.life.lifelib;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.Exec;
import com.shutdownhook.toolbox.Template;

import com.shutdownhook.life.lifelib.Organism.Cycle;

public class PopWriter
{
	// +----------------+
	// | Config & Setup |
	// +----------------+


	public static class Config
	{
		public String BasePath;

		public String IndexFileName = "index.html";
		public String ConfigFileName = "config.json";
		public String PopulationFileNameFormat = "population-%03d.html";
		public String OrganismFileNameFormat = "organism-%s.html";
		
		public String PopulationTemplate = "@population.html.tmpl";
		public Integer PopulationColumns = 8;

		public String OrganismPrefixTemplate = "@organism-prefix.html.tmpl";
		public String OrganismSuffixTemplate = "@organism-suffix.html.tmpl";
		public Integer OrganismColumns = 8;
		
		public String OrganismDivTemplate = "@organismDiv.html.tmpl";

		public String IndexTemplate = "@index.html.tmpl";
	}

	public PopWriter(Population pop, Config cfg) throws Exception {

		this.cfg = cfg;
		this.pop = pop;
		
		String indexTemplateText = Easy.stringFromSmartyPath(cfg.IndexTemplate);
		this.indexTemplate = new Template(indexTemplateText);

		String populationTemplateText = Easy.stringFromSmartyPath(cfg.PopulationTemplate);
		this.popTemplate = new Template(populationTemplateText);

		String orgDivTemplateText = Easy.stringFromSmartyPath(cfg.OrganismDivTemplate);
		this.orgDivTemplate = new Template(orgDivTemplateText);

		String orgPrefixTemplateText = Easy.stringFromSmartyPath(cfg.OrganismPrefixTemplate);
		String orgSuffixTemplateText = Easy.stringFromSmartyPath(cfg.OrganismSuffixTemplate);
		this.orgPrefixTemplate = new Template(orgPrefixTemplateText);
		this.orgSuffixTemplate = new Template(orgSuffixTemplateText);
		
		this.graphics = new Graphics(new Graphics.Config());
	}
	
	// +-------------+
	// | writeConfig |
	// +-------------+

	public void writeConfig() throws Exception {
		File file = getConfigFile();
		String json = new Gson().toJson(pop.getConfig());
		Easy.stringToFile(file.getAbsolutePath(), json);
	}

	// +------------+
	// | writeIndex |
	// +------------+

	public void writeIndex() throws Exception {
		
		File file = getIndexFile();

		int cycleCount = pop.getCycleCount();
		
		Map tokens = new HashMap<String,String>();
		tokens.put("NAME", pop.getName());
		tokens.put("CYCLE_COUNT", Integer.toString(cycleCount));
		tokens.put("CONFIG_URL", getConfigFile().getName());
		tokens.put("FIRST_CYCLE_URL", getPopulationFile(1).getName());
		tokens.put("LAST_CYCLE_URL", getPopulationFile(cycleCount).getName());
		
		String json = new GsonBuilder().setPrettyPrinting().create().toJson(pop.getConfig());
		tokens.put("CONFIG_JSON", json);
		
		String html = indexTemplate.render(tokens, new Template.TemplateProcessor() {

			public boolean repeat(String[] args, int counter) {

				if (cycleNum > cycleCount) return(false);

				tokens.put("CYCLE_URL", getPopulationFile(cycleNum).getName());
				tokens.put("CYCLE_NUM", Integer.toString(cycleNum));
				
				++cycleNum;
				return(true);
			}

			private int cycleNum = 1;
		});

		Easy.stringToFile(file.getAbsolutePath(), html);
	}

	// +-----------------+
	// | writePopulation |
	// +-----------------+

	public void writePopulation() throws Exception {

		File file = getPopulationFile(pop.getAge());
		
		Map tokens = new HashMap<String,String>();
		tokens.put("NAME", pop.getName());
		tokens.put("AGE", Integer.toString(pop.getAge()));
		tokens.put("CYCLE_COUNT", Integer.toString(pop.getCycleCount()));
		tokens.put("FITNESS_METRICS", pop.getLastMetrics().toString());

		tokens.put("PREV_LINK", "");
		tokens.put("NEXT_LINK", "");
		
		if (pop.getAge() > 1) {
			String prevName = getPopulationFile(pop.getAge() - 1).getName();
			tokens.put("PREV_LINK", "<a href='" + prevName + "'>previous</a>");
		}

		if (pop.getAge() < pop.getCycleCount()) {
			String nextName = getPopulationFile(pop.getAge() + 1).getName();
			tokens.put("NEXT_LINK", "<a href='" + nextName + "'>next</a>");
		}

		tokens.put("COL_TEMPLATE", repeatString("auto", cfg.PopulationColumns));

		String html = popTemplate.render(tokens, new Template.TemplateProcessor() {
				
			public boolean repeat(String[] args, int counter) {

				if (iorg == pop.getOrganisms().length) return(false);

				Organism org = pop.getOrganisms()[iorg];
				tokens.put("ORG_DIV", getOrganismDiv(org, row, col));

				++col;
				if (col > cfg.PopulationColumns) {
					col = 1;
					++row;
				}

				++iorg;
				return(true);
			}

			private int iorg = 0;
			private int row = 1;
			private int col = 1;
		});

		Easy.stringToFile(file.getAbsolutePath(), html);
	}

	// +---------------------+
	// | writeOrganismPrefix |
	// | writeOrganismCycle  |
	// | writeOrganismSuffix |
	// +---------------------+

	public void writeOrganismPrefix(Organism org, Organism parent1, Organism parent2)
		throws Exception {

		File file = getOrganismFile(org);

		boolean haveParents = (parent1 != null || parent2 != null);
		String parent1Div = (parent1 == null ? "" : getOrganismDiv(parent1, 1, 1));
		String parent2Div = (parent2 == null ? "" : getOrganismDiv(parent2, 1, 2));
		
		Map tokens = new HashMap<String,String>();
		tokens.put("ID", org.getId());
		tokens.put("COL_TEMPLATE", repeatString("auto", cfg.OrganismColumns));
		tokens.put("HAVE_PARENTS", Boolean.toString(haveParents));
		tokens.put("PARENT1_ORG_DIV", parent1Div);
		tokens.put("PARENT2_ORG_DIV", parent2Div);
		
		String html = orgPrefixTemplate.render(tokens);
		Easy.stringToFile(file.getAbsolutePath(), html);
	}

	public void writeOrganismCycle(Organism org) throws Exception {

		File file = getOrganismFile(org);

		int row = (org.getAge() / cfg.OrganismColumns) + 1;
		int col = (org.getAge() % cfg.OrganismColumns) + 1;

		String html = getOrganismDiv(org, row, col);
		Easy.appendStringToFile(file.getAbsolutePath(), html);
	}

	public void writeOrganismSuffix(Organism org) throws Exception {

		File file = getOrganismFile(org);

		Map tokens = new HashMap<String,String>();
		// no tokens yet

		String html = orgSuffixTemplate.render(tokens);
		Easy.appendStringToFile(file.getAbsolutePath(), html);
	}
	
	// +----------------+
	// | getOrganismDiv |
	// +----------------+

	public String getOrganismDiv(Organism org, int row, int col) {

		try {
			Cycle cycle = org.getLastCycle();
			String img = graphics.renderDataURL(cycle.EndState);

			Map<String,String> tokens = new HashMap<String,String>();
			tokens.put("AGE", Integer.toString(org.getAge()));
			tokens.put("FITNESS", String.format("%.3f", cycle.Fitness));
			tokens.put("IMG", img);
			tokens.put("ROW", Integer.toString(row));
			tokens.put("COL", Integer.toString(col));
			tokens.put("URL", getOrganismFile(org).getName());
			
			return(orgDivTemplate.render(tokens));
		}
		catch (Exception e) {
			log.severe(Easy.exMsg(e, "getOrganismDiv", true));
			return("");
		}
	}

	// +-------+
	// | files |
	// +-------+

	private File getPopulationFile(int age) {
		String name = String.format(cfg.PopulationFileNameFormat, age);
		return(new File(getWriteDir(), name));
	}

	private File getOrganismFile(Organism org) {
		String name = String.format(cfg.OrganismFileNameFormat, org.getId());
		return(new File(getWriteDir(), name));
	}

	private File getConfigFile() {
		return(new File(getWriteDir(), cfg.ConfigFileName));
	}

	private File getIndexFile() {
		return(new File(getWriteDir(), cfg.IndexFileName));
	}
	
	private File getWriteDir() {

		if (writeDir != null) return(writeDir);

		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");
		String name = pop.getName() + "-" + LocalDateTime.now().format(dtf);
		writeDir = uniquify(new File(cfg.BasePath), name, "");
		
		writeDir.mkdirs();
		return(writeDir);
	}

	private File uniquify(File base, String name, String suffix) {

		base.mkdirs();
		
		File f = new File(base, name + suffix);
		
		int uniquifier = 0;
		while (f.exists()) {
			f = new File(base, String.format("%s-%d%s", name, ++uniquifier, suffix));
		}

		return(f);
	}
	
	// +---------+
	// | helpers |
	// +---------+

	private static String repeatString(String str, int count) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < count; ++i) {
			if (i > 0) sb.append(" ");
			sb.append(str);
		}
		return(sb.toString());
	}
		
	private static String doubleString(double d) {
		if (d == Double.MAX_VALUE) return("inf");
		if (d == Double.MIN_VALUE) return("inf");
		return(String.format("%.3f", d));
	}

	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	private Population pop;

	private Template indexTemplate;
	private Template popTemplate;
	private Template orgPrefixTemplate;
	private Template orgSuffixTemplate;
	private Template orgDivTemplate;
	
	private Graphics graphics;

	private File writeDir;
	
	private final static Logger log = Logger.getLogger(PopWriter.class.getName());
}
