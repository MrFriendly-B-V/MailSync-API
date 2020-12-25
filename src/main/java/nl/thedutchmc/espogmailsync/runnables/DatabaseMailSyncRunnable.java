package nl.thedutchmc.espogmailsync.runnables;

import java.sql.Blob;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.rowset.serial.SerialBlob;

import org.apache.commons.lang3.exception.ExceptionUtils;

import nl.thedutchmc.espogmailsync.App;
import nl.thedutchmc.espogmailsync.database.Serializer;
import nl.thedutchmc.espogmailsync.database.SqlManager;
import nl.thedutchmc.espogmailsync.database.StatementType;
import nl.thedutchmc.espogmailsync.mailobjects.Message;
import nl.thedutchmc.espogmailsync.mailobjects.MessageThread;

/**
 * This runnable is used to sync fetched emails with a SQL database
 */
public class DatabaseMailSyncRunnable implements Runnable {
	
	@Override
 	public void run() {
		App.logInfo("Starting sync with Database for Messages and MessageThreads...");
		
		//Turn the HashMaps in Apps into Lists, we don't care for the keys
		List<Message> messages = new ArrayList<>(App.getMessages().values());
		List<MessageThread> messageThreads = new ArrayList<>(App.getMessageThreads().values());
		
		Serializer ser = new Serializer();
		SqlManager sqlManager = App.getSqlManager();
		
		//Iterate over all Messages
		for(int i = 0; i < messages.size(); i++) {
			Message m = messages.get(i);
			
			//Serialize the Message object
			byte[] mSerialized = ser.serializeObject(m);
			
			try {
				//Create a blob out of the serialized Message
				Blob mBlob = new SerialBlob(mSerialized);
				
				//Prepare a statement to execute
				PreparedStatement preparedStatement = sqlManager.getConnection().prepareStatement("REPLACE INTO messages (id, data) VALUES (?, ?)");
				preparedStatement.setInt(1, i);
				preparedStatement.setBlob(2, mBlob);
				
				//Execute the statement
				sqlManager.executeStatement(StatementType.update, preparedStatement);
			} catch (SQLException e) {
				App.logError("Unable to write Message object to SQL database. Caused by SQLException");
				App.logDebug(ExceptionUtils.getStackTrace(e));
			}
			
			App.logDebug("Serialized Message and stored it into databse with message ID: " + m.getId());
		}
		
		//Iterate over all MessageThreads
		for(int i = 0; i < messageThreads.size(); i++) {
			MessageThread mt = messageThreads.get(i);
			
			//Serialize the MessageThread
			byte[] mtSerialized = ser.serializeObject(mt);
			
			try {
				//Creae a blob out of the serialized MessageThread
				Blob mtBlob = new SerialBlob(mtSerialized);
				
				//Prepare a statement to execute
				PreparedStatement preparedStatement = sqlManager.getConnection().prepareStatement("REPLACE INTO messageThreads (id, data) VALUES (?, ?)");
				preparedStatement.setInt(1, i);
				preparedStatement.setBlob(2, mtBlob);
				
				//Execute the statement
				App.getSqlManager().executeStatement(StatementType.update, preparedStatement);
			} catch (SQLException e) {
				App.logError("Unable to write MessageThread object to SQL database. Caused by SQLException");
				App.logDebug(ExceptionUtils.getStackTrace(e));
			}
			
			App.logDebug("Serialized MessageThread and stored it into databse with message ID: " + mt.getId());
		}
		
		App.logInfo("Sync complete.");
	}
}
