package pe.sfe.persistence;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import pe.sfe.core.app.Ports;
import pe.sfe.core.domain.EstadoComprobante;

/** RF-10: lee la cola de contingencia (comprobante + envio_outbox). Solo lectura; filtra por caja. */
@Component
public class JdbcContingencia implements Ports.Contingencia {

    private static final RowMapper<Ports.ItemContingencia> FILA = (rs, n) -> {
        OffsetDateTime ultimo = rs.getObject("ultimo_intento_en", OffsetDateTime.class);
        OffsetDateTime proximo = rs.getObject("proximo_intento", OffsetDateTime.class);
        return new Ports.ItemContingencia(
                rs.getObject("id", UUID.class),
                rs.getString("serie").trim() + "-" + rs.getLong("correlativo"),
                EstadoComprobante.valueOf(rs.getString("estado")),
                rs.getString("estado_envio"),
                rs.getInt("intentos"),
                ultimo == null ? null : ultimo.toInstant(),
                proximo == null ? null : proximo.toInstant(),
                rs.getString("ultimo_error"),
                rs.getObject("plazo_limite", LocalDate.class),
                rs.getBoolean("plazo_vencido"));
    };

    private final JdbcClient jdbc;

    public JdbcContingencia(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Ports.ItemContingencia> pendientes(long posId) {
        return jdbc.sql("""
                SELECT c.id, c.serie, c.correlativo, c.estado, c.plazo_limite,
                       (c.plazo_limite < current_date) AS plazo_vencido,
                       o.estado AS estado_envio, o.intentos, o.ultimo_intento_en, o.proximo_intento, o.ultimo_error
                  FROM comprobante c
                  JOIN envio_outbox o ON o.comprobante_id = c.id
                 WHERE c.pos_id = :pos AND c.estado IN ('PENDIENTE', 'EN_CONTINGENCIA')
                 ORDER BY c.plazo_limite, c.correlativo
                """).param("pos", posId).query(FILA).list();
    }
}