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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.activation.MimeType;
import javax.naming.InitialContext;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.sql.DataSource;
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
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

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
import org.xml.sax.SAXException;

import com.portfolio.data.provider.DataProvider;
import com.portfolio.data.utils.DomUtils;
import com.portfolio.data.utils.javaUtils;
import com.portfolio.eventbus.HandlerLogging;
import com.portfolio.eventbus.HandlerNotificationSakai;
import com.portfolio.eventbus.KEvent;
import com.portfolio.eventbus.KEventHandler;
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

	DataSource ds;
	DataProvider dataProvider;
	Credential credential = null;
	int logRestRequests = 0;
	String label = null;
	String casUrlValidation = null;
	String elggDefaultApiUrl = null;
	String elggDefaultSiteUrl = null;
	String elggApiKey = null;
	String elggDefaultUserPassword = null;

	ServletContext servContext;


	KEventbus eventbus = new KEventbus();

	public RestServicePortfolio( @Context ServletConfig sc , @Context ServletContext context)
	{
		try
		{
			// Initialize data provider and cas
			try
			{
				casUrlValidation =  context.getInitParameter("casUrlValidation") ;
			}
			catch(Exception ex)
			{
				casUrlValidation = null;
			};

			try
			{
				elggDefaultApiUrl =  context.getInitParameter("elggDefaultApiUrl") ;
			}
			catch(Exception ex)
			{
				elggDefaultApiUrl = null;
			};

			try
			{
				elggDefaultSiteUrl =  context.getInitParameter("elggDefaultSiteUrl") ;
			}
			catch(Exception ex)
			{
				elggDefaultSiteUrl = null;
			};



			try
			{
				elggApiKey =  context.getInitParameter("elggApiKey") ;
			}
			catch(Exception ex)
			{
				elggApiKey = null;
			};

			try
			{
				elggDefaultUserPassword =  context.getInitParameter("elggDefaultUserPassword") ;
			}
			catch(Exception ex)
			{
				elggDefaultUserPassword = null;
			};

			servContext = context;
			String dataProviderName  =  sc.getInitParameter("dataProviderClass");
			dataProvider = (DataProvider)Class.forName(dataProviderName).newInstance();

			// Try to initialize Datasource
			InitialContext cxt = new InitialContext();
			if ( cxt == null ) {
				throw new Exception("no context found!");
			}

			/// Init this here, might fail depending on server hosting
			ds = (DataSource) cxt.lookup( "java:/comp/env/jdbc/portfolio-backend" );
			if ( ds == null ) {
				throw new Exception("Data  jdbc/portfolio-backend source not found!");
			}
		}
		catch( Exception e )
		{
			logger.error("CAN'T INIT PROVIDER: "+e.toString());
			e.printStackTrace();
		}
	}

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

	public Connection getConnection() throws ParserConfigurationException, SAXException, IOException, SQLException, ClassNotFoundException
	{
		// Open META-INF/context.xml
		DocumentBuilderFactory documentBuilderFactory =DocumentBuilderFactory.newInstance();
		DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
		Document doc = documentBuilder.parse(servContext.getRealPath("/")+"/META-INF/context.xml");
		NodeList res = doc.getElementsByTagName("Resource");
		Node dbres = res.item(0);

		Properties info = new Properties();
		NamedNodeMap attr = dbres.getAttributes();
		String url = "";
		for( int i=0; i<attr.getLength(); ++i )
		{
			Node att = attr.item(i);
			String name = att.getNodeName();
			String val = att.getNodeValue();
			if( "url".equals(name) )
				url = val;
			else if( "username".equals(name) )	// username (context.xml) -> user (properties)
				info.put("user", val);
			else if( "driverClassName".equals(name) )
				Class.forName(val);
			else
				info.put(name, val);
		}

		return DriverManager.getConnection(url, info);
	}

	public void initService( HttpServletRequest request )
	{
		try
		{
			Connection con = null;
			if( ds == null )	// Case where we can't deploy context.xml
			{
				con = getConnection();
				dataProvider.setConnection(con);
			}
			else
			{
				con = ds.getConnection();
				dataProvider.setConnection(con);
			}
//			dataProvider.setDataSource(ds);

			credential = new Credential(con);

			/// Configure eventbus
			//	    KEventHandler handlerDB = new HandlerDatabase(request, dataProvider);
			KEventHandler handlerLog = new HandlerLogging(request, dataProvider);
			KEventHandler handlerSakaiNotification = new HandlerNotificationSakai(request, dataProvider);

			//        eventbus.addChain( handlerDB );
			eventbus.addChain( handlerLog );
			eventbus.addChain( handlerSakaiNotification );
		}
		catch ( Exception e )
		{
			logger.error("CAN'T CREATE CONNECTION: "+e.getMessage());
			e.printStackTrace();
		}
	}

	public UserInfo checkCredential(HttpServletRequest request, String login, String token, String group )
	{
		HttpSession session = request.getSession(true);
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

	/// Fetch current user information
	@Path("/credential")
	@GET
	@Produces(MediaType.APPLICATION_XML)
	public Response getCredential( @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		if( ui.userId == 0 )	// Non valid userid
		{
			return Response.status(401).build();
		}

		try
		{
			String xmluser = dataProvider.getInfUser(ui.userId, ui.userId);
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
	public String getGroups(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			String xmlGroups = (String) dataProvider.getUserGroups(ui.userId);
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
	public String getUsers(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			String xmlGroups = dataProvider.getListUsers(ui.userId);
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
	public String getUser(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("user-id") int userid, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			String xmluser = dataProvider.getInfUser(ui.userId, userid);
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
	public String putUser( String xmlInfUser, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("user-id") int userid, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			String queryuser = dataProvider.putInfUser(ui.userId, userid, xmlInfUser);
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
	public String getUserId(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("username") String username, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			String userid = dataProvider.getUserID(ui.userId, username);
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
	public String getGroupsUser(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("user-id") int useridCible, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			String xmlgroupsUser = dataProvider.getGroupsUser(ui.userId, useridCible);
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

	@Path("/groupRights")
	@GET
	@Produces(MediaType.APPLICATION_XML)
	public String getGroupRights(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest )
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			String xmlGroups = (String) dataProvider.getGroupRights(ui.userId, groupId);
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

	@Path("/groupRightsInfos")
	@GET
	@Produces(MediaType.APPLICATION_XML)
	public String getGroupRightsInfos(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("portfolioId") String portfolioId)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			String xmlGroups = dataProvider.getGroupRightsInfos(ui.userId, portfolioId);
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

	@Path("/portfolios/portfolio/{portfolio-id}")
	@GET
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, "application/zip", MediaType.APPLICATION_OCTET_STREAM})
	public Object getPortfolio(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("portfolio-id") String portfolioUuid,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept, @QueryParam("user") Integer userId, @QueryParam("group") Integer group, @QueryParam("resources") String resource, @QueryParam("files") String files, @QueryParam("export") String export, @QueryParam("lang") String lang)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		Response response = null;
		try
		{
			String portfolio = dataProvider.getPortfolio(new MimeType("text/xml"),portfolioUuid,ui.userId, 0, this.label, resource, "", ui.subId).toString();

			if( "faux".equals(portfolio) )
			{
				response = Response.status(403).build();
			}

			if( response == null )
			{
				Date time = new Date();
				Document doc = DomUtils.xmlString2Document(portfolio, new StringBuffer());
				NodeList codes = doc.getDocumentElement().getElementsByTagName("code");
				// Le premier c'est celui du root
				Node codenode = codes.item(0);
				String code = "";
				if( codenode != null )
					code = codenode.getTextContent();

				if( export != null )
				{
					response = Response
							.ok(portfolio)
							.header("content-disposition","attachment; filename = \""+code+"-"+time+".xml\"")
							.build();
				}
				else if(resource != null && files != null)
				{
					//// Cas du renvoi d'un ZIP

					/// Temp file in temp directory
					File tempDir = new File(System.getProperty("java.io.tmpdir", null));
					File tempZip = File.createTempFile(portfolioUuid, ".zip", tempDir);

					FileOutputStream fos = new FileOutputStream(tempZip);
					ZipOutputStream zos = new ZipOutputStream(fos);
//					BufferedOutputStream bos = new BufferedOutputStream(zos);

					/// zos.setComment("Some comment");

					/// Write xml file to zip
					ZipEntry ze = new ZipEntry(portfolioUuid+".xml");
					zos.putNextEntry(ze);

					byte[] bytes = portfolio.getBytes("UTF-8");
					zos.write(bytes);

					zos.closeEntry();

					/// Find all fileid/filename
					XPath xPath = XPathFactory.newInstance().newXPath();
					String filterRes = "//asmResource/fileid";
					NodeList nodelist = (NodeList) xPath.compile(filterRes).evaluate(doc, XPathConstants.NODESET);

					/// Direct link to data
					// String urlTarget = "http://"+ server + "/user/" + user +"/file/" + uuid +"/"+ lang+ "/ptype/fs";

					/*
					String langatt = "";
					if( lang != null )
						langatt = "?lang="+lang;
					else
						langatt = "?lang=fr";
					//*/

					/// Fetch all files
					for( int i=0; i<nodelist.getLength(); ++i )
					{
						Node res = nodelist.item(i);
						Node p = res.getParentNode();	// resource -> container
						Node gp = p.getParentNode();	// container -> context
						Node uuidNode = gp.getAttributes().getNamedItem("id");
						String uuid = uuidNode.getTextContent();

						String filterName = "./filename[@lang and text()]";
						NodeList textList = (NodeList) xPath.compile(filterName).evaluate(p, XPathConstants.NODESET);
						String filename = "";
						if( textList.getLength() != 0 )
						{
							Element fileNode = (Element) textList.item(0);
							filename = fileNode.getTextContent();
							lang = fileNode.getAttribute("lang");
							if( "".equals(lang) ) lang = "fr";
						}

						String servlet = httpServletRequest.getRequestURI();
						servlet = servlet.substring(0, servlet.indexOf("/", 7));
						String server = httpServletRequest.getServerName();
						int port = httpServletRequest.getServerPort();
//						"http://"+ server + /resources/resource/file/ uuid ? lang= size=
						// String urlTarget = "http://"+ server + "/user/" + user +"/file/" + uuid +"/"+ lang+ "/ptype/fs";
						String url = "http://"+server+":"+port+servlet+"/resources/resource/file/"+uuid+"?lang="+lang;
						HttpGet get = new HttpGet(url);

						// Transfer sessionid so that local request still get security checked
						HttpSession session = httpServletRequest.getSession(true);
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
						int extindex = filenameext.lastIndexOf(".");
						filenameext = uuid +"_"+ lang + filenameext.substring(extindex);

						// Save it to zip file
//						int length = (int) entity.getContentLength();
						InputStream content = entity.getContent();

//						BufferedInputStream bis = new BufferedInputStream(entity.getContent());

						ze = new ZipEntry(filenameext);
						try
						{
							int totalread = 0;
							zos.putNextEntry(ze);
							int inByte;
							byte[] buf = new byte[4096];
//							zos.write(bytes,0,inByte);
							while( (inByte = content.read(buf)) != -1 )
							{
								totalread += inByte;
								zos.write(buf, 0, inByte);
							}
							System.out.println("FILE: "+filenameext+" -> "+totalread);
							content.close();
//							bis.close();
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

					/// Return zip file
					RandomAccessFile f = new RandomAccessFile(tempZip.getAbsoluteFile(), "r");
					byte[] b = new byte[(int)f.length()];
					f.read(b);
					f.close();

					response = Response
							.ok(b, MediaType.APPLICATION_OCTET_STREAM)
							.header("content-disposition","attachment; filename = \""+code+"-"+time+".zip")
							.build();

					// Temp file cleanup
					tempZip.delete();
				}
				else
				{
					//try { this.userId = userId; } catch(Exception ex) { this.userId = -1; };
					//	        	String returnValue = dataProvider.getPortfolio(new MimeType("text/xml"),portfolioUuid,this.userId, this.groupId, this.label, resource, files).toString();
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
			if( dataProvider != null )
				dataProvider.disconnect();
		}

		return response;
	}

	/*@Path("/portfolios/portfolio/{portfolio-id}")
	@GET
	@Produces("application/zip")
	public String getPortfolioZip( @PathParam("portfolio-id") String portfolioUuid,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept, @QueryParam("user") Integer userId, @QueryParam("group") Integer group, @PathParam("resource") Boolean resource, @PathParam("files") Boolean files)
	{
		initialize(httpServletRequest,true);
		//httpServletRequest.setHeader("Content-Disposition", "attachment; filename=\""+code+"_"+now+".xml\";");
        try
		{
        	//try { this.userId = userId; } catch(Exception ex) { this.userId = -1; };
        	String returnValue = dataProvider.getPortfolioZip(new MimeType("text/xml"),portfolioUuid,this.userId, this.groupId, this.label, resource, files).toString();
        	if(returnValue.equals("faux")){

				throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
			}
        	if(accept.equals(MediaType.APPLICATION_JSON))
				returnValue = XML.toJSONObject(returnValue).toString();

        	logRestRequest(httpServletRequest, null, returnValue, Status.OK.getStatusCode());
//        	dataProvider.disconnect();
        	return returnValue;
		}
        catch(RestWebApplicationException ex)
        {
        	throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
        }
        catch(SQLException ex)
        {
        	logRestRequest(httpServletRequest, null, "Portfolio "+portfolioUuid+" not found",Status.NOT_FOUND.getStatusCode());
//        	dataProvider.disconnect();
        	throw new RestWebApplicationException(Status.NOT_FOUND, "Portfolio "+portfolioUuid+" not found");
        }
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, null,ex.getMessage()+"\n\n"+ex.getStackTrace(), Status.INTERNAL_SERVER_ERROR.getStatusCode());
//			dataProvider.disconnect();
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
        finally
        {
          dataProvider.disconnect();
        }
	}*/

	@Path("/portfolios/portfolio/code/{code}")
	@GET
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})

	public String getPortfolioByCode(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("code") String code,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept, @QueryParam("user") Integer userId, @QueryParam("group") Integer group, @QueryParam("resources") String resources )
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			String returnValue = dataProvider.getPortfolioByCode(new MimeType("text/xml"),code,ui.userId, groupId, resources, ui.subId).toString();
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
			dataProvider.disconnect();
		}
	}

	/// Liste des portfolios de l'utilisateur courant
	@Path("/portfolios")
	@GET
	@Consumes(MediaType.APPLICATION_XML)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	public String getPortfolios(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept, @QueryParam("active") String active, @QueryParam("user") Integer userId, @QueryParam("code") String code, @QueryParam("portfolio") String portfolioUuid )
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			if(portfolioUuid!=null)
			{
				String returnValue = dataProvider.getPortfolio(new MimeType("text/xml"),portfolioUuid,ui.userId, groupId, this.label, null, null, ui.subId).toString();
				if(accept.equals(MediaType.APPLICATION_JSON))
					returnValue = XML.toJSONObject(returnValue).toString();

				logRestRequest(httpServletRequest, null, returnValue, Status.OK.getStatusCode());

				return returnValue;

			}
			else
			{
				String portfolioCode = null;
				String returnValue = "";
				Boolean portfolioActive;
				try { if(active.equals("false") ||  active.equals("0")) portfolioActive = false; else portfolioActive = true; }
				catch(Exception ex) { portfolioActive = true; };

				try { portfolioCode = code; } catch(Exception ex) { };
				if(portfolioCode!=null)
				{
					returnValue = dataProvider.getPortfolioByCode(new MimeType("text/xml"),portfolioCode, ui.userId, groupId, null, ui.subId).toString();
				}
				else
				{
					if( userId != null && credential.isAdmin(ui.userId) )
					{
						returnValue = dataProvider.getPortfolios(new MimeType("text/xml"), userId, groupId, portfolioActive, ui.subId).toString();
					}
					else
					{
						returnValue = dataProvider.getPortfolios(new MimeType("text/xml"), ui.userId, groupId, portfolioActive, ui.subId).toString();
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
			dataProvider.disconnect();
		}
	}

	/// R�-�crit le portfolios
	@Path("/portfolios/portfolio/{portfolio-id}")
	@PUT
	@Consumes(MediaType.APPLICATION_XML)
	@Produces(MediaType.APPLICATION_XML)
	public String putPortfolio( String xmlPortfolio, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("portfolio-id") String portfolioUuid,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("active") String active, @QueryParam("user") Integer userId )
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			Boolean portfolioActive;
			try { if(active.equals("false") ||  active.equals("0")) portfolioActive = false; else portfolioActive = true; }
			catch(Exception ex) { portfolioActive = null; };

			dataProvider.putPortfolio(new MimeType("text/xml"),new MimeType("text/xml"),xmlPortfolio,portfolioUuid, ui.userId,portfolioActive, groupId,null);
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
			dataProvider.disconnect();
		}
	}

	@Path("/portfolios")
	@PUT
	@Consumes(MediaType.APPLICATION_XML)
	@Produces(MediaType.APPLICATION_XML)
	public String putPortfolioConfiguration(String xmlPortfolio, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("portfolio") String portfolioUuid, @QueryParam("active") Boolean portfolioActive)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			String returnValue = "";
			if(portfolioUuid!=null && portfolioActive!=null)
			{
				dataProvider.putPortfolioConfiguration(portfolioUuid,portfolioActive, ui.userId);
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
			dataProvider.disconnect();
		}
	}

	@Path("/users")
	@POST
	@Consumes(MediaType.APPLICATION_XML)
	@Produces(MediaType.APPLICATION_XML)
	public String  postUser(String xmluser, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			String xmlUser = dataProvider.postUsers(xmluser, ui.userId);
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
			dataProvider.disconnect();
		}
	}

	/// S�lection du r�le de l'utilisateur courant
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
			dataProvider.disconnect();
		}
	}

	@Path("group")
	@POST
	@Produces(MediaType.APPLICATION_XML)
	public String  postGroup(String xmlgroup, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		/**
		 * <group grid="" owner="" label=""></group>
		 */
		try
		{
			String xmlGroup = (String) dataProvider.postGroup(xmlgroup, ui.userId);
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
	public String  postGroupsUsers( @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("userId") int userId )
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			if(dataProvider.postGroupsUsers(ui.userId, userId,groupId))
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
			dataProvider.disconnect();
		}
	}

	@Path("RightGroup")
	@POST
	@Produces(MediaType.APPLICATION_XML)
	public Response  postRightGroup( @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("groupRightId") int groupRightId )
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			if(dataProvider.postRightGroup(groupRightId,groupId, ui.userId))
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
			dataProvider.disconnect();
		}
		return null;
	}

	/// D'un portfolio, cr�e une instance ou l'on prend compte des droits sp�cifi� dans les attributs wad
	@Path("/portfolios/instanciate/{portfolio-id}")
	@POST
	public String postInstanciatePortfolio(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @PathParam("portfolio-id") String portfolioId , @QueryParam("sourcecode") String srccode, @QueryParam("targetcode") String tgtcode, @QueryParam("copyshared") String copy, @QueryParam("groupname") String groupname)
	{
		String value = "Instanciate: "+portfolioId;

		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			boolean copyshared = false;
			if( "y".equalsIgnoreCase(copy) )
				copyshared = true;

			String returnValue = dataProvider.postInstanciatePortfolio(new MimeType("text/xml"),portfolioId, srccode, tgtcode, ui.userId, groupId, copyshared, groupname).toString();
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

	@Path("/portfolios/copy/{portfolio-id}")
	@POST
	public String postCopyPortfolio(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @PathParam("portfolio-id") String portfolioId , @QueryParam("sourcecode") String srccode, @QueryParam("targetcode") String tgtcode )
	{
		String value = "Instanciate: "+portfolioId;

		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			String returnValue = dataProvider.postCopyPortfolio(new MimeType("text/xml"),portfolioId, srccode, tgtcode, ui.userId ).toString();
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
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Produces(MediaType.APPLICATION_XML)
	public String postFormPortfolio(@FormDataParam("uploadfile") String xmlPortfolio, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("user") Integer userId, @QueryParam("model") String modelId)
	{
		return postPortfolio(xmlPortfolio, user, token, groupId, sc, httpServletRequest, userId, modelId);
	}

	///	Cr�e un portfolio avec les donn�es xml envoy�es
	@Path("/portfolios")
	@POST
	@Consumes(MediaType.APPLICATION_XML)
	@Produces(MediaType.APPLICATION_XML)
	public String postPortfolio(String xmlPortfolio, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("user") Integer userId, @QueryParam("model") String modelId)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			String returnValue = dataProvider.postPortfolio(new MimeType("text/xml"),new MimeType("text/xml"),xmlPortfolio, ui.userId, groupId, modelId, ui.subId).toString();
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
			dataProvider.disconnect();
		}
	}

	///	Cr�e un portfolio avec les donn�es zip envoy�es
	@Path("/portfolios/zip")
	@POST
	@Consumes("application/zip")	// Envoie donn�e brut
	public String postPortfolioZip(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("user") Integer userId, @QueryParam("model") String modelId)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			String returnValue = dataProvider.postPortfolioZip(new MimeType("text/xml"),new MimeType("text/xml"),httpServletRequest, ui.userId, groupId, modelId, ui.subId).toString();
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

	/// Efface un portfolio
	@Path("/portfolios/portfolio/{portfolio-id}")
	@DELETE
	@Produces(MediaType.APPLICATION_XML)
	public String deletePortfolio(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("portfolio-id") String portfolioUuid,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("user") Integer userId )
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{

			Integer nbPortfolioDeleted = Integer.parseInt(dataProvider.deletePortfolio(portfolioUuid, ui.userId, groupId).toString());
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

	/// R�cup�re les donn�es d'un noeud
	@Path("/nodes/node/{node-id}")
	@GET
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Consumes(MediaType.APPLICATION_XML)
	public String getNode( @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("node-id") String nodeUuid,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept, @QueryParam("user") Integer userId )
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			String returnValue = dataProvider.getNode(new MimeType("text/xml"),nodeUuid,false, ui.userId, groupId, this.label).toString();
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
			dataProvider.disconnect();
		}
	}

	// R�cup�re un noeud et ses enfants
	@Path("/nodes/node/{node-id}/children")
	@GET
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Consumes(MediaType.APPLICATION_XML)
	public String getNodeWithChildren( @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("node-id") String nodeUuid,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept, @QueryParam("user") Integer userId )
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			String returnValue = dataProvider.getNode(new MimeType("text/xml"),nodeUuid,true, ui.userId, groupId, this.label).toString();
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
			dataProvider.disconnect();
		}
	}

	/// R�cup�re les metadonn�es d'un noeud
	@Path("/nodes/node/{nodeid}/metadatawad")
	@GET
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Consumes(MediaType.APPLICATION_XML)
	public String getNodeMetadataWad( @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("nodeid") String nodeUuid,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept, @QueryParam("user") Integer userId )
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			String returnValue = dataProvider.getNodeMetadataWad(new MimeType("text/xml"),nodeUuid,true, ui.userId, groupId, this.label).toString();
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
			dataProvider.disconnect();
		}
	}

	@Path("/nodes/node/{node-id}/rights")
	@GET
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Consumes(MediaType.APPLICATION_XML)
	public String getNodeRights( @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("node-id") String nodeUuid,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept, @QueryParam("user") Integer userId )
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			String returnValue = dataProvider.getNodeRights(nodeUuid, ui.userId, groupId);
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
			dataProvider.disconnect();
		}
	}

	@Path("/nodes/node/{node-id}/rights")
	@POST
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Consumes(MediaType.APPLICATION_XML)
	public String postNodeRights( String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("node-id") String nodeUuid,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept, @QueryParam("user") Integer userId )
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			DocumentBuilderFactory documentBuilderFactory =DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
			Document doc = documentBuilder.parse(new ByteArrayInputStream(xmlNode.getBytes("UTF-8")));

			XPath xPath = XPathFactory.newInstance().newXPath();
			String xpathRole = "//role";
			XPathExpression findRole = xPath.compile(xpathRole);
			NodeList roles = (NodeList) findRole.evaluate(doc, XPathConstants.NODESET);

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
					dataProvider.postRights(ui.userId, nodeUuid, rolename, noderight);
				}
				else if( "action".equals(right.getNodeName()) )	// Using an action on node
				{
					// reset right
					dataProvider.postMacroOnNode(ui.userId, nodeUuid, "reset");
				}
			}

