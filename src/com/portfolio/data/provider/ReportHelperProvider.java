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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
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
import java.sql.Statement;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.http.HttpResponse;
import org.apache.http.impl.client.HttpClients;
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

//import com.mysql.jdbc.Statement;
import com.portfolio.data.utils.ConfigUtils;
import com.portfolio.data.utils.DomUtils;
import com.portfolio.data.utils.FileUtils;
import com.portfolio.data.utils.LogUtils;
import com.portfolio.data.utils.PostForm;
import com.portfolio.data.utils.SqlUtils;
import com.portfolio.rest.RestWebApplicationException;
import com.portfolio.security.Credential;
import com.portfolio.security.NodeRight;

public class ReportHelperProvider {

	final Logger logger = LoggerFactory.getLogger(ReportHelperProvider.class);

	final private Credential cred = new Credential();
	static BufferedWriter securityLog = null;
	private String dbserveur = null;

	DataSource ds = null;

	public ReportHelperProvider() throws Exception
	{
		dbserveur = ConfigUtils.getInstance().getProperty("serverType");
		String securitylog = ConfigUtils.getInstance().getProperty("security_log");
		if( !"".equals(securitylog) && securitylog != null )
			securityLog = LogUtils.getLog(securitylog);
	}

	public String getVector( Connection c, int userId, HashMap<String, String> map ) throws SQLException
	{
		PreparedStatement st;
		String sql;

		Set<Entry<String, String>> values = map.entrySet();
		ArrayList<String> cols = new ArrayList<String>();
		ArrayList<String> vals = new ArrayList<String>();
		Iterator<Entry<String, String>> iterval = values.iterator();
		while( iterval.hasNext() )
		{
			Entry<String, String> entry = iterval.next();
			cols.add(entry.getKey()+"=?");
			vals.add(entry.getValue());
		}
		
		String col = String.join(" AND ", cols);
		sql = String.format("SELECT * FROM vector_table WHERE %s;", col);
		System.out.println("SQL: "+sql);
		st = c.prepareStatement(sql);
		
		for( int i=0; i<vals.size(); i++ )
		{
			st.setString(i+1, vals.get(i));
			System.out.println("PARAMS "+(i+1)+" VAL: "+vals.get(i));
		}
		ResultSet rs = st.executeQuery();
		StringBuilder output = new StringBuilder();
		output.append("<vectors>");
		if( rs != null)
			while( rs.next() )
			{
				int userid = rs.getInt("userid");
				Date date = rs.getDate("date");
				output.append(MessageFormat.format("<vector uid={0,number,integer} date=''{1,date,short} {1,time,medium}''>", userid, date));
				
				for(int i=1; i<=10; i++)
				{
					String a_n = "a"+i;
					String a_val = rs.getString(a_n);
					if( !"".equals(a_val) )
						output.append(String.format("<%s>%s</%s>", a_n, a_val, a_n));
				}
				
				output.append("</vector>");
			}
		output.append("</vectors>");
		
		rs.close();
		st.close();

		return output.toString();
	}

	public int writeVector( Connection c, int userId, HashMap<String, String> map ) throws SQLException
	{
		PreparedStatement st;
		String sql;

		Set<Entry<String, String>> values = map.entrySet();
		ArrayList<String> holder = new ArrayList<String>();
		ArrayList<String> cols = new ArrayList<String>();
		ArrayList<String> vals = new ArrayList<String>();
		Iterator<Entry<String, String>> iterval = values.iterator();
		while( iterval.hasNext() )
		{
			Entry<String, String> entry = iterval.next();
			holder.add("?");
			cols.add(entry.getKey());
			vals.add(entry.getValue());
		}
		
		// Check if previous vector exist
		int count = checkVector(c, cols, vals);
		if( count > 0 ) return -1;
		
		String colsjoin = String.join(",", cols);
		String holderjoin = String.join(",", holder);
		sql = String.format("INSERT INTO vector_table(%s) VALUES(%s);", colsjoin, holderjoin);
		st = c.prepareStatement(sql);
		
		System.out.println("SQL: "+sql);
		
		for( int i=0; i<vals.size(); i++ )
		{
			st.setString(i+1, vals.get(i));
			System.out.println("PARAMS "+(i+1)+" VAL: "+vals.get(i));
		}
		st.executeUpdate();
		st.close();
		
		return 0;
	}
	
	public int deleteVector( Connection c, HashMap<String, String> map ) throws SQLException
	{
		ArrayList<String> cols = new ArrayList<String>();
		ArrayList<String> vals = new ArrayList<String>();
		Iterator<Entry<String, String>> iterval = map.entrySet().iterator();
		while( iterval.hasNext() )
		{
			Entry<String, String> entry = iterval.next();
			if( "date".equals(entry.getKey()) )
				cols.add(entry.getKey()+"<?");
			else
				cols.add(entry.getKey()+"=?");
			vals.add(entry.getValue());
		}
		
		String col = String.join(" AND ", cols);
		String sql = String.format("DELETE FROM vector_table WHERE %s;", col);
		System.out.println("SQL: "+sql);
		PreparedStatement st = c.prepareStatement(sql);
		
		for( int i=0; i<vals.size(); i++ )
		{
			st.setString(i+1, vals.get(i));
			System.out.println("PARAMS "+(i+1)+" VAL: "+vals.get(i));
		}
		int count = st.executeUpdate();
		
		st.close();

		return count;
	}
	
	/// Because can't make UNIQUE key on all column. Size is too big
	public int checkVector( Connection c, ArrayList<String> cols, ArrayList<String> vals ) throws SQLException
	{
		String col = String.join("=? AND ", cols);
		col += "=?";
		String sql = String.format("SELECT COUNT(*) FROM vector_table WHERE %s;", col);
		System.out.println("SQL: "+sql);
		PreparedStatement st = c.prepareStatement(sql);
		
		for( int i=0; i<vals.size(); i++ )
		{
			st.setString(i+1, vals.get(i));
		}
		ResultSet rs = st.executeQuery();
		
		int count = 0;
		if (rs != null && ( rs.next() ) )
			count = rs.getInt(1);
		
		rs.close();
		st.close();
		
		System.out.println(String.format("VECTOR CHECK FOUND (%d) VALUES", count));
		
		return count;
	}
	
}
