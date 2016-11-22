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
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import com.portfolio.data.provider.DataProvider;
import com.portfolio.data.utils.ConfigUtils;
import com.portfolio.data.utils.DomUtils;
import com.portfolio.data.utils.LogUtils;
import com.portfolio.data.utils.SqlUtils;

public class LoggingService  extends HttpServlet
{
	static final Logger logger = LoggerFactory.getLogger(LoggingService.class);
	private static final long serialVersionUID = -1464636556529383111L;
	/**
	 *
	 */

	DataProvider dataProvider = null;

	@Override
	public void init( ServletConfig config ) throws ServletException
	{
		super.init(config);
		try
		{
			LogUtils.initDirectory(getServletContext());
			
			dataProvider = SqlUtils.initProvider(getServletContext(), logger);
		}
		catch( Exception e )
		{
			e.printStackTrace();
			logger.error(e.getMessage());
		}
		
	}

	/*
	public void initialize(HttpServletRequest httpServletRequest)
	{
		Connection c;
		try
		{
//			dataProvider.disconnect();	// Ensure we are disconnected
//			c = SqlUtils.getConnection(getServletContext());
//			dataProvider.setConnection(c);
		}
		catch( Exception e )
		{
			e.printStackTrace();
			logger.error(e.getMessage());
		}
	}
	//*/

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
	{
//		initialize(request);
		
		Connection c = null;
		try
		{
			HttpSession session = request.getSession(true);
			Integer val = (Integer) session.getAttribute("uid");
			/// Basic check if user is logged on
			if( val == null )
			{
				response.setStatus(403);
				return;
			}

			/// Logfile name
			String loggingLine = request.getParameter("n");
			String filename  =  ConfigUtils.get("logfile_"+loggingLine);

			if( filename == null )	// Wanting an undefined logfile
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
			if( "true".equals(showuser) )
			{
				c = SqlUtils.getConnection(session.getServletContext());
				String userinfo = dataProvider.getInfUser(c, 1, val);
				Document doc = DomUtils.xmlString2Document(userinfo, null);
				NodeList usernameNodes = doc.getElementsByTagName("username");
				username = usernameNodes.item(0).getTextContent();
//				dataProvider.disconnect();
			}
			
			/// Formatting
			SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
			
			/// Complete path
			Date date = new Date();
			String datestring = dateFormat.format(date);
			InputStreamReader bis = new InputStreamReader(request.getInputStream(), "UTF-8");
			BufferedReader bread = new BufferedReader(bis);
			
			BufferedWriter bwrite = LogUtils.getLog(filename);
			String outputformat = "%s : %s - '%s' -- ";
			bwrite.write(String.format(outputformat, datestring, context, username));
			String s;
			while( (s=bread.readLine())!=null )
			{
				bwrite.write(s);
				bwrite.newLine();
			}
			bwrite.flush();
			bwrite.close();
		}
		catch( Exception e )
		{
			e.printStackTrace();
			logger.error(e.getMessage());
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
				request.getInputStream().close();
				response.getWriter().close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
			catch( IOException e )
			{
				e.printStackTrace();
				logger.error(e.getMessage());
			}
		}

	}
}

