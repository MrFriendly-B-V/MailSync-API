package nl.thedutchmc.espogmailsync.gsonobjects.in.gmail;

public class MessagesList {

	private SmallMessage[] messages;
	private String nextPageToken;
	private Integer resultSizeEstimate;
	
	/**
	 * @return the messages
	 */
	public SmallMessage[] getMessages() {
		return messages;
	}
	/**
	 * @return the nextPageToken
	 */
	public String getNextPageToken() {
		return nextPageToken;
	}
	/**
	 * @return the resultSizeEstimate
	 */
	public Integer getResultSizeEstimate() {
		return resultSizeEstimate;
	}
	
}
