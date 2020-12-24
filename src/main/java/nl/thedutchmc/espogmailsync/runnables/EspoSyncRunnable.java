package nl.thedutchmc.espogmailsync.runnables;

import java.io.IOException;
import java.net.MalformedURLException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import nl.thedutchmc.espogmailsync.App;
import nl.thedutchmc.espogmailsync.Config;
import nl.thedutchmc.espogmailsync.URLBuilder;
import nl.thedutchmc.httplib.Http;
import nl.thedutchmc.httplib.Http.RequestMethod;
import nl.thedutchmc.httplib.Http.ResponseObject;

public class EspoSyncRunnable implements Runnable {

	@Override
	public void run() {
		
		//Get all the Accounts (relations, i.e. companies) from Espo
		final String listAccountsUri = Config.espoHost + "/api/v1/Account";
		HashMap<String, String> params = new HashMap<>();
		params.put("select", "id");
		
		HashMap<String, String> headers = new HashMap<>();
		headers.put("X-Hmac-Authorization",  getHmacAuthorization("GET", "Account"));
		
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
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		if(response.getResponseCode() != 200) {
			App.logError(response.getConnectionMessage());
			return;
		}
		
		JSONObject jsonResponse = new JSONObject(response.getMessage());
		JSONArray jsonList = jsonResponse.getJSONArray("list");
		for(Object oListItem : jsonList) {
			JSONObject jsonListItem = (JSONObject) oListItem;
			String id = jsonListItem.getString("id");
			
			getContactsForAccount(id);
			
			return;
		}
		
	}
	
	private List<JSONObject> getContactsForAccount(String accountId) {
		
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
		String params = "select=emailAddress&where%5B0%5D%5Btype%5D=linkedWith&where%5B0%5D%5Battribute%5D=account&where%5B0%5D%5Bvalue%5D=" + accountId;

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
		
		ResponseObject responseObject = null;
		try {
			responseObject = new Http(true).makeRequest(
					RequestMethod.GET, 
					contactUri + "?" + params, 
					null, //Params, we gotta do it a different way because of weird param encoding used by EspoCRM 
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
		
		if(responseObject.getResponseCode() != 200) {
			App.logError(responseObject.getConnectionMessage());
			return null;
		}
		
		App.logInfo(responseObject.getMessage());
		return null;
	}
	
	private String getHmacAuthorization(String method, String path) {
		//Setup the hashing algorithm
		Mac sha256_HMAC = null;
		try {
			sha256_HMAC = Mac.getInstance("HmacSHA256");
			SecretKeySpec secretKey = new SecretKeySpec(Config.espoSecretKey.getBytes(), "HmacSHA256");
			sha256_HMAC.init(secretKey);
		} catch (NoSuchAlgorithmException e) {
			// We don't need to handle this exception, since the `HmacSHA256` algorithm is always there
			e.printStackTrace();
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
