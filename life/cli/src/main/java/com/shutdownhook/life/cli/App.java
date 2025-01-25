//
// APP.JAVA
//

package com.shutdownhook.life.cli;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.logging.Logger;

import com.shutdownhook.life.lifelib.Bitmap;
import com.shutdownhook.life.lifelib.Fitness;
import com.shutdownhook.life.lifelib.Graphics;
import com.shutdownhook.life.lifelib.LifeRulesProcessor;
import com.shutdownhook.life.lifelib.Misc;
import com.shutdownhook.life.lifelib.Neighborhood.NeighborhoodType;
import com.shutdownhook.life.lifelib.NeighborhoodRulesProcessor;
import com.shutdownhook.life.lifelib.Population;
import com.shutdownhook.life.lifelib.Rules;
import com.shutdownhook.life.lifelib.Serializers;
import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.Exec;
import com.shutdownhook.toolbox.Template;

import com.google.gson.Gson;

public class App
{
	// +------+
	// | life |
	// +------+

	// run [GENERATIONS] [STARTSTATE] [RULESNAME] [DX] [DY] [SEED]
	// STARTSTATE = random, blinker, lwss, pulsar, combo, fill, empty, random
	// RULESNAME = life or path to rules file
	
	private static void run_html(String args[]) throws Exception {
		runHelper(args, true);
	}

	private static void run(String args[]) throws Exception {
		runHelper(args, false);
	}

	private static void runHelper(String args[], boolean html) throws Exception {

		int generations = (args.length >= 2 ? Integer.parseInt(args[1]) : 10);
		String startState = (args.length >= 3 ? args[2].toLowerCase() : "random");
		String rulesName = (args.length >= 4 ? args[3] : "life");
		Bitmap environment = getEnvironment(startState, args);

		Graphics graphics = new Graphics(new Graphics.Config());
		StringBuilder sbHtml = new StringBuilder();

		if (html) {
			sbHtml.append("const imgs = [\n");
		}
		else {
			Cursor.cls();
			System.out.println(String.format("(1 of %d) %s", generations,
											 Serializers.toString(environment)));
		}

		Rules.RulesProcessor rules = rulesName.equals("life")
			? Rules.get(Rules.RulesType.Life)
			: NeighborhoodRulesProcessor.fromFile(new File(rulesName));
		
		for (int i = 0; i < generations; ++i) {
			
			environment = Rules.apply(environment, rules);
			Thread.sleep(500);
			
			if (html) {
				// dataurl output
				String url = graphics.renderDataURL(environment, "bmp", 3);
				if (i > 0) sbHtml.append(",");
				sbHtml.append("'" + url + "'"); 
			}
			else {
				// console output
				Cursor.cls();
				System.out.print(String.format("(%d of %d) ", i+1, generations));
				System.out.println(Serializers.toString(environment));
			}
		}

		if (html) {
			sbHtml.append("];");
			String templateText = Easy.stringFromResource("animation.html.tmpl");
			Template t = new Template(templateText);
			Map<String,String> tokens = new HashMap<String,String>();
			tokens.put("IMAGES", sbHtml.toString());
			System.out.println(t.render(tokens));
		}

		/*
		if (!rulesName.equals("life")) {
			double fitness = Fitness.compute(environment, Fitness.FitnessType.VStripesCombo);
			System.out.println(String.format("Final VStripesCombo fitness: %.3f", fitness));
		}
		*/
	}

	private static Bitmap getEnvironment(String state, String[] args) throws Exception {

		switch (state) {
			case "blinker": return(Serializers.fromString(LifeRulesProcessor.BLINKER));
			case "lwss": return(Serializers.fromString(LifeRulesProcessor.LWSS));
			case "pulsar": return(Serializers.fromString(LifeRulesProcessor.PULSAR));
			case "combo": return(Serializers.fromString(LifeRulesProcessor.COMBO));
		}

		int dx = (args.length >= 5 ? Integer.parseInt(args[4]) : 5);
		int dy = (args.length >= 6 ? Integer.parseInt(args[5]) : 5);
		Bitmap env = new Bitmap(dx, dy);

		if (state.equals("fill")) {
			env.fill(true);
			return(env);
		}

		if (state.equals("empty")) {
			return(env);
		}

		if (state.equals("single")) {
			env.set(dx / 2, dy / 2, true);
			return(env);
		}

		if (args.length >= 7) env.seed(Long.parseLong(args[6]));
		env.randomize();

		return(env);
	}

	// +--------+
	// | evolve |
	// +--------+

