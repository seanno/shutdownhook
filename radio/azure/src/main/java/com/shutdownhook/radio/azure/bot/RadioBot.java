/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.radio.azure.bot;

import java.io.IOException;
import java.lang.IllegalArgumentException;
import java.net.URI;

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
import com.microsoft.bot.schema.ActionTypes;
import com.microsoft.bot.schema.Activity;
import com.microsoft.bot.schema.ActivityTypes;
import com.microsoft.bot.schema.Attachment;
import com.microsoft.bot.schema.CardAction;
import com.microsoft.bot.schema.CardImage;
import com.microsoft.bot.schema.ChannelAccount;
import com.microsoft.bot.schema.HeroCard;
import com.microsoft.bot.schema.MediaUrl;
import com.microsoft.bot.schema.ResourceResponse;
import com.microsoft.bot.schema.ThumbnailCard;

import com.shutdownhook.radio.azure.Model;
import com.shutdownhook.radio.azure.Radio;
import com.shutdownhook.radio.azure.Global;

import com.shutdownhook.toolbox.Easy;

public class RadioBot extends ActivityHandler {

	public RadioBot(URI uri) {

		channelUrlFmt = uri.resolve("home").toString()  +
			"?channel=%s";
		
		addUrlFmt = uri.resolve("addVideo").toString()  +
			"?channel=%s&video=%s&who=%s";

		objectMapper = new ObjectMapper()
			.enable(SerializationFeature.INDENT_OUTPUT)
			.findAndRegisterModules(); 
	}

	// +----------+
	// | Handlers |
	// +----------+

	// --- nowMessage ---
	
	private void nowMessage(BotContext ctx) {

		Activity activity = null;
		
		try {
			
			Model.Channel channel = ctx.Radio.getChannel(getChannelIdForRadio(ctx));
			Model.Video video = channel.CurrentVideo;

			if (video == null) {
				String msg = "Nothing is playing at the moment! Try sending me " +
					"an **add** or **search** message to get started.";

				activity = MessageFactory.text(msg);
			}
			else {
				
				ThumbnailCard card = new ThumbnailCard();
				card.setTitle(video.Title);
				
				if (video.ThumbnailUrl != null) {
					card.setImage(new CardImage(video.ThumbnailUrl));
				}

				card.setButtons(new CardAction(ActionTypes.OPEN_URL,
											   "Open Radio",
											   getRadioChannelUrl(ctx)));

				List<Attachment> attachments = new ArrayList<>();
				attachments.add(card.toAttachment());
				activity = MessageFactory.attachment(attachments);
			}
		}
		catch (Exception e) {
			String msg = "An exception occurred loading the channel: " + e.toString();
			activity = MessageFactory.text(msg);
		}

		ctx.Responses.add(activity);
	}

	// --- helpMessage ---
	
	private void helpMessage(BotContext ctx, String msg) {

		String realMsg = msg;
		if (realMsg == null) {
			realMsg = String.format("Happy to help, %s!",
									ctx.Turn.getActivity().getFrom().getName());
		}

		HeroCard card = new HeroCard();
		card.setTitle(realMsg);

		String markdown = "";
		
		try { markdown = Easy.stringFromResource("help.md"); }
		catch (IOException e) { /* won't happen */ }

		if (inTeamsChannel(ctx)) {
			
			String myId = ctx.Turn.getActivity().getRecipient().getId();
			
			markdown += 
				"\n\nRemember that in a Teams Channel, you'll need to " +
				"mention me with @" + myId + " to use these commands.";
		}
		
		card.setText(markdown);

        card.setButtons(new CardAction(ActionTypes.OPEN_URL, "Open Radio",
									   getRadioChannelUrl(ctx)));
		
		List<Attachment> attachments = new ArrayList<>();
		attachments.add(card.toAttachment());

		ctx.Responses.add(MessageFactory.attachment(attachments));
	}

	// --- addMessage ---

	private void addMessage(BotContext ctx, String params) {

		Activity activity;
		
		try {
			Model.Video video = ctx.Radio.addVideo(getChannelIdForRadio(ctx),
												   params, getSenderName(ctx));

			String msg = String.format("Added \"%s\" and queued it up!", video.Title);
			activity = MessageFactory.text(msg);
		}
		catch (Exception e) {
			String msg = "An exception occurred adding the video: " + e.toString();
			activity = MessageFactory.text(msg);
		}

		ctx.Responses.add(activity);
	}

	// --- searchMessage ---

	// Note we use a regular message card with embedded links. This is a less-than
	// perfect user experience, but it will work across channels that don't support
	// Adaptive cards (i.e., most of them). Another approach would have been to
	// use prompts in a statefull conversation, but that adds some complexity and
	// probably doesn't really work that well anyways since users probably want to
	// preview the videos before choosing.

	private final static int MAX_SEARCH_RESULTS = 5;
	
	private void searchMessage(BotContext ctx, String params) {

		Activity activity;
		
		try {
			List<Model.Video> videos =
				ctx.Radio.searchVideos(params, MAX_SEARCH_RESULTS);

			StringBuilder sb = new StringBuilder();
			sb.append("Here's what I found:\n\n\n\n");

			for (int i = 0; i < videos.size(); ++i) {
				
				Model.Video video = videos.get(i);
				String item = String.format("%d. %s ([preview](%s)) ([add](%s))\n\n",
											i + 1,
											video.Title,
											video.getVideoUrl(),
											getRadioChannelAddUrl(ctx, video.Id));

				sb.append(item);
			}
			
			activity = MessageFactory.text(sb.toString());
		}
		catch (Exception e) {
			String msg = "An exception occurred while searching: " + e.toString();
			activity = MessageFactory.text(msg);
		}

		ctx.Responses.add(activity);
	}

