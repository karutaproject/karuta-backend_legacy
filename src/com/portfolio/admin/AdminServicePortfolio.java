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

package com.portfolio.admin;

import java.util.Enumeration;

import javax.activation.MimeType;
import javax.servlet.ServletConfig;
import javax.servlet.http.Cookie;
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
import javax.ws.rs.core.UriInfo;

import com.portfolio.data.provider.AdminProvider;
import com.portfolio.data.utils.javaUtils;
import com.portfolio.rest.RestWebApplicationException;


@Path("/api")
public class AdminServicePortfolio {
	@Context ServletConfig sc;
	@Context
	UriInfo uriInfo;

	AdminProvider dataProvider;
	int userId = 0;
	int groupId = -1;
	int logRestRequests = 0;
	String label = null;
	HttpSession session;

	private static final String NETWORK_NAME = "Google";
	private static final String AUTHORIZE_URL = "https://www.google.com/accounts/OAuthAuthorizeToken?oauth_token=";
	private static final String PROTECTED_RESOURCE_URL = "https://docs.google.com/feeds/default/private/full/";
	private static final String SCOPE = "https://docs.google.com/feeds/";

	public void logRestRequest(HttpServletRequest httpServletRequest, String inBody, String outBody, int httpCode)
	{

		try
		{
			if(logRestRequests==1)
			{
				String httpHeaders = "";
				Enumeration headerNames = httpServletRequest.getHeaderNames();
				while(headerNames.hasMoreElements()) {
					String headerName = (String)headerNames.nextElement();
					httpHeaders += headerName+": "+httpServletRequest.getHeader(headerName)+"\n";
				}
				String url = httpServletRequest.getRequestURL().toString();
				if(httpServletRequest.getQueryString()!=null) url += "?" + httpServletRequest.getQueryString().toString();
				dataProvider.writeLog(url,
						httpServletRequest.getMethod().toString(),
						httpHeaders,
						inBody, outBody, httpCode);
			}
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
		}
	}

	public void initService( HttpServletRequest request )
	{
		try
		{
			this.session = request.getSession(true);
			String dataProviderName  =  this.sc.getInitParameter("adminProviderClass");
			dataProvider = (AdminProvider)Class.forName(dataProviderName).newInstance();
		}
		catch ( Exception e ){}
	}

	public void checkCredential(HttpServletRequest request, String login, String token, String group )
	{
		initService(request);
		Integer val = (Integer) session.getAttribute("uid");
		if( val != null )
			this.userId = val;
		val = (Integer) session.getAttribute("gid");
		if( val != null )
			this.groupId = val;
	}

