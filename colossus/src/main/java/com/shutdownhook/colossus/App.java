//
// APP.JAVA
//

package com.shutdownhook.colossus;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import com.shutdownhook.toolbox.Easy;

public class App 
{
	public static void main(String[] args) throws Exception {
		
		Easy.configureLoggingProperties("@logging.properties");

		List<Project.ProjectResult> results = new ArrayList<Project.ProjectResult>();
		
		try {
			Project project = new Project(args[0], null, null);
			project.run(results, null);
		}
		finally {
			for (Project.ProjectResult result : results) {
				System.out.println(result.toString());
			}
		}
	}

	private final static Logger log = Logger.getLogger(App.class.getName());
}
