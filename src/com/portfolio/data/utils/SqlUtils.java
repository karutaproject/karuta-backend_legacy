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
import java.sql.Timestamp;
import java.util.Properties;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletContext;
import javax.sql.DataSource;

import org.apache.commons.dbcp2.cpdsadapter.DriverAdapterCPDS;
import org.apache.commons.dbcp2.datasources.SharedPoolDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.portfolio.data.provider.ReportHelperProvider;
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
	static ReportHelperProvider rh = null;

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
		if( dp == null )
			dp = (DataProvider)Class.forName(dataProviderName).newInstance();

//		Connection connection = getConnection(application);
//		dataProvider.setConnection(connection);
		
		return dp;
	}

	public static ReportHelperProvider initProviderHelper(ServletContext application, Logger logger) throws Exception
	{
	//============= init servers ===============================
		String dataProviderName = "com.portfolio.data.provider.ReportHelperProvider";
		if( rh == null )
			rh = (ReportHelperProvider)Class.forName(dataProviderName).getConstructor().newInstance();

//		Connection connection = getConnection(application);
//		dataProvider.setConnection(connection);
		
		return rh;
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
				
				Properties info = new Properties();
				info.put("user", dbuser);
				info.put("password", dbpass);
				cpds.setConnectionProperties(info);
				
				SharedPoolDataSource tds = new SharedPoolDataSource();
				tds.setConnectionPoolDataSource(cpds);
				
				/// TODO: Complete it with other parameters, also, benchmark
				/// Configuring other stuff
				tds.setValidationQuery("SELECT 1 FROM DUAL");
				tds.setDefaultTestOnBorrow(true);
				tds.setDefaultTestWhileIdle(true);
				tds.setDefaultTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
				
				String maxwait = ConfigUtils.get("DB.MaxWait");
				String maxtotal = ConfigUtils.get("DB.MaxTotal");
				String minidle = ConfigUtils.get("DB.MinIdle");
				String maxidle = ConfigUtils.get("DB.MaxIdle");
				String waiteviction = ConfigUtils.get("DB.WaitEviction");
				String numtesteviction =  ConfigUtils.get("DB.NumTestEviction");
				/// In case something hasn't been set
				if( maxwait == null ) maxwait = "1000";
				if( maxtotal == null ) maxtotal = "1000";
				if( minidle == null ) minidle = "1";
				if( maxidle == null ) maxidle = "1000";
				if( waiteviction == null ) waiteviction = "60000";
				if( numtesteviction == null ) numtesteviction = "10";
				
				tds.setDefaultMaxWaitMillis(Integer.decode(maxwait));
				tds.setMaxTotal(Integer.decode(maxtotal));
				tds.setDefaultMaxIdle(Integer.decode(maxidle));
				tds.setDefaultTimeBetweenEvictionRunsMillis(Integer.decode(waiteviction));
				tds.setDefaultNumTestsPerEvictionRun(Integer.decode(numtesteviction));
				
				ds = tds;
			}
			catch( Exception e )
			{
				e.printStackTrace();
			}
			loaded = true;
		}
		return ds.getConnection();
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
