/* =======================================================
	Copyright 2021 - ePortfolium - Licensed under the
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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReportHelperProvider {

	private static final Logger logger = LoggerFactory.getLogger(ReportHelperProvider.class);
	//private static final Logger securityLog = LoggerFactory.getLogger("securityLogger");

	public ReportHelperProvider() {}

	public String getVector( Connection c, int userId, HashMap<String, String> map ) throws SQLException {
		PreparedStatement st;
		final String sql;

		final Set<Entry<String, String>> values = map.entrySet();
		ArrayList<String> cols = new ArrayList<>();
		ArrayList<String> vals = new ArrayList<>();
		for (Entry<String, String> entry : values) {
			cols.add(entry.getKey() + "=?");
			vals.add(entry.getValue());
		}

		sql = String.format("SELECT * FROM vector_table WHERE %s;", String.join(" AND ", cols));
		logger.debug("SQL: {}", sql);
		st = c.prepareStatement(sql);

		for( int i=0; i<vals.size(); i++ ) {
			st.setString(i+1, vals.get(i));
			logger.debug("PARAMS {} VAL: {}", i+1, vals.get(i));
		}
		final ResultSet rs = st.executeQuery();
		StringBuilder output = new StringBuilder();
		output.append("<vectors>");
		if( rs != null) {
			while (rs.next()) {
				final int userid = rs.getInt("userid");
				final Date date = rs.getDate("date");
				output.append(MessageFormat.format("<vector uid=''{0,number,integer}'' date=''{1,date,short} {1,time,medium}''>", userid, date));

				for (int i = 1; i <= 10; i++) {
					final String a_n = "a" + i;
					final String a_val = rs.getString(a_n);
					if (!"".equals(a_val))
						output.append(String.format("<%s>%s</%s>", a_n, a_val, a_n));
				}
				output.append("</vector>");
			}
			rs.close();
		}
		output.append("</vectors>");
		st.close();

		return output.toString();
	}

	public int writeVector( Connection c, int userId, HashMap<String, String> map ) throws SQLException {
		PreparedStatement st;
		final String sql;

		final Set<Entry<String, String>> values = map.entrySet();
		ArrayList<String> holder = new ArrayList<>();
		ArrayList<String> cols = new ArrayList<>();
		ArrayList<String> vals = new ArrayList<>();
		for (Entry<String, String> entry : values) {
			holder.add("?");
			cols.add(entry.getKey());
			vals.add(entry.getValue());
		}

		// Check if previous vector exist
		final int count = checkVector(c, cols, vals);
		if( count > 0 ) return -1;

		sql = String.format("INSERT INTO vector_table(%s) VALUES(%s);",
				String.join(",", cols),
				String.join(",", holder));
		st = c.prepareStatement(sql);

		logger.debug("SQL: {}", sql);

		for( int i=0; i<vals.size(); i++ ) {
			st.setString(i+1, vals.get(i));
			logger.debug("PARAMS {} VAL: {}", i+1, vals.get(i));
		}
		st.executeUpdate();
		st.close();

		return 0;
	}

	public int deleteVector( Connection c, HashMap<String, String> map ) throws SQLException {
		ArrayList<String> cols = new ArrayList<>();
		ArrayList<String> vals = new ArrayList<>();
		for (Entry<String, String> entry : map.entrySet()) {
			if ("date".equals(entry.getKey()))
				cols.add(entry.getKey() + "<?");
			else
				cols.add(entry.getKey() + "=?");
			vals.add(entry.getValue());
		}

		final String sql = String.format("DELETE FROM vector_table WHERE %s;", String.join(" AND ", cols));
		logger.debug("SQL: {}", sql);
		PreparedStatement st = c.prepareStatement(sql);

		for( int i=0; i<vals.size(); i++ ) {
			st.setString(i+1, vals.get(i));
			logger.debug("PARAMS {} VAL: {}", i+1, vals.get(i));
		}
		int count = st.executeUpdate();

		st.close();

		return count;
	}

	/// Because can't make UNIQUE key on all column. Size is too big
	public int checkVector( Connection c, ArrayList<String> cols, ArrayList<String> vals ) throws SQLException	{
		String col = String.join("=? AND ", cols);
		col += "=?";
		final String sql = String.format("SELECT COUNT(*) FROM vector_table WHERE %s;", col);
		logger.debug("SQL: {}", sql);
		PreparedStatement st = c.prepareStatement(sql);

		for( int i=0; i<vals.size(); i++ ) {
			st.setString(i+1, vals.get(i));
		}
		ResultSet rs = st.executeQuery();

		int count = 0;
		if (rs != null && ( rs.next() ) ) {
			count = rs.getInt(1);
			rs.close();
		}
		st.close();

		logger.debug(String.format("VECTOR CHECK FOUND (%d) VALUES", count));

		return count;
	}

}