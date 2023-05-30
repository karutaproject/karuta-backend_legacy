package com.eportfolium.karuta.data.attachment;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import javax.activation.MimeType;
import javax.activation.MimeTypeParseException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import com.eportfolium.karuta.data.provider.DataProvider;
import com.eportfolium.karuta.data.utils.DomUtils;
import com.eportfolium.karuta.data.utils.SqlUtils;
import com.eportfolium.karuta.security.Credential;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.SAXException;

public class CompareServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(CompareServlet.class);
    private static final long serialVersionUID = 9188067506635747901L;

    DataProvider dataProvider;
    boolean hasNodeReadRight = false;
    boolean hasNodeWriteRight = false;
    Credential credential;
    int userId;
    int groupId = -1;
    HttpSession session;
    static DataProvider provider;

    public void initialize(HttpServletRequest httpServletRequest) {
    }

    public static int comparaisonParCodes(String code1, String code2, Element racine, Connection c) throws TransformerFactoryConfigurationError, MimeTypeParseException, Exception {
        int pourcentage = 0;
        NodeList liste1 = racine.getElementsByTagName("code");
        List<Node> listeCode1 = new ArrayList<>();
        List<Node> listeCode2 = new ArrayList<>();
        for (int i = 0; i < liste1.getLength(); i++) {
            Node nd = liste1.item(i).getFirstChild();
            if (nd != null) {
                String txt = nd.getNodeValue();
                if (txt.equals(code1)) {
                    listeCode1.add(liste1.item(i));
                } else if (txt.equals(code2)) {
                    listeCode2.add(liste1.item(i));
                }
            }
        }

        if (listeCode1.size() == 1 && listeCode2.size() == 1) {
            Node parNd1 = listeCode1.get(0).getParentNode().getParentNode();
            Node parNd2 = listeCode2.get(0).getParentNode().getParentNode();

            pourcentage = comparaisonVraiFaux(parNd1, parNd2, c);


        } else if (listeCode1.size() == 0 | listeCode2.size() == 0) {
            logger.warn("Aucune donnée à comparer");
            //TODO something is missing
        } else {
            logger.warn("Impossible de comparer plus de deux données");
            //TODO something is missing
        }
        return pourcentage;
    }


    public static int comparaisonVraiFaux(Node nd1, Node nd2, Connection c) throws TransformerFactoryConfigurationError, MimeTypeParseException, Exception {
        int prct = 0;
        int nbBR = 0;
        int nbComparaison = 0;
        boolean similaire = false;
        Element elt1 = (Element) nd1;
        Element elt2 = (Element) nd2;
        NodeList ndL1 = elt1.getElementsByTagName("code");
        NodeList ndL2 = elt2.getElementsByTagName("code");
        for (int i = 0; i < ndL1.getLength(); i++) {
            for (int j = 0; j < ndL2.getLength(); j++) {

                if (ndL1.item(i).hasChildNodes() && ndL2.item(j).hasChildNodes()) {

                    String cd1 = ndL1.item(i).getFirstChild().getNodeValue().trim();
                    String cd2 = ndL2.item(j).getFirstChild().getNodeValue().trim();

                    if (cd1.equals(cd2)) {
                        Element pndL1 = (Element) ndL1.item(i).getParentNode();
                        Element pndL2 = (Element) ndL2.item(j).getParentNode();
                        String str1 = pndL1.getAttribute("xsi_type");
                        String str2 = pndL2.getAttribute("xsi_type");

                        if (str1.equals("nodeRes") && str2.equals("nodeRes")) {
                            Element aComparer1 = (Element) pndL1.getNextSibling().getNextSibling().getFirstChild();
                            String v1 = aComparer1.getTextContent().trim();
                            Element aComparer2 = (Element) pndL2.getNextSibling().getNextSibling().getFirstChild();
                            String v2 = aComparer2.getTextContent().trim();

                            if (v1 != null && v2 != null && v1.equals(v2)) {
                                similaire = true;
                                nbComparaison = nbComparaison + 1;
                                nbBR = nbBR + 1;
                                Element n = (Element) aComparer1.getParentNode().getParentNode().getFirstChild();
                                n.setAttribute("compare", "true"); //ajout attribut compare dans metadatawad
                                StringWriter stw = new StringWriter();
                                Transformer serializer = TransformerFactory.newInstance().newTransformer();
                                serializer.transform(new DOMSource(n), new StreamResult(stw));
                                String result = stw.toString();

                                Element nP = (Element) n.getParentNode();
                                String idd = nP.getAttribute("id");

                                provider.putNodeMetadataWad(c, new MimeType("text/xml"), idd, result, 1, 0);
                            } else if ((v1 != null && v2 != null && !v1.equals(v2))) {
                                similaire = false;
                                nbComparaison = nbComparaison + 1;
                                Element n = (Element) aComparer1.getParentNode().getParentNode().getFirstChild();
                                n.setAttribute("compare", "false");
                                StringWriter stw = new StringWriter();
                                Transformer serializer = TransformerFactory.newInstance().newTransformer();
                                serializer.transform(new DOMSource(n), new StreamResult(stw));
                                String result = stw.toString();

                                Element nP = (Element) n.getParentNode();
                                String idd = nP.getAttribute("id");

                                provider.putNodeMetadataWad(c, new MimeType("text/xml"), idd, result, 1, 0);
                            } else {
                            }
                            aComparer1 = null;
                            aComparer2 = null;
                        } else {

                        }

                        pndL1 = null;
                        pndL2 = null;
                    } else {
                    }
                    cd1 = "";
                    cd2 = "ee";
                } else {
                }
            }
        }

        prct = ((100 * nbBR) / (nbComparaison));
		logger.debug(nbBR + "/" + nbComparaison + "   " + prct);

        return prct;

    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        String uuidsNds = request.getPathInfo().substring(1);

        String uuidREP = uuidsNds.substring(0, 36);
        String uuidSOL = uuidsNds.substring(36, 72);
        String uuidNd = uuidsNds.substring(72);

        Connection c;

        String lbl = null;
        Object ingt = "g";
        Object ingtREP = "";
        Object ingtSOL = "";
        try {
            provider = SqlUtils.initProvider();
            c = SqlUtils.getConnection();
            ingt = provider.getNode(c, new MimeType("text/xml"), uuidNd, true, 1, groupId, null, lbl, null); //pour test remplacer uuidNd par strNoeud
            ingtREP = provider.getNode(c, new MimeType("text/xml"), uuidREP, true, 1, groupId, null, lbl, null);
            ingtSOL = provider.getNode(c, new MimeType("text/xml"), uuidSOL, true, 1, groupId, null, lbl, null);
        } catch (Exception e) {
            logger.error("Intercepted error", e);
            // TODO managing error
        }

        //parse les données du noeud
        DocumentBuilderFactory documentBuilderFactory = DomUtils.newSecureDocumentBuilderFactory();
        DocumentBuilder documentBuilder;
        try {
            documentBuilder = documentBuilderFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
			logger.error("Creating newDocumentBuilder error", e);
            throw new ServletException(e);
        }
        ByteArrayInputStream is = new ByteArrayInputStream(ingt.toString().getBytes(StandardCharsets.UTF_8));
        ByteArrayInputStream isREP = new ByteArrayInputStream(ingtREP.toString().getBytes(StandardCharsets.UTF_8));
        ByteArrayInputStream isSOL = new ByteArrayInputStream(ingtSOL.toString().getBytes(StandardCharsets.UTF_8));
        Document doc = null;
        Document docREP = null;
        Document docSOL = null;
        try {
            doc = documentBuilder.parse(is);
            docREP = documentBuilder.parse(isREP);
            docSOL = documentBuilder.parse(isSOL);
        } catch (SAXException e) {
			logger.error("Intercepted error", e);
            //TODO something is missing
        }
        DOMImplementationLS impl = (DOMImplementationLS) doc.getImplementation().getFeature("LS", "3.0");
        LSSerializer serial = impl.createLSSerializer();
        serial.getDomConfig().setParameter("xml-declaration", true);

        Element root = doc.getDocumentElement();
        Element rootREP = docREP.getDocumentElement();
        Element rootSOL = docSOL.getDocumentElement();

        try {
            c = SqlUtils.getConnection();
            int aRenvoyer = comparaisonVraiFaux(rootREP, rootSOL, c);
            response.getWriter().print(aRenvoyer);
        } catch (Exception e) {
			logger.error("Intercepted error", e);
            //TODO something is missing
        }
    }
}