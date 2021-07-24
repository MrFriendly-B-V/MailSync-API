package nl.mrfriendly.espogmailsync.runnables;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.json.JSONObject;

import com.google.gson.Gson;

import nl.mrfriendly.espogmailsync.App;
import nl.mrfriendly.espogmailsync.gsonobjects.in.espocrm.Account;
import nl.mrfriendly.espogmailsync.gsonobjects.in.espocrm.Contact;
import nl.mrfriendly.espogmailsync.gsonobjects.in.espocrm.GetAccounts;
import nl.mrfriendly.espogmailsync.gsonobjects.in.espocrm.GetContacts;
import nl.mrfriendly.espogmailsync.utils.EspoUtils;
import nl.mrfriendly.espogmailsync.utils.Utils;
import nl.mrfriendly.espogmailsync.utils.EspoUtils.HttpMethod;
import nl.thedutchmc.httplib.Http;
import nl.thedutchmc.httplib.Http.MediaFormat;
import nl.thedutchmc.httplib.Http.RequestMethod;
import nl.thedutchmc.httplib.Http.ResponseObject;

public class EspoAccountSyncRunnableV2 implements Runnable {

	@Override
	public void run() {
		while(App.RUNNING) {
			App.logInfo("Starting Account synchronization with EspoCRM.");
			
			//GSON instance used in this Thread
			final Gson gson = new Gson();
			
			//Get all Account's from EspoCRM
			//if accountsStr is empty, something went wrong. Continue.
			String accountsStr = getAccounts();
			if(accountsStr == "") {
				continue;
			}
			
			//Iterate over each account
			GetAccounts getAccounts = gson.fromJson(accountsStr, GetAccounts.class);
			for(Account account : getAccounts.getAccounts()) {
				App.logDebug("Processing Account " + account.getId());
				
				List<String> emailsInAccount = new ArrayList<>();
				
				//If the Account has an E-Mail address, add it to the list
				if(account.getEmailAddress() != null) {
					emailsInAccount.add(account.getEmailAddress());
				}
				
				//Get all Contact's linked with this account.
				//if getContactsForAccountStr is empty, something went wrong. Continue.
				String getContactsForAccountStr = getContactsForAccount(account.getId());
				if(getContactsForAccountStr == "") {
					continue;
				}
				
				//Loop over every Contact returned
				GetContacts getContacts = gson.fromJson(getContactsForAccountStr, GetContacts.class);
				for(Contact contact : getContacts.getContacts()) {
					App.logDebug(String.format("Processing Contact %s for Account %s", contact.getId(), account.getId()));
					
					//If the Contact has an E-Mail address, add it to the list
					if(contact.getEmailAddress() != null) {
						emailsInAccount.add(contact.getEmailAddress());
					}
				}
				
				//Add a MailSync URL to the Account, based off the email address found
				setMailSyncLink(emailsInAccount, account.getId());
				
				//Sleep for 0.5 seconds, don't want to overwhelm EspoCRM
				try {
					Thread.sleep(500);
				} catch(InterruptedException e) {
					App.logDebug(Utils.getStackTrace(e));
				}
			}
			
			App.logInfo("Account synchronization with EspoCRM complete. Running again in 1 hour!");
			try {
				Thread.sleep(3600000);
			} catch(InterruptedException e) {
				App.logDebug(Utils.getStackTrace(e));
			}
		}
	}
	
