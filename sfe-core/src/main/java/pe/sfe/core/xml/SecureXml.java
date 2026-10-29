package pe.sfe.core.xml;

import java.io.ByteArrayInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerFactory;
import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Unico punto donde se crean parsers y transformers XML. Todo parseo de XML externo (CDR de SUNAT, respuestas SOAP)
 * pasa por aqui: DOCTYPE prohibido (disallow-doctype-decl), sin entidades externas ni XInclude (defensa XXE).
 */
public final class SecureXml {

    private SecureXml() {
    }

    private static DocumentBuilderFactory factory() {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            return f;
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("El parser XML del JDK no soporta la configuracion segura requerida", e);
        }
    }

    private static DocumentBuilder builder() {
        try {
            DocumentBuilder b = factory().newDocumentBuilder();
            b.setErrorHandler(new ErrorHandler() {
                @Override public void warning(SAXParseException e) { /* ignorar */ }
                @Override public void error(SAXParseException e) throws SAXException { throw e; }
                @Override public void fatalError(SAXParseException e) throws SAXException { throw e; }
            });
            return b;
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Document newDocument() {
        return builder().newDocument();
    }

    /** Parsea bytes respetando la codificacion declarada en el prologo (p. ej. ISO-8859-1). */
    public static Document parse(byte[] xml) {
        try {
            return builder().parse(new ByteArrayInputStream(xml));
        } catch (SAXException e) {
            throw new XmlSeguridadException("XML invalido o con construcciones no permitidas (DOCTYPE/entidades): "
                    + e.getMessage(), e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static TransformerFactory transformerFactory() {
        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            return tf;
        } catch (javax.xml.transform.TransformerConfigurationException | IllegalArgumentException e) {
            throw new IllegalStateException("TransformerFactory sin soporte de configuracion segura", e);
        }
    }

    public static class XmlSeguridadException extends RuntimeException {
        public XmlSeguridadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
