package nl.mrfriendly.mailsync.springcontrollers;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
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
import nl.mrfriendly.mailsync.database.types.DatabaseMessage;
import nl.mrfriendly.mailsync.gsonobjects.in.authlander.DescribeUserResponse;
import nl.mrfriendly.mailsync.gsonobjects.out.BasicResponse;
import nl.mrfriendly.mailsync.gsonobjects.out.GetActive;
import nl.mrfriendly.mailsync.gsonobjects.out.GetMail;
import nl.mrfriendly.mailsync.utils.Utils;

@RestController
@RequestMapping("/mailsync")
public class GetController {

	static final int PAGE_SIZE = 50;
	
	@CrossOrigin(origins = {"https://intern.mrfriendly.nl", "http://localhost"})
	@RequestMapping(value = "mail", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	public String mail(@RequestParam String sessionId, @RequestParam(required = false) String addresses, @RequestParam(required = false) String page) {
		final Gson gson = new Gson();
		
		String userId = Authentication.getUserId(sessionId);
		if(userId == null) {
			return gson.toJson(new BasicResponse(401));
		}
		
		String[] scopes = Authentication.getScopes(userId);
		if(scopes == null) {
			return gson.toJson(new BasicResponse(500));
		}
		
		List<String> scopeList = Arrays.asList(scopes);
		if(!scopeList.contains("mrfriendly.mailsync.read")) {
			return gson.toJson(new BasicResponse(403));
		}
		
		//-1 because page 1 in the query is page 0 for us, etc.
		int pageInt = Integer.valueOf(page) -1;
		
		//Calculate page offset
		int offset = PAGE_SIZE * pageInt;
		
		//Split addresses into an Array
		String[] addr = addresses.split(",");
		
		List<DatabaseMessage> messages = new ArrayList<>();
		
		//Fetch all mail from the database for the provided addr
		final SqlManager sqlManager = App.getSqlManager();
		try {
			final String queryPrefix = "SELECT id,sender,receiver,cc,bcc,subject,data,internal_date FROM messages WHERE";
			final String variableQuery = "sender LIKE ? OR receiver LIKE ? OR cc LIKE ? OR bcc LIKE ?";
			final String querySuffix = "LIMIT ? OFFSET ?";
			
			String query = queryPrefix;
			for(int i = 0; i < addr.length; i++) {
				
				if(i != 0) {
					query += " OR";
				}
				
				query += " " + variableQuery;
			}
			query += " " + querySuffix;
			
			PreparedStatement pr = sqlManager.createPreparedStatement(query);
			
			//Bind the parameters for the addresses
			int j = 1;
			for(int i = 0; i < addr.length; i++) {
				//Surround with '%' because then MySQL will look for anything that
				//Contains the address, which is what we want
				String address = "%" + addr[i] + "%";
				
				pr.setString(j, address);
				pr.setString(j+1, address);
				pr.setString(j+2, address);
				pr.setString(j+3, address);
				
				j+=4;
			}
			
			pr.setInt(addr.length*4 +1, PAGE_SIZE);
			pr.setInt(addr.length*4 +2, offset);
			
			//Execute the statement, and deserialize the ResultSet into a DatabaseMessage.
			//Add every DatabaseMessage instance to the List of messages
			ResultSet rs = sqlManager.executeFetchStatement(pr);
			while(rs.next()) {
				DatabaseMessage dbMessage = new DatabaseMessage(rs.getString("id"), rs.getString("data"));
				
				dbMessage.setReceiver(rs.getString("receiver"));
				dbMessage.setSender(rs.getString("sender"));
				dbMessage.setCc(rs.getString("cc"));
				dbMessage.setBcc(rs.getString("bcc"));
				dbMessage.setSubject(rs.getString("subject"));
				dbMessage.setInternalDate(rs.getString("internal_date"));
				
				messages.add(dbMessage);
			}
			
		} catch(SQLException e) {
			App.logError("SQLException occured while processing GET /mailsync/mail request.");
			App.logDebug(Utils.getStackTrace(e));
			return gson.toJson(new BasicResponse(500));
		}
		
		return gson.toJson(new GetMail(
				200,
				messages.toArray(new DatabaseMessage[0]), 
				pageInt +1, // +1 here. Frontend is not 0-based
				(messages.size() >= 50) ? true : false));
	}
	
	@CrossOrigin(origins = {"https://intern.mrfriendly.nl", "http://localhost"})
	@RequestMapping(value = "active", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	public String getActive(@RequestParam String sessionId) {
		final Gson gson = new Gson();
		
		String authenticatingUserId = Authentication.getUserId(sessionId);
		if(authenticatingUserId == null) {
			return gson.toJson(new BasicResponse(401));
		}
		
		String[] scopes = Authentication.getScopes(authenticatingUserId);
		if(scopes == null) {
			return gson.toJson(new BasicResponse(500));
		}
		
		List<String> scopeList = Arrays.asList(scopes);
		if(!scopeList.contains("mrfriendly.mailsync.active")) {
			return gson.toJson(new BasicResponse(403));
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
			App.logError("An error occured while getting all user IDs from the database while processing POST /mailsync/active");
			App.logDebug(Utils.getStackTrace(e));
			return gson.toJson(new BasicResponse(500));
		}
		
		//For every User ID make a request to the authentication server to get the email address
		List<String> emailAddresses = new ArrayList<>();
		for(String userId : userIds) {
			DescribeUserResponse dur = Authentication.getUserDetails(userId);
			if(dur.email == null) {
				continue;
			}
				
			emailAddresses.add(dur.email);
		}
		
		return gson.toJson(new GetActive(200, emailAddresses.toArray(new String[0])));
	}
}
