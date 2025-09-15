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
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.fileupload2.core.DiskFileItemFactory;
import org.apache.commons.fileupload2.core.FileItem;
import org.apache.commons.fileupload2.core.FileUploadException;
import org.apache.commons.fileupload2.jakarta.servlet6.JakartaServletFileUpload;
import org.apache.commons.io.IOUtils;
import org.apache.fop.apps.FopFactory;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.eportfolium.karuta.data.provider.DataProvider;
import com.eportfolium.karuta.data.utils.ConfigUtils;
import com.eportfolium.karuta.data.utils.SqlUtils;

import jakarta.activation.MimeType;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

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
		var last = servletDir.lastIndexOf(File.separator);
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
			final var userconfig = new File(ConfigUtils.getInstance().getConfigPath() + "fopuserconfig.xml");
			filename = userconfig.getCanonicalPath();
			//Setting up the FOP factory
			this.fopFactory = FopFactory.newInstance(userconfig);
			logger.info("File '{}' loaded", filename);
		} catch (final Exception e) {
			logger.error("No configuration file found at '" + filename + "' using default values", e);
			throw new ServletException(e);
		}

		try {
			final var dataProviderName = ConfigUtils.getInstance().getRequiredProperty("dataProviderClass");
			dataProvider = (DataProvider) Class.forName(dataProviderName).getConstructor().newInstance();
		} catch (final Exception e) {
			logger.error("Can't init servlet", e);
			throw new ServletException(e);
		}
	}

	public void initialize(HttpServletRequest httpServletRequest) {
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
		try (var c = SqlUtils.getConnection()) {

			final var origin = request.getRequestURL().toString();
			logger.trace("Is connection null {}", c);

			/// Variable stuff
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
			final var xslfile = request.getParameter("xsl");
			final var format = request.getParameter("format");
			//		String format = formatNode.item(0).getTextContent();
			var parameters = request.getParameter("parameters");
			final var documentid = request.getParameter("documentid");
			final var portfolios = request.getParameter("portfolioids");
			String[] portfolioid = null;
			if (portfolios != null) {
				portfolioid = portfolios.split(";");
			}
			final var nodes = request.getParameter("nodeids");
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

			var redirectDoc = false;
			if (documentid != null) {
				redirectDoc = true;
				logger.trace("documentid @ {}", documentid);
			}

			var usefop = false;
			var ext = "";
			if (org.apache.xmlgraphics.util.MimeConstants.MIME_PDF.equals(format)) {
				usefop = true;
				ext = ".pdf";
			} else if (org.apache.xmlgraphics.util.MimeConstants.MIME_RTF.equals(format)) {
				usefop = true;
				ext = ".rtf";
			}
			//// Paramètre portfolio-uuid et file-xsl
			//		String uuid = request.getParameter("uuid");
			//		String xslfile = request.getParameter("xsl");

			final var aggregate = new StringBuilder();
			// On aggrège les données
			if (portfolioid != null) {
				for (final String p : portfolioid) {
					final var portfolioxml = dataProvider
							.getPortfolio(c, new MimeType("text/xml"), p, userId, groupId, "", null, null, 0, null)
							.toString();
					aggregate.append(portfolioxml);
				}
			}

			if (nodeid != null) {
				for (final String n : nodeid) {
					final var node = dataProvider.getNode(c, new MimeType("text/xml"), n, true, userId, groupId, null,
							"", null);
					final var nodexml = node == null ? "" : node.toString();
					aggregate.append(nodexml);
				}
			}

			// Est-ce qu'on a eu besoin d'aggr�ger les donn�es?
			var input = aggregate.toString();
			final var pattern = "<\\?xml[^>]*>"; // Purge previous xml declaration

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
			final var documentBuilderFactory = DocumentBuilderFactory.newInstance();
			documentBuilder = documentBuilderFactory.newDocumentBuilder();
			InputStream is = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
			//			InputSource is = new InputSource(new StringReader(input));
			final var doc = documentBuilder.parse(is);

			/// Proxy stuff
			final var xPath = XPathFactory.newInstance().newXPath();
			//			String filterRes = "//asmResource[@xsi_type='Proxy']";
			final var filterRes = "//*[local-name()='asmResource' and @*[local-name()='xsi_type' and .='Proxy']]";
			//			NodeList nodelist = XPathAPI.selectNodeList(doc.getDocumentElement(), filterRes);
			//			String filterCode = "./code/text()";
			final var filterCode = "./*[local-name()='code']/text()";
			final var nodelist = (NodeList) xPath.compile(filterRes).evaluate(doc, XPathConstants.NODESET);

			final var codeFilter = xPath.compile(filterCode);

			for (var i = 0; i < nodelist.getLength(); ++i) {
				final var res = nodelist.item(i);
				final var gp = res.getParentNode(); // resource -> context -> container
				final var ggp = gp.getParentNode();

				final var uuid = (Node) codeFilter.evaluate(res, XPathConstants.NODE);
				//				Node uuid = XPathAPI.selectSingleNode(res, filterCode);

				/// Fetch node we want to replace
				final var returnValue = dataProvider.getNode(c, new MimeType("text/xml"), uuid.getTextContent(), true,
						userId, groupId, null, "", null);
				if (returnValue == null) {
					continue;
				}
				final var returnString = returnValue.toString();
				is = new ByteArrayInputStream(returnString.getBytes(StandardCharsets.UTF_8));
				final var rep = documentBuilder.parse(is);
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
			final var domSource = new DOMSource(doc);
			final var writer = new StringWriter();
			final var result = new StreamResult(writer);
			final var tf = TransformerFactory.newInstance();
			final var transformer = tf.newTransformer();
			transformer.transform(domSource, result);
			writer.flush();
			input = writer.toString();

			logger.trace("INPUT DATA: {}", input);

			// Setup a buffer to obtain the content length
			final var stageout = new ByteArrayOutputStream();
			final var out = new ByteArrayOutputStream();

			//// Setup Transformer (1st stage)
			/// Base path
			final var basepath = xslfile.substring(0, xslfile.indexOf(File.separator));
			final var firstStage = baseDir +
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
			final var transformer1 = transFactory.newTransformer(xsltSrc1);
			final var stageSource = new StreamSource(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
			final Result stageRes = new StreamResult(stageout);
			transformer1.transform(stageSource, stageRes);

			// Setup Transformer (2nd stage)
			final var secondStage = baseDir + File.separator + xslfile;
			final Source xsltSrc2 = new StreamSource(new File(secondStage));
			final var transformer2 = transFactory.newTransformer(xsltSrc2);

			// Configure parameter from xml
			final var table = parameters.split(";");
			for (final String line : table) {
				final var var = line.indexOf(":");
				final var par = line.substring(0, var);
				final var val = line.substring(var + 1);
				transformer2.setParameter(par, val);
			}

			// Setup input
			final var xmlSource = new StreamSource(new ByteArrayInputStream(
					stageout.toString(StandardCharsets.UTF_8.toString()).getBytes(StandardCharsets.UTF_8)));
			//			StreamSource xmlSource = new StreamSource(new File(baseDir+origin, "projectteam.xml") );

			Result res = null;
			if (usefop) {
				/// FIXME: Might need to include the entity for html stuff?
				//Setup FOP
				//Make sure the XSL transformation's result is piped through to FOP
				//				logger.error("Converting with FOP");
				final var fop = fopFactory.newFop(format, out);

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
				final var urlTarget = server + "/resources/resource/file/" + documentid;
				logger.trace("Redirect @ {}", urlTarget);

				final var clientbuilder = HttpClientBuilder.create();
				final var client = clientbuilder.build();

				final var post = new HttpPost(urlTarget);
				post.addHeader("referer", origin);
				final var sessionid = request.getSession().getId();
				logger.info("Session: " + sessionid);
				post.addHeader("Cookie", "JSESSIONID=" + sessionid);
				final var builder = MultipartEntityBuilder.create();
				builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
				final var body = new ByteArrayBody(out.toByteArray(), "generated" + ext);

				builder.addPart("uploadfile", body);

				final var entity = builder.build();
				post.setEntity(entity);
				final HttpResponse ret = client.execute(post);
				final var stringret = new BasicResponseHandler().handleResponse(ret);

				final var code = ret.getStatusLine().getStatusCode();
				response.setStatus(code);
				final var output = response.getOutputStream();
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
			final var message = e.getMessage();
			response.setStatus(500);
			response.getOutputStream().write(message.getBytes(StandardCharsets.UTF_8));
			response.getOutputStream().close();
			request.getInputStream().close();

			logger.error("Intercept error", e);
			//TODO managing error
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
		var userId = 0;
		final var session = request.getSession(true);
		if (session != null) {
			var val = (Integer) session.getAttribute("uid");
			if (val != null) {
				userId = val;
			}
			val = (Integer) session.getAttribute("gid");
			if (val != null) {
			}
		}

		/// FORM name="data"
		// Create a factory for disk-based file items
		final var factory = DiskFileItemFactory.builder().get();
		// Create a new file upload handler
		final var upload = new JakartaServletFileUpload(factory);

		List<FileItem> items;
		final var data = new StringWriter();
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
		final var xslfile = request.getParameter("xsl");
		final var format = request.getParameter("format");
		//		String format = formatNode.item(0).getTextContent();
		var parameters = request.getParameter("parameters");
		parameters = parameters + ";urlimage:" + internalServer;

		logger.info("===== POST PARAMETERS: =====");
		logger.info("xsl: {}", xslfile);
		logger.info("format: {}", format);
		logger.info("user: {}", userId);
		logger.info("parameters: {}", parameters);

		final var pattern = "<\\?xml[^>]*>"; // Purge previous xml declaration
		var input = data.toString().replaceAll(pattern, "");

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

		var usefop = false;
		var ext = "";
		if (org.apache.xmlgraphics.util.MimeConstants.MIME_PDF.equals(format)) {
			usefop = true;
			ext = ".pdf";
		} else if (org.apache.xmlgraphics.util.MimeConstants.MIME_RTF.equals(format)) {
			usefop = true;
			ext = ".rtf";
		} else if ("application/csv".equals(format)) {
			ext = ".csv";
		}

		try {
			// Setup a buffer to obtain the content length
			final var out = new ByteArrayOutputStream(); // Data to send back

			// Setup Transformer (2nd stage)
			final var secondStage = baseDir + File.separator + xslfile;
			final Source xsltSrc2 = new StreamSource(new File(secondStage));
			final var transformer2 = transFactory.newTransformer(xsltSrc2);

			// Configure parameter from xml
			if (parameters != null) {
				final var table = parameters.split(";");
				for (final String line : table) {
					final var var = line.indexOf(":");
					final var par = line.substring(0, var);
					final var val = line.substring(var + 1);
					transformer2.setParameter(par, val);
				}
			}

			// Setup input
			final var bytes = data.toString().getBytes(StandardCharsets.UTF_8);
			final var xmlSource = new StreamSource(new ByteArrayInputStream(bytes));
			//			StreamSource xmlSource = new StreamSource(new File(baseDir+origin, "projectteam.xml") );

			Result res = null;
			if (usefop) {
				/// FIXME: Might need to include the entity for html stuff?
				//Setup FOP
				//Make sure the XSL transformation's result is piped through to FOP
				final var fop = fopFactory.newFop(format, out);

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
			final var message = e.getMessage();
			response.setStatus(500);
			response.getOutputStream().write(message.getBytes(StandardCharsets.UTF_8));
			response.getOutputStream().close();

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
			final var first = referer.indexOf('/', 7);
			final var last = referer.lastIndexOf('/');
			ref = referer.substring(first, last);
		}

		response.setContentType(connection.getContentType());
		response.setStatus(connection.getResponseCode());
		response.setContentLength(connection.getContentLength());

		/// Transfer headers
		final var headers = connection.getHeaderFields();
		var size = headers.size();
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

		/// Write back data
		final var stream = new DataInputStream(in);
		final var buffer = new byte[1024];
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