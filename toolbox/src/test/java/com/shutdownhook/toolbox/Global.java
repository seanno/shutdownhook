/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.toolbox;

public class Global
{
	public static void init() {

        System.setProperty("java.util.logging.config.file",
						   ClassLoader.getSystemResource("logging.properties").getPath());

		System.setProperty("java.util.logging.SimpleFormatter.format",
						   "[%1$tF %1$tT] [%4$-7s] %5$s %n");
    }
}
