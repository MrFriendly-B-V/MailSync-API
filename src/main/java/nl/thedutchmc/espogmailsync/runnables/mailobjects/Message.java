package nl.thedutchmc.espogmailsync.runnables.mailobjects;

import java.util.List;

public class Message {	
	private String id, threadId, epochDate;
	private long epochLong;
	private MessagePart messagePart;
	private List<Label> labels;
	
	public Message(String id, String threadId, String epochDate, MessagePart messagePart, List<Label> labels) {
		this.id = id;
		this.threadId = threadId;
		this.epochDate = epochDate;
		this.messagePart = messagePart;
		
		epochLong = Long.valueOf(epochDate);
	}
	
	public String getId() {
		return this.id;
	}
	
	public String getThreadId() {
		return this.threadId;
	}
	
	public String getEpochDate() {
		return this.epochDate;
	}
	
	public MessagePart getMessagePart() {
		return this.messagePart;
	}
	
	public List<Label> getLabels() {
		return this.labels;
	}
	
	public long getEpochLong() {
		return this.epochLong;
	}
}
