package pe.sfe.core.domain;

import java.math.BigDecimal;
import java.util.List;

public record Totales(
        List<LineaCalculada> lineas,
        BigDecimal gravadas,
        BigDecimal exoneradas,
        BigDecimal inafectas,
        BigDecimal exportacion,
        BigDecimal igv,
        BigDecimal valorVenta,
        BigDecimal importeTotal) {
}
