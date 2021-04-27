/*
** Read about this code at http://shutdownhook.com.
** No restrictions on use; no assurances or warranties either!
*/

// There is a lot of black magic in here gleaned from a ton of coding-
// by-google. I don't pretend to claim that it's perfect, but have tried
// to encapsulate it all in one place so that I can tweak it easily.

package com.shutdownhook.toolbox;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import java.net.InetSocketAddress;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.logging.Logger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;

public class SecureServer extends WebServer
{
	// +--------------+
	// | SecureServer |
	// +--------------+

	public SecureServer(WebServer.Config cfg) {
		super(cfg);
	}
	
	@Override
	protected void createHttpServer(InetSocketAddress address) throws Exception {

		KeyStore ks = keyStoreFromCertAndKey();
		
		KeyManagerFactory kmf = KeyManagerFactory.getInstance("PKIX");
		kmf.init(ks, null);

		TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
		tmf.init(ks);

		SSLContext ssl = SSLContext.getInstance("TLS");
		ssl.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

		server = HttpsServer.create(address, 0);

		((HttpsServer)server).setHttpsConfigurator(new HttpsConfigurator(ssl) {
				public void configure(HttpsParameters params) {

					SSLEngine engine = ssl.createSSLEngine();

					params.setNeedClientAuth(false);
					params.setCipherSuites(engine.getEnabledCipherSuites());
					params.setProtocols(engine.getEnabledProtocols());
					params.setSSLParameters(ssl.getDefaultSSLParameters());
				}
			});
	}
	
	private KeyStore keyStoreFromCertAndKey() throws KeyStoreException,
													 IOException,
													 NoSuchAlgorithmException,
													 CertificateException,
													 InvalidKeySpecException {

		// default type is supposedly the "preferred" one?
		KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());

		// this initializes it as empty
		ks.load(null, null);

		// load up the actual key stuff
		Certificate[] certs = getCertsFromPemFile(cfg.SSLCertificateFile);
		RSAPrivateKey key = getPrivateKeyFromPemFile(cfg.SSLCertificateKeyFile);

		if (cfg.SSLCertificateKeyFile.equals("@localhost.key")) {
			log.warning("USING EMBEDDED TEST/DEV KEY --- this should never show in prod!");
		}
		
		// and tuck it all into place
		ks.setCertificateEntry("alias", certs[0]);
		ks.setKeyEntry("alias", key, null, certs);

		return(ks);
	}

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

	private RSAPrivateKey getPrivateKeyFromPemFile(String path) throws InvalidKeySpecException,
																	   IOException,
																	   NoSuchAlgorithmException {

		String str = Easy.stringFromSmartyPath(path);
		str = str.replace("-----BEGIN PRIVATE KEY-----", "");
		str = str.replace("-----END PRIVATE KEY-----", "");
		str = str.replace("\n", "");
		
		byte[] rgb = Base64.getDecoder().decode(str);
		
		KeyFactory keyFactory = KeyFactory.getInstance("RSA");
		PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(rgb);
		return((RSAPrivateKey) keyFactory.generatePrivate(keySpec));
	}

	// +---------+
	// | Members |
	// +---------+

	private final static Logger log = Logger.getLogger(WebServer.class.getName());
}
															 
	
