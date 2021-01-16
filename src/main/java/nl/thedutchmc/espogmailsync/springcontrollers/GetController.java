package nl.thedutchmc.espogmailsync.springcontrollers;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.sql.Blob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import nl.thedutchmc.espogmailsync.App;
import nl.thedutchmc.espogmailsync.Config;
import nl.thedutchmc.espogmailsync.database.Serializer;
import nl.thedutchmc.espogmailsync.database.ResultObject;
import nl.thedutchmc.espogmailsync.database.SqlManager;
import nl.thedutchmc.espogmailsync.database.StatementType;
import nl.thedutchmc.espogmailsync.mailobjects.*;
import nl.thedutchmc.httplib.Http;
import nl.thedutchmc.httplib.Http.ResponseObject;

@RestController
@RequestMapping("/espogmailsync")
public class GetController {

	@CrossOrigin(origins = "https://intern.mrfriendly.nl")
	@RequestMapping(value = "mail", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	public String mail(@RequestParam String sessionId, @RequestParam(required = false) String senderAddress, @RequestParam(required = false) String mailId, @RequestParam(required = false) String page) {
		// ----------------------------
		// BEGIN OF USER AUTHENTICATION
		// ----------------------------
		HashMap<String, String> params = new HashMap<>();
		params.put("sessionId", sessionId);
		params.put("apiToken", Config.apiToken);
		
		//Send a request to the auth server to verify the user
		ResponseObject responseObject = null;
		try {
			 responseObject = new Http(App.DEBUG).makeRequest(Http.RequestMethod.POST, 
					 Config.authServerHost + "/oauth/token", //Host
					 params, //URL parameters
					 null, null, //Body
					 new HashMap<String, String>()); //Headers
		} catch (MalformedURLException e) {
			App.logError("Unable to verify sessionId with authserver. Is the URL correct? Caused by MalformedURLException");
			App.logDebug(ExceptionUtils.getStackTrace(e));
		} catch (IOException e) {
			App.logError("Unable to verify sessionId with authserver. Caused by IOException");
			App.logDebug(ExceptionUtils.getStackTrace(e));
		}

		//If the status code isn't 200, that means it's not a valid session
		JSONObject json = new JSONObject(responseObject.getMessage());
		if(json.getInt("status") != 200) {
			App.logDebug(json.get("message"));
			JSONObject response = new JSONObject();
			response.put("status", json.getInt("status"));
			response.put("message", "The AuthServer did not return the requested details. Check debug logs");
			
			return response.toString();
		}
		// --------------------------
		// END OF USER AUTHENTICATION
		// --------------------------

		
		List<Message> messagesList = new LinkedList<>();

		//Pull mails from the db for these email addresses or mail ids
		try {
			if(senderAddress != null) {
			
				String[] addresses = senderAddress.split(",");
				
				SqlManager sql = App.getSqlManager();
	
				String stmt = "SELECT data FROM messages";
				for(int i = 0; i < addresses.length; i++) {
					if(i == 0) {
						stmt += " WHERE sender='" + addresses[i] + "'";
						stmt += " OR receiver='" + addresses[i] + "'";
					} else {
						stmt += " OR sender='" + addresses[i] + "'";
						stmt += " OR receiver='" + addresses[i] + "'";
					}
				}
								
				PreparedStatement preparedStatement = sql.getConnection().prepareStatement(stmt);
				ResultObject ro = sql.executeStatement(StatementType.query, preparedStatement);
				ResultSet rs = ro.getResultSet();
				
				while(rs.next()) {
					Blob mBlob = rs.getBlob("data");
					byte[] mBytes = mBlob.getBytes(1, (int) mBlob.length());
					Message m = (Message) Serializer.deserializeObject(mBytes);
					if(!messagesList.contains(m)) messagesList.add(m);
				}
			}
						
			if(mailId != null) {
				SqlManager sql = App.getSqlManager();
				
				PreparedStatement preparedStatement = sql.getConnection().prepareStatement("SELECT data FROM messages WHERE gmailId=?");
				preparedStatement.setString(1, mailId);
				ResultObject ro = sql.executeStatement(StatementType.query, preparedStatement);
				ResultSet rs = ro.getResultSet();
				
				while(rs.next()) {
					Blob mBlob = rs.getBlob("data");
					byte[] mBytes = mBlob.getBytes(1, (int) mBlob.length());
					Message m = (Message) Serializer.deserializeObject(mBytes); 
					messagesList.add(m);
				}
			}
		} catch (SQLException e) {
			App.logDebug(ExceptionUtils.getStackTrace(e));
		}
		
		int realPage = 0;
		if(page != null) {
			realPage = Integer.valueOf(page);
		}
		
		
		int startingIndex = 0;
		int endingIndex = messagesList.size();
		if(mailId == null) {
			final int pageSize = 50;
			startingIndex = realPage * pageSize;
			endingIndex = realPage * pageSize + pageSize;
			
			if(realPage != 0) {
				startingIndex -= 50;
				endingIndex -= 50;
			}
			
			if(endingIndex > messagesList.size()) endingIndex = messagesList.size() -1;
			
			App.logInfo("Start: " + startingIndex + " end: " + endingIndex);

			//Sort the list by epoch
			Collections.sort(messagesList, Comparator.comparingLong(Message::getEpochLong).reversed());
		}
		
		JSONObject finalResult = new JSONObject();
		JSONArray messages = new JSONArray();
		for(int index = startingIndex; index < endingIndex; index++) {
			Message message = messagesList.get(index);
			
			JSONObject result = new JSONObject();
			result.put("epoch_date", message.getEpochDate());
			result.put("id", message.getId());
			
			//Get the labels for this email
			//TODO does not work
			if(message.getLabels() != null)  {
				JSONArray labels = new JSONArray();

				for(Label label : message.getLabels()) {
					JSONObject labelJson = new JSONObject();
					labelJson.put("name", label.getName());
					labelJson.put("text_color", label.getColorHexText());
					labelJson.put("backgroud_color", label.getColorHexBackground());
					labels.put(labelJson);
				}
				
				result.put("labels", labels);
			}
			
			//Get the MessageThread associated with this message from the database
			MessageThread messageThread = null;
			try {				
				SqlManager sql = App.getSqlManager();

				PreparedStatement preparedStatement = sql.getConnection().prepareStatement("SELECT data FROM messageThreads WHERE gmailId=?");
				preparedStatement.setString(1, message.getThreadId());
				
				ResultObject ro = sql.executeStatement(StatementType.query, preparedStatement);
				ResultSet rs = ro.getResultSet();
								
				while(rs.next()) {
					Blob mtBlob = rs.getBlob("data");
					byte[] mtBytes = mtBlob.getBytes(1, (int) mtBlob.length());
					MessageThread mt = (MessageThread) Serializer.deserializeObject(mtBytes);
					messageThread = mt;
				}
			} catch(SQLException e) {
				App.logDebug(ExceptionUtils.getStackTrace(e));
			}
			
			if(messageThread == null) {
			} else {
				//Get the next and previous message for this message
				//MessageThread messageThread = App.getMessageThread(message.getThreadId());
				result.put("thread_id", messageThread.getId());
				
				//Get all the epoch times in the mail Thread and put them in a HashMap
				HashMap<Long,Message> messagesWithEpochs = new HashMap<>();
				for(Message m : messageThread.getMessages()) {
					messagesWithEpochs.put(m.getEpochLong(), m);
				}
				
				//Iterate over the epochs list to find the next and previous messages, if there are any
				List<Long> epochs = new ArrayList<>(messagesWithEpochs.keySet());
				Collections.sort(epochs);
				for(int i = 0; i < epochs.size(); i++) {
					if(epochs.get(i).equals(message.getEpochLong())) {
						
						//Check if this message is not the first
						if(i != 0) {
							result.put("previousMessage", messagesWithEpochs.get(epochs.get(i -1)).getId());
						}
						
						//Check if this message is not the last
						if(i != epochs.size()-1) {
							result.put("nextMessage", messagesWithEpochs.get(epochs.get(i+1)).getId());
						}
					}
				}
			}
			
			//HTML and plaintext body
			result.put("body_text_plain", message.getMessageText());
			result.put("body_text_html", message.getMessageHtml());
			
			//java.util.Base64.getMimeDecoder().decode(message.getMessageHtml().getBytes());
			//byte[] decoded = java.util.Base64.getDecoder().decode(message.getMessageHtml().getBytes());
			byte[] bDecoded = java.util.Base64.getUrlDecoder().decode(message.getMessageHtml().getBytes());
			String strDecoded = new String(bDecoded, StandardCharsets.UTF_8).replace("http://", "https://");
			
			result.put("body_text_html_decoded", strDecoded);
			
			//Put the headers in (From, To, Subject etc)
			for(Header header : message.getHeaders()) {
				switch(header.getName()) {
				case "From":
					result.put("from", header.getValue());
					break;
				case "To":
					result.put("to", header.getValue());
					break;
				case "Subject":
					result.put("subject", header.getValue());
					break;
				case "Cc":
					result.put("cc", header.getValue());
					break;
				}
			}
			
			if(senderAddress != null) {
				String[] senders = senderAddress.split(",");
				for(String sender : senders) {
					if(result.has("from") && result.getString("from").toLowerCase().contains(sender.toLowerCase())) result.put("direction", "outbound");
					if(result.has("to") && result.getString("to").toLowerCase().contains(sender.toLowerCase())) result.put("direction", "inbound");
				}
			}
			
			messages.put(result);
		}
		
		finalResult.put("status", 200);
		finalResult.put("messages", messages);
		
		messages = null;
		messagesList = null;
		
		return finalResult.toString();
	}
}
