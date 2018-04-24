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
import java.io.ByteArrayInputStream;
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
import java.util.UUID;

import javax.activation.MimeType;
import javax.activation.MimeTypeParseException;
import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

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
		if( val == null )
		{
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			PrintWriter writer = response.getWriter();
			writer.write("No data sent");
			writer.close();
			request.getInputStream().close();
			return;
		}
		/// Decrypt data
		Cipher rc4;
		String output="";

		try
		{
			byte[] data = stringToHex(val.toCharArray());
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
		catch( ArrayIndexOutOfBoundsException e )
		{
			e.printStackTrace();
		}

		/// Keeping access log
		Date date = new Date();
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		BufferedWriter log = LogUtils.getLog("directAccess.log");
		String datestring = dateFormat.format(date);
		
		/// Check case we are in, act accordingly
		
		String[] splitData = output.split(" ");
		String uuid = splitData[0];
		String email = splitData[1];
		String role = splitData[2];
		String showtorole = splitData[6];
		int level = Integer.parseInt(splitData[3]);
		
		if( "unlimited".equals(splitData[4]) )
		{
			// Log access
			log.write("["+datestring+"] Direct link access by: "+email+ " ("+role+") for uuid: "+uuid+" level: "+level+" duration: "+splitData[4]);
		}
		else
		{
			int duration = Integer.parseInt(splitData[4]);	// In hours (minimum 1h)
			long endtime = 0;
			if( splitData.length >= 5 )
				endtime = Long.parseLong(splitData[5]);
			
			/// Check if link is still valid
			long currtime = date.getTime()/1000;
			if( currtime > endtime )
			{
				log.write("["+datestring+"] Old link access by: "+email+ " ("+role+") for uuid: "+uuid+" level: "+level+" duration: "+duration+" ends at: "+endtime);
				log.newLine();
				log.flush();
				log.close();
				response.setStatus(403);
				response.getWriter().close();
				request.getInputStream().close();
				return;
			}
			else
			{
				// Log connection attempt. email, uuid, role access, hour, ip, date
				log.write("["+datestring+"] Direct link access by: "+email+ " ("+role+") for uuid: "+uuid+" level: "+level+" duration: "+duration+" ends at: "+endtime);
				log.newLine();
				log.flush();
				log.close();
			}
		}

		
		/// log in person with associated email
		Connection c = null;
		try
		{
			/// Init DB connection
			c = SqlUtils.getConnection(getServletContext());
			session = request.getSession(true);
			boolean isLogged = false;
			Integer uidcheck = (Integer) session.getAttribute("uid");
			int uid = 0;
			if( uidcheck != null )
			{
				uid = uidcheck;
				isLogged = true;
			}
			
			String[] login = null;
			switch( level )
			{
				case 4:	// Just log as public (world)
					if( !isLogged )
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
					break;
					
				case 3:	// Create account for this person
					if( "2world".equals(showtorole) )
					{
						/// Create bogus email
						String username = UUID.randomUUID().toString();
						String domainname = UUID.randomUUID().toString();
						String tld = UUID.randomUUID().toString();
						
						email = username+"@"+domainname+"."+tld;
					}
					
					/// Check if user exist by logging in
					login = dataProvider.logViaEmail(c, email);
					if( login != null )
					{
						uid = Integer.parseInt(login[2]);
						session.setAttribute("user", login[1]);
						session.setAttribute("uid", uid);
						session.setAttribute("source", "public.htm");
						
						String referer = (String) request.getHeader("referer");	// Can be spoofed
//						System.out.println("Login from source: "+referer);
						isLogged = true;
					}
					
					else if( !isLogged )
					{
						login = new String[] {"0","0","0"};
						try
						{
						login[2] = dataProvider.createUser(c, email, email);
						uid = Integer.parseInt(login[2]);
						}
						catch(Exception e){}	//
					}

				case 2:	// Share portfolio 
					if( uid > 0 )
					{
						/// Find group for this node
						int rrgid = dataProvider.getRoleByNode(c, 1, uuid, role);

						/// Put person in specified group
						String userInfo = "<users><user id='"+uid+"' /></users>";
						dataProvider.postRRGUsers(c, 1, rrgid, userInfo);

					}
//					dataProvider.disconnect();

				case 1:	// Temp login
					if( !isLogged )
					{
						login = dataProvider.logViaEmail(c, email);
						uid = Integer.parseInt(login[2]);
						/// Log person
						session.setAttribute("user", login[1]);
						session.setAttribute("uid", uid);
						session.setAttribute("source", "public.htm");
						
						String referer = (String) request.getHeader("referer");	// Can be spoofed
//						System.out.println("Login from source: "+referer);
					}
					break;
					
				case 0:	// Just ask for login
					break;
			}
			

			if( login != null )	// If account exists
			{

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
			}
			else	// User doesn't exists
			{
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
		String level = request.getParameter("l");
		String duration = request.getParameter("d");
		String type = request.getParameter("type");
		String sharerole = request.getParameter("sharerole");
		String showtorole = request.getParameter("showtorole");
		Connection c = null;
		Document doc = null;
		
		/// Fetching data to be checked upon
		String nodedata = "";
		try
		{
			c = SqlUtils.getConnection(this.getServletContext());
			Object retdata = dataProvider.getNode(c, new MimeType("text/xml"), uuid, false, 1, 0, "", 1);
			if( retdata == null )
			{
				response.setStatus(HttpServletResponse.SC_NOT_FOUND);
				PrintWriter writer = response.getWriter();
				writer.write("Node not found");
				writer.close();
				request.getInputStream().close();
				return;
			}
			nodedata = retdata.toString();

//			System.out.println("DIRECT FETCH NODE: "+nodedata);
			DocumentBuilderFactory documentBuilderFactory =DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
			doc = documentBuilder.parse(new ByteArrayInputStream(nodedata.getBytes("UTF-8")));
		}
		catch( Exception e1 )
		{
			e1.printStackTrace();
		}
		finally
		{
			if(c != null) try
			{
				c.close();
			}
			catch( SQLException e )
			{
				e.printStackTrace();
			}
		}
		NodeList metadata = doc.getElementsByTagName("metadata-wad");
		String[] values = null;
		if( metadata.getLength() > 0 )
		{
			Node meta = metadata.item(0);
			/// Authorized role to create share requests
			Node nodeshareroles = meta.getAttributes().getNamedItem("shareroles");
			
			if( nodeshareroles == null )
			{
				response.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED);
				PrintWriter writer = response.getWriter();
				writer.write("Missing shareroles attribute");
				writer.close();
				request.getInputStream().close();
				return;
			}

			String shareroleval = nodeshareroles.getTextContent();
			String multiplex[] = shareroleval.split(";");
			/// Find matching line
			String find_pattern = "";
			if("email".equals(type) )
				find_pattern = "^"+sharerole+","+role+","+email+",.*";
			else if( "showtorole".equals(type) )
				find_pattern = "^"+sharerole+","+role+","+showtorole+",.*";
			
			int f=0;
			for( f=0; f< multiplex.length; f++ )
			{
				if( multiplex[f].matches(find_pattern) )
					break;
			}
			if( f >= multiplex.length )
			{
				response.setStatus(HttpServletResponse.SC_FORBIDDEN);
				PrintWriter writer = response.getWriter();
				writer.write("No matching rule");
				writer.close();
				request.getInputStream().close();
				return;
			}

			values = multiplex[f].split(",");
//			System.out.println("VALUES: "+shareroleval);
		}
		
		// Parameters checking
		boolean isok = false;
		// shareroles format:
		/*
		  0: rôle,
		  1: rôle destinataire,
		  2: rôles et/ou courriels,
		  3: niveau (0-4),
		  4: durée de vie du lien (en heures),
		  5: libellé du bouton@fr,
		  6: condition (optionel)
		 **/
		String checkStatus = "Invalid: ";
		if( "email".equals(type) )
		{
			if( values[1].contains(role) && values[2].contains(email) )
			{
				isok = true;
			}
			else
			{
				if( !role.equals(values[1]) )
					checkStatus += "Role doesn't match. ";
				if( !values[2].contains(email) )
					checkStatus += "Email doesn't match.";
			}
		}
		else if( "showtorole".equals(type) )
		{
			if( values[1].contains(role) && values[2].contains(showtorole) )
			{
				isok = true;
			}
			else
			{
				if( !role.equals(values[1]) )
					checkStatus += "Role doesn't match. ";
				if( !showtorole.equals(values[2]) )
					checkStatus += "showtorole doesn't match.";
			}
		}
		else
		{
			checkStatus += "type missing or invalid.";
		}
		
		if( !isok )
		{
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			PrintWriter writer = response.getWriter();
			writer.write(checkStatus);
			writer.close();
			request.getInputStream().close();
			return;
		}
		
		if(duration == null)
			duration = "72";	// Default 72h
		String endtimeString = "";
		if( "unlimited".equals(duration) )
		{
			endtimeString = duration;
		}
		else
		{
			int durationInt = Integer.parseInt(duration);
			if( durationInt < 1 )
				durationInt = 1;
			else if( durationInt > 24*30 )	// 720 hours, 30 days
				durationInt = 24*30;
			Date current = new Date();
			long endtime = current.getTime()/1000 + durationInt*3600;	// Number of seconds
			endtimeString = Long.toString(endtime);
		}

		/// Keeping creation log
		BufferedWriter log = LogUtils.getLog("directAccess.log");
		Date date = new Date();
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		String datestring = dateFormat.format(date);
		log.write("["+datestring+"] Direct link creation for user: "+uid+" for access at: "+uuid+" with email: "+email+ " ("+role+"). Access level: '"+level+"' for duraction: '"+duration+"' ending at: '"+endtimeString+"'");
		log.newLine();
		log.flush();
		log.close();
		
		/// Encrypt nodeuuid email role
		String output = "";
		try
		{
			String data = uuid+" "+email+" "+role+" "+level+" "+duration+" "+endtimeString+" "+showtorole;
			Cipher rc4 = Cipher.getInstance("RC4");
			String secretkey = ConfigUtils.get("directkey");
			SecretKeySpec key = new SecretKeySpec(secretkey.getBytes(), "RC4");
			rc4.init(Cipher.ENCRYPT_MODE, key);
			byte[] clear = rc4.update(data.getBytes());
			output = hexToString(clear);
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
//	System.out.println("DIRECT FETCH NODE: "+output);

	}

	final protected static char[] resolveHex = "0123456789ABCDEF".toCharArray();
	public static String hexToString(byte[] bytes) {
			StringBuilder hexchars = new StringBuilder(bytes.length * 2);
			for ( int j = 0; j < bytes.length; j++ ) {
				hexchars.append(resolveHex[(bytes[j] & 0xFF) >>> 4]);
				hexchars.append(resolveHex[(bytes[j] & 0xFF) & 0x0F]);
			}
			return hexchars.toString();
	}

	// speed vs space
	final protected static char[] resolveChar = {
			0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
			0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
			0,0,0,0,0,0,0,0,0,1,2,3,4,5,6,7,8,9,0,0,
			0,0,0,0,0,10,11,12,13,14,15,0,0,0,0,0,0,0,0,0,
			0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,10,11,12,
			13,14,15,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
			0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
	public static byte[] stringToHex(char[] s) {
		int len = s.length>>1;
		byte[] data = new byte[len];
		for (int i = 0; i < len; ++i) {
			data[i] = (byte) (resolveChar[s[i<<1]] << 4 | resolveChar[s[(i<<1)+1]]);
		}
		return data;
	}
}

