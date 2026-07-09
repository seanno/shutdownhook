//
// CONVERSATIONCONFIGTEST.JAVA
//

package com.shutdownhook.colossus;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConversationConfigTest
{
	private Path tempFile;

	@After
	public void tearDown() throws Exception {
		if (tempFile != null && Files.exists(tempFile)) Files.delete(tempFile);
	}

	// +----------+
	// | Defaults |
	// +----------+

	@Test
	public void testDefaults() {
		Conversation.Config cfg = new Conversation.Config();
		assertEquals("http://localhost:11434", cfg.BaseUrl);
		assertEquals(0.0d, cfg.Temperature, 0.001d);
		assertEquals(4096L, cfg.MaxTokens);
		assertEquals(4L, cfg.MaxTokensCeilingDivisor);
		assertEquals(100, cfg.PruneTruncationLength);
		assertNotNull(cfg.Utility);
		assertNotNull(cfg.Environment);
	}

	// +-----------+
	// | JSON / IO |
	// +-----------+

	@Test
	public void testJsonRoundTrip() {
		Conversation.Config cfg = new Conversation.Config();
		cfg.Model = "test-model";
		cfg.BaseUrl = "http://example.com";
		cfg.Temperature = 0.7d;
		cfg.MaxTokens = 2048L;
		cfg.SystemPrompt = "You are helpful.";

		Conversation.Config restored = Conversation.Config.fromJson(cfg.toJson());

		assertEquals(cfg.Model, restored.Model);
		assertEquals(cfg.BaseUrl, restored.BaseUrl);
		assertEquals(cfg.Temperature, restored.Temperature, 0.001d);
		assertEquals(cfg.MaxTokens, restored.MaxTokens);
		assertEquals(cfg.SystemPrompt, restored.SystemPrompt);
	}

	// +-------+
	// | clone |
	// +-------+

	@Test
	public void testClonePreservesValues() {
		Conversation.Config original = new Conversation.Config();
		original.Model = "my-model";
		original.BaseUrl = "http://custom.host";
		original.MaxTokens = 8192L;
		original.Temperature = 0.4d;
		original.SystemPrompt = "Be concise.";
		original.PruneTruncationLength = 50;

		Conversation.Config cloned = original.clone();

		assertEquals(original.Model, cloned.Model);
		assertEquals(original.BaseUrl, cloned.BaseUrl);
		assertEquals(original.MaxTokens, cloned.MaxTokens);
		assertEquals(original.Temperature, cloned.Temperature, 0.001d);
		assertEquals(original.SystemPrompt, cloned.SystemPrompt);
		assertEquals(original.PruneTruncationLength, cloned.PruneTruncationLength);
	}

	@Test
	public void testCloneIsIndependent() {
		Conversation.Config original = new Conversation.Config();
		original.Model = "original-model";
		original.Temperature = 0.5d;

		Conversation.Config cloned = original.clone();
		cloned.Model = "cloned-model";
		cloned.Temperature = 0.9d;

		assertEquals("original-model", original.Model);
		assertEquals(0.5d, original.Temperature, 0.001d);
	}

	@Test
	public void testClonePreservesNestedEnvironmentConfig() {
		Conversation.Config original = new Conversation.Config();
		original.Environment.LocationString = "Seattle";
		original.Environment.TimeZone = "America/Los_Angeles";

		Conversation.Config cloned = original.clone();

		assertEquals("Seattle", cloned.Environment.LocationString);
		assertEquals("America/Los_Angeles", cloned.Environment.TimeZone);

		cloned.Environment.LocationString = "Boston";
		assertEquals("Seattle", original.Environment.LocationString);
	}

	// +----------+
	// | override |
	// +----------+

	@Test
	public void testOverrideNonexistentFileReturnsClone() throws Exception {
		Conversation.Config original = new Conversation.Config();
		original.Model = "base-model";
		original.Temperature = 0.3d;

		Path absent = Paths.get("/tmp/colossus_test_absent_" + System.nanoTime() + ".json");
		Conversation.Config result = original.override(absent);

		assertEquals(original.Model, result.Model);
		assertEquals(original.Temperature, result.Temperature, 0.001d);
	}

	@Test
	public void testOverrideMergesSpecifiedValues() throws Exception {
		Conversation.Config original = new Conversation.Config();
		original.Model = "base-model";
		original.Temperature = 0.3d;
		original.MaxTokens = 1000L;

		tempFile = Files.createTempFile("colossus-test-", ".json");
		Files.write(tempFile,
			"{\"Model\": \"override-model\", \"Temperature\": 0.8}"
			.getBytes(StandardCharsets.UTF_8));

		Conversation.Config result = original.override(tempFile);

		assertEquals("override-model", result.Model);
		assertEquals(0.8d, result.Temperature, 0.001d);
	}

	@Test
	public void testOverridePreservesUnspecifiedValues() throws Exception {
		Conversation.Config original = new Conversation.Config();
		original.Model = "base-model";
		original.MaxTokens = 9999L;
		original.SystemPrompt = "Keep this.";

		tempFile = Files.createTempFile("colossus-test-", ".json");
		Files.write(tempFile,
			"{\"Model\": \"new-model\"}"
			.getBytes(StandardCharsets.UTF_8));

		Conversation.Config result = original.override(tempFile);

		assertEquals("new-model", result.Model);
		assertEquals(9999L, result.MaxTokens);
		assertEquals("Keep this.", result.SystemPrompt);
	}

	@Test
	public void testOverrideDoesNotModifyOriginal() throws Exception {
		Conversation.Config original = new Conversation.Config();
		original.Model = "base-model";

		tempFile = Files.createTempFile("colossus-test-", ".json");
		Files.write(tempFile,
			"{\"Model\": \"override-model\"}"
			.getBytes(StandardCharsets.UTF_8));

		original.override(tempFile);

		assertEquals("base-model", original.Model);
	}

	@Test
	public void testOverrideCanSetNestedConfig() throws Exception {
		Conversation.Config original = new Conversation.Config();

		tempFile = Files.createTempFile("colossus-test-", ".json");
		Files.write(tempFile,
			"{\"Environment\": {\"LocationString\": \"Paris\", \"TimeZone\": \"Europe/Paris\"}}"
			.getBytes(StandardCharsets.UTF_8));

		Conversation.Config result = original.override(tempFile);

		assertEquals("Paris", result.Environment.LocationString);
		assertEquals("Europe/Paris", result.Environment.TimeZone);
	}
}
