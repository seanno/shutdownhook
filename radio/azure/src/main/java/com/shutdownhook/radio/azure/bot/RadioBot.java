/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.radio.azure.bot;

import java.io.UnsupportedEncodingException;
import java.lang.IllegalArgumentException;
import java.net.URI;
import java.net.URLEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import com.microsoft.bot.builder.ActivityHandler;
import com.microsoft.bot.builder.Bot;
import com.microsoft.bot.builder.MessageFactory;
import com.microsoft.bot.builder.TurnContext;
import com.microsoft.bot.connector.Async;
import com.microsoft.bot.schema.Activity;
import com.microsoft.bot.schema.ActivityTypes;
import com.microsoft.bot.schema.ChannelAccount;
import com.microsoft.bot.schema.ResourceResponse;

import com.shutdownhook.radio.azure.Radio;
import com.shutdownhook.radio.azure.Global;

public class RadioBot extends ActivityHandler {

	public RadioBot(URI uri) {

		radioUrlBase = uri.resolve("home").toString() + "?channel=";

		objectMapper = new ObjectMapper()
			.enable(SerializationFeature.INDENT_OUTPUT)
			.findAndRegisterModules(); 
	}

	// +---------------+
	// | handleMessage |
	// +---------------+

	// --- helpMessage ---
	
	private void helpMessage(BotContext ctx) {
		// nyi
		String msg = "NYI; radio url is " + getRadioChannelUrl(ctx);
		ctx.Responses.add(MessageFactory.text(msg));
	}

	// --- debugMessage ---

	private void debugMessage(BotContext ctx) {
		
		if (!Global.booleanConfig("ALLOW_DEBUG_MSG", false)) {
			defaultMessage(ctx);
			return;
		}

		String msg;
		
		try {
			String activityJson = objectMapper.writeValueAsString(ctx.Turn.getActivity());
			msg = String.format("Activity: %s\n", activityJson);
		}
		catch (Exception e) {
			msg = "Exception generating debug msg: " + e.toString();
		}
		
		ctx.Responses.add(MessageFactory.text(msg));
	}

	// --- defaultMessage ---
	
	private void defaultMessage(BotContext ctx) {
		ctx.Responses.add(MessageFactory.text("Sorry, I didn't catch that. Try 'help' maybe?"));
	}
	
	// OK we are braindead simple here. First string of characters before a space is a command,
	// everything else is parameters specific to the command. Fancy parsing can be another project.

	private CompletableFuture<Void> handleMessage(TurnContext turnContext) {
		return(botHandler(turnContext, (ctx) -> {

			String msg = ctx.Turn.getActivity().getText().trim();
			int ichSpace = msg.indexOf(' ');
			
			String cmd = (ichSpace == -1 ? msg : msg.substring(0, ichSpace)).toLowerCase();
			String params = (ichSpace == -1 ? "" : msg.substring(ichSpace + 1));

			switch (cmd) {
			    case "debug": debugMessage(ctx); break;
			    case "help": helpMessage(ctx); break;
			    default: defaultMessage(ctx); break;
			}
			
		}));
	}
		
	// +--------------------+
	// | handleMembersAdded |
	// +--------------------+

	private CompletableFuture<Void> handleMembersAdded(TurnContext turnContext) {
		return(botHandler(turnContext, (ctx) -> {

			String botId = ctx.Turn.getActivity().getRecipient().getId();
			for (ChannelAccount member : ctx.Turn.getActivity().getMembersAdded()) {
				if (member.getId().equals(botId)) continue;
				ctx.Responses.add(MessageFactory.text("Hello " + member.getName() + "!"));
			}
			
		}));
	}
	
	// +------------+
	// | Dispatcher |
	// +------------+

	// Why am I intercepting here and duplicating some dispatching myself?
	// Because there is a bunch of boilerplate noise that I want to encapsulate,
	// and the library has made it hard to do that consistently. This works fine
	// and I like the flow control better anyways because of the weird nested
	// 'type' e.g., CONVERSATION_UPDATE is be both add and remove which is dumb.
	
    @Override
    public CompletableFuture<Void> onTurn(TurnContext turnContext) {
		
		if (turnContext == null ||
			turnContext.getActivity() == null ||
			turnContext.getActivity().getType() == null) {

			String msg = "turnContext must be !null and have activity + type";
			log.warning(msg);
			
			return(Async.completeExceptionally(new IllegalArgumentException(msg)));
		}

		// this is just a filter to interept messages we care about, not to 
		// transform anything ... prettification happens later (in botHandler)

		Activity activity = turnContext.getActivity();
		
		switch (activity.getType()) {
			
		    case ActivityTypes.MESSAGE:
				return(handleMessage(turnContext));

		    case ActivityTypes.CONVERSATION_UPDATE:
				if (activity.getMembersAdded() != null) {
					return(handleMembersAdded(turnContext));
				}
				break;
		}

		return(super.onTurn(turnContext));
	}

	// +---------------+
	// | Noise Control |
	// +---------------+

	public static class BotContext
	{
		public TurnContext Turn;
		public List<Activity> Responses;
		public Radio Radio;
	}

	public interface BotHandler {
		public void handle(BotContext context);
	}

	private CompletableFuture<Void> botHandler(TurnContext turnContext, BotHandler handler) {

		BotContext ctx = new BotContext();
		ctx.Turn = turnContext;
		ctx.Responses = new ArrayList<Activity>();

		try {
			ctx.Radio = Global.getRadio();
		}
		catch (Exception e) {
			return(Async.completeExceptionally(e));
		}

		handler.handle(ctx);

		return(turnContext
			   .sendActivities(ctx.Responses.toArray(new Activity[ctx.Responses.size()]))
			   .thenApply((rr) -> null));
	}

	// +-------------------+
	// | Helpers & Members |
	// +-------------------+

	private JsonNode getChannelData(BotContext ctx) {

		if (ctx.Turn.getActivity().getChannelData() == null) {
			return(null);
		}

		try {
			return(ctx.Turn.getActivity().getChannelData(JsonNode.class));
		}
		catch (Exception e) {
			log.warning("ChannelData not valid json? " + e.toString());
			return(null);
		}
	}
	
	private String getRadioChannelUrl(BotContext ctx) {

		String idEnc = ctx.Turn.getActivity().getChannelId();
		
		try { idEnc = URLEncoder.encode(idEnc, "UTF-8"); }
		catch (UnsupportedEncodingException e) { /* won't happen */ }

		return(radioUrlBase + idEnc);
	}
	
	private String radioUrlBase;
	private ObjectMapper objectMapper;
	
	// +-----------+
	// | Singleton |
	// +-----------+

	public static synchronized RadioBot singleton(URI uri) {
		if (bot == null) bot = new RadioBot(uri);
		return(bot);
	}

	private static RadioBot bot = null;

	private final static Logger log = Logger.getLogger(RadioBot.class.getName());
}
