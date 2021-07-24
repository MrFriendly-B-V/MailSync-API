package nl.mrfriendly.mailsync.gsonobjects.in.authlander;

import com.google.gson.annotations.SerializedName;

public class GetTokenResponse extends AuthlanderBaseResponse {
	@SerializedName("access_token")
	public String accessToken;
	public long expiry;
	public boolean active;
}
