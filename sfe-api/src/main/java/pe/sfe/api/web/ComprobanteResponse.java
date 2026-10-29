package pe.sfe.api.web;

import java.math.BigDecimal;
import java.util.UUID;
import pe.sfe.core.app.Ports;

public record ComprobanteResponse(
        UUID id, String tipo, String serie, long correlativo, String numero, String estado,
        BigDecimal importeTotal, String cdrCodigo, String cdrDescripcion) {

    public static ComprobanteResponse de(Ports.ComprobanteResumen c) {
        return new ComprobanteResponse(c.id(), c.tipo().codigo(), c.serie(), c.correlativo(),
                c.serie() + "-" + c.correlativo(), c.estado().name(), c.importeTotal(), c.cdrCodigo(), c.cdrDescripcion());
    }
}
