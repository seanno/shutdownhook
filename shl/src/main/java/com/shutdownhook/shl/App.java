/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.shl;

import java.util.logging.Logger;
import com.shutdownhook.toolbox.Easy;

public class App 
{
	public static void main(String[] args) throws Exception {
		
		Easy.setSimpleLogFormat("INFO");

		String configJson = Easy.stringFromSmartyPath(args[0]);
		SHLServer.Config cfg = SHLServer.Config.fromJson(configJson);

		SHLServer server = new SHLServer(cfg);
		server.runSync();
	}

	private final static Logger log = Logger.getLogger(App.class.getName());
}
