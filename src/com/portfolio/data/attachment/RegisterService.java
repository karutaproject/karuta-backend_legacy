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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;

import javax.naming.InitialContext;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.sql.DataSource;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.portfolio.data.provider.DataProvider;
import com.portfolio.data.utils.ConfigUtils;
import com.portfolio.data.utils.DomUtils;
import com.portfolio.data.utils.MailUtils;
import com.portfolio.data.utils.SqlUtils;
import com.portfolio.security.Credential;

public class RegisterService  extends HttpServlet {

	/**
	 *
	 */
	final Logger logger = LoggerFactory.getLogger(RegisterService.class);
	private static final long serialVersionUID = 9188067506635747901L;

//	DataProvider dataProvider;
	boolean hasNodeReadRight = false;
	boolean hasNodeWriteRight = false;
	Credential credential;
	int userId;
	int groupId = -1;
	String user = "";
	String context = "";
	HttpSession session;
	String dataProviderName;

	@Override
	public void init( ServletConfig config ) throws ServletException
	{
		super.init(config);
		try
		{
			ConfigUtils.loadConfigFile(config);
			dataProviderName = ConfigUtils.get("dataProviderClass");
		}
		catch( Exception e )
		{
			e.printStackTrace();
		}
	}

	public DataProvider initialize(HttpServletRequest httpServletRequest)
	{
		DataProvider dataProvider = null;
		DataSource ds = null;
		try
		{
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
		catch ( Exception e )
		{
			logger.info("CAN'T CREATE CONNECTION: "+e.getMessage());
			e.printStackTrace();
		}

		try
		{
			Connection con = null;
			if( ds == null )	// Case where we can't deploy context.xml
			{
				con = SqlUtils.getConnection(getServletContext());
				dataProvider.setConnection(con);
			}
			else
			{
				con = ds.getConnection();
				dataProvider.setConnection(con);
			}
//			dataProvider.setDataSource(ds);

//			credential = new Credential(con);

			/// Configure session
			/// FIXME: Oracle part might be missing
			if( "mysql".equals(ConfigUtils.get("serverType")) )
			{
				PreparedStatement st = con.prepareStatement("SET SESSION group_concat_max_len = 1048576");	// 1MB
				st.execute();
				st.close();
			}
		}
		catch( Exception ex )
		{
			logger.error(ex.getMessage());
		}

		return dataProvider;
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException
	{
		DataProvider dataProvider = initialize(request);
		response.setCharacterEncoding("UTF-8");
		StringWriter inputdata = new StringWriter();
		IOUtils.copy(request.getInputStream(), inputdata, "UTF-8");

		try
		{
			Document doc = DomUtils.xmlString2Document(inputdata.toString(), new StringBuffer());
			Element credentialElement = doc.getDocumentElement();
			String username = "";
			String password = "";
			String mail = "";
			String mailcc = "";
			boolean hasChanged = false;

			String converted = "";
			if(credentialElement.getNodeName().equals("users"))
			{
				NodeList children = children = credentialElement.getChildNodes();
				for(int i=0;i<children.getLength();i++)
				{
					if(children.item(i).getNodeName().equals("user"))
					{
						NodeList children2 = null;
						children2 = children.item(i).getChildNodes();
						for(int y=0;y<children2.getLength();y++)
						{
							if(children2.item(y).getNodeName().equals("username"))
							{
								username = DomUtils.getInnerXml(children2.item(y));
							}
							if(children2.item(y).getNodeName().equals("email"))
							{
								mail = DomUtils.getInnerXml(children2.item(y));
							}
						}

						/// Generate password
						long base = System.currentTimeMillis();
						MessageDigest md = MessageDigest.getInstance("SHA-1");
						byte[] output = md.digest(Long.toString(base).getBytes());
						password = String.format("%032X", new BigInteger(1, output));
						password = password.substring(0, 9);

						//// Force a password in it and set as designer
						Node passNode = doc.createElement("password");
						passNode.setTextContent(password);
						children.item(i).appendChild(passNode);
						Node designerNode = doc.createElement("designer");
						designerNode.setTextContent("1");
						children.item(i).appendChild(designerNode);

						/// Change it back to string
						TransformerFactory tf = TransformerFactory.newInstance();
						Transformer transformer = tf.newTransformer();
						transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
						StringWriter writer = new StringWriter();
						transformer.transform(new DOMSource(doc), new StreamResult(writer));
						converted = writer.getBuffer().toString().replaceAll("\n|\r", "");

						break;
					}
				}
			}

			if( !"".equals(username) )
			{
				String val = dataProvider.postUsers(converted, 1);
				if( !"".equals(val) )
				{
					logger.debug("Account create: "+val);
					hasChanged = true;
				}
				else
					logger.debug("Account creation fail: "+username);
			}

			// Username should be in an email format
			if( hasChanged )
			{
				response.setStatus(200);
				// Send email
				String content = "Your account with username: "+username+" has been created with the password: "+password;
				MailUtils.postMail(getServletConfig(), mail, mailcc, "Account created for Karuta: "+username, content, logger);
				PrintWriter output = response.getWriter();
				output.write("created");
				output.close();
			}
			else
			{
				response.setStatus(400);
				PrintWriter output = response.getWriter();
				output.write("username exists");
				output.close();
				request.getInputStream().close();
			}
		}
		catch( Exception e )
		{
			e.printStackTrace();
		}
		finally
		{

		}
	}
}

