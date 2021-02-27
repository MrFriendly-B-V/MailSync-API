package nl.thedutchmc.espogmailsync.gsonobjects.in.gmail;

public class Message {

	private String id, threadId, snippet, historyId, internalDate, raw;
	private String[] threadIds;
	private Integer sizeEstimate;
	
	private MessagePart payload;

	/**
	 * @return the id
	 */
	public String getId() {
		return id;
	}

	/**
	 * @return the threadId
	 */
	public String getThreadId() {
		return threadId;
	}

	/**
	 * @return the snippet
	 */
	public String getSnippet() {
		return snippet;
	}

	/**
	 * @return the historyId
	 */
	public String getHistoryId() {
		return historyId;
	}

	/**
	 * @return the internalDate
	 */
	public String getInternalDate() {
		return internalDate;
	}

	/**
	 * @return the raw
	 */
	public String getRaw() {
		return raw;
	}

	/**
	 * @return the threadIds
	 */
	public String[] getThreadIds() {
		return threadIds;
	}

	/**
	 * @return the sizeEstimate
	 */
	public Integer getSizeEstimate() {
		return sizeEstimate;
	}

	/**
	 * @return the payload
	 */
	public MessagePart getPayload() {
		return payload;
	}
}
