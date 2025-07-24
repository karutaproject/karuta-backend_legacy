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

package com.eportfolium.karuta.data.attachment;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.message.BasicHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.ls.DOMImplementationLS;
import org.xml.sax.InputSource;

import com.eportfolium.karuta.data.provider.DataProvider;
import com.eportfolium.karuta.data.utils.ConfigUtils;
import com.eportfolium.karuta.data.utils.HttpClientUtils;
import com.eportfolium.karuta.data.utils.SqlUtils;
import com.eportfolium.karuta.rest.RestWebApplicationException;
import com.eportfolium.karuta.security.Credential;
import com.google.gson.stream.JsonWriter;

import jakarta.ws.rs.core.Response.Status;

public class FileServlet extends HttpServlet {
	private static final long serialVersionUID = -4435511662030988745L;

	public static final String PROP_FILESERVER = "fileserver";

	private static final Logger logger = LoggerFactory.getLogger(FileServlet.class);

	private final Credential credential = new Credential();
	private final ArrayList<String> ourIPs = new ArrayList<>();
	private String server;
	private DataProvider dataProvider;

	@Override
	public void init(ServletConfig config) throws ServletException {

		try {
			super.init(config);

			ConfigUtils.init(config.getServletContext());

			dataProvider = SqlUtils.initProvider();
			/// List possible local address
			final var interfaces = NetworkInterface.getNetworkInterfaces();
			while (interfaces.hasMoreElements()) {
				final var current = interfaces.nextElement();
				if (!current.isUp()) {
					continue;
				}
				final var addresses = current.getInetAddresses();
				while (addresses.hasMoreElements()) {
					final var current_addr = addresses.nextElement();
					if (current_addr instanceof Inet4Address) {
						final var ip = current_addr.getHostAddress();
						logger.debug("USED IP: {}", ip);
						ourIPs.add(ip);
					}
				}
			}
			// Force localhost ip to be set, sometime it isn't listed
			//			ourIPs.add("127.0.0.1");

			server = ConfigUtils.getInstance().getRequiredProperty(PROP_FILESERVER);
		} catch (final Exception e) {
			logger.error("Unable to init the servlet", e);
			throw new ServletException("Unable to init the servlet", e);
		}

	}

	public void initialize(HttpServletRequest httpServletRequest) {
		//		  checkCredential(httpServletRequest);
	}

