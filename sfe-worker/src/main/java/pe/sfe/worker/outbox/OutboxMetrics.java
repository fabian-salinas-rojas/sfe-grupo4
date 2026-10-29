package pe.sfe.worker.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Metricas para alertar (RNF-03/RN-06): cola pendiente y comprobantes que superaron su plazo legal de envio. */
@Component
public class OutboxMetrics implements MeterBinder {

    private final JdbcClient jdbc;

    public OutboxMetrics(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("sfe.outbox.pendientes", () -> contar("SELECT count(*) FROM envio_outbox WHERE estado <> 'COMPLETADO'"))
                .description("Envios aun no completados").register(registry);
        Gauge.builder("sfe.comprobantes.plazo_vencido", () -> contar(
                        "SELECT count(*) FROM comprobante WHERE estado IN ('PENDIENTE','EN_CONTINGENCIA') AND plazo_limite < current_date"))
                .description("Comprobantes sin CDR cuyo plazo de envio (RN-06) ya vencio: requieren atencion").register(registry);
        Gauge.builder("sfe.outbox.max_intentos", () -> contar("SELECT coalesce(max(intentos), 0) FROM envio_outbox WHERE estado <> 'COMPLETADO'"))
                .description("Mayor numero de intentos entre los envios pendientes").register(registry);
    }

    private double contar(String sql) {
        try {
            Long n = jdbc.sql(sql).query(Long.class).single();
            return n == null ? 0 : n;
        } catch (RuntimeException e) {
            return Double.NaN; // BD no disponible: la metrica queda en NaN en vez de romper el scrape
        }
    }
}
