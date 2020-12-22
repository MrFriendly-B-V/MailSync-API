package nl.thedutchmc.espogmailsync;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.JSONObject;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import nl.thedutchmc.espogmailsync.runnables.FetchMailRunnable;
import nl.thedutchmc.httplib.Http;
import nl.thedutchmc.httplib.Http.ResponseObject;

@RestController
@RequestMapping("/espogmailsync")
public class PostController {
	
	@RequestMapping(value = "active", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	public String getActive(@RequestParam String sessionId) {
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
			
		JSONObject json = new JSONObject(responseObject.getMessage());
		if(json.getInt("status") != 200) {
			App.logDebug(json.get("message"));
			JSONObject response = new JSONObject();
			response.put("status", json.getInt("status"));
			response.put("message", "The AuthServer did not return the requested details. Check debug logs");
			
			return response.toString();
		}
		
		String token = json.getString("token");
		String id = json.getString("id");
		String email = json.getString("email");
		
		//TODO THIS IS TEMP
		//GmailApi api = new GmailApi();
		//JSONObject apiResponse = api.UserHistoryList(token, null);
		Thread fetchMailRunnableThread = new Thread(new FetchMailRunnable(token, id));
		fetchMailRunnableThread.start();
		
		//END OF TEMP
		
		return "SURE";
	}
}
