package nl.thedutchmc.espogmailsync.runnables.mailobjects;

import java.util.List;

public class MessageThread {
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
