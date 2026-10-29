package pe.sfe.core.app;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.DoubleSupplier;
import pe.sfe.core.app.Ports.EnvioPendiente;
import pe.sfe.core.policy.Backoff;
import pe.sfe.core.policy.EnvioClassifier;
import pe.sfe.core.policy.ResultadoEnvio;
import pe.sfe.core.soap.BillClient;

/**
 * Caso de uso del sfe-worker. Un ciclo: reclamar (SKIP LOCKED + lease) -> sendBill (fuera de transaccion) ->
 * clasificar -> persistir. Cualquier excepcion de un envio se clasifica; nunca aborta el lote ni al worker.
 * Nunca registra el contenido del ZIP ni credenciales.
 */
public final class ProcesarEnviosService {

    public record Resumen(int procesados, int aceptados, int rechazados, int reintentos, int excepciones) {
    }

    private final Ports.Outbox outbox;
    private final BillClient client;
    private final EnvioClassifier classifier;
    private final Backoff backoff;
    private final Clock clock;
    private final DoubleSupplier azar;
    private final Duration lease;
    private final int tamanoLote;
    private final String workerId;

    public ProcesarEnviosService(Ports.Outbox outbox, BillClient client, EnvioClassifier classifier, Backoff backoff,
                                 Clock clock, DoubleSupplier azar, Duration lease, int tamanoLote, String workerId) {
        this.outbox = outbox;
        this.client = client;
        this.classifier = classifier;
        this.backoff = backoff;
        this.clock = clock;
        this.azar = azar;
        this.lease = lease;
        this.tamanoLote = tamanoLote;
        this.workerId = workerId;
    }

    public Resumen procesarLote() {
        List<EnvioPendiente> lote = outbox.reclamar(tamanoLote, lease, workerId, clock.instant());
        int ok = 0, rech = 0, reint = 0, exc = 0;
        for (EnvioPendiente p : lote) {
            ResultadoEnvio r = enviar(p);
            Instant ahora = clock.instant();
            Instant proximo = r instanceof ResultadoEnvio.Reintentar
                    ? ahora.plus(backoff.retraso(p.intento(), azar)) : null;
            outbox.registrarResultado(p, r, proximo, ahora);
            switch (r) {
                case ResultadoEnvio.Aceptado a -> ok++;
                case ResultadoEnvio.AceptadoConObservaciones a -> ok++;
                case ResultadoEnvio.Rechazado x -> rech++;
                case ResultadoEnvio.Excepcion x -> exc++;
                case ResultadoEnvio.Reintentar x -> reint++;
            }
        }
        return new Resumen(lote.size(), ok, rech, reint, exc);
    }

    private ResultadoEnvio enviar(EnvioPendiente p) {
        try {
            byte[] cdrZip = client.sendBill(p.zipNombre(), p.zip());
            return classifier.deCdrZip(cdrZip);
        } catch (RuntimeException e) {
            return classifier.deFalla(e);
        }
    }
}