	public void initialize(HttpServletRequest httpServletRequest, boolean doCheckCredentials){

		String dataProviderName  =  this.sc.getInitParameter("dataProviderClass");
		String login = "0";
		String token = "0";
		String Valeur = "0";
		String uid = "0";
		String label = "0";

		try
		{
			logRestRequests = Integer.parseInt(this.sc.getInitParameter("logRestRequests"));
		}
		catch(Exception ex) {}

		try
		{
			// marche pas
			//httpServletRequest.setCharacterEncoding("utf-8");

			//On initialise le dataProvider
			dataProvider = (AdminProvider)Class.forName(dataProviderName).newInstance();

			if(doCheckCredentials)
			{
				login = httpServletRequest.getParameter("login");
				token = httpServletRequest.getParameter("token");


				if(login!=null && token!=null)
				{
					uid = dataProvider.getUserUidByTokenAndLogin(login, token);
					doCheckCredentials = false;

					if(uid == null)
						throw new RestWebApplicationException(Status.FORBIDDEN, "Token non valide... (uid="+uid+",token="+token+")");
					else if(uid.equals("0"))
						throw new RestWebApplicationException(Status.FORBIDDEN, "Token non valide... (uid="+uid+",token="+token+")");
					this.userId = Integer.parseInt(uid);
				}
			}

			if(login==null) login = "";
			if(token==null) token = "";

			if(doCheckCredentials)
			{
				Cookie[] cookies = httpServletRequest.getCookies();

				try
				{
					int a = cookies.length;
					String token1 = "0";

					if (cookies.length >= 0){

						for(int i=0; i < cookies.length; i++)
						{
							Cookie MonCookie = cookies[i];

							if ("user".equals(MonCookie.getName()))
							{
								login = cookies[i].getValue();
							}
							else if ("credential".equals(MonCookie.getName()))
							{
								token = cookies[i].getValue();
							}
							else if ("group".contains(MonCookie.getName()))
							{
								try
								{
									groupId = Integer.parseInt(cookies[i].getValue());
								}
								catch(Exception ex)
								{

								}
							}
							else if ("label".equals(MonCookie.getName()))
							{
								label = cookies[i].getValue();
							}
							else
							{
								Valeur = null;
								uid = "0";
							}
						}
					}
					else uid = "0";
				}
				catch(Exception ex)
				{
					throw new RestWebApplicationException(Status.FORBIDDEN, "Token non valide... (uid="+uid+",token="+token+")");
				}

				if(!login.equals("0") && !token.equals("0"))
				{
					uid = dataProvider.getUserUidByTokenAndLogin(login, token);
				}

				if(uid == null)
					throw new RestWebApplicationException(Status.FORBIDDEN, "Token non valide... (uid="+uid+",token="+token+")");
				else if(uid.equals("0"))
					throw new RestWebApplicationException(Status.FORBIDDEN, "Token non valide... (uid="+uid+",token="+token+")");
				this.userId = Integer.parseInt(uid);
			}

			// Si le credential correspondant au token n'a pas �t� trouv� en base
			// alors on renvoie tout de suite une erreur 403

			if(doCheckCredentials)
			{
				if(uid == null)
					throw new RestWebApplicationException(Status.FORBIDDEN, "Token non valide... (uid="+uid+",token="+token+")");
				else if(uid.equals("0"))
					throw new RestWebApplicationException(Status.FORBIDDEN, "Token non valide... (uid="+uid+",token="+token+")");
			}
		}
		catch(RestWebApplicationException ex)
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, "Token non valide... (uid="+uid+",token="+token+")");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
	}

	@Path("/credential")
	@GET
	@Produces(MediaType.APPLICATION_XML)
	public Response getCredential( @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		checkCredential(httpServletRequest, user, token, group);

		try
		{
			String xmluser = dataProvider.getInfUser(this.userId, this.userId);
			logRestRequest(httpServletRequest, "", xmluser, Status.OK.getStatusCode());

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
			dataProvider.disconnect();
		}
	}



	@Path("/groups")
	@GET
	@Produces(MediaType.APPLICATION_XML)
	public String getGroups(@CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		checkCredential(httpServletRequest, user, token, group);

		try
		{
			String xmlGroups = (String) dataProvider.getUserGroups(this.userId);
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
			dataProvider.disconnect();
		}
	}

	@Path("/grouplist")
	@GET
	@Produces(MediaType.APPLICATION_XML)
	public String getGroupList(@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		checkCredential(httpServletRequest, null, null, null);
		try
		{
			String xmlGroups = dataProvider.getGroupList();
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
			dataProvider.disconnect();
		}
	}

	@Path("/groupuserlist")
	@GET
	@Produces(MediaType.APPLICATION_XML)
	public String getGroupUserList(@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		checkCredential(httpServletRequest, null, null, null);
		try
		{
			String xmlGroups = dataProvider.getGroupUserList();
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
			dataProvider.disconnect();
		}
	}

	@Path("/grouprightlist")
	@GET
	@Produces(MediaType.APPLICATION_XML)
	public String getGroupRightList(@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		checkCredential(httpServletRequest, null, null, null);
		try
		{
			String xmlGroups = dataProvider.getGroupRightList();
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
			dataProvider.disconnect();
		}
	}

	@Path("/users")
	@GET
	@Produces(MediaType.APPLICATION_XML)
	public String getUsers(@CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		checkCredential(httpServletRequest, user, token, group);

		try
		{
			String xmlGroups = dataProvider.getListUsers(this.userId);
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
			dataProvider.disconnect();
		}
	}

	@Path("/users/user/{user-id}")
	@GET
	@Produces(MediaType.APPLICATION_XML)
	public String getUser(@CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @PathParam("user-id") int userid, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		checkCredential(httpServletRequest, user, token, group);

		try
		{
			String xmluser = dataProvider.getInfUser(this.userId, userid);
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
			dataProvider.disconnect();
		}
	}

	@Path("/users/user/{user-id}")
	@PUT
	@Produces(MediaType.APPLICATION_XML)
	public String putUser( String xmlInfUser, @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @PathParam("user-id") int userid, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		checkCredential(httpServletRequest, user, token, group);

		try
		{
			String queryuser = dataProvider.putInfUser(this.userId, userid, xmlInfUser);
			logRestRequest(httpServletRequest, "", queryuser, Status.OK.getStatusCode());

			return queryuser;
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, "", ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, "Error : "+ex.getMessage());
		}
		finally
		{
			dataProvider.disconnect();
		}
	}

	@Path("/users/user/username/{username}")
	@GET
	@Produces(MediaType.APPLICATION_XML)
	public String getUserId(@CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @PathParam("username") String username, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		checkCredential(httpServletRequest, user, token, group);

		try
		{
			String userid = dataProvider.getUserID(this.userId, username);
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
			dataProvider.disconnect();
		}
	}

	@Path("/users/user/{user-id}/groups")
	@GET
	@Produces(MediaType.APPLICATION_XML)
	public String getGroupsUser(@CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @PathParam("user-id") int useridCible, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		checkCredential(httpServletRequest, user, token, group);

		try
		{
			String xmlgroupsUser = dataProvider.getGroupsUser(this.userId, useridCible);
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
			dataProvider.disconnect();
		}
	}

	@Path("/groupRights/{portfolio-id}")
	@GET
	@Produces(MediaType.APPLICATION_XML)
	public String getGroupRights(@CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("groupId") Integer groupId, @PathParam("portfolio-id") String portfolio)
	{
		checkCredential(httpServletRequest, user, token, group);
		try
		{
			String xmlGroups = dataProvider.getPortfolioRights(this.userId, portfolio);
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
			dataProvider.disconnect();
		}
	}

	@Path("/rights/{node-id}/{grid}")
	@POST
	@Consumes(MediaType.APPLICATION_XML)
	@Produces(MediaType.APPLICATION_XML)
	public String postRights(String xml, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @PathParam("grid") Integer grid, @PathParam("node-id") String node)
	{
		/**
		 * <node rd="1" wr="" dl="" sb="" ad="" types="" rules_id="" notify_roles="" />
		 */
		checkCredential(httpServletRequest, null, null, null);
		try
		{
			boolean status = dataProvider.postNodeRight(this.userId, node, grid, xml);
			logRestRequest(httpServletRequest, "", xml, Status.OK.getStatusCode());
			return Boolean.toString(status);
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, "", ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			dataProvider.disconnect();
		}
	}

	@Path("/groupRightsInfos")
	@GET
	@Produces(MediaType.APPLICATION_XML)
	public String getGroupRightsInfos(@CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("portfolioId") String portfolioId)
	{
		checkCredential(httpServletRequest, user, token, group);

		try
		{
			String xmlGroups = dataProvider.getGroupRightsInfos(this.userId, portfolioId);
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
			dataProvider.disconnect();
		}
	}

	@Path("/users")
	@POST
	@Consumes(MediaType.APPLICATION_XML)
	@Produces(MediaType.APPLICATION_XML)
	public String  postUser(String xmluser, @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		checkCredential(httpServletRequest, user, token, group);

		try
		{
			String xmlUser = dataProvider.postUsers(xmluser, this.userId);
			logRestRequest(httpServletRequest, "", xmlUser, Status.OK.getStatusCode());

			return xmlUser;
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, xmluser, ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.BAD_REQUEST, ex.getMessage());
		}
		finally
		{
			dataProvider.disconnect();
		}
	}

	@Path("/label/{label}")
	@POST
	@Produces(MediaType.APPLICATION_XML)
	public Response  postCredentialGroupLabel( @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest, @PathParam("label") String label)
	{
		checkCredential(httpServletRequest, user, token, group);

		try
		{
			//if(dataProvider.isUserMemberOfGroup(this.userId,Integer.parseInt(groupId)))
			//{

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
			dataProvider.disconnect();
		}
	}

	@Path("group")
	@POST
	@Produces(MediaType.APPLICATION_XML)
	public String  postGroup(String xmlgroup, @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		checkCredential(httpServletRequest, user, token, group);

		try
		{
			String xmlGroup = (String) dataProvider.postGroup(xmlgroup, this.userId);
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
			dataProvider.disconnect();
		}
	}

	@Path("/groupsUsers")
	@POST
	@Produces(MediaType.APPLICATION_XML)
	public String  postGroupsUsers( @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("userId") int userId, @QueryParam("groupId") int groupId)
	{
		checkCredential(httpServletRequest, user, token, group);

		try
		{
			if(dataProvider.postGroupsUsers(userId,groupId))
			{
				return "<ok/>";
			}
			else throw new RestWebApplicationException(Status.FORBIDDEN, this.userId+" ne fait pas parti du groupe "+groupId);

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
			dataProvider.disconnect();
		}
	}

	@Path("RightGroup")
	@POST
	@Produces(MediaType.APPLICATION_XML)
	public Response  postRightGroup( @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("groupRightId") int groupRightId, @QueryParam("groupId") int groupId)
	{
		checkCredential(httpServletRequest, user, token, group);

		try
		{
			if(dataProvider.postRightGroup(groupRightId,groupId, this.userId))
			{
				System.out.print("ajout�");
			}
			else throw new RestWebApplicationException(Status.FORBIDDEN, this.userId+" ne fait pas parti du groupe "+groupId);

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
			dataProvider.disconnect();
		}
		return null;
	}

	@Path("/portfolios/instanciate/{portfolio-id}")
	@POST
	public String postInstanciatePortfolio(@CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @PathParam("portfolio-id") String portfolioId, @QueryParam("code") String newcode, @QueryParam("copyshared") String copy)
	{
		Boolean portfolioModelId;
		String value = "Instanciate: "+portfolioId;

		checkCredential(httpServletRequest, user, token, group);

		try
		{
			boolean copyshared = false;
			if( "y".equalsIgnoreCase(copy) )
				copyshared = true;

			//try { this.userId = userId; } catch(Exception ex) { this.userId = -1; };
			String returnValue = dataProvider.postInstanciateNode(new MimeType("text/xml"),portfolioId, newcode, this.userId,this.groupId, copyshared).toString();
			logRestRequest(httpServletRequest, value+" to: "+returnValue, returnValue, Status.OK.getStatusCode());

			return returnValue;
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, value+" --> Error", ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			dataProvider.disconnect();
		}
	}

	@Path("/portfolios")
	@POST
	@Consumes(MediaType.APPLICATION_XML)
	@Produces(MediaType.APPLICATION_XML)
	public String postPortfolio(String xmlPortfolio, @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("user") Integer userId, @QueryParam("group") Integer groupId, @QueryParam("model") String modelId)
	{
		Boolean portfolioModelId;

		checkCredential(httpServletRequest, user, token, group);

		try
		{
			//try { this.userId = userId; } catch(Exception ex) { this.userId = -1; };
			String returnValue = dataProvider.postPortfolio(new MimeType("text/xml"),new MimeType("text/xml"),xmlPortfolio,this.userId,this.groupId, modelId).toString();
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
			dataProvider.disconnect();
		}
	}

	@Path("/portfolios")
	@POST
	@Consumes("application/zip")
	public String postPortfolioZip(@CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("user") Integer userId, @QueryParam("group") Integer groupId, @QueryParam("model") String modelId)
	{
		Boolean portfolioModelId;

		checkCredential(httpServletRequest, user, token, group);

		try
		{
			//try { this.userId = userId; } catch(Exception ex) { this.userId = -1; };
			//String returnValue = dataProvider.postPortfolio(new MimeType("text/xml"),new MimeType("text/xml"),emplacement,this.userId,this.groupId, modelId).toString();
			String returnValue = dataProvider.postPortfolioZip(new MimeType("text/xml"),new MimeType("text/xml"),httpServletRequest,this.userId,this.groupId, modelId).toString();
			logRestRequest(httpServletRequest, returnValue, returnValue, Status.OK.getStatusCode());

			return returnValue;
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), modelId, Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			dataProvider.disconnect();
		}
	}

	@Path("/portfolios/portfolio/{portfolio-id}")
	@DELETE
	@Produces(MediaType.APPLICATION_XML)
	public String deletePortfolio(@CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @PathParam("portfolio-id") String portfolioUuid,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("user") Integer userId, @QueryParam("group") Integer groupId)
	{
		checkCredential(httpServletRequest, user, token, group);

		try
		{

			Integer nbPortfolioDeleted = Integer.parseInt(dataProvider.deletePortfolio(portfolioUuid,this.userId,this.groupId).toString());
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
			dataProvider.disconnect();
		}
	}


	@Path("/roleUser")
	@POST
	@Produces(MediaType.APPLICATION_XML)
	public String postRoleUser(@CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("grid") int grid,@QueryParam("user-id") Integer userid)
	{
		checkCredential(httpServletRequest, user, token, group);

		try
		{
			//TODO userId
			String returnValue = dataProvider.postRoleUser(this.userId, grid, userid).toString();
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
			dataProvider.disconnect();
		}
	}

	@Path("/roles/role/{role-id}")
	@PUT
	@Produces(MediaType.APPLICATION_XML)
	public String putRole(String xmlRole, @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @PathParam("role-id") int roleId)
	{
		checkCredential(httpServletRequest, user, token, group);

		try
		{
			//TODO userId
			String returnValue = dataProvider.putRole(xmlRole, this.userId, roleId).toString();
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
			dataProvider.disconnect();
		}
	}

	@Path("/roles/portfolio/{portfolio-id}")
	@GET
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	public String getRolePortfolio( @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @QueryParam("role") String role, @PathParam("portfolio-id") String portfolioId,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept)
	{
		checkCredential(httpServletRequest, user, token, group);

		try
		{
			String returnValue = dataProvider.getRolePortfolio(new MimeType("text/xml"),portfolioId,this.userId).toString();
			//			if(accept.equals(MediaType.APPLICATION_JSON))
			//				returnValue = XML.toJSONObject(returnValue).toString();
			logRestRequest(httpServletRequest, null, returnValue, Status.OK.getStatusCode());

			return returnValue;
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, null,ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());
			//			dataProvider.disconnect();
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			dataProvider.disconnect();
		}
	}

	@Path("/roles/role/{role-id}")
	@GET
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	public String getRole( @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @PathParam("role-id") Integer roleId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept)
	{
		checkCredential(httpServletRequest, user, token, group);

		try
		{

			String returnValue = dataProvider.getRole(new MimeType("text/xml"),roleId,this.userId).toString();
			if(returnValue.equals(""))
			{
				logRestRequest(httpServletRequest, null,null, Status.NOT_FOUND.getStatusCode());

				throw new RestWebApplicationException(Status.NOT_FOUND, "Role "+roleId+" not found");
			}

			//			if(accept.equals(MediaType.APPLICATION_JSON))
			//				returnValue = XML.toJSONObject(returnValue).toString();
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
			dataProvider.disconnect();
		}
	}


	@Path("/groupRights")
	@DELETE
	@Produces(MediaType.APPLICATION_XML)
	public String deleteGroupRights(@CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("groupId") Integer groupId, @QueryParam("groupRightId") Integer groupRightId)
	{
		checkCredential(httpServletRequest, user, token, group);

		try
		{
			int nbResourceDeleted = Integer.parseInt(dataProvider.deleteGroupRights(groupId, groupRightId, this.userId).toString());
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
			dataProvider.disconnect();
		}
	}

	@Path("/users")
	@DELETE
	@Produces(MediaType.APPLICATION_XML)
	public String deleteUsers(@CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("userId") Integer userId)
	{
		checkCredential(httpServletRequest, user, token, group);

		try
		{
			int nbResourceDeleted = Integer.parseInt(dataProvider.deleteUsers(userId, null).toString());
			if(nbResourceDeleted==0)
			{
				System.out.print("suprim�");
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
			dataProvider.disconnect();
		}
	}

	@Path("/users/user/{user-id}")
	@DELETE
	@Produces(MediaType.APPLICATION_XML)
	public String deleteUser(@CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @PathParam("user-id") Integer userid)
	{
		checkCredential(httpServletRequest, user, token, group);

		try
		{
			int nbResourceDeleted = Integer.parseInt(dataProvider.deleteUsers(this.userId,userid).toString());
			//			 if(nbResourceDeleted==0)
			//			 {
			//				 System.out.print("suprim�");
			//			 }
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
			dataProvider.disconnect();
		}
	}



	@Path("/users/Portfolio/{portfolio-id}/Role/{role}/users")
	@GET
	@Produces(MediaType.APPLICATION_XML)
	public String getUsersByRole(@CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @PathParam("portfolio-id") String portfolioUuid, @PathParam("role") String role, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		checkCredential(httpServletRequest, user, token, group);

		try
		{
			String xmlUsers = dataProvider.getUsersByRole(this.userId, portfolioUuid, role);
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
			dataProvider.disconnect();
		}
	}

	@Path("/users/Portfolio/{portfolio-id}/Role/{role}/groups")
	@GET
	@Produces(MediaType.APPLICATION_XML)
	public String getGroupsByRole(@CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @PathParam("portfolio-id") String portfolioUuid, @PathParam("role") String role, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		checkCredential(httpServletRequest, user, token, group);

		try
		{
			String xmlGroups = dataProvider.getGroupsByRole(this.userId, portfolioUuid, role);
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
			dataProvider.disconnect();
		}
	}

	@Path("/usersgroups")
	@GET
	public String getUsersByGroup(@CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		checkCredential(httpServletRequest, user, token, group);

		try
		{
			String xmlUsers = dataProvider.getUsersByGroup(this.userId);
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
			dataProvider.disconnect();
		}
	}


	/********************************************************/
	/** Partie utilisation des macro-commandes et gestion **/
	/********************************************************/
	@Path("/types")
	@POST
	@Consumes(MediaType.APPLICATION_XML+","+MediaType.TEXT_PLAIN)
	@Produces(MediaType.TEXT_PLAIN)
	public String postTypesManage(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest,
			@QueryParam("type") Integer type, @QueryParam("node") Integer nodeid,
			@QueryParam("parent") Integer parentid, @QueryParam("instance") Integer instance)
	{
		checkCredential(httpServletRequest, user, token, group);

		String returnValue="";
		try
		{
			// Ajoute un noeud, spécifie l'instance que ce nouveau noeud utilise
			if( type != null )
			{
				returnValue = dataProvider.postAddNodeType(this.userId, type, nodeid, parentid, instance, xmlNode);
				logRestRequest(httpServletRequest, xmlNode, returnValue, Status.OK.getStatusCode());

				if(returnValue == "faux")
				{
					throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
				}
			}
			// Crée un nouveau type, pas de noeud encore
			else if( type == null && nodeid == null && parentid == null && instance == null )
			{
				returnValue = dataProvider.postCreateType(this.userId, xmlNode);
				logRestRequest(httpServletRequest, xmlNode, returnValue, Status.OK.getStatusCode());

				if(returnValue == "faux")
				{
					throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
				}
			}
			// Erreur de requête
			else
			{
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
			dataProvider.disconnect();
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			dataProvider.disconnect();
		}
	}

	@Path("/types/{uuid}/{id}")
	@POST
	@Consumes(MediaType.APPLICATION_XML+","+MediaType.TEXT_PLAIN)
	@Produces(MediaType.TEXT_PLAIN)
	public String postTypes(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @PathParam("uuid") String uuid, @PathParam("id") Integer typeid,
			@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		checkCredential(httpServletRequest, user, token, group);

		String returnValue="";
		try
		{
			// On instancie un type sous le noeud spécifié. Crée les droits nécéssaire
			if( uuid != null && typeid != null )
			{
				returnValue = dataProvider.postUseType(this.userId, uuid, typeid).toString();
				logRestRequest(httpServletRequest, xmlNode, returnValue, Status.OK.getStatusCode());

				if(returnValue == "faux")
				{
					throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
				}
			}
			// Erreur de requête
			else
			{
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
			dataProvider.disconnect();
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			dataProvider.disconnect();
		}
	}

	@Path("/types")
	@GET
	@Produces(MediaType.APPLICATION_XML)
	public String getTypesManager(@CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest,
			@QueryParam("type") Integer type)
	{
		checkCredential(httpServletRequest, user, token, group);

		String returnValue="";
		try
		{
			// Retourne le contenu du type
			if( type != null )
			{
				returnValue = dataProvider.getTypeData(this.userId, type);
				logRestRequest(httpServletRequest, "getTypesManager", returnValue, Status.OK.getStatusCode());

				if(returnValue == "faux")
				{
					throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
				}

			}
			// Retourne le nom des types disponible
			else if( type == null )
			{
				returnValue = dataProvider.getAllTypes(this.userId);
				logRestRequest(httpServletRequest, "getTypesManager", returnValue, Status.OK.getStatusCode());

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
			logRestRequest(httpServletRequest, "getTypesManager",ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());
			dataProvider.disconnect();
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			dataProvider.disconnect();
		}
	}

	@Path("/types/{portfolio-uuid}")
	@GET
	@Produces(MediaType.TEXT_PLAIN)
	public String getTypes( @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @PathParam("portfolio-uuid") String uuid, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("type") Integer type)
	{
		checkCredential(httpServletRequest, user, token, group);

		String returnValue="";
		try
		{
			// Retourne le contenu du type
			if( uuid == null && type != null )
			{
				returnValue = dataProvider.getTypeData(this.userId, type);
				logRestRequest(httpServletRequest, "getTypes", returnValue, Status.OK.getStatusCode());

				if(returnValue == "faux")
				{
					throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
				}
			}
			// Retourne les types disponible dans un portfolio
			else if( uuid != null && type == null )
			{
				returnValue = dataProvider.getPortfolioTypes(this.userId, uuid);
				logRestRequest(httpServletRequest, "getTypes", returnValue, Status.OK.getStatusCode());

				if(returnValue == "faux")
				{
					throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
				}
			}
			// Retourne le nom des types disponible
			else if( uuid == null && type == null )
			{
				returnValue = dataProvider.getAllTypes(this.userId);
				logRestRequest(httpServletRequest, "getTypes", returnValue, Status.OK.getStatusCode());

				if(returnValue == "faux")
				{
					throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
				}
			}
			// Erreur de requête
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
			logRestRequest(httpServletRequest, "getTypes",ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());
			dataProvider.disconnect();
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			dataProvider.disconnect();
		}
	}

	@Path("/types")
	@PUT
	@Consumes(MediaType.APPLICATION_XML+","+MediaType.TEXT_PLAIN)
	@Produces(MediaType.TEXT_PLAIN)
	public String putTypes(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest,
			@QueryParam("type") Integer type, @QueryParam("node") Integer nodeid,
			@QueryParam("parent") Integer parentid, @QueryParam("instance") Integer instance)
	{
		checkCredential(httpServletRequest, user, token, group);

		String returnValue="";
		try
		{
			// Mise à jour d'un noeud et de son contenu
			if( type != null && nodeid != null )
			{
				returnValue = dataProvider.putTypeData(this.userId, type, nodeid, parentid, instance, xmlNode);
				logRestRequest(httpServletRequest, xmlNode, returnValue, Status.OK.getStatusCode());

				if(returnValue == "faux")
				{
					throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
				}
			}
			// Mise à jour du nom du type
			else if( type != null && nodeid == null )
			{
				returnValue = dataProvider.putTypeName(this.userId, type, xmlNode);
				logRestRequest(httpServletRequest, xmlNode, returnValue, Status.OK.getStatusCode());

				if(returnValue == "faux")
				{
					throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
				}
			}
			// Erreur de requête
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
			dataProvider.disconnect();
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			dataProvider.disconnect();
		}
	}

	@Path("/types")
	@DELETE
	@Consumes(MediaType.APPLICATION_XML+","+MediaType.TEXT_PLAIN)
	@Produces(MediaType.TEXT_PLAIN)
	public String deleteTypes(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("type") Integer type, @QueryParam("node") Integer nodeid)
	{
		checkCredential(httpServletRequest, user, token, group);

		String returnValue="";
		try
		{
			// On efface un noeud de la définition
			if( type != null && nodeid != null )
			{
				returnValue = dataProvider.deleteTypeNode(this.userId, type, nodeid);
				logRestRequest(httpServletRequest, xmlNode, returnValue, Status.OK.getStatusCode());

				if(returnValue == "faux")
				{
					throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
				}

			}
			// On efface la définition
			else if( type != null && nodeid == null )
			{
				returnValue = dataProvider.deleteType(this.userId, type);
				logRestRequest(httpServletRequest, xmlNode, returnValue, Status.OK.getStatusCode());

				if(returnValue == "faux")
				{
					throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
				}
			}
			// Erreur de requête
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
			dataProvider.disconnect();
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			dataProvider.disconnect();
		}
	}


	/********************************************************/
	/** Partie groupe de droits et utilisateurs            **/
	/********************************************************/

	/// Liste les rrg selon certain param�tres, portfolio, utilisateur, role
	@Path("/rolerightsgroups")
	@GET
	@Produces(MediaType.APPLICATION_XML)
	public String getRightsGroup( @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("portfolio") String portfolio, @QueryParam("user") Integer queryuser, @QueryParam("role") String role)
	{
		checkCredential(httpServletRequest, user, token, group);

		String returnValue="";
		try
		{
			// Retourne le contenu du type
			returnValue = dataProvider.getRRGList(this.userId, portfolio, queryuser, role);
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
			dataProvider.disconnect();
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			dataProvider.disconnect();
		}
	}

	@Path("/rolerightsgroups/all/users")
	@GET
	@Produces(MediaType.APPLICATION_XML)
	public String getPortfolioRightInfo( @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("portfolio") String portId )
	{
		checkCredential(httpServletRequest, user, token, group);

		String returnValue="";
		try
		{
			// Retourne le contenu du type
			if( portId != null )
			{
				returnValue = dataProvider.getPortfolioInfo(this.userId, portId);
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
			dataProvider.disconnect();
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			dataProvider.disconnect();
		}
	}

	@Path("/rolerightsgroups/rolerightsgroup/{rolerightsgroup-id}")
	@GET
	@Produces(MediaType.APPLICATION_XML)
	public String getRightInfo( @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @PathParam("rolerightsgroup-id") Integer rrgId )
	{
		checkCredential(httpServletRequest, user, token, group);

		String returnValue="";
		try
		{
			// Retourne le contenu du type
			if( rrgId != null )
			{
				returnValue = dataProvider.getRRGInfo(this.userId, rrgId);
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
			dataProvider.disconnect();
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			dataProvider.disconnect();
		}
	}

	@Path("/rolerightsgroups/rolerightsgroup/{rolerightsgroup-id}")
	@PUT
	@Produces(MediaType.APPLICATION_XML)
	public String putRightInfo(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @PathParam("rolerightsgroup-id") Integer rrgId )
	{
		checkCredential(httpServletRequest, user, token, group);

		String returnValue="";
		try
		{
			// Retourne le contenu du type
			if( rrgId != null )
			{
				returnValue = dataProvider.putRRGUpdate(this.userId, rrgId, xmlNode);
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
			dataProvider.disconnect();
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			dataProvider.disconnect();
		}
	}

	@Path("/rolerightsgroups")
	@POST
	@Produces(MediaType.APPLICATION_XML)
	public String postRightGroups(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest )
	{
		checkCredential(httpServletRequest, user, token, group);

		String returnValue="";
		try
		{
			returnValue = dataProvider.postRRGCreate(this.userId, xmlNode);
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
			dataProvider.disconnect();
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			dataProvider.disconnect();
		}
	}

	@Path("/rolerightsgroups/rolerightsgroup/{rolerightsgroup-id}/users")
	@POST
	@Produces(MediaType.APPLICATION_XML)
	public String postRightGroupUser(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @PathParam("rolerightsgroup-id") Integer rrgId )
	{
		checkCredential(httpServletRequest, user, token, group);

		String returnValue="";
		try
		{
			returnValue = dataProvider.postRRGUsers(this.userId, rrgId, xmlNode);
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
			dataProvider.disconnect();
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			dataProvider.disconnect();
		}
	}

	@Path("/rolerightsgroups/rolerightsgroup/{rolerightsgroup-id}/users/user/{user-id}")
	@POST
	@Produces(MediaType.APPLICATION_XML)
	public String postRightGroupUsers(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @PathParam("rolerightsgroup-id") Integer rrgId, @PathParam("user-id") Integer queryuser )
	{
		checkCredential(httpServletRequest, user, token, group);

		String returnValue="";
		try
		{
			returnValue = dataProvider.postRRGUser(this.userId, rrgId, queryuser);
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
			dataProvider.disconnect();
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			dataProvider.disconnect();
		}
	}

	@Path("/rolerightsgroups/rolerightsgroup/{rolerightsgroup-id}")
	@DELETE
	@Produces(MediaType.APPLICATION_XML)
	public String deleteRightGroup(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @PathParam("rolerightsgroup-id") Integer rrgId )
	{
		checkCredential(httpServletRequest, user, token, group);

		String returnValue="";
		try
		{
			returnValue = dataProvider.deleteRRG(this.userId, rrgId);
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
			dataProvider.disconnect();
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			dataProvider.disconnect();
		}
	}

	@Path("/rolerightsgroups/rolerightsgroup/{rolerightsgroup-id}/users/user/{user-id}")
	@DELETE
	@Produces(MediaType.APPLICATION_XML)
	public String deleteRightGroupUser(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @PathParam("rolerightsgroup-id") Integer rrgId, @PathParam("user-id") Integer queryuser )
	{
		checkCredential(httpServletRequest, user, token, group);

		String returnValue="";
		try
		{
			returnValue = dataProvider.deleteRRGUser(this.userId, rrgId, queryuser);
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
			dataProvider.disconnect();
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			dataProvider.disconnect();
		}
	}

	@Path("/rolerightsgroups/all/users")
	@DELETE
	@Produces(MediaType.APPLICATION_XML)
	public String deletePortfolioRightInfo(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("portfolio") String portId )
	{
		checkCredential(httpServletRequest, user, token, group);

		String returnValue="";
		try
		{
			// Retourne le contenu du type
			if( portId != null )
			{
				returnValue = dataProvider.deletePortfolioUser(this.userId, portId);
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
			dataProvider.disconnect();
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			dataProvider.disconnect();
		}
	}
}
