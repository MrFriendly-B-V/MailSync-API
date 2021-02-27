package nl.thedutchmc.espogmailsync.gsonobjects.espocrm;

import com.google.gson.annotations.SerializedName;

public class GetAccounts {
	private Integer total;
	
	@SerializedName("list")
	private Account[] accounts;

	/**
	 * @return the total
	 */
	public Integer getTotal() {
		return total;
	}
	
	/**
	 * @return the accounts
	 */
	public Account[] getAccounts() {
		return accounts;
	}
	
	
}
