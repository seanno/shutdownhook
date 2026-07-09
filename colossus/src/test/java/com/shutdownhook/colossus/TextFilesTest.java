//
// TEXTFILESTEST.JAVA
//

package com.shutdownhook.colossus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class TextFilesTest
{
	private Path tempDir;

	@Before
	public void setUp() throws Exception {
		tempDir = Files.createTempDirectory("colossus-test-");
	}

	@After
	public void tearDown() throws Exception {
		if (tempDir == null || !Files.exists(tempDir)) return;
		Files.walk(tempDir)
			.sorted(java.util.Comparator.reverseOrder())
			.forEach(p -> { try { Files.delete(p); } catch (Exception e) { /* ignore */ } });
	}

	// +---------+
	// | Helpers |
	// +---------+

	private TextFiles make() throws Exception {
		return make(new TextFiles.Config());
	}

	private TextFiles make(TextFiles.Config cfg) throws Exception {
		cfg.BasePath = tempDir.toString();
		return new TextFiles(cfg);
	}

	// +-------+
	// | write |
	// +-------+

	@Test
	public void testWriteAndRead() throws Exception {
		TextFiles txt = make();
		txt.put("test.txt", "Hello, World!");
		TextFiles.ReadInfo info = txt.read("test.txt");
		assertEquals("Hello, World!", info.Contents);
		assertEquals(13L, info.FileLength);
		assertEquals(13L, info.ContentsLength);
		assertEquals(0L, info.StartIndex);
	}

	@Test
	public void testWriteInfoReturnValue() throws Exception {
		TextFiles txt = make();
		TextFiles.WriteInfo info = txt.put("test.txt", "Hello");
		assertEquals(5L, info.WrittenLength);
		assertEquals(5L, info.RequestedLength);
	}

	@Test
	public void testWriteOverwritesExistingFile() throws Exception {
		TextFiles txt = make();
		txt.put("test.txt", "original");
		txt.put("test.txt", "replaced");
		TextFiles.ReadInfo info = txt.read("test.txt");
		assertEquals("replaced", info.Contents);
	}

	// +--------+
	// | append |
	// +--------+

	@Test
	public void testAppendToExistingFile() throws Exception {
		TextFiles txt = make();
		txt.put("test.txt", "Hello");
		txt.append("test.txt", ", World!");
		TextFiles.ReadInfo info = txt.read("test.txt");
		assertEquals("Hello, World!", info.Contents);
	}

	@Test
	public void testAppendInfoReturnValue() throws Exception {
		TextFiles txt = make();
		txt.put("test.txt", "Hello");
		TextFiles.WriteInfo info = txt.append("test.txt", ", World!");
		assertEquals(8L, info.WrittenLength);
		assertEquals(8L, info.RequestedLength);
	}

	// +------+
	// | read |
	// +------+

	@Test
	public void testReadWithLengthLimit() throws Exception {
		TextFiles txt = make();
		txt.put("test.txt", "Hello, World!");
		TextFiles.ReadInfo info = txt.read("test.txt", 0, 5);
		assertEquals("Hello", info.Contents);
		assertEquals(5L, info.ContentsLength);
		assertEquals(0L, info.StartIndex);
		assertEquals(13L, info.FileLength);
	}

	@Test
	public void testReadBeyondEndClampsToFileLength() throws Exception {
		TextFiles txt = make();
		txt.put("test.txt", "Hello");
		// request more chars than available
		TextFiles.ReadInfo info = txt.read("test.txt", 0, 100);
		assertEquals("Hello", info.Contents);
		assertEquals(5L, info.FileLength);
	}

	// +-----------+
	// | MaxLength |
	// +-----------+

	@Test
	public void testMaxReadLengthTruncatesContent() throws Exception {
		TextFiles.Config cfg = new TextFiles.Config();
		cfg.MaxReadLength = 5;
		TextFiles txt = make(cfg);

		txt.put("test.txt", "Hello, World!");
		TextFiles.ReadInfo info = txt.read("test.txt");

		assertEquals("Hello", info.Contents);
		assertEquals(5L, info.ContentsLength);
		assertEquals(13L, info.FileLength); // full file length still reported
	}

	@Test
	public void testMaxWriteLengthTruncatesWrite() throws Exception {
		TextFiles.Config cfg = new TextFiles.Config();
		cfg.MaxWriteLength = 5;
		TextFiles txt = make(cfg);

		TextFiles.WriteInfo writeInfo = txt.put("test.txt", "Hello, World!");
		assertEquals(5L, writeInfo.WrittenLength);
		assertEquals(13L, writeInfo.RequestedLength);

		TextFiles.ReadInfo readInfo = txt.read("test.txt");
		assertEquals("Hello", readInfo.Contents);
	}

	// +----------+
	// | ReadOnly |
	// +----------+

	@Test
	public void testReadOnlyBlocksPut() throws Exception {
		TextFiles.Config cfg = new TextFiles.Config();
		cfg.ReadOnly = true;
		TextFiles txt = make(cfg);

		try {
			txt.put("test.txt", "content");
			fail("Expected exception for read-only put");
		}
		catch (Exception e) {
			assertTrue(e.getMessage().contains("read only"));
		}
	}

	@Test
	public void testReadOnlyBlocksAppend() throws Exception {
		TextFiles.Config cfg = new TextFiles.Config();
		cfg.ReadOnly = true;
		TextFiles txt = make(cfg);

		try {
			txt.append("test.txt", "content");
			fail("Expected exception for read-only append");
		}
		catch (Exception e) {
			assertTrue(e.getMessage().contains("read only"));
		}
	}

	// +---------------+
	// | Path security |
	// +---------------+

	@Test
	public void testPathTraversalBlocked() throws Exception {
		TextFiles txt = make();

		try {
			txt.read("../outside.txt");
			fail("Expected exception for path traversal");
		}
		catch (Exception e) {
			assertTrue(e.getMessage().contains("Invalid Path"));
		}
	}

	@Test
	public void testAbsolutePathOutsideBaseBlocked() throws Exception {
		TextFiles txt = make();

		try {
			txt.read("/etc/passwd");
			fail("Expected exception for absolute path outside base");
		}
		catch (Exception e) {
			assertTrue(e.getMessage().contains("Invalid Path"));
		}
	}

	// +------+
	// | list |
	// +------+

	@Test
	public void testListFilesReturnsAll() throws Exception {
		TextFiles txt = make();
		txt.put("a.txt", "aaa");
		txt.put("b.txt", "bbb");
		txt.put("c.txt", "ccc");

		List<TextFiles.FileInfo> infos = txt.listFileInfos(".");
		assertEquals(3, infos.size());
	}

	@Test
	public void testListFilesMaxResults() throws Exception {
		TextFiles txt = make();
		txt.put("a.txt", "aaa");
		txt.put("b.txt", "bbb");
		txt.put("c.txt", "ccc");

		List<TextFiles.FileInfo> infos = txt.listFileInfos(".", 2);
		assertEquals(2, infos.size());
	}

	@Test
	public void testListFileInfoHasCorrectMetadata() throws Exception {
		TextFiles txt = make();
		txt.put("hello.txt", "Hello!");

		List<TextFiles.FileInfo> infos = txt.listFileInfos(".");
		assertEquals(1, infos.size());

		TextFiles.FileInfo info = infos.get(0);
		assertEquals("hello.txt", info.Name);
		assertFalse(info.IsDirectory);
		assertEquals(6L, info.SizeBytes);
		assertNotNull(info.Modified);
	}

	@Test
	public void testListEmptyDirectory() throws Exception {
		TextFiles txt = make();
		List<TextFiles.FileInfo> infos = txt.listFileInfos(".");
		assertEquals(0, infos.size());
	}
}
