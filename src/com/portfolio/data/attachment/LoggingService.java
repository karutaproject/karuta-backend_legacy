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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.portfolio.data.provider.DataProvider;
import com.portfolio.data.utils.ConfigUtils;
import com.portfolio.data.utils.DomUtils;
import com.portfolio.data.utils.LogUtils;
import com.portfolio.data.utils.SqlUtils;
import com.portfolio.security.Credential;
import org.apache.commons.lang3.BooleanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

public class LoggingService extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(LoggingService.class);
    private static final long serialVersionUID = -1464636556529383111L;
    /**
     *
     */

    DataProvider dataProvider = null;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        try {
            ConfigUtils.init(getServletContext());
            LogUtils.initDirectory(getServletContext());

            dataProvider = SqlUtils.initProvider();
        } catch (Exception e) {
            logger.error("Can't init servlet", e);
            throw new ServletException(e);
        }

    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        /// Check if user is logged in
        HttpSession session = request.getSession(false);
        if (session == null) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        int uid = (Integer) session.getAttribute("uid");
        if (uid == 0) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        final Credential credential = new Credential();
        /// Check if user is admin
        Connection c;
        try {
            c = SqlUtils.getConnection();
            if (credential.isAdmin(c, uid)) {
                /// Logfile name
                final String loggingLine = request.getParameter("n");
                final String filename = ConfigUtils.getInstance().getProperty("logfile_" + loggingLine);

                if (filename == null)    // Wanting an undefined logfile
                {
                    response.setStatus(400);
                    PrintWriter writer = response.getWriter();
                    writer.append("Undefined log file");
                    writer.close();
                    return;
                }

                FileReader fr = new FileReader(filename);
                BufferedReader bread = new BufferedReader(fr);
                OutputStreamWriter osw = new OutputStreamWriter(response.getOutputStream());
                BufferedWriter bwrite = new BufferedWriter(osw);

                char[] buffer = new char[1024];
                int offset = 0;
                int read = 1;

                while (read > 0) {
                    read = bread.read(buffer, offset, 1024);
                    offset += read;
                    bwrite.write(buffer);
                }

                /// Cleanup
                bread.close();
            }
        } catch (Exception e) {
            logger.error("Intercept error", e);
            //TODO managing error
        }

        /// Close connections
        try {
            request.getReader().close();
            response.getWriter().close();
        } catch (Exception e) {
            logger.error("Intercept error", e);
            //TODO managing error
        }

    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        /// Check if user is logged in
        HttpSession session = request.getSession(false);
        if (session == null) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        int uid = (Integer) session.getAttribute("uid");
        if (uid == 0) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        Connection c = null;
        boolean raw = false;
        try {
            Integer val = (Integer) session.getAttribute("uid");
            /// Basic check if user is logged on
            if (val == null) {
                response.setStatus(403);
                return;
            }

            /// Logfile name
            final String loggingLine = request.getParameter("n");
            final String filename = ConfigUtils.getInstance().getProperty("logfile_" + loggingLine);

            if (filename == null)    // Wanting an undefined logfile
            {
                response.setStatus(400);
                PrintWriter writer = response.getWriter();
                writer.append("Undefined log file");
                writer.close();
                return;
            }

            String context = request.getContextPath();
            String username = "";
            String showuser = request.getParameter("user");
            String rawparam = request.getParameter("raw");
            if (BooleanUtils.toBoolean(rawparam)) {
                raw = true;
            }

            if (BooleanUtils.toBoolean(showuser)) {
                c = SqlUtils.getConnection();
                String userinfo = dataProvider.getInfUser(c, 1, val);
                Document doc = DomUtils.xmlString2Document(userinfo, null);
                NodeList usernameNodes = doc.getElementsByTagName("username");
                username = usernameNodes.item(0).getTextContent();
//				dataProvider.disconnect();
            }

            /// Complete path
            InputStreamReader bis = new InputStreamReader(request.getInputStream(), StandardCharsets.UTF_8);
            BufferedReader bread = new BufferedReader(bis);

            BufferedWriter bwrite = LogUtils.getLog(filename);
            if (!raw) {
                String outputformat = "%s : %s - '%s' -- ";
                bwrite.write(String.format(outputformat,  LogUtils.getCurrentDate(), context, username));
            }
            String s;
            while ((s = bread.readLine()) != null) {
                bwrite.write(s);
                bwrite.newLine();
            }
            bwrite.flush();
            bwrite.close();
        } catch (Exception e) {
            logger.error("Intercept error", e);
            //TODO managing error
        } finally {
            try {
                if (c != null) c.close();
                request.getInputStream().close();
                response.getWriter().close();
            } catch (SQLException | IOException e) {
                logger.error("Intercept error", e);
                //TODO managing error
            }
        }

    }
}