package com.eportfolium.karuta.security.oauth;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import net.oauth.OAuth;
import net.oauth.OAuthMessage;

public class OAuth2Parse {
	public static String getMessage(HttpServletRequest request, String URL) {
		if (URL == null) {
			URL = request.getRequestURL().toString();
		}
		final var q = URL.indexOf('?');
		if (q >= 0) {
			URL = URL.substring(0, q);
			// The query string parameters will be included in
			// the result from getParameters(request).
		}

		final var method = request.getMethod();
		final var parameters = getParameters(request);

		return "OAuthMessage(" + method + ", " + URL + ", " + parameters + ")";
	}

	@SuppressWarnings("unchecked")
	public static List<OAuth.Parameter> getParameters(HttpServletRequest request) {
		final List<OAuth.Parameter> list = new ArrayList<>();
		for (final var headers = request.getHeaders("Authorization"); headers != null
				&& headers.hasMoreElements();) {
			final var header = headers.nextElement();
			for (final OAuth.Parameter parameter : OAuthMessage.decodeAuthorization(header)) {
				if (!"realm".equalsIgnoreCase(parameter.getKey())) {
					list.add(parameter);
				}
			}
		}
		for (final Object e : request.getParameterMap().entrySet()) {
			final var entry = (Map.Entry<String, String[]>) e;
			final var name = entry.getKey();
			for (final String value : entry.getValue()) {
				list.add(new OAuth.Parameter(name, value));
			}
		}
		return list;
	}

}
