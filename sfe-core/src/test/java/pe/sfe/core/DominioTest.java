package pe.sfe.core;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import pe.sfe.core.domain.*;
import pe.sfe.core.security.AesGcmCipher;
import pe.sfe.core.security.Secret;
import pe.sfe.core.soap.SunatCredentials;
import pe.sfe.core.support.Fixtures;
import pe.sfe.core.text.MontoEnLetras;
import pe.sfe.core.text.TextSanitizer;

/** RN-01..RN-04, saneo de texto, monto en letras, secretos y cifrado. */
class DominioTest {

    // ---------------------------------------------------------------- RN-04 totales
    @Test
    void totalesCuadranConElDetalleYRedondeanPorLinea() {
        Totales t = CalculadoraTotales.calcular(List.of(
                Fixtures.item("A", "10", "5.00", "10"),
                Fixtures.item("B", "1", "8.474576", "10"),
                Fixtures.item("C", "2", "10.00", "20"),
                Fixtures.item("D", "1", "3.00", "30")));
        assertEquals(new BigDecimal("58.47"), t.gravadas());
        assertEquals(new BigDecimal("20.00"), t.exoneradas());
        assertEquals(new BigDecimal("3.00"), t.inafectas());
        assertEquals(new BigDecimal("10.52"), t.igv());           // 9.00 + 1.52
        assertEquals(new BigDecimal("81.47"), t.valorVenta());
        assertEquals(new BigDecimal("91.99"), t.importeTotal());
        assertEquals(new BigDecimal("5.90"), t.lineas().get(0).precioUnitarioConIgv());
        assertEquals(t.importeTotal(), t.lineas().stream().map(l -> l.valorVenta().add(l.igv()))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    // ---------------------------------------------------------------- RN-01..03
    private static SolicitudEmision sol(TipoComprobante tipo, Adquirente a, String cant, String vu) {
        return new SolicitudEmision("V-1", tipo, "PEN", a, List.of(Fixtures.item("X", cant, vu, "10")), null, null);
    }

    @Test
    void rn01FacturaExigeRucDelAdquirente() {
        var e = assertThrows(NegocioException.class,
                () -> ReglasNegocio.validar(sol(TipoComprobante.FACTURA, new Adquirente("1", "12345678", "JUAN", null), "1", "10")));
        assertTrue(e.getMessage().contains("RN-01"));
        assertDoesNotThrow(() -> ReglasNegocio.validar(
                sol(TipoComprobante.FACTURA, new Adquirente("6", "20512345678", "ACME SAC", null), "1", "10")));
        assertThrows(NegocioException.class, () -> ReglasNegocio.validar(
                sol(TipoComprobante.FACTURA, new Adquirente("6", "2051234567", "ACME SAC", null), "1", "10")));
    }

    @Test
    void rn02BoletaSobreSetecientosExigeIdentificarAlCliente() {
        // 5 x 150 = 750 + IGV > 700
        var e = assertThrows(NegocioException.class,
                () -> ReglasNegocio.validar(sol(TipoComprobante.BOLETA, Adquirente.consumidorFinal(), "5", "150")));
        assertTrue(e.getMessage().contains("RN-02"));
        assertDoesNotThrow(() -> ReglasNegocio.validar(
                sol(TipoComprobante.BOLETA, new Adquirente("1", "45678912", "JUAN PEREZ", null), "5", "150")));
        assertDoesNotThrow(() -> ReglasNegocio.validar(sol(TipoComprobante.BOLETA, Adquirente.consumidorFinal(), "1", "100")));
    }

    @Test
    void rn03NotaExigeReferenciaMotivoYCatalogo() {
        var cli = new Adquirente("6", "20512345678", "ACME SAC", null);
        var items = List.of(Fixtures.item("X", "1", "10", "10"));
        assertThrows(NegocioException.class, () -> ReglasNegocio.validar(
                new SolicitudEmision("V", TipoComprobante.NOTA_CREDITO, "PEN", cli, items, null, null)));
        assertThrows(NegocioException.class, () -> ReglasNegocio.validar(new SolicitudEmision("V", TipoComprobante.NOTA_CREDITO,
                "PEN", cli, items, new ReferenciaDocumento(TipoComprobante.FACTURA, "F001", 1), new MotivoNota("99", "x"))));
        assertThrows(NegocioException.class, () -> ReglasNegocio.validar(new SolicitudEmision("V", TipoComprobante.NOTA_CREDITO,
                "PEN", cli, items, new ReferenciaDocumento(TipoComprobante.FACTURA, "B001", 1), new MotivoNota("01", "x"))));
        var ok = new SolicitudEmision("V", TipoComprobante.NOTA_CREDITO, "PEN", cli, items,
                new ReferenciaDocumento(TipoComprobante.FACTURA, "F001", 1), new MotivoNota("01", "Anulacion"));
        assertDoesNotThrow(() -> ReglasNegocio.validar(ok));
        assertEquals('F', ok.letraSerie()); // la serie de la nota hereda F/B del documento modificado
    }

    @Test
    void validacionesBasicasDeItemsYMoneda() {
        var cli = new Adquirente("6", "20512345678", "ACME SAC", null);
        var mala = new SolicitudEmision("", TipoComprobante.FACTURA, "EUR", cli,
                List.of(new ItemVenta("c", "", "N", BigDecimal.ZERO, new BigDecimal("-1"), "99")), null, null);
        var e = assertThrows(NegocioException.class, () -> ReglasNegocio.validar(mala));
        assertTrue(e.violaciones().size() >= 6, e.getMessage());
    }

    // ---------------------------------------------------------------- texto
    @Test
    void sanitizadorLatin1() {
        assertEquals("Pi\u00f1a \"Caf\u00e9\" - 5EUR o", TextSanitizer.latin1("Pi\u00f1a \u201cCaf\u00e9\u201d \u2013 5\u20ac \u0151"));
        assertEquals("a b", TextSanitizer.latin1("a\u00a0\u00a0 \n b"));
        assertEquals("ab", TextSanitizer.latin1("a\u0000\u0007b"));
        assertEquals("?", TextSanitizer.latin1("\u4e2d"));            // sin equivalente: "?"
        assertEquals("", TextSanitizer.latin1(null));
        // todo lo que devuelve es representable en ISO-8859-1
        String s = TextSanitizer.latin1("caf\u00e9 \u201c\u2026\u201d \ud83d\ude00 \u0151 \u20ac");
        assertTrue(java.nio.charset.StandardCharsets.ISO_8859_1.newEncoder().canEncode(s), s);
    }

    @Test
    void montoEnLetras() {
        assertEquals("CIENTO DIECIOCHO CON 00/100 SOLES", MontoEnLetras.convertir(new BigDecimal("118"), "PEN"));
        assertEquals("MIL CIENTO OCHENTA CON 50/100 SOLES", MontoEnLetras.convertir(new BigDecimal("1180.5"), "PEN"));
        assertEquals("VEINTIUN MIL CON 01/100 SOLES", MontoEnLetras.convertir(new BigDecimal("21000.01"), "PEN"));
        assertEquals("UN MILLON DOSCIENTOS MIL CON 00/100 DOLARES AMERICANOS", MontoEnLetras.convertir(new BigDecimal("1200000"), "USD"));
        assertEquals("CIEN CON 00/100 SOLES", MontoEnLetras.convertir(new BigDecimal("100"), "PEN"));
        assertEquals("CERO CON 50/100 SOLES", MontoEnLetras.convertir(new BigDecimal("0.50"), "PEN"));
        assertEquals("TREINTA Y UN MIL QUINIENTOS CON 00/100 SOLES", MontoEnLetras.convertir(new BigDecimal("31500"), "PEN"));
    }

    // ---------------------------------------------------------------- secretos
    @Test
    void secretosNuncaSeImprimen() {
        Secret s = Secret.of("claveSOL-123");
        assertEquals("***", s.toString());
        assertFalse(("x" + s).contains("claveSOL"));
        SunatCredentials c = SunatCredentials.produccion("20608945123", "USUARIO1", s);
        assertFalse(c.toString().contains("claveSOL"));
        assertEquals("20608945123USUARIO1", c.username());
    }

    @Test
    void cp_auth_08_credencialesBetaSegunManual() {
        SunatCredentials c = SunatCredentials.beta("20100066603");
        assertEquals("20100066603MODDATOS", c.username());
        assertEquals("moddatos", c.password().reveal());
        assertEquals("MODDATOS", SunatCredentials.beta("20100066603", "MODDATOS").password().reveal());
        assertFalse(c.toString().contains("moddatos"));
    }

    @Test
    void aesGcmCifraDescifraYDetectaAlteracion() {
        String key = java.util.Base64.getEncoder().encodeToString(new byte[32]);
        AesGcmCipher c = AesGcmCipher.fromBase64Key(key);
        String blob = c.encryptToBase64("mi-clave-sol", "emisor:1:sol");
        assertEquals("mi-clave-sol", c.decryptToSecret(blob, "emisor:1:sol").reveal());
        assertNotEquals(blob, c.encryptToBase64("mi-clave-sol", "emisor:1:sol"), "IV aleatorio");
        assertThrows(IllegalArgumentException.class, () -> c.decryptToSecret(blob, "emisor:2:sol")); // AAD distinto
        byte[] raw = java.util.Base64.getDecoder().decode(blob);
        raw[raw.length - 1] ^= 1;
        assertThrows(IllegalArgumentException.class,
                () -> c.decrypt(raw, "emisor:1:sol")); // dato alterado
        assertThrows(IllegalStateException.class, () -> AesGcmCipher.fromBase64Key(java.util.Base64.getEncoder().encodeToString(new byte[16])));
        var ex = assertThrows(IllegalStateException.class, () -> AesGcmCipher.fromBase64Key("no-es-base64!!"));
        assertFalse(ex.getMessage().contains("no-es-base64"));
    }
}
