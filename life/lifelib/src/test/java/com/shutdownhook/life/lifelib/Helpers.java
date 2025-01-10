//
// HELPERS.JAVA

package com.shutdownhook.life.lifelib;

public class Helpers
{
	// +------+
	// | init |
	// +------+
	
	public static void init() {

        System.setProperty("java.util.logging.config.file",
						   ClassLoader.getSystemResource("test-logging.properties").getPath());

		System.setProperty("java.util.logging.SimpleFormatter.format",
						   "[%1$tF %1$tT] [%4$-7s] %5$s %n");
    }

	static { Helpers.init(); }

}
