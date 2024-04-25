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

package com.eportfolium.karuta.data.utils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.text.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;
import org.w3c.tidy.Tidy;
import org.xml.sax.InputSource;

//import com.mysql.jdbc.Connection;
//import com.mysql.jdbc.PreparedStatement;

/*
<%-- SAX classes --%>
<%@ page import="org.xml.sax.*" %>
<%@ page import="javax.xml.transform.sax.SAXResult" %>


<%-- FOP --%>
<%@ page import="org.apache.fop.apps.FOUserAgent" %>
<%@ page import="org.apache.fop.apps.Fop" %>
<%@ page import="org.apache.fop.apps.FopFactory" %>
<%@ page import="org.apache.fop.apps.MimeConstants" %>
*/

//  ==================================================================================
//					Affichage DOM
//  ==================================================================================

public class DomUtils {
	final static Logger logger = LoggerFactory.getLogger(DomUtils.class);
	final static String ACCESS_EXTERNAL_DTD = "http://javax.xml.XMLConstants/property/accessExternalDTD";
	final static String ACCESS_EXTERNAL_STYLESHEET = "http://javax.xml.XMLConstants/property/accessExternalStylesheet";

	//  ==================================================================================
	//  ============================= Manage DOM =============================================
	//  ==================================================================================

	//  =======================================
	private static Document buildDOM(String xmlString) throws Exception {
		//  =======================================
		final DocumentBuilderFactory domBuildFact = DocumentBuilderFactory.newInstance();
		final DocumentBuilder domBuild = domBuildFact.newDocumentBuilder();
		return domBuild.parse(new InputSource(new StringReader(xmlString)));
	}

	public static String cleanXMLData(String data) throws UnsupportedEncodingException {
		// data = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"+data;

		final Tidy tidy = new Tidy();
		tidy.setInputEncoding(StandardCharsets.UTF_8.toString());
		tidy.setOutputEncoding(StandardCharsets.UTF_8.toString());
		tidy.setWraplen(Integer.MAX_VALUE);
		//  tidy.setPrintBodyOnly(true);
		tidy.setXmlOut(true);
		tidy.setXmlTags(true);
		tidy.setSmartIndent(true);
		tidy.setMakeClean(true);
		tidy.setForceOutput(true);
		final ByteArrayInputStream inputStream = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
		final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		tidy.parseDOM(inputStream, outputStream);
		return outputStream.toString(StandardCharsets.UTF_8.toString());
	}

	//======================================
	public static String convertToXMLOLD(String xml) {
		//======================================
		//	String newXML = xml;
		//	String xml1 = "";
		//	String xml2 = "";
		//	String html = "";
		//	if (xml.indexOf("<text>")>-1) {
		//	xml1 = xml.substring(0,xml.indexOf("<text>")+6);
		//	xml2 = xml.substring(xml.indexOf("</text>"));
		//	html = xml.substring(xml.indexOf("<text>")+6,xml.indexOf("</text>"));
		//	}
		//	if (xml.indexOf("<comment>")>-1) {
		//	xml1 = xml.substring(0,xml.indexOf("<comment>")+9);
		//	xml2 = xml.substring(xml.indexOf("</comment>"));
		//	html = xml.substring(xml.indexOf("<comment>")+9,xml.indexOf("</comment>"));
		//	}
		//	if (xml.indexOf("<description>")>-1) {
		//	xml1 = xml.substring(0,xml.indexOf("<description>")+13);
		//	xml2 = xml.substring(xml.indexOf("</description>"));
		//	html = xml.substring(xml.indexOf("<description>")+13,xml.indexOf("</description>"));
		//	}
		//	if (html.length()>0) {  // xml is html
		//	html = "<!DOCTYPE HTML PUBLIC '-//W3C//DTD HTML 4.01 Transitional//EN' 'http://www.w3.org/TR/html4/loose.dtd'><head><title></title></head><body>" +html+"</body>";
		final StringReader in = new StringReader(xml);
		final StringWriter out = new StringWriter();
		final Tidy tidy = new Tidy();
		tidy.setInputEncoding(StandardCharsets.UTF_8.toString());
		tidy.setOutputEncoding(StandardCharsets.UTF_8.toString());
		tidy.setWraplen(Integer.MAX_VALUE);
		tidy.setPrintBodyOnly(true);
		tidy.setMakeClean(true);
		//	tidy.setForceOutput(true);
		tidy.setSmartIndent(true);
		tidy.setXmlTags(true);
		tidy.setXmlOut(true);

		//	tidy.setWraplen(0);
		tidy.parseDOM(in, out);
		final String newXML = out.toString();
		//	newXML = xml1+newHTML.substring(newHTML.indexOf("<body>")+6,newHTML.indexOf("</body>"))+xml2;
		//	} else {
		//	newXML =xml;
		//	}
		//	return newXML;
		return newXML;
	}

