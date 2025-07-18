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

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.eportfolium.karuta.data.utils.ConfigUtils;
import com.eportfolium.karuta.data.utils.DomUtils;
import com.eportfolium.karuta.data.utils.MailUtils;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

public class MailService extends HttpServlet {

	/**
	 *
	 */
	private static final Logger logger = LogManager.getLogger(MailService.class);
	private static final long serialVersionUID = 9188067506635747901L;

	boolean hasNodeReadRight = false;
	boolean hasNodeWriteRight = false;
	int userId;
	int groupId = -1;
	HttpSession session;
	ArrayList<String> ourIPs = new ArrayList<>();

	private String notification;
	private String sakaiInterfaceURL;
	private String sakaiUsername;
	private String sakaiPassword;
	private String sakaiDirectSessionURL;

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);

		/// List possible local address
		try {
			ConfigUtils.init(getServletContext());
			final var interfaces = NetworkInterface.getNetworkInterfaces();
			while (interfaces.hasMoreElements()) {
				final var current = interfaces.nextElement();
				if (!current.isUp() || current.isLoopback() || current.isVirtual()) {
					continue;
				}
				final var addresses = current.getInetAddresses();
				while (addresses.hasMoreElements()) {
					final var current_addr = addresses.nextElement();
					if (current_addr instanceof Inet4Address) {
						ourIPs.add(current_addr.getHostAddress());
					}
				}
			}
			notification = ConfigUtils.getInstance().getProperty("notification");
			sakaiInterfaceURL = ConfigUtils.getInstance().getRequiredProperty("sakaiInterface");
			sakaiUsername = ConfigUtils.getInstance().getRequiredProperty("sakaiUsername");
			sakaiPassword = ConfigUtils.getInstance().getRequiredProperty("sakaiPassword");
			sakaiDirectSessionURL = ConfigUtils.getInstance().getRequiredProperty("sakaiDirectSessionUrl");
		} catch (final Exception e) {
			logger.error("Can't init servlet", e);
			throw new ServletException(e);
		}
	}

	public void initialize(HttpServletRequest httpServletRequest) {
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) {
		/// Check if user is logged in
		final var session = request.getSession(false);
		if (session == null) {
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		final int uid = (Integer) session.getAttribute("uid");
		if (uid == 0) {
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		final var keepcc = BooleanUtils.toBoolean(request.getParameter("ccsender"));

		logger.trace("Sending mail for user '{}'", uid);

		var sender = "";
		var recipient = "";
		var subject = "";
		var message = "";

		final var writer = new StringWriter();
		try {
			IOUtils.copy(request.getInputStream(), writer, StandardCharsets.UTF_8);
			final var data = writer.toString();
			if ("".equals(data)) {
				response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				final var responsewriter = response.getWriter();
				responsewriter.println("Empty content");
				responsewriter.close();
				return;
			}
			final var doc = DomUtils.xmlString2Document(data, new StringBuilder());
			final var senderNode = doc.getElementsByTagName("sender");
			final var recipientNode = doc.getElementsByTagName("recipient");
			final var recipient_ccNode = doc.getElementsByTagName("recipient_cc");
			final var recipient_bccNode = doc.getElementsByTagName("recipient_bcc");
			final var subjectNode = doc.getElementsByTagName("subject");
			final var messageNode = doc.getElementsByTagName("message");

			/// From
			if (senderNode.getLength() > 0) {
				sender = senderNode.item(0).getFirstChild().getNodeValue();
			}
			/// Recipient
			if (recipientNode.getLength() > 0) {
				recipient = recipientNode.item(0).getFirstChild().getNodeValue();
			}
			/// CC
			if (recipient_ccNode.getLength() > 0 && recipient_ccNode.item(0).getFirstChild() != null) {
				recipient_ccNode.item(0).getFirstChild().getNodeValue();
			}
			/// BCC
			if (recipient_bccNode.getLength() > 0 && recipient_bccNode.item(0).getFirstChild() != null) {
				recipient_bccNode.item(0).getFirstChild().getNodeValue();
			}
			/// Subject
			if (subjectNode.getLength() > 0) {
				subject = subjectNode.item(0).getFirstChild().getNodeValue();
			}
			/// Message
			if (messageNode.getLength() > 0) {
				message = messageNode.item(0).getFirstChild().getNodeValue();
			}
		} catch (final Exception e) {
			logger.error("Erreur d'envoie de mail", e);
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			return;
		}

		final var config = getServletConfig();
		logger.trace("Message via '{}'", notification);

		var retval = 0;
		try {
			switch (notification) {
			case "email":
				if (keepcc) {
					retval = MailUtils.postMail(config, recipient, sender, subject, message, logger);
				} else {
					retval = MailUtils.postMail(config, recipient, "", subject, message, logger);
				}
				logger.trace("Mail to '{}' from '{}' by uid '{}'", recipient, sender, uid);
				break;
			case "sakai":
				final var recip = recipient.split(",");
				final var var = getSakaiTicket();

				for (final String user : recip) {
					final var status = sendMessage(var, user, message);
					logger.trace("Message sent to {} -> {}", user, status);
				}
				break;
			default:
				logger.error("Unknown notification method {} ", notification);
				throw new IllegalStateException(String.format("Unknown notification method '%s' ", notification));
			}
		} catch (final Exception e) {
			logger.error("Intercepted error", e);
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}

		try {
			if (retval < 0) {
				response.setStatus(HttpServletResponse.SC_GONE);
			}
			response.getOutputStream().close();
			request.getInputStream().close();
		} catch (final Exception e) {
			logger.error("Intercepted error", e);
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	String[] getSakaiTicket() {
		final String[] ret = { "", "" };
		try {
			/// Configurable?

			final var urlParameters = "_username=" + sakaiUsername + "&_password=" + sakaiPassword;

			/// Will have to use some context config
			final var urlTicker = new URL(sakaiDirectSessionURL);

			final var connect = (HttpURLConnection) urlTicker.openConnection();
			connect.setDoOutput(true);
			connect.setDoInput(true);
			connect.setInstanceFollowRedirects(false);
			connect.setRequestMethod("POST");
			connect.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			connect.setRequestProperty("charset", "utf-8");
			connect.setRequestProperty("Content-Length", String.valueOf(urlParameters.getBytes().length));
			connect.setUseCaches(false);
			connect.connect();

			final var wr = new DataOutputStream(connect.getOutputStream());
			wr.writeBytes(urlParameters);
			wr.flush();
			wr.close();

			final var readTicket = new StringBuilder();
			final var rd = new BufferedReader(new InputStreamReader(connect.getInputStream(), StandardCharsets.UTF_8));
			final var buffer = new char[1024];
			var offset = 0;
			int read;
			do {
				read = rd.read(buffer, offset, 1024);
				offset += read;
				readTicket.append(buffer);
			} while (read == 1024);
			rd.close();

			ret[1] = connect.getHeaderField("Set-Cookie");

			connect.disconnect();

			ret[0] = readTicket.toString();
		} catch (final Exception e) {
			logger.error("Intercepted error", e);
			//TODO something is missing
		}

		return ret;
	}

	int sendMessage(String[] auth, String user, String message) {
		var ret = 500;

		try {
			final var urlParameters = "notification=\"" + message + "\"&_sessionId=" + auth[0];

			/// Send for this user
			final var urlTicker = new URL(sakaiInterfaceURL + user);

			final var connect = (HttpURLConnection) urlTicker.openConnection();
			connect.setDoOutput(true);
			connect.setDoInput(true);
			connect.setInstanceFollowRedirects(false);
			connect.setRequestMethod("POST");
			connect.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			connect.setRequestProperty("charset", "utf-8");
			connect.setRequestProperty("Content-Length", String.valueOf(urlParameters.getBytes().length));
			connect.setUseCaches(false);
			connect.setRequestProperty("Cookie", auth[1]);
			connect.connect();

			final var wr = new DataOutputStream(connect.getOutputStream());
			wr.writeBytes(urlParameters);
			wr.flush();
			wr.close();

			ret = connect.getResponseCode();

			logger.trace("Notification '{}'", ret);
		} catch (final Exception e) {
			logger.error("Intercepted error", e);
			//TODO something is missing
		}

		return ret;
	}

}