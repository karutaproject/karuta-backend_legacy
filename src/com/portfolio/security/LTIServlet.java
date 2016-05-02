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

package com.portfolio.security;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.naming.InitialContext;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.sql.DataSource;

import net.oauth.OAuthAccessor;
import net.oauth.OAuthConsumer;
import net.oauth.OAuthException;
import net.oauth.OAuthMessage;
import net.oauth.OAuthValidator;
import net.oauth.SimpleOAuthValidator;
import net.oauth.server.OAuthServlet;
import net.oauth.signature.OAuthSignatureMethod;

import org.apache.commons.codec.digest.DigestUtils;
import org.imsglobal.basiclti.BasicLTIConstants;
import org.imsglobal.basiclti.BasicLTIUtil;
import org.sakaiproject.basiclti.util.BlowFish;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.portfolio.data.provider.DataProvider;
import com.portfolio.data.utils.ConfigUtils;
import com.portfolio.data.utils.SqlUtils;

/**
 * Class supporting lti integration
 * Used basiclti-util-2.1.0 (from sakai 2.9.2) for the oauth dependencies
 * @author chmaurer
 *
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

	@Override
  public void init()
	{
		sc = getServletConfig();
	  ServletContext application = getServletConfig().getServletContext();
	  try
	  {
	  	ConfigUtils.loadConfigFile(sc.getServletContext());
	    loadRoleMapAttributes(application);
	  }
	  catch( Exception e ){ e.printStackTrace(); }
	}

	/**
	 * See if we want to have tracing enabled based off a config value.
	 * To enable trace output, set the following: <pre>LTIServlet.trace=true</pre>
	 * Anything else is false.
	 * @param application
	 * @return
	 */
	private boolean isTrace(ServletContext application) {
		String traceStr =  (String)application.getAttribute("LTIServlet.trace");
		return "true".equalsIgnoreCase(traceStr);

	}



	/**
	 * Initialize the DB connection
	 * @param application
	 * @return
	 * @throws Exception
	 */
	private void initDB(ServletContext application, StringBuffer outTrace) throws Exception {
//		Connection connexion = null;			// hors du try pour fermer dans finally

		//============= init servers ===============================
//	    this.sc = getServletConfig();
        String dataProviderName  =  ConfigUtils.get("dataProviderClass");
        dataProvider = (DataProvider)Class.forName(dataProviderName).newInstance();

        /*
  			// Try to initialize Datasource
  			InitialContext cxt = new InitialContext();
  			if ( cxt == null ) {
  				throw new Exception("no context found!");
  			}

  			/// Init this here, might fail depending on server hosting
  			DataSource ds = (DataSource) cxt.lookup( "java:/comp/env/jdbc/portfolio-backend" );
  			if ( ds == null ) {
  				throw new Exception("Data  jdbc/portfolio-backend source not found!");
  			}

  			if( ds == null )	// Case where we can't deploy context.xml
  			{
  				Connection con = SqlUtils.getConnection(application);
  				dataProvider.setConnection(con);
  			}
  			else
  				dataProvider.setConnection(ds.getConnection());
//  			dataProvider.setDataSource(ds);
  			//*/
        /*
		String DBuser =  (String)application.getAttribute("DBuser");
		String DBpwd =  (String)application.getAttribute("DBpwd");
		String DBserver =  (String)application.getAttribute("DBserver");
		String DBdatabase =  (String)application.getAttribute("DBdatabase");
		Class.forName("com.mysql.jdbc.Driver").newInstance();
		String connectionUrl = "jdbc:mysql://"+DBserver+"/"+DBdatabase+"?user="+DBuser+"&password="+DBpwd;
		//outTrace.append("\nDB url: " + connectionUrl);
		connexion = DriverManager.getConnection(connectionUrl);
		//*/

		return;
	}

	/**
	 * Clean up the DB connection
	 * @param connexion
	 */
	private void destroyDB(Connection connexion) {
		if(connexion != null) {
			try{
				connexion.close();
				}
			catch(Exception e){
				System.err.println("Erreur dans User-doGet: " +e);
			}
		}
	}

	private Map<String, String> processLoginCookie( HttpServletRequest request )
	{
	  Map<String, String> processedCookies = new HashMap<String, String>();
	  Cookie[] cookies = request.getCookies();
	  if( cookies != null )
	  for( int i=0; i<cookies.length; ++i )
	  {
	    Cookie c = cookies[i];
	    String name = c.getName();
	    String val = c.getValue();
	    processedCookies.put(name, val);
	  }

	  return processedCookies;
	}

	/**
	 * Process the request parameters into a map
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
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		StringBuffer outTrace = new StringBuffer();
		String logFName = null;
		Connection connexion = null;			// hors du try pour fermer dans finally
		HttpSession session = request.getSession(true);
		String ppath = session.getServletContext().getRealPath("/");
		String outsideDir =ppath.substring(0,ppath.lastIndexOf("/"))+"_files/";

		//super.doPost(request, response);
		logFName = outsideDir +"logs/logLTI.txt";
		outTrace.append("\nBegin");

		Map<String, String> cookies = processLoginCookie(request);

		Map<String, Object> payload = new HashMap<String, Object>();
		payload.put(OAUTH_MESSAGE, OAuthServlet.getMessage(request, null));
		Map<String, String> payloadStr = processRequest(request, outTrace);
		payload.putAll(payloadStr);

		ServletContext application = getServletConfig().getServletContext();

		try {
//	        LoadProperties(application);
	        initDB(application, outTrace);

//			wadbackend.WadUtilities.setApplicationAttributes(application, session);

			try {
				//ensureAuthz(request, application, outTrace);
				validate(payload, application, outTrace);
			}
			catch (LTIException e) {
				//outTrace.append("\n" + e.getMessage());
				response.sendError(HttpServletResponse.SC_UNAUTHORIZED, e.getLocalizedMessage());
				return;
			}

			loadRoleMapAttributes(application);

			connexion = SqlUtils.getConnection(session.getServletContext());
			String userId = getOrCreateUser(payload, cookies, connexion, outTrace);

			if( !"0".equals(userId) ) // FIXME: Need more checking and/or change uid String to int
			{
				session.setAttribute("uid", Integer.parseInt(userId));
				String userName = (String)payload.get(BasicLTIConstants.LIS_PERSON_SOURCEDID);
				if( userName == null )	/// Normally, lis_person_sourcedid is sent, otherwise, use email
					userName = (String)payload.get(BasicLTIConstants.LIS_PERSON_CONTACT_EMAIL_PRIMARY);
				session.setAttribute("user", userName);
			}
			else
			{
				session.invalidate();
				return;
			}

			//============Group Processing======================
			String contextLabel = (String)payload.get(BasicLTIConstants.CONTEXT_LABEL);
			String ltiRole = (String)payload.get(BasicLTIConstants.ROLES);
			String contextRole = (String)payload.get("ext_sakai_role");
			String inputRole = contextRole == null ? ltiRole : contextRole;
			outTrace.append("\nLTI Role: " + ltiRole);
			outTrace.append("\nContext Role: " + contextRole);
			outTrace.append("\nInput Role: " + inputRole);
//			String siteGroupId = getOrCreateGroup(connexion, contextLabel, "topUser", outTrace);

			StringBuffer siteGroup = new StringBuffer();
			siteGroup.append(contextLabel);
			siteGroup.append("-");
			siteGroup.append(inputRole);
			String wadRole = roleMapper(application, inputRole, outTrace);
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

			String userName = (String)payload.get(BasicLTIConstants.LIS_PERSON_SOURCEDID);
			if( userName == null )	/// Normally, lis_person_sourcedid is sent, otherwise, use email
				userName = (String)payload.get(BasicLTIConstants.LIS_PERSON_CONTACT_EMAIL_PRIMARY);
			session.setAttribute("uid", Integer.parseInt(userId));
			session.setAttribute("username", userName);
			session.setAttribute("userRole", wadRole);
//			session.setAttribute("gid", Integer.parseInt(siteRoleGroupId));
			session.setAttribute("useridentifier", userName);

			String link = processEncrypted(request, payload);

			if( "".equals(link) ) // Regular old behavior (which need to be changed some time donw the road)
				//Send along to WAD now
				response.sendRedirect(ConfigUtils.get("lti_redirect_location"));
			else	// Otherwise, show different service
			{
				response.getWriter().write(link);
			}
		}
		catch (Exception e) {
			outTrace.append("\nSOMETHING BAD JUST HAPPENED!!!: " + e);
			response.sendError(500, e.getLocalizedMessage());
		}
		finally {
			destroyDB(connexion);
			if (isTrace(application)) {
			  // writeLog
		        FileOutputStream logfile = null;
		        try {
		            logfile = new FileOutputStream (logFName, true);
		            Calendar cal = Calendar.getInstance();
		            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss_S");
		            String time = sdf.format(cal.getTime());

		            PrintStream logstream = new PrintStream(logfile);
		            logstream.println(time+": POSTlti:" + outTrace.toString());
		            logger.info(outTrace.toString());
		            logfile.close();
		        }   catch(FileNotFoundException err) {
		        } catch(IOException err) {
		        }
//				wadbackend.WadUtilities.appendlogfile(logFName, "POSTlti:" + outTrace.toString());
			}
		}

	}

	private String processEncrypted( HttpServletRequest request, Map<String, Object> payload ) throws UnsupportedEncodingException
	{
		String link = "";
		String encrypted_session = (String)payload.get("ext_sakai_encrypted_session");
		String serverid = (String)payload.get("ext_sakai_serverid");
		String sakaiserver = (String)payload.get("ext_sakai_server");

		if( encrypted_session == null || serverid == null || sakaiserver == null ) return link;

		ServletContext application = getServletConfig().getServletContext();
		String oauth_consumer_key = (String) payload.get("oauth_consumer_key");
    final String configPrefix = "basiclti.provider." + oauth_consumer_key + ".";
    final String oauth_secret = (String) ConfigUtils.get(configPrefix+ "secret");

		/// Fetch and decode session
		String sha1Secret = DigestUtils.sha1Hex(oauth_secret);

    String sessionid = BlowFish.decrypt(sha1Secret, encrypted_session);

		sessionid += "."+serverid;

		HttpSession session = request.getSession(true);
		/// Safer than sending it back through the pipes
		session.setAttribute("sakai_session", sessionid);
		session.setAttribute("sakai_server", sakaiserver);

		link = "<p>Ready to import</p>\n";

    return link;
	}



	/**
	 * Lookup or create a user based on the data in the valueMap
	 * @see wadbackend.WadUser#getUserId(Connection, String)
	 * @see wadbackend.WadUser#createUser(Connection, String, StringBuffer)
	 * @param payload Key/Value pairs containing the post request parameters
	 * @param connexion DB Connection
	 * @param outTrace
	 * @return The found or created user's id
	 * @throws Exception
	 */
	private String getOrCreateUser(Map<String, Object> payload, Map<String, String> cookies, Connection connexion, StringBuffer outTrace) throws Exception {
		String userId = "0";
		StringBuffer userXml = buildUserXml(payload);
		outTrace.append("\nUserXML: "+userXml);

		//// FIXME: Complete this with other info from LTI
		//Does the user already exist?
		String username = (String)payload.get(BasicLTIConstants.LIS_PERSON_SOURCEDID);
		String email = (String)payload.get(BasicLTIConstants.LIS_PERSON_CONTACT_EMAIL_PRIMARY);
//		if( username == null )	/// If all fail, at least we get the context_id
//			username = (String)payload.get(BasicLTIConstants.CONTEXT_ID);

		userId = dataProvider.getUserId( connexion, username, email );
		if ( "0".equals(userId) ) {
			//create it
			userId = dataProvider.createUser(connexion, username, email);
			outTrace.append("\nCreate User (self) results: " + userId);
		}
		else {
			outTrace.append("\nUser found: " + userId);
		}
		return userId;
	}

	/**
	 * Lookup or create a group based on the passed title and role
	 * @see wadbackend.WadGroup#getGroupByName(Connection, String, StringBuffer)
	 * @see wadbackend.WadGroup#createGroup(Connection, String, StringBuffer)
	 * @param connexion DB Connection
	 * @param groupTitle Title of the group we want to create/lookup
	 * @param role Role of the group
	 * @param outTrace
	 * @return
	 * @throws Exception
	 */
	private String getOrCreateGroup(Connection connexion, String groupTitle, String role, StringBuffer outTrace) throws Exception {
		//Does the site group already exist?
		String group = dataProvider.getGroupByName(connexion, role);;
//		String groupId = "";
		if ("0".equals(group)) {
			//create it
		  /// createGroup
//			StringBuffer groupXml = buildGroupXml(groupTitle, role);
			group = dataProvider.createGroup(connexion, role);
//			groupId = wadbackend.WadUtilities.getAttribute(group,  "id");
			outTrace.append("\nCreate Group (self) results: " + group);
		}
		else {
			outTrace.append("\nGroup found: " + group);
		}
		return group;
	}


	/**
	 * Build the xml structure needed for creating a user
	 * @see wadbackend.WadUser#xmlUser(String[])
	 * @param payload
	 * @return
	 * @throws Exception
	 */
	private StringBuffer buildUserXml(Map<String, Object> payload) throws Exception
	{
	  String userName = (String)payload.get(BasicLTIConstants.LIS_PERSON_SOURCEDID);
	  String fname = (String)payload.get(BasicLTIConstants.LIS_PERSON_NAME_GIVEN);
	  String lname = (String)payload.get(BasicLTIConstants.LIS_PERSON_NAME_FAMILY);
	  String email = (String)payload.get(BasicLTIConstants.LIS_PERSON_CONTACT_EMAIL_PRIMARY);
	  String active = "1";

	  StringBuffer xml = new StringBuffer();
	  xml.append(
	      "<user id='-1'>" +
	          "<username>"+userName+"</username>" +
	          "<firstname>"+fname+"</firstname>" +
	          "<lastname>"+lname+"</lastname>" +
	          "<email>"+email+"</email>" +
	          "<active>"+active+"</active>" +
	      "</user>");

		return xml;
	}

	/**
	 * Create a group, always passing in a -1 for the id (so it creates a new one) and active as 1
	 * @see wadbackend.WadGroup#xmlGroup(String[])
	 * @param title Title to be used for the new group
	 * @param role Role to be used for the new group
	 * @return
	 * @throws Exception
	 */
	private StringBuffer buildGroupXml(String title, String role) throws Exception
	{
	  StringBuffer xml = new StringBuffer();

	  xml.append(
	      "<group id='-1'>" +
	          "<label>"+title+"</label>" +
	          "<role>"+role+"</role>" +
	          "<active>1</active>" +
	      "</group>");

	  return xml;
	}

	/**
	 * Map from a passed in Role (Sakai role) to a WAD role
	 * @param inputRole
	 * @return
	 */
	static public String roleMapper(ServletContext application, String inputRole, StringBuffer outTrace) throws Exception {
		//Replace any spaces in the role name
		String adjustedInput = inputRole.replaceAll(" ", "_");
		String wadRole = (String)application.getAttribute(adjustedInput);
		//return roleMap.get(inputRole);
		if (wadRole == null) {
			throw new LTIException("roleMap.error", inputRole, null);
		}
		if( outTrace != null )
		  outTrace.append("\nRole map: " + adjustedInput + "=>" + wadRole);
		return wadRole;
	}

	/**
	 * Load in the roleMap.properties file that contains the lti_role=WAD_role definitions
	 * @param application
	 * @param session
	 * @throws Exception
	 */
	private static void loadRoleMapAttributes(ServletContext application) throws Exception {
		String servName = application.getContextPath();
		String path = application.getRealPath("/");
		File base = new File(path+"../..");
		String tomcatRoot = base.getCanonicalPath();
		path = tomcatRoot + servName +"_config"+File.separatorChar;

		String Filename = path+"roleMap.properties";
		java.io.FileInputStream fichierSrce =  new java.io.FileInputStream(Filename);
		java.io.BufferedReader readerSrce = new java.io.BufferedReader(new java.io.InputStreamReader(fichierSrce,"UTF-8"));
		String line = null;
		String variable = null;
		String value = null;
		while ((line = readerSrce.readLine())!=null){
			if (!line.startsWith("#") && line.length()>2) { // ce n'est pas un commentaire et longueur>=3 ex: x=b est le minumum
				String[] tok = line.split("=");
				variable = tok[0];
				value = tok[1];
				application.setAttribute(variable,value);
			}
		}
		fichierSrce.close();

	}

	/**
	 * Ensure that this is a proper lti request and it is authorized
	 * @param payload Map of the request parameters
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
        if(!BasicLTIUtil.equals(lti_message_type, "basic-lti-launch-request")) {
            throw new LTIException("launch.invalid", "lti_message_type="+lti_message_type, null);
        }

        if(!BasicLTIUtil.equals(lti_version, "LTI-1p0")) {
            throw new LTIException( "launch.invalid", "lti_version="+lti_version, null);
        }

        if(BasicLTIUtil.isBlank(oauth_consumer_key)) {
            throw new LTIException( "launch.missing", "oauth_consumer_key", null);
        }

        if(BasicLTIUtil.isBlank(user_id)) {
            throw new LTIException( "launch.missing", "user_id", null);
        }
        outTrace.append("user_id=" + user_id);

        // Lookup the secret
        //TODO: Maybe put this in a db table for scalability?
        final String configPrefix = "basiclti.provider." + oauth_consumer_key + ".";
        final String oauth_secret = ConfigUtils.get(configPrefix+ "secret");
        //final String oauth_secret = ServerConfigurationService.getString(configPrefix+ "secret", null);
        if (oauth_secret == null) {
            throw new LTIException( "launch.key.notfound",oauth_consumer_key, null);
        }
        final OAuthMessage oam = (OAuthMessage) payload.get(OAUTH_MESSAGE);
        final OAuthValidator oav = new SimpleOAuthValidator();
        final OAuthConsumer cons = new OAuthConsumer("about:blank#OAuth+CallBack+NotUsed", oauth_consumer_key,oauth_secret, null);

        final OAuthAccessor acc = new OAuthAccessor(cons);

        String base_string = null;
        try {
            base_string = OAuthSignatureMethod.getBaseString(oam);
        } catch (Exception e) {
            outTrace.append("\nERROR: " + e.getLocalizedMessage() + e);
            base_string = null;
        }
        outTrace.append("\nBaseString: " + base_string);

        try {
            oav.validateMessage(oam, acc);
        } /*catch (NullPointerException e) {
            outTrace.append("\nProvider failed to validate message");
            outTrace.append("\n" + e.getLocalizedMessage() + ": " + e);
            if (base_string != null) {
                outTrace.append("\nWARN: " + base_string);
            }
            throw new LTIException( "launch.no.validate", context_id, e);
        } */catch (OAuthException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			//e.g
			throw new LTIException( "launch.no.validate", e.getLocalizedMessage(), e.getCause());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new LTIException( "launch.no.validate", context_id, e);
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new LTIException( "launch.no.validate", context_id, e);
		}
    }

	/**
	 * Exception class for tracking certain types of errors
	 * @author chmaurer
	 *
	 */
	private static class LTIException extends RuntimeException {

		private static final long serialVersionUID = -2890251603390152099L;

		public LTIException(String msg, String detail, Throwable t) {
			//String msg = foo + bar;
			super(msg + ": " + detail, t);
		}
	}

	private void LoadProperties( ServletContext application ) throws Exception
	{
	     String ppath = application.getRealPath("");
	        String appli ="";
	        if (ppath.indexOf("/")>-1)
	            appli = ppath.substring(ppath.lastIndexOf("/")+1);
	        else
	            appli = ppath.substring(ppath.lastIndexOf("\\")+1);  // pour windows
	        String line = null;
	        String name = null;
	        //--------------------------------------------------------
	    		String path = application.getRealPath("/");
	    		path = path.replaceFirst(File.separator+"$", "_config"+File.separator);

	        String Filename = path+"configKaruta.properties";
	        java.io.FileInputStream fichierSrce =  new java.io.FileInputStream(Filename);
	        java.io.BufferedReader readerSrce = new java.io.BufferedReader(new java.io.InputStreamReader(fichierSrce,"UTF-8"));
	        String variable = null;
	        String value = null;
	        while ((line = readerSrce.readLine())!=null){
	            if (!line.startsWith("#") && line.length()>2) { // ce n'est pas un commentaire et longueur>=3 ex: x=b est le minumum
	                variable = line.substring(0, line.indexOf("="));
	                value = line.substring(line.indexOf("=")+1);
	                application.setAttribute(variable,value);
	            }
	        }
	        fichierSrce.close();
	        /*
	        //--------------------------------------------------------
	        String appVersionFilename = ppath+"/version";
	        java.io.FileInputStream appVersionfichierSrce =  new java.io.FileInputStream(appVersionFilename);
	        java.io.BufferedReader appVersionreaderSrce = new java.io.BufferedReader(new java.io.InputStreamReader(appVersionfichierSrce,"UTF-8"));
	        line = appVersionreaderSrce.readLine();
	        name = line.substring(line.indexOf("=")+1);
	        line = appVersionreaderSrce.readLine();
	        String appVersion = line.substring(line.indexOf("=")+1);
	        application.setAttribute("appVersion",name+" : "+appVersion);
	        appVersionfichierSrce.close();
	        //--------------------------------------------------------
	        String coreVersionFilename = ppath+"/wadcore/version";
	        java.io.FileInputStream coreVersionfichierSrce =  new java.io.FileInputStream(coreVersionFilename);
	        java.io.BufferedReader coreVersionreaderSrce = new java.io.BufferedReader(new java.io.InputStreamReader(coreVersionfichierSrce,"UTF-8"));
	        line = coreVersionreaderSrce.readLine();
	        name = line.substring(line.indexOf("=")+1);
	        line = coreVersionreaderSrce.readLine();
	        String coreVersion = line.substring(line.indexOf("=")+1);
	        application.setAttribute("coreVersion",name+" : "+coreVersion);
	        coreVersionfichierSrce.close();
	        //*/

	}

}
