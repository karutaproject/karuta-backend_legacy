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
import java.io.InputStream;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.http.Cookie;
import javax.sql.DataSource;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/*

-- Create DB and tables
CREATE DATABASE epm_server;

-- Grant
GRANT ALL PRIVILEGES ON epm_server.* to root@'%' IDENTIFIED BY 'root';

USE epm_server;

-- Server Script (source: http://bugs.mysql.com/bug.php?id=1214)
delimiter //
CREATE FUNCTION uuid2bin(uuid CHAR(36)) RETURNS BINARY(16) DETERMINISTIC
BEGIN
  RETURN UNHEX(REPLACE(uuid, '-',''));
END//

CREATE FUNCTION bin2uuid(bin BINARY(16)) RETURNS CHAR(36) DETERMINISTIC
BEGIN
  DECLARE hex CHAR(32);

  SET hex = HEX(bin);

  RETURN LOWER(CONCAT(LEFT(hex, 8),'-',
                      SUBSTR(hex, 9,4),'-',
                      SUBSTR(hex,13,4),'-',
                      SUBSTR(hex,17,4),'-',
                      RIGHT(hex, 12)
                          ));
END//

delimiter ;


-- Credentials
CREATE TABLE IF NOT EXISTS credential (
userid BIGINT NOT NULL AUTO_INCREMENT,
login varchar(255) NOT NULL,
display_name varchar(255) NOT NULL,
password BINARY(20) NOT NULL,
token VARCHAR(255),
c_date BIGINT,
PRIMARY KEY(userid, login));

-- password = UNHEX(SHA1('password'))

-- Dynamic types
CREATE TABLE IF NOT EXISTS types_table (
type_id BIGINT NOT NULL AUTO_INCREMENT,
owner BIGINT NOT NULL,
label VARCHAR(255) NOT NULL DEFAULT 'Nouveau Type',
def BLOB,
PRIMARY KEY(type_id));

-- What we allow adding under a specific type
CREATE TABLE IF NOT EXISTS type_add (
type_from BIGINT NOT NULL,
type_allowed BIGINT NOT NULL,
who ENUM('Own','Other') NOT NULL,
PRIMARY KEY(type_from,type_allowed,who));

-- Group Rights Info
-- change_rights when we give this group the possibility to change rights in other group at the same level
CREATE TABLE IF NOT EXISTS group_right_info (
grid BIGINT NOT NULL AUTO_INCREMENT,
owner BIGINT NOT NULL,
label VARCHAR(255) NOT NULL DEFAULT 'Nouveau groupe',
change_rights BOOL NOT NULL DEFAULT 0,
PRIMARY KEY(grid));

-- Droit et groupe
CREATE TABLE IF NOT EXISTS group_rights (
grid BIGINT NOT NULL,
id BINARY(16) NOT NULL,
RD TINYINT(1) NOT NULL DEFAULT True,
WR TINYINT(1) NOT NULL DEFAULT False,
DL TINYINT(1) NOT NULL DEFAULT False,
SB TINYINT(1) NOT NULL DEFAULT False,
AD TINYINT(1) NOT NULL DEFAULT False,
types_id TEXT DEFAULT NULL,
PRIMARY KEY(grid, id));

-- Group Info
CREATE TABLE IF NOT EXISTS group_info (
gid BIGINT NOT NULL AUTO_INCREMENT,
grid BIGINT,
owner BIGINT NOT NULL,
label VARCHAR(255) NOT NULL DEFAULT 'Nouveau groupe',
PRIMARY KEY(gid));

-- Group with users
CREATE TABLE IF NOT EXISTS group_user (
gid BIGINT NOT NULL,
userid BIGINT NOT NULL,
KEY(gid));

-- Group with groups
CREATE TABLE IF NOT EXISTS group_group (
gid BIGINT NOT NULL,
child_gid BIGINT NOT NULL,
PRIMARY KEY(gid, child_gid));

-- Data
-- DEFAULT uuid2bin(UUID()) for id?, owner: userid from credential
CREATE TABLE IF NOT EXISTS data_table (
id BINARY(16) NOT NULL,
owner BIGINT NOT NULL,
creator BIGINT NOT NULL,
type VARCHAR(255) NOT NULL,
mimetype VARCHAR(255),
filename VARCHAR(255),
c_date BIGINT,
data BLOB,
PRIMARY KEY(id));

-- Base type insert
INSERT INTO types_table(owner,label,def) VALUES(0,'Portfolio','<?xml version="1.0" encoding="UTF-8" standalone="no"?><asmRoot/>');
INSERT INTO types_table(owner,label,def) VALUES(0,'Texte','<?xml version="1.0" encoding="UTF-8" standalone="no"?><root xsi_type="Text"/>');
INSERT INTO types_table(owner,label,def) VALUES(0,'Document','<?xml version="1.0" encoding="UTF-8" standalone="no"?><root xsi_type="Document"/>');
INSERT INTO types_table(owner,label,def) VALUES(0,'Formulaire','<?xml version="1.0" encoding="UTF-8" standalone="no"?><root xsi_type="FormResource"/>');
INSERT INTO types_table(owner,label,def) VALUES(0,'Competence','<?xml version="1.0" encoding="UTF-8" standalone="no"?><root xsi_type="CompetencyResource"/>');
INSERT INTO types_table(owner,label,def) VALUES(0,'Section','<?xml version="1.0" encoding="UTF-8" standalone="no"?><root xsi_type="Section"/>');
INSERT INTO types_table(owner,label,def) VALUES(0,'Comment','<?xml version="1.0" encoding="UTF-8" standalone="no"?><root xsi_type="Comment"/>');
INSERT INTO types_table(owner,label,def) VALUES(0,'Auto-eval','<?xml version="1.0" encoding="UTF-8" standalone="no"?><root xsi_type="autoeval"/>');
--

*/

public class DBConnect {

    private static final Logger logger = LoggerFactory.getLogger(DBConnect.class);
    private final String dbserveur = "mysql";
//	private final String dbserveur = "oracle";

    Connection con = null;
    int uid;
    ResultSet rs = null;
    PreparedStatement stmt = null;

    DBConnect(String loggedUser) {
        try {
            InitialContext cxt = new InitialContext();
            DataSource dataSource = (DataSource) cxt.lookup("java:/comp/env/jdbc/portfolio-backend");
            con = dataSource.getConnection();

            if (loggedUser == null) {
                uid = 0;
                return;
            }

            /// Find out base user id, it's used in practically every requests
            String query = "SELECT userid FROM credential WHERE login=?";
            stmt = con.prepareStatement(query);
            stmt.setString(1, loggedUser);
            rs = stmt.executeQuery();

            if (!rs.next()) uid = 0;

            /// base info
            uid = rs.getInt(1);
        } catch (SQLException e) {
            logger.error("SQL error", e);

        } catch (NamingException e) {
            logger.error("Intercepted error", e);
        }
    }

    void cleanup() {
        try {
            if (con != null) {
                con.close();
                con = null;
            }
        } catch (SQLException e) {
            logger.error("SQL error", e);
        }
    }

    /**
     * Group Rights related
     */

