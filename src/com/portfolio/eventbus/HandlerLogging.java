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

package com.portfolio.eventbus;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Enumeration;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.portfolio.data.provider.DataProvider;


public class HandlerLogging implements KEventHandler
{
	HttpServletRequest httpServletRequest;
	HttpSession session;
	int userId;
	int groupId;
	DataProvider dataProvider;

	public HandlerLogging( HttpServletRequest request, DataProvider provider )
	{
		httpServletRequest = request;
		dataProvider = provider;

		this.session = request.getSession(true);
		Integer val = (Integer) session.getAttribute("uid");
		if( val != null )
			this.userId = val;
		val = (Integer) session.getAttribute("gid");
		if( val != null )
			this.groupId = val;
	}

	@Override
	public boolean processEvent( KEvent event )
	{
		try
		{
			switch( event.eventType )
			{
				case LOGIN:
					String httpHeaders = "";
					Enumeration<String> headerNames = httpServletRequest.getHeaderNames();
					while(headerNames.hasMoreElements())
					{
						String headerName = headerNames.nextElement();
						httpHeaders += headerName+": "+httpServletRequest.getHeader(headerName)+"\n";
					}
					String url = httpServletRequest.getRequestURL().toString();
					if(httpServletRequest.getQueryString()!=null) url += "?" + httpServletRequest.getQueryString().toString();
					dataProvider.writeLog(url, httpServletRequest.getMethod().toString(),
							httpHeaders, event.inputData, event.doc.toString(), event.status);

					//// TODO Devrait aussi Žcrire une partie dans les fichiers
					System.out.println("LOGIN EVENT");

					break;

				default:
					break;
			}
		}
		catch(Exception ex)
		{
			/*
			ex.printStackTrace();
			logRestRequest(httpServletRequest, "", ex.getMessage()+"\n\n"+javaUtils.getCompleteStackTrace(ex), Status.INTERNAL_SERVER_ERROR.getStatusCode());
			throw new RestWebApplicationException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
			//*/
		}
		finally
		{
		}

		return true;
	}

	Document parseString( String data ) throws UnsupportedEncodingException, SAXException, IOException, ParserConfigurationException
	{
		DocumentBuilderFactory documentBuilderFactory =DocumentBuilderFactory.newInstance();
		DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
		Document doc = documentBuilder.parse(new ByteArrayInputStream(data.getBytes("UTF-8")));
		doc.setXmlStandalone(true);

		return doc;
	}

}
