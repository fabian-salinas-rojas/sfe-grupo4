package pe.sfe.core.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * RN-04, regla UNICA de calculo (GD-02): importe total = suma de valores de venta + IGV.
 * Redondeo HALF_UP a 2 decimales por linea; los totales son la suma de las lineas ya redondeadas,
 * asi base imponible, IGV y total siempre cuadran con el detalle (causa tipica de rechazos de SUNAT).
 */
public final class CalculadoraTotales {

    public static final BigDecimal TASA_IGV = new BigDecimal("0.18");
    private static final BigDecimal UNO_MAS_IGV = BigDecimal.ONE.add(TASA_IGV);

    private CalculadoraTotales() {
    }

    public static Totales calcular(List<ItemVenta> items) {
        List<LineaCalculada> lineas = new ArrayList<>(items.size());
        BigDecimal gravadas = cero(), exoneradas = cero(), inafectas = cero(), exportacion = cero(), igv = cero();
        int n = 0;
        for (ItemVenta it : items) {
            n++;
            BigDecimal valorVenta = money(it.cantidad().multiply(it.valorUnitario()));
            boolean gravado = "10".equals(it.afectacionIgv());
            BigDecimal igvLinea = gravado ? money(valorVenta.multiply(TASA_IGV)) : cero();
            BigDecimal precioConIgv = gravado ? money(it.valorUnitario().multiply(UNO_MAS_IGV)) : money(it.valorUnitario());
            lineas.add(new LineaCalculada(n, it, valorVenta, igvLinea, precioConIgv));
            switch (it.afectacionIgv()) {
                case "10" -> gravadas = gravadas.add(valorVenta);
                case "20" -> exoneradas = exoneradas.add(valorVenta);
                case "30" -> inafectas = inafectas.add(valorVenta);
                case "40" -> exportacion = exportacion.add(valorVenta);
                default -> throw new IllegalArgumentException("Afectacion IGV no soportada: " + it.afectacionIgv());
            }
            igv = igv.add(igvLinea);
        }
        BigDecimal valorVenta = gravadas.add(exoneradas).add(inafectas).add(exportacion);
        return new Totales(List.copyOf(lineas), gravadas, exoneradas, inafectas, exportacion, igv, valorVenta,
                valorVenta.add(igv));
    }

    public static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal cero() {
        return BigDecimal.ZERO.setScale(2);
    }
}
