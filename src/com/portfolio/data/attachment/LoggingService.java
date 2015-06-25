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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.portfolio.data.provider.DataProvider;
import com.portfolio.data.utils.ConfigUtils;
import com.portfolio.security.Credential;

public class LoggingService  extends HttpServlet
{
	static final Logger logger = LoggerFactory.getLogger(LoggingService.class);
	private static final long serialVersionUID = -1464636556529383111L;
	/**
	 *
	 */

	DataProvider dataProvider;
	boolean hasNodeReadRight = false;
	boolean hasNodeWriteRight = false;
	Credential credential;
	int userId;
	int groupId = -1;
	String user = "";
	String context = "";
	HttpSession session;

	public void initialize(HttpServletRequest httpServletRequest)
	{
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
	{
		initialize(request);
		
		HttpSession session = request.getSession(true);
		String path = "";
		
		try
		{
			/// Log folder
			String servName = session.getServletContext().getContextPath();
			path = session.getServletContext().getRealPath("/");
			File base = new File(path+"../..");
			String tomcatRoot = base.getCanonicalPath();
			path = tomcatRoot + servName +"_logs"+File.separatorChar;
		}
		catch( IOException e1 )
		{
			e1.printStackTrace();
		}

		/// Logfile name
		String loggingLine = request.getParameter("line");
		String filename  =  ConfigUtils.get("logfile_"+loggingLine);

		int buffSize = 1024;
		int offset = 0;
		int read = 0;
		try
		{
			/// Formatting
			SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
			
			/// Complete path
			FileOutputStream fos = new FileOutputStream(path+filename);
			OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF-8");
			Date date = new Date();
			osw.append(dateFormat.format(date)+": ");
			BufferedInputStream bis = new BufferedInputStream(request.getInputStream());
			byte[] data = new byte[buffSize];
			do
			{
				read = bis.read(data, offset, buffSize);
				fos.write(data);
				offset += read;
			} while( read != -1 );
			osw.append("\r\n");
			osw.close();
		}
		catch( IOException e1 )
		{
			e1.printStackTrace();
		}
		
		
		
		try
		{
		}
		catch( Exception e )
		{
			logger.error(e.getMessage());
			e.printStackTrace();
		}
		response.setCharacterEncoding("UTF-8");

	}
}