	//  ---------------------------------------------------
	public static void createAndSetAttribute(Node node, String tagAttributeName, String tagAttributeValue)
			throws Exception {
		//  ---------------------------------------------------
		((Document) node).createAttribute(tagAttributeName);
		((Document) node).getDocumentElement().setAttribute(tagAttributeName, tagAttributeValue);

	}

	//  ==================================================================================
	//  ============================= Transformation  ===========================================
	//  ==================================================================================

	//  =======================================
	public static String file2String(String fileName, StringBuilder outTrace) throws Exception {
		//  =======================================
		final StringBuilder result = new StringBuilder();
		try {
			final FileInputStream fichierSrce = new FileInputStream(fileName);
			final BufferedReader readerSrce = new BufferedReader(
					new InputStreamReader(fichierSrce, StandardCharsets.UTF_8));
			String line;
			while ((line = readerSrce.readLine()) != null) {
				result.append(line);
			}
			readerSrce.close();
			fichierSrce.close();
		} catch (final IOException ioe) {
			outTrace.append("<br/>file2String-- Error: " + ioe);
		}

		return result.toString();

	}

	public static String filterXmlResource(String xml) throws UnsupportedEncodingException {
		if (xml.startsWith("<?xml")) {
			final int posEndXml = xml.indexOf("?>");
			xml = xml.substring(posEndXml);

			return DomUtils.cleanXMLData(xml);

		}
		return DomUtils.cleanXMLData(xml);

	}

	public static String filtrerInnerXml(String chaine) {
		final String motif = "<?xml version=\"1.0\" encoding=\"UTF-16\"?>";
		chaine = chaine.replace(motif, "").trim();
		chaine = chaine.replace("\n\t\t\t\t\n", "\n").trim();
		return chaine;
	}

