package pe.sfe.core.cdr;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import pe.sfe.core.sign.XmlSigner;
import pe.sfe.core.xml.SecureXml;
import pe.sfe.core.xml.ubl.Ns;

/** Interpreta el XML de la CDR con un parser seguro (sin DOCTYPE ni entidades externas: defensa XXE). */
public final class CdrParser {

    private static final Pattern NOTA = Pattern.compile("^\\s*(\\d{3,4})\\s*-\\s*(.*)$", Pattern.DOTALL);

    private final XmlSigner signer;

    public CdrParser(XmlSigner signer) {
        this.signer = signer;
    }

    public Cdr parse(byte[] xml) {
        Document doc = SecureXml.parse(xml);
        Element raiz = doc.getDocumentElement();
        if (!"ApplicationResponse".equals(raiz.getLocalName())) {
            throw new IllegalArgumentException("El XML no es un ApplicationResponse: " + raiz.getLocalName());
        }
        String idProceso = textoHijo(raiz, "ID");
        List<Cdr.Observacion> notas = new ArrayList<>();
        for (Node n = raiz.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && Ns.CBC.equals(e.getNamespaceURI()) && "Note".equals(e.getLocalName())) {
                notas.add(parseNota(e.getTextContent()));
            }
        }
        Element resp = primero(doc, Ns_CAC, "Response");
        if (resp == null) {
            throw new IllegalArgumentException("CDR sin cac:DocumentResponse/cac:Response");
        }
        String referencia = textoHijo(resp, "ReferenceID");
        String codigo = textoHijo(resp, "ResponseCode");
        String descripcion = textoHijo(resp, "Description");
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("CDR sin cbc:ResponseCode");
        }
        boolean firma;
        try {
            firma = signer.verificar(xml, false);
        } catch (RuntimeException e) {
            firma = false;
        }
        return new Cdr(idProceso, referencia, codigo.trim(), descripcion, List.copyOf(notas), firma);
    }

    private static final String Ns_CAC = Ns.CAC;

    private static Cdr.Observacion parseNota(String texto) {
        Matcher m = NOTA.matcher(texto);
        return m.matches() ? new Cdr.Observacion(m.group(1), m.group(2).strip())
                : new Cdr.Observacion("", texto.strip());
    }

    private static Element primero(Document doc, String ns, String local) {
        NodeList nl = doc.getElementsByTagNameNS(ns, local);
        return nl.getLength() == 0 ? null : (Element) nl.item(0);
    }

    /** Texto del primer hijo DIRECTO cbc:{local}. */
    private static String textoHijo(Element padre, String local) {
        for (Node n = padre.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && Ns.CBC.equals(e.getNamespaceURI()) && local.equals(e.getLocalName())) {
                return e.getTextContent().strip();
            }
        }
        return null;
    }
}
