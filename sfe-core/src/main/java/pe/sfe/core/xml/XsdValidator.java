package pe.sfe.core.xml;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import pe.sfe.core.domain.TipoComprobante;
import pe.sfe.core.xml.ubl.Ns;

/**
 * Validacion contra los XSD oficiales de UBL 2.1 (RNF-04, PA-01). Los esquemas se cargan UNA vez desde el classpath
 * mediante un {@link LSResourceResolver} propio, de modo que ACCESS_EXTERNAL_SCHEMA queda vacio (sin acceso a red ni a
 * ningun protocolo) y funciona igual con clases sueltas, jar normal o jar anidado de Spring Boot ("nested:").
 *
 * Nota: UBL exige un hijo dentro de ext:ExtensionContent (xsd:any minOccurs=1). Antes de firmar ese nodo esta vacio,
 * por eso {@link #validarAntesDeFirmar} valida una COPIA con un marcador ds:Signature y deja el original intacto.
 */
public final class XsdValidator {

    private static final String BASE = "/xsd/ubl21/maindoc/";
    private static final String PREFIJO = "classpath:";

    private final Map<TipoComprobante, Schema> schemas = new EnumMap<>(TipoComprobante.class);

    public XsdValidator() {
        Schema invoice = cargar("UBL-Invoice-2.1.xsd");
        schemas.put(TipoComprobante.FACTURA, invoice);
        schemas.put(TipoComprobante.BOLETA, invoice);
        schemas.put(TipoComprobante.NOTA_CREDITO, cargar("UBL-CreditNote-2.1.xsd"));
        schemas.put(TipoComprobante.NOTA_DEBITO, cargar("UBL-DebitNote-2.1.xsd"));
    }

    private static Schema cargar(String archivo) {
        String ruta = BASE + archivo;
        try (InputStream in = XsdValidator.class.getResourceAsStream(ruta)) {
            if (in == null) {
                throw new IllegalStateException("XSD no encontrado en el classpath: " + ruta);
            }
            SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            sf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            sf.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            sf.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            sf.setResourceResolver(new ResolvedorClasspath());
            return sf.newSchema(new StreamSource(in, PREFIJO + ruta));
        } catch (SAXException | IOException e) {
            throw new IllegalStateException("No se pudo compilar el XSD " + archivo, e);
        }
    }

    /** Resuelve xsd:import / xsd:include relativos SOLO dentro del classpath (nunca red ni disco). */
    private static final class ResolvedorClasspath implements LSResourceResolver {
        @Override
        public LSInput resolveResource(String type, String namespaceURI, String publicId, String systemId,
                                       String baseURI) {
            if (systemId == null) {
                return null;
            }
            try {
                URI base = URI.create(baseURI != null && baseURI.startsWith(PREFIJO) ? baseURI : PREFIJO + BASE + "x.xsd");
                String ruta = base.resolve(systemId).getPath();
                InputStream in = XsdValidator.class.getResourceAsStream(ruta);
                if (in == null) {
                    return null;
                }
                return new EntradaClasspath(in, PREFIJO + ruta, publicId);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    private record EntradaClasspath(InputStream flujo, String id, String publico) implements LSInput {
        @Override public Reader getCharacterStream() { return null; }
        @Override public void setCharacterStream(Reader r) { }
        @Override public InputStream getByteStream() { return flujo; }
        @Override public void setByteStream(InputStream s) { }
        @Override public String getStringData() { return null; }
        @Override public void setStringData(String s) { }
        @Override public String getSystemId() { return id; }
        @Override public void setSystemId(String s) { }
        @Override public String getPublicId() { return publico; }
        @Override public void setPublicId(String s) { }
        @Override public String getBaseURI() { return null; }
        @Override public void setBaseURI(String s) { }
        @Override public String getEncoding() { return null; }
        @Override public void setEncoding(String s) { }
        @Override public boolean getCertifiedText() { return false; }
        @Override public void setCertifiedText(boolean b) { }
    }

    /** Valida el documento tal cual (p. ej. ya firmado). */
    public void validar(TipoComprobante tipo, Document doc) {
        List<String> errores = new ArrayList<>();
        try {
            Validator v = schemas.get(tipo).newValidator();
            v.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            v.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            v.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            v.setErrorHandler(new Acumulador(errores));
            v.validate(new DOMSource(doc));
        } catch (SAXException e) {
            errores.add(e.getMessage());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        if (!errores.isEmpty()) {
            throw new XsdValidationException(errores);
        }
    }

    /** Valida una copia con marcador de firma (no ds:Signature); no modifica {@code doc}. */
    public void validarAntesDeFirmar(TipoComprobante tipo, Document doc) {
        Document copia = (Document) doc.cloneNode(true);
        NodeList nl = copia.getElementsByTagNameNS(Ns.EXT, "ExtensionContent");
        if (nl.getLength() == 1) {
            // Elemento de otro namespace SIN esquema: cumple xsd:any "##other lax" sin intentar validarse como firma real.
            Element marcador = copia.createElementNS("urn:sfe:firma-pendiente", "sfe:marcador");
            nl.item(0).appendChild(marcador);
        }
        validar(tipo, copia);
    }

    private record Acumulador(List<String> errores) implements ErrorHandler {
        @Override public void warning(SAXParseException e) { /* ignorar advertencias */ }
        @Override public void error(SAXParseException e) { errores.add(formato(e)); }
        @Override public void fatalError(SAXParseException e) { errores.add(formato(e)); }
        private static String formato(SAXParseException e) {
            return "linea " + e.getLineNumber() + ": " + e.getMessage();
        }
    }

    public static class XsdValidationException extends RuntimeException {
        private final transient List<String> errores;

        public XsdValidationException(List<String> errores) {
            super("XML UBL no cumple el XSD: " + String.join(" | ", errores));
            this.errores = List.copyOf(errores);
        }

        public List<String> errores() {
            return errores;
        }
    }
}