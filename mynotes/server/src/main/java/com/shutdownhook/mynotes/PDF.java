/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
**
** >>>> default configuration requires pdftohtml 
*/

package com.shutdownhook.mynotes;

import java.io.InputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.google.gson.Gson;

import com.shutdownhook.toolbox.Easy;

public class PDF
{
	// +----------------+
	// | Config & Setup |
	// +----------------+

	public static class Config
	{
		// param is path to input pdf
		public String CommandFormat = "pdftohtml -dataurls -c -s %s";
		public String OutputFileSuffix = "-html.html";

		public static Config fromJson(String json) {
			return(new Gson().fromJson(json, Config.class));
		}
	}
	
	public PDF(Config cfg) throws Exception {
		this.cfg = cfg;
	}

	// +----------------------+
	// | convertToHtml(Async) |
	// +----------------------+

	public CompletableFuture<File> convertToHtmlAsync(InputStream pdfStream) {
		return(Exec.runAsync("convertToHtml", new Exec.AsyncOperation() {
			public File execute() throws Exception {
				return(convertToHtml(pdfStream));
			}
		}));
	}

	public File convertToHtml(InputStream pdfStream) throws Exception {

		File pdfFile = null;

		try {

			// 1. decode the input stream to a temp file
			pdfFile = File.createTempFile("exp", ".pdf");
			pdfFile.deleteOnExit();

			Base64.Decoder decoder = Base64.getDecoder();
			Files.copy(decoder.wrap(pdfStream), pdfFile.toPath(),
					   StandardCopyOption.REPLACE_EXISTING);

			// 2. do the conversion, saving to another temp file
			String command = String.format(cfg.CommandFormat, pdfFile.getAbsolutePath());
			String result = Easy.stringFromProcess(command);

			String outputPath = pdfFile.getAbsolutePath();
			int ich = outputPath.lastIndexOf(".");
			outputPath = outputPath.substring(0, ich) + cfg.OutputFileSuffix;

			File htmFile = new File(outputPath);
			if (!htmFile.exists()) {
				throw new Exception("pdftohtml failed; output: " + result);
			}
			
			htmFile.deleteOnExit();
			
			// 3. and return the file
			return(htmFile);
		}
		finally {
			quietDelete(pdfFile);
		}
	}
	
	// +---------+
	// | Helpers |
	// +---------+
		
	private static void quietDelete(File f) {
		if (f == null) return;
		try { f.delete(); }
		catch (Exception e) { /* eat it */ }
	}
	
	// +---------+
	// | Members |
	// +---------+

	private Config cfg;

	private final static Logger log = Logger.getLogger(PDF.class.getName());
}
