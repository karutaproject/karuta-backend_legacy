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

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import com.portfolio.data.provider.DataProvider;
import com.portfolio.data.utils.ConfigUtils;
import com.portfolio.data.utils.DomUtils;
import com.portfolio.data.utils.MailUtils;
import com.portfolio.security.Credential;

public class MailService  extends HttpServlet {

	/**
	 *
	 */
	final Logger logger = LoggerFactory.getLogger(MailService.class);
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
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
	{
		/// Check if user is logged in
		HttpSession session = request.getSession(false);
		if( session == null )
			return;

		int uid = (Integer) session.getAttribute("uid");
		if( uid == 0 )
			return;

		logger.trace("Sending mail for user: "+uid);

		String sender = "";
		String recipient = "";
		String recipient_cc = "";
		String recipient_bcc = "";
		String subject = "";
		String message = "";

		StringWriter writer = new StringWriter();
		try
		{
			IOUtils.copy(request.getInputStream(), writer);
			String data = writer.toString();
			Document doc = DomUtils.xmlString2Document(data, new StringBuffer());
			NodeList senderNode = doc.getElementsByTagName("sender");
			NodeList recipientNode = doc.getElementsByTagName("recipient");
			NodeList recipient_ccNode = doc.getElementsByTagName("recipient_cc");
			NodeList recipient_bccNode = doc.getElementsByTagName("recipient_bcc");
			NodeList subjectNode = doc.getElementsByTagName("subject");
			NodeList messageNode = doc.getElementsByTagName("message");

			/// From
			if( senderNode.getLength() > 0 )
				sender = senderNode.item(0).getFirstChild().getNodeValue();
			/// Recipient
			if( recipientNode.getLength() > 0 )
				recipient = recipientNode.item(0).getFirstChild().getNodeValue();
			/// CC
			if( recipient_ccNode.getLength() > 0 )
				recipient_cc = recipient_ccNode.item(0).getFirstChild().getNodeValue();
			/// BCC
			if( recipient_bccNode.getLength() > 0 )
				recipient_bcc = recipient_bccNode.item(0).getFirstChild().getNodeValue();
			/// Subject
			if( subjectNode.getLength() > 0 )
				subject = subjectNode.item(0).getFirstChild().getNodeValue();
			/// Message
			if( messageNode.getLength() > 0 )
				message = messageNode.item(0).getFirstChild().getNodeValue();
		}
		catch( Exception e )
		{
			e.printStackTrace();
			logger.error(e.getMessage());
			return;
		}

		ServletConfig config = getServletConfig();
		String notification = ConfigUtils.get("notification");
		logger.trace("Message via: "+notification);

		try
		{
			if( "email".equals(notification) )
			{
//				System.out.println("SENDING: "+message);
				MailUtils.postMail(config, recipient, sender, subject, message, logger);
				logger.trace("Mail to: "+recipient+"; from: "+sender+" by uid: "+uid);
			}
			else if( "sakai".equals(notification) )
			{
				/// Recipient is username list rather than email address
				String[] recip = recipient.split(",");
				String[] var = getSakaiTicket();

				for( int i=0; i<recip.length; ++i )
				{
					String user = recip[i];
					int status = sendMessage(var, user, message);
					logger.trace("Message sent to: "+user+" -> "+status);
				}
			}
			else
			{
				logger.error("Unknown notification method: "+notification);
			}
		}
		catch( Exception e )
		{
			e.printStackTrace();
			logger.error(e.getMessage());
		}
		try
		{
			response.getOutputStream().close();
			request.getInputStream().close();
		}
		catch( Exception e )
		{
			e.printStackTrace();
			logger.error(e.getMessage());
		}
	}

	int sendMessage( String auth[], String user, String message )
	{
		int ret = 500;

		try
		{
			String urlParameters = "notification=\""+message+"\"&_sessionId="+auth[0];

			/// Send for this user
			String url = ConfigUtils.get("sakaiInterface");
			URL urlTicker = new URL(url+user);

			HttpURLConnection connect = (HttpURLConnection) urlTicker.openConnection();
			connect.setDoOutput(true);
			connect.setDoInput(true);
			connect.setInstanceFollowRedirects(false);
			connect.setRequestMethod("POST");
			connect.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			connect.setRequestProperty("charset", "utf-8");
			connect.setRequestProperty("Content-Length", "" + Integer.toString(urlParameters.getBytes().length));
			connect.setUseCaches(false);
			connect.setRequestProperty("Cookie", auth[1]);
			connect.connect();

			DataOutputStream wr = new DataOutputStream(connect.getOutputStream());
			wr.writeBytes(urlParameters);
			wr.flush();
			wr.close();

			ret = connect.getResponseCode();

			logger.trace("Notification: "+ret);
		}
		catch( Exception e )
		{
			e.printStackTrace();
			logger.error(e.getMessage());
		}

		return ret;
	}

	String[] getSakaiTicket()
	{
		String[] ret = {"",""};
		try
		{
			/// Configurable?
			String username = ConfigUtils.get("sakaiUsername");
			String password = ConfigUtils.get("sakaiPassword");
			String urlDirectSession = ConfigUtils.get("sakaiDirectSessionUrl");

			String urlParameters = "_username="+username+"&_password="+password;

			/// Will have to use some context config
			URL urlTicker = new URL(urlDirectSession);

			HttpURLConnection connect = (HttpURLConnection) urlTicker.openConnection();
			connect.setDoOutput(true);
			connect.setDoInput(true);
			connect.setInstanceFollowRedirects(false);
			connect.setRequestMethod("POST");
			connect.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			connect.setRequestProperty("charset", "utf-8");
			connect.setRequestProperty("Content-Length", "" + Integer.toString(urlParameters.getBytes().length));
			connect.setUseCaches(false);
			connect.connect();

			DataOutputStream wr = new DataOutputStream(connect.getOutputStream());
			wr.writeBytes(urlParameters);
			wr.flush();
			wr.close();

			StringBuilder readTicket = new StringBuilder();
			BufferedReader rd = new BufferedReader(new InputStreamReader(connect.getInputStream(), "UTF-8"));
			char buffer[] = new char[1024];
			int offset = 0;
			int read = 0;
			do
			{
				read = rd.read(buffer, offset, 1024);
				offset += read;
				readTicket.append(buffer);
			}while( read == 1024 );
			rd.close();

			ret[1] = connect.getHeaderField("Set-Cookie");

			connect.disconnect();

			ret[0] = readTicket.toString();
		}
		catch( Exception e )
		{
			logger.error(e.getMessage());
			e.printStackTrace();
		}

		return ret;
	}

}

