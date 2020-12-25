package nl.thedutchmc.espogmailsync.database;

import java.sql.Blob;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;

import org.apache.commons.lang3.exception.ExceptionUtils;

import nl.thedutchmc.espogmailsync.App;
import nl.thedutchmc.espogmailsync.mailobjects.Message;
import nl.thedutchmc.espogmailsync.mailobjects.MessageThread;

public class DatabaseGetMail {

	/**
	 * Method to fetch all Message objects from the database
	 * @return Returns a HashMap of the ID of the message, and the Message object
	 */
	public HashMap<String, Message> getMessages() {
		App.logInfo("Fetching all Messages from database...");
		
		SqlManager sqlManager = App.getSqlManager();
		Serializer serializer = new Serializer();
		
		HashMap<String, Message> result = new HashMap<>();
		try {
			
			//Select the 'data' column from the 'messages' table
			String statement = "SELECT data FROM messages";
			ResultObject ro = sqlManager.executeStatement(StatementType.query, sqlManager.getConnection().prepareStatement(statement));
			ResultSet rs = ro.getResultSet();
			
			//Iterate over the retrieved result
			while(rs.next()) {
				
				//Turn the blob back into a Message object
				Blob mBlob = rs.getBlob("data");
				byte[] mBytes = mBlob.getBytes(1, (int) mBlob.length());
				Message m = (Message) serializer.deserializeObject(mBytes);
				
				//Add the Message object to the result map
				result.put(m.getId(), m);
			}
		} catch (SQLException e) {
			App.logError("Unable to fetch Message from Database due to a SQLException");
			App.logDebug(ExceptionUtils.getStackTrace(e));
		}
		
		App.logInfo("All Messages fetched.");
		return result;
	}
	
	/**
	 * Method used to fetch all MessageThreads from the database
	 * @return Returns a HashMap with the ID of the MessageThread, and the MessageThread
	 */
	public HashMap<String, MessageThread> getMessageThreads() {
		App.logInfo("Fetching all MessageThreads from database...");
		
		SqlManager sqlManager = App.getSqlManager();
		Serializer serializer = new Serializer();
		
		HashMap<String, MessageThread> result = new HashMap<>();
		try {
			
			//Select the 'data' column from the 'messageThreads' table
			String statement = "SELECT data FROM messageThreads";
			ResultObject ro = sqlManager.executeStatement(StatementType.query, sqlManager.getConnection().prepareStatement(statement));
			ResultSet rs = ro.getResultSet();
			
			//Iterate over the retrieved resultset
			while(rs.next()) {
				
				//Deserialize the blob back into a MessageThread object
				Blob mtBlob = rs.getBlob("data");
				byte[] mtBytes = mtBlob.getBytes(1, (int) mtBlob.length());
				MessageThread mt = (MessageThread) serializer.deserializeObject(mtBytes);
				
				//Add the MessageThread object to the result map
				result.put(mt.getId(), mt);
			}
		} catch(SQLException e) {
			App.logError("Unable to fetch MessageThread from Database due to a SQLException");
			App.logDebug(ExceptionUtils.getStackTrace(e));
		}
		
		App.logInfo("All MessageThreads fetched.");
		return result;
	}
}
