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

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
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
	Credential credential;
	int userId;
	int groupId = -1;
	HttpSession session;
	ArrayList<String> ourIPs = new ArrayList<String>();

	@Override
	public void init( ServletConfig config ) throws ServletException
	{
		super.init(config);
		/// List possible local address
		try
		{
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
			SecretKeySpec key = new SecretKeySpec("testkey".getBytes(), "RC4");
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

		String[] splitData = output.split(" ");
		String uuid = "";
		String email = splitData[1];
		String role = splitData[2];
		/// log person with associated email
		try
		{
			session = request.getSession(true);
			dataProvider = SqlUtils.initProvider(getServletContext(), logger);
			String[] login = dataProvider.logViaEmail(email);

			if( login != null )
			{
				/// Init DB connection
				DataProvider dataProvider = null;
				try
				{
					dataProvider = SqlUtils.initProvider(getServletContext(), logger);
				}
				catch( Exception e1 )
				{
					e1.printStackTrace();
				}
				if( dataProvider == null )
					return;


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
					int rrgid = dataProvider.getRoleByNode(1, uuid, role);

					/// Put person in specified group
					String userInfo = "<users><user id='"+uid+"'></users>";
					dataProvider.postRRGUsers(1, rrgid, userInfo);

					/// Log person
					session.setAttribute("user", login[1]);
					session.setAttribute("uid", uid);
					uuid = splitData[0];
				}

				dataProvider.disconnect();
			}
		}
		catch( Exception e )
		{
			e.printStackTrace();
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

		String uuid = request.getParameter("uuid");
		String email = request.getParameter("email");
		String role = request.getParameter("role");

		/// Encrypt nodeuuid email role
		String output = "";
		try
		{
			String data = uuid+" "+email+" "+role;
			Cipher rc4 = Cipher.getInstance("RC4");
			SecretKeySpec key = new SecretKeySpec("testkey".getBytes(), "RC4");
			rc4.init(Cipher.ENCRYPT_MODE, key);
			byte[] clear = rc4.update(data.getBytes());
	    output = bytesToHex(clear);

			System.out.println(output);
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

