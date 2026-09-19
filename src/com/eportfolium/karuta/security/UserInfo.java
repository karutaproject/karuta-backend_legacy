package com.eportfolium.karuta.security;

final public class UserInfo {
	public final String subUser;
	public final int subId;
	public final String User;
	public final int userId;
	//		int groupId = -1;

	public UserInfo(int userId, String user) {
		this(userId, user, 0, "");
	}

	public UserInfo(int userId, String user, int subId, String subUser) {
		this.userId = userId;
		this.User = user;
		this.subId = subId;
		this.subUser = subUser;
	}

	@Override
	public String toString() {
		return "UserInfo{" +
				"subUser='" +
				subUser +
				'\'' +
				", subId=" +
				subId +
				", User='" +
				User +
				'\'' +
				", userId=" +
				userId +
				'}';
	}
}
