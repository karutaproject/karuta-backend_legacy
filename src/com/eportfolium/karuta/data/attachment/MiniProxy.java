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

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class MiniProxy extends HttpServlet {
	private static final long serialVersionUID = -5389232495090560087L;

	private static final Logger logger = LoggerFactory.getLogger(MiniProxy.class);
	/**
	 *
	 */
	String baseURL;

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		baseURL = getServletConfig().getInitParameter("baseURL");
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

		try {
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
			logger.info("Query to: {}", queryURL);

			final var urlConn = new URL(queryURL);
			final var connection = (HttpURLConnection) urlConn.openConnection();
			connection.setDoOutput(true);
			connection.setUseCaches(false);
			connection.setInstanceFollowRedirects(false);
			connection.setRequestMethod("GET");
			//			connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

			// Need some user agent, otherwise return 409
			//			connection.connect();

			final var code = connection.getResponseCode();
			final var msg = connection.getResponseMessage();

			/// Write back answer if it went fine
			if (code != HttpURLConnection.HTTP_OK) {
				logger.error("Couldn't get data: " + msg);
				response.setStatus(code);
				final var writer = response.getWriter();
				writer.write(msg);
				writer.close();
			} else {
				final OutputStream output = response.getOutputStream();
				final var inputData = connection.getInputStream();
				IOUtils.copy(inputData, output);
				inputData.close();
				output.close();
			}

			connection.disconnect();
		} catch (final Exception e) {
			logger.error("Intercepted error", e);
			//TODO something is missing
		}

		response.setStatus(HttpServletResponse.SC_OK);
		return;
	}
}