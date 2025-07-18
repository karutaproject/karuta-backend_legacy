/* =======================================================
	Copyright 2019 - ePortfolium - Licensed under the
	Educational Community License, Version 2.0 (the "License"); you may
	not use this file except in compliance with the License. You may
	obtain a copy of the License at

	http://www.osedu.org/licenses/ECL-2.0

	Unless required by applicable law or agreed to in writing,
	software distributed under the License is distributed on an "AS IS"
	BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
	or implied. See the License for the specific language governing
	permissions and limitations under the License.
   ======================================================= */

package com.eportfolium.karuta.security;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jwa.AlgorithmConstraints.ConstraintType;
import org.jose4j.jwk.HttpsJwks;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.keys.resolvers.JwksVerificationKeyResolver;

import com.eportfolium.karuta.data.provider.DataProvider;
import com.eportfolium.karuta.data.utils.ConfigUtils;
import com.eportfolium.karuta.data.utils.SqlUtils;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 *
 */
public class OAuth2 extends HttpServlet {

	public static final Pattern PATTERN_TOKEN = Pattern.compile("id_token\":\"([^\"]*)");
	private static final long serialVersionUID = -5793392467087229614L;

	private static final Logger logger = LogManager.getLogger(OAuth2.class);

	ServletConfig sc;
	DataProvider dataProvider;

