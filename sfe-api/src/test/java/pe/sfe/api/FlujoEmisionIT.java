package pe.sfe.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pe.sfe.core.app.Ports;
import pe.sfe.core.cdr.Cdr;
import pe.sfe.core.policy.ResultadoEnvio;

/**
 * Integracion de punta a punta con PostgreSQL 16 REAL (Testcontainers; requiere Docker): API Key, 202, idempotencia,
 * correlativos, outbox (reclamar con SKIP LOCKED + registrar resultado) y reintento.
 * NOTA: escrita sin poder ejecutarla en el entorno donde se genero el proyecto (sin Maven Central ni Docker);
 * el SQL equivalente SI se valido contra PostgreSQL 16 y la logica con pruebas unitarias de sfe-core.
 */
@Testcontainers
@ActiveProfiles("dev")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.password=test",
        "sfe.security.master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "sfe.signing.keystore-password=it-pass-123"})
class FlujoEmisionIT {

    static final String API_KEY = "dev-key-caja-01-NO-USAR-EN-PROD";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void certificado(DynamicPropertyRegistry registry) throws Exception {
        Path dir = Files.createTempDirectory("sfe-it-");
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

    private HttpEntity<Map<String, Object>> conLlave(Map<String, Object> cuerpo, String llave) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (llave != null) h.set("X-API-Key", llave);
        return new HttpEntity<>(cuerpo, h);
    }

    private static Map<String, Object> factura(String idVenta) {
        return Map.of(
                "idVentaExterno", idVenta, "tipo", "01", "moneda", "PEN",
                "adquirente", Map.of("tipoDocumento", "6", "numeroDocumento", "20512345678", "denominacion", "CLIENTE EJEMPLO SAC"),
                "items", List.of(Map.of("codigo", "P001", "descripcion", "Cuaderno A4", "unidadMedida", "NIU",
                        "cantidad", 2, "valorUnitario", 50.00, "afectacionIgv", "10")));
    }

