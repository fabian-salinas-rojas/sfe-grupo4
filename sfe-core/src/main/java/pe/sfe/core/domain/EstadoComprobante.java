package pe.sfe.core.domain;

/**
 * Estado tributario por comprobante (APF1, clase EstadoTributario). Se usa un subconjunto:
 * ENVIADO y ANULADO no aplican a este incremento (sendBill es sincrono; la baja es otro flujo).
 * EXCEPCION es un estado adicional: SUNAT devolvio una excepcion del contribuyente (1000-1999),
 * el documento se considera "no informado" y requiere correccion manual (manual SUNAT, 4.1).
 */
public enum EstadoComprobante {
    PENDIENTE,
    EN_CONTINGENCIA,
    ACEPTADO,
    ACEPTADO_CON_OBSERVACIONES,
    RECHAZADO,
    EXCEPCION;

    public boolean esFinal() {
        return this == ACEPTADO || this == ACEPTADO_CON_OBSERVACIONES || this == RECHAZADO || this == EXCEPCION;
    }
}
