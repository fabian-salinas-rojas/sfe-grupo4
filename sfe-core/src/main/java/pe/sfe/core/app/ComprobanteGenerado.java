package pe.sfe.core.app;

import pe.sfe.core.domain.Totales;
import pe.sfe.core.pack.NombreArchivo;

/** Salida del pipeline: los bytes del XML son EXACTAMENTE los que se firmaron y viajan dentro del ZIP. */
public record ComprobanteGenerado(NombreArchivo nombre, byte[] xmlFirmado, byte[] zip, Totales totales, String xmlSha256) {
}