    @Test
    void sinApiKeyOConLlaveInvalidaResponde401() {
        assertThat(rest.exchange("/api/v1/comprobantes", HttpMethod.POST, conLlave(factura("X"), null), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(rest.exchange("/api/v1/comprobantes", HttpMethod.POST, conLlave(factura("X"), "llave-falsa"), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void emisionResponde202EsIdempotenteYConsecutiva() {
        ResponseEntity<Map> a = rest.exchange("/api/v1/comprobantes", HttpMethod.POST, conLlave(factura("VENTA-A"), API_KEY), Map.class);
        assertThat(a.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(a.getBody().get("estado")).isEqualTo("PENDIENTE");
        assertThat(a.getBody().get("serie")).isEqualTo("F001");
        assertThat(a.getBody().get("importeTotal")).isEqualTo(118.0);
        assertThat(a.getHeaders().getLocation()).isNotNull();

        ResponseEntity<Map> repetida = rest.exchange("/api/v1/comprobantes", HttpMethod.POST, conLlave(factura("VENTA-A"), API_KEY), Map.class);
        assertThat(repetida.getBody().get("id")).isEqualTo(a.getBody().get("id"));
        assertThat(repetida.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");

        ResponseEntity<Map> b = rest.exchange("/api/v1/comprobantes", HttpMethod.POST, conLlave(factura("VENTA-B"), API_KEY), Map.class);
        assertThat(((Number) b.getBody().get("correlativo")).longValue())
                .isEqualTo(((Number) a.getBody().get("correlativo")).longValue() + 1);

        ResponseEntity<Map> get = rest.exchange("/api/v1/comprobantes/" + a.getBody().get("id"), HttpMethod.GET, conLlave(null, API_KEY), Map.class);
        assertThat(get.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void reglaDeNegocioDevuelve422() {
        Map<String, Object> mala = Map.of("idVentaExterno", "VENTA-RN", "tipo", "01", "moneda", "PEN",
                "adquirente", Map.of("tipoDocumento", "1", "numeroDocumento", "12345678", "denominacion", "JUAN"),
                "items", List.of(Map.of("descripcion", "x", "unidadMedida", "NIU", "cantidad", 1, "valorUnitario", 1, "afectacionIgv", "10")));
        assertThat(rest.exchange("/api/v1/comprobantes", HttpMethod.POST, conLlave(mala, API_KEY), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @SuppressWarnings("unchecked")
    void outboxReclamaConLeaseReintentaYAceptaSinPerderElComprobante() {
        ResponseEntity<Map> e = rest.exchange("/api/v1/comprobantes", HttpMethod.POST, conLlave(factura("VENTA-OUT"), API_KEY), Map.class);
        String id = (String) e.getBody().get("id");

        // intento 1: falla de red -> vuelve a la cola
        List<Ports.EnvioPendiente> lote = outbox.reclamar(50, Duration.ofSeconds(30), "it-worker", Instant.now());
        Ports.EnvioPendiente mio = lote.stream().filter(p -> p.comprobanteId().toString().equals(id)).findFirst().orElseThrow();
        assertThat(mio.intento()).isEqualTo(1);
        assertThat(mio.zipNombre()).endsWith(".zip");
        assertThat(outbox.reclamar(50, Duration.ofSeconds(30), "otro", Instant.now()).stream()
                .anyMatch(p -> p.comprobanteId().toString().equals(id))).isFalse(); // en proceso: nadie mas lo toma
        outbox.registrarResultado(mio, new ResultadoEnvio.Reintentar("timeout"), Instant.now().minusSeconds(1), Instant.now());
        ResponseEntity<Map> tras = rest.exchange("/api/v1/comprobantes/" + id, HttpMethod.GET, conLlave(null, API_KEY), Map.class);
        assertThat(tras.getBody().get("estado")).isEqualTo("EN_CONTINGENCIA");

        // intento 2: SUNAT acepta
        Ports.EnvioPendiente otra = outbox.reclamar(50, Duration.ofSeconds(30), "it-worker", Instant.now()).stream()
                .filter(p -> p.comprobanteId().toString().equals(id)).findFirst().orElseThrow();
        assertThat(otra.intento()).isEqualTo(2);
        Cdr cdr = new Cdr("1", "F001-1", "0", "La Factura ha sido aceptada", List.of(), false);
        outbox.registrarResultado(otra, new ResultadoEnvio.Aceptado(cdr, new byte[]{1, 2, 3}), null, Instant.now());
        ResponseEntity<Map> fin = rest.exchange("/api/v1/comprobantes/" + id, HttpMethod.GET, conLlave(null, API_KEY), Map.class);
        assertThat(fin.getBody().get("estado")).isEqualTo("ACEPTADO");
        assertThat(fin.getBody().get("cdrCodigo")).isEqualTo("0");

        // un resultado tardio de un intento obsoleto NO pisa el estado final (fencing)
        outbox.registrarResultado(mio, new ResultadoEnvio.Reintentar("tardio"), Instant.now(), Instant.now());
        ResponseEntity<Map> igual = rest.exchange("/api/v1/comprobantes/" + id, HttpMethod.GET, conLlave(null, API_KEY), Map.class);
        assertThat(igual.getBody().get("estado")).isEqualTo("ACEPTADO");
    }

    @Test
    @SuppressWarnings("unchecked")
    void reporteDeContingenciaListaPendientesConIntentosYError() {
        ResponseEntity<Map> e = rest.exchange("/api/v1/comprobantes", HttpMethod.POST, conLlave(factura("VENTA-RF10"), API_KEY), Map.class);
        String id = (String) e.getBody().get("id");
        Ports.EnvioPendiente mio = outbox.reclamar(50, Duration.ofSeconds(30), "it-rf10", Instant.now()).stream()
                .filter(p -> p.comprobanteId().toString().equals(id)).findFirst().orElseThrow();
        outbox.registrarResultado(mio, new ResultadoEnvio.Reintentar("timeout"), Instant.now().plusSeconds(600), Instant.now());

        ResponseEntity<List> r = rest.exchange("/api/v1/contingencia", HttpMethod.GET, conLlave(null, API_KEY), List.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> item = ((List<Map<String, Object>>) r.getBody()).stream()
                .filter(m -> id.equals(m.get("comprobanteId"))).findFirst().orElseThrow();
        assertThat(item.get("estado")).isEqualTo("EN_CONTINGENCIA");
        assertThat(((Number) item.get("intentos")).intValue()).isEqualTo(1);
        assertThat(item.get("ultimoError")).isNotNull();
    }}
