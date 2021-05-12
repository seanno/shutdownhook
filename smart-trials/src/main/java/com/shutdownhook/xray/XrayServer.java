/*
** Read about this code at http://shutdownhook.com.
** No restrictions on use; no assurances or warranties either!
*/

package com.shutdownhook.xray;

import java.lang.reflect.Modifier;
import java.util.*;
import com.shutdownhook.toolbox.*;
import com.shutdownhook.toolbox.WebServer.*;
import com.shutdownhook.smart.*;
import com.google.gson.*;

public class XrayServer extends SmartServer
{
	public XrayServer(SmartServer.Config cfg) throws Exception {
		super(cfg);
	}

    public static void main(String[] args) throws Exception 
    {
		String json = Easy.stringFromSmartyPath("@xray.json");
		XrayServer server = new XrayServer(Config.fromJson(json));
		server.runSync();
    }
	
	@Override
	protected void registerAdditionalHandlers(WebServer server,
											  SmartEhr smart) throws Exception {

		server.registerHandler("/session", new SessionHandler(smart) {
			public void handle2(Request request, Response response,
							    SmartEhr.Session session) throws Exception {

				// make a gson that will include transients
				Gson gson = new GsonBuilder()
					.excludeFieldsWithModifiers(Modifier.STATIC) 
					.setPrettyPrinting()
					.create();

				response.setJson(gson.toJson(session));
			}
		});

		server.registerHandler("/fhir", new SessionHandler(smart) {
			public void handle2(Request request, Response response,
							    SmartEhr.Session session) throws Exception {

				String path = request.QueryParams.get("path");
				response.setJson(smart.getJson(session, path).toString());
			}
		});
	}

	@Override
	public void home(Request request, Response response,
					 SmartEhr.Session session, SmartEhr smart) throws Exception {

		StringBuilder sb = new StringBuilder();

		sb.append("<!DOCTYPE html>\n<html><head><style>li{margin-bottom:6px;}</style>");
		sb.append("<script type='text/javascript'>function go(){window.open('/fhir?path='+");
		sb.append("escape(document.getElementById('path').value));return(false);}</script>");
		sb.append("</head><body>");

		sb.append("<p>");
		sb.append("<b>Site</b>: ").append(Easy.htmlEncode(session.SiteId)).append("<br/>");
		sb.append("<b>Session</b>: ").append(session.Id.toString()).append("<br/>");
		sb.append("<b>UserResource</b>: ").append(Easy.htmlEncode(session.UserResource)).append("<br/>");
		sb.append("<b>Patient</b>: ").append(Easy.htmlEncode(session.PatientId)).append("<br/>");
		sb.append("</p>");

		sb.append("<ul>");
		sb.append("<li><a target='_blank' href='/session'>Session Detail</a></li>");
		appendFhirListItem(sb, session.UserResource);
		appendFhirListItem(sb, "Patient/" + session.PatientId);
		appendFhirListItem(sb, "Condition?patient=" + session.PatientId + "&category=problem-list-item");
		appendFhirListItem(sb, "MedicationRequest?patient=" + session.PatientId);

		sb.append("<li>Custom: <input type='text' id='path' style='width: 300px;' /> ");
		sb.append("<a href='#' onclick='return(go());'>request</a></li>");
											 
		appendFhirListItem(sb, "metadata");
		appendFhirListItem(sb, ".well-known/smart-configuration");
		sb.append("</ul>");

		sb.append("</body></html>");
		response.setHtml(sb.toString());
	}

	private static void appendFhirListItem(StringBuilder sb, String path) {
		sb.append("<li><a target='_blank' href='/fhir?path=")
			.append(Easy.urlEncode(path)).append("'>")
			.append(Easy.htmlEncode(path)).append("</a></li>");
	}
}
