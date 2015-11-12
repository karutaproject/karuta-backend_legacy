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

package com.portfolio.data.attachment;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.portfolio.data.provider.DataProvider;
import com.portfolio.data.utils.ConfigUtils;
import com.portfolio.data.utils.LogUtils;
import com.portfolio.data.utils.SqlUtils;
import com.portfolio.security.Credential;

public class DirectURLService  extends HttpServlet {

	/**
	 *
	 */
	final Logger logger = LoggerFactory.getLogger(DirectURLService.class);
	private static final long serialVersionUID = 9188067506635747901L;

	DataProvider dataProvider;
	boolean hasNodeReadRight = false;
	boolean hasNodeWriteRight = false;
	HttpSession session;
	ArrayList<String> ourIPs = new ArrayList<String>();

	@Override
	public void init( ServletConfig config ) throws ServletException
	{
		super.init(config);
		/// List possible local address
		try
		{
			dataProvider = SqlUtils.initProvider(getServletContext(), logger);
			Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
			while (interfaces.hasMoreElements()){
				NetworkInterface current = interfaces.nextElement();
				if (!current.isUp() || current.isLoopback() || current.isVirtual()) continue;
				Enumeration<InetAddress> addresses = current.getInetAddresses();
				while (addresses.hasMoreElements()){
					InetAddress current_addr = addresses.nextElement();
					if (current_addr instanceof Inet4Address)
						ourIPs.add(current_addr.getHostAddress());
				}
			}
			
		}
		catch( Exception e )
		{
		}
	}

	public void initialize(HttpServletRequest httpServletRequest)
	{
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException
	{
		String val = request.getParameter("i");
		byte[] data = hexStringToByteArray(val);
		/// Decrypt data
		Cipher rc4;
		String output="";
		try
		{
			rc4 = Cipher.getInstance("RC4");
			String secretkey = ConfigUtils.get("directkey");
			SecretKeySpec key = new SecretKeySpec(secretkey.getBytes(), "RC4");
			rc4.init(Cipher.DECRYPT_MODE, key);

			byte[] ciphertext = rc4.update(data);
	    output = new String(ciphertext);
		}
		catch( NoSuchAlgorithmException e )
		{
			e.printStackTrace();
		}
		catch( NoSuchPaddingException e )
		{
			e.printStackTrace();
		}
		catch( InvalidKeyException e )
		{
			e.printStackTrace();
		}

		/// Check case we are in, act accordingly
		
		String[] splitData = output.split(" ");
		String uuid = splitData[0];
		String email = splitData[1];
		String role = splitData[2];

		/// Keeping access log
		BufferedWriter log = LogUtils.getLog("directAccess.log");
		Date date = new Date();
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		String datestring = dateFormat.format(date);
		log.write("["+datestring+"] Direct link access by: "+email+ " ("+role+") for uuid: "+uuid);
		log.newLine();
		log.flush();
		log.close();
		
		/// log in person with associated email
		Connection c = null;
		try
		{
			session = request.getSession(true);
			c = SqlUtils.getConnection(getServletContext());
			String[] login = dataProvider.logViaEmail(c, email);

			if( login != null )	// If account exists
			{
				/// Init DB connection

				///// TODO: Log email, uuid, role access, hour, ip, date
				
				//// FIXME: Make it so we create account and put this new account in the uuid/role group

				// TODO
				/*
				///// Check if uuid hasn't been shared already in a previous call (specific table)
				/// Prevent sharing with another personal account after being evaluated.
				/// Since the specific group will be the username (specific rights),
				/// we can also know if student tried sharing it with self first

				///// Check if this user is not giving rights to self (existing user account)

				///// Check if user has some access to this uuid
				/// Prevent somebody else to share another student node

				///// Check if user has right to share

				//// Put person in specified group
				//*/

				/// Check if person exist
				int uid = Integer.parseInt(login[2]);
				if( uid > 0 )
				{
					/// Find group for this node
					int rrgid = dataProvider.getRoleByNode(c, 1, uuid, role);

					/// Put person in specified group
					String userInfo = "<users><user id='"+uid+"' /></users>";
					dataProvider.postRRGUsers(c, 1, rrgid, userInfo);

					/// Log person
					session.setAttribute("user", login[1]);
					session.setAttribute("uid", uid);
				}
//				dataProvider.disconnect();
			}
			else	// User doesn't exists
			{
				int pubid = 0;
				/// Find public id and log as such
				String sql = "SELECT userid FROM credential WHERE login='public'";
				PreparedStatement st = c.prepareStatement(sql);
				ResultSet rs = st.executeQuery();
				rs.next();
				pubid = rs.getInt(1);
				
				session.setAttribute("user", "public");
				session.setAttribute("uid", pubid);
			}
			
		}
		catch( Exception e )
		{
			e.printStackTrace();
			uuid = "";
		}
		finally
		{
			try
			{
				if( c != null ) c.close();
			}
			catch( SQLException e ){ e.printStackTrace(); }
		}

		PrintWriter writer = response.getWriter();
		writer.write(uuid);
		writer.close();
		request.getInputStream().close();

	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException
	{
		/// Check if user is logged in
		HttpSession session = request.getSession(false);
		if( session == null )
			return;

		int uid = (Integer) session.getAttribute("uid");
		if( uid == 0 )
			return;

		/// TODO: From UUID, check metadata attribute "secure" and redirect to specific url for direct log in
		/// Manage and keep different case number
		String uuid = request.getParameter("uuid");
		String email = request.getParameter("email");
		String role = request.getParameter("role");

		/// Keeping creation log
		BufferedWriter log = LogUtils.getLog("directAccess.log");
		Date date = new Date();
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		String datestring = dateFormat.format(date);
		log.write("["+datestring+"] Direct link creation for user: "+uid+" for access at: "+uuid+" with email: "+email+ " ("+role+")");
		log.newLine();
		log.flush();
		log.close();
		
		/// Encrypt nodeuuid email role
		String output = "";
		try
		{
			String data = uuid+" "+email+" "+role;
			Cipher rc4 = Cipher.getInstance("RC4");
			String secretkey = ConfigUtils.get("directkey");
			SecretKeySpec key = new SecretKeySpec(secretkey.getBytes(), "RC4");
			rc4.init(Cipher.ENCRYPT_MODE, key);
			byte[] clear = rc4.update(data.getBytes());
			output = bytesToHex(clear);
		}
		catch( NoSuchAlgorithmException e )
		{
			e.printStackTrace();
		}
		catch( NoSuchPaddingException e )
		{
			e.printStackTrace();
		}
		catch( InvalidKeyException e )
		{
			e.printStackTrace();
		}

		/// Return encrypted data
		PrintWriter writer = response.getWriter();
		writer.write(output);
		writer.close();
		request.getInputStream().close();

	}

	final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
	public static String bytesToHex(byte[] bytes) {
	    char[] hexChars = new char[bytes.length * 2];
	    for ( int j = 0; j < bytes.length; j++ ) {
	        int v = bytes[j] & 0xFF;
	        hexChars[j * 2] = hexArray[v >>> 4];
	        hexChars[j * 2 + 1] = hexArray[v & 0x0F];
	    }
	    return new String(hexChars);
	}

	public static byte[] hexStringToByteArray(String s) {
    int len = s.length();
    byte[] data = new byte[len / 2];
    for (int i = 0; i < len; i += 2) {
        data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                             + Character.digit(s.charAt(i+1), 16));
    }
    return data;
	}
}

