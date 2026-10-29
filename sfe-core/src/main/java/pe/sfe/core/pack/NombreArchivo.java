package pe.sfe.core.pack;

import java.util.regex.Pattern;
import pe.sfe.core.domain.TipoComprobante;

/** Convencion de nombres del manual SUNAT (1.2): {RUC}-{TT}-{SERIE}-{CORRELATIVO}.xml / .zip */
public record NombreArchivo(String base) {

    private static final Pattern RUC = Pattern.compile("\\d{11}");
    private static final Pattern SERIE = Pattern.compile("[FB][A-Z0-9]{3}");

    public static NombreArchivo comprobante(String ruc, TipoComprobante tipo, String serie, long correlativo) {
        if (!RUC.matcher(ruc).matches()) {
            throw new IllegalArgumentException("RUC del emisor invalido");
        }
        if (!SERIE.matcher(serie).matches()) {
            throw new IllegalArgumentException("Serie invalida: " + serie);
        }
        if (correlativo < 1 || correlativo > 99_999_999L) {
            throw new IllegalArgumentException("Correlativo fuera de rango (1..99999999)");
        }
        return new NombreArchivo(ruc + "-" + tipo.codigo() + "-" + serie + "-" + correlativo);
    }

    public String xml() {
        return base + ".xml";
    }

    public String zip() {
        return base + ".zip";
    }
}
