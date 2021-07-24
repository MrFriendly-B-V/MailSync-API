package nl.mrfriendly.espogmailsync.gsonobjects.in.espocrm;

import com.google.gson.annotations.SerializedName;

public class GetContacts {
	private Integer total;
	
	@SerializedName("list")
	private Contact[] contacts;

	/**
	 * @return the contacts
	 */
	public Contact[] getContacts() {
		return contacts;
	}

	/**
	 * @return the total
	 */
	public Integer getTotal() {
		return total;
	}
	
}
