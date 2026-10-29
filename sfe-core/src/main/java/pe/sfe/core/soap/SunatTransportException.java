package pe.sfe.core.soap;

/** Falla de red, timeout de conexion/lectura, TLS o HTTP 5xx: SUNAT no llego a procesar (o no sabemos si lo hizo). */
public class SunatTransportException extends RuntimeException {
    public SunatTransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
