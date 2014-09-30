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
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.UUID;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.activation.MimeType;
import javax.servlet.ServletContext;
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
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.IOUtils;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.mysql.jdbc.Statement;
import com.portfolio.data.utils.DomUtils;
import com.portfolio.data.utils.FileUtils;
import com.portfolio.data.utils.PictureUtils;
import com.portfolio.data.utils.SqlUtils;
import com.portfolio.rest.RestWebApplicationException;
import com.portfolio.security.Credential;
import com.portfolio.security.NodeRight;

/**
 * @author vassoill
 * Implémentation du dataProvider pour MySQL
 *
 */
public class MysqlDataProvider implements DataProvider {

	final Logger logger = LoggerFactory.getLogger(MysqlDataProvider.class);

	private Connection connection = null;
	private Credential credential = null;
	private String portfolioUuidPreliminaire = null; // Sert pour generer un uuid avant import du portfolio
//	private final ArrayList<String> portfolioRessourcesImportUuid = new ArrayList();
//	private final ArrayList<String> portfolioRessourcesImportPath = new ArrayList();
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

	DataSource ds = null;

	public MysqlDataProvider() throws Exception
	{
	}

	@Override
	public void setConnection( Connection c )
	{
		this.connection = c;
		credential = new Credential(connection);
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

		//try
		//{
		// On recupere d'abord les informations dans la table structures
		sql = "SELECT bin2uuid(node_uuid) as node_uuid, bin2uuid(node_parent_uuid) as node_parent_uuid,  node_children_uuid as node_children_uuid, node_order, metadata, metadata_wad, metadata_epm, bin2uuid(res_node_uuid) as res_node_uuid,  bin2uuid(res_res_node_uuid) as res_res_node_uuid,  bin2uuid(res_context_node_uuid) as res_context_node_uuid, shared_res, shared_node, shared_node_res,bin2uuid(shared_res_uuid) AS shared_res_uuid, bin2uuid(shared_node_uuid) AS shared_node_uuid, bin2uuid(shared_node_res_uuid) AS shared_node_res_uuid,asm_type, xsi_type, semtag, semantictag, label, code, descr, format, modif_user_id, modif_date,  bin2uuid(portfolio_id) as portfolio_id FROM node WHERE node_uuid = uuid2bin(?) ";
		st = connection.prepareStatement(sql);
		st.setString(1, nodeUuid);

		// On doit vérifier le droit d'accès en lecture avant de retourner le noeud
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
		st = connection.prepareStatement(sql);
		st.setString(1, portfolioUuid);

		return st.executeQuery();
	}

