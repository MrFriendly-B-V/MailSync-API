package nl.thedutchmc.espogmailsync.springcontrollers;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
import nl.thedutchmc.espogmailsync.gsonobjects.out.BasicResponse;
import nl.thedutchmc.espogmailsync.runnables.FetchMailThreadV2;
import nl.thedutchmc.espogmailsync.utils.Utils;

@RestController
@RequestMapping("/espogmailsync")
public class PostController {
	
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
			App.logError("An error occured while getting information from the database while processing POST /espogmailsync/new");
			App.logDebug(Utils.getStackTrace(e));
			return gson.toJson(new BasicResponse(500));
		}
		
		//Write the new user ID to the database
		try {
			final String query = "INSERT INTO users (id) VALUES (?)";
			PreparedStatement pr = sqlManager.createPreparedStatement(query);
			pr.setString(1, authResponse.getId());
			
			sqlManager.executePutStatement(pr);
		} catch(SQLException e) {
			App.logError("An error occured while inserting a new user into the database");
			App.logDebug(Utils.getStackTrace(e));
			return gson.toJson(new BasicResponse(500));
		}
		
		//Start a new FetchMailThreadV2
		new Thread(new FetchMailThreadV2(authResponse.getId())).start();
		
		return gson.toJson(new BasicResponse(200));
	}
}
