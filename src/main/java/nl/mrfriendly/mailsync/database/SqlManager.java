package nl.mrfriendly.mailsync.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import nl.mrfriendly.mailsync.App;
import nl.mrfriendly.mailsync.Environment;
import nl.mrfriendly.mailsync.utils.Utils;

public class SqlManager {

	private Connection connection;
	
	public Connection getConnection() {
		return this.connection;
	}
	
	public SqlManager() {
		App.logInfo("Initializing Database Connector...");
		
		try {
			Class.forName("com.mysql.cj.jdbc.Driver");
		} catch(ClassNotFoundException e) {
			App.logError("Unable to load MySQL Driver. Make sure you have it installed! Exiting");
			App.logDebug(Utils.getStackTrace(e));
			System.exit(1);
		}
		
		App.logInfo("Connecting to database...");

		try {
			Environment e = App.getEnvironment();
			connection = DriverManager.getConnection("jdbc:mysql://" + e.getMysqlHost() + "/" + e.getMysqlDb() + "?user=" + e.getMysqlUsername() + "&password=" +  e.getMysqlPassword());
		} catch (SQLException e) {
			App.logError("Unable to connect to the specified database! Exiting");
			App.logDebug(Utils.getStackTrace(e));
			System.exit(1);
		}
		
		App.logInfo("Connection with database established.");
	}
	
	public ResultSet executeFetchStatement(PreparedStatement pr) throws SQLException {
		return pr.executeQuery();
	}
	
	public void executePutStatement(PreparedStatement pr) throws SQLException {
		pr.execute();
	}
	
	public PreparedStatement createPreparedStatement(String sql) throws SQLException {
		return this.connection.prepareStatement(sql);
	}
}