/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.toolbox;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
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
import java.nio.file.StandardOpenOption;
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
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Locale;
import java.util.HashMap;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
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

		String homeDir = System.getProperty("user.home");
		
		return(new FileInputStream(new File(path)
								   .getCanonicalPath()
								   .replaceAll("~", homeDir)));
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

	public static void appendStringToFile(String path, String data) throws IOException {
		Files.write(Paths.get(path), data.getBytes("UTF-8"), StandardOpenOption.APPEND);
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

	public static void smartyPathToFile(String smartyPath,
										String destinationPath) throws IOException {

		InputStream stm = null;

		try {
			stm = streamFromSmartyPath(smartyPath);
			inputStreamToFile(stm, destinationPath);
		}
		finally {
			if (stm != null) stm.close();
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
	
	// +---------------+
	// | HTML Encoding |
	// +---------------+

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

	// adapted from https://stackoverflow.com/a/24575417/3289113.
	// assumes the escape value is trapped between & and ; and must be 
	// CCH_ENTITY_MIN to CCH_ENTITY_MAX in length, which provides some
	// protection against stray & characters that show up unencoded.

	private static int CCH_ENTITY_MIN = 2;
	private static int CCH_ENTITY_MAX = 6;
	
	public static String htmlDecode(String input) {
		
		StringBuffer sb = new StringBuffer();

		int cch = input.length();
		int ich = 0;

		while (ich < cch) {
			
			// find next ampersand
			int ichNextAmp = input.indexOf("&", ich);
			if (ichNextAmp == -1) ichNextAmp = cch;

			// copy over everything before that
			sb.append(input.substring(ich, ichNextAmp));
			ich = ichNextAmp;

			// now we're either pointing at an entity or at EOS
			if (ich < cch) {
				++ich;
				int ichEntityEnd = input.indexOf(";", ich);
				if (ichEntityEnd == -1) ichEntityEnd = cch;

				int cchEntity = ichEntityEnd - ich;
				if (cchEntity < CCH_ENTITY_MIN || cchEntity > CCH_ENTITY_MAX) {
					// hmm. well ok we'll just add that sucker in there
					// and see what happens on the next go-around
					sb.append("&");
				}
				else {
					// looks like an actual entity, figure that out
					appendDecodedHtmlEntity(sb, input.substring(ich, ichEntityEnd));
					ich = ichEntityEnd + 1;
				}
			}
		}

		return(sb.toString());
	}

	private static void appendDecodedHtmlEntity(StringBuffer sb, String entity) {

		if (entity.charAt(0) == '#') {

			// numeric, assumed decimal unless we see hex marker
			
			int ichNum = 1;
			int radix = 10;
			
			if (entity.charAt(1) == 'x' || entity.charAt(1) == 'X') {
				ichNum = 2;
				radix = 16;
			}

			try {
				int entityVal = Integer.parseInt(entity.substring(ichNum), radix);
			
				if (entityVal > 0xFFFF) {
					char[] rgch = Character.toChars(entityVal);
					sb.append(rgch[0]).append(rgch[1]);
				}
				else {
					sb.append((char)entityVal);
				}
			}
			catch (NumberFormatException e) {
				// they said it was a number but it wasn't, just skip.
			}
		}
		else {

			// named entity, use the lookup
			sb.append(htmlEntityLookup.get(entity));
		}
	}
	
    private static final String[][] HTML_NAMED_ENTITIES = {
        {"\"",     "quot"}, // " - double-quote
        {"&",      "amp"}, // & - ampersand
        {"<",      "lt"}, // < - less-than
        {">",      "gt"}, // > - greater-than

        // Mapping to escape ISO-8859-1 characters to their named HTML 3.x equivalents.
        {"\u00A0", "nbsp"}, // non-breaking space
        {"\u00A1", "iexcl"}, // inverted exclamation mark
        {"\u00A2", "cent"}, // cent sign
        {"\u00A3", "pound"}, // pound sign
        {"\u00A4", "curren"}, // currency sign
        {"\u00A5", "yen"}, // yen sign = yuan sign
        {"\u00A6", "brvbar"}, // broken bar = broken vertical bar
        {"\u00A7", "sect"}, // section sign
        {"\u00A8", "uml"}, // diaeresis = spacing diaeresis
        {"\u00A9", "copy"}, // © - copyright sign
        {"\u00AA", "ordf"}, // feminine ordinal indicator
        {"\u00AB", "laquo"}, // left-pointing double angle quotation mark = left pointing guillemet
        {"\u00AC", "not"}, // not sign
        {"\u00AD", "shy"}, // soft hyphen = discretionary hyphen
        {"\u00AE", "reg"}, // ® - registered trademark sign
        {"\u00AF", "macr"}, // macron = spacing macron = overline = APL overbar
        {"\u00B0", "deg"}, // degree sign
        {"\u00B1", "plusmn"}, // plus-minus sign = plus-or-minus sign
        {"\u00B2", "sup2"}, // superscript two = superscript digit two = squared
        {"\u00B3", "sup3"}, // superscript three = superscript digit three = cubed
        {"\u00B4", "acute"}, // acute accent = spacing acute
        {"\u00B5", "micro"}, // micro sign
        {"\u00B6", "para"}, // pilcrow sign = paragraph sign
        {"\u00B7", "middot"}, // middle dot = Georgian comma = Greek middle dot
        {"\u00B8", "cedil"}, // cedilla = spacing cedilla
        {"\u00B9", "sup1"}, // superscript one = superscript digit one
        {"\u00BA", "ordm"}, // masculine ordinal indicator
        {"\u00BB", "raquo"}, // right-pointing double angle quotation mark = right pointing guillemet
        {"\u00BC", "frac14"}, // vulgar fraction one quarter = fraction one quarter
        {"\u00BD", "frac12"}, // vulgar fraction one half = fraction one half
        {"\u00BE", "frac34"}, // vulgar fraction three quarters = fraction three quarters
        {"\u00BF", "iquest"}, // inverted question mark = turned question mark
        {"\u00C0", "Agrave"}, // А - uppercase A, grave accent
        {"\u00C1", "Aacute"}, // Б - uppercase A, acute accent
        {"\u00C2", "Acirc"}, // В - uppercase A, circumflex accent
        {"\u00C3", "Atilde"}, // Г - uppercase A, tilde
        {"\u00C4", "Auml"}, // Д - uppercase A, umlaut
        {"\u00C5", "Aring"}, // Е - uppercase A, ring
        {"\u00C6", "AElig"}, // Ж - uppercase AE
        {"\u00C7", "Ccedil"}, // З - uppercase C, cedilla
        {"\u00C8", "Egrave"}, // И - uppercase E, grave accent
        {"\u00C9", "Eacute"}, // Й - uppercase E, acute accent
        {"\u00CA", "Ecirc"}, // К - uppercase E, circumflex accent
        {"\u00CB", "Euml"}, // Л - uppercase E, umlaut
        {"\u00CC", "Igrave"}, // М - uppercase I, grave accent
        {"\u00CD", "Iacute"}, // Н - uppercase I, acute accent
        {"\u00CE", "Icirc"}, // О - uppercase I, circumflex accent
        {"\u00CF", "Iuml"}, // П - uppercase I, umlaut
        {"\u00D0", "ETH"}, // Р - uppercase Eth, Icelandic
        {"\u00D1", "Ntilde"}, // С - uppercase N, tilde
        {"\u00D2", "Ograve"}, // Т - uppercase O, grave accent
        {"\u00D3", "Oacute"}, // У - uppercase O, acute accent
        {"\u00D4", "Ocirc"}, // Ф - uppercase O, circumflex accent
        {"\u00D5", "Otilde"}, // Х - uppercase O, tilde
        {"\u00D6", "Ouml"}, // Ц - uppercase O, umlaut
        {"\u00D7", "times"}, // multiplication sign
        {"\u00D8", "Oslash"}, // Ш - uppercase O, slash
        {"\u00D9", "Ugrave"}, // Щ - uppercase U, grave accent
        {"\u00DA", "Uacute"}, // Ъ - uppercase U, acute accent
        {"\u00DB", "Ucirc"}, // Ы - uppercase U, circumflex accent
        {"\u00DC", "Uuml"}, // Ь - uppercase U, umlaut
        {"\u00DD", "Yacute"}, // Э - uppercase Y, acute accent
        {"\u00DE", "THORN"}, // Ю - uppercase THORN, Icelandic
        {"\u00DF", "szlig"}, // Я - lowercase sharps, German
        {"\u00E0", "agrave"}, // а - lowercase a, grave accent
        {"\u00E1", "aacute"}, // б - lowercase a, acute accent
        {"\u00E2", "acirc"}, // в - lowercase a, circumflex accent
        {"\u00E3", "atilde"}, // г - lowercase a, tilde
        {"\u00E4", "auml"}, // д - lowercase a, umlaut
        {"\u00E5", "aring"}, // е - lowercase a, ring
        {"\u00E6", "aelig"}, // ж - lowercase ae
        {"\u00E7", "ccedil"}, // з - lowercase c, cedilla
        {"\u00E8", "egrave"}, // и - lowercase e, grave accent
        {"\u00E9", "eacute"}, // й - lowercase e, acute accent
        {"\u00EA", "ecirc"}, // к - lowercase e, circumflex accent
        {"\u00EB", "euml"}, // л - lowercase e, umlaut
        {"\u00EC", "igrave"}, // м - lowercase i, grave accent
        {"\u00ED", "iacute"}, // н - lowercase i, acute accent
        {"\u00EE", "icirc"}, // о - lowercase i, circumflex accent
        {"\u00EF", "iuml"}, // п - lowercase i, umlaut
        {"\u00F0", "eth"}, // р - lowercase eth, Icelandic
        {"\u00F1", "ntilde"}, // с - lowercase n, tilde
        {"\u00F2", "ograve"}, // т - lowercase o, grave accent
        {"\u00F3", "oacute"}, // у - lowercase o, acute accent
        {"\u00F4", "ocirc"}, // ф - lowercase o, circumflex accent
        {"\u00F5", "otilde"}, // х - lowercase o, tilde
        {"\u00F6", "ouml"}, // ц - lowercase o, umlaut
        {"\u00F7", "divide"}, // division sign
        {"\u00F8", "oslash"}, // ш - lowercase o, slash
        {"\u00F9", "ugrave"}, // щ - lowercase u, grave accent
        {"\u00FA", "uacute"}, // ъ - lowercase u, acute accent
        {"\u00FB", "ucirc"}, // ы - lowercase u, circumflex accent
        {"\u00FC", "uuml"}, // ь - lowercase u, umlaut
        {"\u00FD", "yacute"}, // э - lowercase y, acute accent
        {"\u00FE", "thorn"}, // ю - lowercase thorn, Icelandic
        {"\u00FF", "yuml"}, // я - lowercase y, umlaut
    };

    private static final HashMap<String, CharSequence> htmlEntityLookup;

    static {
        htmlEntityLookup = new HashMap<String, CharSequence>();
        for (final CharSequence[] seq : HTML_NAMED_ENTITIES) 
            htmlEntityLookup.put(seq[1].toString(), seq[0]);
    }

	// +--------------------+
	// | URLs and Encodings |
	// +--------------------+

	public static String base64Encode(String input) {
		try { return(Base64.getEncoder().encodeToString(input.getBytes("UTF-8"))); }
		catch (UnsupportedEncodingException e) { return(null); } // won't happen
	}

	public static String base64Decode(String input) throws IllegalArgumentException {
		try { return(new String(Base64.getDecoder().decode(input), "UTF-8")); }
		catch (UnsupportedEncodingException e) { return(null); } // won't happen
	}

	public static String base64urlEncode(String input) {
		try { return(Base64.getUrlEncoder().withoutPadding().encodeToString(input.getBytes("UTF-8"))); }
		catch (UnsupportedEncodingException e) { return(null); } // won't happen
	}

	public static String base64urlDecode(String input) throws IllegalArgumentException {
		try { return(new String(Base64.getUrlDecoder().decode(input), "UTF-8")); }
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

	public static String urlAddQueryParam(String baseUrl, String name, String value) {

		Map<String,String> params = new HashMap<String,String>();
		params.put(name, value);
		
		return(urlAddQueryParams(baseUrl, params));
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

	public static Map<String,String> parseQueryString(String input) {

		Map<String,String> params = new HashMap<String,String>();

		if (input != null && !input.isEmpty()) {
			for (String pair : input.split("&")) {
				String[] kv = pair.split("=");
				String v = (kv.length > 1 ? urlDecode(kv[1]) : "");
				params.put(urlDecode(kv[0]), v);
			}
		}

		return(params);
	}

	// +-----------------+
	// | Hashes and such |
	// +-----------------+

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

	// +--------------------+
	// | Process Mgmt Stuff | 
	// +--------------------+

	public static void waitForExit() throws Exception {

		final Object shutdownTrigger = new Object();
		
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
		    synchronized (shutdownTrigger) { shutdownTrigger.notify(); }
		}));

		synchronized (shutdownTrigger) {
			try { shutdownTrigger.wait(); }
			catch (InterruptedException e) { /* nut-n-honey */ }
		}
	}
	
	// +-------+
	// | Files |
	// +-------+

	public static void recursiveDelete(File file) throws Exception {
		
		if (!file.exists()) return;

		if (file.isDirectory()) {
			for (File child : file.listFiles()) {
				recursiveDelete(child);
			}
		}

		file.delete();
	}

	public static void unzipToPath(String zipPath, String destPath) throws Exception {

		ZipFile zipFile = null;
		InputStream stm = null;

		try {
			zipFile = new ZipFile(zipPath);

			File destination = new File(destPath);
			destination.mkdirs();

			Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();

			while (zipEntries.hasMoreElements()) {
				
				ZipEntry zipEntry = zipEntries.nextElement();
				File thisFile = new File(destination, zipEntry.getName());

				thisFile.getParentFile().mkdirs();

				if (!zipEntry.isDirectory()) {
					stm = zipFile.getInputStream(zipEntry);
					inputStreamToFile(stm, thisFile.getAbsolutePath());
					stm.close(); stm = null;
				}
			}
			
		}
		finally {
			if (stm != null) stm.close();
			if (zipFile != null) zipFile.close();
		}
	}

	// +---------+
	// | Strings |
	// +---------+

	public static String join(Iterable iterable, String sep) {
		
		StringBuilder sb = new StringBuilder();

		Iterator iter = iterable.iterator();
		while (iter.hasNext()) {
			if (sb.length() > 0) sb.append(sep);
			sb.append(iter.next().toString());
		}

		return(sb.toString());
	}

	public static Boolean nullOrEmpty(String s) {
		return(s == null || s.isEmpty());
	}

	// +------+
	// | Misc |
	// +------+

	public static void safeClose(Closeable c) {
		try { if (c != null) c.close(); }
		catch (Exception e) { /* eat it */ }
	}
	
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
	
	public static String exMsg(Throwable e, String msg, boolean includeStack) {

		String log = String.format("Exception (%s): %s%s",
								   e.toString(), msg,
								   (includeStack ? "\n" + getStackTrace(e) : ""));
		return(log);
	}
	
	public static String getStackTrace(Throwable e) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		e.printStackTrace(pw);
		return(sw.toString());
	}

	public static String jvmInfo() {
		
		Runtime runtime = Runtime.getRuntime();

		return(String.format("Java: %s \n" +
							 "Max Heap: %d mb \n" +
							 "Processors: %d \n",
							 System.getProperty("java.version"),
							 runtime.maxMemory() / 1024 / 1024,
							 runtime.availableProcessors()));
	}

	public static void configureLoggingProperties(String smartyPath) {

		InputStream stm = null;
		
		try {
			stm = streamFromSmartyPath(smartyPath);
			LogManager.getLogManager().readConfiguration(stm);
		}
		catch (IOException e) {
			System.err.println(exMsg(e, "configureLoggingProperties", false));
		}
		finally {
			if (stm != null) {
				try { stm.close(); }
				catch (Exception e2) { System.err.println(e2.toString()); }
			}
		}
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
	
	public static String smartyGetProperty(String input) {
		if (input == null) return(input);
		if (!input.startsWith("#")) return(input);
		return(superGetProperty(input.substring(1), null));
	}

	private final static Logger log = Logger.getLogger(Easy.class.getName());
}
