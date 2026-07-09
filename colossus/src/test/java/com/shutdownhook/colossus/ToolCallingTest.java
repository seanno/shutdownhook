//
// TOOLCALLINGTEST.JAVA
//

package com.shutdownhook.colossus;

import com.google.gson.JsonObject;

import org.junit.Test;
import static org.junit.Assert.*;

public class ToolCallingTest
{
	// +-----------+
	// | ToolClass |
	// +-----------+

	@Test
	public void testToolClassJsonRoundTrip() {
		ToolCalling.ToolClass tc = new ToolCalling.ToolClass();
		tc.ClassName = "com.example.MyTool";
		tc.Name = "my_tool";
		tc.Description = "Does stuff.";

		ToolCalling.ToolClass restored = ToolCalling.ToolClass.fromJson(tc.toJson());

		assertEquals(tc.ClassName, restored.ClassName);
		assertEquals(tc.Name, restored.Name);
		assertEquals(tc.Description, restored.Description);
	}

	@Test
	public void testToolClassCloneIsIndependent() {
		ToolCalling.ToolClass original = new ToolCalling.ToolClass();
		original.ClassName = "com.example.OriginalTool";
		original.Name = "original";

		ToolCalling.ToolClass cloned = original.clone();

		assertEquals(original.ClassName, cloned.ClassName);
		assertEquals(original.Name, cloned.Name);

		cloned.ClassName = "com.example.ClonedTool";
		cloned.Name = "cloned";

		assertEquals("com.example.OriginalTool", original.ClassName);
		assertEquals("original", original.Name);
	}

	@Test
	public void testToolClassCloneDeepCopiesConfig() {
		ToolCalling.ToolClass original = new ToolCalling.ToolClass();
		original.ClassName = "com.example.Tool";
		original.Config = new JsonObject();
		original.Config.addProperty("key", "original-value");

		ToolCalling.ToolClass cloned = original.clone();

		assertEquals("original-value", cloned.Config.get("key").getAsString());

		cloned.Config.addProperty("key", "changed");
		assertEquals("original-value", original.Config.get("key").getAsString());
	}

	@Test
	public void testToolClassNullConfigSurvivesRoundTrip() {
		ToolCalling.ToolClass original = new ToolCalling.ToolClass();
		original.ClassName = "com.example.Tool";
		original.Config = null;

		ToolCalling.ToolClass cloned = original.clone();
		assertNull(cloned.Config);
	}

	// +------------+
	// | loadConfig |
	// +------------+

	@Test
	public void testLoadConfigWithNullConfigUsesDefaults() throws Exception {
		ToolCalling.ToolClass tc = new ToolCalling.ToolClass();
		tc.Config = null;

		TextFiles.Config cfg = ToolCalling.loadConfig(tc, TextFiles.Config.class);

		assertNotNull(cfg);
		assertEquals("/tmp", cfg.BasePath);
		assertFalse(cfg.ReadOnly);
		assertEquals(0, cfg.MaxReadLength);
	}

	@Test
	public void testLoadConfigWithProvidedValuesOverridesDefaults() throws Exception {
		ToolCalling.ToolClass tc = new ToolCalling.ToolClass();
		tc.Config = new JsonObject();
		tc.Config.addProperty("BasePath", "/custom/path");
		tc.Config.addProperty("ReadOnly", true);
		tc.Config.addProperty("MaxReadLength", 256);

		TextFiles.Config cfg = ToolCalling.loadConfig(tc, TextFiles.Config.class);

		assertEquals("/custom/path", cfg.BasePath);
		assertTrue(cfg.ReadOnly);
		assertEquals(256, cfg.MaxReadLength);
	}

	@Test
	public void testLoadConfigPartialOverrideKeepsOtherDefaults() throws Exception {
		ToolCalling.ToolClass tc = new ToolCalling.ToolClass();
		tc.Config = new JsonObject();
		tc.Config.addProperty("ReadOnly", true);
		// BasePath not specified

		TextFiles.Config cfg = ToolCalling.loadConfig(tc, TextFiles.Config.class);

		assertTrue(cfg.ReadOnly);
		assertEquals("/tmp", cfg.BasePath); // default preserved
	}
}
