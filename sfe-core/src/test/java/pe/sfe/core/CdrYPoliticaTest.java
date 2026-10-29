package pe.sfe.core;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import pe.sfe.core.cdr.Cdr;
import pe.sfe.core.cdr.CdrParser;
import pe.sfe.core.pack.ZipReader;
import pe.sfe.core.policy.Backoff;
import pe.sfe.core.policy.EnvioClassifier;
import pe.sfe.core.policy.ResultadoEnvio;
import pe.sfe.core.sign.XmlSigner;
import pe.sfe.core.soap.SunatFaultException;
import pe.sfe.core.soap.SunatTransportException;
import pe.sfe.core.xml.SecureXml;

/** CP-CDR-04 (lectura de la constancia), politica de reintentos y defensa XXE. */
class CdrYPoliticaTest {

    static final String CDR_NS = "xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:ApplicationResponse-2\" "
            + "xmlns:cac=\"urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2\" "
            + "xmlns:cbc=\"urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2\" "
            + "xmlns:ext=\"urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2\"";

    /** Basado en C.1 / C.2 del manual de SUNAT (con los namespaces declarados correctamente). */
    static byte[] cdr(String codigo, String descripcion, String... notas) {
        StringBuilder n = new StringBuilder();
        for (String s : notas) n.append("<cbc:Note>").append(s).append("</cbc:Note>");
        return ("<?xml version=\"1.0\" encoding=\"ISO-8859-1\" standalone=\"no\"?><ApplicationResponse " + CDR_NS + ">"
                + "<cbc:UBLVersionID>2.0</cbc:UBLVersionID><cbc:CustomizationID>1.0</cbc:CustomizationID>"
                + "<cbc:ID>201200000230061</cbc:ID><cbc:IssueDate>2012-06-12</cbc:IssueDate>" + n
                + "<cac:DocumentResponse><cac:Response><cbc:ReferenceID>FA01-981</cbc:ReferenceID>"
                + "<cbc:ResponseCode>" + codigo + "</cbc:ResponseCode><cbc:Description>" + descripcion + "</cbc:Description>"
                + "</cac:Response><cac:DocumentReference><cbc:ID>FA01-981</cbc:ID></cac:DocumentReference></cac:DocumentResponse>"
                + "</ApplicationResponse>").getBytes(StandardCharsets.ISO_8859_1);
    }

    static byte[] zip(String nombre, byte[] contenido) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            try (ZipOutputStream z = new ZipOutputStream(b)) {
                z.putNextEntry(new ZipEntry("dummy/")); z.closeEntry();
                z.putNextEntry(new ZipEntry(nombre)); z.write(contenido); z.closeEntry();
            }
            return b.toByteArray();
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    final EnvioClassifier clf = new EnvioClassifier(new CdrParser(new XmlSigner()));

    @Test
    void cdrAceptadoConObservacionesDelManual() {
        Cdr c = new CdrParser(new XmlSigner()).parse(cdr("0", "La Factura numero FA01-981, ha sido aceptada",
                "4031 - Debe indicar el nombre comercial", "4001 - El numero de RUC del receptor no existe."));
        assertTrue(c.aceptado());
        assertEquals("201200000230061", c.idProceso());
        assertEquals("FA01-981", c.referenciaDocumento());
        assertEquals(List.of(new Cdr.Observacion("4031", "Debe indicar el nombre comercial"),
                new Cdr.Observacion("4001", "El numero de RUC del receptor no existe.")), c.observaciones());
        assertInstanceOf(ResultadoEnvio.AceptadoConObservaciones.class, clf.deCdr(c, new byte[0]));
    }

    @Test
    void cdrAceptadoSinNotas() {
        var r = clf.deCdrZip(zip("R-20608945123-01-F001-1.xml", cdr("0", "aceptada")));
        assertInstanceOf(ResultadoEnvio.Aceptado.class, r);
        assertEquals("ACEPTADO", r.estado().name());
    }

    @Test
    void cdrRechazadoNormativoNuncaSeReintenta() {
        var r = clf.deCdrZip(zip("R-x.xml", cdr("2047", "Es obligatorio al menos un AdditionalMonetaryTotal con codigo 1001, 1002 o 1003")));
        var rech = assertInstanceOf(ResultadoEnvio.Rechazado.class, r);
        assertEquals("2047", rech.codigo());
        assertEquals("RECHAZADO", r.estado().name());
        for (String cod : new String[]{"2000", "2335", "3999"}) {
            assertInstanceOf(ResultadoEnvio.Rechazado.class, clf.deCdrZip(zip("R-x.xml", cdr(cod, "x"))));
        }
    }

    @Test
    void rn05CualquierCodigoDistintoDeCeroEnUnaCdrEsRechazoSalvoExcepcionesDeSunat() {
        assertInstanceOf(ResultadoEnvio.Reintentar.class, clf.deCdrZip(zip("R-x.xml", cdr("0135", "SUNAT no disponible")))); // 100-999
        assertInstanceOf(ResultadoEnvio.Rechazado.class, clf.deCdrZip(zip("R-x.xml", cdr("1033", "x"))));
        assertInstanceOf(ResultadoEnvio.Rechazado.class, clf.deCdrZip(zip("R-x.xml", cdr("E123", "codigo atipico"))));
        assertInstanceOf(ResultadoEnvio.Rechazado.class, clf.deCdrZip(zip("R-x.xml", cdr("ABC", "codigo raro"))));
    }

