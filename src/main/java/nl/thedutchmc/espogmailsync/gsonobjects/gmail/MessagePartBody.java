package nl.thedutchmc.espogmailsync.gsonobjects.gmail;

public class MessagePartBody {
	private String attachmentId, data;
	private Integer size;
	
	/**
	 * @return the attachmentId
	 */
	public String getAttachmentId() {
		return attachmentId;
	}
	/**
	 * @return the data
	 */
	public String getData() {
		return data;
	}
	/**
	 * @return the size
	 */
	public Integer getSize() {
		return size;
	}
}
