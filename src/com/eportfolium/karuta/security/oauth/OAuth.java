/*
 * Copyright 2007 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.eportfolium.karuta.security.oauth;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author John Kristian
 */
public class OAuth {

	/** A name/value pair. */
	public static class Parameter implements Map.Entry<String, String> {

		private final String key;

		private String value;

		public Parameter(String key, String value) {
			this.key = key;
			this.value = value;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if ((obj == null) || (getClass() != obj.getClass())) {
				return false;
			}
			final var that = (Parameter) obj;
			if (key == null) {
				if (that.key != null) {
					return false;
				}
			} else if (!key.equals(that.key)) {
				return false;
			}
			if (value == null) {
				if (that.value != null) {
					return false;
				}
			} else if (!value.equals(that.value)) {
				return false;
			}
			return true;
		}

		@Override
		public String getKey() {
			return key;
		}

		@Override
		public String getValue() {
			return value;
		}

		@Override
		public int hashCode() {
			final var prime = 31;
			var result = 1;
			result = prime * result + ((key == null) ? 0 : key.hashCode());
			result = prime * result + ((value == null) ? 0 : value.hashCode());
			return result;
		}

		@Override
		public String setValue(String value) {
			try {
				return this.value;
			} finally {
				this.value = value;
			}
		}

		@Override
		public String toString() {
			return percentEncode(getKey()) + '=' + percentEncode(getValue());
		}
	}

	/**
	 * Strings used for <a href="http://wiki.oauth.net/ProblemReporting">problem
	 * reporting</a>.
	 */
	public static class Problems {
		public static final String VERSION_REJECTED = "version_rejected";
		public static final String PARAMETER_ABSENT = "parameter_absent";
		public static final String PARAMETER_REJECTED = "parameter_rejected";
		public static final String TIMESTAMP_REFUSED = "timestamp_refused";
		public static final String NONCE_USED = "nonce_used";
		public static final String SIGNATURE_METHOD_REJECTED = "signature_method_rejected";
		public static final String SIGNATURE_INVALID = "signature_invalid";
		public static final String CONSUMER_KEY_UNKNOWN = "consumer_key_unknown";
		public static final String CONSUMER_KEY_REJECTED = "consumer_key_rejected";
		public static final String CONSUMER_KEY_REFUSED = "consumer_key_refused";
		public static final String TOKEN_USED = "token_used";
		public static final String TOKEN_EXPIRED = "token_expired";
		public static final String TOKEN_REVOKED = "token_revoked";
		public static final String TOKEN_REJECTED = "token_rejected";
		public static final String ADDITIONAL_AUTHORIZATION_REQUIRED = "additional_authorization_required";
		public static final String PERMISSION_UNKNOWN = "permission_unknown";
		public static final String PERMISSION_DENIED = "permission_denied";
		public static final String USER_REFUSED = "user_refused";

		public static final String OAUTH_ACCEPTABLE_VERSIONS = "oauth_acceptable_versions";
		public static final String OAUTH_ACCEPTABLE_TIMESTAMPS = "oauth_acceptable_timestamps";
		public static final String OAUTH_PARAMETERS_ABSENT = "oauth_parameters_absent";
		public static final String OAUTH_PARAMETERS_REJECTED = "oauth_parameters_rejected";
		public static final String OAUTH_PROBLEM_ADVICE = "oauth_problem_advice";

		/**
		 * A map from an
		 * <a href="http://wiki.oauth.net/ProblemReporting">oauth_problem</a> value to
		 * the appropriate HTTP response code.
		 */
		public static final Map<String, Integer> TO_HTTP_CODE = mapToHttpCode();

		private static Map<String, Integer> mapToHttpCode() {
			final var badRequest = new Integer(400);
			final var unauthorized = new Integer(401);
			final var serviceUnavailable = new Integer(503);
			final Map<String, Integer> map = new HashMap<>();

			map.put(Problems.VERSION_REJECTED, badRequest);
			map.put(Problems.PARAMETER_ABSENT, badRequest);
			map.put(Problems.PARAMETER_REJECTED, badRequest);
			map.put(Problems.TIMESTAMP_REFUSED, badRequest);
			map.put(Problems.SIGNATURE_METHOD_REJECTED, badRequest);

			map.put(Problems.NONCE_USED, unauthorized);
			map.put(Problems.TOKEN_USED, unauthorized);
			map.put(Problems.TOKEN_EXPIRED, unauthorized);
			map.put(Problems.TOKEN_REVOKED, unauthorized);
			map.put(Problems.TOKEN_REJECTED, unauthorized);
			map.put("token_not_authorized", unauthorized);
			map.put(Problems.SIGNATURE_INVALID, unauthorized);
			map.put(Problems.CONSUMER_KEY_UNKNOWN, unauthorized);
			map.put(Problems.CONSUMER_KEY_REJECTED, unauthorized);
			map.put(Problems.ADDITIONAL_AUTHORIZATION_REQUIRED, unauthorized);
			map.put(Problems.PERMISSION_UNKNOWN, unauthorized);
			map.put(Problems.PERMISSION_DENIED, unauthorized);

			map.put(Problems.USER_REFUSED, serviceUnavailable);
			map.put(Problems.CONSUMER_KEY_REFUSED, serviceUnavailable);
			return Collections.unmodifiableMap(map);
		}

	}

	public static final String VERSION_1_0 = "1.0";

