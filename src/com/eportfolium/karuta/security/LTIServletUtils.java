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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import com.eportfolium.karuta.data.provider.DataProvider;
import com.eportfolium.karuta.data.utils.ConfigUtils;
import com.eportfolium.karuta.data.utils.SqlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tsugi.basiclti.BasicLTIConstants;
import org.tsugi.basiclti.BasicLTIUtil;
import org.tsugi.json.IMSJSONRequest;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class LTIServletUtils {

    public final static Logger logger = LoggerFactory.getLogger(LTIServletUtils.class);

    protected static final String OAUTH_MESSAGE = "oauth_message";
    protected static final String OAUTH_CONSUMER_KEY = "oauth_consumer_key";
    protected static final String EXT_SAKAI_ROLE = "ext_sakai_role";

    static DataProvider dataProvider;

    /**
     * Initialize the DB connection
     *
     * @param application
     * @return
     * @throws Exception
     */
    protected static Connection initDB(ServletContext application, StringBuffer outTrace) throws Exception {
        String dataProviderName = "com.eportfolium.karuta.data.provider.MysqlDataProvider";
        dataProvider = (DataProvider) Class.forName(dataProviderName).getConstructor().newInstance();

        // Open META-INF/context.xml
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        Document doc = documentBuilder.parse(application.getRealPath("/") + "/META-INF/context.xml");
        NodeList res = doc.getElementsByTagName("Resource");
        Node dbres = res.item(0);

        Properties info = new Properties();
        NamedNodeMap attr = dbres.getAttributes();
        String url = "";
        for (int i = 0; i < attr.getLength(); ++i) {
            Node att = attr.item(i);
            String name = att.getNodeName();
            String val = att.getNodeValue();
            if ("url".equals(name))
                url = val;
            else if ("username".equals(name))    // username (context.xml) -> user (properties)
                info.put("user", val);
            else if ("driverClassName".equals(name))
                Class.forName(val);
            else
                info.put(name, val);
        }

        Connection connection = DriverManager.getConnection(url, info);

        return connection;
    }

    /**
     * Clean up the DB connection
     *
     * @param connexion
     */
    protected static void destroyDB(Connection connexion) {
        if (connexion != null) {
            try {
                connexion.close();
            } catch (Exception e) {
                logger.error("Erreur dans User-doGet", e);
            }
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
    protected static boolean isTrace(ServletContext application) {
        String traceStr = (String) application.getAttribute("LTIServlet.trace");
        return Boolean.parseBoolean(traceStr);

    }

    /**
     * Process the request parameters into a map
     *
     * @param request
     * @param outTrace
     * @return
     * @throws IOException
     */
    protected static Map<String, Object> processRequest(HttpServletRequest request, StringBuffer outTrace) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        for (Enumeration<String> e = request.getParameterNames(); e.hasMoreElements(); ) {
            String key = e.nextElement();
            String value = request.getParameter(key);
            payload.put(key, value);
            outTrace.append("\nkey: ").append(key).append("(").append(value).append(")");
        }
        return payload;
    }

    /**
     * Handle the launch action from either the v1 or v2 servlet
     *
     * @param payload
     * @param application
     * @param response
     * @param session
     * @param outTrace
     * @throws ServletException
     * @throws IOException
     */
    protected static void handleLaunch(Map<String, Object> payload, ServletContext application, HttpServletResponse response,
                                       HttpSession session, StringBuffer outTrace) throws ServletException, IOException {
        Connection connexion = null;
        Connection connection = null;

        try {
            LTIServletUtils.loadRoleMapAttributes(application, session);

            connexion = LTIServletUtils.initDB(application, outTrace);

            final String userId = LTIServletUtils.getOrCreateUser(payload, connexion, outTrace);

            //============Group Processing======================
            final String contextLabel = (String) payload.get(BasicLTIConstants.CONTEXT_LABEL);
            final String ltiRole = (String) payload.get(BasicLTIConstants.ROLES);
            final String contextRole = (String) payload.get(LTIServletUtils.EXT_SAKAI_ROLE);
            final String inputRole = contextRole == null ? ltiRole : contextRole;
            outTrace.append("\nLTI Role: ").append(ltiRole);
            outTrace.append("\nContext Role: ").append(contextRole);
            outTrace.append("\nInput Role: ").append(inputRole);
            final String siteGroupId = LTIServletUtils.getOrCreateGroup(connexion, contextLabel, "topUser", outTrace);

            final String siteGroup = contextLabel + "-" + inputRole;
            final String wadRole = LTIServletUtils.roleMapper(application, inputRole, outTrace);
            final String siteRoleGroupId = LTIServletUtils.getOrCreateGroup(connexion, siteGroup, wadRole, outTrace);

            connection = SqlUtils.getConnection();

            //See what groups the user is in
            boolean isInSiteGroup = dataProvider.isUserInGroup(connection, userId, siteGroupId);
            boolean isInSiteRoleGroup = dataProvider.isUserInGroup(connection, userId, siteRoleGroupId);

            if (!isInSiteGroup) {
                dataProvider.putUserGroup(connection, siteGroupId, userId);
            }

            if (!isInSiteRoleGroup) {
                dataProvider.putUserGroup(connection, siteRoleGroupId, userId);
            }

            //Check for nested groups
            // Do we have this?
			/*
			String topGroup = wadbackend.WadGroup.getGroupByName(connexion, "Top", outTrace);
	//		String topGroupId = wadbackend.WadUtilities.getAttribute(topGroup, "id");
			boolean isSiteInTopGroup = wadbackend.WadGroup.isGroupInGroup(connexion, topGroup, siteGroupId, outTrace);
			boolean isSiteRoleInSiteGroup = wadbackend.WadGroup.isGroupInGroup(connexion, siteGroupId, siteRoleGroupId, outTrace);

			if (!isSiteInTopGroup) {
				wadbackend.WadGroup.relGroup_Parent(connexion, topGroup, siteGroupId, "add", outTrace);
			}

			if (!isSiteRoleInSiteGroup) {
				wadbackend.WadGroup.relGroup_Parent(connexion, siteGroupId, siteRoleGroupId, "add", outTrace);
			}
			//*/

            String userName = (String) payload.get(BasicLTIConstants.LIS_PERSON_SOURCEDID);
            if (userName == null)    /// Normally, lis_person_sourcedid is sent, otherwise, use email
                userName = (String) payload.get(BasicLTIConstants.LIS_PERSON_CONTACT_EMAIL_PRIMARY);

            session.setAttribute("userid", userId);
            session.setAttribute("username", userName);
            session.setAttribute("userRole", wadRole);
            session.setAttribute("groupid", siteRoleGroupId);
            session.setAttribute("useridentifier", userName);

            //Send along to WAD now
            String redirectURL = (String) application.getAttribute("lti_redirect_location");
            response.sendRedirect(redirectURL);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try {
                connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
            LTIServletUtils.destroyDB(connexion);
        }
    }

    protected static void oauthValidate(HttpServletRequest request, Map<String, Object> payload, ServletContext application) {
        final String oauth_consumer_key = (String) payload.get(LTIServletUtils.OAUTH_CONSUMER_KEY);
        final String configPrefix = "basiclti.provider." + oauth_consumer_key + ".";
        final String oauth_secret = (String) application.getAttribute(configPrefix + "secret");


        IMSJSONRequest ijr = new IMSJSONRequest(request);
        ijr.validateRequest(oauth_consumer_key, oauth_secret, request);
    }

    /**
     * Ensure that this is a proper lti request and it is authorized
     *
     * @param payload     Map of the request parameters
     * @param application
     * @param outTrace
     * @throws LTIException
     */
    protected static void validateParams(Map<String, Object> payload, ServletContext application, StringBuffer outTrace) throws LTIException {


        //check parameters
        String lti_message_type = (String) payload.get(BasicLTIConstants.LTI_MESSAGE_TYPE);
//        String lti_version = (String) payload.get(BasicLTIConstants.LTI_VERSION);
        String oauth_consumer_key = (String) payload.get(LTIServletUtils.OAUTH_CONSUMER_KEY);
        String user_id = (String) payload.get(BasicLTIConstants.USER_ID);
//        String context_id = (String) payload.get(BasicLTIConstants.CONTEXT_ID);

        outTrace.append("\nHere I am!");
        if (!BasicLTIUtil.equals(lti_message_type, "basic-lti-launch-request")) {
            throw new LTIException("launch.invalid", "lti_message_type=" + lti_message_type, null);
        }

//        if(!BasicLTIUtil.equals(lti_version, "LTI-1p0")) {
//            throw new LTIException( "launch.invalid", "lti_version="+lti_version, null);
//        }

        if (BasicLTIUtil.isBlank(oauth_consumer_key)) {
            throw new LTIException("launch.missing", "oauth_consumer_key", null);
        }

        if (BasicLTIUtil.isBlank(user_id)) {
            throw new LTIException("launch.missing", "user_id", null);
        }
        outTrace.append("user_id=" + user_id);

        // Lookup the secret
        //TODO: Maybe put this in a db table for scalability?
        final String configPrefix = "basiclti.provider." + oauth_consumer_key + ".";
        final String oauth_secret = (String) application.getAttribute(configPrefix + "secret");
        //final String oauth_secret = ServerConfigurationService.getString(configPrefix+ "secret", null);
        if (oauth_secret == null) {
            throw new LTIException("launch.key.notfound", oauth_consumer_key, null);
        }
    }


    /**
     * Lookup or create a user based on the data in the valueMap
     * If a new user gets created, also create a record in an lti log table for tracking purposes.
     *
     * @param payload   Key/Value pairs containing the post request parameters
     * @param connexion DB Connection
     * @param outTrace
     * @return The found or created user's id
     * @throws Exception
     */
    protected static String getOrCreateUser(Map<String, Object> payload, Connection connexion, StringBuffer outTrace) throws Exception {
        String userId = "0";
        StringBuffer userXml = buildUserXml(payload);
        outTrace.append("\nUserXML: ").append(userXml);

        //Does the user already exist?
        userId = dataProvider.getUserId(connexion, buildUsername(payload), null);
        if ("0".equals(userId)) {
            //create it
            userId = dataProvider.createUser(connexion, buildUsername(payload), null);
            outTrace.append("\nCreate User (self) results: ").append(userId);
        } else {
            outTrace.append("\nUser found: ").append(userId);
        }

        //Check for log entry
        String lms_user_eid = (String) payload.get(BasicLTIConstants.LIS_PERSON_SOURCEDID);
        String lms_user_id = (String) payload.get(BasicLTIConstants.USER_ID);
        String consumer_key = (String) payload.get(OAUTH_CONSUMER_KEY);

        String logId = LTIUserLog.getLogEntryId(connexion, lms_user_id, lms_user_eid, userId, consumer_key, outTrace);
        if ("0".equals(logId)) {
            // Create log entry
            StringBuffer logResult = LTIUserLog.createUserLogEntry(connexion, lms_user_id, lms_user_eid, userId, consumer_key, outTrace);
            outTrace.append("\nCreate User - Create LTI User Log results: ").append(logResult);
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
    protected static String getOrCreateGroup(Connection connexion, String groupTitle, String role, StringBuffer outTrace) throws Exception {
        //Does the site group already exist?
        String group = dataProvider.getGroupByName(connexion, role);
//		String groupId = "";
        if ("0".equals(group)) {
            //create it
            StringBuffer groupXml = buildGroupXml(groupTitle, role);
            group = dataProvider.createGroup(connexion, role);
//			group = dataProvider.createGroup(groupXml.toString()).toString();
//			groupId = wadbackend.WadUtilities.getAttribute(group,  "id");
            outTrace.append("\nCreate Group (self) results: ").append(group);
        } else {
            outTrace.append("\nGroup found: ").append(group);
        }
        return group;
    }

    /**
     * Combine the consumer key and the lms user id, hoping to make a unique username across multiple lti clients
     *
     * @param payload
     * @return
     */
    protected static String buildUsername(Map<String, Object> payload) {
        String consumer_key = (String) payload.get(OAUTH_CONSUMER_KEY);
        String lms_user_id = (String) payload.get(BasicLTIConstants.USER_ID);
        return consumer_key + "_" + lms_user_id;
    }


    /**
     * Build the xml structure needed for creating a user
     *
     * @param payload
     * @return
     * @throws Exception
     */
    protected static StringBuffer buildUserXml(Map<String, Object> payload) throws Exception {
        String userName = buildUsername(payload);
        String fname = (String) payload.get(BasicLTIConstants.LIS_PERSON_NAME_GIVEN);
        String lname = (String) payload.get(BasicLTIConstants.LIS_PERSON_NAME_FAMILY);
        String email = (String) payload.get(BasicLTIConstants.LIS_PERSON_CONTACT_EMAIL_PRIMARY);
        String active = "1";
        String[] userInfo = {"-1", userName, fname, lname, email, active};

        StringBuffer xml = new StringBuffer();
        xml.append("<user id='-1'>" + "<username>").append(userName).append("</username>").append("<firstname>").append(fname).append("</firstname>")
                .append("<lastname>").append(lname).append("</lastname>").append("<email>").append(email).append("</email>")
                .append("<active>").append(active).append("</active>").append("</user>");

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
    protected static StringBuffer buildGroupXml(String title, String role) throws Exception {
        StringBuffer xml = new StringBuffer();
        xml.append("<group id='-1'>" + "<label>").append(title).append("</label>").append("<role>").append(role).append("</role>")
                .append("<active>1</active>").append("</group>");

        return xml;
    }

    /**
     * Map from a passed in Role (Sakai role) to a WAD role
     *
     * @param inputRole
     * @return
     */
    protected static String roleMapper(ServletContext application, String inputRole, StringBuffer outTrace) throws Exception {
        //Replace any spaces in the role name
        String adjustedInput = inputRole.replaceAll(" ", "_");
        String wadRole = (String) application.getAttribute(adjustedInput);
        //return roleMap.get(inputRole);
        if (wadRole == null) {
            throw new LTIException("roleMap.error", inputRole, null);
        }
        outTrace.append("\nRole map: ").append(adjustedInput).append("=>").append(wadRole);
        return wadRole;
    }

    /**
     * Load in the roleMap.properties file that contains the lti_role=WAD_role definitions
     *
     * @param application
     * @param session
     * @throws Exception
     */
    protected static void loadRoleMapAttributes(ServletContext application, HttpSession session) throws Exception {
        java.io.FileInputStream fichierSrce = new java.io.FileInputStream(ConfigUtils.getInstance().getConfigPath() + "roleMap.properties");
        java.io.BufferedReader readerSrce = new java.io.BufferedReader(new java.io.InputStreamReader(fichierSrce, StandardCharsets.UTF_8));
        String line = null;
        String variable = null;
        String value = null;
        while ((line = readerSrce.readLine()) != null) {
            if (!line.startsWith("#") && line.length() > 2) { // ce n'est pas un commentaire et longueur>=3 ex: x=b est le minumum
                variable = line.substring(0, line.indexOf("="));
                value = line.substring(line.indexOf("=") + 1);
                application.setAttribute(variable, value);
            }
        }
        fichierSrce.close();

    }

    /**
     * Exception class for tracking certain types of errors
     *
     * @author chmaurer
     */
    protected static class LTIException extends RuntimeException {

        private static final long serialVersionUID = -2890251603390152099L;

        public LTIException(String msg, String detail, Throwable t) {
            //String msg = foo + bar;
            super(msg + ": " + detail, t);
        }
    }
}