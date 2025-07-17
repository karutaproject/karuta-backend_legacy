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
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.eportfolium.karuta.data.utils.ConfigUtils;
import com.eportfolium.karuta.data.utils.MailUtils;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

public class MessageService extends HttpServlet {

	/**
	 *
	 */
	private static final Logger logger = LoggerFactory.getLogger(MessageService.class);
	private static final long serialVersionUID = 9188067506635747901L;

	boolean hasNodeReadRight = false;
	boolean hasNodeWriteRight = false;
	int userId;
	int groupId = -1;
	HttpSession session;

	private String notification;
	private String sakaiInterfaceURL;
	private String sakaiUsername;
	private String sakaiPassword;
	private String sakaiDirectSessionURL;

	public void initialize(HttpServletRequest httpServletRequest) throws Exception {
		ConfigUtils.init(getServletContext());
		notification = ConfigUtils.getInstance().getProperty("notification");
		sakaiInterfaceURL = ConfigUtils.getInstance().getRequiredProperty("sakaiInterface");
		sakaiUsername = ConfigUtils.getInstance().getRequiredProperty("sakaiUsername");
		sakaiPassword = ConfigUtils.getInstance().getRequiredProperty("sakaiPassword");
		sakaiDirectSessionURL = ConfigUtils.getInstance().getRequiredProperty("sakaiDirectSessionUrl");
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) {
		/// Check if user has an account
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

		/// From
		/// Recipient
		final var recipient = request.getParameter("recipient");
		/// CC
		final var recipient_cc = request.getParameter("recipient_cc");
		request.getParameter("recipient_bcc");
		/// Subject
		final var subject = request.getParameter("subject");
		/// Message
		final var message = request.getParameter("message");

		final var config = getServletConfig();
		logger.debug("Message to '{}'", notification);
		switch (notification) {
		case "email":
			try {
				MailUtils.postMail(config, recipient, recipient_cc, subject, message, logger);
			} catch (final Exception e) {
				logger.error(e.getMessage());
				//TODO Something is missing
			}
			break;
		case "sakai":
			/// Recipient is username list rather than email address
			final var recip = recipient.split(",");
			final var var = getSakaiTicket();

			for (final String user : recip) {
				final var status = sendMessage(var, user, message);
				logger.debug("Message sent to '{}' -> '{}' ", user, status);
			}
			break;
		default:
			logger.error("Unknown notification method {} ", notification);
			throw new IllegalStateException(String.format("Unknown notification method '%s' ", notification));
		}

		try {
			response.getOutputStream().close();
			request.getInputStream().close();
		} catch (final Exception e) {
			logger.error("Intercepted error", e);
			//TODO something is missing
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
			connect.setRequestProperty("Content-Length", "" + Integer.toString(urlParameters.getBytes().length));
			connect.setUseCaches(false);
			connect.connect();

			final var wr = new DataOutputStream(connect.getOutputStream());
			wr.writeBytes(urlParameters);
			wr.flush();
			wr.close();

			final var readTicket = new StringBuilder();
			final var rd = new BufferedReader(
					new InputStreamReader(connect.getInputStream(), StandardCharsets.UTF_8));
			final var buffer = new char[1024];
			var offset = 0;
			var read = 0;
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
			connect.setRequestProperty("Content-Length", "" + Integer.toString(urlParameters.getBytes().length));
			connect.setUseCaches(false);
			connect.setRequestProperty("Cookie", auth[1]);
			connect.connect();

			final var wr = new DataOutputStream(connect.getOutputStream());
			wr.writeBytes(urlParameters);
			wr.flush();
			wr.close();

			ret = connect.getResponseCode();

			logger.debug("Notification '{}'", ret);
		} catch (final Exception e) {
			logger.error("Intercepted error", e);
			//TODO something is missing
		}

		return ret;
	}
}