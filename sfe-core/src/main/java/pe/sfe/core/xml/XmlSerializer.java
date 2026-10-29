package pe.sfe.core.xml;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;

/**
 * DOM -> bytes. Regla del Stack Ideal (error 2): el StreamResult recibe DIRECTAMENTE el ByteArrayOutputStream,
 * sin Writer intermedio, de modo que no hay nada que vaciar ni cerrar antes de leer los bytes
 * (un OutputStreamWriter sin flush produce XML vacio o truncado).
 */
public final class XmlSerializer {

    private XmlSerializer() {
    }

    public static byte[] toBytes(Document doc, Charset charset) {
        try {
            Transformer t = SecureXml.transformerFactory().newTransformer();
            t.setOutputProperty(OutputKeys.METHOD, "xml");
            t.setOutputProperty(OutputKeys.ENCODING, charset.name());
            t.setOutputProperty(OutputKeys.INDENT, "no");
            ByteArrayOutputStream baos = new ByteArrayOutputStream(16 * 1024);
            t.transform(new DOMSource(doc), new StreamResult(baos));
            return baos.toByteArray();
        } catch (TransformerException e) {
            throw new IllegalStateException("No se pudo serializar el XML", e);
        }
    }
}
