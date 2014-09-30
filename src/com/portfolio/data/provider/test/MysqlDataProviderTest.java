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

package com.portfolio.data.provider.test;

import java.util.Properties;

import javax.activation.MimeType;

import com.portfolio.data.provider.DataProvider;
import com.portfolio.data.utils.DomUtils;



public class MysqlDataProviderTest {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

		String dataProviderName  = "com.portfolio.data.provider.MysqlDataProvider";
        Properties connectionProperties = new Properties();
        connectionProperties.setProperty("url", "jdbc:mysql://localhost/portfolio");
        connectionProperties.setProperty("login", "root");
        connectionProperties.setProperty("password", "");




		try
		{
			MimeType xmlMimeType = new MimeType("text/xml");
			DataProvider dataProvider = (DataProvider)Class.forName(dataProviderName).newInstance();
//			dataProvider.connect(connectionProperties);
			//System.out.println(dataProvider.getNode(new MimeType("text/xml"),"1"));
			//System.out.println(dataProvider.getNode(new MimeType("text/xml"),"2"));
			//System.out.println(dataProvider.getNode(new MimeType("text/xml"),"3"));
			//System.out.println("----------------------");
			//System.out.println(dataProvider.getNodeWithChildren(new MimeType("text/xml"),"1"));



			/*
			String xml = "";
			xml += "<eportfolio xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" schemaVersion=\"1.0\">";
			xml += "<asmRoot id=\"uuid-1\"  semantictag=\"\" xsi_type=\"Root\" modified=\"date-1\">";
			xml += "<label>LABEL</label>";
			xml += "<code>CODE</code>";
			xml += "<descr>DESCR</descr>";
			xml += "<asmStructure id=\"uuid-2\" semantictag=\"referentiels\" xsi_type=\"ReferentielStruct\" modified=\"date-2\">";
			xml += "<asmUnit  id=\"uuid-3\" semantictag=\"\" xsi_type=\"ReferentielUnit\" modified=\"date-3\">";
			xml += "<asmUnitStructure id=\"uuid-4\" semantictag=\"\" xsi_type=\"NewUnitStructure\" modified=\"date-4\">";
			xml += "<asmContext id=\"uuid-5\" semantictag=\"\" xsi_type=\"ReferenceContext\" modified='date-5'>";
			xml += "<asmResource id=\"uuid-6\" modified=\"date-6\"  xsi_type=\"Referentiel\"  format=\"xml\"/>";
			xml += "</asmContext>";
			xml += "</asmUnitStructure>";
			xml += "</asmUnit>";
			xml += "<asmUnit  id=\"uuid-7\" xsi_type=\"Proxy\" modified=\"date-7\">";
			xml += "<asmUnitStructure id=\"uuid-8\" semantictag=\"\" xsi_type=\"NewUnitStructure\" modified=\"date-8\"/>";
			xml += "</asmUnit>";
			xml += "</asmStructure>";
			xml += "</asmRoot>";
			xml += "</eportfolio>";
				*/

			String portfolioUuid = "aaaa-bbbb-cccc";
			Integer userId = 2;
			Integer groupId = 26;


			// putPortfolio
			String xml = DomUtils.file2String("c:\\temp\\iut2.xml", new StringBuffer());
			System.out.println("--- putPortfolio() ------");
			dataProvider.putPortfolio(xmlMimeType,xmlMimeType,xml,portfolioUuid,userId,true, groupId,null);


			  // getNode
			String uuid = "43020565-b650-4655-b466-af2c69b0c714";
			System.out.println("--- getNode("+uuid+") ------");
			System.out.println(dataProvider.getNode(xmlMimeType, uuid, false,userId, groupId, null));
			// getNode with children
			System.out.println("--- getNode("+uuid+") with children ------");
			System.out.println(dataProvider.getNode(xmlMimeType, uuid, true,userId, groupId, null));

			// putNode
			String parent_uuid_putnode = "82af4eae-0119-4055-b422-e37cece57e0f";
			System.out.println("--- putNode("+parent_uuid_putnode+") ------");
			String xml_putnode = DomUtils.file2String("c:\\temp\\putnode.xml", new StringBuffer());
			System.out.println(dataProvider.putNode(xmlMimeType, parent_uuid_putnode, xml_putnode,userId, groupId));



			String uuid_deletenode = "b6b20bf7-3732-4256-ae16-171f42030207";
			System.out.println("--- deleteNode("+uuid_deletenode+") ------");
			System.out.println(dataProvider.deleteNode(uuid_deletenode,userId, groupId));


			// getPortfolio
			System.out.println("--- getPortfolio() ------");
			String xml_out = dataProvider.getPortfolio(xmlMimeType,portfolioUuid,userId, groupId, null, null, null, 0).toString();
			DomUtils.saveString(xml_out, "c:\\temp\\out2.xml");

			//getPortfolios
			userId = null;
			System.out.println("--- getPortfolios("+userId+") ------");
			System.out.println(dataProvider.getPortfolios(xmlMimeType, userId,groupId, true, 0));

			//getNodes
			String uuid_getnodes = "43020565-b650-4655-b466-af2c69b0c714";
			System.out.println("--- getNodes("+uuid_getnodes+") ------");
			System.out.println(dataProvider.getNodes(xmlMimeType, null,
					userId, groupId, null,uuid_getnodes, null,
					null,null)
			);


			//deletePortfolio
			//System.out.println("--- deletePortfolio("+portfolioUuid+") ------");
			//System.out.println(dataProvider.deletePortfolio(portfolioUuid));
			//getPortfolios
			userId = null;
			System.out.println("--- getPortfolios("+userId+") ------");
			System.out.println(dataProvider.getPortfolios(xmlMimeType, userId,groupId, true, 0));


		}
		catch(Exception ex)
		{
			ex.printStackTrace();
		}


	}

}
