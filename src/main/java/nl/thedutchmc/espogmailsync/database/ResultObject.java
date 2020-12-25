package nl.thedutchmc.espogmailsync.database;

import java.sql.ResultSet;

public class ResultObject {
	private ResultSet resultSet;
	private int resultInt;
	private StatementType type;
	
	public ResultObject(StatementType type, ResultSet resultSet) {
		this.type = type;
		this.resultSet = resultSet;
	}
	
	public ResultObject(StatementType type, int resultInt) {
		this.type = type;
		this.resultInt = resultInt;
	}
	
	public ResultSet getResultSet() {
		return this.resultSet;
	}
	
	public int getResultInt() {
		return this.resultInt;
	}
	
	public StatementType getStatementType() {
		return this.type;
	}
}
