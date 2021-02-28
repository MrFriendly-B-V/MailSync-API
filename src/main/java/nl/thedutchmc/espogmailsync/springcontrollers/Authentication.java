package nl.thedutchmc.espogmailsync.springcontrollers;

import java.io.IOException;
import java.util.HashMap;

import com.google.gson.Gson;

import nl.thedutchmc.espogmailsync.App;
import nl.thedutchmc.espogmailsync.gsonobjects.in.AuthResponse;
import nl.thedutchmc.espogmailsync.utils.Utils;
import nl.thedutchmc.httplib.Http;
import nl.thedutchmc.httplib.Http.RequestMethod;
import nl.thedutchmc.httplib.Http.ResponseObject;

public class Authentication {

	public static AuthResponse isAuthenticated(String sessionId) {
		final String endpoint = App.getEnvironment().getAuthServerHost() + "/oauth/token";
		
		HashMap<String, String> urlParameters = new HashMap<>();
		urlParameters.put("sessionId", sessionId);
		urlParameters.put("apiToken", App.getEnvironment().getAuthApiToken());
		
		ResponseObject apiResponse;
		try {
			apiResponse = new Http(App.DEBUG).makeRequest(RequestMethod.POST, endpoint, urlParameters, null, null, null);
		} catch(IOException e) {
			App.logError("An error occured while getting authentication details from the AuthServer");
			App.logDebug(Utils.getStackTrace(e));
			return null;
		}
		
		if(apiResponse.getResponseCode() != 200) {
			App.logError("Got non-200 status code while getting authentication details from the AuthServer");
			App.logDebug(apiResponse.getConnectionMessage());
			return null;
		}
		
		final Gson gson = new Gson();
		AuthResponse authResponse = gson.fromJson(apiResponse.getMessage(), AuthResponse.class);

		return authResponse;
	}
	
}