	public static String getInnerXml(Node node) {
		final DOMImplementationLS lsImpl = (DOMImplementationLS) node.getOwnerDocument().getImplementation()
				.getFeature("LS", "3.0");
		final LSSerializer lsSerializer = lsImpl.createLSSerializer();
		final NodeList childNodes = node.getChildNodes();
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < childNodes.getLength(); i++) {
			sb.append(lsSerializer.writeToString(childNodes.item(i)));
		}
		// TODO Comprendre pourquoi CDATA est mal fermé
		if (sb.toString().startsWith("<![CDATA[")) {
			sb.append("]]>");
		}
		return DomUtils.filtrerInnerXml(sb.toString());
	}

	public static String getJsonAttributeOutput(String attributeName, String attributeValue) {
		if (attributeValue == null) {
			attributeValue = "";
		}
		return "'-" + attributeName + "': '" + attributeValue + "'";
	}

	//=======================================
	/*private String processXSLTfile2String2 (ServletContext application, Document xml, String xsl, String param[], String paramVal[], StringBuilder outTrace) throws Exception {
	//=======================================
	outTrace.append("<br>-->processXSLTfile2String2-"+xsl);
	//Transformer transformer = TransformerFactory.newInstance().newTransformer(new StreamSource(new File(xsl)));
	Transformer transformer = (Transformer)application.getAttribute(xsl);
	outTrace.append(".1");
	StreamResult result = new StreamResult(new StringWriter());
	outTrace.append(".2");
	DOMSource source = new DOMSource(xml);
	outTrace.append(".3");
	for (int i = 0; i < param.length; i++) {
		transformer.setParameter(param[i], paramVal[i]);
	}
	outTrace.append(".4");
	transformer.transform(source, result);
	outTrace.append("<br><--processXSLTfile2String-"+xsl);
	return result.getWriter().toString();
	}*/

	//  ==================================================================================
	//  ================================ Utilitaires  ===========================================
	//  ==================================================================================

	public static String getJsonElementOutput(String tagName, String value) {
		if (value == null) {
			return "'" + tagName + "': ''";
		}
		return "'" + tagName + "': '" + value + "'";
	}

	public static String getNodeAttributesString(Node node) {
		final StringBuilder ret = new StringBuilder();
		final NamedNodeMap attributes = node.getAttributes();
		for (int i = 0; i < attributes.getLength(); i++) {
			final Attr attribute = (Attr) attributes.item(i);
			ret.append(attribute.getName().trim()).append("=\"")
					.append(StringEscapeUtils.escapeXml11(attribute.getValue().trim())).append("\" ");
		}
		return ret.toString();
	}

	//  ---------------------------------------------------
	public static String getRootuuid(Node node) throws Exception {
		//  ---------------------------------------------------
		String result = null;
		final NodeList liste = ((Document) node).getElementsByTagName("asmRoot");
		if (liste.getLength() != 0) {
			final Element elt = (Element) liste.item(0);
			result = elt.getAttribute("uuid");
		}
		return result;
	}

	public static String getXmlAttributeOutput(String attributeName, String attributeValue) {
		if (attributeValue == null) {
			attributeValue = "";
		}
		return attributeName + "=\"" + attributeValue + "\"";
	}

	public static String getXmlAttributeOutputInt(String attributeName, Integer attributeValue) {
		if (attributeValue == null) {
			attributeValue = 0;
		}
		return attributeName + "=\"" + attributeValue + "\"";
	}

	// -------------------------------------------------------------------------------
	/*int xml2pdf (String ppath, String xslFName, Document xmldoc, String pdfFName, String param[], String paramVal[], StringBuilder outTrace, boolean trace) throws Exception {
	// -------------------------------------------------------------------------------
	outTrace.append("<br>Entrée xml2pdf: ");
	int result=1;
	FopFactory fopFactory = FopFactory.newInstance();
	FOUserAgent foUserAgent = fopFactory.newFOUserAgent();
	
	FileOutputStream fos =null;
	
	try {
	
		//Cree des flux de lecture/ecriture sur les fichiers XSL, XML et outputName.
		File stylesheet = new File(xslFName);
		File outfile = new File(pdfFName);
	
		//Cree un objet Transformer a partir du XSL
		TransformerFactory tFactory = TransformerFactory.newInstance();
		StreamSource stylesource = new StreamSource(stylesheet);
		Transformer transformer = tFactory.newTransformer(stylesource);
	
		String s = System.getProperty("file.separator");
		String ppathImg = ppath;
		if (!s.equals("/")){
			ppathImg =ppathImg.replace (s,"/");
		}
		outTrace.append("<br>ppath:"+ppathImg);
		transformer.setParameter("ppath", ppathImg);
		for (int i = 0; i < param.length; i++) {
			transformer.setParameter(param[i], paramVal[i]);
			outTrace.append("<br>"+param[i]+":"+paramVal[i]);
		}
	
		//Creation du flux d'entree du Transformer (document XML)
		DOMSource source = new DOMSource(xmldoc);
		//Creation du flux de sortie du Transformer (Flux de sortie vers outfile)
		fos = new FileOutputStream(outfile, false);
		Fop fop = fopFactory.newFop(MimeConstants.MIME_PDF, foUserAgent, fos);
	
		Result resultpdf = new SAXResult(fop.getDefaultHandler());
		transformer.transform(source, resultpdf);
		fos.close();
	}	catch(Exception e){
		outTrace.append("Erreur xml2pdf: " +e);
		trace=true;
		result=-1;
	} finally {
		return result;
	}
	}
	*/

	public static String getXmlElementOutput(String tagName, String value) {
		if (value == null) {
			return "<" + tagName + "/>";
		}
		return "<" + tagName + ">" + value + "</" + tagName + ">";
	}

	public static DocumentBuilderFactory newSecureDocumentBuilderFactory() {
		final DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		docFactory.setXIncludeAware(false);
		docFactory.setExpandEntityReferences(false);
		trySetFeature(docFactory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
		trySetFeature(docFactory, "http://apache.org/xml/features/disallow-doctype-decl", true);
		trySetFeature(docFactory, "http://xml.org/sax/features/external-general-entities", false);
		trySetFeature(docFactory, "http://xml.org/sax/features/external-parameter-entities", false);
		trySetAttribute(docFactory, "http://javax.xml.XMLConstants/property/accessExternalDTD", "");
		trySetAttribute(docFactory, "http://javax.xml.XMLConstants/property/accessExternalSchema", "");
		return docFactory;
	}

	private static TransformerFactory newSecureTransformerFactory() throws TransformerConfigurationException {
		final TransformerFactory transformerFactory = TransformerFactory.newInstance();
		transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		if (transformerFactory.getFeature(ACCESS_EXTERNAL_DTD)) {
			transformerFactory.setFeature(ACCESS_EXTERNAL_DTD, false);
		}
		if (transformerFactory.getFeature(ACCESS_EXTERNAL_STYLESHEET)) {
			transformerFactory.setFeature(ACCESS_EXTERNAL_STYLESHEET, false);
		}
		return transformerFactory;
	}

	//  =======================================
	public static void processXSLT(Document xml, String xsltName, Document result, StringBuilder outTrace,
			boolean trace) throws Exception {
		//  =======================================
		outTrace.append("a.");
		processXSLT(new DOMSource(xml), xsltName, new DOMResult(result), outTrace, trace);
		outTrace.append("b.");
	}

	//  =======================================
	public static void processXSLT(Document xml, String xsltName, Writer result, StringBuilder outTrace, boolean trace)
			throws Exception {
		//  =======================================
		outTrace.append("c.");
		processXSLT(new DOMSource(xml), xsltName, new StreamResult(result), outTrace, trace);
		outTrace.append("d.");
	}

	//  =======================================
	public static void processXSLT(Source xml, String xsltName, Result result, StringBuilder outTrace, boolean trace)
			throws Exception {
		//  =======================================
		outTrace.append("<br>processXSLT... ").append(xsltName);
		final StreamSource stylesource = new StreamSource(xsltName);
		final Transformer transformer = newSecureTransformerFactory().newTransformer(stylesource);

		try {
			transformer.transform(xml, result);
		} catch (final TransformerException tce) {
			throw new TransformerException(tce.getMessageAndLocation());
		}
		if (trace) {
			outTrace.append(" ... ok");
		}
	}

	//  =======================================
	public static String processXSLTfile2String(Document xml, String xslFile, String param[], String paramVal[],
			StringBuilder outTrace) throws Exception {
		//  =======================================
		logger.debug("<br>-->processXSLTfile2String-" + xslFile);
		outTrace.append("<br>-->processXSLTfile2String-").append(xslFile);
		final Transformer transformer = newSecureTransformerFactory()
				.newTransformer(new StreamSource(new File(xslFile)));
		outTrace.append(".1");
		final StreamResult result = new StreamResult(new StringWriter());
		outTrace.append(".2");
		final DOMSource source = new DOMSource(xml);
		outTrace.append(".3");
		for (int i = 0; i < param.length; i++) {
			outTrace.append("<br>setParemater - ").append(param[i]).append(":").append(paramVal[i]).append("...");
			logger.debug("<br>setParameter - " + param[i] + ":" + paramVal[i] + "...");
			transformer.setParameter(param[i], paramVal[i]);
			outTrace.append("ok");
			logger.debug("ok");
		}
		outTrace.append(".4");
		transformer.transform(source, result);
		outTrace.append("<br><--processXSLTfile2String-").append(xslFile);
		logger.debug("<br><--processXSLTfile2String-" + xslFile);
		return result.getWriter().toString();
	}

	//  ---------------------------------------------------
	public static String readXmlString(Connection connexion, String id, StringBuilder outTrace) throws Exception {
		//  ---------------------------------------------------
		PreparedStatement ps = null;
		ResultSet rs = null;
		String reqSQL = null;
		String xmlString = "";
		try {
			reqSQL = "SELECT xml FROM tree where id=" + id;
			ps = connexion.prepareStatement(reqSQL);
			rs = ps.executeQuery();
			if (rs.next()) {
				xmlString = rs.getString(1);
			}
		} catch (final Exception e) {
			outTrace.append("Erreur readXmlString:  (req=").append(reqSQL).append("  error:").append(e);
		} finally {
			rs.close();
			ps.close();
		}
		return xmlString;
	}

	//  ---------------------------------------------------
	public static void saveString(String str, String fileName) throws Exception {
		//  ---------------------------------------------------
		final Writer fwriter = new OutputStreamWriter(new FileOutputStream(fileName), StandardCharsets.UTF_8);
		fwriter.write(str);
		fwriter.close();
	}

	//  ---------------------------------------------------
	public static void SetAttribute(Node node, String tagAttributeName, String tagAttributeValue) throws Exception {
		//  ---------------------------------------------------
		((Document) node).getDocumentElement().setAttribute(tagAttributeName, tagAttributeValue);

	}

	private static void trySetAttribute(DocumentBuilderFactory factory, String feature, String value) {
		try {
			factory.setAttribute(feature, value);
		} catch (final Exception e) {
			logger.info("The feature '" + feature + "' is probably not supported by your XML processor.");
			throw new RuntimeException(e);
		}
	}

	private static void trySetFeature(DocumentBuilderFactory factory, String feature, boolean value) {
		try {
			factory.setFeature(feature, value);
		} catch (final Exception e) {
			logger.info("The feature '" + feature + "' is probably not supported by your XML processor.");
			throw new RuntimeException(e);
		}
	}

	//  =======================================
	public static Document xmlString2Document(String xmlString, StringBuilder outTrace) throws Exception {
		//  =======================================
		final DocumentBuilderFactory factory = newSecureDocumentBuilderFactory();

		Document xmldoc = null;
		final DocumentBuilder builder = factory.newDocumentBuilder();
		xmldoc = builder.parse(new InputSource(new StringReader(xmlString)));
		return xmldoc;
	}

}