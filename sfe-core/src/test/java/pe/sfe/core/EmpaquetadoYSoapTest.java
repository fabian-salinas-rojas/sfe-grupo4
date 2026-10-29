package pe.sfe.core;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.transform.dom.DOMSource;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import pe.sfe.core.domain.TipoComprobante;
import pe.sfe.core.pack.NombreArchivo;
import pe.sfe.core.pack.ZipPackager;
import pe.sfe.core.security.Secret;
import pe.sfe.core.sign.CertificadoNoVigenteException;
import pe.sfe.core.sign.SigningKey;
import pe.sfe.core.soap.StatusResponse;
import pe.sfe.core.soap.SunatSoapPayloads;
import pe.sfe.core.support.Fixtures;
import pe.sfe.core.xml.SecureXml;

/** CP-SOAP-03 (cuerpos SOAP segun manual), nombres de archivo, ZIP y certificados. */
class EmpaquetadoYSoapTest {

    @Test
    void nombresDeArchivoSegunManual() {
        var n = NombreArchivo.comprobante("20100066603", TipoComprobante.FACTURA, "F001", 1);
        assertEquals("20100066603-01-F001-1.zip", n.zip());
        assertEquals("20100066603-01-F001-1.xml", n.xml());
        assertEquals("20100066603-07-FC01-12.xml", NombreArchivo.comprobante("20100066603", TipoComprobante.NOTA_CREDITO, "FC01", 12).xml());
        assertThrows(IllegalArgumentException.class, () -> NombreArchivo.comprobante("123", TipoComprobante.FACTURA, "F001", 1));
        assertThrows(IllegalArgumentException.class, () -> NombreArchivo.comprobante("20100066603", TipoComprobante.FACTURA, "X001", 1));
        assertThrows(IllegalArgumentException.class, () -> NombreArchivo.comprobante("20100066603", TipoComprobante.FACTURA, "F001", 100_000_000L));
    }

