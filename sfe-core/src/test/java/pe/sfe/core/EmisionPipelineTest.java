package pe.sfe.core;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneId;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import pe.sfe.core.app.ComprobanteGenerado;
import pe.sfe.core.app.EmisionPipeline;
import pe.sfe.core.domain.DocumentoVenta;
import pe.sfe.core.sign.SignatureAlgo;
import pe.sfe.core.sign.SigningKey;
import pe.sfe.core.sign.XmlSigner;
import pe.sfe.core.support.Fixtures;
import pe.sfe.core.xml.SecureXml;
import pe.sfe.core.xml.UblXmlBuilder;
import pe.sfe.core.xml.XsdValidator;
import pe.sfe.core.xml.ubl.Ns;

/** CP-XML-01 (UBL valido por tipo) y CP-SIGN-02 (firma enveloped correcta, verificable e intacta). */
class EmisionPipelineTest {

    static final Clock RELOJ = Clock.system(ZoneId.of("America/Lima"));
    static SigningKey llave;
    static XsdValidator xsd;
    static UblXmlBuilder builder;
    static XmlSigner signer;

    @BeforeAll
    static void init() {
        llave = Fixtures.llaveVigente();
        xsd = new XsdValidator();
        builder = new UblXmlBuilder();
        signer = new XmlSigner();
    }

    EmisionPipeline pipeline(SignatureAlgo algo, boolean dummy) {
        return new EmisionPipeline(builder, xsd, signer, algo, dummy, RELOJ);
    }

    @Test
    void facturaBoletaNotasGeneranXmlFirmadoValidoYZip() {
        for (DocumentoVenta d : new DocumentoVenta[]{Fixtures.factura(), Fixtures.boleta(), Fixtures.notaCredito(),
                Fixtures.notaDebito()}) {
            ComprobanteGenerado g = pipeline(SignatureAlgo.SHA256, true).generar(d, llave);
            String xml = new String(g.xmlFirmado(), StandardCharsets.ISO_8859_1);
            assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"ISO-8859-1\""), xml.substring(0, 60));
            assertTrue(signer.verificar(g.xmlFirmado(), SignatureAlgo.SHA256), d.tipo() + " no verifica");
            // el XML firmado completo tambien cumple el XSD (ds:Signature entra por xsd:any lax)
            xsd.validar(d.tipo(), SecureXml.parse(g.xmlFirmado()));
            assertEquals(Fixtures.RUC + "-" + d.tipo().codigo() + "-" + d.serie() + "-" + d.correlativo() + ".zip",
                    g.nombre().zip());
        }
    }

    @Test
    void firmaEnvelopedUnicaConIdReferenciadoPorCacSignature() {
        ComprobanteGenerado g = pipeline(SignatureAlgo.SHA256, false).generar(Fixtures.factura(), llave);
        Document doc = SecureXml.parse(g.xmlFirmado());
        assertEquals(1, doc.getElementsByTagNameNS(Ns.EXT, "UBLExtension").getLength());
        NodeList sigs = doc.getElementsByTagNameNS(Ns.DS, "Signature");
        assertEquals(1, sigs.getLength());
        assertEquals("SignatureSP", ((org.w3c.dom.Element) sigs.item(0)).getAttribute("Id"));
        assertEquals("ExtensionContent", sigs.item(0).getParentNode().getLocalName());
        assertEquals("#SignatureSP", doc.getElementsByTagNameNS(Ns.CBC, "URI").item(0).getTextContent());
        assertEquals("", ((org.w3c.dom.Element) doc.getElementsByTagNameNS(Ns.DS, "Reference").item(0)).getAttribute("URI"));
    }

    @Test
    void alterarUnByteDespuesDeFirmarInvalidaLaFirma() {
        ComprobanteGenerado g = pipeline(SignatureAlgo.SHA256, false).generar(Fixtures.factura(), llave);
        String xml = new String(g.xmlFirmado(), StandardCharsets.ISO_8859_1).replace("CLIENTE EJEMPLO", "CLIENTE EJEMPLX");
        assertFalse(signer.verificar(xml.getBytes(StandardCharsets.ISO_8859_1), SignatureAlgo.SHA256));
    }

    @Test
    void xmlDelZipEsIdenticoByteAByteAlFirmado() throws Exception {
        ComprobanteGenerado g = pipeline(SignatureAlgo.SHA256, true).generar(Fixtures.factura(), llave);
        try (ZipInputStream zis = new ZipInputStream(new java.io.ByteArrayInputStream(g.zip()))) {
            ZipEntry dummy = zis.getNextEntry();
            assertEquals("dummy/", dummy.getName());
            ZipEntry xml = zis.getNextEntry();
            assertEquals(g.nombre().xml(), xml.getName());
            assertArrayEquals(g.xmlFirmado(), zis.readAllBytes(), "El ZIP debe contener los bytes firmados sin tocar");
        }
    }

    @Test
    void tildesEneYCaracteresFueraDeLatin1SeSaneanYNoRompenLaFirma() {
        DocumentoVenta base = Fixtures.factura();
        var raros = new pe.sfe.core.domain.ItemVenta("X\u201c1", "Pi\u00f1a & \u201cCaf\u00e9\u201d \u2013 5\u20ac \u0151 <b>", "NIU",
                new java.math.BigDecimal("1"), new java.math.BigDecimal("10.00"), "10");
        DocumentoVenta d = new DocumentoVenta(base.tipo(), base.serie(), base.correlativo(), base.fechaEmision(),
                base.horaEmision(), base.moneda(), base.emisor(), base.adquirente(), java.util.List.of(raros), null, null);
        ComprobanteGenerado g = pipeline(SignatureAlgo.SHA256, false).generar(d, llave);
        String xml = new String(g.xmlFirmado(), StandardCharsets.ISO_8859_1);
        assertTrue(xml.contains("Pi\u00f1a &amp; \"Caf\u00e9\" - 5EUR o &lt;b&gt;"), "texto saneado y escapado");
        assertTrue(signer.verificar(g.xmlFirmado(), SignatureAlgo.SHA256));
    }

    @Test
    void sha1ConfigurableFirmaYVerifica() {
        ComprobanteGenerado g = pipeline(SignatureAlgo.SHA1, false).generar(Fixtures.boleta(), llave);
        assertTrue(new String(g.xmlFirmado(), StandardCharsets.ISO_8859_1).contains("rsa-sha1"));
        assertTrue(signer.verificar(g.xmlFirmado(), SignatureAlgo.SHA1));
    }
}