//			returnValue = dataProvider.postRRGCreate(ui.userId, xmlNode);
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
			dataProvider.disconnect();
		}

		return "";
	}

	@Path("/nodes/firstbysemantictag/{portfolio-uuid}/{semantictag}")
	@GET
	@Produces({MediaType.APPLICATION_XML})
	@Consumes(MediaType.APPLICATION_XML)
	public String getNodeBySemanticTag( @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("portfolio-uuid") String portfolioUuid,@PathParam("semantictag") String semantictag,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			String returnValue = dataProvider.getNodeBySemanticTag(new MimeType("text/xml"),portfolioUuid,semantictag, ui.userId, groupId).toString();
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
			dataProvider.disconnect();
		}
	}

	@Path("/nodes/bysemantictag/{portfolio-uuid}/{semantictag}")
	@GET
	@Produces({MediaType.APPLICATION_XML})
	@Consumes(MediaType.APPLICATION_XML)
	public String getNodesBySemanticTag( @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("portfolio-uuid") String portfolioUuid,@PathParam("semantictag") String semantictag,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			String returnValue = dataProvider.getNodesBySemanticTag(new MimeType("text/xml"), ui.userId, groupId,portfolioUuid,semantictag).toString();
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
			dataProvider.disconnect();
		}
	}

	// R��crit les donn�es d'un noeud
	@Path("/nodes/node/{node-id}")
	@PUT
	@Produces(MediaType.APPLICATION_XML)
	public String putNode(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("node-id") String nodeUuid,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("user") Integer userId )
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			String returnValue = dataProvider.putNode(new MimeType("text/xml"),nodeUuid,xmlNode, ui.userId, groupId).toString();
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
			dataProvider.disconnect();
		}
	}

	// R��crit les metadonn�es d'un noeud
	@Path("/nodes/node/{nodeid}/metadata")
	@PUT
	@Produces(MediaType.APPLICATION_XML)
	public String putNodeMetadata(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("nodeid") String nodeUuid,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			String returnValue = dataProvider.putNodeMetadata(new MimeType("text/xml"),nodeUuid,xmlNode, ui.userId, groupId).toString();
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
			dataProvider.disconnect();
		}
	}

	// R��crit les metadonn�es wad d'un noeud
	@Path("/nodes/node/{nodeid}/metadatawad")
	@PUT
	@Produces(MediaType.APPLICATION_XML)
	public String putNodeMetadataWad(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("nodeid") String nodeUuid,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			String returnValue = dataProvider.putNodeMetadataWad(new MimeType("text/xml"),nodeUuid,xmlNode, ui.userId, groupId).toString();
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
			dataProvider.disconnect();
		}
	}

	// R��crit les metadonn�es epm d'un noeud
	@Path("/nodes/node/{nodeid}/metadataepm")
	@PUT
	@Produces(MediaType.APPLICATION_XML)
	public String putNodeMetadataEpm(String xmlNode, @PathParam("nodeid") String nodeUuid, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		UserInfo ui = checkCredential(httpServletRequest, null, null, null);

		try
		{
			String returnValue = dataProvider.putNodeMetadataEpm(new MimeType("text/xml"),nodeUuid,xmlNode, ui.userId, groupId).toString();
			if(returnValue.equals("faux")){

				throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
			}
			if( "erreur".equals(returnValue) )
				throw new RestWebApplicationException(Status.NOT_MODIFIED, "Erreur");

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
			dataProvider.disconnect();
		}
	}

	// R��crit le nodecontext d'un noeud
	@Path("/nodes/node/{nodeid}/nodecontext")
	@PUT
	@Produces(MediaType.APPLICATION_XML)
	public String putNodeNodeContext(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("nodeid") String nodeUuid,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			String returnValue = dataProvider.putNodeNodeContext(new MimeType("text/xml"),nodeUuid,xmlNode, ui.userId, groupId).toString();
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
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, xmlNode,ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			dataProvider.disconnect();
		}
	}

	// R��crit le noderesource d'un noeud
	@Path("/nodes/node/{nodeid}/noderesource")
	@PUT
	@Produces(MediaType.APPLICATION_XML)
	public String putNodeNodeResource(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("nodeid") String nodeUuid,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			/// Branchement pour l'interpr�tation du contenu, besoin de v�rifier les limitations ?
			//xmlNode = xmlNode.getBytes("UTF-8").toString();
			/// putNode(MimeType inMimeType, String nodeUuid, String in,int userId, int groupId)
			//          String returnValue = dataProvider.putNode(new MimeType("text/xml"),nodeUuid,xmlNode,this.userId,this.groupId).toString();
			String returnValue = dataProvider.putNodeNodeResource(new MimeType("text/xml"),nodeUuid,xmlNode, ui.userId, groupId).toString();
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
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, xmlNode,ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			dataProvider.disconnect();
		}
	}

	// Instancie un noeud avec �valuation des droits selon les param�tres de filtrage � la source
	@Path("/nodes/node/import/{dest-id}")
	@POST
	public String postImportNode(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("dest-id") String parentId,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("srcetag") String semtag, @QueryParam("srcecode") String code)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			String returnValue = dataProvider.postImportNode(new MimeType("text/xml"), parentId, semtag, code, ui.userId, groupId).toString();
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
			dataProvider.disconnect();
		}
	}

	// Copie un noeud sans �valuation des droits selon les param�tres de filtrage � la source
	@Path("/nodes/node/copy/{dest-id}")
	@POST
	public String postCopyNode(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("dest-id") String parentId,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("srcetag") String semtag, @QueryParam("srcecode") String code)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			String returnValue = dataProvider.postCopyNode(new MimeType("text/xml"), parentId, semtag, code, ui.userId, groupId).toString();
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
			dataProvider.disconnect();
		}
	}

	@Path("/nodes")
	@GET
	@Produces({MediaType.APPLICATION_XML})
	@Consumes(MediaType.APPLICATION_XML)
	public String getNodes( @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("dest-id") String parentId,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("portfoliocode") String portfoliocode, @QueryParam("semtag") String semtag, @QueryParam("semtag_parent") String semtag_parent, @QueryParam("code_parent") String code_parent)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			String returnValue = dataProvider.getNodes(new MimeType("text/xml"), portfoliocode, semtag, ui.userId, groupId, semtag_parent, code_parent).toString();
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
			//            dataProvider.disconnect();
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			dataProvider.disconnect();
		}
	}

	@Path("/nodes/node/{parent-id}")
	@POST
	@Consumes(MediaType.APPLICATION_XML)
	@Produces(MediaType.APPLICATION_XML)
	public Response postNode(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") Integer group, @PathParam("parent-id") String parentId,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("user") Integer userId, @QueryParam("group") int groupId)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		KEvent event = new KEvent();
		event.requestType = KEvent.RequestType.POST;
		event.eventType = KEvent.EventType.NODE;
		event.uuid = parentId;
		event.inputData = xmlNode;

		try
		{
			String returnValue = dataProvider.postNode(new MimeType("text/xml"),parentId,xmlNode, ui.userId, groupId).toString();
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
			dataProvider.disconnect();
		}
	}

	// Remonte un noeud dans l'ordre des enfants
	@Path("/nodes/node/{node-id}/moveup")
	@POST
	@Consumes(MediaType.APPLICATION_XML)
	@Produces(MediaType.APPLICATION_XML)
	public Response postMoveNodeUp(String xmlNode, @PathParam("node-id") String nodeId,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest )
	{
		UserInfo ui = checkCredential(httpServletRequest, null, null, null);

		/*
      KEvent event = new KEvent();
      event.requestType = KEvent.RequestType.POST;
      event.eventType = KEvent.EventType.NODE;
      event.uuid = parentId;
      event.inputData = xmlNode;
      //*/
		Response response;

		try
		{
			if( nodeId == null )
			{
				response = Response.status(400).entity("Missing uuid").build();
			}
			else
			{
				int returnValue = dataProvider.postMoveNodeUp(ui.userId, nodeId);
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
			dataProvider.disconnect();
		}

		return response;
	}

	// Change le parent d'un noeud, le met en tant que dernier enfant
	@Path("/nodes/node/{node-id}/parentof/{parent-id}")
	@POST
	@Consumes(MediaType.APPLICATION_XML)
	@Produces(MediaType.APPLICATION_XML)
	public Response postChangeNodeParent(String xmlNode, @PathParam("node-id") String nodeId, @PathParam("parent-id") String parentId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest )
	{
		UserInfo ui = checkCredential(httpServletRequest, null, null, null);

		/*
      KEvent event = new KEvent();
      event.requestType = KEvent.RequestType.POST;
      event.eventType = KEvent.EventType.NODE;
      event.uuid = parentId;
      event.inputData = xmlNode;
      //*/

		try
		{
			boolean returnValue = dataProvider.postChangeNodeParent(ui.userId, nodeId, parentId);
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
			dataProvider.disconnect();
		}
	}

	// Effectue une macro commande sur un noeud, concerne les changements de droits
	@Path("/nodes/node/{node-id}/action/{action-name}")
	@POST
	@Consumes(MediaType.APPLICATION_XML)
	@Produces(MediaType.APPLICATION_XML)
	public String postActionNode(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("node-id") String nodeId, @PathParam("action-name") String macro, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("user") Integer userId )
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			String returnValue = dataProvider.postMacroOnNode(ui.userId, nodeId, macro);
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
			dataProvider.disconnect();
		}
	}

	// Effacement d'un noeud
	@Path("/nodes/node/{node-uuid}")
	@DELETE
	@Produces(MediaType.APPLICATION_XML)
	public String deleteNode(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("node-uuid") String nodeUuid,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("user") Integer userId )
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			int nbDeletedNodes = Integer.parseInt(dataProvider.deleteNode(nodeUuid, ui.userId, groupId).toString());
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
			dataProvider.disconnect();
		}
	}

	// Retrait du partage d'un portfolio
	@Deprecated
	@Path("/share/{portfolioid}")
	@DELETE
	@Produces({MediaType.APPLICATION_XML})
	@Consumes(MediaType.APPLICATION_XML)
	public String deleteSharePortfolio(String xmlNode, @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest, @PathParam("portfolioid") String portfolioid)
	{
		UserInfo ui = checkCredential(httpServletRequest, null, null, null);

		int returnValue=-1;
		try
		{
			returnValue = dataProvider.deleteCompleteShare(portfolioid, ui.userId);
			logRestRequest(httpServletRequest, xmlNode, Integer.toString(returnValue), Status.OK.getStatusCode());

			switch( returnValue )
			{
				case -1:
					throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");

				case -2:
					throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'�tes pas le propri�taire");

				default:
					break;
			}
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
			dataProvider.disconnect();
		}

		return Integer.toString(returnValue);
	}

	// Retire une personne du partage
	@Deprecated
	@Path("/share/{portfolioid}/{userid}")
	@DELETE
	@Produces({MediaType.APPLICATION_XML})
	@Consumes(MediaType.APPLICATION_XML)
	public String delteSharePortfolioUser(String xmlNode, @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest, @PathParam("portfolioid") String portfolioid, @PathParam("userid") int user)
	{
		UserInfo ui = checkCredential(httpServletRequest, null, null, null);

		int returnValue=-1;
		try
		{
			returnValue = dataProvider.deleteCompleteShareUser(portfolioid, ui.userId, user);
			logRestRequest(httpServletRequest, xmlNode, Integer.toString(returnValue), Status.OK.getStatusCode());

			switch( returnValue )
			{
				case -1:
					throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");

				case -2:
					throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'�tes pas le propri�taire");

				default:
					break;
			}
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
			dataProvider.disconnect();
		}

		return Integer.toString(returnValue);
	}

	// Met une personne dans le partage de portfolio (on rend tout disponible en lecture ou lecture/�criture)
	@Deprecated
	@Path("/share/{portfolioid}/{userid}")
	@POST
	@Produces({MediaType.APPLICATION_XML})
	@Consumes(MediaType.APPLICATION_XML)
	public String postSharePortfolio(String xmlNode, @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest, @PathParam("portfolioid") String portfolioid, @PathParam("userid") int user, @QueryParam("write") String write)
	{
		UserInfo ui = checkCredential(httpServletRequest, null, null, null);

		int returnValue=-1;
		try
		{
			returnValue = dataProvider.postCompleteShare(portfolioid, ui.userId, user);
			logRestRequest(httpServletRequest, xmlNode, Integer.toString(returnValue), Status.OK.getStatusCode());

			switch( returnValue )
			{
				case -1:
					throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");

				case -2:
					throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'�tes pas le propri�taire");

				default:
					break;
			}
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
			dataProvider.disconnect();
		}

		return Integer.toString(returnValue);
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

	@Path("/resources/resource/{node-parent-id}")
	@GET
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Consumes(MediaType.APPLICATION_XML)
	public String getResource( @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("node-parent-id") String nodeParentUuid,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept, @QueryParam("user") Integer userId )
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			String returnValue = dataProvider.getResource(new MimeType("text/xml"),nodeParentUuid, ui.userId, groupId).toString();
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
			dataProvider.disconnect();
		}
	}

	@Path("/resources/portfolios/{portfolio-id}")
	@GET
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	public String getResources( @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("portfolio-id") String portfolioUuid,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept, @QueryParam("user") Integer userId )
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			String returnValue = dataProvider.getResources(new MimeType("text/xml"),portfolioUuid, ui.userId, groupId).toString();
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
			dataProvider.disconnect();
		}
	}

	@Path("/resources/resource/{node-parent-uuid}")
	@PUT
	@Produces(MediaType.APPLICATION_XML)
	public String putResource(String xmlResource, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("node-parent-uuid") String nodeParentUuid,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("user") Integer userId )
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		KEvent event = new KEvent();
		event.requestType = KEvent.RequestType.POST;
		event.eventType = KEvent.EventType.NODE;
		event.uuid = nodeParentUuid;
		event.inputData = xmlResource;

		try
		{
			String returnValue = dataProvider.putResource(new MimeType("text/xml"),nodeParentUuid,xmlResource, ui.userId, groupId).toString();
			logRestRequest(httpServletRequest, xmlResource, returnValue, Status.OK.getStatusCode());

			eventbus.processEvent(event);

			return returnValue;
		}
		catch( RestWebApplicationException ex )
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, xmlResource, ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw ex;
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, xmlResource, ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			dataProvider.disconnect();
		}
	}

	@Path("/resources/{node-parent-uuid}")
	@POST
	@Produces(MediaType.APPLICATION_XML)
	public String postResource(String xmlResource, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("node-parent-uuid") String nodeParentUuid,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("user") Integer userId )
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			//TODO userId
			String returnValue = dataProvider.postResource(new MimeType("text/xml"),nodeParentUuid,xmlResource, ui.userId, groupId).toString();
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
			dataProvider.disconnect();
		}
	}

	@Path("/resources")
	@POST
	@Produces(MediaType.APPLICATION_XML)
	public String postResource(String xmlResource, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("type") Integer type, @QueryParam("resource") String resource,@QueryParam("user") Integer userId )
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			//TODO userId
			String returnValue = dataProvider.postResource(new MimeType("text/xml"),resource,xmlResource, ui.userId, groupId).toString();
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
			dataProvider.disconnect();
		}
	}

	@Path("/roleUser")
	@POST
	@Produces(MediaType.APPLICATION_XML)
	public String postRoleUser(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("grid") int grid,@QueryParam("user-id") Integer userid)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			//TODO userId
			String returnValue = dataProvider.postRoleUser(ui.userId, grid, userid).toString();
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
	public String putRole(String xmlRole, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @PathParam("role-id") int roleId)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			//TODO userId
			String returnValue = dataProvider.putRole(xmlRole, ui.userId, roleId).toString();
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
	public String getRolePortfolio( @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @QueryParam("role") String role, @PathParam("portfolio-id") String portfolioId,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			String returnValue = dataProvider.getRolePortfolio(new MimeType("text/xml"),role,portfolioId,ui.userId).toString();
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
			dataProvider.disconnect();
		}
	}

	@Path("/roles/role/{role-id}")
	@GET
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	public String getRole( @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("role-id") Integer roleId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{

			String returnValue = dataProvider.getRole(new MimeType("text/xml"),roleId,ui.userId).toString();
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

	@Path("/models")
	@GET
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	public String getModels(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			String returnValue = dataProvider.getModels(new MimeType("text/xml"),ui.userId).toString();
			if(returnValue.equals(""))
			{
				logRestRequest(httpServletRequest, null,null, Status.NOT_FOUND.getStatusCode());

				throw new RestWebApplicationException(Status.NOT_FOUND, "Role "+" not found");
			}

			//			if(accept.equals(MediaType.APPLICATION_JSON))
			//				returnValue = XML.toJSONObject(returnValue).toString();
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
			dataProvider.disconnect();
		}
	}

	@Path("/models/{model-id}")
	@GET
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	public String getModel(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("model-id") Integer modelId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			String returnValue = dataProvider.getModel(new MimeType("text/xml"),modelId,ui.userId).toString();
			if(returnValue.equals(""))
			{
				logRestRequest(httpServletRequest, null,null, Status.NOT_FOUND.getStatusCode());

				throw new RestWebApplicationException(Status.NOT_FOUND, "Role "+" not found");
			}

			//			if(accept.equals(MediaType.APPLICATION_JSON))
			//				returnValue = XML.toJSONObject(returnValue).toString();
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
			dataProvider.disconnect();
		}
	}

	@Path("/models")
	@POST
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	public String postModel(String xmlModel, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{

			String returnValue = dataProvider.postModels(new MimeType("text/xml"),xmlModel,ui.userId).toString();
			if(returnValue.equals(""))
			{
				logRestRequest(httpServletRequest, null,null, Status.NOT_FOUND.getStatusCode());

				throw new RestWebApplicationException(Status.NOT_FOUND, "Role "+" not found");
			}

			//			if(accept.equals(MediaType.APPLICATION_JSON))
			//				returnValue = XML.toJSONObject(returnValue).toString();
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
			dataProvider.disconnect();
		}
	}

	@Path("/resources/{resource-id}")
	@DELETE
	@Produces(MediaType.APPLICATION_XML)
	public String deleteResource(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("resource-id") String resourceUuid,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("user") Integer userId )
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			int nbResourceDeleted = Integer.parseInt(dataProvider.deleteResource(resourceUuid, ui.userId, groupId).toString());
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
			dataProvider.disconnect();
		}
	}

	@Path("/groupRights")
	@DELETE
	@Produces(MediaType.APPLICATION_XML)
	public String deleteGroupRights(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("groupRightId") Integer groupRightId)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			int nbResourceDeleted = Integer.parseInt(dataProvider.deleteGroupRights(groupId, groupRightId, ui.userId).toString());
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
	public String deleteUsers(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("userId") Integer userId)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			int nbResourceDeleted = Integer.parseInt(dataProvider.deleteUsers(userId, null).toString());
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

	@Path("/users/user/{user-id}")
	@DELETE
	@Produces(MediaType.APPLICATION_XML)
	public String deleteUser(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @PathParam("user-id") Integer userid)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			int nbResourceDeleted = Integer.parseInt(dataProvider.deleteUsers(ui.userId, userid).toString());
			//			 if(nbResourceDeleted==0)
			//			 {
			//				 System.out.print("supprim�");
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

	@Path("/groups/{portfolio-id}")
	@GET
	@Produces(MediaType.APPLICATION_XML)
	public String getGroupsPortfolio(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("portfolio-id") String portfolioUuid, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			String xmlGroups = dataProvider.getGroupsPortfolio(portfolioUuid, ui.userId);
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


	@Path("/credential/group/{portfolio-id}")
	@GET
	@Produces(MediaType.APPLICATION_XML)
	public String getUserGroupByPortfolio(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("portfolio-id") String portfolioUuid, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		HttpSession session = httpServletRequest.getSession(true);
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			String xmlGroups = dataProvider.getUserGroupByPortfolio(portfolioUuid, ui.userId);
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
//					ui.groupId = Integer.parseInt(gid);
//					session.setAttribute("gid", ui.groupId);
				}
			}
			else if( groups.getLength() == 0 )	// Pas de groupe, on rend invalide le choix
			{
//				ui.groupId = -1;
//				session.setAttribute("gid", ui.groupId);
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
			dataProvider.disconnect();
		}
	}

	/*
	@Path("/resources/resource/file/{lang}/{node-id}")
	@POST
	@Produces(MediaType.APPLICATION_XML)
	public String postFile( @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @PathParam("node-id") String nodeParentUuid,@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, group);

		try
		{
			String filename = "";
			MultipartRequest multi= new MultipartRequest(httpServletRequest,"c:\\temp\\upload",5*1024*1024);
			Enumeration files = multi.getFileNames();
			java.io.File f = null;
			while(files.hasMoreElements()) {
				String name=(String)files.nextElement();
				filename = multi.getFilesystemName(name);
				String type = multi.getContentType(name);
				f = multi.getFile(name);
			}
			if (f!=null) {
				InputStream is = new FileInputStream(f);
				byte b[]=new byte[is.available()];
				is.read(b);
				String extension = filename.substring(filename.lastIndexOf(".")+1);
				//PreparedStatement ps = new P
				//String resId = wadbackend.WadNode.getResId(connexion,nodeId);
				//outTrace.append("<br/>nodeId :"+nodeId+" resId:"+resId);
				//wadbackend.WadFile.saveDocumentBD (connexion, resId, b, filename, extension, username, lang, outTrace) ;
				//outTrace.append("<br/>filename :|"+filename+"|");
				System.out.println(filename+" : "+extension+" : "+b.length);
				//outPrint.append("<span id='file-filename'>"+filename+"</span>"+uploadFile_jsp[LANG][1]);
				// ============================
			} else {
				//outPrint.append("<span id='file-filename'>"+uploadFile_jsp[LANG][2]+"</span>"+uploadFile_jsp[LANG][1]);
				//outTrace.append("<br/>aucun fichier");
			}
			return "";
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, null,ex.getMessage()+"\n\n"+ex.getStackTrace(), Status.INTERNAL_SERVER_ERROR.getStatusCode());
			dataProvider.disconnect();
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
	}
	//*/

	@Path("/credential/login")
	@PUT
	@Produces(MediaType.APPLICATION_XML)
	@Consumes(MediaType.APPLICATION_XML)
	public Response  putCredentialFromXml(String xmlCredential, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		return this.postCredentialFromXml(xmlCredential, user, token, 0, sc, httpServletRequest);
	}

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

		try
		{
			Document doc = DomUtils.xmlString2Document(xmlCredential, new StringBuffer());
			Element credentialElement = doc.getDocumentElement();
			String login = "";
			String password = "";
			String substit = null;
			if(credentialElement.getNodeName().equals("credential"))
			{
				String[] templogin = DomUtils.getInnerXml(doc.getElementsByTagName("login").item(0)).split(":");
				password = DomUtils.getInnerXml(doc.getElementsByTagName("password").item(0));

				if( templogin.length > 1 )
					substit = templogin[1];
				login = templogin[0];
			}

			int dummy = 0;
			String[] resultCredential = dataProvider.postCredentialFromXml(dummy, login, password, substit);
			// 0: xml de retour
			// 1,2: username, uid
			// 3,4: substitute name, substitute id
			if( resultCredential == null )
			{
				event.status = 403;
				retVal = "invalid credential";
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
				}
				else
				{
					String login1 = resultCredential[1];
					int userId = Integer.parseInt(resultCredential[2]);

					session.setAttribute("user", login1);
					session.setAttribute("uid", userId);
					session.setAttribute("subuser", "");
					session.setAttribute("subuid", 0);
				}
				//				if(tokenID==null) throw new RestWebApplicationException(Status.FORBIDDEN, "invalid credential or invalid group member");
				//				else if(tokenID.length()==0) throw new RestWebApplicationException(Status.FORBIDDEN, "invalid credential or invalid group member");

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
			logger.error(ex.getMessage());
			ex.printStackTrace();
			logRestRequest(httpServletRequest, xmlCredential, ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			dataProvider.disconnect();
		}
	}


	/// Fetch current user information
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

	try
	{


			ServiceTicketValidator sv = new ServiceTicketValidator();



			if(casUrlValidation!=null)
				sv.setCasValidateUrl(casUrlValidation);
			else
				sv.setCasValidateUrl("https://cas-upmf.grenet.fr/serviceValidate");
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
			userId =  dataProvider.getUserId(sv.getUser());
			if(userId!=null)
			{
				session.setAttribute("user", sv.getUser());
				session.setAttribute("uid",Integer.parseInt(userId));
				dataProvider.disconnect();
			}
			else
			{
				dataProvider.disconnect();
				return Response.status(403).entity("Login "+sv.getUser()+" not found or bad CAS auth (bad ticket or bad url service : "+completeURL+") : "+sv.getErrorMessage()).build();
			}


			  Response response = null;

			    try {


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



			//return Response.ok(xmlResponse).build();
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			dataProvider.disconnect();
			throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires (ticket ?, casUrlValidation) :"+casUrlValidation);
		}


	}



	@Path("/credential/logout")
	@POST
	@Produces(MediaType.APPLICATION_XML)
	@Consumes(MediaType.APPLICATION_XML)
	public Response logout(@Context ServletConfig sc, @Context HttpServletRequest httpServletRequest)
	{
		HttpSession session = httpServletRequest.getSession(false);
		if( session != null )
			session.invalidate();
		return  Response.ok("logout").build();
	}

	@Path("/nodes/{node-id}/xsl/{xsl-file}")
	@GET
	@Produces(MediaType.TEXT_HTML)
	@Consumes(MediaType.APPLICATION_XML)
	public String getNodeWithXSL( @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("node-id") String nodeUuid,@PathParam("xsl-file") String xslFile, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept, @QueryParam("user") Integer userId, @QueryParam("p1") String p1, @QueryParam("p2") String p2, @QueryParam("p3") String  p3)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			javax.servlet.http.HttpSession session = httpServletRequest.getSession(true);
			String ppath = session.getServletContext().getRealPath(File.separator);
			// TODO xslFile avec _ => repertoire_fichier
			xslFile = xslFile.replace(".", "");
			xslFile = xslFile.replace("/", "");
			String[] tmp = xslFile.split("-");

			xslFile =       ppath.substring(0,ppath.lastIndexOf(File.separator))+File.separator+"xsl"+File.separator+tmp[0]+File.separator+tmp[1]+".xsl";
			String returnValue = dataProvider.getNodeWithXSL(new MimeType("text/xml"),nodeUuid,xslFile, ui.userId, groupId).toString();
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
			dataProvider.disconnect();
		}
	}

	@Path("/nodes/{node-id}/frommodelbysemantictag/{semantic-tag}")
	@POST
	@Consumes(MediaType.APPLICATION_XML)
	@Produces(MediaType.APPLICATION_XML)
	public String postNodeFromModelBySemanticTag(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("node-id") String nodeUuid, @PathParam("semantic-tag") String semantictag, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("user") Integer userId )
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		try
		{
			String returnValue = dataProvider.postNodeFromModelBySemanticTag(new MimeType("text/xml"),nodeUuid,semantictag, ui.userId, groupId).toString();
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
			dataProvider.disconnect();
		}
	}

	@Path("/portfolios/zip")
	@POST
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Produces(MediaType.TEXT_PLAIN)
	public String postPortfolioByForm( @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("user") Integer userId, @QueryParam("model") String modelId)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);
		String returnValue = "";

		try
		{
			returnValue = dataProvider.postPortfolioZip(new MimeType("text/xml"),new MimeType("text/xml"),httpServletRequest, ui.userId, groupId, modelId, ui.subId).toString();
		}
		catch( Exception e )
		{
			e.printStackTrace();
		}
		finally
		{
			dataProvider.disconnect();
		}

		return returnValue;
		/*
		String xmlPortfolio = "";
		try
		{
			MultipartRequest multi;
			String os = System.getProperty("os.name").toLowerCase();
			// windows
			if(os.indexOf("win") >= 0)
			{
				multi = new MultipartRequest(httpServletRequest,"c:\\windows\\temp",5*1024*1024);
			}
			else
			{
				multi = new MultipartRequest(httpServletRequest,"/tmp",5*1024*1024);
			}
			Enumeration files = multi.getFileNames();
			java.io.File f = null;
			String type = "";
			String name= "";

			while(files.hasMoreElements()) {
				name=(String)files.nextElement();
				String filename = multi.getFilesystemName(name);
				type = multi.getContentType(name);
				f = multi.getFile(name);
			}
			if (f!=null)
			{

				xmlPortfolio = DomUtils.file2String(f.getPath(), new StringBuffer());
			}
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, xmlPortfolio, ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());

			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		//*/