	@Override
	protected void doDelete(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		initialize(request);

		Connection c = null;
		try {
			c = SqlUtils.getConnection();

			var userId = 0;
			var groupId = 0;
			request.getContextPath();
			final var url = request.getPathInfo();

			final var session = request.getSession(true);
			if (session != null) {
				var val = (Integer) session.getAttribute("uid");
				if (val != null) {
					userId = val;
				}
				val = (Integer) session.getAttribute("gid");
				if (val != null) {
					groupId = val;
				}
			}

			new StringBuffer();
			new StringBuffer();
			response.setCharacterEncoding(StandardCharsets.UTF_8.toString());

			logger.info("FileServlet::doDelete: {} from user: {}", url, userId);
			// ====== URI : /resources/file[/{lang}]/{context-id}
			// ====== PathInfo: /resources/file[/{uuid}?lang={fr|en}&size={S|L}] pathInfo
			//			String uri = request.getRequestURI();
			final var token = url.split("/");
			if (token.length < 2) {
				response.setStatus(404);
				response.getOutputStream().close();
				return;
			}
			final var uuid = token[1];
			//wadbackend.WadUtilities.appendlogfile(logFName, "GETfile:"+request.getRemoteAddr()+":"+uri);

			/// FIXME: Passe la sécurité si la source provient de localhost, il faudrait un échange afin de s'assurer que n'importe quel servlet ne puisse y accéder
			final var sourceip = request.getRemoteAddr();

			/// Vérification des droits d'accés
			// TODO: Might be something special with proxy and export/PDF, to investigate

			if (!ourIPs.contains(sourceip)) {
				if (userId == 0) {
					logger.error("Forbidden access: no userID");
					throw new RestWebApplicationException(Status.FORBIDDEN, "");
				}

				if (!credential.hasNodeRight(c, userId, groupId, uuid, Credential.READ)) {
					logger.error("Forbidden access: no rights");
					response.sendError(HttpServletResponse.SC_FORBIDDEN);
					//throw new Exception("L'utilisateur userId="+userId+" n'a pas le droit READ sur le noeud "+nodeUuid);
				}
			} else // Si la requête est locale et qu'il n'y a pas de session, on ignore la vérification
			{
				logger.error("IP OK: bypass");
			}

			/// On récupère le noeud de la ressource pour retrouver le lien
			final var data = dataProvider.getResNode(c, uuid, userId, groupId);

			//			javax.servlet.http.HttpSession session = request.getSession(true);
			//====================================================
			//String ppath = session.getServletContext().getRealPath("/");
			//logFName = ppath +"logs/logNode.txt";
			//====================================================
			var size = request.getParameter("size");
			if (size == null) {
				size = "";
			}

			var lang = request.getParameter("lang");
			if (lang == null) {
				lang = "fr";
			}

			request.getHeader("referer");

			/// Parse les données
			final var documentBuilderFactory = DocumentBuilderFactory.newInstance();
			final var documentBuilder = documentBuilderFactory.newDocumentBuilder();
			final var is = new InputSource(new StringReader("<node>" + data + "</node>"));
			final var doc = documentBuilder.parse(is);
			final var impl = (DOMImplementationLS) doc.getImplementation().getFeature("LS", "3.0");
			final var serial = impl.createLSSerializer();
			serial.getDomConfig().setParameter("xml-declaration", false);

			/// Trouve le bon noeud
			final var xPath = XPathFactory.newInstance().newXPath();

			/// Either we have a fileid per language
			//			String filterRes = "//fileid[@lang='"+lang+"']";
			final var filterRes = "//*[local-name()='fileid' and @lang='" + lang + "']";
			final var nodelist = (NodeList) xPath.compile(filterRes).evaluate(doc, XPathConstants.NODESET);
			var resolve = "";
			if (nodelist.getLength() != 0) {
				final var fileNode = (Element) nodelist.item(0);
				resolve = fileNode.getTextContent();
			}

			/// Or just a single one shared
			if ("".equals(resolve)) {
				response.setStatus(404);
				response.getOutputStream().close();
				return;
			}

			logger.info("!!! RESOLVE: " + resolve);

			/// Envoie de la requête au servlet de fichiers
			// http://localhost:8080/MiniRestFileServer/user/claudecoulombe/file/a8e0f07f-671c-4f6a-be6c-9dba12c519cf/ptype/sql
			/// TODO: Ne plus avoir besoin du switch
			var urlTarget = server + "/" + resolve;

			if ("T".equals(size)) {
				urlTarget = urlTarget + "/thumb";
				//			String urlTarget = "http://"+ server + "/user/" + resolve +"/"+ lang + "/ptype/fs";
			}

			final var connection = CreateConnection(urlTarget, request);
			connection.connect();

			final var responseCode = connection.getResponseCode();
			response.setStatus(responseCode);

			connection.disconnect();
		} catch (final Exception e) {
			logger.error("Intercepted error", e);
			//TODO something is missing
		} //wadbackend.WadUtilities.appendlogfile(logFName, "GETfile: error"+e);
		finally {
			try {
				if (c != null) {
					c.close();
				}
			} catch (final Exception e) {
				final var out = response.getOutputStream();
				out.println("Erreur dans doDelete: " + e);
				out.close();
			}
			//				dataProvider.disconnect();
			request.getInputStream().close();
			response.getOutputStream().close();
		}
	}

	// =====================================================================================
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		initialize(request);

