//
// APP.JAVA
//

package com.shutdownhook.life.cli;

import java.io.File;
import java.util.Scanner;
import java.util.logging.Logger;

import com.shutdownhook.life.lifelib.Bitmap;
import com.shutdownhook.life.lifelib.LifeRulesProcessor;
import com.shutdownhook.life.lifelib.Population;
import com.shutdownhook.life.lifelib.Rules;
import com.shutdownhook.life.lifelib.Serializers;
import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.Exec;

import com.google.gson.Gson;

public class App
{
	// +------+
	// | life |
	// +------+

	private static void life(boolean lifeRules, String args[]) throws Exception {

		int generations = (args.length >= 2 ? Integer.parseInt(args[1]) : 10);
		String startState = (args.length >= 3 ? args[2].toLowerCase() : "random");
		Bitmap environment = getEnvironment(startState, args);
		
		Cursor.cls();
		System.out.println(String.format("(1 of %d) %s", generations,
										 Serializers.toString(environment)));

		Rules.RulesProcessor rules =
			Rules.get(lifeRules ? Rules.RulesType.Life: Rules.RulesType.Neighborhood_Moore);
		
		for (int i = 0; i < generations; ++i) {
			environment = Rules.apply(environment, rules);

			Thread.sleep(500);
			Cursor.cls();
			System.out.print(String.format("(%d of %d) ", i+1, generations));
			System.out.println(Serializers.toString(environment));
		}
	}

	private static Bitmap getEnvironment(String state, String[] args) throws Exception {

		switch (state) {
			case "blinker": return(Serializers.fromString(LifeRulesProcessor.BLINKER));
			case "lwss": return(Serializers.fromString(LifeRulesProcessor.LWSS));
			case "pulsar": return(Serializers.fromString(LifeRulesProcessor.PULSAR));
		}

		int dx = (args.length >= 4 ? Integer.parseInt(args[3]) : 5);
		int dy = (args.length >= 5 ? Integer.parseInt(args[4]) : 5);
		Bitmap env = new Bitmap(dx, dy);

		if (state.equals("fill")) {
			env.fill(true);
			return(env);
		}

		if (state.equals("empty")) {
			return(env);
		}

		if (args.length >= 6) env.seed(Long.parseLong(args[5]));
		env.randomize();

		return(env);
	}

	// +--------+
	// | evolve |
	// +--------+

	private static void evolve(String args[]) throws Exception {

		String json = Easy.stringFromSmartyPath(args[1]);
		Integer cycleCount = Integer.parseInt(args[2]);
	
		Population.Config cfg = new Gson().fromJson(json, Population.Config.class);
		Population pop = new Population(cfg, cycleCount);
		
		Exec exec = new Exec();

		for (int i = 0; i < cycleCount; ++i) {

			System.out.println(String.format("running pop cycle %d of %d",
											 i + 1, cycleCount));

			pop.runCycleAsync(exec, false).get();

			System.out.println(pop.getLastMetrics().toString());

			if (i != (cycleCount -1)) {
				System.out.println("reproducing...");
				pop.reproduceAsync(exec).get();
			}
		}

		exec.shutdownPool();
		
		System.out.println("done");
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
			case "h": case "help": help(); break;
			default: System.out.println("huh?"); break;
			case "q": case "quit": return(false);

			case "life": life(true, cmds); break;
			case "evolve": evolve(cmds); break;
			case "nh": life(false, cmds); break;
			case "test": test(cmds); break;
		}

		return(true);
	}
	
	private static void help() {
		System.out.println("life [G] [pattern]: run life pattern for G generations");
		System.out.println("life [G] random DX DY [seed]: run random DX/DY life for G generations (opt seed)");
		System.out.println("life [G] fill DX DY: run filled DX/DY life for G generations (opt seed)");
		System.out.println("quit: quit the program");
		System.out.println("help: this message");
		System.out.println("help: this message");
		System.out.println("(known life patterns: blinker, lwss, pulsar)");
	}

	public static void main(String[] args) throws Exception {

		Easy.configureLoggingProperties("@logging.properties");
		System.out.println(Easy.jvmInfo());

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
