package nl.thedutchmc.espogmailsync.gsonobjects.in;

public class TokenResponse {
	private Integer status;
	private String message, token, id, email;
	
	/**
	 * @return the status
	 */
	public Integer getStatus() {
		return status;
	}
	/**
	 * @return the token
	 */
	public String getToken() {
		return token;
	}
	/**
	 * @return the id
	 */
	public String getId() {
		return id;
	}
	/**
	 * @return the email
	 */
	public String getEmail() {
		return email;
	}
	/**
	 * @return the message
	 */
	public String getMessage() {
		return message;
	}
}
