/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
**
** (FULLY WRITTEN BY CLAUDE)
*/

package com.shutdownhook.toolbox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import org.junit.Assert;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Ignore;

public class LineTransformerTest
{
	@BeforeClass
	public static void beforeClass() throws Exception {
		Global.init();
	}

	@After
	public void afterEach() throws Exception {
		if (tempFile != null) {
			Files.deleteIfExists(tempFile);
			tempFile = null;
		}
	}

	// +------+
	// | read |
	// +------+

	@Test
	public void testRead_fullRange() throws Exception {
		Path p = createTempFile(FIVE_LINES);
		String result = LineTransformer.read(p, 0, 4);
		Assert.assertEquals(FIVE_LINES, result);
	}

	@Test
	public void testRead_middleRange() throws Exception {
		Path p = createTempFile(FIVE_LINES);
		String result = LineTransformer.read(p, 1, 3);
		Assert.assertEquals("bbb\nccc\nddd\n", result);
	}

	@Test
	public void testRead_singleLine() throws Exception {
		Path p = createTempFile(FIVE_LINES);
		String result = LineTransformer.read(p, 2, 2);
		Assert.assertEquals("ccc\n", result);
	}

	@Test
	public void testRead_firstLine() throws Exception {
		Path p = createTempFile(FIVE_LINES);
		String result = LineTransformer.read(p, 0, 0);
		Assert.assertEquals("aaa\n", result);
	}

	@Test
	public void testRead_lastLine() throws Exception {
		Path p = createTempFile(FIVE_LINES);
		String result = LineTransformer.read(p, 4, 4);
		Assert.assertEquals("eee\n", result);
	}

	@Test
	public void testRead_openEnded() throws Exception {
		Path p = createTempFile(FIVE_LINES);
		String result = LineTransformer.read(p, 2, -1);
		Assert.assertEquals("ccc\nddd\neee\n", result);
	}

	// +---------+
	// | readAll |
	// +---------+

	@Test
	public void testReadAll() throws Exception {
		Path p = createTempFile(FIVE_LINES);
		String result = LineTransformer.readAll(p);
		Assert.assertEquals(FIVE_LINES, result);
	}

	// +--------+
	// | search |
	// +--------+

	@Test
	public void testSearch_found() throws Exception {
		Path p = createTempFile(FIVE_LINES);
		String result = LineTransformer.search(p, "ccc");
		Assert.assertEquals("2\tccc\n", result);
	}

	@Test
	public void testSearch_caseInsensitive() throws Exception {
		Path p = createTempFile(FIVE_LINES);
		String result = LineTransformer.search(p, "CCC");
		Assert.assertEquals("2\tccc\n", result);
	}

	@Test
	public void testSearch_notFound() throws Exception {
		Path p = createTempFile(FIVE_LINES);
		String result = LineTransformer.search(p, "zzz");
		Assert.assertEquals("", result);
	}

	@Test
	public void testSearch_multipleMatches() throws Exception {
		Path p = createTempFile("aXa\nbXb\ncXc\n");
		String result = LineTransformer.search(p, "X");
		Assert.assertEquals("0\taXa\n1\tbXb\n2\tcXc\n", result);
	}

	// +--------+
	// | delete |
	// +--------+

	@Test
	public void testDelete_middle() throws Exception {
		Path p = createTempFile(FIVE_LINES);
		LineTransformer.delete(p, 1, 3);
		Assert.assertEquals("aaa\neee\n", Files.readString(p));
	}

	@Test
	public void testDelete_first() throws Exception {
		Path p = createTempFile(FIVE_LINES);
		LineTransformer.delete(p, 0, 0);
		Assert.assertEquals("bbb\nccc\nddd\neee\n", Files.readString(p));
	}

	@Test
	public void testDelete_last() throws Exception {
		Path p = createTempFile(FIVE_LINES);
		LineTransformer.delete(p, 4, 4);
		Assert.assertEquals("aaa\nbbb\nccc\nddd\n", Files.readString(p));
	}

