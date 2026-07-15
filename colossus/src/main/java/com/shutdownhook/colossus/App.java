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
		
		if (args.length < 1) {
			System.out.println("Usage java -cp [COLOSSUS.JAR] com.shutdownhook.colossus.App \\" +
							   "[PROJECT_PATH] (TARGET_PROJECT) (PROMPT_OVERRIDE)");
			return;
		}
		
		String projectPath = args[0];
		String targetProject = (args.length > 1 ? args[1] : null);
		String promptOverride = (args.length > 2 ? args[2] : null);
		
		Easy.configureLoggingProperties("@logging.properties");

		
		List<Project.ProjectResult> results = new ArrayList<Project.ProjectResult>();
		
		try {
			Project project = new Project(projectPath, null, null);
			project.run(results, null, targetProject, promptOverride);
		}
		finally {
			for (Project.ProjectResult result : results) {
				System.out.println(result.toString());
			}
		}
	}

	private final static Logger log = Logger.getLogger(App.class.getName());
}
