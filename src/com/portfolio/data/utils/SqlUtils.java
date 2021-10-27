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

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import com.portfolio.data.provider.DataProvider;
import org.apache.commons.dbcp2.cpdsadapter.DriverAdapterCPDS;
import org.apache.commons.dbcp2.datasources.SharedPoolDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SqlUtils {
    public static final String PROP_DATA_PROVIDER_CLASS = "dataProviderClass";

    static final Logger logger = LoggerFactory.getLogger(SqlUtils.class);

    static boolean loaded = false;
    static InitialContext ctx = null;
    static DataSource ds = null;
    static DataProvider dp = null;

    public static String getCurrentTimeStamp() {
        java.util.Date date = new java.util.Date();
        return new Timestamp(date.getTime()).toString();
    }

    public static Timestamp getCurrentTimeStamp2() {
        return new Timestamp(System.currentTimeMillis());
    }

    public static DataProvider initProvider() throws Exception {
        //============= init servers ===============================
        final String dataProviderName = ConfigUtils.getInstance().getRequiredProperty(PROP_DATA_PROVIDER_CLASS);
        if (dp == null)
            dp = (DataProvider) Class.forName(dataProviderName).newInstance();

//		Connection connection = getConnection(application);
//		dataProvider.setConnection(connection);

        return dp;
    }

    // If servContext is null, only load from pooled connection
    public static Connection getConnection() throws Exception {
        if (!loaded) {
            final String resourceDatasourceName = ConfigUtils.getInstance().getProperty("JDBC.external.resourceName");
            if (resourceDatasourceName != null) {
                ctx = new InitialContext();
                Context envCtx = (Context) ctx.lookup("java:comp/env");
                ds = (DataSource) envCtx.lookup(resourceDatasourceName);
                logger.info("Using external datasource with name {}: {}", resourceDatasourceName, ds.toString());

            } else {
                DriverAdapterCPDS cpds = new DriverAdapterCPDS();
                cpds.setDriver(ConfigUtils.getInstance().getRequiredProperty("DBDriver"));
                cpds.setUrl(ConfigUtils.getInstance().getRequiredProperty("DBUrl"));

                Properties info = new Properties();
                info.put("user", ConfigUtils.getInstance().getRequiredProperty("DBUser"));
                info.put("password", ConfigUtils.getInstance().getRequiredProperty("DBPass"));
                cpds.setConnectionProperties(info);

                SharedPoolDataSource tds = new SharedPoolDataSource();
                tds.setConnectionPoolDataSource(cpds);

                /// TODO: Complete it with other parameters, also, benchmark
                /// Configuring other stuff
                tds.setValidationQuery("SELECT 1 FROM DUAL");
                tds.setDefaultTestOnBorrow(true);
                tds.setDefaultTestWhileIdle(true);
                tds.setDefaultTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

                tds.setDefaultMaxWaitMillis(Integer.parseInt(ConfigUtils.getInstance().getProperty("DB.MaxWait", "10000")));
                tds.setMaxTotal(Integer.parseInt(ConfigUtils.getInstance().getProperty("DB.MaxTotal", "1000")));
                tds.setDefaultMinIdle(Integer.parseInt(ConfigUtils.getInstance().getProperty("DB.MinIdle", "5")));
                tds.setDefaultMaxIdle(Integer.parseInt(ConfigUtils.getInstance().getProperty("DB.MaxIdle", "1000")));
                tds.setDefaultTimeBetweenEvictionRunsMillis(Integer.parseInt(ConfigUtils.getInstance().getProperty("DB.WaitEviction", "60000")));
                tds.setDefaultNumTestsPerEvictionRun(Integer.parseInt(ConfigUtils.getInstance().getProperty("DB.NumTestEviction", "5")));
                ds = tds;
                logger.info("Using internal datasource {} !", tds);
            }


            loaded = true;
        }
        return ds.getConnection();
    }

    public static void close() {
        try {
            if (ctx != null) {
                ctx.close();
                ctx = null;
            }
        } catch (NamingException e) {
            logger.error("Intercept error", e);
        }
    }
}