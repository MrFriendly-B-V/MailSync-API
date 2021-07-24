package nl.mrfriendly.espogmailsync.gsonobjects.out;

public class BasicResponse {

	private int status;
	
	public BasicResponse(int status) {
		this.status = status;
	}

	/**
	 * @return the status
	 */
	public int getStatus() {
		return status;
	}
	
}
