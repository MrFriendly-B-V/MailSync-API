package nl.thedutchmc.espogmailsync.runnables;

import java.io.IOException;
import java.net.MalformedURLException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import nl.thedutchmc.espogmailsync.App;
import nl.thedutchmc.espogmailsync.Config;
import nl.thedutchmc.httplib.Http;
import nl.thedutchmc.httplib.Http.MediaFormat;
import nl.thedutchmc.httplib.Http.RequestMethod;
import nl.thedutchmc.httplib.Http.ResponseObject;

/**
 * This runnable is used to sync EspoCRM with this application
 * It will add a link to the frontend associated with this application to the mailSync field
 */
public class EspoSyncRunnable implements Runnable {

	@Override
	public void run() {
		App.logInfo("Starting sync with EspoCRM...");
		
		//Get all the Accounts (relations, i.e. companies) from Espo
		final String listAccountsUri = Config.espoHost + "/api/v1/Account";
		
		//Parameters for the request
		HashMap<String, String> params = new HashMap<>();
		params.put("select", "id,emailAddress");
		
		//Headers for the request
		HashMap<String, String> headers = new HashMap<>();
		headers.put("X-Hmac-Authorization", getHmacAuthorization("GET", "Account"));
		
		//Make a request to Espo for all the Accounts (relations) it knows about
		ResponseObject response = null;
		try {
			response = new Http(true).makeRequest(
					RequestMethod.GET, 
					listAccountsUri,
					params, 
					null, //Request body format
					null, //Request body
					headers);
		} catch (MalformedURLException e) {
			App.logError("Unable to fetch list of Accounts from Espo. Caused by MalformedURLException.");
			App.logDebug(ExceptionUtils.getStackTrace(e));
			return;
		} catch (IOException e) {
			App.logError("Unable to fetch list of Accounts from Espo. Caused by IOException.");
			App.logDebug(ExceptionUtils.getStackTrace(e));
			return;
		}
		
		//If the response code isn't 200, something's up. We cannot continue
		if(response.getResponseCode() != 200) {
			App.logError("Unable to fetch list of Accounts from Espo.");
			App.logDebug(response.getConnectionMessage());
			return;
		}

		//Get the `list` array from the response
		JSONArray jsonList = new JSONObject(response.getMessage()).getJSONArray("list");
		
		//Iterate over all the items in the array, each is an Account (relation)
		for(Object oListItem : jsonList) {
			
			//Cast to JSONObject and get the account id
			JSONObject jsonListItem = (JSONObject) oListItem;
			String id = jsonListItem.getString("id");
			
			List<String> accountEmails = new ArrayList<>();
			
			if(!jsonListItem.isNull("emailAddress")) {
				String email = jsonListItem.getString("emailAddress");
				accountEmails.add(email);
			}

			//Make a request to Espo to get all contacts associated with this Account (relation)
			JSONArray jsonContactList = getContactsForAccount(id);
			
			//Iterate over all the items in the array, each is a Contact
			for(Object oContact : jsonContactList) {
				
				//Cast to JSON
				JSONObject jsonContact = (JSONObject) oContact;
				
				//There's a chance the emailAddress field isn't filled in
				//If it isnt filled in, we can't do anything for this contact
				if(jsonContact.isNull("emailAddress")) continue;
				
				//Add the email address of this Contact to the list of the Account (relation)
				accountEmails.add(jsonContact.getString("emailAddress"));
								
				//For every contact we also add a link, with just the email of that contact
				JSONObject putContactMail = new JSONObject();
				putContactMail.put("mailSync", Config.frontendHost + "/espogmailsync/all-mail.php?addresses=" + jsonContact.getString("emailAddress"));
				
				HashMap<String, String> putHeaders = new HashMap<>();
				putHeaders.put("X-Hmac-Authorization", getHmacAuthorization("PUT", "Contact/" + jsonContact.getString("id")));
				
				String targetUri = Config.espoHost + "/api/v1/Contact/" + jsonContact.getString("id");
				
				try {
					new Http(App.DEBUG).makeRequest(RequestMethod.PUT,
							targetUri, 
							null, 
							MediaFormat.JSON, 
							putContactMail.toString(), 
							putHeaders);
				} catch (MalformedURLException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			
			//If there were no email addresses added, continue to the next Account (relation)
			if(accountEmails.size() == 0) continue;
			
			//Join the list into a comma-separated String
			String allMail = String.join(",", accountEmails.toArray(new String[0]));
			
			//Create a JSONObject (the payload) so we can set the mailSync field in Espo for this Account (relation)
			JSONObject jsonPutAllAccountDomains = new JSONObject();
			jsonPutAllAccountDomains.put("mailSync", Config.frontendHost + "/espogmailsync/all-mail.php?addresses=" + allMail);
						
			//Set the headers used for the request
			HashMap<String, String> putHeaders = new HashMap<>();
			putHeaders.put("X-Hmac-Authorization",  getHmacAuthorization("PUT", "Account/" + id));

			String targetUri = Config.espoHost + "/api/v1/Account/" + id;
			
			//Make the request to update the Account with the link to the frontend
			ResponseObject putResponseObject = null;
			try {
				putResponseObject = new Http(App.DEBUG).makeRequest(RequestMethod.PUT,
						targetUri,
						null,
						MediaFormat.JSON,
						jsonPutAllAccountDomains.toString(),
						putHeaders);
			} catch (MalformedURLException e) {
				App.logError("Unable to update emailSync field on Account. Caused by MalformedURLException");
				App.logDebug(ExceptionUtils.getStackTrace(e));
				continue;
			} catch (IOException e) {
				App.logError("Unable to update emailSync field on Account. Caused by IOException");
				App.logDebug(ExceptionUtils.getStackTrace(e));
				continue;
			}
			
			//Check if the response code isn't 200, in that case we want to continue on to the next Account
			if(putResponseObject.getResponseCode() != 200) {
				App.logError("Unable to set emailSync field on Account " + id);
				App.logDebug(putResponseObject.getConnectionMessage());
				continue;
			}
			
			//Sleep for second, as to not overwhelm EspoCRM or anger Windows
			try {
				Thread.sleep(1000L);
			} catch(InterruptedException e) {
				//No need to handle this exception, since it has no effect on the program
			}
			
			App.logDebug("Finished syncing Account " + id + " with EspoCRM");
		}
		
		App.logInfo("Sync complete.");
	}
	
	/**
	 * Method used to fetch all Contacts for the specified Account id from EspoCRM
	 * @param accountId The ID of the Account to fetch all Contacts for
	 * @return JSONArray of Contacts
	 */
	private JSONArray getContactsForAccount(String accountId) {
		//Headers for the request
		HashMap<String, String> headers = new HashMap<>();
		headers.put("X-Hmac-Authorization",  getHmacAuthorization("GET", "Contact"));
		
		final String contactUri = Config.espoHost + "/api/v1/Contact";
		
		/* Generated with the following PHP, derived from the EspoCRM PHP library:
		 * 
		    <?php
			$where = array(
			    [
			        'type' => 'linkedWith',
			        'attribute' => 'account',
			        'value' => 'ACCOUNTID'
			    ]
			    );
			
			$params = [
			    'select' => 'emailAddress',
			    'where' => $where
			];
			
			echo http_build_query($params);
			?>
		 * 
		 * 
		 */
		//EspoCRM uses PHP's way of encoding. 
		//TODO write a function that does this too
		//Decoded: select=emailAddress&where[0][type]=linkedWith&where[0][attribute]=account&where[0][value]=ACCOUNTID
		String params = "select=id,emailAddress&where%5B0%5D%5Btype%5D=linkedWith&where%5B0%5D%5Battribute%5D=account&where%5B0%5D%5Bvalue%5D=" + accountId;

		//TODO this does not work, blame the parser
		/*List<Object> where = new LinkedList<>();
		HashMap<String, String> selectorAccount = new LinkedHashMap<>();
		selectorAccount.put("type", "linkedWith");
		selectorAccount.put("attribute", "account");
		selectorAccount.put("value", accountId);
		where.add(where);
		
		HashMap<String, Object> paramsMap = new LinkedHashMap<>();
		paramsMap.put("select", "emailAddress");
		paramsMap.put("where", where);
		
		App.logInfo(URLBuilder.httpBuildQuery(paramsMap, "UTF-8"));*/
		//END OF THIS DOES NOT WORK
		
		//Make the request to Espo to fetch the Contacts
		ResponseObject responseObject = null;
		try {
			responseObject = new Http(App.DEBUG).makeRequest(
					RequestMethod.GET, 
					contactUri + "?" + params, 
					null, //Params, we gotta do it a different way because of PHP parameter encoding used by EspoCRM 
					null, //Request Body MIME type
					null, //Request Body
					headers);
		} catch (MalformedURLException e) {
			App.logError("Unable to get Contacts for Account " + accountId + ". Caused by a MalformedURLException. Check `espoHost` in the configuration");
			App.logDebug(ExceptionUtils.getStackTrace(e));
		} catch (IOException e) {
			App.logError("Unable to get Contacts for Account " + accountId + ". Caused by an IOException");
			App.logDebug(ExceptionUtils.getStackTrace(e));
		}
		
		//Check if the HTTP status code is something else than 200
		if(responseObject.getResponseCode() != 200) {
			App.logError("Unable to fetch list of Contacts for Account " + accountId);
			App.logDebug(responseObject.getConnectionMessage());
			return null;
		}
		
		//Return the result
		return new JSONObject(responseObject.getMessage()).getJSONArray("list");
	}
	
	/**
	 * Method used to get the HMAC authorization String
	 * @param method HTTP method used, e.g GET or POST
	 * @param path Path the request is going to, that is anything after /api/v1/
	 * @return Returns the HMAC authorization String
	 */
	private String getHmacAuthorization(String method, String path) {
		//Setup the hashing algorithm
		Mac sha256_HMAC = null;
		try {
			sha256_HMAC = Mac.getInstance("HmacSHA256");
			SecretKeySpec secretKey = new SecretKeySpec(Config.espoSecretKey.getBytes(), "HmacSHA256");
			sha256_HMAC.init(secretKey);
		} catch (NoSuchAlgorithmException e) {
			// We don't need to handle this exception, since the `HmacSHA256` algorithm is always there
		} catch (InvalidKeyException e) {
			App.logError("Invalid espoSecretKey. Please double check your config!");
			App.logDebug(ExceptionUtils.getStackTrace(e));
		}
		
		//Get the hash
		//Compose of (method + ' /' + path)
		//Where method: GET, POST etc
		//Where path: Account, Contact etc
		byte[] hash = sha256_HMAC.doFinal((method + " /" + path).getBytes());
		
		//Compose the final list of Bytes
		//Compose of apiKey + ':' + hash
		//String#getBytes() returns a byte[], so we first have to turn it into
		//a Byte[], then put it in a List<Byte> before we can add it.
		List<Byte> hmacBytes = new ArrayList<>();
		hmacBytes.addAll(Arrays.asList(ArrayUtils.toObject((Config.espoApiKey + ":").getBytes())));
		hmacBytes.addAll(Arrays.asList(ArrayUtils.toObject(hash)));
		
		//Get the final hmacAuthorization value
		//First turn the hmacBytes<Byte> into a byte[],
		//Then encode it as base64
		String hmacAuthorization = Base64.getEncoder().encodeToString(ArrayUtils.toPrimitive(hmacBytes.toArray(new Byte[0])));
		
		//Finally return that base64 String
		return hmacAuthorization;
	}
}
