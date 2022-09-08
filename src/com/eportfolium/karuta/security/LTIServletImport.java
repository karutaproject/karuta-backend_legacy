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

package com.eportfolium.karuta.security;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.eportfolium.karuta.data.provider.DataProvider;
import com.eportfolium.karuta.data.utils.HttpClientUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.message.BasicHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class supporting lti integration
 * Used basiclti-util-2.1.0 (from sakai 2.9.2) for the oauth dependencies
 *
 * @author chmaurer
 */
public class LTIServletImport extends HttpServlet {

    private static final long serialVersionUID = -5793392467087229614L;

    private static final Logger logger = LoggerFactory.getLogger(LTIServletImport.class);

    ServletConfig sc;
    DataProvider dataProvider;

    String cookie = "wad";
    //boolean log = true;
    //boolean trace = true;

    @Override
    public void init() {
        ServletContext application = getServletConfig().getServletContext();
        try {
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String url = request.getParameter("url");
        HttpSession session = request.getSession(true);
        if (session != null) {
            final String sakai_session = (String) session.getAttribute("sakai_session");
            final String sakai_server = (String) session.getAttribute("sakai_server");
            logger.info("doGet IS IT OK: {}", sakai_session);
            // Base server http://localhost:9090
            final Header header = new BasicHeader("JSESSIONID", sakai_session);
            final Set<Header> headers = new HashSet<>();
            headers.add(header);

            HttpResponse get = HttpClientUtils.goGet(headers, sakai_server + "/" + url);
            if (get != null) {
                // Retrieve data
                try {
                    InputStream retrieve = get.getEntity().getContent();
                    ServletOutputStream output = response.getOutputStream();

                    IOUtils.copy(retrieve, output);
                    IOUtils.closeQuietly(output, null);

                    output.flush();
                    output.close();
                    retrieve.close();
                } catch (IOException e) {
                    logger.error("IO Exception", e);
                }
            }
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession(true);
        if (session != null) {
            final String sakai_session = (String) session.getAttribute("sakai_session");
            final String sakai_server = (String) session.getAttribute("ext_sakai_server");
            logger.info("doPost IS IT OK: {}", sakai_session);
            // Base server http://localhost:9090
            final Header header = new BasicHeader("JSESSIONID", sakai_session);
            final Set<Header> headers = new HashSet<>();
            headers.add(header);

            // TODO strange behaviour url is not set and doing get from POST
            HttpResponse get = HttpClientUtils.goGet(headers, "");
            if (get != null) {
                // Retrieve data
                try {
                    InputStream retrieve = get.getEntity().getContent();
                    ServletOutputStream output = response.getOutputStream();

                    IOUtils.copy(retrieve, output);
                    IOUtils.closeQuietly(output, null);

                    output.flush();
                    output.close();
                    retrieve.close();
                } catch (IOException e) {
                    logger.error("IO Exception", e);
                }
            }
        }
    }


}