package nl.thedutchmc.espogmailsync.gmail;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.JSONObject;

import nl.thedutchmc.espogmailsync.App;
import nl.thedutchmc.httplib.Http;
import nl.thedutchmc.httplib.Http.RequestMethod;
import nl.thedutchmc.httplib.Http.ResponseObject;

public class GmailApi {

	public JSONObject UserHistoryList(String token, String pageToken) {
		final String apiUrl = "https://gmail.googleapis.com/gmail/v1/users/me/messages";
		
		HashMap<String, String> params = new HashMap<String, String>();
		params.put("maxResults", "50");
		
		if(pageToken != null) {
			params.put("pageToken", pageToken);
		}
		
		params.put("includeSpamTrash", "true");
		
		HashMap<String, String> headers = new HashMap<String, String>();
		headers.put("Authorization", "Bearer " + token);
		
		ResponseObject responseObject = null;
		try {
			responseObject = new Http(App.DEBUG).makeRequest(
					RequestMethod.GET, 
					apiUrl, 
					params, 
					null, //Body MIME type
					null, //Body
					headers);
		} catch (MalformedURLException e) {
			App.logError("Something went wrong whilst fetching Emails from the Gmail API. Caused by a MalformedURLException");
			App.logDebug(ExceptionUtils.getStackTrace(e));
		} catch (IOException e) {
			App.logError("Something went wrong whilst fetching Emails from the Gmail API. Caused by an IOException");
			App.logDebug(ExceptionUtils.getStackTrace(e));
		}
		
		if(responseObject == null) {
			return null;
		}
		
		if(responseObject.getResponseCode() != 200) {
			App.logDebug(responseObject.getConnectionMessage());
			return null;
		}
		
		return new JSONObject(responseObject.getMessage());
	}
	
	public JSONObject UserThreadsGet(String token, String threadId) {
		final String apiUrl = "https://gmail.googleapis.com/gmail/v1/users/me/threads/" + threadId;
		
		HashMap<String, String> params = new HashMap<>();
		params.put("format", "MINIMAL");
		
		HashMap<String, String> headers = new HashMap<String, String>();
		headers.put("Authorization", "Bearer " + token);
		
		ResponseObject responseObject = null;
		try {
			responseObject = new Http(App.DEBUG).makeRequest(
					RequestMethod.GET, 
					apiUrl, 
					params, 
					null, //Body MIME type
					null, //Body
					headers);
		} catch (MalformedURLException e) {
			App.logError("Something went wrong whilst fetching threads from the Gmail API. Caused by a MalformedURLException");
			App.logDebug(ExceptionUtils.getStackTrace(e));
		} catch (IOException e) {
			App.logError("Something went wrong whilst fetching threads from the Gmail API. Caused by an IOException");
			App.logDebug(ExceptionUtils.getStackTrace(e));
		}
		
		if(responseObject == null) {
			return null;
		}
		
		if(responseObject.getResponseCode() != 200) {
			App.logDebug(responseObject.getConnectionMessage());
			return null;
		}
		
		return new JSONObject(responseObject.getMessage());
	}
	
	public JSONObject UserMessagesGet(String token, String messageId) {
		final String apiUrl = "https://gmail.googleapis.com/gmail/v1/users/me/messages/" + messageId;
		
		HashMap<String, String> params = new HashMap<>();
		params.put("format", "FULL");
		
		HashMap<String, String> headers = new HashMap<String, String>();
		headers.put("Authorization", "Bearer " + token);
		
		ResponseObject responseObject = null;
		try {
			responseObject = new Http(App.DEBUG).makeRequest(
					RequestMethod.GET, 
					apiUrl, 
					params, 
					null, //Body MIME type
					null, //Body
					headers);
		} catch (MalformedURLException e) {
			App.logError("Something went wrong whilst fetching a message from the Gmail API. Caused by a MalformedURLException");
			App.logDebug(ExceptionUtils.getStackTrace(e));
		} catch (IOException e) {
			App.logError("Something went wrong whilst fetching a message from the Gmail API. Caused by an IOException");
			App.logDebug(ExceptionUtils.getStackTrace(e));
		}
		
		if(responseObject == null) {
			return null;
		}
		
		if(responseObject.getResponseCode() != 200) {
			App.logDebug(responseObject.getConnectionMessage());
			return null;
		}
		
		return new JSONObject(responseObject.getMessage());
	}
	
	public JSONObject UserLabelsGet(String token, String labelId) {
		final String apiUrl = "https://gmail.googleapis.com/gmail/v1/users/me/labels/" + labelId;
		
		HashMap<String, String> headers = new HashMap<String, String>();
		headers.put("Authorization", "Bearer " + token);
		
		ResponseObject responseObject = null;
		try {
			responseObject = new Http(App.DEBUG).makeRequest(
					RequestMethod.GET, 
					apiUrl, 
					null, //URL parameters 
					null, //Body MIME type
					null, //Body
					headers);
		} catch (MalformedURLException e) {
			App.logError("Something went wrong whilst fetching a label from the Gmail API. Caused by a MalformedURLException");
			App.logDebug(ExceptionUtils.getStackTrace(e));
		} catch (IOException e) {
			App.logError("Something went wrong whilst fetching a label from the Gmail API. Caused by an IOException");
			App.logDebug(ExceptionUtils.getStackTrace(e));
		}
		
		if(responseObject == null) {
			return null;
		}
		
		if(responseObject.getResponseCode() != 200) {
			App.logDebug(responseObject.getConnectionMessage());
			return null;
		}
		
		return new JSONObject(responseObject.getMessage());
	}
}
