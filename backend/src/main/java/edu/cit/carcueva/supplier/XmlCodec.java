package edu.cit.carcueva.supplier;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Writes and reads LegacySupply's XML documents. Uses the JDK's built-in DOM
 * parser (no extra library). Package-private - no other module ever sees XML.
 */
final class XmlCodec {

    private XmlCodec() {
    }

    static String authRequest(String clientId, String apiKey) {
        return "<AuthRequest>"
                + "<ClientId>" + escape(clientId) + "</ClientId>"
                + "<ApiKey>" + escape(apiKey) + "</ApiKey>"
                + "</AuthRequest>";
    }

    static String purchaseOrder(String supplierSku, int qty, String buyerRef) {
        return "<PurchaseOrder>"
                + "<SupplierSku>" + escape(supplierSku) + "</SupplierSku>"
                + "<Qty>" + qty + "</Qty>"
                + "<BuyerRef>" + escape(buyerRef) + "</BuyerRef>"
                + "</PurchaseOrder>";
    }

    static Document parse(String xml) {
        if (xml == null || xml.isBlank()) {
            throw LegacySupplyException.malformed("empty body");
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Refuse DOCTYPEs so a hostile response can't pull in external entities (XXE).
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            throw LegacySupplyException.malformed(e.getMessage());
        }
    }

    /** Text of the first element with this tag anywhere in the document, or null. */
    static String text(Document doc, String tag) {
        NodeList nodes = doc.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent().trim();
    }

    static PurchaseOrderAck readAck(Element element) {
        return new PurchaseOrderAck(
                childText(element, "PoNumber"),
                childText(element, "StatusCode"),
                childText(element, "SupplierSku"),
                parseIntOrZero(childText(element, "Qty")),
                childText(element, "Uom"),
                childText(element, "BuyerRef"));
    }

    /** Every purchase order inside a PurchaseOrderList, whatever its wrapper element is called. */
    static List<PurchaseOrderAck> readAcks(Document doc) {
        List<PurchaseOrderAck> acks = new ArrayList<>();
        NodeList poNumbers = doc.getElementsByTagName("PoNumber");
        for (int i = 0; i < poNumbers.getLength(); i++) {
            Node parent = poNumbers.item(i).getParentNode();
            if (parent instanceof Element element) {
                acks.add(readAck(element));
            }
        }
        return acks;
    }

    /** [code, message] from an LSError body; falls back gracefully for non-XML errors (e.g. an HTML 502 page). */
    static String[] readError(String body) {
        try {
            Document doc = parse(body);
            String code = text(doc, "Code");
            String message = text(doc, "Message");
            return new String[] {code, message != null ? message : "(no message)"};
        } catch (LegacySupplyException e) {
            String snippet = body == null ? "" : body.strip();
            if (snippet.length() > 120) {
                snippet = snippet.substring(0, 120) + "...";
            }
            return new String[] {null, "Non-XML error body: " + snippet};
        }
    }

    private static String childText(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent().trim();
    }

    private static int parseIntOrZero(String value) {
        try {
            return value == null ? 0 : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
