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

	/**
	 * {@link https://developers.google.com/gmail/api/reference/rest/v1/users.history/list }
	 * @param token Authentication Token
	 * @param pageToken Gmail page token
	 * @return JSONObject of the API's response
	 */
	public JSONObject UserHistoryList(String token, String pageToken) {
		final String apiUrl = "https://gmail.googleapis.com/gmail/v1/users/me/messages";
		
		//URL Parameters
		HashMap<String, String> params = new HashMap<String, String>();
		params.put("maxResults", "50");
		params.put("includeSpamTrash", "true");

		if(pageToken != null) {
			params.put("pageToken", pageToken);
		}
		
		//Request headers
		HashMap<String, String> headers = new HashMap<String, String>();
		headers.put("Authorization", "Bearer " + token);
		
		//Make the request
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
		
		//Validate the response
		if(responseObject == null) {
			return null;
		}
		
		//Check if the response code is somethingf else than 200
		if(responseObject.getResponseCode() != 200) {
			App.logDebug(responseObject.getConnectionMessage());
			//TODO Better handling of a non 200 status code
			return null;
		}
		
		return new JSONObject(responseObject.getMessage());
	}
	
	/**
	 * {@link https://developers.google.com/gmail/api/reference/rest/v1/users.threads/get}
	 * @param token Authentication token
	 * @param threadId ID of the thread to get
	 * @return Returns a JSONObject of the API's response
	 */
	public JSONObject UserThreadsGet(String token, String threadId) {
		final String apiUrl = "https://gmail.googleapis.com/gmail/v1/users/me/threads/" + threadId;
		
		//URL Parameters
		HashMap<String, String> params = new HashMap<>();
		params.put("format", "MINIMAL");
		
		//Request headers
		HashMap<String, String> headers = new HashMap<String, String>();
		headers.put("Authorization", "Bearer " + token);
		
		//Make the request
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
		
		//Validate the response
		if(responseObject == null) {
			return null;
		}
		
		//Check if the status code is something other than 200
		if(responseObject.getResponseCode() != 200) {
			App.logDebug(responseObject.getConnectionMessage());
			//TODO Better handling of a non 200 status code
			return null;
		}
		
		return new JSONObject(responseObject.getMessage());
	}
	
	/**
	 * {@link https://developers.google.com/gmail/api/reference/rest/v1/users.messages/get}
	 * @param token Authentication token
	 * @param messageId ID of the message to get
	 * @return Returns a JSONObject of the API's response
	 */
	public JSONObject UserMessagesGet(String token, String messageId) {
		final String apiUrl = "https://gmail.googleapis.com/gmail/v1/users/me/messages/" + messageId;
		
		//URL Parameters
		HashMap<String, String> params = new HashMap<>();
		params.put("format", "FULL");
		
		//Request headers
		HashMap<String, String> headers = new HashMap<String, String>();
		headers.put("Authorization", "Bearer " + token);
		
		//Make the request
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
		
		//Validate the response
		if(responseObject == null) {
			return null;
		}
		
		//Check if the response code is something other than 200
		if(responseObject.getResponseCode() != 200) {
			App.logDebug(responseObject.getConnectionMessage());
			//TODO Better handling of a non 200 status code
			return null;
		}
		
		return new JSONObject(responseObject.getMessage());
	}
	
	/**
	 * {@link https://developers.google.com/gmail/api/reference/rest/v1/users.labels/get}
	 * @param token Authentication token
	 * @param labelId ID of the label to get
	 * @return Returns a JSONObject of the API's response
	 */
	public JSONObject UserLabelsGet(String token, String labelId) {
		final String apiUrl = "https://gmail.googleapis.com/gmail/v1/users/me/labels/" + labelId;
		
		//Request headers
		HashMap<String, String> headers = new HashMap<String, String>();
		headers.put("Authorization", "Bearer " + token);
		
		//Make the request
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
		
		//Validate the response
		if(responseObject == null) {
			return null;
		}
		
		//Check if the status code is something other than 200
		if(responseObject.getResponseCode() != 200) {
			App.logDebug(responseObject.getConnectionMessage());
			//TODO Better handling of a non 200 status code
			return null;
		}
		
		return new JSONObject(responseObject.getMessage());
	}
}
