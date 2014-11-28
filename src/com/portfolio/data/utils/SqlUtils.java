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

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Properties;

import javax.servlet.ServletContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class SqlUtils {


	public static  String getCurrentTimeStamp()
	{

		 java.util.Date date= new java.util.Date();
		 return new Timestamp(date.getTime()).toString();
	}
	public static  Timestamp getCurrentTimeStamp2()
	{

		 return new Timestamp(System.currentTimeMillis());
	}

	public static Connection getConnection( ServletContext servContext ) throws ParserConfigurationException, SAXException, IOException, SQLException, ClassNotFoundException
	{
		// Open META-INF/context.xml
		DocumentBuilderFactory documentBuilderFactory =DocumentBuilderFactory.newInstance();
		DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
		Document doc = documentBuilder.parse(servContext.getRealPath("/")+"/META-INF/context.xml");
		NodeList res = doc.getElementsByTagName("Resource");
		Node dbres = res.item(0);

		Properties info = new Properties();
		NamedNodeMap attr = dbres.getAttributes();
		String url = "";
		for( int i=0; i<attr.getLength(); ++i )
		{
			Node att = attr.item(i);
			String name = att.getNodeName();
			String val = att.getNodeValue();
			if( "url".equals(name) )
				url = val;
			else if( "username".equals(name) )	// username (context.xml) -> user (properties)
				info.put("user", val);
			else if( "driverClassName".equals(name) )
				Class.forName(val);
			else
				info.put(name, val);
		}

		return DriverManager.getConnection(url, info);
	}

}
