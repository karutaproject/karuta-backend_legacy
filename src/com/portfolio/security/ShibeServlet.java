/* =======================================================
	Copyright 2018 - ePortfolium - Licensed under the
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

package com.portfolio.security;

import java.io.IOException;
import java.io.PrintWriter;
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
import com.portfolio.data.utils.SqlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class ShibeServlet extends HttpServlet {

    private static final long serialVersionUID = -5793392467087229614L;

    private final static Logger logger = LoggerFactory.getLogger(ShibeServlet.class);

    DataProvider dataProvider;

    @Override
    public void init() throws ServletException {
        System.setProperty("https.protocols", "TLSv1.2");
        try {
            ConfigUtils.init(getServletContext());
            dataProvider = SqlUtils.initProvider();
        } catch (Exception e) {
            logger.error("Can't init servlet", e);
			throw new ServletException(e);
        }
    }


    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

        HttpSession session = request.getSession(true);
        String rem = request.getRemoteUser();

        Connection connexion = null;
        int uid = 0;
        try {
            connexion = SqlUtils.getConnection(session.getServletContext());
            String userId = dataProvider.getUserId(connexion, rem, null);
            uid = Integer.parseInt(userId);

            if (uid == 0) {
                logger.info("[SHIBESERV] Creating account for {}", rem);
                userId = dataProvider.createUser(connexion, rem, null);
                uid = Integer.parseInt(userId);

                /// Update values
                String fn = ConfigUtils.getInstance().getProperty("shib_firstname");
                String ln = ConfigUtils.getInstance().getProperty("shib_lastname");
                String mail = (String) request.getAttribute(ConfigUtils.getInstance().getProperty("shib_email"));
                if ((fn != null && !"".equals(fn)) && (ln != null && !"".equals(ln))) {
                    fn = (String) request.getAttribute(fn);
                    ln = (String) request.getAttribute(ln);

                    /// Regular function need old password to update
                    /// But external account generate password unreachable with regular method
                    dataProvider.putInfUserInternal(connexion, uid, uid, fn, ln, mail);
                } else {
                    String cn = (String) request.getAttribute(ConfigUtils.getInstance().getProperty("shib_fullname"));
                    String[] namefrag = cn.split(" ");
                    if (namefrag.length > 1) {
                        dataProvider.putInfUserInternal(connexion, uid, uid, namefrag[1], namefrag[0], mail);
                    } else {
                        dataProvider.putInfUserInternal(connexion, uid, uid, namefrag[0], namefrag[0], mail);
                    }
                }
            }
            session.setAttribute("uid", uid);
            session.setAttribute("user", rem);
            session.setAttribute("fromshibe", 1);
        } catch (Exception e) {
            logger.error("Intercepted error", e);
			//TODO something missing
        } finally {
            if (connexion != null) {
                try {
                    connexion.close();
                } catch (SQLException e) {
                    logger.error("SQL request error", e);
                }
            }
        }

        if (uid > 0) {
            String location = ConfigUtils.getInstance().getRequiredProperty("ui_redirect_location");
            logger.info("Location: " + location);
            response.sendRedirect(location);
            response.getWriter().close();
            ;
        } else {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            PrintWriter writer = response.getWriter();
            writer.write("forbidden");
            writer.close();
        }
        request.getReader().close();
    }

}