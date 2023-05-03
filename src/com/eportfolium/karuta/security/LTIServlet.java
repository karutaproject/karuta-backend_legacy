/* =======================================================
	Copyright 2014 - ePortfolium - Licensed under the
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

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.eportfolium.karuta.data.provider.DataProvider;
import com.eportfolium.karuta.data.utils.ConfigUtils;
import com.eportfolium.karuta.data.utils.SqlUtils;
import net.oauth.OAuthAccessor;
import net.oauth.OAuthConsumer;
import net.oauth.OAuthException;
import net.oauth.OAuthMessage;
import net.oauth.OAuthValidator;
import net.oauth.SimpleOAuthValidator;
import net.oauth.server.OAuthServlet;
import net.oauth.signature.OAuthSignatureMethod;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.sakaiproject.basiclti.util.BlowFish;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tsugi.basiclti.BasicLTIConstants;
import org.tsugi.basiclti.BasicLTIUtil;

/**
 * Class supporting lti integration
 * Used basiclti-util-2.1.0 (from sakai 2.9.2) for the oauth dependencies
 *
 * @author chmaurer
 */
public class LTIServlet extends HttpServlet {

    private static final long serialVersionUID = -5793392467087229614L;

    private static final String OAUTH_MESSAGE = "oauth_message";
    final Logger logger = LoggerFactory.getLogger(LTIServlet.class);

    ServletConfig sc;
    DataProvider dataProvider;

    String cookie = "wad";
    //boolean log = true;
    //boolean trace = true;

    private boolean useEmail;
    private boolean ltiCreateUser;
    private String ltiUserName;
    private String ltiRedirectLocation;

    @Override
    public void init() throws ServletException {
        sc = getServletConfig();
        ServletContext application = getServletConfig().getServletContext();
        try {
            ConfigUtils.init(sc.getServletContext());
//	    loadRoleMapAttributes(application);
            useEmail = BooleanUtils.toBoolean(ConfigUtils.getInstance().getProperty("lti_email_as_username"));
            ltiCreateUser = BooleanUtils.toBoolean(ConfigUtils.getInstance().getProperty("lti_create_user"));
            ltiUserName = ConfigUtils.getInstance().getProperty("lti_userid");
            ltiRedirectLocation = ConfigUtils.getInstance().getProperty("lti_redirect_location");
        } catch (Exception e) {
            logger.error("Can't init servlet", e);
			throw new ServletException(e);
        }
    }

    /**
     * See if we want to have tracing enabled based off a config value.
     * To enable trace output, set the following: <pre>LTIServlet.trace=true</pre>
     * Anything else is false.
     *
     * @param application
     * @return
     */
    private boolean isTrace(ServletContext application) {
        String traceStr = (String) application.getAttribute("LTIServlet.trace");
        return Boolean.parseBoolean(traceStr);

    }


    /**
     * Initialize the DB connection
     *
     * @throws Exception
     */
    private void initDB() throws Exception {
//		Connection connexion = null;			// hors du try pour fermer dans finally

        //============= init servers ===============================
//	    this.sc = getServletConfig();
        if (dataProvider == null) {
            final String dataProviderName = ConfigUtils.getInstance().getRequiredProperty("dataProviderClass");
            dataProvider = (DataProvider) Class.forName(dataProviderName).getConstructor().newInstance();
        }
    }

    /**
     * Clean up the DB connection
     *
     * @param connexion
     */
    private void destroyDB(Connection connexion) {
        if (connexion != null) {
            try {
                connexion.close();
            } catch (Exception e) {
                logger.error("Erreur dans User-doGet", e);
                //TODO missing management ?
            }
        }
    }

    private Map<String, String> processLoginCookie(HttpServletRequest request) {
        Map<String, String> processedCookies = new HashMap<>();
        Cookie[] cookies = request.getCookies();
        if (cookies != null)
            for (Cookie c : cookies) {
                String name = c.getName();
                String val = c.getValue();
                processedCookies.put(name, val);
            }

        return processedCookies;
    }

