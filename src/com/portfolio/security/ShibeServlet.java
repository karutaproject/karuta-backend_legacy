/* =======================================================
	Copyright 2018 - ePortfolium - Licensed under the
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

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.portfolio.data.provider.DataProvider;
import com.portfolio.data.utils.ConfigUtils;
import com.portfolio.data.utils.SqlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class ShibeServlet extends HttpServlet {

    private static final long serialVersionUID = -5793392467087229614L;

    private final static Logger logger = LoggerFactory.getLogger(ShibeServlet.class);
    public static final String SHIB_AUTH = "shib_auth";
    public static final String SHIB_FIRSTNAME = "shib_firstname";
    public static final String SHIB_LASTNAME = "shib_lastname";
    public static final String SHIB_FULLNAME = "shib_fullname";
    public static final String SHIB_EMAIL = "shib_email";
    public static final String SHIB_REMOTE_USER = "shib_remote_user";
    public static final String SHIB_CREATE_USER = "shib_create_user";

    private static String principalRequestHeader = "REMOTE_USER";
    private static String firstNameRequestHeader;
    private static String lastNameRequestHeader;
    private static String fullNameRequestHeader;
    private static String emailRequestHeader;
    private static boolean createUserNotExisting = true;
    private static boolean useFullName = false;

    DataProvider dataProvider;

    @Override
    public void init() throws ServletException {
        try {
            ConfigUtils.init(getServletContext());
            dataProvider = SqlUtils.initProvider();

            if (Boolean.parseBoolean(ConfigUtils.getInstance().getProperty(SHIB_AUTH))) {
                logger.info("the Shibboleth Auth is Activated from the configuration.");
                // init header attributes to use
                firstNameRequestHeader = ConfigUtils.getInstance().getProperty(SHIB_FIRSTNAME);
                lastNameRequestHeader = ConfigUtils.getInstance().getProperty(SHIB_LASTNAME);
                fullNameRequestHeader = ConfigUtils.getInstance().getProperty(SHIB_FULLNAME);
                emailRequestHeader = ConfigUtils.getInstance().getRequiredProperty(SHIB_EMAIL);
                final String principalHeaderName = ConfigUtils.getInstance().getProperty(SHIB_REMOTE_USER);
                if (principalHeaderName != null && !principalHeaderName.trim().isEmpty()) {
                    principalRequestHeader = principalHeaderName;
                }
                final String createUserProperty = ConfigUtils.getInstance().getProperty(SHIB_CREATE_USER);
                if (createUserProperty != null) {
                    createUserNotExisting = Boolean.parseBoolean(createUserProperty);
                }

                if (createUserNotExisting) {
                    logger.warn("User creation from shibboleth auth is activated");
                    final boolean fnAndLnAreSet = firstNameRequestHeader != null && !firstNameRequestHeader.trim().isEmpty()
                            && lastNameRequestHeader != null && !lastNameRequestHeader.trim().isEmpty();

                    if (!fnAndLnAreSet && (fullNameRequestHeader == null || fullNameRequestHeader.trim().isEmpty())) {
                        throw new IllegalArgumentException("The username can't be retrieved, no shibboleth attribute initialized !");
                    } else if (!fnAndLnAreSet){
                        useFullName = true;
                    }
                }

            }
        } catch (Exception e) {
            logger.error("Can't init servlet", e);
			throw new ServletException(e);
        }
    }


    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(true);
        final String remoteUser = getRemoteUser(request);

        Connection connexion = null;
        int uid = 0;
        if (remoteUser != null) {
            try {
                connexion = SqlUtils.getConnection();
                String userId = dataProvider.getUserId(connexion, remoteUser, null);
                uid = Integer.parseInt(userId);

                if (uid == 0 && createUserNotExisting) {
                    logger.info("[SHIBESERV] Creating account for {}", remoteUser);
                    final String mail = getShibAttribute(request, emailRequestHeader);
                    if (mail == null || mail.isEmpty()) {
                        logger.error("L'adresse mail '{}' n'a pas de valeur valide pour l'utilisateur avec l'identifiant '{}'", mail, remoteUser);
                        throw new IllegalAccessException("Les informations utilisateurs nécessaires n'ont pas été transmises");
                    }
                    userId = dataProvider.createUser(connexion, remoteUser, mail);
                    uid = Integer.parseInt(userId);

                    /// Update values
                    if (!useFullName) {
                        final String fn = getShibAttribute(request, firstNameRequestHeader);
                        final String ln = getShibAttribute(request, lastNameRequestHeader);

                        /// Regular function need old password to update
                        /// But external account generate password unreachable with regular method
                        if (fn != null && !fn.isEmpty() && ln != null && !ln.isEmpty()) {
                            dataProvider.putInfUserInternal(connexion, uid, uid, fn, ln, mail);
                        } else {
                            logger.error("Le prénom '{}' et/ou le nom '{}' n'ont pas de valeur valide pour l'utilisateur avec l'identifiant '{}'", ln, fn, remoteUser);
                            throw new IllegalAccessException("Les informations utilisateurs nécessaires n'ont pas été transmises");
                        }
                    } else {
                        final String cn = getShibAttribute(request, fullNameRequestHeader);
                        if (cn == null || cn.isEmpty()) {
                            logger.error("Le nom d'affichage '{}' n'a pas de valeur valide pour l'utilisateur avec l'identifiant '{}'", cn, remoteUser);
                            throw new IllegalAccessException("Les informations utilisateurs nécessaires n'ont pas été transmises");
                        }
                        final String[] namefrag = cn.split(" ");
                        if (namefrag.length > 1) {
                            dataProvider.putInfUserInternal(connexion, uid, uid, namefrag[1], namefrag[0], mail);
                        } else {
                            dataProvider.putInfUserInternal(connexion, uid, uid, namefrag[0], namefrag[0], mail);
                        }
                    }
                } else {
                    logger.warn("User '{}' tried to connect but wasn't found into database.", remoteUser);
                }
                session.setAttribute("uid", uid);
                session.setAttribute("user", remoteUser);
                session.setAttribute("fromshibe", 1);
                logger.debug("User '{}' successfully authentified with linked DB account id '{}'", remoteUser, uid);
            } catch (Exception e) {
                logger.error("Intercepted error", e);
                //TODO something missing
            } finally {
                if (connexion != null) {
                    try {
                        connexion.close();
                    } catch (SQLException e) {
                        logger.error("SQL request error", e);
                    }
                }
            }
        }

        if (uid > 0) {
            String location = ConfigUtils.getInstance().getRequiredProperty("ui_redirect_location");
            logger.info("Location: " + location);
            response.sendRedirect(location);
            response.getWriter().close();
        } else {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            PrintWriter writer = response.getWriter();
            writer.write("forbidden");
            writer.close();
        }
        request.getReader().close();
    }

    private String getRemoteUser(final HttpServletRequest request){
        String userId = request.getRemoteUser();

        if (userId != null) {
            return userId;
        }

        userId = request.getHeader(principalRequestHeader);
        return userId;
    }

    private String getShibAttribute(final HttpServletRequest request, final String attributeName){
        String userAttr = (String) request.getAttribute(attributeName);

        if (userAttr != null) {
            return userAttr;
        }

        userAttr = request.getHeader(attributeName);
        return userAttr;
    }

}