	public ResultSet getMysqlPortfolios(Integer userId, int substid, Boolean portfolioActive)
	{
		PreparedStatement st;
		String sql;

		try
		{
			// Si on est admin, on récupère la liste complête
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

			sql += "UNION ";
			if( substid != 0 )
			{
				// Croisement entre les portfolio accessible par le substituant et le substitué seulement
				sql = "SELECT DISTINCT bin2uuid(p.portfolio_id) AS portfolio_id, bin2uuid(p.root_node_uuid) AS root_node_uuid, p.modif_user_id, p.modif_date, p.active, p.user_id " +
						"FROM group_user gu " +
						"LEFT JOIN group_info gi ON gu.gid=gi.gid " +
						"LEFT JOIN group_right_info gri ON gri.grid=gi.grid " +
						"LEFT JOIN portfolio p ON gri.portfolio_id=p.portfolio_id, " +
						"group_user gu2 " +
						"LEFT JOIN group_info gi2 ON gu2.gid=gi2.gid " +
						"LEFT JOIN group_right_info gri2 ON gri2.grid=gi2.grid " +
						"LEFT JOIN portfolio p2 ON gri2.portfolio_id=p2.portfolio_id " +
						"WHERE p.portfolio_id=p2.portfolio_id AND gu.userid=? AND gu2.userid=? ";
			}
			// Les portfolios dont on a reçu les droits
			else
			{
				sql += "SELECT DISTINCT bin2uuid(p.portfolio_id) AS portfolio_id, bin2uuid(p.root_node_uuid) AS root_node_uuid, p.modif_user_id, p.modif_date, p.active, p.user_id " +
						"FROM group_user gu " +
						"LEFT JOIN group_info gi ON gu.gid=gi.gid " +
						"LEFT JOIN group_right_info gri ON gri.grid=gi.grid " +
						"LEFT JOIN portfolio p ON gri.portfolio_id=p.portfolio_id " +
						"WHERE gu.userid=? ";
			}

			if(portfolioActive) sql += "  AND active = 1 "; else sql += "  AND active = 0 ";

			sql += "UNION ";
			/// Les portfolios par partage complet
			sql += "SELECT DISTINCT bin2uuid(p.portfolio_id) AS portfolio_id, bin2uuid(p.root_node_uuid) AS root_node_uuid, p.modif_user_id, p.modif_date, p.active, p.user_id " +
					"FROM complete_share cs " +
					"LEFT JOIN node n ON cs.portfolio_id=n.portfolio_id " +
					"LEFT JOIN portfolio p ON n.portfolio_id=p.portfolio_id " +
					"WHERE cs.userid=? ";


			if(portfolioActive) sql += " AND p.active = 1";
			else sql += " AND p.active = 0";

			//						  sql += " GROUP BY portfolio_id,root_node_uuid,modif_user_id,modif_date,active, user_id ";
			//sql += " ORDER BY modif_date ASC ";

			st = connection.prepareStatement(sql);
			if(userId!=null) st.setInt(1, userId);
			if( substid == 0 )
			{
				if(userId!=null)
					st.setInt(2, userId);
			}
			else
				st.setInt(2, substid);
			st.setInt(3, userId);
			//			  if(userId!=null) st.setInt(3, userId);

			return st.executeQuery();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		return null;
	}

	public ResultSet getMysqlUserGroups(Integer userId)
	{
		PreparedStatement st;
		String sql;

		try
		{
			// On recupere d'abord les informations dans la table structures

			sql = "SELECT * FROM group_user gu, credential cr, group_info gi  WHERE gu.userid=cr.userid  AND gi.gid=gu.gid ";
			if(userId!=null) sql += "  AND cr.userid = ? ";
			sql += " ORDER BY label ASC ";
			st = connection.prepareStatement(sql);
			if(userId!=null) st.setInt(1, userId);

			return st.executeQuery();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		return null;
	}

	public ResultSet getMysqlUsers(Integer userId)
	{
		PreparedStatement st;
		String sql;

		try
		{
			// On recupere d'abord les informations dans la table structures

			sql = "SELECT * FROM credential";
			//if(userId!=null) sql += "  AND cr.userid = ? ";
			//sql += " ORDER BY display_name ASC ";
			st = connection.prepareStatement(sql);
			// if(userId!=null) st.setInt(1, userId);

			return st.executeQuery();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		return null;
	}

	public ResultSet getMysqlGroupRights(Integer userId, Integer groupId)
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

			Integer iActive = (active) ? 1 : 0;

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

		NodeRight right = credential.getPortfolioRight(userId, groupId, portfolioUuid, Credential.DELETE);
		if( credential.isAdmin(userId) || right.delete )
			hasRights = true;

		if(hasRights)
		{
			/// Si il y a quelque chose de particulier, on s'assure que tout soit bien nettoyé de façon séparé
			try
			{
				connection.setAutoCommit(false);

				/// Portfolio
				sql = "DELETE FROM portfolio WHERE portfolio_id=uuid2bin(?)";
				st = connection.prepareStatement(sql);
				st.setString(1, portfolioUuid);
				st.executeUpdate();

				/// Nodes
				sql = "DELETE FROM node WHERE portfolio_id=uuid2bin(?)";	/// On garde les resources, c'est ce qu'il faut?
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

		if(credential.hasNodeRight(userId,groupId,nodeUuid,Credential.DELETE))
		{
			sql  = " DELETE FROM node WHERE node_uuid=uuid2bin(?) ";
			st = connection.prepareStatement(sql);
			st.setString(1, nodeUuid);
			Integer nbDeletedNodes = st.executeUpdate();

			// On met à jour les enfants du parent
			updateMysqlNodeChildren(nodeParentUuid);

			return nbDeletedNodes;
		}
		return 0;
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
		PreparedStatement st=null;

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
		PreparedStatement st=null;
		String sql;
		int status = -1;

		try
		{
			/// Re-numérote les noeud (on commence à 0)
			sql = "UPDATE node SET node_order=@ii:=@ii+1 " +	// La 1ère valeur va être 0
					"WHERE node_parent_uuid=uuid2bin(?) AND (@ii:=-1) " +  // Pour tromper la requête parce qu'on veut commencer à 0
					"ORDER by node_order";
			if (dbserveur.equals("oracle")){
				sql = "UPDATE node n1 SET n1.node_order=(SELECT (rnum-1) FROM (SELECT node_uuid, row_number() OVER (ORDER BY node_order ASC) rnum, node_parent_uuid FROM node WHERE node_parent_uuid=uuid2bin(?)) n2 WHERE n1.node_uuid= n2.node_uuid) WHERE n1.node_parent_uuid=?";
			}
			st = connection.prepareStatement(sql);
			st.setString(1, nodeUuid);
			if (dbserveur.equals("oracle")){
				st.setString(2, nodeUuid);
			}
			st.executeUpdate();

			/// Met à jour les enfants
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
			st.executeUpdate();
			int changes = st.getUpdateCount();

			if( changes == 0 )	// Specific case when there's no children left in parent
			{
				sql = "UPDATE node n "
						+ "SET n.node_children_uuid=\"\" "
						+ "WHERE n.node_uuid=uuid2bin(?)";
				if (dbserveur.equals("oracle")){
					sql = "UPDATE node n "
							+ "SET n.node_children_uuid=\" \" "
							+ "WHERE n.node_uuid=uuid2bin(?)";
				}
				st = connection.prepareStatement(sql);
				st.setString(1, nodeUuid);
				st.executeUpdate();
				changes = st.getUpdateCount();
			}

			status = changes;
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			if( st != null ) try{ st.close(); }catch( SQLException e ){ e.printStackTrace(); }
		}
		return status;
	}

	private int insertMysqlResource(String uuid,String parentUuid, String xsiType,String content,String portfolioModelId, int sharedNodeRes,int sharedRes, int userId)
	{
		String sql = "";
		PreparedStatement st=null;
		int status = -1;

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
				if( st != null ) try{ st.close(); }catch( SQLException e ){ e.printStackTrace(); }
			}
			// Ensuite on met à jour les id ressource au niveau du noeud parent
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

//			return st.executeUpdate();
			st.executeUpdate();
			status= st.getUpdateCount();
		}
		catch(Exception ex)
		{
			//System.out.println("root_node_uuid : "+uuid);
			ex.printStackTrace();
			status= -1;
		}
		finally
		{
			if( st != null ) try{ st.close(); }catch( SQLException e ){ e.printStackTrace(); }
		}
		return status;
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

				/// Interpétation du code (vive le hack... Non)
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
	public Object getUserGroups(int userId) throws Exception {
		ResultSet res = getMysqlUserGroups(userId);

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

		return result;
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
		String root_node = res.getString("root_node_uuid");

		if( st != null ) st.close();
		if( res != null ) res.close();

		return root_node;
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
		}
		return result;
	}


	@Override
	public Object getPortfolio(MimeType outMimeType, String portfolioUuid, int userId, int groupId, String label, String resource, String files, int substid) throws Exception
	{
		String rootNodeUuid = getPortfolioRootNode(portfolioUuid);
		String header = "";
		String footer = "";
		NodeRight nodeRight = credential.getPortfolioRight(userId,groupId, portfolioUuid, Credential.READ);
		if(!nodeRight.read)
		{
			userId = credential.getPublicUid();
//			NodeRight nodeRight = new NodeRight(false,false,false,false,false,false);
			/// Vérifie les droits avec le compte publique (dernière chance)
			nodeRight = credential.getPublicRight(userId, 123, rootNodeUuid, "dummy");
			if( !nodeRight.read )
				return "faux";
		}

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
				e.printStackTrace();
			}

			Element root = document.createElement("portfolio");
			root.setAttribute("id", portfolioUuid);
			root.setAttribute("code", "0");

			//// Noeuds supplémentaire pour WAD
			// Version
			Element verNode = document.createElement("version");
			Text version = document.createTextNode("4");
			verNode.appendChild(version);
			root.appendChild(verNode);
			// metadata-wad
//			Element metawad = document.createElement("metadata-wad");
//			metawad.setAttribute("prog", "main.jsp");
//			metawad.setAttribute("owner", "N");
//			root.appendChild(metawad);
			int owner = credential.getOwner(userId, portfolioUuid);
			String isOwner = "N";
			if( owner == userId )
				isOwner = "Y";
			root.setAttribute("owner", isOwner);

			//          root.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
			//          root.setAttribute("schemaVersion", "1.0");
			document.appendChild(root);

			getLinearXml(portfolioUuid, rootNodeUuid, root, true, null, userId, nodeRight.groupId, nodeRight.groupLabel);


			StringWriter stw = new StringWriter();
			Transformer serializer = TransformerFactory.newInstance().newTransformer();
			serializer.transform(new DOMSource(document), new StreamResult(stw));

			if(resource !=null && files != null)
			{
				if(resource.equals("true") && files.equals("true"))
				{
					String adressedufichier =  System.getProperty("user.dir") +"/tmp_getPortfolio_"+ new Date() +".xml";
					String adresseduzip = System.getProperty("user.dir") + "/tmp_getPortfolio_"+ new Date() +".zip";

					File file = null;
					PrintWriter ecrire;
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

					try
					{
						ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(adresseduzip));
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
	public Object getPortfolioByCode(MimeType mimeType, String portfolioCode, int userId, int groupId, String resources, int substid) throws Exception
	{
		//return this.getPortfolio(mimeType, this.getPortfolioUuidByPortfolioCode(portfolioCode), userId, groupId, null);

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
			return this.getPortfolio(new MimeType("text/xml"),pid,userId, groupId, null, null, null, substid).toString();
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
	public Object getPortfolios(MimeType outMimeType, int userId, int groupId, Boolean portfolioActive, int substid) throws SQLException
	{
		ResultSet res = getMysqlPortfolios(userId, substid, portfolioActive);
		String result = "";
		if(outMimeType.getSubType().equals("xml"))
		{
			result = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><portfolios>";
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

		// On recupere d'abord l'uuid du premier noeud trouvé correspondant au semantictag
		res = this.getMysqlNodeUuidBySemanticTag(portfolioUuid, semantictag);
		res.next();
		nodeUuid = res.getString("node_uuid");

		if(!credential.hasNodeRight(userId,groupId,nodeUuid, Credential.READ))
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
				if(!credential.hasNodeRight(userId,groupId,nodeUuid, Credential.READ))
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
	public Object postPortfolio(MimeType inMimeType,MimeType outMimeType,String in,  int userId, int groupId, String portfolioModelId, int substid) throws Exception
	{
		if(!credential.isAdmin(userId) && !credential.isCreator(userId) )
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		StringBuffer outTrace = new StringBuffer();
		String portfolioUuid;

		// Si le modele est renseigné, on ignore le XML posté et on récupere le contenu du modele
		// à la place
		if(portfolioModelId!=null)
			in = getPortfolio(inMimeType,portfolioModelId,userId, groupId, null, null, null, substid).toString();

		// On génère un nouvel uuid
		if(this.portfolioUuidPreliminaire!=null)
			portfolioUuid = portfolioUuidPreliminaire;
		else
			portfolioUuid = UUID.randomUUID().toString();
		//On vérifie l'existence du portfolio. Si le portfolio existe, on ne l'écrase pas
		//if(getPortfolioRootNode(portfolioUuid)==null)
		//{
		//	throw new Exception("Le portfolio dont l'uuid="+portfolioUuid+" existe");
		//}
		//else
		//			putPortfolio(inMimeType,outMimeType,in, portfolioUuid,userId,true,groupId, portfolioModelId);



		if(in.length()>0)
		{
			//if(resPortfolio!=null) deleteMySqlPortfolio(portfolioUuid,userId, groupId);

			Document doc = DomUtils.xmlString2Document(in, outTrace);

			/// Check if portfolio code is already used
			XPath xPath = XPathFactory.newInstance().newXPath();
			String filterRes = "//asmRoot/asmResource/code";
			NodeList nodelist = (NodeList) xPath.compile(filterRes).evaluate(doc, XPathConstants.NODESET);

			if( nodelist.getLength() > 0 )
			{
				String code = nodelist.item(0).getTextContent();
				// Simple query
				if( isCodeExist(code) )
					throw new RestWebApplicationException(Status.CONFLICT, "Existing code.");
			}


			Node rootNode = (doc.getElementsByTagName("portfolio")).item(0);
			if(rootNode==null)
				throw new Exception("Root Node (portfolio) not found !");
			else
			{
				rootNode = (doc.getElementsByTagName("asmRoot")).item(0);

				String uuid = UUID.randomUUID().toString();

				insertMysqlPortfolio(portfolioUuid,uuid,0,userId);

				writeNode(rootNode, portfolioUuid, portfolioModelId, userId,0, uuid,null,0,0,false, null);
			}
		}
		updateMysqlPortfolioActive(portfolioUuid,true);

		updateMysqlPortfolioModelId(portfolioUuid,portfolioModelId);

		/// Créer groupe 'designer', 'all' est mis avec ce qui est spécifié dans le xml reçu
		int groupid = postCreateRole(portfolioUuid, "designer", userId);

		/// Ajoute la personne dans ce groupe
		putUserGroup(Integer.toString(groupid), Integer.toString(userId));

		/// S'assure que la date est bonne
		touchPortfolio(null, portfolioUuid);

		String result = "<portfolios>";
		result += "<portfolio ";
		result += DomUtils.getXmlAttributeOutput("id", portfolioUuid)+" ";
		result += "/>";
		result += "</portfolios>";
		return result;
	}

	public boolean isCodeExist( String code )
	{
		boolean response = false;
		String sql;
		PreparedStatement st = null;
		ResultSet rs = null;
		try
		{
			// Retire la personne du rôle
			sql = "SELECT bin2uuid(portfolio_id) FROM node WHERE code=?";
			st = connection.prepareStatement(sql);
			st.setString(1, code);
			rs = st.executeQuery();

			if( rs.next() )
				response = true;
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
		}
		finally
		{
			try
			{
				if( rs != null )
					rs.close();
				if( st != null )
					st.close();
			}
			catch( SQLException e )
			{
				e.printStackTrace();
			}
		}

		return response;
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
				rootNode = (doc.getElementsByTagName("asmRoot")).item(0);

				String uuid = UUID.randomUUID().toString();
				Node idAtt = rootNode.getAttributes().getNamedItem("id");
				if(idAtt!=null)
				{
					String tempId = idAtt.getNodeValue();
					if(tempId.length()>0)
					{
						uuid = tempId;
					}
				}
				insertMysqlPortfolio(portfolioUuid,uuid,0,userId);

				writeNode(rootNode, portfolioUuid, portfolioModelId, userId,0, null,null,0,0,true, null);
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
	public Object getNodes(MimeType outMimeType, String portfolioUuid,
			int userId,int groupId, String semtag, String parentUuid, String filterId,
			String filterParameters, String sortId) throws SQLException
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

	private  StringBuffer getNodeXmlOutput(String nodeUuid,boolean withChildren, String withChildrenOfXsiType, int userId,int groupId, String label,boolean checkSecurity) throws SQLException
	{
		StringBuffer result = new StringBuffer();
		// Verification securité
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


	private void getLinearXml(String portfolioUuid, String rootuuid, Node portfolio, boolean withChildren, String withChildrenOfXsiType, int userId,int groupId, String role) throws SQLException, SAXException, IOException, ParserConfigurationException
	{
		DocumentBuilderFactory newInstance = DocumentBuilderFactory.newInstance();
		DocumentBuilder parse=newInstance.newDocumentBuilder();

		/*
		long time0 = 0;
		long time1 = 0;
		long time2 = 0;
		long time3 = 0;
		//*/

//		time0 = System.currentTimeMillis();

		ResultSet resNode = getMysqlStructure(portfolioUuid,userId, groupId);

		//	  time1= System.currentTimeMillis();

		Document document=portfolio.getOwnerDocument();

		HashMap<String, Node> resolve = new HashMap<String, Node>();
		/// Node -> parent
		ArrayList<Object[]> entries = new ArrayList<Object[]>();

		processQuery(resNode, resolve, entries, document, parse, role);
		resNode.close();

		resNode = getSharedMysqlStructure(portfolioUuid,userId, groupId);
		if( resNode != null )
		{
			processQuery(resNode, resolve, entries, document, parse, role);
			resNode.close();
		}

//		time2 = System.currentTimeMillis();

		/// Reconstruct tree, using children list (node_children_uuid)
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

//		time3 = System.currentTimeMillis();

		Node rootNode = resolve.get(rootuuid);
		if( rootNode != null )
			portfolio.appendChild(rootNode);

		/*
		System.out.println("---- Portfolio ---");
		System.out.println("Time query: "+(time1-time0));
		System.out.println("Parsing: "+(time2-time1));
		System.out.println("Reconstruction: "+(time3-time2));
		System.out.println("------------------");
		//*/
	}

	private void processQuery( ResultSet result, HashMap<String, Node> resolve, ArrayList<Object[]> entries, Document document, DocumentBuilder parse, String role ) throws UnsupportedEncodingException, DOMException, SQLException, SAXException, IOException
	{
		if( result != null )
			while( result.next() )
			{
				String nodeUuid = result.getString("node_uuid");
				if( nodeUuid == null ) continue;    // Cas où on a des droits sur plus de noeuds qui ne sont pas dans le portfolio

				String childsId = result.getString("node_children_uuid");

				String type = result.getString("asm_type");

				String xsi_type = result.getString("xsi_type");
				if( null == xsi_type )
					xsi_type = "";

				int rd = result.getInt("RD");
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

				/// On spécifie aussi le rôle qui a été choisi dans la récupération des données
				String snode = "<"+type+" delete=\""+deleteRight+"\" id=\""+nodeUuid+"\" read=\""+readRight+"\" submit=\""+submitRight+"\" write=\""+writeRight+"\" xsi_type=\""+xsi_type+"\" " +macro +" role=\""+role+"\">"+
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
		PreparedStatement st=null;
		String sql;
		ResultSet rs = null;

		try
		{
			String rootNodeUuid = getPortfolioRootNode(portfolioUuid);

			// Cas admin ou partage totale
			if(credential.isAdmin(userId) || credential.isCompleteShare(userId, rootNodeUuid) || credential.isDesigner(userId, rootNodeUuid))
			{
				sql = "SELECT bin2uuid(n.node_uuid) AS node_uuid, " +
						"node_children_uuid, n.node_order, n.metadata, n.metadata_wad, n.metadata_epm, " +
						"n.shared_node AS shared_node, bin2uuid(n.shared_node_res_uuid) AS shared_node_res_uuid, bin2uuid(n.res_node_uuid) AS res_node_uuid, " +
						"r1.xsi_type AS r1_type, r1.content AS r1_content, bin2uuid(n.res_res_node_uuid) as res_res_node_uuid, " +
						"r2.content AS r2_content, bin2uuid(n.res_context_node_uuid) as res_context_node_uuid, " +
						"r3.content AS r3_content, n.asm_type, n.xsi_type, " +
						"1 AS RD, 1 AS WR, 1 AS SB, 1 AS DL, '' AS types_id, '' AS rules_id " +
						"FROM node n " +
						"LEFT JOIN resource_table r1 ON n.res_node_uuid=r1.node_uuid " +
						"LEFT JOIN resource_table r2 ON n.res_res_node_uuid=r2.node_uuid " +
						"LEFT JOIN resource_table r3 ON n.res_context_node_uuid=r3.node_uuid " +
						"WHERE portfolio_id=uuid2bin(?)";
				st = connection.prepareStatement(sql);
				st.setString(1, portfolioUuid);
			}
			else
			{
				/// FIXME: Il faudrait peut-être prendre une autre stratégie pour sélectionner les bonnes données
				// Cas propriétaire
				// Cas générale (partage via droits)

				sql = "CREATE TEMPORARY TABLE t_rights(" +
						"grid BIGINT NOT NULL, " +
						"id binary(16) UNIQUE NOT NULL, " +
						"RD TINYINT(1) NOT NULL, " +
						"WR TINYINT(1) NOT NULL, " +
						"DL TINYINT(1) NOT NULL, " +
						"SB TINYINT(1) NOT NULL, " +
						"AD TINYINT(1) NOT NULL," +
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
			                "types_id CLOB, " +
			                "rules_id CLOB) ON COMMIT DELETE ROWS";
				}
				st = connection.prepareStatement(sql);
				st.execute();
				st.close();

				/// Droits données par le groupe sélectionné
				sql = "INSERT INTO t_rights(grid,id,RD,WR,DL,SB,AD) " +
						"SELECT gr.grid, gr.id, gr.RD, gr.WR, gr.DL, gr.SB, gr.AD " +
						"FROM group_info gi, group_right_info gri, group_rights gr " +
						"WHERE gi.grid=gri.grid AND gri.grid=gr.grid AND gi.gid=?";
				st = connection.prepareStatement(sql);
				st.setInt(1, groupId);
				st.executeUpdate();
				st.close();

				/// Droits donné spécifiquement à un utilisateur
				if (dbserveur.equals("mysql")){
					sql = "INSERT INTO t_rights(grid,id,RD,WR,DL,SB,AD) ";
					sql += "SELECT gr.grid, gr.id, gr.RD, gr.WR, gr.DL, gr.SB, gr.AD " +
						"FROM group_right_info gri " +
						"LEFT JOIN group_info gi ON gri.grid=gi.grid " +
						"LEFT JOIN group_rights gr ON gri.grid=gr.grid " +
						"WHERE gri.portfolio_id=uuid2bin(?) " +
						"AND gi.label=(SELECT login FROM credential WHERE userid=?) " +
						"ON DUPLICATE KEY " +
						"UPDATE t_rights.RD=GREATEST(t_rights.RD,gr.RD), " +
						"t_rights.WR=GREATEST(t_rights.WR,gr.WR), " +
						"t_rights.DL=GREATEST(t_rights.DL,gr.DL), " +
						"t_rights.SB=GREATEST(t_rights.SB,gr.SB), " +
						"t_rights.AD=GREATEST(t_rights.AD,gr.AD)";
				} else if (dbserveur.equals("oracle")){
		        	sql = "MERGE INTO t_rights d USING (";
			        sql += "SELECT gr.grid, gr.id, gr.RD, gr.WR, gr.DL, gr.SB, gr.AD " +
						"FROM group_right_info gri " +
						"LEFT JOIN group_info gi ON gri.grid=gi.grid " +
						"LEFT JOIN group_rights gr ON gri.grid=gr.grid " +
						"WHERE gri.portfolio_id=uuid2bin(?) " +
						"AND gi.label=(SELECT login FROM credential WHERE userid=?) ";
		        	sql += ") s ON (d.grid = s.grid AND d.id = s.id) WHEN MATCHED THEN UPDATE SET " +
							"d.RD=GREATEST(d.RD,gr.RD), " +
							"d.WR=GREATEST(d.WR,gr.WR), " +
							"d.DL=GREATEST(d.DL,gr.DL), " +
							"d.SB=GREATEST(d.SB,gr.SB), " +
							"d.AD=GREATEST(d.AD,gr.AD)";
		        	sql += " WHEN NOT MATCHED THEN INSERT (d.grid, d.id, d.RD, d.WR, d.DL, d.SB, d.AD) VALUES (s.grid, s.id, s.RD, s.WR, s.DL, s.SB, s.AD)";
				}
				st = connection.prepareStatement(sql);
				st.setString(1, portfolioUuid);
				st.setInt(2, userId);
				st.executeUpdate();
				st.close();

				/// Droits données par le portfolio à 'tout le monde'
				/// Fusion des droits, pas très beau mais bon.
				/// FIXME: Devrait peut-être vérifier si la personne a les droits d'y accéder?
				if (dbserveur.equals("mysql")){
	        		sql = "INSERT INTO t_rights(grid,id,RD,WR,DL,SB,AD) ";
	        		sql += "SELECT gr.grid, gr.id, gr.RD, gr.WR, gr.DL, gr.SB, gr.AD " +
						"FROM group_right_info gri " +
						"LEFT JOIN group_info gi ON gri.grid=gi.grid " +
						"LEFT JOIN group_rights gr ON gri.grid=gr.grid " +
						"WHERE gri.portfolio_id=uuid2bin(?) " +
						"AND gi.label='all' " +
						"ON DUPLICATE KEY " +
						"UPDATE t_rights.RD=GREATEST(t_rights.RD,gr.RD), " +
						"t_rights.WR=GREATEST(t_rights.WR,gr.WR), " +
						"t_rights.DL=GREATEST(t_rights.DL,gr.DL), " +
						"t_rights.SB=GREATEST(t_rights.SB,gr.SB), " +
						"t_rights.AD=GREATEST(t_rights.AD,gr.AD)";
				} else if (dbserveur.equals("oracle")){
		        	sql = "MERGE INTO t_rights d USING (";
			        sql += "SELECT gr.grid, gr.id, gr.RD, gr.WR, gr.DL, gr.SB, gr.AD " +
							"FROM group_right_info gri " +
							"LEFT JOIN group_info gi ON gri.grid=gi.grid " +
							"LEFT JOIN group_rights gr ON gri.grid=gr.grid " +
							"WHERE gri.portfolio_id=uuid2bin(?) " +
							"AND gi.label='all' ";
		        	sql += ") s ON (d.grid = s.grid AND d.id = s.id) WHEN MATCHED THEN UPDATE SET " +
							"d.RD=GREATEST(d.RD,gr.RD), " +
							"d.WR=GREATEST(d.WR,gr.WR), " +
							"d.DL=GREATEST(d.DL,gr.DL), " +
							"d.SB=GREATEST(d.SB,gr.SB), " +
							"d.AD=GREATEST(d.AD,gr.AD)";
		        	sql += " WHEN NOT MATCHED THEN INSERT (d.grid, d.id, d.RD, d.WR, d.DL, d.SB, d.AD) VALUES (s.grid, s.id, s.RD, s.WR, s.DL, s.SB, s.AD)";
				}
				st = connection.prepareStatement(sql);
				st.setString(1, portfolioUuid);
				st.executeUpdate();
				st.close();

				/// Actuelle sélection des données
				sql = "SELECT bin2uuid(n.node_uuid) AS node_uuid, " +
						"node_children_uuid, n.node_order, n.metadata, n.metadata_wad, n.metadata_epm, " +
						"n.shared_node AS shared_node, bin2uuid(n.shared_node_res_uuid) AS shared_node_res_uuid, bin2uuid(n.res_node_uuid) AS res_node_uuid, " +
						"r1.xsi_type AS r1_type, r1.content AS r1_content, bin2uuid(n.res_res_node_uuid) as res_res_node_uuid, " +
						"r2.content AS r2_content, bin2uuid(n.res_context_node_uuid) as res_context_node_uuid, " +
						"r3.content AS r3_content, n.asm_type, n.xsi_type, " +
						"tr.RD, tr.WR, tr.SB, tr.DL, gr.types_id, gr.rules_id " +
						"FROM group_rights gr, t_rights tr " +
						"LEFT JOIN node n ON tr.id=n.node_uuid " +
						"LEFT JOIN resource_table r1 ON n.res_node_uuid=r1.node_uuid " +   // Récupération des données res_node
						"LEFT JOIN resource_table r2 ON n.res_res_node_uuid=r2.node_uuid " +   // Récupération des données res_res_node
						"LEFT JOIN resource_table r3 ON n.res_context_node_uuid=r3.node_uuid " +   // Récupération des données res_context
						"WHERE tr.grid=gr.grid AND tr.id=gr.id " +
						"UNION ALL " +	/// Union pour les données appartenant au créateur
						"SELECT bin2uuid(n.node_uuid) AS node_uuid, " +
						"node_children_uuid, n.node_order, n.metadata, n.metadata_wad, n.metadata_epm, " +
						"n.shared_node AS shared_node, bin2uuid(n.shared_node_res_uuid) AS shared_node_res_uuid, bin2uuid(n.res_node_uuid) AS res_node_uuid, " +
						"r1.xsi_type AS r1_type, r1.content AS r1_content, bin2uuid(n.res_res_node_uuid) as res_res_node_uuid, " +
						"r2.content AS r2_content, bin2uuid(n.res_context_node_uuid) as res_context_node_uuid, " +
						"r3.content AS r3_content, n.asm_type, n.xsi_type, " +
						"1 AS RD, 1 AS WR, 1 AS SB, 1 AS DL, '' AS types_id, '' AS rules_id " +
						"FROM node n " +
						"LEFT JOIN resource_table r1 ON n.res_node_uuid=r1.node_uuid " +
						"LEFT JOIN resource_table r2 ON n.res_res_node_uuid=r2.node_uuid " +
						"LEFT JOIN resource_table r3 ON n.res_context_node_uuid=r3.node_uuid " +
						"WHERE n.modif_user_id=? AND portfolio_id=uuid2bin(?)";
				st = connection.prepareStatement(sql);
				st.setInt(1, userId);
				st.setString(2, portfolioUuid);
			}
			rs = st.executeQuery();   // Pas sûr si les 'statement' restent ouvert après que la connexion soit fermée
		}
		catch(SQLException e)
		{
			e.printStackTrace();
		}
		finally
		{
			try
			{
				// Les 'pooled connection' ne se ferment pas vraiment. On nettoie manuellement les tables temporaires...
				if (dbserveur.equals("mysql")){
					sql = "DROP TEMPORARY TABLE IF EXISTS t_rights";
					st = connection.prepareStatement(sql);
					st.execute();
					st.close();
				} else if (dbserveur.equals("oracle")){
		        	sql = "{call drop_tables(tmpTableList('t_rights'))}";
		            CallableStatement ocs = connection.prepareCall(sql) ;
		            ocs.execute();
		            ocs.close();
				}
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}

		return rs;
	}

	/// Récupère les noeuds partagés d'un portfolio
	/// C'est séparé car les noeud ne provenant pas d'un même portfolio, on ne peut pas les sélectionner rapidement
	/// Autre possibilité serait de garder ce même type de fonctionnement pour une sélection par niveau d'un portfolio.
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
		                  ",  CONSTRAINT t_struc_UK_uuid UNIQUE (uuid)) ON COMMIT DELETE ROWS";
			}
			st = connection.prepareStatement(sql);
			st.execute();
			st.close();

			// En double car on ne peut pas faire d'update/select d'une même table temporaire
			sql = "CREATE TEMPORARY TABLE t_struc_2(" +
					"uuid binary(16) UNIQUE NOT NULL, " +
					"node_parent_uuid binary(16), " +
					"t_level INT) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
			if (dbserveur.equals("oracle")){
		        sql = "CREATE GLOBAL TEMPORARY TABLE t_struc_2(" +
		                "uuid RAW(16) NOT NULL, " +
		                "node_parent_uuid RAW(16), " +
		                "t_level NUMBER(10,0)"+
		                ",  CONSTRAINT t_struc_2_UK_uuid UNIQUE (uuid)) ON COMMIT DELETE ROWS";
			}
			st = connection.prepareStatement(sql);
			st.execute();
			st.close();

			/// Initialise la descente des noeuds partagés
			sql = "INSERT INTO t_struc(uuid, node_parent_uuid, t_level) " +
					"SELECT n.shared_node_uuid, n.node_parent_uuid, 0 " +
					"FROM node n " +
					"WHERE n.portfolio_id=uuid2bin(?) AND shared_node=1";
			st = connection.prepareStatement(sql);
			st.setString(1, portfolioUuid);
			st.executeUpdate();
			st.close();

			/// On boucle, sera toujours <= à "nombre de noeud du portfolio"
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

			st = connection.prepareStatement(sql);
			while( added != 0 )
			{
				st.setInt(1, level+1);
				st.setInt(2, level);
				st.executeUpdate();
				added = stTemp.executeUpdate();   // On s'arrête quand rien à été ajouté
				level = level + 1;    // Prochaine étape
			}
			st.close();
			stTemp.close();

			// Sélectionne les données selon la filtration
			sql = "SELECT bin2uuid(n.node_uuid) AS node_uuid," +
					" node_children_uuid, " +
					" n.node_order," +
					" n.metadata, n.metadata_wad, n.metadata_epm," +
					" n.shared_node AS shared_node," +
					" bin2uuid(n.shared_node_res_uuid) AS shared_node_res_uuid," +
					" bin2uuid(n.res_node_uuid) AS res_node_uuid," +
					" r1.xsi_type AS r1_type, r1.content AS r1_content," +     // donnée res_node
					" bin2uuid(n.res_res_node_uuid) as res_res_node_uuid," +
					" r2.content AS r2_content," +     // donnée res_res_node
					" bin2uuid(n.res_context_node_uuid) as res_context_node_uuid," +
					" r3.content AS r3_content," +     // donnée res_context
					" n.asm_type, n.xsi_type," +
					" gr.RD, gr.WR, gr.SB, gr.DL, gr.types_id, gr.rules_id," +   // info sur les droits
					" bin2uuid(n.portfolio_id) AS portfolio_id" +
					" FROM node n" +
					" LEFT JOIN resource_table r1 ON n.res_node_uuid=r1.node_uuid" +         // Récupération des données res_node
					" LEFT JOIN resource_table r2 ON n.res_res_node_uuid=r2.node_uuid" +     // Récupération des données res_res_node
					" LEFT JOIN resource_table r3 ON n.res_context_node_uuid=r3.node_uuid" + // Récupération des données res_context
//					" LEFT JOIN (group_rights gr, group_info gi, group_user gu)" +     // Vérification des droits
					" LEFT JOIN group_rights gr ON n.node_uuid=gr.id" +     // VÃ©rification des droits
					" CROSS JOIN group_info gi ON gr.grid=gi.grid" +
					" CROSS JOIN group_user gu ON gi.gid=gu.gid" +
					" WHERE gu.userid=? AND gr.RD=1" +  // On doit au moins avoir le droit de lecture
					" AND n.node_uuid IN (SELECT uuid FROM t_struc)";   // Selon note filtrage, prendre les noeud nécéssaire

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
	/// Récupère les noeuds en dessous par niveau. Pour faciliter le traitement des shared_node
	/// Mais ça serait beaucoup plus simple de faire un objet a traiter dans le client
	private ResultSet getNodePerLevel(String nodeUuid, int userId,  int groupId) throws SQLException
	{
		PreparedStatement st = null;
		String sql;
		ResultSet res = null;

		try
		{
			// Filtrage avec droits
			sql = "CREATE TEMPORARY TABLE t_rights(" +
					"grid BIGINT NOT NULL, " +
					"id binary(16) UNIQUE NOT NULL, " +
					"RD TINYINT(1) NOT NULL, " +
					"WR TINYINT(1) NOT NULL, " +
					"DL TINYINT(1) NOT NULL, " +
					"SB TINYINT(1) NOT NULL, " +
					"AD TINYINT(1) NOT NULL," +
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
		                "types_id CLOB, " +
		                "rules_id CLOB) " +
		                ",  CONSTRAINT t_rights_UK_id UNIQUE (id) ON COMMIT DELETE ROWS";
			}
			st = connection.prepareStatement(sql);
			st.execute();
			st.close();


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
		                  ",  CONSTRAINT t_struc_UK_uuid UNIQUE (uuid)) ON COMMIT DELETE ROWS";
			}
			st = connection.prepareStatement(sql);
			st.execute();
			st.close();

			// En double car on ne peut pas faire d'update/select d'une même table temporaire
			sql = "CREATE TEMPORARY TABLE t_struc_2(" +
					"uuid binary(16) UNIQUE NOT NULL, " +
					"node_parent_uuid binary(16), " +
					"t_level INT) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
			if (dbserveur.equals("oracle")){
		        sql = "CREATE GLOBAL TEMPORARY TABLE t_struc_2(" +
		                "uuid RAW(16) NOT NULL, " +
		                "node_parent_uuid RAW(16), " +
		                "t_level NUMBER(10,0)"+
		                ",  CONSTRAINT t_struc_2_UK_uuid UNIQUE (uuid)) ON COMMIT DELETE ROWS";
			}
			st = connection.prepareStatement(sql);
			st.execute();
			st.close();

			/// Initialise la descente des noeuds, si il y a un partagé on partira de là, sinon du noeud par défaut
			sql = "INSERT INTO t_struc(uuid, node_parent_uuid, t_level) " +
					"SELECT COALESCE(n.shared_node_uuid, n.node_uuid), n.node_parent_uuid, 0 " +
					"FROM node n " +
					"WHERE n.node_uuid=uuid2bin(?)";
			st = connection.prepareStatement(sql);
			st.setString(1, nodeUuid);
			st.executeUpdate();
			st.close();

			/// On boucle, avec les shared_node si ils existent.
			/// FIXME: Possiblité de boucle infini
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
				added = stTemp.executeUpdate();   // On s'arrête quand rien à été ajouté
				level = level + 1;    // Prochaine étape
			}
			st.close();
			stTemp.close();

			/*// Redondant, pourquoi c'est là?
			sql = "INSERT INTO t_rights(grid,id,RD,WR,DL,SB,AD) " +
					"SELECT gr.grid, gr.id, gr.RD, gr.WR, gr.DL, gr.SB, gr.AD " +
					"FROM group_info gi, group_right_info gri, group_rights gr " +
					"WHERE gi.grid=gri.grid AND gri.grid=gr.grid AND gi.gid=? AND gr.RD=1";
			st = connection.prepareStatement(sql);
			st.setInt(1, groupId);
			st.executeUpdate();
			st.close();
			//*/

			if( credential.isDesigner(userId, nodeUuid) )
			{
				sql = "INSERT INTO t_rights(grid, id, RD, WR, DL, SB, AD, types_id, rules_id) " +
						"SELECT 0, ts.uuid, 1, 1, 1, 0, 0, NULL, NULL " +
						"FROM t_struc ts";
				st = connection.prepareStatement(sql);
			}
			else
			{
				// Aggrégation des droits avec 'all', l'appartenance du groupe de l'utilisateur, et les droits propres à l'utilisateur
				sql = "INSERT INTO t_rights(grid, id, RD, WR, DL, SB, AD, types_id, rules_id) " +
						"SELECT gr.grid, gr.id, MAX(gr.RD), MAX(gr.WR), MAX(gr.DL), MAX(gr.SB), MAX(gr.AD), types_id, rules_id " +
						"FROM group_info gi, group_right_info gri, group_rights gr, t_struc ts " +
						"WHERE gi.grid=gr.grid AND gri.grid=gr.grid AND ts.uuid=gr.id " +
						"AND (gri.label='all' OR gi.gid=? OR " +
						"gri.grid=(SELECT grid FROM credential c, group_right_info gri, node n WHERE n.node_uuid=uuid2bin(?) AND n.portfolio_id=gri.portfolio_id AND c.login=gri.label AND c.userid=?)) " +
						"GROUP BY gr.id";
				st = connection.prepareStatement(sql);
				st.setInt(1, groupId);
				st.setString(2, nodeUuid);
				st.setInt(3, userId);
			}
			st.executeUpdate();

			// Sélectionne les données selon la filtration
			sql = "SELECT bin2uuid(n.node_uuid) AS node_uuid," +
					" node_children_uuid, " +
					" n.node_order," +
					" n.metadata, n.metadata_wad, n.metadata_epm," +
					" n.shared_node AS shared_node," +
					" bin2uuid(n.shared_node_res_uuid) AS shared_node_res_uuid," +
					" bin2uuid(n.res_node_uuid) AS res_node_uuid," +
					" r1.xsi_type AS r1_type, r1.content AS r1_content," +     // donnée res_node
					" bin2uuid(n.res_res_node_uuid) as res_res_node_uuid," +
					" r2.content AS r2_content," +     // donnée res_res_node
					" bin2uuid(n.res_context_node_uuid) as res_context_node_uuid," +
					" r3.content AS r3_content," +     // donnée res_context
					" n.asm_type, n.xsi_type," +
					" tr.RD, tr.WR, tr.SB, tr.DL, tr.types_id, tr.rules_id," +   // info sur les droits
					" bin2uuid(n.portfolio_id) AS portfolio_id" +
					" FROM node n" +
					" LEFT JOIN resource_table r1 ON n.res_node_uuid=r1.node_uuid" +         // Récupération des données res_node
					" LEFT JOIN resource_table r2 ON n.res_res_node_uuid=r2.node_uuid" +     // Récupération des données res_res_node
					" LEFT JOIN resource_table r3 ON n.res_context_node_uuid=r3.node_uuid" + // Récupération des données res_context
					" LEFT JOIN t_rights tr" +     // Vérification des droits
					" ON n.node_uuid=tr.id" +   // On doit au moins avoir le droit de lecture
					" WHERE tr.RD=1 AND n.node_uuid IN (SELECT uuid FROM t_struc)";   // Selon note filtrage, prendre les noeud nécéssaire

			st = connection.prepareStatement(sql);
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
					sql = "DROP TEMPORARY TABLE IF EXISTS t_rights, t_struc, t_struc_2";
					st = connection.prepareStatement(sql);
					st.execute();
					st.close();
				} else if (dbserveur.equals("oracle")){
		        	sql = "{call drop_tables(tmpTableList('t_rights', 't_struc', 't_struc_2'))}";
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

		if(!credential.hasNodeRight(userId,groupId,nodeUuid, Credential.READ))
			return result;

		ResultSet resNode = getMysqlNode(nodeUuid,userId, groupId);

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
		{
			userId = credential.getPublicUid();
			/// Vérifie les droits avec le compte publique (dernière chance)
			credential.getPublicRight(userId, 123, nodeUuid, "dummy");

			if( !nodeRight.read )
				return nodexml;
		}

		if(outMimeType.getSubType().equals("xml"))
		{
			ResultSet result = getNodePerLevel(nodeUuid, userId, nodeRight.groupId);

			/// Préparation du XML que l'on va renvoyer
			DocumentBuilderFactory documentBuilderFactory =DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder = null;
			Document document=null;
			documentBuilder = documentBuilderFactory.newDocumentBuilder();
			document = documentBuilder.newDocument();
			document.setXmlStandalone(true);

			HashMap<String, Node> resolve = new HashMap<String, Node>();
			/// Node -> parent
			ArrayList<Object[]> entries = new ArrayList<Object[]>();

			processQuery(result, resolve, entries, document, documentBuilder, nodeRight.groupLabel);
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
	public Object deleteNode(String nodeUuid, int userId, int groupId)
	{
		NodeRight nodeRight = credential.getNodeRight(userId,groupId,nodeUuid, Credential.DELETE);

		if(!nodeRight.delete)
			if(!credential.isAdmin(userId) && !credential.isDesigner(userId, nodeUuid))
				throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		PreparedStatement st;
		String sql = "";
		int result = 0;
		try
		{
			/// Pour le filtrage de la structure
			sql = "CREATE TEMPORARY TABLE t_struc(" +
					"uuid binary(16) UNIQUE NOT NULL, " +
					"node_parent_uuid binary(16), " +
					"res_node_uuid binary(16), " +
					"res_res_node_uuid binary(16), " +
					"res_context_node_uuid binary(16), " +
					"t_level INT) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
			if (dbserveur.equals("oracle")){
		        sql = "CREATE GLOBAL TEMPORARY TABLE t_struc(" +
		                "uuid RAW(16) NOT NULL, " +
		                "node_parent_uuid RAW(16), " +
		                "res_node_uuid RAW(16), " +
		                "res_res_node_uuid RAW(16), " +
		                "res_context_node_uuid RAW(16), " +
		                "t_level NUMBER(10,0)"+
		                ",  CONSTRAINT t_struc_UK_uuid UNIQUE (uuid)) ON COMMIT DELETE ROWS";
			}
			st = connection.prepareStatement(sql);
			st.execute();
			st.close();

			sql = "CREATE TEMPORARY TABLE t_struc_2(" +
					"uuid binary(16) UNIQUE NOT NULL, " +
					"node_parent_uuid binary(16) NOT NULL, " +
					"res_node_uuid binary(16), " +
					"res_res_node_uuid binary(16), " +
					"res_context_node_uuid binary(16), " +
					"t_level INT) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
			if (dbserveur.equals("oracle")){
		        sql = "CREATE GLOBAL TEMPORARY TABLE t_struc_2(" +
		                "uuid RAW(16) NOT NULL, " +
		                "node_parent_uuid RAW(16), " +
		                "res_node_uuid RAW(16), " +
		                "res_res_node_uuid RAW(16), " +
		                "res_context_node_uuid RAW(16), " +
		                "t_level NUMBER(10,0)"+
		                ",  CONSTRAINT t_struc_2_UK_uuid UNIQUE (uuid)) ON COMMIT DELETE ROWS";
			}
			st = connection.prepareStatement(sql);
			st.execute();
			st.close();

			/// Pour le filtrage des ressources
			sql = "CREATE TEMPORARY TABLE t_res(" +
					"uuid binary(16) UNIQUE NOT NULL) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
			if (dbserveur.equals("oracle")){
		        sql = "CREATE GLOBAL TEMPORARY TABLE t_res(" +
		                "uuid RAW(16) NOT NULL, " +
		                ",  CONSTRAINT t_res_UK_uuid UNIQUE (uuid)) ON COMMIT DELETE ROWS";
			}
			st = connection.prepareStatement(sql);
			st.execute();
			st.close();

			/// Liste les noeud a filtrer
			// Initiale
			sql = "INSERT INTO t_struc(uuid, node_parent_uuid, res_node_uuid, res_res_node_uuid, res_context_node_uuid, t_level) " +
					"SELECT node_uuid, node_parent_uuid, res_node_uuid, res_res_node_uuid, res_context_node_uuid, 0 " +
					"FROM node WHERE node_uuid=uuid2bin(?)";
			st = connection.prepareStatement(sql);
			st.setString(1, nodeUuid);
			st.executeUpdate();
			st.close();

			/// On descend les noeuds
			int level = 0;
			int added = 1;
			if (dbserveur.equals("mysql")){
			      sql = "INSERT IGNORE INTO t_struc_2(uuid, node_parent_uuid, res_node_uuid, res_res_node_uuid, res_context_node_uuid, t_level) ";
				} else if (dbserveur.equals("oracle")){
			      sql = "INSERT /*+ ignore_row_on_dupkey_index(t_struc_2,t_struc_2_UK_uuid)*/ INTO t_struc_2(uuid, node_parent_uuid, res_node_uuid, res_res_node_uuid, res_context_node_uuid, t_level) ";
				}
				sql += "SELECT node_uuid, node_parent_uuid, res_node_uuid, res_res_node_uuid, res_context_node_uuid, ? " +
					"FROM node WHERE node_parent_uuid IN (SELECT uuid FROM t_struc t " +
					"WHERE t.t_level=?)";
			st = connection.prepareStatement(sql);

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

				added = stTemp.executeUpdate();   // On s'arrête quand rien à été ajouté
				level = level + 1;    // Prochaine étape
			}
			st.close();
			stTemp.close();

			/// On liste les ressources à effacer
			if (dbserveur.equals("mysql")){
				sql = "INSERT INTO t_res(uuid) SELECT res_node_uuid FROM t_struc WHERE res_node_uuid <> 0x0000000000000000000000000000000";
			} else if (dbserveur.equals("oracle")){
				sql = "INSERT INTO t_res(uuid) SELECT res_node_uuid FROM t_struc WHERE res_node_uuid <> '00000000000000000000000000000000'";
			}
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			if (dbserveur.equals("mysql")){
				sql = "INSERT INTO t_res(uuid) SELECT res_res_node_uuid FROM t_struc WHERE res_res_node_uuid <> 0x0000000000000000000000000000000";
			} else if (dbserveur.equals("oracle")){
				sql = "INSERT INTO t_res(uuid) SELECT res_res_node_uuid FROM t_struc WHERE res_res_node_uuid <> '00000000000000000000000000000000'";
			}
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			if (dbserveur.equals("mysql")){
				sql = "INSERT INTO t_res(uuid) SELECT res_context_node_uuid FROM t_struc WHERE res_context_node_uuid <> 0x0000000000000000000000000000000";
			} else if (dbserveur.equals("oracle")){
				sql = "INSERT INTO t_res(uuid) SELECT res_context_node_uuid FROM t_struc WHERE res_context_node_uuid <> '00000000000000000000000000000000'";
			}
			if (dbserveur.equals("mysql")){
			} else if (dbserveur.equals("oracle")){
			}
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			connection.setAutoCommit(false);
			/// On efface
			// Les ressources
			sql = "DELETE FROM resource_table WHERE node_uuid IN (SELECT uuid FROM t_res)";
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			// Les noeuds
			sql = "DELETE FROM node WHERE node_uuid IN (SELECT uuid FROM t_struc)";
			st = connection.prepareStatement(sql);
			result = st.executeUpdate();
			st.close();
		}
		catch( Exception e )
		{
			try
			{
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
					sql = "DROP TEMPORARY TABLE IF EXISTS t_struc, t_struc_2, t_res";
					st = connection.prepareStatement(sql);
					st.execute();
					st.close();
				} else if (dbserveur.equals("oracle")){
		            sql = "{call drop_tables(tmpTableList('t_struc', 't_struc_2', 't_res'))}";
		            CallableStatement ocs = connection.prepareCall(sql) ;
		            ocs.execute();
		            ocs.close();
				}

				connection.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}

		System.out.println("deleteNode :"+nodeUuid);

		return result;
	}

	@Override
	public Object postInstanciatePortfolio(MimeType inMimeType, String portfolioUuid, String srcCode, String newCode, int userId, int groupId, boolean copyshared ) throws Exception
	{
		String sql = "";
		PreparedStatement st;
		String newPortfolioUuid = UUID.randomUUID().toString();

		try
		{
			/// Find source code
			if( srcCode != null )
			{
				/// Find back portfolio uuid from source code
				sql = "SELECT bin2uuid(portfolio_id) AS uuid FROM node WHERE code=?";
				st = connection.prepareStatement(sql);
				st.setString(1, srcCode);
				ResultSet res = st.executeQuery();
				if( res.next() )
					portfolioUuid = res.getString("uuid");
			}

			if( portfolioUuid == null )
				return "";

			///// Création des tables temporaires
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
					"code varchar(250)  DEFAULT NULL, " +
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
		                  "metadata CLOB DEFAULT ' ', " +
		                  "metadata_wad CLOB DEFAULT ' ', " +
		                  "metadata_epm CLOB DEFAULT ' ', " +
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
		                  "code VARCHAR2(250 CHAR)  DEFAULT NULL, " +
		                  "descr VARCHAR2(250 CHAR)  DEFAULT NULL, " +
		                  "format VARCHAR2(30 CHAR) DEFAULT NULL, " +
		                  "modif_user_id NUMBER(12) NOT NULL, " +
		                  "modif_date timestamp DEFAULT NULL, " +
		                  "portfolio_id RAW(16) DEFAULT NULL) ON COMMIT DELETE ROWS";
			}
			st = connection.prepareStatement(sql);
			st.execute();
			st.close();

			/// Pour la copie des données
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
		                "modif_date timestamp DEFAULT NULL) ON COMMIT DELETE ROWS";
			}
			st = connection.prepareStatement(sql);
			st.execute();
			st.close();

			/// Pour la mise à jour de la liste des enfants/parents
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
		                ",  CONSTRAINT t_struc_UK_uuid UNIQUE (uuid)) ON COMMIT DELETE ROWS";
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
		                ",  CONSTRAINT t_struc_2_UK_uuid UNIQUE (uuid)) ON COMMIT DELETE ROWS";
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
		                "types_id CLOB, " +
		                "rules_id CLOB) ON COMMIT DELETE ROWS";
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
					added = stTemp.executeUpdate();   // On s'arrête quand rien à été ajouté
					level = level + 1;    // Prochaine étape
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

			/// Copie les uuid pour la résolution des parents/enfants
			sql = "INSERT INTO t_struc(node_order, new_uuid, uuid, node_parent_uuid) " +
					"SELECT node_order, new_uuid, node_uuid, node_parent_uuid FROM t_data";
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			if( !copyshared )
			{
				/// Cas spécial pour shared_node=1
				// Le temps qu'on refasse la liste des enfants, on va enlever le noeud plus tard
				sql = "UPDATE t_data SET shared_node_uuid=node_uuid WHERE shared_node=1";
				st = connection.prepareStatement(sql);
				st.executeUpdate();
				st.close();

				// Met a jour t_struc pour la redirection. C'est pour la list des enfants
				// FIXME: A vérifier les appels qui modifie la liste des enfants.
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

			/// Copie des données non partagés (shared=0)
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
				sql += "d.res_node_uuid <> '00000000000000000000000000000000'"; // Binaire non null
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

			/// Résolution des nouveaux uuid avec les parents
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

			/// Mise à jour de la liste des enfants (! requête particulière)
			/// L'ordre détermine le rendu visuel final du xml
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

			// Mise à jour du code dans le contenu du noeud (blech)
			if (dbserveur.equals("mysql")){
				sql = "UPDATE t_data d " +
					"LEFT JOIN t_res r ON d.res_res_node_uuid=r.new_uuid " +  // Il faut utiliser le nouveau uuid
					"SET r.content=REPLACE(r.content, d.code, ?) " +
					"WHERE d.asm_type='asmRoot'";
			} else if (dbserveur.equals("oracle")){
		        sql = "UPDATE t_res r SET r.content=(SELECT REPLACE(r2.content, d.code, ?) FROM t_data d LEFT JOIN t_res r2 ON d.res_res_node_uuid=r2.new_uuid WHERE d.asm_type='asmRoot') WHERE EXISTS (SELECT 1 FROM t_data d LEFT JOIN t_res r2 ON d.res_res_node_uuid=r2.new_uuid WHERE d.asm_type='asmRoot')";
			}
			st = connection.prepareStatement(sql);
			st.setString(1, newCode);
			st.executeUpdate();
			st.close();

			// Mise à jour du code dans le code interne de la BD
			sql = "UPDATE t_data d SET d.code=? WHERE d.asm_type='asmRoot'";
			st = connection.prepareStatement(sql);
			st.setString(1, newCode);
			st.executeUpdate();
			st.close();

			/// temp class
			class right
			{
				int rd=0;
				int wr=0;
				int dl=0;
				int sb=0;
				int ad=0;
				String types="";
				String rules="";
				String notify="";
			};

			class groupright
			{
				right getGroup( String label )
				{
					right r = rights.get(label.trim());
					if( r == null )
					{
						r = new right();
						rights.put(label, r);
					}
					return r;
				}

				void setNotify( String roles )
				{
					Iterator<right> iter = rights.values().iterator();
					while( iter.hasNext() )
					{
						right r = iter.next();
						r.notify = roles.trim();
					}
				}

				HashMap<String, right> rights = new HashMap<String, right>();
			};

			class resolver
			{
				groupright getUuid( String uuid )
				{
					groupright gr = resolve.get(uuid);
					if( gr == null )
					{
						gr = new groupright();
						resolve.put(uuid, gr);
					}
					return gr;
				};

				HashMap<String, groupright> resolve = new HashMap<String, groupright>();
				HashMap<String, Integer> groups = new HashMap<String, Integer>();
			};

			resolver resolve = new resolver();

			/// Crée les groupes de droits en les copiants dans la table d'origine
			// Sélectionne les groupes concernés
			/*
			sql = "SELECT login FROM credential c WHERE c.userid=?";
			st = connection.prepareStatement(sql);
			st.setInt(1, userId);
			ResultSet res = st.executeQuery();

			String login="";
			if( res.next() )
				login = res.getString("login");
			//*/

			// Selection des metadonnées
			sql = "SELECT bin2uuid(t.new_uuid) AS uuid, bin2uuid(t.portfolio_id) AS puuid, t.metadata, t.metadata_wad, t.metadata_epm " +
					"FROM t_data t";
			st = connection.prepareStatement(sql);
			ResultSet res = st.executeQuery();

			DocumentBuilder documentBuilder;
			DocumentBuilderFactory documentBuilderFactory =DocumentBuilderFactory.newInstance();
			documentBuilder = documentBuilderFactory.newDocumentBuilder();
			while( res.next() )
			{
				String uuid = res.getString("uuid");
				//        	String puuid = res.getString("puuid");
				String meta = res.getString("metadata_wad");
				//          meta = meta.replaceAll("user", login);
				String nodeString = "<?xml version='1.0' encoding='UTF-8' standalone='no'?><transfer "+meta+"/>";

				groupright role = resolve.getUuid(uuid);

				try
				{
					/// parse meta
					InputSource is = new InputSource(new StringReader(nodeString));
					Document doc = documentBuilder.parse(is);

					/// Process attributes
					Element attribNode = doc.getDocumentElement();
					NamedNodeMap attribMap = attribNode.getAttributes();

					String nodeRole;
					Node att = attribMap.getNamedItem("access");
					if(att != null)
					{
						//if(access.equalsIgnoreCase("public") || access.contains("public"))
						//	credential.postGroupRight("all",uuid,Credential.READ,portfolioUuid,userId);
					}
					att = attribMap.getNamedItem("seenoderoles");
					if(att != null)
					{
						StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements())
						{
							nodeRole = tokens.nextElement().toString();

							right r = role.getGroup(nodeRole);
							r.rd = 1;

							resolve.groups.put(nodeRole, 0);
						}
					}
					att = attribMap.getNamedItem("showtoroles");
					if(att != null)
					{
						StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements())
						{
							nodeRole = tokens.nextElement().toString();

							right r = role.getGroup(nodeRole);
							r.rd = 0;

							resolve.groups.put(nodeRole, 0);
						}
					}
					att = attribMap.getNamedItem("delnoderoles");
					if(att != null)
					{
						StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements())
						{

							nodeRole = tokens.nextElement().toString();
							right r = role.getGroup(nodeRole);
							r.dl = 1;

							resolve.groups.put(nodeRole, 0);
						}
					}
					att = attribMap.getNamedItem("editnoderoles");
					if(att != null)
					{
						StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements())
						{
							nodeRole = tokens.nextElement().toString();
							right r = role.getGroup(nodeRole);
							r.wr = 1;

							resolve.groups.put(nodeRole, 0);
						}
					}
					att = attribMap.getNamedItem("submitnoderoles");
					if(att != null)
					{
						StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements())
						{
							nodeRole = tokens.nextElement().toString();
							right r = role.getGroup(nodeRole);
							r.sb = 1;

							resolve.groups.put(nodeRole, 0);
						}
					}
					att = attribMap.getNamedItem("seeresroles");
					if(att != null)
					{
						StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements())
						{
							nodeRole = tokens.nextElement().toString();
							right r = role.getGroup(nodeRole);
							r.rd = 1;

							resolve.groups.put(nodeRole, 0);
						}
					}
					att = attribMap.getNamedItem("delresroles");
					if(att != null)
					{
						StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements())
						{
							nodeRole = tokens.nextElement().toString();
							right r = role.getGroup(nodeRole);
							r.dl = 1;

							resolve.groups.put(nodeRole, 0);
						}
					}
					att = attribMap.getNamedItem("editresroles");
					if(att != null)
					{
						StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements())
						{
							nodeRole = tokens.nextElement().toString();
							right r = role.getGroup(nodeRole);
							r.wr = 1;

							resolve.groups.put(nodeRole, 0);
						}
					}
					att = attribMap.getNamedItem("submitresroles");
					if(att != null)
					{
						StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements())
						{
							nodeRole = tokens.nextElement().toString();
							right r = role.getGroup(nodeRole);
							r.sb = 1;

							resolve.groups.put(nodeRole, 0);
						}
					}
					Node actionroles = attribMap.getNamedItem("actionroles");
					if(actionroles!=null)
					{
						/// Format pour l'instant: actionroles="sender:1,2;responsable:4"
						StringTokenizer tokens = new StringTokenizer(actionroles.getNodeValue(), ";");
						while (tokens.hasMoreElements())
						{
							nodeRole = tokens.nextElement().toString();
							StringTokenizer data = new StringTokenizer(nodeRole, ":");
							String nrole = data.nextElement().toString();
							String actions = data.nextElement().toString().trim();
							right r = role.getGroup(nrole);
							r.rules = actions;

							resolve.groups.put(nrole, 0);
						}
					}
					Node menuroles = attribMap.getNamedItem("menuroles");
					if(menuroles!=null)
					{
						/// Pour les différents items du menu
						StringTokenizer menuline = new StringTokenizer(menuroles.getNodeValue(), ";");

						while( menuline.hasMoreTokens() )
						{
							String line = menuline.nextToken();
							/// Format pour l'instant: mi6-parts,mission,Ajouter une mission,secret_agent
							StringTokenizer tokens = new StringTokenizer(line, ",");
							String menurolename = null;
							for( int t=0; t<4; ++t )
								menurolename = tokens.nextToken();

							if( menurolename != null )
								resolve.groups.put(menurolename.trim(), 0);
						}
					}
					Node notifyroles = attribMap.getNamedItem("notifyroles");
					if(notifyroles!=null)
					{
						/// Format pour l'instant: notifyroles="sender responsable"
						StringTokenizer tokens = new StringTokenizer(notifyroles.getNodeValue(), " ");
						String merge = "";
						if( tokens.hasMoreElements() )
							merge = tokens.nextElement().toString().trim();
						while (tokens.hasMoreElements())
							merge += ","+tokens.nextElement().toString().trim();
						role.setNotify(merge);
					}

					// No need to set public on multiple portoflio
					/*
					meta = res.getString("metadata");
					nodeString = "<?xml version='1.0' encoding='UTF-8' standalone='no'?><transfer "+meta+"/>";
					is = new InputSource(new StringReader(nodeString));
					doc = documentBuilder.parse(is);
					attribNode = doc.getDocumentElement();
					attribMap = attribNode.getAttributes();

					boolean isPublic = false;
					try
					{
						String publicatt = attribMap.getNamedItem("public").getNodeValue();
						if( "Y".equals(publicatt) )
							isPublic = true;
					}
					catch(Exception ex) {}
					setPublicState(userId, puuid, isPublic);
					//*/

				}
				catch( Exception e )
				{
					e.printStackTrace();
				}
			}
			res.close();
			st.close();

