package nl.mrfriendly.mailsync.gsonobjects.in.authlander;

import com.google.gson.annotations.SerializedName;

public class UserScopesResponse extends AuthlanderBaseResponse {

	public String[] scopes;
	
	@SerializedName("is_active")
	public boolean isActive;
}
