package pe.sfe.core.domain;

import java.util.List;

/**
 * Pedido de emision tal como llega del POS (ya sin formato HTTP). La fecha y hora de emision las pone
 * el servidor (reloj America/Lima), nunca la caja. idVentaExterno es la clave de idempotencia por POS.
 */
public record SolicitudEmision(
        String idVentaExterno,
        TipoComprobante tipo,
        String moneda,
        Adquirente adquirente,
        List<ItemVenta> items,
        ReferenciaDocumento referencia,
        MotivoNota motivo) {

    /** Letra que debe llevar la serie: F/B segun el tipo, o la del comprobante modificado en notas (RN-03). */
    public char letraSerie() {
        return switch (tipo) {
            case FACTURA -> 'F';
            case BOLETA -> 'B';
            case NOTA_CREDITO, NOTA_DEBITO -> referencia.serie().charAt(0);
        };
    }
}
