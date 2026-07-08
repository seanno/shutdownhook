//
// TEXTFILES.JAVA
//

package com.shutdownhook.colossus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

import com.google.gson.JsonObject;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.WebRequests;

public class TextFiles
{
	// +------------------+
	// | Setup & Teardown |
	// +------------------+
	
	public static class Config
	{
		public String BasePath = "/tmp";
		public boolean ReadOnly = false;
		public int MaxReadLength = 0; // unlimited
		public int MaxWriteLength = 0; // unlimited
	}
	
	public TextFiles(Config cfg) throws Exception {
		this.cfg = cfg;
		this.base = Paths.get(cfg.BasePath).toAbsolutePath().normalize();
	}

	// +------+
	// | grep |
	// +------+

	public String grep(String inputPath, String regex, boolean caseInsensitive) throws Exception {

		String cmd = String.format("grep -E %s '%s' '%s'",
								   (caseInsensitive ? "-i" : ""),
								   regex, verifyPathString(inputPath));

		return(Easy.stringFromProcess(cmd));
	}

	// +------+
	// | read |
	// +------+

	public static class ReadInfo
	{
		public String Contents;
		public long StartIndex;
		public long ContentsLength;
		public long FileLength;
	}
	
	public ReadInfo read(String inputPath) throws Exception {
		return(read(inputPath, 0, 0));
	}

	public ReadInfo read(String inputPath, int ichStart, int cch) throws Exception {

		String contents = Easy.stringFromFile(verifyPathString(inputPath));

		if (ichStart > contents.length()) throw new Exception("Can't read beyond end of file");
							
		int ichMac = ichStart + (cch == 0 ? contents.length() : ichStart + cch);
		if (ichMac > contents.length()) ichMac = contents.length();

		if (cfg.MaxReadLength != 0 && (ichMac - ichStart) > cfg.MaxReadLength) {
			ichMac = ichStart + cfg.MaxReadLength;
		}

		ReadInfo info = new ReadInfo();
		info.Contents = contents.substring(ichStart,ichMac); // java is smart if this is a nop
		info.StartIndex = ichStart;
		info.ContentsLength = (ichMac - ichStart);
		info.FileLength = contents.length();

		return(info);
	}

	// +--------+
	// | put    |
	// | append |
	// +--------+

	public static class WriteInfo
	{
		public long WrittenLength;
		public long RequestedLength;
	}
	
	public WriteInfo put(String inputPath, String contents) throws Exception {
		if (cfg.ReadOnly) throw new Exception("can't write to read only TextFiles");
		String output = getWritableString(contents);
		Easy.stringToFile(verifyPathString(inputPath), output);
		return(makeWriteInfo(output.length(), contents.length()));
	}

	public WriteInfo append(String inputPath, String contents) throws Exception {
		if (cfg.ReadOnly) throw new Exception("can't write to read only TextFiles");
		String output = getWritableString(contents);
		Easy.appendStringToFile(verifyPathString(inputPath), output);
		return(makeWriteInfo(output.length(), contents.length()));
	}

	private WriteInfo makeWriteInfo(long cchWritten, long cchRequested) {
		WriteInfo info = new WriteInfo();
		info.WrittenLength = cchWritten;
		info.RequestedLength = cchRequested;
		return(info);
	}

	// +----------+
	// | download |
	// +----------+

	public long download(String inputPath, String url, boolean extractText, WebRequests webRequests) throws Exception {

		if (cfg.ReadOnly) throw new Exception("can't write to read only TextFiles");
		WebRequests.Response response = webRequests.fetch(url);
		if (!response.successful()) response.throwException("TextFiles.download");

		String contents = response.Body;
		if (extractText) contents = extractTextFromHtml(contents);
		
		Easy.stringToFile(verifyPathString(inputPath), contents);
		return(contents.length());
	}

	private static String extractTextFromHtml(String body) {
		try {
			Document doc = Jsoup.parse(body);
			String cleanText = doc.body().text();
			return(cleanText);
		}
		catch (Exception e) {
			log.warning(Easy.exMsg(e, "jsoup", true));
			return(body);
		}
	}

	// +------+
	// | list |
	// +------+

	public static class FileInfo
	{
		public String Name;
		public boolean IsDirectory;
		public long SizeBytes;
		public Instant Modified;

		public static FileInfo fromPath(Path path) throws Exception {
			FileInfo info = new FileInfo();
			info.Name = path.getFileName().toString();
			info.IsDirectory = Files.isDirectory(path);
			info.SizeBytes = (info.IsDirectory ? 0L : Files.size(path));
			info.Modified = Files.getLastModifiedTime(path).toInstant();
			return(info);
		}
	}

	public List<FileInfo> listFileInfos(String inputPath, int maxResults) throws Exception {

		// get the list
		List<FileInfo> infos = new ArrayList<FileInfo>();
		for (Path path : listPaths(inputPath)) infos.add(FileInfo.fromPath(path));

		// always sort recent to the top
		infos.sort(Comparator.nullsLast(new Comparator<FileInfo>() {
			public int compare(FileInfo info1, FileInfo info2) {
				return(info2.Modified.compareTo(info1.Modified)); // reverse order
			}
		}));

		// maybe truncate
		if (maxResults > 0 && infos.size() > maxResults) {
			infos.subList(maxResults, infos.size()).clear();
		}

		return(infos);
	}

	public List<FileInfo> listFileInfos(String inputPath) throws Exception {
		return(listFileInfos(inputPath, 0));
	}
	
	private List<Path> listPaths(String inputPath) throws Exception {
		return(Files.list(verifyPath(inputPath)).toList());
	}

	// +---------+
	// | Helpers |
	// +---------+

	private Path verifyPath(String input) throws Exception {
		Path target = base.resolve(input).normalize();
		if (!target.startsWith(base)) throw new Exception("Invalid Path: " + input);

		Path parent = target.getParent();
		if (parent != null) Files.createDirectories(parent);
		
		return(target);
	}

	private String verifyPathString(String input) throws Exception {
		return(verifyPath(input).toString());
	}

	private String getWritableString(String input) {
		if (cfg.MaxWriteLength == 0 || input.length() <= cfg.MaxWriteLength) return(input);
		return(input.substring(0, cfg.MaxWriteLength));
	}

	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	private Path base;

	private final static Logger log = Logger.getLogger(TextFiles.class.getName());
}
