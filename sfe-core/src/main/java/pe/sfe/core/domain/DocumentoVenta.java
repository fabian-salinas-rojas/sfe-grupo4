package pe.sfe.core.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Documento ya numerado, listo para convertirse en XML UBL 2.1. referencia y motivo solo aplican a notas. */
public record DocumentoVenta(
        TipoComprobante tipo,
        String serie,
        long correlativo,
        LocalDate fechaEmision,
        LocalTime horaEmision,
        String moneda,
        Emisor emisor,
        Adquirente adquirente,
        List<ItemVenta> items,
        ReferenciaDocumento referencia,
        MotivoNota motivo) {

    public String numero() {
        return serie + "-" + correlativo;
    }
}
