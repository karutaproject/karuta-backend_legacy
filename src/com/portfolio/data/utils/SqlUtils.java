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

import javax.naming.InitialContext;
import javax.servlet.ServletContext;
import javax.sql.DataSource;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.portfolio.data.provider.DataProvider;

public class SqlUtils
{
	static final Logger logger = LoggerFactory.getLogger(SqlUtils.class);
	public static  String getCurrentTimeStamp()
	{
		java.util.Date date= new java.util.Date();
		return new Timestamp(date.getTime()).toString();
	}
	public static  Timestamp getCurrentTimeStamp2()
	{
		 return new Timestamp(System.currentTimeMillis());
	}

	public static DataProvider initProvider(ServletContext application, Logger logger) throws Exception
	{
	//============= init servers ===============================
		String dataProviderName = ConfigUtils.get("dataProviderClass");
		DataProvider dataProvider = (DataProvider)Class.forName(dataProviderName).newInstance();

		// Try to initialize Datasource
		InitialContext cxt = new InitialContext();
		if ( cxt == null ) {
			throw new Exception("no context found!");
		}

		/// Init this here, might fail depending on server hosting
		DataSource ds = (DataSource) cxt.lookup( "java:/comp/env/jdbc/portfolio-backend" );
		if ( ds == null ) {
			throw new Exception("Data  jdbc/portfolio-backend source not found!");
		}

		if( ds == null )	// Case where we can't deploy context.xml
		{
			Connection con = SqlUtils.getConnection(application);
			dataProvider.setConnection(con);
		}
		else
			dataProvider.setConnection(ds.getConnection());

		return dataProvider;
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