    /// get user specified rights
    Document myGroupRightsList() {
        try {
            String query = "SELECT gri.grid, gri.owner, gri.label" +
                    " FROM group_right_info gri" +
                    " WHERE gri.owner =?";
            stmt = con.prepareStatement(query);
            stmt.setLong(1, uid);
            rs = stmt.executeQuery();

            // displaying records
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = null;
            try {
                docBuilder = builderFactory.newDocumentBuilder();
            } catch (ParserConfigurationException e1) {
                e1.printStackTrace();
            }

            //creating a new instance of a DOM to build a DOM tree.
            Document doc = docBuilder.newDocument();
            Element root = doc.createElement("group_rights");
            doc.appendChild(root);

      /*
      <group_rights>
        <group templateId='grid' owner='owner'>
          <label>bla<label/>
        </group>
        <group>
        ...
      </group_rights>
      //*/

            while (rs.next()) {
                long grid = rs.getLong(1);
                long owner = rs.getLong(2);
                String label = rs.getString(3);

                Element right = doc.createElement("group");
                right.setAttribute("templateId", Long.toString(grid));
                right.setAttribute("owner", Long.toString(owner));
                Element labelNode = doc.createElement("label");
                labelNode.setTextContent(label);

                right.appendChild(labelNode);
                root.appendChild(right);
            }

            return doc;
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                    rs = null;
                }
                if (stmt != null) {
                    stmt.close();
                    stmt = null;
                }
            } catch (SQLException e) {
            }
        }

        return null;
    }

    Document mySharedGroupRightsList(long gid) {
        try {
            String query = "SELECT gri.grid, gri.owner, gri.label" +
                    " FROM group_group as gg, group_info gi, group_right_info gri" +
                    " WHERE gi.grid=gri.grid AND gg.child_gid=gi.gid AND gg.gid IN" +
                    " (SELECT gid FROM group_group WHERE child_gid=?)";
            stmt = con.prepareStatement(query);
            stmt.setLong(1, gid);
            rs = stmt.executeQuery();

            // displaying records
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = null;
            try {
                docBuilder = builderFactory.newDocumentBuilder();
            } catch (ParserConfigurationException e1) {
                e1.printStackTrace();
            }

            //creating a new instance of a DOM to build a DOM tree.
            Document doc = docBuilder.newDocument();
            Element root = doc.createElement("group_rights");
            doc.appendChild(root);

      /*
      <group_rights>
        <group templateId='grid' owner='owner'>
          <label>bla<label/>
        </group>
        <group>
        ...
      </group_rights>
      //*/

            while (rs.next()) {
                long grid = rs.getLong(1);
                long owner = rs.getLong(2);
                String label = rs.getString(3);

                Element right = doc.createElement("group");
                right.setAttribute("templateId", Long.toString(grid));
                right.setAttribute("owner", Long.toString(owner));
                Element labelNode = doc.createElement("label");
                labelNode.setTextContent(label);

                right.appendChild(labelNode);
                root.appendChild(right);
            }

            return doc;
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                    rs = null;
                }
                if (stmt != null) {
                    stmt.close();
                    stmt = null;
                }
            } catch (SQLException e) {
            }
        }

        return null;
    }

    /// Get specified group right id from group right id
    Document GroupRightsListFromGrid(long grid) {
        ResultSet rs = null;
        PreparedStatement stmt = null;
        try {
            String query = "SELECT bin2uuid(d.id), d.type, gr.RD, gr.WR, gr.DL, gr.SB, gr.AD, gr.typesId, d.owner, d.creator, d.c_date" +
                    " FROM data_table d, group_rights gr" +
                    " WHERE d.id=gr.id AND gr.grid=?";
            stmt = con.prepareStatement(query);
            stmt.setLong(1, grid);
            rs = stmt.executeQuery();

            // displaying records
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = null;
            try {
                docBuilder = builderFactory.newDocumentBuilder();
            } catch (ParserConfigurationException e1) {
                e1.printStackTrace();
            }

            //creating a new instance of a DOM to build a DOM tree.
            Document doc = docBuilder.newDocument();
            Element root = doc.createElement("group_rights");
            root.setAttribute("templateId", Long.toString(grid));
            doc.appendChild(root);

            if (rs.next() == false) return doc;

            do {
                String id = rs.getString(1);
                String type = rs.getString(2);
                boolean read = rs.getBoolean(3);
                boolean write = rs.getBoolean(4);
                boolean del = rs.getBoolean(5);
                boolean submit = rs.getBoolean(6);
                boolean add = rs.getBoolean(7);
                String type_id = rs.getString(8);
                String owner = rs.getString(9);
                String creator = rs.getString(10);
                Long date = rs.getLong(11);

                Element child = doc.createElement("item");
                child.setAttribute("id", id);
                child.setAttribute("action", "Read");   // FIXME To be removed
                child.setAttribute("read", Boolean.toString(read));
                child.setAttribute("write", Boolean.toString(write));
                child.setAttribute("del", Boolean.toString(del));
                child.setAttribute("submit", Boolean.toString(submit));
                child.setAttribute("add", Boolean.toString(add));
                child.setAttribute("owner", owner);
                child.setAttribute("creator", creator);
                child.setAttribute("date", Long.toString(date));
                child.setAttribute("type", type);
                if (type_id != null)
                    child.setAttribute("type_id", type_id);
                root.appendChild(child);
            } while (rs.next());

            return doc;
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                    rs = null;
                }
                if (stmt != null) {
                    stmt.close();
                    stmt = null;
                }
            } catch (SQLException e) {
            }
        }

        return null;
    }

    /// Get specified group right id from group id
    Document GroupRightsListFromGid(long groupId, String uuid) {
        ResultSet rs = null;
        PreparedStatement stmt = null;
        try {
            String specific = "";
            if (uuid != null)
                specific = " AND gr.id=uuid2bin(?)";
            String query = "SELECT bin2uuid(d.id), d.type, gr.RD, gr.WR, gr.DL, gr.SB, gr.AD, gr.typesId, d.owner, d.creator, d.c_date, gi.grid" +
                    " FROM data_table d, group_rights gr, group_info gi" +
                    " WHERE gi.grid=gr.grid AND d.id=gr.id AND gi.gid=? " + specific + "";
            stmt = con.prepareStatement(query);
            stmt.setLong(1, groupId);
            if (uuid != null)
                stmt.setString(2, uuid);
            rs = stmt.executeQuery();

            // displaying records
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = null;
            try {
                docBuilder = builderFactory.newDocumentBuilder();
            } catch (ParserConfigurationException e1) {
                e1.printStackTrace();
            }

            //creating a new instance of a DOM to build a DOM tree.
            Document doc = docBuilder.newDocument();
            Element root = doc.createElement("group_rights");
            doc.appendChild(root);
            if (rs.next() == false) return doc;

            root.setAttribute("gid", Long.toString(groupId));
            root.setAttribute("templateId", Long.toString(rs.getLong(12)));

            do {
                String id = rs.getString(1);
                String type = rs.getString(2);
                boolean read = rs.getBoolean(3);
                boolean write = rs.getBoolean(4);
                boolean del = rs.getBoolean(5);
                boolean submit = rs.getBoolean(6);
                boolean add = rs.getBoolean(7);
                String type_id = rs.getString(8);
                String owner = rs.getString(9);
                String creator = rs.getString(10);
                Long date = rs.getLong(11);

                Element child = doc.createElement("item");

                child.setAttribute("id", id);
                child.setAttribute("read", Boolean.toString(read));
                child.setAttribute("write", Boolean.toString(write));
                child.setAttribute("del", Boolean.toString(del));
                child.setAttribute("submit", Boolean.toString(submit));
                child.setAttribute("add", Boolean.toString(add));
                if (type_id != null)
                    child.setAttribute("type_id", type_id);
                child.setAttribute("owner", owner);
                child.setAttribute("creator", creator);
                child.setAttribute("date", Long.toString(date));
                child.setAttribute("type", type);
                root.appendChild(child);
            } while (rs.next());

            return doc;
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                    rs = null;
                }
                if (stmt != null) {
                    stmt.close();
                    stmt = null;
                }
            } catch (SQLException e) {
            }
        }

        return null;
    }

    long addGroupRights(String label) {
        ResultSet rs = null;
        PreparedStatement stmt = null;

        try {
            /// Adding stuff
            String query = "INSERT INTO group_right_info(owner, label) VALUES(?, ?)";
            stmt = con.prepareStatement(query);
            stmt.setLong(1, uid);
            stmt.setString(2, label);
            stmt.executeUpdate();
            stmt.close();

            /// Add default rights
            if (dbserveur.equals("mysql")) {
                query = "SELECT LAST_INSERT_ID() FROM group_right_info";
            } else if (dbserveur.equals("oracle")) {
                query = "SELECT group_right_info_SEQ.CURRVAL FROM DUAL";
            }
            stmt = con.prepareStatement(query);
            rs = stmt.executeQuery();

            if (rs.next()) {
                long value = rs.getLong(1);
                return value;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                    rs = null;
                }
                if (stmt != null) {
                    stmt.close();
                    stmt = null;
                }
            } catch (SQLException e) {
            }
        }
        return 0;
    }

    boolean canchange_rights(long userGroupContext, long templateId) {
        ResultSet rs = null;
        PreparedStatement stmt = null;
        boolean hasRight = false;

        /// FIXME: Check if the target group we change is in the same hierarchy
        /// than the one user is

        try {
            /// Check if user has rights to change template from its use context.
            /// and that the group_right_info owner is the same than the group that
            /// give the right to modify rights
            if (userGroupContext != 0) {
                String queryCheck = "SELECT gri.change_rights FROM group_user AS gu, group_info AS gi, group_right_info AS gri" +
                        " WHERE gu.gid=gi.gid AND gi.grid=gri.grid" +
                        " AND gi.owner=gri.owner" + // template owner is the same than group owner give right
                        " AND gi.gid=? AND gu.userid=?";
                stmt = con.prepareStatement(queryCheck);
                stmt.setLong(1, userGroupContext);
                stmt.setLong(2, uid);
                rs = stmt.executeQuery();

                if (rs.next() == false) return false;
                if (rs.getLong(1) == 1)
                    hasRight = true;
                stmt.close();
            }

            /// Check if user own the group
            if (!hasRight) {
                String queryCheck = "SELECT grid FROM group_right_info" +
                        " WHERE grid=? AND owner=?";
                stmt = con.prepareStatement(queryCheck);
                stmt.setLong(1, templateId);
                stmt.setLong(2, uid);
                rs = stmt.executeQuery();

                if (rs.next() == false) return false;
                hasRight = true;
                stmt.close();
            }

            if (!hasRight) return hasRight;
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                    rs = null;
                }
                if (stmt != null) {
                    stmt.close();
                    stmt = null;
                }
            } catch (SQLException e) {
            }
        }
        return hasRight;
    }

    /// Add a uuid in a group rule
    boolean addResourceInTemplate(long templateId, String uuid) {
        ResultSet rs = null;
        PreparedStatement stmt = null;

        try {
            /// Adding line to template
            String query = "INSERT INTO group_rights(grid, id) VALUES(?, uuid2bin(?))";
            stmt = con.prepareStatement(query);
            stmt.setLong(1, templateId);
            stmt.setString(2, uuid);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                    rs = null;
                }
                if (stmt != null) {
                    stmt.close();
                    stmt = null;
                }
            } catch (SQLException e) {
            }
        }
        return true;
    }

    /// Update a definition in template
    boolean updateResourceInTemplate(long templateId, String uuid, String action, long type_id) {
        ResultSet rs = null;
        PreparedStatement stmt = null;

        /// FIXME: support pour la modification de type_id, on fait  manuellement pour l'instant

        try {
            // Actions we have to have
            String[] actions = action.split(",");
            HashSet<String> actionSet = new HashSet<String>(Arrays.asList(actions));

            boolean read = false;
            boolean write = false;
            boolean del = false;
            boolean submit = false;
            boolean add = false;

            if (actionSet.contains("Read"))
                read = true;
            if (actionSet.contains("Write"))
                write = true;
            if (actionSet.contains("Delete"))
                del = true;
            if (actionSet.contains("Submit"))
                submit = true;
            if (actionSet.contains("Add"))
                add = true;

            String query = "UPDATE group_rights SET RD=?,WR=?,DL=?,SB=?,AD=? WHERE grid=? AND id=uuid2bin(?)";
            stmt = con.prepareStatement(query);
            stmt.setBoolean(1, read);
            stmt.setBoolean(2, write);
            stmt.setBoolean(3, del);
            stmt.setBoolean(4, submit);
            stmt.setBoolean(5, add);
            stmt.setLong(6, templateId);
            stmt.setString(7, uuid);
            stmt.executeUpdate();
            stmt.close();

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                    rs = null;
                }
                if (stmt != null) {
                    stmt.close();
                    stmt = null;
                }
            } catch (SQLException e) {
            }
        }
        return true;
    }

    /// Remove a definition in template
    public boolean delResourceInGroup(long templateId, String uuid) {
        ResultSet rs = null;
        PreparedStatement stmt = null;
        boolean hasRight = false;

        try {
            /// Deleting specific item
            String query = "DELETE FROM group_rights WHERE grid=? AND id=uuid2bin(?)";
            stmt = con.prepareStatement(query);
            stmt.setLong(1, templateId);
            stmt.setString(2, uuid);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                    rs = null;
                }
                if (stmt != null) {
                    stmt.close();
                    stmt = null;
                }
            } catch (SQLException e) {
            }
        }
        return hasRight;
    }

    //// Remove template, if there's a binding, unlink all things
    boolean removeTemplate(long templateId) {
        ResultSet rs = null;
        PreparedStatement stmt = null;

        try {
            /// Check if template is owned by 'username' and has a binding to specified template
            String queryCheck = "SELECT grid FROM group_right_info WHERE grid=? AND owner=?";
            stmt = con.prepareStatement(queryCheck);
            stmt.setLong(1, templateId);
            stmt.setLong(2, uid);
            rs = stmt.executeQuery();
            stmt.close();

//      if( rs.next() == false )  // Not owned by username or doesn't exist
//        return false;

            // Owned by username, one or multiple groups

            ///// Should be a cascade trigger
            /// Remove template
            String queryDelete = "DELETE FROM group_rights WHERE grid=?";
            stmt = con.prepareStatement(queryDelete);
            stmt.setLong(1, templateId);
            stmt.executeUpdate();
            stmt.close();

            /// Remove template info
            String queryDeleteGroup = "DELETE FROM group_right_info WHERE grid=?";
            stmt = con.prepareStatement(queryDeleteGroup);
            stmt.setLong(1, templateId);
            stmt.executeUpdate();
            stmt.close();

            /// Clear template binding (Even if it's not ours, how is that possible?)
            String queryClearBinding = "UPDATE group_info SET grid=null WHERE grid=?";
            stmt = con.prepareStatement(queryClearBinding);
            stmt.setLong(1, templateId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                    rs = null;
                }
                if (stmt != null) {
                    stmt.close();
                    stmt = null;
                }
            } catch (SQLException e) {
            }
        }

        return true;
    }

    /// change rights submission
    public void applySubmission(long groupId, String uuid) {
        ResultSet rs = null;
        PreparedStatement stmt = null;

        try {
            /// Remove possibility of writing or deleting what was added (by user)
            String query = "UPDATE group_rights SET WR=0,DL=0,SB=0" +
                    " WHERE grid=(SELECT grid FROM group_info WHERE gid=?) AND id=uuid2bin(?)";

            stmt = con.prepareStatement(query);
            stmt.setLong(1, groupId);
            stmt.setString(2, uuid);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                    rs = null;
                }
                if (stmt != null) {
                    stmt.close();
                    stmt = null;
                }
            } catch (SQLException e) {
            }
        }
    }

    /***
     *  Rights related
     */
    /// Get user rights list with uuid that has been shared with or from user
    Document myGroupAccessList() {
        ResultSet rs = null;
        PreparedStatement stmt = null;
        try {
            String query = "SELECT g.owner, g.gid, gg.child_gid, g.grid, g.label" +
                    " FROM group_info g" +
//          " LEFT JOIN groupRightAssociation gra ON g.gid=gra.gid" +
                    " LEFT JOIN group_group gg ON g.gid=gg.gid" +
                    // "sub-groups" we belong to
                    " WHERE g.gid IN (SELECT gu.gid FROM group_user gu WHERE gu.userid=?)" +
                    // "groups" we belong to
                    " OR gg.child_gid IN (SELECT gu.gid FROM group_user gu WHERE gu.userid=?)";
            stmt = con.prepareStatement(query);
            stmt.setLong(1, uid);
            stmt.setLong(2, uid);
            rs = stmt.executeQuery();

            // displaying records
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = null;
            try {
                docBuilder = builderFactory.newDocumentBuilder();
            } catch (ParserConfigurationException e1) {
                e1.printStackTrace();
            }

            //creating a new instance of a DOM to build a DOM tree.
            Document doc = docBuilder.newDocument();
            Element root = doc.createElement("groups");
            doc.appendChild(root);

            while (rs.next()) {
                long owner = rs.getLong(1);
                long gid = rs.getLong(2);
                long child_gid = rs.getLong(3);
                long grid = rs.getLong(4);
                String label = rs.getString(5);

                Element right = doc.createElement("group");
                if (child_gid != 0)
                    right.setAttribute("child_gid", Long.toString(child_gid));
                right.setAttribute("id", Long.toString(gid));
                right.setAttribute("owner", Long.toString(owner));
                if (grid != 0)
                    right.setAttribute("templateId", Long.toString(grid));

                Element labelNode = doc.createElement("label");
                labelNode.setTextContent(label);

                right.appendChild(labelNode);
                root.appendChild(right);
            }

            return doc;
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                    rs = null;
                }
                if (stmt != null) {
                    stmt.close();
                    stmt = null;
                }
            } catch (SQLException e) {
            }
        }

        return null;
    }

    /**
     * Portfolio related
     */
    /// Get this person's portfolio
    Document myPortfolioList() {
        ResultSet rs = null;
        PreparedStatement stmt = null;
        try {
            String query = "SELECT bin2uuid(id) FROM data_table WHERE type='portfolio' AND " +
                    "owner=?";
            stmt = con.prepareStatement(query);
            stmt.setLong(1, uid);
            rs = stmt.executeQuery();

            // displaying records
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = null;
            try {
                docBuilder = builderFactory.newDocumentBuilder();
            } catch (ParserConfigurationException e1) {
                e1.printStackTrace();
            }

            //creating a new instance of a DOM to build a DOM tree.
            Document doc = docBuilder.newDocument();
            Element root = doc.createElement("portfolios");
            doc.appendChild(root);
            while (rs.next()) {
                String something = rs.getObject(1).toString();
                Element child = doc.createElement("portfolio");
                child.setAttribute("uuid", something);
                root.appendChild(child);
            }

            return doc;
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                    rs = null;
                }
                if (stmt != null) {
                    stmt.close();
                    stmt = null;
                }
            } catch (SQLException e) {
            }
        }

        return null;
    }

    Object[] getPortfolio(String uuid) {
        ResultSet rs = null;
        PreparedStatement stmt = null;
        try {
            String query = "SELECT mimetype, data FROM data_table WHERE id=uuid2bin(?) AND type='portfolio'";
            stmt = con.prepareStatement(query);
            stmt.setString(1, uuid);
            rs = stmt.executeQuery();
            // displaying records

            Blob blob = null;
            String mimetype = null;
            byte[] data = null;
            if (rs.next()) {
                mimetype = rs.getString(1);
                blob = rs.getBlob(2);
                data = blob.getBytes(1, (int) blob.length());
            }

            Object[] ret = {mimetype, data};

            return ret;
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                    rs = null;
                }
                if (stmt != null) {
                    stmt.close();
                    stmt = null;
                }
            } catch (SQLException e) {
            }
        }

        return null;
    }

    /**
     * Resource related
     */

    Object[] getResource(String uuid) {
        ResultSet rs = null;
        PreparedStatement stmt = null;
        try {
            String query = "SELECT mimetype, data FROM data_table WHERE id=uuid2bin(?)";
            stmt = con.prepareStatement(query);
            stmt.setString(1, uuid);
            rs = stmt.executeQuery();
            // displaying records

            Blob blob = null;
            String mimetype = null;
            byte[] data = null;
            if (rs.next()) {
                mimetype = rs.getString(1);
                blob = rs.getBlob(2);
                data = blob.getBytes(1, (int) blob.length());
            }

            Object[] ret = {mimetype, data};

            return ret;
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                    rs = null;
                }
                if (stmt != null) {
                    stmt.close();
                    stmt = null;
                }
            } catch (SQLException e) {
            }
        }

        return null;
    }

    String updateResource(InputStream input, String uuid) throws IOException {
        ResultSet rs = null;
        PreparedStatement stmt = null;
        try {
            /// Adding stuff
            String query = "UPDATE data_table SET data=?"
                    + " WHERE id=uuid2bin(?)";
            stmt = con.prepareStatement(query);
            stmt.setBlob(1, input);
            stmt.setString(2, uuid);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                    rs = null;
                }
                if (stmt != null) {
                    stmt.close();
                    stmt = null;
                }
            } catch (SQLException e) {
            }
        }

        return uuid;
    }

    /// If there's gid put the owner of the group as owner of the resource,
    /// as well as add some rule in the related groupRight
    /// also, add the right to read to all sibling nodes
    String addResource(InputStream file, long gid, long type_id, String mimetype, String filename, String fileType) throws IOException {
//    String mimetype = fileItem.getContentType();
//    String filename = FilenameUtils.getName(fileItem.getName());
//    InputStream file = fileItem.getInputStream();
        Date now = new Date();

        ResultSet rs = null;
        PreparedStatement stmt = null;
        String uuid = null;

        try {
            /// Get a UUID
            String getUUID = "SELECT UUID()";
            if (dbserveur.equals("oracle")) {
                getUUID = "SELECT bin2uuid(sys_guid()) FROM DUAL";
            }
            Statement statement = con.createStatement();
            rs = statement.executeQuery(getUUID);
            if (rs.next())
                uuid = rs.getString(1);

            /// Adding stuff
            String query = "";
            String group = "?";
            if (gid != 0)
                group = "(SELECT owner FROM group_info WHERE gid=?)";

            String type = "?";
            if (type_id != 0)
                type = "(SELECT def FROM types_table WHERE type_id=?)";

            query = "INSERT INTO data_table(id, owner, creator, type, mimetype, filename, c_date, data)"
                    + " VALUES(uuid2bin(?), " + group + ", ?, ?, ?, ?, ?, " + type + ")";

            stmt = con.prepareStatement(query);
            stmt.setString(1, uuid);
            if (gid != 0)
                stmt.setLong(2, gid);
            else
                stmt.setLong(2, uid);
            stmt.setLong(3, uid);
            stmt.setString(4, fileType);
            stmt.setString(5, mimetype);
            stmt.setString(6, filename);
            stmt.setLong(7, now.getTime());
            if (type_id == 0)
                stmt.setBlob(8, file);
            else
                stmt.setLong(8, type_id);
            stmt.executeUpdate();
            stmt.close();

            /// Add default rights
            if (gid != 0) {
                // TODO: Really use trigger/script or rollback feature in case something goes wrong
                /// Add 'Read' to sibling node and self
                String addRead = "";
                if (dbserveur.equals("mysql")) {
                    addRead = "INSERT IGNORE INTO group_rights";
                } else if (dbserveur.equals("oracle")) {
                    addRead = "INSERT /*+ ignore_row_on_dupkey_index(group_rights,group_rights_PK)*/ INTO group_rights(grid,id,RD,WR,DL,SB,AD,types_id)";
                }
                addRead += " SELECT grid, uuid2bin(?) AS id," +
                        " 1 AS RD, 0 AS WR, 0 AS DL, 0 AS SB, 0 AS AD, NULL AS types_id" +
                        " FROM group_group gg JOIN group_info gi ON gg.child_gid=gi.gid" +
                        " WHERE gg.gid IN (SELECT gid FROM group_group WHERE child_gid=?)";
                stmt = con.prepareStatement(addRead);
                stmt.setString(1, uuid);
                stmt.setLong(2, gid);
                stmt.executeUpdate();
                stmt.close();

                /// Add other rights to the creator
                String grid = "(SELECT grid FROM group_info WHERE gid=?)";
                query = "UPDATE group_rights SET WR=1, DL=1, SB=1"
                        + " WHERE grid=" + grid + " AND id=uuid2bin(?)";
                stmt = con.prepareStatement(query);
                stmt.setLong(1, gid);
                stmt.setString(2, uuid);
                stmt.executeUpdate();
                stmt.close();

                /// Find out what are the Add rights for this added type

                /// Add 'Add' Rights to creator
                String creatorAdd = "UPDATE group_rights" +
                        " SET AD=1, typesId=(";
                if (dbserveur.equals("mysql")) {
                    creatorAdd += "SELECT GROUP_CONCAT(type_allowed SEPARATOR ',')";
                } else if (dbserveur.equals("oracle")) {
                    creatorAdd += "SELECT LISTAGG(type_allowed, ',') WITHIN GROUP (ORDER BY type_allowed)";
                }
                creatorAdd += " AS typesId FROM type_add WHERE type_from=? AND who='Own')" +
                        " WHERE grid=" + grid + " AND id=uuid2bin(?)";
                stmt = con.prepareStatement(creatorAdd);
                stmt.setLong(1, type_id);
                stmt.setLong(2, gid);
                stmt.setString(3, uuid);
                stmt.executeUpdate();
                stmt.close();

                /// Add 'Add' Rights to others
                String otherAdd = "UPDATE group_rights SET AD=1, typesId=(";
                if (dbserveur.equals("mysql")) {
                    otherAdd += "SELECT GROUP_CONCAT(type_allowed SEPARATOR ',')";
                } else if (dbserveur.equals("oracle")) {
                    otherAdd += "SELECT LISTAGG(type_allowed, ',') WITHIN GROUP (ORDER BY type_allowed)";
                }
                otherAdd += " AS typesId FROM type_add WHERE type_from=? AND who='Other')" +
                        " WHERE grid IN (SELECT grid" +
                        " FROM group_group gg JOIN group_info gi ON gg.child_gid=gi.gid" +
                        " WHERE gg.gid IN (SELECT gid FROM group_group WHERE child_gid=?) AND gg.child_gid!=?)" +
                        " AND id=uuid2bin(?)";
                stmt = con.prepareStatement(otherAdd);
                stmt.setLong(1, type_id);
                stmt.setLong(2, gid);
                stmt.setLong(3, gid);
                stmt.setString(4, uuid);
                stmt.executeUpdate();
            }

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                    rs = null;
                }
                if (stmt != null) {
                    stmt.close();
                    stmt = null;
                }
            } catch (SQLException e) {
            }
        }

        return uuid;
    }

    String deleteResource(String uuid) {
        ResultSet rs = null;
        PreparedStatement stmt = null;
        try {
            /// Deleting stuff
            String query = "DELETE FROM data_table WHERE id=uuid2bin(?)";
            stmt = con.prepareStatement(query);
            stmt.setString(1, uuid);
            stmt.executeUpdate();
            stmt.close();

            /// Delete defined GroupRights related entry
            query = "DELETE FROM group_rights WHERE id=uuid2bin(?)";
            stmt = con.prepareStatement(query);
            stmt.setString(1, uuid);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                    rs = null;
                }
                if (stmt != null) {
                    stmt.close();
                    stmt = null;
                }
            } catch (SQLException e) {
            }
        }

        return uuid;
    }

    //// Get all files directly (no meta-data)
    Document myRawFileList() {
        try {
            String query = "SELECT bin2uuid(id), filename FROM data_table " +
                    "WHERE type='file' AND owner=?";
            stmt = con.prepareStatement(query);
            stmt.setLong(1, uid);
            rs = stmt.executeQuery();

            // displaying records
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = null;
            try {
                docBuilder = builderFactory.newDocumentBuilder();
            } catch (ParserConfigurationException e1) {
                e1.printStackTrace();
            }

            //creating a new instance of a DOM to build a DOM tree.
            Document doc = docBuilder.newDocument();
            Element root = doc.createElement("files");
            doc.appendChild(root);

            while (rs.next()) {
                String uuid = rs.getString(1);
                String filename = rs.getString(2);

                Element file = doc.createElement("files");
                file.setAttribute("id", uuid);
                file.setAttribute("filename", filename);

                root.appendChild(file);
            }

            return doc;
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                    rs = null;
                }
                if (stmt != null) {
                    stmt.close();
                    stmt = null;
                }
            } catch (SQLException e) {
            }
        }

        return null;
    }

    /// File and 'meta-data' stuff
    Document myMetaFileList() {
        try {
            String query = "SELECT bin2uuid(id), filename FROM data_table " +
                    "WHERE type='fileMetadata' AND owner=?";
            stmt = con.prepareStatement(query);
            stmt.setLong(1, uid);
            rs = stmt.executeQuery();

            // displaying records
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = null;
            try {
                docBuilder = builderFactory.newDocumentBuilder();
            } catch (ParserConfigurationException e1) {
                e1.printStackTrace();
            }

            //creating a new instance of a DOM to build a DOM tree.
            Document doc = docBuilder.newDocument();
            Element root = doc.createElement("files");
            doc.appendChild(root);

            while (rs.next()) {
                String uuid = rs.getString(1);
                String filename = rs.getString(2);

                Element file = doc.createElement("files");
                file.setAttribute("id", uuid);
                file.setAttribute("filename", filename);

                root.appendChild(file);
            }

            return doc;
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                    rs = null;
                }
                if (stmt != null) {
                    stmt.close();
                    stmt = null;
                }
            } catch (SQLException e) {
            }
        }

        return null;
    }


    boolean canSubstitute(String username) {
        if (username == null)
            return false;

        ///
        ResultSet rs = null;
        PreparedStatement stmt = null;
        try {
            String query = "SELECT userid FROM credential WHERE login=?  AND can_substitute=1 ";
            stmt = con.prepareStatement(query);
            stmt.setString(1, username);
            rs = stmt.executeQuery();

            if (rs.next())
                return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
        return false;
    }

    /// During POST
    int isValid(String username, String password) {
        if (username == null || password == null)
            return 0;

        ///
        ResultSet rs = null;
        PreparedStatement stmt = null;
        try {
            String query = "SELECT login FROM credential WHERE login=? AND password=UNHEX(SHA1(?))";
            if (dbserveur.equals("oracle")) {
                query = "SELECT login FROM credential WHERE login=? AND password=crypt(?)";
            }
            stmt = con.prepareStatement(query);
            stmt.setString(1, username);
            stmt.setString(2, password);
            rs = stmt.executeQuery();

            if (rs.next())
                return uid;
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                    rs = null;
                }
                if (stmt != null) {
                    stmt.close();
                    stmt = null;
                }
            } catch (SQLException e) {
            }
        }

        return 0;
    }

    void setCookie(String username, String cookie, int age) {
        ResultSet rs = null;
        PreparedStatement stmt = null;
        try {
            Date d = new Date();
            /// Adding stuff
            String query = "UPDATE credential SET token=?, c_date=? WHERE login=?";
            stmt = con.prepareStatement(query);
            stmt.setString(1, cookie);
            stmt.setLong(2, d.getTime() + age);
            stmt.setString(3, username);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                    rs = null;
                }
                if (stmt != null) {
                    stmt.close();
                    stmt = null;
                }
            } catch (SQLException e) {
            }
        }

    }

    /**
     * listing query
     */
    public Document usersList() {
        /// TODO: Have check if a profile is visible?
        ResultSet rs = null;
        PreparedStatement stmt = null;
        try {
            /// Might give too much
            String query = "SELECT display_name, login, userid FROM credential";
            stmt = con.prepareStatement(query);
            rs = stmt.executeQuery();

            // displaying records
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = null;
            try {
                docBuilder = builderFactory.newDocumentBuilder();
            } catch (ParserConfigurationException e1) {
                e1.printStackTrace();
            }

            //creating a new instance of a DOM to build a DOM tree.
            Document doc = docBuilder.newDocument();
            Element root = doc.createElement("users");
            doc.appendChild(root);
            while (rs.next()) {
                String name = rs.getString(1);
                String login = rs.getString(2);
                long user = rs.getLong(3);
                Element child = doc.createElement("user");
                child.setAttribute("name", name);
                child.setAttribute("login", login);
                child.setAttribute("uid", Long.toString(user));
                root.appendChild(child);
            }

            return doc;
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                    rs = null;
                }
                if (stmt != null) {
                    stmt.close();
                    stmt = null;
                }
            } catch (SQLException e) {
            }
        }

        return null;
    }

    public Document getTypeList() {
        ResultSet rs = null;
        PreparedStatement stmt = null;
        try {
            /// Might give too much
            String query = "SELECT type_id, owner, label FROM types_table";
            stmt = con.prepareStatement(query);
            rs = stmt.executeQuery();

            // displaying records
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = null;
            try {
                docBuilder = builderFactory.newDocumentBuilder();
            } catch (ParserConfigurationException e1) {
                e1.printStackTrace();
            }

            //creating a new instance of a DOM to build a DOM tree.
            Document doc = docBuilder.newDocument();
            Element root = doc.createElement("types");
            doc.appendChild(root);
            while (rs.next()) {
                long type_id = rs.getLong(1);
                long owner = rs.getLong(2);
                String label = rs.getString(3);
                Element child = doc.createElement("type");
                child.setAttribute("type_id", Long.toString(type_id));
                child.setAttribute("owner", Long.toString(owner));
                child.setAttribute("label", label);
                root.appendChild(child);
            }

            return doc;
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                    rs = null;
                }
                if (stmt != null) {
                    stmt.close();
                    stmt = null;
                }
            } catch (SQLException e) {
            }
        }

        return null;
    }

    /**
     * Groups related methods
     */

    Document myGroupList() {
        /// Select group that I own or belong to
        ResultSet rs = null;
        PreparedStatement stmt = null;
        try {
      /*
      SELECT t1.gid, t1.child_gid, t2.child_gid, t3.child_gid
       FROM group_group as t1
       RIGHT JOIN group_group AS t2 ON t1.child_gid=t2.gid
       RIGHT JOIN group_group AS t3 ON t2.child_gid=t3.gid
       WHERE t3.child_gid IN (SELECT gid FROM group_user WHERE userid=?);

      SELECT gi.label, gi.owner, gi.grid, t3.child_gid, null AS child_gid FROM group_info gi, group_group AS t3 WHERE t3.child_gid IN (SELECT gid FROM group_user WHERE userid=?) AND gi.gid=t3.child_gid
      UNION
      SELECT gi.label, gi.owner, gi.grid, t3.gid, t3.child_gid FROM group_info gi, group_group AS t3 WHERE t3.child_gid IN (SELECT gid FROM group_user WHERE userid=?) AND gi.gid=t3.gid
      UNION
      SELECT gi.label, gi.owner, gi.grid, t2.gid, t2.child_gid FROM group_info gi, group_group AS t2 RIGHT JOIN group_group AS t3 ON t2.child_gid=t3.gid WHERE t3.child_gid IN (SELECT gid FROM group_user WHERE userid=?) AND gi.gid=t2.gid
      UNION
      SELECT gi.label, gi.owner, gi.grid, t1.gid, t1.child_gid FROM group_info gi, group_group AS t1 RIGHT JOIN group_group as t2 ON t1.child_gid=t2.gid RIGHT JOIN group_group AS t3 ON t2.child_gid=t3.gid WHERE t3.child_gid IN (SELECT gid FROM group_user WHERE userid=?) AND gi.gid=t1.gid
      UNION
      SELECT gi.label, gi.owner, gi.grid, gi.gid, gg.child_gid FROM group_info gi LEFT JOIN group_group gg ON gi.gid=gg.gid WHERE gi.owner=?;
      */
            String query =
                    /// Select 4th level
                    "SELECT gi.label, gi.owner, gi.grid, t3.child_gid, null AS child_gid FROM group_info gi, group_group AS t3 WHERE t3.child_gid IN (SELECT gid FROM group_user WHERE userid=?) AND gi.gid=t3.child_gid" +
                            " UNION" +
                            /// Select 3rd level
                            " SELECT gi.label, gi.owner, gi.grid, t3.gid, t3.child_gid FROM group_info gi, group_group AS t3 WHERE t3.child_gid IN (SELECT gid FROM group_user WHERE userid=?) AND gi.gid=t3.gid" +
                            " UNION" +
                            /// Select 2nd level
                            " SELECT gi.label, gi.owner, gi.grid, t2.gid, t2.child_gid FROM group_info gi, group_group AS t2 RIGHT JOIN group_group AS t3 ON t2.child_gid=t3.gid WHERE t3.child_gid IN (SELECT gid FROM group_user WHERE userid=?) AND gi.gid=t2.gid" +
                            " UNION" +
                            /// Select top level
                            " SELECT gi.label, gi.owner, gi.grid, t1.gid, t1.child_gid FROM group_info gi, group_group AS t1 RIGHT JOIN group_group as t2 ON t1.child_gid=t2.gid RIGHT JOIN group_group AS t3 ON t2.child_gid=t3.gid WHERE t3.child_gid IN (SELECT gid FROM group_user WHERE userid=?) AND gi.gid=t1.gid" +
                            " UNION" +
                            /// Select owned groups
                            " SELECT gi.label, gi.owner, gi.grid, gi.gid, gg.child_gid FROM group_info gi LEFT JOIN group_group gg ON gi.gid=gg.gid WHERE gi.owner=?";
            stmt = con.prepareStatement(query);
            stmt.setLong(1, uid);
            stmt.setLong(2, uid);
            stmt.setLong(3, uid);
            stmt.setLong(4, uid);
            stmt.setLong(5, uid);
            rs = stmt.executeQuery();

            // displaying records
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = null;
            try {
                docBuilder = builderFactory.newDocumentBuilder();
            } catch (ParserConfigurationException e1) {
                e1.printStackTrace();
            }

            //creating a new instance of a DOM to build a DOM tree.
            Document doc = docBuilder.newDocument();
            Element root = doc.createElement("groups");
            doc.appendChild(root);

            while (rs.next()) {
                String label = rs.getString(1);
                long owner = rs.getLong(2);
                long grid = rs.getLong(3);
                long gid = rs.getLong(4);
                long child_gid = rs.getLong(5);

                Element right = doc.createElement("group");
                if (child_gid != 0)
                    right.setAttribute("child_gid", Long.toString(child_gid));
                right.setAttribute("id", Long.toString(gid));
                right.setAttribute("owner", Long.toString(owner));
                if (grid != 0)
                    right.setAttribute("templateId", Long.toString(grid));

                Element labelNode = doc.createElement("label");
                labelNode.setTextContent(label);

                right.appendChild(labelNode);
                root.appendChild(right);
            }

            return doc;

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                    rs = null;
                }
                if (stmt != null) {
                    stmt.close();
                    stmt = null;
                }
            } catch (SQLException e) {
            }
        }

        return null;
    }

    /// Find sibling group (and grid) in the hierarchy.
    /// Used when delegating rights and we don't want to 'spill' too much data.
    Document siblingGroupList(long groupid) {
        ResultSet rs = null;
        PreparedStatement stmt = null;
        try {
            String query = "SELECT gi.owner, gi.gid, gi.grid, gi.label " +
                    "FROM group_group gg, group_info gi " +
                    "WHERE gg.child_gid=gi.gid AND gg.gid IN " +
                    "(SELECT gid FROM group_group WHERE child_gid=?)";
            stmt = con.prepareStatement(query);
            stmt.setLong(1, groupid);
            rs = stmt.executeQuery();

            // displaying records
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = null;
            try {
                docBuilder = builderFactory.newDocumentBuilder();
            } catch (ParserConfigurationException e1) {
                e1.printStackTrace();
            }

            //creating a new instance of a DOM to build a DOM tree.
            Document doc = docBuilder.newDocument();
            Element root = doc.createElement("siblings");
            root.setAttribute("id", Long.toString(groupid));
            doc.appendChild(root);

            while (rs.next()) {
                long owner = rs.getLong(1);
                long gid = rs.getLong(2);
                long grid = rs.getLong(3);
                String label = rs.getString(4);

                Element group = doc.createElement("siblingGroup");
                group.setAttribute("id", Long.toString(gid));
                group.setAttribute("owner", Long.toString(owner));
                if (grid != 0)
                    group.setAttribute("templateId", Long.toString(grid));

                Element labelNode = doc.createElement("label");
                labelNode.setTextContent(label);

                group.appendChild(labelNode);
                root.appendChild(group);
            }

            return doc;
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                    rs = null;
                }
                if (stmt != null) {
                    stmt.close();
                    stmt = null;
                }
            } catch (SQLException e) {
            }
        }

        return null;
    }

    //// Fetch user list from group
    Document group_userList(long gid) {
        /// Select group that I own
        ResultSet rs = null;
        PreparedStatement stmt = null;
        try {
            /// Check group list if it's ours, might relax this later
            String query = "SELECT g.label, g.grid, gu.userid FROM group_info AS g, group_user AS gu" +
                    " WHERE g.gid=gu.gid AND g.gid=? AND g.owner=?";
            stmt = con.prepareStatement(query);
            stmt.setLong(1, gid);
            stmt.setLong(2, uid);
            rs = stmt.executeQuery();

            // displaying records
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = null;
            try {
                docBuilder = builderFactory.newDocumentBuilder();
            } catch (ParserConfigurationException e1) {
                e1.printStackTrace();
            }

            //creating a new instance of a DOM to build a DOM tree.
            Document doc = docBuilder.newDocument();
            Element root = doc.createElement("group");
            root.setAttribute("groupId", Long.toString(gid));
            doc.appendChild(root);

            if (rs.next()) {
                String label = rs.getString(1);
                long grid = rs.getLong(2);

                root.setAttribute("groupRightId", Long.toString(grid));
                root.setAttribute("label", label);

                do {
                    long userid = rs.getLong(3);

                    Element item = doc.createElement("user");
                    root.appendChild(item);
                    item.setAttribute("userId", Long.toString(userid));

                } while (rs.next());
            }

            return doc;
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                    rs = null;
                }
                if (stmt != null) {
                    stmt.close();
                    stmt = null;
                }
            } catch (SQLException e) {
            }
        }

        return null;
    }

    long createGroup(String groupName) {
        ResultSet rs = null;
        PreparedStatement stmt = null;

        try {
            /// Adding stuff
            String query = "INSERT INTO group_info(owner, label) VALUES(?, ?)";
            stmt = con.prepareStatement(query);
            stmt.setLong(1, uid);
            stmt.setString(2, groupName);
            stmt.executeUpdate();
            stmt.close();

            /// Add default rights
            if (dbserveur.equals("mysql")) {
                query = "SELECT LAST_INSERT_ID() FROM group_info";
            } else if (dbserveur.equals("oracle")) {
                query = "SELECT group_info_SEQ.CURRVAL FROM DUAL";
            }
            stmt = con.prepareStatement(query);
            rs = stmt.executeQuery();

            if (rs.next()) {
                long value = rs.getLong(1);
                return value;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                    rs = null;
                }
                if (stmt != null) {
                    stmt.close();
                    stmt = null;
                }
            } catch (SQLException e) {
            }
        }
        return 0;
    }

    boolean updateGroupName(long groupId, String groupName) {
        return false;
    }

    boolean bindGroupTemplate(long groupId, long templateId) {
        ResultSet rs = null;
        PreparedStatement stmt = null;

        try {
            /// Check if group is owned by 'username' and has a binding
            String queryCheck = "SELECT gid FROM group_info" +
                    " WHERE gid=? AND owner=?";
            stmt = con.prepareStatement(queryCheck);
            stmt.setLong(1, groupId);
            stmt.setLong(2, uid);
            rs = stmt.executeQuery();

            if (rs.next() == false)  // Not owned by username or doesn't exist
                return false;
            stmt.close();

            /// Update group entry
            String queryUpdate = "UPDATE group_info SET grid=? WHERE gid=? AND owner=?";
            stmt = con.prepareStatement(queryUpdate);
            stmt.setLong(1, templateId);
            stmt.setLong(2, groupId);
            stmt.setLong(3, uid);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                    rs = null;
                }
                if (stmt != null) {
                    stmt.close();
                    stmt = null;
                }
            } catch (SQLException e) {
            }
        }

        return true;
    }

    //// More efficient way to do it?
    /// remove link from group and template, rerun all related rights
    boolean unbindGroupTemplate(long groupId) {
        ResultSet rs = null;
        PreparedStatement stmt = null;

        try {
            /// Check if group is owned by 'username' and has a binding to specified template
            String queryCheck = "SELECT grid FROM group_info" +
                    " WHERE gid=? AND owner=?";
            stmt = con.prepareStatement(queryCheck);
            stmt.setLong(1, groupId);
            stmt.setLong(2, uid);
            rs = stmt.executeQuery();

            if (rs.next() == false)  // Not owned by username or doesn't exist
                return false;
            stmt.close();

            //// Remove binding
            String queryUnbind = "UPDATE group_info SET grid=null WHERE gid=? AND owner=?";
            stmt = con.prepareStatement(queryUnbind);
            stmt.setLong(1, groupId);
            stmt.setLong(2, uid);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                    rs = null;
                }
                if (stmt != null) {
                    stmt.close();
                    stmt = null;
                }
            } catch (SQLException e) {
            }
        }

        return true;
    }

    //// Add user in group, if there is a template binding, add those rights
    boolean addUserInGroup(long groupId, long userId) {
        ResultSet rs = null;
        PreparedStatement stmt = null;

        try {
            /// Check if group is owned by 'username' and has a binding
            String queryCheck = "SELECT owner, grid FROM group_info" +
                    " WHERE gid=? AND owner=?";
            stmt = con.prepareStatement(queryCheck);
            stmt.setLong(1, groupId);
            stmt.setLong(2, uid);
            rs = stmt.executeQuery();

            if (rs.next())   // Owned by username
            {
                /// add user in group
                String queryAdd = "INSERT INTO group_user(gid,userid) VALUES(?,?)";
                stmt = con.prepareStatement(queryAdd);
                stmt.setLong(1, groupId);
                stmt.setLong(2, userId);
                stmt.executeUpdate();
                stmt.close();
            } else  // Not owned by username or doesn't exist
                return false;

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                    rs = null;
                }
                if (stmt != null) {
                    stmt.close();
                    stmt = null;
                }
            } catch (SQLException e) {
            }
        }

        return true;
    }

    //// Remove user from group, if a template is linked, rerun rights merging
    //// More efficient way to do it?
    boolean removeUserInGroup(long groupId, long userId) {
        ResultSet rs = null;
        PreparedStatement stmt = null;

        try {
            /// Check if group is owned by 'username' and has a binding to specified template
            String queryCheck = "SELECT owner, grid FROM group_info" +
                    " WHERE gid=? AND owner=?";
            stmt = con.prepareStatement(queryCheck);
            stmt.setLong(1, groupId);
            stmt.setLong(2, uid);
            rs = stmt.executeQuery();

            if (rs.next())   // Owned by username
            {
                /// remove user from group
                String queryDelete = "DELETE FROM group_user WHERE gid=? AND userid=?";
                stmt = con.prepareStatement(queryDelete);
                stmt.setLong(1, groupId);
                stmt.setLong(2, userId);
                stmt.executeUpdate();
                stmt.close();
            } else  // Not owned by username or doesn't exist
                return false;

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                    rs = null;
                }
                if (stmt != null) {
                    stmt.close();
                    stmt = null;
                }
            } catch (SQLException e) {
            }
        }

        return true;
    }

    //// Remove group, if there's a binding, unlink things
    boolean removeGroup(long groupId) {
        ResultSet rs = null;
        PreparedStatement stmt = null;

        try {
            /// Check if group is owned by 'username' and has a binding to specified template
            String queryCheck = "SELECT grid FROM group_info WHERE gid=? AND owner=?";
            stmt = con.prepareStatement(queryCheck);
            stmt.setLong(1, groupId);
            stmt.setLong(2, uid);
            rs = stmt.executeQuery();

            if (rs.next())   // Owned by username
            {
                // Nothing set or it's not the one specified, so no need to unbind anything
//        if( grid == 0 )
//          return true;
            } else  // Not owned by username or doesn't exist
                return false;
            stmt.close();

            /// Unbind group
//      unbindGroupTemplate( groupId );

            /// Delete group
            /// Remove group and instanciation (the users in the group)
            String queryDelete = "DELETE FROM group_user WHERE gid=?";
            stmt = con.prepareStatement(queryDelete);
            stmt.setLong(1, groupId);
            stmt.executeUpdate();
            stmt.close();

            /// Remove group
            queryDelete = "DELETE FROM group_info WHERE gid=?";
            stmt = con.prepareStatement(queryDelete);
            stmt.setLong(1, groupId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                    rs = null;
                }
                if (stmt != null) {
                    stmt.close();
                    stmt = null;
                }
            } catch (SQLException e) {
            }
        }

        return true;
    }


    /// General check for other requests
    /// TODO: Check token validity
    long hasCredentials(String login, String token) {
        ResultSet rs = null;
        PreparedStatement stmt = null;
        try {
            String query = "SELECT userid FROM credential WHERE login=? AND token=?";
            stmt = con.prepareStatement(query);
            stmt.setString(1, login);
            stmt.setString(2, token);
            rs = stmt.executeQuery();

            if (rs.next())
                return rs.getLong(1);
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                    rs = null;
                }
                if (stmt != null) {
                    stmt.close();
                    stmt = null;
                }
            } catch (SQLException e) {
            }
        }

        return 0;
    }

    /// {gid} from where we are seeing the resource {uuid} with {action}
    boolean hasRight(long gid, String uuid, String action, long tid) {
        /// Something specific to do when there is not all rights asked
        if (uuid == null) return false;
        ResultSet rs = null;
        PreparedStatement stmt = null;
        try {
            /// Check if it's the owner's
            if (gid == 0) {
                String query = "SELECT owner FROM data_table AS d" +
                        " WHERE d.owner=? AND d.id=uuid2bin(?)";
                stmt = con.prepareStatement(query);
                stmt.setLong(1, uid);
                stmt.setString(2, uuid);
            } else  /// Shared from someone else
            {
                String act = "";
                if ("Read".equals(action))
                    act = "RD";
                else if ("Write".equals(action))
                    act = "WR";
                else if ("Delete".equals(action))
                    act = "DL";
                else if ("Submit".equals(action))
                    act = "SB";
                else if ("Add".equals(action))
                    act = "AD";

                String type = "";
                if (tid != 0)
                    type = " AND typesId LIKE (?)";

                String query = "SELECT " + act + " FROM group_user AS gu, group_info AS gi, group_rights AS gr" +
                        " WHERE gu.gid=gi.gid AND gi.grid=gr.grid" +
                        " AND gu.userid=? AND gu.gid=? AND gr.id=uuid2bin(?) AND gr." + act + "=1" + type;
                stmt = con.prepareStatement(query);
                stmt.setLong(1, uid);
                stmt.setLong(2, gid);
                stmt.setString(3, uuid);
                if (tid != 0)
                    stmt.setString(4, "%" + Long.toString(tid) + "%");
            }

            rs = stmt.executeQuery();
            if (rs.next()) {
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                    rs = null;
                }
                if (stmt != null) {
                    stmt.close();
                    stmt = null;
                }
            } catch (SQLException e) {
            }
        }

        return false;
    }

    /// Get needed data from cookies
    /// 0: login, 1: token
    /// TODO: Update cookie date
    static public String[] getLogin(Cookie[] cookies) {
        String login = null, token = null;
        String[] ret = {login, token};
        if (cookies == null) return ret;

        for (int i = 0; i < cookies.length; ++i) {
            Cookie cookie = cookies[i];
            String name = cookie.getName();
            if ("credential".equals(name))
                token = cookie.getValue();
            else if ("user".equals(name))
                login = cookie.getValue();
        }

        ret[0] = login;
        ret[1] = token;
        return ret;
    }

}