			/*
			sql = "SELECT grid FROM group_right_info " +
					"WHERE portfolio_id=uuid2bin(?)";
			st = connection.prepareStatement(sql);
			st.setString(1, portfolioUuid);
			ResultSet res = st.executeQuery();

			/// Pour chaque grid, on en créé un nouveau et met à jour nos nouveaux droits
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
			//*/

			connection.setAutoCommit(false);

			/// On insère les données pré-compilé
			Iterator<String> entries = resolve.groups.keySet().iterator();

			// Créé les groupes, ils n'existent pas
			String grquery = "INSERT INTO group_info(grid,owner,label) " +
					"VALUES(?,?,?)";
			PreparedStatement st2 = connection.prepareStatement(grquery);
			String gri = "INSERT INTO group_right_info(owner, label, change_rights, portfolio_id) " +
					"VALUES(?,?,?,uuid2bin(?))";
			st = connection.prepareStatement(gri, Statement.RETURN_GENERATED_KEYS);
			if (dbserveur.equals("oracle")){
				  st = connection.prepareStatement(sql, new String[]{"grid"});
			}

			while( entries.hasNext() )
			{
				String label = entries.next();
				st.setInt(1, 1);
				st.setString(2, label);
				st.setInt(3, 0);
				st.setString(4, newPortfolioUuid);

				st.execute();
				ResultSet keys = st.getGeneratedKeys();
				keys.next();
				int grid = keys.getInt(1);
				resolve.groups.put( label, grid );

				st2.setInt(1, grid);
				st2.setInt(2, 1);
				st2.setString(3, label);
				st2.execute();
			}

			/// Ajout des droits des noeuds
			String insertRight = "INSERT INTO group_rights(grid, id, RD, WR, DL, SB, AD, types_id, rules_id, notify_roles) " +
					"VALUES(?,uuid2bin(?),?,?,?,?,?,?,?,?)";
			st = connection.prepareStatement(insertRight);

			Iterator<Entry<String, groupright>> rights = resolve.resolve.entrySet().iterator();
			while( rights.hasNext() )
			{
				Entry<String, groupright> entry = rights.next();
				String uuid = entry.getKey();
				groupright gr = entry.getValue();

				Iterator<Entry<String, right>> rightiter = gr.rights.entrySet().iterator();
				while( rightiter.hasNext() )
				{
					Entry<String, right> rightelem = rightiter.next();
					String group = rightelem.getKey();
					int grid = resolve.groups.get(group);
					right rightval = rightelem.getValue();
					st.setInt(1, grid);
					st.setString(2, uuid);
					st.setInt(3, rightval.rd);
					st.setInt(4, rightval.wr);
					st.setInt(5, rightval.dl);
					st.setInt(6, rightval.sb);
					st.setInt(7, rightval.ad);
					st.setString(8, rightval.types);
					st.setString(9, rightval.rules);
					st.setString(10, rightval.notify);

					st.execute();
				}
			}

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

			/// Ajout du portfolio dans la table
			sql = "INSERT INTO portfolio(portfolio_id, root_node_uuid, user_id, model_id, modif_user_id, modif_date, active) " +
					"SELECT d.portfolio_id, d.new_uuid, p.user_id, p.model_id, p.modif_user_id, p.modif_date, p.active " +
					"FROM t_data d INNER JOIN portfolio p " +
					"ON d.node_uuid=p.root_node_uuid";

			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			/// Finalement on crée un rôle designer
			int groupid = postCreateRole(newPortfolioUuid, "designer", userId);

			/// Ajoute la personne dans ce groupe
			putUserGroup(Integer.toString(groupid), Integer.toString(userId));

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
	public Object postCopyPortfolio(MimeType inMimeType, String portfolioUuid, String srcCode, String newCode, int userId ) throws Exception
	{
		String sql = "";
		PreparedStatement st;
		String newPortfolioUuid = UUID.randomUUID().toString();

		try
		{
			/// Find source code
			if( srcCode != null )
			{
				/// Find back portfolio uuid from source code
				sql = "SELECT bin2uuid(portfolio_id) AS uuid FROM node WHERE code=?";
				st = connection.prepareStatement(sql);
				st.setString(1, srcCode);
				ResultSet res = st.executeQuery();
				if( res.next() )
					portfolioUuid = res.getString("uuid");
			}

			if( portfolioUuid == null )
				return "Error: no portofolio selected";

			///// Création des tables temporaires
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
					"code varchar(250)  DEFAULT NULL, " +
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
		                "metadata CLOB DEFAULT ' ', " +
		                "metadata_wad CLOB DEFAULT ' ', " +
		                "metadata_epm CLOB DEFAULT ' ', " +
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
		                "code VARCHAR2(250 CHAR)  DEFAULT NULL, " +
		                "descr VARCHAR2(250 CHAR)  DEFAULT NULL, " +
		                "format VARCHAR2(30 CHAR) DEFAULT NULL, " +
		                "modif_user_id NUMBER(12) NOT NULL, " +
		                "modif_date timestamp DEFAULT NULL, " +
		                "portfolio_id RAW(16) DEFAULT NULL) ON COMMIT DELETE ROWS";
			}
			st = connection.prepareStatement(sql);
			st.execute();
			st.close();

			/// Pour la copie des données
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
		                "xsi_type VARCHAR2(50 CHAR) DEFAULT NULL, " +
		                "content CLOB, " +
		                "user_id NUMBER(11) DEFAULT NULL, " +
		                "modif_user_id NUMBER(12) NOT NULL, " +
		                "modif_date timestamp DEFAULT NULL) ON COMMIT DELETE ROWS";
			}
			st = connection.prepareStatement(sql);
			st.execute();
			st.close();

