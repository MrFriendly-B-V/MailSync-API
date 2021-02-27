package nl.thedutchmc.espogmailsync.runnables;

import java.io.IOException;
import java.util.HashMap;

import org.json.JSONObject;

import com.google.gson.Gson;

import nl.thedutchmc.espogmailsync.App;
import nl.thedutchmc.espogmailsync.EspoUtils;
import nl.thedutchmc.espogmailsync.EspoUtils.HttpMethod;
import nl.thedutchmc.espogmailsync.gsonobjects.espocrm.Contact;
import nl.thedutchmc.espogmailsync.gsonobjects.espocrm.GetContacts;
import nl.thedutchmc.httplib.Http;
import nl.thedutchmc.httplib.Http.MediaFormat;
import nl.thedutchmc.httplib.Http.RequestMethod;
import nl.thedutchmc.httplib.Http.ResponseObject;

public class EspoContactSyncRunnableV2 implements Runnable {

	@Override
	public void run() {
		while(App.RUNNING) {
			App.logInfo("Starting Contact synchronization with EspoCRM");
			
			//GSON instance used in this thread
			final Gson gson = new Gson();
			
			//Get all Contact's from EspoCRM
			String getContactStr = getContacts();
			if(getContactStr == "") {
				continue;
			}
			
			//Iterate over each Contact
			GetContacts getContacts = gson.fromJson(getContactStr, GetContacts.class);
			for(Contact contact : getContacts.getContacts()) {
				App.logDebug("Processing Contact " + contact.getId());
				
				if(contact.getEmailAddress() == null) {
					continue;
				}
				
				//Set the mailSync Field in EspoCRM for this Contact
				setMailSyncLink(contact.getEmailAddress(), contact.getId());
				
				//Sleep for 0.5 seconds, don't want to overload EspoCRM
				try {
					Thread.sleep(500);
				} catch(InterruptedException e) {
					e.printStackTrace();
				}
			}
			
			App.logInfo("Contact synchronization with EspoCRM complete. Running again in 1 hour!");
			try {
				Thread.sleep(3600000);
			} catch(InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	private void setMailSyncLink(String emailAddress, String contactId) {
		final String endpoint = App.getEnvironment().getEspoHost() + "/api/v1/Contact/" + contactId;
		
		final String finalUrl = App.getEnvironment().getFrontendHost() + "/espogmailsync/all-mail.php?addresses=" + emailAddress;
		
		JSONObject requestBody = new JSONObject();
		//'mailSync' is the name of the Field in EspoCRM
		requestBody.put("mailSync", finalUrl);
		
		HashMap<String, String> headers = new HashMap<>();
		headers.put("X-Hmac-Authorization", EspoUtils.getHmacAuthorization(HttpMethod.PUT, "Contact/" + contactId));
	
		ResponseObject apiResponse;
		try {
			apiResponse = new Http(App.DEBUG).makeRequest(RequestMethod.PUT, endpoint, null, MediaFormat.JSON, requestBody.toString(), headers);
		} catch(IOException e) {
			e.printStackTrace();
			return;
		}
		
		if(apiResponse.getResponseCode() != 200) {
			App.logError(apiResponse.getConnectionMessage());
		}
	}
	
	/**
	 * Get all Contact's known to EspoCRM
	 * @return The result, or an empty String if an error occured
	 */
	private String getContacts() {
		final String endpoint = App.getEnvironment().getEspoHost() + "/api/v1/Contact";
		
		HashMap<String, String> urlParameters = new HashMap<>();
		urlParameters.put("select", "id,emailAddress");
		
		HashMap<String, String> headers = new HashMap<>();
		headers.put("X-Hmac-Authorization", EspoUtils.getHmacAuthorization(HttpMethod.GET, "Contact"));
		
		ResponseObject apiResponse;
		try {
			apiResponse = new Http(App.DEBUG).makeRequest(RequestMethod.GET, endpoint, urlParameters, null, null, headers);
		} catch(IOException e) {
			e.printStackTrace();
			return "";
		}
		
		if(apiResponse.getResponseCode() != 200) {
			App.logError(apiResponse.getConnectionMessage());
			return "";
		}
		
		return apiResponse.getMessage();
	}
}
