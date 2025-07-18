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

import javax.xml.parsers.ParserConfigurationException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.eportfolium.karuta.data.provider.DataProvider;
import com.eportfolium.karuta.data.utils.DomUtils;
import com.eportfolium.karuta.data.utils.SqlUtils;

import jakarta.activation.MimeType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

public class HandlerNotificationSakai implements KEventHandler {
	private static final Logger logger = LogManager.getLogger(HandlerNotificationSakai.class);
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
		} catch (Exception e) {
			e.printStackTrace();
		}

		this.session = request.getSession(true);
		var val = (Integer) session.getAttribute("uid");
		if (val != null) {
			this.userId = val;
		}
		val = (Integer) session.getAttribute("gid");
		if (val != null) {
			this.groupId = val;
		}
		this.username = (String) session.getAttribute("user");
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
					var notif = dataProvider.getNotificationUserList(connection, userId, groupId, event.uuid);

					if (notif.isEmpty()) {
						return false;
					}

					var context = dataProvider.getNode(connection, new MimeType("text/xml"), event.uuid, true,
							this.userId, this.groupId, null, null, null).toString();
					var docContext = parseString(context);
					var res = docContext.getElementsByTagName("asmResource");
					var blah = "";
					for (var i = 0; i < res.getLength(); ++i) {
						var r = res.item(i);
						var type = r.getAttributes().getNamedItem("xsi_type").getNodeValue();
						if ("nodeRes".equals(type)) {
							var childs = r.getChildNodes();
							for (var j = 0; j < childs.getLength(); ++j) {
								var c = childs.item(j);
								var cname = c.getNodeName();
								if ("label".equals(cname)) {
									var lang = c.getAttributes().getNamedItem("lang").getNodeValue();
									if ("fr".equals(lang)) {
										blah = c.getTextContent();
										break;
									}
								}
							}
							break;
						}
					}

					var doc = parseString(event.inputData);
					doc.getElementsByTagName("");
					var type = "";

					var portfolio = dataProvider.getPortfolioUuidByNodeUuid(connection, event.uuid);

					getSakaiTicket();

					var log = new StringBuilder("ticket:" + ticket + ";");
					for (String[] val : notif) {
						var user = val[0];
						var lastname = val[1];
						var status = sendMessage(user,
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
		} catch (Exception ex) {
			logger.error("Intercept error", ex);
			//TODO missing management
			//			logRestRequest(httpServletRequest, "", ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());
			//			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		return true;
	}

	boolean getSakaiTicket() {
		var ret = true;
		try {
			/// Configurable?
			var urlParameters = "_username=testadmin&_password=testadmin";

			/// Will have to use some context config
			var urlTicker = new URL("http://osp2.threecanoes.com/direct/session");

			var connect = (HttpURLConnection) urlTicker.openConnection();
			connect.setDoOutput(true);
			connect.setDoInput(true);
			connect.setInstanceFollowRedirects(false);
			connect.setRequestMethod("POST");
			connect.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			connect.setRequestProperty("charset", "utf-8");
			connect.setRequestProperty("Content-Length", "" + Integer.toString(urlParameters.getBytes().length));
			connect.setUseCaches(false);
			connect.connect();

			var wr = new DataOutputStream(connect.getOutputStream());
			wr.writeBytes(urlParameters);
			wr.flush();
			wr.close();

			var readTicket = new StringBuilder();
			var rd = new BufferedReader(new InputStreamReader(connect.getInputStream(), StandardCharsets.UTF_8));
			var buffer = new char[1024];
			var offset = 0;
			var read = 0;
			do {
				read = rd.read(buffer, offset, 1024);
				offset += read;
				readTicket.append(buffer);
			} while (read == 1024);
			rd.close();

			sessionCookie = connect.getHeaderField("Set-Cookie");

			connect.disconnect();

			ticket = readTicket.toString();
		} catch (Exception e) {
			e.printStackTrace();
			ret = false;
		}

		return ret;
	}

	Document parseString(String data)
			throws UnsupportedEncodingException, SAXException, IOException, ParserConfigurationException {
		var documentBuilderFactory = DomUtils.newSecureDocumentBuilderFactory();
		var documentBuilder = documentBuilderFactory.newDocumentBuilder();
		var doc = documentBuilder.parse(new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)));
		doc.setXmlStandalone(true);

		return doc;
	}

	int sendMessage(String user, String message) {
		var ret = 500;

		try {
			var urlParameters = "notification=\"" + message + "\"&_sessionId=" + ticket;

			/// Send for this user
			var urlTicker = new URL("http://osp2.threecanoes.com/direct/notify/post/" + user);

			var connect = (HttpURLConnection) urlTicker.openConnection();
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

			var wr = new DataOutputStream(connect.getOutputStream());
			wr.writeBytes(urlParameters);
			wr.flush();
			wr.close();

			ret = connect.getResponseCode();

			logger.debug("Notification: {}", ret);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return ret;
	}

}