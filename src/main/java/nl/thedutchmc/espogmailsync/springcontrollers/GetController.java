package nl.thedutchmc.espogmailsync.springcontrollers;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
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
import nl.thedutchmc.espogmailsync.database.types.DatabaseMessage;
import nl.thedutchmc.espogmailsync.gsonobjects.in.AuthResponse;
import nl.thedutchmc.espogmailsync.gsonobjects.out.BasicResponse;

@RestController
@RequestMapping("/espogmailsync")
public class GetController {

	static final int PAGE_SIZE = 50;
	
	@CrossOrigin(origins = {"https://intern.mrfriendly.nl", "http://localhost"})
	@RequestMapping(value = "mail", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	public String mail(@RequestParam String sessionId, @RequestParam(required = false) String addresses, @RequestParam(required = false) String page) {
		final Gson gson = new Gson();
		
		AuthResponse authResponse = Authentication.isAuthenticated(sessionId);
		if(authResponse == null) {
			return gson.toJson(new BasicResponse(401));
		}
		
		//Validate that the provided page String is a valid integer
		//TODO
		
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
			final String queryPrefix = "SELECT id,sender,receiver,cc,bcc,subject FROM messages WHERE";
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
			
			App.logError(query);
			PreparedStatement pr = sqlManager.createPreparedStatement(query);
			
			//Bind the parameters for the addresses
			int j = 1;
			for(int i = 0; i < addr.length; i++) {
				String address = "%" + addr[i] + "%";
				
				pr.setString(j, address);
				pr.setString(j+1, address);
				pr.setString(j+2, address);
				pr.setString(j+3, address);
				
				j+=4;
			}
			
			pr.setInt(addr.length*4 +1, PAGE_SIZE);
			pr.setInt(addr.length*4 +2, offset);
			
			App.logError(pr.toString());
		} catch(SQLException e) {
			e.printStackTrace();
		}
		
		
		//TODO continue 
		return "";
	}
}
