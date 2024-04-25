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

package com.eportfolium.karuta.data.provider;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.activation.MimeType;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
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

import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.eportfolium.karuta.data.utils.ConfigUtils;
import com.eportfolium.karuta.data.utils.DomUtils;
import com.eportfolium.karuta.data.utils.FileUtils;
import com.eportfolium.karuta.data.utils.HttpClientUtils;
import com.eportfolium.karuta.data.utils.PostForm;
import com.eportfolium.karuta.data.utils.SqlUtils;
import com.eportfolium.karuta.rest.RestWebApplicationException;
import com.eportfolium.karuta.security.Credential;
import com.eportfolium.karuta.security.NodeRight;

/**
 * @author vassoill Implementation du dataProvider pour MySQL
 */
public class MysqlDataProvider implements DataProvider {

	// Help reconstruct tree
	static class t_tree {
		String data = "";
		String type = "";
		String childString = "";
	}

	public static final Pattern EMAIL_PATTERN = Pattern.compile(".*@.*\\..*");
	public static final Pattern ROLE_PATTERN = Pattern.compile("<roles>([^<]*)</roles>");
	public static final Pattern SEESTART_PAT = Pattern.compile("seestart=\"([^\"]*)");
	public static final Pattern SEEEND_PAT = Pattern.compile("seeend=\"([^\"]*)");
	public static final Pattern FILEID_PAT = Pattern.compile("lang=\"([^\"]*)\">([^%]*)");
	public static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");
	public static final SimpleDateFormat DATETIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	public static final String ONLYUSER = "(?<![-=+])(user)(?![-=+])";

	public static final Pattern PATTERN_ONLYUSER = Pattern.compile(ONLYUSER);
	private static final Logger logger = LoggerFactory.getLogger(MysqlDataProvider.class);
	private static final Logger securityLog = LoggerFactory.getLogger("securityLogger");
	public static final String DATABASE_FALSE = "faux";
	public static final String DB_YES = "Y";
	public static final String DB_NO = "N";

	public static final String XML_YES = "y";

	public static String[] findFiles(String directoryPath, String id) {
		//========================================================================
		if (id == null) {
			id = "";
		}
		// Current folder
		final File directory = new File(directoryPath);
		final File[] subfiles = directory.listFiles();
		final ArrayList<String> results = new ArrayList<>();

		// Under this, try to find necessary files
		for (final File fileOrDir : subfiles) {
			final String name = fileOrDir.getName();

			if ("__MACOSX".equals(name)) { /// Could be a better filtering
				continue;
			}

			// One folder level under this one
			if (fileOrDir.isDirectory()) {
				final File subdir = new File(directoryPath + name);
				final File[] subsubfiles = subdir.listFiles();
				for (final File subsubfile : subsubfiles) {
					final String subname = subsubfile.getName();

					if (subname.endsWith(id) || id.isEmpty()) {
						final String completename = directoryPath + name + File.separatorChar + subname;
						results.add(completename);
					}
				}
			} else if (fileOrDir.isFile()) {
				final String subname = fileOrDir.getName();
				if (name.contains(id) || id.isEmpty()) {
					final String completename = directoryPath + subname;
					results.add(completename);
				}
			}
		}

		final String[] result = new String[results.size()];
		results.toArray(result);

		return result;
	}

	public static String unzip(String zipFile, String destinationFolder) throws IOException {
		String folder = "";
		final File zipfile = new File(zipFile);

		try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipfile)))) {
			ZipEntry ze;
			while ((ze = zis.getNextEntry()) != null) {
				folder = destinationFolder;
				final File f = new File(folder, ze.getName());

				if (ze.isDirectory()) {
					f.mkdirs();
					continue;
				}

				f.getParentFile().mkdirs();
				final OutputStream fos = new BufferedOutputStream(new FileOutputStream(f));
				try {
					try {
						final byte[] buf = new byte[8192];
						int bytesRead;
						while (-1 != (bytesRead = zis.read(buf))) {
							fos.write(buf, 0, bytesRead);
						}
					} finally {
						fos.close();
					}
				} catch (final IOException ioe) {
					f.delete();
					throw ioe;
				}
			}
		}
		return folder;
	}

	final private Credential cred = new Credential();
	private final String userDir;

	private final String dbserveur;

	private final String backend;

	private final boolean createAsDesigner;

	public MysqlDataProvider() {
		dbserveur = ConfigUtils.getInstance().getRequiredProperty("serverType");
		createAsDesigner = BooleanUtils.toBoolean(ConfigUtils.getInstance().getProperty("createAsDesigner"));
		userDir = System.getProperty("user.dir");
		backend = ConfigUtils.getInstance().getRequiredProperty("backendserver");
	}

	@Override
	public boolean changePassword(Connection c, String username, String password) {
		boolean changed = false;

		String sql;
		PreparedStatement st = null;
		final ResultSet res = null;

		try {
			sql = "UPDATE credential SET password=UNHEX(SHA1(?)) WHERE login=?";
			st = c.prepareStatement(sql);
			st.setString(1, password);
			st.setString(2, username);
			st.executeUpdate();

			changed = true;
		} catch (final Exception ex) {
			ex.printStackTrace();
		} finally {
			try {
				if (res != null) {
					res.close();
				}
				if (st != null) {
					st.close();
				}
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}

		return changed;
	}

	/// Probably configuration related, sometime MySQL cache queries
	/// and on occasion not, slowing down the whole system
	private String checkCache(Connection c, String code) throws SQLException {
		String sql;
		PreparedStatement st;

		long t1, t1a, t1b, t1c, t1d;

		t1 = System.currentTimeMillis();

		/// Cache
		if (dbserveur.equals("mysql")) {
			sql = "CREATE TABLE IF NOT EXISTS t_node_cache(" +
					"node_uuid binary(16)  NOT NULL, " +
					"node_parent_uuid binary(16) DEFAULT NULL, " +
					"node_order int(12) NOT NULL, " +
					//					"metadata_wad varchar(2798) NOT NULL, " +
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
					"semtag varchar(100) DEFAULT NULL, " +
					"semantictag varchar(100) DEFAULT NULL, " +
					"label varchar(100)  DEFAULT NULL, " +
					"code varchar(255)  DEFAULT NULL, " +
					"descr varchar(100)  DEFAULT NULL, " +
					"format varchar(30) DEFAULT NULL, " +
					"modif_user_id int(12) NOT NULL, " +
					"modif_date timestamp NULL DEFAULT NULL, " +
					"portfolio_id binary(16) DEFAULT NULL, " +
					"PRIMARY KEY (`node_uuid`)) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
			st = c.prepareStatement(sql);
			st.execute();
			st.close();
		} else if (dbserveur.equals("oracle")) {
			final String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_node_cache(" +
					"node_uuid VARCHAR2(32)  NOT NULL, " +
					"node_parent_uuid VARCHAR2(32) DEFAULT NULL, " +
					"node_order NUMBER(12) NOT NULL, " +
					//					"metadata_wad VARCHAR2(2798 CHAR) DEFAULT NULL, " +
					"res_node_uuid VARCHAR2(32) DEFAULT NULL, " +
					"res_res_node_uuid VARCHAR2(32) DEFAULT NULL, " +
					"res_context_node_uuid VARCHAR2(32)  DEFAULT NULL, " +
					"shared_res NUMBER(1) NOT NULL, " +
					"shared_node NUMBER(1) NOT NULL, " +
					"shared_node_res NUMBER(1) NOT NULL, " +
					"shared_res_uuid VARCHAR2(32) DEFAULT NULL, " +
					"shared_node_uuid VARCHAR2(32) DEFAULT NULL, " +
					"shared_node_res_uuid VARCHAR2(32) DEFAULT NULL, " +
					"asm_type VARCHAR2(50 CHAR) DEFAULT NULL, " +
					"xsi_type VARCHAR2(50 CHAR)  DEFAULT NULL, " +
					"semtag VARCHAR2(100 CHAR) DEFAULT NULL, " +
					"semantictag VARCHAR2(100 CHAR) DEFAULT NULL, " +
					"label VARCHAR2(100 CHAR)  DEFAULT NULL, " +
					"code VARCHAR2(255 CHAR)  DEFAULT NULL, " +
					"descr VARCHAR2(100 CHAR)  DEFAULT NULL, " +
					"format VARCHAR2(30 CHAR) DEFAULT NULL, " +
					"modif_user_id NUMBER(12) NOT NULL, " +
					"modif_date timestamp DEFAULT NULL, " +
					"portfolio_id VARCHAR2(32) DEFAULT NULL) ON COMMIT PRESERVE ROWS";
			sql = "{call create_or_empty_table('t_node_cache','" + v_sql + "')}";
			final CallableStatement ocs = c.prepareCall(sql);
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

		if (res.next()) /// Cache hit
		{
			portfolioCode = res.getString(1);
			logger.info("CACHE HIT FOR CODE: {} -> {}", code, portfolioCode);
			res.close();
			st.close();

			/// Checking date
			sql = "SELECT c.modif_date " +
					"FROM portfolio p, node n, t_node_cache c " +
					"WHERE p.root_node_uuid=n.node_uuid " +
					"AND c.modif_date = p.modif_date " +
					"AND c.code=n.code " +
					"AND c.code=?";
			st = c.prepareStatement(sql);
			st.setString(1, code);
			res = st.executeQuery();
			if (!res.next()) {
				logger.info("INVALIDATE CACHE FOR: {}", code);
				updateCache = true;
			}
			res.close();
			st.close();
		} else {
			res.close();
			st.close();
			getCache = true;
		}

		t1b = System.currentTimeMillis();

		if (updateCache) /// FIXME: Sync problems
		{
			logger.info("FLUSH CACHE FOR: {} -> {}", code, portfolioCode);
			sql = "DELETE FROM t_node_cache WHERE portfolio_id=uuid2bin(?)";
			st = c.prepareStatement(sql);
			st.setString(1, portfolioCode);
			st.execute();
			st.close();
			getCache = true;
		}

		t1c = System.currentTimeMillis();

		if (getCache) /// Cache miss, load it
		{
			logger.info("CACHE MISS FOR CODE: {}", code);

			/// We'll put all node cached dated the same than portfolio. Related to checking cache validity
			if (dbserveur.equals("mysql")) {
				sql = "INSERT INTO t_node_cache(node_uuid, node_parent_uuid, node_order, res_node_uuid, res_res_node_uuid, res_context_node_uuid, shared_res, shared_node, shared_node_res, shared_res_uuid, shared_node_uuid, shared_node_res_uuid, asm_type, xsi_type, semtag, semantictag, label, code, descr, format, modif_user_id, modif_date, portfolio_id) ";
				sql += "SELECT SQL_NO_CACHE n.node_uuid, n.node_parent_uuid, n.node_order, n.res_node_uuid, n.res_res_node_uuid, n.res_context_node_uuid, n.shared_res, n.shared_node, n.shared_node_res, n.shared_res_uuid, n.shared_node_uuid, n.shared_node_res_uuid, n.asm_type, n.xsi_type, n.semtag, n.semantictag, n.label, n.code, n.descr, n.format, n.modif_user_id, p.modif_date, n.portfolio_id " +
						"FROM node n, portfolio p " +
						"WHERE n.portfolio_id=p.portfolio_id AND p.portfolio_id=(" +
						"SELECT n1.portfolio_id " +
						"FROM node n1, portfolio p " +
						"WHERE n1.portfolio_id=p.portfolio_id AND n1.code=? AND p.active=1) " +
						"ON DUPLICATE KEY UPDATE node_parent_uuid=n.node_parent_uuid, node_order=n.node_order, res_node_uuid=n.res_node_uuid, res_res_node_uuid=n.res_res_node_uuid, res_context_node_uuid=n.res_context_node_uuid, shared_res=n.shared_res, shared_node=n.shared_node, shared_node_res=n.shared_node_res, shared_res_uuid=n.shared_res_uuid, shared_node_uuid=n.shared_node_uuid, shared_node_res_uuid=n.shared_node_res_uuid, asm_type=n.asm_type, xsi_type=n.xsi_type, semtag=n.semtag, semantictag=n.semantictag, label=n.label, code=n.code, descr=n.descr, format=n.format, modif_user_id=n.modif_user_id, modif_date=n.modif_date, portfolio_id=n.portfolio_id";
			} else if (dbserveur.equals("oracle")) {
				/// FIXME: Not entirely sure it works...
				sql = "MERGE INTO t_node_cache t USING(";
				sql += "SELECT /*+ SQL_NO_CACHE */ n.node_uuid, n.node_parent_uuid, n.node_order, n.res_node_uuid, n.res_res_node_uuid, n.res_context_node_uuid, n.shared_res, n.shared_node, n.shared_node_res, n.shared_res_uuid, n.shared_node_uuid, n.shared_node_res_uuid, n.asm_type, n.xsi_type, n.semtag, n.semantictag, n.label, n.code, n.descr, n.format, n.modif_user_id, p.modif_date, n.portfolio_id " +
						"FROM node n, portfolio p " +
						"WHERE n.portfolio_id=p.portfolio_id AND p.portfolio_id=(" +
						"SELECT n1.portfolio_id " +
						"FROM node n1, portfolio p " +
						"WHERE n1.portfolio_id=p.portfolio_id AND n1.code=? AND p.active=1)) n3 " +
						"on (t.node_uuid = n3.node_uuid) " +
						"WHEN MATCHED THEN UPDATE " +
						"SET t.node_parent_uuid=n3.node_parent_uuid, t.node_order=n3.node_order, t.res_node_uuid=n3.res_node_uuid, t.res_res_node_uuid=n3.res_res_node_uuid, t.res_context_node_uuid=n3.res_context_node_uuid, t.shared_res=n3.shared_res, t.shared_node=n3.shared_node, t.shared_node_res=n3.shared_node_res, t.shared_res_uuid=n3.shared_res_uuid, t.shared_node_uuid=n3.shared_node_uuid, t.shared_node_res_uuid=n3.shared_node_res_uuid, t.asm_type=n3.asm_type, t.xsi_type=n3.xsi_type, t.semtag=n3.semtag, t.semantictag=n3.semantictag, t.label=n3.label, t.code=n3.code, t.descr=n3.descr, t.format=n3.format, t.modif_user_id=n3.modif_user_id, t.modif_date=n3.modif_date, t.portfolio_id=n3.portfolio_id " +
						"WHEN NOT MATCHED THEN " +
						"INSERT (t.node_uuid,t.node_parent_uuid, t.node_order, t.res_node_uuid, t.res_res_node_uuid, t.res_context_node_uuid, t.shared_res, t.shared_node, t.shared_node_res, t.shared_res_uuid, t.shared_node_uuid, t.shared_node_res_uuid, t.asm_type, t.xsi_type, t.semtag, t.semantictag, t.label, t.code, t.descr, t.format, t.modif_user_id, t.modif_date, t.portfolio_id) " +
						"VALUES (n3.node_uuid,n3.node_parent_uuid, n3.node_order, n3.res_node_uuid, n3.res_res_node_uuid, n3.res_context_node_uuid, n3.shared_res, n3.shared_node, n3.shared_node_res, n3.shared_res_uuid, n3.shared_node_uuid, n3.shared_node_res_uuid, n3.asm_type, n3.xsi_type, n3.semtag, n3.semantictag, n3.label, n3.code, n3.descr, n3.format, n3.modif_user_id, n3.modif_date, n3.portfolio_id)";
			}

			st = c.prepareStatement(sql);
			st.setString(1, code);
			final int insertData = st.executeUpdate();
			st.close();

			if (insertData == 0) { // Code isn't found, no need to go further
				return null;
			}

			/// Re-select portfolio id, case is when the portfolio has been deleted and recreated with same code
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

		if (logger.isTraceEnabled()) {
			logger.trace("{}, {}, {}, {}", (t1a - t1), (t1b - t1a), (t1c - t1b), (t1d - t1c));
		}

		return portfolioCode;
	}

	//// Pourquoi on a converti les " en ' en premier lieu?
	//// Avec de l'espoir on en aura plus besoin (meilleur performance)
	private void convertAttr(Element attributes, String att) {
		final String nodeString = "<?xml version='1.0' encoding='UTF-8' standalone='no'?><transfer " + att + "/>";

		try {
			/// Ensure we can parse it correctly
			DocumentBuilder documentBuilder;
			final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
			documentBuilder = documentBuilderFactory.newDocumentBuilder();
			final InputSource is = new InputSource(new StringReader(nodeString));
			final Document doc = documentBuilder.parse(is);

			/// Transfer attributes
			final Element attribNode = doc.getDocumentElement();
			final NamedNodeMap attribMap = attribNode.getAttributes();

			for (int i = 0; i < attribMap.getLength(); ++i) {
				final Node singleatt = attribMap.item(i);
				final String name = singleatt.getNodeName();
				final String value = singleatt.getNodeValue();
				attributes.setAttribute(name, value);
			}
		} catch (final Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public String createGroup(Connection c, String name) {
		PreparedStatement st;
		String sql;
		int retval = 0;

		try {
			sql = "INSERT INTO group_right_info(owner, label) VALUES(1,?)";
			st = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
			if (dbserveur.equals("oracle")) {
				st = c.prepareStatement(sql, new String[] { "grid" });
			}
			st.setString(1, name);
			st.executeUpdate();
			ResultSet rs = st.getGeneratedKeys();
			if (rs.next()) {
				retval = rs.getInt(1);
				rs.close();
				st.close();

				sql = "INSERT INTO group_info(grid, owner, label) VALUES(?,1,?)";
				st = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
				if (dbserveur.equals("oracle")) {
					st = c.prepareStatement(sql, new String[] { "gid" });
				}
				st.setInt(1, retval);
				st.setString(2, name);
				st.executeUpdate();
				rs = st.getGeneratedKeys();
				if (rs.next()) {
					retval = rs.getInt(1);
				}
			}

			rs.close();
			st.close();
		} catch (final Exception ex) {
			ex.printStackTrace();
		}

		return Integer.toString(retval);
	}

	@Override
	public String createUser(Connection c, String username, String email) throws Exception {
		PreparedStatement st = null;
		String sql;
		ResultSet res = null;
		String retval = "0";

		if (email != null && !EMAIL_PATTERN.matcher(email).matches()) {
			return retval;
		}

		try {
			final Date date = new Date();
			int isDesigner = 0;
			String other = "";
			if (createAsDesigner) {
				isDesigner = 1;
				other = "xlimited";
			}

			/// Credential checking use hashing, we'll never reach this.
			sql = "INSERT INTO credential SET login=?, display_firstname=?, email=?, display_lastname='', is_designer=?, other=?, password=SUBSTR(CONCAT('*', UPPER(SHA1(UNHEX(SHA1(?))))), 26)";
			if (dbserveur.equals("oracle")) {
				sql = "INSERT INTO credential SET login=?, display_firstname=?, email=?, display_lastname='', is_designer=?, other=?, password=?";
			}
			st = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
			if (dbserveur.equals("oracle")) {
				st = c.prepareStatement(sql, new String[] { "userid" });
			}
			st.setString(1, username);
			st.setString(2, username);
			if (email != null) {
				st.setString(3, email);
			} else {
				st.setString(3, "");
			}
			st.setInt(4, isDesigner);
			st.setString(5, other);
			st.setString(6, date + "somesalt");
			st.executeUpdate();
			res = st.getGeneratedKeys();
			if (res.next()) {
				retval = Integer.toString(res.getInt(1));
			}
		} catch (final Exception ex) {
			logger.error("Managed error", ex);
		} finally {
			if (res != null) {
				res.close();
			}
			if (st != null) {
				st.close();
			}
		}

		return retval;
	}

	@Override
	public void dataProvider() {
	}

	@Override
	public int deleteCredential(Connection c, int userId) {
		if (!cred.isAdmin(c, userId)) {
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");
		}

		return updateMysqlCredentialToken(c, userId, null);
	}

	@Override
	public Object deleteGroupRights(Connection c, Integer groupId, Integer groupRightId, Integer userId) {
		if (!cred.isAdmin(c, userId)) {
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");
		}

		return deleteMysqlGroupRights(c, groupId);
	}

	private Integer deleteMysqlGroupRights(Connection c, Integer groupId) {
		String sql;
		PreparedStatement st;
		int status = 1;

		try {
			/// FIXME: il manque les droits actuels
			sql = " DELETE gi, gu " +
					"FROM group_info gi " +
					"LEFT JOIN group_user gu ON gi.gid=gu.gid " +
					"WHERE gid=?";
			if (dbserveur.equals("oracle")) {
				sql = " DELETE FROM group_info gi WHERE gid=?";
			}
			st = c.prepareStatement(sql);
			st.setInt(1, groupId);
			st.executeUpdate();

			status = 0;
		} catch (final SQLException e) {
			e.printStackTrace();
		}

		return status;
	}

	private int deleteMySqlPortfolio(Connection c, String portfolioUuid, int userId, int groupId) throws SQLException {
		String sql;
		PreparedStatement st;
		int status = 0;
		boolean hasRights = false;

		final NodeRight right = cred.getPortfolioRight(c, userId, groupId, portfolioUuid, Credential.DELETE, null);
		if (right.delete || cred.isAdmin(c, userId)) {
			hasRights = true;
		}

		if (hasRights) {
			/// Si il y a quelque chose de particulier, on s'assure que tout soit bien nettoye de fa�on separe
			try {
				c.setAutoCommit(false);

				/// Group and rights
				if (dbserveur.equals("oracle")) {
					sql = "DELETE group_right_info gri WHERE gri.portfolio_id=uuid2bin(?)";
				} else {
					sql = "DELETE gri, gi, gu, gr " +
							"FROM group_right_info gri " +
							"LEFT JOIN group_info gi ON gri.grid=gi.grid " +
							"LEFT JOIN group_user gu ON gi.gid=gu.gid " +
							"LEFT JOIN group_rights gr ON gri.grid=gr.grid " +
							"WHERE gri.portfolio_id=uuid2bin(?)";
				}
				st = c.prepareStatement(sql);
				st.setString(1, portfolioUuid);
				st.executeUpdate();
				st.close();

				/// Resources
				if (dbserveur.equals("oracle")) {
					sql = "DELETE resource_table r WHERE  " +
							" exists (select 1 from node n where n.res_context_node_uuid=r.node_uuid AND portfolio_id=uuid2bin(?))";
				} else {
					sql = "DELETE r FROM resource_table r, node n " +
							"WHERE n.res_context_node_uuid=r.node_uuid AND portfolio_id=uuid2bin(?)";
				}
				st = c.prepareStatement(sql);
				st.setString(1, portfolioUuid);
				st.executeUpdate();
				st.close();

				if (dbserveur.equals("oracle")) {
					sql = "DELETE resource_table r WHERE  " +
							" exists (select 1 from node n where n.res_res_node_uuid=r.node_uuid AND portfolio_id=uuid2bin(?))";
				} else {
					sql = "DELETE r FROM resource_table r, node n " +
							"WHERE n.res_res_node_uuid=r.node_uuid AND portfolio_id=uuid2bin(?)";
				}
				st = c.prepareStatement(sql);
				st.setString(1, portfolioUuid);
				st.executeUpdate();
				st.close();

				if (dbserveur.equals("oracle")) {
					sql = "DELETE resource_table r WHERE  " +
							" exists (select 1 from node n where n.res_node_uuid=r.node_uuid AND portfolio_id=uuid2bin(?))";
				} else {
					sql = "DELETE r FROM resource_table r, node n " +
							"WHERE n.res_node_uuid=r.node_uuid AND portfolio_id=uuid2bin(?)";
				}
				st = c.prepareStatement(sql);
				st.setString(1, portfolioUuid);
				st.executeUpdate();
				st.close();

				/// Nodes
				if (dbserveur.equals("oracle")) {
					sql = "DELETE node WHERE portfolio_id=uuid2bin(?)";
				} else {
					sql = "DELETE FROM node WHERE portfolio_id=uuid2bin(?)";
				}
				st = c.prepareStatement(sql);
				st.setString(1, portfolioUuid);
				st.executeUpdate();
				st.close();

				/// Portfolio
				if (dbserveur.equals("oracle")) {
					sql = "DELETE portfolio WHERE portfolio_id=uuid2bin(?)";
				} else {
					sql = "DELETE FROM portfolio WHERE portfolio_id=uuid2bin(?)";
				}
				st = c.prepareStatement(sql);
				st.setString(1, portfolioUuid);
				st.executeUpdate();
				st.close();

				/// Portfolio group
				sql = "DELETE FROM portfolio_group_members WHERE portfolio_id=uuid2bin(?)";
				st = c.prepareStatement(sql);
				st.setString(1, portfolioUuid);
				st.executeUpdate();
				st.close();
			} catch (final Exception e) {
				c.commit();
				try {
					c.rollback();
				} catch (final SQLException e1) {
					logger.error("Managed error", e1);
				}
				logger.error("Managed error", e);
			} finally {
				c.commit();
				c.setAutoCommit(true);
				status = 1;
			}
		}
		return status;
	}

	private int deleteMySqlResource(Connection c, String resourceUuid, int userId, int groupId) throws SQLException {
		String sql;
		PreparedStatement st;

		if (cred.hasNodeRight(c, userId, groupId, resourceUuid, Credential.DELETE)) {
			sql = " DELETE FROM resource_table WHERE node_uuid=uuid2bin(?) ";
			st = c.prepareStatement(sql);
			st.setString(1, resourceUuid);
			return st.executeUpdate();
		}
		return 0;
	}

	@Override
	public Object deleteNode(Connection c, String nodeUuid, int userId, int groupId, String userRole) {
		long t1, t2, t3, t4, t5, t6;
		final long t0 = System.currentTimeMillis();

		final NodeRight nodeRight = cred.getNodeRight(c, userId, groupId, nodeUuid, Credential.DELETE, userRole);

		if (!nodeRight.delete) {
			if (!cred.isAdmin(c, userId) && !cred.isDesigner(c, userId, nodeUuid)) {
				throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");
			}
		}

		t1 = System.currentTimeMillis();

		PreparedStatement st;
		String sql;
		int result = 0;
		String parentid = "";
		try {
			/// Temp table for node ids, so we can traverse from here
			if (dbserveur.equals("mysql")) {
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
			} else if (dbserveur.equals("oracle")) {
				final String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_node(" +
						"node_uuid VARCHAR2(32)  NOT NULL, " +
						"node_parent_uuid VARCHAR2(32) DEFAULT NULL, " +
						"node_order NUMBER(12) NOT NULL, " +
						"res_node_uuid VARCHAR2(32) DEFAULT NULL, " +
						"res_res_node_uuid VARCHAR2(32) DEFAULT NULL, " +
						"res_context_node_uuid VARCHAR2(32)  DEFAULT NULL, " +
						"shared_res NUMBER(1) NOT NULL, " +
						"shared_node NUMBER(1) NOT NULL, " +
						"shared_node_res NUMBER(1) NOT NULL, " +
						"shared_res_uuid VARCHAR2(32) DEFAULT NULL, " +
						"shared_node_uuid VARCHAR2(32) DEFAULT NULL, " +
						"shared_node_res_uuid VARCHAR2(32) DEFAULT NULL, " +
						"portfolio_id VARCHAR2(32) DEFAULT NULL) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_node','" + v_sql + "')}";
				final CallableStatement ocs = c.prepareCall(sql);
				ocs.execute();
				ocs.close();
			}

			/// Pour le filtrage de la structure
			if (dbserveur.equals("mysql")) {
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
			} else if (dbserveur.equals("oracle")) {
				final String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_struc_node_resids(" +
						"uuid VARCHAR2(32) NOT NULL, " +
						"node_parent_uuid VARCHAR2(32), " +
						"res_node_uuid VARCHAR2(32), " +
						"res_res_node_uuid VARCHAR2(32), " +
						"res_context_node_uuid VARCHAR2(32), " +
						"t_level NUMBER(10,0)" +
						",  CONSTRAINT t_struc_node_resids_UK_uuid UNIQUE (uuid)) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_struc_node_resids','" + v_sql + "')}";
				final CallableStatement ocs = c.prepareCall(sql);
				ocs.execute();
				ocs.close();
			}

			if (dbserveur.equals("mysql")) {
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
			} else if (dbserveur.equals("oracle")) {
				final String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_struc_node_resids_2(" +
						"uuid VARCHAR2(32) NOT NULL, " +
						"node_parent_uuid VARCHAR2(32), " +
						"res_node_uuid VARCHAR2(32), " +
						"res_res_node_uuid VARCHAR2(32), " +
						"res_context_node_uuid VARCHAR2(32), " +
						"t_level NUMBER(10,0)" +
						",  CONSTRAINT t_struc_node_resids_2_UK_uuid UNIQUE (uuid)) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_struc_node_resids_2','" + v_sql + "')}";
				final CallableStatement ocs = c.prepareCall(sql);
				ocs.execute();
				ocs.close();
			}

			/// Pour le filtrage des ressources
			if (dbserveur.equals("mysql")) {
				sql = "CREATE TEMPORARY TABLE t_res_uuid(" +
						"uuid binary(16) UNIQUE NOT NULL) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")) {
				final String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_res_uuid(" +
						"uuid VARCHAR2(32) NOT NULL, " +
						"  CONSTRAINT t_res_UK_uuid UNIQUE (uuid)) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_res_uuid','" + v_sql + "')}";
				final CallableStatement ocs = c.prepareCall(sql);
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
			final ResultSet res = st.executeQuery();
			if (res.next()) {
				parentid = res.getString("bin2uuid(node_parent_uuid)");
			}
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
			if (dbserveur.equals("mysql")) {
				sql = "INSERT IGNORE INTO t_struc_node_resids_2(uuid, node_parent_uuid, res_node_uuid, res_res_node_uuid, res_context_node_uuid, t_level) ";
			} else if (dbserveur.equals("oracle")) {
				sql = "INSERT /*+ ignore_row_on_dupkey_index(t_struc_node_resids_2,t_struc_node_resids_2_UK_uuid)*/ INTO t_struc_node_resids_2(uuid, node_parent_uuid, res_node_uuid, res_res_node_uuid, res_context_node_uuid, t_level) ";
			}
			sql += "SELECT node_uuid, node_parent_uuid, res_node_uuid, res_res_node_uuid, res_context_node_uuid, ? " +
					"FROM t_node WHERE node_parent_uuid IN (SELECT uuid FROM t_struc_node_resids t " +
					"WHERE t.t_level=?)";
			st = c.prepareStatement(sql);

			String sqlTemp = null;
			if (dbserveur.equals("mysql")) {
				sqlTemp = "INSERT IGNORE INTO t_struc_node_resids SELECT * FROM t_struc_node_resids_2;";
			} else if (dbserveur.equals("oracle")) {
				sqlTemp = "INSERT /*+ ignore_row_on_dupkey_index(t_struc_node_resids,t_struc_node_resids_UK_uuid)*/ INTO t_struc_node_resids SELECT * FROM t_struc_node_resids_2";
			}
			final PreparedStatement stTemp = c.prepareStatement(sqlTemp);

			while (added != 0) {
				st.setInt(1, level + 1);
				st.setInt(2, level);
				st.executeUpdate();

				added = stTemp.executeUpdate(); // On s'arrete quand rien e ete ajoute
				level = level + 1; // Prochaine etape
			}
			st.close();
			stTemp.close();

			t4 = System.currentTimeMillis();

			/// On liste les ressources e effacer
			if (dbserveur.equals("mysql")) {
				sql = "INSERT INTO t_res_uuid(uuid) SELECT res_node_uuid FROM t_struc_node_resids WHERE res_node_uuid <> 0x0000000000000000000000000000000";
			} else if (dbserveur.equals("oracle")) {
				sql = "INSERT INTO t_res_uuid(uuid) SELECT res_node_uuid FROM t_struc_node_resids WHERE res_node_uuid <> '00000000000000000000000000000000'";
			}
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			if (dbserveur.equals("mysql")) {
				sql = "INSERT INTO t_res_uuid(uuid) SELECT res_res_node_uuid FROM t_struc_node_resids WHERE res_res_node_uuid <> 0x0000000000000000000000000000000";
			} else if (dbserveur.equals("oracle")) {
				sql = "INSERT INTO t_res_uuid(uuid) SELECT res_res_node_uuid FROM t_struc_node_resids WHERE res_res_node_uuid <> '00000000000000000000000000000000'";
			}
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			if (dbserveur.equals("mysql")) {
				sql = "INSERT INTO t_res_uuid(uuid) SELECT res_context_node_uuid FROM t_struc_node_resids WHERE res_context_node_uuid <> 0x0000000000000000000000000000000";
			} else if (dbserveur.equals("oracle")) {
				sql = "INSERT INTO t_res_uuid(uuid) SELECT res_context_node_uuid FROM t_struc_node_resids WHERE res_context_node_uuid <> '00000000000000000000000000000000'";
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
			if ("mysql".equals(dbserveur)) {
				sql = "DELETE rt FROM t_res_uuid tru LEFT JOIN resource_table rt ON tru.uuid=rt.node_uuid";
			} else // FIXME Not sure if it's correct
			{
				sql = "DELETE resource_table WHERE (node_uuid) IN (SELECT uuid FROM t_res_uuid)";
			}
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			// Les noeuds
			if ("mysql".equals(dbserveur)) {
				sql = "DELETE n FROM t_struc_node_resids tsnr LEFT JOIN node n ON tsnr.uuid=n.node_uuid";
			} else // FIXME Not sure if it's correct
			{
				sql = "DELETE node WHERE (node_uuid) IN (SELECT uuid FROM t_struc_node_resids)";
			}
			st = c.prepareStatement(sql);
			result = st.executeUpdate();
			st.close();

			t6 = System.currentTimeMillis();

			if (logger.isTraceEnabled()) {
				final long checkRights = t1 - t0;
				final long initstuff = t2 - t1;
				final long insertbase = t3 - t2;
				final long traversetree = t4 - t3;
				final long listresource = t5 - t4;
				final long purge = t6 - t5;
				logger.trace(
						"=====DELETE=====\nCheck rights: {}\nInitialize: {}\nInsert data: {}\nTraverse: {}\nList res: {}\nDelete: {}\n",
						checkRights, initstuff, insertbase, traversetree, listresource, purge);
			}
		} catch (final Exception e) {
			try {
				if (!c.getAutoCommit()) {
					c.rollback();
				}
			} catch (final SQLException e1) {
				e1.printStackTrace();
			}
			e.printStackTrace();
		} finally {
			try {
				c.setAutoCommit(true);
				// Les 'pooled connection' ne se ferment pas vraiment. On nettoie manuellement les tables temporaires...
				if (dbserveur.equals("mysql")) {
					sql = "DROP TEMPORARY TABLE IF EXISTS t_node, t_struc_node_resids, t_struc_node_resids_2, t_res_uuid";
					st = c.prepareStatement(sql);
					st.execute();
					st.close();
				}
				updateMysqlNodeChildren(c, parentid);
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}

		logger.info("deleteNode :" + nodeUuid);

		return result;
	}

	@Override
	public String deletePersonRole(Connection c, String portfolioUuid, String role, int userId, int uid) {
		if (!cred.isAdmin(c, userId)) {
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");
		}

		String sql;
		PreparedStatement st = null;
		ResultSet rs = null;
		try {
			// Retire la personne du r�le
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
		} catch (final Exception ex) {
			try {
				c.rollback();
				c.setAutoCommit(true);
			} catch (final SQLException e1) {
				e1.printStackTrace();
			}
			ex.printStackTrace();
		} finally {
			try {
				if (rs != null) {
					rs.close();
				}
				if (st != null) {
					st.close();
				}
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}

		return "";
	}

	@Override
	public Object deletePortfolio(Connection c, String portfolioUuid, int userId, int groupId) throws SQLException {
		/*
		if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");
		//*/

		return this.deleteMySqlPortfolio(c, portfolioUuid, userId, groupId);
	}

	@Override
	public String deletePortfolioFromPortfolioGroups(Connection c, String uuid, int portfolioGroupId, int userId) {
		String sql;
		PreparedStatement st = null;
		try {
			sql = "DELETE FROM portfolio_group_members WHERE pg=? AND portfolio_id=uuid2bin(?)";
			st = c.prepareStatement(sql);
			st.setInt(1, portfolioGroupId);
			st.setString(2, uuid);
			st.execute();
		} catch (final SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				if (st != null) {
					st.close();
				}
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}

		return null;
	}

	@Override
	public String deletePortfolioGroups(Connection c, int portfolioGroupId, int userId) {
		String sql;
		PreparedStatement st = null;
		try {
			c.setAutoCommit(false);

			sql = "DELETE FROM portfolio_group WHERE pg=?";
			st = c.prepareStatement(sql);
			st.setInt(1, portfolioGroupId);
			st.execute();

			sql = "DELETE FROM portfolio_group_members WHERE pg=?";
			st = c.prepareStatement(sql);
			st.setInt(1, portfolioGroupId);
			st.execute();

			c.setAutoCommit(true);

		} catch (final SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				if (st != null) {
					st.close();
				}
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}

		return null;
	}

	/// Retire les utilisateurs des RRG d'un portfolio donne
	@Override
	public String deletePortfolioUser(Connection c, int userId, String portId) {
		//    if(!credential.isAdmin(userId))
		//      throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		try {
			c.setAutoCommit(false);

			/// Bla here
			final String sqlRRG = "DELETE FROM group_user " +
					"WHERE gid IN " +
					"(SELECT gi.gid " +
					"FROM group_right_info gri " +
					"LEFT JOIN group_info gi ON gri.grid=gi.grid " +
					"WHERE gri.portfolio_id=uuid2bin(?))";
			final PreparedStatement rrgst = c.prepareStatement(sqlRRG);
			rrgst.setString(1, portId);
			rrgst.executeUpdate();
			rrgst.close();
		} catch (final Exception e) {
			try {
				c.rollback();
			} catch (final SQLException e1) {
				e1.printStackTrace();
			}
			e.printStackTrace();
		} finally {
			try {
				c.setAutoCommit(true);
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}

		return null;
	}

	@Override
	public Object deleteResource(Connection c, String resourceUuid, int userId, int groupId) throws Exception {
		if (!cred.isAdmin(c, userId)) {
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");
		}

		return deleteMySqlResource(c, resourceUuid, userId, groupId);
		//TODO asmResource(s) dans table Node et parentNode children a mettre e jour
	}

	@Override
	public String deleteRRG(Connection c, int userId, Integer rrgId) {
		if (!cred.isAdmin(c, userId) && !cred.isOwnerRRG(c, userId, rrgId)) {
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");
		}
		final String value = "";
		try {
			c.setAutoCommit(false);

			String sqlRRG = "DELETE gri, gu, gi, gr " +
					"FROM group_right_info AS gri " +
					"LEFT JOIN group_rights gr ON gri.grid=gr.grid " +
					"LEFT JOIN group_info gi ON gri.grid=gi.grid " +
					"LEFT JOIN group_user gu ON gi.gid=gu.gid " +
					"WHERE gri.grid=?";
			if (dbserveur.equals("oracle")) {
				sqlRRG = "DELETE FROM group_right_info AS gri WHERE gri.grid=?";
			}
			final PreparedStatement rrgst = c.prepareStatement(sqlRRG);
			rrgst.setInt(1, rrgId);
			rrgst.executeUpdate();
			rrgst.close();
		} catch (final Exception e) {
			try {
				c.rollback();
			} catch (final SQLException e1) {
				e1.printStackTrace();
			}
			e.printStackTrace();
		} finally {
			try {
				c.setAutoCommit(true);
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}

		return value;
	}

	@Override
	public String deleteRRGUser(Connection c, int userId, Integer rrgId, Integer user) {
		if (!cred.isAdmin(c, userId) && !cred.isOwnerRRG(c, userId, rrgId)) {
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");
		}

		final String value = "";

		try {
			c.setAutoCommit(false);

			final String sqlRRG = "DELETE FROM group_user " +
					"WHERE userid=? AND gid=(SELECT gid FROM group_info WHERE grid=?)";
			final PreparedStatement rrgst = c.prepareStatement(sqlRRG);
			rrgst.setInt(1, user);
			rrgst.setInt(2, rrgId);
			rrgst.executeUpdate();
			rrgst.close();
		} catch (final Exception e) {
			try {
				c.rollback();
			} catch (final SQLException e1) {
				e1.printStackTrace();
			}
			e.printStackTrace();
		} finally {
			try {
				c.setAutoCommit(true);
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}

		return value;
	}

	@Override
	public int deleteShareGroup(Connection c, String portfolio, Integer userId) {
		int status = -1;
		String sql;
		PreparedStatement st;

		/// Check if portfolio is owner by the user sending this command
		if (!cred.isOwner(c, userId, portfolio) && !cred.isAdmin(c, userId)) {
			return -2; // Not owner
		}

		try {
			c.setAutoCommit(false);

			// Delete and cleanup
			sql = "DELETE gri, gr, gi, gu " +
					"FROM group_right_info gri " +
					"LEFT JOIN group_rights gr ON gri.grid=gr.grid " +
					"LEFT JOIN group_info gi ON gri.grid=gi.grid " +
					"LEFT JOIN group_user gu ON gi.gid=gu.gid " +
					"WHERE gri.label=? " +
					"AND gri.portfolio_id=uuid2bin(?)";
			if (dbserveur.equals("oracle")) {
				sql = "DELETE FROM group_right_info gri WHERE gri.label=? " + "AND gri.portfolio_id=uuid2bin(?)";
			}
			st = c.prepareStatement(sql);
			st.setString(1, "shared");
			st.setString(2, portfolio);
			st.executeUpdate();

			status = 0;
		} catch (final Exception e) {
			try {
				c.rollback();
			} catch (final SQLException e1) {
				e1.printStackTrace();
			}
			e.printStackTrace();
		} finally {
			try {
				c.setAutoCommit(true);
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}

		return status;
	}

	@Override
	public int deleteSharePerson(Connection c, String portfolio, int user, Integer userId) {
		int status = -1;
		String sql;
		PreparedStatement st;

		/// Check if portfolio is owner by the user sending this command
		if (!cred.isOwner(c, userId, portfolio) && !cred.isAdmin(c, userId)) {
			return -2; // Not owner
		}

		try {
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
		} catch (final Exception e) {
			try {
				c.rollback();
			} catch (final SQLException e1) {
				e1.printStackTrace();
			}
			e.printStackTrace();
		} finally {
			try {
				c.setAutoCommit(true);
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}

		return status;
	}

	@Override
	public Object deleteUser(int userid, int userId1) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int deleteUsers(Connection c, Integer userId, Integer userId2) {
		String sql;
		PreparedStatement st;
		int result;

		try {

			sql = " DELETE FROM credential WHERE userid=? ";
			st = c.prepareStatement(sql);
			st.setInt(1, userId2);
			st.executeUpdate();

			sql = " DELETE FROM group_user WHERE userid=?";
			st = c.prepareStatement(sql);
			st.setInt(1, userId2);

			result = 1;

		} catch (final SQLException e) {
			e.printStackTrace();
			result = -1;
		}
		return result;
	}

	@Override
	public Boolean deleteUsersFromUserGroups(Connection c, int userId, int usersgroup, int currentUid) {
		String sql;
		PreparedStatement st = null;
		final Boolean isOK = true;

		try {
			sql = "DELETE FROM credential_group_members WHERE cg=? AND userid=?";
			st = c.prepareStatement(sql);
			st.setInt(1, usersgroup);
			st.setInt(2, userId);
			st.execute();
		} catch (final SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				if (st != null) {
					st.close();
				}
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}

		return isOK;
	}

	@Override
	public Boolean deleteUsersGroups(Connection c, int usersgroup, int currentUid) {
		String sql;
		PreparedStatement st = null;
		final Boolean isOK = true;

		try {
			c.setAutoCommit(false);

			sql = "DELETE FROM credential_group WHERE cg=?";
			st = c.prepareStatement(sql);
			st.setInt(1, usersgroup);
			st.execute();

			sql = "DELETE FROM credential_group_members WHERE cg=?";
			st = c.prepareStatement(sql);
			st.setInt(1, usersgroup);
			st.execute();

			c.setAutoCommit(true);

		} catch (final SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				if (st != null) {
					st.close();
				}
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}

		return isOK;
	}

	@Override
	public String emailFromLogin(Connection c, String username) {
		if ("".equals(username) || username == null) {
			return "";
		}

		String email = null;

		String sql;
		PreparedStatement st = null;
		ResultSet res = null;

		try {
			sql = "SELECT email FROM credential WHERE login=?";
			st = c.prepareStatement(sql);
			st.setString(1, username);
			res = st.executeQuery();

			if (res.next()) {
				email = res.getString(1);
			}
		} catch (final Exception ex) {
			ex.printStackTrace();
		} finally {
			try {
				if (res != null) {
					res.close();
				}
				if (st != null) {
					st.close();
				}
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}

		return email;
	}

	/******************************/
	/**
	 * Macro commandes et cie
	 *
	 * ## ## ### ##### ##### ##### ####### ## ## ## ## ## ## ## ## ## # ## ## ## ##
	 * ## ## ## ## ## ## ## ####### ## ###### ## ## ## ## ## ## ## ## ## ## ## ## ##
	 * ## ## ## ## ## ## ## ## ## ## ## ## ## ##### ## ## #####
	 **/
	/*****************************/

	public String executeAction(Connection c, int userId, String nodeUuid, String action, String role)
			throws SQLException, ParserConfigurationException, SAXException, IOException {

		String val = "erreur";
		String sql;
		PreparedStatement st;

		if ("showto".equals(action)) {

			if (cred.isAdmin(c, userId)) // Can activate it
			{
				String showto = role;
				showto = showto.replace(" ", "','");

				//// Il faut qu'il y a un showtorole
				if (!"('')".equals(showto)) {
					// Update rights
					/// Ajoute/remplace les droits
					// FIXME: Je crois que quelque chose manque
					sql = "UPDATE group_rights SET RD=? " +
							"WHERE id=uuid2bin(?) AND grid IN " +
							"(SELECT gri.grid FROM group_right_info gri, node n " +
							"WHERE gri.label IN (?) AND gri.portfolio_id=n.portfolio_id AND n.node_uuid=uuid2bin(?) ) ";

					if (dbserveur.equals("oracle")) {
						sql = "MERGE INTO group_rights d USING (" +
								"SELECT gr.grid, gr.id, ? AS RD, 0 AS WR, 0 AS DL, 0 AS AD, NULL AS types_id, NULL AS rules_id " +
								"FROM group_right_info gri, group_rights gr " +
								"WHERE gri.label IN " +
								showto +
								" " + /// Might not be safe
								"AND gri.grid=gr.grid " +
								"AND gr.id IN (SELECT uuid FROM t_struc_nodeid)) s " +
								"ON (d.id=s.id AND d.grid=s.grid) " +
								"WHEN MATCHED THEN " +
								"UPDATE SET d.RD=?, d.WR=s.WR, d.DL=s.DL, d.AD=s.AD, d.types_id=s.types_id, d.rules_id=s.rules_id " +
								"WHEN NOT MATCHED THEN " +
								"INSERT (d.grid, d.id, d.RD, d.WR, d.DL, d.AD, d.types_id, d.rules_id) " +
								"VALUES (s.grid, s.id, s.RD, s.WR, s.DL, s.AD, s.types_id, s.rules_id)";
					}
					st = c.prepareStatement(sql);
					if ("hide".equals(action)) {
						st.setInt(1, 0);
						st.setString(2, nodeUuid);
						st.setString(3, showto);
						st.setString(4, nodeUuid);
					} else if ("showto".equals(action)) {
						st.setInt(1, 1);
						st.setString(2, nodeUuid);
						st.setString(3, showto);
						st.setString(4, nodeUuid);
					}
					st.executeUpdate();
					st.close();

				}
			}

			val = "OK";
		}

		return val;
	}

	@Override
	public int getGroupByGroupLabel(Connection c, String groupLabel, int userId) {
		String sql;
		PreparedStatement st = null;
		ResultSet res = null;

		int groupId = -1;
		try {
			sql = "SELECT cg FROM credential_group cg " + "WHERE cg.label=?";
			st = c.prepareStatement(sql);
			st.setString(1, groupLabel);
			res = st.executeQuery();

			if (res.next()) {
				groupId = res.getInt("cg");
			}
		} catch (final SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				if (res != null) {
					res.close();
				}
				if (st != null) {
					st.close();
				}
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}

		return groupId;
	}

	@Override
	public String getGroupByName(Connection c, String name) {
		PreparedStatement st;
		String sql;
		ResultSet res;
		String retval = "0";

		try {
			sql = "SELECT gid FROM group_info WHERE label=? ";
			st = c.prepareStatement(sql);
			st.setString(1, name);
			res = st.executeQuery();
			if (res.next()) {
				retval = res.getString(1);
			}
		} catch (final Exception ex) {
			ex.printStackTrace();
		}

		return retval;
	}

	@Override
	public String getGroupByUser(Connection c, int user, int userId) {
		String sql;
		PreparedStatement st = null;
		ResultSet res = null;

		final StringBuilder result = new StringBuilder("<groups>");
		try {
			sql = "SELECT * FROM credential_group cg, credential_group_members cgm " +
					"WHERE cg.cg=cgm.cg AND cgm.userid=?";
			st = c.prepareStatement(sql);
			st.setInt(1, user);
			res = st.executeQuery();

			while (res.next()) {
				result.append("<group ");
				result.append(DomUtils.getXmlAttributeOutput("id", "" + res.getInt("cg.cg"))).append(" ");
				result.append(">");
				result.append("<label>").append(res.getString("cg.label")).append("</label>");
				result.append("</group>");
			}
		} catch (final SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				if (res != null) {
					res.close();
				}
				if (st != null) {
					st.close();
				}
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}
		result.append("</groups>");
		return result.toString();
	}

	@Override
	public Object getGroupRights(Connection c, int userId, int groupId) throws Exception {
		if (!cred.isAdmin(c, userId)) {
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");
		}

		final ResultSet res = getMysqlGroupRights(c, userId, groupId);
		final String AD = "1";
		final String SB = "1";
		final String WR = "1";
		final String DL = "1";
		final String RD = "1";

		final StringBuilder result = new StringBuilder("<groupRights>");
		while (res.next()) {
			result.append("<groupRight ");
			result.append(DomUtils.getXmlAttributeOutput("gid", res.getString("gid"))).append(" ");
			result.append(DomUtils.getXmlAttributeOutput("templateId", res.getString("grid"))).append(" ");
			result.append(">");

			result.append("<item ");
			if (AD.equalsIgnoreCase(res.getString("AD"))) {
				result.append(DomUtils.getXmlAttributeOutput("add", "True")).append(" ");
			} else {
				result.append(DomUtils.getXmlAttributeOutput("add", "False")).append(" ");
			}
			result.append(DomUtils.getXmlAttributeOutput("creator", res.getString("owner"))).append(" ");
			result.append(DomUtils.getXmlAttributeOutput("date", res.getString("DL"))).append(" ");
			if (DL.equals(res.getString("DL"))) {
				result.append(DomUtils.getXmlAttributeOutput("del", "True")).append(" ");
			} else {
				result.append(DomUtils.getXmlAttributeOutput("del", "False")).append(" ");
			}
			result.append(DomUtils.getXmlAttributeOutput("id", res.getString("id"))).append(" ");
			result.append(DomUtils.getXmlAttributeOutput("owner", res.getString("owner"))).append(" ");
			if (RD.equals(res.getString("RD"))) {
				result.append(DomUtils.getXmlAttributeOutput("read", "True")).append(" ");
			} else {
				result.append(DomUtils.getXmlAttributeOutput("read", "False")).append(" ");
			}
			if (SB.equals(res.getString("SB"))) {
				result.append(DomUtils.getXmlAttributeOutput("submit", "True")).append(" ");
			} else {
				result.append(DomUtils.getXmlAttributeOutput("submit", "False")).append(" ");
			}
			//result += DomUtils.getXmlAttributeOutput("type", res.getString("t.label"))+" ";
			result.append(DomUtils.getXmlAttributeOutput("typeId", res.getString("types_id"))).append(" ");
			if (WR.equals(res.getString("WR"))) {
				result.append(DomUtils.getXmlAttributeOutput("write", "True")).append(" ");
			} else {
				result.append(DomUtils.getXmlAttributeOutput("write", "False")).append(" ");
			}
			result.append("/>");

			while (res.next()) // FIXME Not sure why it's in double loop will suffice
			{
				result.append("<item ");
				if (AD.equalsIgnoreCase(res.getString("AD"))) {
					result.append(DomUtils.getXmlAttributeOutput("add", "True")).append(" ");
				} else {
					result.append(DomUtils.getXmlAttributeOutput("add", "False")).append(" ");
				}
				result.append(DomUtils.getXmlAttributeOutput("creator", res.getString("owner"))).append(" ");
				result.append(DomUtils.getXmlAttributeOutput("date", res.getString("DL"))).append(" "); /// FIXME Was there date?
				if (DL.equals(res.getString("DL"))) {
					result.append(DomUtils.getXmlAttributeOutput("del", "True")).append(" ");
				} else {
					result.append(DomUtils.getXmlAttributeOutput("del", "False")).append(" ");
				}
				result.append(DomUtils.getXmlAttributeOutput("id", res.getString("id"))).append(" ");
				result.append(DomUtils.getXmlAttributeOutput("owner", res.getString("owner"))).append(" ");
				if (RD.equals(res.getString("RD"))) {
					result.append(DomUtils.getXmlAttributeOutput("read", "True")).append(" ");
				} else {
					result.append(DomUtils.getXmlAttributeOutput("read", "False")).append(" ");
				}
				if (SB.equals(res.getString("SB"))) {
					result.append(DomUtils.getXmlAttributeOutput("submit", "True")).append(" ");
				} else {
					result.append(DomUtils.getXmlAttributeOutput("submit", "False")).append(" ");
				}
				//result += DomUtils.getXmlAttributeOutput("type", res.getString("t.label"))+" ";
				result.append(DomUtils.getXmlAttributeOutput("typeId", res.getString("types_id"))).append(" ");
				if (WR.equals(res.getString("WR"))) {
					result.append(DomUtils.getXmlAttributeOutput("write", "True")).append(" ");
				} else {
					result.append(DomUtils.getXmlAttributeOutput("write", "False")).append(" ");
				}
				result.append("/>");
			}

			result.append("</groupRight>");
		}

		result.append("</groupRights>");

		return result.toString();
	}

	@Override
	public String getGroupRightsInfos(Connection c, int userId, String portfolioId) throws SQLException {
		if (!cred.isAdmin(c, userId)) {
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");
		}

		final ResultSet res = getMysqlGroupRightsInfos(c, portfolioId);

		final StringBuilder result = new StringBuilder("<groupRightsInfos>");
		if (res != null) {
			while (res.next()) {
				result.append("<groupRightInfo ");
				result.append(DomUtils.getXmlAttributeOutput("grid", res.getString("grid"))).append(" ");
				result.append(">");
				result.append("<label>").append(res.getString("label")).append("</label>");
				result.append("<owner>").append(res.getString("owner")).append("</owner>");

				result.append("</groupRightInfo>");
			}
		}

		result.append("</groupRightsInfos>");

		return result.toString();
	}

	@Override
	public String getGroupsByRole(Connection c, int userId, String portfolioUuid, String role) {
		String sql;
		PreparedStatement st;
		ResultSet res;

		try {
			sql = "SELECT DISTINCT gu.gid FROM group_right_info gri, group_info gi, group_user gu WHERE gu.gid = gi.gid AND gi.grid = gri.grid AND gri.portfolio_id = uuid2bin(?) AND gri.label = ?";
			st = c.prepareStatement(sql);
			st.setString(1, portfolioUuid);
			st.setString(2, role);
			res = st.executeQuery();
		} catch (final Exception ex) {
			ex.printStackTrace();
			return null;
		}

		final StringBuilder result = new StringBuilder("<groups>");
		try {
			while (res.next()) {
				result.append(DomUtils.getXmlElementOutput("group", res.getString("gid")));
			}
		} catch (final SQLException e) {
			e.printStackTrace();
		}

		result.append("</groups>");

		return result.toString();
	}

	@Override
	public String getGroupsPortfolio(Connection c, String portfolioUuid, int userId, String userRole) {
		final NodeRight right = cred.getPortfolioRight(c, userId, 0, portfolioUuid, Credential.READ, userRole);
		if (!right.read) {
			return null;
		}

		final ResultSet res = getMysqlGroupsPortfolio(c, portfolioUuid);

		final StringBuilder result = new StringBuilder("<groups>");
		try {
			while (res.next()) {
				result.append("<group ");
				result.append(DomUtils.getXmlAttributeOutput("id", res.getString("gid"))).append(" ");
				//result += DomUtils.getXmlAttributeOutput("owner", res.getString("owner"))+" ";
				result.append(DomUtils.getXmlAttributeOutput("templateId", res.getString("grid"))).append(" ");
				result.append(">");
				result.append(DomUtils.getXmlElementOutput("groupid", res.getString("gid"))).append(" ");
				result.append(DomUtils.getXmlElementOutput("groupname", res.getString("g_label")));

				result.append(DomUtils.getXmlElementOutput("roleid", res.getString("grid"))).append(" ");
				result.append(DomUtils.getXmlElementOutput("rolename", res.getString("gri_label"))).append(" ");

				result.append("</group>");
			}

			result.append("</groups>");

			return result.toString();
		} catch (final SQLException e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public String getInfUser(Connection c, int userId, int userid) {
		PreparedStatement st;
		String sql;
		ResultSet res;
		String result = "";

		try {
			//requetes SQL permettant de recuperer toutes les informations
			//dans la table credential pour un userid(utilisateur) particulier
			sql = "SELECT * FROM credential c " +
					"LEFT JOIN credential_substitution cs " +
					"ON c.userid=cs.userid " +
					"WHERE c.userid = ?";
			st = c.prepareStatement(sql);
			st.setInt(1, userid);
			res = st.executeQuery();

			if (!res.next()) {
				return "User " + userid + " not found";
			}
			//traitement de la reponse, renvoie des donnees sous forme d'un xml
			try {
				String subs = res.getString("id");
				if (subs != null) {
					subs = "1";
				} else {
					subs = "0";
				}

				result += "<user ";
				result += DomUtils.getXmlAttributeOutput("id", res.getString("userid")) + " ";
				result += ">";
				result += DomUtils.getXmlElementOutput("username", res.getString("login"));
				result += DomUtils.getXmlElementOutput("firstname", res.getString("display_firstname"));
				result += DomUtils.getXmlElementOutput("lastname", res.getString("display_lastname"));
				result += DomUtils.getXmlElementOutput("email", res.getString("email"));
				result += DomUtils.getXmlElementOutput("admin", res.getString("is_admin"));
				result += DomUtils.getXmlElementOutput("designer", res.getString("is_designer"));
				result += DomUtils.getXmlElementOutput("active", res.getString("active"));
				result += DomUtils.getXmlElementOutput("substitute", subs);
				result += DomUtils.getXmlElementOutput("other", res.getString("other"));
				result += "</user>";

			} catch (final SQLException e) {
				e.printStackTrace();
			}

		} catch (final SQLException e) {
			logger.error("User " + userid + " not found", e);
			throw new RestWebApplicationException(Status.NOT_FOUND, "User " + userid + " not found");

		}

		return result;
	}

	private String getLinearXml(Connection c, String portfolioUuid, String rootuuid, int userId, int groupId,
			String role, Integer cutoff) throws SQLException, ParserConfigurationException {
		final DocumentBuilderFactory newInstance = DocumentBuilderFactory.newInstance();
		newInstance.newDocumentBuilder();

		long time0;
		long time1;
		long time2;
		long time3;
		long time4;
		long time5;
		final long time6 = 0;

		time0 = System.currentTimeMillis();

		ResultSet resNode = getMysqlStructure(c, portfolioUuid, userId, groupId);

		time1 = System.currentTimeMillis();

		final HashMap<String, Object[]> resolve = new HashMap<>();
		/// Node -> parent
		final HashMap<String, t_tree> entries = new HashMap<>();

		processQuery(resNode, resolve, entries, role);
		resNode.close();

		time2 = System.currentTimeMillis();

		resNode = getSharedMysqlStructure(c, portfolioUuid, userId, cutoff);

		time3 = System.currentTimeMillis();

		if (resNode != null) {
			processQuery(resNode, resolve, entries, role);
			resNode.close();
		}

		time4 = System.currentTimeMillis();

		/// Reconstruct functional tree
		final t_tree root = entries.get(rootuuid);
		final StringBuilder out = new StringBuilder(256);
		if (root != null) {
			reconstructTree(out, root, entries);
		}

		time5 = System.currentTimeMillis();

		if (logger.isTraceEnabled()) {
			logger.trace(
					"---- Portfolio ---\nQuery Main: {}\nParsing Main: {}\nQuery shared: {}\nParsing shared: {}\nReconstruction a: {}\nReconstruction b: {}\n------------------",
					(time1 - time0), (time2 - time1), (time3 - time2), (time4 - time3), (time5 - time4),
					(time6 - time5));
		}

		return out.toString();
	}

	@Override
	public String getListUsers(Connection c, int userId, String username, String firstname, String lastname,
			String email) {
		final ResultSet res = getMysqlUsers(c, userId, username, firstname, lastname, email);

		final StringBuilder result = new StringBuilder();
		result.append("<users>");
		try {
			int curUser = 0;
			while (res.next()) {
				final int userid = res.getInt("userid");
				if (curUser != userid) {
					curUser = userid;
					String subs = res.getString("id");
					if (subs != null) {
						subs = "1";
					} else {
						subs = "0";
					}

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
					result.append("</designer><sharer>");
					result.append(res.getString("is_sharer"));
					result.append("</sharer><email>");
					result.append(res.getString("email"));
					result.append("</email><active>");
					result.append(res.getString("active"));
					result.append("</active><substitute>");
					result.append(subs);
					result.append("</substitute>");
					result.append("<other>").append(res.getString("other")).append("</other>");
					result.append("</user>");
				} else {
				}
			}
		} catch (final SQLException e) {
			e.printStackTrace();
			return "<users></users>";
		}

		result.append("</users>");

		return result.toString();

	}

	@Deprecated
	@Override
	public Object getModel(Connection c, MimeType mimeType, Integer modelId, int userId) throws Exception {
		PreparedStatement st;
		String sql;
		ResultSet res;

		sql = "SELECT *  FROM  portfolio_model WHERE portfolio_id = uuid2bin(?)";

		st = c.prepareStatement(sql);
		res = st.executeQuery();

		final StringBuilder result = new StringBuilder();
		try {
			while (res.next()) {
				result.append("<model ");
				result.append(DomUtils.getXmlAttributeOutput("id", res.getString("pmid"))).append(" ");
				result.append(">");
				result.append(DomUtils.getXmlElementOutput("label", res.getString("pm_label")));
				result.append(DomUtils.getXmlElementOutput("treeid", res.getString("portfolio_id")));
				result.append("</model>");
			}
		} catch (final SQLException e) {
			e.printStackTrace();
			return null;
		}

		return result.toString();
	}

	@Deprecated
	@Override
	public Object getModels(Connection c, MimeType mimeType, int userId) throws Exception {
		PreparedStatement st;
		String sql;
		ResultSet res;

		sql = "SELECT *  FROM  portfolio_model";

		st = c.prepareStatement(sql);
		res = st.executeQuery();

		final StringBuilder result = new StringBuilder();
		result.append("<models> ");
		try {
			while (res.next()) {
				result.append("<model ");
				result.append(DomUtils.getXmlAttributeOutput("id", res.getString("pmid"))).append(" ");
				result.append(">");
				result.append(DomUtils.getXmlElementOutput("label", res.getString("pm_label")));
				result.append(DomUtils.getXmlElementOutput("treeid", res.getString("portfolio_id")));
				result.append("</model>");
			}
		} catch (final SQLException e) {
			e.printStackTrace();
			return null;
		}
		result.append("</models> ");

		return result.toString();
	}

	public ResultSet getMysqlGroupRights(Connection c, Integer userId, Integer groupId) {
		PreparedStatement st;
		String sql;

		try {
			// On recupere d'abord les informations dans la table structures
			sql = "SELECT bin2uuid(id) as id,RD,WR,DL,SB,AD,types_id,gid,gr.grid,gi.owner,gi.label FROM group_rights gr, group_info gi WHERE  gr.grid = gi.grid AND gi.gid = ?";
			st = c.prepareStatement(sql);
			st.setInt(1, groupId);

			return st.executeQuery();
		} catch (final Exception e) {
			e.printStackTrace();
		}

		return null;
	}

	private ResultSet getMysqlGroupRightsInfos(Connection c, String portfolioId) {
		PreparedStatement st;
		String sql;

		try {
			// On recupere d'abord les informations dans la table structures
			sql = "SELECT grid,owner,label FROM group_right_info WHERE  portfolio_id = uuid2bin(?) ";
			st = c.prepareStatement(sql);
			st.setString(1, portfolioId);

			return st.executeQuery();
		} catch (final Exception e) {
			e.printStackTrace();
		}

		return null;
	}

	public ResultSet getMysqlGroupsPortfolio(Connection c, String portfolioUuid) {
		PreparedStatement st;
		String sql;

		try {
			// On recupere d'abord les informations dans la table structures
			sql = "SELECT gi.gid, gi.grid,gi.label as g_label, gri.label as gri_label  FROM  group_right_info gri , group_info gi  WHERE   gri.grid = gi.grid  AND gri.portfolio_id = uuid2bin(?) ";

			sql += "  ORDER BY g_label ASC ";
			st = c.prepareStatement(sql);
			st.setString(1, portfolioUuid);

			return st.executeQuery();
		} catch (final Exception e) {
			e.printStackTrace();
		}

		return null;
	}

	public ResultSet getMysqlNode(Connection c, String nodeUuid, int userId, int groupId) throws SQLException {
		PreparedStatement st;
		String sql;

		// On recupere d'abord les informations dans la table structures
		sql = "SELECT bin2uuid(node_uuid) as node_uuid, bin2uuid(node_parent_uuid) as node_parent_uuid,  node_children_uuid as node_children_uuid, node_order, metadata, metadata_wad, metadata_epm, bin2uuid(res_node_uuid) as res_node_uuid,  bin2uuid(res_res_node_uuid) as res_res_node_uuid,  bin2uuid(res_context_node_uuid) as res_context_node_uuid, shared_res, shared_node, shared_node_res,bin2uuid(shared_res_uuid) AS shared_res_uuid, bin2uuid(shared_node_uuid) AS shared_node_uuid, bin2uuid(shared_node_res_uuid) AS shared_node_res_uuid,asm_type, xsi_type, semtag, semantictag, label, code, descr, format, modif_user_id, modif_date,  bin2uuid(portfolio_id) as portfolio_id FROM node WHERE node_uuid = uuid2bin(?) ";
		st = c.prepareStatement(sql);
		st.setString(1, nodeUuid);

		// On doit verifier le droit d'acces en lecture avant de retourner le noeud
		//if(!credential.getNodeRight(userId,groupId,nodeUuid,Credential.DELETE))
		//return null;
		//else
		return st.executeQuery();
	}

	public Integer getMysqlNodeNextOrderChildren(Connection c, String nodeUuid) throws Exception {
		PreparedStatement st;
		String sql;

		// On recupere d'abord les informations dans la table structures
		sql = "SELECT COUNT(*) as node_order FROM node WHERE node_parent_uuid = uuid2bin(?) GROUP BY node_parent_uuid";
		st = c.prepareStatement(sql);
		st.setString(1, nodeUuid);

		final java.sql.ResultSet res = st.executeQuery();
		try {
			res.next();
			return res.getInt("node_order");
		} catch (final Exception ex) {
			return 0;
		}
	}

	private ResultSet getMysqlNodeResultset(Connection c, String nodeUuid) {
		PreparedStatement st;
		String sql;

		try {
			sql = "SELECT bin2uuid(node_uuid) as node_uuid, bin2uuid(node_parent_uuid) as node_parent_uuid,  node_children_uuid as node_children_uuid, node_order, metadata, metadata_wad, metadata_epm, bin2uuid(res_node_uuid) as res_node_uuid,  bin2uuid(res_res_node_uuid) as res_res_node_uuid,  bin2uuid(res_context_node_uuid) as res_context_node_uuid, shared_res, shared_node, asm_type, xsi_type, semtag, label, code, descr, format, modif_user_id, modif_date,  bin2uuid(portfolio_id) as portfolio_id FROM node WHERE node_uuid = uuid2bin(?) ";
			st = c.prepareStatement(sql);
			st.setString(1, nodeUuid);

			return st.executeQuery();
		} catch (final Exception ex) {
			ex.printStackTrace();
			return null;
		}
	}

	private ResultSet getMysqlNodeUuidBySemanticTag(Connection c, String portfolioUuid, String semantictag)
			throws SQLException {
		String sql;
		PreparedStatement st;
		final String text = "%" + semantictag + "%";

		try {
			sql = "SELECT bin2uuid(node_uuid) AS node_uuid, bin2uuid(res_node_uuid) AS res_node_uuid, bin2uuid(res_res_node_uuid) AS res_res_node_uuid, bin2uuid(res_context_node_uuid) AS res_context_node_uuid, " +
					"node_children_uuid, code, asm_type, label, node_order " +
					"FROM node WHERE portfolio_id = uuid2bin(?) AND " +
					"semantictag LIKE ? ORDER BY code, node_order";
			st = c.prepareStatement(sql);

			st.setString(1, portfolioUuid);
			st.setString(2, text);

			return st.executeQuery();
		} catch (final Exception ex) {
			ex.printStackTrace();
			return null;
		}
	}

	private ResultSet getMysqlOtherNodeUuidByPortfolioModelUuidBySemanticTag(Connection c, String portfolioModelUuid,
			String semantictag) throws SQLException {
		String sql;
		PreparedStatement st;

		try {

			sql = "SELECT bin2uuid(node_uuid) AS node_uuid FROM model_node WHERE portfolio_model_uuid = uuid2bin(?) and  semantic_tag=? ";
			st = c.prepareStatement(sql);
			st.setString(1, portfolioModelUuid);
			st.setString(2, semantictag);
			return st.executeQuery();
		} catch (final Exception ex) {
			ex.printStackTrace();
			return null;
		}
	}

	private ResultSet getMysqlPortfolioResultset(Connection c, String portfolioUuid) {
		PreparedStatement st;
		String sql;

		try {
			sql = "SELECT bin2uuid(portfolio_id) AS portfolio_id,bin2uuid(model_id) AS model_id,bin2uuid(root_node_uuid) as root_node_uuid,modif_user_id,modif_date,active user_id FROM portfolio WHERE portfolio_id = uuid2bin(?) ";
			st = c.prepareStatement(sql);
			st.setString(1, portfolioUuid);

			return st.executeQuery();
		} catch (final Exception ex) {
			ex.printStackTrace();
			return null;
		}
	}

	public ResultSet getMysqlPortfolios(Connection c, Integer userId, int substid, Boolean portfolioActive) {
		if (userId == null && substid == 0) {
			return null;
		}
		PreparedStatement st;
		String sql;

		try {
			// Ordering by code. A bit hackish but it work as intended
			// Si on est admin, on recupere la liste complete
			if (cred.isAdmin(c, userId)) {
				sql = "SELECT bin2uuid(p.portfolio_id) AS portfolio_id, bin2uuid(p.root_node_uuid) AS root_node_uuid, p.modif_user_id, p.modif_date, p.active, p.user_id " +
						"FROM portfolio p, node n, resource_table r " +
						"WHERE p.root_node_uuid=n.node_uuid " +
						"AND n.res_res_node_uuid=r.node_uuid ";
				if (portfolioActive) {
					sql += "  AND active = 1 ";
				} else {
					sql += "  AND active = 0 ";
				}

				if ("mysql".equals(dbserveur)) {
					sql += " ORDER BY content";
				}
				if ("oracle".equals(dbserveur)) {
					sql += " ORDER BY dbms_lob.substr(content, 0, 4000)";
				}

				st = c.prepareStatement(sql);
				return st.executeQuery();
			}

			if (cred.isAdmin(c, substid)) { // If root wants to debug user UI
				substid = 0;
			}

			// On recupere d'abord les informations dans la table structures

			/// XXX Dammit Oracle, why are you so useless?
			/// FIXME: Might be a problem with Oracle if root ressource is > 4000 characters.
			///   If that happens, will have to rewrite the whole query
			/// Top level query so we can sort it by code
			sql = "SELECT portfolio_id, root_node_uuid, modif_user_id, modif_date, active, user_id, content " +
					"FROM (";
			//			sql = "";

			/// Portfolio that user own, and those that he can modify
			if ("mysql".equals(dbserveur)) {
				sql += "SELECT bin2uuid(p.portfolio_id) AS portfolio_id, bin2uuid(p.root_node_uuid) as root_node_uuid, p.modif_user_id, p.modif_date, p.active, p.user_id, r.content ";
			} else if ("oracle".equals(dbserveur)) {
				sql += "SELECT bin2uuid(p.portfolio_id) AS portfolio_id, bin2uuid(p.root_node_uuid) as root_node_uuid, p.modif_user_id, p.modif_date, p.active, p.user_id, TO_CHAR(r.content) AS content ";
			}
			sql += "FROM portfolio p, node n, resource_table r " +
					"WHERE p.root_node_uuid=n.node_uuid AND n.res_res_node_uuid=r.node_uuid ";
			sql += "AND (p.modif_user_id = ?)"; // Param 1
			if (portfolioActive) {
				sql += "AND p.active = 1 ";
			} else {
				sql += "AND p.active = 0 ";
			}

			sql += "UNION ALL ";
			if (substid != 0) {
				// Cross between portfolio that current user can access and those coming from the substitution
				if ("mysql".equals(dbserveur)) {
					sql += "SELECT bin2uuid(p.portfolio_id) AS portfolio_id, bin2uuid(p.root_node_uuid) AS root_node_uuid, p.modif_user_id, p.modif_date, p.active, p.user_id, r.content ";
				} else if ("oracle".equals(dbserveur)) {
					sql += "SELECT bin2uuid(p.portfolio_id) AS portfolio_id, bin2uuid(p.root_node_uuid) AS root_node_uuid, p.modif_user_id, p.modif_date, p.active, p.user_id, TO_CHAR(r.content) AS content ";
				}
				sql += "FROM node n, resource_table r, group_user gu " +
						"LEFT JOIN group_info gi ON gu.gid=gi.gid " +
						"LEFT JOIN group_right_info gri ON gri.grid=gi.grid " +
						"LEFT JOIN portfolio p ON gri.portfolio_id=p.portfolio_id, " +
						"group_user gu2 " +
						"LEFT JOIN group_info gi2 ON gu2.gid=gi2.gid " +
						"LEFT JOIN group_right_info gri2 ON gri2.grid=gi2.grid " +
						"LEFT JOIN portfolio p2 ON gri2.portfolio_id=p2.portfolio_id " +
						"WHERE p.root_node_uuid=n.node_uuid AND n.res_res_node_uuid=r.node_uuid AND " +
						"p.portfolio_id=p2.portfolio_id AND gu.userid=? AND gu2.userid=? "; // Param 2,3
			}
			// Portfolio we have received some specific rights to it
			else {
				if ("mysql".equals(dbserveur)) {
					sql += "SELECT bin2uuid(p.portfolio_id) AS portfolio_id, bin2uuid(p.root_node_uuid) AS root_node_uuid, p.modif_user_id, p.modif_date, p.active, p.user_id, r.content ";
				} else if ("oracle".equals(dbserveur)) {
					sql += "SELECT bin2uuid(p.portfolio_id) AS portfolio_id, bin2uuid(p.root_node_uuid) AS root_node_uuid, p.modif_user_id, p.modif_date, p.active, p.user_id, TO_CHAR(r.content) AS content ";
				}
				sql += "FROM node n, resource_table r, group_user gu " +
						"LEFT JOIN group_info gi ON gu.gid=gi.gid " +
						"LEFT JOIN group_right_info gri ON gri.grid=gi.grid " +
						"LEFT JOIN portfolio p ON gri.portfolio_id=p.portfolio_id " +
						"WHERE p.root_node_uuid=n.node_uuid AND n.res_res_node_uuid=r.node_uuid " +
						"AND gu.userid=? "; // Param 2
			}

			if (portfolioActive) {
				sql += "  AND p.active = 1 ";
			} else {
				sql += "  AND p.active = 0 ";
				/// FIXME might need to check active from substitute too
			}

			/// Closing top level query and sorting
			if ("mysql".equals(dbserveur)) {
				sql += ") t GROUP BY portfolio_id ORDER BY content";
			} else if ("oracle".equals(dbserveur)) {
				sql += ") t GROUP BY portfolio_id, root_node_uuid, modif_user_id, modif_date, active, user_id, content ORDER BY content";
			}

			st = c.prepareStatement(sql);
			st.setInt(1, userId); // From ownership
			if (substid == 0) {
				st.setInt(2, userId); // From specific rights given
			} else {
				st.setInt(2, substid); // Portfolio that substitution and
				st.setInt(3, userId); // current user can acess
			}
			//			  if(userId!=null) st.setInt(3, userId);

			return st.executeQuery();
		} catch (final Exception e) {
			e.printStackTrace();
		}

		return null;
	}

	public ResultSet getMysqlResource(Connection c, String nodeUuid) {
		PreparedStatement st;
		String sql;

		try {
			// On recupere d'abord les informations dans la table structures
			sql = "SELECT bin2uuid(node_uuid) AS node_uuid, xsi_type, content, user_id, modif_user_id, modif_date FROM resource_table WHERE node_uuid = uuid2bin(?) ";

			st = c.prepareStatement(sql);
			st.setString(1, nodeUuid);

			return st.executeQuery();
		} catch (final Exception e) {
			e.printStackTrace();
		}

		return null;
	}

	public String[] getMysqlResourceByNodeParentUuid(Connection c, String nodeParentUuid) {
		PreparedStatement st;
		String sql;

		String[] data = null;

		try {
			// On recupere d'abord les informations dans la table structures
			sql = "SELECT bin2uuid(r.node_uuid) AS node_uuid, r.xsi_type, r.content, r.user_id, r.modif_user_id, r.modif_date " +
					"FROM resource_table r, node n " +
					"WHERE r.node_uuid=n.res_node_uuid AND " +
					"n.node_uuid = uuid2bin(?)";
			st = c.prepareStatement(sql);
			st.setString(1, nodeParentUuid);

			final ResultSet res = st.executeQuery();
			if (res.next()) {
				data = new String[2];
				data[0] = res.getString("node_uuid");
				data[1] = res.getString("content");
			}
		} catch (final Exception e) {
			e.printStackTrace();
		}

		return data;
	}

	public ResultSet getMysqlResources(Connection c, String portfolioUuid) throws SQLException {
		PreparedStatement st;
		String sql;

		// On recupere d'abord les informations dans la table structures
		sql = "SELECT bin2uuid(res_node_uuid) AS res_node_uuid  FROM node WHERE portfolio_id= uuid2bin(?) AND res_node_uuid IS NOT NULL AND res_node_uuid<>'' ";
		if (dbserveur.equals("oracle")) {
			sql = "SELECT bin2uuid(res_node_uuid) AS res_node_uuid  FROM node WHERE portfolio_id= uuid2bin(?) AND res_node_uuid IS NOT NULL ";
		}
		st = c.prepareStatement(sql);
		st.setString(1, portfolioUuid);

		return st.executeQuery();
	}

	private ResultSet getMysqlStructure(Connection c, String portfolioUuid, int userId, int groupId)
			throws SQLException {
		PreparedStatement st;
		String sql = "";
		ResultSet rs = null;

		long time0 = 0;
		long time1 = 0;
		long time2 = 0;
		long time3 = 0;
		long time4 = 0;
		long time5 = 0;
		long time6 = 0;

		try {
			time0 = System.currentTimeMillis();

			final String rootNodeUuid = getPortfolioRootNode(c, portfolioUuid);

			time1 = System.currentTimeMillis();

			// Cas admin, designer, owner
			if (cred.isAdmin(c, userId) || cred.isDesigner(c, userId, rootNodeUuid)
					|| userId == cred.getOwner(c, userId, portfolioUuid)) {
				sql = "SELECT bin2uuid(n.node_uuid) AS node_uuid, " +
						"node_children_uuid, n.node_order, n.metadata, n.metadata_wad, n.metadata_epm, " +
						"n.shared_node AS shared_node, bin2uuid(n.shared_node_res_uuid) AS shared_node_res_uuid, bin2uuid(n.res_node_uuid) AS res_node_uuid, n.modif_date, " +
						"r1.xsi_type AS r1_type, r1.content AS r1_content, r1.modif_date AS r1_modif_date, bin2uuid(n.res_res_node_uuid) as res_res_node_uuid, " +
						"r2.content AS r2_content, r2.modif_date AS r2_modif_date, bin2uuid(n.res_context_node_uuid) as res_context_node_uuid, " +
						"r3.content AS r3_content, r3.modif_date AS r3_modif_date, n.asm_type, n.xsi_type, " +
						"1 AS RD, 1 AS WR, 1 AS SB, 1 AS DL, NULL AS types_id, NULL AS rules_id " +
						"FROM node n " +
						"LEFT JOIN resource_table r1 ON n.res_node_uuid=r1.node_uuid " +
						"LEFT JOIN resource_table r2 ON n.res_res_node_uuid=r2.node_uuid " +
						"LEFT JOIN resource_table r3 ON n.res_context_node_uuid=r3.node_uuid " +
						"WHERE portfolio_id=uuid2bin(?)";
				st = c.prepareStatement(sql);
				st.setString(1, portfolioUuid);
			} else if (cred.hasSomeRight(c, userId, portfolioUuid)) {
				/// FIXME: Il faudrait peut-etre prendre une autre strategie pour selectionner les bonnes donnees
				// Cas proprietaire
				// Cas generale (partage via droits)

				if (dbserveur.equals("mysql")) {
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
				} else if (dbserveur.equals("oracle")) {
					final String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_rights(" +
							"grid NUMBER(19,0) NOT NULL, " +
							"id VARCHAR2(32) NOT NULL, " +
							"RD NUMBER(1) NOT NULL, " +
							"WR NUMBER(1) NOT NULL, " +
							"DL NUMBER(1) NOT NULL, " +
							"SB NUMBER(1) NOT NULL, " +
							"AD NUMBER(1) NOT NULL, CONSTRAINT t_rights_UK_id UNIQUE (id)) ON COMMIT PRESERVE ROWS";
					sql = "{call create_or_empty_table('t_rights','" + v_sql + "')}";
					final CallableStatement ocs = c.prepareCall(sql);
					ocs.execute();
					ocs.close();
				}
				time2 = System.currentTimeMillis();

				time3 = System.currentTimeMillis();

				/*
				/// Droits donnees par le groupe selectionne
				sql = "INSERT INTO t_rights(grid,id,RD,WR,DL,SB,AD) " +
						"SELECT gr.grid, gr.id, gr.RD, gr.WR, gr.DL, gr.SB, gr.AD " +
						"FROM group_info gi, group_right_info gri, group_rights gr " +
						"WHERE gi.grid=gri.grid AND gri.grid=gr.grid AND gi.gid=?";
				st = connection.prepareStatement(sql);
				st.setInt(1, groupId);
				st.executeUpdate();
				st.close();
				//*/

				/// Droits donnees par le portfolio � 'tout le monde'
				/// Fusion des droits, pas tres beau mais bon.
				/// Droits donne specifiquement � un utilisateur
				/// FIXME: Devrait peut-etre verifier si la personne a les droits d'y acceder?
				if (dbserveur.equals("mysql")) {
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
				} else if (dbserveur.equals("oracle")) {
					sql = "MERGE INTO t_rights d USING (";
					sql += "SELECT MAX(gr.grid) AS grid, gr.id, MAX(gr.RD) AS RD, MAX(gr.WR) AS WR, MAX(gr.DL) AS DL, MAX(gr.SB) AS SB, MAX(gr.AD) AS AD " + // FIXME MAX(gr.grid) will have unintended consequences
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

				/// Filter dates, directly change the temp rights list
				///// Remove node for simple users if there's a date limitation
				sql = "DELETE FROM t_rights WHERE id=uuid2bin(?)";
				final PreparedStatement stFilter = c.prepareStatement(sql);

				// Fetch metadata
				sql = "SELECT bin2uuid(node_uuid) AS node_uuid, metadata_wad FROM node n, t_rights tsp " +
						"WHERE n.node_uuid=tsp.id " +
						"AND ((metadata_wad LIKE '%seestart%') OR (metadata_wad LIKE '%seeend%'))";
				st = c.prepareStatement(sql);
				final ResultSet res = st.executeQuery();
				String meta;
				while (res.next()) {
					/// Checking if date has been declared
					meta = res.getString("metadata_wad");

					final Matcher startMatcher = SEESTART_PAT.matcher(meta);
					final Matcher endMatcher = SEEEND_PAT.matcher(meta);
					String seestart = null;
					String seeend = null;
					if (startMatcher.find()) {
						seestart = startMatcher.group(1);
					}
					if (endMatcher.find()) {
						seeend = endMatcher.group(1);
					}

					final String uuid = res.getString("node_uuid");
					final long currentTime = System.currentTimeMillis();
					// Nothing on that line
					try {
						if (seestart == null && seeend == null) {
							continue;
						}
						if (seestart != null && seeend == null) { // Only a start view
							Date dt;
							dt = SIMPLE_DATE_FORMAT.parse(seestart);
							final long starttime = dt.getTime();
							if (starttime > currentTime) {
								stFilter.setString(1, uuid);
								stFilter.executeUpdate();
							}
						} else if (seestart == null && seeend != null) { // Only end view
							final Date dt = SIMPLE_DATE_FORMAT.parse(seeend);
							final long endtime = dt.getTime();
							if (endtime < currentTime) {
								stFilter.setString(1, uuid);
								stFilter.executeUpdate();
							}
						} else { // Restriction on start and end
							Date dt = SIMPLE_DATE_FORMAT.parse(seestart);
							final long starttime = dt.getTime();
							dt = SIMPLE_DATE_FORMAT.parse(seeend);
							final long endtime = dt.getTime();
							if (endtime < currentTime || starttime > currentTime) {
								stFilter.setString(1, uuid);
								stFilter.executeUpdate();
							}
						}
					} catch (final ParseException e) {
						// For some reason, date isn't formatted correctly
						// Should never happen
						logger.error("Error on date formatting", e);
					}
				}
				res.close();
				stFilter.close();
				st.close();

				time5 = System.currentTimeMillis();

				/// Actuelle selection des donnees
				sql = "SELECT bin2uuid(n.node_uuid) AS node_uuid, " +
						"node_children_uuid, n.node_order, n.metadata, n.metadata_wad, n.metadata_epm, " +
						"n.shared_node AS shared_node, bin2uuid(n.shared_node_res_uuid) AS shared_node_res_uuid, bin2uuid(n.res_node_uuid) AS res_node_uuid, n.modif_date, " +
						"r1.xsi_type AS r1_type, r1.content AS r1_content, r1.modif_date AS r1_modif_date, bin2uuid(n.res_res_node_uuid) as res_res_node_uuid, " +
						"r2.content AS r2_content, r2.modif_date AS r2_modif_date, bin2uuid(n.res_context_node_uuid) as res_context_node_uuid, " +
						"r3.content AS r3_content, r3.modif_date AS r3_modif_date, n.asm_type, n.xsi_type, " +
						"tr.RD, tr.WR, tr.SB, tr.DL, gr.types_id, gr.rules_id " +
						"FROM group_rights gr, t_rights tr " +
						"LEFT JOIN node n ON tr.id=n.node_uuid " +
						"LEFT JOIN resource_table r1 ON n.res_node_uuid=r1.node_uuid " + // Recuperation des donnees res_node
						"LEFT JOIN resource_table r2 ON n.res_res_node_uuid=r2.node_uuid " + // Recuperation des donnees res_res_node
						"LEFT JOIN resource_table r3 ON n.res_context_node_uuid=r3.node_uuid " + // Recuperation des donnees res_context
						"WHERE tr.grid=gr.grid AND tr.id=gr.id AND tr.RD=1 "; // +
				/*
				"UNION ALL " +	/// Union pour les donnees appartenant au createur
				"SELECT bin2uuid(n.node_uuid) AS node_uuid, " +
				"node_children_uuid, n.node_order, n.metadata, n.metadata_wad, n.metadata_epm, " +
				"n.shared_node AS shared_node, bin2uuid(n.shared_node_res_uuid) AS shared_node_res_uuid, bin2uuid(n.res_node_uuid) AS res_node_uuid, n.modif_date, " +
				"r1.xsi_type AS r1_type, r1.content AS r1_content, r1.modif_date AS r1_modif_date, bin2uuid(n.res_res_node_uuid) as res_res_node_uuid, " +
				"r2.content AS r2_content, r2.modif_date AS r2_modif_date, bin2uuid(n.res_context_node_uuid) as res_context_node_uuid, " +
				"r3.content AS r3_content, r3.modif_date AS r3_modif_date, n.asm_type, n.xsi_type, " +
				"1 AS RD, 1 AS WR, 1 AS SB, 1 AS DL, NULL AS types_id, NULL AS rules_id " +
				"FROM node n " +
				"LEFT JOIN resource_table r1 ON n.res_node_uuid=r1.node_uuid " +
				"LEFT JOIN resource_table r2 ON n.res_res_node_uuid=r2.node_uuid " +
				"LEFT JOIN resource_table r3 ON n.res_context_node_uuid=r3.node_uuid " +
				"WHERE n.modif_user_id=? AND portfolio_id=uuid2bin(?)";
				//*/
				st = c.prepareStatement(sql);
				//				st.setInt(1, userId);
				//				st.setString(2, portfolioUuid);
			} else if (cred.isPublic(c, null, portfolioUuid)) // Public case, looks like previous query, but with different rights
			{
				sql = "SELECT bin2uuid(n.node_uuid) AS node_uuid, " +
						"node_children_uuid, n.node_order, n.metadata, n.metadata_wad, n.metadata_epm, " +
						"n.shared_node AS shared_node, bin2uuid(n.shared_node_res_uuid) AS shared_node_res_uuid, bin2uuid(n.res_node_uuid) AS res_node_uuid, n.modif_date, " +
						"r1.xsi_type AS r1_type, r1.content AS r1_content, r1.modif_date AS r1_modif_date, bin2uuid(n.res_res_node_uuid) as res_res_node_uuid, " +
						"r2.content AS r2_content, r2.modif_date AS r2_modif_date, bin2uuid(n.res_context_node_uuid) as res_context_node_uuid, " +
						"r3.content AS r3_content, r3.modif_date AS r3_modif_date, n.asm_type, n.xsi_type, " +
						"1 AS RD, 0 AS WR, 0 AS SB, 0 AS DL, NULL AS types_id, NULL AS rules_id " +
						"FROM node n " +
						"LEFT JOIN resource_table r1 ON n.res_node_uuid=r1.node_uuid " +
						"LEFT JOIN resource_table r2 ON n.res_res_node_uuid=r2.node_uuid " +
						"LEFT JOIN resource_table r3 ON n.res_context_node_uuid=r3.node_uuid " +
						"WHERE portfolio_id=uuid2bin(?)";
				st = c.prepareStatement(sql);
				st.setString(1, portfolioUuid);
			} else {
				// Neither admin or creator,
				// Neither owner or have some right
				// Neither public
				sql = "SELECT NULL LIMIT 0;";
				st = c.prepareStatement(sql);
			}
			rs = st.executeQuery();

			time6 = System.currentTimeMillis();
		} catch (final SQLException e) {
			logger.error("SQL Exception", e);
		} finally {
			try {
				// Les 'pooled connection' ne se ferment pas vraiment. On nettoie manuellement les tables temporaires...
				if (dbserveur.equals("mysql")) {
					sql = "DROP TEMPORARY TABLE IF EXISTS t_rights";
					st = c.prepareStatement(sql);
					st.execute();
					st.close();
				}
			} catch (final SQLException e) {
				logger.error("SQL Exception", e);
			}
		}

		if (logger.isTraceEnabled()) {
			logger.trace(
					"---- Query Portfolio ----\nFetch root: {}\nCheck rights: {}\nCreate temp: {}\nFetch rights all/group: {}\nFetch user rights: {}\nActual query: {}\n",
					(time1 - time0), (time2 - time1), (time3 - time2), (time4 - time3), (time5 - time4),
					(time6 - time5));
		}

		return rs;
	}

	public ResultSet getMySqlUser(Connection c, int userId) {
		PreparedStatement st;
		String sql;
		ResultSet res;

		try {
			sql = "SELECT * FROM user WHERE user_id = ? ";
			st = c.prepareStatement(sql);
			st.setInt(1, userId);
			res = st.executeQuery();
			res.next();
			return res;
		} catch (final SQLException ex) {
			logger.error("SQL exception ", ex);
			return null;
		}

	}

	public ResultSet getMySqlUserByLogin(Connection c, String login) throws Exception {
		PreparedStatement st;
		String sql;
		ResultSet res;

		try {

			sql = "SELECT * FROM credential WHERE login = ? ";
			st = c.prepareStatement(sql);
			st.setString(1, login);
			res = st.executeQuery();
			res.next();
			return res;
		} catch (final Exception ex) {
			ex.printStackTrace();
			return null;
		}

	}

	public ResultSet getMysqlUserGroupByPortfolio(Connection c, String portfolioUuid, int userId) {
		PreparedStatement st;
		String sql;

		try {
			// On recupere d'abord les informations dans la table structures
			sql = "SELECT gi.gid, gi.owner, gi.grid,gi.label as g_label, gri.label as gri_label  FROM  group_right_info gri , group_info gi, group_user gu  WHERE   gu.gid=gi.gid AND gri.grid = gi.grid  AND gri.portfolio_id = uuid2bin(?) AND gu.userid= ? ";

			st = c.prepareStatement(sql);
			st.setString(1, portfolioUuid);
			st.setInt(2, userId);

			return st.executeQuery();
		} catch (final Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public ResultSet getMysqlUserGroups(Connection c, Integer userId) {
		PreparedStatement st;
		String sql;

		try {
			// On recupere d'abord les informations dans la table structures

			sql = "SELECT * FROM group_user gu, credential cr, group_info gi  WHERE gu.userid=cr.userid  AND gi.gid=gu.gid ";
			if (userId != null) {
				sql += "  AND cr.userid = ? ";
			}
			sql += " ORDER BY label ASC ";
			st = c.prepareStatement(sql);
			if (userId != null) {
				st.setInt(1, userId);
			}

			return st.executeQuery();
		} catch (final Exception e) {
			e.printStackTrace();
		}

		return null;
	}

	public ResultSet getMysqlUsers(Connection c, Integer userId, String username, String firstname, String lastname,
			String email) {
		PreparedStatement st;
		String sql;

		try {
			// On recupere d'abord les informations dans la table structures
			sql = "SELECT * FROM credential c " + "LEFT JOIN credential_substitution cs " + "ON c.userid=cs.userid ";
			int count = 0;
			if (username != null) {
				count++;
			}
			if (firstname != null) {
				count++;
			}
			if (lastname != null) {
				count++;
			}
			if (email != null) {
				count++;
			}
			if (count > 0) {
				sql += "WHERE ";
				if (username != null) {
					sql += "login LIKE ? ";
					if (count > 1) {
						sql += "AND ";
					}
				}
				if (firstname != null) {
					sql += "display_firstname LIKE ? ";
					if (count > 1) {
						sql += "AND ";
					}
				}
				if (lastname != null) {
					sql += "display_lastname LIKE ? ";
					if (count > 1) {
						sql += "AND ";
					}
				}
				if (email != null) {
					sql += "email LIKE ? ";
				}
			}
			sql += "ORDER BY c.userid";
			st = c.prepareStatement(sql);

			int start = 1;
			if (username != null) {
				st.setString(start, "%" + username + "%");
				start++;
			}
			if (firstname != null) {
				st.setString(start, "%" + firstname + "%");
				start++;
			}
			if (lastname != null) {
				st.setString(start, "%" + lastname + "%");
				start++;
			}
			if (email != null) {
				st.setString(start, "%" + email + "%");
				start++;
			}

			return st.executeQuery();
		} catch (final Exception e) {
			e.printStackTrace();
		}

		return null;
	}

	@Override
	public String getMysqlUserUid(Connection c, String login) throws Exception {
		PreparedStatement st;
		String sql;
		ResultSet res;
		String ret = null;

		try {
			sql = "SELECT userid FROM credential WHERE login = ? ";
			st = c.prepareStatement(sql);
			st.setString(1, login);
			res = st.executeQuery();
			if (res.next()) {
				ret = res.getString("userid");
			}
		} catch (final Exception ex) {
			ex.printStackTrace();
		}
		return ret;
	}

	@Override
	public Object getNode(Connection c, MimeType outMimeType, String nodeUuid, boolean withChildren, int userId,
			int groupId, String userRole, String label, Integer cutoff)
			throws SQLException, TransformerFactoryConfigurationError, ParserConfigurationException, DOMException,
			SAXException, IOException, TransformerException {
		final StringBuilder nodexml = new StringBuilder();

		final long t_start = System.currentTimeMillis();

		final NodeRight nodeRight = cred.getNodeRight(c, userId, groupId, nodeUuid, label, userRole);

		final long t_nodeRight = System.currentTimeMillis();

		if (!nodeRight.read) {
			userId = cred.getPublicUid(c);
			/// Verifie les droits avec le compte publique (derniere chance)
			//			cred.getPublicRight(c, userId, 123, nodeUuid, "dummy");

			if (!cred.isPublic(c, nodeUuid, null)) {
				return nodexml;
			}
		}

		if (outMimeType.getSubType().equals("xml")) {
			final ResultSet result = getNodePerLevel(c, nodeUuid, userId, nodeRight.rrgId, cutoff);
			if (result == null) { // Node doesn't exist
				return null;
			}

			final long t_nodePerLevel = System.currentTimeMillis();

			/// Preparation du XML que l'on va renvoyer
			final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder;
			Document document;
			documentBuilder = documentBuilderFactory.newDocumentBuilder();
			document = documentBuilder.newDocument();
			document.setXmlStandalone(true);

			final HashMap<String, Object[]> resolve = new HashMap<>();
			/// Node -> parent
			final HashMap<String, t_tree> entries = new HashMap<>();

			final long t_initContruction = System.currentTimeMillis();

			processQuery(result, resolve, entries, nodeRight.groupLabel);
			result.close();

			final long t_processQuery = System.currentTimeMillis();

			/// Reconstruct functional tree
			final t_tree root = entries.get(nodeUuid);
			final StringBuilder out = new StringBuilder(256);
			reconstructTree(out, root, entries);

			nodexml.append(out);
			final long t_buildXML = System.currentTimeMillis();

			final long t_convertString = System.currentTimeMillis();

			if (logger.isTraceEnabled()) {
				final long d_right = t_nodeRight - t_start;
				final long d_queryNodes = t_nodePerLevel - t_nodeRight;
				final long d_initConstruct = t_initContruction - t_nodePerLevel;
				final long d_processQuery = t_processQuery - t_initContruction;
				final long d_buildXML = t_buildXML - t_processQuery;
				final long d_convertString = t_convertString - t_buildXML;

				logger.trace(
						"Query Rights: {}\nQuery Nodes: {}\nInit build: {}\nParse Query: {}\nBuild XML: {}\nConvert XML: {}\n",
						d_right, d_queryNodes, d_initConstruct, d_processQuery, d_buildXML, d_convertString);
			}

			return nodexml;
		}
		if (outMimeType.getSubType().equals("json")) {
			return "{" +
					getNodeJsonOutput(c, nodeUuid, withChildren, null, userId, groupId, userRole, label, true) +
					"}";
		}
		return null;
	}

	@Override
	public Object getNodeBySemanticTag(Connection c, MimeType outMimeType, String portfolioUuid, String semantictag,
			int userId, int groupId, String userRole) throws Exception {
		ResultSet res;
		String nodeUuid;

		// On recupere d'abord l'uuid du premier noeud trouve correspondant au semantictag
		res = this.getMysqlNodeUuidBySemanticTag(c, portfolioUuid, semantictag);
		res.next();
		nodeUuid = res.getString("node_uuid");

		if (!cred.hasNodeRight(c, userId, groupId, nodeUuid, Credential.READ)) {
			return null;
		}

		if (outMimeType.getSubType().equals("xml")) {
			return getNodeXmlOutput(c, nodeUuid, true, null, userId, groupId, userRole, null, true);
		}
		if (outMimeType.getSubType().equals("json")) {
			return "{" + getNodeJsonOutput(c, nodeUuid, true, null, userId, groupId, userRole, null, true) + "}";
		}
		return null;
	}

	private StringBuilder getNodeJsonOutput(Connection c, String nodeUuid, boolean withChildren,
			String withChildrenOfXsiType, int userId, int groupId, String userRole, String label, boolean checkSecurity)
			throws SQLException {
		final StringBuilder result = new StringBuilder();
		final ResultSet resNode = getMysqlNode(c, nodeUuid, userId, groupId);
		ResultSet resResource;

		if (checkSecurity) {
			final NodeRight nodeRight = cred.getNodeRight(c, userId, groupId, nodeUuid, label, userRole);
			//
			if (!nodeRight.read) {
				return result;
			}
		}

		if (resNode.next()) {
			result.append("\"").append(resNode.getString("asm_type")).append("\": { ")
					.append(DomUtils.getJsonAttributeOutput("id", resNode.getString("node_uuid"))).append(", ");
			result.append(DomUtils.getJsonAttributeOutput("semantictag", resNode.getString("semtag"))).append(", ");

			if (resNode.getString("xsi_type") != null) {
				if (resNode.getString("xsi_type").length() > 0) {
					result.append(DomUtils.getJsonAttributeOutput("xsi_type", resNode.getString("xsi_type")))
							.append(", ");
				}
			}

			result.append(DomUtils.getJsonAttributeOutput("format", resNode.getString("format"))).append(", ");
			result.append(DomUtils.getJsonAttributeOutput("modified", resNode.getTimestamp("modif_date").toString()))
					.append(", ");

			if (resNode.getString("asm_type").equals("asmResource")) {
				// si asmResource
				try {
					resResource = getMysqlResource(c, nodeUuid);
					if (resResource.next()) {
						result.append("\"#cdata-section\": \"")
								.append(JSONObject.escape(resResource.getString("content"))).append("\"");
					}
				} catch (final Exception ex) {
					ex.printStackTrace();
				}
			}

			if (withChildren || withChildrenOfXsiType != null) {
				String[] arrayChild;
				try {
					if (resNode.getString("node_children_uuid").length() > 0) {
						result.append(", ");
						arrayChild = resNode.getString("node_children_uuid").split(",");
						for (int i = 0; i < (arrayChild.length); i++) {
							final ResultSet resChildNode = this.getMysqlNodeResultset(c, arrayChild[i]);
							String tmpXsiType = "";
							try {
								tmpXsiType = resChildNode.getString("xsi_type");
							} catch (final Exception ex) {
								logger.error("Exception", ex);
							}
							if (withChildrenOfXsiType == null || withChildrenOfXsiType.equals(tmpXsiType)) {
								result.append(getNodeJsonOutput(c, arrayChild[i], true, null, userId, groupId, userRole,
										label, true));
							}

							if (withChildrenOfXsiType == null) {
								if (arrayChild.length > 1) {
									if (i < (arrayChild.length - 1)) {
										result.append(", ");
									}
								}
							}
						}
					}
				} catch (final Exception ex) {
					// Pas de children
				}
			}

			result.append(" } ");
		}

		return result;
	}

	@Override
	public Object getNodeMetadataWad(Connection c, MimeType mimeType, String nodeUuid, boolean b, int userId,
			int groupId, String userRole, String label) throws SQLException {
		final StringBuilder result = new StringBuilder();
		// Verification securite
		final NodeRight nodeRight = cred.getNodeRight(c, userId, groupId, nodeUuid, label, userRole);

		if (!nodeRight.read) {
			return result;
		}

		final ResultSet resNode = getMysqlNode(c, nodeUuid, userId, groupId);

		//try
		//{
		//			resNode.next();

		if (resNode.next()) {
			if (!resNode.getString("asm_type").equals("asmResource")) {
				if (resNode.getString("metadata_wad") != null && !resNode.getString("metadata_wad").equals("")) {
					result.append("<metadata-wad ").append(resNode.getString("metadata_wad")).append("/>");
				} else {
					result.append("<metadata-wad/>");
				}

			}
		}

		resNode.close();

		return result;
	}

	public Integer getNodeOrderByNodeUuid(Connection c, String nodeUuid) {
		PreparedStatement st;
		String sql;
		ResultSet res;

		try {
			sql = "SELECT node_order FROM node WHERE node_uuid = uuid2bin(?) ";
			st = c.prepareStatement(sql);
			st.setString(1, nodeUuid);
			res = st.executeQuery();
			res.next();
			return res.getInt("node_order");
		} catch (final Exception ex) {
			ex.printStackTrace();
			return 0;
		}
	}

	public String getNodeParentUuidByNodeUuid(Connection c, String nodeUuid) {
		PreparedStatement st;
		String sql;
		ResultSet res;

		try {
			sql = "SELECT bin2uuid(node_parent_uuid) AS node_parent_uuid FROM node WHERE node_uuid = uuid2bin(?) ";
			st = c.prepareStatement(sql);
			st.setString(1, nodeUuid);
			res = st.executeQuery();
			res.next();
			return res.getString("node_parent_uuid");
		} catch (final Exception ex) {
			ex.printStackTrace();
			return null;
		}

	}

	/// TODO: A faire un 'benchmark' dessus
	/// Recupere les noeuds en dessous par niveau. Pour faciliter le traitement des shared_node
	/// Mais ea serait beaucoup plus simple de faire un objet a traiter dans le client
	private ResultSet getNodePerLevel(Connection c, String nodeUuid, int userId, int rrgId, Integer cutoff)
			throws SQLException {
		PreparedStatement st;
		String sql;
		ResultSet res = null;

		final long t_start = System.currentTimeMillis();

		try {
			// Take data subset for faster queries (instead of the whole DB each time)
			if (dbserveur.equals("mysql")) {
				sql = "CREATE TEMPORARY TABLE t_node(" +
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
						"asm_type varchar(50) DEFAULT NULL, " +
						"xsi_type varchar(50)  DEFAULT NULL, " +
						"semtag varchar(100) DEFAULT NULL, " +
						"semantictag varchar(100) DEFAULT NULL, " +
						"label varchar(100)  DEFAULT NULL, " +
						"code varchar(255)  DEFAULT NULL, " +
						"descr varchar(100)  DEFAULT NULL, " +
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

				// En double car on ne peut pas faire d'update/select d'une meme table temporaire
				sql = "CREATE TEMPORARY TABLE t_struc_parentid_2(" +
						"uuid binary(16) UNIQUE NOT NULL, " +
						"node_parent_uuid binary(16), " +
						"t_level INT) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();

			} else if (dbserveur.equals("oracle")) {
				String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_node(" +
						"node_uuid VARCHAR2(32)  NOT NULL, " +
						"node_parent_uuid VARCHAR2(32) DEFAULT NULL, " +
						"node_order NUMBER(12) NOT NULL, " +
						"res_node_uuid VARCHAR2(32) DEFAULT NULL, " +
						"res_res_node_uuid VARCHAR2(32) DEFAULT NULL, " +
						"res_context_node_uuid VARCHAR2(32)  DEFAULT NULL, " +
						"shared_res NUMBER(1) NOT NULL, " +
						"shared_node NUMBER(1) NOT NULL, " +
						"shared_node_res NUMBER(1) NOT NULL, " +
						"shared_res_uuid VARCHAR2(32) DEFAULT NULL, " +
						"shared_node_uuid VARCHAR2(32) DEFAULT NULL, " +
						"shared_node_res_uuid VARCHAR2(32) DEFAULT NULL, " +
						"asm_type VARCHAR2(50 CHAR) DEFAULT NULL, " +
						"xsi_type VARCHAR2(50 CHAR)  DEFAULT NULL, " +
						"semtag VARCHAR2(100 CHAR) DEFAULT NULL, " +
						"semantictag VARCHAR2(100 CHAR) DEFAULT NULL, " +
						"label VARCHAR2(100 CHAR)  DEFAULT NULL, " +
						"code VARCHAR2(255 CHAR)  DEFAULT NULL, " +
						"descr VARCHAR2(100 CHAR)  DEFAULT NULL, " +
						"format VARCHAR2(30 CHAR) DEFAULT NULL, " +
						"modif_user_id NUMBER(12) DEFAULT NULL, " +
						"modif_date timestamp DEFAULT NULL, " +
						"portfolio_id VARCHAR2(32) DEFAULT NULL, CONSTRAINT t_node_UK_id UNIQUE (node_uuid)) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_node','" + v_sql + "')}";
				CallableStatement ocs = c.prepareCall(sql);
				ocs.execute();
				ocs.close();

				// XXX 22, because oracle can't clean up correctly after itself
				v_sql = "CREATE GLOBAL TEMPORARY TABLE t_rights_22(" +
						"grid NUMBER(19,0) NOT NULL, " +
						"id VARCHAR2(32) NOT NULL, " +
						"RD NUMBER(1) NOT NULL, " +
						"WR NUMBER(1) NOT NULL, " +
						"DL NUMBER(1) NOT NULL, " +
						"SB NUMBER(1) NOT NULL, " +
						"AD NUMBER(1) NOT NULL, CONSTRAINT t_rights_22_UK_id UNIQUE (id)) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_rights_22','" + v_sql + "')}";
				ocs = c.prepareCall(sql);
				ocs.execute();
				ocs.close();

				/// Pour le filtrage de la structure
				v_sql = "CREATE GLOBAL TEMPORARY TABLE t_struc_parentid(" +
						"uuid VARCHAR2(32) NOT NULL, " +
						"node_parent_uuid VARCHAR2(32), " +
						"t_level NUMBER(10,0)" +
						",  CONSTRAINT t_struc_parentid_UK_uuid UNIQUE (uuid)) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_struc_parentid','" + v_sql + "')}";
				ocs = c.prepareCall(sql);
				ocs.execute();
				ocs.close();

				// En double car on ne peut pas faire d'update/select d'une meme table temporaire
				v_sql = "CREATE GLOBAL TEMPORARY TABLE t_struc_parentid_2(" +
						"uuid VARCHAR2(32) NOT NULL, " +
						"node_parent_uuid VARCHAR2(32), " +
						"t_level NUMBER(10,0)" +
						",  CONSTRAINT t_struc_parentid_2_UK_uuid UNIQUE (uuid)) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_struc_parentid_2','" + v_sql + "')}";
				ocs = c.prepareCall(sql);
				ocs.execute();
				ocs.close();
			}

			final long t_tempTable = System.currentTimeMillis();

			/// Portfolio id, gonna need that later
			sql = "SELECT bin2uuid(portfolio_id) AS portfolio_id FROM node WHERE node_uuid=uuid2bin(?)";
			st = c.prepareStatement(sql);
			st.setString(1, nodeUuid);
			res = st.executeQuery();
			if (!res.next()) { // A non-existing uuid has been given
				return null;
			}
			final String portfolioid = res.getString("portfolio_id");
			res.close();
			st.close();

			if (dbserveur.equals("oracle")) {
				sql = "INSERT INTO t_node " +
						"SELECT node_uuid, node_parent_uuid, node_order, res_node_uuid, res_res_node_uuid, res_context_node_uuid, shared_res, shared_node, shared_node_res, shared_res_uuid, shared_node_uuid, shared_node_res_uuid, asm_type, xsi_type, semtag, semantictag, label, code, descr, format, modif_user_id, modif_date, portfolio_id " +
						"FROM node n " +
						"WHERE n.portfolio_id=uuid2bin(?)";
			} else {
				/// Init temp data table
				sql = "INSERT INTO t_node (node_uuid, node_parent_uuid, node_order, res_node_uuid, res_res_node_uuid, res_context_node_uuid, shared_res, shared_node, shared_node_res, shared_res_uuid, shared_node_uuid, shared_node_res_uuid, asm_type, xsi_type, semtag, semantictag, label, code, descr, format, modif_user_id, modif_date, portfolio_id)" +
						" SELECT distinct node_uuid, node_parent_uuid, node_order, res_node_uuid, res_res_node_uuid, res_context_node_uuid, shared_res, shared_node, shared_node_res, shared_res_uuid, shared_node_uuid, shared_node_res_uuid, asm_type, xsi_type, semtag, semantictag, label, code, descr, format, modif_user_id, modif_date, portfolio_id " +
						" FROM node n " +
						" WHERE n.portfolio_id=uuid2bin(?)";
			}
			st = c.prepareStatement(sql);
			st.setString(1, portfolioid);
			st.executeUpdate();
			st.close();

			final long t_dataTable = System.currentTimeMillis();

			/// Initialise la descente des noeuds, si il y a un partage on partira de le, sinon du noeud par defaut
			/// FIXME: There will be something with shared_node_uuid
			sql = "INSERT INTO t_struc_parentid(uuid, node_parent_uuid, t_level) " +
					"SELECT COALESCE(n.shared_node_uuid, n.node_uuid), n.node_parent_uuid, 0 " +
					"FROM t_node n " +
					"WHERE n.node_uuid=uuid2bin(?)";
			st = c.prepareStatement(sql);
			st.setString(1, nodeUuid);
			st.executeUpdate();
			st.close();

			final long t_initNode = System.currentTimeMillis();

			/// On boucle, avec les shared_node si ils existent.
			/// FIXME: Possiblite de boucle infini
			int level = 0;
			int added = 1;
			if (dbserveur.equals("mysql")) {
				sql = "INSERT IGNORE INTO t_struc_parentid_2(uuid, node_parent_uuid, t_level) ";
			} else if (dbserveur.equals("oracle")) {
				sql = "INSERT /*+ ignore_row_on_dupkey_index(t_struc_parentid_2,t_struc_parentid_2_UK_uuid)*/ INTO t_struc_parentid_2(uuid, node_parent_uuid, t_level) ";
			}
			sql += "SELECT COALESCE(n.shared_node_uuid, n.node_uuid), n.node_parent_uuid, ? " +
					"FROM t_node n WHERE n.portfolio_id=uuid2bin(?) AND n.node_parent_uuid IN (SELECT uuid FROM t_struc_parentid t " +
					"WHERE t.t_level=?)";

			String sqlTemp = null;
			if (dbserveur.equals("mysql")) {
				sqlTemp = "INSERT IGNORE INTO t_struc_parentid SELECT * FROM t_struc_parentid_2;";
			} else if (dbserveur.equals("oracle")) {
				sqlTemp = "INSERT /*+ ignore_row_on_dupkey_index(t_struc_parentid,t_struc_parentid_UK_uuid)*/ INTO t_struc_parentid SELECT * FROM t_struc_parentid_2";
			}
			final PreparedStatement stTemp = c.prepareStatement(sqlTemp);

			final long t_initLoop = System.currentTimeMillis();

			st = c.prepareStatement(sql);
			st.setString(2, portfolioid);
			while (added != 0 && (cutoff == null || level < cutoff)) {
				st.setInt(1, level + 1);
				st.setInt(3, level);
				st.executeUpdate();
				added = stTemp.executeUpdate(); // On s'arrete quand rien e ete ajoute
				level = level + 1; // Prochaine etape
			}
			st.close();
			stTemp.close();

			final long t_endLoop = System.currentTimeMillis();

			if (cred.isDesigner(c, userId, nodeUuid) || cred.isAdmin(c, userId)) {
				sql = "INSERT INTO t_rights_22(grid, id, RD, WR, DL, SB, AD) " +
						"SELECT 0, ts.uuid, 1, 1, 1, 0, 0 " +
						"FROM t_struc_parentid ts";
				st = c.prepareStatement(sql);
			} else {
				if (cred.isPublic(c, nodeUuid, null)) {
					sql = "INSERT INTO t_rights_22(grid, id, RD, WR, DL, SB, AD) " +
							"SELECT 0, ts.uuid, 1, 0, 0, 0, 0 " +
							"FROM t_struc_parentid ts";
					st = c.prepareStatement(sql);
					st.executeUpdate();
					st.close();
				}

				///// Remove node for simple users if there's a date limitation
				sql = "DELETE FROM t_struc_parentid WHERE uuid=uuid2bin(?)";
				final PreparedStatement stFilter = c.prepareStatement(sql);

				// Fetch metadata
				sql = "SELECT bin2uuid(uuid) AS node_uuid, metadata_wad FROM node n, t_struc_parentid tsp " +
						"WHERE n.node_uuid=tsp.uuid " +
						"AND ((metadata_wad LIKE '%seestart%') OR (metadata_wad LIKE '%seeend%'))";
				st = c.prepareStatement(sql);
				res = st.executeQuery();
				String meta;
				while (res.next()) {
					/// Checking if date has been declared
					meta = res.getString("metadata_wad");

					final Matcher startMatcher = SEESTART_PAT.matcher(meta);
					final Matcher endMatcher = SEEEND_PAT.matcher(meta);
					String seestart = null;
					String seeend = null;
					if (startMatcher.find()) {
						seestart = startMatcher.group(1);
					}
					if (endMatcher.find()) {
						seeend = endMatcher.group(1);
					}

					final String uuid = res.getString("node_uuid");
					final long currentTime = System.currentTimeMillis();
					// Nothing on that line
					try {
						if (seestart == null && seeend == null) {
							continue;
						}
						if (seestart != null && seeend == null) { // Only a start view
							Date dt;
							dt = SIMPLE_DATE_FORMAT.parse(seestart);
							final long starttime = dt.getTime();
							if (starttime > currentTime) {
								stFilter.setString(1, uuid);
								stFilter.executeUpdate();
							}
						} else if (seestart == null) { // Only end view
							final Date dt = SIMPLE_DATE_FORMAT.parse(seeend);
							final long endtime = dt.getTime();
							if (endtime < currentTime) {
								stFilter.setString(1, uuid);
								stFilter.executeUpdate();
							}
						} else { // Restriction on start and end
							Date dt = SIMPLE_DATE_FORMAT.parse(seestart);
							final long starttime = dt.getTime();
							dt = SIMPLE_DATE_FORMAT.parse(seeend);
							final long endtime = dt.getTime();
							if (endtime < currentTime || starttime > currentTime) {
								stFilter.setString(1, uuid);
								stFilter.executeUpdate();
							}
						}
					} catch (final ParseException e) {
						// For some reason, date isn't formatted correctly
						// Should never happen
						e.printStackTrace();
					}
				}
				res.close();
				stFilter.close();
				st.close();

				// Aggregation des droits avec 'all', l'appartenance du groupe de l'utilisateur, et les droits propres e l'utilisateur
				if (dbserveur.equals("mysql")) {
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
				} else if (dbserveur.equals("oracle")) {
					sql = "MERGE INTO t_rights_22 d USING (";
					sql += "SELECT MAX(gr.grid) AS grid, gr.id, MAX(gr.RD) AS RD, MAX(gr.WR) AS WR, MAX(gr.DL) AS DL, MAX(gr.SB) AS SB, MAX(gr.AD) AS AD " + // FIXME MAX(gr.grid) will have unintended consequences
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
			}
			st.executeUpdate();
			st.close();

			final long t_allRights = System.currentTimeMillis();

			// Selectionne les donnees selon la filtration
			sql = "SELECT bin2uuid(n.node_uuid) AS node_uuid," +
					" n.node_children_uuid, " +
					" n.node_order," +
					" n.metadata, n.metadata_wad, n.metadata_epm," +
					" n.shared_node AS shared_node," +
					" bin2uuid(n.shared_node_res_uuid) AS shared_node_res_uuid," +
					" bin2uuid(n.res_node_uuid) AS res_node_uuid," +
					" n.modif_date," +
					" r1.xsi_type AS r1_type, r1.content AS r1_content," + // donnee res_node
					" bin2uuid(n.res_res_node_uuid) as res_res_node_uuid," +
					" r1.modif_date AS r1_modif_date, " +
					" r2.content AS r2_content," + // donnee res_res_node
					" bin2uuid(n.res_context_node_uuid) as res_context_node_uuid," +
					" r2.modif_date AS r2_modif_date, " +
					" r3.content AS r3_content," + // donnee res_context
					" r3.modif_date AS r3_modif_date, " +
					" n.asm_type, n.xsi_type," +
					" tr.RD, tr.WR, tr.SB, tr.DL, NULL AS types_id, NULL AS rules_id," + // info sur les droits
					" bin2uuid(n.portfolio_id) AS portfolio_id" +
					" FROM node n" + // Going back to original table, mainly for list of child nodes
					" LEFT JOIN resource_table r1 ON n.res_node_uuid=r1.node_uuid" + // Recuperation des donnees res_node
					" LEFT JOIN resource_table r2 ON n.res_res_node_uuid=r2.node_uuid" + // Recuperation des donnees res_res_node
					" LEFT JOIN resource_table r3 ON n.res_context_node_uuid=r3.node_uuid" + // Recuperation des donnees res_context
					" LEFT JOIN t_rights_22 tr" + // Verification des droits
					" ON n.node_uuid=tr.id" + // On doit au moins avoir le droit de lecture
					" WHERE tr.RD=1 AND n.node_uuid IN (SELECT uuid FROM t_struc_parentid)"; // Selon note filtrage, prendre les noeud necessaire

			st = c.prepareStatement(sql);
			res = st.executeQuery();

			final long t_aggregate = System.currentTimeMillis();

			if (logger.isTraceEnabled()) {
				final long d_tempTable = t_tempTable - t_start;
				final long d_initData = t_dataTable - t_tempTable;
				final long d_initRecusion = t_initNode - t_dataTable;
				final long d_initLoop = t_initLoop - t_initNode;
				final long d_endLoop = t_endLoop - t_initLoop;
				final long d_fetchRights = t_allRights - t_endLoop;
				final long d_aggregateInfo = t_aggregate - t_allRights;

				logger.trace(
						"===== Get node per level ====\nTemp table creation: {}\nInit data: {}\nInit node recursion: {}\nInit queries recursion: {}\n" +
								"End loop: {}\nAdd 'all' rights: {}\nAggregate info: {}\n",
						d_tempTable, d_initData, d_initRecusion, d_initLoop, d_endLoop, d_fetchRights, d_aggregateInfo);
			}
		} catch (final SQLException e) {
			logger.error("SQL error", e);
		} finally {
			try {
				// Les 'pooled connection' ne se ferment pas vraiment. On nettoie manuellement les tables temporaires...
				if (dbserveur.equals("mysql")) {
					sql = "DROP TEMPORARY TABLE IF EXISTS t_node, t_rights_22, t_struc_parentid, t_struc_parentid_2";
					st = c.prepareStatement(sql);
					st.execute();
					st.close();
				}
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}

		return res;
	}

	@Override
	public String getNodePortfolioId(Connection c, String nodeUuid) throws Exception {
		final String sql = "SELECT bin2uuid(portfolio_id) FROM node WHERE node_uuid=uuid2bin(?)";
		String portfolioid = "";
		final PreparedStatement st = c.prepareStatement(sql);
		st.setString(1, nodeUuid);
		final ResultSet res = st.executeQuery();
		if (res.next()) {
			portfolioid = res.getString(1);
		}
		st.close();

		return portfolioid;
	}

	@Override
	public String getNodeRights(Connection c, String nodeUuid, int userId, int groupId) throws Exception {
		String sql;
		PreparedStatement st;
		ResultSet res;
		String result = "";

		try {
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
			final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
			final DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
			final Document doc = documentBuilder.newDocument();
			final Element root = doc.createElement("node");
			doc.appendChild(root);
			root.setAttribute("uuid", nodeUuid);
			while (res.next()) {
				final int grid = res.getInt("grid");
				final String rolename = res.getString("label");
				final String readRight = res.getInt("RD") == 1 ? DB_YES : DB_NO;
				final String writeRight = res.getInt("WR") == 1 ? DB_YES : DB_NO;
				final String deleteRight = res.getInt("DL") == 1 ? DB_YES : DB_NO;
				final String submitRight = res.getInt("SB") == 1 ? DB_YES : DB_NO;

				final Element role = doc.createElement("role");
				root.appendChild(role);
				role.setAttribute("name", rolename);
				role.setAttribute("id", Integer.toString(grid));

				final Element right = doc.createElement("right");
				role.appendChild(right);
				right.setAttribute("RD", readRight);
				right.setAttribute("WR", writeRight);
				right.setAttribute("DL", deleteRight);
				right.setAttribute("SB", submitRight);
			}

			st.close();

			final StringWriter stw = new StringWriter();
			final Transformer serializer = TransformerFactory.newInstance().newTransformer();
			serializer.transform(new DOMSource(doc), new StreamResult(stw));

			result = stw.toString();
		} catch (final Exception e) {
			logger.error("Intercepted error", e);
			e.printStackTrace();
			return result;
		}

		return result;
	}

	@Override
	public Object getNodes(Connection c, MimeType outMimeType, String portfolioUuid, int userId, int groupId,
			String userRole, String semtag, String parentUuid, String filterId, String filterParameters, String sortId,
			Integer cutoff) throws SQLException {
		return getNodeXmlListOutput(c, parentUuid, true, userId, groupId);
	}

	@Override
	public Object getNodes(Connection c, MimeType mimeType, String portfoliocode, String semtag, int userId,
			int groupId, String userRole, String semtag_parent, String code_parent, Integer cutoff)
			throws SQLException {
		PreparedStatement st = null;
		String sql;
		ResultSet res;
		ResultSet res3;
		String pid;

		pid = this.getPortfolioUuidByPortfolioCode(c, portfoliocode);

		if ("".equals(pid)) {
			throw new RestWebApplicationException(Status.NOT_FOUND, "Not found");
		}

		final NodeRight right = cred.getPortfolioRight(c, userId, groupId, pid, Credential.READ, userRole);
		if (!right.read && !cred.isAdmin(c, userId) && !cred.isPublic(c, null, pid) && !cred.isOwner(c, userId, pid)) {
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");
		}

		final StringBuilder result = new StringBuilder();

		try {
			// Not null, not empty
			// When we have a set, subset, and code of selected item
			/// Searching nodes subset where semtag is under semtag_parent. First filtering is with code_parent
			if (semtag_parent != null && !"".equals(semtag_parent) && code_parent != null && !"".equals(code_parent)) {
				/// Temp table where we will search into
				if (dbserveur.equals("mysql")) {
					sql = "CREATE TEMPORARY TABLE t_s_node_2(" +
							"node_uuid binary(16)  NOT NULL, " +
							"node_parent_uuid binary(16) DEFAULT NULL, " +
							"asm_type varchar(50) DEFAULT NULL, " +
							"semtag varchar(100) DEFAULT NULL, " +
							"semantictag varchar(100) DEFAULT NULL, " +
							"code varchar(255)  DEFAULT NULL," +
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
				} else if (dbserveur.equals("oracle")) {
					String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_s_node_2(" +
							"node_uuid VARCHAR2(32)  NOT NULL, " +
							"node_parent_uuid VARCHAR2(32) DEFAULT NULL, " +
							"asm_type VARCHAR2(50 CHAR) DEFAULT NULL, " +
							"semtag VARCHAR2(100 CHAR) DEFAULT NULL, " +
							"semantictag VARCHAR2(100 CHAR) DEFAULT NULL, " +
							"code VARCHAR2(255 CHAR)  DEFAULT NULL," +
							"node_order NUMBER(10,0) NOT NULL) ON COMMIT PRESERVE ROWS";
					sql = "{call create_or_empty_table('t_s_node_2','" + v_sql + "')}";
					CallableStatement ocs = c.prepareCall(sql);
					ocs.execute();
					ocs.close();

					v_sql = "CREATE GLOBAL TEMPORARY TABLE t_struc_parentid(" +
							"uuid VARCHAR2(32) NOT NULL, " +
							"node_parent_uuid VARCHAR2(32), " +
							"t_level NUMBER(10,0)" +
							",  CONSTRAINT t_struc_parentid_UK_uuid UNIQUE (uuid)) ON COMMIT PRESERVE ROWS";
					sql = "{call create_or_empty_table('t_struc_parentid','" + v_sql + "')}";
					ocs = c.prepareCall(sql);
					ocs.execute();
					ocs.close();

					v_sql = "CREATE GLOBAL TEMPORARY TABLE t_struc_parentid_2(" +
							"uuid VARCHAR2(32) NOT NULL, " +
							"node_parent_uuid VARCHAR2(32), " +
							"t_level NUMBER(10,0)" +
							",  CONSTRAINT t_struc_parentid_2_UK_uuid UNIQUE (uuid)) ON COMMIT PRESERVE ROWS";
					sql = "{call create_or_empty_table('t_struc_parentid_2','" + v_sql + "')}";
					ocs = c.prepareCall(sql);
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
				try {
					sql = "INSERT INTO t_struc_parentid " +
							"SELECT node_uuid, node_parent_uuid, 0 " +
							"FROM t_s_node_2 WHERE semantictag LIKE ? AND code = ?";
					//sql = "SELECT bin2uuid(node_uuid) AS node_uuid, bin2uuid(res_res_node_uuid) AS res_res_node_uuid, node_children_uuid, code, asm_type, label FROM node WHERE portfolio_id = uuid2bin('c884bdcd-2165-469b-9939-14376f7f3500') AND metadata LIKE '%semantictag=%competence%'";
					st = c.prepareStatement(sql);
					st.setString(1, "%" + semtag_parent + "%");
					st.setString(2, code_parent);
					st.executeUpdate();

					int level = 0;
					int added = 1;
					if (dbserveur.equals("mysql")) {
						sql = "INSERT IGNORE INTO t_struc_parentid_2(uuid, node_parent_uuid, t_level) ";
					} else if (dbserveur.equals("oracle")) {
						sql = "INSERT /*+ ignore_row_on_dupkey_index(t_struc_parentid_2,t_struc_parentid_2_UK_uuid)*/ INTO t_struc_parentid_2(uuid, node_parent_uuid, t_level) ";
					}
					sql += "SELECT n.node_uuid, n.node_parent_uuid, ? " +
							"FROM t_s_node_2 n WHERE n.node_parent_uuid IN (SELECT uuid FROM t_struc_parentid t " +
							"WHERE t.t_level=?)";

					String sqlTemp = null;
					if (dbserveur.equals("mysql")) {
						sqlTemp = "INSERT IGNORE INTO t_struc_parentid SELECT * FROM t_struc_parentid_2;";
					} else if (dbserveur.equals("oracle")) {
						sqlTemp = "INSERT /*+ ignore_row_on_dupkey_index(t_struc_parentid,t_struc_parentid_UK_uuid)*/ INTO t_struc_parentid SELECT * FROM t_struc_parentid_2";
					}
					final PreparedStatement stTemp = c.prepareStatement(sqlTemp);

					st = c.prepareStatement(sql);

					while (added != 0 && (cutoff == null || level < cutoff)) {
						st.setInt(1, level + 1);
						st.setInt(2, level);
						st.executeUpdate();
						added = stTemp.executeUpdate(); // On s'arrete quand rien e ete ajoute
						level = level + 1; // Prochaine etape
					}
					st.close();
					stTemp.close();

					/// SELECT semtag from under parent_tag, parent_code
					sql = "SELECT bin2uuid(node_uuid) AS node_uuid, asm_type " +
							"FROM t_s_node_2 " +
							"WHERE semantictag LIKE ? AND node_uuid IN (SELECT uuid FROM t_struc_parentid) " +
							"ORDER BY code, node_order";
					st = c.prepareStatement(sql);
					st.setString(1, "%" + semtag + "%");
					res3 = st.executeQuery();

					result.append("<nodes>");
					while (res3.next()) /// FIXME Could be done in a better way
					{
						result.append("<node ");
						result.append(DomUtils.getXmlAttributeOutput("id", res3.getString("node_uuid")));
						result.append(">");
						if (res3.getString("asm_type").equalsIgnoreCase("asmContext")) {
							result.append(getRessource(c, res3.getString("node_uuid"), userId, groupId, "Context"));
						} else {
							result.append(getRessource(c, res3.getString("node_uuid"), userId, groupId, "nonContext"));
						}
						result.append("</node>");
					}
					result.append("</nodes>");
				} catch (final Exception ex) {
					ex.printStackTrace();
					return null;
				}

				return result.toString();
			}
			sql = "SELECT  bin2uuid(portfolio_id) AS portfolio_id,bin2uuid(root_node_uuid) as root_node_uuid, modif_user_id,modif_date, active, user_id " +
					"FROM portfolio " +
					"WHERE portfolio_id = uuid2bin(?) ";
			st = c.prepareStatement(sql);
			st.setString(1, pid);
			res = st.executeQuery();

			if (res.next()) {
				ResultSet res1;

				try {
					res1 = getMysqlNodeUuidBySemanticTag(c, pid, semtag);
				} catch (final Exception ex) {
					ex.printStackTrace();
					return null;
				}

				result.append("<nodes>");

				while (res1 != null && res1.next()) {
					result.append("<node ");
					result.append(DomUtils.getXmlAttributeOutput("id", res1.getString("node_uuid")));
					result.append(">");
					if (res1.getString("asm_type").equalsIgnoreCase("asmContext")) {
						result.append(getRessource(c, res1.getString("node_uuid"), userId, groupId, "Context"));
					} else {
						result.append(getRessource(c, res1.getString("node_uuid"), userId, groupId, "nonContext"));
					}
					result.append("</node>");
				}
				result.append("</nodes>");
			}
		} catch (final Exception e) {
			try {
				// Les 'pooled connection' ne se ferment pas vraiment. On nettoie manuellement les tables temporaires...
				if (dbserveur.equals("mysql")) {
					sql = "DROP TEMPORARY TABLE IF EXISTS t_s_node_2, t_struc_parentid, t_struc_parentid_2";
					st = c.prepareStatement(sql);
					st.execute();
					st.close();
				}
			} catch (final SQLException se) {
				se.printStackTrace();
			}
			e.printStackTrace();
		} finally {
			// Les 'pooled connection' ne se ferment pas vraiment. On nettoie manuellement les tables temporaires...
			if (dbserveur.equals("mysql")) {
				sql = "DROP TEMPORARY TABLE IF EXISTS t_s_node_2, t_struc_parentid, t_struc_parentid_2";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			}
		}

		return result.toString();
	}

	@Override
	public Object getNodesBySemanticTag(Connection c, MimeType outMimeType, int userId, int groupId,
			String portfolioUuid, String semanticTag) throws SQLException {
		final ResultSet res = this.getMysqlNodeUuidBySemanticTag(c, portfolioUuid, semanticTag);
		StringBuilder result = new StringBuilder();
		if (outMimeType.getSubType().equals("xml")) {
			result = new StringBuilder("<nodes>");
			while (res.next()) {
				final String nodeUuid = res.getString("node_uuid");
				if (!cred.hasNodeRight(c, userId, groupId, nodeUuid, Credential.READ)) {
					return null;
				}

				result.append("<node ");
				result.append(DomUtils.getXmlAttributeOutput("id", nodeUuid)).append(" ");
				result.append(">");
				result.append("</node>");
			}
			result.append("</nodes>");
		} else if (outMimeType.getSubType().equals("json")) {

			result = new StringBuilder("{ \"nodes\": { \"node\": [");
			boolean firstPass = false;
			while (res.next()) {
				if (firstPass) {
					result.append(",");
				}
				result.append("{ ");
				result.append(DomUtils.getJsonAttributeOutput("id", res.getString("id"))).append(", ");

				result.append("} ");
				firstPass = true;
			}
			result.append("] } }");
		}

		return result.toString();
	}

	@Override
	public Object getNodesParent(Connection c, MimeType mimeType, String portfoliocode, String semtag, int userId,
			int groupId, String semtag_parent, String code_parent) throws Exception {
		PreparedStatement st = null;
		String sql;
		ResultSet res3;
		ResultSet res4;
		final String pid = this.getPortfolioUuidByPortfolioCode(c, portfoliocode);
		final StringBuilder result = new StringBuilder();

		try {
			sql = "SELECT  bin2uuid(portfolio_id) AS portfolio_id,bin2uuid(root_node_uuid) as root_node_uuid, modif_user_id,modif_date, active, user_id " +
					"FROM portfolio " +
					"WHERE portfolio_id = uuid2bin(?) ";
			st = c.prepareStatement(sql);
			st.setString(1, pid);
			st.executeQuery();

			sql = "SELECT  bin2uuid(node_uuid) AS node_uuid,bin2uuid(node_children_uuid) as node_children_uuid, code, semantictag " +
					"FROM node " +
					"WHERE portfolio_id = uuid2bin(?) " +
					"and  metadata LIKE ? " +
					"and code = ?";
			st = c.prepareStatement(sql);
			st.setString(1, pid);
			st.setString(2, "%semantictag=%" + semtag_parent + "%");
			st.setString(3, code_parent);
			res3 = st.executeQuery();

			if (res3.next()) {
				final String children = res3.getString("node_children_uuid");
				final String delim = ",";
				String[] listChildren;
				listChildren = children.split(delim);

				for (int i = 0; i <= listChildren.length; i++) {

					sql = "SELECT  bin2uuid(node_uuid) AS node_uuid, code, semantictag " +
							"FROM node " +
							"WHERE semantictag = ? and node_uuid = ?";
					st = c.prepareStatement(sql);
					//				st.setString(1, listChildren[i]);
					//				st.setString(2, semtag);
					st.setString(1, semtag);
					st.setString(2, listChildren[i]);
					res4 = st.executeQuery();

					result.append("<nodes>");

					if (res4.next()) {

						result.append("<node ");
						result.append(DomUtils.getXmlAttributeOutput("id", res4.getString("node_uuid")));
						result.append(">");
						result.append("</node>");

					}

					result.append("</nodes>");
				}
			}
		} catch (final Exception e) {
			e.printStackTrace();

		}

		return null;
	}

	public String getNodeUuidBySemtag(Connection c, String semtag, String uuid_parent) throws SQLException {

		PreparedStatement st;
		String sql;
		PreparedStatement st2;
		String sql2;
		ResultSet res;
		String result = null;

		try {
			if (dbserveur.equals("mysql")) {
				sql = "CREATE TEMPORARY TABLE t_node(" +
						"node_uuid binary(16) UNIQUE NOT NULL, " +
						"semantictag varchar(100) DEFAULT NULL, " +
						"res_node_uuid binary(16) DEFAULT NULL, " +
						"res_res_node_uuid binary(16) DEFAULT NULL, " +
						"res_context_node_uuid binary(16) DEFAULT NULL, " +
						"t_level INT) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")) {
				final String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_node(" +
						"node_uuid VARCHAR2(32) UNIQUE NOT NULL, " +
						"semantictag VARCHAR2(100 CHAR) DEFAULT NULL, " +
						"res_node_uuid VARCHAR2(32) DEFAULT NULL, " +
						"res_res_node_uuid VARCHAR2(32) DEFAULT NULL, " +
						"res_context_node_uuid VARCHAR2(32) DEFAULT NULL, " +
						"t_level NUMBER(10,0)) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_node','" + v_sql + "')}";
				final CallableStatement ocs = c.prepareCall(sql);
				ocs.execute();
				ocs.close();
			}

			//deuxieme fois pour pouvoir lire et ecrire

			if (dbserveur.equals("mysql")) {
				sql = "CREATE TEMPORARY TABLE t_node_2(" +
						"node_uuid binary(16) UNIQUE NOT NULL, " +
						"semantictag varchar(100) DEFAULT NULL, " +
						"res_node_uuid binary(16) DEFAULT NULL, " +
						"res_res_node_uuid binary(16) DEFAULT NULL, " +
						"res_context_node_uuid binary(16) DEFAULT NULL, " +
						"t_level INT) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")) {
				final String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_node_2(" +
						"node_uuid VARCHAR2(32) UNIQUE NOT NULL, " +
						"semantictag VARCHAR2(100 CHAR) DEFAULT NULL, " +
						"res_node_uuid VARCHAR2(32) DEFAULT NULL, " +
						"res_res_node_uuid VARCHAR2(32) DEFAULT NULL, " +
						"res_context_node_uuid VARCHAR2(32) DEFAULT NULL, " +
						"t_level NUMBER(10,0)) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_node','" + v_sql + "')}";
				final CallableStatement ocs = c.prepareCall(sql);
				ocs.execute();
				ocs.close();
			}

			// Init table
			sql = "INSERT INTO t_node(node_uuid, semantictag, res_node_uuid, res_res_node_uuid, res_context_node_uuid, t_level) " +
					"SELECT n.node_uuid, n.semantictag, n.res_node_uuid, n.res_res_node_uuid, n.res_context_node_uuid, 0 " +
					"FROM node n " +
					"WHERE n.node_uuid=uuid2bin(\"" +
					uuid_parent +
					"\")";
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			sql = "INSERT INTO t_node_2(node_uuid, semantictag, res_node_uuid, res_res_node_uuid, res_context_node_uuid, t_level) " +
					"SELECT n.node_uuid, n.semantictag, n.res_node_uuid, n.res_res_node_uuid, n.res_context_node_uuid, 0 " +
					"FROM node n " +
					"WHERE n.node_uuid=uuid2bin(\"" +
					uuid_parent +
					"\")";
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			/// On boucle, sera toujours <= au "nombre de noeud du portfolio"
			int level = 0;
			int added = 1;
			if (dbserveur.equals("mysql")) {
				sql = "INSERT IGNORE INTO t_node_2(node_uuid, semantictag, res_node_uuid, res_res_node_uuid, res_context_node_uuid, t_level) ";
			} else if (dbserveur.equals("oracle")) {
				sql = "INSERT /*+ ignore_row_on_dupkey_index(t_node_2,t_node_2_UK_uuid)*/ INTO t_node_2(node_uuid, semanctictag, res_node_uuid, res_res_node_uuid, res_context_node_uuid, t_level) ";
			}
			sql += "SELECT n.node_uuid, n.semantictag, n.res_node_uuid, n.res_res_node_uuid, n.res_context_node_uuid, ? " +
					"FROM node n WHERE n.node_parent_uuid IN (SELECT node_uuid FROM t_node t " +
					"WHERE t.t_level=?)";

			String sqlTemp = null;
			if (dbserveur.equals("mysql")) {
				sqlTemp = "INSERT IGNORE INTO t_node SELECT * FROM t_node_2;";
			} else if (dbserveur.equals("oracle")) {
				sqlTemp = "INSERT /*+ ignore_row_on_dupkey_index(t_node,t_node_UK_uuid)*/ INTO t_node SELECT * FROM t_node_2";
			}
			final PreparedStatement stTemp = c.prepareStatement(sqlTemp);

			st = c.prepareStatement(sql);
			while (added != 0) {
				st.setInt(1, level + 1);
				st.setInt(2, level);
				st.executeUpdate();
				added = stTemp.executeUpdate(); // On s'arrete quand rien a ete ajoute
				level = level + 1; // Prochaine etape
			}

			sql2 = "SELECT bin2uuid(node_uuid) FROM t_node WHERE semantictag LIKE \"" + semtag + "\"";
			st2 = c.prepareStatement(sql2);
			res = st2.executeQuery();
			res.next();
			result = res.getString(1);
			st2.close();
			st.close();
			stTemp.close();

		} catch (final Exception e) {
			logger.error("Exception", e);
		} finally {
			sql = "DROP TEMPORARY TABLE IF EXISTS t_node,t_node_2";
			st = c.prepareStatement(sql);
			st.execute();
			st.close();
		}
		return result;
	}

	@Override
	public Object getNodeWithXSL(Connection c, MimeType mimeType, String nodeUuid, String xslFile, String parameters,
			int userId, int groupId, String userRole) {
		String xml;
		try {
			/// Preparing parameters for future need, format: "par1:par1val;par2:par2val;..."
			final String[] table = parameters.split(";");
			final int parSize = table.length;
			final String[] param = new String[parSize];
			final String[] paramVal = new String[parSize];
			for (int i = 0; i < parSize; ++i) {
				final String line = table[i];
				final int var = line.indexOf(":");
				param[i] = line.substring(0, var);
				paramVal[i] = line.substring(var + 1);
			}

			/// TODO: Test this more, should use getNode rather than having another output
			xml = getNode(c, new MimeType("text/xml"), nodeUuid, true, userId, groupId, userRole, null, null)
					.toString();
			if (xml == null) {
				return null;
			}

			//			xml = getNodeXmlOutput(nodeUuid,true,null,userId, groupId, null,true).toString();
			return DomUtils.processXSLTfile2String(DomUtils.xmlString2Document(xml, new StringBuilder()), xslFile,
					param, paramVal, new StringBuilder());
		} catch (final Exception e) {
			//			e.printStackTrace();
			logger.error("Managed error:", e);
			return null;
		}
	}

	private StringBuilder getNodeXmlListOutput(Connection c, String nodeUuid, boolean withChildren, int userId,
			int groupId) throws SQLException {
		final StringBuilder result = new StringBuilder();

		if (!cred.hasNodeRight(c, userId, groupId, nodeUuid, Credential.READ)) {
			return result;
		}

		final ResultSet resNode = getMysqlNode(c, nodeUuid, userId, groupId);

		final String indentation = "";

		try {
			//			resNode.next();
			if (resNode.next()) {
				result.append(indentation).append("<").append(resNode.getString("asm_type")).append(" ")
						.append(DomUtils.getXmlAttributeOutput("id", resNode.getString("node_uuid"))).append(" ");
				result.append(DomUtils.getXmlAttributeOutput("semantictag", resNode.getString("semtag"))).append(" ");

				if (resNode.getString("xsi_type") != null) {
					if (resNode.getString("xsi_type").length() > 0) {
						result.append(DomUtils.getXmlAttributeOutput("xsi_type", resNode.getString("xsi_type")))
								.append(" ");
					}
				}

				result.append(DomUtils.getXmlAttributeOutput("format", resNode.getString("format"))).append(" ");

				result.append(DomUtils.getXmlAttributeOutput("modified", resNode.getTimestamp("modif_date").toString()))
						.append(" ");

				result.append("/>");

				//if (resNode.getString("asm_type").equals("asmResource")) {
				// si asmResource
				//                    try {
				//                    } catch (Exception ex) {
				//                        ex.printStackTrace();
				//                    }
				//}

				if (withChildren) {
					String[] arrayChild;
					try {
						if (resNode.getString("node_children_uuid").length() > 0) {
							arrayChild = resNode.getString("node_children_uuid").split(",");
							for (final String s : arrayChild) {
								result.append(getNodeXmlListOutput(c, s, true, userId, groupId));
							}
						}
					} catch (final Exception ex) {
						// Pas de children
					}
				}

			}
		} catch (final Exception ex) {
			ex.printStackTrace();
		}
		return result;
	}

	private StringBuilder getNodeXmlOutput(Connection c, String nodeUuid, boolean withChildren,
			String withChildrenOfXsiType, int userId, int groupId, String userRole, String label, boolean checkSecurity)
			throws SQLException {
		final StringBuilder result = new StringBuilder();
		// Verification securite
		if (checkSecurity) {
			NodeRight nodeRight = cred.getNodeRight(c, userId, groupId, nodeUuid, label, userRole);
			if (!nodeRight.read) {
				userId = cred.getPublicUid(c);
				//			NodeRight nodeRight = new NodeRight(false,false,false,false,false,false);
				/// Verifie les droits avec le compte publique (derniere chance)
				nodeRight = cred.getPublicRight(c, userId, 123, nodeUuid, "dummy");
				if (!nodeRight.read) {
					return result;
				}
			}
		}

		final ResultSet resNode = getMysqlNode(c, nodeUuid, userId, groupId);
		ResultSet resResource;

		final String indentation = " ";

		long metaxml = 0;
		long resource = 0;
		long children = 0;
		long end;
		final long start = System.currentTimeMillis();

		if (resNode.next()) {
			if (resNode.getString("shared_node_uuid") != null) {
				result.append(getNodeXmlOutput(c, resNode.getString("shared_node_uuid"), true, null, userId, groupId,
						userRole, null, true));
			} else {
				result.append(indentation).append("<").append(resNode.getString("asm_type")).append(" ")
						.append(DomUtils.getXmlAttributeOutput("id", resNode.getString("node_uuid"))).append(" ");
				result.append(">");

				if (!resNode.getString("asm_type").equals("asmResource")) {
					final DocumentBuilderFactory newInstance = DocumentBuilderFactory.newInstance();
					DocumentBuilder builder;
					Document document = null;
					try {
						builder = newInstance.newDocumentBuilder();
						document = builder.newDocument();
					} catch (final ParserConfigurationException e) {
						e.printStackTrace();
					}

					metaxml = System.currentTimeMillis();

					if (resNode.getString("metadata_wad") != null && !resNode.getString("metadata_wad").equals("")) {
						final Element meta = document.createElement("metadata-wad");
						convertAttr(meta, resNode.getString("metadata_wad"));

						final TransformerFactory transFactory = TransformerFactory.newInstance();
						Transformer transformer;
						try {
							transformer = transFactory.newTransformer();
							final StringWriter buffer = new StringWriter();
							transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
							transformer.transform(new DOMSource(meta), new StreamResult(buffer));
							result.append(buffer);
						} catch (final Exception e) {
							e.printStackTrace();
						}
					} else {
						result.append("<metadata-wad/>");
					}

					if (resNode.getString("metadata_epm") != null && !resNode.getString("metadata_epm").equals("")) {
						result.append("<metadata-epm ").append(resNode.getString("metadata_epm")).append("/>");
					} else {
						result.append("<metadata-epm/>");
					}

					if (resNode.getString("metadata") != null && !resNode.getString("metadata").equals("")) {
						result.append("<metadata ").append(resNode.getString("metadata")).append("/>");
					} else {
						result.append("<metadata/>");
					}

					//
					result.append(DomUtils.getXmlElementOutput("code", resNode.getString("code")));
					result.append(DomUtils.getXmlElementOutput("label", resNode.getString("label")));
					result.append(DomUtils.getXmlElementOutput("description", resNode.getString("descr")));
					try {
						result.append(DomUtils.getXmlElementOutput("semanticTag", resNode.getString("semantictag")));
					} catch (final Exception ex) {
						result.append(DomUtils.getXmlElementOutput("semanticTag", ""));
					}
				}

				resource = System.currentTimeMillis();
				if (resNode.getString("res_res_node_uuid") != null) {
					if (resNode.getString("res_res_node_uuid").length() > 0) {
						result.append("<asmResource id='").append(resNode.getString("res_res_node_uuid"))
								.append("'  contextid='").append(nodeUuid).append("' xsi_type='nodeRes'>");
						resResource = getMysqlResource(c, resNode.getString("res_res_node_uuid"));
						if (resResource.next()) {
							result.append(resResource.getString("content"));
						}
						result.append("</asmResource>");
						resResource.close();
					}
				}
				if (resNode.getString("res_context_node_uuid") != null) {
					if (resNode.getString("res_context_node_uuid").length() > 0) {
						result.append("<asmResource id='").append(resNode.getString("res_context_node_uuid"))
								.append("' contextid='").append(nodeUuid).append("' xsi_type='context'>");
						resResource = getMysqlResource(c, resNode.getString("res_context_node_uuid"));
						if (resResource.next()) {
							result.append(resResource.getString("content"));
						}
						result.append("</asmResource>");
						resResource.close();
					}
				}
				if (resNode.getString("res_node_uuid") != null) {
					if (resNode.getString("res_node_uuid").length() > 0) {
						resResource = getMysqlResource(c, resNode.getString("res_node_uuid"));
						if (resResource.next()) {
							result.append("<asmResource id='").append(resNode.getString("res_node_uuid"))
									.append("' contextid='").append(nodeUuid).append("' xsi_type='")
									.append(resResource.getString("xsi_type")).append("'>");

							result.append(resResource.getString("content"));
							result.append("</asmResource>");
						}
						resResource.close();
					}
				}

				children = System.currentTimeMillis();
				if (withChildren || withChildrenOfXsiType != null) {
					String[] arrayChild;
					try {
						if (resNode.getString("node_children_uuid").length() > 0) {
							arrayChild = resNode.getString("node_children_uuid").split(",");
							for (final String s : arrayChild) {
								final ResultSet resChildNode = this.getMysqlNodeResultset(c, s);

								String tmpXsiType = "";
								try {
									resChildNode.next();
									tmpXsiType = resChildNode.getString("xsi_type");
								} catch (final Exception ex) {
									logger.error("Exception", ex);
								}
								if (withChildrenOfXsiType == null || withChildrenOfXsiType.equals(tmpXsiType)) {
									result.append(
											getNodeXmlOutput(c, s, true, null, userId, groupId, userRole, null, true));
								}

								resChildNode.close();
							}
						}
					} catch (final Exception ex) {
						// Pas de children
					}
				}

				result.append("</").append(resNode.getString("asm_type")).append(">");
			}
			end = System.currentTimeMillis();

			if (logger.isTraceEnabled()) {
				final long d_start = metaxml - start;
				final long d_metaxml = resource - metaxml;
				final long d_resource = children - resource;
				final long d_children = end - children;

				logger.trace("START: {}\nMETAXML: {}\nRESOURCE: {}\nCHILDREN: {}\n", d_start, d_metaxml, d_resource,
						d_children);
			}

		}

		resNode.close();

		return result;
	}

	@Override
	public Set<String[]> getNotificationUserList(Connection c, int userId, int groupId, String uuid) {
		PreparedStatement st;
		String sql;
		ResultSet res;
		final Set<String[]> retval = new HashSet<>();

		try {
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
			if (res.next()) {
				portfolio = res.getString(1);
				roles = res.getString(2);
			}
			res.close();
			st.close();

			if ("".equals(roles) || roles == null) {
				return retval;
			}

			final String[] roleArray = roles.split(",");
			final Set<String> roleSet = new HashSet<>(Arrays.asList(roleArray));

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
			while (res.next()) {
				final String label = res.getString(1);
				final String login = res.getString(2);
				final String lastname = res.getString(3);

				if (roleSet.contains(label)) {
					final String[] val = { login, lastname };
					retval.add(val);
				}
			}
			res.close();
			st.close();
		} catch (final Exception ex) {
			ex.printStackTrace();
		}
		return retval;
	}

	@Override
	public String[] getPorfolioGroup(int userId, String groupName) {
		return null;
	}

	@Override
	public Object getPortfolio(Connection c, MimeType outMimeType, String portfolioUuid, int userId, int groupId,
			String userrole, String resource, String files, int substid, Integer cutoff) throws Exception {
		System.currentTimeMillis();

		final String rootNodeUuid = getPortfolioRootNode(c, portfolioUuid);
		String header = "";
		String footer = "";
		NodeRight nodeRight = cred.getPortfolioRight(c, userId, groupId, portfolioUuid, Credential.READ, userrole);
		if (!nodeRight.read) {
			userId = cred.getPublicUid(c);
			//			NodeRight nodeRight = new NodeRight(false,false,false,false,false,false);
			/// Verifie les droits avec le compte publique (derniere chance)
			nodeRight = cred.getPublicRight(c, userId, 123, rootNodeUuid, "dummy");
			if (!nodeRight.read) {
				return DATABASE_FALSE;
			}
		}

		System.currentTimeMillis();

		if (outMimeType.getSubType().equals("xml")) {
			final int owner = cred.getOwner(c, userId, portfolioUuid);
			String isOwner = DB_NO;
			if (owner == userId) {
				isOwner = DB_YES;
			}

			final String headerXML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><portfolio code=\"0\" id=\"" +
					portfolioUuid +
					"\" owner=\"" +
					isOwner +
					"\"><version>4</version>";

			System.currentTimeMillis();

			final String data = getLinearXml(c, portfolioUuid, rootNodeUuid, userId, nodeRight.groupId,
					nodeRight.groupLabel, cutoff);

			System.currentTimeMillis();

			final StringWriter stw = new StringWriter();
			stw.append(headerXML).append(data).append("</portfolio>");

			System.currentTimeMillis();

			if (resource != null && files != null) {
				if (Boolean.parseBoolean(resource) && Boolean.parseBoolean(files)) {
					final String adressedufichier = userDir + "/tmp_getPortfolio_" + new Date() + ".xml";
					final String adresseduzip = userDir + "/tmp_getPortfolio_" + new Date() + ".zip";

					File file = null;
					PrintWriter ecrire;
					try {
						file = new File(adressedufichier);
						ecrire = new PrintWriter(new FileOutputStream(adressedufichier));
						ecrire.println(stw);
						ecrire.flush();
						ecrire.close();
						logger.info("fichier cree ");
					} catch (final IOException ioe) {
						logger.error("Erreur interceptée ", ioe);
					}

					try {
						final ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(adresseduzip));
						zip.setMethod(ZipOutputStream.DEFLATED);
						zip.setLevel(Deflater.BEST_COMPRESSION);
						final File dataDirectories = new File(file.getName());
						final FileInputStream fis = new FileInputStream(dataDirectories);
						final byte[] bytes = new byte[fis.available()];
						fis.read(bytes);

						final ZipEntry entry = new ZipEntry(file.getName());
						entry.setTime(dataDirectories.lastModified());
						zip.putNextEntry(entry);
						zip.write(bytes);
						zip.closeEntry();
						fis.close();
						//zipDirectory(dataDirectories, zip);
						zip.close();

						file.delete();

						return adresseduzip;
					} catch (final IOException io) {
						logger.error("Erreur interceptée ", io);
					}
				}
			}

			System.currentTimeMillis();
			return stw.toString();
		}
		if (outMimeType.getSubType().equals("json")) {
			header = "{\"portfolio\": { \"-xmlns:xsi\": \"http://www.w3.org/2001/XMLSchema-instance\",\"-schemaVersion\": \"1.0\",";
			footer = "}}";
		}

		return header + getNode(c, outMimeType, rootNodeUuid, true, userId, groupId, userrole, null, cutoff).toString()
				+ footer;
	}

	@Override
	public Object getPortfolioByCode(Connection c, MimeType mimeType, String portfolioCode, int userId, int groupId,
			String userRole, String resources, int substid) throws Exception {
		PreparedStatement st;
		String sql;
		ResultSet res = null;
		final String pid = this.getPortfolioUuidByPortfolioCode(c, portfolioCode);
		final boolean withResources = Boolean.parseBoolean(resources);
		;
		String result = "";

		if (withResources) {
			return this.getPortfolio(c, new MimeType("text/xml"), pid, userId, groupId, null, null, null, substid, null)
					.toString();
		}
		try {
			sql = "SELECT  bin2uuid(portfolio_id) AS portfolio_id,bin2uuid(root_node_uuid) as root_node_uuid, modif_user_id,modif_date, active, user_id " +
					"FROM portfolio " +
					"WHERE portfolio_id = uuid2bin(?) ";
			st = c.prepareStatement(sql);
			st.setString(1, pid);
			res = st.executeQuery();
		} catch (final Exception e) {
			logger.error("getPortfolioByCode error", e);
		}

		if (res.next()) {
			result += "<portfolio ";
			result += DomUtils.getXmlAttributeOutput("id", res.getString("portfolio_id")) + " ";
			result += DomUtils.getXmlAttributeOutput("root_node_id", res.getString("root_node_uuid")) + " ";
			result += ">";
			result += getNodeXmlOutput(c, res.getString("root_node_uuid"), false, "nodeRes", userId, groupId, userRole,
					null, false);
			result += "</portfolio>";
		}

		return result;
	}

	@Override
	public String getPortfolioByPortfolioGroup(Connection c, Integer portfolioGroupId, int userId) {
		String sql;
		PreparedStatement st = null;
		ResultSet res = null;

		final StringBuilder result = new StringBuilder();
		result.append("<group id=\"").append(portfolioGroupId).append("\">");
		//		String result = "<group id=\""+portfolioGroupId+"\">";
		try {
			sql = "SELECT bin2uuid(portfolio_id) AS portfolio_id FROM portfolio_group_members WHERE pg=?";
			st = c.prepareStatement(sql);
			st.setInt(1, portfolioGroupId);
			res = st.executeQuery();

			while (res.next()) {
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
		} catch (final SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				if (res != null) {
					res.close();
				}
				if (st != null) {
					st.close();
				}
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}

		result.append("</group>");
		//		result += "</group>";

		return result.toString();
	}

	@Override
	public int getPortfolioGroupIdFromLabel(Connection c, String groupLabel, int userId) {
		String sql;
		PreparedStatement st = null;
		ResultSet res = null;
		int groupid = -1;

		try {
			sql = "SELECT pg FROM portfolio_group pg " + "WHERE pg.label=?";
			st = c.prepareStatement(sql);
			st.setString(1, groupLabel);
			res = st.executeQuery();

			if (res.next()) {
				groupid = res.getInt("pg");
			}
		} catch (final SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				if (res != null) {
					res.close();
				}
				if (st != null) {
					st.close();
				}
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}

		return groupid;
	}

	@Override
	public String getPortfolioGroupList(Connection c, int userId) {
		String sql;
		PreparedStatement st = null;
		ResultSet res = null;

		final StringBuilder result = new StringBuilder();
		result.append("<groups>");
		try {
			sql = "SELECT * FROM portfolio_group";
			st = c.prepareStatement(sql);
			res = st.executeQuery();

			class TreeNode {
				String nodeContent;
				int nodeId;
				ArrayList<TreeNode> childs = new ArrayList<>();
			}
			class ProcessTree {
				public void reconstruct(StringBuilder data, TreeNode tree) {
					final String nodeData = tree.nodeContent;
					data.append(nodeData); // Add current node content
					for (int i = 0; i < tree.childs.size(); ++i) {
						final TreeNode child = tree.childs.get(i);
						reconstruct(data, child);
					}
					// Close node tag
					data.append("</group>");
				}
			}

			final ArrayList<TreeNode> trees = new ArrayList<>();
			final HashMap<Integer, TreeNode> resolve = new HashMap<>();

			final ProcessTree pf = new ProcessTree();

			final StringBuilder currNode = new StringBuilder();
			while (res.next()) {
				currNode.setLength(0);
				final String pgStr = res.getString("pg");
				final String type = res.getString("type");
				currNode.append("<group type='").append(type.toLowerCase()).append("' id=\"");
				currNode.append(pgStr);
				currNode.append("\"><label>");
				currNode.append(res.getString("label"));
				currNode.append("</label>");
				// group tag will be closed at reconstruction

				final TreeNode currTreeNode = new TreeNode();
				currTreeNode.nodeContent = currNode.toString();
				currTreeNode.nodeId = Integer.parseInt(pgStr);
				final int parentId = res.getInt("pg_parent");
				resolve.put(currTreeNode.nodeId, currTreeNode);
				if (parentId != 0) {
					final TreeNode parentTreeNode = resolve.get(parentId);
					parentTreeNode.childs.add(currTreeNode);
				} else // Top level groups
				{
					trees.add(currTreeNode);
				}
			}

			/// Go through top level parent and reconstruct each tree
			for (final TreeNode topNode : trees) {
				pf.reconstruct(result, topNode);
			}

		} catch (final SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				if (res != null) {
					res.close();
				}
				if (st != null) {
					st.close();
				}
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}

		result.append("</groups>");

		return result.toString();
	}

	@Override
	public String getPortfolioGroupListFromPortfolio(Connection c, String portfolioid, int userId) {
		String sql;
		PreparedStatement st = null;
		ResultSet res = null;

		final StringBuilder result = new StringBuilder();
		result.append("<portfolio id=\"").append(portfolioid).append("\">");
		//		String result = "<group id=\""+portfolioGroupId+"\">";
		try {
			sql = "SELECT pg.pg, label FROM portfolio_group_members pgm, portfolio_group pg " +
					"WHERE pg.pg=pgm.pg AND portfolio_id=uuid2bin(?)";
			st = c.prepareStatement(sql);
			st.setString(1, portfolioid);
			res = st.executeQuery();

			while (res.next()) {
				result.append("<group");
				result.append(" id=\"");
				result.append(res.getString("pg"));
				result.append("\">");
				result.append(res.getString("label"));
				//				result += ">";
				result.append("</group>");
				//				result += "</portfolio>";
			}
		} catch (final SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				if (res != null) {
					res.close();
				}
				if (st != null) {
					st.close();
				}
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}

		result.append("</portfolio>");
		//		result += "</group>";

		return result.toString();
	}

	/// Liste des RRG et utilisateurs d'un portfolio donne
	@Override
	public String getPortfolioInfo(Connection c, int userId, String portId) {
		final String status = "erreur";
		PreparedStatement st;
		ResultSet res;

		try {
			// group_right_info pid:grid -> group_info grid:gid -> group_user gid:userid
			final String sql = "SELECT gri.grid AS grid, gri.label AS label, gu.userid AS userid, c.display_firstname AS firstname, c.display_lastname AS lastname, c.email AS email " +
					"FROM credential c, group_right_info gri " +
					"LEFT JOIN group_info gi ON gri.grid=gi.grid " +
					"LEFT JOIN group_user gu ON gi.gid=gu.gid " +
					"WHERE gu.userid=c.userid AND gri.portfolio_id=uuid2bin(?)";
			st = c.prepareStatement(sql);
			st.setString(1, portId);
			res = st.executeQuery();

			/// Time to create data
			final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
			final DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
			final Document document = documentBuilder.newDocument();

			final Element root = document.createElement("portfolio");
			root.setAttribute("id", portId);
			document.appendChild(root);

			Element rrgUsers = null;

			long rrg = 0;
			while (res.next()) {
				if (rrg != res.getLong("grid")) {
					rrg = res.getLong("grid");
					final Element rrgNode = document.createElement("rrg");
					rrgNode.setAttribute("id", Long.toString(rrg));

					final Element rrgLabel = document.createElement("label");
					rrgLabel.setTextContent(res.getString("label"));

					rrgUsers = document.createElement("users");

					rrgNode.appendChild(rrgLabel);
					rrgNode.appendChild(rrgUsers);
					root.appendChild(rrgNode);
				}

				final long uid = res.getLong("userid");
				if (!res.wasNull()) {
					final Element user = document.createElement("user");
					user.setAttribute("id", Long.toString(uid));

					final String firstname = res.getString("firstname");
					final Element firstnameNode = document.createElement("display_firstname");
					firstnameNode.setTextContent(firstname);

					final String lastname = res.getString("lastname");
					final Element lastnameNode = document.createElement("display_lastname");
					lastnameNode.setTextContent(lastname);

					final String email = res.getString("email");
					final Element emailNode = document.createElement("email");
					emailNode.setTextContent(email);

					user.appendChild(firstnameNode);
					user.appendChild(lastnameNode);
					user.appendChild(emailNode);
					rrgUsers.appendChild(user);
				}
			}

			res.close();
			st.close();

			final StringWriter stw = new StringWriter();
			final Transformer serializer = TransformerFactory.newInstance().newTransformer();
			final DOMSource source = new DOMSource(document);
			final StreamResult stream = new StreamResult(stw);
			serializer.transform(source, stream);
			return stw.toString();
		} catch (final Exception e) {
			logger.error("Exception", e);
		}

		return status;
	}

	public String getPortfolioModelUuid(Connection c, String portfolioUuid) throws SQLException {
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

	public String getPortfolioRootNode(Connection c, String portfolioUuid) throws SQLException {
		PreparedStatement st;
		String sql;
		ResultSet res;

		sql = "SELECT bin2uuid(root_node_uuid) AS root_node_uuid FROM portfolio WHERE portfolio_id = uuid2bin(?) ";
		st = c.prepareStatement(sql);
		st.setString(1, portfolioUuid);
		res = st.executeQuery();
		String root_node = "";
		if (res.next()) {
			root_node = res.getString("root_node_uuid");
		}

		st.close();
		res.close();

		return root_node;
	}

	@Override
	public Object getPortfolios(Connection c, MimeType outMimeType, int userId, int groupId, String userRole,
			Boolean portfolioActive, int substid, Boolean portfolioProject, String projectId, Boolean countOnly,
			String search) throws SQLException {
		PreparedStatement st;
		ResultSet res;
		Integer count = null;
		boolean codeFilterProjectId = false;
		boolean codeFilterSearch = false;
		String sql;
		String sql_count;
		String sql_suffix;
		final StringBuilder out = new StringBuilder();
		final boolean all = projectId != null && !projectId.equalsIgnoreCase("all");
		if (cred.isAdmin(c, userId)) {
			if (!dbserveur.equals("oracle")) {
				sql = "SELECT bin2uuid(p.root_node_uuid) as root_node_uuid, p.modif_date, bin2uuid(n.node_uuid) as node_uuid, bin2uuid(n.node_parent_uuid) as node_parent_uuid, n.node_children_uuid as node_children_uuid, n.node_order, n.metadata, n.metadata_wad, n.metadata_epm, bin2uuid(n.res_node_uuid) as res_node_uuid,  bin2uuid(n.res_res_node_uuid) as res_res_node_uuid, bin2uuid(n.res_context_node_uuid) as res_context_node_uuid, n.shared_res, n.shared_node, n.shared_node_res, bin2uuid(n.shared_res_uuid) AS shared_res_uuid, bin2uuid(n.shared_node_uuid) AS shared_node_uuid, bin2uuid(n.shared_node_res_uuid) AS shared_node_res_uuid, n.asm_type, n.xsi_type, n.semtag, n.semantictag, n.label, n.code, n.descr, n.format, n.modif_user_id, n.modif_date, bin2uuid(n.portfolio_id) as portfolio_id, r1.content, r1.xsi_type, r2.content, r2.xsi_type, r3.content, r3.xsi_type ";
			} else {
				sql = "SELECT bin2uuid(p.root_node_uuid) as root_node_uuid, p.modif_date as portfolio_modif_date, bin2uuid(n.node_uuid) as node_uuid, bin2uuid(n.node_parent_uuid) as node_parent_uuid, n.node_children_uuid as node_children_uuid, n.node_order, n.metadata, n.metadata_wad, n.metadata_epm, bin2uuid(n.res_node_uuid) as res_node_uuid,  bin2uuid(n.res_res_node_uuid) as res_res_node_uuid, bin2uuid(n.res_context_node_uuid) as res_context_node_uuid, n.shared_res, n.shared_node, n.shared_node_res, bin2uuid(n.shared_res_uuid) AS shared_res_uuid, bin2uuid(n.shared_node_uuid) AS shared_node_uuid, bin2uuid(n.shared_node_res_uuid) AS shared_node_res_uuid, n.asm_type, n.xsi_type, n.semtag, n.semantictag, n.label, n.code, n.descr, n.format, n.modif_user_id, n.modif_date, bin2uuid(n.portfolio_id) as portfolio_id, r1.content as r1_content, r1.xsi_type as r1_xsi_type, r2.content as r2_content, r2.xsi_type as r2_xsi_type, r3.content as r3_content, r3.xsi_type as r3_xsi_type ";
			}
			sql_count = " SELECT count(*) AS c ";
			sql_suffix = " FROM portfolio p, node n " +
					"LEFT JOIN resource_table r1 ON n.res_res_node_uuid=r1.node_uuid " +
					"LEFT JOIN resource_table r2 ON n.res_context_node_uuid=r2.node_uuid " +
					"LEFT JOIN resource_table r3 ON n.res_node_uuid=r3.node_uuid " +
					"WHERE p.root_node_uuid=n.node_uuid ";

			sql += sql_suffix;
			sql_count += sql_suffix;

			//projects
			if (portfolioProject != null) {
				if (!portfolioProject || all) {
					return getPortfoliosNoProject(c, outMimeType, 0, groupId, userRole, sql, countOnly, search,
							portfolioActive);
					// Not efficient in MySQL, disabled
					//sql += "AND SUBSTRING_INDEX(n.code, '.', 1) NOT IN (SELECT n.code  FROM portfolio p, node n LEFT JOIN resource_table r1 ON n.res_res_node_uuid=r1.node_uuid LEFT JOIN resource_table r2 ON n.res_context_node_uuid=r2.node_uuid LEFT JOIN resource_table r3 ON n.res_node_uuid=r3.node_uuid WHERE p.root_node_uuid=n.node_uuid AND n.semantictag LIKE '%karuta-project%' ) ";
					//sql_count += "AND SUBSTRING_INDEX(n.code, '.', 1) NOT IN (SELECT n.code  FROM portfolio p, node n LEFT JOIN resource_table r1 ON n.res_res_node_uuid=r1.node_uuid LEFT JOIN resource_table r2 ON n.res_context_node_uuid=r2.node_uuid LEFT JOIN resource_table r3 ON n.res_node_uuid=r3.node_uuid WHERE p.root_node_uuid=n.node_uuid AND n.semantictag LIKE '%karuta-project%' ) ";
				}
				sql += "AND n.semantictag LIKE '%karuta-project%' ";
				sql_count += "AND n.semantictag LIKE '%karuta-project%' ";
			} else if (projectId != null) {
				if (all) {
					sql += "AND n.code LIKE ? ";
					sql_count += "AND n.code LIKE ? ";
					codeFilterProjectId = true;
				}
			} else if (search != null) {
				if (search.length() > 0) {
					sql += "AND n.code LIKE ? ";
					sql_count += "AND n.code LIKE ? ";
					codeFilterSearch = true;
				}
			}
			//active
			if (portfolioActive) {
				sql += "AND p.active=1 ";
				sql_count += "AND p.active=1 ";
			} else {
				sql += "AND p.active=0 ";
				sql_count += "AND p.active=0 ";
			}
			if (!dbserveur.equals("oracle")) {
				sql += "ORDER BY r1.content;";
			}

			st = c.prepareStatement(sql_count);
			if (codeFilterProjectId) {
				st.setString(1, projectId + "%");
			} else if (codeFilterSearch) {
				st.setString(1, "%" + search + "%");
			}
			res = st.executeQuery();
			while (res.next()) {
				count = res.getInt("c");
			}

			if (countOnly) {
				if (outMimeType.getSubType().equals("xml")) {
					out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><portfolios count=\"").append(count)
							.append("\" />");
				} else if (outMimeType.getSubType().equals("json")) {
				}

			} else {
				st = c.prepareStatement(sql);
				if (codeFilterProjectId) {
					st.setString(1, projectId + "%");
				} else if (codeFilterSearch) {
					st.setString(1, "%" + search + "%");
				}

				res = st.executeQuery();
			}

		} else {
			if (!dbserveur.equals("oracle")) {
				sql = "SELECT DISTINCT bin2uuid(p.root_node_uuid) as root_node_uuid, p.modif_date, bin2uuid(n.node_uuid) as node_uuid, bin2uuid(n.node_parent_uuid) as node_parent_uuid, n.node_children_uuid as node_children_uuid, n.node_order, n.metadata, n.metadata_wad, n.metadata_epm, bin2uuid(n.res_node_uuid) as res_node_uuid,  bin2uuid(n.res_res_node_uuid) as res_res_node_uuid, bin2uuid(n.res_context_node_uuid) as res_context_node_uuid, n.shared_res, n.shared_node, n.shared_node_res, bin2uuid(n.shared_res_uuid) AS shared_res_uuid, bin2uuid(n.shared_node_uuid) AS shared_node_uuid, bin2uuid(n.shared_node_res_uuid) AS shared_node_res_uuid, n.asm_type, n.xsi_type, n.semtag, n.semantictag, n.label, n.code, n.descr, n.format, n.modif_user_id, n.modif_date, bin2uuid(n.portfolio_id) as portfolio_id, r1.content, r1.xsi_type, r2.content, r2.xsi_type, r3.content, r3.xsi_type ";
			} else {
				//				sql = "SELECT DISTINCT bin2uuid(p.root_node_uuid) as root_node_uuid, p.modif_date as portfolio_modif_date, bin2uuid(n.node_uuid) as node_uuid, bin2uuid(n.node_parent_uuid) as node_parent_uuid, n.node_children_uuid as node_children_uuid, n.node_order, n.metadata, n.metadata_wad, n.metadata_epm, bin2uuid(n.res_node_uuid) as res_node_uuid,  bin2uuid(n.res_res_node_uuid) as res_res_node_uuid, bin2uuid(n.res_context_node_uuid) as res_context_node_uuid, n.shared_res, n.shared_node, n.shared_node_res, bin2uuid(n.shared_res_uuid) AS shared_res_uuid, bin2uuid(n.shared_node_uuid) AS shared_node_uuid, bin2uuid(n.shared_node_res_uuid) AS shared_node_res_uuid, n.asm_type, n.xsi_type, n.semtag, n.semantictag, n.label, n.code, n.descr, n.format, n.modif_user_id, n.modif_date, bin2uuid(n.portfolio_id) as portfolio_id, r1.content as r1_content, r1.xsi_type as r1_xsi_type, r2.content as r2_content, r2.xsi_type as r2_xsi_type, r3.content as r3_content, r3.xsi_type as r3_xsi_type " ;
				sql = "SELECT bin2uuid(p.root_node_uuid) as root_node_uuid, p.modif_date as portfolio_modif_date, bin2uuid(n.node_uuid) as node_uuid, bin2uuid(n.node_parent_uuid) as node_parent_uuid, n.node_children_uuid as node_children_uuid, n.node_order, n.metadata, n.metadata_wad, n.metadata_epm, bin2uuid(n.res_node_uuid) as res_node_uuid,  bin2uuid(n.res_res_node_uuid) as res_res_node_uuid, bin2uuid(n.res_context_node_uuid) as res_context_node_uuid, n.shared_res, n.shared_node, n.shared_node_res, bin2uuid(n.shared_res_uuid) AS shared_res_uuid, bin2uuid(n.shared_node_uuid) AS shared_node_uuid, bin2uuid(n.shared_node_res_uuid) AS shared_node_res_uuid, n.asm_type, n.xsi_type, n.semtag, n.semantictag, n.label, n.code, n.descr, n.format, n.modif_user_id, n.modif_date, bin2uuid(n.portfolio_id) as portfolio_id, r1.content as r1_content, r1.xsi_type as r1_xsi_type, r2.content as r2_content, r2.xsi_type as r2_xsi_type, r3.content as r3_content, r3.xsi_type as r3_xsi_type ";

			}
			sql_count = " SELECT count(DISTINCT p.root_node_uuid) AS c ";
			sql_suffix = " FROM portfolio p, group_right_info gri, group_info gi, group_user gu, node n " +
					"LEFT JOIN resource_table r1 ON n.res_res_node_uuid=r1.node_uuid " +
					"LEFT JOIN resource_table r2 ON n.res_context_node_uuid=r2.node_uuid " +
					"LEFT JOIN resource_table r3 ON n.res_node_uuid=r3.node_uuid " +
					"WHERE p.portfolio_id=gri.portfolio_id AND gri.grid=gi.grid AND gi.gid=gu.gid AND p.root_node_uuid=n.node_uuid AND " +
					"(gu.userid=? OR p.modif_user_id=?) ";

			sql += sql_suffix;
			sql_count += sql_suffix;

			//projects
			if (portfolioProject != null) {
				if (projectId == null) {
					projectId = "";
				}
				if (!portfolioProject || projectId.equalsIgnoreCase("all")) {
					return getPortfoliosNoProject(c, outMimeType, userId, groupId, userRole, sql, countOnly, search,
							portfolioActive);
					// Not efficient in MySQL, disabled
					//sql += "AND SUBSTRING_INDEX(n.code, '.', 1) NOT IN (SELECT n.code  FROM portfolio p, node n LEFT JOIN resource_table r1 ON n.res_res_node_uuid=r1.node_uuid LEFT JOIN resource_table r2 ON n.res_context_node_uuid=r2.node_uuid LEFT JOIN resource_table r3 ON n.res_node_uuid=r3.node_uuid WHERE p.root_node_uuid=n.node_uuid AND n.semantictag LIKE '%karuta-project%' ) ";
					//sql_count += "AND SUBSTRING_INDEX(n.code, '.', 1) NOT IN (SELECT n.code  FROM portfolio p, node n LEFT JOIN resource_table r1 ON n.res_res_node_uuid=r1.node_uuid LEFT JOIN resource_table r2 ON n.res_context_node_uuid=r2.node_uuid LEFT JOIN resource_table r3 ON n.res_node_uuid=r3.node_uuid WHERE p.root_node_uuid=n.node_uuid AND n.semantictag LIKE '%karuta-project%' ) ";
				}
				sql += "AND n.semantictag LIKE '%karuta-project%' ";
				sql_count += "AND n.semantictag LIKE '%karuta-project%' ";
			} else if (projectId != null) {
				if (all) {
					sql += "AND n.code LIKE ? ";
					sql_count += "AND n.code LIKE ? ";
					codeFilterProjectId = true;
				}
			} else if (search != null) {
				if (search.length() > 0) {
					sql += "AND n.code LIKE ? ";
					sql_count += "AND n.code LIKE ? ";
					codeFilterSearch = true;
				}
			}
			//active
			if (portfolioActive) {
				sql += "AND p.active=1 ";
				sql_count += "AND p.active=1 ";
			} else {
				sql += "AND p.active=0 ";
				sql_count += "AND p.active=0 ";
			}
			if (!dbserveur.equals("oracle")) {
				sql += "ORDER BY r1.content";
			}

			st = c.prepareStatement(sql_count);
			st.setInt(1, userId);
			st.setInt(2, userId);
			if (codeFilterProjectId) {
				st.setString(3, projectId + "%");
			} else if (codeFilterSearch) {
				st.setString(3, "%" + search + "%");
			}
			res = st.executeQuery();
			while (res.next()) {
				count = res.getInt("c");
			}

			if (countOnly) {
				if (outMimeType.getSubType().equals("xml")) {
					out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><portfolios count=\"").append(count)
							.append("\" />");
				} else if (outMimeType.getSubType().equals("json")) {
				}

			} else {
				st = c.prepareStatement(sql);
				st.setInt(1, userId);
				st.setInt(2, userId);
				if (codeFilterProjectId) {
					st.setString(3, projectId + "%");
				} else if (codeFilterSearch) {
					st.setString(3, "%" + search + "%");
				}

				res = st.executeQuery();
			}
		}
		//TODO si portfolioProject=false parcourir une premiere fois pour extraire les projets, puis re-parcourir pour

		if (!countOnly) {
			if (outMimeType.getSubType().equals("xml")) {
				out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><portfolios count=\"").append(count)
						.append("\" >");
				while (res.next()) {
					String isOwner = DB_NO;
					final String ownerId = res.getString("modif_user_id");
					if (Integer.parseInt(ownerId) == userId) {
						isOwner = DB_YES;
					}

					out.append("<portfolio id=\"").append(res.getString("portfolio_id"));
					out.append("\" root_node_id=\"").append(res.getString("root_node_uuid"));
					out.append("\" owner=\"").append(isOwner);
					out.append("\" ownerid=\"").append(ownerId);
					if (dbserveur.equals("oracle")) {
						out.append("\" modified=\"").append(res.getString("portfolio_modif_date")).append("\">");
					} else {
						out.append("\" modified=\"").append(res.getString("p.modif_date")).append("\">");
					}
					final String nodeUuid = res.getString("root_node_uuid");

					if (res.getString("shared_node_uuid") != null) // FIXME, add to query
					{
						out.append(getNodeXmlOutput(c, res.getString("shared_node_uuid"), true, null, userId, groupId,
								userRole, null, true));
					} else {
						final String nodetype = res.getString("asm_type");
						out.append("<").append(nodetype).append(" id=\"").append(res.getString("node_uuid"))
								.append("\">");

						if (!"asmResource".equals(nodetype)) {
							final String metawad = res.getString("metadata_wad");
							if (metawad != null && !"".equals(metawad)) {
								out.append("<metadata-wad ").append(metawad).append("/>");
							} else {
								out.append("<metadata-wad/>");
							}

							final String metaepm = res.getString("metadata_epm");
							if (metaepm != null && !"".equals(metaepm)) {
								out.append("<metadata-epm ").append(metaepm).append("/>");
							} else {
								out.append("<metadata-epm/>");
							}

							final String meta = res.getString("metadata");
							if (meta != null && !"".equals(meta)) {
								out.append("<metadata ").append(meta).append("/>");
							} else {
								out.append("<metadata/>");
							}

							final String code = res.getString("code");
							if (meta != null && !"".equals(meta)) {
								out.append("<code>").append(code).append("</code>");
							} else {
								out.append("<code/>");
							}

							final String label = res.getString("label");
							if (label != null && !"".equals(label)) {
								out.append("<label>").append(label).append("</label>");
							} else {
								out.append("<label/>");
							}

							final String descr = res.getString("descr");
							if (descr != null && !"".equals(descr)) {
								out.append("<description>").append(descr).append("</description>");
							} else {
								out.append("<description/>");
							}

							final String semantic = res.getString("semantictag");
							if (semantic != null && !"".equals(semantic)) {
								out.append("<semanticTag>").append(semantic).append("</semanticTag>");
							} else {
								out.append("<semanticTag/>");
							}
						}

						final String resresuuid = res.getString("res_res_node_uuid");
						if (resresuuid != null && !"".equals(resresuuid)) {
							String xsitype;
							if (dbserveur.equals("oracle")) {
								xsitype = res.getString("r1_xsi_type");
							} else {
								xsitype = res.getString("r1.xsi_type");
							}
							out.append("<asmResource id='").append(resresuuid).append("' contextid='").append(nodeUuid)
									.append("' xsi_type='").append(xsitype).append("'>");
							String resrescont;
							if (dbserveur.equals("oracle")) {
								resrescont = res.getString("r1_content");
							} else {
								resrescont = res.getString("r1.content");
							}
							if (resrescont != null && !"".equals(resrescont)) {
								out.append(resrescont);
							}
							out.append("</asmResource>");
						}

						final String rescontuuid = res.getString("res_context_node_uuid");
						if (rescontuuid != null && !"".equals(rescontuuid)) {
							String xsitype;
							if (dbserveur.equals("oracle")) {
								xsitype = res.getString("r2_xsi_type");
							} else {
								xsitype = res.getString("r2.xsi_type");
							}
							out.append("<asmResource id='").append(rescontuuid).append("' contextid='").append(nodeUuid)
									.append("' xsi_type='").append(xsitype).append("'>");
							String resrescont;
							if (dbserveur.equals("oracle")) {
								resrescont = res.getString("r2_content");
							} else {
								resrescont = res.getString("r2.content");
							}
							if (resrescont != null && !"".equals(resrescont)) {
								out.append(resrescont);
							}
							out.append("</asmResource>");
						}

						final String resnodeuuid = res.getString("res_node_uuid");
						if (resnodeuuid != null && !"".equals(resnodeuuid)) {
							String xsitype;
							if (dbserveur.equals("oracle")) {
								xsitype = res.getString("r3_xsi_type");
							} else {
								xsitype = res.getString("r3.xsi_type");
							}
							out.append("<asmResource id='").append(resnodeuuid).append("' contextid='").append(nodeUuid)
									.append("' xsi_type='").append(xsitype).append("'>");
							String resrescont;
							if (dbserveur.equals("oracle")) {
								resrescont = res.getString("r3_content");
							} else {
								resrescont = res.getString("r3.content");
							}
							if (resrescont != null && !"".equals(resrescont)) {
								out.append(resrescont);
							}
							out.append("</asmResource>");
						}
						out.append("</").append(nodetype).append(">");
						out.append("</portfolio>");
					}
				}
				out.append("</portfolios>");
			} else if (outMimeType.getSubType().equals("json")) {

				out.append("{ \"portfolios\": { \"portfolio\": [");
				boolean firstPass = false;
				while (res.next()) {
					if (firstPass) {
						out.append(",");
					}
					out.append("{ ");
					out.append(DomUtils.getJsonAttributeOutput("id", res.getString("portfolio_id"))).append(", ");
					out.append(DomUtils.getJsonAttributeOutput("root_node_id", res.getString("root_node_uuid")))
							.append(", ");
					out.append(getNodeJsonOutput(c, res.getString("root_node_uuid"), false, "nodeRes", userId, groupId,
							userRole, null, false));
					out.append("} ");
					firstPass = true;
				}
				out.append("] } }");
			}
		}
		res.close();
		st.close();

		return out.toString();
	}

	@Override
	public String getPortfolioShared(Connection c, int user, int userId) throws SQLException {
		String sql;
		PreparedStatement st;
		ResultSet res;
		final StringBuilder out = new StringBuilder();

		try {
			sql = "SELECT gu.gid, bin2uuid(gri.portfolio_id) " +
					"FROM group_user gu, group_info gi, group_right_info gri " +
					"WHERE gu.gid=gi.gid AND gi.grid=gri.grid " +
					"AND gu.userid=?";
			st = c.prepareStatement(sql);
			st.setInt(1, userId);
			res = st.executeQuery();

			out.append("<portfolios>");
			while (res.next()) {
				final int gid = res.getInt(1);
				final String portfolio = res.getString(2);
				out.append("<portfolio gid='").append(gid).append("' portfolio='").append(portfolio).append("'/>");
			}
			out.append("</portfolios>");
		} catch (final Exception e) {
			e.printStackTrace();
		}

		return out.toString();
	}

	public Object getPortfoliosNoProject(Connection c, MimeType outMimeType, int userId, int groupId, String userRole,
			String sql, Boolean countOnly, String search, Boolean portfolioActive) throws SQLException {
		/// orz
		PreparedStatement st;
		ResultSet res;
		int count = 0;
		final StringBuilder out = new StringBuilder();
		final ArrayList<String> codePortfolios = new ArrayList<>();
		final ArrayList<String> codePortfoliosProjects = new ArrayList<>();
		final ArrayList<String> codePortfoliosNonProjects = new ArrayList<>();

		if (userId > 0) {
			st = c.prepareStatement(sql);
			st.setInt(1, userId);
			st.setInt(2, userId);

		} else {
			sql += " AND p.active=?";

			st = c.prepareStatement(sql);
			st.setBoolean(1, portfolioActive);

		}
		res = st.executeQuery();

		while (res.next()) {
			final String code = res.getString("code");
			final String semanticTag = res.getString("semantictag");
			codePortfolios.add(code);
			if (semanticTag != null) {
				if (semanticTag.contains("karuta-project")) {
					codePortfoliosProjects.add(code);
					//String[] tmp = code.split(".");
					//codePortfoliosProjects.add(tmp[0]);
				}
			}
		}

		for (final String code : codePortfolios) {
			if (code.contains(".")) {
				final String[] tmp = code.split("\\.");
				final String tmpCodeProjet = tmp[0];
				if (!codePortfoliosProjects.contains(tmpCodeProjet)) {
					count++;
					codePortfoliosNonProjects.add(code);
				}
			} else if (!codePortfoliosProjects.contains(code)) {
				count++;
				codePortfoliosNonProjects.add(code);
			}
		}
		//TODO

		if (countOnly) {
			if (outMimeType.getSubType().equals("xml")) {
				out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><portfolios count=\"").append(count)
						.append("\" />");
			} else if (outMimeType.getSubType().equals("json")) {
			}

		} else {
			res = st.executeQuery();
			if (outMimeType.getSubType().equals("xml")) {
				out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><portfolios count=\"").append(count)
						.append("\" >");
				while (res.next()) {
					if (codePortfoliosNonProjects.contains(res.getString("code"))) {
						String isOwner = DB_NO;
						final String ownerId = res.getString("modif_user_id");
						if (Integer.parseInt(ownerId) == userId) {
							isOwner = DB_YES;
						}

						out.append("<portfolio id=\"").append(res.getString("portfolio_id"));
						out.append("\" root_node_id=\"").append(res.getString("root_node_uuid"));
						out.append("\" owner=\"").append(isOwner);
						out.append("\" ownerid=\"").append(ownerId);
						out.append("\" modified=\"").append(res.getString("p.modif_date")).append("\">");

						final String nodeUuid = res.getString("root_node_uuid");

						if (res.getString("shared_node_uuid") != null) // FIXME, add to query
						{
							out.append(getNodeXmlOutput(c, res.getString("shared_node_uuid"), true, null, userId,
									groupId, userRole, null, true));
						} else {
							final String nodetype = res.getString("asm_type");
							out.append("<").append(nodetype).append(" id=\"").append(res.getString("node_uuid"))
									.append("\">");

							if (!"asmResource".equals(nodetype)) {
								final String metawad = res.getString("metadata_wad");
								if (metawad != null && !"".equals(metawad)) {
									out.append("<metadata-wad ").append(metawad).append("/>");
								} else {
									out.append("<metadata-wad/>");
								}

								final String metaepm = res.getString("metadata_epm");
								if (metaepm != null && !"".equals(metaepm)) {
									out.append("<metadata-epm ").append(metaepm).append("/>");
								} else {
									out.append("<metadata-epm/>");
								}

								final String meta = res.getString("metadata");
								if (meta != null && !"".equals(meta)) {
									out.append("<metadata ").append(meta).append("/>");
								} else {
									out.append("<metadata/>");
								}

								final String code = res.getString("code");
								if (meta != null && !"".equals(meta)) {
									out.append("<code>").append(code).append("</code>");
								} else {
									out.append("<code/>");
								}

								final String label = res.getString("label");
								if (label != null && !"".equals(label)) {
									out.append("<label>").append(label).append("</label>");
								} else {
									out.append("<label/>");
								}

								final String descr = res.getString("descr");
								if (descr != null && !"".equals(descr)) {
									out.append("<description>").append(descr).append("</description>");
								} else {
									out.append("<description/>");
								}

								final String semantic = res.getString("semantictag");
								if (semantic != null && !"".equals(semantic)) {
									out.append("<semanticTag>").append(semantic).append("</semanticTag>");
								} else {
									out.append("<semanticTag/>");
								}
							}

							final String resresuuid = res.getString("res_res_node_uuid");
							if (resresuuid != null && !"".equals(resresuuid)) {
								final String xsitype = res.getString("r1.xsi_type");
								out.append("<asmResource id='").append(resresuuid).append("' contextid='")
										.append(nodeUuid).append("' xsi_type='").append(xsitype).append("'>");
								final String resrescont = res.getString("r1.content");
								if (resrescont != null && !"".equals(resrescont)) {
									out.append(resrescont);
								}
								out.append("</asmResource>");
							}

							final String rescontuuid = res.getString("res_context_node_uuid");
							if (rescontuuid != null && !"".equals(rescontuuid)) {
								final String xsitype = res.getString("r2.xsi_type");
								out.append("<asmResource id='").append(rescontuuid).append("' contextid='")
										.append(nodeUuid).append("' xsi_type='").append(xsitype).append("'>");
								final String resrescont = res.getString("r2.content");
								if (resrescont != null && !"".equals(resrescont)) {
									out.append(resrescont);
								}
								out.append("</asmResource>");
							}

							final String resnodeuuid = res.getString("res_node_uuid");
							if (resnodeuuid != null && !"".equals(resnodeuuid)) {
								final String xsitype = res.getString("r3.xsi_type");
								out.append("<asmResource id='").append(resnodeuuid).append("' contextid='")
										.append(nodeUuid).append("' xsi_type='").append(xsitype).append("'>");
								final String resrescont = res.getString("r3.content");
								if (resrescont != null && !"".equals(resrescont)) {
									out.append(resrescont);
								}
								out.append("</asmResource>");
							}
							out.append("</").append(nodetype).append(">");
							out.append("</portfolio>");
						}

					}
				}
				out.append("</portfolios>");
			} else if (outMimeType.getSubType().equals("json")) {
				final StringBuilder result = new StringBuilder("{ \"portfolios\": { \"portfolio\": [");
				boolean firstPass = false;
				while (res.next()) {
					if (codePortfoliosNonProjects.contains(res.getString("code"))) {
						if (firstPass) {
							result.append(",");
						}
						result.append("{ ");
						result.append(DomUtils.getJsonAttributeOutput("id", res.getString("portfolio_id")))
								.append(", ");
						result.append(DomUtils.getJsonAttributeOutput("root_node_id", res.getString("root_node_uuid")))
								.append(", ");
						result.append(getNodeJsonOutput(c, res.getString("root_node_uuid"), false, "nodeRes", userId,
								groupId, userRole, null, false));
						result.append("} ");
						firstPass = true;
					}
				}
				result.append("] } }");
			}
		}

		res.close();
		st.close();

		return out.toString();

	}

	@Override
	public ArrayList<Pair<String, String>> getPortfolioUniqueFile(Connection c, String portfolioUuid, int userId)
			throws Exception {
		PreparedStatement st = null;
		ResultSet rs = null;
		String sql;
		final ArrayList<Pair<String, String>> retval = new ArrayList<Pair<String, String>>();

		try {
			/// Small temp table
			sql = "CREATE TEMPORARY TABLE t_fileid(" +
					"resuuid char(36), " +
					"fileid VARCHAR(64) NOT NULL) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;";
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			/// Insert added files in portfolio
			sql = "INSERT INTO t_fileid \n" +
					"SELECT bin2uuid(n.node_uuid), " +
					"CONCAT(\"%\",REGEXP_SUBSTR(content, \"fileid[^>]*>[^<]+\"),\"%\") " +
					"FROM node n, resource_table rt " +
					"WHERE n.portfolio_id=uuid2bin(?) AND " +
					"n.res_node_uuid=rt.node_uuid AND " +
					"content LIKE \"%fileid%\";";
			st = c.prepareStatement(sql);
			st.setString(1, portfolioUuid);
			st.executeUpdate();
			st.close();

			/// Clear empty values
			sql = "DELETE FROM t_fileid WHERE fileid=\"%%\";";
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			/// Check referenced file count
			sql = "SELECT COUNT(*) AS fcount, t.resuuid, t.fileid " +
					"FROM resource_table rt, t_fileid t " +
					"WHERE rt.content LIKE t.fileid " +
					"GROUP BY t.fileid ORDER BY fcount;";
			st = c.prepareStatement(sql);
			rs = st.executeQuery();

			while (rs.next()) {
				final int count = rs.getInt(1);
				/// Only keep value where count is 1
				if (count > 1) {
					break; // Values are ordered
				}

				final String nodeuuid = rs.getString(2);
				final String rawfileid = rs.getString(3);

				// Fetch lang and fileid
				final Matcher info = FILEID_PAT.matcher(rawfileid);
				String lang = "";
				if (info.find()) {
					lang = info.group(1);

					// nodeid and lang
					final Pair<String, String> data = Pair.of(nodeuuid, lang);
					retval.add(data);
				}

			}
			st.close();
		} catch (final Exception e) {
			e.printStackTrace();
		} finally {
			sql = "DROP TEMPORARY TABLE IF EXISTS t_fileid";
			st = c.prepareStatement(sql);
			st.execute();
			st.close();
		}

		return retval;
	}

	public int getPortfolioUserId(Connection c, String portfolioUuid) throws SQLException {
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

	@Override
	public String getPortfolioUuidByNodeUuid(Connection c, String nodeUuid) throws Exception {
		PreparedStatement st;
		String sql;
		ResultSet res;
		String result = null;

		sql = "SELECT bin2uuid(portfolio_id) AS portfolio_id FROM node WHERE node_uuid = uuid2bin(?) ";
		st = c.prepareStatement(sql);
		st.setString(1, nodeUuid);
		res = st.executeQuery();
		res.next();
		try {
			result = res.getString("portfolio_id");
		} catch (final Exception ex) {
			logger.error("Exception", ex);
		}
		return result;
	}

	public String getPortfolioUuidByPortfolioCode(Connection c, String portfolioCode) {
		PreparedStatement st;
		String sql;
		ResultSet res;

		try {
			sql = "SELECT bin2uuid(p.portfolio_id) AS portfolio_id " +
					"FROM portfolio p, node n " +
					"WHERE p.active=1 AND p.portfolio_id=n.portfolio_id AND p.root_node_uuid=n.node_uuid AND n.code = ?";
			st = c.prepareStatement(sql);
			st.setString(1, portfolioCode);
			res = st.executeQuery();

			if (res.next()) {
				return res.getString("portfolio_id");
			}
			return "";
		} catch (final Exception ex) {
			ex.printStackTrace();
			return null;
		}
	}

	@Override
	public Object getPortfolioZip(MimeType mimeType, String portfolioUuid, int userId, int groupId, String label,
			Boolean resource, Boolean files) throws Exception {
		return null;
	}

	@Override
	public String getResNode(Connection c, String contextUuid, int userId, int groupId) throws Exception {
		PreparedStatement st = null;
		ResultSet res = null;
		String status = "";

		try {
			final String sql = "SELECT content FROM resource_table " +
					"WHERE node_uuid=(SELECT res_node_uuid FROM node WHERE node_uuid=uuid2bin(?))";
			st = c.prepareStatement(sql);
			st.setString(1, contextUuid);

			res = st.executeQuery();

			if (res.next()) {
				status = res.getString("content");
			}
		} catch (final Exception e) {
			e.printStackTrace();
		} finally {
			if (st != null) {
				try {
					st.close();
				} catch (final SQLException e) {
					logger.error("SQLException", e);
				}
			}
			if (res != null) {
				try {
					res.close();
				} catch (final SQLException e) {
					logger.error("SQLException", e);
				}
			}
		}

		return status;
	}

	@Override
	public Object getResource(Connection c, MimeType outMimeType, String nodeParentUuid, int userId, int groupId)
			throws Exception {
		final String[] data = getMysqlResourceByNodeParentUuid(c, nodeParentUuid);

		if (!cred.hasNodeRight(c, userId, groupId, nodeParentUuid, Credential.READ)) {
			throw new RestWebApplicationException(Status.FORBIDDEN, " No READ credential ");
		}

		return "<asmResource id=\"" +
				data[0] +
				"\" contextid=\"" +
				nodeParentUuid +
				"\"  >" +
				data[1] +
				"</asmResource>";
	}

	@Override
	public String getResourceNodeUuidByParentNodeUuid(Connection c, String nodeParentUuid) {
		PreparedStatement st;
		String sql;
		ResultSet res;

		try {
			sql = "SELECT bin2uuid(res_node_uuid)AS res_node_uuid FROM node WHERE node_uuid = uuid2bin(?) ";
			st = c.prepareStatement(sql);
			st.setString(1, nodeParentUuid);
			res = st.executeQuery();
			res.next();
			return res.getString("res_node_uuid");
		} catch (final Exception ex) {
			ex.printStackTrace();
			return null;
		}
	}

	@Override
	public Object getResources(Connection c, MimeType outMimeType, String portfolioUuid, int userId, int groupId)
			throws Exception {
		final java.sql.ResultSet res = getMysqlResources(c, portfolioUuid);
		final StringBuilder returnValue = new StringBuilder();
		if (outMimeType.getSubType().equals("xml")) {
			returnValue.append("<resources>");
			while (res.next()) {
				returnValue.append("<resource ")
						.append(DomUtils.getXmlAttributeOutput("id", res.getString("res_node_uuid"))).append(" />");
			}
			returnValue.append("</resources>");
		} else {
			returnValue.append("{");
			boolean firstNode = true;
			while (res.next()) {
				if (firstNode) {
					firstNode = false;
				} else {
					returnValue.append(" , ");
				}
				returnValue.append("resource: { ")
						.append(DomUtils.getJsonAttributeOutput("id", res.getString("res_node_uuid"))).append(" } ");
			}
			returnValue.append("}");
		}
		return returnValue.toString();
	}

	@Override
	public String getRessource(Connection c, String nodeUuid, int userId, int groupId, String type)
			throws SQLException {
		// Recupere le noeud, et assemble les ressources, si il y en a
		final StringBuilder result = new StringBuilder();

		ResultSet resResource;
		final ResultSet resNode = getMysqlNode(c, nodeUuid, userId, groupId);

		if (resNode.next()) {
			String m_epm = resNode.getString("metadata_epm");
			if (m_epm == null) {
				m_epm = "";
			}
			result.append("<").append(resNode.getString("asm_type")).append(" id='")
					.append(resNode.getString("node_uuid")).append("'>");
			result.append("<metadata ").append(resNode.getString("metadata")).append("/>");
			result.append("<metadata-epm ").append(m_epm).append("/>");
			result.append("<metadata-wad ").append(resNode.getString("metadata_wad")).append("/>");

			resResource = getMysqlResource(c, resNode.getString("res_node_uuid"));
			if (resResource.next()) {
				if (resNode.getString("res_node_uuid") != null) {
					if (resNode.getString("res_node_uuid").length() > 0) {
						result.append("<asmResource id='").append(resNode.getString("res_node_uuid"))
								.append("' contextid='").append(resNode.getString("node_uuid")).append("' xsi_type='")
								.append(resResource.getString("xsi_type")).append("'>");
						result.append(resResource.getString("content"));
						result.append("</asmResource>");

						resResource.close();
					}
				}
			}
			resResource = getMysqlResource(c, resNode.getString("res_res_node_uuid"));
			if (resResource.next()) {
				if (resNode.getString("res_res_node_uuid") != null) {
					if (resNode.getString("res_res_node_uuid").length() > 0) {
						result.append("<asmResource id='").append(resNode.getString("res_res_node_uuid"))
								.append("' contextid='").append(resNode.getString("node_uuid")).append("' xsi_type='")
								.append(resResource.getString("xsi_type")).append("'>");
						//String text = "<node>"+resResource.getString("content")+"</node>";
						result.append(resResource.getString("content"));
						result.append("</asmResource>");

						resResource.close();
					}
				}
			}
			resResource = getMysqlResource(c, resNode.getString("res_context_node_uuid"));
			if (resResource.next()) {
				if (resNode.getString("res_context_node_uuid") != null) {
					if (resNode.getString("res_context_node_uuid").length() > 0) {
						result.append("<asmResource id='").append(resNode.getString("res_context_node_uuid"))
								.append("' contextid='").append(resNode.getString("node_uuid")).append("' xsi_type='")
								.append(resResource.getString("xsi_type")).append("'>");
						result.append(resResource.getString("content"));
						result.append("</asmResource>");

						resResource.close();
					}
				}
			}

			result.append("</").append(resNode.getString("asm_type")).append(">");
		}

		resNode.close();

		return result.toString();
	}

	@Override
	public String getRole(Connection c, MimeType mimeType, int grid, int userId) throws SQLException {
		PreparedStatement st;
		String sql;
		ResultSet res;

		sql = "SELECT grid FROM group_right_info WHERE grid = ?";
		st = c.prepareStatement(sql);
		st.setInt(1, grid);

		res = st.executeQuery();
		final StringBuilder result = new StringBuilder();
		try {
			while (res.next()) {
				result.append("<role ");
				result.append(DomUtils.getXmlAttributeOutput("id", res.getString("grid"))).append(" ");
				//result += DomUtils.getXmlAttributeOutput("owner", res.getString("owner"))+" ";
				result.append(DomUtils.getXmlAttributeOutput("owner", res.getString("owner"))).append(" ");
				result.append(">");
				result.append(DomUtils.getXmlElementOutput("label", res.getString("label"))).append(" ");
				result.append(DomUtils.getXmlElementOutput("portfolio_id", res.getString("portfolio_id")));

				result.append("</role>");
			}
		} catch (final Exception ex) {
			return null;
		}

		return null;
	}

	@Override
	public Integer getRoleByNode(Connection c, int userId, String nodeUuid, String role) {
		if (!cred.isAdmin(c, userId)) {
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");
		}

		String sql;
		PreparedStatement st = null;
		ResultSet res = null;

		int group = 0;
		try {
			// Check if role exists already
			sql = "SELECT gri.grid FROM group_info gi, group_right_info gri, node n " +
					"WHERE n.portfolio_id=gri.portfolio_id " +
					"AND gri.grid=gi.grid " +
					"AND n.node_uuid = uuid2bin(?) " +
					"AND gri.label = ?";
			st = c.prepareStatement(sql);
			st.setString(1, nodeUuid);
			st.setString(2, role);
			res = st.executeQuery();
			if (res.next()) {
				group = res.getInt("grid");
			}

			// If not, create it
			if (group == 0) {
				res.close();
				st.close();

				sql = "INSERT INTO group_right_info(owner, label, portfolio_id) " +
						"SELECT 1, ?, portfolio_id " +
						"FROM node " +
						"WHERE node_uuid=uuid2bin(?)";
				st = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
				if (dbserveur.equals("oracle")) {
					st = c.prepareStatement(sql, new String[] { "grid" });
				}
				st.setString(1, role);
				st.setString(2, nodeUuid);
				st.executeUpdate();
				ResultSet rs = st.getGeneratedKeys();
				if (rs.next()) {
					final int retval = rs.getInt(1);
					rs.close();
					st.close();

					sql = "INSERT INTO group_info(grid, owner, label) VALUES(?,1,?)";
					st = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
					if (dbserveur.equals("oracle")) {
						st = c.prepareStatement(sql, new String[] { "gid" });
					}
					st.setInt(1, retval);
					st.setString(2, role);
					st.executeUpdate();
					rs = st.getGeneratedKeys();
					if (rs.next()) {
						group = rs.getInt(1);
					}
				}
			}
		} catch (final Exception ex) {
			ex.printStackTrace();
			return null;
		} finally {
			try {
				if (res != null) {
					res.close();
				}
				if (st != null) {
					st.close();
				}
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}

		return group;
	}

	@Override
	public String getRolePortfolio(Connection c, MimeType mimeType, String role, String portfolioId, int userId)
			throws SQLException {
		int grid;

		PreparedStatement st;
		String sql;
		ResultSet res;

		sql = "SELECT grid FROM group_right_info WHERE label = ? and portfolio_id = uuid2bin(?)";
		st = c.prepareStatement(sql);
		st.setString(1, role);
		st.setString(2, portfolioId);

		res = st.executeQuery();

		if (!res.next()) {

			return "Le grid n'existe pas";
		}
		grid = res.getInt("grid");

		return "grid = " + grid;
	}

	@Override
	public String getRoleUser(Connection c, int userId, int userid) {
		PreparedStatement st;
		String sql;
		ResultSet res;

		final StringBuilder result = new StringBuilder("<profiles>");

		try {
			sql = "SELECT * FROM group_user gu, group_info gi, group_right_info gri WHERE userid = ? and gi.gid = gu.gid and gi.grid = gri.grid";
			st = c.prepareStatement(sql);
			st.setInt(1, userid);
			res = st.executeQuery();

			result.append("<profile>");

			while (res.next()) {
				result.append("<group");
				result.append(DomUtils.getXmlAttributeOutput("id", res.getString("gid"))).append(" ");
				result.append(">");
				result.append(DomUtils.getXmlElementOutput("label", res.getString("gi.label")));
				result.append(DomUtils.getXmlElementOutput("role", res.getString("grid.label")));
				result.append("</group>");
			}
			result.append("</profile>");
		} catch (final SQLException e) {
			e.printStackTrace();
		}

		result.append("</profiles>");

		return result.toString();
	}

	@Override
	public String getRRGInfo(Connection c, int userId, Integer rrgid) {
		String sql;
		PreparedStatement st;

		ResultSet res;
		try {
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
			final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
			final DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
			final Document document = documentBuilder.newDocument();

			final Element root = document.createElement("rolerightsgroup");
			root.setAttribute("id", rrgid.toString());
			document.appendChild(root);

			Element usersNode;
			if (!res.next()) {
				return "";
			}
			final String label = res.getString("label");
			final String portfolioid = res.getString("portfolio");

			final Element labelNode = document.createElement("label");
			labelNode.appendChild(document.createTextNode(label));
			root.appendChild(labelNode);

			final Element portfolioNode = document.createElement("portofolio");
			portfolioNode.setAttribute("id", portfolioid);
			root.appendChild(portfolioNode);

			usersNode = document.createElement("users");
			root.appendChild(usersNode);

			do {
				final int id = res.getInt("userid");
				final Element userNode = document.createElement("user");
				userNode.setAttribute("id", String.valueOf(id));
				usersNode.appendChild(userNode);

				final String login = res.getString("login");
				final Element usernameNode = document.createElement("username");
				userNode.appendChild(usernameNode);
				if (login != null) {
					usernameNode.appendChild(document.createTextNode(login));
				}

				final String firstname = res.getString("display_firstname");
				final Element fnNode = document.createElement("firstname");
				userNode.appendChild(fnNode);
				if (firstname != null) {
					fnNode.appendChild(document.createTextNode(firstname));
				}

				final String lastname = res.getString("display_lastname");
				final Element lnNode = document.createElement("lastname");
				userNode.appendChild(lnNode);
				if (lastname != null) {
					lnNode.appendChild(document.createTextNode(lastname));
				}

				final String email = res.getString("email");
				final Element eNode = document.createElement("email");
				userNode.appendChild(eNode);
				if (email != null) {
					eNode.appendChild(document.createTextNode(email));
				}

			} while (res.next());

			res.close();
			st.close();

			final StringWriter stw = new StringWriter();
			final Transformer serializer = TransformerFactory.newInstance().newTransformer();
			final DOMSource source = new DOMSource(document);
			final StreamResult stream = new StreamResult(stw);
			serializer.transform(source, stream);
			return stw.toString();
		} catch (final Exception e) {
			e.printStackTrace();
		}

		return "";
	}

	/*************************************/
	/** Groupe de droits et cie **/
	/************************************/

	@Override
	public String getRRGList(Connection c, int userId, String portfolio, Integer user, String role) {
		String sql;
		PreparedStatement st;

		ResultSet res;
		try {
			boolean bypass = false;
			if (portfolio != null && user != null) // Intersection d'un portfolio et utilisateur
			{
				sql = "SELECT DISTINCT gri.grid, label, bin2uuid(gri.portfolio_id) AS portfolio " +
						"FROM resource_table r " +
						"LEFT JOIN group_rights gr ON r.node_uuid=gr.id " +
						"LEFT JOIN group_right_info gri ON gr.grid=gri.grid " +
						"WHERE r.user_id=? AND gri.portfolio_id=uuid2bin(?)";
				st = c.prepareStatement(sql);
				st.setInt(1, user);
				st.setString(2, portfolio);
			} else if (portfolio != null && role != null) // Intersection d'un portfolio et role
			{
				sql = "SELECT DISTINCT gri.grid, label, bin2uuid(gri.portfolio_id) AS portfolio " +
						"FROM group_right_info gri " +
						"WHERE gri.label=? AND gri.portfolio_id=uuid2bin(?)";
				st = c.prepareStatement(sql);
				st.setString(1, role);
				st.setString(2, portfolio);

				bypass = true;
			} else if (portfolio != null) // Juste ceux relie e un portfolio
			{
				sql = "SELECT grid, label, bin2uuid(gri.portfolio_id) AS portfolio " +
						"FROM group_right_info gri " +
						"WHERE gri.portfolio_id=uuid2bin(?)";
				st = c.prepareStatement(sql);
				st.setString(1, portfolio);
			} else if (user != null) // Juste ceux relie e une personne
			{
				/// Requete longue, il faudrait le prendre d'un autre chemin avec ensemble plus petit, si possible
				sql = "SELECT DISTINCT gri.grid, label, bin2uuid(gri.portfolio_id) AS portfolio " +
						"FROM resource_table r " +
						"LEFT JOIN group_rights gr ON r.node_uuid=gr.id " +
						"LEFT JOIN group_right_info gri ON gr.grid=gri.grid " +
						"WHERE r.user_id=?";
				st = c.prepareStatement(sql);
				st.setInt(1, user);
			} else // Tout les groupe disponible
			{
				sql = "SELECT grid, label, bin2uuid(gri.portfolio_id) AS portfolio " + "FROM group_right_info gri";
				st = c.prepareStatement(sql);
			}

			res = st.executeQuery();

			/// Time to create data
			final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
			final DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
			final Document document = documentBuilder.newDocument();

			final Element root = document.createElement("rolerightsgroups");
			document.appendChild(root);

			if (bypass) // WAD6 demande un format specifique pour ce type de requete (...)
			{
				if (res.next()) {
					final int id = res.getInt("grid");
					if (id == 0) {
						return "";
					}
					return Integer.toString(id);
				}
			} else { // WAD6 demande un format specifique pour ce type de requete (...)
				while (res.next()) {
					final int id = res.getInt("grid");
					if (id == 0) { // Bonne chances que ce soit vide
						continue;
					}
					final Element rrg = document.createElement("rolerightsgroup");
					rrg.setAttribute("id", Integer.toString(id));
					root.appendChild(rrg);
					final String label = res.getString("label");
					final Element labelNode = document.createElement("label");
					rrg.appendChild(labelNode);
					if (label != null) {
						labelNode.appendChild(document.createTextNode(label));
					}
					final String pid = res.getString("portfolio");
					final Element portfolioNode = document.createElement("portfolio");
					portfolioNode.setAttribute("id", pid);
					rrg.appendChild(portfolioNode);
				}
			}

			res.close();
			st.close();

			final StringWriter stw = new StringWriter();
			final Transformer serializer = TransformerFactory.newInstance().newTransformer();
			final DOMSource source = new DOMSource(document);
			final StreamResult stream = new StreamResult(stw);
			serializer.transform(source, stream);
			return stw.toString();
		} catch (final Exception e) {
			e.printStackTrace();
		}

		return "";
	}

	/// Recupere les noeuds partages d'un portfolio
	/// C'est separe car les noeud ne provenant pas d'un meme portfolio, on ne peut pas les selectionner rapidement
	/// Autre possibilite serait de garder ce meme type de fonctionnement pour une selection par niveau d'un portfolio.
	/// TODO: A faire un 'benchmark' dessus
	private ResultSet getSharedMysqlStructure(Connection c, String portfolioUuid, int userId, Integer cutoff)
			throws SQLException {
		PreparedStatement st;
		String sql;
		ResultSet res = null;

		try {
			/// Check if there's shared node in this portfolio
			sql = "SELECT bin2uuid(n.shared_node_uuid) AS shared_node_uuid " +
					"FROM node n " +
					"WHERE n.portfolio_id=uuid2bin(?) AND shared_node=1";
			st = c.prepareStatement(sql);
			st.setString(1, portfolioUuid);
			res = st.executeQuery();
			if (!res.next()) {
				return null;
			}
			final String sharedNode = res.getString("shared_node_uuid");
			if (sharedNode == null) {
				return null;
			}

			/// Pour le filtrage de la structure
			if (dbserveur.equals("mysql")) {
				sql = "CREATE TEMPORARY TABLE t_struc_parentid(" +
						"uuid binary(16) UNIQUE NOT NULL, " +
						"node_parent_uuid binary(16), " +
						"t_level INT) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")) {
				final String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_struc_parentid(" +
						"uuid VARCHAR2(32) NOT NULL, " +
						"node_parent_uuid VARCHAR2(32), " +
						"t_level NUMBER(10,0)" +
						",  CONSTRAINT t_struc_parentid_UK_uuid UNIQUE (uuid)) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_struc_parentid','" + v_sql + "')}";
				final CallableStatement ocs = c.prepareCall(sql);
				ocs.execute();
				ocs.close();
			}

			// En double car on ne peut pas faire d'update/select d'une meme table temporaire
			if (dbserveur.equals("mysql")) {
				sql = "CREATE TEMPORARY TABLE t_struc_parentid_2(" +
						"uuid binary(16) UNIQUE NOT NULL, " +
						"node_parent_uuid binary(16), " +
						"t_level INT) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")) {
				final String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_struc_parentid_2(" +
						"uuid VARCHAR2(32) NOT NULL, " +
						"node_parent_uuid VARCHAR2(32), " +
						"t_level NUMBER(10,0)" +
						",  CONSTRAINT t_struc_parentid_2_UK_uuid UNIQUE (uuid)) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_struc_parentid_2','" + v_sql + "')}";
				final CallableStatement ocs = c.prepareCall(sql);
				ocs.execute();
				ocs.close();
			}

			/// Initialise la descente des noeuds partages
			sql = "INSERT INTO t_struc_parentid(uuid, node_parent_uuid, t_level) " +
					"SELECT n.shared_node_uuid, n.node_parent_uuid, 0 " +
					"FROM node n " +
					"WHERE n.portfolio_id=uuid2bin(?) AND shared_node=1";
			st = c.prepareStatement(sql);
			st.setString(1, portfolioUuid);
			st.executeUpdate();
			st.close();

			/// On boucle, sera toujours <= e "nombre de noeud du portfolio"
			int level = 0;
			int added = 1;
			if (dbserveur.equals("mysql")) {
				sql = "INSERT IGNORE INTO t_struc_parentid_2(uuid, node_parent_uuid, t_level) ";
			} else if (dbserveur.equals("oracle")) {
				sql = "INSERT /*+ ignore_row_on_dupkey_index(t_struc_parentid_2,t_struc_parentid_2_UK_uuid)*/ INTO t_struc_parentid_2(uuid, node_parent_uuid, t_level) ";
			}
			sql += "SELECT n.node_uuid, n.node_parent_uuid, ? " +
					"FROM node n WHERE n.portfolio_id=uuid2bin(?) AND n.node_parent_uuid IN (SELECT uuid FROM t_struc_parentid t " +
					"WHERE t.t_level=?)";

			String sqlTemp = null;
			if (dbserveur.equals("mysql")) {
				sqlTemp = "INSERT IGNORE INTO t_struc_parentid SELECT * FROM t_struc_parentid_2;";
			} else if (dbserveur.equals("oracle")) {
				sqlTemp = "INSERT /*+ ignore_row_on_dupkey_index(t_struc_parentid,t_struc_parentid_UK_uuid)*/ INTO t_struc_parentid SELECT * FROM t_struc_parentid_2";
			}
			final PreparedStatement stTemp = c.prepareStatement(sqlTemp);

			st = c.prepareStatement(sql);
			st.setString(2, portfolioUuid);
			while (added != 0 && (cutoff == null || level < cutoff)) {
				st.setInt(1, level + 1);
				st.setInt(3, level);
				st.executeUpdate();
				added = stTemp.executeUpdate(); // On s'arrete quand rien a ete ajoute
				level = level + 1; // Prochaine etape
			}
			st.close();
			stTemp.close();

			// Selectionne les donnees selon la filtration
			sql = "SELECT bin2uuid(n.node_uuid) AS node_uuid," +
					" node_children_uuid, " +
					" n.node_order," +
					" n.metadata, n.metadata_wad, n.metadata_epm," +
					" n.shared_node AS shared_node," +
					" bin2uuid(n.shared_node_res_uuid) AS shared_node_res_uuid," +
					" bin2uuid(n.res_node_uuid) AS res_node_uuid," +
					" r1.xsi_type AS r1_type, r1.content AS r1_content," + // donnee res_node
					" bin2uuid(n.res_res_node_uuid) as res_res_node_uuid," +
					" r2.content AS r2_content," + // donnee res_res_node
					" bin2uuid(n.res_context_node_uuid) as res_context_node_uuid," +
					" r3.content AS r3_content," + // donnee res_context
					" n.asm_type, n.xsi_type," +
					" gr.RD, gr.WR, gr.SB, gr.DL, gr.types_id, gr.rules_id," + // info sur les droits
					" bin2uuid(n.portfolio_id) AS portfolio_id" +
					" FROM node n" +
					" LEFT JOIN resource_table r1 ON n.res_node_uuid=r1.node_uuid" + // Recuperation des donnees res_node
					" LEFT JOIN resource_table r2 ON n.res_res_node_uuid=r2.node_uuid" + // Recuperation des donnees res_res_node
					" LEFT JOIN resource_table r3 ON n.res_context_node_uuid=r3.node_uuid" + // Recuperation des donnees res_context
					//					" LEFT JOIN (group_rights gr, group_info gi, group_user gu)" +     // Verification des droits
					" LEFT JOIN group_rights gr ON n.node_uuid=gr.id" + // Vérification des droits
					" LEFT JOIN group_info gi ON gr.grid=gi.grid" +
					" LEFT JOIN group_user gu ON gi.gid=gu.gid" +
					" WHERE gu.userid=? AND gr.RD=1" + // On doit au moins avoir le droit de lecture
					" AND n.node_uuid IN (SELECT uuid FROM t_struc_parentid)"; // Selon note filtrage, prendre les noeud necessaire

			st = c.prepareStatement(sql);
			st.setInt(1, userId);
			res = st.executeQuery();
		} catch (final SQLException e) {
			logger.error("SQL Exception", e);
		} finally {
			try {
				// Les 'pooled connection' ne se ferment pas vraiment. On nettoie manuellement les tables temporaires...
				if (dbserveur.equals("mysql")) {
					sql = "DROP TEMPORARY TABLE IF EXISTS t_struc_parentid, t_struc_parentid_2";
					st = c.prepareStatement(sql);
					st.execute();
					st.close();
				}
			} catch (final SQLException e) {
				logger.error("SQL Exception", e);
			}
		}

		return res;
	}

	@Override
	public Object getUser(Connection c, int userId) {
		try {
			return getMySqlUser(c, userId);
		} catch (final Exception ex) {
			return null;
		}
	}

	@Override
	public String getUserGroupByPortfolio(Connection c, String portfolioUuid, int userId) {
		final ResultSet res = getMysqlUserGroupByPortfolio(c, portfolioUuid, userId);

		final StringBuilder result = new StringBuilder("<groups>");
		try {
			while (res.next()) {
				result.append("<group ");
				result.append(DomUtils.getXmlAttributeOutput("id", res.getString("gid"))).append(" ");
				//result += DomUtils.getXmlAttributeOutput("owner", res.getString("owner"))+" ";
				result.append(DomUtils.getXmlAttributeOutput("templateId", res.getString("grid"))).append(" ");
				result.append(">");
				result.append(DomUtils.getXmlElementOutput("label", res.getString("g_label")));
				result.append(DomUtils.getXmlElementOutput("role", res.getString("g_label")));
				result.append(DomUtils.getXmlElementOutput("groupid", res.getString("gid")));
				result.append("</group>");

			}
		} catch (final SQLException e) {
			e.printStackTrace();
			return null;
		}

		result.append("</groups>");
		return result.toString();
	}

	@Override
	public String getUserGroupList(Connection c, int userId) {
		String sql;
		PreparedStatement st = null;
		ResultSet res = null;

		final StringBuilder result = new StringBuilder("<groups>");
		try {
			sql = "SELECT * FROM credential_group";
			st = c.prepareStatement(sql);
			res = st.executeQuery();

			while (res.next()) {
				result.append("<group ");
				result.append(DomUtils.getXmlAttributeOutput("id", res.getString("cg"))).append(" ");
				result.append(">");
				result.append(DomUtils.getXmlElementOutput("label", res.getString("label")));
				result.append("</group>");
			}
		} catch (final SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				if (res != null) {
					res.close();
				}
				if (st != null) {
					st.close();
				}
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}

		result.append("</groups>");

		return result.toString();
	}

	@Override
	public Object getUserGroups(Connection c, int userId) throws Exception {
		final ResultSet res = getMysqlUserGroups(c, userId);

		final StringBuilder result = new StringBuilder("<groups>");
		while (res.next()) {
			result.append("<group ");
			result.append(DomUtils.getXmlAttributeOutput("id", res.getString("gid"))).append(" ");
			result.append(DomUtils.getXmlAttributeOutput("owner", res.getString("owner"))).append(" ");
			result.append(DomUtils.getXmlAttributeOutput("templateId", res.getString("grid"))).append(" ");
			result.append(">");
			result.append(DomUtils.getXmlElementOutput("label", res.getString("label")));
			result.append("</group>");
		}

		result.append("</groups>");

		return result.toString();
	}

	@Override
	public String getUserId(Connection c, String username, String email) throws Exception {
		PreparedStatement st;
		String sql;
		ResultSet res;
		String retval = "0";

		try {
			if (!"".equals(username) && username != null) {
				sql = "SELECT userid FROM credential WHERE login = ? ";
				st = c.prepareStatement(sql);
				st.setString(1, username);
			} else if (!"".equals(email) && email != null) {
				sql = "SELECT userid FROM credential WHERE email = ? ";
				st = c.prepareStatement(sql);
				st.setString(1, email);
			} else {
				return retval;
			}
			res = st.executeQuery();
			if (res.next()) {
				retval = res.getString(1);
			}
		} catch (final Exception ex) {
			ex.printStackTrace();
		}

		return retval;
	}

	/// Retrouve le uid du username
	/// currentUser est le au cas oe on voudrait limiter l'acces
	@Override
	public String getUserID(Connection c, int currentUser, String username) {
		PreparedStatement st;
		ResultSet res;
		int result;
		try {
			final String sql = "SELECT userid FROM credential WHERE login=?";
			st = c.prepareStatement(sql);
			st.setString(1, username);
			res = st.executeQuery();
			if (!res.next()) {
				throw new RestWebApplicationException(Status.NOT_FOUND, "User " + username + " not found");
			}
			result = res.getInt("userid");
		} catch (final SQLException e) {
			logger.error("getUserID {} not found", username);
			throw new RestWebApplicationException(Status.NOT_FOUND, "User " + username + " not found");
		}

		return Integer.toString(result);
	}

	@Override
	public Object getUsers(Connection c, int userId, String username, String firstname, String lastname, String email)
			throws Exception {
		final ResultSet res = getMysqlUsers(c, userId, username, firstname, lastname, email);

		final StringBuilder result = new StringBuilder("<users>");
		int curUser = 0;
		while (res.next()) {
			final int userid = res.getInt("userid");
			if (curUser != userid) {
				curUser = userid;
				String subs = res.getString("id");
				if (subs != null) {
					subs = "1";
				} else {
					subs = "0";
				}

				result.append("<user ");
				result.append(DomUtils.getXmlAttributeOutput("id", res.getString("userid"))).append(" ");
				result.append(">");
				result.append(DomUtils.getXmlElementOutput("label", res.getString("login")));
				result.append(DomUtils.getXmlElementOutput("display_firstname", res.getString("display_firstname")));
				result.append(DomUtils.getXmlElementOutput("display_lastname", res.getString("display_lastname")));
				result.append(DomUtils.getXmlElementOutput("email", res.getString("email")));
				result.append(DomUtils.getXmlElementOutput("active", res.getString("active")));
				result.append(DomUtils.getXmlElementOutput("substitute", subs));
				result.append("</user>");
			}
		}

		result.append("</users>");

		return result.toString();
	}

	@Override
	public String getUsersByRole(Connection c, int userId, String portfolioUuid, String role) throws SQLException {
		String sql;
		PreparedStatement st;
		ResultSet res;

		try {
			sql = "SELECT * FROM credential c, group_right_info gri, group_info gi, group_user gu WHERE c.userid = gu.userid AND gu.gid = gi.gid AND gi.grid = gri.grid AND gri.portfolio_id = uuid2bin(?) AND gri.label = ?";
			st = c.prepareStatement(sql);
			st.setString(1, portfolioUuid);
			st.setString(2, role);
			res = st.executeQuery();
		} catch (final Exception ex) {
			ex.printStackTrace();
			return null;
		}

		final StringBuilder result = new StringBuilder("<users>");
		try {
			while (res.next()) {
				result.append("<user ");
				result.append(DomUtils.getXmlAttributeOutput("id", res.getString("userid"))).append(" ");
				result.append(">");
				result.append(DomUtils.getXmlElementOutput("username", res.getString("login")));
				result.append(DomUtils.getXmlElementOutput("firstname", res.getString("display_firstname")));
				result.append(DomUtils.getXmlElementOutput("lastname", res.getString("display_lastname")));
				result.append(DomUtils.getXmlElementOutput("email", res.getString("email")));
				result.append("</user>");
			}
		} catch (final SQLException e) {
			e.printStackTrace();
		}

		result.append("</users>");

		return result.toString();

	}

	@Override
	public String getUsersByUserGroup(Connection c, int userGroupId, int userId) {
		String sql;
		PreparedStatement st = null;
		ResultSet res = null;

		final StringBuilder result = new StringBuilder("<group id=\"" + userGroupId + "\"><users>");
		try {
			sql = "SELECT * FROM credential_group_members WHERE cg=?";
			st = c.prepareStatement(sql);
			st.setInt(1, userGroupId);
			res = st.executeQuery();

			while (res.next()) {
				result.append("<user ");
				result.append(DomUtils.getXmlAttributeOutput("id", "" + res.getInt("userid"))).append(" ");
				result.append(">");
				result.append("</user>");
			}
		} catch (final SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				if (res != null) {
					res.close();
				}
				if (st != null) {
					st.close();
				}
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}

		result.append("</users></group>");

		return result.toString();
	}

	private boolean insertMySqlLog(Connection c, String url, String method, String headers, String inBody,
			String outBody, int code) {
		String sql;
		PreparedStatement st;

		try {
			sql = "INSERT INTO log_table(log_date,log_url,log_method,log_headers,log_in_body,log_out_body,log_code) ";
			if (dbserveur.equals("mysql")) {
				sql += "VALUES(NOW(),?,?,?,?,?,?)";
			} else if (dbserveur.equals("oracle")) {
				sql += "VALUES(CURRENT_TIMESTAMP,?,?,?,?,?,?)";
			}
			st = c.prepareStatement(sql);
			st.setString(1, url);
			st.setString(2, method);
			st.setString(3, headers);
			st.setString(4, inBody);
			st.setString(5, outBody);
			st.setInt(6, code);

			return st.execute();
		} catch (final Exception ex) {
			ex.printStackTrace();
			return false;
		}
	}

	/*
	 *  Ecrit le noeud dans la base MySQL
	 */
	private int insertMySqlNode(Connection c, String nodeUuid, String nodeParentUuid, String nodeChildrenUuid,
			String asmType, String xsiType, int sharedRes, int sharedNode, int sharedNodeRes, String sharedResUuid,
			String sharedNodeUuid, String sharedNodeResUuid, String metadata, String metadataWad, String metadataEpm,
			String semtag, String semanticTag, String label, String code, String descr, String format, int order,
			int modifUserId, String portfolioUuid) {
		String sql = "";
		PreparedStatement st;

		try {
			if (nodeChildrenUuid == null) {
				nodeChildrenUuid = getMysqlNodeResultset(c, nodeUuid).getString("node_children_uuid");
			}
		} catch (final Exception ex) {
			logger.error("Exception", ex);
		}

		/// Because Oracle can't do its work properly
		if ("".equals(semanticTag)) {
			semanticTag = null;
		}
		if ("".equals(nodeChildrenUuid)) {
			nodeChildrenUuid = null;
		}
		if ("".equals(xsiType)) {
			xsiType = null;
		}
		if ("".equals(code)) {
			code = null;
		}

		try {
			if (dbserveur.equals("mysql")) {
				sql = "REPLACE INTO node(node_uuid,node_parent_uuid,node_children_uuid,node_order,";
				sql += "asm_type,xsi_type,shared_res,shared_node,shared_node_res,shared_res_uuid,shared_node_uuid,shared_node_res_uuid, metadata,metadata_wad,metadata_epm,semtag,semantictag,label,code,descr,format,modif_user_id,modif_date,portfolio_id) ";
				sql += "VALUES(uuid2bin(?),uuid2bin(?),?,?,?,?,?,?,?,uuid2bin(?),uuid2bin(?),uuid2bin(?),?,?,?,?,?,?,?,?,?,?,?,uuid2bin(?))";
			} else if (dbserveur.equals("oracle")) {
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
			st.setString(10, sharedResUuid);
			st.setString(11, sharedNodeUuid);
			st.setString(12, sharedNodeResUuid);
			st.setString(13, metadata);
			st.setString(14, metadataWad);
			st.setString(15, metadataEpm);
			st.setString(16, semtag);
			st.setString(17, semanticTag);
			st.setString(18, label);
			st.setString(19, code);
			st.setString(20, descr);
			st.setString(21, format);
			st.setInt(22, modifUserId);
			if (dbserveur.equals("mysql")) {
				st.setString(23, SqlUtils.getCurrentTimeStamp());
			} else if (dbserveur.equals("oracle")) {
				st.setTimestamp(23, SqlUtils.getCurrentTimeStamp2());
			}
			st.setString(24, portfolioUuid);

			return st.executeUpdate();
		} catch (final Exception ex) {
			ex.printStackTrace();
			return -1;
		}
	}

	private int insertMysqlPortfolio(Connection c, String portfolioUuid, String rootNodeUuid, int modelId, int userId) {
		String sql = "";
		PreparedStatement st;

		try {
			if (dbserveur.equals("mysql")) {
				sql = "REPLACE INTO portfolio(portfolio_id,root_node_uuid,user_id,model_id,modif_user_id,modif_date) ";
				sql += "VALUES(uuid2bin(?),uuid2bin(?),?,?,?,?)";
			} else if (dbserveur.equals("oracle")) {
				sql = "MERGE INTO portfolio d USING (SELECT uuid2bin(?) portfolio_id,uuid2bin(?) root_node_uuid,? user_id,? model_id,? modif_user_id,? modif_date FROM DUAL) s ON (d.portfolio_id = s.portfolio_id) WHEN MATCHED THEN UPDATE SET d.root_node_uuid = s.root_node_uuid, d.user_id = s.user_id,d.model_id = s.model_id, d.modif_user_id = s.modif_user_id, d.modif_date = s.modif_date WHEN NOT MATCHED THEN INSERT (d.portfolio_id, d.root_node_uuid, d.user_id, d.model_id, d.modif_user_id, d.modif_date) VALUES (s.portfolio_id, s.root_node_uuid, s.user_id, s.model_id, s.modif_user_id, s.modif_date)";
			}
			st = c.prepareStatement(sql);
			st.setString(1, portfolioUuid);
			st.setString(2, rootNodeUuid);
			st.setInt(3, userId);
			if (dbserveur.equals("mysql")) {
				st.setInt(4, modelId);
			} else if (dbserveur.equals("oracle")) {
				st.setString(4, String.format("%32s", Integer.toHexString(modelId)).replace(' ', '0'));
			}
			st.setInt(5, userId);
			if (dbserveur.equals("mysql")) {
				st.setString(6, SqlUtils.getCurrentTimeStamp());
			} else if (dbserveur.equals("oracle")) {
				st.setTimestamp(6, SqlUtils.getCurrentTimeStamp2());
			}

			final int update = st.executeUpdate();
			st.close();
			return update;

		} catch (final Exception ex) {
			logger.error("Managed error with root_node_uuid {}", rootNodeUuid, ex);
			return -1;
		}
	}

	private int insertMysqlResource(Connection c, String uuid, String parentUuid, String xsiType, String content,
			String portfolioModelId, int sharedNodeRes, int sharedRes, int userId) {
		String sql = "";
		PreparedStatement st = null;
		int status;

		try {
			if (((xsiType.equals("nodeRes") && sharedNodeRes == 1)
					|| (!xsiType.equals("context") && !xsiType.equals("nodeRes") && sharedRes == 1))
					&& portfolioModelId != null) {
				// On ne fait rien

			} else {
				if (dbserveur.equals("mysql")) {
					sql = "REPLACE INTO resource_table(node_uuid,xsi_type,content,user_id,modif_user_id,modif_date) ";
					sql += "VALUES(uuid2bin(?),?,?,?,?,?)";
				} else if (dbserveur.equals("oracle")) {
					sql = "MERGE INTO resource_table d USING (SELECT uuid2bin(?) node_uuid,? xsi_type,? content,? user_id,? modif_user_id,? modif_date FROM DUAL) s ON (d.node_uuid = s.node_uuid) WHEN MATCHED THEN UPDATE SET d.xsi_type = s.xsi_type, d.content = s.content, d.user_id = s.user_id, d.modif_user_id = s.modif_user_id, d.modif_date = s.modif_date WHEN NOT MATCHED THEN INSERT (d.node_uuid, d.xsi_type, d.content, d.user_id, d.modif_user_id, d.modif_date) VALUES (s.node_uuid, s.xsi_type, s.content, s.user_id, s.modif_user_id, s.modif_date)";
				}
				st = c.prepareStatement(sql);
				st.setString(1, uuid);
				st.setString(2, xsiType);
				st.setString(3, content);
				st.setInt(4, userId);
				st.setInt(5, userId);
				if (dbserveur.equals("mysql")) {
					st.setString(6, SqlUtils.getCurrentTimeStamp());
				} else if (dbserveur.equals("oracle")) {
					st.setTimestamp(6, SqlUtils.getCurrentTimeStamp2());
				}

				st.executeUpdate();
				if (st != null) {
					try {
						st.close();
					} catch (final SQLException e) {
						e.printStackTrace();
					}
				}
			}
			// Ensuite on met à jour les id ressource au niveau du noeud parent
			if (xsiType.equals("nodeRes")) {
				sql = " UPDATE node SET res_res_node_uuid =uuid2bin(?), shared_node_res_uuid=uuid2bin(?) ";
				sql += " WHERE node_uuid = uuid2bin(?) ";
				st = c.prepareStatement(sql);
				st.setString(1, uuid);
				if (sharedNodeRes == 1 && portfolioModelId != null) {
					st.setString(2, uuid);
				} else {
					st.setString(2, null);
				}
				st.setString(3, parentUuid);
			} else if (xsiType.equals("context")) {
				sql = " UPDATE node SET res_context_node_uuid=uuid2bin(?)";
				sql += " WHERE node_uuid = uuid2bin(?) ";
				st = c.prepareStatement(sql);
				st.setString(1, uuid);
				st.setString(2, parentUuid);
			} else {
				sql = " UPDATE node SET res_node_uuid=uuid2bin(?), shared_res_uuid=uuid2bin(?) ";
				sql += " WHERE node_uuid = uuid2bin(?) ";
				st = c.prepareStatement(sql);
				st.setString(1, uuid);
				if (sharedRes == 1 && portfolioModelId != null) {
					st.setString(2, uuid);
				} else {
					st.setString(2, null);
				}
				st.setString(3, parentUuid);
			}

			st.executeUpdate();
			status = st.getUpdateCount();
		} catch (final Exception ex) {
			ex.printStackTrace();
			status = -1;
		} finally {
			if (st != null) {
				try {
					st.close();
				} catch (final SQLException e) {
					e.printStackTrace();
				}
			}
		}
		return status;
	}

	@Deprecated
	private int insertMysqlUser(Connection c, int userId, String oAuthToken, String oAuthSecret) {
		String sql;
		PreparedStatement st;

		try {
			sql = "REPLACE INTO user(user_id,oauth_token,oauth_secret) ";

			sql += "VALUES(?,?,?)";
			st = c.prepareStatement(sql);
			st.setInt(1, userId);
			st.setString(2, oAuthToken);
			st.setString(3, oAuthSecret);

			return st.executeUpdate();
		} catch (final Exception ex) {
			logger.error("Managed error", ex);
			return -1;
		}
	}

	@Override
	public boolean isAdmin(Connection c, String uid) {
		final int userid = Integer.parseInt(uid);
		return cred.isAdmin(c, userid);
	}

	// Same code allowed with nodes in different portfolio, and not root node
	@Override
	public boolean isCodeExist(Connection c, String code, String nodeuuid) {
		boolean response = false;
		String sql;
		PreparedStatement st = null;
		ResultSet rs = null;
		try {
			sql = "SELECT bin2uuid(portfolio_id) FROM node " + "WHERE asm_type=? AND code=? ";
			if (nodeuuid != null) {
				sql += "AND node_uuid!=uuid2bin(?) AND portfolio_id=(SELECT portfolio_id FROM node WHERE node_uuid=uuid2bin(?))";
			}
			st = c.prepareStatement(sql);
			st.setString(1, "asmRoot");
			st.setString(2, code);
			if (nodeuuid != null) {
				st.setString(3, nodeuuid);
				st.setString(4, nodeuuid);
			}
			rs = st.executeQuery();

			if (rs.next()) {
				response = true;
			}
		} catch (final Exception ex) {
			ex.printStackTrace();
		} finally {
			try {
				if (rs != null) {
					rs.close();
				}
				if (st != null) {
					st.close();
				}
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}

		return response;
	}

	@Override
	public boolean isUserInGroup(Connection c, String uid, String gid) {
		PreparedStatement st;
		String sql;
		ResultSet res;
		boolean retval = false;

		try {
			sql = "SELECT userid FROM group_user WHERE userid=? AND gid=?";
			st = c.prepareStatement(sql);
			st.setString(1, uid);
			st.setString(2, gid);
			res = st.executeQuery();
			if (res.next()) {
				retval = true;
			}
		} catch (final Exception ex) {
			ex.printStackTrace();
		}

		return retval;
	}

	@Override
	public boolean isUserMemberOfGroup(Connection c, int userId, int groupId) {
		return cred.isUserMemberOfGroup(c, userId, groupId);
	}

	@Override
	public String[] logViaEmail(Connection c, String email) {
		final boolean email_ok = EMAIL_PATTERN.matcher(email).matches();
		if (!email_ok) {
			return null;
		}
		String[] data = null;
		String sql;
		PreparedStatement st = null;
		ResultSet res = null;

		try {
			sql = "SELECT userid, login FROM credential WHERE email=?";
			st = c.prepareStatement(sql);
			st.setString(1, email);
			res = st.executeQuery();

			if (res.next()) {
				final int userid = res.getInt(1);
				final String username = res.getString(2);
				data = new String[5];
				data[1] = username;
				data[2] = Integer.toString(userid);
			}
		} catch (final Exception ex) {
			ex.printStackTrace();
		} finally {
			try {
				if (res != null) {
					res.close();
				}
				if (st != null) {
					st.close();
				}
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}

		return data;
	}

	@Override
	public boolean postChangeNodeParent(Connection c, int userid, String uuid, String uuidParent) {
		/// FIXME something with parent uuid too
		//			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		if (uuid.equals(uuidParent)) { // Ajouter un noeud e lui-meme
			return false;
		}

		String sql;
		PreparedStatement st;
		boolean status = false;

		try {
			/// Keep origin parent uuid
			sql = "SELECT bin2uuid(node_parent_uuid) AS puuid " + "FROM node " + "WHERE node_uuid=uuid2bin(?)";
			st = c.prepareStatement(sql);
			st.setString(1, uuid);
			ResultSet res = st.executeQuery();

			String puuid = "";
			if (res.next()) {
				puuid = res.getString("puuid");
			}

			/// Admin and designer can move regardless
			if (cred.isAdmin(c, userid) || cred.isDesigner(c, userid, uuid)) {
				final int next = getMysqlNodeNextOrderChildren(c, uuidParent);

				c.setAutoCommit(false);

				sql = "UPDATE node " +
						"SET node_parent_uuid=uuid2bin(?), node_order=? " +
						"WHERE node_uuid=uuid2bin(?)";
				st = c.prepareStatement(sql);
				st.setString(1, uuidParent);
				st.setInt(2, next);
				st.setString(3, uuid);
				st.executeUpdate();
			} else {
				/// Check if source and destination are writable by user and in same portfolio/same grid
				sql = "SELECT gr2.WR " +
						"FROM group_rights gr2, " +
						"(SELECT n1.portfolio_id, gi1.grid " +
						"FROM group_user gu1, group_info gi1, group_rights gr1, node n1 " +
						"WHERE gu1.userid=? " +
						"AND gu1.gid=gi1.gid AND gi1.grid=gr1.grid AND gr1.WR=1 " +
						"AND gr1.id=uuid2bin(?) AND gr1.id=n1.node_uuid) AS t1 " +
						"WHERE t1.grid=gr2.grid AND gr2.id=uuid2bin(?);";
				st = c.prepareStatement(sql);
				st.setInt(1, userid);
				st.setString(2, uuid);
				st.setString(3, uuidParent);
				res = st.executeQuery();

				if (!res.next()) {
					return false;
				}
				final int next = getMysqlNodeNextOrderChildren(c, uuidParent);

				c.setAutoCommit(false);

				sql = "UPDATE node " +
						"SET node_parent_uuid=uuid2bin(?), node_order=? " +
						"WHERE node_uuid=uuid2bin(?)";
				st = c.prepareStatement(sql);
				st.setString(1, uuidParent);
				st.setInt(2, next);
				st.setString(3, uuid);
				st.executeUpdate();
			}

			/// Update children list, origin and destination
			updateMysqlNodeChildren(c, puuid);
			updateMysqlNodeChildren(c, uuidParent);

			touchPortfolio(c, uuid, null);

			status = true;
		} catch (final Exception e) {
			try {
				c.rollback();
			} catch (final SQLException e1) {
				e1.printStackTrace();
			}
			e.printStackTrace();
		} finally {
			try {
				c.setAutoCommit(true);
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}

		return status;
	}

	// Meme chose que postImportNode, mais on ne prend pas en compte le parsage des droits
	@Override
	public Object postCopyNode(Connection c, MimeType inMimeType, String destUuid, String tag, String code,
			String srcuuid, int userId, int groupId) throws Exception {
		if ("".equals(tag) || tag == null || "".equals(code) || code == null) {
			if (srcuuid == null || "".equals(srcuuid)) {
				return "erreur";
			}
		}

		String sql;
		PreparedStatement st;
		String createdUuid = "erreur";

		try {
			/// Check/update cache
			String portfolioCode = "";

			if (srcuuid != null) {
				// Check if user has right to read it
				if (!cred.hasNodeRight(c, userId, groupId, srcuuid, Credential.READ)) {
					return "No rights";
				}
			} else {
				/// Check/update cache
				portfolioCode = checkCache(c, code);

				if (portfolioCode == null) {
					return "Inexistent selection";
				}
			}

			//			t1 = System.currentTimeMillis();

			///// Creation des tables temporaires
			/// Pour la copie de la structure
			if (dbserveur.equals("mysql")) {
				sql = "CREATE TEMPORARY TABLE t_data_node(" +
						"new_uuid binary(16) NOT NULL, " + /// Pour la copie d'une nouvelle structure
						"node_uuid binary(16)  NOT NULL, " +
						"node_parent_uuid binary(16) DEFAULT NULL, " +
						"node_order int(12) NOT NULL, " +
						//						"metadata_wad varchar(2798) NOT NULL, " +
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
						"semtag varchar(100) DEFAULT NULL, " +
						"semantictag varchar(100) DEFAULT NULL, " +
						"label varchar(100)  DEFAULT NULL, " +
						"code varchar(255)  DEFAULT NULL, " +
						"descr varchar(100)  DEFAULT NULL, " +
						"format varchar(30) DEFAULT NULL, " +
						"modif_user_id int(12) NOT NULL, " +
						"modif_date timestamp NULL DEFAULT NULL, " +
						"portfolio_id binary(16) DEFAULT NULL) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")) {
				final String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_data_node(" +
						"new_uuid VARCHAR2(32) NOT NULL, " + /// Pour la copie d'une nouvelle structure
						"node_uuid VARCHAR2(32)  NOT NULL, " +
						"node_parent_uuid VARCHAR2(32) DEFAULT NULL, " +
						"node_order NUMBER(12) NOT NULL, " +
						//						"metadata_wad VARCHAR2(2798 CHAR) DEFAULT NULL, " +
						"res_node_uuid VARCHAR2(32) DEFAULT NULL, " +
						"res_res_node_uuid VARCHAR2(32) DEFAULT NULL, " +
						"res_context_node_uuid VARCHAR2(32)  DEFAULT NULL, " +
						"shared_res NUMBER(1) NOT NULL, " +
						"shared_node NUMBER(1) NOT NULL, " +
						"shared_node_res NUMBER(1) NOT NULL, " +
						"shared_res_uuid VARCHAR2(32) DEFAULT NULL, " +
						"shared_node_uuid VARCHAR2(32) DEFAULT NULL, " +
						"shared_node_res_uuid VARCHAR2(32) DEFAULT NULL, " +
						"asm_type VARCHAR2(50 CHAR) DEFAULT NULL, " +
						"xsi_type VARCHAR2(50 CHAR)  DEFAULT NULL, " +
						"semtag VARCHAR2(100 CHAR) DEFAULT NULL, " +
						"semantictag VARCHAR2(100 CHAR) DEFAULT NULL, " +
						"label VARCHAR2(100 CHAR)  DEFAULT NULL, " +
						"code VARCHAR2(255 CHAR)  DEFAULT NULL, " +
						"descr VARCHAR2(100 CHAR)  DEFAULT NULL, " +
						"format VARCHAR2(30 CHAR) DEFAULT NULL, " +
						"modif_user_id NUMBER(12) NOT NULL, " +
						"modif_date timestamp DEFAULT NULL, " +
						"portfolio_id VARCHAR2(32) DEFAULT NULL) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_data_node','" + v_sql + "')}";
				final CallableStatement ocs = c.prepareCall(sql);
				ocs.execute();
				ocs.close();
			}

			String baseUuid;
			ResultSet res;
			if (srcuuid != null) // Since we're not sure if those nodes have to be cached, do it from general table
			{
				sql = "INSERT INTO t_data_node ";
				if (dbserveur.equals("mysql")) {
					sql += "SELECT uuid2bin(UUID()), ";
				} else if (dbserveur.equals("oracle")) {
					sql += "SELECT sys_guid(), ";
				}
				//				sql += "node_uuid, node_parent_uuid, node_order, metadata_wad, res_node_uuid, res_res_node_uuid, res_context_node_uuid, shared_res, shared_node, shared_node_res, shared_res_uuid, shared_node_uuid, shared_node_res_uuid, asm_type, xsi_type, semtag, semantictag, label, code, descr, format, modif_user_id, modif_date, portfolio_id " +
				sql += "node_uuid, node_parent_uuid, node_order, res_node_uuid, res_res_node_uuid, res_context_node_uuid, shared_res, shared_node, shared_node_res, shared_res_uuid, shared_node_uuid, shared_node_res_uuid, asm_type, xsi_type, semtag, semantictag, label, code, descr, format, modif_user_id, modif_date, portfolio_id " +
						"FROM node n " +
						"WHERE n.portfolio_id=(SELECT portfolio_id FROM node n1 WHERE n1.node_uuid=uuid2bin(?))";
				st = c.prepareStatement(sql);
				st.setString(1, srcuuid);
				st.executeUpdate();
				st.close();

				// Then skip tag searching since we know the uuid
				baseUuid = srcuuid;
			} else {
				// Copie the whole portfolio from shared cache to local cache
				/// Copie de la structure
				sql = "INSERT INTO t_data_node ";
				if (dbserveur.equals("mysql")) {
					sql += "SELECT uuid2bin(UUID()), ";
				} else if (dbserveur.equals("oracle")) {
					sql += "SELECT sys_guid(), ";
				}
				//				sql += "node_uuid, node_parent_uuid, node_order, metadata_wad, res_node_uuid, res_res_node_uuid, res_context_node_uuid, shared_res, shared_node, shared_node_res, shared_res_uuid, shared_node_uuid, shared_node_res_uuid, asm_type, xsi_type, semtag, semantictag, label, code, descr, format, modif_user_id, modif_date, portfolio_id " +
				sql += "node_uuid, node_parent_uuid, node_order, res_node_uuid, res_res_node_uuid, res_context_node_uuid, shared_res, shared_node, shared_node_res, shared_res_uuid, shared_node_uuid, shared_node_res_uuid, asm_type, xsi_type, semtag, semantictag, label, code, descr, format, modif_user_id, modif_date, portfolio_id " +
						"FROM t_node_cache n " +
						"WHERE portfolio_id=uuid2bin(?)";
				st = c.prepareStatement(sql);
				st.setString(1, portfolioCode);
				st.executeUpdate();
				st.close();

				/// Check if we can find a code with the tag sent
				sql = "SELECT bin2uuid(n2.node_uuid) AS nUuid, bin2uuid(n2.portfolio_id) AS pUuid " +
						"FROM t_data_node n2 " +
						"WHERE n2.code=? AND n2.portfolio_id=uuid2bin(?)";
				st = c.prepareStatement(sql);
				st.setString(1, tag);
				st.setString(2, portfolioCode);
				res = st.executeQuery();
				if (res.next()) // Take the first one declared
				{
					baseUuid = res.getString("nUuid");
					res.getString("pUuid");
					res.close();
					st.close();
				} else // If nothing, then continue with semantictag
				{
					/// Find the right starting node we want
					sql = "SELECT bin2uuid(n2.node_uuid) AS nUuid, bin2uuid(n2.portfolio_id) AS pUuid " +
							"FROM t_data_node n2 " +
							"WHERE n2.semantictag = ? AND n2.portfolio_id=uuid2bin(?)";
					st = c.prepareStatement(sql);
					st.setString(1, tag);
					st.setString(2, portfolioCode);

					res = st.executeQuery();
					if (!res.next()) {
						res.close();
						st.close();
						return "Selection non existante.";
					}
					baseUuid = res.getString("nUuid");
					res.getString("pUuid");
					res.close();
					st.close();
				}
			}

			//			t1a = System.currentTimeMillis();

			//			t2 = System.currentTimeMillis();

			/// Pour la copie des donnees
			if (dbserveur.equals("mysql")) {
				sql = "CREATE TEMPORARY TABLE t_res_node(" +
						"new_uuid binary(16) NOT NULL, " + /// Pour la copie d'une nouvelle structure
						"node_uuid binary(16) NOT NULL, " +
						"xsi_type varchar(50) DEFAULT NULL, " +
						"user_id int(11) DEFAULT NULL, " +
						"modif_user_id int(12) NOT NULL, " +
						"modif_date timestamp NULL DEFAULT NULL) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")) {
				final String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_res_node(" +
						"new_uuid VARCHAR2(32) NOT NULL, " + /// Pour la copie d'une nouvelle structure
						"node_uuid VARCHAR2(32) NOT NULL, " +
						"xsi_type VARCHAR2(50 CHAR) DEFAULT NULL, " +
						"user_id NUMBER(11) DEFAULT NULL, " +
						"modif_user_id NUMBER(12) NOT NULL, " +
						"modif_date timestamp DEFAULT NULL) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_res_node','" + v_sql + "')}";
				final CallableStatement ocs = c.prepareCall(sql);
				ocs.execute();
				ocs.close();
			}

			//			t3 = System.currentTimeMillis();

			/// Pour le filtrage de la structure
			if (dbserveur.equals("mysql")) {
				sql = "CREATE TEMPORARY TABLE t_struc(" +
						"node_order int(12) NOT NULL, " +
						"new_uuid binary(16) NOT NULL, " +
						"uuid binary(16) UNIQUE NOT NULL, " +
						"node_parent_uuid binary(16) NOT NULL, " +
						"t_level INT) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")) {
				final String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_struc(" +
						"node_order NUMBER(12) NOT NULL, " +
						"new_uuid VARCHAR2(32) NOT NULL, " +
						"uuid VARCHAR2(32) NOT NULL, " +
						"node_parent_uuid VARCHAR2(32), " +
						"t_level NUMBER(10,0)" +
						",  CONSTRAINT t_struc_UK_uuid UNIQUE (uuid)) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_struc','" + v_sql + "')}";
				final CallableStatement ocs = c.prepareCall(sql);
				ocs.execute();
				ocs.close();
			}

			//			t4 = System.currentTimeMillis();

			// En double car on ne peut pas faire d'update/select d'une meme table temporaire
			if (dbserveur.equals("mysql")) {
				sql = "CREATE TEMPORARY TABLE t_struc_2(" +
						"node_order int(12) NOT NULL, " +
						"new_uuid binary(16) NOT NULL, " +
						"uuid binary(16) UNIQUE NOT NULL, " +
						"node_parent_uuid binary(16) NOT NULL, " +
						"t_level INT) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")) {
				final String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_struc_2(" +
						"node_order NUMBER(12) NOT NULL, " +
						"new_uuid VARCHAR2(32) NOT NULL, " +
						"uuid VARCHAR2(32) NOT NULL, " +
						"node_parent_uuid VARCHAR2(32), " +
						"t_level NUMBER(10,0)" +
						",  CONSTRAINT t_struc_2_UK_uuid UNIQUE (uuid)) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_struc_2','" + v_sql + "')}";
				final CallableStatement ocs = c.prepareCall(sql);
				ocs.execute();
				ocs.close();
			}

			//			t5 = System.currentTimeMillis();

			//			t6 = System.currentTimeMillis();

			/// Dans la table temporaire on retrouve les noeuds concernes
			/// (assure une convergence de la recursion et limite le nombre de lignes dans la recherche)
			/// Init table
			sql = "INSERT INTO t_struc(node_order, new_uuid, uuid, node_parent_uuid, t_level) " +
					"SELECT d.node_order, d.new_uuid, d.node_uuid, uuid2bin(?), 0 " +
					"FROM t_data_node d " +
					"WHERE d.node_uuid=uuid2bin(?)";
			st = c.prepareStatement(sql);
			st.setString(1, destUuid); // Pour le branchement avec la structure de destination
			st.setString(2, baseUuid);
			st.executeUpdate();
			st.close();

			//			t7 = System.currentTimeMillis();

			/// On boucle, sera toujours <= e "nombre de noeud du portfolio"
			int level = 0;
			int added = 1;
			if (dbserveur.equals("mysql")) {
				sql = "INSERT IGNORE INTO t_struc_2(node_order, new_uuid, uuid, node_parent_uuid, t_level) ";
			} else if (dbserveur.equals("oracle")) {
				sql = "INSERT /*+ ignore_row_on_dupkey_index(t_struc_2,t_struc_2_UK_uuid)*/ INTO t_struc_2(node_order, new_uuid, uuid, node_parent_uuid, t_level) ";
			}
			sql += "SELECT d.node_order, d.new_uuid, d.node_uuid, d.node_parent_uuid, ? " +
					"FROM t_data_node d WHERE d.node_parent_uuid IN (SELECT uuid FROM t_struc t " +
					"WHERE t.t_level=?)";

			String sqlTemp = null;
			if (dbserveur.equals("mysql")) {
				sqlTemp = "INSERT IGNORE INTO t_struc SELECT * FROM t_struc_2;";
			} else if (dbserveur.equals("oracle")) {
				sqlTemp = "INSERT /*+ ignore_row_on_dupkey_index(t_struc,t_struc_UK_uuid)*/ INTO t_struc SELECT * FROM t_struc_2";
			}
			final PreparedStatement stTemp = c.prepareStatement(sqlTemp);

			st = c.prepareStatement(sql);
			while (added != 0) {
				st.setInt(1, level + 1);
				st.setInt(2, level);
				st.executeUpdate();
				added = stTemp.executeUpdate(); // On s'arrete quand rien e ete ajoute
				level = level + 1; // Prochaine etape
			}
			st.close();
			stTemp.close();

			//			t8 = System.currentTimeMillis();

			/// On retire les elements null, ea pose probleme par la suite
			if (dbserveur.equals("mysql")) {
				sql = "DELETE FROM t_struc WHERE new_uuid=0x0000000000000000000000000000000";
			} else if (dbserveur.equals("oracle")) {
				sql = "DELETE FROM t_struc WHERE new_uuid='00000000000000000000000000000000'";
			}
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			//			t9 = System.currentTimeMillis();

			/// On filtre les donnees dont on a pas besoin
			sql = "DELETE FROM t_data_node WHERE node_uuid NOT IN (SELECT uuid FROM t_struc)";
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			//			t10 = System.currentTimeMillis();

			///// FIXME TODO: Verifier les droits sur les donnees restantes

			/// Copie des donnees non partages (shared=0)
			sql = "INSERT INTO t_res_node(new_uuid, node_uuid, xsi_type, user_id, modif_user_id, modif_date) ";
			if (dbserveur.equals("mysql")) {
				sql += "SELECT uuid2bin(UUID()), ";
			} else if (dbserveur.equals("oracle")) {
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

			/// Resolution des nouveaux uuid avec les parents
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

			/// Mise e jour du parent de la nouvelle copie ainsi que l'ordre
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

			// Mise e jour de l'appartenance au portfolio de destination
			sql = "UPDATE t_data_node " +
					"SET portfolio_id=(SELECT portfolio_id FROM node WHERE node_uuid=uuid2bin(?))";
			st = c.prepareStatement(sql);
			st.setString(1, destUuid);
			st.executeUpdate();
			st.close();

			/// Mise e jour de l'appartenance des donnees
			sql = "UPDATE t_data_node " + "SET modif_user_id=?";
			st = c.prepareStatement(sql);
			st.setInt(1, userId);
			st.executeUpdate();
			st.close();

			sql = "UPDATE t_res_node " + "SET modif_user_id=?";
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

			/// Mise e jour de la liste des enfants
			if (dbserveur.equals("mysql")) {
				sql = "UPDATE node d, (" +
						"SELECT p.node_parent_uuid, " +
						"GROUP_CONCAT(bin2uuid(p.new_uuid) ORDER BY p.node_order) AS value " +
						"FROM t_data_node p GROUP BY p.node_parent_uuid) tmp " +
						"SET d.node_children_uuid=tmp.value " +
						"WHERE tmp.node_parent_uuid=d.node_uuid";
			} else if (dbserveur.equals("oracle")) {
				sql = "UPDATE node d SET d.node_children_uuid=(SELECT value FROM (SELECT p.node_parent_uuid, LISTAGG(bin2uuid(p.new_uuid), ',') WITHIN GROUP (ORDER BY p.node_order) AS value FROM t_data_node p GROUP BY p.node_parent_uuid) tmp WHERE tmp.node_parent_uuid=d.node_uuid) WHERE EXISTS (SELECT 1 FROM t_data_node WHERE node_parent_uuid=d.node_uuid)";
			}
			st = c.prepareStatement(sql);
			st.execute();
			st.close();

			//			t20 = System.currentTimeMillis();

			/// Ajout de l'enfant dans la structure originelle
			if (dbserveur.equals("mysql")) {
				sql = "UPDATE node n1, (" +
						"SELECT GROUP_CONCAT(bin2uuid(n2.node_uuid) ORDER BY n2.node_order) AS value " +
						"FROM node n2 " +
						"WHERE n2.node_parent_uuid=uuid2bin(?) " +
						"GROUP BY n2.node_parent_uuid) tmp " +
						"SET n1.node_children_uuid=tmp.value " +
						"WHERE n1.node_uuid=uuid2bin(?)";
			} else if (dbserveur.equals("oracle")) {
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
					"WHERE n.node_uuid=uuid2bin(?)) g," + // Retrouve les groupes de destination via le noeud de destination
					"(SELECT gri.label, s.new_uuid, gr.RD, gr.WR, gr.DL, gr.SB, gr.AD, gr.types_id, gr.rules_id " +
					"FROM t_struc s, group_rights gr, group_right_info gri " +
					"WHERE s.uuid=gr.id AND gr.grid=gri.grid) r " + // Prend la liste des droits actuel des noeuds dupliques
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

			/// On recupere le uuid cree
			sql = "SELECT bin2uuid(new_uuid) FROM t_data_node WHERE node_uuid=uuid2bin(?)";
			st = c.prepareStatement(sql);
			st.setString(1, baseUuid);
			res = st.executeQuery();
			if (res.next()) {
				createdUuid = res.getString(1);
			}
			res.close();
			st.close();
		} catch (final Exception e) {
			try {
				createdUuid = "erreur: " + e.getMessage();
				if (!c.getAutoCommit()) {
					c.rollback();
				}
			} catch (final SQLException e1) {
				e1.printStackTrace();
			}
			e.printStackTrace();
		} finally {
			try {
				c.setAutoCommit(true);
				// Les 'pooled connection' ne se ferment pas vraiment. On nettoie manuellement les tables temporaires...
				if (dbserveur.equals("mysql")) {
					sql = "DROP TEMPORARY TABLE IF EXISTS t_data_node, t_res_node, t_struc, t_struc_2";
					st = c.prepareStatement(sql);
					st.execute();
					st.close();
				}

				touchPortfolio(c, destUuid, null);
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}

		return createdUuid;
	}

	@Override
	public Object postCopyPortfolio(Connection c, MimeType inMimeType, String portfolioUuid, String srcCode,
			String newCode, int userId, boolean setOwner) throws Exception {
		String sql;
		PreparedStatement st;
		String newPortfolioUuid = UUID.randomUUID().toString();

		try {
			/// Find source code
			if (srcCode != null) {
				/// Find back portfolio uuid from source code
				sql = "SELECT bin2uuid(portfolio_id) AS uuid FROM node WHERE code=?";
				st = c.prepareStatement(sql);
				st.setString(1, srcCode);
				final ResultSet res = st.executeQuery();
				if (res.next()) {
					portfolioUuid = res.getString("uuid");
				}
			}

			if (portfolioUuid == null) {
				return "Error: no portofolio selected";
			}

			///// Creation des tables temporaires
			/// Pour la copie de la structure
			if (dbserveur.equals("mysql")) {
				sql = "CREATE TEMPORARY TABLE t_data(" +
						"new_uuid binary(16) NOT NULL, " + /// Pour la copie d'une nouvelle structure
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
						"semtag varchar(100) DEFAULT NULL, " +
						"semantictag varchar(100) DEFAULT NULL, " +
						"label varchar(100)  DEFAULT NULL, " +
						"code varchar(255)  DEFAULT NULL, " +
						"descr varchar(100)  DEFAULT NULL, " +
						"format varchar(30) DEFAULT NULL, " +
						"modif_user_id int(12) NOT NULL, " +
						"modif_date timestamp NULL DEFAULT NULL, " +
						"portfolio_id binary(16) DEFAULT NULL) DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")) {
				final String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_data(" +
						"new_uuid VARCHAR2(32) NOT NULL, " + /// Pour la copie d'une nouvelle structure
						"node_uuid VARCHAR2(32)  NOT NULL, " +
						"node_parent_uuid VARCHAR2(32) DEFAULT NULL, " +
						"node_children_uuid CLOB, " +
						"node_order NUMBER(12) NOT NULL, " +
						"metadata CLOB DEFAULT NULL, " +
						"metadata_wad CLOB DEFAULT NULL, " +
						"metadata_epm CLOB DEFAULT NULL, " +
						"res_node_uuid VARCHAR2(32) DEFAULT NULL, " +
						"res_res_node_uuid VARCHAR2(32) DEFAULT NULL, " +
						"res_context_node_uuid VARCHAR2(32)  DEFAULT NULL, " +
						"shared_res NUMBER(1) NOT NULL, " +
						"shared_node NUMBER(1) NOT NULL, " +
						"shared_node_res NUMBER(1) NOT NULL, " +
						"shared_res_uuid VARCHAR2(32) DEFAULT NULL, " +
						"shared_node_uuid VARCHAR2(32) DEFAULT NULL, " +
						"shared_node_res_uuid VARCHAR2(32) DEFAULT NULL, " +
						"asm_type VARCHAR2(50 CHAR) DEFAULT NULL, " +
						"xsi_type VARCHAR2(50 CHAR)  DEFAULT NULL, " +
						"semtag VARCHAR2(100 CHAR) DEFAULT NULL, " +
						"semantictag VARCHAR2(100 CHAR) DEFAULT NULL, " +
						"label VARCHAR2(100 CHAR)  DEFAULT NULL, " +
						"code VARCHAR2(255 CHAR)  DEFAULT NULL, " +
						"descr VARCHAR2(100 CHAR)  DEFAULT NULL, " +
						"format VARCHAR2(30 CHAR) DEFAULT NULL, " +
						"modif_user_id NUMBER(12) NOT NULL, " +
						"modif_date timestamp DEFAULT NULL, " +
						"portfolio_id VARCHAR2(32) DEFAULT NULL) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_data','" + v_sql + "')}";
				final CallableStatement ocs = c.prepareCall(sql);
				ocs.execute();
				ocs.close();
			}

			/// Pour la copie des donnees
			if (dbserveur.equals("mysql")) {
				sql = "CREATE TEMPORARY TABLE t_res(" +
						"new_uuid binary(16) NOT NULL, " + /// Pour la copie d'une nouvelle structure
						"node_uuid binary(16) NOT NULL, " +
						"xsi_type varchar(50) NOT NULL, " +
						"content text, " +
						"user_id int(11) DEFAULT NULL, " +
						"modif_user_id int(12) NOT NULL, " +
						"modif_date timestamp NULL DEFAULT NULL) DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")) {
				final String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_res(" +
						"new_uuid VARCHAR2(32) NOT NULL, " + /// Pour la copie d'une nouvelle structure
						"node_uuid VARCHAR2(32) NOT NULL, " +
						"xsi_type VARCHAR2(50 CHAR) DEFAULT NULL, " +
						"content CLOB, " +
						"user_id NUMBER(11) DEFAULT NULL, " +
						"modif_user_id NUMBER(12) NOT NULL, " +
						"modif_date timestamp DEFAULT NULL) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_res','" + v_sql + "')}";
				final CallableStatement ocs = c.prepareCall(sql);
				ocs.execute();
				ocs.close();
			}

			/// Pour la mise e jour de la liste des enfants/parents
			if (dbserveur.equals("mysql")) {
				sql = "CREATE TEMPORARY TABLE t_struc(" +
						"node_order int(12) NOT NULL, " +
						"new_uuid binary(16) NOT NULL, " +
						"uuid binary(16) UNIQUE NOT NULL, " +
						"node_parent_uuid binary(16), " +
						"t_level INT) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")) {
				final String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_struc(" +
						"node_order NUMBER(12) NOT NULL, " +
						"new_uuid VARCHAR2(32) NOT NULL, " +
						"uuid VARCHAR2(32) NOT NULL, " +
						"node_parent_uuid VARCHAR2(32), " +
						"t_level NUMBER(10,0)" +
						",  CONSTRAINT t_struc_UK_uuid UNIQUE (uuid)) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_struc','" + v_sql + "')}";
				final CallableStatement ocs = c.prepareCall(sql);
				ocs.execute();
				ocs.close();
			}

			/////////
			/// Copie de la structure
			sql = "INSERT INTO t_data(new_uuid, node_uuid, node_parent_uuid, node_children_uuid, node_order, metadata, metadata_wad, metadata_epm, res_node_uuid, res_res_node_uuid, res_context_node_uuid , shared_res, shared_node, shared_node_res, shared_res_uuid, shared_node_uuid, shared_node_res_uuid, asm_type, xsi_type, semtag, semantictag, label, code, descr, format, modif_user_id, modif_date, portfolio_id) ";
			if (dbserveur.equals("mysql")) {
				sql += "SELECT uuid2bin(UUID()), ";
			} else if (dbserveur.equals("oracle")) {
				sql += "SELECT sys_guid(), ";
			}
			sql += "node_uuid, node_parent_uuid, node_children_uuid, node_order, metadata, metadata_wad, metadata_epm, res_node_uuid, res_res_node_uuid, res_context_node_uuid , shared_res, shared_node, shared_node_res, shared_res_uuid, shared_node_uuid, shared_node_res_uuid, asm_type, xsi_type, semtag, semantictag, label, code, descr, format, ?, modif_date, portfolio_id " +
					"FROM node n " +
					"WHERE portfolio_id=uuid2bin(?)";
			st = c.prepareStatement(sql);
			if (setOwner) {
				st.setInt(1, userId);
			} else {
				st.setInt(1, 1); // FIXME hard-coded root userid
			}
			st.setString(2, portfolioUuid);
			st.executeUpdate();
			st.close();

			/// Copie les uuid pour la resolution des parents/enfants
			sql = "INSERT INTO t_struc(node_order, new_uuid, uuid, node_parent_uuid) " +
					"SELECT node_order, new_uuid, node_uuid, node_parent_uuid FROM t_data";
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			/// Copie des ressources
			sql = "INSERT INTO t_res(new_uuid, node_uuid, xsi_type, content, user_id, modif_user_id, modif_date) ";
			if (dbserveur.equals("mysql")) {
				sql += "SELECT uuid2bin(UUID()), ";
			} else if (dbserveur.equals("oracle")) {
				sql += "SELECT sys_guid(), ";
			}
			sql += "r.node_uuid, r.xsi_type, r.content, r.user_id, r.modif_user_id, r.modif_date " +
					"FROM t_data d " +
					"LEFT JOIN resource_table r ON d.res_node_uuid=r.node_uuid ";
			if (dbserveur.equals("mysql")) {
				sql += "WHERE d.res_node_uuid <> 0x0000000000000000000000000000000"; // Binaire non null
			} else if (dbserveur.equals("oracle")) {
				sql += "WHERE d.res_node_uuid <> '00000000000000000000000000000000'"; // Binaire non null
			}
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			sql = "INSERT INTO t_res(new_uuid, node_uuid, xsi_type, content, user_id, modif_user_id, modif_date) ";
			if (dbserveur.equals("mysql")) {
				sql += "SELECT uuid2bin(UUID()), ";
			} else if (dbserveur.equals("oracle")) {
				sql += "SELECT sys_guid(), ";
			}
			sql += "r.node_uuid, r.xsi_type, r.content, r.user_id, r.modif_user_id, r.modif_date " +
					"FROM t_data d " +
					"LEFT JOIN resource_table r ON d.res_context_node_uuid=r.node_uuid ";
			if (dbserveur.equals("mysql")) {
				sql += "WHERE d.res_context_node_uuid <> 0x0000000000000000000000000000000"; // Binaire non null
			} else if (dbserveur.equals("oracle")) {
				sql += "WHERE d.res_context_node_uuid <> '00000000000000000000000000000000'"; // Binaire non null
			}
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			// nodeRes
			sql = "INSERT INTO t_res(new_uuid, node_uuid, xsi_type, content, user_id, modif_user_id, modif_date) ";
			if (dbserveur.equals("mysql")) {
				sql += "SELECT uuid2bin(UUID()), ";
			} else if (dbserveur.equals("oracle")) {
				sql += "SELECT sys_guid(), ";
			}
			sql += "r.node_uuid, r.xsi_type, r.content, r.user_id, r.modif_user_id, r.modif_date " +
					"FROM t_data d " +
					"LEFT JOIN resource_table r ON d.res_res_node_uuid=r.node_uuid ";
			if (dbserveur.equals("mysql")) {
				sql += "WHERE d.res_res_node_uuid <> 0x0000000000000000000000000000000"; // Binaire non null
			} else if (dbserveur.equals("oracle")) {
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

			/// Resolution des nouveaux uuid avec les parents
			// Avec la structure
			sql = "UPDATE t_data t " +
					"SET t.node_parent_uuid = (SELECT new_uuid FROM t_struc s WHERE s.uuid=t.node_parent_uuid)";
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			// Avec les ressources (et droits des ressources)
			if (dbserveur.equals("mysql")) {
				sql = "UPDATE t_data d INNER JOIN t_res r ON d.res_node_uuid=r.node_uuid " +
						"SET d.res_node_uuid=r.new_uuid";
			} else if (dbserveur.equals("oracle")) {
				sql = "UPDATE t_data d SET d.res_node_uuid=(SELECT r.new_uuid FROM t_res r WHERE d.res_node_uuid=r.node_uuid) WHERE EXISTS (SELECT 1 FROM t_res r WHERE d.res_node_uuid=r.node_uuid)";
			}
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			if (dbserveur.equals("mysql")) {
				sql = "UPDATE t_data d INNER JOIN t_res r ON d.res_res_node_uuid=r.node_uuid " +
						"SET d.res_res_node_uuid=r.new_uuid";
			} else if (dbserveur.equals("oracle")) {
				sql = "UPDATE t_data d SET d.res_res_node_uuid=(SELECT r.new_uuid FROM t_res r WHERE d.res_res_node_uuid=r.node_uuid) WHERE EXISTS (SELECT 1 FROM t_res r WHERE d.res_res_node_uuid=r.node_uuid)";
			}
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			if (dbserveur.equals("mysql")) {
				sql = "UPDATE t_data d INNER JOIN t_res r ON d.res_context_node_uuid=r.node_uuid " +
						"SET d.res_context_node_uuid=r.new_uuid";
			} else if (dbserveur.equals("oracle")) {
				sql = "UPDATE t_data d SET d.res_context_node_uuid=(SELECT r.new_uuid FROM t_res r WHERE d.res_context_node_uuid=r.node_uuid) WHERE EXISTS (SELECT 1 FROM t_res r WHERE d.res_context_node_uuid=r.node_uuid)";
			}
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			/// Mise e jour de la liste des enfants (! requete particuliere)
			/// L'ordre determine le rendu visuel final du xml
			if (dbserveur.equals("mysql")) {
				sql = "UPDATE t_data d, (" +
						"SELECT node_parent_uuid, GROUP_CONCAT(bin2uuid(s.new_uuid) ORDER BY s.node_order) AS value " +
						"FROM t_struc s GROUP BY s.node_parent_uuid) tmp " +
						"SET d.node_children_uuid=tmp.value " +
						"WHERE tmp.node_parent_uuid=d.node_uuid";
			} else if (dbserveur.equals("oracle")) {
				sql = "UPDATE t_data d SET d.node_children_uuid=(SELECT value FROM (SELECT node_parent_uuid, LISTAGG(bin2uuid(s.new_uuid), ',') WITHIN GROUP (ORDER BY s.node_order) AS value FROM t_struc s GROUP BY s.node_parent_uuid) tmp WHERE tmp.node_parent_uuid=d.node_uuid) WHERE EXISTS (SELECT 1 FROM t_struc WHERE node_parent_uuid=d.node_uuid)";
			}
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			// Mise e jour du code dans le contenu du noeud
			if (dbserveur.equals("mysql")) {
				sql = "UPDATE t_data d " +
						"LEFT JOIN t_res r ON d.res_res_node_uuid=r.new_uuid " + // Il faut utiliser le nouveau uuid
						"SET r.content=REPLACE(r.content, d.code, ?) " +
						"WHERE d.asm_type='asmRoot'";
			} else if (dbserveur.equals("oracle")) {
				sql = "UPDATE t_res r SET r.content=(SELECT REPLACE(r2.content, d.code, ?) FROM t_data d LEFT JOIN t_res r2 ON d.res_res_node_uuid=r2.new_uuid WHERE d.asm_type='asmRoot') WHERE EXISTS (SELECT 1 FROM t_data d WHERE d.res_res_node_uuid=r.new_uuid AND d.asm_type='asmRoot')";
			}
			st = c.prepareStatement(sql);
			st.setString(1, newCode);
			st.executeUpdate();
			st.close();

			// Mise e jour du code dans le code interne de la BD
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

			/// Finalement on cree un rele designer
			int groupid = postCreateRole(c, newPortfolioUuid, "designer", userId);

			/// Ajoute la personne dans ce groupe
			putUserGroup(c, Integer.toString(groupid), Integer.toString(userId));

			/// Force 'all' role creation
			groupid = postCreateRole(c, newPortfolioUuid, "all", userId);

			/// Check base portfolio's public state and act accordingly
			if (cred.isPublic(c, null, portfolioUuid)) {
				setPublicState(c, userId, newPortfolioUuid, true);
			}

		} catch (final Exception e) {
			try {
				newPortfolioUuid = "erreur: " + e.getMessage();
				if (!c.getAutoCommit()) {
					c.rollback();
				}
			} catch (final SQLException e1) {
				e1.printStackTrace();
			}
			e.printStackTrace();
		} finally {
			try {
				c.setAutoCommit(true);
				// Les 'pooled connection' ne se ferment pas vraiment. On nettoie manuellement les tables temporaires...
				if (dbserveur.equals("mysql")) {
					sql = "DROP TEMPORARY TABLE IF EXISTS t_data, t_res, t_struc";
					st = c.prepareStatement(sql);
					st.execute();
					st.close();
				}
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}

		return newPortfolioUuid;
	}

	@Override
	public int postCreateRole(Connection c, String portfolioUuid, String role, int userId) {
		int groupid = 0;
		String rootNodeUuid = "";
		try {
			rootNodeUuid = getPortfolioRootNode(c, portfolioUuid);
		} catch (final SQLException e2) {
			e2.printStackTrace();
		}

		if (!cred.isAdmin(c, userId) && !cred.isDesigner(c, userId, rootNodeUuid) && !cred.isCreator(c, userId)) {
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");
		}

		String sql;
		PreparedStatement st = null;
		ResultSet rs = null;

		try {
			// Verifie si le rele existe pour ce portfolio
			sql = "SELECT gi.gid FROM group_right_info gri " +
					"LEFT JOIN group_info gi ON gri.grid=gi.grid " +
					"WHERE portfolio_id=uuid2bin(?) AND gri.label=?";
			st = c.prepareStatement(sql);
			st.setString(1, portfolioUuid);
			st.setString(2, role);
			rs = st.executeQuery();

			if (rs.next()) // On le retourne directement
			{
				groupid = rs.getInt(1);
			} else {
				c.setAutoCommit(false);

				// Cree le rele
				sql = "INSERT INTO group_right_info(portfolio_id, label, owner) VALUES(uuid2bin(?),?,?)";
				st = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
				if (dbserveur.equals("oracle")) {
					st = c.prepareStatement(sql, new String[] { "grid" });
				}
				st.setString(1, portfolioUuid);
				st.setString(2, role);
				st.setInt(3, 1);

				st.executeUpdate();
				ResultSet key = st.getGeneratedKeys();
				int grid;
				if (key.next()) {
					grid = key.getInt(1);

					st.close();
					sql = "INSERT INTO group_info(grid, owner, label) VALUES(?,?,?)";
					st = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
					if (dbserveur.equals("oracle")) {
						st = c.prepareStatement(sql, new String[] { "gid" });
					}
					st.setInt(1, grid);
					st.setInt(2, 1);
					st.setString(3, role);

					st.executeUpdate();
					key = st.getGeneratedKeys();
					if (key.next()) {
						groupid = key.getInt(1);
					}
				} else {
					c.rollback();
				}
			}
		} catch (final Exception ex) {
			try {
				c.rollback();
			} catch (final SQLException e1) {
				e1.printStackTrace();
			}
			ex.printStackTrace();
		} finally {
			try {
				c.setAutoCommit(true);
				if (rs != null) {
					rs.close();
				}
				if (st != null) {
					st.close();
				}
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}

		return groupid;
	}

	@Override
	public String[] postCredentialFromXml(Connection c, Integer userId, String username, String password,
			String substitute) throws ServletException, IOException {
		String sql;
		ResultSet rs;
		PreparedStatement stmt;
		String[] returnValue = null;
		int uid;
		int subuid = 0;
		try {
			// Does user have a valid account?
			sql = "SELECT userid, login FROM credential WHERE login=? AND password=UNHEX(SHA1(?))";
			if (dbserveur.equals("oracle")) {
				sql = "SELECT userid, login FROM credential WHERE login=? AND password=crypt(?)";
			}
			stmt = c.prepareStatement(sql);
			stmt.setString(1, username);
			stmt.setString(2, password);
			rs = stmt.executeQuery();

			if (!rs.next()) {
				return returnValue;
			}
			uid = rs.getInt(1);

			if (null != substitute) {
				/// Specific lenient substitution rule
				sql = "SELECT cs.id " +
						"FROM credential_substitution cs " +
						"WHERE cs.userid=? AND cs.id=? AND cs.type=?";
				stmt = c.prepareStatement(sql);
				stmt.setInt(1, uid);
				stmt.setInt(2, 0); // 0 -> Any account, specific otherwise
				stmt.setString(3, "USER");
				rs = stmt.executeQuery();

				if (rs.next()) // User can get "any" account, except admin one
				{
					sql = "SELECT c.userid " + "FROM credential c " + "WHERE c.login=? AND is_admin=0";
					stmt = c.prepareStatement(sql);
					stmt.setString(1, substitute);
					rs = stmt.executeQuery();

					if (rs.next()) {
						subuid = rs.getInt(1);
					}
				} else {
					/// General rule, when something specific is written in 'id', with USER or GROUP
					sql = "SELECT c.userid " +
							"FROM credential c, credential_substitution cs " +
							"WHERE c.userid=cs.id AND c.login=? AND cs.userid=? AND cs.type='USER' " + // As specific user
							"UNION " +
							"SELECT c.userid " +
							"FROM credential c, credential_substitution cs, group_user gu " +
							"WHERE c.userid=gu.userid AND gu.gid=cs.id AND c.login=? AND cs.userid=? AND cs.type='GROUP'"; // Anybody in group
					stmt = c.prepareStatement(sql);
					stmt.setString(1, substitute);
					stmt.setInt(2, uid);
					stmt.setString(3, substitute);
					stmt.setInt(4, uid);
					rs = stmt.executeQuery();

					if (rs.next()) {
						subuid = rs.getInt(1);
					}
				}
			}

			ResultSet res;

			returnValue = new String[5];
			returnValue[1] = username; // Username
			returnValue[2] = Integer.toString(uid); // User id
			returnValue[4] = Integer.toString(subuid); // Substitute
			if (subuid != 0) {
				returnValue[3] = substitute;
				res = getMySqlUserByLogin(c, substitute);
			} else {
				returnValue[3] = "";
				res = getMySqlUserByLogin(c, username);
			}

			final String credential = "<credential>" +
					DomUtils.getXmlElementOutput("useridentifier", res.getString("login")) +
					DomUtils.getXmlElementOutput("token", res.getString("token")) +
					DomUtils.getXmlElementOutput("firstname", res.getString("display_firstname")) +
					DomUtils.getXmlElementOutput("lastname", res.getString("display_lastname")) +
					DomUtils.getXmlElementOutput("admin", res.getString("is_admin")) +
					DomUtils.getXmlElementOutput("designer", res.getString("is_designer")) +
					DomUtils.getXmlElementOutput("email", res.getString("email")) +
					DomUtils.getXmlElementOutput("other", res.getString("other")) +
					"</credential>";
			returnValue[0] = credential;
		} catch (final Exception e) {
			e.printStackTrace();
		}

		return returnValue;
	}

	@Override
	public Object postGroup(Connection c, String in, int userId) throws Exception {
		if (!cred.isAdmin(c, userId)) {
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");
		}

		String result;
		Integer grid = 0;
		int owner = 0;
		String label = null;

		//On prepare les requetes SQL
		PreparedStatement stInsert;
		String sqlInsert;

		//On recupere le body
		final Document doc = DomUtils.xmlString2Document(in, new StringBuilder());
		final Element etu = doc.getDocumentElement();

		//On verifie le bon format
		if (etu.getNodeName().equals("group")) {
			//On recupere les attributs
			try {
				if (etu.getAttributes().getNamedItem("grid") != null) {
					grid = Integer.parseInt(etu.getAttributes().getNamedItem("grid").getNodeValue());
				} else {
					grid = null;
				}
			} catch (final Exception ex) {
				logger.error("Exception", ex);
			}

			try {
				if (etu.getAttributes().getNamedItem("owner") != null) {
					owner = Integer.parseInt(etu.getAttributes().getNamedItem("owner").getNodeValue());
					if (owner == 0) {
						owner = userId;
					}
				} else {
					owner = userId;
				}
			} catch (final Exception ex) {
				logger.error("Exception", ex);
			}

			try {
				if (etu.getAttributes().getNamedItem("label") != null) {
					label = etu.getAttributes().getNamedItem("label").getNodeValue();
				}
			} catch (final Exception ex) {
				logger.error("Exception", ex);
			}

		} else {
			result = "Erreur lors de la recuperation des attributs du groupe dans le XML";
		}

		if (grid == null) {
			return "";
		}

		//On ajoute le groupe dans la base de donnees
		sqlInsert = "REPLACE INTO group_info(grid, owner, label) VALUES (?, ?, ?)";
		if (dbserveur.equals("oracle")) {
			sqlInsert = "MERGE INTO group_info d using (SELECT ? grid,? owner,? label from dual) s ON (1=2) WHEN NOT MATCHED THEN INSERT (d.grid, d.owner, d.label) values (s.grid, s.owner, s.label)";
		}
		stInsert = c.prepareStatement(sqlInsert);
		stInsert.setInt(1, grid);
		stInsert.setInt(2, owner);
		stInsert.setString(3, label);
		stInsert.executeUpdate();

		//On renvoie le body pour qu'il soit stocke dans le log
		result = "<group ";
		result += DomUtils.getXmlAttributeOutputInt("grid", grid) + " ";
		result += DomUtils.getXmlAttributeOutputInt("owner", owner) + " ";
		result += DomUtils.getXmlAttributeOutput("label", label) + " ";
		result += ">";
		result += "</group>";

		return result;
	}

	@Override
	public boolean postGroupsUsers(Connection c, int user, int userId, int groupId) {
		PreparedStatement stInsert;
		String sqlInsert;

		if (!cred.isAdmin(c, user)) {
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");
		}

		try {
			sqlInsert = "REPLACE INTO group_user(gid, userid) VALUES (?, ?)";
			if (dbserveur.equals("oracle")) {
				sqlInsert = "MERGE INTO group_user d using (SELECT ? gid,? userid FROM DUAL) s ON (d.gid=s.gid AND d.userid=s.userid) WHEN NOT MATCHED THEN INSERT (d.gid, d.userid) VALUES (s.gid, s.userid)";
			}
			stInsert = c.prepareStatement(sqlInsert);
			stInsert.setInt(1, groupId);
			stInsert.setInt(2, userId);
			stInsert.executeUpdate();
			return true;
		} catch (final Exception ex) {
			ex.printStackTrace();
			return false;
		}
	}

	@Override
	public Object postImportNode(Connection c, MimeType inMimeType, String destUuid, String tag, String code,
			String srcuuid, int userId, int groupId) throws Exception {
		if ("".equals(tag) || tag == null || "".equals(code) || code == null) {
			if (srcuuid == null || "".equals(srcuuid)) {
				return "erreur";
			}
		}

		String sql;
		PreparedStatement st;
		String createdUuid = "erreur";

		final long start = System.currentTimeMillis();
		long t1 = 0;
		long t1e = 0;
		long t2 = 0;
		long t3 = 0;
		long t4 = 0;
		long t5 = 0;
		long t6 = 0;
		long t7 = 0;
		long t8 = 0;
		long t9 = 0;
		long t10 = 0;
		long t11 = 0;
		long t12 = 0;
		long t13 = 0;
		long t14 = 0;
		long t15 = 0;
		long t16 = 0;
		long t17 = 0;
		long t18 = 0;
		long t19 = 0;
		long t20 = 0;
		long t21 = 0;
		long t22 = 0, t23 = 0;
		long end = 0;

		try {
			/// If we have a uuid specified
			String portfolioCode = "";
			if (srcuuid != null) {
				// Check if user has right to read it
				if (!cred.hasNodeRight(c, userId, groupId, srcuuid, Credential.READ)) {
					return "No rights";
				}
			} else {
				/// Check/update cache
				portfolioCode = checkCache(c, code);

				if (portfolioCode == null) {
					return "Inexistent selection";
				}
			}

			///// Creation des tables temporaires
			/// Pour la copie de la structure
			if (dbserveur.equals("mysql")) {
				sql = "CREATE TEMPORARY TABLE IF NOT EXISTS t_data_node(" +
						"new_uuid binary(16) NOT NULL, " + /// Pour la copie d'une nouvelle structure
						"node_uuid binary(16)  NOT NULL, " +
						"node_parent_uuid binary(16) DEFAULT NULL, " +
						"node_order int(12) NOT NULL, " +
						//						"metadata_wad varchar(2798) NOT NULL, " +
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
						"semtag varchar(100) DEFAULT NULL, " +
						"semantictag varchar(100) DEFAULT NULL, " +
						"label varchar(100)  DEFAULT NULL, " +
						"code varchar(255)  DEFAULT NULL, " +
						"descr varchar(100)  DEFAULT NULL, " +
						"format varchar(30) DEFAULT NULL, " +
						"modif_user_id int(12) NOT NULL, " +
						"modif_date timestamp NULL DEFAULT NULL, " +
						"portfolio_id binary(16) DEFAULT NULL) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")) {
				final String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_data_node(" +
						"new_uuid VARCHAR2(32) NOT NULL, " + /// Pour la copie d'une nouvelle structure
						"node_uuid VARCHAR2(32)  NOT NULL, " +
						"node_parent_uuid VARCHAR2(32) DEFAULT NULL, " +
						"node_order NUMBER(12) NOT NULL, " +
						//						"metadata_wad VARCHAR2(2798 CHAR) DEFAULT NULL, " +
						"res_node_uuid VARCHAR2(32) DEFAULT NULL, " +
						"res_res_node_uuid VARCHAR2(32) DEFAULT NULL, " +
						"res_context_node_uuid VARCHAR2(32)  DEFAULT NULL, " +
						"shared_res NUMBER(1) NOT NULL, " +
						"shared_node NUMBER(1) NOT NULL, " +
						"shared_node_res NUMBER(1) NOT NULL, " +
						"shared_res_uuid VARCHAR2(32) DEFAULT NULL, " +
						"shared_node_uuid VARCHAR2(32) DEFAULT NULL, " +
						"shared_node_res_uuid VARCHAR2(32) DEFAULT NULL, " +
						"asm_type VARCHAR2(50 CHAR) DEFAULT NULL, " +
						"xsi_type VARCHAR2(50 CHAR)  DEFAULT NULL, " +
						"semtag VARCHAR2(100 CHAR) DEFAULT NULL, " +
						"semantictag VARCHAR2(100 CHAR) DEFAULT NULL, " +
						"label VARCHAR2(100 CHAR)  DEFAULT NULL, " +
						"code VARCHAR2(255 CHAR)  DEFAULT NULL, " +
						"descr VARCHAR2(100 CHAR)  DEFAULT NULL, " +
						"format VARCHAR2(30 CHAR) DEFAULT NULL, " +
						"modif_user_id NUMBER(12) NOT NULL, " +
						"modif_date timestamp DEFAULT NULL, " +
						"portfolio_id VARCHAR2(32) DEFAULT NULL) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_data_node','" + v_sql + "')}";
				final CallableStatement ocs = c.prepareCall(sql);
				ocs.execute();
				ocs.close();
			}

			t1 = System.currentTimeMillis();

			// If we have uuid, copy portfolio from uuid to local cache
			String baseUuid;
			ResultSet res;
			if (srcuuid != null) // Since we're not sure if those nodes have to be cached, do it from general table
			{
				sql = "INSERT INTO t_data_node ";
				if (dbserveur.equals("mysql")) {
					sql += "SELECT uuid2bin(UUID()), ";
				} else if (dbserveur.equals("oracle")) {
					sql += "SELECT sys_guid(), ";
				}
				//				sql += "node_uuid, node_parent_uuid, node_order, metadata_wad, res_node_uuid, res_res_node_uuid, res_context_node_uuid, shared_res, shared_node, shared_node_res, shared_res_uuid, shared_node_uuid, shared_node_res_uuid, asm_type, xsi_type, semtag, semantictag, label, code, descr, format, modif_user_id, modif_date, portfolio_id " +
				sql += "node_uuid, node_parent_uuid, node_order, res_node_uuid, res_res_node_uuid, res_context_node_uuid, shared_res, shared_node, shared_node_res, shared_res_uuid, shared_node_uuid, shared_node_res_uuid, asm_type, xsi_type, semtag, semantictag, label, code, descr, format, modif_user_id, modif_date, portfolio_id " +
						"FROM node n " +
						"WHERE n.portfolio_id=(SELECT portfolio_id FROM node n1 WHERE n1.node_uuid=uuid2bin(?))";
				st = c.prepareStatement(sql);
				st.setString(1, srcuuid);
				st.executeUpdate();
				st.close();

				// Then skip tag searching since we know the uuid
				baseUuid = srcuuid;
			} else {
				// Copie the whole portfolio from shared cache to local cache
				sql = "INSERT INTO t_data_node ";
				if (dbserveur.equals("mysql")) {
					sql += "SELECT uuid2bin(UUID()), ";
				} else if (dbserveur.equals("oracle")) {
					sql += "SELECT sys_guid(), ";
				}
				//				sql += "node_uuid, node_parent_uuid, node_order, metadata_wad, res_node_uuid, res_res_node_uuid, res_context_node_uuid, shared_res, shared_node, shared_node_res, shared_res_uuid, shared_node_uuid, shared_node_res_uuid, asm_type, xsi_type, semtag, semantictag, label, code, descr, format, modif_user_id, modif_date, portfolio_id " +
				sql += "node_uuid, node_parent_uuid, node_order, res_node_uuid, res_res_node_uuid, res_context_node_uuid, shared_res, shared_node, shared_node_res, shared_res_uuid, shared_node_uuid, shared_node_res_uuid, asm_type, xsi_type, semtag, semantictag, label, code, descr, format, modif_user_id, modif_date, portfolio_id " +
						"FROM t_node_cache n " +
						"WHERE n.portfolio_id=uuid2bin(?)";
				st = c.prepareStatement(sql);
				st.setString(1, portfolioCode);
				st.executeUpdate();
				st.close();

				/// Try if the tag sent exist in code
				sql = "SELECT bin2uuid(n2.node_uuid) AS nUuid, bin2uuid(n2.portfolio_id) AS pUuid " +
						"FROM t_data_node n2 " +
						"WHERE n2.code=? AND n2.portfolio_id=uuid2bin(?)";
				st = c.prepareStatement(sql);
				st.setString(1, tag);
				st.setString(2, portfolioCode);

				res = st.executeQuery();
				if (res.next()) // Take the first one declared
				{
					baseUuid = res.getString("nUuid");
					res.getString("pUuid");
					res.close();
					st.close();
				} else // Otherwise it's with semantictag, maybe
				{
					/// Find the right starting node we want
					sql = "SELECT bin2uuid(n2.node_uuid) AS nUuid, bin2uuid(n2.portfolio_id) AS pUuid " +
							"FROM t_data_node n2 " +
							"WHERE n2.semantictag = ? AND n2.portfolio_id=uuid2bin(?)";
					st = c.prepareStatement(sql);
					st.setString(1, tag);
					st.setString(2, portfolioCode);

					res = st.executeQuery();
					if (!res.next()) {
						res.close();
						st.close();
						return "Selection non existante.";
					}
					baseUuid = res.getString("nUuid");
					res.getString("pUuid");
					res.close();
					st.close();
				}
			}

			t1e = System.currentTimeMillis();

			t2 = System.currentTimeMillis();

			/// Pour la copie des donnees
			if (dbserveur.equals("mysql")) {
				sql = "CREATE TEMPORARY TABLE t_res_node(" +
						"new_uuid binary(16) NOT NULL, " + /// Pour la copie d'une nouvelle structure
						"node_uuid binary(16) NOT NULL, " +
						"xsi_type varchar(50) DEFAULT NULL, " +
						"user_id int(11) DEFAULT NULL, " +
						"modif_user_id int(12) NOT NULL, " +
						"modif_date timestamp NULL DEFAULT NULL) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")) {
				final String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_res_node(" +
						"new_uuid VARCHAR2(32) NOT NULL, " + /// Pour la copie d'une nouvelle structure
						"node_uuid VARCHAR2(32) NOT NULL, " +
						"xsi_type VARCHAR2(50 CHAR) DEFAULT NULL, " +
						"user_id NUMBER(11) DEFAULT NULL, " +
						"modif_user_id NUMBER(12) NOT NULL, " +
						"modif_date timestamp DEFAULT NULL) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_res_node','" + v_sql + "')}";
				final CallableStatement ocs = c.prepareCall(sql);
				ocs.execute();
				ocs.close();
			}

			t3 = System.currentTimeMillis();

			/// Pour le filtrage de la structure
			if (dbserveur.equals("mysql")) {
				sql = "CREATE TEMPORARY TABLE t_struc(" +
						"node_order int(12) NOT NULL, " +
						"new_uuid binary(16) NOT NULL, " +
						"uuid binary(16) UNIQUE NOT NULL, " +
						"node_parent_uuid binary(16) NOT NULL, " +
						"t_level INT) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")) {
				final String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_struc(" +
						"node_order NUMBER(12) NOT NULL, " +
						"new_uuid VARCHAR2(32) NOT NULL, " +
						"uuid VARCHAR2(32) NOT NULL, " +
						"node_parent_uuid VARCHAR2(32), " +
						"t_level NUMBER(10,0)" +
						",  CONSTRAINT t_struc_UK_uuid UNIQUE (uuid)) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_struc','" + v_sql + "')}";
				final CallableStatement ocs = c.prepareCall(sql);
				ocs.execute();
				ocs.close();
			}

			t4 = System.currentTimeMillis();

			// En double car on ne peut pas faire d'update/select d'une meme table temporaire
			if (dbserveur.equals("mysql")) {
				sql = "CREATE TEMPORARY TABLE t_struc_2(" +
						"node_order int(12) NOT NULL, " +
						"new_uuid binary(16) NOT NULL, " +
						"uuid binary(16) UNIQUE NOT NULL, " +
						"node_parent_uuid binary(16) NOT NULL, " +
						"t_level INT) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")) {
				final String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_struc_2(" +
						"node_order NUMBER(12) NOT NULL, " +
						"new_uuid VARCHAR2(32) NOT NULL, " +
						"uuid VARCHAR2(32) NOT NULL, " +
						"node_parent_uuid VARCHAR2(32), " +
						"t_level NUMBER(10,0)" +
						",  CONSTRAINT t_struc_2_UK_uuid UNIQUE (uuid)) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_struc_2','" + v_sql + "')}";
				final CallableStatement ocs = c.prepareCall(sql);
				ocs.execute();
				ocs.close();
			}

			t5 = System.currentTimeMillis();

			t6 = System.currentTimeMillis();

			/// Dans la table temporaire on retrouve les noeuds concernes
			/// (assure une convergence de la recursion et limite le nombre de lignes dans la recherche)
			/// Init table
			sql = "INSERT INTO t_struc(node_order, new_uuid, uuid, node_parent_uuid, t_level) " +
					"SELECT d.node_order, d.new_uuid, d.node_uuid, uuid2bin(?), 0 " +
					"FROM t_data_node d " +
					"WHERE d.node_uuid=uuid2bin(?)";
			st = c.prepareStatement(sql);
			st.setString(1, destUuid); // Pour le branchement avec la structure de destination
			st.setString(2, baseUuid);
			st.executeUpdate();
			st.close();

			t7 = System.currentTimeMillis();

			/// On boucle, sera toujours <= e "nombre de noeud du portfolio"
			int level = 0;
			int added = 1;
			if (dbserveur.equals("mysql")) {
				sql = "INSERT IGNORE INTO t_struc_2(node_order, new_uuid, uuid, node_parent_uuid, t_level) ";
			} else if (dbserveur.equals("oracle")) {
				sql = "INSERT /*+ ignore_row_on_dupkey_index(t_struc_2,t_struc_2_UK_uuid)*/ INTO t_struc_2(node_order, new_uuid, uuid, node_parent_uuid, t_level) ";
			}
			sql += "SELECT d.node_order, d.new_uuid, d.node_uuid, d.node_parent_uuid, ? " +
					"FROM t_data_node d WHERE d.node_parent_uuid IN (SELECT uuid FROM t_struc t " +
					"WHERE t.t_level=?)";

			String sqlTemp = null;
			if (dbserveur.equals("mysql")) {
				sqlTemp = "INSERT IGNORE INTO t_struc SELECT * FROM t_struc_2;";
			} else if (dbserveur.equals("oracle")) {
				sqlTemp = "INSERT /*+ ignore_row_on_dupkey_index(t_struc,t_struc_UK_uuid)*/ INTO t_struc SELECT * FROM t_struc_2";
			}
			final PreparedStatement stTemp = c.prepareStatement(sqlTemp);

			st = c.prepareStatement(sql);
			while (added != 0) {
				st.setInt(1, level + 1);
				st.setInt(2, level);
				st.executeUpdate();
				added = stTemp.executeUpdate(); // On s'arrete quand rien e ete ajoute
				level = level + 1; // Prochaine etape
			}
			st.close();
			stTemp.close();

			t8 = System.currentTimeMillis();

			/// On retire les elements null, ea pose probleme par la suite
			if (dbserveur.equals("mysql")) {
				sql = "DELETE FROM t_struc WHERE new_uuid=0x0000000000000000000000000000000";
			} else if (dbserveur.equals("oracle")) {
				sql = "DELETE FROM t_struc WHERE new_uuid='00000000000000000000000000000000'";
			}
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			t9 = System.currentTimeMillis();

			/// On filtre les donnees dont on a pas besoin
			sql = "DELETE FROM t_data_node WHERE node_uuid NOT IN (SELECT uuid FROM t_struc)";
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			t10 = System.currentTimeMillis();

			///// FIXME TODO: Verifier les droits sur les donnees restantes

			/// Copie des donnees non partages (shared=0)
			sql = "INSERT INTO t_res_node(new_uuid, node_uuid, xsi_type, user_id, modif_user_id, modif_date) ";
			if (dbserveur.equals("mysql")) {
				sql += "SELECT uuid2bin(UUID()), ";
			} else if (dbserveur.equals("oracle")) {
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

			/// Resolution des nouveaux uuid avec les parents
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

			/// Mise e jour du parent de la nouvelle copie ainsi que l'ordre
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

			// Mise e jour de l'appartenance au portfolio de destination
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

			String login = "";
			if (res.next()) {
				login = res.getString("login");
				res.close();
				st.close();
			}

			//// Temp rights table
			if (dbserveur.equals("mysql")) {
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
			} else if (dbserveur.equals("oracle")) {
				final String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_group_right_info(" +
						"grid NUMBER(19,0) NOT NULL, " +
						"owner NUMBER(19,0) NOT NULL, " +
						"label VARCHAR2(255 CHAR) DEFAULT NULL, " +
						"change_rights NUMBER(1) NOT NULL, " +
						"portfolio_id VARCHAR2(32) NOT NULL, " +
						"CONSTRAINT t_group_right_info_UK_id UNIQUE (grid)) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_group_right_info','" + v_sql + "')}";
				final CallableStatement ocs = c.prepareCall(sql);
				ocs.execute();
				ocs.close();
			}

			/// Copy current roles for easier referencing
			sql = "INSERT INTO t_group_right_info " +
					"SELECT * FROM group_right_info WHERE portfolio_id=(" +
					"SELECT n.portfolio_id FROM node n WHERE n.node_uuid=uuid2bin(?))";
			st = c.prepareStatement(sql);
			st.setString(1, destUuid); /// TODO: Might want to have the destination portfolio id
			final int hasGroup = st.executeUpdate();
			st.close();

			/// Managing rights
			if (hasGroup > 0) {
				if (dbserveur.equals("mysql")) {
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
				} else if (dbserveur.equals("oracle")) {
					final String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_group_rights(" +
							"grid NUMBER(19,0) NOT NULL, " +
							"id VARCHAR2(32) NOT NULL, " +
							"RD NUMBER(1) NOT NULL, " +
							"WR NUMBER(1) NOT NULL, " +
							"DL NUMBER(1) NOT NULL, " +
							"SB NUMBER(1) NOT NULL, " +
							"AD NUMBER(1) NOT NULL, " +
							"types_id VARCHAR2(2000 CHAR) DEFAULT NULL, " +
							"rules_id VARCHAR2(2000 CHAR) DEFAULT NULL, " +
							"notify_roles clob DEFAULT NULL, " +
							"CONSTRAINT t_group_rights_UK_id UNIQUE (id)) ON COMMIT PRESERVE ROWS";
					sql = "{call create_or_empty_table('t_group_rights','" + v_sql + "')}";
					final CallableStatement ocs = c.prepareCall(sql);
					ocs.execute();
					ocs.close();
				}

				/// FIXME: Would be better to parse all and insert in one go
				/// Prepare statement
				// Horrible
				String sqlUpdateNoRD = "INSERT INTO t_group_rights(grid,id, RD) VALUES((SELECT grid FROM t_group_right_info WHERE label=?), uuid2bin(?), 0) ON DUPLICATE KEY UPDATE RD = 0 ";
				if (dbserveur.equals("oracle")) {
					sqlUpdateNoRD = "MERGE INTO t_group_rights d USING (SELECT (SELECT grid FROM t_group_right_info WHERE label=?) AS grid, uuid2bin(?) AS id, 0 AS RD) t ON (d.grid=t.grid AND d.id=t.id)  WHEN MATCHED THEN UPDATE SET d.RD=0 WHEN NOT MATCHED THEN INSERT (grid, id, RD) VALUES (t.grid, t.id, t.RD)";
				}
				final PreparedStatement stNoRD = c.prepareStatement(sqlUpdateNoRD);

				String sqlUpdateRD = "INSERT INTO t_group_rights(grid,id, RD) VALUES((SELECT grid FROM t_group_right_info WHERE label=?), uuid2bin(?), 1) ON DUPLICATE KEY UPDATE RD = 1 ";
				if (dbserveur.equals("oracle")) {
					sqlUpdateRD = "MERGE INTO t_group_rights d USING (SELECT (SELECT grid FROM t_group_right_info WHERE label=?) AS grid, uuid2bin(?) AS id, 1 AS RD) t ON (d.grid=t.grid AND d.id=t.id)  WHEN MATCHED THEN UPDATE SET d.RD=1 WHEN NOT MATCHED THEN INSERT (grid, id, RD) VALUES (t.grid, t.id, t.RD)";
				}
				final PreparedStatement stRD = c.prepareStatement(sqlUpdateRD);

				String sqlUpdateWR = "INSERT INTO t_group_rights(grid,id, WR, RD) VALUES((SELECT grid FROM t_group_right_info WHERE label=?), uuid2bin(?), 1, 0) ON DUPLICATE KEY UPDATE WR = 1";
				if (dbserveur.equals("oracle")) {
					sqlUpdateWR = "MERGE INTO t_group_rights d USING (SELECT (SELECT grid FROM t_group_right_info WHERE label=?) AS grid, uuid2bin(?) AS id, 1 AS WR, 0 AS RD) t ON (d.grid=t.grid AND d.id=t.id)  WHEN MATCHED THEN UPDATE SET d.WR=1 WHEN NOT MATCHED THEN INSERT (grid, id, WR, RD) VALUES (t.grid, t.id, t.WR, t.RD)";
				}
				final PreparedStatement stWR = c.prepareStatement(sqlUpdateWR);

				String sqlUpdateDL = "INSERT INTO t_group_rights(grid,id, DL, RD) VALUES((SELECT grid FROM t_group_right_info WHERE label=?), uuid2bin(?), 1, 0) ON DUPLICATE KEY UPDATE DL = 1";
				if (dbserveur.equals("oracle")) {
					sqlUpdateDL = "MERGE INTO t_group_rights d USING (SELECT (SELECT grid FROM t_group_right_info WHERE label=?) AS grid, uuid2bin(?) AS id, 1 AS DL, 0 AS RD) t ON (d.grid=t.grid AND d.id=t.id)  WHEN MATCHED THEN UPDATE SET d.DL=1 WHEN NOT MATCHED THEN INSERT (grid, id, DL, RD) VALUES (t.grid, t.id, t.DL, t.RD)";
				}
				final PreparedStatement stDL = c.prepareStatement(sqlUpdateDL);

				String sqlUpdateSB = "INSERT INTO t_group_rights(grid,id, SB, RD) VALUES((SELECT grid FROM t_group_right_info WHERE label=?), uuid2bin(?), 1, 0) ON DUPLICATE KEY UPDATE SB = 1";
				if (dbserveur.equals("oracle")) {
					sqlUpdateSB = "MERGE INTO t_group_rights d USING (SELECT (SELECT grid FROM t_group_right_info WHERE label=?) AS grid, uuid2bin(?) AS id, 1 AS SB, 0 AS RD) t ON (d.grid=t.grid AND d.id=t.id)  WHEN MATCHED THEN UPDATE SET d.SB=1 WHEN NOT MATCHED THEN INSERT (grid, id, SB, RD) VALUES (t.grid, t.id, t.SB, t.RD)";
				}
				final PreparedStatement stSB = c.prepareStatement(sqlUpdateSB);

				//////// Copy metadata_wad since it's needed in a specific manipulation
				//// Actual column is TEXT which can't be put in memory
				if (dbserveur.equals("mysql")) {
					sql = "CREATE TEMPORARY TABLE t_meta(" +
							"new_uuid binary(16) UNIQUE NOT NULL, " +
							"portfolio_id binary(16) NOT NULL, " +
							"metadata TEXT NOT NULL," +
							"metadata_wad TEXT NOT NULL," +
							"metadata_epm TEXT NOT NULL" +
							" ) DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
					st = c.prepareStatement(sql);
					st.execute();
					st.close();
				} else if (dbserveur.equals("oracle")) {
					final String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_meta(" +
							"new_uuid VARCHAR2(32) NOT NULL, " +
							"portfolio_id VARCHAR2(32) NOT NULL, " +
							"metadata CLOB NOT NULL " +
							"metadata_wad CLOB NOT NULL " +
							"metadata_epm CLOB NOT NULL " +
							",  CONSTRAINT t_struc_2_UK_uuid UNIQUE (new_uuid)) ON COMMIT PRESERVE ROWS";
					sql = "{call create_or_empty_table('t_meta','" + v_sql + "')}";
					final CallableStatement ocs = c.prepareCall(sql);
					ocs.execute();
					ocs.close();
				}

				/// Put metadata_wad inside a separate temp table that will be written on disk
				/// Only manipulation related to it ('user' case) will have impact
				sql = "INSERT INTO t_meta (new_uuid, portfolio_id, metadata, metadata_wad, metadata_epm) " +
						"SELECT t.new_uuid, t.portfolio_id, n.metadata, n.metadata_wad, n.metadata_epm " +
						"FROM t_data_node t, node n WHERE t.node_uuid=n.node_uuid";
				st = c.prepareStatement(sql);
				st.executeUpdate();
				st.close();

				// Selection des metadonnees
				sql = "SELECT bin2uuid(t.new_uuid) AS uuid, bin2uuid(t.portfolio_id) AS puuid, t.metadata_wad " +
						"FROM t_meta t";
				st = c.prepareStatement(sql);
				res = st.executeQuery();

				t18 = System.currentTimeMillis();

				/// Loop through metadata and assemble rights
				while (res.next()) {
					final String uuid = res.getString("uuid");
					final String portfolioUuid = res.getString("puuid");
					// Process et remplacement de 'user' par la personne en cours
					String meta = res.getString("metadata_wad");

					final Matcher matcher = PATTERN_ONLYUSER.matcher(meta);
					if (matcher.find()) {
						meta = meta.replaceAll(ONLYUSER, login);

						/// Replace metadata with actual username
						sql = "UPDATE t_meta t SET t.metadata_wad=? WHERE t.new_uuid=uuid2bin(?)";
						st = c.prepareStatement(sql);
						st.setString(1, meta);
						st.setString(2, uuid);
						st.executeUpdate();
						st.close();

						/// Ensure specific user group exist in final tables, and add user in it
						final int ngid = getRoleByNode(c, 1, destUuid, login);
						postGroupsUsers(c, 1, userId, ngid);

						/// Ensure entry is there in temp table, just need a skeleton info
						sql = "REPLACE INTO t_group_right_info(grid, owner, label) VALUES(?, 1, ?)";
						if (dbserveur.equals("oracle")) {
							// FIXME Unsure about this, might need testing
							sql = "MERGE INTO group_info d using (SELECT ? grid,1 ,? label from dual) s ON (1=2) WHEN NOT MATCHED THEN INSERT (d.grid, d.owner, d.label) values (s.grid, s.owner, s.label)";
						}
						st = c.prepareStatement(sql);
						st.setInt(1, ngid);
						st.setString(2, login);
						st.executeUpdate();
						st.close();
					}

					final String nodeString = "<?xml version='1.0' encoding='UTF-8' standalone='no'?><transfer " +
							meta +
							"></transfer>";
					try {
						/// Ensure we can parse it correctly
						DocumentBuilder documentBuilder;
						final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
						documentBuilder = documentBuilderFactory.newDocumentBuilder();
						final InputSource is = new InputSource(new StringReader(nodeString));
						final Document doc = documentBuilder.parse(is);

						/// Process attributes
						final Element attribNode = doc.getDocumentElement();
						final NamedNodeMap attribMap = attribNode.getAttributes();

						/// FIXME: e ameliorer pour faciliter le changement des droits
						String nodeRole;
						Node att = attribMap.getNamedItem("access");
						//if (att != null) {
						//if(access.equalsIgnoreCase("public") || access.contains("public"))
						//	credential.postGroupRight("all",uuid,Credential.READ,portfolioUuid,userId);
						//}
						att = attribMap.getNamedItem("seenoderoles");
						if (att != null) {
							final StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
							while (tokens.hasMoreElements()) {
								nodeRole = tokens.nextElement().toString();
								stRD.setString(1, nodeRole);
								stRD.setString(2, uuid);
								try {
									stRD.executeUpdate();
								} catch (final Exception e) {
									logger.error("Role '" +
											nodeRole +
											"' might not exist in destination portfolio. (seenoderoles)");
								}
							}
						}
						att = attribMap.getNamedItem("showtoroles");
						if (att != null) {
							final StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
							while (tokens.hasMoreElements()) {
								nodeRole = tokens.nextElement().toString();
								stNoRD.setString(1, nodeRole);
								stNoRD.setString(2, uuid);
								try {
									stNoRD.executeUpdate();
								} catch (final Exception e) {
									logger.error("Role '" +
											nodeRole +
											"' might not exist in destination portfolio. (showtoroles)");
								}
							}
						}
						att = attribMap.getNamedItem("delnoderoles");
						if (att != null) {
							final StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
							while (tokens.hasMoreElements()) {
								nodeRole = tokens.nextElement().toString();
								stDL.setString(1, nodeRole);
								stDL.setString(2, uuid);
								try {
									stDL.executeUpdate();
								} catch (final Exception e) {
									logger.error("Role '" +
											nodeRole +
											"' might not exist in destination portfolio. (delroles)");
								}

							}
						}
						att = attribMap.getNamedItem("editnoderoles");
						if (att != null) {
							final StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
							while (tokens.hasMoreElements()) {
								nodeRole = tokens.nextElement().toString();
								stWR.setString(1, nodeRole);
								stWR.setString(2, uuid);
								try {
									stWR.executeUpdate();
								} catch (final Exception e) {
									logger.error("Role '" +
											nodeRole +
											"' might not exist in destination portfolio. (editnoderoles)");
								}
							}
						}
						att = attribMap.getNamedItem("submitroles");
						if (att != null) {
							final StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
							while (tokens.hasMoreElements()) {
								nodeRole = tokens.nextElement().toString();
								stSB.setString(1, nodeRole);
								stSB.setString(2, uuid);
								try {
									stSB.executeUpdate();
								} catch (final Exception e) {
									logger.error("Role '" +
											nodeRole +
											"' might not exist in destination portfolio. (submitroles)");
								}
							}
						}
						//					/*
						att = attribMap.getNamedItem("seeresroles");
						if (att != null) {
							final StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
							while (tokens.hasMoreElements()) {
								nodeRole = tokens.nextElement().toString();
								cred.postGroupRight(c, nodeRole, uuid, Credential.READ, portfolioUuid, userId);
							}
						}
						att = attribMap.getNamedItem("delresroles");
						if (att != null) {
							final StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
							while (tokens.hasMoreElements()) {
								nodeRole = tokens.nextElement().toString();
								cred.postGroupRight(c, nodeRole, uuid, Credential.DELETE, portfolioUuid, userId);
							}
						}
						att = attribMap.getNamedItem("editresroles");
						if (att != null) {
							final StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
							while (tokens.hasMoreElements()) {
								nodeRole = tokens.nextElement().toString();
								stWR.setString(1, nodeRole);
								stWR.setString(2, uuid);
								try {
									stWR.executeUpdate();
								} catch (final Exception e) {
									logger.error("Role '" +
											nodeRole +
											"' might not exist in destination portfolio. (editresroles)");
								}
							}
						}
						att = attribMap.getNamedItem("submitresroles");
						if (att != null) {
							final StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
							while (tokens.hasMoreElements()) {
								nodeRole = tokens.nextElement().toString();
								cred.postGroupRight(c, nodeRole, uuid, Credential.SUBMIT, portfolioUuid, userId);
							}
						}
						//*/
						/// FIXME: Incomplete
						/// FIXME: Incomplete
						final Node menuroles = attribMap.getNamedItem("menuroles");
						if (menuroles != null) {
							/// Pour les differents items du menu
							final StringTokenizer menuline = new StringTokenizer(menuroles.getNodeValue(), ";");

							while (menuline.hasMoreTokens()) {
								final String line = menuline.nextToken();

								/// New format is an xml
								final Matcher roleMatcher = ROLE_PATTERN.matcher(line);
								String menurolename = null;
								if (roleMatcher.find()) {
									menurolename = roleMatcher.group(1);
								}

								/// Keeping old format for compatibility
								if (menurolename == null) {
									/// Format pour l'instant: code_portfolio,tag_semantique,label@en/libelle@fr,reles[;autre menu]
									final String[] tokens = line.split(",");
									if (tokens.length == 4) {
										menurolename = tokens[3];
									}
								}

								if (menurolename != null) {
									// Break down list of roles
									final String[] roles = menurolename.split(" ");
									for (final String role : roles) {
										// Ensure roles exists
										postCreateRole(c, portfolioCode, role, 1);
									}
								}
							}
						}
						final Node actionroles = attribMap.getNamedItem("actionroles");
						if (actionroles != null) {
							/// Format pour l'instant: actionroles="sender:1,2;responsable:4"
							final StringTokenizer tokens = new StringTokenizer(actionroles.getNodeValue(), ";");
							while (tokens.hasMoreElements()) {
								nodeRole = tokens.nextElement().toString();
								final StringTokenizer data = new StringTokenizer(nodeRole, ":");
								final String role = data.nextElement().toString();
								final String actions = data.nextElement().toString();
								cred.postGroupRight(c, role, uuid, actions, portfolioUuid, userId);
							}
						}

						final Node notifyroles = attribMap.getNamedItem("notifyroles");
						if (notifyroles != null) {
							/// Format pour l'instant: actionroles="sender:1,2;responsable:4"
							final StringTokenizer tokens = new StringTokenizer(notifyroles.getNodeValue(), " ");
							StringBuilder merge = new StringBuilder();
							if (tokens.hasMoreElements()) {
								merge = new StringBuilder(tokens.nextElement().toString());
							}
							while (tokens.hasMoreElements()) {
								merge.append(",").append(tokens.nextElement().toString());
							}
							postNotifyRoles(c, userId, portfolioUuid, uuid, merge.toString());
						}

						/// FIXME? Not sure why importing a node should check if the portfolio is public or not
						/*
						meta = res.getString("metadata");
						nodeString = "<?xml version='1.0' encoding='UTF-8' standalone='no'?><transfer "+meta+"/>";
						is = new InputSource(new StringReader(nodeString));
						doc = documentBuilder.parse(is);
						attribNode = doc.getDocumentElement();
						attribMap = attribNode.getAttributes();
						
						
						try
						{
							Node publicatt = attribMap.getNamedItem("public");
							if( publicatt != null && DB_YES.equals(publicatt.getNodeValue()) )
								setPublicState(c, userId, portfolioUuid, true);
							else if ( DN_NO.equals(publicatt) )
								setPublicState(c, userId, portfolioUuid, false);
						}
						catch(Exception ex)
						{
							ex.printStackTrace();
						}
						//*/

					} catch (final Exception e) {
						logger.error("Error when working on rights: " + e.getMessage());
						e.printStackTrace();
					}
				}
				stNoRD.close();
				stRD.close();
				stWR.close();
				stDL.close();
				stSB.close();
				res.close();
				st.close();
			} /// End of rights management

			t19 = System.currentTimeMillis();

			/// On copie tout dans les vrai tables
			c.setAutoCommit(false);

			/// Structure, Join because the TEXT fields are copied from the base nodes
			sql = "INSERT INTO node(node_uuid, node_parent_uuid, node_order, metadata, metadata_wad, metadata_epm, res_node_uuid, res_res_node_uuid, res_context_node_uuid, shared_res, shared_node, shared_node_res, shared_res_uuid, shared_node_uuid, shared_node_res_uuid, asm_type, xsi_type, semtag, semantictag, label, code, descr, format, modif_user_id, modif_date, portfolio_id) " +
					"SELECT t.new_uuid, t.node_parent_uuid, t.node_order, tm.metadata, tm.metadata_wad, tm.metadata_epm, t.res_node_uuid, t.res_res_node_uuid, t.res_context_node_uuid, t.shared_res, t.shared_node, t.shared_node_res, t.shared_res_uuid, t.shared_node_uuid, t.shared_node_res_uuid, t.asm_type, t.xsi_type, t.semtag, t.semantictag, t.label, t.code, t.descr, t.format, t.modif_user_id, t.modif_date, t.portfolio_id " +
					"FROM t_data_node t, t_meta tm WHERE t.new_uuid=tm.new_uuid";
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
			/// Mise e jour de la liste des enfants
			if (dbserveur.equals("mysql")) {
				sql = "UPDATE node d, (" +
						"SELECT p.node_parent_uuid, " +
						"GROUP_CONCAT(bin2uuid(p.new_uuid) ORDER BY p.node_order) AS value " +
						"FROM t_data_node p GROUP BY p.node_parent_uuid) tmp " +
						"SET d.node_children_uuid=tmp.value " +
						"WHERE tmp.node_parent_uuid=d.node_uuid";
			} else if (dbserveur.equals("oracle")) {
				sql = "UPDATE node d SET d.node_children_uuid=(SELECT value FROM (SELECT p.node_parent_uuid, LISTAGG(bin2uuid(p.new_uuid), ',') WITHIN GROUP (ORDER BY p.node_order) AS value FROM t_data_node p GROUP BY p.node_parent_uuid) tmp WHERE tmp.node_parent_uuid=d.node_uuid) WHERE EXISTS (SELECT 1 FROM t_data_node WHERE node_parent_uuid=d.node_uuid)";
			}
			st = c.prepareStatement(sql);
			st.execute();
			st.close();

			t22 = System.currentTimeMillis();

			/// Ajout de l'enfant dans la structure originelle
			if (dbserveur.equals("mysql")) {
				sql = "UPDATE node n1, (" +
						"SELECT GROUP_CONCAT(bin2uuid(n2.node_uuid) ORDER BY n2.node_order) AS value " +
						"FROM node n2 " +
						"WHERE n2.node_parent_uuid=uuid2bin(?) " +
						"GROUP BY n2.node_parent_uuid) tmp " +
						"SET n1.node_children_uuid=tmp.value " +
						"WHERE n1.node_uuid=uuid2bin(?)";
			} else if (dbserveur.equals("oracle")) {
				sql = "UPDATE node SET node_children_uuid=(SELECT LISTAGG(bin2uuid(n2.node_uuid), ',') WITHIN GROUP (ORDER BY n2.node_order) AS value FROM node n2 WHERE n2.node_parent_uuid=uuid2bin(?) GROUP BY n2.node_parent_uuid) WHERE node_uuid=uuid2bin(?)";
			}
			st = c.prepareStatement(sql);
			st.setString(1, destUuid);
			st.setString(2, destUuid);
			st.executeUpdate();
			st.close();

			t23 = System.currentTimeMillis();

			end = System.currentTimeMillis();

			/// On recupere le uuid cree
			sql = "SELECT bin2uuid(new_uuid) FROM t_data_node WHERE node_uuid=uuid2bin(?)";
			st = c.prepareStatement(sql);
			st.setString(1, baseUuid);
			res = st.executeQuery();
			if (res.next()) {
				createdUuid = res.getString(1);
			}
			res.close();
			st.close();
		} catch (final Exception e) {
			try {
				createdUuid = "erreur: " + e.getMessage();
				if (!c.getAutoCommit()) {
					c.rollback();
				}
			} catch (final SQLException e1) {
				e1.printStackTrace();
			}
			e.printStackTrace();
		} finally {
			try {
				c.setAutoCommit(true);
				// Les 'pooled connection' ne se ferment pas vraiment. On nettoie manuellement les tables temporaires...
				if (dbserveur.equals("mysql")) {
					sql = "DROP TEMPORARY TABLE IF EXISTS t_data_node, t_group_right_info, t_group_rights, t_res_node, t_struc, t_struc_2, t_meta";
					st = c.prepareStatement(sql);
					st.execute();
					st.close();
				}

				touchPortfolio(c, destUuid, null);
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}

		if (logger.isTraceEnabled()) {
			logger.trace((t1 - start) +
					"," +
					(t1e - t1) +
					"," +
					(t2 - t1e) +
					"," +
					(t3 - t2) +
					"," +
					(t4 - t3) +
					"," +
					(t5 - t4) +
					"," +
					(t6 - t5) +
					"," +
					(t7 - t6) +
					"," +
					(t8 - t7) +
					"," +
					(t9 - t8) +
					"," +
					(t10 - t9) +
					"," +
					(t11 - t10) +
					"," +
					(t12 - t11) +
					"," +
					(t13 - t12) +
					"," +
					(t14 - t13) +
					"," +
					(t15 - t14) +
					"," +
					(t16 - t15) +
					"," +
					(t17 - t16) +
					"," +
					(t18 - t17) +
					"," +
					(t19 - t18) +
					"," +
					(t20 - t19) +
					"," +
					(t21 - t20) +
					"," +
					(t22 - t21) +
					"," +
					(t23 - t22) +
					"," +
					(end - t23));
			logger.trace("---- Import ---");
			logger.trace("d0-1: " + (t1 - start));
			logger.trace("d1: " + t1);
			logger.trace("d2: " + t2);
			logger.trace("d2-3: " + (t3 - t2));
			logger.trace("d3-4: " + (t4 - t3));
			logger.trace("d4-5: " + (t5 - t4));
			logger.trace("d5-6: " + (t6 - t5));
			logger.trace("d6-7: " + (t7 - t6));
			logger.trace("d7-8: " + (t8 - t7));
			logger.trace("d8-9: " + (t9 - t8));
			logger.trace("d9-10: " + (t10 - t9));
			logger.trace("d10-11: " + (t11 - t10));
			logger.trace("d11-12: " + (t12 - t11));
			logger.trace("d12-13: " + (t13 - t12));
			logger.trace("d13-14: " + (t14 - t13));
			logger.trace("d14-15: " + (t15 - t14));
			logger.trace("d15-16: " + (t16 - t15));
			logger.trace("d16-17: " + (t17 - t16));
			logger.trace("d17-18: " + (t18 - t17));
			logger.trace("d18-19: " + (t19 - t18));
			logger.trace("d19-20: " + (t20 - t19));
			logger.trace("d20-21: " + (t21 - t20));
			logger.trace("d21-22: " + (t22 - t21));
			logger.trace("d22-23: " + (t23 - t22));
			logger.trace("d24-23: " + (end - t23));
			logger.trace("------------------");
		}

		return createdUuid;
	}

	@Override
	public Object postInstanciatePortfolio(Connection c, MimeType inMimeType, String portfolioUuid, String srcCode,
			String newCode, int userId, int groupId, boolean copyshared, String portfGroupName, boolean setOwner)
			throws Exception {
		if (!cred.isAdmin(c, userId) && !cred.isCreator(c, userId)) {
			return "no rights";
		}

		String sql;
		PreparedStatement st;
		String newPortfolioUuid = UUID.randomUUID().toString();
		boolean setPublic = false;

		try {
			/// Find source code
			if (srcCode != null) {
				/// Find back portfolio uuid from source code
				sql = "SELECT bin2uuid(portfolio_id) AS uuid FROM node WHERE code=?";
				st = c.prepareStatement(sql);
				st.setString(1, srcCode);
				final ResultSet res = st.executeQuery();
				if (res.next()) {
					portfolioUuid = res.getString("uuid");
				} else {
					portfolioUuid = null;
				}
			}

			if (portfolioUuid == null) {
				return "";
			}

			///// Creation des tables temporaires
			/// Pour la copie de la structure
			if (dbserveur.equals("mysql")) {
				sql = "CREATE TEMPORARY TABLE t_data(" +
						"new_uuid binary(16) NOT NULL, " + /// Pour la copie d'une nouvelle structure
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
						"semtag varchar(100) DEFAULT NULL, " +
						"semantictag varchar(100) DEFAULT NULL, " +
						"label varchar(100)  DEFAULT NULL, " +
						"code varchar(255)  DEFAULT NULL, " +
						"descr varchar(100)  DEFAULT NULL, " +
						"format varchar(30) DEFAULT NULL, " +
						"modif_user_id int(12) NOT NULL, " +
						"modif_date timestamp NULL DEFAULT NULL, " +
						"portfolio_id binary(16) DEFAULT NULL) DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")) {
				final String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_data(" +
						"new_uuid VARCHAR2(32) NOT NULL, " + /// Pour la copie d'une nouvelle structure
						"node_uuid VARCHAR2(32)  NOT NULL, " +
						"node_parent_uuid VARCHAR2(32) DEFAULT NULL, " +
						"node_children_uuid CLOB, " +
						"node_order NUMBER(12) NOT NULL, " +
						"metadata CLOB DEFAULT NULL, " +
						"metadata_wad CLOB DEFAULT NULL, " +
						"metadata_epm CLOB DEFAULT NULL, " +
						"res_node_uuid VARCHAR2(32) DEFAULT NULL, " +
						"res_res_node_uuid VARCHAR2(32) DEFAULT NULL, " +
						"res_context_node_uuid VARCHAR2(32)  DEFAULT NULL, " +
						"shared_res NUMBER(1) NOT NULL, " +
						"shared_node NUMBER(1) NOT NULL, " +
						"shared_node_res NUMBER(1) NOT NULL, " +
						"shared_res_uuid VARCHAR2(32) DEFAULT NULL, " +
						"shared_node_uuid VARCHAR2(32) DEFAULT NULL, " +
						"shared_node_res_uuid VARCHAR2(32) DEFAULT NULL, " +
						"asm_type VARCHAR2(50 CHAR) DEFAULT NULL, " +
						"xsi_type VARCHAR2(50 CHAR)  DEFAULT NULL, " +
						"semtag VARCHAR2(100 CHAR) DEFAULT NULL, " +
						"semantictag VARCHAR2(100 CHAR) DEFAULT NULL, " +
						"label VARCHAR2(100 CHAR)  DEFAULT NULL, " +
						"code VARCHAR2(255 CHAR)  DEFAULT NULL, " +
						"descr VARCHAR2(100 CHAR)  DEFAULT NULL, " +
						"format VARCHAR2(30 CHAR) DEFAULT NULL, " +
						"modif_user_id NUMBER(12) NOT NULL, " +
						"modif_date timestamp DEFAULT NULL, " +
						"portfolio_id VARCHAR2(32) DEFAULT NULL) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_data','" + v_sql + "')}";
				final CallableStatement ocs = c.prepareCall(sql);
				ocs.execute();
				ocs.close();
			}

			/// Pour la copie des donnees
			if (dbserveur.equals("mysql")) {
				sql = "CREATE TEMPORARY TABLE t_res(" +
						"new_uuid binary(16) NOT NULL, " + /// Pour la copie d'une nouvelle structure
						"node_uuid binary(16) NOT NULL, " +
						"xsi_type varchar(50) NOT NULL, " +
						"content text, " +
						"user_id int(11) DEFAULT NULL, " +
						"modif_user_id int(12) NOT NULL, " +
						"modif_date timestamp NULL DEFAULT NULL) DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")) {
				final String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_res(" +
						"new_uuid VARCHAR2(32) NOT NULL, " + /// Pour la copie d'une nouvelle structure
						"node_uuid VARCHAR2(32) NOT NULL, " +
						"xsi_type VARCHAR2(50 CHAR) NOT NULL, " +
						"content CLOB, " +
						"user_id NUMBER(11) DEFAULT NULL, " +
						"modif_user_id NUMBER(12) NOT NULL, " +
						"modif_date timestamp DEFAULT NULL) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_res','" + v_sql + "')}";
				final CallableStatement ocs = c.prepareCall(sql);
				ocs.execute();
				ocs.close();
			}

			/// Pour la mise e jour de la liste des enfants/parents
			if (dbserveur.equals("mysql")) {
				sql = "CREATE TEMPORARY TABLE t_struc(" +
						"node_order int(12) NOT NULL, " +
						"new_uuid binary(16) NOT NULL, " +
						"uuid binary(16) UNIQUE NOT NULL, " +
						"node_parent_uuid binary(16), " +
						"t_level INT) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")) {
				final String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_struc(" +
						"node_order NUMBER(12) NOT NULL, " +
						"new_uuid VARCHAR2(32) NOT NULL, " +
						"uuid VARCHAR2(32) NOT NULL, " +
						"node_parent_uuid VARCHAR2(32), " +
						"t_level NUMBER(10,0)" +
						",  CONSTRAINT t_struc_UK_uuid UNIQUE (uuid)) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_struc','" + v_sql + "')}";
				final CallableStatement ocs = c.prepareCall(sql);
				ocs.execute();
				ocs.close();
			}

			/// Pour l'histoire des shared_node a filtrer
			if (dbserveur.equals("mysql")) {
				sql = "CREATE TEMPORARY TABLE t_struc_2(" +
						"node_order int(12) NOT NULL, " +
						"new_uuid binary(16) NOT NULL, " +
						"uuid binary(16) UNIQUE NOT NULL, " +
						"node_parent_uuid binary(16), " +
						"t_level INT) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")) {
				final String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_struc_2(" +
						"node_order NUMBER(12) NOT NULL, " +
						"new_uuid VARCHAR2(32) NOT NULL, " +
						"uuid VARCHAR2(32) NOT NULL, " +
						"node_parent_uuid VARCHAR2(32), " +
						"t_level NUMBER(10,0)" +
						",  CONSTRAINT t_struc_2_UK_uuid UNIQUE (uuid)) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_struc_2','" + v_sql + "')}";
				final CallableStatement ocs = c.prepareCall(sql);
				ocs.execute();
				ocs.close();
			}

			/// Pour les nouveaux ensembles de droits
			if (dbserveur.equals("mysql")) {
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
			} else if (dbserveur.equals("oracle")) {
				final String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_rights(" +
						"grid NUMBER(19,0) NOT NULL, " +
						"id VARCHAR2(32) NOT NULL, " +
						"RD NUMBER(1) NOT NULL, " +
						"WR NUMBER(1) NOT NULL, " +
						"DL NUMBER(1) NOT NULL, " +
						"SB NUMBER(1) NOT NULL, " +
						"AD NUMBER(1) NOT NULL, " +
						"types_id VARCHAR2(2000 CHAR), " +
						"rules_id VARCHAR2(2000 CHAR)) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_rights','" + v_sql + "')}";
				final CallableStatement ocs = c.prepareCall(sql);
				ocs.execute();
				ocs.close();
			}

			/// Copie de la structure
			sql = "INSERT INTO t_data(new_uuid, node_uuid, node_parent_uuid, node_children_uuid, node_order, metadata, metadata_wad, metadata_epm, res_node_uuid, res_res_node_uuid, res_context_node_uuid , shared_res, shared_node, shared_node_res, shared_res_uuid, shared_node_uuid, shared_node_res_uuid, asm_type, xsi_type, semtag, semantictag, label, code, descr, format, modif_user_id, modif_date, portfolio_id) ";
			if (dbserveur.equals("mysql")) {
				sql += "SELECT uuid2bin(UUID()), ";
			} else if (dbserveur.equals("oracle")) {
				sql += "SELECT sys_guid(), ";
			}
			sql += "node_uuid, node_parent_uuid, node_children_uuid, node_order, metadata, metadata_wad, metadata_epm, res_node_uuid, res_res_node_uuid, res_context_node_uuid , shared_res, shared_node, shared_node_res, shared_res_uuid, shared_node_uuid, shared_node_res_uuid, asm_type, xsi_type, semtag, semantictag, label, code, descr, format, ?, modif_date, portfolio_id " +
					"FROM node n " +
					"WHERE portfolio_id=uuid2bin(?)";
			st = c.prepareStatement(sql);
			st.setInt(1, userId); // User asking to instanciate a portfolio will always have the right to modify it
			st.setString(2, portfolioUuid);

			st.executeUpdate();
			st.close();

			if (!copyshared) {
				/// Liste les noeud a filtrer
				sql = "INSERT INTO t_struc(node_order, new_uuid, uuid, node_parent_uuid, t_level) " +
						"SELECT node_order, new_uuid, node_uuid, node_parent_uuid, 0 FROM t_data WHERE shared_node=1";
				st = c.prepareStatement(sql);
				st.executeUpdate();
				st.close();

				int level = 0;
				int added = 1;
				if (dbserveur.equals("mysql")) {
					sql = "INSERT IGNORE INTO t_struc_2(node_order, new_uuid, uuid, node_parent_uuid, t_level) ";
				} else if (dbserveur.equals("oracle")) {
					sql = "INSERT /*+ ignore_row_on_dupkey_index(t_struc_2,t_struc_2_UK_uuid)*/ INTO t_struc_2(node_order, new_uuid, uuid, node_parent_uuid, t_level) ";
				}
				sql += "SELECT d.node_order, d.new_uuid, d.node_uuid, d.node_parent_uuid, ? " +
						"FROM t_data d WHERE d.node_parent_uuid IN (SELECT uuid FROM t_struc t " +
						"WHERE t.t_level=?)";

				String sqlTemp = null;
				if (dbserveur.equals("mysql")) {
					sqlTemp = "INSERT IGNORE INTO t_struc SELECT * FROM t_struc_2;";
				} else if (dbserveur.equals("oracle")) {
					sqlTemp = "INSERT /*+ ignore_row_on_dupkey_index(t_struc,t_struc_UK_uuid)*/ INTO t_struc SELECT * FROM t_struc_2";
				}
				final PreparedStatement stTemp = c.prepareStatement(sqlTemp);

				st = c.prepareStatement(sql);
				while (added != 0) {
					st.setInt(1, level + 1);
					st.setInt(2, level);
					st.executeUpdate();
					added = stTemp.executeUpdate(); // On s'arrete quand rien e ete ajoute
					level = level + 1; // Prochaine etape
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

			/// Copie les uuid pour la resolution des parents/enfants
			sql = "INSERT INTO t_struc(node_order, new_uuid, uuid, node_parent_uuid) " +
					"SELECT node_order, new_uuid, node_uuid, node_parent_uuid FROM t_data";
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			if (!copyshared) {
				/// Cas special pour shared_node=1
				// Le temps qu'on refasse la liste des enfants, on va enlever le noeud plus tard
				sql = "UPDATE t_data SET shared_node_uuid=node_uuid WHERE shared_node=1";
				st = c.prepareStatement(sql);
				st.executeUpdate();
				st.close();

				// Met a jour t_struc pour la redirection. C'est pour la list des enfants
				// FIXME: A verifier les appels qui modifie la liste des enfants.
				if (dbserveur.equals("mysql")) {
					sql = "UPDATE t_struc s INNER JOIN t_data d ON s.uuid=d.node_uuid " +
							"SET s.new_uuid=d.node_uuid WHERE d.shared_node=1";
				} else if (dbserveur.equals("oracle")) {
					sql = "UPDATE t_struc s SET s.new_uuid=(SELECT d.node_uuid FROM t_struc s2 INNER JOIN t_data d ON s2.uuid=d.node_uuid WHERE d.shared_node=1) WHERE EXISTS (SELECT 1 FROM t_struc s2 INNER JOIN t_data d ON s2.uuid=d.node_uuid WHERE d.shared_node=1)";
				}
				st = c.prepareStatement(sql);
				st.executeUpdate();
				st.close();
			}

			/// Copie des donnees non partages (shared=0)
			// Specific
			sql = "INSERT INTO t_res(new_uuid, node_uuid, xsi_type, content, user_id, modif_user_id, modif_date) ";
			if (dbserveur.equals("mysql")) {
				sql += "SELECT uuid2bin(UUID()), ";
			} else if (dbserveur.equals("oracle")) {
				sql += "SELECT sys_guid(), ";
			}
			sql += "r.node_uuid, r.xsi_type, r.content, r.user_id, r.modif_user_id, r.modif_date " +
					"FROM t_data d " +
					"LEFT JOIN resource_table r ON d.res_node_uuid=r.node_uuid " +
					"WHERE ";
			if (!copyshared) {
				sql += "shared_res=0 AND ";
			}
			if (dbserveur.equals("mysql")) {
				sql += "d.res_node_uuid <> 0x0000000000000000000000000000000"; // Binaire non null
			} else if (dbserveur.equals("oracle")) {
				sql += "d.res_node_uuid <> '00000000000000000000000000000000'"; // Binaire non null
			}
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			sql = "INSERT INTO t_res(new_uuid, node_uuid, xsi_type, content, user_id, modif_user_id, modif_date) ";
			if (dbserveur.equals("mysql")) {
				sql += "SELECT uuid2bin(UUID()), ";
			} else if (dbserveur.equals("oracle")) {
				sql += "SELECT sys_guid(), ";
			}
			sql += "r.node_uuid, r.xsi_type, r.content, r.user_id, r.modif_user_id, r.modif_date " +
					"FROM t_data d " +
					"LEFT JOIN resource_table r ON d.res_context_node_uuid=r.node_uuid " +
					"WHERE ";
			if (!copyshared) {
				sql += "shared_node=0 AND ";
			}
			if (dbserveur.equals("mysql")) {
				sql += "d.res_context_node_uuid <> 0x0000000000000000000000000000000";
			} else if (dbserveur.equals("oracle")) {
				sql += "d.res_context_node_uuid <> '00000000000000000000000000000000'";
			}
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			// nodeRes
			sql = "INSERT INTO t_res(new_uuid, node_uuid, xsi_type, content, user_id, modif_user_id, modif_date) ";
			if (dbserveur.equals("mysql")) {
				sql += "SELECT uuid2bin(UUID()), ";
			} else if (dbserveur.equals("oracle")) {
				sql += "SELECT sys_guid(), ";
			}
			sql += "r.node_uuid, r.xsi_type, r.content, r.user_id, r.modif_user_id, r.modif_date " +
					"FROM t_data d " +
					"LEFT JOIN resource_table r ON d.res_res_node_uuid=r.node_uuid " +
					"WHERE ";
			if (!copyshared) {
				sql += "shared_node_res=0 AND ";
			}
			if (dbserveur.equals("mysql")) {
				sql += "d.res_res_node_uuid <> 0x0000000000000000000000000000000";
			} else if (dbserveur.equals("oracle")) {
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

			/// Resolution des nouveaux uuid avec les parents
			// Avec la structure (et droits sur la structure)
			if (dbserveur.equals("mysql")) {
				sql = "UPDATE t_rights ri, t_data d SET ri.id=d.new_uuid WHERE ri.id=d.node_uuid AND d.shared_node=0";
			} else if (dbserveur.equals("oracle")) {
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
			if (dbserveur.equals("mysql")) {
				sql = "UPDATE t_rights ri, t_res re SET ri.id = re.new_uuid WHERE re.node_uuid=ri.id";
			} else if (dbserveur.equals("oracle")) {
				sql = "UPDATE t_rights ri SET ri.id=(SELECT new_uuid FROM t_res re WHERE re.node_uuid=ri.id) WHERE EXISTS (SELECT 1 FROM t_res re WHERE re.node_uuid=ri.id)";
			}
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			if (dbserveur.equals("mysql")) {
				sql = "UPDATE t_data d INNER JOIN t_res r ON d.res_node_uuid=r.node_uuid " +
						"SET d.res_node_uuid=r.new_uuid";
			} else if (dbserveur.equals("oracle")) {
				sql = "UPDATE t_data d SET d.res_node_uuid=(SELECT r.new_uuid FROM t_res r WHERE d.res_node_uuid=r.node_uuid) WHERE EXISTS (SELECT 1 FROM t_res r WHERE d.res_node_uuid=r.node_uuid)";
			}
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			if (dbserveur.equals("mysql")) {
				sql = "UPDATE t_data d INNER JOIN t_res r ON d.res_res_node_uuid=r.node_uuid " +
						"SET d.res_res_node_uuid=r.new_uuid";
			} else if (dbserveur.equals("oracle")) {
				sql = "UPDATE t_data d SET d.res_res_node_uuid=(SELECT r.new_uuid FROM t_res r WHERE d.res_res_node_uuid=r.node_uuid) WHERE EXISTS (SELECT 1 FROM t_res r WHERE d.res_res_node_uuid=r.node_uuid)";
			}
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			if (dbserveur.equals("mysql")) {
				sql = "UPDATE t_data d INNER JOIN t_res r ON d.res_context_node_uuid=r.node_uuid " +
						"SET d.res_context_node_uuid=r.new_uuid";
			} else if (dbserveur.equals("oracle")) {
				sql = "UPDATE t_data d SET d.res_context_node_uuid=(SELECT r.new_uuid FROM t_res r WHERE d.res_context_node_uuid=r.node_uuid) WHERE EXISTS (SELECT 1 FROM t_res r WHERE d.res_context_node_uuid=r.node_uuid)";
			}
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			/// Mise e jour de la liste des enfants (! requete particuliere)
			/// L'ordre determine le rendu visuel final du xml
			if (dbserveur.equals("mysql")) {
				sql = "UPDATE t_data d, (" +
						"SELECT node_parent_uuid, GROUP_CONCAT(bin2uuid(s.new_uuid) ORDER BY s.node_order) AS value " +
						"FROM t_struc s GROUP BY s.node_parent_uuid) tmp " +
						"SET d.node_children_uuid=tmp.value " +
						"WHERE tmp.node_parent_uuid=d.node_uuid";
			} else if (dbserveur.equals("oracle")) {
				sql = "UPDATE t_data d SET d.node_children_uuid=(SELECT value FROM (SELECT node_parent_uuid, LISTAGG(bin2uuid(s.new_uuid), ',') WITHIN GROUP (ORDER BY s.node_order) AS value FROM t_struc s GROUP BY s.node_parent_uuid) tmp WHERE tmp.node_parent_uuid=d.node_uuid) WHERE EXISTS (SELECT 1 FROM t_struc WHERE node_parent_uuid=d.node_uuid)";
			}
			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			// Mise e jour du code dans le contenu du noeud (blech)
			if (dbserveur.equals("mysql")) {
				sql = "UPDATE t_data d " +
						"LEFT JOIN t_res r ON d.res_res_node_uuid=r.new_uuid " + // Il faut utiliser le nouveau uuid
						"SET r.content=REPLACE(r.content, CONCAT(\"<code>\",d.code,\"</code>\"), ?) " +
						"WHERE d.asm_type='asmRoot'";
			} else if (dbserveur.equals("oracle")) {
				sql = "UPDATE t_res r SET r.content=(SELECT REPLACE(r2.content, d.code, ?) FROM t_data d LEFT JOIN t_res r2 ON d.res_res_node_uuid=r2.new_uuid WHERE d.asm_type='asmRoot') WHERE EXISTS (SELECT 1 FROM t_data d WHERE d.res_res_node_uuid=r.new_uuid AND d.asm_type='asmRoot')";
			}
			st = c.prepareStatement(sql);
			st.setString(1, "<code>" + newCode + "</code>");
			st.executeUpdate();
			st.close();

			// Mise e jour du code dans le code interne de la BD
			sql = "UPDATE t_data d SET d.code=? WHERE d.asm_type='asmRoot'";
			st = c.prepareStatement(sql);
			st.setString(1, newCode);
			st.executeUpdate();
			st.close();

			/// temp class
			class right {
				int rd = 0;
				int wr = 0;
				int dl = 0;
				int sb = 0;
				int ad = 0;
				String types = "";
				String rules = "";
				String notify = "";
			}

			class groupright {
				HashMap<String, right> rights = new HashMap<>();

				right getGroup(String label) {
					right r = rights.get(label.trim());
					if (r == null) {
						r = new right();
						rights.put(label, r);
					}
					return r;
				}

				void setNotify(String roles) {
					for (final right r : rights.values()) {
						r.notify = roles.trim();
					}
				}
			}

			class resolver {
				HashMap<String, groupright> resolve = new HashMap<>();

				HashMap<String, Integer> groups = new HashMap<>();

				groupright getUuid(String uuid) {
					groupright gr = resolve.get(uuid);
					if (gr == null) {
						gr = new groupright();
						resolve.put(uuid, gr);
					}
					return gr;
				}
			}

			final resolver resolve = new resolver();

			/// FIXME might want to regroup it with the import node I think
			// Selection des metadonnees
			sql = "SELECT bin2uuid(t.new_uuid) AS uuid, bin2uuid(t.portfolio_id) AS puuid, t.metadata, t.metadata_wad, t.metadata_epm " +
					"FROM t_data t";
			st = c.prepareStatement(sql);
			final ResultSet res = st.executeQuery();

			DocumentBuilder documentBuilder;
			final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
			documentBuilder = documentBuilderFactory.newDocumentBuilder();
			while (res.next()) {
				final String uuid = res.getString("uuid");
				//        	String puuid = res.getString("puuid");
				String meta = res.getString("metadata_wad");
				//          meta = meta.replaceAll("user", login);
				String nodeString = "<?xml version='1.0' encoding='UTF-8' standalone='no'?><transfer " + meta + "/>";

				final groupright role = resolve.getUuid(uuid);

				try {
					/// parse meta
					InputSource is = new InputSource(new StringReader(nodeString));
					Document doc = documentBuilder.parse(is);

					/// Process attributes
					Element attribNode = doc.getDocumentElement();
					NamedNodeMap attribMap = attribNode.getAttributes();

					String nodeRole;
					Node att = attribMap.getNamedItem("access");
					//if(access.equalsIgnoreCase("public") || access.contains("public"))
					//	credential.postGroupRight("all",uuid,Credential.READ,portfolioUuid,userId);
					att = attribMap.getNamedItem("seenoderoles");
					if (att != null) {
						final StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements()) {
							nodeRole = tokens.nextElement().toString();

							final right r = role.getGroup(nodeRole);
							r.rd = 1;

							resolve.groups.put(nodeRole, 0);
						}
					}
					att = attribMap.getNamedItem("showtoroles");
					if (att != null) {
						final StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements()) {
							nodeRole = tokens.nextElement().toString();

							final right r = role.getGroup(nodeRole);
							r.rd = 0;

							resolve.groups.put(nodeRole, 0);
						}
					}
					att = attribMap.getNamedItem("delnoderoles");
					if (att != null) {
						final StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements()) {

							nodeRole = tokens.nextElement().toString();
							final right r = role.getGroup(nodeRole);
							r.dl = 1;

							resolve.groups.put(nodeRole, 0);
						}
					}
					att = attribMap.getNamedItem("editnoderoles");
					if (att != null) {
						final StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements()) {
							nodeRole = tokens.nextElement().toString();
							final right r = role.getGroup(nodeRole);
							r.wr = 1;

							resolve.groups.put(nodeRole, 0);
						}
					}
					att = attribMap.getNamedItem("submitnoderoles");
					if (att != null) {
						final StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements()) {
							nodeRole = tokens.nextElement().toString();
							final right r = role.getGroup(nodeRole);
							r.sb = 1;

							resolve.groups.put(nodeRole, 0);
						}
					}
					att = attribMap.getNamedItem("seeresroles");
					if (att != null) {
						final StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements()) {
							nodeRole = tokens.nextElement().toString();
							final right r = role.getGroup(nodeRole);
							r.rd = 1;

							resolve.groups.put(nodeRole, 0);
						}
					}
					att = attribMap.getNamedItem("delresroles");
					if (att != null) {
						final StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements()) {
							nodeRole = tokens.nextElement().toString();
							final right r = role.getGroup(nodeRole);
							r.dl = 1;

							resolve.groups.put(nodeRole, 0);
						}
					}
					att = attribMap.getNamedItem("editresroles");
					if (att != null) {
						final StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements()) {
							nodeRole = tokens.nextElement().toString();
							final right r = role.getGroup(nodeRole);
							r.wr = 1;

							resolve.groups.put(nodeRole, 0);
						}
					}
					att = attribMap.getNamedItem("submitroles");
					if (att != null) {
						final StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements()) {
							nodeRole = tokens.nextElement().toString();
							final right r = role.getGroup(nodeRole);
							r.sb = 1;

							resolve.groups.put(nodeRole, 0);
						}
					}
					final Node actionroles = attribMap.getNamedItem("actionroles");
					if (actionroles != null) {
						/// Format pour l'instant: actionroles="sender:1,2;responsable:4"
						final StringTokenizer tokens = new StringTokenizer(actionroles.getNodeValue(), ";");
						while (tokens.hasMoreElements()) {
							nodeRole = tokens.nextElement().toString();
							final StringTokenizer data = new StringTokenizer(nodeRole, ":");
							final String nrole = data.nextElement().toString();
							final String actions = data.nextElement().toString().trim();
							final right r = role.getGroup(nrole);
							r.rules = actions;

							resolve.groups.put(nrole, 0);
						}
					}
					final Node menuroles = attribMap.getNamedItem("menuroles");
					if (menuroles != null) {
						/// Pour les differents items du menu
						final StringTokenizer menuline = new StringTokenizer(menuroles.getNodeValue(), ";");

						while (menuline.hasMoreTokens()) {
							final String line = menuline.nextToken();

							/// New format is an xml
							final Matcher roleMatcher = ROLE_PATTERN.matcher(line);
							String menurolename = null;
							if (roleMatcher.find()) {
								menurolename = roleMatcher.group(1);
							}

							/// Keeping old format for compatibility
							if (menurolename == null) {
								/// Format pour l'instant: code_portfolio,tag_semantique,label@en/libelle@fr,reles[;autre menu]
								final String[] tokens = line.split(",");
								if (tokens.length == 4) {
									menurolename = tokens[3];
								}
							}

							if (menurolename != null) {
								// Break down list of roles
								final String[] roles = menurolename.split(" ");
								for (final String s : roles) {
									resolve.groups.put(s.trim(), 0);
								}
							}
						}
					}
					final Node notifyroles = attribMap.getNamedItem("notifyroles");
					if (notifyroles != null) {
						/// Format pour l'instant: notifyroles="sender responsable"
						final StringTokenizer tokens = new StringTokenizer(notifyroles.getNodeValue(), " ");
						StringBuilder merge = new StringBuilder();
						if (tokens.hasMoreElements()) {
							merge = new StringBuilder(tokens.nextElement().toString().trim());
						}
						while (tokens.hasMoreElements()) {
							merge.append(",").append(tokens.nextElement().toString().trim());
						}
						role.setNotify(merge.toString());
					}

					// Check if base portfolio is public
					if (cred.isPublic(c, null, portfolioUuid)) {
						setPublic = true;
					}
					//					/*
					meta = res.getString("metadata");
					nodeString = "<?xml version='1.0' encoding='UTF-8' standalone='no'?><transfer " + meta + "/>";
					is = new InputSource(new StringReader(nodeString));
					doc = documentBuilder.parse(is);
					attribNode = doc.getDocumentElement();
					attribMap = attribNode.getAttributes();
					final Node publicatt = attribMap.getNamedItem("public");
					if (publicatt != null && DB_YES.equals(publicatt.getNodeValue())) {
						setPublic = true;
						//*/
					}
				} catch (final Exception e) {
					e.printStackTrace();
				}
			}

			res.close();
			st.close();

			c.setAutoCommit(false);

			/// On insere les donnees pre-compile
			final Iterator<String> entries = resolve.groups.keySet().iterator();

			// Cree les groupes, ils n'existent pas
			final String grquery = "INSERT INTO group_info(grid,owner,label) " + "VALUES(?,?,?)";
			final PreparedStatement st2 = c.prepareStatement(grquery);
			final String gri = "INSERT INTO group_right_info(owner, label, change_rights, portfolio_id) " +
					"VALUES(?,?,?,uuid2bin(?))";
			if ("mysql".equals(dbserveur)) {
				st = c.prepareStatement(gri, Statement.RETURN_GENERATED_KEYS);
			}
			if (dbserveur.equals("oracle")) {
				st = c.prepareStatement(gri, new String[] { "grid" });
			}

			while (entries.hasNext()) {
				final String label = entries.next();
				st.setInt(1, 1);
				st.setString(2, label);
				st.setInt(3, 0);
				st.setString(4, newPortfolioUuid);

				st.execute();
				final ResultSet keys = st.getGeneratedKeys();
				keys.next();
				final int grid = keys.getInt(1);
				resolve.groups.put(label, grid);

				st2.setInt(1, grid);
				st2.setInt(2, 1);
				st2.setString(3, label);
				st2.execute();
			}
			st2.close();
			st.close();

			/// Ajout des droits des noeuds
			final String insertRight = "INSERT INTO group_rights(grid, id, RD, WR, DL, SB, AD, types_id, rules_id, notify_roles) " +
					"VALUES(?,uuid2bin(?),?,?,?,?,?,?,?,?)";
			st = c.prepareStatement(insertRight);

			for (final Entry<String, groupright> entry : resolve.resolve.entrySet()) {
				final String uuid = entry.getKey();
				final groupright gr = entry.getValue();

				for (final Entry<String, right> rightelem : gr.rights.entrySet()) {
					final String group = rightelem.getKey();
					final int grid = resolve.groups.get(group);
					final right rightval = rightelem.getValue();
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
			if (setOwner) { // If we asked to change ownership
				sql += "d.modif_user_id";
			} else { // If we asked to change ownership
				sql += "p.user_id";
			}
			sql += ", p.model_id, d.modif_user_id, p.modif_date, p.active " +
					"FROM t_data d INNER JOIN portfolio p " +
					"ON d.node_uuid=p.root_node_uuid";

			st = c.prepareStatement(sql);
			st.executeUpdate();
			st.close();

			/// Create base group
			int groupid = postCreateRole(c, newPortfolioUuid, "all", userId);
			/// Finalement on cree un rele designer
			groupid = postCreateRole(c, newPortfolioUuid, "designer", userId);

			/// Ajoute la personne dans ce groupe
			putUserGroup(c, Integer.toString(groupid), Integer.toString(userId));

			// Update time
			touchPortfolio(c, null, newPortfolioUuid);

			/// Set portfolio public if needed
			if (setPublic) {
				setPublicState(c, userId, newPortfolioUuid, setPublic);
			}
		} catch (final Exception e) {
			logger.error("MESSAGE: " + e.getMessage() + " " + e.getLocalizedMessage());

			try {
				newPortfolioUuid = "erreur: " + e.getMessage();
				if (!c.getAutoCommit()) {
					c.rollback();
				}
			} catch (final SQLException e1) {
				e1.printStackTrace();
			}
			e.printStackTrace();
		} finally {
			try {
				c.setAutoCommit(true);
				// Les 'pooled connection' ne se ferment pas vraiment. On nettoie manuellement les tables temporaires...
				if (dbserveur.equals("mysql")) {
					sql = "DROP TEMPORARY TABLE IF EXISTS t_data, t_res, t_struc, t_struc_2, t_rights";
					st = c.prepareStatement(sql);
					st.execute();
					st.close();
				}
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}

		return newPortfolioUuid;
	}

	@Override
	public String postMacroOnNode(Connection c, int userId, String nodeUuid, String macroName) {
		String val = "erreur";
		String sql;
		PreparedStatement st;

		try {
			/// Selection du grid de l'utilisateur
			sql = "SELECT gr.grid, gi.label " +
					"FROM group_rights gr, group_info gi, group_user gu " +
					"WHERE gr.grid=gi.grid AND gi.gid=gu.gid AND gu.userid=? AND gr.id=uuid2bin(?) AND NOT gi.label='all'";
			st = c.prepareStatement(sql);
			st.setInt(1, userId);
			st.setString(2, nodeUuid);
			ResultSet res = st.executeQuery();
			/// res.getFetchSize() retourne 0, meme avec un bon resultat
			int grid = 0;
			String grlabl = "";
			if (res.next()) {
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
			if (res.next()) {
				meta = res.getString("metadata_wad");
			}
			res.close();
			st.close();

			/// FIXME:	Check if user has indeed the right to

			// Parse it, for the amount of manipulation we do, it will be simpler than find/replace
			final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
			final DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
			meta = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><metadata-wad " + meta + "></metadata-wad>";
			logger.info("ACTION OUT: {}", meta);
			final InputSource is = new InputSource(new StringReader(meta));
			final Document doc = documentBuilder.parse(is);
			final Element rootMeta = doc.getDocumentElement();

			final NamedNodeMap metaAttr = rootMeta.getAttributes();
			// int resetgroup = getRoleByNode(c, 1, nodeUuid, "resetter");    // Check for the possibility of resetter group
			// if ("reset".equals(macroName) && (cred.isAdmin(c, userId) || cred.isUserMemberOfRole(c, userId, resetgroup)))    // Admin, or part of resetter group

			if ("reset".equals(macroName) && (cred.hasRightInPortfolio(c, userId, nodeUuid))) // Admin, or access to portfolio
			{
				/// if reset and admin
				// Call specific function to process current temporary table
				queryChildren(c, nodeUuid);
				resetRights(c);
			} else if ("show".equals(macroName) || "hide".equals(macroName)) {
				// Check if current group can show stuff
				final Node roleitem = metaAttr.getNamedItem("showroles");
				final String roles = roleitem.getNodeValue();
				if (roles.contains(grlabl)) // Can activate it
				{
					final String showto = metaAttr.getNamedItem("showtoroles").getNodeValue();
					final StringBuilder vallist = new StringBuilder("?");
					final String[] valarray = showto.split(" ");
					for (int i = 0; i < valarray.length - 1; ++i) {
						vallist.append(",?");
					}
					//					showto = showto.replace(" ", ",");
					//					showto = "'" + showto +"'";

					//// Il faut qu'il y a un showtorole
					if (!"".equals(showto)) {
						// Update rights, have to make it work with Oracle too...
						sql = "UPDATE group_rights SET RD=? " +
								"WHERE id=uuid2bin(?) AND grid IN " +
								"(SELECT gri.grid FROM group_right_info gri, node n " +
								"WHERE gri.label IN (" +
								vallist +
								") AND gri.portfolio_id=n.portfolio_id AND n.node_uuid=uuid2bin(?) ) ";

						st = c.prepareStatement(sql);
						if ("hide".equals(macroName)) {
							st.setInt(1, 0);
						} else if ("show".equals(macroName)) {
							st.setInt(1, 1);
						}
						st.setString(2, nodeUuid);
						for (int i = 0; i < valarray.length; ++i) {
							st.setString(3 + i, valarray[i]);
						}
						//						st.setString(3, showto);
						st.setString(3 + valarray.length, nodeUuid);

						st.executeUpdate();
						st.close();

						Node isPriv = metaAttr.getNamedItem("private");
						if (isPriv == null) {
							isPriv = doc.createAttribute("private");
							metaAttr.setNamedItem(isPriv);
						}
						// Update local string
						if ("hide".equals(macroName)) {
							isPriv.setNodeValue(DB_YES);
						} else if ("show".equals(macroName)) {
							isPriv.setNodeValue(DB_NO);
						}
					}
				}

				// Update DB
				meta = DomUtils.getNodeAttributesString(rootMeta);
				logger.info("META: {}", meta);

				sql = "UPDATE node SET metadata_wad=? WHERE node_uuid=uuid2bin(?)";
				st = c.prepareStatement(sql);
				st.setString(1, meta);
				st.setString(2, nodeUuid);
				st.executeUpdate();
				st.close();

			} else if ("submit".equals(macroName)) {
				queryChildren(c, nodeUuid);

				/// Apply changes
				logger.info("ACTION: {}, grid: {}  -> uuid: {}", macroName, grid, nodeUuid);
				/// Insert/replace existing editing related rights
				sql = "INSERT INTO group_rights(grid, id, WR, DL, AD, SB, types_id, rules_id) " +
						"SELECT gr.grid, gr.id, 0, 0, 0, 0, NULL, NULL " +
						"FROM group_rights gr " +
						"WHERE gr.id IN (SELECT uuid FROM t_struc_nodeid) AND gr.grid=? " +
						"ON DUPLICATE KEY UPDATE WR=0, DL=0, AD=0, SB=0, types_id=null, rules_id=null";

				if (dbserveur.equals("oracle")) {
					sql = "MERGE INTO group_rights d USING (" +
							"SELECT gr.grid, gr.id, 0 WR, 0 DL, 0 AD, 0 SB, NULL types_id, NULL rules_id " +
							"FROM group_rights gr " +
							"WHERE gr.id IN (SELECT uuid FROM t_struc_nodeid) AND gr.grid=? ) s " +
							"ON (d.grid=s.grid AND d.id=s.id) " +
							"WHEN MATCHED THEN UPDATE " +
							"SET d.WR=0, d.DL=0, d.AD=0, d.SB=0, d.types_id=s.types_id, d.rules_id=s.rules_id " +
							"WHEN NOT MATCHED THEN " +
							"INSERT (d.grid, d.id, d.WR, d.DL, d.AD, d.SB, d.types_id, d.rules_id) " +
							"VALUES (s.grid, s.id, s.WR, s.DL, s.AD, s.SB, s.types_id, s.rules_id)";
				}
				st = c.prepareStatement(sql);
				st.setInt(1, grid);
				final int rows = st.executeUpdate();
				st.close();

				if (rows == 0) {
					return "unchanged";
				}

				/// FIXME: This part might be deprecated in the near future
				/// Verifie le showtoroles
				final Node showtonode = metaAttr.getNamedItem("showtoroles");
				String showto = "";
				if (showtonode != null) {
					showto = showtonode.getNodeValue();
				}
				showto = showto.replace(" ", "','");
				//				showto = "('" + showto +"')";

				//// Il faut qu'il y a un showtorole
				logger.info("SHOWTO: {}", showto);
				if (!"".equals(showto)) {
					logger.info("SHOWING TO: {}", showto);
					// Update rights
					sql = "UPDATE group_rights SET RD=? " +
							"WHERE id=uuid2bin(?) AND grid IN " +
							"(SELECT gri.grid FROM group_right_info gri, node n " +
							"WHERE gri.label IN (?) AND gri.portfolio_id=n.portfolio_id AND n.node_uuid=uuid2bin(?) ) ";
					st = c.prepareStatement(sql);
					st.setInt(1, 1);
					st.setString(2, nodeUuid);
					st.setString(3, showto);
					st.setString(4, nodeUuid);
					st.executeUpdate();
					st.close();

					metaAttr.removeNamedItem("private");
				}

				/// We then update the metadata notifying it was submitted
				rootMeta.setAttribute("submitted", DB_YES);
				/// Submitted date
				final Date time = new Date();
				final String timeFormat = DATETIME_FORMAT.format(time);
				rootMeta.setAttribute("submitteddate", timeFormat);
				final String updatedMeta = DomUtils.getNodeAttributesString(rootMeta);
				sql = "UPDATE node SET metadata_wad=? WHERE node_uuid=uuid2bin(?)";
				st = c.prepareStatement(sql);
				st.setString(1, updatedMeta);
				st.setString(2, nodeUuid);
				st.executeUpdate();
				st.close();
			} else if ("submitall".equals(macroName)) {
				// Fill temp table 't_struc_nodeid' with node ids
				queryChildren(c, nodeUuid);

				/// Apply changes
				logger.info("ACTION: {}, grid: {} -> uuid: {}", macroName, grid, nodeUuid);
				/// Insert/replace existing editing related rights
				/// Same as submit, except we don't limit to user's own group right
				sql = "INSERT INTO group_rights(grid, id, WR, DL, AD, SB, types_id, rules_id) " +
						"SELECT gr.grid, gr.id, 0, 0, 0, 0, NULL, NULL " +
						"FROM group_rights gr " +
						"WHERE gr.id IN (SELECT uuid FROM t_struc_nodeid) " +
						"ON DUPLICATE KEY UPDATE WR=0, DL=0, AD=0, SB=0, types_id=null, rules_id=null";

				if (dbserveur.equals("oracle")) {
					sql = "MERGE INTO group_rights d USING (" +
							"SELECT gr.grid, gr.id, 0 WR, 0 DL, 0 AD, 0 SB, NULL types_id, NULL rules_id " +
							"FROM group_rights gr " +
							"WHERE gr.id IN (SELECT uuid FROM t_struc_nodeid)) s " +
							"ON (d.grid=s.grid AND d.id=s.id) " +
							"WHEN MATCHED THEN UPDATE " +
							"SET d.WR=0, d.DL=0, d.AD=0, d.SB=0, d.types_id=s.types_id, d.rules_id=s.rules_id " +
							"WHEN NOT MATCHED THEN " +
							"INSERT (d.grid, d.id, d.WR, d.DL, d.AD, d.SB, d.types_id, d.rules_id) " +
							"VALUES (s.grid, s.id, s.WR, s.DL, s.AD, s.SB, s.types_id, s.rules_id)";
				}
				st = c.prepareStatement(sql);
				final int rows = st.executeUpdate();
				st.close();

				if (rows == 0) {
					return "unchanged";
				}

				/// We then update the metadata notifying it was submitted
				rootMeta.setAttribute("submitted", DB_YES);
				/// Submitted date
				final Date time = new Date();
				final String timeFormat = DATETIME_FORMAT.format(time);
				rootMeta.setAttribute("submitteddate", timeFormat);
				final String updatedMeta = DomUtils.getNodeAttributesString(rootMeta);
				sql = "UPDATE node SET metadata_wad=? WHERE node_uuid=uuid2bin(?)";
				st = c.prepareStatement(sql);
				st.setString(1, updatedMeta);
				st.setString(2, nodeUuid);
				st.executeUpdate();
				st.close();
			} else if ("submitQuizz".equals(macroName)) {
				sql = "DROP TEMPORARY TABLE IF EXISTS t_struc_nodeid, t_struc_nodeid_2";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();

				//Comparaison reponses
				//sql="SELECT bin2uuid(node_parent_uuid) " + "FROM node n " + "WHERE node_uuid=uuid2bin(\""+nodeUuid+"\")";

				//uuid1
				sql = "SELECT bin2uuid(node_uuid) " +
						"FROM node n " +
						"WHERE semantictag LIKE \"quizz\"" +
						" AND node_parent_uuid=uuid2bin(?)";
				st = c.prepareStatement(sql);
				st.setString(1, nodeUuid);
				final ResultSet rs1 = st.executeQuery();
				rs1.next();
				//String uuidParent = rs.getString(1);
				final String uuidREP = rs1.getString(1);
				st.close();

				//uuid2
				sql = "SELECT content " +
						"FROM resource_table r_t " +
						"WHERE xsi_type " +
						"LIKE \"Proxy\" " +
						"AND node_uuid " +
						"IN (SELECT res_node_uuid FROM node n WHERE semantictag LIKE \"proxy-quizz\" " +
						"AND node_parent_uuid=uuid2bin(?)) ";
				st = c.prepareStatement(sql);
				st.setString(1, nodeUuid);
				final ResultSet rs2 = st.executeQuery();
				rs2.next();
				final String ContentUuid2 = rs2.getString(1);
				final String uuidSOL = ContentUuid2.substring(6, 42);
				st.close();

				final String uuids = uuidREP + uuidSOL + nodeUuid;

				// FIXEME why doing that here ! never do an http request when doing database processing !
				int prctElv = 0;
				final HttpResponse response = HttpClientUtils.goGet(new HashSet<Header>(),
						backend + "/compare/" + uuids);
				if (response != null) {
					prctElv = Integer.parseInt(EntityUtils.toString(response.getEntity()));
				}

				//Recherche noeud pourcentage mini
				final String nodePrct = getNodeUuidBySemtag(c, "level", nodeUuid); //recuperation noeud avec semantictag "mini"

				//parse le noeud
				final String lbl = null;
				final Object ndSol = getNode(c, new MimeType("text/xml"), nodePrct, true, 1, 0, null, lbl, null);
				if (ndSol == null) {
					return null;
				}

				final DocumentBuilderFactory documentBuilderFactory2 = DocumentBuilderFactory.newInstance();
				final DocumentBuilder documentBuilder2 = documentBuilderFactory2.newDocumentBuilder();
				final ByteArrayInputStream is2 = new ByteArrayInputStream(
						ndSol.toString().getBytes(StandardCharsets.UTF_8));
				final Document doc2 = documentBuilder2.parse(is2);

				final DOMImplementationLS impl = (DOMImplementationLS) doc2.getImplementation().getFeature("LS", "3.0");
				final LSSerializer serial = impl.createLSSerializer();
				serial.getDomConfig().setParameter("xml-declaration", true);

				//recuperation valeur seuil
				final Element root = doc2.getDocumentElement();

				//root.getElementsByTagName("semantictag");
				final Node ndValeur = root.getFirstChild().getNextSibling().getNextSibling().getNextSibling()
						.getNextSibling().getNextSibling();
				ndValeur.getFirstChild().getNextSibling().getNextSibling();
				final String seuil = ndValeur.getFirstChild().getNextSibling().getNextSibling().getTextContent().trim();
				final int prctMini = Integer.parseInt(seuil);

				//recuperation asmContext contenant l'action
				sql = "SELECT bin2uuid(node_uuid) " +
						"FROM node n " +
						"WHERE node_uuid  IN (SELECT uuid2bin(node_children_uuid) " +
						"FROM node n " +
						"WHERE semantictag LIKE \"action\" AND node_parent_uuid " +
						"LIKE uuid2bin(?)) ";
				st = c.prepareStatement(sql);
				st.setString(1, nodeUuid);
				final ResultSet rsa = st.executeQuery();
				rsa.next();
				final String contextActionNodeUuid = rsa.getString(1);
				st.close();

				//Recuperation uuidNoeud sur lequel effectuer l'action, role et action
				final String lbl2 = null;
				final Object nd = getNode(c, new MimeType("text/xml"), contextActionNodeUuid, true, 1, 0, null, lbl2,
						null);
				if (nd == null) {
					return null;
				}

				final DocumentBuilderFactory documentBuilderFactory3 = DocumentBuilderFactory.newInstance();
				final DocumentBuilder documentBuilder3 = documentBuilderFactory3.newDocumentBuilder();
				final ByteArrayInputStream is3 = new ByteArrayInputStream(
						nd.toString().getBytes(StandardCharsets.UTF_8));
				final Document doc3 = documentBuilder3.parse(is3);

				final DOMImplementationLS imple = (DOMImplementationLS) doc3.getImplementation().getFeature("LS",
						"3.0");
				final LSSerializer seriale = imple.createLSSerializer();
				seriale.getDomConfig().setParameter("xml-declaration", true);

				final Element racine = doc3.getDocumentElement();

				String action;
				String nodeAction;
				String role;

				final NodeList valueList = racine.getElementsByTagName("value");
				nodeAction = valueList.item(0).getFirstChild().getNodeValue();

				final NodeList actionList = racine.getElementsByTagName("action");
				action = actionList.item(0).getFirstChild().getNodeValue();

				final NodeList roleList = racine.getElementsByTagName("role");
				role = roleList.item(0).getFirstChild().getNodeValue();

				sql = "SELECT gu.userid FROM group_rights gr, group_right_info gri, " +
						"group_info gi, group_user gu WHERE gr.id LIKE uuid2bin(?) " +
						"AND gri.grid LIKE gr.grid AND gi.label LIKE gri.label AND gi.grid LIKE gr.grid " +
						"AND gu.gid LIKE gi.gid";
				st = c.prepareStatement(sql);
				st.setString(1, nodeAction);
				final ResultSet rsr = st.executeQuery();
				rsr.next();
				final String usId = rsr.getString(1);
				st.close();

				userId = Integer.parseInt(usId);

				//comparaison
				if (prctElv >= prctMini) {
					//postMacroOnNode(c, 1, nodeAction, "show");//dire de modifier showto en show
					executeAction(c, 1, nodeAction, action, role);

					sql = "SELECT metadata_wad FROM node WHERE node_uuid=uuid2bin(?)";
					st = c.prepareStatement(sql);
					st.setString(1, nodeAction);
					res = st.executeQuery();
					String metaA = "";
					if (res.next()) {
						metaA = res.getString("metadata_wad");
					}
					res.close();
					st.close();

					// Parsage meta
					final DocumentBuilderFactory documentBuilderFactorys = DocumentBuilderFactory.newInstance();
					final DocumentBuilder documentBuilders = documentBuilderFactorys.newDocumentBuilder();
					metaA = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><metadata-wad " + metaA + "></metadata-wad>";
					logger.info("ACTION OUT: {}", metaA);
					final InputSource iss = new InputSource(new StringReader(metaA));
					final Document docs = documentBuilders.parse(iss);
					final Element rootMetaA = docs.getDocumentElement();

					final NamedNodeMap metaAttrs = rootMetaA.getAttributes();
					metaAttrs.removeNamedItem("private");

					final String updatedMeta = DomUtils.getNodeAttributesString(rootMetaA);
					sql = "UPDATE node SET metadata_wad=? WHERE node_uuid=uuid2bin(?)";
					st = c.prepareStatement(sql);
					st.setString(1, updatedMeta);
					st.setString(2, nodeAction);
					st.executeUpdate();
					st.close();

					postMacroOnNode(c, userId, nodeUuid, "submit");
				} else {
					postMacroOnNode(c, userId, uuidREP, "submit");
				}
			}
			val = "OK";
		} catch (final Exception e) {
			e.printStackTrace();
		} finally {
			try {
				// Les 'pooled connection' ne se ferment pas vraiment. On nettoie manuellement les tables temporaires...
				if (dbserveur.equals("mysql")) {
					sql = "DROP TEMPORARY TABLE IF EXISTS t_struc_nodeid, t_struc_nodeid_2";
					st = c.prepareStatement(sql);
					st.execute();
					st.close();
				}
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}

		return val;
	}

	@Deprecated
	@Override
	public Object postModels(Connection c, MimeType mimeType, String xmlModel, int userId) throws Exception {
		if (!cred.isAdmin(c, userId)) {
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");
		}

		String result = "";

		//On recupere le body
		Document doc;

		doc = DomUtils.xmlString2Document(xmlModel, new StringBuilder());
		final Element users = doc.getDocumentElement();

		NodeList children;

		children = users.getChildNodes();
		// On parcourt une premiere fois les enfants pour recuperer la liste e ecrire en base

		//On verifie le bon format
		if (users.getNodeName().equals("models")) {
			for (int i = 0; i < children.getLength(); i++) {
				if (children.item(i).getNodeName().equals("model")) {
					final NodeList children2 = children.item(i).getChildNodes();
					for (int y = 0; y < children2.getLength(); y++) {

						if (children2.item(y).getNodeName().equals("label")) {
							DomUtils.getInnerXml(children2.item(y));
						}
						if (children2.item(y).getNodeName().equals("treeid")) {
							DomUtils.getInnerXml(children2.item(y));
						}
					}
				}
			}
		} else {
			result = "Erreur lors de la recuperation des attributs de l'utilisateur dans le XML";
		}

		return result;
	}

	@Override
	public int postMoveNodeUp(Connection c, int userid, String uuid) {
		//		if(!cred.isAdmin(c, userid) && !cred.isDesigner(c, userid, uuid) )
		//			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		String sql;
		PreparedStatement st;
		int status = -1;

		try {
			sql = "SELECT bin2uuid(node_parent_uuid) AS puuid, node_order " +
					"FROM node " +
					"WHERE node_uuid=uuid2bin(?)";
			st = c.prepareStatement(sql);
			st.setString(1, uuid);
			final ResultSet res = st.executeQuery();

			int order = -1;
			String puuid = "";
			if (res.next()) {
				order = res.getInt("node_order");
				puuid = res.getString("puuid");
			}
			res.close();

			if (order == 0) {
				status = -2;
			} else if (order > 0) {
				c.setAutoCommit(false);

				/// Swap node order
				sql = "UPDATE node SET node_order=";
				if ("mysql".equals(dbserveur)) {
					sql += "IF( node_order=?, ?, ? ) ";
				} else if ("oracle".equals(dbserveur)) {
					sql += "decode( node_order, ?, ?, ? ) ";
				}

				sql += "WHERE node_order IN ( ?, ? ) " + "AND node_parent_uuid=uuid2bin(?)";
				st = c.prepareStatement(sql);
				st.setInt(1, order);
				st.setInt(2, order - 1);
				st.setInt(3, order);
				st.setInt(4, order - 1);
				st.setInt(5, order);
				st.setString(6, puuid);
				st.executeUpdate();
				st.close();

				/// Update children list
				updateMysqlNodeChildren(c, puuid);

				status = 0;

				touchPortfolio(c, uuid, null);
			}
		} catch (final SQLException e) {
			try {
				c.rollback();
			} catch (final SQLException e1) {
				e1.printStackTrace();
			}
			e.printStackTrace();
		} finally {
			try {
				c.setAutoCommit(true);
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}

		return status;
	}

	@Override
	public Object postNode(Connection c, MimeType inMimeType, String parentNodeUuid, String in, int userId, int groupId,
			boolean forcedUuid) throws Exception {

		/*
		NodeRight noderight = credential.getNodeRight(userId,groupId, parentNodeUuid, credential.ADD);
		if(!noderight.add)
		if(!credential.isAdmin(userId))
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");
		//*/

		//TODO Optimiser le nombre de requetes (3 => 1)

		final int nodeOrder = getMysqlNodeNextOrderChildren(c, parentNodeUuid);
		final String portfolioUid = getPortfolioUuidByNodeUuid(c, parentNodeUuid);

		String result;
		final String portfolioModelId = getPortfolioModelUuid(c, portfolioUid);

		//TODO getNodeRight postNode

		final String inPars = DomUtils.cleanXMLData(in);
		final Document doc = DomUtils.xmlString2Document(inPars, new StringBuilder());
		// Puis on le recree
		Node rootNode;
		String nodeType;
		rootNode = doc.getDocumentElement();
		nodeType = rootNode.getNodeName();

		//		String nodeUuid = writeNode(c, rootNode, portfolioUid,  portfolioModelId,userId,nodeOrder,null,parentNodeUuid,0,0, true, null, false);
		final String nodeUuid = writeNode(c, rootNode, portfolioUid, portfolioModelId, userId, nodeOrder, null,
				parentNodeUuid, 0, 0, forcedUuid, null, true);

		result = "<nodes>";
		result += "<" + nodeType + " ";
		result += DomUtils.getXmlAttributeOutput("id", nodeUuid) + " ";
		result += "/>";
		result += "</nodes>";

		touchPortfolio(c, parentNodeUuid, null);

		return result;
	}

	@Override
	public Object postNodeFromModelBySemanticTag(Connection c, MimeType inMimeType, String parentNodeUuid,
			String semanticTag, int userId, int groupId, String userRole) throws Exception {
		final String portfolioUid = getPortfolioUuidByNodeUuid(c, parentNodeUuid);

		final String portfolioModelId = getPortfolioModelUuid(c, portfolioUid);

		final String xml = getNodeBySemanticTag(c, inMimeType, portfolioModelId, semanticTag, userId, groupId, userRole)
				.toString();

		final ResultSet res = getMysqlOtherNodeUuidByPortfolioModelUuidBySemanticTag(c, portfolioModelId, semanticTag);
		String otherParentNodeUuid = null;
		if (res != null) {
			res.next();
			// C'est le noeud obtenu dans le modele indique par la table de correspondance
			otherParentNodeUuid = res.getString("node_uuid");
		}
		return postNode(c, inMimeType, otherParentNodeUuid, xml, userId, groupId, true);
	}

	@Override
	public boolean postNodeRight(int userId, String nodeUuid) throws Exception {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean postNotifyRoles(Connection c, int userId, String portfolio, String uuid, String notify) {
		boolean ret = false;
		if (!cred.isAdmin(c, userId)) {
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");
		}

		String sql;
		PreparedStatement st;
		try {
			sql = "UPDATE group_rights SET notify_roles=? " +
					"WHERE id=uuid2bin(?) AND grid IN " +
					"(SELECT grid FROM group_right_info WHERE portfolio_id=uuid2bin(?))";
			st = c.prepareStatement(sql);
			st.setString(1, notify);
			st.setString(2, uuid);
			st.setString(3, portfolio);
			st.executeUpdate();
			st.close();

			ret = true;
		} catch (final SQLException e) {
			e.printStackTrace();
		}

		return ret;
	}

	@Override
	public Object postPortfolio(Connection c, MimeType inMimeType, MimeType outMimeType, String in, int userId,
			int groupId, String portfolioModelId, int substid, boolean parseRights, String projectName)
			throws Exception {
		if (!cred.isAdmin(c, userId) && !cred.isCreator(c, userId)) {
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");
		}

		final StringBuilder outTrace = new StringBuilder();
		String portfolioUuid;

		// Si le modele est renseigne, on ignore le XML poste et on recupere le contenu du modele
		// à la place
		// FIXME Unused, we instanciate/copy a portfolio
		if (portfolioModelId != null) {
			in = getPortfolio(c, inMimeType, portfolioModelId, userId, groupId, null, null, null, substid, null)
					.toString();
		}

		// On genere un nouvel uuid
		portfolioUuid = UUID.randomUUID().toString();

		if (in.length() > 0) {
			final Document doc = DomUtils.xmlString2Document(in, outTrace);

			/// Check if portfolio code is already used
			final XPath xPath = XPathFactory.newInstance().newXPath();
			final String filterRes = "//*[local-name()='asmRoot']/*[local-name()='asmResource']/*[local-name()='code']";
			final NodeList nodelist = (NodeList) xPath.compile(filterRes).evaluate(doc, XPathConstants.NODESET);

			if (nodelist.getLength() > 0) {
				String code = nodelist.item(0).getTextContent();
				if (projectName != null) {
					// Find if it contains a project name
					final int dot = code.indexOf(".");
					if (dot < 0) { // Doesn't exist, add it
						code = projectName + "." + code;
					} else { // Doesn't exist, add it
						code = projectName + code.substring(dot);
					}

				}

				// Simple query
				if (isCodeExist(c, code, null)) {
					throw new RestWebApplicationException(Status.CONFLICT, "Existing code.");
				}

				nodelist.item(0).setTextContent(code);
			}

			Node rootNode = (doc.getElementsByTagName("portfolio")).item(0);
			if (rootNode == null) {
				throw new Exception("Root Node (portfolio) not found !");
			}
			rootNode = (doc.getElementsByTagName("asmRoot")).item(0);

			final String uuid = UUID.randomUUID().toString();

			insertMysqlPortfolio(c, portfolioUuid, uuid, 0, userId);

			writeNode(c, rootNode, portfolioUuid, portfolioModelId, userId, 0, uuid, null, 0, 0, false, null,
					parseRights);
		}
		updateMysqlPortfolioActive(c, portfolioUuid, true);

		updateMysqlPortfolioModelId(c, portfolioUuid, portfolioModelId);

		/// If we instanciate, don't need the designer role
		//		if( !parseRights )
		{
			int groupid = postCreateRole(c, portfolioUuid, "all", userId);
			/// Creer groupe 'designer', 'all' est mis avec ce qui est specifique dans le xml reçu
			groupid = postCreateRole(c, portfolioUuid, "designer", userId);

			/// Ajoute la personne dans ce groupe
			putUserGroup(c, Integer.toString(groupid), Integer.toString(userId));
		}

		/// S'assure que la date est bonne
		touchPortfolio(c, null, portfolioUuid);

		String result = "<portfolios>";
		result += "<portfolio ";
		result += DomUtils.getXmlAttributeOutput("id", portfolioUuid) + " ";
		result += "/>";
		result += "</portfolios>";
		return result;
	}

	/********************************************************/
	/**
	 * ###### ##### ###### ####### ### ###### ## ## ## ## ## ## # ## ## ## ## ## ##
	 * ## ## ## ## # ## ## ## ###### ## ## ###### # ## ### ###### ## ## ## ## ## #
	 * ## ## ## ## ## ## ## ## ## # ## ## ## ## ## ##### ## ## # ### ## ## /**
	 * Managing and listing portfolios /
	 ********************************************************/
	@Override
	public int postPortfolioGroup(Connection c, String groupname, String type, Integer parent, int userId) {
		String sql;
		PreparedStatement st = null;
		ResultSet res = null;
		int groupid = -1;

		try {
			if (parent != null) { // Check parent exists
				sql = "SELECT pg FROM portfolio_group WHERE pg=? AND type='GROUP'";
				st = c.prepareStatement(sql);
				st.setInt(1, parent);
				res = st.executeQuery();
				if (!res.next()) {
					return -1;
				}
				res.close();
				st.close();
			}

			sql = "INSERT INTO portfolio_group";
			if (parent == null) {
				sql += "(label, type) VALUE(?, ?)";
			} else {
				// Ensure parent exists
				sql += "(label, type, pg_parent) VALUE(?, ?, ?)";
			}
			if (dbserveur.equals("oracle")) {
				st = c.prepareStatement(sql, new String[] { "pg" });
			} else {
				st = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
			}
			st.setString(1, groupname);
			st.setString(2, type);
			if (parent != null) {
				st.setInt(3, parent);
			}
			st.executeUpdate();
			res = st.getGeneratedKeys();
			if (res.next()) {
				groupid = res.getInt(1);
			}

		} catch (final SQLException e) {
			logger.error(e.getMessage());
			e.printStackTrace();
		} finally {
			try {
				if (res != null) {
					res.close();
				}
				if (st != null) {
					st.close();
				}
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}

		return groupid;
	}

	@Override
	public Object postPortfolioParserights(Connection c, String portfolioUuid, int userId) {
		// TODO Auto-generated method stub
		if (!cred.isAdmin(c, userId) && !cred.isCreator(c, userId)) {
			return "no rights";
		}

		String sql;
		PreparedStatement st;
		boolean setPublic = false;

		try {
			/// temp class
			class right {
				int rd = 0;
				int wr = 0;
				int dl = 0;
				int sb = 0;
				int ad = 0;
				String types = "";
				String rules = "";
				String notify = "";
			}

			class groupright {
				HashMap<String, right> rights = new HashMap<>();

				right getGroup(String label) {
					right r = rights.get(label.trim());
					if (r == null) {
						r = new right();
						rights.put(label, r);
					}
					return r;
				}

				void setNotify(String roles) {
					for (final right r : rights.values()) {
						r.notify = roles.trim();
					}
				}
			}

			class resolver {
				HashMap<String, groupright> resolve = new HashMap<>();

				HashMap<String, Integer> groups = new HashMap<>();

				groupright getUuid(String uuid) {
					groupright gr = resolve.get(uuid);
					if (gr == null) {
						gr = new groupright();
						resolve.put(uuid, gr);
					}
					return gr;
				}
			}

			final resolver resolve = new resolver();

			// Selection des metadonnees
			sql = "SELECT bin2uuid(n.node_uuid) AS uuid, bin2uuid(n.portfolio_id) AS puuid, n.metadata, n.metadata_wad, n.metadata_epm " +
					"FROM node n " +
					"WHERE portfolio_id=uuid2bin(?)";
			st = c.prepareStatement(sql);
			st.setString(1, portfolioUuid);
			final ResultSet res = st.executeQuery();

			DocumentBuilder documentBuilder;
			final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
			documentBuilder = documentBuilderFactory.newDocumentBuilder();
			while (res.next()) {
				final String uuid = res.getString("uuid");
				//        	String puuid = res.getString("puuid");
				String meta = res.getString("metadata_wad");
				//          meta = meta.replaceAll("user", login);
				String nodeString = "<?xml version='1.0' encoding='UTF-8' standalone='no'?><transfer " + meta + "/>";

				final groupright role = resolve.getUuid(uuid);

				try {
					/// parse meta
					InputSource is = new InputSource(new StringReader(nodeString));
					Document doc = documentBuilder.parse(is);

					/// Process attributes
					Element attribNode = doc.getDocumentElement();
					NamedNodeMap attribMap = attribNode.getAttributes();

					String nodeRole;
					Node att = attribMap.getNamedItem("access");
					//if (att != null) {
					//if(access.equalsIgnoreCase("public") || access.contains("public"))
					//	credential.postGroupRight("all",uuid,Credential.READ,portfolioUuid,userId);
					//}
					att = attribMap.getNamedItem("seenoderoles");
					if (att != null) {
						final StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements()) {
							nodeRole = tokens.nextElement().toString();

							final right r = role.getGroup(nodeRole);
							r.rd = 1;

							resolve.groups.put(nodeRole, 0);
						}
					}
					att = attribMap.getNamedItem("showtoroles");
					if (att != null) {
						final StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements()) {
							nodeRole = tokens.nextElement().toString();

							final right r = role.getGroup(nodeRole);
							r.rd = 0;

							resolve.groups.put(nodeRole, 0);
						}
					}
					att = attribMap.getNamedItem("delnoderoles");
					if (att != null) {
						final StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements()) {

							nodeRole = tokens.nextElement().toString();
							final right r = role.getGroup(nodeRole);
							r.dl = 1;

							resolve.groups.put(nodeRole, 0);
						}
					}
					att = attribMap.getNamedItem("editnoderoles");
					if (att != null) {
						final StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements()) {
							nodeRole = tokens.nextElement().toString();
							final right r = role.getGroup(nodeRole);
							r.wr = 1;

							resolve.groups.put(nodeRole, 0);
						}
					}
					att = attribMap.getNamedItem("submitnoderoles");
					if (att != null) {
						final StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements()) {
							nodeRole = tokens.nextElement().toString();
							final right r = role.getGroup(nodeRole);
							r.sb = 1;

							resolve.groups.put(nodeRole, 0);
						}
					}
					att = attribMap.getNamedItem("seeresroles");
					if (att != null) {
						final StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements()) {
							nodeRole = tokens.nextElement().toString();
							final right r = role.getGroup(nodeRole);
							r.rd = 1;

							resolve.groups.put(nodeRole, 0);
						}
					}
					att = attribMap.getNamedItem("delresroles");
					if (att != null) {
						final StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements()) {
							nodeRole = tokens.nextElement().toString();
							final right r = role.getGroup(nodeRole);
							r.dl = 1;

							resolve.groups.put(nodeRole, 0);
						}
					}
					att = attribMap.getNamedItem("editresroles");
					if (att != null) {
						final StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements()) {
							nodeRole = tokens.nextElement().toString();
							final right r = role.getGroup(nodeRole);
							r.wr = 1;

							resolve.groups.put(nodeRole, 0);
						}
					}
					att = attribMap.getNamedItem("submitroles");
					if (att != null) {
						final StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements()) {
							nodeRole = tokens.nextElement().toString();
							final right r = role.getGroup(nodeRole);
							r.sb = 1;

							resolve.groups.put(nodeRole, 0);
						}
					}
					final Node actionroles = attribMap.getNamedItem("actionroles");
					if (actionroles != null) {
						/// Format pour l'instant: actionroles="sender:1,2;responsable:4"
						final StringTokenizer tokens = new StringTokenizer(actionroles.getNodeValue(), ";");
						while (tokens.hasMoreElements()) {
							nodeRole = tokens.nextElement().toString();
							final StringTokenizer data = new StringTokenizer(nodeRole, ":");
							final String nrole = data.nextElement().toString();
							final String actions = data.nextElement().toString().trim();
							final right r = role.getGroup(nrole);
							r.rules = actions;

							resolve.groups.put(nrole, 0);
						}
					}
					final Node menuroles = attribMap.getNamedItem("menuroles");
					if (menuroles != null) {
						/// Pour les differents items du menu
						final StringTokenizer menuline = new StringTokenizer(menuroles.getNodeValue(), ";");

						while (menuline.hasMoreTokens()) {
							final String line = menuline.nextToken();

							/// New format is an xml
							final Matcher roleMatcher = ROLE_PATTERN.matcher(line);
							String menurolename = null;
							if (roleMatcher.find()) {
								menurolename = roleMatcher.group(1);
							}

							/// Keeping old format for compatibility
							if (menurolename == null) {
								/// Format pour l'instant: code_portfolio,tag_semantique,label@en/libelle@fr,reles[;autre menu]
								final String[] tokens = line.split(",");
								if (tokens.length == 4) {
									menurolename = tokens[3];
								}
							}

							if (menurolename != null) {
								// Break down list of roles
								final String[] roles = menurolename.split(" ");
								for (final String s : roles) {
									resolve.groups.put(s.trim(), 0);
								}
							}
						}
					}
					final Node notifyroles = attribMap.getNamedItem("notifyroles");
					if (notifyroles != null) {
						/// Format pour l'instant: notifyroles="sender responsable"
						final StringTokenizer tokens = new StringTokenizer(notifyroles.getNodeValue(), " ");
						StringBuilder merge = new StringBuilder();
						if (tokens.hasMoreElements()) {
							merge = new StringBuilder(tokens.nextElement().toString().trim());
						}
						while (tokens.hasMoreElements()) {
							merge.append(",").append(tokens.nextElement().toString().trim());
						}
						role.setNotify(merge.toString());
					}

					// Check if base portfolio is public
					if (cred.isPublic(c, null, portfolioUuid)) {
						setPublic = true;
					}
					//					/*
					meta = res.getString("metadata");
					nodeString = "<?xml version='1.0' encoding='UTF-8' standalone='no'?><transfer " + meta + "/>";
					is = new InputSource(new StringReader(nodeString));
					doc = documentBuilder.parse(is);
					attribNode = doc.getDocumentElement();
					attribMap = attribNode.getAttributes();
					final Node publicatt = attribMap.getNamedItem("public");
					if (publicatt != null && DB_YES.equals(publicatt.getNodeValue())) {
						setPublic = true;
						//*/
					}
				} catch (final Exception e) {
					e.printStackTrace();
				}
			}

			res.close();
			st.close();

			/// Clear previous rights and groups
			// Rights on node
			String clear = "DELETE FROM group_rights WHERE grid IN (SELECT grid FROM group_right_info WHERE portfolio_id=uuid2bin(?))";
			PreparedStatement stclear = c.prepareStatement(clear);
			stclear.setString(1, portfolioUuid);
			stclear.execute();
			stclear.close();

			/// Users in group
			clear = "DELETE FROM group_user WHERE gid IN (SELECT gid FROM group_info gi, group_right_info gri WHERE portfolio_id=uuid2bin(?) AND gi.grid=gri.grid)";
			stclear = c.prepareStatement(clear);
			stclear.setString(1, portfolioUuid);
			stclear.execute();
			stclear.close();

			/// User groups
			clear = "DELETE FROM group_info WHERE grid IN (SELECT grid FROM group_right_info WHERE portfolio_id=uuid2bin(?))";
			stclear = c.prepareStatement(clear);
			stclear.setString(1, portfolioUuid);
			stclear.execute();
			stclear.close();

			/// Rights group
			clear = "DELETE FROM group_right_info WHERE portfolio_id=uuid2bin(?)";
			stclear = c.prepareStatement(clear);
			stclear.setString(1, portfolioUuid);
			stclear.execute();
			stclear.close();

			c.setAutoCommit(false);

			/// On insere les donnees pre-compile
			final Iterator<String> entries = resolve.groups.keySet().iterator();

			// Cree les groupes, ils n'existent pas
			final String grquery = "INSERT INTO group_info(grid,owner,label) " + "VALUES(?,?,?)";
			final PreparedStatement st2 = c.prepareStatement(grquery);
			final String gri = "INSERT INTO group_right_info(owner, label, change_rights, portfolio_id) " +
					"VALUES(?,?,?,uuid2bin(?))";
			if ("mysql".equals(dbserveur)) {
				st = c.prepareStatement(gri, Statement.RETURN_GENERATED_KEYS);
			}
			if (dbserveur.equals("oracle")) {
				st = c.prepareStatement(gri, new String[] { "grid" });
			}

			while (entries.hasNext()) {
				final String label = entries.next();
				st.setInt(1, 1);
				st.setString(2, label);
				st.setInt(3, 0);
				st.setString(4, portfolioUuid);

				st.execute();
				final ResultSet keys = st.getGeneratedKeys();
				keys.next();
				final int grid = keys.getInt(1);
				resolve.groups.put(label, grid);

				st2.setInt(1, grid);
				st2.setInt(2, 1);
				st2.setString(3, label);
				st2.execute();
			}
			st2.close();
			st.close();

			/// Ajout des droits des noeuds
			final String insertRight = "INSERT INTO group_rights(grid, id, RD, WR, DL, SB, AD, types_id, rules_id, notify_roles) " +
					"VALUES(?,uuid2bin(?),?,?,?,?,?,?,?,?)";
			st = c.prepareStatement(insertRight);

			for (final Entry<String, groupright> entry : resolve.resolve.entrySet()) {
				final String uuid = entry.getKey();
				final groupright gr = entry.getValue();

				for (final Entry<String, right> rightelem : gr.rights.entrySet()) {
					final String group = rightelem.getKey();
					final int grid = resolve.groups.get(group);
					final right rightval = rightelem.getValue();
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

			/// Create base group
			int groupid = postCreateRole(c, portfolioUuid, "all", userId);
			/// Finalement on cree un rele designer
			groupid = postCreateRole(c, portfolioUuid, "designer", userId);

			/// Ajoute la personne dans ce groupe
			putUserGroup(c, Integer.toString(groupid), Integer.toString(userId));

			// Update time
			touchPortfolio(c, null, portfolioUuid);

			/// Set portfolio public if needed
			if (setPublic) {
				setPublicState(c, userId, portfolioUuid, setPublic);
			}
		} catch (final Exception e) {
			logger.error("MESSAGE: " + e.getMessage() + " " + e.getLocalizedMessage());

			try {
				portfolioUuid = "erreur: " + e.getMessage();
				if (!c.getAutoCommit()) {
					c.rollback();
				}
			} catch (final SQLException e1) {
				e1.printStackTrace();
			}
			e.printStackTrace();
		} finally {
			try {
				c.setAutoCommit(true);
				// Les 'pooled connection' ne se ferment pas vraiment. On nettoie manuellement les tables temporaires...
				if (dbserveur.equals("mysql")) {
					sql = "DROP TEMPORARY TABLE IF EXISTS t_data, t_res, t_struc, t_struc_2, t_rights";
					st = c.prepareStatement(sql);
					st.execute();
					st.close();
				}
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}

		return portfolioUuid;
	}

	@Override
	public Object postPortfolioZip(Connection c, MimeType mimeType, MimeType mimeType2,
			HttpServletRequest httpServletRequest, InputStream inputStream, int userId, int groupId, String modelId,
			int substid, boolean parseRights, String projectName) throws Exception {
		if (!cred.isAdmin(c, userId) && !cred.isCreator(c, userId)) {
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");
		}

		//		boolean isMultipart = ServletFileUpload.isMultipartContent(httpServletRequest);
		// Create a factory for disk-based file items
		final DiskFileItemFactory factory = new DiskFileItemFactory();

		httpServletRequest.getSession().getServletContext();
		final File repository = new File(System.getProperty("java.io.tmpdir", null));
		factory.setRepository(repository);

		if (projectName == null) {
			projectName = "";
		} else {
			projectName = projectName.trim();
		}

		new ServletFileUpload(factory);

		final DataInputStream inZip = new DataInputStream(inputStream);
		// Parse the request
		String foldersfiles;
		String[] xmlFiles;
		String[] allFiles;
		final byte[] buff = new byte[0x100000]; // 1MB buffer

		final javax.servlet.http.HttpSession session = httpServletRequest.getSession(true);
		final String baseDirString = ConfigUtils.getInstance().getServletName() + "_files" + File.separator;
		final File baseDir = new File(repository, baseDirString);
		logger.info("Zip file will be saved on {}", baseDir.getCanonicalPath());
		// if the directory does not exist, create it
		if (!baseDir.exists()) {
			baseDir.mkdirs();
		}

		//Creation du zip
		final String portfolioUuidPreliminaire = UUID.randomUUID().toString();
		final File filezip = new File(baseDir, "xml_" + portfolioUuidPreliminaire + ".zip");
		if (!filezip.exists()) {
			filezip.createNewFile();
		}
		final FileOutputStream outZip = new FileOutputStream(filezip);

		int len;

		while ((len = inZip.read(buff)) != -1) {
			outZip.write(buff, 0, len);
		}

		inZip.close();
		outZip.close();

		//-- unzip --
		foldersfiles = unzip(filezip.getAbsolutePath(),
				baseDir.getAbsolutePath() + File.separator + portfolioUuidPreliminaire + File.separator);
		// Unzip just the next zip level. I hope there will be no zipped documents...
		final String[] zipFiles = findFiles(foldersfiles, "zip");
		for (final String zipFile : zipFiles) {
			unzip(zipFile, foldersfiles);
		}

		xmlFiles = findFiles(foldersfiles, "xml");
		allFiles = findFiles(foldersfiles, null);

		////// Lecture du fichier de portfolio
		final StringBuilder outTrace = new StringBuilder();
		//// Importation du portfolio
		//--- Read xml fileL ----
		///// Pour associer l'ancien uuid -> nouveau, pour les fichiers
		final HashMap<String, String> resolve = new HashMap<>();
		String portfolioUuid = "erreur";
		boolean hasLoaded = false;
		for (final String xmlFilepath : xmlFiles) {
			final String xmlFilename = xmlFilepath.substring(xmlFilepath.lastIndexOf(File.separator));
			if (xmlFilename.contains("_")) {
				continue; // Case when we add an xml in the portfolio
			}

			final BufferedReader br = new BufferedReader(
					new InputStreamReader(new FileInputStream(xmlFilepath), StandardCharsets.UTF_8));
			String line;
			final StringBuilder sb = new StringBuilder();

			while ((line = br.readLine()) != null) {
				sb.append(line.trim());
			}
			br.close();
			final String xml = sb.toString();

			portfolioUuid = UUID.randomUUID().toString();

			if (xml.contains("<portfolio")) // Le porfolio (peux mieux faire)
			{
				final Document doc = DomUtils.xmlString2Document(xml, outTrace);

				// Find code
				/// Cherche si on a deje envoye quelque chose
				final XPath xPath = XPathFactory.newInstance().newXPath();
				final String filterRes = "//*[local-name()='asmRoot']/*[local-name()='asmResource']/*[local-name()='code']";
				final NodeList nodelist = (NodeList) xPath.compile(filterRes).evaluate(doc, XPathConstants.NODESET);

				if (nodelist.getLength() > 0) {
					String code = nodelist.item(0).getTextContent();

					if (!"".equals(projectName)) // If a new name has been specified
					{
						// Find if it contains a project name
						final int dot = code.indexOf(".");
						if (dot <= 0) {
							continue;
						}
						code = projectName + code.substring(dot);

						// Check if new code exists
						if (isCodeExist(c, code, null)) {
							throw new RestWebApplicationException(Status.CONFLICT, "Existing code.");
						}

						// Replace content
						nodelist.item(0).setTextContent(code);
					} else // Simple query
					if (isCodeExist(c, code, null)) {
						throw new RestWebApplicationException(Status.CONFLICT, "Existing code.");
					}
				}

				// Check if it needs replacing

				Node rootNode = (doc.getElementsByTagName("portfolio")).item(0);
				if (rootNode == null) {
					throw new Exception("Root Node (portfolio) not found !");
				}
				rootNode = (doc.getElementsByTagName("asmRoot")).item(0);

				final String uuid = UUID.randomUUID().toString();

				insertMysqlPortfolio(c, portfolioUuid, uuid, 0, userId);

				writeNode(c, rootNode, portfolioUuid, null, userId, 0, uuid, null, 0, 0, false, resolve, parseRights);
				updateMysqlPortfolioActive(c, portfolioUuid, true);

				/// Create base group
				int groupid = postCreateRole(c, portfolioUuid, "all", userId);
				/// Finalement on cree un rele designer
				groupid = postCreateRole(c, portfolioUuid, "designer", userId);

				/// Ajoute la personne dans ce groupe
				putUserGroup(c, Integer.toString(groupid), Integer.toString(userId));

				hasLoaded = true;
			}
		}

		if (hasLoaded) {
			for (final String fullPath : allFiles) {
				final String tmpFileName = fullPath.substring(fullPath.lastIndexOf(File.separator) + 1);

				if (!tmpFileName.contains("_")) {
					continue; // We want ressources now, they have '_' in their name
				}
				int index = tmpFileName.indexOf("_");
				if (index == -1) {
					index = tmpFileName.indexOf(".");
				}
				int last = tmpFileName.lastIndexOf(File.separator);
				if (last == -1) {
					last = 0;
				}
				final String uuid = tmpFileName.substring(last, index);

				String lang;
				try {
					lang = tmpFileName.substring(index + 1, index + 3);

					if ("un".equals(lang)) { // Hack sort of fixing previous implementation
						lang = "en";
					}
				} catch (final Exception ex) {
					lang = "";
				}

				String extension;
				try {
					extension = tmpFileName.substring(tmpFileName.lastIndexOf(".") + 1);
				} catch (final Exception ex) {
					extension = null;
				}

				FileUtils.getMimeTypeFromExtension(extension);

				// Attention on initialise la ligne file
				// avec l'UUID d'origine de l'asmContext parent
				// Il sera mis e jour avec l'UUID asmContext final dans writeNode
				try {
					UUID.fromString(uuid);
					final String resolved = resolve.get(uuid); /// New uuid
					final String sessionval = session.getId();
					final String user = (String) session.getAttribute("user");
					final File file = new File(fullPath);

					if (resolved != null) {
						/// Have to send it in FORM, compatibility with regular file posting
						PostForm.rewriteFile(sessionval, backend, user, resolved, lang, file);
					}
				} catch (final Exception ex) {
					// Le nom du fichier ne commence pas par un UUID,
					// ce n'est donc pas une ressource
					ex.printStackTrace();
				}
			}
		}

		/// Need to delete files before removing folder
		for (final String filename_item : allFiles) {
			final File file = new File(filename_item);
			file.delete();
		}
		//		File zipfile = new File(filename);
		filezip.delete();
		final File zipdir = new File(foldersfiles);
		zipdir.delete();
		baseDir.delete(); /// If another import is running, won't delete directory

		return portfolioUuid;
	}

	@Override
	public Object postResource(Connection c, MimeType inMimeType, String nodeParentUuid, String in, int userId,
			int groupId) throws Exception {
		if (!cred.isAdmin(c, userId)) {
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");
		}

		in = DomUtils.filterXmlResource(in);

		if (!cred.hasNodeRight(c, userId, groupId, nodeParentUuid, Credential.WRITE)) {
			throw new RestWebApplicationException(Status.FORBIDDEN, " No WRITE credential ");
		}
		postNode(c, inMimeType, nodeParentUuid, in, userId, groupId, true);

		return "";
	}

	@Override
	public boolean postRightGroup(Connection c, int groupRightId, int groupId, Integer userId) {
		PreparedStatement stUpdate;
		String sqlUpdate;

		if (!cred.isAdmin(c, userId)) {
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");
		}

		try {
			sqlUpdate = "UPDATE group_info SET grid=? WHERE gid=?";
			stUpdate = c.prepareStatement(sqlUpdate);
			stUpdate.setInt(1, groupRightId);
			stUpdate.setInt(2, groupId);
			stUpdate.executeUpdate();
			return true;
		} catch (final Exception ex) {
			ex.printStackTrace();
			return false;
		}
	}

	@Override
	public String postRights(Connection c, int userId, String uuid, String role, NodeRight rights) {
		if (!cred.isAdmin(c, userId) && !cred.isOwnerFromNode(c, userId, uuid)) {
			throw new RestWebApplicationException(Status.FORBIDDEN, "No rights");
		}

		try {
			/// Oracle can't do anything right
			final String sqlgrid = "SELECT gr.grid " +
					"FROM group_info gi, group_rights gr " +
					"WHERE gi.grid=gr.grid AND gi.label=? AND gr.id=uuid2bin(?)";
			PreparedStatement st = c.prepareStatement(sqlgrid);
			st.setString(1, role);
			st.setString(2, uuid);
			final ResultSet res = st.executeQuery();
			int grid = -1;
			if (res.next()) {
				grid = res.getInt("grid");
			}
			res.close();
			st.close();

			final ArrayList<String[]> args = new ArrayList<>();
			if (rights.read != null) {
				final String[] arg = { "gr.RD", rights.read.toString() };
				args.add(arg);
			}
			if (rights.write != null) {
				final String[] arg = { "gr.WR", rights.write.toString() };
				args.add(arg);
			}
			if (rights.delete != null) {
				final String[] arg = { "gr.DL", rights.delete.toString() };
				args.add(arg);
			}
			if (rights.submit != null) {
				final String[] arg = { "gr.SB", rights.submit.toString() };
				args.add(arg);
			}

			if (args.isEmpty()) {
				return "";
			}

			String[] arg = args.get(0);
			final StringBuilder sql = new StringBuilder("UPDATE group_rights gr SET " + arg[0] + "=?");

			for (int i = 1; i < args.size(); ++i) {
				arg = args.get(i);
				sql.append(", ").append(arg[0]).append("=?");
			}
			sql.append(" WHERE gr.grid=? AND gr.id=uuid2bin(?)");
			st = c.prepareStatement(sql.toString());

			int i = 1;
			do {
				arg = args.get(i - 1);
				int val = 0;
				if (Boolean.parseBoolean(arg[1])) {
					val = 1;
				}
				st.setInt(i, val);
				++i;
			} while (i <= args.size());

			st.setInt(i, grid);
			++i;
			st.setString(i, uuid);

			st.executeUpdate();
			st.close();
		} catch (final Exception e) {
			logger.error("Exception", e);
		}

		return "ok";
	}

	@Override
	public String postRoleUser(Connection c, int userId, int grid, Integer userid2) throws SQLException {
		if (!cred.isAdmin(c, userId)) {
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");
		}

		String label = null;
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

		/// Verifie si un groupe existe, deje associe e un rele
		if (!res.next()) {
			sql = "SELECT * FROM group_right_info WHERE grid = ?";

			st = c.prepareStatement(sql);
			st.setInt(1, grid);

			res1 = st.executeQuery();

			if (res1.next()) {

				label = res1.getString("label");
				res1.getString("portfolio_id");
				res1.getString("change_rights");
				owner = res1.getInt("owner");
			}

			/// Synchronise les valeurs du rele avec le groupe d'utilisateur
			sqlInsert = "REPLACE INTO group_info(grid, owner, label) VALUES (?, ?, ?)";
			stInsert = c.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS);
			if (dbserveur.equals("oracle")) {
				sqlInsert = "MERGE INTO group_info d using (SELECT ? grid,? owner,? label from dual) s ON (1=2) WHEN NOT MATCHED THEN INSERT (d.grid, d.owner, d.label) values (s.grid, s.owner, s.label)";
				stInsert = c.prepareStatement(sqlInsert, new String[] { "gid" });
			}

			stInsert.setInt(1, grid);
			stInsert.setInt(2, owner);
			stInsert.setString(3, label);
			stInsert.executeUpdate();

			final ResultSet rs = stInsert.getGeneratedKeys();

			if (rs.next()) {
				gid = rs.getInt(1);
			}

			// Ajoute la personne
			sqlInsert = "REPLACE INTO group_user(gid, userid) VALUES (?, ?)";
			stInsert = c.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS);
			if (dbserveur.equals("oracle")) {
				sqlInsert = "MERGE INTO group_user d using (SELECT ? gid,? userid FROM DUAL) s ON (d.gid=s.gid AND d.userid=s.userid) WHEN NOT MATCHED THEN INSERT (d.gid, d.userid) VALUES (s.gid, s.userid)";
				stInsert = c.prepareStatement(sqlInsert);
			}

			stInsert.setInt(1, gid);
			stInsert.setInt(2, userid2);
			stInsert.executeUpdate();

		} else {

			gid = res.getInt("gid");

			sqlInsert = "REPLACE INTO group_user(gid, userid) VALUES (?, ?)";
			stInsert = c.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS);
			if (dbserveur.equals("oracle")) {
				sqlInsert = "MERGE INTO group_user d using (SELECT ? gid,? userid FROM DUAL) s ON (d.gid=s.gid AND d.userid=s.userid) WHEN NOT MATCHED THEN INSERT (d.gid, d.userid) VALUES (s.gid, s.userid)";
				stInsert = c.prepareStatement(sqlInsert);
			}

			stInsert.setInt(1, gid);
			stInsert.setInt(2, userid2);
			stInsert.executeUpdate();

		}

		return "user " + userid2 + " rajoute au groupd gid " + gid + " pour correspondre au groupRight grid " + grid;
	}

	@Override
	public String postRRGCreate(Connection c, int userId, String portfolio, String data) {
		if (!cred.isAdmin(c, userId) && !cred.isOwner(c, userId, portfolio)) {
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");
		}

		String value = "erreur";
		/// Parse data
		DocumentBuilder documentBuilder;
		Document document = null;
		final DocumentBuilderFactory documentBuilderFactory = DomUtils.newSecureDocumentBuilderFactory();

		try {
			documentBuilder = documentBuilderFactory.newDocumentBuilder();
			final InputSource is = new InputSource(new StringReader(data));
			document = documentBuilder.parse(is);
		} catch (final Exception e) {
			logger.error("Managed error:", e.getMessage());
			e.printStackTrace();
		}

		/// Probleme de parsage
		if (document == null) {
			return value;
		}

		try {
			c.setAutoCommit(false);
			final Element labelNode = document.getDocumentElement();
			String label = null;
			//      NodeList rrgNodes = document.getElementsByTagName("rolerightsgroup");

			final String sqlRRG = "INSERT INTO group_right_info(owner,label,portfolio_id) VALUES(?,?,uuid2bin(?))";
			PreparedStatement rrgst = c.prepareStatement(sqlRRG, Statement.RETURN_GENERATED_KEYS);
			if (dbserveur.equals("oracle")) {
				rrgst = c.prepareStatement(sqlRRG, new String[] { "grid" });
			}
			rrgst.setInt(1, userId);

			if (labelNode != null) {
				final Node labelText = labelNode.getFirstChild();
				if (labelText != null) {
					label = labelText.getNodeValue();
				}
			}

			if (label == null) {
				return value;
			}
			/// Creation du groupe de droit
			rrgst.setString(2, label);
			rrgst.setString(3, portfolio);
			rrgst.executeUpdate();

			final ResultSet rs = rrgst.getGeneratedKeys();
			int grid = 0;
			if (rs.next()) {
				grid = rs.getInt(1);
			}
			rrgst.close();
			labelNode.setAttribute("id", Integer.toString(grid));

			/// Recupere les donnees avec identifiant mis-e-jour
			final StringWriter stw = new StringWriter();
			final Transformer serializer = TransformerFactory.newInstance().newTransformer();
			final DOMSource source = new DOMSource(document);
			final StreamResult stream = new StreamResult(stw);
			serializer.transform(source, stream);
			value = stw.toString();
		} catch (final Exception e) {
			try {
				c.rollback();
			} catch (final SQLException e1) {
				e1.printStackTrace();
			}
			e.printStackTrace();
		} finally {
			try {
				c.setAutoCommit(true);
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}

		return value;
	}

	@Override
	public String postRRGUser(Connection c, int userId, Integer rrgid, Integer user) {
		if (!cred.isAdmin(c, userId) && !cred.isOwnerRRG(c, userId, rrgid)) {
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");
		}

		final String value = "";
		ResultSet res;
		try {
			c.setAutoCommit(false);

			/// Verifie si un group_info/grid existe
			final String sqlCheck = "SELECT gid FROM group_info WHERE grid=?";
			PreparedStatement st = c.prepareStatement(sqlCheck);
			st.setInt(1, rrgid);
			res = st.executeQuery();

			if (!res.next()) {
				/// Copie de RRG vers group_info
				final String sqlCopy = "INSERT INTO group_info(grid,owner,label)" +
						" SELECT grid,owner,label FROM group_right_info WHERE grid=?";
				st = c.prepareStatement(sqlCopy);
				st.setInt(1, rrgid);
				st.executeUpdate();
				st.close();
			}

			/// Ajout des utilisateurs
			String sqlUser = "";
			if (dbserveur.equals("mysql")) {
				sqlUser = "INSERT IGNORE INTO group_user(gid,userid) ";
			} else if (dbserveur.equals("oracle")) {
				sqlUser = "INSERT /*+ ignore_row_on_dupkey_index(group_user,group_user_PK)*/ INTO group_user(gid,userid) ";
			}
			sqlUser += "SELECT gi.gid,? FROM group_info gi WHERE gi.grid=?";
			st = c.prepareStatement(sqlUser);
			st.setInt(1, user);
			st.setInt(2, rrgid);
			st.executeUpdate();
			st.close();
		} catch (final Exception e) {
			try {
				c.rollback();
			} catch (final SQLException e1) {
				e1.printStackTrace();
			}
			e.printStackTrace();
		} finally {
			try {
				c.setAutoCommit(true);
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}

		return value;
	}

	@Override
	public String postRRGUsers(Connection c, int userId, Integer rrgid, String data) {
		if (!cred.isAdmin(c, userId) && !cred.isOwnerRRG(c, userId, rrgid) && !cred.isSharer(c, userId)) {
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");
		}

		final String value = "";
		/// Parse data
		DocumentBuilder documentBuilder;
		Document document = null;
		try {
			final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
			documentBuilder = documentBuilderFactory.newDocumentBuilder();
			final InputSource is = new InputSource(new StringReader(data));
			document = documentBuilder.parse(is);
		} catch (final Exception e) {
			logger.error("Exception", e);
		}

		/// Probleme de parsage
		if (document == null) {
			return value;
		}

		try {
			c.setAutoCommit(false);
			final Element root = document.getDocumentElement();

			/// Ajout des utilisateurs
			final NodeList users = root.getElementsByTagName("user");
			String sqlUser = "";
			if (dbserveur.equals("mysql")) {
				sqlUser = "INSERT IGNORE INTO group_user(gid,userid) ";
			} else if (dbserveur.equals("oracle")) {
				sqlUser = "INSERT /*+ ignore_row_on_dupkey_index(group_user,group_user_PK)*/ INTO group_user(gid,userid) ";
			}
			sqlUser += "SELECT gi.gid,? FROM group_info gi WHERE gi.grid=?";
			final PreparedStatement st = c.prepareStatement(sqlUser);
			st.setInt(2, rrgid);
			for (int j = 0; j < users.getLength(); ++j) {
				final Element user = (Element) users.item(j);
				final String uidl = user.getAttribute("id");
				final int uid = Integer.parseInt(uidl);
				st.setInt(1, uid);
				st.executeUpdate();
			}
			st.close();
		} catch (final Exception e) {
			try {
				c.rollback();
			} catch (final SQLException e1) {
				e1.printStackTrace();
			}
			e.printStackTrace();
		} finally {
			try {
				c.setAutoCommit(true);
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}

		return value;
	}

	@Override
	public Object postUser(Connection c, String in, int userId) throws Exception {
		if (!cred.isAdmin(c, userId)) {
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");
		}

		StringBuilder result;
		String login = null;
		String firstname = null;
		String lastname = null;
		String label = null;
		String password = null;
		String active = "1";
		String substitute = null;
		String email = null;
		int uuid = 0;
		int newId = 0;

		//On prepare les requetes SQL
		PreparedStatement stInsert;
		String sqlInsert;

		//On recupere le body
		final Document doc = DomUtils.xmlString2Document(in, new StringBuilder());
		final Element etu = doc.getDocumentElement();

		//On verifie le bon format
		if (etu.getNodeName().equals("user")) {
			//On recupere les attributs
			try {
				if (etu.getAttributes().getNamedItem("uid") != null) {
					login = etu.getAttributes().getNamedItem("uid").getNodeValue();
					final String uid = getMysqlUserUid(c, login);

					if (uid != null) {
						uuid = Integer.parseInt(uid);
					}
				}

				if (etu.getAttributes().getNamedItem("firstname") != null) {
					firstname = etu.getAttributes().getNamedItem("firstname").getNodeValue();
				}

				if (etu.getAttributes().getNamedItem("lastname") != null) {
					lastname = etu.getAttributes().getNamedItem("lastname").getNodeValue();
				}

				if (etu.getAttributes().getNamedItem("label") != null) {
					label = etu.getAttributes().getNamedItem("label").getNodeValue();
				}

				if (etu.getAttributes().getNamedItem("password") != null) {
					password = etu.getAttributes().getNamedItem("password").getNodeValue();
				}

				if (etu.getAttributes().getNamedItem("email") != null) {
					email = etu.getAttributes().getNamedItem("email").getNodeValue();
				}

				if (etu.getAttributes().getNamedItem("active") != null) {
					active = etu.getAttributes().getNamedItem("active").getNodeValue();
				}

				if (etu.getAttributes().getNamedItem("substitute") != null) {
					substitute = etu.getAttributes().getNamedItem("substitute").getNodeValue();
				}
			} catch (final Exception ex) {
				logger.error("Managed error", ex);
			}

		} else {
			result = new StringBuilder("Erreur lors de la recuperation des attributs de l'utilisateur dans le XML");
		}

		//On ajoute l'utilisateur dans la base de donnees
		if (etu.getAttributes().getNamedItem("firstname") != null
				&& etu.getAttributes().getNamedItem("lastname") != null
				&& etu.getAttributes().getNamedItem("label") == null) {

			sqlInsert = "REPLACE INTO credential(userid, login, display_firstname, display_lastname, email, password, active) VALUES (?, ?, ?, ?, ?, UNHEX(SHA1(?)),?)";
			stInsert = c.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS);
			if (dbserveur.equals("oracle")) {
				sqlInsert = "MERGE INTO credential d USING (SELECT ? userid,? login,? display_firstname,? display_lastname, ? email, crypt(?) password,? active FROM DUAL) s ON (d.userid=s.userid) WHEN MATCHED THEN UPDATE SET d.login=s.login, d.display_firstname = s.display_firstname, d.display_lastname = s.display_lastname, d.email = s.email, d.password = s.password, d.active = s.active WHEN NOT MATCHED THEN INSERT (d.userid, d.login, d.display_firstname, d.display_lastname, d.password, d.active) VALUES (s.userid, s.login, s.display_firstname, s.display_lastname, s.password, s.active)";
				stInsert = c.prepareStatement(sqlInsert, new String[] { "userid" });
			}
			stInsert.setInt(1, uuid);
			stInsert.setString(2, login);
			stInsert.setString(3, firstname);
			stInsert.setString(4, lastname);
			stInsert.setString(5, email);
			stInsert.setString(6, password);
			stInsert.setString(7, active);
			stInsert.executeUpdate();
		} else {
			sqlInsert = "REPLACE INTO credential(userid, login, display_firstname, display_lastname, email, password, active) VALUES (?, ?, ?, ?, ?, UNHEX(SHA1(?)),?)";
			stInsert = c.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS);
			if (dbserveur.equals("oracle")) {
				sqlInsert = "MERGE INTO credential d USING (SELECT ? userid,? login,? display_firstname,? display_lastname, ? email, crypt(?) password,? active FROM DUAL) s ON (d.userid=s.userid) WHEN MATCHED THEN UPDATE SET d.login=s.login, d.display_firstname = s.display_firstname, d.display_lastname = s.display_lastname, d.email = s.email, d.password = s.password, d.active = s.active WHEN NOT MATCHED THEN INSERT (d.userid, d.login, d.display_firstname, d.display_lastname, d.password, d.active) VALUES (s.userid, s.login, s.display_firstname, s.display_lastname, s.password, s.active)";
				stInsert = c.prepareStatement(sqlInsert, new String[] { "userid" });
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

		final ResultSet rs = stInsert.getGeneratedKeys();
		if (rs.next()) {
			newId = rs.getInt(1);
		}
		stInsert.close();
		rs.close();

		/// Add the possibility of user substitution
		PreparedStatement subst = null;
		/// FIXME: More complete rule to use
		if ("1".equals(substitute)) { // id=0, don't check who this person can substitute (except root)
			final String sql = "INSERT IGNORE INTO credential_substitution(userid, id, type) VALUES(?,0,'USER')";
			subst = c.prepareStatement(sql);
			subst.setInt(1, uuid);
			subst.execute();
		} else if ("0".equals(substitute)) {
			final String sql = "DELETE FROM credential_substitution WHERE userid=? AND id=0";
			subst = c.prepareStatement(sql);
			subst.setInt(1, uuid);
			subst.execute();
		}
		if (subst != null) {
			subst.close();
		}

		//On renvoie le body pour qu'il soit stocke dans le log
		result = new StringBuilder("<user ");
		result.append(DomUtils.getXmlAttributeOutput("uid", login)).append(" ");
		result.append(DomUtils.getXmlAttributeOutput("firstname", firstname)).append(" ");
		result.append(DomUtils.getXmlAttributeOutput("lastname", lastname)).append(" ");
		result.append(DomUtils.getXmlAttributeOutput("label", label)).append(" ");
		result.append(DomUtils.getXmlAttributeOutput("email", email)).append(" ");
		result.append(DomUtils.getXmlAttributeOutput("password", password)).append(" ");
		result.append(DomUtils.getXmlAttributeOutputInt("uuid", newId)).append(" ");
		result.append(DomUtils.getXmlAttributeOutput("substitute", substitute)).append(" ");
		result.append(">");
		result.append("</user>");

		return result.toString();
	}

	/********************************************************/
	/**
	 * ## ## ##### ####### ##### ### ###### ## ## ## ## ## ## ## ## ## ## ## ## ##
	 * ## ## ## ## ## ## ## ## ## ##### #### ##### ## ### ###### ## ## ## ## ## ##
	 * ## ## ## ## ## ## ## ## ## ## ## ## ## ## ## ##### ##### ####### ## ## ### ##
	 * ## /** Managing and listing user groups /
	 ********************************************************/
	@Override
	public int postUserGroup(Connection c, String label, int userid) {
		String sql;
		PreparedStatement st = null;
		ResultSet res = null;
		int groupid = -1;

		try {
			sql = "INSERT INTO credential_group(label) VALUE(?)";
			if (dbserveur.equals("oracle")) {
				st = c.prepareStatement(sql, new String[] { "cg" });
			} else {
				st = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
			}
			st.setString(1, label);
			st.executeUpdate();
			res = st.getGeneratedKeys();
			if (res.next()) {
				groupid = res.getInt(1);
			}

		} catch (final SQLException e) {
			logger.error(e.getMessage());
			e.printStackTrace();
		} finally {
			try {
				if (res != null) {
					res.close();
				}
				if (st != null) {
					st.close();
				}
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}

		return groupid;
	}

	@Override
	public String postUsers(Connection c, String in, int userId) throws Exception {
		if (!cred.isAdmin(c, userId) && !cred.isCreator(c, userId)) {
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");
		}

		String result = null;
		//On prepare les requetes SQL
		PreparedStatement stInsert;
		String sqlInsert;

		//On recupere le body
		Document doc;

		doc = DomUtils.xmlString2Document(in, new StringBuilder());
		final Element users = doc.getDocumentElement();

		NodeList children;

		children = users.getChildNodes();
		// On parcourt une premiere fois les enfants pour recuperer la liste e ecrire en base

		//On verifie le bon format
		final StringBuilder userdone = new StringBuilder();
		userdone.append("<users>");
		String username = null;
		try {
			if (users.getNodeName().equals("users")) {
				c.setAutoCommit(false);

				for (int i = 0; i < children.getLength(); i++) {
					String password = null;
					String firstname = null;
					String lastname = null;
					String email = null;
					String designerstr = null;
					String active = "1";
					String substitute = null;
					String other = "";
					int id = 0;
					int designer;

					if (children.item(i).getNodeName().equals("user")) {
						NodeList children2;
						children2 = children.item(i).getChildNodes();
						for (int y = 0; y < children2.getLength(); y++) {
							if (children2.item(y).getNodeName().equals("username")) {
								username = DomUtils.getInnerXml(children2.item(y));
							}
							if (children2.item(y).getNodeName().equals("password")) {
								password = DomUtils.getInnerXml(children2.item(y));
							}
							if (children2.item(y).getNodeName().equals("firstname")) {
								firstname = DomUtils.getInnerXml(children2.item(y));
							}
							if (children2.item(y).getNodeName().equals("lastname")) {
								lastname = DomUtils.getInnerXml(children2.item(y));
							}
							if (children2.item(y).getNodeName().equals("email")) {
								email = DomUtils.getInnerXml(children2.item(y));
							}
							if (children2.item(y).getNodeName().equals("active")) {
								active = DomUtils.getInnerXml(children2.item(y));
							}
							if (children2.item(y).getNodeName().equals("designer")) {
								designerstr = DomUtils.getInnerXml(children2.item(y));
							}
							if (children2.item(y).getNodeName().equals("substitute")) {
								substitute = DomUtils.getInnerXml(children2.item(y));
							}
							if (children2.item(y).getNodeName().equals("other")) {
								other = DomUtils.getInnerXml(children2.item(y));
							}
						}

						if (username != null && username.length() < 2) {
							continue;
						}
						if (password != null && password.length() < 2) {
							continue;
						}

						//On ajoute l'utilisateur dans la base de donnees
						sqlInsert = "INSERT INTO credential(login, display_firstname, display_lastname,email, password, active, is_designer, other) VALUES (?, ?, ?, ?, UNHEX(SHA1(?)),?,?,?)";
						stInsert = c.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS);
						if (dbserveur.equals("oracle")) {
							sqlInsert = "INSERT INTO credential(login, display_firstname, display_lastname,email, password, active, is_designer, other) VALUES (?, ?, ?, ?, crypt(?),?,?,?)";
							stInsert = c.prepareStatement(sqlInsert, new String[] { "userid" });
						}

						stInsert.setString(1, username);

						if (firstname == null) {
							firstname = " ";
						}
						stInsert.setString(2, firstname);

						if (lastname == null) {
							lastname = " ";
						}
						stInsert.setString(3, lastname);

						if (email == null) {
							email = " ";
						}
						stInsert.setString(4, email);

						stInsert.setString(5, password);

						if (active == null) {
							active = " ";
						}
						stInsert.setString(6, active);

						if ("1".equals(designerstr)) {
							designer = 1;
						} else {
							designer = 0;
						}
						stInsert.setInt(7, designer);
						stInsert.setString(8, other);

						stInsert.executeUpdate();

						final ResultSet rs = stInsert.getGeneratedKeys();
						if (rs.next()) {
							id = rs.getInt(1);
						}

						if (substitute != null) {
							PreparedStatement subst = null;
							/// FIXME: More complete rule to use
							if ("1".equals(substitute)) { // id=0, don't check who this person can substitute (except root)
								final String sql = "INSERT IGNORE INTO credential_substitution(userid, id, type) VALUES(?,0,'USER')";
								subst = c.prepareStatement(sql);
								subst.setInt(1, id);
								subst.execute();
							} else if ("0".equals(substitute)) {
								final String sql = "DELETE FROM credential_substitution WHERE userid=? AND id=0";
								subst = c.prepareStatement(sql);
								subst.setInt(1, id);
								subst.execute();
							}
							if (subst != null) {
								subst.close();
							}
						} else {
							substitute = "0";
						}

						userdone.append("<user ").append("id=\"").append(id).append("\">");
						userdone.append("<username>").append(username).append("</username>");
						userdone.append("<firstname>").append(firstname).append("</firstname>");
						userdone.append("<lastname>").append(lastname).append("</lastname>");
						userdone.append("<email>").append(email).append("</email>");
						userdone.append("<active>").append(active).append("</active>");
						userdone.append("<designer>").append(designerstr).append("</designer>");
						userdone.append("<substitute>").append(substitute).append("</substitute>");
						userdone.append("<other>").append(substitute).append("</other>");
						userdone.append("</user>");
					}
				}
			} else {
				result = "Missing \"users\" tag";
			}
		} catch (final SQLException e) {
			logger.error(e.getMessage());
			//			e.printStackTrace();
			c.rollback();
			result = "Error when processing user: " + username;
		} finally {
			c.setAutoCommit(true);
		}
		userdone.append("</users>");

		if (result == null) {
			result = userdone.toString();
		}

		return result;
	}

	@Override
	public String postUsersGroups(int userId) {
		// TODO Auto-generated method stub
		return null;
	}

	public String processMeta(Connection c, int userId, String meta) {
		try {
			/// FIXME: Patch court terme pour la migration
			/// Trouve le login associe au userId
			final String sql = "SELECT login FROM credential c " + "WHERE c.userid=?";
			final PreparedStatement st = c.prepareStatement(sql);
			st.setInt(1, userId);
			final ResultSet res = st.executeQuery();

			/// res.getFetchSize() retourne 0, meme avec un bon resultat
			String login = "";
			if (res.next()) {
				login = res.getString("login");
			}

			/// Remplace 'user' par le login de l'utilisateur
			final String onlyuser = "(?<![-=+])\b(user)\b(?![-=+])";
			meta = meta.replaceAll(onlyuser, login);

			/// Ajoute un droit dans la table

		} catch (final SQLException e) {
			e.printStackTrace();
		}

		return meta;
	}

	private void processQuery(ResultSet result, HashMap<String, Object[]> resolve, HashMap<String, t_tree> entries,
			String role) throws DOMException, SQLException {
		final StringBuilder data = new StringBuilder(256);
		if (result != null) {
			while (result.next()) {
				data.setLength(0);

				final String nodeUuid = result.getString("node_uuid");
				if (nodeUuid == null) {
					continue; // Cas où on a des droits sur plus de noeuds qui ne sont pas dans le portfolio
				}

				final String childsId = result.getString("node_children_uuid");

				final String type = result.getString("asm_type");

				data.append("<");
				data.append(type);
				data.append(" ");

				String xsi_type = result.getString("xsi_type");
				if (null == xsi_type) {
					xsi_type = "";
				}

				final String readRight = result.getInt("RD") == 1 ? DB_YES : DB_NO;
				final String writeRight = result.getInt("WR") == 1 ? DB_YES : DB_NO;
				final String submitRight = result.getInt("SB") == 1 ? DB_YES : DB_NO;
				final String deleteRight = result.getInt("DL") == 1 ? DB_YES : DB_NO;
				final String macro = result.getString("rules_id");
				final String nodeDate = result.getString("modif_date");

				if (macro != null) {
					data.append("action=\"");
					data.append(macro);
					data.append("\" ");
				}

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
				data.append("\" last_modif=\"");
				data.append(nodeDate);
				data.append("\" xsi_type=\"");
				data.append(xsi_type);
				data.append("\">");

				String attr = result.getString("metadata_wad");
				if (attr != null && !"".equals(attr)) /// Attributes exists
				{
					data.append("<metadata-wad ");
					data.append(attr);
					data.append("/>");
				} else {
					data.append("<metadata-wad/>");
				}

				attr = result.getString("metadata_epm");
				if (attr != null && !"".equals(attr)) /// Attributes exists
				{
					data.append("<metadata-epm ");
					data.append(attr);
					data.append("/>");
				} else {
					data.append("<metadata-epm/>");
				}

				attr = result.getString("metadata");
				if (attr != null && !"".equals(attr)) /// Attributes exists
				{
					data.append("<metadata ");
					data.append(attr);
					data.append("/>");
				} else {
					data.append("<metadata/>");
				}

				final String res_res_node_uuid = result.getString("res_res_node_uuid");
				if (res_res_node_uuid != null && res_res_node_uuid.length() > 0) {
					final String nodeContent = result.getString("r2_content");
					final String resModifdate = result.getString("r2_modif_date");
					if (nodeContent != null) {
						data.append("<asmResource contextid=\"");
						data.append(nodeUuid);
						data.append("\" id=\"");
						data.append(res_res_node_uuid);
						data.append("\" last_modif=\"");
						data.append(resModifdate);
						data.append("\" xsi_type=\"nodeRes\">");
						data.append(nodeContent.trim());
						data.append("</asmResource>");
					}
				}

				final String res_context_node_uuid = result.getString("res_context_node_uuid");
				if (res_context_node_uuid != null && res_context_node_uuid.length() > 0) {
					final String nodeContent = result.getString("r3_content");
					final String resModifdate = result.getString("r3_modif_date");
					if (nodeContent != null) {
						data.append("<asmResource contextid=\"");
						data.append(nodeUuid);
						data.append("\" id=\"");
						data.append(res_context_node_uuid);
						data.append("\" last_modif=\"");
						data.append(resModifdate);
						data.append("\" xsi_type=\"context\">");
						data.append(nodeContent.trim());
						data.append("</asmResource>");
					} else {
						data.append("<asmResource contextid=\"");
						data.append(nodeUuid);
						data.append("\" id=\"");
						data.append(res_context_node_uuid);
						data.append("\" xsi_type=\"context\"/>");
					}
				}

				final String res_node_uuid = result.getString("res_node_uuid");
				if (res_node_uuid != null && res_node_uuid.length() > 0) {
					final String nodeContent = result.getString("r1_content");
					final String resModifdate = result.getString("r1_modif_date");
					if (nodeContent != null) {
						data.append("<asmResource contextid=\"");
						data.append(nodeUuid);
						data.append("\" id=\"");
						data.append(res_node_uuid);
						data.append("\" last_modif=\"");
						data.append(resModifdate);
						data.append("\" xsi_type=\"");
						data.append(result.getString("r1_type"));
						data.append("\">");
						data.append(nodeContent.trim());
						data.append("</asmResource>");
					}
				}

				final String snode = data.toString();

				/// Prepare data to reconstruct tree
				final t_tree entry = new t_tree();
				entry.type = type;
				entry.data = snode;
				final Object[] nodeData = { snode, type };
				resolve.put(nodeUuid, nodeData);
				if (!"".equals(childsId) && childsId != null) {
					entry.childString = childsId;
				}
				entries.put(nodeUuid, entry);

			}
		}
	}

	@Override
	public String putInfUser(Connection c, int userId, int userid2, String in) throws SQLException {
		String result1;
		String originalp = null;
		String password = null;
		String email = null;
		String username = null;
		String firstname = null;
		String lastname = null;
		String active = null;
		String is_admin = null;
		String is_designer = null;
		String is_sharer = null;
		String hasSubstitute = null;
		String other = "";

		//On prepare les requetes SQL
		PreparedStatement st;
		String sql;

		//On recupere le body
		Document doc;
		Element infUser = null;
		try {
			doc = DomUtils.xmlString2Document(in, new StringBuilder());
			infUser = doc.getDocumentElement();
		} catch (final Exception e) {
			e.printStackTrace();
		}

		infUser.getChildNodes();

		if (infUser.getNodeName().equals("user")) {
			//On recupere les attributs

			if (infUser.getAttributes().getNamedItem("id") != null) {
				Integer.parseInt(infUser.getAttributes().getNamedItem("id").getNodeValue());
			} else {
			}
			NodeList children2;
			children2 = infUser.getChildNodes();
			/// Fetch parameters
			/// TODO Make some function out of this I think
			for (int y = 0; y < children2.getLength(); y++) {
				if (children2.item(y).getNodeName().equals("username")) {
					username = DomUtils.getInnerXml(children2.item(y));
				} else if (children2.item(y).getNodeName().equals("prevpass")) {
					originalp = DomUtils.getInnerXml(children2.item(y));
				} else if (children2.item(y).getNodeName().equals("password")) {
					password = DomUtils.getInnerXml(children2.item(y));
				} else if (children2.item(y).getNodeName().equals("firstname")) {
					firstname = DomUtils.getInnerXml(children2.item(y));
				} else if (children2.item(y).getNodeName().equals("lastname")) {
					lastname = DomUtils.getInnerXml(children2.item(y));
				} else if (children2.item(y).getNodeName().equals("email")) {
					email = DomUtils.getInnerXml(children2.item(y));
				} else if (children2.item(y).getNodeName().equals("admin")) {
					is_admin = DomUtils.getInnerXml(children2.item(y));
				} else if (children2.item(y).getNodeName().equals("designer")) {
					is_designer = DomUtils.getInnerXml(children2.item(y));
				} else if (children2.item(y).getNodeName().equals("sharer")) {
					is_sharer = DomUtils.getInnerXml(children2.item(y));
				} else if (children2.item(y).getNodeName().equals("active")) {
					active = DomUtils.getInnerXml(children2.item(y));
				} else if (children2.item(y).getNodeName().equals("substitute")) {
					hasSubstitute = DomUtils.getInnerXml(children2.item(y));
				} else if (children2.item(y).getNodeName().equals("other")) {
					other = DomUtils.getInnerXml(children2.item(y));
				}
			}

			/// Check if user has the correct password to execute changes
			boolean isOK = false;
			if (originalp != null) {
				sql = "SELECT userid FROM credential WHERE userid=? AND password=UNHEX(SHA1(?))";
				st = c.prepareStatement(sql);
				st.setInt(1, userId);
				st.setString(2, originalp);
				final ResultSet res = st.executeQuery();
				if (res.next()) {
					isOK = true;
				}
			}

			int changeLevel = 99;
			if (isOK) /// Password was provided for either admin or designer
			{
				if (cred.isAdmin(c, userId)) {
					changeLevel = 0;
				} else if (cred.isCreator(c, userId)) {
					changeLevel = 1;
				}
			} else if (cred.isAdmin(c, userId)) {
				changeLevel = 0;
			} else if (cred.isCreator(c, userId)) {
				changeLevel = 2;
			} else {
				changeLevel = 99;
			}

			switch (changeLevel) {
			case 0: // Do admin and creator changes, with password
				if (is_admin != null) {
					int is_adminInt = 0;
					if ("1".equals(is_admin)) {
						is_adminInt = 1;
					}

					sql = "UPDATE credential SET is_admin = ? WHERE  userid = ?";

					st = c.prepareStatement(sql);
					st.setInt(1, is_adminInt);
					st.setInt(2, userid2);
					st.executeUpdate();
				}
				/// Can continue setting rights
			case 1: // Only do creator changes, with password
				if (username != null) {
					sql = "UPDATE credential SET login = ? WHERE  userid = ?";

					st = c.prepareStatement(sql);
					st.setString(1, username);
					st.setInt(2, userid2);
					st.executeUpdate();
				}
				if (password != null) {
					sql = "UPDATE credential SET password = UNHEX(SHA1(?)) WHERE  userid = ?";
					if (dbserveur.equals("oracle")) {
						sql = "UPDATE credential SET password = crypt(?) WHERE  userid = ?";
					}

					st = c.prepareStatement(sql);
					st.setString(1, password);
					st.setInt(2, userid2);
					st.executeUpdate();
				}
				if (firstname != null) {
					sql = "UPDATE credential SET display_firstname = ? WHERE  userid = ?";

					st = c.prepareStatement(sql);
					st.setString(1, firstname);
					st.setInt(2, userid2);
					st.executeUpdate();
				}
				if (lastname != null) {
					sql = "UPDATE credential SET display_lastname = ? WHERE  userid = ?";

					st = c.prepareStatement(sql);
					st.setString(1, lastname);
					st.setInt(2, userid2);
					st.executeUpdate();
				}
				if (email != null) {
					sql = "UPDATE credential SET email = ? WHERE  userid = ?";

					st = c.prepareStatement(sql);
					st.setString(1, email);
					st.setInt(2, userid2);
					st.executeUpdate();
				}
				if (active != null) {
					int activeInt = 0;
					if ("1".equals(active)) {
						activeInt = 1;
					}

					sql = "UPDATE credential SET active = ? WHERE  userid = ?";

					st = c.prepareStatement(sql);
					st.setInt(1, activeInt);
					st.setInt(2, userid2);
					st.executeUpdate();
				}
				if (other != null) {
					sql = "UPDATE credential SET other = ? WHERE  userid = ?";

					st = c.prepareStatement(sql);
					st.setString(1, other);
					st.setInt(2, userid2);
					st.executeUpdate();
				}
				if (hasSubstitute != null) {
					/// Add the possibility of user substitution
					PreparedStatement subst = null;
					/// FIXME: More complete rule to use
					if ("1".equals(hasSubstitute)) { // id=0, don't check who this person can substitute (except root)
						if (dbserveur.equals("mysql")) {
							sql = "INSERT IGNORE INTO credential_substitution(userid, id, type) VALUES(?,0,'USER')";
						} else {
							sql = "MERGE INTO credential_substitution cs " +
									"USING " +
									"(select * from credential_substitution where type='USER' and id=0 and userid=?) t " +
									" ON (cs.userid=t.userid and cs.id=t.id and cs.type=t.type) " +
									" WHEN NOT MATCHED THEN " +
									" INSERT (userid, id, type) VALUES (t.userid,t.id,t.type)";
						}
						subst = c.prepareStatement(sql);
						subst.setInt(1, userid2);
						subst.execute();
					} else if ("0".equals(hasSubstitute)) {
						sql = "DELETE FROM credential_substitution WHERE userid=? AND id=0";
						subst = c.prepareStatement(sql);
						subst.setInt(1, userid2);
						subst.execute();
					}
					if (subst != null) {
						subst.close();
					}
				}
				/// Can continue setting rights
			case 2: /// admin/designer account without password given
				if (is_designer != null && userId == userid2) {
					int is_designerInt = 0;
					if ("1".equals(is_designer)) {
						is_designerInt = 1;
					}

					sql = "UPDATE credential SET is_designer = ? WHERE  userid = ?";

					st = c.prepareStatement(sql);
					st.setInt(1, is_designerInt);
					st.setInt(2, userId); // Change for self only
					st.executeUpdate();
				}
				if (is_sharer != null) {
					int is_sharerInt = 0;
					if ("1".equals(is_sharer)) {
						is_sharerInt = 1;
					}

					sql = "UPDATE credential SET is_sharer = ? WHERE  userid = ?";

					st = c.prepareStatement(sql);
					st.setInt(1, is_sharerInt);
					st.setInt(2, userid2);
					st.executeUpdate();
				}
				break;

			default:
				throw new RestWebApplicationException(Status.FORBIDDEN, "Not authorized");
			//					break;
			}

		}

		result1 = "" + userid2;

		return result1;
	}

	@Override
	public String putInfUserInternal(Connection c, int userId, int userid2, String fname, String lname, String email)
			throws SQLException {
		int isDesigner = 0;
		String other = "";
		if (createAsDesigner) {
			isDesigner = 1;
			other = "xlimited";
		}

		final String sql = "UPDATE credential SET display_firstname = ?, display_lastname = ?, email = ?, is_designer = ?, other = ? WHERE userid = ?";
		final PreparedStatement st = c.prepareStatement(sql);
		st.setString(1, fname);
		st.setString(2, lname);
		st.setString(3, email);
		st.setInt(4, isDesigner);
		st.setString(5, other);
		st.setInt(6, userid2);
		st.executeUpdate();

		return null;
	}

	@Override
	public Object putNode(Connection c, MimeType inMimeType, String nodeUuid, String in, int userId, int groupId)
			throws Exception {
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
		StringBuilder nodeChildrenUuid = null;

		int sharedRes = 0;
		int sharedNode = 0;
		int sharedNodeRes = 0;

		final long t_start = System.currentTimeMillis();

		//TODO putNode getNodeRight
		if (!cred.hasNodeRight(c, userId, groupId, nodeUuid, Credential.WRITE)) {
			throw new RestWebApplicationException(Status.FORBIDDEN, " No WRITE credential ");
		}

		final long t_rights = System.currentTimeMillis();

		final String inPars = DomUtils.cleanXMLData(in);
		final Document doc = DomUtils.xmlString2Document(inPars, new StringBuilder());
		// Puis on le recree
		Node node;
		node = doc.getDocumentElement();

		final long t_parsexml = System.currentTimeMillis();

		if (node == null) {
			return null;
		}

		try {
			if (node.getNodeName() != null) {
				asmType = node.getNodeName();
			}

			if (node.getAttributes().getNamedItem("xsi_type") != null) {
				xsiType = node.getAttributes().getNamedItem("xsi_type").getNodeValue();
			}

			if (node.getAttributes().getNamedItem("semtag") != null) {
				semtag = node.getAttributes().getNamedItem("semtag").getNodeValue();
			}

			if (node.getAttributes().getNamedItem("format") != null) {
				format = node.getAttributes().getNamedItem("format").getNodeValue();
			}
		} catch (final Exception ex) {
			logger.error("error on", ex);
		}

		// Si id defini, alors on ecrit en base
		//TODO Transactionnel noeud+enfant
		final NodeList children = node.getChildNodes();
		// On parcourt une premiere fois les enfants pour recuperer la liste e ecrire en base
		int j = 0;
		for (int i = 0; i < children.getLength(); i++) {
			final Node currentNode = children.item(i);
			if (!currentNode.getNodeName().equals("#text")) {
				// On verifie si l'enfant n'est pas un element de type code, label ou descr
				if (currentNode.getNodeName().equals("label")) {
					label = DomUtils.getInnerXml(children.item(i));
				} else if (currentNode.getNodeName().equals("code")) {
					code = DomUtils.getInnerXml(children.item(i));
				} else if (currentNode.getNodeName().equals("description")) {
					descr = DomUtils.getInnerXml(children.item(i));
				} else if (currentNode.getNodeName().equals("semanticTag")) {
					semtag = DomUtils.getInnerXml(children.item(i));
				} else if (currentNode.getNodeName().equals("asmResource")) {
					// Si le noeud est de type asmResource, on stocke le innerXML du noeud
					updateMysqlResourceByXsiType(c, nodeUuid,
							currentNode.getAttributes().getNamedItem("xsi_type").getNodeValue(),
							DomUtils.getInnerXml(children.item(i)), userId);
				} else if (currentNode.getNodeName().equals("metadata-wad")) {
					metadataWad = DomUtils.getNodeAttributesString(children.item(i));// " attr1=\"wad1\" attr2=\"wad2\" ";
				} else if (currentNode.getNodeName().equals("metadata-epm")) {
					metadataEpm = DomUtils.getNodeAttributesString(children.item(i));
				} else if (currentNode.getNodeName().equals("metadata")) {
					String tmpSharedRes = "";
					String tmpSharedNode = "";
					String tmpSharedNodeResource = "";
					try {
						if (currentNode.getAttributes().getNamedItem("sharedRes") != null) {
							tmpSharedRes = currentNode.getAttributes().getNamedItem("sharedRes").getNodeValue();
						}

						if (currentNode.getAttributes().getNamedItem("sharedNode") != null) {
							tmpSharedNode = currentNode.getAttributes().getNamedItem("sharedNode").getNodeValue();
						}

						if (currentNode.getAttributes().getNamedItem("sharedNodeResource") != null) {
							tmpSharedNodeResource = currentNode.getAttributes().getNamedItem("sharedNodeResource")
									.getNodeValue();
						}
					} catch (final Exception ex) {
						logger.error("Managed error", ex);
					}

					if (tmpSharedRes.equalsIgnoreCase(XML_YES)) {
						sharedRes = 1;
					}
					if (tmpSharedNode.equalsIgnoreCase(XML_YES)) {
						sharedNode = 1;
					}
					if (tmpSharedNodeResource.equalsIgnoreCase(XML_YES)) {
						sharedNodeRes = 1;
					}

					metadata = DomUtils.getNodeAttributesString(children.item(i));
				} else if (currentNode.getAttributes() != null) {
					if (currentNode.getAttributes().getNamedItem("id") != null) {
						if (nodeChildrenUuid == null) {
							nodeChildrenUuid = new StringBuilder();
						}
						if (j > 0) {
							nodeChildrenUuid.append(",");
						}
						nodeChildrenUuid.append(currentNode.getAttributes().getNamedItem("id").getNodeValue());
						updatetMySqlNodeOrder(c, currentNode.getAttributes().getNamedItem("id").getNodeValue(), j);
						logger.error("UPDATE NODE ORDER");
						j++;
					}
				}
			}
		}

		final long t_endparsing = System.currentTimeMillis();

		// Si le noeud est de type asmResource, on stocke le innerXML du noeud
		if (node.getNodeName().equals("asmResource")) {
			updateMysqlResource(c, nodeUuid, xsiType, DomUtils.getInnerXml(node), userId);
		}

		final long t_udpateRes = System.currentTimeMillis();

		if (nodeChildrenUuid != null) {
			updateMysqlNodeChildren(c, nodeUuid);
			//TODO UpdateNode different selon creation de modele ou instantiation copie
		}

		final long t_updateNodeChildren = System.currentTimeMillis();

		touchPortfolio(c, nodeUuid, null);

		final long t_touchPortfolio = System.currentTimeMillis();

		final int retval = updatetMySqlNode(c, nodeUuid, asmType, xsiType, semtag, label, code, descr, format, metadata,
				metadataWad, metadataEpm, sharedRes, sharedNode, sharedNodeRes, userId);

		final long t_udpateNode = System.currentTimeMillis();

		if (logger.isTraceEnabled()) {
			final long d_rights = t_rights - t_start;
			final long d_parsexml = t_parsexml - t_rights;
			final long d_parsenode = t_endparsing - t_parsexml;
			final long d_updRes = t_udpateRes - t_endparsing;
			final long d_updateOrder = t_updateNodeChildren - t_udpateRes;
			final long d_touchPort = t_touchPortfolio - t_updateNodeChildren;
			final long d_updatNode = t_udpateNode - t_touchPortfolio;

			logger.trace(
					"===== PUT Node =====\nCheck rights: {}\nParse XML: {}\nParse nodes: {}\nUpdate Resource: {}\nUpdate order: {}\nTouch portfolio: {}\nUpdate node: {}\n",
					d_rights, d_parsexml, d_parsenode, d_updRes, d_updateOrder, d_touchPort, d_updatNode);
		}

		return retval;
	}

	@Override
	public Object putNodeMetadata(Connection c, MimeType mimeType, String nodeUuid, String xmlNode, int userId,
			int groupId) throws Exception {
		String metadata;

		int sharedRes = 0;
		int sharedNode = 0;
		int sharedNodeRes = 0;

		//TODO putNode getNodeRight
		if (!cred.hasNodeRight(c, userId, groupId, nodeUuid, Credential.WRITE)) {
			throw new RestWebApplicationException(Status.FORBIDDEN, " No WRITE credential ");
		}

		String status = "erreur";

		final String portfolioUid = getPortfolioUuidByNodeUuid(c, nodeUuid);

		// D'abord on supprime les noeuds existants
		//deleteNode(nodeUuid, userId);
		xmlNode = DomUtils.cleanXMLData(xmlNode);
		final Document doc = DomUtils.xmlString2Document(xmlNode, new StringBuilder());

		// Puis on le recree
		Node node;
		node = doc.getDocumentElement();

		if (node.getNodeName().equals("metadata")) {

			String tag = "";
			final NamedNodeMap attr = node.getAttributes();

			/// Public has to be managed via the group/user function
			String tmpSharedRes;
			String tmpSharedNode;
			String tmpSharedNodeResource;
			try {
				Node currentNode = attr.getNamedItem("public");
				if (currentNode != null) {
					final String publicatt = currentNode.getNodeValue();
					if (DB_YES.equals(publicatt)) {
						setPublicState(c, userId, portfolioUid, true);
					} else if (DB_NO.equals(publicatt)) {
						setPublicState(c, userId, portfolioUid, false);
					}

				}

				currentNode = attr.getNamedItem("semantictag");
				if (currentNode != null) {
					tag = currentNode.getNodeValue();
				}

				currentNode = attr.getNamedItem("sharedResource");
				if (currentNode != null) {
					tmpSharedRes = currentNode.getNodeValue();
					if (tmpSharedRes.equalsIgnoreCase(XML_YES)) {
						sharedRes = 1;
					}
				}
				currentNode = attr.getNamedItem("sharedNode");
				if (currentNode != null) {
					tmpSharedNode = currentNode.getNodeValue();
					if (tmpSharedNode.equalsIgnoreCase(XML_YES)) {
						sharedNode = 1;
					}
				}

				currentNode = attr.getNamedItem("sharedNodeResource");
				if (currentNode != null) {
					tmpSharedNodeResource = currentNode.getNodeValue();
					if (tmpSharedNodeResource.equalsIgnoreCase(XML_YES)) {
						sharedNodeRes = 1;
					}
				}

				metadata = DomUtils.getNodeAttributesString(node);

				/// Mettre à jour les flags et donnee du champ
				final String sql = "UPDATE node SET metadata=?, semantictag=?, shared_res=?, shared_node=?, shared_node_res=? WHERE node_uuid=uuid2bin(?)";
				final PreparedStatement st = c.prepareStatement(sql);
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
			} catch (final Exception ex) {
				logger.error("Managed error", ex);
			}

		}

		return status;
	}

	@Override
	public Object putNodeMetadataEpm(Connection c, MimeType mimeType, String nodeUuid, String xmlNode, int userId,
			int groupId) throws Exception {
		if (!cred.hasNodeRight(c, userId, groupId, nodeUuid, Credential.WRITE)) {
			throw new RestWebApplicationException(Status.FORBIDDEN, " No WRITE credential ");
		}

		xmlNode = DomUtils.cleanXMLData(xmlNode);
		final Document doc = DomUtils.xmlString2Document(xmlNode, new StringBuilder());
		Node node;
		node = doc.getDocumentElement();

		String metadataepm = "";
		if (node.getNodeName().equals("metadata-epm")) {
			metadataepm = DomUtils.getNodeAttributesString(node);// " attr1=\"wad1\" attr2=\"wad2\" ";
		}

		String sql;
		PreparedStatement st;

		sql = "UPDATE node SET metadata_epm = ? " + "WHERE node_uuid = uuid2bin(?) ";
		st = c.prepareStatement(sql);

		st.setString(1, metadataepm);
		st.setString(2, nodeUuid);

		if (st.executeUpdate() == 1) {
			touchPortfolio(c, nodeUuid, null);
			return "editer";
		}

		return "erreur";
	}

	@Override
	public Object putNodeMetadataWad(Connection c, MimeType mimeType, String nodeUuid, String xmlNode, int userId,
			int groupId) throws Exception {
		String metadatawad = "";

		//TODO putNode getNodeRight
		if (!cred.hasNodeRight(c, userId, groupId, nodeUuid, Credential.WRITE)) {
			throw new RestWebApplicationException(Status.FORBIDDEN, " No WRITE credential ");
		}

		// D'abord on supprime les noeuds existants
		//deleteNode(nodeUuid, userId);
		xmlNode = DomUtils.cleanXMLData(xmlNode);
		final Document doc = DomUtils.xmlString2Document(xmlNode, new StringBuilder());
		// Puis on le recree
		Node node;
		node = doc.getDocumentElement();

		if (node.getNodeName().equals("metadata-wad")) {
			metadatawad = DomUtils.getNodeAttributesString(node);// " attr1=\"wad1\" attr2=\"wad2\" ";
		}

		if (1 == updatetMySqlNodeMetadatawad(c, nodeUuid, metadatawad)) {
			touchPortfolio(c, nodeUuid, null);
			return "editer";
		}

		return "erreur";
	}

	@Override
	public Object putNodeNodeContext(Connection c, MimeType mimeType, String nodeUuid, String xmlNode, int userId,
			int groupId) throws Exception {
		if (!cred.hasNodeRight(c, userId, groupId, nodeUuid, Credential.WRITE)) {
			throw new RestWebApplicationException(Status.FORBIDDEN, " No WRITE credential ");
		}

		xmlNode = DomUtils.cleanXMLData(xmlNode);
		final Document doc = DomUtils.xmlString2Document(xmlNode, new StringBuilder());
		// Puis on le recree
		Node node;
		node = doc.getDocumentElement();

		if (node.getNodeName().equals("asmResource")) {
			// Si le noeud est de type asmResource, on stocke le innerXML du noeud
			updateMysqlResourceByXsiType(c, nodeUuid, "context", DomUtils.getInnerXml(node), userId);
			return "editer";
		}
		return "erreur";
	}

	@Override
	public Object putNodeNodeResource(Connection c, MimeType mimeType, String nodeUuid, String xmlNode, int userId,
			int groupId) throws Exception {
		if (!cred.hasNodeRight(c, userId, groupId, nodeUuid, Credential.WRITE)) {
			throw new RestWebApplicationException(Status.FORBIDDEN, " No WRITE credential ");
			//return DATABASE_FALSE;
		}

		xmlNode = DomUtils.cleanXMLData(xmlNode);
		final Document doc = DomUtils.xmlString2Document(xmlNode, new StringBuilder());
		// Puis on le recree
		Node node;
		node = doc.getDocumentElement();

		if (node.getNodeName().equals("asmResource")) {
			// Si le noeud est de type asmResource, on stocke le innerXML du noeud
			updateMysqlResourceByXsiType(c, nodeUuid, "nodeRes", DomUtils.getInnerXml(node), userId);
			return "editer";
		}
		return "erreur";
	}

	@Override
	public Object putPortfolio(Connection c, MimeType inMimeType, MimeType outMimeType, String in, String portfolioUuid,
			int userId, Boolean portfolioActive, int groupId, String portfolioModelId) throws Exception {
		final StringBuilder outTrace = new StringBuilder();

		//		if(!credential.isAdmin(userId))
		//			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");

		final ResultSet resPortfolio = getMysqlPortfolioResultset(c, portfolioUuid);
		if (resPortfolio != null) {
			resPortfolio.next();
		}

		// Si on est en PUT le portfolio existe donc on regarde si modele ou pas
		try {
			portfolioModelId = resPortfolio.getString("model_id");
		} catch (final Exception ex) {
			logger.error("Exception", ex);
		}

		if (userId <= 0) {
			if (resPortfolio != null) {
				userId = resPortfolio.getInt("user_id");
			}
		}

		if (in.length() > 0) {
			final Document doc = DomUtils.xmlString2Document(in, outTrace);

			Node rootNode = (doc.getElementsByTagName("portfolio")).item(0);
			if (rootNode == null) {
				throw new Exception("Root Node (portfolio) not found !");
			}
			rootNode = (doc.getElementsByTagName("asmRoot")).item(0);

			String uuid = UUID.randomUUID().toString();
			final Node idAtt = rootNode.getAttributes().getNamedItem("id");
			if (idAtt != null) {
				final String tempId = idAtt.getNodeValue();
				if (tempId.length() > 0) {
					uuid = tempId;
				}
			}
			insertMysqlPortfolio(c, portfolioUuid, uuid, 0, userId);

			writeNode(c, rootNode, portfolioUuid, portfolioModelId, userId, 0, null, null, 0, 0, true, null, false);
		}

		updateMysqlPortfolioActive(c, portfolioUuid, portfolioActive);

		return true;
	}

	@Override
	public Object putPortfolioConfiguration(Connection c, String portfolioUuid, Boolean portfolioActive,
			Integer userId) {
		if (!cred.isAdmin(c, userId)) {
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");
		}

		return updateMysqlPortfolioConfiguration(c, portfolioUuid, portfolioActive);
	}

	@Override
	public int putPortfolioInGroup(Connection c, String uuid, Integer portfolioGroupId, String label, int userId) {
		String sql;
		PreparedStatement st = null;
		ResultSet res = null;

		try {
			if (label != null) {
				sql = "UPDATE portfolio_group SET label=? WHERE pg=?";
				st = c.prepareStatement(sql);
				st.setString(1, label);
				st.setInt(2, portfolioGroupId);
				st.execute();
			} else {
				/// Check if exist with correct type
				sql = "SELECT pg FROM portfolio_group WHERE pg=? AND type='PORTFOLIO'";
				st = c.prepareStatement(sql);
				st.setInt(1, portfolioGroupId);
				res = st.executeQuery();
				if (!res.next()) {
					return -1;
				}

				res.close();
				st.close();

				sql = "SELECT portfolio_id FROM portfolio WHERE portfolio_id=uuid2bin(?)";
				st = c.prepareStatement(sql);
				st.setString(1, uuid);
				res = st.executeQuery();
				if (!res.next()) {
					return -1;
				}

				res.close();
				st.close();

				sql = "INSERT INTO portfolio_group_members(pg, portfolio_id) VALUES(?, uuid2bin(?))";
				st = c.prepareStatement(sql);
				st.setInt(1, portfolioGroupId);
				st.setString(2, uuid);
				st.executeUpdate();
			}
		} catch (final SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				if (res != null) {
					res.close();
				}
				if (st != null) {
					st.close();
				}
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}

		return 0;
	}

	@Override
	public Object putResource(Connection c, MimeType inMimeType, String nodeParentUuid, String in, int userId,
			int groupId) throws Exception {
		// TODO userId ???

		in = DomUtils.filterXmlResource(in);

		int retVal = -1;
		final String[] data = getMysqlResourceByNodeParentUuid(c, nodeParentUuid);
		String nodeUuid;
		if (data != null) // Asking to change a non existng node
		{
			nodeUuid = data[0];

			final Document doc = DomUtils.xmlString2Document(in, new StringBuilder());
			// Puis on le recree
			Node node;

			node = (doc.getElementsByTagName("asmResource")).item(0);

			if (!cred.hasNodeRight(c, userId, groupId, nodeParentUuid, Credential.WRITE)) {
				throw new RestWebApplicationException(Status.FORBIDDEN, " No WRITE credential ");
			}

			touchPortfolio(c, nodeParentUuid, null);

			retVal = updateMysqlResource(c, nodeUuid, null, DomUtils.getInnerXml(node), userId);
		}

		return retVal;
	}

	@Override
	public Object putRole(Connection c, String xmlRole, int userId, int roleId) throws Exception {
		if (!cred.isAdmin(c, userId)) {
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");
		}

		String result;
		final String username = null;
		final String password = null;
		final String firstname = null;
		final String lastname = null;
		final String email = null;
		int id = 0;

		//On prepare les requetes SQL
		PreparedStatement stInsert;
		String sqlInsert;

		//On recupere le body
		Document doc;

		doc = DomUtils.xmlString2Document(xmlRole, new StringBuilder());
		final Element role = doc.getDocumentElement();

		final NodeList children = role.getChildNodes();
		// On parcourt une premiere fois les enfants pour recuperer la liste e ecrire en base

		//On verifie le bon format
		if (role.getNodeName().equals("role")) {
			for (int i = 0; i < children.getLength(); i++) {
				if (children.item(i).getNodeName().equals("label")) {
					DomUtils.getInnerXml(children.item(i));
				}
			}
		} else {
			result = "Erreur lors de la recuperation des attributs de l'utilisateur dans le XML";
		}

		//On ajoute l'utilisateur dans la base de donnees
		try {
			sqlInsert = "REPLACE INTO credential(login, display_firstname, display_lastname,email, password) VALUES (?, ?, ?, ?, UNHEX(SHA1(?)))";
			stInsert = c.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS);
			if (dbserveur.equals("oracle")) {
				sqlInsert = "INSERT INTO credential(login, display_firstname, display_lastname,email, password) VALUES (?, ?, ?, ?, crypt(?))";
				stInsert = c.prepareStatement(sqlInsert, new String[] { "userid" });
			}

			stInsert.setString(1, username);
			stInsert.setString(2, firstname);
			stInsert.setString(3, lastname);
			stInsert.setString(4, email);
			stInsert.setString(5, password);
			stInsert.executeUpdate();

			final ResultSet rs = stInsert.getGeneratedKeys();
			if (rs.next()) {
				id = rs.getInt(1);
			}
		} catch (final SQLException e) {
			e.printStackTrace();
		}

		result = "" + id;

		return result;
	}

	@Override
	public String putRRGUpdate(Connection c, int userId, Integer rrgId, String data) {
		if (!cred.isAdmin(c, userId) && !cred.isOwnerRRG(c, userId, rrgId)) {
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");
		}

		String sql;
		PreparedStatement st;

		/// Parse data
		DocumentBuilder documentBuilder;
		Document document = null;
		try {
			final DocumentBuilderFactory documentBuilderFactory = DomUtils.newSecureDocumentBuilderFactory();
			documentBuilder = documentBuilderFactory.newDocumentBuilder();
			final InputSource is = new InputSource(new StringReader(data));
			document = documentBuilder.parse(is);
		} catch (final Exception e) {
			e.printStackTrace();
		}

		/// Probleme de parsage
		if (document == null) {
			return "erreur";
		}

		final NodeList labelNodes = document.getElementsByTagName("label");
		final Node labelNode = labelNodes.item(0);
		String sqlLabel = "";
		final ArrayList<String> text = new ArrayList<>();
		if (labelNode != null) {
			final Node labelText = labelNode.getFirstChild();
			if (labelText != null) {
				text.add(labelText.getNodeValue());
				sqlLabel = "SET label=? ";
			}
		}

		final NodeList portfolioNodes = document.getElementsByTagName("portfolio");
		final Element portfolioNode = (Element) portfolioNodes.item(0);
		String sqlPid = "";
		if (portfolioNode != null) {
			text.add(portfolioNode.getAttribute("id"));
			sqlPid = "SET portfolio_id=? ";
		}

		// Il faut au moins 1 parametre e changer
		if (text.isEmpty()) {
			return "";
		}

		try {
			sql = "UPDATE group_right_info " + sqlLabel + sqlPid + "WHERE grid=?";
			st = c.prepareStatement(sql);

			for (int i = 0; i < text.size(); ++i) {
				st.setString(i + 1, text.get(i));
			}

			st.setInt(text.size() + 1, rrgId);

			st.executeUpdate();
		} catch (final Exception e) {
			e.printStackTrace();
		}

		return "";
	}

	@Override
	public Object putUser(Connection c, int userId, String oAuthToken, String oAuthSecret) {
		return insertMysqlUser(c, userId, oAuthToken, oAuthSecret);
	}

	@Override
	public Integer putUserGroup(Connection c, String usergroup, String userPut) {
		PreparedStatement st;
		String sql;
		int retval = 0;

		try {
			final int gid = Integer.parseInt(usergroup);
			final int uid = Integer.parseInt(userPut);

			sql = "INSERT IGNORE INTO group_user(gid, userid) VALUES(?,?)";
			st = c.prepareStatement(sql);
			st.setInt(1, gid);
			st.setInt(2, uid);
			retval = st.executeUpdate();
		} catch (final Exception ex) {
			ex.printStackTrace();
		}

		return retval;
	}

	@Override
	public Boolean putUserGroupLabel(Connection c, Integer user, int siteGroupId, String label) {
		String sql;
		PreparedStatement st = null;
		final boolean isOK = true;

		try {
			sql = "UPDATE credential_group SET label=? WHERE cg=?";
			st = c.prepareStatement(sql);
			st.setString(1, label);
			st.setInt(2, siteGroupId);
			st.execute();

		} catch (final SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				if (st != null) {
					st.close();
				}
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}

		return isOK;
	}

	@Override
	public Boolean putUserInUserGroup(Connection c, int user, int siteGroupId, int currentUid) {
		String sql;
		PreparedStatement st = null;
		final boolean isOK = true;

		try {
			sql = "INSERT INTO credential_group_members(cg, userid) VALUES(?, ?)";
			st = c.prepareStatement(sql);
			st.setInt(1, siteGroupId);
			st.setInt(2, user);
			st.execute();

		} catch (final SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				if (st != null) {
					st.close();
				}
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}

		return isOK;
	}

	/// List children uuid nodes inside a temporary table, don't forget to clean up afterwards
	private boolean queryChildren(Connection c, String nodeUuid) {
		try {
			String sql;
			PreparedStatement st;

			/// Find all children nodes where we will remove editing rights for current group rights
			/// Pour retrouver les enfants du noeud et affecter les droits
			if (dbserveur.equals("mysql")) {
				sql = "CREATE TEMPORARY TABLE t_struc_nodeid(" +
						"uuid binary(16) UNIQUE NOT NULL, " +
						"t_level INT) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")) {
				final String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_struc_nodeid(" +
						"uuid VARCHAR2(32) NOT NULL, " +
						"t_level NUMBER(10,0)" +
						",  CONSTRAINT t_struc_nodeid_UK_uuid UNIQUE (uuid)) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_struc_nodeid','" + v_sql + "')}";
				final CallableStatement ocs = c.prepareCall(sql);
				ocs.execute();
				ocs.close();
			}

			// En double car on ne peut pas faire d'update/select d'une meme table temporaire
			if (dbserveur.equals("mysql")) {
				sql = "CREATE TEMPORARY TABLE t_struc_nodeid_2(" +
						"uuid binary(16) UNIQUE NOT NULL, " +
						"t_level INT) ENGINE=MEMORY DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci";
				st = c.prepareStatement(sql);
				st.execute();
				st.close();
			} else if (dbserveur.equals("oracle")) {
				final String v_sql = "CREATE GLOBAL TEMPORARY TABLE t_struc_nodeid_2(" +
						"uuid VARCHAR2(32) NOT NULL, " +
						"t_level NUMBER(10,0)" +
						",  CONSTRAINT t_struc_nodeid_2_UK_uuid UNIQUE (uuid)) ON COMMIT PRESERVE ROWS";
				sql = "{call create_or_empty_table('t_struc_nodeid_2','" + v_sql + "')}";
				final CallableStatement ocs = c.prepareCall(sql);
				ocs.execute();
				ocs.close();
			}

			/// Dans la table temporaire on retrouve les noeuds concernes
			/// (assure une convergence de la recursion et limite le nombre de lignes dans la recherche)
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
			/// On boucle, recursion par niveau
			int level = 0;
			int added = 1;
			if (dbserveur.equals("mysql")) {
				sql = "INSERT IGNORE INTO t_struc_nodeid_2(uuid, t_level) ";
			} else if (dbserveur.equals("oracle")) {
				sql = "INSERT /*+ ignore_row_on_dupkey_index(t_struc_nodeid_2,t_struc_nodeid_2_UK_uuid)*/ INTO t_struc_nodeid_2(uuid, t_level) ";
			}
			sql += "SELECT n.node_uuid, ? " +
					"FROM node n WHERE n.node_parent_uuid IN (SELECT uuid FROM t_struc_nodeid t " +
					"WHERE t.t_level=?)";

			String sqlTemp = null;
			if (dbserveur.equals("mysql")) {
				sqlTemp = "INSERT IGNORE INTO t_struc_nodeid SELECT * FROM t_struc_nodeid_2;";
			} else if (dbserveur.equals("oracle")) {
				sqlTemp = "INSERT /*+ ignore_row_on_dupkey_index(t_struc_nodeid,t_struc_nodeid_UK_uuid)*/ INTO t_struc_nodeid SELECT * FROM t_struc_nodeid_2";
			}
			final PreparedStatement stTemp = c.prepareStatement(sqlTemp);

			st = c.prepareStatement(sql);
			while (added != 0) {
				st.setInt(1, level + 1);
				st.setInt(2, level);
				st.executeUpdate();
				added = stTemp.executeUpdate(); // On s'arrete quand rien e ete ajoute
				level = level + 1; // Prochaine etape
			}
			st.close();
			stTemp.close();
			//*/

		} catch (final Exception e) {
			logger.error(e.getMessage());
		}

		return true;
	}

	private void reconstructTree(StringBuilder data, t_tree node, HashMap<String, t_tree> entries) {
		if (node == null || node.childString == null) {
			return;
		}

		final String[] childsId = node.childString.split(",");
		data.append(node.data);
		//		String data = node.data;

		for (final String cid : childsId) {
			if ("".equals(cid)) {
				continue;
			}

			final t_tree c = entries.remove(cid); // Help converge a bit faster
			if (c != null) {
				reconstructTree(data, c, entries);
			} /*else {
				  // Node missing from query, can be related to security
				  // safe to ignore
				}*/
		}

		data.append("</").append(node.type).append(">");
	}

	@Override
	public boolean registerUser(Connection c, String username, String password) {
		boolean changed = false;
		String sql;
		PreparedStatement st = null;
		ResultSet res = null;
		try {
			// Check if user exists
			sql = "SELECT login FROM credential WHERE login=?";
			st = c.prepareStatement(sql);
			st.setString(1, username);
			res = st.executeQuery();

			if (!res.next()) {
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
		} catch (final Exception ex) {
			ex.printStackTrace();
		} finally {
			try {
				if (res != null) {
					res.close();
				}
				if (st != null) {
					st.close();
				}
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}

		return changed;
	}

	public String resetRights(Connection c) {
		try {
			/// temp class
			class right {
				int rd = 0;
				int wr = 0;
				int dl = 0;
				int sb = 0;
				int ad = 0;
				String types = "";
				String rules = "";
				String notify = "";
			}

			class groupright {
				HashMap<String, right> rights = new HashMap<>();

				right getGroup(String label) {
					right r = rights.get(label.trim());
					if (r == null) {
						r = new right();
						rights.put(label, r);
					}
					return r;
				}

				void setNotify(String roles) {
					for (final right r : rights.values()) {
						r.notify = roles.trim();
					}
				}
			}

			class resolver {
				HashMap<String, groupright> resolve = new HashMap<>();

				HashMap<String, Integer> groups = new HashMap<>();

				groupright getUuid(String uuid) {
					groupright gr = resolve.get(uuid);
					if (gr == null) {
						gr = new groupright();
						resolve.put(uuid, gr);
					}
					return gr;
				}
			}

			final resolver resolve = new resolver();

			/// t_struc_nodeid is already populated with the uuid we have to reset
			String sql = "SELECT bin2uuid(n.node_uuid) AS uuid, bin2uuid(n.portfolio_id) AS puuid, n.metadata, n.metadata_wad, n.metadata_epm " +
					"FROM t_struc_nodeid t, node n WHERE t.uuid=n.node_uuid";
			PreparedStatement st = c.prepareStatement(sql);
			ResultSet res = st.executeQuery();

			DocumentBuilder documentBuilder;
			final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
			documentBuilder = documentBuilderFactory.newDocumentBuilder();
			while (res.next()) // TODO Maybe pre-process into temp table
			{
				final String uuid = res.getString("uuid");
				final String meta = res.getString("metadata_wad");
				final String nodeString = "<?xml version='1.0' encoding='UTF-8' standalone='no'?><transfer " +
						meta +
						"/>";

				final groupright role = resolve.getUuid(uuid);

				try {
					/// parse meta
					final InputSource is = new InputSource(new StringReader(nodeString));
					final Document doc = documentBuilder.parse(is);

					/// Process attributes
					final Element attribNode = doc.getDocumentElement();
					final NamedNodeMap attribMap = attribNode.getAttributes();

					String nodeRole;
					Node att = attribMap.getNamedItem("access");

					att = attribMap.getNamedItem("seenoderoles");
					if (att != null) {
						final StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements()) {
							nodeRole = tokens.nextElement().toString();

							final right r = role.getGroup(nodeRole);
							r.rd = 1;

							resolve.groups.put(nodeRole, 0);
						}
					}
					att = attribMap.getNamedItem("showtoroles");
					if (att != null) {
						final StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements()) {
							nodeRole = tokens.nextElement().toString();

							final right r = role.getGroup(nodeRole);
							r.rd = 0;

							resolve.groups.put(nodeRole, 0);
						}
					}
					att = attribMap.getNamedItem("delnoderoles");
					if (att != null) {
						final StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements()) {

							nodeRole = tokens.nextElement().toString();
							final right r = role.getGroup(nodeRole);
							r.dl = 1;

							resolve.groups.put(nodeRole, 0);
						}
					}
					att = attribMap.getNamedItem("editnoderoles");
					if (att != null) {
						final StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements()) {
							nodeRole = tokens.nextElement().toString();
							final right r = role.getGroup(nodeRole);
							r.wr = 1;

							resolve.groups.put(nodeRole, 0);
						}
					}
					att = attribMap.getNamedItem("submitroles");
					if (att != null) {
						final StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements()) {
							nodeRole = tokens.nextElement().toString();
							final right r = role.getGroup(nodeRole);
							r.sb = 1;

							resolve.groups.put(nodeRole, 0);
						}
					}
					att = attribMap.getNamedItem("seeresroles");
					if (att != null) {
						final StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements()) {
							nodeRole = tokens.nextElement().toString();
							final right r = role.getGroup(nodeRole);
							r.rd = 1;

							resolve.groups.put(nodeRole, 0);
						}
					}
					att = attribMap.getNamedItem("delresroles");
					if (att != null) {
						final StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements()) {
							nodeRole = tokens.nextElement().toString();
							final right r = role.getGroup(nodeRole);
							r.dl = 1;

							resolve.groups.put(nodeRole, 0);
						}
					}
					att = attribMap.getNamedItem("editresroles");
					if (att != null) {
						final StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements()) {
							nodeRole = tokens.nextElement().toString();
							final right r = role.getGroup(nodeRole);
							r.wr = 1;

							resolve.groups.put(nodeRole, 0);
						}
					}
					att = attribMap.getNamedItem("submitresroles");
					if (att != null) {
						final StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements()) {
							nodeRole = tokens.nextElement().toString();
							final right r = role.getGroup(nodeRole);
							r.sb = 1;

							resolve.groups.put(nodeRole, 0);
						}
					}
					final Node actionroles = attribMap.getNamedItem("actionroles");
					if (actionroles != null) {
						/// Format pour l'instant: actionroles="sender:1,2;responsable:4"
						final StringTokenizer tokens = new StringTokenizer(actionroles.getNodeValue(), ";");
						while (tokens.hasMoreElements()) {
							nodeRole = tokens.nextElement().toString();
							final StringTokenizer data = new StringTokenizer(nodeRole, ":");
							final String nrole = data.nextElement().toString();
							final String actions = data.nextElement().toString().trim();
							final right r = role.getGroup(nrole);
							r.rules = actions;

							resolve.groups.put(nrole, 0);
						}
					}
					final Node menuroles = attribMap.getNamedItem("menuroles");
					if (menuroles != null) {
						/// Pour les differents items du menu
						final StringTokenizer menuline = new StringTokenizer(menuroles.getNodeValue(), ";");

						while (menuline.hasMoreTokens()) {
							final String line = menuline.nextToken();

							/// New format is an xml
							final Matcher roleMatcher = ROLE_PATTERN.matcher(line);
							String menurolename = null;
							if (roleMatcher.find()) {
								menurolename = roleMatcher.group(1);
							}

							/// Keeping old format for compatibility
							if (menurolename == null) {
								/// Format pour l'instant: code_portfolio,tag_semantique,label@en/libelle@fr,reles[;autre menu]
								final String[] tokens = line.split(",");
								if (tokens.length == 4) {
									menurolename = tokens[3];
								}
							}

							if (menurolename != null) {
								// Break down list of roles
								final String[] roles = menurolename.split(" ");
								for (final String s : roles) {
									resolve.groups.put(s.trim(), 0);
								}
							}
						}
					}
					final Node notifyroles = attribMap.getNamedItem("notifyroles");
					if (notifyroles != null) {
						/// Format pour l'instant: notifyroles="sender responsable"
						final StringTokenizer tokens = new StringTokenizer(notifyroles.getNodeValue(), " ");
						StringBuilder merge = new StringBuilder();
						if (tokens.hasMoreElements()) {
							merge = new StringBuilder(tokens.nextElement().toString().trim());
						}
						while (tokens.hasMoreElements()) {
							merge.append(",").append(tokens.nextElement().toString().trim());
						}
						role.setNotify(merge.toString());
					}

					/// Now remove mention to being submitted
					attribNode.removeAttribute("submitted");
					attribNode.removeAttribute("submitteddate");
					final String resetMeta = DomUtils.getNodeAttributesString(attribNode);
					sql = "UPDATE node SET metadata_wad=? WHERE node_uuid=uuid2bin(?)";
					final PreparedStatement stu = c.prepareStatement(sql);
					stu.setString(1, resetMeta);
					stu.setString(2, uuid);
					stu.executeUpdate();
					stu.close();
				} catch (final Exception e) {
					e.printStackTrace();
				}
			}
			res.close();
			st.close();

			c.setAutoCommit(false);

			/// On insere les donnees pre-compile
			//			Iterator<String> entries = resolve.groups.keySet().iterator();

			/// Ajout des droits des noeuds FIXME
			// portfolio, group name, id -> rights
			final String updateRight = "UPDATE group_rights gr SET gr.RD=?, gr.WR=?, gr.DL=?, gr.SB=?, gr.AD=?, gr.types_id=?, gr.rules_id=?, gr.notify_roles=? " +
					"WHERE gr.grid=? AND gr.id=uuid2bin(?)";
			st = c.prepareStatement(updateRight);

			for (final Entry<String, groupright> entry : resolve.resolve.entrySet()) {
				final String uuid = entry.getKey();
				final groupright gr = entry.getValue();

				for (final Entry<String, right> rightelem : gr.rights.entrySet()) {
					final String group = rightelem.getKey();

					final String sqlgrid = "SELECT gr.grid " +
							"FROM group_rights gr, group_right_info gri " +
							"WHERE gri.grid=gr.grid AND gri.label=? AND gr.id=uuid2bin(?)";
					final PreparedStatement st2 = c.prepareStatement(sqlgrid);
					st2.setString(1, group);
					st2.setString(2, uuid);
					res = st2.executeQuery();
					int grid = -1;
					if (res.next()) {
						grid = res.getInt("grid");
					}
					st2.close();

					//					int grid = resolve.groups.get(group);
					final right rightval = rightelem.getValue();
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
		} catch (final Exception e) {
			try {
				if (!c.getAutoCommit()) {
					c.rollback();
				}
			} catch (final SQLException e1) {
				e1.printStackTrace();
			}
			e.printStackTrace();
		} finally {
			try {
				c.setAutoCommit(true);
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}

		return null;
	}

	@Override
	public boolean setPublicState(Connection c, int userId, String portfolio, boolean isPublic) {
		boolean ret = false;
		if (!cred.isAdmin(c, userId) && !cred.isOwner(c, userId, portfolio) && !cred.isDesigner(c, userId, portfolio)
				&& !cred.isCreator(c, userId)) {
			throw new RestWebApplicationException(Status.FORBIDDEN, "No admin right");
		}

		String sql;
		PreparedStatement st = null;
		try {
			// S'assure qu'il y ait au moins un groupe de base
			sql = "SELECT gi.gid " +
					"FROM group_right_info gri LEFT JOIN group_info gi ON gri.grid=gi.grid " +
					"WHERE gri.portfolio_id=uuid2bin(?) AND gri.label='all'";
			st = c.prepareStatement(sql);
			st.setString(1, portfolio);
			final ResultSet rs = st.executeQuery();

			int gid = 0;
			if (rs.next()) {
				gid = rs.getInt("gid");
			}
			rs.close();
			st.close();

			if (gid == 0) //  If not exist, create 'all' groups
			{
				c.setAutoCommit(false);
				sql = "INSERT INTO group_right_info(owner, label, portfolio_id) " + "VALUES(?,'all',uuid2bin(?))";
				st = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
				if (dbserveur.equals("oracle")) {
					st = c.prepareStatement(sql, new String[] { "grid" });
				}
				st.setInt(1, userId);
				st.setString(2, portfolio);

				int grid = 0;
				st.executeUpdate();
				ResultSet key = st.getGeneratedKeys();
				if (key.next()) {
					grid = key.getInt(1);
				}
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
				if (dbserveur.equals("oracle")) {
					st = c.prepareStatement(sql, new String[] { "gid" });
				}
				st.setInt(1, grid);
				st.setInt(2, userId);
				st.executeUpdate();

				key = st.getGeneratedKeys();
				if (key.next()) {
					gid = key.getInt(1);
				}
				key.close();
				st.close();
				c.commit();
			}

			if (isPublic) // Insere ou retire 'sys_public' dans le groupe 'all' du portfolio
			{
				sql = "INSERT IGNORE INTO group_user(gid, userid) " +
						"VALUES( ?, (SELECT userid FROM credential WHERE login='sys_public'))";
				if (dbserveur.equals("oracle")) {
					sql = "INSERT /*+ ignore_row_on_dupkey_index(group_user,group_user_PK)*/ INTO group_user(gid, userid) " +
							"VALUES(?,(SELECT userid FROM credential WHERE login='sys_public'))";
				}
			} else {
				sql = "DELETE FROM group_user " +
						"WHERE userid=(SELECT userid FROM credential WHERE login='sys_public') " +
						"AND gid=?";
			}

			st = c.prepareStatement(sql);
			st.setInt(1, gid);
			st.executeUpdate();

			ret = true;
		} catch (final SQLException e) {
			try {
				c.rollback();
				c.setAutoCommit(true);
			} catch (final SQLException e1) {
				e1.printStackTrace();
			}
			e.printStackTrace();
		} finally {
			try {
				c.setAutoCommit(true);
				if (st != null) {
					st.close();
				}
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}

		return ret;
	}

	@Override
	public boolean touchPortfolio(Connection c, String fromNodeuuid, String fromPortuuid) {
		boolean hasChanged = false;

		String sql;
		PreparedStatement st = null;
		final ResultSet res = null;

		try {
			if (fromNodeuuid != null) {
				sql = "UPDATE portfolio SET modif_date=NOW() ";
				if (dbserveur.equals("oracle")) {
					sql = "UPDATE portfolio SET modif_date=CURRENT_TIMESTAMP ";
				}
				sql += "WHERE portfolio_id=(SELECT portfolio_id FROM node WHERE node_uuid=uuid2bin(?))";
				st = c.prepareStatement(sql);
				st.setString(1, fromNodeuuid);
				st.executeUpdate();

				hasChanged = true;
			} else if (fromPortuuid != null) {
				sql = "UPDATE portfolio SET modif_date=NOW() ";
				if (dbserveur.equals("oracle")) {
					sql = "UPDATE portfolio SET modif_date=CURRENT_TIMESTAMP ";
				}
				sql += "WHERE portfolio_id=uuid2bin(?)";
				st = c.prepareStatement(sql);
				st.setString(1, fromPortuuid);
				st.executeUpdate();

				hasChanged = true;
			}
		} catch (final Exception ex) {
			ex.printStackTrace();
		} finally {
			try {
				if (res != null) {
					res.close();
				}
				if (st != null) {
					st.close();
				}
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}

		return hasChanged;
	}

	private int updateMysqlCredentialToken(Connection c, Integer userId, String token) {
		String sql;
		PreparedStatement st;

		try {
			sql = "UPDATE  credential SET token = ? WHERE userid  = ? ";

			st = c.prepareStatement(sql);
			st.setString(1, token);
			st.setInt(2, userId);

			return st.executeUpdate();
		} catch (final Exception ex) {
			ex.printStackTrace();
			return -1;
		}
	}

	/*
	 *  Ecrit le noeud dans la base MySQL
	 */
	private int updateMysqlNodeChildren(Connection c, String nodeUuid) {
		PreparedStatement st = null;
		String sql;
		int status = -1;

		try {
			/// Re-numerote les noeud (on commence � 0)
			sql = "UPDATE node SET node_order=@ii:=@ii+1 " + // La 1ere valeur va etre 0
					"WHERE node_parent_uuid=uuid2bin(?) AND (@ii:=-1) " + // Pour tromper la requete parce qu'on veut commencer � 0
					"ORDER by node_order";
			if (dbserveur.equals("oracle")) {
				sql = "UPDATE node n1 SET n1.node_order=(" +
						"SELECT (n2.rnum-1) FROM (" +
						"SELECT node_uuid, row_number() OVER (ORDER BY node_order ASC) rnum, node_parent_uuid " +
						"FROM node WHERE node_parent_uuid=uuid2bin(?)) n2 " +
						"WHERE n1.node_uuid= n2.node_uuid) " +
						"WHERE n1.node_parent_uuid=uuid2bin(?)";
			}
			st = c.prepareStatement(sql);
			st.setString(1, nodeUuid);
			if (dbserveur.equals("oracle")) {
				st.setString(2, nodeUuid);
			}
			st.executeUpdate();
			st.close();

			/// Met � jour les enfants
			if (dbserveur.equals("mysql")) {
				sql = "UPDATE node n1, " +
						"(SELECT GROUP_CONCAT(bin2uuid(COALESCE(n2.shared_node_uuid,n2.node_uuid)) ORDER BY n2.node_order) AS value " +
						"FROM node n2 " +
						"WHERE n2.node_parent_uuid=uuid2bin(?) " +
						"GROUP BY n2.node_parent_uuid) tmp " +
						"SET n1.node_children_uuid=tmp.value " +
						"WHERE n1.node_uuid=uuid2bin(?)";
			} else if (dbserveur.equals("oracle")) {
				sql = "UPDATE node SET node_children_uuid=(SELECT LISTAGG(bin2uuid(COALESCE(n2.shared_node_uuid,n2.node_uuid)), ',') WITHIN GROUP (ORDER BY n2.node_order) AS value FROM node n2 WHERE n2.node_parent_uuid=uuid2bin(?) GROUP BY n2.node_parent_uuid) WHERE node_uuid=uuid2bin(?)";
			}
			st = c.prepareStatement(sql);
			st.setString(1, nodeUuid);
			st.setString(2, nodeUuid);
			st.executeUpdate();
			int changes = st.getUpdateCount();
			st.close();

			if (changes == 0) // Specific case when there's no children left in parent
			{
				sql = "UPDATE node n " + "SET n.node_children_uuid=NULL " + "WHERE n.node_uuid=uuid2bin(?)";
				st = c.prepareStatement(sql);
				st.setString(1, nodeUuid);
				st.executeUpdate();
				changes = st.getUpdateCount();
				st.close();
			}

			status = changes;
		} catch (final Exception e) {
			e.printStackTrace();
		} finally {
			if (st != null) {
				try {
					st.close();
				} catch (final SQLException e) {
					e.printStackTrace();
				}
			}
		}
		return status;
	}

	private int updateMysqlPortfolioActive(Connection c, String portfolioUuid, Boolean active) {
		String sql;
		PreparedStatement st;

		try {
			sql = "UPDATE  portfolio SET active = ?  WHERE portfolio_id = uuid2bin(?) ";
			st = c.prepareStatement(sql);
			st.setInt(1, (active) ? 1 : 0);
			st.setString(2, portfolioUuid);

			return st.executeUpdate();
		} catch (final Exception ex) {
			logger.error("Managed error", ex);
			return -1;
		}
	}

	private Object updateMysqlPortfolioConfiguration(Connection c, String portfolioUuid, Boolean portfolioActive) {
		String sql;
		PreparedStatement st;

		try {
			sql = "UPDATE  portfolio SET active = ? WHERE portfolio_id  = uuid2bin(?) ";

			st = c.prepareStatement(sql);
			st.setInt(1, portfolioActive ? 1 : 0);
			st.setString(2, portfolioUuid);

			return st.executeUpdate();
		} catch (final Exception ex) {
			ex.printStackTrace();
			return null;
		}
	}

	private int updateMysqlPortfolioModelId(Connection c, String portfolioUuid, String portfolioModelId) {
		String sql;
		PreparedStatement st;

		try {
			sql = "UPDATE  portfolio SET model_id = uuid2bin(?)  WHERE portfolio_id = uuid2bin(?) ";
			st = c.prepareStatement(sql);

			st.setString(1, portfolioModelId);
			st.setString(2, portfolioUuid);

			return st.executeUpdate();
		} catch (final Exception ex) {
			logger.error("Managed error", ex);
			return -1;
		}
	}

	private int updateMysqlResource(Connection c, String uuid, String xsiType, String content, int userId) {
		String sql = "";
		PreparedStatement st = null;

		try {
			if (xsiType != null) {
				if (dbserveur.equals("mysql")) {
					sql = "REPLACE INTO resource_table(node_uuid,xsi_type,content,user_id,modif_user_id,modif_date) ";
					sql += "VALUES(uuid2bin(?),?,?,?,?,?)";
				} else if (dbserveur.equals("oracle")) {
					sql = "MERGE INTO resource_table d USING (SELECT uuid2bin(?) node_uuid,? xsi_type,? content,? user_id,? modif_user_id,? modif_date FROM DUAL) s ON (d.node_uuid = s.node_uuid) WHEN MATCHED THEN UPDATE SET d.xsi_type = s.xsi_type, d.content = s.content, d.user_id = s.user_id, d.modif_user_id = s.modif_user_id, d.modif_date = s.modif_date WHEN NOT MATCHED THEN INSERT (d.node_uuid, d.xsi_type, d.content, d.user_id, d.modif_user_id, d.modif_date) VALUES (s.node_uuid, s.xsi_type, s.content, s.user_id, s.modif_user_id, s.modif_date)";
				}
				st = c.prepareStatement(sql);
				st.setString(1, uuid);
				st.setString(2, xsiType);
				st.setString(3, content);
				st.setInt(4, userId);
				st.setInt(5, userId);
				if (dbserveur.equals("mysql")) {
					st.setString(6, SqlUtils.getCurrentTimeStamp());
				} else if (dbserveur.equals("oracle")) {
					st.setTimestamp(6, SqlUtils.getCurrentTimeStamp2());
				}

			} else {
				sql = "UPDATE  resource_table SET content = ?,user_id = ?,modif_user_id = ?,modif_date = ? WHERE node_uuid = uuid2bin(?) ";
				st = c.prepareStatement(sql);
				st.setString(1, content);
				st.setInt(2, userId);
				st.setInt(3, userId);
				if (dbserveur.equals("mysql")) {
					st.setString(4, SqlUtils.getCurrentTimeStamp());
				} else if (dbserveur.equals("oracle")) {
					st.setTimestamp(4, SqlUtils.getCurrentTimeStamp2());
				}
				st.setString(5, uuid);

			}
			return st.executeUpdate();
		} catch (final Exception ex) {
			logger.error(ex.getMessage());
			ex.printStackTrace();
			return -1;
		} finally {
			if (st != null) {
				try {
					st.close();
				} catch (final SQLException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private int updateMysqlResourceByXsiType(Connection c, String nodeUuid, String xsiType, String content,
			int userId) {
		String sql;
		PreparedStatement st;

		try {
			if (xsiType.equals("nodeRes")) {
				sql = " UPDATE resource_table SET content=?,user_id=?,modif_user_id=?,modif_date=? ";
				sql += " WHERE node_uuid = (SELECT res_res_node_uuid FROM node ";
				sql += " WHERE node_uuid=uuid2bin(?))  ";

				/// Interpretation du code (vive le hack... Non)
				final Document doc = DomUtils.xmlString2Document(
						"<?xml version='1.0' encoding='UTF-8' standalone='no'?><res>" + content + "</res>",
						new StringBuilder());
				final NodeList nodes = doc.getElementsByTagName("code");
				final Node code = nodes.item(0);
				if (code != null) {
					final Node codeContent = code.getFirstChild();

					String codeVal;
					if (codeContent != null) {
						codeVal = codeContent.getNodeValue();
						// Check if code already exists
						if (isCodeExist(c, codeVal, nodeUuid)) {
							throw new RestWebApplicationException(Status.CONFLICT, "Existing code.");
						}
						final String sq = "UPDATE node SET code=? WHERE node_uuid=uuid2bin(?)";
						st = c.prepareStatement(sq);
						st.setString(1, codeVal);
						st.setString(2, nodeUuid);
						st.executeUpdate();
						st.close();
					}
				}
			} else if (xsiType.equals("context")) {
				sql = " UPDATE resource_table SET content=?,user_id=?,modif_user_id=?,modif_date=? ";
				sql += " WHERE node_uuid = (SELECT res_context_node_uuid FROM node ";
				sql += " WHERE node_uuid=uuid2bin(?))  ";
			} else {
				sql = " UPDATE resource_table SET content=?,user_id=?,modif_user_id=?,modif_date=? ";
				sql += " WHERE node_uuid = (SELECT res_node_uuid FROM node ";
				sql += " WHERE node_uuid=uuid2bin(?))  ";
			}
			st = c.prepareStatement(sql);
			st.setString(1, content);

			st.setInt(2, userId);
			st.setInt(3, userId);
			if (dbserveur.equals("mysql")) {
				st.setString(4, SqlUtils.getCurrentTimeStamp());
			} else if (dbserveur.equals("oracle")) {
				st.setTimestamp(4, SqlUtils.getCurrentTimeStamp2());
			}
			st.setString(5, nodeUuid);

			return st.executeUpdate();
		} catch (final RestWebApplicationException e) {
			throw e;
		} catch (final Exception ex) {
			ex.printStackTrace();
			return -1;
		}
	}

	private int updatetMySqlNode(Connection c, String nodeUuid, String asmType, String xsiType, String semantictag,
			String label, String code, String descr, String format, String metadata, String metadataWad,
			String metadataEpm, int sharedRes, int sharedNode, int sharedNodeRes, int modifUserId) throws Exception {
		String sql;
		PreparedStatement st;

		sql = "UPDATE node SET ";
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
		if (dbserveur.equals("mysql")) {
			st.setString(15, SqlUtils.getCurrentTimeStamp());
		} else if (dbserveur.equals("oracle")) {
			st.setTimestamp(15, SqlUtils.getCurrentTimeStamp2());
		}
		st.setString(16, nodeUuid);
		final int val = st.executeUpdate();
		st.close();

		return val;
	}

	private int updatetMySqlNodeMetadatawad(Connection c, String nodeUuid, String metadatawad) throws Exception {
		String sql;
		PreparedStatement st;

		sql = "UPDATE node SET ";
		sql += "metadata_wad=?, modif_date=?";
		sql += " WHERE node_uuid = uuid2bin(?) ";
		st = c.prepareStatement(sql);

		st.setString(1, metadatawad);
		if (dbserveur.equals("mysql")) {
			st.setString(2, SqlUtils.getCurrentTimeStamp());
		} else if (dbserveur.equals("oracle")) {
			st.setTimestamp(2, SqlUtils.getCurrentTimeStamp2());
		}
		st.setString(3, nodeUuid);

		return st.executeUpdate();
	}

	private int updatetMySqlNodeOrder(Connection c, String nodeUuid, int order) throws Exception {
		String sql;
		PreparedStatement st;

		sql = "UPDATE node SET ";
		sql += " node_order = ? ";
		sql += " WHERE node_uuid = uuid2bin(?) ";
		st = c.prepareStatement(sql);

		st.setInt(1, order);
		st.setString(2, nodeUuid);

		return st.executeUpdate();
	}

	@Override
	public String UserChangeInfo(Connection c, int userId, int userid2, String in) throws SQLException {
		if (userId != userid2) {
			throw new RestWebApplicationException(Status.FORBIDDEN, "Not authorized");
		}

		String result1;
		String originalp = null;
		String password = null;
		String email = null;
		String firstname = null;
		String lastname = null;
		//On prepare les requetes SQL
		PreparedStatement st;
		String sql;

		// Parse input
		Document doc;
		Element infUser = null;
		try {
			doc = DomUtils.xmlString2Document(in, new StringBuilder());
			infUser = doc.getDocumentElement();
		} catch (final Exception e) {
			e.printStackTrace();
		}

		infUser.getChildNodes();

		if (infUser.getNodeName().equals("user")) {
			//On recupere les attributs

			if (infUser.getAttributes().getNamedItem("id") != null) {
				Integer.parseInt(infUser.getAttributes().getNamedItem("id").getNodeValue());
			} else {
			}
			NodeList children2;
			children2 = infUser.getChildNodes();
			/// Get parameters
			for (int y = 0; y < children2.getLength(); y++) {
				if (children2.item(y).getNodeName().equals("prevpass")) {
					originalp = DomUtils.getInnerXml(children2.item(y));
				} else if (children2.item(y).getNodeName().equals("password")) {
					password = DomUtils.getInnerXml(children2.item(y));
				} else if (children2.item(y).getNodeName().equals("email")) {
					email = DomUtils.getInnerXml(children2.item(y));
				} else if (children2.item(y).getNodeName().equals("firstname")) {
					firstname = DomUtils.getInnerXml(children2.item(y));
				} else if (children2.item(y).getNodeName().equals("lastname")) {
					lastname = DomUtils.getInnerXml(children2.item(y));
				}
			}

			/// Checking if previous password match
			boolean isOK = false;
			if (originalp != null) {
				sql = "SELECT userid FROM credential WHERE userid=? AND password=UNHEX(SHA1(?))";
				st = c.prepareStatement(sql);
				st.setInt(1, userId);
				st.setString(2, originalp);
				final ResultSet res = st.executeQuery();
				if (res.next()) {
					isOK = true;
				}
			}
			/// Executing changes if valid
			if (isOK) {
				if (password != null) {
					sql = "UPDATE credential SET password = UNHEX(SHA1(?)) WHERE  userid = ?";
					if (dbserveur.equals("oracle")) {
						sql = "UPDATE credential SET password = crypt(?) WHERE  userid = ?";
					}

					st = c.prepareStatement(sql);
					st.setString(1, password);
					st.setInt(2, userId);
					st.executeUpdate();
					securityLog.info("User '{}' Changed password", userId);
				}
				if (email != null) {
					sql = "UPDATE credential SET email = ? WHERE  userid = ?";

					st = c.prepareStatement(sql);
					st.setString(1, email);
					st.setInt(2, userId);
					st.executeUpdate();
				}
				if (firstname != null) {
					sql = "UPDATE credential SET display_firstname = ? WHERE  userid = ?";

					st = c.prepareStatement(sql);
					st.setString(1, firstname);
					st.setInt(2, userid2);
					st.executeUpdate();
				}
				if (lastname != null) {
					sql = "UPDATE credential SET display_lastname = ? WHERE  userid = ?";

					st = c.prepareStatement(sql);
					st.setString(1, lastname);
					st.setInt(2, userid2);
					st.executeUpdate();
				}
			}

		}

		result1 = "" + userid2;

		return result1;
	}

	@Override
	public void writeLog(Connection c, String url, String method, String headers, String inBody, String outBody,
			int code) {
		insertMySqlLog(c, url, method, headers, inBody, outBody, code);
	}

	/*
	 * forcedParentUuid permet de forcer l'uuid parent, independamment de l'attribut du noeud fourni
	 */
	private String writeNode(Connection c, Node node, String portfolioUuid, String portfolioModelId, int userId,
			int ordrer, String forcedUuid, String forcedUuidParent, int sharedResParent, int sharedNodeResParent,
			boolean rewriteId, HashMap<String, String> resolve, boolean parseRights) throws Exception {
		String uuid;
		final String originUuid = null;
		String parentUuid = null;
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

		String nodeRole;

		if (node == null) {
			return null;
		}

		String currentid = "";
		final Node idAtt = node.getAttributes().getNamedItem("id");
		if (idAtt != null) {
			final String tempId = idAtt.getNodeValue();
			if (tempId.length() > 0) {
				currentid = tempId;
			}
		}

		// Si uuid force, alors on ne tient pas compte de l'uuid indique dans le xml
		if (rewriteId) // On garde les uuid par defaut
		{
			uuid = currentid;
		} else if (forcedUuid != null && !"".equals(forcedUuid)) {
			uuid = forcedUuid;
		} else {
			uuid = UUID.randomUUID().toString();
		}

		if (resolve != null) { // Mapping old id -> new id
			resolve.put(currentid, uuid);
		}

		if (forcedUuidParent != null) {
			// Dans le cas d'un uuid parent force => POST => on genere un UUID
			parentUuid = forcedUuidParent;
		}

		/// Recuperation d'autre infos
		try {
			if (node.getNodeName() != null) {
				asmType = node.getNodeName();
			}

			if (node.getAttributes().getNamedItem("xsi_type") != null) {
				xsiType = node.getAttributes().getNamedItem("xsi_type").getNodeValue().trim();
			}

			if (node.getAttributes().getNamedItem("semtag") != null) {
				semtag = node.getAttributes().getNamedItem("semtag").getNodeValue().trim();
			}

			if (node.getAttributes().getNamedItem("format") != null) {
				format = node.getAttributes().getNamedItem("format").getNodeValue().trim();
			}
		} catch (final Exception ex) {
			logger.error("Managed error", ex);
		}

		// Si id defini, alors on ecrit en base
		//TODO Transactionnel noeud+enfant
		NodeList children = null;
		try {
			children = node.getChildNodes();
			// On parcourt une premiere fois les enfants pour recuperer la liste e ecrire en base
			for (int i = 0; i < children.getLength(); i++) {
				final Node child = children.item(i);

				if ("#text".equals(child.getNodeName())) {
					continue;
				}

				switch (child.getNodeName()) {
				case "metadata-wad":
					metadataWad = DomUtils.getNodeAttributesString(children.item(i));

					if (parseRights) {
						// Gestion de la securite integree
						//

						child.getAttributes().getNamedItem("access");

						try {
							Node att = child.getAttributes().getNamedItem("seenoderoles");
							if (att != null) {
								final StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
								while (tokens.hasMoreElements()) {

									nodeRole = tokens.nextElement().toString();
									cred.postGroupRight(c, nodeRole, uuid, Credential.READ, portfolioUuid, userId);
								}
							}

							att = child.getAttributes().getNamedItem("delnoderoles");
							if (att != null) {
								final StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
								while (tokens.hasMoreElements()) {

									nodeRole = tokens.nextElement().toString();
									cred.postGroupRight(c, nodeRole, uuid, Credential.DELETE, portfolioUuid, userId);
								}
							}

							att = child.getAttributes().getNamedItem("editnoderoles");
							if (att != null) {
								final StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
								while (tokens.hasMoreElements()) {

									nodeRole = tokens.nextElement().toString();
									cred.postGroupRight(c, nodeRole, uuid, Credential.WRITE, portfolioUuid, userId);
								}
							}

							att = child.getAttributes().getNamedItem("submitnoderoles");
							if (att != null) { // TODO submitnoderoles deprecated fro submitroles
								final StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
								while (tokens.hasMoreElements()) {
									nodeRole = tokens.nextElement().toString();
									cred.postGroupRight(c, nodeRole, uuid, Credential.SUBMIT, portfolioUuid, userId);
								}
							}

							att = child.getAttributes().getNamedItem("seeresroles");
							if (att != null) {
								final StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
								while (tokens.hasMoreElements()) {
									nodeRole = tokens.nextElement().toString();
									cred.postGroupRight(c, nodeRole, uuid, Credential.READ, portfolioUuid, userId);
								}
							}

							att = child.getAttributes().getNamedItem("delresroles");
							if (att != null) {
								final StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
								while (tokens.hasMoreElements()) {
									nodeRole = tokens.nextElement().toString();
									cred.postGroupRight(c, nodeRole, uuid, Credential.DELETE, portfolioUuid, userId);
								}
							}

							att = child.getAttributes().getNamedItem("editresroles");
							if (att != null) {
								final StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
								while (tokens.hasMoreElements()) {
									nodeRole = tokens.nextElement().toString();
									cred.postGroupRight(c, nodeRole, uuid, Credential.WRITE, portfolioUuid, userId);
								}
							}

							att = child.getAttributes().getNamedItem("submitresroles");
							if (att != null) // TODO submitresroles deprecated fro submitroles
							{
								final StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
								while (tokens.hasMoreElements()) {
									nodeRole = tokens.nextElement().toString();
									cred.postGroupRight(c, nodeRole, uuid, Credential.SUBMIT, portfolioUuid, userId);
								}
							}

							att = child.getAttributes().getNamedItem("submitroles");
							if (att != null) {
								final StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
								while (tokens.hasMoreElements()) {
									nodeRole = tokens.nextElement().toString();
									cred.postGroupRight(c, nodeRole, uuid, Credential.SUBMIT, portfolioUuid, userId);
								}
							}

							att = child.getAttributes().getNamedItem("showtoroles");
							if (att != null) {
								final StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
								while (tokens.hasMoreElements()) {
									nodeRole = tokens.nextElement().toString();
									cred.postGroupRight(c, nodeRole, uuid, Credential.NONE, portfolioUuid, userId);
								}
							}

							final Node actionroles = child.getAttributes().getNamedItem("actionroles");
							if (actionroles != null) {
								/// Format pour l'instant: actionroles="sender:1,2;responsable:4"
								final StringTokenizer tokens = new StringTokenizer(actionroles.getNodeValue(), ";");
								while (tokens.hasMoreElements()) {
									nodeRole = tokens.nextElement().toString();
									final StringTokenizer data = new StringTokenizer(nodeRole, ":");
									final String role = data.nextElement().toString();
									final String actions = data.nextElement().toString();
									cred.postGroupRight(c, role, uuid, actions, portfolioUuid, userId);
								}
							}

							/// TODO: e l'integration avec sakai/LTI
							final Node notifyroles = child.getAttributes().getNamedItem("notifyroles");
							if (notifyroles != null) {
								/// Format pour l'instant: actionroles="sender:1,2;responsable:4"
								final StringTokenizer tokens = new StringTokenizer(notifyroles.getNodeValue(), " ");
								StringBuilder merge = new StringBuilder();
								if (tokens.hasMoreElements()) {
									merge = new StringBuilder(tokens.nextElement().toString());
								}
								while (tokens.hasMoreElements()) {
									merge.append(",").append(tokens.nextElement().toString());
								}

								postNotifyRoles(c, userId, portfolioUuid, uuid, merge.toString());
							}
						} catch (final Exception ex) {
							logger.error("Managed error", ex);
						}

					}

					break;
				case "metadata-epm":
					metadataEpm = DomUtils.getNodeAttributesString(children.item(i));
					break;
				case "metadata":
					String tmpSharedRes = "";
					String tmpSharedNode = "";
					String tmpSharedNodeRes = "";
					try {
						if (child.getAttributes().getNamedItem("public") != null) {
							final String publicatt = child.getAttributes().getNamedItem("public").getNodeValue();
							// TODO don't know if other value is available but could be simplified by
							// setPublicState(c, userId, portfolioUuid, DB_YES.equals(publicatt));
							if (DB_YES.equals(publicatt)) {
								setPublicState(c, userId, portfolioUuid, true);
							} else if (DB_NO.equals(publicatt)) {
								setPublicState(c, userId, portfolioUuid, false);
							}
						}

						if (child.getAttributes().getNamedItem("sharedResource") != null) {
							tmpSharedRes = child.getAttributes().getNamedItem("sharedResource").getNodeValue();
						}

						if (child.getAttributes().getNamedItem("sharedNode") != null) {
							tmpSharedNode = child.getAttributes().getNamedItem("sharedNode").getNodeValue();
						}

						if (child.getAttributes().getNamedItem("sharedNodeResource") != null) {
							tmpSharedNodeRes = child.getAttributes().getNamedItem("sharedNodeResource").getNodeValue();
						}

						if (child.getAttributes().getNamedItem("semantictag") != null) {
							semanticTag = child.getAttributes().getNamedItem("semantictag").getNodeValue();
						}
					} catch (final Exception ex) {
						logger.error("Managed error", ex);
					}

					if (tmpSharedRes.equalsIgnoreCase(XML_YES)) {
						sharedRes = 1;
					}
					if (tmpSharedNode.equalsIgnoreCase(XML_YES)) {
						sharedNode = 1;
					}
					if (tmpSharedNodeRes.equalsIgnoreCase(XML_YES)) {
						sharedNodeRes = 1;
					}

					metadata = DomUtils.getNodeAttributesString(children.item(i));
					break;
				// On verifie si l'enfant n'est pas un element de type code, label ou descr
				case "label":
					label = DomUtils.getInnerXml(child);
					break;
				case "code":
					code = DomUtils.getInnerXml(child);
					break;
				case "description":
					descr = DomUtils.getInnerXml(child);
					break;
				default:
					child.getAttributes();
					break;
				}
			}
		} catch (final Exception ex) {
			// Pas d'enfants
			logger.error("Managed error", ex);
		}

		// Si on est au debut de l'arbre, on stocke la definition du portfolio
		// dans la table portfolio
		if (uuid != null && node.getParentNode() != null) {
			// On retrouve le code cache dans les ressources. blegh
			final NodeList childs = node.getChildNodes();
			for (int k = 0; k < childs.getLength(); ++k) {
				final Node child = childs.item(k);
				if ("asmResource".equals(child.getNodeName())) {
					final NodeList grandchilds = child.getChildNodes();
					for (int l = 0; l < grandchilds.getLength(); ++l) {
						final Node gc = grandchilds.item(l);
						if ("code".equals(gc.getNodeName())) {
							code = DomUtils.getInnerXml(gc);
							break;
						}
					}
				}
				if (code != null) {
					break;
				}
			}

			if (!node.getNodeName().equals("asmRoot") && portfolioUuid == null) {
				throw new Exception("Il manque la balise asmRoot !!");
			}
		}

		// Si on instancie un portfolio e partir d'un modele
		// Alors on gere les share*
		if (portfolioModelId != null) {
			if (sharedNode == 1) {
				sharedNodeUuid = originUuid;
			}
		} else {
		}

		if (uuid != null && !node.getNodeName().equals("portfolio") && !node.getNodeName().equals("asmResource")) {
			insertMySqlNode(c, uuid, parentUuid, "", asmType, xsiType, sharedRes, sharedNode, sharedNodeRes,
					sharedResUuid, sharedNodeUuid, sharedNodeResUuid, metadata, metadataWad, metadataEpm, semtag,
					semanticTag, label, code, descr, format, ordrer, userId, portfolioUuid);
		}

		// Si le parent a ete force, cela veut dire qu'il faut mettre e jour les enfants du parent
		//TODO
		// MODIF : On le met e jour tout le temps car dans le cas d'un POST les uuid ne sont pas connus e l'avance
		//if(forcedUuidParent!=null)

		// Si le noeud est de type asmResource, on stocke le innerXML du noeud
		if (node.getNodeName().equals("asmResource")) {
			if (portfolioModelId != null) {
				if (xsiType.equals("nodeRes") && sharedNodeResParent == 1) {
					sharedNodeResUuid = originUuid;
					insertMysqlResource(c, sharedNodeResUuid, parentUuid, xsiType, DomUtils.getInnerXml(node),
							portfolioModelId, sharedNodeResParent, sharedResParent, userId);
				} else if (!xsiType.equals("context") && !xsiType.equals("nodeRes") && sharedResParent == 1) {

					sharedResUuid = originUuid;
					insertMysqlResource(c, sharedResUuid, parentUuid, xsiType, DomUtils.getInnerXml(node),
							portfolioModelId, sharedNodeResParent, sharedResParent, userId);
				} else {
					insertMysqlResource(c, uuid, parentUuid, xsiType, DomUtils.getInnerXml(node), portfolioModelId,
							sharedNodeResParent, sharedResParent, userId);
				}
			} else {
				insertMysqlResource(c, uuid, parentUuid, xsiType, DomUtils.getInnerXml(node), portfolioModelId,
						sharedNodeResParent, sharedResParent, userId);
			}

		}

		// On reparcourt ensuite les enfants pour continuer la recursivite
		//		if(children!=null && sharedNode!=1)
		if (children != null) {
			int k = 0;
			for (int i = 0; i < children.getLength(); i++) {
				final Node child = children.item(i);
				String childId = null;
				if (!rewriteId) {
					childId = UUID.randomUUID().toString();
				}

				if (child.getAttributes() != null) {
					final String nodeName = child.getNodeName();
					if ("asmRoot".equals(nodeName) || "asmStructure".equals(nodeName) || "asmUnit".equals(nodeName)
							|| "asmUnitStructure".equals(nodeName) || "asmUnitContent".equals(nodeName)
							|| "asmContext".equals(nodeName)) {
						logger.trace("uid={}, enfant_uuid={}, ordre={}", uuid, child.getAttributes().getNamedItem("id"),
								k);
						writeNode(c, child, portfolioUuid, portfolioModelId, userId, k, childId, uuid, sharedRes,
								sharedNodeRes, rewriteId, resolve, parseRights);
						k++;
					} else if ("asmResource".equals(nodeName)) // Les asmResource pose probleme dans l'ordre des noeuds
					{
						writeNode(c, child, portfolioUuid, portfolioModelId, userId, k, childId, uuid, sharedRes,
								sharedNodeRes, rewriteId, resolve, parseRights);
					}
				}
			}
		}

		updateMysqlNodeChildren(c, forcedUuidParent);

		return uuid;
	}

}