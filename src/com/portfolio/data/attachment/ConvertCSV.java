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
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.json.JSONArray;
import org.json.JSONObject;

import au.com.bytecode.opencsv.CSVReader;

import com.portfolio.data.provider.DataProvider;
import com.portfolio.security.Credential;

public class ConvertCSV  extends HttpServlet {

	/**
	 *
	 */
	private static final long serialVersionUID = 9188067506635747901L;

	DataProvider dataProvider;
	boolean hasNodeReadRight = false;
	boolean hasNodeWriteRight = false;
	Credential credential;
	int userId;
	int groupId = -1;
	String user = "";
	String context = "";
	HttpSession session;

	public void initialize(HttpServletRequest httpServletRequest)
	{
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
	{
		boolean isMultipart = ServletFileUpload.isMultipartContent(request);
		if( !isMultipart )
		{
			try
			{
				request.getInputStream().close();
				response.setStatus(417);
				response.getWriter().close();
			}
			catch( IOException e )
			{
				e.printStackTrace();
			}
			return;
		}

		initialize(request);
		response.setContentType("application/json");

		JSONObject data = new JSONObject();
		try
		{
			DiskFileItemFactory factory = new DiskFileItemFactory();
			ServletFileUpload upload = new ServletFileUpload(factory);
			List<FileItem> items = upload.parseRequest(request);
			Iterator<FileItem> iter = items.iterator();

			List<String[]> meta = new ArrayList<String[]>();
			List<List<String[]>> linesData = new ArrayList<List<String[]>>();

			while( iter.hasNext() )
			{
				FileItem item = iter.next();
				if (item.isFormField())
				{
					// Process regular form field (input type="text|radio|checkbox|etc", select, etc).
				}
				else
				{
					// Process form file field (input type="file").
					String fieldname = item.getFieldName();
					if( "uploadfile".equals(fieldname) )	// name="uploadfile"
					{
						InputStreamReader isr = new InputStreamReader(item.getInputStream());
						CSVReader reader = new CSVReader(isr,';');
						String[] headerLine;
						String[] dataLine;

						headerLine = reader.readNext();
						if( headerLine == null )
							break;

						dataLine = reader.readNext();
						if( dataLine == null )
							break;

						for( int i=0; i<headerLine.length; ++i )
						{
							data.put(headerLine[i], dataLine[i]);
						}

						headerLine = reader.readNext();
						if( headerLine == null )
							break;

						JSONArray lines = new JSONArray();
						while( (dataLine = reader.readNext()) != null )
						{
							JSONObject lineInfo = new JSONObject();
							for( int i=0; i<headerLine.length; ++i )
							{
								lineInfo.put(headerLine[i], dataLine[i]);
							}
							lines.put(lineInfo);
						}

						data.put("lines", lines);

						isr.close();
					}
				}
			}
		}
		catch( Exception e )
		{

		}

		PrintWriter out = null;
		try
		{
			out = response.getWriter();
			out.print(data);
		}
		catch( IOException e )
		{
			e.printStackTrace();
		}
		finally
		{
			if( out != null )
			{
				out.flush();
				out.close();
			}
		}

	}
}

