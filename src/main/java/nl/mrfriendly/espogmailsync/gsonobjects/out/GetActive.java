package nl.mrfriendly.espogmailsync.gsonobjects.out;

public class GetActive extends BasicResponse {

	@SuppressWarnings("unused")
	private String[] activeUsers;
	
	public GetActive(int status, String[] activeUsers) {
		super(status);
		
		this.activeUsers = activeUsers;
	}
}
