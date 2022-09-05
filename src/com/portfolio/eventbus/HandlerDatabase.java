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

package com.portfolio.eventbus;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;

import javax.activation.MimeType;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.ws.rs.core.Response.Status;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import com.portfolio.data.provider.DataProvider;
import com.portfolio.data.provider.MysqlDataProvider;
import com.portfolio.data.utils.DomUtils;
import com.portfolio.data.utils.SqlUtils;
import com.portfolio.rest.RestWebApplicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;


public class HandlerDatabase implements KEventHandler {
    private static final Logger logger = LoggerFactory.getLogger(HandlerDatabase.class);
    HttpServletRequest httpServletRequest;
    HttpSession session;
    int userId;
    int groupId;
    DataProvider dataProvider;
    Connection connection;

    public HandlerDatabase(HttpServletRequest request, DataProvider provider) {
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
    }

    @Override
    public boolean processEvent(KEvent event) {

        //  initialize(httpServletRequest,true);
        try {
            switch (event.eventType) {
                case LOGIN:
                    String[] resultCredential = dataProvider.postCredentialFromXml(connection, this.userId, event.inputData, "", null);
                    if (resultCredential != null) {
                        String login1 = resultCredential[0];
                        String tokenID = resultCredential[1];

                        if (tokenID == null) throw new RestWebApplicationException(Status.FORBIDDEN, "invalid credential or invalid group member");
                        else if (tokenID.length() == 0) throw new RestWebApplicationException(Status.FORBIDDEN, "invalid credential or invalid group member");

                        this.userId = Integer.parseInt(resultCredential[3]);
                        session.setAttribute("user", login1);
                        session.setAttribute("uid", this.userId);

                        event.status = 200;
                        event.doc = parseString(resultCredential[2]);

                        logger.debug("LOGIN event");
                    } else throw new RestWebApplicationException(Status.FORBIDDEN, "invalid credential or invalid group member");

                    break;

                case NODE:
                    if (event.requestType == KEvent.RequestType.POST) {
                        String returnValue = dataProvider.postNode(connection, new MimeType("text/xml"), event.uuid, event.inputData, this.userId, this.groupId, true).toString();
                        if (MysqlDataProvider.DATABASE_FALSE.equals(returnValue)) {
                            event.message = "Vous n'avez pas les droits d'acces";
                            event.status = 403;
                        } else {
                            event.status = 200;
                            event.doc = parseString(returnValue);
                        }

                        logger.debug("POST NODE event");
                    }
                    break;

                default:
                    break;
            }
			/*
			String xmlUsers = dataProvider.getUsersByGroup(this.userId);
			logRestRequest(httpServletRequest, "", xmlUsers, Status.OK.getStatusCode());
			return xmlUsers;
			//*/
        } catch (Exception ex) {
            logger.error("Intercepted error", ex);
			/*
			logRestRequest(httpServletRequest, "", ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
			//*/
        }

        return true;
    }

    Document parseString(String data) throws UnsupportedEncodingException, SAXException, IOException, ParserConfigurationException {
        DocumentBuilderFactory documentBuilderFactory = DomUtils.newSecureDocumentBuilderFactory();
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        Document doc = documentBuilder.parse(new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)));
        doc.setXmlStandalone(true);

        return doc;
    }

}