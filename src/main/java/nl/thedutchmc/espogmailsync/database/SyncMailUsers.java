package nl.thedutchmc.espogmailsync.database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.exception.ExceptionUtils;

import nl.thedutchmc.espogmailsync.App;

public class SyncMailUsers {

	private SqlManager sqlManager;
	
	public SyncMailUsers() {
		this.sqlManager = App.getSqlManager();
	}
	
	/**
	 * Retrieve all mail users from the database
	 * @return Returns a String[] of ids of all the mail users in the database
	 */
	public String[] getMailUsers() {
		try {
			
			//Make the request to the database
			String statement = "SELECT id FROM mailUsers";
			ResultObject ro = sqlManager.executeStatement(StatementType.query, sqlManager.getConnection().prepareStatement(statement));
			ResultSet rs = ro.getResultSet();
			
			//Iterate over the results
			List<String> ids = new ArrayList<>();
			while(rs.next()) {
				ids.add(rs.getString("id"));
			}
			
			//Convert the ids list to a String array and return
			return ids.toArray(new String[0]);
		} catch(SQLException e) {
			App.logError("Unable to fetch mailUsers from database due to a SQLException");
			App.logDebug(ExceptionUtils.getStackTrace(e));
		}
		
		return null;
	}
	
	/**
	 * Add a mail user to the database
	 * @param id
	 */
	public void addMailUser(String id) {
		try {
			String statement = "REPLACE INTO mailUsers (id) VALUES ('" + id + "')";
			sqlManager.executeStatement(StatementType.update, sqlManager.getConnection().prepareStatement(statement));
		} catch (SQLException e) {
			App.logError("Unable to insert mailUser " + id + " into database due to a SQLException");
			App.logDebug(ExceptionUtils.getStackTrace(e));
		}
	}
}
