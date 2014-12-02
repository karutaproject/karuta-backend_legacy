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

package com.portfolio.data.attachment;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.naming.InitialContext;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.sql.DataSource;
import javax.ws.rs.core.Response.Status;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.google.gson.stream.JsonWriter;
import com.portfolio.data.provider.DataProvider;
import com.portfolio.rest.RestWebApplicationException;
import com.portfolio.security.Credential;

public class FileServlet  extends HttpServlet
{
	final Logger logger = LoggerFactory.getLogger(FileServlet.class);

//	DataProvider dataProvider = null;
	boolean hasNodeReadRight = false;
	boolean hasNodeWriteRight = false;
//	Credential credential;

	private String server = "";
	private String backend = "";
	ServletContext servContext;
	DataSource ds;
	DataProvider dataProvider;
	ArrayList<String> ourIPs = new ArrayList<String>();

	@Override
	public void init( ServletConfig config )
	{
		/// List possible local address
		try
		{
			Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
			while (interfaces.hasMoreElements()){
				NetworkInterface current = interfaces.nextElement();
				if (!current.isUp() || current.isLoopback() || current.isVirtual()) continue;
				Enumeration<InetAddress> addresses = current.getInetAddresses();
				while (addresses.hasMoreElements()){
					InetAddress current_addr = addresses.nextElement();
					if (current_addr instanceof Inet4Address)
						ourIPs.add(current_addr.getHostAddress());
				}
			}
		}
		catch( Exception e )
		{
		}

		servContext = config.getServletContext();
		backend = config.getServletContext().getInitParameter("backendserver");
		server = config.getServletContext().getInitParameter("fileserver");
		try
		{
			String dataProviderName  =  config.getInitParameter("dataProviderClass");
			dataProvider = (DataProvider)Class.forName(dataProviderName).newInstance();

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
			e.printStackTrace();
		}
	}

	public void initialize(HttpServletRequest httpServletRequest)
	{
			//		  checkCredential(httpServletRequest);
	}

	@Override
	protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		doPost(request, response);
	}

	// =====================================================================================
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		// =====================================================================================
		initialize(request);

		int userId = 0;
		int groupId = 0;
		String user = "";

		HttpSession session = request.getSession(true);
		if( session != null )
		{
			Integer val = (Integer) session.getAttribute("uid");
			if( val != null )
				userId = val;
			val = (Integer) session.getAttribute("gid");
			if( val != null )
				groupId = val;
			user = (String) session.getAttribute("user");
		}

		Credential credential = null;
		Connection c = null;
		try
		{
			//On initialise le dataProvider
			if( ds == null )	// Case where we can't deploy context.xml
			{ c = getConnection(); }
			else
			{ c = ds.getConnection(); }
			dataProvider.setConnection(c);
			credential = new Credential(c);
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}


		/// uuid: celui de la ressource

		/// /resources/resource/file/{uuid}[?size=[S|L]&lang=[fr|en]]

		String origin = request.getRequestURL().toString();

		/// Récupération des paramètres
		String url = request.getPathInfo();
		String[] token = url.split("/");
		String uuid = token[1];

		String size = request.getParameter("size");
		if(size == null)
			size = "S";

		String lang = request.getParameter("lang");
		if (lang==null){
			lang = "fr";
		}

		/// Vérification des droits d'accès
		if(!credential.hasNodeRight(userId, groupId, uuid, Credential.WRITE))
		{
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			//throw new Exception("L'utilisateur userId="+userId+" n'a pas le droit WRITE sur le noeud "+nodeUuid);
		}

		String data;
		String fileid = "";
		try
		{
			data = dataProvider.getResNode(uuid, userId, groupId);

			/// Parse les données
			DocumentBuilderFactory documentBuilderFactory =DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
			InputSource is = new InputSource(new StringReader("<node>"+data+"</node>"));
			Document doc = documentBuilder.parse(is);
			DOMImplementationLS impl = (DOMImplementationLS)doc.getImplementation().getFeature("LS", "3.0");
			LSSerializer serial = impl.createLSSerializer();
			serial.getDomConfig().setParameter("xml-declaration", false);

			/// Cherche si on a déjà envoyé quelque chose
			XPath xPath = XPathFactory.newInstance().newXPath();
			String filterRes = "//filename[@lang=\""+lang+"\"]";
			NodeList nodelist = (NodeList) xPath.compile(filterRes).evaluate(doc, XPathConstants.NODESET);

			String filename = "";
			if( nodelist.getLength() > 0 )
				filename = nodelist.item(0).getTextContent();

			if( !"".equals(filename) )
			{
				/// Already have one, per language
				String filterId = "//fileid[@lang='"+lang+"']";
				NodeList idlist = (NodeList) xPath.compile(filterId).evaluate(doc, XPathConstants.NODESET);
				if( idlist.getLength() != 0 )
				{
					Element fileNode = (Element) idlist.item(0);
					fileid = fileNode.getTextContent();
				}
			}
		}
		catch( Exception e2 )
		{
			e2.printStackTrace();
		}

		int last = fileid.lastIndexOf("/") +1;	// FIXME temp patch
		if( last < 0 )
			last = 0;
		fileid = fileid.substring(last);
		/// request.getHeader("REFERRER");

		/// écriture des données
		String urlTarget = "http://"+ server + "/" + fileid;
