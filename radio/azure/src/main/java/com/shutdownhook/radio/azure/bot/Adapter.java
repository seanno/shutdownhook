/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.radio.azure.bot;

import java.util.HashMap;
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
	
	public synchronized static BotFrameworkHttpAdapter getAdapter(String configSuffix) {

		BotFrameworkHttpAdapter adapter = adapters.get(configSuffix);
		
		if (adapter == null) {

			Configuration config = new EnvConfiguration(configSuffix);
			adapter = new BotFrameworkHttpAdapter(config);

			adapters.put(configSuffix, adapter);
		}
		
		return(adapter);
	}

	private static Map<String,BotFrameworkHttpAdapter> adapters =
		new HashMap<String, BotFrameworkHttpAdapter>();

	// +------------------+
	// | EnvConfiguration |
	// +------------------+

	// Implementation of com.microsoft.bot.integration.Configuration that sources
	// from environment variables as used by Function App Configuration.
	//
	// configSuffix lets us serve multiple bots from the same process. It's a little
	// tricky since we have to return a props object we pre-process the names
	// and only include ones with the configSuffix if it's provided. 

	public static class EnvConfiguration implements Configuration
	{
		public EnvConfiguration(String configSuffix) {

			props = new Properties();
			for (Map.Entry<String,String> entry : System.getenv().entrySet()) {

				String processedKey = processKey(entry.getKey(), configSuffix);
				if (processedKey != null) props.setProperty(processedKey, entry.getValue());
			}
		}

		private String processKey(String inputKey, String configSuffix) {
			
			if (configSuffix == null || configSuffix.isEmpty()) {
				return(inputKey);
			}

			if (!inputKey.endsWith(configSuffix)) {
				return(null);
			}

			return(inputKey.substring(0, inputKey.length() - configSuffix.length()));
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
