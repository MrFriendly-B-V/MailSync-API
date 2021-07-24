package nl.mrfriendly.mailsync.runnables;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.google.gson.Gson;

import nl.mrfriendly.mailsync.App;
import nl.mrfriendly.mailsync.database.SqlManager;
import nl.mrfriendly.mailsync.database.types.DatabaseMessage;
import nl.mrfriendly.mailsync.gsonobjects.in.authlander.GetTokenResponse;
import nl.mrfriendly.mailsync.gsonobjects.in.gmail.Header;
import nl.mrfriendly.mailsync.gsonobjects.in.gmail.Message;
import nl.mrfriendly.mailsync.gsonobjects.in.gmail.MessagePart;
import nl.mrfriendly.mailsync.gsonobjects.in.gmail.MessagesList;
import nl.mrfriendly.mailsync.gsonobjects.in.gmail.SmallMessage;
import nl.mrfriendly.mailsync.springcontrollers.Authentication;
import nl.mrfriendly.mailsync.utils.Utils;
import dev.array21.httplib.Http;
import dev.array21.httplib.Http.RequestMethod;
import dev.array21.httplib.Http.ResponseObject;

public class FetchMailThreadV2 implements Runnable {

	private String userId;
	private volatile String authToken;
	private volatile boolean isDone;
	private volatile boolean userActive = true;
	
	/**
	 * @param userId The Google ID of the user to fetch mail for
	 */
	public FetchMailThreadV2(String userId) {
		this.userId = userId;
	}
	