    /**
     * Process the request parameters into a map
     *
     * @param request
     * @param outTrace
     * @return
     * @throws IOException
     */
    private Map<String, String> processRequest(HttpServletRequest request, StringBuffer outTrace) throws IOException {
        Map<String, String> payload = new HashMap<String, String>();
        for (Enumeration<String> e = request.getParameterNames(); e.hasMoreElements(); ) {
            String key = e.nextElement();
            String value = request.getParameter(key);
            payload.put(key, value);
            logger.trace("\nkey: " + key + "(" + value + ")");
        }
        return payload;
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doPost(request, response);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doPost(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding(StandardCharsets.UTF_8.toString());

        StringBuffer outTrace = new StringBuffer();
        String logFName = null;
        Connection connexion = null;            // hors du try pour fermer dans finally
        HttpSession session = request.getSession(true);
        String ppath = session.getServletContext().getRealPath("/");
        String outsideDir = ppath.substring(0, ppath.lastIndexOf("/")) + "_files/";

        //super.doPost(request, response);
        logFName = outsideDir + "logs/logLTI.txt";
        outTrace.append("\nBegin");

        Map<String, String> cookies = processLoginCookie(request);

        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put(OAUTH_MESSAGE, OAuthServlet.getMessage(request, null));
        Map<String, String> payloadStr = processRequest(request, outTrace);
        payload.putAll(payloadStr);

        ServletContext application = getServletConfig().getServletContext();

        try {
//	        LoadProperties(application);
            initDB();

//			wadbackend.WadUtilities.setApplicationAttributes(application, session);

            try {
                //ensureAuthz(request, application, outTrace);
                validate(payload, application, outTrace);
            } catch (LTIException e) {
                //outTrace.append("\n" + e.getMessage());
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, e.getLocalizedMessage());
                return;
            }

//			loadRoleMapAttributes(application);

            connexion = SqlUtils.getConnection();
            String userId = getOrCreateUser(payload, cookies, connexion, outTrace);

            if (!"0".equals(userId)) // FIXME: Need more checking and/or change uid String to int
            {
                session.setAttribute("uid", Integer.parseInt(userId));

                String userName = "";
                if (useEmail) {
                    userName = (String) payload.get(BasicLTIConstants.LIS_PERSON_CONTACT_EMAIL_PRIMARY);
                } else {
                    if (ltiUserName != null && !ltiUserName.isEmpty())
                        userName = (String) payload.get(ltiUserName);
                    else
                        userName = (String) payload.get(BasicLTIConstants.LIS_PERSON_SOURCEDID);
                }

                session.setAttribute("uid", Integer.parseInt(userId));
                session.setAttribute("user", userName);
                session.setAttribute("username", userName);
                session.setAttribute("useridentifier", userName);
            } else {
                String server = "https://" + request.getServerName() + request.getContextPath();
                session.invalidate();
                response.sendRedirect(server + "/lti-403.html");
                return;
            }

            //============Group Processing======================
            String contextLabel = (String) payload.get(BasicLTIConstants.CONTEXT_LABEL);
            String ltiRole = (String) payload.get(BasicLTIConstants.ROLES);
            String contextRole = (String) payload.get("ext_sakai_role");
            String inputRole = contextRole == null ? ltiRole : contextRole;
//			outTrace.append("\nLTI Role: " + ltiRole);
//			outTrace.append("\nContext Role: " + contextRole);
//			outTrace.append("\nInput Role: " + inputRole);
//			String siteGroupId = getOrCreateGroup(connexion, contextLabel, "topUser", outTrace);

            /* Not used code
            StringBuilder siteGroup = new StringBuilder();
            siteGroup.append(contextLabel);
            siteGroup.append("-");
            siteGroup.append(inputRole);
            */
//			String wadRole = roleMapper(application, inputRole, outTrace);
//			String siteRoleGroupId = getOrCreateGroup(connexion, siteGroup.toString(), wadRole, outTrace);

            /// We can create a group and put the user in it, but there's no link to rights
            //See what groups the user is in
            /// isUserMemberOfGroup
			/*
			boolean isInSiteGroup = dataProvider.isUserInGroup( userId, siteGroupId );
			boolean isInSiteRoleGroup = dataProvider.isUserInGroup( userId, siteRoleGroupId );

			if (!isInSiteGroup) {
			  dataProvider.putUserGroup(siteGroupId, userId);
			}

			if (!isInSiteRoleGroup) {
			  dataProvider.putUserGroup(siteRoleGroupId, userId);
			}
			//*/

            //Check for nested groups
            // Do we have this?
			/*
			String topGroup = wadbackend.WadGroup.getGroupByName(connexion, "Top", outTrace);
//			String topGroupId = wadbackend.WadUtilities.getAttribute(topGroup, "id");
			boolean isSiteInTopGroup = wadbackend.WadGroup.isGroupInGroup(connexion, topGroup, siteGroupId, outTrace);
			boolean isSiteRoleInSiteGroup = wadbackend.WadGroup.isGroupInGroup(connexion, siteGroupId, siteRoleGroupId, outTrace);

			if (!isSiteInTopGroup) {
				wadbackend.WadGroup.relGroup_Parent(connexion, topGroup, siteGroupId, "add", outTrace);
			}

			if (!isSiteRoleInSiteGroup) {
				wadbackend.WadGroup.relGroup_Parent(connexion, siteGroupId, siteRoleGroupId, "add", outTrace);
			}
			//*/

//			String userName = ConfigUtils.get("lti_userid");
//			if( userName == null )	/// Normally, lis_person_sourcedid is sent, otherwise, use email
//				userName = (String)payload.get(BasicLTIConstants.LIS_PERSON_CONTACT_EMAIL_PRIMARY);
//			session.setAttribute("uid", Integer.parseInt(userId));
//			session.setAttribute("username", userName);
//			session.setAttribute("userRole", wadRole);
//			session.setAttribute("gid", Integer.parseInt(siteRoleGroupId));
//			session.setAttribute("useridentifier", userName);

            String link = processEncrypted(request, payload);

            if ("".equals(link)) // Regular old behavior (which need to be changed some time donw the road)
                //Send along to WAD now
                response.sendRedirect(ltiRedirectLocation);
            else    // Otherwise, show different service
            {
                response.getWriter().write(link);
            }
        } catch (Exception e) {
            outTrace.append("\nSOMETHING BAD JUST HAPPENED!!!: ").append(e);
            response.sendError(500, e.getLocalizedMessage());
        } finally {
            destroyDB(connexion);
            if (isTrace(application)) {
                // writeLog
                FileOutputStream logfile = null;
                try {
                    logfile = new FileOutputStream(logFName, true);
                    Calendar cal = Calendar.getInstance();
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss_S");
                    String time = sdf.format(cal.getTime());

                    PrintStream logstream = new PrintStream(logfile);
                    logstream.println(time + ": POSTlti:" + outTrace);
                    logger.info(outTrace.toString());
                    logfile.close();
                } catch (IOException err) {
                    logger.error("Can't write into " + logFName, err );
                }
//				wadbackend.WadUtilities.appendlogfile(logFName, "POSTlti:" + outTrace.toString());
            }
        }

    }

