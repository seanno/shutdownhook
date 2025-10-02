//
// APP.JAVA
//

package com.shutdownhook.ants;

import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.logging.Logger;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.Exec;
import com.shutdownhook.toolbox.Template;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class App
{
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
	
	// +------+
	// | ants |
	// +------+

	private final static String IMAGES_TOKEN = "{{IMAGES_CSV}}";
	private final static String CONFIG_TOKEN = "{{CONFIG}}";
	
	private static void ants(String[] cmds) throws Exception {

		Gson gson = new GsonBuilder().setPrettyPrinting().create();

		int cycles = Integer.parseInt(cmds[1]);
		String config = cmds[2];
		String output = cmds[3];

		String json = Easy.stringFromFile(config);
		AntWorld.Config cfg = gson.fromJson(json, AntWorld.Config.class);
		if (cfg.Seed == null) cfg.Seed = System.nanoTime();
		
		String html = Easy.stringFromResource("animation.html.tmpl");
		html = html.replace(CONFIG_TOKEN, gson.toJson(cfg));
		
		int ichToken = html.indexOf(IMAGES_TOKEN);
		
		FileWriter writer = new FileWriter(output);
		writer.write(html, 0, ichToken);
		
		AntWorld world = new AntWorld(cfg);
		for (int i = 0; i < cycles; ++i) {
			world.cycle();
			fileWrite(writer, i == 0 ? "'" : ",'");
			fileWrite(writer, world.renderDataURL());
			fileWrite(writer, "'");
		}

		writer.write(html, ichToken + IMAGES_TOKEN.length(),
					 html.length() - (ichToken + IMAGES_TOKEN.length()));

		writer.flush();
		writer.close();
	}

	private static void fileWrite(FileWriter writer, String s) throws Exception {
		writer.write(s, 0, s.length());
	}

	// +------------+
	// | Entrypoint |
	// +------------+

	private static boolean handleCommand(String[] cmds) throws Exception {
		
		switch (cmds[0]) {
			default: System.out.println("huh?"); break;
			case "q": case "quit": return(false);

			case "test": test(cmds); break;
			case "ants": ants(cmds); break;
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
