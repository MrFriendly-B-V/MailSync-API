package nl.thedutchmc.espogmailsync.mailobjects;

import java.io.Serializable;
import java.util.List;

public class MessageThread implements Serializable {
	private static final long serialVersionUID = 1L;
	
	private String id;
	private List<Message> messages;
	
	public MessageThread(String id, List<Message> messages) {
		this.id = id;
		this.messages = messages;
	}
	
	public String getId() {
		return this.id;
	}
	
	public List<Message> getMessages() {
		return this.messages;
	}
}
