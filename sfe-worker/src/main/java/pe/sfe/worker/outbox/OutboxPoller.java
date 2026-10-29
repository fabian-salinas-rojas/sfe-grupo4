package pe.sfe.worker.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.sfe.core.app.ProcesarEnviosService;

/** Sondea el outbox. fixedDelay: no solapa ciclos dentro de la misma instancia. Nunca deja morir el planificador. */
@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final ProcesarEnviosService servicio;

    public OutboxPoller(ProcesarEnviosService servicio) {
        this.servicio = servicio;
    }

    @Scheduled(fixedDelayString = "${sfe.worker.poll-interval:PT2S}", initialDelayString = "${sfe.worker.initial-delay:PT5S}")
    void ciclo() {
        try {
            var r = servicio.procesarLote();
            if (r.procesados() > 0) {
                log.info("Lote: {} procesados, {} aceptados, {} rechazados, {} reintentos, {} excepciones",
                        r.procesados(), r.aceptados(), r.rechazados(), r.reintentos(), r.excepciones());
            }
        } catch (RuntimeException e) {
            // Solo la clase y el mensaje: nunca el objeto completo (podria arrastrar datos sensibles).
            log.error("Ciclo del worker fallo: {}: {}", e.getClass().getSimpleName(), e.getMessage());
        }
    }
}
