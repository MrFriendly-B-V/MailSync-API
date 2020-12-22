package nl.thedutchmc.espogmailsync.runnables.mailobjects;

public class Header {
	private String name, value;
	
	public Header(String name, String value) {
		this.name = name;
		this.value = value;
	}
	
	public String getName() {
		return this.name;
	}
	
	public String getValue() {
		return this.value;
	}
}
