/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.radio.azure;

import java.util.Optional;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import com.shutdownhook.toolbox.Easy;

public class Functions {

    @FunctionName("home")
    public HttpResponseMessage home(
	    @HttpTrigger(name = "req", methods = {HttpMethod.GET}, authLevel = AuthorizationLevel.ANONYMOUS)
		final HttpRequestMessage<Optional<String>> request, final ExecutionContext context) {

		HttpResponseMessage r = handleRequest(request, context, (response) -> {
			response.body(Global.getRadio().getStaticHtml(null));
			response.header("Content-Type", "text/html");
		});

		return(r);
    }

    @FunctionName("channel")
    public HttpResponseMessage channel(
	    @HttpTrigger(name = "req", methods = {HttpMethod.GET}, authLevel = AuthorizationLevel.ANONYMOUS)
		final HttpRequestMessage<Optional<String>> request, final ExecutionContext context) {

		HttpResponseMessage r = handleRequest(request, context, (response) -> {
			String name = request.getQueryParameters().get("channel");
			Model.Channel channel = Global.getRadio().getChannel(name);
			response.body(channel.toJson());
			response.header("Content-Type", "application/json");
		});

		return(r);
    }

    @FunctionName("playlist")
    public HttpResponseMessage playlist(
	    @HttpTrigger(name = "req", methods = {HttpMethod.GET}, authLevel = AuthorizationLevel.ANONYMOUS)
		final HttpRequestMessage<Optional<String>> request, final ExecutionContext context) {

		HttpResponseMessage r = handleRequest(request, context, (response) -> {
			String name = request.getQueryParameters().get("channel");
			Model.Playlist playlist = Global.getRadio().getPlaylist(name);
			response.body(playlist.toJson());
			response.header("Content-Type", "application/json");
		});

		return(r);
    }

    @FunctionName("addVideo")
    public HttpResponseMessage addVideo(
	    @HttpTrigger(name = "req", methods = {HttpMethod.GET}, authLevel = AuthorizationLevel.ANONYMOUS)
		final HttpRequestMessage<Optional<String>> request, final ExecutionContext context) {

		HttpResponseMessage r = handleRequest(request, context, (response) -> {
			String name = request.getQueryParameters().get("channel");
			String video = request.getQueryParameters().get("video");
			String who = request.getQueryParameters().get("who");

			String channelUrl = String.format("home?channel=%s&who=%s",
											  Easy.urlEncode(name),
											  Easy.urlEncode(who));

			Global.getRadio().addVideo(name, video, who);

			response.header("Content-Type", "text/html");
			response.body("<html><title>Added</title><body>" +
						  "Added OK! You can close this window or " +
						  "<a href=\"" + channelUrl + "\">start listening</a>." +
						  "</body></html>");
		});

		return(r);
    }

	// +---------+
	// | Helpers |
	// +---------+
	
	public interface RadioRequestHandler {
		public void handle(HttpResponseMessage.Builder response) throws Exception;
	}
	
	private HttpResponseMessage handleRequest(final HttpRequestMessage<Optional<String>> request,
											  final ExecutionContext context,
											  final RadioRequestHandler handler) {

        context.getLogger().info("Request for: " + request.getUri().toString());
		HttpResponseMessage.Builder response = request.createResponseBuilder(HttpStatus.OK);
		
		try {
			handler.handle(response);
		}
		catch (Exception e) {
			context.getLogger().warning(Easy.exMsg(e, "handleRequest", true));
			response.status(HttpStatus.INTERNAL_SERVER_ERROR);
		}

		return(response.build());
	}
}
