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
import java.io.ByteArrayInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Enumeration;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import com.portfolio.data.provider.DataProvider;
import com.portfolio.data.utils.ConfigUtils;
import com.portfolio.data.utils.DomUtils;
import com.portfolio.data.utils.LogUtils;
import com.portfolio.data.utils.SqlUtils;
import com.portfolio.security.Credential;

public class ReportService  extends HttpServlet
{
	static final Logger logger = LoggerFactory.getLogger(ReportService.class);
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

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
	{
		/// Check if user is logged in
		HttpSession session = request.getSession(false);
		if( session == null )
		{
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		int uid = (Integer) session.getAttribute("uid");
		if( uid == 0 )
		{
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			return;
		}
		
		final Credential credential = new Credential();
		/// Check if user is admin
		Connection c;
		try
		{
			c = SqlUtils.getConnection(getServletContext());
			if( credential.isAdmin(c, uid) )
			{
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

				FileReader fr = new FileReader(filename);
				BufferedReader bread = new BufferedReader(fr);
				OutputStreamWriter osw = new OutputStreamWriter(response.getOutputStream());
				BufferedWriter bwrite = new BufferedWriter(osw);
				
				char[] buffer = new char[1024];
				int offset = 0;
				int read=1;
				
				while( read > 0 )
				{
					read = bread.read(buffer, offset, 1024);
					offset += read;
					bwrite.write(buffer);
				}
				
				/// Cleanup
				bread.close();
			}
		}
		catch( Exception e )
		{
			e.printStackTrace();
		}
		
		/// Close connections
		try
		{
			request.getReader().close();
			response.getWriter().close();
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}

	}
		
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
	{
		/// Check if user is logged in
		HttpSession session = request.getSession(false);
		if( session == null )
		{
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		int uid = (Integer) session.getAttribute("uid");
		if( uid == 0 )
		{
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			return;
		}
		
		Connection c = null;
		try
		{
			/// Find user's username
			c = SqlUtils.getConnection(session.getServletContext());
			String userinfo = dataProvider.getInfUser(c, 1, uid);
			Document doc = DomUtils.xmlString2Document(userinfo, null);
			NodeList usernameNodes = doc.getElementsByTagName("username");
			String username = usernameNodes.item(0).getTextContent();

			if( null == username)
			{
				response.setStatus(400);
				PrintWriter writer = response.getWriter();
				writer.append("Username error");
				writer.close();
				return;
			}
			
			/// Prepare to transfer username to report daemon
			StringWriter writer = new StringWriter();
			IOUtils.copy(request.getInputStream(), writer);
			String data = writer.toString();

			data += "&user="+username;
			
			String urlTarget = "127.0.0.1:8081/";
			
			/// Create connection
			URL urlConn = new URL(urlTarget);
			HttpURLConnection connection = (HttpURLConnection) urlConn.openConnection();
			connection.setDoOutput(true);
			connection.setUseCaches(false);
			connection.setInstanceFollowRedirects(false);
			String method = request.getMethod();
			connection.setRequestMethod(method);

			String context = request.getContextPath();
			connection.setRequestProperty("app", context);

			/// Transfer headers
			String key = "";
			String value = "";
			Enumeration<String> header = request.getHeaderNames();
			while( header.hasMoreElements() )
			{
				key = header.nextElement();
				value = request.getHeader(key);
				connection.setRequestProperty(key, value);
			}

			connection.connect();

			/// Send data to report daemon
			ByteArrayInputStream bais = new ByteArrayInputStream(data.getBytes());
			OutputStream outputData = connection.getOutputStream();
			IOUtils.copy(bais, outputData);

			/// Those 2 lines are needed, otherwise, no request sent
			int code = connection.getResponseCode();
			String msg = connection.getResponseMessage();

			if( code != HttpURLConnection.HTTP_OK )
				logger.error("Couldn't send file: "+msg);
			
			/// Retrieving info
			InputStream objReturn = connection.getInputStream();

			/// Write back daemon response
			ServletOutputStream os = response.getOutputStream();
			IOUtils.copy(objReturn, os);
			
			// Close connection to report daemon
			connection.disconnect();
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