//		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		/*
		try
		{
			//try { this.userId = userId; } catch(Exception ex) { this.userId = -1; };
			String returnValue = dataProvider.postPortfolio(new MimeType("text/xml"),new MimeType("text/xml"),xmlPortfolio, ui.userId, groupId, modelId, ui.subId).toString();
			logRestRequest(httpServletRequest, xmlPortfolio, returnValue, Status.OK.getStatusCode());
			//	 				 	                        dataProvider.disconnect();
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
		//*/
	}

	@Path("/users/Portfolio/{portfolio-id}/Role/{role}/users")
	@GET
	@Produces(MediaType.APPLICATION_XML)
	public String getUsersByRole(@CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @PathParam("portfolio-id") String portfolioUuid, @PathParam("role") String role, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, group);

		try
		{
			String xmlUsers = dataProvider.getUsersByRole(ui.userId, portfolioUuid, role);
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
		UserInfo ui = checkCredential(httpServletRequest, user, token, group);

		try
		{
			String xmlGroups = dataProvider.getGroupsByRole(ui.userId, portfolioUuid, role);
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
		UserInfo ui = checkCredential(httpServletRequest, user, token, group);

		try
		{
			String xmlUsers = dataProvider.getUsersByGroup(ui.userId);
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

	@Path("/action")
	@POST
	@Consumes(MediaType.APPLICATION_XML+","+MediaType.TEXT_PLAIN)
	@Produces(MediaType.TEXT_PLAIN)
	public String postMacroManage( String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest,
			@QueryParam("macro") Integer macro, @QueryParam("role") String role )
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, group);

		String returnValue = "";
		try
		{
			// On ajoute un droit pour un r�le
			if( macro!=null && role!=null )
			{
				returnValue = dataProvider.postAddAction(ui.userId, macro, role, xmlNode);
				logRestRequest(httpServletRequest, xmlNode, returnValue, Status.OK.getStatusCode());

				if( returnValue=="faux" ) { throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces"); }

			}
			// On cr�e une nouvelle r�gle
			else if( macro==null && role==null )
			{
				returnValue = dataProvider.postCreateMacro(ui.userId, xmlNode).toString();
				logRestRequest(httpServletRequest, xmlNode, returnValue, Status.OK.getStatusCode());

				if( returnValue=="faux" ) { throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces"); }

			}
			// Erreur de requ�te
			else
			{
				returnValue = "";
			}

			return returnValue;
		}
		catch( RestWebApplicationException ex )
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
		}
		catch( Exception ex )
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, xmlNode, ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());
			dataProvider.disconnect();
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			dataProvider.disconnect();
		}
	}

	@Path("/action/{uuid}/{macro-name}")
	@POST
	@Consumes(MediaType.APPLICATION_XML+","+MediaType.TEXT_PLAIN)
	@Produces(MediaType.TEXT_PLAIN)
	public String postMacro(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @PathParam("uuid") String uuid, @PathParam("macro-name") String macroName,
			@Context ServletConfig sc, @Context HttpServletRequest httpServletRequest)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, group);

		String returnValue="";
		try
		{
			// On execute l'action sur le noeud uuid
			if( uuid != null && macroName != null )
			{
				returnValue = dataProvider.postMacroOnNode(ui.userId, uuid, macroName);
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
			dataProvider.disconnect();
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			dataProvider.disconnect();
		}
	}

	@Path("/action/{portfolio-id}")
	@GET
	@Produces(MediaType.APPLICATION_XML)
	public String getAction(@CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @PathParam("portfolio-id") String uuid,
			@Context ServletConfig sc,@Context HttpServletRequest httpServletRequest)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, group);

		String returnValue="";
		try
		{
			// Retourne le nom des actions possible sur ce portfolio
			// La r�solution d'o� on applique/affiche les actions se passe dans le client
			if( uuid != null )
			{
				returnValue = dataProvider.getPortfolioMacro(ui.userId, uuid);
				logRestRequest(httpServletRequest, "", returnValue, Status.OK.getStatusCode());

				if(returnValue == "faux")
				{
					throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
				}

			}
			// Erreur de requ�te
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
			logRestRequest(httpServletRequest, "",ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());
			dataProvider.disconnect();
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			dataProvider.disconnect();
		}
	}

	@Path("/action")
	@GET
	@Consumes(MediaType.APPLICATION_XML)
	@Produces(MediaType.APPLICATION_XML)
	public String getActionManager(@CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest,
			@QueryParam("macro") Integer macro)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, group);

		String returnValue="";
		try
		{
			// Retourne la liste des actions pour la macro sp�cifi�
			if( macro != null )
			{
				returnValue = dataProvider.getMacroActions(ui.userId, macro);
				logRestRequest(httpServletRequest, "", returnValue, Status.OK.getStatusCode());

				if(returnValue == "faux")
				{
					throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
				}

			}
			// Retourne le nom de toute les actions possible, avec les identifiants
			else if ( macro == null )
			{
				returnValue = dataProvider.getAllActionLabel(ui.userId);
				logRestRequest(httpServletRequest, "", returnValue, Status.OK.getStatusCode());

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
			logRestRequest(httpServletRequest, "",ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());
			dataProvider.disconnect();
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			dataProvider.disconnect();
		}
	}

	@Path("/action")
	@PUT
	@Consumes(MediaType.APPLICATION_XML+","+MediaType.TEXT_PLAIN)
	@Produces(MediaType.APPLICATION_XML)
	public String putAction(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("macro") Integer macro, @QueryParam("role") String role)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, group);

		String returnValue = "";
		try
		{
			// Mise � jour d'une action
			if ( macro != null && role != null )
			{
				returnValue = dataProvider.putMacroAction(ui.userId, macro, role, xmlNode);
				logRestRequest(httpServletRequest, xmlNode, returnValue, Status.OK.getStatusCode());

				if(returnValue == "faux")
				{
					throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
				}
			}
			// Mise � jour du nom d'une macro
			else if( macro != null )
			{
				returnValue = dataProvider.putMacroName(ui.userId, macro, xmlNode);
				logRestRequest(httpServletRequest, xmlNode, returnValue, Status.OK.getStatusCode());

				if(returnValue == "faux")
				{
					throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
				}
			}
			// Erreur de requ�te
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

	@Path("/action")
	@DELETE
	@Produces(MediaType.APPLICATION_XML)
	public String deleteAction(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("macro") Integer macro, @QueryParam("role") String role)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, group);

		String returnValue = "";
		try
		{
			// On efface une action de la macro
			if ( macro != null && role != null )
			{
				returnValue = dataProvider.deleteMacroAction(ui.userId, macro, role);
				logRestRequest(httpServletRequest, xmlNode, returnValue, Status.OK.getStatusCode());

				if(returnValue == "faux")
				{
					throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
				}

			}
			// On efface la macro au complet
			else if( macro != null )
			{
				returnValue = dataProvider.deleteMacro(ui.userId, macro);
				logRestRequest(httpServletRequest, xmlNode, returnValue, Status.OK.getStatusCode());

				if(returnValue == "faux")
				{
					throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
				}

			}
			// Erreur de requ�te
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

	/********************************************************/
	/** Partie utilisation des macro-commandes et gestion **/
	/********************************************************/
	/*
	@Path("/types")
	@POST
	@Consumes(MediaType.APPLICATION_XML+","+MediaType.TEXT_PLAIN)
	@Produces(MediaType.TEXT_PLAIN)
	public String postTypesManage(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest,
			@QueryParam("type") Integer type, @QueryParam("node") Integer nodeid,
			@QueryParam("parent") Integer parentid, @QueryParam("instance") Integer instance)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, group);

		String returnValue="";
		try
		{
			// Ajoute un noeud, sp�cifie l'instance que ce nouveau noeud utilise
			if( type != null )
			{
				returnValue = dataProvider.postAddNodeType(ui.userId, type, nodeid, parentid, instance, xmlNode);
				logRestRequest(httpServletRequest, xmlNode, returnValue, Status.OK.getStatusCode());

				if(returnValue == "faux")
				{
					throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
				}

			}
			// Cr�e un nouveau type, pas de noeud encore
			else if( type == null && nodeid == null && parentid == null && instance == null )
			{
				returnValue = dataProvider.postCreateType(ui.userId, xmlNode);
				logRestRequest(httpServletRequest, xmlNode, returnValue, Status.OK.getStatusCode());

				if(returnValue == "faux")
				{
					throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
				}

			}
			// Erreur de requ�te
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
		UserInfo ui = checkCredential(httpServletRequest, user, token, group);

		String returnValue="";
		try
		{
			// On instancie un type sous le noeud sp�cifi�. Cr�e les droits n�c�ssaire
			if( uuid != null && typeid != null )
			{
				returnValue = dataProvider.postUseType(ui.userId, uuid, typeid).toString();
				logRestRequest(httpServletRequest, xmlNode, returnValue, Status.OK.getStatusCode());

				if(returnValue == "faux")
				{
					throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
				}
			}
			// Erreur de requ�te
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
	public String getTypesManager( @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest,
			@QueryParam("type") Integer type)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, group);

		String returnValue="";
		try
		{
			// Retourne le contenu du type
			if( type != null )
			{
				returnValue = dataProvider.getTypeData(ui.userId, type);
				logRestRequest(httpServletRequest, "getTypesManager", returnValue, Status.OK.getStatusCode());

				if(returnValue == "faux")
				{
					throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
				}

			}
			// Retourne le nom des types disponible
			else if( type == null )
			{
				returnValue = dataProvider.getAllTypes(ui.userId);
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
		UserInfo ui = checkCredential(httpServletRequest, user, token, group);

		String returnValue="";
		try
		{
			// Retourne le contenu du type
			if( uuid == null && type != null )
			{
				returnValue = dataProvider.getTypeData(ui.userId, type);
				logRestRequest(httpServletRequest, "getTypes", returnValue, Status.OK.getStatusCode());

				if(returnValue == "faux")
				{
					throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
				}

			}
			// Retourne les types disponible dans un portfolio
			else if( uuid != null && type == null )
			{
				returnValue = dataProvider.getPortfolioTypes(ui.userId, uuid);
				logRestRequest(httpServletRequest, "getTypes", returnValue, Status.OK.getStatusCode());

				if(returnValue == "faux")
				{
					throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
				}
			}
			// Retourne le nom des types disponible
			else if( uuid == null && type == null )
			{
				returnValue = dataProvider.getAllTypes(ui.userId);
				logRestRequest(httpServletRequest, "getTypes", returnValue, Status.OK.getStatusCode());

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
		UserInfo ui = checkCredential(httpServletRequest, user, token, group);

		String returnValue="";
		try
		{
			// Mise � jour d'un noeud et de son contenu
			if( type != null && nodeid != null )
			{
				returnValue = dataProvider.putTypeData(ui.userId, type, nodeid, parentid, instance, xmlNode);
				logRestRequest(httpServletRequest, xmlNode, returnValue, Status.OK.getStatusCode());

				if(returnValue == "faux")
				{
					throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
				}

			}
			// Mise � jour du nom du type
			else if( type != null && nodeid == null )
			{
				returnValue = dataProvider.putTypeName(ui.userId, type, xmlNode);
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
		UserInfo ui = checkCredential(httpServletRequest, user, token, group);

		String returnValue="";
		try
		{
			// On efface un noeud de la d�finition
			if( type != null && nodeid != null )
			{
				returnValue = dataProvider.deleteTypeNode(ui.userId, type, nodeid);
				logRestRequest(httpServletRequest, xmlNode, returnValue, Status.OK.getStatusCode());

				if(returnValue == "faux")
				{
					throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
				}

			}
			// On efface la d�finition
			else if( type != null && nodeid == null )
			{
				returnValue = dataProvider.deleteType(ui.userId, type);
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
			dataProvider.disconnect();
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		finally
		{
			dataProvider.disconnect();
		}
	}
	//*/


	/********************************************************/
	/** Partie groupe de droits et utilisateurs            **/
	/********************************************************/

	@Path("/rights")
	@POST
	@Produces(MediaType.APPLICATION_XML)
	public String postChangeRights(String xmlNode, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest )
	{
		UserInfo ui = checkCredential(httpServletRequest, null, null, null);

		String returnValue="";
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
			String xpathRole = "/role";
			XPathExpression findRole = xPath.compile(xpathRole);
			String xpathNodeFilter = "/xpath";
			XPathExpression findXpath = xPath.compile(xpathNodeFilter);
			String nodefilter = "";
			NodeList roles = null;

			/// Fetch portfolio(s)
			String portfolioNode = "//portfoliogroup";
			Node portgroupnode = (Node) xPath.compile(portfolioNode).evaluate(doc, XPathConstants.NODE);
			if( portgroupnode == null )
			{
				String portgroupname = portgroupnode.getAttributes().getNamedItem("name").getNodeValue();
				// Query portfolio group for list of uuid

				// while( res.next() )
				// portfolio.add(portfolio);

				Node xpathNode = (Node) findXpath.evaluate(portgroupnode, XPathConstants.NODE);
				nodefilter = xpathNode.getNodeValue();
				roles = (NodeList) findRole.evaluate(portgroupnode, XPathConstants.NODESET);
			}
			else
			{
				// Or add the single one
				portfolioNode = "//portfolio[@uuid]";
				Node portnode = (Node) xPath.compile(portfolioNode).evaluate(doc, XPathConstants.NODE);
				portfolio.add(portnode.getNodeValue());

				Node xpathNode = (Node) findXpath.evaluate(portnode, XPathConstants.NODE);
				nodefilter = xpathNode.getNodeValue();
				roles = (NodeList) findRole.evaluate(portnode, XPathConstants.NODESET);
			}

			ArrayList<String> nodes = new ArrayList<String>();
			XPathExpression xpathFilter = xPath.compile(nodefilter);
			for( int i=0; i<portfolio.size(); ++i )	// For all portfolio
			{
				String portfolioUuid = portfolio.get(i);
				String portfolioStr = dataProvider.getPortfolio(new MimeType("text/xml"),portfolioUuid,ui.userId, 0, this.label, null, null, ui.subId).toString();
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
				String singleNode = "/node";
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
						dataProvider.postRights(ui.userId, nodeid, rolename, noderight);
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
						dataProvider.postMacroOnNode(ui.userId, nodeid, "reset");
					}
				}
			}

