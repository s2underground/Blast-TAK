package com.atakmap.android.blast.plugin.desktop.net;

import java.io.StringReader;
import java.io.StringWriter;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

/**
 * One received CoT event, read from XML with no DTDs or entities: the
 * parts the desktop uses. Received data is untrusted, so anything
 * malformed gives null and sizes are capped.
 */
public final class CotIn {

    /** Larger events aren't read (a datagram is at most 64 KB). */
    public static final int MAX_CHARS = 1 << 20;

    public String uid, type, how;
    public double lat = Double.NaN, lon = Double.NaN;
    /** Stale time, ms (0 if missing). */
    public long staleMs;
    /** From &lt;contact&gt;: callsign and endpoint ("ip:port:proto"). */
    public String callsign, endpoint;
    /** From &lt;__group&gt;. */
    public String team, role;
    /** From &lt;creator&gt; (who made the item) and &lt;link&gt; (delete). */
    public String creatorUid, linkUid;
    /** A child of &lt;detail&gt; by name, serialized, or null. */
    private Element detail;

    private CotIn() {
    }

    /** The XML of the detail child named {@code name}, or null. */
    public String detailXml(String name) {
        Element e = child(detail, name);
        return e == null ? null : serialize(e);
    }

    public boolean hasDetail(String name) {
        return child(detail, name) != null;
    }

    /** Parse an event; null if it isn't a usable one. */
    public static CotIn parse(String xml) {
        if (xml == null || xml.length() > MAX_CHARS)
            return null;
        try {
            Document doc = builder().parse(new InputSource(new StringReader(
                    xml)));
            Element ev = doc.getDocumentElement();
            if (ev == null || !"event".equals(ev.getTagName()))
                return null;
            CotIn c = new CotIn();
            c.uid = ev.getAttribute("uid");
            c.type = ev.getAttribute("type");
            c.how = ev.getAttribute("how");
            if (c.uid.isEmpty() || c.uid.length() > 256 || c.type.isEmpty())
                return null;
            c.staleMs = time(ev.getAttribute("stale"));
            Element pt = child(ev, "point");
            if (pt != null) {
                c.lat = number(pt.getAttribute("lat"));
                c.lon = number(pt.getAttribute("lon"));
            }
            c.detail = child(ev, "detail");
            Element contact = child(c.detail, "contact");
            if (contact != null) {
                c.callsign = attr(contact, "callsign");
                c.endpoint = attr(contact, "endpoint");
            }
            Element group = child(c.detail, "__group");
            if (group != null) {
                c.team = attr(group, "name");
                c.role = attr(group, "role");
            }
            Element creator = child(c.detail, "creator");
            if (creator != null)
                c.creatorUid = attr(creator, "uid");
            Element link = child(c.detail, "link");
            if (link != null)
                c.linkUid = attr(link, "uid");
            return c;
        } catch (Exception e) {
            return null;
        }
    }

    public boolean hasPosition() {
        return lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
    }

    private static String attr(Element e, String name) {
        return e.hasAttribute(name) ? e.getAttribute(name) : null;
    }

    private static Element child(Element parent, String name) {
        if (parent == null)
            return null;
        NodeList l = parent.getChildNodes();
        for (int i = 0; i < l.getLength(); i++) {
            Node n = l.item(i);
            if (n instanceof Element && name.equals(((Element) n)
                    .getTagName()))
                return (Element) n;
        }
        return null;
    }

    private static double number(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static long time(String s) {
        if (s == null || s.isEmpty())
            return 0;
        String[] fmts = { "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'" };
        String t = s;
        // More than milliseconds: keep three digits.
        int dot = t.indexOf('.');
        if (dot > 0 && t.endsWith("Z") && t.length() - dot - 2 > 3)
            t = t.substring(0, dot + 4) + "Z";
        for (String f : fmts) {
            SimpleDateFormat p = new SimpleDateFormat(f, Locale.US);
            p.setTimeZone(TimeZone.getTimeZone("UTC"));
            p.setLenient(false);
            ParsePosition pos = new ParsePosition(0);
            Date d = p.parse(t, pos);
            if (d != null && pos.getIndex() == t.length())
                return d.getTime();
        }
        return 0;
    }

    private static DocumentBuilder builder() throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",
                true);
        f.setFeature("http://xml.org/sax/features/external-general-entities",
                false);
        f.setFeature(
                "http://xml.org/sax/features/external-parameter-entities",
                false);
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        f.setNamespaceAware(false);
        DocumentBuilder b = f.newDocumentBuilder();
        b.setErrorHandler(new ErrorHandler() {
            @Override
            public void warning(SAXParseException e) {
            }

            @Override
            public void error(SAXParseException e) throws SAXParseException {
                throw e;
            }

            @Override
            public void fatalError(SAXParseException e)
                    throws SAXParseException {
                throw e;
            }
        });
        return b;
    }

    private static String serialize(Element e) {
        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            Transformer t = tf.newTransformer();
            t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter w = new StringWriter();
            t.transform(new DOMSource(e), new StreamResult(w));
            return w.toString();
        } catch (Exception ex) {
            return null;
        }
    }
}
