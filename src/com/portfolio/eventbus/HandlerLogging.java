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
import java.util.Enumeration;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import com.portfolio.data.provider.DataProvider;
import com.portfolio.data.utils.SqlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;


public class HandlerLogging implements KEventHandler {

	private static final Logger logger = LoggerFactory.getLogger(HandlerLogging.class);
    HttpServletRequest httpServletRequest;
    HttpSession session;
    int userId;
    int groupId;
    DataProvider dataProvider;
    Connection connection;

    public HandlerLogging(HttpServletRequest request, DataProvider provider) {
        httpServletRequest = request;
        dataProvider = provider;
        try {
            connection = SqlUtils.getConnection(request.getSession().getServletContext());
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
        try {
            if (event.eventType == KEvent.EventType.LOGIN) {
                StringBuilder httpHeaders = new StringBuilder();
                Enumeration<String> headerNames = httpServletRequest.getHeaderNames();
                while (headerNames.hasMoreElements()) {
                    String headerName = headerNames.nextElement();
                    httpHeaders.append(headerName).append(": ").append(httpServletRequest.getHeader(headerName)).append("\n");
                }
                String url = httpServletRequest.getRequestURL().toString();
                if (httpServletRequest.getQueryString() != null) url += "?" + httpServletRequest.getQueryString();
                dataProvider.writeLog(connection, url, httpServletRequest.getMethod(),
                        httpHeaders.toString(), event.inputData, event.doc.toString(), event.status);

                //// TODO Devrait aussi écrire une partie dans les fichiers
                logger.debug("LOGIN EVENT");
            }
        } catch (Exception ex) {
			logger.error("Intercepted error", ex);
			/*
			ex.printStackTrace();
			logRestRequest(httpServletRequest, "", ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
			//*/
        }


        return true;
    }

    Document parseString(String data) throws UnsupportedEncodingException, SAXException, IOException, ParserConfigurationException {
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        Document doc = documentBuilder.parse(new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)));
        doc.setXmlStandalone(true);

        return doc;
    }

}