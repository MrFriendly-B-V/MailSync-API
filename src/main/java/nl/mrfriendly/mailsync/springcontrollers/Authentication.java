package nl.mrfriendly.mailsync.springcontrollers;

import java.io.IOException;
import java.util.HashMap;

import com.google.gson.Gson;

import nl.mrfriendly.mailsync.App;
import nl.mrfriendly.mailsync.gsonobjects.in.authlander.CheckSessionResponse;
import nl.mrfriendly.mailsync.gsonobjects.in.authlander.DescribeSessionResponse;
import nl.mrfriendly.mailsync.gsonobjects.in.authlander.DescribeUserResponse;
import nl.mrfriendly.mailsync.gsonobjects.in.authlander.GetTokenResponse;
import nl.mrfriendly.mailsync.gsonobjects.in.authlander.UserScopesResponse;
import nl.mrfriendly.mailsync.utils.Utils;
import dev.array21.httplib.Http;
import dev.array21.httplib.Http.RequestMethod;
import dev.array21.httplib.Http.ResponseObject;

public class Authentication {
	
	public static String getUserId(String sessionId) {
		final String endpoint = App.getEnvironment().getAuthServerHost() + "/session/describe/" + sessionId;
		
		ResponseObject apiResponse;
		try {
			apiResponse = new Http(App.DEBUG).makeRequest(RequestMethod.GET, endpoint, null, null, null, null);
		} catch(IOException e) {
			App.logError("An error occured while getting authentication details from Authlander");
			App.logDebug(Utils.getStackTrace(e));
			return null;
		}

		final Gson gson = new Gson();
		DescribeSessionResponse	authResponse = gson.fromJson(apiResponse.getMessage(), DescribeSessionResponse.class);

		System.out.println(apiResponse.getMessage());
		
		if(authResponse.error != null) {
			return null;
		}
		
		if(!authResponse.active ) {
			return null;
		}
		
		return authResponse.userId;
	}
	
	public static String[] getScopes(String userId) {
		final String endpoint = App.getEnvironment().getAuthServerHost() + "/user/scopes/" + userId;
		
		ResponseObject apiResponse;
		try {
			apiResponse = new Http(App.DEBUG).makeRequest(RequestMethod.GET, endpoint, null, null, null, null);
		} catch(IOException e) {
			App.logError("An error occured while getting authentication details from Authlander");
			App.logDebug(Utils.getStackTrace(e));
			return null;
		}
		
		if(apiResponse.getResponseCode() != 200) {
			App.logError("Got non-200 status code while getting authentication details from Authlander");
			App.logDebug(apiResponse.getConnectionMessage());
			return null;
		}
		
		final Gson gson = new Gson();
		UserScopesResponse authResponse = gson.fromJson(apiResponse.getMessage(), UserScopesResponse.class);
		
		System.out.println(apiResponse.getMessage());
		
		if(authResponse.error != null) {
			return null;
		}
		
		if(!authResponse.isActive) {
			return null;
		}
		
		
		return authResponse.scopes;
	}
	
	public static DescribeUserResponse getUserDetails(String userId) {
		final String endpoint = App.getEnvironment().getAuthServerHost() + "/user/describe/" + userId;
		
		ResponseObject apiResponse;
		try {
			apiResponse = new Http(App.DEBUG).makeRequest(RequestMethod.GET, endpoint, null, null, null, null);
		} catch(IOException e) {
			App.logError("An error occured while getting authentication details from Authlander");
			App.logDebug(Utils.getStackTrace(e));
			return null;
		}
		
		final Gson gson = new Gson();
		DescribeUserResponse authResponse = gson.fromJson(apiResponse.getMessage(), DescribeUserResponse.class);
		
		return authResponse;
	}
	
	public static GetTokenResponse getGoogleToken(String userId) {
		final String endpoint = App.getEnvironment().getAuthServerHost() + "/token/get/" + userId;
		
		HashMap<String, String> headers = new HashMap<>();
		headers.put("Authorization", App.getEnvironment().getAuthApiToken());
		
		ResponseObject apiResponse;
		try {
			apiResponse = new Http(App.DEBUG).makeRequest(RequestMethod.GET, endpoint, null, null, null, headers);
		} catch(IOException e) {
			App.logError("An error occured while getting authentication details from Authlander");
			App.logDebug(Utils.getStackTrace(e));
			return null;
		}
		
		final Gson gson = new Gson();
		GetTokenResponse authResponse = gson.fromJson(apiResponse.getMessage(), GetTokenResponse.class);
		
		return authResponse;
	}

	public static boolean isAuthenticated(String sessionId) {
		final String endpoint = App.getEnvironment().getAuthServerHost() + "/session/check/" + sessionId;
		
		ResponseObject apiResponse;
		try {
			apiResponse = new Http(App.DEBUG).makeRequest(RequestMethod.GET, endpoint, null, null, null, null);
		} catch(IOException e) {
			App.logError("An error occured while getting authentication details from Authlander");
			App.logDebug(Utils.getStackTrace(e));
			return false;
		}
		
		final Gson gson = new Gson();
		CheckSessionResponse authResponse = gson.fromJson(apiResponse.getMessage(), CheckSessionResponse.class);

		if(authResponse.error != null) {
			return false;
		}
		
		return authResponse.sessionValid;
	}
	
}
