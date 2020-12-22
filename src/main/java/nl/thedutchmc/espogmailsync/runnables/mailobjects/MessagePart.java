package nl.thedutchmc.espogmailsync.runnables.mailobjects;

import java.util.List;

public class MessagePart {
	private List<Header> headers;
	private String attachmentId, data64;
	private String mimeType;
	
	private List<MessagePart> subparts;
	
	public MessagePart(List<Header> headers, String attachmentId, String data64, List<MessagePart> subparts, String mimeType) {
		this.headers = headers;
		this.attachmentId = attachmentId;
		this.data64 = data64;
		this.subparts = subparts;
		this.mimeType = mimeType;
	}
	
	public List<Header> getHeaders() {
		return this.headers;
	}
	
	public String getAttachmentId() {
		return this.attachmentId;
	}
	
	public String getData64() {
		return this.data64;
	}
	
	public List<MessagePart> getSubParts() {
		return this.subparts;
	}
	
	public String getMimeType() {
		return this.mimeType;
	}
}
