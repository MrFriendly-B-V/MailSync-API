package nl.mrfriendly.espogmailsync.gsonobjects.out;

import com.google.gson.annotations.SerializedName;

import nl.mrfriendly.espogmailsync.database.types.DatabaseMessage;

public class GetMail extends BasicResponse {

	@SerializedName("messages")
	DatabaseMessage[] databaseMessages;
	int page;
	boolean hasNextPage;
	
	public GetMail(int status, DatabaseMessage[] databaseMessages, int page, boolean hasNextPage) {
		super(status);
		
		this.databaseMessages = databaseMessages;
		this.page = page;
		this.hasNextPage = hasNextPage;
	}
}
