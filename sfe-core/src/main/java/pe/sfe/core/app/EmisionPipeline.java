package pe.sfe.core.app;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import org.w3c.dom.Document;
import pe.sfe.core.domain.CalculadoraTotales;
import pe.sfe.core.domain.DocumentoVenta;
import pe.sfe.core.domain.Totales;
import pe.sfe.core.pack.NombreArchivo;
import pe.sfe.core.pack.ZipPackager;
import pe.sfe.core.sign.SignatureAlgo;
import pe.sfe.core.sign.SigningKey;
import pe.sfe.core.sign.XmlSigner;
import pe.sfe.core.xml.UblXmlBuilder;
import pe.sfe.core.xml.XmlSerializer;
import pe.sfe.core.xml.XsdValidator;

/**
 * Pipeline sincrono y puro: totales -> UBL (JAXB) -> XSD -> firma XMLDSig -> bytes (una sola serializacion
 * ISO-8859-1) -> autoverificacion -> ZIP. Sin I/O de red ni de BD: se prueba entero en memoria.
 * Es seguro para uso concurrente (JAXBContext/Schema inmutables; Marshaller/Validator se crean por llamada).
 */
public final class EmisionPipeline {

    private final UblXmlBuilder builder;
    private final XsdValidator xsd;
    private final XmlSigner signer;
    private final SignatureAlgo algo;
    private final boolean carpetaDummy;
    private final Clock clock;

    public EmisionPipeline(UblXmlBuilder builder, XsdValidator xsd, XmlSigner signer, SignatureAlgo algo,
                           boolean carpetaDummy, Clock clock) {
        this.builder = builder;
        this.xsd = xsd;
        this.signer = signer;
        this.algo = algo;
        this.carpetaDummy = carpetaDummy;
        this.clock = clock;
    }

    public ComprobanteGenerado generar(DocumentoVenta doc, SigningKey key) {
        key.assertVigente(clock);
        Totales totales = CalculadoraTotales.calcular(doc.items());
        Document dom = builder.build(doc, totales);
        xsd.validarAntesDeFirmar(doc.tipo(), dom);
        signer.firmar(dom, key, algo);
        byte[] xml = XmlSerializer.toBytes(dom, StandardCharsets.ISO_8859_1);
        if (!signer.verificar(xml, algo)) {
            throw new IllegalStateException("La firma recien generada no verifica: el XML no se enviara");
        }
        NombreArchivo nombre = NombreArchivo.comprobante(doc.emisor().ruc(), doc.tipo(), doc.serie(), doc.correlativo());
        byte[] zip = ZipPackager.empaquetar(nombre, xml, carpetaDummy);
        return new ComprobanteGenerado(nombre, xml, zip, totales, sha256(xml));
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