	@Test
	public void testDelete_openEnded() throws Exception {
		Path p = createTempFile(FIVE_LINES);
		LineTransformer.delete(p, 2, -1);
		Assert.assertEquals("aaa\nbbb\n", Files.readString(p));
	}

	// +---------+
	// | replace |
	// +---------+

	@Test
	public void testReplace_singleLine() throws Exception {
		Path p = createTempFile(FIVE_LINES);
		LineTransformer.replace(p, 2, 2, "CCC");
		Assert.assertEquals("aaa\nbbb\nCCC\nddd\neee\n", Files.readString(p));
	}

	@Test
	public void testReplace_multipleLines() throws Exception {
		Path p = createTempFile(FIVE_LINES);
		LineTransformer.replace(p, 1, 3, "REPLACED");
		Assert.assertEquals("aaa\nREPLACED\neee\n", Files.readString(p));
	}

	@Test
	public void testReplace_withMultilineContent() throws Exception {
		Path p = createTempFile(FIVE_LINES);
		LineTransformer.replace(p, 1, 3, "X\nY\nZ");
		Assert.assertEquals("aaa\nX\nY\nZ\neee\n", Files.readString(p));
	}

	// +--------+
	// | insert |
	// +--------+

	@Test
	public void testInsert_atBeginning() throws Exception {
		Path p = createTempFile(FIVE_LINES);
		LineTransformer.insert(p, 0, "zzz");
		Assert.assertEquals("zzz\naaa\nbbb\nccc\nddd\neee\n", Files.readString(p));
	}

	@Test
	public void testInsert_inMiddle() throws Exception {
		Path p = createTempFile(FIVE_LINES);
		LineTransformer.insert(p, 2, "zzz");
		Assert.assertEquals("aaa\nbbb\nzzz\nccc\nddd\neee\n", Files.readString(p));
	}

	@Test
	public void testInsert_multipleLines() throws Exception {
		Path p = createTempFile(FIVE_LINES);
		LineTransformer.insert(p, 2, "x\ny\nz");
		Assert.assertEquals("aaa\nbbb\nx\ny\nz\nccc\nddd\neee\n", Files.readString(p));
	}

	// +--------+
	// | append |
	// +--------+

	@Test
	public void testAppend_toExistingFile() throws Exception {
		Path p = createTempFile(FIVE_LINES);
		LineTransformer.append(p, "fff");
		Assert.assertEquals("aaa\nbbb\nccc\nddd\neee\nfff\n", Files.readString(p));
	}

	@Test
	public void testAppend_toFileWithoutTrailingNewline() throws Exception {
		Path p = createTempFile("aaa\nbbb");
		LineTransformer.append(p, "ccc");
		Assert.assertEquals("aaa\nbbb\nccc\n", Files.readString(p));
	}

	@Test
	public void testAppend_toEmptyFile() throws Exception {
		Path p = createTempFile("");
		LineTransformer.append(p, "aaa");
		String result = Files.readString(p);
		Assert.assertTrue(result.contains("aaa"));
		Assert.assertTrue(result.endsWith("\n"));
	}

	@Test
	public void testAppend_multipleLines() throws Exception {
		Path p = createTempFile(FIVE_LINES);
		LineTransformer.append(p, "fff\nggg");
		Assert.assertEquals("aaa\nbbb\nccc\nddd\neee\nfff\nggg\n", Files.readString(p));
	}

	// +---------+
	// | process |
	// +---------+

	@Test
	public void testProcess_countsLines() throws Exception {
		Path p = createTempFile(FIVE_LINES);
		final int[] count = { 0 };
		LineTransformer.process(p, new LineTransformer.LineProcessor() {
			public String processLine(int lineIndex, String line) throws IOException {
				count[0]++;
				return(null);
			}
		});
		Assert.assertEquals(5, count[0]);
	}

	@Test
	public void testProcess_stopEarly() throws Exception {
		Path p = createTempFile(FIVE_LINES);
		final int[] count = { 0 };
		LineTransformer.process(p, new LineTransformer.LineProcessor() {
			public String processLine(int lineIndex, String line) throws IOException {
				count[0]++;
				return(null);
			}
			public boolean stop(int lineIndex) {
				return(lineIndex >= 2);
			}
		});
		Assert.assertEquals(3, count[0]);
	}