//		String urlTarget = "http://"+ server + "/user/" + user +"/file/" + uuid +"/"+ lang+ "/ptype/fs";

		// Unpack form, fetch binary data and send
	// Create a factory for disk-based file items
		DiskFileItemFactory factory = new DiskFileItemFactory();

		// Configure a repository (to ensure a secure temp location is used)
		/*
		ServletContext servletContext = this.getServletConfig().getServletContext();
		File repository = (File) servletContext.getAttribute("javax.servlet.context.tempdir");
		factory.setRepository(repository);
		//*/

		// Create a new file upload handler
		ServletFileUpload upload = new ServletFileUpload(factory);

		String json = "";
		HttpURLConnection connection=null;
		// Parse the request
		try
		{
			List<FileItem> items = upload.parseRequest(request);
		// Process the uploaded items
			Iterator<FileItem> iter = items.iterator();
			while (iter.hasNext())
			{
				FileItem item = iter.next();

				if ("uploadfile".equals(item.getFieldName()))
				{
					// Send raw data
					InputStream inputData = item.getInputStream();

					/*
					URL urlConn = new URL(urlTarget);
					connection = (HttpURLConnection) urlConn.openConnection();
					connection.setDoOutput(true);
					connection.setUseCaches(false);                 /// We don't want to cache data
					connection.setInstanceFollowRedirects(false);   /// Let client follow any redirection
					String method = request.getMethod();
					connection.setRequestMethod(method);

					String context = request.getContextPath();
					connection.setRequestProperty("app", context);
					//*/

					String fileName = item.getName();
					long filesize = item.getSize();
					String contentType = item.getContentType();

//					/*
					connection = CreateConnection( urlTarget, request );
					connection.setRequestProperty("filename",uuid);
					connection.setRequestProperty("content-type", "application/octet-stream");
					connection.setRequestProperty("content-length", Long.toString(filesize));
					//*/
					connection.connect();

					OutputStream outputData = connection.getOutputStream();
					IOUtils.copy(inputData, outputData);

					/// Those 2 lines are needed, otherwise, no request sent
					int code = connection.getResponseCode();
					String msg = connection.getResponseMessage();

					InputStream objReturn = connection.getInputStream();
					StringWriter idResponse = new StringWriter();
					IOUtils.copy(objReturn, idResponse);
					fileid = idResponse.toString();

					connection.disconnect();

					/// Construct Json
					StringWriter StringOutput = new StringWriter();
					JsonWriter writer = new JsonWriter(StringOutput);
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


					/*
					DataOutputStream datawriter = new DataOutputStream(connection.getOutputStream());
					byte[] buffer = new byte[1024];
					int dataSize;
					while( (dataSize = inputData.read(buffer,0,buffer.length)) != -1 )
					{
						datawriter.write(buffer, 0, dataSize);
					}
					datawriter.flush();
					datawriter.close();
					//*/
//					outputData.close();
//					inputData.close();

					break;
				}
			}
		}
		catch( FileUploadException e1 )
		{
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		/*
		HttpURLConnection connection = CreateConnection( urlTarget, request );

		connection.setRequestProperty("referer", origin);

		/// Send post data
		ServletInputStream inputData = request.getInputStream();
		DataOutputStream writer = new DataOutputStream(connection.getOutputStream());

		byte[] buffer = new byte[1024];
		int dataSize;
		while( (dataSize = inputData.read(buffer,0,buffer.length)) != -1 )
		{
			writer.write(buffer, 0, dataSize);
		}
		inputData.close();
		writer.close();

		/// So we can forward some Set-Cookie
		String ref = request.getHeader("referer");

		/// Prend le JSON du fileserver
		InputStream in = connection.getInputStream();

		InitAnswer(connection, response, ref);

		BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
		StringBuilder builder = new StringBuilder();
		for( String line = null; (line = reader.readLine()) != null; )
			builder.append(line).append("\n");
		//*/

		/// Envoie la mise à jour au backend
		/*
		try
		{
			PostForm.updateResource(session.getId(), backend, uuid, lang, json);
		}
		catch( Exception e )
		{
			e.printStackTrace();
		}
		//*/

		connection.disconnect();
		/// Renvoie le JSON au client
		response.setContentType("application/json");
		PrintWriter respWriter = response.getWriter();
		respWriter.write(json);

//		RetrieveAnswer(connection, response, ref);
		dataProvider.disconnect();
	}

	// =====================================================================================
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		initialize(request);

		int userId = 0;
		int groupId = 0;
		String user = "";
		String context = request.getContextPath();
		String url = request.getPathInfo();

		HttpSession session = request.getSession(true);
		if( session != null )
		{
			Integer val = (Integer) session.getAttribute("uid");
			if( val != null )
				userId = val;
			val = (Integer) session.getAttribute("gid");
			if( val != null )
				groupId = val;
			user = (String) session.getAttribute("user");
		}

		Credential credential = null;
		try
		{
			//On initialise le dataProvider
			Connection c = null;
			if( ds == null )	// Case where we can't deploy context.xml
			{ c = getConnection(); }
			else
			{ c = ds.getConnection(); }
			dataProvider.setConnection(c);
			credential = new Credential(c);
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}

		// =====================================================================================
		boolean trace = false;
		StringBuffer outTrace = new StringBuffer();
		StringBuffer outPrint = new StringBuffer();
		String logFName = null;

		response.setCharacterEncoding("UTF-8");

		System.out.println("FileServlet::doGet");
		try{
			// ====== URI : /resources/file[/{lang}]/{context-id}
			// ====== PathInfo: /resources/file[/{uuid}?lang={fr|en}&size={S|L}] pathInfo
			//			String uri = request.getRequestURI();
			String[] token = url.split("/");
			String uuid = token[1];
			//wadbackend.WadUtilities.appendlogfile(logFName, "GETfile:"+request.getRemoteAddr()+":"+uri);

			/// FIXME: Passe la sécurité si la source provient de localhost, il faudrait un échange afin de s'assurer que n'importe quel servlet ne puisse y accéder
			String sourceip = request.getRemoteAddr();
			System.out.println("IP: "+sourceip);

			/// Vérification des droits d'accès
			// TODO: Might be something special with proxy and export/PDF, to investigate
			if( !ourIPs.contains(sourceip) )
			{
				if( userId == 0 )
					throw new RestWebApplicationException(Status.FORBIDDEN, "");

				if(!credential.hasNodeRight(userId, groupId, uuid, Credential.READ))
				{
					response.sendError(HttpServletResponse.SC_FORBIDDEN);
					//throw new Exception("L'utilisateur userId="+userId+" n'a pas le droit READ sur le noeud "+nodeUuid);
				}
			}
			else	// Si la requête est locale et qu'il n'y a pas de session, on ignore la vérification
			{
				System.out.println("IP OK: bypass");
			}

			/// On récupère le noeud de la ressource pour retrouver le lien
			String data = dataProvider.getResNode(uuid, userId, groupId);


			//			javax.servlet.http.HttpSession session = request.getSession(true);
			//====================================================
			//String ppath = session.getServletContext().getRealPath("/");
			//logFName = ppath +"logs/logNode.txt";
			//====================================================
			String size = request.getParameter("size");
			if(size == null)
				size = "S";

			String lang = request.getParameter("lang");
			if (lang==null){
				lang = "fr";
			}

			/*
			String nodeUuid = uri.substring(uri.lastIndexOf("/")+1);
			if  (uri.lastIndexOf("/")>uri.indexOf("file/")+6) { // -- file/ = 5 carac. --
				lang = uri.substring(uri.indexOf("file/")+5,uri.lastIndexOf("/"));
			}
			//*/

			String ref = request.getHeader("referer");

			/// Parse les données
			DocumentBuilderFactory documentBuilderFactory =DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
			InputSource is = new InputSource(new StringReader("<node>"+data+"</node>"));
			Document doc = documentBuilder.parse(is);
			DOMImplementationLS impl = (DOMImplementationLS)doc.getImplementation().getFeature("LS", "3.0");
			LSSerializer serial = impl.createLSSerializer();
			serial.getDomConfig().setParameter("xml-declaration", false);

			/// Trouve le bon noeud
			XPath xPath = XPathFactory.newInstance().newXPath();

			/// Either we have a fileid per language
			String filterRes = "//fileid[@lang='"+lang+"']";
			NodeList nodelist = (NodeList) xPath.compile(filterRes).evaluate(doc, XPathConstants.NODESET);
			String resolve = "";
			if( nodelist.getLength() != 0 )
			{
				Element fileNode = (Element) nodelist.item(0);
				resolve = fileNode.getTextContent();
			}

			/// Or just a single one shared
			if( "".equals(resolve) )
			{
				response.setStatus(404);
				return;
			}

			String filterName = "//filename[@lang='"+lang+"']";
			NodeList textList = (NodeList) xPath.compile(filterName).evaluate(doc, XPathConstants.NODESET);
			String filename = "";
			if( textList.getLength() != 0 )
			{
				Element fileNode = (Element) textList.item(0);
				filename = fileNode.getTextContent();
			}

			String filterType = "//type[@lang='"+lang+"']";
			textList = (NodeList) xPath.compile(filterType).evaluate(doc, XPathConstants.NODESET);
			String type = "";
			if( textList.getLength() != 0 )
			{
				Element fileNode = (Element) textList.item(0);
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

			System.out.println("!!! RESOLVE: "+resolve);

			/// Envoie de la requête au servlet de fichiers
			// http://localhost:8080/MiniRestFileServer/user/claudecoulombe/file/a8e0f07f-671c-4f6a-be6c-9dba12c519cf/ptype/sql
			/// TODO: Ne plus avoir besoin du switch
			String urlTarget = "http://"+ server + "/" + resolve;
//			String urlTarget = "http://"+ server + "/user/" + resolve +"/"+ lang + "/ptype/fs";

			HttpURLConnection connection = CreateConnection( urlTarget, request );
			connection.connect();
			InputStream input = connection.getInputStream();
			String sizeComplete = connection.getHeaderField("Content-Length");
			int completeSize = Integer.parseInt(sizeComplete);

			response.setContentLength(completeSize);
			response.setContentType(type);
			response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
			ServletOutputStream output = response.getOutputStream();

			byte[] buffer = new byte[completeSize];
			int totalRead = 0;
			int bytesRead = -1;

			while ((bytesRead = input.read(buffer,0,completeSize)) != -1 || totalRead < completeSize) {
				output.write(buffer, 0, bytesRead);
				totalRead += bytesRead;
			}

//			IOUtils.copy(input, output);
//			IOUtils.closeQuietly(output);

			output.flush();
			output.close();
			input.close();
			connection.disconnect();
		}
		catch( RestWebApplicationException e )
		{
			logger.error(e.getMessage());
			e.printStackTrace();
		}
		catch(Exception e){
			logger.error(e.toString()+" -> "+e.getLocalizedMessage());
			e.printStackTrace();
			//wadbackend.WadUtilities.appendlogfile(logFName, "GETfile: error"+e);
		}
		finally
		{
			try{
				dataProvider.disconnect();
			}
			catch(Exception e){
				ServletOutputStream out = response.getOutputStream();
				out.println("Erreur dans doGet: " +e);
				out.close();
			}
		}
	}

	HttpURLConnection CreateConnection( String url, HttpServletRequest request ) throws MalformedURLException, IOException
	{
		/// Create connection
		URL urlConn = new URL(url);
		HttpURLConnection connection = (HttpURLConnection) urlConn.openConnection();
		connection.setDoOutput(true);
		connection.setUseCaches(false);                 /// We don't want to cache data
		connection.setInstanceFollowRedirects(false);   /// Let client follow any redirection
		String method = request.getMethod();
		connection.setRequestMethod(method);

		String context = request.getContextPath();
		connection.setRequestProperty("app", context);

		/// Transfer headers
		String key = "";
		String value = "";
		Enumeration<String> header = request.getHeaderNames();
		while( header.hasMoreElements() )
		{
			key = header.nextElement();
			value = request.getHeader(key);
			connection.setRequestProperty(key, value);
		}

		return connection;
	}

	void InitAnswer( HttpURLConnection connection, HttpServletResponse response, String referer ) throws MalformedURLException, IOException
	{
		String ref = null;
		if( referer != null )
		{
			int first = referer.indexOf('/', 7);
			int last = referer.lastIndexOf('/');
			ref = referer.substring(first, last);
		}

		response.setContentType(connection.getContentType());
		response.setStatus(connection.getResponseCode());
		response.setContentLength(connection.getContentLength());

		/// Transfer headers
		Map<String, List<String>> headers = connection.getHeaderFields();
		int size=headers.size();
		for( int i=1; i<size; ++i )
		{
			String key = connection.getHeaderFieldKey(i);
			String value = connection.getHeaderField(i);
			//	      response.setHeader(key, value);
			response.addHeader(key, value);
		}

		/// Deal with correct path with set cookie
		List<String> setValues = headers.get("Set-Cookie");
		if( setValues != null )
		{
			String setVal = setValues.get(0);
			int pathPlace = setVal.indexOf("Path=");
			if( pathPlace > 0 )
			{
				setVal = setVal.substring(0, pathPlace+5);  // Some assumption, may break
				setVal = setVal+ref;

				response.setHeader("Set-Cookie", setVal);
			}
		}
	}

	void RetrieveAnswer( HttpURLConnection connection, HttpServletResponse response, String referer ) throws MalformedURLException, IOException
	{
		/// Receive answer
		InputStream in;
		try
		{
			in = connection.getInputStream();
		}
		catch (Exception e)
		{
			System.out.println(e.toString());
			in = connection.getErrorStream();
		}

		InitAnswer(connection, response, referer);

		/// Write back data
		DataInputStream stream = new DataInputStream(in);
		byte[] buffer = new byte[1024];
		int size;
		ServletOutputStream out=null;
		try
		{
			out = response.getOutputStream();
			while( (size = stream.read(buffer,0,buffer.length)) != -1 )
				out.write(buffer, 0, size);

		}
		catch (Exception e)
		{
			System.out.println(e.toString());
			System.out.println("Writing messed up!");
		}
		finally
		{
			in.close();
			out.flush();  // close() should flush already, but Tomcat 5.5 doesn't
			out.close();
		}
	}

	/// Horrible duplicate, make it shared somehow
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

	// [username, ?]
	String[] processCookie( Cookie[] cookies )
	{
		String login=null;
		String[] ret = {login};
		if( cookies == null ) return ret;

		for( int i=0; i<cookies.length; ++i )
		{
			Cookie cookie = cookies[i];
			String name = cookie.getName();
			if( "user".equals(name) || "useridentifier".equals(name) )
				login = cookie.getValue();
		}

		ret[0] = login;
		return ret;
	}
}

