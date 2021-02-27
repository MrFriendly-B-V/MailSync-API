package nl.thedutchmc.espogmailsync.gsonobjects.gmail;

public class MessagePart {

	private String partId, mimeType, filename;
	
	private Header[] headers;
	private MessagePartBody body;
	
	private MessagePart[] parts;


	/**
	 * @return the partId
	 */
	public String getPartId() {
		return partId;
	}


	/**
	 * @return the mimeType
	 */
	public String getMimeType() {
		return mimeType;
	}


	/**
	 * @return the filename
	 */
	public String getFilename() {
		return filename;
	}


	/**
	 * @return the headers
	 */
	public Header[] getHeaders() {
		return headers;
	}


	/**
	 * @return the parts
	 */
	public MessagePart[] getParts() {
		return parts;
	}


	/**
	 * @return the body
	 */
	public MessagePartBody getBody() {
		return body;
	}
	
}
