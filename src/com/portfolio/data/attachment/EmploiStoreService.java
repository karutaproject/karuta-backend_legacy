/* =======================================================
	Copyright 2019 - ePortfolium - Licensed under the
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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.portfolio.data.utils.ConfigUtils;

public class EmploiStoreService extends HttpServlet
{
	private static final long serialVersionUID = -5389232495090560087L;

	static final Logger logger = LoggerFactory.getLogger(EmploiStoreService.class);
	/**
	 * 
	 */

	public void init( ServletConfig config ) throws ServletException
	{
		super.init(config);
		System.setProperty("https.protocols", "TLSv1,TLSv1.1,TLSv1.2");
	}
	
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
	{
		/// Only people from our system can query
		HttpSession session = request.getSession(false);
		if( session == null || session.getAttribute("uid") == null )
		{
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			PrintWriter out;
			try
			{
				out = response.getWriter();
				out.write("403");
				out.close();
			}
			catch( IOException e )
			{
				e.printStackTrace();
			}
			return;
		}

		//// Login to service
		String serviceURL = ConfigUtils.get("ROMEServiceURL");
		String clientid = ConfigUtils.get("ROMEclientid");
		String clientsecret = ConfigUtils.get("ROMEclientsecret");
		String scopestr = ConfigUtils.get("ROMEscope");
		
		String scope = String.format("application_%s%%20%s", clientid, scopestr);
		String body = String.format("grant_type=client_credentials&client_id=%s&client_secret=%s&scope=%s", clientid, clientsecret, scope);
		
		try
		{
			URL urlConn = new URL(serviceURL);
			HttpURLConnection connection = (HttpURLConnection) urlConn.openConnection();
			connection.setDoOutput(true);
			connection.setUseCaches(false);
			connection.setInstanceFollowRedirects(false);
			connection.setRequestMethod("POST");
			connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			
			/// Send login information
			ByteArrayInputStream bais = new ByteArrayInputStream(body.getBytes());
			OutputStream outputData = connection.getOutputStream();
			int transferred = IOUtils.copy(bais, outputData);
			if(  transferred == body.length() )
				logger.debug("Send: Complete");
			else
				logger.error("Send mismatch: "+transferred+" != "+body.length());

			/// Read answer
			int code = connection.getResponseCode();
			String msg = connection.getResponseMessage();
			if( code != HttpURLConnection.HTTP_OK )
				logger.error("Couldn't log: "+msg);
			else
			{
				logger.debug("Code: ("+code+") msg: "+msg);
			}

			StringBuilder logininfo = new StringBuilder();
			String line = "";
			InputStream objReturn = connection.getInputStream();
			BufferedReader breader = new BufferedReader(new InputStreamReader(objReturn,"UTF-8"));
			while ((line = breader.readLine()) != null) {
				logininfo.append(line);
			}
			connection.disconnect();
			
			/// Can't be bothered to parse json
			String tokenregexp = "access_token\":\"([^\"]*)";
			Pattern ptoken = Pattern.compile(tokenregexp);
			Matcher pmatcher = ptoken.matcher(logininfo.toString());
			String access_token = "";
			if (pmatcher.find())
			{
				access_token = pmatcher.group(1);
			}
			System.out.println("Current token:"+ access_token);
			
			///// Send wanted query
			String pathinfo = request.getPathInfo();
			String query = request.getQueryString();
			if( "/".equals(pathinfo) || pathinfo == null )
			{
				pathinfo = "";
				query = "?" + query;
			}
			else
				query = "";
			
			String repoURL = ConfigUtils.get("ROMERepoURL");
			String queryURL = String.format("%s%s%s", repoURL, pathinfo, query);
			System.out.println("Query to: "+queryURL);

			urlConn = new URL(queryURL);
			connection = (HttpURLConnection) urlConn.openConnection();
			connection.setRequestMethod("GET");
			connection.setRequestProperty("Authorization", String.format("Bearer %s", access_token));
			// Need some user agent, otherwise return 409
			connection.setRequestProperty("User-Agent", "Error 409 without user-agent");
//			connection.connect();

			code = connection.getResponseCode();
			msg = connection.getResponseMessage();

			if( code != HttpURLConnection.HTTP_OK )
			{
				logger.error("Couldn't get file: "+msg);
				response.setStatus(code);
				PrintWriter writer = response.getWriter();
				writer.write(msg);
				writer.close();
			}
			else
			{
				OutputStream output = response.getOutputStream();
				/// Send data to report daemon
				InputStream inputData = connection.getInputStream();
				IOUtils.copy(inputData, output);
				inputData.close();
				output.close();
			}

			connection.disconnect();
		}
		catch( MalformedURLException e )
		{
			e.printStackTrace();
		}
		catch( IOException e )
		{
			e.printStackTrace();
		}
		catch( Exception e )
		{
			e.printStackTrace();
		}

		response.setStatus(HttpServletResponse.SC_OK);
		return;
	}
}

