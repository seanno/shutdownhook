/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.radio.azure.bot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import com.microsoft.bot.builder.ActivityHandler;
import com.microsoft.bot.builder.Bot;
import com.microsoft.bot.builder.ConversationState;
import com.microsoft.bot.builder.MessageFactory;
import com.microsoft.bot.builder.TurnContext;
import com.microsoft.bot.builder.StatePropertyAccessor;
import com.microsoft.bot.connector.Channels;
import com.microsoft.bot.schema.Activity;
import com.microsoft.bot.schema.ChannelAccount;
import com.microsoft.bot.schema.ResourceResponse;

import com.shutdownhook.radio.azure.Wumpus;
import com.shutdownhook.radio.azure.Wumpus.WumpusState;
import com.shutdownhook.toolbox.Easy;

public class WumpusBot extends ActivityHandler {

	public WumpusBot() {

		Storage.Config cfg = new Storage.Config();
		cfg.Endpoint = System.getenv("COSMOS_ENDPOINT");
		cfg.Database = System.getenv("COSMOS_DATABASE");
		cfg.Container = System.getenv("COSMOS_CONTAINER_WUMPUS");

		try {
			storage = new Storage(cfg);
			conversationState = new ConversationState(storage);
		}
		catch (Exception e) {
			log.severe(Easy.exMsg(e, "WumpusBot Constructor", false));
		}
	}

	// +----------------+
	// | onMembersAdded |
	// +----------------+
	
	@Override
    protected CompletableFuture<Void> onMembersAdded(List<ChannelAccount> membersAdded,
													 TurnContext turnContext) {

		// we may be in a group chat or channel so don't (necessarily)
		// start a new game; just send help and a prompt as intro

		StatePropertyAccessor<WumpusState> wumpusAccessor = conversationState.createProperty("wumpus");
		CompletableFuture<WumpusState> wumpusFuture = wumpusAccessor.get(turnContext, WumpusState::new);

		return(wumpusFuture.thenApply((wumpusState) -> {

			Wumpus wumpus = new Wumpus(wumpusState);
			
			List<Activity> activities = new ArrayList<Activity>();
			activities.add(md(wumpus.help(), turnContext));
			activities.add(md(wumpus.prompt(), turnContext));

			conversationState.saveChanges(turnContext).join();

			return(turnContext.sendActivities(activities.toArray(new Activity[activities.size()])));
		})
		.thenApply(res -> null));
	}

	// +-------------------+
	// | onMessageActivity |
	// +-------------------+

	@Override
    protected CompletableFuture<Void> onMessageActivity(TurnContext turnContext) {

		// this "play" trigger is for channels that need a poke to start
		// things off; this will trigger the "onMemberAdded" activity.
		// twilio is a special case because they never seem to get onMemberAdded
		// no matter what; so let it pass (wumpus will turn 'p' into help)
		
		final String msg = turnContext.getActivity().getText();

		if (msg.toLowerCase().trim().equals("play") &&
			!turnContext.getActivity().getChannelId().toLowerCase().contains("sms")) {
			
			return(CompletableFuture.completedFuture(null));
		}

		StatePropertyAccessor<WumpusState> wumpusAccessor = conversationState.createProperty("wumpus");
		CompletableFuture<WumpusState> wumpusFuture = wumpusAccessor.get(turnContext, WumpusState::new);

		return(wumpusFuture.thenApply((wumpusState) -> {

			Wumpus wumpus = new Wumpus(wumpusState);
			
			List<Activity> activities = new ArrayList<Activity>();
			
			String result = wumpus.action(msg);
			result = ((result == null) ? "" : result + "\n");
			
			activities.add(md(result + wumpus.prompt(), turnContext));

			conversationState.saveChanges(turnContext).join();

			return(turnContext.sendActivities(activities.toArray(new Activity[activities.size()])));
		})
		.thenApply(res -> null));
    }

	// +-------------------+
	// | Members & Helpers |
	// +-------------------+

	private Activity md(String input, TurnContext turn) {

		String finalMsg = null;
		
		switch (turn.getActivity().getChannelId()) {
		    case Channels.MSTEAMS:
		    case Channels.EMULATOR:
		    case Channels.WEBCHAT:
				finalMsg = input.replaceAll("\n", "\n\n");
				break;

		    default:
				finalMsg = input;
				break;
		}
		
		return(MessageFactory.text(finalMsg));
	}
	
	private Storage storage;
	private ConversationState conversationState;

	// +-----------+
	// | Singleton |
	// +-----------+

	public static synchronized WumpusBot singleton() {
		if (bot == null) bot = new WumpusBot();
		return(bot);
	}

	private static WumpusBot bot = null;

	private final static Logger log = Logger.getLogger(WumpusBot.class.getName());
}
