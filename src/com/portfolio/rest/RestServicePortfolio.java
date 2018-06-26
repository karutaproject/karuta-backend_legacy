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

package com.portfolio.rest;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.activation.MimeType;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.ws.rs.Consumes;
import javax.ws.rs.CookieParam;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.XML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.portfolio.data.provider.DataProvider;
import com.portfolio.data.utils.ConfigUtils;
import com.portfolio.data.utils.DomUtils;
import com.portfolio.data.utils.LogUtils;
import com.portfolio.data.utils.MailUtils;
import com.portfolio.data.utils.SqlUtils;
import com.portfolio.data.utils.javaUtils;
import com.portfolio.eventbus.KEvent;
import com.portfolio.eventbus.KEventbus;
import com.portfolio.security.Credential;
import com.portfolio.security.NodeRight;
import com.portfolio.socialnetwork.Elgg;
import com.portfolio.socialnetwork.Ning;
import com.sun.jersey.multipart.FormDataParam;

import edu.yale.its.tp.cas.client.ServiceTicketValidator;

/// I hate this line, sometime it works with a '/', sometime it doesn't
@Path("/api")
public class RestServicePortfolio
{
	final Logger logger = LoggerFactory.getLogger(RestServicePortfolio.class);

	class UserInfo
	{
		String subUser = "";
		int subId = 0;
		String User = "";
		int userId = 0;
//		int groupId = -1;
	}

//	DataSource ds;
	DataProvider dataProvider;
	final Credential credential = new Credential();
	int logRestRequests = 0;
	String label = null;
	String casUrlValidation = null;
	String elggDefaultApiUrl = null;
	String elggDefaultSiteUrl = null;
	String elggApiKey = null;
	String elggDefaultUserPassword = null;

	ServletContext servContext;
	ServletConfig servConfig;

	KEventbus eventbus = new KEventbus();
	
	static BufferedWriter editLog = null;
	static BufferedWriter errorLog = null;

	static final String logFormat = "[%1$s] %2$s %3$s: %4$s -- %5$s (%6$s) === %7$s\n";
	static final String logFormatShort = "%7$s\n";
	
	/**
	 * Initialize service objects
	 **/
	public RestServicePortfolio( @Context ServletConfig sc , @Context ServletContext context)
	{
		try
		{
			// Loading configKaruta.properties
			ConfigUtils.loadConfigFile(sc.getServletContext());

			// User action logging
			String editlog = ConfigUtils.get("edit_log");
			if( !"".equals(editlog) && editlog != null )
				editLog = LogUtils.getLog(editlog);
			// General error log
			String errorlog = ConfigUtils.get("error_log");
			if( !"".equals(errorlog) && errorlog != null )
				errorLog = LogUtils.getLog(errorlog);

			// Initialize data provider and cas
			casUrlValidation =  ConfigUtils.get("casUrlValidation") ;
			// Elgg variables
			elggDefaultApiUrl =  ConfigUtils.get("elggDefaultApiUrl") ;
			elggDefaultSiteUrl =  ConfigUtils.get("elggDefaultSiteUrl") ;
			elggApiKey =  ConfigUtils.get("elggApiKey") ;
			elggDefaultUserPassword =  ConfigUtils.get("elggDefaultUserPassword") ;

			servConfig = sc;
			servContext = context;
			dataProvider = SqlUtils.initProvider(context, null);
		}
		catch( NullPointerException e )
		{
//			e.printStackTrace();
//			logger.error("COULDN'T LOAD CONFIGURATION FILE AT: "+ConfigUtils.getFilepath());
		}
		catch( Exception e )
		{
			logger.info("CAN'T INIT PROVIDER: "+e.toString());
			e.printStackTrace();
		}
	}

	@Deprecated
	public void logRestRequest(HttpServletRequest httpServletRequest, String inBody, String outBody, int httpCode)
	{

		try
		{
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
		}

	}

	/**
	 * Initialize DB connections
	 **/
	public void initService( HttpServletRequest request )
	{
		try
		{
		}
		catch ( Exception e )
		{
			logger.error("CAN'T CREATE CONNECTION: "+e.getMessage());
//			e.printStackTrace();
		}
	}

	/**
	 * Fetch user session info
	 **/
	public UserInfo checkCredential(HttpServletRequest request, String login, String token, String group )
	{
		HttpSession session = request.getSession(true);

		/*
		String referer = (String) request.getHeader("referer");	// Can be spoofed
		String source = (String) session.getAttribute("source");
		if( source != null )
		{
			if( referer == null )
			{
				session.invalidate();
				session = request.getSession(true);
			}
			else
			{
				int last = referer.lastIndexOf("/");
				int stop = referer.indexOf("?");
				String page = "";
				if(stop > 0)
					page = referer.substring(last+1, stop);
				else
					page = referer.substring(last+1);
				if( !page.equals(source) )
				{
					session.invalidate();
					session = request.getSession(true);
				}
			}
		}
		//*/
		
		UserInfo ui = new UserInfo();
		initService(request);
		Integer val = (Integer) session.getAttribute("uid");
		if( val != null )
			ui.userId = val;
//		val = (Integer) session.getAttribute("gid");
//		if( val != null )
//			ui.groupId = val;
		val = (Integer) session.getAttribute("subuid");
		if( val != null )
			ui.subId = val;
		ui.User = (String) session.getAttribute("user");
		ui.subUser = (String) session.getAttribute("subuser");

		return ui;
	}

