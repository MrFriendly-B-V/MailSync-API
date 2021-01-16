package nl.thedutchmc.espogmailsync.runnables;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

import nl.thedutchmc.espogmailsync.App;
import nl.thedutchmc.espogmailsync.gmail.GmailApi;
import nl.thedutchmc.espogmailsync.mailobjects.*;

/**
 * This runnable is used to sync a user with GMail.
 * This runnable will fetch their new emails and 'order' them into Message objects and MessageThreat objects
 */
public class FetchMailRunnable implements Runnable {

	private String token;
	private String id;
	
	private boolean done = false;
	
	public FetchMailRunnable(String token, String id) {
		this.token = token;
		this.id = id;
		
		Thread updateTokenThread = new Thread(new UpdateTokenRunnable(this, id));
    	updateTokenThread.start();
	}

	@Override
	public void run() {
		App.logInfo("Starting Gmail for user " + id + "...");
		
		String nextPageToken = "";
		boolean nextRound = true;
		boolean firstRound = true;
		GmailApi api = new GmailApi(id);
		
		HashMap<String, Message> analysedMessages = new HashMap<>();
		HashMap<String, MessageThread> analysedThreads = new HashMap<>();
		
		int pageIndex = 0;
		while(nextRound) {
			App.logDebug("Analysing inbox page: " + pageIndex + " for user: " + id);
			pageIndex++;
			
			//First we fetch all messages on this page
			JSONObject responseJson = null;
			if(firstRound) {
				responseJson = api.userHistoryList(token, null);				
				firstRound = false;
	 		} else {
	 			responseJson = api.userHistoryList(token, nextPageToken);
	 		}
			
			//Check if a nextPageToken is included,
			//if this is the case we will do another round after this for that page
			if(!responseJson.has("nextPageToken")) {
				nextRound = false;
			} else {
				nextPageToken = responseJson.getString("nextPageToken");
			}
			
			//Get all the messages and loop over them
			final JSONArray messages = responseJson.getJSONArray("messages");
			for(Object o : messages) {
				final JSONObject message = (JSONObject) o;
				final String threadId = message.getString("threadId");
				
				//Check if the message's thread ID has already been checked,
				//if so we do not do it again
				if(App.threadsAnalysed.contains(threadId)) {
					continue;
				}
				
				App.threadsAnalysed.add(threadId);
				
				//Fetch information about the thread from Google
				JSONObject threadJson = api.userThreadsGet(token, threadId);
				JSONArray threadMessages = threadJson.getJSONArray("messages");
				
				List<Message> messagesInThread = new ArrayList<>();
				
				App.logDebug("Analysed MessageThread " + threadId + " for user: " + id);
				
				//Iterate over all messages in that thread
				for(Object oThreadMessage : threadMessages) {
					final JSONObject threadMessage = (JSONObject) oThreadMessage;
					final String messageId = threadMessage.getString("id");
					
					//Check if we've already checked the message
					//If so we do not do it again
					if(App.messagesAnalysed.contains(messageId)) {
						continue;
					}
					
					App.messagesAnalysed.add(messageId);
					
					//Get details about this specific message from Google
					JSONObject messageJson = api.userMessagesGet(token, messageId);
					
					//App.logDebug(messageJson);
					
					//Get all the labels associated with this message
					//Put them in the `labels` list
					List<Label> labels = new ArrayList<>();
					if(messageJson.has("labelIds")) {
						JSONArray labelsIdsJson = messageJson.getJSONArray("labelIds");
						for(Object oLabelId : labelsIdsJson) {
							String labelId = (String) oLabelId;
							
							JSONObject labelJson = api.userLabelsGet(token, labelId);
							
							String textColor = "";
							String backgroundColor = "";
							if(labelJson.has("color")) {
								JSONObject labelColor = labelJson.getJSONObject("color");

								textColor = labelColor.getString("textColor");
								backgroundColor = labelColor.getString("backgroundColor");
							}
							
							Label label = new Label(
									labelId, 
									labelJson.getString("name"), 
									textColor, 
									backgroundColor);
							
							labels.add(label);
						}
						//TODO fetch label information
					}
					
					//Get the time the message was accepted by google,
					//so when it was 'received'
					String epochDate = messageJson.getString("internalDate");
					
					//Get the message payload
					JSONObject messagePayload = messageJson.getJSONObject("payload");
					
					List<JSONObject> results = new ArrayList<>();
					if(!messagePayload.has("parts")) {
						if(messagePayload.has("body") && messagePayload.getJSONObject("body").getInt("size") != 0) {
							results.add(messagePayload);
						}
					} else {
						JSONArray payloadParts = messagePayload.getJSONArray("parts");
						
						payloadParts.forEach(oPart -> {
							JSONObject part = (JSONObject) oPart;
							
							//Analyze the body
							results.addAll(analyzePart(part));
						});
					}
					
					//Analyze the headers
					JSONArray payloadHeaders = messagePayload.getJSONArray("headers");
					
					List<Header> headers = new ArrayList<>();
					payloadHeaders.forEach(oHeader -> {
						JSONObject jsonHeader = (JSONObject) oHeader;
						
						Header header = new Header(jsonHeader.getString("name"), jsonHeader.getString("value"));
						headers.add(header);
					});
					
					String messageHtml = "";
					String messageText = "";
					for(JSONObject result : results) {
						if(result.getString("mimeType").equals("text/html")) {
							if(result.getJSONObject("body").has("data")) {
								messageHtml = result.getJSONObject("body").getString("data");
							}
						}
						
						if(result.getString("mimeType").equals("text/plain")) {
							if(result.getJSONObject("body").has("data")) {
								messageText = result.getJSONObject("body").getString("data");
							}
						}
					}
					
					//Add the analysed message to the list of analysed messages and to the list of 
					//anaylised messages in the current MessageThread
					Message messageObject = new Message(messageId, threadId, epochDate, labels, headers);
					messageObject.setMessageText(messageText);
					messageObject.setMessageHtml(messageHtml);
					
					analysedMessages.put(messageId, messageObject);
					messagesInThread.add(messageObject);
					
					App.logDebug("Analysed message: " + messageId + " for user: " + id);
				}
								
				//Add the analysed Thread to the list of analysed threads
				MessageThread messageThreadObject = new MessageThread(threadId, messagesInThread);
				analysedThreads.put(threadId, messageThreadObject);
				
				App.logDebug("Analysed Thread: " + threadId + " for user: " + id);
				
				Thread databaseMailSyncThread = new Thread(new DatabaseMailSyncRunnable(new ArrayList<>(analysedMessages.values()), new ArrayList<>(analysedThreads.values())), "databaseMailSyncRunnable-" + id);
				databaseMailSyncThread.start();
				
				analysedMessages.clear();
				analysedThreads.clear();
			}
			
			try {
				Thread.sleep(250);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
		App.logInfo("Sync with GMail for user " + id + " complete.");
		
		//We've analysed the entire inbox.
		App.logInfo("Analysed entire inbox for " + id + ". Total new message count: " + analysedMessages.size() + ". Total new MessageThreads: " + analysedThreads.size() + ".");

		done = true;
		
		//Sync to the database
		Thread databaseMailSyncThread = new Thread(new DatabaseMailSyncRunnable(new ArrayList<>(analysedMessages.values()), new ArrayList<>(analysedThreads.values())), "databaseMailSyncRunnable-" + id);
		databaseMailSyncThread.start();
	}
	
	/**
	 * This method is used to analyze a MessagePart, and peel it back sort of like you would peel an onion
	 * @see https://developers.google.com/gmail/api/reference/rest/v1/users.messages#Message.MessagePart
	 * @param partToAnalyze The part to analyze
	 * @return a List of MessagePart's with a body inside of them
	 */
	private static List<JSONObject> analyzePart(JSONObject partToAnalyze) {
		List<JSONObject> results = new ArrayList<>();
		
		//Check if the body has a size of 0
		if(partToAnalyze.getJSONObject("body").getInt("size") == 0) {
			
			//Check if the part has 'child' parts
			if(partToAnalyze.has("parts")) {
				JSONArray subParts = partToAnalyze.getJSONArray("parts");
				
				//Iterate over those child parts
				for(Object oSubPart : subParts) {
					JSONObject jsonSubPart = (JSONObject) oSubPart;
					
					//Check if the child part has a body, if not, go through this method again
					//If yes, add it to the results
					if(jsonSubPart.getJSONObject("body").getInt("size") != 0) {
						results.add(jsonSubPart);
					} else {
						results.addAll(analyzePart(jsonSubPart));
					}
				}
			}
		} else {
			//The part to be analyzed had a body right in the first 'layer'
			results.add(partToAnalyze);
		}
		
		return results;
	}
	
	public void setToken(String token) {
		this.token = token;
	}
	
	public boolean getDone() {
		return this.done;
	}
}
