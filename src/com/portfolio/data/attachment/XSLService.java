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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Connection;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import javax.activation.MimeType;
import javax.naming.InitialContext;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.sql.DataSource;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.portfolio.data.provider.DataProvider;
import com.portfolio.security.Credential;

public class XSLService  extends HttpServlet {

	/**
	 *
	 */
	private static final long serialVersionUID = 9188067506635747901L;

	DataProvider dataProvider;
	boolean hasNodeReadRight = false;
	boolean hasNodeWriteRight = false;
	Credential credential;
	int userId;
	int groupId = -1;
	String user = "";
	ServletContext sc;
	String context = "";
	DataSource ds;

	private String server;
	String baseDir = System.getProperty("catalina.base") + "/webapps";
	String servletDir;

	private TransformerFactory transFactory;
	private FopFactory fopFactory;

	@Override
	public void init(ServletConfig config) throws ServletException
	{
		sc = config.getServletContext();
		servletDir = sc.getRealPath("/");
		server = config.getInitParameter("redirectedServer");

		//Setting up the JAXP TransformerFactory
		this.transFactory = TransformerFactory.newInstance();

		//Setting up the FOP factory
		this.fopFactory = FopFactory.newInstance();

		try
		{
			InitialContext cxt = new InitialContext();
			ds = (DataSource) cxt.lookup( "java:/comp/env/jdbc/portfolio-backend" );

			if ( ds == null ) {
				throw new Exception("Data  jdbc/portfolio-backend source not found!");
			}

			String dataProviderName = config.getInitParameter("dataProviderClass");
			dataProvider = (DataProvider)Class.forName(dataProviderName).newInstance();
		}
		catch( Exception e )
		{
			e.printStackTrace();
		}
	}

