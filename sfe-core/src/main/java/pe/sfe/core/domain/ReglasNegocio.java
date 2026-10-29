package pe.sfe.core.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Reglas RN-01..RN-03 del APF1 (RN-04 vive en {@link CalculadoraTotales}). Funciones puras, sin I/O. */
public final class ReglasNegocio {

    private static final BigDecimal UMBRAL_IDENTIFICACION_PEN = new BigDecimal("700");
    private static final Pattern SERIE_REF = Pattern.compile("[FB][A-Z0-9]{3}");
    private static final Pattern UNIDAD = Pattern.compile("[A-Z0-9]{2,3}");

    private ReglasNegocio() {
    }

    public static void validar(SolicitudEmision s) {
        List<String> v = new ArrayList<>();
        if (s.idVentaExterno() == null || s.idVentaExterno().isBlank() || s.idVentaExterno().length() > 64) {
            v.add("idVentaExterno es obligatorio (maximo 64 caracteres)");
        }
        if (s.tipo() == null) {
            v.add("tipo de comprobante obligatorio");
            throw new NegocioException(v);
        }
        if (s.moneda() == null || !CatalogosSunat.MONEDAS.contains(s.moneda())) {
            v.add("moneda no soportada (PEN o USD)");
        }
        validarItems(s, v);
        validarAdquirente(s, v);
        if (s.tipo().esNota()) {
            validarNota(s, v);
        }
        if (v.isEmpty() && s.tipo() == TipoComprobante.BOLETA) {
            validarBoleta(s, v);
        }
        if (!v.isEmpty()) {
            throw new NegocioException(v);
        }
    }

    private static void validarItems(SolicitudEmision s, List<String> v) {
        if (s.items() == null || s.items().isEmpty()) {
            v.add("Debe haber al menos un item");
            return;
        }
        int n = 0;
        for (ItemVenta it : s.items()) {
            n++;
            String p = "item " + n + ": ";
            if (it.descripcion() == null || it.descripcion().isBlank()) {
                v.add(p + "descripcion obligatoria");
            }
            if (it.cantidad() == null || it.cantidad().signum() <= 0) {
                v.add(p + "cantidad debe ser mayor que cero");
            }
            if (it.valorUnitario() == null || it.valorUnitario().signum() < 0) {
                v.add(p + "valor unitario no puede ser negativo");
            }
            if (it.afectacionIgv() == null || !CatalogosSunat.AFECTACIONES_IGV.contains(it.afectacionIgv())) {
                v.add(p + "afectacion IGV fuera del catalogo 07 soportado (10, 20, 30, 40)");
            }
            if (it.unidadMedida() == null || !UNIDAD.matcher(it.unidadMedida()).matches()) {
                v.add(p + "unidad de medida invalida (codigo UN/ECE, p. ej. NIU, ZZ, KGM)");
            }
        }
    }

    private static void validarAdquirente(SolicitudEmision s, List<String> v) {
        Adquirente a = s.adquirente();
        if (a == null) {
            v.add("adquirente obligatorio");
            return;
        }
        if (!CatalogosSunat.TIPOS_DOC_IDENTIDAD.contains(a.tipoDocumento())) {
            v.add("tipo de documento del adquirente fuera del catalogo 06");
            return;
        }
        String num = a.numeroDocumento() == null ? "" : a.numeroDocumento();
        switch (a.tipoDocumento()) {
            case "6" -> {
                if (!num.matches("\\d{11}")) v.add("RUC del adquirente debe tener 11 digitos");
            }
            case "1" -> {
                if (!num.matches("\\d{8}")) v.add("DNI del adquirente debe tener 8 digitos");
            }
            case "4", "7" -> {
                if (!num.matches("[A-Za-z0-9]{1,12}")) v.add("documento del adquirente invalido (1 a 12 alfanumericos)");
            }
            default -> { /* "0": sin documento */ }
        }
        if (a.identificado() && (a.denominacion() == null || a.denominacion().isBlank())) {
            v.add("denominacion del adquirente obligatoria cuando esta identificado");
        }
        // RN-01: toda factura se emite a un cliente identificado con RUC.
        if (s.tipo() == TipoComprobante.FACTURA && !"6".equals(a.tipoDocumento())) {
            v.add("RN-01: la factura exige adquirente con RUC (tipo de documento 6)");
        }
    }

    private static void validarNota(SolicitudEmision s, List<String> v) {
        ReferenciaDocumento r = s.referencia();
        if (r == null) {
            v.add("RN-03: la nota debe indicar el comprobante que modifica");
        } else {
            if (r.tipo() != TipoComprobante.FACTURA && r.tipo() != TipoComprobante.BOLETA) {
                v.add("RN-03: una nota solo puede modificar una factura o boleta");
            }
            if (r.serie() == null || !SERIE_REF.matcher(r.serie()).matches()) {
                v.add("RN-03: serie del comprobante modificado invalida");
            } else if (r.tipo() == TipoComprobante.FACTURA && r.serie().charAt(0) != 'F') {
                v.add("RN-03: la factura modificada debe tener serie F###");
            } else if (r.tipo() == TipoComprobante.BOLETA && r.serie().charAt(0) != 'B') {
                v.add("RN-03: la boleta modificada debe tener serie B###");
            }
            if (r.correlativo() < 1 || r.correlativo() > 99_999_999L) {
                v.add("RN-03: correlativo del comprobante modificado fuera de rango");
            }
        }
        MotivoNota m = s.motivo();
        if (m == null || m.descripcion() == null || m.descripcion().isBlank()) {
            v.add("RN-03: el motivo de sustento es obligatorio");
        } else {
            var catalogo = s.tipo() == TipoComprobante.NOTA_CREDITO
                    ? CatalogosSunat.TIPOS_NOTA_CREDITO : CatalogosSunat.TIPOS_NOTA_DEBITO;
            if (!catalogo.containsKey(m.codigo())) {
                v.add("RN-03: tipo de nota fuera del catalogo " + (s.tipo() == TipoComprobante.NOTA_CREDITO ? "09" : "10"));
            }
        }
    }

    /** RN-02: boleta; identificar al adquirente cuando el importe supera S/ 700. */
    private static void validarBoleta(SolicitudEmision s, List<String> v) {
        Totales t = CalculadoraTotales.calcular(s.items());
        boolean exige = "USD".equals(s.moneda())
                ? true // conservador: sin tipo de cambio en el prototipo, en USD siempre se identifica
                : t.importeTotal().compareTo(UMBRAL_IDENTIFICACION_PEN) > 0;
        if (exige && !s.adquirente().identificado()) {
            v.add("RN-02: boleta con importe mayor a S/ 700 exige identificar al adquirente (tipo y numero de documento)");
        }
    }
}
