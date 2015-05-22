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

import java.io.IOException;
import java.io.InputStream;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.portfolio.data.provider.DataProvider;

/**
 * Class supporting lti integration
 * Used basiclti-util-2.1.0 (from sakai 2.9.2) for the oauth dependencies
 * @author chmaurer
 *
 */
public class LTIServletImport extends HttpServlet {

	private static final long serialVersionUID = -5793392467087229614L;

	final Logger logger = LoggerFactory.getLogger(LTIServletImport.class);

	ServletConfig sc;
	DataProvider dataProvider;

	String cookie = "wad";
	//boolean log = true;
	//boolean trace = true;

	@Override
  public void init()
	{
	  ServletContext application = getServletConfig().getServletContext();
	  try
	  {
	  }
	  catch( Exception e ){ e.printStackTrace(); }
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		String url = request.getParameter("url");
		HttpSession session = request.getSession(true);
		String sakai_session = (String) session.getAttribute("sakai_session");
		String sakai_server = (String) session.getAttribute("sakai_server");	// Base server http://localhost:9090
		System.out.println("IS IT OK: "+sakai_session);

		HttpClient client = new HttpClient();

		//// Connection to Sakai
		GetMethod get = new GetMethod(sakai_server+"/"+url);
		Header header = new Header();
		header.setName("JSESSIONID");
		header.setValue(sakai_session);
		get.setRequestHeader(header);

		int status = client.executeMethod(get);
		if (status != HttpStatus.SC_OK) {
			System.err.println("Method failed: " + get.getStatusLine());
		}

		InputStream retrieve = get.getResponseBodyAsStream();
		ServletOutputStream output = response.getOutputStream();

		IOUtils.copy(retrieve, output);
		IOUtils.closeQuietly(output);

		output.flush();
		output.close();
		retrieve.close();
		get.releaseConnection();

		try {
		}
		catch (Exception e)
		{
		}
		finally {
		}
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		HttpSession session = request.getSession(true);
		String sakai_session = (String) session.getAttribute("sakai_session");
		String sakai_server = (String) session.getAttribute("ext_sakai_server");	// Base server http://localhost:9090
		System.out.println("IS IT OK: "+sakai_session);

		HttpClient client = new HttpClient();

		//// Connection to Sakai
		GetMethod get = new GetMethod("");
		Header header = new Header();
		header.setName("JSESSIONID");
		header.setValue(sakai_session);
		get.setRequestHeader(header);

		int status = client.executeMethod(get);
		if (status != HttpStatus.SC_OK) {
			System.err.println("Method failed: " + get.getStatusLine());
		}

		InputStream retrieve = get.getResponseBodyAsStream();
		ServletOutputStream output = response.getOutputStream();

		IOUtils.copy(retrieve, output);
		IOUtils.closeQuietly(output);

		output.flush();
		output.close();
		retrieve.close();
		get.releaseConnection();

		try {
		}
		catch (Exception e)
		{
		}
		finally {
		}

	}


}
