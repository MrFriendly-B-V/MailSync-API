package nl.thedutchmc.espogmailsync.runnables;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;
import org.json.JSONObject;

import nl.thedutchmc.espogmailsync.App;
import nl.thedutchmc.espogmailsync.Config;
import nl.thedutchmc.httplib.Http;
import nl.thedutchmc.httplib.Http.RequestMethod;
import nl.thedutchmc.httplib.Http.ResponseObject;

public class UpdateTokenRunnable implements Runnable {
	
	private FetchMailRunnable fetchMailRunnable;
	private String userId; 
	
	public UpdateTokenRunnable(FetchMailRunnable fetchMailRunnable, String userId) {
		this.fetchMailRunnable = fetchMailRunnable;
		this.userId = userId;
	}
	
	@Override
	public void run() {
		while(!fetchMailRunnable.getDone()) {
			App.logInfo("Updating token for user: " + userId);
						
			HashMap<String, String> params = new HashMap<>();
			params.put("userId", userId);
			params.put("apiToken", Config.apiToken);
			
			ResponseObject responseObject = null;
			try {
				responseObject = new Http(App.DEBUG).makeRequest(RequestMethod.POST, Config.authServerHost + "/oauth/token", params, null, null, new HashMap<>());
			} catch (MalformedURLException e) {
				
			} catch (IOException e) {
				
			}
			
			if(responseObject.getResponseCode() != 200) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				
				continue;
			}
			
			JSONObject responseJson = new JSONObject(responseObject.getMessage());
			String newToken = responseJson.getString("token");
			
			synchronized (fetchMailRunnable) {
				fetchMailRunnable.setToken(newToken);
			}
			
			App.logInfo("Token for user: " + userId + " has been updated!");
			
			try {
				Thread.sleep(60L * 2L * 1000L); //2 minutes
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}
