package nl.thedutchmc.espogmailsync.mailobjects;

import java.io.Serializable;

public class Label implements Serializable {
	private static final long serialVersionUID = 1L;
	
	private String id, name, colorHexText, colorHexBackground;
	
	public Label(String id, String name, String colorHexText, String colorHexBackground) {
		this.id = id;
		this.name = name;
		this.colorHexText = colorHexText;
		this.colorHexBackground = colorHexBackground;
	}
	
	public String getId() {
		return this.id;
	}
	
	public String getName() {
		return this.name;
	}
	
	public String getColorHexText() {
		return this.colorHexText;
	}
	
	public String getColorHexBackground() {
		return this.colorHexBackground;
	}
}
