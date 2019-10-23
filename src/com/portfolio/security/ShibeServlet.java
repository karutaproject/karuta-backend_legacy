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
import java.util.Enumeration;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.portfolio.data.provider.DataProvider;
import com.portfolio.data.utils.ConfigUtils;
import com.portfolio.data.utils.SqlUtils;

/**
 *
 */
public class ShibeServlet extends HttpServlet {

	private static final long serialVersionUID = -5793392467087229614L;

	final Logger logger = LoggerFactory.getLogger(ShibeServlet.class);

	ServletConfig sc;
	DataProvider dataProvider;

	@Override
  public void init()
	{
		System.setProperty("https.protocols", "TLSv1.2");
		sc = getServletConfig();
	  ServletContext application = getServletConfig().getServletContext();
	  try
	  {
	  	ConfigUtils.loadConfigFile(sc.getServletContext());
			dataProvider = SqlUtils.initProvider(application, null);
	  }
	  catch( Exception e ){ e.printStackTrace(); }
	}


	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
		
		HttpSession session = request.getSession(true);
		String rem = request.getRemoteUser();

		Connection connexion = null;
		int uid = 0;
		try
		{
			connexion = SqlUtils.getConnection(session.getServletContext());
			String userId = dataProvider.getUserId( connexion, rem, null );
			uid = Integer.parseInt(userId);
			
			if(uid == 0 )
			{
				System.out.println("[SHIBESERV] Creating account for "+rem);
				userId = dataProvider.createUser(connexion, rem, null);
				uid = Integer.parseInt(userId);
				
				/// Update values
				String mail = (String) request.getAttribute(ConfigUtils.get("shib_email"));
				String cn = (String) request.getAttribute(ConfigUtils.get("shib_name"));
				String[] namefrag = cn.split(" ");
				/// Regular function need old password to update
				/// But external account generate password unreachable with regular method
				dataProvider.putInfUserInternal(connexion, uid, uid, namefrag[1], namefrag[0], mail);
			}
			session.setAttribute("uid", uid);
			session.setAttribute("user", rem);
			session.setAttribute("fromshibe", 1);
		}
		catch( Exception e )
		{
			e.printStackTrace();
		}
		finally
		{
			if( connexion != null )
			{
				try
				{
					connexion.close();
				}
				catch( SQLException e ) { e.printStackTrace(); }
			}
		}

		if( uid > 0 )
		{
			String location = ConfigUtils.get("ui_redirect_location");
			System.out.println("Location: "+location);
			response.sendRedirect(location);
			response.getWriter().close();;
		}
		else
		{
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			PrintWriter writer = response.getWriter();
			writer.write("forbidden");
			writer.close();
		}
		request.getReader().close();
	}

}