    @Test
    void cdrIlegibleSeReintenta() {
        assertInstanceOf(ResultadoEnvio.Reintentar.class, clf.deCdrZip("no es un zip".getBytes()));
        assertInstanceOf(ResultadoEnvio.Reintentar.class, clf.deCdrZip(zip("R-x.xml", "<x/>".getBytes())));
    }

    // ------------------------------------------------------------ fallas
    @Test
    void redTimeoutYFaultServerSeReintentan() {
        assertInstanceOf(ResultadoEnvio.Reintentar.class, clf.deFalla(new SunatTransportException("x", new SocketTimeoutException("Read timed out"))));
        assertInstanceOf(ResultadoEnvio.Reintentar.class, clf.deFalla(new RuntimeException("envoltorio", new IOException("Connection reset"))));
        assertInstanceOf(ResultadoEnvio.Reintentar.class, clf.deFalla(new SunatFaultException("soap-env:Server", "error interno")));
        assertInstanceOf(ResultadoEnvio.Reintentar.class, clf.deFalla(new SunatFaultException("soap-env:Server.0135", "no disponible")));
        assertInstanceOf(ResultadoEnvio.Reintentar.class, clf.deFalla(new IllegalStateException("bug inesperado")));
    }

    @Test
    void faultConCodigoDeRechazoOExcepcionDelContribuyenteNoSeReintenta() {
        assertInstanceOf(ResultadoEnvio.Rechazado.class, clf.deFalla(new SunatFaultException("soap-env:Client.2335", "doc rechazado")));
        // el codigo numerico manda sobre el prefijo: un rechazo jamas se reintenta aunque diga Server
        assertInstanceOf(ResultadoEnvio.Rechazado.class, clf.deFalla(new SunatFaultException("soap-env:Server.2800", "x")));
        assertInstanceOf(ResultadoEnvio.Excepcion.class, clf.deFalla(new SunatFaultException("soap-env:Client.1033", "x")));
        assertInstanceOf(ResultadoEnvio.Excepcion.class, clf.deFalla(new SunatFaultException("soap-env:Client", "archivo malformado")));
        // Stack 6: solo "Fault tipo Server" se reintenta; un Client (aun con codigo 0100-0999) es documento no informado
        assertInstanceOf(ResultadoEnvio.Excepcion.class, clf.deFalla(new SunatFaultException("soap-env:Client.0150", "x")));
        assertInstanceOf(ResultadoEnvio.Excepcion.class, clf.deFalla(new SunatFaultException("soap-env:Server.1500", "codigo del contribuyente")));
        // la causa envuelta tambien se reconoce
        assertInstanceOf(ResultadoEnvio.Rechazado.class, clf.deFalla(new RuntimeException("w", new SunatFaultException("Client.3030", "x"))));
    }

    @Test
    void backoffExponencialConTopeYJitter() {
        Backoff b = new Backoff(Duration.ofSeconds(10), Duration.ofMinutes(15), 0.2);
        assertEquals(Duration.ofSeconds(10), b.retraso(1, () -> 0.5));
        assertEquals(Duration.ofSeconds(20), b.retraso(2, () -> 0.5));
        assertEquals(Duration.ofSeconds(80), b.retraso(4, () -> 0.5));
        assertEquals(Duration.ofMinutes(15), b.retraso(12, () -> 0.5));     // tope
        assertEquals(Duration.ofMinutes(15), b.retraso(1000, () -> 0.5));   // sin desbordar
        assertEquals(Duration.ofSeconds(8), b.retraso(1, () -> 0.0));       // -20%
        assertEquals(Duration.ofSeconds(12), b.retraso(1, () -> 1.0));      // +20%
    }

    // ------------------------------------------------------------ XXE / zip
    @Test
    void parserRechazaDoctypeYEntidadesExternas() {
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE r [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><r>&x;</r>";
        assertThrows(SecureXml.XmlSeguridadException.class, () -> SecureXml.parse(xxe.getBytes()));
        String laughs = "<?xml version=\"1.0\"?><!DOCTYPE l [<!ENTITY a \"aaaa\"><!ENTITY b \"&a;&a;&a;&a;\">]><l>&b;</l>";
        assertThrows(SecureXml.XmlSeguridadException.class, () -> SecureXml.parse(laughs.getBytes()));
    }

    @Test
    void cdrConDoctypeNoSeProcesa() {
        byte[] malo = ("<?xml version=\"1.0\"?><!DOCTYPE ApplicationResponse [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>"
                + "<ApplicationResponse " + CDR_NS + "><cbc:ID>&x;</cbc:ID></ApplicationResponse>").getBytes();
        assertThrows(SecureXml.XmlSeguridadException.class, () -> new CdrParser(new XmlSigner()).parse(malo));
        assertInstanceOf(ResultadoEnvio.Reintentar.class, clf.deCdrZip(zip("R-x.xml", malo)));
    }

    @Test
    void zipReaderPrefiereLaConstanciaYLimitaTamano() {
        byte[] z = zip("R-abc.xml", "<a/>".getBytes());
        assertEquals("R-abc.xml", ZipReader.primerXml(z).nombre());
        byte[] enorme = new byte[6 * 1024 * 1024];
        java.util.Arrays.fill(enorme, (byte) 65);
        assertThrows(IllegalArgumentException.class, () -> ZipReader.primerXml(zip("R-big.xml", enorme)));
        assertThrows(IllegalArgumentException.class, () -> ZipReader.primerXml(zip("nota.txt", "x".getBytes())));
    }
}
