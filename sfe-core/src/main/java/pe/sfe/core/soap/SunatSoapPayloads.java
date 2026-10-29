package pe.sfe.core.soap;

import java.util.Base64;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import pe.sfe.core.xml.SecureXml;

/**
 * Cuerpos SOAP de billService construidos como DOM (no strings) y extraccion de las respuestas.
 * El encabezado WS-Security lo agrega Wss4jSecurityInterceptor en el cliente Spring-WS.
 */
public final class SunatSoapPayloads {

    public static final String NS = "http://service.sunat.gob.pe";

    private SunatSoapPayloads() {
    }

    public static Document sendBill(String fileName, byte[] zip) {
        return archivo("sendBill", fileName, zip);
    }

    public static Document sendSummary(String fileName, byte[] zip) {
        return archivo("sendSummary", fileName, zip);
    }

    public static Document getStatus(String ticket) {
        Document d = SecureXml.newDocument();
        Element op = operacion(d, "getStatus");
        hijo(d, op, "ticket", ticket);
        return d;
    }

    public static Document getStatusCdr(String ruc, String tipo, String serie, long numero) {
        Document d = SecureXml.newDocument();
        Element op = operacion(d, "getStatusCdr");
        hijo(d, op, "rucComprobante", ruc);
        hijo(d, op, "tipoComprobante", tipo);
        hijo(d, op, "serieComprobante", serie);
        hijo(d, op, "numeroComprobante", String.valueOf(numero));
        return d;
    }

    private static Document archivo(String operacion, String fileName, byte[] zip) {
        Document d = SecureXml.newDocument();
        Element op = operacion(d, operacion);
        hijo(d, op, "fileName", fileName);
        hijo(d, op, "contentFile", Base64.getEncoder().encodeToString(zip));
        return d;
    }

    private static Element operacion(Document d, String nombre) {
        Element op = d.createElementNS(NS, "ser:" + nombre);
        op.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:ser", NS);
        d.appendChild(op);
        return op;
    }

    private static void hijo(Document d, Element padre, String nombre, String valor) {
        Element e = d.createElementNS(null, nombre);
        e.setTextContent(valor);
        padre.appendChild(e);
    }

    // ------------------------------------------------------------------ respuestas

    /** Source de la respuesta SOAP (body) -> DOM, sin construcciones peligrosas. */
    public static Document aDom(Source source) {
        try {
            DOMResult res = new DOMResult();
            SecureXml.transformerFactory().newTransformer().transform(source, res);
            return (Document) res.getNode();
        } catch (TransformerException e) {
            throw new IllegalArgumentException("Respuesta SOAP ilegible", e);
        }
    }

    /**
     * sendBillResponse: el ZIP de la CDR viene en el primer hijo elemento (applicationResponse; el manual lo llama
     * "document"). Se toma el primer hijo para tolerar ambos nombres.
     */
    public static byte[] zipDeSendBillResponse(Document respuesta) {
        Element raiz = respuesta.getDocumentElement();
        Element contenido = primerHijoElemento(raiz);
        if (contenido == null || contenido.getTextContent().isBlank()) {
            throw new IllegalArgumentException("sendBillResponse sin contenido");
        }
        return Base64.getMimeDecoder().decode(contenido.getTextContent().strip());
    }

    public static String ticketDeSendSummaryResponse(Document respuesta) {
        Element e = primerHijoElemento(respuesta.getDocumentElement());
        if (e == null || e.getTextContent().isBlank()) {
            throw new IllegalArgumentException("sendSummaryResponse sin ticket");
        }
        return e.getTextContent().strip();
    }

    /** getStatusResponse/status o getStatusCdrResponse/statusCdr: statusCode, content (Base64), statusMessage. */
    public static StatusResponse status(Document respuesta) {
        Element estado = primerHijoElemento(respuesta.getDocumentElement());
        if (estado == null) {
            throw new IllegalArgumentException("Respuesta de estado vacia");
        }
        String code = null, msg = null;
        byte[] content = null;
        for (Node n = estado.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e) {
                switch (e.getLocalName()) {
                    case "statusCode" -> code = e.getTextContent().strip();
                    case "statusMessage" -> msg = e.getTextContent().strip();
                    case "content" -> {
                        String b64 = e.getTextContent().strip();
                        content = b64.isEmpty() ? null : Base64.getMimeDecoder().decode(b64);
                    }
                    default -> { /* ignorar */ }
                }
            }
        }
        return new StatusResponse(code, content, msg);
    }

    private static Element primerHijoElemento(Element padre) {
        for (Node n = padre.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e) {
                return e;
            }
        }
        return null;
    }
}
