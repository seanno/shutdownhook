//
// APP.JAVA
//

package com.shutdownhook.colossus;

import java.util.logging.Logger;
import com.shutdownhook.toolbox.Easy;

public class App 
{
	public static void main(String[] args) throws Exception {
		Project project = new Project(args[0], null);
		project.run();
	}

	private final static Logger log = Logger.getLogger(App.class.getName());
}
