package nl.thedutchmc.espogmailsync;

public class User {

	private String id, token, email;
	
	public User(String id, String token, String email) {
		this.id = id;
		this.token = token;
		this.email = email;
	}
	
	public String getId() {
		return this.id;
	}
	
	public String getToken() {
		return this.token;
	}
	
	public void setToken(String token) {
		this.token = token;
	}
	
	public String getEmail() {
		return this.email;
	}
}
