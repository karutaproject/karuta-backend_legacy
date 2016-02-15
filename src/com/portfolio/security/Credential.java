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

package com.portfolio.security;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Random;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.portfolio.data.utils.ConfigUtils;

/**
 * Servlet implementation class Credential
 */
public class Credential
{
	final Logger logger = LoggerFactory.getLogger(Credential.class);

//	private final Connection connection;
	public static final String NONE = "none";
	public static final String ADD = "add";
	public static final String READ = "read";
	public static final String WRITE = "write";
	public static final String SUBMIT = "submit";
	public static final String DELETE = "delete";
	public static final String LIER = "lier";
	public static final String DELIER = "delier";

	private String dbserveur = null;

	/**
	 * @param connection
	 * @see HttpServlet#HttpServlet()
	 */
	public Credential()
	{
		super();
		dbserveur = ConfigUtils.get("serverType");
//		this.connection = connection;
	}

	public int getAllGroupRightId(Connection c, String portfolio_id)
	{
		PreparedStatement st;
		String sql;
		ResultSet res;
		int grid = 0;
		String label = "all";

		try
		{
			{
				/// Requete SQL qui cherche le grid du gr "all" en fonction du portfolioid
				sql = "SELECT grid FROM group_right_info gr WHERE portfolio_id = uuid2bin(?) AND label = ? ";
				st = c.prepareStatement(sql);
				st.setString(1, portfolio_id);
				st.setString(2, label);
				res = st.executeQuery();

				if (res.next())
				{
					grid = res.getInt("grid");
				}
				return grid;
			}
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			return grid;
		}
	}

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	@Deprecated
	public void doGet( HttpServletRequest request, HttpServletResponse response ) throws ServletException, IOException
	{
		Cookie[] cookies = request.getCookies();
		String[] cred = DBConnect.getLogin(cookies);
		// Check credential
		DBConnect db = new DBConnect(cred[0]);
		long valid = db.hasCredentials(cred[0], cred[1]);

		ServletOutputStream output = response.getOutputStream();
		output.print(valid);

		db.cleanup();
		output.close();
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 * retourne 2 chaines : [0] = cookie login, [1] = cookie credential
	 */
	@Deprecated
	public String[] doPost( String login, String password ) throws ServletException, IOException
	{
		DBConnect db;
		long valid;

		if(login.contains("#"))
		{
			String[] logins = login.split("#");
			String loginTestUser = logins[0];
			String loginUser = logins[1];

			db = new DBConnect(loginTestUser);
			if(db.canSubstitute(loginUser))
			{
				valid = db.isValid(loginUser, password);
				login = loginTestUser;
			}
			else valid = 0;

		}
		else
		{
			db = new DBConnect(login);
			valid = db.isValid(login, password);
		}






		String[] cookie = new String[3];

		// String output = password;
		String tokenID = null;
		int age = 0;


		if( valid != 0 )
		{
			Random rand = new Random();
			tokenID = Long.toString(rand.nextLong());
			age = 3600;

			cookie[0] = login;
			cookie[1] = tokenID;
			cookie[2] = Long.toString(valid);

			db.setCookie(login, tokenID, age);
			System.out.println("valide");
			db.cleanup();
			return cookie;
		}
		else
		{
			System.out.println(401);
			db.cleanup();
			return null;
			//cookie = null;
		}

		/*Cookie userLogin = new Cookie("login", login);
    userLogin.setPath("/");
    userLogin.setMaxAge(age);
    password.addCookie(userLogin);*/

		/*Cookie userCookie = new Cookie("credential", tokenID);
    userCookie.setPath("/");
    userCookie.setMaxAge(age);
    password.addCookie(userCookie);*/

		/// Keep track of tokenID




		//password.flushBuffer(); /// Ensure everything is written, sometime one of the cookies is not set
	}

	/**
	 * @see HttpServlet#doPut(HttpServletRequest request, HttpServletResponse response)
	 */
	@Deprecated
	protected void doPut( HttpServletRequest request, HttpServletResponse response ) throws ServletException, IOException
	{
		/// UNHEX(SHA1('password'))
		/// Create account
	}

	public int getMysqlUserUid(Connection c, String login) throws Exception
	{
		PreparedStatement st;
		String sql;
		ResultSet res;
		int uid=0;

		try
		{
			sql = "SELECT userid FROM credential WHERE login = ?";
			st = c.prepareStatement(sql);
			st.setString(1, login);
			res = st.executeQuery();
			if( res.next() )
				uid = res.getInt("userid");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
		}
		return uid;
	}


	//test pour l'affichage du getPortfolio
	public NodeRight getPortfolioRight(Connection c, int userId, int groupId, String portfolioUuid, String droit)
	{
		PreparedStatement st;
		String sql;
		ResultSet res;
//		boolean reponse = false;
		NodeRight reponse = new NodeRight(false,false,false,false,false,false);

		try
		{
			//sql = "SELECT distinct portfolio_id FROM GroupRights gr, group_user gu, group_info gi, node n WHERE gu.gid = gi.gid AND gi.grid = gr.grid and gr.id = n.node_uuid AND gu.userid = ? and gr.grid =  '26'";
			sql = "SELECT user_id, bin2uuid(root_node_uuid) as root_node_uuid FROM portfolio WHERE portfolio_id = uuid2bin(?)";
			st = c.prepareStatement(sql);
			st.setString(1, portfolioUuid);
			res = st.executeQuery();
			res.next();

			/*
			if(res.getInt("user_id") == userId || isAdmin(userId))
			{
				reponse = true;
			}
			else
			{
			}
			//*/

			reponse = getNodeRight(c, userId, groupId, res.getString("root_node_uuid"), droit);

		}
		catch(Exception ex)
		{
			ex.printStackTrace();
//			reponse = false;
		}

		return reponse;
	}

	public boolean hasNodeRight(Connection c, int userId, int groupId, String node_uuid, String droit)
	{
		NodeRight nodeRight = getNodeRight(c, userId, groupId, node_uuid, null);
		if(droit.equals(READ))
			return nodeRight.read;
		else if(droit.equals(WRITE))
			return nodeRight.write;
		else if(droit.equals(SUBMIT))
			return nodeRight.submit;
		else if(droit.equals(DELETE))
			return nodeRight.delete;
		else
			return false;
	}

	//test pour l'affichage des differentes methodes de Node
	public NodeRight getNodeRight(Connection c, int userId, int groupId, String node_uuid, String label)
	{
		PreparedStatement st=null;
		String sql;
		ResultSet res=null;

		// On initialise les droits à false : par defaut accès à rien
		NodeRight nodeRight = new NodeRight(false,false,false,false,false,false);

		try
		{
			long t1=0, t2=0, t3=0, t4=0, t5=0, t6=0;
			long t0 = System.currentTimeMillis();
			if( getPortfolioAdmin(c, userId, node_uuid) || isAdmin(c, userId) )
			{
				nodeRight.read = true;
				nodeRight.write = true;
				nodeRight.submit = true;
				nodeRight.delete = true;
			}
			else if( isDesigner(c, userId, node_uuid) )	/// Droits via le partage totale (obsolète) ou si c'est designer
			{
				nodeRight.read = true;
				nodeRight.write = true;
				nodeRight.submit = true;
				nodeRight.delete = true;
			}
			else
			{
				t1 = System.currentTimeMillis();
				// Sans sélection de groupe
				if(groupId == 0)
				{
					// On regarde si la personne à un droit sur ce noeud dans l'un des groupes du portfolio
					// Pourrait être un casse tête si un noeud est référencé dans plusieurs groupes
					sql = "SELECT gi.gid, gi.grid, gri.label " +
							"FROM node n, group_right_info gri, group_info gi, group_user gu " +
							"WHERE n.portfolio_id=gri.portfolio_id " +
							"AND gri.grid=gi.grid " +
							"AND gi.gid=gu.gid " +
							"AND gu.userid=? " +
							"AND n.node_uuid=uuid2bin(?)";
					st = c.prepareStatement(sql);
					st.setInt(1, userId);
					st.setString(2, node_uuid);
					res = st.executeQuery();

					if( res.next() )
					{
						groupId = res.getInt(1);	// On prend le premier
						int grid = res.getInt(2);
						String groupName = res.getString(3);
						// Spécifie dans quel contexte on lui donne le droit
						nodeRight.groupId = groupId;
						nodeRight.rrgId = grid;
						nodeRight.groupLabel = groupName;
					}

					st.close();
					res.close();
				}

				t2 = System.currentTimeMillis();
				
				/// Sinon on évalue le droit donnée directement
				sql = "SELECT bin2uuid(id) as id, RD, WR, DL, SB, AD " +
						"FROM group_rights gr, group_user gu, group_info gi " +
						"WHERE gu.gid = gi.gid " +
						"AND gi.grid = gr.grid AND gu.userid = ? " +
						"AND gr.id = uuid2bin(?) AND gu.gid = ?";
				st = c.prepareStatement(sql);
				st.setInt(1, userId);
				st.setString(2, node_uuid);
				st.setInt(3, groupId);
				res = st.executeQuery();
				if(res.next())
				{
					nodeRight.read = nodeRight.read || (res.getInt("RD") == 1);
					nodeRight.write = nodeRight.write || (res.getInt("WR") == 1);
					nodeRight.submit = nodeRight.submit || (res.getInt("SB") == 1);
					nodeRight.delete = nodeRight.delete || (res.getInt("DL") == 1);
				}
				
				st.close();
				res.close();
				
				t3 = System.currentTimeMillis();

				/// Les droits donné spécifiquement à l'utilisateur
				sql = "SELECT bin2uuid(id) as id, RD, WR, DL, SB, AD " +
						"FROM group_rights " +
						"WHERE id=uuid2bin(?) " +
						"AND grid=(SELECT grid " +
						"FROM credential c, group_right_info gri, node n " +
						"WHERE c.login=gri.label AND c.userid=? AND gri.portfolio_id=n.portfolio_id AND n.node_uuid=uuid2bin(?))";
				st = c.prepareStatement(sql);
				st.setString(1, node_uuid);
				st.setInt(2, userId);
				st.setString(3, node_uuid);

				res = st.executeQuery();
				if(res.next())
				{
					nodeRight.read = nodeRight.read || (res.getInt("RD") == 1);
					nodeRight.write = nodeRight.write || (res.getInt("WR") == 1);
					nodeRight.submit = nodeRight.submit || (res.getInt("SB") == 1);
					nodeRight.delete = nodeRight.delete || (res.getInt("DL") == 1);
				}

				res.close();
				st.close();

				t4 = System.currentTimeMillis();
				
				/// Les droits que l'on a du groupe "all"
				/// NOTE: Pas de vérification si la personne est dans le groupe 'all'
				///  Le fonctionnement voulu est différent de ce que j'avais prévu, mais ça marche aussi
				sql = "SELECT bin2uuid(id) as id, RD, WR, DL, SB, AD " +
						"FROM group_rights " +
						"WHERE id=uuid2bin(?) " +
						"AND grid=(SELECT gri2.grid " +
						"FROM group_info gi " +
						"INNER JOIN group_right_info gri1 ON gi.grid=gri1.grid " +
						"INNER JOIN group_right_info gri2 ON gri1.portfolio_id=gri2.portfolio_id " +
						"WHERE gi.gid=? AND gri2.label='all')";
				st = c.prepareStatement(sql);
				st.setString(1, node_uuid);
				st.setInt(2, groupId);

				res = st.executeQuery();
				if(res.next())
				{
					nodeRight.read = nodeRight.read || (res.getInt("RD") == 1);
					nodeRight.write = nodeRight.write || (res.getInt("WR") == 1);
					nodeRight.submit = nodeRight.submit || (res.getInt("SB") == 1);
					nodeRight.delete = nodeRight.delete || (res.getInt("DL") == 1);
				}
				res.close();
				st.close();
				
				t5 = System.currentTimeMillis();
			} // fin else

			/// Public rights (last chance for rights)
			if( isPublic( c, node_uuid, null ) )
			{
				nodeRight.read = true;
			}
			t6 = System.currentTimeMillis();
			
			/*
			long checkSysInfo = t1-t0;
			long groupSelect = t2-t1;
			long rightFromGroup = t3-t2;
			long rightSpecificUser = t4-t3;
			long rightFromAll = t5-t4;
			long checkPublic = t6-t5;
			System.out.println("=====Check Rights=====");
			System.out.println("Check sys info: "+checkSysInfo);
			System.out.println("Group selection: "+groupSelect);
			System.out.println("Right from group: "+rightFromGroup);
			System.out.println("Right for user: "+rightSpecificUser);
			System.out.println("Right from all: "+rightFromAll);
			System.out.println("Check public: "+checkPublic);
			//*/
		}
		catch(Exception ex)
		{
			logger.error(ex.getMessage());
			ex.printStackTrace();
		}
		finally
		{
			if( st != null ) try{ st.close(); }catch( SQLException e ){ e.printStackTrace(); }
			if( res != null ) try{ res.close(); }catch( SQLException e ){ e.printStackTrace(); }
		}

		return nodeRight;

	}

	/// From node, check if portoflio has user 'sys_public' in group 'all'
	/// To differentiate between 'public' to the world, and 'public' to people with an account
	public boolean isPublic( Connection c, String node_uuid, String portfolio_uuid )
	{
		PreparedStatement st = null;
		ResultSet res = null;
		String sql = "";
		boolean val = false;
		try
		{
			if( node_uuid != null )
			{
				sql = "SELECT gu.userid " +
						"FROM node n, group_right_info gri, group_info gi, group_user gu, credential c " +
						"WHERE gri.grid=gi.grid AND gu.gid=gi.gid AND gu.userid=c.userid AND " +
						"n.node_uuid=uuid2bin(?) AND n.portfolio_id=gri.portfolio_id " +
						"AND gri.label='all' " +
						"AND c.login='sys_public'";
				st = c.prepareStatement(sql);
				st.setString(1, node_uuid);
			}
			else
			{
				sql = "SELECT gu.userid " +
						"FROM group_right_info gri, group_info gi, group_user gu, credential c " +
						"WHERE gri.grid=gi.grid AND gu.gid=gi.gid AND gu.userid=c.userid AND " +
						"gri.portfolio_id=uuid2bin(?) " +
						"AND gri.label='all' " +
						"AND c.login='sys_public'";
				st = c.prepareStatement(sql);
				st.setString(1, portfolio_uuid);
			}
			res = st.executeQuery();
			if(res.next())
			{
				val = true;
			}
			res.close();
			st.close();
		}
		catch( Exception e )
		{
			e.printStackTrace();
		}
		finally
		{
			if( st != null ) try{ st.close(); }catch( SQLException e ){ e.printStackTrace(); }
			if( res != null ) try{ res.close(); }catch( SQLException e ){ e.printStackTrace(); }
		}
		return val;
	}

	public int getPublicUid( Connection c )
	{
		String sql;
		PreparedStatement st = null;
		ResultSet res = null;
		int publicid = 0;

		try
		{
			// Fetching 'sys_public' userid
			sql = "SELECT userid FROM credential WHERE login='sys_public'";
			st = c.prepareStatement(sql);
			res = st.executeQuery();
			if(res.next())
				publicid = res.getInt(1);
		}
		catch( Exception e )
		{
			e.printStackTrace();
		}
		finally
		{
			if( st != null ) try{ st.close(); }catch( SQLException e ){ e.printStackTrace(); }
			if( res != null ) try{ res.close(); }catch( SQLException e ){ e.printStackTrace(); }
		}

		return publicid;
	}

	public NodeRight getPublicRight(Connection c, int userId, int groupId, String node_uuid, String label)
	{
		String sql;
		PreparedStatement st = null;
		ResultSet res = null;
		NodeRight nodeRight = new NodeRight(false,false,false,false,false,false);

		/// userId doit être celui de publique, pire cas c'est un autre utilisateur
		/// mais si cette personne n'as pas de droits, il n'y aura rien en retour
		try
		{
			/// A partir du moment ou c'est publique, peu importe le groupe d'appartenance
			/// le noeud est accessible
			sql = "SELECT bin2uuid(id) as id, RD, WR, DL, SB, AD " +
					"FROM group_rights gr " +
					"LEFT JOIN group_right_info gri ON gr.grid=gri.grid " +
					"LEFT JOIN group_info gi ON gri.grid=gi.grid " +
					"LEFT JOIN group_user gu ON gi.gid=gu.gid " +
					"WHERE id=uuid2bin(?) " +
					"AND gri.label='all' " +
					"AND gu.userid=?";
			st = c.prepareStatement(sql);
			st.setString(1, node_uuid);
			st.setInt(2, userId);

			res = st.executeQuery();
			if(res.next())
			{
				nodeRight.read = nodeRight.read || (res.getInt("RD") == 1);
				nodeRight.write = nodeRight.write || (res.getInt("WR") == 1);
				nodeRight.submit = nodeRight.submit || (res.getInt("SB") == 1);
				nodeRight.delete = nodeRight.delete || (res.getInt("DL") == 1);
			}
		}
		catch( Exception e )
		{
			e.printStackTrace();
		}
		finally
		{
			if( st != null ) try{ st.close(); }catch( SQLException e ){ e.printStackTrace(); }
			if( res != null ) try{ res.close(); }catch( SQLException e ){ e.printStackTrace(); }
		}

		return nodeRight;
	}

	//recuperation de l'uuid du createur du portfolio
	public boolean getPortfolioAdmin(Connection c, int userId, String node_uuid)
	{
		PreparedStatement st=null;
		String sql;
		ResultSet res=null;
		boolean reponse = false;

		try
		{
			sql = "SELECT user_id " +
					"FROM portfolio p, node n " +
					"WHERE n.portfolio_id = p.portfolio_id AND n.node_uuid = uuid2bin(?)";
			st = c.prepareStatement(sql);
			st.setString(1, node_uuid);
			res = st.executeQuery();

			if(res.next() && res.getInt("user_id") == userId)
			{
				reponse = true;
			}
			else reponse = false;
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
		}
		finally
		{
			if( st != null ) try{ st.close(); }catch( SQLException e ){ e.printStackTrace(); }
			if( res != null ) try{ res.close(); }catch( SQLException e ){ e.printStackTrace(); }
		}

		return reponse;
	}

	//recuperation du portfolio_id
	public String getPortfolio_id(Connection c, String node_uuid)
	{
		PreparedStatement st;
		String sql;
		ResultSet res;
		String portfolio_id = null;

		try
		{
			//sql = "SELECT distinct portfolio_id FROM GroupRights gr, group_user gu, group_info gi, node n WHERE gu.gid = gi.gid AND gi.grid = gr.grid and gr.id = n.node_uuid AND gu.userid = ? and gr.grid =  '26'";
			sql = "SELECT bin2uuid(portfolio_id) as portfolio_id FROM node n WHERE node_uuid = uuid2bin(?)";
			st = c.prepareStatement(sql);
			st.setString(1, node_uuid);
			res = st.executeQuery();

			if (res.next())
			{
				portfolio_id = res.getString("portfolio_id");
			}

		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			portfolio_id = "faux";
		}

		return portfolio_id;
	}


	public boolean postRights( Connection c, String role, int userId, String uuid, NodeRight rights, String portfolioUuid )
	{
		PreparedStatement st=null;
		PreparedStatement st1=null;
		PreparedStatement st2=null;
		String sql;
		String sqlUpdate;
		String sqlInsert;
		ResultSet generatedKeys;
		ResultSet res=null;
		ResultSet res2=null;
		int grid = -1;
		boolean reponse = false;

		try
		{
			if(role != null && !role.trim().equals("") && rights !=null)
			{
				if( "user".equals(role) ) // Si le nom de group est 'user'. Le remplacer par le role de l'utilisateur (voir pour juste le nom plus tard)
				{
					sql = "SELECT bin2uuid(portfolio_id) as portfolio_id, gri.grid " +
							"FROM group_user gu " +
							"LEFT JOIN group_info gi ON gu.gid=gi.gid " +
							"LEFT JOIN group_right_info gri ON gi.grid=gri.grid " +
							"WHERE portfolio_id = uuid2bin(?) AND gu.userid = ?";
					st = c.prepareStatement(sql);
					st.setString(1, portfolioUuid);
					st.setInt(2, userId);
					res = st.executeQuery();
				}
				else if( !"".equals(portfolioUuid) )	/// Rôle et portfolio
				{
					sql = "SELECT bin2uuid(portfolio_id) as portfolio_id FROM group_right_info gri  WHERE portfolio_id = uuid2bin(?) AND label = ?";
					st = c.prepareStatement(sql);
					st.setString(1, portfolioUuid);
					st.setString(2, role);
					res = st.executeQuery();

					if(!res.next())    /// Groupe non-existant
					{
						sqlInsert	= "INSERT INTO group_right_info(owner,label, portfolio_id) Values (?,?, uuid2bin(?)) ";
						st1 = c.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS);
						if (dbserveur.equals("oracle")){
							st1 = c.prepareStatement(sqlInsert, new String[]{"grid"});
						}
						st1.setInt(1, userId);
						st1.setString(2, role);
						st1.setString(3, portfolioUuid);
						st1.executeUpdate();

						generatedKeys = st1.getGeneratedKeys();

						if (generatedKeys.next()) {
							grid = generatedKeys.getInt(1);
						}
						st1.close();

						/// Crée une copie dans group_info, le temps de ré-organiser tout ça
						sqlInsert = "INSERT INTO group_info(grid,owner,label) Values (?,?,?) ";
						st1 = c.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS);
						if (dbserveur.equals("oracle")){
							st1 = c.prepareStatement(sqlInsert, new String[]{"gid"});
						}
						st1.setInt(1, grid);
						st1.setInt(2, userId);
						st1.setString(3, role);
						st1.executeUpdate();
						st1.close();
					}

					sql = "SELECT bin2uuid(portfolio_id) as portfolio_id,  grid FROM group_right_info gri  WHERE portfolio_id = uuid2bin(?) AND label = ?";
					st = c.prepareStatement(sql);
					st.setString(1, portfolioUuid);
					st.setString(2, role);
					res = st.executeQuery();
				}
				else	// Rôle et uuid
				{
					sql = "SELECT bin2uuid(gri.portfolio_id) as portfolio_id, gri.grid " +
							"FROM group_rights gr, group_right_info gri " +
							"WHERE gr.id = uuid2bin(?) AND " +
							"gri.label = ?";
					st = c.prepareStatement(sql);
					st.setString(1, uuid);
					st.setString(2, role);
					res = st.executeQuery();
				}

				if(res.next())  /// On a trouvé notre groupe
				{
					if (grid == -1)
					{
						grid = res.getInt("grid");
					}

					sql = "SELECT bin2uuid(id) as id, grid FROM group_rights gri WHERE  grid=? AND id = uuid2bin(?) ";
					st2 = c.prepareStatement(sql);
					st2.setInt(1, grid);
					st2.setString(2, uuid);
					res2 = st2.executeQuery();

					if(res2.next())
					{
						try
						{
							sqlUpdate = "UPDATE group_rights SET RD=?, WR=?, DL=?, SB=? WHERE grid=? AND id=uuid2bin(?)";
							st = c.prepareStatement(sqlUpdate);
							st.setBoolean(1, rights.read);
							st.setBoolean(2, rights.write);
							st.setBoolean(3, rights.delete);
							st.setBoolean(4, rights.submit);
							st.setInt(5, grid);
							st.setString(6, uuid);
							st.executeUpdate();
						}
						catch(Exception ex)
						{

						}
						finally
						{
							if( st != null ) try{ st.close(); }catch( SQLException e ){ e.printStackTrace(); }
						}
					}
					else
					{
						try
						{
							sqlUpdate = "INSERT INTO group_rights(grid, id, RD, WR, DL, SB) VALUES (?, uuid2bin(?),?,?,?,?)";
							st = c.prepareStatement(sqlUpdate);
							st.setInt(1, grid);
							st.setString(2, uuid);
							st.setBoolean(1, rights.read);
							st.setBoolean(2, rights.write);
							st.setBoolean(3, rights.delete);
							st.setBoolean(4, rights.submit);
							st.executeUpdate();
						}
						catch(Exception ex)
						{

						}
						finally
						{
							if( st != null ) try{ st.close(); }catch( SQLException e ){ e.printStackTrace(); }
						}
					}

				}
			}
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			reponse = false;
		}
		finally
		{
			if( res2 != null ) try{ res2.close(); }catch( SQLException e ){ e.printStackTrace(); }
			if( res != null ) try{ res.close(); }catch( SQLException e ){ e.printStackTrace(); }
			if( st != null ) try{ st.close(); }catch( SQLException e ){ e.printStackTrace(); }
			if( st1 != null ) try{ st1.close(); }catch( SQLException e ){ e.printStackTrace(); }
			if( st2 != null ) try{ st2.close(); }catch( SQLException e ){ e.printStackTrace(); }
		}

