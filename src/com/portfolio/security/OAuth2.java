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

package com.portfolio.security;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.portfolio.data.provider.DataProvider;
import com.portfolio.data.utils.ConfigUtils;
import com.portfolio.data.utils.SqlUtils;
import org.apache.commons.io.IOUtils;
import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jwa.AlgorithmConstraints.ConstraintType;
import org.jose4j.jwk.HttpsJwks;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.jwt.consumer.JwtContext;
import org.jose4j.keys.resolvers.JwksVerificationKeyResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class OAuth2 extends HttpServlet {

    private static final long serialVersionUID = -5793392467087229614L;

    private static final Logger logger = LoggerFactory.getLogger(OAuth2.class);

    ServletConfig sc;
    DataProvider dataProvider;

    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        try {
            ConfigUtils.init(getServletContext());
            dataProvider = SqlUtils.initProvider();
        } catch (Exception e) {
           logger.error("Can't init servlet", e);
           throw new ServletException(e);
        }
    }

    Map<String, String> ParseParameter(String queryParam) {
        if (queryParam == null) return null;

        String[] param = queryParam.split("&");
        Map<String, String> parameters = new HashMap<String, String>();
        for (String s : param) {
            String[] values = s.split("=");
            if (values.length > 1)
                parameters.put(values[0], values[1]);
            else
                parameters.put(values[0], "");
        }

        return parameters;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(true);
        /// Check if code and state is in the parameter
        String query = request.getQueryString();
        Map<String, String> param = ParseParameter(query);
        if (param != null && param.containsKey("code"))    // Might be a return, check state
        {
            String retstate = param.get("state");
            //// First check if state match with current user
            String sesstate = (String) session.getAttribute("state");
            if (retstate.equals(sesstate)) {
                //// Authentication seems good, ask for token to be used in querying info
                final String URLToken = ConfigUtils.getInstance().getRequiredProperty("URLToken");

                String grant_type = "authorization_code";
                final String client_id = ConfigUtils.getInstance().getRequiredProperty("OAUth_client_id");
                /// Need secret to be url encoded
                final String client_secret = URLEncoder.encode(ConfigUtils.getInstance().getRequiredProperty("OAuth_client_secret"), StandardCharsets.UTF_8.toString());
                final String redirect_uri = request.getRequestURL().toString();
                final String code = param.get("code");
                final String authdata = String.format("grant_type=%s&client_id=%s&client_secret=%s&redirect_uri=%s&code=%s",
                        grant_type, client_id, client_secret, redirect_uri, code);

                URL urlConn = new URL(URLToken);
                HttpURLConnection connection = (HttpURLConnection) urlConn.openConnection();
                connection.setDoInput(true);
                connection.setDoOutput(true);
                connection.setUseCaches(false);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

//				System.out.println("Connecting to: "+URLToken+" data: "+authdata);

                /// Receiving login information
                ByteArrayInputStream bais = new ByteArrayInputStream(authdata.getBytes());
                OutputStream outputData = connection.getOutputStream();
                int transferred = IOUtils.copy(bais, outputData);
                if (transferred == authdata.length())
                    logger.debug("Send: Complete");
                else
                    logger.error("Send mismatch: " + transferred + " != " + authdata.length());

                /// Read answer status
                int retcode = connection.getResponseCode();
                String msg = connection.getResponseMessage();

                if (retcode != HttpURLConnection.HTTP_OK) {
                    logger.error("Couldn't log: " + msg);
                } else {
                    /// Fetching data
                    StringWriter swriter = new StringWriter();
                    InputStream inputData = connection.getInputStream();
                    IOUtils.copy(inputData, swriter);
                    inputData.close();
                    /// Can't be bothered to parse json
                    String tokenregexp = "id_token\":\"([^\"]*)";
                    Pattern ptoken = Pattern.compile(tokenregexp);
                    Matcher pmatcher = ptoken.matcher(swriter.toString());
                    String id_token = "";
                    if (pmatcher.find()) {
                        id_token = pmatcher.group(1);
                    }
                    try {
//						System.out.println("Processing =====");
//						System.out.println(id_token);
//						System.out.println("================");

                        //// Decoding
                        JwtConsumer firstPassJwtConsumer = new JwtConsumerBuilder()
                                .setSkipAllValidators()
                                .setDisableRequireSignature()
                                .setSkipSignatureVerification()
                                .build();

                        JwtContext jwtContext = firstPassJwtConsumer.process(id_token);
                        String issuer = jwtContext.getJwtClaims().getIssuer();
                        //// Checking auth server key, use auto-key resolver
                        HttpsJwks keyUrl = new HttpsJwks(ConfigUtils.getInstance().getRequiredProperty("URLKeys"));
                        JwksVerificationKeyResolver verificationKeyResolver = new JwksVerificationKeyResolver(keyUrl.getJsonWebKeys());

                        AlgorithmConstraints algorithmConstraints = new AlgorithmConstraints(ConstraintType.WHITELIST,
                                AlgorithmIdentifiers.RSA_USING_SHA256, AlgorithmIdentifiers.RSA_USING_SHA384);

                        JwtConsumer secondPassJwtConsumer = new JwtConsumerBuilder()
                                .setExpectedIssuer(issuer)
                                .setVerificationKeyResolver(verificationKeyResolver)
                                .setRequireExpirationTime()
                                .setAllowedClockSkewInSeconds(30)
                                .setRequireSubject()
                                .setExpectedAudience(client_id)
                                .setJwsAlgorithmConstraints(algorithmConstraints)
                                .build();

                        secondPassJwtConsumer.processContext(jwtContext);

                        //// Should be able to read relevent data
                        JwtClaims claims = jwtContext.getJwtClaims();
                        String name = (String) claims.getClaimValue("name");
                        String username = (String) claims.getClaimValue("preferred_username");

                        //// Now log with username
                        Connection connexion = SqlUtils.getConnection(session.getServletContext());
                        String userId = dataProvider.getUserId(connexion, username, null);
                        int uid = Integer.parseInt(userId);
                        if (uid == 0) {
                            userId = dataProvider.createUser(connexion, username, null);
                            uid = Integer.parseInt(userId);
                        }
                        session.setAttribute("uid", uid);
                        session.setAttribute("user", username);
                        session.setAttribute("fromoauth", 1);

                        /// Redirect to front-end
                        response.sendRedirect(ConfigUtils.getInstance().getRequiredProperty("ui_redirect_location"));

                        request.getReader().close();
//						System.out.println("data: " + name+" -- "+username);
//						System.out.println("Code: ("+retcode+") msg: "+msg+" data: " + parsedToken);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    logger.debug("Code: (" + retcode + ") msg: " + msg);
                }

                /// Closing connection to auth server
                connection.disconnect();
                response.getWriter().close();

            } else {
                System.out.println("Invalid OAuth2 query: state doesn't match");
            }
        } else    /// Authentication start
        {
            /// Get here redirect to authentication website
            final String URL = ConfigUtils.getInstance().getRequiredProperty("URLAuthorize");
            final String response_type = "code";
            final String client_id = ConfigUtils.getInstance().getRequiredProperty("OAUth_client_id");
            final String redirect_uri = request.getRequestURL().toString();    // This servlet URL
            final String scope = ConfigUtils.getInstance().getRequiredProperty("OAuth_scope");
            final String state = UUID.randomUUID().toString().replaceAll("-", "");    // Generated value
            final String nonce = UUID.randomUUID().toString().replaceAll("-", "");    // Generated value for remote server
            final String urlQuery = String.format("%s?response_type=%s&client_id=%s&redirect_uri=%s&scope=%s&state=%s&nonce=%s",
                    URL, response_type, client_id, redirect_uri, scope, state, nonce);

//			System.out.println("Redirect to: "+urlQuery);

            /// Keep it for return call
            session.setAttribute("state", state);

            /// Send client to URL for authentication
            response.sendRedirect(urlQuery);

            request.getReader().close();
            response.getWriter().close();
        }
    }

}