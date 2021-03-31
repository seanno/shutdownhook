/*
** Read about this code at http://shutdownhook.com.
** No restrictions on use; no assurances or warranties either!
*/

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.URLDecoder;
import java.util.*;
import java.util.logging.Logger;

public class Handlers
{
	// +---------------+
	// | Radio Actions |
	// +---------------+

	public static class GetChannel extends RadioHandler implements HttpHandler
	{
		public GetChannel(Radio radio) { super(radio); }

		public void go(HttpExchange exchange, Map<String,String> params) throws Exception {
			Model.Channel channel = radio.getChannel(params.get("channel"));
			sendJson(exchange, channel.toJson());
		}
	}

	public static class GetPlaylist extends RadioHandler implements HttpHandler
	{
		public GetPlaylist(Radio radio) { super(radio); }

		public void go(HttpExchange exchange, Map<String,String> params) throws Exception {
			Model.Playlist playlist = radio.getPlaylist(params.get("channel"));
			sendJson(exchange, playlist.toJson());
		}
	}

	public static class AddVideo extends RadioHandler implements HttpHandler
	{
		public AddVideo(Radio radio) { super(radio); }

		public void go(HttpExchange exchange, Map<String,String> params) throws Exception {
			radio.addVideo(params.get("channel"), params.get("video"), params.get("who"));
			sendText(exchange, "OK");
		}
	}

	// +-------------------+
	// | Sends Static HTML |
	// +-------------------+

	public static class Static extends RadioHandler implements HttpHandler
	{
		public Static(Radio radio) { super(radio); }
		
		public void go(HttpExchange exchange, Map<String,String> params) throws Exception {
			String html = radio.getStaticHtml(params.get("name"));
			sendHtml(exchange, html == null ? "" : html);
		}
	}

	// +-------------------+
	// | Base with Helpers |
	// +-------------------+

	public abstract static class RadioHandler implements HttpHandler
	{
		protected final static Logger log = Logger.getLogger(RadioHandler.class.getName());

		public RadioHandler(Radio radio) { this.radio = radio; }
		protected Radio radio;

		public abstract void go(HttpExchange exchange, Map<String,String> params) throws Exception;

		public void handle(HttpExchange exchange) {
			log.info(String.format("Request %s: %s",
								   this.getClass().getName(),
								   exchange.getRequestURI()));
			
			try {
				go(exchange, parseQueryString(exchange));
			}
			catch (Exception e) {
				log.severe(String.format("Exception in %s: %s", this.getClass().getName(), e));
				
				StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw);
				e.printStackTrace(pw);
				log.severe(sw.toString());
				
				try { exchange.sendResponseHeaders(500, 0); }
				catch (Exception inner) { /* eat it */ }
			}
			finally {
				exchange.close();
			}
		}

		protected void sendJson(HttpExchange exchange, String data) throws Exception {
			sendString(exchange, data, "application/json");
		}
		
		protected void sendText(HttpExchange exchange, String data) throws Exception {
			sendString(exchange, data, "text/plain");
		}
		
		protected void sendHtml(HttpExchange exchange, String data) throws Exception {
			sendString(exchange, data, "text/html");
		}
		
		protected void sendString(HttpExchange exchange, String data, String contentType) throws Exception {
			byte[] bytes = data.getBytes();
			exchange.getResponseHeaders().add("Content-Type", contentType);
			exchange.sendResponseHeaders(200, bytes.length);
			OutputStream stream = exchange.getResponseBody();
			stream.write(bytes);
		}

		private static Map<String,String> parseQueryString(HttpExchange exchange) {

			Map<String,String> params = new HashMap<String,String>();

			String queryString = exchange.getRequestURI().getRawQuery();
			if (queryString != null && !queryString.isEmpty()) {
				for (String pair : queryString.split("&")) {
					String[] kv = pair.split("=");
					try {
						params.put(URLDecoder.decode(kv[0], "UTF-8"),
								   URLDecoder.decode(kv[1], "UTF-8"));
					}
					catch (UnsupportedEncodingException e) {
						// will never happen
					}
				}
			}

			return(params);
		}
	}

}
