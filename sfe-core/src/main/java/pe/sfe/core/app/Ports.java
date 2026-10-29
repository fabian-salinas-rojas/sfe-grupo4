package pe.sfe.core.app;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import pe.sfe.core.domain.Emisor;
import pe.sfe.core.domain.EstadoComprobante;
import pe.sfe.core.domain.TipoComprobante;
import pe.sfe.core.policy.ResultadoEnvio;
import pe.sfe.core.sign.SigningKey;

/** Puertos hexagonales: la logica vive en sfe-core; JPA/JDBC, REST y SOAP son adaptadores en otros modulos. */
public final class Ports {

    private Ports() {
    }

    public interface Transaccion {
        <T> T ejecutar(Supplier<T> trabajo);
    }

    public record Asignacion(String serie, long correlativo) {
    }

    public interface Correlativos {
        /** Debe ejecutarse DENTRO de la transaccion de emision: si algo falla, el numero se revierte (sin saltos). */
        Asignacion asignar(long posId, TipoComprobante tipo, char letraSerie);
    }

    public interface Emisores {
        Emisor delPos(long posId);
    }

    public interface Llaves {
        SigningKey para(Emisor emisor);
    }

    public record NuevoComprobante(
            long posId, String idVentaExterno, TipoComprobante tipo, String serie, long correlativo,
            LocalDate fechaEmision, String moneda, BigDecimal importeTotal, byte[] xmlFirmado, String xmlSha256,
            String zipNombre, byte[] zip, LocalDate plazoLimite, Instant creadoEn) {
    }

    public record ComprobanteResumen(
            UUID id, TipoComprobante tipo, String serie, long correlativo, EstadoComprobante estado,
            BigDecimal importeTotal, String cdrCodigo, String cdrDescripcion) {
    }

    /** Lo lanza el adaptador cuando choca la unicidad (pos, idVentaExterno). */
    public static class VentaDuplicadaException extends RuntimeException {
        public VentaDuplicadaException(String message) {
            super(message);
        }
    }

    public interface Comprobantes {
        Optional<ComprobanteResumen> buscarPorIdVenta(long posId, String idVentaExterno);

        Optional<ComprobanteResumen> buscarPorId(long posId, UUID id);

        /** Inserta comprobante + fila de outbox (PENDIENTE) + bitacora en la MISMA transaccion. */
        ComprobanteResumen registrar(NuevoComprobante nuevo);
    }

    public record EnvioPendiente(
            UUID envioId, UUID comprobanteId, String zipNombre, byte[] zip, int intento, LocalDate plazoLimite) {
    }

    public interface Outbox {
        /**
         * Reclama hasta {@code max} envios vencidos con FOR UPDATE SKIP LOCKED y les pone un lease; aumenta el contador
         * de intentos. Transaccion corta: el envio SOAP ocurre FUERA de ella.
         */
        List<EnvioPendiente> reclamar(int max, Duration lease, String workerId, Instant ahora);

        /** Persiste el desenlace: estado del comprobante, CDR, outbox (completado o reprogramado) y bitacora. */
        void registrarResultado(EnvioPendiente envio, ResultadoEnvio resultado, Instant proximoIntento, Instant ahora);
    }

    public record ItemContingencia(
            UUID comprobanteId, String numero, EstadoComprobante estado, String estadoEnvio, int intentos,
            Instant ultimoIntento, Instant proximoIntento, String ultimoError,
            LocalDate plazoLimite, boolean plazoVencido) {
    }

    /** RF-10: comprobantes en cola (PENDIENTE / EN_CONTINGENCIA) de la caja autenticada. */
    public interface Contingencia {
        List<ItemContingencia> pendientes(long posId);
    }}
