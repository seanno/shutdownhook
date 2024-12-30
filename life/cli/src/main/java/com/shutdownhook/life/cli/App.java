//
// APP.JAVA
//

package com.shutdownhook.life.cli;

import java.util.Scanner;
import java.util.logging.Logger;

import com.shutdownhook.life.lifelib.Bitmap;
import com.shutdownhook.life.lifelib.LifeRules;
import com.shutdownhook.life.lifelib.Rules;
import com.shutdownhook.life.lifelib.Serializers;
import com.shutdownhook.toolbox.Easy;

public class App
{
	// +------+
	// | life |
	// +------+

	private static void life(String args[]) throws Exception {

		int generations = (args.length >= 2 ? Integer.parseInt(args[1]) : 10);
		String startState = (args.length >= 3 ? args[2].toLowerCase() : "random");
		Bitmap environment = getEnvironment(startState, args);
		
		Cursor.cls();
		System.out.println(String.format("(1 of %d) %s", generations,
										 Serializers.toString(environment)));

		LifeRules rules = new LifeRules();
		
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
			case "blinker": return(Serializers.fromString(LifeRules.BLINKER));
			case "lwss": return(Serializers.fromString(LifeRules.LWSS));
			case "pulsar": return(Serializers.fromString(LifeRules.PULSAR));
		}

		int dx = (args.length >= 4 ? Integer.parseInt(args[3]) : 5);
		int dy = (args.length >= 5 ? Integer.parseInt(args[4]) : 5);
		Bitmap env = new Bitmap(dx, dy);

		if (state == "fill") {
			env.fill(true);
			return(env);
		}

		if (args.length >= 6) env.seed(Long.parseLong(args[5]));
		env.randomize();

		return(env);
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
	
	public static void main(String[] args) throws Exception {

		Easy.configureLoggingProperties("@logging.properties");
		Scanner scanner = new Scanner(System.in);
		boolean keepGoing = true;

		while (keepGoing) {

			System.out.print("> ");
			
			String cmd = scanner.nextLine();
			String[] cmds = cmd.toLowerCase().trim().split(" ");

			switch (cmds[0]) {
				case "h":
				case "help":
					help();
					break;

				case "q":
				case "quit":
					keepGoing = false;
					break;

				case "life":
					life(cmds);
					break;

				case "test":
					test(cmds);
					break;

				default:
					System.out.println("huh?");
					break;
			}
			
		}
		
		scanner.close();
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

	private final static Logger log = Logger.getLogger(App.class.getName());
}
