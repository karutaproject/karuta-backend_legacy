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

package com.portfolio.data.utils;


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

import org.apache.commons.lang3.StringEscapeUtils;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;
import org.w3c.tidy.Tidy;
import org.xml.sax.InputSource;

import com.mysql.jdbc.Connection;
import com.mysql.jdbc.PreparedStatement;
import com.mysql.jdbc.ResultSet;

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

public class DomUtils
{


//  =======================================
	private static String dom2string(Document dom) throws Exception {  // à supprimer
//  =======================================
		Transformer transformer = TransformerFactory.newInstance().newTransformer();
		StreamResult result = new StreamResult(new StringWriter());
		DOMSource source = new DOMSource(dom);
		transformer.transform(source, result);
		return result.getWriter().toString();
	}

//  ==================================================================================
//  ============================= Manage DOM =============================================
//  ==================================================================================

//  =======================================
	private  static Document newDOM () throws Exception {
//  =======================================
		DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder domBuilder = domFactory.newDocumentBuilder();
		return domBuilder.newDocument();
	}

//  =======================================
	private static Document buildDOM (String xmlString) throws Exception {
//  =======================================
		DocumentBuilderFactory domBuildFact = DocumentBuilderFactory.newInstance();
		DocumentBuilder domBuild = domBuildFact.newDocumentBuilder();
		return domBuild.parse(new InputSource(new StringReader(xmlString)));
	}

//  ===============================
	private  static Document loadDOM (String XMLfileName) throws Exception {
//  ===============================
		DocumentBuilderFactory domBuildFact = DocumentBuilderFactory.newInstance();
		DocumentBuilder domBuild = domBuildFact.newDocumentBuilder();
		return domBuild.parse(XMLfileName);
	}

//  ===============================
	private static void saveDOM (Document doc, String xmlFileName) throws Exception {
//  ===============================
			Transformer trans = TransformerFactory.newInstance().newTransformer();
			Source srce = new DOMSource(doc);
			Result dest = new StreamResult(new File(xmlFileName));
			trans.transform(srce,dest);
	}

//  =======================================
	private static String printDOM(Document doc) throws Exception {
//  =======================================
		Transformer transformer = TransformerFactory.newInstance().newTransformer();
		StreamResult result = new StreamResult(new StringWriter());
		transformer.transform(new DOMSource(doc), result);
		return result.getWriter().toString();
	}

//  ---------------------------------------------------
	public  static void createAndSetAttribute(Node node, String tagAttributeName, String tagAttributeValue) throws Exception {
//  ---------------------------------------------------
		((Document)node).createAttribute(tagAttributeName);
		((Document)node).getDocumentElement().setAttribute(tagAttributeName, tagAttributeValue) ;

	}
//  ---------------------------------------------------
	public  static void SetAttribute(Node node, String tagAttributeName, String tagAttributeValue) throws Exception {
//  ---------------------------------------------------
		((Document)node).getDocumentElement().setAttribute(tagAttributeName, tagAttributeValue) ;

	}

//  ---------------------------------------------------
	public static  String getRootuuid(Node node) throws Exception {
//  ---------------------------------------------------
		String result =null;
		NodeList liste = ((Document)node).getElementsByTagName("asmRoot");
		if (liste.getLength() != 0) {
			Element elt = (Element) liste.item(0);
			result = elt.getAttribute("uuid");
		}
		return result;
	}

//  ==================================================================================
//  ============================= Transformation  ===========================================
//  ==================================================================================



//  =======================================
	public static void processXSLT (Source xml, String xsltName, Result result, StringBuffer outTrace, boolean trace) throws Exception {
//  =======================================
		outTrace.append("<br>processXSLT... "+xsltName);
		TransformerFactory tFactory = TransformerFactory.newInstance();
		StreamSource stylesource = new StreamSource(xsltName);
		Transformer transformer = tFactory.newTransformer(stylesource);

		try {
			transformer.transform(xml, result);
		}
		catch (TransformerConfigurationException tce) {
			throw new TransformerException(tce.getMessageAndLocation());
		}
		catch (TransformerException te) {
			throw new TransformerException(te.getMessageAndLocation());
		}
		if (trace) outTrace.append(" ... ok");
	}

//  =======================================
    public  static void processXSLT (Document xml, String xsltName, Document result, StringBuffer outTrace, boolean trace) throws Exception {
//  =======================================
          outTrace.append("a.");
          processXSLT(new DOMSource(xml), xsltName, new DOMResult(result), outTrace, trace);
          outTrace.append("b.");
}

//  =======================================
      public  static void processXSLT (Document xml, String xsltName, Writer result, StringBuffer outTrace, boolean trace) throws Exception {
//  =======================================
        outTrace.append("c.");
        processXSLT(new DOMSource(xml), xsltName, new StreamResult(result), outTrace, trace);
        outTrace.append("d.");
  }



//  =======================================
public static String processXSLTfile2String (Document xml, String xslFile, String param[], String paramVal[], StringBuffer outTrace) throws Exception {
//  =======================================
	outTrace.append("<br>-->processXSLTfile2String-"+xslFile);
	Transformer transformer = TransformerFactory.newInstance().newTransformer(new StreamSource(new File(xslFile)));
	outTrace.append(".1");
	StreamResult result = new StreamResult(new StringWriter());
	outTrace.append(".2");
	DOMSource source = new DOMSource(xml);
	outTrace.append(".3");
	for (int i = 0; i < param.length; i++) {
		outTrace.append("<br>setParemater - "+param[i]+":"+paramVal[i]+"...");
		transformer.setParameter(param[i], paramVal[i]);
		outTrace.append("ok");
	}
	outTrace.append(".4");
	transformer.transform(source, result);
	outTrace.append("<br><--processXSLTfile2String-"+xslFile);
	return result.getWriter().toString();
}

//=======================================
/*private String processXSLTfile2String2 (ServletContext application, Document xml, String xsl, String param[], String paramVal[], StringBuffer outTrace) throws Exception {
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

//  =======================================
	public static Document xmlString2Document (String xmlString, StringBuffer outTrace) throws Exception {
//  =======================================
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		Document xmldoc =null;
		DocumentBuilder builder = factory.newDocumentBuilder();
		xmldoc = builder.parse(new InputSource(new StringReader(xmlString)));
		return xmldoc;
}

//  =======================================
	public static String file2String (String fileName, StringBuffer outTrace) throws Exception {
//  =======================================
	String result ="";
	try {
		FileInputStream fichierSrce =  new FileInputStream(fileName);
		BufferedReader readerSrce = new BufferedReader(new InputStreamReader(fichierSrce,"UTF-8"));
		String line;
		while( (line=readerSrce.readLine())!=null){
			result += line;
		}
		readerSrce.close();
		fichierSrce.close();
	}
	catch (IOException ioe) {
		outTrace.append("<br/>file2String-- Error: "+ioe);
	}
	finally {
		return result;
	}
}



// -------------------------------------------------------------------------------
	/*int xml2pdf (String ppath, String xslFName, Document xmldoc, String pdfFName, String param[], String paramVal[], StringBuffer outTrace, boolean trace) throws Exception {
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

//  ---------------------------------------------------
public  static String readXmlString(Connection connexion, String id, StringBuffer outTrace) throws Exception {
//  ---------------------------------------------------
		PreparedStatement ps = null;
		ResultSet rs = null;
		String reqSQL = null;
		String xmlString = "";
		try{
			reqSQL = "SELECT xml FROM tree where id="+id;
			ps = (PreparedStatement) connexion.prepareStatement(reqSQL);
			rs = (ResultSet) ps.executeQuery();
			if (rs.next()) {
				xmlString = rs.getString(1);
			}
		}	catch(Exception e){
			outTrace.append("Erreur readXmlString:  (req="+reqSQL+"  error:"+e);
		}	finally {
			rs.close();
			ps.close();
			return xmlString;
		}
	}

//  ---------------------------------------------------
	public  static void saveString (String str, String fileName) throws Exception {
//  ---------------------------------------------------
		Writer fwriter = new OutputStreamWriter(new FileOutputStream(fileName), "UTF-8");
		fwriter.write(str);
		fwriter.close();
	}

//  ---------------------------------------------------
    private static void insererXML(Connection connexion,Document xmlSourceDoc, String partageableId, StringBuffer outTrace, boolean trace) throws Exception {
//  ---------------------------------------------------
	if (trace) outTrace.append("<br>insererPartageable -- entrée");
	if (trace) outTrace.append("<br>partageableId="+partageableId);
	// ===============chargement du document source ========================================

	if (trace) outTrace.append("<br>lecture du document xml :" + partageableId+"...");
	Document xmlPartageable = buildDOM(readXmlString(connexion, partageableId,outTrace));
	if (trace) outTrace.append(" ok");

	DocumentFragment aInserer = xmlSourceDoc.createDocumentFragment();

	NodeList liste = xmlPartageable.getDocumentElement().getChildNodes();
	int nbListe = liste.getLength();
	if (trace) outTrace.append("<br> nbListe="+nbListe);
	for (int i=0;i<nbListe;i++) {
		aInserer.appendChild(xmlSourceDoc.importNode(liste.item(i),true));
	}

	xmlSourceDoc.getFirstChild().insertBefore(aInserer,xmlSourceDoc.getFirstChild().getFirstChild());
	if (trace) outTrace.append("<br>insererPartageable -- sortie");
    }




    public static String getInnerXml(Node node) {
        DOMImplementationLS lsImpl = (DOMImplementationLS)node.getOwnerDocument().getImplementation().getFeature("LS", "3.0");
        LSSerializer lsSerializer = lsImpl.createLSSerializer();
        NodeList childNodes = node.getChildNodes();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < childNodes.getLength(); i++) {
           sb.append(lsSerializer.writeToString(childNodes.item(i)));
        }
        // TODO Comprendre pourquoi CDATA est mal fermé
        if(sb.toString().startsWith("<![CDATA[")) sb.append("]]>");
        return DomUtils.filtrerInnerXml(sb.toString());
    }

	public static String getXmlAttributeOutput(String attributeName,String attributeValue)
	{
		if(attributeValue==null) attributeValue = "";
		return attributeName+"=\""+attributeValue+"\"";
	}

	public static String getXmlAttributeOutputInt(String attributeName,Integer attributeValue)
	{
		if(attributeValue==null) attributeValue = 0;
		return attributeName+"=\""+attributeValue+"\"";
	}

	public static String filterXmlResource(String xml) throws UnsupportedEncodingException
	{
		if(xml.startsWith("<?xml"))
		{
			int posEndXml = xml.indexOf("?>");
			xml =  xml.substring(posEndXml);

			return DomUtils.cleanXMLData(xml);

		}
		else return DomUtils.cleanXMLData(xml);

	}

	public static String getJsonAttributeOutput(String attributeName,String attributeValue)
	{
		if(attributeValue==null) attributeValue = "";
		return "'-"+attributeName+"': '"+attributeValue+"'";
	}

	public static String getXmlElementOutput(String tagName, String value)
	{
		if(value==null) return "<"+tagName+"/>";
		else
		return "<"+tagName+">"+value+"</"+tagName+">";
	}

	public static String getJsonElementOutput(String tagName, String value)
	{
		if(value==null) return "'"+tagName+"': ''";
		else
			return "'"+tagName+"': '"+value+"'";
	}


	public static String getNodeAttributesString(Node node)
	{
		String ret = "";
		NamedNodeMap attributes = node.getAttributes();
		for(int i=0;i<attributes.getLength();i++)
		{
			Attr attribute = (Attr)attributes.item(i);
			ret += attribute.getName().trim() +"=\""+ StringEscapeUtils.escapeXml11(attribute.getValue().trim())+"\" ";
		}
		return ret;
	}

	public static String filtrerInnerXml(String chaine)
	{
		String motif = "<?xml version=\"1.0\" encoding=\"UTF-16\"?>";
		chaine = chaine.replace(motif, "").trim();
		chaine = chaine.replace("\n\t\t\t\t\n", "\n").trim();
		return chaine;
	}

	public static String cleanXMLData(String data) throws UnsupportedEncodingException {
	   // data = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"+data;

		Tidy tidy = new Tidy();
	    tidy.setInputEncoding("UTF-8");
	    tidy.setOutputEncoding("UTF-8");
	    tidy.setWraplen(Integer.MAX_VALUE);
	  //  tidy.setPrintBodyOnly(true);
	    tidy.setXmlOut(true);
	    tidy.setXmlTags(true);
	    tidy.setSmartIndent(true);
	    tidy.setMakeClean(true);
	    tidy.setForceOutput(true);
	    ByteArrayInputStream inputStream = new ByteArrayInputStream(data.getBytes("UTF-8"));
	    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
	    tidy.parseDOM(inputStream, outputStream);
	    return outputStream.toString("UTF-8");
	}

	//======================================
	public static String convertToXMLOLD(String xml){
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
	StringReader in = new StringReader(xml);
	StringWriter out = new StringWriter();
	Tidy tidy = new Tidy();
	tidy.setInputEncoding("UTF-8");
    tidy.setOutputEncoding("UTF-8");
    tidy.setWraplen(Integer.MAX_VALUE);
    tidy.setPrintBodyOnly(true);
	tidy.setMakeClean(true);
//	tidy.setForceOutput(true);
    tidy.setSmartIndent(true);
    tidy.setXmlTags(true);
	tidy.setXmlOut(true);

//	tidy.setWraplen(0);
	tidy.parseDOM(in, out);
	String newXML = out.toString();
//	newXML = xml1+newHTML.substring(newHTML.indexOf("<body>")+6,newHTML.indexOf("</body>"))+xml2;
//	} else {
//	newXML =xml;
//	}
//	return newXML;
	return newXML;
	}

}
