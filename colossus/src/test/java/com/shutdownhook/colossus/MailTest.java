//
// MAILTEST.JAVA
//

package com.shutdownhook.colossus;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.Properties;

import jakarta.mail.Message;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class MailTest
{
	// +-------+
	// | Setup |
	// +-------+

	private Mail mail;
	private Session session;
	private Method parseMessageText;

	@Before
	public void setUp() throws Exception {
		Mail.Config cfg = new Mail.Config();
		cfg.Email = "test@example.com";
		cfg.Password_S = "password";
		cfg.ImapHost = "imap.example.com";
		mail = new Mail(cfg);

		session = Session.getInstance(new Properties());

		parseMessageText = Mail.class.getDeclaredMethod("parseMessageText", Message.class);
		parseMessageText.setAccessible(true);
	}

	private String parse(MimeMessage msg) throws Exception {
		// round-trip through bytes so getContent() works correctly on all part types
		msg.saveChanges();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		msg.writeTo(baos);
		MimeMessage parsed = new MimeMessage(session, new ByteArrayInputStream(baos.toByteArray()));
		return((String) parseMessageText.invoke(mail, parsed));
	}

	private MimeMessage simpleMessage(String contentType, String content) throws Exception {
		MimeMessage msg = new MimeMessage(session);
		msg.setContent(content, contentType);
		return(msg);
	}

	// +------------+
	// | text/plain |
	// +------------+

	@Test
	public void testPlainText() throws Exception {
		assertEquals("Hello world", parse(simpleMessage("text/plain", "Hello world")));
	}

	// +-----------+
	// | text/html |
	// +-----------+

	@Test
	public void testHtmlTagsStripped() throws Exception {
		assertEquals("Hello world", parse(simpleMessage("text/html", "<p>Hello <b>world</b></p>")));
	}

	@Test
	public void testHtmlNestedTagsStripped() throws Exception {
		assertEquals("one two three", parse(simpleMessage("text/html",
			"<div><p>one</p> <span>two</span> <a href='x'>three</a></div>")));
	}

	// +-----------------------------+
	// | non-text / non-multipart   |
	// +-----------------------------+

	@Test
	public void testNonTextReturnsNull() throws Exception {
		assertNull(parse(simpleMessage("application/octet-stream", "data")));
	}

	// +-----------------------+
	// | multipart/alternative |
	// +-----------------------+

	@Test
	public void testAlternativePrefersHtmlOverPlain() throws Exception {
		// RFC 2046: parts ordered least-preferred first, so plain before html
		MimeMultipart mp = new MimeMultipart("alternative");

		MimeBodyPart plain = new MimeBodyPart();
		plain.setContent("plain text", "text/plain");
		mp.addBodyPart(plain);

		MimeBodyPart html = new MimeBodyPart();
		html.setContent("<p>html text</p>", "text/html");
		mp.addBodyPart(html);

		MimeMessage msg = new MimeMessage(session);
		msg.setContent(mp);

		assertEquals("html text", parse(msg));
	}

	@Test
	public void testAlternativePlainWhenNoHtml() throws Exception {
		MimeMultipart mp = new MimeMultipart("alternative");

		MimeBodyPart plain = new MimeBodyPart();
		plain.setContent("just plain", "text/plain");
		mp.addBodyPart(plain);

		MimeMessage msg = new MimeMessage(session);
		msg.setContent(mp);

		assertEquals("just plain", parse(msg));
	}

	@Test
	public void testAlternativeFallsThroughToNestedMultipart() throws Exception {
		// Alternative with no text parts — should fall through to general loop
		// and recurse into a nested multipart to find text
		MimeMultipart inner = new MimeMultipart("mixed");
		MimeBodyPart innerBody = new MimeBodyPart();
		innerBody.setContent("nested plain", "text/plain");
		inner.addBodyPart(innerBody);

		MimeBodyPart innerPart = new MimeBodyPart();
		innerPart.setContent(inner);

		MimeBodyPart imagePart = new MimeBodyPart();
		imagePart.setContent("fake image data", "image/png");

		MimeMultipart alt = new MimeMultipart("alternative");
		alt.addBodyPart(imagePart);
		alt.addBodyPart(innerPart);

		MimeMessage msg = new MimeMessage(session);
		msg.setContent(alt);

		assertEquals("nested plain", parse(msg));
	}

	// +----------------+
	// | multipart/mixed |
	// +----------------+

	@Test
	public void testMixedSkipsTextAttachment() throws Exception {
		MimeMultipart mp = new MimeMultipart("mixed");

		MimeBodyPart body = new MimeBodyPart();
		body.setContent("message body", "text/plain");
		mp.addBodyPart(body);

		MimeBodyPart attachment = new MimeBodyPart();
		attachment.setContent("attachment content", "text/plain");
		attachment.setDisposition(Part.ATTACHMENT);
		attachment.setFileName("file.txt");
		mp.addBodyPart(attachment);

		MimeMessage msg = new MimeMessage(session);
		msg.setContent(mp);

		assertEquals("message body", parse(msg));
	}

	@Test
	public void testMixedSkipsNonTextAttachment() throws Exception {
		MimeMultipart mp = new MimeMultipart("mixed");

		MimeBodyPart body = new MimeBodyPart();
		body.setContent("<p>html body</p>", "text/html");
		mp.addBodyPart(body);

		MimeBodyPart attachment = new MimeBodyPart();
		attachment.setContent("pdf data", "application/pdf");
		attachment.setDisposition(Part.ATTACHMENT);
		attachment.setFileName("doc.pdf");
		mp.addBodyPart(attachment);

		MimeMessage msg = new MimeMessage(session);
		msg.setContent(mp);

		assertEquals("html body", parse(msg));
	}

	// +-----------+
	// | Whitelist |
	// +-----------+

	private Method blockedAddressMethod;

	private Mail mailWithWhitelist(String[] whitelist) throws Exception {
		Mail.Config cfg = new Mail.Config();
		cfg.Email = "test@example.com";
		cfg.Password_S = "password";
		cfg.ImapHost = "imap.example.com";
		cfg.SendWhiteList = whitelist;
		return new Mail(cfg);
	}

	private boolean blocked(Mail m, String addr) throws Exception {
		if (blockedAddressMethod == null) {
			blockedAddressMethod = Mail.class.getDeclaredMethod("blockedAddress", String.class);
			blockedAddressMethod.setAccessible(true);
		}
		return (boolean) blockedAddressMethod.invoke(m, addr);
	}

	@Test
	public void testWhitelistNullAllowsAll() throws Exception {
		Mail m = mailWithWhitelist(null);
		assertFalse(blocked(m, "anyone@example.com"));
	}

	@Test
	public void testWhitelistEmptyBlocksAll() throws Exception {
		Mail m = mailWithWhitelist(new String[0]);
		assertTrue(blocked(m, "anyone@example.com"));
	}

	@Test
	public void testWhitelistMatchNotBlocked() throws Exception {
		Mail m = mailWithWhitelist(new String[]{"allowed@example.com"});
		assertFalse(blocked(m, "allowed@example.com"));
	}

	@Test
	public void testWhitelistNoMatchBlocked() throws Exception {
		Mail m = mailWithWhitelist(new String[]{"allowed@example.com"});
		assertTrue(blocked(m, "other@example.com"));
	}

	@Test
	public void testWhitelistCaseNormalized() throws Exception {
		// cleanupWhiteList lowercases entries; sendInternal lowercases the addr before calling blocked
		Mail m = mailWithWhitelist(new String[]{"Allowed@Example.COM"});
		assertFalse(blocked(m, "allowed@example.com"));
	}

	@Test
	public void testWhitelistTrimmed() throws Exception {
		Mail m = mailWithWhitelist(new String[]{"  allowed@example.com  "});
		assertFalse(blocked(m, "allowed@example.com"));
	}

	@Test
	public void testMixedWithAlternativeBodyAndAttachment() throws Exception {
		// Common real-world structure: mixed outer, alternative body, binary attachment
		MimeMultipart alternative = new MimeMultipart("alternative");

		MimeBodyPart plain = new MimeBodyPart();
		plain.setContent("plain text", "text/plain");
		alternative.addBodyPart(plain);

		MimeBodyPart html = new MimeBodyPart();
		html.setContent("<p>html text</p>", "text/html");
		alternative.addBodyPart(html);

		MimeBodyPart altPart = new MimeBodyPart();
		altPart.setContent(alternative);

		MimeBodyPart attachment = new MimeBodyPart();
		attachment.setContent("pdf data", "application/pdf");
		attachment.setDisposition(Part.ATTACHMENT);
		attachment.setFileName("doc.pdf");

		MimeMultipart mixed = new MimeMultipart("mixed");
		mixed.addBodyPart(altPart);
		mixed.addBodyPart(attachment);

		MimeMessage msg = new MimeMessage(session);
		msg.setContent(mixed);

		assertEquals("html text", parse(msg));
	}
}
