package nl.thedutchmc.espogmailsync.springcontrollers;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.gson.Gson;

import nl.thedutchmc.espogmailsync.App;
import nl.thedutchmc.espogmailsync.database.SqlManager;
import nl.thedutchmc.espogmailsync.gsonobjects.in.AuthResponse;
import nl.thedutchmc.espogmailsync.gsonobjects.in.TokenResponse;
import nl.thedutchmc.espogmailsync.gsonobjects.out.BasicResponse;
import nl.thedutchmc.espogmailsync.gsonobjects.out.GetActive;
import nl.thedutchmc.httplib.Http;
import nl.thedutchmc.httplib.Http.ResponseObject;

@RestController
@RequestMapping("/espogmailsync")
public class PostController {
	
	//TODO move to a GET request
	@CrossOrigin(origins = {"https://intern.mrfriendly.nl", "http://localhost"})
	@RequestMapping(value = "active", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	public String getActive(@RequestParam String sessionId) {
		final Gson gson = new Gson();
		
		AuthResponse authResponse = Authentication.isAuthenticated(sessionId);
		if(authResponse == null) {
			return gson.toJson(new BasicResponse(401));
		}
		
		//Fetch all user IDs from the database
		List<String> userIds = new ArrayList<>();
		try {
			final SqlManager sqlManager = App.getSqlManager();
			final String query = "SELECT id FROM users";
			PreparedStatement pr = sqlManager.createPreparedStatement(query);
			
			ResultSet rs = sqlManager.executeFetchStatement(pr);
			
			while(rs.next()) {
				userIds.add(rs.getString("id"));
			}
		} catch(SQLException e) {
			e.printStackTrace();
			return gson.toJson(new BasicResponse(500));
		}
		
		final String endpoint = App.getEnvironment().getAuthServerHost() + "/oauth/token";
		
		//For every User ID make a request to the authentication server to get the email address
		List<String> emailAddresses = new ArrayList<>();
		for(String userId : userIds) {
			HashMap<String, String> urlParameters = new HashMap<>();
			urlParameters.put("userId", userId);
			urlParameters.put("apiToken", App.getEnvironment().getAuthApiToken());
			
			ResponseObject apiResponse;
			try {
				apiResponse = new Http(App.DEBUG).makeRequest(Http.RequestMethod.POST, endpoint, urlParameters, null, null, null);
			} catch(IOException e) {
				e.printStackTrace();
				return gson.toJson(new BasicResponse(500));
			}
			
			if(apiResponse.getResponseCode() != 200) {
				App.logError(apiResponse.getConnectionMessage());
				return gson.toJson(new BasicResponse(500));
			}
			
			TokenResponse tokenResponse = gson.fromJson(apiResponse.getMessage(), TokenResponse.class);
			if(tokenResponse.getStatus() != 200) {
				App.logError(tokenResponse.getMessage());
				return gson.toJson(new BasicResponse(500));
			}
				
			emailAddresses.add(tokenResponse.getEmail());
		}
		
		return gson.toJson(new GetActive(200, emailAddresses.toArray(new String[0])));
	}
	
	@CrossOrigin(origins = {"https://intern.mrfriendly.nl", "http://localhost"})
	@RequestMapping(value = "new", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	public String postNew(@RequestParam String sessionId) {
		final Gson gson = new Gson();
		
		AuthResponse authResponse = Authentication.isAuthenticated(sessionId);
		if(authResponse == null) {
			return gson.toJson(new BasicResponse(401));
		}
		
		final SqlManager sqlManager = App.getSqlManager();
		
		//Check if the user ID already exists in the database
		try {
			final String query = "SELECT id FROM users WHERE id=?";
			PreparedStatement pr = sqlManager.createPreparedStatement(query);
			pr.setString(1, authResponse.getId());
			
			ResultSet rs = sqlManager.executeFetchStatement(pr);
			if(rs.getMetaData().getColumnCount() > 0) {
				return gson.toJson(new BasicResponse(409));
			}
		} catch(SQLException e) {
			e.printStackTrace();
			return gson.toJson(new BasicResponse(500));
		}
		
		//Write the new user ID to the database
		try {
			final String query = "INSERT INTO users (id) VALUES (?)";
			PreparedStatement pr = sqlManager.createPreparedStatement(query);
			pr.setString(1, authResponse.getId());
			
			sqlManager.executePutStatement(pr);
		} catch(SQLException e) {
			e.printStackTrace();
			return gson.toJson(new BasicResponse(500));
		}
		
		return gson.toJson(new BasicResponse(200));
	}
}
