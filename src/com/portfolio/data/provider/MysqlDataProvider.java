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
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.mysql.jdbc.Statement;
import com.portfolio.data.utils.ConfigUtils;
import com.portfolio.data.utils.DomUtils;
import com.portfolio.data.utils.FileUtils;
import com.portfolio.data.utils.PostForm;
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

//	private Connection connection = null;
	final private Credential cred = new Credential();
	private String portfolioUuidPreliminaire = null; // Sert pour generer un uuid avant import du portfolio
//	private final ArrayList<String> portfolioRessourcesImportUuid = new ArrayList();
//	private final ArrayList<String> portfolioRessourcesImportPath = new ArrayList();
	public static final Integer RIGHT_GET = 1;
	public static final Integer RIGHT_POST = 2;
	public static final Integer RIGHT_PUT = 3;
	public static final Integer RIGHT_DELETE = 4;


//	private final String dbserveur = "mysql";
//	private final String dbserveur = "oracle";
	private String dbserveur = null;

	@Override
	public void dataProvider()
	{
	}

	DataSource ds = null;

	public MysqlDataProvider() throws Exception
	{
		dbserveur = ConfigUtils.get("serverType");
	}

	@Override
	public void setConnection( Connection c )
	{
//		this.connection = c;
//		credential = new Credential(connection);
	}

	/*
	@Override
	public void disconnect(){

		try {
			connection.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
		//*/

	public Integer getMysqlNodeNextOrderChildren(Connection c, String nodeUuid)  throws Exception
	{
		PreparedStatement st;
		String sql;

		// On recupere d'abord les informations dans la table structures
		sql = "SELECT COUNT(*) as node_order FROM node WHERE node_parent_uuid = uuid2bin(?) GROUP BY node_parent_uuid";
		st = c.prepareStatement(sql);
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


	public ResultSet getMysqlNode(Connection c, String nodeUuid, int userId,  int groupId) throws SQLException
	{
		PreparedStatement st;
		String sql;

		//try
		//{
		// On recupere d'abord les informations dans la table structures
		sql = "SELECT bin2uuid(node_uuid) as node_uuid, bin2uuid(node_parent_uuid) as node_parent_uuid,  node_children_uuid as node_children_uuid, node_order, metadata, metadata_wad, metadata_epm, bin2uuid(res_node_uuid) as res_node_uuid,  bin2uuid(res_res_node_uuid) as res_res_node_uuid,  bin2uuid(res_context_node_uuid) as res_context_node_uuid, shared_res, shared_node, shared_node_res,bin2uuid(shared_res_uuid) AS shared_res_uuid, bin2uuid(shared_node_uuid) AS shared_node_uuid, bin2uuid(shared_node_res_uuid) AS shared_node_res_uuid,asm_type, xsi_type, semtag, semantictag, label, code, descr, format, modif_user_id, modif_date,  bin2uuid(portfolio_id) as portfolio_id FROM node WHERE node_uuid = uuid2bin(?) ";
		st = c.prepareStatement(sql);
		st.setString(1, nodeUuid);

		// On doit vérifier le droit d'accès en lecture avant de retourner le noeud
		//if(!credential.getNodeRight(userId,groupId,nodeUuid,Credential.DELETE))
		//return null;
		//else
		return st.executeQuery();
	}

	public ResultSet getMysqlResource(Connection c, String nodeUuid)
	{
		PreparedStatement st;
		String sql;

		try
		{
			// On recupere d'abord les informations dans la table structures
			sql  = "SELECT bin2uuid(node_uuid) AS node_uuid, xsi_type, content, user_id, modif_user_id, modif_date FROM resource_table WHERE node_uuid = uuid2bin(?) ";

			st = c.prepareStatement(sql);
			st.setString(1, nodeUuid);

			return st.executeQuery();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		return null;
	}

	public String[] getMysqlResourceByNodeParentUuid(Connection c, String nodeParentUuid)
	{
		PreparedStatement st;
		String sql;

		String [] data = null;

		try
		{
			// On recupere d'abord les informations dans la table structures
			sql  = "SELECT bin2uuid(r.node_uuid) AS node_uuid, r.xsi_type, r.content, r.user_id, r.modif_user_id, r.modif_date " +
					"FROM resource_table r, node n " +
					"WHERE r.node_uuid=n.res_node_uuid AND " +
					"n.node_uuid = uuid2bin(?)";
			st = c.prepareStatement(sql);
			st.setString(1, nodeParentUuid);

			ResultSet res = st.executeQuery();
			if( res.next() )
			{
				data = new String[2];
				data[0] = res.getString("node_uuid");
				data[1] = res.getString("content");
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		return data;
	}

	public ResultSet getMysqlResources(Connection c, String portfolioUuid) throws SQLException
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
		st = c.prepareStatement(sql);
		st.setString(1, portfolioUuid);

		return st.executeQuery();
	}

	public ResultSet getMysqlPortfolios(Connection c, Integer userId, int substid, Boolean portfolioActive)
	{
		if( userId == null && substid == 0 ) return null;
		PreparedStatement st;
		String sql = "";

		try
		{
			// Ordering by code. A bit hackish but it work as intended
			// Si on est admin, on récupère la liste complête
			if( cred.isAdmin(c, userId) )
			{
				sql = "SELECT bin2uuid(p.portfolio_id) AS portfolio_id, bin2uuid(p.root_node_uuid) AS root_node_uuid, p.modif_user_id, p.modif_date, p.active, p.user_id " +
						"FROM portfolio p, node n, resource_table r " +
						"WHERE p.root_node_uuid=n.node_uuid " +
						"AND n.res_res_node_uuid=r.node_uuid ";
				if(portfolioActive) sql += "  AND active = 1 "; else sql += "  AND active = 0 ";

				if( "mysql".equals(dbserveur) )
					sql += " ORDER BY content";
				if( "oracle".equals(dbserveur) )
					sql += " ORDER BY dbms_lob.substr(content, 0, 4000)";

				st = c.prepareStatement(sql);
				return st.executeQuery();
			}

			if( cred.isAdmin(c, substid) )	// If root wants to debug user UI
				substid = 0;

			// On recupere d'abord les informations dans la table structures

			/// XXX Dammit Oracle, why are you so useless?
			/// FIXME: Might be a problem with Oracle if root ressource is > 4000 characters.
			///   If that happens, will have to rewrite the whole query
			/// Top level query so we can sort it by code
			sql = "SELECT portfolio_id, root_node_uuid, modif_user_id, modif_date, active, user_id, content " +
					"FROM (";
//			sql = "";

			/// Portfolio that user own, and those that he can modify
			if( "mysql".equals(dbserveur) )
				sql += "SELECT bin2uuid(p.portfolio_id) AS portfolio_id, bin2uuid(p.root_node_uuid) as root_node_uuid, p.modif_user_id, p.modif_date, p.active, p.user_id, r.content ";
			else if( "oracle".equals(dbserveur) )
				sql += "SELECT bin2uuid(p.portfolio_id) AS portfolio_id, bin2uuid(p.root_node_uuid) as root_node_uuid, p.modif_user_id, p.modif_date, p.active, p.user_id, TO_CHAR(r.content) AS content ";
			sql += "FROM portfolio p, node n, resource_table r " +
					"WHERE p.root_node_uuid=n.node_uuid AND n.res_res_node_uuid=r.node_uuid ";
			sql += "AND (p.modif_user_id = ?)";	// Param 1
			if(portfolioActive) sql += "AND p.active = 1 "; else sql += "AND p.active = 0 ";

			sql += "UNION ALL ";
			if( substid != 0 )
			{
				// Cross between portfolio that current user can access and those coming from the substitution
				if( "mysql".equals(dbserveur) )
					sql += "SELECT bin2uuid(p.portfolio_id) AS portfolio_id, bin2uuid(p.root_node_uuid) AS root_node_uuid, p.modif_user_id, p.modif_date, p.active, p.user_id, r.content ";
				else if( "oracle".equals(dbserveur) )
					sql += "SELECT bin2uuid(p.portfolio_id) AS portfolio_id, bin2uuid(p.root_node_uuid) AS root_node_uuid, p.modif_user_id, p.modif_date, p.active, p.user_id, TO_CHAR(r.content) AS content ";
				sql += "FROM node n, resource_table r, group_user gu " +
						"LEFT JOIN group_info gi ON gu.gid=gi.gid " +
						"LEFT JOIN group_right_info gri ON gri.grid=gi.grid " +
						"LEFT JOIN portfolio p ON gri.portfolio_id=p.portfolio_id, " +
						"group_user gu2 " +
						"LEFT JOIN group_info gi2 ON gu2.gid=gi2.gid " +
						"LEFT JOIN group_right_info gri2 ON gri2.grid=gi2.grid " +
						"LEFT JOIN portfolio p2 ON gri2.portfolio_id=p2.portfolio_id " +
						"WHERE p.root_node_uuid=n.node_uuid AND n.res_res_node_uuid=r.node_uuid AND " +
						"p.portfolio_id=p2.portfolio_id AND gu.userid=? AND gu2.userid=? ";	// Param 2,3
			}
			// Portfolio we have received some specific rights to it
			else
			{
				if( "mysql".equals(dbserveur) )
					sql += "SELECT bin2uuid(p.portfolio_id) AS portfolio_id, bin2uuid(p.root_node_uuid) AS root_node_uuid, p.modif_user_id, p.modif_date, p.active, p.user_id, r.content ";
				else if( "oracle".equals(dbserveur) )
					sql += "SELECT bin2uuid(p.portfolio_id) AS portfolio_id, bin2uuid(p.root_node_uuid) AS root_node_uuid, p.modif_user_id, p.modif_date, p.active, p.user_id, TO_CHAR(r.content) AS content ";
				sql += "FROM node n, resource_table r, group_user gu " +
						"LEFT JOIN group_info gi ON gu.gid=gi.gid " +
						"LEFT JOIN group_right_info gri ON gri.grid=gi.grid " +
						"LEFT JOIN portfolio p ON gri.portfolio_id=p.portfolio_id " +
						"WHERE p.root_node_uuid=n.node_uuid AND n.res_res_node_uuid=r.node_uuid " +
						"AND gu.userid=? ";	// Param 2
			}

			if(portfolioActive) sql += "  AND p.active = 1 "; else sql += "  AND p.active = 0 ";
			/// FIXME might need to check active from substitute too

			//						  sql += " GROUP BY portfolio_id,root_node_uuid,modif_user_id,modif_date,active, user_id ";
			//sql += " ORDER BY modif_date ASC ";
			/// Closing top level query and sorting
			if( "mysql".equals(dbserveur) )
				sql += ") t GROUP BY portfolio_id ORDER BY content";
			else if( "oracle".equals(dbserveur) )
				sql += ") t GROUP BY portfolio_id, root_node_uuid, modif_user_id, modif_date, active, user_id, content ORDER BY content";

			st = c.prepareStatement(sql);
			st.setInt(1, userId);	// From ownership
			if( substid == 0 )
			{
				st.setInt(2, userId);	// From specific rights given
			}
			else
			{
				st.setInt(2, substid);	// Portfolio that substitution and
				st.setInt(3, userId);	// current user can acess 
			}
			//			  if(userId!=null) st.setInt(3, userId);

			return st.executeQuery();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		return null;
	}

	public ResultSet getMysqlUserGroups(Connection c, Integer userId)
	{
		PreparedStatement st;
		String sql;

		try
		{
			// On recupere d'abord les informations dans la table structures

			sql = "SELECT * FROM group_user gu, credential cr, group_info gi  WHERE gu.userid=cr.userid  AND gi.gid=gu.gid ";
			if(userId!=null) sql += "  AND cr.userid = ? ";
			sql += " ORDER BY label ASC ";
			st = c.prepareStatement(sql);
			if(userId!=null) st.setInt(1, userId);

			return st.executeQuery();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		return null;
	}

	public ResultSet getMysqlUsers(Connection c, Integer userId)
	{
		PreparedStatement st;
		String sql;

		try
		{
			// On recupere d'abord les informations dans la table structures

			sql = "SELECT * FROM credential c " +
					"LEFT JOIN credential_substitution cs " +
					"ON c.userid=cs.userid " +
					"ORDER BY c.userid";
			//if(userId!=null) sql += "  AND cr.userid = ? ";
			//sql += " ORDER BY display_name ASC ";
			st = c.prepareStatement(sql);
			// if(userId!=null) st.setInt(1, userId);

			return st.executeQuery();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		return null;
	}

	public ResultSet getMysqlGroupRights(Connection c, Integer userId, Integer groupId)
	{
		PreparedStatement st;
		String sql;

		try
		{
			// On recupere d'abord les informations dans la table structures

			sql = "SELECT bin2uuid(id) as id,RD,WR,DL,SB,AD,types_id,gid,gr.grid,gi.owner,gi.label FROM group_rights gr, group_info gi WHERE  gr.grid = gi.grid AND gi.gid = ?";
			//if(userId!=null) sql += "  AND cr.userid = ? ";
			//sql += " ORDER BY display_name ASC ";
			st = c.prepareStatement(sql);
			st.setInt(1, groupId);

			return st.executeQuery();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		return null;
	}

	private ResultSet getMysqlNodeResultset(Connection c, String nodeUuid)
	{
		PreparedStatement st;
		String sql;

		try
		{
			sql = "SELECT bin2uuid(node_uuid) as node_uuid, bin2uuid(node_parent_uuid) as node_parent_uuid,  node_children_uuid as node_children_uuid, node_order, metadata, metadata_wad, metadata_epm, bin2uuid(res_node_uuid) as res_node_uuid,  bin2uuid(res_res_node_uuid) as res_res_node_uuid,  bin2uuid(res_context_node_uuid) as res_context_node_uuid, shared_res, shared_node, asm_type, xsi_type, semtag, label, code, descr, format, modif_user_id, modif_date,  bin2uuid(portfolio_id) as portfolio_id FROM node WHERE node_uuid = uuid2bin(?) ";
			st = c.prepareStatement(sql);
			st.setString(1, nodeUuid);
			return st.executeQuery();
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			return null;
		}
	}

	private ResultSet getMysqlPortfolioResultset(Connection c, String portfolioUuid)
	{
		PreparedStatement st;
		String sql;

		try
		{
			sql = "SELECT bin2uuid(portfolio_id) AS portfolio_id,bin2uuid(model_id) AS model_id,bin2uuid(root_node_uuid) as root_node_uuid,modif_user_id,modif_date,active user_id FROM portfolio WHERE portfolio_id = uuid2bin(?) ";
			st = c.prepareStatement(sql);
			st.setString(1, portfolioUuid);
			return st.executeQuery();
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			return null;
		}
	}


	private int insertMysqlPortfolio(Connection c, String portfolioUuid,String rootNodeUuid,int modelId,int userId)
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
			st = c.prepareStatement(sql);
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

	private int updateMysqlPortfolioActive(Connection c, String portfolioUuid, Boolean active)
	{
		String sql = "";
		PreparedStatement st;

		try
		{
			sql  = "UPDATE  portfolio SET active = ?  WHERE portfolio_id = uuid2bin(?) ";

			Integer iActive = (active) ? 1 : 0;

			st = c.prepareStatement(sql);

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

	private int updateMysqlPortfolioModelId(Connection c, String portfolioUuid, String portfolioModelId)
	{
		String sql = "";
		PreparedStatement st;

		try
		{
			sql  = "UPDATE  portfolio SET model_id = uuid2bin(?)  WHERE portfolio_id = uuid2bin(?) ";
			st = c.prepareStatement(sql);

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

	private int deleteMySqlPortfolio(Connection c, String portfolioUuid, int userId, int groupId) throws SQLException
	{
		String sql = "";
		PreparedStatement st;
		int status = 0;
		boolean hasRights = false;

		NodeRight right = cred.getPortfolioRight(c, userId, groupId, portfolioUuid, Credential.DELETE);
		if( right.delete || cred.isAdmin(c, userId) )
			hasRights = true;

		if(hasRights)
		{
			/// Si il y a quelque chose de particulier, on s'assure que tout soit bien nettoyé de façon séparé
			try
			{
				c.setAutoCommit(false);

				/// Portfolio
				sql = "DELETE FROM portfolio WHERE portfolio_id=uuid2bin(?)";
				st = c.prepareStatement(sql);
				st.setString(1, portfolioUuid);
				st.executeUpdate();

				/// Nodes
				sql = "DELETE FROM node WHERE portfolio_id=uuid2bin(?)";	/// On garde les resources, c'est ce qu'il faut?
				st = c.prepareStatement(sql);
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
				st = c.prepareStatement(sql);
				st.setString(1, portfolioUuid);
				st.executeUpdate();
			}
			catch( Exception e )
			{
				try{ c.rollback(); }
				catch( SQLException e1 ){ e1.printStackTrace(); }
				e.printStackTrace();
			}
			finally
			{
				c.commit();
				c.setAutoCommit(true);
				c.close();
				status = 1;
			}
		}
		return status;
	}

	private int deleteMySqlNode(Connection c, String nodeUuid, String nodeParentUuid,int userId, int groupId) throws SQLException
	{
		String sql = "";
		PreparedStatement st;

		if(cred.hasNodeRight(c, userId,groupId,nodeUuid,Credential.DELETE))
		{
			sql  = " DELETE FROM node WHERE node_uuid=uuid2bin(?) ";
			st = c.prepareStatement(sql);
			st.setString(1, nodeUuid);
			Integer nbDeletedNodes = st.executeUpdate();

			// On met à jour les enfants du parent
			updateMysqlNodeChildren(c, nodeParentUuid);

			return nbDeletedNodes;
		}
		return 0;
	}

	/*
	 *  Ecrit le noeud dans la base MySQL
	 */
	private int insertMySqlNode(Connection c, String nodeUuid,String nodeParentUuid,String nodeChildrenUuid,
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
				nodeChildrenUuid = getMysqlNodeResultset(c, nodeUuid).getString("node_children_uuid");
			}
		}
		catch(Exception ex)
		{

		}

		/// Because Oracle can't do its work properly
		if( "".equals(semanticTag) ) semanticTag = null;
		if( "".equals(nodeChildrenUuid) ) nodeChildrenUuid = null;
		if( "".equals(xsiType) ) xsiType = null;
		if( "".equals(code) ) code = null;

		try
		{
			if (dbserveur.equals("mysql")){
				sql  = "REPLACE INTO node(node_uuid,node_parent_uuid,node_children_uuid,node_order,";
				sql += "asm_type,xsi_type,shared_res,shared_node,shared_node_res,shared_res_uuid,shared_node_uuid,shared_node_res_uuid, metadata,metadata_wad,metadata_epm,semtag,semantictag,label,code,descr,format,modif_user_id,modif_date,portfolio_id) ";
				sql += "VALUES(uuid2bin(?),uuid2bin(?),?,?,?,?,?,?,?,uuid2bin(?),uuid2bin(?),uuid2bin(?),?,?,?,?,?,?,?,?,?,?,?,uuid2bin(?))";
			} else if (dbserveur.equals("oracle")){
				sql = "MERGE INTO node d USING (SELECT uuid2bin(?) node_uuid,uuid2bin(?) node_parent_uuid,? node_children_uuid,? node_order,? asm_type,? xsi_type,? shared_res,? shared_node,? shared_node_res,uuid2bin(?) shared_res_uuid,uuid2bin(?) shared_node_uuid,uuid2bin(?) shared_node_res_uuid,? metadata,? metadata_wad,? metadata_epm,? semtag,? semantictag,? label,? code,? descr,? format,? modif_user_id,? modif_date,uuid2bin(?) portfolio_id FROM DUAL) s ON (d.node_uuid = s.node_uuid) WHEN MATCHED THEN UPDATE SET d.node_parent_uuid=s.node_parent_uuid,d.node_children_uuid=s.node_children_uuid,d.node_order=s.node_order,d.asm_type=s.asm_type,d.xsi_type=s.xsi_type,d.shared_res=s.shared_res,d.shared_node=s.shared_node,d.shared_node_res=s.shared_node_res,d.shared_res_uuid=s.shared_res_uuid,d.shared_node_uuid=s.shared_node_uuid,d.shared_node_res_uuid=s.shared_node_res_uuid,d.metadata=s.metadata,d.metadata_wad=s.metadata_wad,d.metadata_epm=s.metadata_epm,d.semtag=s.semtag,d.semantictag=s.semantictag,d.label=s.label,d.code=s.code,d.descr=s.descr,d.format=s.format,d.modif_user_id=s.modif_user_id,d.modif_date=s.modif_date,d.portfolio_id=s.portfolio_id WHEN NOT MATCHED THEN INSERT (d.node_uuid,d.node_parent_uuid,d.node_children_uuid,d.node_order,d.asm_type,d.xsi_type,d.shared_res,d.shared_node,d.shared_node_res,d.shared_res_uuid,d.shared_node_uuid,d.shared_node_res_uuid,d.metadata,d.metadata_wad,d.metadata_epm,d.semtag,d.semantictag,d.label,d.code,d.descr,d.format,d.modif_user_id,d.modif_date,d.portfolio_id) VALUES (s.node_uuid,s.node_parent_uuid,s.node_children_uuid,s.node_order,s.asm_type,s.xsi_type,s.shared_res,s.shared_node,s.shared_node_res,s.shared_res_uuid,s.shared_node_uuid,s.shared_node_res_uuid,s.metadata,s.metadata_wad,s.metadata_epm,s.semtag,s.semantictag,s.label,s.code,s.descr,s.format,s.modif_user_id,s.modif_date,s.portfolio_id)";
			}
			st = c.prepareStatement(sql);
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

	private int updatetMySqlNode(Connection c, String nodeUuid, String asmType,String xsiType,String semantictag, String label, String code, String descr,String format, String metadata,String metadataWad,String metadataEpm, int sharedRes, int sharedNode, int sharedNodeRes,int modifUserId) throws Exception
	{
		String sql = "";
		PreparedStatement st;

		sql  = "UPDATE node SET ";
		sql += "asm_type = ?,xsi_type = ?,semantictag = ?,label = ?,code = ?,descr = ?,format = ? ,metadata = ?,metadata_wad = ?, metadata_epm = ?,shared_res = ?,shared_node = ?,shared_node_res = ?, modif_user_id = ?,modif_date = ? ";
		sql += " WHERE node_uuid = uuid2bin(?) ";
		st = c.prepareStatement(sql);

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
		int val = st.executeUpdate();
		st.close();

		return val;
	}

	private int updatetMySqlNodeOrder(Connection c, String nodeUuid, int order) throws Exception
	{
		String sql = "";
		PreparedStatement st=null;

		sql  = "UPDATE node SET ";
		sql += " node_order = ? ";
		sql += " WHERE node_uuid = uuid2bin(?) ";
		st = c.prepareStatement(sql);

		st.setInt(1, order);
		st.setString(2, nodeUuid);
		int val = st.executeUpdate();

		return val;
	}

	/*
	 *  Ecrit le noeud dans la base MySQL
	 */
	private int updateMysqlNodeChildren(Connection c, String nodeUuid)
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
			if (dbserveur.equals("oracle"))
			{
				sql = "UPDATE node n1 SET n1.node_order=(" +
						"SELECT (n2.rnum-1) FROM (" +
						"SELECT node_uuid, row_number() OVER (ORDER BY node_order ASC) rnum, node_parent_uuid " +
						"FROM node WHERE node_parent_uuid=uuid2bin(?)) n2 " +
						"WHERE n1.node_uuid= n2.node_uuid) " +
						"WHERE n1.node_parent_uuid=uuid2bin(?)";
			}
			st = c.prepareStatement(sql);
			st.setString(1, nodeUuid);
			if (dbserveur.equals("oracle")){
				st.setString(2, nodeUuid);
			}
			st.executeUpdate();
			st.close();

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
			st = c.prepareStatement(sql);
			st.setString(1, nodeUuid);
			st.setString(2, nodeUuid);
			st.executeUpdate();
			int changes = st.getUpdateCount();
			st.close();

			if( changes == 0 )	// Specific case when there's no children left in parent
			{
				sql = "UPDATE node n "
						+ "SET n.node_children_uuid=NULL "
						+ "WHERE n.node_uuid=uuid2bin(?)";
				st = c.prepareStatement(sql);
				st.setString(1, nodeUuid);
				st.executeUpdate();
				changes = st.getUpdateCount();
				st.close();
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

	private int insertMysqlResource(Connection c, String uuid,String parentUuid, String xsiType,String content,String portfolioModelId, int sharedNodeRes,int sharedRes, int userId)
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
				st = c.prepareStatement(sql);
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
				st = c.prepareStatement(sql);
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
				st = c.prepareStatement(sql);
				st.setString(1, uuid);
				st.setString(2,parentUuid);
			}
			else
			{
				sql = " UPDATE node SET res_node_uuid=uuid2bin(?), shared_res_uuid=uuid2bin(?) ";
				sql += " WHERE node_uuid = uuid2bin(?) ";
				st = c.prepareStatement(sql);
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

	private int updateMysqlResource(Connection c, String uuid,String xsiType, String content,int userId)
	{
		String sql = "";
		PreparedStatement st = null;

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
				st = c.prepareStatement(sql);
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

				st = c.prepareStatement(sql);

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
			logger.error(ex.getMessage());
			ex.printStackTrace();
			return -1;
		}
		finally
		{
			if( st != null )
			{
				try { st.close(); }
				catch( SQLException e ) { e.printStackTrace(); }
			}
		}
	}

	private int updateMysqlResourceByXsiType(Connection c, String nodeUuid, String xsiType,String content,int userId)
	{
		String sql = "";
		PreparedStatement st;

		try
		{
			if(xsiType.equals("nodeRes"))
			{
				sql = " UPDATE resource_table SET content=?,user_id=?,modif_user_id=?,modif_date=? ";
				sql += " WHERE node_uuid = (SELECT res_res_node_uuid FROM node ";
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
						st = c.prepareStatement(sq);
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
				sql += " WHERE node_uuid = (SELECT res_context_node_uuid FROM node ";
				sql += " WHERE node_uuid=uuid2bin(?))  ";
			}
			else
			{
				sql = " UPDATE resource_table SET content=?,user_id=?,modif_user_id=?,modif_date=? ";
				sql += " WHERE node_uuid = (SELECT res_node_uuid FROM node ";
				sql += " WHERE node_uuid=uuid2bin(?))  ";
			}
			st = c.prepareStatement(sql);
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

	private int deleteMySqlResource(Connection c, String resourceUuid, int userId, int groupId) throws SQLException
	{
		String sql = "";
		PreparedStatement st;

		if(cred.hasNodeRight(c, userId,groupId,resourceUuid,Credential.DELETE))
		{
			sql  = " DELETE FROM resource_table WHERE node_uuid=uuid2bin(?) ";
			st = c.prepareStatement(sql);
			st.setString(1, resourceUuid);
			return st.executeUpdate();
		}
		return 0;
	}

	@Override
	public Object putUser(Connection c, int userId,String oAuthToken, String oAuthSecret)
	{
		return insertMysqlUser(c, userId, oAuthToken,oAuthSecret);
	}

	@Override
	public Object getUserGroups(Connection c, int userId) throws Exception {
		ResultSet res = getMysqlUserGroups(c, userId);

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
	public boolean isUserMemberOfGroup(Connection c, int userId,int groupId)
	{
		return cred.isUserMemberOfGroup(c, userId,groupId);
	}

	@Override
	public Object getUser(Connection c, int userId)
	{
		try
		{
			return getMySqlUser(c, userId);
		}
		catch(Exception ex)
		{
			return null;
		}
	}

	public ResultSet getMySqlUser(Connection c, int userId) throws Exception
	{
		PreparedStatement st;
		String sql;
		ResultSet res;

		try
		{
			sql = "SELECT * FROM user WHERE user_id = ? ";
			st = c.prepareStatement(sql);
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
	private int insertMysqlUser(Connection c, int userId, String oAuthToken,String oAuthSecret)
	{
		String sql = "";
		PreparedStatement st;

		try
		{
			sql  = "REPLACE INTO user(user_id,oauth_token,oauth_secret) ";

			sql += "VALUES(?,?,?)";
			st = c.prepareStatement(sql);
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

	private boolean insertMySqlLog(Connection c, String url,String method,String headers,String inBody, String outBody, int code)
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
			st = c.prepareStatement(sql);
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

	public String getPortfolioRootNode(Connection c, String portfolioUuid) throws SQLException
	{
		PreparedStatement st;
		String sql;
		ResultSet res;

		sql = "SELECT bin2uuid(root_node_uuid) AS root_node_uuid FROM portfolio WHERE portfolio_id = uuid2bin(?) ";
		st = c.prepareStatement(sql);
		st.setString(1, portfolioUuid);
		res = st.executeQuery();
		res.next();
		String root_node = res.getString("root_node_uuid");

		if( st != null ) st.close();
		if( res != null ) res.close();

		return root_node;
	}

	public String getPortfolioModelUuid(Connection c, String portfolioUuid) throws SQLException
	{
		PreparedStatement st;
		String sql;
		ResultSet res;

		sql = "SELECT bin2uuid(model_id) AS model_id FROM portfolio WHERE portfolio_id = uuid2bin(?) ";
		st = c.prepareStatement(sql);
		st.setString(1, portfolioUuid);
		res = st.executeQuery();
		res.next();
		return res.getString("model_id");
	}



	public int getPortfolioUserId(Connection c, String portfolioUuid) throws SQLException
	{
		PreparedStatement st;
		String sql;
		ResultSet res;

		sql = "SELECT user_id FROM portfolio WHERE portfolio_id = uuid2bin(?) ";
		st = c.prepareStatement(sql);
		st.setString(1, portfolioUuid);
		res = st.executeQuery();
		res.next();
		return res.getInt("user_id");
	}

	public String getNodeParentUuidByNodeUuid(Connection c, String nodeUuid)
	{
		PreparedStatement st;
		String sql;
		ResultSet res;

		try
		{
			sql = "SELECT bin2uuid(node_parent_uuid) AS node_parent_uuid FROM node WHERE node_uuid = uuid2bin(?) ";
			st = c.prepareStatement(sql);
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

	public String getPortfolioUuidByPortfolioCode(Connection c, String portfolioCode)
	{
		PreparedStatement st;
		String sql;
		ResultSet res;

		try
		{
			sql = "SELECT bin2uuid(p.portfolio_id) AS portfolio_id " +
					"FROM portfolio p, node n " +
					"WHERE p.active=1 AND p.portfolio_id=n.portfolio_id AND n.code = ? AND n.asm_type='asmRoot'";
			st = c.prepareStatement(sql);
			st.setString(1, portfolioCode);
			res = st.executeQuery();
/*
			sql  = "SELECT bin2uuid(portfolio_id) AS portfolio_id FROM portfolio WHERE active=1 AND root_node_uuid IN (";
			sql += " SELECT node_uuid  FROM node WHERE asm_type='asmRoot' AND res_res_node_uuid ";
			sql += " IN ( SELECT node_uuid FROM resource_table WHERE content LIKE ? AND xsi_type='nodeRes') ) ";
			st = connection.prepareStatement(sql);
			st.setString(1, "%<code>"+portfolioCode+"</code>%");
			res = st.executeQuery();
//*/
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
	public String getResourceNodeUuidByParentNodeUuid(Connection c, String nodeParentUuid)
	{
		PreparedStatement st;
		String sql;
		ResultSet res;

		try
		{
			sql = "SELECT bin2uuid(res_node_uuid)AS res_node_uuid FROM node WHERE node_uuid = uuid2bin(?) ";
			st = c.prepareStatement(sql);
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

	public Integer getNodeOrderByNodeUuid(Connection c, String nodeUuid)
	{
		PreparedStatement st;
		String sql;
		ResultSet res;

		try
		{
			sql = "SELECT node_order FROM node WHERE node_uuid = uuid2bin(?) ";
			st = c.prepareStatement(sql);
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
	public String getPortfolioUuidByNodeUuid(Connection c, String nodeUuid) throws Exception
	{
		PreparedStatement st;
		String sql;
		ResultSet res;
		String result = null;

		sql = "SELECT bin2uuid(portfolio_id) AS portfolio_id FROM node WHERE node_uuid = uuid2bin(?) ";
		st = c.prepareStatement(sql);
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
	public Object getPortfolio(Connection c, MimeType outMimeType, String portfolioUuid, int userId, int groupId, String label, String resource, String files, int substid) throws Exception
	{
		long t1=0, t2=0, t3=0, t4=0, t5=0;
		long t0 = System.currentTimeMillis();
		
		String rootNodeUuid = getPortfolioRootNode(c, portfolioUuid);
		String header = "";
		String footer = "";
		NodeRight nodeRight = cred.getPortfolioRight(c, userId,groupId, portfolioUuid, Credential.READ);
		if(!nodeRight.read)
		{
			userId = cred.getPublicUid(c);
//			NodeRight nodeRight = new NodeRight(false,false,false,false,false,false);
			/// Vérifie les droits avec le compte publique (dernière chance)
			nodeRight = cred.getPublicRight(c, userId, 123, rootNodeUuid, "dummy");
			if( !nodeRight.read )
				return "faux";
		}
		
		t1 = System.currentTimeMillis();

		if(outMimeType.getSubType().equals("xml"))
		{
			//			header = "<portfolio xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' schemaVersion='1.0'>";
			//			footer = "</portfolio>";
			/*
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
			//*/
			int owner = cred.getOwner(c, userId, portfolioUuid);
			String isOwner = "N";
			if( owner == userId )
				isOwner = "Y";
//			root.setAttribute("owner", isOwner);

			String headerXML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><portfolio code=\"0\" id=\""+portfolioUuid+"\" owner=\""+isOwner+"\"><version>4</version>";
			//          root.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
			//          root.setAttribute("schemaVersion", "1.0");
//			document.appendChild(root);

			t2 = System.currentTimeMillis();
			 
			String data = getLinearXml(c, portfolioUuid, rootNodeUuid, null, true, null, userId, nodeRight.rrgId, nodeRight.groupLabel);

			t3 = System.currentTimeMillis();
			
			StringWriter stw = new StringWriter();
			stw.append(headerXML+data+"</portfolio>");

			t4 = System.currentTimeMillis();
			
			/*
			Transformer serializer = TransformerFactory.newInstance().newTransformer();
			serializer.transform(new DOMSource(document), new StreamResult(stw));
			//*/

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

			t5 = System.currentTimeMillis();
			
			/*
			long checkRights = t1-t0;
			long initStuff = t2-t1;
			long getData = t3-t2;
			long appendData = t4-t3;
			long zipRelated = t5-t4;
			System.out.println("=====Get Portfolio=====");
			System.out.println("Check rights: "+checkRights);
			System.out.println("Init stuff: "+initStuff);
			System.out.println("Get data: "+getData);
			System.out.println("Append data: "+appendData);
			System.out.println("Zip related: "+zipRelated);
			//*/
			
			return stw.toString();
		}
		else if(outMimeType.getSubType().equals("json"))
		{
			header = "{\"portfolio\": { \"-xmlns:xsi\": \"http://www.w3.org/2001/XMLSchema-instance\",\"-schemaVersion\": \"1.0\",";
			footer = "}}";
		}

		System.out.println("AM I EVEN GOING HERE?");
		
		return header+getNode(c, outMimeType, rootNodeUuid,true, userId, groupId, label).toString()+footer;
	}

	@Override
	public Object getPortfolioByCode(Connection c, MimeType mimeType, String portfolioCode, int userId, int groupId, String resources, int substid) throws Exception
	{
		//return this.getPortfolio(mimeType, this.getPortfolioUuidByPortfolioCode(portfolioCode), userId, groupId, null);

		PreparedStatement st = null;
		String sql;
		ResultSet res = null;
		String pid = this.getPortfolioUuidByPortfolioCode(c, portfolioCode);
		Boolean withResources = false;
		String result = "";

		try
		{
			withResources = Boolean.parseBoolean(resources);
		}
		catch(Exception ex) {}

		if(withResources)
		{
			return this.getPortfolio(c, new MimeType("text/xml"),pid,userId, groupId, null, null, null, substid).toString();
		}
		else
		{
			try
			{
				sql = "SELECT  bin2uuid(portfolio_id) AS portfolio_id,bin2uuid(root_node_uuid) as root_node_uuid, modif_user_id,modif_date, active, user_id "
						+ "FROM portfolio "
						+ "WHERE portfolio_id = uuid2bin(?) ";
				st = c.prepareStatement(sql);
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
				result += getNodeXmlOutput(c, res.getString("root_node_uuid"), false, "nodeRes", userId,  groupId, null,false);
				result += "</portfolio>";
			}
		}

		return result;
	}

	@Override
	public Object getPortfolios(Connection c, MimeType outMimeType, int userId, int groupId, Boolean portfolioActive, int substid) throws SQLException
	{
		PreparedStatement st = null;
		ResultSet res = null;
		
		String sql = "";
		if( cred.isAdmin(c, userId) )
		{
			sql = "SELECT bin2uuid(p.root_node_uuid) as root_node_uuid, p.modif_date, bin2uuid(n.node_uuid) as node_uuid, bin2uuid(n.node_parent_uuid) as node_parent_uuid, n.node_children_uuid as node_children_uuid, n.node_order, n.metadata, n.metadata_wad, n.metadata_epm, bin2uuid(n.res_node_uuid) as res_node_uuid,  bin2uuid(n.res_res_node_uuid) as res_res_node_uuid, bin2uuid(n.res_context_node_uuid) as res_context_node_uuid, n.shared_res, n.shared_node, n.shared_node_res, bin2uuid(n.shared_res_uuid) AS shared_res_uuid, bin2uuid(n.shared_node_uuid) AS shared_node_uuid, bin2uuid(n.shared_node_res_uuid) AS shared_node_res_uuid, n.asm_type, n.xsi_type, n.semtag, n.semantictag, n.label, n.code, n.descr, n.format, n.modif_user_id, n.modif_date, bin2uuid(n.portfolio_id) as portfolio_id, r1.content, r1.xsi_type, r2.content, r2.xsi_type, r3.content, r3.xsi_type " +
					"FROM portfolio p, node n " +
					"LEFT JOIN resource_table r1 ON n.res_res_node_uuid=r1.node_uuid " +
					"LEFT JOIN resource_table r2 ON n.res_context_node_uuid=r2.node_uuid " +
					"LEFT JOIN resource_table r3 ON n.res_node_uuid=r3.node_uuid " +
					"WHERE p.root_node_uuid=n.node_uuid ";
			if( portfolioActive ) sql += "AND p.active=1 ";
			else sql += "AND p.active=0 ";
			sql += "ORDER BY r1.content;";
			
			st = c.prepareStatement(sql);
			res = st.executeQuery();
		}
		else
		{
			sql = "SELECT bin2uuid(p.root_node_uuid) as root_node_uuid, p.modif_date, bin2uuid(n.node_uuid) as node_uuid, bin2uuid(n.node_parent_uuid) as node_parent_uuid, n.node_children_uuid as node_children_uuid, n.node_order, n.metadata, n.metadata_wad, n.metadata_epm, bin2uuid(n.res_node_uuid) as res_node_uuid,  bin2uuid(n.res_res_node_uuid) as res_res_node_uuid, bin2uuid(n.res_context_node_uuid) as res_context_node_uuid, n.shared_res, n.shared_node, n.shared_node_res, bin2uuid(n.shared_res_uuid) AS shared_res_uuid, bin2uuid(n.shared_node_uuid) AS shared_node_uuid, bin2uuid(n.shared_node_res_uuid) AS shared_node_res_uuid, n.asm_type, n.xsi_type, n.semtag, n.semantictag, n.label, n.code, n.descr, n.format, n.modif_user_id, n.modif_date, bin2uuid(n.portfolio_id) as portfolio_id, r1.content, r1.xsi_type, r2.content, r2.xsi_type, r3.content, r3.xsi_type " +
					"FROM portfolio p, group_right_info gri, group_info gi, group_user gu, node n " +
					"LEFT JOIN resource_table r1 ON n.res_res_node_uuid=r1.node_uuid " +
					"LEFT JOIN resource_table r2 ON n.res_context_node_uuid=r2.node_uuid " +
					"LEFT JOIN resource_table r3 ON n.res_node_uuid=r3.node_uuid " +
					"WHERE p.portfolio_id=gri.portfolio_id AND gri.grid=gi.grid AND gi.gid=gu.gid AND p.root_node_uuid=n.node_uuid AND " +
					"(gu.userid=? OR p.modif_user_id=?) ";
			if( portfolioActive ) sql += "AND p.active=1 ";
			else sql += "AND p.active=0 ";
			sql += "ORDER BY r1.content;";
			
			st = c.prepareStatement(sql);
			st.setInt(1, userId);
			st.setInt(2, userId);
			res = st.executeQuery();
		}
		
		StringBuilder out = new StringBuilder();
		if(outMimeType.getSubType().equals("xml"))
		{
			out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><portfolios>");
			while(res.next())
			{
				String isOwner = "N";
				String ownerId = res.getString("modif_user_id");
				if( Integer.parseInt(ownerId) == userId )
					isOwner = "Y";
				
				out.append("<portfolio id=\"").append(res.getString("portfolio_id"));
				out.append("\" root_node_id=\"").append(res.getString("root_node_uuid"));
				out.append("\" owner=\"").append(isOwner);
				out.append("\" ownerid=\"").append(ownerId);
				out.append("\" modified=\"").append(res.getString("p.modif_date")).append("\">");
				
				String nodeUuid = res.getString("root_node_uuid");
				
				if(res.getString("shared_node_uuid")!=null)	// FIXME, add to query
				{
					out.append(getNodeXmlOutput(c, res.getString("shared_node_uuid"),true,null,userId,groupId, null,true));
				}
				else
				{
					String nodetype = res.getString("asm_type");
					out.append("<").append(nodetype).append(" id=\"").append(res.getString("node_uuid")).append("\">");
					
					if(!"asmResource".equals(nodetype))
					{
						String metawad = res.getString("metadata_wad");
						if(metawad!=null && !"".equals(metawad) )
						{
							out.append("<metadata-wad ").append(metawad).append("/>");
						}
						else
							out.append("<metadata-wad/>");
						
						String metaepm = res.getString("metadata_epm");
						if(metaepm!=null && !"".equals(metaepm) )
							out.append("<metadata-epm "+metaepm+"/>");
						else
							out.append("<metadata-epm/>");
						
						String meta = res.getString("metadata");
						if(meta!=null && !"".equals(meta))
							out.append("<metadata "+meta+"/>");
						else
							out.append("<metadata/>");
						
						String code = res.getString("code");
						if(meta!=null && !"".equals(meta))
							out.append("<code>").append(code).append("</code>");
						else
							out.append("<code/>");
						
						String label = res.getString("label");
						if(label!=null && !"".equals(label))
							out.append("<label>").append(label).append("</label>");
						else
							out.append("<label/>");
							
						String descr = res.getString("descr");
						if(descr!=null && !"".equals(descr))
							out.append("<description>").append(descr).append("</description>");
						else
							out.append("<description/>");
						
						String semantic = res.getString("semantictag");
						if(semantic!=null && !"".equals(semantic))
							out.append("<semanticTag>").append(semantic).append("</semanticTag>");
						else
							out.append("<semanticTag/>");
					}
					
					String resresuuid = res.getString("res_res_node_uuid");
					if( resresuuid != null && !"".equals(resresuuid) )
					{
						String xsitype = res.getString("r1.xsi_type");
						out.append("<asmResource id='").append(resresuuid).append("' contextid='").append(nodeUuid).append("' xsi_type='").append(xsitype).append("'>");
						String resrescont = res.getString("r1.content");
						if( resrescont != null && !"".equals(resrescont) )
							out.append(resrescont);
						out.append("</asmResource>");
					}
					
					String rescontuuid = res.getString("res_context_node_uuid");
					if( rescontuuid != null && !"".equals(rescontuuid) )
					{
						String xsitype = res.getString("r2.xsi_type");
						out.append("<asmResource id='").append(rescontuuid).append("' contextid='").append(nodeUuid).append("' xsi_type='").append(xsitype).append("'>");
						String resrescont = res.getString("r2.content");
						if( resrescont != null && !"".equals(resrescont) )
							out.append(resrescont);
						out.append("</asmResource>");
					}
					
					String resnodeuuid = res.getString("res_node_uuid");
					if( resnodeuuid != null && !"".equals(resnodeuuid) )
					{
						String xsitype = res.getString("r3.xsi_type");
						out.append("<asmResource id='").append(resnodeuuid).append("' contextid='").append(nodeUuid).append("' xsi_type='").append(xsitype).append("'>");
						String resrescont = res.getString("r3.content");
						if( resrescont != null && !"".equals(resrescont) )
							out.append(resrescont);
						out.append("</asmResource>");
					}
					out.append("</"+nodetype+">");
					out.append("</portfolio>");
				}
			}
			out.append("</portfolios>");
		}
		else if(outMimeType.getSubType().equals("json"))
		{
			String result = "";
			result = "{ \"portfolios\": { \"portfolio\": [";
			boolean firstPass = false;
			while(res.next())
			{
				if(firstPass) result += ",";
				result += "{ ";
				result += DomUtils.getJsonAttributeOutput("id", res.getString("portfolio_id"))+", ";
				result += DomUtils.getJsonAttributeOutput("root_node_id", res.getString("root_node_uuid"))+", ";
				result += getNodeJsonOutput(c, res.getString("root_node_uuid"), false, "nodeRes", userId,  groupId,null,false);
				result += "} ";
				firstPass = true;
			}
			result += "] } }";
		}
		res.close();
		st.close();

		return out.toString();
	}

	@Override
	public Object getNodeBySemanticTag(Connection c, MimeType outMimeType, String portfolioUuid, String semantictag, int userId, int groupId) throws Exception
	{
		ResultSet res;
		String nodeUuid;

		// On recupere d'abord l'uuid du premier noeud trouvé correspondant au semantictag
		res = this.getMysqlNodeUuidBySemanticTag(c, portfolioUuid, semantictag);
		res.next();
		nodeUuid = res.getString("node_uuid");

		if(!cred.hasNodeRight(c, userId,groupId,nodeUuid, Credential.READ))
			return null;

		if(outMimeType.getSubType().equals("xml"))
			return getNodeXmlOutput(c, nodeUuid,true,null,userId, groupId, null,true);
		else if(outMimeType.getSubType().equals("json"))
			return "{"+getNodeJsonOutput(c, nodeUuid,true,null,userId, groupId,null,true)+"}";
		else
			return null;
	}

	@Override
	public Object getNodesBySemanticTag(Connection c, MimeType outMimeType, int userId,int groupId, String portfolioUuid, String semanticTag) throws SQLException
	{
		ResultSet res = this.getMysqlNodeUuidBySemanticTag(c, portfolioUuid, semanticTag);
		String result = "";
		if(outMimeType.getSubType().equals("xml"))
		{
			result = "<nodes>";
			while(res.next())
			{
				String nodeUuid = res.getString("node_uuid");
				if(!cred.hasNodeRight(c, userId,groupId,nodeUuid, Credential.READ))
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
	public Object postPortfolio(Connection c, MimeType inMimeType,MimeType outMimeType,String in,  int userId, int groupId, String portfolioModelId, int substid, boolean parseRights) throws Exception
	{
		if(!cred.isAdmin(c, userId) && !cred.isCreator(c, userId) )
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		StringBuffer outTrace = new StringBuffer();
		String portfolioUuid;

		// Si le modele est renseigné, on ignore le XML posté et on récupere le contenu du modele
		// à la place
		if(portfolioModelId!=null)
			in = getPortfolio(c, inMimeType,portfolioModelId,userId, groupId, null, null, null, substid).toString();

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
//			String filterRes = "//asmRoot/asmResource/code";
			String filterRes = "//*[local-name()='asmRoot']/*[local-name()='asmResource']/*[local-name()='code']";
			NodeList nodelist = (NodeList) xPath.compile(filterRes).evaluate(doc, XPathConstants.NODESET);

			if( nodelist.getLength() > 0 )
			{
				String code = nodelist.item(0).getTextContent();
				// Simple query
				if( isCodeExist(c, code) )
					throw new RestWebApplicationException(Status.CONFLICT, "Existing code.");
			}


			Node rootNode = (doc.getElementsByTagName("portfolio")).item(0);
			if(rootNode==null)
				throw new Exception("Root Node (portfolio) not found !");
			else
			{
				rootNode = (doc.getElementsByTagName("asmRoot")).item(0);

				String uuid = UUID.randomUUID().toString();

				insertMysqlPortfolio(c, portfolioUuid,uuid,0,userId);

				writeNode(c, rootNode, portfolioUuid, portfolioModelId, userId,0, uuid,null,0,0,false, null, parseRights);
			}
		}
		updateMysqlPortfolioActive(c, portfolioUuid,true);

		updateMysqlPortfolioModelId(c, portfolioUuid,portfolioModelId);

		/// If we instanciate, don't need the designer role
//		if( !parseRights )
		{
			/// Créer groupe 'designer', 'all' est mis avec ce qui est spécifié dans le xml reçu
			int groupid = postCreateRole(c, portfolioUuid, "designer", userId);

			/// Ajoute la personne dans ce groupe
			putUserGroup(c, Integer.toString(groupid), Integer.toString(userId));
		}

		/// S'assure que la date est bonne
		touchPortfolio(c, null, portfolioUuid);

		String result = "<portfolios>";
		result += "<portfolio ";
		result += DomUtils.getXmlAttributeOutput("id", portfolioUuid)+" ";
		result += "/>";
		result += "</portfolios>";
		return result;
	}

	@Override
	public boolean isCodeExist( Connection c, String code )
	{
		boolean response = false;
		String sql;
		PreparedStatement st = null;
		ResultSet rs = null;
		try
		{
			// Retire la personne du rôle
			sql = "SELECT bin2uuid(portfolio_id) FROM node WHERE code=?";
			st = c.prepareStatement(sql);
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
	public Object putPortfolio(Connection c, MimeType inMimeType, MimeType outMimeType, String in, String portfolioUuid, int userId, Boolean portfolioActive, int groupId, String portfolioModelId) throws Exception
	{
		StringBuffer outTrace = new StringBuffer();

		//		if(!credential.isAdmin(userId))
		//			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		ResultSet resPortfolio = getMysqlPortfolioResultset(c, portfolioUuid);
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
				insertMysqlPortfolio(c, portfolioUuid,uuid,0,userId);

				writeNode(c, rootNode, portfolioUuid, portfolioModelId, userId,0, null,null,0,0,true, null, false);
			}
		}

		updateMysqlPortfolioActive(c, portfolioUuid,portfolioActive);

		return true;
	}

	@Override
	public Object deletePortfolio(Connection c, String portfolioUuid, int userId, int groupId) throws SQLException
	{
		/*
		if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");
		//*/

		return this.deleteMySqlPortfolio(c, portfolioUuid, userId, groupId);
	}

	@Override
	public Object getNodes(Connection c, MimeType outMimeType, String portfolioUuid,
			int userId,int groupId, String semtag, String parentUuid, String filterId,
			String filterParameters, String sortId) throws SQLException
	{
		return getNodeXmlListOutput(c, parentUuid, true, userId, groupId);
	}

	private  StringBuffer getNodeJsonOutput(Connection c, String nodeUuid,boolean withChildren, String withChildrenOfXsiType,int userId,int groupId,String label, boolean checkSecurity) throws SQLException
	{
		StringBuffer result = new StringBuffer();
		ResultSet resNode = getMysqlNode(c, nodeUuid,userId,groupId);
		ResultSet resResource;

		if(checkSecurity)
		{
			NodeRight nodeRight = cred.getNodeRight(c, userId,groupId,nodeUuid, label);
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
					resResource = getMysqlResource(c, nodeUuid);
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
							ResultSet resChildNode = this.getMysqlNodeResultset(c, arrayChild[i]);
							String tmpXsiType = "";
							try
							{
								tmpXsiType = resChildNode.getString("xsi_type");
							}
							catch(Exception ex)
							{

							}
							if(withChildrenOfXsiType==null || withChildrenOfXsiType.equals(tmpXsiType))
								result.append(getNodeJsonOutput(c, arrayChild[i],true,null,userId,groupId,label,true));

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

	private  StringBuffer getNodeXmlOutput(Connection c, String nodeUuid,boolean withChildren, String withChildrenOfXsiType, int userId,int groupId, String label,boolean checkSecurity) throws SQLException
	{
		StringBuffer result = new StringBuffer();
		// Verification securité
		if(checkSecurity)
		{
			NodeRight nodeRight = cred.getNodeRight(c, userId,groupId,nodeUuid, label);
			if(!nodeRight.read)
			{
				userId = cred.getPublicUid(c);
//			NodeRight nodeRight = new NodeRight(false,false,false,false,false,false);
			/// Vérifie les droits avec le compte publique (dernière chance)
				nodeRight = cred.getPublicRight(c, userId, 123, nodeUuid, "dummy");
				if( !nodeRight.read )
					return result;
			}
		}

		ResultSet resNode = getMysqlNode(c, nodeUuid,userId, groupId);
		ResultSet resResource;

		String indentation = " ";

		long start = 0;
		long metaxml = 0;
		long resource = 0;
		long children = 0;
		long end = 0;
		
		start = System.currentTimeMillis();
		if(resNode.next())
		{
			if(resNode.getString("shared_node_uuid")!=null)
			{
				result.append(getNodeXmlOutput(c, resNode.getString("shared_node_uuid"),true,null,userId,groupId, null,true));
			}
			else
			{
				result.append(indentation+"<"+resNode.getString("asm_type")+" "+DomUtils.getXmlAttributeOutput("id",resNode.getString("node_uuid"))+" ");

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

					metaxml = System.currentTimeMillis();
					
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

				resource = System.currentTimeMillis();
				if(resNode.getString("res_res_node_uuid")!=null)
					if(resNode.getString("res_res_node_uuid").length()>0)
					{
						result.append("<asmResource id='"+resNode.getString("res_res_node_uuid")+"'  contextid='"+nodeUuid+"' xsi_type='nodeRes'>");
						resResource = getMysqlResource(c, resNode.getString("res_res_node_uuid"));
						if(resResource.next())
							result.append(resResource.getString("content"));
						result.append("</asmResource>");
						resResource.close();
					}
				if(resNode.getString("res_context_node_uuid")!=null)
					if(resNode.getString("res_context_node_uuid").length()>0)
					{
						result.append("<asmResource id='"+resNode.getString("res_context_node_uuid")+"' contextid='"+nodeUuid+"' xsi_type='context'>");
						resResource = getMysqlResource(c, resNode.getString("res_context_node_uuid"));
						if(resResource.next())
							result.append(resResource.getString("content"));
						result.append("</asmResource>");
						resResource.close();
					}
				if(resNode.getString("res_node_uuid")!=null)
					if(resNode.getString("res_node_uuid").length()>0)
					{
						resResource = getMysqlResource(c, resNode.getString("res_node_uuid"));
						if(resResource.next())
						{
							result.append("<asmResource id='"+resNode.getString("res_node_uuid")+"' contextid='"+nodeUuid+"' xsi_type='"+resResource.getString("xsi_type")+"'>");

							result.append(resResource.getString("content"));
							result.append("</asmResource>");
						}
						resResource.close();
					}

				children = System.currentTimeMillis();
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
								ResultSet resChildNode = this.getMysqlNodeResultset(c, arrayChild[i]);

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
									result.append(getNodeXmlOutput(c, arrayChild[i],true,null,userId,groupId, null,true));

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
			end = System.currentTimeMillis();

			long d_start = metaxml - start;
			long d_metaxml = resource - metaxml;
			long d_resource = children - resource;
			long d_children = end -children;

			System.out.println("START: "+d_start);
			System.out.println("METAXML: "+d_metaxml);
			System.out.println("RESOURCE: "+d_resource);
			System.out.println("CHILDREN: "+d_children);
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


	private String getLinearXml(Connection c, String portfolioUuid, String rootuuid, Node portfolio, boolean withChildren, String withChildrenOfXsiType, int userId,int groupId, String role) throws SQLException, SAXException, IOException, ParserConfigurationException
//	private String getLinearXml(String portfolioUuid, String rootuuid, boolean withChildren, String withChildrenOfXsiType, int userId,int groupId, String role) throws SQLException, SAXException, IOException, ParserConfigurationException
	{
		DocumentBuilderFactory newInstance = DocumentBuilderFactory.newInstance();
		DocumentBuilder parse=newInstance.newDocumentBuilder();

//		/*
		long time0 = 0;
		long time1 = 0;
		long time2 = 0;
		long time3 = 0;
		long time4 = 0;
		long time5 = 0;
		long time6 = 0;
		//*/

		time0 = System.currentTimeMillis();

		ResultSet resNode = getMysqlStructure(c, portfolioUuid,userId, groupId);

		time1= System.currentTimeMillis();

//		Document document=portfolio.getOwnerDocument();

		HashMap<String, Object[]> resolve = new HashMap<String, Object[]>();
		/// Node -> parent
		HashMap<String, t_tree> entries = new HashMap<String, t_tree>();

		processQuery(resNode, resolve, entries, null, parse, role);
		resNode.close();

		time2 = System.currentTimeMillis();

		resNode = getSharedMysqlStructure(c, portfolioUuid,userId, groupId);

		time3 = System.currentTimeMillis();

		if( resNode != null )
		{
			processQuery(resNode, resolve, entries, null, parse, role);
			resNode.close();
		}

		time4 = System.currentTimeMillis();

		/// Reconstruct functional tree
		t_tree root = entries.get(rootuuid);
		StringBuilder out = new StringBuilder(256);
		if( root != null )
			reconstructTree( out, root, entries );

		time5 = System.currentTimeMillis();

		/// Reconstruct data
//		String output = reconstructData(root);

		/// return string

		/// Reconstruct tree, using children list (node_children_uuid)
		/*
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
		//*/

		time6 = System.currentTimeMillis();

		/*
		Node rootNode = resolve.get(rootuuid);
		if( rootNode != null )
			portfolio.appendChild(rootNode);
		//*/

		/*
		System.out.println("---- Portfolio ---");
		System.out.println("Query Main: "+(time1-time0));
		System.out.println("Parsing Main: "+(time2-time1));
		System.out.println("Query shared: "+(time3-time2));
		System.out.println("Parsing shared: "+(time4-time3));
		System.out.println("Reconstruction a: "+(time5-time4));
		System.out.println("Reconstruction b: "+(time6-time5));
		System.out.println("------------------");
		//*/

		return out.toString();
	}

	// Help reconstruct tree
	class t_tree
	{
		String data = "";
		String type = "";
		String childString = "";
	};

	private void reconstructTree( StringBuilder data, t_tree node, HashMap<String, t_tree> entries )
	{
		if( node.childString == null ) return;
		
		String[] childsId = node.childString.split(",");
		data.append(node.data);
//		String data = node.data;

		for( int i=0; i<childsId.length; ++i )
		{
			String cid = childsId[i];
			if( "".equals(cid) ) continue;

			t_tree c = entries.remove(cid);	// Help converge a bit faster
			if( c != null )
			{
				reconstructTree(data, c, entries);
			}
			else
			{
				// Node missing from query, can be related to security
				// safe to ignore
			}
		}

		data.append("</").append(node.type).append(">");
	}

	private void processQuery( ResultSet result, HashMap<String, Object[]> resolve, HashMap<String, t_tree> entries, Document document, DocumentBuilder parse, String role ) throws UnsupportedEncodingException, DOMException, SQLException, SAXException, IOException
	{
		long t_01 = 0;
		long t_02 = 0;
		long t_03 = 0;
		long t_04 = 0;
		long t_05 = 0;
		long t_06 = 0;

		long totalConstruct = 0;
		long totalAggregate = 0;
		long totalParse = 0;
		long totalAdopt = 0;
		long totalReconstruct = 0;
		StringBuilder data = new StringBuilder(256);
		if( result != null )
			while( result.next() )
			{
				data.setLength(0);
				
				t_01 = System.currentTimeMillis();
				String nodeUuid = result.getString("node_uuid");
				if( nodeUuid == null ) continue;    // Cas où on a des droits sur plus de noeuds qui ne sont pas dans le portfolio

				String childsId = result.getString("node_children_uuid");

				String type = result.getString("asm_type");

				data.append("<");
				data.append(type);
				data.append(" ");
				
				String xsi_type = result.getString("xsi_type");
				if( null == xsi_type )
					xsi_type = "";

				String readRight= result.getInt("RD")==1 ? "Y" : "N";
				String writeRight= result.getInt("WR")==1 ? "Y" : "N";
				String submitRight= result.getInt("SB")==1 ? "Y" : "N";
				String deleteRight= result.getInt("DL")==1 ? "Y" : "N";
				String macro = result.getString("rules_id");
				
				if( macro != null )
				{
					data.append("action=\"");
					data.append(macro);
					data.append("\" ");
//					macro = "action=\""+macro+"\"";
				}
				else
					macro = "";

				data.append("delete=\"");
				data.append(deleteRight);
				data.append("\" id=\"");
				data.append(nodeUuid);
				data.append("\" read=\"");
				data.append(readRight);
				data.append("\" role=\"");
				data.append(role);
				data.append("\" submit=\"");
				data.append(submitRight);
				data.append("\" write=\"");
				data.append(writeRight);
				data.append("\" xsi_type=\"");
				data.append(xsi_type);
				data.append("\">");
				
				String attr = result.getString("metadata_wad");
				String metaFragwad="";
//				if( !"".equals(attr) )  /// Attributes exists
				if( attr!=null && !"".equals(attr) )  /// Attributes exists
				{
					data.append("<metadata-wad ");
					data.append(attr);
					data.append("/>");
//					metaFragwad = "<metadata-wad "+attr+"/>";
				}
				else
				{
					data.append("<metadata-wad/>");
//					metaFragwad = "<metadata-wad />";
				}

				attr = result.getString("metadata_epm");
				String metaFragepm="";
//				if( !"".equals(attr) )  /// Attributes exists
				if( attr!=null && !"".equals(attr) )  /// Attributes exists
				{
					data.append("<metadata-epm ");
					data.append(attr);
					data.append("/>");
//					metaFragepm = "<metadata-epm "+attr+"/>";
				}
				else
				{
					data.append("<metadata-epm/>");
//					metaFragepm = "<metadata-epm />";
				}

				attr = result.getString("metadata");
				String metaFrag="";
//				if( !"".equals(attr) )  /// Attributes exists
				if( attr!=null && !"".equals(attr) )  /// Attributes exists
				{
					data.append("<metadata ");
					data.append(attr);
					data.append("/>");
//					metaFrag = "<metadata "+attr+"/>";
				}
				else
				{
					data.append("<metadata/>");
//					metaFrag = "<metadata />";
				}

				String res_res_node_uuid = result.getString("res_res_node_uuid");
				String res_res_node = "";
				if( res_res_node_uuid!=null && res_res_node_uuid.length()>0 )
				{
					String nodeContent = result.getString("r2_content");
					if( nodeContent != null )
					{
						data.append("<asmResource contextid=\"");
						data.append(nodeUuid);
						data.append("\" id=\"");
						data.append(res_res_node_uuid);
						data.append("\" xsi_type=\"nodeRes\">");
						data.append(nodeContent.trim());
						data.append("</asmResource>");
//						res_res_node = "<asmResource contextid=\""+nodeUuid+"\" id=\""+res_res_node_uuid+"\" xsi_type=\"nodeRes\">"+nodeContent.trim()+"</asmResource>";
					}
				}

				String res_context_node_uuid = result.getString("res_context_node_uuid");
				String context_node = "";
				if( res_context_node_uuid!=null && res_context_node_uuid.length()>0 )
				{
					String nodeContent = result.getString("r3_content");
					if( nodeContent != null )
					{
						data.append("<asmResource contextid=\"");
						data.append(nodeUuid);
						data.append("\" id=\"");
						data.append(res_context_node_uuid);
						data.append("\" xsi_type=\"context\">");
						data.append(nodeContent.trim());
						data.append("</asmResource>");
//						context_node = "<asmResource contextid=\""+nodeUuid+"\" id=\""+res_context_node_uuid+"\" xsi_type=\"context\">"+nodeContent.trim()+"</asmResource>";
					} else {
						data.append("<asmResource contextid=\"");
						data.append(nodeUuid);
						data.append("\" id=\"");
						data.append(res_context_node_uuid);
						data.append("\" xsi_type=\"context\"/>");
//						context_node = "<asmResource contextid=\""+nodeUuid+"\" id=\""+res_context_node_uuid+"\" xsi_type=\"context\"/>";
					}
				}

				String res_node_uuid = result.getString("res_node_uuid");
				String specific_node = "";
				if( res_node_uuid!=null && res_node_uuid.length()>0 )
				{
					String nodeContent = result.getString("r1_content");
					if( nodeContent != null )
					{
						data.append("<asmResource contextid=\"");
						data.append(nodeUuid);
						data.append("\" id=\"");
						data.append(res_node_uuid);
						data.append("\" xsi_type=\"");
						data.append(result.getString("r1_type"));
						data.append("\">");
						data.append(nodeContent.trim());
						data.append("</asmResource>");
//						specific_node = "<asmResource contextid=\""+nodeUuid+"\" id=\""+res_node_uuid+"\" xsi_type=\""+result.getString("r1_type")+"\">"+nodeContent.trim()+"</asmResource>";
					}
				}

				t_02 = System.currentTimeMillis();

				/// On spécifie aussi le rôle qui a été choisi dans la récupération des données
				/*
				String snode = "<"+type+" "+macro+" delete=\""+deleteRight+"\" id=\""+nodeUuid+"\" read=\""+readRight+"\" role=\""+role+"\" submit=\""+submitRight+"\" write=\""+writeRight+"\" xsi_type=\""+xsi_type+"\" >"+
						metaFragwad+
						metaFragepm+
						metaFrag+
						res_res_node+
						context_node+
						specific_node
//						+"</"+type+">";
						+"";	// Will be closed when recontructing by string instead of xml parsing
				//*/
				String snode = data.toString();
				
				t_03 = System.currentTimeMillis();

				/*
				Document frag = parse.parse(new ByteArrayInputStream(snode.getBytes("UTF-8")));
				Element ressource = frag.getDocumentElement();
				//*/

				t_04 = System.currentTimeMillis();

//				document.adoptNode(ressource);

				t_05 = System.currentTimeMillis();

//				Node node = ressource;

				/// Prepare data to reconstruct tree
//				resolve.put(nodeUuid, node);
				t_tree entry = new t_tree();
//				entry.uuid = nodeUuid;
				entry.type = type;
				entry.data = snode;
				Object [] nodeData = {snode, type};
				resolve.put(nodeUuid, nodeData);
				if( !"".equals(childsId) && childsId != null )
				{
					entry.childString = childsId;
//					Object[] obj = {node, childsId};
//					entries.add(obj);
				}
				entries.put(nodeUuid, entry);

				t_06 = System.currentTimeMillis();

				/*
				totalConstruct += t_02-t_01;
				totalAggregate += t_03-t_02;
				totalParse += t_04-t_03;
				totalAdopt += t_05-t_04;
				totalReconstruct += t_06-t_05;
//*/
				
				/*
				System.out.println("======= Loop =======");
				System.out.println("Retrieve data: "+ (t_02-t_01));
				System.out.println("Aggregate data: "+ (t_03-t_02));
				System.out.println("Parse as XML: "+ (t_04-t_03));
				System.out.println("Adopt XML: "+ (t_05-t_04));
				System.out.println("Store for reconstruction: "+ (t_06-t_05));
				//*/
			}
		/*
		System.out.println("======= Total =======");
		System.out.println("Construct: "+ totalConstruct);
		System.out.println("Aggregate: "+ totalAggregate);
		System.out.println("Parsing: "+ totalParse);
		System.out.println("Adopt: "+ totalAdopt);
		System.out.println("Reconstruction: "+ totalReconstruct);
		//*/
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

	private ResultSet getMysqlStructure(Connection c, String portfolioUuid, int userId,  int groupId) throws SQLException
	{
		PreparedStatement st=null;
		String sql = "";
		ResultSet rs = null;

		long time0=0;
		long time1=0;
		long time2=0;
		long time3=0;
		long time4=0;
		long time5=0;
		long time6=0;

		try
		{
			time0 = System.currentTimeMillis();

			String rootNodeUuid = getPortfolioRootNode(c, portfolioUuid);

			time1 = System.currentTimeMillis();

			// Cas admin, designer
			if(cred.isAdmin(c, userId) || cred.isDesigner(c, userId, rootNodeUuid))
			{
				sql = "SELECT bin2uuid(n.node_uuid) AS node_uuid, " +
						"node_children_uuid, n.node_order, n.metadata, n.metadata_wad, n.metadata_epm, " +
						"n.shared_node AS shared_node, bin2uuid(n.shared_node_res_uuid) AS shared_node_res_uuid, bin2uuid(n.res_node_uuid) AS res_node_uuid, " +
						"r1.xsi_type AS r1_type, r1.content AS r1_content, bin2uuid(n.res_res_node_uuid) as res_res_node_uuid, " +
						"r2.content AS r2_content, bin2uuid(n.res_context_node_uuid) as res_context_node_uuid, " +
						"r3.content AS r3_content, n.asm_type, n.xsi_type, " +
						"1 AS RD, 1 AS WR, 1 AS SB, 1 AS DL, NULL AS types_id, NULL AS rules_id " +
						"FROM node n " +
						"LEFT JOIN resource_table r1 ON n.res_node_uuid=r1.node_uuid " +
						"LEFT JOIN resource_table r2 ON n.res_res_node_uuid=r2.node_uuid " +
						"LEFT JOIN resource_table r3 ON n.res_context_node_uuid=r3.node_uuid " +
						"WHERE portfolio_id=uuid2bin(?)";
				st = c.prepareStatement(sql);
				st.setString(1, portfolioUuid);
			}
			else if(cred.isPublic(c, null, portfolioUuid))	// Public case, looks like previous query, but with different rights
			{
				sql = "SELECT bin2uuid(n.node_uuid) AS node_uuid, " +
						"node_children_uuid, n.node_order, n.metadata, n.metadata_wad, n.metadata_epm, " +
						"n.shared_node AS shared_node, bin2uuid(n.shared_node_res_uuid) AS shared_node_res_uuid, bin2uuid(n.res_node_uuid) AS res_node_uuid, " +
						"r1.xsi_type AS r1_type, r1.content AS r1_content, bin2uuid(n.res_res_node_uuid) as res_res_node_uuid, " +
						"r2.content AS r2_content, bin2uuid(n.res_context_node_uuid) as res_context_node_uuid, " +
						"r3.content AS r3_content, n.asm_type, n.xsi_type, " +
						"1 AS RD, 0 AS WR, 0 AS SB, 0 AS DL, NULL AS types_id, NULL AS rules_id " +
						"FROM node n " +
						"LEFT JOIN resource_table r1 ON n.res_node_uuid=r1.node_uuid " +
						"LEFT JOIN resource_table r2 ON n.res_res_node_uuid=r2.node_uuid " +
						"LEFT JOIN resource_table r3 ON n.res_context_node_uuid=r3.node_uuid " +
						"WHERE portfolio_id=uuid2bin(?)";
				st = c.prepareStatement(sql);
				st.setString(1, portfolioUuid);
			}
			else
			{
				/// FIXME: Il faudrait peut-être prendre une autre stratégie pour sélectionner les bonnes données
				// Cas propriétaire
				// Cas générale (partage via droits)

				if (dbserveur.equals("mysql")){
					sql = "CREATE TEMPORARY TABLE t_rights(" +
							"grid BIGINT NOT NULL, " +
							"id binary(16) UNIQUE NOT NULL, " +
							"RD TINYINT(1) NOT NULL, " +
							"WR TINYINT(1) NOT NULL, " +
							"DL TINYINT(1) NOT NULL, " +
							"SB TINYINT(1) NOT NULL, " +
							"AD TINYINT(1) NOT NULL) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
					st = c.prepareStatement(sql);
					st.execute();
					st.close();
				} else if (dbserveur.equals("oracle")){
					String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_rights(" +
							"grid NUMBER(19,0) NOT NULL, " +
							"id RAW(16) NOT NULL, " +
							"RD NUMBER(1) NOT NULL, " +
							"WR NUMBER(1) NOT NULL, " +
							"DL NUMBER(1) NOT NULL, " +
							"SB NUMBER(1) NOT NULL, " +
							"AD NUMBER(1) NOT NULL, CONSTRAINT t_rights_UK_id UNIQUE (id)) ON COMMIT PRESERVE ROWS";
					sql = "{call create_or_empty_table('t_rights','"+v_sql+"')}";
					CallableStatement ocs = c.prepareCall(sql) ;
					ocs.execute();
					ocs.close();
				}
				time2 = System.currentTimeMillis();

				time3 = System.currentTimeMillis();

				/*
				/// Droits données par le groupe sélectionné
				sql = "INSERT INTO t_rights(grid,id,RD,WR,DL,SB,AD) " +
						"SELECT gr.grid, gr.id, gr.RD, gr.WR, gr.DL, gr.SB, gr.AD " +
						"FROM group_info gi, group_right_info gri, group_rights gr " +
						"WHERE gi.grid=gri.grid AND gri.grid=gr.grid AND gi.gid=?";
				st = connection.prepareStatement(sql);
				st.setInt(1, groupId);
				st.executeUpdate();
				st.close();
				//*/

				/// Droits données par le portfolio à 'tout le monde'
				/// Fusion des droits, pas très beau mais bon.
				/// Droits donné spécifiquement à un utilisateur
				/// FIXME: Devrait peut-être vérifier si la personne a les droits d'y accéder?
				if (dbserveur.equals("mysql")){
					sql = "INSERT INTO t_rights(grid,id,RD,WR,DL,SB,AD) ";
					sql += "SELECT gr.grid, gr.id, gr.RD, gr.WR, gr.DL, gr.SB, gr.AD " +
						"FROM group_right_info gri, group_info gi, group_rights gr " +
						"WHERE gri.grid=gi.grid AND gri.grid=gr.grid AND gri.portfolio_id=uuid2bin(?) " +
						"AND (gi.label='all' OR gi.grid=? OR gi.label=(SELECT login FROM credential WHERE userid=?)) " +
						"ON DUPLICATE KEY " +
						"UPDATE t_rights.RD=GREATEST(t_rights.RD,gr.RD), " +
						"t_rights.WR=GREATEST(t_rights.WR,gr.WR), " +
						"t_rights.DL=GREATEST(t_rights.DL,gr.DL), " +
						"t_rights.SB=GREATEST(t_rights.SB,gr.SB), " +
						"t_rights.AD=GREATEST(t_rights.AD,gr.AD)";
				}
				else if (dbserveur.equals("oracle"))
				{
					sql = "MERGE INTO t_rights d USING (";
					sql += "SELECT MAX(gr.grid) AS grid, gr.id, MAX(gr.RD) AS RD, MAX(gr.WR) AS WR, MAX(gr.DL) AS DL, MAX(gr.SB) AS SB, MAX(gr.AD) AS AD " +	// FIXME MAX(gr.grid) will have unintended consequences
							"FROM group_right_info gri, group_info gi, group_rights gr " +
							"WHERE gri.grid=gi.grid AND gri.grid=gr.grid AND gri.portfolio_id=uuid2bin(?) " +
							"AND (gi.label='all' OR gi.grid=? OR gi.label=(SELECT login FROM credential WHERE userid=?)) ";
					sql += " GROUP BY gr.id) s ON (d.grid = s.grid AND d.id = s.id) WHEN MATCHED THEN UPDATE SET " +
							"d.RD=GREATEST(d.RD,s.RD), " +
							"d.WR=GREATEST(d.WR,s.WR), " +
							"d.DL=GREATEST(d.DL,s.DL), " +
							"d.SB=GREATEST(d.SB,s.SB), " +
							"d.AD=GREATEST(d.AD,s.AD)";
					sql += " WHEN NOT MATCHED THEN INSERT (d.grid, d.id, d.RD, d.WR, d.DL, d.SB, d.AD) VALUES (s.grid, s.id, s.RD, s.WR, s.DL, s.SB, s.AD)";
				}
				st = c.prepareStatement(sql);
				st.setString(1, portfolioUuid);
				st.setInt(2, groupId);
				st.setInt(3, userId);
				st.executeUpdate();
				st.close();

				time4 = System.currentTimeMillis();

				/*
				/// Debug stuff for Oracle
				sql = "SELECT gr.grid, gr.id, gr.RD, gr.WR, gr.DL, gr.SB, gr.AD " +
							"FROM group_right_info gri " +
							"LEFT JOIN group_info gi ON gri.grid=gi.grid " +
							"LEFT JOIN group_rights gr ON gri.grid=gr.grid " +
							"WHERE gri.portfolio_id=uuid2bin(?) ";
				st = connection.prepareStatement(sql);
				st.setString(1, portfolioUuid);
				ResultSet res = st.executeQuery();
				System.out.println("portfolio:"+portfolioUuid+" --> "+groupId+" -- "+userId);
				while( res.next() )
				{
					String grid = res.getString(1);
					String id = res.getString(2);
					String rw = res.getString(4);
					System.out.println("grid: "+grid+" ID:"+id+" --> "+rw);
				}
				res.close();
				st.close();
				///*/
				/*
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
				//*/

				time5 = System.currentTimeMillis();

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
						"WHERE tr.grid=gr.grid AND tr.id=gr.id AND tr.RD=1 " +
						"UNION ALL " +	/// Union pour les données appartenant au créateur
						"SELECT bin2uuid(n.node_uuid) AS node_uuid, " +
						"node_children_uuid, n.node_order, n.metadata, n.metadata_wad, n.metadata_epm, " +
						"n.shared_node AS shared_node, bin2uuid(n.shared_node_res_uuid) AS shared_node_res_uuid, bin2uuid(n.res_node_uuid) AS res_node_uuid, " +
						"r1.xsi_type AS r1_type, r1.content AS r1_content, bin2uuid(n.res_res_node_uuid) as res_res_node_uuid, " +
						"r2.content AS r2_content, bin2uuid(n.res_context_node_uuid) as res_context_node_uuid, " +
						"r3.content AS r3_content, n.asm_type, n.xsi_type, " +
						"1 AS RD, 1 AS WR, 1 AS SB, 1 AS DL, NULL AS types_id, NULL AS rules_id " +
						"FROM node n " +
						"LEFT JOIN resource_table r1 ON n.res_node_uuid=r1.node_uuid " +
						"LEFT JOIN resource_table r2 ON n.res_res_node_uuid=r2.node_uuid " +
						"LEFT JOIN resource_table r3 ON n.res_context_node_uuid=r3.node_uuid " +
						"WHERE n.modif_user_id=? AND portfolio_id=uuid2bin(?)";
				st = c.prepareStatement(sql);
				st.setInt(1, userId);
				st.setString(2, portfolioUuid);
			}
			rs = st.executeQuery();   // Pas sûr si les 'statement' restent ouvert après que la connexion soit fermée

			time6 = System.currentTimeMillis();
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
					st = c.prepareStatement(sql);
					st.execute();
					st.close();
				}
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}

//		System.out.println((time1-time0)+","+(time2-time1)+","+(time3-time2)+","+(time4-time3)+","+(time5-time4)+","+(time6-time5));
//		/*
		System.out.println("---- Query Portfolio ----");
		System.out.println("Fetch root: "+(time1-time0));
		System.out.println("Check rights: "+(time2-time1));
		System.out.println("Create temp: "+(time3-time2));
		System.out.println("Fetch rights all/group: "+(time4-time3));
		System.out.println("Fetch user rights: "+(time5-time4));
		System.out.println("Actual query: "+(time6-time5));
		//*/

		return rs;
	}

	/// Récupère les noeuds partagés d'un portfolio
	/// C'est séparé car les noeud ne provenant pas d'un même portfolio, on ne peut pas les sélectionner rapidement
	/// Autre possibilité serait de garder ce même type de fonctionnement pour une sélection par niveau d'un portfolio.
	/// TODO: A faire un 'benchmark' dessus
	private ResultSet getSharedMysqlStructure(Connection c, String portfolioUuid, int userId,  int groupId) throws SQLException
	{
		PreparedStatement st = null;
		String sql;
		ResultSet res = null;

		try
		{
			/// Check if there's shared node in this portfolio
			sql = 	"SELECT bin2uuid(n.shared_node_uuid) AS shared_node_uuid " +
					"FROM node n " +
					"WHERE n.portfolio_id=uuid2bin(?) AND shared_node=1";
			st = c.prepareStatement(sql);
			st.setString(1, portfolioUuid);
			res = st.executeQuery();
			if( res.next() )
			{
				String sharedNode = res.getString("shared_node_uuid");
				if( sharedNode == null )
					return null;
			}
			else
				return null;

			/// Pour le filtrage de la structure
			if (dbserveur.equals("mysql")){
				sql = "CREATE TEMPORARY TABLE t_struc_parentid(" +
						"uuid binary(16) UNIQUE NOT NULL, " +
						"node_parent_uuid binary(16), " +
						"t_level INT) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")){
				String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_struc_parentid(" +
						"uuid RAW(16) NOT NULL, " +
						"node_parent_uuid RAW(16), " +
						"t_level NUMBER(10,0)"+
						",  CONSTRAINT t_struc_parentid_UK_uuid UNIQUE (uuid)) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_struc_parentid','"+v_sql+"')}";
				CallableStatement ocs = c.prepareCall(sql) ;
				ocs.execute();
				ocs.close();
			}

			// En double car on ne peut pas faire d'update/select d'une même table temporaire
			if (dbserveur.equals("mysql")){
				sql = "CREATE TEMPORARY TABLE t_struc_parentid_2(" +
						"uuid binary(16) UNIQUE NOT NULL, " +
						"node_parent_uuid binary(16), " +
						"t_level INT) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")){
				String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_struc_parentid_2(" +
						"uuid RAW(16) NOT NULL, " +
						"node_parent_uuid RAW(16), " +
						"t_level NUMBER(10,0)"+
						",  CONSTRAINT t_struc_parentid_2_UK_uuid UNIQUE (uuid)) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_struc_parentid_2','"+v_sql+"')}";
				CallableStatement ocs = c.prepareCall(sql) ;
				ocs.execute();
				ocs.close();
			}

			/// Initialise la descente des noeuds partagés
			sql = "INSERT INTO t_struc_parentid(uuid, node_parent_uuid, t_level) " +
					"SELECT n.shared_node_uuid, n.node_parent_uuid, 0 " +
					"FROM node n " +
					"WHERE n.portfolio_id=uuid2bin(?) AND shared_node=1";
			st = c.prepareStatement(sql);
			st.setString(1, portfolioUuid);
			st.executeUpdate();
			st.close();

			/// On boucle, sera toujours <= à "nombre de noeud du portfolio"
			int level = 0;
			int added = 1;
			if (dbserveur.equals("mysql")){
				sql = "INSERT IGNORE INTO t_struc_parentid_2(uuid, node_parent_uuid, t_level) ";
			} else if (dbserveur.equals("oracle")){
				sql = "INSERT /*+ ignore_row_on_dupkey_index(t_struc_parentid_2,t_struc_parentid_2_UK_uuid)*/ INTO t_struc_parentid_2(uuid, node_parent_uuid, t_level) ";
			}
			sql += "SELECT n.node_uuid, n.node_parent_uuid, ? " +
					"FROM node n WHERE n.node_parent_uuid IN (SELECT uuid FROM t_struc_parentid t " +
					"WHERE t.t_level=?)";

	        String sqlTemp=null;
			if (dbserveur.equals("mysql")){
				sqlTemp = "INSERT IGNORE INTO t_struc_parentid SELECT * FROM t_struc_parentid_2;";
			} else if (dbserveur.equals("oracle")){
				sqlTemp = "INSERT /*+ ignore_row_on_dupkey_index(t_struc_parentid,t_struc_parentid_UK_uuid)*/ INTO t_struc_parentid SELECT * FROM t_struc_parentid_2";
			}
	        PreparedStatement stTemp = c.prepareStatement(sqlTemp);

			st = c.prepareStatement(sql);
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
					" LEFT JOIN group_info gi ON gr.grid=gi.grid" +
					" LEFT JOIN group_user gu ON gi.gid=gu.gid" +
					" WHERE gu.userid=? AND gr.RD=1" +  // On doit au moins avoir le droit de lecture
					" AND n.node_uuid IN (SELECT uuid FROM t_struc_parentid)";   // Selon note filtrage, prendre les noeud nécéssaire

			st = c.prepareStatement(sql);
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
					sql = "DROP TEMPORARY TABLE IF EXISTS t_struc_parentid, t_struc_parentid_2";
					st = c.prepareStatement(sql);
					st.execute();
					st.close();
				}
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}

		return res;
	}

	/// TODO: A faire un 'benchmark' dessus
	/// Récupère les noeuds en dessous par niveau. Pour faciliter le traitement des shared_node
	/// Mais ça serait beaucoup plus simple de faire un objet a traiter dans le client
	private ResultSet getNodePerLevel(Connection c, String nodeUuid, int userId,  int rrgId) throws SQLException
	{
		PreparedStatement st = null;
		String sql="";
		ResultSet res = null;

		long t_start = System.currentTimeMillis();

		try
		{
			// Take data subset for faster queries (instead of the whole DB each time)
			if (dbserveur.equals("mysql")){
				sql = "CREATE TEMPORARY TABLE t_node(" +
						"node_uuid binary(16)  NOT NULL, " +
						"node_parent_uuid binary(16) DEFAULT NULL, " +
//						"node_children_uuid varchar(8000), " +	/// FIXME Will break if we try to import a really wide tree, or if there's a large number of nodes under one parent
						"node_order int(12) NOT NULL, " +
//						"metadata varchar(255) NOT NULL, " +
//						"metadata_wad varchar(255) NOT NULL, " +
//						"metadata_epm varchar(255) NOT NULL, " +
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
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
				
				// Filtrage avec droits
				sql = "CREATE TEMPORARY TABLE t_rights_22(" +
						"grid BIGINT NOT NULL, " +
						"id binary(16) UNIQUE NOT NULL, " +
						"RD TINYINT(1) NOT NULL, " +
						"WR TINYINT(1) NOT NULL, " +
						"DL TINYINT(1) NOT NULL, " +
						"SB TINYINT(1) NOT NULL, " +
						"AD TINYINT(1) NOT NULL) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
				
				/// Pour le filtrage de la structure
				sql = "CREATE TEMPORARY TABLE t_struc_parentid(" +
						"uuid binary(16) UNIQUE NOT NULL, " +
						"node_parent_uuid binary(16), " +
						"t_level INT) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
				
				// En double car on ne peut pas faire d'update/select d'une même table temporaire
				sql = "CREATE TEMPORARY TABLE t_struc_parentid_2(" +
						"uuid binary(16) UNIQUE NOT NULL, " +
						"node_parent_uuid binary(16), " +
						"t_level INT) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();

			} else if (dbserveur.equals("oracle")){
				String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_node(" +
						"node_uuid RAW(16)  NOT NULL, " +
						"node_parent_uuid RAW(16) DEFAULT NULL, " +
//						"node_children_uuid CLOB, " +
						"node_order NUMBER(12) NOT NULL, " +
//						"metadata CLOB DEFAULT NULL, " +
//						"metadata_wad CLOB DEFAULT NULL, " +
//						"metadata_epm CLOB DEFAULT NULL, " +
						"res_node_uuid RAW(16) DEFAULT NULL, " +
						"res_res_node_uuid RAW(16) DEFAULT NULL, " +
						"res_context_node_uuid RAW(16)  DEFAULT NULL, " +
						"shared_res NUMBER(1) NOT NULL, " +
						"shared_node NUMBER(1) NOT NULL, " +
						"shared_node_res NUMBER(1) NOT NULL, " +
						"shared_res_uuid RAW(16) DEFAULT NULL, " +
						"shared_node_uuid RAW(16) DEFAULT NULL, " +
						"shared_node_res_uuid RAW(16) DEFAULT NULL, " +
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
						"portfolio_id RAW(16) DEFAULT NULL, CONSTRAINT t_node_UK_id UNIQUE (node_uuid)) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_node','"+v_sql+"')}";
				CallableStatement ocs = c.prepareCall(sql) ;
				ocs.execute();
				ocs.close();
				
				// XXX 22, because oracle can't clean up correctly after itself
				v_sql = "CREATE GLOBAL TEMPORARY TABLE t_rights_22(" +
						"grid NUMBER(19,0) NOT NULL, " +
						"id RAW(16) NOT NULL, " +
						"RD NUMBER(1) NOT NULL, " +
						"WR NUMBER(1) NOT NULL, " +
						"DL NUMBER(1) NOT NULL, " +
						"SB NUMBER(1) NOT NULL, " +
						"AD NUMBER(1) NOT NULL, CONSTRAINT t_rights_22_UK_id UNIQUE (id)) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_rights_22','"+v_sql+"')}";
				ocs = c.prepareCall(sql) ;
				ocs.execute();
				ocs.close();

				/// Pour le filtrage de la structure
				v_sql = "CREATE GLOBAL TEMPORARY TABLE t_struc_parentid(" +
						"uuid RAW(16) NOT NULL, " +
						"node_parent_uuid RAW(16), " +
						"t_level NUMBER(10,0)"+
						",  CONSTRAINT t_struc_parentid_UK_uuid UNIQUE (uuid)) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_struc_parentid','"+v_sql+"')}";
				ocs = c.prepareCall(sql) ;
				ocs.execute();
				ocs.close();

				// En double car on ne peut pas faire d'update/select d'une même table temporaire
				v_sql = "CREATE GLOBAL TEMPORARY TABLE t_struc_parentid_2(" +
						"uuid RAW(16) NOT NULL, " +
						"node_parent_uuid RAW(16), " +
						"t_level NUMBER(10,0)"+
						",  CONSTRAINT t_struc_parentid_2_UK_uuid UNIQUE (uuid)) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_struc_parentid_2','"+v_sql+"')}";
				ocs = c.prepareCall(sql) ;
				ocs.execute();
				ocs.close();
			}

			long t_tempTable = System.currentTimeMillis();

			/// Portfolio id, gonna need that later
			sql = "SELECT bin2uuid(portfolio_id) AS portfolio_id FROM node WHERE node_uuid=uuid2bin(?)";
			st = c.prepareStatement(sql);
			st.setString(1, nodeUuid);
			res = st.executeQuery();
			res.next();
			String portfolioid = res.getString("portfolio_id");
			res.close();
			st.close();
			
			/// Init temp data table
			sql = "INSERT INTO t_node " +
					"SELECT node_uuid, node_parent_uuid, node_order, res_node_uuid, res_res_node_uuid, res_context_node_uuid, shared_res, shared_node, shared_node_res, shared_res_uuid, shared_node_uuid, shared_node_res_uuid, asm_type, xsi_type, semtag, semantictag, label, code, descr, format, modif_user_id, modif_date, portfolio_id " +
					"FROM node n " +
					"WHERE n.portfolio_id=uuid2bin(?)";
			st = c.prepareStatement(sql);
			st.setString(1, portfolioid);
			st.executeUpdate();
			st.close();

			long t_dataTable = System.currentTimeMillis();

			/// Initialise la descente des noeuds, si il y a un partagé on partira de là, sinon du noeud par défaut
			/// FIXME: There will be something with shared_node_uuid
			sql = "INSERT INTO t_struc_parentid(uuid, node_parent_uuid, t_level) " +
					"SELECT COALESCE(n.shared_node_uuid, n.node_uuid), n.node_parent_uuid, 0 " +
					"FROM t_node n " +
					"WHERE n.node_uuid=uuid2bin(?)";
			st = c.prepareStatement(sql);
			st.setString(1, nodeUuid);
			st.executeUpdate();
			st.close();

			long t_initNode = System.currentTimeMillis();

			/// On boucle, avec les shared_node si ils existent.
			/// FIXME: Possiblité de boucle infini
			int level = 0;
			int added = 1;
			if (dbserveur.equals("mysql")){
				sql = "INSERT IGNORE INTO t_struc_parentid_2(uuid, node_parent_uuid, t_level) ";
			} else if (dbserveur.equals("oracle")){
				sql = "INSERT /*+ ignore_row_on_dupkey_index(t_struc_parentid_2,t_struc_parentid_2_UK_uuid)*/ INTO t_struc_parentid_2(uuid, node_parent_uuid, t_level) ";
			}
			sql += "SELECT COALESCE(n.shared_node_uuid, n.node_uuid), n.node_parent_uuid, ? " +
					"FROM t_node n WHERE n.node_parent_uuid IN (SELECT uuid FROM t_struc_parentid t " +
					"WHERE t.t_level=?)";

			String sqlTemp=null;
			if (dbserveur.equals("mysql")){
				sqlTemp = "INSERT IGNORE INTO t_struc_parentid SELECT * FROM t_struc_parentid_2;";
			} else if (dbserveur.equals("oracle")){
				sqlTemp = "INSERT /*+ ignore_row_on_dupkey_index(t_struc_parentid,t_struc_parentid_UK_uuid)*/ INTO t_struc_parentid SELECT * FROM t_struc_parentid_2";
			}
			PreparedStatement stTemp = c.prepareStatement(sqlTemp);

			long t_initLoop = System.currentTimeMillis();

			st = c.prepareStatement(sql);
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

			long t_endLoop = System.currentTimeMillis();

			if( cred.isDesigner(c, userId, nodeUuid) || cred.isAdmin(c, userId) )
			{
				sql = "INSERT INTO t_rights_22(grid, id, RD, WR, DL, SB, AD) " +
						"SELECT 0, ts.uuid, 1, 1, 1, 0, 0 " +
						"FROM t_struc_parentid ts";
				st = c.prepareStatement(sql);
			}
			else if ( cred.isPublic(c, nodeUuid, null) )
			{
				sql = "INSERT INTO t_rights_22(grid, id, RD, WR, DL, SB, AD) " +
						"SELECT 0, ts.uuid, 1, 0, 0, 0, 0 " +
						"FROM t_struc_parentid ts";
				st = c.prepareStatement(sql);
			}
			else
			{
				// Aggrégation des droits avec 'all', l'appartenance du groupe de l'utilisateur, et les droits propres à l'utilisateur
				if (dbserveur.equals("mysql")){
					sql = "INSERT INTO t_rights_22(grid,id,RD,WR,DL,SB,AD) ";
					sql += "SELECT gr.grid, gr.id, gr.RD, gr.WR, gr.DL, gr.SB, gr.AD " +
						"FROM group_right_info gri, group_rights gr, t_struc_parentid ts " +
						"WHERE gri.portfolio_id=uuid2bin(?) AND gri.grid=gr.grid AND ts.uuid=gr.id " +
						"AND (gri.grid=(SELECT grid FROM group_right_info WHERE portfolio_id=uuid2bin(?) AND label='all') OR gri.grid=? OR " +
						"gri.grid=(SELECT grid FROM credential c, group_right_info gri, t_node n WHERE n.node_uuid=uuid2bin(?) AND n.portfolio_id=gri.portfolio_id AND c.login=gri.label AND c.userid=?)) " +
						"ON DUPLICATE KEY " +
						"UPDATE t_rights_22.RD=GREATEST(t_rights_22.RD,gr.RD), " +
						"t_rights_22.WR=GREATEST(t_rights_22.WR, gr.WR), " +
						"t_rights_22.DL=GREATEST(t_rights_22.DL, gr.DL), " +
						"t_rights_22.SB=GREATEST(t_rights_22.SB, gr.SB), " +
						"t_rights_22.AD=GREATEST(t_rights_22.AD, gr.AD)";
				}
				else if (dbserveur.equals("oracle"))
				{
					sql = "MERGE INTO t_rights_22 d USING (";
					sql += "SELECT MAX(gr.grid) AS grid, gr.id, MAX(gr.RD) AS RD, MAX(gr.WR) AS WR, MAX(gr.DL) AS DL, MAX(gr.SB) AS SB, MAX(gr.AD) AS AD " +	// FIXME MAX(gr.grid) will have unintended consequences
							"FROM group_right_info gri, group_rights gr, t_struc_parentid ts " +
							"WHERE gri.grid=gr.grid AND ts.uuid=gr.id AND gri.portfolio_id=uuid2bin(?) " +
							"AND (gri.grid=(SELECT grid FROM group_right_info WHERE portfolio_id=uuid2bin(?) AND label='all') OR gri.grid=? OR " +
						"gri.grid=(SELECT grid FROM credential c, group_right_info gri, t_node n WHERE n.node_uuid=uuid2bin(?) AND n.portfolio_id=gri.portfolio_id AND c.login=gri.label AND c.userid=?)) ";
					sql += " GROUP BY gr.id) s " +
							"ON (d.grid = s.grid AND d.id = s.id) " +
							"WHEN MATCHED THEN UPDATE SET " +
							"d.RD=GREATEST(d.RD,s.RD), " +
							"d.WR=GREATEST(d.WR,s.WR), " +
							"d.DL=GREATEST(d.DL,s.DL), " +
							"d.SB=GREATEST(d.SB,s.SB), " +
							"d.AD=GREATEST(d.AD,s.AD)";
					sql += " WHEN NOT MATCHED THEN INSERT (d.grid, d.id, d.RD, d.WR, d.DL, d.SB, d.AD) VALUES (s.grid, s.id, s.RD, s.WR, s.DL, s.SB, s.AD)";
				}
				st = c.prepareStatement(sql);
				st.setString(1, portfolioid);
				st.setString(2, portfolioid);
				st.setInt(3, rrgId);
				st.setString(4, nodeUuid);
				st.setInt(5, userId);
//				System.out.println("VALUES: "+portfolioid+" "+rrgId+" "+nodeUuid+" "+userId);
			}
			st.executeUpdate();
			st.close();

			long t_allRights = System.currentTimeMillis();

			// Sélectionne les données selon la filtration
			sql = "SELECT bin2uuid(n.node_uuid) AS node_uuid," +
					" n.node_children_uuid, " +
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
					" tr.RD, tr.WR, tr.SB, tr.DL, NULL AS types_id, NULL AS rules_id," +   // info sur les droits
					" bin2uuid(n.portfolio_id) AS portfolio_id" +
					" FROM node n" +	// Going back to original table, mainly for list of child nodes
					" LEFT JOIN resource_table r1 ON n.res_node_uuid=r1.node_uuid" +         // Récupération des données res_node
					" LEFT JOIN resource_table r2 ON n.res_res_node_uuid=r2.node_uuid" +     // Récupération des données res_res_node
					" LEFT JOIN resource_table r3 ON n.res_context_node_uuid=r3.node_uuid" + // Récupération des données res_context
					" LEFT JOIN t_rights_22 tr" +     // Vérification des droits
					" ON n.node_uuid=tr.id" +   // On doit au moins avoir le droit de lecture
					" WHERE tr.RD=1 AND n.node_uuid IN (SELECT uuid FROM t_struc_parentid)";   // Selon note filtrage, prendre les noeud nécéssaire

			st = c.prepareStatement(sql);
			res = st.executeQuery();

			long t_aggregate = System.currentTimeMillis();

			/*
			long d_tempTable = t_tempTable - t_start;
			long d_initData = t_dataTable - t_tempTable;
			long d_initRecusion = t_initNode - t_dataTable;
			long d_initLoop = t_initLoop - t_initNode;
			long d_endLoop = t_endLoop - t_initLoop;
			long d_fetchRights = t_allRights - t_endLoop;
			long d_aggregateInfo = t_aggregate - t_allRights;

			System.out.println("===== Get node per level ====");
			System.out.println("Temp table creation: "+d_tempTable);
			System.out.println("Init data: "+d_initData);
			System.out.println("Init node recursion: "+d_initRecusion);
			System.out.println("Init queries recursion: "+d_initLoop);
			System.out.println("End loop: "+d_endLoop);
			System.out.println("Add 'all' rights: "+d_fetchRights);
			System.out.println("Aggregate info: "+d_aggregateInfo);
			//*/
		}
		catch( SQLException e )
		{
			e.printStackTrace();
			logger.error(e.getMessage());
		}
		finally
		{
			try
			{
				// Les 'pooled connection' ne se ferment pas vraiment. On nettoie manuellement les tables temporaires...
				if (dbserveur.equals("mysql")){
					sql = "DROP TEMPORARY TABLE IF EXISTS t_node, t_rights_22, t_struc_parentid, t_struc_parentid_2";
					st = c.prepareStatement(sql);
					st.execute();
					st.close();
				}
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}

		return res;
	}

	private  StringBuffer getNodeXmlListOutput(Connection c, String nodeUuid,boolean withChildren,int userId, int groupId) throws SQLException
	{
		StringBuffer result = new StringBuffer();

		if(!cred.hasNodeRight(c, userId,groupId,nodeUuid, Credential.READ))
			return result;

		ResultSet resNode = getMysqlNode(c, nodeUuid,userId, groupId);

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
								result.append(getNodeXmlListOutput(c, arrayChild[i],true, userId, groupId));
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
	public Object getNode(Connection c, MimeType outMimeType, String nodeUuid,boolean withChildren, int userId,int groupId, String label) throws SQLException, TransformerFactoryConfigurationError, ParserConfigurationException, UnsupportedEncodingException, DOMException, SAXException, IOException, TransformerException
	{
		StringBuffer nodexml = new StringBuffer();

		long t_start = System.currentTimeMillis();

		NodeRight nodeRight = cred.getNodeRight(c, userId,groupId,nodeUuid, label);

		long t_nodeRight = System.currentTimeMillis();

		if(!nodeRight.read)
		{
			userId = cred.getPublicUid(c);
			/// Vérifie les droits avec le compte publique (dernière chance)
			cred.getPublicRight(c, userId, 123, nodeUuid, "dummy");

			if( !nodeRight.read )
				return nodexml;
		}

		if(outMimeType.getSubType().equals("xml"))
		{
			ResultSet result = getNodePerLevel(c, nodeUuid, userId, nodeRight.rrgId);

			long t_nodePerLevel = System.currentTimeMillis();

			/// Préparation du XML que l'on va renvoyer
			DocumentBuilderFactory documentBuilderFactory =DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder = null;
			Document document=null;
			documentBuilder = documentBuilderFactory.newDocumentBuilder();
			document = documentBuilder.newDocument();
			document.setXmlStandalone(true);

			HashMap<String, Object[]> resolve = new HashMap<String, Object[]>();
			/// Node -> parent
			HashMap<String, t_tree> entries = new HashMap<String, t_tree>();

			long t_initContruction = System.currentTimeMillis();

			processQuery(result, resolve, entries, document, documentBuilder, nodeRight.groupLabel);
			result.close();

			long t_processQuery = System.currentTimeMillis();

			/// Reconstruct functional tree
			t_tree root = entries.get(nodeUuid);
			StringBuilder out = new StringBuilder(256);
			reconstructTree( out, root, entries );

			/// Reconstruct data
//			String output = reconstructData(root);

			/*
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
			//*/

			nodexml.append(out.toString());
			long t_buildXML = System.currentTimeMillis();

			/*
			Node root = resolve.get(nodeUuid);
			Node node = document.createElement("node");
			node.appendChild(root);
			document.appendChild(node);

			StringWriter stw = new StringWriter();
			Transformer serializer = TransformerFactory.newInstance().newTransformer();
			serializer.transform(new DOMSource(document), new StreamResult(stw));
			nodexml.append(stw.toString());
			//*/

			long t_convertString = System.currentTimeMillis();

			/*
			long d_right = t_nodeRight - t_start;
			long d_queryNodes = t_nodePerLevel - t_nodeRight;
			long d_initConstruct = t_initContruction - t_nodePerLevel;
			long d_processQuery = t_processQuery - t_initContruction;
			long d_buildXML = t_buildXML - t_processQuery;
			long d_convertString = t_convertString - t_buildXML;

			System.out.println("Query Rights: "+d_right);
			System.out.println("Query Nodes: "+d_queryNodes);
			System.out.println("Init build: "+d_initConstruct);
			System.out.println("Parse Query: "+d_processQuery);
			System.out.println("Build XML: "+d_buildXML);
			System.out.println("Convert XML: "+d_convertString);
			//*/

			//		  StringBuffer sb = getNodeXmlOutput(nodeUuid,withChildren,null,userId, groupId, label,true);
			//          StringBuffer sb = getLinearNodeXml(nodeUuid,withChildren,null,userId, groupId, label,true);

			//		  sb.insert(0, "<node>");
			//		  sb.append("</node>");
			return nodexml;
		}
		else if(outMimeType.getSubType().equals("json"))
			return "{"+getNodeJsonOutput(c, nodeUuid,withChildren,null,userId, groupId,label,true)+"}";
		else
			return null;
	}

	@Override
	public Object deleteNode(Connection c, String nodeUuid, int userId, int groupId)
	{
		long t1=0, t2=0, t3=0, t4=0, t5=0, t6=0;
		long t0 = System.currentTimeMillis();
		
		NodeRight nodeRight = cred.getNodeRight(c, userId,groupId,nodeUuid, Credential.DELETE);

		if(!nodeRight.delete)
			if(!cred.isAdmin(c, userId) && !cred.isDesigner(c, userId, nodeUuid))
				throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		t1 = System.currentTimeMillis();
		
		PreparedStatement st;
		String sql = "";
		int result = 0;
		String parentid = "";
		try
		{
			/// Temp table for node ids, so we can traverse from here
			if (dbserveur.equals("mysql")){
				sql = "CREATE TEMPORARY TABLE IF NOT EXISTS t_node(" +
						"node_uuid binary(16)  NOT NULL, " +
						"node_parent_uuid binary(16) DEFAULT NULL, " +
						"node_order int(12) NOT NULL, " +
						"res_node_uuid binary(16) DEFAULT NULL, " +
						"res_res_node_uuid binary(16) DEFAULT NULL, " +
						"res_context_node_uuid binary(16)  DEFAULT NULL, " +
						"shared_res int(1) NOT NULL, " +
						"shared_node int(1) NOT NULL, " +
						"shared_node_res int(1) NOT NULL, " +
						"shared_res_uuid BINARY(16)  NULL, " +
						"shared_node_uuid BINARY(16) NULL, " +
						"shared_node_res_uuid BINARY(16) NULL, " +
						"portfolio_id binary(16) DEFAULT NULL) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")){
				String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_node(" +
						"node_uuid RAW(16)  NOT NULL, " +
						"node_parent_uuid RAW(16) DEFAULT NULL, " +
						"node_order NUMBER(12) NOT NULL, " +
						"res_node_uuid RAW(16) DEFAULT NULL, " +
						"res_res_node_uuid RAW(16) DEFAULT NULL, " +
						"res_context_node_uuid RAW(16)  DEFAULT NULL, " +
						"shared_res NUMBER(1) NOT NULL, " +
						"shared_node NUMBER(1) NOT NULL, " +
						"shared_node_res NUMBER(1) NOT NULL, " +
						"shared_res_uuid RAW(16) DEFAULT NULL, " +
						"shared_node_uuid RAW(16) DEFAULT NULL, " +
						"shared_node_res_uuid RAW(16) DEFAULT NULL, " +
						"portfolio_id RAW(16) DEFAULT NULL) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_node','"+v_sql+"')}";
				CallableStatement ocs = c.prepareCall(sql) ;
				ocs.execute();
				ocs.close();
			}

			/// Pour le filtrage de la structure
			if (dbserveur.equals("mysql")){
				sql = "CREATE TEMPORARY TABLE t_struc_node_resids(" +
						"uuid binary(16) UNIQUE NOT NULL, " +
						"node_parent_uuid binary(16), " +
						"res_node_uuid binary(16), " +
						"res_res_node_uuid binary(16), " +
						"res_context_node_uuid binary(16), " +
						"t_level INT) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")){
				String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_struc_node_resids(" +
						"uuid RAW(16) NOT NULL, " +
						"node_parent_uuid RAW(16), " +
						"res_node_uuid RAW(16), " +
						"res_res_node_uuid RAW(16), " +
						"res_context_node_uuid RAW(16), " +
						"t_level NUMBER(10,0)"+
						",  CONSTRAINT t_struc_node_resids_UK_uuid UNIQUE (uuid)) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_struc_node_resids','"+v_sql+"')}";
				CallableStatement ocs = c.prepareCall(sql) ;
				ocs.execute();
				ocs.close();
			}

			if (dbserveur.equals("mysql")){
				sql = "CREATE TEMPORARY TABLE t_struc_node_resids_2(" +
						"uuid binary(16) UNIQUE NOT NULL, " +
						"node_parent_uuid binary(16) NOT NULL, " +
						"res_node_uuid binary(16), " +
						"res_res_node_uuid binary(16), " +
						"res_context_node_uuid binary(16), " +
						"t_level INT) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")){
				String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_struc_node_resids_2(" +
						"uuid RAW(16) NOT NULL, " +
						"node_parent_uuid RAW(16), " +
						"res_node_uuid RAW(16), " +
						"res_res_node_uuid RAW(16), " +
						"res_context_node_uuid RAW(16), " +
						"t_level NUMBER(10,0)"+
						",  CONSTRAINT t_struc_node_resids_2_UK_uuid UNIQUE (uuid)) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_struc_node_resids_2','"+v_sql+"')}";
				CallableStatement ocs = c.prepareCall(sql) ;
				ocs.execute();
				ocs.close();
			}

			/// Pour le filtrage des ressources
			if (dbserveur.equals("mysql")){
				sql = "CREATE TEMPORARY TABLE t_res_uuid(" +
						"uuid binary(16) UNIQUE NOT NULL) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")){
				String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_res_uuid(" +
						"uuid RAW(16) NOT NULL, " +
						"  CONSTRAINT t_res_UK_uuid UNIQUE (uuid)) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_res_uuid','"+v_sql+"')}";
				CallableStatement ocs = c.prepareCall(sql) ;
				ocs.execute();
				ocs.close();
			}

			t2 = System.currentTimeMillis();

			/// Copy portfolio base info
			sql = "INSERT INTO t_node(node_uuid, node_parent_uuid, node_order, res_node_uuid, res_res_node_uuid, res_context_node_uuid, shared_res, shared_node, shared_node_res, shared_res_uuid, shared_node_uuid, shared_node_res_uuid, portfolio_id) " +
					"SELECT node_uuid, node_parent_uuid, node_order, res_node_uuid, res_res_node_uuid, res_context_node_uuid, shared_res, shared_node, shared_node_res, shared_res_uuid, shared_node_uuid, shared_node_res_uuid, portfolio_id " +
					"FROM node WHERE portfolio_id=(SELECT portfolio_id FROM node WHERE node_uuid=uuid2bin(?))";
			st = c.prepareStatement(sql);
			st.setString(1, nodeUuid);
			st.execute();
			st.close();
			
			/// Find parent for re-ordering the remaining childs
			sql = "SELECT bin2uuid(node_parent_uuid) FROM t_node WHERE node_uuid=uuid2bin(?)";
			st = c.prepareStatement(sql);
			st.setString(1, nodeUuid);
			ResultSet res = st.executeQuery();
			if( res.next() )
				parentid = res.getString("bin2uuid(node_parent_uuid)");
			res.close();
			st.close();
			

			/// Liste les noeud a filtrer
			// Initiale
			sql = "INSERT INTO t_struc_node_resids(uuid, node_parent_uuid, res_node_uuid, res_res_node_uuid, res_context_node_uuid, t_level) " +
					"SELECT node_uuid, node_parent_uuid, res_node_uuid, res_res_node_uuid, res_context_node_uuid, 0 " +
					"FROM t_node WHERE node_uuid=uuid2bin(?)";
			st = c.prepareStatement(sql);
			st.setString(1, nodeUuid);
			st.executeUpdate();
			st.close();

			t3 = System.currentTimeMillis();

			/// On descend les noeuds
			int level = 0;
			int added = 1;
			if (dbserveur.equals("mysql")){
				sql = "INSERT IGNORE INTO t_struc_node_resids_2(uuid, node_parent_uuid, res_node_uuid, res_res_node_uuid, res_context_node_uuid, t_level) ";
				} else if (dbserveur.equals("oracle")){
					sql = "INSERT /*+ ignore_row_on_dupkey_index(t_struc_node_resids_2,t_struc_node_resids_2_UK_uuid)*/ INTO t_struc_node_resids_2(uuid, node_parent_uuid, res_node_uuid, res_res_node_uuid, res_context_node_uuid, t_level) ";
				}
				sql += "SELECT node_uuid, node_parent_uuid, res_node_uuid, res_res_node_uuid, res_context_node_uuid, ? " +
					"FROM t_node WHERE node_parent_uuid IN (SELECT uuid FROM t_struc_node_resids t " +
					"WHERE t.t_level=?)";
			st = c.prepareStatement(sql);

			String sqlTemp=null;
			if (dbserveur.equals("mysql")){
				sqlTemp = "INSERT IGNORE INTO t_struc_node_resids SELECT * FROM t_struc_node_resids_2;";
			} else if (dbserveur.equals("oracle")){
				sqlTemp = "INSERT /*+ ignore_row_on_dupkey_index(t_struc_node_resids,t_struc_node_resids_UK_uuid)*/ INTO t_struc_node_resids SELECT * FROM t_struc_node_resids_2";
			}
			PreparedStatement stTemp = c.prepareStatement(sqlTemp);

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

			t4 = System.currentTimeMillis();

			/// On liste les ressources à effacer
			if (dbserveur.equals("mysql")){
				sql = "INSERT INTO t_res_uuid(uuid) SELECT res_node_uuid FROM t_struc_node_resids WHERE res_node_uuid <> 0x0000000000000000000000000000000";
			} else if (dbserveur.equals("oracle")){
				sql = "INSERT INTO t_res_uuid(uuid) SELECT res_node_uuid FROM t_struc_node_resids WHERE res_node_uuid <> '00000000000000000000000000000000'";
			}
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			if (dbserveur.equals("mysql")){
				sql = "INSERT INTO t_res_uuid(uuid) SELECT res_res_node_uuid FROM t_struc_node_resids WHERE res_res_node_uuid <> 0x0000000000000000000000000000000";
			} else if (dbserveur.equals("oracle")){
				sql = "INSERT INTO t_res_uuid(uuid) SELECT res_res_node_uuid FROM t_struc_node_resids WHERE res_res_node_uuid <> '00000000000000000000000000000000'";
			}
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			if (dbserveur.equals("mysql")){
				sql = "INSERT INTO t_res_uuid(uuid) SELECT res_context_node_uuid FROM t_struc_node_resids WHERE res_context_node_uuid <> 0x0000000000000000000000000000000";
			} else if (dbserveur.equals("oracle")){
				sql = "INSERT INTO t_res_uuid(uuid) SELECT res_context_node_uuid FROM t_struc_node_resids WHERE res_context_node_uuid <> '00000000000000000000000000000000'";
			}
			if (dbserveur.equals("mysql")){
			} else if (dbserveur.equals("oracle")){
			}
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			t5 = System.currentTimeMillis();

			c.setAutoCommit(false);
			/// Update last changed date, doing it first since uuid won't exist anymore
			touchPortfolio(c, nodeUuid, null);

			/// On efface
			// Les ressources
			if( "mysql".equals(dbserveur) )
			{
				sql = "DELETE rt FROM t_res_uuid tru LEFT JOIN resource_table rt ON tru.uuid=rt.node_uuid";
			}
			else	// FIXME Not sure if it's correct
			{
				sql = "DELETE resource_table WHERE (node_uuid) IN (SELECT uuid FROM t_res_uuid)";
			}
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			// Les noeuds
			if( "mysql".equals(dbserveur) )
			{
				sql = "DELETE n FROM t_struc_node_resids tsnr LEFT JOIN node n ON tsnr.uuid=n.node_uuid";
			}
			else	// FIXME Not sure if it's correct
			{
				sql = "DELETE node WHERE (node_uuid) IN (SELECT uuid FROM t_struc_node_resids)";
			}
			st = c.prepareStatement(sql);
			result = st.executeUpdate();
			st.close();
			
			t6 = System.currentTimeMillis();

			/*
			long checkRights = t1-t0;
			long initstuff = t2-t1;
			long insertbase = t3-t2;
			long traversetree = t4-t3;
			long listresource = t5-t4;
			long purge = t6-t5;
			System.out.println("=====DELETE=====");
			System.out.println("Check rights: "+checkRights);
			System.out.println("Initialize: "+initstuff);
			System.out.println("Insert data: "+insertbase);
			System.out.println("Traverse: "+traversetree);
			System.out.println("List res: "+listresource);
			System.out.println("Delete: "+purge);
			//*/
		}
		catch( Exception e )
		{
			try
			{
				if( c.getAutoCommit() == false )
					c.rollback();
			}
			catch( SQLException e1 ){ e1.printStackTrace(); }
			e.printStackTrace();
		}
		finally
		{
			try
			{
				c.setAutoCommit(true);
				// Les 'pooled connection' ne se ferment pas vraiment. On nettoie manuellement les tables temporaires...
				if (dbserveur.equals("mysql")){
					sql = "DROP TEMPORARY TABLE IF EXISTS t_node, t_struc_node_resids, t_struc_node_resids_2, t_res_uuid";
					st = c.prepareStatement(sql);
					st.execute();
					st.close();
				}
				updateMysqlNodeChildren(c, parentid);

				c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}

		System.out.println("deleteNode :"+nodeUuid);

		return result;
	}

	@Override
	public Object postInstanciatePortfolio(Connection c, MimeType inMimeType, String portfolioUuid, String srcCode, String newCode, int userId, int groupId, boolean copyshared, String portfGroupName, boolean setOwner ) throws Exception
	{
		if( !cred.isAdmin(c, userId) && !cred.isCreator(c, userId) ) return "no rights";
		
		String sql = "";
		PreparedStatement st;
		String newPortfolioUuid = UUID.randomUUID().toString();
		boolean setPublic = false;

		try
		{
			/// Find source code
			if( srcCode != null )
			{
				/// Find back portfolio uuid from source code
				sql = "SELECT bin2uuid(portfolio_id) AS uuid FROM node WHERE code=?";
				st = c.prepareStatement(sql);
				st.setString(1, srcCode);
				ResultSet res = st.executeQuery();
				if( res.next() )
					portfolioUuid = res.getString("uuid");
			}

			if( portfolioUuid == null )
				return "";

			///// Création des tables temporaires
			/// Pour la copie de la structure
			if (dbserveur.equals("mysql")){
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
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")){
				String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_data(" +
						"new_uuid RAW(16) NOT NULL, " +  /// Pour la copie d'une nouvelle structure
						"node_uuid RAW(16)  NOT NULL, " +
						"node_parent_uuid RAW(16) DEFAULT NULL, " +
						"node_children_uuid CLOB, " +
						"node_order NUMBER(12) NOT NULL, " +
						"metadata CLOB DEFAULT NULL, " +
						"metadata_wad CLOB DEFAULT NULL, " +
						"metadata_epm CLOB DEFAULT NULL, " +
						"res_node_uuid RAW(16) DEFAULT NULL, " +
						"res_res_node_uuid RAW(16) DEFAULT NULL, " +
						"res_context_node_uuid RAW(16)  DEFAULT NULL, " +
						"shared_res NUMBER(1) NOT NULL, " +
						"shared_node NUMBER(1) NOT NULL, " +
						"shared_node_res NUMBER(1) NOT NULL, " +
						"shared_res_uuid RAW(16) DEFAULT NULL, " +
						"shared_node_uuid RAW(16) DEFAULT NULL, " +
						"shared_node_res_uuid RAW(16) DEFAULT NULL, " +
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
						"portfolio_id RAW(16) DEFAULT NULL) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_data','"+v_sql+"')}";
				CallableStatement ocs = c.prepareCall(sql) ;
				ocs.execute();
				ocs.close();
			}

			/// Pour la copie des données
			if (dbserveur.equals("mysql")){
				sql = "CREATE TEMPORARY TABLE t_res(" +
						"new_uuid binary(16) NOT NULL, " +  /// Pour la copie d'une nouvelle structure
						"node_uuid binary(16) NOT NULL, " +
						"xsi_type varchar(50) NOT NULL, " +
						"content text, " +
						"user_id int(11) DEFAULT NULL, " +
						"modif_user_id int(12) NOT NULL, " +
						"modif_date timestamp NULL DEFAULT NULL) DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")){
				String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_res(" +
						"new_uuid RAW(16) NOT NULL, " +  /// Pour la copie d'une nouvelle structure
						"node_uuid RAW(16) NOT NULL, " +
						"xsi_type VARCHAR2(50 CHAR) NOT NULL, " +
						"content CLOB, " +
						"user_id NUMBER(11) DEFAULT NULL, " +
						"modif_user_id NUMBER(12) NOT NULL, " +
						"modif_date timestamp DEFAULT NULL) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_res','"+v_sql+"')}";
				CallableStatement ocs = c.prepareCall(sql) ;
				ocs.execute();
				ocs.close();
			}

			/// Pour la mise à jour de la liste des enfants/parents
			if (dbserveur.equals("mysql")){
				sql = "CREATE TEMPORARY TABLE t_struc(" +
						"node_order int(12) NOT NULL, " +
						"new_uuid binary(16) NOT NULL, " +
						"uuid binary(16) UNIQUE NOT NULL, " +
						"node_parent_uuid binary(16), " +
						"t_level INT) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")){
				String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_struc(" +
						"node_order NUMBER(12) NOT NULL, " +
						"new_uuid RAW(16) NOT NULL, " +
						"uuid RAW(16) NOT NULL, " +
						"node_parent_uuid RAW(16), " +
						"t_level NUMBER(10,0)"+
						",  CONSTRAINT t_struc_UK_uuid UNIQUE (uuid)) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_struc','"+v_sql+"')}";
				CallableStatement ocs = c.prepareCall(sql) ;
				ocs.execute();
				ocs.close();
			}

			/// Pour l'histoire des shared_node a filtrer
			if (dbserveur.equals("mysql")){
				sql = "CREATE TEMPORARY TABLE t_struc_2(" +
						"node_order int(12) NOT NULL, " +
						"new_uuid binary(16) NOT NULL, " +
						"uuid binary(16) UNIQUE NOT NULL, " +
						"node_parent_uuid binary(16), " +
						"t_level INT) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")){
				String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_struc_2(" +
						"node_order NUMBER(12) NOT NULL, " +
						"new_uuid RAW(16) NOT NULL, " +
						"uuid RAW(16) NOT NULL, " +
						"node_parent_uuid RAW(16), " +
						"t_level NUMBER(10,0)"+
						",  CONSTRAINT t_struc_2_UK_uuid UNIQUE (uuid)) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_struc_2','"+v_sql+"')}";
				CallableStatement ocs = c.prepareCall(sql) ;
				ocs.execute();
				ocs.close();
			}

			/// Pour les nouveaux ensembles de droits
			if (dbserveur.equals("mysql")){
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
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")){
				String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_rights(" +
						"grid NUMBER(19,0) NOT NULL, " +
						"id RAW(16) NOT NULL, " +
						"RD NUMBER(1) NOT NULL, " +
						"WR NUMBER(1) NOT NULL, " +
						"DL NUMBER(1) NOT NULL, " +
						"SB NUMBER(1) NOT NULL, " +
						"AD NUMBER(1) NOT NULL, " +
						"types_id VARCHAR2(2000 CHAR), " +
						"rules_id VARCHAR2(2000 CHAR)) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_rights','"+v_sql+"')}";
				CallableStatement ocs = c.prepareCall(sql) ;
				ocs.execute();
				ocs.close();
			}

			/// Copie de la structure
			sql = "INSERT INTO t_data(new_uuid, node_uuid, node_parent_uuid, node_children_uuid, node_order, metadata, metadata_wad, metadata_epm, res_node_uuid, res_res_node_uuid, res_context_node_uuid , shared_res, shared_node, shared_node_res, shared_res_uuid, shared_node_uuid, shared_node_res_uuid, asm_type, xsi_type, semtag, semantictag, label, code, descr, format, modif_user_id, modif_date, portfolio_id) ";
			if (dbserveur.equals("mysql")){
				sql += "SELECT uuid2bin(UUID()), ";
			} else if (dbserveur.equals("oracle")){
				sql += "SELECT sys_guid(), ";
			}
			sql += "node_uuid, node_parent_uuid, node_children_uuid, node_order, metadata, metadata_wad, metadata_epm, res_node_uuid, res_res_node_uuid, res_context_node_uuid , shared_res, shared_node, shared_node_res, shared_res_uuid, shared_node_uuid, shared_node_res_uuid, asm_type, xsi_type, semtag, semantictag, label, code, descr, format, ?, modif_date, portfolio_id " +
					"FROM node n " +
					"WHERE portfolio_id=uuid2bin(?)";
			st = c.prepareStatement(sql);
			st.setInt(1, userId);	// User asking to instanciate a portfolio will always have the right to modify it
			st.setString(2, portfolioUuid);

			st.executeUpdate();
			st.close();

			if( !copyshared )
			{
				/// Liste les noeud a filtrer
				sql = "INSERT INTO t_struc(node_order, new_uuid, uuid, node_parent_uuid, t_level) " +
						"SELECT node_order, new_uuid, node_uuid, node_parent_uuid, 0 FROM t_data WHERE shared_node=1";
				st = c.prepareStatement(sql);
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
				PreparedStatement stTemp = c.prepareStatement(sqlTemp);

				st = c.prepareStatement(sql);
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
				sql = "DELETE FROM t_struc WHERE uuid IN (SELECT node_uuid FROM t_data WHERE shared_node=1)";
				st = c.prepareStatement(sql);
				st.executeUpdate();
				st.close();

				sql = "DELETE FROM t_data WHERE node_uuid IN (SELECT uuid FROM t_struc)";
				st = c.prepareStatement(sql);
				st.executeUpdate();
				st.close();

				sql = "DELETE FROM t_struc";
				st = c.prepareStatement(sql);
				st.executeUpdate();
				st.close();

			}

			/// Copie les uuid pour la résolution des parents/enfants
			sql = "INSERT INTO t_struc(node_order, new_uuid, uuid, node_parent_uuid) " +
					"SELECT node_order, new_uuid, node_uuid, node_parent_uuid FROM t_data";
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			if( !copyshared )
			{
				/// Cas spécial pour shared_node=1
				// Le temps qu'on refasse la liste des enfants, on va enlever le noeud plus tard
				sql = "UPDATE t_data SET shared_node_uuid=node_uuid WHERE shared_node=1";
				st = c.prepareStatement(sql);
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
				st = c.prepareStatement(sql);
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
			st = c.prepareStatement(sql);
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
			st = c.prepareStatement(sql);
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
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			/// Changement du uuid du portfolio
			sql = "UPDATE t_data t SET t.portfolio_id=uuid2bin(?)";
			st = c.prepareStatement(sql);
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
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			sql = "UPDATE t_data t " +
					"SET t.node_parent_uuid = (SELECT new_uuid FROM t_struc s WHERE s.uuid=t.node_parent_uuid)";
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			// Avec les ressources (et droits des ressources)
			if (dbserveur.equals("mysql")){
				sql = "UPDATE t_rights ri, t_res re SET ri.id = re.new_uuid WHERE re.node_uuid=ri.id";
			} else if (dbserveur.equals("oracle")){
				sql = "UPDATE t_rights ri SET ri.id=(SELECT new_uuid FROM t_res re WHERE re.node_uuid=ri.id) WHERE EXISTS (SELECT 1 FROM t_res re WHERE re.node_uuid=ri.id)";
			}
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			if (dbserveur.equals("mysql")){
				sql = "UPDATE t_data d INNER JOIN t_res r ON d.res_node_uuid=r.node_uuid " +
					"SET d.res_node_uuid=r.new_uuid";
			} else if (dbserveur.equals("oracle")){
				sql = "UPDATE t_data d SET d.res_node_uuid=(SELECT r.new_uuid FROM t_res r WHERE d.res_node_uuid=r.node_uuid) WHERE EXISTS (SELECT 1 FROM t_res r WHERE d.res_node_uuid=r.node_uuid)";
			}
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			if (dbserveur.equals("mysql")){
				sql = "UPDATE t_data d INNER JOIN t_res r ON d.res_res_node_uuid=r.node_uuid " +
					"SET d.res_res_node_uuid=r.new_uuid";
			} else if (dbserveur.equals("oracle")){
				sql = "UPDATE t_data d SET d.res_res_node_uuid=(SELECT r.new_uuid FROM t_res r WHERE d.res_res_node_uuid=r.node_uuid) WHERE EXISTS (SELECT 1 FROM t_res r WHERE d.res_res_node_uuid=r.node_uuid)";
			}
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			if (dbserveur.equals("mysql")){
				sql = "UPDATE t_data d INNER JOIN t_res r ON d.res_context_node_uuid=r.node_uuid " +
					"SET d.res_context_node_uuid=r.new_uuid";
			} else if (dbserveur.equals("oracle")){
				sql = "UPDATE t_data d SET d.res_context_node_uuid=(SELECT r.new_uuid FROM t_res r WHERE d.res_context_node_uuid=r.node_uuid) WHERE EXISTS (SELECT 1 FROM t_res r WHERE d.res_context_node_uuid=r.node_uuid)";
			}
			st = c.prepareStatement(sql);
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
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			// Mise à jour du code dans le contenu du noeud (blech)
			if (dbserveur.equals("mysql")){
				sql = "UPDATE t_data d " +
					"LEFT JOIN t_res r ON d.res_res_node_uuid=r.new_uuid " +  // Il faut utiliser le nouveau uuid
					"SET r.content=REPLACE(r.content, d.code, ?) " +
					"WHERE d.asm_type='asmRoot'";
			} else if (dbserveur.equals("oracle")){
				sql = "UPDATE t_res r SET r.content=(SELECT REPLACE(r2.content, d.code, ?) FROM t_data d LEFT JOIN t_res r2 ON d.res_res_node_uuid=r2.new_uuid WHERE d.asm_type='asmRoot') WHERE EXISTS (SELECT 1 FROM t_data d WHERE d.res_res_node_uuid=r.new_uuid AND d.asm_type='asmRoot')";
			}
			st = c.prepareStatement(sql);
			st.setString(1, newCode);
			st.executeUpdate();
			st.close();

			// Mise à jour du code dans le code interne de la BD
			sql = "UPDATE t_data d SET d.code=? WHERE d.asm_type='asmRoot'";
			st = c.prepareStatement(sql);
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
			st = c.prepareStatement(sql);
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
					att = attribMap.getNamedItem("submitroles");
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

					// Check if we have to put the portfolio as public
					meta = res.getString("metadata");
					nodeString = "<?xml version='1.0' encoding='UTF-8' standalone='no'?><transfer "+meta+"/>";
					is = new InputSource(new StringReader(nodeString));
					doc = documentBuilder.parse(is);
					attribNode = doc.getDocumentElement();
					attribMap = attribNode.getAttributes();
					Node publicatt = attribMap.getNamedItem("public");
					if( publicatt != null && "Y".equals(publicatt.getNodeValue()) )
						setPublic = true;
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

			c.setAutoCommit(false);

			/// On insère les données pré-compilé
			Iterator<String> entries = resolve.groups.keySet().iterator();

			// Créé les groupes, ils n'existent pas
			String grquery = "INSERT INTO group_info(grid,owner,label) " +
					"VALUES(?,?,?)";
			PreparedStatement st2 = c.prepareStatement(grquery);
			String gri = "INSERT INTO group_right_info(owner, label, change_rights, portfolio_id) " +
					"VALUES(?,?,?,uuid2bin(?))";
			if( "mysql".equals(dbserveur) )
				st = c.prepareStatement(gri, Statement.RETURN_GENERATED_KEYS);
			if (dbserveur.equals("oracle"))
				st = c.prepareStatement(gri, new String[]{"grid"});

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
			st2.close();
			st.close();

			/// Ajout des droits des noeuds
			String insertRight = "INSERT INTO group_rights(grid, id, RD, WR, DL, SB, AD, types_id, rules_id, notify_roles) " +
					"VALUES(?,uuid2bin(?),?,?,?,?,?,?,?,?)";
			st = c.prepareStatement(insertRight);

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
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			/// Resources
			sql = "INSERT INTO resource_table(node_uuid, xsi_type, content, user_id, modif_user_id, modif_date) " +
					"SELECT new_uuid, xsi_type, content, user_id, modif_user_id, modif_date " +
					"FROM t_res";
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			/// Ajout du portfolio dans la table
			sql = "INSERT INTO portfolio(portfolio_id, root_node_uuid, user_id, model_id, modif_user_id, modif_date, active) " +
					"SELECT d.portfolio_id, d.new_uuid, ";
			if( setOwner )	// If we asked to change ownership
				sql += "d.modif_user_id";
			else
				sql += "p.user_id";
			sql += ", p.model_id, d.modif_user_id, p.modif_date, p.active " +
					"FROM t_data d INNER JOIN portfolio p " +
					"ON d.node_uuid=p.root_node_uuid";

			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			/*
			/// Ajout du portfolio dans le groupe de portfolio
			if( null == portfGroupName || "".equals(portfGroupName) )
				portfGroupName = "default";

			sql = "INSERT INTO portfolio_group(owner, portfolio_id, group_name) VALUES(?,uuid2bin(?),?)";

			st = c.prepareStatement(sql);
			st.setInt(1, userId);
			st.setString(2, newPortfolioUuid);
			st.setString(3, portfGroupName);
			st.executeUpdate();
			st.close();
			//*/

			/// Finalement on crée un rôle designer
			int groupid = postCreateRole(c, newPortfolioUuid, "designer", userId);

			/// Ajoute la personne dans ce groupe
			putUserGroup(c, Integer.toString(groupid), Integer.toString(userId));

			/// Set portfolio public if needed
			if( setPublic )
				setPublicState(c, userId, newPortfolioUuid, setPublic);
		}
		catch( Exception e )
		{
			logger.error("MESSAGE: "+e.getMessage() +" "+e.getLocalizedMessage());

			try
			{
				newPortfolioUuid = "erreur: "+e.getMessage();
				if( c.getAutoCommit() == false )
					c.rollback();
			}
			catch( SQLException e1 ){ e1.printStackTrace(); }
			e.printStackTrace();
		}
		finally
		{
			try
			{
				c.setAutoCommit(true);
				// Les 'pooled connection' ne se ferment pas vraiment. On nettoie manuellement les tables temporaires...
				if (dbserveur.equals("mysql")){
					sql = "DROP TEMPORARY TABLE IF EXISTS t_data, t_res, t_struc, t_struc_2, t_rights";
					st = c.prepareStatement(sql);
					st.execute();
					st.close();
				}

				c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}

		return newPortfolioUuid;
	}

	@Override
	public Object postCopyPortfolio(Connection c, MimeType inMimeType, String portfolioUuid, String srcCode, String newCode, int userId, boolean setOwner ) throws Exception
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
				st = c.prepareStatement(sql);
				st.setString(1, srcCode);
				ResultSet res = st.executeQuery();
				if( res.next() )
					portfolioUuid = res.getString("uuid");
			}

			if( portfolioUuid == null )
				return "Error: no portofolio selected";

			///// Création des tables temporaires
			/// Pour la copie de la structure
			if (dbserveur.equals("mysql")){
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
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")){
				String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_data(" +
						"new_uuid RAW(16) NOT NULL, " +  /// Pour la copie d'une nouvelle structure
						"node_uuid RAW(16)  NOT NULL, " +
						"node_parent_uuid RAW(16) DEFAULT NULL, " +
						"node_children_uuid CLOB, " +
						"node_order NUMBER(12) NOT NULL, " +
						"metadata CLOB DEFAULT NULL, " +
						"metadata_wad CLOB DEFAULT NULL, " +
						"metadata_epm CLOB DEFAULT NULL, " +
						"res_node_uuid RAW(16) DEFAULT NULL, " +
						"res_res_node_uuid RAW(16) DEFAULT NULL, " +
						"res_context_node_uuid RAW(16)  DEFAULT NULL, " +
						"shared_res NUMBER(1) NOT NULL, " +
						"shared_node NUMBER(1) NOT NULL, " +
						"shared_node_res NUMBER(1) NOT NULL, " +
						"shared_res_uuid RAW(16) DEFAULT NULL, " +
						"shared_node_uuid RAW(16) DEFAULT NULL, " +
						"shared_node_res_uuid RAW(16) DEFAULT NULL, " +
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
						"portfolio_id RAW(16) DEFAULT NULL) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_data','"+v_sql+"')}";
				CallableStatement ocs = c.prepareCall(sql) ;
				ocs.execute();
				ocs.close();
			}

			/// Pour la copie des données
			if (dbserveur.equals("mysql")){
				sql = "CREATE TEMPORARY TABLE t_res(" +
						"new_uuid binary(16) NOT NULL, " +  /// Pour la copie d'une nouvelle structure
						"node_uuid binary(16) NOT NULL, " +
						"xsi_type varchar(50) NOT NULL, " +
						"content text, " +
						"user_id int(11) DEFAULT NULL, " +
						"modif_user_id int(12) NOT NULL, " +
						"modif_date timestamp NULL DEFAULT NULL) DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")){
				String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_res(" +
						"new_uuid RAW(16) NOT NULL, " +  /// Pour la copie d'une nouvelle structure
						"node_uuid RAW(16) NOT NULL, " +
						"xsi_type VARCHAR2(50 CHAR) DEFAULT NULL, " +
						"content CLOB, " +
						"user_id NUMBER(11) DEFAULT NULL, " +
						"modif_user_id NUMBER(12) NOT NULL, " +
						"modif_date timestamp DEFAULT NULL) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_res','"+v_sql+"')}";
				CallableStatement ocs = c.prepareCall(sql) ;
				ocs.execute();
				ocs.close();
			}

			/// Pour la mise à jour de la liste des enfants/parents
			if (dbserveur.equals("mysql")){
				sql = "CREATE TEMPORARY TABLE t_struc(" +
						"node_order int(12) NOT NULL, " +
						"new_uuid binary(16) NOT NULL, " +
						"uuid binary(16) UNIQUE NOT NULL, " +
						"node_parent_uuid binary(16), " +
						"t_level INT) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")){
				String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_struc(" +
						"node_order NUMBER(12) NOT NULL, " +
						"new_uuid RAW(16) NOT NULL, " +
						"uuid RAW(16) NOT NULL, " +
						"node_parent_uuid RAW(16), " +
						"t_level NUMBER(10,0)"+
						",  CONSTRAINT t_struc_UK_uuid UNIQUE (uuid)) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_struc','"+v_sql+"')}";
				CallableStatement ocs = c.prepareCall(sql) ;
				ocs.execute();
				ocs.close();
			}

			/////////
			/// Copie de la structure
			sql = "INSERT INTO t_data(new_uuid, node_uuid, node_parent_uuid, node_children_uuid, node_order, metadata, metadata_wad, metadata_epm, res_node_uuid, res_res_node_uuid, res_context_node_uuid , shared_res, shared_node, shared_node_res, shared_res_uuid, shared_node_uuid, shared_node_res_uuid, asm_type, xsi_type, semtag, semantictag, label, code, descr, format, modif_user_id, modif_date, portfolio_id) ";
			if (dbserveur.equals("mysql")){
				sql += "SELECT uuid2bin(UUID()), ";
			} else if (dbserveur.equals("oracle")){
				sql += "SELECT sys_guid(), ";
			}
			sql += "node_uuid, node_parent_uuid, node_children_uuid, node_order, metadata, metadata_wad, metadata_epm, res_node_uuid, res_res_node_uuid, res_context_node_uuid , shared_res, shared_node, shared_node_res, shared_res_uuid, shared_node_uuid, shared_node_res_uuid, asm_type, xsi_type, semtag, semantictag, label, code, descr, format, ?, modif_date, portfolio_id " +
					"FROM node n " +
					"WHERE portfolio_id=uuid2bin(?)";
			st = c.prepareStatement(sql);
			if( setOwner )
				st.setInt(1, userId);
			else
				st.setInt(1, 1);	// FIXME hard-coded root userid
			st.setString(2, portfolioUuid);
			st.executeUpdate();
			st.close();

			/// Copie les uuid pour la résolution des parents/enfants
			sql = "INSERT INTO t_struc(node_order, new_uuid, uuid, node_parent_uuid) " +
					"SELECT node_order, new_uuid, node_uuid, node_parent_uuid FROM t_data";
			st = c.prepareStatement(sql);
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
			st = c.prepareStatement(sql);
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
			st = c.prepareStatement(sql);
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
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			/// Changement du uuid du portfolio
			sql = "UPDATE t_data t SET t.portfolio_id=uuid2bin(?)";
			st = c.prepareStatement(sql);
			st.setString(1, newPortfolioUuid);
			st.executeUpdate();
			st.close();

			/// Résolution des nouveaux uuid avec les parents
			// Avec la structure
			sql = "UPDATE t_data t " +
					"SET t.node_parent_uuid = (SELECT new_uuid FROM t_struc s WHERE s.uuid=t.node_parent_uuid)";
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			// Avec les ressources (et droits des ressources)
			if (dbserveur.equals("mysql")){
				sql = "UPDATE t_data d INNER JOIN t_res r ON d.res_node_uuid=r.node_uuid " +
					"SET d.res_node_uuid=r.new_uuid";
			} else if (dbserveur.equals("oracle")){
				sql = "UPDATE t_data d SET d.res_node_uuid=(SELECT r.new_uuid FROM t_res r WHERE d.res_node_uuid=r.node_uuid) WHERE EXISTS (SELECT 1 FROM t_res r WHERE d.res_node_uuid=r.node_uuid)";
			}
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			if (dbserveur.equals("mysql")){
				sql = "UPDATE t_data d INNER JOIN t_res r ON d.res_res_node_uuid=r.node_uuid " +
					"SET d.res_res_node_uuid=r.new_uuid";
			} else if (dbserveur.equals("oracle")){
				sql = "UPDATE t_data d SET d.res_res_node_uuid=(SELECT r.new_uuid FROM t_res r WHERE d.res_res_node_uuid=r.node_uuid) WHERE EXISTS (SELECT 1 FROM t_res r WHERE d.res_res_node_uuid=r.node_uuid)";
			}
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			if (dbserveur.equals("mysql")){
				sql = "UPDATE t_data d INNER JOIN t_res r ON d.res_context_node_uuid=r.node_uuid " +
					"SET d.res_context_node_uuid=r.new_uuid";
			} else if (dbserveur.equals("oracle")){
				sql = "UPDATE t_data d SET d.res_context_node_uuid=(SELECT r.new_uuid FROM t_res r WHERE d.res_context_node_uuid=r.node_uuid) WHERE EXISTS (SELECT 1 FROM t_res r WHERE d.res_context_node_uuid=r.node_uuid)";
			}
			st = c.prepareStatement(sql);
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
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			// Mise à jour du code dans le contenu du noeud
			if (dbserveur.equals("mysql")){
				sql = "UPDATE t_data d " +
					"LEFT JOIN t_res r ON d.res_res_node_uuid=r.new_uuid " +  // Il faut utiliser le nouveau uuid
					"SET r.content=REPLACE(r.content, d.code, ?) " +
					"WHERE d.asm_type='asmRoot'";
			} else if (dbserveur.equals("oracle")){
				sql = "UPDATE t_res r SET r.content=(SELECT REPLACE(r2.content, d.code, ?) FROM t_data d LEFT JOIN t_res r2 ON d.res_res_node_uuid=r2.new_uuid WHERE d.asm_type='asmRoot') WHERE EXISTS (SELECT 1 FROM t_data d WHERE d.res_res_node_uuid=r.new_uuid AND d.asm_type='asmRoot')";
			}
			st = c.prepareStatement(sql);
			st.setString(1, newCode);
			st.executeUpdate();
			st.close();

			// Mise à jour du code dans le code interne de la BD
			sql = "UPDATE t_data d SET d.code=? WHERE d.asm_type='asmRoot'";
			st = c.prepareStatement(sql);
			st.setString(1, newCode);
			st.executeUpdate();
			st.close();

			c.setAutoCommit(false);

			/// On copie tout dans les vrai tables
			/// Structure
			sql = "INSERT INTO node(node_uuid, node_parent_uuid, node_children_uuid, node_order, metadata, metadata_wad, metadata_epm, res_node_uuid, res_res_node_uuid, res_context_node_uuid, shared_res, shared_node, shared_node_res, shared_res_uuid, shared_node_uuid, shared_node_res_uuid, asm_type, xsi_type, semtag, semantictag, label, code, descr, format, modif_user_id, modif_date, portfolio_id) " +
					"SELECT new_uuid, node_parent_uuid, node_children_uuid, node_order, metadata, metadata_wad, metadata_epm, res_node_uuid, res_res_node_uuid, res_context_node_uuid, shared_res, shared_node, shared_node_res, shared_res_uuid, shared_node_uuid, shared_node_res_uuid, asm_type, xsi_type, semtag, semantictag, label, code, descr, format, modif_user_id, modif_date, portfolio_id " +
					"FROM t_data";
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			/// Resources
			sql = "INSERT INTO resource_table(node_uuid, xsi_type, content, user_id, modif_user_id, modif_date) " +
					"SELECT new_uuid, xsi_type, content, user_id, modif_user_id, modif_date " +
					"FROM t_res";
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			/// Ajout du portfolio dans la table
			sql = "INSERT INTO portfolio(portfolio_id, root_node_uuid, user_id, model_id, modif_user_id, modif_date, active) " +
					"SELECT d.portfolio_id, d.new_uuid, p.user_id, p.model_id, d.modif_user_id, p.modif_date, p.active " +
					"FROM t_data d INNER JOIN portfolio p " +
					"ON d.node_uuid=p.root_node_uuid";

			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			/// Finalement on crée un rôle designer
			int groupid = postCreateRole(c, newPortfolioUuid, "designer", userId);

			/// Ajoute la personne dans ce groupe
			putUserGroup(c, Integer.toString(groupid), Integer.toString(userId));
		}
		catch( Exception e )
		{
			try
			{
				newPortfolioUuid = "erreur: "+e.getMessage();
				if( c.getAutoCommit() == false )
					c.rollback();
			}
			catch( SQLException e1 ){ e1.printStackTrace(); }
			e.printStackTrace();
		}
		finally
		{
			try
			{
				c.setAutoCommit(true);
				// Les 'pooled connection' ne se ferment pas vraiment. On nettoie manuellement les tables temporaires...
				if (dbserveur.equals("mysql")){
					sql = "DROP TEMPORARY TABLE IF EXISTS t_data, t_res, t_struc";
					st = c.prepareStatement(sql);
					st.execute();
					st.close();
				}

				c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}

		return newPortfolioUuid;
	}

	private String checkCache( Connection c, String code ) throws SQLException
	{
		String sql = "";
		PreparedStatement st;
		
		long t1=0, t1a=0; long t1b=0; long t1c=0; long t1d=0; 
		
		t1 = System.currentTimeMillis();
		
		/// Cache
		if (dbserveur.equals("mysql")){
			sql = "CREATE TABLE IF NOT EXISTS t_node_cache(" +
					"node_uuid binary(16)  NOT NULL, " +
					"node_parent_uuid binary(16) DEFAULT NULL, " +
//					"node_children_uuid varchar(10000), " +	/// FIXME Will break if we try to import a really wide tree
					"node_order int(12) NOT NULL, " +
//					"metadata varchar(255) NOT NULL, " +
					"metadata_wad varchar(2048) NOT NULL, " +
//					"metadata_epm varchar(255) NOT NULL, " +
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
					"portfolio_id binary(16) DEFAULT NULL, " +
					"PRIMARY KEY (`node_uuid`)) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
			st = c.prepareStatement(sql);
			st.execute();
			st.close();
		} else if (dbserveur.equals("oracle")){
			String v_sql = "CREATE GLOBAL TABLE t_node_cache(" +
					"node_uuid RAW(16)  NOT NULL, " +
					"node_parent_uuid RAW(16) DEFAULT NULL, " +
//					"node_children_uuid CLOB, " +
					"node_order NUMBER(12) NOT NULL, " +
//					"metadata CLOB DEFAULT NULL, " +
					"metadata_wad VARCHAR2(2048 CHAR) DEFAULT NULL, " +
//					"metadata_epm CLOB DEFAULT NULL, " +
					"res_node_uuid RAW(16) DEFAULT NULL, " +
					"res_res_node_uuid RAW(16) DEFAULT NULL, " +
					"res_context_node_uuid RAW(16)  DEFAULT NULL, " +
					"shared_res NUMBER(1) NOT NULL, " +
					"shared_node NUMBER(1) NOT NULL, " +
					"shared_node_res NUMBER(1) NOT NULL, " +
					"shared_res_uuid RAW(16) DEFAULT NULL, " +
					"shared_node_uuid RAW(16) DEFAULT NULL, " +
					"shared_node_res_uuid RAW(16) DEFAULT NULL, " +
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
					"portfolio_id RAW(16) DEFAULT NULL) ON COMMIT PRESERVE ROWS";
			sql = "{call create_or_empty_table('t_node_cache','"+v_sql+"')}";
			CallableStatement ocs = c.prepareCall(sql) ;
			ocs.execute();
			ocs.close();
		}
		
		/// Check if we already have the portfolio in cache
		sql = "SELECT bin2uuid(portfolio_id) FROM t_node_cache WHERE code=?";
		st = c.prepareStatement(sql);
		st.setString(1, code);
		ResultSet res = st.executeQuery();
		String portfolioCode = "";

		t1a = System.currentTimeMillis();

		boolean getCache = false;
		boolean updateCache = false;

		if( res.next() )	/// Cache hit
		{
			portfolioCode = res.getString(1);
			System.out.println("CACHE HIT FOR CODE: "+code+" -> "+portfolioCode);
			res.close();
			st.close();

			/// Checking date
			sql = "SELECT c.modif_date " +
					"FROM t_node_cache c, portfolio p " +
					"WHERE c.modif_date = p.modif_date " +
					"AND c.portfolio_id=p.portfolio_id " +
					"AND code=?";
			st = c.prepareStatement(sql);
			st.setString(1, code);
			res = st.executeQuery();
			if( !res.next() )
				updateCache = true;
			res.close();
			st.close();
		}
		else
		{
			res.close();
			st.close();
			getCache=true;
		}

		t1b = System.currentTimeMillis();

		if( updateCache )	/// FIXME: Sync problems
		{
			System.out.println("FLUSH CACHE FOR CODE: "+code+" -> "+portfolioCode);
			sql = "DELETE FROM t_node_cache WHERE portfolio_id=uuid2bin(?)";
			st = c.prepareStatement(sql);
			st.setString(1, portfolioCode);
			st.execute();
			st.close();
			getCache=true;
		}

		t1c = System.currentTimeMillis();

		if( getCache ) 	/// Cache miss, load it
		{
			System.out.println("CACHE MISS FOR CODE: "+code);

			/// Also force last date from the portfolio list to all nodes
			if (dbserveur.equals("mysql")){
				sql = "INSERT IGNORE INTO t_node_cache ";
			} else if (dbserveur.equals("oracle")){
				sql = "INSERT /*+ ignore_row_on_dupkey_index(node_uuid)*/ INTO t_node_cache ";
			}
			sql += "SELECT n.node_uuid, n.node_parent_uuid, n.node_order, n.metadata_wad, n.res_node_uuid, n.res_res_node_uuid, n.res_context_node_uuid, n.shared_res, n.shared_node, n.shared_node_res, n.shared_res_uuid, n.shared_node_uuid, n.shared_node_res_uuid, n.asm_type, n.xsi_type, n.semtag, n.semantictag, n.label, n.code, n.descr, n.format, n.modif_user_id, p.modif_date, n.portfolio_id " +
					"FROM node n, portfolio p " +
					"WHERE n.portfolio_id=p.portfolio_id AND n.portfolio_id=(" +
					"SELECT n1.portfolio_id " +
					"FROM node n1 LEFT JOIN portfolio p ON n1.portfolio_id=p.portfolio_id " +
					"WHERE n1.code=? AND p.active=1)";

			st = c.prepareStatement(sql);
			st.setString(1, code);
			int insertData = st.executeUpdate();
			st.close();

			if( insertData == 0 )	// Code isn't found, no need to go further
				return null;

			sql = "SELECT bin2uuid(portfolio_id) FROM t_node_cache WHERE code=?";
			st = c.prepareStatement(sql);
			st.setString(1, code);
			res = st.executeQuery();

			res.next();
			portfolioCode = res.getString(1);

			res.close();
			st.close();
		}

		t1d = System.currentTimeMillis();

		System.out.println((t1a-t1)+","+(t1b-t1a)+","+(t1c-t1b)+","+(t1d-t1c));
		
		return portfolioCode;
	}
	
	@Override
	public Object postImportNode( Connection c, MimeType inMimeType, String destUuid, String tag, String code, String srcuuid, int userId, int groupId ) throws Exception
	{
		if( "".equals(tag) || tag == null || "".equals(code) || code == null )
		{
			if( srcuuid == null || "".equals(srcuuid) )
				return "erreur";
		}

		String sql = "";
		PreparedStatement st;
		String createdUuid="erreur";

//		/*
		long start = System.currentTimeMillis();
		long t1=0; long t1e=0; long t2=0; long t3=0; long t4=0; long t5=0;
		long t6=0; long t7=0; long t8=0; long t9=0; long t10=0;
		long t11=0; long t12=0; long t13=0; long t14=0; long t15=0;
		long t16=0; long t17=0; long t18=0; long t19=0; long t20=0;
		long t21=0; long t22=0, t23=0;
		long end=0;
		//*/

		try
		{
			/// If we have a uuid specified
			String portfolioCode = "";
			if( srcuuid != null )
			{
				// Check if user has right to read it
				if( !cred.hasNodeRight(c, userId, groupId, srcuuid, Credential.READ) )
						return "No rights";
			}
			else
			{
				/// Check/update cache
				portfolioCode = checkCache(c, code);
				
				if( portfolioCode == null )
					return "Inexistent selection";
			}

			///// Création des tables temporaires
			/// Pour la copie de la structure
			if (dbserveur.equals("mysql")){
				sql = "CREATE TEMPORARY TABLE IF NOT EXISTS t_data_node(" +
						"new_uuid binary(16) NOT NULL, " +  /// Pour la copie d'une nouvelle structure
						"node_uuid binary(16)  NOT NULL, " +
						"node_parent_uuid binary(16) DEFAULT NULL, " +
//						"node_children_uuid varchar(10000), " +	/// FIXME Will break if we try to import a really wide tree
						"node_order int(12) NOT NULL, " +
//						"metadata varchar(255) NOT NULL, " +
						"metadata_wad varchar(2048) NOT NULL, " +
//						"metadata_epm varchar(255) NOT NULL, " +
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
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")){
				String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_data_node(" +
						"new_uuid RAW(16) NOT NULL, " +  /// Pour la copie d'une nouvelle structure
						"node_uuid RAW(16)  NOT NULL, " +
						"node_parent_uuid RAW(16) DEFAULT NULL, " +
//						"node_children_uuid CLOB, " +
						"node_order NUMBER(12) NOT NULL, " +
//						"metadata CLOB DEFAULT NULL, " +
						"metadata_wad VARCHAR2(2048 CHAR) DEFAULT NULL, " +
//						"metadata_epm CLOB DEFAULT NULL, " +
						"res_node_uuid RAW(16) DEFAULT NULL, " +
						"res_res_node_uuid RAW(16) DEFAULT NULL, " +
						"res_context_node_uuid RAW(16)  DEFAULT NULL, " +
						"shared_res NUMBER(1) NOT NULL, " +
						"shared_node NUMBER(1) NOT NULL, " +
						"shared_node_res NUMBER(1) NOT NULL, " +
						"shared_res_uuid RAW(16) DEFAULT NULL, " +
						"shared_node_uuid RAW(16) DEFAULT NULL, " +
						"shared_node_res_uuid RAW(16) DEFAULT NULL, " +
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
						"portfolio_id RAW(16) DEFAULT NULL) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_data_node','"+v_sql+"')}";
				CallableStatement ocs = c.prepareCall(sql) ;
				ocs.execute();
				ocs.close();
			}

			t1 = System.currentTimeMillis();
			
			// If we have uuid, copy portfolio from uuid to local cache
			String baseUuid="";
			ResultSet res = null;
			if( srcuuid != null )
			{
				sql = "INSERT INTO t_data_node ";
				if (dbserveur.equals("mysql")){
					sql += "SELECT uuid2bin(UUID()), ";
				} else if (dbserveur.equals("oracle")){
					sql += "SELECT sys_guid(), ";
				}
				sql += "node_uuid, node_parent_uuid, node_order, metadata_wad, res_node_uuid, res_res_node_uuid, res_context_node_uuid, shared_res, shared_node, shared_node_res, shared_res_uuid, shared_node_uuid, shared_node_res_uuid, asm_type, xsi_type, semtag, semantictag, label, code, descr, format, modif_user_id, modif_date, portfolio_id " +
						"FROM t_node_cache n " +
						"WHERE n.portfolio_id=(SELECT node_uuid=uuid2bin(?))";
				st = c.prepareStatement(sql);
				st.setString(1, srcuuid);
				st.executeUpdate();
				st.close();
				
				// Then skip tag searching since we know the uuid
				baseUuid = srcuuid;
			}
			else
			{
				// Copie the whole portfolio from shared cache to local cache
				sql = "INSERT INTO t_data_node ";
				if (dbserveur.equals("mysql")){
					sql += "SELECT uuid2bin(UUID()), ";
				} else if (dbserveur.equals("oracle")){
					sql += "SELECT sys_guid(), ";
				}
				sql += "node_uuid, node_parent_uuid, node_order, metadata_wad, res_node_uuid, res_res_node_uuid, res_context_node_uuid, shared_res, shared_node, shared_node_res, shared_res_uuid, shared_node_uuid, shared_node_res_uuid, asm_type, xsi_type, semtag, semantictag, label, code, descr, format, modif_user_id, modif_date, portfolio_id " +
						"FROM t_node_cache n " +
						"WHERE n.portfolio_id=uuid2bin(?)";
				st = c.prepareStatement(sql);
				st.setString(1, portfolioCode);
				st.executeUpdate();
				st.close();
	
				t1e = System.currentTimeMillis();
	
				/// Find the right starting node we want
				sql = "SELECT bin2uuid(n2.node_uuid) AS nUuid, bin2uuid(n2.portfolio_id) AS pUuid " +
						"FROM t_data_node n2 " +
						"WHERE n2.semantictag=? AND n2.portfolio_id=uuid2bin(?)";
				st = c.prepareStatement(sql);
				st.setString(1, tag);
				st.setString(2, portfolioCode);
	
				res = st.executeQuery();
				String pUuid="";
				if( res.next() )	// Take the first one declared
				{
					baseUuid = res.getString("nUuid");
					pUuid = res.getString("pUuid");
					res.close();
					st.close();
				}
				else
				{
					res.close();
					st.close();
					return "Selection non existante.";
				}
			}

			t2 = System.currentTimeMillis();

			/// Pour la copie des données
			if (dbserveur.equals("mysql")){
				sql = "CREATE TEMPORARY TABLE t_res_node(" +
						"new_uuid binary(16) NOT NULL, " +  /// Pour la copie d'une nouvelle structure
						"node_uuid binary(16) NOT NULL, " +
						"xsi_type varchar(50) DEFAULT NULL, " +
//						"content text, " +
						"user_id int(11) DEFAULT NULL, " +
						"modif_user_id int(12) NOT NULL, " +
						"modif_date timestamp NULL DEFAULT NULL) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")){
				String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_res_node(" +
						"new_uuid RAW(16) NOT NULL, " +  /// Pour la copie d'une nouvelle structure
						"node_uuid RAW(16) NOT NULL, " +
						"xsi_type VARCHAR2(50 CHAR) DEFAULT NULL, " +
//						"content CLOB, " +
						"user_id NUMBER(11) DEFAULT NULL, " +
						"modif_user_id NUMBER(12) NOT NULL, " +
						"modif_date timestamp DEFAULT NULL) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_res_node','"+v_sql+"')}";
				CallableStatement ocs = c.prepareCall(sql) ;
				ocs.execute();
				ocs.close();
			}

			t3 = System.currentTimeMillis();

			/// Pour le filtrage de la structure
			if (dbserveur.equals("mysql")){
				sql = "CREATE TEMPORARY TABLE t_struc(" +
						"node_order int(12) NOT NULL, " +
						"new_uuid binary(16) NOT NULL, " +
						"uuid binary(16) UNIQUE NOT NULL, " +
						"node_parent_uuid binary(16) NOT NULL, " +
						"t_level INT) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")){
				String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_struc(" +
						"node_order NUMBER(12) NOT NULL, " +
						"new_uuid RAW(16) NOT NULL, " +
						"uuid RAW(16) NOT NULL, " +
						"node_parent_uuid RAW(16), " +
						"t_level NUMBER(10,0)"+
						",  CONSTRAINT t_struc_UK_uuid UNIQUE (uuid)) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_struc','"+v_sql+"')}";
				CallableStatement ocs = c.prepareCall(sql) ;
				ocs.execute();
				ocs.close();
			}

			t4 = System.currentTimeMillis();

			// En double car on ne peut pas faire d'update/select d'une même table temporaire
			if (dbserveur.equals("mysql")){
				sql = "CREATE TEMPORARY TABLE t_struc_2(" +
						"node_order int(12) NOT NULL, " +
						"new_uuid binary(16) NOT NULL, " +
						"uuid binary(16) UNIQUE NOT NULL, " +
						"node_parent_uuid binary(16) NOT NULL, " +
						"t_level INT) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")){
				String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_struc_2(" +
						"node_order NUMBER(12) NOT NULL, " +
						"new_uuid RAW(16) NOT NULL, " +
						"uuid RAW(16) NOT NULL, " +
						"node_parent_uuid RAW(16), " +
						"t_level NUMBER(10,0)"+
						",  CONSTRAINT t_struc_2_UK_uuid UNIQUE (uuid)) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_struc_2','"+v_sql+"')}";
				CallableStatement ocs = c.prepareCall(sql) ;
				ocs.execute();
				ocs.close();
			}

			t5 = System.currentTimeMillis();

			/*
			/// Copie de la structure
			sql = "INSERT INTO t_data_node(new_uuid, node_uuid, node_parent_uuid, node_order, res_node_uuid, res_res_node_uuid, res_context_node_uuid , shared_res, shared_node, shared_node_res, shared_res_uuid, shared_node_uuid, shared_node_res_uuid, asm_type, xsi_type, semtag, semantictag, label, code, descr, format, modif_user_id, modif_date, portfolio_id) ";
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
			//*/

			t6 = System.currentTimeMillis();

			/// Dans la table temporaire on retrouve les noeuds concernés
			/// (assure une convergence de la récursion et limite le nombre de lignes dans la recherche)
			/// Init table
			sql = "INSERT INTO t_struc(node_order, new_uuid, uuid, node_parent_uuid, t_level) " +
					"SELECT d.node_order, d.new_uuid, d.node_uuid, uuid2bin(?), 0 " +
					"FROM t_data_node d " +
					"WHERE d.node_uuid=uuid2bin(?)";
			st = c.prepareStatement(sql);
			st.setString(1, destUuid);  // Pour le branchement avec la structure de destination
			st.setString(2, baseUuid);
			st.executeUpdate();
			st.close();

			t7 = System.currentTimeMillis();

			/// On boucle, sera toujours <= à "nombre de noeud du portfolio"
			int level = 0;
			int added = 1;
			if (dbserveur.equals("mysql")){
	        	sql = "INSERT IGNORE INTO t_struc_2(node_order, new_uuid, uuid, node_parent_uuid, t_level) ";
			} else if (dbserveur.equals("oracle")){
				sql = "INSERT /*+ ignore_row_on_dupkey_index(t_struc_2,t_struc_2_UK_uuid)*/ INTO t_struc_2(node_order, new_uuid, uuid, node_parent_uuid, t_level) ";
			}
			sql += "SELECT d.node_order, d.new_uuid, d.node_uuid, d.node_parent_uuid, ? " +
					"FROM t_data_node d WHERE d.node_parent_uuid IN (SELECT uuid FROM t_struc t " +
					"WHERE t.t_level=?)";

			String sqlTemp=null;
			if (dbserveur.equals("mysql")){
				sqlTemp = "INSERT IGNORE INTO t_struc SELECT * FROM t_struc_2;";
			} else if (dbserveur.equals("oracle")){
				sqlTemp = "INSERT /*+ ignore_row_on_dupkey_index(t_struc,t_struc_UK_uuid)*/ INTO t_struc SELECT * FROM t_struc_2";
			}
			PreparedStatement stTemp = c.prepareStatement(sqlTemp);

			st = c.prepareStatement(sql);
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

			t8 = System.currentTimeMillis();

			/// On retire les éléments null, ça pose problême par la suite
			if (dbserveur.equals("mysql")){
				sql = "DELETE FROM t_struc WHERE new_uuid=0x0000000000000000000000000000000";
			} else if (dbserveur.equals("oracle")){
		        sql = "DELETE FROM t_struc WHERE new_uuid='00000000000000000000000000000000'";
			}
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			t9 = System.currentTimeMillis();

			/// On filtre les données dont on a pas besoin
			sql = "DELETE FROM t_data_node WHERE node_uuid NOT IN (SELECT uuid FROM t_struc)";
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			t10 = System.currentTimeMillis();

			///// FIXME TODO: Vérifier les droits sur les données restantes

			/// Copie des données non partagés (shared=0)
			sql = "INSERT INTO t_res_node(new_uuid, node_uuid, xsi_type, user_id, modif_user_id, modif_date) ";
			if (dbserveur.equals("mysql")){
				sql += "SELECT uuid2bin(UUID()), ";
			} else if (dbserveur.equals("oracle")){
				sql += "SELECT sys_guid(), ";
			}
			sql += "r.node_uuid, r.xsi_type, r.user_id, r.modif_user_id, r.modif_date " +
					"FROM t_data_node d, resource_table r " +
					"WHERE (d.res_node_uuid=r.node_uuid " +
					"OR res_res_node_uuid=r.node_uuid " +
					"OR res_context_node_uuid=r.node_uuid) " +
					"AND (shared_res=0 OR shared_node=0 OR shared_node_res=0)";
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			t11 = System.currentTimeMillis();

			/// Résolution des nouveaux uuid avec les parents
			// Avec la structure
			sql = "UPDATE t_data_node t " +
					"SET t.node_parent_uuid = (SELECT new_uuid FROM t_struc s WHERE s.uuid=t.node_parent_uuid)";
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			t12 = System.currentTimeMillis();

			// Avec les ressources
			sql = "UPDATE t_data_node t " +
					"SET t.res_node_uuid = (SELECT new_uuid FROM t_res_node r WHERE r.node_uuid= t.res_node_uuid)";
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			t13 = System.currentTimeMillis();

			sql = "UPDATE t_data_node t " +
					"SET t.res_res_node_uuid = (SELECT new_uuid FROM t_res_node r WHERE r.node_uuid= t.res_res_node_uuid)";
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			t14 = System.currentTimeMillis();

			sql = "UPDATE t_data_node t " +
					"SET t.res_context_node_uuid = (SELECT new_uuid FROM t_res_node r WHERE r.node_uuid=t.res_context_node_uuid)";
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			t15 = System.currentTimeMillis();

			/// Mise à jour du parent de la nouvelle copie ainsi que l'ordre
			sql = "UPDATE t_data_node " +
					"SET node_parent_uuid=uuid2bin(?), " +
					"node_order=(SELECT COUNT(node_parent_uuid) FROM node WHERE node_parent_uuid=uuid2bin(?)) " +
					"WHERE node_uuid=uuid2bin(?)";
			st = c.prepareStatement(sql);
			st.setString(1, destUuid);
			st.setString(2, destUuid);
			st.setString(3, baseUuid);
			st.executeUpdate();
			st.close();

			t16 = System.currentTimeMillis();

			// Mise à jour de l'appartenance au portfolio de destination
			sql = "UPDATE t_data_node " +
					"SET portfolio_id=(SELECT portfolio_id FROM node WHERE node_uuid=uuid2bin(?))";
			st = c.prepareStatement(sql);
			st.setString(1, destUuid);
			st.executeUpdate();
			st.close();

			t17 = System.currentTimeMillis();

			/// Parsage des droits des noeuds et initialisation dans la BD
			// Login
			sql = "SELECT login FROM credential c WHERE c.userid=?";
			st = c.prepareStatement(sql);
			st.setInt(1, userId);
			res = st.executeQuery();

			String login="";
			if( res.next() )
			{
				login = res.getString("login");
				res.close();
				st.close();
			}

			//// Temp rights table
			if (dbserveur.equals("mysql")){
				sql = "CREATE TEMPORARY TABLE IF NOT EXISTS `t_group_right_info` ( " +
						"`grid` bigint(20) NOT NULL, " +
						"`owner` bigint(20) NOT NULL, " +
						"`label` varchar(255) COLLATE utf8_unicode_ci NOT NULL DEFAULT 'Nouveau groupe', " +
						"`change_rights` tinyint(1) NOT NULL DEFAULT '0', " +
						"`portfolio_id` binary(16) DEFAULT NULL ," +
						"PRIMARY KEY (`grid`)" +
						") ENGINE=MEMORY AUTO_INCREMENT=3 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")){
				String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_group_right_info(" +
						"grid NUMBER(19,0) NOT NULL, " +
						"owner NUMBER(19,0) NOT NULL, " +
						"label VARCHAR2(255 CHAR) DEFAULT NULL, " +
						"change_rights NUMBER(1) NOT NULL, " +
						"portfolio_id RAW(16) NOT NULL, " +
						"CONSTRAINT t_group_right_info_UK_id UNIQUE (grid)) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_group_right_info','"+v_sql+"')}";
				CallableStatement ocs = c.prepareCall(sql) ;
				ocs.execute();
				ocs.close();
			}

			/// Copy current roles for easier referencing
			/// FIXME: We presuppose all groups referenced already exists
			sql = "INSERT INTO `t_group_right_info` " +
					"SELECT * FROM group_right_info WHERE portfolio_id=(" +
					"SELECT n.portfolio_id FROM node n WHERE n.node_uuid=uuid2bin(?))";
			st = c.prepareStatement(sql);
			st.setString(1, destUuid);	/// TODO: Might want to have the destination portfolio id
			st.execute();
			st.close();

			if (dbserveur.equals("mysql")){
				sql = "CREATE TEMPORARY TABLE `t_group_rights` (" +
						"`grid` bigint(20) NOT NULL, " +
						"`id` binary(16) NOT NULL, " +
						"`RD` tinyint(1) NOT NULL DEFAULT '1', " +
						"`WR` tinyint(1) NOT NULL DEFAULT '0', " +
						"`DL` tinyint(1) NOT NULL DEFAULT '0', " +
						"`SB` tinyint(1) NOT NULL DEFAULT '0', " +
						"`AD` tinyint(1) NOT NULL DEFAULT '0', " +
						"`types_id` varchar(255) COLLATE utf8_unicode_ci, " +
						"`rules_id` varchar(255) COLLATE utf8_unicode_ci, " +
						"`notify_roles` varchar(10000) COLLATE utf8_unicode_ci, " +
						"PRIMARY KEY (`grid`,`id`) " +
						") ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")){
				String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_group_rights(" +
						"grid NUMBER(19,0) NOT NULL, " +
						"id RAW(16) NOT NULL, " +
						"RD NUMBER(1) NOT NULL, " +
						"WR NUMBER(1) NOT NULL, " +
						"DL NUMBER(1) NOT NULL, " +
						"SB NUMBER(1) NOT NULL, " +
						"AD NUMBER(1) NOT NULL, " +
						"types_id VARCHAR2(255 CHAR) DEFAULT NULL, " +
						"rules_id VARCHAR2(255 CHAR) DEFAULT NULL, " +
						"notify_roles VARCHAR2(10000 CHAR) DEFAULT NULL, " +
						"CONSTRAINT t_group_rights_UK_id UNIQUE (id)) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_group_rights','"+v_sql+"')}";
				CallableStatement ocs = c.prepareCall(sql) ;
				ocs.execute();
				ocs.close();
			}

			/// FIXME: Would be better to parse all and insert in one go
			/// Prepare statement
			String sqlUpdateRD = "INSERT INTO t_group_rights(grid,id, RD) VALUES((SELECT grid FROM t_group_right_info WHERE label=?), uuid2bin(?), 1) ON DUPLICATE KEY UPDATE RD = 1 ";
			if( dbserveur.equals("oracle") )
				sqlUpdateRD = "MERGE INTO t_group_rights d USING (SELECT (SELECT grid FROM t_group_right_info WHERE label=?) AS grid, uuid2bin(?) AS id, 1 AS RD) t ON (d.grid=t.grid AND d.id=t.id)  WHEN MATCHED THEN UPDATE SET d.RD=1 WHEN NOT MATCHED THEN INSERT (grid, id, RD) VALUES (t.grid, t.id, t.RD)";
			PreparedStatement stRD = c.prepareStatement(sqlUpdateRD);

			String sqlUpdateWR = "INSERT INTO t_group_rights(grid,id, WR, RD) VALUES((SELECT grid FROM t_group_right_info WHERE label=?), uuid2bin(?), 1, 0) ON DUPLICATE KEY UPDATE WR = 1";
			if( dbserveur.equals("oracle") )
				sqlUpdateWR = "MERGE INTO t_group_rights d USING (SELECT (SELECT grid FROM t_group_right_info WHERE label=?) AS grid, uuid2bin(?) AS id, 1 AS WR, 0 AS RD) t ON (d.grid=t.grid AND d.id=t.id)  WHEN MATCHED THEN UPDATE SET d.WR=1 WHEN NOT MATCHED THEN INSERT (grid, id, WR, RD) VALUES (t.grid, t.id, t.WR, t.RD)";
			PreparedStatement stWR = c.prepareStatement(sqlUpdateWR);

			String sqlUpdateDL = "INSERT INTO t_group_rights(grid,id, DL, RD) VALUES((SELECT grid FROM t_group_right_info WHERE label=?), uuid2bin(?), 1, 0) ON DUPLICATE KEY UPDATE DL = 1";
			if( dbserveur.equals("oracle") )
				sqlUpdateDL = "MERGE INTO t_group_rights d USING (SELECT (SELECT grid FROM t_group_right_info WHERE label=?) AS grid, uuid2bin(?) AS id, 1 AS DL, 0 AS RD) t ON (d.grid=t.grid AND d.id=t.id)  WHEN MATCHED THEN UPDATE SET d.DL=1 WHEN NOT MATCHED THEN INSERT (grid, id, DL, RD) VALUES (t.grid, t.id, t.DL, t.RD)";
			PreparedStatement stDL = c.prepareStatement(sqlUpdateDL);

			String sqlUpdateSB = "INSERT INTO t_group_rights(grid,id, SB, RD) VALUES((SELECT grid FROM t_group_right_info WHERE label=?), uuid2bin(?), 1, 0) ON DUPLICATE KEY UPDATE SB = 1";
			if( dbserveur.equals("oracle") )
				sqlUpdateSB = "MERGE INTO t_group_rights d USING (SELECT (SELECT grid FROM t_group_right_info WHERE label=?) AS grid, uuid2bin(?) AS id, 1 AS SB, 0 AS RD) t ON (d.grid=t.grid AND d.id=t.id)  WHEN MATCHED THEN UPDATE SET d.SB=1 WHEN NOT MATCHED THEN INSERT (grid, id, SB, RD) VALUES (t.grid, t.id, t.SB, t.RD)";
			PreparedStatement stSB = c.prepareStatement(sqlUpdateSB);

			// Selection des metadonnées
			sql = "SELECT bin2uuid(t.new_uuid) AS uuid, bin2uuid(t.portfolio_id) AS puuid, n.metadata, n.metadata_wad, n.metadata_epm " +
					"FROM t_data_node t LEFT JOIN node n ON t.node_uuid=n.node_uuid";
			st = c.prepareStatement(sql);
			res = st.executeQuery();

			t18 = System.currentTimeMillis();

			while( res.next() )
			{
				String uuid = res.getString("uuid");
				String portfolioUuid = res.getString("puuid");
				// Process et remplacement de 'user' par la personne en cours
				String meta = res.getString("metadata_wad");

				if( meta.contains("user") )
				{
					meta = meta.replaceAll("user", login);

					/// Replace metadata with actual username
					sql = "UPDATE t_data_node t SET t.metadata_wad=? WHERE t.new_uuid=uuid2bin(?)";
					st = c.prepareStatement(sql);
					st.setString(1, meta);
					st.setString(2, uuid);
					st.executeUpdate();
					st.close();

					/// Ensure specific user group exist in final tables, and add user in it
					int ngid = getRoleByNode(c, 1, destUuid, login);
					postGroupsUsers(c, 1, userId, ngid);
					
					/// Ensure entry is there in temp table, just need a skeleton info
					sql = "REPLACE INTO t_group_right_info(grid, owner, label) VALUES((SELECT grid FROM group_info gi WHERE gid=?), 1, ?)";
					if (dbserveur.equals("oracle")){
						// FIXME Unsure about this, might need testing
						sql = "MERGE INTO group_info d using (SELECT ? grid,1 ,? label from dual) s ON (1=2) WHEN NOT MATCHED THEN INSERT (d.grid, d.owner, d.label) values (s.grid, s.owner, s.label)";
					}
					st = c.prepareStatement(sql);
					st.setInt(1, ngid);
					st.setString(2, login);
					st.executeUpdate();
					st.close();
				}

				String nodeString = "<?xml version='1.0' encoding='UTF-8' standalone='no'?><transfer "+meta+"></transfer>";
//				System.out.println("!!!!!! METADATA: "+nodeString);
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
						stRD.setString(2, uuid);
						while (tokens.hasMoreElements())
						{
							nodeRole = tokens.nextElement().toString();
							stRD.setString(1, nodeRole);
							int result = stRD.executeUpdate();
//							System.out.println("RD "+nodeRole+" -> "+result+" : "+uuid);
//							credential.postGroupRight(nodeRole,uuid,Credential.READ,portfolioUuid,userId);
						}
					}
					att = attribMap.getNamedItem("delnoderoles");
					if(att != null)
					{
						StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						stDL.setString(2, uuid);
						while (tokens.hasMoreElements())
						{
							nodeRole = tokens.nextElement().toString();
							stDL.setString(1, nodeRole);
							int result = stDL.executeUpdate();
//							credential.postGroupRight(nodeRole,uuid,Credential.DELETE,portfolioUuid,userId);
//							System.out.println("DL "+nodeRole+" -> "+result+" : "+uuid);
						}
					}
					att = attribMap.getNamedItem("editnoderoles");
					if(att != null)
					{
						StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						stWR.setString(2, uuid);
						while (tokens.hasMoreElements())
						{
							nodeRole = tokens.nextElement().toString();
							stWR.setString(1, nodeRole);
							int result = stWR.executeUpdate();
//							credential.postGroupRight(nodeRole,uuid,Credential.WRITE,portfolioUuid,userId);
//							System.out.println("WR "+nodeRole+" -> "+result+" : "+uuid);
						}
					}
					att = attribMap.getNamedItem("submitroles");
					if(att != null)
					{
						StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						stSB.setString(2, uuid);
						while (tokens.hasMoreElements())
						{
							nodeRole = tokens.nextElement().toString();
							stSB.setString(1, nodeRole);
							int result = stSB.executeUpdate();
//							credential.postGroupRight(nodeRole,uuid,Credential.SUBMIT,portfolioUuid,userId);
//							System.out.println("SB "+nodeRole+" -> "+result+" : "+uuid);
						}
					}
//					/*
					att = attribMap.getNamedItem("seeresroles");
					if(att != null)
					{
						StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements())
						{
							nodeRole = tokens.nextElement().toString();
							cred.postGroupRight(c, nodeRole,uuid,Credential.READ,portfolioUuid,userId);
						}
					}
					att = attribMap.getNamedItem("delresroles");
					if(att != null)
					{
						StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements())
						{
							nodeRole = tokens.nextElement().toString();
							cred.postGroupRight(c, nodeRole,uuid,Credential.DELETE,portfolioUuid,userId);
						}
					}
					att = attribMap.getNamedItem("editresroles");
					if(att != null)
					{
						StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						stWR.setString(2, uuid);
						while (tokens.hasMoreElements())
						{
							nodeRole = tokens.nextElement().toString();
							stWR.setString(1, nodeRole);
							int result = stWR.executeUpdate();
//							credential.postGroupRight(nodeRole,uuid,Credential.WRITE,portfolioUuid,userId);
//							System.out.println("WR2 "+nodeRole+" -> "+result+" : "+uuid);
						}
					}
					att = attribMap.getNamedItem("submitresroles");
					if(att != null)
					{
						StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements())
						{
							nodeRole = tokens.nextElement().toString();
							cred.postGroupRight(c, nodeRole,uuid,Credential.SUBMIT,portfolioUuid,userId);
						}
					}
					//*/
					/// FIXME: Incomplete
					/// FIXME: Incomplete
					/// FIXME: Incomplete
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
							cred.postGroupRight(c, role,uuid,actions,portfolioUuid,userId);
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
						postNotifyRoles(c, userId, portfolioUuid, uuid, merge);
					}

					meta = res.getString("metadata");
					nodeString = "<?xml version='1.0' encoding='UTF-8' standalone='no'?><transfer "+meta+"/>";
					is = new InputSource(new StringReader(nodeString));
					doc = documentBuilder.parse(is);
					attribNode = doc.getDocumentElement();
					attribMap = attribNode.getAttributes();

					try
					{
						Node publicatt = attribMap.getNamedItem("public");
						if( publicatt != null && "Y".equals(publicatt.getNodeValue()) )
							setPublicState(c, userId, portfolioUuid, true);
						else if ( "N".equals(publicatt) )
							setPublicState(c, userId, portfolioUuid, false);
					}
					catch(Exception ex)
					{
						ex.printStackTrace();
					}

				}
				catch( Exception e )
				{
					e.printStackTrace();
				}
			}
			stRD.close();
			stWR.close();
			stDL.close();
			stSB.close();
			res.close();
			st.close();

			t19 = System.currentTimeMillis();

			/// On copie tout dans les vrai tables
			c.setAutoCommit(false);

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

			t22 = System.currentTimeMillis();
			//*/

			/// Structure, Join because the TEXT fields are copied from the base nodes
			// FIXME possibly move TEXT as bigger VARCHAR, but might want more mem
			sql = "INSERT INTO node(node_uuid, node_parent_uuid, node_order, metadata, metadata_wad, metadata_epm, res_node_uuid, res_res_node_uuid, res_context_node_uuid, shared_res, shared_node, shared_node_res, shared_res_uuid, shared_node_uuid, shared_node_res_uuid, asm_type, xsi_type, semtag, semantictag, label, code, descr, format, modif_user_id, modif_date, portfolio_id) " +
					"SELECT t.new_uuid, t.node_parent_uuid, t.node_order, n.metadata, t.metadata_wad, n.metadata_epm, t.res_node_uuid, t.res_res_node_uuid, t.res_context_node_uuid, t.shared_res, t.shared_node, t.shared_node_res, t.shared_res_uuid, t.shared_node_uuid, t.shared_node_res_uuid, t.asm_type, t.xsi_type, t.semtag, t.semantictag, t.label, t.code, t.descr, t.format, t.modif_user_id, t.modif_date, t.portfolio_id " +
					"FROM t_data_node t LEFT JOIN node n ON t.node_uuid=n.node_uuid";
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			t20 = System.currentTimeMillis();

			// FIXME, might want to add the portfolio condition?
			/// Resources
			sql = "INSERT INTO resource_table(node_uuid, xsi_type, content, user_id, modif_user_id, modif_date) " +
					"SELECT t.new_uuid, r.xsi_type, r.content, r.user_id, r.modif_user_id, r.modif_date " +
					"FROM t_res_node t LEFT JOIN resource_table r ON t.node_uuid=r.node_uuid";
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

	//		/*
			/// FIXME: Will have to transfer date limit one day
			/// Ajout des droits des resources
			sql = "INSERT INTO group_rights(grid, id, RD, WR, DL, SB, AD, types_id, rules_id, notify_roles) SELECT grid, id, RD, WR, DL, SB, AD, types_id, rules_id, notify_roles FROM t_group_rights";
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();
			//*/

			t21 = System.currentTimeMillis();

			/// FIXME: could be done before with temp table and temp stsructure
			/// Mise à jour de la liste des enfants
			if (dbserveur.equals("mysql")){
			sql = "UPDATE node d, (" +
					"SELECT p.node_parent_uuid, " +
					"GROUP_CONCAT(bin2uuid(p.new_uuid) ORDER BY p.node_order) AS value " +
					"FROM t_data_node p GROUP BY p.node_parent_uuid) tmp " +
					"SET d.node_children_uuid=tmp.value " +
					"WHERE tmp.node_parent_uuid=d.node_uuid";
			} else if (dbserveur.equals("oracle")){
		        sql = "UPDATE node d SET d.node_children_uuid=(SELECT value FROM (SELECT p.node_parent_uuid, LISTAGG(bin2uuid(p.new_uuid), ',') WITHIN GROUP (ORDER BY p.node_order) AS value FROM t_data_node p GROUP BY p.node_parent_uuid) tmp WHERE tmp.node_parent_uuid=d.node_uuid) WHERE EXISTS (SELECT 1 FROM t_data_node WHERE node_parent_uuid=d.node_uuid)";
			}
			st = c.prepareStatement(sql);
			st.execute();
			st.close();

			t22 = System.currentTimeMillis();

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
			st = c.prepareStatement(sql);
			st.setString(1, destUuid);
			st.setString(2, destUuid);
			st.executeUpdate();
			st.close();

			t23 = System.currentTimeMillis();

			end = System.currentTimeMillis();

			/// On récupère le uuid créé
			sql = "SELECT bin2uuid(new_uuid) FROM t_data_node WHERE node_uuid=uuid2bin(?)";
			st = c.prepareStatement(sql);
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
				if(c.getAutoCommit() == false)
					c.rollback();
			}
			catch( SQLException e1 ){ e1.printStackTrace(); }
			e.printStackTrace();
		}
		finally
		{
			try
			{
				c.setAutoCommit(true);
				// Les 'pooled connection' ne se ferment pas vraiment. On nettoie manuellement les tables temporaires...
				if (dbserveur.equals("mysql")){
					sql = "DROP TEMPORARY TABLE IF EXISTS t_data_node, t_group_right_info, t_group_rights, t_res_node, t_struc, t_struc_2";
					st = c.prepareStatement(sql);
					st.execute();
					st.close();
				}

				touchPortfolio(c, destUuid, null);

				c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}

		/*
		System.out.println((t1-start)+","+(t1e-t1)+","+(t2-t1e)+","+(t3-t2)+","+(t4-t3)+","+(t5-t4)+","+(t6-t5)+","+(t7-t6)+","+(t8-t7)+","+(t9-t8)+","+(t10-t9)+","+(t11-t10)+","+(t12-t11)+","+(t13-t12)+","+(t14-t13)+","+(t15-t14)+","+(t16-t15)+","+(t17-t16)+","+(t18-t17)+","+(t19-t18)+","+(t20-t19)+","+(t21-t20)+","+(t22-t21)+","+(t23-t22)+","+(end-t23));
		System.out.println("---- Import ---");
		System.out.println("d0-1: "+(t1-start));
		System.out.println("d1a-1: "+(t1a-t1));
		System.out.println("d1b-1a: "+(t1b-t1a));
		System.out.println("d2-1b: "+(t2-t1b));
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
		System.out.println("d22-23: "+(t23-t22));
		System.out.println("d24-23: "+(end-t23));
		System.out.println("------------------");
		//*/

		return createdUuid;
	}

	// Même chose que postImportNode, mais on ne prend pas en compte le parsage des droits
	@Override
	public Object postCopyNode( Connection c, MimeType inMimeType, String destUuid, String tag, String code, int userId, int groupId ) throws Exception
	{
		if( "".equals(tag) || tag == null || "".equals(code) || code == null )
			return "erreur";

		String sql = "";
		PreparedStatement st;
		String createdUuid="erreur";

		/*
		long start = System.currentTimeMillis();
		long t1=0; long t1a=0; long t2=0; long t3=0; long t4=0; long t5=0;
		long t6=0; long t7=0; long t8=0; long t9=0; long t10=0;
		long t11=0; long t12=0; long t13=0; long t14=0; long t15=0;
		long t16=0; long t17=0; long t18=0; long t19=0; long t20=0;
		long t21=0; long t22=0;
		long end=0;
		//*/

		try
		{
			/// Check/update cache
			String portfolioCode = checkCache(c, code);

			if( portfolioCode == null )
				return "Inexistant selection";
			
//			t1 = System.currentTimeMillis();

			///// Création des tables temporaires
			/// Pour la copie de la structure
			if (dbserveur.equals("mysql")){
				sql = "CREATE TEMPORARY TABLE t_data_node(" +
						"new_uuid binary(16) NOT NULL, " +  /// Pour la copie d'une nouvelle structure
						"node_uuid binary(16)  NOT NULL, " +
						"node_parent_uuid binary(16) DEFAULT NULL, " +
//						"node_children_uuid blob, " +
						"node_order int(12) NOT NULL, " +
//						"metadata text NOT NULL, " +
						"metadata_wad varchar(2048) NOT NULL, " +
//						"metadata_epm text NOT NULL, " +
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
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")){
				String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_data_node(" +
						"new_uuid RAW(16) NOT NULL, " +  /// Pour la copie d'une nouvelle structure
						"node_uuid RAW(16)  NOT NULL, " +
						"node_parent_uuid RAW(16) DEFAULT NULL, " +
						"node_order NUMBER(12) NOT NULL, " +
//					"metadata CLOB DEFAULT NULL, " +
						"metadata_wad VARCHAR2(2048 CHAR) DEFAULT NULL, " +
//					"metadata_epm CLOB DEFAULT NULL, " +
						"res_node_uuid RAW(16) DEFAULT NULL, " +
						"res_res_node_uuid RAW(16) DEFAULT NULL, " +
						"res_context_node_uuid RAW(16)  DEFAULT NULL, " +
						"shared_res NUMBER(1) NOT NULL, " +
						"shared_node NUMBER(1) NOT NULL, " +
						"shared_node_res NUMBER(1) NOT NULL, " +
						"shared_res_uuid RAW(16) DEFAULT NULL, " +
						"shared_node_uuid RAW(16) DEFAULT NULL, " +
						"shared_node_res_uuid RAW(16) DEFAULT NULL, " +
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
						"portfolio_id RAW(16) DEFAULT NULL) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_data_node','"+v_sql+"')}";
				CallableStatement ocs = c.prepareCall(sql) ;
				ocs.execute();
				ocs.close();
			}

			// Copie the whole portfolio from shared cache to local cache
			/// Copie de la structure
			sql = "INSERT INTO t_data_node ";
			if (dbserveur.equals("mysql")){
				sql += "SELECT uuid2bin(UUID()), ";
			} else if (dbserveur.equals("oracle")){
				sql += "SELECT sys_guid(), ";
			}
			sql += "node_uuid, node_parent_uuid, node_order, metadata_wad, res_node_uuid, res_res_node_uuid, res_context_node_uuid, shared_res, shared_node, shared_node_res, shared_res_uuid, shared_node_uuid, shared_node_res_uuid, asm_type, xsi_type, semtag, semantictag, label, code, descr, format, modif_user_id, modif_date, portfolio_id " +
					"FROM t_node_cache n " +
					"WHERE portfolio_id=uuid2bin(?)";
			st = c.prepareStatement(sql);
			st.setString(1, portfolioCode);
			st.executeUpdate();
			st.close();

//			t1a = System.currentTimeMillis();

			/// Find the right starting node we want
			sql = "SELECT bin2uuid(n2.node_uuid) AS nUuid, bin2uuid(n2.portfolio_id) AS pUuid " +
					"FROM t_data_node n2 " +
					"WHERE n2.semantictag=? AND n2.portfolio_id=uuid2bin(?)";
			st = c.prepareStatement(sql);
			st.setString(1, tag);
			st.setString(2, portfolioCode);

			ResultSet res = st.executeQuery();
			String baseUuid="";
			String pUuid="";
			if( res.next() )	// Take the first one declared
			{
				baseUuid = res.getString("nUuid");
				pUuid = res.getString("pUuid");
				res.close();
				st.close();
			}
			else
			{
				res.close();
				st.close();
				return "Selection non existante.";
			}
			
//			t2 = System.currentTimeMillis();

			/// Pour la copie des données
			if (dbserveur.equals("mysql")){
				sql = "CREATE TEMPORARY TABLE t_res_node(" +
						"new_uuid binary(16) NOT NULL, " +  /// Pour la copie d'une nouvelle structure
						"node_uuid binary(16) NOT NULL, " +
						"xsi_type varchar(50) DEFAULT NULL, " +
//						"content text, " +
						"user_id int(11) DEFAULT NULL, " +
						"modif_user_id int(12) NOT NULL, " +
						"modif_date timestamp NULL DEFAULT NULL) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")){
				String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_res_node(" +
						"new_uuid RAW(16) NOT NULL, " +  /// Pour la copie d'une nouvelle structure
						"node_uuid RAW(16) NOT NULL, " +
						"xsi_type VARCHAR2(50 CHAR) DEFAULT NULL, " +
//						"content CLOB, " +
						"user_id NUMBER(11) DEFAULT NULL, " +
						"modif_user_id NUMBER(12) NOT NULL, " +
						"modif_date timestamp DEFAULT NULL) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_res_node','"+v_sql+"')}";
				CallableStatement ocs = c.prepareCall(sql) ;
				ocs.execute();
				ocs.close();
			}

//			t3 = System.currentTimeMillis();

			/// Pour le filtrage de la structure
			if (dbserveur.equals("mysql")){
				sql = "CREATE TEMPORARY TABLE t_struc(" +
						"node_order int(12) NOT NULL, " +
						"new_uuid binary(16) NOT NULL, " +
						"uuid binary(16) UNIQUE NOT NULL, " +
						"node_parent_uuid binary(16) NOT NULL, " +
						"t_level INT) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")){
				String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_struc(" +
						"node_order NUMBER(12) NOT NULL, " +
						"new_uuid RAW(16) NOT NULL, " +
						"uuid RAW(16) NOT NULL, " +
						"node_parent_uuid RAW(16), " +
						"t_level NUMBER(10,0)"+
						",  CONSTRAINT t_struc_UK_uuid UNIQUE (uuid)) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_struc','"+v_sql+"')}";
				CallableStatement ocs = c.prepareCall(sql) ;
				ocs.execute();
				ocs.close();
			}

//			t4 = System.currentTimeMillis();

			// En double car on ne peut pas faire d'update/select d'une même table temporaire
			if (dbserveur.equals("mysql")){
				sql = "CREATE TEMPORARY TABLE t_struc_2(" +
						"node_order int(12) NOT NULL, " +
						"new_uuid binary(16) NOT NULL, " +
						"uuid binary(16) UNIQUE NOT NULL, " +
						"node_parent_uuid binary(16) NOT NULL, " +
						"t_level INT) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")){
				String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_struc_2(" +
						"node_order NUMBER(12) NOT NULL, " +
						"new_uuid RAW(16) NOT NULL, " +
						"uuid RAW(16) NOT NULL, " +
						"node_parent_uuid RAW(16), " +
						"t_level NUMBER(10,0)"+
						",  CONSTRAINT t_struc_2_UK_uuid UNIQUE (uuid)) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_struc_2','"+v_sql+"')}";
				CallableStatement ocs = c.prepareCall(sql) ;
				ocs.execute();
				ocs.close();
			}

//			t5 = System.currentTimeMillis();


//			t6 = System.currentTimeMillis();

			/// Dans la table temporaire on retrouve les noeuds concernés
			/// (assure une convergence de la récursion et limite le nombre de lignes dans la recherche)
			/// Init table
			sql = "INSERT INTO t_struc(node_order, new_uuid, uuid, node_parent_uuid, t_level) " +
					"SELECT d.node_order, d.new_uuid, d.node_uuid, uuid2bin(?), 0 " +
					"FROM t_data_node d " +
					"WHERE d.node_uuid=uuid2bin(?)";
			st = c.prepareStatement(sql);
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
					"FROM t_data_node d WHERE d.node_parent_uuid IN (SELECT uuid FROM t_struc t " +
					"WHERE t.t_level=?)";

			String sqlTemp=null;
			if (dbserveur.equals("mysql")){
				sqlTemp = "INSERT IGNORE INTO t_struc SELECT * FROM t_struc_2;";
			} else if (dbserveur.equals("oracle")){
				sqlTemp = "INSERT /*+ ignore_row_on_dupkey_index(t_struc,t_struc_UK_uuid)*/ INTO t_struc SELECT * FROM t_struc_2";
			}
			PreparedStatement stTemp = c.prepareStatement(sqlTemp);

			st = c.prepareStatement(sql);
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
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

//			t9 = System.currentTimeMillis();

			/// On filtre les données dont on a pas besoin
			sql = "DELETE FROM t_data_node WHERE node_uuid NOT IN (SELECT uuid FROM t_struc)";
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

//			t10 = System.currentTimeMillis();

			///// FIXME TODO: Vérifier les droits sur les données restantes

			/// Copie des données non partagés (shared=0)
			sql = "INSERT INTO t_res_node(new_uuid, node_uuid, xsi_type, user_id, modif_user_id, modif_date) ";
			if (dbserveur.equals("mysql")){
				sql += "SELECT uuid2bin(UUID()), ";
			} else if (dbserveur.equals("oracle")){
				sql += "SELECT sys_guid(), ";
			}
			sql += "r.node_uuid, r.xsi_type, r.user_id, r.modif_user_id, r.modif_date " +
					"FROM t_data_node d, resource_table r " +
					"WHERE (d.res_node_uuid=r.node_uuid " +
					"OR res_res_node_uuid=r.node_uuid " +
					"OR res_context_node_uuid=r.node_uuid) " +
					"AND (shared_res=0 OR shared_node=0 OR shared_node_res=0)";
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

//			t11 = System.currentTimeMillis();

			/// Résolution des nouveaux uuid avec les parents
			// Avec la structure
			sql = "UPDATE t_data_node t " +
					"SET t.node_parent_uuid = (SELECT new_uuid FROM t_struc s WHERE s.uuid=t.node_parent_uuid)";
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

//			t12 = System.currentTimeMillis();

			// Avec les ressources
			sql = "UPDATE t_data_node t " +
					"SET t.res_node_uuid = (SELECT new_uuid FROM t_res_node r WHERE r.node_uuid= t.res_node_uuid)";
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

//			t13 = System.currentTimeMillis();

			sql = "UPDATE t_data_node t " +
					"SET t.res_res_node_uuid = (SELECT new_uuid FROM t_res_node r WHERE r.node_uuid= t.res_res_node_uuid)";
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

//			t14 = System.currentTimeMillis();

			sql = "UPDATE t_data_node t " +
					"SET t.res_context_node_uuid = (SELECT new_uuid FROM t_res_node r WHERE r.node_uuid=t.res_context_node_uuid)";
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

//			t15 = System.currentTimeMillis();

			/// Mise à jour du parent de la nouvelle copie ainsi que l'ordre
			sql = "UPDATE t_data_node " +
					"SET node_parent_uuid=uuid2bin(?), " +
					"node_order=(SELECT COUNT(node_parent_uuid) FROM node WHERE node_parent_uuid=uuid2bin(?)) " +
					"WHERE node_uuid=uuid2bin(?)";
			st = c.prepareStatement(sql);
			st.setString(1, destUuid);
			st.setString(2, destUuid);
			st.setString(3, baseUuid);
			st.executeUpdate();
			st.close();

//			t16 = System.currentTimeMillis();

			// Mise à jour de l'appartenance au portfolio de destination
			sql = "UPDATE t_data_node " +
					"SET portfolio_id=(SELECT portfolio_id FROM node WHERE node_uuid=uuid2bin(?))";
			st = c.prepareStatement(sql);
			st.setString(1, destUuid);
			st.executeUpdate();
			st.close();

			/// Mise à jour de l'appartenance des données
			sql = "UPDATE t_data_node " +
					"SET modif_user_id=?";
			st = c.prepareStatement(sql);
			st.setInt(1, userId);
			st.executeUpdate();
			st.close();

			sql = "UPDATE t_res_node " +
					"SET modif_user_id=?";
			st = c.prepareStatement(sql);
			st.setInt(1, userId);
			st.executeUpdate();
			st.close();

//			t17 = System.currentTimeMillis();

			/// On copie tout dans les vrai tables
			c.setAutoCommit(false);

			/// Structure
			sql = "INSERT INTO node(node_uuid, node_parent_uuid, node_order, metadata, metadata_wad, metadata_epm, res_node_uuid, res_res_node_uuid, res_context_node_uuid, shared_res, shared_node, shared_node_res, shared_res_uuid, shared_node_uuid, shared_node_res_uuid, asm_type, xsi_type, semtag, semantictag, label, code, descr, format, modif_user_id, modif_date, portfolio_id) " +
					"SELECT t.new_uuid, t.node_parent_uuid, t.node_order, n.metadata, n.metadata_wad, n.metadata_epm, t.res_node_uuid, t.res_res_node_uuid, t.res_context_node_uuid, t.shared_res, t.shared_node, t.shared_node_res, t.shared_res_uuid, t.shared_node_uuid, t.shared_node_res_uuid, t.asm_type, t.xsi_type, t.semtag, t.semantictag, t.label, t.code, t.descr, t.format, t.modif_user_id, t.modif_date, t.portfolio_id " +
					"FROM t_data_node t LEFT JOIN node n ON t.node_uuid=n.node_uuid";
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

//			t18 = System.currentTimeMillis();

			/// Resources
			sql = "INSERT INTO resource_table(node_uuid, xsi_type, content, user_id, modif_user_id, modif_date) " +
					"SELECT t.new_uuid, r.xsi_type, r.content, r.user_id, r.modif_user_id, r.modif_date " +
					"FROM t_res_node t LEFT JOIN resource_table r ON t.node_uuid=r.node_uuid";
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

//			t19 = System.currentTimeMillis();

			/// Mise à jour de la liste des enfants
			if (dbserveur.equals("mysql")){
				sql = "UPDATE node d, (" +
					"SELECT p.node_parent_uuid, " +
					"GROUP_CONCAT(bin2uuid(p.new_uuid) ORDER BY p.node_order) AS value " +
					"FROM t_data_node p GROUP BY p.node_parent_uuid) tmp " +
					"SET d.node_children_uuid=tmp.value " +
					"WHERE tmp.node_parent_uuid=d.node_uuid";
			} else if (dbserveur.equals("oracle")){
		        sql = "UPDATE node d SET d.node_children_uuid=(SELECT value FROM (SELECT p.node_parent_uuid, LISTAGG(bin2uuid(p.new_uuid), ',') WITHIN GROUP (ORDER BY p.node_order) AS value FROM t_data_node p GROUP BY p.node_parent_uuid) tmp WHERE tmp.node_parent_uuid=d.node_uuid) WHERE EXISTS (SELECT 1 FROM t_data_node WHERE node_parent_uuid=d.node_uuid)";
			}
			st = c.prepareStatement(sql);
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
			st = c.prepareStatement(sql);
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
					"WHERE n.node_uuid=uuid2bin(?)) g," +  // Retrouve les groupes de destination via le noeud de destination
					"(SELECT gri.label, s.new_uuid, gr.RD, gr.WR, gr.DL, gr.SB, gr.AD, gr.types_id, gr.rules_id " +
					"FROM t_struc s, group_rights gr, group_right_info gri " +
					"WHERE s.uuid=gr.id AND gr.grid=gri.grid) r " + // Prend la liste des droits actuel des noeuds dupliqués
					"WHERE g.label=r.label"; // On croise le nouveau 'grid' avec le 'grid' d'origine via le label
			st = c.prepareStatement(sql);
			st.setString(1, destUuid);
			st.executeUpdate();
			st.close();

//			t22 = System.currentTimeMillis();

			/// Ajout des droits des resources
			// Apparement inutile si l'on s'en occupe qu'au niveau du contexte...
			sql = "INSERT INTO group_rights(grid, id, RD, WR, DL, SB, AD, types_id, rules_id) " +
					"SELECT gr.grid, r.new_uuid, gr.RD, gr.WR, gr.DL, gr.SB, gr.AD, gr.types_id, gr.rules_id " +
					"FROM t_res_node r " +
					"LEFT JOIN group_rights gr ON r.node_uuid=gr.id " +
					"LEFT JOIN group_info gi ON gr.grid=gi.grid " +
					"WHERE gi.gid=?";
			st = c.prepareStatement(sql);
			st.setInt(1, groupId);
			st.executeUpdate();
			st.close();

//			end = System.currentTimeMillis();

			/// On récupère le uuid créé
			sql = "SELECT bin2uuid(new_uuid) FROM t_data_node WHERE node_uuid=uuid2bin(?)";
			st = c.prepareStatement(sql);
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
				if(c.getAutoCommit() == false)
					c.rollback();
			}
			catch( SQLException e1 ){ e1.printStackTrace(); }
			e.printStackTrace();
		}
		finally
		{
			try
			{
				c.setAutoCommit(true);
				// Les 'pooled connection' ne se ferment pas vraiment. On nettoie manuellement les tables temporaires...
				if (dbserveur.equals("mysql")){
					sql = "DROP TEMPORARY TABLE IF EXISTS t_data_node, t_res_node, t_struc, t_struc_2";
					st = c.prepareStatement(sql);
					st.execute();
					st.close();
				}

				touchPortfolio(c, destUuid, null);

				c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}

		/*
		System.out.println("---- Portfolio ---");
		System.out.println("d0-1: "+(t1-start));
		System.out.println("d1-1a: "+(t1a-t1));
		System.out.println("d1a-2: "+(t2-t1a));
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
	public int postMoveNodeUp( Connection c, int userid, String uuid )
	{
		if(!cred.isAdmin(c, userid) && !cred.isDesigner(c, userid, uuid) )
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		String sql = "";
		PreparedStatement st;
		int status = -1;

		try
		{
			sql = "SELECT bin2uuid(node_parent_uuid) AS puuid, node_order " +
					"FROM node " +
					"WHERE node_uuid=uuid2bin(?)";
			st = c.prepareStatement(sql);
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
				c.setAutoCommit(false);

				/// Swap node order
				sql = "UPDATE node SET node_order=";
				if( "mysql".equals(dbserveur) )
					sql += "IF( node_order=?, ?, ? ) ";
				else if( "oracle".equals(dbserveur) )
					sql += "decode( node_order, ?, ?, ? ) ";

				sql += "WHERE node_order IN ( ?, ? ) " +
						"AND node_parent_uuid=uuid2bin(?)";
				st = c.prepareStatement(sql);
				st.setInt(1, order);
				st.setInt(2, order-1);
				st.setInt(3, order);
				st.setInt(4, order-1);
				st.setInt(5, order);
				st.setString(6, puuid);
				st.executeUpdate();
				st.close();

				/// Update children list
				updateMysqlNodeChildren(c, puuid);

				status = 0;
			}
		}
		catch(SQLException e)
		{
			try
			{
				c.rollback();
			}
			catch( SQLException e1 ){ e1.printStackTrace(); }
			e.printStackTrace();
		}
		finally
		{
			try
			{
				c.setAutoCommit(true);
				c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}

		return status;
	}

	@Override
	public boolean postChangeNodeParent( Connection c, int userid, String uuid, String uuidParent)
	{
		/// FIXME something with parent uuid too
		if(!cred.isAdmin(c, userid) && !cred.isDesigner(c, userid, uuid) )
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
			st = c.prepareStatement(sql);
			st.setString(1, uuid);
			ResultSet res = st.executeQuery();

			String puuid="";
			if( res.next() )
			{
				puuid = res.getString("puuid");
			}

			int next = getMysqlNodeNextOrderChildren(c, uuidParent);

			c.setAutoCommit(false);

			sql = "UPDATE node " +
					"SET node_parent_uuid=uuid2bin(?), node_order=? " +
					"WHERE node_uuid=uuid2bin(?)";
			st = c.prepareStatement(sql);
			st.setString(1, uuidParent);
			st.setInt(2, next);
			st.setString(3, uuid);
			st.executeUpdate();

			/// Update children list, origin and destination
			updateMysqlNodeChildren(c, puuid);
			updateMysqlNodeChildren(c, uuidParent);

			status = true;
		}
		catch(Exception e)
		{
			try
			{
				c.rollback();
			}
			catch( SQLException e1 ){ e1.printStackTrace(); }
			e.printStackTrace();
		}
		finally
		{
			try
			{
				c.setAutoCommit(true);
				c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}

		return status;
	}

	@Override
	public Object postNode(Connection c, MimeType inMimeType, String parentNodeUuid, String in,int userId, int groupId) throws Exception {

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

		int nodeOrder = getMysqlNodeNextOrderChildren(c, parentNodeUuid);
		String portfolioUid = getPortfolioUuidByNodeUuid(c, parentNodeUuid);

		String result = null;
		String portfolioModelId = getPortfolioModelUuid(c, portfolioUid);

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

		String nodeUuid = writeNode(c, rootNode, portfolioUid,  portfolioModelId,userId,nodeOrder,null,parentNodeUuid,0,0, true, null, false);

		result = "<nodes>";
		result += "<"+nodeType+" ";
		result += DomUtils.getXmlAttributeOutput("id", nodeUuid)+" ";
		result += "/>";
		result += "</nodes>";

		touchPortfolio(c, parentNodeUuid, null);

		return result;
	}

	@Override
	public Object putNode(Connection c, MimeType inMimeType, String nodeUuid, String in,int userId, int groupId) throws Exception
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

		long t_start = System.currentTimeMillis();

		//TODO putNode getNodeRight
		if(!cred.hasNodeRight(c, userId,groupId,nodeUuid, Credential.WRITE))
			throw new RestWebApplicationException(Status.FORBIDDEN, " No WRITE credential ");
		//return "faux";

		long t_rights = System.currentTimeMillis();

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

		long t_parsexml = System.currentTimeMillis();

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
					updateMysqlResourceByXsiType(c, nodeUuid,children.item(i).getAttributes().getNamedItem("xsi_type").getNodeValue().toString(),DomUtils.getInnerXml(children.item(i)),userId);
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
						updatetMySqlNodeOrder(c, children.item(i).getAttributes().getNamedItem("id").getNodeValue().toString(),j);
						System.out.println("UPDATE NODE ORDER");
						j++;
					}
				}
			}
		}

		long t_endparsing = System.currentTimeMillis();

		// Si le noeud est de type asmResource, on stocke le innerXML du noeud
		if(node.getNodeName().equals("asmResource"))
		{
			updateMysqlResource(c, nodeUuid,xsiType, DomUtils.getInnerXml(node),userId);
		}

		long t_udpateRes = System.currentTimeMillis();

		if(nodeChildrenUuid!=null) updateMysqlNodeChildren(c, nodeUuid);
		//TODO UpdateNode different selon creation de modele ou instantiation copie

		long t_updateNodeChildren = System.currentTimeMillis();

		touchPortfolio(c, nodeUuid, null);

		long t_touchPortfolio = System.currentTimeMillis();

		int retval = updatetMySqlNode(c, nodeUuid, asmType, xsiType, semtag, label, code, descr, format, metadata,metadataWad,metadataEpm,sharedRes,sharedNode,sharedNodeRes, userId);

		long t_udpateNode = System.currentTimeMillis();

		/*
		long d_rights = t_rights - t_start;
		long d_parsexml = t_parsexml - t_rights;
		long d_parsenode = t_endparsing - t_parsexml;
		long d_updRes = t_udpateRes - t_endparsing;
		long d_updateOrder = t_updateNodeChildren - t_udpateRes;
		long d_touchPort = t_touchPortfolio - t_updateNodeChildren;
		long d_updatNode = t_udpateNode - t_touchPortfolio;

		System.out.println("===== PUT Node =====");
		System.out.println("Check rights: "+d_rights);
		System.out.println("Parse XML: "+d_parsexml);
		System.out.println("Parse nodes: "+d_parsenode);
		System.out.println("Update Resource: "+d_updRes);
		System.out.println("Update order: "+d_updateOrder);
		System.out.println("Touch portfolio: "+d_touchPort);
		System.out.println("Update node: "+d_updatNode);
		//*/

		return retval;
	}

	@Override
	public Object deleteResource(Connection c, String resourceUuid,int userId, int groupId) throws Exception
	{
		if(!cred.isAdmin(c, userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		return deleteMySqlResource(c, resourceUuid, userId, groupId);
		//TODO asmResource(s) dans table Node et parentNode children a mettre à jour
	}

	@Override
	public Object getResource(Connection c, MimeType outMimeType, String nodeParentUuid, int userId, int groupId) throws Exception
	{
		String[] data = getMysqlResourceByNodeParentUuid(c, nodeParentUuid);
//		java.sql.ResultSet res =
//		res.next();
		if(!cred.hasNodeRight(c, userId,groupId, nodeParentUuid, Credential.READ))
			throw new RestWebApplicationException(Status.FORBIDDEN, " No READ credential ");
		//return "faux";
		String result = "<asmResource id=\""+data[0]+"\" contextid=\""+nodeParentUuid+"\"  >"+data[1]+"</asmResource>";

		return result;
	}

	@Override
	public int postCreateRole(Connection c, String portfolioUuid, String role, int userId)
	{
		int groupid = 0;
		String rootNodeUuid = "";
		try
		{
			rootNodeUuid = getPortfolioRootNode(c, portfolioUuid);
		}
		catch( SQLException e2 )
		{
			e2.printStackTrace();
		}

		if(!cred.isAdmin(c, userId) && !cred.isDesigner(c, userId, rootNodeUuid) && !cred.isCreator(c, userId))
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
			st = c.prepareStatement(sql);
			st.setString(1, portfolioUuid);
			st.setString(2, role);
			rs = st.executeQuery();

			if( rs.next() )	// On le retourne directement
			{
				groupid = rs.getInt(1);
			}
			else
			{
				c.setAutoCommit(false);

				// Crée le rôle
				sql = "INSERT INTO group_right_info(portfolio_id, label, owner) VALUES(uuid2bin(?),?,?)";
				st = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
				if (dbserveur.equals("oracle")){
					  st = c.prepareStatement(sql, new String[]{"grid"});
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
					st = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
					if (dbserveur.equals("oracle")){
						  st = c.prepareStatement(sql, new String[]{"gid"});
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
					c.rollback();
				}
			}
		}
		catch(Exception ex)
		{
			try
			{	c.rollback();	}
			catch( SQLException e1 ){ e1.printStackTrace(); }
			ex.printStackTrace();
		}
		finally
		{
			try
			{
				c.setAutoCommit(true);
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
	public String deletePersonRole(Connection c, String portfolioUuid, String role, int userId, int uid)
	{
		if(!cred.isAdmin(c, userId))
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
			st = c.prepareStatement(sql);
			st.setInt(1, uid);
			st.setString(2, portfolioUuid);
			st.setString(3, role);
			rs = st.executeQuery();
		}
		catch(Exception ex)
		{
			try
			{	c.rollback();
				c.setAutoCommit(true);	}
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
	public Object getResources(Connection c, MimeType outMimeType, String portfolioUuid, int userId, int groupId) throws Exception
	{
		java.sql.ResultSet res = getMysqlResources(c, portfolioUuid);
		String returnValue = "";
		if(outMimeType.getSubType().equals("xml"))
		{
			returnValue += "<resources>";
			while(res.next())
			{
				if(!cred.hasNodeRight(c, userId,groupId, res.getString("res_node_uuid"),Credential.READ ))
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
				if(!cred.hasNodeRight(c, userId,groupId, res.getString("res_node_uuid"), Credential.READ))
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
	public Object postResource(Connection c, MimeType inMimeType, String nodeParentUuid, String in, int userId, int groupId) throws Exception
	{
		if(!cred.isAdmin(c, userId))
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

//		java.sql.ResultSet res = getMysqlResourceByNodeParentUuid(nodeParentUuid);
//		res.next();
		if(!cred.hasNodeRight(c, userId,groupId, nodeParentUuid, Credential.WRITE))
		{
			throw new RestWebApplicationException(Status.FORBIDDEN, " No WRITE credential ");
			//return "faux";
		}
		else postNode(c, inMimeType, nodeParentUuid, in, userId, groupId);
		//else throw new Exception("le noeud contient déjà un enfant de type asmResource !");
		return "";
	}

	@Override
	public Object putResource(Connection c, MimeType inMimeType, String nodeParentUuid, String in, int userId, int groupId) throws Exception
	{
		// TODO userId ???
//		long t_start = System.currentTimeMillis();

		in = DomUtils.filterXmlResource(in);

//		long t_filtRes = System.currentTimeMillis();

		int retVal = -1;
		String[] data = getMysqlResourceByNodeParentUuid(c, nodeParentUuid);
		String nodeUuid = "";
		if( data != null )	// Asking to change a non existng node
		{
			nodeUuid = data[0];

//		long t_getResParent = System.currentTimeMillis();

			Document doc = DomUtils.xmlString2Document(in, new StringBuffer());
		// Puis on le recree
			Node node;

//		long t_convertXML = System.currentTimeMillis();

		//	nodeType = rootNode.getNodeName();
			node = (doc.getElementsByTagName("asmResource")).item(0);

			if(!cred.hasNodeRight(c, userId,groupId, nodeParentUuid, Credential.WRITE))
				throw new RestWebApplicationException(Status.FORBIDDEN, " No WRITE credential ");
		//return "faux";

//		long t_checkRights = System.currentTimeMillis();

			touchPortfolio(c, nodeParentUuid, null);

//		long t_upddatePortTime = System.currentTimeMillis();

		//putNode(inMimeType, nodeUuid, in, userId);
			retVal = updateMysqlResource(c, nodeUuid,null,DomUtils.getInnerXml(node),userId);
		}
//		int retVal = updateMysqlResource(nodeParentUuid,null,DomUtils.getInnerXml(node),userId);

//		long t_end = System.currentTimeMillis();

		/*
		long d_filtRes = t_filtRes - t_start;
		long d_resParent = t_getResParent - t_filtRes;
		long d_convertXML = t_convertXML - t_getResParent;
		long d_resParent2 = t_resResParent2 - t_convertXML;
		long d_checkRights = t_checkRights - t_resResParent2;
		long d_updateTime = t_upddatePortTime - t_checkRights;
		long d_updateRes = t_end - t_upddatePortTime;

		System.out.println("Filter resource: "+d_filtRes);
		System.out.println("Fetch parent res: "+d_resParent);
		System.out.println("Convert XML: "+d_convertXML);
		System.out.println("Fetch parent res2: "+d_resParent2);
		System.out.println("Check rights: "+d_checkRights);
		System.out.println("Update time: "+d_updateTime);
		System.out.println("Update res: "+d_updateRes);
		//*/

		return retVal;
	}

	/*
	 * forcedParentUuid permet de forcer l'uuid parent, indépendamment de l'attribut du noeud fourni
	 */
	private String writeNode(Connection c, Node node, String portfolioUuid, String portfolioModelId, int userId, int ordrer, String forcedUuid, String forcedUuidParent,int sharedResParent,int sharedNodeResParent, boolean rewriteId, HashMap<String,String> resolve, boolean parseRights ) throws Exception
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

					if( parseRights )
					{
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
								cred.postGroupRight(c, nodeRole,uuid,Credential.READ,portfolioUuid,userId);
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
								cred.postGroupRight(c, nodeRole,uuid,Credential.DELETE,portfolioUuid,userId);
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
								cred.postGroupRight(c, nodeRole,uuid,Credential.WRITE,portfolioUuid,userId);
							}
						}
					}
					catch(Exception ex) {}
					try
					{
						if(metadataWadNode.getAttributes().getNamedItem("submitnoderoles")!=null)	// TODO submitnoderoles deprecated fro submitroles
						{
							StringTokenizer tokens = new StringTokenizer(metadataWadNode.getAttributes().getNamedItem("submitnoderoles").getNodeValue(), " ");
							while (tokens.hasMoreElements())
							{
								nodeRole = tokens.nextElement().toString();
								cred.postGroupRight(c, nodeRole,uuid,Credential.SUBMIT,portfolioUuid,userId);
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
								cred.postGroupRight(c, nodeRole,uuid,Credential.READ,portfolioUuid,userId);
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
								cred.postGroupRight(c, nodeRole,uuid,Credential.DELETE,portfolioUuid,userId);
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
								cred.postGroupRight(c, nodeRole,uuid,Credential.WRITE,portfolioUuid,userId);
							}
						}
					}
					catch(Exception ex) {}

					try
					{
						if(metadataWadNode.getAttributes().getNamedItem("submitresroles")!=null)	// TODO submitresroles deprecated fro submitroles
						{
							StringTokenizer tokens = new StringTokenizer(metadataWadNode.getAttributes().getNamedItem("submitresroles").getNodeValue(), " ");
							while (tokens.hasMoreElements())
							{
								nodeRole = tokens.nextElement().toString();
								cred.postGroupRight(c, nodeRole,uuid,Credential.SUBMIT,portfolioUuid,userId);
							}
						}
					}
					catch(Exception ex) {}

					try
					{
						if(metadataWadNode.getAttributes().getNamedItem("submitroles")!=null)
						{
							StringTokenizer tokens = new StringTokenizer(metadataWadNode.getAttributes().getNamedItem("submitroles").getNodeValue(), " ");
							while (tokens.hasMoreElements())
							{
								nodeRole = tokens.nextElement().toString();
								cred.postGroupRight(c, nodeRole,uuid,Credential.SUBMIT,portfolioUuid,userId);
							}
						}
					}
					catch(Exception ex) {}

					try
					{
						if(metadataWadNode.getAttributes().getNamedItem("showtoroles")!=null)
						{
							StringTokenizer tokens = new StringTokenizer(metadataWadNode.getAttributes().getNamedItem("showtoroles").getNodeValue(), " ");
							while (tokens.hasMoreElements())
							{
								nodeRole = tokens.nextElement().toString();
								cred.postGroupRight(c, nodeRole,uuid,Credential.NONE,portfolioUuid,userId);
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
								cred.postGroupRight(c, role,uuid,actions,portfolioUuid,userId);
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

							postNotifyRoles(c, userId, portfolioUuid, uuid, merge);
						}
					}
					catch(Exception ex) {}

				}

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
							setPublicState(c, userId, portfolioUuid, true);
						else if( "N".equals(publicatt) )
							setPublicState(c, userId, portfolioUuid, false);
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
			returnValue = insertMySqlNode(c, uuid, parentUuid, "", asmType, xsiType,
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
					insertMysqlResource(c, sharedNodeResUuid,parentUuid,xsiType,DomUtils.getInnerXml(node),portfolioModelId, sharedNodeResParent,sharedResParent, userId);
				}
				else if(!xsiType.equals("context") && !xsiType.equals("nodeRes") && sharedResParent==1)
				{

					sharedResUuid = originUuid;
					insertMysqlResource(c, sharedResUuid,parentUuid,xsiType,DomUtils.getInnerXml(node),portfolioModelId, sharedNodeResParent,sharedResParent, userId);
				}
				else
				{
					insertMysqlResource(c, uuid,parentUuid,xsiType,DomUtils.getInnerXml(node),portfolioModelId, sharedNodeResParent,sharedResParent, userId);
				}
			}
			else insertMysqlResource(c, uuid,parentUuid,xsiType,DomUtils.getInnerXml(node),portfolioModelId, sharedNodeResParent,sharedResParent, userId);

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
						writeNode(c, child,portfolioUuid,portfolioModelId,userId,k,childId,uuid,sharedRes,sharedNodeRes,rewriteId, resolve, parseRights);
						k++;
					}
					else if( "asmResource".equals(nodeName) ) // Les asmResource pose problême dans l'ordre des noeuds
					{
						writeNode(c, child,portfolioUuid,portfolioModelId,userId,k,childId,uuid,sharedRes,sharedNodeRes,rewriteId, resolve, parseRights);
					}
				}
			}
		}

		updateMysqlNodeChildren(c, forcedUuidParent);

		return uuid;
	}

	@Override
	public void writeLog(Connection c, String url, String method, String headers, String inBody, String outBody, int code)
	{
		insertMySqlLog(c, url,method,headers,inBody, outBody, code);
	}

	@Override
	public Object putPortfolioConfiguration(Connection c, String portfolioUuid, Boolean portfolioActive, Integer userId)
	{
		if(!cred.isAdmin(c, userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		return updateMysqlPortfolioConfiguration(c, portfolioUuid, portfolioActive);
	}

	private Object updateMysqlPortfolioConfiguration(Connection c, String portfolioUuid, Boolean portfolioActive)
	{
		String sql = "";
		PreparedStatement st;

		try
		{
			sql  = "UPDATE  portfolio SET active = ? WHERE portfolio_id  = uuid2bin(?) ";

			st = c.prepareStatement(sql);
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
	public String getMysqlUserUid(Connection c, String login) throws Exception
	{
		PreparedStatement st;
		String sql;
		ResultSet res;

		try
		{
			sql = "SELECT userid FROM credential WHERE login = ? ";
			st = c.prepareStatement(sql);
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
			return cred.doPost(login, password);
		}catch (ServletException e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public void getCredential(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		try{
			cred.doGet(request, response);
		}catch (ServletException e) {
			e.printStackTrace();
		}
	}

	@Override
	public String getUserUidByTokenAndLogin(Connection c, String login, String token) throws Exception
	{
		try{
			return cred.getMysqlUserUidByTokenAndLogin(c, login, token);
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
	public Object postGroup(Connection c, String in, int userId) throws Exception
	{
		if(!cred.isAdmin(c, userId))
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
		stInsert = c.prepareStatement(sqlInsert);
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
	public Object getUsers(Connection c, int userId) throws Exception
	{
		ResultSet res = getMysqlUsers(c, userId);

		String result = "<users>";
		int curUser = 0;
		while(res.next())
		{
			int userid = res.getInt("userid");
			if( curUser != userid )
			{
				curUser = userid;
				String subs = res.getString("id");
				if( subs != null )
					subs = "1";
				else
					subs = "0";

				result += "<user ";
				result += DomUtils.getXmlAttributeOutput("id", res.getString("userid"))+" ";
				result += ">";
				result += DomUtils.getXmlElementOutput("label", res.getString("login"));
				result += DomUtils.getXmlElementOutput("display_firstname", res.getString("display_firstname"));
				result += DomUtils.getXmlElementOutput("display_lastname", res.getString("display_lastname"));
				result += DomUtils.getXmlElementOutput("email", res.getString("email"));
				result += DomUtils.getXmlElementOutput("active", res.getString("active"));
				result += DomUtils.getXmlElementOutput("substitute", subs);
				result += "</user>";
			}
			else {}
		}

		result += "</users>";

		return result;
	}

	@Override
	public Object getGroupRights(Connection c, int userId, int groupId) throws Exception
	{
		if(!cred.isAdmin(c, userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		ResultSet res = getMysqlGroupRights(c, userId, groupId);
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

			while(res.next())	// FIXME Not sure why it's in double loop will suffice
			{
				result += "<item ";
				if (AD.equalsIgnoreCase(res.getString("AD"))){
					result += DomUtils.getXmlAttributeOutput("add", "True")+" ";
				}else {result += DomUtils.getXmlAttributeOutput("add", "False")+" ";}
				result += DomUtils.getXmlAttributeOutput("creator", res.getString("owner"))+" ";
				result += DomUtils.getXmlAttributeOutput("date", res.getString("DL"))+" ";	/// FIXME Was there date?
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
	public boolean postGroupsUsers(Connection c, int user, int userId, int groupId)
	{
		PreparedStatement stInsert;
		String sqlInsert;

		if(!cred.isAdmin(c, user))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		try
		{
			sqlInsert = "REPLACE INTO group_user(gid, userid) VALUES (?, ?)";
			if (dbserveur.equals("oracle")){
				sqlInsert = "MERGE INTO group_user d using (SELECT ? gid,? userid FROM DUAL) s ON (d.gid=s.gid AND d.userid=s.userid) WHEN NOT MATCHED THEN INSERT (d.gid, d.userid) VALUES (s.gid, s.userid)";
			}
			stInsert = c.prepareStatement(sqlInsert);
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
	public boolean postRightGroup(Connection c, int groupRightId, int groupId, Integer userId)
	{
		PreparedStatement stUpdate;
		String sqlUpdate;

		if(!cred.isAdmin(c, userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		try
		{
			sqlUpdate = "UPDATE group_info SET grid=? WHERE gid=?";
			stUpdate = c.prepareStatement(sqlUpdate);
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
	public boolean postNotifyRoles(Connection c, int userId, String portfolio, String uuid, String notify)
	{
		boolean ret = false;
		if(!cred.isAdmin(c, userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		String sql = "";
		PreparedStatement st;
		try
		{
			sql  = "UPDATE group_rights SET notify_roles=? " +
					"WHERE id=uuid2bin(?) AND grid IN " +
					"(SELECT grid FROM group_right_info WHERE portfolio_id=uuid2bin(?))";
			st = c.prepareStatement(sql);
			st.setString(1, notify);
			st.setString(2, uuid);
			st.setString(3, portfolio);
			st.executeUpdate();
			st.close();

			ret = true;
		}
		catch (SQLException e){ e.printStackTrace(); }

		return ret;
	}

	@Override
	public boolean setPublicState(Connection c, int userId, String portfolio, boolean isPublic)
	{
		boolean ret = false;
		if( !cred.isAdmin(c, userId) && !cred.isOwner(c, userId, portfolio) && !cred.isDesigner(c, userId, portfolio) && !cred.isCreator(c, userId) )
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		String sql = "";
		PreparedStatement st = null;
		try
		{
			// S'assure qu'il y ait au moins un groupe de base
			sql = "SELECT gi.gid " +
					"FROM group_right_info gri LEFT JOIN group_info gi ON gri.grid=gi.grid " +
					"WHERE gri.portfolio_id=uuid2bin(?) AND gri.label='all'";
			st = c.prepareStatement(sql);
			st.setString(1, portfolio);
			ResultSet rs = st.executeQuery();

			int gid=0;
			if( rs.next() )
				gid = rs.getInt("gid");
			rs.close();
			st.close();

			if( gid == 0 )	//  If not exist, create 'all' groups
			{
				c.setAutoCommit(false);
				sql = "INSERT INTO group_right_info(owner, label, portfolio_id) " +
						"VALUES(?,'all',uuid2bin(?))";
				st = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
				if (dbserveur.equals("oracle")){
					  st = c.prepareStatement(sql, new String[]{"grid"});
				}
				st.setInt(1, userId);
				st.setString(2, portfolio);

				int grid = 0;
				st.executeUpdate();
				ResultSet key = st.getGeneratedKeys();
				if( key.next() )
					grid = key.getInt(1);
				key.close();
				st.close();

				// Insert all nodes into rights	TODO: Might need updates on additional nodes too
				sql = "INSERT INTO group_rights(grid,id) " +
						"(SELECT ?, node_uuid " +
						"FROM node WHERE portfolio_id=uuid2bin(?))";
				st = c.prepareStatement(sql);
				st.setInt(1, grid);
				st.setString(2, portfolio);
				st.executeUpdate();
				st.close();

				sql = "INSERT INTO group_info(grid, owner, label) VALUES(?,?,'all')";
				st = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
				if (dbserveur.equals("oracle")){
					  st = c.prepareStatement(sql, new String[]{"gid"});
				}
				st.setInt(1, grid);
				st.setInt(2, userId);
				st.executeUpdate();

				key = st.getGeneratedKeys();
				if( key.next() )
					gid = key.getInt(1);
				key.close();
				st.close();
				c.commit();
			}

			if( isPublic )	// Insère ou retire 'public' dans le groupe 'all' du portfolio
			{
				sql = "INSERT IGNORE INTO group_user(gid, userid) " +
						"VALUES( ?, (SELECT userid FROM credential WHERE login='public'))";
				if (dbserveur.equals("oracle")){
					sql = "INSERT /*+ ignore_row_on_dupkey_index(group_user,group_user_PK)*/ INTO group_user(gid, userid) " +
							"VALUES(?,(SELECT userid FROM credential WHERE login='public'))";
				}
			}
			else
			{
				sql = "DELETE FROM group_user " +
						"WHERE userid=(SELECT userid FROM credential WHERE login='public') " +
						"AND gid=?";
			}

			st = c.prepareStatement(sql);
			st.setInt(1, gid);
			st.executeUpdate();

			ret = true;
		}
		catch (SQLException e)
		{
			try
			{	c.rollback();
				c.setAutoCommit(true);	}
			catch( SQLException e1 ){ e1.printStackTrace(); }
			e.printStackTrace();
		}
		finally
		{
			try
			{
				c.setAutoCommit(true);
				if( st != null )
					st.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}

		return ret;
	}

	@Override
	public int postShareGroup(Connection c, String portfolio, int user, Integer userId, String write)
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
		if( !cred.isOwner(c, userId, portfolio) && !cred.isAdmin(c, userId) )
			return -2;	// Not owner

		try
		{
			c.setAutoCommit(false);

			// Check if shared group exist
			sql = "SELECT gi.gid, gri.grid " +
					"FROM group_right_info gri, group_info gi " +
					"WHERE gi.grid=gri.grid " +
					"AND gri.label=? " +
					"AND gri.portfolio_id=uuid2bin(?)";
			st = c.prepareStatement(sql);
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
				st = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
				if (dbserveur.equals("oracle")){
					  st = c.prepareStatement(sql, new String[]{"grid"});
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
				st = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
				if (dbserveur.equals("oracle")){
					  st = c.prepareStatement(sql, new String[]{"gid"});
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
			st = c.prepareStatement(sql);
			st.setInt(1, gid);
			st.setInt(2, user);

			st.executeUpdate();

			st.close();
			/// Flush and insert all rights info in created group
			sql = "DELETE FROM group_rights WHERE grid=?";
			st = c.prepareStatement(sql);
			st.setInt(1, grid);
			st.executeUpdate();
			st.close();

			sql = "INSERT INTO group_rights(grid, id, RD, WR) " +
					"SELECT ?, node_uuid, 1, ? FROM node WHERE portfolio_id=uuid2bin(?)";
			st = c.prepareStatement(sql);
			/// With parameter, add default WR, DL
			st.setInt(1, grid);
			st.setInt(2, wr);	/// Flag to select if we write too
			st.setString(3, portfolio);
			st.executeUpdate();

			status = 0;
		}
		catch( Exception e )
		{
			try{ c.rollback(); }
			catch( SQLException e1 ){ e1.printStackTrace(); }
			e.printStackTrace();
		}
		finally
		{
			try
			{ c.setAutoCommit(true);
			c.close(); }
			catch( SQLException e ){ e.printStackTrace(); }
		}

		return status;
	}

	@Override
	public int deleteShareGroup(Connection c, String portfolio, Integer userId)
	{
		int status = -1;
		String sql = "";
		PreparedStatement st;

		/// Check if portfolio is owner by the user sending this command
		if( !cred.isOwner(c, userId, portfolio) && !cred.isAdmin(c, userId) )
			return -2;	// Not owner

		try
		{
			c.setAutoCommit(false);

			// Delete and cleanup
			sql = "DELETE gri, gr, gi, gu " +
					"FROM group_right_info gri " +
					"LEFT JOIN group_rights gr ON gri.grid=gr.grid " +
					"LEFT JOIN group_info gi ON gri.grid=gi.grid " +
					"LEFT JOIN group_user gu ON gi.gid=gu.gid " +
					"WHERE gri.label=? " +
					"AND gri.portfolio_id=uuid2bin(?)";
			if (dbserveur.equals("oracle")){
				sql = "DELETE FROM group_right_info gri WHERE gri.label=? " +
						"AND gri.portfolio_id=uuid2bin(?)";
			}
			st = c.prepareStatement(sql);
			st.setString(1, "shared");
			st.setString(2, portfolio);
			st.executeUpdate();

			status = 0;
		}
		catch( Exception e )
		{
			try{ c.rollback(); }
			catch( SQLException e1 ){ e1.printStackTrace(); }
			e.printStackTrace();
		}
		finally
		{
			try
			{ c.setAutoCommit(true);
			c.close(); }
			catch( SQLException e ){ e.printStackTrace(); }
		}

		return status;
	}

	@Override
	public int deleteSharePerson(Connection c, String portfolio, int user, Integer userId)
	{
		int status = -1;
		String sql = "";
		PreparedStatement st;

		/// Check if portfolio is owner by the user sending this command
		if( !cred.isOwner(c, userId, portfolio) && !cred.isAdmin(c, userId) )
			return -2;	// Not owner

		try
		{
			c.setAutoCommit(false);

			sql = "DELETE FROM group_user " +
					"WHERE userid=? " +
					"AND gid=(" +
					"SELECT gi.gid " +
					"FROM group_right_info gri, group_info gi " +
					"WHERE gri.grid=gi.grid " +
					"AND gri.label=? " +
					"AND gri.portfolio_id=uuid2bin(?))";
			st = c.prepareStatement(sql);
			st.setInt(1, user);
			st.setString(2, "shared");
			st.setString(3, portfolio);
			st.executeUpdate();

			status = 0;
		}
		catch( Exception e )
		{
			try{ c.rollback(); }
			catch( SQLException e1 ){ e1.printStackTrace(); }
			e.printStackTrace();
		}
		finally
		{
			try
			{ c.setAutoCommit(true);
			c.close(); }
			catch( SQLException e ){ e.printStackTrace(); }
		}

		return status;
	}

	@Override
	public Object deleteUsers(Connection c, Integer userId,Integer userId2)
	{
		if(!cred.isAdmin(c, userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		/// Looks like it should be merged since not used anywhere else
		int res = deleteMysqlUsers(c, userId2);

		return res;
	}

	private int deleteMysqlUsers(Connection c, Integer userId)
	{
		String sql = "";
		PreparedStatement st;
		//try
		//{
		//if(credential.getPortfolioRight(userId, groupId, portfolioUuid, Credential.DELETE))
		//{

		try {

			sql  = " DELETE FROM credential WHERE userid=? ";
			st = c.prepareStatement(sql);
			st.setInt(1, userId);
			st.executeUpdate();

			sql  = " DELETE FROM group_user WHERE userid=?";
			st = c.prepareStatement(sql);
			st.setInt(1, userId);

			return 0;

		} catch (SQLException e) {
			e.printStackTrace();
			return 1;
		}
	}

	@Override
	public Object deleteGroupRights(Connection c, Integer groupId, Integer groupRightId, Integer userId)
	{
		if(!cred.isAdmin(c, userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		int res = deleteMysqlGroupRights(c, groupId, groupRightId);

		return res;
	}

	private Integer deleteMysqlGroupRights(Connection c, Integer groupId, Integer groupRightId)
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
			if (dbserveur.equals("oracle")){
				sql  = " DELETE FROM group_info gi WHERE gid=?";
			}
			st = c.prepareStatement(sql);
			st.setInt(1, groupId);
			st.executeUpdate();

			status =0;
		}
		catch (SQLException e) { e.printStackTrace(); }

		return status;
	}

	@Override
	public Object postPortfolioZip(Connection c, MimeType mimeType, MimeType mimeType2, HttpServletRequest httpServletRequest, int userId, int groupId, String modelId, int substid, boolean parseRights) throws IOException
	{
		if(!cred.isAdmin(c, userId) && !cred.isCreator(c, userId))
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
//		int formDataLength = httpServletRequest.getContentLength();
		byte[] buff = new byte[0x100000];	// 1MB buffer

		// Recuperation de l'heure à laquelle le zip est créé
		//Calendar cal = Calendar.getInstance();
		//SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss_S");
		//String now = sdf.format(cal.getTime());

		this.genererPortfolioUuidPreliminaire();

		javax.servlet.http.HttpSession session = httpServletRequest.getSession(true);
		String ppath = session.getServletContext().getRealPath("/");
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
		boolean hasLoaded = false;
		try
		{
			for(int i=0;i<xmlFiles.length;i++)
			{
				String xmlFilepath = xmlFiles[i];
				String xmlFilename = xmlFilepath.substring(xmlFilepath.lastIndexOf(File.separator));
				if( xmlFilename.contains("_") ) continue;	// Case when we add an xml in the portfolio

				BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(xmlFilepath), "UTF8"));
				String line;
				StringBuilder sb = new StringBuilder();

				while((line=br.readLine())!= null){
					sb.append(line.trim());
				}
				br.close();
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

						insertMysqlPortfolio(c, portfolioUuid,uuid,0,userId);

						writeNode(c, rootNode, portfolioUuid, null, userId,0, uuid,null,0,0,false, resolve, parseRights);
					}
					updateMysqlPortfolioActive(c, portfolioUuid,true);

					/// Finalement on crée un rôle designer
					int groupid = postCreateRole(c, portfolioUuid, "designer", userId);

					/// Ajoute la personne dans ce groupe
					putUserGroup(c, Integer.toString(groupid), Integer.toString(userId));

					hasLoaded = true;
				}
			}
		}
		catch( Exception e )
		{
			e.printStackTrace();
		}

		if( hasLoaded )
		for(int i=0;i<allFiles.length;i++)
		{
			String fullPath = allFiles[i];
			String tmpFileName = fullPath.substring(fullPath.lastIndexOf(File.separator)+1);

			if( !tmpFileName.contains("_") ) continue;	// We want ressources now, they have '_' in their name
			int index = tmpFileName.indexOf("_");
			if( index == -1 )
				index =  tmpFileName.indexOf(".");
			int last = tmpFileName.lastIndexOf(File.separator);
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
				String backend = ConfigUtils.get("backendserver");
//						session.getServletContext().getInitParameter("backendserver");

				if( resolved != null )
				{
					/// Have to send it in FORM, compatibility with regular file posting
					PostForm.sendFile(sessionval, backend, user, resolved, lang, file);

					/// No need to fetch resulting ID, since we provided it
					/*
					InputStream objReturn = connect.getInputStream();
					StringWriter idResponse = new StringWriter();
					IOUtils.copy(objReturn, idResponse);
					fileid = idResponse.toString();
					//*/
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
				ex.printStackTrace();
			}
		}

		File zipfile = new File(filename);
		zipfile.delete();
		File zipdir = new File(outsideDir+this.portfolioUuidPreliminaire+File.separator);
		zipdir.delete();

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
	public Object postUser(Connection c, String in, int userId) throws Exception
	{
		if(!cred.isAdmin(c, userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		String result = null;
		String  login = null;
		String  firstname = null;
		String  lastname = null;
		String  label = null;
		String  password = null;
		String active = "1";
		String substitute = null;
		String email = null;
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
					String uid = getMysqlUserUid(c, login);
					
					if ( uid != null){
						uuid = Integer.parseInt(uid);
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
				if(etu.getAttributes().getNamedItem("email")!=null)
				{
					email = etu.getAttributes().getNamedItem("email").getNodeValue();
				}
			}catch(Exception ex) {}
			try{
				if(etu.getAttributes().getNamedItem("active")!=null)
				{
					active = etu.getAttributes().getNamedItem("active").getNodeValue();
				}
			}catch(Exception ex) {}
			try{
				if(etu.getAttributes().getNamedItem("substitute")!=null)
				{
					substitute = etu.getAttributes().getNamedItem("substitute").getNodeValue();
				}
			}catch(Exception ex) {}

		}else{
			result = "Erreur lors de la recuperation des attributs de l'utilisateur dans le XML";
		}

		//On ajoute l'utilisateur dans la base de donnees
		if (etu.getAttributes().getNamedItem("firstname")!=null && etu.getAttributes().getNamedItem("lastname")!=null && etu.getAttributes().getNamedItem("label")==null){

			sqlInsert = "REPLACE INTO credential(userid, login, display_firstname, display_lastname, email, password, active) VALUES (?, ?, ?, ?, ?, UNHEX(SHA1(?)),?)";
			stInsert = c.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS);
			if (dbserveur.equals("oracle")){
				sqlInsert = "MERGE INTO credential d USING (SELECT ? userid,? login,? display_firstname,? display_lastname, ? email, crypt(?) password,? active FROM DUAL) s ON (d.userid=s.userid) WHEN MATCHED THEN UPDATE SET d.login=s.login, d.display_firstname = s.display_firstname, d.display_lastname = s.display_lastname, d.email = s.email, d.password = s.password, d.active = s.active WHEN NOT MATCHED THEN INSERT (d.userid, d.login, d.display_firstname, d.display_lastname, d.password, d.active) VALUES (s.userid, s.login, s.display_firstname, s.display_lastname, s.password, s.active)";
				stInsert = c.prepareStatement(sqlInsert, new String[]{"userid"});
			}
			stInsert.setInt(1, uuid);
			stInsert.setString(2, login);
			stInsert.setString(3, firstname);
			stInsert.setString(4, lastname);
			stInsert.setString(5, email);
			stInsert.setString(6, password);
			stInsert.setString(7, active);
			stInsert.executeUpdate();
		}
		else {
			sqlInsert = "REPLACE INTO credential(userid, login, display_firstname, display_lastname, email, password, active) VALUES (?, ?, ?, ?, ?, UNHEX(SHA1(?)),?)";
			stInsert = c.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS);
			if (dbserveur.equals("oracle")){
				sqlInsert = "MERGE INTO credential d USING (SELECT ? userid,? login,? display_firstname,? display_lastname, ? email, crypt(?) password,? active FROM DUAL) s ON (d.userid=s.userid) WHEN MATCHED THEN UPDATE SET d.login=s.login, d.display_firstname = s.display_firstname, d.display_lastname = s.display_lastname, d.email = s.email, d.password = s.password, d.active = s.active WHEN NOT MATCHED THEN INSERT (d.userid, d.login, d.display_firstname, d.display_lastname, d.password, d.active) VALUES (s.userid, s.login, s.display_firstname, s.display_lastname, s.password, s.active)";
				stInsert = c.prepareStatement(sqlInsert, new String[]{"userid"});
			}
			stInsert.setInt(1, uuid);
			stInsert.setString(2, login);
			stInsert.setString(3, " ");
			stInsert.setString(4, label);
			stInsert.setString(5, email);
			stInsert.setString(6, password);
			stInsert.setString(7, active);
			stInsert.executeUpdate();
		}

		ResultSet rs = stInsert.getGeneratedKeys();
		if (rs.next()) {
			newId = rs.getInt(1);
		}
		stInsert.close();
		rs.close();

		/// Add the possibility of user substitution
		PreparedStatement subst = null;
		/// FIXME: More complete rule to use
		if( "1".equals(substitute) )
		{	// id=0, don't check who this person can substitute (except root)
			String sql = "INSERT IGNORE INTO credential_substitution(userid, id, type) VALUES(?,0,'USER')";
			subst = c.prepareStatement(sql);
			subst.setInt(1, uuid);
			subst.execute();
		}
		else if( "0".equals(substitute) )
		{
			String sql = "DELETE FROM credential_substitution WHERE userid=? AND id=0";
			subst = c.prepareStatement(sql);
			subst.setInt(1, uuid);
			subst.execute();
		}
		if( subst != null )
			subst.close();

		//On renvoie le body pour qu'il soit stocké dans le log
		result = "<user ";
		result += DomUtils.getXmlAttributeOutput("uid", login)+" ";
		result += DomUtils.getXmlAttributeOutput("firstname", firstname)+" ";
		result += DomUtils.getXmlAttributeOutput("lastname", lastname)+" ";
		result += DomUtils.getXmlAttributeOutput("label", label)+" ";
		result += DomUtils.getXmlAttributeOutput("email", email)+" ";
		result += DomUtils.getXmlAttributeOutput("password", password)+" ";
		result += DomUtils.getXmlAttributeOutputInt("uuid", newId)+" ";
		result += DomUtils.getXmlAttributeOutput("substitute", substitute)+" ";
		result += ">";
		result += "</user>";

		return result;
	}

	private ResultSet getMysqlNodeUuidBySemanticTag(Connection c, String portfolioUuid, String semantictag) throws SQLException
	{
		String sql = "";
		PreparedStatement st;
		String text = "%"+semantictag+"%";

		try
		{
			sql = "SELECT bin2uuid(node_uuid) AS node_uuid, bin2uuid(res_node_uuid) AS res_node_uuid, bin2uuid(res_res_node_uuid) AS res_res_node_uuid, bin2uuid(res_context_node_uuid) AS res_context_node_uuid, " +
					"node_children_uuid, code, asm_type, label, node_order " +
					"FROM node WHERE portfolio_id = uuid2bin(?) AND " +
					"semantictag LIKE ? ORDER BY code, node_order";
			//sql = "SELECT bin2uuid(node_uuid) AS node_uuid, bin2uuid(res_res_node_uuid) AS res_res_node_uuid, node_children_uuid, code, asm_type, label FROM node WHERE portfolio_id = uuid2bin('c884bdcd-2165-469b-9939-14376f7f3500') AND metadata LIKE '%semantictag=%competence%'";
			st = c.prepareStatement(sql);

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
	public String getGroupRightsInfos(Connection c, int userId, String portfolioId) throws SQLException
	{
		if(!cred.isAdmin(c, userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		ResultSet res = getMysqlGroupRightsInfos(c, userId, portfolioId);

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

	private ResultSet getMysqlGroupRightsInfos(Connection c, int userId, String portfolioId)
	{
		PreparedStatement st;
		String sql;

		try
		{
			// On recupere d'abord les informations dans la table structures

			sql = "SELECT grid,owner,label FROM group_right_info WHERE  portfolio_id = uuid2bin(?) ";
			//if(userId!=null) sql += "  AND cr.userid = ? ";
			//sql += " ORDER BY display_name ASC ";
			st = c.prepareStatement(sql);
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
	public String getListUsers(Connection c, int userId)
	{
		ResultSet res = getMysqlUsers(c, userId);

		StringBuilder result = new StringBuilder();
		result.append("<users>");
		try {
			int curUser = 0;
			while(res.next())
			{
				int userid = res.getInt("userid");
				if( curUser != userid )
				{
					curUser = userid;
					String subs = res.getString("id");
					if( subs != null )
						subs = "1";
					else
						subs = "0";

					result.append("<user id=\"");
					result.append(res.getString("userid"));
					result.append("\"><username>");
					result.append(res.getString("login"));
					result.append("</username><firstname>");
					result.append(res.getString("display_firstname"));
					result.append("</firstname><lastname>");
					result.append(res.getString("display_lastname"));
					result.append("</lastname><admin>");
					result.append(res.getString("is_admin"));
					result.append("</admin><designer>");
					result.append(res.getString("is_designer"));
					result.append("</designer><email>");
					result.append(res.getString("email"));
					result.append("</email><active>");
					result.append(res.getString("active"));
					result.append("</active><substitute>");
					result.append(subs);
					result.append("</substitute></user>");
				}
				else {}
			}
		}
		catch (SQLException e)
		{
			e.printStackTrace();
			return "<users></users>";
		}

		result.append("</users>");

		return result.toString();

	}

	/// Retrouve le uid du username
	/// currentUser est là au cas où on voudrait limiter l'accès
	@Override
	public String getUserID(Connection c, int currentUser, String username)
	{
		PreparedStatement st;
		ResultSet res = null;
		int result = 0;
		try
		{
			String sql = "SELECT userid FROM credential WHERE login=?";
			st = c.prepareStatement(sql);
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
	public String getInfUser(Connection c, int userId, int userid)
	{
		PreparedStatement st;
		String sql;
		ResultSet res = null;
		String result ="";

		try
		{
			//requetes SQL permettant de recuperer toutes les informations
			//dans la table credential pour un userid(utilisateur) particulier
			sql = "SELECT * FROM credential c " +
					"LEFT JOIN credential_substitution cs " +
					"ON c.userid=cs.userid " +
					"WHERE c.userid = ?";
			st = c.prepareStatement(sql);
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
				try {
					String subs = res.getString("id");
					if( subs != null )
						subs = "1";
					else
						subs = "0";

					result += "<user ";
					result += DomUtils.getXmlAttributeOutput("id", res.getString("userid"))+" ";
					result += ">";
					result += DomUtils.getXmlElementOutput("username", res.getString("login"));
					result += DomUtils.getXmlElementOutput("firstname", res.getString("display_firstname"));
					result += DomUtils.getXmlElementOutput("lastname", res.getString("display_lastname"));
					result += DomUtils.getXmlElementOutput("email", res.getString("email"));
					result += DomUtils.getXmlElementOutput("admin", res.getString("is_admin"));
					result += DomUtils.getXmlElementOutput("designer", res.getString("is_designer"));
					result += DomUtils.getXmlElementOutput("active", res.getString("active"));
					result += DomUtils.getXmlElementOutput("substitute", subs);
					result += "</user>";

				}
				catch (SQLException e)
				{ e.printStackTrace(); }
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
	public String getRoleUser(Connection c, int userId, int userid)
	{
		PreparedStatement st;
		String sql;
		ResultSet res = null;

		try
		{
			sql = "SELECT * FROM group_user gu, group_info gi, group_right_info gri WHERE userid = ? and gi.gid = gu.gid and gi.grid = gri.grid";
			st = c.prepareStatement(sql);
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
	public String putInfUser(Connection c, int userId, int userid2, String in) throws SQLException
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

					st = c.prepareStatement(sql);
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

					st = c.prepareStatement(sql);
					st.setString(1, password);
					st.setInt(2, userid2);
					st.executeUpdate();
				}
				if(children2.item(y).getNodeName().equals("firstname"))
				{
					firstname = DomUtils.getInnerXml(children2.item(y));

					sql = "UPDATE credential SET display_firstname = ? WHERE  userid = ?";

					st = c.prepareStatement(sql);
					st.setString(1, firstname);
					st.setInt(2, userid2);
					st.executeUpdate();
				}
				if(children2.item(y).getNodeName().equals("lastname"))
				{
					lastname = DomUtils.getInnerXml(children2.item(y));

					sql = "UPDATE credential SET display_lastname = ? WHERE  userid = ?";

					st = c.prepareStatement(sql);
					st.setString(1, lastname);
					st.setInt(2, userid2);
					st.executeUpdate();
				}
				if(children2.item(y).getNodeName().equals("email"))
				{
					email = DomUtils.getInnerXml(children2.item(y));

					sql = "UPDATE credential SET email = ? WHERE  userid = ?";

					st = c.prepareStatement(sql);
					st.setString(1, email);
					st.setInt(2, userid2);
					st.executeUpdate();
				}
				if(children2.item(y).getNodeName().equals("admin"))
				{
					is_admin = DomUtils.getInnerXml(children2.item(y));

					int is_adminInt = Integer.parseInt(is_admin);

					sql = "UPDATE credential SET is_admin = ? WHERE  userid = ?";

					st = c.prepareStatement(sql);
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

					st = c.prepareStatement(sql);
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

					st = c.prepareStatement(sql);
					st.setInt(1, activeInt);
					st.setInt(2, userid2);
					st.executeUpdate();
				}

				if(children2.item(y).getNodeName().equals("substitute"))
				{
					String hasSubstitute = DomUtils.getInnerXml(children2.item(y));

					/// Add the possibility of user substitution
					PreparedStatement subst = null;
					/// FIXME: More complete rule to use
					if( "1".equals(hasSubstitute) )
					{	// id=0, don't check who this person can substitute (except root)
						sql = "INSERT IGNORE INTO credential_substitution(userid, id, type) VALUES(?,0,'USER')";
						subst = c.prepareStatement(sql);
						subst.setInt(1, userid2);
						subst.execute();
					}
					else if( "0".equals(hasSubstitute) )
					{
						sql = "DELETE FROM credential_substitution WHERE userid=? AND id=0";
						subst = c.prepareStatement(sql);
						subst.setInt(1, userid2);
						subst.execute();
					}
					if( subst != null )
						subst.close();
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
	public String postUsers(Connection c, String in, int userId) throws Exception
	{
		if(!cred.isAdmin(c, userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		String result = null;
		String  username = null;
		String  password = null;
		String  firstname = null;
		String  lastname = null;
		String  email = null;
		String designerstr = null;
		String  active = "1";
		String substitute = null;
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
						if(children2.item(y).getNodeName().equals("substitute"))
						{
							substitute = DomUtils.getInnerXml(children2.item(y));
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
			sqlInsert = "INSERT INTO credential(login, display_firstname, display_lastname,email, password, active, is_designer) VALUES (?, ?, ?, ?, UNHEX(SHA1(?)),?,?)";
			stInsert = c.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS);
			if (dbserveur.equals("oracle")){
				  sqlInsert = "INSERT INTO credential(login, display_firstname, display_lastname,email, password, active, is_designer) VALUES (?, ?, ?, ?, crypt(?),?,?)";
				  stInsert = c.prepareStatement(sqlInsert, new String[]{"userid"});
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

			if( substitute != null )
			{
				PreparedStatement subst = null;
				/// FIXME: More complete rule to use
				if( "1".equals(substitute) )
				{	// id=0, don't check who this person can substitute (except root)
					String sql = "INSERT IGNORE INTO credential_substitution(userid, id, type) VALUES(?,0,'USER')";
					subst = c.prepareStatement(sql);
					subst.setInt(1, id);
					subst.execute();
				}
				else if( "0".equals(substitute) )
				{
					String sql = "DELETE FROM credential_substitution WHERE userid=? AND id=0";
					subst = c.prepareStatement(sql);
					subst.setInt(1, id);
					subst.execute();
				}
				if( subst != null )
					subst.close();
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
			result += DomUtils.getXmlElementOutput("substitute", substitute);
			result += "</user>";

			result += "</users>";

		} catch (SQLException e) {
			logger.error(e.getMessage());
//			e.printStackTrace();
			result = null;
		}

		return result;
	}

	@Override
	public String[] postCredentialFromXml(Connection c, Integer userId, String username, String password, String substitute) throws ServletException, IOException
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
			stmt=c.prepareStatement(sql);
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
				stmt=c.prepareStatement(sql);
				stmt.setInt(1, uid);
				stmt.setInt(2, 0);		// 0 -> Any account, specific otherwise
				stmt.setString(3, "USER");
				rs = stmt.executeQuery();

				if( rs.next() )	// User can get "any" account, except admin one
				{
					sql = "SELECT c.userid " +
							"FROM credential c " +
							"WHERE c.login=? AND is_admin=0";
					stmt=c.prepareStatement(sql);
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
					stmt=c.prepareStatement(sql);
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
				res = getMySqlUserByLogin(c, substitute);
			}
			else
			{
				returnValue[3] = "";
				res = getMySqlUserByLogin(c, username);
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

	public ResultSet getMySqlUserByLogin(Connection c, String login) throws Exception
	{
		PreparedStatement st;
		String sql;
		ResultSet res;

		try
		{

			sql = "SELECT * FROM credential WHERE login = ? ";
			st = c.prepareStatement(sql);
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
	public int deleteCredential(Connection c, int userId)
	{
		if(!cred.isAdmin(c, userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		int res = updateMysqlCredentialToken(c, userId, null);

		return res;
	}

	@Override
	public Object getNodeWithXSL(Connection c, MimeType mimeType, String nodeUuid, String xslFile, String parameters, int userId, int groupId)
	{
		String xml;
		try {
			/// Preparing parameters for future need, format: "par1:par1val;par2:par2val;..."
			String[] table = parameters.split(";");
			int parSize = table.length;
			String param[] = new String[parSize];
			String paramVal[] = new String[parSize];
			for( int i=0; i<parSize; ++i )
			{
				String line = table[i];
				int var = line.indexOf(":");
				param[i] = line.substring(0, var);
				paramVal[i] = line.substring(var+1);
			}

			/// TODO: Test this more, should use getNode rather than having another output
			xml = getNode(c, new MimeType("text/xml"),nodeUuid,true, userId, groupId, null).toString();

//			xml = getNodeXmlOutput(nodeUuid,true,null,userId, groupId, null,true).toString();
			return DomUtils.processXSLTfile2String( DomUtils.xmlString2Document(xml, new StringBuffer()), xslFile, param, paramVal, new StringBuffer());
		} catch (Exception e) {
//			e.printStackTrace();
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
	public Object postNodeFromModelBySemanticTag(Connection c, MimeType inMimeType, String parentNodeUuid, String semanticTag,int userId, int groupId) throws Exception
	{
		String portfolioUid = getPortfolioUuidByNodeUuid(c, parentNodeUuid);

		String portfolioModelId = getPortfolioModelUuid(c, portfolioUid);

		String xml = getNodeBySemanticTag(c, inMimeType, portfolioModelId,
				semanticTag,userId, groupId).toString();

		ResultSet res = getMysqlOtherNodeUuidByPortfolioModelUuidBySemanticTag(c, portfolioModelId, semanticTag);
		res.next();
		// C'est le noeud obtenu dans le modele indiqué par la table de correspondance
		String otherParentNodeUuid = res.getString("node_uuid");

		return postNode(c, inMimeType, otherParentNodeUuid, xml,userId, groupId);
	}

	public ResultSet getMysqlGroupsPortfolio(Connection c, String portfolioUuid)
	{
		PreparedStatement st;
		String sql;

		try
		{
			// On recupere d'abord les informations dans la table structures
			sql = "SELECT gi.gid, gi.grid,gi.label as g_label, gri.label as gri_label  FROM  group_right_info gri , group_info gi  WHERE   gri.grid = gi.grid  AND gri.portfolio_id = uuid2bin(?) ";

			sql += "  ORDER BY g_label ASC ";
			st = c.prepareStatement(sql);
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
	public String getGroupsPortfolio(Connection c, String portfolioUuid, int userId)
	{
		NodeRight right = cred.getPortfolioRight(c, userId,0, portfolioUuid, Credential.READ);
		if(!right.read)
			return null;

		ResultSet res = getMysqlGroupsPortfolio(c, portfolioUuid);

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
	public Integer getRoleByNode( Connection c, int userId, String nodeUuid, String role )
	{
		if(!cred.isAdmin(c, userId))
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
			st = c.prepareStatement(sql);
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
				st = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
				if (dbserveur.equals("oracle")){
					st = c.prepareStatement(sql, new String[]{"grid"});
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
					st = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
					if (dbserveur.equals("oracle")){
						st = c.prepareStatement(sql, new String[]{"gid"});
					}
					st.setInt(1, retval);
					st.setString(2, role);
					st.executeUpdate();
					rs = st.getGeneratedKeys();
					if( rs.next() ){ group = rs.getInt(1); }
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
	public String postRoleUser(Connection c, int userId, int grid, Integer userid2) throws SQLException
	{
		if(!cred.isAdmin(c, userId))
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
		st = c.prepareStatement(sql);
		st.setInt(1, grid);

		res = st.executeQuery();

		/// Vérifie si un groupe existe, déjà associé à un rôle
		if(!res.next())
		{
			sql = "SELECT * FROM group_right_info WHERE grid = ?";

			st = c.prepareStatement(sql);
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
			stInsert = c.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS);
			if (dbserveur.equals("oracle")){
				sqlInsert = "MERGE INTO group_info d using (SELECT ? grid,? owner,? label from dual) s ON (1=2) WHEN NOT MATCHED THEN INSERT (d.grid, d.owner, d.label) values (s.grid, s.owner, s.label)";
				stInsert = c.prepareStatement(sqlInsert, new String[]{"gid"});
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
			stInsert = c.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS);
			if (dbserveur.equals("oracle")){
				sqlInsert = "MERGE INTO group_user d using (SELECT ? gid,? userid FROM DUAL) s ON (d.gid=s.gid AND d.userid=s.userid) WHEN NOT MATCHED THEN INSERT (d.gid, d.userid) VALUES (s.gid, s.userid)";
				stInsert = c.prepareStatement(sqlInsert);
			}

			stInsert.setInt(1, gid);
			stInsert.setInt(2, userid2);
			stInsert.executeUpdate();

		}else {

			gid = res.getInt("gid");

			sqlInsert = "REPLACE INTO group_user(gid, userid) VALUES (?, ?)";
			stInsert = c.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS);
			if (dbserveur.equals("oracle")){
				sqlInsert = "MERGE INTO group_user d using (SELECT ? gid,? userid FROM DUAL) s ON (d.gid=s.gid AND d.userid=s.userid) WHEN NOT MATCHED THEN INSERT (d.gid, d.userid) VALUES (s.gid, s.userid)";
				stInsert = c.prepareStatement(sqlInsert);
			}

			stInsert.setInt(1, gid);
			stInsert.setInt(2, userid2);
			stInsert.executeUpdate();

		}

		return "user "+userid2+" rajouté au groupd gid "+gid+" pour correspondre au groupRight grid "+grid;
	}

	@Override
	public String getRolePortfolio(Connection c, MimeType mimeType, String role, String portfolioId, int userId) throws SQLException
	{
		Integer grid = null;

		PreparedStatement st;
		String sql;
		ResultSet res;

		sql = "SELECT grid FROM group_right_info WHERE label = ? and portfolio_id = uuid2bin(?)";
		st = c.prepareStatement(sql);
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
	public String getRole(Connection c, MimeType mimeType, int grid, int userId) throws SQLException
	{
		PreparedStatement st;
		String sql;
		ResultSet res;

		sql = "SELECT grid FROM group_right_info WHERE grid = ?";
		st = c.prepareStatement(sql);
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

	private int updateMysqlCredentialToken(Connection c, Integer userId, String  token)
	{
		String sql = "";
		PreparedStatement st;

		try
		{
			sql  = "UPDATE  credential SET token = ? WHERE userid  = ? ";

			st = c.prepareStatement(sql);
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

	private ResultSet getMysqlOtherNodeUuidByPortfolioModelUuidBySemanticTag(Connection c, String portfolioModelUuid, String semantictag) throws SQLException
	{
		String sql = "";
		PreparedStatement st;

		try
		{

			sql = "SELECT bin2uuid(node_uuid) AS node_uuid FROM model_node WHERE portfolio_model_uuid = uuid2bin(?) and  semantic_tag=? ";
			st = c.prepareStatement(sql);
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
	public String getUsersByRole(Connection c, int userId, String portfolioUuid, String role) throws SQLException
	{
		String sql = "";
		PreparedStatement st;
		ResultSet res = null;

		try
		{
			sql = "SELECT * FROM credential c, group_right_info gri, group_info gi, group_user gu WHERE c.userid = gu.userid AND gu.gid = gi.gid AND gi.grid = gri.grid AND gri.portfolio_id = uuid2bin(?) AND gri.label = ?";
			st = c.prepareStatement(sql);
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
	public String getGroupsByRole(Connection c, int userId, String portfolioUuid, String role)
	{
		String sql = "";
		PreparedStatement st;
		ResultSet res = null;

		try
		{
			sql = "SELECT DISTINCT gu.gid FROM group_right_info gri, group_info gi, group_user gu WHERE gu.gid = gi.gid AND gi.grid = gri.grid AND gri.portfolio_id = uuid2bin(?) AND gri.label = ?";
			st = c.prepareStatement(sql);
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
	public Object getNodeMetadataWad(Connection c, MimeType mimeType, String nodeUuid, boolean b, int userId, int groupId, String label) throws SQLException
	{
		StringBuffer result = new StringBuffer();
		// Verification securité
		NodeRight nodeRight = cred.getNodeRight(c, userId,groupId,nodeUuid, label);

		if(!nodeRight.read)
			return result;

		ResultSet resNode = getMysqlNode(c, nodeUuid,userId, groupId);

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
	public String getResNode(Connection c, String contextUuid, int userId, int groupId) throws Exception
	{
		PreparedStatement st=null;
		ResultSet res=null;
		String status="";

		try
		{
			String sql = "SELECT content FROM resource_table " +
					"WHERE node_uuid=(SELECT res_node_uuid FROM node WHERE node_uuid=uuid2bin(?))";
			st = c.prepareStatement(sql);
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

	@Override
	public String getNodeRights(Connection c, String nodeUuid, int userId, int groupId) throws Exception
	{
		String sql;
		PreparedStatement st;
		ResultSet res=null;
		String result= "";

		try
		{
			// On recupere les droits du noeud et les groupes associes
			sql = "SELECT gri.grid, gri.label, gr.RD, gr.WR, gr.DL, gr.SB " +
					"FROM group_rights gr, group_right_info gri " +
					"WHERE gr.grid=gri.grid AND id=uuid2bin(?)";
			st = c.prepareStatement(sql);
			st.setString(1, nodeUuid);
			res = st.executeQuery();

			/*
			 * <node uuid="">
			 *   <role name="">
			 *     <right RD="" WR="" DL="" />
			 *   </role>
			 * </node>
			 */
			DocumentBuilderFactory documentBuilderFactory =DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
			Document doc = documentBuilder.newDocument();
			Element root = doc.createElement("node");
			doc.appendChild(root);
			root.setAttribute("uuid", nodeUuid);
			while( res.next() )
			{
				int grid = res.getInt("grid");
				String rolename = res.getString("label");
				String readRight= res.getInt("RD")==1 ? "Y" : "N";
				String writeRight= res.getInt("WR")==1 ? "Y" : "N";
				String deleteRight= res.getInt("DL")==1 ? "Y" : "N";
				String submitRight= res.getInt("SB")==1 ? "Y" : "N";

				Element role = doc.createElement("role");
				root.appendChild(role);
				role.setAttribute("name", rolename);
				role.setAttribute("id", Integer.toString(grid));

				Element right = doc.createElement("right");
				role.appendChild(right);
				right.setAttribute("RD", readRight);
				right.setAttribute("WR", writeRight);
				right.setAttribute("DL", deleteRight);
				right.setAttribute("SB", submitRight);
			}

			st.close();

			StringWriter stw = new StringWriter();
			Transformer serializer = TransformerFactory.newInstance().newTransformer();
			serializer.transform(new DOMSource(doc), new StreamResult(stw));

			result = stw.toString();
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return result;
		}

		return result;
	}

	private int updatetMySqlNodeMetadatawad(Connection c, String nodeUuid, String metadatawad) throws Exception
	{
		String sql = "";
		PreparedStatement st;


		sql  = "UPDATE node SET ";
		sql += "metadata_wad = ?";
		sql += " WHERE node_uuid = uuid2bin(?) ";
		st = c.prepareStatement(sql);

		st.setString(1, metadatawad);
		st.setString(2, nodeUuid);

		return st.executeUpdate();
	}

	@Override
	public Object putNodeMetadata(Connection c, MimeType mimeType, String nodeUuid, String xmlNode, int userId, int groupId) throws Exception
	{
		String metadata = "";

		int sharedRes = 0;
		int sharedNode = 0;
		int sharedNodeRes = 0;

		//TODO putNode getNodeRight
		if(!cred.hasNodeRight(c, userId,groupId,nodeUuid, Credential.WRITE))
			throw new RestWebApplicationException(Status.FORBIDDEN, " No WRITE credential ");
		//return "faux";

		String status = "erreur";

		String portfolioUid = getPortfolioUuidByNodeUuid(c, nodeUuid);

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
					setPublicState(c, userId, portfolioUid,true);
				else if( "N".equals(publicatt) )
					setPublicState(c, userId, portfolioUid,false);
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
				PreparedStatement st = c.prepareStatement(sql);
				st.setString(1, metadata);
				st.setString(2, tag);
				st.setInt(3, sharedRes);
				st.setInt(4, sharedNode);
				st.setInt(5, sharedNodeRes);
				st.setString(6, nodeUuid);
				st.executeUpdate();
				st.close();

				status = "editer";

				touchPortfolio(c, null, portfolioUid);
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

	public ResultSet getMysqlUserGroupByPortfolio(Connection c, String portfolioUuid, int userId)
	{
		PreparedStatement st;
		String sql;

		try
		{
			// On recupere d'abord les informations dans la table structures
			sql = "SELECT gi.gid, gi.owner, gi.grid,gi.label as g_label, gri.label as gri_label  FROM  group_right_info gri , group_info gi, group_user gu  WHERE   gu.gid=gi.gid AND gri.grid = gi.grid  AND gri.portfolio_id = uuid2bin(?) AND gu.userid= ? ";

			st = c.prepareStatement(sql);
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
	public String getUserGroupByPortfolio(Connection c, String portfolioUuid, int userId)
	{
		ResultSet res = getMysqlUserGroupByPortfolio(c, portfolioUuid, userId);

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
	public Object putNodeMetadataWad(Connection c, MimeType mimeType, String nodeUuid, String xmlNode, int userId, int groupId) throws Exception
	{
		String metadatawad = "";

		//TODO putNode getNodeRight
		if(!cred.hasNodeRight(c, userId,groupId,nodeUuid, Credential.WRITE))
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

		if (1 == updatetMySqlNodeMetadatawad(c, nodeUuid,metadatawad)){
			touchPortfolio( c, nodeUuid, null );
			return "editer";
		}

		return "erreur";
	}

	@Override
	public Object putNodeMetadataEpm(Connection c, MimeType mimeType, String nodeUuid, String xmlNode, int userId, int groupId) throws Exception
	{
		if(!cred.hasNodeRight(c, userId,groupId,nodeUuid, Credential.WRITE))
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
		st = c.prepareStatement(sql);

		st.setString(1, metadataepm);
		st.setString(2, nodeUuid);

		if (st.executeUpdate() == 1)
		{
			touchPortfolio( c, nodeUuid, null );
			return "editer";
		}
		
		return "erreur";
	}

	@Override
	public Object putNodeNodeContext(Connection c, MimeType mimeType, String nodeUuid, String xmlNode, int userId, int groupId) throws Exception
	{
		if(!cred.hasNodeRight(c, userId,groupId,nodeUuid, Credential.WRITE))
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
			updateMysqlResourceByXsiType(c, nodeUuid,"context",DomUtils.getInnerXml(node),userId);
			return "editer";
		}
		return "erreur";
	}

	@Override
	public Object putNodeNodeResource(Connection c, MimeType mimeType, String nodeUuid, String xmlNode, int userId, int groupId) throws Exception
	{
		if(!cred.hasNodeRight(c, userId,groupId,nodeUuid, Credential.WRITE))
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
			updateMysqlResourceByXsiType(c, nodeUuid,"nodeRes",DomUtils.getInnerXml(node),userId);
			return "editer";
		}
		return "erreur";
	}

	@Override
	public Object putRole(Connection c, String xmlRole, int userId, int roleId) throws Exception
	{
		if(!cred.isAdmin(c, userId))
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
			stInsert = c.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS);
			if (dbserveur.equals("oracle")){
				sqlInsert = "INSERT INTO credential(login, display_firstname, display_lastname,email, password) VALUES (?, ?, ?, ?, crypt(?))";
				stInsert = c.prepareStatement(sqlInsert, new String[]{"userid"});
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

	@Deprecated
	@Override
	public Object getModels(Connection c, MimeType mimeType, int userId) throws Exception
	{
		PreparedStatement st;
		String sql;
		ResultSet res = null;

		sql = "SELECT *  FROM  portfolio_model";

		st = c.prepareStatement(sql);
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

	@Deprecated
	@Override
	public Object getModel(Connection c, MimeType mimeType, Integer modelId, int userId) throws Exception
	{
		PreparedStatement st;
		String sql;
		ResultSet res = null;

		sql = "SELECT *  FROM  portfolio_model WHERE portfolio_id = uuid2bin(?)";

		st = c.prepareStatement(sql);
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

	@Deprecated
	@Override
	public Object postModels(Connection c, MimeType mimeType, String xmlModel, int userId) throws Exception
	{
		if(!cred.isAdmin(c, userId))
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

	public String processMeta(Connection c, int userId, String meta)
	{
		try
		{
			/// FIXME: Patch court terme pour la migration
			/// Trouve le login associé au userId
			String sql = "SELECT login FROM credential c " +
					"WHERE c.userid=?";
			PreparedStatement  st = c.prepareStatement(sql);
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
	public String postMacroOnNode( Connection c, int userId, String nodeUuid, String macroName )
	{
		String val = "erreur";
		String sql = "";
		PreparedStatement st;
		/// SELECT grid, role, RD,WR,DL,AD,types_id,rules_id FROM rule_table rt LEFT JOIN group_right_info gri ON rt.role=gri.label LEFT JOIN node n ON n.portfolio_id=gri.portfolio_id WHERE rule_id=1 AND n.node_uuid=uuid2bin('d48cafa1-5180-4c83-9e22-5d4d45bbf6e2');
		/// SELECT grid,bin2uuid(id),RD,WR,DL,SB,AD,types_id,rules_id FROM group_rights WHERE id=uuid2bin('d48cafa1-5180-4c83-9e22-5d4d45bbf6e2');

		// If admin
		// and reset is called

		try
		{
			/// Pour retrouver les enfants du noeud et affecter les droits
			if (dbserveur.equals("mysql")){
				sql = "CREATE TEMPORARY TABLE t_struc_nodeid(" +
						"uuid binary(16) UNIQUE NOT NULL, " +
						"t_level INT) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")){
				String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_struc_nodeid(" +
						"uuid RAW(16) NOT NULL, " +
						"t_level NUMBER(10,0)"+
						",  CONSTRAINT t_struc_nodeid_UK_uuid UNIQUE (uuid)) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_struc_nodeid','"+v_sql+"')}";
				CallableStatement ocs = c.prepareCall(sql) ;
				ocs.execute();
				ocs.close();
			}

			// En double car on ne peut pas faire d'update/select d'une même table temporaire
			if (dbserveur.equals("mysql")){
				sql = "CREATE TEMPORARY TABLE t_struc_nodeid_2(" +
						"uuid binary(16) UNIQUE NOT NULL, " +
						"t_level INT) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")){
				String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_struc_nodeid_2(" +
						"uuid RAW(16) NOT NULL, " +
						"t_level NUMBER(10,0)"+
						",  CONSTRAINT t_struc_nodeid_2_UK_uuid UNIQUE (uuid)) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_struc_nodeid_2','"+v_sql+"')}";
				CallableStatement ocs = c.prepareCall(sql) ;
				ocs.execute();
				ocs.close();
			}

			/// Dans la table temporaire on retrouve les noeuds concernés
			/// (assure une convergence de la récursion et limite le nombre de lignes dans la recherche)
			/// Init table
			sql = "INSERT INTO t_struc_nodeid(uuid, t_level) " +
					"SELECT n.node_uuid, 0 " +
					"FROM node n " +
					"WHERE n.node_uuid=uuid2bin(?)";
			st = c.prepareStatement(sql);
			st.setString(1, nodeUuid);
			st.executeUpdate();
			st.close();

//			/*
			/// On boucle, récursion par niveau
			int level = 0;
			int added = 1;
			if (dbserveur.equals("mysql")){
			    sql = "INSERT IGNORE INTO t_struc_nodeid_2(uuid, t_level) ";
			} else if (dbserveur.equals("oracle")){
			    sql = "INSERT /*+ ignore_row_on_dupkey_index(t_struc_nodeid_2,t_struc_nodeid_2_UK_uuid)*/ INTO t_struc_nodeid_2(uuid, t_level) ";
			}
			sql += "SELECT n.node_uuid, ? " +
					"FROM node n WHERE n.node_parent_uuid IN (SELECT uuid FROM t_struc_nodeid t " +
					"WHERE t.t_level=?)";

			String sqlTemp=null;
			if (dbserveur.equals("mysql")){
				sqlTemp = "INSERT IGNORE INTO t_struc_nodeid SELECT * FROM t_struc_nodeid_2;";
			} else if (dbserveur.equals("oracle")){
				sqlTemp = "INSERT /*+ ignore_row_on_dupkey_index(t_struc_nodeid,t_struc_nodeid_UK_uuid)*/ INTO t_struc_nodeid SELECT * FROM t_struc_nodeid_2";
			}
			PreparedStatement stTemp = c.prepareStatement(sqlTemp);

			st = c.prepareStatement(sql);
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
					"WHERE gr.grid=gi.grid AND gi.gid=gu.gid AND gu.userid=? AND gr.id=uuid2bin(?) AND NOT gi.label='all'";
			st = c.prepareStatement(sql);
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
			st = c.prepareStatement(sql);
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

			NamedNodeMap metaAttr = rootMeta.getAttributes();
			if( "reset".equals(macroName) && cred.isAdmin(c, userId) )
			{
				/// if reset and admin
				// Call specific function to process current temporary table
				resetRights(c);
			}
			else if( "show".equals(macroName) || "hide".equals(macroName) )
			{
				/// FIXME: Could only change the needed rights
				// Check if current group can show stuff
				Node roleitem = metaAttr.getNamedItem("showroles");
				String roles = roleitem.getNodeValue();
				if( roles.contains(grlabl) )	// Can activate it
				{
					String showto = metaAttr.getNamedItem("showtoroles").getNodeValue();
					showto = showto.replace(" ", "','");
					showto = "('" + showto +"')";	/// XXX Might mess up Mysql, but works in Oracle

					//// Il faut qu'il y a un showtorole
					if( !"('')".equals(showto) )
					{
						// Update rights
						/// Ajoute/remplace les droits
						// FIXME: Je crois que quelque chose manque
						sql = "INSERT INTO group_rights(grid, id, RD, WR, DL, AD, types_id, rules_id) " +
								"SELECT gr.grid, gr.id, ?, 0, 0, 0, NULL, NULL " +
								"FROM group_right_info gri, group_rights gr " +
								"WHERE gri.label IN "+showto+" AND gri.grid=gr.grid AND gr.id IN (SELECT uuid FROM t_struc_nodeid) " +
								"ON DUPLICATE KEY UPDATE RD=?, WR=gr.WR, DL=gr.DL, AD=gr.AD, types_id=gr.types_id, rules_id=gr.rules_id";

						if (dbserveur.equals("oracle")){
							sql = "MERGE INTO group_rights d USING (" +
									"SELECT gr.grid, gr.id, ? AS RD, 0 AS WR, 0 AS DL, 0 AS AD, NULL AS types_id, NULL AS rules_id " +
									"FROM group_right_info gri, group_rights gr " +
									"WHERE gri.label IN "+showto+" " +	/// Might not be safe
											"AND gri.grid=gr.grid " +
											"AND gr.id IN (SELECT uuid FROM t_struc_nodeid)) s " +
									"ON (d.id=s.id AND d.grid=s.grid) "+
									"WHEN MATCHED THEN " +
									"UPDATE SET d.RD=?, d.WR=s.WR, d.DL=s.DL, d.AD=s.AD, d.types_id=s.types_id, d.rules_id=s.rules_id " +
									"WHEN NOT MATCHED THEN " +
									"INSERT (d.grid, d.id, d.RD, d.WR, d.DL, d.AD, d.types_id, d.rules_id) " +
									"VALUES (s.grid, s.id, s.RD, s.WR, s.DL, s.AD, s.types_id, s.rules_id)";
						}
						st = c.prepareStatement(sql);
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
						if( isPriv == null )
						{
							isPriv = doc.createAttribute("private");
							metaAttr.setNamedItem(isPriv);
						}
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
					st = c.prepareStatement(sql);
					st.setString(1, meta);
					st.setString(2, nodeUuid);
					st.executeUpdate();
					st.close();
				}

			}
			else if( "submit".equals(macroName) )
			{
				System.out.println("ACTION: "+macroName+" grid: "+grid+" -> uuid: "+nodeUuid);
				// Update rights
				/// Ajoute/remplace les droits
				sql = "INSERT INTO group_rights(grid, id, RD, WR, DL, AD, SB, types_id, rules_id) " +
						"SELECT gr.grid, gr.id, 1, 0, 0, 0, 0, NULL, NULL " +
						"FROM group_rights gr " +
						"WHERE gr.id IN (SELECT uuid FROM t_struc_nodeid) AND gr.grid=? " +
						"ON DUPLICATE KEY UPDATE RD=1, WR=0, DL=0, AD=0, SB=0, types_id=null, rules_id=null";

				if (dbserveur.equals("oracle")){
					sql = "MERGE INTO group_rights d USING (" +
							"SELECT gr.grid, gr.id, 1 RD, 0 WR, 0 DL, 0 AD, 0 SB, NULL types_id, NULL rules_id " +
							"FROM group_rights gr " +
							"WHERE gr.id IN (SELECT uuid FROM t_struc_nodeid) AND gr.grid=? ) s " +
							"ON (d.grid=s.grid AND d.id=s.id) " +
							"WHEN MATCHED THEN UPDATE " +
							"SET d.RD=1, d.WR=0, d.DL=0, d.AD=0, d.SB=0, d.types_id=s.types_id, d.rules_id=s.rules_id " +
							"WHEN NOT MATCHED THEN " +
							"INSERT (d.grid, d.id, d.RD, d.WR, d.DL, d.AD, d.SB, d.types_id, d.rules_id) " +
							"VALUES (s.grid, s.id, s.RD, s.WR, s.DL, s.AD, s.SB, s.types_id, s.rules_id)";
				}
				st = c.prepareStatement(sql);
				st.setInt(1, grid);
				int rows = st.executeUpdate();
				st.close();
				
				if( rows == 0 )
					return "unchanged";

				/// Vérifie le showtoroles
				Node showtonode = metaAttr.getNamedItem("showtoroles");
				String showto = "";
				if( showtonode != null )
					showto = showtonode.getNodeValue();
				showto = showto.replace(" ", "','");
				showto = "('" + showto +"')";

				//// Il faut qu'il y a un showtorole
				System.out.println("SHOWTO: "+showto);
				if( !"('')".equals(showto) )
				{
					System.out.println("SHOWING TO: "+showto);
					// Update rights
					/// Ajoute/remplace les droits
					// FIXME: Je crois que quelque chose manque
					sql = "INSERT INTO group_rights(grid, id, RD, WR, DL, AD, types_id, rules_id) " +
							"SELECT gri.grid, gr.id, 1, 0, 0, 0, NULL, NULL " +
							"FROM group_right_info gri, group_rights gr " +
							"WHERE gri.label IN "+showto+" " +
							"AND gri.portfolio_id=(SELECT portfolio_id FROM node WHERE node_uuid=uuid2bin(?)) " +
							"AND gr.id IN (SELECT uuid FROM t_struc_nodeid) " +
							"ON DUPLICATE KEY UPDATE RD=1, WR=0, DL=0, AD=0, types_id=NULL, rules_id=NULL";

					if (dbserveur.equals("oracle")){
						sql = "MERGE INTO group_rights d USING (" +
								"SELECT gri.grid, gr.id, 1 AS RD, 0 AS WR, 0 AS DL, 0 AS AD, NULL AS types_id, NULL AS rules_id " +
								"FROM group_right_info gri, group_rights gr " +
								"WHERE gri.label IN "+showto+" " +
								"AND gri.grid=gr.grid " +
								"AND gri.portfolio_id=(SELECT portfolio_id FROM node WHERE node_uuid=uuid2bin(?)) " +
								"AND gr.id IN (SELECT uuid FROM t_struc_nodeid)) s " +
								"ON (d.grid = s.grid AND d.id = s.id) " +
								"WHEN MATCHED THEN " +
								"UPDATE SET d.RD=s.RD, d.WR=s.WR, d.DL=s.DL, d.AD=s.AD, d.types_id=s.types_id, d.rules_id=s.rules_id " +
								"WHEN NOT MATCHED THEN " +
								"INSERT (d.grid, d.id, d.RD, d.WR, d.DL, d.AD, d.types_id, d.rules_id) " +
								"VALUES (s.grid, s.id, s.RD, s.WR, s.DL, s.AD, s.types_id, s.rules_id)";
					}
					st = c.prepareStatement(sql);
					st.setString(1, nodeUuid);
					st.executeUpdate();
					st.close();

//					Node isPriv = metaAttr.getNamedItem("private");
//					isPriv.setNodeValue("Y");
				}
				
				/// We then update the metadata notifying it was submitted
				rootMeta.setAttribute("submitted", "Y");
				String updatedMeta = DomUtils.getNodeAttributesString(rootMeta);
				sql = "UPDATE node SET metadata_wad=? WHERE node_uuid=uuid2bin(?)";
				st = c.prepareStatement(sql);
				st.setString(1, updatedMeta);
				st.setString(2, nodeUuid);
				st.executeUpdate();
				st.close();
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
					sql = "DROP TEMPORARY TABLE IF EXISTS t_struc_nodeid, t_struc_nodeid_2";
					st = c.prepareStatement(sql);
					st.execute();
					st.close();
				}

				c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}

		return val;
	}

	public String resetRights( Connection c )
	{
		try
		{
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

			/// t_struc_nodeid is already populated with the uuid we have to reset
			String sql = "SELECT bin2uuid(n.node_uuid) AS uuid, bin2uuid(n.portfolio_id) AS puuid, n.metadata, n.metadata_wad, n.metadata_epm " +
					"FROM t_struc_nodeid t, node n WHERE t.uuid=n.node_uuid";
			PreparedStatement st = c.prepareStatement(sql);
			ResultSet res = st.executeQuery();

			DocumentBuilder documentBuilder;
			DocumentBuilderFactory documentBuilderFactory =DocumentBuilderFactory.newInstance();
			documentBuilder = documentBuilderFactory.newDocumentBuilder();
			while( res.next() )	// TODO Maybe pre-process into temp table
			{
				String uuid = res.getString("uuid");
				String meta = res.getString("metadata_wad");
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
					att = attribMap.getNamedItem("submitroles");
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

					/// Now remove mention to being submitted
					attribNode.removeAttribute("submitted");
					String resetMeta = DomUtils.getNodeAttributesString(attribNode);
					sql = "UPDATE node SET metadata_wad=? WHERE node_uuid=uuid2bin(?)";
					PreparedStatement stu = c.prepareStatement(sql);
					stu.setString(1, resetMeta);
					stu.setString(2, uuid);
					stu.executeUpdate();
					stu.close();
				}
				catch( Exception e )
				{
					e.printStackTrace();
				}
			}
			res.close();
			st.close();

			c.setAutoCommit(false);

			/// On insère les données pré-compilé
//			Iterator<String> entries = resolve.groups.keySet().iterator();

			/// Ajout des droits des noeuds FIXME
			// portfolio, group name, id -> rights
			String updateRight = "UPDATE group_rights gr SET gr.RD=?, gr.WR=?, gr.DL=?, gr.SB=?, gr.AD=?, gr.types_id=?, gr.rules_id=?, gr.notify_roles=? " +
					"WHERE gr.grid=? AND gr.id=uuid2bin(?)";
			st = c.prepareStatement(updateRight);

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

					String sqlgrid = "SELECT gr.grid " +
							"FROM group_rights gr, group_right_info gri " +
							"WHERE gri.grid=gr.grid AND gri.label=? AND gr.id=uuid2bin(?)";
					PreparedStatement st2 = c.prepareStatement(sqlgrid);
					st2.setString(1, group);
					st2.setString(2, uuid);
					res = st2.executeQuery();
					int grid = -1;
					if( res.next() )
						grid = res.getInt("grid");
					st2.close();

//					int grid = resolve.groups.get(group);
					right rightval = rightelem.getValue();
					st.setInt(1, rightval.rd);
					st.setInt(2, rightval.wr);
					st.setInt(3, rightval.dl);
					st.setInt(4, rightval.sb);
					st.setInt(5, rightval.ad);
					st.setString(6, rightval.types);
					st.setString(7, rightval.rules);
					st.setString(8, rightval.notify);
					st.setInt(9, grid);
					st.setString(10, uuid);

					st.execute();
				}
			}
			st.close();
		}
		catch( Exception e )
		{
			try
			{
				if( c.getAutoCommit() == false )
					c.rollback();
			}
			catch( SQLException e1 ){ e1.printStackTrace(); }
			e.printStackTrace();
		}
		finally
		{
			try
			{
				c.setAutoCommit(true);
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}

		return null;
	}

	/*************************************/
	/** Groupe de droits et cie        **/
	/************************************/

	@Override
	public String getRRGList( Connection c, int userId, String portfolio, Integer user, String role )
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
				st = c.prepareStatement(sql);
				st.setInt(1, user);
				st.setString(2, portfolio);
			}
			else if( portfolio != null && role != null )  // Intersection d'un portfolio et role
			{
				sql = "SELECT DISTINCT gri.grid, label, bin2uuid(gri.portfolio_id) AS portfolio " +
						"FROM group_right_info gri " +
						"WHERE gri.label=? AND gri.portfolio_id=uuid2bin(?)";
				st = c.prepareStatement(sql);
				st.setString(1, role);
				st.setString(2, portfolio);

				bypass = true;
			}
			else if( portfolio != null )  // Juste ceux relié à un portfolio
			{
				sql = "SELECT grid, label, bin2uuid(gri.portfolio_id) AS portfolio " +
						"FROM group_right_info gri " +
						"WHERE gri.portfolio_id=uuid2bin(?)";
				st = c.prepareStatement(sql);
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
				st = c.prepareStatement(sql);
				st.setInt(1, user);
			}
			else  // Tout les groupe disponible
			{
				sql = "SELECT grid, label, bin2uuid(gri.portfolio_id) AS portfolio " +
						"FROM group_right_info gri";
				st = c.prepareStatement(sql);
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
	public String getRRGInfo( Connection c, int userId, Integer rrgid )
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
			st = c.prepareStatement(sql);
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
	public String getPortfolioInfo( Connection c, int userId, String portId )
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
			st = c.prepareStatement(sql);
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
					rrgLabel.setTextContent(res.getString("label"));

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
				c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}


		return status;
	}


	@Override
	public String putRRGUpdate( Connection c, int userId, Integer rrgId, String data )
	{
		if(!cred.isAdmin(c, userId) && !cred.isOwnerRRG(c, userId, rrgId))
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
			st = c.prepareStatement(sql);

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
	public String postRRGCreate( Connection c, int userId, String portfolio, String data )
	{
		if(!cred.isAdmin(c, userId) && !cred.isOwner(c, userId, portfolio))
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
			c.setAutoCommit(false);
			Element labelNode = document.getDocumentElement();
			String label = null;
			//      NodeList rrgNodes = document.getElementsByTagName("rolerightsgroup");

			String sqlRRG = "INSERT INTO group_right_info(owner,label,portfolio_id) VALUES(?,?,uuid2bin(?))";
			PreparedStatement rrgst = c.prepareStatement(sqlRRG, Statement.RETURN_GENERATED_KEYS);
			if (dbserveur.equals("oracle")){
				rrgst = c.prepareStatement(sqlRRG, new String[]{"grid"});
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
			try{ c.rollback(); }
			catch( SQLException e1 ){ e1.printStackTrace(); }
			e.printStackTrace();
		}
		finally
		{
			try
			{
				c.setAutoCommit(true);
				c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}

		return value;
	}

	@Override
	public String postRRGUser( Connection c, int userId, Integer rrgid, Integer user )
	{
		if(!cred.isAdmin(c, userId) && !cred.isOwnerRRG(c, userId, rrgid))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		String value="";
		ResultSet res=null;
		try
		{
			c.setAutoCommit(false);

			/// Vérifie si un group_info/grid existe
			String sqlCheck = "SELECT gid FROM group_info WHERE grid=?";
			PreparedStatement st = c.prepareStatement(sqlCheck);
			st.setInt(1, rrgid);
			res = st.executeQuery();

			if(!res.next())
			{
				/// Copie de RRG vers group_info
				String sqlCopy = "INSERT INTO group_info(grid,owner,label)" +
						" SELECT grid,owner,label FROM group_right_info WHERE grid=?";
				st = c.prepareStatement(sqlCopy);
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
			st = c.prepareStatement(sqlUser);
			st.setInt(1, user);
			st.setInt(2, rrgid);
			st.executeUpdate();
			st.close();
		}
		catch( Exception e )
		{
			try{ c.rollback(); }
			catch( SQLException e1 ){ e1.printStackTrace(); }
			e.printStackTrace();
		}
		finally
		{
			try
			{
				c.setAutoCommit(true);
				c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}

		return value;
	}

	@Override
	public String[] getPorfolioGroup( int userId, String groupName )
	{
		return null;
	}

	@Override
	public String postRights(Connection c, int userId, String uuid, String role, NodeRight rights)
	{
		if( !cred.isAdmin(c, userId) )
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		try
		{
			/// Oracle can't do anything right
			String sqlgrid = "SELECT gr.grid " +
					"FROM group_info gi, group_rights gr " +
					"WHERE gi.grid=gr.grid AND gi.label=? AND gr.id=uuid2bin(?)";
			PreparedStatement st = c.prepareStatement(sqlgrid);
			st.setString(1, role);
			st.setString(2, uuid);
			ResultSet res = st.executeQuery();
			int grid = -1;
			if( res.next() )
				grid = res.getInt("grid");
			res.close();
			st.close();

			ArrayList<String[]> args = new ArrayList<String[]>();
			if( rights.read != null )
			{ String[] arg = {"gr.RD",rights.read.toString()};
				args.add(arg); }
			if( rights.write != null )
			{ String[] arg = {"gr.WR",rights.write.toString()};
				args.add(arg); }
			if( rights.delete != null )
			{ String[] arg = {"gr.DL",rights.delete.toString()};
				args.add(arg); }
			if( rights.submit != null )
			{ String[] arg = {"gr.SB",rights.submit.toString()};
				args.add(arg); }

			if( args.isEmpty() )
				return "";

			String[] arg = args.get(0);
			String sql = "UPDATE group_rights gr SET "+arg[0]+"=?";

			for( int i=1; i<args.size(); ++i )
			{
				arg = args.get(i);
				sql += ", "+arg[0]+"=?";
			}
			sql += " WHERE gr.grid=? AND gr.id=uuid2bin(?)";
			st = c.prepareStatement(sql);

			int i=1;
			do
			{
				arg = args.get(i-1);
				int val = 0;
				if( "true".equals(arg[1]) ) val = 1;
				st.setInt(i, val);
				++i;
			} while( i<=args.size() );

			st.setInt(i, grid);
			++i;
			st.setString(i, uuid);

			st.executeUpdate();
			st.close();
		}
		catch( Exception e )
		{
			e.printStackTrace();
		}
		finally
		{
		}

		return "ok";
	}

	@Override
	public String postRRGUsers( Connection c, int userId, Integer rrgid, String data )
	{
		if(!cred.isAdmin(c, userId) && !cred.isOwnerRRG(c, userId, rrgid))
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
			c.setAutoCommit(false);
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
			PreparedStatement st = c.prepareStatement(sqlUser);
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
			try{ c.rollback(); }
			catch( SQLException e1 ){ e1.printStackTrace(); }
			e.printStackTrace();
		}
		finally
		{
			try
			{
				c.setAutoCommit(true);
				c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}

		return value;
	}

	@Override
	public String deleteRRG( Connection c, int userId, Integer rrgId )
	{
		if(!cred.isAdmin(c, userId) && !cred.isOwnerRRG(c, userId, rrgId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		String value="";

		try
		{
			c.setAutoCommit(false);

			String sqlRRG = "DELETE gri, gu, gi, gr " +
					"FROM group_right_info AS gri " +
					"LEFT JOIN group_rights gr ON gri.grid=gr.grid " +
					"LEFT JOIN group_info gi ON gri.grid=gi.grid " +
					"LEFT JOIN group_user gu ON gi.gid=gu.gid " +
					"WHERE gri.grid=?";
			if (dbserveur.equals("oracle")){
				sqlRRG = "DELETE FROM group_right_info AS gri WHERE gri.grid=?";
			}
			PreparedStatement rrgst = c.prepareStatement(sqlRRG);
			rrgst.setInt(1, rrgId);
			rrgst.executeUpdate();
			rrgst.close();
		}
		catch( Exception e )
		{
			try{ c.rollback(); }
			catch( SQLException e1 ){ e1.printStackTrace(); }
			e.printStackTrace();
		}
		finally
		{
			try
			{
				c.setAutoCommit(true);
				c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}

		return value;
	}

	@Override
	public String deleteRRGUser( Connection c, int userId, Integer rrgId, Integer user )
	{
		if(!cred.isAdmin(c, userId) && !cred.isOwnerRRG(c, userId, rrgId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		String value="";

		try
		{
			c.setAutoCommit(false);

			String sqlRRG = "DELETE FROM group_user " +
					"WHERE userid=? AND gid=(SELECT gid FROM group_info WHERE grid=?)";
			PreparedStatement rrgst = c.prepareStatement(sqlRRG);
			rrgst.setInt(1, user);
			rrgst.setInt(2, rrgId);
			rrgst.executeUpdate();
			rrgst.close();
		}
		catch( Exception e )
		{
			try{ c.rollback(); }
			catch( SQLException e1 ){ e1.printStackTrace(); }
			e.printStackTrace();
		}
		finally
		{
			try
			{
				c.setAutoCommit(true);
				c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}

		return value;
	}


	/// Retire les utilisateurs des RRG d'un portfolio donné
	@Override
	public String deletePortfolioUser( Connection c, int userId, String portId )
	{
		//    if(!credential.isAdmin(userId))
		//      throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		try
		{
			c.setAutoCommit(false);

			/// Bla here
			String sqlRRG = "DELETE FROM group_user " +
					"WHERE gid IN " +
					"(SELECT gi.gid " +
					"FROM group_right_info gri " +
					"LEFT JOIN group_info gi ON gri.grid=gi.grid " +
					"WHERE gri.portfolio_id=uuid2bin(?))";
			PreparedStatement rrgst = c.prepareStatement(sqlRRG);
			rrgst.setString(1, portId);
			rrgst.executeUpdate();
			rrgst.close();
		}
		catch( Exception e )
		{
			try{ c.rollback(); }
			catch( SQLException e1 ){ e1.printStackTrace(); }
			e.printStackTrace();
		}
		finally
		{
			try
			{
				c.setAutoCommit(true);
				c.close();
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
	public Integer putUserGroup(Connection c, String usergroup, String userPut)
	{
		PreparedStatement st;
		String sql;
		Integer retval = 0;

		try
		{
			int gid = Integer.parseInt(usergroup);
			int uid = Integer.parseInt(userPut);

			sql = "INSERT INTO group_user(gid, userid) VALUES(?,?)";
			st = c.prepareStatement(sql);
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

	/********************************************************/
	/**
	 * ##   ##  #####  ####### #####     ###   ######
	 * ##   ## ##   ## ##      ##   ## ##   ## ##   ##
	 * ##   ## ##      ##      ##   ## ##      ##   ##
	 * ##   ##  #####  ####    #####   ##  ### ######
	 * ##   ##      ## ##      ##   ## ##   ## ##   ##
	 * ##   ## ##   ## ##      ##   ## ##   ## ##   ##
	 *  #####   #####  ####### ##   ##   ###   ##   ##
  /** Managing and listing user groups
	/********************************************************/
	@Override
	public int postUserGroup(Connection c, String label, int userid)
	{
		String sql = "";
		PreparedStatement st = null;
		ResultSet res = null;
		int groupid = -1;

		try
		{
			sql = "INSERT INTO  * FROM credential_group(label) VALUE(?)";
			if (dbserveur.equals("oracle"))
				st = c.prepareStatement(sql, new String[]{"cg"});
			else
				st = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
			st.setString(1, label);
			st.executeUpdate();
			res = st.getGeneratedKeys();
			if( res.next() )
				groupid = res.getInt(1);
			
		} catch (SQLException e)
		{
			logger.error(e.getMessage());
			e.printStackTrace();
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
		
		return groupid;
	}

	@Override
	public String getUserGroupList( Connection c, int userId )
	{
		String sql = "";
		PreparedStatement st = null;
		ResultSet res = null;

		String result = "<groups>";
		try
		{
			sql = "SELECT * FROM credential_group";
			st = c.prepareStatement(sql);
			res = st.executeQuery();

			while(res.next())
			{
				result +="<group ";
				result += DomUtils.getXmlAttributeOutput("cg", res.getString("cg"))+" ";
				result += ">";
				result += DomUtils.getXmlElementOutput("label", res.getString("label"));
				result += "</group>";
			}
		} catch (SQLException e) {
			e.printStackTrace();
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

		result += "</groups>";

		return result;
	}
	
	@Override
	public String getUsersByUserGroup(Connection c, int userGroupId, int userId)
	{
		String sql = "";
		PreparedStatement st = null;
		ResultSet res = null;

		String result = "<group id=\""+userGroupId+"\">";
		try
		{
			sql = "SELECT * FROM credential_group_members WHERE cg=?";
			st = c.prepareStatement(sql);
			st.setInt(1, userGroupId);
			res = st.executeQuery();

			while(res.next())
			{
				result +="<user";
				result += DomUtils.getXmlAttributeOutput("id", ""+res.getInt("userid"))+" ";
				result += ">";
				result += "</user>";
			}
		} catch (SQLException e) {
			e.printStackTrace();
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

		result += "</group>";

		return result;
	}

	@Override
	public Integer putUserInUserGroup(Connection c, int user, int siteGroupId, int currentUid)
	{
		String sql = "";
		PreparedStatement st = null;
		ResultSet res = null;

		try
		{
			sql = "INSERT INTO credential_group_members(cg, userid) VALUES(?, ?)";
			st = c.prepareStatement(sql);
			st.setInt(1, siteGroupId);
			st.setInt(2, user);
			res = st.executeQuery();

		} catch (SQLException e) {
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
		
		return 0;
	}
	
	@Override
	public String deleteUsersGroups(Connection c, int usersgroup, int currentUid)
	{
		String sql = "";
		PreparedStatement st = null;
		ResultSet res = null;

		try
		{
			c.setAutoCommit(false);
			
			sql = "DELETE FROM credential_group WHERE cg=?";
			st = c.prepareStatement(sql);
			st.setInt(1, usersgroup);
			res = st.executeQuery();
			
			sql = "DELETE FROM credential_group_members WHERE cg=?";
			st = c.prepareStatement(sql);
			st.setInt(1, usersgroup);
			res = st.executeQuery();
			
			c.setAutoCommit(true);

		}
		catch (SQLException e)
		{
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
		
		return null;
	}

	@Override
	public String deleteUsersFromUserGroups(Connection c, int userId, int usersgroup, int currentUid)
	{
		String sql = "";
		PreparedStatement st = null;
		ResultSet res = null;

		try
		{
			sql = "DELETE FROM credential_group_members WHERE cg=? AND userid=?";
			st = c.prepareStatement(sql);
			st.setInt(1, usersgroup);
			st.setInt(2, userId);
			res = st.executeQuery();
		}
		catch (SQLException e)
		{
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
		
		return null;
	}

	
	/********************************************************/
	/**
	 * ######   #####  ######  #######   ###   ######
	 * ##   ## ##   ## ##   ##    #    ##   ## ##   ##
	 * ##   ## ##   ## ##   ##    #    ##      ##   ##
	 * ######  ##   ## ######     #    ##  ### ######
	 * ##      ##   ## ## ##      #    ##   ## ##   ##
	 * ##      ##   ## ##  ##     #    ##   ## ##   ##
	 * ##       #####  ##   ##    #      ###   ##   ##
  /** Managing and listing portfolios
	/********************************************************/
	@Override
	public int postPortfolioGroup( Connection c, String groupname, String type, Integer parent, int userId )
	{
		String sql = "";
		PreparedStatement st = null;
		ResultSet res = null;
		int groupid = -1;

		try
		{
			if( parent != null )
			{	// Check parent exists
				sql = "SELECT pg FROM portfolio_group WHERE pg=? AND type='GROUP'";
				st = c.prepareStatement(sql);
				st.setInt(1, parent);
				res = st.executeQuery();
				if( !res.next() )
					return -1;
				res.close();
				st.close();
			}
			
			sql = "INSERT INTO portfolio_group";
			if( parent == null )
				sql += "(label, type) VALUE(?, ?)";
			else
				// Ensure parent exists
				sql += "(label, type, pg_parent) VALUE(?, ?, ?)";
			if (dbserveur.equals("oracle"))
				st = c.prepareStatement(sql, new String[]{"pg"});
			else
				st = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
			st.setString(1, groupname);
			st.setString(2, type);
			if( parent != null )
				st.setInt(3, parent);
			st.executeUpdate();
			res = st.getGeneratedKeys();
			if( res.next() )
				groupid = res.getInt(1);
			
		} catch (SQLException e)
		{
			logger.error(e.getMessage());
			e.printStackTrace();
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
		
		return groupid;
	}
	
	@Override
	public String getPortfolioGroupList( Connection c, int userId )
	{
		String sql = "";
		PreparedStatement st = null;
		ResultSet res = null;

		StringBuilder result = new StringBuilder();
		result.append("<groups>");
		try
		{
			sql = "SELECT * FROM portfolio_group";
			st = c.prepareStatement(sql);
			res = st.executeQuery();

			class TreeNode
			{
				String nodeContent;
				String type;
				int nodeId;
				ArrayList<TreeNode> childs = new ArrayList<TreeNode>();
			}
			class ProcessTree
			{
				public void reconstruct(StringBuilder data, TreeNode tree)
				{
					String nodeData = tree.nodeContent;
					data.append(nodeData);	// Add current node content
					for( int i=0; i<tree.childs.size(); ++i )
					{
						TreeNode child = tree.childs.get(i);
						reconstruct(data, child);
					}
					// Close node tag
					data.append("</group").append(tree.type).append(">");
				}
			}
			
			ArrayList<TreeNode> trees = new ArrayList<TreeNode>();
			HashMap<Integer, TreeNode> resolve = new HashMap<Integer, TreeNode>();
			
			ProcessTree pf = new ProcessTree();
			
			StringBuilder currNode = new StringBuilder();
			while(res.next())
			{
				currNode.setLength(0);
				String pgStr = res.getString("pg");
				String type = res.getString("type");
				currNode.append("<group").append(type).append(" pg=\"");
				currNode.append(pgStr);
				currNode.append("\"><label>");
				currNode.append(res.getString("label"));
				currNode.append("</label>");
				// group tag will be closed at reconstruction
				
				TreeNode currTreeNode = new TreeNode();
				currTreeNode.nodeContent = currNode.toString();
				currTreeNode.nodeId = Integer.parseInt(pgStr);
				currTreeNode.type = type;
				Integer parentId = res.getInt("pg_parent");
				resolve.put(currTreeNode.nodeId, currTreeNode);
				if( parentId != null && parentId != 0 )
				{
					TreeNode parentTreeNode = resolve.get(parentId);
					parentTreeNode.childs.add(currTreeNode);
				}
				else	// Top level groups
				{
					trees.add(currTreeNode);
				}
			}
			
			/// Go through top level parent and reconstruct each tree
			for( int i=0; i<trees.size(); ++i )
			{
				TreeNode topNode = trees.get(i);
				pf.reconstruct(result, topNode);
			}
			
		} catch (SQLException e) {
			e.printStackTrace();
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

		result.append("</groups>");

		return result.toString();
	}

	@Override
	public String getPortfolioByPortfolioGroup( Connection c, Integer portfolioGroupId, int userId )
	{
		String sql = "";
		PreparedStatement st = null;
		ResultSet res = null;

		StringBuilder result = new StringBuilder();
		result.append("<group id=\"").append(portfolioGroupId).append("\">");
//		String result = "<group id=\""+portfolioGroupId+"\">";
		try
		{
			sql = "SELECT bin2uuid(portfolio_id) AS portfolio_id FROM portfolio_group_members WHERE pg=?";
			st = c.prepareStatement(sql);
			st.setInt(1, portfolioGroupId);
			res = st.executeQuery();

			while(res.next())
			{
				result.append("<portfolio");
//				result +="<portfolio";
				result.append(" id=\"");
				result.append(res.getString("portfolio_id"));
				result.append("\"");
//				result += DomUtils.getXmlAttributeOutput("id", ""+res.getInt("userid"))+" ";
				result.append(">");
//				result += ">";
				result.append("</portfolio>");
//				result += "</portfolio>";
			}
		} catch (SQLException e) {
			e.printStackTrace();
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

		result.append("</group>");
//		result += "</group>";

		return result.toString();
	}

	@Override
	public int putPortfolioInGroup( Connection c, String uuid, Integer portfolioGroupId, int userId )
	{
		String sql = "";
		PreparedStatement st = null;
		ResultSet res = null;

		try
		{
			/// Check if exist with correct type
			sql = "SELECT pg FROM portfolio_group WHERE pg=? AND type='PORTFOLIO'";
			st = c.prepareStatement(sql);
			st.setInt(1, portfolioGroupId);
			res = st.executeQuery();
			if( !res.next() )
				return -1;
			
			res.close();
			st.close();
			
			sql = "SELECT portfolio_id FROM portfolio WHERE portfolio_id=uuid2bin(?)";
			st = c.prepareStatement(sql);
			st.setString(1, uuid);
			res = st.executeQuery();
			if( !res.next() )
				return -1;
			
			res.close();
			st.close();
			
			sql = "INSERT INTO portfolio_group_members(pg, portfolio_id) VALUES(?, uuid2bin(?))";
			st = c.prepareStatement(sql);
			st.setInt(1, portfolioGroupId);
			st.setString(2, uuid);
			st.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
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
		
		return 0;
	}

	@Override
	public String deletePortfolioGroups( Connection c, int portfolioGroupId, int userId )
	{
		String sql = "";
		PreparedStatement st = null;
		ResultSet res = null;

		try
		{
			c.setAutoCommit(false);
			
			sql = "DELETE FROM portfolio_group WHERE pg=?";
			st = c.prepareStatement(sql);
			st.setInt(1, portfolioGroupId);
			res = st.executeQuery();
			
			sql = "DELETE FROM portfolio_group_members WHERE pg=?";
			st = c.prepareStatement(sql);
			st.setInt(1, portfolioGroupId);
			res = st.executeQuery();
			
			c.setAutoCommit(true);

		}
		catch (SQLException e)
		{
			e.printStackTrace();
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
		
		return null;
	}

	@Override
	public String deletePortfolioFromPortfolioGroups( Connection c, String uuid, int portfolioGroupId, int userId )
	{
		String sql = "";
		PreparedStatement st = null;
		ResultSet res = null;

		try
		{
			sql = "DELETE FROM portfolio_group_members WHERE pg=? AND portfolio_id=uuid2bin(?)";
			st = c.prepareStatement(sql);
			st.setInt(1, portfolioGroupId);
			st.setString(2, uuid);
			res = st.executeQuery();
		}
		catch (SQLException e)
		{
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
		
		return null;
	}


	@Override
	public String getRessource(Connection c, String nodeUuid, int userId, int groupId, String type) throws SQLException
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
		resNode = getMysqlNode(c, nodeUuid,userId, groupId);
		resResource = null;

		if(resNode.next())
		{
			String m_epm = resNode.getString("metadata_epm");
			if( m_epm == null )
				m_epm="";
			result += "<"+resNode.getString("asm_type")+" id='"+resNode.getString("node_uuid")+"'>";
			result += "<metadata "+resNode.getString("metadata")+"/>";
			result += "<metadata-epm "+m_epm+"/>";
			result += "<metadata-wad "+resNode.getString("metadata_wad")+"/>";

			resResource = getMysqlResource(c, resNode.getString("res_node_uuid"));
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
			resResource = getMysqlResource(c, resNode.getString("res_res_node_uuid"));
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
			resResource = getMysqlResource(c, resNode.getString("res_context_node_uuid"));
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
	public Object getNodes(Connection c, MimeType mimeType, String portfoliocode, String semtag, int userId, int groupId, String semtag_parent, String code_parent) throws SQLException
	{
		PreparedStatement st = null;
		String sql;
		ResultSet res = null;
		ResultSet res3 = null;
		ResultSet res4 = null;
		String pid = null;

		pid = this.getPortfolioUuidByPortfolioCode(c, portfoliocode);

		if( "".equals(pid) )
			throw new RestWebApplicationException(Status.NOT_FOUND, "Not found");

		NodeRight right = cred.getPortfolioRight(c, userId, groupId, pid, Credential.READ);
		if(!right.read && !cred.isAdmin(c, userId) && !cred.isPublic(c, null, pid) && !cred.isOwner(c, userId, pid))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		String result = "";

		try
		{
			// Not null, not empty
			// When we have a set, subset, and code of selected item
			/// Searching nodes subset where semtag is under semtag_parent. First filtering is with code_parent
			if(semtag_parent != null && !"".equals(semtag_parent) && code_parent != null && !"".equals(code_parent) )
			{
				/// Temp table where we will search into
				if (dbserveur.equals("mysql"))
				{
					sql = "CREATE TEMPORARY TABLE t_s_node_2(" +
							"node_uuid binary(16)  NOT NULL, " +
							"node_parent_uuid binary(16) DEFAULT NULL, " +
							"asm_type varchar(50) DEFAULT NULL, " +
							"semtag varchar(250) DEFAULT NULL, " +
							"semantictag varchar(250) DEFAULT NULL, " +
							"code varchar(250)  DEFAULT NULL," +
							"node_order int(12) NOT NULL) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
					st = c.prepareStatement(sql);
					st.execute();
					st.close();

					sql = "CREATE TEMPORARY TABLE t_struc_parentid(" +
							"uuid binary(16) UNIQUE NOT NULL, " +
							"node_parent_uuid binary(16), " +
							"t_level INT) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
					st = c.prepareStatement(sql);
					st.execute();
					st.close();

					sql = "CREATE TEMPORARY TABLE t_struc_parentid_2(" +
							"uuid binary(16) UNIQUE NOT NULL, " +
							"node_parent_uuid binary(16), " +
							"t_level INT) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
					st = c.prepareStatement(sql);
					st.execute();
					st.close();
				}
				else if (dbserveur.equals("oracle"))
				{
					String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_s_node_2(" +
							"node_uuid RAW(16)  NOT NULL, " +
							"node_parent_uuid RAW(16) DEFAULT NULL, " +
							"asm_type VARCHAR2(50 CHAR) DEFAULT NULL, " +
							"semtag VARCHAR2(250 CHAR) DEFAULT NULL, " +
							"semantictag VARCHAR2(250 CHAR) DEFAULT NULL, " +
							"code VARCHAR2(250 CHAR)  DEFAULT NULL," +
							"node_order NUMBER(10,0) NOT NULL) ON COMMIT PRESERVE ROWS";
					sql = "{call create_or_empty_table('t_s_node_2','"+v_sql+"')}";
					CallableStatement ocs = c.prepareCall(sql) ;
					ocs.execute();
					ocs.close();

					v_sql = "CREATE GLOBAL TEMPORARY TABLE t_struc_parentid(" +
							"uuid RAW(16) NOT NULL, " +
							"node_parent_uuid RAW(16), " +
							"t_level NUMBER(10,0)"+
							",  CONSTRAINT t_struc_parentid_UK_uuid UNIQUE (uuid)) ON COMMIT PRESERVE ROWS";
					sql = "{call create_or_empty_table('t_struc_parentid','"+v_sql+"')}";
					ocs = c.prepareCall(sql) ;
					ocs.execute();
					ocs.close();

					v_sql = "CREATE GLOBAL TEMPORARY TABLE t_struc_parentid_2(" +
							"uuid RAW(16) NOT NULL, " +
							"node_parent_uuid RAW(16), " +
							"t_level NUMBER(10,0)"+
							",  CONSTRAINT t_struc_parentid_2_UK_uuid UNIQUE (uuid)) ON COMMIT PRESERVE ROWS";
					sql = "{call create_or_empty_table('t_struc_parentid_2','"+v_sql+"')}";
					ocs = c.prepareCall(sql) ;
					ocs.execute();
					ocs.close();
				}

				/// Init temp data table
				sql = "INSERT INTO t_s_node_2(node_uuid, node_parent_uuid, asm_type, semtag, semantictag, code, node_order) " +
						"SELECT node_uuid, node_parent_uuid, asm_type, semtag, semantictag, code, node_order " +
						"FROM node n " +
						"WHERE n.portfolio_id=uuid2bin(?)";
				st = c.prepareStatement(sql);
				st.setString(1, pid);
				st.executeUpdate();
				st.close();

				/// Find parent tag
				try
				{
					sql = "INSERT INTO t_struc_parentid " +
							"SELECT node_uuid, node_parent_uuid, 0 " +
							"FROM t_s_node_2 WHERE semantictag LIKE ? AND code LIKE ?";
					//sql = "SELECT bin2uuid(node_uuid) AS node_uuid, bin2uuid(res_res_node_uuid) AS res_res_node_uuid, node_children_uuid, code, asm_type, label FROM node WHERE portfolio_id = uuid2bin('c884bdcd-2165-469b-9939-14376f7f3500') AND metadata LIKE '%semantictag=%competence%'";
					st = c.prepareStatement(sql);
					st.setString(1, "%"+semtag_parent+"%");
					st.setString(2, "%"+code_parent+"%");
					st.executeUpdate();

					int level = 0;
					int added = 1;
					if (dbserveur.equals("mysql")){
						sql = "INSERT IGNORE INTO t_struc_parentid_2(uuid, node_parent_uuid, t_level) ";
					} else if (dbserveur.equals("oracle")){
						sql = "INSERT /*+ ignore_row_on_dupkey_index(t_struc_parentid_2,t_struc_parentid_2_UK_uuid)*/ INTO t_struc_parentid_2(uuid, node_parent_uuid, t_level) ";
					}
					sql += "SELECT n.node_uuid, n.node_parent_uuid, ? " +
							"FROM node n WHERE n.node_parent_uuid IN (SELECT uuid FROM t_struc_parentid t " +
							"WHERE t.t_level=?)";

					String sqlTemp=null;
					if (dbserveur.equals("mysql")){
						sqlTemp = "INSERT IGNORE INTO t_struc_parentid SELECT * FROM t_struc_parentid_2;";
					} else if (dbserveur.equals("oracle")){
						sqlTemp = "INSERT /*+ ignore_row_on_dupkey_index(t_struc_parentid,t_struc_parentid_UK_uuid)*/ INTO t_struc_parentid SELECT * FROM t_struc_parentid_2";
					}
					PreparedStatement stTemp = c.prepareStatement(sqlTemp);

					st = c.prepareStatement(sql);
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

					/// SELECT semtag from under parent_tag, parent_code
					sql = "SELECT bin2uuid(node_uuid) AS node_uuid, asm_type " +
							"FROM t_s_node_2 " +
							"WHERE semantictag LIKE ? AND node_uuid IN (SELECT uuid FROM t_struc_parentid) " +
							"ORDER BY code, node_order";
					st = c.prepareStatement(sql);
					st.setString(1, "%"+semtag+"%");
					res3 = st.executeQuery();

					result += "<nodes>";
					while( res3.next() )	/// FIXME Could be done in a better way
					{
						result += "<node ";
						result += DomUtils.getXmlAttributeOutput("id", res3.getString("node_uuid"));
						result += ">";
						if (res3.getString("asm_type").equalsIgnoreCase("asmContext"))
						{
							result += getRessource(c, res3.getString("node_uuid"), userId, groupId, "Context");
						}
						else
						{
							result += getRessource(c, res3.getString("node_uuid"), userId, groupId, "nonContext");
						}
						result += "</node>";
					}
					result += "</nodes>";
				}
				catch(Exception ex)
				{
					ex.printStackTrace();
					return null;
				}

					/*
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

								/// FIXME: Search 3 levels down
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
					//*/

				return result;
			}
			else
			{
				sql = "SELECT  bin2uuid(portfolio_id) AS portfolio_id,bin2uuid(root_node_uuid) as root_node_uuid, modif_user_id,modif_date, active, user_id "
						+ "FROM portfolio "
						+ "WHERE portfolio_id = uuid2bin(?) ";
				st = c.prepareStatement(sql);
				st.setString(1, pid);
				res = st.executeQuery();

				if(res.next())
				{
					ResultSet res1 = null;

					try
					{
						res1 = getMysqlNodeUuidBySemanticTag(c, pid, semtag);
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
							result += getRessource(c, res1.getString("node_uuid"), userId, groupId, "Context");
						}
						else
						{
							result += getRessource(c, res1.getString("node_uuid"), userId, groupId, "nonContext");
						}
						result += "</node>";
					}
					result += "</nodes>";
				}
			}
		}
		catch (Exception e)
		{
			try
			{
				// Les 'pooled connection' ne se ferment pas vraiment. On nettoie manuellement les tables temporaires...
				if (dbserveur.equals("mysql")){
					sql = "DROP TEMPORARY TABLE IF EXISTS t_s_node_2, t_struc_parentid, t_struc_parentid_2";
					st = c.prepareStatement(sql);
					st.execute();
					st.close();
				}
			}
			catch( SQLException se ){ se.printStackTrace(); }
			e.printStackTrace();
		}
		finally
		{
			// Les 'pooled connection' ne se ferment pas vraiment. On nettoie manuellement les tables temporaires...
			if (dbserveur.equals("mysql")){
				sql = "DROP TEMPORARY TABLE IF EXISTS t_s_node_2, t_struc_parentid, t_struc_parentid_2";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			}
		}

		return result;
	}

	@Override
	public Object getPortfolioZip(MimeType mimeType, String portfolioUuid, int userId, int groupId, String label, Boolean resource, Boolean files) throws Exception
	{
		return null;
	}


	@Override
	public Object getNodesParent(Connection c, MimeType mimeType, String portfoliocode, String semtag, int userId, int groupId, String semtag_parent, String code_parent) throws Exception
	{
		PreparedStatement st = null;
		String sql;
		ResultSet res = null;
		ResultSet res3 = null;
		ResultSet res4 = null;
		String pid = this.getPortfolioUuidByPortfolioCode(c, portfoliocode);
		String result = "";

		try
		{
			sql = "SELECT  bin2uuid(portfolio_id) AS portfolio_id,bin2uuid(root_node_uuid) as root_node_uuid, modif_user_id,modif_date, active, user_id "
					+ "FROM portfolio "
					+ "WHERE portfolio_id = uuid2bin(?) ";
			st = c.prepareStatement(sql);
			st.setString(1, pid);
			res = st.executeQuery();

			sql = "SELECT  bin2uuid(node_uuid) AS node_uuid,bin2uuid(node_children_uuid) as node_children_uuid, code, semantictag "
					+ "FROM node "
					+ "WHERE portfolio_id = uuid2bin(?) "
					+ "and  metadata LIKE ? "
					+ "and code = ?";
			st = c.prepareStatement(sql);
			st.setString(1, pid);
			st.setString(2, "%semantictag=%"+semtag_parent+"%");
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
					st = c.prepareStatement(sql);
	//				st.setString(1, listChildren[i]);
	//				st.setString(2, semtag);
					st.setString(1, semtag);
					st.setString(2, listChildren[i]);
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
	public boolean isAdmin( Connection c, String uid )
	{
		int userid = Integer.parseInt(uid);
		return cred.isAdmin(c, userid);
	}

	@Override
	public String getUserId(Connection c, String username, String email) throws Exception
	{
		PreparedStatement st;
		String sql;
		ResultSet res;
		String retval = "0";

		try
		{
			if( username != null )
			{
				sql = "SELECT userid FROM credential WHERE login = ? ";
				st = c.prepareStatement(sql);
				st.setString(1, username);
			}
			else if( email != null )
			{
				sql = "SELECT userid FROM credential WHERE email = ? ";
				st = c.prepareStatement(sql);
				st.setString(1, email);
			}
			else
				return retval;
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
	public String createUser(Connection c, String username) throws Exception
	{
		PreparedStatement st = null;
		String sql;
		ResultSet res = null;
		String retval = "0";

		try
		{
			Date date = new Date();
			sql = "INSERT INTO credential SET login=?, display_firstname=?, display_lastname='', password=UNHEX(SHA1(?))";
			if (dbserveur.equals("oracle")){
				sql = "INSERT INTO credential SET login=?, display_firstname=?, display_lastname='', password=crypt(?)";
			}
			st = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
			if (dbserveur.equals("oracle")){
				st = c.prepareStatement(sql, new String[]{"userid"});
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
			logger.error(ex.getMessage());
			ex.printStackTrace();
		}
		finally
		{
			if( res != null )
				res.close();
			if( st != null )
				st.close();
		}

		return retval;
	}

	@Override
	public String getGroupByName( Connection c, String name )
	{
		PreparedStatement st;
		String sql;
		ResultSet res;
		String retval = "0";

		try
		{
			sql = "SELECT gid FROM group_info WHERE label=? ";
			st = c.prepareStatement(sql);
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
	public String createGroup( Connection c, String name )
	{
		PreparedStatement st;
		String sql;
		int retval = 0;

		try
		{
			sql = "INSERT INTO group_right_info(owner, label) VALUES(1,?)";
			st = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
			if (dbserveur.equals("oracle")){
				st = c.prepareStatement(sql, new String[]{"grid"});
			}
			st.setString(1, name);
			st.executeUpdate();
			ResultSet rs = st.getGeneratedKeys();
			if( rs.next() )
			{
				retval = rs.getInt(1);
				rs.close(); st.close();

				sql = "INSERT INTO group_info(grid, owner, label) VALUES(?,1,?)";
				st = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
				if (dbserveur.equals("oracle")){
					st = c.prepareStatement(sql, new String[]{"gid"});
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
	public boolean isUserInGroup( Connection c, String uid, String gid )
	{
		PreparedStatement st;
		String sql;
		ResultSet res;
		boolean retval = false;

		try
		{
			sql = "SELECT userid FROM group_user WHERE userid=? AND gid=?";
			st = c.prepareStatement(sql);
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
	public Set<String[]> getNotificationUserList( Connection c, int userId, int groupId, String uuid )
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
			st = c.prepareStatement(sql);
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
			st = c.prepareStatement(sql);
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
	public boolean touchPortfolio( Connection c, String fromNodeuuid, String fromPortuuid )
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
				st = c.prepareStatement(sql);
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
				st = c.prepareStatement(sql);
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

	@Override
	public String[] logViaEmail( Connection c, String email )
	{
		String[] data = null;
		String sql;
		PreparedStatement st = null;
		ResultSet res = null;

		try
		{
			sql = "SELECT userid, login FROM credential WHERE email=?";
			st = c.prepareStatement(sql);
			st.setString(1, email);
			res = st.executeQuery();

			if( res.next() )
			{
				int userid = res.getInt(1);
				String username = res.getString(2);
				data = new String[5];
				data[1] = username;
				data[2] = Integer.toString(userid);
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

		return data;
	}

	@Override
	public String emailFromLogin( Connection c, String username )
	{
		if( "".equals(username) || username == null )
			return "";

		String email = null;;

		String sql;
		PreparedStatement st = null;
		ResultSet res = null;

		try
		{
			sql = "SELECT email FROM credential WHERE login=?";
			st = c.prepareStatement(sql);
			st.setString(1, username);
			res = st.executeQuery();

			if( res.next() )
				email = res.getString(1);
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

		return email;
	}

	@Override
	public boolean changePassword( Connection c, String username, String password )
	{
		boolean changed = false;

		String sql;
		PreparedStatement st = null;
		ResultSet res = null;

		try
		{
			sql = "UPDATE credential SET password=UNHEX(SHA1(?)) WHERE login=?";
			st = c.prepareStatement(sql);
			st.setString(1, password);
			st.setString(2, username);
			st.executeUpdate();

			changed = true;
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

		return changed;
	}

	@Override
	public boolean registerUser( Connection c, String username, String password )
	{
		boolean changed = false;
		String sql = "";
		PreparedStatement st = null;
		ResultSet res = null;
		try
		{
			// Check if user exists
			sql = "SELECT login FROM credential WHERE login=?";
			st = c.prepareStatement(sql);
			st.setString(1, username);
			res = st.executeQuery();

			if( !res.next() )
			{
				res.close();
				st.close();

				// Insert user
				sql = "INSERT INTO credential(login, password, is_designer, display_firstname, display_lastname) VALUES(?, UNHEX(SHA1(?)), 1, '', '')";
				st = c.prepareStatement(sql);
				st.setString(1, username);
				st.setString(2, password);
				st.executeUpdate();

				changed = true;
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


		return changed;
	}

}
