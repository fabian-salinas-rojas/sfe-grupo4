package pe.sfe.core.policy;

import pe.sfe.core.cdr.Cdr;
import pe.sfe.core.domain.EstadoComprobante;

/** Desenlace de UN intento de envio a SUNAT. Solo {@link Reintentar} vuelve a la cola. */
public sealed interface ResultadoEnvio {

    EstadoComprobante estado();

    record Aceptado(Cdr cdr, byte[] cdrZip) implements ResultadoEnvio {
        public EstadoComprobante estado() { return EstadoComprobante.ACEPTADO; }
    }

    record AceptadoConObservaciones(Cdr cdr, byte[] cdrZip) implements ResultadoEnvio {
        public EstadoComprobante estado() { return EstadoComprobante.ACEPTADO_CON_OBSERVACIONES; }
    }

    /**
     * Rechazo normativo (2000-3999). Serie y correlativo quedan CONSUMIDOS: jamas se reintenta;
     * hay que emitir un documento nuevo con otro numero (manual, 4.1). cdr puede ser null si vino como SOAP Fault.
     */
    record Rechazado(String codigo, String descripcion, Cdr cdr, byte[] cdrZip) implements ResultadoEnvio {
        public EstadoComprobante estado() { return EstadoComprobante.RECHAZADO; }
    }

    /** Excepcion del contribuyente (1000-1999 / Fault Client): documento NO informado; reintentar igual falla igual. */
    record Excepcion(String codigo, String descripcion) implements ResultadoEnvio {
        public EstadoComprobante estado() { return EstadoComprobante.EXCEPCION; }
    }

    /** Red, timeout, Fault Server o excepcion propia de SUNAT (0100-0999): contingencia con backoff exponencial. */
    record Reintentar(String motivo) implements ResultadoEnvio {
        public EstadoComprobante estado() { return EstadoComprobante.EN_CONTINGENCIA; }
    }
}
