package nl.thedutchmc.espogmailsync;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import nl.thedutchmc.espogmailsync.runnables.mailobjects.Label;
import nl.thedutchmc.espogmailsync.runnables.mailobjects.Message;
import nl.thedutchmc.espogmailsync.runnables.mailobjects.MessagePart;
import nl.thedutchmc.espogmailsync.runnables.mailobjects.MessageThread;
import nl.thedutchmc.httplib.Http;
import nl.thedutchmc.httplib.Http.ResponseObject;

@RestController
@RequestMapping("/espogmailsync")
public class GetController {

	@RequestMapping(value = "mail", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	//@GetMapping("mail")
	public String mail(@RequestParam String sessionId) {
		App.logDebug("uhh");
		HashMap<String, String> params = new HashMap<>();
		params.put("sessionId", sessionId);
		params.put("apiToken", Config.apiToken);
		
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
		App.logDebug("2x");

		JSONObject json = new JSONObject(responseObject.getMessage());
		if(json.getInt("status") != 200) {
			App.logDebug(json.get("message"));
			JSONObject response = new JSONObject();
			response.put("status", json.getInt("status"));
			response.put("message", "The AuthServer did not return the requested details. Check debug logs");
			
			return response.toString();
		}
		
		JSONObject finalResult = new JSONObject();
		JSONArray messages = new JSONArray();
		for(Message message : App.getMessages().values()) {
			JSONObject result = new JSONObject();
			result.put("epoch_date", message.getEpochDate());
			result.put("id", message.getId());
			
			//Get the labels for this email
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
			
			HashMap<Long,Message> messagesWithEpochs = new HashMap<>();
			for(Message m : messageThread.getMessages()) {
				messagesWithEpochs.put(m.getEpochLong(), m);
			}
			
			List<Long> epochs = new ArrayList<>(messagesWithEpochs.keySet());
			Collections.sort(epochs);
			for(int i = 0; i < epochs.size(); i++) {
				if(epochs.get(i).equals(message.getEpochLong())) {
					if(i != 0) {
						result.put("previousMessage", messagesWithEpochs.get(epochs.get(i -1)).getId());
					}
					
					if(i != epochs.size()-1) {
						result.put("nextMessage", messagesWithEpochs.get(epochs.get(i+1)).getId());
					}
				}
			}
			
			MessagePart part = message.getMessagePart();
			for(MessagePart subpart : part.getSubParts()) {
				boolean dataAquired = false;
				
				if(subpart.getMimeType().equals("text/html")) {
					result.put("body_html", subpart.getData64());
					dataAquired = true;
				}
				
				if(subpart.getMimeType().equals("text/plain")) {
					result.put("body_text_plain", subpart.getData64());
					dataAquired = true;
				}
				
				if(!dataAquired) {
					for(MessagePart subSubPart : subpart.getSubParts()) {
						
						if(subSubPart.getMimeType().equals("text/html")) {
							result.put("body_html", subpart.getData64());
						}
						
						if(subSubPart.getMimeType().equals("text/plain")) {
							result.put("body_text_plain", subpart.getData64());
						}
					}
				}
			}
			//TODO BODY
			//TODO FROM, TO, CC, BCC, SUBJECT
			
			messages.put(result);
		}
		
		finalResult.put("messages", messages);
		
		return finalResult.toString();
	}
}