    private String processEncrypted(HttpServletRequest request, Map<String, Object> payload) throws UnsupportedEncodingException {
        String link = "";
        String encrypted_session = (String) payload.get("ext_sakai_encrypted_session");
        String serverid = (String) payload.get("ext_sakai_serverid");
        String sakaiserver = (String) payload.get("ext_sakai_server");

        if (encrypted_session == null || serverid == null || sakaiserver == null) return link;

        ServletContext application = getServletConfig().getServletContext();
        String oauth_consumer_key = (String) payload.get("oauth_consumer_key");
        final String configPrefix = "basiclti.provider." + oauth_consumer_key + ".";
        final String oauth_secret = ConfigUtils.getInstance().getRequiredProperty(configPrefix + "secret");

        /// Fetch and decode session
        String sha1Secret = DigestUtils.sha1Hex(oauth_secret);

        String sessionid = BlowFish.decrypt(sha1Secret, encrypted_session);

        sessionid += "." + serverid;

        HttpSession session = request.getSession(true);
        /// Safer than sending it back through the pipes
        session.setAttribute("sakai_session", sessionid);
        session.setAttribute("sakai_server", sakaiserver);

        link = "<p>Ready to import</p>\n";

        return link;
    }


    /**
     * Lookup or create a user based on the data in the valueMap
     *
     * @param payload   Key/Value pairs containing the post request parameters
     * @param connexion DB Connection
     * @param outTrace
     * @return The found or created user's id
     * @throws Exception
     */
    private String getOrCreateUser(Map<String, Object> payload, Map<String, String> cookies, Connection connexion, StringBuffer outTrace) throws Exception {
        String userId = "0";
        StringBuffer userXml = buildUserXml(payload);
        outTrace.append("\nUserXML: ").append(userXml);

        //// FIXME: Complete this with other info from LTI
        //Does the user already exist?
        String username = null;
        String email = (String) payload.get(BasicLTIConstants.LIS_PERSON_CONTACT_EMAIL_PRIMARY);
//		if( username == null )	/// If all fail, at least we get the context_id
//			username = (String)payload.get(BasicLTIConstants.CONTEXT_ID);

        if (useEmail) {
            username = (String) payload.get(BasicLTIConstants.LIS_PERSON_CONTACT_EMAIL_PRIMARY);
        } else {
            if (!StringUtils.isBlank(ltiUserName)) {
                username = (String) payload.get(ltiUserName);
            } else {
                username = (String) payload.get(BasicLTIConstants.LIS_PERSON_SOURCEDID);
            }
        }

        userId = dataProvider.getUserId(connexion, username, email);
        if ("0".equals(userId)) {
            //create it

            if (ltiCreateUser) {
                userId = dataProvider.createUser(connexion, username, email);
                int uid = Integer.parseInt(userId);
                String famName = (String) payload.get(BasicLTIConstants.LIS_PERSON_NAME_FAMILY);
                String gibName = (String) payload.get(BasicLTIConstants.LIS_PERSON_NAME_GIVEN);
                dataProvider.putInfUserInternal(connexion, uid, uid, gibName, famName, email);
                outTrace.append("\nCreate User (self) results: ").append(userId);
            } else {
                outTrace.append("\nUser not created: ").append(username);
            }
        } else {
            outTrace.append("\nUser found: ").append(userId);
        }
        return userId;
    }

