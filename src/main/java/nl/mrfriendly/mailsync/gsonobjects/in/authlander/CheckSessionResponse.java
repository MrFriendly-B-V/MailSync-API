package nl.mrfriendly.mailsync.gsonobjects.in.authlander;

import com.google.gson.annotations.SerializedName;

public class CheckSessionResponse extends AuthlanderBaseResponse {
	@SerializedName("session_valid")
	public boolean sessionValid;
	public boolean active;
}
