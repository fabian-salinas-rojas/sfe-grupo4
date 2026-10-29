package pe.sfe.core.domain;

import java.util.Map;
import java.util.Set;

/** Catalogos de SUNAT usados por las validaciones (los mismos valores se siembran en la BD, V2__catalogos). */
public final class CatalogosSunat {

    /** Catalogo 06. */
    public static final Set<String> TIPOS_DOC_IDENTIDAD = Set.of("0", "1", "4", "6", "7");
    /** Catalogo 07 (solo operaciones onerosas: alcance del prototipo). */
    public static final Set<String> AFECTACIONES_IGV = Set.of("10", "20", "30", "40");
    /** Catalogo 09: tipo de nota de credito. */
    public static final Map<String, String> TIPOS_NOTA_CREDITO = Map.ofEntries(
            Map.entry("01", "Anulacion de la operacion"),
            Map.entry("02", "Anulacion por error en el RUC"),
            Map.entry("03", "Correccion por error en la descripcion"),
            Map.entry("04", "Descuento global"),
            Map.entry("05", "Descuento por item"),
            Map.entry("06", "Devolucion total"),
            Map.entry("07", "Devolucion por item"),
            Map.entry("08", "Bonificacion"),
            Map.entry("09", "Disminucion en el valor"),
            Map.entry("10", "Otros conceptos"));
    /** Catalogo 10: tipo de nota de debito. */
    public static final Map<String, String> TIPOS_NOTA_DEBITO = Map.of(
            "01", "Intereses por mora",
            "02", "Aumento en el valor",
            "03", "Penalidades / otros conceptos");
    public static final Set<String> MONEDAS = Set.of("PEN", "USD");

    private CatalogosSunat() {
    }
}
