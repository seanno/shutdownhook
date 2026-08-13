//
// Mail.JAVA
//

package com.shutdownhook.colossus;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.logging.Logger;

import jakarta.mail.Address;
import jakarta.mail.Authenticator;
import jakarta.mail.FetchProfile;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.Transport;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.ReceivedDateTerm;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import com.google.gson.Gson;

import com.shutdownhook.toolbox.Easy;

public class Mail implements Closeable
{
	public static String INBOX = "INBOX";
	
	// +------------------+
	// | Setup & Teardown |
	// +------------------+

	public static class Config
	{
		public String Email;
		public String Password;

		public String SmtpHost;
		public int SmtpPort = 587;

		public String ImapHost;
		public int ImapPort = 993;
		
		public String[] SendWhiteList; // null = all, [] = none, else whitelist
		
		public static Config fromJson(String json) {
			return(new Gson().fromJson(json, Config.class));
		}

		public String toJson() {
			return(new Gson().toJson(this));
		}

	}

	public Mail(Config cfg) throws Exception {
		this(cfg, new Utility(new Utility.Config()));
	}

	public Mail(Config cfg, Utility utils) throws IllegalArgumentException {
		this.cfg = cfg;
		this.utils = utils;
		this.session = getSession();
		cleanupWhiteList(); 
	}

	public void close() {
		if (store != null) {
			try { store.close(); } catch (Exception e) { /* eat it */ }
		}
	}

	// +-------------+
	// | MessageData |
	// +-------------+

	public static class SavedPosition
	{
		public long Validity;
		public long UID;

		public static SavedPosition of(long validity, long uid) {
			SavedPosition pos = new SavedPosition();
			pos.Validity = validity;
			pos.UID = uid;
			return(pos);
		}
	}

	public static class SendMessageData
	{
		public String[] ToAddresses;
		public String Subject;
		public String Text;
	}

	public static class MessageData extends SendMessageData
	{
		public String[] FromAddresses;
		public Instant Received;
		public SavedPosition Position;
	}

	// +------+
	// | send |
	// +------+

	public boolean send(SendMessageData msg, boolean isHtml) {
		try {
			sendInternal(msg, isHtml);
			return(true);
		}
		catch (MessagingException e) {
			log.severe(Easy.exMsg(e, "send", false));
			return(false);
		}
	}

	public void sendInternal(SendMessageData msg, boolean isHtml)
		throws MessagingException, IllegalArgumentException {

		if (msg.ToAddresses == null || msg.ToAddresses.length == 0) {
			throw new IllegalArgumentException("missing to address");
		}
		
		MimeMessage mime = new MimeMessage(session);
		mime.setFrom(cfg.Email);

		int added = 0;
		for (String toAddr : msg.ToAddresses) {
			String cleanAddr = toAddr.trim().toLowerCase(Locale.ROOT);
			if (blockedAddress(cleanAddr)) {
				log.warning("Address not on whitelist: " + cleanAddr);
				continue;
			}
			mime.addRecipients(Message.RecipientType.TO, cleanAddr);
			++added;
		}

		if (added == 0) throw new IllegalArgumentException("no valid recipients");

		mime.setSubject(msg.Subject);
		mime.setText(msg.Text, "UTF-8", isHtml ? "html" : "plain");

		Transport.send(mime);
	}

	private boolean blockedAddress(String addr) {
		
		if (cfg.SendWhiteList == null) return(false);

		for (String okAddr : cfg.SendWhiteList) {
			if (addr.equals(okAddr)) return(false);
		}

		return(true);
	}

	// +---------------------+
	// | downloadFolderSince |
	// | getFolderSince      |
	// +---------------------+

	// note "daysBack" is day-granular. So if you pass "since 1/1/25 13:00:00" you still get everything
	// on that date before 1pm ... SavedPosition is intended to help avoid repeat processing

	public static class GetResult
	{
		public SavedPosition LastPosition = SavedPosition.of(-1L, -1L);
		public List<MessageData> MessageDatas = new ArrayList<MessageData>();
	}

	// returns message objects with Text redacted. Full messages files are written into downloadPath
	// with the pattern (UID.txt). If downloadPath is null, files will be written to a unique temp
	// directory. Otherwise the caller is responsible for ensuring there will not be name collisions.
	//
	// If non-null, savedPositionPath is the path to a file that (if it exists) contains saved position
	// information from a previous run. The file is updated with new information by this call.
	
