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

package com.portfolio.data.attachment;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MiniProxy extends HttpServlet {
    private static final long serialVersionUID = -5389232495090560087L;

    private static final Logger logger = LoggerFactory.getLogger(MiniProxy.class);
    /**
     *
     */
    String baseURL;

    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        baseURL = getServletConfig().getInitParameter("baseURL");

//		System.setProperty("https.protocols", "TLSv1,TLSv1.1,TLSv1.2");
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        /// Only people from our system can query
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("uid") == null) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            PrintWriter out;
            try {
                out = response.getWriter();
                out.write("403");
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }

        try {
            ///// Send wanted query
            String pathinfo = request.getPathInfo();
            String query = request.getQueryString();
            if ("/".equals(pathinfo) || pathinfo == null) {
                pathinfo = "";
                query = "?" + query;
            } else
                query = "";

            String queryURL = String.format("%s%s%s", baseURL, pathinfo, query);
            logger.info("Query to: {}", queryURL);

            URL urlConn = new URL(queryURL);
            HttpURLConnection connection = (HttpURLConnection) urlConn.openConnection();
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
//			connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            // Need some user agent, otherwise return 409
//			connection.connect();

            int code = connection.getResponseCode();
            String msg = connection.getResponseMessage();

            /// Write back answer if it went fine
            if (code != HttpURLConnection.HTTP_OK) {
                logger.error("Couldn't get data: " + msg);
                response.setStatus(code);
                PrintWriter writer = response.getWriter();
                writer.write(msg);
                writer.close();
            } else {
                OutputStream output = response.getOutputStream();
                InputStream inputData = connection.getInputStream();
                IOUtils.copy(inputData, output);
                inputData.close();
                output.close();
            }

            connection.disconnect();
        } catch (Exception e) {
			logger.error("Intercepted error", e);
			//TODO something is missing
        }

        response.setStatus(HttpServletResponse.SC_OK);
        return;
    }
}