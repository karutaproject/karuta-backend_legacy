/* =======================================================
	Copyright 2021 - ePortfolium - Licensed under the
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
import java.io.OutputStream;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.regex.Pattern;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.portfolio.data.provider.ReportHelperProvider;
import com.portfolio.data.utils.DomUtils;
import com.portfolio.data.utils.LogUtils;
import com.portfolio.data.utils.SqlUtils;
import com.portfolio.security.Credential;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ReportHelper extends HttpServlet {
    public static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    public static final Pattern PATTERN = Pattern.compile("a1?\\d");
    static final Logger logger = LoggerFactory.getLogger(ReportHelper.class);

    final private Credential cred = new Credential();

    ReportHelperProvider dataProvider = null;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        try {
            LogUtils.initDirectory(getServletContext());

            dataProvider = SqlUtils.initProviderHelper();
        } catch (Exception e) {
            logger.error("Can't init servlet", e);
            throw new ServletException(e);
        }
    }

    // Searching
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        /// Check if user is logged in
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("uid") == null) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        int uid = (Integer) session.getAttribute("uid");
        if (uid == 0) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        Connection c = null;
        try {
            HashMap<String, String> map = new HashMap<>();

            //// Process input
            // If there's a userid
            String requested_uid_str = request.getParameter("userid");
            if (requested_uid_str != null) {
                int requested_uid = Integer.parseInt(requested_uid_str);
                if (requested_uid > 0)
                    map.put("userid", requested_uid_str);
            }
            // Column parameters
            for (int i = 1; i <= 10; i++) {
                String key = "a" + i;
                String value = request.getParameter(key);
                if (value != null)
                    map.put(key, value);
            }
            /// Query
            c = SqlUtils.getConnection();
            String vectorValue = dataProvider.getVector(c, uid, map);

            // Send result
            OutputStream output = response.getOutputStream();
            output.write(vectorValue.getBytes());
            output.close();

        } catch (Exception e) {
            logger.error("Exception", e);
            response.setStatus(500);
        } finally {
            /// Close connections
            try {
                if (c != null)
                    c.close();
            } catch (Exception e) {
                logger.error("Exception", e);
            }
        }

    }

    // Write vector
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        /// Check if user is logged in
        HttpSession session = request.getSession(false);
//		/*
        if (session == null) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        int uid = (Integer) session.getAttribute("uid");
        if (uid == 0) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        //*/

        Connection c = null;
        BufferedReader reader;
        try {
            StringBuilder sb = new StringBuilder();
            reader = request.getReader();
            char[] buffer = new char[128];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, read);
            }
            reader.close();
            String data = sb.toString();
            logger.debug("RECEIVED: {}", data);
            if ("".equals(data)) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                PrintWriter responsewriter = response.getWriter();
                responsewriter.println("Empty content");
                responsewriter.close();
                return;
            }
            Document doc = DomUtils.xmlString2Document(data, new StringBuffer());
            NodeList vectorNode = doc.getElementsByTagName("vector");
            HashMap<String, String> map = new HashMap<>();
            map.put("userid", Integer.toString(uid));
            if (vectorNode.getLength() == 1) {

                Node a_node = vectorNode.item(0).getFirstChild();
                while (a_node != null) {
                    String name = a_node.getNodeName();
                    String val = a_node.getTextContent();
                    if (PATTERN.matcher(name).find())
                        map.put(name, val);
                    a_node = a_node.getNextSibling();
                }
            }

            /// Send query
            c = SqlUtils.getConnection();
            int retValue = dataProvider.writeVector(c, uid, map);

            // Send result
            OutputStream output = response.getOutputStream();
            String text = "OK";
            if (retValue < 0) {
                response.setStatus(304);
                text = "Not modified";
            }
            output.write(text.getBytes());
            output.close();

        } catch (Exception e) {
            logger.error("Exception", e);
            response.setStatus(500);
        } finally {
            try {
                if (c != null) c.close();
            } catch (SQLException e) {
                logger.error("SQLException", e);
            }
        }

    }

    /// Delete specific vector
    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) {
        /// Check if user is logged in
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("uid") == null) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        int uid = (Integer) session.getAttribute("uid");
        if (uid == 0) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        Connection c = null;
        try {
            HashMap<String, String> map = new HashMap<>();

            //// Process input
            // If there's a userid
            String date = request.getParameter("date");
            if (date != null) {
                Date d = DATE_FORMAT.parse(date);
                map.put("date", DATE_FORMAT.format(d));
            }
            // Column parameters
            for (int i = 1; i <= 10; i++) {
                String key = "a" + i;
                String value = request.getParameter(key);
                if (value != null)
                    map.put(key, value);
            }

            /// Query
            c = SqlUtils.getConnection();
            if (!cred.isAdmin(c, uid))
                map.put("userid", Integer.toString(uid));

            int value = dataProvider.deleteVector(c, map);

            // Send result
            OutputStream output = response.getOutputStream();
            output.write(Integer.toString(value).getBytes());
            output.close();

        } catch (Exception e) {
            logger.error("Exception", e);
            response.setStatus(500);
        } finally {
            /// Close connections
            try {
                if (c != null)
                    c.close();
            } catch (Exception e) {
                logger.error("Exception", e);
            }
        }

    }
}