	public GetResult downloadFolderSince(String downloadPath, String folderPath,
											int daysBack, String savedPositionPath, boolean markRead)
		throws IllegalArgumentException, IOException {

		// setup download path
		Path target = (downloadPath == null ? Files.createTempDirectory("colossus") : Paths.get(downloadPath));
		if (!Files.exists(target)) Files.createDirectory(target);

		// read saved position file
		SavedPosition savedPosition = null;
		Path pos = (savedPositionPath == null ? null : Paths.get(savedPositionPath));
		
		if (pos != null && Files.exists(pos)) {
			try {
				String json = Easy.stringFromFile(pos.toString());
				savedPosition = utils.getGson().fromJson(json, SavedPosition.class);
			}
			catch (Exception e) {
				log.warning(Easy.exMsg(e, "savedPosition load; ignoring", false));
			}
		}

		// do the work
		GetResult result = getFolderSinceInternal(target, folderPath, daysBack, savedPosition, markRead);

		// update saved position file
		if (result != null && pos != null) {
			try {
				Easy.stringToFile(pos.toString(), utils.getCompactGson().toJson(result.LastPosition));
			}
			catch (Exception e) {
				log.warning(Easy.exMsg(e, "savedPosition save; ignoring", false));
			}
		}

		return(result);
	}

	// returns full message objects
	
	public GetResult getFolderSince(String folderPath, int daysBack, SavedPosition position, boolean markRead) 
		throws IllegalArgumentException, IOException {

		return(getFolderSinceInternal(null, folderPath, daysBack, position, markRead));
	}

	// internal helper
	
	private GetResult getFolderSinceInternal(Path target, String folderPath,
											int daysBack, SavedPosition position, boolean markRead)
		
		throws IllegalArgumentException, IOException {

		if (daysBack < 1) throw new IllegalArgumentException("daysBack must be >= 1");
							  
		GetResult result = new GetResult();
		Folder folder = null;
		boolean success = false;

		try {
			folder = getStore().getFolder(folderPath);
			folder.open(markRead ? Folder.READ_WRITE : Folder.READ_ONLY);
			
			Date dateSince = computeDateSince(daysBack);
			long currentValidity = ((UIDFolder)folder).getUIDValidity();
			
			if (position == null || position.Validity != currentValidity) {
				
				if (position != null) {
					log.warning(String.format("Validity changed %d->%d, reset",
											  position.Validity, currentValidity));
				}
				
				result.LastPosition.Validity = currentValidity;
				result.LastPosition.UID = -1L;
			}
			else {
				result.LastPosition.Validity = position.Validity;
				result.LastPosition.UID = position.UID;
			}

			long savedUID = result.LastPosition.UID;

			Message[] messages = folder.search(new ReceivedDateTerm(ComparisonTerm.GE, dateSince));

			FetchProfile fp = new FetchProfile();
			fp.add(FetchProfile.Item.ENVELOPE);
			fp.add(FetchProfile.Item.CONTENT_INFO);
			folder.fetch(messages, fp);

			for (Message message : messages) {
				
				long uid = ((UIDFolder)folder).getUID(message);
				if (uid <= savedUID) continue;
				
				if (uid > result.LastPosition.UID) result.LastPosition.UID = uid;

				MessageData data = new MessageData();
				result.MessageDatas.add(data);
				
				data.Position = SavedPosition.of(result.LastPosition.Validity, uid);
				data.FromAddresses = parseAddresses(message.getFrom());
				data.ToAddresses = parseAddresses(message.getRecipients(Message.RecipientType.TO));
				data.Received = message.getReceivedDate().toInstant();
				data.Subject = message.getSubject();
				data.Text = parseMessageText(message);

				if (target != null) {
					Path path = target.resolve(String.format("%d.txt", data.Position.UID));
					Easy.stringToFile(path.toString(), utils.getCompactGson().toJson(data));
					data.Text = null; 
				}
				
				if (markRead) message.setFlag(Flags.Flag.SEEN, true);
			}
			
			success = true;
			return(result);
		}
		catch (MessagingException e) {
			log.severe(Easy.exMsg(e, "folder: " + folder, false));
			return(null);
		}
		finally {
			if (folder != null && folder.isOpen()) {
				try { folder.close(false); } catch (Exception e) { /* eat it */ }
			}
		}
	}

	private String[] parseAddresses(Address[] addresses) {
		if (addresses == null) return(null);
		List<String> parsed = new ArrayList<String>();

		for (Address a : addresses) {
			if (!(a instanceof InternetAddress)) continue;
			InternetAddress ia = (InternetAddress) a;
			if (ia.isGroup()) continue;
			String str = ia.getAddress();
			if (str != null) parsed.add(str.trim().toLowerCase(Locale.ROOT));
		}

		return(parsed.toArray(new String[parsed.size()]));
	}
	
