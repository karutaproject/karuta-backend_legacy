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
import javax.naming.NamingException;
import javax.servlet.ServletContext;
import javax.sql.DataSource;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.dbcp2.cpdsadapter.DriverAdapterCPDS;
import org.apache.commons.dbcp2.datasources.SharedPoolDataSource;
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
	static DataProvider dp = null;

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
		if( dp == null )
			dp = (DataProvider)Class.forName(dataProviderName).newInstance();

//		Connection connection = getConnection(application);
//		dataProvider.setConnection(connection);
		
		return dp;
	}

	// If servContext is null, only load from pooled connection
	public static Connection getConnection( ServletContext servContext ) throws Exception
	{
		if( !loaded )
		{
			String dbdriver = ConfigUtils.get("DBDriver");
			ConfigUtils.clear("DBDriver");
			String dbuser = ConfigUtils.get("DBUser");
			ConfigUtils.clear("DBUser");
			String dbpass = ConfigUtils.get("DBPass");
			ConfigUtils.clear("DBPass");
			String dburl = ConfigUtils.get("DBUrl");
			ConfigUtils.clear("DBUrl");

			try
			{
				DriverAdapterCPDS cpds = new DriverAdapterCPDS();
				cpds.setDriver(dbdriver);
				cpds.setUrl(dburl);
//				cpds.setUser(dbuser);
//				cpds.setPassword(dbpass);
				
				Properties info = new Properties();
				info.put("user", dbuser);
				info.put("password", dbpass);
				cpds.setConnectionProperties(info);
				
				SharedPoolDataSource tds = new SharedPoolDataSource();
				tds.setConnectionPoolDataSource(cpds);
				
				/// TODO: Complete it with other parameters, also, benchmark
				/// Configuring other stuff
				tds.setValidationQuery("SELECT 1");
				tds.setDefaultTestOnBorrow(true);
				tds.setDefaultTestWhileIdle(true);
				
				String maxwait = ConfigUtils.get("DB.MaxWait");
				String maxtotal = ConfigUtils.get("DB.MaxTotal");
				String minidle = ConfigUtils.get("DB.MinIdle");
				String maxidle = ConfigUtils.get("DB.MaxIdle");
				String waiteviction = ConfigUtils.get("DB.WaitEviction");
				/// In case something hasn't been set
				if( maxwait == null ) maxwait = "1000";
				if( maxtotal == null ) maxtotal = "1000";
				if( minidle == null ) minidle = "1";
				if( maxidle == null ) maxidle = "1000";
				if( waiteviction == null ) waiteviction = "60000";
				
				tds.setDefaultMaxWaitMillis(Integer.decode(maxwait));
				tds.setDefaultMaxTotal(Integer.decode(maxtotal));
				tds.setDefaultMinIdle(Integer.decode(minidle));
				tds.setDefaultMaxIdle(Integer.decode(maxidle));
				tds.setDefaultTimeBetweenEvictionRunsMillis(Integer.decode(waiteviction));
				
				ds = tds;
			}
			catch( Exception e )
			{
				e.printStackTrace();
			}

			// Try to initialize Datasource
			/*
			cxt = new InitialContext();
			if ( cxt == null ) {
				throw new Exception("no context found!");
			}
			//*/
	
			/// Init this here, might fail depending on server hosting
			try
			{
//				ds = (DataSource) cxt.lookup( "java:/comp/env/jdbc/portfolio-backend" );
			}
			catch( Exception e )
			{
				logger.info("Might not be possible to load context.xml: "+e.getMessage());
			}
			loaded = true;
		}

//		if( ds != null )	// Return the connection directly
			return ds.getConnection();

		/// Deprecated with hosting
		/*
		//// Case where we can't deploy context.xml, load it raw via the DriverManager
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
		//*/
	}

	public static void close()
	{
		try
		{
			if( cxt != null )
			{
				cxt.close();
				cxt = null;
			}
		}
		catch( NamingException e )
		{
			e.printStackTrace();
		}
	}
}