	// --- defaultMessage ---
	
	private void defaultMessage(BotContext ctx) {
		ctx.Responses.add(MessageFactory.text("Sorry, I didn't catch that. Try 'help' maybe?"));
	}
	
	// --- sendDebugActivity ---

	private void sendDebugActivity(BotContext ctx) {
		
		String msg;
		
		try {
			msg = objectMapper.writeValueAsString(ctx.Turn.getActivity());
		}
		catch (Exception e) {
			msg = "Exception generating debug msg: " + e.toString();
		}
		
		ctx.Responses.add(MessageFactory.text(msg));
	}

	// +---------------+
	// | handleMessage |
	// +---------------+

	// OK we are braindead simple here. First string of characters before a space is a command,
	// everything else is parameters specific to the command. Fancy parsing can be another project.

	private CompletableFuture<Void> handleMessage(TurnContext turnContext) {
		return(botHandler(turnContext, (ctx) -> {

			String msg = stripMentions(ctx.Turn.getActivity().getText()).trim();
			int ichSpace = msg.indexOf(' ');

			if (Global.booleanConfig("ALLOW_DEBUG_MSG", false)) {

				log.info("INBOUND MSG: " + msg);

				if (msg.endsWith("(debug)")) {
					sendDebugActivity(ctx);
					msg = msg.replaceAll("(debug)", "").trim();
				}
			}
			
			String cmd = (ichSpace == -1 ? msg : msg.substring(0, ichSpace)).toLowerCase();
			String params = (ichSpace == -1 ? "" : msg.substring(ichSpace + 1));

			switch (cmd) {
			    case "now":
			    case "playing":
					nowMessage(ctx);
					break;

			    case "add":
					addMessage(ctx, params);
					break;

			    case "search":
					searchMessage(ctx, params);
					break;
				
			    case "help":
					helpMessage(ctx, null);
					break;
					
			    default:
					defaultMessage(ctx);
					break;
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
				if (!member.getId().equals(botId)) {
					helpMessage(ctx, "Welcome!");
					return;
				}
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

	private String getSenderName(BotContext ctx) {
		return(ctx.Turn.getActivity().getFrom().getName());
	}
	
	private boolean inTeamsChannel(BotContext ctx) {
		JsonNode channelData = getChannelData(ctx);
		return(channelData != null && channelData.has("teamsChannelId"));
	}

	private String getChannelIdForRadio(BotContext ctx) {

		String id = null;

		// teams channel
		if (id == null) {
			JsonNode channelData = getChannelData(ctx);
			if (channelData != null &&
				channelData.has("channel") &&
				channelData.get("channel").has("id")) {

				// this is where teams puts the channel id
				id = channelData.get("channel").get("id").asText();
			}
		}

		// conversation id
		if (id == null) {
			if (ctx.Turn.getActivity().getConversation() != null) {
				id = ctx.Turn.getActivity().getConversation().getId();
			}
		}

		// fallback --- channel id (e.g., "webchat" or "msteams")
		if (id == null) {
			id = ctx.Turn.getActivity().getChannelId();
		}

		return(id);
	}
	
	private String getRadioChannelUrl(BotContext ctx) {
		String id = getChannelIdForRadio(ctx);
		return(String.format(channelUrlFmt, Easy.urlEncode(id)));
	}
	
	private String getRadioChannelAddUrl(BotContext ctx, String videoIdOrUrl) {
		String id = getChannelIdForRadio(ctx);
		String who =getSenderName(ctx);
		
		return(String.format(addUrlFmt, Easy.urlEncode(id),
							 Easy.urlEncode(videoIdOrUrl),
							 Easy.urlEncode(getSenderName(ctx))));
	}

	// This is terribly specific. In Teams Channels, bots only receive messages
	// in which they are mentioned. This mention is purely routing noise and not
	// relevant to the content of the message. There are various ways to get rid
	// of this; I've chosen to just strip anything between <at>...</at> markers
	// which is how the mention shows up in the plain text "message" data.
	
	private String stripMentions(String input) {

		int ich = 0;
		int cch = input.length();

		StringBuilder sb = new StringBuilder();
		
		while (ich < cch) {
			int ichAtStart = input.indexOf("<at>", ich);
			if (ichAtStart == -1) ichAtStart = cch;

			if (ichAtStart > ich) {
				// something is before the next <at> marker or end; remember it.
				sb.append(input.substring(ich, ichAtStart));
				ich = ichAtStart;
			}

			if (ichAtStart < cch) {
				// we must be pointing at an "<at>" string.
				ich += 4;
				int ichAtEnd = input.indexOf("</at>", ich);
				if (ichAtEnd == -1) {
					// malformed, oh well
					ich = cch;
				}
				else {
					ich = ichAtEnd + 5;
				}
			}
		}

		return(sb.toString());
	}

	private String channelUrlFmt;
	private String addUrlFmt;

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
