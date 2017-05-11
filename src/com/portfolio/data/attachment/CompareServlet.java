package com.portfolio.data.attachment;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.activation.MimeType;
import javax.activation.MimeTypeParseException;
import javax.naming.InitialContext;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.sql.DataSource;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.IOUtils;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.MimeConstants;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.opensaml.saml1.core.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.*;

import com.portfolio.data.provider.DataProvider;
import com.portfolio.data.utils.ConfigUtils;
import com.portfolio.data.utils.SqlUtils;
import com.portfolio.security.Credential;

public class CompareServlet extends HttpServlet {
    
    final Logger logger = LoggerFactory.getLogger(CompareServlet.class);
	private static final long serialVersionUID = 9188067506635747901L;

	DataProvider dataProvider;
	boolean hasNodeReadRight = false;
	boolean hasNodeWriteRight = false;
	Credential credential;
	int userId;
	int groupId = -1;
	HttpSession session;
	static DataProvider provider;
    
    public void initialize(HttpServletRequest httpServletRequest)
    {
    }
	
	public static int comparaisonParCodes(String code1, String code2, Element racine, Connection c) throws TransformerFactoryConfigurationError, MimeTypeParseException, Exception
    {
		int pourcentage=0;
		NodeList liste1 = racine.getElementsByTagName("code");
    	List<Node> listeCode1 = new ArrayList<Node>();
    	List<Node> listeCode2 = new ArrayList<Node>();
    	for(int i=0;i<liste1.getLength();i++)
		{
			Node nd = liste1.item(i).getFirstChild();
			if(nd!=null)
			{
				String txt = nd.getNodeValue();
				if(txt.equals(code1))
				{
					listeCode1.add(liste1.item(i));
				}
				else if(txt.equals(code2))
				{
					listeCode2.add(liste1.item(i));
				}
			}
			else{};
		}
    	
    	if(listeCode1.size()==1 && listeCode2.size()==1)
    	{
    		Node parNd1=listeCode1.get(0).getParentNode().getParentNode();
    		Node parNd2=listeCode2.get(0).getParentNode().getParentNode();
    		
    		pourcentage = comparaisonVraiFaux(parNd1,parNd2,c);
    		
    	
    	}
    	else if(listeCode1.size()==0 | listeCode2.size()==0)
    	{
    		System.out.println("Aucune donnée à comparer");
    	}
    	else if(listeCode1.size()!= 0 && listeCode2.size() != 0 && (listeCode1.size()>1 | listeCode2.size()>1))
    	{
    		System.out.println("Impossible de comparer plus de deux données");
    	}
    	return pourcentage;
    }
    
   
       	public static int comparaisonVraiFaux(Node nd1, Node nd2, Connection c) throws TransformerFactoryConfigurationError, MimeTypeParseException, Exception
    	{
    		int prct=0;
    		int nbBR=0;
    		int nbComparaison=0;
       		boolean similaire = false;
    		Element elt1 = (Element) nd1;
    		Element elt2 = (Element) nd2;
    		NodeList ndL1 = elt1.getElementsByTagName("code");
    		NodeList ndL2 = elt2.getElementsByTagName("code");
    		for (int i=0;i<ndL1.getLength();i++)
    		{
    			for(int j=0;j<ndL2.getLength();j++)
    			{
    				
    				if(ndL1.item(i).hasChildNodes() && ndL2.item(j).hasChildNodes())
    				{
    				
    					String cd1=ndL1.item(i).getFirstChild().getNodeValue().trim();
    					String cd2=ndL2.item(j).getFirstChild().getNodeValue().trim();
    					
    					if(cd1.equals(cd2))
    					{
    						Element pndL1 = (Element) ndL1.item(i).getParentNode();
    						Element pndL2 = (Element) ndL2.item(j).getParentNode();
    						String str1 = pndL1.getAttribute("xsi_type");
    						String str2 = pndL2.getAttribute("xsi_type");
    						
    						if(str1.equals("nodeRes") && str2.equals("nodeRes"))
    						{
    							Element aComparer1 = (Element) pndL1.getNextSibling().getNextSibling().getFirstChild();
    							String v1 = aComparer1.getTextContent().trim();
    							Element aComparer2 = (Element) pndL2.getNextSibling().getNextSibling().getFirstChild();
    							String v2 = aComparer2.getTextContent().trim();
    						
    							if(v1!=null && v2!=null && v1.equals(v2))
    							{
    								similaire=true;
    								nbComparaison = nbComparaison+1;
    								nbBR=nbBR+1;
    								Element n = (Element) aComparer1.getParentNode().getParentNode().getFirstChild(); 
    								n.setAttribute("compare", "true"); //ajout attribut compare dans metadatawad 
    								StringWriter stw = new StringWriter();
    								Transformer serializer = TransformerFactory.newInstance().newTransformer();
    								serializer.transform(new DOMSource(n), new StreamResult(stw));
    								String result = stw.toString();

    								Element nP = (Element) n.getParentNode();
    								String idd=nP.getAttribute("id");

    								provider.putNodeMetadataWad(c, new MimeType("text/xml"), idd, result, 1, 0);
    							}
    							else if ((v1!=null && v2!=null && !v1.equals(v2)))
    							{
    								similaire=false;
    								nbComparaison = nbComparaison+1;
    								Element n = (Element) aComparer1.getParentNode().getParentNode().getFirstChild();
    								n.setAttribute("compare", "false");
    								StringWriter stw = new StringWriter();
    								Transformer serializer = TransformerFactory.newInstance().newTransformer();
    								serializer.transform(new DOMSource(n), new StreamResult(stw));
    								String result = stw.toString();

    								Element nP = (Element) n.getParentNode();
    								String idd=nP.getAttribute("id");

    								provider.putNodeMetadataWad(c, new MimeType("text/xml"), idd, result, 1, 0);
    							}
    							else 
    							{}
    							aComparer1=null;
    							aComparer2=null;
    						}
    						else
    						{
    						
    						}
    						
    						pndL1=null;
    						pndL2=null;
    					}
    					else{}
    					cd1=""; cd2="ee";
    				}
    				else{}
    			}
    		}
    			
    		prct = ((100*nbBR)/(nbComparaison));
    		System.out.println(nbBR+"/"+nbComparaison+"   "+prct);

    		return prct;
    		
    	}
        
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException
    {
    	String uuidsNds = request.getPathInfo().substring(1);
    	
    	String uuidREP = uuidsNds.substring(0,36);
    	String uuidSOL = uuidsNds.substring(36,72);
    	String uuidNd = uuidsNds.substring(72);
    	
	  	Connection c;
		
		String lbl = null;
		Object ingt = "g";
		Object ingtREP = "";
		Object ingtSOL = "";
		try {
			provider = SqlUtils.initProvider(null, logger);
			c = SqlUtils.getConnection(getServletContext());
			ingt = provider.getNode(c, new MimeType("text/xml"), uuidNd, true, 1, groupId, lbl); //pour test remplacer uuidNd par strNoeud
			ingtREP = provider.getNode(c, new MimeType("text/xml"), uuidREP, true, 1, groupId, lbl);
			ingtSOL = provider.getNode(c, new MimeType("text/xml"), uuidSOL, true, 1, groupId, lbl);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
									}
		//out.println("<h2>FAIT...</h2>");
		
		
		//parse les données du noeud
		DocumentBuilderFactory documentBuilderFactory =DocumentBuilderFactory.newInstance();
		DocumentBuilder documentBuilder = null;
		try {
			documentBuilder = documentBuilderFactory.newDocumentBuilder();
		} catch (ParserConfigurationException e1) {
			e1.printStackTrace();
		}
		ByteArrayInputStream is = new ByteArrayInputStream(ingt.toString().getBytes("UTF-8"));
		ByteArrayInputStream isREP = new ByteArrayInputStream(ingtREP.toString().getBytes("UTF-8"));
		ByteArrayInputStream isSOL = new ByteArrayInputStream(ingtSOL.toString().getBytes("UTF-8"));
		Document doc = null;
		Document docREP = null;
		Document docSOL = null;
		try {
			doc = documentBuilder.parse(is);
			docREP = documentBuilder.parse(isREP);
			docSOL = documentBuilder.parse(isSOL);
		} catch (SAXException e) {
			e.printStackTrace();
		}
		DOMImplementationLS impl = (DOMImplementationLS)doc.getImplementation().getFeature("LS", "3.0");
		LSSerializer serial = impl.createLSSerializer();
		serial.getDomConfig().setParameter("xml-declaration", true);
		
		System.out.println("fjsfoigb");
		
		Element root = doc.getDocumentElement();
		Element rootREP = docREP.getDocumentElement();
		Element rootSOL = docSOL.getDocumentElement();
		
		try {
			c=SqlUtils.getConnection(getServletContext());
			int aRenvoyer = comparaisonVraiFaux(rootREP, rootSOL, c);
			response.getWriter().print(aRenvoyer);
		} catch (TransformerFactoryConfigurationError e) {
			e.printStackTrace();
		} catch (TransformerException e) {
			e.printStackTrace();
		} catch (MimeTypeParseException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
    }
}
