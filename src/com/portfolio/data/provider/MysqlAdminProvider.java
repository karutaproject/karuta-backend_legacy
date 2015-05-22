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

package com.portfolio.data.provider;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.UUID;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.activation.MimeType;
import javax.activation.MimeTypeParseException;
import javax.naming.InitialContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import javax.ws.rs.core.Response.Status;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.json.simple.JSONObject;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;



//import com.mysql.jdbc.Statement;
import java.sql.Statement;

import com.portfolio.data.utils.DomUtils;
import com.portfolio.data.utils.FileUtils;
import com.portfolio.data.utils.PictureUtils;
import com.portfolio.data.utils.SqlUtils;
import com.portfolio.rest.RestWebApplicationException;
import com.portfolio.security.Credential;
import com.portfolio.security.NodeRight;

/**
 * @author vassoill
 * Implï¿½mentation du dataProvider pour MySQL
 * @modif Thi Lan Anh Dinh
 * pour Oracle
 *
 */
public class MysqlAdminProvider implements AdminProvider
{

	private Connection connection;
	private final Credential credential;
	private final String xml = "";
	private String portfolioUuidPreliminaire = null; // Sert pour generer un uuid avant import du portfolio
	private final ArrayList<String> portfolioRessourcesImportUuid = new ArrayList();
	private final ArrayList<String> portfolioRessourcesImportPath = new ArrayList();
	public static final Integer RIGHT_GET = 1;
	public static final Integer RIGHT_POST = 2;
	public static final Integer RIGHT_PUT = 3;
	public static final Integer RIGHT_DELETE = 4;

	private final String dbserveur = "mysql";
//	private final String dbserveur = "oracle";

	@Override
	public void dataProvider()
	{
	}

	@Override
	public Connection getConnection()
	{
		return this.connection;
	}

	public MysqlAdminProvider() throws Exception
	{
		connect(new Properties());
		credential = new Credential(connection);

	}

	@Override
	public  void connect(Properties connectionProperties) throws Exception
	{

		InitialContext cxt = new InitialContext();
		if ( cxt == null ) {
			throw new Exception("no context found!");
		}

		DataSource ds = (DataSource) cxt.lookup( "java:/comp/env/jdbc/portfolio-backend" );

		if ( ds == null ) {
			throw new Exception("Data  jdbc/portfolio-backend source not found!");
		}
		connection = ds.getConnection();

	}

	@Override
	public void disconnect(){

		try {
			connection.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public Integer getMysqlNodeNextOrderChildren(String nodeUuid)  throws Exception
	{
		PreparedStatement st;
		String sql;

		// On recupere d'abord les informations dans la table structures
		sql = "SELECT COUNT(*) as node_order FROM node WHERE node_parent_uuid = uuid2bin(?) GROUP BY node_parent_uuid";
		st = connection.prepareStatement(sql);
		st.setString(1, nodeUuid);

		java.sql.ResultSet res = st.executeQuery();
		try
		{
			res.next();
			return res.getInt("node_order");
		}
		catch(Exception ex)
		{
			return 0;
		}
	}


	public ResultSet getMysqlNode(String nodeUuid, int userId,  int groupId) throws SQLException
	{
		PreparedStatement st;
		String sql;

		// On recupere d'abord les informations dans la table structures
		sql = "SELECT bin2uuid(node_uuid) as node_uuid, bin2uuid(node_parent_uuid) as node_parent_uuid,  node_children_uuid as node_children_uuid, node_order, metadata, metadata_wad, metadata_epm, bin2uuid(res_node_uuid) as res_node_uuid,  bin2uuid(res_res_node_uuid) as res_res_node_uuid,  bin2uuid(res_context_node_uuid) as res_context_node_uuid, shared_res, shared_node, shared_node_res,bin2uuid(shared_res_uuid) AS shared_res_uuid, bin2uuid(shared_node_uuid) AS shared_node_uuid, bin2uuid(shared_node_res_uuid) AS shared_node_res_uuid,asm_type, xsi_type, semtag, semantictag, label, code, descr, format, modif_user_id, modif_date,  bin2uuid(portfolio_id) as portfolio_id FROM node WHERE node_uuid = uuid2bin(?) ";
		st = connection.prepareStatement(sql);
		st.setString(1, nodeUuid);

		// On doit vï¿½rifier le droit d'accï¿½s en lecture avant de retourner le noeud
		//if(!credential.getNodeRight(userId,groupId,nodeUuid,Credential.DELETE))
		//return null;
		//else
		return st.executeQuery();
	}

	public ResultSet getMysqlResource(String nodeUuid)
	{
		PreparedStatement st;
		String sql;

		try
		{
			// On recupere d'abord les informations dans la table structures
			sql  = "SELECT bin2uuid(node_uuid) AS node_uuid, xsi_type, content, user_id, modif_user_id, modif_date FROM resource_table WHERE node_uuid = uuid2bin(?) ";

			st = connection.prepareStatement(sql);
			st.setString(1, nodeUuid);

			return st.executeQuery();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}


		return null;
	}

	public ResultSet getMysqlResourceByNodeParentUuid(String nodeParentUuid)
	{
		PreparedStatement st;
		String sql;

		try
		{
			// On recupere d'abord les informations dans la table structures
			sql  = "SELECT bin2uuid(node_uuid) AS node_uuid, xsi_type, content, user_id, modif_user_id, modif_date FROM resource_table WHERE node_uuid IN ";
			sql += " (SELECT res_node_uuid FROM node WHERE node_uuid = uuid2bin(?) ) ";
			st = connection.prepareStatement(sql);
			st.setString(1, nodeParentUuid);

			return st.executeQuery();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}


		return null;
	}

	public ResultSet getMysqlResources(String portfolioUuid) throws SQLException
	{
		PreparedStatement st;
		String sql;

		//try
		//{
		// On recupere d'abord les informations dans la table structures
		sql = "SELECT bin2uuid(res_node_uuid) AS res_node_uuid  FROM node WHERE portfolio_id= uuid2bin(?) AND res_node_uuid IS NOT NULL AND res_node_uuid<>'' ";
		if (dbserveur.equals("oracle")){
			sql = "SELECT bin2uuid(res_node_uuid) AS res_node_uuid  FROM node WHERE portfolio_id= uuid2bin(?) AND res_node_uuid IS NOT NULL ";
		}
		st = connection.prepareStatement(sql);
		st.setString(1, portfolioUuid);

		return st.executeQuery();
	}


	public ResultSet getMysqlPortfolios(Integer userId, Boolean portfolioActive)
	{
		PreparedStatement st;
		String sql;

		try
		{
			// Si on est admin, on r�cup�re la liste compl�te
			if( credential.isAdmin(userId) )
			{
				sql = "SELECT bin2uuid(p.portfolio_id) AS portfolio_id,bin2uuid(p.root_node_uuid) as root_node_uuid,p.modif_user_id,p.modif_date,p.active, p.user_id FROM portfolio p WHERE 1=1 ";
				if(portfolioActive) sql += "  AND active = 1 "; else sql += "  AND active = 0 ";

				st = connection.prepareStatement(sql);
				return st.executeQuery();
			}

			// On recupere d'abord les informations dans la table structures

			/// Les portfolio de l'utilisateur
			sql = "SELECT  bin2uuid(p.portfolio_id) AS portfolio_id,bin2uuid(p.root_node_uuid) as root_node_uuid,p.modif_user_id,p.modif_date,p.active, p.user_id FROM portfolio p WHERE 1=1 ";
			if(userId!=null) sql += "  AND user_id = ? ";
			if(portfolioActive) sql += "  AND active = 1 "; else sql += "  AND active = 0 ";

			// Les portfolios dont on a re�u les droits
			sql += "UNION ";
			sql += "SELECT DISTINCT bin2uuid(p.portfolio_id) AS portfolio_id, bin2uuid(p.root_node_uuid) AS root_node_uuid, p.modif_user_id, p.modif_date, p.active, p.user_id " +
					"FROM group_user gu " +
					"LEFT JOIN group_info gi ON gu.gid=gi.gid " +
					"LEFT JOIN group_right_info gri ON gri.grid=gi.grid " +
					"LEFT JOIN portfolio p ON gri.portfolio_id=p.portfolio_id " +
					"WHERE gu.userid=? ";

			if(portfolioActive) sql += " AND p.active = 1";
			else sql += " AND p.active = 0";

			st = connection.prepareStatement(sql);
			if(userId!=null) st.setInt(1, userId);
			if(userId!=null) st.setInt(2, userId);
			//			  if(userId!=null) st.setInt(3, userId);

			return st.executeQuery();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}


		return null;
	}

	public Document getMysqlUserGroups(Integer userId)
	{
		PreparedStatement st;
		String sql;

		try
		{
			// Selection des groupes d'utilisateurs avec liste de ces derniers
			// + lien avec les groupes de droits
			// On doit faire une 'outer join' mais il y a pas �a sous MySQL donc on fait l'�quivalent
			sql = "SELECT gi.gid, gi.owner AS gowner, gi.label AS glabel, gri.grid, gri.owner AS growner, gri.label AS grlabel, GROUP_CONCAT(userid) AS users, bin2uuid(gri.portfolio_id) AS portfolio_id ";
			if (dbserveur.equals("oracle")){
				sql = "SELECT gi.gid, gi.owner AS gowner, gi.label AS glabel, gri.grid, gri.owner AS growner, gri.label AS grlabel, LISTAGG(userid, ',') WITHIN GROUP (ORDER BY userid) AS users, bin2uuid(gri.portfolio_id) AS portfolio_id ";
			}
			sql += "FROM group_info gi " +
					"LEFT JOIN group_user gu ON gi.gid=gu.gid " +
					"LEFT JOIN group_right_info gri ON gi.grid=gri.grid GROUP BY gi.gid " +
					"UNION ALL " +
					"SELECT gi.gid, gi.owner AS gowner, gi.label AS glabel, gri.grid, gri.owner AS growner, gri.label AS grlabel, NULL, bin2uuid(gri.portfolio_id) AS portfolio_id " +
					"FROM group_right_info gri " +
					"LEFT JOIN group_info gi ON gi.grid=gri.grid " +
					"WHERE gi.grid IS NULL ORDER BY portfolio_id";
			st = connection.prepareStatement(sql);

			DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
			Document doc = docBuilder.newDocument();
			Element root = doc.createElement("root");
			doc.appendChild(root);

			ResultSet result = st.executeQuery();
			String prevPort = "DUMMY";
			Element port_node = null;
			while( result.next() )
			{
				Integer gid = result.getInt("gid");
				Integer gowner = result.getInt("gowner");
				String glabel = result.getString("glabel");
				Integer grid = result.getInt("grid");
				Integer growner = result.getInt("growner");
				String grlabel = result.getString("grlabel");
				String users = result.getString("users");
				String portfolio_id = result.getString("portfolio_id");

				if( portfolio_id == null ) portfolio_id = "";
				if( !prevPort.equals(portfolio_id) )
				{
					port_node = doc.createElement("portfolio");
					port_node.setAttribute("id", portfolio_id);
					prevPort = portfolio_id;
					root.appendChild(port_node);
				}

				Element groupinfo = doc.createElement("group_info");
				Element group = doc.createElement("group_user");
				group.setAttribute("id", Integer.toString(gid));
				group.setAttribute("owner", Integer.toString(gowner));
				Element label = doc.createElement("label");
				label.setTextContent(glabel);
				group.appendChild(label);
				Element usersNode = doc.createElement("users");
				usersNode.setTextContent(users);
				group.appendChild(usersNode);

				Element grlabelNode = doc.createElement("group_right");
				grlabelNode.setAttribute("id", Integer.toString(grid));
				grlabelNode.setAttribute("owner", Integer.toString(growner));
				grlabelNode.setTextContent(grlabel);

				groupinfo.appendChild(group);
				groupinfo.appendChild(grlabelNode);

				port_node.appendChild(groupinfo);

			}

			return doc;
		}
		catch (Exception e){ e.printStackTrace(); }

		return null;
	}


	private ResultSet getMysqlGroupRights(Integer userId, Integer groupId)
	{
		PreparedStatement st;
		String sql;

		try
		{
			// On recupere d'abord les informations dans la table structures

			sql = "SELECT bin2uuid(id) as id,RD,WR,DL,SB,AD,types_id,gid,gr.grid,gi.owner,gi.label FROM group_rights gr, group_info gi WHERE  gr.grid = gi.grid AND gi.gid = ?";
			//if(userId!=null) sql += "  AND cr.userid = ? ";
			//sql += " ORDER BY display_name ASC ";
			st = connection.prepareStatement(sql);
			st.setInt(1, groupId);

			return st.executeQuery();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		return null;
	}

	private ResultSet getMysqlNodeResultset(String nodeUuid)
	{
		PreparedStatement st;
		String sql;

		try
		{
			sql = "SELECT bin2uuid(node_uuid) as node_uuid, bin2uuid(node_parent_uuid) as node_parent_uuid,  node_children_uuid as node_children_uuid, node_order, metadata, metadata_wad, metadata_epm, bin2uuid(res_node_uuid) as res_node_uuid,  bin2uuid(res_res_node_uuid) as res_res_node_uuid,  bin2uuid(res_context_node_uuid) as res_context_node_uuid, shared_res, shared_node, asm_type, xsi_type, semtag, label, code, descr, format, modif_user_id, modif_date,  bin2uuid(portfolio_id) as portfolio_id FROM node WHERE node_uuid = uuid2bin(?) ";
			st = connection.prepareStatement(sql);
			st.setString(1, nodeUuid);
			return st.executeQuery();
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			return null;
		}
	}

	private ResultSet getMysqlNodeInfosResultset(String nodeUuid)
	{
		PreparedStatement st;
		String sql;

		try
		{
			sql = "SELECT * FROM node_info WHERE node_uuid = uuid2bin(?) ";
			st = connection.prepareStatement(sql);
			st.setString(1, nodeUuid);
			return st.executeQuery();
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			return null;
		}
	}

	private ResultSet getMysqlPortfolioResultset(String portfolioUuid)
	{
		PreparedStatement st;
		String sql;

		try
		{
			sql = "SELECT bin2uuid(portfolio_id) AS portfolio_id,bin2uuid(model_id) AS model_id,bin2uuid(root_node_uuid) as root_node_uuid,modif_user_id,modif_date,active user_id FROM portfolio WHERE portfolio_id = uuid2bin(?) ";
			st = connection.prepareStatement(sql);
			st.setString(1, portfolioUuid);
			return st.executeQuery();
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			return null;
		}
	}


	private int insertMysqlPortfolio(String portfolioUuid,String rootNodeUuid,int modelId,int userId)
	{
		String sql = "";
		PreparedStatement st;

		try
		{
			if (dbserveur.equals("mysql")){
				sql  = "REPLACE INTO portfolio(portfolio_id,root_node_uuid,user_id,model_id,modif_user_id,modif_date) ";
				sql += "VALUES(uuid2bin(?),uuid2bin(?),?,?,?,?)";
			} else if (dbserveur.equals("oracle")){
				sql  = "MERGE INTO portfolio d USING (SELECT uuid2bin(?) portfolio_id,uuid2bin(?) root_node_uuid,? user_id,? model_id,? modif_user_id,? modif_date FROM DUAL) s ON (d.portfolio_id = s.portfolio_id) WHEN MATCHED THEN UPDATE SET d.root_node_uuid = s.root_node_uuid, d.user_id = s.user_id,d.model_id = s.model_id, d.modif_user_id = s.modif_user_id, d.modif_date = s.modif_date WHEN NOT MATCHED THEN INSERT (d.portfolio_id, d.root_node_uuid, d.user_id, d.model_id, d.modif_user_id, d.modif_date) VALUES (s.portfolio_id, s.root_node_uuid, s.user_id, s.model_id, s.modif_user_id, s.modif_date)";
			}
			st = connection.prepareStatement(sql);
			st.setString(1,portfolioUuid);
			st.setString(2, rootNodeUuid);
			st.setInt(3, userId);
			if (dbserveur.equals("mysql")){
				st.setInt(4,modelId);
			} else if (dbserveur.equals("oracle")){
				st.setString(4,String.format("%32s", Integer.toHexString(modelId)).replace(' ', '0'));
			}
			st.setInt(5, userId);
			if (dbserveur.equals("mysql")){
				st.setString(6, SqlUtils.getCurrentTimeStamp());
			} else if (dbserveur.equals("oracle")){
				st.setTimestamp(6, SqlUtils.getCurrentTimeStamp2());
			}

			return st.executeUpdate();

		}
		catch(Exception ex)
		{
			System.out.println("root_node_uuid : "+rootNodeUuid);
			ex.printStackTrace();
			return -1;
		}
	}

	private int updateMysqlPortfolioActive(String portfolioUuid, Boolean active)
	{
		String sql = "";
		PreparedStatement st;

		try
		{
			sql  = "UPDATE  portfolio SET active = ?  WHERE portfolio_id = uuid2bin(?) ";

			//Integer iActive = (active) ? 1 : 0;
			Integer iActive = 1;

			st = connection.prepareStatement(sql);

			st.setInt(1, iActive);
			st.setString(2, portfolioUuid);


			return st.executeUpdate();
		}
		catch(Exception ex)
		{
			//System.out.println("root_node_uuid : "+uuid);
			ex.printStackTrace();
			return -1;
		}
	}

	private int updateMysqlPortfolioModelId(String portfolioUuid, String portfolioModelId)
	{
		String sql = "";
		PreparedStatement st;

		try
		{
			sql  = "UPDATE  portfolio SET model_id = uuid2bin(?)  WHERE portfolio_id = uuid2bin(?) ";

			st = connection.prepareStatement(sql);

			st.setString(1, portfolioModelId);
			st.setString(2, portfolioUuid);

			return st.executeUpdate();
		}
		catch(Exception ex)
		{
			//System.out.println("root_node_uuid : "+uuid);
			ex.printStackTrace();
			return -1;
		}
	}

	private int deleteMySqlPortfolio(String portfolioUuid, int userId, int groupId) throws SQLException
	{
		String sql = "";
		PreparedStatement st;
		int status = 0;
		boolean hasRights = false;

		NodeRight nodeRight = credential.getPortfolioRight(userId, groupId, portfolioUuid, Credential.DELETE);
		if( credential.isAdmin(userId) || nodeRight.delete )
			hasRights = true;

		if(hasRights)
		{
			/// Si il y a quelque chose de particulier, on s'assure que tout soit bien nettoy� de fa�on s�par�
			try
			{
				connection.setAutoCommit(false);

				/// Portfolio
				sql = "DELETE FROM portfolio WHERE portfolio_id=uuid2bin(?)";
				st = connection.prepareStatement(sql);
				st.setString(1, portfolioUuid);
				st.executeUpdate();

				/// Nodes
				sql = "DELETE FROM node WHERE portfolio_id=uuid2bin(?)";
				st = connection.prepareStatement(sql);
				st.setString(1, portfolioUuid);
				st.executeUpdate();

				/// Group and rights
				sql = "DELETE gri, gi, gu, gr " +
						"FROM group_right_info gri " +
						"LEFT JOIN group_info gi ON gri.grid=gi.grid " +
						"LEFT JOIN group_user gu ON gi.gid=gu.gid " +
						"LEFT JOIN group_rights gr ON gri.grid=gr.grid " +
						"WHERE gri.portfolio_id=uuid2bin(?)";
				if (dbserveur.equals("oracle")){
					sql = "DELETE FROM group_right_info gri WHERE gri.portfolio_id=uuid2bin(?)";
				}
				st = connection.prepareStatement(sql);
				st.setString(1, portfolioUuid);
				st.executeUpdate();
			}
			catch( Exception e )
			{
				try{ connection.rollback(); }
				catch( SQLException e1 ){ e1.printStackTrace(); }
				e.printStackTrace();
			}
			finally
			{
				connection.setAutoCommit(true);
				connection.close();
				status = 1;
			}
		}
		return status;
	}

	private int deleteMySqlNode(String nodeUuid, String nodeParentUuid,int userId, int groupId) throws SQLException
	{
		String sql = "";
		PreparedStatement st;
		//try
		//{

		if(credential.hasNodeRight(userId,groupId,nodeUuid,credential.DELETE))
		{
			sql  = " DELETE FROM node WHERE node_uuid=uuid2bin(?) ";
			st = connection.prepareStatement(sql);
			st.setString(1, nodeUuid);
			Integer nbDeletedNodes = st.executeUpdate();

			// On met ï¿½ jour les enfants du parent
			updateMysqlNodeChildren(nodeParentUuid);

			return nbDeletedNodes;
		}
		//}
		//catch(Exception ex)
		//{
		//	ex.printStackTrace();
		//	return -1;
		//}
		return 0;
	}


	private boolean getNodeRight(int userId, int groupId, String nodeUuid, int delete) {
		// TODO Auto-generated method stub
		return false;
	}

	/*
	 *  Ecrit le noeud dans la base MySQL
	 */
	private int insertMySqlNode(String nodeUuid,String nodeParentUuid,String nodeChildrenUuid,
			String asmType,String xsiType,
			int sharedRes, int sharedNode, int sharedNodeRes, String sharedResUuid, String sharedNodeUuid,String sharedNodeResUuid, String metadata, String metadataWad, String metadataEpm,
			String semtag, String semanticTag,
			String label, String code, String descr,String format,int order, int modifUserId, String portfolioUuid)
	{
		String sql = "";
		PreparedStatement st;

		try
		{
			if(nodeChildrenUuid==null)
			{
				nodeChildrenUuid = getMysqlNodeResultset(nodeUuid).getString("node_children_uuid");
			}
		}
		catch(Exception ex)
		{

		}

		try
		{
			if (dbserveur.equals("mysql")){
				sql  = "REPLACE INTO node(node_uuid,node_parent_uuid,node_children_uuid,node_order,";
				sql += "asm_type,xsi_type,shared_res,shared_node,shared_node_res,shared_res_uuid,shared_node_uuid,shared_node_res_uuid, metadata,metadata_wad,metadata_epm,semtag,semantictag,label,code,descr,format,modif_user_id,modif_date,portfolio_id) ";
				sql += "VALUES(uuid2bin(?),uuid2bin(?),?,?,?,?,?,?,?,uuid2bin(?),uuid2bin(?),uuid2bin(?),?,?,?,?,?,?,?,?,?,?,?,uuid2bin(?))";
			} else if (dbserveur.equals("oracle")){
				sql = "MERGE INTO node d USING (SELECT uuid2bin(?) node_uuid,uuid2bin(?) node_parent_uuid,? node_children_uuid,? node_order,? asm_type,? xsi_type,? shared_res,? shared_node,? shared_node_res,uuid2bin(?) shared_res_uuid,uuid2bin(?) shared_node_uuid,uuid2bin(?) shared_node_res_uuid,? metadata,? metadata_wad,? metadata_epm,? semtag,? semantictag,? label,? code,? descr,? format,? modif_user_id,? modif_date,uuid2bin(?) portfolio_id FROM DUAL) s ON (d.node_uuid = s.node_uuid) WHEN MATCHED THEN UPDATE SET d.node_parent_uuid=s.node_parent_uuid,d.node_children_uuid=s.node_children_uuid,d.node_order=s.node_order,d.asm_type=s.asm_type,d.xsi_type=s.xsi_type,d.shared_res=s.shared_res,d.shared_node=s.shared_node,d.shared_node_res=s.shared_node_res,d.shared_res_uuid=s.shared_res_uuid,d.shared_node_uuid=s.shared_node_uuid,d.shared_node_res_uuid=s.shared_node_res_uuid,d.metadata=s.metadata,d.metadata_wad=s.metadata_wad,d.metadata_epm=s.metadata_epm,d.semtag=s.semtag,d.semantictag=s.semantictag,d.label=s.label,d.code=s.code,d.descr=s.descr,d.format=s.format,d.modif_user_id=s.modif_user_id,d.modif_date=s.modif_date,d.portfolio_id=s.portfolio_id WHEN NOT MATCHED THEN INSERT (d.node_uuid,d.node_parent_uuid,d.node_children_uuid,d.node_order,d.asm_type,d.xsi_type,d.shared_res,d.shared_node,d.shared_node_res,d.shared_res_uuid,d.shared_node_uuid,d.shared_node_res_uuid,d.metadata,d.metadata_wad,d.metadata_epm,d.semtag,d.semantictag,d.label,d.code,d.descr,d.format,d.modif_user_id,d.modif_date,d.portfolio_id) VALUES (s.node_uuid,s.node_parent_uuid,s.node_children_uuid,s.node_order,s.asm_type,s.xsi_type,s.shared_res,s.shared_node,s.shared_node_res,s.shared_res_uuid,s.shared_node_uuid,s.shared_node_res_uuid,s.metadata,s.metadata_wad,s.metadata_epm,s.semtag,s.semantictag,s.label,s.code,s.descr,s.format,s.modif_user_id,s.modif_date,s.portfolio_id)";
			}
			st = connection.prepareStatement(sql);
			st.setString(1, nodeUuid);
			st.setString(2, nodeParentUuid);
			st.setString(3, nodeChildrenUuid);
			st.setInt(4, order);
			st.setString(5, asmType);
			st.setString(6, xsiType);
			st.setInt(7, sharedRes);
			st.setInt(8, sharedNode);
			st.setInt(9, sharedNodeRes);
			st.setString(10,sharedResUuid);
			st.setString(11,sharedNodeUuid);
			st.setString(12,sharedNodeResUuid);
			st.setString(13, metadata);
			st.setString(14, metadataWad);
			st.setString(15, metadataEpm);
			st.setString(16, semtag);
			st.setString(17,semanticTag);
			st.setString(18, label);
			st.setString(19, code);
			st.setString(20, descr);
			st.setString(21, format);
			st.setInt(22, modifUserId);
			if (dbserveur.equals("mysql")){
				st.setString(23, SqlUtils.getCurrentTimeStamp());
			} else if (dbserveur.equals("oracle")){
				st.setTimestamp(23, SqlUtils.getCurrentTimeStamp2());
			}
			st.setString(24, portfolioUuid);

			return st.executeUpdate();

		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			return -1;
		}
	}

	private int updatetMySqlNode(String nodeUuid, String asmType,String xsiType,String semantictag, String label, String code, String descr,String format, String metadata,String metadataWad,String metadataEpm, int sharedRes, int sharedNode, int sharedNodeRes,int modifUserId) throws Exception
	{
		String sql = "";
		PreparedStatement st;

		sql  = "UPDATE node SET ";
		sql += "asm_type = ?,xsi_type = ?,semantictag = ?,label = ?,code = ?,descr = ?,format = ? ,metadata = ?,metadata_wad = ?, metadata_epm = ?,shared_res = ?,shared_node = ?,shared_node_res = ?, modif_user_id = ?,modif_date = ? ";
		sql += " WHERE node_uuid = uuid2bin(?) ";
		st = connection.prepareStatement(sql);

		st.setString(1, asmType);
		st.setString(2, xsiType);
		st.setString(3, semantictag);
		st.setString(4, label);
		st.setString(5, code);
		st.setString(6, descr);
		st.setString(7, format);

		st.setString(8, metadata);
		st.setString(9, metadataWad);
		st.setString(10, metadataEpm);

		st.setInt(11, sharedRes);
		st.setInt(12, sharedNode);
		st.setInt(13, sharedNodeRes);

		st.setInt(14, modifUserId);
		if (dbserveur.equals("mysql")){
			st.setString(15, SqlUtils.getCurrentTimeStamp());
		} else if (dbserveur.equals("oracle")){
			st.setTimestamp(15, SqlUtils.getCurrentTimeStamp2());
		}
		st.setString(16, nodeUuid);

		return st.executeUpdate();
	}

	private int updatetMySqlNodeOrder(String nodeUuid, int order) throws Exception
	{
		String sql = "";
		PreparedStatement st;

		sql  = "UPDATE node SET ";
		sql += " node_order = ? ";
		sql += " WHERE node_uuid = uuid2bin(?) ";
		st = connection.prepareStatement(sql);

		st.setInt(1, order);
		st.setString(2, nodeUuid);

		return st.executeUpdate();
	}

	/*
	 *  Ecrit le noeud dans la base MySQL
	 */
	private int updateMysqlNodeChildren(String nodeUuid)
	{
		PreparedStatement st;
		String sql;
		String nodeChildrenUuid = "";

		try
		{
			/// Re-num�rote les noeud (on commence � 0)
			sql = "UPDATE node SET node_order=@ii:=@ii+1 " +
					"WHERE node_parent_uuid=uuid2bin(?) AND (@ii:=-1)+2 " +  // Condition + valeur initiale de @ii, Il faut avoir un '1', d'o� le +2
					"ORDER by node_order";
			if (dbserveur.equals("oracle")){
				sql = "UPDATE node n1 SET n1.node_order=(SELECT (rnum-1) FROM (SELECT node_uuid, row_number() OVER (ORDER BY node_order asc) rnum, node_parent_uuid FROM node WHERE node_parent_uuid=uuid2bin(?)) n2 WHERE n1.node_uuid= n2.node_uuid) WHERE n1.node_parent_uuid=?";
			}
			st = connection.prepareStatement(sql);
			st.setString(1, nodeUuid);
			if (dbserveur.equals("oracle")){
				st.setString(2, nodeUuid);
			}
			st.executeUpdate();

			/// Met � jour les enfants
			if (dbserveur.equals("mysql")){
				sql = "UPDATE node n1, "
					+ "(SELECT GROUP_CONCAT(bin2uuid(COALESCE(n2.shared_node_uuid,n2.node_uuid)) ORDER BY n2.node_order) AS value "
					+ "FROM node n2 "
					+ "WHERE n2.node_parent_uuid=uuid2bin(?) "
					+ "GROUP BY n2.node_parent_uuid) tmp "
					+ "SET n1.node_children_uuid=tmp.value "
					+ "WHERE n1.node_uuid=uuid2bin(?)";
			} else if (dbserveur.equals("oracle")){
		          sql = "UPDATE node SET node_children_uuid=(SELECT LISTAGG(bin2uuid(COALESCE(n2.shared_node_uuid,n2.node_uuid)), ',') WITHIN GROUP (ORDER BY n2.node_order) AS value FROM node n2 WHERE n2.node_parent_uuid=uuid2bin(?) GROUP BY n2.node_parent_uuid) WHERE node_uuid=uuid2bin(?)";
			}
			st = connection.prepareStatement(sql);
			st.setString(1, nodeUuid);
			st.setString(2, nodeUuid);
			return st.executeUpdate();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return -1;
	}

	private int insertMysqlResource(String uuid,String parentUuid, String xsiType,String content,String portfolioModelId, int sharedNodeRes,int sharedRes, int userId)
	{
		String sql = "";
		PreparedStatement st;

		try
		{
			if(   ((xsiType.equals("nodeRes") && sharedNodeRes==1)
					|| (!xsiType.equals("context") && !xsiType.equals("nodeRes") && sharedRes==1)
					)
					&& portfolioModelId!=null
					)
			{
				// On ne fait rien

			}
			else
			{
				if (dbserveur.equals("mysql")){
					sql  = "REPLACE INTO resource_table(node_uuid,xsi_type,content,user_id,modif_user_id,modif_date) ";
					sql += "VALUES(uuid2bin(?),?,?,?,?,?)";
				} else if (dbserveur.equals("oracle")){
					sql = "MERGE INTO resource_table d USING (SELECT uuid2bin(?) node_uuid,? xsi_type,? content,? user_id,? modif_user_id,? modif_date FROM DUAL) s ON (d.node_uuid = s.node_uuid) WHEN MATCHED THEN UPDATE SET d.xsi_type = s.xsi_type, d.content = s.content, d.user_id = s.user_id, d.modif_user_id = s.modif_user_id, d.modif_date = s.modif_date WHEN NOT MATCHED THEN INSERT (d.node_uuid, d.xsi_type, d.content, d.user_id, d.modif_user_id, d.modif_date) VALUES (s.node_uuid, s.xsi_type, s.content, s.user_id, s.modif_user_id, s.modif_date)";
				}
				st = connection.prepareStatement(sql);
				st.setString(1,uuid);
				st.setString(2,xsiType);
				st.setString(3, content);
				st.setInt(4,userId);
				st.setInt(5, userId);
				if (dbserveur.equals("mysql")){
					st.setString(6, SqlUtils.getCurrentTimeStamp());
				} else if (dbserveur.equals("oracle")){
					st.setTimestamp(6, SqlUtils.getCurrentTimeStamp2());
				}

				st.executeUpdate();
			}
			// Ensuite on met ï¿½ jour les id ressource au niveau du noeud parent
			if(xsiType.equals("nodeRes"))
			{
				sql = " UPDATE node SET res_res_node_uuid =uuid2bin(?), shared_node_res_uuid=uuid2bin(?) ";
				sql += " WHERE node_uuid = uuid2bin(?) ";
				st = connection.prepareStatement(sql);
				st.setString(1, uuid);
				if(sharedNodeRes==1 && portfolioModelId!=null)
					st.setString(2,uuid);
				else
					st.setString(2,null);
				st.setString(3,parentUuid);
			}
			else if(xsiType.equals("context"))
			{
				sql = " UPDATE node SET res_context_node_uuid=uuid2bin(?)";
				sql += " WHERE node_uuid = uuid2bin(?) ";
				st = connection.prepareStatement(sql);
				st.setString(1, uuid);
				st.setString(2,parentUuid);
			}
			else
			{
				sql = " UPDATE node SET res_node_uuid=uuid2bin(?), shared_res_uuid=uuid2bin(?) ";
				sql += " WHERE node_uuid = uuid2bin(?) ";
				st = connection.prepareStatement(sql);
				st.setString(1, uuid);
				if(sharedRes==1 && portfolioModelId!=null)
					st.setString(2,uuid);
				else
					st.setString(2,null);
				st.setString(3,parentUuid);
			}

			return st.executeUpdate();
		}
		catch(Exception ex)
		{
			//System.out.println("root_node_uuid : "+uuid);
			ex.printStackTrace();
			return -1;
		}
	}

	private int updateMysqlResource(String uuid,String xsiType, String content,int userId)
	{
		String sql = "";
		PreparedStatement st;

		try
		{
			if(xsiType!=null)
			{
				if (dbserveur.equals("mysql")){
					sql  = "REPLACE INTO resource_table(node_uuid,xsi_type,content,user_id,modif_user_id,modif_date) ";
					sql += "VALUES(uuid2bin(?),?,?,?,?,?)";
				} else if (dbserveur.equals("oracle")){
					sql = "MERGE INTO resource_table d USING (SELECT uuid2bin(?) node_uuid,? xsi_type,? content,? user_id,? modif_user_id,? modif_date FROM DUAL) s ON (d.node_uuid = s.node_uuid) WHEN MATCHED THEN UPDATE SET d.xsi_type = s.xsi_type, d.content = s.content, d.user_id = s.user_id, d.modif_user_id = s.modif_user_id, d.modif_date = s.modif_date WHEN NOT MATCHED THEN INSERT (d.node_uuid, d.xsi_type, d.content, d.user_id, d.modif_user_id, d.modif_date) VALUES (s.node_uuid, s.xsi_type, s.content, s.user_id, s.modif_user_id, s.modif_date)";
				}
				st = connection.prepareStatement(sql);
				st.setString(1,uuid);
				st.setString(2,xsiType);
				st.setString(3, content);
				st.setInt(4,userId);
				st.setInt(5, userId);
				if (dbserveur.equals("mysql")){
					st.setString(6, SqlUtils.getCurrentTimeStamp());
				} else if (dbserveur.equals("oracle")){
					st.setTimestamp(6, SqlUtils.getCurrentTimeStamp2());
				}

				return st.executeUpdate();
			}
			else
			{
				sql  = "UPDATE  resource_table SET content = ?,user_id = ?,modif_user_id = ?,modif_date = ? WHERE node_uuid = uuid2bin(?) ";

				st = connection.prepareStatement(sql);

				st.setString(1, content);
				st.setInt(2,userId);
				st.setInt(3, userId);
				if (dbserveur.equals("mysql")){
					st.setString(4, SqlUtils.getCurrentTimeStamp());
				} else if (dbserveur.equals("oracle")){
					st.setTimestamp(4, SqlUtils.getCurrentTimeStamp2());
				}
				st.setString(5,uuid);

				return st.executeUpdate();
			}


		}
		catch(Exception ex)
		{
			//System.out.println("root_node_uuid : "+uuid);
			ex.printStackTrace();
			return -1;
		}
	}

	private int updateMysqlResourceByXsiType(String nodeUuid, String xsiType,String content,int userId)
	{
		String sql = "";
		PreparedStatement st;

		try
		{
			if(xsiType.equals("nodeRes"))
			{
				sql = " UPDATE resource_table SET content=?,user_id=?,modif_user_id=?,modif_date=? ";
				sql += " WHERE node_uuid IN (SELECT res_res_node_uuid FROM node ";
				sql += " WHERE node_uuid=uuid2bin(?))  ";

				/// Interp�tation du code (vive le hack... Non)
				Document doc = DomUtils.xmlString2Document("<?xml version='1.0' encoding='UTF-8' standalone='no'?><res>"+content+"</res>", new StringBuffer());
				NodeList nodes = doc.getElementsByTagName("code");
				Node code = nodes.item(0);
				if( code != null )
				{
					Node codeContent = code.getFirstChild();
					String codeVal;
					if( codeContent != null )
					{
						codeVal = codeContent.getNodeValue();
						String sq = "UPDATE node SET code=? WHERE node_uuid=uuid2bin(?)";
						st = connection.prepareStatement(sq);
						st.setString(1, codeVal);
						st.setString(2, nodeUuid);
						st.executeUpdate();
						st.close();
					}
				}
			}
			else if(xsiType.equals("context"))
			{
				sql = " UPDATE resource_table SET content=?,user_id=?,modif_user_id=?,modif_date=? ";
				sql += " WHERE node_uuid IN (SELECT res_context_node_uuid FROM node ";
				sql += " WHERE node_uuid=uuid2bin(?))  ";
			}
			else
			{
				sql = " UPDATE resource_table SET content=?,user_id=?,modif_user_id=?,modif_date=? ";
				sql += " WHERE node_uuid IN (SELECT res_node_uuid FROM node ";
				sql += " WHERE node_uuid=uuid2bin(?))  ";
			}
			st = connection.prepareStatement(sql);
			st.setString(1, content);

			st.setInt(2,userId);
			st.setInt(3, userId);
			if (dbserveur.equals("mysql")){
				st.setString(4, SqlUtils.getCurrentTimeStamp());
			} else if (dbserveur.equals("oracle")){
				st.setTimestamp(4, SqlUtils.getCurrentTimeStamp2());
			}
			st.setString(5,nodeUuid);
			// st.setString(6,xsiType);

			return st.executeUpdate();

		}
		catch(Exception ex)
		{
			//System.out.println("root_node_uuid : "+uuid);
			ex.printStackTrace();
			return -1;
		}
	}

	private int deleteMySqlResource(String resourceUuid, int userId, int groupId) throws SQLException
	{
		String sql = "";
		PreparedStatement st;

		if(credential.hasNodeRight(userId,groupId,resourceUuid,Credential.DELETE))
		{
			sql  = " DELETE FROM resource_table WHERE node_uuid=uuid2bin(?) ";
			st = connection.prepareStatement(sql);
			st.setString(1, resourceUuid);
			return st.executeUpdate();
		}
		return 0;

	}

	@Override
	public Object putUser(int userId,String oAuthToken, String oAuthSecret)
	{
		return insertMysqlUser(userId, oAuthToken,oAuthSecret);
	}

	@Override
	public Object getUserGroups(int userId) throws Exception
	{
		Document res = getMysqlUserGroups(userId);
		StringWriter stw = new StringWriter();
		Transformer serializer = TransformerFactory.newInstance().newTransformer();
		serializer.transform(new DOMSource(res), new StreamResult(stw));

		/*
		String result = "<groups>";
		while(res.next())
		{
			result += "<group ";
			result += DomUtils.getXmlAttributeOutput("id", res.getString("gid"))+" ";
			result += DomUtils.getXmlAttributeOutput("owner", res.getString("owner"))+" ";
			result += DomUtils.getXmlAttributeOutput("templateId", res.getString("grid"))+" ";
			result += ">";
			result += DomUtils.getXmlElementOutput("label", res.getString("label"));
			result += "</group>";
		}

		result += "</groups>";
		//*/
		return stw.toString();
	}

	@Override
	public boolean isUserMemberOfGroup(int userId,int groupId)
	{
		return this.credential.isUserMemberOfGroup(userId,groupId);
	}

	@Override
	public Object getUser(int userId)
	{
		try
		{
			return getMySqlUser(userId);
		}
		catch(Exception ex)
		{
			return null;
		}
	}

	public ResultSet getMySqlUser(int userId) throws Exception
	{
		PreparedStatement st;
		String sql;
		ResultSet res;

		try
		{
			sql = "SELECT * FROM user WHERE user_id = ? ";
			st = connection.prepareStatement(sql);
			st.setInt(1, userId);
			res = st.executeQuery();
			res.next();
			return res;
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			return null;
		}
	}

	@Deprecated
	private int insertMysqlUser(int userId, String oAuthToken,String oAuthSecret)
	{
		String sql = "";
		PreparedStatement st;

		try
		{
			sql  = "REPLACE INTO user(user_id,oauth_token,oauth_secret) ";

			sql += "VALUES(?,?,?)";
			st = connection.prepareStatement(sql);
			st.setInt(1,userId);
			st.setString(2, oAuthToken);
			st.setString(3, oAuthSecret);

			return st.executeUpdate();

		}
		catch(Exception ex)
		{
			//System.out.println("root_node_uuid : "+uuid);
			ex.printStackTrace();
			return -1;
		}
	}

	private boolean insertMySqlLog(String url,String method,String headers,String inBody, String outBody, int code)
	{
		String sql = "";
		PreparedStatement st;

		try
		{
			sql  = "INSERT INTO log_table(log_date,log_url,log_method,log_headers,log_in_body,log_out_body,log_code) ";
			if (dbserveur.equals("mysql")){
				sql += "VALUES(NOW(),?,?,?,?,?,?)";
			} else if (dbserveur.equals("oracle")){
				sql += "VALUES(CURRENT_TIMESTAMP,?,?,?,?,?,?)";
			}
			st = connection.prepareStatement(sql);
			st.setString(1,url);
			st.setString(2, method);
			st.setString(3, headers);
			st.setString(4, inBody);
			st.setString(5, outBody);
			st.setInt(6, code);

			return st.execute();

		}
		catch(Exception ex)
		{
			//System.out.println("root_node_uuid : "+uuid);
			ex.printStackTrace();
			return false;
		}
	}

	public int updateMysqlFileUuid(String nodeUuidOrigine, String nodeUuidDestination)
	{
		String sql = "";
		PreparedStatement st;

		try
		{
			sql  = "UPDATE file_table SET node_uuid=uuid2bin(?) WHERE node_uuid=uuid2bin(?) ";

			st = connection.prepareStatement(sql);
			st.setString(1,nodeUuidDestination);
			st.setString(2, nodeUuidOrigine);

			return st.executeUpdate();

		}
		catch(Exception ex)
		{
			//System.out.println("root_node_uuid : "+uuid);
			ex.printStackTrace();
			return -1;
		}
	}

	public int updateMysqlFile(String nodeUuid,String lang,String fileName,String type,String extension,int size,byte[] fileBytes, int userId)
	{
		String sql = "";
		PreparedStatement st;

		try
		{
			if (dbserveur.equals("mysql")){
				sql  = "REPLACE INTO file_table(node_uuid,lang,name,type,extension,filesize,filecontent,modif_user_id,modif_date) ";
				sql += "VALUES(uuid2bin(?),?,?,?,?,?,?,?,?)";
			} else if (dbserveur.equals("oracle")){
				sql = "MERGE INTO file_table d USING (SELECT uuid2bin(?) node_uuid,? lang,? name,? type,? extension,? filesize,? filecontent,? modif_user_id,? modif_date FROM DUAL) s ON (d.node_uuid = s.node_uuid AND d.lang = s.lang) WHEN MATCHED THEN UPDATE SET d.name = s.name, d.type = s.type, d.extension = s.extension, d.filesize = s.filesize, d.filecontent = s.filecontent, d.modif_user_id = s.modif_user_id, d.modif_date = s.modif_date WHEN NOT MATCHED THEN INSERT (d.node_uuid, d.lang, d.name, d.type, d.extension, d.filesize, d.filecontent, d.modif_user_id, d.modif_date) VALUES (s.node_uuid, s.lang, s.name, s.type, s.extension, s.filesize, s.filecontent, s.modif_user_id, s.modif_date)";
			}
			st = connection.prepareStatement(sql);
			st.setString(1,nodeUuid);
			st.setString(2, lang);
			st.setString(3, fileName);
			st.setString(4, type);
			st.setString(5, extension);
			st.setInt(6, size);
			st.setBytes(7, fileBytes);
			st.setInt(8, userId);
			if (dbserveur.equals("mysql")){
				st.setString(9,SqlUtils.getCurrentTimeStamp());
			} else if (dbserveur.equals("oracle")){
				st.setTimestamp(9, SqlUtils.getCurrentTimeStamp2());
			}

			return st.executeUpdate();

		}
		catch(Exception ex)
		{
			//System.out.println("root_node_uuid : "+uuid);
			ex.printStackTrace();
			return -1;
		}
	}

	public Object getMysqlFile(String nodeUuid,String lang)
	{

		PreparedStatement st;
		String sql;
		ResultSet res;

		try
		{
			sql = "SELECT bin2uuid(node_uuid) as node_uuid, lang, name, type, extension, filesize, filecontent, modif_user_id, modif_date FROM file_table WHERE node_uuid = uuid2bin(?) AND lang = ?";
			st = connection.prepareStatement(sql);
			st.setString(1, nodeUuid);
			st.setString(2, lang);
			res = st.executeQuery();
			if(res.next())
				return res;
			else
				return null;
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			return null;
		}
	}

	public String getPortfolioRootNode(String portfolioUuid) throws SQLException
	{
		PreparedStatement st;
		String sql;
		ResultSet res;

		sql = "SELECT bin2uuid(root_node_uuid) AS root_node_uuid FROM portfolio WHERE portfolio_id = uuid2bin(?) ";
		st = connection.prepareStatement(sql);
		st.setString(1, portfolioUuid);
		res = st.executeQuery();
		res.next();
		return res.getString("root_node_uuid");
	}

	public String getPortfolioModelUuid(String portfolioUuid) throws SQLException
	{
		PreparedStatement st;
		String sql;
		ResultSet res;

		sql = "SELECT bin2uuid(model_id) AS model_id FROM portfolio WHERE portfolio_id = uuid2bin(?) ";
		st = connection.prepareStatement(sql);
		st.setString(1, portfolioUuid);
		res = st.executeQuery();
		res.next();
		return res.getString("model_id");
	}



	public int getPortfolioUserId(String portfolioUuid) throws SQLException
	{
		PreparedStatement st;
		String sql;
		ResultSet res;

		sql = "SELECT user_id FROM portfolio WHERE portfolio_id = uuid2bin(?) ";
		st = connection.prepareStatement(sql);
		st.setString(1, portfolioUuid);
		res = st.executeQuery();
		res.next();
		return res.getInt("user_id");
	}

	public String getNodeParentUuidByNodeUuid(String nodeUuid)
	{
		PreparedStatement st;
		String sql;
		ResultSet res;

		try
		{
			sql = "SELECT bin2uuid(node_parent_uuid) AS node_parent_uuid FROM node WHERE node_uuid = uuid2bin(?) ";
			st = connection.prepareStatement(sql);
			st.setString(1, nodeUuid);
			res = st.executeQuery();
			res.next();
			return res.getString("node_parent_uuid");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			return null;
		}
	}

	public String getPortfolioUuidByPortfolioCode(String portfolioCode)
	{
		PreparedStatement st;
		String sql;
		ResultSet res;

		try
		{
			sql  = "SELECT bin2uuid(portfolio_id) AS portfolio_id FROM portfolio WHERE active=1 AND root_node_uuid IN (";
			sql += " SELECT node_uuid  FROM node WHERE asm_type='asmRoot' AND res_res_node_uuid ";
			sql += " IN ( SELECT node_uuid FROM resource_table WHERE content LIKE ? AND xsi_type='nodeRes') ) ";
			st = connection.prepareStatement(sql);
			st.setString(1, "%<code>"+portfolioCode+"</code>%");
			res = st.executeQuery();

			if( res.next() )
				return res.getString("portfolio_id");
			else
				return "";
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			return null;
		}
	}

	@Override
	public String getResourceNodeUuidByParentNodeUuid(String nodeParentUuid)
	{
		PreparedStatement st;
		String sql;
		ResultSet res;

		try
		{
			sql = "SELECT bin2uuid(res_node_uuid)AS res_node_uuid FROM node WHERE node_uuid = uuid2bin(?) ";
			st = connection.prepareStatement(sql);
			st.setString(1, nodeParentUuid);
			res = st.executeQuery();
			res.next();
			return res.getString("res_node_uuid");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			return null;
		}

	}

	public Integer getNodeOrderByNodeUuid(String nodeUuid)
	{
		PreparedStatement st;
		String sql;
		ResultSet res;

		try
		{
			sql = "SELECT node_order FROM node WHERE node_uuid = uuid2bin(?) ";
			st = connection.prepareStatement(sql);
			st.setString(1, nodeUuid);
			res = st.executeQuery();
			res.next();
			return res.getInt("node_order");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			return 0;
		}
	}


	@Override
	public String getPortfolioUuidByNodeUuid(String nodeUuid) throws Exception
	{
		PreparedStatement st;
		String sql;
		ResultSet res;
		String result = null;

		sql = "SELECT bin2uuid(portfolio_id) AS portfolio_id FROM node WHERE node_uuid = uuid2bin(?) ";
		st = connection.prepareStatement(sql);
		st.setString(1, nodeUuid);
		res = st.executeQuery();
		res.next();
		try
		{
			result = res.getString("portfolio_id");
		}
		catch(Exception ex)
		{
			//ex.printStackTrace();

		}
		return result;
	}


	@Override
	public Object getPortfolio(MimeType outMimeType, String portfolioUuid, int userId, int groupId, String label, String resource, String files) throws Exception
	{
		String rootNodeUuid = getPortfolioRootNode(portfolioUuid);
		String header = "";
		String footer = "";
		NodeRight nodeRight = credential.getPortfolioRight(userId,groupId, portfolioUuid, Credential.READ);
		if( !nodeRight.read )
			return "faux";

		if(outMimeType.getSubType().equals("xml"))
		{
			//			header = "<portfolio xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' schemaVersion='1.0'>";
			//			footer = "</portfolio>";

			DocumentBuilderFactory documentBuilderFactory =DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder;
			Document document=null;
			try
			{
				documentBuilder = documentBuilderFactory.newDocumentBuilder();
				document = documentBuilder.newDocument();
				document.setXmlStandalone(true);
			}
			catch( ParserConfigurationException e )
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			Element root = document.createElement("portfolio");
			root.setAttribute("id", portfolioUuid);
			root.setAttribute("code", "0");

			//// Noeuds suppl�mentaire pour WAD
			// Version
			Element verNode = document.createElement("version");
			Text version = document.createTextNode("3");
			verNode.appendChild(version);
			root.appendChild(verNode);
			// metadata-wad
			Element metawad = document.createElement("metadata-wad");
			metawad.setAttribute("prog", "main.jsp");
			metawad.setAttribute("owner", "N");
			root.appendChild(metawad);

			//          root.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
			//          root.setAttribute("schemaVersion", "1.0");
			document.appendChild(root);

			getLinearXml(portfolioUuid, rootNodeUuid, root, true, null, userId, groupId);

			StringWriter stw = new StringWriter();
			Transformer serializer = TransformerFactory.newInstance().newTransformer();
			serializer.transform(new DOMSource(document), new StreamResult(stw));

			if(resource !=null && files != null){

				if(resource.equals("true") && files.equals("true"))
				{
					String adressedufichier =  System.getProperty("user.dir") +"/tmp_getPortfolio_"+ new Date() +".xml";
					String adresseduzip = System.getProperty("user.dir") + "/tmp_getPortfolio_"+ new Date() +".zip";

					File file = null;
					PrintWriter ecrire;
					PrintWriter ecri ;
					try
					{
						file = new File(adressedufichier);
						ecrire = new PrintWriter(new FileOutputStream(adressedufichier));
						ecrire.println(stw.toString());
						ecrire.flush();
						ecrire.close();
						System.out.print("fichier cree ");
					}
					catch(IOException ioe){
						System.out.print("Erreur : ");
						ioe.printStackTrace();
					}

					try{
						String fileName = portfolioUuid+".zip";

						ZipOutputStream zip =
								new ZipOutputStream(new FileOutputStream(adresseduzip));
						zip.setMethod(ZipOutputStream.DEFLATED);
						zip.setLevel(Deflater.BEST_COMPRESSION);
						File dataDirectories = new File(file.getName());
						FileInputStream fis = new FileInputStream(dataDirectories);
						byte[] bytes = new byte[fis.available()];
						fis.read(bytes);

						ZipEntry entry = new ZipEntry(file.getName());
						entry.setTime(dataDirectories.lastModified());
						zip.putNextEntry(entry);
						zip.write(bytes);
						zip.closeEntry();
						fis.close();
						//zipDirectory(dataDirectories, zip);
						zip.close();

						file.delete();

						return adresseduzip;

					} catch (FileNotFoundException fileNotFound) {
						fileNotFound.printStackTrace();

					} catch (IOException io) {

						io.printStackTrace();
					}
				}
			}

			return stw.toString();

		}
		else if(outMimeType.getSubType().equals("json"))
		{
			header = "{\"portfolio\": { \"-xmlns:xsi\": \"http://www.w3.org/2001/XMLSchema-instance\",\"-schemaVersion\": \"1.0\",";
			footer = "}}";
		}

		return header+getNode(outMimeType, rootNodeUuid,true, userId, groupId, label).toString()+footer;
	}

	@Override
	public Object getPortfolioByCode(MimeType mimeType, String portfolioCode, int userId, int groupId, String resources) throws Exception
	{
		PreparedStatement st = null;
		String sql;
		ResultSet res = null;
		String pid = this.getPortfolioUuidByPortfolioCode(portfolioCode);
		Boolean withResources = false;
		String result = "";

		try
		{
			withResources = Boolean.parseBoolean(resources);

		}
		catch(Exception ex) {}

		if(withResources)
		{

			return this.getPortfolio(new MimeType("text/xml"),pid,userId, groupId, null, null, null).toString();
		}
		else
		{
			try
			{
				sql = "SELECT  bin2uuid(portfolio_id) AS portfolio_id,bin2uuid(root_node_uuid) as root_node_uuid, modif_user_id,modif_date, active, user_id "
						+ "FROM portfolio "
						+ "WHERE portfolio_id = uuid2bin(?) ";
				st = connection.prepareStatement(sql);
				st.setString(1, pid);
				res = st.executeQuery();

			}
			catch (Exception e)
			{
				e.printStackTrace();
			}

			if(res.next())
			{
				result += "<portfolio ";
				result += DomUtils.getXmlAttributeOutput("id", res.getString("portfolio_id"))+" ";
				result += DomUtils.getXmlAttributeOutput("root_node_id", res.getString("root_node_uuid"))+" ";
				result += ">";
				result += getNodeXmlOutput(res.getString("root_node_uuid"), false, "nodeRes", userId,  groupId, null,false);
				result += "</portfolio>";
			}
		}

		return result;
	}

	@Override
	public Object getPortfolios(MimeType outMimeType, int userId,int groupId, Boolean portfolioActive) throws SQLException
	{
		ResultSet res = getMysqlPortfolios(userId,portfolioActive);
		String result = "";
		if(outMimeType.getSubType().equals("xml"))
		{
			result = "<portfolios>";
			while(res.next())
			{
				String isOwner = "N";
				if( Integer.parseInt(res.getString("user_id")) == userId )
					isOwner = "Y";

				result += "<portfolio ";
				result += DomUtils.getXmlAttributeOutput("id", res.getString("portfolio_id"))+" ";
				result += DomUtils.getXmlAttributeOutput("root_node_id", res.getString("root_node_uuid"))+" ";
				result += DomUtils.getXmlAttributeOutput("owner",isOwner)+" ";
				result += DomUtils.getXmlAttributeOutput("modified", res.getString("modif_date"))+" ";
				result += ">";
				result += getNodeXmlOutput(res.getString("root_node_uuid"), false, "nodeRes", userId,  groupId, null,false);
				result += "</portfolio>";
			}
			result += "</portfolios>";
		}
		else if(outMimeType.getSubType().equals("json"))
		{
			result = "{ \"portfolios\": { \"portfolio\": [";
			boolean firstPass = false;
			while(res.next())
			{
				if(firstPass) result += ",";
				result += "{ ";
				result += DomUtils.getJsonAttributeOutput("id", res.getString("portfolio_id"))+", ";
				result += DomUtils.getJsonAttributeOutput("root_node_id", res.getString("root_node_uuid"))+", ";
				result += getNodeJsonOutput(res.getString("root_node_uuid"), false, "nodeRes", userId,  groupId,null,false);
				result += "} ";
				firstPass = true;
			}
			result += "] } }";
		}
		res.close();

		return result;
	}

	@Override
	public Object getNodeBySemanticTag(MimeType outMimeType, String portfolioUuid, String semantictag, int userId, int groupId) throws Exception
	{
		ResultSet res;
		String nodeUuid;

		// On recupere d'abord l'uuid du premier noeud trouvï¿½ correspondant au semantictag
		res = this.getMysqlNodeUuidBySemanticTag(portfolioUuid, semantictag);
		res.next();
		nodeUuid = res.getString("node_uuid");

		if(!credential.hasNodeRight(userId,groupId,nodeUuid, credential.READ))
			return null;

		if(outMimeType.getSubType().equals("xml"))
			return getNodeXmlOutput(nodeUuid,true,null,userId, groupId, null,true);
		else if(outMimeType.getSubType().equals("json"))
			return "{"+getNodeJsonOutput(nodeUuid,true,null,userId, groupId,null,true)+"}";
		else
			return null;
	}

	@Override
	public Object getNodesBySemanticTag(MimeType outMimeType, int userId,int groupId, String portfolioUuid, String semanticTag) throws SQLException
	{
		ResultSet res = this.getMysqlNodeUuidBySemanticTag(portfolioUuid, semanticTag);
		String result = "";
		if(outMimeType.getSubType().equals("xml"))
		{
			result = "<nodes>";
			while(res.next())
			{
				String nodeUuid = res.getString("node_uuid");
				if(!credential.hasNodeRight(userId,groupId,nodeUuid, credential.READ))
					return null;

				result += "<node ";
				result += DomUtils.getXmlAttributeOutput("id", nodeUuid)+" ";
				result += ">";
				result += "</node>";
			}
			result += "</nodes>";
		}
		else if(outMimeType.getSubType().equals("json"))
		{

			result = "{ \"nodes\": { \"node\": [";
			boolean firstPass = false;
			while(res.next())
			{
				if(firstPass) result += ",";
				result += "{ ";
				result += DomUtils.getJsonAttributeOutput("id", res.getString("id"))+", ";

				result += "} ";
				firstPass = true;
			}
			result += "] } }";
		}

		return result;
	}

	public void genererPortfolioUuidPreliminaire()
	{
		this.portfolioUuidPreliminaire = UUID.randomUUID().toString();
	}

	@Override
	public Object postPortfolio(MimeType inMimeType,MimeType outMimeType,String in,  int userId, int groupId, String portfolioModelId) throws Exception
	{
		//		if(!credential.isAdmin(userId))
		//			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		StringBuffer outTrace = new StringBuffer();
		String portfolioUuid;

		// Si le modele est renseignï¿½, on ignore le XML postï¿½ et on rï¿½cupere le contenu du modele
		// ï¿½ la place
		if(portfolioModelId!=null)
			in = getPortfolio(inMimeType,portfolioModelId,userId, groupId, null, null, null).toString();

		// On gï¿½nï¿½re un nouvel uuid
		if(this.portfolioUuidPreliminaire!=null)
			portfolioUuid = portfolioUuidPreliminaire;
		else
			portfolioUuid = UUID.randomUUID().toString();
		//On vï¿½rifie l'existence du portfolio. Si le portfolio existe, on ne l'ï¿½crase pas
		//if(getPortfolioRootNode(portfolioUuid)==null)
		//{
		//	throw new Exception("Le portfolio dont l'uuid="+portfolioUuid+" existe");
		//}
		//else
		putPortfolio(inMimeType,outMimeType,in, portfolioUuid,userId,true,groupId, portfolioModelId);
		updateMysqlPortfolioModelId(portfolioUuid,portfolioModelId);

		String result = "<portfolios>";
		result += "<portfolio ";
		result += DomUtils.getXmlAttributeOutput("id", portfolioUuid)+" ";
		result += "/>";
		result += "</portfolios>";
		return result;
	}

	@Override
	public Object putPortfolio(MimeType inMimeType, MimeType outMimeType, String in, String portfolioUuid, int userId, Boolean portfolioActive, int groupId, String portfolioModelId) throws Exception
	{
		StringBuffer outTrace = new StringBuffer();

		//		if(!credential.isAdmin(userId))
		//			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		ResultSet resPortfolio = getMysqlPortfolioResultset(portfolioUuid);
		if(resPortfolio!=null)
		{
			resPortfolio.next();

		}

		// Si on est en PUT le portfolio existe donc on regarde si modele ou pas
		try
		{
			portfolioModelId = resPortfolio.getString("model_id");
		}
		catch(Exception ex)
		{

		}

		if(userId<=0)
		{
			if(resPortfolio!=null) userId = resPortfolio.getInt("user_id");
		}

		if(in.length()>0)
		{
			//if(resPortfolio!=null) deleteMySqlPortfolio(portfolioUuid,userId, groupId);

			Document doc = DomUtils.xmlString2Document(in, outTrace);

			Node rootNode = (doc.getElementsByTagName("portfolio")).item(0);
			if(rootNode==null)
				throw new Exception("Root Node (portfolio) not found !");
			else
			{
				/*if(delete){
					deletePortfolio(portfolioUuid, userId, groupId);
					writeNode(rootNode, portfolioUuid, portfolioModelId, userId,0,true,null,0,0,true);
				}*/
				rootNode = (doc.getElementsByTagName("asmRoot")).item(0);

				String uuid = UUID.randomUUID().toString();

				insertMysqlPortfolio(portfolioUuid,uuid,0,userId);

				writeNode(rootNode, portfolioUuid, portfolioModelId, userId,0, uuid,null,0,0,true);
			}
		}
		updateMysqlPortfolioActive(portfolioUuid,portfolioActive);

		return true;
	}

	@Override
	public Object deletePortfolio(String portfolioUuid, int userId, int groupId) throws SQLException
	{
		/*
		if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");
		//*/

		return this.deleteMySqlPortfolio(portfolioUuid, userId, groupId);
	}

	@Override
	public Object getNodes(MimeType outMimeType, String portfolioUuid, int userId,int groupId, String semtag, String parentUuid, String filterId, String filterParameters, String sortId) throws SQLException
	{
		return getNodeXmlListOutput(parentUuid, true, userId, groupId);
	}

	private  StringBuffer getNodeJsonOutput(String nodeUuid,boolean withChildren, String withChildrenOfXsiType,int userId,int groupId,String label, boolean checkSecurity) throws SQLException
	{
		StringBuffer result = new StringBuffer();
		ResultSet resNode = getMysqlNode(nodeUuid,userId,groupId);
		ResultSet resResource;

		if(checkSecurity)
		{
			NodeRight nodeRight = credential.getNodeRight(userId,groupId,nodeUuid, label);
			//
			if(!nodeRight.read)
				return result;
		}

		String indentation = " ";

		//try
		//{
		//			resNode.next();
		if(resNode.next())
		{
			result.append("\""+resNode.getString("asm_type")+"\": { "+DomUtils.getJsonAttributeOutput("id",resNode.getString("node_uuid"))+", ");
			//if(resNodes.getString("node_parent_uuid").length()>0)
			//	result.append(getXmlAttributeOutput("parent-uuid",resNodes.getString("node_parent_uuid"))+" ");
			//if(resNodes.getString("semtag")!=null)
			//if(resNodes.getString("semtag").length()>0)
			result.append(DomUtils.getJsonAttributeOutput("semantictag",resNode.getString("semtag"))+", ");

			if(resNode.getString("xsi_type")!=null)
				if(resNode.getString("xsi_type").length()>0)
					result.append(DomUtils.getJsonAttributeOutput("xsi_type",resNode.getString("xsi_type"))+", ");

			//if(resNodes.getString("format")!=null)
			//	if(resNodes.getString("format").length()>0)
			result.append(DomUtils.getJsonAttributeOutput("format",resNode.getString("format"))+", ");

			//if(resNodes.getTimestamp("modif_date")!=null)
			result.append(DomUtils.getJsonAttributeOutput("modified",resNode.getTimestamp("modif_date").toGMTString())+", ");

			//result.append(">");

			if(!resNode.getString("asm_type").equals("asmResource"))
			{
				//obsolete
				//result.append(DomUtils.getJsonElementOutput("code", resNode.getString("code"))+", ");
				//result.append(DomUtils.getJsonElementOutput("label", resNode.getString("label"))+", ");
				//result.append(DomUtils.getJsonElementOutput("description", resNode.getString("descr"))+" ");
			}
			else
			{
				// si asmResource
				try
				{
					resResource = getMysqlResource(nodeUuid);
					if(resResource.next())
						result.append("\"#cdata-section\": \""+JSONObject.escape(resResource.getString("content"))+"\"");
				}
				catch(Exception ex)
				{
					ex.printStackTrace();
				}
			}

			if(withChildren || withChildrenOfXsiType!=null)
			{
				String[] arrayChild;
				try
				{
					if(resNode.getString("node_children_uuid").length()>0)
					{
						result.append(", ");
						arrayChild = resNode.getString("node_children_uuid").split(",");
						for(int i =0;i<(arrayChild.length);i++)
						{
							ResultSet resChildNode = this.getMysqlNodeResultset(arrayChild[i]);
							String tmpXsiType = "";
							try
							{
								tmpXsiType = resChildNode.getString("xsi_type");
							}
							catch(Exception ex)
							{

							}
							if(withChildrenOfXsiType==null || withChildrenOfXsiType.equals(tmpXsiType))
								result.append(getNodeJsonOutput(arrayChild[i],true,null,userId,groupId,label,true));

							if(withChildrenOfXsiType==null)
								if(arrayChild.length>1)
									if(i<(arrayChild.length-1)) result.append(", ");
						}
					}
				}
				catch(Exception ex)
				{
					// Pas de children
				}
			}

			result.append(" } ");
		}
		//}
		//catch(Exception ex)
		//{
		//	ex.printStackTrace();
		//
		//}
		return result;
	}

	private StringBuffer getNodeXmlOutput(String nodeUuid,boolean withChildren, String withChildrenOfXsiType, int userId,int groupId, String label,boolean checkSecurity) throws SQLException
	{
		StringBuffer result = new StringBuffer();
		// Verification securitï¿½
		if(checkSecurity)
		{
			NodeRight nodeRight = credential.getNodeRight(userId,groupId,nodeUuid, label);
			//
			if(!nodeRight.read)
				return result;
		}

		ResultSet resNode = getMysqlNode(nodeUuid,userId, groupId);
		ResultSet resResource;

		String indentation = " ";

		//try
		//{
		//			resNode.next();

		if(resNode.next())
		{
			if(resNode.getString("shared_node_uuid")!=null)
			{
				result.append(getNodeXmlOutput(resNode.getString("shared_node_uuid"),true,null,userId,groupId, null,true));
			}
			else
			{
				result.append(indentation+"<"+resNode.getString("asm_type")+" "+DomUtils.getXmlAttributeOutput("id",resNode.getString("node_uuid"))+" ");
				//if(resNodes.getString("node_parent_uuid").length()>0)
				//	result.append(getXmlAttributeOutput("parent-uuid",resNodes.getString("node_parent_uuid"))+" ");
				//		result.append(DomUtils.getXmlAttributeOutput("semantictag",resNode.getString("semtag"))+" ");
				//					String readRight= (nodeRight.read) ? "Y" : "N";
				//					String writeRight= (nodeRight.write) ? "Y" : "N";
				//					String submitRight= (nodeRight.submit) ? "Y" : "N";
				//					String deleteRight= (nodeRight.delete) ? "Y" : "N";


				//					result.append(DomUtils.getXmlAttributeOutput("read",readRight)+" ");
				//					result.append(DomUtils.getXmlAttributeOutput("write",writeRight)+" ");
				//					result.append(DomUtils.getXmlAttributeOutput("submit",submitRight)+" ");
				//					result.append(DomUtils.getXmlAttributeOutput("delete",deleteRight)+" ");
				//
				//
				//							result.append(DomUtils.getXmlAttributeOutput("xsi_type",resNode.getString("xsi_type"))+" ");

				//		result.append(DomUtils.getXmlAttributeOutput("format",resNode.getString("format"))+" ");

				//		result.append(DomUtils.getXmlAttributeOutput("modified",resNode.getTimestamp("modif_date").toGMTString())+" ");

				result.append(">");

				if(!resNode.getString("asm_type").equals("asmResource"))
				{
					DocumentBuilderFactory newInstance = DocumentBuilderFactory.newInstance();
					DocumentBuilder builder;
					Document document = null;
					try
					{
						builder = newInstance.newDocumentBuilder();
						document = builder.newDocument();
					}
					catch( ParserConfigurationException e )
					{
						e.printStackTrace();
					}

//					if(!resNode.getString("metadata_wad").equals("") )
					if(resNode.getString("metadata_wad")!=null && !resNode.getString("metadata_wad").equals("") )
					{
						Element meta = document.createElement("metadata-wad");
						convertAttr(meta, resNode.getString("metadata_wad"));

						TransformerFactory transFactory = TransformerFactory.newInstance();
						Transformer transformer;
						try
						{
							transformer = transFactory.newTransformer();
							StringWriter buffer = new StringWriter();
							transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
							transformer.transform(new DOMSource(meta), new StreamResult(buffer));
							result.append(buffer.toString());
						}
						catch( Exception e )
						{
							e.printStackTrace();
						}
					}
					else
						result.append("<metadata-wad/>");

//					if(!resNode.getString("metadata_epm").equals("") )
					if(resNode.getString("metadata_epm")!=null && !resNode.getString("metadata_epm").equals("") )
						result.append("<metadata-epm "+resNode.getString("metadata_epm")+"/>");
					else
						result.append("<metadata-epm/>");

//					if(!resNode.getString("metadata").equals("") )
					if(resNode.getString("metadata")!=null && !resNode.getString("metadata").equals("") )
						result.append("<metadata "+resNode.getString("metadata")+"/>");
					else
						result.append("<metadata/>");

					//
					result.append(DomUtils.getXmlElementOutput("code", resNode.getString("code")));
					result.append(DomUtils.getXmlElementOutput("label", resNode.getString("label")));
					result.append(DomUtils.getXmlElementOutput("description", resNode.getString("descr")));
					try
					{
						result.append(DomUtils.getXmlElementOutput("semanticTag", resNode.getString("semantictag")));
					}
					catch(Exception ex)
					{
						result.append(DomUtils.getXmlElementOutput("semanticTag", ""));
					}
				}
				else
				{
					/*// si asmResource
						try
						{
							resResource = getMysqlResource(nodeUuid);
							if(resResource.next())
								result.append(resResource.getString("content"));
						}
						catch(Exception ex)
						{
							ex.printStackTrace();
						}*/

				}

				if(resNode.getString("res_res_node_uuid")!=null)
					if(resNode.getString("res_res_node_uuid").length()>0)
					{
						result.append("<asmResource id='"+resNode.getString("res_res_node_uuid")+"'  contextid='"+nodeUuid+"' xsi_type='nodeRes'>");
						resResource = getMysqlResource(resNode.getString("res_res_node_uuid"));
						if(resResource.next())
							result.append(resResource.getString("content"));
						result.append("</asmResource>");
						resResource.close();
					}
				if(resNode.getString("res_context_node_uuid")!=null)
					if(resNode.getString("res_context_node_uuid").length()>0)
					{
						result.append("<asmResource id='"+resNode.getString("res_context_node_uuid")+"' contextid='"+nodeUuid+"' xsi_type='context'>");
						resResource = getMysqlResource(resNode.getString("res_context_node_uuid"));
						if(resResource.next())
							result.append(resResource.getString("content"));
						result.append("</asmResource>");
						resResource.close();
					}
				if(resNode.getString("res_node_uuid")!=null)
					if(resNode.getString("res_node_uuid").length()>0)
					{
						resResource = getMysqlResource(resNode.getString("res_node_uuid"));
						if(resResource.next())
						{
							result.append("<asmResource id='"+resNode.getString("res_node_uuid")+"' contextid='"+nodeUuid+"' xsi_type='"+resResource.getString("xsi_type")+"'>");

							result.append(resResource.getString("content"));
							result.append("</asmResource>");
						}
						resResource.close();
					}

				if(withChildren || withChildrenOfXsiType!=null)
				{
					String[] arrayChild;
					try
					{
						if(resNode.getString("node_children_uuid").length()>0)
						{
							arrayChild = resNode.getString("node_children_uuid").split(",");
							for(int i =0;i<(arrayChild.length);i++)
							{
								ResultSet resChildNode = this.getMysqlNodeResultset(arrayChild[i]);

								String tmpXsiType = "";
								try
								{
									resChildNode.next();
									tmpXsiType = resChildNode.getString("xsi_type");
								}
								catch(Exception ex)
								{

								}
								if(withChildrenOfXsiType==null || withChildrenOfXsiType.equals(tmpXsiType))
									result.append(getNodeXmlOutput(arrayChild[i],true,null,userId,groupId, null,true));

								resChildNode.close();
							}
						}
					}
					catch(Exception ex)
					{
						// Pas de children
					}
				}

				result.append("</"+resNode.getString("asm_type")+">");
			}
		}

		resNode.close();
		//}
		//catch(Exception ex)
		//{
		//	ex.printStackTrace();
		//
		//}
		return result;
	}


	private void getLinearXml(String portfolioUuid, String rootuuid, Node portfolio, boolean withChildren, String withChildrenOfXsiType, int userId,int groupId) throws SQLException, SAXException, IOException, ParserConfigurationException
	{
		DocumentBuilderFactory newInstance = DocumentBuilderFactory.newInstance();
		DocumentBuilder parse=newInstance.newDocumentBuilder();

		/*
	  long time0 = 0;
      long time1 = 0;
      long time2 = 0;
      long time3 = 0;
      //*/

		//      time0 = System.currentTimeMillis();

		ResultSet resNode = getMysqlStructure(portfolioUuid,userId, groupId);

		//	  time1= System.currentTimeMillis();

		Document document=portfolio.getOwnerDocument();

		HashMap<String, Node> resolve = new HashMap<String, Node>();
		/// Node -> parent
		ArrayList<Object[]> entries = new ArrayList<Object[]>();

		processQuery(resNode, resolve, entries, document, parse);
		resNode.close();

		resNode = getSharedMysqlStructure(portfolioUuid,userId, groupId);
		if( resNode != null )
		{
			processQuery(resNode, resolve, entries, document, parse);
			resNode.close();
		}

		//	  time2 = System.currentTimeMillis();

		/// Reconstruct tree
		for( int i=0; i<entries.size(); ++i )
		{
			Object[] obj = entries.get(i);
			Node node = (Node) obj[0];
			String childsId = (String) obj[1];

			String[] tok = childsId.split(",");
			for( int j=0; j<tok.length; ++j )
			{
				String id = tok[j];
				Node child = resolve.get(id);
				if( child != null )
					node.appendChild(child);

			}
		}

		//	  time3 = System.currentTimeMillis();

		Node rootNode = resolve.get(rootuuid);
		portfolio.appendChild(rootNode);

		/*
      System.out.println("---- Portfolio ---");
	  System.out.println("Time query: "+(time1-time0));
      System.out.println("Parsing: "+(time2-time1));
      System.out.println("Reconstruction: "+(time3-time2));
      System.out.println("------------------");
      //*/
	}

	private void processQuery( ResultSet result, HashMap<String, Node> resolve, ArrayList<Object[]> entries, Document document, DocumentBuilder parse ) throws UnsupportedEncodingException, DOMException, SQLException, SAXException, IOException
	{
		if( result != null )
			while( result.next() )
			{
				String nodeUuid = result.getString("node_uuid");
				String childsId = result.getString("node_children_uuid");

				String type = result.getString("asm_type");

				String xsi_type = result.getString("xsi_type");
				if( null == xsi_type )
					xsi_type = "";

				String readRight= result.getInt("RD")==1 ? "Y" : "N";
				String writeRight= result.getInt("WR")==1 ? "Y" : "N";
				String submitRight= result.getInt("SB")==1 ? "Y" : "N";
				String deleteRight= result.getInt("DL")==1 ? "Y" : "N";
				String macro = result.getString("rules_id");
				if( macro != null )
					macro = "action=\""+macro+"\"";
				else
					macro = "";

				String attr = result.getString("metadata_wad");
				String metaFragwad;
//				if( !"".equals(attr) )  /// Attributes exists
		        if( attr!=null && !"".equals(attr) )  /// Attributes exists
				{
					metaFragwad = "<metadata-wad "+attr+"/>";
				}
				else
				{
					metaFragwad = "<metadata-wad />";
				}

				attr = result.getString("metadata_epm");
				String metaFragepm;
//				if( !"".equals(attr) )  /// Attributes exists
		        if( attr!=null && !"".equals(attr) )  /// Attributes exists
				{
					metaFragepm = "<metadata-epm "+attr+"/>";
				}
				else
				{
					metaFragepm = "<metadata-epm />";
				}

				attr = result.getString("metadata");
				String metaFrag;
//				if( !"".equals(attr) )  /// Attributes exists
		        if( attr!=null && !"".equals(attr) )  /// Attributes exists
				{
					metaFrag = "<metadata "+attr+"/>";
				}
				else
				{
					metaFrag = "<metadata />";
				}

				String res_res_node_uuid = result.getString("res_res_node_uuid");
				String res_res_node = "";
				if( res_res_node_uuid!=null && res_res_node_uuid.length()>0 )
				{
					String nodeContent = result.getString("r2_content");
					if( nodeContent != null )
					{
						res_res_node = "<asmResource contextid=\""+nodeUuid+"\" id=\""+res_res_node_uuid+"\" xsi_type=\"nodeRes\">"+nodeContent.trim()+"</asmResource>";
					}
				}

				String res_context_node_uuid = result.getString("res_context_node_uuid");
				String context_node = "";
				if( res_context_node_uuid!=null && res_context_node_uuid.length()>0 )
				{
					String nodeContent = result.getString("r3_content");
					if( nodeContent != null )
					{
						context_node = "<asmResource contextid=\""+nodeUuid+"\" id=\""+res_context_node_uuid+"\" xsi_type=\"context\">"+nodeContent.trim()+"</asmResource>";
					}
				}

				String res_node_uuid = result.getString("res_node_uuid");
				String specific_node = "";
				if( res_node_uuid!=null && res_node_uuid.length()>0 )
				{
					String nodeContent = result.getString("r1_content");
					if( nodeContent != null )
					{
						specific_node = "<asmResource contextid=\""+nodeUuid+"\" id=\""+res_node_uuid+"\" xsi_type=\""+result.getString("r1_type")+"\">"+nodeContent.trim()+"</asmResource>";
					}
				}

				String snode = "<"+type+" delete=\""+deleteRight+"\" id=\""+nodeUuid+"\" read=\""+readRight+"\" submit=\""+submitRight+"\" write=\""+writeRight+"\" xsi_type=\""+xsi_type+"\" " +macro +">"+
						metaFragwad+
						metaFragepm+
						metaFrag+
						res_res_node+
						context_node+
						specific_node
						+"</"+type+">";

				Document frag = parse.parse(new ByteArrayInputStream(snode.getBytes("UTF-8")));
				Element ressource = frag.getDocumentElement();
				document.adoptNode(ressource);

				Node node = ressource;

				/// Prepare data to reconstruct tree
				resolve.put(nodeUuid, node);
				if( !"".equals(childsId) && childsId != null )
				{
					Object[] obj = {node, childsId};
					entries.add(obj);
				}
			}
	}

	//// Pourquoi on a converti les " en ' en premier lieu?
	//// Avec de l'espoir on en aura plus besoin (meilleur performance)
	private void convertAttr( Element attributes, String att )
	{
		String nodeString = "<?xml version='1.0' encoding='UTF-8' standalone='no'?><transfer "+att+"/>";

		try
		{
			/// Ensure we can parse it correctly
			DocumentBuilder documentBuilder;
			DocumentBuilderFactory documentBuilderFactory =DocumentBuilderFactory.newInstance();
			documentBuilder = documentBuilderFactory.newDocumentBuilder();
			InputSource is = new InputSource(new StringReader(nodeString));
			Document doc = documentBuilder.parse(is);

			/// Transfer attributes
			Element attribNode = doc.getDocumentElement();
			NamedNodeMap attribMap = attribNode.getAttributes();

			for( int i=0; i<attribMap.getLength(); ++i )
			{
				Node singleatt = attribMap.item(i);
				String name = singleatt.getNodeName();
				String value = singleatt.getNodeValue();
				attributes.setAttribute(name, value);
			}
		}
		catch( Exception e )
		{
			e.printStackTrace();
		}
	}

	private ResultSet getMysqlStructure(String portfolioUuid, int userId,  int groupId) throws SQLException
	{
		PreparedStatement st;
		String sql;

		sql = "SELECT bin2uuid(n.node_uuid) AS node_uuid," +
				" node_children_uuid, " +
				" n.node_order," +
				" n.metadata, n.metadata_wad, n.metadata_epm," +
				" n.shared_node AS shared_node," +
				" bin2uuid(n.shared_node_res_uuid) AS shared_node_res_uuid," +
				" bin2uuid(n.res_node_uuid) AS res_node_uuid," +
				" r1.xsi_type AS r1_type, r1.content AS r1_content," +     // donnÃ©e res_node
				" bin2uuid(n.res_res_node_uuid) as res_res_node_uuid," +
				" r2.content AS r2_content," +     // donnÃ©e res_res_node
				" bin2uuid(n.res_context_node_uuid) as res_context_node_uuid," +
				" r3.content AS r3_content," +     // donnÃ©e res_context
				" n.asm_type, n.xsi_type," +
				" gr.RD, gr.WR, gr.SB, gr.DL, gr.types_id, gr.rules_id," +   // info sur les droits
				" bin2uuid(n.portfolio_id) AS portfolio_id" +
				" FROM node n" +
				" LEFT JOIN resource_table r1 ON n.res_node_uuid=r1.node_uuid" +         // RÃ©cupÃ©ration des donnÃ©es res_node
				" LEFT JOIN resource_table r2 ON n.res_res_node_uuid=r2.node_uuid" +     // RÃ©cupÃ©ration des donnÃ©es res_res_node
				" LEFT JOIN resource_table r3 ON n.res_context_node_uuid=r3.node_uuid" + // RÃ©cupÃ©ration des donnÃ©es res_context
//				" LEFT JOIN (group_rights gr, group_info gi, group_user gu)" +     // VÃ©rification des droits
				" LEFT JOIN group_rights gr CROSS JOIN group_info gi CROSS JOIN group_user gu" +     // VÃ©rification des droits
				" ON (n.node_uuid=gr.id AND gr.grid=gi.grid AND" +
				" gi.gid=gu.gid AND gu.userid=? AND gr.RD=1)" +   // On doit au moins avoir le droit de lecture
				" WHERE n.portfolio_id = uuid2bin(?)";

		st = connection.prepareStatement(sql);
		st.setInt(1, userId);
		st.setString(2, portfolioUuid);

		return st.executeQuery();
	}

	/// R�cup�re les noeuds partag�s d'un portfolio
	/// C'est s�par� car les noeud ne provenant pas d'un m�me portfolio, on ne peut pas les s�lectionner rapidement
	/// Autre possibilit� serait de garder ce m�me type de fonctionnement pour une s�lection par niveau d'un portfolio.
	/// TODO: A faire un 'benchmark' dessus
	private ResultSet getSharedMysqlStructure(String portfolioUuid, int userId,  int groupId) throws SQLException
	{
		PreparedStatement st = null;
		String sql;
		ResultSet res = null;

		try
		{
			/// Pour le filtrage de la structure
			sql = "CREATE TEMPORARY TABLE t_struc(" +
					"uuid binary(16) UNIQUE NOT NULL, " +
					"node_parent_uuid binary(16), " +
					"t_level INT) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
			if (dbserveur.equals("oracle")){
		          sql = "CREATE GLOBAL TEMPORARY TABLE t_struc(" +
		                  "uuid RAW(16) NOT NULL, " +
		                  "node_parent_uuid RAW(16), " +
		                  "t_level NUMBER(10,0)"+
		                  ",  CONSTRAINT t_struc_UK_uuid UNIQUE (uuid)) ON COMMIT PRESERVE ROWS";    	  
			}
			st = connection.prepareStatement(sql);
			st.execute();
			st.close();

			// En double car on ne peut pas faire d'update/select d'une m�me table temporaire
			sql = "CREATE TEMPORARY TABLE t_struc_2(" +
					"uuid binary(16) UNIQUE NOT NULL, " +
					"node_parent_uuid binary(16), " +
					"t_level INT) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
			if (dbserveur.equals("oracle")){
		        sql = "CREATE GLOBAL TEMPORARY TABLE t_struc_2(" +
		                "uuid RAW(16) NOT NULL, " +
		                "node_parent_uuid RAW(16), " +
		                "t_level NUMBER(10,0)"+
		                ",  CONSTRAINT t_struc_2_UK_uuid UNIQUE (uuid)) ON COMMIT PRESERVE ROWS";
			}
			st = connection.prepareStatement(sql);
			st.execute();
			st.close();

			/// Initialise la descente des noeuds partag�s
			sql = "INSERT INTO t_struc(uuid, node_parent_uuid, t_level) " +
					"SELECT n.shared_node_uuid, n.node_parent_uuid, 0 " +
					"FROM node n " +
					"WHERE n.portfolio_id=uuid2bin(?) AND shared_node=1";
			st = connection.prepareStatement(sql);
			st.setString(1, portfolioUuid);
			st.executeUpdate();
			st.close();

			/// On boucle, sera toujours <= � "nombre de noeud du portfolio"
			int level = 0;
			int added = 1;
			if (dbserveur.equals("mysql")){
		        sql = "INSERT IGNORE INTO t_struc_2(uuid, node_parent_uuid, t_level) ";
			} else if (dbserveur.equals("oracle")){
				sql = "INSERT /*+ ignore_row_on_dupkey_index(t_struc_2,t_struc_2_UK_uuid)*/ INTO t_struc_2(uuid, node_parent_uuid, t_level) ";
			}
			sql += "SELECT n.node_uuid, n.node_parent_uuid, ? " +
					"FROM node n WHERE n.node_parent_uuid IN (SELECT uuid FROM t_struc t " +
					"WHERE t.t_level=?)";

	        String sqlTemp=null;
			if (dbserveur.equals("mysql")){
				sqlTemp = "INSERT IGNORE INTO t_struc SELECT * FROM t_struc_2;";
			} else if (dbserveur.equals("oracle")){
				sqlTemp = "INSERT /*+ ignore_row_on_dupkey_index(t_struc,t_struc_UK_uuid)*/ INTO t_struc SELECT * FROM t_struc_2";
			}
	        PreparedStatement stTemp = connection.prepareStatement(sqlTemp);
			if (dbserveur.equals("oracle")){
				stTemp = connection.prepareStatement("INSERT /*+ ignore_row_on_dupkey_index(t_struc,t_struc_UK_uuid)*/ INTO t_struc SELECT * FROM t_struc_2");
			}

			st = connection.prepareStatement(sql);
			while( added != 0 )
			{
				st.setInt(1, level+1);
				st.setInt(2, level);
				st.executeUpdate();
				added = stTemp.executeUpdate();   // On s'arr�te quand rien � �t� ajout�
				level = level + 1;    // Prochaine �tape
			}
			st.close();
			stTemp.close();

			// S�lectionne les donn�es selon la filtration
			sql = "SELECT bin2uuid(n.node_uuid) AS node_uuid," +
					" node_children_uuid, " +
					" n.node_order," +
					" n.metadata, n.metadata_wad, n.metadata_epm," +
					" n.shared_node AS shared_node," +
					" bin2uuid(n.shared_node_res_uuid) AS shared_node_res_uuid," +
					" bin2uuid(n.res_node_uuid) AS res_node_uuid," +
					" r1.xsi_type AS r1_type, r1.content AS r1_content," +     // donnÃ©e res_node
					" bin2uuid(n.res_res_node_uuid) as res_res_node_uuid," +
					" r2.content AS r2_content," +     // donnÃ©e res_res_node
					" bin2uuid(n.res_context_node_uuid) as res_context_node_uuid," +
					" r3.content AS r3_content," +     // donnÃ©e res_context
					" n.asm_type, n.xsi_type," +
					" gr.RD, gr.WR, gr.SB, gr.DL, gr.types_id, gr.rules_id," +   // info sur les droits
					" bin2uuid(n.portfolio_id) AS portfolio_id" +
					" FROM node n" +
					" LEFT JOIN resource_table r1 ON n.res_node_uuid=r1.node_uuid" +         // RÃ©cupÃ©ration des donnÃ©es res_node
					" LEFT JOIN resource_table r2 ON n.res_res_node_uuid=r2.node_uuid" +     // RÃ©cupÃ©ration des donnÃ©es res_res_node
					" LEFT JOIN resource_table r3 ON n.res_context_node_uuid=r3.node_uuid" + // RÃ©cupÃ©ration des donnÃ©es res_context
//					" LEFT JOIN (group_rights gr, group_info gi, group_user gu)" +     // VÃ©rification des droits
					" LEFT JOIN group_rights gr CROSS JOIN group_info gi CROSS JOIN group_user gu" +     // VÃ©rification des droits
					" ON (n.node_uuid=gr.id AND gr.grid=gi.grid AND" +
					" gi.gid=gu.gid AND gu.userid=? AND gr.RD=1)" +   // On doit au moins avoir le droit de lecture
					" WHERE n.node_uuid IN (SELECT uuid FROM t_struc)";   // Selon note filtrage, prendre les noeud n�c�ssaire

			st = connection.prepareStatement(sql);
			st.setInt(1, userId);
			res = st.executeQuery();
		}
		catch( SQLException e )
		{
			e.printStackTrace();
		}
		finally
		{
			try
			{
				// Les 'pooled connection' ne se ferment pas vraiment. On nettoie manuellement les tables temporaires...
				if (dbserveur.equals("mysql")){
					sql = "DROP TEMPORARY TABLE IF EXISTS t_struc, t_struc_2";
					st = connection.prepareStatement(sql);
					st.execute();
					st.close();
				} else if (dbserveur.equals("oracle")){
		        	sql = "{call drop_tables(tmpTableList('t_struc', 't_struc_2'))}";
		            CallableStatement ocs = connection.prepareCall(sql) ;
		            ocs.execute(); 
		            ocs.close();
				}
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}

		return res;
	}

	/// TODO: A faire un 'benchmark' dessus
	/// R�cup�re les noeuds en dessous par niveau. Pour faciliter le traitement des shared_node
	/// Mais �a serait beaucoup plus simple de faire un objet a traiter dans le client
	private ResultSet getNodePerLevel(String nodeUuid, int userId,  int groupId) throws SQLException
	{
		PreparedStatement st = null;
		String sql;
		ResultSet res = null;

		try
		{
			/// Pour le filtrage de la structure
			sql = "CREATE TEMPORARY TABLE t_struc(" +
					"uuid binary(16) UNIQUE NOT NULL, " +
					"node_parent_uuid binary(16), " +
					"t_level INT) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
			if (dbserveur.equals("oracle")){
		          sql = "CREATE GLOBAL TEMPORARY TABLE t_struc(" +
		                  "uuid RAW(16) NOT NULL, " +
		                  "node_parent_uuid RAW(16), " +
		                  "t_level NUMBER(10,0)"+
		                  ",  CONSTRAINT t_struc_UK_uuid UNIQUE (uuid)) ON COMMIT PRESERVE ROWS";
			}
			st = connection.prepareStatement(sql);
			st.execute();
			st.close();

			// En double car on ne peut pas faire d'update/select d'une m�me table temporaire
			sql = "CREATE TEMPORARY TABLE t_struc_2(" +
					"uuid binary(16) UNIQUE NOT NULL, " +
					"node_parent_uuid binary(16), " +
					"t_level INT) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
			if (dbserveur.equals("oracle")){
		        sql = "CREATE GLOBAL TEMPORARY TABLE t_struc_2(" +
		                "uuid RAW(16) NOT NULL, " +
		                "node_parent_uuid RAW(16), " +
		                "t_level NUMBER(10,0)"+
		                ",  CONSTRAINT t_struc_2_UK_uuid UNIQUE (uuid)) ON COMMIT PRESERVE ROWS";
			}
			st = connection.prepareStatement(sql);
			st.execute();
			st.close();

			/// Initialise la descente des noeuds, si il y a un partag� on partira de l�, sinon du noeud par d�faut
			sql = "INSERT INTO t_struc(uuid, node_parent_uuid, t_level) " +
					"SELECT COALESCE(n.shared_node_uuid, n.node_uuid), n.node_parent_uuid, 0 " +
					"FROM node n " +
					"WHERE n.node_uuid=uuid2bin(?)";
			st = connection.prepareStatement(sql);
			st.setString(1, nodeUuid);
			st.executeUpdate();
			st.close();

			/// On boucle, avec les shared_node si ils existent.
			/// FIXME: Possiblit� de boucle infini
			int level = 0;
			int added = 1;
			if (dbserveur.equals("mysql")){
		        sql = "INSERT IGNORE INTO t_struc_2(uuid, node_parent_uuid, t_level) ";
			} else if (dbserveur.equals("oracle")){
				sql = "INSERT /*+ ignore_row_on_dupkey_index(t_struc_2,t_struc_2_UK_uuid)*/ INTO t_struc_2(uuid, node_parent_uuid, t_level) ";
			}
			sql += "SELECT COALESCE(n.shared_node_uuid, n.node_uuid), n.node_parent_uuid, ? " +
					"FROM node n WHERE n.node_parent_uuid IN (SELECT uuid FROM t_struc t " +
					"WHERE t.t_level=?)";

	        String sqlTemp=null;
			if (dbserveur.equals("mysql")){
				sqlTemp = "INSERT IGNORE INTO t_struc SELECT * FROM t_struc_2;";
			} else if (dbserveur.equals("oracle")){
				sqlTemp = "INSERT /*+ ignore_row_on_dupkey_index(t_struc,t_struc_UK_uuid)*/ INTO t_struc SELECT * FROM t_struc_2";
			}
	        PreparedStatement stTemp = connection.prepareStatement(sqlTemp);

			st = connection.prepareStatement(sql);
			while( added != 0 )
			{
				st.setInt(1, level+1);
				st.setInt(2, level);
				st.executeUpdate();
				added = stTemp.executeUpdate();   // On s'arr�te quand rien � �t� ajout�
				level = level + 1;    // Prochaine �tape
			}
			st.close();
			stTemp.close();

			// S�lectionne les donn�es selon la filtration
			sql = "SELECT bin2uuid(n.node_uuid) AS node_uuid," +
					" node_children_uuid, " +
					" n.node_order," +
					" n.metadata, n.metadata_wad, n.metadata_epm," +
					" n.shared_node AS shared_node," +
					" bin2uuid(n.shared_node_res_uuid) AS shared_node_res_uuid," +
					" bin2uuid(n.res_node_uuid) AS res_node_uuid," +
					" r1.xsi_type AS r1_type, r1.content AS r1_content," +     // donnÃ©e res_node
					" bin2uuid(n.res_res_node_uuid) as res_res_node_uuid," +
					" r2.content AS r2_content," +     // donnÃ©e res_res_node
					" bin2uuid(n.res_context_node_uuid) as res_context_node_uuid," +
					" r3.content AS r3_content," +     // donnÃ©e res_context
					" n.asm_type, n.xsi_type," +
					" gr.RD, gr.WR, gr.SB, gr.DL, gr.types_id, gr.rules_id," +   // info sur les droits
					" bin2uuid(n.portfolio_id) AS portfolio_id" +
					" FROM node n" +
					" LEFT JOIN resource_table r1 ON n.res_node_uuid=r1.node_uuid" +         // RÃ©cupÃ©ration des donnÃ©es res_node
					" LEFT JOIN resource_table r2 ON n.res_res_node_uuid=r2.node_uuid" +     // RÃ©cupÃ©ration des donnÃ©es res_res_node
					" LEFT JOIN resource_table r3 ON n.res_context_node_uuid=r3.node_uuid" + // RÃ©cupÃ©ration des donnÃ©es res_context
//					" LEFT JOIN (group_rights gr, group_info gi, group_user gu)" +     // VÃ©rification des droits
					" LEFT JOIN group_rights gr CROSS JOIN group_info gi CROSS JOIN group_user gu" +     // VÃ©rification des droits
					" ON (n.node_uuid=gr.id AND gr.grid=gi.grid AND" +
					" gi.gid=gu.gid AND gu.userid=? AND gr.RD=1)" +   // On doit au moins avoir le droit de lecture
					" WHERE n.node_uuid IN (SELECT uuid FROM t_struc)";   // Selon note filtrage, prendre les noeud n�c�ssaire

			st = connection.prepareStatement(sql);
			st.setInt(1, userId);
			res = st.executeQuery();
		}
		catch( SQLException e )
		{
			e.printStackTrace();
		}
		finally
		{
			try
			{
				// Les 'pooled connection' ne se ferment pas vraiment. On nettoie manuellement les tables temporaires...
				if (dbserveur.equals("mysql")){
					sql = "DROP TEMPORARY TABLE IF EXISTS t_struc, t_struc_2";
					st = connection.prepareStatement(sql);
					st.execute();
					st.close();
				} else if (dbserveur.equals("oracle")){
		        	sql = "{call drop_tables(tmpTableList('t_struc', 't_struc_2'))}";
		            CallableStatement ocs = connection.prepareCall(sql) ;
		            ocs.execute(); 
		            ocs.close();
				}
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}

		return res;
	}

	private  StringBuffer getNodeXmlListOutput(String nodeUuid,boolean withChildren,int userId, int groupId) throws SQLException
	{
		StringBuffer result = new StringBuffer();

		if(!credential.hasNodeRight(userId,groupId,nodeUuid, credential.READ))
			return result;

		ResultSet resNode = getMysqlNode(nodeUuid,userId, groupId);
		ResultSet resResource;

		String indentation = "";

		try
		{
			//			resNode.next();
			if(resNode.next())
			{
				result.append(indentation+"<"+resNode.getString("asm_type")+" "+DomUtils.getXmlAttributeOutput("id",resNode.getString("node_uuid"))+" ");
				//if(resNodes.getString("node_parent_uuid").length()>0)
				//	result.append(getXmlAttributeOutput("parent-uuid",resNodes.getString("node_parent_uuid"))+" ");
				//if(resNodes.getString("semtag")!=null)
				//if(resNodes.getString("semtag").length()>0)
				result.append(DomUtils.getXmlAttributeOutput("semantictag",resNode.getString("semtag"))+" ");

				if(resNode.getString("xsi_type")!=null)
					if(resNode.getString("xsi_type").length()>0)
						result.append(DomUtils.getXmlAttributeOutput("xsi_type",resNode.getString("xsi_type"))+" ");

				//if(resNodes.getString("format")!=null)
				//	if(resNodes.getString("format").length()>0)
				result.append(DomUtils.getXmlAttributeOutput("format",resNode.getString("format"))+" ");

				//if(resNodes.getTimestamp("modif_date")!=null)
				result.append(DomUtils.getXmlAttributeOutput("modified",resNode.getTimestamp("modif_date").toGMTString())+" ");


				result.append("/>");

				if(!resNode.getString("asm_type").equals("asmResource"))
				{
					//result.append(DomUtils.getXmlElementOutput("code", resNode.getString("code")));
					//result.append(DomUtils.getXmlElementOutput("label", resNode.getString("label")));
					//result.append(DomUtils.getXmlElementOutput("description", resNode.getString("descr")));
				}
				else
				{
					// si asmResource
					try
					{
						//resResource = getMysqlResource(nodeUuid);
						//resResource.next();
						//result.append(resResource.getString("content"));
					}
					catch(Exception ex)
					{
						ex.printStackTrace();
					}
				}

				if(withChildren)
				{
					String[] arrayChild;
					try
					{
						if(resNode.getString("node_children_uuid").length()>0)
						{
							arrayChild = resNode.getString("node_children_uuid").split(",");
							for(int i =0;i<(arrayChild.length);i++)
							{
								result.append(getNodeXmlListOutput(arrayChild[i],true, userId, groupId));
							}
						}
					}
					catch(Exception ex)
					{
						// Pas de children
					}
				}

				//result.append("</"+resNode.getString("asm_type")+">");



			}
		}
		catch(Exception ex)
		{
			ex.printStackTrace();

		}
		return result;
	}

	@Override
	public Object getNode(MimeType outMimeType, String nodeUuid,boolean withChildren, int userId,int groupId, String label) throws SQLException, TransformerFactoryConfigurationError, ParserConfigurationException, UnsupportedEncodingException, DOMException, SAXException, IOException, TransformerException
	{
		StringBuffer nodexml = new StringBuffer();

		NodeRight nodeRight = credential.getNodeRight(userId,groupId,nodeUuid, label);

		if(!nodeRight.read)
			return nodexml;

		if(outMimeType.getSubType().equals("xml"))
		{
			ResultSet result = getNodePerLevel(nodeUuid, userId, groupId);

			/// Pr�paration du XML que l'on va renvoyer
			DocumentBuilderFactory documentBuilderFactory =DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder = null;
			Document document=null;
			documentBuilder = documentBuilderFactory.newDocumentBuilder();
			document = documentBuilder.newDocument();
			document.setXmlStandalone(true);

			HashMap<String, Node> resolve = new HashMap<String, Node>();
			/// Node -> parent
			ArrayList<Object[]> entries = new ArrayList<Object[]>();

			processQuery(result, resolve, entries, document, documentBuilder);
			result.close();

			for( int i=0; i<entries.size(); ++i )
			{
				Object[] obj = entries.get(i);
				Node node = (Node) obj[0];
				String childsId = (String) obj[1];

				String[] tok = childsId.split(",");
				for( int j=0; j<tok.length; ++j )
				{
					String id = tok[j];
					Node child = resolve.get(id);
					if( child != null )
						node.appendChild(child);
				}
			}

			Node root = resolve.get(nodeUuid);
			Node node = document.createElement("node");
			node.appendChild(root);
			document.appendChild(node);

			StringWriter stw = new StringWriter();
			Transformer serializer = TransformerFactory.newInstance().newTransformer();
			serializer.transform(new DOMSource(document), new StreamResult(stw));
			nodexml.append(stw.toString());

			//		  StringBuffer sb = getNodeXmlOutput(nodeUuid,withChildren,null,userId, groupId, label,true);
			//          StringBuffer sb = getLinearNodeXml(nodeUuid,withChildren,null,userId, groupId, label,true);

			//		  sb.insert(0, "<node>");
			//		  sb.append("</node>");
			return nodexml;
		}
		else if(outMimeType.getSubType().equals("json"))
			return "{"+getNodeJsonOutput(nodeUuid,withChildren,null,userId, groupId,label,true)+"}";
		else
			return null;
	}

	@Override
	public Object deleteNode(String nodeUuid, int userId, int groupId) throws Exception
	{
		NodeRight nodeRight = credential.getNodeRight(userId,groupId,nodeUuid, credential.DELETE);

		if(!nodeRight.delete)
			if(!credential.isAdmin(userId))
				throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		Integer result = 0;
		ResultSet resNode = getMysqlNode(nodeUuid, userId, groupId);
		ResultSet resResource;

		String indentation = " ";

		System.out.println("deleteNode :"+nodeUuid);

		/*if(resNode == null){

				System.out.print("lallalaï¿½");

			}*/

		//try
		//{

		if(resNode.next())
		{
			//					resNode.next();

			result = deleteMySqlNode(resNode.getString("node_uuid"),resNode.getString("node_parent_uuid"), userId, groupId);

			if(!resNode.getString("asm_type").equals("asmResource"))
			{
				//
			}
			else
			{
				// si asmResource
				try
				{
					resResource = getMysqlResource(nodeUuid);
					resResource.next();
					//TODO Ressource delete
				}
				catch(Exception ex)
				{
					ex.printStackTrace();
				}
			}


			String[] arrayChild;
			try
			{
				if(resNode.getString("node_children_uuid").length()>0)
				{
					arrayChild = resNode.getString("node_children_uuid").split(",");
					for(int i =0;i<(arrayChild.length);i++)
					{
						deleteNode(arrayChild[i],userId, groupId);
					}
				}
			}
			catch(Exception ex)
			{
				// Pas de children
			}
		}
		else {return result;}

		return result;
	}

	@Override
	public Object postInstanciateNode( MimeType inMimeType, String portfolioUuid, String newCode, int userId, int groupId, boolean copyshared ) throws Exception
	{
		String sql = "";
		PreparedStatement st;
		String newPortfolioUuid = UUID.randomUUID().toString();

		try
		{
			///// Cr�ation des tables temporaires
			/// Pour la copie de la structure
			sql = "CREATE TEMPORARY TABLE t_data(" +
					"new_uuid binary(16) NOT NULL, " +  /// Pour la copie d'une nouvelle structure
					"node_uuid binary(16)  NOT NULL, " +
					"node_parent_uuid binary(16) DEFAULT NULL, " +
					"node_children_uuid blob, " +
					"node_order int(12) NOT NULL, " +
					"metadata text NOT NULL, " +
					"metadata_wad text NOT NULL, " +
					"metadata_epm text NOT NULL, " +
					"res_node_uuid binary(16) DEFAULT NULL, " +
					"res_res_node_uuid binary(16) DEFAULT NULL, " +
					"res_context_node_uuid binary(16)  DEFAULT NULL, " +
					"shared_res int(1) NOT NULL, " +
					"shared_node int(1) NOT NULL, " +
					"shared_node_res int(1) NOT NULL, " +
					"shared_res_uuid BINARY(16)  NULL, " +
					"shared_node_uuid BINARY(16) NULL, " +
					"shared_node_res_uuid BINARY(16) NULL, " +
					"asm_type varchar(50) DEFAULT NULL, " +
					"xsi_type varchar(50)  DEFAULT NULL, " +
					"semtag varchar(250) DEFAULT NULL, " +
					"semantictag varchar(250) DEFAULT NULL, " +
					"label varchar(250)  DEFAULT NULL, " +
					"code varchar(45)  DEFAULT NULL, " +
					"descr varchar(250)  DEFAULT NULL, " +
					"format varchar(30) DEFAULT NULL, " +
					"modif_user_id int(12) NOT NULL, " +
					"modif_date timestamp NULL DEFAULT NULL, " +
					"portfolio_id binary(16) DEFAULT NULL) DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
			if (dbserveur.equals("oracle")){
		          sql = "CREATE GLOBAL TEMPORARY TABLE t_data(" +
		                  "new_uuid RAW(16) NOT NULL, " +  /// Pour la copie d'une nouvelle structure
		                  "node_uuid RAW(16)  NOT NULL, " +
		                  "node_parent_uuid RAW(16) DEFAULT NULL, " +
		                  "node_children_uuid blob, " +
		                  "node_order NUMBER(12) NOT NULL, " +
		                  "metadata CLOB NOT NULL, " +
		                  "metadata_wad CLOB NOT NULL, " +
		                  "metadata_epm CLOB NOT NULL, " +
		                  "res_node_uuid RAW(16) DEFAULT NULL, " +
		                  "res_res_node_uuid RAW(16) DEFAULT NULL, " +
		                  "res_context_node_uuid RAW(16)  DEFAULT NULL, " +
		                  "shared_res NUMBER(1) NOT NULL, " +
		                  "shared_node NUMBER(1) NOT NULL, " +
		                  "shared_node_res NUMBER(1) NOT NULL, " +
		                  "shared_res_uuid RAW(16)  NULL, " +
		                  "shared_node_uuid RAW(16) NULL, " +
		                  "shared_node_res_uuid RAW(16) NULL, " +
		                  "asm_type VARCHAR2(50 CHAR) DEFAULT NULL, " +
		                  "xsi_type VARCHAR2(50 CHAR)  DEFAULT NULL, " +
		                  "semtag VARCHAR2(250 CHAR) DEFAULT NULL, " +
		                  "semantictag VARCHAR2(250 CHAR) DEFAULT NULL, " +
		                  "label VARCHAR2(250 CHAR)  DEFAULT NULL, " +
		                  "code VARCHAR2(45 CHAR)  DEFAULT NULL, " +
		                  "descr VARCHAR2(250 CHAR)  DEFAULT NULL, " +
		                  "format VARCHAR2(30 CHAR) DEFAULT NULL, " +
		                  "modif_user_id NUMBER(12) NOT NULL, " +
		                  "modif_date timestamp DEFAULT NULL, " +
		                  "portfolio_id RAW(16) DEFAULT NULL) ON COMMIT PRESERVE ROWS";
			}
			st = connection.prepareStatement(sql);
			st.execute();
			st.close();

			/// Pour la copie des donn�es
			sql = "CREATE TEMPORARY TABLE t_res(" +
					"new_uuid binary(16) NOT NULL, " +  /// Pour la copie d'une nouvelle structure
					"node_uuid binary(16) NOT NULL, " +
					"xsi_type varchar(50) NOT NULL, " +
					"content text, " +
					"user_id int(11) DEFAULT NULL, " +
					"modif_user_id int(12) NOT NULL, " +
					"modif_date timestamp NULL DEFAULT NULL) DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
			if (dbserveur.equals("oracle")){
		        sql = "CREATE GLOBAL TEMPORARY TABLE t_res(" +
		                "new_uuid RAW(16) NOT NULL, " +  /// Pour la copie d'une nouvelle structure
		                "node_uuid RAW(16) NOT NULL, " +
		                "xsi_type VARCHAR2(50 CHAR) NOT NULL, " +
		                "content CLOB, " +
		                "user_id NUMBER(11) DEFAULT NULL, " +
		                "modif_user_id NUMBER(12) NOT NULL, " +
		                "modif_date timestamp DEFAULT NULL) ON COMMIT PRESERVE ROWS";
			}
			st = connection.prepareStatement(sql);
			st.execute();
			st.close();

			/// Pour la mise � jour de la liste des enfants/parents
			sql = "CREATE TEMPORARY TABLE t_struc(" +
					"node_order int(12) NOT NULL, " +
					"new_uuid binary(16) NOT NULL, " +
					"uuid binary(16) UNIQUE NOT NULL, " +
					"node_parent_uuid binary(16), " +
					"t_level INT) DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
			if (dbserveur.equals("oracle")){
		        sql = "CREATE GLOBAL TEMPORARY TABLE t_struc(" +
		                "node_order NUMBER(12) NOT NULL, " +
		                "new_uuid RAW(16) NOT NULL, " +
		                "uuid RAW(16) NOT NULL, " +
		                "node_parent_uuid RAW(16), " +
		                "t_level NUMBER(10,0)"+
		                ",  CONSTRAINT t_struc_UK_uuid UNIQUE (uuid)) ON COMMIT PRESERVE ROWS";
			}
			st = connection.prepareStatement(sql);
			st.execute();
			st.close();

			/// Pour l'histoire des shared_node a filtrer
			sql = "CREATE TEMPORARY TABLE t_struc_2(" +
					"node_order int(12) NOT NULL, " +
					"new_uuid binary(16) NOT NULL, " +
					"uuid binary(16) UNIQUE NOT NULL, " +
					"node_parent_uuid binary(16), " +
					"t_level INT) DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
			if (dbserveur.equals("oracle")){
		        sql = "CREATE GLOBAL TEMPORARY TABLE t_struc_2(" +
		                "node_order NUMBER(12) NOT NULL, " +
		                "new_uuid RAW(16) NOT NULL, " +
		                "uuid RAW(16) NOT NULL, " +
		                "node_parent_uuid RAW(16), " +
		                "t_level NUMBER(10,0)"+
		                ",  CONSTRAINT t_struc_2_UK_uuid UNIQUE (uuid)) ON COMMIT PRESERVE ROWS";
			}
			st = connection.prepareStatement(sql);
			st.execute();
			st.close();

			/// Pour les nouveaux ensembles de droits
			sql = "CREATE TEMPORARY TABLE t_rights(" +
					"grid BIGINT NOT NULL, " +
					"id binary(16) NOT NULL, " +
					"RD BOOL NOT NULL, " +
					"WR BOOL NOT NULL, " +
					"DL BOOL NOT NULL, " +
					"SB BOOL NOT NULL, " +
					"AD BOOL NOT NULL, " +
					"types_id TEXT, " +
					"rules_id TEXT) DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
			if (dbserveur.equals("oracle")){
		        sql = "CREATE GLOBAL TEMPORARY TABLE t_rights(" +
		                "grid NUMBER(19,0) NOT NULL, " +
		                "id RAW(16) NOT NULL, " +
		                "RD NUMBER(1) NOT NULL, " +
		                "WR NUMBER(1) NOT NULL, " +
		                "DL NUMBER(1) NOT NULL, " +
		                "SB NUMBER(1) NOT NULL, " +
		                "AD NUMBER(1) NOT NULL, " +
		                "types_id VARCHAR2(2000 CHAR), " +
		                "rules_id VARCHAR2(2000 CHAR)) ON COMMIT PRESERVE ROWS";
			}
			st = connection.prepareStatement(sql);
			st.execute();
			st.close();

			/// Copie de la structure
			sql = "INSERT INTO t_data(new_uuid, node_uuid, node_parent_uuid, node_children_uuid, node_order, metadata, metadata_wad, metadata_epm, res_node_uuid, res_res_node_uuid, res_context_node_uuid , shared_res, shared_node, shared_node_res, shared_res_uuid, shared_node_uuid, shared_node_res_uuid, asm_type, xsi_type, semtag, semantictag, label, code, descr, format, modif_user_id, modif_date, portfolio_id) ";
			if (dbserveur.equals("mysql")){
				sql += "SELECT uuid2bin(UUID()), ";
			} else if (dbserveur.equals("oracle")){
				sql += "SELECT sys_guid(), ";
			}	
			sql += "node_uuid, node_parent_uuid, node_children_uuid, node_order, metadata, metadata_wad, metadata_epm, res_node_uuid, res_res_node_uuid, res_context_node_uuid , shared_res, shared_node, shared_node_res, shared_res_uuid, shared_node_uuid, shared_node_res_uuid, asm_type, xsi_type, semtag, semantictag, label, code, descr, format, modif_user_id, modif_date, portfolio_id " +
					"FROM node n " +
					"WHERE portfolio_id=uuid2bin(?)";
			st = connection.prepareStatement(sql);
			st.setString(1, portfolioUuid);
			st.executeUpdate();
			st.close();

			if( !copyshared )
			{
				/// Liste les noeud a filtrer
				sql = "INSERT INTO t_struc(node_order, new_uuid, uuid, node_parent_uuid, t_level) " +
						"SELECT node_order, new_uuid, node_uuid, node_parent_uuid, 0 FROM t_data WHERE shared_node=1";
				st = connection.prepareStatement(sql);
				st.executeUpdate();
				st.close();

				int level = 0;
				int added = 1;
				if (dbserveur.equals("mysql")){
		        	sql = "INSERT IGNORE INTO t_struc_2(node_order, new_uuid, uuid, node_parent_uuid, t_level) ";
				} else if (dbserveur.equals("oracle")){
					sql = "INSERT /*+ ignore_row_on_dupkey_index(t_struc_2,t_struc_2_UK_uuid)*/ INTO t_struc_2(node_order, new_uuid, uuid, node_parent_uuid, t_level) ";
				}
				sql += "SELECT d.node_order, d.new_uuid, d.node_uuid, d.node_parent_uuid, ? " +
						"FROM t_data d WHERE d.node_parent_uuid IN (SELECT uuid FROM t_struc t " +
						"WHERE t.t_level=?)";

		        String sqlTemp=null;
				if (dbserveur.equals("mysql")){
					sqlTemp = "INSERT IGNORE INTO t_struc SELECT * FROM t_struc_2;";
				} else if (dbserveur.equals("oracle")){
					sqlTemp = "INSERT /*+ ignore_row_on_dupkey_index(t_struc,t_struc_UK_uuid)*/ INTO t_struc SELECT * FROM t_struc_2";
				}
		        PreparedStatement stTemp = connection.prepareStatement(sqlTemp);

				st = connection.prepareStatement(sql);
				while( added != 0 )
				{
					st.setInt(1, level+1);
					st.setInt(2, level);
					st.executeUpdate();
					added = stTemp.executeUpdate();   // On s'arr�te quand rien � �t� ajout�
					level = level + 1;    // Prochaine �tape
				}
				st.close();
				stTemp.close();

				// Retire les noeuds en dessous du shared
				// TODO Quand est-ce que je deviendrai un graph?
				sql = "DELETE FROM t_struc WHERE uuid IN (SELECT node_uuid FROM t_data WHERE shared_node=1)";
				st = connection.prepareStatement(sql);
				st.executeUpdate();
				st.close();

				sql = "DELETE FROM t_data WHERE node_uuid IN (SELECT uuid FROM t_struc)";
				st = connection.prepareStatement(sql);
				st.executeUpdate();
				st.close();

				sql = "DELETE FROM t_struc";
				st = connection.prepareStatement(sql);
				st.executeUpdate();
				st.close();

			}

			/// Copie les uuid pour la r�solution des parents/enfants
			sql = "INSERT INTO t_struc(node_order, new_uuid, uuid, node_parent_uuid) " +
					"SELECT node_order, new_uuid, node_uuid, node_parent_uuid FROM t_data";
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			if( !copyshared )
			{
				/// Cas sp�cial pour shared_node=1
				// Le temps qu'on refasse la liste des enfants, on va enlever le noeud plus tard
				sql = "UPDATE t_data SET shared_node_uuid=node_uuid WHERE shared_node=1";
				st = connection.prepareStatement(sql);
				st.executeUpdate();
				st.close();

				// Met a jour t_struc pour la redirection. C'est pour la list des enfants
				// FIXME: A v�rifier les appels qui modifie la liste des enfants.
				if (dbserveur.equals("mysql")){
					sql = "UPDATE t_struc s INNER JOIN t_data d ON s.uuid=d.node_uuid " +
						"SET s.new_uuid=d.node_uuid WHERE d.shared_node=1";
				} else if (dbserveur.equals("oracle")){
			          sql = "UPDATE t_struc s SET s.new_uuid=(SELECT d.node_uuid FROM t_struc s2 INNER JOIN t_data d ON s2.uuid=d.node_uuid WHERE d.shared_node=1) WHERE EXISTS (SELECT 1 FROM t_struc s2 INNER JOIN t_data d ON s2.uuid=d.node_uuid WHERE d.shared_node=1)";
				}
				st = connection.prepareStatement(sql);
				st.executeUpdate();
				st.close();
			}

			/// Copie des droits sur les noeuds et ressources
			sql = "INSERT INTO t_rights(grid, id, RD, WR, DL, SB, AD, types_id, rules_id) " +
					"SELECT gr.grid, gr.id, gr.RD, gr.WR, gr.DL, gr.SB, gr.AD, gr.types_id, gr.rules_id " +
					"FROM group_right_info gri INNER JOIN group_rights gr ON gri.grid=gr.grid " +
					"WHERE gri.portfolio_id=uuid2bin(?)";
			st = connection.prepareStatement(sql);
			st.setString(1, portfolioUuid);
			st.executeUpdate();
			st.close();

			/// Copie des donn�es non partag�s (shared=0)
			// Specific
			sql = "INSERT INTO t_res(new_uuid, node_uuid, xsi_type, content, user_id, modif_user_id, modif_date) ";
			if (dbserveur.equals("mysql")){
				sql += "SELECT uuid2bin(UUID()), ";
			} else if (dbserveur.equals("oracle")){
				sql += "SELECT sys_guid(), ";
			}	
			sql += "r.node_uuid, r.xsi_type, r.content, r.user_id, r.modif_user_id, r.modif_date " +
					"FROM t_data d " +
					"LEFT JOIN resource_table r ON d.res_node_uuid=r.node_uuid " +
					"WHERE ";
			if( !copyshared )
				sql += "shared_res=0 AND ";
			if (dbserveur.equals("mysql")){
				sql += "d.res_node_uuid <> 0x0000000000000000000000000000000"; // Binaire non null
			} else if (dbserveur.equals("oracle")){
				sql += "d.res_node_uuid <> '00000000000000000000000000000000'";
			}
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			sql = "INSERT INTO t_res(new_uuid, node_uuid, xsi_type, content, user_id, modif_user_id, modif_date) ";
			if (dbserveur.equals("mysql")){
				sql += "SELECT uuid2bin(UUID()), ";
			} else if (dbserveur.equals("oracle")){
				sql += "SELECT sys_guid(), ";
			}	
			sql += "r.node_uuid, r.xsi_type, r.content, r.user_id, r.modif_user_id, r.modif_date " +
					"FROM t_data d " +
					"LEFT JOIN resource_table r ON d.res_context_node_uuid=r.node_uuid " +
					"WHERE ";
			if( !copyshared )
				sql += "shared_node=0 AND ";
			if (dbserveur.equals("mysql")){
				sql += "d.res_context_node_uuid <> 0x0000000000000000000000000000000";
			} else if (dbserveur.equals("oracle")){
				sql += "d.res_context_node_uuid <> '00000000000000000000000000000000'";
			}
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			// nodeRes
			sql = "INSERT INTO t_res(new_uuid, node_uuid, xsi_type, content, user_id, modif_user_id, modif_date) ";
			if (dbserveur.equals("mysql")){
				sql += "SELECT uuid2bin(UUID()), ";
			} else if (dbserveur.equals("oracle")){
				sql += "SELECT sys_guid(), ";
			}	
			sql += "r.node_uuid, r.xsi_type, r.content, r.user_id, r.modif_user_id, r.modif_date " +
					"FROM t_data d " +
					"LEFT JOIN resource_table r ON d.res_res_node_uuid=r.node_uuid " +
					"WHERE ";
			if( !copyshared )
				sql += "shared_node_res=0 AND ";
			if (dbserveur.equals("mysql")){
				sql += "d.res_res_node_uuid <> 0x0000000000000000000000000000000";
			} else if (dbserveur.equals("oracle")){
				sql += "d.res_res_node_uuid <> '00000000000000000000000000000000'";
			}
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			/// Changement du uuid du portfolio
			sql = "UPDATE t_data t SET t.portfolio_id=uuid2bin(?)";
			st = connection.prepareStatement(sql);
			st.setString(1, newPortfolioUuid);
			st.executeUpdate();
			st.close();

			/// R�solution des nouveaux uuid avec les parents
			// Avec la structure (et droits sur la structure)
			if (dbserveur.equals("mysql")){
				sql = "UPDATE t_rights ri, t_data d SET ri.id=d.new_uuid WHERE ri.id=d.node_uuid AND d.shared_node=0";
			} else if (dbserveur.equals("oracle")){
		        sql = "UPDATE t_rights ri SET ri.id=(SELECT new_uuid FROM t_data d WHERE ri.id=d.node_uuid AND d.shared_node=0) WHERE EXISTS (SELECT 1 FROM t_data d WHERE ri.id=d.node_uuid AND d.shared_node=0)";
			}
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			sql = "UPDATE t_data t " +
					"SET t.node_parent_uuid = (SELECT new_uuid FROM t_struc s WHERE s.uuid=t.node_parent_uuid)";
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			// Avec les ressources (et droits des ressources)
			if (dbserveur.equals("mysql")){
				sql = "UPDATE t_rights ri, t_res re SET ri.id = re.new_uuid WHERE re.node_uuid=ri.id";
			} else if (dbserveur.equals("oracle")){
		        sql = "UPDATE t_rights ri SET ri.id=(SELECT new_uuid FROM t_res re WHERE re.node_uuid=ri.id) WHERE EXISTS (SELECT 1 FROM t_res re WHERE re.node_uuid=ri.id)";
			}
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			if (dbserveur.equals("mysql")){
				sql = "UPDATE t_data d INNER JOIN t_res r ON d.res_node_uuid=r.node_uuid " +
					"SET d.res_node_uuid=r.new_uuid";
			} else if (dbserveur.equals("oracle")){
			    sql = "UPDATE t_data d SET d.res_node_uuid=(SELECT r.new_uuid FROM t_data d2 INNER JOIN t_res r ON d2.res_node_uuid=r.node_uuid) WHERE EXISTS (SELECT 1 FROM t_data d2 INNER JOIN t_res r ON d2.res_node_uuid=r.node_uuid)";
			}
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			if (dbserveur.equals("mysql")){
				sql = "UPDATE t_data d INNER JOIN t_res r ON d.res_res_node_uuid=r.node_uuid " +
					"SET d.res_res_node_uuid=r.new_uuid";
			} else if (dbserveur.equals("oracle")){
		        sql = "UPDATE t_data d SET d.res_res_node_uuid=(SELECT r.new_uuid FROM t_data d2 INNER JOIN t_res r ON d2.res_res_node_uuid=r.node_uuid) WHERE EXISTS (SELECT 1 FROM t_data d2 INNER JOIN t_res r ON d2.res_res_node_uuid=r.node_uuid)";
			}
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			if (dbserveur.equals("mysql")){
				sql = "UPDATE t_data d INNER JOIN t_res r ON d.res_context_node_uuid=r.node_uuid " +
					"SET d.res_context_node_uuid=r.new_uuid";
			} else if (dbserveur.equals("oracle")){
		        sql = "UPDATE t_data d SET d.res_context_node_uuid=(SELECT r.new_uuid FROM t_data d2 INNER JOIN t_res r ON d2.res_context_node_uuid=r.node_uuid) WHERE EXISTS (SELECT 1 FROM t_data d2 INNER JOIN t_res r ON d2.res_context_node_uuid=r.node_uuid)";
			}
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			/// Mise � jour de la liste des enfants (! requ�te particuli�re)
			/// L'ordre d�termine le rendu visuel final du xml
			if (dbserveur.equals("mysql")){
				sql = "UPDATE t_data d, (" +
					"SELECT node_parent_uuid, GROUP_CONCAT(bin2uuid(s.new_uuid) ORDER BY s.node_order) AS value " +
					"FROM t_struc s GROUP BY s.node_parent_uuid) tmp " +
					"SET d.node_children_uuid=tmp.value " +
					"WHERE tmp.node_parent_uuid=d.node_uuid";
			} else if (dbserveur.equals("oracle")){
		        sql = "UPDATE t_data d SET d.node_children_uuid=(SELECT value FROM (SELECT node_parent_uuid, LISTAGG(bin2uuid(s.new_uuid), ',') WITHIN GROUP (ORDER BY s.node_order) AS value FROM t_struc s GROUP BY s.node_parent_uuid) tmp WHERE tmp.node_parent_uuid=d.node_uuid) WHERE EXISTS (SELECT 1 FROM t_struc WHERE node_parent_uuid=d.node_uuid)";
			}
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			// Mise � jour du code dans le contenu du noeud (blech)
			if (dbserveur.equals("mysql")){
				sql = "UPDATE t_data d " +
					"LEFT JOIN t_res r ON d.res_res_node_uuid=r.new_uuid " +  // Il faut utiliser le nouveau uuid
					"SET r.content=REPLACE(r.content, d.code, ?) " +
					"WHERE d.asm_type='asmRoot'";
			} else if (dbserveur.equals("oracle")){
		        sql = "UPDATE t_res r SET r.content=(SELECT REPLACE(r2.content, d.code, ?) FROM t_data d LEFT JOIN t_res r2 ON d.res_res_node_uuid=r2.new_uuid WHERE d.asm_type='asmRoot') WHERE EXISTS (SELECT 1 FROM t_data d WHERE d.res_res_node_uuid=r.new_uuid AND d.asm_type='asmRoot')";
			}
			st = connection.prepareStatement(sql);
			st.setString(1, newCode);
			st.executeUpdate();
			st.close();

			// Mise � jour du code dans le code interne de la BD
			sql = "UPDATE t_data d SET d.code=? WHERE d.asm_type='asmRoot'";
			st = connection.prepareStatement(sql);
			st.setString(1, newCode);
			st.executeUpdate();
			st.close();

			/// Cr�e les groupes de droits en les copiants dans la table d'origine
			// S�lectionne les groupes concern�s
			connection.setAutoCommit(false);
			sql = "SELECT grid FROM group_right_info " +
					"WHERE portfolio_id=uuid2bin(?)";
			st = connection.prepareStatement(sql);
			st.setString(1, portfolioUuid);
			ResultSet res = st.executeQuery();

			/// Pour chaque grid, on en cr�� un nouveau et met � jour nos nouveaux droits
			sql = "INSERT INTO group_right_info(owner, label, change_rights, portfolio_id) " +
					"SELECT owner, label, change_rights, uuid2bin(?) FROM group_right_info WHERE grid=?";
			st = connection.prepareStatement(sql);
			st.setString(1, newPortfolioUuid);

			if (dbserveur.equals("mysql")){
				sql = "UPDATE t_rights SET grid=LAST_INSERT_ID() WHERE grid=?";
			} else if (dbserveur.equals("oracle")){
        		sql = "UPDATE t_rights SET grid=group_right_info_SEQ.CURRVAL WHERE grid=?";
			}
			PreparedStatement stUpd = connection.prepareStatement(sql);

			while( res.next() )
			{
				int grid = res.getInt("grid");
				st.setInt(2, grid);
				st.executeUpdate();   // Ajout du nouveau rrg

				stUpd.setInt(1, grid);
				stUpd.executeUpdate();  /// Met a jour la table de droit temporaire
			}
			st.close();

			/// On copie tout dans les vrai tables
			/// Structure
			sql = "INSERT INTO node(node_uuid, node_parent_uuid, node_children_uuid, node_order, metadata, metadata_wad, metadata_epm, res_node_uuid, res_res_node_uuid, res_context_node_uuid, shared_res, shared_node, shared_node_res, shared_res_uuid, shared_node_uuid, shared_node_res_uuid, asm_type, xsi_type, semtag, semantictag, label, code, descr, format, modif_user_id, modif_date, portfolio_id) " +
					"SELECT new_uuid, node_parent_uuid, node_children_uuid, node_order, metadata, metadata_wad, metadata_epm, res_node_uuid, res_res_node_uuid, res_context_node_uuid, shared_res, shared_node, shared_node_res, shared_res_uuid, shared_node_uuid, shared_node_res_uuid, asm_type, xsi_type, semtag, semantictag, label, code, descr, format, modif_user_id, modif_date, portfolio_id " +
					"FROM t_data";
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			/// Resources
			sql = "INSERT INTO resource_table(node_uuid, xsi_type, content, user_id, modif_user_id, modif_date) " +
					"SELECT new_uuid, xsi_type, content, user_id, modif_user_id, modif_date " +
					"FROM t_res";
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			/// Ajout des droits des noeuds
			sql = "INSERT INTO group_rights(grid, id, RD, WR, DL, SB, AD, types_id, rules_id) " +
					"SELECT grid, id, RD, WR, DL, SB, AD, types_id, rules_id " +
					"FROM t_rights";
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			/// Ajout du portfolio dans la table
			sql = "INSERT INTO portfolio(portfolio_id, root_node_uuid, user_id, model_id, modif_user_id, modif_date, active) " +
					"SELECT d.portfolio_id, d.new_uuid, p.user_id, p.model_id, p.modif_user_id, p.modif_date, p.active " +
					"FROM t_data d INNER JOIN portfolio p " +
					"ON d.node_uuid=p.root_node_uuid";

			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();
		}
		catch( Exception e )
		{
			try
			{
				newPortfolioUuid = "erreur: "+e.getMessage();
				if( connection.getAutoCommit() == false )
					connection.rollback();
			}
			catch( SQLException e1 ){ e1.printStackTrace(); }
			e.printStackTrace();
		}
		finally
		{
			try
			{
				connection.setAutoCommit(true);
				// Les 'pooled connection' ne se ferment pas vraiment. On nettoie manuellement les tables temporaires...
				if (dbserveur.equals("mysql")){
					sql = "DROP TEMPORARY TABLE IF EXISTS t_data, t_res, t_struc, t_struc_2, t_rights";
					st = connection.prepareStatement(sql);
					st.execute();
					st.close();
				} else if (dbserveur.equals("oracle")){
		            sql = "{call drop_tables(tmpTableList('t_data', 't_res','t_struc', 't_struc_2', 't_rights'))}";
		            CallableStatement ocs = connection.prepareCall(sql) ;
		            ocs.execute(); 
		            ocs.close();
				}

				connection.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}

		return newPortfolioUuid;
	}

	@Override
	public Object postImportNode( MimeType inMimeType, String destUuid, String tag, String code, int userId, int groupId ) throws Exception
	{
		if( "".equals(tag) || tag == null || "".equals(code) || code == null )
			return "erreur";

		String sql = "";
		PreparedStatement st;
		String createdUuid="erreur";

		/*
      long start = System.currentTimeMillis();
      long t1=0; long t2=0; long t3=0; long t4=0; long t5=0;
      long t6=0; long t7=0; long t8=0; long t9=0; long t10=0;
      long t11=0; long t12=0; long t13=0; long t14=0; long t15=0;
      long t16=0; long t17=0; long t18=0; long t19=0; long t20=0;
      long t21=0; long t22=0;
      long end=0;
      //*/

		try
		{
			/// On retrouve le uuid du noeud de base dont le tag est inclus dans le code et est actif
			sql = "SELECT bin2uuid(n2.node_uuid) AS nUuid, bin2uuid(n2.portfolio_id) AS pUuid " +
					"FROM node n1 " +
					"LEFT JOIN node n2 ON n1.portfolio_id=n2.portfolio_id " +
					"LEFT JOIN portfolio p ON p.portfolio_id=n2.portfolio_id " +
					"WHERE n2.semantictag=? AND n1.code=? " +
					"AND p.active =1";
			st = connection.prepareStatement(sql);
			st.setString(1, tag);
			st.setString(2, code);
			ResultSet res = st.executeQuery();
			String baseUuid="";
			String pUuid="";
			if( res.next() )    // On prend le premier, tr�s chic pour l'utilisateur...
			{
				baseUuid = res.getString("nUuid");
				pUuid = res.getString("pUuid");
			}
			else
				return "Selection non existante.";

			//        t1 = System.currentTimeMillis();

			///// Cr�ation des tables temporaires
			/// Pour la copie de la structure
			sql = "CREATE TEMPORARY TABLE t_data(" +
					"new_uuid binary(16) NOT NULL, " +  /// Pour la copie d'une nouvelle structure
					"node_uuid binary(16)  NOT NULL, " +
					"node_parent_uuid binary(16) DEFAULT NULL, " +
					//            "node_children_uuid blob, " +
					"node_order int(12) NOT NULL, " +
					//            "metadata text NOT NULL, " +
					//            "metadata_wad text NOT NULL, " +
					//            "metadata_epm text NOT NULL, " +
					"res_node_uuid binary(16) DEFAULT NULL, " +
					"res_res_node_uuid binary(16) DEFAULT NULL, " +
					"res_context_node_uuid binary(16)  DEFAULT NULL, " +
					"shared_res int(1) NOT NULL, " +
					"shared_node int(1) NOT NULL, " +
					"shared_node_res int(1) NOT NULL, " +
					"shared_res_uuid BINARY(16)  NULL, " +
					"shared_node_uuid BINARY(16) NULL, " +
					"shared_node_res_uuid BINARY(16) NULL, " +
					"asm_type varchar(50) DEFAULT NULL, " +
					"xsi_type varchar(50)  DEFAULT NULL, " +
					"semtag varchar(250) DEFAULT NULL, " +
					"semantictag varchar(250) DEFAULT NULL, " +
					"label varchar(250)  DEFAULT NULL, " +
					"code varchar(45)  DEFAULT NULL, " +
					"descr varchar(250)  DEFAULT NULL, " +
					"format varchar(30) DEFAULT NULL, " +
					"modif_user_id int(12) NOT NULL, " +
					"modif_date timestamp NULL DEFAULT NULL, " +
					"portfolio_id binary(16) DEFAULT NULL) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
			if (dbserveur.equals("oracle")){
		        sql = "CREATE GLOBAL TEMPORARY TABLE t_data(" +
		                "new_uuid RAW(16) NOT NULL, " +  /// Pour la copie d'une nouvelle structure
		                "node_uuid RAW(16)  NOT NULL, " +
		                "node_parent_uuid RAW(16) DEFAULT NULL, " +
//		                "node_children_uuid blob, " +
		                "node_order NUMBER(12) NOT NULL, " +
//		                "metadata CLOB NOT NULL, " +
//		                "metadata_wad CLOB NOT NULL, " +
//		                "metadata_epm CLOB NOT NULL, " +
		                "res_node_uuid RAW(16) DEFAULT NULL, " +
		                "res_res_node_uuid RAW(16) DEFAULT NULL, " +
		                "res_context_node_uuid RAW(16)  DEFAULT NULL, " +
		                "shared_res NUMBER(1) NOT NULL, " +
		                "shared_node NUMBER(1) NOT NULL, " +
		                "shared_node_res NUMBER(1) NOT NULL, " +
		                "shared_res_uuid RAW(16)  NULL, " +
		                "shared_node_uuid RAW(16) NULL, " +
		                "shared_node_res_uuid RAW(16) NULL, " +
		                "asm_type VARCHAR2(50 CHAR) DEFAULT NULL, " +
		                "xsi_type VARCHAR2(50 CHAR)  DEFAULT NULL, " +
		                "semtag VARCHAR2(250 CHAR) DEFAULT NULL, " +
		                "semantictag VARCHAR2(250 CHAR) DEFAULT NULL, " +
		                "label VARCHAR2(250 CHAR)  DEFAULT NULL, " +
		                "code VARCHAR2(45 CHAR)  DEFAULT NULL, " +
		                "descr VARCHAR2(250 CHAR)  DEFAULT NULL, " +
		                "format VARCHAR2(30 CHAR) DEFAULT NULL, " +
		                "modif_user_id NUMBER(12) NOT NULL, " +
		                "modif_date timestamp DEFAULT NULL, " +
		                "portfolio_id RAW(16) DEFAULT NULL) ON COMMIT PRESERVE ROWS";
			}
			st = connection.prepareStatement(sql);
			st.execute();
			st.close();

			//        t2 = System.currentTimeMillis();

			/// Pour la copie des donn�es
			sql = "CREATE TEMPORARY TABLE t_res(" +
					"new_uuid binary(16) NOT NULL, " +  /// Pour la copie d'une nouvelle structure
					"node_uuid binary(16) NOT NULL, " +
					"xsi_type varchar(50) DEFAULT NULL, " +
					//            "content text, " +
					"user_id int(11) DEFAULT NULL, " +
					"modif_user_id int(12) NOT NULL, " +
					"modif_date timestamp NULL DEFAULT NULL) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
			if (dbserveur.equals("oracle")){
		        sql = "CREATE GLOBAL TEMPORARY TABLE t_res(" +
		                "new_uuid RAW(16) NOT NULL, " +  /// Pour la copie d'une nouvelle structure
		                "node_uuid RAW(16) NOT NULL, " +
		                "xsi_type VARCHAR2(50 CHAR) DEFAULT NULL, " +
//		                "content CLOB, " +
		                "user_id NUMBER(11) DEFAULT NULL, " +
		                "modif_user_id NUMBER(12) NOT NULL, " +
		                "modif_date timestamp DEFAULT NULL) ON COMMIT PRESERVE ROWS";
			}
			st = connection.prepareStatement(sql);
			st.execute();
			st.close();

			//        t3 = System.currentTimeMillis();

			/// Pour le filtrage de la structure
			sql = "CREATE TEMPORARY TABLE t_struc(" +
					"node_order int(12) NOT NULL, " +
					"new_uuid binary(16) NOT NULL, " +
					"uuid binary(16) UNIQUE NOT NULL, " +
					"node_parent_uuid binary(16) NOT NULL, " +
					"t_level INT) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
			if (dbserveur.equals("oracle")){
		        sql = "CREATE GLOBAL TEMPORARY TABLE t_struc(" +
		                "node_order NUMBER(12) NOT NULL, " +
		                "new_uuid RAW(16) NOT NULL, " +
		                "uuid RAW(16) UNIQUE NOT NULL, " +
		                "node_parent_uuid RAW(16) NOT NULL, " +
		                "t_level NUMBER(10,0)) ON COMMIT PRESERVE ROWS";
			}
			st = connection.prepareStatement(sql);
			st.execute();
			st.close();

			//        t4 = System.currentTimeMillis();

			// En double car on ne peut pas faire d'update/select d'une m�me table temporaire
			sql = "CREATE TEMPORARY TABLE t_struc_2(" +
					"node_order int(12) NOT NULL, " +
					"new_uuid binary(16) NOT NULL, " +
					"uuid binary(16) UNIQUE NOT NULL, " +
					"node_parent_uuid binary(16) NOT NULL, " +
					"t_level INT) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
			if (dbserveur.equals("oracle")){
		        sql = "CREATE GLOBAL TEMPORARY TABLE t_struc_2(" +
		                "node_order NUMBER(12) NOT NULL, " +
		                "new_uuid RAW(16) NOT NULL, " +
		                "uuid RAW(16) UNIQUE NOT NULL, " +
		                "node_parent_uuid RAW(16) NOT NULL, " +
		                "t_level NUMBER(10,0)"+
		                ",  CONSTRAINT t_struc_UK_uuid UNIQUE (uuid)) ON COMMIT PRESERVE ROWS";
			}
			st = connection.prepareStatement(sql);
			st.execute();
			st.close();

			//        t5 = System.currentTimeMillis();

			/// Copie de la structure
			sql = "INSERT INTO t_data(new_uuid, node_uuid, node_parent_uuid, node_order, res_node_uuid, res_res_node_uuid, res_context_node_uuid , shared_res, shared_node, shared_node_res, shared_res_uuid, shared_node_uuid, shared_node_res_uuid, asm_type, xsi_type, semtag, semantictag, label, code, descr, format, modif_user_id, modif_date, portfolio_id) ";
			if (dbserveur.equals("mysql")){
				sql += "SELECT uuid2bin(UUID()), ";
			} else if (dbserveur.equals("oracle")){
				sql += "SELECT sys_guid(), ";
			}	
			sql += "node_uuid, node_parent_uuid, node_order, res_node_uuid, res_res_node_uuid, res_context_node_uuid , shared_res, shared_node, shared_node_res, shared_res_uuid, shared_node_uuid, shared_node_res_uuid, asm_type, xsi_type, semtag, semantictag, label, code, descr, format, modif_user_id, modif_date, portfolio_id " +
					"FROM node n " +
					"WHERE portfolio_id=uuid2bin(?)";
			st = connection.prepareStatement(sql);
			st.setString(1, pUuid);
			st.executeUpdate();
			st.close();

			//        t6 = System.currentTimeMillis();

			/// Dans la table temporaire on retrouve les noeuds concern�s
			/// (assure une convergence de la r�cursion et limite le nombre de lignes dans la recherche)
			/// Init table
			sql = "INSERT INTO t_struc(node_order, new_uuid, uuid, node_parent_uuid, t_level) " +
					"SELECT d.node_order, d.new_uuid, d.node_uuid, uuid2bin(?), 0 " +
					"FROM t_data d " +
					"WHERE d.node_uuid=uuid2bin(?)";
			st = connection.prepareStatement(sql);
			st.setString(1, destUuid);  // Pour le branchement avec la structure de destination
			st.setString(2, baseUuid);
			st.executeUpdate();
			st.close();

			//        t7 = System.currentTimeMillis();

			/// On boucle, sera toujours <= � "nombre de noeud du portfolio"
			int level = 0;
			int added = 1;
			if (dbserveur.equals("mysql")){
	        	sql = "INSERT IGNORE INTO t_struc_2(node_order, new_uuid, uuid, node_parent_uuid, t_level) ";
			} else if (dbserveur.equals("oracle")){
				sql = "INSERT /*+ ignore_row_on_dupkey_index(t_struc_2,t_struc_2_UK_uuid)*/ INTO t_struc_2(node_order, new_uuid, uuid, node_parent_uuid, t_level) ";
			}
			sql += "SELECT d.node_order, d.new_uuid, d.node_uuid, d.node_parent_uuid, ? " +
					"FROM t_data d WHERE d.node_parent_uuid IN (SELECT uuid FROM t_struc t " +
					"WHERE t.t_level=?)";

	        String sqlTemp=null;
			if (dbserveur.equals("mysql")){
				sqlTemp = "INSERT IGNORE INTO t_struc SELECT * FROM t_struc_2;";
			} else if (dbserveur.equals("oracle")){
				sqlTemp = "INSERT /*+ ignore_row_on_dupkey_index(t_struc,t_struc_UK_uuid)*/ INTO t_struc SELECT * FROM t_struc_2";
			}
	        PreparedStatement stTemp = connection.prepareStatement(sqlTemp);

			st = connection.prepareStatement(sql);
			while( added != 0 )
			{
				st.setInt(1, level+1);
				st.setInt(2, level);
				st.executeUpdate();
				added = stTemp.executeUpdate();   // On s'arr�te quand rien � �t� ajout�
				level = level + 1;    // Prochaine �tape
			}
			st.close();
			stTemp.close();

			//        t8 = System.currentTimeMillis();

			/// On retire les éléments null, ça pose problème par la suite
			if (dbserveur.equals("mysql")){
				sql = "DELETE FROM t_struc WHERE new_uuid=0x0000000000000000000000000000000";
			} else if (dbserveur.equals("oracle")){
		        sql = "DELETE FROM t_struc WHERE new_uuid='00000000000000000000000000000000'";
			}
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			//        t9 = System.currentTimeMillis();

			/// On filtre les donn�es dont on a pas besoin
			sql = "DELETE FROM t_data WHERE node_uuid NOT IN (SELECT uuid FROM t_struc)";
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			//        t10 = System.currentTimeMillis();

			///// FIXME TODO: V�rifier les droits sur les donn�es restantes

			/// Copie des donn�es non partag�s (shared=0)
			sql = "INSERT INTO t_res(new_uuid, node_uuid, xsi_type, user_id, modif_user_id, modif_date) ";
			if (dbserveur.equals("mysql")){
				sql += "SELECT uuid2bin(UUID()), ";
			} else if (dbserveur.equals("oracle")){
				sql += "SELECT sys_guid(), ";
			}	
			sql += "r.node_uuid, r.xsi_type, r.user_id, r.modif_user_id, r.modif_date " +
					"FROM t_data d, resource_table r " +
					"WHERE (d.res_node_uuid=r.node_uuid " +
					"OR res_res_node_uuid=r.node_uuid " +
					"OR res_context_node_uuid=r.node_uuid) " +
					"AND (shared_res=0 OR shared_node=0 OR shared_node_res=0)";
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			//        t11 = System.currentTimeMillis();

			/// R�solution des nouveaux uuid avec les parents
			// Avec la structure
			sql = "UPDATE t_data t " +
					"SET t.node_parent_uuid = (SELECT new_uuid FROM t_struc s WHERE s.uuid=t.node_parent_uuid)";
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			//        t12 = System.currentTimeMillis();

			// Avec les ressources
			sql = "UPDATE t_data t " +
					"SET t.res_node_uuid = (SELECT new_uuid FROM t_res r WHERE r.node_uuid= t.res_node_uuid)";
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			//        t13 = System.currentTimeMillis();

			sql = "UPDATE t_data t " +
					"SET t.res_res_node_uuid = (SELECT new_uuid FROM t_res r WHERE r.node_uuid= t.res_res_node_uuid)";
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			//        t14 = System.currentTimeMillis();

			sql = "UPDATE t_data t " +
					"SET t.res_context_node_uuid = (SELECT new_uuid FROM t_res r WHERE r.node_uuid=t.res_context_node_uuid)";
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			//        t15 = System.currentTimeMillis();

			/// Mise � jour du parent de la nouvelle copie ainsi que l'ordre
			sql = "UPDATE t_data " +
					"SET node_parent_uuid=uuid2bin(?), " +
					"node_order=(SELECT COUNT(node_parent_uuid) FROM node WHERE node_parent_uuid=uuid2bin(?)) " +
					"WHERE node_uuid=uuid2bin(?)";
			st = connection.prepareStatement(sql);
			st.setString(1, destUuid);
			st.setString(2, destUuid);
			st.setString(3, baseUuid);
			st.executeUpdate();
			st.close();

			//        t16 = System.currentTimeMillis();

			// Mise � jour de l'appartenance au portfolio de destination
			sql = "UPDATE t_data " +
					"SET portfolio_id=(SELECT portfolio_id FROM node WHERE node_uuid=uuid2bin(?))";
			st = connection.prepareStatement(sql);
			st.setString(1, destUuid);
			st.executeUpdate();
			st.close();

			//        t17 = System.currentTimeMillis();

			/// On copie tout dans les vrai tables
			connection.setAutoCommit(false);

			/// Structure
			sql = "INSERT INTO node(node_uuid, node_parent_uuid, node_order, metadata, metadata_wad, metadata_epm, res_node_uuid, res_res_node_uuid, res_context_node_uuid, shared_res, shared_node, shared_node_res, shared_res_uuid, shared_node_uuid, shared_node_res_uuid, asm_type, xsi_type, semtag, semantictag, label, code, descr, format, modif_user_id, modif_date, portfolio_id) " +
					"SELECT t.new_uuid, t.node_parent_uuid, t.node_order, n.metadata, n.metadata_wad, n.metadata_epm, t.res_node_uuid, t.res_res_node_uuid, t.res_context_node_uuid, t.shared_res, t.shared_node, t.shared_node_res, t.shared_res_uuid, t.shared_node_uuid, t.shared_node_res_uuid, t.asm_type, t.xsi_type, t.semtag, t.semantictag, t.label, t.code, t.descr, t.format, t.modif_user_id, t.modif_date, t.portfolio_id " +
					"FROM t_data t LEFT JOIN node n ON t.node_uuid=n.node_uuid";
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			//        t18 = System.currentTimeMillis();

			/// Resources
			sql = "INSERT INTO resource_table(node_uuid, xsi_type, content, user_id, modif_user_id, modif_date) " +
					"SELECT t.new_uuid, r.xsi_type, r.content, r.user_id, r.modif_user_id, r.modif_date " +
					"FROM t_res t LEFT JOIN resource_table r ON t.node_uuid=r.node_uuid";
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			//        t19 = System.currentTimeMillis();

			/// Mise � jour de la liste des enfants
			if (dbserveur.equals("mysql")){
				sql = "UPDATE node d, (" +
					"SELECT p.node_parent_uuid, " +
					"GROUP_CONCAT(bin2uuid(p.new_uuid) ORDER BY p.node_order) AS value " +
					"FROM t_data p GROUP BY p.node_parent_uuid) tmp " +
					"SET d.node_children_uuid=tmp.value " +
					"WHERE tmp.node_parent_uuid=d.node_uuid";
			} else if (dbserveur.equals("oracle")){
		        sql = "UPDATE node d SET d.node_children_uuid=(SELECT value FROM (SELECT p.node_parent_uuid, LISTAGG(bin2uuid(p.new_uuid), ',') WITHIN GROUP (ORDER BY p.node_order) AS value FROM t_data p GROUP BY p.node_parent_uuid) tmp WHERE tmp.node_parent_uuid=d.node_uuid) WHERE EXISTS (SELECT 1 FROM t_data WHERE node_parent_uuid=d.node_uuid)";
			}
			st = connection.prepareStatement(sql);
			st.execute();
			st.close();

			//        t20 = System.currentTimeMillis();

			/// Ajout de l'enfant dans la structure originelle
			if (dbserveur.equals("mysql")){
				sql = "UPDATE node n1, (" +
					"SELECT GROUP_CONCAT(bin2uuid(n2.node_uuid) ORDER BY n2.node_order) AS value " +
					"FROM node n2 " +
					"WHERE n2.node_parent_uuid=uuid2bin(?) " +
					"GROUP BY n2.node_parent_uuid) tmp " +
					"SET n1.node_children_uuid=tmp.value " +
					"WHERE n1.node_uuid=uuid2bin(?)";
			} else if (dbserveur.equals("oracle")){
		        sql = "UPDATE node SET node_children_uuid=(SELECT LISTAGG(bin2uuid(n2.node_uuid), ',') WITHIN GROUP (ORDER BY n2.node_order) AS value FROM node n2 WHERE n2.node_parent_uuid=uuid2bin(?) GROUP BY n2.node_parent_uuid) WHERE node_uuid=uuid2bin(?)";
			}
			st = connection.prepareStatement(sql);
			st.setString(1, destUuid);
			st.setString(2, destUuid);
			st.executeUpdate();
			st.close();

			//        t21 = System.currentTimeMillis();

			/// Ajout des droits des noeuds
			sql = "INSERT INTO group_rights(grid, id, RD, WR, DL, SB, AD, types_id, rules_id) " +
					"SELECT g.grid, r.new_uuid, r.RD, r.WR, r.DL, r.SB, r.AD, r.types_id, r.rules_id " +
					"FROM " +
					"(SELECT gri.grid, gri.label " +
					"FROM node n " +
					"LEFT JOIN group_right_info gri ON n.portfolio_id=gri.portfolio_id " +
					"WHERE n.node_uuid=uuid2bin(?)) g," +  // Retrouve les groupes de destination via le noeud de destination
					"(SELECT gri.label, s.new_uuid, gr.RD, gr.WR, gr.DL, gr.SB, gr.AD, gr.types_id, gr.rules_id " +
					"FROM t_struc s, group_rights gr, group_right_info gri " +
					"WHERE s.uuid=gr.id AND gr.grid=gri.grid) r " + // Prend la liste des droits actuel des noeuds dupliqu�s
					"WHERE g.label=r.label"; // On croise le nouveau 'grid' avec le 'grid' d'origine via le label
			st = connection.prepareStatement(sql);
			st.setString(1, destUuid);
			st.executeUpdate();
			st.close();

			//        t22 = System.currentTimeMillis();

			/// Ajout des droits des resources
			// Apparement inutile si l'on s'en occupe qu'au niveau du contexte...
			sql = "INSERT INTO group_rights(grid, id, RD, WR, DL, SB, AD, types_id, rules_id) " +
					"SELECT gr.grid, r.new_uuid, gr.RD, gr.WR, gr.DL, gr.SB, gr.AD, gr.types_id, gr.rules_id " +
					"FROM t_res r " +
					"LEFT JOIN group_rights gr ON r.node_uuid=gr.id " +
					"LEFT JOIN group_info gi ON gr.grid=gi.grid " +
					"WHERE gi.gid=?";
			st = connection.prepareStatement(sql);
			st.setInt(1, groupId);
			st.executeUpdate();
			st.close();

			//        end = System.currentTimeMillis();

			createdUuid = "";
		}
		catch( Exception e )
		{
			try
			{
				createdUuid = "erreur: "+e.getMessage();
				if(connection.getAutoCommit() == false)
					connection.rollback();
			}
			catch( SQLException e1 ){ e1.printStackTrace(); }
			e.printStackTrace();
		}
		finally
		{
			try
			{
				connection.setAutoCommit(true);
				// Les 'pooled connection' ne se ferment pas vraiment. On nettoie manuellement les tables temporaires...
				if (dbserveur.equals("mysql")){
					sql = "DROP TEMPORARY TABLE IF EXISTS t_data, t_res, t_struc, t_struc_2";
					st = connection.prepareStatement(sql);
					st.execute();
					st.close();
				} else if (dbserveur.equals("oracle")){
		            sql = "{call drop_tables(tmpTableList('t_data', 't_res','t_struc', 't_struc_2'))}";
		            CallableStatement ocs = connection.prepareCall(sql) ;
		            ocs.execute(); 
		            ocs.close();
				}

				connection.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}

		/*
      System.out.println("---- Portfolio ---");
      System.out.println("d0-1: "+(t1-start));
      System.out.println("d1-2: "+(t2-t1));
      System.out.println("d2-3: "+(t3-t2));
      System.out.println("d3-4: "+(t4-t3));
      System.out.println("d4-5: "+(t5-t4));
      System.out.println("d5-6: "+(t6-t5));
      System.out.println("d6-7: "+(t7-t6));
      System.out.println("d7-8: "+(t8-t7));
      System.out.println("d8-9: "+(t9-t8));
      System.out.println("d9-10: "+(t10-t9));
      System.out.println("d10-11: "+(t11-t10));
      System.out.println("d11-12: "+(t12-t11));
      System.out.println("d12-13: "+(t13-t12));
      System.out.println("d13-14: "+(t14-t13));
      System.out.println("d14-15: "+(t15-t14));
      System.out.println("d15-16: "+(t16-t15));
      System.out.println("d16-17: "+(t17-t16));
      System.out.println("d17-18: "+(t18-t17));
      System.out.println("d18-19: "+(t19-t18));
      System.out.println("d19-20: "+(t20-t19));
      System.out.println("d20-21: "+(t21-t20));
      System.out.println("d21-22: "+(t22-t21));
      System.out.println("d22-23: "+(end-t22));
      System.out.println("------------------");
      //*/

		return createdUuid;
	}


	@Override
	public Object postNode(MimeType inMimeType, String parentNodeUuid, String in,int userId, int groupId) throws Exception
	{
		/// FIXME On devrait vérifier le droit d'ajouter.
		/// Mais dans WAD il n'y a pas ce type de droit
		// Retrait le temps de corriger ça, sinon un étudiant ne peux pas ajouter des auto-évaluations/commentaires, etc
		/*
	  NodeRight noderight = credential.getNodeRight(userId,groupId, parentNodeUuid, credential.ADD);
      if(!noderight.add)
        if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");
		//*/

		//TODO Optimiser le nombre de requetes (3 => 1)

		int nodeOrder = getMysqlNodeNextOrderChildren(parentNodeUuid);
		String portfolioUid = getPortfolioUuidByNodeUuid(parentNodeUuid);

		String result = null;
		String portfolioModelId = getPortfolioModelUuid(portfolioUid);

		//TODO getNodeRight postNode

		//	return "faux";

		String inPars = DomUtils.cleanXMLData(in);
		Document doc = DomUtils.xmlString2Document(inPars, new StringBuffer());
		// Puis on le recree
		Node rootNode;
		String nodeType = "";
		rootNode = doc.getDocumentElement();
		nodeType = rootNode.getNodeName();
		/*rootNode = (doc.getElementsByTagName("asmUnit")).item(0);
		if(rootNode!=null)  nodeType = "asmUnit";

		if(rootNode==null)
		{
			rootNode = (doc.getElementsByTagName("asmUnitStructure")).item(0);
			if(rootNode!=null)  nodeType = "asmUnitStructure";
		}

		if(rootNode==null)
		{
			rootNode = (doc.getElementsByTagName("asmStructure")).item(0);
			if(rootNode!=null)  nodeType = "asmStructure";
		}

		if(rootNode==null)
		{
			rootNode = (doc.getElementsByTagName("asmContext")).item(0);
			if(rootNode!=null)  nodeType = "asmContext";
		}

		if(rootNode==null)
		{
			rootNode = (doc.getElementsByTagName("asmResource")).item(0);
			if(rootNode!=null)  nodeType = "asmResource";
		}
		 */

		String nodeUuid = writeNode(rootNode, portfolioUid,  portfolioModelId,userId,nodeOrder,null,parentNodeUuid,0,0, false);

		result = "<nodes>";
		result += "<"+nodeType+" ";
		result += DomUtils.getXmlAttributeOutput("id", nodeUuid)+" ";
		result += "/>";
		result += "</nodes>";

		return result;
	}

	@Override
	public Object putNode(MimeType inMimeType, String nodeUuid, String in,int userId, int groupId) throws Exception
	{
		String uuid = null;
		String asmType = null;
		String xsiType = null;
		String semtag = null;
		String format = null;
		String label = null;
		String code = null;
		String descr = null;
		String metadata = "";
		String metadataWad = "";
		String metadataEpm = "";
		String nodeChildrenUuid = null;
		String semanticTag = null;

		int sharedRes = 0;
		int sharedNode = 0;
		int sharedNodeRes = 0;

		int returnValue = 0;

		//TODO putNode getNodeRight
		if(!credential.hasNodeRight(userId,groupId,nodeUuid, credential.WRITE))
			return "faux";

		//TODO Optimiser le nombre de requetes (3 => 1)
		int nodeOrder = getNodeOrderByNodeUuid(nodeUuid);
		String parentNodeUuid = getNodeParentUuidByNodeUuid(nodeUuid);
		String portfolioUid = getPortfolioUuidByNodeUuid(nodeUuid);
		String portfolioModelId = getPortfolioModelUuid(portfolioUid);

		// D'abord on supprime les noeuds existants
		//deleteNode(nodeUuid, userId);

		String inPars = DomUtils.cleanXMLData(in);
		Document doc = DomUtils.xmlString2Document(inPars, new StringBuffer());
		// Puis on le recree
		Node node;
		node = doc.getDocumentElement();
		//	nodeType = rootNode.getNodeName();
		/*node = (doc.getElementsByTagName("asmUnit")).item(0);
		if(node==null) node = (doc.getElementsByTagName("asmUnitStructure")).item(0);
		if(node==null) node = (doc.getElementsByTagName("asmStructure")).item(0);
		if(node==null) node = (doc.getElementsByTagName("asmResource")).item(0);
		if(node==null) node = (doc.getElementsByTagName("asmContext")).item(0);
		if(node==null) node = (doc.getElementsByTagName("asmRoot")).item(0);
		 */

		if(node==null) return null;

		try
		{
			if(node.getNodeName()!=null) asmType = node.getNodeName();
		}
		catch(Exception ex) {}
		try
		{
			if(node.getAttributes().getNamedItem("xsi_type")!=null) xsiType = node.getAttributes().getNamedItem("xsi_type").getNodeValue();
		}
		catch(Exception ex) {}
		try
		{
			if(node.getAttributes().getNamedItem("semtag")!=null) semtag = node.getAttributes().getNamedItem("semtag").getNodeValue();
		}
		catch(Exception ex)
		{

		}
		try
		{
			if(node.getAttributes().getNamedItem("format")!=null) format = node.getAttributes().getNamedItem("format").getNodeValue();
		}
		catch(Exception ex) {}

		// Si id dï¿½fini, alors on ï¿½crit en base
		//TODO Transactionnel noeud+enfant
		NodeList children = null;

		children = node.getChildNodes();
		// On parcourt une premiï¿½re fois les enfants pour rï¿½cuperer la liste ï¿½ ï¿½crire en base
		int j=0;
		for(int i=0;i<children.getLength();i++)
		{
			//if(!children.item(i).getNodeName().equals("#text"))
			//	nodeChildren.add(children.item(i).getAttributes().getNamedItem("id").getNodeValue());
			if(!children.item(i).getNodeName().equals("#text"))
			{
				// On vï¿½rifie si l'enfant n'est pas un ï¿½lement de type code, label ou descr
				if(children.item(i).getNodeName().equals("label"))
				{
					label = DomUtils.getInnerXml(children.item(i));
				}
				else if(children.item(i).getNodeName().equals("code"))
				{
					code = DomUtils.getInnerXml(children.item(i));
				}
				else if(children.item(i).getNodeName().equals("description"))
				{
					descr = DomUtils.getInnerXml(children.item(i));
				}
				else if(children.item(i).getNodeName().equals("semanticTag"))
				{
					semtag = DomUtils.getInnerXml(children.item(i));
				}
				else if(children.item(i).getNodeName().equals("asmResource"))
				{
					// Si le noeud est de type asmResource, on stocke le innerXML du noeud
					updateMysqlResourceByXsiType(nodeUuid,children.item(i).getAttributes().getNamedItem("xsi_type").getNodeValue().toString(),DomUtils.getInnerXml(children.item(i)),userId);
				}
				else if(children.item(i).getNodeName().equals("metadata-wad"))
				{
					metadataWad = DomUtils.getNodeAttributesString(children.item(i));// " attr1=\"wad1\" attr2=\"wad2\" ";
					metadataWad = processMeta(userId, metadataWad);
				}
				else if(children.item(i).getNodeName().equals("metadata-epm"))
				{
					metadataEpm = DomUtils.getNodeAttributesString(children.item(i));
				}
				else if(children.item(i).getNodeName().equals("metadata"))
				{
					String tmpSharedRes = "";
					try
					{
						tmpSharedRes = children.item(i).getAttributes().getNamedItem("sharedRes").getNodeValue();
					}
					catch(Exception ex) {}

					String tmpSharedNode = "";
					try
					{
						tmpSharedNode = children.item(i).getAttributes().getNamedItem("sharedNode").getNodeValue();
					}
					catch(Exception ex)   {}
					String tmpSharedNodeResource = "";
					try
					{
						tmpSharedNodeResource = children.item(i).getAttributes().getNamedItem("sharedNodeResource").getNodeValue();
					}
					catch(Exception ex)   {}

					if(tmpSharedRes.equalsIgnoreCase("y"))
						sharedRes = 1;
					if(tmpSharedNode.equalsIgnoreCase("y"))
						sharedNode = 1;
					if(tmpSharedNodeResource.equalsIgnoreCase("y"))
						sharedNodeRes = 1;

					metadata = DomUtils.getNodeAttributesString(children.item(i));
				}
				else if(children.item(i).getAttributes()!=null)
				{
					if(children.item(i).getAttributes().getNamedItem("id")!=null)
					{
						if(nodeChildrenUuid==null) nodeChildrenUuid = "";
						if(j>0) nodeChildrenUuid += ",";
						nodeChildrenUuid += children.item(i).getAttributes().getNamedItem("id").getNodeValue().toString();
						updatetMySqlNodeOrder(children.item(i).getAttributes().getNamedItem("id").getNodeValue().toString(),j);
						j++;
					}
				}
			}
		}

		// Si le noeud est de type asmResource, on stocke le innerXML du noeud
		if(node.getNodeName().equals("asmResource"))
		{
			updateMysqlResource(nodeUuid,xsiType, DomUtils.getInnerXml(node),userId);
		}

		if(nodeChildrenUuid!=null) updateMysqlNodeChildren(nodeUuid);
		//TODO UpdateNode different selon creation de modele ou instantiation copie
		return updatetMySqlNode(nodeUuid, asmType, xsiType, semtag, label, code, descr, format, metadata,metadataWad,metadataEpm,sharedRes,sharedNode,sharedNodeRes, userId);
	}

	@Override
	public Object deleteResource(String resourceUuid,int userId, int groupId) throws Exception
	{
		if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		return deleteMySqlResource(resourceUuid, userId, groupId);
		//TODO asmResource(s) dans table Node et parentNode children ï¿½ mettre ï¿½ jour
	}

	@Override
	public Object getResource(MimeType outMimeType, String nodeParentUuid, int userId, int groupId) throws Exception
	{
		java.sql.ResultSet res = getMysqlResourceByNodeParentUuid(nodeParentUuid);
		res.next();
		if(!credential.hasNodeRight(userId,groupId, nodeParentUuid, credential.READ))
			return "faux";
		String returnValue = "";
		return "<asmResource id=\""+res.getString("node_uuid")+"\" contextid=\""+nodeParentUuid+"\"  >"+res.getString("content")+"</asmResource>";
	}

	@Override
	public Object getResources(MimeType outMimeType, String portfolioUuid, int userId, int groupId) throws Exception
	{
		java.sql.ResultSet res = getMysqlResources(portfolioUuid);
		String returnValue = "";
		if(outMimeType.getSubType().equals("xml"))
		{
			returnValue += "<resources>";
			while(res.next())
			{
				if(!credential.hasNodeRight(userId,groupId, res.getString("res_node_uuid"),Credential.READ ))
				{
					//returnValue += null;
				}
				returnValue += "<resource "+DomUtils.getXmlAttributeOutput("id", res.getString("res_node_uuid"))+" />";
			}
			returnValue += "</resources>";
		}
		else
		{
			returnValue += "{";
			boolean firstNode = true;
			while(res.next())
			{
				if(firstNode) firstNode = false;
				else returnValue +=" , ";
				if(!credential.hasNodeRight(userId,groupId, res.getString("res_node_uuid"), Credential.READ))
				{
					//returnValue += null;
				}
				else
				{
					returnValue += "resource: { "+DomUtils.getJsonAttributeOutput("id", res.getString("res_node_uuid"))+" } ";
				}
			}
			returnValue += "}";
		}
		return returnValue;
	}

	@Override
	public Object postResource(MimeType inMimeType, String nodeParentUuid, String in, int userId, int groupId) throws Exception
	{
		if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		//TODO GENERER Uuid
		//String resourceUuid = UUID.randomUUID().toString();
		//return this.insertMysqlResourceByNodeParentUuid(nodeParentUuid,in,userId);
		in = DomUtils.filterXmlResource(in);

		/*String resource;
		try
		{
			resource = getResource(inMimeType, nodeParentUuid, userId).toString();
		}
		catch(Exception ex)
		{
			resource = null;
		}*/

		//if(resource==null)


		java.sql.ResultSet res = getMysqlResourceByNodeParentUuid(nodeParentUuid);
		res.next();
		if(!credential.hasNodeRight(userId,groupId, nodeParentUuid, Credential.WRITE))
		{
			return "faux";
		}
		else postNode(inMimeType, nodeParentUuid, in, userId, groupId);
		//else throw new Exception("le noeud contient dï¿½jï¿½ un enfant de type asmResource !");
		return "";
	}

	@Override
	public Object putResource(MimeType inMimeType, String nodeParentUuid, String in, int userId, int groupId) throws Exception
	{
		// TODO userId ???
		in = DomUtils.filterXmlResource(in);

		ResultSet resNode = getMysqlResourceByNodeParentUuid(nodeParentUuid);
		resNode.next();
		String nodeUuid = resNode.getString("node_uuid");


		Document doc = DomUtils.xmlString2Document(in, new StringBuffer());
		// Puis on le recree
		Node node;

		//	nodeType = rootNode.getNodeName();
		node = (doc.getElementsByTagName("asmResource")).item(0);

		java.sql.ResultSet res = getMysqlResourceByNodeParentUuid(nodeParentUuid);
		res.next();
		if(!credential.hasNodeRight(userId,groupId, nodeParentUuid, credential.WRITE))
			return "faux";

		//putNode(inMimeType, nodeUuid, in, userId);
		return updateMysqlResource(nodeUuid,null,DomUtils.getInnerXml(node),userId);
		//return "";
		//return this.updateMysqlResourceByNodeParentUuid(nodeParentUuid,in,0);
	}

	/*
	 * forcedParentUuid permet de forcer l'uuid parent, indï¿½pendamment de l'attribut du noeud fourni
	 */
	private String writeNode(Node node, String portfolioUuid, String portfolioModelId, int userId, int ordrer, String forcedUuid, String forcedUuidParent,int sharedResParent,int sharedNodeResParent, boolean isPut) throws Exception
	{
		String uuid = "";
		String originUuid = null;
		String parentUuid = null;
		String nodeChildrenUuid = null;
		String modelNodeUuid = null;
		int sharedRes = 0;
		int sharedNode = 0;
		int sharedNodeRes = 0;

		String sharedResUuid = null;
		String sharedNodeUuid = null;
		String sharedNodeResUuid = null;

		String metadata = "";
		String metadataWad = "";
		String metadataEpm = "";
		String asmType = null;
		String xsiType = null;
		String semtag = null;
		String format = null;
		String label = null;
		String code = null;
		String descr = null;
		String semanticTag = null;

		String nodeRole = null;

		String access = null;

		int returnValue = 0;

		if(node==null) return null;

		if(node.getNodeName().equals("portfolio"))
		{
			// On n'attribue pas d'uuid sur la balise portfolio
		}
		else
		{
			try
			{
				if(node.getAttributes().getNamedItem("id")!=null)
				{
					originUuid = node.getAttributes().getNamedItem("id").getNodeValue();
					if(originUuid.length()>0)
					{
						uuid = node.getAttributes().getNamedItem("id").getNodeValue();
					}
					else uuid = UUID.randomUUID().toString();
				}
				else uuid = UUID.randomUUID().toString();
			}
			catch(Exception ex)
			{
				uuid = UUID.randomUUID().toString();
			}
		}

		// Si uuid forcï¿½, alors on ne tient pas compte de l'uuid indiquï¿½ dans le xml
		if(forcedUuid!=null)
		{
			uuid = forcedUuid;
			parentUuid = forcedUuidParent;
		}
		else if(forcedUuidParent!=null)
		{
			// Dans le cas d'un uuid parent forcï¿½ => POST => on gï¿½nï¿½re un UUID
			if(uuid==null) uuid = UUID.randomUUID().toString();
			parentUuid = forcedUuidParent;
		}
		else
		{
			try
			{

				if(node.getParentNode().getAttributes().getNamedItem("id")!=null)
					if(node.getParentNode().getAttributes().getNamedItem("id").getNodeValue().length()>0)
						parentUuid = node.getParentNode().getAttributes().getNamedItem("id").getNodeValue();
			}
			catch(Exception ex)
			{
				//parentUuid = UUID.randomUUID().toString();
			}
		}


		try
		{
			if(node.getNodeName()!=null) asmType = node.getNodeName();
		}
		catch(Exception ex) {}
		try
		{
			if(node.getAttributes().getNamedItem("xsi_type")!=null) xsiType = node.getAttributes().getNamedItem("xsi_type").getNodeValue().trim();
		}
		catch(Exception ex) {}
		try
		{
			if(node.getAttributes().getNamedItem("semtag")!=null) semtag = node.getAttributes().getNamedItem("semtag").getNodeValue().trim();
		}
		catch(Exception ex)
		{

		}
		try
		{
			if(node.getAttributes().getNamedItem("format")!=null) format = node.getAttributes().getNamedItem("format").getNodeValue().trim();
		}
		catch(Exception ex) {}

		// Si id dï¿½fini, alors on ï¿½crit en base
		//TODO Transactionnel noeud+enfant
		NodeList children = null;
		try
		{
			children = node.getChildNodes();
			// On parcourt une premiï¿½re fois les enfants pour rï¿½cuperer la liste ï¿½ ï¿½crire en base
			int j=0;
			for(int i=0;i<children.getLength();i++)
			{
				//if(!children.item(i).getNodeName().equals("#text"))
				//	nodeChildren.add(children.item(i).getAttributes().getNamedItem("id").getNodeValue());
				if(!children.item(i).getNodeName().equals("#text"))
				{
					if(children.item(i).getNodeName().equals("metadata-wad"))
					{

						metadataWad = DomUtils.getNodeAttributesString(children.item(i));// " attr1=\"wad1\" attr2=\"wad2\" ";
						metadataWad = processMeta(userId, metadataWad);

						// Gestion de la securitï¿½ intï¿½grï¿½e
						//
						Node metadataWadNode = children.item(i);
						try
						{
							if(metadataWadNode.getAttributes().getNamedItem("access")!=null)
							{
								//if(access.equalsIgnoreCase("public") || access.contains("public"))
								//	credential.postGroupRight("all",uuid,Credential.READ,portfolioUuid,userId);
							}
						}
						catch(Exception ex) {}

						try
						{
							if(metadataWadNode.getAttributes().getNamedItem("seenoderoles")!=null)
							{
								StringTokenizer tokens = new StringTokenizer(metadataWadNode.getAttributes().getNamedItem("seenoderoles").getNodeValue(), " ");
								while (tokens.hasMoreElements())
								{

									nodeRole = tokens.nextElement().toString();
									credential.postGroupRight(nodeRole,uuid,Credential.READ,portfolioUuid,userId);
								}
							}
						}
						catch(Exception ex) {}
						try
						{
							if(metadataWadNode.getAttributes().getNamedItem("delnoderoles")!=null)
							{
								StringTokenizer tokens = new StringTokenizer(metadataWadNode.getAttributes().getNamedItem("delnoderoles").getNodeValue(), " ");
								while (tokens.hasMoreElements())
								{

									nodeRole = tokens.nextElement().toString();
									credential.postGroupRight(nodeRole,uuid,Credential.DELETE,portfolioUuid,userId);
								}
							}
						}
						catch(Exception ex) {}
						try
						{
							if(metadataWadNode.getAttributes().getNamedItem("editnoderoles")!=null)
							{
								StringTokenizer tokens = new StringTokenizer(metadataWadNode.getAttributes().getNamedItem("editnoderoles").getNodeValue(), " ");
								while (tokens.hasMoreElements())
								{

									nodeRole = tokens.nextElement().toString();
									credential.postGroupRight(nodeRole,uuid,Credential.WRITE,portfolioUuid,userId);
								}
							}
						}
						catch(Exception ex) {}
						try
						{
							if(metadataWadNode.getAttributes().getNamedItem("submitnoderoles")!=null)
							{
								StringTokenizer tokens = new StringTokenizer(metadataWadNode.getAttributes().getNamedItem("submitnoderoles").getNodeValue(), " ");
								while (tokens.hasMoreElements())
								{

									nodeRole = tokens.nextElement().toString();
									credential.postGroupRight(nodeRole,uuid,Credential.SUBMIT,portfolioUuid,userId);
								}
							}
						}
						catch(Exception ex) {}
						//
						try
						{
							if(metadataWadNode.getAttributes().getNamedItem("seeresroles")!=null)
							{
								StringTokenizer tokens = new StringTokenizer(metadataWadNode.getAttributes().getNamedItem("seeresroles").getNodeValue(), " ");
								while (tokens.hasMoreElements())
								{

									nodeRole = tokens.nextElement().toString();
									credential.postGroupRight(nodeRole,uuid,Credential.READ,portfolioUuid,userId);
								}
							}
						}
						catch(Exception ex) {}

						try
						{
							if(metadataWadNode.getAttributes().getNamedItem("delresroles")!=null)
							{
								StringTokenizer tokens = new StringTokenizer(metadataWadNode.getAttributes().getNamedItem("delresroles").getNodeValue(), " ");
								while (tokens.hasMoreElements())
								{

									nodeRole = tokens.nextElement().toString();
									credential.postGroupRight(nodeRole,uuid,Credential.DELETE,portfolioUuid,userId);
								}
							}
						}
						catch(Exception ex) {}

						try
						{
							if(metadataWadNode.getAttributes().getNamedItem("editresroles")!=null)
							{
								StringTokenizer tokens = new StringTokenizer(metadataWadNode.getAttributes().getNamedItem("editresroles").getNodeValue(), " ");
								while (tokens.hasMoreElements())
								{

									nodeRole = tokens.nextElement().toString();
									credential.postGroupRight(nodeRole,uuid,Credential.WRITE,portfolioUuid,userId);
								}
							}
						}
						catch(Exception ex) {}

						try
						{
							if(metadataWadNode.getAttributes().getNamedItem("submitresroles")!=null)
							{
								StringTokenizer tokens = new StringTokenizer(metadataWadNode.getAttributes().getNamedItem("submitresroles").getNodeValue(), " ");
								while (tokens.hasMoreElements())
								{

									nodeRole = tokens.nextElement().toString();
									credential.postGroupRight(nodeRole,uuid,Credential.SUBMIT,portfolioUuid,userId);
								}
							}
						}
						catch(Exception ex) {}

						try
						{
							Node actionroles = metadataWadNode.getAttributes().getNamedItem("actionroles");
							if(actionroles!=null)
							{
								/// Format pour l'instant: actionroles="sender:1,2;responsable:4"
								StringTokenizer tokens = new StringTokenizer(actionroles.getNodeValue(), ";");
								while (tokens.hasMoreElements())
								{
									nodeRole = tokens.nextElement().toString();
									StringTokenizer data = new StringTokenizer(nodeRole, ":");
									String role = data.nextElement().toString();
									String actions = data.nextElement().toString();
									credential.postGroupRight(role,uuid,actions,portfolioUuid,userId);
								}
							}
						}
						catch(Exception ex) {}


					}
					else if(children.item(i).getNodeName().equals("metadata-epm"))
					{
						metadataEpm = DomUtils.getNodeAttributesString(children.item(i));
					}
					else if(children.item(i).getNodeName().equals("metadata"))
					{
						String tmpSharedRes = "";
						try
						{
							tmpSharedRes = children.item(i).getAttributes().getNamedItem("sharedResource").getNodeValue();
						}
						catch(Exception ex) {}

						String tmpSharedNode = "";
						try
						{
							tmpSharedNode = children.item(i).getAttributes().getNamedItem("sharedNode").getNodeValue();
						}
						catch(Exception ex)   {}

						String tmpSharedNodeRes = "";
						try
						{
							tmpSharedNodeRes = children.item(i).getAttributes().getNamedItem("sharedNodeResource").getNodeValue();
						}
						catch(Exception ex)   {}

						try
						{
							semanticTag = children.item(i).getAttributes().getNamedItem("semantictag").getNodeValue();
							/*
	                    else if(children.item(i).getNodeName().equals("semanticTag"))
	                    {
	                        semanticTag = DomUtils.getInnerXml(children.item(i));
	                    }
	                    //*/
						}
						catch(Exception ex)   {}

						if(tmpSharedRes.equalsIgnoreCase("y"))
							sharedRes = 1;
						if(tmpSharedNode.equalsIgnoreCase("y"))
							sharedNode = 1;
						if(tmpSharedNodeRes.equalsIgnoreCase("y"))
							sharedNodeRes = 1;

						metadata = DomUtils.getNodeAttributesString(children.item(i));
					}
					// On vï¿½rifie si l'enfant n'est pas un ï¿½lement de type code, label ou descr
					else if(children.item(i).getNodeName().equals("label"))
					{
						label = DomUtils.getInnerXml(children.item(i));
					}
					else if(children.item(i).getNodeName().equals("code"))
					{
						code = DomUtils.getInnerXml(children.item(i));
					}
					else if(children.item(i).getNodeName().equals("description"))
					{
						descr = DomUtils.getInnerXml(children.item(i));
					}
					else if(children.item(i).getAttributes()!=null)
					{
						if(children.item(i).getAttributes().getNamedItem("id")!=null)
						{

							if(nodeChildrenUuid==null) nodeChildrenUuid = "";
							if(j>0) nodeChildrenUuid += ",";
							nodeChildrenUuid += children.item(i).getAttributes().getNamedItem("id").getNodeValue().toString();

							j++;
						}
					}
				}
			}
		}
		catch(Exception ex)
		{
			// Pas d'enfants
			ex.printStackTrace();
		}

		//System.out.println(uuid+":"+node.getNodeName()+":"+parentUuid+":"+nodeChildrenUuid);

		// Si on est au debut de l'arbre, on stocke la dï¿½finition du portfolio
		// dans la table portfolio
		if(uuid!=null && node.getParentNode()!=null)
		{
			if(node.getNodeName().equals("asmRoot"))
			{
				// On retrouve le code cach� dans une ressource. blegh
				NodeList childs = node.getChildNodes();
				for( int k=0; k<childs.getLength(); ++k )
				{
					Node child = childs.item(k);
					if( "asmResource".equals(child.getNodeName()) )
					{
						NodeList grandchilds = child.getChildNodes();
						for( int l=0; l<grandchilds.getLength(); ++l )
						{
							Node gc = grandchilds.item(l);
							if( "code".equals(gc.getNodeName()) )
							{
								code = DomUtils.getInnerXml(gc);
								break;
							}
						}
					}
					if( code != null ) break;
				}
			}
			else if(portfolioUuid==null) throw new Exception("Il manque la balise asmRoot !!");
		}

		// Si on instancie un portfolio ï¿½ partir d'un modï¿½le
		// Alors on gï¿½re les share*
		if(portfolioModelId!=null)
		{
			if(sharedNode==1)
			{
				sharedNodeUuid = originUuid;
			}

		}
		else modelNodeUuid = null;


		if(uuid!=null && !node.getNodeName().equals("portfolio") && !node.getNodeName().equals("asmResource"))
			returnValue = insertMySqlNode(uuid, parentUuid, nodeChildrenUuid, asmType, xsiType,
					sharedRes, sharedNode, sharedNodeRes, sharedResUuid, sharedNodeUuid,sharedNodeResUuid, metadata, metadataWad, metadataEpm,
					semtag, semanticTag,
					label, code, descr, format, ordrer ,userId, portfolioUuid);

		// Si le parent a ï¿½tï¿½ forcï¿½, cela veut dire qu'il faut mettre ï¿½ jour les enfants du parent
		//TODO
		// MODIF : On le met ï¿½ jour tout le temps car dans le cas d'un POST les uuid ne sont pas connus ï¿½ l'avance
		//if(forcedUuidParent!=null)
		updateMysqlNodeChildren(forcedUuidParent);

		// Si le noeud est de type asmResource, on stocke le innerXML du noeud
		if(node.getNodeName().equals("asmResource"))
		{
			if(portfolioModelId!=null)
			{
				if(xsiType.equals("nodeRes") && sharedNodeResParent==1)
				{
					sharedNodeResUuid = originUuid;
					insertMysqlResource(sharedNodeResUuid,parentUuid,xsiType,DomUtils.getInnerXml(node),portfolioModelId, sharedNodeResParent,sharedResParent, userId);
				}
				else if(!xsiType.equals("context") && !xsiType.equals("nodeRes") && sharedResParent==1)
				{

					sharedResUuid = originUuid;
					insertMysqlResource(sharedResUuid,parentUuid,xsiType,DomUtils.getInnerXml(node),portfolioModelId, sharedNodeResParent,sharedResParent, userId);
				}
				else
				{
					insertMysqlResource(uuid,parentUuid,xsiType,DomUtils.getInnerXml(node),portfolioModelId, sharedNodeResParent,sharedResParent, userId);
				}
			}
			else insertMysqlResource(uuid,parentUuid,xsiType,DomUtils.getInnerXml(node),portfolioModelId, sharedNodeResParent,sharedResParent, userId);

		}

		// On vï¿½rifie enfin si on a importï¿½ des ressources fichiers dont l'UID correspond
		// pour remplacer l'UID d'origine par l'UID generï¿½
		if(portfolioRessourcesImportUuid.size()>0)
		{
			for(int k=0;k<portfolioRessourcesImportUuid.size();k++)
			{
				if(portfolioRessourcesImportUuid.get(k).equals(originUuid))
				{
					String portfolioRessourcesDestPath = portfolioRessourcesImportPath.get(k).replace(originUuid, uuid);
					File f = new File(portfolioRessourcesImportPath.get(k));
					f.renameTo(new File(portfolioRessourcesDestPath));
					//System.out.println();
					updateMysqlFileUuid(originUuid,uuid);
				}

			}
		}

		// On reparcourt ensuite les enfants pour continuer la recursivitï¿½
		//		if(children!=null && sharedNode!=1)
		if( children!=null )
		{
			int k=0;
			for(int i=0;i<children.getLength();i++)
			{
				Node child = children.item(i);
				if(child.getAttributes()!=null)
				{
					String nodeName = child.getNodeName();
					if("asmRoot".equals(nodeName) ||
							"asmStructure".equals(nodeName) ||
							"asmUnit".equals(nodeName) ||
							"asmUnitStructure".equals(nodeName) ||
							"asmUnitContent".equals(nodeName) ||
							"asmContext".equals(nodeName) )
					{
						//System.out.println("uid="+uuid+":"+",enfant_uuid="+children.item(i).getAttributes().getNamedItem("id")+",ordre="+k);
						writeNode(child,portfolioUuid,portfolioModelId,userId,k,null,uuid,sharedRes,sharedNodeRes,isPut);
						k++;
					}
					else if( "asmResource".equals(nodeName) ) // Les asmResource pose probl�me dans l'ordre des noeuds
					{
						writeNode(child,portfolioUuid,portfolioModelId,userId,k,null,uuid,sharedRes,sharedNodeRes,isPut);
					}
				}
			}
		}

		return uuid;
	}

	@Override
	public void writeLog(String url, String method, String headers, String inBody, String outBody, int code)
	{
		insertMySqlLog(url,method,headers,inBody, outBody, code);
	}

	@Override
	public Object putFile(String nodeUuid, String lang,String fileName, String destDirectory,String type, String extension, int size, byte[] fileBytes, int userId) throws Exception
	{
		String portfolioUuid = this.getPortfolioUuidByNodeUuid(nodeUuid);
		// Cas ou on utilise la methode postPortfolioZip :
		// On a generï¿½ un uuid preliminaire au moment du dezippage, mais les noeuds ne sont pas encore importï¿½s
		// c'est donc lui qu'on utilise
		if(portfolioUuid==null) portfolioUuid=this.portfolioUuidPreliminaire;

		this.writeFileToDatastore(portfolioUuid,nodeUuid,lang,extension,fileBytes,destDirectory);

		return updateMysqlFile(nodeUuid,lang,fileName,type,extension,size,null, userId);
	}

	public boolean writeFileToDatastore(String portfolioUuid,String nodeUuid,String lang,String extension,byte[] fileBytes,String destDirectory) throws IOException
	{
		String localFileName = destDirectory+portfolioUuid+File.separator+nodeUuid+"_"+lang+"."+extension;
		String localFileNameImgMini = destDirectory+portfolioUuid+File.separator+nodeUuid+"_"+lang+"_Mini."+extension;

		//InputStream docBlob = blob.getBinaryStream();
		File destDirectoryFile = new File(destDirectory);
		//System.out.println(outsideDir);
		// if the directory does not exist, create it
		if (!destDirectoryFile.exists())
		{
			destDirectoryFile.mkdir();
		}

		File portfolioDirectoryFile = new File(destDirectory+portfolioUuid+File.separator);
		//System.out.println(outsideDir);
		// if the directory does not exist, create it
		if (!portfolioDirectoryFile.exists())
		{
			portfolioDirectoryFile.mkdir();
		}

		// Si le fichier existe dï¿½jï¿½ (cas du dezippage) on ne le recree pas
		File localFile = new File(localFileName);
		if (!localFile.exists())
		{
			FileOutputStream fos = new FileOutputStream(localFileName);
			try {
				fos.write(fileBytes);
			}
			catch(Exception ex)
			{
				ex.printStackTrace();
			}
			finally {
				fos.close();
			}
		}

		// Cas d'une image : on produit la miniature
		if (extension.equalsIgnoreCase("png")
				|| extension.equalsIgnoreCase("jpeg")
				|| extension.equalsIgnoreCase("gif")
				|| extension.equalsIgnoreCase("iso")
				|| extension.equalsIgnoreCase("jpg")
				|| extension.equalsIgnoreCase("jpe")
				|| extension.equalsIgnoreCase("bmp")
				|| extension.equalsIgnoreCase("pict"))
		{
			PictureUtils.resizeImage(localFileName, localFileNameImgMini, extension);
		}
		return true;
	}

	@Override
	public Object getFile(String nodeUuid,String lang)
	{
		return getMysqlFile(nodeUuid,lang);
	}

	@Override
	public Object putPortfolioConfiguration(String portfolioUuid, Boolean portfolioActive, Integer userId)
	{
		if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		return updateMysqlPortfolioConfiguration(portfolioUuid,
				portfolioActive);
	}

	private Object updateMysqlPortfolioConfiguration(String portfolioUuid, Boolean portfolioActive)
	{
		String sql = "";
		PreparedStatement st;

		try
		{
			sql  = "UPDATE  portfolio SET active = ? WHERE portfolio_id  = uuid2bin(?) ";

			st = connection.prepareStatement(sql);
			Integer active = portfolioActive ? 1 : 0;
			st.setInt(1,active);
			st.setString(2,portfolioUuid);

			return st.executeUpdate();
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			return null;
		}
	}

	@Override
	public String getMysqlUserUid(String login) throws Exception
	{
		PreparedStatement st;
		String sql;
		ResultSet res;

		try
		{
			sql = "SELECT userid FROM credential WHERE login = ? ";
			st = connection.prepareStatement(sql);
			st.setString(1, login);
			res = st.executeQuery();
			res.next();
			return res.getString("userid");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			return null;
		}
	}

	@Override
	public String[] postCredential(String login, String password, Integer UserId) throws ServletException, IOException
	{
		try{
			return credential.doPost(login, password);
		}catch (ServletException e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public void getCredential(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		try{
			credential.doGet(request, response);
		}catch (ServletException e) {
			e.printStackTrace();
		}
	}

	@Override
	public String getUserUidByTokenAndLogin(String login, String token) throws Exception
	{
		try{
			return credential.getMysqlUserUidByTokenAndLogin(login, token);
		}catch (ServletException e) {
			e.printStackTrace();
			return null;
		}

	}

	@Override
	public boolean postNodeRight(int userId, String nodeUuid, int grid, String xml)
	{
		boolean status = false;
		try
		{
			/// On devrait probablement parser le xml avant.
			Document doc = DomUtils.xmlString2Document(xml, new StringBuffer());
			Element node = doc.getDocumentElement();
			NamedNodeMap att = node.getAttributes();
			ArrayList<Integer> valuesInt = new ArrayList<Integer>();
			ArrayList<String> valuesStr = new ArrayList<String>();

			String sql = "UPDATE group_rights SET ";

			for( int i=0; i<att.getLength(); ++i )
			{
				Node at = att.item(i);
				String name = at.getNodeName();
				String value = at.getNodeValue();
				if( "rd".equals(name) )
				{
					sql += "RD=?";
					valuesInt.add(Integer.parseInt(value));
				}
				else if("wr".equals(name))
				{
					sql += "WR=?";
					valuesInt.add(Integer.parseInt(value));
				}
				else if("dl".equals(name))
				{
					sql += "DL=?";
					valuesInt.add(Integer.parseInt(value));
				}
				else if("sb".equals(name))
				{
					sql += "SB=?";
					valuesInt.add(Integer.parseInt(value));
				}
				else if("ad".equals(name))
				{
					sql += "AD";
					valuesInt.add(Integer.parseInt(value));
				}
				if( i<att.getLength()-1 )
					sql +=",";
			}
			for( int i=0; i<att.getLength(); ++i )
			{
				Node at = att.item(i);
				String name = at.getNodeName();
				String value = at.getNodeValue();
				if( "types".equals(name) )
				{
					sql += "types_id=?";
					valuesStr.add(value);
				}
				else if("rules".equals(name))
				{
					sql += "rules_id=?";
					valuesStr.add(value);
				}
				else if("notify".equals(name))
				{
					sql += "notify_roles=?";
					valuesStr.add(value);
				}
				if( i<att.getLength()-1 )
					sql +=",";
			}

			sql += " WHERE grid=? AND id=uuid2bin(?)";

			int size = valuesInt.size()+valuesStr.size()+1;
			/*
			for( int i=0; i<valuesInt.size(); ++i )
			{
				sql += "?,";
			}
			for( int i=0; i<valuesStr.size(); ++i )
			{
				sql += "?,";
			}
			sql += "?,uuid2bin(?))";
			//*/

			PreparedStatement stInsert = connection.prepareStatement(sql);
			int index = 1;
			Iterator<Integer> iterAtt = valuesInt.iterator();
			while( iterAtt.hasNext() )
			{
				int v = iterAtt.next();
				stInsert.setInt(index, v);
				++index;
			}
			Iterator<String> iterStr = valuesStr.iterator();
			while( iterStr.hasNext() )
			{
				String v = iterStr.next();
				stInsert.setString(index, v);
				++index;
			}
			stInsert.setInt(size, grid);
			stInsert.setString(size+1, nodeUuid);
			stInsert.executeUpdate();

			status = true;
		}
		catch( Exception e )
		{
			e.printStackTrace();
		}
		finally
		{
			status = true;
			try
			{
				connection.close();
			}
			catch( SQLException e )
			{
				e.printStackTrace();
			}
		}

		return status;
	}

	@Override
	public Object postGroup(String in, int userId) throws Exception
	{
		if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		String result = null;
		Integer  grid = 0;
		int  owner = 0;
		String  label = null;

		//On prepare les requetes SQL
		PreparedStatement stInsert;
		String sqlInsert;

		//On recupere le body
		Document doc = DomUtils.xmlString2Document(in, new StringBuffer());
		Element etu = doc.getDocumentElement();

		//On verifie le bon format
		if(etu.getNodeName().equals("group"))
		{
			//On recupere les attributs
			try{
				if(etu.getAttributes().getNamedItem("grid")!=null)
				{
					grid = Integer.parseInt(etu.getAttributes().getNamedItem("grid").getNodeValue());
				}else{
					grid = null;
				}
			}catch(Exception ex) {}

			try{
				if(etu.getAttributes().getNamedItem("owner")!=null)
				{
					owner = Integer.parseInt(etu.getAttributes().getNamedItem("owner").getNodeValue());
				}
			}catch(Exception ex) {}

			try{
				if(etu.getAttributes().getNamedItem("label")!=null)
				{
					label = etu.getAttributes().getNamedItem("label").getNodeValue();
				}
			}catch(Exception ex) {}

		}else{
			result = "Erreur lors de la recuperation des attributs du groupe dans le XML";
		}


		//On ajoute le groupe dans la base de donnees
		sqlInsert = "REPLACE INTO group_info(grid, owner, label) VALUES (?, ?, ?)";
		if (dbserveur.equals("oracle")){
			sqlInsert = "MERGE INTO group_info d using (SELECT ? grid,? owner,? label from dual) s ON (1=2) WHEN NOT MATCHED THEN INSERT (d.grid, d.owner, d.label) values (s.grid, s.owner, s.label)";
		}
		stInsert = connection.prepareStatement(sqlInsert);
		stInsert.setInt(1, grid);
		stInsert.setInt(2, owner);
		stInsert.setString(3, label);
		stInsert.executeUpdate();

		//On renvoie le body pour qu'il soit stockï¿½ dans le log
		result = "<group ";
		result += DomUtils.getXmlAttributeOutputInt("grid", grid)+" ";
		result += DomUtils.getXmlAttributeOutputInt("owner", owner)+" ";
		result += DomUtils.getXmlAttributeOutput("label", label)+" ";
		result += ">";
		result += "</group>";


		return result;
	}

	@Override
	public String getPortfolioRights(int userId, String portfolio) throws Exception
	{
		if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		String result = "";
		ResultSet res;

		DocumentBuilderFactory documentBuilderFactory =DocumentBuilderFactory.newInstance();
		DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
		Document document = documentBuilder.newDocument();

		Element gri = document.createElement("rights");
		document.appendChild(gri);

		Element node = null;

		try
		{
			String sql = "SELECT gri.grid, bin2uuid(id) as id, RD, WR, DL, SB, AD, types_id, rules_id, notify_roles " +
					"FROM group_right_info gri " +
					"LEFT JOIN group_rights gr ON gri.grid=gr.grid " +
					"WHERE gri.portfolio_id=uuid2bin(?) ORDER BY id";
			PreparedStatement st = connection.prepareStatement(sql);
			st.setString(1, portfolio);
			res = st.executeQuery();

			String previd = "DUMMY";
			while( res.next() )
			{
				int grid = res.getInt("grid");
				String id = res.getString("id");
				if( null == id ) id = "";
				int rd = res.getInt("RD");
				int wr = res.getInt("WR");
				int dl = res.getInt("DL");
				int sb = res.getInt("SB");
				int ad = res.getInt("AD");
				String types = res.getString("types_id");
				String rules = res.getString("rules_id");
				String notify = res.getString("notify_roles");

				if( !previd.equals(id) )
				{
					node = document.createElement("node");
					node.setAttribute("id", id);
					gri.appendChild(node);

					previd = id;
				}

				Element gridnode = document.createElement("grid");
				gridnode.setAttribute("id", Integer.toString(grid));
				gridnode.setAttribute("rd", Integer.toString(rd));
				gridnode.setAttribute("wr", Integer.toString(wr));
				gridnode.setAttribute("dl", Integer.toString(dl));
				gridnode.setAttribute("sb", Integer.toString(sb));
				gridnode.setAttribute("ad", Integer.toString(ad));
				gridnode.setAttribute("types_id", types);
				gridnode.setAttribute("rules_id", rules);
				gridnode.setAttribute("notify_roles", notify);

				node.appendChild(gridnode);
			}

			StringWriter stw = new StringWriter();
			Transformer serializer = TransformerFactory.newInstance().newTransformer();
			serializer.transform(new DOMSource(document), new StreamResult(stw));
			result = stw.toString();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		return result;
	}

	@Override
	public boolean postGroupsUsers(int userId, int groupId)
	{
		PreparedStatement stInsert;
		String sqlInsert;

		if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		try
		{
			sqlInsert = "REPLACE INTO group_user(gid, userid) VALUES (?, ?)";
			if (dbserveur.equals("oracle")){
				sqlInsert = "MERGE INTO group_user d using (SELECT ? gid,? userid FROM DUAL) s ON (d.gid=s.gid AND d.userid=s.userid) WHEN NOT MATCHED THEN INSERT (d.gid, d.userid) VALUES (s.gid, s.userid)";
			}
			stInsert = connection.prepareStatement(sqlInsert);
			stInsert.setInt(1, groupId);
			stInsert.setInt(2, userId);
			stInsert.executeUpdate();
			return true;
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			return false;
		}
	}

	@Override
	public boolean postRightGroup(int groupRightId, int groupId, Integer userId)
	{
		PreparedStatement stUpdate;
		String sqlUpdate;

		if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		try
		{
			sqlUpdate = "UPDATE group_info SET grid=? WHERE gid=?";
			stUpdate = connection.prepareStatement(sqlUpdate);
			stUpdate.setInt(1, groupRightId);
			stUpdate.setInt(2, groupId);
			stUpdate.executeUpdate();
			return true;
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			return false;

		}
	}

	@Override
	public Object deleteUsers(Integer userId,Integer userId2)
	{
		if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		int res = deleteMysqlUsers(userId2);

		return res;
	}

	private int deleteMysqlUsers(Integer userId)
	{
		String sql = "";
		PreparedStatement st;
		//try
		//{
		//if(credential.getPortfolioRight(userId, groupId, portfolioUuid, Credential.DELETE))
		//{

		try {

			sql  = " DELETE FROM credential WHERE userid=? ";
			st = connection.prepareStatement(sql);
			st.setInt(1, userId);
			st.executeUpdate();

			sql  = " DELETE FROM group_user WHERE userid=?";
			st = connection.prepareStatement(sql);
			st.setInt(1, userId);

			return 0;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return 1;
		}
	}

	@Override
	public Object deleteGroupRights(Integer groupId, Integer groupRightId, Integer userId)
	{
		if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		int res = deleteMysqlGroupRights(groupId, groupRightId);

		return res;
	}

	private Integer deleteMysqlGroupRights(Integer groupId, Integer groupRightId)
	{
		String sql = "";
		PreparedStatement st;


		try {
			sql  = " DELETE FROM group_info WHERE gid=? AND grid=? ";
			st = connection.prepareStatement(sql);
			st.setInt(1, groupId);
			st.setInt(2, groupRightId);
			st.executeUpdate();

			return 0;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return 1;
		}
	}

	@Override
	public Object postPortfolioZip(MimeType mimeType, MimeType mimeType2, HttpServletRequest httpServletRequest, int userId, int groupId, String modelId) throws FileNotFoundException, IOException
	{
		if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		DataInputStream inZip = new DataInputStream(httpServletRequest.getInputStream());

		ByteArrayOutputStream byte2 = new ByteArrayOutputStream();
		StringBuffer outTrace = new StringBuffer();

		String portfolioId = null;
		portfolioId = httpServletRequest.getParameter("portfolio");

		String foldersfiles= null;
		String filename;
		String[] xmlFiles;
		String[] allFiles;
		String[] ImgFiles;
		int formDataLength = httpServletRequest.getContentLength();
		byte[] buff = new byte[formDataLength];

		// Recuperation de l'heure ï¿½ laquelle le zip est crï¿½ï¿½
		//Calendar cal = Calendar.getInstance();
		//SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss_S");
		//String now = sdf.format(cal.getTime());

		this.genererPortfolioUuidPreliminaire();

		javax.servlet.http.HttpSession session = httpServletRequest.getSession(true);
		String ppath = session.getServletContext().getRealPath(File.separator);
		String outsideDir =ppath.substring(0,ppath.lastIndexOf(File.separator))+"_files"+File.separator;
		File outsideDirectoryFile = new File(outsideDir);
		System.out.println(outsideDir);
		// if the directory does not exist, create it
		if (!outsideDirectoryFile.exists())
		{
			outsideDirectoryFile.mkdir();
		}

		//Creation du zip
		filename = outsideDir+"xml_"+this.portfolioUuidPreliminaire+".zip";
		FileOutputStream outZip = new FileOutputStream(filename);

		int len;

		while ( (len = inZip.read(buff)) != -1) {
			outZip.write(buff, 0, len);
		}

		inZip.close ();
		outZip.close();

		//-- unzip --
		foldersfiles = unzip(filename,outsideDir+this.portfolioUuidPreliminaire+File.separator);
		//TODO Attention si plusieurs XML dans le fichier
		xmlFiles = findFiles(outsideDir+this.portfolioUuidPreliminaire+File.separator, "xml");
		allFiles = findFiles(outsideDir+this.portfolioUuidPreliminaire+File.separator, null);

		for(int i=0;i<allFiles.length;i++)
		{
			portfolioRessourcesImportPath.add(allFiles[i]);
			String tmpFileName = allFiles[i].substring(allFiles[i].lastIndexOf(File.separator)+1);
			String uuid = tmpFileName.substring(0,tmpFileName.indexOf("_"));
			portfolioRessourcesImportUuid.add(uuid);


			tmpFileName = allFiles[i].substring(allFiles[i].lastIndexOf(File.separator)+1);
			String lang;
			try
			{
				int tmpPos = tmpFileName.indexOf("_");
				lang = tmpFileName.substring(tmpPos+1,tmpPos+3);
			}
			catch(Exception ex)
			{
				lang = "";
			}
			InputStream is = new FileInputStream(allFiles[i]);
			byte b[]=new byte[is.available()];
			is.read(b);
			String extension;
			try
			{
				extension = tmpFileName.substring(tmpFileName.lastIndexOf(".")+1);
			}
			catch(Exception ex)
			{
				extension = null;

			}

			// trop long
			//String tmpMimeType = FileUtils.getMimeType("file://"+allFiles[i]);
			String tmpMimeType = FileUtils.getMimeTypeFromExtension(extension);

			// Attention on initialise la ligne file
			// avec l'UUID d'origine de l'asmContext parent
			// Il sera mis ï¿½ jour avec l'UUID asmContext final dans writeNode
			try
			{
				UUID tmpUuid = UUID.fromString(uuid);

				if(tmpUuid.toString().equals(uuid))
					this.putFile(uuid,lang,tmpFileName,outsideDir,tmpMimeType,extension,b.length,b,userId);
			}
			catch(Exception ex)
			{
				// Le nom du fichier ne commence pas par un UUID,
				// ce n'est donc pas une ressource
			}
		}

		//TODO Supprimer le zip quand ï¿½a fonctionnera bien

		//--- Read xml fileL ----
		for(int i=0;i<xmlFiles.length;i++)
		{
			BufferedReader br = new BufferedReader(new FileReader(new File(xmlFiles[i])));
			String line;
			StringBuilder sb = new StringBuilder();

			while((line=br.readLine())!= null){
				sb.append(line.trim());
			}
			String xml = "?";
			xml = sb.toString();

			if(xml.contains("<portfolio id="))
			{
				try {
					Object returnValue = postPortfolio(new MimeType("text/xml"), new MimeType("text/xml"), xml, userId, groupId, null);
					return returnValue;
				} catch (MimeTypeParseException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

		}


		return false;
	}

	public static String unzip(String zipFile,String destinationFolder) throws FileNotFoundException, IOException
	{
		String folder ="";
		File zipfile = new File(zipFile);
		ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipfile)));

		ZipEntry ze = null;
		try {
			while((ze = zis.getNextEntry()) != null){

				//folder = zipfile.getCanonicalPath().substring(0, zipfile.getCanonicalPath().length()-4)+"/";
				folder = destinationFolder;
				File f = new File(folder, ze.getName());


				if (ze.isDirectory()) {
					f.mkdirs();
					continue;
				}

				f.getParentFile().mkdirs();
				OutputStream fos = new BufferedOutputStream(new FileOutputStream(f));
				try {
					try {
						final byte[] buf = new byte[8192];
						int bytesRead;
						while (-1 != (bytesRead = zis.read(buf)))
							fos.write(buf, 0, bytesRead);
					}
					finally {
						fos.close();
					}
				}
				catch (final IOException ioe) {
					f.delete();
					throw ioe;
				}
			}
		}
		finally {
			zis.close();
		}
		return folder;
	}

	public static String[] findFiles(String directoryPath, String id)
	{
		//========================================================================
		int i,j;
		if(id==null) id = "";
		File directory = new File(directoryPath);
		File[] subfiles = directory.listFiles();
		int nbFilesFinded = 0;
		for(i=0 ; i<subfiles.length; i++)
			if(subfiles[i].getName().contains(id) || id.equals(""))
				nbFilesFinded++;
		String[] result = new String[nbFilesFinded];
		j=0;
		for(i=0 ; i<subfiles.length; i++){
			if(subfiles[i].getName().contains(id) || id.equals("")){
				result[j] = directoryPath+subfiles[i].getName();
				j++;
			}
		}
		return result;
	}

	@Override
	public Object postUser(String in, int userId) throws Exception
	{
		if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		String result = null;
		String  login = null;
		String  firstname = null;
		String  lastname = null;
		String  label = null;
		String  password = null;
		String active = "1";
		Integer  uuid = 0;
		Integer newId = 0;

		//On prepare les requetes SQL
		PreparedStatement stInsert;
		String sqlInsert;

		//On recupere le body
		Document doc = DomUtils.xmlString2Document(in, new StringBuffer());
		Element etu = doc.getDocumentElement();

		//On verifie le bon format
		if(etu.getNodeName().equals("user"))
		{
			//On recupere les attributs
			try{
				if(etu.getAttributes().getNamedItem("uid")!=null)
				{
					login = etu.getAttributes().getNamedItem("uid").getNodeValue();

					if (getMysqlUserUid(login) != null){
						uuid = Integer.parseInt(getMysqlUserUid(login));
					}
				}
			}catch(Exception ex) {}


			try{
				if(etu.getAttributes().getNamedItem("firstname")!=null)
				{
					firstname = etu.getAttributes().getNamedItem("firstname").getNodeValue();
				}
			}catch(Exception ex) {}

			try{
				if(etu.getAttributes().getNamedItem("lastname")!=null)
				{
					lastname = etu.getAttributes().getNamedItem("lastname").getNodeValue();
				}
			}catch(Exception ex) {}

			try{
				if(etu.getAttributes().getNamedItem("label")!=null)
				{
					label = etu.getAttributes().getNamedItem("label").getNodeValue();
				}
			}catch(Exception ex) {}

			try{
				if(etu.getAttributes().getNamedItem("password")!=null)
				{
					password = etu.getAttributes().getNamedItem("password").getNodeValue();
				}
			}catch(Exception ex) {}
			try{
				if(etu.getAttributes().getNamedItem("active")!=null)
				{
					active = etu.getAttributes().getNamedItem("active").getNodeValue();
				}
			}catch(Exception ex) {}

		}else{
			result = "Erreur lors de la recuperation des attributs de l'utilisateur dans le XML";
		}


		//On ajoute l'utilisateur dans la base de donnees
		if (etu.getAttributes().getNamedItem("firstname")!=null && etu.getAttributes().getNamedItem("lastname")!=null && etu.getAttributes().getNamedItem("label")==null){

			sqlInsert = "REPLACE INTO credential(userid, login, display_firstname, display_lastname, password, active) VALUES (?, ?, ?, ?, UNHEX(SHA1(?)),?)";
			stInsert = connection.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS);
			if (dbserveur.equals("oracle")){
				sqlInsert = "MERGE INTO credential d USING (SELECT ? userid,? login,? display_firstname,? display_lastname,crypt(?) password,? active FROM DUAL) s ON (d.userid=s.userid) WHEN MATCHED THEN UPDATE SET d.login=s.login, d.display_firstname = s.display_firstname, d.display_lastname = s.display_lastname, d.password = s.password, d.active = s.active WHEN NOT MATCHED THEN INSERT (d.userid, d.login, d.display_firstname, d.display_lastname, d.password, d.active) VALUES (s.userid, s.login, s.display_firstname, s.display_lastname, s.password, s.active)";
				stInsert = connection.prepareStatement(sqlInsert, new String[]{"userid"});
			}
			stInsert.setInt(1, uuid);
			stInsert.setString(2, login);
			stInsert.setString(3, firstname);
			stInsert.setString(4, lastname);
			stInsert.setString(5, password);
			stInsert.setString(6, active);
			stInsert.executeUpdate();
		}
		else {
			sqlInsert = "REPLACE INTO credential(userid, login, display_firstname, display_lastname, password, active) VALUES (?, ?, ?, ?, UNHEX(SHA1(?)),?)";
			stInsert = connection.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS);
			if (dbserveur.equals("oracle")){
				sqlInsert = "MERGE INTO credential d USING (SELECT ? userid,? login,? display_firstname,? display_lastname,crypt(?) password,? active FROM DUAL) s ON (d.userid=s.userid) WHEN MATCHED THEN UPDATE SET d.login=s.login, d.display_firstname = s.display_firstname, d.display_lastname = s.display_lastname, d.password = s.password, d.active = s.active WHEN NOT MATCHED THEN INSERT (d.userid, d.login, d.display_firstname, d.display_lastname, d.password, d.active) VALUES (s.userid, s.login, s.display_firstname, s.display_lastname, s.password, s.active)";
				stInsert = connection.prepareStatement(sqlInsert, new String[]{"userid"});
			}
			stInsert.setInt(1, uuid);
			stInsert.setString(2, login);
			stInsert.setString(3, " ");
			stInsert.setString(4, label);
			stInsert.setString(5, password);
			stInsert.setString(6, active);
			stInsert.executeUpdate();
		}

		ResultSet rs = stInsert.getGeneratedKeys();
		if (rs.next()) {
			newId = rs.getInt(1);
		}

		//On renvoie le body pour qu'il soit stockï¿½ dans le log
		result = "<user ";
		result += DomUtils.getXmlAttributeOutput("uid", login)+" ";
		result += DomUtils.getXmlAttributeOutput("firstname", firstname)+" ";
		result += DomUtils.getXmlAttributeOutput("lastname", lastname)+" ";
		result += DomUtils.getXmlAttributeOutput("label", label)+" ";
		result += DomUtils.getXmlAttributeOutput("password", password)+" ";
		result += DomUtils.getXmlAttributeOutputInt("uuid", newId)+" ";
		result += ">";
		result += "</user>";

		return result;
	}

	private ResultSet getMysqlNodeUuidBySemanticTag(String portfolioUuid, String semantictag) throws SQLException
	{
		String sql = "";
		PreparedStatement st;
		String text = "%semantictag=%"+semantictag+"%";

		try
		{
			sql = "SELECT bin2uuid(node_uuid) AS node_uuid, bin2uuid(res_res_node_uuid) AS res_res_node_uuid, node_children_uuid, code, asm_type, label FROM node WHERE portfolio_id = uuid2bin(?) and metadata LIKE ? ";
			//sql = "SELECT bin2uuid(node_uuid) AS node_uuid, bin2uuid(res_res_node_uuid) AS res_res_node_uuid, node_children_uuid, code, asm_type, label FROM node WHERE portfolio_id = uuid2bin('c884bdcd-2165-469b-9939-14376f7f3500') AND metadata LIKE '%semantictag=%competence%'";
			st = connection.prepareStatement(sql);

			st.setString(1, portfolioUuid);
			st.setString(2, text);

			return st.executeQuery();
		}
		catch(Exception ex)
		{

			ex.printStackTrace();
			return null;
		}
	}


	private ResultSet getNodeuuidMysqlBySemanticTag(MimeType mimeType, String portfolioUuid, String semantictag, int userId, int groupId) throws SQLException
	{
		String sql = "";
		PreparedStatement st;

		try
		{

			sql = "SELECT bin2uuid(node_uuid) AS node_uuid FROM node WHERE portfolio_id = uuid2bin(?) and  semantictag = ?";
			st = connection.prepareStatement(sql);
			st.setString(1, portfolioUuid);
			st.setString(2, semantictag);
			return st.executeQuery();
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			return null;
		}
	}

	@Override
	public String getGroupRightsInfos(int userId, String portfolioId)
	{
		if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		String sql = "";
		ResultSet rs = null;
		PreparedStatement st = null;
		String result = "";

		try
		{
			sql = "SELECT gr.grid,bin2uuid(id),RD,WR,DL,SB,AD,types_id,rules_id, notify_roles " +
					"FROM group_right_info gri " +
					"LEFT JOIN group_rights gr ON gri.grid=gr.grid " +
					"WHERE portfolio_id=uuid2bin(?) " +
					"ORDER BY gr.grid";
			st = connection.prepareStatement(sql);
			st.setString(1, portfolioId);

			rs = st.executeQuery();

			DocumentBuilderFactory documentBuilderFactory =DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
			Document document = documentBuilder.newDocument();

			Element gri = document.createElement("groupRightsInfos");
			document.appendChild(gri);

			int grid = -1;
			Element gridNode = null;

			while(rs.next())
			{
				int ng = rs.getInt(1);
				if( grid != ng )
				{
					gridNode = document.createElement("grid");
					gridNode.setAttribute("id", Integer.toString(ng));
					gri.appendChild(gridNode);
					grid = ng;
				}

				String id = rs.getString(2);
				int rd = rs.getInt(3);
				int wr = rs.getInt(4);
				int dl = rs.getInt(5);
				int sb = rs.getInt(6);
				int ad = rs.getInt(7);
				String types = rs.getString(8);
				String action = rs.getString(9);
				String notify = rs.getString(10);

				Element node = document.createElement("node");
				gridNode.appendChild(node);

				node.setAttribute("id", id);
				node.setAttribute("RD", Integer.toString(rd));
				node.setAttribute("WR", Integer.toString(wr));
				node.setAttribute("DL", Integer.toString(dl));
				node.setAttribute("SB", Integer.toString(sb));
				node.setAttribute("AD", Integer.toString(ad));

				Element typ = document.createElement("types");
				typ.setTextContent(types);
				Element act = document.createElement("action");
				act.setTextContent(action);
				Element not = document.createElement("notify");
				not.setTextContent(notify);

				node.appendChild(typ);
				node.appendChild(act);
				node.appendChild(not);
			}

			StringWriter stw = new StringWriter();
			Transformer serializer = TransformerFactory.newInstance().newTransformer();
			serializer.transform(new DOMSource(document), new StreamResult(stw));
			result = stw.toString();
		}
		catch( Exception e )
		{
			e.printStackTrace();
		}

		return result;
	}

	@Override
	public String getListUsers(int userId)
	{
		PreparedStatement st;
		String sql;
		ResultSet res;
		String result = "";

		try
		{
			// On recupere d'abord les informations dans la table structures
			sql = "SELECT * FROM credential";
			st = connection.prepareStatement(sql);

			res = st.executeQuery();

			result = "<users>";
			while(res.next())
			{
				result += "<user ";
				result += DomUtils.getXmlAttributeOutput("id", res.getString("userid"))+" ";
				result += ">";
				result += DomUtils.getXmlElementOutput("label", res.getString("login"));
				result += DomUtils.getXmlElementOutput("display_firstname", res.getString("display_firstname"));
				result += DomUtils.getXmlElementOutput("display_lastname", res.getString("display_lastname"));
				result += DomUtils.getXmlElementOutput("email", res.getString("email"));
				result += DomUtils.getXmlElementOutput("active", res.getString("active"));
				result += "</user>";
			}
			result += "</users>";
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		return result;
	}

	/// Retrouve le uid du username
	/// currentUser est l� au cas o� on voudrait limiter l'acc�s
	@Override
	public String getUserID(int currentUser, String username)
	{
		PreparedStatement st;
		ResultSet res = null;
		int result = 0;
		try
		{
			String sql = "SELECT userid FROM credential WHERE login=?";
			st = connection.prepareStatement(sql);
			st.setString(1, username);
			res = st.executeQuery();
			if( res.next())
				result = res.getInt("userid");
			else
				throw new RestWebApplicationException(Status.NOT_FOUND, "User "+username+" not found");
		}
		catch (SQLException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new RestWebApplicationException(Status.NOT_FOUND, "User "+username+" not found");
		}

		return Integer.toString(result);
	}

	@Override
	public String getInfUser(int userId, int userid)
	{
		PreparedStatement st;
		String sql;
		ResultSet res = null;
		String result ="";

		try
		{
			//requetes SQL permettant de recuperer toutes les informations
			//dans la table credential pour un userid(utilisateur) particulier
			sql = "SELECT * FROM credential WHERE userid = ? ";
			st = connection.prepareStatement(sql);
			st.setInt(1, userid);
			res = st.executeQuery();

		}
		catch(Exception ex)
		{
			ex.printStackTrace();
		}
		try {
			if( res.next()){
				//traitement de la réponse, renvoie des données sous forme d'un xml
				result ="<user ";
				try {

					result += DomUtils.getXmlAttributeOutput("id", res.getString("userid"))+" ";
					result += ">";
					result += DomUtils.getXmlElementOutput("username", res.getString("login"));
					result += DomUtils.getXmlElementOutput("firstname", res.getString("display_firstname"));
					result += DomUtils.getXmlElementOutput("lastname", res.getString("display_lastname"));
					result += DomUtils.getXmlElementOutput("email", res.getString("email"));

					if( res.getInt("is_admin") == 1 )
						result += DomUtils.getXmlElementOutput("admin", res.getString("is_admin"));

					result += DomUtils.getXmlElementOutput("active", res.getString("active"));
					result += "</user>";

				} catch (SQLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}else{
				return "User "+userid+" not found";
			}

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new RestWebApplicationException(Status.NOT_FOUND, "User "+userid+" not found");

		}

		return result;
	}

	@Override
	public String getGroupsUser(int userId, int userid)
	{
		PreparedStatement st;
		String sql;
		ResultSet res = null;

		try
		{

			sql = "SELECT * FROM group_user gu, group_info gi, group_right_info gri WHERE userid = ? and gi.gid = gu.gid and gi.grid = gri.grid";
			st = connection.prepareStatement(sql);
			st.setInt(1, userid);
			res = st.executeQuery();
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
		}

		String result = "<profiles>";
		result +="<profile>";
		try {
			while(res.next())
			{
				result +="<group";
				result += DomUtils.getXmlAttributeOutput("id", res.getString("gid"))+" ";
				result += ">";
				result += DomUtils.getXmlElementOutput("label", res.getString("gi.label"));
				result += DomUtils.getXmlElementOutput("role", res.getString("grid.label"));
				result += "</group>";
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		result += "</profile>";
		result += "</profiles>";

		return result;
	}

	@Override
	public String putInfUser(int userId, int userid2, String in) throws SQLException
	{
		String result1 = null;
		Integer  id = 0;
		String  password = null;
		String  email = null;
		String result = null;
		String  username = null;
		String  firstname = null;
		String  lastname = null;
		String  active = null;
		String is_admin = null;

		//On prepare les requetes SQL
		PreparedStatement st;
		String sql;

		//On recupere le body
		Document doc;
		Element infUser = null;
		try {
			doc = DomUtils.xmlString2Document(in, new StringBuffer());
			infUser = doc.getDocumentElement();
		} catch (Exception e) {
			e.printStackTrace();
		}

		NodeList children = null;

		children = infUser.getChildNodes();

		//		if(infUser.getNodeName().equals("users"))
		//		{
		//			for(int i=0;i<children.getLength();i++)
		//			{
		if(infUser.getNodeName().equals("user"))
		{
			//On recupere les attributs

			if(infUser.getAttributes().getNamedItem("id")!=null)
			{
				id = Integer.parseInt(infUser.getAttributes().getNamedItem("id").getNodeValue());
			}else{
				id = null;
			}
			NodeList children2 = null;
			children2 = infUser.getChildNodes();
			for(int y=0;y<children2.getLength();y++)
			{
				if(children2.item(y).getNodeName().equals("username"))
				{
					username = DomUtils.getInnerXml(children2.item(y));

					sql = "UPDATE credential SET login = ? WHERE  userid = ?";

					st = connection.prepareStatement(sql);
					st.setString(1, username);
					st.setInt(2, userid2);
					st.executeUpdate();
				}
				if(children2.item(y).getNodeName().equals("password"))
				{
					password = DomUtils.getInnerXml(children2.item(y));

					sql = "UPDATE credential SET password = UNHEX(SHA1(?)) WHERE  userid = ?";
					if (dbserveur.equals("oracle")){
						sql = "UPDATE credential SET password = crypt(?) WHERE  userid = ?";
					}

					st = connection.prepareStatement(sql);
					st.setString(1, password);
					st.setInt(2, userid2);
					st.executeUpdate();
				}
				if(children2.item(y).getNodeName().equals("firstname"))
				{
					firstname = DomUtils.getInnerXml(children2.item(y));

					sql = "UPDATE credential SET display_firstname = ? WHERE  userid = ?";

					st = connection.prepareStatement(sql);
					st.setString(1, firstname);
					st.setInt(2, userid2);
					st.executeUpdate();
				}
				if(children2.item(y).getNodeName().equals("lastname"))
				{
					lastname = DomUtils.getInnerXml(children2.item(y));

					sql = "UPDATE credential SET display_lastname = ? WHERE  userid = ?";

					st = connection.prepareStatement(sql);
					st.setString(1, lastname);
					st.setInt(2, userid2);
					st.executeUpdate();
				}
				if(children2.item(y).getNodeName().equals("email"))
				{
					email = DomUtils.getInnerXml(children2.item(y));

					sql = "UPDATE credential SET email = ? WHERE  userid = ?";

					st = connection.prepareStatement(sql);
					st.setString(1, email);
					st.setInt(2, userid2);
					st.executeUpdate();
				}
				if(children2.item(y).getNodeName().equals("is_admin"))
				{
					is_admin = DomUtils.getInnerXml(children2.item(y));

					int is_adminInt = Integer.parseInt(is_admin);

					sql = "UPDATE credential SET is_admin = ? WHERE  userid = ?";

					st = connection.prepareStatement(sql);
					st.setInt(1, is_adminInt);
					st.setInt(2, userid2);
					st.executeUpdate();
				}
				if(children2.item(y).getNodeName().equals("active"))
				{
					active = DomUtils.getInnerXml(children2.item(y));

					int activeInt = Integer.parseInt(active);

					sql = "UPDATE credential SET active = ? WHERE  userid = ?";

					st = connection.prepareStatement(sql);
					st.setInt(1, activeInt);
					st.setInt(2, userid2);
					st.executeUpdate();
				}
			}
		}

		result1 = ""+userid2;

		return result1;
	}

	@Override
	public String postUsers(String in, int userId) throws Exception
	{
		if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		String result = null;
		String  username = null;
		String  password = null;
		String  firstname = null;
		String  lastname = null;
		String  email = null;
		String  active = "1";
		int id = 0;

		//On prepare les requetes SQL
		PreparedStatement stInsert;
		String sqlInsert;

		//On recupere le body
		Document doc;

		doc = DomUtils.xmlString2Document(in, new StringBuffer());
		Element users = doc.getDocumentElement();

		NodeList children = null;

		children = users.getChildNodes();
		// On parcourt une première fois les enfants pour récuperer la liste à écrire en base

		//On verifie le bon format
		if(users.getNodeName().equals("users"))
		{
			for(int i=0;i<children.getLength();i++)
			{
				if(children.item(i).getNodeName().equals("user"))
				{
					NodeList children2 = null;
					children2 = children.item(i).getChildNodes();
					for(int y=0;y<children2.getLength();y++)
					{

						if(children2.item(y).getNodeName().equals("username"))
						{
							username = DomUtils.getInnerXml(children2.item(y));
						}
						if(children2.item(y).getNodeName().equals("password"))
						{
							password = DomUtils.getInnerXml(children2.item(y));
						}
						if(children2.item(y).getNodeName().equals("firstname"))
						{
							firstname = DomUtils.getInnerXml(children2.item(y));
						}
						if(children2.item(y).getNodeName().equals("lastname"))
						{
							lastname = DomUtils.getInnerXml(children2.item(y));
						}
						if(children2.item(y).getNodeName().equals("email"))
						{
							email = DomUtils.getInnerXml(children2.item(y));
						}
						if(children2.item(y).getNodeName().equals("active"))
						{
							active = DomUtils.getInnerXml(children2.item(y));
						}

					}
				}
			}
		}else{
			result = "Erreur lors de la recuperation des attributs de l'utilisateur dans le XML";
		}

		//On ajoute l'utilisateur dans la base de donnees
		try {
			sqlInsert = "REPLACE INTO credential(login, display_firstname, display_lastname,email, password, active) VALUES (?, ?, ?, ?, UNHEX(SHA1(?)),?)";
			stInsert = connection.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS);
			if (dbserveur.equals("oracle")){
				  sqlInsert = "INSERT INTO credential(login, display_firstname, display_lastname,email, password, active) VALUES (?, ?, ?, ?, crypt(?),?)";
				  stInsert = connection.prepareStatement(sqlInsert, new String[]{"userid"});
			}

			stInsert.setString(1, username);

			if(firstname == null)
			{
				firstname = " ";
				stInsert.setString(2, firstname);
			}else{
				stInsert.setString(2, firstname);
			}

			if(lastname == null)
			{
				lastname = " ";
				stInsert.setString(3, lastname);
			}else{
				stInsert.setString(3, lastname);
			}

			if(email == null)
			{
				email = " ";
				stInsert.setString(4, email);
			}else{
				stInsert.setString(4, email);
			}

			stInsert.setString(5, password);

			if(active == null)
			{
				active = " ";
				stInsert.setString(6, active);
			}else{
				stInsert.setString(6, active);
			}


			stInsert.executeUpdate();

			ResultSet rs = stInsert.getGeneratedKeys();
			if (rs.next()) {
				id = rs.getInt(1);
			}

		} catch (SQLException e) {
			e.printStackTrace();
		}
		//On renvoie le body pour qu'il soit stocké dans le log
		result = "<users>";

		result += "<user ";
		result += DomUtils.getXmlAttributeOutputInt("id",id);
		result += ">";
		result += DomUtils.getXmlElementOutput("username", username);
		result += DomUtils.getXmlElementOutput("password", password);
		result += DomUtils.getXmlElementOutput("firstname", firstname);
		result += DomUtils.getXmlElementOutput("lastname", lastname);
		result += DomUtils.getXmlElementOutput("email", email);
		result += DomUtils.getXmlElementOutput("active", active);
		result += "</user>";

		result += "</users>";

		return result;

	}

	@Override
	public String[] postCredentialFromXml(String xml, Integer userId) throws ServletException, IOException{


		try{
			String[] returnValue = new String[4];
			Document doc = DomUtils.xmlString2Document(xml, new StringBuffer());
			Element credentialElement = doc.getDocumentElement();


			//On verifie le bon format
			if(credentialElement.getNodeName().equals("credential"))
			{
				String login = DomUtils.getInnerXml(doc.getElementsByTagName("login").item(0));
				String password = DomUtils.getInnerXml(doc.getElementsByTagName("password").item(0));



				String[] credentialInfos =  credential.doPost(login, password);


				if(credentialInfos!=null)
				{
					returnValue[0] = credentialInfos[0];
					returnValue[1] = credentialInfos[1];
					returnValue[2] = "";
					returnValue[3] = credentialInfos[2];

					String login1 = credentialInfos[0];
					ResultSet res = getMySqlUserByLogin(login1);
					returnValue[2] +="<credential>";
					returnValue[2] += DomUtils.getXmlElementOutput("useridentifier", res.getString("login"));
					returnValue[2] += DomUtils.getXmlElementOutput("token", res.getString("token"));
					returnValue[2] += DomUtils.getXmlElementOutput("firstname", res.getString("display_firstname"));
					returnValue[2] += DomUtils.getXmlElementOutput("lastname", res.getString("display_lastname"));
					if( res.getInt("is_admin") == 1 )
						returnValue[2] += DomUtils.getXmlElementOutput("admin", res.getString("is_admin"));
					returnValue[2] += DomUtils.getXmlElementOutput("email",res.getString("email"));
					returnValue[2] +="</credential>";
				}
				return returnValue;
			}
			else return null;
		}catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public ResultSet getMySqlUserByLogin(String login) throws Exception
	{
		PreparedStatement st;
		String sql;
		ResultSet res;

		try
		{

			sql = "SELECT * FROM credential WHERE login = ? ";
			st = connection.prepareStatement(sql);
			st.setString(1, login);
			res = st.executeQuery();
			res.next();
			return res;
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			return null;
		}

	}

	@Override
	public int deleteCredential(int userId) {
		// TODO Auto-generated method stub

		if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		int res = updateMysqlCredentialToken(userId, null);

		return res;
	}

	@Override
	public Object getNodeWithXSL(MimeType mimeType, String nodeUuid,
			String xslFile, int userId, int groupId) {

		String xml;
		try {
			xml = getNodeXmlOutput(nodeUuid,true,null,userId, groupId, null,true).toString();
			String param[] = new String[0];
			String paramVal[] = new String[0];
			return DomUtils.processXSLTfile2String( DomUtils.xmlString2Document(xml, new StringBuffer()), xslFile, param, paramVal, new StringBuffer());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}


	}

	@Override
	public Object deleteUser(int userid, int userId1) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object postNodeFromModelBySemanticTag(MimeType inMimeType, String parentNodeUuid, String semanticTag,int userId, int groupId) throws Exception
	{
		String portfolioUid = getPortfolioUuidByNodeUuid(parentNodeUuid);

		String result = null;
		String portfolioModelId = getPortfolioModelUuid(portfolioUid);


		String xml = getNodeBySemanticTag(inMimeType, portfolioModelId,
				semanticTag,userId, groupId).toString();

		ResultSet res = getMysqlOtherNodeUuidByPortfolioModelUuidBySemanticTag(portfolioModelId, semanticTag);
		res.next();
		// C'est le noeud obtenu dans le modele indiqu� par la table de correspondance
		String otherParentNodeUuid = res.getString("node_uuid");

		return postNode(inMimeType, otherParentNodeUuid, xml,userId, groupId);
	}
	public ResultSet getMysqlGroupsPortfolio(String portfolioUuid)
	{
		PreparedStatement st;
		String sql;

		try
		{
			// On recupere d'abord les informations dans la table structures

			sql = "SELECT gi.gid, gi.grid,gi.label as g_label, gri.label as gri_label  FROM  group_right_info gri , group_info gi  WHERE   gri.grid = gi.grid  AND gri.portfolio_id = uuid2bin(?) ";

			sql += "  ORDER BY g_label ASC ";
			st = connection.prepareStatement(sql);
			st.setString(1, portfolioUuid);

			return st.executeQuery();



			/*  if(outMimeType.getSubType().toString().equals("xml"))
		                                          return getNodeXmlOutput(resNodes);
		                                  else
	                                          return null;
			 */


			//sql = "SELECT * FROM structures WHERE node_uuid = ? ";


		}
		catch (Exception e)
		{
			e.printStackTrace();
		}


		return null;
	}

	@Override
	public String getGroupsPortfolio(String portfolioUuid, int userId)
	{
		NodeRight nodeRight = credential.getPortfolioRight(userId,0, portfolioUuid, Credential.READ);
		if( !nodeRight.read )
			return null;

		ResultSet res = getMysqlGroupsPortfolio(portfolioUuid);

		String result = "<groups>";
		try {
			while(res.next())
			{
				result += "<group ";
				result += DomUtils.getXmlAttributeOutput("id", res.getString("gid"))+" ";
				//result += DomUtils.getXmlAttributeOutput("owner", res.getString("owner"))+" ";
				result += DomUtils.getXmlAttributeOutput("templateId", res.getString("grid"))+" ";
				result += ">";
				result += DomUtils.getXmlElementOutput("groupid", res.getString("gid"))+" ";
				result += DomUtils.getXmlElementOutput("groupname", res.getString("g_label"));

				result += DomUtils.getXmlElementOutput("roleid", res.getString("grid"))+" ";
				result += DomUtils.getXmlElementOutput("rolename", res.getString("gri_label"))+" ";

				result += "</group>";
			}

			result += "</groups>";

			return result;
		} catch (SQLException e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public String getGroupList()
	{
		PreparedStatement st;
		String sql;
		ResultSet res;
		String retVal = "";

		try
		{
			sql = "SELECT * FROM group_info";
			st = connection.prepareStatement(sql);
			res = st.executeQuery();

			DocumentBuilderFactory documentBuilderFactory =DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
			Document document = documentBuilder.newDocument();

			Element groups = document.createElement("groups");
			document.appendChild(groups);

			while( res.next() )
			{
				Integer gid = res.getInt(1);
				Integer grid = res.getInt(2);
				Integer owner = res.getInt(3);
				String label = res.getString(4);
				Element group = document.createElement("group");
				group.setAttribute("gid", gid.toString());
				group.setAttribute("grid", grid.toString());
				group.setAttribute("owner", owner.toString());
				group.appendChild(document.createTextNode(label));

				groups.appendChild(group);
			}

			TransformerFactory tf = TransformerFactory.newInstance();
			Transformer transformer = tf.newTransformer();
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			StringWriter writer = new StringWriter();
			transformer.transform(new DOMSource(document), new StreamResult(writer));

			retVal = writer.getBuffer().toString().replaceAll("\n|\r", "");
		}
		catch(Exception ex){ ex.printStackTrace(); }

		return retVal;
	}

	@Override
	public String getGroupUserList()
	{
		PreparedStatement st;
		String sql;
		ResultSet res;
		String retVal = "";

		try
		{
			sql = "SELECT * FROM group_user";
			st = connection.prepareStatement(sql);
			res = st.executeQuery();

			DocumentBuilderFactory documentBuilderFactory =DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
			Document document = documentBuilder.newDocument();

			Element groups = document.createElement("group_user");
			document.appendChild(groups);

			while( res.next() )
			{
				Integer gid = res.getInt(1);
				Integer uid = res.getInt(2);
				Element group = document.createElement("gu");
				group.setAttribute("gid", gid.toString());
				group.setAttribute("uid", uid.toString());

				groups.appendChild(group);
			}

			TransformerFactory tf = TransformerFactory.newInstance();
			Transformer transformer = tf.newTransformer();
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			StringWriter writer = new StringWriter();
			transformer.transform(new DOMSource(document), new StreamResult(writer));

			retVal = writer.getBuffer().toString().replaceAll("\n|\r", "");
		}
		catch(Exception ex){ ex.printStackTrace(); }

		return retVal;
	}

	@Override
	public String postRoleUser(int userId, int grid, Integer userid2) throws SQLException {
		// TODO Auto-generated method stub

		if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		String label = null;
		String portfolio_id;
		String change_rights;
		int owner = 0;
		int gid = 0;

		PreparedStatement st;
		String sql;
		ResultSet res;
		ResultSet res1;

		PreparedStatement stInsert;
		String sqlInsert;

		sql = "SELECT * FROM group_info WHERE grid = ?";
		st = connection.prepareStatement(sql);
		st.setInt(1, grid);

		res = st.executeQuery();

		if(!res.next()){

			sql = "SELECT * FROM group_right_info WHERE grid = ?";

			st = connection.prepareStatement(sql);
			st.setInt(1, grid);

			res1 = st.executeQuery();

			if(res1.next()){

				label = res1.getString("label");
				portfolio_id = res1.getString("portfolio_id");
				change_rights = res1.getString("change_rights");
				owner = res1.getInt("owner");
			}

			sqlInsert = "REPLACE INTO group_info(grid, owner, label) VALUES (?, ?, ?)";
			stInsert = connection.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS);
			if (dbserveur.equals("oracle")){
				sqlInsert = "MERGE INTO group_info d using (SELECT ? grid,? owner,? label from dual) s ON (1=2) WHEN NOT MATCHED THEN INSERT (d.grid, d.owner, d.label) values (s.grid, s.owner, s.label)";
				stInsert = connection.prepareStatement(sqlInsert, new String[]{"gid"});
			}

			stInsert.setInt(1, grid);
			stInsert.setInt(2, owner);
			stInsert.setString(3, label);
			stInsert.executeUpdate();

			ResultSet rs = stInsert.getGeneratedKeys();


			if (rs.next()) {
				gid = rs.getInt(1);
			}

			sqlInsert = "REPLACE INTO group_user(gid, userid) VALUES (?, ?)";
			stInsert = connection.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS);
			if (dbserveur.equals("oracle")){
				sqlInsert = "MERGE INTO group_user d using (SELECT ? gid,? userid FROM DUAL) s ON (d.gid=s.gid AND d.userid=s.userid) WHEN NOT MATCHED THEN INSERT (d.gid, d.userid) VALUES (s.gid, s.userid)";
				stInsert = connection.prepareStatement(sqlInsert);
			}

			stInsert.setInt(1, gid);
			stInsert.setInt(2, userid2);
			stInsert.executeUpdate();

		}else {

			gid = res.getInt("gid");

			sqlInsert = "REPLACE INTO group_user(gid, userid) VALUES (?, ?)";
			stInsert = connection.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS);
			if (dbserveur.equals("oracle")){
				sqlInsert = "MERGE INTO group_user d using (SELECT ? gid,? userid FROM DUAL) s ON (d.gid=s.gid AND d.userid=s.userid) WHEN NOT MATCHED THEN INSERT (d.gid, d.userid) VALUES (s.gid, s.userid)";
				stInsert = connection.prepareStatement(sqlInsert);
			}

			stInsert.setInt(1, gid);
			stInsert.setInt(2, userid2);
			stInsert.executeUpdate();

		}

		return "user "+userid2+" rajouté au groupd gid "+gid+" pour correspondre au groupRight grid "+grid;
	}

	/// Liste des groupes -> uuid a partir du portofolio
	@Override
	public String getRolePortfolio(MimeType mimeType, String portfolioId, int userId)
	{
		PreparedStatement st;
		String sql;
		ResultSet res;

		String retVal = "";

		try
		{
			sql = "SELECT gri.grid, gri.owner, gri.change_rights, bin2uuid(gr.id) AS uuid, gr.RD, gr.WR, gr.DL, gr.SB, gr.AD, gr.types_id, gr.rules_id, gr.notify_roles " +
					"FROM group_right_info gri " +
					"LEFT JOIN group_rights gr ON gri.grid=gr.grid " +
					"WHERE gri.portfolio_id=uuid2bin(?) " +
					"ORDER BY gri.grid";
			st = connection.prepareStatement(sql);
			st.setString(1, portfolioId);

			res = st.executeQuery();

			DocumentBuilderFactory documentBuilderFactory =DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
			Document document = documentBuilder.newDocument();

			Element root = document.createElement("portfolio");
			root.setAttribute("id", portfolioId);
			document.appendChild(root);

			int prevgrid = 0;

			while(res.next())
			{
				int grid = res.getInt("grid");
				int owner = res.getInt("owner");
				int change = res.getInt("change_rights");
				Element rights = null;
				if( prevgrid != grid )
				{
					rights = document.createElement("rights");
					rights.setAttribute("id", Integer.toString(grid));
					rights.setAttribute("owner", Integer.toString(owner));
					rights.setAttribute("change_rights", Integer.toString(change));
					root.appendChild(rights);
					prevgrid = grid;
				}

				String uuid = res.getString("uuid");
				int rd = res.getInt("RD");
				int wr = res.getInt("WR");
				int dl = res.getInt("DL");
				int sb = res.getInt("SB");
				int ad = res.getInt("AD");
				String types_id = res.getString("types_id");
				if( types_id == null ) types_id = "";
				String rules_id = res.getString("rules_id");
				if( rules_id == null ) rules_id = "";
				String notify = res.getString("notify_roles");
				if( notify == null ) notify = "";

				Element right = document.createElement("right");
				right.setAttribute("id", uuid);
				right.setAttribute("RD", Integer.toString(rd));
				right.setAttribute("WR", Integer.toString(wr));
				right.setAttribute("DL", Integer.toString(dl));
				right.setAttribute("SB", Integer.toString(sb));
				right.setAttribute("AD", Integer.toString(ad));
				right.setAttribute("types_id", types_id);
				right.setAttribute("rules_id", rules_id);
				right.setAttribute("notify", notify);

				rights.appendChild(right);

			}

			TransformerFactory tf = TransformerFactory.newInstance();
			Transformer transformer = tf.newTransformer();
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			StringWriter writer = new StringWriter();
			transformer.transform(new DOMSource(document), new StreamResult(writer));

			retVal = writer.getBuffer().toString().replaceAll("\n|\r", "");

		}
		catch( Exception e )
		{

		}

		return retVal;
	}

	@Override
	public String getRole(MimeType mimeType, int grid, int userId) throws SQLException {
		// TODO Auto-generated method stub



		PreparedStatement st;
		String sql;
		ResultSet res;


		sql = "SELECT grid FROM group_right_info WHERE grid = ?";
		st = connection.prepareStatement(sql);
		st.setInt(1, grid);

		res = st.executeQuery();
		String result = "";
		try {
			while(res.next())
			{
				result += "<role ";
				result += DomUtils.getXmlAttributeOutput("id", res.getString("grid"))+" ";
				//result += DomUtils.getXmlAttributeOutput("owner", res.getString("owner"))+" ";
				result += DomUtils.getXmlAttributeOutput("owner", res.getString("owner"))+" ";
				result += ">";
				result += DomUtils.getXmlElementOutput("label", res.getString("label"))+" ";
				result += DomUtils.getXmlElementOutput("portfolio_id", res.getString("portfolio_id"));

				result += "</role>";
			}
		}
		catch(Exception ex)
		{
			return null;
		}

		return null;

	}

	@Override
	public String getGroupRightList()
	{
		PreparedStatement st;
		String sql;
		ResultSet res;
		String retVal = "";

		try
		{
			sql = "SELECT grid, owner, label, change_rights, bin2uuid(portfolio_id) " +
					"FROM group_right_info ORDER BY portfolio_id";
			st = connection.prepareStatement(sql);
			res = st.executeQuery();

			DocumentBuilderFactory documentBuilderFactory =DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
			Document document = documentBuilder.newDocument();

			Element groups = document.createElement("group_rights");
			document.appendChild(groups);

			String prevPort = "DUMMY";
			Element portNode = null;
			while( res.next() )
			{
				Integer grid = res.getInt(1);
				Integer owner = res.getInt(2);
				String label = res.getString(3);
				Integer change = res.getInt(4);
				String portfolio = res.getString(5);
				if( portfolio == null )
					portfolio = "";

				if( !prevPort.equals(portfolio) )
				{
					portNode = document.createElement("portfolio");
					portNode.setAttribute("id", portfolio);
					groups.appendChild(portNode);

					prevPort = portfolio;
				}

				Element group = document.createElement("group");
				group.setAttribute("grid", grid.toString());
				group.setAttribute("owner", owner.toString());
				group.setAttribute("change_rights", change.toString());
				//          group.setAttribute("portfolio", portfolio);
				group.setNodeValue(label);

				portNode.appendChild(group);
			}

			TransformerFactory tf = TransformerFactory.newInstance();
			Transformer transformer = tf.newTransformer();
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			StringWriter writer = new StringWriter();
			transformer.transform(new DOMSource(document), new StreamResult(writer));

			retVal = writer.getBuffer().toString().replaceAll("\n|\r", "");
		}
		catch(Exception ex){ ex.printStackTrace(); }

		return retVal;
	}

	private int updateMysqlCredentialToken(Integer userId, String  token)
	{
		String sql = "";
		PreparedStatement st;

		try
		{
			sql  = "UPDATE  credential SET token = ? WHERE userid  = ? ";

			st = connection.prepareStatement(sql);
			st.setString(1,token);
			st.setInt(2,userId);

			return st.executeUpdate();

		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			return -1;
		}
	}

	private ResultSet getMysqlOtherNodeUuidByPortfolioModelUuidBySemanticTag(String portfolioModelUuid, String semantictag) throws SQLException
	{
		String sql = "";
		PreparedStatement st;

		try
		{

			sql = "SELECT bin2uuid(node_uuid) AS node_uuid FROM model_node WHERE portfolio_model_uuid = uuid2bin(?) and  semantic_tag=? ";
			st = connection.prepareStatement(sql);
			st.setString(1, portfolioModelUuid);
			st.setString(2, semantictag);
			return st.executeQuery();
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			return null;
		}
	}

	@Override
	public String getUsersByRole(int userId, String portfolioUuid, String role)
			throws SQLException {
		// TODO Auto-generated method stub

		String sql = "";
		PreparedStatement st;
		ResultSet res = null;

		try
		{

			sql = "SELECT * FROM credential c, group_right_info gri, group_info gi, group_user gu WHERE c.userid = gu.userid AND gu.gid = gi.gid AND gi.grid = gri.grid AND gri.portfolio_id = uuid2bin(?) AND gri.label = ?";
			st = connection.prepareStatement(sql);
			st.setString(1, portfolioUuid);
			st.setString(2, role);
			res = st.executeQuery();
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			return null;
		}

		String result = "<users>";
		try {
			while(res.next())
			{
				result +="<user ";
				result += DomUtils.getXmlAttributeOutput("id", res.getString("userid"))+" ";
				result += ">";
				result += DomUtils.getXmlElementOutput("username", res.getString("login"));
				result += DomUtils.getXmlElementOutput("firstname", res.getString("display_firstname"));
				result += DomUtils.getXmlElementOutput("lastname", res.getString("display_lastname"));
				result += DomUtils.getXmlElementOutput("email", res.getString("email"));
				result += "</user>";
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}


		result += "</users>";

		return result;

	}

	@Override
	public String getGroupsByRole(int userId, String portfolioUuid, String role) {
		// TODO Auto-generated method stub

		String sql = "";
		PreparedStatement st;
		ResultSet res = null;

		try
		{

			sql = "SELECT DISTINCT gu.gid FROM group_right_info gri, group_info gi, group_user gu WHERE gu.gid = gi.gid AND gi.grid = gri.grid AND gri.portfolio_id = uuid2bin(?) AND gri.label = ?";
			st = connection.prepareStatement(sql);
			st.setString(1, portfolioUuid);
			st.setString(2, role);
			res = st.executeQuery();
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			return null;
		}

		String result = "<groups>";
		try {
			while(res.next())
			{
				result += DomUtils.getXmlElementOutput("group", res.getString("gid"));
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}


		result += "</groups>";

		return result;
	}

	@Override
	public String getUsersByGroup(int userId, int groupId)
			throws SQLException {
		// TODO Auto-generated method stub
		String sql = "";
		PreparedStatement st;
		ResultSet res = null;

		sql = "SELECT * FROM credential_group";
		st = connection.prepareStatement(sql);
		st.setInt(1, groupId);
		res = st.executeQuery();

		String result = "<groups>";
		try {
			while(res.next())
			{
				result +="<group ";
				//result += DomUtils.getXmlAttributeOutput("id", res.getString("userid"))+" ";
				result += ">";
				result += DomUtils.getXmlElementOutput("cg", res.getString("cg"));
				result += "</group>";
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}


		result += "</groups>";

		return result;
	}

	@Override
	public String postUsersGroupsUser(int userId, int usersgroup, int userid2) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getNodeMetadataWad(MimeType mimeType, String nodeUuid,
			boolean b, int userId, int groupId, String label) throws SQLException {
		// TODO Auto-generated method stub




		StringBuffer result = new StringBuffer();
		// Verification securitï¿½
		NodeRight nodeRight = credential.getNodeRight(userId,groupId,nodeUuid, label);

		if(!nodeRight.read)
			return result;

		ResultSet resNode = getMysqlNode(nodeUuid,userId, groupId);

		//try
		//{
		//			resNode.next();

		if(resNode.next())
		{

			if(!resNode.getString("asm_type").equals("asmResource"))
			{
//				if(!resNode.getString("metadata_wad").equals("") )
				if(resNode.getString("metadata_wad")!=null && !resNode.getString("metadata_wad").equals("") )
					result.append("<metadata-wad "+resNode.getString("metadata_wad")+"/>");
				else
					result.append("<metadata-wad/>");

			}
		}

		resNode.close();

		return result;
	}

	private int updatetMySqlNodeMetadata(String nodeUuid,
			String metadata)
					throws Exception
					{
		String sql = "";
		PreparedStatement st;


		sql  = "UPDATE node SET ";
		sql += "metadata = ?";
		sql += " WHERE node_uuid = uuid2bin(?) ";
		st = connection.prepareStatement(sql);

		st.setString(1, metadata);
		st.setString(2, nodeUuid);

		return st.executeUpdate();
					}

	private int updatetMySqlNodeMetadatawad(String nodeUuid,
			String metadatawad)
					throws Exception
					{
		String sql = "";
		PreparedStatement st;


		sql  = "UPDATE node SET ";
		sql += "metadata_wad = ?";
		sql += " WHERE node_uuid = uuid2bin(?) ";
		st = connection.prepareStatement(sql);

		st.setString(1, metadatawad);
		st.setString(2, nodeUuid);

		return st.executeUpdate();
					}


	@Override
	public Object putNodeMetadata(MimeType mimeType, String nodeUuid,
			String xmlNode, int userId, int groupId) throws Exception {

		String metadata = "";

		int sharedRes = 0;
		int sharedNode = 0;
		int sharedNodeRes = 0;

		int returnValue = 0;

		//TODO putNode getNodeRight
		if(!credential.hasNodeRight(userId,groupId,nodeUuid, credential.WRITE))
			return "faux";

		String status = "erreur";

		//TODO Optimiser le nombre de requetes (3 => 1)
		int nodeOrder = getNodeOrderByNodeUuid(nodeUuid);
		String parentNodeUuid = getNodeParentUuidByNodeUuid(nodeUuid);
		String portfolioUid = getPortfolioUuidByNodeUuid(nodeUuid);
		String portfolioModelId = getPortfolioModelUuid(portfolioUid);

		// D'abord on supprime les noeuds existants
		//deleteNode(nodeUuid, userId);
		xmlNode = DomUtils.cleanXMLData(xmlNode);
		Document doc = DomUtils.xmlString2Document(xmlNode, new StringBuffer());
		// Puis on le recree
		Node node;
		node = doc.getDocumentElement();

		if(node.getNodeName().equals("metadata"))
		{
			String tag = "";
			NamedNodeMap attr = node.getAttributes();
			try
			{
				tag = attr.getNamedItem("semantictag").getNodeValue();
			}
			catch(Exception ex) {}

			String tmpSharedRes = "";
			try
			{
				tmpSharedRes = attr.getNamedItem("sharedResource").getNodeValue();
				if(tmpSharedRes.equalsIgnoreCase("y"))
					sharedRes = 1;
			}
			catch(Exception ex) {}

			String tmpSharedNode = "";
			try
			{
				tmpSharedNode = attr.getNamedItem("sharedNode").getNodeValue();
				if(tmpSharedNode.equalsIgnoreCase("y"))
					sharedNode = 1;
			}
			catch(Exception ex) {}

			String tmpSharedNodeResource = "";
			try
			{
				tmpSharedNodeResource = attr.getNamedItem("sharedNodeResource").getNodeValue();
				if(tmpSharedNodeResource.equalsIgnoreCase("y"))
					sharedNodeRes = 1;
			}
			catch(Exception ex) {}

			metadata = DomUtils.getNodeAttributesString(node);

			try
			{
				/// Mettre � jour les flags et donn�e du champ
				String sql = "UPDATE node SET metadata=?, semantictag=?, shared_res=?, shared_node=?, shared_node_res=? WHERE node_uuid=uuid2bin(?)";
				PreparedStatement st = connection.prepareStatement(sql);
				st.setString(1, metadata);
				st.setString(2, tag);
				st.setInt(3, sharedRes);
				st.setInt(4, sharedNode);
				st.setInt(5, sharedNodeRes);
				st.setString(6, nodeUuid);
				st.executeUpdate();
				st.close();

				status = "editer";
			}
			catch(Exception ex) {}

		}

		//		if (1 == updatetMySqlNodeMetadata(nodeUuid,metadata)){
		//			return ;
		//		}

		return status;
	}



	public ResultSet getMysqlUserGroupByPortfolio(String portfolioUuid, int userId)
	{
		PreparedStatement st;
		String sql;

		try
		{
			// On recupere d'abord les informations dans la table structures

			sql = "SELECT gi.gid, gi.owner, gi.grid,gi.label as g_label, gri.label as gri_label  FROM  group_right_info gri , group_info gi, group_user gu  WHERE   gu.gid=gi.gid AND gri.grid = gi.grid  AND gri.portfolio_id = uuid2bin(?) AND gu.userid= ? ";


			st = connection.prepareStatement(sql);
			st.setString(1, portfolioUuid);
			st.setInt(2, userId);

			return st.executeQuery();



		}
		catch (Exception e)
		{
			e.printStackTrace();
			return null;
		}

	}


	@Override
	public String getUserGroupByPortfolio(String portfolioUuid, int userId) {
		// TODO Auto-generated method stub
		// TODO Auto-generated method stub
		ResultSet res = getMysqlUserGroupByPortfolio(portfolioUuid, userId);

		String result = "<groups>";
		try {
			while(res.next())
			{
				result += "<group ";
				result += DomUtils.getXmlAttributeOutput("id", res.getString("gid"))+" ";
				//result += DomUtils.getXmlAttributeOutput("owner", res.getString("owner"))+" ";
				result += DomUtils.getXmlAttributeOutput("templateId", res.getString("grid"))+" ";
				result += ">";
				result += DomUtils.getXmlElementOutput("label", res.getString("g_label"));
				result += DomUtils.getXmlElementOutput("role", res.getString("g_label"));
				result += DomUtils.getXmlElementOutput("groupid", res.getString("gid"));
				result += "</group>";

			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}

		result += "</groups>";
		return result;

	}

	@Override
	public Object putNodeMetadataWad(MimeType mimeType, String nodeUuid,
			String xmlNode, int userId, int groupId) throws Exception {
		// TODO Auto-generated method stub
		String metadatawad = "";

		int sharedRes = 0;
		int sharedNode = 0;
		int sharedNodeRes = 0;

		int returnValue = 0;

		//TODO putNode getNodeRight
		if(!credential.hasNodeRight(userId,groupId,nodeUuid, credential.WRITE))
			return "faux";


		//TODO Optimiser le nombre de requetes (3 => 1)
		int nodeOrder = getNodeOrderByNodeUuid(nodeUuid);
		String parentNodeUuid = getNodeParentUuidByNodeUuid(nodeUuid);
		String portfolioUid = getPortfolioUuidByNodeUuid(nodeUuid);
		String portfolioModelId = getPortfolioModelUuid(portfolioUid);

		// D'abord on supprime les noeuds existants
		//deleteNode(nodeUuid, userId);
		xmlNode = DomUtils.cleanXMLData(xmlNode);
		Document doc = DomUtils.xmlString2Document(xmlNode, new StringBuffer());
		// Puis on le recree
		Node node;
		node = doc.getDocumentElement();

		if(node.getNodeName().equals("metadata-wad"))
		{
			metadatawad = DomUtils.getNodeAttributesString(node);// " attr1=\"wad1\" attr2=\"wad2\" ";
			metadatawad = processMeta(userId, metadatawad);
		}

		if (1 == updatetMySqlNodeMetadatawad(nodeUuid,metadatawad)){
			return "editer";
		}

		return "erreur";
	}

	@Override
	public Object putNodeNodeContext(MimeType mimeType, String nodeUuid,
			String xmlNode, int userId, int groupId) throws Exception{

		if(!credential.hasNodeRight(userId,groupId,nodeUuid, credential.WRITE))
			return "faux";

		xmlNode = DomUtils.cleanXMLData(xmlNode);
		Document doc = DomUtils.xmlString2Document(xmlNode, new StringBuffer());
		// Puis on le recree
		Node node;
		node = doc.getDocumentElement();

		if(node.getNodeName().equals("asmResource"))
		{
			// Si le noeud est de type asmResource, on stocke le innerXML du noeud
			updateMysqlResourceByXsiType(nodeUuid,"context",DomUtils.getInnerXml(node),userId);
			return "editer";
		}
		return "erreur";
	}

	@Override
	public Object putNodeNodeResource(MimeType mimeType, String nodeUuid,
			String xmlNode, int userId, int groupId) throws Exception {

		// TODO Auto-generated method stub
		if(!credential.hasNodeRight(userId,groupId,nodeUuid, credential.WRITE))
			return "faux";

		xmlNode = DomUtils.cleanXMLData(xmlNode);
		Document doc = DomUtils.xmlString2Document(xmlNode, new StringBuffer());
		// Puis on le recree
		Node node;
		node = doc.getDocumentElement();

		if(node.getNodeName().equals("asmResource"))
		{
			// Si le noeud est de type asmResource, on stocke le innerXML du noeud
			updateMysqlResourceByXsiType(nodeUuid,"nodeRes",DomUtils.getInnerXml(node),userId);
			return "editer";
		}
		return "erreur";
	}

	@Override
	public Object putRole(String xmlRole, int userId, int roleId) throws Exception {
		// TODO Auto-generated method stub


		if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		String result = null;
		String  username = null;
		String  password = null;
		String  firstname = null;
		String  lastname = null;
		String  email = null;
		String  label = null;
		int id = 0;

		//On prepare les requetes SQL
		PreparedStatement stInsert;
		String sqlInsert;

		//On recupere le body
		Document doc;


		doc = DomUtils.xmlString2Document(xmlRole, new StringBuffer());
		Element role = doc.getDocumentElement();


		NodeList children = null;


		children = role.getChildNodes();
		// On parcourt une première fois les enfants pour récuperer la liste à écrire en base

		//On verifie le bon format
		if(role.getNodeName().equals("role"))
		{
			for(int i=0;i<children.getLength();i++)
			{
				if(children.item(i).getNodeName().equals("label"))
				{
					label = DomUtils.getInnerXml(children.item(i));
				}

			}

		}else{
			result = "Erreur lors de la recuperation des attributs de l'utilisateur dans le XML";
		}


		//On ajoute l'utilisateur dans la base de donnees
		try {
			sqlInsert = "REPLACE INTO credential(login, display_firstname, display_lastname,email, password) VALUES (?, ?, ?, ?, UNHEX(SHA1(?)))";
			stInsert = connection.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS);
			if (dbserveur.equals("oracle")){
				sqlInsert = "INSERT INTO credential(login, display_firstname, display_lastname,email, password) VALUES (?, ?, ?, ?, crypt(?))";
				stInsert = connection.prepareStatement(sqlInsert, new String[]{"userid"});
			}

			stInsert.setString(1, username);
			stInsert.setString(2, firstname);
			stInsert.setString(3, lastname);
			stInsert.setString(4, email);
			stInsert.setString(5, password);
			stInsert.executeUpdate();

			ResultSet rs = stInsert.getGeneratedKeys();
			if (rs.next()) {
				id = rs.getInt(1);
			}

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		result = ""+id;

		return result;
	}

	@Override
	public Object getModels(MimeType mimeType, int userId) throws Exception {
		// TODO Auto-generated method stub

		PreparedStatement st;
		String sql;
		ResultSet res = null;

		sql = "SELECT *  FROM  portfolio_model";


		st = connection.prepareStatement(sql);
		res = st.executeQuery();

		String result = "";
		result += "<models> ";
		try {
			while(res.next())
			{
				result += "<model ";
				result += DomUtils.getXmlAttributeOutput("id", res.getString("pmid"))+" ";
				result += ">";
				result += DomUtils.getXmlElementOutput("label", res.getString("pm_label"));
				result += DomUtils.getXmlElementOutput("treeid", res.getString("portfolio_id"));
				result += "</model>";
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
		result += "</models> ";

		return result;
	}

	@Override
	public Object getModel(MimeType mimeType, Integer modelId, int userId)
			throws Exception {
		PreparedStatement st;
		String sql;
		ResultSet res = null;

		sql = "SELECT *  FROM  portfolio_model WHERE portfolio_id = uuid2bin(?)";


		st = connection.prepareStatement(sql);
		res = st.executeQuery();

		String result = "";
		try {
			while(res.next())
			{
				result += "<model ";
				result += DomUtils.getXmlAttributeOutput("id", res.getString("pmid"))+" ";
				result += ">";
				result += DomUtils.getXmlElementOutput("label", res.getString("pm_label"));
				result += DomUtils.getXmlElementOutput("treeid", res.getString("portfolio_id"));
				result += "</model>";
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}

		return result;
	}

	@Override
	public Object postModels(MimeType mimeType, String xmlModel, int userId)
			throws Exception {
		// TODO Auto-generated method stub
		if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		String pm_label = null;
		String portfolio_id = null;
		Integer id = 0;
		String result = "";

		//On prepare les requetes SQL
		PreparedStatement stInsert;
		String sqlInsert;

		//On recupere le body
		Document doc;


		doc = DomUtils.xmlString2Document(xmlModel, new StringBuffer());
		Element users = doc.getDocumentElement();


		NodeList children = null;


		children = users.getChildNodes();
		// On parcourt une première fois les enfants pour récuperer la liste à écrire en base

		//On verifie le bon format
		if(users.getNodeName().equals("models"))
		{
			for(int i=0;i<children.getLength();i++)
			{
				if(children.item(i).getNodeName().equals("model"))
				{
					NodeList children2 = null;
					children2 = children.item(i).getChildNodes();
					for(int y=0;y<children2.getLength();y++)
					{

						if(children2.item(y).getNodeName().equals("label"))
						{
							pm_label = DomUtils.getInnerXml(children2.item(y));
						}
						if(children2.item(y).getNodeName().equals("treeid"))
						{
							portfolio_id = DomUtils.getInnerXml(children2.item(y));
						}

						//requete sql à refaire
					}
				}
			}
		}else{
			result = "Erreur lors de la recuperation des attributs de l'utilisateur dans le XML";
		}

		return result;

	}

	public String processMeta(int userId, String meta)
	{
		try
		{
			/// FIXME: Patch court terme pour la migration
			/// Trouve le login associ� au userId
			String sql = "SELECT login FROM credential c " +
					"WHERE c.userid=?";
			PreparedStatement  st = connection.prepareStatement(sql);
			st.setInt(1, userId);
			ResultSet res = st.executeQuery();

			/// res.getFetchSize() retourne 0, même avec un bon résultat
			String login="";
			if( res.next() )
				login = res.getString("login");

			/// Remplace 'user' par le login de l'utilisateur
			meta = meta.replaceAll("user", login);

			/// Ajoute un droit dans la table

		}
		catch( SQLException e )
		{
			e.printStackTrace();
		}

		return meta;
	}

	/******************************/
	/**
	 *  Macro commandes et cie
	 *
	 *  ##   ##   ###    #####  #####    #####
	 *  ####### ##   ## ##   ## ##   ## ##   ##
	 *  ## # ## ##   ## ##   ## ##   ## ##   ##
	 *  ##   ## ####### ##      ######  ##   ##
	 *  ##   ## ##   ## ##   ## ##   ## ##   ##
	 *  ##   ## ##   ## ##   ## ##   ## ##   ##
	 *  ##   ## ##   ##  #####  ##   ##  #####
	 **/
	/*****************************/

	@Override
	public String postMacroOnNode( int userId, String nodeUuid, Integer macro )
	{
		String val = "erreur";
		String sql = "";
		PreparedStatement st;
		/// SELECT grid, role, RD,WR,DL,AD,types_id,rules_id FROM rule_table rt LEFT JOIN group_right_info gri ON rt.role=gri.label LEFT JOIN node n ON n.portfolio_id=gri.portfolio_id WHERE rule_id=1 AND n.node_uuid=uuid2bin('d48cafa1-5180-4c83-9e22-5d4d45bbf6e2');
		/// SELECT grid,bin2uuid(id),RD,WR,DL,SB,AD,types_id,rules_id FROM group_rights WHERE id=uuid2bin('d48cafa1-5180-4c83-9e22-5d4d45bbf6e2');
		try
		{
			/// Vérifie si l'utilisateur a le droit d'utiliser cette macro-commande
			sql = "SELECT userid FROM group_user gu LEFT JOIN group_info gi ON gu.gid=gi.gid " +
					"LEFT JOIN group_rights gr ON gi.grid=gr.grid " +
					"WHERE gu.userid=? AND id=uuid2bin(?) AND rules_id LIKE CONCAT('%',?,'%')";
			st = connection.prepareStatement(sql);
			st.setInt(1, userId);
			st.setString(2, nodeUuid);
			st.setInt(3, macro);
			ResultSet res = st.executeQuery();

			/// res.getFetchSize() retourne 0, même avec un bon résultat
			int uid=0;
			if( res.next() )
				uid = res.getInt("userid");
			if( uid != userId ) return "";

			res.close();
			st.close();

			/// Pour retrouver les enfants du noeud et affecter les droits
			sql = "CREATE TEMPORARY TABLE t_struc(" +
					"uuid binary(16) UNIQUE NOT NULL, " +
					"t_level INT) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
			if (dbserveur.equals("oracle")){
			      sql = "CREATE GLOBAL TEMPORARY TABLE t_struc(" +
			              "uuid RAW(16) NOT NULL, " +
			              "t_level NUMBER(10,0)"+
			                ",  CONSTRAINT t_struc_UK_uuid UNIQUE (uuid)) ON COMMIT PRESERVE ROWS";
			}
			st = connection.prepareStatement(sql);
			st.execute();

			// En double car on ne peut pas faire d'update/select d'une m�me table temporaire
			sql = "CREATE TEMPORARY TABLE t_struc_2(" +
					"uuid binary(16) UNIQUE NOT NULL, " +
					"t_level INT) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
			if (dbserveur.equals("oracle")){
			      sql = "CREATE GLOBAL TEMPORARY TABLE t_struc_2(" +
			              "uuid RAW(16) NOT NULL, " +
			              "t_level NUMBER(10,0)"+
			                ",  CONSTRAINT t_struc_2_UK_uuid UNIQUE (uuid)) ON COMMIT PRESERVE ROWS";
			}
			st = connection.prepareStatement(sql);
			st.execute();
			st.close();

			/// Dans la table temporaire on retrouve les noeuds concern�s
			/// (assure une convergence de la r�cursion et limite le nombre de lignes dans la recherche)
			/// Init table
			sql = "INSERT INTO t_struc(uuid, t_level) " +
					"SELECT n.node_uuid, 0 " +
					"FROM node n " +
					"WHERE n.node_uuid=uuid2bin(?)";
			st = connection.prepareStatement(sql);
			st.setString(1, nodeUuid);
			st.executeUpdate();
			st.close();

			/// On boucle, r�cursion par niveau
			int level = 0;
			int added = 1;
			if (dbserveur.equals("mysql")){
			    sql = "INSERT IGNORE INTO t_struc_2(uuid, t_level) ";
			} else if (dbserveur.equals("oracle")){
			    sql = "INSERT /*+ ignore_row_on_dupkey_index(t_struc_2,t_struc_2_UK_uuid)*/ INTO t_struc_2(uuid, t_level) ";
			}
			sql += "SELECT n.node_uuid, ? " +
					"FROM node n WHERE n.node_parent_uuid IN (SELECT uuid FROM t_struc t " +
					"WHERE t.t_level=?)";

	        String sqlTemp=null;
			if (dbserveur.equals("mysql")){
				sqlTemp = "INSERT IGNORE INTO t_struc SELECT * FROM t_struc_2;";
			} else if (dbserveur.equals("oracle")){
				sqlTemp = "INSERT /*+ ignore_row_on_dupkey_index(t_struc,t_struc_UK_uuid)*/ INTO t_struc SELECT * FROM t_struc_2";
			}
	        PreparedStatement stTemp = connection.prepareStatement(sqlTemp);

			st = connection.prepareStatement(sql);
			while( added != 0 )
			{
				st.setInt(1, level+1);
				st.setInt(2, level);
				st.executeUpdate();
				added = stTemp.executeUpdate();   // On s'arr�te quand rien � �t� ajout�
				level = level + 1;    // Prochaine �tape
			}
			st.close();
			stTemp.close();

			/// Ajoute/remplace les droits
			sql = "INSERT INTO group_rights(grid, id, RD, WR, DL, AD, types_id, rules_id) " +
					// Séléctionne les nouveaux droits et les bon groupes concernés
					"SELECT gri.grid, n.node_uuid, rt.RD, rt.WR, rt.DL, rt.AD, rt.types_id, rt.rules_id " +
					"FROM rule_table rt LEFT JOIN group_right_info gri ON rt.role=gri.label " +
					"LEFT JOIN node n ON n.portfolio_id=gri.portfolio_id " +
					"WHERE rt.rule_id=? AND n.node_uuid IN (SELECT uuid FROM t_struc) " +
					// Dans le cas d'une mise à jour de droits existant
					"ON DUPLICATE KEY UPDATE RD=rt.RD, WR=rt.WR, DL=rt.DL, AD=rt.AD, types_id=rt.types_id, rules_id=rt.rules_id";
			if (dbserveur.equals("oracle")){
				sql = "MERGE INTO group_rights d USING (SELECT gri.grid, n.node_uuid, rt.RD, rt.WR, rt.DL, rt.AD, rt.types_id, rt.rules_id FROM rule_table rt LEFT JOIN group_right_info gri ON rt.role=gri.label LEFT JOIN node n ON n.portfolio_id=gri.portfolio_id WHERE rt.rule_id=? AND n.node_uuid IN (SELECT uuid FROM t_struc)) s ON (d.grid = s.grid AND d.id = s.id) WHEN MATCHED THEN UPDATE SET d.RD=rt.RD, d.WR=rt.WR, d.DL=rt.DL, d.AD=rt.AD, d.types_id=rt.types_id, d.rules_id=rt.rules_id WHEN NOT MATCHED THEN INSERT (d.grid, d.id, d.RD, d.WR, d.DL, d.AD, d.types_id, d.rules_id) VALUES (s.grid, s.id, s.RD, s.WR, s.DL, s.AD, s.types_id, s.rules_id)";
			}
			st = connection.prepareStatement(sql);
			st.setInt(1, macro);
			st.executeUpdate();
			st.close();

			val = "OK";
		}
		catch( SQLException e )
		{
			e.printStackTrace();
		}
		finally
		{
			try
			{
				// Les 'pooled connection' ne se ferment pas vraiment. On nettoie manuellement les tables temporaires...
				if (dbserveur.equals("mysql")){
					sql = "DROP TEMPORARY TABLE IF EXISTS t_struc, t_struc_2";
					st = connection.prepareStatement(sql);
					st.execute();
					st.close();
				} else if (dbserveur.equals("oracle")){
		        	sql = "{call drop_tables(tmpTableList('t_struc', 't_struc_2'))}";
		            CallableStatement ocs = connection.prepareCall(sql) ;
		            ocs.execute(); 
		            ocs.close();
				}

				connection.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}

		return val;
	}

	@Override
	public String postAddAction( int userId, Integer macro, String role, String data )
	{
		if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		String sql = "";
		PreparedStatement st;
		Integer output=0;

		try
		{
			DocumentBuilderFactory documentBuilderFactory =DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
			InputSource is = new InputSource(new StringReader(data));
			Document doc = documentBuilder.parse(is);

			Element root = doc.getDocumentElement();
			boolean rd = "true".equals(root.getAttribute("read")) ? true : false;
			boolean wr = "true".equals(root.getAttribute("write")) ? true : false;
			boolean dl = "true".equals(root.getAttribute("delete")) ? true : false;
			boolean ad = "true".equals(root.getAttribute("add")) ? true : false;

			Node typesNode = doc.getElementsByTagName("types").item(0);
			Node types = typesNode.getFirstChild();
			String typesText = "";
			if( types != null )
				typesText = types.getNodeValue();

			Node rulesNode = doc.getElementsByTagName("rules").item(0);
			Node rules = rulesNode.getFirstChild();
			String rulesText = "";
			if( rules != null )
				rulesText = rules.getNodeValue();

			sql = "INSERT INTO rule_table(rule_id, role, RD, WR, DL, AD, types_id, rules_id) " +
					"VALUE(?,?,?,?,?,?,?,?)";
			st = connection.prepareStatement(sql);
			st.setInt(1, macro);
			st.setString(2, role);
			st.setBoolean(3, rd);
			st.setBoolean(4, wr);
			st.setBoolean(5, dl);
			st.setBoolean(6, ad);
			st.setString(7, typesText);
			st.setString(8, rulesText);

			output = st.executeUpdate();
			st.close();
		}
		catch( Exception e )
		{
			e.printStackTrace();
		}

		return output.toString();
	}

	@Override
	public Integer postCreateMacro( int userId, String macroName )
	{
		// Création d'une nouvelle macro
		if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		String sql = "";
		PreparedStatement st;
		Integer output=0;

		try
		{
			sql = "INSERT INTO rule_info(label) VALUE(?)";
			st = connection.prepareStatement(sql);
			st.setString(1, macroName);
			st.executeUpdate();

			ResultSet rs = st.getGeneratedKeys();
			if( rs.next() )
				output = rs.getInt(1);

			st.close();
		}
		catch( SQLException e )
		{
			e.printStackTrace();
		}

		return output;
	}

	@Override
	public String getAllActionLabel( int userId )
	{
		if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		String sql = "";
		PreparedStatement st;

		ResultSet res=null;
		try
		{
			sql = "SELECT * FROM rule_info";
			st = connection.prepareStatement(sql);
			res = st.executeQuery();

			/// Time to create data
			DocumentBuilderFactory documentBuilderFactory =DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder;
			Document document=null;

			documentBuilder = documentBuilderFactory.newDocumentBuilder();
			document = documentBuilder.newDocument();

			Element root = document.createElement("macros");
			document.appendChild(root);

			while( res.next() )
			{
				String label = res.getString("label");
				Integer id = res.getInt("rule_id");

				Element macro = document.createElement("macro");
				macro.setAttribute("id", id.toString());
				Text textLabel = document.createTextNode(label);
				macro.appendChild(textLabel);

				root.appendChild(macro);
			}
			res.close();
			st.close();

			StringWriter stw = new StringWriter();
			Transformer serializer = TransformerFactory.newInstance().newTransformer();
			serializer.transform(new DOMSource(document), new StreamResult(stw));
			return stw.toString();
		}
		catch( Exception e )
		{
			e.printStackTrace();
		}

		return "";
	}

	@Override
	public String getMacroActions( int userId, Integer macro )
	{
		if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		String sql = "";
		PreparedStatement st;

		ResultSet res=null;
		try
		{
			sql = "SELECT role,RD,WR,DL,AD,types_id,rules_id FROM rule_table WHERE rule_id=?";
			st = connection.prepareStatement(sql);
			st.setInt(1, macro);
			res = st.executeQuery();

			/// Time to create data
			DocumentBuilderFactory documentBuilderFactory =DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
			Document document=null;

			document = documentBuilder.newDocument();

			Element root = document.createElement("macro");
			root.setAttribute("id", macro.toString());
			document.appendChild(root);

			while( res.next() )
			{
				String role = res.getString("role");
				Boolean rd = res.getBoolean("RD");
				Boolean wr = res.getBoolean("WR");
				Boolean dl = res.getBoolean("DL");
				Boolean ad = res.getBoolean("AD");
				String types = res.getString("types_id");
				String rules = res.getString("rules_id");

				Element rule = document.createElement("rule");
				rule.setAttribute("read", rd.toString());
				rule.setAttribute("write", wr.toString());
				rule.setAttribute("delete", dl.toString());
				rule.setAttribute("add", ad.toString());

				Element roleNode = document.createElement("role");
				Text roleLabel = document.createTextNode(role);
				roleNode.appendChild(roleLabel);
				rule.appendChild(roleNode);

				Element typesNode = document.createElement("types");
				Text typesLabel = document.createTextNode(types);
				if( types != null )
					typesNode.appendChild(typesLabel);
				rule.appendChild(typesNode);

				Element rulesNode = document.createElement("rules");
				Text rulesLabel = document.createTextNode(rules);
				if( rules != null )
					rulesNode.appendChild(rulesLabel);
				rule.appendChild(rulesNode);

				root.appendChild(rule);
			}
			res.close();
			st.close();

			StringWriter stw = new StringWriter();
			Transformer serializer = TransformerFactory.newInstance().newTransformer();
			serializer.transform(new DOMSource(document), new StreamResult(stw));

			return stw.toString();
		}
		catch( Exception e )
		{
			e.printStackTrace();
		}

		return "";
	}

	@Override
	public String getPortfolioMacro( int userId, String portfolioId )
	{
		// Retourne les labels et id d'actions qui sont utilisable dans le portfolio
		String sql = "";
		PreparedStatement st;

		ResultSet res=null;
		try
		{
			sql = "SELECT ri.rule_id, ri.label FROM rule_info ri WHERE EXISTS " +
					"(SELECT gr.rules_id FROM group_right_info gri " +
					"LEFT JOIN group_rights gr ON gri.grid=gr.grid " +
					"WHERE portfolio_id=uuid2bin(?) " +
					"HAVING rules_id LIKE CONCAT('%', ri.rule_id, '%') )";
			st = connection.prepareStatement(sql);
			st.setString(1, portfolioId);
			res = st.executeQuery();

			/// Time to create data
			DocumentBuilderFactory documentBuilderFactory =DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder;
			Document document=null;

			documentBuilder = documentBuilderFactory.newDocumentBuilder();
			document = documentBuilder.newDocument();

			Element root = document.createElement("actions");
			document.appendChild(root);

			while( res.next() )
			{
				Integer id = res.getInt("rule_id");
				String label = res.getString("label");

				Element action = document.createElement("action");
				action.setAttribute("id", id.toString());
				Text labelNode = document.createTextNode(label);
				action.appendChild(labelNode);

				root.appendChild(action);
			}
			res.close();
			st.close();

			StringWriter stw = new StringWriter();
			Transformer serializer = TransformerFactory.newInstance().newTransformer();
			serializer.transform(new DOMSource(document), new StreamResult(stw));
			return stw.toString();
		}
		catch( Exception e )
		{
			e.printStackTrace();
		}

		return "";
	}

	@Override
	public String putMacroName( int userId, Integer macro, String name )
	{
		if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		String sql = "";
		PreparedStatement st;

		try
		{
			sql = "UPDATE rule_info SET label=? " +
					"WHERE rule_id=?";
			st = connection.prepareStatement(sql);
			st.setString(1, name);
			st.setInt(2, macro);
			st.executeUpdate();
			st.close();
		}
		catch( SQLException e )
		{
			e.printStackTrace();
		}

		return "OK";
	}

	@Override
	public String putMacroAction( int userId, Integer macro, String role, String data )
	{
		if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		String sql = "";
		PreparedStatement st;
		Integer output=0;

		try
		{
			DocumentBuilderFactory documentBuilderFactory =DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
			InputSource is = new InputSource(new StringReader(data));
			Document doc = documentBuilder.parse(is);

			Element root = doc.getDocumentElement();
			boolean rd = "true".equals(root.getAttribute("read")) ? true : false;
			boolean wr = "true".equals(root.getAttribute("write")) ? true : false;
			boolean dl = "true".equals(root.getAttribute("delete")) ? true : false;
			boolean ad = "true".equals(root.getAttribute("add")) ? true : false;

			Node typesNode = doc.getElementsByTagName("types").item(0);
			Node types = typesNode.getFirstChild();
			String typesText = "";
			if( types != null )
				typesText = types.getNodeValue();

			Node rulesNode = doc.getElementsByTagName("rules").item(0);
			Node rules = rulesNode.getFirstChild();
			String rulesText = "";
			if( rules != null )
				rulesText = rules.getNodeValue();

			sql = "UPDATE rule_table SET RD=?, WR=?, DL=?, AD=?, " +
					"types_id=?, rules_id=? " +
					"WHERE rule_id=? AND role=?";
			st = connection.prepareStatement(sql);
			st.setBoolean(1, rd);
			st.setBoolean(2, wr);
			st.setBoolean(3, dl);
			st.setBoolean(4, ad);
			st.setString(5, typesText);
			st.setString(6, rulesText);
			st.setInt(7, macro);
			st.setString(8, role);

			output = st.executeUpdate();
			st.close();
		}
		catch( Exception e )
		{
			e.printStackTrace();
		}

		return output.toString();
	}

	@Override
	public String deleteMacro( int userId, Integer macro )
	{
		// Retrait d'une règle et les commandes associée
		if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		String sql = "";
		PreparedStatement st;

		try
		{
			sql = "DELETE ri, rt FROM rule_info AS ri " +
					"LEFT JOIN rule_table AS rt ON ri.rule_id=rt.rule_id " +
					"WHERE ri.rule_id=?";
			if (dbserveur.equals("oracle")){
				sql = "DELETE FROM rule_info AS ri WHERE ri.rule_id=?";
			}
			st = connection.prepareStatement(sql);
			st.setInt(1, macro);
			st.executeUpdate();
			st.close();
		}
		catch( SQLException e )
		{
			e.printStackTrace();
		}

		return "OK";
	}

	@Override
	public String deleteMacroAction( int userId, Integer macro, String role )
	{
		if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		String sql = "";
		PreparedStatement st;

		try
		{
			sql = "DELETE FROM rule_table " +
					"WHERE rule_id=? AND role=?";
			st = connection.prepareStatement(sql);
			st.setInt(1, macro);
			st.setString(2, role);
			st.executeUpdate();
			st.close();
		}
		catch( SQLException e )
		{
			e.printStackTrace();
		}

		return "OK";
	}


	/*************************************/
	/** Types, gestion des types et cie
	 *
	 *  ####### ##   ## ######  #######  #####
	 *    ##    ##   ## ##   ## ##      ##   ##
	 *    ##     #  ##  ##   ## ##      ##
	 *    ##      ##    ######  #####     ###
	 *    ##      ##    ##      ##           ##
	 *    ##      ##    ##      ##      ##   ##
	 *    ##      ##    ##      #######  #####
	 **/
	/************************************/

	@Override
	public String postCreateType( int userId, String name )
	{
		if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		String sql = "";
		PreparedStatement st;
		Integer output=0;

		try
		{
			sql = "INSERT INTO definition_info(label) VALUE(?)";
			st = connection.prepareStatement(sql);
			st.setString(1, name);
			st.executeUpdate();

			ResultSet rs = st.getGeneratedKeys();
			if( rs.next() )
				output = rs.getInt(1);

			st.close();
		}
		catch( SQLException e )
		{
			e.printStackTrace();
		}

		return output.toString();
	}

	@Override
	public String postAddNodeType( int userId, Integer type, Integer nodeid, Integer parentid, Integer instance, String data )
	{
		if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		/**
		 * Format que l'on reçoit:
		 * <asm*>
		 *   <asmResource xsi_type='nodeRes'>{node_data}</asmResource>
		 *   <asmResource xsi_type='context'>{node_data}</asmResource>
		 *   <asmResource xsi_type='*'>{node_data}</asmResource>
		 * </asm*>
		 */

		String sql = "";
		PreparedStatement st;
		Integer output=0;
		Integer parentId=0;

		String asmtype = "";
		String xsitype = "";
		try
		{
			/// Prépare les données pour les requêtes
			// Parse
			DocumentBuilderFactory documentBuilderFactory =DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
			Document document=documentBuilder.parse(new ByteArrayInputStream(data.getBytes("UTF-8")));

			// Traite le noeud racine des données, retourne l'identifiant du noeud racine
			Element nodeData = document.getDocumentElement();
			asmtype = nodeData.getNodeName();

			connection.setAutoCommit(true);

			// Utilise parentid si on rattache un autre groupe de noeud en dessous d'un noeud existant
			sql = "INSERT INTO definition_type(def_id,asm_type,parent_node,instance_rule) " +
					"VALUE(?,?,?,?)";
			st = connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);
			if (dbserveur.equals("oracle")){
				  st = connection.prepareStatement(sql, new String[]{"node_id"});
			}
			st.setInt(1,type);
			st.setString(2,asmtype);

			if( parentid == null ) st.setNull(3, Types.BIGINT);
			else st.setInt(3,parentid);

			if( instance == null ) st.setNull(4, Types.BIGINT);
			else st.setInt(4,instance);

			output = st.executeUpdate();
			ResultSet key = st.getGeneratedKeys();
			// On récupère l'identifiant du noeud 'racine' des données ajoutés
			if( key.next() )
				parentId = key.getInt(1);
			st.close();

			// Soit 2 ou 3 resources
			asmtype = "asmResource";
			NodeList resources = document.getElementsByTagName("asmResource");
			sql = "INSERT INTO definition_type(def_id,asm_type,xsi_type,parent_node,node_data,instance_rule) " +
					"VALUE(?,?,?,?,?,?)";
			st = connection.prepareStatement(sql);
			st.setInt(1, type);
			st.setString(2, asmtype);
			st.setInt(4, parentId);

			for( int i=0; i<resources.getLength(); ++i )
			{
				Element resource = (Element) resources.item(i);
				xsitype = resource.getAttribute("xsi_type");
				String resContent = DomUtils.getInnerXml(resource);

				st.setString(3,xsitype);
				st.setString(5,resContent);

				if( instance == null ) st.setNull(6, Types.BIGINT);
				else st.setInt(6,instance);

				// On ajoute les données des ressources restante
				output = st.executeUpdate();

			}
			st.close();
		}
		catch( Exception e )
		{
			try{ connection.rollback(); }
			catch( SQLException e1 ){ e1.printStackTrace(); }
			e.printStackTrace();
		}
		finally
		{
			try
			{ connection.setAutoCommit(true);
			connection.close(); }
			catch( SQLException e ){ e.printStackTrace(); }
		}

		return output.toString();
	}

	@Override
	public String postUseType( int userId, String nodeUuid, Integer type )
	{
		String sql = "";
		PreparedStatement st;
		String createdUuid = "erreur";

		try
		{
			/// Vérifie si l'utilisateur a le droit d'ajouter ce type
			sql = "SELECT userid FROM group_user gu LEFT JOIN group_info gi ON gu.gid=gi.gid " +
					"LEFT JOIN group_rights gr ON gi.grid=gr.grid " +
					"WHERE gu.userid=? AND id=uuid2bin(?) AND types_id LIKE CONCAT('%',?,'%')";
			st = connection.prepareStatement(sql);
			st.setInt(1, userId);
			st.setString(2, nodeUuid);
			st.setInt(3, type);
			ResultSet res = st.executeQuery();

			/// res.getFetchSize() retourne 0, même avec un bon résultat
			int uid=0;
			if( res.next() )
				uid = res.getInt("userid");
			if( uid != userId ) return "";

			res.close();
			st.close();

			/// Ça devient sérieux
			// Crée les tables temporaires pour les données et droits
			sql = "CREATE TEMPORARY TABLE t_data(" +
					"node_id BIGINT," +
					"asm_type VARCHAR(50)," +
					"xsi_type VARCHAR(50)," +
					"parent_node BIGINT," +
					"node_data TEXT," +
					"instance_rule BIGINT," +
					"node_uuid BINARY(16)," +
					"node_parent_uuid BINARY(16)," +
					"node_children_uuid TEXT," +
					"res_node_uuid BINARY(16)," +
					"res_res_node_uuid BINARY(16)," +
					"res_context_node_uuid BINARY(16))";
			if (dbserveur.equals("oracle")){
			      sql = "CREATE GLOBAL TEMPORARY TABLE t_data(" +
			        		"node_id NUMBER(19,0)," +
			        		"asm_type VARCHAR2(50 CHAR)," +
			        		"xsi_type VARCHAR2(50 CHAR)," +
			        		"parent_node NUMBER(19,0)," +
			        		"node_data CLOB," +
			        		"instance_rule NUMBER(19,0)," +
			        		"node_uuid RAW(16)," +
			        		"node_parent_uuid RAW(16)," +
			        		"node_children_uuid CLOB," +
			        		"res_node_uuid RAW(16)," +
			        		"res_res_node_uuid RAW(16)," +
			        		"res_context_node_uuid RAW(16)) ON COMMIT PRESERVE ROWS";
			}
			st = connection.prepareStatement(sql);
			st.execute();
			st.close();

			sql = "CREATE TEMPORARY TABLE t_rights(" +
					"grid BIGINT," +
					"id BINARY(16)," +
					"role VARCHAR(255)," +
					"RD BOOLEAN," +
					"WR BOOLEAN," +
					"DL BOOLEAN," +
					"AD BOOLEAN," +
					"types_id TEXT," +
					"rules_id TEXT)";
			if (dbserveur.equals("oracle")){
			      sql = "CREATE GLOBAL TEMPORARY TABLE t_rights(" +
			        		"grid NUMBER(19,0)," +
			        		"id RAW(16)," +
			        		"role VARCHAR2(255 CHAR)," +
			        		"RD NUMBER(1)," +
			        		"WR NUMBER(1)," +
			        		"DL NUMBER(1)," +
			        		"AD NUMBER(1)," +
			        		"types_id VARCHAR2(2000 CHAR)," +
			        		"rules_id VARCHAR2(2000 CHAR)) ON COMMIT PRESERVE ROWS";
			}
			st = connection.prepareStatement(sql);
			st.execute();

			sql = "CREATE TEMPORARY TABLE t_struc(" +
					"node_id BIGINT," +
					"parent_node BIGINT," +
					"node_uuid BINARY(16)," +
					"asm_type VARCHAR(50)," +
					"xsi_type VARCHAR(50))";
			if (dbserveur.equals("oracle")){
			      sql = "CREATE GLOBAL TEMPORARY TABLE t_struc(" +
			        		"node_id NUMBER(19,0)," +
			        		"parent_node NUMBER(19,0)," +
			        		"node_uuid RAW(16)," +
			        		"asm_type VARCHAR2(50 CHAR)," +
			        		"xsi_type VARCHAR2(50 CHAR)) ON COMMIT PRESERVE ROWS";
			}
			st = connection.prepareStatement(sql);
			st.execute();
			st.close();

			/// Instancie les données nécéssaire et génère les uuid
			sql = "INSERT INTO t_data(node_id,asm_type,xsi_type,parent_node,node_data,instance_rule,node_uuid) " +
					"SELECT node_id,asm_type,xsi_type,parent_node,node_data,instance_rule, ";
			if (dbserveur.equals("mysql")){
				sql += "uuid2bin(UUID()) ";
			} else if (dbserveur.equals("oracle")){
				sql += "sys_guid() ";
			}	
			sql += "FROM definition_type WHERE def_id=?";
			st = connection.prepareStatement(sql);
			st.setInt(1, type);
			st.executeUpdate();
			st.close();

			/// Instancie les droits pour chaque donnée
			// SELECT bin2uuid(node_uuid), role FROM t_data d, rule_table rt WHERE d.instance_rule=rt.rule_id;
			// SELECT grid,gri.label FROM group_right_info gri LEFT JOIN node n ON gri.portfolio_id=n.portfolio_id WHERE n.node_uuid=uuid2bin('d48cafa1-5180-4c83-9e22-5d4d45bbf6e2');
			// SELECT bin2uuid(d.node_uuid),role FROM t_data d LEFT JOIN rule_table rt ON d.instance_rule=rt.rule_id LEFT JOIN group_right_info gri ON rt.role=gri.label LEFT JOIN node n ON gri.portfolio_id=n.portfolio_id WHERE n.node_uuid=uuid2bin('d48cafa1-5180-4c83-9e22-5d4d45bbf6e2')
			sql = "INSERT INTO t_rights(grid,id,role,RD,WR,DL,AD,types_id,rules_id) " +
					"SELECT grid,d.node_uuid,role,RD,WR,DL,AD,types_id,rules_id " +
					"FROM t_data d " +
					"LEFT JOIN rule_table rt ON d.instance_rule=rt.rule_id " +
					"LEFT JOIN group_right_info gri ON rt.role=gri.label " +
					"LEFT JOIN node n ON gri.portfolio_id=n.portfolio_id " +
					"WHERE n.node_uuid=uuid2bin(?)";
			st = connection.prepareStatement(sql);
			st.setString(1, nodeUuid);
			st.executeUpdate();
			st.close();

			/// Fait la résolution des liens avec parents
			// On ne peut pas faire de self-join sur une table temporaire...
			// Relier le noeud 'root'
			sql = "UPDATE t_data SET node_parent_uuid=uuid2bin(?) WHERE parent_node IS NULL";
			st = connection.prepareStatement(sql);
			st.setString(1, nodeUuid);
			st.executeUpdate();
			st.close();

			// Copie les infos minimum pour mettre à jour les uuid des parents
			// INSERT INTO t_struc(node_id, node_uuid) SELECT node_id,node_uuid FROM t_data;
			sql = "INSERT INTO t_struc(node_id, parent_node, node_uuid, asm_type, xsi_type) " +
					"SELECT node_id, parent_node, node_uuid, asm_type, xsi_type FROM t_data";
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			// Mise à jour des uuid des parents
			// UPDATE t_data t SET node_parent_uuid=(SELECT node_uuid FROM t_struc s WHERE t.parent_node=s.node_id);
			sql = "UPDATE t_data t SET node_parent_uuid=(SELECT node_uuid " +
					"FROM t_struc s WHERE t.parent_node=s.node_id)";
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			// Faire la liste des child_nodes
			// UPDATE t_data t SET node_children=(SELECT GROUP_CONCAT(bin2uuid(s.node_uuid)) FROM t_struc s WHERE t.node_id=s.parent_id);
			sql = "UPDATE t_data t SET node_children_uuid=(";
			if (dbserveur.equals("mysql")){
				sql += "SELECT GROUP_CONCAT(bin2uuid(s.node_uuid)) ";
			} else if (dbserveur.equals("oracle")){
				sql += "SELECT LISTAGG(bin2uuid(s.node_uuid), ',') WITHIN GROUP (ORDER BY bin2uuid(s.node_uuid)) ";
			}
			sql += "FROM t_struc s WHERE t.node_id=s.parent_node)";
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			// Ajouter les liens vers asmResource selon le xsi_type (...)
			sql = "UPDATE t_data t SET res_context_node_uuid=(SELECT node_uuid FROM t_struc s WHERE t.node_id=s.parent_node AND xsi_type='context')";
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			sql = "UPDATE t_data t SET res_res_node_uuid=(SELECT node_uuid FROM t_struc s WHERE t.node_id=s.parent_node AND xsi_type='nodeRes')";
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			sql = "UPDATE t_data t SET res_node_uuid=(SELECT node_uuid FROM t_struc s WHERE t.node_id=s.parent_node AND xsi_type NOT IN('nodeRes','context'))";
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			connection.setAutoCommit(false);
			// Copie les données vers la vrai table de structure
			// INSERT INTO node(node_uuid, node_parent_uuid, node_children_uuid, res_node_uuid, res_res_node_uuid, res_context_node_uuid, asm_type, portfolio_id)
			sql = "INSERT INTO node(node_uuid, node_parent_uuid, node_children_uuid, " +
					"res_node_uuid, res_res_node_uuid, res_context_node_uuid, " +
					"node_order, asm_type, portfolio_id," +
					"metadata,metadata_wad,metadata_epm," +
					"shared_res, shared_node, shared_node_res," +
					"modif_user_id) " +
					"SELECT t.node_uuid,t.node_parent_uuid,t.node_children_uuid," +
					"t.res_node_uuid,t.res_res_node_uuid,t.res_context_node_uuid," +
					"t.node_id, t.asm_type, portfolio_id," +
//					"'','',''," +
					"' ',' ',' '," +
//					"false, false, false," +
					"0, 0, 0," +
					"? " +
					"FROM t_data t, node n " +
					"WHERE n.node_uuid=uuid2bin(?) AND t.xsi_type IS NULL";
			st = connection.prepareStatement(sql);
			st.setInt(1, userId);
			st.setString(2, nodeUuid);
			st.executeUpdate();
			st.close();

			// Copie les données vers la vrai table des ressources
			sql = "INSERT INTO resource_table(node_uuid,xsi_type,content,user_id,modif_user_id,modif_date) ";
			if (dbserveur.equals("mysql")){
				sql += "SELECT node_uuid,xsi_type,node_data,?,?,NOW() ";
			} else if (dbserveur.equals("oracle")){
				sql += "SELECT node_uuid,xsi_type,node_data,?,?,CURRENT_TIMESTAMP ";
			}
			sql += "FROM t_data " +
					"WHERE asm_type='asmResource'";
			st = connection.prepareStatement(sql);
			st.setInt(1, userId);
			st.setInt(2, userId);
			st.executeUpdate();
			st.close();

			// Copie les droits vers la vrai table
			sql = "INSERT INTO group_rights(grid,id,RD,WR,DL,AD,types_id,rules_id) " +
					"SELECT grid,id,RD,WR,DL,AD,types_id,rules_id FROM t_rights";
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			// On récupère la racine créé
			sql = "SELECT bin2uuid(node_uuid) AS uuid FROM t_data WHERE parent_node IS NULL";
			st = connection.prepareStatement(sql);
			res = st.executeQuery();

			if( res.next() )
				createdUuid = res.getString("uuid");
		}
		catch( Exception e )
		{
			try
			{
				connection.rollback();
				createdUuid = "erreur";
			}
			catch( SQLException e1 ){ e1.printStackTrace(); }
			e.printStackTrace();
		}
		finally
		{
			try
			{
				connection.setAutoCommit(true);
				// Les 'pooled connection' ne se ferment pas vraiment. On nettoie manuellement les tables temporaires...
				if (dbserveur.equals("mysql")){
					sql = "DROP TEMPORARY TABLE IF EXISTS t_data, t_struc, t_rights";
					st = connection.prepareStatement(sql);
					st.execute();
					st.close();
				} else if (dbserveur.equals("oracle")){
		        	sql = "{call drop_tables(tmpTableList('t_data', ,'t_struc', 't_rights'))}";
		            CallableStatement ocs = connection.prepareCall(sql) ;
		            ocs.execute(); 
		            ocs.close();
				}

				connection.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}

		return createdUuid;
	}

	@Override
	public String getAllTypes( int userId )
	{
		if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		String sql = "";
		PreparedStatement st;

		ResultSet res=null;
		try
		{
			sql = "SELECT * FROM definition_info";
			st = connection.prepareStatement(sql);
			res = st.executeQuery();

			/// Time to create data
			DocumentBuilderFactory documentBuilderFactory =DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder;
			Document document=null;

			documentBuilder = documentBuilderFactory.newDocumentBuilder();
			document = documentBuilder.newDocument();

			Element root = document.createElement("types");
			document.appendChild(root);

			while( res.next() )
			{
				String label = res.getString("label");
				Integer id = res.getInt("def_id");

				Element macro = document.createElement("type");
				macro.setAttribute("id", id.toString());
				Text textLabel = document.createTextNode(label);
				macro.appendChild(textLabel);

				root.appendChild(macro);
			}
			res.close();
			st.close();

			StringWriter stw = new StringWriter();
			Transformer serializer = TransformerFactory.newInstance().newTransformer();
			serializer.transform(new DOMSource(document), new StreamResult(stw));
			return stw.toString();
		}
		catch( Exception e )
		{
			e.printStackTrace();
		}

		return "";
	}

	@Override
	public String getTypeData( int userId, Integer type )
	{
		String sql = "";
		PreparedStatement st;

		HashMap<Integer, Node> resolve = new HashMap<Integer, Node>();
		/// Node -> parent
		ArrayList<Object[]> entries = new ArrayList<Object[]>();

		ResultSet res=null;
		try
		{
			sql = "SELECT node_id, asm_type, xsi_type, parent_node, node_data, instance_rule " +
					"FROM definition_type " +
					"WHERE def_id=?";
			st = connection.prepareStatement(sql);
			st.setInt(1, type);
			res = st.executeQuery();

			/// Time to create data
			DocumentBuilderFactory documentBuilderFactory =DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder;
			Document document=null;

			documentBuilder = documentBuilderFactory.newDocumentBuilder();
			document = documentBuilder.newDocument();

			Element root = document.createElement("type");
			root.setAttribute("typeid", type.toString());
			document.appendChild(root);

			while( res.next() )
			{
				Integer id = res.getInt("node_id");
				String asmtype = res.getString("asm_type");
				String xsitype = res.getString("xsi_type");
				Integer parent = res.getInt("parent_node");
				if( res.wasNull() )
					parent = null;
				String data = res.getString("node_data");
				Integer instance = res.getInt("instance_rule");

				Element nodeData = null;
				if( data != null )  // Une ressource
				{
					data = "<asmResource>"+data+"</asmResource>";
					Document frag = documentBuilder.parse(new ByteArrayInputStream(data.getBytes("UTF-8")));
					nodeData = frag.getDocumentElement();
					nodeData.setAttribute("id", id.toString());
					nodeData.setAttribute("instance", instance.toString());
					document.adoptNode(nodeData);
					nodeData.setAttribute("xsi_type", xsitype);
				}
				else    // Un bout de structure
				{
					nodeData = document.createElement(asmtype);
				}

				/// Info pour reconstruire l'arbre
				resolve.put(id, nodeData);
				Object[] obj = {nodeData, parent};
				entries.add(obj);
			}
			res.close();
			st.close();

			/// Reconstruction
			Iterator<Object[]> iter = entries.iterator();
			while(iter.hasNext())
			{
				Object obj[] = iter.next();
				Node node = (Node) obj[0];
				Integer parent = (Integer) obj[1];
				if( parent != null )
				{
					Node parentNode = resolve.get(parent);
					parentNode.appendChild(node);
				}
				else
					root.appendChild(node);
			}

			StringWriter stw = new StringWriter();
			Transformer serializer = TransformerFactory.newInstance().newTransformer();
			serializer.transform(new DOMSource(document), new StreamResult(stw));
			return stw.toString();
		}
		catch( Exception e )
		{
			e.printStackTrace();
		}

		return "";
	}

	@Override
	public String getPortfolioTypes( int userId, String portfolioId )
	{
		/// Récupère la liste des types utilisé dans le portfolio
		String sql = "";
		PreparedStatement st;

		ResultSet res=null;
		try
		{
			sql = "SELECT di.def_id, di.label FROM definition_info di WHERE EXISTS " +
					"(SELECT gr.types_id FROM group_right_info gri " +
					"LEFT JOIN group_rights gr ON gri.grid=gr.grid " +
					"WHERE portfolio_id=uuid2bin(?) " +
					"HAVING def_id LIKE CONCAT('%', di.def_id, '%') )";
			st = connection.prepareStatement(sql);
			st.setString(1, portfolioId);
			res = st.executeQuery();

			/// Time to create data
			DocumentBuilderFactory documentBuilderFactory =DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder;
			Document document=null;

			documentBuilder = documentBuilderFactory.newDocumentBuilder();
			document = documentBuilder.newDocument();

			Element root = document.createElement("types");
			document.appendChild(root);

			while( res.next() )
			{
				Integer id = res.getInt("def_id");
				String label = res.getString("label");

				Element action = document.createElement("type");
				action.setAttribute("id", id.toString());
				Text labelNode = document.createTextNode(label);
				action.appendChild(labelNode);

				root.appendChild(action);
			}
			res.close();
			st.close();

			StringWriter stw = new StringWriter();
			Transformer serializer = TransformerFactory.newInstance().newTransformer();
			serializer.transform(new DOMSource(document), new StreamResult(stw));
			return stw.toString();
		}
		catch( Exception e )
		{
			e.printStackTrace();
		}

		return "";
	}

	@Override
	public String putTypeName( int userId, Integer type, String name )
	{
		if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		String sql = "";
		PreparedStatement st;

		try
		{
			sql = "UPDATE definition_info SET label=? " +
					"WHERE def_id=?";
			st = connection.prepareStatement(sql);
			st.setString(1, name);
			st.setInt(2, type);
			st.executeUpdate();
			st.close();
		}
		catch( SQLException e )
		{
			e.printStackTrace();
		}

		return "OK";
	}

	@Override
	public String putTypeData( int userId, Integer type, Integer nodeid, Integer parentid, Integer instance, String data )
	{
		if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		String sql = "";
		PreparedStatement st;
		Integer output=0;
		ArrayList<Integer> index = new ArrayList<Integer>();

		try
		{
			sql = "UPDATE definition_type SET";
			if( parentid != null )
			{
				index.add( parentid );
				sql = sql + " parent_node=?";
			}
			if( instance != null )
			{
				int length = index.size();
				if( length > 0 )
					sql = sql + ",";

				index.add( instance );
				sql = sql + " instance_rule=?";
			}

			if( parentid == null && instance == null && data != null )
				sql = sql + " node_data=?";

			sql = sql + " WHERE def_id=? AND node_id=?";
			st = connection.prepareStatement(sql);

			int i=0;
			for(; i<index.size(); ++i)
				st.setInt(i+1, index.get(i));
			if( i == 0 )
				st.setString(++i, data);

			st.setInt(++i, type);
			st.setInt(++i, nodeid);

			output = st.executeUpdate();
			st.close();
		}
		catch( Exception e )
		{
			e.printStackTrace();
		}

		return output.toString();
	}

	@Override
	public String deleteType( int userId, Integer type )
	{
		if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		String sql = "";
		PreparedStatement st;

		try
		{
			sql = "DELETE di, dt FROM definition_info AS di " +
					"LEFT JOIN definition_type AS dt ON di.def_id=dt.def_id " +
					"WHERE di.def_id=?";
			if (dbserveur.equals("oracle")){
				sql = "DELETE FROM definition_info AS di WHERE di.def_id=?";
			}
			st = connection.prepareStatement(sql);
			st.setInt(1, type);
			st.executeUpdate();
			st.close();
		}
		catch( SQLException e )
		{
			e.printStackTrace();
		}

		return "OK";
	}

	@Override
	public String deleteTypeNode( int userId, Integer type, Integer nodeid )
	{
		if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		String sql = "";
		PreparedStatement st;

		try
		{
			sql = "DELETE FROM definition_type " +
					"WHERE def_id=? AND node_id=?";
			st = connection.prepareStatement(sql);
			st.setInt(1, type);
			st.setInt(2, nodeid);
			st.executeUpdate();
			st.close();
		}
		catch( SQLException e )
		{
			e.printStackTrace();
		}

		return "OK";
	}

	/*************************************/
	/** Groupe de droits et cie        **/
	/************************************/

	@Override
	public String getRRGList( int userId, String portfolio, Integer user, String role )
	{
		String sql = "";
		PreparedStatement st;

		ResultSet res=null;
		try
		{
			boolean bypass = false;
			sql = "";
			if( portfolio != null && user != null )   // Intersection d'un portfolio et utilisateur
			{
				sql = "SELECT DISTINCT gri.grid, label, bin2uuid(gri.portfolio_id) AS portfolio " +
						"FROM resource_table r " +
						"LEFT JOIN group_rights gr ON r.node_uuid=gr.id " +
						"LEFT JOIN group_right_info gri ON gr.grid=gri.grid " +
						"WHERE r.user_id=? AND gri.portfolio_id=uuid2bin(?)";
				st = connection.prepareStatement(sql);
				st.setInt(1, user);
				st.setString(2, portfolio);
			}
			else if( portfolio != null && role != null )  // Intersection d'un portfolio et role
			{
				sql = "SELECT DISTINCT gri.grid, label, bin2uuid(gri.portfolio_id) AS portfolio " +
						"FROM group_right_info gri " +
						"WHERE gri.label=? AND gri.portfolio_id=uuid2bin(?)";
				st = connection.prepareStatement(sql);
				st.setString(1, role);
				st.setString(2, portfolio);

				bypass = true;
			}
			else if( portfolio != null )  // Juste ceux relié à un portfolio
			{
				sql = "SELECT grid, label, bin2uuid(gri.portfolio_id) AS portfolio " +
						"FROM group_right_info gri " +
						"WHERE gri.portfolio_id=uuid2bin(?)";
				st = connection.prepareStatement(sql);
				st.setString(1, portfolio);
			}
			else if( user != null )   // Juste ceux relié à une personne
			{
				/// Requête longue, il faudrait le prendre d'un autre chemin avec ensemble plus petit, si possible
				sql = "SELECT DISTINCT gri.grid, label, bin2uuid(gri.portfolio_id) AS portfolio " +
						"FROM resource_table r " +
						"LEFT JOIN group_rights gr ON r.node_uuid=gr.id " +
						"LEFT JOIN group_right_info gri ON gr.grid=gri.grid " +
						"WHERE r.user_id=?";
				st = connection.prepareStatement(sql);
				st.setInt(1, user);
			}
			else  // Tout les groupe disponible
			{
				sql = "SELECT grid, label, bin2uuid(gri.portfolio_id) AS portfolio " +
						"FROM group_right_info gri";
				st = connection.prepareStatement(sql);
			}

			res = st.executeQuery();

			/// Time to create data
			DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
			Document document= documentBuilder.newDocument();

			Element root = document.createElement("rolerightsgroups");
			document.appendChild(root);

			if( bypass )  // WAD6 demande un format sp�cifique pour ce type de requ�te (...)
			{
				if( res.next() )
				{
					Integer id = res.getInt("grid");
					if( id == null || id == 0 )
						return "";
					else
						return id.toString();
				}
			}
			else
				while( res.next() )
				{
					Integer id = res.getInt("grid");
					if( id == null || id == 0 )    // Bonne chances que ce soit vide
						continue;

					Element rrg = document.createElement("rolerightsgroup");
					rrg.setAttribute("id", id.toString());
					root.appendChild(rrg);

					String label = res.getString("label");
					Element labelNode = document.createElement("label");
					rrg.appendChild(labelNode);
					if( label != null )
						labelNode.appendChild(document.createTextNode(label));

					String pid = res.getString("portfolio");
					Element portfolioNode = document.createElement("portfolio");
					portfolioNode.setAttribute("id", pid);
					rrg.appendChild(portfolioNode);
				}

			res.close();
			st.close();

			StringWriter stw = new StringWriter();
			Transformer serializer = TransformerFactory.newInstance().newTransformer();
			DOMSource source = new DOMSource(document);
			StreamResult stream = new StreamResult(stw);
			serializer.transform(source, stream);
			return stw.toString();
		}
		catch( Exception e )
		{
			e.printStackTrace();
		}

		return "";
	}

	@Override
	public String getRRGInfo( int userId, Integer rrgid )
	{
		String sql = "";
		PreparedStatement st;

		ResultSet res=null;
		try
		{
			sql = "SELECT gri.grid, gri.label, bin2uuid(gri.portfolio_id) AS portfolio, c.userid, c.login, " +
					"c.display_firstname, c.display_lastname, c.email " +
					"FROM group_right_info gri " +
					"LEFT JOIN group_info gi ON gri.grid=gi.grid " +
					"LEFT JOIN group_user gu ON gi.gid=gu.gid " +
					"LEFT JOIN credential c ON gu.userid=c.userid " +
					"WHERE gri.grid=?";
			st = connection.prepareStatement(sql);
			st.setInt(1, rrgid);
			res = st.executeQuery();

			/// Time to create data
			DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
			Document document= documentBuilder.newDocument();

			Element root = document.createElement("rolerightsgroup");
			root.setAttribute("id", rrgid.toString());
			document.appendChild(root);

			Element usersNode=null;
			if( res.next() )  //
			{
				String label = res.getString("label");
				String portfolioid = res.getString("portfolio");

				Element labelNode = document.createElement("label");
				labelNode.appendChild(document.createTextNode(label));
				root.appendChild(labelNode);

				Element portfolioNode = document.createElement("portofolio");
				portfolioNode.setAttribute("id", portfolioid);
				root.appendChild(portfolioNode);

				usersNode = document.createElement("users");
				root.appendChild(usersNode);
			}
			else
				return "";

			do
			{
				Integer id = res.getInt("userid");
				if( id == null )    // Bonne chances que ce soit vide
					continue;

				Element userNode = document.createElement("user");
				userNode.setAttribute("id", id.toString());
				usersNode.appendChild(userNode);

				String login = res.getString("login");
				Element usernameNode = document.createElement("username");
				userNode.appendChild(usernameNode);
				if( login != null )
					usernameNode.appendChild(document.createTextNode(login));

				String firstname = res.getString("display_firstname");
				Element fnNode = document.createElement("firstname");
				userNode.appendChild(fnNode);
				if( firstname != null )
					fnNode.appendChild(document.createTextNode(firstname));

				String lastname = res.getString("display_lastname");
				Element lnNode = document.createElement("lastname");
				userNode.appendChild(lnNode);
				if( lastname != null )
					lnNode.appendChild(document.createTextNode(lastname));

				String email = res.getString("email");
				Element eNode = document.createElement("email");
				userNode.appendChild(eNode);
				if( email != null )
					eNode.appendChild(document.createTextNode(email));

			} while( res.next() );

			res.close();
			st.close();

			StringWriter stw = new StringWriter();
			Transformer serializer = TransformerFactory.newInstance().newTransformer();
			DOMSource source = new DOMSource(document);
			StreamResult stream = new StreamResult(stw);
			serializer.transform(source, stream);
			return stw.toString();
		}
		catch( Exception e )
		{
			e.printStackTrace();
		}

		return "";
	}

	/// Liste des RRG et utilisateurs d'un portfolio donn�
	@Override
	public String getPortfolioInfo( int userId, String portId )
	{
		String status = "erreur";
		PreparedStatement st;
		ResultSet res=null;

		try
		{
			// group_right_info pid:grid -> group_info grid:gid -> group_user gid:userid
			String sql = "SELECT gri.grid AS grid, gri.label AS label, gu.userid AS userid " +
					"FROM group_right_info gri " +
					"LEFT JOIN group_info gi ON gri.grid=gi.grid " +
					"LEFT JOIN group_user gu ON gi.gid=gu.gid " +
					"WHERE gri.portfolio_id=uuid2bin(?)";
			st = connection.prepareStatement(sql);
			st.setString(1, portId);
			res = st.executeQuery();

			/// Time to create data
			DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
			Document document= documentBuilder.newDocument();

			Element root = document.createElement("portfolio");
			root.setAttribute("id", portId);
			document.appendChild(root);

			Element rrgUsers = null;

			long rrg = 0;
			while( res.next() )
			{
				if( rrg != res.getLong("grid") )
				{
					rrg = res.getLong("grid");
					Element rrgNode = document.createElement("rrg");
					rrgNode.setAttribute("id", Long.toString(rrg));

					Element rrgLabel = document.createElement("label");
					rrgLabel.setTextContent(res.getString("gri.label"));

					rrgUsers = document.createElement("users");

					rrgNode.appendChild(rrgLabel);
					rrgNode.appendChild(rrgUsers);
					root.appendChild(rrgNode);
				}

				Long uid = res.getLong("userid");
				if( !res.wasNull() )
				{
					Element user = document.createElement("user");
					user.setAttribute("id", Long.toString(uid));

					rrgUsers.appendChild(user);
				}
			}

			res.close();
			st.close();

			StringWriter stw = new StringWriter();
			Transformer serializer = TransformerFactory.newInstance().newTransformer();
			DOMSource source = new DOMSource(document);
			StreamResult stream = new StreamResult(stw);
			serializer.transform(source, stream);
			return stw.toString();
		}
		catch( Exception e )
		{
			e.printStackTrace();
		}
		finally
		{
			try
			{
				connection.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}


		return status;
	}


	@Override
	public String putRRGUpdate( int userId, Integer rrgId, String data )
	{
		if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		String sql = "";
		PreparedStatement st;

		/// Parse data
		DocumentBuilder documentBuilder;
		Document document = null;
		try
		{
			DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
			documentBuilder = documentBuilderFactory.newDocumentBuilder();
			InputSource is = new InputSource(new StringReader(data));
			document= documentBuilder.parse(is);
		}
		catch( Exception e )
		{
			e.printStackTrace();
		}

		/// Problème de parsage
		if( document == null ) return "erreur";

		NodeList labelNodes = document.getElementsByTagName("label");
		Node labelNode = labelNodes.item(0);
		String sqlLabel = "";
		ArrayList<String> text = new ArrayList<String>();
		if( labelNode != null )
		{
			Node labelText = labelNode.getFirstChild();
			if( labelText != null )
			{
				text.add(labelText.getNodeValue());
				sqlLabel = "SET label=? ";
			}
		}

		NodeList portfolioNodes = document.getElementsByTagName("portfolio");
		Element portfolioNode = (Element) portfolioNodes.item(0);
		String sqlPid = "";
		if( portfolioNode != null )
		{
			text.add(portfolioNode.getAttribute("id"));
			sqlPid = "SET portfolio_id=? ";
		}

		// Il faut au moins 1 paramètre à changer
		if( text.isEmpty() ) return "";

		try
		{
			sql = "UPDATE group_right_info "+sqlLabel+sqlPid+"WHERE grid=?";
			st = connection.prepareStatement(sql);

			for( int i=0; i<text.size(); ++i )
				st.setString(i+1, text.get(i));

			st.setInt(text.size()+1, rrgId);

			st.executeQuery();
		}
		catch( Exception e )
		{
			e.printStackTrace();
		}

		return "";
	}


	@Override
	public String postRRGCreate( int userId, String data )
	{
		if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		String value="erreur";
		/// Parse data
		DocumentBuilder documentBuilder;
		Document document = null;
		try
		{
			DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
			documentBuilder = documentBuilderFactory.newDocumentBuilder();
			InputSource is = new InputSource(new StringReader(data));
			document= documentBuilder.parse(is);
		}
		catch( Exception e )
		{
			e.printStackTrace();
		}

		/// Problème de parsage
		if( document == null ) return value;

		try
		{
			connection.setAutoCommit(false);
			NodeList rrgNodes = document.getElementsByTagName("rolerightsgroup");

			String sqlRRG = "INSERT INTO group_right_info(owner,label,portfolio_id) VALUES(?,?,uuid2bin(?))";
			PreparedStatement rrgst = connection.prepareStatement(sqlRRG);
			rrgst.setInt(1, userId);
			String sqlGU = "INSERT INTO group_info(grid,owner,label) VALUES(?,?,?)";
			PreparedStatement gust = connection.prepareStatement(sqlGU);
			gust.setInt(2, userId);
			for( int i=0; i<rrgNodes.getLength(); ++i )
			{
				Element rrgNode = (Element) rrgNodes.item(i);
				NodeList labelNodes = rrgNode.getElementsByTagName("label");
				Node labelNode = labelNodes.item(0);
				String label = "";
				if( labelNode != null )
				{
					Node labelText = labelNode.getFirstChild();
					if( labelText != null )
						label = labelText.getNodeValue();
				}

				NodeList portfolioNodes = rrgNode.getElementsByTagName("portfolio");
				Element portfolioNode = (Element) portfolioNodes.item(0);
				String pid = "";
				if( portfolioNode != null )
					pid = portfolioNode.getAttribute("id");

				/// Création du groupe de droit
				rrgst.setString(2, label);
				rrgst.setString(3, pid);
				rrgst.executeUpdate();

				/// Récupère l'identifiant du RRG, à utiliser lors de l'ajout d'utilisateurs
				ResultSet rs = rrgst.getGeneratedKeys();
				Integer rrgid=0;
				if( rs.next() )
					rrgid = rs.getInt(1);
				rs.close();
				/// Met à jour le noeud pour plus tard
				rrgNode.setAttribute("id", rrgid.toString());

				/// Création du groupe d'utilisateur
				gust.setInt(1, rrgid);
				gust.setString(3, label);
				gust.executeUpdate();
				rs = gust.getGeneratedKeys();
				Integer gid=0;
				if( rs.next() )
					gid = rs.getInt(1);
				rs.close();

				/// Ajout des utilisateurs au groupe d'utilisateur
				NodeList users = rrgNode.getElementsByTagName("user");
				String sqlUser = "INSERT INTO group_user(gid,userid) VALUES(?,?)";
				PreparedStatement st = connection.prepareStatement(sqlUser);
				st.setInt(1, gid);
				for( int j=0; j<users.getLength(); ++j )
				{
					Element user = (Element) users.item(j);
					String uidl = user.getAttribute("id");
					Integer uid = Integer.valueOf(uidl);
					st.setInt(2, uid);
					st.executeUpdate();
				}
				st.close();
			}
			rrgst.close();

			/// Récupère les données avec identifiant mis-à-jour
			StringWriter stw = new StringWriter();
			Transformer serializer = TransformerFactory.newInstance().newTransformer();
			DOMSource source = new DOMSource(document);
			StreamResult stream = new StreamResult(stw);
			serializer.transform(source, stream);
			value = stw.toString();
		}
		catch( Exception e )
		{
			try{ connection.rollback(); }
			catch( SQLException e1 ){ e1.printStackTrace(); }
			e.printStackTrace();
		}
		finally
		{
			try
			{
				connection.setAutoCommit(true);
				connection.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}

		return value;
	}

	@Override
	public String postRRGUser( int userId, Integer rrgid, Integer user )
	{
		//    if(!credential.isAdmin(userId))
		//      throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		String value="";
		ResultSet res=null;
		try
		{
			connection.setAutoCommit(false);

			/// V�rifie si un group_info/grid existe
			String sqlCheck = "SELECT gid FROM group_info WHERE grid=?";
			PreparedStatement st = connection.prepareStatement(sqlCheck);
			st.setInt(1, rrgid);
			res = st.executeQuery();

			if(!res.next())
			{
				/// Copie de RRG vers group_info
				String sqlCopy = "INSERT INTO group_info(grid,owner,label)" +
						" SELECT grid,owner,label FROM group_right_info WHERE grid=?";
				st = connection.prepareStatement(sqlCopy);
				st.setInt(1, rrgid);
				st.executeUpdate();
				st.close();
			}

			/// Ajout des utilisateurs
	        String sqlUser = "";
			if (dbserveur.equals("mysql")){
				sqlUser = "INSERT IGNORE INTO group_user(gid,userid) ";
			} else if (dbserveur.equals("oracle")){
				sqlUser = "INSERT /*+ ignore_row_on_dupkey_index(group_user,group_user_PK)*/ INTO group_user(gid,userid) ";
			}
			sqlUser += "SELECT gi.gid,? FROM group_info gi WHERE gi.grid=?";
			st = connection.prepareStatement(sqlUser);
			st.setInt(1, user);
			st.setInt(2, rrgid);
			st.executeUpdate();
			st.close();
		}
		catch( Exception e )
		{
			try{ connection.rollback(); }
			catch( SQLException e1 ){ e1.printStackTrace(); }
			e.printStackTrace();
		}
		finally
		{
			try
			{
				connection.setAutoCommit(true);
				connection.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}

		return value;
	}

	@Override
	public String postRRGUsers( int userId, Integer rrgid, String data )
	{
		//    if(!credential.isAdmin(userId))
		//      throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		String value="";
		/// Parse data
		DocumentBuilder documentBuilder;
		Document document = null;
		try
		{
			DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
			documentBuilder = documentBuilderFactory.newDocumentBuilder();
			InputSource is = new InputSource(new StringReader(data));
			document= documentBuilder.parse(is);
		}
		catch( Exception e )
		{
			e.printStackTrace();
		}

		/// Problème de parsage
		if( document == null ) return value;

		try
		{
			connection.setAutoCommit(false);
			Element root = document.getDocumentElement();

			/// Ajout des utilisateurs
			NodeList users = root.getElementsByTagName("user");
	        String sqlUser = "";
			if (dbserveur.equals("mysql")){
				sqlUser = "INSERT IGNORE INTO group_user(gid,userid) ";
			} else if (dbserveur.equals("oracle")){
				sqlUser = "INSERT /*+ ignore_row_on_dupkey_index(group_user,group_user_PK)*/ INTO group_user(gid,userid) ";
			}
			sqlUser += "SELECT gi.gid,? FROM group_info gi WHERE gi.grid=?";
			PreparedStatement st = connection.prepareStatement(sqlUser);
			st.setInt(2, rrgid);
			for( int j=0; j<users.getLength(); ++j )
			{
				Element user = (Element) users.item(j);
				String uidl = user.getAttribute("id");
				Integer uid = Integer.valueOf(uidl);
				st.setInt(1, uid);
				st.executeUpdate();
			}
			st.close();
		}
		catch( Exception e )
		{
			try{ connection.rollback(); }
			catch( SQLException e1 ){ e1.printStackTrace(); }
			e.printStackTrace();
		}
		finally
		{
			try
			{
				connection.setAutoCommit(true);
				connection.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}

		return value;
	}

	@Override
	public String deleteRRG( int userId, Integer rrgId )
	{
		//    if(!credential.isAdmin(userId))
		//      throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		String value="";

		try
		{
			connection.setAutoCommit(false);

			String sqlRRG = "DELETE gri, gu, gi, gr " +
					"FROM group_right_info AS gri " +
					"LEFT JOIN group_rights gr ON gri.grid=gr.grid " +
					"LEFT JOIN group_info gi ON gri.grid=gi.grid " +
					"LEFT JOIN group_user gu ON gi.gid=gu.gid " +
					"WHERE gri.grid=?";
			if (dbserveur.equals("oracle")){
				sqlRRG = "DELETE FROM group_right_info AS gri WHERE gri.grid=?";
			}
			PreparedStatement rrgst = connection.prepareStatement(sqlRRG);
			rrgst.setInt(1, rrgId);
			rrgst.executeUpdate();
			rrgst.close();
		}
		catch( Exception e )
		{
			try{ connection.rollback(); }
			catch( SQLException e1 ){ e1.printStackTrace(); }
			e.printStackTrace();
		}
		finally
		{
			try
			{
				connection.setAutoCommit(true);
				connection.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}

		return value;
	}

	@Override
	public String deleteRRGUser( int userId, Integer rrgId, Integer user )
	{
		if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		String value="";

		try
		{
			connection.setAutoCommit(false);

			String sqlRRG = "DELETE FROM group_user " +
					"WHERE userid=? AND gid=(SELECT gid FROM group_info WHERE grid=?)";
			PreparedStatement rrgst = connection.prepareStatement(sqlRRG);
			rrgst.setInt(1, user);
			rrgst.setInt(2, rrgId);
			rrgst.executeUpdate();
			rrgst.close();
		}
		catch( Exception e )
		{
			try{ connection.rollback(); }
			catch( SQLException e1 ){ e1.printStackTrace(); }
			e.printStackTrace();
		}
		finally
		{
			try
			{
				connection.setAutoCommit(true);
				connection.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}

		return value;
	}


	/// Retire les utilisateurs des RRG d'un portfolio donn�
	@Override
	public String deletePortfolioUser( int userId, String portId )
	{
		//    if(!credential.isAdmin(userId))
		//      throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		try
		{
			connection.setAutoCommit(false);


			/// Bla here
			String sqlRRG = "DELETE FROM group_user " +
					"WHERE gid IN " +
					"(SELECT gi.gid " +
					"FROM group_right_info gri " +
					"LEFT JOIN group_info gi ON gri.grid=gi.grid " +
					"WHERE gri.portfolio_id=uuid2bin(?))";
			PreparedStatement rrgst = connection.prepareStatement(sqlRRG);
			rrgst.setString(1, portId);
			rrgst.executeUpdate();
			rrgst.close();
		}
		catch( Exception e )
		{
			try{ connection.rollback(); }
			catch( SQLException e1 ){ e1.printStackTrace(); }
			e.printStackTrace();
		}
		finally
		{
			try
			{
				connection.setAutoCommit(true);
				connection.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}

		return null;
	}


	@Override
	public String postUsersGroups(int userId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Integer putUserGroup(String usergroup, String userPut)
	{
		PreparedStatement st;
		String sql;
		Integer retval = 0;

		try
		{
			int gid = Integer.parseInt(usergroup);
			int uid = Integer.parseInt(userPut);

			sql = "INSERT INTO group_user(gid, userid) VALUES(?,?)";
			st = connection.prepareStatement(sql);
			st.setInt(1, gid);
			st.setInt(2, uid);
			retval = st.executeUpdate();
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
		}

		return retval;
	}

	@Override
	public String getUsersByGroup(int userId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String deleteUsersGroups(int userId, int usersgroup) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String deleteUsersGroupsUser(int userId, int usersgroup, int userid2) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getRessource(String nodeUuid, int userId, int groupId, String type) throws SQLException {
		// TODO Auto-generated method stub



		//ResultSet res = getMysqlPortfolios(userId,portfolioActive);
		String result = "";

		String sql1 = "";
		PreparedStatement st1;
		ResultSet res1 = null;
		ResultSet resNode = null;
		ResultSet resResource = null;


		resNode = getMysqlNode(nodeUuid,userId, groupId);
		resResource = null;

		if(type.equals("Context"))
		{
			if(resNode.next())
			{
				resResource = getMysqlResource(resNode.getString("res_node_uuid"));
				if (resResource.next())
				{
					String a = resResource.getString("xsi_type");
					if((resResource.getString("xsi_type")).equals("nodeRes") || (resResource.getString("xsi_type")).equals("context"))
					{
					}
					else{
						if(resNode.getString("res_node_uuid")!=null)
							if(resNode.getString("res_node_uuid").length()>0)
							{
								DocumentBuilderFactory documentBuilderFactory =DocumentBuilderFactory.newInstance();
								DocumentBuilder documentBuilder = null;
								Document document = null;

								try {
									documentBuilder = documentBuilderFactory.newDocumentBuilder();
								} catch (ParserConfigurationException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}

								result += "<asmResource id='"+resNode.getString("res_node_uuid")+"' contextid='"+resNode.getString("node_uuid")+"' xsi_type='"+resResource.getString("xsi_type")+"'>";
								//String text = "<node>"+resResource.getString("content")+"</node>";
								result += resResource.getString("content");
								result += "</asmResource>";

								resResource.close();
							}
					}
				}
				resResource = getMysqlResource(resNode.getString("res_res_node_uuid"));
				if (resResource.next())
				{String a = resResource.getString("xsi_type");
				if((resResource.getString("xsi_type")).equals("nodeRes") || (resResource.getString("xsi_type")).equals("context"))
				{
				}
				else{
					if(resNode.getString("res_res_node_uuid")!=null)
						if(resNode.getString("res_res_node_uuid").length()>0)
						{
							DocumentBuilderFactory documentBuilderFactory =DocumentBuilderFactory.newInstance();
							DocumentBuilder documentBuilder = null;
							Document document = null;

							try {
								documentBuilder = documentBuilderFactory.newDocumentBuilder();
							} catch (ParserConfigurationException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}

							result += "<asmResource id='"+resNode.getString("res_res_node_uuid")+"' contextid='"+resNode.getString("node_uuid")+"' xsi_type='"+resResource.getString("xsi_type")+"'>";
							//String text = "<node>"+resResource.getString("content")+"</node>";
							result += resResource.getString("content");
							result += "</asmResource>";

							resResource.close();
						}
				}
				}
				resResource = getMysqlResource(resNode.getString("res_context_node_uuid"));
				if (resResource.next())
				{String a = resResource.getString("xsi_type");
				if((resResource.getString("xsi_type")).equals("nodeRes") || (resResource.getString("xsi_type")).equals("context"))
				{
				}
				else{
					if(resNode.getString("res_context_node_uuid")!=null)
						if(resNode.getString("res_context_node_uuid").length()>0)
						{


							//result += "<asmResource id='"+resNode.getString("res_node_uuid")+"' contextid='"+resNode.getString("node_uuid")+"' xsi_type='"+resResource.getString("xsi_type")+"'>";
							DocumentBuilderFactory documentBuilderFactory =DocumentBuilderFactory.newInstance();
							DocumentBuilder documentBuilder = null;
							Document document = null;

							try {
								documentBuilder = documentBuilderFactory.newDocumentBuilder();
							} catch (ParserConfigurationException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}

							result += "<asmResource id='"+resNode.getString("res_context_node_uuid")+"' contextid='"+resNode.getString("node_uuid")+"' xsi_type='"+resResource.getString("xsi_type")+"'>";
							//String text = "<node>"+resResource.getString("content")+"</node>";
							result += resResource.getString("content");
							result += "</asmResource>";

							resResource.close();
						}
				}
				}
			}
		}else{
			if(resNode.next())
			{
				resResource = getMysqlResource(resNode.getString("res_node_uuid"));
				if (resResource.next())
				{
					String a = resResource.getString("xsi_type");
					if((resResource.getString("xsi_type")).equals("nodeRes"))
					{
						if(resNode.getString("res_node_uuid")!=null)
							if(resNode.getString("res_node_uuid").length()>0)
							{
								DocumentBuilderFactory documentBuilderFactory =DocumentBuilderFactory.newInstance();
								DocumentBuilder documentBuilder = null;
								Document document = null;

								try {
									documentBuilder = documentBuilderFactory.newDocumentBuilder();
								} catch (ParserConfigurationException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}

								result += "<asmResource id='"+resNode.getString("res_node_uuid")+"' contextid='"+resNode.getString("node_uuid")+"' xsi_type='"+resResource.getString("xsi_type")+"'>";
								//String text = "<node>"+resResource.getString("content")+"</node>";
								result += resResource.getString("content");
								result += "</asmResource>";

								resResource.close();
							}
					}
				}
				resResource = getMysqlResource(resNode.getString("res_res_node_uuid"));
				if (resResource.next())
				{String a = resResource.getString("xsi_type");
				if((resResource.getString("xsi_type")).equals("nodeRes"))
				{
					if(resNode.getString("res_res_node_uuid")!=null)
						if(resNode.getString("res_res_node_uuid").length()>0)
						{
							DocumentBuilderFactory documentBuilderFactory =DocumentBuilderFactory.newInstance();
							DocumentBuilder documentBuilder = null;
							Document document = null;

							try {
								documentBuilder = documentBuilderFactory.newDocumentBuilder();
							} catch (ParserConfigurationException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}

							result += "<asmResource id='"+resNode.getString("res_res_node_uuid")+"' contextid='"+resNode.getString("node_uuid")+"' xsi_type='"+resResource.getString("xsi_type")+"'>";
							//String text = "<node>"+resResource.getString("content")+"</node>";
							result += resResource.getString("content");
							result += "</asmResource>";

							resResource.close();
						}
				}
				}
				resResource = getMysqlResource(resNode.getString("res_context_node_uuid"));
				if (resResource.next())
				{String a = resResource.getString("xsi_type");
				if((resResource.getString("xsi_type")).equals("nodeRes"))
				{
					if(resNode.getString("res_context_node_uuid")!=null)
						if(resNode.getString("res_context_node_uuid").length()>0)
						{
							DocumentBuilderFactory documentBuilderFactory =DocumentBuilderFactory.newInstance();
							DocumentBuilder documentBuilder = null;
							Document document = null;

							try {
								documentBuilder = documentBuilderFactory.newDocumentBuilder();
							} catch (ParserConfigurationException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}

							result += "<asmResource id='"+resNode.getString("res_context_node_uuid")+"' contextid='"+resNode.getString("node_uuid")+"' xsi_type='"+resResource.getString("xsi_type")+"'>";
							//String text = "<node>"+resResource.getString("content")+"</node>";
							result += resResource.getString("content");
							result += "</asmResource>";

							resResource.close();
						}
				}
				}
			}
		}

		resNode.close();

		return result;

	}

	@Override
	public Object getNodes(MimeType mimeType, String portfoliocode,
			String semtag, int userId, int groupId, String semtag_parent, String code_parent) throws SQLException {

		// TODO Auto-generated method stub

		PreparedStatement st = null;
		PreparedStatement st1 = null;
		String sql;
		String sql1;
		ResultSet res = null;
		ResultSet res3 = null;
		ResultSet res4 = null;
		String pid = null;

		try{
			pid = this.getPortfolioUuidByPortfolioCode(portfoliocode);
			if( "".equals(pid) )
				return "";
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			return null;
		}

		String result = "";

		try
		{
			sql = "SELECT  bin2uuid(portfolio_id) AS portfolio_id,bin2uuid(root_node_uuid) as root_node_uuid, modif_user_id,modif_date, active, user_id "
					+ "FROM portfolio "
					+ "WHERE portfolio_id = uuid2bin(?) ";
			st = connection.prepareStatement(sql);
			st.setString(1, pid);
			res = st.executeQuery();

			if(semtag_parent != null && code_parent != null)
			{
				res3 = getMysqlNodeUuidBySemanticTag(pid, semtag_parent);

				if(res3.next())
				{
					String node = res3.getString("res_res_node_uuid");
					ResultSet resResource = getMysqlResource(res3.getString("res_res_node_uuid"));

					if (resResource.next())
					{String a = resResource.getString("xsi_type");
					if((resResource.getString("xsi_type")).equals("nodeRes"))
					{
						if(res3.getString("res_res_node_uuid")!=null)
							if(res3.getString("res_res_node_uuid").length()>0)
							{
								DocumentBuilderFactory documentBuilderFactory =DocumentBuilderFactory.newInstance();
								DocumentBuilder documentBuilder = null;
								Document document = null;

								try {
									documentBuilder = documentBuilderFactory.newDocumentBuilder();
								} catch (ParserConfigurationException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}

								String text = "<node>"+resResource.getString("content")+"</node>";

								try {
									document = documentBuilder.parse(new ByteArrayInputStream(text.getBytes("UTF-8")));
									//document = documentBuilder.parse(new InputSource(new StringReader(text)));
								} catch (SAXException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								} catch (IOException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}

								String nom = document.getElementsByTagName("code").item(0).getTextContent();


								if(document.getElementsByTagName("code").item(0).getTextContent() != null)
								{

									if(document.getElementsByTagName("code").item(0).getTextContent().equals(code_parent))
									{

										String children = res3.getString("node_children_uuid");
										String delim = ",";
										String[] listChildren = null;
										String listNode = null;
										listChildren = children.split(delim);
										result += "<nodes>";
										for(int i = 0; i < listChildren.length; i++){

											sql = "SELECT  bin2uuid(node_uuid) AS node_uuid, metadata, asm_type "
													+ "FROM node "
													+ "WHERE node_uuid = uuid2bin(?) and metadata LIKE '%semantictag=%'?'%'";
											st = connection.prepareStatement(sql);
											st.setString(1, listChildren[i]);
											st.setString(2, semtag);
											res4 = st.executeQuery();



											if(res4.next()){

												/*result += "<node ";
																		result += DomUtils.getXmlAttributeOutput("id", res4.getString("node_uuid"));
																		result += ">";
																		result += "</node>";		*/

												result += "<node ";
												result += DomUtils.getXmlAttributeOutput("id", res4.getString("node_uuid"));
												result += ">";
												if (res4.getString("asm_type").equalsIgnoreCase("asmContext"))
												{
													result += getRessource(res4.getString("node_uuid"), userId, groupId, "Context");
												}
												else{

													result += getRessource(res4.getString("node_uuid"), userId, groupId, "nonContext");
												}
												result += "</node>";

											}

										}

										result += "</nodes>";
									}
								}
							}

					}
					}
				}


				return result;

			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return null;
		}

		if(res.next()){

			ResultSet res1 = null;

			try
			{
				res1 = getMysqlNodeUuidBySemanticTag(pid, semtag);
			}
			catch(Exception ex)
			{
				ex.printStackTrace();
				return null;
			}

			result += "<nodes>";

			while(res1.next())
			{
				result += "<node ";
				result += DomUtils.getXmlAttributeOutput("id", res1.getString("node_uuid"));
				result += ">";
				if (res1.getString("asm_type").equalsIgnoreCase("asmContext"))
				{
					result += getRessource(res1.getString("node_uuid"), userId, groupId, "Context");
				}
				else{

					result += getRessource(res1.getString("node_uuid"), userId, groupId, "nonContext");
				}

				result += "</node>";
			}

			result += "</nodes>";
		}

		return result;


	}

	@Override
	public Object getPortfolioZip(MimeType mimeType, String portfolioUuid,
			int userId, int groupId, String label, Boolean resource, Boolean files)
					throws Exception {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public Object getNodesParent(MimeType mimeType, String portfoliocode,
			String semtag, int userId, int groupId, String semtag_parent,
			String code_parent) throws Exception {

		// TODO Auto-generated method stub

		PreparedStatement st = null;
		String sql;
		ResultSet res = null;
		ResultSet res3 = null;
		ResultSet res4 = null;
		String pid = this.getPortfolioUuidByPortfolioCode(portfoliocode);
		String result = "";

		try
		{
			sql = "SELECT  bin2uuid(portfolio_id) AS portfolio_id,bin2uuid(root_node_uuid) as root_node_uuid, modif_user_id,modif_date, active, user_id "
					+ "FROM portfolio "
					+ "WHERE portfolio_id = uuid2bin(?) ";
			st = connection.prepareStatement(sql);
			st.setString(1, pid);
			res = st.executeQuery();

			sql = "SELECT  bin2uuid(node_uuid) AS node_uuid,bin2uuid(node_children_uuid) as node_children_uuid, code, semantictag "
					+ "FROM node "
					+ "WHERE portfolio_id = uuid2bin(?) "
					+ "and  metadata LIKE '%semantictag=%'?'%' "
					+ "and code = ?";
			st = connection.prepareStatement(sql);
			st.setString(1, pid);
			st.setString(2, semtag_parent);
			st.setString(3, code_parent);
			res3 = st.executeQuery();

			if(res3.next())
			{
				String children = res3.getString("node_children_uuid");
				String delim = ",";
				String[] listChildren = null;
				String listNode = null;
				listChildren = children.split(delim);

				for(int i = 0; i <= listChildren.length; i++){

					sql = "SELECT  bin2uuid(node_uuid) AS node_uuid, code, semantictag "
							+ "FROM node "
							+ "WHERE semantictag = ? and node_uuid = ?";
					st = connection.prepareStatement(sql);
					st.setString(1, listChildren[i]);
					st.setString(2, semtag);
					res4 = st.executeQuery();

					result += "<nodes>";

					if(res4.next()){

						result += "<node ";
						result += DomUtils.getXmlAttributeOutput("id", res4.getString("node_uuid"));
						result += ">";
						result += "</node>";

					}

					result += "</nodes>";

				}
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();

		}

		return null;
	}

	@Override
	public boolean isAdmin( String uid )
	{
		int userid = Integer.parseInt(uid);
		return credential.isAdmin(userid);
	}


	@Override
	public String getUserId(String username) throws Exception
	{
		PreparedStatement st;
		String sql;
		ResultSet res;
		String retval = "0";

		try
		{
			sql = "SELECT userid FROM credential WHERE login = ? ";
			st = connection.prepareStatement(sql);
			st.setString(1, username);
			res = st.executeQuery();
			if( res.next() )
				retval = res.getString(1);
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
		}

		return retval;
	}

	@Override
	public String createUser(String username) throws Exception
	{
		return null;
	}

	@Override
	public String getGroupByName( String name )
	{
		PreparedStatement st;
		String sql;
		ResultSet res;
		String retval = "0";

		try
		{
			sql = "SELECT gid FROM group_info WHERE label=? ";
			st = connection.prepareStatement(sql);
			st.setString(1, name);
			res = st.executeQuery();
			if( res.next() )
				retval = res.getString(1);
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
		}

		return retval;
	}

	@Override
	public String createGroup( String name )
	{
		PreparedStatement st;
		String sql;
		int retval = 0;

		try
		{
			sql = "INSERT INTO group_right_info(owner, label) VALUES(1,?)";
			st = connection.prepareStatement(sql);
			st.setString(1, name);
			retval = st.executeUpdate();
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
		}

		return Integer.toString(retval);
	}

	@Override
	public boolean isUserInGroup( String uid, String gid )
	{
		PreparedStatement st;
		String sql;
		ResultSet res;
		boolean retval = false;

		try
		{
			sql = "SELECT userid FROM group_user WHERE userid=? AND gid=?";
			st = connection.prepareStatement(sql);
			st.setString(1, uid);
			st.setString(2, gid);
			res = st.executeQuery();
			if( res.next() )
				retval = true;
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
		}

		return retval;
	};


}