    /**
     * Lookup or create a group based on the passed title and role
     *
     * @param connexion  DB Connection
     * @param groupTitle Title of the group we want to create/lookup
     * @param role       Role of the group
     * @param outTrace
     * @return
     * @throws Exception
     */
    private String getOrCreateGroup(Connection connexion, String groupTitle, String role, StringBuffer outTrace) throws Exception {
        //Does the site group already exist?
        String group = dataProvider.getGroupByName(connexion, role);

//		String groupId = "";
        if ("0".equals(group)) {
            //create it
            /// createGroup
//			StringBuffer groupXml = buildGroupXml(groupTitle, role);
            group = dataProvider.createGroup(connexion, role);
//			groupId = wadbackend.WadUtilities.getAttribute(group,  "id");
            outTrace.append("\nCreate Group (self) results: ").append(group);
        } else {
            outTrace.append("\nGroup found: ").append(group);
        }
        return group;
    }


    /**
     * Build the xml structure needed for creating a user
     *
     * @param payload
     * @return
     * @throws Exception
     */
    private StringBuffer buildUserXml(Map<String, Object> payload) throws Exception {
        String userName = (String) payload.get(BasicLTIConstants.LIS_PERSON_SOURCEDID);
        String fname = (String) payload.get(BasicLTIConstants.LIS_PERSON_NAME_GIVEN);
        String lname = (String) payload.get(BasicLTIConstants.LIS_PERSON_NAME_FAMILY);
        String email = (String) payload.get(BasicLTIConstants.LIS_PERSON_CONTACT_EMAIL_PRIMARY);
        String active = "1";

        StringBuffer xml = new StringBuffer();
        xml.append("<user id='-1'>").append("<username>").append(userName).append("</username>").append("<firstname>")
                .append(fname).append("</firstname>").append("<lastname>").append(lname).append("</lastname>").append("<email>")
                .append(email).append("</email>").append("<active>").append(active).append("</active>").append("</user>");

        return xml;
    }

