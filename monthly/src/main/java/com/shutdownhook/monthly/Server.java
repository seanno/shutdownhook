/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.monthly;

import java.io.Closeable;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

import com.google.gson.Gson;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.Template;
import com.shutdownhook.toolbox.WebRequests;
import com.shutdownhook.toolbox.WebServer;
import com.shutdownhook.toolbox.WebServer.Request;
import com.shutdownhook.toolbox.WebServer.Response;

public class Server implements Closeable
{
	// +----------------+
	// | Config & Setup |
	// +----------------+

	public static class Config
	{
		public WebServer.Config WebServer = new WebServer.Config();
		public WebRequests.Config WebRequests = new WebRequests.Config();

		public String LocalZone = null; // defaults to LOCAL_ZONE

		public String LoggingConfigPath = "@logging.properties";
		public String CalendarUrl = "/";
		
		public static Config fromJson(String json) {
			return(new Gson().fromJson(json, Config.class));
		}
	}
	
	public Server(Config cfg) throws Exception {
		
		this.cfg = cfg;
		this.gson = new Gson();
		this.insta = new Instagram(cfg.WebRequests);
		
		this.zone = (cfg.LocalZone == null ? ZoneId.systemDefault()
					 : ZoneId.of(cfg.LocalZone));

		log.info(String.format("Using timezone %s (from %s)",
							   this.zone, cfg.LocalZone));

		setupWebServer();
	}
	
	private void setupWebServer() throws Exception {

		server = WebServer.create(cfg.WebServer);

		registerCalendarHandler();
		server.registerEmptyHandler("/favicon.ico", 404);
	}

	// +----------------+
	// | Server Control |
	// +----------------+

	public void start() { server.start(); }
	public void runSync() throws Exception { server.runSync(); }
	public void close() { insta.close(); server.close(); }

	// +-------------------------+
	// | registerCalendarHandler |
	// +-------------------------+

	private final static String CAL_TEMPLATE = "calendar.html.tmpl";
	private final static String CAL_TKN_USERNAME = "USERNAME";
	private final static String CAL_TKN_MONTHYEAR = "MONTHYEAR";
	private final static String CAL_TKN_DAYCOL = "DAYCOL";
	private final static String CAL_TKN_DAYROW = "DAYROW";
	private final static String CAL_TKN_DAYNUM = "DAYNUM";
	private final static String CAL_TKN_DAYIMAGE = "DAYIMAGE";

	private final static String CAL_DAYIMAGE_FMT =
		"<a href='%s'><img src='%s' title='%d - %s' /></a>";

	private void registerCalendarHandler() throws Exception {

		String templateText = Easy.stringFromResource(CAL_TEMPLATE);
		final Template template = new Template(templateText);
		
		server.registerHandler(cfg.CalendarUrl, new WebServer.Handler() {
				
			public void handle(Request request, Response response) throws Exception {

				int month = Integer.parseInt(request.QueryParams.get("m"));
				int year = Integer.parseInt(request.QueryParams.get("y"));
				
				final Map<Integer, List<Instagram.ProcessedPost>> postMap =
					insta.getProcessedPostsForMonth(request.User.Token, zone, month, year);

				if (postMap == null) {
					forceReauth(request, response);
					return;
				}

				final String userName = insta.getUserInfo(request.User.Token).username;
													
				response.setHtml(renderTemplate(template, userName, month, year, postMap));
			}
		});
	}

	private String renderTemplate(Template template,
								  String userName, int month, int year,
								  Map<Integer, List<Instagram.ProcessedPost>> postMap)
		throws Exception {

		final Map tokens = new HashMap<String,String>();
		tokens.put(CAL_TKN_USERNAME, userName);

		String monthName = Month.of(month).getDisplayName(TextStyle.FULL, Locale.getDefault());
		tokens.put(CAL_TKN_MONTHYEAR, String.format("%s %d", monthName, year));

		String html = template.render(tokens, new Template.TemplateProcessor() {

			private int week = 1;
			private int day = 1;
			private LocalDate d = LocalDate.of(year, month, 1);
				
			public boolean repeat(String[] args, int counter) {

				if (d.getMonthValue() != month) return(false);

				int dowVal = d.getDayOfWeek().getValue();
				tokens.put(CAL_TKN_DAYCOL, Integer.toString((dowVal == 7 ? 1 : dowVal + 1)));
				tokens.put(CAL_TKN_DAYROW, Integer.toString(week + 1));
				tokens.put(CAL_TKN_DAYNUM, Integer.toString(day));

				String anchor = "";
				if (postMap.containsKey(day)) {

					Instagram.ProcessedPost post = postMap.get(day).get(0);
					
					anchor = String.format(CAL_DAYIMAGE_FMT, post.TargetUrl,
										   post.ImageUrl, day, post.Caption);
				}
												  
				tokens.put(CAL_TKN_DAYIMAGE, anchor);
				
				d = d.plusDays(1);
				if (d.getDayOfWeek().equals(DayOfWeek.SUNDAY)) ++week;
				++day;
				
				return(true);
			}

			public String token(String token, String args) throws Exception {

				// nyi
				log.warning("UNKNOWN TOKEN: " + token);
				return("");
			}
		});

		return(html);
	}
	
	// +---------+
	// | Helpers |
	// +---------+

	private void forceReauth(Request request, Response response) throws Exception {

		String url = String.format("%s?rnd=%d%s", request.Path, System.currentTimeMillis(),
								   (request.QueryString == null ? "" : "&" + request.QueryString));

		response.deleteSessionCookie(cfg.WebServer.OAuth2CookieName, request);
		response.redirect(url);
	}

	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	private WebServer server;
	private Gson gson;
	private Instagram insta;
	private ZoneId zone;

	private final static Logger log = Logger.getLogger(Server.class.getName());
}
