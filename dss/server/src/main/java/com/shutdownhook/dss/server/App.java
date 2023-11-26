/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.dss.server;

import java.util.logging.Logger;
import com.shutdownhook.toolbox.Easy;

public class App 
{
	public static void main(String[] args) throws Exception {

		if (args.length < 1) {
			System.err.println("java ... [path to config]");
			return;
		}

		Easy.setSimpleLogFormat("INFO");

		String json = Easy.stringFromSmartyPath(args[0]);
		Server.Config cfg = Server.Config.fromJson(json);

		Server server = new Server(cfg);

		try { server.runSync(); }
		finally { server.close(); }
	}

	private final static Logger log = Logger.getLogger(App.class.getName());
}
