package pe.sfe.core.domain;

/** Comprobante que una nota de credito/debito modifica. */
public record ReferenciaDocumento(TipoComprobante tipo, String serie, long correlativo) {

    public String numero() {
        return serie + "-" + correlativo;
    }
}
