/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.tides;

import java.util.logging.Logger;
import com.shutdownhook.toolbox.Easy;

public class App 
{
	public static void main(String[] args) throws Exception {
		
		Easy.setSimpleLogFormat("INFO");

		log.info("yo");
	}

	private final static Logger log = Logger.getLogger(App.class.getName());
}