	public void initialize(HttpServletRequest httpServletRequest)
	{
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException
	{
		/**
		 * Format demand�:
		 * <convert>
		 *   <portfolioid>{uuid}</portfolioid>
		 *   <portfolioid>{uuid}</portfolioid>
		 *   <nodeid>{uuid}</nodeid>
		 *   <nodeid>{uuid}</nodeid>
		 *   <documentid>{uuid}</documentid>
		 *   <xsl>{r�pertoire}{fichier}</xsl>
		 *   <format>[pdf rtf xml ...]</format>
		 *   <parameters>
		 *     <maVar1>lala</maVar1>
		 *     ...
		 *   </parameters>
		 * </convert>
		 */
		try
		{
//			this.dataProvider.connect(new Properties());

			//On initialise le dataProvider
			Connection c = ds.getConnection();
//			dataProvider.setDataSource(ds);
//			dataProvider.connect(null);
			dataProvider.setConnection(c);
//			credential = new Credential(c);
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}

		String origin = request.getRequestURL().toString();

		/// Variable stuff
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
			doc = documentBuilder.parse(new ByteArrayInputStream(sb.toString().getBytes("UTF-8")));
		}
		catch( Exception e )
		{
			e.printStackTrace();
		}

		/// On lit les param�tres
		NodeList portfolioNode = doc.getElementsByTagName("portfolioid");
		NodeList nodeNode = doc.getElementsByTagName("nodeid");
		NodeList documentNode = doc.getElementsByTagName("documentid");
		NodeList xslNode = doc.getElementsByTagName("xsl");
		NodeList formatNode = doc.getElementsByTagName("format");
		NodeList parametersNode = doc.getElementsByTagName("parameters");
		//*/
//		String xslfile = xslNode.item(0).getTextContent();
		String xslfile = request.getParameter("xsl");
		String format = request.getParameter("format");
//		String format = formatNode.item(0).getTextContent();
		String parameters = request.getParameter("parameters");
		String documentid = request.getParameter("documentid");
		String portfolios = request.getParameter("portfolioids");
		String[] portfolioid = null;
		if( portfolios != null )
			portfolioid = portfolios.split(";");
		String nodes = request.getParameter("nodeids");
		String[] nodeid = null;
		if( nodes != null )
			nodeid = nodes.split(";");

		System.out.println("PARAMETERS: ");
		System.out.println("xsl: "+xslfile);
		System.out.println("format: "+format);
		System.out.println("user: "+userId);
		System.out.println("portfolioids: "+portfolios);
		System.out.println("nodeids: "+nodes);
		System.out.println("parameters: "+parameters);

		boolean redirectDoc = false;
		if( documentid != null )
		{
			redirectDoc = true;
			System.out.println("documentid @ "+documentid);
		}

		boolean usefop = false;
		String ext = "";
		if( MimeConstants.MIME_PDF.equals(format) )
		{
			usefop = true;
			ext = ".pdf";
		}
		else if( MimeConstants.MIME_RTF.equals(format) )
		{
			usefop = true;
			ext = ".rtf";
		}
		//// Param�tre portfolio-uuid et file-xsl
//		String uuid = request.getParameter("uuid");
//		String xslfile = request.getParameter("xsl");

		StringBuilder aggregate = new StringBuilder();
		try
		{
			int portcount = 0;
			int nodecount = 0;
			// On aggr�ge les donn�es
			if( portfolioid!=null )
			{
				portcount = portfolioid.length;
				for( int i=0; i<portfolioid.length; ++i )
				{
					String p = portfolioid[i];
					String portfolioxml = dataProvider.getPortfolio(new MimeType("text/xml"), p, userId, groupId, "", null, null, 0).toString();
					aggregate.append(portfolioxml);
				}
			}

			if( nodeid!=null )
			{
				nodecount = nodeid.length;
				for( int i=0; i<nodeid.length; ++i )
				{
					String n = nodeid[i];
					String nodexml = dataProvider.getNode(new MimeType("text/xml"), n, true, userId, groupId, "").toString();
					aggregate.append(nodexml);
				}
			}

			// Est-ce qu'on a eu besoin d'aggr�ger les donn�es?
			String input = aggregate.toString();
			String pattern = "<\\?xml[^>]*>";	// Purge previous xml declaration

			input = input.replaceAll(pattern, "");

			input = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
					"<!DOCTYPE xsl:stylesheet [" +
					"<!ENTITY % lat1 PUBLIC \"-//W3C//ENTITIES Latin 1 for XHTML//EN\" \""+servletDir+"xhtml-lat1.ent\">" +
					"<!ENTITY % symbol PUBLIC \"-//W3C//ENTITIES Symbols for XHTML//EN\" \""+servletDir+"xhtml-symbol.ent\">" +
					"<!ENTITY % special PUBLIC \"-//W3C//ENTITIES Special for XHTML//EN\" \""+servletDir+"xhtml-special.ent\">" +
					"%lat1;" +
					"%symbol;" +
					"%special;" +
					"]>" + // For the pesky special characters
					"<root>" + input + "</root>";

//			System.out.println("INPUT WITH PROXY:"+ input);

			/// Résolution des proxys
			DocumentBuilder documentBuilder;
			DocumentBuilderFactory documentBuilderFactory =DocumentBuilderFactory.newInstance();
			documentBuilder = documentBuilderFactory.newDocumentBuilder();
			InputSource is = new InputSource(new StringReader(input));
			Document doc = documentBuilder.parse(is);

			/// Proxy stuff
			XPath xPath = XPathFactory.newInstance().newXPath();
			String filterRes = "//asmResource[@xsi_type='Proxy']";
			String filterCode = "./code/text()";
			NodeList nodelist = (NodeList) xPath.compile(filterRes).evaluate(doc, XPathConstants.NODESET);

			XPathExpression codeFilter = xPath.compile(filterCode);

			for( int i=0; i<nodelist.getLength(); ++i )
			{
				Node res = nodelist.item(i);
				Node gp = res.getParentNode();	// resource -> context -> container
				Node ggp = gp.getParentNode();
				Node uuid = (Node) codeFilter.evaluate(res, XPathConstants.NODE);

				/// Fetch node we want to replace
				String returnValue = dataProvider.getNode(new MimeType("text/xml"), uuid.getTextContent(), true, userId, groupId, "").toString();

				Document rep = documentBuilder.parse( new InputSource(new StringReader(returnValue)) );
				Element repNode = rep.getDocumentElement();
				Node proxyNode = repNode.getFirstChild();
				proxyNode = doc.importNode(proxyNode,true);	// adoptNode have some weird side effect. To be banned
//				doc.replaceChild(proxyNode, gp);
				ggp.insertBefore(proxyNode, gp);	// replaceChild doesn't work.
				ggp.removeChild(gp);
			}

			try	// Convert XML document to string
			{
				DOMSource domSource = new DOMSource(doc);
				StringWriter writer = new StringWriter();
				StreamResult result = new StreamResult(writer);
				TransformerFactory tf = TransformerFactory.newInstance();
				Transformer transformer = tf.newTransformer();
				transformer.transform(domSource, result);
				writer.flush();
				input = writer.toString();
			}
			catch(TransformerException ex)
			{
				ex.printStackTrace();
			}

//			System.out.println("INPUT DATA:"+ input);

			// Setup a buffer to obtain the content length
			ByteArrayOutputStream stageout = new ByteArrayOutputStream();
			ByteArrayOutputStream out = new ByteArrayOutputStream();

			//// Setup Transformer (1st stage)
			/// Base path
			String basepath = xslfile.substring(0,xslfile.indexOf(File.separator));
			String firstStage = baseDir+File.separator+basepath+File.separator+"karuta"+File.separator+"xsl"+File.separator+"html2xml.xsl";
			System.out.println("FIRST: "+firstStage);
			Source xsltSrc1 = new StreamSource(new File(firstStage));
			Transformer transformer1 = transFactory.newTransformer(xsltSrc1);
			StreamSource stageSource = new StreamSource(new ByteArrayInputStream( input.getBytes() ) );
			Result stageRes = new StreamResult(stageout);
			transformer1.transform(stageSource, stageRes);

			// Setup Transformer (2nd stage)
			String secondStage = baseDir+File.separator+xslfile;
			Source xsltSrc2 = new StreamSource(new File(secondStage));
			Transformer transformer2 = transFactory.newTransformer(xsltSrc2);

			// Configure parameter from xml
			String[] table = parameters.split(";");
			for( int i=0; i<table.length; ++i )
			{
				String line = table[i];
				int var = line.indexOf(":");
				String par = line.substring(0, var);
				String val = line.substring(var+1);
				transformer2.setParameter(par, val);
			}

			// Setup input
			StreamSource xmlSource = new StreamSource(new ByteArrayInputStream( stageout.toString().getBytes() ) );
//			StreamSource xmlSource = new StreamSource(new File(baseDir+origin, "projectteam.xml") );


			Result res = null;
			if( usefop )
			{
				/// FIXME: Might need to include the entity for html stuff?
				//Setup FOP
				//Make sure the XSL transformation's result is piped through to FOP
				Fop fop = fopFactory.newFop(format, out);

				res = new SAXResult(fop.getDefaultHandler());

				//Start the transformation and rendering process
				transformer2.transform(xmlSource, res);
			}
			else
			{
				res = new StreamResult(out);

				//Start the transformation and rendering process
				transformer2.transform(xmlSource, res);
			}

			if( redirectDoc )
			{

				// /resources/resource/file/{uuid}[?size=[S|L]&lang=[fr|en]]
				String urlTarget = "http://"+ server + "/resources/resource/file/" + documentid;
				System.out.println("Redirect @ "+urlTarget);

				HttpClientBuilder clientbuilder = HttpClientBuilder.create();
				CloseableHttpClient client = clientbuilder.build();

				HttpPost post = new HttpPost(urlTarget);
				post.addHeader("referer", origin);
				String sessionid = request.getSession().getId();
				System.out.println("Session: "+sessionid);
				post.addHeader("Cookie","JSESSIONID="+sessionid);
				MultipartEntityBuilder builder = MultipartEntityBuilder.create();
				builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
				ByteArrayBody body = new ByteArrayBody(out.toByteArray(), "generated"+ext);

				builder.addPart("uploadfile", body);

				HttpEntity entity = builder.build();
				post.setEntity(entity);
				HttpResponse ret = client.execute(post);
				String stringret = new BasicResponseHandler().handleResponse(ret);

				int code = ret.getStatusLine().getStatusCode();
				response.setStatus(code);
				ServletOutputStream output = response.getOutputStream();
				output.write(stringret.getBytes(), 0, stringret.length());
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
			}
			else
			{
				response.reset();
				response.setHeader("Content-Disposition", "attachment; filename=generated"+ext);
				response.setContentType(format);
				response.setContentLength(out.size());
				response.getOutputStream().write(out.toByteArray());
				response.getOutputStream().flush();
			}
		}
		catch( Exception e )
		{
			String message = e.getMessage();
			response.setStatus(500);
			response.getOutputStream().write(message.getBytes());
			response.getOutputStream().close();

			e.printStackTrace();
		}
		finally
		{
			dataProvider.disconnect();
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

		/// Write back data
		DataInputStream stream = new DataInputStream(in);
		byte[] buffer = new byte[1024];
		//	    int size;
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

}

