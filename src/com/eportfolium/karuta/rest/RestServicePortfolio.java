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
package com.eportfolium.karuta.rest;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.StringWriter;
import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.activation.MimeType;
import javax.servlet.ServletConfig;
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
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import com.eportfolium.karuta.data.provider.DataProvider;
import com.eportfolium.karuta.data.provider.MysqlDataProvider;
import com.eportfolium.karuta.data.utils.ConfigUtils;
import com.eportfolium.karuta.data.utils.DomUtils;
import com.eportfolium.karuta.data.utils.HttpClientUtils;
import com.eportfolium.karuta.data.utils.MailUtils;
import com.eportfolium.karuta.data.utils.SqlUtils;
import com.eportfolium.karuta.eventbus.KEvent;
import com.eportfolium.karuta.eventbus.KEventbus;
import com.eportfolium.karuta.security.ConnexionLdap;
import com.eportfolium.karuta.security.Credential;
import com.eportfolium.karuta.security.NodeRight;
import com.eportfolium.karuta.socialnetwork.Elgg;
import com.eportfolium.karuta.socialnetwork.Ning;
import com.google.gson.Gson;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.jasig.cas.client.validation.Assertion;
import org.jasig.cas.client.validation.Cas20ServiceTicketValidator;
import org.jasig.cas.client.validation.TicketValidationException;
import org.json.XML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/// I hate this line, sometime it works with a '/', sometime it doesn't
@Path("/api")
public class RestServicePortfolio {

    private static final SimpleDateFormat DT = new SimpleDateFormat("yyyy-MM-dd HHmmss");
    private static final SimpleDateFormat DT2 = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    private static final Logger logger = LoggerFactory.getLogger(RestServicePortfolio.class);

    private static final Logger securityLog = LoggerFactory.getLogger("securityLogger");
    private static final Logger authLog = LoggerFactory.getLogger("authLogger");
    private static final Logger errorLog = LoggerFactory.getLogger("errorLogger");
    private static final Logger editLog = LoggerFactory.getLogger("editLogger");

    private static final String logFormat = "[%1$s] %2$s %3$s: %4$s -- %5$s (%6$s) === %7$s\n";
    private static final String logFormatShort = "%7$s\n";

    private String tempdir;

    class UserInfo {
        String subUser = "";
        int subId = 0;
        String User = "";
        int userId = 0;
//		int groupId = -1;

        @Override
        public String toString() {
            return "UserInfo{" +
                    "subUser='" + subUser + '\'' +
                    ", subId=" + subId +
                    ", User='" + User + '\'' +
                    ", userId=" + userId +
                    '}';
        }
    }

    //	DataSource ds;
    private DataProvider dataProvider;
    private final Credential credential = new Credential();

    // Options
    private boolean activelogin;
    private String backend;
    private boolean resetPWEnable;
    private String emailResetMessage;
    private String ccEmail;
    private String basicLogoutRedirectionURL;
    private boolean casCreateAccount;
    private String casUrlValidation;
    private String casUrlsJsonString;
    private String elggDefaultApiUrl;
    private String elggDefaultSiteUrl;
    private String elggApiKey;
    private String elggDefaultUserPassword;
    private String shibbolethLogoutRedirectionURL;
    private Map<String, String> casUrlsValidation;

    private String ldapUrl;

    private String label;
    private String archivePath;

    private KEventbus eventbus = new KEventbus();

    /**
     * Initialize service objects
     **/
    public RestServicePortfolio(@Context ServletConfig sc) {
        try {
            // Loading configKaruta.properties
            ConfigUtils.init(sc.getServletContext());

            tempdir = System.getProperty("java.io.tmpdir", null);

            // Initialize
            activelogin = BooleanUtils.toBoolean(ConfigUtils.getInstance().getProperty("activate_login"));
            resetPWEnable = BooleanUtils.toBoolean(ConfigUtils.getInstance().getProperty("enable_password_reset"));
            emailResetMessage = ConfigUtils.getInstance().getProperty("email_password_reset");
            ccEmail = ConfigUtils.getInstance().getProperty("sys_email");
            basicLogoutRedirectionURL = ConfigUtils.getInstance().getProperty("baseui_redirect_location");
            backend = ConfigUtils.getInstance().getRequiredProperty("backendserver");
            // CAS
            casUrlValidation = ConfigUtils.getInstance().getProperty("casUrlValidation");
            casCreateAccount = BooleanUtils.toBoolean(ConfigUtils.getInstance().getProperty("casCreateAccount"));
            casUrlsJsonString = ConfigUtils.getInstance().getProperty("casUrlValidationMapping");
            Gson gson = new Gson();
            casUrlsValidation = gson.fromJson(casUrlsJsonString, Map.class);

            // LDAP
            ldapUrl = ConfigUtils.getInstance().getProperty("ldap.provider.url");

            // Elgg variables
            elggDefaultApiUrl = ConfigUtils.getInstance().getProperty("elggDefaultApiUrl");

            elggDefaultSiteUrl = ConfigUtils.getInstance().getProperty("elggDefaultSiteUrl");
            elggApiKey = ConfigUtils.getInstance().getProperty("elggApiKey");
            elggDefaultUserPassword = ConfigUtils.getInstance().getProperty("elggDefaultUserPassword");

            //shibboleth
            shibbolethLogoutRedirectionURL = ConfigUtils.getInstance().getProperty("shib_logout");

            // data provider
            dataProvider = SqlUtils.initProvider();

            archivePath = ConfigUtils.getInstance().getKarutaHome() + ConfigUtils.getInstance().getServletName() + "_archive" + File.separatorChar;
        } catch (Exception e) {
            logger.error("CAN'T INIT REST SERVICE from " + ConfigUtils.getInstance().getConfigPath(), e);
        }
    }

    /**
     * Fetch user session info
     **/
    public UserInfo checkCredential(HttpServletRequest request, String login, String token, String group) {
        HttpSession session = request.getSession(true);

        UserInfo ui = new UserInfo();
        Integer val = (Integer) session.getAttribute("uid");
        if (val != null) {
            ui.userId = val;
        } else {
            // Non valid userid
            logger.error("Request {} on '{}' unauthorized for a not logged in user", request.getMethod(), request.getRequestURI());
            throw new RestWebApplicationException(Status.UNAUTHORIZED, "User not logged in");
        }
//		val = (Integer) session.getAttribute("gid");
//		if( val != null )
//			ui.groupId = val;
        val = (Integer) session.getAttribute("subuid");
        if (val != null)
            ui.subId = val;
        ui.User = (String) session.getAttribute("user");
        ui.subUser = (String) session.getAttribute("subuser");

        return ui;
    }

    /**
     * Fetch current user info
     * GET /rest/api/credential
     * parameters:
     * return:
     * <user id="uid">
     * <username></username>
     * <firstname></firstname>
     * <lastname></lastname>
     * <email></email>
     * <admin>1/0</admin>
     * <designer>1/0</designer>
     * <active>1/0</active>
     * <substitute>1/0</substitute>
     * </user>
     **/
    @Path("/credential")
    @GET
    @Produces(MediaType.APPLICATION_XML)
    public Response getCredential(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,
                                  @Context HttpServletRequest httpServletRequest) {
        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        if (ui.userId == 0)    // Non valid userid
        {
            return Response.status(401).build();
        }

        try {
            c = SqlUtils.getConnection();

            String xmluser = dataProvider.getInfUser(c, ui.userId, ui.userId);

            /// Add shibboleth info if needed
            HttpSession session = httpServletRequest.getSession(false);
            Integer fromshibe = (Integer) session.getAttribute("fromshibe");
            /// If we need some special stuff

            return Response.ok(xmluser).build();
        } catch (Exception ex) {
            logger.error("getCredential - Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("getCredential - Managed error", e);
            }
        }
    }

