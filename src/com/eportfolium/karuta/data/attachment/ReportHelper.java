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

package com.eportfolium.karuta.data.attachment;

import java.io.BufferedReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.regex.Pattern;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import com.eportfolium.karuta.data.provider.ReportHelperProvider;
import com.eportfolium.karuta.data.utils.DomUtils;
import com.eportfolium.karuta.data.utils.LogUtils;
import com.eportfolium.karuta.data.utils.SqlUtils;
import com.eportfolium.karuta.security.Credential;
import org.apache.http.entity.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
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
            response.setContentType(ContentType.APPLICATION_XML.getMimeType());
            response.setCharacterEncoding(StandardCharsets.UTF_8.toString());
            OutputStream output = response.getOutputStream();
            output.write(vectorValue.getBytes(StandardCharsets.UTF_8));
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
            DocumentBuilderFactory documentBuilderFactory = DomUtils.newSecureDocumentBuilderFactory();
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            Document doc = documentBuilder.parse(request.getInputStream());
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

            // Inverse rights to create groups
            NodeList nList = doc.getElementsByTagName("rights");
            HashMap<String, HashSet<String>> groups = new HashMap<String, HashSet<String>>();
            String[] attribName = {"w","r","d"};
            Node nRight = nList.item(0);
            if (nRight != null) {
                NamedNodeMap attribs = nRight.getAttributes();
                for (String att : attribName) {
                    Node value = attribs.getNamedItem(att);
                    if (value == null) continue;
                    String names = value.getTextContent();
                    String[] split = names.split(",");
                    for (String s : split) {
                        s = s.trim();
                        HashSet<String> right = groups.get(s);
                        if (right == null) {
                            right = new HashSet<String>();
                            groups.put(s, right);
                        }
                        right.add(att);
                    }
                }
            }

            /// Send query
            c = SqlUtils.getConnection();
            c.setAutoCommit(false);
            int retValue = dataProvider.writeVector(c, uid, map, groups);

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
            try {
                if (c != null)
                    c.rollback();
            } catch( SQLException e1 ) {
                logger.error("SQLException",e1);
            }
            response.setStatus(500);
        } finally {
            try {
                if (c != null) {
                    c.commit();
                    c.close();
                }
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