//			returnValue = dataProvider.postRRGCreate(ui.userId, xmlNode);
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

	/// Liste les rrg selon certain param�tres, portfolio, utilisateur, role
	@Path("/rolerightsgroups")
	@GET
	@Produces(MediaType.APPLICATION_XML)
	public String getRightsGroup( @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest, @QueryParam("portfolio") String portfolio, @QueryParam("user") Integer queryuser, @QueryParam("role") String role)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, group);

		String returnValue="";
		try
		{
			// Retourne le contenu du type
			returnValue = dataProvider.getRRGList(ui.userId, portfolio, queryuser, role);
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
		UserInfo ui = checkCredential(httpServletRequest, user, token, group);

		String returnValue="";
		try
		{
			// Retourne le contenu du type
			if( portId != null )
			{
				returnValue = dataProvider.getPortfolioInfo(ui.userId, portId);
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
		UserInfo ui = checkCredential(httpServletRequest, user, token, group);

		String returnValue="";
		try
		{
			// Retourne le contenu du type
			if( rrgId != null )
			{
				returnValue = dataProvider.getRRGInfo(ui.userId, rrgId);
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
		UserInfo ui = checkCredential(httpServletRequest, user, token, group);

		String returnValue="";
		try
		{
			// Retourne le contenu du type
			if( rrgId != null )
			{
				returnValue = dataProvider.putRRGUpdate(ui.userId, rrgId, xmlNode);
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

	@Path("/rolerightsgroups/{portfolio-id}")
	@POST
	@Produces(MediaType.APPLICATION_XML)
	public String postRightGroups(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @PathParam("portfolio-id") String portfolio, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest )
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, group);

		/**
		 * <node>LABEL</node>
		 */

		String returnValue="";
		try
		{
			returnValue = dataProvider.postRRGCreate(ui.userId, portfolio, xmlNode);
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
		UserInfo ui = checkCredential(httpServletRequest, user, token, group);

		String returnValue="";
		try
		{
			returnValue = dataProvider.postRRGUsers(ui.userId, rrgId, xmlNode);
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
		UserInfo ui = checkCredential(httpServletRequest, user, token, group);

		String returnValue="";
		try
		{
			returnValue = dataProvider.postRRGUser(ui.userId, rrgId, queryuser);
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
		UserInfo ui = checkCredential(httpServletRequest, user, token, group);

		String returnValue="";
		try
		{
			returnValue = dataProvider.deleteRRG(ui.userId, rrgId);
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
		UserInfo ui = checkCredential(httpServletRequest, user, token, group);

		String returnValue="";
		try
		{
			returnValue = dataProvider.deleteRRGUser(ui.userId, rrgId, queryuser);
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
		UserInfo ui = checkCredential(httpServletRequest, user, token, group);

		String returnValue="";
		try
		{
			// Retourne le contenu du type
			if( portId != null )
			{
				returnValue = dataProvider.deletePortfolioUser(ui.userId, portId);
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
		// checkCredential(httpServletRequest, user, token, group);
		try
		{
			Elgg elgg = new Elgg(elggDefaultApiUrl,elggDefaultSiteUrl,elggApiKey,ui.User,  elggDefaultUserPassword);
			return elgg.getSiteRiverFeed(iLimit);
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, "",javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());
			dataProvider.disconnect();
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
	}

	@Path("/elgg/wire")
	@POST
	@Produces(MediaType.APPLICATION_XML)
	public String getElggSiteRiverFeed(String message, @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,@Context HttpServletRequest httpServletRequest,
			@QueryParam("type") Integer type)
	{
		UserInfo ui = checkCredential(httpServletRequest, user, token, null);

		// checkCredential(httpServletRequest, user, token, group);
		try
		{
			Elgg elgg = new Elgg(elggDefaultApiUrl,elggDefaultSiteUrl,elggApiKey,ui.User, elggDefaultUserPassword);
			return elgg.postWire(message);
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			logRestRequest(httpServletRequest, "",javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());
			dataProvider.disconnect();
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
	}



}