			/// Pour la mise à jour de la liste des enfants/parents
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
		                ",  CONSTRAINT t_struc_UK_uuid UNIQUE (uuid)) ON COMMIT DELETE ROWS";
			}
			st = connection.prepareStatement(sql);
			st.execute();
			st.close();

			/////////
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

			/// Copie les uuid pour la résolution des parents/enfants
			sql = "INSERT INTO t_struc(node_order, new_uuid, uuid, node_parent_uuid) " +
					"SELECT node_order, new_uuid, node_uuid, node_parent_uuid FROM t_data";
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			/// Copie des ressources
			sql = "INSERT INTO t_res(new_uuid, node_uuid, xsi_type, content, user_id, modif_user_id, modif_date) ";
			if (dbserveur.equals("mysql")){
				sql += "SELECT uuid2bin(UUID()), ";
			} else if (dbserveur.equals("oracle")){
				sql += "SELECT sys_guid(), ";
			}
			sql += "r.node_uuid, r.xsi_type, r.content, r.user_id, r.modif_user_id, r.modif_date " +
					"FROM t_data d " +
					"LEFT JOIN resource_table r ON d.res_node_uuid=r.node_uuid ";
			if (dbserveur.equals("mysql")){
				sql += "WHERE d.res_node_uuid <> 0x0000000000000000000000000000000"; // Binaire non null
			} else if (dbserveur.equals("oracle")){
				sql += "WHERE d.res_node_uuid <> '00000000000000000000000000000000'"; // Binaire non null
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
					"LEFT JOIN resource_table r ON d.res_context_node_uuid=r.node_uuid ";
			if (dbserveur.equals("mysql")){
				sql += "WHERE d.res_context_node_uuid <> 0x0000000000000000000000000000000"; // Binaire non null
			} else if (dbserveur.equals("oracle")){
				sql += "WHERE d.res_context_node_uuid <> '00000000000000000000000000000000'"; // Binaire non null
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
					"LEFT JOIN resource_table r ON d.res_res_node_uuid=r.node_uuid ";
			if (dbserveur.equals("mysql")){
				sql += "WHERE d.res_res_node_uuid <> 0x0000000000000000000000000000000"; // Binaire non null
			} else if (dbserveur.equals("oracle")){
				sql += "WHERE d.res_res_node_uuid <> '00000000000000000000000000000000'"; // Binaire non null
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

			/// Résolution des nouveaux uuid avec les parents
			// Avec la structure
			sql = "UPDATE t_data t " +
					"SET t.node_parent_uuid = (SELECT new_uuid FROM t_struc s WHERE s.uuid=t.node_parent_uuid)";
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			// Avec les ressources (et droits des ressources)
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

			/// Mise à jour de la liste des enfants (! requête particulière)
			/// L'ordre détermine le rendu visuel final du xml
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

			// Mise à jour du code dans le contenu du noeud
			if (dbserveur.equals("mysql")){
				sql = "UPDATE t_data d " +
					"LEFT JOIN t_res r ON d.res_res_node_uuid=r.new_uuid " +  // Il faut utiliser le nouveau uuid
					"SET r.content=REPLACE(r.content, d.code, ?) " +
					"WHERE d.asm_type='asmRoot'";
			} else if (dbserveur.equals("oracle")){
		        sql = "UPDATE t_res r SET r.content=(SELECT REPLACE(r2.content, d.code, ?) FROM t_data d LEFT JOIN t_res r2 ON d.res_res_node_uuid=r2.new_uuid WHERE d.asm_type='asmRoot') WHERE EXISTS (SELECT 1 FROM t_data d LEFT JOIN t_res r2 ON d.res_res_node_uuid=r2.new_uuid WHERE d.asm_type='asmRoot')";
			}
			st = connection.prepareStatement(sql);
			st.setString(1, newCode);
			st.executeUpdate();
			st.close();

			// Mise à jour du code dans le code interne de la BD
			sql = "UPDATE t_data d SET d.code=? WHERE d.asm_type='asmRoot'";
			st = connection.prepareStatement(sql);
			st.setString(1, newCode);
			st.executeUpdate();
			st.close();

			connection.setAutoCommit(false);

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
					sql = "DROP TEMPORARY TABLE IF EXISTS t_data, t_res, t_struc";
					st = connection.prepareStatement(sql);
					st.execute();
					st.close();
				} else if (dbserveur.equals("oracle")){
		            sql = "{call drop_tables(tmpTableList('t_data', 't_res','t_struc'))}";
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
			if( res.next() )    // On prend le premier, très chic pour l'utilisateur...
			{
				baseUuid = res.getString("nUuid");
				pUuid = res.getString("pUuid");
			}
			else
				return "Selection non existante.";

//			t1 = System.currentTimeMillis();

			///// Création des tables temporaires
			/// Pour la copie de la structure
			sql = "CREATE TEMPORARY TABLE t_data(" +
					"new_uuid binary(16) NOT NULL, " +  /// Pour la copie d'une nouvelle structure
					"node_uuid binary(16)  NOT NULL, " +
					"node_parent_uuid binary(16) DEFAULT NULL, " +
//					"node_children_uuid blob, " +
					"node_order int(12) NOT NULL, " +
//					"metadata text NOT NULL, " +
//					"metadata_wad text NOT NULL, " +
//					"metadata_epm text NOT NULL, " +
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
					"code varchar(250)  DEFAULT NULL, " +
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
//		                "metadata CLOB DEFAULT ' ', " +
//		                "metadata_wad CLOB DEFAULT ' ', " +
//		                "metadata_epm CLOB DEFAULT ' ', " +
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
		                "code VARCHAR2(250 CHAR)  DEFAULT NULL, " +
		                "descr VARCHAR2(250 CHAR)  DEFAULT NULL, " +
		                "format VARCHAR2(30 CHAR) DEFAULT NULL, " +
		                "modif_user_id NUMBER(12) NOT NULL, " +
		                "modif_date timestamp DEFAULT NULL, " +
		                "portfolio_id RAW(16) DEFAULT NULL) ON COMMIT DELETE ROWS";
			}
			st = connection.prepareStatement(sql);
			st.execute();
			st.close();

//			t2 = System.currentTimeMillis();

			/// Pour la copie des données
			sql = "CREATE TEMPORARY TABLE t_res(" +
					"new_uuid binary(16) NOT NULL, " +  /// Pour la copie d'une nouvelle structure
					"node_uuid binary(16) NOT NULL, " +
					"xsi_type varchar(50) DEFAULT NULL, " +
//					"content text, " +
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
		                "modif_date timestamp DEFAULT NULL) ON COMMIT DELETE ROWS";
			}
			st = connection.prepareStatement(sql);
			st.execute();
			st.close();

//			t3 = System.currentTimeMillis();

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
		                "uuid RAW(16) NOT NULL, " +
		                "node_parent_uuid RAW(16) NOT NULL, " +
		                "t_level NUMBER(10,0)"+
		                ",  CONSTRAINT t_struc_UK_uuid UNIQUE (uuid)) ON COMMIT DELETE ROWS";
			}
			st = connection.prepareStatement(sql);
			st.execute();
			st.close();

//			t4 = System.currentTimeMillis();

			// En double car on ne peut pas faire d'update/select d'une même table temporaire
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
		                "uuid RAW(16) NOT NULL, " +
		                "node_parent_uuid RAW(16) NOT NULL, " +
		                "t_level NUMBER(10,0)"+
		                ",  CONSTRAINT t_struc_2_UK_uuid UNIQUE (uuid)) ON COMMIT DELETE ROWS";
			}
			st = connection.prepareStatement(sql);
			st.execute();
			st.close();

//			t5 = System.currentTimeMillis();

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

//			t6 = System.currentTimeMillis();

			/// Dans la table temporaire on retrouve les noeuds concernés
			/// (assure une convergence de la récursion et limite le nombre de lignes dans la recherche)
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

//			t7 = System.currentTimeMillis();

			/// On boucle, sera toujours <= à "nombre de noeud du portfolio"
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
				added = stTemp.executeUpdate();   // On s'arrête quand rien à été ajouté
				level = level + 1;    // Prochaine étape
			}
			st.close();
			stTemp.close();

//			t8 = System.currentTimeMillis();

			/// On retire les éléments null, ça pose problême par la suite
			if (dbserveur.equals("mysql")){
				sql = "DELETE FROM t_struc WHERE new_uuid=0x0000000000000000000000000000000";
			} else if (dbserveur.equals("oracle")){
		        sql = "DELETE FROM t_struc WHERE new_uuid='00000000000000000000000000000000'";
			}
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

//			t9 = System.currentTimeMillis();

			/// On filtre les données dont on a pas besoin
			sql = "DELETE FROM t_data WHERE node_uuid NOT IN (SELECT uuid FROM t_struc)";
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

//			t10 = System.currentTimeMillis();

			///// FIXME TODO: Vérifier les droits sur les données restantes

			/// Copie des données non partagés (shared=0)
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
					"AND (shared_res=false OR shared_node=false OR shared_node_res=false)";
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

//			t11 = System.currentTimeMillis();

			/// Résolution des nouveaux uuid avec les parents
			// Avec la structure
			sql = "UPDATE t_data t " +
					"SET t.node_parent_uuid = (SELECT new_uuid FROM t_struc s WHERE s.uuid=t.node_parent_uuid)";
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

//			t12 = System.currentTimeMillis();

			// Avec les ressources
			sql = "UPDATE t_data t " +
					"SET t.res_node_uuid = (SELECT new_uuid FROM t_res r WHERE r.node_uuid= t.res_node_uuid)";
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

//			t13 = System.currentTimeMillis();

			sql = "UPDATE t_data t " +
					"SET t.res_res_node_uuid = (SELECT new_uuid FROM t_res r WHERE r.node_uuid= t.res_res_node_uuid)";
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

//			t14 = System.currentTimeMillis();

			sql = "UPDATE t_data t " +
					"SET t.res_context_node_uuid = (SELECT new_uuid FROM t_res r WHERE r.node_uuid=t.res_context_node_uuid)";
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

//			t15 = System.currentTimeMillis();

			/// Mise à jour du parent de la nouvelle copie ainsi que l'ordre
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

//			t16 = System.currentTimeMillis();

			// Mise à jour de l'appartenance au portfolio de destination
			sql = "UPDATE t_data " +
					"SET portfolio_id=(SELECT portfolio_id FROM node WHERE node_uuid=uuid2bin(?))";
			st = connection.prepareStatement(sql);
			st.setString(1, destUuid);
			st.executeUpdate();
			st.close();

//			t17 = System.currentTimeMillis();

			/// On copie tout dans les vrai tables
			connection.setAutoCommit(false);

			/// Structure
			sql = "INSERT INTO node(node_uuid, node_parent_uuid, node_order, metadata, metadata_wad, metadata_epm, res_node_uuid, res_res_node_uuid, res_context_node_uuid, shared_res, shared_node, shared_node_res, shared_res_uuid, shared_node_uuid, shared_node_res_uuid, asm_type, xsi_type, semtag, semantictag, label, code, descr, format, modif_user_id, modif_date, portfolio_id) " +
					"SELECT t.new_uuid, t.node_parent_uuid, t.node_order, n.metadata, n.metadata_wad, n.metadata_epm, t.res_node_uuid, t.res_res_node_uuid, t.res_context_node_uuid, t.shared_res, t.shared_node, t.shared_node_res, t.shared_res_uuid, t.shared_node_uuid, t.shared_node_res_uuid, t.asm_type, t.xsi_type, t.semtag, t.semantictag, t.label, t.code, t.descr, t.format, t.modif_user_id, t.modif_date, t.portfolio_id " +
					"FROM t_data t LEFT JOIN node n ON t.node_uuid=n.node_uuid";
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

//			t18 = System.currentTimeMillis();

			/// Resources
			sql = "INSERT INTO resource_table(node_uuid, xsi_type, content, user_id, modif_user_id, modif_date) " +
					"SELECT t.new_uuid, r.xsi_type, r.content, r.user_id, r.modif_user_id, r.modif_date " +
					"FROM t_res t LEFT JOIN resource_table r ON t.node_uuid=r.node_uuid";
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

//			t19 = System.currentTimeMillis();

			/// Mise à jour de la liste des enfants
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

//			t20 = System.currentTimeMillis();

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

//			t21 = System.currentTimeMillis();


			/// Parsage des droits des noeuds et initialisation dans la BD
			// Login
			sql = "SELECT login FROM credential c WHERE c.userid=?";
			st = connection.prepareStatement(sql);
			st.setInt(1, userId);
			res = st.executeQuery();

			String login="";
			if( res.next() )
				login = res.getString("login");

			// Selection des metadonnées
			sql = "SELECT bin2uuid(t.new_uuid) AS uuid, bin2uuid(t.portfolio_id) AS puuid, n.metadata, n.metadata_wad, n.metadata_epm " +
					"FROM t_data t LEFT JOIN node n ON t.node_uuid=n.node_uuid";
			st = connection.prepareStatement(sql);
			res = st.executeQuery();

			while( res.next() )
			{
				String uuid = res.getString("uuid");
				String portfolioUuid = res.getString("puuid");
				// Process et remplacement de 'user' par la personne en cours
				String meta = res.getString("metadata_wad");

				if( meta.contains("user") )
				{
					meta = meta.replaceAll("user", login);

					//// FIXME: should be done before with t_data
					/// Replace metadata
					sql = "UPDATE node SET metadata_wad=? WHERE node_uuid=uuid2bin(?)";
					st = connection.prepareStatement(sql);
					st.setString(1, meta);
					st.setString(2, uuid);
					st.executeUpdate();
					st.close();

					/// Ensure specific user group exist
					getRoleByNode(1, destUuid, login);
				}

				String nodeString = "<?xml version='1.0' encoding='UTF-8' standalone='no'?><transfer "+meta+"/>";
				try
				{
					/// Ensure we can parse it correctly
					DocumentBuilder documentBuilder;
					DocumentBuilderFactory documentBuilderFactory =DocumentBuilderFactory.newInstance();
					documentBuilder = documentBuilderFactory.newDocumentBuilder();
					InputSource is = new InputSource(new StringReader(nodeString));
					Document doc = documentBuilder.parse(is);

					/// Process attributes
					Element attribNode = doc.getDocumentElement();
					NamedNodeMap attribMap = attribNode.getAttributes();

					/// FIXME: à améliorer pour faciliter le changement des droits
					String nodeRole;
					Node att = attribMap.getNamedItem("access");
					if(att != null)
					{
						//if(access.equalsIgnoreCase("public") || access.contains("public"))
						//	credential.postGroupRight("all",uuid,Credential.READ,portfolioUuid,userId);
					}
					att = attribMap.getNamedItem("seenoderoles");
					if(att != null)
					{
						StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements())
						{
							nodeRole = tokens.nextElement().toString();
							credential.postGroupRight(nodeRole,uuid,Credential.READ,portfolioUuid,userId);
						}
					}
					att = attribMap.getNamedItem("delnoderoles");
					if(att != null)
					{
						StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements())
						{
							nodeRole = tokens.nextElement().toString();
							credential.postGroupRight(nodeRole,uuid,Credential.DELETE,portfolioUuid,userId);
						}
					}
					att = attribMap.getNamedItem("editnoderoles");
					if(att != null)
					{
						StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements())
						{
							nodeRole = tokens.nextElement().toString();
							credential.postGroupRight(nodeRole,uuid,Credential.WRITE,portfolioUuid,userId);
						}
					}
					att = attribMap.getNamedItem("submitroles");
					if(att != null)
					{
						StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements())
						{
							nodeRole = tokens.nextElement().toString();
							credential.postGroupRight(nodeRole,uuid,Credential.SUBMIT,portfolioUuid,userId);
						}
					}
					att = attribMap.getNamedItem("seeresroles");
					if(att != null)
					{
						StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements())
						{
							nodeRole = tokens.nextElement().toString();
							credential.postGroupRight(nodeRole,uuid,Credential.READ,portfolioUuid,userId);
						}
					}
					att = attribMap.getNamedItem("delresroles");
					if(att != null)
					{
						StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements())
						{
							nodeRole = tokens.nextElement().toString();
							credential.postGroupRight(nodeRole,uuid,Credential.DELETE,portfolioUuid,userId);
						}
					}
					att = attribMap.getNamedItem("editresroles");
					if(att != null)
					{
						StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements())
						{
							nodeRole = tokens.nextElement().toString();
							credential.postGroupRight(nodeRole,uuid,Credential.WRITE,portfolioUuid,userId);
						}
					}
					att = attribMap.getNamedItem("submitresroles");
					if(att != null)
					{
						StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements())
						{
							nodeRole = tokens.nextElement().toString();
							credential.postGroupRight(nodeRole,uuid,Credential.SUBMIT,portfolioUuid,userId);
						}
					}
					Node actionroles = attribMap.getNamedItem("actionroles");
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

					Node notifyroles = attribMap.getNamedItem("notifyroles");
					if(notifyroles!=null)
					{
						/// Format pour l'instant: actionroles="sender:1,2;responsable:4"
						StringTokenizer tokens = new StringTokenizer(notifyroles.getNodeValue(), " ");
						String merge = "";
						if( tokens.hasMoreElements() )
							merge = tokens.nextElement().toString();
						while (tokens.hasMoreElements())
							merge += ","+tokens.nextElement().toString();
						postNotifyRoles(userId, portfolioUuid, uuid, merge);
					}

					meta = res.getString("metadata");
					nodeString = "<?xml version='1.0' encoding='UTF-8' standalone='no'?><transfer "+meta+"/>";
					is = new InputSource(new StringReader(nodeString));
					doc = documentBuilder.parse(is);
					attribNode = doc.getDocumentElement();
					attribMap = attribNode.getAttributes();

					try
					{
						String publicatt = attribMap.getNamedItem("public").getNodeValue();
						if( "Y".equals(publicatt) )
							setPublicState(userId, portfolioUuid, true);
						else if ( "N".equals(publicatt) )
							setPublicState(userId, portfolioUuid, false);
					}
					catch(Exception ex) {}

				}
				catch( Exception e )
				{
					e.printStackTrace();
				}
			}
			res.close();
			st.close();

			/*
			/// Ajout des droits des noeuds
			sql = "INSERT INTO group_rights(grid, id, RD, WR, DL, SB, AD, types_id, rules_id) " +
					"SELECT g.grid, r.new_uuid, r.RD, r.WR, r.DL, r.SB, r.AD, r.types_id, r.rules_id " +
					"FROM " +
					"(SELECT gri.grid, gri.label " +
					"FROM node n " +
					"LEFT JOIN group_right_info gri ON n.portfolio_id=gri.portfolio_id " +
					"WHERE n.node_uuid=uuid2bin(?)) AS g," +  // Retrouve les groupes de destination via le noeud de destination
					"(SELECT gri.label, s.new_uuid, gr.RD, gr.WR, gr.DL, gr.SB, gr.AD, gr.types_id, gr.rules_id " +
					"FROM t_struc s, group_rights gr, group_right_info gri " +
					"WHERE s.uuid=gr.id AND gr.grid=gri.grid) AS r " + // Prend la liste des droits actuel des noeuds dupliqu���s
					"WHERE g.label=r.label"; // On croise le nouveau 'grid' avec le 'grid' d'origine via le label
			st = connection.prepareStatement(sql);
			st.setString(1, destUuid);
			st.executeUpdate();
			st.close();

//			t22 = System.currentTimeMillis();

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
			//*/

//			end = System.currentTimeMillis();

			/// On récupère le uuid créé
			sql = "SELECT bin2uuid(new_uuid) FROM t_data WHERE node_uuid=uuid2bin(?)";
			st = connection.prepareStatement(sql);
			st.setString(1, baseUuid);
			res = st.executeQuery();
			if( res.next() )
				createdUuid = res.getString(1);
			res.close();
			st.close();
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

				touchPortfolio(destUuid, null);

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

	// Même chose que postImportNode, mais on ne prend pas en compte le parsage des droits
	@Override
	public Object postCopyNode( MimeType inMimeType, String destUuid, String tag, String code, int userId, int groupId ) throws Exception
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
			if( res.next() )	// On prend le premier, très chic pour l'utilisateur...
			{
				baseUuid = res.getString("nUuid");
				pUuid = res.getString("pUuid");
			}
			else
				return "Selection non existante.";

//			t1 = System.currentTimeMillis();

			///// Création des tables temporaires
			/// Pour la copie de la structure
			sql = "CREATE TEMPORARY TABLE t_data(" +
					"new_uuid binary(16) NOT NULL, " +  /// Pour la copie d'une nouvelle structure
					"node_uuid binary(16)  NOT NULL, " +
					"node_parent_uuid binary(16) DEFAULT NULL, " +
//					"node_children_uuid blob, " +
					"node_order int(12) NOT NULL, " +
//					"metadata text NOT NULL, " +
//					"metadata_wad text NOT NULL, " +
//					"metadata_epm text NOT NULL, " +
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
					"code varchar(250)  DEFAULT NULL, " +
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
		                "node_order NUMBER(12) NOT NULL, " +
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
		                "code VARCHAR2(250 CHAR)  DEFAULT NULL, " +
		                "descr VARCHAR2(250 CHAR)  DEFAULT NULL, " +
		                "format VARCHAR2(30 CHAR) DEFAULT NULL, " +
		                "modif_user_id NUMBER(12) NOT NULL, " +
		                "modif_date timestamp DEFAULT NULL, " +
		                "portfolio_id RAW(16) DEFAULT NULL) ON COMMIT DELETE ROWS";
			}
			st = connection.prepareStatement(sql);
			st.execute();
			st.close();

//			t2 = System.currentTimeMillis();

			/// Pour la copie des données
			sql = "CREATE TEMPORARY TABLE t_res(" +
					"new_uuid binary(16) NOT NULL, " +  /// Pour la copie d'une nouvelle structure
					"node_uuid binary(16) NOT NULL, " +
					"xsi_type varchar(50) DEFAULT NULL, " +
//					"content text, " +
					"user_id int(11) DEFAULT NULL, " +
					"modif_user_id int(12) NOT NULL, " +
					"modif_date timestamp NULL DEFAULT NULL) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
			if (dbserveur.equals("oracle")){
		        sql = "CREATE GLOBAL TEMPORARY TABLE t_res(" +
		                "new_uuid RAW(16) NOT NULL, " +  /// Pour la copie d'une nouvelle structure
		                "node_uuid RAW(16) NOT NULL, " +
		                "xsi_type VARCHAR2(50 CHAR) DEFAULT NULL, " +
//		              "content CLOB, " +
		                "user_id NUMBER(11) DEFAULT NULL, " +
		                "modif_user_id NUMBER(12) NOT NULL, " +
		                "modif_date timestamp DEFAULT NULL) ON COMMIT DELETE ROWS";
			}
			st = connection.prepareStatement(sql);
			st.execute();
			st.close();

//			t3 = System.currentTimeMillis();

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
		                "uuid RAW(16) NOT NULL, " +
		                "node_parent_uuid RAW(16) NOT NULL, " +
		                "t_level NUMBER(10,0)"+
		                ",  CONSTRAINT t_struc_UK_uuid UNIQUE (uuid)) ON COMMIT DELETE ROWS";
			}
			st = connection.prepareStatement(sql);
			st.execute();
			st.close();

//			t4 = System.currentTimeMillis();

			// En double car on ne peut pas faire d'update/select d'une même table temporaire
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
		                "uuid RAW(16) NOT NULL, " +
		                "node_parent_uuid RAW(16) NOT NULL, " +
		                "t_level NUMBER(10,0)"+
		                ",  CONSTRAINT t_struc_2_UK_uuid UNIQUE (uuid)) ON COMMIT DELETE ROWS";
			}
			st = connection.prepareStatement(sql);
			st.execute();
			st.close();

//			t5 = System.currentTimeMillis();

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

//			t6 = System.currentTimeMillis();

			/// Dans la table temporaire on retrouve les noeuds concernés
			/// (assure une convergence de la récursion et limite le nombre de lignes dans la recherche)
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

//			t7 = System.currentTimeMillis();

			/// On boucle, sera toujours <= à "nombre de noeud du portfolio"
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
				added = stTemp.executeUpdate();   // On s'arrête quand rien à été ajouté
				level = level + 1;    // Prochaine étape
			}
			st.close();
			stTemp.close();

//			t8 = System.currentTimeMillis();

			/// On retire les éléments null, ça pose problême par la suite
			if (dbserveur.equals("mysql")){
				sql = "DELETE FROM t_struc WHERE new_uuid=0x0000000000000000000000000000000";
			} else if (dbserveur.equals("oracle")){
		        sql = "DELETE FROM t_struc WHERE new_uuid='00000000000000000000000000000000'";
			}
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

//			t9 = System.currentTimeMillis();

			/// On filtre les données dont on a pas besoin
			sql = "DELETE FROM t_data WHERE node_uuid NOT IN (SELECT uuid FROM t_struc)";
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

//			t10 = System.currentTimeMillis();

			///// FIXME TODO: Vérifier les droits sur les données restantes

			/// Copie des données non partagés (shared=0)
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
					"AND (shared_res=false OR shared_node=false OR shared_node_res=false)";
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

//			t11 = System.currentTimeMillis();

			/// Résolution des nouveaux uuid avec les parents
			// Avec la structure
			sql = "UPDATE t_data t " +
					"SET t.node_parent_uuid = (SELECT new_uuid FROM t_struc s WHERE s.uuid=t.node_parent_uuid)";
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

//			t12 = System.currentTimeMillis();

			// Avec les ressources
			sql = "UPDATE t_data t " +
					"SET t.res_node_uuid = (SELECT new_uuid FROM t_res r WHERE r.node_uuid= t.res_node_uuid)";
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

//			t13 = System.currentTimeMillis();

			sql = "UPDATE t_data t " +
					"SET t.res_res_node_uuid = (SELECT new_uuid FROM t_res r WHERE r.node_uuid= t.res_res_node_uuid)";
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

//			t14 = System.currentTimeMillis();

			sql = "UPDATE t_data t " +
					"SET t.res_context_node_uuid = (SELECT new_uuid FROM t_res r WHERE r.node_uuid=t.res_context_node_uuid)";
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

//			t15 = System.currentTimeMillis();

			/// Mise à jour du parent de la nouvelle copie ainsi que l'ordre
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

