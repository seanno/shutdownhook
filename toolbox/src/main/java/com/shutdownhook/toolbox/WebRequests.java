/*
** Read about this code at http://shutdownhook.com.
** No restrictions on use; no assurances or warranties either!
*/

package com.shutdownhook.toolbox;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.lang.StringBuilder;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class WebRequests implements Closeable
{
	public static class Config
	{
		int ThreadCount = 0; // 0 = use an on-demand pool, else specific fixed count
		int ShutdownWaitSeconds = 30;
		int TimeoutMillis = 60000;
		boolean FollowRedirects = true;
		boolean LogResponse = true;
		int CchReadBuffer = 4096;
	}

	public static class Response
	{
		int Status;
		String StatusText;
		Exception Ex;
		String Body;

		public boolean successful() {
			return(Ex == null && Status >= 200 && Status <= 300);
		}

		protected void setException(Exception e) {
			Status = 500;
			StatusText = "Exception";
			Ex = e;
		}
	}

	public WebRequests(Config cfg) {

		this.cfg = cfg;
		this.syncFutures = new HashSet<Future>();

		this.pool = (cfg.ThreadCount == 0
					 ? Executors.newCachedThreadPool()
					 : Executors.newFixedThreadPool(cfg.ThreadCount));
	}

	public void close() {

		cancelSyncFutures();
		
		// note thanks to the nature of HttpUrlConnection, there are cases where
		// threads just aren't going to respond to these signals. This is crappy
		// but not the end of the world becuase the Futures handle their own
		// timeouts and that will get control back to application code. Obs a
		// better choice would be to use JDK11 HttpClient, but trying to keep this
		// class viable in older versions.

		try {
			pool.shutdown();
			pool.awaitTermination(cfg.ShutdownWaitSeconds / 2, TimeUnit.SECONDS);
			pool.shutdownNow();
			pool.awaitTermination(cfg.ShutdownWaitSeconds / 2, TimeUnit.SECONDS);
		}
		catch (InterruptedException e) {
			// oh well
		}
	}

	public Response get(String url) {
		return(get(url, null));
	}

	public Response get(String url, Map<String,String> queryParams) {
		
		CompletableFuture<Response> future = getAsync(url, queryParams);
		Response response = null;

		rememberSyncFuture(future);
		
		try {
			response = future.get(cfg.TimeoutMillis, TimeUnit.MILLISECONDS);
		}
		catch (Exception e) {
			response.setException(e);
		}
		finally {
			forgetSyncFuture(future);
		}

		return(response);
	}

	public CompletableFuture<Response> getAsync(String url, Map<String,String> queryParams) {
		
		CompletableFuture<Response> future = new CompletableFuture<Response>();

		pool.submit(() -> {

			HttpURLConnection conn = null;
			InputStreamReader reader = null;
			BufferedReader buffered = null;

			Response response = new Response();
			Instant started = Instant.now();

			String fullUrl = addQueryParams(url, queryParams);

			try {
				conn = (HttpURLConnection) (new URL(fullUrl).openConnection());
				conn.setRequestMethod("GET");
				conn.setFollowRedirects(cfg.FollowRedirects);
				conn.setConnectTimeout(cfg.TimeoutMillis);
				conn.setReadTimeout(cfg.TimeoutMillis);

				response.Status = conn.getResponseCode();
				response.StatusText = conn.getResponseMessage();

				InputStream stm = getInputStreamReally(conn);
				
				if (stm != null) {
					
					StringBuilder sb = new StringBuilder();
					reader = new InputStreamReader(stm);
					buffered = new BufferedReader(reader);

					char[] rgch = new char[cfg.CchReadBuffer];
					int cch = -1;

					while ((cch = buffered.read(rgch, 0, rgch.length)) != -1) {
						sb.append(rgch, 0, cch);
					}

					response.Body = sb.toString();
				}
			}
			catch (Exception e) {
				response.setException(e);
			}
			finally {
				try {
					if (buffered != null) buffered.close();
					if (reader != null) reader.close();
					if (conn != null) conn.disconnect();
				}
				catch (Exception e2) {
					log.severe("Exception closing WebRequest: " + e2.toString());
				}
			}

			if (cfg.LogResponse) {
			
				long elapsed = ChronoUnit.MILLIS.between(started, Instant.now());

				if (response.Ex == null) {
					String msg = String.format("WebRequest for %s: %d/%s, %d bytes (%d ms)",
											   fullUrl, response.Status, response.StatusText,
											   response.Body == null ? 0 : response.Body.length(),
											   elapsed);
					log.info(msg);
				}
				else {
					String msg = String.format("WebRequest for %s: EXCEPTION %s (%d ms)",
											   fullUrl, response.Ex.toString(), elapsed);
					log.warning(msg);
				}
			}

			future.complete(response);
		});
		
		return(future);
	}

	private InputStream getInputStreamReally(HttpURLConnection conn) {
		
		InputStream stm = null;
		
		try {
			stm = conn.getInputStream();
		}
		catch (IOException e) {
			stm = conn.getErrorStream();
		}

		return(stm);
	}

	private String addQueryParams(String baseUrl, Map<String,String> queryParams) {

		if (queryParams == null || queryParams.size() == 0)
			return(baseUrl);
		
		StringBuilder sb = new StringBuilder();
		sb.append(baseUrl);
		boolean firstParam = (baseUrl.indexOf("?") == -1);
		
		for (String queryParam : queryParams.keySet()) {

			sb.append(firstParam ? "?" : "&"); firstParam = false;
			sb.append(queryParam).append("=");
			sb.append(URLEncoder.encode(queryParams.get(queryParam), StandardCharsets.UTF_8));
		}

		return(sb.toString());
	}

	private synchronized void rememberSyncFuture(Future future) {
		syncFutures.add(future);
	}

	private synchronized void forgetSyncFuture(Future future) {
		syncFutures.remove(future);
	}

	private synchronized void cancelSyncFutures() {
		for (Future future : syncFutures) future.cancel(true);
	}

	public static void main(String[] args) {
		WebRequests requests = new WebRequests(new Config());
		Response response = requests.get(args[0]);
		if (response.successful()) System.out.println(response.Body);
		requests.close();
	}

	private Config cfg;
	private ExecutorService pool;
	private Set<Future> syncFutures;

	private final static Logger log = Logger.getLogger(WebRequests.class.getName());
}
