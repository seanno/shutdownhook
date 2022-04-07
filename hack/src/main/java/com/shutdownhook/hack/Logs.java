/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.hack;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class Logs
{
	private static final Logger log = LogManager.getLogger(Logs.class);

	public static void log(String msg) {

		String trust = System.getProperty("com.sun.jndi.ldap.object.trustURLCodebase");
		if (trust == null || !"true".equals(trust.toLowerCase().trim())) {
			System.out.println("needs -Dcom.sun.jndi.ldap.object.trustURLCodebase=true");
			return;
		}
			
		log.error(msg);
	}
}
