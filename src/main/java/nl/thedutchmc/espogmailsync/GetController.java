package nl.thedutchmc.espogmailsync;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import nl.thedutchmc.espogmailsync.runnables.EspoSyncRunnable;
import nl.thedutchmc.espogmailsync.runnables.mailobjects.Header;
import nl.thedutchmc.espogmailsync.runnables.mailobjects.Label;
import nl.thedutchmc.espogmailsync.runnables.mailobjects.Message;
import nl.thedutchmc.espogmailsync.runnables.mailobjects.MessageThread;
import nl.thedutchmc.httplib.Http;
import nl.thedutchmc.httplib.Http.ResponseObject;

@RestController
@RequestMapping("/espogmailsync")
public class GetController {

	@CrossOrigin(origins = "https://intern.mrfriendly.nl")
	@RequestMapping(value = "mail", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	public String mail(@RequestParam String sessionId, @RequestParam(required = false) String senderDomain, @RequestParam(required = false) String mailId) {
		
		// ----------------------------
		// BEGIN OF USER AUTHENTICATION
		// ----------------------------
		HashMap<String, String> params = new HashMap<>();
		params.put("sessionId", sessionId);
		params.put("apiToken", Config.apiToken);
		
		//Send a request to the auth server to verify the user
		ResponseObject responseObject = null;
		try {
			 responseObject = new Http().makeRequest(Http.RequestMethod.POST, 
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

		
		JSONObject finalResult = new JSONObject();
		JSONArray messages = new JSONArray();
		for(Message message : App.getMessages().values()) {
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
			
			//Get the next and previous message for this message
			MessageThread messageThread = App.getMessageThread(message.getThreadId());
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
			
			//If the senderDomain has been given, we only want to return emails from that domain
			if(senderDomain != null) {
				String fromDomain = result.getString("from").split("<")[1].split("@")[1].replace(">", "");
				if(!fromDomain.equalsIgnoreCase(senderDomain)) continue;
			}
			
			if(mailId != null) {
				if(!mailId.equals(result.getString("id"))) continue;
			}
			
			messages.put(result);
		}
		
		finalResult.put("status", 200);
		finalResult.put("messages", messages);
		
		return finalResult.toString();
	}
	
	@GetMapping("test")
	public String test() {
		Thread espoSyncThread = new Thread(new EspoSyncRunnable());
		espoSyncThread.start();
		
		return "OK";
	}
}