//			t16 = System.currentTimeMillis();

			// Mise à jour de l'appartenance au portfolio de destination
			sql = "UPDATE t_data " +
					"SET portfolio_id=(SELECT portfolio_id FROM node WHERE node_uuid=uuid2bin(?))";
			st = connection.prepareStatement(sql);
			st.setString(1, destUuid);
			st.executeUpdate();
			st.close();

			/// Mise à jour de l'appartenance des données
			sql = "UPDATE t_data " +
					"SET modif_user_id=?";
			st = connection.prepareStatement(sql);
			st.setInt(1, userId);
			st.executeUpdate();
			st.close();

			sql = "UPDATE t_res " +
					"SET modif_user_id=?";
			st = connection.prepareStatement(sql);
			st.setInt(1, userId);
			st.executeUpdate();
			st.close();

//			t17 = System.currentTimeMillis();

			/// On copie tout dans les vrai tables
			connection.setAutoCommit(false);

			/// Structure
			sql = "INSERT INTO node(node_uuid, node_parent_uuid, node_order, metadata, metadata_wad, metadata_epm, res_node_uuid, res_res_node_uuid, res_context_node_uuid, shared_res, shared_node, shared_node_res, shared_res_uuid, shared_node_uuid, shared_node_res_uuid, asm_type, xsi_type, semtag, semantictag, label, code, descr, format, modif_user_id, modif_date, portfolio_id) " +
					"SELECT t.new_uuid, t.node_parent_uuid, t.node_order, n.metadata, n.metadata_wad, n.metadata_epm, t.res_node_uuid, t.res_res_node_uuid, t.res_context_node_uuid, t.shared_res, t.shared_node, t.shared_node_res, t.shared_res_uuid, t.shared_node_uuid, t.shared_node_res_uuid, t.asm_type, t.xsi_type, t.semtag, t.semantictag, t.label, t.code, t.descr, t.format, t.modif_user_id, t.modif_date, t.portfolio_id " +
					"FROM t_data t LEFT JOIN node n ON t.node_uuid=n.node_uuid";
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

//			t18 = System.currentTimeMillis();

			/// Resources
			sql = "INSERT INTO resource_table(node_uuid, xsi_type, content, user_id, modif_user_id, modif_date) " +
					"SELECT t.new_uuid, r.xsi_type, r.content, r.user_id, r.modif_user_id, r.modif_date " +
					"FROM t_res t LEFT JOIN resource_table r ON t.node_uuid=r.node_uuid";
			st = connection.prepareStatement(sql);
			st.executeUpdate();
			st.close();

//			t19 = System.currentTimeMillis();

			/// Mise à jour de la liste des enfants
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

//			t20 = System.currentTimeMillis();

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

//			t21 = System.currentTimeMillis();

			/// Ajout des droits des noeuds
			sql = "INSERT INTO group_rights(grid, id, RD, WR, DL, SB, AD, types_id, rules_id) " +
					"SELECT g.grid, r.new_uuid, r.RD, r.WR, r.DL, r.SB, r.AD, r.types_id, r.rules_id " +
					"FROM " +
					"(SELECT gri.grid, gri.label " +
					"FROM node n " +
					"LEFT JOIN group_right_info gri ON n.portfolio_id=gri.portfolio_id " +
					"WHERE n.node_uuid=uuid2bin(?)) AS g," +  // Retrouve les groupes de destination via le noeud de destination
					"(SELECT gri.label, s.new_uuid, gr.RD, gr.WR, gr.DL, gr.SB, gr.AD, gr.types_id, gr.rules_id " +
					"FROM t_struc s, group_rights gr, group_right_info gri " +
					"WHERE s.uuid=gr.id AND gr.grid=gri.grid) AS r " + // Prend la liste des droits actuel des noeuds dupliqués
					"WHERE g.label=r.label"; // On croise le nouveau 'grid' avec le 'grid' d'origine via le label
			st = connection.prepareStatement(sql);
			st.setString(1, destUuid);
			st.executeUpdate();
			st.close();

//			t22 = System.currentTimeMillis();

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