		return false;
	}

	//Ajout des droits du portfolio dans group_right_info, group_rights
	// FIXME: Ne peut pas enlever des droits
	public boolean postGroupRight(Connection c, String label, String uuid, String droit, String portfolioUuid, int userId)
	{
		PreparedStatement st=null;
		PreparedStatement st1=null;
		PreparedStatement st2=null;
		String sql;
		String sqlUpdate;
		String sqlInsert;
		ResultSet res=null;
		ResultSet res2=null;
		int RD = 0;
		int WR = 0;
		int DL = 0;
		int SB = 0;
		int AD = 0;
		int grid = -1;
		boolean reponse = false;


		try
		{

			if (droit == READ)
			{
				RD = 1;
			}
			else if (droit == WRITE)
			{
				WR = 1;
			}
			else if (droit == DELETE)
			{
				DL = 1;
			}
			else if (droit == SUBMIT)
			{
				SB = 1;
			}
			else if ( droit == ADD )
			{
				AD = 1;
			}

			if(label != null && !label.trim().equals("") && droit !=null)
			{
				if( "user".equals(label) )  // Si le nom de group est 'user'. Le remplacer par le role de l'utilisateur (voir pour juste le nom plus tard)
				{
					sql = "SELECT bin2uuid(portfolio_id) as portfolio_id, gri.grid " +
							"FROM group_user gu " +
							"LEFT JOIN group_info gi ON gu.gid=gi.gid " +
							"LEFT JOIN group_right_info gri ON gi.grid=gri.grid " +
							"WHERE portfolio_id = uuid2bin(?) AND gu.userid = ?";
					st = c.prepareStatement(sql);
					st.setString(1, portfolioUuid);
					st.setInt(2, userId);
					res = st.executeQuery();
				}
				else if( !"".equals(portfolioUuid) )	/// Rôle et portfolio
				{
					sql = "SELECT bin2uuid(portfolio_id) as portfolio_id FROM group_right_info gri  WHERE portfolio_id = uuid2bin(?) AND label = ?";
					st = c.prepareStatement(sql);
					st.setString(1, portfolioUuid);
					st.setString(2, label);
					res = st.executeQuery();

					if(!res.next())    /// Groupe non-existant
					{
						res.close();
						st.close();

						sqlInsert	= "INSERT INTO group_right_info(owner, label, change_rights, portfolio_id) Values (?, ?, 0, uuid2bin(?)) ";
						if( "mysql".equals(dbserveur) )
							st1 = c.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS);
						if (dbserveur.equals("oracle"))
							st1 = c.prepareStatement(sqlInsert, new String[]{"grid"});
						st1.setInt(1, userId);
						st1.setString(2, label);
						st1.setString(3, portfolioUuid);
						st1.executeUpdate();

						ResultSet keys = st1.getGeneratedKeys();
						keys.next();
						grid = keys.getInt(1);
						st1.close();

						/// Crée une copie dans group_info, le temps de ré-organiser tout ça
						sqlInsert = "INSERT INTO group_info(grid,owner,label) Values (?,?,?) ";
						st1 = c.prepareStatement(sqlInsert);
						st1.setInt(1, grid);
						st1.setInt(2, userId);
						st1.setString(3, label);
						st1.executeUpdate();
						st1.close();
					}

					sql = "SELECT bin2uuid(portfolio_id) as portfolio_id,  grid FROM group_right_info gri  WHERE portfolio_id = uuid2bin(?) AND label = ?";
					st = c.prepareStatement(sql);
					st.setString(1, portfolioUuid);
					st.setString(2, label);
					res = st.executeQuery();
				}
				else	// Rôle et uuid
				{
					sql = "SELECT bin2uuid(gri.portfolio_id) as portfolio_id, gri.grid " +
							"FROM group_rights gr, group_right_info gri " +
							"WHERE gr.id = uuid2bin(?) AND " +
							"gri.label = ?";
					st = c.prepareStatement(sql);
					st.setString(1, uuid);
					st.setString(2, label);
					res = st.executeQuery();
				}

				if(res.next())  /// On a trouvé notre groupe
				{
					if (grid == -1)
					{
						grid = res.getInt("grid");
					}
					res.close();
					st.close();

					sql = "SELECT bin2uuid(id) as id, grid FROM group_rights gri WHERE  grid=? AND id = uuid2bin(?) ";
					st2 = c.prepareStatement(sql);
					st2.setInt(1, grid);
					st2.setString(2, uuid);
					res2 = st2.executeQuery();

					if(res2.next())
					{
						res2.close();
						st2.close();
						//if((grid == res2.getInt("grid") || uuid.equals(res2.getString("id"))))
						//{
						try
						{

							if(droit == READ)
							{
								sqlUpdate = "UPDATE group_rights SET RD = ?  WHERE grid = ? AND id = uuid2bin(?)";
								st = c.prepareStatement(sqlUpdate);
								st.setInt(1, RD);
								st.setInt(2, grid);
								st.setString(3, uuid);
								st.executeUpdate();
							}
							else if (droit == WRITE)
							{
								sqlUpdate = "UPDATE group_rights SET WR = ? WHERE grid = ? AND id = uuid2bin(?)";
								st = c.prepareStatement(sqlUpdate);
								st.setInt(1, WR);
								st.setInt(2, grid);
								st.setString(3, uuid);
								st.executeUpdate();
							}
							else if (droit == DELETE)
							{
								sqlUpdate = "UPDATE group_rights SET DL = ? WHERE grid = ? AND id = uuid2bin(?)";
								st = c.prepareStatement(sqlUpdate);
								st.setInt(1, DL);
								st.setInt(2, grid);
								st.setString(3, uuid);
								st.executeUpdate();
							}
							else if (droit == SUBMIT)
							{
								//// FIXME: ajoute le rules_id pré-canné pour certaine valeurs
								sqlUpdate = "UPDATE group_rights SET SB = ? WHERE grid = ? AND id = uuid2bin(?)";
								st = c.prepareStatement(sqlUpdate);
								st.setInt(1, SB);
								st.setInt(2, grid);
								st.setString(3, uuid);
								st.executeUpdate();
							}
							else if ( droit == ADD )
							{
								sqlUpdate = "UPDATE group_rights SET AD = ? WHERE grid = ? AND id = uuid2bin(?)";
								st = c.prepareStatement(sqlUpdate);
								st.setInt(1, AD);
								st.setInt(2, grid);
								st.setString(3, uuid);
								st.executeUpdate();
							}
							else  // Les droits d'exécuter des actions. FIXME Pas propre, à changer plus tard.
							{
								sqlUpdate = "UPDATE group_rights SET rules_id = ? WHERE grid = ? AND id = uuid2bin(?)";
								st = c.prepareStatement(sqlUpdate);
								st.setString(1, droit);
								st.setInt(2, grid);
								st.setString(3, uuid);
								st.executeUpdate();
							}
						}
						catch(Exception ex)
						{

						}
						finally
						{
							if( st != null ) try{ st.close(); }catch( SQLException e ){ e.printStackTrace(); }
						}
						//}
						//else{

						//}
					}
					else  // FIXME Pas de noeud existant. Il me semble qu'il y a un UPDATE OR INSERT dans MySQL. A vérifier et arranger au besoin.
					{
						try
						{
							if(droit == READ)
							{
								sqlInsert = "INSERT INTO group_rights(grid, id, RD) VALUES (?, uuid2bin(?),?)";
								st = c.prepareStatement(sqlInsert);
								st.setInt(1, grid);
								st.setString(2, uuid);
								st.setInt(3, RD);
								st.executeUpdate();
							}
							else if (droit == WRITE)
							{
								sqlInsert = "INSERT INTO group_rights(grid, id, WR) VALUES (?, uuid2bin(?),?)";
								st = c.prepareStatement(sqlInsert);
								st.setInt(1, grid);
								st.setString(2, uuid);
								st.setInt(3, WR);
								st.executeUpdate();
							}
							else if (droit == DELETE)
							{
								sqlInsert = "INSERT INTO group_rights(grid, id, DL) VALUES (?, uuid2bin(?),?)";
								st = c.prepareStatement(sqlInsert);
								st.setInt(1, grid);
								st.setString(2, uuid);
								st.setInt(3, DL);
								st.executeUpdate();
							}
							else if (droit == SUBMIT)
							{
								sqlInsert = "INSERT INTO group_rights(grid, id, SB) VALUES (?, uuid2bin(?),?)";
								st = c.prepareStatement(sqlInsert);
								st.setInt(1, grid);
								st.setString(2, uuid);
								st.setInt(3, SB);
								st.executeUpdate();
							}
							else
							{
								sqlInsert = "INSERT INTO group_rights(grid, id, AD) VALUES (?, uuid2bin(?),?)";
								st = c.prepareStatement(sqlInsert);
								st.setInt(1, grid);
								st.setString(2, uuid);
								st.setInt(3, AD);
								st.executeUpdate();
							}
						}
						catch(Exception ex)
						{

						}
						finally
						{
							if( st != null ) try{ st.close(); }catch( SQLException e ){ e.printStackTrace(); }
						}
					}

				}
			}
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			reponse = false;
		}
		finally
		{
			if( res2 != null ) try{ res2.close(); }catch( SQLException e ){ e.printStackTrace(); }
			if( res != null ) try{ res.close(); }catch( SQLException e ){ e.printStackTrace(); }
			if( st != null ) try{ st.close(); }catch( SQLException e ){ e.printStackTrace(); }
			if( st1 != null ) try{ st1.close(); }catch( SQLException e ){ e.printStackTrace(); }
			if( st2 != null ) try{ st2.close(); }catch( SQLException e ){ e.printStackTrace(); }
		}

		return reponse;
	}

	public boolean isUserMemberOfGroup(Connection c, int userId, int groupId) {
		boolean status = false;
		PreparedStatement st=null;
		String sql;
		ResultSet res=null;
		try
		{
			sql = "SELECT userid FROM group_user gu WHERE gu.userid = ? AND gu.gid = ?";
			st = c.prepareStatement(sql);
			st.setInt(1, userId);

			st.setInt(2, groupId);

			res = st.executeQuery();
//			return (res.next());
			if( res.next() )
				status = true;;


		}
		catch (SQLException ex)
		{

			ex.printStackTrace();
			status = false;
		}
		finally
		{
			if( st != null ) try{ st.close(); }catch( SQLException e ){ e.printStackTrace(); }
			if( res != null ) try{ res.close(); }catch( SQLException e ){ e.printStackTrace(); }
		}
		return status;
	}


	public boolean isAdmin( Connection c, Integer userId)
	{
		boolean status = false;
		if( userId == null )
			return status;

		///
		ResultSet rs=null;
		PreparedStatement stmt=null;
		try
		{
			String query = "SELECT userid FROM credential WHERE userid=?  AND is_admin=1 ";
			stmt=c.prepareStatement(query);
			stmt.setInt(1, userId);
			rs = stmt.executeQuery();

			if( rs.next() )
				status = true;;
		}
		catch( SQLException e )
		{
			logger.error(e.getMessage());
			e.printStackTrace();
		}
		finally
		{
			if( stmt != null ) try{ stmt.close(); }catch( SQLException e ){ e.printStackTrace(); }
			if( rs != null ) try{ rs.close(); }catch( SQLException e ){ e.printStackTrace(); }
		}

		return status;
	}

	// System-wide designer
	public boolean isCreator( Connection c, Integer userId )
	{
		boolean status = false;
		if( userId == null )
			return status;

		ResultSet rs=null;
		PreparedStatement stmt=null;
		try
		{
			String query = "SELECT c.userid FROM credential c WHERE c.userid=? AND c.is_designer=1";
			stmt=c.prepareStatement(query);
			stmt.setInt(1, userId);
			rs = stmt.executeQuery();

			if( rs.next() ){
				status = true;
			}
		}
		catch( SQLException e )
		{
			e.printStackTrace();
			status = false;
		}
		finally {
			if( stmt != null ) try{ stmt.close(); }catch( SQLException e ){ e.printStackTrace(); }
			if( rs != null ) try{ rs.close(); }catch( SQLException e ){ e.printStackTrace(); }
		}
		return status;
	}

	/// Specific portfolio designer
	public boolean isDesigner( Connection c, Integer userId, String nodeId )
	{
		boolean status = false;
		if( userId == null )
			return status;

		ResultSet rs=null;
		PreparedStatement stmt=null;
		try
		{
			// FIXME
			String query = "SELECT gu.userid " +
					"FROM node n " +
					"LEFT JOIN group_right_info gri ON n.portfolio_id=gri.portfolio_id " +
					"LEFT JOIN group_info gi ON gri.grid=gi.grid " +
					"LEFT JOIN group_user gu ON gi.gid=gu.gid " +
					"WHERE gu.userid=? AND n.node_uuid=uuid2bin(?) AND gri.label='designer' ";
			stmt=c.prepareStatement(query);
			stmt.setInt(1, userId);
			stmt.setString(2, nodeId);
			rs = stmt.executeQuery();

			if( rs.next() ){
				status = true;
			}
		}
		catch( SQLException e )
		{
			e.printStackTrace();
			status = false;
		}
		finally {
			if( stmt != null ) try{ stmt.close(); }catch( SQLException e ){ e.printStackTrace(); }
			if( rs != null ) try{ rs.close(); }catch( SQLException e ){ e.printStackTrace(); }
		}
		return status;
	}

	/*
	/// Across the system
	public boolean isDesigner( Integer userId )
	{
		if( userId == null )
			return false;

		ResultSet rs=null;
		PreparedStatement stmt=null;
		try
		{
			String query = "SELECT c.userid FROM credential c WHERE c.userid=? AND c.is_designer=1 ";
			stmt=connection.prepareStatement(query);
			stmt.setInt(1, userId);
			rs = stmt.executeQuery();

			if( rs.next() )
				return true;
		}
		catch( SQLException e )
		{
			e.printStackTrace();
			return false;
		}
		return false;
	}
	//*/

	public boolean isOwner( Connection c, Integer userId, String portfolio )
	{
		if( userId == null )
			return false;

		///
		ResultSet rs=null;
		PreparedStatement stmt=null;
		try
		{
			/// FIXME
			String query = "SELECT modif_user_id FROM node WHERE modif_user_id=? AND portfolio_id=uuid2bin(?)";
			stmt=c.prepareStatement(query);
			stmt.setInt(1, userId);
			stmt.setString(2, portfolio);
			rs = stmt.executeQuery();

			if( rs.next() )
				return true;
		}
		catch( SQLException e )
		{
			e.printStackTrace();
			return false;
		}
		finally
		{
			if( rs!=null ) try { rs.close(); } catch( SQLException e ) { e.printStackTrace(); }
			if( stmt!=null ) try { stmt.close(); } catch( SQLException e ) { e.printStackTrace(); }
		}
		return false;
	}

	public boolean isNodeOwner( Connection c, Integer userId, String nodeuuid )
	{
		if( userId == null )
			return false;

		ResultSet rs=null;
		PreparedStatement stmt=null;
		try
		{
			String query = "SELECT modif_user_id FROM node WHERE modif_user_id=? AND node_uuid=uuid2bin(?)";
			stmt=c.prepareStatement(query);
			stmt.setInt(1, userId);
			stmt.setString(2, nodeuuid);
			rs = stmt.executeQuery();

			if( rs.next() )
				return true;
		}
		catch( SQLException e )
		{
			e.printStackTrace();
			return false;
		}
		return false;
	}

	public int getOwner( Connection c, Integer userId, String portfolio )
	{
		ResultSet rs=null;
		PreparedStatement stmt=null;
		try
		{
			String query = "SELECT modif_user_id FROM portfolio WHERE portfolio_id=uuid2bin(?)";
			stmt=c.prepareStatement(query);
			stmt.setString(1, portfolio);
			rs = stmt.executeQuery();

			if( rs.next() )
				return rs.getInt(1);
		}
		catch( SQLException e )
		{
			e.printStackTrace();
		}
		return 0;
	}


	public boolean isOwnerRRG( Connection c, Integer userId, int rrg )
	{
		if( userId == null )
			return false;

		///
		ResultSet rs=null;
		PreparedStatement stmt=null;
		try
		{
			String query = "SELECT p.modif_user_id " +
					"FROM group_right_info gri " +
					"LEFT JOIN portfolio p ON gri.portfolio_id=p.portfolio_id " +
					"WHERE p.modif_user_id=? AND gri.grid=?";
			stmt=c.prepareStatement(query);
			stmt.setInt(1, userId);
			stmt.setInt(2, rrg);
			rs = stmt.executeQuery();

			if( rs.next() )
				return true;
		}
		catch( SQLException e )
		{
			e.printStackTrace();
			return false;
		}
		return false;
	}

}