    /**
     * Create a group, always passing in a -1 for the id (so it creates a new one) and active as 1
     *
     * @param title Title to be used for the new group
     * @param role  Role to be used for the new group
     * @return
     * @throws Exception
     */
    private StringBuffer buildGroupXml(String title, String role) throws Exception {
        StringBuffer xml = new StringBuffer();

        xml.append("<group id='-1'>").append("<label>").append(title).append("</label>").append("<role>")
                .append(role).append("</role>").append("<active>1</active>").append("</group>");

        return xml;
    }

    /**
     * Ensure that this is a proper lti request and it is authorized
     *
     * @param payload     Map of the request parameters
     * @param application
     * @param outTrace
     * @throws LTIException
     */
    private void validate(Map<String, Object> payload, ServletContext application, StringBuffer outTrace) throws LTIException {


        //check parameters
        String lti_message_type = (String) payload.get(BasicLTIConstants.LTI_MESSAGE_TYPE);
        String lti_version = (String) payload.get(BasicLTIConstants.LTI_VERSION);
        String oauth_consumer_key = (String) payload.get("oauth_consumer_key");
        String user_id = (String) payload.get(BasicLTIConstants.USER_ID);
        String context_id = (String) payload.get(BasicLTIConstants.CONTEXT_ID);

        outTrace.append("\nHere I am!");
        if (!BasicLTIUtil.equals(lti_message_type, "basic-lti-launch-request")) {
            throw new LTIException("launch.invalid", "lti_message_type=" + lti_message_type, null);
        }

        if (!BasicLTIUtil.equals(lti_version, "LTI-1p0")) {
            throw new LTIException("launch.invalid", "lti_version=" + lti_version, null);
        }

        if (BasicLTIUtil.isBlank(oauth_consumer_key)) {
            throw new LTIException("launch.missing", "oauth_consumer_key", null);
        }

        if (BasicLTIUtil.isBlank(user_id)) {
            throw new LTIException("launch.missing", "user_id", null);
        }
        outTrace.append("user_id=").append(user_id);

        // Lookup the secret
        //TODO: Maybe put this in a db table for scalability?
        final String configPrefix = "basiclti.provider." + oauth_consumer_key + ".";
        final String oauth_secret = ConfigUtils.getInstance().getProperty(configPrefix + "secret");
        //final String oauth_secret = ServerConfigurationService.getString(configPrefix+ "secret", null);
        if (oauth_secret == null) {
            throw new LTIException("launch.key.notfound", oauth_consumer_key, null);
        }
        final OAuthMessage oam = (OAuthMessage) payload.get(OAUTH_MESSAGE);
        final OAuthValidator oav = new SimpleOAuthValidator();
        final OAuthConsumer cons = new OAuthConsumer("about:blank#OAuth+CallBack+NotUsed", oauth_consumer_key, oauth_secret, null);

        final OAuthAccessor acc = new OAuthAccessor(cons);

        String base_string = null;
        try {
            base_string = OAuthSignatureMethod.getBaseString(oam);
        } catch (Exception e) {
            outTrace.append("\nERROR: ").append(e.getLocalizedMessage()).append(e);
        }
        outTrace.append("\nBaseString: ").append(base_string);

        try {
            oav.validateMessage(oam, acc);
        } /*catch (NullPointerException e) {
            outTrace.append("\nProvider failed to validate message");
            outTrace.append("\n" + e.getLocalizedMessage() + ": " + e);
            if (base_string != null) {
                outTrace.append("\nWARN: " + base_string);
            }
            throw new LTIException( "launch.no.validate", context_id, e);
        } */ catch (OAuthException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            throw new LTIException("launch.no.validate", e.getLocalizedMessage(), e.getCause());
        } catch (IOException | URISyntaxException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            throw new LTIException("launch.no.validate", context_id, e);
        }
    }

    /**
     * Exception class for tracking certain types of errors
     *
     * @author chmaurer
     */
    private static class LTIException extends RuntimeException {

        private static final long serialVersionUID = -2890251603390152099L;

        public LTIException(String msg, String detail, Throwable t) {
            //String msg = foo + bar;
            super(msg + ": " + detail, t);
        }
    }

}