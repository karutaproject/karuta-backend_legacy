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

import java.io.File;
import java.util.HashMap;

import javax.servlet.ServletConfig;

public class ConfigUtils
{
	static boolean hasLoaded = false;
	static HashMap<String, String> attributes = new HashMap<String, String>();
	public static boolean loadConfigFile( ServletConfig config ) throws Exception
	{
		if( hasLoaded ) return true;
		try
		{
			String servName = config.getServletContext().getContextPath();
			String path = config.getServletContext().getRealPath("/");
			File base = new File(path+"../..");
			String tomcatRoot = base.getCanonicalPath();
			path = tomcatRoot + servName +"_config"+File.separatorChar;

			attributes = new HashMap<String, String>();
			java.io.FileInputStream fichierSrce =  new java.io.FileInputStream(path+"configKaruta.properties");
			java.io.BufferedReader readerSrce = new java.io.BufferedReader(new java.io.InputStreamReader(fichierSrce,"UTF-8"));
			String line = null;
			String variable = null;
			String value = null;
			while ((line = readerSrce.readLine())!=null){
				if (!line.startsWith("#") && line.length()>2) { // ce n'est pas un commentaire et longueur>=3 ex: x=b est le minumum
					String[] tok = line.split("=");
					variable = tok[0];
					value = tok[1];
					attributes.put(variable, value);
				}
			}
			fichierSrce.close();
			hasLoaded = true;
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
}
