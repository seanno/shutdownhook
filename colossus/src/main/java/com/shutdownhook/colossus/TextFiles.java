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

import com.shutdownhook.toolbox.Easy;

public class TextFiles
{
	// +------------------+
	// | Setup & Teardown |
	// +------------------+
	
	public static class Config
	{
		public String BasePath = "/tmp";
		public boolean ReadOnly = false;
	}
	
	public TextFiles(Config cfg) throws Exception {
		this.cfg = cfg;
		this.base = Paths.get(cfg.BasePath).toAbsolutePath().normalize();
	}

	// +------+
	// | read |
	// +------+

	public String read(String inputPath) throws Exception {
		return(Easy.stringFromFile(verifyPathString(inputPath)));
	}

	// +--------+
	// | put    |
	// | append |
	// +--------+

	public void put(String inputPath, String contents) throws Exception {
		if (cfg.ReadOnly) throw new Exception("can't write to read only TextFiles");
		Easy.stringToFile(verifyPathString(inputPath), contents);
	}

	public void append(String inputPath, String contents) throws Exception {
		if (cfg.ReadOnly) throw new Exception("can't write to read only TextFiles");
		Easy.appendStringToFile(verifyPathString(inputPath), contents);
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
		return(target);
	}

	private String verifyPathString(String input) throws Exception {
		return(verifyPath(input).toString());
	}

	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	private Path base;

	private final static Logger log = Logger.getLogger(TextFiles.class.getName());
}
