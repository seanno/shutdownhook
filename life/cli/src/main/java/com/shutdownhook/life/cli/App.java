//
// APP.JAVA
//

package com.shutdownhook.life.cli;

import java.util.Scanner;
import java.util.logging.Logger;

import com.shutdownhook.life.lifelib.Bitmap3D;
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

		int dx = (args.length >= 2 ? Integer.parseInt(args[1]) : 5);
		int dy = (args.length >= 3 ? Integer.parseInt(args[2]) : 5);
		int generations = (args.length >= 4 ? Integer.parseInt(args[3]) : 5);
		
		Bitmap3D environment = new Bitmap3D(dx, dy);
		environment.randomize(null);
		System.out.println("-------- INITIAL STATE");
		System.out.println(Serializers.toString(environment));
		
		LifeRules rules = new LifeRules();
		
		for (int i = 0; i < generations; ++i) {
			environment = Rules.apply(environment, rules);
			System.out.println(String.format("-------- GENERATION %d", i+1));
			System.out.println(Serializers.toString(environment));
		}
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

				default:
					System.out.println("huh?");
					break;
			}
			
		}
		
		scanner.close();
	}

	private static void help() {
		System.out.println("life X Y Z: run the game of life on an X/Y grid for Z generations");
		System.out.println("quit: quit the program");
		System.out.println("help: this message");
	}

	private final static Logger log = Logger.getLogger(App.class.getName());
}