	// +-----------+
	// | transform |
	// +-----------+

	@Test
	public void testTransform_uppercase() throws Exception {
		Path p = createTempFile(FIVE_LINES);
		LineTransformer.transform(p, new LineTransformer.LineProcessor() {
			public String processLine(int lineIndex, String line) throws IOException {
				return(line.toUpperCase());
			}
		});
		Assert.assertEquals("AAA\nBBB\nCCC\nDDD\nEEE\n", Files.readString(p));
	}

	@Test
	public void testTransform_filterLines() throws Exception {
		Path p = createTempFile(FIVE_LINES);
		LineTransformer.transform(p, new LineTransformer.LineProcessor() {
			public String processLine(int lineIndex, String line) throws IOException {
				return(lineIndex % 2 == 0 ? line : null);
			}
		});
		Assert.assertEquals("aaa\nccc\neee\n", Files.readString(p));
	}

	@Test
	public void testTransform_noTempFileLeftBehind() throws Exception {
		Path p = createTempFile(FIVE_LINES);
		Path tmpPath = Path.of(p.toString() + ".tmp");
		LineTransformer.transform(p, new LineTransformer.LineProcessor() {
			public String processLine(int lineIndex, String line) throws IOException {
				return(line);
			}
		});
		Assert.assertFalse(Files.exists(tmpPath));
	}

	// +------------------+
	// | nonExistingFile  |
	// +------------------+

	@Test
	public void testRead_nonExistingFile() throws Exception {
		Path p = nonExistingPath();
		String result = LineTransformer.read(p, 0, 4);
		Assert.assertEquals("", result);
	}

	@Test
	public void testReadAll_nonExistingFile() throws Exception {
		Path p = nonExistingPath();
		String result = LineTransformer.readAll(p);
		Assert.assertEquals("", result);
	}

	@Test
	public void testSearch_nonExistingFile() throws Exception {
		Path p = nonExistingPath();
		String result = LineTransformer.search(p, "anything");
		Assert.assertEquals("", result);
	}

	@Test
	public void testDelete_nonExistingFile() throws Exception {
		Path p = nonExistingPath();
		LineTransformer.delete(p, 0, 4);
		Assert.assertFalse(Files.exists(p));
	}

	@Test
	public void testInsert_nonExistingFile_atZero() throws Exception {
		Path p = nonExistingPath();
		tempFile = p;
		LineTransformer.insert(p, 0, "hello\nworld");
		Assert.assertEquals("hello\nworld\n", Files.readString(p));
	}

	@Test(expected = NoSuchFileException.class)
	public void testInsert_nonExistingFile_nonZero() throws Exception {
		Path p = nonExistingPath();
		LineTransformer.insert(p, 5, "hello");
	}

	@Test
	public void testReplace_nonExistingFile_atZero() throws Exception {
		Path p = nonExistingPath();
		tempFile = p;
		LineTransformer.replace(p, 0, 0, "hello\nworld");
		Assert.assertEquals("hello\nworld\n", Files.readString(p));
	}

	@Test(expected = NoSuchFileException.class)
	public void testReplace_nonExistingFile_nonZero() throws Exception {
		Path p = nonExistingPath();
		LineTransformer.replace(p, 5, 10, "hello");
	}

	@Test
	public void testAppend_nonExistingFile() throws Exception {
		Path p = nonExistingPath();
		tempFile = p;
		LineTransformer.append(p, "hello");
		Assert.assertEquals("hello\n", Files.readString(p));
	}

	// +---------+
	// | Helpers |
	// +---------+

	private static final String FIVE_LINES = "aaa\nbbb\nccc\nddd\neee\n";

	private Path tempFile;

	private Path createTempFile(String content) throws IOException {
		tempFile = Files.createTempFile("lt-test-", ".txt");
		Files.writeString(tempFile, content);
		return(tempFile);
	}

	private Path nonExistingPath() throws IOException {
		Path p = Files.createTempFile("lt-test-noexist-", ".txt");
		Files.delete(p);
		return(p);
	}
}