	@Override
	public void run() {
		while(App.RUNNING && this.userActive) {
			this.isDone = false;
			App.logInfo("Starting Gmail Inbox analysis for user " + this.userId);
			
			//First we need to fetch the message IDs we've already analyzed
			List<String> messageIdsAnalyzed = new ArrayList<>();
			SqlManager sqlManager = App.getSqlManager();
			try {
				final String fetchMessageIdsStatement = "SELECT id FROM messages";
				final PreparedStatement pr = sqlManager.createPreparedStatement(fetchMessageIdsStatement);
				ResultSet rs = sqlManager.executeFetchStatement(pr);
				
				while(rs.next()) {
					String id = rs.getString("id");
					messageIdsAnalyzed.add(id);
				}
			} catch(SQLException e) {
				App.logError("An error occured while getting Message IDs from the database. Retrying in 60 seconds");
				App.logDebug(Utils.getStackTrace(e));
				
				try {
					Thread.sleep(60000);
				} catch(InterruptedException e1) {
					App.logDebug(Utils.getStackTrace(e1));
				}
				
				continue;
			}
			
			//Start a thread to periodically fetch the authToken from the authentication server
			Thread tokenThread = new Thread(new UpdateTokenRunnable(this.userId), "UpdateTokenThread-" + this.userId);
			tokenThread.start();
			
			//Wait 10 seconds for the tokenThread to get the initial token
			try {
				Thread.sleep(10000);
			} catch(InterruptedException e) {
				App.logDebug(Utils.getStackTrace(e));
			}
			
			//Create a GSON instance. This is used everywhere throughout this runnable
			final Gson gson = new Gson();
			
			//Next up we need to analyze the entire inbox
			//Get every inbox page, get all of it's messages and determine if we've already analyzed the message
			//If that is the case we don't have to do anything for that message. If it is not the case
			//then we need to anaylze it further
			List<MessagesList> allMessagesList = new ArrayList<>(); 

			String nextPageToken = "";
			boolean firstIteration = true;
			
			//If there's a nextPageToken, get the details of that page
			while(nextPageToken != null && this.userActive) {
				//On the first iteration there is no page token, so set it to null
				if(firstIteration) {
					firstIteration = false;
					nextPageToken = null;
				}
				
				App.logDebug(String.format("Analysing Inbox page %s for user %s", nextPageToken, this.userId));
				
				//Get the page from the Gmail API
				final String messagesListStr = usersMessagesList(this.userId, nextPageToken);
				if(messagesListStr == "") {
					continue;
				}
				
				//Deserialize
				final MessagesList messagesList = gson.fromJson(messagesListStr, MessagesList.class); 
				if(messagesList.getNextPageToken() != null) {
					nextPageToken = messagesList.getNextPageToken();
				} else {
					//No nextPageToken. We're done
					break;
				}
				
				//Add the messagesList object to the list
				allMessagesList.add(messagesList);
			}
			
			//Iterate over the list of fetches MessagesList, and it's child SmallMessage's. 
			//Check for each if we've already analyzed this message
			List<String> messageIdsToAnalyze = new ArrayList<>();
			for(MessagesList messagesList : allMessagesList) {
				App.logDebug(String.format("Analysing MessagesList %s for user %s", messagesList, this.userId));
				
				for(SmallMessage smallMessage : messagesList.getMessages()) {
					//If the list of message IDs we've already analyzed doesn't contain
					//the ID in this SmallMessage, that means we've not yet analyzed it,
					//add it to the list of messages that need analyzing
					if(!messageIdsAnalyzed.contains(smallMessage.getId())) {						
						messageIdsToAnalyze.add(smallMessage.getId());
					}				
				}
			}
			
			App.logDebug(String.format("Analysing %d messages for user %s.", messageIdsToAnalyze.size(), this.userId));
			
			//We now have a list with IDs of messages that we've not yet anaylzed.
			//Iterate over every ID in this list, and anaylze it.
			for(String messageId : messageIdsToAnalyze) {
				App.logDebug(String.format("Analysing message %s for user %s.", messageId, this.userId));
				
				String messageStr = usersMessagesGet(this.userId, messageId);
				if(messageStr == "") {
					continue;
				}
				
				Message message = gson.fromJson(messageStr, Message.class);
				MessagePart payload = message.getPayload();
				
				List<MessagePart> analyzedPayload = getMessagePartData(payload);
				String data = null;
				for(MessagePart part : analyzedPayload) {
					if(part.getMimeType().equals("text/html") || part.getMimeType().equals("text/plain")) {
						if(part.getBody().getData() != null) {
							data = part.getBody().getData();
						}
					}
				}
				
				DatabaseMessage databaseMessage = new DatabaseMessage(messageId, data);
				databaseMessage.setInternalDate(message.getInternalDate());
				
				//Extract the headers we want:
				// - From
				// - To
				// - Subject
				// - Cc
				// - Bcc
				for(Header header : payload.getHeaders()) {
					switch(header.getName()) {
					case "To":
						databaseMessage.setReceiver(header.getValue());
						break;
					case "From":
						databaseMessage.setSender(header.getValue());
					case "Subject":
						if(header.getValue() != null) {
							databaseMessage.setSubject(header.getValue());
						} else {
							databaseMessage.setSubject("");
						}
					case "Cc":
						if(header.getValue() != null) {
							databaseMessage.setCc(header.getValue());
						} else {
							databaseMessage.setCc("");
						}
					case "Bcc":
						if(header.getValue() != null) {
							databaseMessage.setBcc(header.getValue());
						} else {
							databaseMessage.setBcc("");
						}
					}
				}
				
				//We've fully analyzed the Message now.
				messageIdsAnalyzed.add(messageId);

				//Send this message of to the database
				try {
					final String sendMessageToDbQuery = "INSERT INTO messages (id, sender, receiver, cc, bcc, subject, data, internal_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
					PreparedStatement pr = sqlManager.createPreparedStatement(sendMessageToDbQuery);
					pr.setString(1, databaseMessage.getId());
					pr.setString(2, databaseMessage.getSender());
					pr.setString(3, databaseMessage.getReceiver());
					pr.setString(4, databaseMessage.getCc());
					pr.setString(5, databaseMessage.getBcc());
					pr.setString(6, databaseMessage.getSubject());
					pr.setString(7, databaseMessage.getData());
					pr.setString(8, databaseMessage.getInternalDate());
			
					sqlManager.executePutStatement(pr);
				} catch(SQLException e) {
					App.logError("An error occured while inserting a DatabaseMessage into the database");
					App.logDebug(Utils.getStackTrace(e));
					continue;
				}
				
				//We dont want to hit Google ratelimits. So wait for .25 seconds (so we only send 4 requests per second)
				try {
					Thread.sleep(250);
				} catch(InterruptedException e) {
					App.logDebug(Utils.getStackTrace(e));
				}
			}
			
			App.logInfo("Analysed entire Gmail Inbox for user " + this.userId);
			this.isDone = true;
			
			//Reschedule to run in 1 hour
			try {
				Thread.sleep(3600000);
			} catch(InterruptedException e) {
				App.logDebug(Utils.getStackTrace(e));
			}
		}
	}
	
	private List<MessagePart> getMessagePartData(MessagePart rootPart) {
		List<MessagePart> result = new ArrayList<>();
		
		if(rootPart.getBody().getSize() == 0) {
			if(rootPart.getParts() != null) {
				for(MessagePart subPart : rootPart.getParts()) {
					if(subPart.getBody().getSize() == 0) {
						result.add(subPart);
					} else {
						result.addAll(getMessagePartData(subPart));
					}
				}
			}
			
		} else {
			result.add(rootPart);
		}
		
		return result;
	}
	
