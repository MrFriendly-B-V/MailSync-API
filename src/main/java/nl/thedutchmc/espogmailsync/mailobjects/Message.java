package nl.thedutchmc.espogmailsync.mailobjects;

import java.io.Serializable;
import java.util.List;

public class Message implements Serializable {	
	private static final long serialVersionUID = 1L;
	
	private String id, threadId, epochDate, messageText, messageHtml;
	private long epochLong;
	private List<Label> labels;
	private List<Header> headers;
	
	public Message(String id, String threadId, String epochDate, List<Label> labels, List<Header> headers) {
		this.id = id;
		this.threadId = threadId;
		this.epochDate = epochDate;
		this.headers = headers;
		
		epochLong = Long.valueOf(epochDate);
	}
	
	public String getMessageText() {
		return this.messageText;
	}
	
	public void setMessageText(String messageText) {
		this.messageText = messageText;
	}
	
	public String getMessageHtml() {
		return this.messageHtml;
	}
	
	public void setMessageHtml(String messageHtml) {
		this.messageHtml = messageHtml;
	}
	
	public List<Header> getHeaders() {
		return this.headers;
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
	
	public List<Label> getLabels() {
		return this.labels;
	}
	
	public long getEpochLong() {
		return this.epochLong;
	}
}
