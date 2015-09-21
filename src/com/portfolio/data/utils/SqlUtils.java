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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.Properties;

import javax.naming.InitialContext;
import javax.servlet.ServletContext;
import javax.sql.DataSource;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.portfolio.data.provider.DataProvider;

public class SqlUtils
{
	static final Logger logger = LoggerFactory.getLogger(SqlUtils.class);
	static final String dataProviderName = ConfigUtils.get("dataProviderClass");
	static final String serverType = ConfigUtils.get("serverType");
	static boolean loaded = false;
	static InitialContext cxt = null;
	static DataSource ds = null;

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
//		String dataProviderName = ConfigUtils.get("dataProviderClass");
		DataProvider dataProvider = (DataProvider)Class.forName(dataProviderName).newInstance();

//		Connection connection = getConnection(application);
//		dataProvider.setConnection(connection);
		
		return dataProvider;
	}

	// If servContext is null, only load from pooled connection
	public static Connection getConnection( ServletContext servContext ) throws Exception
	{
		if( !loaded )
		{
			// Try to initialize Datasource
			cxt = new InitialContext();
			if ( cxt == null ) {
				throw new Exception("no context found!");
			}
	
			/// Init this here, might fail depending on server hosting
			try
			{
				ds = (DataSource) cxt.lookup( "java:/comp/env/jdbc/portfolio-backend" );
			}
			catch( Exception e )
			{
				logger.info("Might not be possible to load context.xml: "+e.getMessage());
			}
			loaded = true;
		}
		
		if( ds != null )	// Return the connection directly
			return ds.getConnection();
		
		//// Case where we can't deploy context.xml
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

		Connection connection = DriverManager.getConnection(url, info);
		if( "mysql".equals(serverType) )
		{	// Because we don't always have access to base configuration
			PreparedStatement st = connection.prepareStatement("SET SESSION group_concat_max_len = 1048576");	// 1MB
			st.execute();
			st.close();
		}

		return connection;
	}

}
