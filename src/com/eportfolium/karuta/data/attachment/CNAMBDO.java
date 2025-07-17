/* =======================================================
	Copyright 2019 - ePortfolium - Licensed under the
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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.eportfolium.karuta.data.utils.ConfigUtils;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class CNAMBDO extends HttpServlet {
	private static final long serialVersionUID = -5389232495090560087L;

	static final Logger logger = LoggerFactory.getLogger(CNAMBDO.class);
	/**
	 *
	 */

	private String baseURL;
	private String clientid;
	private String clientsecret;

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		System.setProperty("https.protocols", "TLSv1.2");

		baseURL = ConfigUtils.getInstance().getProperty("CNAMBaseURL");
		clientid = ConfigUtils.getInstance().getProperty("CNAMclientid");
		clientsecret = ConfigUtils.getInstance().getProperty("CNAMclientsecret");
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) {
		/// Only people from our system can query
		final var session = request.getSession(false);
		if (session == null || session.getAttribute("uid") == null) {
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			PrintWriter out;
			try {
				out = response.getWriter();
				out.write("403");
				out.close();
			} catch (final IOException e) {
				e.printStackTrace();
			}
			return;
		}

		//// Login to service
		final var body = String.format("_username=%s&_password=%s", clientid, clientsecret);

		try {
			var urlConn = new URL(baseURL + "/login");
			var connection = (HttpURLConnection) urlConn.openConnection();
			connection.setDoOutput(true);
			connection.setUseCaches(false);
			connection.setInstanceFollowRedirects(false);
			connection.setRequestMethod("POST");
			connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

			/// Send login information
			final var bais = new ByteArrayInputStream(body.getBytes());
			final var outputData = connection.getOutputStream();
			final var transferred = IOUtils.copy(bais, outputData);
			if (transferred == body.length()) {
				logger.debug("Send: Complete");
			} else {
				logger.error("Send mismatch: " + transferred + " != " + body.length());
			}

			/// Read answer
			var code = connection.getResponseCode();
			var msg = connection.getResponseMessage();
			if (code != HttpURLConnection.HTTP_OK) {
				logger.error("Couldn't log: " + msg);
			} else {
				logger.debug("Code: (" + code + ") msg: " + msg);
			}

			final var logininfo = new StringBuilder();
			var line = "";
			final var objReturn = connection.getInputStream();
			final var breader = new BufferedReader(new InputStreamReader(objReturn, "UTF-8"));
			while ((line = breader.readLine()) != null) {
				logininfo.append(line);
			}
			connection.disconnect();

			/// Can't be bothered to parse json
			final var tokenregexp = "token\":\"([^\"]*)";
			final var ptoken = Pattern.compile(tokenregexp);
			final var pmatcher = ptoken.matcher(logininfo.toString());
			var access_token = "";
			if (pmatcher.find()) {
				access_token = pmatcher.group(1);
			}
			//			System.out.println("Current token:"+ access_token);

			///// Send wanted query
			var pathinfo = request.getPathInfo();
			var query = request.getQueryString();
			if ("/".equals(pathinfo) || pathinfo == null) {
				pathinfo = "";
				query = "?" + query;
			} else {
				query = "";
			}

			final var queryURL = String.format("%s%s%s", baseURL, pathinfo, query);
			logger.debug("Query to: " + queryURL);

			urlConn = new URL(queryURL);
			connection = (HttpURLConnection) urlConn.openConnection();
			connection.setRequestMethod("GET");
			connection.setRequestProperty("Authorization", String.format("Bearer %s", access_token));
			// Need some user agent, otherwise return 409
			//			connection.setRequestProperty("User-Agent", "Error 409 without user-agent");
			//			connection.connect();

			code = connection.getResponseCode();
			msg = connection.getResponseMessage();

			if (code != HttpURLConnection.HTTP_OK) {
				logger.error("Couldn't get data: " + msg);
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

			connection.disconnect();
		} catch (final MalformedURLException e) {
			e.printStackTrace();
		} catch (final IOException e) {
			e.printStackTrace();
		} catch (final Exception e) {
			e.printStackTrace();
		}

		response.setStatus(HttpServletResponse.SC_OK);
		return;
	}
}
