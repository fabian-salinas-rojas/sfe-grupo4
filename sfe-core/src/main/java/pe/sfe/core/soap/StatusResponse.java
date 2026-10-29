package pe.sfe.core.soap;

/** Respuesta de getStatus / getStatusCdr. statusCode: 0 ok, 98 en proceso, 99 con errores (manual, getStatus). */
public record StatusResponse(String statusCode, byte[] content, String statusMessage) {
}
