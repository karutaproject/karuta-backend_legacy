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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigUtils
{
	static final Logger logger = LoggerFactory.getLogger(ConfigUtils.class);
	static boolean hasLoaded = false;
	static HashMap<String, String> attributes = new HashMap<String, String>();
	static String filePath = "";
	
	public static boolean loadConfigFile( ServletContext context ) throws Exception
	{
		if( hasLoaded ) return true;
		String path = "";
		try
		{
			String servName = context.getContextPath();
			path = context.getRealPath("/");
			File base = new File(path+".."+File.separatorChar+"..");
			String tomcatRoot = base.getCanonicalPath();
			path = tomcatRoot + servName +"_config"+File.separatorChar;

			attributes = new HashMap<String, String>();
			filePath = path+"configKaruta.properties";
			FileInputStream fichierSrce =  new FileInputStream(filePath);
			BufferedReader readerSrce = new BufferedReader(new java.io.InputStreamReader(fichierSrce,"UTF-8"));
			String line = null;
			String variable = null;
			String value = null;
			while ((line = readerSrce.readLine())!=null){
				if (!line.startsWith("#") && line.length()>2) { // ce n'est pas un commentaire et longueur>=3 ex: x=b est le minumum
					int cut = line.indexOf("=");
					variable = line.substring(0, cut);
					value = line.substring(cut+1);
					attributes.put(variable, value);
				}
			}
			fichierSrce.close();
			hasLoaded = true;
			logger.info("Configuration file loaded: "+filePath);
			
			/// While we're at it, init logger
			LogUtils.initDirectory(context);
		}
		catch(Exception e)
		{
//			e.printStackTrace();
			logger.error("Can't load file :"+filePath+" ("+e.getMessage()+")");
		}
		finally
		{
		}
		return hasLoaded;
	}

	public static String get( String attribute )
	{
		return attributes.get(attribute);
	}
	
	public static void clear( String attribute )
	{
		attributes.remove(attribute);
	}
}
