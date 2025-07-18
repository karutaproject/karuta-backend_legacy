/* =======================================================
	Copyright 2017 - ePortfolium - Licensed under the
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipOutputStream;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.eportfolium.karuta.data.provider.DataProvider;
import com.eportfolium.karuta.data.utils.ConfigUtils;
import com.eportfolium.karuta.data.utils.DomUtils;
import com.eportfolium.karuta.data.utils.SqlUtils;

import jakarta.activation.MimeType;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

public class ExportHTMLService extends HttpServlet {

	public static final Pattern IMG_URL_PATTERN = Pattern.compile("img[^>]*src=\"(?!files)([^\"]*)");
	public static final SimpleDateFormat DATE_PATTERN_FILENAME = new SimpleDateFormat("yyyy-MM-dd_HHmmss");
	private static final Logger logger = LogManager.getLogger(ExportHTMLService.class);
	private static final long serialVersionUID = 9188067506635747901L;

	public static final Pattern STYLESHEET_URL_PATTERN = Pattern.compile("stylesheet.*?href=\"([^\"]*)");

	private DataProvider dataProvider;
	private String tempdir;
	private String backend;

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		try {
			ConfigUtils.init(getServletContext());
			dataProvider = SqlUtils.initProvider();
			tempdir = System.getProperty("java.io.tmpdir", null);
			backend = ConfigUtils.getInstance().getRequiredProperty("backendserver");
		} catch (final Exception e) {
			logger.error("Can't init servlet", e);
			throw new ServletException(e);
		}
	}

	public void initialize(HttpServletRequest httpServletRequest) {
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
		request.getReader().close();
		response.getWriter().close();
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
		/// Check if user is logged in
		final var session = request.getSession(false);
		if (session == null) {
			return;
		}

		final int uid = (Integer) session.getAttribute("uid");
		if (uid == 0) {
			return;
		}

		request.setCharacterEncoding(StandardCharsets.UTF_8.toString());

		final var portfolioUuid = request.getParameter("pid");
		var lang = request.getParameter("lang");

		final var data = new StringBuilder();
		/// Only a div
		data.append(request.getParameter("content"));

		// Fetch raw portfolio, since it's easier to know if it's a document or image
		Connection c;
		var portfolio = "";
		try {
			c = SqlUtils.getConnection();
			portfolio = dataProvider
					.getPortfolio(c, new MimeType("text/xml"), portfolioUuid, uid, 0, "", "true", "", uid, null)
					.toString();
			c.close();
		} catch (final Exception e) {
			logger.error("Intercepted error", e);
			//TODO something is missing
		}

		/// Temp file in temp directory
		final var tempDir = new File(tempdir);
		if (!tempDir.isDirectory()) {
			tempDir.mkdirs();
		}
		final var tempZip = File.createTempFile(portfolioUuid, ".zip", tempDir);

		final var fos = new FileOutputStream(tempZip);
		final var zos = new ZipOutputStream(fos);

		final var ref = request.getHeaders(HttpHeaders.REFERER).nextElement();
		final var appliname = ref.replaceFirst("(http[s]?://[^/]*/[^/]*/).*", "$1");

		//////// Check where the CSS are in the webpage
		// http://localhost:8079/karuta/other/bootstrap/css/bootstrap.min.css

		var m = STYLESHEET_URL_PATTERN.matcher(data);
		//// Find all css links
		while (m.find()) {
			var link = m.group(1);
			final var filename = link.substring(link.lastIndexOf("/") + 1);
			// Fix relative CSS link, could easily break.
			if (link.contains("../../../")) {
				final var servername = request.getScheme() +
						"://" +
						request.getServerName() +
						":" +
						request.getServerPort() +
						"/";
				link = servername + link.replace("../../../", ""); // Main CSS files
			} else if (link.contains("../../")) // Usual location
			{
				link = appliname + link.replace("../../", ""); // other css
			} else {
				link = appliname + link.replace("../", "application/"); // specific CSS files (usual location)
			}
			// Fetch them and put them in the zip file
			logger.info(link + " ->" + filename);
			WriteURLInZip(session, link, "css" + File.separator + filename, zos);
		}

		//// Rewrite html link for the CSS
		var datastr = data.toString();
		datastr = datastr.replaceAll("href=\"[^\"]*(/[^\"]*.[css|less]\")", "href=\"css$1");

		// Add export javascript file
		WriteURLInZip(session, appliname + "/exported.js", "exported.js", zos);
		// Insert definition in html page
		datastr = datastr.replaceFirst("</head>", "<script src=\"exported.js\"></script></head>");

		//////// Find all fileid/filename
		Document doc;
		NodeList nodelist = null;
		final var xPath = XPathFactory.newInstance().newXPath();
		try {
			doc = DomUtils.xmlString2Document(portfolio, new StringBuilder());
			final var filterRes = "//*[local-name()='asmResource']/*[local-name()='fileid' and text()]";
			nodelist = (NodeList) xPath.compile(filterRes).evaluate(doc, XPathConstants.NODESET);
		} catch (final Exception e) {
			logger.error("Intercepted error", e);
			//TODO something is missing
		}

		/// Fetch all files
		for (var i = 0; i < nodelist.getLength(); ++i) {
			// Fetch back parent node that has all info under or at that level
			final var res = nodelist.item(i).getParentNode();
			/// Check if fileid has a lang
			final var resel = (Element) res;

			final var fileids = resel.getElementsByTagName("fileid");
			final var filenames = resel.getElementsByTagName("filename");
			for (var j = 0; j < fileids.getLength(); ++j) {
				final var resLang = fileids.item(j);
				final var resFilename = filenames.item(j);
				final var langAtt = resLang.getAttributes().getNamedItem("lang");
				final var contextid = res.getAttributes().getNamedItem("contextid").getTextContent();
				final var realFilename = resFilename.getTextContent();
				final var fileid = resLang.getTextContent();
				logger.info("===== context: {} =====", i);
				logger.info("Context: {}", contextid);
				logger.info("Fileid: {}", fileid);
				logger.info("Filename: {}", realFilename);
				logger.info("Lang: {}", langAtt);
				logger.info("==========");
				var filterName = "";
				if (langAtt != null) {
					lang = langAtt.getNodeValue();
					filterName = ".//*[local-name()='filename' and @lang='" + lang + "' and text()]";
				} else {
					filterName = ".//*[local-name()='filename' and @lang and text()]";
				}

				final var p = res.getParentNode(); // fileid -> resource
				final var gp = p.getParentNode(); // resource -> context
				final var uuidNode = gp.getAttributes().getNamedItem("id");
				final var uuid = uuidNode.getTextContent();

				NodeList textList = null;
				try {
					textList = (NodeList) xPath.compile(filterName).evaluate(p, XPathConstants.NODESET);
				} catch (final XPathExpressionException e1) {
					e1.printStackTrace();
				}
				String filename = null;
				if (textList != null && textList.getLength() != 0) {
					final var fileNode = (Element) textList.item(0);
					filename = fileNode.getTextContent();
					lang = fileNode.getAttribute("lang"); // In case it's a general fileid, fetch first filename (which can break things if nodes are not clean)
					if ("".equals(lang)) {
						lang = "fr";
					}
				}

				// Put specific name for later recovery
				if (filename == null || filename.isEmpty()) {
					continue;
				}
				var lastDot = filename.lastIndexOf(".");
				if (lastDot < 0) {
					lastDot = 0;
				}
				var filenameext = filename.substring(0); /// find extension
				final var extindex = filenameext.lastIndexOf(".") + 1;
				filenameext = uuid + "_" + lang + "." + filenameext.substring(extindex);

				final var url = backend + "/resources/resource/file/" + contextid + "?lang=" + lang;

				final var filepath = "files" + File.separator + lang + File.separator + filename;

				logger.info("Added files URL: {}", url);

				WriteURLInZip(session, url, filepath, zos);

				/// Rewrite file link
				logger.info("Replacing: {}?lang={}", contextid, lang);
				if (datastr.contains(contextid + "?lang=" + lang)) {
					logger.debug("ISIN");
				}
				datastr = datastr.replaceFirst("['\"][^'\"]*" + contextid + "\\?lang=" + lang + "[^'\"]*['\"]",
						filepath);
				if (logger.isDebugEnabled()) {
					if (datastr.contains(contextid + "?lang=" + lang)) {
						logger.debug("ISSTILLIN");
					} else {
						logger.debug("REPLACED");
					}
				}
			}
		}

		/// Resolve remaining resources (logo, icons, etc) that have not been replaced
		m = IMG_URL_PATTERN.matcher(data);
		// Find all resource links
		while (m.find()) {
			final var baselink = m.group(1);
			final var filename = baselink.substring(baselink.lastIndexOf("/") + 1);
			// Fix relative resource link, could easily break.
			final var servername = request.getScheme() +
					"://" +
					request.getServerName() +
					":" +
					request.getServerPort() +
					"/";
			var link = baselink;
			if (baselink.contains("../../../")) {
				link = servername + baselink.replace("../../../", "");
			} else {
				link = appliname + baselink.replace("../", "karuta/"); /// Will easily break
			}
			// Fetch them and put them in the zip file
			logger.info("Other res: " + link + " ->" + filename);
			WriteURLInZip(session, link, "files" + File.separator + filename, zos);

			/// Rewrite base resource
			datastr = datastr.replaceAll(baselink, "files/" + filename);
		}

		/// Try to put the font files, will really easily break
		WriteURLInZip(session, appliname + "other/bootstrap/fonts/glyphicons-halflings-regular.woff2",
				"fonts" + File.separator + "glyphicons-halflings-regular.woff2", zos);
		WriteURLInZip(session, appliname + "other/bootstrap/fonts/glyphicons-halflings-regular.woff",
				"fonts" + File.separator + "glyphicons-halflings-regular.woff", zos);
		WriteURLInZip(session, appliname + "other/bootstrap/fonts/glyphicons-halflings-regular.ttf",
				"fonts" + File.separator + "glyphicons-halflings-regular.ttf", zos);

		/// Write main html file to zip
		final var ze = new ZipEntry("portfolio.html");
		zos.putNextEntry(ze);

		final var bytes = datastr.getBytes();
		zos.write(bytes);

		zos.closeEntry();

		zos.close();
		fos.close();

		/// Return data
		final var f = new RandomAccessFile(tempZip.getAbsoluteFile(), "r");
		final var b = new byte[(int) f.length()];
		f.read(b);
		f.close();

		final var timeFormat = DATE_PATTERN_FILENAME.format(new Date());

		response.addHeader("Content-Type", "application/zip");
		response.addHeader("Content-Length", Integer.toString(b.length));
		response.addHeader("Content-Disposition", "attachment; filename=\"Export-" + timeFormat + ".zip\"");
		final var writer = response.getOutputStream();
		writer.write(b);
		writer.close();
		request.getInputStream().close();

		/// Cleanup
		tempZip.delete();
	}

	protected void WriteURLInZip(HttpSession session, String url, String filepath, ZipOutputStream zipfile)
			throws IllegalStateException, IOException {
		final var get = new HttpGet(url);

		// Transfer sessionid so that local request still get security checked
		get.addHeader("Cookie", "JSESSIONID=" + session.getId());

		// Send request
		final var client = HttpClients.createDefault();
		final var ret = client.execute(get);
		final var entity = ret.getEntity();

		// Save it to zip file with a folder name
		final var content = entity.getContent();
		final var ze = new ZipEntry(filepath);
		try {
			var totalread = 0;
			zipfile.putNextEntry(ze);
			int inByte;
			final var buf = new byte[4096];
			while ((inByte = content.read(buf)) != -1) {
				totalread += inByte;
				zipfile.write(buf, 0, inByte);
			}
			logger.info("FILE: {} => {} : {}", url, filepath, totalread);
			content.close();
			zipfile.closeEntry();
		} catch (final ZipException e) {
			logger.error("Zip error", e);
			// TODO something is missing
		} catch (final Exception e) {
			logger.error("Intercepted error", e);
			//TODO something is missing
		}
		EntityUtils.consume(entity);
		ret.close();
		client.close();
	}

}