	/**
	 *	Fetch current user info
	 *	GET /rest/api/credential
	 *	parameters:
	 *	return:
	 *	<user id="uid">
	 *		<username></username>
	 *		<firstname></firstname>
	 *		<lastname></lastname>
	 *		<email></email>
	 *		<admin>1/0</admin>
	 *		<designer>1/0</designer>
	 *		<active>1/0</active>
	 *		<substitute>1/0</substitute>
	 *	</user>
	 **/
	@Path("/credential")
	@GET
	@Produces(MediaType.APPLICATION_XML)
	public Response getCredential( @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;

		if( ui.userId == 0 )	// Non valid userid
		{
			return Response.status(401).build();
		}

		try
		{
			c = SqlUtils.getConnection(servContext);

			String xmluser = dataProvider.getInfUser(c, ui.userId, ui.userId);
			logRestRequest(httpServletRequest, "", xmluser, Status.OK.getStatusCode());

			/// Add shibboleth info if needed
			HttpSession session = httpServletRequest.getSession(false);
			int fromshibe = (Integer) session.getAttribute("fromshibe");
			String alist = ConfigUtils.get("shib_attrib");
			if( fromshibe == 1 && alist != null )
			{
				/// Fetch and construct needed data
				String[] attriblist = alist.split(",");
				int lastst = xmluser.lastIndexOf("<");
				StringBuilder shibuilder = new StringBuilder( xmluser.substring(0, lastst) );
				for( String attrib : attriblist )
				{
					String value = (String) httpServletRequest.getAttribute(attrib);
					shibuilder.append("<").append(attrib).append(">")
					.append(value)
					.append("</").append(attrib).append(">");
				}
				/// Add it as last tag after "moving" the closing tag
				shibuilder.append(xmluser.substring(lastst));
				xmluser = shibuilder.toString();
			}
			
			return Response.ok(xmluser).build();
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, "getCredential", ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Get groups from a user id
	 *	GET /rest/api/groups
	 *	parameters:
	 *	- group: group id
	 *	return:
	 *	<groups>
	 *		<group id="gid" owner="uid" templateId="rrgid">GROUP LABEL</group>
	 *		...
	 *	</groups>
	 **/
	@Path("/groups")
	@GET
	@Produces(MediaType.APPLICATION_XML)
	public String getGroups(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;
		
		try
		{
			c = SqlUtils.getConnection(servContext);

			String xmlGroups = (String) dataProvider.getUserGroups(c, ui.userId);
			logRestRequest(httpServletRequest, "", xmlGroups, Status.OK.getStatusCode());

			return xmlGroups;
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, "", ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}


	/**
	 *	Get user list
	 *	GET /rest/api/users
	 *	parameters:
	 *	return:
	 *	<users>
	 *		<user id="uid">
	 *			<username></username>
	 *			<firstname></firstname>
	 *			<lastname></lastname>
	 *			<admin>1/0</admin>
	 *			<designer>1/0</designer>
	 *			<email></email>
	 *			<active>1/0</active>
	 *			<substitute>1/0</substitute>
	 *		</user>
	 *		...
	 *	</users>
	 **/
	@Path("/users")
	@GET
	@Produces(MediaType.APPLICATION_XML)
	public String getUsers(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("username") String username, @QueryParam("firstname") String firstname, @QueryParam("lastname") String lastname, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;

		if( ui.userId == 0 )
			throw new RestWebApplicationException(Status.FORBIDDEN, "Not logged in");
		try
		{
			c = SqlUtils.getConnection(servContext);

			String xmlGroups = "";
			if(credential.isAdmin(c, ui.userId) || credential.isCreator(c, ui.userId))
				xmlGroups = dataProvider.getListUsers(c, ui.userId, username, firstname, lastname);
			else if(ui.userId != 0)
				xmlGroups = dataProvider.getInfUser(c, ui.userId, ui.userId);
			else
				throw new RestWebApplicationException(Status.FORBIDDEN, "Not authorized");
			logRestRequest(httpServletRequest, "", xmlGroups, Status.OK.getStatusCode());

			return xmlGroups;
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, "", ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Get a specific user info
	 *	GET /rest/api/users/user/{user-id}
	 *	parameters:
	 *	return:
	 *	<user id="uid">
	 *		<username></username>
	 *		<firstname></firstname>
	 *		<lastname></lastname>
	 *		<admin>1/0</admin>
	 *		<designer>1/0</designer>
	 *		<email></email>
	 *		<active>1/0</active>
	 *		<substitute>1/0</substitute>
	 *	</user>
	 **/
	@Path("/users/user/{user-id}")
	@GET
	@Produces(MediaType.APPLICATION_XML)
	public String getUser(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("user-id") int userid, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;

		try
		{
			c = SqlUtils.getConnection(servContext);

			String xmluser = dataProvider.getInfUser(c, ui.userId, userid);
			logRestRequest(httpServletRequest, "", xmluser, Status.OK.getStatusCode());

			return xmluser;
		}
		catch(RestWebApplicationException ex)
		{
			logRestRequest(httpServletRequest, null, null, Status.NOT_FOUND.getStatusCode());

			throw new RestWebApplicationException(Status.NOT_FOUND, ex.getResponse().getEntity().toString());
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, "", ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Modify user info
	 *	PUT /rest/api/users/user/{user-id}
	 *	body:
	 *	<user id="uid">
	 *		<username></username>
	 *		<firstname></firstname>
	 *		<lastname></lastname>
	 *		<admin>1/0</admin>
	 *		<designer>1/0</designer>
	 *		<email></email>
	 *		<active>1/0</active>
	 *		<substitute>1/0</substitute>
	 *	</user>
	 *
	 *	parameters:
	 *
	 *	return:
	 *	<user id="uid">
	 *		<username></username>
	 *		<firstname></firstname>
	 *		<lastname></lastname>
	 *		<admin>1/0</admin>
	 *		<designer>1/0</designer>
	 *		<email></email>
	 *		<active>1/0</active>
	 *		<substitute>1/0</substitute>
	 *	</user>
	 **/
	@Path("/users/user/{user-id}")
	@PUT
	@Produces(MediaType.APPLICATION_XML)
	public String putUser( String xmlInfUser, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("user-id") int userid, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;
		
		try
		{
			c = SqlUtils.getConnection(servContext);

			String queryuser = "";
			if(credential.isAdmin(c, ui.userId) || credential.isCreator(c, ui.userId))
				queryuser = dataProvider.putInfUser(c, ui.userId, userid, xmlInfUser);
			else if(ui.userId == userid)	/// Changing self
				queryuser = dataProvider.UserChangeInfo(c, ui.userId, userid, xmlInfUser);
			else
				throw new RestWebApplicationException(Status.FORBIDDEN, "Not authorized");

			logRestRequest(httpServletRequest, "", queryuser, Status.OK.getStatusCode());

			return queryuser;
		}
		catch(RestWebApplicationException ex)
		{
			logRestRequest(httpServletRequest, null, null, Status.NOT_FOUND.getStatusCode());
			throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, "", ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, "Error : "+ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Get user id from username
	 *	GET /rest/api/users/user/username/{username}
	 *	parameters:
	 *	return:
	 *	userid (long)
	 **/
	@Path("/users/user/username/{username}")
	@GET
	@Produces(MediaType.APPLICATION_XML)
	public String getUserId(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("username") String username, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;

		try
		{
			c = SqlUtils.getConnection(servContext);

			String userid = dataProvider.getUserID(c, ui.userId, username);
			logRestRequest(httpServletRequest, "", username, Status.OK.getStatusCode());
			return userid;
		}
		catch(RestWebApplicationException ex)
		{
			logRestRequest(httpServletRequest, null, null, Status.NOT_FOUND.getStatusCode());
			throw new RestWebApplicationException(Status.NOT_FOUND, ex.getResponse().getEntity().toString());
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, "", ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Get a list of role/group for this user
	 *	GET /rest/api/users/user/{user-id}/groups
	 *	parameters:
	 *	return:
	 *	<profiles>
	 *		<profile>
	 *			<group id="gid">
	 *				<label></label>
	 *				<role></role>
	 *			</group>
	 *		</profile>
	 *	</profiles>
	 **/
	@Path("/users/user/{user-id}/groups")
	@GET
	@Produces(MediaType.APPLICATION_XML)
	public String getGroupsUser(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("user-id") int useridCible, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;

		try
		{
			c = SqlUtils.getConnection(servContext);

			String xmlgroupsUser = dataProvider.getRoleUser(c, ui.userId, useridCible);
			logRestRequest(httpServletRequest, "", xmlgroupsUser, Status.OK.getStatusCode());

			return xmlgroupsUser;
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, "", ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Get rights in a role from a groupid
	 *	GET /rest/api/groupRights
	 *	parameters:
	 *	- group: role id
	 *	return:
	 *	<groupRights>
	 *		<groupRight  gid="groupid" templateId="grouprightid>
	 *			<item
	 *				AD="True/False"
	 *				creator="uid";
	 *				date="";
	 *				DL="True/False"
	 *				id=uuid
	 *				owner=uid";
	 *				RD="True/False"
	 *				SB="True"/"False"
	 *				typeId=" ";
	 *				WR="True/False"/>";
	 *		</groupRight>
	 *	</groupRights>
	 **/
	@Path("/groupRights")
	@GET
	@Produces(MediaType.APPLICATION_XML)
	public String getGroupRights(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest )
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;

		try
		{
			c = SqlUtils.getConnection(servContext);
			
			String xmlGroups = (String) dataProvider.getGroupRights(c, ui.userId, groupId);
			logRestRequest(httpServletRequest, "", xmlGroups, Status.OK.getStatusCode());

			return xmlGroups;
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, "", ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Get role list from portfolio from uuid
	 *	GET /rest/api/groupRightsInfos
	 *	parameters:
	 *	- portfolioId: portfolio uuid
	 *	return:
	 *	<groupRightsInfos>
	 *		<groupRightInfo grid="grouprightid">
	 *			<label></label>
	 *			<owner>UID</owner>
	 *		</groupRightInfo>
	 *	</groupRightsInfos>
	 **/
	@Path("/groupRightsInfos")
	@GET
	@Produces(MediaType.APPLICATION_XML)
	public String getGroupRightsInfos(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("portfolioId") String portfolioId)
	{
		if( !isUUID(portfolioId) )
		{
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}
		
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;

		try
		{
			c = SqlUtils.getConnection(servContext);

			String xmlGroups = dataProvider.getGroupRightsInfos(c, ui.userId, portfolioId);
			logRestRequest(httpServletRequest, "", xmlGroups, Status.OK.getStatusCode());

			return xmlGroups;
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, "", ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Get a portfolio from uuid
	 *	GET /rest/api/portfolios/portfolio/{portfolio-id}
	 *	parameters:
	 *	- resources:
	 *	- files: if set with resource, return a zip file
	 *	- export: if set, return xml as a file download
	 *	return:
	 *	zip
	 *	as file download
	 *	content
	 *	<?xml version=\"1.0\" encoding=\"UTF-8\"?>
	 *	<portfolio code=\"0\" id=\""+portfolioUuid+"\" owner=\""+isOwner+"\"><version>4</version>
	 *		<asmRoot>
	 *			<asm*>
	 *				<metadata-wad></metadata-wad>
	 *				<metadata></metadata>
	 *				<metadata-epm></metadata-epm>
	 *				<asmResource xsi_type="nodeRes">
	 *				<asmResource xsi_type="context">
	 *				<asmResource xsi_type="SPECIFIC TYPE">
	 *			</asm*>
	 *		</asmRoot>
	 *	</portfolio>
	 **/
	@Path("/portfolios/portfolio/{portfolio-id}")
	@GET
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, "application/zip", MediaType.APPLICATION_OCTET_STREAM})
	public Object getPortfolio(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("portfolio-id") String portfolioUuid,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept, @QueryParam("user") Integer userId, @QueryParam("group") Integer group, @QueryParam("resources") String resource, @QueryParam("files") String files, @QueryParam("export") String export, @QueryParam("lang") String lang, @QueryParam("level") Integer cutoff)
	{
		if( !isUUID(portfolioUuid) )
		{
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}
		
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		Connection c = null;
		Response response = null;
		try
		{
			c = SqlUtils.getConnection(servContext);
			String portfolio = dataProvider.getPortfolio(c, new MimeType("text/xml"),portfolioUuid,ui.userId, 0, this.label, resource, "", ui.subId, cutoff).toString();

			if( "faux".equals(portfolio) )
			{
				response = Response.status(403).build();
			}

			if( response == null )
			{
				/// Finding back code. Not really pretty
				Date time = new Date();
				SimpleDateFormat dt = new SimpleDateFormat("yyyy-MM-dd HHmmss");
				String timeFormat = dt.format(time);
				Document doc = DomUtils.xmlString2Document(portfolio, new StringBuffer());
				NodeList codes = doc.getDocumentElement().getElementsByTagName("code");
				// Le premier c'est celui du root
				Node codenode = codes.item(0);
				String code = "";
				if( codenode != null )
					code = codenode.getTextContent();
				// Sanitize code
				code = code.replace("_", "");

				if( export != null )
				{
					response = Response
							.ok(portfolio)
							.header("content-disposition","attachment; filename = \""+code+"-"+timeFormat+".xml\"")
							.build();
				}
				else if(resource != null && files != null)
				{
					//// Cas du renvoi d'un ZIP
					HttpSession session = httpServletRequest.getSession(true);
					File tempZip = getZipFile(portfolioUuid, portfolio, lang, doc, session);

					/// Return zip file
					RandomAccessFile f = new RandomAccessFile(tempZip.getAbsoluteFile(), "r");
					byte[] b = new byte[(int)f.length()];
					f.read(b);
					f.close();

					response = Response
							.ok(b, MediaType.APPLICATION_OCTET_STREAM)
							.header("content-disposition","attachment; filename = \""+code+"-"+timeFormat+".zip")
							.build();

					// Temp file cleanup
					tempZip.delete();
				}
				else
				{
					if(portfolio.equals("faux")){

						throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
					}

					if(accept.equals(MediaType.APPLICATION_JSON))
					{
						portfolio = XML.toJSONObject(portfolio).toString();
						response = Response.ok(portfolio).type(MediaType.APPLICATION_JSON).build();
					}
					else
						response = Response.ok(portfolio).type(MediaType.APPLICATION_XML).build();

					logRestRequest(httpServletRequest, null, portfolio, Status.OK.getStatusCode());
				}
			}
		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
		}
		catch(SQLException ex)
		{
			logRestRequest(httpServletRequest, null, "Portfolio "+portfolioUuid+" not found",Status.NOT_FOUND.getStatusCode());
			logger.info("Portfolio "+portfolioUuid+" not found");
			throw new RestWebApplicationException(Status.NOT_FOUND, "Portfolio "+portfolioUuid+" not found");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, null,ex.getMessage()+"\n\n"+ex.getStackTrace(), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}

		return response;
	}

	private File getZipFile( String portfolioUuid, String portfolioContent, String lang, Document doc, HttpSession session ) throws IOException, XPathExpressionException
	{
		if( !isUUID(portfolioUuid) )
		{
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}

		/// Temp file in temp directory
		File tempDir = new File(System.getProperty("java.io.tmpdir", null));
		File tempZip = File.createTempFile(portfolioUuid, ".zip", tempDir);

		FileOutputStream fos = new FileOutputStream(tempZip);
		ZipOutputStream zos = new ZipOutputStream(fos);

		/// Write xml file to zip
		ZipEntry ze = new ZipEntry(portfolioUuid+".xml");
		zos.putNextEntry(ze);

		byte[] bytes = portfolioContent.getBytes("UTF-8");
		zos.write(bytes);

		zos.closeEntry();

		/// Find all fileid/filename
		XPath xPath = XPathFactory.newInstance().newXPath();
		String filterRes = "//*[local-name()='asmResource']/*[local-name()='fileid' and text()]";
		NodeList nodelist = (NodeList) xPath.compile(filterRes).evaluate(doc, XPathConstants.NODESET);

		/// Fetch all files
		for( int i=0; i<nodelist.getLength(); ++i )
		{
			Node res = nodelist.item(i);
			/// Check if fileid has a lang
			Node langAtt = res.getAttributes().getNamedItem("lang");
			String filterName = "";
			if( langAtt != null )
			{
				lang = langAtt.getNodeValue();
				filterName = ".//*[local-name()='filename' and @lang='"+lang+"' and text()]";
			}
			else
			{
				filterName = ".//*[local-name()='filename' and @lang and text()]";
			}

			Node p = res.getParentNode();	// fileid -> resource
			Node gp = p.getParentNode();	// resource -> context
			Node uuidNode = gp.getAttributes().getNamedItem("id");
			String uuid = uuidNode.getTextContent();

			NodeList textList = (NodeList) xPath.compile(filterName).evaluate(p, XPathConstants.NODESET);
			String filename = "";
			if( textList.getLength() != 0 )
			{
				Element fileNode = (Element) textList.item(0);
				filename = fileNode.getTextContent();
				lang = fileNode.getAttribute("lang");	// In case it's a general fileid, fetch first filename (which can break things if nodes are not clean)
				if( "".equals(lang) ) lang = "fr";
			}

			String backend = ConfigUtils.get("backendserver"); 
			String url = backend+"/resources/resource/file/"+uuid+"?lang="+lang;
			HttpGet get = new HttpGet(url);

			// Transfer sessionid so that local request still get security checked
			get.addHeader("Cookie","JSESSIONID="+session.getId());

			// Send request
			CloseableHttpClient client = HttpClients.createDefault();
			CloseableHttpResponse ret = client.execute(get);
			HttpEntity entity = ret.getEntity();

			// Put specific name for later recovery
			if( "".equals(filename) )
				continue;
			int lastDot = filename.lastIndexOf(".");
			if( lastDot < 0 )
				lastDot = 0;
			String filenameext = filename.substring(0);	/// find extension
			int extindex = filenameext.lastIndexOf(".") + 1;
			filenameext = uuid +"_"+ lang +"."+ filenameext.substring(extindex);

			// Save it to zip file
			InputStream content = entity.getContent();
			ze = new ZipEntry(filenameext);
			try
			{
				int totalread = 0;
				zos.putNextEntry(ze);
				int inByte;
				byte[] buf = new byte[4096];
				while( (inByte = content.read(buf)) != -1 )
				{
					totalread += inByte;
					zos.write(buf, 0, inByte);
				}
				System.out.println("FILE: "+filenameext+" -> "+totalread);
				content.close();
				zos.closeEntry();
			}
			catch( Exception e )
			{
				e.printStackTrace();
			}
			EntityUtils.consume(entity);
			ret.close();
			client.close();
		}

		zos.close();
		fos.close();
		
		return tempZip;
	}
	
	
	/**
	 *	Return the portfolio from its code
	 *	GET /rest/api/portfolios/code/{code}
	 *	parameters:
	 *	return:
	 *	see 'content' of "GET /rest/api/portfolios/portfolio/{portfolio-id}"
	 **/
	@Path("/portfolios/portfolio/code/{code}")
	@GET
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	public Object getPortfolioByCode(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("code") String code,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept, @QueryParam("user") Integer userId, @QueryParam("group") Integer group, @QueryParam("resources") String resources )
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;
		
		try
		{
			if( resources == null )
				resources = "false";
			c = SqlUtils.getConnection(servContext);
			String returnValue = dataProvider.getPortfolioByCode(c, new MimeType("text/xml"),code,ui.userId, groupId, resources, ui.subId).toString();
			if( "faux".equals(returnValue)){

				throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
			}
			if( "".equals(returnValue) )
			{
				return Response.status(Status.NOT_FOUND).entity("").build();
			}
			if(MediaType.APPLICATION_JSON.equals(accept))	// Not really used
				returnValue = XML.toJSONObject(returnValue).toString();

			logRestRequest(httpServletRequest, null, returnValue, Status.OK.getStatusCode());

			return returnValue;
		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
		}
		catch(SQLException ex)
		{
			logRestRequest(httpServletRequest, null, "Portfolio code = "+code+" not found",Status.NOT_FOUND.getStatusCode());

			throw new RestWebApplicationException(Status.NOT_FOUND, "Portfolio code = "+code+" not found");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, null,ex.getMessage()+"\n\n"+ex.getStackTrace(), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	List portfolios for current user (return also other things, but should be removed)
	 *	GET /rest/api/portfolios
	 *	parameters:
	 *	- active: false/0	(also show inactive portoflios)
	 *	- code
	 *	- n: number of results (10<n<50)
	 *	- i: index start + n
	 *	return:
	 *	<?xml version=\"1.0\" encoding=\"UTF-8\"?>
	 *	<portfolios>
	 *		<portfolio  id="uuid" root_node_id="uuid" owner="Y/N" ownerid="uid" modified="DATE">
	 *			<asmRoot id="uuid">
	 *				<metadata-wad/>
	 *				<metadata-epm/>
	 *				<metadata/>
	 *				<code></code>
	 *				<label/>
	 *				<description/>
	 *				<semanticTag/>
	 *				<asmResource xsi_type="nodeRes"></asmResource>
	 *				<asmResource xsi_type="context"/>
	 *			</asmRoot>
	 *		</portfolio>
	 *		...
	 *	</portfolios>
	 **/
	@Path("/portfolios")
	@GET
	@Consumes(MediaType.APPLICATION_XML)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	public String getPortfolios(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept, @QueryParam("active") String active, @QueryParam("user") Integer userId, @QueryParam("code") String code, @QueryParam("portfolio") String portfolioUuid, @QueryParam("i") String index, @QueryParam("n") String numResult, @QueryParam("level") Integer cutoff, @QueryParam("public") String public_var, @QueryParam("project") String project, @QueryParam("count") String count,  @QueryParam("search") String search)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;
		
		try
		{
			c = SqlUtils.getConnection(servContext);
			if(portfolioUuid!=null)
			{
				String returnValue = dataProvider.getPortfolio(c, new MimeType("text/xml"),portfolioUuid,ui.userId, groupId, this.label, null, null, ui.subId, cutoff).toString();
				if(accept.equals(MediaType.APPLICATION_JSON))
					returnValue = XML.toJSONObject(returnValue).toString();

				logRestRequest(httpServletRequest, null, returnValue, Status.OK.getStatusCode());

				return returnValue;

			}
			else
			{
				String portfolioCode = null;
				String returnValue = "";
				Boolean countOnly = false;
				Boolean portfolioActive;
				Boolean portfolioProject = null;
				String portfolioProjectId = null;

				try { if(active.equals("false") ||  active.equals("0")) portfolioActive = false; else portfolioActive = true; }
				catch(Exception ex) { portfolioActive = true; };
				
				try { 
						if(project.equals("false") ||  project.equals("0")) portfolioProject = false;
						else if(project.equals("true") ||  project.equals("1")) portfolioProject = true;
						else if(project.length()>0) portfolioProjectId = project;
				}
				catch(Exception ex) { portfolioProject = null; };

				try { if(count.equals("true") ||  count.equals("1")) countOnly = true; else countOnly = false; } catch(Exception ex) { countOnly = false;  };
				
				try { portfolioCode = code; } catch(Exception ex) { };
				if(portfolioCode!=null)
				{
					returnValue = dataProvider.getPortfolioByCode(c, new MimeType("text/xml"),portfolioCode, ui.userId, groupId, null, ui.subId).toString();
				}
				else
				{
					if( public_var != null )
					{
						int publicid = credential.getMysqlUserUid(c, "public");
						returnValue = dataProvider.getPortfolios(c, new MimeType("text/xml"), publicid, groupId, portfolioActive, 0, portfolioProject, portfolioProjectId, countOnly, search).toString();
					}
					else if( userId != null && credential.isAdmin(c, ui.userId) )	//	XXX If user is admin, can ask any specific list of portfolios as a specific user	(normally redudant with substitution) 
					{
						returnValue = dataProvider.getPortfolios(c, new MimeType("text/xml"), userId, groupId, portfolioActive, ui.subId, portfolioProject, portfolioProjectId, countOnly, search).toString();
					}
					else	/// For user logged in
					{
						returnValue = dataProvider.getPortfolios(c, new MimeType("text/xml"), ui.userId, groupId, portfolioActive, ui.subId, portfolioProject, portfolioProjectId, countOnly, search).toString();
					}

					if(accept.equals(MediaType.APPLICATION_JSON))
						returnValue = XML.toJSONObject(returnValue).toString();
				}
				logRestRequest(httpServletRequest, null, returnValue, Status.OK.getStatusCode());

				return returnValue;
			}
		}
		catch(RestWebApplicationException ex)
		{
			logRestRequest(httpServletRequest,null, null, Status.FORBIDDEN.getStatusCode());

			throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
		}
		catch(SQLException ex)
		{
			logRestRequest(httpServletRequest,null, null, Status.NOT_FOUND.getStatusCode());

			throw new RestWebApplicationException(Status.NOT_FOUND, "Portfolios  not found");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, null,ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Rewrite portfolio content
	 *	PUT /rest/api/portfolios/portfolios/{portfolio-id}
	 *	parameters:
	 *	content
	 *	see GET /rest/api/portfolios/portfolio/{portfolio-id}
	 *	and/or the asm format
	 *	return:
	 **/
	@Path("/portfolios/portfolio/{portfolio-id}")
	@PUT
	@Consumes(MediaType.APPLICATION_XML)
	@Produces(MediaType.APPLICATION_XML)
	public String putPortfolio( String xmlPortfolio, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("portfolio-id") String portfolioUuid,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("active") String active, @QueryParam("user") Integer userId )
	{
		if( !isUUID(portfolioUuid) )
		{
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}

		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;
		
		try
		{
			Boolean portfolioActive;
			if("false".equals(active) || "0".equals(active))
				portfolioActive = false;
			else
				portfolioActive = true;

			c = SqlUtils.getConnection(servContext);
			dataProvider.putPortfolio(c, new MimeType("text/xml"),new MimeType("text/xml"),xmlPortfolio,portfolioUuid, ui.userId,portfolioActive, groupId,null);
			logRestRequest(httpServletRequest, xmlPortfolio, null, Status.OK.getStatusCode());

			return "";

		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, xmlPortfolio, ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Change portfolio owner
	 *	PUT /rest/api/portfolios/portfolios/{portfolio-id}/setOwner/{newOwnerId}
	 *	parameters:
	 *		- portfolio-id
	 *		- newOwnerId
	 *	return:
	 **/
	@Path("/portfolios/portfolio/{portfolio-id}/setOwner/{newOwnerId}")
	@PUT
	@Consumes(MediaType.APPLICATION_XML)
	@Produces(MediaType.APPLICATION_XML)
	public String putPortfolioOwner( String xmlPortfolio, @CookieParam("user") String user, @CookieParam("credential") String token, @PathParam("portfolio-id") String portfolioUuid, @PathParam("newOwnerId") int newOwner, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest )
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;
		boolean retval = false;
		
		try
		{
			c = SqlUtils.getConnection(servContext);
			// Check if logged user is either admin, or owner of the current portfolio
			if( credential.isAdmin(c, ui.userId) || credential.isOwner(c, ui.userId, portfolioUuid) )
			{
				retval = credential.putPortfolioOwner(c, portfolioUuid, newOwner);
				logRestRequest(httpServletRequest, xmlPortfolio, null, Status.OK.getStatusCode());
			}
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, xmlPortfolio, ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
		
		return Boolean.toString(retval);
	}

	/**
	 *	Modify some portfolio option
	 *	PUT /rest/api/portfolios/portfolios/{portfolio-id}
	 *	parameters:
	 *	- portfolio: uuid
	 *	- active:	0/1, true/false
	 *	return:
	 **/
	@Path("/portfolios")
	@PUT
	@Consumes(MediaType.APPLICATION_XML)
	@Produces(MediaType.APPLICATION_XML)
	public String putPortfolioConfiguration(String xmlPortfolio, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("portfolio") String portfolioUuid, @QueryParam("active") Boolean portfolioActive)
	{
		if( !isUUID(portfolioUuid) )
		{
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}

		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;
		
		try
		{
			String returnValue = "";
			if(portfolioUuid!=null && portfolioActive!=null)
			{
				c = SqlUtils.getConnection(servContext);
				dataProvider.putPortfolioConfiguration(c, portfolioUuid,portfolioActive, ui.userId);
			}
			logRestRequest(httpServletRequest, xmlPortfolio, returnValue, Status.OK.getStatusCode());

			return returnValue;
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, xmlPortfolio, ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Add a user
	 *	POST /rest/api/users
	 *	parameters:
	 *	content:
	 *	<users>
	 *		<user id="uid">
	 *			<username></username>
	 *			<firstname></firstname>
	 *			<lastname></lastname>
	 *			<admin>1/0</admin>
	 *			<designer>1/0</designer>
	 *			<email></email>
	 *			<active>1/0</active>
	 *			<substitute>1/0</substitute>
	 *		</user>
	 *		...
	 *	</users>
	 *
	 *	return:
	 **/
	@Path("/users")
	@POST
	@Consumes(MediaType.APPLICATION_XML)
	@Produces(MediaType.APPLICATION_XML)
	public Response postUser(String xmluser, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;

		try
		{
			c = SqlUtils.getConnection(servContext);
			String xmlUser = dataProvider.postUsers(c, xmluser, ui.userId);
			logRestRequest(httpServletRequest, "", xmlUser, Status.OK.getStatusCode());

			if( xmlUser == null )
			{
				return Response.status(Status.CONFLICT).entity("Existing user or invalid input").build();
			}
			
			return Response.ok(xmlUser).build();
		}
		catch(Exception ex)
		{
			logger.error(ex.getMessage());
			ex.printStackTrace();
			logRestRequest(httpServletRequest, xmluser, ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.BAD_REQUEST, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Unused (?)
	 *	POST /rest/api/label/{label}
	 *	parameters:
	 *	return:
	 **/
	@Deprecated
	@Path("/label/{label}")
	@POST
	@Produces(MediaType.APPLICATION_XML)
	public Response  postCredentialGroupLabel( @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest, @PathParam("label") String label)
	{
		checkCredential(httpServletRequest, user, token, null);

		try
		{
			String name = sc.getServletContext().getContextPath();

			return  Response.ok().build(); //.cookie(new NewCookie("label", label, name, null, null, 3600 /*maxAge*/, false)).build();
		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, ex.getMessage());
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, "", ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
//			dataProvider.disconnect();
		}
	}

	/**
	 *	Selecting role for current user
	 *	TODO: Was deactivated, but it might come back later on
	 *	POST /rest/api/credential/group/{group-id}
	 *	parameters:
	 *	- group: group id
	 *	return:
	 **/
	@Deprecated
	@Path("/credential/group/{group-id}")
	@POST
	@Produces(MediaType.APPLICATION_XML)
	public Response  postCredentialGroup( @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest )
	{
		HttpSession session = httpServletRequest.getSession(true);
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
//			if(dataProvider.isUserMemberOfGroup( ui.userId, groupId))
			{
//				ui.groupId = groupId.intValue();
//				session.setAttribute("gid", ui.groupId);
				return  Response.ok().build(); //.cookie(new NewCookie("group", groupId, name, null, null, 36000 /*maxAge*/, false)).build();
			}
//			else throw new RestWebApplicationException(Status.FORBIDDEN, ui.userId+" ne fait pas parti du groupe "+groupId);

		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, ex.getMessage());
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, "", ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
//			dataProvider.disconnect();
		}
	}

	/**
	 *	Add a user group
	 *	POST /rest/api/credential/group/{group-id}
	 *	parameters:
	 *	<group grid="" owner="" label=""></group>
	 *
	 *	return:
	 *	<group grid="" owner="" label=""></group>
	 **/
	@Path("group")
	@POST
	@Produces(MediaType.APPLICATION_XML)
	public String  postGroup(String xmlgroup, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;
		
		try
		{
			c = SqlUtils.getConnection(servContext);
			String xmlGroup = (String) dataProvider.postGroup(c, xmlgroup, ui.userId);
			logRestRequest(httpServletRequest, "", xmlGroup, Status.OK.getStatusCode());

			return xmlGroup;
		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, "", ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.BAD_REQUEST, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
//			dataProvider.disconnect();
		}
	}

	/**
	 *	Insert a user in a user group
	 *	POST /rest/api/groupsUsers
	 *	parameters:
	 *	-	group: gid
	 *	- userId: uid
	 *	return:
	 *	<ok/>
	 **/
	@Path("/groupsUsers")
	@POST
	@Produces(MediaType.APPLICATION_XML)
	public String  postGroupsUsers( @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("userId") int userId )
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;

		try
		{
			c = SqlUtils.getConnection(servContext);
			if(dataProvider.postGroupsUsers(c, ui.userId, userId,groupId))
			{
				return "<ok/>";
			}
			else throw new RestWebApplicationException(Status.FORBIDDEN, ui.userId+" ne fait pas parti du groupe "+groupId);

		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, "", ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Change the group right associated to a user group
	 *	POST /rest/api/RightGroup
	 *	parameters:
	 *	- group:	user group id
	 *	- groupRightId: group right id
	 *	return:
	 **/
	@Path("RightGroup")
	@POST
	@Produces(MediaType.APPLICATION_XML)
	public Response  postRightGroup( @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("groupRightId") int groupRightId )
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;

		try
		{
			c = SqlUtils.getConnection(servContext);
			if(dataProvider.postRightGroup(c, groupRightId,groupId, ui.userId))
			{
				System.out.print("ajout�");
			}
			else throw new RestWebApplicationException(Status.FORBIDDEN, ui.userId+" ne fait pas parti du groupe "+groupId);

		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, "", ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
		return null;
	}

	/**
	 *	From a base portfolio, make an instance with parsed rights in the attributes
	 *	POST /rest/api/portfolios/instanciate/{portfolio-id}
	 *	parameters:
	 *	- sourcecode: if set, rather than use the provided portfolio uuid, search for the portfolio by code
	 *	- targetcode: code we want the portfolio to have. If code already exists, adds a number after
	 *	- copyshared: y/null Make a copy of shared nodes, rather than keeping the link to the original data
	 *	- owner: true/null Set the current user instanciating the portfolio as owner. Otherwise keep the one that created it.
	 *
	 *	return:
	 *	instanciated portfolio uuid
	 **/
	@Path("/portfolios/instanciate/{portfolio-id}")
	@POST
	public Object postInstanciatePortfolio(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @PathParam("portfolio-id") String portfolioId , @QueryParam("sourcecode") String srccode, @QueryParam("targetcode") String tgtcode, @QueryParam("copyshared") String copy, @QueryParam("groupname") String groupname, @QueryParam("owner") String setowner)
	{
		/*
		if( !isUUID(portfolioId) )
		{
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}
		//*/

		String value = "Instanciate: "+portfolioId;

		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		//// TODO: IF user is creator and has parameter owner -> change ownership
		Connection c = null;

		try
		{
			boolean setOwner = false;
			if( "true".equals(setowner) )
				setOwner = true;
			boolean copyshared = false;
			if( "y".equalsIgnoreCase(copy) )
				copyshared = true;

			/// Check if code exist, find a suitable one otherwise. Eh.
			String newcode = tgtcode;
			int num = 0;
			c = SqlUtils.getConnection(servContext);
			while( dataProvider.isCodeExist(c, newcode, null) )
				newcode = tgtcode+" ("+ num++ +")";
			tgtcode = newcode;

			String returnValue = dataProvider.postInstanciatePortfolio(c, new MimeType("text/xml"),portfolioId, srccode, tgtcode, ui.userId, groupId, copyshared, groupname, setOwner).toString();
			logRestRequest(httpServletRequest, value+" to: "+returnValue, returnValue, Status.OK.getStatusCode());

			if( returnValue.startsWith("no rights") )
				throw new RestWebApplicationException(Status.FORBIDDEN, returnValue);
			else if( returnValue.startsWith("erreur") )
				throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, returnValue);
			else if( "".equals(returnValue) )
			{
				return Response.status(Status.NOT_FOUND).build();
			}

			return returnValue;
		}
		catch(Exception ex)
		{
			logger.error(ex.getMessage());
			ex.printStackTrace();
			logRestRequest(httpServletRequest, value+" --> Error", ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	From a base portfolio, just make a direct copy without rights parsing
	 *	POST /rest/api/portfolios/copy/{portfolio-id}
	 *	parameters:
	 *	Same as in instanciate
	 *	return:
	 *	Same as in instanciate
	 **/
	@Path("/portfolios/copy/{portfolio-id}")
	@POST
	public Response postCopyPortfolio(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @PathParam("portfolio-id") String portfolioId , @QueryParam("sourcecode") String srccode, @QueryParam("targetcode") String tgtcode, @QueryParam("owner") String setowner )
	{
		/*
		if( !isUUID(portfolioId) )
		{
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}
		//*/

		String value = "Instanciate: "+portfolioId;

		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		//// TODO: IF user is creator and has parameter owner -> change ownership
		Connection c = null;
		try
		{
			boolean setOwner = false;
			if( "true".equals(setowner) )
				setOwner = true;

			/// Check if code exist, find a suitable one otherwise. Eh.
			String newcode = tgtcode;
			c = SqlUtils.getConnection(servContext);
			if( dataProvider.isCodeExist(c, newcode, null) )
			{
				return Response.status(Status.CONFLICT).entity("code exist").build();
			}

			tgtcode = newcode;

			String returnValue = dataProvider.postCopyPortfolio(c, new MimeType("text/xml"),portfolioId, srccode, tgtcode, ui.userId, setOwner ).toString();
			logRestRequest(httpServletRequest, value+" to: "+returnValue, returnValue, Status.OK.getStatusCode());

			return Response.status(Status.OK).entity(returnValue).build();
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, value+" --> Error", ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	As a form, import xml into the database
	 *	POST /rest/api/portfolios
	 *	parameters:
	 *	return:
	 **/
	@Path("/portfolios")
	@POST
//	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Produces(MediaType.APPLICATION_XML)
	public String postFormPortfolio(@FormDataParam("uploadfile") String xmlPortfolio, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("user") Integer userId, @QueryParam("model") String modelId, @QueryParam("srce") String srceType, @QueryParam("srceurl") String srceUrl, @QueryParam("xsl") String xsl, @FormDataParam("instance") String instance, @FormDataParam("project") String projectName )
	{
		return postPortfolio(xmlPortfolio, user, token, groupId, sc, httpServletRequest, userId, modelId, srceType, srceUrl, xsl, instance, projectName);
	}

	/**
	 *	As a form, import xml into the database
	 *	POST /rest/api/portfolios
	 *	parameters:
	 *	- model: another uuid, not sure why it's here
	 *	- srce: sakai/null	Need to be logged in on sakai first
	 *	- srceurl: url part of the sakai system to fetch
	 *	- xsl: filename when using with sakai source, convert data before importing it
	 *	- instance: true/null if as an instance, parse rights. Otherwise just write nodes
	 *	xml: ASM format
	 *	return:
	 *	<portfolios>
	 *		<portfolio id="uuid"/>
	 *	</portfolios>
	 **/
	@Path("/portfolios")
	@POST
	@Consumes(MediaType.APPLICATION_XML)
//	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Produces(MediaType.APPLICATION_XML)
	public String postPortfolio(String xmlPortfolio, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("user") Integer userId, @QueryParam("model") String modelId, @QueryParam("srce") String srceType, @QueryParam("srceurl") String srceUrl, @QueryParam("xsl") String xsl, @QueryParam("instance") String instance, @QueryParam("project") String projectName )
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		if( "sakai".equals(srceType) )
		{
			/// Session Sakai
			HttpSession session = httpServletRequest.getSession(false);
			if( session != null )
			{
				String sakai_session = (String) session.getAttribute("sakai_session");
				String sakai_server = (String) session.getAttribute("sakai_server");	// Base server http://localhost:9090

				HttpClient client = new HttpClient();

				/// Fetch page
				GetMethod get = new GetMethod(sakai_server+"/"+srceUrl);
				Header header = new Header();
				header.setName("JSESSIONID");
				header.setValue(sakai_session);
				get.setRequestHeader(header);

				try
				{
					int status = client.executeMethod(get);
					if (status != HttpStatus.SC_OK) {
						System.err.println("Method failed: " + get.getStatusLine());
					}

					// Retrieve data
					InputStream retrieve = get.getResponseBodyAsStream();
					String sakaiData = IOUtils.toString(retrieve, "UTF-8");

					//// Convert it via XSL
					/// Path to XSL
					String servletDir = sc.getServletContext().getRealPath("/");
					int last = servletDir.lastIndexOf(File.separator);
					last = servletDir.lastIndexOf(File.separator, last-1);
					String baseDir = servletDir.substring(0, last);

					String basepath = xsl.substring(0,xsl.indexOf(File.separator));
					String firstStage = baseDir+File.separator+basepath+File.separator+"karuta"+File.separator+"xsl"+File.separator+"html2xml.xsl";
					System.out.println("FIRST: "+firstStage);

					/// Storing transformed data
					StringWriter dataTransformed = new StringWriter();

					/// Apply change
					Source xsltSrc1 = new StreamSource(new File(firstStage));
					TransformerFactory transFactory = TransformerFactory.newInstance();
					Transformer transformer1 = transFactory.newTransformer(xsltSrc1);
					StreamSource stageSource = new StreamSource(new ByteArrayInputStream( sakaiData.getBytes() ) );
					Result stageRes = new StreamResult(dataTransformed);
					transformer1.transform(stageSource, stageRes);

					/// Result as portfolio data to be imported
					xmlPortfolio = dataTransformed.toString();
				}
				catch( HttpException e )
				{
					e.printStackTrace();
				}
				catch( IOException e )
				{
					e.printStackTrace();
				}
				catch( TransformerConfigurationException e )
				{
					e.printStackTrace();
				}
				catch( TransformerException e )
				{
					e.printStackTrace();
				}
			}
		}

		Connection c = null;
		try
		{
			boolean instantiate = false;
			if( "true".equals(instance) )
				instantiate = true;

			c = SqlUtils.getConnection(servContext);
			String returnValue = dataProvider.postPortfolio(c, new MimeType("text/xml"),new MimeType("text/xml"),xmlPortfolio, ui.userId, groupId, modelId, ui.subId, instantiate, projectName).toString();
			logRestRequest(httpServletRequest, xmlPortfolio, returnValue, Status.OK.getStatusCode());

			return returnValue;
		}
		catch(RestWebApplicationException ex)
		{
			logRestRequest(httpServletRequest, null, null, ex.getResponse().getStatus());

			throw new RestWebApplicationException(ex.getStatus(), ex.getCustomMessage());
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, xmlPortfolio, ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Return a list of portfolio shared to a user
	 *	GET /portfolios/shared/{userid}
	 *	parameters:
	 *	return:
	 **/
	@Path("/portfolios/shared/{userid}")
	@POST
	@Produces(MediaType.APPLICATION_XML)
	public Response  getPortfolioShared( @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest, @PathParam("userid") int userid)
	{
		UserInfo uinfo = checkCredential(httpServletRequest, user, token, null);

		Connection c = null;
		try
		{
			c = SqlUtils.getConnection(servContext);
			if( credential.isAdmin(c, uinfo.userId) )
			{
				String res = dataProvider.getPortfolioShared(c, uinfo.userId, userid);
				return  Response.ok(res).build();
			}
			else
			{
				return Response.status(403).build();
			}
		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, ex.getMessage());
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, "", ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}


	// GET /portfolios/zip ? portfolio={}, toujours avec files
	// zip s�par�s
	// zip des zip
	/**
	 *	Fetching multiple portfolio in a zip
	 *	GET /rest/api/portfolios
	 *	parameters:
	 *	portfolio: separated with ','
	 *	return:
	 *	zipped portfolio (with files) inside zip file
	 **/
	@Path("/portfolios/zip")
	@GET
	@Consumes("application/zip")	// Envoie donn�e brut
	public Object getPortfolioZip(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("portfolios") String portfolioList, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("user") Integer userId, @QueryParam("model") String modelId, @QueryParam("instance") String instance, @QueryParam("lang") String lang )
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;

		try
		{
			HttpSession session = httpServletRequest.getSession(false);
			String [] list = portfolioList.split(",");
			File[] files = new File[list.length];

			c = SqlUtils.getConnection(servContext);
			
			/// Suppose the first portfolio has the right name to be used
			
			String name = "";
			
			/// Create all the zip files
			for( int i=0; i<list.length; ++i )
			{
				String portfolioUuid = list[i];
				String portfolio = dataProvider.getPortfolio(c, new MimeType("text/xml"), portfolioUuid, ui.userId, 0, this.label, "true", "", ui.subId, null).toString();
	
				// No name yet
				if( "".equals(name) )
				{
					StringBuffer outTrace = new StringBuffer();
					Document doc = DomUtils.xmlString2Document(portfolio, outTrace);
					XPath xPath = XPathFactory.newInstance().newXPath();
					String filterRes = "//*[local-name()='asmRoot']/*[local-name()='asmResource']/*[local-name()='code']";
					NodeList nodelist = (NodeList) xPath.compile(filterRes).evaluate(doc, XPathConstants.NODESET);

					if( nodelist.getLength() > 0 )
						name = nodelist.item(0).getTextContent();
				}
				
				Document doc = DomUtils.xmlString2Document(portfolio, new StringBuffer());
				
				files[i] = getZipFile(portfolioUuid, portfolio, lang, doc, session);
				
			}
			
			// Make a big zip of it
			File tempDir = new File(System.getProperty("java.io.tmpdir", null));
			File bigZip = File.createTempFile("project_", ".zip", tempDir);
			
			// Add content to it
			FileOutputStream fos = new FileOutputStream(bigZip);
			ZipOutputStream zos = new ZipOutputStream(fos);

			byte[] buffer = new byte[0x1000];
			
			for( int i=0; i<files.length; ++i )
			{
				File file = files[i];
				FileInputStream fis = new FileInputStream(file);
				String filename = file.getName();
				
				/// Write xml file to zip
				ZipEntry ze = new ZipEntry(filename+".zip");
				zos.putNextEntry(ze);
				int read = 1;
				while( read > 0 )
				{
					read = fis.read(buffer);
					zos.write(buffer);
				}
				zos.closeEntry();
			}
			zos.close();

			/// Return zip file
			RandomAccessFile f = new RandomAccessFile(bigZip.getAbsoluteFile(), "r");
			byte[] b = new byte[(int)f.length()];
			f.read(b);
			f.close();

			Date time = new Date();
			SimpleDateFormat dt = new SimpleDateFormat("yyyy-MM-dd HHmmss");
			String timeFormat = dt.format(time);

			Response response = Response
					.ok(b, MediaType.APPLICATION_OCTET_STREAM)
					.header("content-disposition","attachment; filename = \""+name+"-"+timeFormat+".zip\"")
					.build();

			// Delete all zipped file
			for( int i=0; i<files.length; ++i )
				files[i].delete();
			
			// And the over-arching zip
			bigZip.delete();

			return response;
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), modelId, Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	
	/**
	 *	As a form, import zip, extract data and put everything into the database
	 *	POST /rest/api/portfolios
	 *	parameters:
	 *	zip: From a zip export of the system
	 *	return:
	 *	portfolio uuid
	 **/
	@Path("/portfolios/zip")
	@POST
	@Consumes(MediaType.MULTIPART_FORM_DATA)
//	@Consumes("application/zip")	// Envoie donn�e brut
	public String postPortfolioZip(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @FormDataParam("fileupload") InputStream fileInputStream, @QueryParam("user") Integer userId, @QueryParam("model") String modelId, @FormDataParam("instance") String instance, @FormDataParam("project") String projectName)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;

		try
		{
			boolean instantiate = false;
			if( "true".equals(instance) )
				instantiate = true;

			c = SqlUtils.getConnection(servContext);
			String returnValue = dataProvider.postPortfolioZip(c, new MimeType("text/xml"),new MimeType("text/xml"), httpServletRequest, fileInputStream, ui.userId, groupId, modelId, ui.subId, instantiate, projectName).toString();
			logRestRequest(httpServletRequest, returnValue, returnValue, Status.OK.getStatusCode());

			return returnValue;
		}
		catch( RestWebApplicationException e )
		{
			throw e;
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), modelId, Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Delete portfolio
	 *	DELETE /rest/api/portfolios/portfolio/{portfolio-id}
	 *	parameters:
	 *	return:
	 **/
	@Path("/portfolios/portfolio/{portfolio-id}")
	@DELETE
	@Produces(MediaType.APPLICATION_XML)
	public String deletePortfolio(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("portfolio-id") String portfolioUuid,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("user") Integer userId )
	{
		if( !isUUID(portfolioUuid) )
		{
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}

		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;

		try
		{
			c = SqlUtils.getConnection(servContext);
			Integer nbPortfolioDeleted = Integer.parseInt(dataProvider.deletePortfolio(c, portfolioUuid, ui.userId, groupId).toString());
			if(nbPortfolioDeleted==0)
			{
				logRestRequest(httpServletRequest, null,null, Status.NOT_FOUND.getStatusCode());

				throw new RestWebApplicationException(Status.NOT_FOUND, "Portfolio "+portfolioUuid+" not found");
			}
			logRestRequest(httpServletRequest, null, null, Status.OK.getStatusCode());

			return "";

		}
		catch(RestWebApplicationException ex)
		{
			logRestRequest(httpServletRequest, null, null, Status.NOT_FOUND.getStatusCode());

			throw new RestWebApplicationException(Status.NOT_FOUND, "Portfolio "+portfolioUuid+" not found");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, null,ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Get a node, without children
	 *	FIXME: Check if it's the case
	 *	GET /rest/api/nodes/node/{node-id}
	 *	parameters:
	 *	return:
	 *	nodes in the ASM format
	 **/
	@Path("/nodes/node/{node-id}")
	@GET
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Consumes(MediaType.APPLICATION_XML)
	public String getNode( @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("node-id") String nodeUuid,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept, @QueryParam("user") Integer userId, @QueryParam("level") Integer cutoff )
	{
		if( !isUUID(nodeUuid) )
		{
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}

		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;

		try
		{
			c = SqlUtils.getConnection(servContext);
			String returnValue = dataProvider.getNode(c, new MimeType("text/xml"),nodeUuid,false, ui.userId, groupId, this.label, cutoff).toString();
			if(returnValue == null)
				throw new RestWebApplicationException(Status.NOT_FOUND, "Node "+nodeUuid+" not found");
			if(returnValue.length() != 0)
			{
				if(accept.equals(MediaType.APPLICATION_JSON))
					returnValue = XML.toJSONObject(returnValue).toString();
				logRestRequest(httpServletRequest, null, returnValue, Status.OK.getStatusCode());
			}
			else
			{
				throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
			}


			return returnValue;
		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
		}
		catch(SQLException ex)
		{
			logRestRequest(httpServletRequest, null,null, Status.NOT_FOUND.getStatusCode());
			ex.printStackTrace();
			throw new RestWebApplicationException(Status.NOT_FOUND, "Node "+nodeUuid+" not found");
		}
		catch(NullPointerException ex)
		{
			logRestRequest(httpServletRequest, null,null, Status.NOT_FOUND.getStatusCode());
			ex.printStackTrace();
			throw new RestWebApplicationException(Status.NOT_FOUND, "Node "+nodeUuid+" not found");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, null, ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Fetch nodes and childrens from node uuid
	 *	GET /rest/api/nodes/node/{node-id}/children
	 *	parameters:
	 *	return:
	 *	nodes in the ASM format
	 **/
	@Path("/nodes/node/{node-id}/children")
	@GET
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Consumes(MediaType.APPLICATION_XML)
	public String getNodeWithChildren( @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("node-id") String nodeUuid,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept, @QueryParam("user") Integer userId, @QueryParam("level") Integer cutoff )
	{
		if( !isUUID(nodeUuid) )
		{
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}
		
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;

		try
		{
			c = SqlUtils.getConnection(servContext);
			String returnValue = dataProvider.getNode(c, new MimeType("text/xml"),nodeUuid,true, ui.userId, groupId, this.label, cutoff).toString();
			if( returnValue == null )
				throw new RestWebApplicationException(Status.NOT_FOUND, "Node "+nodeUuid+" not found");
			if(returnValue.length() != 0)
			{
				if(accept.equals(MediaType.APPLICATION_JSON))
					returnValue = XML.toJSONObject(returnValue).toString();
				logRestRequest(httpServletRequest, null, returnValue, Status.OK.getStatusCode());

				return returnValue;
			}
			else
			{
				throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
			}
		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
		}
		catch(SQLException ex)
		{
			logRestRequest(httpServletRequest, null,null, Status.NOT_FOUND.getStatusCode());

			throw new RestWebApplicationException(Status.NOT_FOUND, "Node "+nodeUuid+" not found");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, null,ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Fetch nodes metdata
	 *	GET /rest/api/nodes/node/{node-id}/metadatawad
	 *	parameters:
	 *	return:
	 *	<metadata-wad/>
	 **/
	@Path("/nodes/node/{nodeid}/metadatawad")
	@GET
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Consumes(MediaType.APPLICATION_XML)
	public String getNodeMetadataWad( @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("nodeid") String nodeUuid,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept, @QueryParam("user") Integer userId )
	{
		if( !isUUID(nodeUuid) )
		{
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}

		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;

		try
		{
			c = SqlUtils.getConnection(servContext);
			String returnValue = dataProvider.getNodeMetadataWad(c, new MimeType("text/xml"),nodeUuid,true, ui.userId, groupId, this.label).toString();
			if(returnValue.length() != 0)
			{
				if(accept.equals(MediaType.APPLICATION_JSON))
					returnValue = XML.toJSONObject(returnValue).toString();
				logRestRequest(httpServletRequest, null, returnValue, Status.OK.getStatusCode());

				return returnValue;
			}
			else
			{
				throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
			}
		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, null,ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Fetch rights per role for a node
	 *	GET /rest/api/nodes/node/{node-id}/rights
	 *	parameters:
	 *	return:
	 *	<node uuid="">
	 *		<role name="">
	 *			<right RD="" WR="" DL="" />
	 *		</role>
	 *	</node>
	 **/
	@Path("/nodes/node/{node-id}/rights")
	@GET
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Consumes(MediaType.APPLICATION_XML)
	public String getNodeRights( @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("node-id") String nodeUuid,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept, @QueryParam("user") Integer userId )
	{
		if( !isUUID(nodeUuid) )
		{
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}

		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;

		try
		{
			c = SqlUtils.getConnection(servContext);
			String returnValue = dataProvider.getNodeRights(c, nodeUuid, ui.userId, groupId);
			if(returnValue.length() != 0)
			{
				if(accept.equals(MediaType.APPLICATION_JSON))
					returnValue = XML.toJSONObject(returnValue).toString();
				logRestRequest(httpServletRequest, null, returnValue, Status.OK.getStatusCode());
			}
			else
			{
				throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
			}

			return returnValue;
		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
		}
		catch(SQLException ex)
		{
			logRestRequest(httpServletRequest, null,null, Status.NOT_FOUND.getStatusCode());

			throw new RestWebApplicationException(Status.NOT_FOUND, "Node "+nodeUuid+" not found");
		}
		catch(NullPointerException ex)
		{
			logRestRequest(httpServletRequest, null,null, Status.NOT_FOUND.getStatusCode());

			throw new RestWebApplicationException(Status.NOT_FOUND, "Node "+nodeUuid+" not found");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, null, ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Fetch portfolio id from a given node id
	 *	GET /rest/api/nodes/node/{node-id}/portfolioid
	 *	parameters:
	 *	return:
	 *		portfolioid
	 **/
	@Path("/nodes/node/{node-id}/portfolioid")
	@GET
	@Produces(MediaType.TEXT_PLAIN)
	public String getNodePortfolioId( @CookieParam("user") String user, @CookieParam("credential") String token, @PathParam("node-id") String nodeUuid, @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept )
	{
		if( !isUUID(nodeUuid) )
		{
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}

		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;

		try
		{
			c = SqlUtils.getConnection(servContext);
			// Admin, or if user has a right to read can fetch this information
			if( !credential.isAdmin(c, ui.userId) && !credential.hasNodeRight(c, ui.userId, 0, nodeUuid, Credential.READ) )
			{
				throw new RestWebApplicationException(Status.FORBIDDEN, "No rights");
			}
			
			String returnValue = dataProvider.getNodePortfolioId( c, nodeUuid );
			if(returnValue.length() != 0)
			{
				return returnValue;
			}
			else
			{
				throw new RestWebApplicationException(Status.NOT_FOUND, "Error, shouldn't happen.");
			}
		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(ex.getStatus(), ex.getResponse().getEntity().toString());
		}
		catch(SQLException ex)
		{
			throw new RestWebApplicationException(Status.NOT_FOUND, "Node "+nodeUuid+" not found");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Change nodes right
	 *	POST /rest/api/nodes/node/{node-id}/rights
	 *	parameters:
	 *	content:
	 *	<node uuid="">
	 *		<role name="">
	 *			<right RD="" WR="" DL="" />
	 *		</role>
	 *	</node>
	 *
	 *	return:
	 **/
	@Path("/nodes/node/{node-id}/rights")
	@POST
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Consumes(MediaType.APPLICATION_XML)
	public String postNodeRights( String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("node-id") String nodeUuid,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept, @QueryParam("user") Integer userId )
	{
		if( !isUUID(nodeUuid) )
		{
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}

		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;

		try
		{
			DocumentBuilderFactory documentBuilderFactory =DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
			Document doc = documentBuilder.parse(new ByteArrayInputStream(xmlNode.getBytes("UTF-8")));

			XPath xPath = XPathFactory.newInstance().newXPath();
//			String xpathRole = "//role";
			String xpathRole = "//*[local-name()='role']";
			XPathExpression findRole = xPath.compile(xpathRole);
			NodeList roles = (NodeList) findRole.evaluate(doc, XPathConstants.NODESET);

			c = SqlUtils.getConnection(servContext);

			/// For all roles we have to change
			for( int i=0; i<roles.getLength(); ++i )
			{
				Node rolenode = roles.item(i);
				String rolename = rolenode.getAttributes().getNamedItem("name").getNodeValue();
				Node right = rolenode.getFirstChild();

				//
				if( "user".equals(rolename) )
				{
					/// username as role
				}

				if( "#text".equals(right.getNodeName()) )
					right = right.getNextSibling();

				if( "right".equals(right.getNodeName()) )	// Changing node rights
				{
					NamedNodeMap rights = right.getAttributes();

					NodeRight noderight = new NodeRight(null,null,null,null,null,null);

					String val = rights.getNamedItem("RD").getNodeValue();
					if( val != null )
						noderight.read = "Y".equals(val) ? true: false;
					val = rights.getNamedItem("WR").getNodeValue();
					if( val != null )
						noderight.write = "Y".equals(val) ? true: false;
					val = rights.getNamedItem("DL").getNodeValue();
					if( val != null )
						noderight.delete = "Y".equals(val) ? true: false;
					val = rights.getNamedItem("SB").getNodeValue();
					if( val != null )
						noderight.submit = "Y".equals(val) ? true: false;

					// change right
					dataProvider.postRights(c, ui.userId, nodeUuid, rolename, noderight);
				}
				else if( "action".equals(right.getNodeName()) )	// Using an action on node
				{
					// reset right
					dataProvider.postMacroOnNode(c, ui.userId, nodeUuid, "reset");
				}
			}

			logRestRequest(httpServletRequest, xmlNode, "Change rights", Status.OK.getStatusCode());
		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
		}
		catch(NullPointerException ex)
		{
			logRestRequest(httpServletRequest, null,null, Status.NOT_FOUND.getStatusCode());

			throw new RestWebApplicationException(Status.NOT_FOUND, "Node "+nodeUuid+" not found");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, null, ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}

		return "";
	}

	/**
	 *	Get the single first semantic tag node inside specified portfolio
	 *	GET /rest/api/nodes/firstbysemantictag/{portfolio-uuid}/{semantictag}
	 *	parameters:
	 *	return:
	 *	node in ASM format
	 **/
	@Path("/nodes/firstbysemantictag/{portfolio-uuid}/{semantictag}")
	@GET
	@Produces({MediaType.APPLICATION_XML})
	@Consumes(MediaType.APPLICATION_XML)
	public String getNodeBySemanticTag( @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("portfolio-uuid") String portfolioUuid,@PathParam("semantictag") String semantictag,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept)
	{
		if( !isUUID(portfolioUuid) )
		{
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}

		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;

		try
		{
			c = SqlUtils.getConnection(servContext);
			String returnValue = dataProvider.getNodeBySemanticTag(c, new MimeType("text/xml"),portfolioUuid,semantictag, ui.userId, groupId).toString();
			if(returnValue.length() != 0)
			{
				logRestRequest(httpServletRequest, null, returnValue, Status.OK.getStatusCode());

				return returnValue;
			}
			else
			{
				throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
			}
		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, null,ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Get multiple semantic tag nodes inside specified portfolio
	 *	GET /rest/api/nodes/nodes/bysemantictag/{portfolio-uuid}/{semantictag}
	 *	parameters:
	 *	return:
	 *	nodes in ASM format
	 **/
	@Path("/nodes/bysemantictag/{portfolio-uuid}/{semantictag}")
	@GET
	@Produces({MediaType.APPLICATION_XML})
	@Consumes(MediaType.APPLICATION_XML)
	public String getNodesBySemanticTag( @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("portfolio-uuid") String portfolioUuid,@PathParam("semantictag") String semantictag,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept)
	{
		if( !isUUID(portfolioUuid) )
		{
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}

		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;

		try
		{
			c = SqlUtils.getConnection(servContext);
			String returnValue = dataProvider.getNodesBySemanticTag(c, new MimeType("text/xml"), ui.userId, groupId,portfolioUuid,semantictag).toString();
			if(returnValue.length() != 0)
			{
				logRestRequest(httpServletRequest, null, returnValue, Status.OK.getStatusCode());

				return returnValue;
			}
			else
			{

				throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
			}
		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, null,ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Rewrite node
	 *	PUT /rest/api/nodes/node/{node-id}
	 *	parameters:
	 *	return:
	 **/
	@Path("/nodes/node/{node-id}")
	@PUT
	@Produces(MediaType.APPLICATION_XML)
	public String putNode(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("node-id") String nodeUuid,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("user") Integer userId )
	{
		if( !isUUID(nodeUuid) )
		{
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}

		//		long t_startRest = System.nanoTime();
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;

//		long t_checkCred = System.nanoTime();

		try
		{
			c = SqlUtils.getConnection(servContext);
			String returnValue = dataProvider.putNode(c, new MimeType("text/xml"),nodeUuid,xmlNode, ui.userId, groupId).toString();

//			long t_query = System.nanoTime();

//			long d_cred = t_checkCred - t_startRest;
//			long d_query = t_query - t_checkCred;

//			System.out.println("Check credential: "+d_cred);
//			System.out.println("Do query: "+d_query);

			if(returnValue.equals("faux")){

				throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
			}
			logRestRequest(httpServletRequest, xmlNode, returnValue, Status.OK.getStatusCode());

			return returnValue;
		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
		}
		catch(SQLException ex)
		{
			logRestRequest(httpServletRequest, xmlNode,null, Status.NOT_FOUND.getStatusCode());

			throw new RestWebApplicationException(Status.NOT_FOUND, "Node "+nodeUuid+" not found");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, xmlNode,ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Rewrite node metadata
	 *	PUT /rest/api/nodes/node/{node-id}/metadata
	 *	parameters:
	 *	return:
	 **/
	@Path("/nodes/node/{nodeid}/metadata")
	@PUT
	@Produces(MediaType.APPLICATION_XML)
	public String putNodeMetadata(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @QueryParam("info") String info, @PathParam("nodeid") String nodeUuid,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		if( !isUUID(nodeUuid) )
		{
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}

		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;
		Date time = new Date();
		SimpleDateFormat dt = new SimpleDateFormat("yyyy-MM-dd HHmmss");
		String timeFormat = dt.format(time);
		String logformat = "";
		if( "false".equals(info) )
			logformat = logFormatShort;
		else
			logformat = logFormat;

		try
		{
			c = SqlUtils.getConnection(servContext);
			String returnValue = dataProvider.putNodeMetadata(c, new MimeType("text/xml"),nodeUuid,xmlNode, ui.userId, groupId).toString();
			
			if(returnValue.equals("faux")){
				errorLog.write(String.format(logformat, "ERR", nodeUuid, "metadata", ui.userId, timeFormat, httpServletRequest.getRemoteAddr(), xmlNode ));
				errorLog.flush();
				editLog.write(String.format(logformat, "ERR", nodeUuid, "metadata", ui.userId, timeFormat, httpServletRequest.getRemoteAddr(), xmlNode ));
				editLog.flush();
				throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
			}
			logRestRequest(httpServletRequest, xmlNode, returnValue, Status.OK.getStatusCode());
			if( editLog!= null )
			{
				editLog.write(String.format(logformat, "OK", nodeUuid, "metadata", ui.userId, timeFormat, httpServletRequest.getRemoteAddr(), xmlNode ));
				editLog.flush();
			}
			
			return returnValue;
		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
		}
		catch(SQLException ex)
		{
			logRestRequest(httpServletRequest, xmlNode,null, Status.NOT_FOUND.getStatusCode());

			throw new RestWebApplicationException(Status.NOT_FOUND, "Node "+nodeUuid+" not found");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, xmlNode,ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Rewrite node wad metadata
	 *	PUT /rest/api/nodes/node/{node-id}/metadatawas
	 *	parameters:
	 *	return:
	 **/
	@Path("/nodes/node/{nodeid}/metadatawad")
	@PUT
	@Produces(MediaType.APPLICATION_XML)
	public String putNodeMetadataWad(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @QueryParam("info") String info, @PathParam("nodeid") String nodeUuid,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		if( !isUUID(nodeUuid) )
		{
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}

		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;
		Date time = new Date();
		SimpleDateFormat dt = new SimpleDateFormat("yyyy-MM-dd HHmmss");
		String timeFormat = dt.format(time);
		String logformat = "";
		if( "false".equals(info) )
			logformat = logFormatShort;
		else
			logformat = logFormat;

		try
		{
			c = SqlUtils.getConnection(servContext);
			String returnValue = dataProvider.putNodeMetadataWad(c, new MimeType("text/xml"),nodeUuid,xmlNode, ui.userId, groupId).toString();
			if(returnValue.equals("faux")){
				errorLog.write(String.format(logformat, "ERR", nodeUuid, "metadatawad", ui.userId, timeFormat, httpServletRequest.getRemoteAddr(), xmlNode ));
				errorLog.flush();
				editLog.write(String.format(logformat, "ERR", nodeUuid, "metadatawad", ui.userId, timeFormat, httpServletRequest.getRemoteAddr(), xmlNode ));
				editLog.flush();
				throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
			}
			logRestRequest(httpServletRequest, xmlNode, returnValue, Status.OK.getStatusCode());
			if( editLog!= null )
			{
				editLog.write(String.format(logformat, "OK", nodeUuid, "metadatawad", ui.userId, timeFormat, httpServletRequest.getRemoteAddr(), xmlNode ));
				editLog.flush();
			}

			return returnValue;
		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
		}
		catch(SQLException ex)
		{
			logRestRequest(httpServletRequest, xmlNode,null, Status.NOT_FOUND.getStatusCode());

			throw new RestWebApplicationException(Status.NOT_FOUND, "Node "+nodeUuid+" not found");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, xmlNode,ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Rewrite node epm metadata
	 *	PUT /rest/api/nodes/node/{node-id}/metadataepm
	 *	parameters:
	 *	return:
	 **/
	@Path("/nodes/node/{nodeid}/metadataepm")
	@PUT
	@Produces(MediaType.APPLICATION_XML)
	public String putNodeMetadataEpm(String xmlNode, @PathParam("nodeid") String nodeUuid, @QueryParam("group") int groupId, @QueryParam("info") String info, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		if( !isUUID(nodeUuid) )
		{
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}

		UserInfo ui = checkCredential(httpServletRequest, null, null, null);
		Connection c = null;
		Date time = new Date();
		SimpleDateFormat dt = new SimpleDateFormat("yyyy-MM-dd HHmmss");
		String timeFormat = dt.format(time);
		String logformat = "";
		if( "false".equals(info) )
			logformat = logFormatShort;
		else
			logformat = logFormat;

		try
		{
			c = SqlUtils.getConnection(servContext);
			String returnValue = dataProvider.putNodeMetadataEpm(c, new MimeType("text/xml"),nodeUuid,xmlNode, ui.userId, groupId).toString();
			if(returnValue.equals("faux")){
				errorLog.write(String.format(logformat, "ERR", nodeUuid, "metadataepm", ui.userId, timeFormat, httpServletRequest.getRemoteAddr(), xmlNode ));
				errorLog.flush();
				editLog.write(String.format(logformat, "ERR", nodeUuid, "metadataepm", ui.userId, timeFormat, httpServletRequest.getRemoteAddr(), xmlNode ));
				editLog.flush();
				throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
			}
			if( "erreur".equals(returnValue) )
			{
				errorLog.write(String.format(logformat, "NMOD", nodeUuid, "metadataepm", ui.userId, timeFormat, httpServletRequest.getRemoteAddr(), xmlNode ));
				errorLog.flush();
				editLog.write(String.format(logformat, "NMOD", nodeUuid, "metadataepm", ui.userId, timeFormat, httpServletRequest.getRemoteAddr(), xmlNode ));
				editLog.flush();
				throw new RestWebApplicationException(Status.NOT_MODIFIED, "Erreur");
			}

			if( editLog!= null )
			{
				editLog.write(String.format(logformat, "OK", nodeUuid, "metadataepm", ui.userId, timeFormat, httpServletRequest.getRemoteAddr(), xmlNode ));
				editLog.flush();
			}
			logRestRequest(httpServletRequest, xmlNode, returnValue, Status.OK.getStatusCode());

			return returnValue;
		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
		}
		catch(SQLException ex)
		{
			logRestRequest(httpServletRequest, xmlNode,null, Status.NOT_FOUND.getStatusCode());

			throw new RestWebApplicationException(Status.NOT_FOUND, "Node "+nodeUuid+" not found");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, xmlNode,ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Rewrite node nodecontext
	 *	PUT /rest/api/nodes/node/{node-id}/nodecontext
	 *	parameters:
	 *	return:
	 **/
	@Path("/nodes/node/{nodeid}/nodecontext")
	@PUT
	@Produces(MediaType.APPLICATION_XML)
	public String putNodeNodeContext(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @QueryParam("info") String info, @PathParam("nodeid") String nodeUuid,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		if( !isUUID(nodeUuid) )
		{
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}

		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;
		Date time = new Date();
		SimpleDateFormat dt = new SimpleDateFormat("yyyy-MM-dd HHmmss");
		String timeFormat = dt.format(time);
		String logformat = "";
		if( "false".equals(info) )
			logformat = logFormatShort;
		else
			logformat = logFormat;

		try
		{
			c = SqlUtils.getConnection(servContext);
			String returnValue = dataProvider.putNodeNodeContext(c, new MimeType("text/xml"),nodeUuid,xmlNode, ui.userId, groupId).toString();
			if(returnValue.equals("faux")){
				errorLog.write(String.format(logformat, "ERR", nodeUuid, "nodecontext", ui.userId, timeFormat, httpServletRequest.getRemoteAddr(), xmlNode ));
				errorLog.flush();
				editLog.write(String.format(logformat, "ERR", nodeUuid, "nodecontext", ui.userId, timeFormat, httpServletRequest.getRemoteAddr(), xmlNode ));
				editLog.flush();
				throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
			}
			logRestRequest(httpServletRequest, xmlNode, returnValue, Status.OK.getStatusCode());
			if( editLog!= null )
			{
				editLog.write(String.format(logformat, "OK", nodeUuid, "nodecontext", ui.userId, timeFormat, httpServletRequest.getRemoteAddr(), xmlNode ));
				editLog.flush();
			}

			return returnValue;
		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, xmlNode,ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Rewrite node resource
	 *	PUT /rest/api/nodes/node/{node-id}/noderesource
	 *	parameters:
	 *	return:
	 **/
	@Path("/nodes/node/{nodeid}/noderesource")
	@PUT
	@Produces(MediaType.APPLICATION_XML)
	public String putNodeNodeResource(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @QueryParam("info") String info, @PathParam("nodeid") String nodeUuid,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		if( !isUUID(nodeUuid) )
		{
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}

		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;
		Date time = new Date();
		SimpleDateFormat dt = new SimpleDateFormat("yyyy-MM-dd HHmmss");
		String timeFormat = dt.format(time);
		String logformat = "";
		if( "false".equals(info) )
			logformat = logFormatShort;
		else
			logformat = logFormat;

		try
		{
			/// Branchement pour l'interpr�tation du contenu, besoin de v�rifier les limitations ?
			//xmlNode = xmlNode.getBytes("UTF-8").toString();
			/// putNode(MimeType inMimeType, String nodeUuid, String in,int userId, int groupId)
			//          String returnValue = dataProvider.putNode(new MimeType("text/xml"),nodeUuid,xmlNode,this.userId,this.groupId).toString();
			c = SqlUtils.getConnection(servContext);
			String returnValue = dataProvider.putNodeNodeResource(c, new MimeType("text/xml"),nodeUuid,xmlNode, ui.userId, groupId).toString();
			if(returnValue.equals("faux")){
				errorLog.write(String.format(logformat, "ERR", nodeUuid, "noderesource", ui.userId, timeFormat, httpServletRequest.getRemoteAddr(), xmlNode ));
				errorLog.flush();
				editLog.write(String.format(logformat, "ERR", nodeUuid, "noderesource", ui.userId, timeFormat, httpServletRequest.getRemoteAddr(), xmlNode ));
				editLog.flush();
				throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
			}
			logRestRequest(httpServletRequest, xmlNode, returnValue, Status.OK.getStatusCode());
			if( editLog!= null )
			{
				editLog.write(String.format(logformat, "OK", nodeUuid, "noderesource", ui.userId, timeFormat, httpServletRequest.getRemoteAddr(), xmlNode ));
				editLog.flush();
			}

			return returnValue;
		}
		catch(RestWebApplicationException ex)
		{
			throw ex;
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, xmlNode,ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Instanciate a node with right parsing
	 *	POST /rest/api/nodes/node/import/{dest-id}
	 *	parameters:
	 *	return:
	 **/
	@Path("/nodes/node/import/{dest-id}")
	@POST
	public String postImportNode(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("dest-id") String parentId,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("srcetag") String semtag, @QueryParam("srcecode") String code, @QueryParam("uuid") String srcuuid)
	{
		/*
		if( !isUUID(srcuuid) || !isUUID(parentId) )
		{
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}
		//*/

		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;

		try
		{
			if( ui.userId == 0 )
				throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'etes pas connecte");
			
			c = SqlUtils.getConnection(servContext);
			String returnValue = dataProvider.postImportNode(c, new MimeType("text/xml"), parentId, semtag, code, srcuuid, ui.userId, groupId).toString();

			logRestRequest(httpServletRequest, xmlNode, returnValue, Status.OK.getStatusCode());

			if("faux".equals(returnValue) )
			{
				throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
			}

			return returnValue;
		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, xmlNode,ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Raw copy a node
	 *	POST /rest/api/nodes/node/copy/{dest-id}
	 *	parameters:
	 *	return:
	 **/
	@Path("/nodes/node/copy/{dest-id}")
	@POST
	public String postCopyNode(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("dest-id") String parentId,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("srcetag") String semtag, @QueryParam("srcecode") String code, @QueryParam("uuid") String srcuuid)
	{
		/*
		if( !isUUID(srcuuid) || !isUUID(parentId) )
		{
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}
		//*/

		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;

		//// TODO: IF user is creator and has parameter owner -> change ownership

		try
		{
			if( ui.userId == 0 )
				throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'etes pas connecte");
			
			c = SqlUtils.getConnection(servContext);
			String returnValue = dataProvider.postCopyNode(c, new MimeType("text/xml"), parentId, semtag, code, srcuuid, ui.userId, groupId).toString();
			logRestRequest(httpServletRequest, xmlNode, returnValue, Status.OK.getStatusCode());

			if(returnValue == "faux")
			{
				throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
			}
			else if( "Selection non existante.".equals(returnValue) )
			{
				throw new RestWebApplicationException(Status.NOT_FOUND, "Selection non existante.");
			}

			return returnValue;
		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, xmlNode,ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 * Fetch nodes right
	 *	GET /rest/api/nodes
	 *	parameters:
	 *	- portfoliocode: mandatory
	 *	- semtag_parent, code_parent: From a code_parent, find the children that have semtag_parent
	 *	- semtag:	mandatory, find the semtag under portfoliocode, or the selection from semtag_parent/code_parent
	 *	return:
	 **/
	@Path("/nodes")
	@GET
	@Produces({MediaType.APPLICATION_XML})
	@Consumes(MediaType.APPLICATION_XML)
	public String getNodes( @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("dest-id") String parentId,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("portfoliocode") String portfoliocode, @QueryParam("semtag") String semtag, @QueryParam("semtag_parent") String semtag_parent, @QueryParam("code_parent") String code_parent, @QueryParam("level") Integer cutoff)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;

		try
		{
			c = SqlUtils.getConnection(servContext);
			String returnValue = dataProvider.getNodes(c, new MimeType("text/xml"), portfoliocode, semtag, ui.userId, groupId, semtag_parent, code_parent, cutoff).toString();
			logRestRequest(httpServletRequest, "getNodes", returnValue, Status.OK.getStatusCode());

			if(returnValue == "faux")
			{
				throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
			}
			else if( "".equals(returnValue) )
			{
				throw new RestWebApplicationException(Status.NOT_FOUND, "Portfolio inexistant");
			}

			return returnValue;
		}
		catch(RestWebApplicationException ex)
		{
			throw ex;
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, "getNodes",ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Insert XML in a node. Moslty used by admin, other people use the import/copy node
	 *	POST /rest/api/nodes/node/{parent-id}
	 *	parameters:
	 *	return:
	 **/
	@Path("/nodes/node/{parent-id}")
	@POST
	@Consumes(MediaType.APPLICATION_XML)
	@Produces(MediaType.APPLICATION_XML)
	public Response postNode(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") Integer group, @PathParam("parent-id") String parentId,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("user") Integer userId, @QueryParam("group") int groupId)
	{
		if( !isUUID(parentId) )
		{
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}

		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		KEvent event = new KEvent();
		event.requestType = KEvent.RequestType.POST;
		event.eventType = KEvent.EventType.NODE;
		event.uuid = parentId;
		event.inputData = xmlNode;

		Connection c = null;
		try
		{
			c = SqlUtils.getConnection(servContext);
			if( ui.userId == 0 )
			{
				return Response.status(403).entity("Not logged in").build();
			}
			else // if( dataProvider.isAdmin(c, Integer.toString(ui.userId)) )
			{
				String returnValue = dataProvider.postNode(c, new MimeType("text/xml"),parentId,xmlNode, ui.userId, groupId, false).toString();
				logRestRequest(httpServletRequest, xmlNode, returnValue, Status.OK.getStatusCode());

				Response response;
				if(returnValue == "faux")
				{
					response = Response.status(event.status).entity(event.message).type(event.mediaType).build();
					throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
				}
				event.status = 200;
				response = Response.status(event.status).entity(returnValue).type(event.mediaType).build();
				eventbus.processEvent(event);

				return response;
			}
//			else
//				return Response.status(403).entity("No").build();
		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, xmlNode,ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Move a node up between siblings
	 *	POST /rest/api/nodes/node/{node-id}/moveup
	 *	parameters:
	 *	return:
	 **/
	@Path("/nodes/node/{node-id}/moveup")
	@POST
	@Consumes(MediaType.APPLICATION_XML)
	@Produces(MediaType.APPLICATION_XML)
	public Response postMoveNodeUp(String xmlNode, @PathParam("node-id") String nodeId,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest )
	{
		if( !isUUID(nodeId) )
		{
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}
		
		UserInfo ui = checkCredential(httpServletRequest, null, null, null);

		/*
      KEvent event = new KEvent();
      event.requestType = KEvent.RequestType.POST;
      event.eventType = KEvent.EventType.NODE;
      event.uuid = parentId;
      event.inputData = xmlNode;
      //*/
		Response response;
		Connection c = null;

		try
		{
			if( nodeId == null )
			{
				response = Response.status(400).entity("Missing uuid").build();
			}
			else
			{
				c = SqlUtils.getConnection(servContext);
				int returnValue = dataProvider.postMoveNodeUp(c, ui.userId, nodeId);
				logRestRequest(httpServletRequest, xmlNode, Integer.toString(returnValue), Status.OK.getStatusCode());

				if( returnValue == -1 )
				{
					response = Response.status(404).entity("Non-existing node").build();
				}
				if( returnValue == -2 )
				{
					response = Response.status(409).entity("Cannot move first node").build();
				}
				else
				{
					response = Response.status(204).build();
				}
			}
		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, xmlNode,ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}

		return response;
	}

	/**
	 *	Move a node to another parent
	 *	POST /rest/api/nodes/node/{node-id}/parentof/{parent-id}
	 *	parameters:
	 *	return:
	 **/
	@Path("/nodes/node/{node-id}/parentof/{parent-id}")
	@POST
	@Consumes(MediaType.APPLICATION_XML)
	@Produces(MediaType.APPLICATION_XML)
	public Response postChangeNodeParent(String xmlNode, @PathParam("node-id") String nodeId, @PathParam("parent-id") String parentId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest )
	{
		if( !isUUID(nodeId) || !isUUID(parentId) )
		{
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}
		
		UserInfo ui = checkCredential(httpServletRequest, null, null, null);

		/*
      KEvent event = new KEvent();
      event.requestType = KEvent.RequestType.POST;
      event.eventType = KEvent.EventType.NODE;
      event.uuid = parentId;
      event.inputData = xmlNode;
      //*/
		Connection c = null;

		try
		{
			c = SqlUtils.getConnection(servContext);
			boolean returnValue = dataProvider.postChangeNodeParent(c, ui.userId, nodeId, parentId);
			logRestRequest(httpServletRequest, xmlNode, Boolean.toString(returnValue), Status.OK.getStatusCode());

			Response response;
			if( returnValue == false )
			{
				response = Response.status(409).entity("Cannot move").build();
			}
			else
			{
				response = Response.status(200).build();
			}

			return response;
		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, xmlNode,ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Execute a macro command on a node, changing rights related
	 *	POST /rest/api/nodes/node/{node-id}/action/{action-name}
	 *	parameters:
	 *	return:
	 **/
	@Path("/nodes/node/{node-id}/action/{action-name}")
	@POST
	@Consumes(MediaType.APPLICATION_XML)
	@Produces(MediaType.APPLICATION_XML)
	public String postActionNode(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("node-id") String nodeId, @PathParam("action-name") String macro, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("user") Integer userId )
	{
		if( !isUUID(nodeId) )
		{
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}

		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;

		try
		{
			c = SqlUtils.getConnection(servContext);
			String returnValue = dataProvider.postMacroOnNode(c, ui.userId, nodeId, macro);
			logRestRequest(httpServletRequest, xmlNode, returnValue, Status.OK.getStatusCode());

			if( returnValue == "erreur")
			{
				throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
			}

			return returnValue;
		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, xmlNode,ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Delete a node
	 *	DELETE /rest/api/nodes/node/{node-uuid}
	 *	parameters:
	 *	return:
	 **/
	@Path("/nodes/node/{node-uuid}")
	@DELETE
	@Produces(MediaType.APPLICATION_XML)
	public String deleteNode(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("node-uuid") String nodeUuid,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("user") Integer userId )
	{
		if( !isUUID(nodeUuid) )
		{
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}
		
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;

		try
		{
			c = SqlUtils.getConnection(servContext);
			int nbDeletedNodes = Integer.parseInt(dataProvider.deleteNode(c, nodeUuid, ui.userId, groupId).toString());
			if(nbDeletedNodes==0)
			{
				logRestRequest(httpServletRequest, null,null, Status.NOT_FOUND.getStatusCode());

				throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
			}
			logRestRequest(httpServletRequest, null,null, Status.OK.getStatusCode());

			return "";
		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
		}
		catch(SQLException ex)
		{
			logRestRequest(httpServletRequest, null,null, Status.NOT_FOUND.getStatusCode());

			throw new RestWebApplicationException(Status.NOT_FOUND, "Node "+nodeUuid+" not found");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, null,ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/******************************/
	/**
	 *  Ressources
	 *
	 *  ######  #######  #####   #####   #####  ##   ## ######   #####  #######  #####
	 *  ##   ## ##      ##   ## ##   ## ##   ## ##   ## ##   ## ##   ## ##      ##   ##
	 *  ##   ## ##      ##      ##      ##   ## ##   ## ##   ## ##      ##      ##
	 *  ######  ####     #####   #####  ##   ## ##   ## ######  ##      ####     #####
	 *  ##   ## ##           ##      ## ##   ## ##   ## ##   ## ##      ##           ##
	 *  ##   ## ##      ##   ## ##   ## ##   ## ##   ## ##   ## ##   ## ##      ##   ##
	 *  ##   ## #######  #####   #####   #####   #####  ##   ##  #####  #######  #####
	 **/
	/*****************************/

	/**
	 * Fetch resource from node uuid
	 *	GET /rest/api/resources/resource/{node-parent-id}
	 *	parameters:
	 *	- portfoliocode: mandatory
	 *	- semtag_parent, code_parent: From a code_parent, find the children that have semtag_parent
	 *	- semtag:	mandatory, find the semtag under portfoliocode, or the selection from semtag_parent/code_parent
	 *	return:
	 **/
	@Path("/resources/resource/{node-parent-id}")
	@GET
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Consumes(MediaType.APPLICATION_XML)
	public String getResource( @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("node-parent-id") String nodeParentUuid,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept, @QueryParam("user") Integer userId )
	{
		if( !isUUID(nodeParentUuid) )
		{
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}

		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;

		try
		{
			c = SqlUtils.getConnection(servContext);
			String returnValue = dataProvider.getResource(c, new MimeType("text/xml"),nodeParentUuid, ui.userId, groupId).toString();
			if(returnValue.equals("faux")){

				throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
			}
			if(accept.equals(MediaType.APPLICATION_JSON))
				returnValue = XML.toJSONObject(returnValue).toString();
			logRestRequest(httpServletRequest, null, returnValue, Status.OK.getStatusCode());

			return returnValue;
		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
		}
		catch(SQLException ex)
		{
			logRestRequest(httpServletRequest, null,null, Status.NOT_FOUND.getStatusCode());

			throw new RestWebApplicationException(Status.NOT_FOUND, "Resource "+nodeParentUuid+" not found");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, null,ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 * Fetch all resource in a portfolio
	 * TODO: is it used?
	 *	GET /rest/api/resources/portfolios/{portfolio-id}
	 *	parameters:
	 *	- portfolio-id
	 *	return:
	 **/
	@Path("/resources/portfolios/{portfolio-id}")
	@GET
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	public String getResources( @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("portfolio-id") String portfolioUuid,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept, @QueryParam("user") Integer userId )
	{
		if( !isUUID(portfolioUuid) )
		{
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}

		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;

		try
		{
			c = SqlUtils.getConnection(servContext);
			String returnValue = dataProvider.getResources(c, new MimeType("text/xml"),portfolioUuid, ui.userId, groupId).toString();
			if(accept.equals(MediaType.APPLICATION_JSON))
				returnValue = XML.toJSONObject(returnValue).toString();
			logRestRequest(httpServletRequest, null, returnValue, Status.OK.getStatusCode());

			return returnValue;
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, null,ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Modify resource content
	 *	PUT /rest/api/resources/resource/{node-parent-uuid}
	 *	parameters:
	 *	return:
	 **/
	@Path("/resources/resource/{node-parent-uuid}")
	@PUT
	@Produces(MediaType.APPLICATION_XML)
	public String putResource(String xmlResource, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @QueryParam("info") String info, @PathParam("node-parent-uuid") String nodeParentUuid,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("user") Integer userId )
	{
		if( !isUUID(nodeParentUuid) )
		{
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}

		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		/*
		KEvent event = new KEvent();
		event.requestType = KEvent.RequestType.POST;
		event.eventType = KEvent.EventType.NODE;
		event.uuid = nodeParentUuid;
		event.inputData = xmlResource;
		//*/
		Connection c = null;
		Date time = new Date();
		SimpleDateFormat dt = new SimpleDateFormat("yyyy-MM-dd HHmmss");
		String timeFormat = dt.format(time);
		String logformat = "";
		if( "false".equals(info) )
			logformat = logFormatShort;
		else
			logformat = logFormat;

		try
		{
			c = SqlUtils.getConnection(servContext);
			String returnValue = dataProvider.putResource(c, new MimeType("text/xml"),nodeParentUuid,xmlResource, ui.userId, groupId).toString();
			logRestRequest(httpServletRequest, xmlResource, returnValue, Status.OK.getStatusCode());
			if( editLog!= null )
			{
				editLog.write(String.format(logformat, "OK", nodeParentUuid, "resource", ui.userId, timeFormat, httpServletRequest.getRemoteAddr(), xmlResource ));
				editLog.flush();
			}

//			eventbus.processEvent(event);

			return returnValue;
		}
		catch( RestWebApplicationException ex )
		{
			try
			{
				errorLog.write(String.format(logformat, "ERR", nodeParentUuid, "resource", ui.userId, timeFormat, httpServletRequest.getRemoteAddr(), xmlResource ));
				errorLog.flush();
				editLog.write(String.format(logformat, "ERR", nodeParentUuid, "resource", ui.userId, timeFormat, httpServletRequest.getRemoteAddr(), xmlResource ));
				editLog.flush();
			}
			catch(Exception exex)
			{
				logger.error(exex.getMessage());
			}
			logger.error(ex.getMessage());
			ex.printStackTrace();
			logRestRequest(httpServletRequest, xmlResource, ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw ex;
		}
		catch(Exception ex)
		{
			try
			{
				errorLog.write(String.format(logformat, "ERR", nodeParentUuid, "resource", ui.userId, timeFormat, httpServletRequest.getRemoteAddr(), xmlResource ));
				errorLog.flush();
			}
			catch(Exception exex)
			{
				logger.error(exex.getMessage());
			}
			logger.error(ex.getMessage());
			ex.printStackTrace();
			logRestRequest(httpServletRequest, xmlResource, ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Add a resource (?)
	 *	POST /rest/api/resources/{node-parent-uuid}
	 *	parameters:
	 *	return:
	 **/
	@Path("/resources/{node-parent-uuid}")
	@POST
	@Produces(MediaType.APPLICATION_XML)
	public String postResource(String xmlResource, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("node-parent-uuid") String nodeParentUuid,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("user") Integer userId )
	{
		if( !isUUID(nodeParentUuid) )
		{
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}

		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;

		try
		{
			//TODO userId
			c = SqlUtils.getConnection(servContext);
			String returnValue = dataProvider.postResource(c, new MimeType("text/xml"),nodeParentUuid,xmlResource, ui.userId, groupId).toString();
			if(returnValue.equals("faux")){

				throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
			}
			logRestRequest(httpServletRequest, xmlResource, returnValue, Status.OK.getStatusCode());

			return returnValue;
		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, xmlResource,  ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	(?)
	 *	POST /rest/api/resources
	 *	parameters:
	 *	return:
	 **/
	@Path("/resources")
	@POST
	@Produces(MediaType.APPLICATION_XML)
	public String postResource(String xmlResource, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("type") Integer type, @QueryParam("resource") String resource,@QueryParam("user") Integer userId )
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;

		try
		{
			//TODO userId
			c = SqlUtils.getConnection(servContext);
			String returnValue = dataProvider.postResource(c, new MimeType("text/xml"),resource,xmlResource, ui.userId, groupId).toString();
			if(returnValue.equals("faux")){

				throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
			}
			logRestRequest(httpServletRequest, xmlResource, returnValue, Status.OK.getStatusCode());

			return returnValue;
		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, xmlResource,  ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Add user to a role (?)
	 *	POST /rest/api/roleUser
	 *	parameters:
	 *	return:
	 **/
	@Path("/roleUser")
	@POST
	@Produces(MediaType.APPLICATION_XML)
	public String postRoleUser(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("grid") int grid,@QueryParam("user-id") Integer userid)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;

		try
		{
			//TODO userId
			c = SqlUtils.getConnection(servContext);
			String returnValue = dataProvider.postRoleUser(c, ui.userId, grid, userid).toString();
			if(returnValue.equals("faux")){

				throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
			}
			//logRestRequest(httpServletRequest, xmlResource, returnValue, Status.OK.getStatusCode());
			//        	dataProvider.disconnect();
			return returnValue;
		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			//logRestRequest(httpServletRequest, xmlResource,  ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());
			//			dataProvider.disconnect();
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Modify a role
	 *	PUT /rest/api/roles/role/{role-id}
	 *	parameters:
	 *	return:
	 **/
	@Path("/roles/role/{role-id}")
	@PUT
	@Produces(MediaType.APPLICATION_XML)
	public String putRole(String xmlRole, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @PathParam("role-id") int roleId)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;

		try
		{
			//TODO userId
			c = SqlUtils.getConnection(servContext);
			String returnValue = dataProvider.putRole(c, xmlRole, ui.userId, roleId).toString();
			if(returnValue.equals("faux")){

				throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
			}
			//logRestRequest(httpServletRequest, xmlResource, returnValue, Status.OK.getStatusCode());
			//        	dataProvider.disconnect();
			return returnValue;
		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			//logRestRequest(httpServletRequest, xmlResource,  ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());
			//			dataProvider.disconnect();
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 * Fetch all role in a portfolio
	 *	GET /rest/api/roles/portfolio/{portfolio-id}
	 *	parameters:
	 *	return:
	 **/
	@Path("/roles/portfolio/{portfolio-id}")
	@GET
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	public String getRolePortfolio( @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @QueryParam("role") String role, @PathParam("portfolio-id") String portfolioId,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept)
	{
		if( !isUUID(portfolioId) )
		{
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}

		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;

		try
		{
			c = SqlUtils.getConnection(servContext);
			String returnValue = dataProvider.getRolePortfolio(c, new MimeType("text/xml"),role,portfolioId,ui.userId).toString();
			//			if(accept.equals(MediaType.APPLICATION_JSON))
			//				returnValue = XML.toJSONObject(returnValue).toString();
			logRestRequest(httpServletRequest, null, returnValue, Status.OK.getStatusCode());

			return returnValue;
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, null,ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 * Fetch rights in a role
	 * FIXME: Might be redundant
	 *	GET /rest/api/roles/role/{role-id}
	 *	parameters:
	 *	return:
	 **/
	@Path("/roles/role/{role-id}")
	@GET
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	public String getRole( @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("role-id") Integer roleId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;

		try
		{
			c = SqlUtils.getConnection(servContext);
			String returnValue = dataProvider.getRole(c, new MimeType("text/xml"),roleId,ui.userId).toString();
			if(returnValue.equals(""))
			{
				logRestRequest(httpServletRequest, null,null, Status.NOT_FOUND.getStatusCode());

				throw new RestWebApplicationException(Status.NOT_FOUND, "Role "+roleId+" not found");
			}

			logRestRequest(httpServletRequest, null, returnValue, Status.OK.getStatusCode());

			return returnValue;
		}
		catch(RestWebApplicationException ex)
		{
			logRestRequest(httpServletRequest, null,null, Status.NOT_FOUND.getStatusCode());

			throw new RestWebApplicationException(Status.NOT_FOUND, "Role "+roleId+" not found");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, null,ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 * Fetch all models
	 * FIXME: Most probably useless
	 *	GET /rest/api/models
	 *	parameters:
	 *	return:
	 **/
	@Deprecated
	@Path("/models")
	@GET
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	public String getModels(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;

		try
		{
			c = SqlUtils.getConnection(servContext);
			String returnValue = dataProvider.getModels(c, new MimeType("text/xml"),ui.userId).toString();
			if(returnValue.equals(""))
			{
				logRestRequest(httpServletRequest, null,null, Status.NOT_FOUND.getStatusCode());

				throw new RestWebApplicationException(Status.NOT_FOUND, "Role "+" not found");
			}

			logRestRequest(httpServletRequest, null, returnValue, Status.OK.getStatusCode());

			return returnValue;
		}
		catch(RestWebApplicationException ex)
		{
			logRestRequest(httpServletRequest, null,null, Status.NOT_FOUND.getStatusCode());

			throw new RestWebApplicationException(Status.NOT_FOUND, "Role "+" not found");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, null,ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 * Fetch a model
	 * FIXME: Most probably useless
	 *	GET /rest/api/{model-id}
	 *	parameters:
	 *	return:
	 **/
	@Deprecated
	@Path("/models/{model-id}")
	@GET
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	public String getModel(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("model-id") Integer modelId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;

		try
		{
			c = SqlUtils.getConnection(servContext);
			String returnValue = dataProvider.getModel(c, new MimeType("text/xml"),modelId,ui.userId).toString();
			if(returnValue.equals(""))
			{
				logRestRequest(httpServletRequest, null,null, Status.NOT_FOUND.getStatusCode());

				throw new RestWebApplicationException(Status.NOT_FOUND, "Role "+" not found");
			}

			logRestRequest(httpServletRequest, null, returnValue, Status.OK.getStatusCode());

			return returnValue;
		}
		catch(RestWebApplicationException ex)
		{
			logRestRequest(httpServletRequest, null,null, Status.NOT_FOUND.getStatusCode());

			throw new RestWebApplicationException(Status.NOT_FOUND, "Role "+" not found");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, null,ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Add a model (deprecated)
	 *	POST /rest/api/models
	 *	parameters:
	 *	return:
	 **/
	@Deprecated
	@Path("/models")
	@POST
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	public String postModel(String xmlModel, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;

		try
		{
			c = SqlUtils.getConnection(servContext);
			String returnValue = dataProvider.postModels(c, new MimeType("text/xml"),xmlModel,ui.userId).toString();
			if(returnValue.equals(""))
			{
				logRestRequest(httpServletRequest, null,null, Status.NOT_FOUND.getStatusCode());

				throw new RestWebApplicationException(Status.NOT_FOUND, "Role "+" not found");
			}

			logRestRequest(httpServletRequest, null, returnValue, Status.OK.getStatusCode());

			return returnValue;
		}
		catch(RestWebApplicationException ex)
		{
			logRestRequest(httpServletRequest, null,null, Status.NOT_FOUND.getStatusCode());

			throw new RestWebApplicationException(Status.NOT_FOUND, "Role "+" not found");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, null,ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Delete a resource
	 *	DELETE /rest/api/resources/{resource-id}
	 *	parameters:
	 *	return:
	 **/
	@Path("/resources/{resource-id}")
	@DELETE
	@Produces(MediaType.APPLICATION_XML)
	public String deleteResource(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("resource-id") String resourceUuid,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("user") Integer userId )
	{
		if( !isUUID(resourceUuid) )
		{
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;

		try
		{
			c = SqlUtils.getConnection(servContext);
			int nbResourceDeleted = Integer.parseInt(dataProvider.deleteResource(c, resourceUuid, ui.userId, groupId).toString());
			if(nbResourceDeleted==0)
			{
				logRestRequest(httpServletRequest, null,null, Status.NOT_FOUND.getStatusCode());

				throw new RestWebApplicationException(Status.NOT_FOUND, "Resource "+resourceUuid+" not found");
			}
			logRestRequest(httpServletRequest, null,null, Status.OK.getStatusCode());

			return "";
		}
		catch(RestWebApplicationException ex)
		{
			logRestRequest(httpServletRequest, null,null, Status.NOT_FOUND.getStatusCode());

			throw new RestWebApplicationException(Status.NOT_FOUND, "Resource "+resourceUuid+" not found");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, null,ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Delete a right definition for a node
	 *	DELETE /rest/api/groupRights
	 *	parameters:
	 *	return:
	 **/
	@Path("/groupRights")
	@DELETE
	@Produces(MediaType.APPLICATION_XML)
	public String deleteGroupRights(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("groupRightId") Integer groupRightId)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;

		try
		{
			c = SqlUtils.getConnection(servContext);
			int nbResourceDeleted = Integer.parseInt(dataProvider.deleteGroupRights(c, groupId, groupRightId, ui.userId).toString());
			if(nbResourceDeleted==0)
			{
				System.out.print("supprim�");
			}
			logRestRequest(httpServletRequest, null,null, Status.OK.getStatusCode());

			return "";
		}
		catch(RestWebApplicationException ex)
		{
			logRestRequest(httpServletRequest, null,null, Status.NOT_FOUND.getStatusCode());

			throw new RestWebApplicationException(Status.NOT_FOUND, "Resource  not found");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, null,ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Delete users
	 *	DELETE /rest/api/users
	 *	parameters:
	 *	return:
	 **/
	@Path("/users")
	@DELETE
	@Produces(MediaType.APPLICATION_XML)
	public String deleteUsers(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("userId") Integer userId)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;

		try
		{
			c = SqlUtils.getConnection(servContext);
			int nbResourceDeleted = Integer.parseInt(dataProvider.deleteUsers(c, userId, null).toString());
			if(nbResourceDeleted==0)
			{
				System.out.print("supprim�");
			}
			logRestRequest(httpServletRequest, null,null, Status.OK.getStatusCode());

			return "";
		}
		catch(RestWebApplicationException ex)
		{
			logRestRequest(httpServletRequest, null,null, Status.NOT_FOUND.getStatusCode());

			throw new RestWebApplicationException(Status.NOT_FOUND, "Resource  not found");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, null,ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Delete specific user
	 *	DELETE /rest/api/users/user/{user-id}
	 *	parameters:
	 *	return:
	 **/
	@Path("/users/user/{user-id}")
	@DELETE
	@Produces(MediaType.APPLICATION_XML)
	public String deleteUser(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @PathParam("user-id") Integer userid)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;

		try
		{
			c = SqlUtils.getConnection(servContext);
			int nbResourceDeleted = Integer.parseInt(dataProvider.deleteUsers(c, ui.userId, userid).toString());
			logRestRequest(httpServletRequest, null,null, Status.OK.getStatusCode());

			return "user "+userid+" deleted";
		}
		catch(RestWebApplicationException ex)
		{
			logRestRequest(httpServletRequest, null,null, Status.NOT_FOUND.getStatusCode());

			throw new RestWebApplicationException(Status.NOT_FOUND, "Resource  not found");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, null,ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Get roles in a portfolio
	 *	GET /rest/api/groups/{portfolio-id}
	 *	parameters:
	 *	return:
	 **/
	@Path("/groups/{portfolio-id}")
	@GET
	@Produces(MediaType.APPLICATION_XML)
	public String getGroupsPortfolio(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("portfolio-id") String portfolioUuid, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		if( !isUUID(portfolioUuid) )
		{
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;

		try
		{
			c = SqlUtils.getConnection(servContext);
			String xmlGroups = dataProvider.getGroupsPortfolio(c, portfolioUuid, ui.userId);
			logRestRequest(httpServletRequest, "", xmlGroups, Status.OK.getStatusCode());

			return xmlGroups;
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, "", ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Get roles in a portfolio
	 *	GET /rest/api/credential/group/{portfolio-id}
	 *	parameters:
	 *	return:
	 **/
	@Path("/credential/group/{portfolio-id}")
	@GET
	@Produces(MediaType.APPLICATION_XML)
	public String getUserGroupByPortfolio(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("portfolio-id") String portfolioUuid, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		if( !isUUID(portfolioUuid) )
		{
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}
		
		HttpSession session = httpServletRequest.getSession(true);
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;

		try
		{
			c = SqlUtils.getConnection(servContext);
			String xmlGroups = dataProvider.getUserGroupByPortfolio(c, portfolioUuid, ui.userId);
			logRestRequest(httpServletRequest, "", xmlGroups, Status.OK.getStatusCode());

			DocumentBuilderFactory documentBuilderFactory =DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder = null;
			Document document=null;
			documentBuilder = documentBuilderFactory.newDocumentBuilder();
			document = documentBuilder.newDocument();
			document.setXmlStandalone(true);
			Document doc = documentBuilder.parse(new ByteArrayInputStream(xmlGroups.getBytes("UTF-8")));
			NodeList groups = doc.getElementsByTagName("group");
			if( groups.getLength() == 1 )
			{
				Node groupnode = groups.item(0);
				String gid = groupnode.getAttributes().getNamedItem("id").getNodeValue();
				if( gid != null )
				{
				}
			}
			else if( groups.getLength() == 0 )	// Pas de groupe, on rend invalide le choix
			{
			}

			return xmlGroups;
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, "", ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Send login information
	 *	PUT /rest/api/credential/login
	 *	parameters:
	 *	return:
	 **/
	@Path("/credential/login")
	@PUT
	@Produces(MediaType.APPLICATION_XML)
	@Consumes(MediaType.APPLICATION_XML)
	public Response  putCredentialFromXml(String xmlCredential, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		return this.postCredentialFromXml(xmlCredential, user, token, 0, sc, httpServletRequest);
	}

	/**
	 *	Send login information
	 *	POST /rest/api/credential/login
	 *	parameters:
	 *	return:
	 **/
	@Path("/credential/login")
	@POST
	@Produces(MediaType.APPLICATION_XML)
	@Consumes(MediaType.APPLICATION_XML)
	public Response  postCredentialFromXml(String xmlCredential, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		HttpSession session = httpServletRequest.getSession(true);
		initService( httpServletRequest );
		KEvent event = new KEvent();
		event.eventType = KEvent.EventType.LOGIN;
		event.inputData = xmlCredential;
		String retVal = "";
		int status = 0;
		Connection c = null;
		
		String authlog = ConfigUtils.get("auth_log");
		BufferedWriter authLog = null;
		try
		{
			if( !"".equals(authlog) && authlog != null )
			authLog = LogUtils.getLog(authlog);
		}
		catch( IOException e1 )
		{
			logger.error("Could not create authentification log file");
		}

		try
		{
			Document doc = DomUtils.xmlString2Document(xmlCredential, new StringBuffer());
			Element credentialElement = doc.getDocumentElement();
			String login = "";
			String password = "";
			String substit = null;
			if(credentialElement.getNodeName().equals("credential"))
			{
				String[] templogin = DomUtils.getInnerXml(doc.getElementsByTagName("login").item(0)).split("#");
				password = DomUtils.getInnerXml(doc.getElementsByTagName("password").item(0));

				if( templogin.length > 1 )
					substit = templogin[1];
				login = templogin[0];
			}

			int dummy = 0;
			c = SqlUtils.getConnection(servContext);
			String[] resultCredential = dataProvider.postCredentialFromXml(c, dummy, login, password, substit);
			// 0: xml de retour
			// 1,2: username, uid
			// 3,4: substitute name, substitute id
			if( resultCredential == null )
			{
				event.status = 403;
				retVal = "invalid credential";
				
				if(authLog != null)
					authLog.write(String.format("Authentication error for user '%s' date '%s'\n", login, LogUtils.getCurrentDate()));
			}
			else if ( !"0".equals(resultCredential[2]) )
			{
				//				String tokenID = resultCredential[2];

				if( substit != null && !"0".equals(resultCredential[4]) )
				{
					int uid = Integer.parseInt(resultCredential[2]);
					int subid = Integer.parseInt(resultCredential[4]);

					session.setAttribute("user", resultCredential[3]);
					session.setAttribute("uid", subid);
					session.setAttribute("subuser", resultCredential[1]);
					session.setAttribute("subuid", uid);
					if(authLog != null)
						authLog.write(String.format("Authentication success for user '%s' date '%s' (Substitution)\n", login, LogUtils.getCurrentDate()));
				}
				else
				{
					String login1 = resultCredential[1];
					int userId = Integer.parseInt(resultCredential[2]);

					session.setAttribute("user", login1);
					session.setAttribute("uid", userId);
					session.setAttribute("subuser", "");
					session.setAttribute("subuid", 0);
					if(authLog != null)
						authLog.write(String.format("Authentication success for user '%s' date '%s'\n", login, LogUtils.getCurrentDate()));
				}

				event.status = 200;
				retVal = resultCredential[0];
			}
			eventbus.processEvent(event);

			return Response.status(event.status).entity(retVal).type(event.mediaType).build();
		}
		catch(RestWebApplicationException ex)
		{
			ex.printStackTrace();
			logger.error(ex.getLocalizedMessage());
			logRestRequest(httpServletRequest,null, "invalid Credential or invalid group member", Status.FORBIDDEN.getStatusCode());
			throw new RestWebApplicationException(Status.FORBIDDEN, ex.getMessage());
		}
		catch(Exception ex)
		{
			status = 500;
			retVal = ex.getMessage();
			logger.error(ex.getMessage());
			logRestRequest(httpServletRequest, xmlCredential, ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
				if( authLog != null )
				{
					authLog.flush();
					authLog.close();
				}
			}
			catch( SQLException e ){ e.printStackTrace(); }
			catch( IOException e )
			{
				e.printStackTrace();
			}
		}
		
		return Response.status(status).entity(retVal).type(MediaType.APPLICATION_XML).build();
	}

	/**
	 *	Tell system you forgot your password
	 *	POST /rest/api/credential/forgot
	 *	parameters:
	 *	return:
	 **/
	@Path("/credential/forgot")
	@POST
	@Consumes(MediaType.APPLICATION_XML)
	public Response postForgotCredential(String xml, @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest)
	{
		HttpSession session = httpServletRequest.getSession(true);
		initService( httpServletRequest );
		int retVal = 404;
		String retText = "";
		Connection c = null;

		try
		{
			Document doc = DomUtils.xmlString2Document(xml, new StringBuffer());
			Element infUser = doc.getDocumentElement();

			String username = "";
			if(infUser.getNodeName().equals("credential"))
			{
				NodeList children2 = infUser.getChildNodes();
				for(int y=0;y<children2.getLength();y++)
				{
					if(children2.item(y).getNodeName().equals("login"))
					{
						username = DomUtils.getInnerXml(children2.item(y));
						break;
					}
				}
			}

			c = SqlUtils.getConnection(servContext);
			// Check if we have that email somewhere
			String email = dataProvider.emailFromLogin(c, username);
			if( email != null && !"".equals(email) )
			{
				// Generate password
				long base = System.currentTimeMillis();
				MessageDigest md = MessageDigest.getInstance("SHA-1");
				byte[] output = md.digest(Long.toString(base).getBytes());
				String password = String.format("%032X", new BigInteger(1, output));
				password = password.substring(0, 9);

				// Write change
				boolean result = dataProvider.changePassword(c, username, password);
				String content = "Your new password: "+password;

				if( result )
				{
					String cc_email = ConfigUtils.get("sys_email");
					// Send email
					MailUtils.postMail(sc, email, cc_email, "Password change for Karuta", content, logger);
					retVal = 200;
					retText = "sent";
				}
			}
		}
		catch(RestWebApplicationException ex)
		{
			ex.printStackTrace();
			logger.error(ex.getLocalizedMessage());
			logRestRequest(httpServletRequest,null, "invalid Credential or invalid group member", Status.FORBIDDEN.getStatusCode());
			throw new RestWebApplicationException(Status.FORBIDDEN, ex.getMessage());
		}
		catch(Exception ex)
		{
			logger.error(ex.getMessage());
			ex.printStackTrace();
			logRestRequest(httpServletRequest, "", ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}

		return Response.status(retVal).entity(retText).build();
	}

	/**
	 *	Fetch current user information (CAS)
	 *	GET /rest/api/credential/login/cas
	 *	parameters:
	 *	return:
	 **/
	@Path("/credential/login/cas")
	@GET
	public Response postCredentialFromCas( @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @QueryParam("ticket") String ticket, @QueryParam("redir") String redir, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		initService( httpServletRequest );
		HttpSession session = httpServletRequest.getSession(true);

		String errorCode = null;
		String errorMessage = null;
		String xmlResponse = null;
		String userId = null;
		String completeURL;
		StringBuffer requestURL;

		Connection c = null;
		try
		{
			ServiceTicketValidator sv = new ServiceTicketValidator();

			if(casUrlValidation==null)
			{
				Response response = null;
				try
				{
					// formulate the response
					response = Response.status(Status.PRECONDITION_FAILED)
							.entity("CAS URL not defined").build();
				} catch (Exception e) {
					response = Response.status(500).build();
				}
				return response;
			}
			
			sv.setCasValidateUrl(casUrlValidation);

			requestURL = httpServletRequest.getRequestURL();
			if (httpServletRequest.getQueryString() != null) {
				requestURL.append("?").append(httpServletRequest.getQueryString());
			}
			completeURL = requestURL.toString();
			sv.setService(completeURL);
			sv.setServiceTicket(ticket);
			//sv.setProxyCallbackUrl(urlOfProxyCallbackServlet);
			sv.validate();

			xmlResponse = sv.getResponse();
			//<cas:user>vassoilm</cas:user>
			//session.setAttribute("user", sv.getUser());
			//session.setAttribute("uid", dataProvider.getUserId(sv.getUser()));
			c = SqlUtils.getConnection(servContext);
			userId =  dataProvider.getUserId(c, sv.getUser(), null);
			if(userId!=null)
			{
				session.setAttribute("user", sv.getUser());
				session.setAttribute("uid",Integer.parseInt(userId));
			}
			else
			{
				return Response.status(403).entity("Login "+sv.getUser()+" not found or bad CAS auth (bad ticket or bad url service : "+completeURL+") : "+sv.getErrorMessage()).build();
			}

			Response response = null;
			try
			{
				// formulate the response
				response = Response.status(201)
						.header(
								"Location",
								redir
								)
								.entity("<script>document.location.replace('"+redir+"')</script>").build();
			} catch (Exception e) {
				response = Response.status(500).build();
			}

			return response;
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires (ticket ?, casUrlValidation) :"+casUrlValidation);
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
			
		}
	}

	/**
	 *	Ask to logout, clear session
	 *	POST /rest/api/credential/logout
	 *	parameters:
	 *	return:
	 **/
	@Path("/credential/logout")
	@POST
	public Response logout(@Context ServletConfig sc, @Context HttpServletRequest httpServletRequest)
	{
		HttpSession session = httpServletRequest.getSession(false);
		if( session != null )
			session.invalidate();
		return  Response.ok("logout").build();
	}

	/**
	 *	Fetch node content
	 *	GET /rest/api/nodes/{node-id}
	 *	parameters:
	 *	return:
	 **/
	@Path("/nodes/{node-id}")
	@GET
	@Consumes(MediaType.APPLICATION_XML)
	public String getNodeWithXSL( @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("node-id") String nodeUuid, @QueryParam("xsl-file") String xslFile, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept, @QueryParam("user") Integer userId, @QueryParam("lang") String lang, @QueryParam("p1") String p1, @QueryParam("p2") String p2, @QueryParam("p3") String  p3)
	{
		if( !isUUID(nodeUuid) )
		{
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}

		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;

		try
		{
			// When we need more parameters, arrange this with format "par1:par1val;par2:par2val;..."
			String parameters = "lang:"+lang;

			javax.servlet.http.HttpSession session = httpServletRequest.getSession(true);
			String ppath = session.getServletContext().getRealPath(File.separator);

			c = SqlUtils.getConnection(servContext);
			/// webapps...
			ppath = ppath.substring(0,ppath.lastIndexOf(File.separator, ppath.length()-2)+1);
			xslFile = ppath+xslFile;
			String returnValue = dataProvider.getNodeWithXSL(c, new MimeType("text/xml"),nodeUuid,xslFile, parameters, ui.userId, groupId).toString();
			if(returnValue.length() != 0)
			{
				if(MediaType.APPLICATION_JSON.equals(accept))
					returnValue = XML.toJSONObject(returnValue).toString();
				logRestRequest(httpServletRequest, null, returnValue, Status.OK.getStatusCode());
			}
			else
			{
				throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
			}


			return returnValue;
		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
		}

		catch(NullPointerException ex)
		{
			logRestRequest(httpServletRequest, null,null, Status.NOT_FOUND.getStatusCode());

			throw new RestWebApplicationException(Status.NOT_FOUND, "Node "+nodeUuid+" not found or xsl not found :"+ex.getMessage());
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, null, ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *
	 *	POST /rest/api/nodes/{node-id}/frommodelbysemantictag/{semantic-tag}
	 *	parameters:
	 *	return:
	 **/
	@Path("/nodes/{node-id}/frommodelbysemantictag/{semantic-tag}")
	@POST
	@Consumes(MediaType.APPLICATION_XML)
	@Produces(MediaType.APPLICATION_XML)
	public String postNodeFromModelBySemanticTag(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("node-id") String nodeUuid, @PathParam("semantic-tag") String semantictag, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("user") Integer userId )
	{
		if( !isUUID(nodeUuid) )
		{
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}

		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		Connection c = null;

		try
		{
			c = SqlUtils.getConnection(servContext);
			String returnValue = dataProvider.postNodeFromModelBySemanticTag(c, new MimeType("text/xml"),nodeUuid,semantictag, ui.userId, groupId).toString();
			logRestRequest(httpServletRequest, xmlNode, returnValue, Status.OK.getStatusCode());

			if(returnValue == "faux")
			{
				throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
			}

			return returnValue;
		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, xmlNode,ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Import zip file
	 *	POST /rest/api/portfolios/zip
	 *	parameters:
	 *	return:
	 **/
	@Path("/portfolios/zip")
	@POST
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Produces(MediaType.TEXT_PLAIN)
	public String postPortfolioByForm( @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("user") Integer userId, @QueryParam("model") String modelId, @FormDataParam("uploadfile") InputStream uploadedInputStream, @FormDataParam("instance") String instance, @FormDataParam("project") String projectName)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		String returnValue = "";
		Connection c = null;

		try
		{
			boolean instantiate = false;
			if( "true".equals(instance) )
				instantiate = true;

			c = SqlUtils.getConnection(servContext);
			returnValue = dataProvider.postPortfolioZip(c, new MimeType("text/xml"),new MimeType("text/xml"), httpServletRequest, uploadedInputStream, ui.userId, groupId, modelId, ui.subId, instantiate, projectName).toString();
		}
		catch( RestWebApplicationException e )
		{
			throw e;
		}
		catch( Exception e )
		{
			e.printStackTrace();
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}

		return returnValue;
	}

	/**
	 *	Fetch userlist from a role and portfolio id
	 *	GET /rest/api/users/Portfolio/{portfolio-id}/Role/{role}/users
	 *	parameters:
	 *	return:
	 **/
	@Path("/users/Portfolio/{portfolio-id}/Role/{role}/users")
	@GET
	@Produces(MediaType.APPLICATION_XML)
	public String getUsersByRole(@CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @PathParam("portfolio-id") String portfolioUuid, @PathParam("role") String role, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		if( !isUUID(portfolioUuid) )
		{
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}

		UserInfo ui = checkCredential(httpServletRequest, user, token, group);
		Connection c = null;

		try
		{
			c = SqlUtils.getConnection(servContext);
			String xmlUsers = dataProvider.getUsersByRole(c, ui.userId, portfolioUuid, role);
			logRestRequest(httpServletRequest, "", xmlUsers, Status.OK.getStatusCode());

			return xmlUsers;
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, "", ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Fetch groups from a role and portfolio id
	 *	GET /rest/api/users/Portfolio/{portfolio-id}/Role/{role}/groups
	 *	parameters:
	 *	return:
	 **/
	@Path("/users/Portfolio/{portfolio-id}/Role/{role}/groups")
	@GET
	@Produces(MediaType.APPLICATION_XML)
	public String getGroupsByRole(@CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @PathParam("portfolio-id") String portfolioUuid, @PathParam("role") String role, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		if( !isUUID(portfolioUuid) )
		{
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}

		UserInfo ui = checkCredential(httpServletRequest, user, token, group);
		Connection c = null;

		try
		{
			c = SqlUtils.getConnection(servContext);
			String xmlGroups = dataProvider.getGroupsByRole(c, ui.userId, portfolioUuid, role);
			logRestRequest(httpServletRequest, "", xmlGroups, Status.OK.getStatusCode());

			return xmlGroups;
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, "", ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/********************************************************/
	/**
	 * ##   ##  #####  ####### #####     ###   ######
	 * ##   ## ##   ## ##      ##   ## ##   ## ##   ##
	 * ##   ## ##      ##      ##   ## ##      ##   ##
	 * ##   ##  #####  ####    #####   ##  ### ######
	 * ##   ##      ## ##      ##   ## ##   ## ##   ##
	 * ##   ## ##   ## ##      ##   ## ##   ## ##   ##
	 *  #####   #####  ####### ##   ##   ###   ##   ##
  /** Managing and listing user groups
	/********************************************************/
	/**
	 *	Create a new user group
	 *	POST /rest/api/usersgroups
	 *	parameters:
	 *	 - label: Name of the group we are creating
	 *	return:
	 *   - groupid
	 **/
	@Path("/usersgroups")
	@POST
	public String postUserGroup(@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("label") String groupname)
	{
		UserInfo ui = checkCredential(httpServletRequest, null, null, null);
		int response = -1;
		Connection c = null;

		try
		{
			c = SqlUtils.getConnection(servContext);
			response = dataProvider.postUserGroup(c, groupname, ui.userId);
			logRestRequest(httpServletRequest, "", "Add user in group", Status.OK.getStatusCode());
			
			if( response == -1 )
				throw new RestWebApplicationException(Status.NOT_MODIFIED, "Error in creation");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, "", ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}

		return Integer.toString(response);
	}

	/**
	 *	Put a user in user group
	 *	PUT /rest/api/usersgroups
	 *	parameters:
	 *	 - group: group id
	 *	 - user: user id
	 *	 - label: label
	 *	return:
	 *	 Code 200
	 **/
	@Path("/usersgroups")
	@PUT
	public Response putUserInUserGroup(@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("group") Integer group, @QueryParam("user") Integer user, @QueryParam("label") String label)
	{
		UserInfo ui = checkCredential(httpServletRequest, null, null, null);
		Connection c = null;
		try
		{
			c = SqlUtils.getConnection(servContext);
			boolean isOK=false;
			if( label != null )
			{
				// Rename group
				isOK = dataProvider.putUserGroupLabel(c, user, group, label);
			}
			else
			{
				// Add user in group
				isOK = dataProvider.putUserInUserGroup(c, user, group, ui.userId);
				logRestRequest(httpServletRequest, "", "Add user in group", Status.OK.getStatusCode());
			}
			if( isOK )
				return Response.status(200).entity("Changed").build();
			else
				return Response.status(200).entity("Not OK").build();
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, "", ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Get users by usergroup, or if there's no group id give, give the list of user group
	 *	GET /rest/api/usersgroups
	 *	parameters:
	 *	 - group: group id
	 *	return:
	 *	 - Without group id
	 *		<groups>
	 *			<group id={groupid}>
	 *				<label>{group name}</label>
	 *			</group>
	 *			...
	 *		</groups>
	 *
	 *	 - With group id
	 *		<group id={groupid}>
	 *			<user id={userid}></user>
	 *			...
	 *		</group>
	 **/
	@Path("/usersgroups")
	@GET
	public String getUsersByUserGroup(@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("group") Integer group, @QueryParam("user") Integer user)
	{
		UserInfo ui = checkCredential(httpServletRequest, null, null, null);

		Connection c = null;
		String xmlUsers = "";
		try
		{
			c = SqlUtils.getConnection(servContext);
			if( user != null )
				xmlUsers = dataProvider.getGroupByUser(c, user, ui.userId);
			else if( group == null )
				xmlUsers = dataProvider.getUserGroupList(c, ui.userId);
			else
				xmlUsers = dataProvider.getUsersByUserGroup(c, group, ui.userId);
			logRestRequest(httpServletRequest, "", xmlUsers, Status.OK.getStatusCode());
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, "", ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}

		return xmlUsers;
	}

	/**
	 *	Remove a user from a user group, or remove a usergroup
	 *	DELETE /rest/api/usersgroups
	 *	parameters:
	 *	 - group: group id
	 *	 - user: user id
	 *	return:
	 *	 Code 200
	 **/
	@Path("/usersgroups")
	@DELETE
	public String deleteUsersByUserGroup(@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("group") int group, @QueryParam("user") Integer user)
	{
		UserInfo ui = checkCredential(httpServletRequest, null, null, null);
		String response = "";
		Connection c = null;
		Boolean isOK=false;

		try
		{
			c = SqlUtils.getConnection(servContext);
			if( user == null )
				isOK = dataProvider.deleteUsersGroups(c, group, ui.userId);
			else
				isOK = dataProvider.deleteUsersFromUserGroups(c, user, group, ui.userId);
			logRestRequest(httpServletRequest, "", response, Status.OK.getStatusCode());

		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, "", ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
		return response;
	}

	/********************************************************/
	/**
	 * ######   #####  ######  #######   ###   ######
	 * ##   ## ##   ## ##   ##    #    ##   ## ##   ##
	 * ##   ## ##   ## ##   ##    #    ##      ##   ##
	 * ######  ##   ## ######     #    ##  ### ######
	 * ##      ##   ## ## ##      #    ##   ## ##   ##
	 * ##      ##   ## ##  ##     #    ##   ## ##   ##
	 * ##       #####  ##   ##    #      ###   ##   ##
  /** Managing and listing portfolios
	/********************************************************/
	/**
	 *	Create a new portfolio group
	 *	POST /rest/api/portfoliogroups
	 *	parameters:
	 *	 - label: Name of the group we are creating
	 *	 - parent: parentid
	 *	 - type: group/portfolio
	 *	return:
	 *   - groupid
	 **/
	@Path("/portfoliogroups")
	@POST
	public Response postPortfolioGroup(@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("label") String groupname, @QueryParam("type") String type, @QueryParam("parent") Integer parent)
	{
		UserInfo ui = checkCredential(httpServletRequest, null, null, null);
		int response = -1;
		Connection c = null;

		// Check type value
		try
		{
			c = SqlUtils.getConnection(servContext);
			response = dataProvider.postPortfolioGroup(c, groupname, type, parent, ui.userId);
			logRestRequest(httpServletRequest, "", "Add portfolio group", Status.OK.getStatusCode());
			
			if( response == -1 )
			{
				return Response.status(Status.NOT_MODIFIED).entity("Error in creation").build();
			}
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, "", ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}

		return Response.ok(Integer.toString(response)).build();
	}

	/**
	 *	Put a portfolio in portfolio group
	 *	PUT /rest/api/portfoliogroups
	 *	parameters:
	 *	 - group: group id
	 *	 - uuid: portfolio id
	 *	return:
	 *	 Code 200
	 **/
	@Path("/portfoliogroups")
	@PUT
	public Response putPortfolioInPortfolioGroup(@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("group") Integer group, @QueryParam("uuid") String uuid, @QueryParam("label") String label )
	{
		UserInfo ui = checkCredential(httpServletRequest, null, null, null);
		Connection c = null;
		
		try
		{
			c = SqlUtils.getConnection(servContext);
			int response = -1;
			response = dataProvider.putPortfolioInGroup(c, uuid, group, label, ui.userId);
			logRestRequest(httpServletRequest, "", "Add user in group", Status.OK.getStatusCode());

			return Response.ok(Integer.toString(response)).build();
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, "", ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Get portfolio by portfoliogroup, or if there's no group id give, give the list of portfolio group
	 *	GET /rest/api/portfoliogroups
	 *	parameters:
	 *	 - group: group id
	 *	return:
	 *	 - Without group id
	 *		<groups>
	 *			<group id={groupid}>
	 *				<label>{group name}</label>
	 *			</group>
	 *			...
	 *		</groups>
	 *
	 *	 - With group id
	 *		<group id={groupid}>
	 *			<portfolio id={uuid}></portfolio>
	 *			...
	 *		</group>
	 **/
	@Path("/portfoliogroups")
	@GET
	public String getPortfolioByPortfolioGroup(@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("group") Integer group, @QueryParam("uuid") String portfolioid)
	{
		UserInfo ui = checkCredential(httpServletRequest, null, null, null);
		Connection c = null;
		String xmlUsers = "";
		
		try
		{
			c = SqlUtils.getConnection(servContext);
			if( portfolioid != null )
			{
				xmlUsers = dataProvider.getPortfolioGroupListFromPortfolio(c, portfolioid, ui.userId);
			}
			else if( group == null )
				xmlUsers = dataProvider.getPortfolioGroupList(c, ui.userId);
			else
				xmlUsers = dataProvider.getPortfolioByPortfolioGroup(c, group, ui.userId);
			logRestRequest(httpServletRequest, "", xmlUsers, Status.OK.getStatusCode());
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, "", ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}

		return xmlUsers;
	}

	/**
	 *	Remove a portfolio from a portfolio group, or remove a portfoliogroup
	 *	DELETE /rest/api/portfoliogroups
	 *	parameters:
	 *	 - group: group id
	 *	 - uuid: portfolio id
	 *	return:
	 *	 Code 200
	 **/
	@Path("/portfoliogroups")
	@DELETE
	public String deletePortfolioByPortfolioGroup(@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("group") int group, @QueryParam("uuid") String uuid)
	{
		UserInfo ui = checkCredential(httpServletRequest, null, null, null);
		String response = "";
		Connection c = null;

		try
		{
			c = SqlUtils.getConnection(servContext);
			if( uuid == null )
				response = dataProvider.deletePortfolioGroups(c, group, ui.userId);
			else
				response = dataProvider.deletePortfolioFromPortfolioGroups(c, uuid, group, ui.userId);
			logRestRequest(httpServletRequest, "", response, Status.OK.getStatusCode());

		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, "", ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
		return response;
	}

	/********************************************************/
	/**
	 * ##   ##   ###     ###   #####     ###
	 * ### ### ##   ## ##   ## ##   ## ##   ##
	 * ## # ## ##   ## ##      ##   ## ##   ##
	 * ##   ## ####### ##      #####   ##   ##
	 * ##   ## ##   ## ##      ##   ## ##   ##
	 * ##   ## ##   ## ##   ## ##   ## ##   ##
	 * ##   ## ##   ##   ###   ##   ##   ###
  /** Partie utilisation des macro-commandes et gestion **/
	/********************************************************/

	/**
	 *	Executing pre-defined macro command on a node
	 *	POST /rest/api/action/{uuid}/{macro-name}
	 *	parameters:
	 *	return:
	 **/
	@Path("/action/{uuid}/{macro-name}")
	@POST
	@Consumes(MediaType.APPLICATION_XML+","+MediaType.TEXT_PLAIN)
	@Produces(MediaType.TEXT_PLAIN)
	public String postMacro(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @PathParam("uuid") String uuid, @PathParam("macro-name") String macroName,
			@Context ServletConfig sc, @Context HttpServletRequest httpServletRequest)
	{
		if( !isUUID(uuid) )
		{
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}

		UserInfo ui = checkCredential(httpServletRequest, user, token, group);

		String returnValue="";
		Connection c = null;
		try
		{
			c = SqlUtils.getConnection(servContext);
			// On execute l'action sur le noeud uuid
			if( uuid != null && macroName != null )
			{
				returnValue = dataProvider.postMacroOnNode(c, ui.userId, uuid, macroName);
				logRestRequest(httpServletRequest, xmlNode, returnValue, Status.OK.getStatusCode());

				if(returnValue == "faux")
				{
					throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
				}
			}
			// Erreur de requ�te
			else
			{
				returnValue = "";
			}

			return returnValue;
		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, xmlNode,ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/********************************************************/
	/**
	 * ######  #######   ###   ##   ## #######  #####
	 * ##   ##    #    ##   ## ##   ##    #    ##   ##
	 * ##   ##    #    ##      ##   ##    #    ##
	 * ######     #    ##  ### #######    #     #####
	 * ##   ##    #    ##   ## ##   ##    #         ##
	 * ##   ##    #    ##   ## ##   ##    #    ##   ##
	 * ##   ## #######   ###   ##   ##    #     #####
	/** Partie groupe de droits et utilisateurs            **/
	/********************************************************/

	/**
	 *	Change rights
	 *	POST /rest/api/rights
	 *	parameters:
	 *	return:
	 **/
	@Path("/rights")
	@POST
	@Produces(MediaType.APPLICATION_XML)
	public String postChangeRights(String xmlNode, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest )
	{
		UserInfo ui = checkCredential(httpServletRequest, null, null, null);

		String returnValue="";
		Connection c = null;
		try
		{
			/**
			 * <node uuid="">
			 *   <role name="">
			 *     <right RD="" WR="" DL="" />
			 *     <action>reset</action>
			 *   </role>
			 * </node>
			 *======
			 * <portfolio uuid="">
			 *   <xpath>XPATH</xpath>
			 *   <role name="">
			 *     <right RD="" WR="" DL="" />
			 *     <action>reset</action>
			 *   </role>
			 * </portfolio>
			 *======
			 * <portfoliogroup name="">
			 *   <xpath>XPATH</xpath>
			 *   <role name="">
			 *     <right RD="" WR="" DL="" />
			 *     <action>reset</action>
			 *   </role>
			 * </portfoliogroup>
			 **/

			DocumentBuilderFactory documentBuilderFactory =DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
			Document doc = documentBuilder.parse(new ByteArrayInputStream(xmlNode.getBytes("UTF-8")));

			XPath xPath = XPathFactory.newInstance().newXPath();
			ArrayList<String> portfolio = new ArrayList<String>();
//			String xpathRole = "//role";
			String xpathRole = "//*[local-name()='role']";
			XPathExpression findRole = xPath.compile(xpathRole);
//			String xpathNodeFilter = "//xpath";
			String xpathNodeFilter = "//*[local-name()='xpath']";
			XPathExpression findXpath = xPath.compile(xpathNodeFilter);
			String nodefilter = "";
			NodeList roles = null;

			/// Fetch portfolio(s)
//			String portfolioNode = "//portfoliogroup";
			String portfolioNode = "//*[local-name()='portfoliogroup']";
			XPathExpression xpathFilter=null;
			Node portgroupnode = (Node) xPath.compile(portfolioNode).evaluate(doc, XPathConstants.NODE);
			if( portgroupnode != null )
			{
				String portgroupname = portgroupnode.getAttributes().getNamedItem("name").getNodeValue();

				Node xpathNode = (Node) findXpath.evaluate(portgroupnode, XPathConstants.NODE);
				nodefilter = xpathNode.getNodeValue();
				xpathFilter = xPath.compile(nodefilter);
				roles = (NodeList) findRole.evaluate(portgroupnode, XPathConstants.NODESET);
			}
			else
			{
				// Or add the single one
//				portfolioNode = "//portfolio[@uuid]";
				portfolioNode = "//*[local-name()='portfolio' and @*[local-name()='uuid']";
				Node portnode = (Node) xPath.compile(portfolioNode).evaluate(doc, XPathConstants.NODE);
				if( portnode != null )
				{
					portfolio.add(portnode.getNodeValue());

					Node xpathNode = (Node) findXpath.evaluate(portnode, XPathConstants.NODE);
					nodefilter = xpathNode.getNodeValue();
					xpathFilter = xPath.compile(nodefilter);
					roles = (NodeList) findRole.evaluate(portnode, XPathConstants.NODESET);
				}
			}

			c = SqlUtils.getConnection(servContext);
			ArrayList<String> nodes = new ArrayList<String>();
			for( int i=0; i<portfolio.size(); ++i )	// For all portfolio
			{
				String portfolioUuid = portfolio.get(i);
				String portfolioStr = dataProvider.getPortfolio(c, new MimeType("text/xml"),portfolioUuid,ui.userId, 0, this.label, null, null, ui.subId, null).toString();
				Document docPort = documentBuilder.parse(new ByteArrayInputStream(portfolioStr.getBytes("UTF-8")));

				/// Fetch nodes inside those portfolios
				NodeList portNodes = (NodeList) xpathFilter.evaluate(docPort, XPathConstants.NODESET);
				for( int j=0; j<portNodes.getLength(); ++j )
				{
					Node node = portNodes.item(j);
					String nodeuuid = node.getAttributes().getNamedItem("id").getNodeValue();

					nodes.add(nodeuuid);	// Keep those we have to change rights
				}
			}

			/// Fetching single node
			if( nodes.isEmpty() )
			{
//				String singleNode = "//node";
				String singleNode = "//*[local-name()='node']";
				Node sNode = (Node) xPath.compile(singleNode).evaluate(doc, XPathConstants.NODE);
				String uuid = sNode.getAttributes().getNamedItem("uuid").getNodeValue();
				nodes.add(uuid);
				roles = (NodeList) findRole.evaluate(sNode, XPathConstants.NODESET);
			}

			/// For all roles we have to change
			for( int i=0; i<roles.getLength(); ++i )
			{
				Node rolenode = roles.item(i);
				String rolename = rolenode.getAttributes().getNamedItem("name").getNodeValue();
				Node right = rolenode.getFirstChild();

				//
				if( "user".equals(rolename) )
				{
					/// username as role
				}

				if( "#text".equals(right.getNodeName()) )
					right = right.getNextSibling();

				if( "right".equals(right.getNodeName()) )	// Changing node rights
				{
					NamedNodeMap rights = right.getAttributes();

					NodeRight noderight = new NodeRight(null,null,null,null,null,null);

					String val = rights.getNamedItem("RD").getNodeValue();
					if( val != null )
						noderight.read = Boolean.parseBoolean(val);
					val = rights.getNamedItem("WR").getNodeValue();
					if( val != null )
						noderight.write = Boolean.parseBoolean(val);
					val = rights.getNamedItem("DL").getNodeValue();
					if( val != null )
						noderight.delete = Boolean.parseBoolean(val);
					val = rights.getNamedItem("SB").getNodeValue();
					if( val != null )
						noderight.submit = Boolean.parseBoolean(val);


					/// Apply modification for all nodes
					for( int j=0; j<nodes.size(); ++j )
					{
						String nodeid = nodes.get(j);

						// change right
						dataProvider.postRights(c, ui.userId, nodeid, rolename, noderight);
					}
				}
				else if( "action".equals(right.getNodeName()) )	// Using an action on node
				{
					/// Apply modification for all nodes
					for( int j=0; j<nodes.size(); ++j )
					{
						String nodeid = nodes.get(j);

						// TODO: check for reset keyword
						// reset right
						dataProvider.postMacroOnNode(c, ui.userId, nodeid, "reset");
					}
				}
			}

			logRestRequest(httpServletRequest, xmlNode, returnValue, Status.OK.getStatusCode());

			if(returnValue == "faux")
			{
				throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
			}

			return returnValue;
		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, xmlNode,ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	List roles
	 *	GET /rest/api/rolerightsgroups
	 *	parameters:
	 *	return:
	 **/
	@Path("/rolerightsgroups")
	@GET
	@Produces(MediaType.APPLICATION_XML)
	public String getRightsGroup( @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("portfolio") String portfolio, @QueryParam("user") Integer queryuser, @QueryParam("role") String role)
	{
		if( !isUUID(portfolio) )
		{
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}

		UserInfo ui = checkCredential(httpServletRequest, user, token, group);

		String returnValue="";
		Connection c = null;
		try
		{
			c = SqlUtils.getConnection(servContext);
			// Retourne le contenu du type
			returnValue = dataProvider.getRRGList(c, ui.userId, portfolio, queryuser, role);
			logRestRequest(httpServletRequest, "getRightsGroup", returnValue, Status.OK.getStatusCode());

			if(returnValue == "faux")
			{
				throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
			}

			return returnValue;
		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, "getRightsGroup",ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	List all users in a specified roles
	 *	GET /rest/api/rolerightsgroups/all/users
	 *	parameters:
	 *	return:
	 **/
	@Path("/rolerightsgroups/all/users")
	@GET
	@Produces(MediaType.APPLICATION_XML)
	public String getPortfolioRightInfo( @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("portfolio") String portId )
	{
		if( !isUUID(portId) )
		{
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}

		UserInfo ui = checkCredential(httpServletRequest, user, token, group);

		String returnValue="";
		Connection c = null;
		try
		{
			c = SqlUtils.getConnection(servContext);
			// Retourne le contenu du type
			if( portId != null )
			{
				returnValue = dataProvider.getPortfolioInfo(c, ui.userId, portId);
				logRestRequest(httpServletRequest, "getPortfolioRightInfo", returnValue, Status.OK.getStatusCode());

				if(returnValue == "faux")
				{
					throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
				}

			}

			return returnValue;
		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, "getPortfolioRightInfo",ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	List rights in the specified role
	 *	GET /rest/api/rolerightsgroups/rolerightsgroup/{rolerightsgroup-id}
	 *	parameters:
	 *	return:
	 **/
	@Path("/rolerightsgroups/rolerightsgroup/{rolerightsgroup-id}")
	@GET
	@Produces(MediaType.APPLICATION_XML)
	public String getRightInfo( @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @PathParam("rolerightsgroup-id") Integer rrgId )
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, group);

		String returnValue="";
		Connection c = null;
		try
		{
			c = SqlUtils.getConnection(servContext);
			// Retourne le contenu du type
			if( rrgId != null )
			{
				returnValue = dataProvider.getRRGInfo(c, ui.userId, rrgId);
				logRestRequest(httpServletRequest, "getRightInfo", returnValue, Status.OK.getStatusCode());

				if(returnValue == "faux")
				{
					throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
				}

			}

			return returnValue;
		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, "getRightInfo",ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Change a right in role
	 *	PUT /rest/api/rolerightsgroups/rolerightsgroup/{rolerightsgroup-id}
	 *	parameters:
	 *	return:
	 **/
	@Path("/rolerightsgroups/rolerightsgroup/{rolerightsgroup-id}")
	@PUT
	@Produces(MediaType.APPLICATION_XML)
	public String putRightInfo(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @PathParam("rolerightsgroup-id") Integer rrgId )
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, group);

		String returnValue="";
		Connection c = null;
		try
		{
			c = SqlUtils.getConnection(servContext);
			// Retourne le contenu du type
			if( rrgId != null )
			{
				returnValue = dataProvider.putRRGUpdate(c, ui.userId, rrgId, xmlNode);
				logRestRequest(httpServletRequest, xmlNode, returnValue, Status.OK.getStatusCode());

				if(returnValue == "faux")
				{
					throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
				}

			}

			return returnValue;
		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, xmlNode,ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Add a role in the portfolio
	 *	POST /rest/api/rolerightsgroups/{portfolio-id}
	 *	parameters:
	 *	return:
	 **/
	@Path("/rolerightsgroups/{portfolio-id}")
	@POST
	@Produces(MediaType.APPLICATION_XML)
	public String postRightGroups(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @PathParam("portfolio-id") String portfolio, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest )
	{
		if( !isUUID(portfolio) )
		{
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}

		UserInfo ui = checkCredential(httpServletRequest, user, token, group);

		/**
		 * <node>LABEL</node>
		 */

		String returnValue="";
		Connection c = null;
		try
		{
			c = SqlUtils.getConnection(servContext);
			returnValue = dataProvider.postRRGCreate(c, ui.userId, portfolio, xmlNode);
			logRestRequest(httpServletRequest, xmlNode, returnValue, Status.OK.getStatusCode());

			if(returnValue == "faux")
			{
				throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
			}

			return returnValue;
		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, xmlNode,ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Add user in a role
	 *	POST /rest/api/rolerightsgroups/rolerightsgroup/{rolerightsgroup-id}/users
	 *	parameters:
	 *	return:
	 **/
	@Path("/rolerightsgroups/rolerightsgroup/{rolerightsgroup-id}/users")
	@POST
	@Produces(MediaType.APPLICATION_XML)
	public String postRightGroupUser(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @PathParam("rolerightsgroup-id") Integer rrgId )
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, group);

		String returnValue="";
		Connection c = null;
		try
		{
			c = SqlUtils.getConnection(servContext);
			returnValue = dataProvider.postRRGUsers(c, ui.userId, rrgId, xmlNode);
			logRestRequest(httpServletRequest, xmlNode, returnValue, Status.OK.getStatusCode());

			if(returnValue == "faux")
			{
				throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
			}

			return returnValue;
		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, xmlNode,ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Add user in a role
	 *	POST /rest/api/rolerightsgroups/rolerightsgroup/{rolerightsgroup-id}/users/user/{user-id}
	 *	parameters:
	 *	return:
	 **/
	@Path("/rolerightsgroups/rolerightsgroup/{rolerightsgroup-id}/users/user/{user-id}")
	@POST
	@Produces(MediaType.APPLICATION_XML)
	public String postRightGroupUsers(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @PathParam("rolerightsgroup-id") Integer rrgId, @PathParam("user-id") Integer queryuser )
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, group);

		String returnValue="";
		Connection c = null;
		try
		{
			c = SqlUtils.getConnection(servContext);
			returnValue = dataProvider.postRRGUser(c, ui.userId, rrgId, queryuser);
			logRestRequest(httpServletRequest, xmlNode, returnValue, Status.OK.getStatusCode());

			if(returnValue == "faux")
			{
				throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
			}

			return returnValue;
		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, xmlNode,ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Delete a role
	 *	DELETE /rest/api/rolerightsgroups/rolerightsgroup/{rolerightsgroup-id}
	 *	parameters:
	 *	return:
	 **/
	@Path("/rolerightsgroups/rolerightsgroup/{rolerightsgroup-id}")
	@DELETE
	@Produces(MediaType.APPLICATION_XML)
	public String deleteRightGroup(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @PathParam("rolerightsgroup-id") Integer rrgId )
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, group);

		String returnValue="";
		Connection c = null;
		try
		{
			c = SqlUtils.getConnection(servContext);
			returnValue = dataProvider.deleteRRG(c, ui.userId, rrgId);
			logRestRequest(httpServletRequest, xmlNode, returnValue, Status.OK.getStatusCode());

			if(returnValue == "faux")
			{
				throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
			}

			return returnValue;
		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, xmlNode,ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Remove user from a role
	 *	DELETE /rest/api/rolerightsgroups/rolerightsgroup/{rolerightsgroup-id}/users/user/{user-id}
	 *	parameters:
	 *	return:
	 **/
	@Path("/rolerightsgroups/rolerightsgroup/{rolerightsgroup-id}/users/user/{user-id}")
	@DELETE
	@Produces(MediaType.APPLICATION_XML)
	public String deleteRightGroupUser(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @PathParam("rolerightsgroup-id") Integer rrgId, @PathParam("user-id") Integer queryuser )
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, group);

		String returnValue="";
		Connection c = null;
		try
		{
			c = SqlUtils.getConnection(servContext);
			returnValue = dataProvider.deleteRRGUser(c, ui.userId, rrgId, queryuser);
			logRestRequest(httpServletRequest, xmlNode, returnValue, Status.OK.getStatusCode());

			if(returnValue == "faux")
			{
				throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
			}

			return returnValue;
		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, xmlNode,ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Remove all users from a role
	 *	DELETE /rest/api/rolerightsgroups/all/users
	 *	parameters:
	 *	return:
	 **/
	@Path("/rolerightsgroups/all/users")
	@DELETE
	@Produces(MediaType.APPLICATION_XML)
	public String deletePortfolioRightInfo(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("portfolio") String portId )
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, group);

		String returnValue="";
		Connection c = null;
		try
		{
			c = SqlUtils.getConnection(servContext);
			// Retourne le contenu du type
			if( portId != null )
			{
				returnValue = dataProvider.deletePortfolioUser(c, ui.userId, portId);
				logRestRequest(httpServletRequest, xmlNode, returnValue, Status.OK.getStatusCode());

				if(returnValue == "faux")
				{
					throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
				}

			}

			return returnValue;
		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, xmlNode,ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}
	}

	/**
	 *	Ning related
	 *	GET /rest/api/ning/activities
	 *	parameters:
	 *	return:
	 **/
	@Path("/ning/activities")
	@GET
	@Produces(MediaType.APPLICATION_XML)
	public String getNingActivities( @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest,
			@QueryParam("type") Integer type)
	{
		checkCredential(httpServletRequest, user, token, group);

		Ning ning = new Ning();
		return ning.getXhtmlActivites();
	}

	/**
	 *	elgg related
	 *	GET /rest/api/elgg/site/river_feed
	 *	parameters:
	 *	return:
	 **/
	@Path("/elgg/site/river_feed")
	@GET
	@Produces(MediaType.TEXT_HTML)
	public String getElggSiteRiverFeed( @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest,
			@QueryParam("type") Integer type, @QueryParam("limit") String limit)
	{
		int iLimit;
		try
		{
			iLimit = Integer.parseInt(limit);
		}
		catch(Exception ex)
		{
			iLimit = 20;
		}
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		System.out.println(ui.User);
		try
		{
			Elgg elgg = new Elgg(elggDefaultApiUrl,elggDefaultSiteUrl,elggApiKey,ui.User,  elggDefaultUserPassword);
			return elgg.getSiteRiverFeed(iLimit);
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, "",javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
	}

	/**
	 *	elgg related
	 *	POST /rest/api/elgg/wire
	 *	parameters:
	 *	return:
	 **/
	@Path("/elgg/wire")
	@POST
	@Produces(MediaType.APPLICATION_XML)
	public String getElggSiteRiverFeed(String message, @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest,
			@QueryParam("type") Integer type)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			Elgg elgg = new Elgg(elggDefaultApiUrl,elggDefaultSiteUrl,elggApiKey,ui.User, elggDefaultUserPassword);
			return elgg.postWire(message);
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, "",javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
	}

	public boolean isUUID(String uuidstr)
	{
		try
		{
			UUID uuid = UUID.fromString(uuidstr);
		}
		catch( Exception e )
		{
			return false;
		}
		
		return true;
	}

}