	private static void evolve(String args[]) throws Exception {

		String json = Easy.stringFromSmartyPath(args[1]);
		Integer cycleCount = Integer.parseInt(args[2]);
		Integer runs = (args.length >= 4 ? Integer.parseInt(args[3]) : 1);
	
		Population.Config cfg = new Gson().fromJson(json, Population.Config.class);
		Exec exec = new Exec();
		
		for (int i = 0; i < runs; ++i) {
			evolveOne(cfg, cycleCount, i+1, exec);
		}

		exec.shutdownPool();
		
		System.out.println("done");
	}
	
	private static void evolveOne(Population.Config cfg,
								  int cycleCount,
								  int runNumber,
								  Exec exec) throws Exception {

		Population pop = new Population(cfg, cycleCount);
		int effectiveCycleCount = cycleCount;

		for (int i = 0; i < effectiveCycleCount; ++i) {

			if (checkShortCircuit()) {
				effectiveCycleCount = i + 1;
				pop.updateCycleCount(effectiveCycleCount);
			}
			
			System.out.print(String.format("run %d: cycle %d of %d... ",
											 runNumber, i + 1, effectiveCycleCount));

			pop.runCycleAsync(exec, false).get();

			System.out.println(pop.getLastMetrics().toString());

			if (i != (effectiveCycleCount - 1)) {
				pop.reproduceAsync(exec).get();
			}
		}
	}

	private static boolean checkShortCircuit() {
		File shortCircuitFile = new File("__shortcircuit.txt");
		return(shortCircuitFile.exists());
	}
	
	// +--------------------+
	// | visualizeRules     |
	// | visualizeRulesHtml |
	// +--------------------+

	private static void visualizeRules(String[] cmds) throws Exception {

		File file = new File(cmds[1]);
		NeighborhoodRulesProcessor rules = NeighborhoodRulesProcessor.fromFile(file);

		Misc.visualizeRules(rules);
	}
	
	private static void visualizeRulesHtml(String[] cmds) throws Exception {

		File file = new File(cmds[1]);
		NeighborhoodRulesProcessor rules = NeighborhoodRulesProcessor.fromFile(file);

		String templateText = Easy.stringFromResource("rules.html.tmpl");
		Template t = new Template(templateText);
		Map<String,String> tokens = new HashMap<String,String>();
		tokens.put("RULES", Misc.visualizeRulesHtml(rules));
		System.out.println(t.render(tokens));
	}
	
	// +-----------+
	// | vnToMoore |
	// +-----------+

	private static void vnToMoore(String[] cmds) throws Exception {

		File vnFile = new File(cmds[1]);
		File mooreFile = new File(cmds[2]);

		System.out.println(String.format("Converting VN rules %s to Moore rules %s",
										 vnFile.getAbsolutePath(),
										 mooreFile.getAbsolutePath()));
		
		NeighborhoodRulesProcessor vnRules = NeighborhoodRulesProcessor.fromFile(vnFile);
		if (!vnRules.getNeighborhoodType().equals(NeighborhoodType.VonNeumann)) {
			throw new Exception("input rules wrong type");
		}

		NeighborhoodRulesProcessor mooreRules = Misc.convertRules_VN2Moore(vnRules);
		mooreRules.toFile(mooreFile);
	}

	// +------+
	// | test |
	// +------+

	private static void test(String[] cmds) throws Exception {
		Cursor.cls();
		Cursor.set(0,0);
		System.out.print("yo");
		Cursor.set(10,10);
		System.out.print("dawg");
	}

	// +------------+
	// | Entrypoint |
	// +------------+

	private static boolean handleCommand(String[] cmds) throws Exception {
		
		switch (cmds[0]) {
			default: System.out.println("huh?"); break;
			case "q": case "quit": return(false);

			case "run": run(cmds); break;
			case "run-html": run_html(cmds); break;
			case "evolve": evolve(cmds); break;
			case "vn-to-moore": vnToMoore(cmds); break;
			case "viz": visualizeRules(cmds); break;
			case "viz-html": visualizeRulesHtml(cmds); break;
			case "test": test(cmds); break;
		}

		return(true);
	}
	
	public static void main(String[] args) throws Exception {

		Easy.configureLoggingProperties("@logging.properties");
		System.err.println(Easy.jvmInfo());

		Scanner scanner = new Scanner(System.in);

		if (args.length > 0) {
			handleCommand(args);
			return;
		}
			
		boolean keepGoing = true;
		while (keepGoing) {
			System.out.print("> ");
			String cmd = scanner.nextLine();
			keepGoing = handleCommand(cmd.toLowerCase().trim().split(" "));
		}
		
		scanner.close();
	}

	private final static Logger log = Logger.getLogger(App.class.getName());
}
