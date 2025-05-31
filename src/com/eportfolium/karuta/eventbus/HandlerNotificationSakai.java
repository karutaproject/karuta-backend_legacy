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

package com.eportfolium.karuta.eventbus;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.Iterator;
import java.util.Set;

import javax.activation.MimeType;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.eportfolium.karuta.data.provider.DataProvider;
import com.eportfolium.karuta.data.utils.DomUtils;
import com.eportfolium.karuta.data.utils.SqlUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

public class HandlerNotificationSakai implements KEventHandler {
	private static final Logger logger = LoggerFactory.getLogger(HandlerNotificationSakai.class);
	HttpServletRequest httpServletRequest;
	HttpSession session;
	int userId;
	int groupId;
	String username;
	DataProvider dataProvider;
	String ticket;
	String sessionCookie;
	Connection connection;

	public HandlerNotificationSakai(HttpServletRequest request, DataProvider provider) {
		httpServletRequest = request;
		dataProvider = provider;
		try {
			connection = SqlUtils.getConnection();
		} catch (final Exception e) {
			e.printStackTrace();
		}

		this.session = request.getSession(true);
		Integer val = (Integer) session.getAttribute("uid");
		if (val != null) {
			this.userId = val;
		}
		val = (Integer) session.getAttribute("gid");
		if (val != null) {
			this.groupId = val;
		}
		this.username = (String) session.getAttribute("user");
	}

	boolean getSakaiTicket() {
		boolean ret = true;
		try {
			/// Configurable?
			final String urlParameters = "_username=testadmin&_password=testadmin";

			/// Will have to use some context config
			final URL urlTicker = new URL("http://osp2.threecanoes.com/direct/session");

			final HttpURLConnection connect = (HttpURLConnection) urlTicker.openConnection();
			connect.setDoOutput(true);
			connect.setDoInput(true);
			connect.setInstanceFollowRedirects(false);
			connect.setRequestMethod("POST");
			connect.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			connect.setRequestProperty("charset", "utf-8");
			connect.setRequestProperty("Content-Length", "" + Integer.toString(urlParameters.getBytes().length));
			connect.setUseCaches(false);
			connect.connect();

			final DataOutputStream wr = new DataOutputStream(connect.getOutputStream());
			wr.writeBytes(urlParameters);
			wr.flush();
			wr.close();

			final StringBuilder readTicket = new StringBuilder();
			final BufferedReader rd = new BufferedReader(
					new InputStreamReader(connect.getInputStream(), StandardCharsets.UTF_8));
			final char[] buffer = new char[1024];
			int offset = 0;
			int read = 0;
			do {
				read = rd.read(buffer, offset, 1024);
				offset += read;
				readTicket.append(buffer);
			} while (read == 1024);
			rd.close();

			sessionCookie = connect.getHeaderField("Set-Cookie");

			connect.disconnect();

			ticket = readTicket.toString();
		} catch (final Exception e) {
			e.printStackTrace();
			ret = false;
		}

		return ret;
	}

	Document parseString(String data)
			throws UnsupportedEncodingException, SAXException, IOException, ParserConfigurationException {
		final DocumentBuilderFactory documentBuilderFactory = DomUtils.newSecureDocumentBuilderFactory();
		final DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
		final Document doc = documentBuilder.parse(new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)));
		doc.setXmlStandalone(true);

		return doc;
	}

	@Override
	public boolean processEvent(KEvent event) {
		if (event == null || event.requestType == null) {
			return false;
		}
		try {
			switch (event.requestType) {
			case POST:
			case PUT:
				if (event.eventType == KEvent.EventType.NODE) {/// Récupère la liste des roles à notifier
					final Set<String[]> notif = dataProvider.getNotificationUserList(connection, userId, groupId,
							event.uuid);

					if (notif.isEmpty()) {
						return false;
					}

					final String context = dataProvider.getNode(connection, new MimeType("text/xml"), event.uuid, true,
							this.userId, this.groupId, null, null, null).toString();
					final Document docContext = parseString(context);
					final NodeList res = docContext.getElementsByTagName("asmResource");
					String blah = "";
					for (int i = 0; i < res.getLength(); ++i) {
						final Node r = res.item(i);
						final String type = r.getAttributes().getNamedItem("xsi_type").getNodeValue();
						if ("nodeRes".equals(type)) {
							final NodeList childs = r.getChildNodes();
							for (int j = 0; j < childs.getLength(); ++j) {
								final Node c = childs.item(j);
								final String cname = c.getNodeName();
								if ("label".equals(cname)) {
									final String lang = c.getAttributes().getNamedItem("lang").getNodeValue();
									if ("fr".equals(lang)) {
										blah = c.getTextContent();
										break;
									}
								}
							}
							break;
						}
					}

					final Iterator<String[]> userIter = notif.iterator();

					final Document doc = parseString(event.inputData);
					doc.getElementsByTagName("");
					final String portfolio = dataProvider.getPortfolioUuidByNodeUuid(connection, event.uuid);

					getSakaiTicket();

					final StringBuilder log = new StringBuilder("ticket:" + ticket + ";");
					while (userIter.hasNext()) {
						final String[] val = userIter.next();
						final String user = val[0];
						final String lastname = val[1];
						final int status = sendMessage(user,
								lastname +
										", user: " +
										username +
										" edited '" +
										blah +
										"' @ " +
										event.uuid +
										" in portfolio " +
										portfolio);
						log.append(user).append(":").append(status).append(";");
					}

					logger.debug("Sakai ticket {}", log);
				}
				break;

			default:
				break;
			}
		} catch (final Exception ex) {
			logger.error("Intercept error", ex);
			//TODO missing management
			//			logRestRequest(httpServletRequest, "", ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());
			//			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		return true;
	}

	int sendMessage(String user, String message) {
		int ret = 500;

		try {
			final String urlParameters = "notification=\"" + message + "\"&_sessionId=" + ticket;

			/// Send for this user
			final URL urlTicker = new URL("http://osp2.threecanoes.com/direct/notify/post/" + user);

			final HttpURLConnection connect = (HttpURLConnection) urlTicker.openConnection();
			connect.setDoOutput(true);
			connect.setDoInput(true);
			connect.setInstanceFollowRedirects(false);
			connect.setRequestMethod("POST");
			connect.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			connect.setRequestProperty("charset", "utf-8");
			connect.setRequestProperty("Content-Length", "" + Integer.toString(urlParameters.getBytes().length));
			connect.setUseCaches(false);
			connect.setRequestProperty("Cookie", sessionCookie);
			connect.connect();

			final DataOutputStream wr = new DataOutputStream(connect.getOutputStream());
			wr.writeBytes(urlParameters);
			wr.flush();
			wr.close();

			ret = connect.getResponseCode();

			logger.debug("Notification: {}", ret);
		} catch (final Exception e) {
			e.printStackTrace();
		}

		return ret;
	}

}