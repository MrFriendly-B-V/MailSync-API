package nl.thedutchmc.espogmailsync.runnables;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import nl.thedutchmc.espogmailsync.App;
import nl.thedutchmc.espogmailsync.gmail.GmailApi;
import nl.thedutchmc.espogmailsync.runnables.mailobjects.*;

public class FetchMailRunnable implements Runnable {

	private String token;
	private String id;
	
	public FetchMailRunnable(String token, String id) {
		this.token = token;
		this.id = id;
	}

	@Override
	public void run() {
		String nextPageToken = "";
		boolean nextRound = true;
		boolean firstRound = true;
		GmailApi api = new GmailApi();
		
		HashMap<String, Message> analysedMessages = new HashMap<>();
		HashMap<String, MessageThread> analysedThreads = new HashMap<>();
		
		int pageIndex = 0;
		while(nextRound) {
			App.logDebug("Analysing inbox page: " + pageIndex + " for user: " + id);
			pageIndex++;
			
			//First we fetch all messages on this page
			JSONObject responseJson = null;
			if(firstRound) {
				responseJson = api.UserHistoryList(token, null);				
				firstRound = false;
	 		} else {
	 			responseJson = api.UserHistoryList(token, nextPageToken);
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
				JSONObject threadJson = api.UserThreadsGet(token, threadId);
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
					JSONObject messageJson = api.UserMessagesGet(token, messageId);
					
					App.logDebug(messageJson);
					
					//Get all the labels associated with this message
					//Put them in the `labels` list
					JSONArray labelsIdsJson = messageJson.getJSONArray("labelIds");
					List<Label> labels = new ArrayList<>();
					for(Object oLabelId : labelsIdsJson) {
						String labelId = (String) oLabelId;
						
						JSONObject labelJson = api.UserLabelsGet(token, labelId);
						
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
					
					//Get the time the message was accepted by google,
					//so when it was 'received'
					String epochDate = messageJson.getString("internalDate");
					
					//Get the message payload
					JSONObject messagePayload = messageJson.getJSONObject("payload");
					JSONArray payloadParts = messagePayload.getJSONArray("parts");
					
					List<JSONObject> results = new ArrayList<>();
					payloadParts.forEach(oPart -> {
						JSONObject part = (JSONObject) oPart;
						
						//Analyze the body
						results.addAll(analyzePart(part));
					});
					
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
							messageHtml = result.getJSONObject("body").getString("data");
						}
						
						if(result.getString("mimeType").equals("text/plain")) {
							messageText = result.getJSONObject("body").getString("data");
						}
					}
					
					//Add the analysed message to the list of analysed messages and to the list of 
					//anaylized messages in the current MessageThread
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
			}
		}
		
		//We've analysed the entire inbox.
		App.logInfo("Analysed entire inbox for " + id + ". Total new message count: " + analysedMessages.size() + ". Total new MessageThreads: " + analysedThreads.size() + ".");
		
		App.addAllMessages(analysedMessages);
		App.addAllThreads(analysedThreads);
	
		//TODO send this to the database gradually.
	}
	
	private static List<JSONObject> analyzePart(JSONObject partToAnalyze) {
		List<JSONObject> results = new ArrayList<>();
		
		if(partToAnalyze.getJSONObject("body").getInt("size") == 0) {
			if(partToAnalyze.has("parts")) {
				JSONArray subParts = partToAnalyze.getJSONArray("parts");
				for(Object oSubPart : subParts) {
					JSONObject jsonSubPart = (JSONObject) oSubPart;
					if(jsonSubPart.getJSONObject("body").getInt("size") != 0) {
						results.add(jsonSubPart);
					} else {
						results.addAll(analyzePart(jsonSubPart));
					}
				}
			}
		} else {
			results.add(partToAnalyze);
		}
		
		return results;
	}
}
