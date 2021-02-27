package nl.thedutchmc.espogmailsync.gsonobjects.in;

public class AuthResponse {
	private Integer status;
	private String token, email, id;
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
	 * @return the email
	 */
	public String getEmail() {
		return email;
	}
	/**
	 * @return the id
	 */
	public String getId() {
		return id;
	}
}
