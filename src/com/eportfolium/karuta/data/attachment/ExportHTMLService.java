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
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipOutputStream;

import javax.activation.MimeType;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.eportfolium.karuta.data.provider.DataProvider;
import com.eportfolium.karuta.data.utils.ConfigUtils;
import com.eportfolium.karuta.data.utils.DomUtils;
import com.eportfolium.karuta.data.utils.SqlUtils;

public class ExportHTMLService extends HttpServlet {

	public static final Pattern IMG_URL_PATTERN = Pattern.compile("img[^>]*src=\"(?!files)([^\"]*)");
	public static final SimpleDateFormat DATE_PATTERN_FILENAME = new SimpleDateFormat("yyyy-MM-dd_HHmmss");
	private static final Logger logger = LoggerFactory.getLogger(ExportHTMLService.class);
	private static final long serialVersionUID = 9188067506635747901L;

	public static final Pattern STYLESHEET_URL_PATTERN = Pattern.compile("stylesheet.*?href=\"([^\"]*)");

	private DataProvider dataProvider;
	private String tempdir;
	private String backend;

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
		request.getReader().close();
		response.getWriter().close();
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
		/// Check if user is logged in
		final HttpSession session = request.getSession(false);
		if (session == null) {
			return;
		}

		final int uid = (Integer) session.getAttribute("uid");
		if (uid == 0) {
			return;
		}

		request.setCharacterEncoding(StandardCharsets.UTF_8.toString());

		final String portfolioUuid = request.getParameter("pid");
		String lang = request.getParameter("lang");

		final StringBuilder data = new StringBuilder();
		/// Only a div
		data.append(request.getParameter("content"));

		// Fetch raw portfolio, since it's easier to know if it's a document or image
		Connection c;
		String portfolio = "";
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
		final File tempDir = new File(tempdir);
		if (!tempDir.isDirectory()) {
			tempDir.mkdirs();
		}
		final File tempZip = File.createTempFile(portfolioUuid, ".zip", tempDir);

		final FileOutputStream fos = new FileOutputStream(tempZip);
		final ZipOutputStream zos = new ZipOutputStream(fos);

		final String ref = request.getHeaders(HttpHeaders.REFERER).nextElement();
		final String appliname = ref.replaceFirst("(http[s]?://[^/]*/[^/]*/).*", "$1");

		//////// Check where the CSS are in the webpage
		// http://localhost:8079/karuta/other/bootstrap/css/bootstrap.min.css

