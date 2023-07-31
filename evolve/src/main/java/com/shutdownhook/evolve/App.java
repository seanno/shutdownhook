/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.evolve;

import com.shutdownhook.toolbox.Easy;

public class App 
{
    public static void main(String[] args) throws Exception {

		if (args.length == 0) {
			System.out.println("First argument must be path to config.");
			return;
		}
		
		Easy.setSimpleLogFormat();
		Server.runSync(Server.Config.fromJson(Easy.stringFromSmartyPath(args[0])));
	}

}
