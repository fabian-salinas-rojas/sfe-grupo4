package pe.sfe.core.domain;

import java.math.BigDecimal;

/**
 * valorUnitario es SIN IGV. afectacionIgv segun Catalogo 07 (este prototipo soporta operaciones
 * onerosas: 10 gravado, 20 exonerado, 30 inafecto, 40 exportacion).
 */
public record ItemVenta(
        String codigo,
        String descripcion,
        String unidadMedida,
        BigDecimal cantidad,
        BigDecimal valorUnitario,
        String afectacionIgv) {
}