//			end = System.currentTimeMillis();

			/// On récupère le uuid créé
			sql = "SELECT bin2uuid(new_uuid) FROM t_data WHERE node_uuid=uuid2bin(?)";
			st = connection.prepareStatement(sql);
			st.setString(1, baseUuid);
			res = st.executeQuery();
			if( res.next() )
				createdUuid = res.getString(1);
			res.close();
			st.close();
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

				touchPortfolio(destUuid, null);

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
	public int postMoveNodeUp( int userid, String uuid )
	{
		if(!credential.isAdmin(userid) && !credential.isDesigner(userid, uuid) )
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		String sql = "";
		PreparedStatement st;
		int status = -1;

		try
		{
			sql = "SELECT bin2uuid(node_parent_uuid) AS puuid, node_order " +
					"FROM node " +
					"WHERE node_uuid=uuid2bin(?)";
			st = connection.prepareStatement(sql);
			st.setString(1, uuid);
			ResultSet res = st.executeQuery();

			int order = -1;
			String puuid = "";
			if( res.next() )
			{
				order = res.getInt("node_order");
				puuid = res.getString("puuid");
			}
			res.close();

			if( order == 0 )
			{
				status = -2;
			}
			else if( order > 0 )
			{
				connection.setAutoCommit(false);

				/// Swap node order
				sql = "UPDATE node SET node_order=IF( node_order=?, ?, ? ) " +
						"WHERE node_order IN ( ?, ? ) " +
						"AND node_parent_uuid=uuid2bin(?)";
				st = connection.prepareStatement(sql);
				st.setInt(1, order);
				st.setInt(2, order-1);
				st.setInt(3, order);
				st.setInt(4, order-1);
				st.setInt(5, order);
				st.setString(6, puuid);
				st.executeUpdate();
				st.close();

				/// Update children list
				updateMysqlNodeChildren(puuid);

				status = 0;
			}
		}
		catch(SQLException e)
		{
			try
			{
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
				connection.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}

		return status;
	}

	@Override
	public boolean postChangeNodeParent( int userid, String uuid, String uuidParent)
	{
		/// FIXME something with parent uuid too
		if(!credential.isAdmin(userid) && !credential.isDesigner(userid, uuid) )
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		if( uuid == uuidParent ) // Ajouter un noeud à lui-même
			return false;

		String sql = "";
		PreparedStatement st;
		boolean status = false;

		try
		{
			sql = "SELECT bin2uuid(node_parent_uuid) AS puuid " +
					"FROM node " +
					"WHERE node_uuid=uuid2bin(?)";
			st = connection.prepareStatement(sql);
			st.setString(1, uuid);
			ResultSet res = st.executeQuery();

			String puuid="";
			if( res.next() )
			{
				puuid = res.getString("puuid");
			}

			int next = getMysqlNodeNextOrderChildren(uuidParent);

			connection.setAutoCommit(false);

			sql = "UPDATE node " +
					"SET node_parent_uuid=uuid2bin(?), node_order=? " +
					"WHERE node_uuid=uuid2bin(?)";
			st = connection.prepareStatement(sql);
			st.setString(1, uuidParent);
			st.setInt(2, next);
			st.setString(3, uuid);
			st.executeUpdate();

			/// Update children list, origin and destination
			updateMysqlNodeChildren(puuid);
			updateMysqlNodeChildren(uuidParent);

			status = true;
		}
		catch(Exception e)
		{
			try
			{
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
				connection.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}

		return status;
	}

	@Override
	public Object postNode(MimeType inMimeType, String parentNodeUuid, String in,int userId, int groupId) throws Exception {

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

		String nodeUuid = writeNode(rootNode, portfolioUid,  portfolioModelId,userId,nodeOrder,null,parentNodeUuid,0,0, true, null);

		result = "<nodes>";
		result += "<"+nodeType+" ";
		result += DomUtils.getXmlAttributeOutput("id", nodeUuid)+" ";
		result += "/>";
		result += "</nodes>";

		touchPortfolio(parentNodeUuid, null);

		return result;
	}

	@Override
	public Object putNode(MimeType inMimeType, String nodeUuid, String in,int userId, int groupId) throws Exception
	{
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

		int sharedRes = 0;
		int sharedNode = 0;
		int sharedNodeRes = 0;

		//TODO putNode getNodeRight
		if(!credential.hasNodeRight(userId,groupId,nodeUuid, Credential.WRITE))
			throw new RestWebApplicationException(Status.FORBIDDEN, " No WRITE credential ");
		//return "faux";

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

		// Si id défini, alors on écrit en base
		//TODO Transactionnel noeud+enfant
		NodeList children = null;

		children = node.getChildNodes();
		// On parcourt une première fois les enfants pour récuperer la liste à écrire en base
		int j=0;
		for(int i=0;i<children.getLength();i++)
		{
			//if(!children.item(i).getNodeName().equals("#text"))
			//	nodeChildren.add(children.item(i).getAttributes().getNamedItem("id").getNodeValue());
			if(!children.item(i).getNodeName().equals("#text"))
			{
				// On vérifie si l'enfant n'est pas un élement de type code, label ou descr
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
//					metadataWad = processMeta(userId, metadataWad);
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

		touchPortfolio(nodeUuid, null);

		return updatetMySqlNode(nodeUuid, asmType, xsiType, semtag, label, code, descr, format, metadata,metadataWad,metadataEpm,sharedRes,sharedNode,sharedNodeRes, userId);
	}

	@Override
	public Object deleteResource(String resourceUuid,int userId, int groupId) throws Exception
	{
		if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		return deleteMySqlResource(resourceUuid, userId, groupId);
		//TODO asmResource(s) dans table Node et parentNode children a mettre à jour
	}

	@Override
	public Object getResource(MimeType outMimeType, String nodeParentUuid, int userId, int groupId) throws Exception
	{
		java.sql.ResultSet res = getMysqlResourceByNodeParentUuid(nodeParentUuid);
		res.next();
		if(!credential.hasNodeRight(userId,groupId, nodeParentUuid, Credential.READ))
			throw new RestWebApplicationException(Status.FORBIDDEN, " No READ credential ");
		//return "faux";
		return "<asmResource id=\""+res.getString("node_uuid")+"\" contextid=\""+nodeParentUuid+"\"  >"+res.getString("content")+"</asmResource>";
	}

	@Override
	public int postCreateRole(String portfolioUuid, String role, int userId)
	{
		int groupid = 0;
		String rootNodeUuid = "";
		try
		{
			rootNodeUuid = getPortfolioRootNode(portfolioUuid);
		}
		catch( SQLException e2 )
		{
			e2.printStackTrace();
		}

		if(!credential.isAdmin(userId) && !credential.isDesigner(userId, rootNodeUuid) && !credential.isCreator(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		String sql;
		PreparedStatement st = null;
		ResultSet rs = null;

		try
		{
			// Vérifie si le rôle existe pour ce portfolio
			sql = "SELECT gi.gid FROM group_right_info gri " +
					"LEFT JOIN group_info gi ON gri.grid=gi.grid " +
					"WHERE portfolio_id=uuid2bin(?) AND gri.label=?";
			st = connection.prepareStatement(sql);
			st.setString(1, portfolioUuid);
			st.setString(2, role);
			rs = st.executeQuery();

			if( rs.next() )	// On le retourne directement
			{
				groupid = rs.getInt(1);
			}
			else
			{
				connection.setAutoCommit(false);

				// Crée le rôle
				sql = "INSERT INTO group_right_info(portfolio_id, label, owner) VALUES(uuid2bin(?),?,?)";
				st = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
				if (dbserveur.equals("oracle")){
					  st = connection.prepareStatement(sql, new String[]{"grid"});
				}
				st.setString(1, portfolioUuid);
				st.setString(2, role);
				st.setInt(3, 1);

				st.executeUpdate();
				ResultSet key = st.getGeneratedKeys();
				int grid;
				if( key.next() )
				{
					grid = key.getInt(1);

					st.close();
					sql = "INSERT INTO group_info(grid, owner, label) VALUES(?,?,?)";
					st = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
					if (dbserveur.equals("oracle")){
						  st = connection.prepareStatement(sql, new String[]{"gid"});
					}
					st.setInt(1, grid);
					st.setInt(2, 1);
					st.setString(3, role);

					st.executeUpdate();
					key = st.getGeneratedKeys();
					if( key.next() )
					{
						groupid = key.getInt(1);
					}
				}
				else
				{
					connection.rollback();
				}
			}
		}
		catch(Exception ex)
		{
			try
			{	connection.rollback();	}
			catch( SQLException e1 ){ e1.printStackTrace(); }
			ex.printStackTrace();
		}
		finally
		{
			try
			{
				connection.setAutoCommit(true);
				if( rs != null )
					rs.close();
				if( st != null )
					st.close();
			}
			catch( SQLException e )
			{
				e.printStackTrace();
			}
		}

		return groupid;
	}

	@Override
	public String deletePersonRole(String portfolioUuid, String role, int userId, int uid)
	{
		if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		String sql;
		PreparedStatement st = null;
		ResultSet rs = null;
		try
		{
			// Retire la personne du rôle
			sql = "DELETE FROM group_user " +
					"WHERE userid=? " +
					"AND gid=(SELECT gi.gid " +
					"FROM group_right_info gri " +
					"LEFT JOIN group_info gi ON gri.grid=gi.grid " +
					"WHERE gri.portfolio_id=uuid2bin(?) AND gri.label=?)";
			st = connection.prepareStatement(sql);
			st.setInt(1, uid);
			st.setString(2, portfolioUuid);
			st.setString(3, role);
			rs = st.executeQuery();
		}
		catch(Exception ex)
		{
			try
			{	connection.rollback();
				connection.setAutoCommit(true);	}
			catch( SQLException e1 ){ e1.printStackTrace(); }
			ex.printStackTrace();
		}
		finally
		{
			try
			{
				if( rs != null )
					rs.close();
				if( st != null )
					st.close();
			}
			catch( SQLException e )
			{
				e.printStackTrace();
			}
		}

		return "";
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
			throw new RestWebApplicationException(Status.FORBIDDEN, " No WRITE credential ");
			//return "faux";
		}
		else postNode(inMimeType, nodeParentUuid, in, userId, groupId);
		//else throw new Exception("le noeud contient déjà un enfant de type asmResource !");
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
		if(!credential.hasNodeRight(userId,groupId, nodeParentUuid, Credential.WRITE))
			throw new RestWebApplicationException(Status.FORBIDDEN, " No WRITE credential ");
		//return "faux";

		touchPortfolio(nodeParentUuid, null);

		//putNode(inMimeType, nodeUuid, in, userId);
		return updateMysqlResource(nodeUuid,null,DomUtils.getInnerXml(node),userId);
	}

	/*
	 * forcedParentUuid permet de forcer l'uuid parent, indépendamment de l'attribut du noeud fourni
	 */
	private String writeNode(Node node, String portfolioUuid, String portfolioModelId, int userId, int ordrer, String forcedUuid, String forcedUuidParent,int sharedResParent,int sharedNodeResParent, boolean rewriteId, HashMap<String,String> resolve ) throws Exception
	{
		String uuid = "";
		String originUuid = null;
		String parentUuid = null;
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
		}

		String currentid = "";
		Node idAtt = node.getAttributes().getNamedItem("id");
		if(idAtt!=null)
		{
			String tempId = idAtt.getNodeValue();
			if(tempId.length()>0)
				currentid = tempId;
		}

		// Si uuid forcé, alors on ne tient pas compte de l'uuid indiqué dans le xml
		if( rewriteId )   // On garde les uuid par défaut
		{
			uuid = currentid;
		}
		else
		{
			uuid = forcedUuid;
		}

		if( resolve != null )	// Mapping old id -> new id
			resolve.put(currentid, uuid);

		if(forcedUuidParent!=null)
		{
			// Dans le cas d'un uuid parent forcé => POST => on génère un UUID
			parentUuid = forcedUuidParent;
		}

		/// Récupération d'autre infos
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

		// Si id défini, alors on écrit en base
		//TODO Transactionnel noeud+enfant
		NodeList children = null;
		try
		{
			children = node.getChildNodes();
			// On parcourt une première fois les enfants pour récuperer la liste à écrire en base
			for(int i=0;i<children.getLength();i++)
			{
				Node child = children.item(i);

				if( "#text".equals(child.getNodeName()) )
					continue;

				//if(!children.item(i).getNodeName().equals("#text"))
				//	nodeChildren.add(children.item(i).getAttributes().getNamedItem("id").getNodeValue());
				if(children.item(i).getNodeName().equals("metadata-wad"))
				{
					metadataWad = DomUtils.getNodeAttributesString(children.item(i));// " attr1=\"wad1\" attr2=\"wad2\" ";
//					metadataWad = processMeta(userId, metadataWad);

					// Gestion de la securité intégrée
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

					try   /// TODO: à l'intégration avec sakai/LTI
					{
						Node notifyroles = metadataWadNode.getAttributes().getNamedItem("notifyroles");
						if(notifyroles!=null)
						{
							/// Format pour l'instant: actionroles="sender:1,2;responsable:4"
							StringTokenizer tokens = new StringTokenizer(notifyroles.getNodeValue(), " ");
							String merge = "";
							if( tokens.hasMoreElements() )
								merge = tokens.nextElement().toString();
							while (tokens.hasMoreElements())
								merge += ","+tokens.nextElement().toString();

							postNotifyRoles(userId, portfolioUuid, uuid, merge);
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
					try
					{
						String publicatt = children.item(i).getAttributes().getNamedItem("public").getNodeValue();
						if( "Y".equals(publicatt) )
							setPublicState(userId, portfolioUuid, true);
						else if( "N".equals(publicatt) )
							setPublicState(userId, portfolioUuid, false);
					}
					catch(Exception ex) {}

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
				// On vérifie si l'enfant n'est pas un élement de type code, label ou descr
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
					/*
					if(children.item(i).getAttributes().getNamedItem("id")!=null)
					{

						if(nodeChildrenUuid==null) nodeChildrenUuid = "";
						if(j>0) nodeChildrenUuid += ",";
						nodeChildrenUuid += children.item(i).getAttributes().getNamedItem("id").getNodeValue().toString();

						j++;
					}
					//*/
				}
			}
		}
		catch(Exception ex)
		{
			// Pas d'enfants
			ex.printStackTrace();
		}

		//System.out.println(uuid+":"+node.getNodeName()+":"+parentUuid+":"+nodeChildrenUuid);

		// Si on est au debut de l'arbre, on stocke la définition du portfolio
		// dans la table portfolio
		if(uuid!=null && node.getParentNode()!=null)
		{
			// On retrouve le code caché dans les ressources. blegh
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

			if(node.getNodeName().equals("asmRoot"))
			{
			}
			else if(portfolioUuid==null) throw new Exception("Il manque la balise asmRoot !!");
		}

		// Si on instancie un portfolio à partir d'un modèle
		// Alors on gère les share*
		if(portfolioModelId!=null)
		{
			if(sharedNode==1)
			{
				sharedNodeUuid = originUuid;
			}
		}
		else modelNodeUuid = null;

		if(uuid!=null && !node.getNodeName().equals("portfolio") && !node.getNodeName().equals("asmResource"))
			returnValue = insertMySqlNode(uuid, parentUuid, "", asmType, xsiType,
					sharedRes, sharedNode, sharedNodeRes, sharedResUuid, sharedNodeUuid,sharedNodeResUuid, metadata, metadataWad, metadataEpm,
					semtag, semanticTag,
					label, code, descr, format, ordrer ,userId, portfolioUuid);

		// Si le parent a été forcé, cela veut dire qu'il faut mettre à jour les enfants du parent
		//TODO
		// MODIF : On le met à jour tout le temps car dans le cas d'un POST les uuid ne sont pas connus à l'avance
		//if(forcedUuidParent!=null)

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

		// On vérifie enfin si on a importé des ressources fichiers dont l'UID correspond
		// pour remplacer l'UID d'origine par l'UID generé
		/*
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
		//*/

		// On reparcourt ensuite les enfants pour continuer la recursivité
		//		if(children!=null && sharedNode!=1)
		if( children!=null )
		{
			int k=0;
			for(int i=0;i<children.getLength();i++)
			{
				Node child = children.item(i);
				String childId = null;
				if( !rewriteId )
					childId = UUID.randomUUID().toString();

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
						writeNode(child,portfolioUuid,portfolioModelId,userId,k,childId,uuid,sharedRes,sharedNodeRes,rewriteId, resolve);
						k++;
					}
					else if( "asmResource".equals(nodeName) ) // Les asmResource pose problême dans l'ordre des noeuds
					{
						writeNode(child,portfolioUuid,portfolioModelId,userId,k,childId,uuid,sharedRes,sharedNodeRes,rewriteId, resolve);
					}
				}
			}
		}

		updateMysqlNodeChildren(forcedUuidParent);

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
		//// ost as form with uploadfile


		String portfolioUuid = this.getPortfolioUuidByNodeUuid(nodeUuid);
		// Cas ou on utilise la methode postPortfolioZip :
		// On a generé un uuid preliminaire au moment du dezippage, mais les noeuds ne sont pas encore importés
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

		// Si le fichier existe déjà (cas du dezippage) on ne le recree pas
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

		return updateMysqlPortfolioConfiguration(portfolioUuid, portfolioActive);
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
	public boolean postNodeRight(int userId, String nodeUuid) throws Exception
	{
		// TODO Auto-generated method stub
		return false;
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
					if( owner == 0 )
						owner = userId;
				}
				else
				{
					owner = userId;
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

		if( grid == null ) return "";

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

		//On renvoie le body pour qu'il soit stocké dans le log
		result = "<group ";
		result += DomUtils.getXmlAttributeOutputInt("grid", grid)+" ";
		result += DomUtils.getXmlAttributeOutputInt("owner", owner)+" ";
		result += DomUtils.getXmlAttributeOutput("label", label)+" ";
		result += ">";
		result += "</group>";

		return result;
	}

	@Override
	public Object getUsers(int userId) throws Exception
	{
		ResultSet res = getMysqlUsers(userId);

		String result = "<users>";
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

		return result;
	}

	@Override
	public Object getGroupRights(int userId, int groupId) throws Exception
	{
		if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		ResultSet res = getMysqlGroupRights(userId, groupId);
		String AD = "1";
		String SB = "1";
		String WR = "1";
		String DL = "1";
		String RD = "1";

		String result = "<groupRights>";
		while(res.next())
		{
			result += "<groupRight ";
			result += DomUtils.getXmlAttributeOutput("gid", res.getString("gid"))+" ";
			result += DomUtils.getXmlAttributeOutput("templateId", res.getString("grid"))+" ";
			result += ">";

			result += "<item ";
			if (AD.equalsIgnoreCase(res.getString("AD"))){
				result += DomUtils.getXmlAttributeOutput("add", "True")+" ";
			}else {result += DomUtils.getXmlAttributeOutput("add", "False")+" ";}
			result += DomUtils.getXmlAttributeOutput("creator", res.getString("owner"))+" ";
			result += DomUtils.getXmlAttributeOutput("date", res.getString("DL"))+" ";
			if (DL.equals(res.getString("DL"))){
				result += DomUtils.getXmlAttributeOutput("del", "True")+" ";
			}else {result += DomUtils.getXmlAttributeOutput("del", "False")+" ";}
			result += DomUtils.getXmlAttributeOutput("id", res.getString("id"))+" ";
			result += DomUtils.getXmlAttributeOutput("owner", res.getString("owner"))+" ";
			if (RD.equals(res.getString("RD"))){
				result += DomUtils.getXmlAttributeOutput("read", "True")+" ";
			}else {result += DomUtils.getXmlAttributeOutput("read", "False")+" ";}
			if (SB.equals(res.getString("SB"))){
				result += DomUtils.getXmlAttributeOutput("submit", "True")+" ";
			}else {result += DomUtils.getXmlAttributeOutput("submit", "False")+" ";}
			//result += DomUtils.getXmlAttributeOutput("type", res.getString("t.label"))+" ";
			result += DomUtils.getXmlAttributeOutput("typeId", res.getString("types_id"))+" ";
			if (WR.equals(res.getString("WR"))){
				result += DomUtils.getXmlAttributeOutput("write", "True")+" ";
			}else {result += DomUtils.getXmlAttributeOutput("write", "False")+" ";}
			result += "/>";

			while(res.next())
			{
				result += "<item ";
				if (AD.equalsIgnoreCase(res.getString("AD"))){
					result += DomUtils.getXmlAttributeOutput("add", "True")+" ";
				}else {result += DomUtils.getXmlAttributeOutput("add", "False")+" ";}
				result += DomUtils.getXmlAttributeOutput("creator", res.getString("owner"))+" ";
				result += DomUtils.getXmlAttributeOutput("date", res.getString("DL"))+" ";
				if (DL.equals(res.getString("DL"))){
					result += DomUtils.getXmlAttributeOutput("del", "True")+" ";
				}else {result += DomUtils.getXmlAttributeOutput("del", "False")+" ";}
				result += DomUtils.getXmlAttributeOutput("id", res.getString("id"))+" ";
				result += DomUtils.getXmlAttributeOutput("owner", res.getString("owner"))+" ";
				if (RD.equals(res.getString("RD"))){
					result += DomUtils.getXmlAttributeOutput("read", "True")+" ";
				}else {result += DomUtils.getXmlAttributeOutput("read", "False")+" ";}
				if (SB.equals(res.getString("SB"))){
					result += DomUtils.getXmlAttributeOutput("submit", "True")+" ";
				}else {result += DomUtils.getXmlAttributeOutput("submit", "False")+" ";}
				//result += DomUtils.getXmlAttributeOutput("type", res.getString("t.label"))+" ";
				result += DomUtils.getXmlAttributeOutput("typeId", res.getString("types_id"))+" ";
				if (WR.equals(res.getString("WR"))){
					result += DomUtils.getXmlAttributeOutput("write", "True")+" ";
				}else {result += DomUtils.getXmlAttributeOutput("write", "False")+" ";}
				result += "/>";
			}

			result += "</groupRight>";
		}

		result += "</groupRights>";

		return result;
	}

	@Override
	public boolean postGroupsUsers(int user, int userId, int groupId)
	{
		PreparedStatement stInsert;
		String sqlInsert;

		if(!credential.isAdmin(user))
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
	public boolean postNotifyRoles(int userId, String portfolio, String uuid, String notify)
	{
		boolean ret = false;
		if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		String sql = "";
		PreparedStatement st;
		try
		{
			sql  = "UPDATE group_rights SET notify_roles=? " +
					"WHERE id=uuid2bin(?) AND grid IN " +
					"(SELECT grid FROM group_right_info WHERE portfolio_id=uuid2bin(?))";
			st = connection.prepareStatement(sql);
			st.setString(1, notify);
			st.setString(2, uuid);
			st.setString(3, portfolio);
			st.executeUpdate();

			ret = true;
		}
		catch (SQLException e){ e.printStackTrace(); }

		return ret;
	}

	@Override
	public boolean setPublicState(int userId, String portfolio, boolean isPublic)
	{
		boolean ret = false;
		if( !credential.isAdmin(userId) && !credential.isOwner(userId, portfolio) )
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		String sql = "";
		PreparedStatement st = null;
		try
		{
			// S'assure qu'il y ait au moins un groupe de base
			sql = "SELECT gi.gid " +
					"FROM group_right_info gri LEFT JOIN group_info gi ON gri.grid=gi.grid " +
					"WHERE gri.portfolio_id=uuid2bin(?) AND gri.label='all'";
			st = connection.prepareStatement(sql);
			st.setString(1, portfolio);
			ResultSet rs = st.executeQuery();

			int gid=0;
			if( rs.next() )
				gid = rs.getInt("gid");
			st.close();

			if( gid == 0 )	//  If not exist, create 'all' groups
			{
				connection.setAutoCommit(false);
				sql = "INSERT INTO group_right_info(owner, label, portfolio_id) " +
						"VALUES(?,'all',uuid2bin(?))";
				st = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
				if (dbserveur.equals("oracle")){
					  st = connection.prepareStatement(sql, new String[]{"grid"});
				}
				st.setInt(1, userId);
				st.setString(2, portfolio);

				int grid = 0;
				st.executeUpdate();
				ResultSet key = st.getGeneratedKeys();
				if( key.next() )
					grid = key.getInt(1);
				st.close();

				// Insert all nodes into rights	TODO: Might need updates on additional nodes too
				sql = "INSERT INTO group_rights(grid,id) " +
						"(SELECT ?, node_uuid " +
						"FROM node WHERE portfolio_id=uuid2bin(?))";
				st = connection.prepareStatement(sql);
				st.setInt(1, grid);
				st.setString(2, portfolio);
				st.executeUpdate();
				st.close();

				sql = "INSERT INTO group_info(grid, owner, label) VALUES(?,?,'all')";
				st = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
				if (dbserveur.equals("oracle")){
					  st = connection.prepareStatement(sql, new String[]{"gid"});
				}
				st.setInt(1, grid);
				st.setInt(2, userId);
				st.executeUpdate();

				key = st.getGeneratedKeys();
				if( key.next() )
					gid = key.getInt(1);
				st.close();
				connection.setAutoCommit(true);
			}

			if( isPublic )	// Insère ou retire 'public' dans le groupe 'all' du portfolio
			{
				sql = "INSERT INTO group_user(gid, userid) " +
						"SELECT ?, (SELECT userid FROM credential WHERE login='public')";
			}
			else
			{
				sql = "DELETE FROM group_user " +
						"WHERE userid=(SELECT userid FROM credential WHERE login='public') " +
						"AND gid=?";
			}
			st = connection.prepareStatement(sql);
			st.setInt(1, gid);
			st.executeUpdate();

			ret = true;
		}
		catch (SQLException e)
		{
			try
			{	connection.rollback();
				connection.setAutoCommit(true);	}
			catch( SQLException e1 ){ e1.printStackTrace(); }
			e.printStackTrace();
		}
		finally
		{
			try
			{
				if( st != null )
					st.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}

		return ret;
	}

	@Override
	public int postShareGroup(String portfolio, int user, Integer userId, String write)
	{
		int status = -1;
		String sql = "";
		PreparedStatement st;
		ResultSet rs;
		int gid=0, grid=0;
		int output;
		int wr=0;

		if( write != null || "y".equals(write) )
			wr=1;

		/// Check if portfolio is owner by the user sending this command
		if( !credential.isOwner(userId, portfolio) && !credential.isAdmin(userId) )
			return -2;	// Not owner

		try
		{
			connection.setAutoCommit(false);

			// Check if shared group exist
			sql = "SELECT gi.gid, gri.grid " +
					"FROM group_right_info gri, group_info gi " +
					"WHERE gi.grid=gri.grid " +
					"AND gri.label=? " +
					"AND gri.portfolio_id=uuid2bin(?)";
			st = connection.prepareStatement(sql);
			st.setString(1, "shared");
			st.setString(2, portfolio);
			rs = st.executeQuery();

			if( rs.next() )
			{
				gid = rs.getInt("gid");
				grid = rs.getInt("grid");
				st.close();
			}
			else
			{	//// FIXME: Move group creation in separate method I guess
				st.close();
				/// Create shared group if not exist
				sql = "INSERT INTO group_right_info(owner, label, portfolio_id) VALUES(?,?,uuid2bin(?))";
				st = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
				if (dbserveur.equals("oracle")){
					  st = connection.prepareStatement(sql, new String[]{"grid"});
				}
				st.setInt(1, userId);
				st.setString(2, "shared");
				st.setString(3, portfolio);

				output = st.executeUpdate();
				ResultSet key = st.getGeneratedKeys();
				if( key.next() )
					grid = key.getInt(1);

				st.close();
				sql = "INSERT INTO group_info(grid, owner, label) VALUES(?,?,?)";
				st = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
				if (dbserveur.equals("oracle")){
					  st = connection.prepareStatement(sql, new String[]{"gid"});
				}
				st.setInt(1, grid);
				st.setInt(2, userId);
				st.setString(3, "shared");

				output = st.executeUpdate();
				key = st.getGeneratedKeys();
				if( key.next() )
					gid = key.getInt(1);
			}

			st.close();
			/// Insert user in this shared group
			sql = "INSERT IGNORE INTO group_user(gid, userid) VALUES(?,?)";
			if (dbserveur.equals("oracle")){
				sql = "INSERT /*+ ignore_row_on_dupkey_index(group_user,group_user_PK)*/ INTO group_user(gid, userid) VALUES(?,?)";
			}
			st = connection.prepareStatement(sql);
			st.setInt(1, gid);
			st.setInt(2, user);

			st.executeUpdate();

			st.close();
			/// Flush and insert all rights info in created group
			sql = "DELETE FROM group_rights WHERE grid=?";
			st = connection.prepareStatement(sql);
			st.setInt(1, grid);
			st.executeUpdate();
			st.close();

			sql = "INSERT INTO group_rights(grid, id, RD, WR) " +
					"SELECT ?, node_uuid, 1, ? FROM node WHERE portfolio_id=uuid2bin(?)";
			st = connection.prepareStatement(sql);
			/// With parameter, add default WR, DL
			st.setInt(1, grid);
			st.setInt(2, wr);	/// Flag to select if we write too
			st.setString(3, portfolio);
			st.executeUpdate();

			status = 0;
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

		return status;
	}

	@Override
	public int postCompleteShare( String portfolio, Integer ownerId, Integer userId )
	{
		int status = -1;
		String sql = "";
		PreparedStatement st;

		/// Check if portfolio is owner by the user sending this command
		if( !credential.isOwner(ownerId, portfolio) && !credential.isAdmin(ownerId) )
			return -2;	// Not owner

		try
		{
			connection.setAutoCommit(false);

			/// Insert owner in complete share (when the other person is adding stuff)
			if (dbserveur.equals("mysql")){
				sql = "INSERT IGNORE INTO complete_share(userid, portfolio_id) VALUES(?,uuid2bin(?))";
			} else if (dbserveur.equals("oracle")){
				sql = "INSERT /*+ ignore_row_on_dupkey_index(complete_share,complete_share_PK)*/ INTO complete_share(userid, portfolio_id) VALUES(?,uuid2bin(?))";
			}
			st = connection.prepareStatement(sql);
			st.setInt(1, ownerId);
			st.setString(2, portfolio);

			st.executeUpdate();

			/// Insert person we shared with
			st.setInt(1, userId);

			st.executeUpdate();
			st.close();

			status = 0;
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

		return status;
	}

	@Override
	public int deleteCompleteShare( String portfolio, Integer ownerId )
	{
		int status = -1;
		String sql = "";
		PreparedStatement st;

		/// Check if portfolio is owner by the user sending this command
		if( !credential.isOwner(ownerId, portfolio) && !credential.isAdmin(ownerId) )
			return -2;	// Not owner

		try
		{
			connection.setAutoCommit(false);

			sql = "DELETE FROM complete_share WHERE portfolio_id=uuid2bin(?)";
			st = connection.prepareStatement(sql);
			st.setString(1, portfolio);
			st.executeUpdate();

			st.close();

			status = 0;
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

		return status;
	}

	@Override
	public int deleteCompleteShareUser( String portfolio, Integer ownerId, Integer userId )
	{
		int status = -1;
		String sql = "";
		PreparedStatement st;
		ResultSet rs;

		/// Check if portfolio is owner by the user sending this command
		if( !credential.isOwner(ownerId, portfolio) && !credential.isAdmin(ownerId) )
			return -2;	// Not owner

		try
		{
			connection.setAutoCommit(false);

			/// Check with how many people the portfolio is still shared with
			sql = "SELECT COUNT(*) FROM complete_share WHERE portfolio_id=uuid2bin(?)";
			st = connection.prepareStatement(sql);
			st.setString(1, portfolio);
			rs = st.executeQuery();
			int count = 0;
			if( rs.next() )
				count = rs.getInt(1);

			if( count > 2 )
			{
				sql = "DELETE FROM complete_share WHERE portfolio_id=uuid2bin(?) AND userid=?";
				st = connection.prepareStatement(sql);
				st.setString(1, portfolio);
				st.setInt(2, userId);
				st.executeUpdate();
			}
			else
			{	// Only owner and remaining user we have shared with
				sql = "DELETE FROM complete_share WHERE portfolio_id=uuid2bin(?)";
				st = connection.prepareStatement(sql);
				st.setString(1, portfolio);
				st.executeUpdate();
			}

			st.close();

			status = 0;
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

		return status;
	}

	@Override
	public int deleteShareGroup(String portfolio, Integer userId)
	{
		int status = -1;
		String sql = "";
		PreparedStatement st;

		/// Check if portfolio is owner by the user sending this command
		if( !credential.isOwner(userId, portfolio) && !credential.isAdmin(userId) )
			return -2;	// Not owner

		try
		{
			connection.setAutoCommit(false);

			// Delete and cleanup
			sql = "DELETE gri, gr, gi, gu " +
					"FROM group_right_info gri " +
					"LEFT JOIN group_rights gr ON gri.grid=gr.grid " +
					"LEFT JOIN group_info gi ON gri.grid=gi.grid " +
					"LEFT JOIN group_user gu ON gi.gid=gu.gid " +
					"WHERE gri.label=? " +
					"AND gri.portfolio_id=uuid2bin(?)";
			st = connection.prepareStatement(sql);
			st.setString(1, "shared");
			st.setString(2, portfolio);
			st.executeUpdate();

			status = 0;
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

		return status;
	}

	@Override
	public int deleteSharePerson(String portfolio, int user, Integer userId)
	{
		int status = -1;
		String sql = "";
		PreparedStatement st;

		/// Check if portfolio is owner by the user sending this command
		if( !credential.isOwner(userId, portfolio) && !credential.isAdmin(userId) )
			return -2;	// Not owner

		try
		{
			connection.setAutoCommit(false);

			sql = "DELETE FROM group_user " +
					"WHERE userid=? " +
					"AND gid=(" +
					"SELECT gi.gid " +
					"FROM group_right_info gri, group_info gi " +
					"WHERE gri.grid=gi.grid " +
					"AND gri.label=? " +
					"AND gri.portfolio_id=uuid2bin(?))";
			st = connection.prepareStatement(sql);
			st.setInt(1, user);
			st.setString(2, "shared");
			st.setString(3, portfolio);
			st.executeUpdate();

			status = 0;
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

		return status;
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
		int status = 1;

		try
		{
			/// FIXME: il manque les droits actuels
			sql  = " DELETE gi, gu " +
					"FROM group_info gi " +
					"LEFT JOIN group_user gu ON gi.gid=gu.gid " +
					"WHERE gid=?";
			st = connection.prepareStatement(sql);
			st.setInt(1, groupId);
			st.executeUpdate();

			status =0;
		}
		catch (SQLException e) { e.printStackTrace(); }

		return status;
	}

	@Override
	public Object postPortfolioZip(MimeType mimeType, MimeType mimeType2, HttpServletRequest httpServletRequest, int userId, int groupId, String modelId, int substid) throws IOException
	{
		if(!credential.isAdmin(userId) && !credential.isCreator(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		boolean isMultipart = ServletFileUpload.isMultipartContent(httpServletRequest);
	// Create a factory for disk-based file items
		DiskFileItemFactory factory = new DiskFileItemFactory();

		// Configure a repository (to ensure a secure temp location is used)
		ServletContext servletContext = httpServletRequest.getSession().getServletContext();
		File repository = (File) servletContext.getAttribute("javax.servlet.context.tempdir");
		factory.setRepository(repository);

		// Create a new file upload handler
		ServletFileUpload upload = new ServletFileUpload(factory);

		DataInputStream inZip = null;
		// Parse the request
		try
		{
			List<FileItem> items = upload.parseRequest(httpServletRequest);
		// Process the uploaded items
			Iterator<FileItem> iter = items.iterator();
			while (iter.hasNext())
			{
				FileItem item = iter.next();
				if (!item.isFormField())
				{
					inZip = new DataInputStream(item.getInputStream());
					break;
				}
			}
		}
		catch( FileUploadException e )
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		String foldersfiles= null;
		String filename;
		String[] xmlFiles;
		String[] allFiles;
		int formDataLength = httpServletRequest.getContentLength();
		byte[] buff = new byte[formDataLength];

		// Recuperation de l'heure à laquelle le zip est créé
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


		////// Lecture du fichier de portfolio
		StringBuffer outTrace = new StringBuffer();
		//// Importation du portfolio
		//--- Read xml fileL ----
		///// Pour associer l'ancien uuid -> nouveau, pour les fichiers
		HashMap<String,  String> resolve = new HashMap<String,String>();
		String portfolioUuid = "erreur";
		try
		{
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

				portfolioUuid = UUID.randomUUID().toString();

				if(xml.contains("<portfolio"))	// Le porfolio (peux mieux faire)
				{
					Document doc = DomUtils.xmlString2Document(xml, outTrace);

					Node rootNode = (doc.getElementsByTagName("portfolio")).item(0);
					if(rootNode==null)
						throw new Exception("Root Node (portfolio) not found !");
					else
					{
						rootNode = (doc.getElementsByTagName("asmRoot")).item(0);

						String uuid = UUID.randomUUID().toString();

						insertMysqlPortfolio(portfolioUuid,uuid,0,userId);

						writeNode(rootNode, portfolioUuid, null, userId,0, uuid,null,0,0,false, resolve);
					}
					updateMysqlPortfolioActive(portfolioUuid,true);

					/// Finalement on crée un rôle designer
					int groupid = postCreateRole(portfolioUuid, "designer", userId);

					/// Ajoute la personne dans ce groupe
					putUserGroup(Integer.toString(groupid), Integer.toString(userId));


				}
			}
		}
		catch( Exception e )
		{
			e.printStackTrace();
		}

		for(int i=0;i<allFiles.length;i++)
		{
			String fullPath = allFiles[i];
			String tmpFileName = allFiles[i].substring(allFiles[i].lastIndexOf(File.separator)+1);

			int index = tmpFileName.indexOf("_");
			if( index == -1 )
				index =  tmpFileName.indexOf(".");
			int last = tmpFileName.lastIndexOf("/");
			if( last == -1 )
				last = 0;
			String uuid = tmpFileName.substring(last, index);

//			tmpFileName = allFiles[i].substring(allFiles[i].lastIndexOf(File.separator)+1);
			String lang;
			try
			{
//				int tmpPos = tmpFileName.indexOf("_");
				lang = tmpFileName.substring(index+1,index+3);

				if( "un".equals(lang) )	// Hack sort of fixing previous implementation
					lang = "en";
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
			// Il sera mis à jour avec l'UUID asmContext final dans writeNode
			try
			{
				UUID tmpUuid = UUID.fromString(uuid);	/// base uuid
				String resolved = resolve.get(uuid);	/// New uuid
				String sessionval = session.getId();
				String user = (String) session.getAttribute("user");
//				String test = outsideDir+File.separator+this.portfolioUuidPreliminaire+File.separator+tmpFileName;
//				File file = new File(outsideDir+File.separator+this.portfolioUuidPreliminaire+File.separator+tmpFileName);
				File file = new File(fullPath);

				// server backend
				// fileserver
				String backend = session.getServletContext().getInitParameter("backendserver");
				String fileserver = session.getServletContext().getInitParameter("fileserver");

				if( resolved != null )
				{
//					String urlTarget = "http://"+ fileserver + "/" + resolved;
					String urlTarget = "http://"+ backend + "/" + resolved;

					String fileName = file.getName();
					FileInputStream sendFile = new FileInputStream(file);
					long filesize = sendFile.available();

					URL urlConn = new URL(urlTarget);
					HttpURLConnection connect = (HttpURLConnection) urlConn.openConnection();
					connect.setDoOutput(true);
					connect.setUseCaches(false);                 /// We don't want to cache data
					connect.setInstanceFollowRedirects(false);   /// Let client follow any redirection
					String method = httpServletRequest.getMethod();
					connect.setRequestMethod(method);

					String context = httpServletRequest.getContextPath();
					connect.setRequestProperty("app", context);

					connect.setRequestProperty("filename",fileName);
					connect.setRequestProperty("content-type", "application/octet-stream");
					connect.setRequestProperty("content-length", Long.toString(filesize));

					connect.connect();

					OutputStream outputData = connect.getOutputStream();
					IOUtils.copy(sendFile, outputData);
					sendFile.close();

					/// Those 2 lines are needed, otherwise, no request sent
					int code = connect.getResponseCode();
					String msg = connect.getResponseMessage();

					/// No need to fetch resulting ID, since we provided it
					/*
					InputStream objReturn = connect.getInputStream();
					StringWriter idResponse = new StringWriter();
					IOUtils.copy(objReturn, idResponse);
					fileid = idResponse.toString();
					//*/

					connect.disconnect();


//					PostForm.sendFile(sessionval, backend, user, uuid, lang, file);
				}

				/*
				if(tmpUuid.toString().equals(uuid))
					this.putFile(uuid,lang,tmpFileName,outsideDir,tmpMimeType,extension,b.length,b,userId);
				//*/
			}
			catch(Exception ex)
			{
				// Le nom du fichier ne commence pas par un UUID,
				// ce n'est donc pas une ressource
			}
		}

		//TODO Supprimer le zip quand ça fonctionnera bien


		return portfolioUuid;
	}

	public static String unzip(String zipFile,String destinationFolder) throws FileNotFoundException, IOException
	{
		String folder ="";
		File zipfile = new File(zipFile);
		ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipfile)));

		ZipEntry ze = null;
		try
		{
			while((ze = zis.getNextEntry()) != null)
			{
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
		if(id==null) id = "";
		// Current folder
		File directory = new File(directoryPath);
		File[] subfiles = directory.listFiles();
		ArrayList<String> results = new ArrayList<String>();

		// Under this, try to find necessary files
		for(int i=0 ; i<subfiles.length; i++)
		{
			File fileOrDir = subfiles[i];
			String name = fileOrDir.getName();

			if( "__MACOSX".equals(name) )	/// Could be a better filtering
				continue;

			// One folder level under this one
			if( fileOrDir.isDirectory() )
			{
				File subdir = new File(directoryPath+name);
				File[] subsubfiles = subdir.listFiles();
				for( int j=0; j<subsubfiles.length; ++j )
				{
					File subsubfile = subsubfiles[j];
					String subname = subsubfile.getName();

					if( subname.endsWith(id) || "".equals(id) )
					{
						String completename = directoryPath+name+File.separatorChar+subname;
						results.add(completename);
					}
				}
			}
			else if( fileOrDir.isFile() )
			{
				String subname = fileOrDir.getName();
				if( name.contains(id) || id.equals("") )
				{
					String completename = directoryPath+subname;
					results.add(completename);
				}
			}
		}

		String[] result = new String[results.size()];
		results.toArray(result);

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

		//On renvoie le body pour qu'il soit stocké dans le log
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
			sql = "SELECT bin2uuid(node_uuid) AS node_uuid, bin2uuid(res_node_uuid) AS res_node_uuid, bin2uuid(res_res_node_uuid) AS res_res_node_uuid, bin2uuid(res_context_node_uuid) AS res_context_node_uuid, " +
					"node_children_uuid, code, asm_type, label " +
					"FROM node WHERE portfolio_id = uuid2bin(?) AND " +
					"metadata LIKE ? ORDER BY node_order";
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


	@Override
	public String getGroupRightsInfos(int userId, String portfolioId) throws SQLException
	{
		if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		ResultSet res = getMysqlGroupRightsInfos(userId, portfolioId);

		String result = "<groupRightsInfos>";
		while(res.next())
		{
			result += "<groupRightInfo ";
			result += DomUtils.getXmlAttributeOutput("grid", res.getString("grid"))+" ";
			result += ">";
			result += "<label>"+res.getString("label")+"</label>";
			result += "<owner>"+res.getString("owner")+"</owner>";

			result += "</groupRightInfo>";
		}

		result += "</groupRightsInfos>";

		return result;
	}

	private ResultSet getMysqlGroupRightsInfos(int userId, String portfolioId)
	{
		PreparedStatement st;
		String sql;

		try
		{
			// On recupere d'abord les informations dans la table structures

			sql = "SELECT grid,owner,label FROM group_right_info WHERE  portfolio_id = uuid2bin(?) ";
			//if(userId!=null) sql += "  AND cr.userid = ? ";
			//sql += " ORDER BY display_name ASC ";
			st = connection.prepareStatement(sql);
			st.setString(1, portfolioId);

			return st.executeQuery();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		return null;
	}

	@Override
	public String getListUsers(int userId)
	{
		ResultSet res = getMysqlUsers(userId);

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
				result += DomUtils.getXmlElementOutput("admin", res.getString("is_admin"));
				result += DomUtils.getXmlElementOutput("designer", res.getString("is_designer"));
				result += DomUtils.getXmlElementOutput("email", res.getString("email"));
				result += DomUtils.getXmlElementOutput("active", res.getString("active"));
				result += "</user>";
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return "<users></users>";
		}

		result += "</users>";

		return result;

	}

	/// Retrouve le uid du username
	/// currentUser est là au cas où on voudrait limiter l'accès
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
					result += DomUtils.getXmlElementOutput("admin", res.getString("is_admin"));
					result += DomUtils.getXmlElementOutput("designer", res.getString("is_designer"));
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
			// TODO Auto-generated catch block
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
		String  username = null;
		String  firstname = null;
		String  lastname = null;
		String  active = null;
		String is_admin = null;
		String is_designer = null;

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
				if(children2.item(y).getNodeName().equals("admin"))
				{
					is_admin = DomUtils.getInnerXml(children2.item(y));

					int is_adminInt = Integer.parseInt(is_admin);

					sql = "UPDATE credential SET is_admin = ? WHERE  userid = ?";

					st = connection.prepareStatement(sql);
					st.setInt(1, is_adminInt);
					st.setInt(2, userid2);
					st.executeUpdate();
				}
//				/*
				if(children2.item(y).getNodeName().equals("designer"))
				{
					is_designer = DomUtils.getInnerXml(children2.item(y));

					int is_designerInt = Integer.parseInt(is_designer);

					sql = "UPDATE credential SET is_designer = ? WHERE  userid = ?";

					st = connection.prepareStatement(sql);
					st.setInt(1, is_designerInt);
					st.setInt(2, userid2);
					st.executeUpdate();
				}
				//*/
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
		//			}

		//		}else{
		//			result = "Erreur lors de la recuperation des attributs du groupe dans le XML";
		//		}

		//			try {
		//
		//			  sql = "UPDATE credential SET login = ?, display_firstname = ?, display_lastname = ?, password = ?, email = ? WHERE  userid = ?";
		//
		//				st = connection.prepareStatement(sql);
		//				st.setString(1, username);
		//				st.setString(2, firstname);
		//				st.setString(3, lastname);
		//				st.setString(4, password);
		//				st.setString(5, email);
		//				st.setInt(6, userid2);
		//
		//				st.executeUpdate();
		//
		//			} catch (SQLException e) {
		//				// TODO Auto-generated catch block
		//				e.printStackTrace();
		//			}
		//		result1 = "<users>";
		//
		//			result1 += "<user ";
		//			result1 += DomUtils.getXmlAttributeOutputInt("id", id)+" ";
		//			result1 += ">";
		//			result1 += DomUtils.getXmlElementOutput("password", password)+" ";
		//			result1 += DomUtils.getXmlElementOutput("email", password)+" ";
		//			result1 += "</user>";
		//		result1 += "</users>";

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
		String designerstr = null;
		String  active = "1";
		int id = 0;
		int designer;

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
						if(children2.item(y).getNodeName().equals("designer"))
						{
							designerstr = DomUtils.getInnerXml(children2.item(y));
						}
					}
				}
			}
		}else{
			result = "Erreur lors de la recuperation des attributs de l'utilisateur dans le XML";
		}

		//On ajoute l'utilisateur dans la base de donnees
		try
		{
			sqlInsert = "REPLACE INTO credential(login, display_firstname, display_lastname,email, password, active, is_designer) VALUES (?, ?, ?, ?, UNHEX(SHA1(?)),?,?)";
			stInsert = connection.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS);
			if (dbserveur.equals("oracle")){
				  sqlInsert = "INSERT INTO credential(login, display_firstname, display_lastname,email, password, active, is_designer) VALUES (?, ?, ?, ?, crypt(?),?,?)";
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

			if(designerstr == null)
			{
				designer = 0;
				stInsert.setInt(7, designer);
			}else{
				designer = Integer.parseInt(designerstr);
				stInsert.setInt(7, designer);
			}

			stInsert.executeUpdate();

			ResultSet rs = stInsert.getGeneratedKeys();
			if (rs.next()) {
				id = rs.getInt(1);
			}

		} catch (SQLException e) {
			// TODO Auto-generated catch block
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
		result += DomUtils.getXmlElementOutput("designer", designerstr);
		result += "</user>";

		result += "</users>";

		return result;
	}

	@Override
	public String[] postCredentialFromXml(Integer userId, String username, String password, String substitute) throws ServletException, IOException
	{
		String sql = null;
		ResultSet rs=null;
		PreparedStatement stmt=null;
		String[] returnValue = null;
		int uid = 0;
		int subuid = 0;
		try
		{
			// Does user have a valid account?
			sql = "SELECT userid, login FROM credential WHERE login=? AND password=UNHEX(SHA1(?))";
			if (dbserveur.equals("oracle")){
				sql = "SELECT userid, login FROM credential WHERE login=? AND password=crypt(?)";
			}
			stmt=connection.prepareStatement(sql);
			stmt.setString(1, username);
			stmt.setString(2, password);
			rs = stmt.executeQuery();

			if( rs.next() )
				uid = rs.getInt(1);
			else
				return returnValue;

			if( null != substitute )
			{
				/// Specific lenient substitution rule
				sql = "SELECT cs.id " +
						"FROM credential_substitution cs " +
						"WHERE cs.userid=? AND cs.id=? AND cs.type=?";
				stmt=connection.prepareStatement(sql);
				stmt.setInt(1, uid);
				stmt.setInt(2, 0);		// 0 -> Any account, specific otherwise
				stmt.setString(3, "USER");
				rs = stmt.executeQuery();

				if( rs.next() )	// User can get "any" account, except admin one
				{
					sql = "SELECT c.userid " +
							"FROM credential c " +
							"WHERE c.login=? AND is_admin=0";
					stmt=connection.prepareStatement(sql);
					stmt.setString(1, substitute);
					rs = stmt.executeQuery();

					if( rs.next() )
						subuid = rs.getInt(1);
				}
				else
				{
					/// General rule, when something specific is written in 'id', with USER or GROUP
					sql = "SELECT c.userid " +
							"FROM credential c, credential_substitution cs " +
							"WHERE c.userid=cs.id AND c.login=? AND cs.userid=? AND cs.type='USER' " +	// As specific user
							"UNION " +
							"SELECT c.userid " +
							"FROM credential c, credential_substitution cs, group_user gu " +
							"WHERE c.userid=gu.userid AND gu.gid=cs.id AND c.login=? AND cs.userid=? AND cs.type='GROUP'";	// Anybody in group
					stmt=connection.prepareStatement(sql);
					stmt.setString(1, substitute);
					stmt.setInt(2, uid);
					stmt.setString(3, substitute);
					stmt.setInt(4, uid);
					rs = stmt.executeQuery();

					if( rs.next() )
						subuid = rs.getInt(1);
				}
			}

			ResultSet res;

			returnValue = new String[5];
			returnValue[1] = username;	// Username
			returnValue[2] = Integer.toString(uid);	// User id
			returnValue[4] = Integer.toString(subuid);	// Substitute
			if( subuid != 0 )
			{
				returnValue[3] = substitute;
				res = getMySqlUserByLogin(substitute);
			}
			else
			{
				returnValue[3] = "";
				res = getMySqlUserByLogin(username);
			}

			returnValue[0] ="<credential>";
			returnValue[0] += DomUtils.getXmlElementOutput("useridentifier", res.getString("login"));
			returnValue[0] += DomUtils.getXmlElementOutput("token", res.getString("token"));
			returnValue[0] += DomUtils.getXmlElementOutput("firstname", res.getString("display_firstname"));
			returnValue[0] += DomUtils.getXmlElementOutput("lastname", res.getString("display_lastname"));
			returnValue[0] += DomUtils.getXmlElementOutput("admin", res.getString("is_admin"));
			returnValue[0] += DomUtils.getXmlElementOutput("designer", res.getString("is_designer"));
			returnValue[0] += DomUtils.getXmlElementOutput("email",res.getString("email"));
			returnValue[0] +="</credential>";
		}
		catch (Exception e){ e.printStackTrace(); }

		return returnValue;
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
	public int deleteCredential(int userId)
	{
		if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		int res = updateMysqlCredentialToken(userId, null);

		return res;
	}

	@Override
	public Object getNodeWithXSL(MimeType mimeType, String nodeUuid, String xslFile, int userId, int groupId)
	{
		String xml;
		try {
			xml = getNodeXmlOutput(nodeUuid,true,null,userId, groupId, null,true).toString();
			String param[] = new String[0];
			String paramVal[] = new String[0];
			return DomUtils.processXSLTfile2String( DomUtils.xmlString2Document(xml, new StringBuffer()), xslFile, param, paramVal, new StringBuffer());
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public Object deleteUser(int userid, int userId1)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object postNodeFromModelBySemanticTag(MimeType inMimeType, String parentNodeUuid, String semanticTag,int userId, int groupId) throws Exception
	{
		String portfolioUid = getPortfolioUuidByNodeUuid(parentNodeUuid);

		String portfolioModelId = getPortfolioModelUuid(portfolioUid);

		String xml = getNodeBySemanticTag(inMimeType, portfolioModelId,
				semanticTag,userId, groupId).toString();

		ResultSet res = getMysqlOtherNodeUuidByPortfolioModelUuidBySemanticTag(portfolioModelId, semanticTag);
		res.next();
		// C'est le noeud obtenu dans le modele indiqué par la table de correspondance
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
		NodeRight right = credential.getPortfolioRight(userId,0, portfolioUuid, Credential.READ);
		if(!right.read)
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
	public Integer getRoleByNode( int userId, String nodeUuid, String role )
	{
		if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		String sql = "";
		PreparedStatement st = null;
		ResultSet res = null;

		int group = 0;
		try
		{
			// Check if role exists already
			sql = "SELECT gid FROM group_info gi, group_right_info gri, node n " +
					"WHERE n.portfolio_id=gri.portfolio_id " +
					"AND gri.grid=gi.grid " +
					"AND n.node_uuid = uuid2bin(?) " +
					"AND gri.label = ?";
			st = connection.prepareStatement(sql);
			st.setString(1, nodeUuid);
			st.setString(2, role);
			res = st.executeQuery();
			if( res.next() )
				group = res.getInt("gid");

			// If not, create it
			if( group == 0 )
			{
				res.close();
				st.close();

				sql = "INSERT INTO group_right_info(owner, label, portfolio_id) " +
						"SELECT 1, ?, portfolio_id " +
						"FROM node " +
						"WHERE node_uuid=uuid2bin(?)";
				st = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
				if (dbserveur.equals("oracle")){
					st = connection.prepareStatement(sql, new String[]{"grid"});
				}
				st.setString(1, role);
				st.setString(2, nodeUuid);
				st.executeUpdate();
				ResultSet rs = st.getGeneratedKeys();
				if( rs.next() )
				{
					int retval = rs.getInt(1);
					rs.close(); st.close();

					sql = "INSERT INTO group_info(grid, owner, label) VALUES(?,1,?)";
					st = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
					if (dbserveur.equals("oracle")){
						st = connection.prepareStatement(sql, new String[]{"gid"});
					}
					st.setInt(1, retval);
					st.setString(2, role);
					st.executeUpdate();
					rs = st.getGeneratedKeys();
					if( rs.next() ){ retval = rs.getInt(1); }
				}
			}
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			return null;
		}
		finally
		{
			try
			{
				if( res != null )
					res.close();
				if( st != null )
					st.close();
			}
			catch( SQLException e )
			{
				e.printStackTrace();
			}
		}


		return group;
	}

	@Override
	public String postRoleUser(int userId, int grid, Integer userid2) throws SQLException
	{
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

		/// Vérifie si un groupe existe, déjà associé à un rôle
		if(!res.next())
		{
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

			/// Synchronise les valeurs du rôle avec le groupe d'utilisateur
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

			// Ajoute la personne
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

	@Override
	public String getRolePortfolio(MimeType mimeType, String role, String portfolioId, int userId) throws SQLException
	{
		Integer grid = null;

		PreparedStatement st;
		String sql;
		ResultSet res;

		sql = "SELECT grid FROM group_right_info WHERE label = ? and portfolio_id = uuid2bin(?)";
		st = connection.prepareStatement(sql);
		st.setString(1, role);
		st.setString(2, portfolioId);

		res = st.executeQuery();

		if(res.next()){

			grid = res.getInt("grid");

		}else{

			return "Le grid n'existe pas";
		}

		return "grid = "+grid;
	}

	@Override
	public String getRole(MimeType mimeType, int grid, int userId) throws SQLException
	{
		PreparedStatement st;
		String sql;
		ResultSet res;

		sql = "SELECT grid FROM group_right_info WHERE grid = ?";
		st = connection.prepareStatement(sql);
		st.setInt(1, grid);

		res = st.executeQuery();
		String result = "";
		try
		{
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
	public String getUsersByRole(int userId, String portfolioUuid, String role) throws SQLException
	{
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
			e.printStackTrace();
		}

		result += "</users>";

		return result;

	}

	@Override
	public String getGroupsByRole(int userId, String portfolioUuid, String role)
	{
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
			e.printStackTrace();
		}

		result += "</groups>";

		return result;
	}

	@Override
	public String getUsersByGroup(int userId, int groupId) throws SQLException
	{
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
			e.printStackTrace();
		}

		result += "</groups>";

		return result;
	}

	@Override
	public String postUsersGroupsUser(int userId, int usersgroup, int userid2)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getNodeMetadataWad(MimeType mimeType, String nodeUuid, boolean b, int userId, int groupId, String label) throws SQLException
	{
		StringBuffer result = new StringBuffer();
		// Verification securité
		NodeRight nodeRight = credential.getNodeRight(userId,groupId,nodeUuid, label);

		if(!nodeRight.read)
			return result;

		ResultSet resNode = getMysqlNode(nodeUuid,userId, groupId);

		//try
		//{
		//			resNode.next();

		if(resNode.next())
		{
			//					result.append(indentation+"<"+resNode.getString("asm_type")+" "+DomUtils.getXmlAttributeOutput("id",resNode.getString("node_uuid"))+" ");
			//					//if(resNodes.getString("node_parent_uuid").length()>0)
			//					//	result.append(getXmlAttributeOutput("parent-uuid",resNodes.getString("node_parent_uuid"))+" ");
			//					//		result.append(DomUtils.getXmlAttributeOutput("semantictag",resNode.getString("semtag"))+" ");
			//					String readRight= (nodeRight.read) ? "Y" : "N";
			//					String writeRight= (nodeRight.write) ? "Y" : "N";
			//					String submitRight= (nodeRight.submit) ? "Y" : "N";
			//					String deleteRight= (nodeRight.delete) ? "Y" : "N";
			//
			//
			//					result.append(DomUtils.getXmlAttributeOutput("read",readRight)+" ");
			//					result.append(DomUtils.getXmlAttributeOutput("write",writeRight)+" ");
			//					result.append(DomUtils.getXmlAttributeOutput("submit",submitRight)+" ");
			//					result.append(DomUtils.getXmlAttributeOutput("delete",deleteRight)+" ");


			//							result.append(DomUtils.getXmlAttributeOutput("xsi_type",resNode.getString("xsi_type"))+" ");

			//		result.append(DomUtils.getXmlAttributeOutput("format",resNode.getString("format"))+" ");

			//		result.append(DomUtils.getXmlAttributeOutput("modified",resNode.getTimestamp("modif_date").toGMTString())+" ");

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

	@Override
	public String getResNode(String contextUuid, int userId, int groupId) throws Exception
	{
		PreparedStatement st=null;
		ResultSet res=null;
		String status="";

		try
		{
			String sql = "SELECT content FROM resource_table " +
					"WHERE node_uuid=(SELECT res_node_uuid FROM node WHERE node_uuid=uuid2bin(?))";
			st = connection.prepareStatement(sql);
			st.setString(1, contextUuid);

			res = st.executeQuery();

			if( res.next() )
				status = res.getString("content");
		}
		catch( Exception e )
		{
			e.printStackTrace();
		}
		finally
		{
			if( st != null ) try{ st.close(); }catch( SQLException e ){  }
			if( res != null ) try{ res.close(); }catch( SQLException e ){  }
		}

		return status;
	}

	private int updatetMySqlNodeMetadatawad(String nodeUuid, String metadatawad) throws Exception
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
	public Object putNodeMetadata(MimeType mimeType, String nodeUuid, String xmlNode, int userId, int groupId) throws Exception
	{
		String metadata = "";

		int sharedRes = 0;
		int sharedNode = 0;
		int sharedNodeRes = 0;

		//TODO putNode getNodeRight
		if(!credential.hasNodeRight(userId,groupId,nodeUuid, Credential.WRITE))
			throw new RestWebApplicationException(Status.FORBIDDEN, " No WRITE credential ");
		//return "faux";

		String status = "erreur";

		String portfolioUid = getPortfolioUuidByNodeUuid(nodeUuid);

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
				String publicatt = attr.getNamedItem("public").getNodeValue();
				if( "Y".equals(publicatt) )
					setPublicState(userId, portfolioUid,true);
				else if( "N".equals(publicatt) )
					setPublicState(userId, portfolioUid,false);
			}
			catch(Exception ex) {}

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
				/// Mettre à jour les flags et donnée du champ
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
			catch(Exception ex)
			{
				ex.printStackTrace();
			}

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
	public String getUserGroupByPortfolio(String portfolioUuid, int userId)
	{
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
			e.printStackTrace();
			return null;
		}

		result += "</groups>";
		return result;
	}

	@Override
	public Object putNodeMetadataWad(MimeType mimeType, String nodeUuid, String xmlNode, int userId, int groupId) throws Exception
	{
		String metadatawad = "";

		//TODO putNode getNodeRight
		if(!credential.hasNodeRight(userId,groupId,nodeUuid, Credential.WRITE))
			throw new RestWebApplicationException(Status.FORBIDDEN, " No WRITE credential ");
		//return "faux";

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
//			metadatawad = StringEscapeUtils.escapeXml10(metadatawad);
//			metadatawad = processMeta(userId, metadatawad);
		}

		if (1 == updatetMySqlNodeMetadatawad(nodeUuid,metadatawad)){
			return "editer";
		}

		return "erreur";
	}

	@Override
	public Object putNodeMetadataEpm(MimeType mimeType, String nodeUuid, String xmlNode, int userId, int groupId) throws Exception
	{
		if(!credential.hasNodeRight(userId,groupId,nodeUuid, Credential.WRITE))
			throw new RestWebApplicationException(Status.FORBIDDEN, " No WRITE credential ");
		//return "faux";

		xmlNode = DomUtils.cleanXMLData(xmlNode);
		Document doc = DomUtils.xmlString2Document(xmlNode, new StringBuffer());
		Node node;
		node = doc.getDocumentElement();

		String metadataepm = "";
		if(node.getNodeName().equals("metadata-epm"))
		{
			metadataepm = DomUtils.getNodeAttributesString(node);// " attr1=\"wad1\" attr2=\"wad2\" ";
//			metadataepm = processMeta(userId, metadataepm);
		}

		String sql = "";
		PreparedStatement st;

		sql  = "UPDATE node SET metadata_epm = ? " +
				"WHERE node_uuid = uuid2bin(?) ";
		st = connection.prepareStatement(sql);

		st.setString(1, metadataepm);
		st.setString(2, nodeUuid);

		if (st.executeUpdate() == 1){
			return "editer";
		}

		return "erreur";
	}

	@Override
	public Object putNodeNodeContext(MimeType mimeType, String nodeUuid, String xmlNode, int userId, int groupId) throws Exception
	{
		if(!credential.hasNodeRight(userId,groupId,nodeUuid, Credential.WRITE))
			throw new RestWebApplicationException(Status.FORBIDDEN, " No WRITE credential ");
		//return "faux";

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
	public Object putNodeNodeResource(MimeType mimeType, String nodeUuid, String xmlNode, int userId, int groupId) throws Exception
	{
		if(!credential.hasNodeRight(userId,groupId,nodeUuid, Credential.WRITE))
			throw new RestWebApplicationException(Status.FORBIDDEN, " No WRITE credential ");
		//return "faux";

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
	public Object putRole(String xmlRole, int userId, int roleId) throws Exception
	{
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
			e.printStackTrace();
		}

		result = ""+id;

		return result;
	}

	@Override
	public Object getModels(MimeType mimeType, int userId) throws Exception
	{
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
	public Object getModel(MimeType mimeType, Integer modelId, int userId) throws Exception
	{
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
			e.printStackTrace();
			return null;
		}

		return result;
	}

	@Override
	public Object postModels(MimeType mimeType, String xmlModel, int userId) throws Exception
	{
		if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		String pm_label = null;
		String portfolio_id = null;
		String result = "";

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

						//								  sqlInsert = "REPLACE INTO portfolio_(login, display_firstname, display_lastname,email, password) VALUES (?, ?, ?, ?, UNHEX(SHA1(?)))";
						//								  stInsert = connection.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS);
						//						if (dbserveur.equals("oracle")){
						//						}
						//
						//								  stInsert.setString(1, pm_label);
						//								  stInsert.setString(2, portfolio_id);
						//								  stInsert.executeUpdate();
						//		  						  ResultSet rs = stInsert.getGeneratedKeys();
						//									if (rs.next()) {
						//										  id = rs.getInt(1);
						//										}
						//
						//
						//									result += "<model ";
						//									result += DomUtils.getXmlAttributeOutputInt("id", id)+" ";
						//									result += ">";
						//									result += DomUtils.getXmlElementOutput("label", pm_label);
						//									result += DomUtils.getXmlElementOutput("treeid", portfolio_id);
						//									result += "</user>";
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
			/// Trouve le login associé au userId
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
	public String postMacroOnNode( int userId, String nodeUuid, String macroName )
	{
		String val = "erreur";
		String sql = "";
		PreparedStatement st;
		/// SELECT grid, role, RD,WR,DL,AD,types_id,rules_id FROM rule_table rt LEFT JOIN group_right_info gri ON rt.role=gri.label LEFT JOIN node n ON n.portfolio_id=gri.portfolio_id WHERE rule_id=1 AND n.node_uuid=uuid2bin('d48cafa1-5180-4c83-9e22-5d4d45bbf6e2');
		/// SELECT grid,bin2uuid(id),RD,WR,DL,SB,AD,types_id,rules_id FROM group_rights WHERE id=uuid2bin('d48cafa1-5180-4c83-9e22-5d4d45bbf6e2');
		try
		{
			/// Vérifie si l'utilisateur a le droit d'utiliser cette macro-commande
			// À arranger
			/*
			sql = "SELECT userid FROM group_user gu LEFT JOIN group_info gi ON gu.gid=gi.gid " +
					"LEFT JOIN group_rights gr ON gi.grid=gr.grid " +
					"WHERE gu.userid=? AND id=uuid2bin(?) AND rules_id REGEXP " +
					"(SELECT CONCAT('[[:<:]]',rule_id,'[[:>:]]') FROM rule_info WHERE label=?)";	// Selection stricte, �vite de trouver '11' au lieu de '1'
			st = connection.prepareStatement(sql);
			st.setInt(1, userId);
			st.setString(2, nodeUuid);
			st.setString(3, macroName);
			ResultSet res = st.executeQuery();

			/// res.getFetchSize() retourne 0, même avec un bon résultat
			int uid=0;
			if( res.next() )
				uid = res.getInt("userid");
			if( uid != userId ) return "";

			res.close();
			st.close();
			//*/

			/// Pour retrouver les enfants du noeud et affecter les droits
			sql = "CREATE TEMPORARY TABLE t_struc(" +
					"uuid binary(16) UNIQUE NOT NULL, " +
					"t_level INT) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
			if (dbserveur.equals("oracle")){
			      sql = "CREATE GLOBAL TEMPORARY TABLE t_struc(" +
			              "uuid RAW(16) NOT NULL, " +
			              "t_level NUMBER(10,0)"+
			                ",  CONSTRAINT t_struc_UK_uuid UNIQUE (uuid)) ON COMMIT DELETE ROWS";
			}
			st = connection.prepareStatement(sql);
			st.execute();

			// En double car on ne peut pas faire d'update/select d'une même table temporaire
			sql = "CREATE TEMPORARY TABLE t_struc_2(" +
					"uuid binary(16) UNIQUE NOT NULL, " +
					"t_level INT) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
			if (dbserveur.equals("oracle")){
			      sql = "CREATE GLOBAL TEMPORARY TABLE t_struc_2(" +
			              "uuid RAW(16) NOT NULL, " +
			              "t_level NUMBER(10,0)"+
			                ",  CONSTRAINT t_struc_2_UK_uuid UNIQUE (uuid)) ON COMMIT DELETE ROWS";
			}
			st = connection.prepareStatement(sql);
			st.execute();
			st.close();

			/// Dans la table temporaire on retrouve les noeuds concernés
			/// (assure une convergence de la récursion et limite le nombre de lignes dans la recherche)
			/// Init table
			sql = "INSERT INTO t_struc(uuid, t_level) " +
					"SELECT n.node_uuid, 0 " +
					"FROM node n " +
					"WHERE n.node_uuid=uuid2bin(?)";
			st = connection.prepareStatement(sql);
			st.setString(1, nodeUuid);
			st.executeUpdate();
			st.close();

//			/*
			/// On boucle, récursion par niveau
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
				sqlTemp = "INSERT INTO t_struc SELECT * FROM t_struc_2";
			}
	        PreparedStatement stTemp = connection.prepareStatement(sqlTemp);

			st = connection.prepareStatement(sql);
			while( added != 0 )
			{
				st.setInt(1, level+1);
				st.setInt(2, level);
				st.executeUpdate();
				added = stTemp.executeUpdate();   // On s'arrête quand rien à été ajouté
				level = level + 1;    // Prochaine étape
			}
			st.close();
			stTemp.close();
			//*/

			/// Selection du grid de l'utilisateur
			sql = "SELECT gr.grid, gi.label " +
					"FROM group_rights gr, group_info gi, group_user gu " +
					"WHERE gr.grid=gi.grid AND gi.gid=gu.gid AND gu.userid=? AND gr.id=uuid2bin(?) AND NOT gi.label=\"all\"";
			st = connection.prepareStatement(sql);
			st.setInt(1, userId);
			st.setString(2, nodeUuid);
			ResultSet res = st.executeQuery();
			/// res.getFetchSize() retourne 0, même avec un bon résultat
			int grid=0;
			String grlabl = "";
			if( res.next() )
			{
				grid = res.getInt("grid");
				grlabl = res.getString("label");
			}
			res.close();
			st.close();

			// Fetch metadata
			sql = "SELECT metadata_wad FROM node WHERE node_uuid=uuid2bin(?)";
			st = connection.prepareStatement(sql);
			st.setString(1, nodeUuid);
			res = st.executeQuery();
			String meta = "";
			if( res.next() )
				meta = res.getString("metadata_wad");
			res.close();
			st.close();

			// Parse it, for the amount of manipulation we do, it will be simpler than find/replace
			DocumentBuilderFactory documentBuilderFactory =DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
			meta = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><metadata-wad "+meta+"></metadata-wad>";
			System.out.println("ACTION OUT: "+meta);
			InputSource is = new InputSource(new StringReader(meta));
			Document doc = documentBuilder.parse(is);
			Element rootMeta = doc.getDocumentElement();
			boolean doUpdate = true;


			/// FIXME: Could only change the needed rights
			NamedNodeMap metaAttr = rootMeta.getAttributes();
			if( "show".equals(macroName) || "hide".equals(macroName) )
			{
				// Check if current group can show stuff
				String roles = metaAttr.getNamedItem("showroles").getNodeValue();
				if( roles.contains(grlabl) )	// Can activate it
				{
					String showto = metaAttr.getNamedItem("showtoroles").getNodeValue();
					showto = showto.replace(" ", "\",\"");
					showto = "(\"" + showto +"\")";

					//// Il faut qu'il y a un showtorole
					if( !"(\"\")".equals(showto) )
					{
						// Update rights
						/// Ajoute/remplace les droits
						// FIXME: Je crois que quelque chose manque
						sql = "INSERT INTO group_rights(grid, id, RD, WR, DL, AD, types_id, rules_id) " +
								"SELECT gr.grid, gr.id, ?, 0, 0, 0, NULL, NULL " +
								"FROM group_right_info gri, group_rights gr " +
								"WHERE gri.label IN "+showto+" AND gri.grid=gr.grid AND gr.id IN (SELECT uuid FROM t_struc) " +
								"ON DUPLICATE KEY UPDATE RD=?, WR=gr.WR, DL=gr.DL, AD=gr.AD, types_id=gr.types_id, rules_id=gr.rules_id";

						if (dbserveur.equals("oracle")){
							sql = "MERGE INTO group_rights d USING (SELECT gr.grid, gr.id, ? RD, 0 WR, 0 DL, 0 AD, NULL types_id, NULL rules_id FROM group_right_info gri, group_rights gr WHERE gri.label IN "+showto+" AND gri.grid=gr.grid AND gr.id IN (SELECT uuid FROM t_struc)) s WHEN MATCHED THEN UPDATE SET d.RD=?, d.WR=gr.WR, d.DL=gr.DL, d.AD=gr.AD, d.types_id=gr.types_id, d.rules_id=gr.rules_id WHEN NOT MATCHED THEN INSERT (d.grid, d.id, d.RD, d.WR, d.DL, d.AD, d.types_id, d.rules_id) VALUES (s.grid, s.id, s.RD, s.WR, s.DL, s.AD, s.types_id, s.rules_id)";
						}
						st = connection.prepareStatement(sql);
						if( "hide".equals(macroName) )
						{
							st.setInt(1, 0);
							st.setInt(2, 0);
						}
						else if( "show".equals(macroName) )
						{
							st.setInt(1, 1);
							st.setInt(2, 1);
						}
		//				st.setString(2, showto);
						st.executeUpdate();
						st.close();

						Node isPriv = metaAttr.getNamedItem("private");
						// Update local string
						if( "hide".equals(macroName) )
							isPriv.setNodeValue("Y");
						else if( "show".equals(macroName) )
							isPriv.setNodeValue("N");
					}
				}

				// Update DB
				if( doUpdate )
				{
					meta = DomUtils.getNodeAttributesString(rootMeta);
					System.out.println("META: "+meta);

					sql = "UPDATE node SET metadata_wad=? WHERE node_uuid=uuid2bin(?)";
					st = connection.prepareStatement(sql);
					st.setString(1, meta);
					st.setString(2, nodeUuid);
					st.executeUpdate();
				}

			}
			else if( "submit".equals(macroName) )
			{
				System.out.println("ACTION: "+macroName+" grid: "+grid+" -> uuid: "+nodeUuid);
				// Update rights
				/// Ajoute/remplace les droits
				// FIXME: Je crois que quelque chose manque
				sql = "INSERT INTO group_rights(grid, id, RD, WR, DL, AD, SB, types_id, rules_id) " +
						"SELECT gr.grid, gr.id, 1, 0, 0, 0, 0, NULL, NULL " +
						"FROM group_rights gr " +
						"WHERE gr.id IN (SELECT uuid FROM t_struc) " +
						"ON DUPLICATE KEY UPDATE RD=1, WR=0, DL=0, AD=0, SB=0, types_id=null, rules_id=null";

				if (dbserveur.equals("oracle")){
					sql = "MERGE INTO group_rights d USING (SELECT gr.grid, gr.id, 1 RD, 0 WR, 0 DL, 0 AD, 0 SB, NULL types_id, NULL rules_id FROM group_rights gr WHERE gr.id IN (SELECT uuid FROM t_struc)) s WHEN MATCHED THEN UPDATE SET d.RD=1, d.WR=0, d.DL=0, d.AD=0, d.SB=0, d.types_id=s.types_id, d.rules_id=s.rules_id WHEN NOT MATCHED THEN INSERT (d.grid, d.id, d.RD, d.WR, d.DL, d.AD, d.SB, d.types_id, d.rules_id) VALUES (s.grid, s.id, s.RD, s.WR, s.DL, s.AD, s.SB, s.types_id, s.rules_id)";
				}
				st = connection.prepareStatement(sql);
				st.executeUpdate();
				st.close();

				/// Vérifie le showtoroles
				String showto = metaAttr.getNamedItem("showtoroles").getNodeValue();
				showto = showto.replace(" ", "\",\"");
				showto = "(\"" + showto +"\")";

				//// Il faut qu'il y a un showtorole
				System.out.println("SHOWTO: "+showto);
				if( !"(\"\")".equals(showto) )
				{
					System.out.println("SHOWING TO: "+showto);
					// Update rights
					/// Ajoute/remplace les droits
					// FIXME: Je crois que quelque chose manque
					sql = "INSERT INTO group_rights(grid, id, RD, WR, DL, AD, types_id, rules_id) " +
							"SELECT gri.grid, gr.id, 1, 0, 0, 0, NULL, NULL " +
							"FROM group_right_info gri, group_rights gr " +
							"WHERE gri.label IN "+showto+" " +
							"AND gri.portfolio_id=(" +
								"SELECT portfolio_id FROM node " +
								"WHERE node_uuid=uuid2bin(?)) " +
							"AND gr.id IN (SELECT uuid FROM t_struc) " +
							"ON DUPLICATE KEY UPDATE RD=1, WR=gr.WR, DL=gr.DL, AD=gr.AD, types_id=gr.types_id, rules_id=gr.rules_id";

					if (dbserveur.equals("oracle")){
						sql = "MERGE INTO group_rights d USING (SELECT gri.grid, n.node_uuid, rt.RD, rt.WR, rt.DL, rt.AD, rt.types_id, rt.rules_id FROM rule_table rt LEFT JOIN group_right_info gri ON rt.role=gri.label LEFT JOIN node n ON n.portfolio_id=gri.portfolio_id WHERE rt.rule_id=? AND n.node_uuid IN (SELECT uuid FROM t_struc)) s ON (d.grid = s.grid AND d.id = s.id) WHEN MATCHED THEN UPDATE SET d.RD=rt.RD, d.WR=rt.WR, d.DL=rt.DL, d.AD=rt.AD, d.types_id=rt.types_id, d.rules_id=rt.rules_id WHEN NOT MATCHED THEN INSERT (d.grid, d.id, d.RD, d.WR, d.DL, d.AD, d.types_id, d.rules_id) VALUES (s.grid, s.id, s.RD, s.WR, s.DL, s.AD, s.types_id, s.rules_id)";
					}
					st = connection.prepareStatement(sql);
					st.setString(1, nodeUuid);
					st.executeUpdate();
					st.close();

//					Node isPriv = metaAttr.getNamedItem("private");
//					isPriv.setNodeValue("Y");
				}
			}

			val = "OK";
		}
		catch( SQLException e )
		{
			e.printStackTrace();
		}
		catch( Exception e )
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
			st = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
			if (dbserveur.equals("oracle")){
				  st = connection.prepareStatement(sql, new String[]{"rule_id"});
			}
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
			st = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
			if (dbserveur.equals("oracle")){
				  st = connection.prepareStatement(sql, new String[]{"def_id"});
			}
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
			// On récuère l'identifiant du noeud 'racine' des données ajoutés
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
			        		"res_context_node_uuid RAW(16)) ON COMMIT DELETE ROWS";
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
			        		"types_id CLOB," +
			        		"rules_id CLOB) ON COMMIT DELETE ROWS";
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
			        		"xsi_type VARCHAR2(50 CHAR)) ON COMMIT DELETE ROWS";
			}
			st = connection.prepareStatement(sql);
			st.execute();
			st.close();

			/// Instancie les données nécéssaire et génère les uuid
			sql = "INSERT INTO t_data(node_id,asm_type,xsi_type,parent_node,node_data,instance_rule,node_uuid) " +
					"SELECT node_id,asm_type,xsi_type,parent_node,node_data,instance_rule,";
			if (dbserveur.equals("mysql")){
				sql += " uuid2bin(UUID()) ";
			} else if (dbserveur.equals("oracle")){
				sql += " sys_guid() ";
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
					"'','',''," +
					"false, false, false," +
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
		            sql = "{call drop_tables(tmpTableList('t_data', 't_struc', 't_rights'))}";
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
				/// Requète longue, il faudrait le prendre d'un autre chemin avec ensemble plus petit, si possible
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

			if( bypass )  // WAD6 demande un format spécifique pour ce type de requête (...)
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

	/// Liste des RRG et utilisateurs d'un portfolio donné
	@Override
	public String getPortfolioInfo( int userId, String portId )
	{
		String status = "erreur";
		PreparedStatement st;
		ResultSet res=null;

		try
		{
			// group_right_info pid:grid -> group_info grid:gid -> group_user gid:userid
			String sql = "SELECT gri.grid, gri.label, gu.userid " +
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
				if( rrg != res.getLong("gri.grid") )
				{
					rrg = res.getLong("gri.grid");
					Element rrgNode = document.createElement("rrg");
					rrgNode.setAttribute("id", Long.toString(rrg));

					Element rrgLabel = document.createElement("label");
					rrgLabel.setTextContent(res.getString("gri.label"));

					rrgUsers = document.createElement("users");

					rrgNode.appendChild(rrgLabel);
					rrgNode.appendChild(rrgUsers);
					root.appendChild(rrgNode);
				}

				Long uid = res.getLong("gu.userid");
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
		if(!credential.isAdmin(userId) && !credential.isOwnerRRG(userId, rrgId))
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

		/// Problême de parsage
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
	public String postRRGCreate( int userId, String portfolio, String data )
	{
		if(!credential.isAdmin(userId) && !credential.isOwner(userId, portfolio))
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

		/// Problême de parsage
		if( document == null ) return value;

		try
		{
			connection.setAutoCommit(false);
			Element labelNode = document.getDocumentElement();
			String label = null;
			//      NodeList rrgNodes = document.getElementsByTagName("rolerightsgroup");

			String sqlRRG = "INSERT INTO group_right_info(owner,label,portfolio_id) VALUES(?,?,uuid2bin(?))";
			PreparedStatement rrgst = connection.prepareStatement(sqlRRG, Statement.RETURN_GENERATED_KEYS);
			if (dbserveur.equals("oracle")){
				rrgst = connection.prepareStatement(sqlRRG, new String[]{"grid"});
			}
			rrgst.setInt(1, userId);
			//      String sqlGU = "INSERT INTO group_info(grid,owner,label) VALUES(?,?,?)";
			//      PreparedStatement gust = connection.prepareStatement(sqlGU);
			//      gust.setInt(2, userId);
			if( labelNode != null )
			{
				Node labelText = labelNode.getFirstChild();
				if( labelText != null )
					label = labelText.getNodeValue();
			}

			if( label == null ) return value;
			/// Création du groupe de droit
			rrgst.setString(2, label);
			rrgst.setString(3, portfolio);
			rrgst.executeUpdate();

			ResultSet rs = rrgst.getGeneratedKeys();
			Integer grid=0;
			if( rs.next() )
				grid = rs.getInt(1);
			rrgst.close();
			labelNode.setAttribute("id", Integer.toString(grid));

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
		if(!credential.isAdmin(userId) && !credential.isOwnerRRG(userId, rrgid))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		String value="";
		ResultSet res=null;
		try
		{
			connection.setAutoCommit(false);

			/// Vérifie si un group_info/grid existe
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
		if(!credential.isAdmin(userId) && !credential.isOwnerRRG(userId, rrgid))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

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

		/// Problême de parsage
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
		if(!credential.isAdmin(userId) && !credential.isOwnerRRG(userId, rrgId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

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
		if(!credential.isAdmin(userId) && !credential.isOwnerRRG(userId, rrgId))
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


	/// Retire les utilisateurs des RRG d'un portfolio donné
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
	public String postUsersGroups(int userId)
	{
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
	public String getUsersByGroup(int userId)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String deleteUsersGroups(int userId, int usersgroup)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String deleteUsersGroupsUser(int userId, int usersgroup, int userid2)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getRessource(String nodeUuid, int userId, int groupId, String type) throws SQLException
	{
		// Récupére le noeud, et assemble les ressources, si il y en a

		//ResultSet res = getMysqlPortfolios(userId,portfolioActive);
		String result = "";

		ResultSet resNode = null;
		ResultSet resResource = null;

		/*
		DocumentBuilderFactory documentBuilderFactory =DocumentBuilderFactory.newInstance();
		DocumentBuilder documentBuilder = null;
		Document document = null;

		try {
			documentBuilder = documentBuilderFactory.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
//*/
		resNode = getMysqlNode(nodeUuid,userId, groupId);
		resResource = null;

		if(resNode.next())
		{
			result += "<"+resNode.getString("asm_type")+" id='"+resNode.getString("node_uuid")+"'>";
			result += "<metadata "+resNode.getString("metadata")+"/>";
			result += "<metadata-epm "+resNode.getString("metadata_epm")+"/>";
			result += "<metadata-wad "+resNode.getString("metadata_wad")+"/>";

			resResource = getMysqlResource(resNode.getString("res_node_uuid"));
			if (resResource.next())
			{
				if(resNode.getString("res_node_uuid")!=null)
					if(resNode.getString("res_node_uuid").length()>0)
					{
						result += "<asmResource id='"+resNode.getString("res_node_uuid")+"' contextid='"+resNode.getString("node_uuid")+"' xsi_type='"+resResource.getString("xsi_type")+"'>";
						//String text = "<node>"+resResource.getString("content")+"</node>";
						result += resResource.getString("content");
						result += "</asmResource>";

						resResource.close();
					}
			}
			resResource = getMysqlResource(resNode.getString("res_res_node_uuid"));
			if (resResource.next())
			{
				if(resNode.getString("res_res_node_uuid")!=null)
					if(resNode.getString("res_res_node_uuid").length()>0)
					{
						result += "<asmResource id='"+resNode.getString("res_res_node_uuid")+"' contextid='"+resNode.getString("node_uuid")+"' xsi_type='"+resResource.getString("xsi_type")+"'>";
						//String text = "<node>"+resResource.getString("content")+"</node>";
						result += resResource.getString("content");
						result += "</asmResource>";

						resResource.close();
					}
			}
			resResource = getMysqlResource(resNode.getString("res_context_node_uuid"));
			if (resResource.next())
			{
				if(resNode.getString("res_context_node_uuid")!=null)
					if(resNode.getString("res_context_node_uuid").length()>0)
					{
						result += "<asmResource id='"+resNode.getString("res_context_node_uuid")+"' contextid='"+resNode.getString("node_uuid")+"' xsi_type='"+resResource.getString("xsi_type")+"'>";
						//String text = "<node>"+resResource.getString("content")+"</node>";
						result += resResource.getString("content");
						result += "</asmResource>";

						resResource.close();
					}
			}

			result += "</"+resNode.getString("asm_type")+">";
		}

		resNode.close();

		return result;
	}

	@Override
	public Object getNodes(MimeType mimeType, String portfoliocode, String semtag, int userId, int groupId, String semtag_parent, String code_parent) throws SQLException
	{
		PreparedStatement st = null;
		String sql;
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

			// Not null, not empty
			if(semtag_parent != null && !"".equals(semtag_parent) && code_parent != null && !"".equals(code_parent) )
			{
				res3 = getMysqlNodeUuidBySemanticTag(pid, semtag_parent);

				while(res3.next())
				{
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
									e.printStackTrace();
								}

								String text = "<node>"+resResource.getString("content")+"</node>";

								try {
									document = documentBuilder.parse(new ByteArrayInputStream(text.getBytes("UTF-8")));
									//document = documentBuilder.parse(new InputSource(new StringReader(text)));
								} catch (SAXException e) {
									e.printStackTrace();
								} catch (IOException e) {
									e.printStackTrace();
								}

								String nom = document.getElementsByTagName("code").item(0).getTextContent();

								if(nom != null)
								{
									if(nom.equals(code_parent))
									{
										String children = res3.getString("node_children_uuid");
										String delim = ",";
										String[] listChildren = null;
										listChildren = children.split(delim);
										result += "<nodes>";
										for(int i = 0; i < listChildren.length; i++){
											sql = "SELECT bin2uuid(node_uuid) AS node_uuid, metadata, asm_type "
													+ "FROM node "
													+ "WHERE node_uuid = uuid2bin(?) and metadata LIKE ?";
											st = connection.prepareStatement(sql);
											st.setString(1, listChildren[i]);
											st.setString(2, "%semantictag=%"+semtag+"%");
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
	public Object getPortfolioZip(MimeType mimeType, String portfolioUuid, int userId, int groupId, String label, Boolean resource, Boolean files) throws Exception
	{
		return null;
	}


	@Override
	public Object getNodesParent(MimeType mimeType, String portfoliocode, String semtag, int userId, int groupId, String semtag_parent, String code_parent) throws Exception
	{
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
		PreparedStatement st;
		String sql;
		ResultSet res;
		String retval = "0";

		try
		{
			Date date = new Date();
			sql = "INSERT INTO credential SET login=?, display_firstname=?, display_lastname='', password=UNHEX(SHA1(?))";
			if (dbserveur.equals("oracle")){
				sql = "INSERT INTO credential SET login=?, display_firstname=?, display_lastname='', password=crypt(?)";
			}
			st = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
			if (dbserveur.equals("oracle")){
				st = connection.prepareStatement(sql, new String[]{"userid"});
			}
			st.setString(1, username);
			st.setString(2, username);
			st.setString(3, date.toString()+"somesalt");
			st.executeUpdate();
			res = st.getGeneratedKeys();
			if( res.next() )
				retval = Integer.toString(res.getInt(1));
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
		}

		return retval;
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
			st = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
			if (dbserveur.equals("oracle")){
				st = connection.prepareStatement(sql, new String[]{"grid"});
			}
			st.setString(1, name);
			st.executeUpdate();
			ResultSet rs = st.getGeneratedKeys();
			if( rs.next() )
			{
				retval = rs.getInt(1);
				rs.close(); st.close();

				sql = "INSERT INTO group_info(grid, owner, label) VALUES(?,1,?)";
				st = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
				if (dbserveur.equals("oracle")){
					st = connection.prepareStatement(sql, new String[]{"gid"});
				}
				st.setInt(1, retval);
				st.setString(2, name);
				st.executeUpdate();
				rs = st.getGeneratedKeys();
				if( rs.next() )
				{
					retval = rs.getInt(1);
				}
			}

			rs.close(); st.close();
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

	@Override
	public Set<String[]> getNotificationUserList( int userId, int groupId, String uuid )
	{
		PreparedStatement st;
		String sql;
		ResultSet res;
		Set<String[]> retval = new HashSet<String[]>();

		try
		{
			// gid+userid -> grid+uuid -> notify_role
			/// Fetch roles to be notified
			sql = "SELECT bin2uuid(gri.portfolio_id), gr.notify_roles " +
					"FROM group_user gu " +
					"LEFT JOIN group_info gi ON gu.gid=gi.gid " +
					"LEFT JOIN group_right_info gri ON gi.grid=gri.grid " +
					"LEFT JOIN group_rights gr ON gri.grid=gr.grid " +
					"WHERE gu.gid=? AND gu.userid=? AND gr.id=uuid2bin(?)";
			st = connection.prepareStatement(sql);
			st.setInt(1, groupId);
			st.setInt(2, userId);
			st.setString(3, uuid);
			res = st.executeQuery();

			String roles = "";
			String portfolio = "";
			if( res.next() )
			{
				portfolio = res.getString(1);
				roles = res.getString(2);
			}
			res.close(); st.close();

			if( "".equals(roles) || roles == null )
				return retval;

			String[] roleArray = roles.split(",");
			Set<String> roleSet = new HashSet<String>(Arrays.asList(roleArray));

			/// Fetch all users/role associated with this portfolio
			sql = "SELECT gi.label, c.login, c.display_lastname " +
					"FROM group_right_info gri " +
					"LEFT JOIN group_info gi ON gri.grid=gi.grid " +
					"LEFT JOIN group_user gu ON gi.gid=gu.gid " +
					"LEFT JOIN credential c ON gu.userid=c.userid " +
					"WHERE gri.portfolio_id=uuid2bin(?)";
			st = connection.prepareStatement(sql);
			st.setString(1, portfolio);
			res = st.executeQuery();

			/// Filter those we don't care
			while( res.next() )
			{
				String label = res.getString(1);
				String login = res.getString(2);
				String lastname = res.getString(3);

				if( roleSet.contains(label) )
				{
					String val[] = {login, lastname};
					retval.add(val);
				}
			}
			res.close(); st.close();
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
		}
		finally
		{
		}

		return retval;
	}

	@Override
	public boolean touchPortfolio( String fromNodeuuid, String fromPortuuid )
	{
		boolean hasChanged = false;

		String sql;
		PreparedStatement st = null;
		ResultSet res = null;

		try
		{
			if( fromNodeuuid != null )
			{
				sql = "UPDATE portfolio SET modif_date=NOW() ";
				if (dbserveur.equals("oracle")){
					sql = "UPDATE portfolio SET modif_date=CURRENT_TIMESTAMP ";
				}
				sql += "WHERE portfolio_id=(SELECT portfolio_id FROM node WHERE node_uuid=uuid2bin(?))";
				st = connection.prepareStatement(sql);
				st.setString(1, fromNodeuuid);
				st.executeUpdate();

				hasChanged = true;
			}
			else if( fromPortuuid != null )
			{
				sql = "UPDATE portfolio SET modif_date=NOW() ";
				if (dbserveur.equals("oracle")){
					sql = "UPDATE portfolio SET modif_date=CURRENT_TIMESTAMP ";
				}
				sql += "WHERE portfolio_id=uuid2bin(?)";
				st = connection.prepareStatement(sql);
				st.setString(1, fromPortuuid);
				st.executeUpdate();

				hasChanged = true;
			}
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
		}
		finally
		{
			try
			{
				if( res != null )
					res.close();
				if( st != null )
					st.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}

		return hasChanged;
	}
}
