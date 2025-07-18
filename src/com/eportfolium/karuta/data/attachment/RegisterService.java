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

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.eportfolium.karuta.data.provider.DataProvider;
import com.eportfolium.karuta.data.utils.ConfigUtils;
import com.eportfolium.karuta.data.utils.DomUtils;
import com.eportfolium.karuta.data.utils.MailUtils;
import com.eportfolium.karuta.data.utils.SqlUtils;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

public class RegisterService extends HttpServlet {

	/**
	 *
	 */
	private static final Logger logger = LogManager.getLogger(RegisterService.class);
	private static final long serialVersionUID = 9188067506635747901L;

	//	DataProvider dataProvider;
	boolean hasNodeReadRight = false;
	boolean hasNodeWriteRight = false;
	int userId;
	int groupId = -1;
	String user = "";
	String context = "";

	HttpSession session;
	String dataProviderName;
	DataProvider dataProvider = null;

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		try {
			ConfigUtils.init(getServletContext());
			dataProviderName = ConfigUtils.getInstance().getRequiredProperty("dataProviderClass");
			dataProvider = (DataProvider) Class.forName(dataProviderName).getConstructor().newInstance();
			final var sc = config.getServletContext();
			sc.getRealPath("/");
		} catch (final Exception e) {
			logger.error("Can't init servlet", e);
			throw new ServletException(e);
		}
	}

	public DataProvider initialize(HttpServletRequest httpServletRequest) {
		return dataProvider;
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
		//		DataProvider dataProvider = initialize(request);
		Connection connection = null;
		try {
			connection = SqlUtils.getConnection();
		} catch (final Exception e1) {
			e1.printStackTrace();
		}

		response.setCharacterEncoding(StandardCharsets.UTF_8.toString());
		final var inputdata = new StringWriter();
		IOUtils.copy(request.getInputStream(), inputdata, StandardCharsets.UTF_8);

		try {
			final var doc = DomUtils.xmlString2Document(inputdata.toString(), new StringBuilder());
			final var credentialElement = doc.getDocumentElement();
			var username = "";
			var password = "";
			var mail = "";
			final var mailcc = "";
			var hasChanged = false;

			var converted = "";
			if (credentialElement.getNodeName().equals("users")) {
				final var children = credentialElement.getChildNodes();
				for (var i = 0; i < children.getLength(); i++) {
					if (children.item(i).getNodeName().equals("user")) {
						NodeList children2 = null;
						children2 = children.item(i).getChildNodes();
						for (var y = 0; y < children2.getLength(); y++) {
							if (children2.item(y).getNodeName().equals("username")) {
								username = DomUtils.getInnerXml(children2.item(y));
							}
							if (children2.item(y).getNodeName().equals("email")) {
								mail = DomUtils.getInnerXml(children2.item(y));
							}
						}

						/// Generate password
						final var base = System.currentTimeMillis();
						final var md = MessageDigest.getInstance("SHA-1");
						final var output = md.digest(Long.toString(base).getBytes());
						password = String.format("%032X", new BigInteger(1, output));
						password = password.substring(0, 9);

						//// Force a password in it and set as designer
						final Node passNode = doc.createElement("password");
						passNode.setTextContent(password);
						children.item(i).appendChild(passNode);
						final Node designerNode = doc.createElement("designer");
						designerNode.setTextContent("1");
						children.item(i).appendChild(designerNode);

						/// Change it back to string
						final var tf = TransformerFactory.newInstance();
						final var transformer = tf.newTransformer();
						transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
						final var writer = new StringWriter();
						transformer.transform(new DOMSource(doc), new StreamResult(writer));
						converted = writer.getBuffer().toString().replaceAll("((\n)|(\r))", "");

						break;
					}
				}
			}

			if (!"".equals(username)) {
				final var val = dataProvider.postUsers(connection, converted, 1);
				if (!"".equals(val)) {
					logger.debug("Account create: " + val);
					hasChanged = true;
				} else {
					logger.debug("Account creation fail: " + username);
				}
			}

			// Username should be in an email format
			if (hasChanged) {
				response.setStatus(200);
				// Send email
				final var content = "Your account with username: " +
						username +
						" has been created with the password: " +
						password;
				MailUtils.postMail(getServletConfig(), mail, mailcc, "Account created for Karuta: " + username, content,
						logger);
				final var output = response.getWriter();
				output.write("created");
				output.close();
			} else {
				response.setStatus(400);
				final var output = response.getWriter();
				output.write("username exists");
				output.close();
				request.getInputStream().close();
			}
		} catch (final Exception e) {
			logger.error("Intercepted error", e);
			//TODO something is missing
		} finally {
			try {
				if (connection != null) {
					connection.close();
				}
			} catch (final SQLException e) {
				logger.error("Intercepted error", e);
				//TODO something is missing
			}
		}
	}
}