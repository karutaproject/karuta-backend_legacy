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
import java.util.Iterator;
import java.util.Set;

import javax.activation.MimeType;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import com.eportfolium.karuta.data.provider.DataProvider;
import com.eportfolium.karuta.data.utils.DomUtils;
import com.eportfolium.karuta.data.utils.SqlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;


public class HandlerNotificationSakai implements KEventHandler {
	private static final Logger logger = LoggerFactory.getLogger(HandlerNotificationSakai.class);
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
        Integer val = (Integer) session.getAttribute("uid");
        if (val != null)
            this.userId = val;
        val = (Integer) session.getAttribute("gid");
        if (val != null)
            this.groupId = val;
        this.username = (String) session.getAttribute("user");
    }

    @Override
    public boolean processEvent(KEvent event) {
        if (event == null || event.requestType == null) return false;
        try {
            switch (event.requestType) {
                case POST:
                case PUT:
                    if (event.eventType == KEvent.EventType.NODE) {/// Récupère la liste des roles à notifier
                        Set<String[]> notif = dataProvider.getNotificationUserList(connection, userId, groupId, event.uuid);

                        if (notif.isEmpty())
                            return false;

                        String context = dataProvider.getNode(connection, new MimeType("text/xml"), event.uuid, true, this.userId, this.groupId, null, null, null).toString();
                        Document docContext = parseString(context);
                        NodeList res = docContext.getElementsByTagName("asmResource");
                        String blah = "";
                        for (int i = 0; i < res.getLength(); ++i) {
                            Node r = res.item(i);
                            String type = r.getAttributes().getNamedItem("xsi_type").getNodeValue();
                            if ("nodeRes".equals(type)) {
                                NodeList childs = r.getChildNodes();
                                for (int j = 0; j < childs.getLength(); ++j) {
                                    Node c = childs.item(j);
                                    String cname = c.getNodeName();
                                    if ("label".equals(cname)) {
                                        String lang = c.getAttributes().getNamedItem("lang").getNodeValue();
                                        if ("fr".equals(lang)) {
                                            blah = c.getTextContent();
                                            break;
                                        }
                                    }
                                }
                                break;
                            }
                        }

                        Iterator<String[]> userIter = notif.iterator();

                        Document doc = parseString(event.inputData);
                        doc.getElementsByTagName("");
                        String type = "";

                        String portfolio = dataProvider.getPortfolioUuidByNodeUuid(connection, event.uuid);

                        getSakaiTicket();

                        StringBuilder log = new StringBuilder("ticket:" + ticket + ";");
                        while (userIter.hasNext()) {
                            String[] val = userIter.next();
                            String user = val[0];
                            String lastname = val[1];
                            int status = sendMessage(user, lastname + ", user: " + username + " edited '" + blah + "' @ " + event.uuid + " in portfolio " + portfolio);
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

    int sendMessage(String user, String message) {
        int ret = 500;

        try {
            String urlParameters = "notification=\"" + message + "\"&_sessionId=" + ticket;

            /// Send for this user
            URL urlTicker = new URL("http://osp2.threecanoes.com/direct/notify/post/" + user);

            HttpURLConnection connect = (HttpURLConnection) urlTicker.openConnection();
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

            DataOutputStream wr = new DataOutputStream(connect.getOutputStream());
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

    boolean getSakaiTicket() {
        boolean ret = true;
        try {
            /// Configurable?
            String urlParameters = "_username=testadmin&_password=testadmin";

            /// Will have to use some context config
            URL urlTicker = new URL("http://osp2.threecanoes.com/direct/session");

            HttpURLConnection connect = (HttpURLConnection) urlTicker.openConnection();
            connect.setDoOutput(true);
            connect.setDoInput(true);
            connect.setInstanceFollowRedirects(false);
            connect.setRequestMethod("POST");
            connect.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connect.setRequestProperty("charset", "utf-8");
            connect.setRequestProperty("Content-Length", "" + Integer.toString(urlParameters.getBytes().length));
            connect.setUseCaches(false);
            connect.connect();

            DataOutputStream wr = new DataOutputStream(connect.getOutputStream());
            wr.writeBytes(urlParameters);
            wr.flush();
            wr.close();

            StringBuilder readTicket = new StringBuilder();
            BufferedReader rd = new BufferedReader(new InputStreamReader(connect.getInputStream(), StandardCharsets.UTF_8));
            char[] buffer = new char[1024];
            int offset = 0;
            int read = 0;
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

    Document parseString(String data) throws UnsupportedEncodingException, SAXException, IOException, ParserConfigurationException {
        DocumentBuilderFactory documentBuilderFactory = DomUtils.newSecureDocumentBuilderFactory();
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        Document doc = documentBuilder.parse(new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)));
        doc.setXmlStandalone(true);

        return doc;
    }

}