	/** The encoding used to represent characters as bytes. */
	public static final String ENCODING = "UTF-8";
	/** The MIME type for a sequence of OAuth parameters. */
	public static final String FORM_ENCODED = "application/x-www-form-urlencoded";
	public static final String OAUTH_CONSUMER_KEY = "oauth_consumer_key";
	public static final String OAUTH_TOKEN = "oauth_token";
	public static final String OAUTH_TOKEN_SECRET = "oauth_token_secret";
	public static final String OAUTH_SIGNATURE_METHOD = "oauth_signature_method";
	public static final String OAUTH_SIGNATURE = "oauth_signature";
	public static final String OAUTH_TIMESTAMP = "oauth_timestamp";
	public static final String OAUTH_NONCE = "oauth_nonce";

	public static final String OAUTH_VERSION = "oauth_version";
	public static final String OAUTH_CALLBACK = "oauth_callback";
	public static final String HMAC_SHA1 = "HMAC-SHA1";

	public static final String HMAC_SHA256 = "HMAC-SHA256";

	public static final String RSA_SHA1 = "RSA-SHA1";

	public static String addParameters(String url, Iterable<? extends Map.Entry<String, String>> parameters)
			throws IOException {
		final var form = formEncode(parameters);
		if (form == null || form.length() <= 0) {
			return url;
		} else {
			return url + ((url.indexOf("?") < 0) ? '?' : '&') + form;
		}
	}

	/**
	 * Construct a URL like the given one, but with the given parameters added to
	 * its query string.
	 */
	public static String addParameters(String url, String... parameters) throws IOException {
		return addParameters(url, newList(parameters));
	}

	/** Parse a form-urlencoded document. */
	public static List<Parameter> decodeForm(String form) {
		final List<Parameter> list = new ArrayList<>();
		if (!isEmpty(form)) {
			for (final String nvp : form.split("\\&")) {
				final var equals = nvp.indexOf('=');
				String name;
				String value;
				if (equals < 0) {
					name = decodePercent(nvp);
					value = null;
				} else {
					name = decodePercent(nvp.substring(0, equals));
					value = decodePercent(nvp.substring(equals + 1));
				}
				list.add(new Parameter(name, value));
			}
		}
		return list;
	}

	public static String decodePercent(String s) {
		try {
			return URLDecoder.decode(s, ENCODING);
			// This implements http://oauth.pbwiki.com/FlexibleDecoding
		} catch (final java.io.UnsupportedEncodingException wow) {
			throw new RuntimeException(wow.getMessage(), wow);
		}
	}

	/**
	 * Construct a form-urlencoded document containing the given sequence of
	 * name/value pairs. Use OAuth percent encoding (not exactly the encoding
	 * mandated by HTTP).
	 */
	@SuppressWarnings("rawtypes")
	public static String formEncode(Iterable<? extends Map.Entry> parameters) throws IOException {
		final var b = new ByteArrayOutputStream();
		formEncode(parameters, b);
		return new String(b.toByteArray());
	}

	/**
	 * Write a form-urlencoded document into the given stream, containing the given
	 * sequence of name/value pairs.
	 */
	@SuppressWarnings("rawtypes")
	public static void formEncode(Iterable<? extends Map.Entry> parameters, OutputStream into) throws IOException {
		if (parameters != null) {
			var first = true;
			for (final Map.Entry parameter : parameters) {
				if (first) {
					first = false;
				} else {
					into.write('&');
				}
				into.write(percentEncode(toString(parameter.getKey())).getBytes());
				into.write('=');
				into.write(percentEncode(toString(parameter.getValue())).getBytes());
			}
		}
	}

	public static boolean isEmpty(String str) {
		return (str == null) || (str.length() == 0);
	}

	/** Return true if the given Content-Type header means FORM_ENCODED. */
	public static boolean isFormEncoded(String contentType) {
		if (contentType == null) {
			return false;
		}
		final var semi = contentType.indexOf(";");
		if (semi >= 0) {
			contentType = contentType.substring(0, semi);
		}
		return FORM_ENCODED.equalsIgnoreCase(contentType.trim());
	}

	/** Construct a list of Parameters from name, value, name, value... */
	public static List<Parameter> newList(String... parameters) {
		final List<Parameter> list = new ArrayList<>(parameters.length / 2);
		for (var p = 0; p + 1 < parameters.length; p += 2) {
			list.add(new Parameter(parameters[p], parameters[p + 1]));
		}
		return list;
	}

	/**
	 * Construct a Map containing a copy of the given parameters. If several
	 * parameters have the same name, the Map will contain the first value, only.
	 */
	@SuppressWarnings("rawtypes")
	public static Map<String, String> newMap(Iterable<? extends Map.Entry> from) {
		final Map<String, String> map = new HashMap<>();
		if (from != null) {
			for (final Map.Entry f : from) {
				final var key = toString(f.getKey());
				if (!map.containsKey(key)) {
					map.put(key, toString(f.getValue()));
				}
			}
		}
		return map;
	}

	/**
	 * {@literal Construct a &-separated list of the given values, percentEncoded.}
	 */
	public static String percentEncode(Iterable<?> values) {
		final var p = new StringBuilder();
		for (final Object v : values) {
			if (p.length() > 0) {
				p.append("&");
			}
			p.append(OAuth.percentEncode(toString(v)));
		}
		return p.toString();
	}

	public static String percentEncode(String s) {
		if (s == null) {
			return "";
		}
		try {
			return URLEncoder.encode(s, ENCODING)
					// OAuth encodes some characters differently:
					.replace("+", "%20").replace("*", "%2A").replace("%7E", "~");
			// This could be done faster with more hand-crafted code.
		} catch (final UnsupportedEncodingException wow) {
			throw new RuntimeException(wow.getMessage(), wow);
		}
	}

	private static final String toString(Object from) {
		return (from == null) ? null : from.toString();
	}
}