    /**
     * Get groups from a user id
     * GET /rest/api/groups
     * parameters:
     * - group: group id
     * return:
     * <groups>
     * <group id="gid" owner="uid" templateId="rrgid">GROUP LABEL</group>
     * ...
     * </groups>
     **/
    @Path("/groups")
    @GET
    @Produces(MediaType.APPLICATION_XML)
    public String getGroups(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,
                            @Context HttpServletRequest httpServletRequest) {
        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        try {
            c = SqlUtils.getConnection();

            return (String) dataProvider.getUserGroups(c, ui.userId);
        } catch (Exception ex) {
            logger.error("getCredential - Managed error", ex);

            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }


    /**
     * Get user list
     * GET /rest/api/users
     * parameters:
     * return:
     * <users>
     * <user id="uid">
     * <username></username>
     * <firstname></firstname>
     * <lastname></lastname>
     * <admin>1/0</admin>
     * <designer>1/0</designer>
     * <email></email>
     * <active>1/0</active>
     * <substitute>1/0</substitute>
     * </user>
     * ...
     * </users>
     **/
    @Path("/users")
    @GET
    @Produces(MediaType.APPLICATION_XML)
    public String getUsers(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("username") String username,
                           @QueryParam("firstname") String firstname, @QueryParam("lastname") String lastname, @QueryParam("group") int groupId,
                           @QueryParam("email") String email, @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest) {
        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        if (ui.userId == 0) {
            logger.error("ui.userId not found!");
            throw new RestWebApplicationException(Status.FORBIDDEN, "Not logged in");
        }
        try {
            c = SqlUtils.getConnection();

            String xmlGroups;
            if (credential.isAdmin(c, ui.userId) || credential.isCreator(c, ui.userId))
                xmlGroups = dataProvider.getListUsers(c, ui.userId, username, firstname, lastname, email);
            else if (ui.userId != 0)
                xmlGroups = dataProvider.getInfUser(c, ui.userId, ui.userId);
            else
                throw new RestWebApplicationException(Status.FORBIDDEN, "Not authorized");

            return xmlGroups;
        } catch (Exception ex) {
            logger.error("getUsers - Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Get a specific user info
     * GET /rest/api/users/user/{user-id}
     * parameters:
     * return:
     * <user id="uid">
     * <username></username>
     * <firstname></firstname>
     * <lastname></lastname>
     * <admin>1/0</admin>
     * <designer>1/0</designer>
     * <email></email>
     * <active>1/0</active>
     * <substitute>1/0</substitute>
     * </user>
     **/
    @Path("/users/user/{user-id}")
    @GET
    @Produces(MediaType.APPLICATION_XML)
    public String getUser(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("user-id") int userid,
                          @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest) {
        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;
        try {
            c = SqlUtils.getConnection();
            return dataProvider.getInfUser(c, ui.userId, userid);
        } catch (RestWebApplicationException ex) {
            logger.error("getUser - Managed error", ex);

            throw new RestWebApplicationException(Status.NOT_FOUND, ex.getResponse().getEntity().toString());
        } catch (Exception ex) {
            logger.error("getUser - Managed error", ex);

            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Modify user info
     * PUT /rest/api/users/user/{user-id}
     * body:
     * <user id="uid">
     * <username></username>
     * <firstname></firstname>
     * <lastname></lastname>
     * <admin>1/0</admin>
     * <designer>1/0</designer>
     * <email></email>
     * <active>1/0</active>
     * <substitute>1/0</substitute>
     * </user>
     * <p>
     * parameters:
     * <p>
     * return:
     * <user id="uid">
     * <username></username>
     * <firstname></firstname>
     * <lastname></lastname>
     * <admin>1/0</admin>
     * <designer>1/0</designer>
     * <email></email>
     * <active>1/0</active>
     * <substitute>1/0</substitute>
     * </user>
     **/
    @Path("/users/user/{user-id}")
    @PUT
    @Produces(MediaType.APPLICATION_XML)
    public String putUser(String xmlInfUser, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId,
                          @PathParam("user-id") int userid, @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest) {
        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        try {
            c = SqlUtils.getConnection();

            String queryuser;
            if (credential.isAdmin(c, ui.userId) || credential.isCreator(c, ui.userId)) {
                queryuser = dataProvider.putInfUser(c, ui.userId, userid, xmlInfUser);
                if (queryuser == null)
                    throw new RestWebApplicationException(Status.FORBIDDEN, "Not authorized");
            } else if (ui.userId == userid)    /// Changing self
            {
                String ip = httpServletRequest.getRemoteAddr();
                securityLog.info("[{}] self change info '{}'}", ip, userid);
                queryuser = dataProvider.UserChangeInfo(c, ui.userId, userid, xmlInfUser);
            } else
                throw new RestWebApplicationException(Status.FORBIDDEN, "Not authorized");

            return queryuser;
        } catch (RestWebApplicationException ex) {
            logger.error("putUser - Managed error", ex);
            throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
        } catch (Exception ex) {
            logger.error("getUsers - Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, "Error : " + ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Get user id from username
     * GET /rest/api/users/user/username/{username}
     * parameters:
     * return:
     * userid (long)
     **/
    @Path("/users/user/username/{username}")
    @GET
    @Produces(MediaType.APPLICATION_XML)
    public String getUserId(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("username") String username,
                            @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest) {
        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        try {
            c = SqlUtils.getConnection();
            return dataProvider.getUserID(c, ui.userId, username);
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.NOT_FOUND, ex.getResponse().getEntity().toString());
        } catch (Exception ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("getUsers - Managed error", e);
            }
        }
    }

    /**
     * Get a list of role/group for this user
     * GET /rest/api/users/user/{user-id}/groups
     * parameters:
     * return:
     * <profiles>
     * <profile>
     * <group id="gid">
     * <label></label>
     * <role></role>
     * </group>
     * </profile>
     * </profiles>
     **/
    @Path("/users/user/{user-id}/groups")
    @GET
    @Produces(MediaType.APPLICATION_XML)
    public String getGroupsUser(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("user-id") int useridCible,
                                @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest) {
        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        try {
            c = SqlUtils.getConnection();

            return dataProvider.getRoleUser(c, ui.userId, useridCible);
        } catch (Exception ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Get rights in a role from a groupid
     * GET /rest/api/groupRights
     * parameters:
     * - group: role id
     * return:
     * <groupRights>
     * <groupRight  gid="groupid" templateId="grouprightid>
     * <item
     * AD="True/False"
     * creator="uid";
     * date="";
     * DL="True/False"
     * id=uuid
     * owner=uid";
     * RD="True/False"
     * SB="True"/"False"
     * typeId=" ";
     * WR="True/False"/>";
     * </groupRight>
     * </groupRights>
     **/
    @Path("/groupRights")
    @GET
    @Produces(MediaType.APPLICATION_XML)
    public String getGroupRights(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,
                                 @Context HttpServletRequest httpServletRequest) {
        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        try {
            c = SqlUtils.getConnection();

            return (String) dataProvider.getGroupRights(c, ui.userId, groupId);
        } catch (Exception ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Get role list from portfolio from uuid
     * GET /rest/api/groupRightsInfos
     * parameters:
     * - portfolioId: portfolio uuid
     * return:
     * <groupRightsInfos>
     * <groupRightInfo grid="grouprightid">
     * <label></label>
     * <owner>UID</owner>
     * </groupRightInfo>
     * </groupRightsInfos>
     **/
    @Path("/groupRightsInfos")
    @GET
    @Produces(MediaType.APPLICATION_XML)
    public String getGroupRightsInfos(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,
                                      @Context HttpServletRequest httpServletRequest, @QueryParam("portfolioId") String portfolioId) {
        if (!isUUID(portfolioId)) {
            logger.error("isUUID({}) is false", portfolioId);
            throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
        }

        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        try {
            c = SqlUtils.getConnection();

            return dataProvider.getGroupRightsInfos(c, ui.userId, portfolioId);
        } catch (Exception ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Get a portfolio from uuid
     * GET /rest/api/portfolios/portfolio/{portfolio-id}
     * parameters:
     * - resources:
     * - files: if set with resource, return a zip file
     * - export: if set, return xml as a file download
     * return:
     * zip
     * as file download
     * content
     * <?xml version=\"1.0\" encoding=\"UTF-8\"?>
     * <portfolio code=\"0\" id=\""+portfolioUuid+"\" owner=\""+isOwner+"\"><version>4</version>
     * <asmRoot>
     * <asm*>
     * <metadata-wad></metadata-wad>
     * <metadata></metadata>
     * <metadata-epm></metadata-epm>
     * <asmResource xsi_type="nodeRes">
     * <asmResource xsi_type="context">
     * <asmResource xsi_type="SPECIFIC TYPE">
     * </asm*>
     * </asmRoot>
     * </portfolio>
     **/
    @Path("/portfolios/portfolio/{portfolio-id}")
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, "application/zip", MediaType.APPLICATION_OCTET_STREAM})
    public Object getPortfolio(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("portfolio-id") String portfolioUuid,
                               @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept,
                               @QueryParam("user") Integer userId, @QueryParam("userrole") String userrole, @QueryParam("resources") String resource,
                               @QueryParam("files") String files, @QueryParam("export") String export, @QueryParam("lang") String lang, @QueryParam("level") Integer cutoff) {
        if (!isUUID(portfolioUuid)) {
            logger.error("isUUID({}) is false", portfolioUuid);
            throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
        }

        UserInfo ui = checkCredential(httpServletRequest, user, token, null);

        Connection c = null;
        Response response = null;
        try {
            c = SqlUtils.getConnection();
            String portfolio = dataProvider.getPortfolio(c, new MimeType("text/xml"), portfolioUuid, ui.userId, 0, userrole, resource, "", ui.subId, cutoff).toString();

            if (MysqlDataProvider.DATABASE_FALSE.equals(portfolio)) {
                response = Response.status(403).build();
            }

            if (response == null) {
                /// Finding back code. Not really pretty
                String timeFormat = DT.format(new Date());
                Document doc = DomUtils.xmlString2Document(portfolio, new StringBuffer());
                NodeList codes = doc.getDocumentElement().getElementsByTagName("code");
                // Le premier c'est celui du root
                Node codenode = codes.item(0);
                String code = "";
                if (codenode != null)
                    code = codenode.getTextContent();
                // Sanitize code
                code = code.replace("_", "");

                if (export != null) {
                    response = Response
                            .ok(portfolio)
                            .header("content-disposition", "attachment; filename = \"" + code + "-" + timeFormat + ".xml\"")
                            .build();
                } else if (resource != null && files != null) {
                    //// Cas du renvoi d'un ZIP
                    HttpSession session = httpServletRequest.getSession(true);
                    File tempZip = getZipFile(portfolioUuid, portfolio, lang, doc, session);

                    /// Return zip file
                    RandomAccessFile f = new RandomAccessFile(tempZip.getAbsoluteFile(), "r");
                    byte[] b = new byte[(int) f.length()];
                    f.read(b);
                    f.close();

                    response = Response
                            .ok(b, MediaType.APPLICATION_OCTET_STREAM)
                            .header("content-disposition", "attachment; filename = \"" + code + "-" + timeFormat + ".zip")
                            .build();

                    // Temp file cleanup
                    tempZip.delete();
                } else {
                    if (portfolio.equals(MysqlDataProvider.DATABASE_FALSE)) {
                        throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
                    }

                    if (accept.equals(MediaType.APPLICATION_JSON)) {
                        portfolio = XML.toJSONObject(portfolio).toString();
                        response = Response.ok(portfolio).type(MediaType.APPLICATION_JSON).build();
                    } else
                        response = Response.ok(portfolio).type(MediaType.APPLICATION_XML).build();


                }
            }
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
        } catch (SQLException ex) {

            logger.info("Portfolio " + portfolioUuid + " not found");
            throw new RestWebApplicationException(Status.NOT_FOUND, "Portfolio " + portfolioUuid + " not found");
        } catch (Exception ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }

        return response;
    }

    private File getZipFile(String portfolioUuid, String portfolioContent, String lang, Document doc, HttpSession session) throws IOException, XPathExpressionException {
        if (!isUUID(portfolioUuid)) {
            logger.error("isUUID({}) is false", portfolioUuid);
            throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
        }

        /// Temp file in temp directory
        File tempDir = new File(tempdir);
        if (!tempDir.isDirectory())
            tempDir.mkdirs();
        File tempZip = File.createTempFile(portfolioUuid, ".zip", tempDir);

        FileOutputStream fos = new FileOutputStream(tempZip);
        ZipOutputStream zos = new ZipOutputStream(fos);

        /// Write xml file to zip
        ZipEntry ze = new ZipEntry(portfolioUuid + ".xml");
        zos.putNextEntry(ze);

        byte[] bytes = portfolioContent.getBytes(StandardCharsets.UTF_8);
        zos.write(bytes);

        zos.closeEntry();

        /// Find all fileid/filename
        XPath xPath = XPathFactory.newInstance().newXPath();
        String filterRes = "//*[local-name()='asmResource']/*[local-name()='fileid' and text()]";
        NodeList nodelist = (NodeList) xPath.compile(filterRes).evaluate(doc, XPathConstants.NODESET);

        /// Fetch all files
        for (int i = 0; i < nodelist.getLength(); ++i) {
            Node res = nodelist.item(i);
            /// Check if fileid has a lang
            Node langAtt = res.getAttributes().getNamedItem("lang");
            String filterName;
            if (langAtt != null) {
                lang = langAtt.getNodeValue();
                filterName = ".//*[local-name()='filename' and @lang='" + lang + "' and text()]";
            } else {
                filterName = ".//*[local-name()='filename' and @lang and text()]";
            }

            Node p = res.getParentNode();    // fileid -> resource
            Node gp = p.getParentNode();    // resource -> context
            Node uuidNode = gp.getAttributes().getNamedItem("id");
            String uuid = uuidNode.getTextContent();

            NodeList textList = (NodeList) xPath.compile(filterName).evaluate(p, XPathConstants.NODESET);
            String filename = "";
            if (textList.getLength() != 0) {
                Element fileNode = (Element) textList.item(0);
                filename = fileNode.getTextContent();
                lang = fileNode.getAttribute("lang");    // In case it's a general fileid, fetch first filename (which can break things if nodes are not clean)
                if ("".equals(lang)) lang = "fr";
            }

            String url = backend + "/resources/resource/file/" + uuid + "?lang=" + lang;
            HttpGet get = new HttpGet(url);

            // Transfer sessionid so that local request still get security checked
            get.addHeader("Cookie", "JSESSIONID=" + session.getId());

            // Send request
            CloseableHttpClient client = HttpClients.createDefault();
            CloseableHttpResponse ret = client.execute(get);
            HttpEntity entity = ret.getEntity();

            // Put specific name for later recovery
            if ("".equals(filename))
                continue;
            int lastDot = filename.lastIndexOf(".");
            if (lastDot < 0)
                lastDot = 0;
            String filenameext = filename;    /// find extension
            int extindex = filenameext.lastIndexOf(".") + 1;
            filenameext = uuid + "_" + lang + "." + filenameext.substring(extindex);

            // Save it to zip file
            InputStream content = entity.getContent();
            ze = new ZipEntry(filenameext);
            try {
                int totalread = 0;
                zos.putNextEntry(ze);
                int inByte;
                byte[] buf = new byte[4096];
                while ((inByte = content.read(buf)) != -1) {
                    totalread += inByte;
                    zos.write(buf, 0, inByte);
                }
                logger.info("FILE: {} -> {}", filenameext, totalread);
                content.close();
                zos.closeEntry();
            } catch (Exception e) {
                logger.error("Managed error", e);
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
     * Return the portfolio from its code
     * GET /rest/api/portfolios/code/{code}
     * parameters:
     * return:
     * see 'content' of "GET /rest/api/portfolios/portfolio/{portfolio-id}"
     **/
    @Path("/portfolios/portfolio/code/{code : .+}")
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Object getPortfolioByCode(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("code") String code,
                                     @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept,
                                     @QueryParam("user") Integer userId, @QueryParam("group") Integer group, @QueryParam("userrole") String userrole, @QueryParam("resources") String resources) {
        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        try {
            c = SqlUtils.getConnection();

            // Try with world public access
            String userid = dataProvider.getUserId(c, "public", null);
            int uid = Integer.parseInt(userid);
            String portfo = dataProvider.getPortfolioByCode(c, new MimeType("text/xml"), code, uid, -1, userrole, "true", -1).toString();

            if( !"faux".equals(portfo) )
            {
                Response.status(Status.NOT_FOUND).entity("").build();
            }

            if (ui.userId == 0) {
                return Response.status(Status.FORBIDDEN).build();
            }

            if (resources == null)
                resources = "false";
            String returnValue = dataProvider.getPortfolioByCode(c, new MimeType("text/xml"), code, ui.userId, groupId, userrole, resources, ui.subId).toString();
            if (MysqlDataProvider.DATABASE_FALSE.equals(returnValue)) {
                logger.error("Code {} not found or user without rights", code);
                throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
            }
            if ("".equals(returnValue)) {
                logger.error("Code {} not found", code);
                return Response.status(Status.NOT_FOUND).entity("").build();
            }
            if (MediaType.APPLICATION_JSON.equals(accept))    // Not really used
                returnValue = XML.toJSONObject(returnValue).toString();


            return returnValue;
        } catch (RestWebApplicationException ex) {
            logger.error("getPortfolioByCode error, will return FORBIDDEN", ex);
            throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
        } catch (SQLException ex) {
            logger.error("getPortfolioByCode error, will return NOT_FOUND for code {}", code, ex);
            throw new RestWebApplicationException(Status.NOT_FOUND, "Portfolio code = " + code + " not found");
        } catch (Exception ex) {
            logger.error("getPortfolioByCode error, will return error 500", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("getPortfolioByCode SQLException error", e);
            }
        }
    }

    /**
     * List portfolios for current user (return also other things, but should be removed)
     * GET /rest/api/portfolios
     * parameters:
     * - active: false/0	(also show inactive portoflios)
     * - code
     * - n: number of results (10<n<50)
     * - i: index start + n
     * - userid: for this user (only with root)
     * return:
     * <?xml version=\"1.0\" encoding=\"UTF-8\"?>
     * <portfolios>
     * <portfolio  id="uuid" root_node_id="uuid" owner="Y/N" ownerid="uid" modified="DATE">
     * <asmRoot id="uuid">
     * <metadata-wad/>
     * <metadata-epm/>
     * <metadata/>
     * <code></code>
     * <label/>
     * <description/>
     * <semanticTag/>
     * <asmResource xsi_type="nodeRes"></asmResource>
     * <asmResource xsi_type="context"/>
     * </asmRoot>
     * </portfolio>
     * ...
     * </portfolios>
     **/
    @Path("/portfolios")
    @GET
    @Consumes(MediaType.APPLICATION_XML)
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public String getPortfolios(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("userrole") String userrole, @QueryParam("group") int groupId, @Context ServletConfig sc,
                                @Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept, @QueryParam("active") String active,
                                @QueryParam("userid") Integer userId, @QueryParam("code") String code, @QueryParam("portfolio") String portfolioUuid,
                                @QueryParam("i") String index, @QueryParam("n") String numResult, @QueryParam("level") Integer cutoff, @QueryParam("public") String public_var,
                                @QueryParam("project") String project, @QueryParam("count") String count, @QueryParam("search") String search) {
        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        try {
            c = SqlUtils.getConnection();
            
            if (portfolioUuid != null) {
                String returnValue = dataProvider.getPortfolio(c, new MimeType("text/xml"), portfolioUuid, ui.userId, groupId, userrole, null,
                        null, ui.subId, cutoff).toString();
                if (accept.equals(MediaType.APPLICATION_JSON))
                    returnValue = XML.toJSONObject(returnValue).toString();


                return returnValue;

            } else {
                String portfolioCode = null;
                String returnValue;
                boolean countOnly;
                boolean portfolioActive;
                Boolean portfolioProject = null;
                String portfolioProjectId = null;

                try {
                    portfolioActive = BooleanUtils.isNotFalse(BooleanUtils.toBooleanObject(active));
                } catch (Exception ex) {
                    portfolioActive = true;
                }


                try {
                    if (BooleanUtils.isFalse(BooleanUtils.toBooleanObject(project))) portfolioProject = false;
                    else if (BooleanUtils.toBoolean(project)) portfolioProject = true;
                    else if (project.length() > 0) portfolioProjectId = project;
                } catch (Exception ex) {
                    //portfolioProject = null;
                }


                try {
                    countOnly = BooleanUtils.toBoolean(count);
                } catch (Exception ex) {
                    countOnly = false;
                }


                try {
                    portfolioCode = code;
                } catch (Exception ex) {
                    logger.error("Managed error", ex);
                }

                if (portfolioCode != null) {
                    returnValue = dataProvider.getPortfolioByCode(c, new MimeType("text/xml"), portfolioCode, ui.userId, groupId, userrole, null, ui.subId).toString();
                } else {
                    if (public_var != null) {
                        int publicid = credential.getMysqlUserUid(c, "public");
                        returnValue = dataProvider.getPortfolios(c, new MimeType("text/xml"), publicid, groupId, userrole, portfolioActive, 0, portfolioProject,
                                portfolioProjectId, countOnly, search).toString();
                    } else if (userId != null && credential.isAdmin(c, ui.userId)) {
                        returnValue = dataProvider.getPortfolios(c, new MimeType("text/xml"), userId, groupId, userrole, portfolioActive, ui.subId, portfolioProject,
                                portfolioProjectId, countOnly, search).toString();
                    } else    /// For user logged in
                    {
                        returnValue = dataProvider.getPortfolios(c, new MimeType("text/xml"), ui.userId, groupId, userrole, portfolioActive, ui.subId, portfolioProject,
                                portfolioProjectId, countOnly, search).toString();
                    }

                    if (accept.equals(MediaType.APPLICATION_JSON))
                        returnValue = XML.toJSONObject(returnValue).toString();
                }


                return returnValue;
            }
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
        } catch (SQLException ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.NOT_FOUND, "Portfolios  not found");
        } catch (Exception ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Rewrite portfolio content
     * PUT /rest/api/portfolios/portfolios/{portfolio-id}
     * parameters:
     * content
     * see GET /rest/api/portfolios/portfolio/{portfolio-id}
     * and/or the asm format
     * return:
     **/
    @Path("/portfolios/portfolio/{portfolio-id}")
    @PUT
    @Consumes(MediaType.APPLICATION_XML)
    @Produces(MediaType.APPLICATION_XML)
    public String putPortfolio(String xmlPortfolio, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId,
                               @PathParam("portfolio-id") String portfolioUuid, @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest,
                               @QueryParam("active") String active, @QueryParam("user") Integer userId) {
        if (!isUUID(portfolioUuid)) {
            logger.error("isUUID({}) is false", portfolioUuid);
            throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
        }

        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        try {
            boolean portfolioActive = BooleanUtils.isNotFalse(BooleanUtils.toBooleanObject(active));

            c = SqlUtils.getConnection();
            dataProvider.putPortfolio(c, new MimeType("text/xml"), new MimeType("text/xml"), xmlPortfolio, portfolioUuid, ui.userId, portfolioActive, groupId, null);

            return "";

        } catch (Exception ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Reparse portfolio rights
     * POST /rest/api/portfolios/portfolios/{portfolio-id}/parserights
     * parameters:
     * return:
     **/
    @Path("/portfolios/portfolio/{portfolio-id}/parserights")
    @POST
    public Response postPortfolio(@PathParam("portfolio-id") String portfolioUuid, @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest) {
        UserInfo ui = checkCredential(httpServletRequest, null, null, null);
        Connection c = null;

        try {
            c = SqlUtils.getConnection();
            if (!credential.isAdmin(c, ui.userId))
                return Response.status(Status.FORBIDDEN).build();

            dataProvider.postPortfolioParserights(c, portfolioUuid, ui.userId);

            return Response.status(Status.OK).build();
        } catch (Exception ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Change portfolio owner
     * PUT /rest/api/portfolios/portfolios/{portfolio-id}/setOwner/{newOwnerId}
     * parameters:
     * - portfolio-id
     * - newOwnerId
     * return:
     **/
    @Path("/portfolios/portfolio/{portfolio-id}/setOwner/{newOwnerId}")
    @PUT
    @Consumes(MediaType.APPLICATION_XML)
    @Produces(MediaType.APPLICATION_XML)
    public String putPortfolioOwner(String xmlPortfolio, @CookieParam("user") String user, @CookieParam("credential") String token, @PathParam("portfolio-id") String portfolioUuid,
                                    @PathParam("newOwnerId") int newOwner, @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest) {
        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;
        boolean retval = false;

        try {
            c = SqlUtils.getConnection();
            // Check if logged user is either admin, or owner of the current portfolio
            if (credential.isAdmin(c, ui.userId) || credential.isOwner(c, ui.userId, portfolioUuid)) {
                retval = credential.putPortfolioOwner(c, portfolioUuid, newOwner);

            }
        } catch (Exception ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }

        return Boolean.toString(retval);
    }

    /**
     * Modify some portfolio option
     * PUT /rest/api/portfolios/portfolios/{portfolio-id}
     * parameters:
     * - portfolio: uuid
     * - active:	0/1, true/false
     * return:
     **/
    @Path("/portfolios")
    @PUT
    @Consumes(MediaType.APPLICATION_XML)
    @Produces(MediaType.APPLICATION_XML)
    public String putPortfolioConfiguration(String xmlPortfolio, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId,
                                            @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest, @QueryParam("portfolio") String portfolioUuid,
                                            @QueryParam("active") Boolean portfolioActive) {
        if (!isUUID(portfolioUuid)) {
            logger.error("isUUID({}) is false", portfolioUuid);
            throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
        }

        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        try {
            if (portfolioUuid != null && portfolioActive != null) {
                c = SqlUtils.getConnection();
                dataProvider.putPortfolioConfiguration(c, portfolioUuid, portfolioActive, ui.userId);
            }
            return "";
        } catch (Exception ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Add a user
     * POST /rest/api/users
     * parameters:
     * content:
     * <users>
     * <user id="uid">
     * <username></username>
     * <firstname></firstname>
     * <lastname></lastname>
     * <admin>1/0</admin>
     * <designer>1/0</designer>
     * <email></email>
     * <active>1/0</active>
     * <substitute>1/0</substitute>
     * </user>
     * ...
     * </users>
     * <p>
     * return:
     **/
    @Path("/users")
    @POST
    @Consumes(MediaType.APPLICATION_XML)
    @Produces(MediaType.APPLICATION_XML)
    public Response postUser(String xmluser, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId,
                             @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest) {
        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        try {
            c = SqlUtils.getConnection();
            final String xmlUser = dataProvider.postUsers(c, xmluser, ui.userId);
            if (xmlUser == null) {
                return Response.status(Status.CONFLICT).entity("Existing user or invalid input").build();
            }

            return Response.ok(xmlUser).build();
        } catch (Exception ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.BAD_REQUEST, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Unused (?)
     * POST /rest/api/label/{label}
     * parameters:
     * return:
     **/
    @Deprecated
    @Path("/label/{label}")
    @POST
    @Produces(MediaType.APPLICATION_XML)
    public Response postCredentialGroupLabel(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId,
                                             @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest, @PathParam("label") String label) {
        checkCredential(httpServletRequest, user, token, null);
        try {
            final String name = sc.getServletContext().getContextPath();

            return Response.ok().build(); //.cookie(new NewCookie("label", label, name, null, null, 3600 /*maxAge*/, false)).build();
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.FORBIDDEN, ex.getMessage());
        } catch (Exception ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        }
    }

    /**
     * Selecting role for current user
     * 	TODO: Was deactivated, but it might come back later on
     * 	POST /rest/api/credential/group/{group-id}
     * 	parameters:
     * 	- group: group id
     * 	return:
     **/
    @Deprecated
    @Path("/credential/group/{group-id}")
    @POST
    @Produces(MediaType.APPLICATION_XML)
    public Response postCredentialGroup(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,
                                        @Context HttpServletRequest httpServletRequest) {
        HttpSession session = httpServletRequest.getSession(true);
        UserInfo ui = checkCredential(httpServletRequest, user, token, null);

        try {
//			if(dataProvider.isUserMemberOfGroup( ui.userId, groupId))
            {
//				ui.groupId = groupId.intValue();
//				session.setAttribute("gid", ui.groupId);
                return Response.ok().build(); //.cookie(new NewCookie("group", groupId, name, null, null, 36000 /*maxAge*/, false)).build();
            }
//			else throw new RestWebApplicationException(Status.FORBIDDEN, ui.userId+" ne fait pas parti du groupe "+groupId);

        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.FORBIDDEN, ex.getMessage());
        } catch (Exception ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        }
    }

    /**
     * Add a user group
     * POST /rest/api/credential/group/{group-id}
     * parameters:
     * <group grid="" owner="" label=""></group>
     * <p>
     * return:
     * <group grid="" owner="" label=""></group>
     **/
    @Path("group")
    @POST
    @Produces(MediaType.APPLICATION_XML)
    public String postGroup(String xmlgroup, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId,
                            @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest) {
        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        try {
            c = SqlUtils.getConnection();

            return (String) dataProvider.postGroup(c, xmlgroup, ui.userId);
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } catch (Exception ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.BAD_REQUEST, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
//			dataProvider.disconnect();
        }
    }

    /**
     * Insert a user in a user group
     * POST /rest/api/groupsUsers
     * parameters:
     * -	group: gid
     * - userId: uid
     * return:
     * <ok/>
     **/
    @Path("/groupsUsers")
    @POST
    @Produces(MediaType.APPLICATION_XML)
    public String postGroupsUsers(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId,
                                  @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest, @QueryParam("userId") int userId) {
        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        try {
            c = SqlUtils.getConnection();
            if (dataProvider.postGroupsUsers(c, ui.userId, userId, groupId)) {
                return "<ok/>";
            } else throw new RestWebApplicationException(Status.FORBIDDEN, ui.userId + " ne fait pas parti du groupe " + groupId);

        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
        } catch (Exception ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Change the group right associated to a user group
     * POST /rest/api/RightGroup
     * parameters:
     * - group:	user group id
     * - groupRightId: group right id
     * return:
     **/
    @Path("RightGroup")
    @POST
    @Produces(MediaType.APPLICATION_XML)
    public Response postRightGroup(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,
                                   @Context HttpServletRequest httpServletRequest, @QueryParam("groupRightId") int groupRightId) {
        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        try {
            c = SqlUtils.getConnection();
            if (dataProvider.postRightGroup(c, groupRightId, groupId, ui.userId)) {
                logger.trace("ajouté");
            } else throw new RestWebApplicationException(Status.FORBIDDEN, ui.userId + " ne fait pas parti du groupe " + groupId);

        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
        } catch (Exception ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
        return null;
    }

    /**
     * From a base portfolio, make an instance with parsed rights in the attributes
     * POST /rest/api/portfolios/instanciate/{portfolio-id}
     * parameters:
     * - sourcecode: if set, rather than use the provided portfolio uuid, search for the portfolio by code
     * - targetcode: code we want the portfolio to have. If code already exists, adds a number after
     * - copyshared: y/null Make a copy of shared nodes, rather than keeping the link to the original data
     * - owner: true/null Set the current user instanciating the portfolio as owner. Otherwise keep the one that created it.
     * <p>
     * return:
     * instanciated portfolio uuid
     **/
    @Path("/portfolios/instanciate/{portfolio-id}")
    @POST
    public Object postInstanciatePortfolio(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,
                                           @Context HttpServletRequest httpServletRequest, @PathParam("portfolio-id") String portfolioId, @QueryParam("sourcecode") String srccode,
                                           @QueryParam("targetcode") String tgtcode, @QueryParam("copyshared") String copy, @QueryParam("groupname") String groupname,
                                           @QueryParam("owner") String setowner) {
		/*
		if( !isUUID(portfolioId) )
		{
			logger.error("isUUID({}) is false", portfolioId);
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}
		//*/

        String value = "Instanciate: " + portfolioId;

        UserInfo ui = checkCredential(httpServletRequest, user, token, null);

        //// TODO: IF user is creator and has parameter owner -> change ownership
        Connection c = null;

        try {
            c = SqlUtils.getConnection();
            if (!credential.isAdmin(c, ui.userId) && !credential.isCreator(c, ui.userId)) {
                return Response.status(Status.FORBIDDEN).entity("403").build();
            }

            boolean setOwner = BooleanUtils.toBoolean(setowner);
            boolean copyshared = BooleanUtils.toBoolean(copy);

            /// Check if code exist, find a suitable one otherwise. Eh.
            String newcode = tgtcode;
            int num = 0;
            while (dataProvider.isCodeExist(c, newcode, null))
                newcode = tgtcode + " (" + num++ + ")";
            tgtcode = newcode;

            final String returnValue = dataProvider.postInstanciatePortfolio(c, new MimeType("text/xml"), portfolioId, srccode, tgtcode, ui.userId, groupId, copyshared,
                    groupname, setOwner).toString();

            if (returnValue.startsWith("no rights"))
                throw new RestWebApplicationException(Status.FORBIDDEN, returnValue);
            else if (returnValue.startsWith("erreur"))
                throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, returnValue);
            else if ("".equals(returnValue)) {
                return Response.status(Status.NOT_FOUND).build();
            }

            return returnValue;
        } catch (RestWebApplicationException e) {
            logger.error("Managed error", e);
            throw e;
        } catch (Exception ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * From a base portfolio, just make a direct copy without rights parsing
     * POST /rest/api/portfolios/copy/{portfolio-id}
     * parameters:
     * Same as in instanciate
     * return:
     * Same as in instanciate
     **/
    @Path("/portfolios/copy/{portfolio-id}")
    @POST
    public Response postCopyPortfolio(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,
                                      @Context HttpServletRequest httpServletRequest, @PathParam("portfolio-id") String portfolioId, @QueryParam("sourcecode") String srccode,
                                      @QueryParam("targetcode") String tgtcode, @QueryParam("owner") String setowner) {
		/*
		if( !isUUID(portfolioId) )
		{
			logger.error("is UUID({}) is false", portfolioId);
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}
		//*/

        String value = "Instanciate: " + portfolioId;

        UserInfo ui = checkCredential(httpServletRequest, user, token, null);

        //// TODO: IF user is creator and has parameter owner -> change ownership
        Connection c = null;
        try {
            c = SqlUtils.getConnection();
            if (!credential.isAdmin(c, ui.userId) && !credential.isCreator(c, ui.userId)) {
                return Response.status(Status.FORBIDDEN).entity("403").build();
            }

            /// Check if code exist, find a suitable one otherwise. Eh.
            String newcode = tgtcode;
            if (dataProvider.isCodeExist(c, newcode, null)) {
                return Response.status(Status.CONFLICT).entity("code exist").build();
            }

            boolean setOwner = Boolean.parseBoolean(setowner);
            tgtcode = newcode;

            String returnValue = dataProvider.postCopyPortfolio(c, new MimeType("text/xml"), portfolioId, srccode, tgtcode, ui.userId, setOwner).toString();


            return Response.status(Status.OK).entity(returnValue).build();
        } catch (Exception ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * As a form, import xml into the database
     * POST /rest/api/portfolios
     * parameters:
     * return:
     **/
    @Path("/portfolios")
    @POST
//	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_XML)
    public String postFormPortfolio(@FormDataParam("uploadfile") String xmlPortfolio, @CookieParam("user") String user, @CookieParam("credential") String token,
                                    @QueryParam("group") int groupId, @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest,
                                    @QueryParam("user") Integer userId, @QueryParam("model") String modelId, @QueryParam("srce") String srceType,
                                    @QueryParam("srceurl") String srceUrl, @QueryParam("xsl") String xsl, @FormDataParam("instance") String instance,
                                    @FormDataParam("project") String projectName) {
        return postPortfolio(xmlPortfolio, user, token, groupId, sc, httpServletRequest, userId, modelId, srceType, srceUrl, xsl, instance, projectName);
    }

    /**
     * As a form, import xml into the database
     * POST /rest/api/portfolios
     * parameters:
     * - model: another uuid, not sure why it's here
     * - srce: sakai/null	Need to be logged in on sakai first
     * - srceurl: url part of the sakai system to fetch
     * - xsl: filename when using with sakai source, convert data before importing it
     * - instance: true/null if as an instance, parse rights. Otherwise just write nodes
     * xml: ASM format
     * return:
     * <portfolios>
     * <portfolio id="uuid"/>
     * </portfolios>
     **/
    @Path("/portfolios")
    @POST
    @Consumes(MediaType.APPLICATION_XML)
//	@Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_XML)
    public String postPortfolio(String xmlPortfolio, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId,
                                @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest, @QueryParam("user") Integer userId,
                                @QueryParam("model") String modelId, @QueryParam("srce") String srceType, @QueryParam("srceurl") String srceUrl,
                                @QueryParam("xsl") String xsl, @QueryParam("instance") String instance, @QueryParam("project") String projectName) {
        UserInfo ui = checkCredential(httpServletRequest, user, token, null);

        if ("sakai".equals(srceType)) {
            /// Session Sakai
            HttpSession session = httpServletRequest.getSession(false);
            if (session != null) {
                final String sakai_session = (String) session.getAttribute("sakai_session");
                final String sakai_server = (String) session.getAttribute("sakai_server");    // Base server http://localhost:9090
                final String url = sakai_server + "/" + srceUrl;
                final Header header = new BasicHeader("JSESSIONID", sakai_session);
                final Set<Header> headers = new HashSet<>();
                headers.add(header);

                HttpResponse response= HttpClientUtils.goGet(headers, url);
                if (response != null) {
                    // Retrieve data
                    try {
                        InputStream retrieve = response.getEntity().getContent();
                        String sakaiData = IOUtils.toString(retrieve, StandardCharsets.UTF_8);

                        //// Convert it via XSL
                        /// Path to XSL
                        String servletDir = sc.getServletContext().getRealPath("/");
                        int last = servletDir.lastIndexOf(File.separator);
                        last = servletDir.lastIndexOf(File.separator, last - 1);
                        String baseDir = servletDir.substring(0, last);

                        String basepath = xsl.substring(0, xsl.indexOf(File.separator));
                        String firstStage = baseDir + File.separator + basepath + File.separator + "karuta" + File.separator + "xsl" + File.separator + "html2xml.xsl";
                        //TODO should be done on an other way !
                        logger.info("FIRST: {}", firstStage);

                        /// Storing transformed data
                        StringWriter dataTransformed = new StringWriter();

                        /// Apply change
                        Source xsltSrc1 = new StreamSource(new File(firstStage));
                        TransformerFactory transFactory = TransformerFactory.newInstance();
                        Transformer transformer1 = transFactory.newTransformer(xsltSrc1);
                        StreamSource stageSource = new StreamSource(new ByteArrayInputStream(sakaiData.getBytes()));
                        Result stageRes = new StreamResult(dataTransformed);
                        transformer1.transform(stageSource, stageRes);

                        /// Result as portfolio data to be imported
                        xmlPortfolio = dataTransformed.toString();
                    } catch (IOException | TransformerException e) {
                        logger.error("Managed error", e);
                    }
                }
            }
        }

        Connection c = null;
        try {
            boolean instantiate = BooleanUtils.toBoolean(instance);

            c = SqlUtils.getConnection();

            return dataProvider.postPortfolio(c, new MimeType("text/xml"), new MimeType("text/xml"), xmlPortfolio, ui.userId, groupId, modelId, ui.subId,
                    instantiate, projectName).toString();
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(ex.getStatus(), ex.getCustomMessage());
        } catch (Exception ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Return a list of portfolio shared to a user
     * GET /portfolios/shared/{userid}
     * parameters:
     * return:
     **/
    @Path("/portfolios/shared/{userid}")
    @POST
    @Produces(MediaType.APPLICATION_XML)
    public Response getPortfolioShared(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,
                                       @Context HttpServletRequest httpServletRequest, @PathParam("userid") int userid) {
        UserInfo uinfo = checkCredential(httpServletRequest, user, token, null);

        Connection c = null;
        try {
            c = SqlUtils.getConnection();
            if (credential.isAdmin(c, uinfo.userId)) {
                String res = dataProvider.getPortfolioShared(c, uinfo.userId, userid);
                return Response.ok(res).build();
            } else {
                return Response.status(403).build();
            }
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.FORBIDDEN, ex.getMessage());
        } catch (Exception ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }


    // GET /portfolios/zip ? portfolio={}, toujours avec files
    // zip séparés
    // zip des zip

    /**
     * Fetching multiple portfolio in a zip
     * GET /rest/api/portfolios
     * parameters:
     * portfolio: separated with ','
     * return:
     * zipped portfolio (with files) inside zip file
     **/
    @Path("/portfolios/zip")
    @GET
    @Consumes("application/zip")    // Envoie donn�e brut
    public Object getPortfolioZip(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("portfolios") String portfolioList,
                                  @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest, @QueryParam("user") Integer userId,
                                  @QueryParam("model") String modelId, @QueryParam("instance") String instance, @QueryParam("lang") String lang,
                                  @QueryParam("archive") String archive) {
        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        try {
            File archiveFolder = null;
            boolean doArchive = false;
            if (BooleanUtils.toBoolean(archive)) {
                /// Check if folder exists
                archiveFolder = new File(archivePath);
                if (!archiveFolder.exists())
                    archiveFolder.mkdirs();
                doArchive = true;
            }

            HttpSession session = httpServletRequest.getSession(false);
            String[] list = portfolioList.split(",");
            File[] files = new File[list.length];

            c = SqlUtils.getConnection();

            /// Suppose the first portfolio has the right name to be used

            String name = "";

            /// Create all the zip files
            for (int i = 0; i < list.length; ++i) {
                String portfolioUuid = list[i];
                String portfolio = dataProvider.getPortfolio(c, new MimeType("text/xml"), portfolioUuid, ui.userId, 0, this.label, "true", "",
                        ui.subId, null).toString();

                // No name yet
                if ("".equals(name)) {
                    StringBuffer outTrace = new StringBuffer();
                    Document doc = DomUtils.xmlString2Document(portfolio, outTrace);
                    XPath xPath = XPathFactory.newInstance().newXPath();
                    String filterRes = "//*[local-name()='asmRoot']/*[local-name()='asmResource']/*[local-name()='code']";
                    NodeList nodelist = (NodeList) xPath.compile(filterRes).evaluate(doc, XPathConstants.NODESET);

                    if (nodelist.getLength() > 0)
                        name = nodelist.item(0).getTextContent();
                }

                Document doc = DomUtils.xmlString2Document(portfolio, new StringBuffer());

                files[i] = getZipFile(portfolioUuid, portfolio, lang, doc, session);

            }

            // Make a big zip of it
            String timeFormat = DT.format(new Date());

            File tempDir = new File(tempdir);
            if (!tempDir.isDirectory())
                tempDir.mkdirs();
            File bigZip = File.createTempFile("project_" + timeFormat, ".zip", tempDir);

            // Add content to it
            FileOutputStream fos = new FileOutputStream(bigZip);
            ZipOutputStream zos = new ZipOutputStream(fos);

            byte[] buffer = new byte[0x1000];

            for (File file : files) {
                FileInputStream fis = new FileInputStream(file);
                String filename = file.getName();

                /// Write xml file to zip
                ZipEntry ze = new ZipEntry(filename + ".zip");
                zos.putNextEntry(ze);
                int read = 1;
                while (read > 0) {
                    read = fis.read(buffer);
                    zos.write(buffer);
                }
                zos.closeEntry();
            }
            zos.close();

            // Delete all zipped file
            for (File file : files) file.delete();

            Response response;
            if (doArchive)    // Keep bigzip inside archive folder
            {
                String filename = name + "-" + timeFormat + ".zip";
                File archiveFile = new File(archiveFolder.getAbsolutePath() + File.separator + filename);
                archiveFile.createNewFile();
                bigZip.renameTo(archiveFile);    // Return filename generated
                response = Response
                        .ok(filename)
                        .build();
            } else    // Return to browser
            {
                /// Return zip file
                RandomAccessFile f = new RandomAccessFile(bigZip.getAbsoluteFile(), "r");
                byte[] b = new byte[(int) f.length()];
                f.read(b);
                f.close();

                response = Response
                        .ok(b, MediaType.APPLICATION_OCTET_STREAM)
                        .header("content-disposition", "attachment; filename = \"" + name + "-" + timeFormat + ".zip\"")
                        .build();

            }
            // Delete over-arching zip
            bigZip.delete();
            return response;
        } catch (Exception e) {
            logger.error("Managed error", e);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, e.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }


    /**
     * As a form, import zip, extract data and put everything into the database
     * POST /rest/api/portfolios
     * parameters:
     * zip: From a zip export of the system
     * return:
     * portfolio uuid
     **/
    @Path("/portfolios/zip")
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
//	@Consumes("application/zip")	// Envoie donn�e brut
    public String postPortfolioZip(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest, @FormDataParam("fileupload") InputStream fileInputStream, @QueryParam("user") Integer userId, @QueryParam("model") String modelId, @FormDataParam("instance") String instance, @FormDataParam("project") String projectName) {
        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        try {
            boolean instantiate = Boolean.parseBoolean(instance);
            c = SqlUtils.getConnection();
            return dataProvider.postPortfolioZip(c, new MimeType("text/xml"), new MimeType("text/xml"), httpServletRequest, fileInputStream, ui.userId, groupId,
                    modelId, ui.subId, instantiate, projectName).toString();
        } catch (RestWebApplicationException e) {
            throw e;
        } catch (Exception ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    
    /**
     * Delete portfolio
     * DELETE /rest/api/portfolios/portfolio/{portfolio-id}
     * parameters:
     * return:
     **/
    @Path("/portfolios/portfolio/{portfolio-id}")
    @DELETE
    @Produces(MediaType.APPLICATION_XML)
    public String deletePortfolio(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId,
                                  @PathParam("portfolio-id") String portfolioUuid, @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest,
                                  @QueryParam("user") Integer userId) {
        if (!isUUID(portfolioUuid)) {
            logger.error("isUUID({})", portfolioUuid);
            throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
        }

        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        try {
        	c = SqlUtils.getConnection();
        		// Get file list in portfolio
        		HttpSession session = httpServletRequest.getSession(true);
        		ArrayList<Pair<String, String>> filelist = dataProvider.getPortfolioUniqueFile(c, portfolioUuid, ui.userId);
        	  
        	  // Loop through and delete
            final String sessionval = session.getId();
            try (CloseableHttpClient httpclient = HttpClients.createDefault())
            {
	            HttpDelete del = new HttpDelete();
	            del.setHeader("Cookie", "JSESSIONID=" + sessionval);    // So that the receiving servlet allow us
	        		for( Pair<String, String> item : filelist )
	        	  {
		              String url = backend + "/resources/resource/file/" + item.getLeft() + "?lang=" + item.getRight();
		              del.setURI(new URI(url));
		              
		              CloseableHttpResponse response = httpclient.execute(del);
		              
		              del.reset();	// Prepare re-use
              }
        	  }
        	  
        	  // Delete portfolio content
            int nbPortfolioDeleted = Integer.parseInt(dataProvider.deletePortfolio(c, portfolioUuid, ui.userId, groupId).toString());
            if (nbPortfolioDeleted == 0) {
                throw new RestWebApplicationException(Status.NOT_FOUND, "Portfolio " + portfolioUuid + " not found");
            }
            return "";

        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.NOT_FOUND, "Portfolio " + portfolioUuid + " not found");
        } catch (Exception ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Get a node, without children
     * FIXME: Check if it's the case
     * GET /rest/api/nodes/node/{node-id}
     * parameters:
     * return:
     * nodes in the ASM format
     **/
    @Path("/nodes/node/{node-id}")
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Consumes(MediaType.APPLICATION_XML)
    public String getNode(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @QueryParam("userrole") String userrole, @PathParam("node-id") String nodeUuid,
                          @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept, @QueryParam("user") Integer userId,
                          @QueryParam("level") Integer cutoff) {
        if (!isUUID(nodeUuid)) {
            logger.error("isUUID({}) is false", nodeUuid);
            throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
        }

        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        try {
            c = SqlUtils.getConnection();
            String returnValue = dataProvider.getNode(c, new MimeType("text/xml"), nodeUuid, false, ui.userId, groupId, userrole, this.label, cutoff).toString();
            if (returnValue == null)
                throw new RestWebApplicationException(Status.NOT_FOUND, "Node " + nodeUuid + " not found");
            if (returnValue.length() != 0) {
                if (accept.equals(MediaType.APPLICATION_JSON))
                    returnValue = XML.toJSONObject(returnValue).toString();

            } else {
                throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
            }


            return returnValue;
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
        } catch (SQLException ex) {
            logger.error("SQLException error", ex);
            throw new RestWebApplicationException(Status.NOT_FOUND, "Node " + nodeUuid + " not found");
        } catch (NullPointerException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.NOT_FOUND, "Node " + nodeUuid + " not found");
        } catch (Exception ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Fetch nodes and childrens from node uuid
     * GET /rest/api/nodes/node/{node-id}/children
     * parameters:
     * return:
     * nodes in the ASM format
     **/
    @Path("/nodes/node/{node-id}/children")
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Consumes(MediaType.APPLICATION_XML)
    public String getNodeWithChildren(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @QueryParam("userrole") String userrole, 
                                      @PathParam("node-id") String nodeUuid, @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest,
                                      @HeaderParam("Accept") String accept, @QueryParam("user") Integer userId, @QueryParam("level") Integer cutoff) {
        if (!isUUID(nodeUuid)) {
            logger.error("isUUID({}) is false", nodeUuid);
            throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
        }

        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        try {
            c = SqlUtils.getConnection();
            String returnValue = dataProvider.getNode(c, new MimeType("text/xml"), nodeUuid, true, ui.userId, groupId, userrole, this.label, cutoff).toString();
            if (returnValue == null)
                throw new RestWebApplicationException(Status.NOT_FOUND, "Node " + nodeUuid + " not found");
            if (returnValue.length() != 0) {
                if (accept.equals(MediaType.APPLICATION_JSON))
                    returnValue = XML.toJSONObject(returnValue).toString();
                return returnValue;
            } else {
                throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
            }
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
        } catch (SQLException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.NOT_FOUND, "Node " + nodeUuid + " not found");
        } catch (Exception ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Fetch nodes metdata
     * GET /rest/api/nodes/node/{node-id}/metadatawad
     * parameters:
     * return:
     * <metadata-wad/>
     **/
    @Path("/nodes/node/{nodeid}/metadatawad")
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Consumes(MediaType.APPLICATION_XML)
    public String getNodeMetadataWad(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @QueryParam("userrole") String userrole, 
                                     @PathParam("nodeid") String nodeUuid, @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest,
                                     @HeaderParam("Accept") String accept, @QueryParam("user") Integer userId) {
        if (!isUUID(nodeUuid)) {
            logger.error("isUUID({}) is false", nodeUuid);
            throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
        }

        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        try {
            c = SqlUtils.getConnection();
            String returnValue = dataProvider.getNodeMetadataWad(c, new MimeType("text/xml"), nodeUuid, true, ui.userId, groupId, userrole, this.label).toString();
            if (returnValue.length() != 0) {
                if (accept.equals(MediaType.APPLICATION_JSON))
                    returnValue = XML.toJSONObject(returnValue).toString();


                return returnValue;
            } else {
                throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
            }
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
        } catch (Exception ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Fetch rights per role for a node
     * GET /rest/api/nodes/node/{node-id}/rights
     * parameters:
     * return:
     * <node uuid="">
     * <role name="">
     * <right RD="" WR="" DL="" />
     * </role>
     * </node>
     **/
    @Path("/nodes/node/{node-id}/rights")
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Consumes(MediaType.APPLICATION_XML)
    public String getNodeRights(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("node-id") String nodeUuid,
                                @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept, @QueryParam("user") Integer userId) {
        if (!isUUID(nodeUuid)) {
            logger.error("isUUID({}) is false", nodeUuid);
            throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
        }

        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        try {
            c = SqlUtils.getConnection();
            String returnValue = dataProvider.getNodeRights(c, nodeUuid, ui.userId, groupId);
            if (returnValue.length() != 0) {
                if (accept.equals(MediaType.APPLICATION_JSON))
                    returnValue = XML.toJSONObject(returnValue).toString();

            } else {
                throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
            }

            return returnValue;
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
        } catch (SQLException ex) {
            logger.error("SQLException error", ex);
            throw new RestWebApplicationException(Status.NOT_FOUND, "Node " + nodeUuid + " not found");
        } catch (NullPointerException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.NOT_FOUND, "Node " + nodeUuid + " not found");
        } catch (Exception ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Fetch portfolio id from a given node id
     * GET /rest/api/nodes/node/{node-id}/portfolioid
     * parameters:
     * return:
     * portfolioid
     **/
    @Path("/nodes/node/{node-id}/portfolioid")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String getNodePortfolioId(@CookieParam("user") String user, @CookieParam("credential") String token, @PathParam("node-id") String nodeUuid, @Context ServletConfig sc,
                                     @Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept) {
        if (!isUUID(nodeUuid)) {
            logger.error("isUUID({}) is false", nodeUuid);
            throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
        }

        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        try {
            c = SqlUtils.getConnection();
            // Admin, or if user has a right to read can fetch this information
            if (!credential.isAdmin(c, ui.userId) && !credential.hasNodeRight(c, ui.userId, 0, nodeUuid, Credential.READ)) {
                throw new RestWebApplicationException(Status.FORBIDDEN, "No rights");
            }

            String returnValue = dataProvider.getNodePortfolioId(c, nodeUuid);
            if (returnValue.length() != 0) {
                return returnValue;
            } else {
                throw new RestWebApplicationException(Status.NOT_FOUND, "Error, shouldn't happen.");
            }
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(ex.getStatus(), ex.getResponse().getEntity().toString());
        } catch (SQLException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.NOT_FOUND, "Node " + nodeUuid + " not found");
        } catch (Exception ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Change nodes right
     * POST /rest/api/nodes/node/{node-id}/rights
     * parameters:
     * content:
     * <node uuid="">
     * <role name="">
     * <right RD="" WR="" DL="" />
     * </role>
     * </node>
     * <p>
     * return:
     **/
    @Path("/nodes/node/{node-id}/rights")
    @POST
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Consumes(MediaType.APPLICATION_XML)
    public String postNodeRights(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId,
                                 @PathParam("node-id") String nodeUuid, @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest,
                                 @HeaderParam("Accept") String accept, @QueryParam("user") Integer userId) {
        if (!isUUID(nodeUuid)) {
            logger.error("isUUID({}) is false", nodeUuid);
            throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
        }

        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        try {
            DocumentBuilderFactory documentBuilderFactory = DomUtils.newSecureDocumentBuilderFactory();
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            Document doc = documentBuilder.parse(new ByteArrayInputStream(xmlNode.getBytes(StandardCharsets.UTF_8)));

            XPath xPath = XPathFactory.newInstance().newXPath();
//			String xpathRole = "//role";
            String xpathRole = "//*[local-name()='role']";
            XPathExpression findRole = xPath.compile(xpathRole);
            NodeList roles = (NodeList) findRole.evaluate(doc, XPathConstants.NODESET);

            c = SqlUtils.getConnection();

            /// For all roles we have to change
            for (int i = 0; i < roles.getLength(); ++i) {
                Node rolenode = roles.item(i);
                String rolename = rolenode.getAttributes().getNamedItem("name").getNodeValue();
                Node right = rolenode.getFirstChild();

                //
                /// username as role

                if ("#text".equals(right.getNodeName()))
                    right = right.getNextSibling();

                if ("right".equals(right.getNodeName()))    // Changing node rights
                {
                    NamedNodeMap rights = right.getAttributes();

                    NodeRight noderight = new NodeRight(null, null, null, null, null, null);

                    String val = rights.getNamedItem("RD").getNodeValue();
                    if (val != null)
                        noderight.read = "Y".equals(val);
                    val = rights.getNamedItem("WR").getNodeValue();
                    if (val != null)
                        noderight.write = "Y".equals(val);
                    val = rights.getNamedItem("DL").getNodeValue();
                    if (val != null)
                        noderight.delete = "Y".equals(val);
                    val = rights.getNamedItem("SB").getNodeValue();
                    if (val != null)
                        noderight.submit = "Y".equals(val);

                    // change right
                    dataProvider.postRights(c, ui.userId, nodeUuid, rolename, noderight);
                } else if ("action".equals(right.getNodeName()))    // Using an action on node
                {
                    // reset right
                    dataProvider.postMacroOnNode(c, ui.userId, nodeUuid, "reset");
                }
            }


        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
        } catch (NullPointerException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.NOT_FOUND, "Node " + nodeUuid + " not found");
        } catch (Exception ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }

        return "";
    }

    /**
     * Get the single first semantic tag node inside specified portfolio
     * GET /rest/api/nodes/firstbysemantictag/{portfolio-uuid}/{semantictag}
     * parameters:
     * return:
     * node in ASM format
     **/
    @Path("/nodes/firstbysemantictag/{portfolio-uuid}/{semantictag}")
    @GET
    @Produces({MediaType.APPLICATION_XML})
    @Consumes(MediaType.APPLICATION_XML)
    public String getNodeBySemanticTag(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @QueryParam("userrole") String userrole, 
                                       @PathParam("portfolio-uuid") String portfolioUuid, @PathParam("semantictag") String semantictag, @Context ServletConfig sc,
                                       @Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept) {
        if (!isUUID(portfolioUuid)) {
            logger.error("isUUID({}) is false", portfolioUuid);
            throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
        }

        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        try {
            c = SqlUtils.getConnection();
            String returnValue = dataProvider.getNodeBySemanticTag(c, new MimeType("text/xml"), portfolioUuid, semantictag, ui.userId, groupId, userrole).toString();
            if (returnValue.length() != 0) {
                return returnValue;
            } else {
                throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
            }
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
        } catch (Exception ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Get multiple semantic tag nodes inside specified portfolio
     * GET /rest/api/nodes/nodes/bysemantictag/{portfolio-uuid}/{semantictag}
     * parameters:
     * return:
     * nodes in ASM format
     **/
    @Path("/nodes/bysemantictag/{portfolio-uuid}/{semantictag}")
    @GET
    @Produces({MediaType.APPLICATION_XML})
    @Consumes(MediaType.APPLICATION_XML)
    public String getNodesBySemanticTag(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId,
                                        @PathParam("portfolio-uuid") String portfolioUuid, @PathParam("semantictag") String semantictag, @Context ServletConfig sc,
                                        @Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept) {
        if (!isUUID(portfolioUuid)) {
            logger.error("isUUID({}) is false", portfolioUuid);
            throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
        }

        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        try {
            c = SqlUtils.getConnection();
            String returnValue = dataProvider.getNodesBySemanticTag(c, new MimeType("text/xml"), ui.userId, groupId, portfolioUuid, semantictag).toString();
            if (returnValue.length() != 0) {
                return returnValue;
            } else {
                throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
            }
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
        } catch (Exception ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Rewrite node
     * PUT /rest/api/nodes/node/{node-id}
     * parameters:
     * return:
     **/
    @Path("/nodes/node/{node-id}")
    @PUT
    @Produces(MediaType.APPLICATION_XML)
    public String putNode(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId,
                          @PathParam("node-id") String nodeUuid, @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest, @QueryParam("user") Integer userId) {
        if (!isUUID(nodeUuid)) {
            logger.error("isUUID({}) is false", nodeUuid);
            throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
        }

        //		long t_startRest = System.nanoTime();
        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

//		long t_checkCred = System.nanoTime();

        try {
            c = SqlUtils.getConnection();
            String returnValue = dataProvider.putNode(c, new MimeType("text/xml"), nodeUuid, xmlNode, ui.userId, groupId).toString();

            if (returnValue.equals(MysqlDataProvider.DATABASE_FALSE)) {
                throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
            }

            return returnValue;
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
        } catch (SQLException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.NOT_FOUND, "Node " + nodeUuid + " not found");
        } catch (Exception ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Rewrite node metadata
     * PUT /rest/api/nodes/node/{node-id}/metadata
     * parameters:
     * return:
     **/
    @Path("/nodes/node/{nodeid}/metadata")
    @PUT
    @Produces(MediaType.APPLICATION_XML)
    public String putNodeMetadata(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId,
                                  @QueryParam("info") String info, @PathParam("nodeid") String nodeUuid, @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest) {
        if (!isUUID(nodeUuid)) {
            logger.error("isUUID({}) is false", nodeUuid);
            throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
        }

        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;
        Date time = new Date();
        SimpleDateFormat dt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S");
        String timeFormat = dt.format(time);
        String logformat = logFormat;
        if (BooleanUtils.isFalse(BooleanUtils.toBooleanObject(info)))
            logformat = logFormatShort;

        try {
            c = SqlUtils.getConnection();
            final String returnValue = dataProvider.putNodeMetadata(c, new MimeType("text/xml"), nodeUuid, xmlNode, ui.userId, groupId).toString();

            if (MysqlDataProvider.DATABASE_FALSE.equals(returnValue)) {
                errorLog.error(String.format(logformat, "ERR", nodeUuid, "metadata", ui.userId, timeFormat, httpServletRequest.getRemoteAddr(), xmlNode));
                editLog.error(String.format(logformat, "ERR", nodeUuid, "metadata", ui.userId, timeFormat, httpServletRequest.getRemoteAddr(), xmlNode));
                throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
            }

            editLog.info(String.format(logformat, "OK", nodeUuid, "metadata", ui.userId, timeFormat, httpServletRequest.getRemoteAddr(), xmlNode));
            return returnValue;
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
        } catch (SQLException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.NOT_FOUND, "Node " + nodeUuid + " not found");
        } catch (Exception ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Rewrite node wad metadata
     * PUT /rest/api/nodes/node/{node-id}/metadatawas
     * parameters:
     * return:
     **/
    @Path("/nodes/node/{nodeid}/metadatawad")
    @PUT
    @Produces(MediaType.APPLICATION_XML)
    public String putNodeMetadataWad(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId,
                                     @QueryParam("info") String info, @PathParam("nodeid") String nodeUuid, @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest) {
        if (!isUUID(nodeUuid)) {
            logger.error("isUUID({}) is false", nodeUuid);
            throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
        }

        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;
        String timeFormat = DT.format(new Date());
        String logformat = logFormat;
        if (BooleanUtils.isFalse(BooleanUtils.toBooleanObject(info)))
            logformat = logFormatShort;


        try {
            c = SqlUtils.getConnection();
            final String returnValue = dataProvider.putNodeMetadataWad(c, new MimeType("text/xml"), nodeUuid, xmlNode, ui.userId, groupId).toString();
            if (MysqlDataProvider.DATABASE_FALSE.equals(returnValue)) {
                errorLog.error(String.format(logformat, "ERR", nodeUuid, "metadatawad", ui.userId, timeFormat, httpServletRequest.getRemoteAddr(), xmlNode));
                editLog.info(String.format(logformat, "ERR", nodeUuid, "metadatawad", ui.userId, timeFormat, httpServletRequest.getRemoteAddr(), xmlNode));
                throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
            }

            editLog.info(String.format(logformat, "OK", nodeUuid, "metadatawad", ui.userId, timeFormat, httpServletRequest.getRemoteAddr(), xmlNode));

            return returnValue;
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
        } catch (SQLException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.NOT_FOUND, "Node " + nodeUuid + " not found");
        } catch (Exception ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Rewrite node epm metadata
     * PUT /rest/api/nodes/node/{node-id}/metadataepm
     * parameters:
     * return:
     **/
    @Path("/nodes/node/{nodeid}/metadataepm")
    @PUT
    @Produces(MediaType.APPLICATION_XML)
    public String putNodeMetadataEpm(String xmlNode, @PathParam("nodeid") String nodeUuid, @QueryParam("group") int groupId, @QueryParam("info") String info,
                                     @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest) {
        if (!isUUID(nodeUuid)) {
            logger.error("isUUID({}) is false", nodeUuid);
            throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
        }

        UserInfo ui = checkCredential(httpServletRequest, null, null, null);
        Connection c = null;
        String timeFormat = DT.format(new Date());
        String logformat = logFormat;
        if (BooleanUtils.isFalse(BooleanUtils.toBooleanObject(info)))
            logformat = logFormatShort;

        try {
            c = SqlUtils.getConnection();
            final String returnValue = dataProvider.putNodeMetadataEpm(c, new MimeType("text/xml"), nodeUuid, xmlNode, ui.userId, groupId).toString();
            if (MysqlDataProvider.DATABASE_FALSE.equals(returnValue)) {
                errorLog.error(String.format(logformat, "ERR", nodeUuid, "metadataepm", ui.userId, timeFormat, httpServletRequest.getRemoteAddr(), xmlNode));
                editLog.info(String.format(logformat, "ERR", nodeUuid, "metadataepm", ui.userId, timeFormat, httpServletRequest.getRemoteAddr(), xmlNode));
                throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
            }
            if ("erreur".equals(returnValue)) {
                errorLog.error(String.format(logformat, "NMOD", nodeUuid, "metadataepm", ui.userId, timeFormat, httpServletRequest.getRemoteAddr(), xmlNode));
                editLog.info(String.format(logformat, "NMOD", nodeUuid, "metadataepm", ui.userId, timeFormat, httpServletRequest.getRemoteAddr(), xmlNode));
                throw new RestWebApplicationException(Status.NOT_MODIFIED, "Erreur");
            }

            editLog.info(String.format(logformat, "OK", nodeUuid, "metadataepm", ui.userId, timeFormat, httpServletRequest.getRemoteAddr(), xmlNode));


            return returnValue;
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
        } catch (SQLException ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.NOT_FOUND, "Node " + nodeUuid + " not found");
        } catch (Exception ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Rewrite node nodecontext
     * PUT /rest/api/nodes/node/{node-id}/nodecontext
     * parameters:
     * return:
     **/
    @Path("/nodes/node/{nodeid}/nodecontext")
    @PUT
    @Produces(MediaType.APPLICATION_XML)
    public String putNodeNodeContext(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId,
                                     @QueryParam("info") String info, @PathParam("nodeid") String nodeUuid, @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest) {
        if (!isUUID(nodeUuid)) {
            logger.error("isUUID({}) is false", nodeUuid);
            throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
        }

        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;
        String timeFormat = DT.format(new Date());
        String logformat = logFormat;
        if (BooleanUtils.isFalse(BooleanUtils.toBooleanObject(info)))
            logformat = logFormatShort;

        try {
            c = SqlUtils.getConnection();
            String returnValue = dataProvider.putNodeNodeContext(c, new MimeType("text/xml"), nodeUuid, xmlNode, ui.userId, groupId).toString();
            if (MysqlDataProvider.DATABASE_FALSE.equals(returnValue)) {
                errorLog.error(String.format(logformat, "ERR", nodeUuid, "nodecontext", ui.userId, timeFormat, httpServletRequest.getRemoteAddr(), xmlNode));
                editLog.info(String.format(logformat, "ERR", nodeUuid, "nodecontext", ui.userId, timeFormat, httpServletRequest.getRemoteAddr(), xmlNode));
                throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
            }

            if (editLog != null) {
                editLog.info(String.format(logformat, "OK", nodeUuid, "nodecontext", ui.userId, timeFormat, httpServletRequest.getRemoteAddr(), xmlNode));
            }

            return returnValue;
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
        } catch (Exception ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Rewrite node resource
     * PUT /rest/api/nodes/node/{node-id}/noderesource
     * parameters:
     * return:
     **/
    @Path("/nodes/node/{nodeid}/noderesource")
    @PUT
    @Produces(MediaType.APPLICATION_XML)
    public String putNodeNodeResource(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId,
                                      @QueryParam("info") String info, @PathParam("nodeid") String nodeUuid, @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest) {
        if (!isUUID(nodeUuid)) {
            logger.error("isUUID({}) is false", nodeUuid);
            throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
        }

        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;
        String timeFormat = DT.format(new Date());
        String logformat = logFormat;
        if (BooleanUtils.isFalse(BooleanUtils.toBooleanObject(info)))
            logformat = logFormatShort;

        try {
            /// Branchement pour l'interprétation du contenu, besoin de vérifier les limitations ?
            //xmlNode = xmlNode.getBytes(StandardCharsets.UTF_8).toString();
            /// putNode(MimeType inMimeType, String nodeUuid, String in,int userId, int groupId)
            //          String returnValue = dataProvider.putNode(new MimeType("text/xml"),nodeUuid,xmlNode,this.userId,this.groupId).toString();
            c = SqlUtils.getConnection();
            String returnValue = dataProvider.putNodeNodeResource(c, new MimeType("text/xml"), nodeUuid, xmlNode, ui.userId, groupId).toString();
            if (MysqlDataProvider.DATABASE_FALSE.equals(returnValue)) {
                errorLog.error(String.format(logformat, "ERR", nodeUuid, "noderesource", ui.userId, timeFormat, httpServletRequest.getRemoteAddr(), xmlNode));
                editLog.info(String.format(logformat, "ERR", nodeUuid, "noderesource", ui.userId, timeFormat, httpServletRequest.getRemoteAddr(), xmlNode));
                throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
            }


            editLog.info(String.format(logformat, "OK", nodeUuid, "noderesource", ui.userId, timeFormat, httpServletRequest.getRemoteAddr(), xmlNode));

            return returnValue;
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw ex;
        } catch (Exception ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Instanciate a node with right parsing
     * POST /rest/api/nodes/node/import/{dest-id}
     * parameters:
     * return:
     **/
    @Path("/nodes/node/import/{dest-id}")
    @POST
    public String postImportNode(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId,
                                 @PathParam("dest-id") String parentId, @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest,
                                 @QueryParam("srcetag") String semtag, @QueryParam("srcecode") String code, @QueryParam("uuid") String srcuuid) {
		/*
		if( !isUUID(srcuuid) || !isUUID(parentId) )
		{
		logger.error("isUUID({}) or isUUID({}) is false", srcuuid, parentId);
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}
		//*/

        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        try {
            if (ui.userId == 0)
                throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'etes pas connecte");

            c = SqlUtils.getConnection();
            String returnValue = dataProvider.postImportNode(c, new MimeType("text/xml"), parentId, semtag, code, srcuuid, ui.userId, groupId).toString();


            if (MysqlDataProvider.DATABASE_FALSE.equals(returnValue)) {
                throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
            }

            return returnValue;
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
        } catch (Exception ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Raw copy a node
     * POST /rest/api/nodes/node/copy/{dest-id}
     * parameters:
     * return:
     **/
    @Path("/nodes/node/copy/{dest-id}")
    @POST
    public String postCopyNode(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId,
                               @PathParam("dest-id") String parentId, @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest,
                               @QueryParam("srcetag") String semtag, @QueryParam("srcecode") String code, @QueryParam("uuid") String srcuuid) {
		/*
		if( !isUUID(srcuuid) || !isUUID(parentId) )
		{
		logger.error("isUUID({}) or isUUID({}) is false", srcuuid, parentId);
			throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
		}
		//*/

        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        //// TODO: IF user is creator and has parameter owner -> change ownership

        try {
            if (ui.userId == 0)
                throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'etes pas connecte");

            c = SqlUtils.getConnection();
            String returnValue = dataProvider.postCopyNode(c, new MimeType("text/xml"), parentId, semtag, code, srcuuid, ui.userId, groupId).toString();


            if (MysqlDataProvider.DATABASE_FALSE.equals(returnValue)) {
                throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
            } else if ("Selection non existante.".equals(returnValue)) {
                throw new RestWebApplicationException(Status.NOT_FOUND, "Selection non existante.");
            }

            return returnValue;
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
        } catch (Exception ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Fetch nodes right
     * GET /rest/api/nodes
     * parameters:
     * - portfoliocode: mandatory
     * - semtag_parent, code_parent: From a code_parent, find the children that have semtag_parent
     * - semtag:	mandatory, find the semtag under portfoliocode, or the selection from semtag_parent/code_parent
     * return:
     **/
    @Path("/nodes")
    @GET
    @Produces({MediaType.APPLICATION_XML})
    @Consumes(MediaType.APPLICATION_XML)
    public String getNodes(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @QueryParam("userrole") String userrole, 
                           @PathParam("dest-id") String parentId, @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest,
                           @QueryParam("portfoliocode") String portfoliocode, @QueryParam("semtag") String semtag, @QueryParam("semtag_parent") String semtag_parent,
                           @QueryParam("code_parent") String code_parent, @QueryParam("level") Integer cutoff) {
        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        try {
            c = SqlUtils.getConnection();
            String returnValue = dataProvider.getNodes(c, new MimeType("text/xml"), portfoliocode, semtag, ui.userId, groupId, userrole, semtag_parent, code_parent, cutoff).toString();


            if (MysqlDataProvider.DATABASE_FALSE.equals(returnValue)) {
                throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
            } else if ("".equals(returnValue)) {
                throw new RestWebApplicationException(Status.NOT_FOUND, "Portfolio inexistant");
            }

            return returnValue;
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw ex;
        } catch (Exception ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Insert XML in a node. Moslty used by admin, other people use the import/copy node
     * POST /rest/api/nodes/node/{parent-id}
     * parameters:
     * return:
     **/
    @Path("/nodes/node/{parent-id}")
    @POST
    @Consumes(MediaType.APPLICATION_XML)
    @Produces(MediaType.APPLICATION_XML)
    public Response postNode(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") Integer group,
                             @PathParam("parent-id") String parentId, @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest,
                             @QueryParam("user") Integer userId, @QueryParam("group") int groupId) {
        if (!isUUID(parentId)) {
            logger.error("isUUID({})  is false", parentId);
            throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
        }

        UserInfo ui = checkCredential(httpServletRequest, user, token, null);

        KEvent event = new KEvent();
        event.requestType = KEvent.RequestType.POST;
        event.eventType = KEvent.EventType.NODE;
        event.uuid = parentId;
        event.inputData = xmlNode;

        Connection c = null;
        try {
            c = SqlUtils.getConnection();
            if (ui.userId == 0) {
                return Response.status(403).entity("Not logged in").build();
            } else // if( dataProvider.isAdmin(c, Integer.toString(ui.userId)) )
            {
                String returnValue = dataProvider.postNode(c, new MimeType("text/xml"), parentId, xmlNode, ui.userId, groupId, false).toString();


                Response response;
                if (MysqlDataProvider.DATABASE_FALSE.equals(returnValue)) {
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
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
        } catch (Exception ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Move a node up between siblings
     * POST /rest/api/nodes/node/{node-id}/moveup
     * parameters:
     * return:
     **/
    @Path("/nodes/node/{node-id}/moveup")
    @POST
    @Consumes(MediaType.APPLICATION_XML)
    @Produces(MediaType.APPLICATION_XML)
    public Response postMoveNodeUp(String xmlNode, @PathParam("node-id") String nodeId, @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest) {
        if (!isUUID(nodeId)) {
            logger.error("isUUID({})  is false", nodeId);
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

        try {
            if (nodeId == null) {
                response = Response.status(400).entity("Missing uuid").build();
            } else {
                c = SqlUtils.getConnection();
                int returnValue = dataProvider.postMoveNodeUp(c, ui.userId, nodeId);


                if (returnValue == -1) {
                    response = Response.status(404).entity("Non-existing node").build();
                }
                if (returnValue == -2) {
                    response = Response.status(409).entity("Cannot move first node").build();
                } else {
                    response = Response.status(204).build();
                }
            }
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
        } catch (Exception ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }

        return response;
    }

    /**
     * Move a node to another parent
     * POST /rest/api/nodes/node/{node-id}/parentof/{parent-id}
     * parameters:
     * return:
     **/
    @Path("/nodes/node/{node-id}/parentof/{parent-id}")
    @POST
    @Consumes(MediaType.APPLICATION_XML)
    @Produces(MediaType.APPLICATION_XML)
    public Response postChangeNodeParent(String xmlNode, @PathParam("node-id") String nodeId, @PathParam("parent-id") String parentId, @Context ServletConfig sc,
                                         @Context HttpServletRequest httpServletRequest) {
        if (!isUUID(nodeId) || !isUUID(parentId)) {
            logger.error("isUUID({}) or isUUID({})  is false", nodeId, parentId);
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

        try {
            c = SqlUtils.getConnection();
            boolean returnValue = dataProvider.postChangeNodeParent(c, ui.userId, nodeId, parentId);


            Response response;
            if (!returnValue) {
                response = Response.status(409).entity("Cannot move").build();
            } else {
                response = Response.status(200).build();
            }

            return response;
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
        } catch (Exception ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Execute a macro command on a node, changing rights related
     * POST /rest/api/nodes/node/{node-id}/action/{action-name}
     * parameters:
     * return:
     **/
    @Path("/nodes/node/{node-id}/action/{action-name}")
    @POST
    @Consumes(MediaType.APPLICATION_XML)
    @Produces(MediaType.APPLICATION_XML)
    public String postActionNode(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId,
                                 @PathParam("node-id") String nodeId, @PathParam("action-name") String macro, @Context ServletConfig sc,
                                 @Context HttpServletRequest httpServletRequest, @QueryParam("user") Integer userId) {
        if (!isUUID(nodeId)) {
            logger.error("isUUID({})  is false", nodeId);
            throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
        }

        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        try {
            c = SqlUtils.getConnection();
            String returnValue = dataProvider.postMacroOnNode(c, ui.userId, nodeId, macro);


            if ("erreur".equals(returnValue)) {
                throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
            }

            return returnValue;
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
        } catch (Exception ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Delete a node
     * DELETE /rest/api/nodes/node/{node-uuid}
     * parameters:
     * return:
     **/
    @Path("/nodes/node/{node-uuid}")
    @DELETE
    @Produces(MediaType.APPLICATION_XML)
    public String deleteNode(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @QueryParam("userrole") String userrole, 
                             @PathParam("node-uuid") String nodeUuid, @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest, @QueryParam("user") Integer userId) {
        if (!isUUID(nodeUuid)) {
            logger.error("isUUID({})  is false", nodeUuid);
            throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
        }

        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        try {
            c = SqlUtils.getConnection();
            int nbDeletedNodes = Integer.parseInt(dataProvider.deleteNode(c, nodeUuid, ui.userId, groupId, userrole).toString());
            if (nbDeletedNodes == 0) {


                throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
            }


            return "";
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
        } catch (SQLException ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.NOT_FOUND, "Node " + nodeUuid + " not found");
        } catch (Exception ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /*
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

    /**
     * Fetch resource from node uuid
     * GET /rest/api/resources/resource/{node-parent-id}
     * parameters:
     * - portfoliocode: mandatory
     * - semtag_parent, code_parent: From a code_parent, find the children that have semtag_parent
     * - semtag:	mandatory, find the semtag under portfoliocode, or the selection from semtag_parent/code_parent
     * return:
     **/
    @Path("/resources/resource/{node-parent-id}")
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Consumes(MediaType.APPLICATION_XML)
    public String getResource(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId,
                              @PathParam("node-parent-id") String nodeParentUuid, @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest,
                              @HeaderParam("Accept") String accept, @QueryParam("user") Integer userId) {
        if (!isUUID(nodeParentUuid)) {
            logger.error("isUUID({})  is false", nodeParentUuid);
            throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
        }

        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        try {
            c = SqlUtils.getConnection();
            String returnValue = dataProvider.getResource(c, new MimeType("text/xml"), nodeParentUuid, ui.userId, groupId).toString();
            if (MysqlDataProvider.DATABASE_FALSE.equals(returnValue)) {

                throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
            }
            if (accept.equals(MediaType.APPLICATION_JSON))
                returnValue = XML.toJSONObject(returnValue).toString();


            return returnValue;
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
        } catch (SQLException ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.NOT_FOUND, "Resource " + nodeParentUuid + " not found");
        } catch (Exception ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Fetch all resource in a portfolio
     * TODO: is it used?
     * 	GET /rest/api/resources/portfolios/{portfolio-id}
     * 	parameters:
     * 	- portfolio-id
     * 	return:
     **/
    @Path("/resources/portfolios/{portfolio-id}")
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public String getResources(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId,
                               @PathParam("portfolio-id") String portfolioUuid, @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest,
                               @HeaderParam("Accept") String accept, @QueryParam("user") Integer userId) {
        if (!isUUID(portfolioUuid)) {
            logger.error("isUUID({})  is false", portfolioUuid);
            throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
        }

        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        try {
            c = SqlUtils.getConnection();
            String returnValue = dataProvider.getResources(c, new MimeType("text/xml"), portfolioUuid, ui.userId, groupId).toString();
            if (accept.equals(MediaType.APPLICATION_JSON))
                returnValue = XML.toJSONObject(returnValue).toString();


            return returnValue;
        } catch (Exception ex) {
            logger.error("Managed error", ex);
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Modify resource content
     * PUT /rest/api/resources/resource/{node-parent-uuid}
     * parameters:
     * return:
     **/
    @Path("/resources/resource/{node-parent-uuid}")
    @PUT
    @Produces(MediaType.APPLICATION_XML)
    public String putResource(String xmlResource, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId,
                              @QueryParam("info") String info, @PathParam("node-parent-uuid") String nodeParentUuid, @Context ServletConfig sc,
                              @Context HttpServletRequest httpServletRequest, @QueryParam("user") Integer userId) {
        if (!isUUID(nodeParentUuid)) {
            logger.error("isUUID({})  is false", nodeParentUuid);
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
        String timeFormat = DT.format(new Date());
        String logformat = logFormat;
        if (BooleanUtils.isFalse(BooleanUtils.toBooleanObject(info)))
            logformat = logFormatShort;
        try {
            c = SqlUtils.getConnection();
            String returnValue = dataProvider.putResource(c, new MimeType("text/xml"), nodeParentUuid, xmlResource, ui.userId, groupId).toString();

            editLog.info(String.format(logformat, "OK", nodeParentUuid, "resource", ui.userId, timeFormat, httpServletRequest.getRemoteAddr(), xmlResource));

//			eventbus.processEvent(event);

            return returnValue;
        } catch (RestWebApplicationException ex) {
            try {
                errorLog.error(String.format(logformat, "ERR", nodeParentUuid, "resource", ui.userId, timeFormat, httpServletRequest.getRemoteAddr(), xmlResource));
                editLog.info(String.format(logformat, "ERR", nodeParentUuid, "resource", ui.userId, timeFormat, httpServletRequest.getRemoteAddr(), xmlResource));
            } catch (Exception exex) {
                logger.error(exex.getMessage());
            }
            logger.error("Managed error", ex);


            throw ex;
        } catch (Exception ex) {
            try {
                errorLog.error(String.format(logformat, "ERR", nodeParentUuid, "resource", ui.userId, timeFormat, httpServletRequest.getRemoteAddr(), xmlResource));
            } catch (Exception exex) {
                logger.error(exex.getMessage());
            }
            logger.error("Managed error", ex);


            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Add a resource (?)
     * POST /rest/api/resources/{node-parent-uuid}
     * parameters:
     * return:
     **/
    @Path("/resources/{node-parent-uuid}")
    @POST
    @Produces(MediaType.APPLICATION_XML)
    public String postResource(String xmlResource, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId,
                               @PathParam("node-parent-uuid") String nodeParentUuid, @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest,
                               @QueryParam("user") Integer userId) {
        if (!isUUID(nodeParentUuid)) {
            logger.error("isUUID({})  is false", nodeParentUuid);
            throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
        }

        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        try {
            //TODO userId
            c = SqlUtils.getConnection();
            String returnValue = dataProvider.postResource(c, new MimeType("text/xml"), nodeParentUuid, xmlResource, ui.userId, groupId).toString();
            if (MysqlDataProvider.DATABASE_FALSE.equals(returnValue)) {

                throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
            }


            return returnValue;
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
        } catch (Exception ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * (?)
     * POST /rest/api/resources
     * parameters:
     * return:
     **/
    @Path("/resources")
    @POST
    @Produces(MediaType.APPLICATION_XML)
    public String postResource(String xmlResource, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId,
                               @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest, @QueryParam("type") Integer type,
                               @QueryParam("resource") String resource, @QueryParam("user") Integer userId) {
        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        try {
            //TODO userId
            c = SqlUtils.getConnection();
            String returnValue = dataProvider.postResource(c, new MimeType("text/xml"), resource, xmlResource, ui.userId, groupId).toString();
            if (MysqlDataProvider.DATABASE_FALSE.equals(returnValue)) {

                throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
            }


            return returnValue;
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
        } catch (Exception ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Add user to a role (?)
     * POST /rest/api/roleUser
     * parameters:
     * return:
     **/
    @Path("/roleUser")
    @POST
    @Produces(MediaType.APPLICATION_XML)
    public String postRoleUser(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,
                               @Context HttpServletRequest httpServletRequest, @QueryParam("grid") int grid, @QueryParam("user-id") Integer userid) {
        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        try {
            //TODO userId
            c = SqlUtils.getConnection();
            String returnValue = dataProvider.postRoleUser(c, ui.userId, grid, userid);
            if (MysqlDataProvider.DATABASE_FALSE.equals(returnValue)) {

                throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
            }
            //
            //        	dataProvider.disconnect();
            return returnValue;
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
        } catch (Exception ex) {
            logger.error("Managed error", ex);
            //
            //			dataProvider.disconnect();
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Modify a role
     * PUT /rest/api/roles/role/{role-id}
     * parameters:
     * return:
     **/
    @Path("/roles/role/{role-id}")
    @PUT
    @Produces(MediaType.APPLICATION_XML)
    public String putRole(String xmlRole, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId,
                          @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest, @PathParam("role-id") int roleId) {
        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        try {
            //TODO userId
            c = SqlUtils.getConnection();
            String returnValue = dataProvider.putRole(c, xmlRole, ui.userId, roleId).toString();
            if (MysqlDataProvider.DATABASE_FALSE.equals(returnValue)) {

                throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
            }
            //
            //        	dataProvider.disconnect();
            return returnValue;
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
        } catch (Exception ex) {
            logger.error("Managed error", ex);
            //
            //			dataProvider.disconnect();
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Fetch all role in a portfolio
     * GET /rest/api/roles/portfolio/{portfolio-id}
     * parameters:
     * return:
     **/
    @Path("/roles/portfolio/{portfolio-id}")
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public String getRolePortfolio(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId,
                                   @QueryParam("role") String role, @PathParam("portfolio-id") String portfolioId, @Context ServletConfig sc,
                                   @Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept) {
        if (!isUUID(portfolioId)) {
            logger.error("isUUID({})  is false", portfolioId);
            throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
        }

        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        try {
            c = SqlUtils.getConnection();
            //			if(accept.equals(MediaType.APPLICATION_JSON))
            //				returnValue = XML.toJSONObject(returnValue).toString();
            return dataProvider.getRolePortfolio(c, new MimeType("text/xml"), role, portfolioId, ui.userId);
        } catch (Exception ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Fetch rights in a role
     * FIXME: Might be redundant
     * GET /rest/api/roles/role/{role-id}
     * parameters:
     * return:
     **/
    @Path("/roles/role/{role-id}")
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public String getRole(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("role-id") Integer roleId,
                          @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept) {
        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;
        try {
            c = SqlUtils.getConnection();
            String returnValue = dataProvider.getRole(c, new MimeType("text/xml"), roleId, ui.userId);
            if (returnValue.equals("")) {
                throw new RestWebApplicationException(Status.NOT_FOUND, "Role " + roleId + " not found");
            }
            return returnValue;
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.NOT_FOUND, "Role " + roleId + " not found");
        } catch (Exception ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Fetch all models
     * FIXME: Most probably useless
     * GET /rest/api/models
     * parameters:
     * return:
     **/
    @Deprecated
    @Path("/models")
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public String getModels(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,
                            @Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept) {
        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        try {
            c = SqlUtils.getConnection();
            String returnValue = dataProvider.getModels(c, new MimeType("text/xml"), ui.userId).toString();
            if (returnValue.equals("")) {


                throw new RestWebApplicationException(Status.NOT_FOUND, "Role " + " not found");
            }


            return returnValue;
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.NOT_FOUND, "Role " + " not found");
        } catch (Exception ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Fetch a model
     * FIXME: Most probably useless
     * GET /rest/api/{model-id}
     * parameters:
     * return:
     **/
    @Deprecated
    @Path("/models/{model-id}")
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public String getModel(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @PathParam("model-id") Integer modelId,
                           @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept) {
        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        try {
            c = SqlUtils.getConnection();
            String returnValue = dataProvider.getModel(c, new MimeType("text/xml"), modelId, ui.userId).toString();
            if (returnValue.equals("")) {


                throw new RestWebApplicationException(Status.NOT_FOUND, "Role " + " not found");
            }


            return returnValue;
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.NOT_FOUND, "Role " + " not found");
        } catch (Exception ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Add a model (deprecated)
     * POST /rest/api/models
     * parameters:
     * return:
     **/
    @Deprecated
    @Path("/models")
    @POST
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public String postModel(String xmlModel, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,
                            @Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept) {
        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        try {
            c = SqlUtils.getConnection();
            String returnValue = dataProvider.postModels(c, new MimeType("text/xml"), xmlModel, ui.userId).toString();
            if (returnValue.equals("")) {


                throw new RestWebApplicationException(Status.NOT_FOUND, "Role " + " not found");
            }


            return returnValue;
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.NOT_FOUND, "Role " + " not found");
        } catch (Exception ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Delete a resource
     * DELETE /rest/api/resources/{resource-id}
     * parameters:
     * return:
     **/
    @Path("/resources/{resource-id}")
    @DELETE
    @Produces(MediaType.APPLICATION_XML)
    public String deleteResource(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId,
                                 @PathParam("resource-id") String resourceUuid, @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest,
                                 @QueryParam("user") Integer userId) {
        if (!isUUID(resourceUuid)) {
            logger.error("isUUID({})  is false", resourceUuid);
            throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
        }
        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        try {
            c = SqlUtils.getConnection();
            int nbResourceDeleted = Integer.parseInt(dataProvider.deleteResource(c, resourceUuid, ui.userId, groupId).toString());
            if (nbResourceDeleted == 0) {


                throw new RestWebApplicationException(Status.NOT_FOUND, "Resource " + resourceUuid + " not found");
            }


            return "";
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.NOT_FOUND, "Resource " + resourceUuid + " not found");
        } catch (Exception ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Delete a right definition for a node
     * DELETE /rest/api/groupRights
     * parameters:
     * return:
     **/
    @Path("/groupRights")
    @DELETE
    @Produces(MediaType.APPLICATION_XML)
    public String deleteGroupRights(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,
                                    @Context HttpServletRequest httpServletRequest, @QueryParam("groupRightId") Integer groupRightId) {
        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        try {
            c = SqlUtils.getConnection();
            int nbResourceDeleted = Integer.parseInt(dataProvider.deleteGroupRights(c, groupId, groupRightId, ui.userId).toString());
            if (nbResourceDeleted == 0) {
                logger.info("supprimé");
            }


            return "";
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.NOT_FOUND, "Resource  not found");
        } catch (Exception ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * !! This or the other gets deleted (redundant)
     * Delete users
     * DELETE /rest/api/users
     * parameters:
     * return:
     **/
    @Path("/users")
    @DELETE
    @Produces(MediaType.APPLICATION_XML)
    public String deleteUsers(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,
                              @Context HttpServletRequest httpServletRequest, @QueryParam("userId") Integer userId) {
        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;
        String message;

        try {
            c = SqlUtils.getConnection();

            // Not (admin or self)
            if (!credential.isAdmin(c, ui.userId) && ui.userId != userId)
                throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

            int nbResourceDeleted = dataProvider.deleteUsers(c, ui.userId, userId);

            if (nbResourceDeleted > 0)
                message = "user " + userId + " deleted";
            else
                message = "user " + userId + " not found";

            logger.debug(message);
            return "";
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.NOT_FOUND, "Resource  not found");
        } catch (Exception ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Delete specific user
     * DELETE /rest/api/users/user/{user-id}
     * parameters:
     * return:
     **/
    @Path("/users/user/{user-id}")
    @DELETE
    @Produces(MediaType.APPLICATION_XML)
    public String deleteUser(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,
                             @Context HttpServletRequest httpServletRequest, @PathParam("user-id") Integer userid) {
        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;
        String message;

        try {
            c = SqlUtils.getConnection();
            // Not (admin or self)
            if (!credential.isAdmin(c, ui.userId) && ui.userId != userid)
                throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

            int nbResourceDeleted = dataProvider.deleteUsers(c, ui.userId, userid);

            if (nbResourceDeleted > 0)
                message = "user " + userid + " deleted";
            else
                message = "user " + userid + " not found";
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.NOT_FOUND, "Resource  not found");
        } catch (Exception ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
        return message;
    }

    /**
     * Get roles in a portfolio
     * GET /rest/api/groups/{portfolio-id}
     * parameters:
     * return:
     **/
    @Path("/groups/{portfolio-id}")
    @GET
    @Produces(MediaType.APPLICATION_XML)
    public String getGroupsPortfolio(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @QueryParam("userrole") String userrole, 
                                     @PathParam("portfolio-id") String portfolioUuid, @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest) {
        if (!isUUID(portfolioUuid)) {
            logger.error("isUUID({})  is false", portfolioUuid);
            throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
        }
        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        try {
            c = SqlUtils.getConnection();


            return dataProvider.getGroupsPortfolio(c, portfolioUuid, ui.userId, userrole);
        } catch (Exception ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Get roles in a portfolio
     * GET /rest/api/credential/group/{portfolio-id}
     * parameters:
     * return:
     **/
    @Path("/credential/group/{portfolio-id}")
    @GET
    @Produces(MediaType.APPLICATION_XML)
    public String getUserGroupByPortfolio(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId,
                                          @PathParam("portfolio-id") String portfolioUuid, @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest) {
        if (!isUUID(portfolioUuid)) {
            logger.error("isUUID({})  is false", portfolioUuid);
            throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
        }

        HttpSession session = httpServletRequest.getSession(true);
        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        try {
            c = SqlUtils.getConnection();
            String xmlGroups = dataProvider.getUserGroupByPortfolio(c, portfolioUuid, ui.userId);


            DocumentBuilderFactory documentBuilderFactory = DomUtils.newSecureDocumentBuilderFactory();
            DocumentBuilder documentBuilder;
            Document document;
            documentBuilder = documentBuilderFactory.newDocumentBuilder();
            document = documentBuilder.newDocument();
            document.setXmlStandalone(true);
            Document doc = documentBuilder.parse(new ByteArrayInputStream(xmlGroups.getBytes(StandardCharsets.UTF_8)));
            NodeList groups = doc.getElementsByTagName("group");
            if (groups.getLength() == 1) {
                Node groupnode = groups.item(0);
                String gid = groupnode.getAttributes().getNamedItem("id").getNodeValue();
            } else if (groups.getLength() == 0)    // Pas de groupe, on rend invalide le choix
            {
            }

            return xmlGroups;
        } catch (Exception ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Send login information
     * PUT /rest/api/credential/login
     * parameters:
     * return:
     **/
    @Path("/credential/login")
    @PUT
    @Produces(MediaType.APPLICATION_XML)
    @Consumes(MediaType.APPLICATION_XML)
    public Response putCredentialFromXml(String xmlCredential, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId,
                                         @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest) {
        return this.postCredentialFromXml(xmlCredential, user, token, 0, sc, httpServletRequest);
    }

    /**
     * Send login information
     * POST /rest/api/credential/login
     * parameters:
     * return:
     **/
    @Path("/credential/login")
    @POST
    @Produces(MediaType.APPLICATION_XML)
    @Consumes(MediaType.APPLICATION_XML)
    public Response postCredentialFromXml(String xmlCredential, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId,
                                          @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest) {

        HttpSession session = httpServletRequest.getSession(true);
        KEvent event = new KEvent();
        event.eventType = KEvent.EventType.LOGIN;
        event.inputData = xmlCredential;
        String retVal = "";
        int status;
        Connection c = null;

        try {
            Document doc = DomUtils.xmlString2Document(xmlCredential, new StringBuffer());
            Element credentialElement = doc.getDocumentElement();
            String login = "";
            String password = "";
            String substit = null;
            if (credentialElement.getNodeName().equals("credential")) {
                String[] templogin = DomUtils.getInnerXml(doc.getElementsByTagName("login").item(0)).split("#");
                password = DomUtils.getInnerXml(doc.getElementsByTagName("password").item(0));

                if (templogin.length > 1)
                    substit = templogin[1];
                login = templogin[0];
            }
            // security to avoid to process login expect on public access as this login is required
            if (!activelogin && !login.equalsIgnoreCase("public")) {
                return Response.status(Status.NOT_FOUND).build();
            }

            /// Test LDAP
//			ConnexionLdap cldap = new ConnexionLdap();
//			cldap.getLdapConnexion();

            int dummy = 0;
            c = SqlUtils.getConnection();
            String[] resultCredential = dataProvider.postCredentialFromXml(c, dummy, login, password, substit);
            // 0: xml de retour
            // 1,2: username, uid
            // 3,4: substitute name, substitute id
            String timeFormat = DT2.format(new Date());
            if (resultCredential == null) {
                event.status = 403;
                retVal = "invalid credential";

                authLog.info(String.format("Authentication error for user '%s' date '%s'\n", login, timeFormat));
            } else if (!"0".equals(resultCredential[2])) {
                //				String tokenID = resultCredential[2];

                if (substit != null && !"0".equals(resultCredential[4])) {
                    int uid = Integer.parseInt(resultCredential[2]);
                    int subid = Integer.parseInt(resultCredential[4]);

                    session.setAttribute("user", resultCredential[3]);
                    session.setAttribute("uid", subid);
                    session.setAttribute("subuser", resultCredential[1]);
                    session.setAttribute("subuid", uid);

                    authLog.info(String.format("Authentication success for user '%s' date '%s' (Substitution)\n", login, timeFormat));
                } else {
                    String login1 = resultCredential[1];
                    int userId = Integer.parseInt(resultCredential[2]);

                    session.setAttribute("user", login1);
                    session.setAttribute("uid", userId);
                    session.setAttribute("subuser", "");
                    session.setAttribute("subuid", 0);

                    authLog.info(String.format("Authentication success for user '%s' date '%s'\n", login, timeFormat));
                }

                event.status = 200;
                retVal = resultCredential[0];
            }
            eventbus.processEvent(event);

            // frontend need to know a 404 when internal login is not available - here because public login
            if (!activelogin ) {
                return Response.status(Status.NOT_FOUND).entity(retVal).type(event.mediaType).build();
            }

            return Response.status(event.status).entity(retVal).type(event.mediaType).build();
        } catch (RestWebApplicationException ex) {
            logger.error("Invalid credentials", ex);
            throw new RestWebApplicationException(Status.FORBIDDEN, ex.getMessage());
        } catch (Exception ex) {
            status = 500;
            retVal = ex.getMessage();
            logger.error("Invalid credentials", ex);
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }

        return Response.status(status).entity(retVal).type(MediaType.APPLICATION_XML).build();
    }

    /**
     * Tell system you forgot your password
     * POST /rest/api/credential/forgot
     * parameters:
     * return:
     **/
    @Path("/credential/forgot")
    @POST
    @Consumes(MediaType.APPLICATION_XML)
    public Response postForgotCredential(String xml, @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest) {
        HttpSession session = httpServletRequest.getSession(true);
        int retVal = 404;
        String retText = "";
        Connection c = null;

        if (resetPWEnable) {
            try {
                Document doc = DomUtils.xmlString2Document(xml, new StringBuffer());
                Element infUser = doc.getDocumentElement();

                String username = "";
                if (infUser.getNodeName().equals("credential")) {
                    NodeList children2 = infUser.getChildNodes();
                    for (int y = 0; y < children2.getLength(); y++) {
                        if (children2.item(y).getNodeName().equals("login")) {
                            username = DomUtils.getInnerXml(children2.item(y));
                            break;
                        }
                    }
                }

                c = SqlUtils.getConnection();
                // Check if we have that email somewhere
                String email = dataProvider.emailFromLogin(c, username);
                if (email != null && !"".equals(email)) {
                    // Generate password
                    long base = System.currentTimeMillis();
                    MessageDigest md = MessageDigest.getInstance("SHA-1");
                    byte[] output = md.digest(Long.toString(base).getBytes());
                    String password = String.format("%032X", new BigInteger(1, output));
                    password = password.substring(0, 9);

                    // Write change
                    boolean result = dataProvider.changePassword(c, username, password);
                    String content = emailResetMessage + password + "<br>\n";
                    String referal = httpServletRequest.getHeader("referer");
//				content += String.format("administrator - %s", referal);

                    if (result) {
                        if (securityLog != null) {
                            String ip = httpServletRequest.getRemoteAddr();
                            securityLog.info("[{}] [{}] asked to reset password", ip, username);
                        }
                        // Send email
                        MailUtils.postMail(sc, email, ccEmail, "Password change for Karuta", content, logger);
                        retVal = 200;
                        retText = "sent";
                    }
                }
            } catch (RestWebApplicationException ex) {
                logger.error("Managed error", ex);
                throw new RestWebApplicationException(Status.FORBIDDEN, ex.getMessage());
            } catch (Exception ex) {
                logger.error("Managed error", ex);
                throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
            } finally {
                try {
                    if (c != null) c.close();
                } catch (SQLException e) {
                    logger.error("Managed error", e);
                }
            }
        }

        return Response.status(retVal).entity(retText).build();
    }

    /**
     * Fetch current user information (CAS)
     * GET /rest/api/credential/login/cas
     * parameters:
     * return:
     **/
    @POST
    @Path("/credential/login/cas")
    public Response postCredentialFromCas(String content, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId,
                                          @QueryParam("ticket") String ticket, @QueryParam("redir") String redir, @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest) throws IllegalAccessException {
        logger.debug("RECEIVED POST CAS: tok: " + token + " tix: " + ticket + " red: " + redir);
        return getCredentialFromCas(user, token, groupId, ticket, redir, sc, httpServletRequest);
    }

    @Path("/credential/login/cas")
    @GET
    public Response getCredentialFromCas(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId,
                                         @QueryParam("ticket") String ticket, @QueryParam("redir") String redir, @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest) throws IllegalAccessException {
        HttpSession session = httpServletRequest.getSession(true);

        String casUrlVal = casUrlValidation;
        if (casUrlsValidation != null && !casUrlsValidation.isEmpty()) {
            if (casUrlsValidation.containsKey(redir)) {
                casUrlVal = casUrlsValidation.get(redir);
            } else {
                final String error = String.format("Unauthorized or not configured redirection '%s' with conf '%s'", redir, casUrlsJsonString);
                logger.error(error);
                throw new IllegalAccessException(error);
            }
        }

        String xmlResponse = null;
        String userId;
        String completeURL;
        StringBuffer requestURL;

        Connection c = null;
        try {

            if (casUrlVal == null) {
                Response response;
                try {
                    // formulate the response
                    response = Response.status(Status.PRECONDITION_FAILED)
                            .entity("CAS URL not defined").build();
                } catch (Exception e) {
                    response = Response.status(500).build();
                }
                return response;
            }

            Cas20ServiceTicketValidator sv = new Cas20ServiceTicketValidator(casUrlVal);

            /// X-Forwarded-Proto is for certain setup, check config file
            /// for some more details
            String proto = httpServletRequest.getHeader("X-Forwarded-Proto");
            requestURL = httpServletRequest.getRequestURL();
            if (proto == null) {
                if (redir != null) {
                    requestURL.append("?redir=").append(redir);
                }
                completeURL = requestURL.toString();
            } else {
                /// Keep only redir parameter
                if (redir != null) {
                    requestURL.append("?redir=").append(redir);
                }
                completeURL = requestURL.replace(0, requestURL.indexOf(":"), proto).toString();
            }
            /// completeURL should be the same provided in the "service" parameter
            logger.debug("Service: {}", completeURL);
            logger.debug("Ticket: {}", ticket);
            //sv.setProxyCallbackUrl(urlOfProxyCallbackServlet);
            Assertion assertion = sv.validate(ticket, completeURL);

            if (!assertion.isValid()) {
                logger.info("CAS response: {}", xmlResponse);
                return Response.status(Status.FORBIDDEN).entity("CAS error").build();
            }
//			/*
            else {
                logger.info("CAS AUTH SHOULD BE FINE: {}", assertion.getPrincipal());
            }
            //*/


            //<cas:user>vassoilm</cas:user>
            final String casUserId = assertion.getPrincipal().getName();
            session.setAttribute("user", casUserId);
//			session.setAttribute("uid", dataProvider.getUserId(sv.getUser()));
            c = SqlUtils.getConnection();
            userId = dataProvider.getUserId(c, casUserId, null);
            if (!"0".equals(userId))    // User exist
            {
                session.setAttribute("user", casUserId);
                session.setAttribute("uid", Integer.parseInt(userId));

                if (ldapUrl != null) {
                    ConnexionLdap cldap = new ConnexionLdap();
                    if (logger.isDebugEnabled()) {
                        final String[] ldapvalues = cldap.getLdapValue(casUserId);
                        logger.debug("LDAP CONNECTION OK: {}", Arrays.toString(ldapvalues));
                    }
                }
            } else {
                if (casCreateAccount) {
                    if (ldapUrl != null) {
                        ConnexionLdap cldap = new ConnexionLdap();
                        final String[] ldapvalues = cldap.getLdapValue(casUserId);
                        if (ldapvalues[1] != null | ldapvalues[2] != null | ldapvalues[3] != null) //si le filtre ldap a renvoyé des valeurs
                        {
                            userId = dataProvider.createUser(c, casUserId, null);
                            int uid = Integer.parseInt(userId);
                            dataProvider.putInfUserInternal(c, uid, uid, ldapvalues[1], ldapvalues[2], ldapvalues[3]);
                            logger.info("USERID: " + casUserId + " " + userId);
                            session.setAttribute("user", casUserId);
                            session.setAttribute("uid", Integer.parseInt(userId));
                        } else {
                            return Response.status(400).entity("Login " + casUserId + " don't have access to Karuta").build();
                        }
                    } else {
                        userId = dataProvider.createUser(c, casUserId, null);
                        logger.info("USERID: " + casUserId + " " + userId);
                        session.setAttribute("user", casUserId);
                        session.setAttribute("uid", Integer.parseInt(userId));
                    }
                } else {
                    logger.warn("Login '{}' not found", casUserId);
                    return Response.status(403).entity("Login " + casUserId + " not found").build();
                }
            }

            Response response;
            try {
                // formulate the response
                response = Response.status(201)
                        .header("Location", redir)
                        .entity("<script>document.location.replace('" + redir + "')</script>").build();
            } catch (Exception e) {
                response = Response.status(500).build();
            }
            return response;
        } catch (TicketValidationException ex) {
            logger.error("CAS Validation Error", ex);
            throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires (ticket ?, casUrlValidation) :" + casUrlVal);
        } catch (Exception ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires :" + ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Ask to logout, clear session
     * POST /rest/api/credential/logout
     * parameters:
     * return:
     **/
    @Path("/credential/logout")
    @GET
    public Response logoutGET(@Context ServletConfig sc, @Context HttpServletRequest httpServletRequest, @QueryParam("redir") String redir) {
        HttpSession session = httpServletRequest.getSession(false);
        //TODO: odd logic between 2 properties and a default location to redirect
        if (session == null) {
            // If value is set, this redirection takes priority
            if (!StringUtils.isBlank(shibbolethLogoutRedirectionURL)) {
                return Response.status(301).header("Location", shibbolethLogoutRedirectionURL).build();
            }
        }
        Integer fromshibe = null;
        if (session != null) fromshibe = (Integer) session.getAttribute("fromshibe");
        if (session != null) session.invalidate();

        if (fromshibe != null && fromshibe == 1) {
            //// Redirect to shibe logout
            //String redir = ConfigUtils.get("shib_logout");
            // Default URL
            return Response.status(301).header("Location", "/Shibboleth.sso/Logout").build();
        }
        if (!StringUtils.isBlank(redir)) {
            return Response.status(301).header("Location", redir).build();
        }
        // Just redirect to base UI location
        return Response.status(301).header("Location", basicLogoutRedirectionURL).build();
    }

    @Path("/credential/logout")
    @POST
    public Response logoutPOST(@Context ServletConfig sc, @Context HttpServletRequest httpServletRequest) {
        HttpSession session = httpServletRequest.getSession(false);
        if (session != null)
            session.invalidate();

        return Response.ok("logout").build();
    }

    /**
     * Fetch node content
     * GET /rest/api/nodes/{node-id}
     * parameters:
     * return:
     **/
    @Path("/nodes/{node-id}")
    @GET
    @Consumes(MediaType.APPLICATION_XML)
    public String getNodeWithXSL(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @QueryParam("userrole") String userrole, 
                                 @PathParam("node-id") String nodeUuid, @QueryParam("xsl-file") String xslFile, @Context ServletConfig sc,
                                 @Context HttpServletRequest httpServletRequest, @HeaderParam("Accept") String accept, @QueryParam("user") Integer userId,
                                 @QueryParam("lang") String lang, @QueryParam("p1") String p1, @QueryParam("p2") String p2, @QueryParam("p3") String p3) {
        if (!isUUID(nodeUuid)) {
            logger.error("isUUID({})  is false", nodeUuid);
            throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
        }

        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        try {
            // When we need more parameters, arrange this with format "par1:par1val;par2:par2val;..."
            String parameters = "lang:" + lang;

            javax.servlet.http.HttpSession session = httpServletRequest.getSession(true);
            String ppath = session.getServletContext().getRealPath(File.separator);

            c = SqlUtils.getConnection();
            /// webapps...
            ppath = ppath.substring(0, ppath.lastIndexOf(File.separator, ppath.length() - 2) + 1);
            xslFile = ppath + xslFile;

            String returnValue = dataProvider.getNodeWithXSL(c, new MimeType("text/xml"), nodeUuid, xslFile, parameters, ui.userId, groupId, userrole).toString();
            if (returnValue.length() != 0) {
                if (MediaType.APPLICATION_JSON.equals(accept))
                    returnValue = XML.toJSONObject(returnValue).toString();

            } else {
                throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
            }


            return returnValue;
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
        } catch (NullPointerException ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.NOT_FOUND, "Node " + nodeUuid + " not found or xsl not found :" + ex.getMessage());
        } catch (Exception ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * POST /rest/api/nodes/{node-id}/frommodelbysemantictag/{semantic-tag}
     * parameters:
     * return:
     **/
    @Path("/nodes/{node-id}/frommodelbysemantictag/{semantic-tag}")
    @POST
    @Consumes(MediaType.APPLICATION_XML)
    @Produces(MediaType.APPLICATION_XML)
    public String postNodeFromModelBySemanticTag(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @QueryParam("userrole") String userrole, 
                                                 @PathParam("node-id") String nodeUuid, @PathParam("semantic-tag") String semantictag, @Context ServletConfig sc,
                                                 @Context HttpServletRequest httpServletRequest, @QueryParam("user") Integer userId) {
        if (!isUUID(nodeUuid)) {
            logger.error("isUUID({})  is false", nodeUuid);
            throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
        }

        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        Connection c = null;

        try {
            c = SqlUtils.getConnection();
            String returnValue = dataProvider.postNodeFromModelBySemanticTag(c, new MimeType("text/xml"), nodeUuid, semantictag, ui.userId, groupId, userrole).toString();


            if (MysqlDataProvider.DATABASE_FALSE.equals(returnValue)) {
                throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
            }

            return returnValue;
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.FORBIDDEN, ex.getResponse().getEntity().toString());
        } catch (Exception ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Import zip file
     * POST /rest/api/portfolios/zip
     * parameters:
     * return:
     **/
    @Path("/portfolios/zip")
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.TEXT_PLAIN)
    public String postPortfolioByForm(@CookieParam("user") String user, @CookieParam("credential") String token, @QueryParam("group") int groupId, @Context ServletConfig sc,
                                      @Context HttpServletRequest httpServletRequest, @QueryParam("user") Integer userId, @QueryParam("model") String modelId,
                                      @FormDataParam("uploadfile") InputStream uploadedInputStream, @FormDataParam("instance") String instance,
                                      @FormDataParam("project") String projectName) {
        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        String returnValue = "";
        Connection c = null;

        try {
            boolean instantiate = Boolean.parseBoolean(instance);

            c = SqlUtils.getConnection();
            returnValue = dataProvider.postPortfolioZip(c, new MimeType("text/xml"), new MimeType("text/xml"), httpServletRequest, uploadedInputStream,
                    ui.userId, groupId, modelId, ui.subId, instantiate, projectName).toString();
        } catch (RestWebApplicationException e) {
            logger.error("Managed error", e);
            throw e;
        } catch (Exception e) {
            logger.error("Managed error", e);
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }

        return returnValue;
    }

    /**
     * Fetch userlist from a role and portfolio id
     * GET /rest/api/users/Portfolio/{portfolio-id}/Role/{role}/users
     * parameters:
     * return:
     **/
    @Path("/users/Portfolio/{portfolio-id}/Role/{role}/users")
    @GET
    @Produces(MediaType.APPLICATION_XML)
    public String getUsersByRole(@CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group,
                                 @PathParam("portfolio-id") String portfolioUuid, @PathParam("role") String role, @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest) {
        if (!isUUID(portfolioUuid)) {
            logger.error("isUUID({})  is false", portfolioUuid);
            throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
        }

        UserInfo ui = checkCredential(httpServletRequest, user, token, group);
        Connection c = null;

        try {
            c = SqlUtils.getConnection();
            return dataProvider.getUsersByRole(c, ui.userId, portfolioUuid, role);
        } catch (Exception ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Fetch groups from a role and portfolio id
     * GET /rest/api/users/Portfolio/{portfolio-id}/Role/{role}/groups
     * parameters:
     * return:
     **/
    @Path("/users/Portfolio/{portfolio-id}/Role/{role}/groups")
    @GET
    @Produces(MediaType.APPLICATION_XML)
    public String getGroupsByRole(@CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group,
                                  @PathParam("portfolio-id") String portfolioUuid, @PathParam("role") String role, @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest) {
        if (!isUUID(portfolioUuid)) {
            logger.error("isUUID({})  is false", portfolioUuid);
            throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
        }

        UserInfo ui = checkCredential(httpServletRequest, user, token, group);
        Connection c = null;

        try {
            c = SqlUtils.getConnection();
            return dataProvider.getGroupsByRole(c, ui.userId, portfolioUuid, role);
        } catch (Exception ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /*
     * ##   ##  #####  ####### #####     ###   ######
     * ##   ## ##   ## ##      ##   ## ##   ## ##   ##
     * ##   ## ##      ##      ##   ## ##      ##   ##
     * ##   ##  #####  ####    #####   ##  ### ######
     * ##   ##      ## ##      ##   ## ##   ## ##   ##
     * ##   ## ##   ## ##      ##   ## ##   ## ##   ##
     *  #####   #####  ####### ##   ##   ###   ##   ##
     ** Managing and listing user groups*/
    /**
     * Create a new user group
     * POST /rest/api/usersgroups
     * parameters:
     * - label: Name of the group we are creating
     * return:
     * - groupid
     **/
    @Path("/usersgroups")
    @POST
    public String postUserGroup(@Context ServletConfig sc, @Context HttpServletRequest httpServletRequest, @QueryParam("label") String groupname) {
        UserInfo ui = checkCredential(httpServletRequest, null, null, null);
        int response = -1;
        Connection c = null;

        try {
            c = SqlUtils.getConnection();
            response = dataProvider.postUserGroup(c, groupname, ui.userId);
            logger.debug("Add user '{}' in group '{}' provided group id {}", ui.userId, groupname, response);

            if (response == -1) {
                logger.warn("Add user '{}' in group '{}' NOT DONE !", ui.userId, groupname);
                throw new RestWebApplicationException(Status.NOT_MODIFIED, "Error in creation");
            }
        } catch (Exception ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }

        return Integer.toString(response);
    }

    /**
     * Put a user in user group
     * PUT /rest/api/usersgroups
     * parameters:
     * - group: group id
     * - user: user id
     * - label: label
     * return:
     * Code 200
     **/
    @Path("/usersgroups")
    @PUT
    public Response putUserInUserGroup(@Context ServletConfig sc, @Context HttpServletRequest httpServletRequest, @QueryParam("group") Integer group,
                                       @QueryParam("user") Integer user, @QueryParam("label") String label) {
        UserInfo ui = checkCredential(httpServletRequest, null, null, null);
        Connection c = null;
        try {
            c = SqlUtils.getConnection();
            boolean isOK = false;
            if (label != null) {
                // Rename group
                isOK = dataProvider.putUserGroupLabel(c, user, group, label);
            } else {
                // Add user in group
                isOK = dataProvider.putUserInUserGroup(c, user, group, ui.userId);

            }
            if (isOK)
                return Response.status(200).entity("Changed").build();
            else
                return Response.status(200).entity("Not OK").build();
        } catch (Exception ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Get users by usergroup, or if there's no group id give, give the list of user group
     * GET /rest/api/usersgroups
     * parameters:
     * - group: group id
     * return:
     * - Without group id
     * <groups>
     * <group id={groupid}>
     * <label>{group name}</label>
     * </group>
     * ...
     * </groups>
     * <p>
     * - With group id
     * <group id={groupid}>
     * <user id={userid}></user>
     * ...
     * </group>
     **/
    @Path("/usersgroups")
    @GET
    public String getUsersByUserGroup(@Context ServletConfig sc, @Context HttpServletRequest httpServletRequest, @QueryParam("group") Integer group,
                                      @QueryParam("user") Integer user, @QueryParam("label") String groupLabel) {
        UserInfo ui = checkCredential(httpServletRequest, null, null, null);

        Connection c = null;
        String xmlUsers = "";
        try {
            c = SqlUtils.getConnection();
            if (groupLabel != null) {
                int groupId = dataProvider.getGroupByGroupLabel(c, groupLabel, ui.userId);
                if (groupId < 0) {
                    throw new RestWebApplicationException(Status.NOT_FOUND, "");
                }
                xmlUsers = Integer.toString(groupId);
            } else if (user != null)
                xmlUsers = dataProvider.getGroupByUser(c, user, ui.userId);
            else if (group == null)
                xmlUsers = dataProvider.getUserGroupList(c, ui.userId);
            else
                xmlUsers = dataProvider.getUsersByUserGroup(c, group, ui.userId);

        } catch (Exception ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
        return xmlUsers;
    }

    /**
     * Remove a user from a user group, or remove a usergroup
     * DELETE /rest/api/usersgroups
     * parameters:
     * - group: group id
     * - user: user id
     * return:
     * Code 200
     **/
    @Path("/usersgroups")
    @DELETE
    public String deleteUsersByUserGroup(@Context ServletConfig sc, @Context HttpServletRequest httpServletRequest, @QueryParam("group") int group, @QueryParam("user") Integer user) {
        UserInfo ui = checkCredential(httpServletRequest, null, null, null);
        String response = "";
        Connection c = null;
        Boolean isOK = false;
        try {
            c = SqlUtils.getConnection();
            if (user == null)
                isOK = dataProvider.deleteUsersGroups(c, group, ui.userId);
            else
                isOK = dataProvider.deleteUsersFromUserGroups(c, user, group, ui.userId);
        } catch (Exception ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
        return response;
    }

    /*
     * ######   #####  ######  #######   ###   ######
     * ##   ## ##   ## ##   ##    #    ##   ## ##   ##
     * ##   ## ##   ## ##   ##    #    ##      ##   ##
     * ######  ##   ## ######     #    ##  ### ######
     * ##      ##   ## ## ##      #    ##   ## ##   ##
     * ##      ##   ## ##  ##     #    ##   ## ##   ##
     * ##       #####  ##   ##    #      ###   ##   ##
     ** Managing and listing portfolios */
    /**
     * Create a new portfolio group
     * POST /rest/api/portfoliogroups
     * parameters:
     * - label: Name of the group we are creating
     * - parent: parentid
     * - type: group/portfolio
     * return:
     * - groupid
     **/
    @Path("/portfoliogroups")
    @POST
    public Response postPortfolioGroup(@Context ServletConfig sc, @Context HttpServletRequest httpServletRequest, @QueryParam("label") String groupname,
                                       @QueryParam("type") String type, @QueryParam("parent") Integer parent) {
        UserInfo ui = checkCredential(httpServletRequest, null, null, null);
        int response = -1;
        Connection c = null;

        // Check type value
        try {
            c = SqlUtils.getConnection();
            response = dataProvider.postPortfolioGroup(c, groupname, type, parent, ui.userId);
            if (response == -1) {
                return Response.status(Status.NOT_MODIFIED).entity("Error in creation").build();
            }
        } catch (Exception ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
        return Response.ok(Integer.toString(response)).build();
    }

    /**
     * Put a portfolio in portfolio group
     * PUT /rest/api/portfoliogroups
     * parameters:
     * - group: group id
     * - uuid: portfolio id
     * return:
     * Code 200
     **/
    @Path("/portfoliogroups")
    @PUT
    public Response putPortfolioInPortfolioGroup(@Context ServletConfig sc, @Context HttpServletRequest httpServletRequest, @QueryParam("group") Integer group,
                                                 @QueryParam("uuid") String uuid, @QueryParam("label") String label) {
        UserInfo ui = checkCredential(httpServletRequest, null, null, null);
        Connection c = null;

        try {
            c = SqlUtils.getConnection();
            int response = -1;
            response = dataProvider.putPortfolioInGroup(c, uuid, group, label, ui.userId);
            return Response.ok(Integer.toString(response)).build();
        } catch (Exception ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Get portfolio by portfoliogroup, or if there's no group id give, give the list of portfolio group
     * GET /rest/api/portfoliogroups
     * parameters:
     * - group: group id
     * - label: group label -> Return group id
     * return:
     * - Without group id
     * <groups>
     * <group id={groupid}>
     * <label>{group name}</label>
     * </group>
     * ...
     * </groups>
     * <p>
     * - With group id
     * <group id={groupid}>
     * <portfolio id={uuid}></portfolio>
     * ...
     * </group>
     **/
    @Path("/portfoliogroups")
    @GET
    public String getPortfolioByPortfolioGroup(@Context ServletConfig sc, @Context HttpServletRequest httpServletRequest, @QueryParam("group") Integer group,
                                               @QueryParam("uuid") String portfolioid, @QueryParam("label") String groupLabel) {
        UserInfo ui = checkCredential(httpServletRequest, null, null, null);
        Connection c = null;
        String xmlUsers;

        try {
            c = SqlUtils.getConnection();
            if (groupLabel != null) {
                int groupid = dataProvider.getPortfolioGroupIdFromLabel(c, groupLabel, ui.userId);
                if (groupid == -1) {
                    throw new RestWebApplicationException(Status.NOT_FOUND, "");
                }
                xmlUsers = Integer.toString(groupid);
            } else if (portfolioid != null) {
                xmlUsers = dataProvider.getPortfolioGroupListFromPortfolio(c, portfolioid, ui.userId);
            } else if (group == null)
                xmlUsers = dataProvider.getPortfolioGroupList(c, ui.userId);
            else
                xmlUsers = dataProvider.getPortfolioByPortfolioGroup(c, group, ui.userId);
        } catch (Exception ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
        return xmlUsers;
    }

    /**
     * Remove a portfolio from a portfolio group, or remove a portfoliogroup
     * DELETE /rest/api/portfoliogroups
     * parameters:
     * - group: group id
     * - uuid: portfolio id
     * return:
     * Code 200
     **/
    @Path("/portfoliogroups")
    @DELETE
    public String deletePortfolioByPortfolioGroup(@Context ServletConfig sc, @Context HttpServletRequest httpServletRequest, @QueryParam("group") int group, @QueryParam("uuid") String uuid) {
        UserInfo ui = checkCredential(httpServletRequest, null, null, null);
        String response;
        Connection c = null;

        try {
            c = SqlUtils.getConnection();
            if (uuid == null)
                response = dataProvider.deletePortfolioGroups(c, group, ui.userId);
            else
                response = dataProvider.deletePortfolioFromPortfolioGroups(c, uuid, group, ui.userId);
        } catch (Exception ex) {
            logger.error("Managed error", ex);

            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
        return response;
    }

    /*
     * ##   ##   ###     ###   #####     ###
     * ### ### ##   ## ##   ## ##   ## ##   ##
     * ## # ## ##   ## ##      ##   ## ##   ##
     * ##   ## ####### ##      #####   ##   ##
     * ##   ## ##   ## ##      ##   ## ##   ##
     * ##   ## ##   ## ##   ## ##   ## ##   ##
     * ##   ## ##   ##   ###   ##   ##   ###
     /** Partie utilisation des macro-commandes et gestion **/

    /**
     * Executing pre-defined macro command on a node
     * POST /rest/api/action/{uuid}/{macro-name}
     * parameters:
     * return:
     **/
    @Path("/action/{uuid}/{macro-name}")
    @POST
    @Consumes(MediaType.APPLICATION_XML + "," + MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public String postMacro(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group,
                            @PathParam("uuid") String uuid, @PathParam("macro-name") String macroName,
                            @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest) {
        if (!isUUID(uuid)) {
            logger.error("isUUID({})  is false", uuid);
            throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
        }
        UserInfo ui = checkCredential(httpServletRequest, user, token, group);

        String returnValue;
        Connection c = null;
        try {
            c = SqlUtils.getConnection();
            // On execute l'action sur le noeud uuid
            if (uuid != null && macroName != null) {
                returnValue = dataProvider.postMacroOnNode(c, ui.userId, uuid, macroName);
                if (MysqlDataProvider.DATABASE_FALSE.equals(returnValue)) {
                    throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
                }
            }
            // Erreur de requête
            else {
                returnValue = "";
            }

            return returnValue;
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
        } catch (Exception ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /*
     * ######  #######   ###   ##   ## #######  #####
     * ##   ##    #    ##   ## ##   ##    #    ##   ##
     * ##   ##    #    ##      ##   ##    #    ##
     * ######     #    ##  ### #######    #     #####
     * ##   ##    #    ##   ## ##   ##    #         ##
     * ##   ##    #    ##   ## ##   ##    #    ##   ##
     * ##   ## #######   ###   ##   ##    #     #####
     /** Partie groupe de droits et utilisateurs            **/

    /**
     * Change rights
     * POST /rest/api/rights
     * parameters:
     * return:
     **/
    @Path("/rights")
    @POST
    @Produces(MediaType.APPLICATION_XML)
    public String postChangeRights(String xmlNode, @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest) {
        UserInfo ui = checkCredential(httpServletRequest, null, null, null);

        String returnValue = "";
        Connection c = null;
        try {
            /*
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

            DocumentBuilderFactory documentBuilderFactory = DomUtils.newSecureDocumentBuilderFactory();
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            Document doc = documentBuilder.parse(new ByteArrayInputStream(xmlNode.getBytes(StandardCharsets.UTF_8)));

            XPath xPath = XPathFactory.newInstance().newXPath();
            ArrayList<String> portfolio = new ArrayList<>();
//			String xpathRole = "//role";
            String xpathRole = "//*[local-name()='role']";
            XPathExpression findRole = xPath.compile(xpathRole);
//			String xpathNodeFilter = "//xpath";
            String xpathNodeFilter = "//*[local-name()='xpath']";
            XPathExpression findXpath = xPath.compile(xpathNodeFilter);
            String nodefilter;
            NodeList roles = null;

            /// Fetch portfolio(s)
//			String portfolioNode = "//portfoliogroup";
            String portfolioNode = "//*[local-name()='portfoliogroup']";
            XPathExpression xpathFilter = null;
            Node portgroupnode = (Node) xPath.compile(portfolioNode).evaluate(doc, XPathConstants.NODE);
            if (portgroupnode != null) {
                String portgroupname = portgroupnode.getAttributes().getNamedItem("name").getNodeValue();

                Node xpathNode = (Node) findXpath.evaluate(portgroupnode, XPathConstants.NODE);
                nodefilter = xpathNode.getNodeValue();
                xpathFilter = xPath.compile(nodefilter);
                roles = (NodeList) findRole.evaluate(portgroupnode, XPathConstants.NODESET);
            } else {
                // Or add the single one
//				portfolioNode = "//portfolio[@uuid]";
                portfolioNode = "//*[local-name()='portfolio'] and @*[local-name()='uuid']";
                Node portnode = (Node) xPath.compile(portfolioNode).evaluate(doc, XPathConstants.NODE);
                if (portnode != null) {
                    portfolio.add(portnode.getNodeValue());

                    Node xpathNode = (Node) findXpath.evaluate(portnode, XPathConstants.NODE);
                    nodefilter = xpathNode.getNodeValue();
                    xpathFilter = xPath.compile(nodefilter);
                    roles = (NodeList) findRole.evaluate(portnode, XPathConstants.NODESET);
                }
            }

            c = SqlUtils.getConnection();
            ArrayList<String> nodes = new ArrayList<>();
            // For all portfolio
            for (String portfolioUuid : portfolio) {
                String portfolioStr = dataProvider.getPortfolio(c, new MimeType("text/xml"), portfolioUuid, ui.userId, 0, this.label, null,
                        null, ui.subId, null).toString();
                Document docPort = documentBuilder.parse(new ByteArrayInputStream(portfolioStr.getBytes(StandardCharsets.UTF_8)));

                /// Fetch nodes inside those portfolios
                NodeList portNodes = (NodeList) xpathFilter.evaluate(docPort, XPathConstants.NODESET);
                for (int j = 0; j < portNodes.getLength(); ++j) {
                    Node node = portNodes.item(j);
                    String nodeuuid = node.getAttributes().getNamedItem("id").getNodeValue();

                    nodes.add(nodeuuid);    // Keep those we have to change rights
                }
            }

            /// Fetching single node
            if (nodes.isEmpty()) {
//				String singleNode = "//node";
                String singleNode = "//*[local-name()='node']";
                Node sNode = (Node) xPath.compile(singleNode).evaluate(doc, XPathConstants.NODE);
                String uuid = sNode.getAttributes().getNamedItem("uuid").getNodeValue();
                nodes.add(uuid);
                roles = (NodeList) findRole.evaluate(sNode, XPathConstants.NODESET);
            }

            /// For all roles we have to change
            for (int i = 0; i < roles.getLength(); ++i) {
                Node rolenode = roles.item(i);
                String rolename = rolenode.getAttributes().getNamedItem("name").getNodeValue();
                Node right = rolenode.getFirstChild();


                if ("#text".equals(right.getNodeName()))
                    right = right.getNextSibling();

                if ("right".equals(right.getNodeName()))    // Changing node rights
                {
                    NamedNodeMap rights = right.getAttributes();

                    NodeRight noderight = new NodeRight(null, null, null, null, null, null);

                    String val = rights.getNamedItem("RD").getNodeValue();
                    if (val != null)
                        noderight.read = Boolean.parseBoolean(val);
                    val = rights.getNamedItem("WR").getNodeValue();
                    if (val != null)
                        noderight.write = Boolean.parseBoolean(val);
                    val = rights.getNamedItem("DL").getNodeValue();
                    if (val != null)
                        noderight.delete = Boolean.parseBoolean(val);
                    val = rights.getNamedItem("SB").getNodeValue();
                    if (val != null)
                        noderight.submit = Boolean.parseBoolean(val);


                    /// Apply modification for all nodes
                    for (String nodeid : nodes) {
                        // change right
                        dataProvider.postRights(c, ui.userId, nodeid, rolename, noderight);
                    }
                } else if ("action".equals(right.getNodeName()))    // Using an action on node
                {
                    /// Apply modification for all nodes
                    for (String nodeid : nodes) {
                        // TODO: check for reset keyword
                        // reset right
                        dataProvider.postMacroOnNode(c, ui.userId, nodeid, "reset");
                    }
                }
            }

            return returnValue;
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
        } catch (Exception ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * List roles
     * GET /rest/api/rolerightsgroups
     * parameters:
     * return:
     **/
    @Path("/rolerightsgroups")
    @GET
    @Produces(MediaType.APPLICATION_XML)
    public String getRightsGroup(@CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,
                                 @Context HttpServletRequest httpServletRequest, @QueryParam("portfolio") String portfolio, @QueryParam("user") Integer queryuser,
                                 @QueryParam("role") String role) {
        if (!isUUID(portfolio)) {
            logger.error("isUUID({})  is false", portfolio);
            throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
        }

        UserInfo ui = checkCredential(httpServletRequest, user, token, group);

        String returnValue;
        Connection c = null;
        try {
            c = SqlUtils.getConnection();
            // Retourne le contenu du type
            returnValue = dataProvider.getRRGList(c, ui.userId, portfolio, queryuser, role);
            if (MysqlDataProvider.DATABASE_FALSE.equals(returnValue)) {
                throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
            }
            return returnValue;
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw ex;
        } catch (Exception ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * List all users in a specified roles
     * GET /rest/api/rolerightsgroups/all/users
     * parameters:
     * return:
     **/
    @Path("/rolerightsgroups/all/users")
    @GET
    @Produces(MediaType.APPLICATION_XML)
    public String getPortfolioRightInfo(@CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,
                                        @Context HttpServletRequest httpServletRequest, @QueryParam("portfolio") String portId) {
        if (!isUUID(portId)) {
            logger.error("isUUID({})  is false", portId);
            throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
        }
        UserInfo ui = checkCredential(httpServletRequest, user, token, group);

        String returnValue = "";
        Connection c = null;
        try {
            c = SqlUtils.getConnection();
            // Retourne le contenu du type
            if (portId != null) {
                returnValue = dataProvider.getPortfolioInfo(c, ui.userId, portId);


                if (MysqlDataProvider.DATABASE_FALSE.equals(returnValue)) {
                    throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
                }

            }

            return returnValue;
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw ex;
        } catch (Exception ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * List rights in the specified role
     * GET /rest/api/rolerightsgroups/rolerightsgroup/{rolerightsgroup-id}
     * parameters:
     * return:
     **/
    @Path("/rolerightsgroups/rolerightsgroup/{rolerightsgroup-id}")
    @GET
    @Produces(MediaType.APPLICATION_XML)
    public String getRightInfo(@CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,
                               @Context HttpServletRequest httpServletRequest, @PathParam("rolerightsgroup-id") Integer rrgId) {
        UserInfo ui = checkCredential(httpServletRequest, user, token, group);

        String returnValue = "";
        Connection c = null;
        try {
            c = SqlUtils.getConnection();
            // Retourne le contenu du type
            if (rrgId != null) {
                returnValue = dataProvider.getRRGInfo(c, ui.userId, rrgId);
                if (MysqlDataProvider.DATABASE_FALSE.equals(returnValue)) {
                    throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
                }
            }
            return returnValue;
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw ex;
        } catch (Exception ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Change a right in role
     * PUT /rest/api/rolerightsgroups/rolerightsgroup/{rolerightsgroup-id}
     * parameters:
     * return:
     **/
    @Path("/rolerightsgroups/rolerightsgroup/{rolerightsgroup-id}")
    @PUT
    @Produces(MediaType.APPLICATION_XML)
    public String putRightInfo(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group,
                               @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest, @PathParam("rolerightsgroup-id") Integer rrgId) {
        UserInfo ui = checkCredential(httpServletRequest, user, token, group);

        String returnValue = "";
        Connection c = null;
        try {
            c = SqlUtils.getConnection();
            // Retourne le contenu du type
            if (rrgId != null) {
                returnValue = dataProvider.putRRGUpdate(c, ui.userId, rrgId, xmlNode);
                if (MysqlDataProvider.DATABASE_FALSE.equals(returnValue)) {
                    throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
                }
            }
            return returnValue;
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
        } catch (Exception ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Add a role in the portfolio
     * POST /rest/api/rolerightsgroups/{portfolio-id}
     * parameters:
     * return:
     **/
    @Path("/rolerightsgroups/{portfolio-id}")
    @POST
    @Produces(MediaType.APPLICATION_XML)
    public String postRightGroups(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group,
                                  @PathParam("portfolio-id") String portfolio, @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest) {
        if (!isUUID(portfolio)) {
            logger.error("isUUID({})  is false", portfolio);
            throw new RestWebApplicationException(Status.BAD_REQUEST, "Not UUID");
        }
        UserInfo ui = checkCredential(httpServletRequest, user, token, group);

        /*
         * <node>LABEL</node>
         */

        String returnValue;
        Connection c = null;
        try {
            c = SqlUtils.getConnection();
            returnValue = dataProvider.postRRGCreate(c, ui.userId, portfolio, xmlNode);
            if (MysqlDataProvider.DATABASE_FALSE.equals(returnValue)) {
                throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
            }
            return returnValue;
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
        } catch (Exception ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Add user in a role
     * POST /rest/api/rolerightsgroups/rolerightsgroup/{rolerightsgroup-id}/users
     * parameters:
     * return:
     **/
    @Path("/rolerightsgroups/rolerightsgroup/{rolerightsgroup-id}/users")
    @POST
    @Produces(MediaType.APPLICATION_XML)
    public String postRightGroupUser(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group,
                                     @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest, @PathParam("rolerightsgroup-id") Integer rrgId) {
        UserInfo ui = checkCredential(httpServletRequest, user, token, group);
        String returnValue;
        Connection c = null;
        try {
            c = SqlUtils.getConnection();
            returnValue = dataProvider.postRRGUsers(c, ui.userId, rrgId, xmlNode);
            if (MysqlDataProvider.DATABASE_FALSE.equals(returnValue)) {
                throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
            }
            return returnValue;
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
        } catch (Exception ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Add user in a role
     * POST /rest/api/rolerightsgroups/rolerightsgroup/{rolerightsgroup-id}/users/user/{user-id}
     * parameters:
     * return:
     **/
    @Path("/rolerightsgroups/rolerightsgroup/{rolerightsgroup-id}/users/user/{user-id}")
    @POST
    @Produces(MediaType.APPLICATION_XML)
    public String postRightGroupUsers(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group,
                                      @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest, @PathParam("rolerightsgroup-id") Integer rrgId,
                                      @PathParam("user-id") Integer queryuser) {
        UserInfo ui = checkCredential(httpServletRequest, user, token, group);

        String returnValue;
        Connection c = null;
        try {
            c = SqlUtils.getConnection();
            returnValue = dataProvider.postRRGUser(c, ui.userId, rrgId, queryuser);
            if (MysqlDataProvider.DATABASE_FALSE.equals(returnValue)) {
                throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
            }
            return returnValue;
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
        } catch (Exception ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Delete a role
     * DELETE /rest/api/rolerightsgroups/rolerightsgroup/{rolerightsgroup-id}
     * parameters:
     * return:
     **/
    @Path("/rolerightsgroups/rolerightsgroup/{rolerightsgroup-id}")
    @DELETE
    @Produces(MediaType.APPLICATION_XML)
    public String deleteRightGroup(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group,
                                   @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest, @PathParam("rolerightsgroup-id") Integer rrgId) {
        UserInfo ui = checkCredential(httpServletRequest, user, token, group);

        String returnValue;
        Connection c = null;
        try {
            c = SqlUtils.getConnection();
            returnValue = dataProvider.deleteRRG(c, ui.userId, rrgId);
            if (MysqlDataProvider.DATABASE_FALSE.equals(returnValue)) {
                throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
            }
            return returnValue;
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
        } catch (Exception ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Remove user from a role
     * DELETE /rest/api/rolerightsgroups/rolerightsgroup/{rolerightsgroup-id}/users/user/{user-id}
     * parameters:
     * return:
     **/
    @Path("/rolerightsgroups/rolerightsgroup/{rolerightsgroup-id}/users/user/{user-id}")
    @DELETE
    @Produces(MediaType.APPLICATION_XML)
    public String deleteRightGroupUser(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group,
                                       @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest, @PathParam("rolerightsgroup-id") Integer rrgId,
                                       @PathParam("user-id") Integer queryuser) {
        UserInfo ui = checkCredential(httpServletRequest, user, token, group);

        String returnValue;
        Connection c = null;
        try {
            c = SqlUtils.getConnection();
            returnValue = dataProvider.deleteRRGUser(c, ui.userId, rrgId, queryuser);
            if (MysqlDataProvider.DATABASE_FALSE.equals(returnValue)) {
                throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
            }
            return returnValue;
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
        } catch (Exception ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Remove all users from a role
     * DELETE /rest/api/rolerightsgroups/all/users
     * parameters:
     * return:
     **/
    @Path("/rolerightsgroups/all/users")
    @DELETE
    @Produces(MediaType.APPLICATION_XML)
    public String deletePortfolioRightInfo(String xmlNode, @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group,
                                           @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest, @QueryParam("portfolio") String portId) {
        UserInfo ui = checkCredential(httpServletRequest, user, token, group);

        String returnValue = "";
        Connection c = null;
        try {
            c = SqlUtils.getConnection();
            // Retourne le contenu du type
            if (portId != null) {
                returnValue = dataProvider.deletePortfolioUser(c, ui.userId, portId);
                if (MysqlDataProvider.DATABASE_FALSE.equals(returnValue)) {
                    throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits d'acces");
                }

            }

            return returnValue;
        } catch (RestWebApplicationException ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.FORBIDDEN, "Vous n'avez pas les droits necessaires");
        } catch (Exception ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("Managed error", e);
            }
        }
    }

    /**
     * Ning related
     * GET /rest/api/ning/activities
     * parameters:
     * return:
     **/
    @Path("/ning/activities")
    @GET
    @Produces(MediaType.APPLICATION_XML)
    public String getNingActivities(@CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,
                                    @Context HttpServletRequest httpServletRequest, @QueryParam("type") Integer type) {
        checkCredential(httpServletRequest, user, token, group);

        Ning ning = new Ning();
        return ning.getXhtmlActivites();
    }

    /**
     * elgg related
     * GET /rest/api/elgg/site/river_feed
     * parameters:
     * return:
     **/
    @Path("/elgg/site/river_feed")
    @GET
    @Produces(MediaType.TEXT_HTML)
    public String getElggSiteRiverFeed(@CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group, @Context ServletConfig sc,
                                       @Context HttpServletRequest httpServletRequest, @QueryParam("type") Integer type, @QueryParam("limit") String limit) {
        int iLimit;
        try {
            iLimit = Integer.parseInt(limit);
        } catch (Exception ex) {
            iLimit = 20;
        }
        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        logger.info(ui.User);
        try {
            Elgg elgg = new Elgg(elggDefaultApiUrl, elggDefaultSiteUrl, elggApiKey, ui.User, elggDefaultUserPassword);
            return elgg.getSiteRiverFeed(iLimit);
        } catch (Exception ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        }
    }

    /**
     * elgg related
     * POST /rest/api/elgg/wire
     * parameters:
     * return:
     **/
    @Path("/elgg/wire")
    @POST
    @Produces(MediaType.APPLICATION_XML)
    public String getElggSiteRiverFeed(String message, @CookieParam("user") String user, @CookieParam("credential") String token, @CookieParam("group") String group,
                                       @Context ServletConfig sc, @Context HttpServletRequest httpServletRequest, @QueryParam("type") Integer type) {
        UserInfo ui = checkCredential(httpServletRequest, user, token, null);
        try {
            Elgg elgg = new Elgg(elggDefaultApiUrl, elggDefaultSiteUrl, elggApiKey, ui.User, elggDefaultUserPassword);
            return elgg.postWire(message);
        } catch (Exception ex) {
            logger.error("Managed error", ex);
            throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        }
    }

    @Path("/version")
    @GET
    @Produces({MediaType.APPLICATION_JSON})
    public String getVersion(@Context HttpServletRequest httpServletRequest) {
        Gson gson = new Gson();
        return gson.toJson(ConfigUtils.getInstance().getBuildInfo());
    }

    @Path("/fileserver-version")
    @GET
    @Produces({MediaType.APPLICATION_JSON})
    public String getFileServerVersion(@Context HttpServletRequest httpServletRequest) {
        Gson gson = new Gson();
        return gson.toJson(ConfigUtils.getInstance().getFileServerBuildinfo());
    }


    public boolean isUUID(String uuidstr) {
        try {
            UUID uuid = UUID.fromString(uuidstr);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

}