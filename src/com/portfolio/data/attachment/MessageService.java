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
import java.net.HttpURLConnection;
import java.net.URL;

import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.portfolio.data.provider.DataProvider;
import com.portfolio.data.utils.ConfigUtils;
import com.portfolio.data.utils.MailUtils;
import com.portfolio.security.Credential;

public class MessageService  extends HttpServlet {

	/**
	 *
	 */
	final Logger logger = LoggerFactory.getLogger(MessageService.class);
	private static final long serialVersionUID = 9188067506635747901L;

	DataProvider dataProvider;
	boolean hasNodeReadRight = false;
	boolean hasNodeWriteRight = false;
	Credential credential;
	int userId;
	int groupId = -1;
	HttpSession session;

	public void initialize(HttpServletRequest httpServletRequest)
	{
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
	{
		/// Check if user has an account


		/// From
		/// Recipient
		String recipient = request.getParameter("recipient");
		/// CC
		String recipient_cc = request.getParameter("recipient_cc");
		/// BCC
		String recipient_bcc = request.getParameter("recipient_bcc");
		/// Subject
		String subject = request.getParameter("subject");
		/// Message
		String message = request.getParameter("message");

		ServletConfig config = getServletConfig();
		String notification = ConfigUtils.get("notification");

		logger.debug("Message to: "+notification);
		if( "email".equals(notification) )
		{
			try
			{
				MailUtils.postMail(config, recipient, recipient_cc, subject, message, logger);
			}
			catch( Exception e )
			{
				logger.error(e.getMessage());
			}
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
				logger.debug("Message sent to: "+user+" -> "+status);
			}
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

			logger.debug("Notification: "+ret);
		}
		catch( Exception e )
		{
			e.printStackTrace();
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
			e.printStackTrace();
		}

		return ret;
	}
}

