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
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.SQLException;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.eportfolium.karuta.data.provider.DataProvider;
import com.eportfolium.karuta.data.utils.DomUtils;
import com.eportfolium.karuta.data.utils.SqlUtils;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ReportService extends HttpServlet {
	private static final Logger logger = LogManager.getLogger(ReportService.class);
	private static final long serialVersionUID = -1464636556529383111L;
	/**
	 *
	 */

	DataProvider dataProvider = null;

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		try {
			dataProvider = SqlUtils.initProvider();
		} catch (final Exception e) {
			logger.error("Can't init servlet", e);
			throw new ServletException(e);
		}

	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) {
		/// Check if user is logged in
		final var session = request.getSession(false);
		if (session == null || session.getAttribute("uid") == null) {
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		final int uid = (Integer) session.getAttribute("uid");
		if (uid == 0) {
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		Connection c = null;
		try {
			/// Find user's username
			c = SqlUtils.getConnection();
			final var userinfo = dataProvider.getInfUser(c, 1, uid);
			final var doc = DomUtils.xmlString2Document(userinfo, null);
			final var usernameNodes = doc.getElementsByTagName("username");
			final var username = usernameNodes.item(0).getTextContent();

			if (null == username) {
				response.setStatus(400);
				final var writer = response.getWriter();
				writer.append("Username error");
				writer.close();
				return;
			}

			final var pathinfo = request.getPathInfo();
			var urlTarget = "http://127.0.0.1:8081";
			/// FIXME: Add user id in filename
			if (pathinfo != null && pathinfo.length() > 0) {
				urlTarget += "/" + username + "__" + pathinfo.substring(1);
			}
			logger.info("Path: " + pathinfo + " -> " + urlTarget);

			/// Create connection
			final var urlConn = new URL(urlTarget);
			final var connection = (HttpURLConnection) urlConn.openConnection();
			connection.setDoOutput(true);
			connection.setUseCaches(false);
			connection.setInstanceFollowRedirects(false);
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

			connection.connect();

			/// Those 2 lines are needed, otherwise, no request sent
			final var code = connection.getResponseCode();
			final var msg = connection.getResponseMessage();

			if (code != HttpURLConnection.HTTP_OK) {
				logger.error("Couldn't send file: " + msg);
				response.setStatus(code);
				final var writer = response.getWriter();
				writer.write(msg);
				writer.close();
			} else {
				final OutputStream output = response.getOutputStream();
				/// Send data to report daemon
				final var inputData = connection.getInputStream();
				IOUtils.copy(inputData, output);
				inputData.close();
				output.close();
			}

			// Close connection to report daemon
			connection.disconnect();
		} catch (final Exception e) {
			logger.error("Intercepted error", e);
			//TODO something is missing
			response.setStatus(500);
		} finally {
			/// Close connections
			try {
				if (c != null) {
					c.close();
					//			request.getReader().close();
					//			response.getWriter().close();
				}
			} catch (final Exception e) {
				logger.error("Intercepted error", e);
				//TODO something is missing
			}
		}

	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) {
		/// Check if user is logged in
		final var session = request.getSession(false);
		//		/*
		if (session == null) {
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		final int uid = (Integer) session.getAttribute("uid");
		if (uid == 0) {
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			return;
		}
		//*/

		Connection c = null;
		try {
			/// Find user's username
			c = SqlUtils.getConnection();
			final var userinfo = dataProvider.getInfUser(c, 1, uid);
			final var doc = DomUtils.xmlString2Document(userinfo, null);
			final var usernameNodes = doc.getElementsByTagName("username");
			final var username = usernameNodes.item(0).getTextContent();

			if (null == username) {
				response.setStatus(400);
				final var writer = response.getWriter();
				writer.append("Username error");
				writer.close();
				return;
			}

			/// Prepare to transfer username to report daemon
			final var writer = new StringWriter();
			IOUtils.copy(request.getInputStream(), writer, Charset.defaultCharset());
			var data = writer.toString();

			data += "&user=" + username;

			final var pathinfo = request.getPathInfo();
			var urlTarget = "http://127.0.0.1:8081";
			if (pathinfo != null) {
				urlTarget += pathinfo;
			}
			logger.debug("Path: {} -> {}", pathinfo, urlTarget);

			/// Create connection
			final var urlConn = new URL(urlTarget);
			final var connection = (HttpURLConnection) urlConn.openConnection();
			connection.setDoOutput(true);
			connection.setUseCaches(false);
			connection.setInstanceFollowRedirects(false);
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
				/// Prevent case when "connection: closed" make post not send data
				if (key.equals("connection")) {
					continue;
				}

				connection.setRequestProperty(key, value);
			}

			connection.connect();

			/// Send data to report daemon
			logger.debug("Sending: {}", data);
			final var bais = new ByteArrayInputStream(data.getBytes());
			final var outputData = connection.getOutputStream();
			final var transferred = IOUtils.copy(bais, outputData);
			if (transferred == data.length()) {
				logger.debug("Send: Complete");
			} else {
				logger.error("Send mismatch: " + transferred + " != " + data.length());
			}

			/// Those 2 lines are needed, otherwise, no request sent
			final var code = connection.getResponseCode();
			final var msg = connection.getResponseMessage();

			if (code != HttpURLConnection.HTTP_OK) {
				logger.error("Couldn't send file: {}", msg);
			} else {
				logger.debug("Code: ({}) msg {} ", code, msg);
			}

			/// Retrieving info
			final var objReturn = connection.getInputStream();

			/// Write back daemon response
			final var os = response.getOutputStream();
			IOUtils.copy(objReturn, os);

			// Close connection to report daemon
			connection.disconnect();

			objReturn.close();
			os.close();
		} catch (final Exception e) {
			logger.error("Intercepted error", e);
			//TODO something is missing
			response.setStatus(500);
		} finally {
			try {
				if (c != null) {
					c.close();
				}
				request.getInputStream().close();
				//				response.getWriter().close();
			} catch (SQLException | IOException e) {
				logger.error("Intercepted error", e);
				//TODO something is missing
			}
		}

	}
}