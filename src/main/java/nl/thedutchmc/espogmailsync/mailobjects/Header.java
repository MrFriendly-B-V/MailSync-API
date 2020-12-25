package nl.thedutchmc.espogmailsync.mailobjects;

import java.io.Serializable;

public class Header implements Serializable {
	private static final long serialVersionUID = 1L;
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
