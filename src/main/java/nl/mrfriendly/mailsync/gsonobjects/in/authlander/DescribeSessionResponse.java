package nl.mrfriendly.mailsync.gsonobjects.in.authlander;

import com.google.gson.annotations.SerializedName;

public class DescribeSessionResponse extends AuthlanderBaseResponse {
	public boolean active;
	
	@SerializedName("user_id")
	public String userId;
}
