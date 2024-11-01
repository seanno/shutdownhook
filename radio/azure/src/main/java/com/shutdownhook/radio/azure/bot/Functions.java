/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.radio.azure.bot;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
	
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.HttpStatusType;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import com.microsoft.bot.builder.Bot;
import com.microsoft.bot.builder.InvokeResponse;
import com.microsoft.bot.connector.authentication.AuthenticationException;
import com.microsoft.bot.schema.Activity;

import com.shutdownhook.toolbox.Easy;

public class Functions {

	// +-------------------+
	// | Function Handlers |
	// +-------------------+

    @FunctionName("bot")
    public HttpResponseMessage bot(
	    @HttpTrigger(name = "req", methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS)
		final HttpRequestMessage<Optional<String>> request, final ExecutionContext context) {

		return(base(request, context, RadioBot.singleton(request.getUri()), null));
	}

    @FunctionName("wumpus")
    public HttpResponseMessage wumpus(
	    @HttpTrigger(name = "req", methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS)
		final HttpRequestMessage<Optional<String>> request, final ExecutionContext context) {

		return(base(request, context, WumpusBot.singleton(), "_wumpus"));
	}

	// +--------------+
	// | Base Handler |
	// +--------------+

	private HttpResponseMessage base(final HttpRequestMessage<Optional<String>> request,
									 final ExecutionContext context,
									 final Bot bot,
									 final String configSuffix) {

        context.getLogger().info("botRequest for: " + request.getUri().toString());
		HttpResponseMessage.Builder response = request.createResponseBuilder(HttpStatus.OK);
		
		try {
            Activity activity = getObjectMapper().readValue(request.getBody().get(),
															Activity.class);
															
            String authHeader = request.getHeaders().get("authorization");

			CompletableFuture<InvokeResponse> future = 
				Adapter.getAdapter(configSuffix).processIncomingActivity(authHeader, activity, bot);

			future.handle((invokeResponse, exInvoke) -> {

				if (exInvoke == null) {
					
					if (invokeResponse != null) {

						try {
							response.body(getObjectMapper().writeValueAsString(invokeResponse.getBody()));
							response.header("Content-Type", "application/json");
							response.status(HttpStatusType.custom(invokeResponse.getStatus()));
						}
						catch (Exception exResponse) {
							context.getLogger().warning(Easy.exMsg(exResponse, "botRequest response ex", false));
							response.status(HttpStatus.INTERNAL_SERVER_ERROR);
						}
					}
				}
				else {
					if (exInvoke instanceof CompletionException &&
						exInvoke.getCause() instanceof AuthenticationException) {
						
						response.status(HttpStatus.UNAUTHORIZED);
					}
					else {
						response.status(HttpStatus.INTERNAL_SERVER_ERROR);
					}
					
					context.getLogger().warning(Easy.exMsg(exInvoke, "botRequest invoke", false));
                }

				return(null);
			});
		}
		catch (Exception e) {
			context.getLogger().warning(Easy.exMsg(e, "botRequest", false));
			response.status(HttpStatus.INTERNAL_SERVER_ERROR);
		}

		return(response.build());
    }

	// +---------+
	// | Helpers |
	// +---------+

	private synchronized static ObjectMapper getObjectMapper() {

		if (objectMapper == null) {

			objectMapper = new ObjectMapper()
				.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
				.findAndRegisterModules(); // auto-discovery of serializer modules
		}
		
		return(objectMapper);
	}

	private static ObjectMapper objectMapper = null;
}
