/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.toolbox;

import java.io.Closeable;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class TextWebSocket implements Closeable
{
	// +----------------+
	// | Config & Setup |
	// +----------------+

	// Note this is really basic at the mo ... haven't done a lot 
	// of work with WebSockets; will evolve as I learn more!
	// Hiding away most of the asyncness because f that noise.
	
	public static class Config
	{
		public Integer ConnectTimeoutSeconds = 30;
		public Integer SendTimeoutSeconds = 30;
		public Integer CloseTimeoutSeconds = 5;
		public Boolean LogContent = false;
	}

	public TextWebSocket(String url, Config cfg) throws Exception {
		
		this.cfg = cfg;

		log.info("Connecting to url: " + url);

		ws = HttpClient
			.newHttpClient()
			.newWebSocketBuilder()
			.connectTimeout(Duration.ofSeconds(cfg.ConnectTimeoutSeconds))
			.buildAsync(new URI(url), new Receiver(this))
			.join();
	}

	public void close() {
		close(WebSocket.NORMAL_CLOSURE, "");
	}

	public void close(int statusCode, String msg) {
		
		if (ws != null) {

			log.fine(String.format("Closing with status = %d, msg = %s",
								   statusCode, msg));
			
			try {
				ws.sendClose(statusCode, msg)
					.orTimeout(cfg.CloseTimeoutSeconds, TimeUnit.SECONDS)
					.join();
			}
			catch (Exception e) {
				log.severe("Exception closing websocket: " + e.toString());
			}

			ws = null;
		}
	}

	// +---------+
	// | Methods |
	// +---------+

	public void send(String message) throws Exception {
		
		if (cfg.LogContent)
			log.info("Sending message: " + message);
		
		ws.sendText(message, true)
			.orTimeout(cfg.SendTimeoutSeconds, TimeUnit.SECONDS)
			.join();
	}

	public void receive(String message) throws Exception {
		// override me
	}

	public void error(Throwable error) throws Throwable {
		// override me
	}

	// +----------+
	// | Receiver |
	// +----------+

	public class Receiver implements WebSocket.Listener
	{
		public Receiver(TextWebSocket tws) {
			this.tws = tws;

			sb = new StringBuilder();
			accumulatingFuture = null;
			resetAccumulator();
		}

		public CompletionStage<?> onText(WebSocket ws, CharSequence message, boolean last) {

			log.fine(String.format("Handling socket text (cch = %d, last = %s)",
									message.length(), last));
			
			sb.append(message);
			ws.request(1);

			if (last) {

				if (cfg.LogContent) log.info("Message Received: " + sb.toString());
				
				try {
					receive(sb.toString());
				}
				catch (Exception e) {
					log.warning(String.format("ImplEx handling message (%s)",
											  e.toString()));
				}

				return(resetAccumulator());
			}

			return(accumulatingFuture);
		}
		
		public void onError(WebSocket ws, Throwable error) {

			log.info("Handling socket error: " + error.toString());

			tws.ws = null; // it's screwed so don't touch it any more
			
			try {
				tws.error(error);
			}
			catch (Throwable e) {
				log.warning(String.format("ImplEx handling error (input = %s; e = %s)",
										  error.toString(), e.toString()));
			}
		}

		private CompletableFuture<?> resetAccumulator() {

			sb.setLength(0);
			if (accumulatingFuture != null) accumulatingFuture.complete(null);

			CompletableFuture<?> completedFuture = accumulatingFuture;
			accumulatingFuture = new CompletableFuture<>();
			
			return(completedFuture);
		}
		
		private TextWebSocket tws;
		private StringBuilder sb;
		private CompletableFuture<?> accumulatingFuture;
	}

	// +-------------------+
	// | Helpers & Members |
	// +-------------------+

	private Config cfg;
	private WebSocket ws;
	
	private final static Logger log = Logger.getLogger(WebSocket.class.getName());
}
