/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.radio.azure.bot;

import java.util.Map;
import java.util.Properties;

import com.microsoft.bot.integration.BotFrameworkHttpAdapter;
import com.microsoft.bot.integration.Configuration;

public class Adapter {

	// +-------------------------+
	// | BotFrameworkHttpAdapter |
	// +-------------------------+

	// Environment-based configurations:
	// 
	//    * MicrosoftAppId
	//    * MicrosoftAppPassword
	//    * ChannelService (default https://botframework.azure.us)
	
	public synchronized static BotFrameworkHttpAdapter getAdapter() {

		if (adapter == null) {

			Configuration config = new EnvConfiguration();
			adapter = new BotFrameworkHttpAdapter(config);
		}
		
		return(adapter);
	}

	private static BotFrameworkHttpAdapter adapter = null;

	// +------------------+
	// | EnvConfiguration |
	// +------------------+

	// Implementation of com.microsoft.bot.integration.Configuration that sources
	// from environment variables as used by Function App Configuration.

	public static class EnvConfiguration implements Configuration
	{
		public EnvConfiguration() {
			props = new Properties();
			for (Map.Entry<String,String> entry : System.getenv().entrySet()) {
				props.setProperty(entry.getKey(), entry.getValue());
			}
		}
		
		public String getProperty(String key) {
			return(props.getProperty(key));
		}
		
		public Properties getProperties() {
			return(props);
		}

		public String[] getProperties(String key) {
			String p = getProperty(key);
			return(p == null ? null : p.split(","));
		}

		private Properties props;
	}

}
