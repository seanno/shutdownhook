/*
** Read about this code at http://shutdownhook.com.
** No restrictions on use; no assurances or warranties either!
*/

package com.shutdownhook.toolbox;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.StringBuilder;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public class WebRequests implements Closeable
{
	public static class Config
	{
		public int ThreadCount = 0; // 0 = use an on-demand pool, else specific fixed count
		public int ShutdownWaitSeconds = 30;
		public int TimeoutMillis = 60000;
		public boolean FollowRedirects = true;
		public boolean LogResponse = true;
		public int CchReadBuffer = 4096;

		// If provided, this cert is added to the trust chain for requests.
		// It is additive, not a replacement for the built-in trusted roots
		public String TrustedCertificateFile;
	}

	public static class Params
	{
		public Map<String,String> QueryParams;
		public Map<String,String> Headers;
		public String MethodOverride;
		public String Body;
		
		public void addQueryParam(String name, String val) {
			if (QueryParams == null) QueryParams = new HashMap<String,String>();
			QueryParams.put(name, val);
		}

		public void addHeader(String name, String val) {
			if (Headers == null) Headers = new HashMap<String,String>();
			Headers.put(name, val);
		}

		public void setContentType(String val) {
			addHeader("Content-Type", val);
		}
		
		public void setAccept(String val) {
			addHeader("Accept", val);
		}

		public void setBasicAuth(String user, String password) {
			addHeader("Authorization", "Basic " + Easy.base64Encode(user + ":" + password));
		}

		public void setForm(Map<String,String> form) {
			setContentType("application/x-www-form-urlencoded");
			Body = Easy.urlFormatQueryParams(form);
		}
	}

	public static class Response
	{
		public int Status;
		public String StatusText;
		public Exception Ex;
		public String Body;
		public Map<String,List<String>> Headers;

		public boolean successful() {
			return(Ex == null && Status >= 200 && Status <= 300);
		}

		protected void setException(Exception e) {
			Status = 500;
			StatusText = "Exception";
			Ex = e;
		}
	}

	public WebRequests(Config cfg) throws Exception {

		this.cfg = cfg;
		this.syncFutures = new HashSet<Future>();

		this.pool = (cfg.ThreadCount == 0
					 ? Executors.newCachedThreadPool()
					 : Executors.newFixedThreadPool(cfg.ThreadCount));

		if (cfg.TrustedCertificateFile != null) {
			ExtendedTrustManager etm = new ExtendedTrustManager(cfg.TrustedCertificateFile);
			SSLContext sslContext = SSLContext.getInstance("TLS");
			sslContext.init(null, new TrustManager[] { etm }, null);
			sslSocketFactory = sslContext.getSocketFactory();
		}
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

	public Response fetch(String url) {
		return(fetch(url, new Params()));
	}

	public Response fetch(String url, Params params) {

		CompletableFuture<Response> future = fetchAsync(url, params);
		Response response = null;

		rememberSyncFuture(future);
		
		try {
			response = future.get(cfg.TimeoutMillis, TimeUnit.MILLISECONDS);
		}
		catch (Exception e) {
			response = new Response();
			response.setException(e);

			if (cfg.LogResponse) {
				String msg = String.format("WebRequest for %s: Sync Get EXCEPTION %s",
										   url, response.Ex.toString());
				log.warning(msg);
			}
		}
		finally {
			forgetSyncFuture(future);
		}

		return(response);
	}

	public CompletableFuture<Response> fetchAsync(String url, Params params) {
		
		CompletableFuture<Response> future = new CompletableFuture<Response>();

		pool.submit(() -> {

			HttpURLConnection conn = null;

			Response response = new Response();
			Instant started = Instant.now();

			String fullUrl = "";
			
			try {
				if (params == null) {
					throw new IllegalArgumentException("params cannot be null");
				}
					
				fullUrl = Easy.urlAddQueryParams(url, params.QueryParams);

				String method = (params.Body == null ? "GET" : "POST");
				if (params.MethodOverride != null) method = params.MethodOverride;

				conn = (HttpURLConnection) (new URL(fullUrl).openConnection());
				setSocketFactory(conn);
				
				conn.setRequestMethod(method);
				conn.setFollowRedirects(cfg.FollowRedirects);
				conn.setConnectTimeout(cfg.TimeoutMillis);
				conn.setReadTimeout(cfg.TimeoutMillis);

				addHeaders(conn, params);
				sendBody(conn, params);
				
				response.Status = conn.getResponseCode();
				response.StatusText = conn.getResponseMessage();
				response.Headers = conn.getHeaderFields();
				
				InputStream stm = getInputStreamReally(conn);
				response.Body = Easy.stringFromInputStream(stm);
				// don't need to close stm; conn.disconnect() handles it
			}
			catch (Exception e) {
				response.setException(e);
			}
			finally {
				try {
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

	private void addHeaders(HttpURLConnection conn, Params params) {

		if (params.Headers == null || params.Headers.size() == 0)
			return;

		for (String name : params.Headers.keySet())
			conn.setRequestProperty(name, params.Headers.get(name));
	}

	private void sendBody(HttpURLConnection conn, Params params) throws IOException {
		
		if (params.Body == null)
			return;

		conn.setRequestProperty("X-Requested-With", "ShutdownHookWebRequests");
		conn.setRequestProperty("Csrf-Token", "nocheck");
		
		conn.setDoOutput(true);

		OutputStream stream = null;

		try {
			byte[] rgb = params.Body.getBytes(StandardCharsets.UTF_8);
			conn.setRequestProperty("Content-Length", Integer.toString(rgb.length));
			
			stream = conn.getOutputStream();
			stream.write(rgb, 0, rgb.length);
		}
		finally {
			if (stream != null) stream.close();
		}
	}

	private void setSocketFactory(HttpURLConnection conn) {
		if (conn instanceof HttpsURLConnection && sslSocketFactory != null) {
			((HttpsURLConnection)conn).setSSLSocketFactory(sslSocketFactory);
		}
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

	public static void main(String[] args) throws Exception {
		WebRequests requests = new WebRequests(new Config());
		Response response = requests.fetch(args[0]);
		if (response.successful()) System.out.println(response.Body);
		requests.close();
	}

	public static class ExtendedTrustManager implements X509TrustManager
	{
		public ExtendedTrustManager(String certificateFile) throws Exception {

			defaultTm = findX509TrustManager(null);

			KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
			ks.load(null, null);

			Certificate[] certs = getCertsFromPemFile(certificateFile);
			for (int i = 0; i < certs.length; ++i) {
				ks.setCertificateEntry("alias" + Integer.toString(i), certs[i]);
			}

			extendedTm = findX509TrustManager(ks);
		}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType)
			throws CertificateException {
			// try ours, then default
			try {
				extendedTm.checkServerTrusted(chain, authType);
			}
			catch (CertificateException e) {
				defaultTm.checkServerTrusted(chain, authType);
			}
		}

		@Override
		public X509Certificate[] getAcceptedIssuers() {
			// always delegate
			return(defaultTm.getAcceptedIssuers());
		}
		
		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType)
			throws CertificateException {
			// always delegate
			defaultTm.checkClientTrusted(chain, authType);
		}

		// this is duplicated here and in SecureServer.java. That is kind of dumb,
		// but I really want to keep these implementations a little independent and
		// I don't think there will be a third reason for me to need this code.
		// If that changes, then we'll revisit.
		private Certificate[] getCertsFromPemFile(String path) throws IOException,
																	  FileNotFoundException,
																	  CertificateException {
			InputStream stream = null;
			BufferedInputStream buffered = null;

			List<Certificate> certs = new ArrayList<Certificate>();
		
			try {
				stream = (path.startsWith("@")
						  ? getClass().getClassLoader().getResourceAsStream(path.substring(1))
						  : new FileInputStream(path));
			
				buffered = new BufferedInputStream(stream);
			
				CertificateFactory certFactory = CertificateFactory.getInstance("X.509");

				while (buffered.available() > 0) {
					certs.add(certFactory.generateCertificate(buffered));
				}
			}
			finally {
				if (buffered != null) buffered.close();
				if (stream != null) stream.close();
			}

			return(certs.toArray(new Certificate[certs.size()]));
		}

		private X509TrustManager findX509TrustManager(KeyStore ks)
			throws NoSuchAlgorithmException, KeyStoreException {

			String alg = TrustManagerFactory.getDefaultAlgorithm();
			TrustManagerFactory tmf = TrustManagerFactory.getInstance(alg);
			tmf.init(ks);

			for (TrustManager tm : tmf.getTrustManagers()) {
				if (tm instanceof X509TrustManager) {
					return((X509TrustManager)tm);
				}
			}

			throw new NoSuchAlgorithmException("X509TrustManager not found");
		}

		private X509TrustManager defaultTm;
		private X509TrustManager extendedTm;
	}

	private Config cfg;
	private ExecutorService pool;
	private Set<Future> syncFutures;
	private SSLSocketFactory sslSocketFactory;

	private final static Logger log = Logger.getLogger(WebRequests.class.getName());
}
