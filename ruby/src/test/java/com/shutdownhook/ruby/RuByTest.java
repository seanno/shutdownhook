/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.ruby;

import org.junit.Test;

import org.junit.Assert;
import org.junit.BeforeClass;

import com.shutdownhook.toolbox.Easy;

public class RuByTest
{
	private static RuBy ruby;
	
	@BeforeClass
	public static void setup() throws Exception {

		Easy.setSimpleLogFormat("FINE");
		
		RuBy.Config cfg = new RuBy.Config();
		cfg.Ip2LocationCsvPath = "@IP2LOCATION-TEST.IPV6.CSV";
		ruby = new RuBy(cfg);
	}
	
	@Test
    public void basicTests() throws Exception
    {
		// before range (!ruby)
		Assert.assertFalse(ruby.inRange("0:0:0:0:0:fffe:ffff:fff6"));

		// first range (!ruby)
		Assert.assertFalse(ruby.inRange("0.0.0.0"));
		Assert.assertFalse(ruby.inRange("0.255.255.254"));
		Assert.assertFalse(ruby.inRange("0.255.255.255"));

		// middle range ipv4 (ruby)
		Assert.assertFalse(ruby.inRange("2.16.20.255"));
		Assert.assertTrue(ruby.inRange("2.16.21.0"));
		Assert.assertTrue(ruby.inRange("2.16.21.6"));
		Assert.assertTrue(ruby.inRange("2.16.21.255"));
		Assert.assertFalse(ruby.inRange("2.16.22.0"));

		// middle range ipv6 (ruby)
		Assert.assertFalse(ruby.inRange("2c0f:f738:0:ffff:ffff:ffff:ffff:ffff"));
		Assert.assertTrue(ruby.inRange("2c0f:f738:1:0:0:0:0:0"));
		Assert.assertTrue(ruby.inRange("2c0f:f738:1:0:0:0:0:48"));
		Assert.assertTrue(ruby.inRange("2c0f:f738:1:ffff:ffff:ffff:ffff:ffff"));
		Assert.assertFalse(ruby.inRange("2c0f:f738:2:0:0:0:0:0"));

		// last range (!ruby)
		Assert.assertFalse(ruby.inRange("2c0f:f739:0:0:0:0:0:0"));
		Assert.assertFalse(ruby.inRange("2c0f:f739:0:0:0:0:0:7"));
		Assert.assertFalse(ruby.inRange("2c0f:f73f:ffff:ffff:ffff:ffff:ffff:ffff"));
		
		// beyond range (!ruby)
		Assert.assertFalse(ruby.inRange("2c0f:f740:0:0:0:0:0:3c"));
    }
}