	private Date computeDateSince(int daysBack) {
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DAY_OF_MONTH, 0 - daysBack);
		return(cal.getTime());
	}

	private String parseMessageText(Message message) {
		try {
			return extractText(message);
		}
		catch (Exception e) {
			log.severe(Easy.exMsg(e, "parseMessageText", false));
			return null;
		}
	}

	private String extractText(Part part) throws Exception {

		if (part.isMimeType("text/html")) return(Jsoup.parse((String) part.getContent()).text());
		if (part.isMimeType("text/*")) return((String) part.getContent());

		if (!part.isMimeType("multipart/*")) return(null);
		
		Multipart mp = (Multipart) part.getContent();
		
		// multipart/alternative, find one we like
		if (part.isMimeType("multipart/alternative")) {

			// note: reverse walk is to support spec that last = best
			for (int i = mp.getCount() - 1; i >= 0; --i) {

				Part bp = mp.getBodyPart(i);
				
				if (bp.isMimeType("text/html")) return(Jsoup.parse((String) bp.getContent()).text());
				if (bp.isMimeType("text/*")) return((String)bp.getContent());
			}
		}

		// multipart/mixed, multipart/related, etc. — skip attachments, return first text found
		// note that multipart/alternative without text will fall into here on purpose to try to find
		// something that works, like maybe another multipart?
		for (int i = 0; i < mp.getCount(); ++i) {

			Part bp = mp.getBodyPart(i);

			if (Part.ATTACHMENT.equalsIgnoreCase(bp.getDisposition())) continue;
			String result = extractText(bp);
			if (result != null) return(result);
		}

		return(null);
	}
	
	// +---------+
	// | Helpers |
	// +---------+

	private synchronized Store getStore() throws MessagingException, IllegalArgumentException {
		if (store == null) {
			store = session.getStore();
			store.connect();
		}
		return(store);
	}
	
	private Session getSession() throws IllegalArgumentException {

		if (cfg.Email == null) throw new IllegalArgumentException("cfg.Email is required");
		if (cfg.Password == null) throw new IllegalArgumentException("cfg.Password is required");

		if (cfg.SmtpHost == null && cfg.ImapHost == null) {
			throw new IllegalArgumentException("cfg.SmtpHost and/or cfg.ImapHost are required");
		}
		
		Properties props = new Properties();

		if (cfg.SmtpHost != null) {
			props.put("mail.transport.protocol", "smtp");
			props.put("mail.smtp.host", cfg.SmtpHost);
			props.put("mail.smtp.port", Integer.toString(cfg.SmtpPort));
			props.put("mail.smtp.auth", "true");
			props.put("mail.smtp.starttls.enable", "true");
		}

		if (cfg.ImapHost != null) {
			props.put("mail.store.protocol", "imap"); 
			props.put("mail.imap.host", cfg.ImapHost);
			props.put("mail.imap.port", Integer.toString(cfg.ImapPort));
			props.put("mail.imap.ssl.enable", "true");
		}

		return(Session.getInstance(props, new Authenticator() {
			protected PasswordAuthentication getPasswordAuthentication() {
				return(new PasswordAuthentication(cfg.Email, cfg.Password));
			}
		}));
	}

	void cleanupWhiteList() {
		
		if (cfg.SendWhiteList == null) return;

		for (int i = 0; i < cfg.SendWhiteList.length; ++i) {
			cfg.SendWhiteList[i] = cfg.SendWhiteList[i].trim().toLowerCase(Locale.ROOT);
		}
	}

	// +------------+
	// | Entrypoint |
	// +------------+

	public static void main(String[] args) throws Exception {

		if (args.length == 0) {
			usage();
			return;
		}
		
		Config cfg = Config.fromJson(Easy.stringFromFile(args[0]));
		Utility utils = new Utility(new Utility.Config());
		Mail mail = new Mail(cfg, utils);

		try {
			String cmd = args[1].trim().toLowerCase(Locale.ROOT);
			switch (cmd) {
				case "send":
					String json = Easy.stringFromFile(args[2]);
					SendMessageData msg = utils.getGson().fromJson(json, SendMessageData.class);
					boolean isHtml = (args.length >= 4 ? Boolean.parseBoolean(args[3]) : false);
					System.out.println(String.valueOf(mail.send(msg, isHtml)));
					break;

				case "list":
				case "download":
					String folder = (args.length >= 3 ? args[2] : "INBOX");
					boolean markRead = (args.length >= 4 ? Boolean.parseBoolean(args[3]) : false);
					GetResult result = null;
					
					if (cmd.equals("list")) {
						SavedPosition pos = (args.length >= 5
											 ? utils.getGson().fromJson(args[4], SavedPosition.class)
											 : null);

						result = mail.getFolderSince(folder, 1, pos, markRead);
					}
					else {
						String targetPath = (args.length >= 5 ? args[4] : null);
						String posPath = (args.length >= 6 ? args[5] : null);
						
						result = mail.downloadFolderSince(targetPath, folder, 1, posPath, markRead);
					}
					
					System.out.println(utils.getGson().toJson(result));
					break;

				default:
					System.out.println("huh?");
					break;
			}
		}
		finally {
			utils.close();
			mail.close();
		}
	}

	private static void usage() {
		System.out.println("java -cp [JAR] com.shutdownhook.colossus.Mail [Config] send [SendMessageData] [isHTML]");
		System.out.println("java -cp [JAR] com.shutdownhook.colossus.Mail [Config] list [Folder] [MarkRead] [posJson]");
		System.out.println("java -cp [JAR] com.shutdownhook.colossus.Mail [Config] download [Folder] [MarkRead] [targetPath] [posPath]");
	}

	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	private Utility utils;
	private Session session;
	private Store store;
	
	private final static Logger log = Logger.getLogger(Mail.class.getName());
}
