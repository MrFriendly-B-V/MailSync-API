package nl.thedutchmc.espogmailsync.springcontrollers;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import nl.thedutchmc.espogmailsync.App;
import nl.thedutchmc.espogmailsync.Config;
import nl.thedutchmc.espogmailsync.User;
import nl.thedutchmc.espogmailsync.database.SyncMailUsers;
import nl.thedutchmc.espogmailsync.runnables.FetchMailRunnable;
import nl.thedutchmc.httplib.Http;
import nl.thedutchmc.httplib.Http.ResponseObject;

@RestController
@RequestMapping("/espogmailsync")
public class PostController {
	
	@RequestMapping(value = "active", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	public String getActive(@RequestParam String sessionId) {
		
		//Check if the user is authenticated
		String strAuthResponse = checkAuth(sessionId);
		JSONObject jsonAuthResponse = new JSONObject(strAuthResponse);
		if(jsonAuthResponse.getInt("status") != 200) return strAuthResponse;
		
		//Iterate over all the active users, and put their email in a json array
		JSONArray jsonActiveUsers = new JSONArray();		
		App.activeUsers.forEach(user -> {
			jsonActiveUsers.put(user.getEmail());
		});
		
		//Return the values
		return new JSONObject()
				.put("active_users", jsonActiveUsers)
				.toString();
	}
	
	@RequestMapping(value = "new", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	public String postNew(@RequestParam String sessionId) {
		String strAuthResponse = checkAuth(sessionId);
		JSONObject jsonAuthResponse = new JSONObject(strAuthResponse);
		if(jsonAuthResponse.getInt("status") != 200) return strAuthResponse;
		
		User user = new User(
				jsonAuthResponse.getString("id"),
				jsonAuthResponse.getString("token"),
				jsonAuthResponse.getString("email"));
		
		App.activeUsers.add(user);
		
		new SyncMailUsers().addMailUser(user.getId());

		Thread fetchMailThread = new Thread(new FetchMailRunnable(user.getToken(), user.getId()), "FetchMailThread-" + user.getId());
		fetchMailThread.start();
		
		JSONObject response = new JSONObject();
		response.put("status", 200);
		response.put("email", user.getEmail());
		
		return response.toString();
	}
	
	/**
	 * Method used to check if a user is authenticated. It will also get their token, email and id
	 * @param sessionId The sessionId to check
	 * @return A String of JSON containing the returned information, or error messages
	 */
	private String checkAuth(String sessionId) {
		HashMap<String, String> params = new HashMap<>();
		params.put("sessionId", sessionId);
		params.put("apiToken", Config.apiToken);
		
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
			
			JSONObject response = new JSONObject();
			response.put("status", 500);
			response.put("message", "MalformedURLException");
			
			return response.toString();
			
		} catch (IOException e) {
			App.logError("Unable to verify sessionId with authserver. Caused by IOException");
			App.logDebug(ExceptionUtils.getStackTrace(e));
			
			JSONObject response = new JSONObject();
			response.put("status", 500);
			response.put("message", "IOException");
			
			return response.toString();
		}
			
		JSONObject json = new JSONObject(responseObject.getMessage());
		if(json.getInt("status") != 200) {
			App.logDebug(json.get("message"));
			JSONObject response = new JSONObject();
			response.put("status", json.getInt("status"));
			response.put("message", "The AuthServer did not return the requested details. Check debug logs");
			
			return response.toString();
		}
		
		JSONObject response = new JSONObject()
					.put("status", 200)
					.put("token", json.get("token"))
					.put("id", json.get("id"))
					.put("email", json.get("email"));
		
		return response.toString();
	}
}
