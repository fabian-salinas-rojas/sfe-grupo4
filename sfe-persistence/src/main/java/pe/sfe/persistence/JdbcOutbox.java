package pe.sfe.persistence;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.sfe.core.app.Ports;
import pe.sfe.core.cdr.Cdr;
import pe.sfe.core.policy.ResultadoEnvio;

/**
 * Cola de envio (outbox) con FOR UPDATE SKIP LOCKED. Dos transacciones CORTAS (reclamar / registrar resultado);
 * la llamada SOAP ocurre entre ambas, sin transaccion ni filas bloqueadas.
 */
@Component
public class JdbcOutbox implements Ports.Outbox {

    private static final Logger log = LoggerFactory.getLogger(JdbcOutbox.class);

    private final JdbcClient jdbc;
    private final String sqlReclamar = SqlResources.cargar("outbox-reclamar.sql");
    private final String sqlCerrar = SqlResources.cargar("outbox-cerrar.sql");
    private final String sqlComprobante = SqlResources.cargar("comprobante-actualizar-estado.sql");

    public JdbcOutbox(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public List<Ports.EnvioPendiente> reclamar(int max, Duration lease, String workerId, Instant ahora) {
        return jdbc.sql(sqlReclamar)
                .param("ahora", utc(ahora))
                .param("lease_segundos", lease.toSeconds())
                .param("worker", workerId)
                .param("max", max)
                .query((rs, n) -> new Ports.EnvioPendiente(
                        rs.getObject("envio_id", UUID.class),
                        rs.getObject("comprobante_id", UUID.class),
                        rs.getString("zip_nombre"),
                        rs.getBytes("zip_contenido"),
                        rs.getInt("intentos"),
                        rs.getObject("plazo_limite", LocalDate.class)))
                .list();
    }

    @Override
    @Transactional
    public void registrarResultado(Ports.EnvioPendiente e, ResultadoEnvio r, Instant proximoIntento, Instant ahora) {
        boolean reintentar = r instanceof ResultadoEnvio.Reintentar;
        int filas = jdbc.sql(sqlCerrar)
                .param("estado", reintentar ? "PENDIENTE" : "COMPLETADO")
                .param("proximo", proximoIntento == null ? null : utc(proximoIntento))
                .param("error", descripcionDeError(r))
                .param("id", e.envioId())
                .param("intento", e.intento())
                .update();
        if (filas == 0) {
            // Nuestro lease vencio y otro worker retomo este envio: su resultado es el valido, el nuestro se descarta.
            log.warn("Envio {} ya no pertenece a este worker (lease vencido); resultado descartado", e.envioId());
            return;
        }
        Cdr cdr = cdrDe(r);
        jdbc.sql(sqlComprobante)
                .param("estado", r.estado().name())
                .param("cdr_codigo", cdr == null ? null : cdr.codigoRespuesta())
                .param("cdr_descripcion", cdr == null ? null : cdr.descripcion())
                .param("cdr_observaciones", cdr == null ? null : cdr.observaciones().stream()
                        .map(o -> o.codigo() + " - " + o.descripcion()).collect(Collectors.joining("\n")))
                .param("cdr_zip", cdrZipDe(r))
                .param("cdr_firma_integra", cdr == null ? null : cdr.firmaIntegra())
                .param("recibido_en", cdr == null ? null : utc(ahora))
                .param("ahora", utc(ahora))
                .param("id", e.comprobanteId())
                .update();
        jdbc.sql("INSERT INTO bitacora_auditoria (comprobante_id, evento, detalle, actor) VALUES (:cid, :ev, :d, 'sfe-worker')")
                .param("cid", e.comprobanteId())
                .param("ev", "ENVIO_" + r.estado().name())
                .param("d", recortar("intento " + e.intento() + (descripcionDeError(r) == null ? "" : ": " + descripcionDeError(r)), 500))
                .update();
    }

    private static Cdr cdrDe(ResultadoEnvio r) {
        return switch (r) {
            case ResultadoEnvio.Aceptado a -> a.cdr();
            case ResultadoEnvio.AceptadoConObservaciones a -> a.cdr();
            case ResultadoEnvio.Rechazado x -> x.cdr();
            default -> null;
        };
    }

    private static byte[] cdrZipDe(ResultadoEnvio r) {
        return switch (r) {
            case ResultadoEnvio.Aceptado a -> a.cdrZip();
            case ResultadoEnvio.AceptadoConObservaciones a -> a.cdrZip();
            case ResultadoEnvio.Rechazado x -> x.cdrZip();
            default -> null;
        };
    }

    private static String descripcionDeError(ResultadoEnvio r) {
        return switch (r) {
            case ResultadoEnvio.Reintentar x -> recortar(x.motivo(), 1000);
            case ResultadoEnvio.Excepcion x -> recortar(x.codigo() + ": " + x.descripcion(), 1000);
            case ResultadoEnvio.Rechazado x -> recortar(x.codigo() + ": " + x.descripcion(), 1000);
            default -> null;
        };
    }

    private static String recortar(String s, int max) {
        return s == null ? null : (s.length() <= max ? s : s.substring(0, max));
    }

    private static OffsetDateTime utc(Instant i) {
        return OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
    }
}
