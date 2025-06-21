package com.shutdownhook.lorawan2;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import java.util.Optional;

public class Ping {

    @FunctionName("Ping")
    public HttpResponseMessage run(
								   
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.GET, HttpMethod.POST},
                authLevel = AuthorizationLevel.ANONYMOUS)
                HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
		
        context.getLogger().info("Request: PING");

        // Parse query parameter
        final String query = request.getQueryParameters().get("name");
        final String name = request.getBody().orElse(query);

        if (name == null) {
            return(request.createResponseBuilder(HttpStatus.BAD_REQUEST)
				   .body(PING_NAME_MISSING)
				   .build());
        } 

		return(request.createResponseBuilder(HttpStatus.OK)
			   .body("Hello, " + name)
			   .build());
    }

	private static String PING_NAME_MISSING =
		"Please pass a name on the query string or in the request body";
}
