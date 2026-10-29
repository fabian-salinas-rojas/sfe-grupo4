package pe.sfe.core.cdr;

import java.util.List;

/**
 * Constancia de Recepcion (ApplicationResponse UBL 2.0) ya interpretada.
 * codigoRespuesta "0" = aceptado; distinto de cero = codigo de error que genero el rechazo (manual, B.13).
 * firmaIntegra solo indica que la firma XMLDSig del CDR es matematicamente consistente con el certificado
 * embebido (integridad); NO prueba por si sola que el firmante sea SUNAT (eso exige fijar el certificado).
 */
public record Cdr(
        String idProceso,
        String referenciaDocumento,
        String codigoRespuesta,
        String descripcion,
        List<Observacion> observaciones,
        boolean firmaIntegra) {

    public record Observacion(String codigo, String descripcion) {
    }

    public boolean aceptado() {
        return "0".equals(codigoRespuesta);
    }
}
