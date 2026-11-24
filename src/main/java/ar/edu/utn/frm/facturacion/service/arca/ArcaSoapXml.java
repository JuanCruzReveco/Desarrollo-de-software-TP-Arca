package ar.edu.utn.frm.facturacion.service.arca;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

final class ArcaSoapXml {
    private ArcaSoapXml() {}

    static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    static Document parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new ArcaConexionException("ARCA devolvió un XML inválido", e);
        }
    }

    static Element first(Node root, String name) {
        NodeList nodes = ((Element) root).getElementsByTagNameNS("*", name);
        return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
    }

    static String text(Node root, String name) {
        Element found = first(root, name);
        return found == null ? null : found.getTextContent().trim();
    }

    static String required(Node root, String name) {
        String value = text(root, name);
        if (value == null || value.isBlank()) throw new ArcaConexionException("Falta " + name + " en la respuesta de ARCA");
        return value;
    }
}
