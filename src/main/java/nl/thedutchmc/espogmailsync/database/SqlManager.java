package nl.thedutchmc.espogmailsync.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.commons.lang3.exception.ExceptionUtils;

import nl.thedutchmc.espogmailsync.App;
import nl.thedutchmc.espogmailsync.Config;

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
			App.logDebug(ExceptionUtils.getStackTrace(e));
			System.exit(1);
		}
		
		App.logInfo("Connecting to the database...");

		try {
			connection = DriverManager.getConnection("jdbc:mysql://" + Config.mysqlHost + "/" + Config.mysqlDb + "?user=" + Config.mysqlUser + "&password=" +  Config.mysqlPassword);
		} catch (SQLException e) {
			App.logError("Unable to connect to the specified database! Exiting");
			App.logDebug(ExceptionUtils.getStackTrace(e));
			System.exit(1);
		}
		
		App.logInfo("Connection with database established.");
	}
	
	/**
	 * Execute a statement against a database
	 * @param type Type of statement
	 * @param preparedStatement The statement to execute
	 * @return Returns a ResultObject 
	 * @throws SQLException
	 */
	public ResultObject executeStatement(StatementType type, PreparedStatement preparedStatement) throws SQLException {
		if(type == StatementType.query) {
			ResultSet resultSet = preparedStatement.executeQuery();
			return new ResultObject(type, resultSet);
		} else if(type == StatementType.update) {
			int resultInt = preparedStatement.executeUpdate();
			return new ResultObject(type, resultInt);
		}
		
		return null;
	}
	
}
