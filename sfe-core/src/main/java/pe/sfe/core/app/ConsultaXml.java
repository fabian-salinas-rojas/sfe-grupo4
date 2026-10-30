package pe.sfe.core.app;

import java.util.Optional;
import java.util.UUID;
import pe.sfe.core.domain.TipoComprobante;

/** RF-06: acceso de solo lectura al XML firmado de un comprobante (filtrado por caja). */
public interface ConsultaXml {

    record XmlComprobante(TipoComprobante tipo, String serie, long correlativo, byte[] xmlFirmado) {
    }

    Optional<XmlComprobante> porId(long posId, UUID id);
}
