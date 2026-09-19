package com.eportfolium.karuta.spring;

import java.util.Optional;

import com.eportfolium.karuta.security.UserInfo;

import jakarta.servlet.http.HttpServletRequest;

public interface AuthenticationService {
	Optional<UserInfo> getAuthenticateUser(HttpServletRequest request);
}
