/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.hack;

public class App 
{
	public static void main(String[] args) throws Exception {

		switch (args[0].toLowerCase().trim()) {
		    case "log": Logs.log(args[1]); break;
		    case "sqlgood": Sql.inject(args[1], args[2], false); break;
		    case "sqlbad": Sql.inject(args[1], args[2], true); break;
		    default: System.err.println("huh?"); break;
		}
	}

}
