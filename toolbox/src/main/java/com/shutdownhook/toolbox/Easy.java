/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.toolbox;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.lang.IllegalArgumentException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Base64;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

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

	// +-----------------+
	// | Dates and Times |
	// +-----------------+

	public static LocalDate parseVariablePrecisionDate(String input) {
		ensureParsers();
		return(LocalDate.parse(input, vpdParser));
	}

	public static ZonedDateTime parseVariablePrecisionDateTime(String input, boolean defaultLocal) {
		ensureParsers();
		DateTimeFormatter dtf = (defaultLocal ? vpdtParserLocal : vpdtParserUTC);
		return(ZonedDateTime.parse(input, dtf));
	}

	private static DateTimeFormatter vpdParser = null;
	private static DateTimeFormatter vpdtParserLocal = null;
	private static DateTimeFormatter vpdtParserUTC = null;

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

	// +------+
	// | Misc |
	// +------+

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
		System.setProperty("java.util.logging.SimpleFormatter.format",
						   "[%1$tF %1$tT] [%4$-7s] %5$s %n");
	}

	private final static Logger log = Logger.getLogger(Easy.class.getName());
}
