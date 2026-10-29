package pe.sfe.core.domain;

import java.math.BigDecimal;

public record LineaCalculada(
        int numero,
        ItemVenta item,
        BigDecimal valorVenta,
        BigDecimal igv,
        BigDecimal precioUnitarioConIgv) {
}
