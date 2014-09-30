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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;

/**
 * This class will create a log entry containing the LMS system's user ID, user EID,
 * the WAD system's user ID, along with the LTI consumer key
 * @author Chris Maurer<maurercw@gmail.com>
 *
 */
public class LTIUserLog {

	/**
	 * Get the id for a log entry with a matching user IDs and consumer_key
	 * @param connexion Connection Object to use for database communication
	 * @param lms_user_id User ID from the LMS to be used as a lookup value
	 * @param lms_user_eid User EID from the LMS to be used as a lookup value
	 * @param wad_user_id	User ID from WAD to be used as a lookup value
	 * @param consumer_key Consumer key to be used as a lookup value
	 * @param outTrace For debug logging
	 * @return The id of the lti_user_log record
	 * @throws Exception
	 */
	public static String getLogEntryId(Connection connexion,String lms_user_id,String lms_user_eid,String wad_user_id, String consumer_key, StringBuffer outTrace) throws Exception {
		String id="0";
		PreparedStatement ps = null;
		ResultSet rs = null;
		String reqSQL = null;

		try{
			reqSQL = "SELECT id FROM lti_user_log WHERE lms_user_id=? and lms_user_eid=? and wad_user_id=? and consumer_key=?";
			ps = connexion.prepareStatement(reqSQL);
			ps.setString(1,lms_user_id);
			ps.setString(2,lms_user_eid);
			ps.setString(3,wad_user_id);
			ps.setString(4,consumer_key);
			rs = ps.executeQuery();
			if (rs.next()) {
				id = rs.getString("id");
			}
		}	catch(Exception e){
			System.out.println("Error getting lti_user_log id for lms_user_id:" + lms_user_id + " and consumer_key:" + consumer_key);
		}	finally {
			cleanup(rs, ps);
		}
		return id;
	}

	/**
	 * Write a new record to the lti_user_log table
	 * @param connexion Connection Object to use for database communication
	 * @param lms_user_id User ID from the LMS
	 * @param lms_user_eid User EID from the LMS
	 * @param wad_user_id	User ID from WAD
	 * @param consumer_key Consumer key
	 * @param outTrace For debug logging
	 * @return An XML element containing the lti_user_log id
	 * @throws Exception
	 */
	public static StringBuffer createUserLogEntry(Connection connexion,String lms_user_id,String lms_user_eid,String wad_user_id,String consumer_key, StringBuffer outTrace) throws Exception {
		PreparedStatement ps = null;
		ResultSet rs = null;
		StringBuffer result = new StringBuffer();
		outTrace.append("\ncreateUserLogEntry ===");
		try{
			String sqlStatement = "INSERT INTO lti_user_log(lms_user_id,lms_user_eid,wad_user_id,creationtime,consumer_key) VALUES (?,?,?,?,?)";
			ps = connexion.prepareStatement(sqlStatement);
			ps.setString(1,lms_user_id);
			ps.setString(2,lms_user_eid);
			ps.setString(3,wad_user_id);
			ps.setTimestamp(4,new Timestamp(new Date().getTime()));
			ps.setString(5,consumer_key);
			ps.executeUpdate();

			String id ="";
			rs = ps.getGeneratedKeys();
			if (rs.next()) {
				id = rs.getString(1);
			}
			rs.close();

			result.append("\n<lti_user_log id='"+id+"'/>");

		}	catch(Exception e){
			System.out.println("\n Erreur dans createUserLogEntry:"+e);
		}	finally {
			cleanup(rs, ps);
		}

		return result;
	}

	/**
	 * Clean up the rs and ps by ensuring that they are closed
	 * @param rs ResultSet that will be closed
	 * @param ps PreparedStatement that will be closed
	 */
	private static void cleanup(ResultSet rs, PreparedStatement ps) {
		if (rs != null) {
			try {
				rs.close();
			}
			catch (SQLException e) {
			}
		}
		if (ps != null) {
			try {
				ps.close();
			}
			catch (SQLException e) {
			}
		}
	}
}
