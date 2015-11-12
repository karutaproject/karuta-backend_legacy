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

package com.portfolio.data.utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;

import javax.servlet.ServletContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogUtils
{
	static final Logger logger = LoggerFactory.getLogger(LogUtils.class);
	static boolean hasLoaded = false;
	static HashMap<String, String> attributes = new HashMap<String, String>();
	static String filePath = "";
	
	/// The folder we use is {TOMCAT_ROOT}/{SERVLET-NAME}_logs
	public static boolean initDirectory( ServletContext context ) throws Exception
	{
		if( hasLoaded ) return true;
		try
		{
			/// Preparing logfile for direct access
			String servName = context.getContextPath();
			String path = context.getRealPath("/");
			File base = new File(path+".."+File.separatorChar+"..");
			String tomcatRoot = base.getCanonicalPath();
			path = tomcatRoot + servName +"_logs"+File.separatorChar;
			
			/// Check if folder exists
			File logFolder = new File(path);
			if( !logFolder.exists() )
				logFolder.mkdirs();

			filePath = path;
			hasLoaded = true;
		}
		catch(Exception e)
		{
			logger.error("Can't create folder: "+filePath+" ("+e.getMessage()+")");
		}
		finally
		{
		}
		return hasLoaded;
	}

	public static BufferedWriter getLog( String filename ) throws FileNotFoundException, UnsupportedEncodingException
	{
		FileOutputStream fos = new FileOutputStream(filePath+filename, true);
		OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF-8");
		BufferedWriter bwrite = new BufferedWriter(osw);
		
		return bwrite;
	}
}
