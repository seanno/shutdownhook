//
// APP.JAVA
//

package com.shutdownhook.backstop;

import com.shutdownhook.toolbox.Easy;

public class App
{
	public static void main(String[] args) {

		Easy.setSimpleLogFormat("INFO");

		Backstop backstop = null;
		
		try {
			String json = Easy.stringFromFile(args[0]);
			Backstop.Config cfg = Backstop.Config.fromJson(json);
			backstop = new Backstop(cfg);
			backstop.checkAllAndSend();
		}
		catch (Exception e) {
			// Nyi more here?
			System.err.println(Easy.exMsg(e, "backstop", true));
		}
		finally {
			if (backstop != null) backstop.close();
		}
	}
}
