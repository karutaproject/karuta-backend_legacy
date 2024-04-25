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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import javax.activation.MimeType;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.IOUtils;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.MimeConstants;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.eportfolium.karuta.data.provider.DataProvider;
import com.eportfolium.karuta.data.utils.ConfigUtils;
import com.eportfolium.karuta.data.utils.SqlUtils;

public class XSLService extends HttpServlet {

	/**
	 *
	 */
	private static final long serialVersionUID = 9188067506635747901L;
	private static final Logger logger = LoggerFactory.getLogger(XSLService.class);

	DataProvider dataProvider;
	boolean hasNodeReadRight = false;
	boolean hasNodeWriteRight = false;
	ServletContext sc;
	String context = "";

	private String server;
	String baseDir;
	String servletDir;
	String internalServer;

	private TransformerFactory transFactory;
	private FopFactory fopFactory;

	HttpURLConnection CreateConnection(String url, HttpServletRequest request)
			throws MalformedURLException, IOException {
		/// Create connection
		final URL urlConn = new URL(url);
		final HttpURLConnection connection = (HttpURLConnection) urlConn.openConnection();
		connection.setDoOutput(true);
		connection.setUseCaches(false); /// We don't want to cache data
		connection.setInstanceFollowRedirects(false); /// Let client follow any redirection
		final String method = request.getMethod();
		connection.setRequestMethod(method);

		final String context = request.getContextPath();
		connection.setRequestProperty("app", context);

		/// Transfer headers
		String key = "";
		String value = "";
		final Enumeration<String> header = request.getHeaderNames();
		while (header.hasMoreElements()) {
			key = header.nextElement();
			value = request.getHeader(key);
			connection.setRequestProperty(key, value);
		}

		return connection;
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
		/**
		 * Format demandé: <convert> <portfolioid>{uuid}</portfolioid>
		 * <portfolioid>{uuid}</portfolioid> <nodeid>{uuid}</nodeid>
		 * <nodeid>{uuid}</nodeid> <documentid>{uuid}</documentid>
		 * <xsl>{répertoire}{fichier}</xsl> <format>[pdf rtf xml ...]</format>
		 * <parameters> <maVar1>lala</maVar1> ... </parameters> </convert>
		 */
		Connection c = null;
		try {
			c = SqlUtils.getConnection();

			final String origin = request.getRequestURL().toString();
			logger.trace("Is connection null {}", c);

			/// Variable stuff
			int userId = 0;
			int groupId = 0;
			final HttpSession session = request.getSession(true);
			if (session != null) {
				Integer val = (Integer) session.getAttribute("uid");
				if (val != null) {
					userId = val;
				}
				val = (Integer) session.getAttribute("gid");
				if (val != null) {
					groupId = val;
				}
			}

			/// TODO: A voire si un form get ne ferait pas l'affaire aussi

			/// On lis le xml
			/*
			BufferedReader rd = new BufferedReader(new InputStreamReader(request.getInputStream()));
			StringBuilder sb = new StringBuilder();
			String line;
			while( (line = rd.readLine()) != null )
				sb.append(line);
			
			DocumentBuilderFactory documentBuilderFactory =DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder = null;
			Document doc=null;
			try
			{
				documentBuilder = documentBuilderFactory.newDocumentBuilder();
				doc = documentBuilder.parse(new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8)));
			}
			catch( Exception e )
			{
				e.printStackTrace();
			}
			
			/// On lit les paramètres
			NodeList portfolioNode = doc.getElementsByTagName("portfolioid");
			NodeList nodeNode = doc.getElementsByTagName("nodeid");
			NodeList documentNode = doc.getElementsByTagName("documentid");
			NodeList xslNode = doc.getElementsByTagName("xsl");
			NodeList formatNode = doc.getElementsByTagName("format");
			NodeList parametersNode = doc.getElementsByTagName("parameters");
			//*/
			//		String xslfile = xslNode.item(0).getTextContent();
			final String xslfile = request.getParameter("xsl");
			final String format = request.getParameter("format");
			//		String format = formatNode.item(0).getTextContent();
			String parameters = request.getParameter("parameters");
			final String documentid = request.getParameter("documentid");
			final String portfolios = request.getParameter("portfolioids");
			String[] portfolioid = null;
			if (portfolios != null) {
				portfolioid = portfolios.split(";");
			}
			final String nodes = request.getParameter("nodeids");
			String[] nodeid = null;
			if (nodes != null) {
				nodeid = nodes.split(";");
			}

			parameters = parameters + ";urlimage:" + internalServer;
			logger.info("====== PARAMETERS: =======");
			logger.info("xsl: {}", xslfile);
			logger.info("format: {}", format);
			logger.info("user: {}", userId);
			logger.info("portfolioids: {}", portfolios);
			logger.info("nodeids: {}", nodes);
			logger.info("parameters: {}", parameters);

			boolean redirectDoc = false;
			if (documentid != null) {
				redirectDoc = true;
				logger.trace("documentid @ {}", documentid);
			}

			boolean usefop = false;
			String ext = "";
			if (MimeConstants.MIME_PDF.equals(format)) {
				usefop = true;
				ext = ".pdf";
			} else if (MimeConstants.MIME_RTF.equals(format)) {
				usefop = true;
				ext = ".rtf";
			}
			//// Paramètre portfolio-uuid et file-xsl
			//		String uuid = request.getParameter("uuid");
			//		String xslfile = request.getParameter("xsl");

			final StringBuilder aggregate = new StringBuilder();
			// On aggrège les données
			if (portfolioid != null) {
				for (final String p : portfolioid) {
					final String portfolioxml = dataProvider
							.getPortfolio(c, new MimeType("text/xml"), p, userId, groupId, "", null, null, 0, null)
							.toString();
					aggregate.append(portfolioxml);
				}
			}

			if (nodeid != null) {
				for (final String n : nodeid) {
					final String nodexml = dataProvider
							.getNode(c, new MimeType("text/xml"), n, true, userId, groupId, null, "", null).toString();
					aggregate.append(nodexml);
				}
			}

			// Est-ce qu'on a eu besoin d'aggr�ger les donn�es?
			String input = aggregate.toString();
			final String pattern = "<\\?xml[^>]*>"; // Purge previous xml declaration

			input = input.replaceAll(pattern, "");

			input = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
					"<!DOCTYPE xsl:stylesheet [" +
					"<!ENTITY % lat1 PUBLIC \"-//W3C//ENTITIES Latin 1 for XHTML//EN\" \"" +
					servletDir +
					"xhtml-lat1.ent\">" +
					"<!ENTITY % symbol PUBLIC \"-//W3C//ENTITIES Symbols for XHTML//EN\" \"" +
					servletDir +
					"xhtml-symbol.ent\">" +
					"<!ENTITY % special PUBLIC \"-//W3C//ENTITIES Special for XHTML//EN\" \"" +
					servletDir +
					"xhtml-special.ent\">" +
					"%lat1;" +
					"%symbol;" +
					"%special;" +
					"]>" + // For the pesky special characters
					"<root>" +
					input +
					"</root>";

			logger.trace("INPUT WITH PROXY: {}", input);

			/// Résolution des proxys
			DocumentBuilder documentBuilder;
			final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
			documentBuilder = documentBuilderFactory.newDocumentBuilder();
			InputStream is = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
			//			InputSource is = new InputSource(new StringReader(input));
			final Document doc = documentBuilder.parse(is);

			/// Proxy stuff
			final XPath xPath = XPathFactory.newInstance().newXPath();
			//			String filterRes = "//asmResource[@xsi_type='Proxy']";
			final String filterRes = "//*[local-name()='asmResource' and @*[local-name()='xsi_type' and .='Proxy']]";
			//			NodeList nodelist = XPathAPI.selectNodeList(doc.getDocumentElement(), filterRes);
			//			String filterCode = "./code/text()";
			final String filterCode = "./*[local-name()='code']/text()";
			final NodeList nodelist = (NodeList) xPath.compile(filterRes).evaluate(doc, XPathConstants.NODESET);

			final XPathExpression codeFilter = xPath.compile(filterCode);

			for (int i = 0; i < nodelist.getLength(); ++i) {
				final Node res = nodelist.item(i);
				final Node gp = res.getParentNode(); // resource -> context -> container
				final Node ggp = gp.getParentNode();

				final Node uuid = (Node) codeFilter.evaluate(res, XPathConstants.NODE);
				//				Node uuid = XPathAPI.selectSingleNode(res, filterCode);

				/// Fetch node we want to replace
				final String returnValue = dataProvider.getNode(c, new MimeType("text/xml"), uuid.getTextContent(),
						true, userId, groupId, null, "", null).toString();
				if (returnValue == null) {
					continue;
				}

				is = new ByteArrayInputStream(returnValue.getBytes(StandardCharsets.UTF_8));
				final Document rep = documentBuilder.parse(is);
				//				Element repNode = rep.getDocumentElement();
				Node proxyNode = rep.getDocumentElement();
				//				Node proxyNode = repNode.getFirstChild();
				//				logger.error("REPLACEMENT: "+proxyNode);
				proxyNode = doc.importNode(proxyNode, true); // adoptNode have some weird side effect. To be banned
				//				doc.replaceChild(proxyNode, gp);
				//				logger.error("BEFORE: "+gp);
				ggp.insertBefore(proxyNode, gp); // replaceChild doesn't work.
				//				logger.error("INSIDE: "+ggp);
				ggp.removeChild(gp);
			}

			// Convert XML document to string
			final DOMSource domSource = new DOMSource(doc);
			final StringWriter writer = new StringWriter();
			final StreamResult result = new StreamResult(writer);
			final TransformerFactory tf = TransformerFactory.newInstance();
			final Transformer transformer = tf.newTransformer();
			transformer.transform(domSource, result);
			writer.flush();
			input = writer.toString();

			logger.trace("INPUT DATA: {}", input);

			// Setup a buffer to obtain the content length
			final ByteArrayOutputStream stageout = new ByteArrayOutputStream();
			final ByteArrayOutputStream out = new ByteArrayOutputStream();

			//// Setup Transformer (1st stage)
			/// Base path
			final String basepath = xslfile.substring(0, xslfile.indexOf(File.separator));
			final String firstStage = baseDir +
					File.separator +
					basepath +
					File.separator +
					"karuta" +
					File.separator +
					"xsl" +
					File.separator +
					"html2xml.xsl";
			logger.trace("FIRST: " + firstStage);
			final Source xsltSrc1 = new StreamSource(new File(firstStage));
			final Transformer transformer1 = transFactory.newTransformer(xsltSrc1);
			final StreamSource stageSource = new StreamSource(
					new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
			final Result stageRes = new StreamResult(stageout);
			transformer1.transform(stageSource, stageRes);

			// Setup Transformer (2nd stage)
			final String secondStage = baseDir + File.separator + xslfile;
			final Source xsltSrc2 = new StreamSource(new File(secondStage));
			final Transformer transformer2 = transFactory.newTransformer(xsltSrc2);

			// Configure parameter from xml
			final String[] table = parameters.split(";");
			for (final String line : table) {
				final int var = line.indexOf(":");
				final String par = line.substring(0, var);
				final String val = line.substring(var + 1);
				transformer2.setParameter(par, val);
			}

			// Setup input
			final StreamSource xmlSource = new StreamSource(new ByteArrayInputStream(
					stageout.toString(StandardCharsets.UTF_8.toString()).getBytes(StandardCharsets.UTF_8)));
			//			StreamSource xmlSource = new StreamSource(new File(baseDir+origin, "projectteam.xml") );

			Result res = null;
			if (usefop) {
				/// FIXME: Might need to include the entity for html stuff?
				//Setup FOP
				//Make sure the XSL transformation's result is piped through to FOP
				//				logger.error("Converting with FOP");
				final Fop fop = fopFactory.newFop(format, out);

				res = new SAXResult(fop.getDefaultHandler());

				//Start the transformation and rendering process
				transformer2.transform(xmlSource, res);
			} else {
				res = new StreamResult(out);

				//Start the transformation and rendering process
				transformer2.transform(xmlSource, res);
			}

			if (redirectDoc) {

				// /resources/resource/file/{uuid}[?size=[S|L]&lang=[fr|en]]
				final String urlTarget = server + "/resources/resource/file/" + documentid;
				logger.trace("Redirect @ {}", urlTarget);

				final HttpClientBuilder clientbuilder = HttpClientBuilder.create();
				final CloseableHttpClient client = clientbuilder.build();

				final HttpPost post = new HttpPost(urlTarget);
				post.addHeader("referer", origin);
				final String sessionid = request.getSession().getId();
				logger.info("Session: " + sessionid);
				post.addHeader("Cookie", "JSESSIONID=" + sessionid);
				final MultipartEntityBuilder builder = MultipartEntityBuilder.create();
				builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
				final ByteArrayBody body = new ByteArrayBody(out.toByteArray(), "generated" + ext);

				builder.addPart("uploadfile", body);

				final HttpEntity entity = builder.build();
				post.setEntity(entity);
				final HttpResponse ret = client.execute(post);
				final String stringret = new BasicResponseHandler().handleResponse(ret);

				final int code = ret.getStatusLine().getStatusCode();
				response.setStatus(code);
				final ServletOutputStream output = response.getOutputStream();
				output.write(stringret.getBytes(StandardCharsets.UTF_8), 0, stringret.length());
				output.close();
				client.close();

				/*
				HttpURLConnection connection = CreateConnection( urlTarget, request );
				
				/// Helping construct Json
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
				
				RetrieveAnswer(connection, response, origin);
				//*/
				logger.trace("Done converting");
			} else {
				response.reset();
				response.setHeader("Content-Disposition", "attachment; filename=generated" + ext);
				response.setContentType(format);
				response.setContentLength(out.size());
				response.getOutputStream().write(out.toByteArray());
				response.getOutputStream().flush();
			}
			request.getInputStream().close();
			response.getOutputStream().close();
		} catch (final Exception e) {
			final String message = e.getMessage();
			response.setStatus(500);
			response.getOutputStream().write(message.getBytes(StandardCharsets.UTF_8));
			response.getOutputStream().close();
			request.getInputStream().close();

			logger.error("Intercept error", e);
			//TODO managing error
		} finally {
			try {
				if (c != null) {
					c.close();
				}
			} catch (final SQLException e) {
				logger.error("Intercept error", e);
				//TODO managing error
			}
			//			dataProvider.disconnect();
		}
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
		/**
		 * Format demandé: <convert> <portfolioid>{uuid}</portfolioid>
		 * <portfolioid>{uuid}</portfolioid> <nodeid>{uuid}</nodeid>
		 * <nodeid>{uuid}</nodeid> <documentid>{uuid}</documentid>
		 * <xsl>{répertoire}{fichier}</xsl> <format>[pdf rtf xml ...]</format>
		 * <parameters> <maVar1>lala</maVar1> ... </parameters> </convert>
		 */

		/// Variable stuff
		int userId = 0;
		final HttpSession session = request.getSession(true);
		if (session != null) {
			Integer val = (Integer) session.getAttribute("uid");
			if (val != null) {
				userId = val;
			}
			val = (Integer) session.getAttribute("gid");
			if (val != null) {
			}
		}

		/// FORM name="data"
		// Create a factory for disk-based file items
		final DiskFileItemFactory factory = new DiskFileItemFactory();
		// Create a new file upload handler
		final ServletFileUpload upload = new ServletFileUpload(factory);

		List<FileItem> items;
		final StringWriter data = new StringWriter();
		try {
			items = upload.parseRequest(request);
			// Process the uploaded items
			for (final FileItem item : items) {
				if ("data".equals(item.getFieldName())) {
					// Data to process
					IOUtils.copy(item.getInputStream(), data, StandardCharsets.UTF_8);
					break;
				}
			}
		} catch (final FileUploadException e) {
			logger.error("Intercept error", e);
			//TODO managing error
		}

		//		String xslfile = xslNode.item(0).getTextContent();
		final String xslfile = request.getParameter("xsl");
		final String format = request.getParameter("format");
		//		String format = formatNode.item(0).getTextContent();
		String parameters = request.getParameter("parameters");
		parameters = parameters + ";urlimage:" + internalServer;

		logger.info("===== POST PARAMETERS: =====");
		logger.info("xsl: {}", xslfile);
		logger.info("format: {}", format);
		logger.info("user: {}", userId);
		logger.info("parameters: {}", parameters);

		final String pattern = "<\\?xml[^>]*>"; // Purge previous xml declaration
		String input = data.toString().replaceAll(pattern, "");

		input = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<!DOCTYPE xsl:stylesheet [" +
				"<!ENTITY % lat1 PUBLIC \"-//W3C//ENTITIES Latin 1 for XHTML//EN\" \"" +
				servletDir +
				"xhtml-lat1.ent\">" +
				"<!ENTITY % symbol PUBLIC \"-//W3C//ENTITIES Symbols for XHTML//EN\" \"" +
				servletDir +
				"xhtml-symbol.ent\">" +
				"<!ENTITY % special PUBLIC \"-//W3C//ENTITIES Special for XHTML//EN\" \"" +
				servletDir +
				"xhtml-special.ent\">" +
				"%lat1;" +
				"%symbol;" +
				"%special;" +
				"]>" +
				input;

		logger.info("INPUT: {}", input);

		boolean usefop = false;
		String ext = "";
		if (MimeConstants.MIME_PDF.equals(format)) {
			usefop = true;
			ext = ".pdf";
		} else if (MimeConstants.MIME_RTF.equals(format)) {
			usefop = true;
			ext = ".rtf";
		} else if ("application/csv".equals(format)) {
			ext = ".csv";
		}

		try {
			// Setup a buffer to obtain the content length
			final ByteArrayOutputStream out = new ByteArrayOutputStream(); // Data to send back

			// Setup Transformer (2nd stage)
			final String secondStage = baseDir + File.separator + xslfile;
			final Source xsltSrc2 = new StreamSource(new File(secondStage));
			final Transformer transformer2 = transFactory.newTransformer(xsltSrc2);

			// Configure parameter from xml
			if (parameters != null) {
				final String[] table = parameters.split(";");
				for (final String line : table) {
					final int var = line.indexOf(":");
					final String par = line.substring(0, var);
					final String val = line.substring(var + 1);
					transformer2.setParameter(par, val);
				}
			}

			// Setup input
			final byte[] bytes = data.toString().getBytes(StandardCharsets.UTF_8);
			final StreamSource xmlSource = new StreamSource(new ByteArrayInputStream(bytes));
			//			StreamSource xmlSource = new StreamSource(new File(baseDir+origin, "projectteam.xml") );

			Result res = null;
			if (usefop) {
				/// FIXME: Might need to include the entity for html stuff?
				//Setup FOP
				//Make sure the XSL transformation's result is piped through to FOP
				final Fop fop = fopFactory.newFop(format, out);

				res = new SAXResult(fop.getDefaultHandler());

				//Start the transformation and rendering process
				transformer2.transform(xmlSource, res);
			} else {
				res = new StreamResult(out);

				//Start the transformation and rendering process
				transformer2.transform(xmlSource, res);
			}

			response.reset();
			response.setHeader("Content-Disposition", "attachment; filename=generated" + ext);
			response.setContentType(format);
			response.setContentLength(out.size());
			response.getOutputStream().write(out.toByteArray());
			response.getOutputStream().flush();
		} catch (final Exception e) {
			logger.error("Intercept error", e);
			final String message = e.getMessage();
			response.setStatus(500);
			response.getOutputStream().write(message.getBytes(StandardCharsets.UTF_8));
			response.getOutputStream().close();

		}
	}

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		try {
			ConfigUtils.init(getServletContext());
		} catch (final Exception e) {
			logger.error("Can't init servlet", e);
			throw new ServletException(e);
		}
		sc = config.getServletContext();
		servletDir = sc.getRealPath("/");
		int last = servletDir.lastIndexOf(File.separator);
		last = servletDir.lastIndexOf(File.separator, last - 1);
		baseDir = servletDir.substring(0, last);
		logger.warn("Servlet XSLService initialized with baseDir '{}'", baseDir);
		server = ConfigUtils.getInstance().getRequiredProperty("backendserver");
		internalServer = ConfigUtils.getInstance().getProperty("XSLInternal");
		if (internalServer == null) {
			internalServer = server;
		}

		//Setting up the JAXP TransformerFactory
		this.transFactory = TransformerFactory.newInstance();

		String filename = null;
		try /// Try to load the configuration file "fopuserconfig.xml", if there isn't any, ignore
		{
			final File userconfig = new File(ConfigUtils.getInstance().getConfigPath() + "fopuserconfig.xml");
			filename = userconfig.getCanonicalPath();
			//Setting up the FOP factory
			this.fopFactory = FopFactory.newInstance(userconfig);
			logger.info("File '{}' loaded", filename);
		} catch (final Exception e) {
			logger.error("No configuration file found at '" + filename + "' using default values", e);
			throw new ServletException(e);
		}

		try {
			final String dataProviderName = ConfigUtils.getInstance().getRequiredProperty("dataProviderClass");
			dataProvider = (DataProvider) Class.forName(dataProviderName).getConstructor().newInstance();
		} catch (final Exception e) {
			logger.error("Can't init servlet", e);
			throw new ServletException(e);
		}
	}

	public void initialize(HttpServletRequest httpServletRequest) {
	}

	void RetrieveAnswer(HttpURLConnection connection, HttpServletResponse response, String referer)
			throws MalformedURLException, IOException {
		/// Receive answer
		InputStream in;
		try {
			in = connection.getInputStream();
		} catch (final Exception e) {
			logger.error("Managed error", e);
			in = connection.getErrorStream();
		}

		String ref = null;
		if (referer != null) {
			final int first = referer.indexOf('/', 7);
			final int last = referer.lastIndexOf('/');
			ref = referer.substring(first, last);
		}

		response.setContentType(connection.getContentType());
		response.setStatus(connection.getResponseCode());
		response.setContentLength(connection.getContentLength());

		/// Transfer headers
		final Map<String, List<String>> headers = connection.getHeaderFields();
		int size = headers.size();
		for (int i = 1; i < size; ++i) {
			final String key = connection.getHeaderFieldKey(i);
			final String value = connection.getHeaderField(i);
			//	      response.setHeader(key, value);
			response.addHeader(key, value);
		}

		/// Deal with correct path with set cookie
		final List<String> setValues = headers.get("Set-Cookie");
		if (setValues != null) {
			String setVal = setValues.get(0);
			final int pathPlace = setVal.indexOf("Path=");
			if (pathPlace > 0) {
				setVal = setVal.substring(0, pathPlace + 5); // Some assumption, may break
				setVal = setVal + ref;

				response.setHeader("Set-Cookie", setVal);
			}
		}

		/// Write back data
		final DataInputStream stream = new DataInputStream(in);
		final byte[] buffer = new byte[1024];
		//	    int size;
		ServletOutputStream out = null;
		try {
			out = response.getOutputStream();
			while ((size = stream.read(buffer, 0, buffer.length)) != -1) {
				out.write(buffer, 0, size);
			}

		} catch (final Exception e) {
			logger.error("Writing messed up!", e);
			//TODO managing error
		} finally {
			in.close();
			out.flush(); // close() should flush already, but Tomcat 5.5 doesn't
			out.close();
		}
	}

}