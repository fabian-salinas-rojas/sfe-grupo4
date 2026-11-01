package pe.sfe.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pe.sfe.core.app.Ports;
import pe.sfe.core.cdr.Cdr;
import pe.sfe.core.policy.ResultadoEnvio;

/**
 * RF-07 con PostgreSQL 16 REAL (Testcontainers; requiere Docker) y el envio de correo SIMULADO (JavaMailSender de Mockito):
 * se comprueba destinatario, remitente, adjuntos (PDF real y XML firmado) y las reglas 409 / 404 / 400.
 * El SMTP real de demostracion es Mailpit (deploy/docker-compose.yml).
 */
@Testcontainers
@ActiveProfiles("dev")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.password=test",
        "sfe.security.master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "sfe.signing.keystore-password=it-pass-123"})
class CorreoIT {

    static final String API_KEY = "dev-key-caja-01-NO-USAR-EN-PROD";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void certificado(DynamicPropertyRegistry registry) throws Exception {
        Path dir = Files.createTempDirectory("sfe-it-correo-");
        Path p12 = dir.resolve("it.p12");
        Process pr = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                "-genkeypair", "-alias", "sfe", "-keyalg", "RSA", "-keysize", "2048", "-sigalg", "SHA256withRSA",
                "-validity", "30", "-startdate", java.time.LocalDateTime.now().minusDays(1)
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")),
                "-dname", "CN=EMISOR IT, OU=20608945123, O=COMERCIAL ANDINA SAC, C=PE", "-storetype", "PKCS12",
                "-keystore", p12.toString(), "-storepass", "it-pass-123", "-keypass", "it-pass-123")
                .redirectErrorStream(true).start();
        if (!pr.waitFor(60, TimeUnit.SECONDS) || pr.exitValue() != 0) {
            throw new IllegalStateException("keytool fallo: " + new String(pr.getInputStream().readAllBytes()));
        }
        registry.add("sfe.signing.keystore-path", p12::toString);
    }

    @Autowired TestRestTemplate rest;
    @Autowired Ports.Outbox outbox;
    @MockitoBean JavaMailSender mailSender;

    @BeforeEach
    void correoSimulado() {
        when(mailSender.createMimeMessage()).thenAnswer(i -> new MimeMessage((Session) null));
    }

    private HttpEntity<Map<String, Object>> conLlave(Map<String, Object> cuerpo) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("X-API-Key", API_KEY);
        return new HttpEntity<>(cuerpo, h);
    }

    private static Map<String, Object> factura(String idVenta) {
        return Map.of(
                "idVentaExterno", idVenta, "tipo", "01", "moneda", "PEN",
                "adquirente", Map.of("tipoDocumento", "6", "numeroDocumento", "20512345678", "denominacion", "CLIENTE EJEMPLO SAC"),
                "items", List.of(Map.of("codigo", "P001", "descripcion", "Cuaderno A4", "unidadMedida", "NIU",
                        "cantidad", 2, "valorUnitario", 50.00, "afectacionIgv", "10")));
    }

    @SuppressWarnings("rawtypes")
    private String emitir(String idVenta) {
        ResponseEntity<Map> e = rest.exchange("/api/v1/comprobantes", HttpMethod.POST, conLlave(factura(idVenta)), Map.class);
        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        return (String) e.getBody().get("id");
    }

    /** Simula al worker: reclama el envio de ESTE comprobante y registra que SUNAT lo acepto. */
    private void aceptar(String id) {
        Ports.EnvioPendiente mio = outbox.reclamar(50, Duration.ofSeconds(30), "it-correo", Instant.now()).stream()
                .filter(p -> p.comprobanteId().toString().equals(id)).findFirst().orElseThrow();
        Cdr cdr = new Cdr("1", "F001-1", "0", "La Factura ha sido aceptada", List.of(), false);
        outbox.registrarResultado(mio, new ResultadoEnvio.Aceptado(cdr, new byte[]{1, 2, 3}), null, Instant.now());
    }

    private ResponseEntity<String> pedirCorreo(String id, String destinatario) {
        return rest.exchange("/api/v1/comprobantes/" + id + "/correo", HttpMethod.POST,
                conLlave(Map.of("destinatario", destinatario)), String.class);
    }

    @Test
    void comprobanteAceptadoSeEnviaConPdfYXmlAdjuntos() throws Exception {
        String id = emitir("VENTA-MAIL-OK");
        aceptar(id);

        ResponseEntity<String> r = pedirCorreo(id, "cliente@ejemplo.com");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);

        ArgumentCaptor<MimeMessage> enviado = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(enviado.capture());
        MimeMessage m = enviado.getValue();
        assertThat(m.getAllRecipients()).hasSize(1);
        assertThat(m.getAllRecipients()[0].toString()).isEqualTo("cliente@ejemplo.com");
        assertThat(m.getSubject()).startsWith("Comprobante electronico F001-");

        Multipart raiz = (Multipart) m.getContent();
        List<String> nombres = new ArrayList<>();
        byte[] pdf = null;
        byte[] xml = null;
        for (int i = 0; i < raiz.getCount(); i++) {
            BodyPart parte = raiz.getBodyPart(i);
            String nombre = parte.getFileName();
            if (nombre == null) continue;             // la parte sin nombre es el cuerpo de texto
            nombres.add(nombre);
            byte[] bytes = parte.getDataHandler().getInputStream().readAllBytes();
            if (nombre.endsWith(".pdf")) pdf = bytes;
            if (nombre.endsWith(".xml")) xml = bytes;
        }
        assertThat(nombres).hasSize(2);
        assertThat(nombres).allMatch(n -> n.startsWith("20608945123-01-F001-"));
        assertThat(pdf).isNotNull();
        assertThat(new String(pdf, 0, 4, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
        assertThat(xml).isNotNull();
        assertThat(new String(xml, java.nio.charset.StandardCharsets.ISO_8859_1)).contains("<ds:Signature");
    }

    @Test
    void comprobantePendienteNoSeEnviaYResponde409() {
        String id = emitir("VENTA-MAIL-PEND");
        assertThat(pedirCorreo(id, "cliente@ejemplo.com").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void comprobanteInexistenteResponde404() {
        assertThat(pedirCorreo(UUID.randomUUID().toString(), "cliente@ejemplo.com").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void destinatarioInvalidoOConVariosDestinosResponde400() {
        String id = emitir("VENTA-MAIL-BAD");
        for (String malo : new String[]{"no-es-correo", "a@b.com, c@d.com", "a@b.com\r\nBcc: x@y.com", "<a@b.com>"}) {
            assertThat(pedirCorreo(id, malo).getStatusCode()).as(malo).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        verify(mailSender, never()).send(any(MimeMessage.class));
    }
}
