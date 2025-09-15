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

package com.eportfolium.karuta.data.attachment;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.fileupload2.core.DiskFileItemFactory;
import org.apache.commons.fileupload2.core.FileItem;
import org.apache.commons.fileupload2.jakarta.servlet6.JakartaServletFileUpload;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.eportfolium.karuta.data.utils.ConfigUtils;

import au.com.bytecode.opencsv.CSVReader;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

public class ConvertCSV extends HttpServlet {

	/**
	 *
	 */
	private static final long serialVersionUID = 9188067506635747901L;

	private static final Logger logger = LoggerFactory.getLogger(ConvertCSV.class);

	boolean hasNodeReadRight = false;
	boolean hasNodeWriteRight = false;
	int userId;
	int groupId = -1;
	String user = "";
	String context = "";
	HttpSession session;

	private String csvsep;

	public void initialize(HttpServletRequest httpServletRequest) {
		csvsep = ConfigUtils.getInstance().getProperty("csv_separator", ",");
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException {
		final var isMultipart = JakartaServletFileUpload.isMultipartContent(request);
		if (!isMultipart) {
			try {
				request.getInputStream().close();
				response.setStatus(417);
				response.getWriter().close();
			} catch (final IOException e) {
				logger.error("Not expected input", e);
				throw new ServletException(e);
			}
			return;
		}

		initialize(request);
		response.setContentType("application/json");
		response.setCharacterEncoding(StandardCharsets.UTF_8.toString());

		final var csvseparator = csvsep.charAt(0); // Otherwise just fetch the first character defined

		JSONObject data = null;
		try {
			final var factory = DiskFileItemFactory.builder().get();
			final var upload = new JakartaServletFileUpload(factory);
			final List<FileItem> items = upload.parseRequest(request);

			//			List<String[]> meta = new ArrayList<String[]>();
			//			List<List<String[]>> linesData = new ArrayList<List<String[]>>();

			for (final FileItem item : items) {
				if (!item.isFormField()) {
					// Process form file field (input type="file").
					final var fieldname = item.getFieldName();
					if ("uploadfile".equals(fieldname)) // name="uploadfile"
					{
						final var isr = new InputStreamReader(item.getInputStream());
						final var rawdata = IOUtils.toByteArray(isr, StandardCharsets.UTF_8);
						var byteReader = new InputStreamReader(new ByteArrayInputStream(rawdata));

						// Different tries depending on locale
						data = ReadCSV(byteReader, ',');
						if (data.length() == 0) {
							// Closing the CSVReader also close the byteReader, no choice
							byteReader = new InputStreamReader(new ByteArrayInputStream(rawdata));
							data = ReadCSV(byteReader, ';');
						}
						if (data.length() == 0) {
							byteReader = new InputStreamReader(new ByteArrayInputStream(rawdata));
							data = ReadCSV(byteReader, csvseparator);
						}

						byteReader.close();
					}
				} /* else {
					// Process regular form field (input type="text|radio|checkbox|etc", select, etc).
					} */
			}
		} catch (final Exception e) {
			logger.error("Exception intercepted", e);
			//TODO something is missing
		}

		PrintWriter out = null;
		try {
			out = response.getWriter();
			out.print(data);
			request.getInputStream().close();
		} catch (final IOException e) {
			logger.error("Exception intercepted", e);
			//TODO something is missing
		} finally {
			if (out != null) {
				out.flush();
				out.close();
			}
		}

	}

	protected JSONObject ReadCSV(InputStreamReader isr, char separator) throws IOException {
		final var data = new JSONObject();
		final var reader = new CSVReader(isr, separator);
		String[] headerLine;
		String[] dataLine;

		headerLine = reader.readNext();
		if (headerLine == null || headerLine.length == 1) // Need at least 2 columns
		{
			reader.close();
			return data;
		}

		dataLine = reader.readNext();
		if (dataLine == null) {
			reader.close();
			return data;
		}

		for (var i = 0; i < headerLine.length; ++i) {
			data.put(headerLine[i], dataLine[i]);
		}

		headerLine = reader.readNext();
		if (headerLine == null) {
			reader.close();
			return data;
		}

		final var lines = new JSONArray();
		while ((dataLine = reader.readNext()) != null) {
			final var lineInfo = new JSONObject();
			for (var i = 0; i < headerLine.length; ++i) {
				lineInfo.put(headerLine[i], dataLine[i]);
			}
			lines.put(lineInfo);
		}

		data.put("lines", lines);

		reader.close(); // Also close the byteReader
		return data;
	}
}