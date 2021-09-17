/*
** Read about this code at http://shutdownhook.com
n** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.toolbox;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.lang.IllegalArgumentException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.HashSet;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.LogManager;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;


public class Easy
{
	// +-----------------------------+
	// | streamFrom... stringFrom... |
	// +-----------------------------+
	
	public static String stringFromInputStream(InputStream stream) throws IOException {

		if (stream == null)
			return(null);

		StringBuilder sb = new StringBuilder();
		
		InputStreamReader reader = null;
		BufferedReader buffered = null;

		try {
			reader = new InputStreamReader(stream);
			buffered = new BufferedReader(reader);

			char[] rgch = new char[4096];
			int cch = -1;

			while ((cch = buffered.read(rgch, 0, rgch.length)) != -1) {
				sb.append(rgch, 0, cch);
			}
		}
		finally {
			if (buffered != null) buffered.close();
			if (reader != null) reader.close();
		}

		return(sb.toString());
	}

	public static InputStream streamFromSmartyPath(String path) throws IOException {
		
		if (path.startsWith("@")) {
			// I'm a resource ... note this depends on the resource being findable
			// by the classloader that loaded us. That should be fine in literally
			// every case but worth a comment I guess.
			return(Easy.class.getClassLoader().getResourceAsStream(path.substring(1)));
		}

		return(new FileInputStream(path));
	}

	public static String stringFromSmartyPath(String path) throws IOException {

		InputStream stream = null;

		try {
			stream = streamFromSmartyPath(path);
			return(Easy.stringFromInputStream(stream));
		}
		finally {
			if (stream != null) stream.close();
		}
	}
	
	public static String stringFromFile(String path) throws IOException {
		if (path.startsWith("@")) throw new IOException("Invalid path: " + path);
		return(stringFromSmartyPath(path));
	}

	public static String stringFromResource(String name) throws IOException {
		return(stringFromSmartyPath("@" + name));
	}

	public static String stringFromProcess(String cmdLine) throws Exception {
		String[] commands = new String[] { "bash", "-c", cmdLine};
		ProcessBuilder pb = new ProcessBuilder(commands);
		Process p = pb.start();
		return(stringFromInputStream(p.getInputStream()));
	}

	public static void stringToFile(String path, String data) throws IOException {
		Files.write(Paths.get(path), data.getBytes("UTF-8"));
	}

	public static void setFileOwnerOnly(String path) throws IOException {

		Set<PosixFilePermission> perms = new HashSet<PosixFilePermission>();
		perms.add(PosixFilePermission.OWNER_READ);
		perms.add(PosixFilePermission.OWNER_WRITE);

		Files.setPosixFilePermissions(Paths.get(path), perms);
	}

	public static void inputStreamToFile(InputStream stm, String path)
		throws IOException {
		
		if (stm == null)
			return;

		FileOutputStream output = null;
		
		try {
			output = new FileOutputStream(path);

			byte[] rgb = new byte[4096];
			int cb = -1;

			while ((cb = stm.read(rgb, 0, rgb.length)) != -1) {
				output.write(rgb, 0, cb);
			}
		}
		finally {
			if (output != null) {
				try { output.flush(); }
				finally {output.close(); }
			}
		}
	}

	// +-----------------+
	// | Dates and Times |
	// +-----------------+

	private static DateTimeFormatter dtfHttp = null;
	private static DateTimeFormatter vpdParser = null;
	private static DateTimeFormatter vpdtParserLocal = null;
	private static DateTimeFormatter vpdtParserUTC = null;

	public static String httpNow() {
		ensureParsers();
		return(dtfHttp.format(ZonedDateTime.now(ZoneOffset.UTC)));
	}
	
	public static LocalDate parseVariablePrecisionDate(String input) {
		ensureParsers();
		return(LocalDate.parse(input, vpdParser));
	}

	public static ZonedDateTime parseVariablePrecisionDateTime(String input, boolean defaultLocal) {
		ensureParsers();
		DateTimeFormatter dtf = (defaultLocal ? vpdtParserLocal : vpdtParserUTC);
		return(ZonedDateTime.parse(input, dtf));
	}


	private synchronized static void ensureParsers() {
		
		if (vpdParser != null) return;

		vpdParser = new DateTimeFormatterBuilder()
			.appendValue(ChronoField.YEAR, 4)
			.optionalStart()
			    .appendLiteral("-")
			    .appendValue(ChronoField.MONTH_OF_YEAR, 2)
			    .optionalStart()
			        .appendLiteral("-")
			        .appendValue(ChronoField.DAY_OF_MONTH, 2)
   			    .optionalEnd()
			.optionalEnd()
			.parseDefaulting(ChronoField.MONTH_OF_YEAR, 1)
			.parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
			.toFormatter();

		DateTimeFormatter vpdtParserZoneless = new DateTimeFormatterBuilder()
			.appendValue(ChronoField.YEAR, 4)
			.optionalStart()
			    .appendLiteral("-")
			    .appendValue(ChronoField.MONTH_OF_YEAR, 2)
			    .optionalStart()
			        .appendLiteral("-")
			        .appendValue(ChronoField.DAY_OF_MONTH, 2)
			        .optionalStart()
			            .appendLiteral("T")
			            .appendValue(ChronoField.HOUR_OF_DAY, 2)
			            .appendLiteral(":")
			            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
			            .optionalStart()
			                .appendLiteral(":")
			                .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
			                .optionalStart()
			                    .appendFraction(ChronoField.MILLI_OF_SECOND, 0, 6, true)
			                .optionalEnd()
			            .optionalEnd()
			            .appendZoneOrOffsetId()
			        .optionalEnd()
   			    .optionalEnd()
			.optionalEnd()
			.parseDefaulting(ChronoField.MONTH_OF_YEAR, 1)
			.parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
			.parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
			.parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
			.parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
			.parseDefaulting(ChronoField.MILLI_OF_SECOND, 0)
			.toFormatter();

		vpdtParserLocal = vpdtParserZoneless.withZone(ZoneId.systemDefault());
		vpdtParserUTC = vpdtParserZoneless.withZone(ZoneOffset.UTC);

		dtfHttp = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss O",
											  Locale.ENGLISH);
	}
	
	// +--------------------+
	// | URLs and Encodings |
	// +--------------------+

	public static String htmlEncode(String input) {
		
		StringBuffer sb = new StringBuffer();

		for (int ich = 0; ich < input.length(); ++ich) {
			
			char ch = input.charAt(ich);
			
			if (ch > 127) {
				sb.append("&#").append((int)ch).append(";");
			}
			else {
				switch (ch) {
				    case '<': sb.append("&lt;"); break;
				    case '>': sb.append("&gt;"); break;
				    case '&': sb.append("&amp;"); break;
				    case '"': sb.append("&quot;"); break;
				    case '\'': sb.append("&apos;"); break;
				    default: sb.append(ch); break;
				}
			}
		}

		return(sb.toString());
	}
	
	public static String base64Encode(String input) {
		try { return(Base64.getEncoder().encodeToString(input.getBytes("UTF-8"))); }
		catch (UnsupportedEncodingException e) { return(null); } // won't happen
	}

	public static String base64Decode(String input) throws IllegalArgumentException {
		try { return(new String(Base64.getDecoder().decode(input), "UTF-8")); }
		catch (UnsupportedEncodingException e) { return(null); } // won't happen
	}

	public static String urlEncode(String input) {
		try { return(URLEncoder.encode(input, "UTF-8")); }
		catch (UnsupportedEncodingException e) { return(null); } // won't happen
	}

	public static String urlDecode(String input) {
		try { return(URLDecoder.decode(input, "UTF-8")); }
		catch (UnsupportedEncodingException e) { return(null); } // won't happen
	}

	public static String urlPaste(String base, String path) {

		// very simple url combinator ... adds a slash between the
		// two elements if necessary but otherwise very stupid

		String baseNoSlash = base;
		if (base.endsWith("/")) baseNoSlash = base.substring(0, base.length() - 1);

		return(baseNoSlash + (path.indexOf('/') == 0 ? "" : "/") + path);
	}

	public static String urlAddQueryParams(String baseUrl, Map<String,String> params) {

		if (params == null || params.size() == 0)
			return(baseUrl);

		return(baseUrl +
			   (baseUrl.indexOf("?") == -1 ? "?" : "&") + 
			   urlFormatQueryParams(params));
	}

	public static String urlFormatQueryParams(Map<String,String> params) {

		StringBuilder sb = new StringBuilder();
		boolean firstParam = true;
		
		for (String queryParam : params.keySet()) {

			sb.append(firstParam ? "" : "&"); firstParam = false;
			sb.append(queryParam).append("=");
			sb.append(urlEncode(params.get(queryParam)));
		}

		return(sb.toString());
	}

	public static String sha256(String input) {

		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] bytes = digest.digest(input.getBytes("UTF-8"));
			return(bytesToHex(bytes));
		}
		catch (NoSuchAlgorithmException e1) { return(null); } // will never happen
		catch (UnsupportedEncodingException e2) { return(null); } // will never happen
	}

	public static String sha256Base64(String input) {

		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] bytes = digest.digest(input.getBytes("UTF-8"));
			return(Base64.getEncoder().encodeToString(bytes));
		}
		catch (NoSuchAlgorithmException e1) { return(null); } // will never happen
		catch (UnsupportedEncodingException e2) { return(null); } // will never happen
	}

	public static String hmac256(String input, String keyBase64)
		throws  InvalidKeyException {
		
		try {
			Mac mac = Mac.getInstance("HmacSHA256");

			SecretKeySpec spec =
				new SecretKeySpec(Base64.getDecoder().decode(keyBase64), "HmacSHA256");
			
			mac.init(spec);
			byte[] rgb = mac.doFinal(input.getBytes("UTF-8"));
			return(Base64.getEncoder().encodeToString(rgb));
		}
		catch (NoSuchAlgorithmException e1) { return(null); } // will never happen
		catch (UnsupportedEncodingException e2) { return(null); } // will never happen
	}

	public static String bytesToHex(byte[] bytes) {
		
		StringBuilder sb = new StringBuilder();

		for (byte b : bytes) {
			String hex = Integer.toHexString(0xFF & b);
			if (hex.length() == 1) sb.append("0");
			sb.append(hex);
		}
		
		return(sb.toString());
	}

	// +------+
	// | Misc |
	// +------+

	public static String randomAlphaNumeric(int cch) {
		StringBuilder sb = new StringBuilder();
		Random random = new Random();
		for (int i = 0; i < cch; ++i) {
			
			int ch = 0;
			while (!Character.isAlphabetic(ch) && !Character.isDigit(ch)) {
				ch = random.nextInt(93) + 30;
			}

			sb.append((char)ch);
		}

		return(sb.toString());
	}
	
	public static String exMsg(Exception e, String msg, boolean includeStack) {

		String log = String.format("Exception (%s): %s%s",
								   e.toString(), msg,
								   (includeStack ? "\n" + getStackTrace(e) : ""));
		return(log);
	}
	
	public static String getStackTrace(Exception e) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		e.printStackTrace(pw);
		return(sw.toString());
	}
	
	public static void setSimpleLogFormat() {
		setSimpleLogFormat("WARNING");
	}

	public static void setSimpleLogFormat(String defaultLevel) {

		System.setProperty("java.util.logging.SimpleFormatter.format",
						   "[%1$tF %1$tT] [%4$-7s] %5$s %n");

		Level level = Level.parse(Easy.superGetProperty("loglevel", defaultLevel));
		Logger rootLogger = Logger.getLogger("");
		rootLogger.setLevel(level);
		for (Handler handler : rootLogger.getHandlers()) handler.setLevel(level);
	}

	public static String superGetProperty(String name, String defaultValue) {
		String val = System.getProperty(name);
		if (val == null) val = System.getenv(name);
		if (val == null) val = defaultValue;
		return(val);
	}

	private final static Logger log = Logger.getLogger(Easy.class.getName());
}