    @Test
    void zipNoSeTruncaConXmlGrande() throws Exception {
        byte[] xml = new byte[2_000_000];
        new java.util.Random(7).nextBytes(xml); // incompresible: el caso que mas facilmente trunca
        var n = NombreArchivo.comprobante("20100066603", TipoComprobante.FACTURA, "F001", 1);
        byte[] zip = ZipPackager.empaquetar(n, xml, false);
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e = z.getNextEntry();
            assertEquals(n.xml(), e.getName());
            assertArrayEquals(xml, z.readAllBytes());
            assertNull(z.getNextEntry());
        }
    }

    @Test
    void sendBillConFormaDelManual() throws Exception {
        byte[] zip = {1, 2, 3, 4};
        Document d = SunatSoapPayloads.sendBill("20100066603-01-F001-1.zip", zip);
        var raiz = d.getDocumentElement();
        assertEquals("sendBill", raiz.getLocalName());
        assertEquals("http://service.sunat.gob.pe", raiz.getNamespaceURI());
        assertEquals("20100066603-01-F001-1.zip", raiz.getElementsByTagName("fileName").item(0).getTextContent());
        assertEquals(Base64.getEncoder().encodeToString(zip), raiz.getElementsByTagName("contentFile").item(0).getTextContent());
        // el cuerpo se serializa sin concatenar strings y es XML bien formado
        byte[] bytes = pe.sfe.core.xml.XmlSerializer.toBytes(d, StandardCharsets.UTF_8);
        assertEquals("sendBill", SecureXml.parse(bytes).getDocumentElement().getLocalName());
    }

    @Test
    void getStatusCdrConLosCuatroCamposDelManual() {
        var op = SunatSoapPayloads.getStatusCdr("20520485750", "01", "F022", 7782).getDocumentElement();
        assertEquals("getStatusCdr", op.getLocalName());
        assertEquals("20520485750", op.getElementsByTagName("rucComprobante").item(0).getTextContent());
        assertEquals("01", op.getElementsByTagName("tipoComprobante").item(0).getTextContent());
        assertEquals("F022", op.getElementsByTagName("serieComprobante").item(0).getTextContent());
        assertEquals("7782", op.getElementsByTagName("numeroComprobante").item(0).getTextContent());
        assertEquals("201100000011227", SunatSoapPayloads.getStatus("201100000011227").getDocumentElement()
                .getElementsByTagName("ticket").item(0).getTextContent());
    }

    @Test
    void respuestasSoapSeInterpretan() {
        String zipB64 = Base64.getEncoder().encodeToString("ZIPDATA".getBytes());
        String sendBill = "<br:sendBillResponse xmlns:br=\"http://service.sunat.gob.pe\"><applicationResponse>" + zipB64
                + "</applicationResponse></br:sendBillResponse>";
        assertArrayEquals("ZIPDATA".getBytes(), SunatSoapPayloads.zipDeSendBillResponse(SecureXml.parse(sendBill.getBytes())));

        String status = "<ns2:getStatusResponse xmlns:ns2=\"http://service.sunat.gob.pe\"><status><content>" + zipB64
                + "</content><statusCode>0</statusCode></status></ns2:getStatusResponse>";
        StatusResponse s = SunatSoapPayloads.status(SecureXml.parse(status.getBytes()));
        assertEquals("0", s.statusCode());
        assertArrayEquals("ZIPDATA".getBytes(), s.content());

        String cdr = "<ns0:getStatusCdrResponse xmlns:ns0=\"http://service.sunat.gob.pe\"><statusCdr><statusCode>0001</statusCode>"
                + "<statusMessage>El comprobante existe y esta aceptado.</statusMessage></statusCdr></ns0:getStatusCdrResponse>";
        StatusResponse c = SunatSoapPayloads.status(SecureXml.parse(cdr.getBytes()));
        assertEquals("0001", c.statusCode());
        assertNull(c.content());
    }

    @Test
    void aDomRechazaDoctypeEnRespuestas() {
        var src = new javax.xml.transform.stream.StreamSource(new java.io.StringReader(
                "<!DOCTYPE r [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><r>&x;</r>"));
        assertThrows(RuntimeException.class, () -> SunatSoapPayloads.aDom(src));
    }

    // ---------------------------------------------------------------- certificados
    @Test
    void certificadoVencidoOaunNoVigenteSeRechaza() {
        var vencido = SigningKey.fromPkcs12(Fixtures.pkcs12("2020/01/01 00:00:00", 30), Fixtures.PASS);
        assertThrows(CertificadoNoVigenteException.class, () -> vencido.assertVigente(java.time.Clock.systemUTC()));
        assertDoesNotThrow(() -> Fixtures.llaveVigente().assertVigente(java.time.Clock.systemUTC()));
    }

    @Test
    void elRucDebeConstarEnElCampoOuDelCertificado() {
        var k = Fixtures.llaveVigente();
        assertTrue(k.perteneceARuc(Fixtures.RUC));
        assertFalse(k.perteneceARuc("20100066603"));
    }

    @Test
    void contrasenaIncorrectaNoSeFiltraEnElMensaje() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> SigningKey.fromPkcs12(Fixtures.pkcs12(null, 30), Secret.of("clave-equivocada-XYZ")));
        assertFalse(ex.getMessage().contains("clave-equivocada-XYZ"));
        assertFalse(Fixtures.llaveVigente().toString().toLowerCase().contains("private key"));
    }

    @Test
    void firmaSinLineBreaksCRLF() throws Exception {
        var p = new pe.sfe.core.app.EmisionPipeline(new pe.sfe.core.xml.UblXmlBuilder(), new pe.sfe.core.xml.XsdValidator(),
                new pe.sfe.core.sign.XmlSigner(), pe.sfe.core.sign.SignatureAlgo.SHA256, false,
                java.time.Clock.systemUTC());
        var g = p.generar(Fixtures.boleta(), Fixtures.llaveVigente());
        assertFalse(new String(g.xmlFirmado(), StandardCharsets.ISO_8859_1).contains("&#13;"));
    }
}
