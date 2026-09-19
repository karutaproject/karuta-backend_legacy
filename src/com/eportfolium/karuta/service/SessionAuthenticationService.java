package com.eportfolium.karuta.service;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.eportfolium.karuta.rest.RestWebApplicationException;
import com.eportfolium.karuta.security.UserInfo;
import com.eportfolium.karuta.spring.AuthenticationService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response.Status;

@Service
public class SessionAuthenticationService implements AuthenticationService {

	private static final Logger logger = LoggerFactory.getLogger(SessionAuthenticationService.class);

	public SessionAuthenticationService() {
	}

	/// Should replace checkCredential at some point
	@Override
	public Optional<UserInfo> getAuthenticateUser(HttpServletRequest request) {
		final var session = request.getSession(false);
		if (session == null) {
			// Non valid userid
			logger.error("Request {} on '{}' unauthorized for a not logged in user", request.getMethod(),
					request.getRequestURI());
			throw new RestWebApplicationException(Status.UNAUTHORIZED, "User not logged in");
		}

		final var userid = (Integer) session.getAttribute("uid");
		if (userid == null) {
			// Non valid userid
			logger.error("Request {} on '{}' unauthorized for a not logged in user", request.getMethod(),
					request.getRequestURI());
			throw new RestWebApplicationException(Status.UNAUTHORIZED, "User not logged in");
		}
		final var user = (String) session.getAttribute("user");
		//		val = (Integer) session.getAttribute("gid");
		//		if( val != null )
		//			ui.groupId = val;
		final var subid = (Integer) session.getAttribute("subuid");

		UserInfo ui;
		if (subid != null) {
			final var subUser = (String) session.getAttribute("subuser");
			ui = new UserInfo(userid, user, subid, subUser);
		} else {
			ui = new UserInfo(userid, user);
		}

		return Optional.of(ui);
	}
}
