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
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.portfolio.data.utils.ConfigUtils;
import com.portfolio.data.utils.DomUtils;
import com.portfolio.data.utils.MailUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

public class MailService extends HttpServlet {

    /**
     *
     */
    private static final Logger logger = LoggerFactory.getLogger(MailService.class);
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
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface current = interfaces.nextElement();
                if (!current.isUp() || current.isLoopback() || current.isVirtual()) continue;
                Enumeration<InetAddress> addresses = current.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress current_addr = addresses.nextElement();
                    if (current_addr instanceof Inet4Address)
                        ourIPs.add(current_addr.getHostAddress());
                }
            }
            notification = ConfigUtils.getInstance().getProperty("notification");
            sakaiInterfaceURL = ConfigUtils.getInstance().getRequiredProperty("sakaiInterface");
            sakaiUsername = ConfigUtils.getInstance().getRequiredProperty("sakaiUsername");
            sakaiPassword = ConfigUtils.getInstance().getRequiredProperty("sakaiPassword");
            sakaiDirectSessionURL = ConfigUtils.getInstance().getRequiredProperty("sakaiDirectSessionUrl");
        } catch (Exception e) {
            logger.error("Can't init servlet", e);
            throw new ServletException(e);
        }
    }

    public void initialize(HttpServletRequest httpServletRequest) {
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

        final boolean keepcc = BooleanUtils.toBoolean(request.getParameter("ccsender"));

        logger.trace("Sending mail for user '{}'", uid);

        String sender = "";
        String recipient = "";
        String recipient_cc = "";
        String recipient_bcc = "";
        String subject = "";
        String message = "";

        StringWriter writer = new StringWriter();
        try {
            IOUtils.copy(request.getInputStream(), writer, StandardCharsets.UTF_8);
            String data = writer.toString();
            if ("".equals(data)) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                PrintWriter responsewriter = response.getWriter();
                responsewriter.println("Empty content");
                responsewriter.close();
                return;
            }
            Document doc = DomUtils.xmlString2Document(data, new StringBuffer());
            NodeList senderNode = doc.getElementsByTagName("sender");
            NodeList recipientNode = doc.getElementsByTagName("recipient");
            NodeList recipient_ccNode = doc.getElementsByTagName("recipient_cc");
            NodeList recipient_bccNode = doc.getElementsByTagName("recipient_bcc");
            NodeList subjectNode = doc.getElementsByTagName("subject");
            NodeList messageNode = doc.getElementsByTagName("message");

            /// From
            if (senderNode.getLength() > 0)
                sender = senderNode.item(0).getFirstChild().getNodeValue();
            /// Recipient
            if (recipientNode.getLength() > 0)
                recipient = recipientNode.item(0).getFirstChild().getNodeValue();
            /// CC
            if (recipient_ccNode.getLength() > 0 && recipient_ccNode.item(0).getFirstChild() != null)
                recipient_cc = recipient_ccNode.item(0).getFirstChild().getNodeValue();
            /// BCC
            if (recipient_bccNode.getLength() > 0 && recipient_bccNode.item(0).getFirstChild() != null)
                recipient_bcc = recipient_bccNode.item(0).getFirstChild().getNodeValue();
            /// Subject
            if (subjectNode.getLength() > 0)
                subject = subjectNode.item(0).getFirstChild().getNodeValue();
            /// Message
            if (messageNode.getLength() > 0)
                message = messageNode.item(0).getFirstChild().getNodeValue();
        } catch (Exception e) {
            logger.error("Erreur d'envoie de mail", e);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        ServletConfig config = getServletConfig();
        logger.trace("Message via '{}'", notification);

        int retval = 0;
        try {
            switch (notification) {
                case "email":
                    if (keepcc)
                        retval = MailUtils.postMail(config, recipient, sender, subject, message, logger);
                    else
                        retval = MailUtils.postMail(config, recipient, "", subject, message, logger);
                    logger.trace("Mail to '{}' from '{}' by uid '{}'", recipient, sender, uid);
                    break;
                case "sakai":
                    final String[] recip = recipient.split(",");
                    final String[] var = getSakaiTicket();

                    for (String user : recip) {
                        int status = sendMessage(var, user, message);
                        logger.trace("Message sent to {} -> {}", user, status);
                    }
                    break;
                default:
                    logger.error("Unknown notification method {} ", notification);
					throw new IllegalStateException(String.format("Unknown notification method '%s' ", notification));
            }
        } catch (Exception e) {
            logger.error("Intercepted error", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }

        try {
            if (retval < 0)
                response.setStatus(HttpServletResponse.SC_GONE);
            response.getOutputStream().close();
            request.getInputStream().close();
        } catch (Exception e) {
            logger.error("Intercepted error", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    int sendMessage(String[] auth, String user, String message) {
        int ret = 500;

        try {
            String urlParameters = "notification=\"" + message + "\"&_sessionId=" + auth[0];

            /// Send for this user
            URL urlTicker = new URL(sakaiInterfaceURL + user);

            HttpURLConnection connect = (HttpURLConnection) urlTicker.openConnection();
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

            DataOutputStream wr = new DataOutputStream(connect.getOutputStream());
            wr.writeBytes(urlParameters);
            wr.flush();
            wr.close();

            ret = connect.getResponseCode();

            logger.trace("Notification '{}'", ret);
        } catch (Exception e) {
            logger.error("Intercepted error", e);
            //TODO something is missing
        }

        return ret;
    }

    String[] getSakaiTicket() {
        String[] ret = {"", ""};
        try {
            /// Configurable?


            final String urlParameters = "_username=" + sakaiUsername + "&_password=" + sakaiPassword;

            /// Will have to use some context config
            URL urlTicker = new URL(sakaiDirectSessionURL);

            HttpURLConnection connect = (HttpURLConnection) urlTicker.openConnection();
            connect.setDoOutput(true);
            connect.setDoInput(true);
            connect.setInstanceFollowRedirects(false);
            connect.setRequestMethod("POST");
            connect.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connect.setRequestProperty("charset", "utf-8");
            connect.setRequestProperty("Content-Length", String.valueOf(urlParameters.getBytes().length));
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
        } catch (Exception e) {
            logger.error("Intercepted error", e);
            //TODO something is missing
        }

        return ret;
    }

}