/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.radio.azure.bot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.microsoft.bot.builder.ActivityHandler;
import com.microsoft.bot.builder.Bot;
import com.microsoft.bot.builder.MessageFactory;
import com.microsoft.bot.builder.TurnContext;
import com.microsoft.bot.schema.ChannelAccount;
import com.microsoft.bot.schema.ResourceResponse;

public class RadioBot extends ActivityHandler {

    @Override
    protected CompletableFuture<Void> onMessageActivity(TurnContext turnContext) {

		// ECHO
		CompletableFuture<ResourceResponse> future =
			turnContext.sendActivity(MessageFactory.text("Echo: " + turnContext.getActivity().getText()));

		return(future.thenApply(result -> null));
		// ECHO

    }

    @Override
    protected CompletableFuture<Void> onMembersAdded(List<ChannelAccount> membersAdded,
													 TurnContext turnContext) {

		// ECHO
		List<CompletableFuture<ResourceResponse>> futures =
			new ArrayList<CompletableFuture<ResourceResponse>>();
			
		String botId = turnContext.getActivity().getRecipient().getId();
		for (ChannelAccount member : membersAdded) {
			if (member.getId().equals(botId)) continue;
			futures.add(turnContext.sendActivity(MessageFactory.text("Hello " + member.getName() + "!")));
		}

		return(CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()])));
		// ECHO

	}

	// +-----------+
	// | Singleton |
	// +-----------+
	
	public static synchronized RadioBot singleton() {
		if (bot == null) bot = new RadioBot();
		return(bot);
	}

	private static RadioBot bot = null;
	
}
