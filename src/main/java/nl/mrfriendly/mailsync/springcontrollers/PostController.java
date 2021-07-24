package nl.mrfriendly.mailsync.springcontrollers;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.gson.Gson;

import nl.mrfriendly.mailsync.App;
import nl.mrfriendly.mailsync.database.SqlManager;
import nl.mrfriendly.mailsync.gsonobjects.out.BasicResponse;
import nl.mrfriendly.mailsync.runnables.FetchMailThreadV2;
import nl.mrfriendly.mailsync.utils.Utils;

@RestController
@RequestMapping("/mailsync")
public class PostController {
	
	@CrossOrigin(origins = {"https://intern.mrfriendly.nl", "http://localhost"})
	@RequestMapping(value = "new", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	public String postNew(@RequestParam String sessionId) {
		final Gson gson = new Gson();
		
		if(!Authentication.isAuthenticated(sessionId)) {
			return gson.toJson(new BasicResponse(401));
		}
		
		String userId = Authentication.getUserId(sessionId);
		if(userId == null) {
			return gson.toJson(new BasicResponse(500));
		}
		
		String[] scopes = Authentication.getScopes(userId);
		if(scopes == null) {
			return gson.toJson(new BasicResponse(500));
		}
		
		List<String> scopeList = Arrays.asList(scopes);
		if(!scopeList.contains("mrfriendly.mailsync.new")) {
			return gson.toJson(new BasicResponse(403));
		}
		
		final SqlManager sqlManager = App.getSqlManager();		
		
		//Check if the user ID already exists in the database
		try {
			final String query = "SELECT id FROM users WHERE id=?";
			PreparedStatement pr = sqlManager.createPreparedStatement(query);
			pr.setString(1, userId);
			
			ResultSet rs = sqlManager.executeFetchStatement(pr);
			
			if(rs.next()) {
				return gson.toJson(new BasicResponse(409));
			}
		} catch(SQLException e) {
			App.logError("An error occured while getting information from the database while processing POST /espogmailsync/new");
			App.logDebug(Utils.getStackTrace(e));
			return gson.toJson(new BasicResponse(500));
		}
		
		//Write the new user ID to the database
		try {
			final String query = "INSERT INTO users (id) VALUES (?)";
			PreparedStatement pr = sqlManager.createPreparedStatement(query);
			pr.setString(1, userId);
			
			sqlManager.executePutStatement(pr);
		} catch(SQLException e) {
			App.logError("An error occured while inserting a new user into the database");
			App.logDebug(Utils.getStackTrace(e));
			return gson.toJson(new BasicResponse(500));
		}
		
		//Start a new FetchMailThreadV2
		new Thread(new FetchMailThreadV2(userId)).start();
		
		return gson.toJson(new BasicResponse(200));
	}
}