		Matcher m = STYLESHEET_URL_PATTERN.matcher(data);
		//// Find all css links
		while (m.find()) {
			String link = m.group(1);
			final String filename = link.substring(link.lastIndexOf("/") + 1);
			// Fix relative CSS link, could easily break.
			if (link.contains("../../../")) {
				final String servername = request.getScheme() +
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
		String datastr = data.toString();
		datastr = datastr.replaceAll("href=\"[^\"]*(/[^\"]*.[css|less]\")", "href=\"css$1");

		// Add export javascript file
		WriteURLInZip(session, appliname + "/exported.js", "exported.js", zos);
		// Insert definition in html page
		datastr = datastr.replaceFirst("</head>", "<script src=\"exported.js\"></script></head>");

		//////// Find all fileid/filename
		Document doc;
		NodeList nodelist = null;
		final XPath xPath = XPathFactory.newInstance().newXPath();
		try {
			doc = DomUtils.xmlString2Document(portfolio, new StringBuilder());
			final String filterRes = "//*[local-name()='asmResource']/*[local-name()='fileid' and text()]";
			nodelist = (NodeList) xPath.compile(filterRes).evaluate(doc, XPathConstants.NODESET);
		} catch (final Exception e) {
			logger.error("Intercepted error", e);
			//TODO something is missing
		}

		/// Fetch all files
		for (int i = 0; i < nodelist.getLength(); ++i) {
			// Fetch back parent node that has all info under or at that level
			final Node res = nodelist.item(i).getParentNode();
			/// Check if fileid has a lang
			final Element resel = (Element) res;

			final NodeList fileids = resel.getElementsByTagName("fileid");
			final NodeList filenames = resel.getElementsByTagName("filename");
			for (int j = 0; j < fileids.getLength(); ++j) {
				final Node resLang = fileids.item(j);
				final Node resFilename = filenames.item(j);
				final Node langAtt = resLang.getAttributes().getNamedItem("lang");
				final String contextid = res.getAttributes().getNamedItem("contextid").getTextContent();
				final String realFilename = resFilename.getTextContent();
				final String fileid = resLang.getTextContent();
				logger.info("===== context: {} =====", i);
				logger.info("Context: {}", contextid);
				logger.info("Fileid: {}", fileid);
				logger.info("Filename: {}", realFilename);
				logger.info("Lang: {}", langAtt);
				logger.info("==========");
				String filterName = "";
				if (langAtt != null) {
					lang = langAtt.getNodeValue();
					filterName = ".//*[local-name()='filename' and @lang='" + lang + "' and text()]";
				} else {
					filterName = ".//*[local-name()='filename' and @lang and text()]";
				}

				final Node p = res.getParentNode(); // fileid -> resource
				final Node gp = p.getParentNode(); // resource -> context
				final Node uuidNode = gp.getAttributes().getNamedItem("id");
				final String uuid = uuidNode.getTextContent();

				NodeList textList = null;
				try {
					textList = (NodeList) xPath.compile(filterName).evaluate(p, XPathConstants.NODESET);
				} catch (final XPathExpressionException e1) {
					e1.printStackTrace();
				}
				String filename = null;
				if (textList != null && textList.getLength() != 0) {
					final Element fileNode = (Element) textList.item(0);
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
				int lastDot = filename.lastIndexOf(".");
				if (lastDot < 0) {
					lastDot = 0;
				}
				String filenameext = filename.substring(0); /// find extension
				final int extindex = filenameext.lastIndexOf(".") + 1;
				filenameext = uuid + "_" + lang + "." + filenameext.substring(extindex);

				final String url = backend + "/resources/resource/file/" + contextid + "?lang=" + lang;

				final String filepath = "files" + File.separator + lang + File.separator + filename;

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
			final String baselink = m.group(1);
			final String filename = baselink.substring(baselink.lastIndexOf("/") + 1);
			// Fix relative resource link, could easily break.
			final String servername = request.getScheme() +
					"://" +
					request.getServerName() +
					":" +
					request.getServerPort() +
					"/";
			String link = baselink;
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
		final ZipEntry ze = new ZipEntry("portfolio.html");
		zos.putNextEntry(ze);

		final byte[] bytes = datastr.getBytes();
		zos.write(bytes);

		zos.closeEntry();

		zos.close();
		fos.close();

		/// Return data
		final RandomAccessFile f = new RandomAccessFile(tempZip.getAbsoluteFile(), "r");
		final byte[] b = new byte[(int) f.length()];
		f.read(b);
		f.close();

		final String timeFormat = DATE_PATTERN_FILENAME.format(new Date());

		response.addHeader("Content-Type", "application/zip");
		response.addHeader("Content-Length", Integer.toString(b.length));
		response.addHeader("Content-Disposition", "attachment; filename=\"Export-" + timeFormat + ".zip\"");
		final ServletOutputStream writer = response.getOutputStream();
		writer.write(b);
		writer.close();
		request.getInputStream().close();

		/// Cleanup
		tempZip.delete();
	}

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

	protected void WriteURLInZip(HttpSession session, String url, String filepath, ZipOutputStream zipfile)
			throws IllegalStateException, IOException {
		final HttpGet get = new HttpGet(url);

		// Transfer sessionid so that local request still get security checked
		get.addHeader("Cookie", "JSESSIONID=" + session.getId());

		// Send request
		final CloseableHttpClient client = HttpClients.createDefault();
		final CloseableHttpResponse ret = client.execute(get);
		final HttpEntity entity = ret.getEntity();

		// Save it to zip file with a folder name
		final InputStream content = entity.getContent();
		final ZipEntry ze = new ZipEntry(filepath);
		try {
			int totalread = 0;
			zipfile.putNextEntry(ze);
			int inByte;
			final byte[] buf = new byte[4096];
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