	/**
	 * Set the value for the mailSync field in EspoCRM
	 * @param emailAddresses The list of email addresses to use
	 * @param accountId The ID of the Account to set the field for
	 */
	private void setMailSyncLink(List<String> emailAddresses, String accountId) {
		final String allMailAddresses = String.join(",", emailAddresses.toArray(new String[0]));
		final String finalUrl = App.getEnvironment().getFrontendHost() + "/espogmailsync/all-mail.php?addresses=" + allMailAddresses;
		
		final String endpoint = App.getEnvironment().getEspoHost() + "/api/v1/Account/" + accountId;
		
		JSONObject requestBody = new JSONObject();
		//'mailSync' is the name of the Field in EspoCRM
		requestBody.put("mailSync", finalUrl);
		
		HashMap<String, String> headers = new HashMap<>();
		headers.put("X-Hmac-Authorization", EspoUtils.getHmacAuthorization(HttpMethod.PUT, "Account/" + accountId));
		
		ResponseObject apiResponse;
		try {
			apiResponse = new Http(App.DEBUG).makeRequest(RequestMethod.PUT, endpoint, null, MediaFormat.JSON, requestBody.toString(), headers);
		} catch(IOException e) {
			App.logError("An issue occured while setting the mailSync URL for Account " + accountId);
			App.logDebug(Utils.getStackTrace(e));
			return;
		}
		
		if(apiResponse.getResponseCode() != 200) {
			App.logError("Got non-200 status code while setting mailSync URL for Account " + accountId);
			App.logDebug(apiResponse.getConnectionMessage());
		}
		
	}
	
	/**
	 * Get all Contact's associated with an Account
	 * @param accountId The ID of the Account
	 * @return Returns the result, or an empty String if an error occured
	 */
	private String getContactsForAccount(String accountId) {
		final String endpoint = App.getEnvironment().getEspoHost() + "/api/v1/Contact";
		
		HashMap<String, String> headers = new HashMap<>();
		headers.put("X-Hmac-Authorization", EspoUtils.getHmacAuthorization(HttpMethod.GET, "Contact"));

		HashMap<String, String> accountFilter = new HashMap<>();
		accountFilter.put("type", "linkedWith");
		accountFilter.put("attribute", "account");
		accountFilter.put("value", accountId);
		
		List<HashMap<String, String>> whereFilter = new ArrayList<>();
		whereFilter.add(accountFilter);
		
		HashMap<String, Object> parameters = new HashMap<>();
		parameters.put("select", "id,emailAddress");
		parameters.put("where", whereFilter);
		
		String urlParametersEncoded = EspoUtils.httpBuildQuery(parameters, "UTF-8");
		
		ResponseObject apiResponse;
		try {
			apiResponse = new Http(App.DEBUG).makeRequest(RequestMethod.GET, endpoint + "?" + urlParametersEncoded, null, null, null, headers);
		} catch(IOException e) {
			App.logError("An error occured while getting Contacts linked to Account " + accountId);
			App.logDebug(Utils.getStackTrace(e));
			return "";
		}
		
		if(apiResponse.getResponseCode() != 200) {
			App.logError("Got non-200 status code while getting Contacts linked to Account " + accountId);
			App.logDebug(apiResponse.getConnectionMessage());
			return "";
		}
		
		return apiResponse.getMessage();
	}
	
	/**
	 * Get all Account's from EspoCRM
	 * @return The result, or an empty String if an error occured
	 */
	private String getAccounts() {
		final String endpoint = App.getEnvironment().getEspoHost() + "/api/v1/Account";
		
		HashMap<String, String> urlParameters = new HashMap<>();
		urlParameters.put("select", "id,emailAddress");
		
		HashMap<String, String> headers = new HashMap<>();
		headers.put("X-Hmac-Authorization", EspoUtils.getHmacAuthorization(HttpMethod.GET, "Account"));
		
		ResponseObject apiResponse;
		try {
			apiResponse = new Http(App.DEBUG).makeRequest(RequestMethod.GET, endpoint, urlParameters, null, null, headers);
		} catch(IOException e) {
			App.logError("An error occured while getting Accounts from EspoCRM");
			App.logDebug(Utils.getStackTrace(e));
			return "";
		}
		
		if(apiResponse.getResponseCode() != 200) {
			App.logError("Got non-200 status code while getting Accounts from EspoCRM");
			App.logDebug(apiResponse.getConnectionMessage());
			return "";
		}
		
		return apiResponse.getMessage();
	}
}
