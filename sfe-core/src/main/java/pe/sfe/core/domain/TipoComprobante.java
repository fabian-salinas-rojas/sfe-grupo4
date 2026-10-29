package pe.sfe.core.domain;

/** Catalogo 01 de SUNAT (subconjunto del alcance: factura, boleta, nota de credito y debito). */
public enum TipoComprobante {
    FACTURA("01"), BOLETA("03"), NOTA_CREDITO("07"), NOTA_DEBITO("08");

    private final String codigo;

    TipoComprobante(String codigo) {
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }

    public boolean esNota() {
        return this == NOTA_CREDITO || this == NOTA_DEBITO;
    }

    public static TipoComprobante deCodigo(String codigo) {
        for (TipoComprobante t : values()) {
            if (t.codigo.equals(codigo)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Tipo de comprobante no soportado: " + codigo);
    }
}
