/* =======================================================
	Copyright 2021 - ePortfolium - Licensed under the
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
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.http.entity.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.eportfolium.karuta.data.provider.ReportHelperProvider;
import com.eportfolium.karuta.data.utils.DomUtils;
import com.eportfolium.karuta.data.utils.LogUtils;
import com.eportfolium.karuta.data.utils.SqlUtils;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ReportHelper extends HttpServlet {
	/**
	 *
	 */
	private static final long serialVersionUID = 7885746223793374448L;

	static final Logger logger = LoggerFactory.getLogger(ReportHelper.class);
	ReportHelperProvider dataProvider = null;

	final String header;
	String servletDir;

	public ReportHelper() {
		header = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<!DOCTYPE vectors [" +
				"<!ENTITY nbsp \"&#xA0;\">" +
				"]>%s";
	}

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		try {
			LogUtils.initDirectory(getServletContext());

			dataProvider = SqlUtils.initProviderHelper();
			final var sc = config.getServletContext();
			servletDir = sc.getRealPath("/");
		} catch (final Exception e) {
			e.printStackTrace();
			logger.error(e.getMessage());
		}

	}

	/// Delete specific vector
	@Override
	protected void doDelete(HttpServletRequest request, HttpServletResponse response) {
		/// Check if user is logged in
		final var session = request.getSession(false);
		if (session == null || session.getAttribute("uid") == null) {
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		final int uid = (Integer) session.getAttribute("uid");
		if (uid == 0) {
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		Connection c = null;
		try {
			final var map = new HashMap<String, String>();

			//// Process input
			// If there's a userid
			final var date = request.getParameter("date");
			if (date != null) {
				final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
				final var d = dateFormat.parse(date);
				map.put("date", dateFormat.format(d));
			}
			// Column parameters
			for (var i = 1; i <= 10; i++) {
				final var key = "a" + i;
				final var value = request.getParameter(key);
				if (value != null) {
					map.put(key, value);
				}
			}

			/// Query
			c = SqlUtils.getConnection();
			map.put("userid", Integer.toString(uid));

			final var value = dataProvider.deleteVector(c, map);

			// Send result
			final OutputStream output = response.getOutputStream();
			output.write(Integer.toString(value).getBytes());
			output.close();

		} catch (final Exception e) {
			e.printStackTrace();
			response.setStatus(500);
		} finally {
			/// Close connections
			try {
				if (c != null) {
					c.close();
					//				request.getReader().close();
					//				response.getWriter().close();
				}
			} catch (final Exception e) {
				e.printStackTrace();
			}
		}

	}

	// Searching
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) {
		/// Check if user is logged in
		final var session = request.getSession(false);
		if (session == null || session.getAttribute("uid") == null) {
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		final int uid = (Integer) session.getAttribute("uid");
		if (uid == 0) {
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		Connection c = null;
		try {
			final var map = new HashMap<String, String>();

			//// Process input
			// If there's a userid
			final var requested_uid_str = request.getParameter("userid");
			if (requested_uid_str != null) {
				final var requested_uid = Integer.parseInt(requested_uid_str);
				if (requested_uid > 0) {
					map.put("userid", requested_uid_str);
				}
			}
			// Column parameters
			for (var i = 1; i <= 10; i++) {
				final var key = "a" + i;
				final var value = request.getParameter(key);
				if (value != null) {
					map.put(key, value);
				}
			}
			/// Query
			c = SqlUtils.getConnection();
			final var vectorValue = dataProvider.getVector(c, uid, map);

			// Send result
			response.setContentType(ContentType.APPLICATION_XML.getMimeType());
			response.setCharacterEncoding(StandardCharsets.UTF_8.toString());
			final OutputStream output = response.getOutputStream();
			output.write(vectorValue.getBytes(StandardCharsets.UTF_8));
			output.close();

		} catch (final Exception e) {
			e.printStackTrace();
			response.setStatus(500);
		} finally {
			/// Close connections
			try {
				if (c != null) {
					c.close();
					//			request.getReader().close();
					//			response.getWriter().close();
				}
			} catch (final Exception e) {
				e.printStackTrace();
			}
		}

	}

	// Write vector
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) {
		/// Check if user is logged in
		final var session = request.getSession(false);
		//		/*
		if (session == null) {
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		final int uid = (Integer) session.getAttribute("uid");
		if (uid == 0) {
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			return;
		}
		//*/

		Connection c = null;
		try {
			final var documentBuilderFactory = DomUtils.newSecureDocumentBuilderFactory();
			documentBuilderFactory.setAttribute("http://apache.org/xml/features/disallow-doctype-decl", false);
			final var documentBuilder = documentBuilderFactory.newDocumentBuilder();

			final var sanitizedXml = String.format(header,
					IOUtils.toString(request.getInputStream(), StandardCharsets.UTF_8));

			logger.error(sanitizedXml);

			final var doc = documentBuilder.parse(new ByteArrayInputStream(sanitizedXml.getBytes()));
			final var vectorNode = doc.getElementsByTagName("vector");
			final var map = new HashMap<String, String>();
			map.put("userid", Integer.toString(uid));
			if (vectorNode.getLength() == 1) {
				final var nodename = "a1?\\d";
				final var namePat = Pattern.compile(nodename);

				var a_node = vectorNode.item(0).getFirstChild();
				while (a_node != null) {
					final var name = a_node.getNodeName();
					final var val = a_node.getTextContent();
					final var nameMatcher = namePat.matcher(name);
					if (nameMatcher.find()) {
						map.put(name, val);
					}
					a_node = a_node.getNextSibling();
				}
			}

			// Inverse rights to create groups
			final var nList = doc.getElementsByTagName("rights");
			final var groups = new HashMap<String, HashSet<String>>();
			final String[] attribName = { "w", "r", "d" };
			final var nRight = nList.item(0);
			if (nRight != null) {
				final var attribs = nRight.getAttributes();
				for (final String att : attribName) {
					final var value = attribs.getNamedItem(att);
					if (value == null) {
						continue;
					}
					final var names = value.getTextContent();
					final var split = names.split(",");
					for (String s : split) {
						s = s.trim();
						var right = groups.get(s);
						if (right == null) {
							right = new HashSet<>();
							groups.put(s, right);
						}
						right.add(att);
					}
				}
			}

			/// Send query
			c = SqlUtils.getConnection();
			c.setAutoCommit(false);
			final var retValue = dataProvider.writeVector(c, uid, map, groups);

			// Send result
			final OutputStream output = response.getOutputStream();
			var text = "OK";
			if (retValue < 0) {
				response.setStatus(304);
				text = "Not modified";
			}
			output.write(text.getBytes());
			output.close();

		} catch (final Exception e) {
			logger.error("Exception", e);
			try {
				if (c != null) {
					c.rollback();
				}
			} catch (final SQLException e1) {
				logger.error("SQLException", e1);
			}
			response.setStatus(500);
		} finally {
			try {
				if (c != null) {
					c.commit();
					c.close();
				}
			} catch (final SQLException e) {
				logger.error("SQLException", e);
			}
		}
	}
}
