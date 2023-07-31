/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

// Attribution and data source: This site or product includes IP2Location LITE
// data available from https://lite.ip2location.com.
//
// The class is super-specialized to quickly and kind-of-compactly
// determine if an IP originates from any of a given set of country
// codes. It depends on the input CSV having the following properties:
//
// 1. CSV format, quoted fields OK
// 2. No header row
// 3. Columns:
//    1 = Range Start (inclusive)
//    2 = Range End (inclusive)
//    3 = ISO3166 Alpha2 Country Code IN UPPERCASE
//    (Additional columns beyond are OK but ignored)
// 4. No overlapping ranges
// 5. Ranges appear in increasing order

package com.shutdownhook.ruby;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.logging.Logger;

import com.shutdownhook.toolbox.Easy;

public class RuBy
{
	// +----------------+
	// | Config & Setup |
	// +----------------+

	public static class Config
	{
		public String Ip2LocationCsvPath;
		public String[] Iso3166RejectList = { "RU", "BY" };
	}

	public RuBy(Config cfg) throws IOException {
		this.cfg = cfg;
		setupRanges();
	}

	// +---------+
	// | inRange |
	// +---------+

	public boolean inRange(String address) throws UnknownHostException {

		IpRange probe = new IpRange(addressToBigInteger(address));
		int irange = Collections.binarySearch(ranges, probe);

		if (irange < 0) {
			int insertionPoint = (irange + 1) * -1;
			irange = insertionPoint - 1;
		}

		if (irange < 0) {
			// our ip is lower than the lowest start so can't be there
			log.fine(String.format("MISS: %s (%s) < all ranges", address, probe));
			
			return(false);
		}

		// else irange is the largest range starting <= probe
		IpRange range = ranges.get(irange);
		boolean ret = (probe.End.compareTo(range.End) <= 0);

		log.fine(String.format("%s: %s (%s) at index %d (%s:%s)",
								(ret ? "HIT" : "MISS"), address, probe,
								irange, range, range.End));
		return(ret);
	}

	// +-------------+
	// | setupRanges |
	// +-------------+

	private void setupRanges() throws IOException {

		InputStream input = null;
		InputStreamReader reader = null;
		BufferedReader bufferedReader = null;

		ranges = new ArrayList<IpRange>();
		
		try {
			input = Easy.streamFromSmartyPath(cfg.Ip2LocationCsvPath);
			reader = new InputStreamReader(input);
			bufferedReader = new BufferedReader(reader);

			String line = null;
			while ((line = bufferedReader.readLine()) != null) {
				String fields[] = line.split(",");
				if (inCountryList(clean(fields[2]))) {
					IpRange range = new IpRange(clean(fields[0]), clean(fields[1]));
					ranges.add(range);

					log.fine(String.format("Added range (%s:%s) for %s",
											range, range.End, fields[2]));
				}
			}
		}
		finally {
			try {
				if (bufferedReader != null) bufferedReader.close();
				if (reader != null) reader.close();
				if (input != null) input.close();
			}
			catch (Exception eInner) {
				// eat it
			}
		}
	}

	private boolean inCountryList(String code) {

		for (String c : cfg.Iso3166RejectList) {
			if (c.equals(code)) return(true);
		}

		return(false);
	}
	
	private String clean(String input) {
		
		String cleaned = input.trim();
		if (cleaned.charAt(0) == '"') {
			cleaned = cleaned.substring(1, cleaned.length() - 1);
		}
		
		return(cleaned);
	}

	// +-------------+
	// | Conversions |
	// +-------------+

	// see https://blog.ip2location.com/knowledge-base/ipv4-mapped-ipv6-address/
	private static BigInteger V4_OFFSET = new BigInteger("281470681743360");

	public static String addressToBigInteger(String input)
		throws UnknownHostException {
		
		byte[] rgb = InetAddress.getByName(input).getAddress();
		BigInteger addr = new BigInteger(1, rgb);
		if (rgb.length == 4) addr = addr.add(V4_OFFSET);
		return(addr.toString());
	}

	public static String bigIntegerToAddress(String input)
		throws IllegalArgumentException, UnknownHostException {
		
		byte[] rgb = new BigInteger(input).toByteArray();
		rgb = fixupBytesForAddress(rgb);
		return(InetAddress.getByAddress(rgb).toString());
	}

	private static byte[] fixupBytesForAddress(byte[] rgbIn) throws IllegalArgumentException {
		
		// first strip leading zero bytes if present
		byte[] rgb = rgbIn;
		while (rgb.length > 1 && rgb[0] == 0) rgb = Arrays.copyOfRange(rgb, 1, rgb.length);

		// now figure out if we need to pad to 4 or 16 bytes
		if (rgb.length == 4 || rgb.length == 16) {
			return(rgb);
		}

		if (rgb.length > 16) {
			throw new IllegalArgumentException(String.format("rgbIn too big (%d)", rgb.length));
		}

		// and pad
		int cb = (rgb.length < 4 ? 4 : 16);
		byte[] rgbPadded = new byte[cb];
		int offset = cb - rgb.length;
		
		for (int i = 0; i < rgb.length; ++i) {
			rgbPadded[i + offset] = rgb[i];
		}

		return(rgbPadded);
	}

	// +---------+
	// | IpRange | 
	// +---------+

	// inherited value is Start, End is End, both inclusive
	
	public static class IpRange extends BigInteger
	{
		public IpRange(String single) {
			super(single);
			End = new BigInteger(single);
		}
		
		public IpRange(String start, String end) {
			super(start);
			End = new BigInteger(end);
		}
		
		public BigInteger End;
	}

	// +---------+
	// | Members | 
	// +---------+

	private Config cfg;
	private ArrayList<IpRange> ranges;

	private final static Logger log = Logger.getLogger(RuBy.class.getName());
}
