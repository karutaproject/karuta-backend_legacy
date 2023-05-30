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

package com.eportfolium.karuta.data.provider.test;

import java.sql.Connection;
import java.util.Properties;

import javax.activation.MimeType;

import com.eportfolium.karuta.data.provider.DataProvider;
import com.eportfolium.karuta.data.utils.DomUtils;
import com.eportfolium.karuta.data.utils.SqlUtils;
import com.eportfolium.karuta.security.Credential;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class MysqlDataProviderTest {

    private static final Logger logger = LoggerFactory.getLogger(MysqlDataProviderTest.class);

    /**
     * @param args
     */
    public static void main(String[] args) {
        // TODO Auto-generated method stub

        String dataProviderName = "com.eportfolium.karuta.data.provider.MysqlDataProvider";
        Properties connectionProperties = new Properties();
        connectionProperties.setProperty("url", "jdbc:mysql://localhost/portfolio");
        connectionProperties.setProperty("login", "root");
        connectionProperties.setProperty("password", "");


        try {
            MimeType xmlMimeType = new MimeType("text/xml");
            DataProvider dataProvider = (DataProvider) Class.forName(dataProviderName).getConstructor().newInstance();
            Connection connection = SqlUtils.getConnection();
            Credential cred = new Credential();

            String portfolioUuid = "aaaa-bbbb-cccc";
            Integer userId = 2;
            int groupId = 26;
            String rolename = "student";


            // putPortfolio
            String xml = DomUtils.file2String("c:\\temp\\iut2.xml", new StringBuffer());
            logger.debug("--- putPortfolio() ------");
            dataProvider.putPortfolio(connection, xmlMimeType, xmlMimeType, xml, portfolioUuid, userId, true, groupId, null);


            // getNode
            String uuid = "43020565-b650-4655-b466-af2c69b0c714";
            logger.debug("--- getNode(" + uuid + ") ------");
            logger.debug("Node {}", dataProvider.getNode(connection, xmlMimeType, uuid, false, userId, groupId, rolename, null, null));
            // getNode with children
            logger.debug("--- getNode(" + uuid + ") with children ------");
            logger.debug("Node {}", dataProvider.getNode(connection, xmlMimeType, uuid, true, userId, groupId, rolename, null, null));

            // putNode
            String parent_uuid_putnode = "82af4eae-0119-4055-b422-e37cece57e0f";
            logger.debug("--- putNode(" + parent_uuid_putnode + ") ------");
            String xml_putnode = DomUtils.file2String("c:\\temp\\putnode.xml", new StringBuffer());
            logger.debug("Node {}", dataProvider.putNode(connection, xmlMimeType, parent_uuid_putnode, xml_putnode, userId, groupId));


            String uuid_deletenode = "b6b20bf7-3732-4256-ae16-171f42030207";
            logger.debug("--- deleteNode(" + uuid_deletenode + ") ------");
            logger.debug("Result {}", dataProvider.deleteNode(connection, uuid_deletenode, userId, groupId, rolename));


            // getPortfolio
            logger.debug("--- getPortfolio() ------");
            String xml_out = dataProvider.getPortfolio(connection, xmlMimeType, portfolioUuid, userId, groupId, null, null, null, 0, null).toString();
            DomUtils.saveString(xml_out, "c:\\temp\\out2.xml");

            //getPortfolios
            userId = null;
            logger.debug("--- getPortfolios(" + userId + ") ------");
            logger.debug("Result {}", dataProvider.getPortfolios(connection, xmlMimeType, userId, groupId, rolename, true, 0, null, null, false, null));

            //getNodes
            String uuid_getnodes = "43020565-b650-4655-b466-af2c69b0c714";
            logger.debug("--- getNodes(" + uuid_getnodes + ") ------");
            logger.debug("Result {}", dataProvider.getNodes(connection, xmlMimeType, null,
                    userId, groupId, rolename, null, uuid_getnodes, null,
                    null, null, null)
            );


            //deletePortfolio
            //logger.debug("--- deletePortfolio("+portfolioUuid+") ------");
            //logger.debug(dataProvider.deletePortfolio(portfolioUuid));
            //getPortfolios
            userId = null;
            logger.debug("--- getPortfolios(" + userId + ") ------");
            logger.debug("Result {}", dataProvider.getPortfolios(connection, xmlMimeType, userId, groupId, rolename, true, 0, null, null, false, null));


        } catch (Exception ex) {
            ex.printStackTrace();
        }


    }

}