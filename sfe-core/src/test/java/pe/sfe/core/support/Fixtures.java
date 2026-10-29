package pe.sfe.core.support;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import pe.sfe.core.domain.Adquirente;
import pe.sfe.core.domain.DocumentoVenta;
import pe.sfe.core.domain.Emisor;
import pe.sfe.core.domain.ItemVenta;
import pe.sfe.core.domain.MotivoNota;
import pe.sfe.core.domain.ReferenciaDocumento;
import pe.sfe.core.domain.TipoComprobante;
import pe.sfe.core.security.Secret;
import pe.sfe.core.sign.SigningKey;

/** Datos de prueba. El certificado se genera con keytool del JDK en cada corrida: no hay llaves en el repositorio. */
public final class Fixtures {

    public static final String RUC = "20608945123";
    public static final Secret PASS = Secret.of("changeit-test");

    private Fixtures() {
    }

    public static Emisor emisor() {
        return new Emisor(RUC, "COMERCIAL ANDINA SAC", "ANDINA MARKET", "150101", "AV. AREQUIPA 1234 - OF. 5",
                "LIMA", "LIMA", "LIMA");
    }

    public static ItemVenta item(String desc, String cant, String vu, String afect) {
        return new ItemVenta("P001", desc, "NIU", new BigDecimal(cant), new BigDecimal(vu), afect);
    }

    public static DocumentoVenta factura() {
        return new DocumentoVenta(TipoComprobante.FACTURA, "F001", 1, LocalDate.of(2026, 10, 23), LocalTime.of(10, 5, 30),
                "PEN", emisor(), new Adquirente("6", "20512345678", "CLIENTE EJEMPLO S.A.C.", "JR. UNION 100, LIMA"),
                List.of(item("Cuaderno A4 cuadriculado", "10", "5.00", "10"), item("Servicio de delivery", "1", "8.474576", "10")),
                null, null);
    }

    public static DocumentoVenta boleta() {
        return new DocumentoVenta(TipoComprobante.BOLETA, "B001", 7, LocalDate.of(2026, 10, 23), LocalTime.of(0, 0, 0),
                "PEN", emisor(), Adquirente.consumidorFinal(), List.of(item("Lapicero azul", "3", "1.50", "10")), null, null);
    }

    public static DocumentoVenta notaCredito() {
        return new DocumentoVenta(TipoComprobante.NOTA_CREDITO, "FC01", 3, LocalDate.of(2026, 10, 23), LocalTime.of(11, 0, 0),
                "PEN", emisor(), new Adquirente("6", "20512345678", "CLIENTE EJEMPLO S.A.C.", null),
                List.of(item("Cuaderno A4 cuadriculado", "2", "5.00", "10")),
                new ReferenciaDocumento(TipoComprobante.FACTURA, "F001", 1), new MotivoNota("07", "Devolucion por item"));
    }

    public static DocumentoVenta notaDebito() {
        return new DocumentoVenta(TipoComprobante.NOTA_DEBITO, "FD01", 2, LocalDate.of(2026, 10, 23), LocalTime.of(12, 30, 0),
                "PEN", emisor(), new Adquirente("6", "20512345678", "CLIENTE EJEMPLO S.A.C.", null),
                List.of(item("Intereses por mora", "1", "10.00", "30")),
                new ReferenciaDocumento(TipoComprobante.FACTURA, "F001", 1), new MotivoNota("01", "Intereses por mora"));
    }

    /** Genera un PKCS#12 autofirmado con keytool. startDate null = ahora. */
    public static byte[] pkcs12(String startDate, int validityDays) {
        try {
            Path dir = Files.createTempDirectory("sfe-test-");
            Path p12 = dir.resolve("test.p12");
            List<String> cmd = new java.util.ArrayList<>(List.of(
                    Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                    "-genkeypair", "-alias", "sfe", "-keyalg", "RSA", "-keysize", "2048",
                    "-sigalg", "SHA256withRSA", "-validity", String.valueOf(validityDays),
                    "-dname", "CN=EMISOR PRUEBA, OU=" + RUC + ", O=COMERCIAL ANDINA SAC, C=PE",
                    "-storetype", "PKCS12", "-keystore", p12.toString(),
                    "-storepass", PASS.reveal(), "-keypass", PASS.reveal()));
            if (startDate != null) {
                cmd.add("-startdate");
                cmd.add(startDate);
            }
            Process pr = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            if (!pr.waitFor(60, TimeUnit.SECONDS) || pr.exitValue() != 0) {
                throw new IllegalStateException("keytool fallo: " + new String(pr.getInputStream().readAllBytes()));
            }
            byte[] bytes = Files.readAllBytes(p12);
            Files.deleteIfExists(p12);
            Files.deleteIfExists(dir);
            return bytes;
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Certificado emitido hace 30 dias y vigente por un ano: valido para cualquier "ahora" razonable. */
    public static SigningKey llaveVigente() {
        String desde = java.time.LocalDateTime.now().minusDays(30)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"));
        return SigningKey.fromPkcs12(pkcs12(desde, 395), PASS);
    }
}