		try (var c = SqlUtils.getConnection();) {
			var userId = 0;
			var groupId = 0;
			final var session = request.getSession(true);
			if (session != null) {
				var val = (Integer) session.getAttribute("uid");
				if (val != null) {
					userId = val;
				}
				val = (Integer) session.getAttribute("gid");
				if (val != null) {
					groupId = val;
				}
			}

			final var url = request.getPathInfo();
			final var isRaw = request.getParameter("raw") != null ? true : false;

			/*
			Credential credential = null;
			try
			{
				//On initialise le dataProvider
				Connection c = null;
				if( ds == null )	// Case where we can't deploy context.xml
				{ c = SqlUtils.getConnection(servContext); }
				else
				{ c = ds.getConnection(); }
				dataProvider.setConnection(c);
				credential = new Credential(c);
			}
			catch(Exception e)
			{
				e.printStackTrace();
			}
			//*/

			response.setCharacterEncoding(StandardCharsets.UTF_8.toString());

			logger.info("FileServlet::doGet: {} from user: {}", url, userId);
			// ====== URI : /resources/file[/{lang}]/{context-id}
			// ====== PathInfo: /resources/file[/{uuid}?lang={fr|en}&size={S|L}] pathInfo
			//			String uri = request.getRequestURI();
			final var token = url.split("/");
			if (token.length < 2) {
				response.setStatus(404);
				response.getOutputStream().close();
				return;
			}
			final var uuid = token[1];
			//wadbackend.WadUtilities.appendlogfile(logFName, "GETfile:"+request.getRemoteAddr()+":"+uri);

			/// FIXME: Passe la sécurité si la source provient de localhost, il faudrait un échange afin de s'assurer que n'importe quel servlet ne puisse y accéder
			final var sourceip = request.getRemoteAddr();

			/// Vérification des droits d'accés
			// TODO: Might be something special with proxy and export/PDF, to investigate

			if (!ourIPs.contains(sourceip)) {
				if (userId == 0) {
					logger.error("Forbidden access: no userID");
					throw new RestWebApplicationException(Status.FORBIDDEN, "");
				}

				if (!credential.hasNodeRight(c, userId, groupId, uuid, Credential.READ)) {
					logger.error("Forbidden access: no rights");
					response.sendError(HttpServletResponse.SC_FORBIDDEN);
					//throw new Exception("L'utilisateur userId="+userId+" n'a pas le droit READ sur le noeud "+nodeUuid);
				}
			} else // Si la requête est locale et qu'il n'y a pas de session, on ignore la vérification
			{
				logger.error("IP OK: bypass");
			}

			/// On récupère le noeud de la ressource pour retrouver le lien
			final var data = dataProvider.getResNode(c, uuid, userId, groupId);

			//			javax.servlet.http.HttpSession session = request.getSession(true);
			//====================================================
			//String ppath = session.getServletContext().getRealPath("/");
			//logFName = ppath +"logs/logNode.txt";
			//====================================================
			var size = request.getParameter("size");
			if (size == null) {
				size = "";
			}

			var lang = request.getParameter("lang");
			if (lang == null) {
				lang = "fr";
			}

			/*
			String nodeUuid = uri.substring(uri.lastIndexOf("/")+1);
			if  (uri.lastIndexOf("/")>uri.indexOf("file/")+6) { // -- file/ = 5 carac. --
				lang = uri.substring(uri.indexOf("file/")+5,uri.lastIndexOf("/"));
			}
			//*/

			request.getHeader("referer");

			/// Parse les données
			final var documentBuilderFactory = DocumentBuilderFactory.newInstance();
			final var documentBuilder = documentBuilderFactory.newDocumentBuilder();
			final var is = new InputSource(new StringReader("<node>" + data + "</node>"));
			final var doc = documentBuilder.parse(is);
			final var impl = (DOMImplementationLS) doc.getImplementation().getFeature("LS", "3.0");
			final var serial = impl.createLSSerializer();
			serial.getDomConfig().setParameter("xml-declaration", false);

			/// Trouve le bon noeud
			final var xPath = XPathFactory.newInstance().newXPath();

			/// Either we have a fileid per language
			//			String filterRes = "//fileid[@lang='"+lang+"']";
			final var filterRes = "//*[local-name()='fileid' and @lang='" + lang + "']";
			final var nodelist = (NodeList) xPath.compile(filterRes).evaluate(doc, XPathConstants.NODESET);
			var resolve = "";
			if (nodelist.getLength() != 0) {
				final var fileNode = (Element) nodelist.item(0);
				resolve = fileNode.getTextContent();
			}

			/// Or just a single one shared
			if ("".equals(resolve)) {
				response.setStatus(404);
				response.getOutputStream().close();
				return;
			}

			//			String filterName = "//filename[@lang='"+lang+"']";
			final var filterName = "//*[local-name()='filename' and @lang='" + lang + "']";
			var textList = (NodeList) xPath.compile(filterName).evaluate(doc, XPathConstants.NODESET);
			var filename = "";
			if (textList.getLength() != 0) {
				final var fileNode = (Element) textList.item(0);
				filename = fileNode.getTextContent();
			}

			//			String filterType = "//type[@lang='"+lang+"']";
			final var filterType = "//*[local-name()='type' and @lang='" + lang + "']";
			textList = (NodeList) xPath.compile(filterType).evaluate(doc, XPathConstants.NODESET);
			var type = "";
			if (textList.getLength() != 0) {
				final var fileNode = (Element) textList.item(0);
				type = fileNode.getTextContent();
			}

			/*
			String filterSize = "//size[@lang='"+lang+"']";
			textList = (NodeList) xPath.compile(filterName).evaluate(doc, XPathConstants.NODESET);
			String filesize = "";
			if( textList.getLength() != 0 )
			{
				Element fileNode = (Element) textList.item(0);
				filesize = fileNode.getTextContent();
			}
			//*/

			logger.info("!!! RESOLVE: " + resolve);

			/// Envoie de la requête au servlet de fichiers
			// http://localhost:8080/MiniRestFileServer/user/claudecoulombe/file/a8e0f07f-671c-4f6a-be6c-9dba12c519cf/ptype/sql
			/// TODO: Ne plus avoir besoin du switch
			var urlTarget = server + "/" + resolve;

			if ("T".equals(size)) {
				urlTarget = urlTarget + "/thumb";
				//			String urlTarget = "http://"+ server + "/user/" + resolve +"/"+ lang + "/ptype/fs";
			}

			final var connection = CreateConnection(urlTarget, request);
			connection.connect();
			final var input = connection.getInputStream();
			final var sizeComplete = connection.getHeaderField("Content-Length");
			final var completeSize = Integer.parseInt(sizeComplete);

			response.setContentLength(completeSize);
			response.setContentType(type);
			if (!isRaw) {
				response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
			}
			final var output = response.getOutputStream();

			final var buffer = new byte[0x100000];
			var totalRead = 0;
			var bytesRead = -1;

			while ((bytesRead = input.read(buffer, 0, 0x100000)) != -1 || totalRead < completeSize) {
				output.write(buffer, 0, bytesRead);
				totalRead += bytesRead;
			}

			//			IOUtils.copy(input, output);
			//			IOUtils.closeQuietly(output);

			output.flush();
			output.close();
			input.close();
			connection.disconnect();
		} catch (final Exception e) {
			logger.error("Intercepted error", e);
			//TODO something is missing
		} //wadbackend.WadUtilities.appendlogfile(logFName, "GETfile: error"+e);
		finally {
			//				dataProvider.disconnect();
			request.getInputStream().close();
			response.getOutputStream().close();
		}
	}

	// =====================================================================================
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		// =====================================================================================
		initialize(request);

		final var useragent = request.getHeader("User-Agent");
		logger.info("Agent: " + useragent);

		Connection c = null;
		try {
			c = SqlUtils.getConnection();

			var userId = 0;
			var groupId = 0;
			var fromSakai = false;

			var doCopy = request.getParameter("copy");
			if (doCopy != null) {
				doCopy = "?copy";
			} else {
				doCopy = "";
			}

			final var session = request.getSession(false);
			if (session == null) {
				response.sendError(HttpServletResponse.SC_FORBIDDEN);
				return;
			}
			final var srceType = request.getParameter("srce");
			if ("sakai".equals(srceType)) {
				fromSakai = true;
			}

			var val = (Integer) session.getAttribute("uid");
			if (val != null) {
				userId = val;
			}
			val = (Integer) session.getAttribute("gid");
			if (val != null) {
				groupId = val;
			}

			/// uuid: celui de la ressource
			/// /resources/resource/file/{uuid}[?size=[S|L]&lang=[fr|en]]

			final var origin = request.getRequestURL().toString();

			/// Récupération des paramétres
			final var url = request.getPathInfo();
			final var token = url.split("/");
			final var uuid = token[1];

			var size = request.getParameter("size");
			if (size == null) {
				size = "S";
			}

			var lang = request.getParameter("lang");
			if (lang == null) {
				lang = "fr";
			}

			/// Vérification des droits d'accés
			if (!credential.hasNodeRight(c, userId, groupId, uuid, Credential.WRITE)) {
				response.sendError(HttpServletResponse.SC_FORBIDDEN);
				return;
				//throw new Exception("L'utilisateur userId="+userId+" n'a pas le droit WRITE sur le noeud "+nodeUuid);
			}

			String data;
			var fileid = "";

			data = dataProvider.getResNode(c, uuid, userId, groupId);

			/// Parse les données
			final var documentBuilderFactory = DocumentBuilderFactory.newInstance();
			final var documentBuilder = documentBuilderFactory.newDocumentBuilder();
			final var is = new InputSource(new StringReader("<node>" + data + "</node>"));
			final var doc = documentBuilder.parse(is);
			final var impl = (DOMImplementationLS) doc.getImplementation().getFeature("LS", "3.0");
			final var serial = impl.createLSSerializer();
			serial.getDomConfig().setParameter("xml-declaration", false);

			/// Cherche si on a déjà envoyé quelque chose
			final var xPath = XPathFactory.newInstance().newXPath();
			//			String filterRes = "//filename[@lang=\""+lang+"\"]";
			final var filterRes = "//*[local-name()='filename' and @lang='" + lang + "']";
			final var nodelist = (NodeList) xPath.compile(filterRes).evaluate(doc, XPathConstants.NODESET);

			if (nodelist.getLength() > 0) {
				nodelist.item(0).getTextContent();
			}

			/// écriture des données
			final var urlTarget = server + "/" + fileid + doCopy;
			//		String urlTarget = "http://"+ server + "/user/" + user +"/file/" + uuid +"/"+ lang+ "/ptype/fs";

			// Unpack form, fetch binary data and send
			// Create a factory for disk-based file items
			final var factory = new DiskFileItemFactory();

			// Create a new file upload handler
			final var upload = new ServletFileUpload(factory);

			var json = "";
			HttpURLConnection connection = null;
			// Parse the request
			InputStream inputData = null;
			var fileName = "";
			var filesize = 0L;
			var contentType = "";

			if (fromSakai) {
				final var sakai_session = (String) session.getAttribute("sakai_session");
				final var sakai_server = (String) session.getAttribute("sakai_server"); // Base server http://localhost:9090
				final var srceUrl = request.getParameter("srceurl");
				final Header header = new BasicHeader("JSESSIONID", sakai_session);
				final Set<Header> headers = new HashSet<>();
				headers.add(header);

				final HttpResponse get = HttpClientUtils.goGet(headers, sakai_server + "/" + srceUrl);
				if (get != null) {
					// Retrieve data
					inputData = get.getEntity().getContent();
					// File detail
					final var nameHeader = get.getHeaders("Content-Disposition")[0];
					final var sizeHeader = get.getHeaders("Content-Length")[0];
					final var typeHeader = get.getHeaders("Content-Type")[0];

					filesize = Integer.parseInt(sizeHeader.getValue());
					contentType = typeHeader.getValue();
					fileName = nameHeader.getValue().split("=")[1];
					if (fileName.startsWith("\"")) {
						fileName = fileName.substring(1, fileName.length() - 1);
					}
				}
			} else //				if( ServletFileUpload.isMultipartContent(request) )
			if (true) {
				final var items = upload.parseRequest(request);
				for (FileItem item : items) {
					if ("uploadfile".equals(item.getFieldName())) {
						// Send raw data
						inputData = item.getInputStream();

						fileName = item.getName();
						filesize = item.getSize();
						contentType = item.getContentType();

						break;
					}
				}
			} else {
				// List headers
				final Enumeration attributes = request.getAttributeNames();
				while (attributes.hasMoreElements()) {
					final var elem = attributes.nextElement();
					logger.error("Object: " + elem.toString());
				}
				logger.error("Not multipart");
				//TODO Something is missing
			}

			if (inputData != null) {
				connection = CreateConnection(urlTarget, request);
				connection.setRequestProperty("filename", uuid);
				connection.setRequestProperty("content-type", "application/octet-stream");
				connection.setRequestProperty("content-length", Long.toString(filesize));
				connection.connect();

				/// Send data to fileserver
				final var outputData = connection.getOutputStream();
				IOUtils.copy(inputData, outputData);

				/// Those 2 lines are needed, otherwise, no request sent
				final var code = connection.getResponseCode();
				final var msg = connection.getResponseMessage();

				if (code != HttpURLConnection.HTTP_OK) {
					logger.error("Couldn't send file: " + msg);
					response.sendError(code);
					return;
				}

				/// Retrieving info
				final var objReturn = connection.getInputStream();
				final var idResponse = new StringWriter();
				IOUtils.copy(objReturn, idResponse, StandardCharsets.UTF_8);
				fileid = idResponse.toString();

				connection.disconnect();

				/// Construct Json
				final var StringOutput = new StringWriter();
				final var writer = new JsonWriter(StringOutput);
				writer.beginObject();
				writer.name("files");
				writer.beginArray();
				writer.beginObject();

				writer.name("name").value(fileName);
				writer.name("size").value(filesize);
				writer.name("type").value(contentType);
				writer.name("url").value(origin);
				writer.name("fileid").value(fileid);
				//                               writer.name("deleteUrl").value(ref);
				//                                       writer.name("deleteType").value("DELETE");
				writer.endObject();

				writer.endArray();
				writer.endObject();

				writer.close();

				json = StringOutput.toString();
			}

			connection.disconnect();
			/// Renvoie le JSON au client
			if (useragent.contains("MSIE 9.0") || useragent.contains("MSIE 8.0") || useragent.contains("MSIE 7.0")) {
				response.setContentType("text/html");
			} else {
				response.setContentType("application/json");
			}
			final var respWriter = response.getWriter();
			respWriter.write(json);

			//		RetrieveAnswer(connection, response, ref);
			//		dataProvider.disconnect();
		} catch (final Exception e) {
			logger.error("Binary transfer error: " + e.getMessage() + "");
			//TODO something is missing
		} finally {
			try {
				if (c != null) {
					c.close();
				}
			} catch (final SQLException e) {
				logger.error("Intercepted error", e);
				//TODO something is missing
			}
		}
	}

	@Override
	protected void doPut(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		// =====================================================================================
		initialize(request);

		final var useragent = request.getHeader("User-Agent");
		logger.info("Agent: " + useragent);

		Connection c = null;
		try {
			c = SqlUtils.getConnection();

			var userId = 0;
			var groupId = 0;
			var fromSakai = false;

			var doCopy = request.getParameter("copy");
			if (doCopy != null) {
				doCopy = "?copy";
			} else {
				doCopy = "";
			}

			final var session = request.getSession(false);
			if (session == null) {
				logger.error("User is not authenticated");
				response.sendError(HttpServletResponse.SC_FORBIDDEN);
				return;
			}
			final var srceType = request.getParameter("srce");
			if ("sakai".equals(srceType)) {
				fromSakai = true;
			}

			var val = (Integer) session.getAttribute("uid");
			if (val != null) {
				userId = val;
			}
			val = (Integer) session.getAttribute("gid");
			if (val != null) {
				groupId = val;
			}

			/// uuid: celui de la ressource
			/// /resources/resource/file/{uuid}[?size=[S|L]&lang=[fr|en]]

			final var origin = request.getRequestURL().toString();

			/// Récupération des paramètres
			final var url = request.getPathInfo();
			final var token = url.split("/");
			final var uuid = token[1];

			var size = request.getParameter("size");
			if (size == null) {
				size = "S";
			}

			var lang = request.getParameter("lang");
			if (lang == null) {
				lang = "fr";
			}

			/// Vérification des droits d'accés
			if (!credential.hasNodeRight(c, userId, groupId, uuid, Credential.WRITE)) {
				logger.error("User is not authorized - userId: {}, groupId: {}, uuid: {}, WRITE", userId, groupId,
						uuid);
				response.sendError(HttpServletResponse.SC_FORBIDDEN);
				//throw new Exception("L'utilisateur userId="+userId+" n'a pas le droit WRITE sur le noeud "+nodeUuid);
			}

			String data;
			var fileid = "";

			data = dataProvider.getResNode(c, uuid, userId, groupId);

			/// Parse les données
			final var documentBuilderFactory = DocumentBuilderFactory.newInstance();
			final var documentBuilder = documentBuilderFactory.newDocumentBuilder();
			final var is = new InputSource(new StringReader("<node>" + data + "</node>"));
			final var doc = documentBuilder.parse(is);
			final var impl = (DOMImplementationLS) doc.getImplementation().getFeature("LS", "3.0");
			final var serial = impl.createLSSerializer();
			serial.getDomConfig().setParameter("xml-declaration", false);

			/// Cherche si on a déjà envoyé quelque chose
			final var xPath = XPathFactory.newInstance().newXPath();
			//			String filterRes = "//filename[@lang=\""+lang+"\"]";
			final var filterRes = "//*[local-name()='filename' and @lang='" + lang + "']";
			final var nodelist = (NodeList) xPath.compile(filterRes).evaluate(doc, XPathConstants.NODESET);

			var filename = "";
			if (nodelist.getLength() > 0) {
				filename = nodelist.item(0).getTextContent();
			}

			/// Ignore replacing file, just consider them all new one
			//			/*
			if (!"".equals(filename)) {
				/// Already have one, per language
				//				String filterId = "//fileid[@lang='"+lang+"']";
				final var filterId = "//*[local-name()='fileid' and @lang='" + lang + "']";
				final var idlist = (NodeList) xPath.compile(filterId).evaluate(doc, XPathConstants.NODESET);
				if (idlist.getLength() != 0) {
					final var fileNode = (Element) idlist.item(0);
					fileid = fileNode.getTextContent();
				}
			}

			var last = fileid.lastIndexOf("/") + 1; // FIXME temp patch
			if (last < 0) {
				last = 0;
			}
			fileid = fileid.substring(last);
			//*/

			/// écriture des données
			final var urlTarget = server + "/" + fileid + doCopy;
			//		String urlTarget = "http://"+ server + "/user/" + user +"/file/" + uuid +"/"+ lang+ "/ptype/fs";

			// Unpack form, fetch binary data and send
			// Create a factory for disk-based file items
			final var factory = new DiskFileItemFactory();

			// Create a new file upload handler
			final var upload = new ServletFileUpload(factory);

			var json = "";
			HttpURLConnection connection = null;
			// Parse the request
			InputStream inputData = null;
			var fileName = "";
			var filesize = 0L;
			var contentType = "";

			if (fromSakai) {
				final var sakai_session = (String) session.getAttribute("sakai_session");
				final var sakai_server = (String) session.getAttribute("sakai_server"); // Base server http://localhost:9090
				final var srceUrl = request.getParameter("srceurl");
				final Header header = new BasicHeader("JSESSIONID", sakai_session);
				final Set<Header> headers = new HashSet<>();
				headers.add(header);

				final HttpResponse get = HttpClientUtils.goGet(headers, sakai_server + "/" + srceUrl);
				if (get != null) {
					// Retrieve data
					inputData = get.getEntity().getContent();
					// File detail
					final var nameHeader = get.getHeaders("Content-Disposition")[0];
					final var sizeHeader = get.getHeaders("Content-Length")[0];
					final var typeHeader = get.getHeaders("Content-Type")[0];

					filesize = Integer.parseInt(sizeHeader.getValue());
					contentType = typeHeader.getValue();
					fileName = nameHeader.getValue().split("=")[1];
					if (fileName.startsWith("\"")) {
						fileName = fileName.substring(1, fileName.length() - 1);
					}
				}
			} else //				if( ServletFileUpload.isMultipartContent(request) )
			// TODO review this part, something should be removed or modified
			if (true) {
				final var items = upload.parseRequest(request);
				// Process the uploaded items
				for (final FileItem item : items) {
					if ("uploadfile".equals(item.getFieldName())) {
						// Send raw data
						inputData = item.getInputStream();

						fileName = item.getName();
						filesize = item.getSize();
						contentType = item.getContentType();

						break;
					}
				}
			} else {
				// List headers
				final Enumeration attributes = request.getAttributeNames();
				while (attributes.hasMoreElements()) {
					final var elem = attributes.nextElement();
					logger.error("Object: " + elem.toString());
					//TODO something is missing
				}
				logger.error("Not multipart");
				//TODO something is missing
			}

			if (inputData != null) {
				connection = CreateConnection(urlTarget, request);
				connection.setRequestProperty("filename", uuid);
				connection.setRequestProperty("content-type", "application/octet-stream");
				connection.setRequestProperty("content-length", Long.toString(filesize));
				connection.connect();

				/// Send data to fileserver
				final var outputData = connection.getOutputStream();
				IOUtils.copy(inputData, outputData);

				connection.getResponseCode();
				connection.getResponseMessage();

				/// Retrieving info
				final var objReturn = connection.getInputStream();
				final var idResponse = new StringWriter();
				IOUtils.copy(objReturn, idResponse, StandardCharsets.UTF_8);
				fileid = idResponse.toString();

				connection.disconnect();

				/// Construct Json
				final var StringOutput = new StringWriter();
				final var writer = new JsonWriter(StringOutput);
				writer.beginObject();
				writer.name("files");
				writer.beginArray();
				writer.beginObject();

				writer.name("name").value(fileName);
				writer.name("size").value(filesize);
				writer.name("type").value(contentType);
				writer.name("url").value(origin);
				writer.name("fileid").value(fileid);
				//                               writer.name("deleteUrl").value(ref);
				//                                       writer.name("deleteType").value("DELETE");
				writer.endObject();

				writer.endArray();
				writer.endObject();

				writer.close();

				json = StringOutput.toString();
			}

			connection.disconnect();
			/// Renvoie le JSON au client
			if (useragent.contains("MSIE 9.0") || useragent.contains("MSIE 8.0") || useragent.contains("MSIE 7.0")) {
				response.setContentType("text/html");
			} else {
				response.setContentType("application/json");
			}
			final var respWriter = response.getWriter();
			respWriter.write(json);

			//		RetrieveAnswer(connection, response, ref);
			//		dataProvider.disconnect();
		} catch (final Exception e) {
			logger.error("Binary transfer error", e);
			//TODO Something is missing
		} finally {
			try {
				if (c != null) {
					c.close();
				}
			} catch (final SQLException e) {
				logger.error("Intercepted error", e);
				//TODO something is missing
			}
		}
	}

	HttpURLConnection CreateConnection(String url, HttpServletRequest request)
			throws MalformedURLException, IOException {
		/// Create connection
		final var urlConn = new URL(url);
		final var connection = (HttpURLConnection) urlConn.openConnection();
		connection.setDoOutput(true);
		connection.setUseCaches(false); /// We don't want to cache data
		connection.setInstanceFollowRedirects(false); /// Let client follow any redirection
		final var method = request.getMethod();
		connection.setRequestMethod(method);

		final var context = request.getContextPath();
		connection.setRequestProperty("app", context);

		/// Transfer headers
		var key = "";
		var value = "";
		final var header = request.getHeaderNames();
		while (header.hasMoreElements()) {
			key = header.nextElement();
			value = request.getHeader(key);
			connection.setRequestProperty(key, value);
		}

		return connection;
	}

	void InitAnswer(HttpURLConnection connection, HttpServletResponse response, String referer)
			throws MalformedURLException, IOException {
		String ref = null;
		if (referer != null) {
			final var first = referer.indexOf('/', 7);
			final var last = referer.lastIndexOf('/');
			ref = referer.substring(first, last);
		}

		response.setContentType(connection.getContentType());
		response.setStatus(connection.getResponseCode());
		response.setContentLength(connection.getContentLength());

		/// Transfer headers
		final var headers = connection.getHeaderFields();
		final var size = headers.size();
		for (var i = 1; i < size; ++i) {
			final var key = connection.getHeaderFieldKey(i);
			final var value = connection.getHeaderField(i);
			//	      response.setHeader(key, value);
			response.addHeader(key, value);
		}

		/// Deal with correct path with set cookie
		final var setValues = headers.get("Set-Cookie");
		if (setValues != null) {
			var setVal = setValues.get(0);
			final var pathPlace = setVal.indexOf("Path=");
			if (pathPlace > 0) {
				setVal = setVal.substring(0, pathPlace + 5); // Some assumption, may break
				setVal = setVal + ref;

				response.setHeader("Set-Cookie", setVal);
			}
		}
	}

	// [username, ?]
	String[] processCookie(Cookie[] cookies) {
		String login = null;
		final String[] ret = { login };
		if (cookies == null) {
			return ret;
		}

		for (final Cookie cookie : cookies) {
			final var name = cookie.getName();
			if ("user".equals(name) || "useridentifier".equals(name)) {
				login = cookie.getValue();
			}
		}

		ret[0] = login;
		return ret;
	}

	void RetrieveAnswer(HttpURLConnection connection, HttpServletResponse response, String referer)
			throws MalformedURLException, IOException {
		/// Receive answer
		InputStream in;
		try {
			in = connection.getInputStream();
		} catch (final Exception e) {
			logger.error("Intercepted error", e);
			in = connection.getErrorStream();
			//TODO something is missing
		}

		InitAnswer(connection, response, referer);

		/// Write back data
		final var stream = new DataInputStream(in);
		final var buffer = new byte[1024];
		int size;
		ServletOutputStream out = null;
		try {
			out = response.getOutputStream();
			while ((size = stream.read(buffer, 0, buffer.length)) != -1) {
				out.write(buffer, 0, size);
			}

		} catch (final Exception e) {
			logger.error("Intercepted error - Writing messed up", e);
			//TODO something is missing
		} finally {
			in.close();
			out.flush(); // close() should flush already, but Tomcat 5.5 doesn't
			out.close();
		}
	}
}