	private String defaultRedirectLocation;
	private String URLToken;
	private String client_id;
	private String client_secret;
	private String scope;
	private String URLKeys;
	private String URLAuthorize;

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		try {
			ConfigUtils.init(getServletContext());
			dataProvider = SqlUtils.initProvider();
			defaultRedirectLocation = ConfigUtils.getInstance().getRequiredProperty("ui_redirect_location");
		} catch (final Exception e) {
			logger.error("Can't init servlet", e);
			throw new ServletException(e);
		}
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
		final var session = request.getSession(true);
		/// Check if code and state is in the parameter
		final var query = request.getQueryString();
		final var param = ParseParameter(query);
		if (param != null && param.containsKey("code")) // Might be a return, check state
		{
			final var retstate = param.get("state");
			//// First check if state match with current user
			final var sesstate = (String) session.getAttribute("state");
			if (retstate.equals(sesstate)) {
				//// Authentication seems good, ask for token to be used in querying info
				lazyInit();
				final var grant_type = "authorization_code";
				final var redirect_uri = request.getRequestURL().toString();
				final var code = param.get("code");
				final var authdata = String.format(
						"grant_type=%s&client_id=%s&client_secret=%s&redirect_uri=%s&code=%s", grant_type, client_id,
						client_secret, redirect_uri, code);

				final var urlConn = new URL(URLToken);
				final var connection = (HttpURLConnection) urlConn.openConnection();
				connection.setDoInput(true);
				connection.setDoOutput(true);
				connection.setUseCaches(false);
				connection.setInstanceFollowRedirects(false);
				connection.setRequestMethod("POST");
				connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

				logger.debug("Connecting to: {} data: {}", URLToken, authdata);

				/// Receiving login information
				final var bais = new ByteArrayInputStream(authdata.getBytes());
				final var outputData = connection.getOutputStream();
				final var transferred = IOUtils.copy(bais, outputData);
				if (transferred == authdata.length()) {
					logger.debug("Send: Complete");
				} else {
					logger.error("Send mismatch: " + transferred + " != " + authdata.length());
				}

				/// Read answer status
				final var retcode = connection.getResponseCode();
				final var msg = connection.getResponseMessage();

				if (retcode != HttpURLConnection.HTTP_OK) {
					logger.error("Couldn't log: " + msg);
				} else {
					/// Fetching data
					final var swriter = new StringWriter();
					final var inputData = connection.getInputStream();
					IOUtils.copy(inputData, swriter, Charset.defaultCharset());
					inputData.close();
					/// Can't be bothered to parse json
					final var pmatcher = PATTERN_TOKEN.matcher(swriter.toString());
					var id_token = "";
					if (pmatcher.find()) {
						id_token = pmatcher.group(1);
					}
					try {
						logger.debug("Processing =====\n{}\n================", id_token);
						//// Decoding
						final var firstPassJwtConsumer = new JwtConsumerBuilder().setSkipAllValidators()
								.setDisableRequireSignature().setSkipSignatureVerification().build();

						final var jwtContext = firstPassJwtConsumer.process(id_token);
						final var issuer = jwtContext.getJwtClaims().getIssuer();
						//// Checking auth server key, use auto-key resolver
						final var keyUrl = new HttpsJwks(URLKeys);
						final var verificationKeyResolver = new JwksVerificationKeyResolver(keyUrl.getJsonWebKeys());

						final var algorithmConstraints = new AlgorithmConstraints(ConstraintType.WHITELIST,
								AlgorithmIdentifiers.RSA_USING_SHA256, AlgorithmIdentifiers.RSA_USING_SHA384);

						final var secondPassJwtConsumer = new JwtConsumerBuilder().setExpectedIssuer(issuer)
								.setVerificationKeyResolver(verificationKeyResolver).setRequireExpirationTime()
								.setAllowedClockSkewInSeconds(30).setRequireSubject().setExpectedAudience(client_id)
								.setJwsAlgorithmConstraints(algorithmConstraints).build();

						secondPassJwtConsumer.processContext(jwtContext);

						//// Should be able to read relevent data
						final var claims = jwtContext.getJwtClaims();
						final var name = (String) claims.getClaimValue("name");
						final var username = (String) claims.getClaimValue("preferred_username");

						//// Now log with username
						Connection connexion = null;
						try {
							connexion = SqlUtils.getConnection();
							var userId = dataProvider.getUserId(connexion, username, null);
							var uid = Integer.parseInt(userId);
							if (uid == 0) {
								userId = dataProvider.createUser(connexion, username, null);
								uid = Integer.parseInt(userId);
							}
							session.setAttribute("uid", uid);
							session.setAttribute("user", username);
							session.setAttribute("fromoauth", 1);
						} catch (final Exception e) {
							logger.error("Managed error", e);
						} finally {
							try {
								if (connexion != null) {
									connexion.close();
								}
							} catch (final SQLException e) {
								logger.error("Managed error", e);
							}
						}

						/// Redirect to front-end
						response.sendRedirect(defaultRedirectLocation);

						request.getReader().close();
						logger.debug("data: {} -- {}, Code ({}) msg {}", name, username, retcode, msg);
					} catch (final Exception e) {
						e.printStackTrace();
					}

					logger.debug("Code: (" + retcode + ") msg: " + msg);
				}

				/// Closing connection to auth server
				connection.disconnect();
				response.getWriter().close();

			} else {
				logger.warn("Invalid OAuth2 query: state doesn't match '{}' vs '{}'", retstate, sesstate);
			}
		} else /// Authentication start
		{
			/// Get here redirect to authentication website
			lazyInit();
			final var response_type = "code";
			final var redirect_uri = request.getRequestURL().toString(); // This servlet URL
			final var state = UUID.randomUUID().toString().replaceAll("-", ""); // Generated value
			final var nonce = UUID.randomUUID().toString().replaceAll("-", ""); // Generated value for remote server
			final var urlQuery = String.format(
					"%s?response_type=%s&client_id=%s&redirect_uri=%s&scope=%s&state=%s&nonce=%s", URLAuthorize,
					response_type, client_id, redirect_uri, scope, state, nonce);

			logger.debug("Redirect to: {}", urlQuery);

			/// Keep it for return call
			session.setAttribute("state", state);

			/// Send client to URL for authentication
			response.sendRedirect(urlQuery);

			request.getReader().close();
			response.getWriter().close();
		}
	}

	private void lazyInit() throws UnsupportedEncodingException {
		if (URLToken == null || client_id == null) {
			URLToken = ConfigUtils.getInstance().getRequiredProperty("URLToken");
			client_id = ConfigUtils.getInstance().getRequiredProperty("OAUth_client_id");
			/// Need secret to be url encoded
			client_secret = URLEncoder.encode(ConfigUtils.getInstance().getRequiredProperty("OAuth_client_secret"),
					StandardCharsets.UTF_8.toString());
			scope = ConfigUtils.getInstance().getRequiredProperty("OAuth_scope");
			URLKeys = ConfigUtils.getInstance().getRequiredProperty("URLKeys");
			URLAuthorize = ConfigUtils.getInstance().getRequiredProperty("URLAuthorize");
		}
	}

	private Map<String, String> ParseParameter(String queryParam) {
		if (queryParam == null) {
			return null;
		}

		final var param = queryParam.split("&");
		final Map<String, String> parameters = new HashMap<>();
		for (final String s : param) {
			final var values = s.split("=");
			if (values.length > 1) {
				parameters.put(values[0], values[1]);
			} else {
				parameters.put(values[0], "");
			}
		}

		return parameters;
	}

}