	/**
	 * Gets the specified message.
	 * @param userId The Google ID of the user
	 * @param messageId The ID of the message to get 
	 * @param authToken Authentication token to use
	 * @return The result, or an empty String if an error occured
	 */
	private String usersMessagesGet(String userId, String messageId) {
		String endpoint = "https://gmail.googleapis.com/gmail/v1/users/{userId}/messages/{id}"
			.replace("{userId}", userId)
			.replace("{id}", messageId);
	
		ResponseObject apiResponse;
		try {
			apiResponse = new Http(App.DEBUG).makeRequest(RequestMethod.GET, endpoint, null, null, null, getAuthorizationHeaderMap(this.authToken));
		} catch(IOException e) {
			App.logError(String.format("An error occured while getting Message details for message %s for user %s", messageId, userId));
			App.logDebug(Utils.getStackTrace(e));
			return "";
		}
		
		int responseCode = apiResponse.getResponseCode();
		if(responseCode != 200) {
			if(responseCode == 401) {
				App.logError(String.format("Got 401 while getting Message details for message %s for user %s. Assuming invalid credentials. Waiting 30 seconds then retrying!", messageId, userId));
				try {
					Thread.sleep(30000);
				} catch (InterruptedException e) {
					App.logDebug(Utils.getStackTrace(e));
				}
				
				return usersMessagesGet(userId, messageId);
			} else {
				App.logError(String.format("Got non-200 status code while getting Message details for message %s for user %s", messageId, userId));
				App.logDebug(apiResponse.getConnectionMessage());
				return "";
			}
		}
		
		return apiResponse.getMessage();
	}
	
	/**
	 * Get a HashMap containing the field necessary for Bearer authentication
	 * @param authToken The authentication to use
	 * @return
	 */
	private HashMap<String, String> getAuthorizationHeaderMap(String authToken) {
		HashMap<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + authToken);
		
		return headers;
	}
	
	/**
	 * Lists the messages in the user's mailbox.
	 * @param id The Google user id
	 * @param pageToken Nullable. Page token to retrieve a specific page of results in the list.
	 * @param authToken Authentication token to use
	 * @return The result, or an empty String if an error occured
	 */
	private String usersMessagesList(String id, String pageToken) {
		String endpoint = "https://gmail.googleapis.com/gmail/v1/users/{userId}/messages"
				.replace("{userId}", id);
		
		HashMap<String, String> urlParameters = new HashMap<>();
		urlParameters.put("maxResults", "50");
		urlParameters.put("includeSpamTrash", "true");
		
		if(pageToken != null) {
			urlParameters.put("pageToken", pageToken);
		}
		
		ResponseObject apiResponse;
		try {
			apiResponse = new Http(App.DEBUG).makeRequest(RequestMethod.GET, endpoint, urlParameters, null, null, getAuthorizationHeaderMap(this.authToken));
		} catch (IOException e) {
			App.logError("An error occured while getting Messages from the user's Inbox");
			App.logDebug(Utils.getStackTrace(e));
			return "";
		}
		
		int responseCode = apiResponse.getResponseCode();
		if(responseCode != 200) {
			if(responseCode == 401) {
				App.logError("Got 401. Assuming invalid credentials. Waiting 30 seconds then retrying!");
				try {
					Thread.sleep(30000);
				} catch (InterruptedException e) {
					App.logDebug(Utils.getStackTrace(e));
				}
				
				return usersMessagesList(id, pageToken);
			} else {
				App.logError("Got non-200 status code while getting Messages from the user's Inbox");
				App.logDebug(apiResponse.getConnectionMessage());
				return "";
			}
		}
		
		return apiResponse.getMessage();
	}

	private class UpdateTokenRunnable implements Runnable {
		
		private String userId;
		private int refreshAttempt = 0;
		
		public UpdateTokenRunnable(String userId) {
			this.userId = userId;
		}
		
		@Override
		public void run() {
			App.logInfo("Starting UpdateToken Thread for user " + this.userId);
			
			while(!isDone && FetchMailThreadV2.this.userActive) {
				App.logDebug("Updating authToken for user " + this.userId);
				
				GetTokenResponse token = Authentication.getGoogleToken(this.userId);
				if(token.accessToken == null) {
					this.refreshAttempt++;
					
					App.logError("Got an error from Authlander while refreshing the access token: " + token.error.message);
					App.logError(String.format("Retrying in 30 seconds. Attempt %d/5", this.refreshAttempt));
					
					try {
						Thread.sleep(30 * 1000L);
					} catch(InterruptedException e) {
						e.printStackTrace();
					}
					
					if(this.refreshAttempt == 5) {
						App.logError("All 5 attempts failed. Giving up.");
						FetchMailThreadV2.this.userActive = false;
					}
				} else {
					this.refreshAttempt = 0;
				}
				
				if(!token.active) {
					FetchMailThreadV2.this.userActive = false;
				}
			
				FetchMailThreadV2.this.authToken = token.accessToken;
				
				// Time until we fetch a new token in seconds
				// token.expiry is an epoch timestamp, so subtract the current time, and then subtract another 5 minutes
				long timeUntilNextRun = token.expiry - (System.currentTimeMillis() / 1000L) - 300;
				try {
					Thread.sleep(timeUntilNextRun * 1000L);
				} catch(InterruptedException e) {
					App.logDebug(Utils.getStackTrace(e));
				}
			}
		}
	}
}